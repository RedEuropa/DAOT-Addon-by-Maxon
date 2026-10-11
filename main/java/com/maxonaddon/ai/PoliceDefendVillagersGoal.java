package com.maxonaddon.ai;

import com.maxonaddon.util.PoliceAlertTracker;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Полицейский ("police_hunter") заступается за мирных жителей: если игрок ударил
 * жителя рядом с ним (см. PoliceDefendVillagersHandler), полицейский бросается
 * на этого игрока с клинком. В отличие от поимки контрабандиста — бой здесь
 * НАСТОЯЩИЙ (полный урон), это защита от реальной угрозы, а не арест.
 *
 * Работает круглосуточно и независимо от ночного дежурства — угроза жителю не ждёт расписания.
 * Приоритет выше, чем у охоты на контрабандистов: спасти человека важнее рутинной поимки.
 */
public class PoliceDefendVillagersGoal extends Goal {

    private static final double LEASH_RANGE = 32.0;
    private static final double ATTACK_RANGE = 2.2;
    private static final float STRIKE_DAMAGE = 6.0F;

    private final Villager officer;
    private Player target;
    private int attackCooldown;

    public PoliceDefendVillagersGoal(Villager officer) {
        this.officer = officer;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!officer.getTags().contains("police_hunter")) return false;

        // Полицейский с винтовкой отвечает огнём (PoliceRifleAttackGoal), а не кулаками:
        // иначе он бросался избивать игрока вплотную вместо того, чтобы стрелять.
        // Тревогу (PoliceAlertTracker) стрелок разбирает сам — он проверяется раньше.
        if (hasRifle()) return false;

        if (target != null && isValidOngoingTarget(target)) return true;
        target = null;

        if (!(officer.level() instanceof ServerLevel level)) return false;

        UUID attackerId = PoliceAlertTracker.consume(officer, level.getGameTime());
        if (attackerId == null) return false;

        Player player = level.getPlayerByUUID(attackerId);
        if (player == null || !isValidOngoingTarget(player)) return false;

        target = player;
        return true;
    }

    /** Винтовка может лежать в любой руке: вне боя в левой, в бою в правой. */
    private boolean hasRifle() {
        return officer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                        .is(com.maxonaddon.MaxonDaotAddon.MARLEYAN_RIFLE_ITEM)
                || officer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND)
                        .is(com.maxonaddon.MaxonDaotAddon.MARLEYAN_RIFLE_ITEM);
    }

    private boolean isValidOngoingTarget(Player candidate) {
        return candidate.isAlive()
                && !candidate.isSpectator()
                && !candidate.isCreative()
                && officer.distanceTo(candidate) < LEASH_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && isValidOngoingTarget(target);
    }

    @Override
    public void start() {
        attackCooldown = 0;
    }

    @Override
    public void stop() {
        target = null;
        officer.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) return;

        officer.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (attackCooldown > 0) attackCooldown--;

        double dist = officer.distanceTo(target);
        if (dist > ATTACK_RANGE) {
            // Скорость уже задана атрибутом MOVEMENT_SPEED (см. PoliceGearHelper) — здесь без множителя.
            officer.getNavigation().moveTo(target, 1.0);
        } else {
            officer.getNavigation().stop();
            if (attackCooldown <= 0) {
                strike();
                attackCooldown = 20;
            }
        }
    }

    private void strike() {
        officer.swing(InteractionHand.MAIN_HAND);
        officer.level().playSound(null, officer.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0F, 1.0F);
        target.hurt(officer.damageSources().mobAttack(officer), STRIKE_DAMAGE);

        if (officer.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(),
                    10, 0.4, 0.4, 0.4, 0.15);
        }
    }
}
