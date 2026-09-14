package com.sp.mixin.ghost;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow @Final public ServerPlayerEntity player;

    /**
     * The spectator menu can teleport a spectator to any player in any dimension. A ghost is
     * meant to see only what the teammate they are watching sees, so the request is dropped and
     * the camera lock stands.
     */
    @Inject(method = "onSpectatorTeleport", at = @At("HEAD"), cancellable = true)
    private void backrooms$noSpectatorTeleportForGhosts(SpectatorTeleportC2SPacket packet, CallbackInfo ci) {
        PlayerComponent component = InitializeComponents.PLAYER.get(this.player);
        if (component.isGhost()) {
            ci.cancel();
        }
    }
}
