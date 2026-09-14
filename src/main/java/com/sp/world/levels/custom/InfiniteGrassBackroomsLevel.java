package com.sp.world.levels.custom;

import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.events.infinite_grass.InfiniteGrassAmbience;
import com.sp.world.generation.chunk_generator.InfGrassChunkGenerator;
import com.sp.world.levels.BackroomsLevel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InfiniteGrassBackroomsLevel extends BackroomsLevel {

    public InfiniteGrassBackroomsLevel() {
        super("inf_grass", InfGrassChunkGenerator.CODEC, new Vec3d(0, 31, 0), BackroomsLevels.INFINITE_FIELD_WORLD_KEY);
    }

    @Override
    public void register() {
        super.register();

        this.registerEvent("ambience", InfiniteGrassAmbience::new);

        // The way out. Rallying here means the group escapes together rather than one at a time.
        this.registerExitRule(new ExitRule(
                this.getLevelId() + "->" + BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL.getLevelId(),
                (world, playerComponent) ->
                        playerComponent.player.getPos().y > 57.5 && playerComponent.player.isOnGround(),
                this::getOverworldTransition,
                new RallyPolicy(6.0, 1800)));
    }

    /**
     * Arrival is deliberately still per-player: everyone goes back to their own spawn point,
     * not the rally point.
     */
    private LevelTransition getOverworldTransition(PlayerComponent playerComponent, Vec3d rallyPos) {
        Optional<Vec3d> optional = Optional.empty();
        BlockPos blockPos1 = new BlockPos(0, 64, 0);
        if (playerComponent.player instanceof ServerPlayerEntity) {
            BlockPos blockPos = ((ServerPlayerEntity) playerComponent.player).getSpawnPointPosition();
            float f = ((ServerPlayerEntity) playerComponent.player).getSpawnAngle();
            boolean bl = ((ServerPlayerEntity) playerComponent.player).isSpawnForced();
            ServerWorld serverWorld = playerComponent.player.getWorld().getServer().getWorld(World.OVERWORLD);

            if (serverWorld != null && blockPos != null) {
                optional = PlayerEntity.findRespawnPosition(serverWorld, blockPos, f, bl, true);
            }

            World overworld = playerComponent.player.getWorld().getServer().getWorld(World.OVERWORLD);
            blockPos1 = overworld.getSpawnPos();
        }

        return new LevelTransition(
                1,
                // The inventory is given back by OverworldRepresentingBackroomsLevel.transitionIn.
                // Restoring it here as well wiped it: the restore clears the live inventory before
                // refilling it, and it empties the stash on the way out, so the second run cleared
                // what the first had just handed back.
                (teleport, tick) -> {},
                new CrossDimensionTeleport(
                        playerComponent,
                        optional.orElse(blockPos1.toCenterPos()),
                        this,
                        BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL),
                (teleport, tick) -> {});
    }

    @Override
    public int nextEventDelay() {
        return random.nextInt(1000, 1200);
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {

    }

    @Override
    public void readFromNbt(NbtCompound nbt) {

    }

    @Override
    public void transitionOut(CrossDimensionTeleport crossDimensionTeleport) {
    }

    @Override
    public void transitionIn(CrossDimensionTeleport crossDimensionTeleport) {
        crossDimensionTeleport.playerComponent().player.fallDistance = 0;
    }

    @Override
    public BoolTextPair allowsTorch() {
        return new BoolTextPair(false, Text.translatable("spb-revamped.flashlight.wet1").append(Text.translatable("spb-revamped.flashlight.wet2").formatted(Formatting.RED)));
    }

    @Override
    public boolean hasVanillaLighting() {
        return true;
    }
}
