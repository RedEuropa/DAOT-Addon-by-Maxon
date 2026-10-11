package com.maxonaddon.event;

import com.maxonaddon.network.CarriageGuiPayload;
import com.maxonaddon.util.ICarriageData;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;

public class CarriageInteractHandler {

    // Точка леса, раз он генерируется слишком далеко для locate
    private static final BlockPos GIANT_FOREST_POS = new BlockPos(70000, 80, 70000);

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            if (entity instanceof Villager villager && villager.getTags().contains("carriage_driver")) {
                ICarriageData playerData = (ICarriageData) player;
                BlockPos npcPos = villager.blockPosition();

                // 1. Получаем ID города
                String cityId = "";
                for (String tag : villager.getTags()) {
                    if (tag.startsWith("daot_city:")) {
                        cityId = tag.replace("daot_city:", "");
                        break;
                    }
                }
                if (cityId.isEmpty()) {
                    // Тега нет — скорее всего, это старый водитель, заспавненный ДО того,
                    // как появилась система тегов "daot_city:" (пережил обновления мода).
                    // Вместо непонятного "city_X_Z" (которого нет ни в одной из 12 кнопок
                    // CarriageScreen — маршрут открывался бы, но никогда не появлялся бы
                    // в списке) ищем ближайшую из 12 канонических точек CarriageSpawner
                    // и самовосстанавливаем правильный "district_N".
                    cityId = com.maxonaddon.util.CarriagePricing.nearestDistrictId(npcPos);
                    if (cityId == null) {
                        cityId = "city_" + npcPos.getX() + "_" + npcPos.getZ();
                    }
                    villager.addTag("daot_city:" + cityId);
                }

                villager.addTag("Visited");

                // Запоминаем на сервере, У КАКОГО водителя игрок сейчас — источник истины
                // для расчёта цены проезда (клиенту при этом верим только в отображении,
                // не в реальном списании изумрудов).
                com.maxonaddon.MaxonDaotAddon.lastCarriageCityId.put(player.getUUID(), cityId);

                // 2. Открываем город и записываем его координаты
                if (!playerData.maxon$getUnlockedCities().containsKey(cityId)) {
                    playerData.maxon$unlockCity(cityId, npcPos);
                    player.sendSystemMessage(Component.translatable("message.maxonaddon.carriage.new_route").withStyle(ChatFormatting.GREEN));
                    tryGrantTravelerPerk(player, playerData);
                }

                // 3. Пытаемся найти лес, если игрок ушел достаточно далеко (> 7000 блоков) и лес еще не открыт
                if (!playerData.maxon$isForestUnlocked() && world instanceof ServerLevel serverLevel) {
                    if (Math.abs(npcPos.getX()) > 7000 || Math.abs(npcPos.getZ()) > 7000) {
                        BlockPos foundForest = com.maxonaddon.util.ForestLocator.getOrFindForest(serverLevel, npcPos);

                        if (foundForest != null) {
                            playerData.maxon$setForestUnlocked(true);
                            player.sendSystemMessage(Component.translatable("message.maxonaddon.carriage.forest_found").withStyle(ChatFormatting.GOLD));
                        }
                    }
                }

                // 4. Шлем пакет для GUI
                if (player instanceof ServerPlayer serverPlayer) {
                    // Диагностика: чтобы точно видеть в логе сервера, какие города у игрока
                    // реально считаются открытыми в момент показа меню (проверить репорты
                    // "не открывает город N" без гадания).
                    com.maxonaddon.MaxonDaotAddon.LOGGER.info(
                            "[Carriage] {} открыл меню у водителя '{}'. Открытые города: {}. Лес открыт: {}",
                            player.getGameProfile().getName(), cityId,
                            playerData.maxon$getUnlockedCities().keySet(), playerData.maxon$isForestUnlocked()
                    );
                    ServerPlayNetworking.send(serverPlayer,
                            new CarriageGuiPayload(
                                    new ArrayList<>(playerData.maxon$getUnlockedCities().keySet()),
                                    playerData.maxon$isForestUnlocked(),
                                    cityId
                            )
                    );
                }

                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        });
    }

    /**
     * Перк "Путешественник" выдаётся за ПОЛНУЮ карту маршрутов — по одному открытому городу
     * на каждую точку из CarriageSpawner.SPAWN_POINTS.
     * <p>
     * Раньше он падал игроку за находку леса: тот ищется у первого же водителя дальше 7000
     * блоков от центра, то есть перк приходил примерно на трети пройденного пути. Описание
     * перка при этом всегда обещало "откройте все маршруты повозок" — теперь код с ним сходится.
     * <p>
     * Лес в условие намеренно не входит: его поиск может не найти подходящего места и вернуть
     * null, и тогда перк оказался бы недостижим вообще.
     */
    private static void tryGrantTravelerPerk(Player player, com.maxonaddon.util.ICarriageData playerData) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (((com.maxonaddon.util.IPlayerStats) serverPlayer).hasUnlockedPerk("traveler")) return;
        if (playerData.maxon$getUnlockedCities().size() < com.maxonaddon.event.CarriageSpawner.SPAWN_POINTS.length) return;

        com.maxonaddon.perk.PerkUnlockHandler.grantPerk(serverPlayer, com.maxonaddon.perk.ModPerk.TRAVELER);
    }
}