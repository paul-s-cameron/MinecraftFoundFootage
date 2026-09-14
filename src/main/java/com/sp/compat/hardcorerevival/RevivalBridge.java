package com.sp.compat.hardcorerevival;

import net.blay09.mods.hardcorerevival.api.HardcoreRevivalAPI;
import net.minecraft.entity.player.PlayerEntity;

/**
 * The only class that touches Hardcore Revival. Reached exclusively through {@link Revival},
 * which checks the mod is loaded first — never reference this class directly.
 */
final class RevivalBridge {
    private RevivalBridge() {
    }

    static boolean isDowned(PlayerEntity player) {
        return HardcoreRevivalAPI.isKnockedOut(player);
    }

    static void wakeUp(PlayerEntity player, boolean applyEffects) {
        if (HardcoreRevivalAPI.isKnockedOut(player)) {
            HardcoreRevivalAPI.wakeup(player, applyEffects);
        }
    }
}
