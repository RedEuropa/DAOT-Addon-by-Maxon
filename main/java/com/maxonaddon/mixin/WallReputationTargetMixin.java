package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.ReputationHelper;
import com.maxonaddon.util.Wall;
import com.maxonaddon.util.WallHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Военные стены (police_hunter/garrison_soldier/scout_legion) не атакуют игрока В ФОРМЕ ТИТАНА,
 * если у него дружеская репутация (>= FRIEND_THRESHOLD) со стеной ИМЕННО этого НПС — КРОМЕ
 * берсерк-режима (тег "berserk_boss" на титане, см. BerserkBossHandler): берсерка атакуют
 * всегда, репутация тут не при чём — явное требование "если игрок берсерк, то его титана
 * атакуют на любой репутации".
 * <p>
 * ВАЖНО: у титана в этом моде 4 разных хитбокса — сам titan (тело, на нём и едет игрок,
 * getControllingPassenger() есть только тут), eye, nape и grab — это ОТДЕЛЬНЫЕ сущности.
 * ИИ-цель солдата почти всегда именно nape/eye (по ним и бьют), а не тело титана, поэтому
 * простой target.getControllingPassenger() у него всегда null и раньше проверка молча
 * пропускалась. У всех этих доп-хитбоксов есть публичный getParentTitan() (тот же приём,
 * что уже используется в UniversalTitanWeakPointMixin/TitanMeleeNapeMixin/
 * ShifterGrabQteHandler/RoyalTitanInteractHandler) — поднимаемся через него до настоящего
 * тела титана и уже там проверяем пассажира/тег берсерка.
 */
@Mixin(Mob.class)
public abstract class WallReputationTargetMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void maxon$blockFriendlyTitanTarget(LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (target == null) return;

        if (!self.getTags().contains("police_hunter")
                && !self.getTags().contains("garrison_soldier")
                && !self.getTags().contains("scout_legion")) {
            return;
        }

        LivingEntity titan = maxon$resolveParentTitan(target);

        if (titan.getTags().contains("berserk_boss")) return; // берсерка бьют всегда

        LivingEntity passenger = titan.getControllingPassenger();
        if (!(passenger instanceof ServerPlayer player)) return; // не титан игрока — не наш случай

        // По тегу ветки, НЕ по позиции — garrison_soldier/scout_legion спавнятся где угодно на
        // карте (см. WallHelper.getWallForSoldier), позиционный getWallAt(self) для них почти
        // всегда null, из-за чего иммунитет друга раньше никогда не срабатывал.
        Wall wall = WallHelper.getWallForSoldier(self);
        if (wall == null) return;

        if (ReputationHelper.isFriend(((IPlayerStats) player).getReputation(wall))) {
            ci.cancel();
        }
    }

    /**
     * Если target — уже само тело титана (у него есть пассажир), возвращает его как есть.
     * Иначе пробует target.getParentTitan() (eye/nape/grab-хитбоксы) и поднимается до тела.
     * Если метода нет или он ничего не вернул — отдаёт исходный target как запасной вариант.
     */
    private static LivingEntity maxon$resolveParentTitan(LivingEntity target) {
        if (target.getControllingPassenger() != null) return target;

        try {
            Object parent = target.getClass().getMethod("getParentTitan").invoke(target);
            if (parent instanceof LivingEntity parentTitan) return parentTitan;
        } catch (Exception ignored) {}

        return target;
    }
}
