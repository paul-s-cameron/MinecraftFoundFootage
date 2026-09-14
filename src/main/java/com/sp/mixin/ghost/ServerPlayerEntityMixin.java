package com.sp.mixin.ghost;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    /**
     * Vanilla drags a spectator onto whatever their camera entity is every tick, and only checks
     * that the entity is <i>alive</i> — never that it is in the same world. A ghost watching a
     * teammate who then leaves the level (escaping to the lobby, or departing on a rally a moment
     * before the ghost does) would be moved to that other dimension's raw coordinates inside
     * their own, dragging their chunk ticket along with them and generating an unrelated region.
     *
     * <p>The camera is re-attached a tick later anyway, so the move is never wanted.
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;updatePositionAndAngles(DDDFF)V"))
    private void backrooms$noCrossWorldCameraGlue(ServerPlayerEntity instance,
                                                  double x, double y, double z, float yaw, float pitch) {
        Entity camera = instance.getCameraEntity();
        if (camera != null && camera != instance && camera.getWorld() != instance.getWorld()) {
            return;
        }
        instance.updatePositionAndAngles(x, y, z, yaw, pitch);
    }
}
