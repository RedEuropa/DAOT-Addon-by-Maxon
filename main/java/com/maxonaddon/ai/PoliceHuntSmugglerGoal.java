package com.maxonaddon.ai;

import com.maxonaddon.util.NightDutyHelper;
import com.maxonaddon.util.SmugglerTradeHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Полицейский ("police_hunter") ловит контрабандистов ("smuggler"), которые сейчас
 * выдают себя капюшоном (ночная маскировка) — не трогает обычных жителей и
 * контрабандистов, у которых сейчас день/капюшон снят.
 *
 * Если погоня уже началась, она НЕ прерывается с рассветом — полицейский помнит
 * конкретную цель и продолжает её преследовать, даже если контрабандист снял плащ
 * прямо у него на глазах (см. canContinueToUse — там уже не проверяется капюшон).
 *
 * Поимка ненасмертельна: клинок бьёт, но здоровье никогда не падает ниже 1.
 * Пойманного контрабандиста метим "smuggler_caught" — он "исправился" навсегда:
 * маскировку больше не надевает и полицию больше не интересует.
 */
public class PoliceHuntSmugglerGoal extends Goal {

    private static final double DETECT_RANGE = 24.0;
    private static final double LEASH_RANGE = 40.0;
    private static final double ATTACK_RANGE = 2.2;
    private static final float CATCH_HEALTH_THRESHOLD = 2.0F;

    private final Villager officer;
    private Villager target;
    private int attackCooldown;

    public PoliceHuntSmugglerGoal(Villager officer) {
        this.officer = officer;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static boolean isFighter(Villager v) {
        return v.getTags().contains("police_hunter");
    }

    private static boolean isExposedSmuggler(Villager v) {
        return v.getTags().contains("smuggler")
                && !v.getTags().contains("smuggler_caught")
                && v.isAlive()
                && SmugglerTradeHelper.isHoodOn(v);
    }

    @Override
    public boolean canUse() {
        if (!officer.getTags().contains("police_hunter")) return false;

        // Уже кого-то преследуем — продолжаем независимо от дня/ночи/капюшона.
        if (target != null && isValidOngoingTarget(target)) return true;
        target = null;

        // Новую цель ищем только ночью и только если этот полицейский на дежурстве.
        if (!NightDutyHelper.isNight(officer.level())) return false;
        if (!NightDutyHelper.isActiveNow(officer)) return false;

        target = findNearestExposedSmuggler();
        return target != null;
    }

    private boolean isValidOngoingTarget(Villager candidate) {
        return candidate.isAlive()
                && !candidate.getTags().contains("smuggler_caught")
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
                attackCooldown = 30;
            }
        }
    }

    private void strike() {
        officer.swing(InteractionHand.MAIN_HAND);
        officer.level().playSound(null, officer.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0F, 1.0F);

        // Ненасмертельный удар — здоровье никогда не опускается ниже 1.
        float current = target.getHealth();
        float damage = Math.min(4.0F, current - 1.0F);
        if (damage > 0) {
            target.hurt(officer.damageSources().mobAttack(officer), damage);
        }

        if (officer.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(),
                    12, 0.4, 0.4, 0.4, 0.15);
        }

        if (target.getHealth() <= CATCH_HEALTH_THRESHOLD) {
            catchSmuggler();
        }
    }

    private void catchSmuggler() {
        target.removeTag("smuggler");
        target.addTag("smuggler_caught");
        SmugglerTradeHelper.takeHoodOff(target);
        SmugglerTradeHelper.clearOffers(target);
        target.setHealth(Math.min(target.getMaxHealth(), 8.0F));

        officer.level().playSound(null, officer.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        officer.level().getEntitiesOfClass(Player.class,
                new AABB(officer.blockPosition()).inflate(16.0)).forEach(player ->
                player.displayClientMessage(Component.translatable("message.maxonaddon.smuggler.caught"), true));

        target = null;
    }

    private Villager findNearestExposedSmuggler() {
        AABB box = officer.getBoundingBox().inflate(DETECT_RANGE);
        List<Villager> smugglers = officer.level().getEntitiesOfClass(Villager.class, box, PoliceHuntSmugglerGoal::isExposedSmuggler);
        if (smugglers.isEmpty()) return null;

        return smugglers.stream()
                .filter(officer::hasLineOfSight)
                .min(Comparator.comparingDouble(officer::distanceToSqr))
                .orElse(null);
    }
}
