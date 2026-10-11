package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import daot.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

// Ставим экстремально высокий приоритет, чтобы наш миксин выполнился последним и перебил ваниллу и другие аддоны
@Mixin(value = LivingEntity.class, priority = 10000)
public abstract class TitanPassengerMixin {

    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void onGetControllingPassenger(CallbackInfoReturnable<LivingEntity> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        String name = self.getClass().getName();

        // Пропускаем все, что не является титаном
        if (!name.startsWith("daot.") || !name.contains("Titan") || name.contains("Eye") || name.contains("Nape") || name.contains("Grab")) {
            return;
        }

        // Если пассажир - игрок
        if (self.getFirstPassenger() instanceof Player player) {

            if (player.isInvisible()) {
                return;
            }

            // 1. ЖЕСТКИЙ ЗАПРЕТ ПРИ ЗАХВАТЕ В ПАСТЬ
            if (player.getTags().contains("dannys-aot:being_grabbed")) {
                System.out.println("[TitanDebug] БЛОКИРОВКА: Игрок " + player.getName().getString() + " находится в пасти!");
                cir.setReturnValue(null);
                return;
            }

            // 2. ЛОГИКА НА СЕРВЕРЕ (Здесь делаем проверку крови)
            if (!player.level().isClientSide() && player.level() instanceof ServerLevel sl) {
                // Получаем кровь игрока
                BloodlineData data = BloodlineData.get(sl); //[cite: 3]
                BloodlineType bloodline = data.getBloodline(player.getUUID()); //[cite: 3]

                // Проверяем, является ли титан шифтером
                boolean isShifter = false;
                UUID shifterUUID = null;

                if (self instanceof TestShifterTitanEntity t) { isShifter = true; shifterUUID = t.getShifterUUID(); }
                else if (self instanceof AttackTitanEntity t) { isShifter = true; shifterUUID = t.getShifterUUID(); }
                else if (self instanceof ArmoredTitanEntity t) { isShifter = true; shifterUUID = t.getShifterUUID(); }
                else if (self instanceof FemaleTitanEntity t) { isShifter = true; shifterUUID = t.getShifterUUID(); }
                else if (self instanceof BeastTitanEntity t) { isShifter = true; shifterUUID = t.getShifterUUID(); }
                else if (self instanceof WarhammerTitanEntity t) { isShifter = true; shifterUUID = t.getShifterUUID(); }
                else if (self instanceof ColossalTitanEntity t) { isShifter = true; shifterUUID = t.getShifterUUID(); }

                // --- ПРОВЕРКА ДЛЯ ШИФТЕРОВ ---
                if (isShifter) {
                    if (shifterUUID != null && shifterUUID.equals(player.getUUID())) {
                        System.out.println("[TitanDebug] УСПЕХ: Игрок " + player.getName().getString() + " управляет своим шифтером.");
                        cir.setReturnValue(player);
                    } else {
                        System.out.println("[TitanDebug] БЛОКИРОВКА: Игрок " + player.getName().getString() + " сел в чужого шифтера.");
                        cir.setReturnValue(null);
                    }
                    return;
                }

                // --- ПРОВЕРКА ДЛЯ ЧИСТЫХ ТИТАНОВ (КРОВЬ) ---
                // Если мы дошли сюда, значит это чистый титан. Проверяем королевскую кровь.
                if (bloodline == BloodlineType.ROYAL) {
                    IPlayerStats stats = (IPlayerStats) player;
                    if (!stats.hasSkill("royal_control_1")) {
                        System.out.println("[TitanDebug] БЛОКИРОВКА: Игрок король, но нет навыка 'Тотальный контроль'.");
                        cir.setReturnValue(null);
                        return;
                    }
                    String ownerName = VillagerTransformTracker.getOwnerName(self);
                    if (ownerName != null && ownerName.equals(player.getGameProfile().getName())) {
                        System.out.println("[TitanDebug] УСПЕХ: Игрок " + player.getName().getString() + " с КОРОЛЕВСКОЙ КРОВЬЮ управляет созданным титаном.");
                        cir.setReturnValue(player);
                        return;
                    } else {
                        System.out.println("[TitanDebug] БЛОКИРОВКА: Игрок " + player.getName().getString() + " король, но этот титан создан не им.");
                    }
                } else {
                    System.out.println("[TitanDebug] БЛОКИРОВКА: Игрок " + player.getName().getString() + " не король (Кровь: " + bloodline + "). Управление чистым титаном запрещено.");
                }

                // Если ни одно условие не подошло — жестко отбираем управление
                cir.setReturnValue(null);
            }
            // 3. ЛОГИКА НА КЛИЕНТЕ (Заглушка для WASD)
            else if (player.level().isClientSide()) {
                // На клиенте просто блокируем руль для всех, кто невидим (значит, он вколол шприц себе)
                // Это предотвратит отправку ложных пакетов движения на сервер
                if (player.isInvisible()) {
                    cir.setReturnValue(null);
                }
            }
        }
    }
}