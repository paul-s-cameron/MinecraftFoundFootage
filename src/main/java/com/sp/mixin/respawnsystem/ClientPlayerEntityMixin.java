package com.sp.mixin.respawnsystem;

import com.sp.init.BackroomsLevels;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dying in the backrooms never shows the vanilla death screen.
 *
 * <p>Death hands the player straight to the ghost transition — black screen, "you can't
 * escape", static — but none of that starts until the respawn lands, so a stock "You died!"
 * card used to sit in front of it: the one piece of vanilla UI the mod otherwise never shows,
 * announcing the death several seconds before the game reacted to it.
 *
 * <p>This is vanilla's own immediate-respawn switch rather than anything custom. Both places
 * that build a DeathScreen ask this first and call {@code requestRespawn()} instead when it
 * says no — {@code ClientPlayNetworkHandler.onDeathMessage} when the death arrives, and
 * {@code MinecraftClient.setScreen(null)}, which is what would otherwise put the screen
 * straight back the moment anything closed it.
 *
 * <p>Scoped to the backrooms on purpose: dying in the lobby dimension keeps vanilla behaviour.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "showsDeathScreen", at = @At("HEAD"), cancellable = true)
    private void backrooms$skipDeathScreenInBackrooms(CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self.getWorld() != null && BackroomsLevels.isInBackrooms(self.getWorld().getRegistryKey())) {
            cir.setReturnValue(false);
        }
    }
}
