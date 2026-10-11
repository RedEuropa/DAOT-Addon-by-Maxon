package com.maxonaddon.ai;

import com.maxonaddon.network.VillagerHookSyncPayload;
import daot.FlareCartridgeItem;
import daot.FlareProjectileEntity;
import daot.network.ODMHookSyncPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ScoutOdmCombatGoal extends Goal {
    private final Villager scout;
    private Entity targetNape;
    private Mob mainTitan;
    private int retreatTicks = 0;
    private int attackCooldown = 0;
    private int searchCooldown = 0;

    private boolean wasHooked = false;
    private boolean wasInCombat = false;

    public ScoutOdmCombatGoal(Villager scout) {
        this.scout = scout;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean isFighter(Villager v) {
        return v.getTags().contains("scout_legion") || v.getTags().contains("brave_garrison");
    }

    @Override
    public boolean canUse() {
        if (!isFighter(scout)) return false;

        // === НОЧНОЕ ДЕЖУРСТВО ===
        // Ночью не все гарнизонцы/скауты патрулируют — часть "спит" (см. NightDutyHelper).
        // Днём это условие не действует, поведение как раньше.
        if (com.maxonaddon.util.NightDutyHelper.isNight(scout.level())
                && !com.maxonaddon.util.NightDutyHelper.isActiveNow(scout)) {
            return false;
        }

        // === БЛОКИРОВКА В ГОРОДЕ ТОЛЬКО ДЛЯ РАЗВЕДКИ ===
        // Если это разведчик (scout_legion) И он в радиусе 200 блоков от (0,0) — отключаем УПМ.
        // Гарнизон (brave_garrison) это условие проигнорирует и продолжит патрулировать город!
        if (scout.getTags().contains("scout_legion") && scout.distanceToSqr(0, scout.getY(), 0) <= 40000) {
            return false;
        }

        if (searchCooldown <= 0) {
            findTarget();
            searchCooldown = 10;
        } else {
            searchCooldown--;
        }

        return targetNape != null && targetNape.isAlive() && mainTitan != null && mainTitan.isAlive();
    }

    @Override
    public boolean canContinueToUse() {

        if (retreatTicks > 0) return true;
        return targetNape != null && targetNape.isAlive() && mainTitan != null && mainTitan.isAlive();
    }

    @Override
    public void start() {
        this.wasInCombat = true;
        this.attackCooldown = 0;
        this.retreatTicks = 0;
    }

    @Override
    public void stop() {
        if (wasHooked) {
            sendHookSync(false, null);
            wasHooked = false;
        }

        scout.removeTag("active_bait");

        if (this.wasInCombat) {
            this.wasInCombat = false;
            if (isAreaClear()) {
                fireVictoryFlare();
            }
        }

        this.targetNape = null;
        this.mainTitan = null;
    }

    private void findTarget() {
        List<Entity> entities = scout.level().getEntities(scout, scout.getBoundingBox().inflate(80.0));
        List<Mob> titans = new ArrayList<>();
        List<Entity> weakpoints = new ArrayList<>();
        List<Entity> eyes = new ArrayList<>();
        List<Villager> allies = new ArrayList<>();

        for (Entity e : entities) {
            if (!e.isAlive()) continue;
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();

            if (id.contains("nape")) {
                weakpoints.add(e);
            } else if (id.contains("eye")) {
                eyes.add(e);
            } else if (e instanceof Mob mob && (id.contains("titan") || id.equals("connie_father") || id.contains("connie"))) {
                titans.add(mob);
            } else if (e instanceof Villager v && isFighter(v)) {
                allies.add(v);
            }
        }

        this.mainTitan = null;
        this.targetNape = null;

        if (titans.isEmpty()) return;

        boolean isScout = scout.getTags().contains("scout_legion");
        boolean isBraveGarrison = scout.getTags().contains("brave_garrison");

        if (isScout && !isBraveGarrison) {
            for (Villager ally : allies) {
                if (ally.getTags().contains("active_bait")) {
                    for (Mob t : titans) {
                        if (t.getTarget() != null && t.getTarget().getId() == ally.getId()) {
                            this.mainTitan = t;
                            break;
                        }
                    }
                    if (this.mainTitan != null) break;
                }
            }
            if (this.mainTitan == null) {
                for (Mob t : titans) {
                    if (t.getTarget() instanceof Villager v && isFighter(v)) {
                        this.mainTitan = t;
                        if (v.getTags().contains("scout_legion")) {
                            v.addTag("active_bait");
                        }
                        break;
                    }
                }
            }
        }

        if (this.mainTitan == null) {
            double closestDist = Double.MAX_VALUE;
            for (Mob t : titans) {
                double dist = scout.distanceToSqr(t);
                if (dist < closestDist) {
                    closestDist = dist;
                    this.mainTitan = t;
                }
            }
        }

        if (this.mainTitan != null && scout.isPassenger()) {
            scout.stopRiding();
        }

        if (this.mainTitan != null) {
            boolean isTitanStunned = false;
            for (Entity eye : eyes) {
                if (eye.distanceToSqr(this.mainTitan) < 400.0 && !eye.isAlive()) {
                    isTitanStunned = true;
                    break;
                }
            }

            if (isBraveGarrison || isTitanStunned) {
                double closestDist = Double.MAX_VALUE;
                for (Entity wp : weakpoints) {
                    double dist = wp.distanceToSqr(this.mainTitan);
                    if (dist < 400.0) {
                        closestDist = dist;
                        this.targetNape = wp;
                    }
                }
            }

            if (this.targetNape == null && !weakpoints.isEmpty()) {
                this.targetNape = weakpoints.get(0);
            }

            if (this.targetNape == null) {
                this.mainTitan = null;
            }
        }
    }

    private boolean isAreaClear() {
        List<Entity> entities = scout.level().getEntities(scout, scout.getBoundingBox().inflate(80.0));
        for (Entity e : entities) {
            if (e.isAlive()) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
                if (id.contains("titan") || id.contains("nape") || id.contains("eye")) {
                    return false;
                }
            }
        }
        return true;
    }

    private void fireVictoryFlare() {
        if (!scout.getTags().contains("scout_legion")) return;
        if (scout.level() instanceof ServerLevel level) {
            List<Entity> flares = level.getEntities(scout, scout.getBoundingBox().inflate(80.0),
                    e -> e.getClass().getSimpleName().contains("FlareProjectileEntity"));

            if (!flares.isEmpty()) return;

            EntityType<?> flareType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "flare_projectile"));
            if (flareType != null && flareType != EntityType.PIG) {
                FlareProjectileEntity flare = new FlareProjectileEntity((EntityType<? extends net.minecraft.world.entity.projectile.AbstractArrow>) flareType, level);
                flare.setPos(scout.getX(), scout.getY() + 2.0, scout.getZ());
                flare.setFlareColorOrdinal(FlareCartridgeItem.FlareColor.GREEN.ordinal());
                Vec3 shootVec = new Vec3(0, 1.0, 0);
                flare.shoot(shootVec.x, shootVec.y, shootVec.z, 2.5F, 1.0F);
                level.addFreshEntity(flare);
            }
        }
    }

    @Override
    public void tick() {
        if (mainTitan != null) {
            scout.getLookControl().setLookAt(mainTitan, 30.0F, 30.0F);
        }

        scout.fallDistance = 0.0f;
        boolean isScout = scout.getTags().contains("scout_legion");
        boolean isBraveGarrison = scout.getTags().contains("brave_garrison");
        boolean shouldBeBait = isScout && !isBraveGarrison && mainTitan != null && mainTitan.getTarget() != null && mainTitan.getTarget().getId() == scout.getId();

        if (shouldBeBait) {
            scout.addTag("active_bait");
            double distToTitan = scout.distanceTo(mainTitan);
            if (distToTitan < 25.0) {
                Vec3 awayVec = scout.position().subtract(mainTitan.position()).normalize();
                // Небольшой вертикальный уклон вместо жёсткого +0.4 — раньше это разгоняло
                // жителей в стратосферу за пару секунд кайтинга.
                Vec3 evadeDir = awayVec.add(0, 0.15, 0).normalize();
                applyOdmFlight(evadeDir, 1.2, 0.02);

                Vec3 hookTarget = scout.position().add(evadeDir.scale(15.0));
                sendHookSync(true, hookTarget);
                wasHooked = true;
            } else {
                scout.setDeltaMovement(scout.getDeltaMovement().multiply(0.8, 0.8, 0.8));
                if (wasHooked) {
                    sendHookSync(false, null);
                    wasHooked = false;
                }
            }
        } else {
            scout.removeTag("active_bait");
            if (attackCooldown > 0) attackCooldown--;

            if (retreatTicks > 0) {
                retreatTicks--;
                double retreatSpeed = isScout ? 0.8 : 0.4;
                Vec3 awayDir = scout.position().subtract(targetNape.position()).normalize();
                applyOdmFlight(awayDir, retreatSpeed, 0.06);

                if (wasHooked) {
                    sendHookSync(false, null);
                    wasHooked = false;
                }
                return;
            }

            double distToNape = scout.distanceTo(targetNape);

            if (distToNape > 5.5) {
                double flySpeed = isScout ? 1.5 : 0.8;
                Vec3 flyDir = targetNape.position().add(0, targetNape.getBbHeight() / 2.0, 0)
                        .subtract(scout.position()).normalize();
                applyOdmFlight(flyDir, flySpeed, 0.08);

                Vec3 hookTarget = targetNape.position().add(0, targetNape.getBbHeight() / 2.0, 0);
                sendHookSync(true, hookTarget);
                wasHooked = true;
            } else {
                if (mainTitan != null) scout.getLookControl().setLookAt(mainTitan, 30.0F, 30.0F);
                if (wasHooked) {
                    sendHookSync(false, null);
                    wasHooked = false;
                }
                if (attackCooldown <= 0) {
                    scout.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

                    boolean hurtSuccess = targetNape.hurt(scout.damageSources().mobAttack(scout), 100.0f);
                    if (!hurtSuccess && mainTitan != null && mainTitan.isAlive()) {
                        mainTitan.hurt(scout.damageSources().mobAttack(scout), 100.0f);
                    }

                    scout.level().playSound(null, scout.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0f, 1.0f);

                    // === ДОБАВЛЯЕМ ВРЕМЕННЫЕ КРИТЫ (ПАРТИКЛЫ) ===
                    if (scout.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(
                                net.minecraft.core.particles.ParticleTypes.CRIT,
                                targetNape.getX(), targetNape.getY() + (targetNape.getBbHeight() / 2.0), targetNape.getZ(),
                                25, 0.5, 0.5, 0.5, 0.2
                        );
                    }

                    retreatTicks = 20;
                    attackCooldown = 40;
                }
            }
        }
    }

    /**
     * Задаёт скорость полёта в направлении {@code dir} (не обязательно единичном — нормализуется
     * внутри), плавно подмешивая её к текущей скорости вместо жёсткой подмены — это убирает
     * дёрганый "телепорт-рывок" каждый тик и даёт инерцию, похожую на настоящий полёт на тросах.
     * Дополнительно ограничивает вертикальную составляющую и не даёт жителю улетать выше titan'а
     * больше чем на разумную высоту — раньше именно отсутствие этих ограничений уносило жителей
     * в небо при длительном кайтинге/полёте.
     *
     * @param verticalBias небольшая добавка вверх, компенсирующая гравитацию в горизонтальном полёте
     */
    private void applyOdmFlight(Vec3 dir, double speed, double verticalBias) {
        if (dir.lengthSqr() < 1.0E-6) return;
        Vec3 n = dir.normalize();

        double targetY = Math.max(-1.4, Math.min(1.15, n.y * speed + verticalBias));
        Vec3 target = new Vec3(n.x * speed, targetY, n.z * speed);

        Vec3 current = scout.getDeltaMovement();
        // Плавное подмешивание (30% к цели за тик) вместо мгновенной подмены скорости.
        double blendFactor = 0.3;
        Vec3 blended = new Vec3(
                current.x + (target.x - current.x) * blendFactor,
                current.y + (target.y - current.y) * blendFactor,
                current.z + (target.z - current.z) * blendFactor
        );

        // Жёсткий потолок высоты относительно титана, за которым гонится/которого атакует житель.
        if (mainTitan != null) {
            double ceiling = mainTitan.getY() + mainTitan.getBbHeight() + 14.0;
            if (scout.getY() > ceiling && blended.y > -0.1) {
                blended = new Vec3(blended.x, -0.1, blended.z);
            }
        }

        scout.setDeltaMovement(blended);

        if (blended.horizontalDistanceSqr() > 1.0E-5) {
            float targetYaw = (float) (Math.atan2(blended.z, blended.x) * (180.0 / Math.PI)) - 90.0F;
            scout.setYRot(targetYaw);
            scout.yBodyRot = targetYaw;
        }
    }

    private void sendHookSync(boolean active, Vec3 hookPos) {
        if (!(scout.level() instanceof ServerLevel)) return;

        double x = hookPos != null ? hookPos.x : 0;
        double y = hookPos != null ? hookPos.y : 0;
        double z = hookPos != null ? hookPos.z : 0;
        VillagerHookSyncPayload payload = new VillagerHookSyncPayload(scout.getId(), active, x, y, z);

        for (ServerPlayer player : PlayerLookup.tracking(scout)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
