package com.maxonaddon.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Контрабандист ("smuggler") убегает от полицейского ("police_hunter"), который его заметил.
 * Не мешает обычной жизни жителя днём/когда никто не гонится — активируется только
 * при реальной близости охотника.
 */
public class SmugglerFleePoliceGoal extends Goal {

    private static final double FLEE_RANGE = 20.0;
    // Скорость уже задана атрибутом MOVEMENT_SPEED (см. SmugglerTradeHelper) — здесь без множителя.
    private static final double FLEE_SPEED = 1.0;
    private static final int REPATH_COOLDOWN = 12;

    private final Villager smuggler;
    private Villager threat;
    private int repathCooldown;

    public SmugglerFleePoliceGoal(Villager smuggler) {
        this.smuggler = smuggler;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!smuggler.getTags().contains("smuggler") || smuggler.getTags().contains("smuggler_caught")) return false;
        // Нельзя убегать посреди торговли с игроком — иначе меню закрывается сразу после открытия.
        if (smuggler.isTrading()) return false;
        threat = findNearestHunter();
        return threat != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (smuggler.isTrading()) return false;
        if (threat == null || !threat.isAlive()) return false;
        return smuggler.distanceTo(threat) < FLEE_RANGE + 6.0;
    }

    @Override
    public void start() {
        repathCooldown = 0;
        flee();
    }

    @Override
    public void stop() {
        threat = null;
        smuggler.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (threat == null) return;
        if (--repathCooldown <= 0) {
            repathCooldown = REPATH_COOLDOWN;
            flee();
        }
    }

    private void flee() {
        if (threat == null) return;

        Vec3 awayFromHunter = smuggler.position().subtract(threat.position());
        Vec3 fleeTarget = DefaultRandomPos.getPosAway(smuggler, 14, 6, awayFromHunter);
        if (fleeTarget == null) {
            fleeTarget = smuggler.position().add(awayFromHunter.normalize().scale(12.0));
        }

        smuggler.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, FLEE_SPEED);
    }

    @Nullable
    private Villager findNearestHunter() {
        AABB box = smuggler.getBoundingBox().inflate(FLEE_RANGE);
        List<Villager> hunters = smuggler.level().getEntitiesOfClass(Villager.class, box,
                v -> v.isAlive() && v.getTags().contains("police_hunter"));
        if (hunters.isEmpty()) return null;

        return hunters.stream()
                .filter(h -> smuggler.hasLineOfSight(h) || smuggler.distanceToSqr(h) < 10.0 * 10.0)
                .min(Comparator.comparingDouble(smuggler::distanceToSqr))
                .orElse(null);
    }
}
