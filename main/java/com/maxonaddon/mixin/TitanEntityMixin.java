package com.maxonaddon.mixin;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerLevelAccessor;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class TitanEntityMixin {

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void onSpawnRedEyed(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnData, CallbackInfoReturnable<SpawnGroupData> cir) {
        Mob self = (Mob) (Object) this;

        if (!self.level().isClientSide()) {
            String className = self.getClass().getName();

            if (className.startsWith("daot.") && !className.contains("Eye") && !className.contains("Nape")) {

                // Проверяем погодные условия: если идет дождь, шанс спавна 40%, иначе 10%
                boolean isRaining = self.level().isRaining();
                float spawnChance = isRaining ? 0.70f : 0.10f;

                if (self.getRandom().nextFloat() < spawnChance) {
                    self.addTag("RedEyedTitan");

                    // Увеличиваем урон ровно в 2 раза
                    if (self.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                        double currentDamage = self.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                        self.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(currentDamage * 2.0);
                    }

                    // Увеличиваем скорость ровно в 2 раза
                    if (self.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                        double currentSpeed = self.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
                        self.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(currentSpeed * 2.0);
                    }
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickRedEyed(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;

        if (!self.level().isClientSide() && self.getTags().contains("RedEyedTitan")) {

            if (self.tickCount % 5 == 0) {
                // Используем поворот ГОЛОВЫ, а не тела, чтобы частицы следовали за лицом
                float headYawRad = (float) Math.toRadians((double) self.yHeadRot);

                // Высчитываем смещение вперед на основе ширины хитбокса (чтобы частицы были на лице, а не в центре мозга)
                double forwardOffset = self.getBbWidth() * 0.45;

                // Рассчитываем координаты
                double eyeX = self.getX() - Math.sin(headYawRad) * forwardOffset;
                // У любого гуманоида глаза находятся примерно на 85% от его общей высоты
                double eyeY = self.getY() + (self.getBbHeight() * 0.85);
                double eyeZ = self.getZ() + Math.cos(headYawRad) * forwardOffset;

                ServerLevel serverLevel = (ServerLevel) self.level();

                // Чуть уменьшил размер частиц (до 1.0f), чтобы они не перекрывали весь обзор титану
                DustParticleOptions redstoneEffect = new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 1.0f);

                serverLevel.sendParticles(
                        redstoneEffect,
                        eyeX, eyeY, eyeZ,
                        3,
                        0.15, 0.05, 0.15, // Значительно уменьшил разброс (dx, dy, dz), чтобы светились именно глаза
                        0.0
                );
            }
        }
    }
}