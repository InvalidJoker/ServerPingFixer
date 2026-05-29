package de.joker.mixin;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {
    @Unique
    private static final Logger log = LoggerFactory.getLogger(ServerStatusPingerMixin.class);

    @Unique
    private static final Set<String> SECOND_PING =
            Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    @Unique
    private static final Queue<Runnable> RETRY_QUEUE = new ConcurrentLinkedQueue<>();

    @Inject(method = "tick", at = @At("TAIL"))
    private void runQueuedRetries(CallbackInfo ci) {
        Runnable task;
        while ((task = RETRY_QUEUE.poll()) != null) {
            task.run();
        }
    }

    @ModifyVariable(
            method = "pingServer",
            at = @At("STORE"),
            ordinal = 0
    )
    private ClientStatusPacketListener replaceListener(
            ClientStatusPacketListener original,
            ServerData data,
            Runnable onPersistentDataChange,
            Runnable onPongResponse,
            EventLoopGroupHolder eventLoopGroupHolder
    ) {
        ServerStatusPinger self = (ServerStatusPinger)(Object)this;

        return new ClientStatusPacketListener() {

            @Override
            public void handleStatusResponse(@NonNull ClientboundStatusResponsePacket packet) {
                original.handleStatusResponse(packet);
            }

            @Override
            public void handlePongResponse(@NonNull ClientboundPongResponsePacket packet) {
                original.handlePongResponse(packet);
            }

            @Override
            public void onDisconnect(@NonNull DisconnectionDetails details) {
                if (details.reason().getString().equals("Finished")) {
                    original.onDisconnect(details);
                    return;
                }

                //log.warn("Failed to ping server {}: {}", data.ip, details.reason().getString());

                if (SECOND_PING.add(data.ip)) {
                    RETRY_QUEUE.add(() -> {
                        try {
                            self.pingServer(
                                    data,
                                    onPersistentDataChange,
                                    () -> {
                                        SECOND_PING.remove(data.ip);
                                        onPongResponse.run();
                                    },
                                    eventLoopGroupHolder
                            );
                        } catch (Throwable t) {
                            log.error("Error while retrying ping for server {}: {}", data.ip, t.getMessage(), t);
                            SECOND_PING.remove(data.ip);
                            original.onDisconnect(details);
                        }
                    });
                    return;
                }

                log.warn("Second ping attempt for server {} failed, giving up: {}", data.ip, details.reason().getString());

                SECOND_PING.remove(data.ip);
                original.onDisconnect(details);
            }

            @Override
            public boolean isAcceptingMessages() {
                return original.isAcceptingMessages();
            }
        };
    }
}
