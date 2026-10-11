package com.maxonaddon.mixin;

import com.maxonaddon.ApgConfig;
import daot.APGProjectileEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(APGProjectileEntity.class)
public abstract class APGProjectileEntityMixin extends AbstractArrow {

    protected APGProjectileEntityMixin(net.minecraft.world.entity.EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
    }

    // Добавляем cancellable = true, чтобы прервать ванильный полет пули
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void enhanceAPGDynamics(CallbackInfo ci) {
        APGProjectileEntity apg = (APGProjectileEntity) (Object) this;
        Level level = apg.level();

        if (level.isClientSide()) {
            return;
        }

        if (!level.getGameRules().getBoolean(ApgConfig.DO_CUSTOM_APG)) {
            return;
        }

        // 1. Raycast (Hitscan) делаем только в первый тик жизни пули
        if (apg.tickCount == 1) {
            double range = 100.0D; // Максимальная дальность стрельбы (в блоках)

            Vec3 startVec = apg.position();
            // Вектор направления берем из изначального импульса выстрела
            Vec3 lookVec = apg.getDeltaMovement().normalize();
            Vec3 endVec = startVec.add(lookVec.scale(range));

            // 1.1 Проверка препятствий (чтобы не простреливать сквозь стены)
            HitResult blockHit = level.clip(new ClipContext(startVec, endVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, apg));
            if (blockHit.getType() != HitResult.Type.MISS) {
                endVec = blockHit.getLocation(); // Укорачиваем луч до стены, если попали в нее
            }

            // 1.2 Поиск сущностей на пути луча (от старта до стены/конца дальности)
            AABB searchBox = apg.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, apg, startVec, endVec, searchBox, e -> e.isAlive() && e != apg.getOwner());

            // 1.3 Обработка попадания
            if (entityHit != null && entityHit.getEntity() != null) {
                Entity target = entityHit.getEntity();
                String className = target.getClass().getSimpleName();

                boolean isPlayer = target instanceof Player;
                boolean isNape = className.contains("Nape");
                boolean isEye = className.contains("Eye");

                // Попали в нужную цель - наносим урон
                if (isPlayer || isNape || isEye) {
                    DamageSource source = apg.damageSources().arrow(apg, apg.getOwner() != null ? apg.getOwner() : apg);
                    target.hurt(source, 13.65F); // Тот самый урон из твоего изначального кода
                }
            }

            // 1.4 Отрисовка трассера (опционально, делает выстрел приятным визуально)
            // Так как пуля удаляется мгновенно, рисуем полосу из частиц от дула до точки попадания
            if (level instanceof ServerLevel serverLevel) {
                double distance = startVec.distanceTo(endVec);
                for (double d = 0; d < distance; d += 1.5D) {
                    Vec3 particlePos = startVec.add(lookVec.scale(d));
                    // Используем частицы крита для имитации следа выстрела
                    serverLevel.sendParticles(ParticleTypes.FLAME, particlePos.x, particlePos.y, particlePos.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }

            // 1.5 Моментально удаляем сущность пули и отменяем ее дальнейший полет
            apg.discard();
            ci.cancel();
        }
    }
}