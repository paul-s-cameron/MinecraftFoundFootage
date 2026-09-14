package com.sp.networking.S2C;

import com.sp.objective.ClientObjective;
import com.sp.objective.Objective;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;

/**
 * Receive half only, like the rest of this package — the write lives in {@code ObjectiveManager},
 * so nothing a dedicated server touches can pull {@code MinecraftClient} in behind it.
 */
public class ObjectivePacket {

    /** Longest objective key we will read, so a malformed packet cannot allocate freely. */
    public static final int MAX_KEY_LENGTH = 128;

    public static void receive(MinecraftClient client, ClientPlayNetworkHandler handler,
                               PacketByteBuf buf, PacketSender responseSender) {
        // Drained here rather than inside execute(): the buffer is released as soon as this
        // returns, which is well before that task runs.
        String key = buf.readString(MAX_KEY_LENGTH);

        Vec3d target = null;
        if (buf.readBoolean()) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            target = new Vec3d(x, y, z);
        }

        long deadlineTick = buf.readLong();
        int present = buf.readVarInt();
        int total = buf.readVarInt();

        Objective objective = new Objective(key, target, deadlineTick, present, total);
        client.execute(() -> ClientObjective.set(objective));
    }
}
