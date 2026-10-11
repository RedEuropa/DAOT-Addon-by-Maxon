package com.maxonaddon.ai;

import com.maxonaddon.OutpostCadetHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;

import java.util.EnumSet;

/**
 * Кадет на сторожевой вышке расхаживает по балкону, а не стоит чучелом.
 * <p>
 * Точки маршрута выбираются вокруг поста — места, где кадет оказался при появлении, — и
 * только на его уровне: спуститься по лестнице внутрь башни или сойти во двор он не должен.
 * Отсюда же и «поводок»: отошёл дальше LEASH_RADIUS — первым делом возвращается.
 * <p>
 * Своя задача, а не ванильный RandomStrollGoal: тот выбирает точки по всей округе и увёл бы
 * пост гулять в поле.
 */
public class CadetPatrolGoal extends Goal {

    /** В каком радиусе от поста кадет выбирает следующую точку. */
    private static final int PATROL_RADIUS = 4;
    /** Дальше этого от поста считаем, что кадета унесло, и ведём обратно. */
    private static final double LEASH_RADIUS = 8.0;
    /** Насколько кадет готов подниматься/спускаться относительно уровня поста. */
    private static final int MAX_LEVEL_DIFF = 1;

    private static final double SPEED = 0.5;
    /** Пауза между переходами — чтобы кадет прохаживался, а не суетился. */
    private static final int PAUSE_TICKS = 70;

    private final Villager cadet;

    /** Место, где кадет заступил на пост. Берётся при первом запуске задачи. */
    private BlockPos post;
    private int pauseLeft;

    public CadetPatrolGoal(Villager cadet) {
        this.cadet = cadet;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.cadet.getTags().contains(OutpostCadetHandler.CADET_TAG);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        if (this.post == null) this.post = this.cadet.blockPosition();
        this.pauseLeft = 0;
    }

    @Override
    public void stop() {
        this.cadet.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.post == null) this.post = this.cadet.blockPosition();

        // Унесло с поста (столкнули, отбросил титан) — возвращаемся, не разгуливая.
        if (this.cadet.blockPosition().distSqr(this.post) > LEASH_RADIUS * LEASH_RADIUS) {
            this.cadet.getNavigation().moveTo(
                    this.post.getX() + 0.5, this.post.getY(), this.post.getZ() + 0.5, SPEED);
            this.pauseLeft = PAUSE_TICKS;
            return;
        }

        if (this.pauseLeft > 0) {
            this.pauseLeft--;
            return;
        }
        if (!this.cadet.getNavigation().isDone()) return;

        BlockPos next = pickSpot();
        if (next != null) {
            this.cadet.getNavigation().moveTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, SPEED);
        }
        this.pauseLeft = PAUSE_TICKS;
    }

    /** Случайная точка на балконе: рядом с постом, на его же уровне и со свободным местом. */
    private BlockPos pickSpot() {
        var random = this.cadet.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = random.nextInt(PATROL_RADIUS * 2 + 1) - PATROL_RADIUS;
            int dz = random.nextInt(PATROL_RADIUS * 2 + 1) - PATROL_RADIUS;
            int dy = random.nextInt(MAX_LEVEL_DIFF * 2 + 1) - MAX_LEVEL_DIFF;
            if (dx == 0 && dz == 0) continue;

            BlockPos candidate = this.post.offset(dx, dy, dz);
            if (!standable(candidate)) continue;
            return candidate;
        }
        return null;
    }

    /** Есть ли опора под ногами и место для самого кадета. */
    private boolean standable(BlockPos pos) {
        var level = this.cadet.level();
        BlockPos below = pos.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
    }
}
