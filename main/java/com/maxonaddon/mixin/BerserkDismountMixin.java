package com.maxonaddon.mixin;

import daot.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {
        AttackTitanEntity.class, ArmoredTitanEntity.class, FemaleTitanEntity.class,
        BeastTitanEntity.class, ColossalTitanEntity.class, WarhammerTitanEntity.class
})
public abstract class BerserkDismountMixin {

    // === БЛОКИРОВКА УПРАВЛЕНИЯ ТИТАНОМ ===
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void blockPlayerControlIfBerserk(CallbackInfoReturnable<LivingEntity> cir) {
        Entity self = (Entity) (Object) this;

        // Если это босс-берсерк и он жив
        if (self.getTags().contains("berserk_boss") && self.isAlive()) {
            // Возвращаем null. Игра будет думать, что "водителя" нет,
            // игрок станет просто грузом, а ИИ (TitanBerserkMeleeGoal) получит полный контроль.
            cir.setReturnValue(null);
        }
    }
}