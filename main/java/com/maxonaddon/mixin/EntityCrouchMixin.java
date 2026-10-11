package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.TraumaType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityCrouchMixin {

    // Перехватываем проверку на присед у любой сущности
    @Inject(method = "isCrouching", at = @At("HEAD"), cancellable = true)
    private void maxon$forceTraumaCrouch(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player) {
            IPlayerStats stats = (IPlayerStats) player;
            // Если ноги сломаны, насильно включаем визуальную анимацию приседа
            if (stats.hasTrauma(TraumaType.LEGS)) {
                cir.setReturnValue(true);
            }
        }
    }
}