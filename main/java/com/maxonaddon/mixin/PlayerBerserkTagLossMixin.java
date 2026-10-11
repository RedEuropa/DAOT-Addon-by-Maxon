package com.maxonaddon.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

@Mixin(Player.class)
public abstract class PlayerBerserkTagLossMixin {

    // ========================================================
    // 1. ПОТЕРЯ ТЕГА ПРИ СМЕРТИ ОТ ТИТАНА
    // ========================================================
    @Inject(method = "die", at = @At("HEAD"))
    private void maxon$onPlayerDie(DamageSource damageSource, CallbackInfo ci) {
        Player self = (Player) (Object) this;

        if (!self.level().isClientSide() && self.getTags().contains("has_shifted_before")) {
            Entity attacker = damageSource.getEntity();

            if (attacker != null) {
                String name = attacker.getClass().getSimpleName();
                if (name.contains("Titan") || name.contains("ConnieFather")) {
                    self.removeTag("has_shifted_before");
                }
            }
        }
    }

    // ========================================================
    // 2. ПОТЕРЯ ТЕГА ПРИ УТРАТЕ СИЛЫ ШИФТЕРА
    // ========================================================
    @Inject(method = "tick", at = @At("TAIL"))
    private void maxon$onPlayerTick(CallbackInfo ci) {
        Player self = (Player) (Object) this;

        // Выполняем проверку раз в секунду (каждые 20 тиков) на сервере
        if (!self.level().isClientSide() && self.tickCount % 20 == 0) {
            if (self.getTags().contains("has_shifted_before")) {
                boolean isShifter = false;

                try {
                    if (self.level() instanceof ServerLevel serverLevel) {
                        // Получаем данные о силах титанов на сервере
                        daot.TitanPowerData powerData = daot.TitanPowerData.get(serverLevel);

                        if (powerData != null) {
                            // Перебираем все возможные силы титанов (Атакующий, Бронированный и т.д.)
                            for (daot.TitanPowerType type : daot.TitanPowerType.values()) {
                                Set<UUID> playersWithPower = powerData.getPlayersWithPower(type);

                                // Если UUID игрока числится хотя бы в одной силе, значит он шифтер
                                if (playersWithPower != null && playersWithPower.contains(self.getUUID())) {
                                    isShifter = true;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Блок try-catch защищает от крашей в случае сбоя API мода
                }

                // Если мы перебрали все силы и не нашли там игрока, снимаем тег
                if (!isShifter) {
                    self.removeTag("has_shifted_before");
                }
            }
        }
    }
}