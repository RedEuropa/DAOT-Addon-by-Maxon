package com.maxonaddon.mixin;

import com.maxonaddon.perk.PerkManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Снижение затрат сытости от перков (см. ModPerk#getHungerReduction) — общий случай, в
 * отличие от AckermanHungerMixin (который снижает голод только во время пробуждения
 * Аккермана через укорочение спец-эффекта). Player#causeFoodExhaustion(float) — единая
 * ванильная точка, через которую идёт ЛЮБое истощение (спринт, прыжки, атаки, добыча
 * блоков и т.д.), поэтому урезаем прямо здесь, до того как оно уйдёт в FoodData.
 */
@Mixin(Player.class)
public abstract class HungerReductionMixin {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float maxon$reducePerkExhaustion(float exhaustion) {
        Player self = (Player) (Object) this;
        double reduction = PerkManager.hungerReduction(self);
        if (reduction <= 0.0) return exhaustion;

        return exhaustion * (1.0F - (float) reduction);
    }
}
