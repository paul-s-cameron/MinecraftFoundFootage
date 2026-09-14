package com.sp.mixin.skinstolen;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;setCameraEntity(Lnet/minecraft/entity/Entity;)V", ordinal = 0))
    private void cantEscapeSpectating(ServerPlayerEntity instance, Entity entity){
        PlayerComponent component = InitializeComponents.PLAYER.get(instance);
        // Sneaking normally drops a spectator back into their own body. Neither a skinwalker's
        // captive nor a ghost is allowed to leave the camera they are locked to.
        if(!component.hasBeenCaptured() && !component.isGhost()){
            instance.setCameraEntity(entity);
        }
    }

    @Inject(method = "canBeSpectated", at = @At("HEAD"), cancellable = true)
    private void stopSpectatorPlayersFromNotBeingCounted(ServerPlayerEntity spectator, CallbackInfoReturnable<Boolean> cir){
        PlayerComponent component = InitializeComponents.PLAYER.get((ServerPlayerEntity) (Object) this);
        if (component.hasBeenCaptured() || component.isBeingCaptured()){
            cir.setReturnValue(true);
        }
    }

    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;setCameraEntity(Lnet/minecraft/entity/Entity;)V"))
    private void dontChangeTargets(ServerPlayerEntity instance, Entity entity){
        PlayerComponent component = InitializeComponents.PLAYER.get((ServerPlayerEntity) (Object) this);
        // Clicking an entity as a spectator normally moves your camera onto it. A ghost may
        // only ever watch a teammate, and that choice is the server's to make.
        if (!component.hasBeenCaptured() && !component.isBeingCaptured() && !component.isGhost()){
            instance.setCameraEntity(entity);
        }
    }
}
