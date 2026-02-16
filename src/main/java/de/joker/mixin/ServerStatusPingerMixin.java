package de.joker.mixin;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

@Mixin(ServerStatusPinger.class)
public class ServerStatusPingerMixin {
    @Unique
    private boolean retried = false;

    @Shadow
    void pingLegacyServer(InetSocketAddress address,
                          ServerAddress serverAddress,
                          ServerData serverData,
                          EventLoopGroupHolder eventLoopGroupHolder) {

    }

    @Inject(method = "pingLegacyServer", at = @At("HEAD"), cancellable = true)
    private void retryNormalBeforeLegacy(InetSocketAddress address,
                                         ServerAddress serverAddress,
                                         ServerData serverData,
                                         EventLoopGroupHolder eventLoopGroupHolder,
                                         CallbackInfo ci) throws UnknownHostException {

        if (!retried) {
            retried = true;
            ci.cancel(); // stop legacy ping

            pingLegacyServer(address, serverAddress, serverData, eventLoopGroupHolder);
        }
    }
}

