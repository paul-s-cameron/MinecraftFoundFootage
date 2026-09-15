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
     * The spectator menu can teleport a spectator to any player in any dimension. Neither a ghost
     * nor a skinwalker's captive may use it: a ghost sees only what the teammate they are watching
     * sees, and a captive is being controlled and chooses nothing. For the captive this is an
     * escape from the capture outright, not merely a better view.
     *
     * <p>Checked here as well as on the client because the client half can simply be absent.
     */
    @Inject(method = "onSpectatorTeleport", at = @At("HEAD"), cancellable = true)
    private void backrooms$noSpectatorTeleportWhenHeld(SpectatorTeleportC2SPacket packet, CallbackInfo ci) {
        PlayerComponent component = InitializeComponents.PLAYER.get(this.player);
        if (component.isCameraLocked()) {
            ci.cancel();
        }
    }
}
