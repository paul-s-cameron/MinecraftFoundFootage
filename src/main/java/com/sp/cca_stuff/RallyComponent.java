package com.sp.cca_stuff;

import com.sp.init.BackroomsLevels;
import com.sp.world.levels.BackroomsLevel;
import dev.onyxstudios.cca.api.v3.component.Component;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Makes a level's exits something the group leaves through together.
 *
 * <p>The first player to reach an exit opens a <i>rally</i> at their position instead of
 * teleporting. Everyone else is told where it is and how long they have; the level departs — all
 * players on the same tick, with the same fade — once everyone has gathered there, or once the
 * countdown expires. Expiry is what keeps a lost or dead player from stalling the run: the level
 * always leaves, it just prefers to leave together.
 *
 * <p>One rally per level at a time. A second exit found while one is open is ignored, so the
 * group cannot be pulled in two directions.
 *
 * <p>This is server-only state and is deliberately <b>not</b> an {@code AutoSyncedComponent}:
 * broadcasting it would hand every client the exit's exact coordinates, when the whole point of
 * the readout is that players only get a distance. A purpose-built packet carrying just what a
 * given player may see comes with the objective HUD.
 */
public class RallyComponent implements Component, ServerTickingComponent {
    /** How often the "regroup" readout is refreshed. Action bar text lasts far longer than this. */
    private static final int STATUS_INTERVAL_TICKS = 10;
    /** How long an operator's {@code /rally cancel} suppresses the exit that was just cancelled. */
    private static final int CANCEL_COOLDOWN_TICKS = 200;
    /** Leaving the rally needs a little more than arriving, so pacing the edge does not flicker. */
    private static final double PRESENCE_HYSTERESIS = 1.5;

    private final World world;

    private String ruleId = null;
    private Vec3d rallyPos = null;
    private UUID openedBy = null;
    /** World time at which the level leaves regardless of who made it. */
    private long deadline = 0L;
    private Set<UUID> present = new HashSet<>();

    /**
     * Whether anyone has been in the level since this rally was loaded. A rally restored from disk
     * must not be thrown away on the first tick after a restart, when nobody has reconnected yet.
     */
    private boolean seenPlayers = false;
    private long reopenBlockedUntil = 0L;

    public RallyComponent(World world) {
        this.world = world;
    }

    // --- queries -------------------------------------------------------------------------

    public boolean isActive() {
        return this.ruleId != null && this.rallyPos != null;
    }

    @Nullable
    public Vec3d getRallyPos() {
        return this.rallyPos;
    }

    @Nullable
    public UUID getOpenedBy() {
        return this.openedBy;
    }

    public long getDeadline() {
        return this.deadline;
    }

    public Set<UUID> getPresent() {
        return this.present;
    }

    // --- driving -------------------------------------------------------------------------

    /**
     * Called for each player from {@link PlayerComponent#serverTick()}. Opens a rally when
     * someone reaches an exit; while one is open, presence is handled by {@link #serverTick()}
     * instead, so this does nothing.
     */
    public void onPlayerTick(BackroomsLevel level, PlayerComponent playerComponent) {
        if (this.world.isClient() || playerComponent.currentTransition != null) {
            return;
        }

        if (!level.hasExitRules()) {
            // Levels whose exits have not been migrated keep the original per-player behaviour.
            List<BackroomsLevel.LevelTransition> teleports = level.checkForTransition(playerComponent, this.world);
            if (!teleports.isEmpty()) {
                assign(playerComponent, teleports.get(0));
            }
            return;
        }

        if (this.isActive() || this.world.getTime() < this.reopenBlockedUntil) {
            return;
        }
        // Stricter than canTravel: a ghost travels with the group but is dead and cannot be
        // credited with finding the way out.
        if (!playerComponent.player.isAlive() || playerComponent.player.isSpectator()) {
            return;
        }

        for (BackroomsLevel.ExitRule rule : level.getExitRules()) {
            if (rule.condition().test(this.world, playerComponent)) {
                this.open(rule, playerComponent);
                return;
            }
        }
    }

    @Override
    public void serverTick() {
        if (this.world.isClient() || !this.isActive()) {
            return;
        }
        if (!BackroomsLevels.isInBackrooms(this.world.getRegistryKey())) {
            this.clear();
            return;
        }

        BackroomsLevel.ExitRule rule = this.activeRule();
        if (rule == null) {
            this.clear();
            return;
        }

        List<ServerPlayerEntity> counted = this.countedPlayers();
        if (counted.isEmpty()) {
            // Only give up once there has actually been somebody here: right after a restart the
            // level is empty for a while before anyone reconnects.
            if (this.seenPlayers) {
                this.clear();
            }
            return;
        }
        this.seenPlayers = true;

        this.updatePresence(counted, rule);

        if (this.present.size() >= counted.size() || this.world.getTime() >= this.deadline) {
            this.depart(rule);
        } else {
            this.showStatus(counted);
        }
    }

    private void open(BackroomsLevel.ExitRule rule, PlayerComponent opener) {
        this.ruleId = rule.id();
        this.rallyPos = opener.player.getPos();
        this.openedBy = opener.player.getUuid();
        this.deadline = this.world.getTime() + rule.policy().countdownTicks();
        this.present = new HashSet<>();
        this.seenPlayers = true;

        Text name = opener.player.getName();
        for (ServerPlayerEntity player : this.serverPlayers()) {
            player.sendMessage(Text.translatable("spb-revamped.rally.found", name), false);
        }
    }

    /**
     * Departs immediately, ignoring the countdown and whoever is missing.
     *
     * @return false if there was no rally to depart.
     */
    public boolean forceDepart() {
        if (this.world.isClient() || !this.isActive()) {
            return false;
        }
        BackroomsLevel.ExitRule rule = this.activeRule();
        if (rule == null) {
            this.clear();
            return false;
        }
        this.depart(rule);
        return true;
    }

    /** @return false if there was no rally to cancel. */
    public boolean cancel() {
        if (!this.isActive()) {
            return false;
        }
        // Without this the exit condition is still true for whoever is standing in it, and the
        // next tick simply opens the same rally again with a fresh countdown.
        this.reopenBlockedUntil = this.world.getTime() + CANCEL_COOLDOWN_TICKS;
        this.clear();
        return true;
    }

    private void depart(BackroomsLevel.ExitRule rule) {
        Vec3d pos = this.rallyPos;

        for (PlayerEntity player : List.copyOf(this.world.getPlayers())) {
            // Exactly the players the rally was waiting for. Taking anyone else would move
            // someone the group never gathered for — a dead player cannot be teleported between
            // dimensions safely, and a spectator here is usually mid-skinwalker-capture, whose
            // state only unwinds while they are still in this level.
            if (!canTravel(player)) {
                continue;
            }
            PlayerComponent playerComponent = InitializeComponents.PLAYER.get(player);
            if (playerComponent.currentTransition != null) {
                continue;
            }
            assign(playerComponent, rule.transition().create(playerComponent, pos));
        }

        this.clear();
    }

    /**
     * Starts a transition. The timer is set here rather than left for
     * {@link PlayerComponent#serverTick()} to initialise, because a timer left over from an
     * interrupted transition (it is saved with the player, the transition is not) would otherwise
     * cut the new fade short or skip it entirely.
     */
    private static void assign(PlayerComponent playerComponent, BackroomsLevel.LevelTransition transition) {
        playerComponent.currentTransition = transition;
        playerComponent.setTeleportingTimer(transition.duration());
    }

    private void updatePresence(List<ServerPlayerEntity> counted, BackroomsLevel.ExitRule rule) {
        Set<UUID> here = new HashSet<>();
        double arriveSquared = rule.policy().radiusSquared();
        double leaveRadius = rule.policy().radius() + PRESENCE_HYSTERESIS;
        double leaveSquared = leaveRadius * leaveRadius;

        for (ServerPlayerEntity player : counted) {
            if (!canTravel(player)) {
                continue;
            }
            double distanceSquared = player.getPos().squaredDistanceTo(this.rallyPos);
            boolean wasHere = this.present.contains(player.getUuid());
            if (distanceSquared <= (wasHere ? leaveSquared : arriveSquared)) {
                here.add(player.getUuid());
            }
        }

        this.present = here;
    }

    private void showStatus(List<ServerPlayerEntity> counted) {
        if (this.world.getTime() % STATUS_INTERVAL_TICKS != 0) {
            return;
        }
        String remaining = formatTime((int) Math.max(0, (this.deadline - this.world.getTime()) / 20));
        String total = String.valueOf(counted.size());
        String here = String.valueOf(this.present.size());

        for (ServerPlayerEntity player : counted) {
            Text text;
            if (this.present.contains(player.getUuid())) {
                text = Text.translatable("spb-revamped.rally.waiting", here, total, remaining);
            } else {
                int distance = (int) Math.round(Math.sqrt(player.getPos().squaredDistanceTo(this.rallyPos)));
                text = Text.translatable("spb-revamped.rally.regroup", distance, here, total, remaining);
            }
            // Action bar rather than chat: a player with chat hidden never sees chat messages.
            player.sendMessage(text, true);
        }
    }

    @Nullable
    private BackroomsLevel.ExitRule activeRule() {
        BackroomsLevel level = BackroomsLevels.getLevel(this.world).orElse(null);
        return level == null ? null : level.getExitRule(this.ruleId);
    }

    /**
     * Who the level waits for: exactly the players it would actually take, minus the ghosts it
     * takes as passengers. A dead player is excluded because departure cannot move them — and it
     * no longer needs to wait for one, since they become a ghost within a few seconds and ride
     * along regardless.
     */
    private List<ServerPlayerEntity> countedPlayers() {
        List<ServerPlayerEntity> counted = new ArrayList<>();
        for (ServerPlayerEntity player : this.serverPlayers()) {
            if (player.isAlive() && !player.isSpectator()) {
                counted.add(player);
            }
        }
        return counted;
    }

    private List<ServerPlayerEntity> serverPlayers() {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (PlayerEntity player : this.world.getPlayers()) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                players.add(serverPlayer);
            }
        }
        return players;
    }

    /** Whether this player can actually be moved to the next level right now. */
    private static boolean canTravel(PlayerEntity player) {
        if (!player.isAlive()) {
            return false;
        }
        if (!player.isSpectator()) {
            return true;
        }
        // Ghosts are the deliberate exception to "take exactly who you waited for": they are
        // dead players riding along with the group, and arriving is what revives them.
        return InitializeComponents.PLAYER.get(player).isGhost();
    }

    private static String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void clear() {
        this.ruleId = null;
        this.rallyPos = null;
        this.openedBy = null;
        this.deadline = 0L;
        this.present = new HashSet<>();
    }

    // --- persistence ----------------------------------------------------------------------

    @Override
    public void readFromNbt(NbtCompound tag) {
        this.clear();
        this.seenPlayers = false;

        if (!tag.contains("rallyRule", NbtElement.STRING_TYPE)) {
            return;
        }

        this.ruleId = tag.getString("rallyRule");
        this.rallyPos = new Vec3d(tag.getDouble("rallyX"), tag.getDouble("rallyY"), tag.getDouble("rallyZ"));
        this.openedBy = tag.containsUuid("rallyOpenedBy") ? tag.getUuid("rallyOpenedBy") : null;
        this.deadline = tag.getLong("rallyDeadline");

        NbtList list = tag.getList("rallyPresent", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            try {
                this.present.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // A malformed entry just means that player is treated as not yet arrived.
            }
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        if (!this.isActive()) {
            return;
        }

        tag.putString("rallyRule", this.ruleId);
        tag.putDouble("rallyX", this.rallyPos.x);
        tag.putDouble("rallyY", this.rallyPos.y);
        tag.putDouble("rallyZ", this.rallyPos.z);
        if (this.openedBy != null) {
            tag.putUuid("rallyOpenedBy", this.openedBy);
        }
        tag.putLong("rallyDeadline", this.deadline);

        NbtList list = new NbtList();
        for (UUID id : this.present) {
            list.add(NbtString.of(id.toString()));
        }
        tag.put("rallyPresent", list);
    }
}
