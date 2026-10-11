package com.maxonaddon.mixin;

import daot.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class UniversalTitanDeathMixin {

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void customTickDeath(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        // Включаем всех, кроме базового TitanEntity
        boolean isOurTitan =
                        self instanceof CrawlerTitanEntity ||
                        self instanceof YellowTitanEntity ||
                        self instanceof ConnieFatherEntity ||
                        self instanceof SmallTitan2Entity ||
                        self instanceof SmallTitanEntity ||
                        self instanceof SadTitanEntity ||
                        self instanceof TitanTropicalEntity ||
                        self instanceof AbnormalTitanEntity ||
                        self instanceof CrawlingAbnormalTitanEntity ||
                        self instanceof FritzTitanEntity ||
                        self instanceof TitanEntity ||
                        self instanceof TitanBeardEntity;

        if (isOurTitan && com.maxonaddon.MaxonConfig.get().enableTitanSkeletons) {
            self.deathTime++;

            float yaw = self.getYRot() * ((float) Math.PI / 180F);
            double forwardX = -Math.sin(yaw);
            double forwardZ = Math.cos(yaw);

// 1. ВЗРЫВ ПАРА и ЗВУК через 5 секунд (100-й тик)
            if (self.deathTime == 100) {
                if (!self.level().isClientSide()) {
                    self.level().playSound(null, self.getX(), self.getY(), self.getZ(),
                            SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.HOSTILE, 1.5F, 1.0F);
                }

                if (self.level().isClientSide()) {
                    for (int i = 0; i <= 4; i++) {
                        double offset = self.getBbHeight() * (i / 4.0);
                        for (int j = 0; j < 30; j++) {
                            double oX = (self.getRandom().nextDouble() - 0.5) * self.getBbWidth();
                            double oZ = (self.getRandom().nextDouble() - 0.5) * self.getBbWidth();
                            self.level().addParticle(ParticleTypes.CLOUD,
                                    self.getX() + forwardX * offset + oX, self.getY() + 0.5, self.getZ() + forwardZ * offset + oZ,
                                    0, 0.05, 0);
                        }
                    }
                }
            }

// 2. Дымится, пока лежит скелет (от 100 до 200 тика)
            if (self.deathTime > 100 && self.deathTime < 200 && self.level().isClientSide()) {
                double rOffset = self.getRandom().nextDouble() * self.getBbHeight();
                self.level().addParticle(ParticleTypes.CLOUD, self.getX() + forwardX * rOffset, self.getY() + 0.5, self.getZ() + forwardZ * rOffset, 0, 0.02, 0);
            }

// 3. Финальное исчезновение через 10 секунд (200-й тик)
            if (self.deathTime >= 200) {
                if (self.level().isClientSide()) {
                    for (int i = 0; i <= 4; i++) {
                        double offset = self.getBbHeight() * (i / 4.0);
                        for (int j = 0; j < 10; j++) {
                            self.level().addParticle(ParticleTypes.CLOUD, self.getX() + forwardX * offset, self.getY() + 0.5, self.getZ() + forwardZ * offset, 0, 0.05, 0);
                        }
                    }
                }

                if (!self.level().isClientSide()) {
                    // Скелет исчезает — снимаем метку захвата с тех, кого он ещё держит.
                    // Сам remove() пассажиров высадит, а вот тег being_grabbed снимается
                    // только в TitanEntity.die(), и для титана, схватившего кого-то уже
                    // будучи мёртвым, он больше не вызовется. Без этой уборки жертва
                    // остаётся помеченной схваченной навсегда.
                    for (Entity passenger : self.getPassengers()) {
                        passenger.removeTag("dannys-aot:being_grabbed");
                    }
                    self.remove(Entity.RemovalReason.KILLED);
                }
            }

            ci.cancel();
        }
    }
}