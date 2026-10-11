package com.maxonaddon.mixin;

import daot.AttackTitanEntity;
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

@Mixin(AttackTitanEntity.class)
public abstract class AttackTitanDamageMixin extends Monster {

    protected AttackTitanDamageMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow public abstract int getDisplayedHardeningMode();

    @Inject(method = "hurtFromNape", at = @At("HEAD"), cancellable = true, remap = false)
    private void reduceNapeDamage(DamageSource source, float amount, CallbackInfo ci) {
        // Если это копье - наносим 100% урона в обход брони
        if (source.getDirectEntity() instanceof ThunderSpearEntity || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }

        if (this.getDisplayedHardeningMode() > 0) {
            this.level().playSound(null, this.getX(), this.getY() + this.getBbHeight(), this.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 1.5F);

            // Оставляем 20% урона от клинков
            super.hurt(source, amount * 0.2F);
            ci.cancel();
        }
    }
}