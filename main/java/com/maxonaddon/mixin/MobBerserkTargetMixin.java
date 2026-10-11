package com.maxonaddon.mixin;

import daot.AttackTitanEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobBerserkTargetMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void preventTargetingShifter(LivingEntity target, CallbackInfo ci) {
        Mob titan = (Mob) (Object) this;

        // === ФИКС: АБСОЛЮТНЫЙ ИГНОР ДЛЯ ГУЛА ===
        if (titan.getTags().contains("rumbling_boss")) {
            ci.cancel(); // Гул не берет в таргет НИКОГО
            return;
        }

        // Старый код для берсерка
        if (titan instanceof AttackTitanEntity attackTitan && attackTitan.getTags().contains("berserk_boss")) {
            if (target == null) return;

            if (attackTitan.hasPassenger(target)) {
                ci.cancel();
            } else if (target instanceof Player p) {
                if (attackTitan.getTags().contains("berserk_owner_" + p.getUUID().toString())) {
                    ci.cancel();
                }
            }
        }
    }
}