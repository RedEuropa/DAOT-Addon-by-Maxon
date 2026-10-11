package com.maxonaddon.mixin;

import com.maxonaddon.MaxonConfig;
import daot.FlareProjectileEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlareProjectileEntity.class)
public abstract class FlareProjectileEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        FlareProjectileEntity flare = (FlareProjectileEntity) (Object) this;

        // Проверяем первый тик снаряда на сервере
        if (!flare.level().isClientSide() && flare.tickCount == 1) {
            // 2 — это фиолетовый цвет (PURPLE)
            if (flare.getFlareColorOrdinal() == 2) {
                if (flare.getOwner() instanceof ServerPlayer player) {

                    // === БЕЗОПАСНАЯ ПРОВЕРКА ИГРОВОГО ПРАВИЛА ===
                    boolean allowFlares = true; // Включено по умолчанию
                    try {
                        if (MaxonConfig.ALLOW_FLARE_REINFORCEMENTS != null) {
                            var rule = flare.level().getGameRules().getRule(MaxonConfig.ALLOW_FLARE_REINFORCEMENTS);
                            if (rule != null) {
                                allowFlares = rule.get();
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибку, если правило не найдено
                    }

                    if (allowFlares) {
                        com.maxonaddon.util.ReinforcementsHandler.callScouts(player, (ServerLevel) flare.level());
                    }
                }
            }
        }
    }
}