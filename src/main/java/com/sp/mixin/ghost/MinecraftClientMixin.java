package com.sp.mixin.ghost;

import com.sp.ghost.GhostCameraClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A ghost's clicks change who they are watching instead of doing nothing.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void backrooms$ghostNextCamera(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (GhostCameraClient.isGhost(client)) {
            GhostCameraClient.cycle(1);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void backrooms$ghostPreviousCamera(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (GhostCameraClient.isGhost(client)) {
            GhostCameraClient.cycle(-1);
            ci.cancel();
        }
    }
}
