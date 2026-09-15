package com.sp.world.levels.custom;

import com.sp.settings.RoundOptions;
import com.sp.world.events.AbstractEvent;
import com.sp.world.events.generic.lights.LightLevelFlicker;
import com.sp.world.events.level1.Level1Blackout;
import net.minecraft.world.World;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.events.generic.lights.LightLevelFlicker;
import com.sp.world.events.level1.Level1Ambience;
import com.sp.world.events.level1.Level1Blackout;
import com.sp.world.generation.chunk_generator.Level1ChunkGenerator;
import com.sp.world.levels.BackroomsLevel;
import com.sp.world.levels.BackroomsLevelWithLights;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

public class Level1BackroomsLevel extends BackroomsLevel implements BackroomsLevelWithLights {
    /**
     * Three per player over a thirty-second blackout, arriving about every seven seconds. Nothing
     * retires, so this is also how many a blackout produces in total — they accumulate and are all
     * dispelled together when the lights come back.
     */
    /** Seven seconds between arrivals, placed fifteen blocks out. Only the count is a host setting. */
    private static final int SMILER_SPAWN_INTERVAL_TICKS = 140;
    private static final double SMILER_SPAWN_DISTANCE = 15.0;

    /** Rebuilt only when the host changes the count; asked for every tick of a blackout otherwise. */
    private SmilerPolicy smilerPolicy = new SmilerPolicy(3, SMILER_SPAWN_INTERVAL_TICKS, SMILER_SPAWN_DISTANCE);

    private Level0BackroomsLevel.LightState lightState = BackroomsLevelWithLights.LightState.ON;

    public Level1BackroomsLevel() {
        super("level1", Level1ChunkGenerator.CODEC, new RoomCount(6, 24, 24, 12, 24), new Vec3d(6, 22, 3), BackroomsLevels.LEVEL1_WORLD_KEY);
    }

    /** Only while the lights are out — the blackout is the whole of a smiler's existence here. */
    @Override
    public SmilerPolicy smilerPolicy() {
        if (this.lightState != BackroomsLevelWithLights.LightState.BLACKOUT
                || !RoundOptions.get().smilersEnabled()) {
            return null;
        }
        int perPlayer = RoundOptions.get().smilersPerPlayer();
        if (this.smilerPolicy.maxNearPlayer() != perPlayer) {
            this.smilerPolicy = new SmilerPolicy(perPlayer, SMILER_SPAWN_INTERVAL_TICKS, SMILER_SPAWN_DISTANCE);
        }
        return this.smilerPolicy;
    }

    /**
     * With random blackouts switched off, the slot a blackout would have taken becomes a flicker:
     * the lights still misbehave on the same cadence, they just never actually go. Substituting
     * rather than re-rolling keeps event timing identical to a round with them on, so the setting
     * changes one thing and not two.
     */
    @Override
    public AbstractEvent getRandomEvent(World world) {
        AbstractEvent event = super.getRandomEvent(world);
        if (event instanceof Level1Blackout && !RoundOptions.get().randomBlackouts()) {
            return new LightLevelFlicker();
        }
        return event;
    }

    @Override
    public void register() {
        super.register();

        this.registerEvent("blackout", Level1Blackout::new);
        this.registerEvent("flicker", LightLevelFlicker::new);
        this.registerEvent("ambience", Level1Ambience::new);

        this.registerExitRule(new ExitRule(
                this.getLevelId() + "->" + BackroomsLevels.LEVEL2_BACKROOMS_LEVEL.getLevelId(),
                (world, playerComponent) ->
                        playerComponent.player.getPos().getY() <= 12 && playerComponent.player.isOnGround(),
                this::getLevel2Transition,
                new RallyPolicy(6.0, 1800)));

        /*
        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();
            BlockState state = world.getBlockState(playerComponent.player.getBlockPos().subtract(new Vec3i(0, 2, 0)));

            if (
                    from instanceof Level1BackroomsLevel &&
                    playerComponent.player.getPos().getY() >= 26 &&
                    playerComponent.player.isOnGround() &&
                    state.isOf(Blocks.BLUE_WOOL)
            ) {
                for (PlayerEntity player : playerComponent.player.getWorld().getPlayers()) {
                    PlayerComponent otherPlayerComponent = InitializeComponents.PLAYER.get(player);
                    playerList.add(getLevel324Transition(otherPlayerComponent));
                }
            }

            return playerList;
        }, this.getLevelId() + "->" + BackroomsLevels.LEVEL324_BACKROOMS_LEVEL.getLevelId());

         */
    }


    private LevelTransition getLevel2Transition(PlayerComponent playerComponent, Vec3d rallyPos) {
        return new LevelTransition(
                30,
                (teleport, tick) -> {
                    if (tick == 30) {
                        if (!playerComponent.player.getWorld().isClient()) {
                            if(!playerComponent.isTeleporting()) {
                                SPBRevamped.sendLevelTransitionLightsOutPacket((ServerPlayerEntity) playerComponent.player, 80);
                            }
                        }
                    }
                }, // Tick
                new CrossDimensionTeleport(
                        playerComponent,
                        calculateLevel2TeleportCoords(rallyPos),
                        this,
                        BackroomsLevels.LEVEL2_BACKROOMS_LEVEL),
                (teleport, tick) -> {}
        ); // Cancel
    }

    @SuppressWarnings("unused") // kept for the commented-out level324 exit above
    private LevelTransition getLevel324Transition(PlayerComponent playerComponent) {
        return new LevelTransition(
                30,
                (teleport, tick) -> {
                    if (tick == 30) {
                        if (!playerComponent.player.getWorld().isClient()) {
                            if(!playerComponent.isTeleporting()) {
                                playerComponent.player.setYaw(playerComponent.player.getYaw() - 90);
                                SPBRevamped.sendLevelTransitionLightsOutPacket((ServerPlayerEntity) playerComponent.player, 80);
                            }
                        }
                    }
                }, // Tick
                new CrossDimensionTeleport(
                        playerComponent,
                        new Vec3d(53, 65, 21),
                        this,
                        BackroomsLevels.LEVEL324_BACKROOMS_LEVEL),
                (teleport, tick) -> {}
        ); // Cancel
    }

    /**
     * Derived from the rally point rather than each player's own position, so the group arrives
     * on the same spot instead of scattered by wherever they were standing.
     */
    private Vec3d calculateLevel2TeleportCoords(Vec3d rallyPos) {
        int chunkStartX = (((int) Math.floor(rallyPos.x)) >> 4) << 4;
        int chunkStartZ = (((int) Math.floor(rallyPos.z)) >> 4) << 4;

        return new Vec3d((rallyPos.x - chunkStartX) - 1, rallyPos.y + 8, rallyPos.z - chunkStartZ);
    }

    @Override
    public int nextEventDelay() {
        return random.nextInt(1000, 1600);
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {
        nbt.putString("lightState", lightState.name());
    }

    @Override
    public void readFromNbt(NbtCompound nbt) {
        this.lightState = BackroomsLevelWithLights.restored(nbt.getString("lightState"));

    }

    @Override
    public void transitionOut(CrossDimensionTeleport crossDimensionTeleport) {

    }

    @Override
    public void transitionIn(CrossDimensionTeleport crossDimensionTeleport) {

    }

    public void setLightState(Level0BackroomsLevel.LightState lightState) {
        this.justChanged();
        this.lightState = lightState;
    }

    public Level0BackroomsLevel.LightState getLightState() {
        return this.lightState;
    }
}
