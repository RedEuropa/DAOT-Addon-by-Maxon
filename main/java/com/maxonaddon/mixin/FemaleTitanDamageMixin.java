package com.maxonaddon.mixin;

import daot.BladeAttackTracker;
import daot.FemaleTitanEntity;
import daot.ThunderSpearEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FemaleTitanEntity.class)
public abstract class FemaleTitanDamageMixin extends Monster {

    protected FemaleTitanDamageMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow public abstract boolean isHardenedNape();
    @Shadow public abstract boolean isDefeated();
    @Shadow public abstract boolean isGrabbing();
    @Shadow public abstract void releaseGrabbedEntity();
    @Shadow public abstract boolean isDismounting();
    @Shadow public abstract void die(DamageSource damageSource);
    @Shadow public abstract void incapacitate();

    @Inject(method = "hurtFromNape", at = @At("HEAD"), cancellable = true, remap = false)
    private void reduceFemaleNapeDamage(DamageSource source, float amount, CallbackInfo ci) {
        if (this.isDefeated() || this.level().isClientSide()) return;

        // 1. ФИКС БАГА: Применяем полный урон от громовых копий (в обход брони)
        if (source.getDirectEntity() instanceof ThunderSpearEntity || source.is(DamageTypeTags.IS_EXPLOSION)) {
            if (this.isGrabbing()) this.releaseGrabbedEntity();
            if (this.isDismounting()) {
                this.die(source);
            } else {
                super.hurt(source, amount); // Применяем урон напрямую
                if (this.getHealth() <= 1.01F) {
                    this.incapacitate();
                }
            }
            ci.cancel();
            return;
        }

        // 2. Порезка урона от клинков при активной броне
        if (this.isHardenedNape()) {
            if (this.isGrabbing()) this.releaseGrabbedEntity();
            if (this.isDismounting()) {
                this.die(source);
            } else {
                this.level().playSound(null, this.getX(), this.getY() + this.getBbHeight(), this.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 1.5F);

                float chargeFraction = BladeAttackTracker.consumeNapeChargeFraction();
                if (chargeFraction > 0.0F) {
                    float perHitDamage = this.getMaxHealth() / 3.0F;
                    float safeDamage = Math.max(0.0F, Math.min(perHitDamage * chargeFraction, this.getHealth() - 1.0F));

                    // Оставляем 20% урона от клинков
                    safeDamage *= 0.2F;

                    super.hurt(source, safeDamage);
                    if (this.getHealth() <= 1.01F) {
                        this.incapacitate();
                    }
                }
            }
            ci.cancel();
        }
    }
}