package com.sp.world.levels.custom;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.events.level2.Level2Ambience;
import com.sp.world.events.level2.Level2Warp;
import com.sp.world.generation.chunk_generator.Level2ChunkGenerator;
import com.sp.world.levels.BackroomsLevel;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class Level2BackroomsLevel extends BackroomsLevel {
    private boolean isWarping = false;

    public Level2BackroomsLevel() {
        super("level2", Level2ChunkGenerator.CODEC, new Vec3d(0.5, 20, 8), BackroomsLevels.LEVEL2_WORLD_KEY);

        this.registerEvent("warp", Level2Warp::new);
        this.registerEvent("abience", Level2Ambience::new);

        this.registerExitRule(new ExitRule(
                "level2 -> poolrooms",
                (world, playerComponent) ->
                        Math.abs(playerComponent.player.getPos().getZ()) >= SPBRevamped.getExitSpawnRadius(world),
                this::getPoolRoomsTransition,
                // Wider and longer than the mazes: the exit is hundreds of blocks down a corridor.
                new RallyPolicy(8.0, 2400)));
    }

    private LevelTransition getPoolRoomsTransition(PlayerComponent playerComponent, Vec3d rallyPos) {
        return new LevelTransition(
                110,
                (teleport, tick) -> {
                    World world = teleport.playerComponent().player.getWorld();

                    if (world.isClient()) {
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
    public int nextEventDelay() {
        return random.nextInt(500, 800);
    }

    public boolean isWarping() {
        return isWarping;
    }

    public void setWarping(boolean warping) {
        isWarping = warping;
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {
        nbt.putBoolean("isWarping", isWarping);
    }

    @Override
    public void readFromNbt(NbtCompound nbt) {
        this.isWarping = nbt.getBoolean("isWarping");
    }

    @Override
    public void transitionOut(CrossDimensionTeleport crossDimensionTeleport) {

    }

    @Override
    public void transitionIn(CrossDimensionTeleport crossDimensionTeleport) {

    }
}
