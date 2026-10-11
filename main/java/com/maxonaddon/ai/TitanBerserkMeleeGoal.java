package com.maxonaddon.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;

public class TitanBerserkMeleeGoal extends Goal {
    private final Monster titan;
    private int attackTimer = 0;
    private int stuckTicks = 0;
    private double lastX, lastZ;
    private double lastTargetDist = -1.0D;
    private int grabCooldown = 400; // 20 секунд
    private int beastRoarCount = 0;
    private boolean femaleDesperationUsed = false;
    private int femaleDesperationTimer = 0;

    private int wanderTimer = 0;
    private double wanderX, wanderZ;
    private int deathTimer = 0;

    public TitanBerserkMeleeGoal(Monster titan) {
        this.titan = titan;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!titan.getTags().contains("berserk_boss")) return false;
        if (titan.getHealth() <= 0.0F || titan.isDeadOrDying()) return false;

        // ВЫКЛЮЧАЕМ ИИ, ЕСЛИ ТИТАН В ОТКЛЮЧКЕ (АФК СТАДИЯ)
        if (titan instanceof daot.ShifterTitan shifter && shifter.isIncapacitated()) return true;

        LivingEntity currentTarget = titan.getTarget();

        // Получаем имя текущего титана без слова "Entity" (например, "AttackTitan")
        String myBaseName = titan.getClass().getSimpleName().replace("Entity", "");

        if (currentTarget != null && currentTarget.isAlive()) {
            if (currentTarget instanceof Player p) {
                String ownerTag = "berserk_owner_" + p.getUUID().toString();
                if (titan.hasPassenger(p) || titan.getTags().contains(ownerTag) || p.isCreative() || p.isSpectator()) {
                    titan.setTarget(null);
                } else {
                    return true;
                }
            } else {
                String targetName = currentTarget.getClass().getSimpleName();
                // ФИКС: Если цель того же вида (или это его часть тела), либо это просто другая часть тела - игнор
                if (targetName.contains(myBaseName) ||
                        targetName.contains("Nape") || targetName.contains("Eye") || targetName.contains("Grab") ||
                        targetName.contains("Hand") || targetName.contains("Leg") || targetName.contains("Dummy") ||
                        titan.hasPassenger(currentTarget) || currentTarget.getVehicle() == titan) {
                    titan.setTarget(null);
                } else {
                    return true;
                }
            }
        }

        AABB searchBox = titan.getBoundingBox().inflate(64.0D);
        List<LivingEntity> potentialTargets = titan.level().getEntitiesOfClass(LivingEntity.class, searchBox, e -> {
            if (e == titan || !e.isAlive() || titan.hasPassenger(e) || e.getVehicle() == titan) return false;

            String name = e.getClass().getSimpleName();

            // ФИКС: Игнорируем титанов того же вида и их части тела
            if (name.contains(myBaseName)) return false;

            if (name.contains("Nape") || name.contains("Eye") || name.contains("Grab") ||
                    name.contains("Hand") || name.contains("Leg") || name.contains("Dummy")) return false;

            if (e instanceof Player p) {
                String ownerTag = "berserk_owner_" + p.getUUID().toString();
                return !p.isCreative() && !p.isSpectator() && !titan.hasPassenger(p) && !titan.getTags().contains(ownerTag);
            }

            // ФИКС: Бьем всех остальных титанов и Батю Конни
            return name.contains("Titan") || name.contains("ConnieFather");
        });

        if (!potentialTargets.isEmpty()) {
            LivingEntity closest = potentialTargets.stream()
                    .min((e1, e2) -> Double.compare(titan.distanceToSqr(e1), titan.distanceToSqr(e2)))
                    .orElse(null);
            if (closest != null) {
                titan.setTarget(closest);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.lastX = titan.getX();
        this.lastZ = titan.getZ();
        this.stuckTicks = 0;
        this.attackTimer = 0;
        this.lastTargetDist = -1.0D;
        this.wanderTimer = 0;
        this.deathTimer = 0;
    }

    @Override
    public void stop() {
        titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
        titan.setSprinting(false);
        titan.setTarget(null);

        try {
            titan.getClass().getMethod("setProtecting", boolean.class).invoke(titan, false);
        } catch (Exception ignored) {}
    }

    @Override
    public void tick() {
        if (this.grabCooldown > 0) {
            this.grabCooldown--;
        }
        try {
            boolean isFalling = false;
            boolean isDefeated = false;
            try { isFalling = (boolean) titan.getClass().getMethod("isFalling").invoke(titan); } catch (Exception ignored) {}
            try { isDefeated = (boolean) titan.getClass().getMethod("isDefeated").invoke(titan); } catch (Exception ignored) {}

            if (isFalling || isDefeated || titan.isDeadOrDying() || titan.getHealth() <= 0.0F) {
                titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
                titan.setSprinting(false);
                titan.setTarget(null);
                titan.getNavigation().stop();
                titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
                titan.setSprinting(false);
                deathTimer++;

                if (deathTimer > 150) {
                    titan.discard();
                }
                return;
            }
        } catch (Exception ignored) {}

        if (titan instanceof daot.ShifterTitan shifter && shifter.isIncapacitated()) {
            titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
            titan.setSprinting(false);
            titan.setTarget(null);
            titan.getNavigation().stop();

            titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
            titan.setSprinting(false);
            try {
                titan.getClass().getMethod("setProtecting", boolean.class).invoke(titan, false);
            } catch (Exception ignored) {}

            deathTimer = 0;
            return;
        }

        try {
            Method isBlindedMethod = titan.getClass().getMethod("isBlinded");
            if ((boolean) isBlindedMethod.invoke(titan)) {
                titan.setSprinting(false);
                titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
                return;
            }
        } catch (Exception ignored) {}

        LivingEntity target = titan.getTarget();
        String myBaseName = titan.getClass().getSimpleName().replace("Entity", "");

        if (target != null) {
            if (!target.isAlive()) {
                titan.setTarget(null);
                target = null;
            } else if (target instanceof Player p) {
                String ownerTag = "berserk_owner_" + p.getUUID().toString();
                if (titan.hasPassenger(p) || titan.getTags().contains(ownerTag) || p.isCreative() || p.isSpectator()) {
                    titan.setTarget(null);
                    target = null;
                }
            } else {
                String targetName = target.getClass().getSimpleName();
                if (targetName.contains(myBaseName) ||
                        targetName.contains("Nape") || targetName.contains("Eye") || targetName.contains("Grab") ||
                        targetName.contains("Hand") || targetName.contains("Leg") || targetName.contains("Dummy") ||
                        titan.hasPassenger(target) || target.getVehicle() == titan) {
                    titan.setTarget(null);
                    target = null;
                }
            }
        }

        if (target == null) {
            titan.setSprinting(false);

            AABB searchBox = titan.getBoundingBox().inflate(64.0D);
            List<LivingEntity> potentialTargets = titan.level().getEntitiesOfClass(LivingEntity.class, searchBox, e -> {
                if (e == titan || !e.isAlive() || titan.hasPassenger(e) || e.getVehicle() == titan) return false;

                String name = e.getClass().getSimpleName();
                if (name.contains(myBaseName)) return false;

                if (name.contains("Nape") || name.contains("Eye") || name.contains("Grab") ||
                        name.contains("Hand") || name.contains("Leg") || name.contains("Dummy")) return false;

                if (e instanceof Player p) {
                    String ownerTag = "berserk_owner_" + p.getUUID().toString();
                    return !p.isCreative() && !p.isSpectator() && !titan.hasPassenger(p) && !titan.getTags().contains(ownerTag);
                }
                return name.contains("Titan") || name.contains("ConnieFather");
            });

            if (!potentialTargets.isEmpty()) {
                LivingEntity closest = potentialTargets.stream()
                        .min((e1, e2) -> Double.compare(titan.distanceToSqr(e1), titan.distanceToSqr(e2)))
                        .orElse(null);
                if (closest != null) {
                    titan.setTarget(closest);
                    return;
                }
            }

            if (wanderTimer <= 0) {
                Player globalPlayer = titan.level().getNearestPlayer(titan, 300.0D);
                if (globalPlayer != null && !globalPlayer.isCreative() && !globalPlayer.isSpectator()) {
                    wanderX = globalPlayer.getX();
                    wanderZ = globalPlayer.getZ();
                } else {
                    wanderX = titan.getX() + (titan.getRandom().nextDouble() - 0.5D) * 100.0D;
                    wanderZ = titan.getZ() + (titan.getRandom().nextDouble() - 0.5D) * 100.0D;
                }
                wanderTimer = 100 + titan.getRandom().nextInt(100);
            }
            wanderTimer--;

            double dx = wanderX - titan.getX();
            double dz = wanderZ - titan.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist > 3.0D) {
                Vec3 dir = new Vec3(dx, 0.0, dz).normalize();
                double speed = 0.25D;
                titan.setDeltaMovement(new Vec3(dir.x * speed, titan.getDeltaMovement().y, dir.z * speed));

                float targetYaw = (float)(Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
                titan.setYRot(targetYaw);
                titan.yBodyRot = targetYaw;
                titan.yHeadRot = targetYaw;
                titan.setXRot(0.0F);

                double movedDistSq = (titan.getX() - lastX) * (titan.getX() - lastX) + (titan.getZ() - lastZ) * (titan.getZ() - lastZ);
                if (movedDistSq < 0.01D) {
                    stuckTicks++;
                    if (stuckTicks > 20 && titan.onGround()) {
                        titan.setDeltaMovement(titan.getDeltaMovement().add(0, 1.2, 0));
                        titan.hasImpulse = true;
                        stuckTicks = 0;
                    }
                } else {
                    stuckTicks = 0;
                }
                lastX = titan.getX();
                lastZ = titan.getZ();
            } else {
                wanderTimer = 0;
            }

            return;
        }

        String titanName = titan.getClass().getSimpleName();
        boolean isFemale = titanName.contains("FemaleTitan");
        boolean isAttack = titanName.contains("AttackTitan");
        boolean isBeast = titanName.contains("BeastTitan");
        boolean isColossal = titanName.contains("ColossalTitan");
        boolean isWarhammer = titanName.contains("WarhammerTitan");
        boolean isJaw = titanName.contains("JawTitan") || titanName.contains("TestShifter");

        if (isFemale && !femaleDesperationUsed && titan.getHealth() <= titan.getMaxHealth() * 0.05f) {
            femaleDesperationUsed = true;
            femaleDesperationTimer = 600;

            try {
                titan.getClass().getMethod("triggerRoar").invoke(titan);
            } catch (Exception ignored) {}

            spawnSupportTitans(3, 2);
        }

        if (femaleDesperationTimer > 0) {
            femaleDesperationTimer--;

            titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
            titan.setSprinting(false);
            titan.getLookControl().setLookAt(target, 40.0F, 40.0F);

            try {
                titan.getClass().getMethod("setProtecting", boolean.class).invoke(titan, true);
            } catch (Exception ignored) {}

            float healAmount = (titan.getMaxHealth() * 0.15f) / 600.0f;
            titan.heal(healAmount);

            return;
        }

        if (isBeast) {
            try {
                int rockPhase = (int) titan.getClass().getMethod("getRockThrowPhase").invoke(titan);
                if (rockPhase > 0) {
                    titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
                    titan.setSprinting(false);
                    titan.getLookControl().setLookAt(target, 40.0F, 40.0F);

                    Vec3 tp = target.position();
                    Vec3 cp = titan.position();
                    double hd = Math.sqrt(Math.pow(tp.x - cp.x, 2) + Math.pow(tp.z - cp.z, 2));

                    float targetYaw = (float)(Math.atan2(tp.z - cp.z, tp.x - cp.x) * (180D / Math.PI)) - 90.0F;
                    titan.setYRot(targetYaw);
                    titan.yBodyRot = targetYaw;
                    titan.yHeadRot = targetYaw;
                    double dY = tp.y - (cp.y + titan.getEyeHeight());
                    float targetPitch = (float)(-(Math.atan2(dY, hd) * (180D / Math.PI)));
                    titan.setXRot(targetPitch);

                    if (rockPhase == 2) {
                        titan.getClass().getMethod("triggerThrow").invoke(titan);
                        attackTimer = 50;
                    }
                    return;
                }
            } catch (Exception ignored) {}
        }

        if ((isFemale || isAttack || isWarhammer) && titan.getHealth() <= titan.getMaxHealth() * 0.5f) {
            if (isWarhammer) {
                try {
                    Method hasHammerMethod = titan.getClass().getMethod("hasHammer");
                    boolean hasHammer = (boolean) hasHammerMethod.invoke(titan);

                    if (!hasHammer) {
                        titan.getClass().getMethod("setHasHammer", boolean.class).invoke(titan, true);
                    }
                } catch (Exception ignored) {}
            } else {
                try {
                    Method getHardening = titan.getClass().getMethod("getHardeningMode");
                    int currentMode = (int) getHardening.invoke(titan);
                    if (currentMode == 0) {
                        titan.getClass().getMethod("setHardeningMode", int.class).invoke(titan, 2);
                    }
                } catch (Exception ignored) {}
            }
        }

        Vec3 targetPos = target.position();
        Vec3 currentPos = titan.position();
        double horizontalDist = Math.sqrt(Math.pow(targetPos.x - currentPos.x, 2) + Math.pow(targetPos.z - currentPos.z, 2));

        double approachSpeed = 0;
        if (this.lastTargetDist > 0) {
            approachSpeed = this.lastTargetDist - horizontalDist;
        }
        this.lastTargetDist = horizontalDist;

        if (isFemale || isAttack) {
            Vec3 look = titan.getLookAngle();
            Vec3 toTarget = targetPos.subtract(currentPos).normalize();

            boolean isBehind = look.dot(toTarget) < -0.2;
            boolean isHigh = target.getY() > titan.getY() + (titan.getBbHeight() * 0.5);

            try {
                if (isBehind && isHigh && horizontalDist < 15.0D) {
                    titan.getClass().getMethod("setProtecting", boolean.class).invoke(titan, true);
                    titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
                    titan.getLookControl().setLookAt(target, 40.0F, 40.0F);
                    return;
                } else {
                    titan.getClass().getMethod("setProtecting", boolean.class).invoke(titan, false);
                }
            } catch (Exception ignored) {}
        }

        if (isFemale && attackTimer <= 0) {
            try {
                int wireCount = (int) titan.getClass().getMethod("getWireRestraintCount").invoke(titan);
                if (wireCount > 0) {
                    if (titan.getRandom().nextBoolean()) {
                        titan.getClass().getMethod("performShifterWireYank").invoke(titan);
                        attackTimer = 30;
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }

        double attackReach = isColossal ? 9.0D : 3.5D;
        boolean inMeleeRange = titan.getBoundingBox().inflate(attackReach).intersects(target.getBoundingBox());
        Vec3 direction = new Vec3(targetPos.x - currentPos.x, 0.0, targetPos.z - currentPos.z);

        if (horizontalDist > 0.001) direction = direction.normalize();

        if (isBeast) {
            if (horizontalDist < 12.0D) {
                inMeleeRange = true;
                direction = Vec3.ZERO;
            } else if (horizontalDist >= 12.0D && horizontalDist < 25.0D) {
                direction = direction.scale(-1);
                inMeleeRange = false;
            } else if (horizontalDist >= 25.0D && horizontalDist < 50.0D) {
                inMeleeRange = true;
                direction = Vec3.ZERO;
            }
        }

        if (isJaw) {
            boolean lowHp = titan.getHealth() <= 20.0f;

            if (lowHp) {
                try {
                    java.lang.reflect.Method getHardening = titan.getClass().getMethod("getHardeningMode");
                    if ((int) getHardening.invoke(titan) == 0) {
                        titan.getClass().getMethod("setHardeningMode", int.class).invoke(titan, 2);
                    }
                } catch (Exception e) {
                    try { titan.getClass().getMethod("toggleNapeHardening").invoke(titan); } catch (Exception ignored) {}
                }

                if (horizontalDist <= 6.0D && attackTimer <= 0) {
                    try { titan.getClass().getMethod("toggleBarrage").invoke(titan); } catch (Exception ignored) {}
                    attackTimer = 100;
                }
                else if (horizontalDist > 10.0D && attackTimer <= 0) {
                    try { titan.getClass().getMethod("startPounce").invoke(titan); } catch (Exception ignored) {}
                    attackTimer = 40;
                }

                inMeleeRange = horizontalDist < 5.0D;
                direction = new Vec3(targetPos.x - currentPos.x, 0.0, targetPos.z - currentPos.z).normalize();

            } else {
                if (horizontalDist < 12.0D && attackTimer <= 0) {
                    direction = currentPos.subtract(targetPos).normalize();
                    inMeleeRange = false;
                } else if (horizontalDist >= 12.0D && horizontalDist < 25.0D && attackTimer <= 0) {
                    direction = targetPos.subtract(currentPos).normalize();
                    try { titan.getClass().getMethod("startPounce").invoke(titan); } catch (Exception ignored) {}
                    attackTimer = 60;
                    inMeleeRange = false;
                } else {
                    direction = targetPos.subtract(currentPos).normalize();
                    inMeleeRange = horizontalDist < 4.0D;
                }
            }
        }

        if (!inMeleeRange) {
            double movedDistSq = (titan.getX() - lastX) * (titan.getX() - lastX) + (titan.getZ() - lastZ) * (titan.getZ() - lastZ);

            if (horizontalDist > 5.0D && movedDistSq < 0.01D && !isBeast) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            lastX = titan.getX();
            lastZ = titan.getZ();

            if (stuckTicks > 40) {
                if (isColossal) {
                    try {
                        titan.getClass().getMethod("performKick").invoke(titan);
                        stuckTicks = 0;
                        attackTimer = 100;
                    } catch (Exception ignored) {}
                } else if (target.getY() > titan.getY() + 10.0D && horizontalDist < 15.0D) {
                    if (titan.onGround()) {
                        Vec3 jumpVel = direction.scale(1.2D).add(0.0D, 1.2D, 0.0D);
                        titan.setDeltaMovement(jumpVel);
                        titan.hasImpulse = true;
                        stuckTicks = 0;
                    }
                }
            }

            boolean shouldSprint = horizontalDist > 12.0D && !isColossal;
            titan.setSprinting(shouldSprint);
            double speed = isColossal ? 0.2D : (shouldSprint ? 0.45D : 0.3D);

            Vec3 movement = new Vec3(direction.x * speed, titan.getDeltaMovement().y, direction.z * speed);
            titan.setDeltaMovement(movement);

            float targetYaw = (float)(Math.atan2(targetPos.z - currentPos.z, targetPos.x - currentPos.x) * (180D / Math.PI)) - 90.0F;
            titan.setYRot(targetYaw);
            titan.yBodyRot = targetYaw;
            titan.yHeadRot = targetYaw;

            double dY = targetPos.y - (currentPos.y + titan.getEyeHeight());
            float targetPitch = (float)(-(Math.atan2(dY, horizontalDist) * (180D / Math.PI)));
            titan.setXRot(targetPitch);

        } else {
            titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
            titan.setSprinting(false);
            titan.getLookControl().setLookAt(target, 40.0F, 40.0F);

            if (attackTimer <= 0) {
                performSmartAttack(target, titanName, horizontalDist, approachSpeed);
                attackTimer = isColossal ? 80 : (30 + titan.getRandom().nextInt(20));
            }
        }

        if (attackTimer > 0) attackTimer--;
    }

    private void performSmartAttack(LivingEntity target, String titanName, double horizontalDist, double approachSpeed) {
        try {
            int roll = titan.getRandom().nextInt(10);

            // 1. Проверяем, имеет ли текущий титан право на захват (Grab)
            boolean isGrabbingTitan = titanName.contains("Armored") ||
                    titanName.contains("Beast") ||
                    titanName.contains("Female") ||
                    titanName.contains("Attack");

            // 2. Если это нужный шифтер, цель — игрок, дистанция подходит и кулдаун прошел, пытаемся схватить
            if (isGrabbingTitan && target instanceof Player && this.grabCooldown <= 0 && horizontalDist <= 5.0D) {
                // ВАЖНО: метода triggerGrab() в dannys-aot НЕ существует (проверено по байткоду 2.4.3) —
                // прежний вызов всегда падал с NoSuchMethodException, исключение молча глоталось,
                // и берсерки в принципе никогда никого не хватали.
                //
                // Настоящий захват делает protected-метод grabEntity(Entity) — он есть ровно
                // у четырёх хватающих шифтеров (Attack/Armored/Female/Beast). Одних
                // setGrabbedEntityId + setGrabPhase НЕ хватает: это только анимация руки,
                // а сам игрок должен стать пассажиром хитбокса-захвата (grabHitboxEntity),
                // что и делает grabEntity внутри.
                if (maxon$invokeGrabEntity(titan, target)) {
                    this.grabCooldown = 400; // Сбрасываем таймер на 20 секунд (400 тиков)
                    return; // Прерываем дальнейшие атаки, так как титан начал захват
                }
            }

            // 3. Индивидуальная логика атак для специфичных титанов
            if (titanName.contains("JawTitan") || titanName.contains("TestShifter")) {
                if (titan.getHealth() > 20.0f) {
                    if (roll < 4) {
                        titan.getClass().getMethod("chompOrRelease").invoke(titan);
                    } else if (roll < 7) {
                        titan.getClass().getMethod("startKickback").invoke(titan);
                    } else {
                        titan.getClass().getMethod("triggerAttack").invoke(titan);
                    }
                }
                return;
            }

            if (titanName.contains("ColossalTitan")) {
                if (roll < 4) titan.getClass().getMethod("triggerSteamAbility").invoke(titan);
                else if (roll < 7) titan.getClass().getMethod("triggerKickAbility").invoke(titan);
                else titan.getClass().getMethod("performSmash").invoke(titan);
                return;
            }

            if (titanName.contains("BeastTitan")) {
                if (roll < 2 && beastRoarCount < 2) {
                    titan.getClass().getMethod("triggerBeastRoarAbility").invoke(titan);
                    beastRoarCount++;
                    return;
                }
                if (horizontalDist >= 15.0D) {
                    if (approachSpeed > 0.8D) {
                        titan.getClass().getMethod("triggerFireRockGrab").invoke(titan);
                    } else if (horizontalDist < 35.0D && approachSpeed > 0.2D) {
                        titan.getClass().getMethod("triggerBigRockGrab").invoke(titan);
                    } else {
                        titan.getClass().getMethod("triggerRockGrab").invoke(titan);
                    }
                } else {
                    if (roll < 5) {
                        titan.getClass().getMethod("triggerAttack").invoke(titan);
                    } else {
                        titan.getClass().getMethod("startEat", LivingEntity.class).invoke(titan, target);
                    }
                }
                return;
            }

            // 4. Базовая логика атак для всех остальных случаев (включая Female, Attack, Armored, если захват в КД)
            if (roll < 4) {
                titan.getClass().getMethod("triggerAttack").invoke(titan);
            } else if (roll < 8) {
                try {
                    titan.getClass().getMethod("triggerAbility", int.class).invoke(titan, titan.getRandom().nextBoolean() ? 1 : 2);
                } catch (Exception e) {
                    titan.getClass().getMethod("triggerAttack").invoke(titan);
                }
            } else {
                try {
                    titan.getClass().getMethod("startEat", LivingEntity.class).invoke(titan, target);
                } catch (Exception e) {
                    titan.getClass().getMethod("triggerAttack").invoke(titan);
                }
            }
        } catch (Exception ignored) {}
    }
    private void spawnSupportTitans(int normalCount, int abnormalCount) {
        if (!(titan.level() instanceof ServerLevel level)) return;

        String[] normalIds = {"titan", "small_titan", "small_titan_2"};
        String[] abnormalIds = {"abnormal_titan", "crawling_abnormal_titan"};

        for (int i = 0; i < normalCount + abnormalCount; i++) {
            boolean isAbnormal = i >= normalCount;
            String[] pool = isAbnormal ? abnormalIds : normalIds;
            String mobId = pool[titan.getRandom().nextInt(pool.length)];

            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("dannys-aot", mobId);
            net.minecraft.world.entity.EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(loc);

            if (type != null) {
                double angle = titan.getRandom().nextDouble() * 2 * Math.PI;
                double distance = 5.0 + titan.getRandom().nextDouble() * 10.0;
                double x = titan.getX() + Math.cos(angle) * distance;
                double z = titan.getZ() + Math.sin(angle) * distance;

                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)x, (int)z);
                net.minecraft.core.BlockPos spawnPos = new net.minecraft.core.BlockPos((int)x, y + 1, (int)z);

                net.minecraft.world.entity.Entity entity = type.create(level);
                if (entity instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    mob.setTarget(titan.getTarget());
                    level.addFreshEntity(mob);
                }
            }
        }
    }

    /**
     * Вызывает родной protected-метод grabEntity(Entity) у шифтера.
     * Он делает весь захват целиком: ставит фазу, телепортирует жертву к хитбоксу
     * и САЖАЕТ её пассажиром на grabHitboxEntity — без этого титан просто держит
     * руку в воздухе, а игрок остаётся на месте.
     * Ищем метод по всей иерархии классов, т.к. он объявлен не всегда в самом конечном классе.
     */
    private boolean maxon$invokeGrabEntity(net.minecraft.world.entity.Mob titan, net.minecraft.world.entity.Entity victim) {
        for (Class<?> cls = titan.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                java.lang.reflect.Method grab = cls.getDeclaredMethod("grabEntity", net.minecraft.world.entity.Entity.class);
                grab.setAccessible(true);
                grab.invoke(titan, victim);
                return true;
            } catch (NoSuchMethodException ignored) {
                // ищем выше по иерархии
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}
