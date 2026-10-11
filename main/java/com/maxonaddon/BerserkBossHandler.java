package com.maxonaddon;

import daot.ShifterTitan;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.UUID;

public class BerserkBossHandler {

    // Обновленный и более надежный метод сброса UUID (пробивает защиту private/protected)
    public static void setTitanUUID(Entity titan, UUID uuid) {
        if (titan == null) return;

        try {
            Method method = null;
            Class<?> currentClass = titan.getClass();

            // Проходимся по всем суперклассам, чтобы точно найти метод
            while (currentClass != Object.class && currentClass != null) {
                try {
                    method = currentClass.getDeclaredMethod("setShifterUUID", UUID.class);
                    break;
                } catch (NoSuchMethodException e) {
                    currentClass = currentClass.getSuperclass();
                }
            }

            if (method != null) {
                method.setAccessible(true);
                method.invoke(titan, uuid);
            } else {
                System.out.println("§c[MaxonAddon] КРИТИЧЕСКАЯ ОШИБКА: Метод setShifterUUID не найден у " + titan.getClass().getSimpleName());
            }
        } catch (Exception e) {
            System.out.println("§c[MaxonAddon] ОШИБКА при сбросе UUID титана (захват управления провален):");
            e.printStackTrace();
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Entity vehicle = player.getVehicle();

                if (vehicle instanceof ShifterTitan) {
                    if (!vehicle.getTags().contains("processed_shift")) {
                        vehicle.addTag("processed_shift");

                        if (!player.getTags().contains("has_shifted_before")) {

                            // Получаем название класса титана
                            String titanName = vehicle.getClass().getSimpleName();

                            // Достаем шансы конкретно под этого титана из нового конфига
                            double chance = BerserkConfig.getChanceForTitan(titanName);

                            // ПРОВЕРКА ГЕЙМРУЛА И ШАНСА ИЗ КОНФИГА
                            boolean isGameruleOn = player.serverLevel().getGameRules().getBoolean(BerserkConfig.DO_SHIFTER_BERSERK);
                            double roll = player.getRandom().nextFloat() * 100.0f; // Рандом от 0.0 до 100.0

                            if (isGameruleOn && roll <= chance) {
                                // Игрок провалил проверку на контроль -> Включаем Берсерка
                                vehicle.addTag("berserk_boss");
                                vehicle.addTag("berserk_owner_" + player.getUUID().toString());
                                setTitanUUID(vehicle, null);

                                // Устанавливаем кастомное ХП боссу
                                double maxHp = BerserkConfig.getHealthForTitan(titanName);
                                if (vehicle instanceof net.minecraft.world.entity.LivingEntity livingVehicle) {
                                    // Устанавливаем новый лимит максимального здоровья
                                    var attribute = livingVehicle.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                                    if (attribute != null) {
                                        attribute.setBaseValue(maxHp);
                                    }
                                    // Восстанавливаем здоровье до нового максимума
                                    livingVehicle.setHealth((float) maxHp);
                                }

                                // Делаем игрока прозрачным встроенными средствами мода!
                                daot.TranslucentTracker.setToggled(player, true);

                                player.displayClientMessage(Component.translatable("message.maxonaddon.berserk_lost_control").withStyle(ChatFormatting.DARK_RED), true);
                            } else {
                                // Геймрул выключен ИЛИ повезло с шансом -> Даем игроку контроль
                                player.addTag("has_shifted_before");
                                player.displayClientMessage(Component.translatable("message.maxonaddon.berserk_controlled").withStyle(ChatFormatting.GREEN), true);
                            }
                        }
                    }
                }
            }
        });

        // Берсерк-титана убили — для его владельца режим тоже кончился насовсем.
        //
        // Без этого выходило нелепо: игрок пережил берсерка (сам погиб в этом облике или
        // титана добили другие), вышел из него — а следующее превращение снова делало его
        // берсерком, потому что тег "обуздал силу" выдавался только за победу в QTE или за
        // удачный бросок при первом превращении. При шансах 100% в конфиге это означало
        // берсерка навсегда.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!entity.getTags().contains("berserk_boss")) return;
            if (entity.level().isClientSide()) return;

            var server = entity.getServer();
            if (server == null) return;

            for (String tag : entity.getTags()) {
                if (!tag.startsWith("berserk_owner_")) continue;
                try {
                    UUID ownerId = UUID.fromString(tag.substring("berserk_owner_".length()));
                    ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
                    // Мёртвому владельцу тег не возвращаем: PlayerBerserkTagLossMixin снимает
                    // его при смерти от титана, и выдача здесь отменяла бы это задним числом —
                    // берсерк-титана как раз обычно и добивают сразу после гибели игрока.
                    // Утрату самой силы шифтера тот же миксин отследит сам, посекундной
                    // проверкой по TitanPowerData, так что жить с этим тегом останется только
                    // тот, кто силу сохранил.
                    if (owner != null && owner.isAlive()) owner.addTag("has_shifted_before");
                } catch (IllegalArgumentException ignored) {
                    // Тег испорчен вручную — просто пропускаем, падать тут незачем.
                }
            }
        });
    }
}