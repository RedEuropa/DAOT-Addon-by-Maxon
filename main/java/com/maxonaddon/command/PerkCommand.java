package com.maxonaddon.command;

import com.maxonaddon.perk.PerkManager;
import com.maxonaddon.util.IPlayerStats;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;

/** Команды для теста перков — сброс разблокировок и надетых слотов у игрока. */
public class PerkCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("resetperks")
                .requires(source -> source.hasPermission(2))
                // /resetperks — на себя
                .executes(context -> resetPerks(context, context.getSource().getPlayerOrException()))
                // /resetperks <игрок> — на цель
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> resetPerks(context, EntityArgument.getPlayer(context, "target")))
                )
        );
    }

    private static int resetPerks(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException {
        IPlayerStats stats = (IPlayerStats) player;

        // Разблокировки снимаем полностью...
        stats.setUnlockedPerks(new HashSet<>());

        // ...и слоты тоже чистим — иначе там мог бы остаться надетым перк, который
        // по факту уже не разблокирован (несогласованное состояние).
        String[] slots = stats.getEquippedPerks();
        for (int i = 0; i < slots.length; i++) {
            stats.setEquippedPerk(i, null);
        }

        PerkManager.refreshAndSync(player);

        context.getSource().sendSuccess(
                () -> Component.literal("§aПерки игрока " + player.getName().getString() + " сброшены."),
                true
        );
        return 1;
    }
}
