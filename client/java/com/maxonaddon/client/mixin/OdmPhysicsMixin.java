package com.maxonaddon.client.mixin;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.mixin.EntityVehicleAccessor;
import com.maxonaddon.client.OdmClientState;
import com.maxonaddon.network.PullEntityPayload;
import com.maxonaddon.network.RequestJamPayload;
import daot.AwakenedPowerClientData;
import daot.BloodlineClientData;
import daot.DannysAot;
import daot.HookPoint;
import daot.ODMGasHUD;
import daot.ODMSoundManager;
import daot.TitanBloodlineClientData;
import daot.network.GasSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * Своя физика полёта на тросах: длина троса управляется вручную, трос натягивается, а
 * зацепленных мобов можно подтягивать к себе.
 * <p>
 * Метод базового мода отменяется целиком и считается заново — «дописать» его точечными
 * инъекциями нельзя: длина троса, натяжение и орбита завязаны друг на друга и на одни и те же
 * клавиши. Всё, что делает оригинал (расход газа, звуки, частицы, заклинивание, бонусы
 * Аккерманов и родословных), здесь воспроизведено — иначе вместе с физикой отвалилось бы и оно.
 * <p>
 * Что здесь своего по сравнению с оригиналом:
 * <ul>
 *   <li>трос имеет длину: W её сматывает, шифт стравливает, и на пределе трос натягивается,
 *       гася скорость «наружу» вместо мгновенного рывка. W при этом только подтягивает к
 *       точке зацепа — набор высоты идёт маятником, а газ на пробеле служит для манёвра;</li>
 *   <li>зацеп за сущность больше не тянет игрока к мобу — наоборот, моб тянется к игроку;</li>
 *   <li>на лошади тросы только удлиняются за всадником и никуда его не тащат.</li>
 * </ul>
 * <p>
 * Выключается настройкой {@code enableOdmPhysics}: тогда метод не отменяется и работает
 * оригинальная механика daot.
 */
@Environment(EnvType.CLIENT)
@Mixin(value = daot.ODMTickHandler.class, remap = false)
public abstract class OdmPhysicsMixin {

    @Shadow private static Map<UUID, Boolean> wasDualHooking;
    @Shadow private static Map<UUID, Long> dualHookStartTime;
    @Shadow private static Map<UUID, Double> boostLevels;
    @Shadow private static Map<UUID, Integer> gasTickCounter;
    @Shadow private static Map<UUID, Boolean> perchingForGas;
    @Shadow private static Map<UUID, Vec3> storedMomentum;
    @Shadow private static boolean currentlyBoosting;

    @Shadow private static int getGasTickInterval() { return 0; }
    @Shadow private static int getGasConsumptionNormal() { return 0; }
    @Shadow private static int getGasConsumptionBoost() { return 0; }
    @Shadow private static double getBasePullSpeed() { return 0; }
    @Shadow private static double getBaseOrbitSpeed() { return 0; }
    @Shadow private static double getDualHookOrbitMultiplier() { return 0; }
    @Shadow private static double getBoostPullMultiplier() { return 0; }
    @Shadow private static double getBoostOrbitMultiplier() { return 0; }
    @Shadow private static double getBoostRampRate() { return 0; }
    @Shadow private static double getBoostDecayRate() { return 0; }
    @Shadow private static double getMaxHookDistance() { return 0; }
    @Shadow private static float getODMPitch(net.minecraft.world.entity.player.Player player) { return 0; }
    @Shadow private static void forceRetractHooks(LocalPlayer player, HookPoint leftHook, HookPoint rightHook) {}
    @Shadow private static void spawnBoostParticles(LocalPlayer player) {}

    /** Сколько тиков подряд лебёдка тянет, а игрок не двигается — считаем, что заклинило. */
    @Unique private static int maxon$stuckTicks = 0;
    @Unique private static int maxon$jamSoundsPlayed = 0;

    @Inject(method = "applyHookMovement", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maxon$applyHookMovement(LocalPlayer player, HookPoint leftHook, HookPoint rightHook, CallbackInfo ci) {
        // Метку ставим ДО проверки флага: по ней рывок понимает, что игрок висит на тросе, и
        // с выключенной своей физикой спрыг с лошади иначе перестал бы работать вовсе.
        OdmClientState.lastHookTime = System.currentTimeMillis();
        if (!MaxonConfig.get().enableOdmPhysics) return;

        Minecraft mc = Minecraft.getInstance();

        // === ВЕРХОМ: тросы тянутся следом, но никуда не тащат ===
        // Иначе всадника срывало бы с седла собственным крюком. Транспорт читаем прямо из поля
        // через EntityVehicleAccessor: обычный getVehicle() для вызовов из daot намеренно врёт
        // (см. EntityVehicleMixin), а нам здесь нужна правда.
        Entity realVehicle = ((EntityVehicleAccessor) player).maxon$getVehicleField();
        if (realVehicle instanceof AbstractHorse) {
            // Без навыка «Всадник» в седло мы не вмешиваемся вовсе — крюки туда и не вылетят,
            // потому что для daot игрок останется обычным пассажиром (см. EntityVehicleMixin).
            if (!((com.maxonaddon.util.IPlayerStats) player).hasRiderHooks()) return;

            Vec3 pos = player.position();
            double maxDist = getMaxHookDistance();
            if (leftHook.active) leftHook.maxRopeLength = Math.min(maxDist, pos.distanceTo(leftHook.position) + 2.0);
            if (rightHook.active) rightHook.maxRopeLength = Math.min(maxDist, pos.distanceTo(rightHook.position) + 2.0);
            ci.cancel();
            return;
        }

        // W — лебёдка: подтягивает игрока К ТОЧКЕ ЗАЦЕПА, укорачивая трос. Полёта сама по себе
        // не даёт — высоту и скорость набирают маятником, разгоняясь по дуге.
        // Пробел — газ: им уже маневрируют на выбранной длине троса.
        // Шифт стравливает трос.
        boolean isReeling = mc.options.keyUp.isDown();
        boolean isSneaking = mc.options.keyShift.isDown();

        // === ДЛИНА ТРОСА ===
        if (isReeling) {
            double spool = MaxonConfig.get().odmReelSpeed;
            if (leftHook.active) leftHook.maxRopeLength = Math.max(1.5, leftHook.maxRopeLength - spool);
            if (rightHook.active) rightHook.maxRopeLength = Math.max(1.5, rightHook.maxRopeLength - spool);
        } else if (isSneaking) {
            double unspool = MaxonConfig.get().odmUnspoolSpeed;
            double maxDist = getMaxHookDistance();
            if (leftHook.active) leftHook.maxRopeLength = Math.min(maxDist, leftHook.maxRopeLength + unspool);
            if (rightHook.active) rightHook.maxRopeLength = Math.min(maxDist, rightHook.maxRopeLength + unspool);
        }

        maxon$pullEntity(player, leftHook);
        maxon$pullEntity(player, rightHook);

        // === КУДА ТЯНЕТ ИГРОКА ===
        boolean leftLatched = maxon$isLatched(leftHook);
        boolean rightLatched = maxon$isLatched(rightHook);

        // Крюк, воткнутый в мелкого моба, игрока за собой не тащит — тащат мобa.
        boolean leftPulls = leftLatched && !maxon$isPullable(leftHook.hookedEntity, player);
        boolean rightPulls = rightLatched && !maxon$isPullable(rightHook.hookedEntity, player);

        Vec3 targetPos = null;
        boolean bothHooks = false;
        if (leftPulls && rightPulls) {
            targetPos = leftHook.position.add(rightHook.position).scale(0.5);
            bothHooks = true;
        } else if (leftPulls) {
            targetPos = leftHook.position;
        } else if (rightPulls) {
            targetPos = rightHook.position;
        }

        if (targetPos == null) {
            wasDualHooking.put(player.getUUID(), false);
            dualHookStartTime.remove(player.getUUID());
            ci.cancel();
            return;
        }

        wasDualHooking.put(player.getUUID(), bothHooks);

        Vec3 playerPos = player.position();
        Vec3 direction = targetPos.subtract(playerPos).normalize();
        boolean isGasBoosting = mc.options.keyJump.isDown();

        daot.BladeAnimationHandler.setBoosting(isGasBoosting);
        currentlyBoosting = isGasBoosting && boostLevels.getOrDefault(player.getUUID(), 0.0) > 0.1;

        boolean pressingA = mc.options.keyLeft.isDown();
        boolean pressingD = mc.options.keyRight.isDown();
        boolean isOrbiting = pressingA || pressingD;

        Vec3 currentVel = player.getDeltaMovement();
        double distToTarget = playerPos.distanceTo(targetPos);
        double reelPullForce = 0.0;

        if (isReeling) {
            if (distToTarget > 2.5) {
                reelPullForce = MaxonConfig.get().odmReelPullForce;
            } else if (!MaxonConfig.get().odmAllowSlingshot) {
                // У самого крюка гасим разгон, иначе игрока катапультирует мимо точки зацепа.
                currentVel = currentVel.scale(0.4);
            }
            maxon$trackJam(player, leftHook, rightHook, distToTarget, currentVel);
        } else {
            maxon$stuckTicks = 0;
            maxon$jamSoundsPlayed = 0;
        }

        double boost = boostLevels.getOrDefault(player.getUUID(), 0.0);
        boost = isGasBoosting
                ? Math.min(1.0, boost + getBoostRampRate())
                : Math.max(0.0, boost - getBoostDecayRate());
        boostLevels.put(player.getUUID(), boost);

        boolean isAckerman = BloodlineClientData.isAckerman();
        boolean ogreShifter = player.getTags().contains("ogre_shifter");
        boolean titanBloodline = TitanBloodlineClientData.isActive();
        ItemStack odmGear = player.getItemBySlot(EquipmentSlot.LEGS);

        // === ГАЗ ===
        int tickCount = gasTickCounter.getOrDefault(player.getUUID(), 0) + 1;
        int gasInterval = titanBloodline ? getGasTickInterval() * 5
                : (ogreShifter ? getGasTickInterval() * 3 : getGasTickInterval());

        boolean consumesGas = isGasBoosting || isOrbiting || isReeling;
        perchingForGas.put(player.getUUID(), !consumesGas);

        if (!consumesGas) {
            tickCount = 0;
        } else if (tickCount >= gasInterval) {
            tickCount = 0;
            int baseGas = 0;
            if (isReeling || isOrbiting) {
                baseGas = (int) (getGasConsumptionNormal() * MaxonConfig.get().odmGasMultiplierReel);
            }
            if (isGasBoosting) {
                baseGas = (int) (getGasConsumptionBoost() * MaxonConfig.get().odmGasMultiplierBoost);
            }

            int gasToConsume = isAckerman ? (int) Math.ceil(baseGas * 1.15) : baseGas;
            // Перк «Экономный расход» — та же скидка, что и у остального газа в аддоне.
            double economy = com.maxonaddon.perk.PerkManager.gasEconomy(player);
            if (economy > 0.0) {
                gasToConsume = (int) Math.max(1, Math.round(gasToConsume * (1.0 - economy)));
            }

            DannysAot.consumeGasFromGear(odmGear, gasToConsume, player);
            ClientPlayNetworking.send(new GasSyncPayload(DannysAot.getGasFromGear(odmGear)));
            odmGear.hurtAndBreak(1, player, EquipmentSlot.LEGS);

            if (!DannysAot.gearHasGas(odmGear, player)) {
                ODMGasHUD.showOutOfGas();
                forceRetractHooks(player, leftHook, rightHook);
                boostLevels.put(player.getUUID(), 0.0);
                ci.cancel();
                return;
            }
        }
        gasTickCounter.put(player.getUUID(), tickCount);

        // === СИЛЫ ===
        Vec3 lookVec = player.getLookAngle();
        double gasSpeed = 0.0;
        if (isGasBoosting) {
            gasSpeed = getBasePullSpeed() * 1.5;
            if (boost > 0.01) gasSpeed *= 1.0 + (getBoostPullMultiplier() - 1.0) * boost;
            if (isAckerman && boost > 0.01) {
                gasSpeed *= 1.0 + (AwakenedPowerClientData.isActive() ? 0.3 : 0.2) * boost;
            }
            if (DannysAot.isAPG(odmGear.getItem()) && boost > 0.01) gasSpeed *= 1.2;
            if (titanBloodline) gasSpeed *= 2.0;
        }

        double orbitSpeed = getBaseOrbitSpeed();
        if (bothHooks && isOrbiting) orbitSpeed *= 1.5;
        else if (bothHooks) orbitSpeed *= getDualHookOrbitMultiplier();
        if (boost > 0.01) orbitSpeed *= 1.0 + (getBoostOrbitMultiplier() - 1.0) * boost;
        if (isGasBoosting && isAckerman && boost > 0.01) {
            orbitSpeed *= 1.0 + (AwakenedPowerClientData.isActive() ? 0.3 : 0.2) * boost;
        }
        if (isGasBoosting && DannysAot.isAPG(odmGear.getItem()) && boost > 0.01) orbitSpeed *= 1.2;
        if (isGasBoosting && titanBloodline) orbitSpeed *= 2.0;

        Vec3 orbitForce = Vec3.ZERO;
        if (isOrbiting) {
            Vec3 right = direction.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            if (right.lengthSqr() < 0.01) right = direction.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
            orbitForce = right.scale(pressingD ? orbitSpeed : -orbitSpeed);
        }

        Vec3 newVelocity = currentVel.scale(0.98)
                .add(lookVec.scale(gasSpeed))
                .add(direction.scale(reelPullForce))
                .add(orbitForce)
                .add(direction.scale(0.05));

        if (isSneaking) newVelocity = newVelocity.add(0.0, -0.1, 0.0);
        if (isReeling && !isGasBoosting) {
            double cap = MaxonConfig.get().odmMaxSpeedReelOnly;
            if (newVelocity.length() > cap) newVelocity = newVelocity.normalize().scale(cap);
        }

        newVelocity = maxon$applyRopeTension(playerPos, targetPos, newVelocity, currentVel, lookVec,
                leftHook, rightHook, bothHooks);

        // Затухание вертикали на малой скорости — иначе игрок «дребезжит», вися на тросе.
        double speed = newVelocity.length();
        if (!player.onGround() && !isGasBoosting && !isOrbiting && !isSneaking && !isReeling && speed < 0.3) {
            double damp = 0.3 + 0.7 * (speed / 0.3);
            newVelocity = new Vec3(newVelocity.x, newVelocity.y * damp, newVelocity.z);
        }

        storedMomentum.put(player.getUUID(), newVelocity);
        player.setDeltaMovement(newVelocity);

        maxon$effects(player, playerPos, newVelocity, isGasBoosting, boost);
        ci.cancel();
    }

    /**
     * Натяжение троса: на пределе длины гасим скорость «наружу» и переводим её в движение по
     * дуге, а не дёргаем игрока обратно рывком.
     */
    @Unique
    private static Vec3 maxon$applyRopeTension(Vec3 playerPos, Vec3 targetPos, Vec3 newVelocity,
                                               Vec3 currentVel, Vec3 lookVec,
                                               HookPoint leftHook, HookPoint rightHook, boolean bothHooks) {
        double maxRope = bothHooks
                ? (leftHook.maxRopeLength + rightHook.maxRopeLength) / 2.0
                : (leftHook.active ? leftHook.maxRopeLength : rightHook.maxRopeLength);

        Vec3 nextPos = playerPos.add(newVelocity);
        Vec3 toHookNext = targetPos.subtract(nextPos);
        double nextDist = toHookNext.length();
        if (nextDist <= maxRope) return newVelocity;

        Vec3 pullDirection = toHookNext.normalize();
        Vec3 outward = pullDirection.scale(-1.0);
        double outwardSpeed = newVelocity.dot(outward);

        if (outwardSpeed > 0) {
            newVelocity = newVelocity.subtract(outward.scale(outwardSpeed));
            // Падение под крюком превращаем в мах вперёд — так получается качание на тросе.
            if (currentVel.y < 0 && targetPos.y > playerPos.y) {
                Vec3 horizontal = new Vec3(lookVec.x, 0, lookVec.z).normalize();
                if (horizontal.lengthSqr() < 0.01) {
                    horizontal = new Vec3(newVelocity.x, 0, newVelocity.z).normalize();
                }
                newVelocity = newVelocity.add(horizontal.scale(outwardSpeed * 0.85));
            }
        }
        return newVelocity.add(pullDirection.scale((nextDist - maxRope) * 0.4));
    }

    /** Долетел ли крюк и держится ли он за что-то. */
    @Unique
    private static boolean maxon$isLatched(HookPoint hook) {
        if (hook == null || !hook.active || hook.isRetracting) return false;
        if (hook.isExtending) return false;
        return !maxon$stillFlying(hook);
    }

    /**
     * Крюк ещё в полёте по НАШЕМУ таймеру.
     * <p>
     * У daot время вылета — константа 150 мс, у нас оно считается от расстояния и ускоряется
     * навыком «Ускоренный крюк» (см. HookSpeedHelper и HookPointMixin). Проверяем это здесь
     * явно, а не полагаемся на флаг isExtending: он обновляется только когда кто-нибудь
     * спросит getExtensionProgress, а физика не обязана ждать рендер.
     */
    @Unique
    private static boolean maxon$stillFlying(HookPoint hook) {
        if (!MaxonConfig.get().enableAdvancedHookMechanics) return false;
        if (hook.startPosition == null || hook.position == null) return false;

        double distance = hook.startPosition.distanceTo(hook.position);
        double durationMs = com.maxonaddon.util.HookSpeedHelper.flightDurationMs(distance);
        return System.currentTimeMillis() - hook.shootTime < durationMs;
    }

    /** Кого трос тянет к игроку, а не наоборот: всё некрупное и не титан. */
    @Unique
    private static boolean maxon$isPullable(Entity entity, LocalPlayer player) {
        if (entity == null || entity.getId() == player.getId()) return false;

        String name = entity.getClass().getSimpleName().toLowerCase();
        if (name.contains("titan") || name.contains("dragon") || name.contains("wither") || name.contains("boss")) {
            return false;
        }
        if (entity.getType().getDescriptionId().contains("connie_father")) return false;

        // Крупную тушу игрок не перетянет — скорее подтянется сам.
        return entity.getBbWidth() <= 2.0f && entity.getBbHeight() <= 3.5f;
    }

    /**
     * Подтягивание зацепленной сущности.
     * <p>
     * Пробел — активная лебёдка: гасим инерцию цели и тащим к себе. Без пробела трос работает
     * поводком: не даёт удаляться, но и не волочит.
     */
    @Unique
    private static void maxon$pullEntity(LocalPlayer player, HookPoint hook) {
        if (hook == null || !hook.active) return;
        if (!maxon$isPullable(hook.hookedEntity, player)) return;

        Entity target = hook.hookedEntity;
        Vec3 playerCenter = player.position().add(0, 1.0, 0);
        Vec3 targetCenter = target.position().add(0, target.getBbHeight() / 2.0, 0);
        double dist = playerCenter.distanceTo(targetCenter);

        // Совсем короткий трос не даём: иначе моб влетает игроку в камеру.
        if (hook.maxRopeLength < 2.0) hook.maxRopeLength = 2.0;
        if (dist <= hook.maxRopeLength) return;

        double stretch = dist - hook.maxRopeLength;
        Vec3 toPlayer = playerCenter.subtract(targetCenter).normalize();
        Vec3 currentVel = target.getDeltaMovement();
        Vec3 finalForce;

        if (Minecraft.getInstance().options.keyUp.isDown()) {
            double forceMag = Math.min(1.2, stretch * 0.2 + 0.35);
            finalForce = currentVel.scale(-0.25).add(toPlayer.scale(forceMag));
        } else {
            Vec3 outward = toPlayer.scale(-1.0);
            double outwardSpeed = currentVel.dot(outward);
            finalForce = Vec3.ZERO;
            if (outwardSpeed > 0) finalForce = finalForce.add(outward.scale(-outwardSpeed));
            finalForce = finalForce.add(toPlayer.scale(Math.min(0.15, stretch * 0.05)));
            if (finalForce.lengthSqr() <= 0.001) return;
        }

        target.setDeltaMovement(currentVel.add(finalForce));
        ClientPlayNetworking.send(new PullEntityPayload(target.getId(), finalForce));
    }

    /**
     * Лебёдка тянет, а игрок стоит — значит трос за что-то держится намертво. Через несколько
     * попыток снаряжение заклинивает и крюки срываются.
     */
    @Unique
    private static void maxon$trackJam(LocalPlayer player, HookPoint leftHook, HookPoint rightHook,
                                       double distToTarget, Vec3 currentVel) {
        if (distToTarget <= 2.5 || currentVel.length() >= 0.15) {
            maxon$stuckTicks = 0;
            maxon$jamSoundsPlayed = 0;
            return;
        }

        if (++maxon$stuckTicks < 10) return;

        maxon$stuckTicks = 0;
        maxon$jamSoundsPlayed++;
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F, 0.8F, false);

        if (maxon$jamSoundsPlayed >= 3) {
            ClientPlayNetworking.send(new RequestJamPayload());
            if (leftHook.active) leftHook.release();
            if (rightHook.active) rightHook.release();
            maxon$jamSoundsPlayed = 0;
        }
    }

    /** Искры при скольжении по земле, звук полёта и частицы форсажа — всё как в оригинале. */
    @Unique
    private static void maxon$effects(LocalPlayer player, Vec3 playerPos, Vec3 velocity,
                                      boolean isGasBoosting, double boost) {
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (player.onGround() && horizontalSpeed > 0.4) {
            for (int i = 0; i < 3; i++) {
                double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.5;
                double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.5;
                player.level().addParticle(ParticleTypes.LAVA,
                        playerPos.x + offsetX, playerPos.y, playerPos.z + offsetZ,
                        -velocity.x * 0.5, 0.1, -velocity.z * 0.5);
            }
            if (player.tickCount % 3 == 0) {
                float pitch = 0.8F + player.getRandom().nextFloat() * 0.4F;
                player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                        SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.5F, pitch, false);
            }
        }

        float odmPitch = getODMPitch(player);
        if (!player.onGround()) ODMSoundManager.playFlightSound(player, odmPitch);
        else ODMSoundManager.stopFlightSound(player);

        if (isGasBoosting && boost > 0.1) {
            ODMSoundManager.playGasBoostSound(player, odmPitch);
            spawnBoostParticles(player);
        } else {
            ODMSoundManager.stopGasBoostSound(player);
        }
    }
}
