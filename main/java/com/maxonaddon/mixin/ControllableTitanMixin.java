package com.maxonaddon.mixin;

import com.maxonaddon.RoyalTitanInteractHandler;
import com.maxonaddon.util.IPlayerStats;
import daot.ShifterTitan;
import daot.VillagerTransformTracker;
import daot.BloodlineData; // Новый импорт
import daot.BloodlineType; // Новый импорт
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(LivingEntity.class)
public abstract class ControllableTitanMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickControlled(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        // Отсеиваем шифтеров правильно!
        if (self instanceof ShifterTitan) return;

        String name = self.getClass().getName();
        if (name.startsWith("daot.") && name.contains("Titan") && !name.contains("Eye") && !name.contains("Nape") && !name.contains("Grab")) {

// --- КИНЕМАТОГРАФИЧНОЕ ИСЦЕЛЕНИЕ ---
            if (RoyalTitanInteractHandler.sacrificingTitans.containsKey(self.getUUID())) {

                // Останавливаем титана на клиенте и сервере
                self.setDeltaMovement(0.0, self.getDeltaMovement().y, 0.0);

                // Таймер должен тикать ТОЛЬКО на сервере, иначе ломается логика
                if (!self.level().isClientSide()) {
                    AttributeInstance attackDamage = self.getAttribute(Attributes.ATTACK_DAMAGE);
                    if (attackDamage != null) {
                        attackDamage.setBaseValue(0.0);
                    }

                    int ticks = RoyalTitanInteractHandler.sacrificingTitans.get(self.getUUID());
                    ticks++;
                    RoyalTitanInteractHandler.sacrificingTitans.put(self.getUUID(), ticks);

                    Player owner = null;
                    try {
                        String ownerName = VillagerTransformTracker.getOwnerName(self);
                        if (ownerName != null && self.level().getServer() != null) {
                            owner = self.level().getServer().getPlayerList().getPlayerByName(ownerName);
                        }
                    } catch (Exception ignored) {}

                    if (owner != null && owner.distanceTo(self) < 10.0D) {
                        // ДАЕМ БЕССМЕРТИЕ (Максимальное сопротивление урону + Поглощение)
                        owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 255, false, false, false));
                        owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20, 5, false, false, false));

                        // Полностью сбрасываем шкалу огня/урона если титан пытается нанести урон
                        owner.hurtTime = 0;

                        if (ticks % 4 == 0 && owner.level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, owner.getX(), owner.getY() + 1.0, owner.getZ(), 15, 0.3, 0.5, 0.3, 0.05);
                            serverLevel.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 1.0, owner.getZ(), 5, 0.3, 0.5, 0.3, 0.05);
                        }
                    }

                    try {
                        Field eatTicksField = self.getClass().getDeclaredField("eatTicks");
                        eatTicksField.setAccessible(true);
                        eatTicksField.setInt(self, 10);
                    } catch (Exception ignored) {}

                    // Исцеляем и убиваем титана ровно через 5 секунд (100 тиков)!
                    if (ticks >= 100) {
                        RoyalTitanInteractHandler.completeHealing(self);
                    }
                }
                return; // Блокируем WASD
            }
            // --- УПРАВЛЕНИЕ И БЕГ ---
            if (!self.level().isClientSide() && self.getFirstPassenger() instanceof Player player) {

                // ИСПРАВЛЕНИЕ 1: Блокируем код управления, если игрок находится в пасти
                if (player.getTags().contains("dannys-aot:being_grabbed")) {
                    return;
                }
                if (player.isInvisible()) {
                    return;
                }

                // ИСПРАВЛЕНИЕ 2: Проверка на Королевскую волю для управления чистым титаном
                boolean canControl = false;
                if (self.level() instanceof ServerLevel sl) {
                    BloodlineData data = BloodlineData.get(sl);
                    if (data.getBloodline(player.getUUID()) == BloodlineType.ROYAL) {
                        IPlayerStats stats = (IPlayerStats) player;

                        // ПРОВЕРКА НАВЫКА
                        if (stats.hasSkill("royal_control_1")) {
                            String ownerName = VillagerTransformTracker.getOwnerName(self);
                            if (ownerName != null && ownerName.equals(player.getGameProfile().getName())) {
                                canControl = true;
                            }
                        }
                    }
                }

                // Если игрок не прошел проверку на власть - отключаем насильное управление
                if (!canControl) {
                    return;
                }

                // Если проверки пройдены, применяем ручное WASD-управление
                if (self instanceof Mob mob) {
                    mob.setTarget(null);
                    boolean isSprinting = player.isSprinting();
                    try {
                        Method setAggressive = self.getClass().getMethod("setAggressive", boolean.class);
                        setAggressive.invoke(self, isSprinting);
                    } catch (Exception ignored) {}
                }

                self.setYRot(player.getYRot());
                self.yRotO = player.getYRot();
                self.yBodyRot = player.getYRot();
                self.yHeadRot = player.getYRot();

                AttributeInstance speedAttribute = self.getAttribute(Attributes.MOVEMENT_SPEED);
                double baseSpeed = 0.27 * 1.5;
                double sprintSpeed = baseSpeed * 2.0;
                double currentSpeed = player.isSprinting() ? sprintSpeed : baseSpeed;

                if (speedAttribute != null) {
                    speedAttribute.setBaseValue(currentSpeed);
                }

                float forward = player.zza;
                float strafe = player.xxa;

                if (forward != 0.0F || strafe != 0.0F) {
                    float cameraYaw = player.getYRot();
                    float relativeRadians = (float) Math.toRadians(cameraYaw);
                    float sin = (float) Math.sin(relativeRadians);
                    float cos = (float) Math.cos(relativeRadians);

                    float localX = strafe * cos - forward * sin;
                    float localZ = strafe * sin + forward * cos;

                    Vec3 moveDir = new Vec3(localX, 0.0, localZ).normalize();
                    self.setDeltaMovement(moveDir.x * currentSpeed, self.getDeltaMovement().y, moveDir.z * currentSpeed);
                } else {
                    self.setDeltaMovement(0.0, self.getDeltaMovement().y, 0.0);
                }
            }
        }
    }
}