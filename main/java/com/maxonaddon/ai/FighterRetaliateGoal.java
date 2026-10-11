package com.maxonaddon.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;

import java.util.EnumSet;

/**
 * Полиция, гарнизон и скауты дают сдачи, если их бьют — не только защищают других
 * (см. PoliceDefendVillagersGoal), но и постоят за себя. Используем встроенный
 * ванильный LivingEntity#getLastHurtByMob() — отдельный трекер тревоги не нужен,
 * он и так есть у любой LivingEntity бесплатно.
 *
 * Полный урон (не "арест", как у поимки контрабандиста) — это самозащита от реальной
 * угрозы. Не бьём своих: если ударивший — тоже боец (полиция/гарнизон/скаут), не мстим.
 */
public class FighterRetaliateGoal extends Goal {

    private static final double LEASH_RANGE = 32.0;
    private static final double ATTACK_RANGE = 2.2;
    private static final float STRIKE_DAMAGE = 6.0F;

    private final Villager fighter;
    private LivingEntity target;
    private int attackCooldown;

    public FighterRetaliateGoal(Villager fighter) {
        this.fighter = fighter;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static boolean isFighter(Villager v) {
        return v.getTags().contains("police_hunter")
                || v.getTags().contains("garrison_soldier")
                || v.getTags().contains("scout_legion");
    }

    private static boolean isFriendlyFighter(LivingEntity entity) {
        return entity instanceof Villager v && isFighter(v);
    }

    /** Винтовка может лежать в любой руке: вне боя в левой, в бою в правой. */
    private boolean hasRifle() {
        return fighter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                        .is(com.maxonaddon.MaxonDaotAddon.MARLEYAN_RIFLE_ITEM)
                || fighter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND)
                        .is(com.maxonaddon.MaxonDaotAddon.MARLEYAN_RIFLE_ITEM);
    }

    @Override
    public boolean canUse() {
        if (!isFighter(fighter)) return false;

        // У полицейского с винтовкой своя боевая задача (PoliceRifleAttackGoal), и обе висят
        // на приоритете 0 с одними флагами MOVE/LOOK. Рукопашная успевала перехватить ответ
        // на удар — полицейский откладывал винтовку и бежал махать кулаками.
        //
        // Проверяем ИМЕННО тег полиции, а не одно наличие винтовки: PoliceRifleAttackGoal
        // работает только на police_hunter, и если винтовка окажется у гарнизонного или
        // скаута, отказ от рукопашной оставил бы его вообще без боевой задачи — стоял бы
        // и молча принимал удары.
        if (fighter.getTags().contains("police_hunter") && hasRifle()) return false;

        if (target != null && isValidOngoingTarget(target)) return true;
        target = null;

        LivingEntity hurter = fighter.getLastHurtByMob();
        if (hurter == null || !isValidOngoingTarget(hurter)) return false;
        if (isFriendlyFighter(hurter)) return false;

        target = hurter;
        return true;
    }

    private boolean isValidOngoingTarget(LivingEntity candidate) {
        return candidate.isAlive() && fighter.distanceTo(candidate) < LEASH_RANGE;
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
        fighter.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) return;

        fighter.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (attackCooldown > 0) attackCooldown--;

        double dist = fighter.distanceTo(target);
        if (dist > ATTACK_RANGE) {
            fighter.getNavigation().moveTo(target, 1.0);
        } else {
            fighter.getNavigation().stop();
            if (attackCooldown <= 0) {
                strike();
                attackCooldown = 20;
            }
        }
    }

    private void strike() {
        fighter.swing(InteractionHand.MAIN_HAND);
        fighter.level().playSound(null, fighter.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0F, 1.0F);
        target.hurt(fighter.damageSources().mobAttack(fighter), STRIKE_DAMAGE);

        if (fighter.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(),
                    10, 0.4, 0.4, 0.4, 0.15);
        }
    }
}
