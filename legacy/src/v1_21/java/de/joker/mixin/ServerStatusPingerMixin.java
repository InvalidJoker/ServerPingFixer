package de.joker.mixin;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

// Minecraft 1.21 - 1.21.10
@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerStatusPingerMixin.class);

    @Unique
    private static final int MAX_ATTEMPTS = 3;

    @Unique
    private static final ConcurrentMap<ServerData, Integer> ATTEMPTS = new ConcurrentHashMap<>();

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
            Runnable onPongResponse
    ) {
        ServerStatusPinger pinger = (ServerStatusPinger) (Object) this;

        return new ClientStatusPacketListener() {
            @Override
            public void handleStatusResponse(ClientboundStatusResponsePacket packet) {
                original.handleStatusResponse(packet);
            }

            @Override
            public void handlePongResponse(ClientboundPongResponsePacket packet) {
                original.handlePongResponse(packet);
            }

            @Override
            public void onDisconnect(DisconnectionDetails details) {
                if (isFinished(details)) {
                    original.onDisconnect(details);
                    return;
                }

                int attempt = ATTEMPTS.merge(data, 1, Integer::sum);

                if (attempt < MAX_ATTEMPTS) {
                    LOGGER.warn(
                            "Ping attempt {} for server {} failed, retrying...",
                            attempt,
                            data.ip
                    );

                    RETRY_QUEUE.add(() -> retryPing(
                            pinger,
                            data,
                            onPersistentDataChange,
                            onPongResponse,
                            original,
                            details
                    ));

                    return;
                }

                LOGGER.warn(
                        "Ping attempt {} for server {} failed, giving up: {}",
                        attempt,
                        data.ip,
                        details.reason().getString()
                );

                cleanup(data);
                original.onDisconnect(details);
            }

            @Override
            public boolean isAcceptingMessages() {
                return original.isAcceptingMessages();
            }
        };
    }

    @Unique
    private void retryPing(
            ServerStatusPinger pinger,
            ServerData data,
            Runnable onPersistentDataChange,
            Runnable onPongResponse,
            ClientStatusPacketListener original,
            DisconnectionDetails details
    ) {
        try {
            pinger.pingServer(
                    data,
                    onPersistentDataChange,
                    () -> {
                        cleanup(data);
                        onPongResponse.run();
                    }
            );
        } catch (Throwable throwable) {
            LOGGER.error(
                    "Error while retrying ping for server {}",
                    data.ip,
                    throwable
            );

            cleanup(data);
            original.onDisconnect(details);
        }
    }

    @Unique
    private static boolean isFinished(DisconnectionDetails details) {
        return isFinishedReason(details.reason());
    }

    // Compare the translation key, the translated text depends on the client language
    @Unique
    private static boolean isFinishedReason(Component reason) {
        return reason.getContents() instanceof TranslatableContents contents
                && "multiplayer.status.finished".equals(contents.getKey());
    }

    @Unique
    private static void cleanup(ServerData data) {
        ATTEMPTS.remove(data);
    }
}
