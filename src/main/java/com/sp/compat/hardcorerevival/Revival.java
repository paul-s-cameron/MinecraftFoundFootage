package com.sp.compat.hardcorerevival;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Guarded access to Hardcore Revival, which turns what would be a death into a downed state a
 * teammate can rescue you from.
 *
 * <p>Nothing here references Hardcore Revival directly — every such call goes through
 * {@link RevivalBridge}, which the JVM only loads once one of these guarded branches is taken.
 * The mod stays fully playable without it; you simply die instead of going down.
 */
public final class Revival {
    public static final String MOD_ID = "hardcorerevival";

    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private Revival() {
    }

    public static boolean isPresent() {
        return PRESENT;
    }

    /** Whether this player is lying down bleeding out, rather than up and playing. */
    public static boolean isDowned(PlayerEntity player) {
        return PRESENT && RevivalBridge.isDowned(player);
    }

    /**
     * Puts a downed player back on their feet without the rescue. Used when a round ends: a
     * player left knocked out would otherwise keep bleeding out in the lobby and die there.
     *
     * @param applyEffects whether to apply the usual post-rescue debuffs
     */
    public static void wakeUp(PlayerEntity player, boolean applyEffects) {
        if (PRESENT) {
            RevivalBridge.wakeUp(player, applyEffects);
        }
    }
}
