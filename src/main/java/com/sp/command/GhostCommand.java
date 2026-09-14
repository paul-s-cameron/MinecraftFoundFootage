package com.sp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.sp.compat.hardcorerevival.Revival;
import com.sp.ghost.GhostManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

/**
 * {@code /ghost revive|status} — the two things testing the downed and ghost states needs that
 * the game cannot otherwise do: put someone back on their feet, and say what state they are in.
 *
 * <p>Deliberately <i>not</i> {@code /revive}. Hardcore Revival already registers that name, and
 * its version only wakes a knocked-out player — it knows nothing about ghosts. Registering the
 * same literal would leave one silently shadowing the other depending on mod load order.
 *
 * <p>Its {@code /knockout <targets>} is the matching way in, so there is no reason to add one
 * here; {@code /kill} skips the downed state entirely and goes straight to a ghost, because
 * {@code minecraft:generic_kill} is in our {@code bypasses_knockout} tag.
 */
public class GhostCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("ghost")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("revive")
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .executes(context -> revive(context.getSource(),
                                        EntityArgumentType.getPlayers(context, "targets")))))
                .then(CommandManager.literal("status")
                        .executes(context -> status(context.getSource(),
                                context.getSource().getServer().getPlayerManager().getPlayerList()))
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .executes(context -> status(context.getSource(),
                                        EntityArgumentType.getPlayers(context, "targets"))))));
    }

    private static int revive(ServerCommandSource source, Collection<ServerPlayerEntity> targets) {
        int count = 0;

        for (ServerPlayerEntity target : targets) {
            ServerPlayerEntity player = target;
            boolean wasDead = !player.isAlive();

            if (wasDead) {
                // A dead player cannot be stood back up in place — respawnPlayer replaces the
                // entity. In a backrooms level that respawn is itself what makes them a ghost,
                // so the ghost branch below is what actually finishes the job.
                player = source.getServer().getPlayerManager().respawnPlayer(player, true);
            }

            GhostManager.ReviveResult result = GhostManager.forceRevive(player);
            String key = wasDead ? "dead" : switch (result) {
                case GHOST -> "ghost";
                case DOWNED -> "downed";
                case ALREADY_UP -> "up";
            };

            String name = player.getEntityName();
            source.sendFeedback(() -> Text.translatable("spb-revamped.ghost.command.revived." + key, name), true);
            count++;
        }

        return count;
    }

    private static int status(ServerCommandSource source, Collection<ServerPlayerEntity> targets) {
        for (ServerPlayerEntity player : targets) {
            String name = player.getEntityName();

            if (!player.isAlive()) {
                source.sendFeedback(() -> Text.translatable("spb-revamped.ghost.command.status.dead", name), false);
            } else if (GhostManager.isGhost(player)) {
                PlayerEntity watched = GhostManager.watching(player);
                source.sendFeedback(() -> watched == null
                        ? Text.translatable("spb-revamped.ghost.command.status.stranded", name)
                        : Text.translatable("spb-revamped.ghost.command.status.ghost", name, watched.getEntityName()), false);
            } else if (Revival.isDowned(player)) {
                source.sendFeedback(() -> Text.translatable("spb-revamped.ghost.command.status.downed", name), false);
            } else if (player.isSpectator()) {
                // Not a ghost but spectating: a skinwalker has them, or something has gone wrong.
                source.sendFeedback(() -> Text.translatable("spb-revamped.ghost.command.status.spectating", name), false);
            } else {
                String health = String.valueOf(Math.round(player.getHealth()));
                source.sendFeedback(() -> Text.translatable("spb-revamped.ghost.command.status.up", name, health), false);
            }
        }

        return targets.size();
    }
}
