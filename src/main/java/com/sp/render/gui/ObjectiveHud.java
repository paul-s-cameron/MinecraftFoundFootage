package com.sp.render.gui;

import com.sp.SPBRevampedClient;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.compat.modmenu.ConfigStuff;
import com.sp.objective.ClientObjective;
import com.sp.objective.Objective;
import com.sp.render.camera.CutsceneManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * The objective line: a camcorder on-screen display, not a quest tracker.
 *
 * <p>The whole mod is shot through a handheld camera, so this is text that camera would burn in —
 * one line, top left, no box and no icons.
 *
 * <p>Distance and countdown are recomputed here every frame from the target and deadline the
 * server sent once, which is why a rally does not cost a packet a second.
 */
public class ObjectiveHud implements HudRenderCallback {

    private static final int MARGIN = 8;
    /** Off-white at ~80% alpha: burned into the picture rather than sitting on top of it. */
    private static final int COLOR = 0xCCE8E4C9;
    /** Escaped rather than literal, so the source file's encoding cannot mangle them. */
    private static final String RECORD_DOT = "● ";
    private static final String SEPARATOR = " · ";

    @Override
    public void onHudRender(DrawContext drawContext, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ConfigStuff.showObjective || client.player == null || client.world == null) {
            return;
        }
        if (client.options.hudHidden) {
            return;
        }

        Objective objective = ClientObjective.get();
        if (objective.isEmpty()) {
            return;
        }

        // Nothing is burned in over a black screen or the falling cutscene — both are moments the
        // camera is not showing the room, and a line of text sitting on them breaks the shot.
        CutsceneManager cutscenes = SPBRevampedClient.getCutsceneManager();
        if (cutscenes.blackScreen.isBlackScreen) {
            return;
        }
        if (InitializeComponents.PLAYER.get(client.player).isDoingCutscene()) {
            return;
        }

        drawContext.drawText(client.textRenderer, line(client, objective), MARGIN, MARGIN, COLOR, true);
    }

    private static Text line(MinecraftClient client, Objective objective) {
        MutableText text = Text.literal(RECORD_DOT).append(Text.translatable(objective.key()));

        if (objective.target() != null) {
            long metres = Math.round(client.player.getPos().distanceTo(objective.target()));
            text.append(Text.literal(SEPARATOR + metres + "m"));
        }
        if (objective.hasTimer()) {
            text.append(Text.literal(SEPARATOR + formatTime(secondsLeft(client, objective))));
        }
        if (objective.hasPresence()) {
            text.append(Text.literal(SEPARATOR + objective.present() + "/" + objective.total()));
        }
        return text;
    }

    private static int secondsLeft(MinecraftClient client, Objective objective) {
        long remaining = objective.deadlineTick() - client.world.getTime();
        return (int) Math.max(0L, remaining / 20L);
    }

    private static String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
