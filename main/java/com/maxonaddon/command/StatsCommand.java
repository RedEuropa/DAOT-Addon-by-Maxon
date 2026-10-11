package com.maxonaddon.command;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.network.OpenStatsScreenPayload;
import com.maxonaddon.util.IPlayerStats;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import daot.BloodlineData;
import daot.BloodlineType;
import daot.TitanPowerData;
import daot.TitanPowerType;
import daot.YmirCurseData;
import daot.ModConfig;
import daot.DannysAot;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.ArrayList; // <--- НОВЫЙ ИМПОРТ

public class StatsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("statistics")
                .requires(source -> MaxonConfig.get().statisticsSystemEnabled)
                .executes(context -> {
                    sendPlayerStats(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("addpoints")
                        .requires(source -> source.hasPermission(2)) // Только для админов
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            // ВЫПОЛНЕНИЕ БЕЗ ЦЕЛИ (НА СЕБЯ)
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            return addPointsToPlayer(context.getSource(), player, StringArgumentType.getString(context, "type"), IntegerArgumentType.getInteger(context, "amount"));
                                        })
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(context -> {
                                                    // ВЫПОЛНЕНИЕ С УКАЗАНИЕМ ЦЕЛИ (ДРУГОЙ ИГРОК)
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                                    return addPointsToPlayer(context.getSource(), target, StringArgumentType.getString(context, "type"), IntegerArgumentType.getInteger(context, "amount"));
                                                })
                                        )
                                )
                        )
                )
        );
    }

    // ВЫНЕСЕННАЯ ЛОГИКА ВЫДАЧИ ОЧКОВ
    private static int addPointsToPlayer(CommandSourceStack source, ServerPlayer target, String type, int amount) {
        IPlayerStats stats = (IPlayerStats) target;

        if (type.equalsIgnoreCase("bloodline")) {
            stats.setBloodlinePoints(stats.getBloodlinePoints() + amount);
            source.sendSuccess(() -> Component.translatable("command.maxonaddon.stats.addpoints.bloodline", amount), false);
        } else if (type.equalsIgnoreCase("titan")) {
            stats.setTitanPoints(stats.getTitanPoints() + amount);
            source.sendSuccess(() -> Component.translatable("command.maxonaddon.stats.addpoints.titan", amount), false);
        } else if (type.equalsIgnoreCase("soldier") || type.equalsIgnoreCase("skill")) {
            stats.setSkillPoints(stats.getSkillPoints() + amount);
            source.sendSuccess(() -> Component.translatable("command.maxonaddon.stats.addpoints.skill", amount), false);
        } else {
            source.sendFailure(Component.translatable("command.maxonaddon.stats.addpoints.error_type"));
            return 0; // Возвращаем 0 при ошибке (команда не выполнена)
        }
        return 1; // Успешное выполнение
    }

    public static void sendPlayerStats(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;

        int killed = stats.getTitansKilled();
        long totalTicks = stats.getTicksPlayedTotal();
        long ticksWithoutDeath = stats.getTicksSinceDeath();

        int level = stats.getLevel();
        int exp = stats.getExp();
        int skillPoints = stats.getSkillPoints();
        int requiredExp = 100 + (level - 1) * 25; // <--- Удалили чтение boolean переменных

        double totalHours = totalTicks / 72000.0;
        double hoursWithoutDeath = ticksWithoutDeath / 72000.0;
        int fearResist = Math.min(100, (killed * 33) + (killed >= 3 ? 1 : 0));

        BloodlineData bloodlineData = BloodlineData.get(player.serverLevel());
        BloodlineType bloodline = bloodlineData.getBloodline(player.getUUID());
        String bloodlineName = bloodline != null ? bloodline.name() : "NONE";

        boolean isShifter = false;
        String shifterStatus = "Нет";
        TitanPowerData powerData = TitanPowerData.get(player.serverLevel());

        for (TitanPowerType type : TitanPowerType.values()) {
            if (powerData.playerHoldsPower(player.getUUID(), type)) {
                String typeName = type.name().toLowerCase().replace("_", " ");
                shifterStatus = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                isShifter = true;
                break;
            }
        }

        if (bloodline == BloodlineType.ACKERMAN || bloodline == BloodlineType.MARLEYAN) {
            shifterStatus = "HIDDEN";
        }

        String ymirCurseTimeStr = "";
        YmirCurseData curseData = YmirCurseData.get(player.server);
        long startTick = curseData.getStartTick(player.getUUID());
        boolean isCurseEnabled = DannysAot.isYmirCurseEnabled(player.serverLevel());

        if (isShifter) {
            if (isCurseEnabled && startTick >= 0L) {
                long gameTime = player.serverLevel().getGameTime();
                long durationTicks = Math.max(1L, (long) ModConfig.get().ymirCurseDurationDays) * 24000L;
                long elapsed = gameTime - startTick;
                long remainingTicks = durationTicks - elapsed;

                if (remainingTicks > 0) {
                    long remainingSeconds = remainingTicks / 20;
                    long days = remainingSeconds / 86400;
                    long hours = (remainingSeconds % 86400) / 3600;
                    long minutes = ((remainingSeconds % 86400) % 3600) / 60;
                    long seconds = remainingSeconds % 60;

                    if (days > 0) {
                        ymirCurseTimeStr = String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds);
                    } else {
                        ymirCurseTimeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                    }
                } else {
                    ymirCurseTimeStr = "EXPIRED";
                }
            }
        } else {
            if (startTick >= 0L) {
                curseData.clear(player.getUUID());
            }
        }

        int bloodlinePoints = stats.getBloodlinePoints();
        int titanPoints = stats.getTitanPoints();
        int questsCompleted = stats.getQuestsCompleted();

        int repMaria = stats.getReputation(com.maxonaddon.util.Wall.MARIA);
        int repRose = stats.getReputation(com.maxonaddon.util.Wall.ROSE);
        int repSina = stats.getReputation(com.maxonaddon.util.Wall.SINA);

        // <--- ПЕРЕДАЕМ СПИСОК НАВЫКОВ И РЕПУТАЦИЮ СТЕН В КОНЦЕ
        ServerPlayNetworking.send(player, new OpenStatsScreenPayload(
                killed, fearResist, totalHours, hoursWithoutDeath, bloodlineName, shifterStatus, ymirCurseTimeStr,
                level, exp, requiredExp, skillPoints, bloodlinePoints, titanPoints, questsCompleted,
                new ArrayList<>(stats.getUnlockedSkills()), repMaria, repRose, repSina
        ));
    }
}