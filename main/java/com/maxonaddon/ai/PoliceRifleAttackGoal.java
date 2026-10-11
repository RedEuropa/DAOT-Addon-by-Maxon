package com.maxonaddon.ai;

import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.item.MarleyanRifleItem;
import com.maxonaddon.util.SmugglerTradeHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Боевое поведение полицейского с марлийской винтовкой.
 * <p>
 * Вне боя винтовка висит в левой руке (её туда кладёт PoliceGearHelper). Появился противник —
 * задача перекладывает оружие в правую руку, держит дистанцию и стреляет: одна пуля — один
 * патрон, кончился магазин — перезарядка. Бой закончился — винтовка возвращается в левую руку.
 * <p>
 * Цель ищется СВОИМ полем, а не через Mob#getTarget(): Villager управляется мозгом, и
 * ванильное поле цели у него никто не выставляет — именно поэтому первая версия этой задачи
 * не запускалась вообще. Источники цели те же, что у соседних задач полиции: тот, кто ударил
 * (LivingEntity#getLastHurtByMob, как в FighterRetaliateGoal), и раскрытый контрабандист —
 * тот, на ком надет капюшон, то есть по факту только ночью.
 * <p>
 * Стрельба тут своя, а не общая с игроком: MaxonDaotAddon.shootMarleyanRifle завязан на
 * ServerPlayer (кулдауны предметов, серверный уровень игрока) и для Villager не подходит.
 */
public class PoliceRifleAttackGoal extends Goal {

    /** Дальше этого стрелять не начинают. */
    private static final double MAX_RANGE = 28.0;
    /** Ближе этого отходят: винтовка — не для ближнего боя. */
    private static final double MIN_RANGE = 6.0;
    /** На таком удалении цель перестаёт считаться своей. */
    private static final double LEASH_RANGE = 40.0;
    /** Радиус поиска контрабандиста — как DETECT_RANGE у погони. */
    private static final double DETECT_RANGE = 24.0;

    private static final float DAMAGE = 7.0F;
    private static final int SHOT_INTERVAL = 45;
    /** Длины разовых фаз в тиках — под animation_length из marleyan_rifle_hand.animation.json. */
    private static final int SHOT_HOLD_TICKS = 25;   // shoot ~1.27 c
    private static final int BOLT_HOLD_TICKS = 30;   // bolt_cycle 1.5 c
    private static final int RELOAD_TICKS = 70;

    /**
     * Порог здоровья, ниже которого контрабандиста положено брать живым
     * (см. CATCH_HEALTH_THRESHOLD в PoliceHuntSmugglerGoal).
     */
    private static final float CATCH_HEALTH_THRESHOLD = 2.0F;

    private final Villager police;

    /** Сколько тиков подряд по цели нельзя работать, прежде чем бросить её и убрать оружие. */
    private static final int LOSE_TARGET_TICKS = 100;

    private LivingEntity target;
    private int shotCooldown;
    private int reloadTicks;
    /** Счётчик тиков без возможности выстрела: цель ушла за MAX_RANGE или спряталась. */
    private int noShotTicks;

    /** Последняя разосланная клиентам фаза анимации — чтобы не слать пакет каждый тик. */
    private int animPhase = com.maxonaddon.network.RifleAnimPayload.IDLE;
    /** Сколько тиков ещё доигрывается разовая фаза (выстрел, затвор). */
    private int animHold;

    public PoliceRifleAttackGoal(Villager police) {
        this.police = police;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.police.getTags().contains("police_hunter")) return false;
        if (findRifle() == null) return false;

        // Уже кого-то ведём — продолжаем.
        if (this.target != null && isValidTarget(this.target)) return true;
        this.target = null;

        // 1. Тот, кто ударил. Работает в любое время суток и вне зависимости от дежурства:
        //    на агрессию отвечают всегда.
        LivingEntity hurter = this.police.getLastHurtByMob();
        if (hurter != null && isValidTarget(hurter) && !isFriendly(hurter)) {
            this.target = hurter;
            return true;
        }

        // 2. Раскрытый контрабандист. Требование ночного дежурства снято намеренно: с ним
        //    стрелял только тот патруль, которому выпал жребий на эту ночь, и со стороны это
        //    выглядело как «полиция вообще не реагирует». Ограничение по времени суток
        //    осталось само собой — контрабандист считается раскрытым только пока на нём
        //    капюшон, а его надевают лишь ночью (см. SmugglerTradeHelper).
        //    Задержание при этом по-прежнему за дежурными: стрельба останавливается у порога
        //    ареста (см. shouldShoot), а довершает дело PoliceHuntSmugglerGoal.
        Villager smuggler = findNearestExposedSmuggler();
        if (smuggler != null) {
            this.target = smuggler;
            return true;
        }

        // 3. Напал на жителя — по тревоге (PoliceAlertTracker). Раньше на это отвечала только
        //    PoliceDefendVillagersGoal, и отвечала кулаками; теперь стрелок берёт ту же цель
        //    и работает по ней винтовкой, как по контрабандисту.
        //    consume() съедает сигнал, поэтому вызываем его уже после проверки на винтовку
        //    в начале метода: безоружный полицейский сигнал не заберёт, и рукопашная задача
        //    получит его в целости.
        if (this.police.level() instanceof ServerLevel level) {
            java.util.UUID attackerId =
                    com.maxonaddon.util.PoliceAlertTracker.consume(this.police, level.getGameTime());
            if (attackerId != null) {
                net.minecraft.world.entity.player.Player aggressor = level.getPlayerByUUID(attackerId);
                if (aggressor != null && !aggressor.isSpectator() && !aggressor.isCreative()
                        && isValidTarget(aggressor)) {
                    this.target = aggressor;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        // Бой заканчивается не только со смертью цели: если по ней уже несколько секунд
        // нельзя работать — ушла за дальность или засела за укрытием — полицейский бросает
        // её и возвращается к службе. Без этого условия цель держалась, пока жива и ближе
        // 40 блоков, и винтовка так и оставалась в боевой руке.
        return this.target != null
                && isValidTarget(this.target)
                && findRifle() != null
                && this.noShotTicks < LOSE_TARGET_TICKS;
    }

    private boolean isValidTarget(LivingEntity candidate) {
        return candidate.isAlive() && this.police.distanceTo(candidate) < LEASH_RANGE;
    }

    /** Своих — полицию, гарнизон, скаутов — не расстреливаем. */
    private boolean isFriendly(LivingEntity entity) {
        if (!(entity instanceof Villager v)) return false;
        return v.getTags().contains("police_hunter")
                || v.getTags().contains("garrison_soldier")
                || v.getTags().contains("scout_legion");
    }

    private Villager findNearestExposedSmuggler() {
        AABB box = this.police.getBoundingBox().inflate(DETECT_RANGE);
        List<Villager> found = this.police.level().getEntitiesOfClass(Villager.class, box, v ->
                v.isAlive()
                        && v.getTags().contains("smuggler")
                        && !v.getTags().contains("smuggler_caught")
                        && SmugglerTradeHelper.isHoodOn(v));

        Villager best = null;
        double bestDist = Double.MAX_VALUE;
        for (Villager candidate : found) {
            double dist = this.police.distanceToSqr(candidate);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    /** Винтовка может быть в любой руке — вне боя в левой, в бою в правой. */
    private ItemStack findRifle() {
        ItemStack main = this.police.getItemBySlot(EquipmentSlot.MAINHAND);
        if (main.is(MaxonDaotAddon.MARLEYAN_RIFLE_ITEM)) return main;

        ItemStack off = this.police.getItemBySlot(EquipmentSlot.OFFHAND);
        if (off.is(MaxonDaotAddon.MARLEYAN_RIFLE_ITEM)) return off;

        return null;
    }

    @Override
    public void start() {
        // В бою оружие в правой руке — так его видно в замахе и так же его держит игрок.
        ItemStack off = this.police.getItemBySlot(EquipmentSlot.OFFHAND);
        if (off.is(MaxonDaotAddon.MARLEYAN_RIFLE_ITEM)) {
            this.police.setItemSlot(EquipmentSlot.MAINHAND, off.copy());
            this.police.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        this.shotCooldown = 20;
        this.reloadTicks = 0;
        this.noShotTicks = 0;
    }

    @Override
    public void stop() {
        // Бой кончился — вешаем обратно в левую руку.
        ItemStack main = this.police.getItemBySlot(EquipmentSlot.MAINHAND);
        if (main.is(MaxonDaotAddon.MARLEYAN_RIFLE_ITEM)) {
            this.police.setItemSlot(EquipmentSlot.OFFHAND, main.copy());
            this.police.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        this.police.getNavigation().stop();
        this.target = null;
        this.reloadTicks = 0;
        this.animHold = 0;
        this.noShotTicks = 0;
        setAnimPhase(com.maxonaddon.network.RifleAnimPayload.IDLE);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        this.police.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        double distance = this.police.distanceTo(this.target);
        // Считаем один раз за тик: ниже это нужно и для манёвра, и для решения о выстреле.
        boolean clearShot = hasClearShot();

        // Пока стрелять не по чему — копим счётчик; появилась возможность — сбрасываем.
        if (clearShot && distance <= MAX_RANGE) {
            this.noShotTicks = 0;
        } else {
            this.noShotTicks++;
        }

        // Держим дистанцию: далеко — подходим, слишком близко — пятимся. Стена между нами —
        // тоже повод сместиться: стоять и целиться в неё бессмысленно.
        if (distance > MAX_RANGE || !clearShot) {
            this.police.getNavigation().moveTo(this.target, 1.0);
        } else if (distance < MIN_RANGE) {
            Vec3 away = this.police.position().subtract(this.target.position()).normalize().scale(4.0);
            Vec3 retreat = this.police.position().add(away);
            this.police.getNavigation().moveTo(retreat.x, retreat.y, retreat.z, 1.0);
        } else {
            this.police.getNavigation().stop();
        }

        ItemStack rifle = findRifle();
        if (rifle == null) return;

        // Разовые фазы (выстрел, затвор) доигрываются, а затем возвращаемся к прицеливанию.
        if (this.animHold > 0) {
            this.animHold--;
            if (this.animHold == BOLT_HOLD_TICKS) {
                setAnimPhase(com.maxonaddon.network.RifleAnimPayload.BOLT);
            } else if (this.animHold == 0) {
                setAnimPhase(com.maxonaddon.network.RifleAnimPayload.AIM);
            }
        }

        // Перезарядка идёт своим чередом и стрелять не даёт.
        if (this.reloadTicks > 0) {
            setAnimPhase(com.maxonaddon.network.RifleAnimPayload.RELOAD);
            this.reloadTicks--;
            if (this.reloadTicks == 0) {
                MarleyanRifleItem.setLoadedRounds(rifle, MarleyanRifleItem.MAX_LOADED_ROUNDS);
                playSound(com.maxonaddon.sound.ModSounds.RIFLE_FULL_RELOAD, 1.0F);
            }
            return;
        }

        if (MarleyanRifleItem.getLoadedRounds(rifle) <= 0) {
            this.reloadTicks = RELOAD_TICKS;
            return;
        }

        if (this.animHold == 0) {
            setAnimPhase(com.maxonaddon.network.RifleAnimPayload.AIM);
        }

        if (this.shotCooldown > 0) {
            this.shotCooldown--;
            return;
        }

        if (distance <= MAX_RANGE && clearShot && shouldShoot()) {
            shoot(rifle);
            this.shotCooldown = SHOT_INTERVAL;
        }
    }

    /**
     * Контрабандиста положено брать живым, поэтому добивать его пулями нельзя: как только
     * следующий выстрел может увести здоровье под порог ареста, стрельба прекращается и
     * эстафету принимает PoliceHuntSmugglerGoal, который довершает задержание врукопашную.
     */
    private boolean shouldShoot() {
        if (this.target instanceof Villager v && v.getTags().contains("smuggler")) {
            return v.getHealth() - DAMAGE > CATCH_HEALTH_THRESHOLD;
        }
        return true;
    }

    /**
     * Куда целится стрелок: чуть ниже глаз цели. Точка одна и та же и в проверке линии огня,
     * и в самом выстреле — иначе можно было бы «увидеть» цель по одной траектории, а пулю
     * послать по другой, в стену.
     */
    private Vec3 aimPoint() {
        return this.target.getEyePosition().subtract(0.0, this.target.getBbHeight() * 0.25, 0.0);
    }

    /**
     * Свободна ли линия огня.
     * <p>
     * Ванильный Sensing#hasLineOfSight тут не годится: он смотрит из глаз в глаза, а пуля
     * летит ниже — из-за этого полицейский палил в подоконник или в край укрытия, тратя
     * патроны и поднимая шум. Клипаем ровно ту траекторию, по которой уйдёт выстрел.
     */
    private boolean hasClearShot() {
        if (this.target == null) return false;
        if (!(this.police.level() instanceof ServerLevel level)) return false;

        BlockHitResult hit = level.clip(new ClipContext(
                this.police.getEyePosition(), aimPoint(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.police));
        return hit.getType() != HitResult.Type.BLOCK;
    }

    private void shoot(ItemStack rifle) {
        if (!(this.police.level() instanceof ServerLevel level)) return;

        MarleyanRifleItem.setLoadedRounds(rifle, MarleyanRifleItem.getLoadedRounds(rifle) - 1);
        // swing() тут намеренно не зовём: он поднимает attackAnim, а по нему модель жителя
        // отыгрывает удар клинком сверху (down_slash) и перебивает нашу анимацию выстрела.
        // Отдача у винтовки своя — фаза SHOOT ниже.

        // Выстрел, затем лязг затвора — те же две фазы и в той же последовательности,
        // что отыгрывает игрок.
        setAnimPhase(com.maxonaddon.network.RifleAnimPayload.SHOOT);
        this.animHold = SHOT_HOLD_TICKS + BOLT_HOLD_TICKS;

        // Стену проверили до выстрела (hasClearShot), поэтому пуля здесь всегда доходит.
        Vec3 impact = aimPoint();
        this.target.hurt(level.damageSources().mobAttack(this.police), DAMAGE);

        playSound(com.maxonaddon.sound.ModSounds.RIFLE_SHOOT, 1.0F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                impact.x, impact.y, impact.z, 3, 0.05, 0.05, 0.05, 0.01);

        // Лязг затвора после выстрела — как у игрока, только без отдельной анимации.
        playSound(com.maxonaddon.sound.ModSounds.RIFLE_BOLT_RELOAD, 0.7F);
    }

    /**
     * Сообщает клиентам новую фазу анимации. Пакет уходит только при смене фазы и только
     * тем, кто реально видит этого жителя.
     */
    private void setAnimPhase(int phase) {
        if (this.animPhase == phase) return;
        this.animPhase = phase;

        if (this.police.level().isClientSide()) return;
        com.maxonaddon.network.RifleAnimPayload payload =
                new com.maxonaddon.network.RifleAnimPayload(this.police.getId(), phase);
        // tracking(entity) — те, кто реально отслеживает этого жителя, а не просто стоит
        // рядом с его чанком.
        for (net.minecraft.server.level.ServerPlayer viewer
                : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(this.police)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, payload);
        }
    }

    private void playSound(SoundEvent sound, float volume) {
        this.police.level().playSound(null, this.police.getX(), this.police.getY(), this.police.getZ(),
                sound, SoundSource.HOSTILE, volume, 1.0F);
    }
}
