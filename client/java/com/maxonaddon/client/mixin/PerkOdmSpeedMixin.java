package com.maxonaddon.client.mixin;

import com.maxonaddon.perk.PerkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Бонус к скорости крюка/полёта УПМ-APG от перков (см. ModPerk.getOdmSpeedBonus()).
 *
 * daot.ODMTickHandler.getBasePullSpeed()/getBaseOrbitSpeed() — приватные static-методы,
 * читающие только daot.ODMConfig (см. бинарный анализ), НЕ ванильный Attributes.MOVEMENT_SPEED —
 * то есть скорость крюка не зависит от него никак, в отличие от обычной ходьбы. ODMTickHandler
 * целиком клиентский и всегда обрабатывает ЛОКАЛЬНОГО игрока (движение крюка — предсказание на
 * стороне клиента), поэтому Minecraft.getInstance().player здесь — именно тот, для кого считается
 * скорость, параметр игрока в сигнатуре метода не нужен.
 */
@Mixin(daot.ODMTickHandler.class)
public class PerkOdmSpeedMixin {

    @Inject(method = "getBasePullSpeed", at = @At("RETURN"), cancellable = true)
    private static void maxon$applyPerkPullSpeed(CallbackInfoReturnable<Double> cir) {
        applyBonus(cir);
    }

    @Inject(method = "getBaseOrbitSpeed", at = @At("RETURN"), cancellable = true)
    private static void maxon$applyPerkOrbitSpeed(CallbackInfoReturnable<Double> cir) {
        applyBonus(cir);
    }

    private static void applyBonus(CallbackInfoReturnable<Double> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        double bonus = PerkManager.odmSpeedBonus(player);
        if (bonus == 0.0) return;

        cir.setReturnValue(cir.getReturnValueD() * (1.0 + bonus));
    }
}
