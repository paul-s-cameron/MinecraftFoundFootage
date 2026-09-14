package com.sp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.RallyComponent;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * {@code /rally force|cancel|status} — a way out when a rally will not resolve on its own,
 * and the readout needed to test one.
 */
public class RallyCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("rally")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("force").executes(context -> {
                    ServerWorld world = context.getSource().getWorld();
                    if (rallyOf(world).forceDepart()) {
                        context.getSource().sendFeedback(() -> Text.translatable("spb-revamped.rally.command.forced"), true);
                        return 1;
                    }
                    context.getSource().sendFeedback(() -> Text.translatable("spb-revamped.rally.command.none"), false);
                    return 0;
                }))
                .then(CommandManager.literal("cancel").executes(context -> {
                    ServerWorld world = context.getSource().getWorld();
                    if (rallyOf(world).cancel()) {
                        context.getSource().sendFeedback(() -> Text.translatable("spb-revamped.rally.command.cancelled"), true);
                        return 1;
                    }
                    context.getSource().sendFeedback(() -> Text.translatable("spb-revamped.rally.command.none"), false);
                    return 0;
                }))
                .then(CommandManager.literal("status").executes(context -> {
                    ServerWorld world = context.getSource().getWorld();
                    RallyComponent rally = rallyOf(world);

                    if (!rally.isActive()) {
                        context.getSource().sendFeedback(() -> Text.translatable("spb-revamped.rally.command.none"), false);
                        return 0;
                    }

                    String position = String.format("%.1f %.1f %.1f",
                            rally.getRallyPos().x, rally.getRallyPos().y, rally.getRallyPos().z);
                    String secondsLeft = String.valueOf(Math.max(0, (rally.getDeadline() - world.getTime()) / 20));
                    String here = String.valueOf(rally.getPresent().size());

                    context.getSource().sendFeedback(() -> Text.translatable(
                            "spb-revamped.rally.command.status", position, here, secondsLeft), false);
                    return 1;
                })));
    }

    private static RallyComponent rallyOf(ServerWorld world) {
        return InitializeComponents.RALLY.get(world);
    }
}
