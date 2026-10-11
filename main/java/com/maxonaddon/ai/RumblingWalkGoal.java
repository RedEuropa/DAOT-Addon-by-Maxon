package com.maxonaddon.ai;

import daot.ColossalTitanEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RumblingWalkGoal extends Goal {
    private final ColossalTitanEntity titan;
    private int stuckTicks = 0;
    private double lastX, lastZ;
    private int steamTimer = 0;
    private int scorchTimer = 0;
    private int deathTimer = 0;

    // Карта для отслеживания времени пребывания сущностей в пару (для эффекта Ханджи)
    private final Map<LivingEntity, Integer> steamExposureMap = new HashMap<>();

    public RumblingWalkGoal(ColossalTitanEntity titan) {
        this.titan = titan;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return titan.getTags().contains("rumbling_boss");
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
        this.steamTimer = 0;
        this.scorchTimer = 0;
        this.deathTimer = 0; // Сброс таймера
        this.steamExposureMap.clear();
        titan.setTarget(null);
    }

    @Override
    public void tick() {
        try {
            boolean isFalling = (boolean) titan.getClass().getMethod("isFalling").invoke(titan);
            boolean isDefeated = (boolean) titan.getClass().getMethod("isDefeated").invoke(titan);

            // 1. ЛОГИКА СМЕРТИ: Ждем анимацию падения
            if (isFalling || isDefeated || titan.isDeadOrDying() || titan.getHealth() <= 0.0F) {
                titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
                titan.setSprinting(false);
                titan.setTarget(null);
                titan.getNavigation().stop();
                deathTimer++;
                // Ждем 150 тиков (7.5 секунд), чтобы Колосс эпично упал, и только потом удаляем
                if (deathTimer > 150) {
                    titan.discard();
                }
                return; // Блокируем весь остальной ИИ
            }

            // 2. ЛОГИКА АФК (выбит из сил): Замораживаем всё, чтобы шея пробивалась
            boolean isIncapacitated = (boolean) titan.getClass().getMethod("isIncapacitated").invoke(titan);
            if (isIncapacitated) {
                titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
                titan.setSprinting(false);
                titan.getNavigation().stop();
                // ВАЖНО: Сбрасываем цель! Иначе он крутит головой за игроком,
                // и невидимый хитбокс шеи отрывается от визуальной модели.
                titan.setTarget(null);

                try {
                    // Принудительно снимаем любую защиту
                    titan.getClass().getMethod("setProtecting", boolean.class).invoke(titan, false);
                } catch (Exception ignored) {}

                deathTimer = 0; // На всякий случай сбрасываем таймер смерти
                return; // Блокируем шаги, пар и разрушение блоков
            }
        } catch (Exception ignored) {}

        // ... дальше идет твой стандартный код сброса таргета и движения ...
        if (titan.getTarget() != null) {
            titan.setTarget(null);
        }

        double targetX = 0.0D;
        double targetZ = 0.0D;

        Vec3 currentPos = titan.position();
        double dx = targetX - currentPos.x;
        double dz = targetZ - currentPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDist < 10.0D) {
            titan.setDeltaMovement(0.0, titan.getDeltaMovement().y, 0.0);
            titan.setSprinting(false);
            generateCustomSteam();
            scorchEarth();
            if (titan.onGround()) dealStompDamage();
            return;
        }

        Vec3 direction = new Vec3(dx, 0.0, dz).normalize();

        double movedDistSq = (titan.getX() - lastX) * (titan.getX() - lastX) + (titan.getZ() - lastZ) * (titan.getZ() - lastZ);
        if (movedDistSq < 0.01D) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        this.lastX = titan.getX();
        this.lastZ = titan.getZ();

        // Постоянно сносим всё на ходу, КРОМЕ исключений
        plowThroughObstacles();

        // Если застрял (уперся в гору из камня/земли/руд) на 3 секунды (60 тиков) - сносим ВСЁ
        if (stuckTicks > 60) {
            breakObstacles();

            if (titan.isInWater() || titan.isInLava()) {
                titan.setDeltaMovement(titan.getDeltaMovement().add(0, 0.8, 0));
                titan.hasImpulse = true;
            }

            stuckTicks = 0;
        }

        titan.setSprinting(false);
        double speed = 0.25D;
        titan.setDeltaMovement(new Vec3(direction.x * speed, titan.getDeltaMovement().y, direction.z * speed));

        float targetYaw = (float)(Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
        titan.setYRot(targetYaw);
        titan.yBodyRot = targetYaw;
        titan.yHeadRot = targetYaw;
        titan.setXRot(0.0F);

        if (titan.onGround()) {
            dealStompDamage();
        }

        steamTimer++;
        if (steamTimer >= 10) {
            steamTimer = 0;
            generateCustomSteam();
            leaveAshTrail(direction);
        }

        scorchTimer++;
        if (scorchTimer >= 5) {
            scorchTimer = 0;
            scorchEarth();
        }
    }

    private void dealStompDamage() {
        if (!(titan.level() instanceof ServerLevel level)) return;

        AABB feetBox = titan.getBoundingBox()
                .setMinY(titan.getY() - 1.0D)
                .setMaxY(titan.getY() + 4.0D)
                .inflate(2.0D, 0.0D, 2.0D);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, feetBox)) {
            if (entity == titan) continue;
            entity.hurt(titan.damageSources().mobAttack(titan), 30.0F);
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.5, 0.0, 0.5).subtract(0, 0.8, 0));
            entity.hasImpulse = true;
        }
    }

    // Безостановочное стирание блоков (с фильтром)
    private void plowThroughObstacles() {
        if (!(titan.level() instanceof ServerLevel level)) return;

        Vec3 forward = titan.getLookAngle().multiply(1, 0, 1).normalize().scale(3.0);
        AABB bb = titan.getBoundingBox().move(forward).inflate(3.0, 1.0, 3.0);

        BlockPos.betweenClosedStream(bb).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || state.is(BlockTags.WITHER_IMMUNE)) return;

            boolean isOre = state.getBlock().getDescriptionId().contains("ore") || state.getTags().anyMatch(tag -> tag.location().getPath().contains("ore"));
            boolean isException = state.is(BlockTags.DIRT) ||
                    state.is(Blocks.STONE) ||
                    state.is(BlockTags.SAND) ||
                    state.is(Blocks.DEEPSLATE) ||
                    isOre;

            if (!isException) {
                level.destroyBlock(pos, false);
            }
        });
    }

    // Удаление вообще всех блоков, если титан наглухо застрял
    private void breakObstacles() {
        if (!(titan.level() instanceof ServerLevel level)) return;

        Vec3 forward = titan.getLookAngle().multiply(1, 0, 1).normalize().scale(3.0);
        AABB bb = titan.getBoundingBox().move(forward).inflate(3.0, 1.0, 3.0);

        BlockPos.betweenClosedStream(bb).forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0 && !state.is(BlockTags.WITHER_IMMUNE)) {
                level.destroyBlock(pos, false);
            }
        });
    }

    private void leaveAshTrail(Vec3 forwardDir) {
        if (!(titan.level() instanceof ServerLevel level)) return;

        Vec3 backward = forwardDir.scale(-1.0).normalize();
        Vec3 trailCenter = titan.position().add(backward.scale(20.0));

        // Слепота только для нижней части (в районе 4 блоков от земли)
        AABB blindnessBox = new AABB(trailCenter, trailCenter)
                .setMinY(titan.getY() - 1.0D)
                .setMaxY(titan.getY() + 4.0D)
                .inflate(20.0, 0.0, 20.0);

        for (Player player : level.getEntitiesOfClass(Player.class, blindnessBox)) {
            if (!player.isCreative() && !player.isSpectator()) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, false, true));
            }
        }
    }

    private void generateCustomSteam() {
        if (!(titan.level() instanceof ServerLevel level)) return;

        AABB steamBox = titan.getBoundingBox().inflate(8.0D);

        // Спавним всего 4 огромных партикла вместо 35 ванильных!
        // Это полностью уберет нагрузку на рендер клиента.
        level.sendParticles(com.maxonaddon.MaxonDaotAddon.TITAN_STEAM,
                titan.getX(), titan.getY() + 1.0, titan.getZ(),
                4, 6.0, 1.0, 6.0, 0.02);

        List<LivingEntity> entitiesInSteam = level.getEntitiesOfClass(LivingEntity.class, steamBox);

        for (LivingEntity entity : entitiesInSteam) {
            if (entity == titan) continue;

            int exposure = steamExposureMap.getOrDefault(entity, 0) + 1;
            steamExposureMap.put(entity, exposure);

            if (exposure >= 60) {
                entity.hurt(titan.damageSources().onFire(), 4.0F);
                entity.setRemainingFireTicks(60);
            }

            Vec3 pushDir = entity.position().subtract(titan.position()).normalize().scale(0.3).add(0, 0.1, 0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pushDir));
            entity.hasImpulse = true;
        }

        steamExposureMap.keySet().removeIf(e -> !entitiesInSteam.contains(e) || e.isDeadOrDying());
    }
    private void scorchEarth() {
        if (!(titan.level() instanceof ServerLevel level)) return;

        BlockPos center = titan.blockPosition();
        int radius = 5;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 4; dy++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(pos);

                        if (state.isAir()) continue;
                        Block block = state.getBlock();

                        boolean isWaterOrIce = (!state.getFluidState().isEmpty() && state.getFluidState().is(FluidTags.WATER)) ||
                                block == Blocks.ICE || block == Blocks.FROSTED_ICE ||
                                block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE ||
                                block == Blocks.SNOW || block == Blocks.SNOW_BLOCK;

                        if (isWaterOrIce) {
                            if (dy < 0) {
                                level.setBlockAndUpdate(pos, Blocks.BASALT.defaultBlockState());
                            } else {
                                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0.05);
                            }
                            continue;
                        }

                        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.PLANKS)) {
                            level.destroyBlock(pos, false);
                            level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
                            continue;
                        }

                        // Заменяем все виды земли на Базальт, Чернит или Уплотненную грязь
                        if (dy <= 0 && state.isSolidRender(level, pos) && level.getBlockState(pos.above()).isAir()) {
                            if (state.is(BlockTags.DIRT)) {
                                Block newBlock;
                                int rand = titan.getRandom().nextInt(3);
                                if (rand == 0) newBlock = Blocks.BASALT;
                                else if (rand == 1) newBlock = Blocks.BLACKSTONE;
                                else newBlock = Blocks.PACKED_MUD;

                                level.setBlockAndUpdate(pos, newBlock.defaultBlockState());

                                if (titan.getRandom().nextInt(12) == 0) {
                                    level.setBlockAndUpdate(pos.above(), Blocks.FIRE.defaultBlockState());
                                }
                            }
                            // Песок и гравий просто поджигаем с шансом
                            else if (block == Blocks.STONE || block == Blocks.SAND || block == Blocks.GRAVEL) {
                                if (titan.getRandom().nextInt(100) < 40) {
                                    if (titan.getRandom().nextInt(12) == 0) {
                                        level.setBlockAndUpdate(pos.above(), Blocks.FIRE.defaultBlockState());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}