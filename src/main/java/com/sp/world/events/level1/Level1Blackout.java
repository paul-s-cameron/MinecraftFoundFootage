package com.sp.world.events.level1;

import com.sp.entity.SmilerSpawner;
import com.sp.init.BackroomsLevels;
import com.sp.init.ModSounds;
import com.sp.world.events.AbstractEvent;
import com.sp.world.levels.BackroomsLevel;
import com.sp.world.levels.BackroomsLevelWithLights;
import com.sp.world.levels.custom.Level1BackroomsLevel;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.Optional;

public class Level1Blackout extends AbstractEvent {
    /** How long to wait before trying again when every direction was in somebody's view. */
    private static final int RETRY_DELAY_TICKS = 10;

    /** Lead-in, so the blackout lands before anything arrives in it. */
    private int smilerSpawnDelay = 80;

    @Override
    public void init(World world) {
        if (!((BackroomsLevels.getLevel(world).orElse(BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL)) instanceof Level1BackroomsLevel level)) {
            return;
        }

        if(level.getLightState() != BackroomsLevelWithLights.LightState.BLACKOUT) {
            level.setLightState(BackroomsLevelWithLights.LightState.BLACKOUT);
            playSound(world, ModSounds.LIGHTS_OUT);
        }
    }

    @Override
    public void ticks(int ticks, World world) {
        if (world.getRegistryKey() != BackroomsLevels.LEVEL1_WORLD_KEY || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        Optional<BackroomsLevel> level = BackroomsLevels.getLevel(world);
        if (level.isEmpty()) {
            return;
        }
        BackroomsLevel.SmilerPolicy policy = level.get().smilerPolicy();
        if (policy == null) {
            return;
        }

        this.smilerSpawnDelay--;
        if (this.smilerSpawnDelay >= 0) {
            return;
        }

        // The interval stays open until one is actually placed, rather than being spent on a turn
        // where every direction happened to be in somebody's view. Otherwise a group facing outward
        // in a corridor could quietly skip most of a blackout's worth of arrivals.
        boolean placed = false;
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            placed |= SmilerSpawner.trySpawnNear(serverWorld, player, policy);
        }

        // A failed turn backs off briefly rather than retrying every tick. Each attempt raycasts
        // once per player to check nobody is looking, so a group facing outward in a corridor -
        // where placement legitimately keeps failing - would otherwise cost hundreds of raycasts a
        // second for as long as the blackout lasts.
        this.smilerSpawnDelay = placed ? policy.spawnIntervalTicks() : RETRY_DELAY_TICKS;
    }

    @Override
    public void finish(World world) {
        super.finish(world);

        if (!((BackroomsLevels.getLevel(world).orElse(BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL)) instanceof Level1BackroomsLevel level)) {
            return;
        }

        level.setLightState(BackroomsLevelWithLights.LightState.ON);
        playSound(world, ModSounds.LIGHTS_ON);
    }


    @Override
    public int duration() {
        return 600;
    }
}
