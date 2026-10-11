package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Снижение голода от пробуждения Аккермана — через УКОРОЧЕНИЕ длительности эффекта Голода,
 * а не через изменение количества "ножек" (единиц истощения на снятие очка сытости).
 *
 * Байткодом подтверждено: daot.AwakenedPowerTracker.applyHungerEffect каждые 40 тиков
 * накладывает MobEffects.HUNGER с amplifier=19, duration=60 тиков. Поскольку 60 > 40,
 * эффект ВСЕГДА активен без единого разрыва (100% аптайм) — именно он и жрёт голод
 * (вэнильная формула Голода добавляет exhaustion каждый тик, пропорционально amplifier+1).
 * Мы укорачиваем duration ниже 40 тиков — тогда между накладыванием эффекта появляется
 * реальное "окно" без штрафа, и голод объективно тратится медленнее.
 */
@Mixin(LivingEntity.class)
public abstract class AckermanHungerMixin {

    // Полный цикл повтора эффекта в daot — 40 тиков (2 сек), исходная длительность — 60.
    private static final int REAPPLY_CYCLE_TICKS = 40;
    private static final int HUNGER_AMPLIFIER = 19; // сигнатура именно нашего эффекта от пробуждения

    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z", at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance maxon$shortenAckermanHunger(MobEffectInstance instance) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return instance;

        if (!instance.getEffect().is(MobEffects.HUNGER) || instance.getAmplifier() != HUNGER_AMPLIFIER) {
            return instance;
        }
        if (!player.getTags().contains("dannys-aot:awakened_power")) return instance;

        IPlayerStats stats = (IPlayerStats) player;
        int newDuration;
        if (stats.hasSkill("ackerman_endurance_2")) {
            newDuration = 28; // ~70% аптайма из 40-тикового цикла — суммарно -30% голода
        } else if (stats.hasSkill("ackerman_endurance_1")) {
            newDuration = 34; // ~85% аптайма — суммарно -15% голода
        } else {
            return instance;
        }

        return new MobEffectInstance(instance.getEffect(), newDuration, instance.getAmplifier(),
                instance.isAmbient(), instance.isVisible(), instance.showIcon());
    }
}
