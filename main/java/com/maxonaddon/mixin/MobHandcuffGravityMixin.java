package com.maxonaddon.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobHandcuffGravityMixin {

    @Unique
    private static final Logger MAXON$LOG = LogUtils.getLogger();

    /** Отладочные логи. Выключи (false), когда всё заработает. */
    @Unique
    private static final boolean MAXON$DEBUG = true;

    @Unique
    private static final int[][] MAXON$OFFSETS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    @Unique
    private boolean maxon$freedLogged = false;

    @Unique
    private int maxon$unstuckCooldown = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void maxon$vesselGravityTick(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (mob.level().isClientSide()) return;

        boolean isCuffed = mob.getTags().contains("handcuffed");
        boolean isWeakened = mob.getTags().contains("weakened_shifter_vessel");
        if (!isCuffed && !isWeakened) return;

        Entity vehicle = mob.getVehicle();

        // 1. Игрок несёт на руках — не вмешиваемся
        if (vehicle instanceof Player) return;

        if (vehicle != null) {
            boolean ghost = vehicle.isRemoved() || !vehicle.isAlive();

            // Живой титан во время анимации смерти — не трогаем, это задумка
            if (!ghost) return;

            // Вежливый способ
            mob.stopRiding();

            // Если не сработало (stopRiding перехвачен сторонним модом) —
            // рвём связь напрямую, с обеих сторон
            if (mob.isPassenger()) {
                maxon$forceUnride(mob, vehicle);

                if (MAXON$DEBUG && !maxon$freedLogged) {
                    maxon$freedLogged = true;
                    MAXON$LOG.info("[MaxonAddon/VESSEL] stopRiding() заблокирован — разорвал связь напрямую ({})",
                            vehicle.getClass().getName());
                }
            } else if (MAXON$DEBUG && !maxon$freedLogged) {
                maxon$freedLogged = true;
                MAXON$LOG.info("[MaxonAddon/VESSEL] Житель отцеплен от трупа штатно");
            }

            mob.setNoGravity(false);
            mob.setDeltaMovement(0.0D, -0.08D, 0.0D);
            mob.fallDistance = 0.0F;
            mob.hasImpulse = true;
        }

        // 2. Флаги, оставшиеся от езды
        if (mob.isNoGravity()) {
            mob.setNoGravity(false);
            mob.hasImpulse = true;
        }
        if (mob.noPhysics) {
            mob.noPhysics = false;
        }

        // 3. Вытаскивание из блоков (защита от удушья при падении с титана)
        if (maxon$unstuckCooldown > 0) {
            maxon$unstuckCooldown--;
        } else if (maxon$pushOutOfBlocks(mob)) {
            maxon$unstuckCooldown = 10;
        }

        // 4. Ручная гравитация: при NoAI=true ванильный travel() её не считает
        if (!mob.isNoAi()) return;

        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(motion.x, motion.y - 0.08D, motion.z);
        mob.move(MoverType.SELF, mob.getDeltaMovement());

        if (mob.onGround()) {
            mob.setDeltaMovement(0, 0, 0);
        } else {
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.98D));
        }

        mob.hasImpulse = true;
    }

    /** Рвёт связь пассажир-транспорт напрямую через поля, минуя stopRiding(). */
    @Unique
    private void maxon$forceUnride(Mob mob, Entity vehicle) {
        // Сторона пассажира
        ((EntityVehicleAccessor) mob).maxon$setVehicleField(null);

        // Сторона транспорта: убираем себя из списка пассажиров
        EntityVehicleAccessor vehicleAccess = (EntityVehicleAccessor) vehicle;
        ImmutableList<Entity> current = vehicleAccess.maxon$getPassengersField();

        if (current != null && current.contains(mob)) {
            ImmutableList.Builder<Entity> rest = ImmutableList.builder();
            for (Entity passenger : current) {
                if (passenger != mob) rest.add(passenger);
            }
            vehicleAccess.maxon$setPassengersField(rest.build());
        }
    }

    /** @return true, если моба реально пришлось вытаскивать из блоков */
    @Unique
    private boolean maxon$pushOutOfBlocks(Mob mob) {
        Level level = mob.level();
        if (level.noCollision(mob, mob.getBoundingBox().deflate(1.0E-4))) return false;

        BlockPos base = mob.blockPosition();

        for (int dy = 0; dy <= 10; dy++) {
            for (int[] off : MAXON$OFFSETS) {
                BlockPos candidate = base.offset(off[0], dy, off[1]);

                double x = candidate.getX() + 0.5D;
                double y = candidate.getY() + 0.05D;
                double z = candidate.getZ() + 0.5D;

                AABB target = mob.getBoundingBox().move(x - mob.getX(), y - mob.getY(), z - mob.getZ());

                if (level.noCollision(mob, target)) {
                    mob.teleportTo(x, y, z);
                    mob.setDeltaMovement(Vec3.ZERO);
                    mob.fallDistance = 0.0F;
                    mob.hasImpulse = true;
                    if (MAXON$DEBUG) {
                        MAXON$LOG.info("[MaxonAddon/VESSEL] Вытащил из блоков {} -> {}", base, candidate);
                    }
                    return true;
                }
            }
        }
        return false;
    }
}