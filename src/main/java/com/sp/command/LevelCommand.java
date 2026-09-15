package com.sp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.levels.BackroomsLevel;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

import java.util.List;
import java.util.Optional;

/**
 * {@code /level <id>} — moves <b>everyone</b> to a level's spawn.
 *
 * <p>Deliberately not just whoever ran it. The whole game is built on the group being in one level
 * at a time — rallies exist so the level departs together, joins put a late arrival with the
 * others, and a player left behind in a level nobody else is in has no way back. A debug teleport
 * that could strand somebody would be undoing that by hand.
 *
 * <p>It also gathers: players already in the target level are moved to its spawn too, so this
 * doubles as a way to pull a scattered group back together.
 */
public class LevelCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("level")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("level", StringArgumentType.word()).suggests(
                                (context, builder) -> {
                                    for (BackroomsLevel backroomsLevel : BackroomsLevels.BACKROOMS_LEVELS) {
                                        builder.suggest(backroomsLevel.getLevelId());
                                    }
                                    return builder.buildFuture();
                                }
                        ).executes(context -> moveEveryone(
                                context.getSource(),
                                StringArgumentType.getString(context, "level"))))
        );
    }

    private static int moveEveryone(ServerCommandSource source, String levelId) {
        Optional<BackroomsLevel> optional = BackroomsLevels.getById(levelId);
        if (optional.isEmpty()) {
            source.sendFeedback(() -> Text.translatable("spb-revamped.level.command.unknown", levelId), false);
            return 0;
        }

        BackroomsLevel level = optional.get();
        ServerWorld destination = source.getServer().getWorld(level.getWorldKey());
        if (destination == null) {
            source.sendFeedback(() -> Text.translatable("spb-revamped.level.command.unloaded", levelId), false);
            return 0;
        }

        int moved = 0;
        // Copied: teleporting across dimensions replaces the entity and mutates the player list.
        for (ServerPlayerEntity player : List.copyOf(source.getServer().getPlayerManager().getPlayerList())) {
            clearPendingTransition(player);
            FabricDimensions.teleport(player, destination,
                    new TeleportTarget(level.getSpawnPos(), Vec3d.ZERO, 0, 90));
            moved++;
        }

        int count = moved;
        source.sendFeedback(() -> Text.translatable("spb-revamped.level.command.moved",
                String.valueOf(count), levelId), true);
        return moved;
    }

    /**
     * A level transition already in flight would finish after this teleport and drag the player
     * straight back out of the level they were just put in.
     */
    private static void clearPendingTransition(ServerPlayerEntity player) {
        PlayerComponent component = InitializeComponents.PLAYER.get(player);
        component.currentTransition = null;
        component.setTeleportingTimer(-1);
        component.setTeleporting(false);
        component.setShouldNoClip(false);
        component.sync();
    }
}
