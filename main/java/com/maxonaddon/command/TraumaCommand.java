package com.maxonaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.maxonaddon.util.TraumaManager;
import com.maxonaddon.util.TraumaType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TraumaCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("trauma")
                .requires(source -> source.hasPermission(2))
                // Команда: /trauma add <игрок> <тип_травмы>
                .then(Commands.literal("add")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(getTraumaSuggestions(false), builder))
                                        .executes(TraumaCommand::addTrauma)
                                )
                        )
                )
                // Команда: /trauma heal <игрок> <тип_травмы_или_ALL>
                .then(Commands.literal("heal")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(getTraumaSuggestions(true), builder))
                                        .executes(TraumaCommand::healTrauma)
                                )
                        )
                )
        );
    }

    private static List<String> getTraumaSuggestions(boolean includeAll) {
        List<String> suggestions = Arrays.stream(TraumaType.values()).map(Enum::name).collect(Collectors.toList());
        if (includeAll) {
            suggestions.add("ALL");
        }
        return suggestions;
    }

    private static int addTrauma(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "target");
        String typeStr = StringArgumentType.getString(context, "type").toUpperCase();

        try {
            TraumaType type = TraumaType.valueOf(typeStr);
            // Вызываем удар монстра!
            TraumaManager.applyMonsterTrauma(player, type);
            context.getSource().sendSuccess(() -> Component.literal("§aТравма " + type + " нанесена игроку " + player.getName().getString() + " по правилам монстра."), true);
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("§cНеизвестный тип травмы. Используйте: HEAD, BODY, ARMS, LEGS"));
        }
        return 1;
    }

    private static int healTrauma(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "target");
        String typeStr = StringArgumentType.getString(context, "type").toUpperCase();

        if (typeStr.equals("ALL")) {
            for (TraumaType type : TraumaType.values()) {
                TraumaManager.healTrauma(player, type);
                TraumaManager.syncTrauma(player, type); // Синхронизируем исцеление
            }
            context.getSource().sendSuccess(() -> Component.literal("§aВсе травмы игрока " + player.getName().getString() + " исцелены."), true);
        } else {
            try {
                TraumaType type = TraumaType.valueOf(typeStr);
                TraumaManager.healTrauma(player, type);
                TraumaManager.syncTrauma(player, type); // Синхронизируем исцеление
                context.getSource().sendSuccess(() -> Component.literal("§aТравма " + type + " исцелена у " + player.getName().getString()), true);
            } catch (IllegalArgumentException e) {
                context.getSource().sendFailure(Component.literal("§cНеизвестный тип травмы."));
            }
        }
        return 1;
    }
}