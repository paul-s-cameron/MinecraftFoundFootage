package com.sp.ghost;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.networking.C2S.GhostCameraSync;
import com.sp.networking.InitializePackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.network.PacketByteBuf;

/**
 * A ghost's controls: click to change who you are watching, number keys to jump straight to
 * someone. The client only ever asks — {@link GhostManager} decides.
 */
public final class GhostCameraClient {
    private GhostCameraClient() {
    }

    public static boolean isGhost(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        PlayerComponent component = InitializeComponents.PLAYER.get(client.player);
        return component.isGhost();
    }

    /**
     * Cancelling doItemUse skips vanilla's own use cooldown, so without this a held right click
     * would cycle the camera twenty times a second.
     */
    private static final int CYCLE_COOLDOWN_TICKS = 5;
    private static int cooldown = 0;

    /** Called every client tick while the local player is a ghost. */
    public static void tick(MinecraftClient client) {
        if (cooldown > 0) {
            cooldown--;
        }

        // Third person would show the teammate being watched from behind, which sees around
        // corners they cannot. A ghost gets their feed, nothing more.
        if (client.options.getPerspective() != Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }

    public static void cycle(int delta) {
        if (cooldown > 0) {
            return;
        }
        cooldown = CYCLE_COOLDOWN_TICKS;
        send(GhostCameraSync.CYCLE, delta);
    }

    public static void select(int index) {
        send(0, index);
    }

    private static void send(int mode, int value) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(mode);
        buffer.writeInt(value);
        ClientPlayNetworking.send(InitializePackets.GHOST_CAMERA, buffer);
    }
}
