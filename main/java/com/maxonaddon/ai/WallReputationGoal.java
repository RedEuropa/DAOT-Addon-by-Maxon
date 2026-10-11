package com.maxonaddon.ai;

import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.ReputationHelper;
import com.maxonaddon.util.Wall;
import com.maxonaddon.util.WallHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Реакция военной силы стены на репутацию игрока (см. Wall/WallHelper/ReputationHelper):
 * - репутация <= ENEMY_THRESHOLD (враг) и игрок подошёл близко — одна предупредительная
 *   "пощёчина" (несмертельный удар), не более раза в WARNING_COOLDOWN_TICKS на этого игрока;
 * - репутация <= WAR_THRESHOLD (война, 0) — настоящая, безостановочная вражда, пока не отойдёт
 *   или не поднимет репутацию.
 * Стена этого конкретного НПС берётся по его собственному округу (WallHelper.getWallAt) — то
 * есть полицейский/гарнизон/скаут реагирует на репутацию ИМЕННО своей стены, а не общую.
 */
public class WallReputationGoal extends Goal {

    private static final double DETECT_RANGE = 16.0;
    private static final double WARNING_RANGE = 6.0; // "подошёл близко" — уже, чем полный обзор
    private static final double ATTACK_RANGE = 2.2;
    private static final long WARNING_COOLDOWN_TICKS = 1200L; // 60 секунд между пощёчинами одному игроку

    private final Villager officer;
    private ServerPlayer target;
    private boolean isWarTarget;
    private boolean warningDelivered;
    private int attackCooldown;

    private final Map<UUID, Long> lastWarningTick = new HashMap<>();

    public WallReputationGoal(Villager officer) {
        this.officer = officer;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static boolean isSoldier(Villager v) {
        return v.getTags().contains("police_hunter")
                || v.getTags().contains("garrison_soldier")
                || v.getTags().contains("scout_legion");
    }

    @Override
    public boolean canUse() {
        if (!isSoldier(officer)) return false;
        if (!(officer.level() instanceof ServerLevel level)) return false;

        Wall wall = WallHelper.getWallForSoldier(officer);
        if (wall == null) return false;

        if (target != null && isWarTarget && isValidWarTarget(target, wall)) return true;
        target = null;

        AABB box = officer.getBoundingBox().inflate(DETECT_RANGE);
        List<ServerPlayer> nearby = level.getEntitiesOfClass(ServerPlayer.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && officer.hasLineOfSight(p));

        ServerPlayer warCandidate = nearby.stream()
                .filter(p -> ReputationHelper.isAtWar(((IPlayerStats) p).getReputation(wall)))
                .min(Comparator.comparingDouble(officer::distanceToSqr))
                .orElse(null);

        if (warCandidate != null) {
            target = warCandidate;
            isWarTarget = true;
            return true;
        }

        long now = level.getGameTime();
        ServerPlayer warningCandidate = nearby.stream()
                .filter(p -> officer.distanceTo(p) <= WARNING_RANGE)
                .filter(p -> ReputationHelper.isEnemy(((IPlayerStats) p).getReputation(wall)))
                .filter(p -> {
                    Long last = lastWarningTick.get(p.getUUID());
                    return last == null || now - last >= WARNING_COOLDOWN_TICKS;
                })
                .min(Comparator.comparingDouble(officer::distanceToSqr))
                .orElse(null);

        if (warningCandidate != null) {
            target = warningCandidate;
            isWarTarget = false;
            return true;
        }

        return false;
    }

    private boolean isValidWarTarget(ServerPlayer candidate, Wall wall) {
        return candidate.isAlive() && !candidate.isCreative() && !candidate.isSpectator()
                && ReputationHelper.isAtWar(((IPlayerStats) candidate).getReputation(wall))
                && officer.distanceTo(candidate) < DETECT_RANGE * 1.5;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null) return false;
        if (isWarTarget) {
            Wall wall = WallHelper.getWallForSoldier(officer);
            return wall != null && isValidWarTarget(target, wall);
        }
        return !warningDelivered; // разовая цель — как только ударили один раз, сразу отпускаем
    }

    @Override
    public void start() {
        attackCooldown = 0;
        warningDelivered = false;
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
            officer.getNavigation().moveTo(target, 1.0);
        } else {
            officer.getNavigation().stop();
            if (attackCooldown <= 0) {
                strike();
                attackCooldown = isWarTarget ? 20 : 999; // разовая цель бьёт один раз и всё
            }
        }
    }

    private void strike() {
        officer.swing(InteractionHand.MAIN_HAND);
        officer.level().playSound(null, officer.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0F, 1.0F);

        if (isWarTarget) {
            target.hurt(officer.damageSources().mobAttack(officer), 4.0F);
        } else {
            // Предупредительная "пощёчина" — не насмерть, просто напоминание, что тут вас не любят.
            float damage = Math.min(3.0F, target.getHealth() - 1.0F);
            if (damage > 0) {
                target.hurt(officer.damageSources().mobAttack(officer), damage);
            }
            lastWarningTick.put(target.getUUID(), officer.level().getGameTime());
            target.displayClientMessage(Component.translatable("message.maxonaddon.reputation.police_warning").withStyle(ChatFormatting.RED), true);
            warningDelivered = true;
        }
    }
}
