package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerFallDamageMixin {

    @ModifyVariable(method = "causeFallDamage", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float maxon$reduceFallDamage(float damageMultiplier) {
        IPlayerStats stats = (IPlayerStats) this;
        float reduction = 0.0f;

        // Наслаиваем скидки на урон
        if (stats.hasSkill("agility_1")) {
            reduction += 0.05f; // +5% защиты
        }
        if (stats.hasSkill("agility_2")) {
            reduction += 0.10f; // +10% защиты (в сумме 15%)
        }
        reduction += (float) com.maxonaddon.perk.PerkManager.fallDamageReduction((Player) (Object) this);

        // Если reduction = 0.15, мы умножаем урон на 0.85
        return damageMultiplier * (1.0f - reduction);
    }
}