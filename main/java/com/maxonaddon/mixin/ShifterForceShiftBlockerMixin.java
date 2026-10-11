package com.maxonaddon.mixin;

import daot.AttackTitanEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ShifterForceShiftBlockerMixin {

    // === БЛОКИРОВКА ФОРСИРОВАННОГО ВЫЛЕЗАНИЯ ИЗ БЕРСЕРКА ===
    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void preventBerserkForceShift(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self instanceof ServerPlayer player) {
            Entity vehicle = player.getVehicle();

            // Если игрок сидит в Атакующем титане
            if (vehicle instanceof AttackTitanEntity titan) {
                // Если этот титан - босс Берсерк
                if (titan.getTags().contains("berserk_boss")) {

                    // Жестко отменяем выполнение этого метода, чтобы мод не выкинул игрока наружу!
                    // Оставляем cir без значения, чтобы метод просто вернул false и продолжил обычную логику смерти (или выживания) игрока
                    // Не ставим setReturnValue(true), иначе игрок станет бессмертным!
                }
            }
        }
    }

}