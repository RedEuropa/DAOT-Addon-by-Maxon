package com.maxonaddon.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class PureTitanSleepMixin {

    // @Unique создает отдельную переменную для каждого конкретного титана.
    // Значение -1 означает, что таймер еще не запущен.
    @Unique
    private int sleepDelayTimer = -1;

    // === СТРАХОВКА ОТ "СТОЯНИЯ КАК ВКОПАННЫЙ" ===
    // Родной daot.TitanEntity$TitanWanderGoal (проверено по байткоду) бродит ТОЛЬКО когда
    // getTarget() == null — если у титана рядом задержалась цель (например, житель в
    // уменьшенном FOLLOW_RANGE) или он просто давно ни на что не реагировал, встроенный
    // wander может подолгу не срабатывать. Отслеживаем реальное смещение и, если титан
    // не двигался слишком долго (и ещё не спит), сами даём ему команду прогуляться —
    // тем же приёмом stuckTicks/lastX/lastZ, что и в RumblingWalkGoal.
    @Unique private double lastX, lastZ;
    @Unique private int stuckTicks = 0;
    @Unique private static final int STUCK_THRESHOLD = 100; // 5 секунд без движения

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickGradualSleep(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        Level level = self.level();

        if (level.isClientSide()) {
            return;
        }

        String className = self.getClass().getName();

        if (className.startsWith("daot.")) {
            // Исключения (глаза, затылки, манекены, шифтеры и аномальные)
            if (className.contains("Eye") || className.contains("Nape") || className.contains("CrystalShell")) return;
            if (className.contains("Dummy")) return;
            if (className.contains("Armored") || className.contains("Attack") ||
                    className.contains("Beast") || className.contains("Colossal") ||
                    className.contains("Female") || className.contains("Warhammer")) return;

            if (self.getTags().contains("RedEyedTitan")) {
                if (self.isNoAi()) self.setNoAi(false);
                return;
            }

            int lightLevel = level.getMaxLocalRawBrightness(self.blockPosition());
            boolean sleepy = lightLevel < 9; // стадии 2 и 3 — уже не полностью бодрствует

            if (lightLevel >= 9) {
                // --- СТАДИЯ 1: Активен (День) ---
                this.sleepDelayTimer = -1; // Сбрасываем таймер сна
                if (self.isNoAi()) self.setNoAi(false);

                AttributeInstance follow = self.getAttribute(Attributes.FOLLOW_RANGE);
                if (follow != null && follow.getBaseValue() < 40.0) {
                    follow.setBaseValue(40.0);
                }

            } else if (lightLevel >= 4) {
                // --- СТАДИЯ 2: Промежуточная (Сумерки) ---
                this.sleepDelayTimer = -1; // Сбрасываем таймер сна
                if (self.isNoAi()) self.setNoAi(false);

                // Легкая слабость
                self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
                self.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));

                AttributeInstance follow = self.getAttribute(Attributes.FOLLOW_RANGE);
                if (follow != null) follow.setBaseValue(12.0);

                if (self.getTarget() != null && self.distanceTo(self.getTarget()) > 12.0) {
                    self.setTarget(null);
                }

            } else {
                // --- СТАДИЯ 3: Ночь (Попытка уснуть) ---

                // Если таймер еще не был запущен, задаем случайное время от 0 до 4800 тиков (0 - 4 минуты)
                if (this.sleepDelayTimer == -1) {
                    this.sleepDelayTimer = self.getRandom().nextInt(4800);
                }

                if (this.sleepDelayTimer > 0) {
                    // Титан "борется со сном" - таймер тикает
                    this.sleepDelayTimer--;

                    if (self.isNoAi()) self.setNoAi(false);

                    // Делаем его еще более вялым в последние минуты перед сном
                    self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
                    self.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));

                    AttributeInstance follow = self.getAttribute(Attributes.FOLLOW_RANGE);
                    if (follow != null) follow.setBaseValue(8.0); // Почти ничего не видит

                    if (self.getTarget() != null && self.distanceTo(self.getTarget()) > 8.0) {
                        self.setTarget(null);
                    }
                } else {
                    // Таймер вышел — титан окончательно засыпает
                    self.setNoAi(true);
                    if (self.getTarget() != null) {
                        self.setTarget(null);
                    }
                }
            }

            // Пока титан ещё не уснул (не noAi) и должен быть вялым/сонным — следим,
            // что он реально бродит, а не стоит вкопанным между приступами сна.
            if (sleepy && !self.isNoAi()) {
                maxon$preventFreezing(self);
            } else {
                this.stuckTicks = 0;
                this.lastX = self.getX();
                this.lastZ = self.getZ();
            }
        }
    }

    @Unique
    private void maxon$preventFreezing(Mob self) {
        double dx = self.getX() - this.lastX;
        double dz = self.getZ() - this.lastZ;
        boolean barelyMoved = (dx * dx + dz * dz) < 0.04; // меньше 0.2 блока за тик

        if (barelyMoved && self.getTarget() == null) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }

        this.lastX = self.getX();
        this.lastZ = self.getZ();

        if (this.stuckTicks >= STUCK_THRESHOLD && self.getNavigation().isDone()) {
            this.stuckTicks = 0;

            double angle = self.getRandom().nextDouble() * Math.PI * 2.0;
            double dist = 5.0 + self.getRandom().nextDouble() * 10.0;
            Vec3 dest = self.position().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);

            self.getNavigation().moveTo(dest.x, dest.y, dest.z, 0.6);
        }
    }
}
