package com.maxonaddon.command;

import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.ReputationHelper;
import com.maxonaddon.util.Wall;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Locale;

/**
 * Админ-команды для ручной выдачи/установки репутации у стены (см. Wall/ReputationHelper).
 * /reputation set <стена> <0..100> [игрок]
 * /reputation add <стена> <-100..100> [игрок]
 * /reputation get <стена> [игрок]
 */
public class ReputationCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reputation")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("wall", StringArgumentType.word())
                                .suggests(ReputationCommand::suggestWalls)
                                .then(Commands.argument("amount", IntegerArgumentType.integer(ReputationHelper.MIN, ReputationHelper.MAX))
                                        .executes(ctx -> setRep(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> setRep(ctx, EntityArgument.getPlayer(ctx, "target")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("add")
                        .then(Commands.argument("wall", StringArgumentType.word())
                                .suggests(ReputationCommand::suggestWalls)
                                .then(Commands.argument("amount", IntegerArgumentType.integer(-ReputationHelper.MAX, ReputationHelper.MAX))
                                        .executes(ctx -> addRep(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> addRep(ctx, EntityArgument.getPlayer(ctx, "target")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("get")
                        .then(Commands.argument("wall", StringArgumentType.word())
                                .suggests(ReputationCommand::suggestWalls)
                                .executes(ctx -> getRep(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> getRep(ctx, EntityArgument.getPlayer(ctx, "target")))
                                )
                        )
                )
        );
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestWalls(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                Arrays.stream(Wall.values()).map(w -> w.name().toLowerCase(Locale.ROOT)),
                builder
        );
    }

    private static Wall parseWall(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "wall");
        try {
            return Wall.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int setRep(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        Wall wall = parseWall(ctx);
        if (wall == null) {
            ctx.getSource().sendFailure(invalidWallMessage());
            return 0;
        }

        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ((IPlayerStats) target).setReputation(wall, amount);
        int result = ((IPlayerStats) target).getReputation(wall);

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.maxonaddon.reputation.set",
                wall.nameRu, target.getName(), result
        ), true);
        return result;
    }

    private static int addRep(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        Wall wall = parseWall(ctx);
        if (wall == null) {
            ctx.getSource().sendFailure(invalidWallMessage());
            return 0;
        }

        // addReputation при выключенной системе молча ничего не делает (см. PlayerStatsMixin) —
        // без этой проверки команда отрапортовала бы об успехе, ничего не изменив.
        if (!com.maxonaddon.MaxonConfig.isReputationEnabled()) {
            ctx.getSource().sendFailure(Component.translatable("command.maxonaddon.reputation.disabled"));
            return 0;
        }

        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ((IPlayerStats) target).addReputation(wall, amount);
        int result = ((IPlayerStats) target).getReputation(wall);

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.maxonaddon.reputation.add",
                amount, wall.nameRu, target.getName(), result
        ), true);
        return result;
    }

    private static int getRep(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        Wall wall = parseWall(ctx);
        if (wall == null) {
            ctx.getSource().sendFailure(invalidWallMessage());
            return 0;
        }

        int result = ((IPlayerStats) target).getReputation(wall);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.maxonaddon.reputation.get",
                target.getName(), wall.nameRu, result
        ), false);
        return result;
    }

    private static Component invalidWallMessage() {
        StringBuilder names = new StringBuilder();
        for (Wall wall : Wall.values()) {
            if (names.length() > 0) names.append(", ");
            names.append(wall.name().toLowerCase(Locale.ROOT));
        }
        return Component.translatable("command.maxonaddon.reputation.invalid_wall", names.toString());
    }
}
