package com.sp.networking.C2S;

import com.sp.ghost.GhostManager;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * A ghost asking to look through someone else. Negative values step backwards through the
 * teammate list, positive step forwards, and anything else is an absolute index from the number
 * keys. The server decides what the request actually resolves to; the client never picks a
 * camera itself.
 */
public class GhostCameraSync {
    /** Marks the payload as "move by this many", rather than "go to this index". */
    public static final int CYCLE = Integer.MIN_VALUE;

    public static void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
                               PacketByteBuf buf, PacketSender responseSender) {
        int mode = buf.readInt();
        int value = buf.readInt();

        server.execute(() -> {
            if (mode == CYCLE) {
                GhostManager.cycle(player, value);
            } else {
                GhostManager.select(player, value);
            }
        });
    }
}
