package com.maxonaddon;

import com.maxonaddon.command.ScoutSpawnCommand;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

public class ScoutSpawner {

    private static int tickCounter = 0;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            // Проверяем каждые 3 минуты (20 тиков * 60 сек * 3 = 3600)
            if (tickCounter >= 3600) {
                tickCounter = 0;

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {

                    // === НОВОЕ: ЖЕСТКАЯ ПРОВЕРКА ИГРОВОГО ПРАВИЛА ===
                    if (!player.serverLevel().getGameRules().getBoolean(MaxonConfig.DO_SOLDIER_SPAWN)) {
                        continue; // Если спавн солдат выключен в мире, переходим к следующему игроку (никого не спавним)
                    }

                    // Проверяем измерение Парадиза
                    if (player.level().dimension().location().toString().equals("dannys-aot:paradis")) {

                        // Шанс 30% на спавн отряда
                        if (Math.random() < 0.3) {
                            // Ищем позицию для спавна (в радиусе 30-40 блоков от игрока)
                            double angle = Math.random() * Math.PI * 2;
                            double distance = 30 + Math.random() * 10;
                            int spawnX = (int) (player.getX() + Math.cos(angle) * distance);
                            int spawnZ = (int) (player.getZ() + Math.sin(angle) * distance);

                            // Находим поверхность
                            int spawnY = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, spawnX, spawnZ);

                            BlockPos spawnPos = new BlockPos(spawnX, spawnY, spawnZ);

                            // Вызываем наш метод спавна из команды
                            ScoutSpawnCommand.spawnScoutSquad(player.serverLevel(), spawnPos);
                        }
                    }
                }
            }
        });
    }
}