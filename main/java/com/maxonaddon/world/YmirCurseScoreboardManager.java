package com.maxonaddon.world;

import daot.ModConfig;
import daot.YmirCurseData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class YmirCurseScoreboardManager {
    // Храним текущую строку времени, чтобы удалять старую перед показом новой
    private static final Map<UUID, String> previousTimes = new HashMap<>();

    public static void init() {
        // 1. ПАУЗА ПРИ ВЫХОДЕ
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            YmirCurseData data = YmirCurseData.get(server);
            long startTick = data.getStartTick(player.getUUID());

            if (startTick >= 0L) {
                long gameTime = server.overworld().getGameTime();
                long elapsed = gameTime - startTick;
                YmirCursePauseData.get(server).setPausedTime(player.getUUID(), elapsed);
            }
            previousTimes.remove(player.getUUID()); // Очищаем кэш игрока
        });

        // 2. ВОЗВРАТ ВРЕМЕНИ ПРИ ВХОДЕ
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            YmirCursePauseData pauseData = YmirCursePauseData.get(server);
            Long savedElapsed = pauseData.getPausedTime(player.getUUID());

            if (savedElapsed != null) {
                long gameTime = server.overworld().getGameTime();
                long newStartTick = gameTime - savedElapsed;
                YmirCurseData.get(server).setStartTick(player.getUUID(), newStartTick);
                pauseData.removePausedTime(player.getUUID());
            }

            // Если проклятие активно при входе, сразу создаем панель
            YmirCurseData data = YmirCurseData.get(server);
            if (data.getStartTick(player.getUUID()) >= 0L) {
                showScoreboardToPlayer(player);
            }
        });

        // 3. ОБНОВЛЕНИЕ ТАЙМЕРА (каждую секунду)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return; // Считаем только раз в 20 тиков (1 сек)

            YmirCurseData data = YmirCurseData.get(server);
            long gameTime = server.overworld().getGameTime();
            long durationTicks = Math.max(1L, (long)ModConfig.get().ymirCurseDurationDays) * 24000L;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                long startTick = data.getStartTick(player.getUUID());

                if (startTick >= 0L) {
                    long elapsed = gameTime - startTick;
                    long remainingTicks = durationTicks - elapsed;

                    if (remainingTicks > 0) {
                        // Если игрок получил проклятие только что и панели еще нет
                        if (!previousTimes.containsKey(player.getUUID())) {
                            showScoreboardToPlayer(player);
                        }
                        updateScoreboardTime(player, remainingTicks);
                    } else {
                        // Время вышло - удаляем панель
                        if (previousTimes.containsKey(player.getUUID())) hideScoreboardFromPlayer(player);
                    }
                } else {
                    // Проклятия нет (может вылечили или умер) - убираем панель
                    if (previousTimes.containsKey(player.getUUID())) hideScoreboardFromPlayer(player);
                }
            }
        });
    }

    private static void showScoreboardToPlayer(ServerPlayer player) {
        Scoreboard dummyScoreboard = new Scoreboard();
        // Создаем фейковую цель скорборда
        Objective objective = new Objective(
                dummyScoreboard,
                "ymir_curse",
                ObjectiveCriteria.DUMMY,
                Component.translatable("gui.maxonaddon.ymir_curse.title").withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                null
        );

        // Пакет 0 = создать скорборд
        player.connection.send(new ClientboundSetObjectivePacket(objective, 0));
        // Размещаем его справа (SIDEBAR)
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));

        previousTimes.put(player.getUUID(), ""); // Инициализируем пустой строкой
    }

    private static void hideScoreboardFromPlayer(ServerPlayer player) {
        Scoreboard dummyScoreboard = new Scoreboard();
        Objective objective = new Objective(
                dummyScoreboard, "ymir_curse", ObjectiveCriteria.DUMMY, Component.empty(),
                ObjectiveCriteria.RenderType.INTEGER, false, null
        );

        // Пакет 1 = удалить скорборд
        player.connection.send(new ClientboundSetObjectivePacket(objective, 1));
        previousTimes.remove(player.getUUID());
    }

    private static void updateScoreboardTime(ServerPlayer player, long remainingTicks) {
        long remainingSeconds = remainingTicks / 20;
        long days = remainingSeconds / 86400;
        long hours = (remainingSeconds % 86400) / 3600;
        long minutes = ((remainingSeconds % 86400) % 3600) / 60;
        long seconds = remainingSeconds % 60;

        String timeString;
        if (days > 0) {
            timeString = String.format("§fОсталось: §c%dд %02d:%02d:%02d", days, hours, minutes, seconds);
        } else {
            timeString = String.format("§fОсталось: §c%02d:%02d:%02d", hours, minutes, seconds);
        }

        String previous = previousTimes.get(player.getUUID());

        // Если секунда прошла и текст изменился
        if (!timeString.equals(previous)) {
            // Удаляем старую строчку времени (иначе они будут заполнять весь экран вниз)
            if (previous != null && !previous.isEmpty()) {
                player.connection.send(new ClientboundResetScorePacket(previous, "ymir_curse"));
            }

            // Отправляем новую строку.
            // В 1.21 мы используем текст прямо в имени "фейкового игрока" (owner).
            player.connection.send(new ClientboundSetScorePacket(
                    timeString,        // Имя "игрока" (наш текст)
                    "ymir_curse",      // Привязка к нашей панели
                    0,                 // Очки (можно поставить 0)
                    Optional.empty(),  // Без кастомного отображения
                    Optional.empty()   // Без кастомного формата чисел
            ));

            previousTimes.put(player.getUUID(), timeString);
        }
    }
}