package com.maxonaddon.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class CrawlerTitanEatMixin {

    // Флаг, чтобы избежать бесконечного цикла урона
    private static boolean maxon$isHandlingEat = false;

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void maxon$onCrawlerFatalHit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (maxon$isHandlingEat) return;

        LivingEntity victim = (LivingEntity) (Object) this;
        if (victim.level().isClientSide()) return;

        // Если урон смертельный (здоровье упадет до 0 или ниже)
        if (victim.getHealth() - amount <= 0.0f) {
            Entity attacker = source.getEntity();

            // Если убивает именно Ползущий Титан, которым управляет игрок
            if (attacker instanceof daot.CrawlerTitanEntity crawler) {
                if (crawler.getFirstPassenger() instanceof ServerPlayer player) {

                    maxon$isHandlingEat = true;
                    try {
                        // Используем рефлексию, чтобы запустить механику DAOT
                        for (java.lang.reflect.Method method : daot.TitanPowerHelper.class.getDeclaredMethods()) {
                            if (method.getName().equals("handleEatingCompletion")) {
                                method.setAccessible(true);

                                // Вызываем: handleEatingCompletion(титан, жертва)
                                // Это заставит DAOT выдать силу игроку и отправить его в Пути!
                                method.invoke(null, crawler, victim);

                                // Отменяем ванильную смерть от удара (цель уже убита скриптом поедания)
                                cir.setReturnValue(false);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        maxon$isHandlingEat = false;
                    }
                }
            }
        }
    }
}