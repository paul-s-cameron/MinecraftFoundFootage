package com.sp.mixin.ghost;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.ghost.GhostCameraClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.SpectatorHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The number keys normally open the spectator menu, whose whole purpose is teleporting to any
 * player in any dimension. Nobody the game is holding gets to use it.
 *
 * <p>The two held states part company on what happens instead. A <b>ghost</b> is dead and picking
 * which teammate to watch, so the keys choose a camera. A skinwalker's <b>captive</b> is alive and
 * being controlled, so they do nothing at all — for them the menu is not a better view, it is a way
 * out of the capture.
 */
@Mixin(SpectatorHud.class)
public class SpectatorHudMixin {

    @Inject(method = "selectSlot", at = @At("HEAD"), cancellable = true)
    private void backrooms$selectCameraOrNothing(int slot, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (GhostCameraClient.isGhost(client)) {
            GhostCameraClient.select(slot);
            ci.cancel();
            return;
        }
        if (backrooms$isHeld(client)) {
            ci.cancel();
        }
    }

    /** Middle click is the other way into the menu. */
    @Inject(method = "useSelectedCommand", at = @At("HEAD"), cancellable = true)
    private void backrooms$noSpectatorMenuWhenHeld(CallbackInfo ci) {
        if (backrooms$isHeld(MinecraftClient.getInstance())) {
            ci.cancel();
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean backrooms$isHeld(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        PlayerComponent component = InitializeComponents.PLAYER.get(client.player);
        return component.isCameraLocked();
    }
}
