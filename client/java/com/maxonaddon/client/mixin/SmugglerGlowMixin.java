package com.maxonaddon.client.mixin;

import com.maxonaddon.client.SmugglerGlowTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Персональная подсветка контрабандистов для перка "Торговец".
 * <p>
 * Приём взят у очков Зика из dannys-aot: свечение решается на КЛИЕНТЕ, поэтому его видит
 * только тот, кому сервер прислал список (см. SmugglerGlowTracker). Серверный эффект
 * Свечения так не умеет — он свойство сущности и виден всем сразу.
 * <p>
 * Цвет контура игра берёт из getTeamColor, поэтому его подменяем тем же условием: без
 * этого подсветка была бы белой и не отличалась от обычного Свечения.
 */
@Mixin(Entity.class)
public class SmugglerGlowMixin {

    /** Красный из палитры чата (ChatFormatting.RED). */
    private static final int MAXON_GLOW_RED = 0xFF5555;

    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void maxon$glowForTrader(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        Entity self = (Entity) (Object) this;
        if (SmugglerGlowTracker.shouldGlow(self.getId())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void maxon$glowColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (SmugglerGlowTracker.shouldGlow(self.getId())) {
            cir.setReturnValue(MAXON_GLOW_RED);
        }
    }
}
