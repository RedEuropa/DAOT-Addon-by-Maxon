package com.maxonaddon.mixin;

import com.maxonaddon.network.JawQteStatePayload;
import com.maxonaddon.util.IJawQteHandler;
import daot.CrystalShellWarhammerEntity;
import daot.TestShifterTitanEntity;
import daot.WarhammerTitanEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(TestShifterTitanEntity.class)
public abstract class TestShifterTitanQteMixin extends Monster implements IJawQteHandler {

    @Unique private CrystalShellWarhammerEntity maxon$heldCrystal = null;
    @Unique private int maxon$qteProgress = 0;
    @Unique private int maxon$nextRequiredKey = 0; // 0 = ЛКМ, 1 = ПКМ
    @Unique private int maxon$moveDecayTimer = 0;

    @Unique private boolean maxon$isMoving = false;
    @Unique private double maxon$lastX = 0.0;
    @Unique private double maxon$lastZ = 0.0;

    protected TestShifterTitanQteMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "triggerAbility", at = @At("HEAD"), cancellable = true, remap = false)
    private void maxon$onTriggerAbility(int ability, CallbackInfo ci) {
        if (ability == 5) {
            if (this.maxon$heldCrystal == null) {
                List<CrystalShellWarhammerEntity> crystals = this.level().getEntitiesOfClass(
                        CrystalShellWarhammerEntity.class, this.getBoundingBox().inflate(12.0)
                );
                if (!crystals.isEmpty()) {
                    crystals.sort((c1, c2) -> Double.compare(this.distanceToSqr(c1), this.distanceToSqr(c2)));
                    this.maxon$startCrystalBite(crystals.get(0));
                }
            } else {
                this.maxon$resetQte();
            }
        } else if (this.maxon$heldCrystal != null) {
            ci.cancel();
        }
    }

    @Inject(method = "triggerAttack", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void maxon$blockLmb(CallbackInfo ci) {
        if (this.maxon$heldCrystal != null) ci.cancel();
    }

    @Inject(method = "triggerArm", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void maxon$blockRmbDrop(CallbackInfo ci) {
        if (this.maxon$heldCrystal != null) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void maxon$tickQte(CallbackInfo ci) {
        if (this.level().isClientSide()) return;

        double dx = this.getX() - this.maxon$lastX;
        double dz = this.getZ() - this.maxon$lastZ;
        this.maxon$isMoving = (dx * dx + dz * dz) > 0.001;

        this.maxon$lastX = this.getX();
        this.maxon$lastZ = this.getZ();

        if (maxon$heldCrystal == null) return;

        if (!maxon$heldCrystal.isAlive() || this.distanceTo(maxon$heldCrystal) > 12.0 || this.hurtTime == 10 || this.getControllingPassenger() == null) {
            maxon$resetQte();
            return;
        }

        // --- УБЫВАНИЕ ПОЛОСКИ ДЛЯ JAW ТИТАНА (1 раз в секунду / 20 тиков) ---
        if (maxon$qteProgress > 0) {
            maxon$moveDecayTimer++;
            if (maxon$moveDecayTimer >= 20) {
                maxon$moveDecayTimer = 0;
                maxon$qteProgress = Math.max(0, maxon$qteProgress - 1);
                maxon$syncQte(); // Отправляем новый прогресс клиенту, полоска сдвинется назад
            }
        } else {
            maxon$moveDecayTimer = 0;
        }
    }

    @Override
    public void handleQtePress(int key) {
        if (maxon$heldCrystal == null || !maxon$heldCrystal.isAlive()) return;

        if (this.maxon$isMoving) return;

        if (key == maxon$nextRequiredKey) {
            maxon$qteProgress++;
            maxon$nextRequiredKey = (maxon$nextRequiredKey == 0) ? 1 : 0;

            this.level().playSound(
                    null,
                    this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    1.5F,
                    0.8F + (this.random.nextFloat() * 0.4F)
            );

            if (maxon$qteProgress >= 20) {
                this.level().playSound(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        3.0F,
                        0.5F
                );
                maxon$executeCrystalCrush();
            } else {
                maxon$syncQte();
            }
        }
    }

    @Unique
    private void maxon$executeCrystalCrush() {
        if (maxon$heldCrystal == null) return;

        int parentTitanId = maxon$heldCrystal.getParentTitanId();
        Entity parentTitan = this.level().getEntity(parentTitanId);
        DamageSource source = this.damageSources().mobAttack(this);

        // 1. Убиваем Вархаммера
        if (parentTitan instanceof WarhammerTitanEntity warhammer) {
            Mob nape = null;
            try {
                java.lang.reflect.Method m = warhammer.getClass().getMethod("getNapeEntity");
                nape = (Mob) m.invoke(warhammer);
            } catch (Exception ignored) {}

            if (nape != null && nape.isAlive()) {
                nape.setHealth(0.0F);
                nape.hurt(source, 500000.0F);
                nape.die(source);
            }
            warhammer.setHealth(0.0F);
            warhammer.hurt(source, 500000.0F);
            warhammer.die(source);
        }

        // 2. Обрабатываем ВСЕХ пассажиров (чтобы житель или игрок упали и умерли)
        List<Entity> passengers = new ArrayList<>(maxon$heldCrystal.getPassengers());
        for (Entity passenger : passengers) {
            passenger.stopRiding();           // Отцепляем от кристалла
            passenger.setInvisible(false);    // Снимаем невидимость (мод прячет жителя внутри кристалла)
            passenger.setNoGravity(false);    // Возвращаем гравитацию, чтобы тело упало

            if (passenger instanceof LivingEntity living) {
                living.setHealth(0.0F);
                living.hurt(source, 500000.0F);
                living.die(source);
            }
        }

        // 3. Уничтожаем кристалл
        maxon$heldCrystal.discard();
        maxon$resetQte();
    }

    @Unique
    private void maxon$startCrystalBite(CrystalShellWarhammerEntity crystal) {
        this.maxon$heldCrystal = crystal;
        this.maxon$qteProgress = 0;
        this.maxon$nextRequiredKey = 0;
        this.maxon$moveDecayTimer = 0;
        this.maxon$isMoving = false;

        this.maxon$lastX = this.getX();
        this.maxon$lastZ = this.getZ();

        this.maxon$syncQte();
    }

    @Unique
    private void maxon$resetQte() {
        this.maxon$heldCrystal = null;
        this.maxon$qteProgress = 0;

        LivingEntity controller = this.getControllingPassenger();
        if (controller instanceof ServerPlayer player) {
            ServerPlayNetworking.send(player, new JawQteStatePayload(false, 0, 0));
        }
    }

    @Unique
    private void maxon$syncQte() {
        LivingEntity controller = this.getControllingPassenger();
        if (controller instanceof ServerPlayer player) {
            ServerPlayNetworking.send(player, new JawQteStatePayload(
                    true,
                    maxon$qteProgress,
                    maxon$nextRequiredKey
            ));
        }
    }
}