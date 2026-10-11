package com.maxonaddon.mixin;

import daot.HookPoint;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HookPoint.class, remap = false)
public class HookPointMixin {
    @Shadow public Vec3 startPosition;
    @Shadow public Vec3 position;
    @Shadow public long shootTime;
    @Shadow public boolean isRetracting;
    @Shadow public boolean isExtending;

    /**
     * Прогресс вылета крюка по НАШЕЙ формуле вместо ванильной.
     * <p>
     * У daot время вылета — константа SHOOT_DURATION = 150 мс, одинаковая хоть на три блока,
     * хоть на тридцать. Наша механика считает его от расстояния (скорость стрелы), но раньше
     * мы умели только придерживать крюк, пока он не долетит: обе точки-перехвата возвращали
     * "ещё летит" и не могли сказать "уже долетел". Из-за этого "Ускоренный крюк" упирался
     * в пол в те же 150 мс — на близких дистанциях навык не давал вообще ничего, а на дальних
     * работал только до этого порога.
     * <p>
     * Теперь прогресс целиком наш, и множитель навыка ускоряет сам вылет.
     */
    @Inject(method = "getExtensionProgress", at = @At("HEAD"), cancellable = true)
    private void onGetExtensionProgress(CallbackInfoReturnable<Float> cir) {
        if (!com.maxonaddon.MaxonConfig.get().enableAdvancedHookMechanics) return;
        if (!this.isExtending) return;
        if (this.startPosition == null || this.position == null) return;

        double distance = this.startPosition.distanceTo(this.position);
        double durationMs = com.maxonaddon.util.HookSpeedHelper.flightDurationMs(distance);
        long elapsed = System.currentTimeMillis() - this.shootTime;

        float progress = (float) Math.min(1.0, elapsed / durationMs);
        // Ваниль на этом же месте снимает флаг вылета — без этого крюк "долетел бы"
        // визуально, но остался в состоянии полёта навсегда.
        if (progress >= 1.0F) this.isExtending = false;
        cir.setReturnValue(progress);
    }

    @Inject(method = "getCurrentPosition", at = @At("HEAD"), cancellable = true)
    private void onGetCurrentPosition(CallbackInfoReturnable<Vec3> cir) {
        if (!com.maxonaddon.MaxonConfig.get().enableAdvancedHookMechanics) return;
        if (this.startPosition != null && this.position != null && !this.isRetracting) {
            double distance = this.startPosition.distanceTo(this.position);
            // Время полёта с учётом навыков "Ускоренный крюк" (см. HookSpeedHelper).
            double durationMs = com.maxonaddon.util.HookSpeedHelper.flightDurationMs(distance);
            long elapsed = System.currentTimeMillis() - this.shootTime;

            if (elapsed < durationMs) {
                float progress = (float) (elapsed / durationMs);
                cir.setReturnValue(this.startPosition.lerp(this.position, progress));
            }
        }
    }
}