package com.sp.entity;

import com.sp.entity.custom.SmilerEntity;
import com.sp.init.ModEntities;
import com.sp.world.levels.BackroomsLevel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Places smilers where nobody is looking.
 *
 * <p>A player must never watch one arrive. They are meant to have always already been there when
 * you first look at the corridor, and a face fading in fifteen blocks ahead of you breaks that as
 * thoroughly as watching one dissolve would. The old spawn picked a random angle around the player
 * with no regard for where they were facing, so it regularly appeared in their torch beam.
 *
 * <p>Nothing here removes a smiler. They live until the lights come back, which is the one vanish
 * that is allowed to be seen because it is the creature's own rule.
 */
public final class SmilerSpawner {

    /** Beyond this, a player is not considered to be looking at anything. */
    private static final double SIGHT_RANGE = 48.0;

    /**
     * How far off-centre still counts as seen. Deliberately generous — roughly a hemisphere rather
     * than a view cone, because being wrong here means a player watches one appear.
     */
    private static final double FORWARD_DOT = 0.0;

    /** Candidate directions tried before giving up for this interval. */
    private static final int PLACEMENT_ATTEMPTS = 12;

    /** So a large group cannot fill a maze with entities. */
    private static final int WORLD_CEILING = 12;

    private SmilerSpawner() {
    }

    /**
     * Tries to place one smiler for this player.
     *
     * @return whether one was placed, so the caller can hold its interval open until it succeeds
     *         rather than silently skipping a turn when every direction was in view.
     */
    public static boolean trySpawnNear(ServerWorld world, ServerPlayerEntity player, BackroomsLevel.SmilerPolicy policy) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        if (countIn(world) >= WORLD_CEILING || countNear(world, player, policy) >= policy.maxNearPlayer()) {
            return false;
        }

        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            float angle = world.getRandom().nextFloat() * 360.0f;
            Vec3d candidate = new Vec3d(0, 0, policy.spawnDistance()).rotateY(angle).add(player.getPos());

            if (!isStandable(world, candidate) || isObserved(world, candidate)) {
                continue;
            }

            SmilerEntity smiler = ModEntities.SMILER_ENTITY.create(world);
            if (smiler == null) {
                return false;
            }
            smiler.refreshPositionAndAngles(Math.floor(candidate.x) + 0.5, candidate.y,
                    Math.floor(candidate.z) + 0.5, 0.0f, 0.0f);
            world.spawnEntity(smiler);
            return true;
        }

        return false;
    }

    /**
     * Whether any player could see this spot: in front of them, within range, and with nothing in
     * the way. Light is deliberately not part of it — a smiler's eyes and teeth are visible in the
     * dark, which is the entire point of the creature, so a torch being off proves nothing.
     */
    public static boolean isObserved(ServerWorld world, Vec3d pos) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Spectators include ghosts, who are looking through a living teammate's eyes — that
            // teammate is in this list and answers for both of them.
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            Vec3d eyes = player.getEyePos();
            Vec3d toPos = pos.subtract(eyes);
            double distance = toPos.length();
            if (distance > SIGHT_RANGE || distance < 1.0E-4) {
                continue;
            }
            if (player.getRotationVec(1.0f).normalize().dotProduct(toPos.normalize()) < FORWARD_DOT) {
                continue;
            }
            if (hasLineOfSight(world, player, eyes, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLineOfSight(ServerWorld world, PlayerEntity player, Vec3d from, Vec3d to) {
        HitResult hit = world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** Somewhere a smiler can stand: open at head height, on something solid. */
    public static boolean isStandable(ServerWorld world, Vec3d pos) {
        BlockPos feet = BlockPos.ofFloored(pos);
        return !world.getBlockState(feet).blocksMovement()
                && !world.getBlockState(feet.up()).blocksMovement()
                && world.getBlockState(feet.down()).blocksMovement();
    }

    private static int countNear(ServerWorld world, ServerPlayerEntity player, BackroomsLevel.SmilerPolicy policy) {
        // Counted over a box rather than the whole level, so the cap follows the group: players who
        // stay together concentrate them, and players who split get their own.
        double radius = policy.spawnDistance() * 2.0;
        Box box = player.getBoundingBox().expand(radius, radius, radius);
        return world.getEntitiesByClass(SmilerEntity.class, box, smiler -> true).size();
    }

    private static int countIn(ServerWorld world) {
        List<? extends SmilerEntity> all = world.getEntitiesByType(ModEntities.SMILER_ENTITY, smiler -> true);
        return all.size();
    }
}
