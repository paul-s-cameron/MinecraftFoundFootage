package com.sp.ghost;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.compat.hardcorerevival.Revival;
import com.sp.init.BackroomsLevels;
import com.sp.world.levels.BackroomsLevel;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A dead player watches the run out through a teammate's eyes, and rejoins it when the group
 * reaches the next level.
 *
 * <p>Deliberately not free-flying spectator: a ghost who could fly the maze would just read it
 * out over voice chat. A ghost is a spectator whose camera is locked to a living or downed
 * player, re-attached here every tick so there is never a frame in which they are loose.
 *
 * <p>Vanilla does the heavy lifting once the camera is attached: {@code ServerPlayerEntity.tick}
 * forces the spectator's position onto the camera entity every tick, so a ghost cannot move on
 * their own and is always physically with the group (which is also what makes proximity voice
 * chat work for them). The ways out of that — sneaking to dismount, clicking another entity, and
 * the spectator menu's teleport — are blocked in {@code ServerPlayerEntityMixin} and
 * {@code ServerPlayNetworkHandlerMixin}.
 *
 * <p>The one case that needs care is a ghost with <i>nobody</i> to watch, because a spectator
 * whose camera is themselves is exactly the free-fly camera this feature exists to prevent. Such
 * a ghost is either sent on to wherever the group actually is, or pinned in place.
 */
public final class GhostManager {
    /**
     * Where a ghost with nobody left to watch is held. Server-side and transient: it is rebuilt
     * the moment a ghost is stranded again, and a stranded ghost does not survive a restart in
     * any meaningful sense anyway.
     */
    private static final Map<UUID, Vec3d> STRANDED = new HashMap<>();

    private GhostManager() {
    }

    /** Turns a player who has just died into a ghost, watching whoever was nearest to them. */
    public static void becomeGhost(ServerPlayerEntity player) {
        PlayerComponent component = InitializeComponents.PLAYER.get(player);

        component.setGhost(true);
        component.setGhostWorld(player.getWorld().getRegistryKey().getValue().toString());
        component.sync();

        player.changeGameMode(GameMode.SPECTATOR);
        attachToNearest(player);
    }

    /** Puts a ghost back in the run. Called on arrival in a level they did not die in. */
    public static void revive(ServerPlayerEntity player) {
        PlayerComponent component = InitializeComponents.PLAYER.get(player);
        if (!component.isGhost()) {
            return;
        }

        component.setGhost(false);
        component.setGhostWorld("");
        component.sync();
        STRANDED.remove(player.getUuid());

        player.setCameraEntity(player);
        player.changeGameMode(GameMode.SURVIVAL);
        restoreVitals(player);
    }

    /**
     * Clears everything death-related about a player without putting them back in a run: wakes
     * them if they are downed, drops the ghost state, and gives them their own eyes back.
     *
     * <p>For callers outside a round — the lobby mod uses it when parking players, because
     * somebody left downed would bleed out and die in the lobby, and a ghost would sit locked to
     * a camera with no way back into their own body.
     */
    public static void reset(ServerPlayerEntity player) {
        // Waking without the rescue effects leaves the half heart the knockout pinned them to,
        // so vitals are restored here rather than through Hardcore Revival's rescue values.
        Revival.wakeUp(player, false);

        PlayerComponent component = InitializeComponents.PLAYER.get(player);
        if (component.isGhost()) {
            component.setGhost(false);
            component.setGhostWorld("");
            component.sync();
        }

        STRANDED.remove(player.getUuid());
        player.setCameraEntity(player);
        restoreVitals(player);
    }

    private static void restoreVitals(ServerPlayerEntity player) {
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.clearStatusEffects();
        player.fallDistance = 0.0f;
    }

    /**
     * Called for every player from {@link PlayerComponent#serverTick()}. Ends a hopeless
     * knockout, keeps a ghost's camera on a valid teammate, and revives them once the group has
     * moved on without them.
     */
    public static void tick(ServerPlayerEntity player, PlayerComponent component) {
        if (Revival.isDowned(player)) {
            // Bleeding out for ninety seconds with nobody alive to reach you is not a rescue
            // moment, it is a wait. Hardcore Revival's own "playing alone" switch does not cover
            // this, because ghosts still count towards the server's player count.
            if (!hasPossibleRescuer(player)) {
                // In our bypasses_knockout tag, so it kills rather than knocking out again.
                player.damage(player.getDamageSources().genericKill(), Float.MAX_VALUE);
            }
            return;
        }

        if (!component.isGhost()) {
            return;
        }

        String here = player.getWorld().getRegistryKey().getValue().toString();
        if (!here.equals(component.getGhostWorld())
                && BackroomsLevels.isInBackrooms(player.getWorld().getRegistryKey())) {
            // The group left the level this player died in and took them along: they are back.
            revive(player);
            return;
        }

        // Vanilla already detaches the camera when its entity dies; this catches that and every
        // other way the target can stop being valid, on the same tick.
        if (isValidTarget(player, player.getCameraEntity())) {
            STRANDED.remove(player.getUuid());
            return;
        }

        if (attachToNearest(player)) {
            STRANDED.remove(player.getUuid());
            return;
        }

        holdStranded(player);
    }

    /**
     * A ghost with nobody left in their level. If the run is still going on somewhere else they
     * are sent after it — arriving in another level is what revives them. Otherwise they are
     * pinned where they are, because the alternative is handing them a free camera.
     */
    private static void holdStranded(ServerPlayerEntity player) {
        ServerWorld destination = levelWithSurvivors(player);
        if (destination != null) {
            BackroomsLevels.getLevel(destination).ifPresent(level ->
                    FabricDimensions.teleport(player, destination,
                            new TeleportTarget(level.getSpawnPos(), Vec3d.ZERO, player.getYaw(), player.getPitch())));
            STRANDED.remove(player.getUuid());
            return;
        }

        Vec3d anchor = STRANDED.computeIfAbsent(player.getUuid(), id -> player.getPos());
        if (player.squaredDistanceTo(anchor) > 0.01) {
            player.networkHandler.requestTeleport(anchor.x, anchor.y, anchor.z, player.getYaw(), player.getPitch());
        }
    }

    /** Another backrooms level that still has somebody playing in it, if there is one. */
    @Nullable
    private static ServerWorld levelWithSurvivors(ServerPlayerEntity ghost) {
        for (BackroomsLevel level : BackroomsLevels.BACKROOMS_LEVELS) {
            if (!BackroomsLevels.isInBackrooms(level.getWorldKey())) {
                continue;
            }
            ServerWorld candidate = ghost.server.getWorld(level.getWorldKey());
            if (candidate == null || candidate == ghost.getWorld()) {
                continue;
            }
            for (PlayerEntity player : candidate.getPlayers()) {
                if (player.isAlive() && !player.isSpectator()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Whether anyone in this player's level could actually come and pick them up. */
    private static boolean hasPossibleRescuer(ServerPlayerEntity downed) {
        for (PlayerEntity candidate : downed.getWorld().getPlayers()) {
            if (candidate != downed
                    && candidate.isAlive()
                    && !candidate.isSpectator()
                    && !Revival.isDowned(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Moves a ghost's view to the next (or previous) teammate. */
    public static void cycle(ServerPlayerEntity player, int delta) {
        PlayerComponent component = InitializeComponents.PLAYER.get(player);
        if (!component.isGhost()) {
            return;
        }

        List<ServerPlayerEntity> targets = targets(player);
        if (targets.isEmpty()) {
            return;
        }

        int current = targets.indexOf(player.getCameraEntity());
        int next = current < 0 ? 0 : Math.floorMod(current + delta, targets.size());
        player.setCameraEntity(targets.get(next));
    }

    /** Moves a ghost's view to the nth teammate, for the number keys. */
    public static void select(ServerPlayerEntity player, int index) {
        PlayerComponent component = InitializeComponents.PLAYER.get(player);
        if (!component.isGhost()) {
            return;
        }

        List<ServerPlayerEntity> targets = targets(player);
        if (index >= 0 && index < targets.size()) {
            player.setCameraEntity(targets.get(index));
        }
    }

    /** @return whether a target was found. */
    private static boolean attachToNearest(ServerPlayerEntity player) {
        ServerPlayerEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ServerPlayerEntity candidate : targets(player)) {
            double distance = candidate.squaredDistanceTo(player);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        if (nearest == null) {
            return false;
        }
        player.setCameraEntity(nearest);
        return true;
    }

    /**
     * Who a ghost may watch: players still in the run, in this level. Downed players count —
     * watching a teammate bleed out is the point — but other ghosts and skinwalker captives
     * (both spectators) do not.
     */
    private static List<ServerPlayerEntity> targets(ServerPlayerEntity player) {
        List<ServerPlayerEntity> targets = new ArrayList<>();
        // Player-list order, so "next" is stable and predictable rather than shuffling around.
        for (ServerPlayerEntity candidate : player.server.getPlayerManager().getPlayerList()) {
            if (isValidTarget(player, candidate)) {
                targets.add(candidate);
            }
        }
        return targets;
    }

    private static boolean isValidTarget(ServerPlayerEntity ghost, @Nullable Entity candidate) {
        return candidate instanceof PlayerEntity
                && candidate != ghost
                && candidate.isAlive()
                && !((PlayerEntity) candidate).isSpectator()
                && candidate.getWorld() == ghost.getWorld();
    }
}
