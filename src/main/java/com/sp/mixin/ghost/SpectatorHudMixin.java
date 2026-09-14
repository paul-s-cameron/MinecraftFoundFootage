package com.sp.mixin.ghost;

import com.sp.ghost.GhostCameraClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.SpectatorHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The number keys normally open the spectator menu, whose whole purpose is teleporting to any
 * player in any dimension. For a ghost they pick which teammate to watch instead, and the menu
 * never opens.
 */
@Mixin(SpectatorHud.class)
public class SpectatorHudMixin {
    @Inject(method = "selectSlot", at = @At("HEAD"), cancellable = true)
    private void backrooms$ghostSelectCamera(int slot, CallbackInfo ci) {
        if (GhostCameraClient.isGhost(MinecraftClient.getInstance())) {
            GhostCameraClient.select(slot);
            ci.cancel();
        }
    }

    /** Middle click is the other way into the menu. */
    @Inject(method = "useSelectedCommand", at = @At("HEAD"), cancellable = true)
    private void backrooms$noSpectatorMenuForGhosts(CallbackInfo ci) {
        if (GhostCameraClient.isGhost(MinecraftClient.getInstance())) {
            ci.cancel();
        }
    }
}
