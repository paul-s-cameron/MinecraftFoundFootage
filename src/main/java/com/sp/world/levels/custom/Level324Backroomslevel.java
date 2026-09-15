package com.sp.world.levels.custom;

import com.sp.SPBRevamped;
import com.sp.SPBRevampedClient;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.init.ModBlocks;
import com.sp.world.events.generic.lights.LightLevelFlicker;
import com.sp.world.events.level324.ScreechSoundEvent;
import com.sp.world.generation.chunk_generator.Level324ChunkGenerator;
import com.sp.world.levels.BackroomsLevel;
import com.sp.world.levels.BackroomsLevelWithLights;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class Level324Backroomslevel extends BackroomsLevel implements BackroomsLevelWithLights {
    private Level0BackroomsLevel.LightState lightState = BackroomsLevelWithLights.LightState.ON;

    public Level324Backroomslevel() {
        super("level324", Level324ChunkGenerator.CODEC, new Vec3d(52,65,21), BackroomsLevels.LEVEL324_WORLD_KEY);

        this.registerEvent("flicker", LightLevelFlicker::new);
        this.registerEvent("ambience", ScreechSoundEvent::new);

        this.registerExitRule(new ExitRule(
                this.getLevelId() + " -> " + BackroomsLevels.INFINITE_FIELD_BACKROOMS_LEVEL.getLevelId(),
                (world, playerComponent) -> {
                    int exitRadius = SPBRevamped.getExitSpawnRadius(world);
                    return hasGrassBeneath(playerComponent)
                            && playerComponent.player.getPos().squaredDistanceTo(new Vec3d(0, 65, 0))
                            >= (double) ((exitRadius / 3) * (exitRadius / 3));
                },
                this::getInfiniteFieldsTransition,
                new RallyPolicy(8.0, 2400)));

        this.registerExitRule(new ExitRule(
                this.getLevelId() + " -> " + BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL.getLevelId(),
                (world, playerComponent) -> {
                    Vec2f[] puddleLocations = new Vec2f[]{
                    new Vec2f(300.0f, 0.0f),
                    new Vec2f(-300.0f, 0.0f),
                    new Vec2f(0.0f, 300.0f),
                    new Vec2f(0.0f, -300.0f),
                    new Vec2f(150.0f, 150.0f),
                    new Vec2f(150.0f, -150.0f),
                    new Vec2f(-150.0f, 150.0f),
                    new Vec2f(-150.0f, -150.0f),
                    new Vec2f(100.0f, 200.0f),
                    new Vec2f(100.0f, -200.0f),
                    new Vec2f(-100.0f, 200.0f),
                    new Vec2f(-100.0f, -200.0f),
                    new Vec2f(200.0f, 100.0f),
                    new Vec2f(-200.0f, 100.0f),
                            new Vec2f(200.0f, -100.0f),
                            new Vec2f(-200.0f, -100.0f)
                    };

                    if (playerComponent.player.getY() >= 20) {
                        return false;
                    }

                    Vec2f playerPos = new Vec2f((float) playerComponent.player.getX(), (float) playerComponent.player.getZ());
                    for (Vec2f puddle : puddleLocations) {
                        if (4 > puddle.distanceSquared(playerPos)) {
                            return true;
                        }
                    }
                    return false;
                },
                this::getPoolRoomsTransition,
                new RallyPolicy(8.0, 2400)));
    }

    private static boolean hasGrassBeneath(PlayerComponent playerComponent) {
        return playerComponent.player.getWorld().getBlockState(playerComponent.player.supportingBlockPos.orElseGet(() ->
                playerComponent.player.getBlockPos().subtract(new Vec3i(0,1,0)))).isOf(ModBlocks.RED_DIRT);
    }

    private LevelTransition getInfiniteFieldsTransition(PlayerComponent playerComponent, Vec3d rallyPos) {
        return new LevelTransition(
                40,
                (teleport, tick) -> {
                    World world = teleport.playerComponent().player.getWorld();

                    if (world.isClient()) {
                        if (tick == 14) {
                            SPBRevampedClient.getCutsceneManager().blackScreen.showBlackScreen(20, true, false);
                        }
                        return;
                    }

                    if (tick == 20) {
                        teleport.playerComponent().setShouldNoClip(true);
                        teleport.playerComponent().sync();
                    }

                    if (tick == 14) {
                        SPBRevamped.sendBlackScreenPacket((ServerPlayerEntity) teleport.playerComponent().player, 20, true, false);
                    }

                    //After the screen turns black THEN teleport
                    if (tick == 1) {
                        teleport.playerComponent().setShouldNoClip(false);
                        teleport.playerComponent().sync();
                    }
                }, // Tick
                new CrossDimensionTeleport(
                        playerComponent,
                        BackroomsLevels.INFINITE_FIELD_BACKROOMS_LEVEL.getSpawnPos(),
                        this,
                        BackroomsLevels.INFINITE_FIELD_BACKROOMS_LEVEL
                ),
                (teleport, tick) -> {
                    teleport.playerComponent().setShouldNoClip(false);
                    teleport.playerComponent().sync();
                }); // Cancel
    }

    private LevelTransition getPoolRoomsTransition(PlayerComponent playerComponent, Vec3d rallyPos) {
        return new LevelTransition(
                10,
                (teleport, tick) -> {
                    World world = teleport.playerComponent().player.getWorld();
                    if (tick == 9) {
                        teleport.playerComponent().setShouldNoClip(true);
                        teleport.playerComponent().sync();
                    }

                    if (world.isClient()) {
                        if (tick == 4) {
                            SPBRevampedClient.getCutsceneManager().blackScreen.showBlackScreen(20, true, false);
                        }
                        return;
                    }

                    if (tick == 4) {
                        SPBRevamped.sendBlackScreenPacket((ServerPlayerEntity) teleport.playerComponent().player, 20, true, false);
                    }

                    //After the screen turns black THEN teleport
                    if (tick == 1) {
                        teleport.playerComponent().setShouldNoClip(false);
                        teleport.playerComponent().sync();
                    }
                }, // Tick
                new CrossDimensionTeleport(
                        playerComponent,
                        BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL.getSpawnPos(),
                        this,
                        BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL
                ),
                (teleport, tick) -> {
                    teleport.playerComponent().setShouldNoClip(false);
                    teleport.playerComponent().sync();
                }); // Cancel
    }

    @Override
    public boolean rendersClouds() {
        return false;
    }

    @Override
    public boolean rendersSky() {
        return false;
    }

    public int nextEventDelay() {
        return random.nextInt(1000, 1200);
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
