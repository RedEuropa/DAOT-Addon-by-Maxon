package com.maxonaddon.mixin;

import daot.AttackTitanEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerBerserkImmunityMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void preventTitanAoEDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player) {
            if (source.getEntity() instanceof AttackTitanEntity titan && titan.getTags().contains("berserk_boss")) {
                if (titan.hasPassenger(player) || titan.getTags().contains("berserk_owner_" + player.getUUID().toString())) {
                    cir.setReturnValue(false); // Полный иммунитет
                }
            }
        }
    }
}