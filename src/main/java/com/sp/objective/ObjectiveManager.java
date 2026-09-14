package com.sp.objective;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.RallyComponent;
import com.sp.init.BackroomsLevels;
import com.sp.networking.InitializePackets;
import com.sp.world.levels.BackroomsLevel;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Works out each player's objective and syncs it when it changes.
 *
 * <p>Recomputed every tick and compared rather than pushed from the events that could change it.
 * There are a lot of those — arriving in a level, a rally opening, someone reaching it, the rally
 * expiring, dying, being carried along as a ghost — and one of them being forgotten is a line that
 * silently says the wrong thing. Comparing is a handful of field reads.
 */
public final class ObjectiveManager {

    /** The rally line is shared by every level; the level only names what you are looking for. */
    private static final String RALLY_KEY = "objective." + SPBRevamped.MOD_ID + ".rally";

    /**
     * What each player was last sent, so nothing goes out while nothing is changing. Server-side
     * and transient: on a fresh join there is no entry, so the first tick always syncs.
     */
    private static final Map<UUID, Objective> LAST_SENT = new HashMap<>();

    private ObjectiveManager() {
    }

    public static void tick(ServerPlayerEntity player) {
        Objective current = compute(player);
        if (current.equals(LAST_SENT.get(player.getUuid()))) {
            return;
        }
        LAST_SENT.put(player.getUuid(), current);
        send(player, current);
    }

    /**
     * The write half of the objective packet. Here rather than beside the read half, because this
     * runs on a dedicated server and everything in {@code networking.S2C} reaches for
     * {@code MinecraftClient} — the same split the rest of the mod's packets use.
     */
    private static void send(ServerPlayerEntity player, Objective objective) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(objective.key());

        Vec3d target = objective.target();
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeDouble(target.x);
            buf.writeDouble(target.y);
            buf.writeDouble(target.z);
        }

        buf.writeLong(objective.deadlineTick());
        buf.writeVarInt(objective.present());
        buf.writeVarInt(objective.total());
        ServerPlayNetworking.send(player, InitializePackets.OBJECTIVE, buf);
    }

    /** Called on disconnect: without it the map grows for the life of the server. */
    public static void forget(UUID playerId) {
        LAST_SENT.remove(playerId);
    }

    private static Objective compute(ServerPlayerEntity player) {
        if (!BackroomsLevels.isInBackrooms(player.getWorld().getRegistryKey())) {
            return Objective.NONE;
        }

        Optional<BackroomsLevel> level = BackroomsLevels.getLevel(player.getWorld());
        if (level.isEmpty()) {
            return Objective.NONE;
        }

        RallyComponent rally = InitializeComponents.RALLY.get(player.getWorld());
        if (!rally.isActive()) {
            return new Objective(searchKey(level.get()), null, 0L, 0, 0);
        }

        // The denominator is who the rally would actually wait for, not who is online: a player in
        // another level is not late, they are somewhere else.
        return new Objective(RALLY_KEY, rally.getRallyPos(), rally.getDeadline(),
                rally.getPresent().size(), rally.countedTotal());
    }

    private static String searchKey(BackroomsLevel level) {
        return "objective." + SPBRevamped.MOD_ID + "." + level.getLevelId();
    }
}
