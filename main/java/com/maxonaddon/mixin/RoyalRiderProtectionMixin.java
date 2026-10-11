package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import daot.BloodlineData;
import daot.BloodlineType;
import daot.VillagerTransformTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Защита всадника-командира от собственного подконтрольного титана (Королевская кровь).
 *
 * Разбор байткода daot.TitanEntity.performEat(LivingEntity) показал, что титан УЖЕ не ест
 * своего пассажира (там есть this.hasPassenger(victim) return) — это работает штатно.
 * Никакого хардкод-таймера "срок подчинения" в daot найти не удалось (проверены
 * BeastTitanEntity, SadTitanEntity, PowerAuthority, VillagerTransformTracker, ModNetworking —
 * владение титаном определяется ИСКЛЮЧИТЕЛЬНО тегом "dannysaot_roar_owner:<имя>" без TTL).
 *
 * Раз явного "таймера" нет, а жалоба на "перестаёт слушаться и ест" реальна, самая надёжная
 * защита — не гадать, ЧЕРЕЗ какой конкретно путь титан наносит урон своему всаднику
 * (укус, грэб, обычная атака), а перехватить САМ РЕЗУЛЬТАТ: если урон пришёл от титана,
 * которым игрок прямо сейчас управляет (тот же тег владения + навык + Королевская кровь),
 * урон просто отменяется. Работает независимо от причины сбоя послушания на стороне daot.
 */
@Mixin(LivingEntity.class)
public abstract class RoyalRiderProtectionMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void maxon$blockOwnTitanDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player)) return;

        Entity attacker = source.getDirectEntity();
        if (attacker == null) {
            attacker = source.getEntity();
        }
        if (attacker == null) return;

        String className = attacker.getClass().getName();
        if (!className.startsWith("daot.") || !(className.contains("Titan") || className.contains("ConnieFather"))) {
            return;
        }
        if (className.contains("Eye") || className.contains("Nape") || className.contains("Grab")) return;

        String ownerName = VillagerTransformTracker.getOwnerName(attacker);
        if (ownerName == null || !ownerName.equals(player.getGameProfile().getName())) return;

        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        BloodlineData data = BloodlineData.get(serverLevel);
        if (data.getBloodline(player.getUUID()) != BloodlineType.ROYAL) return;

        IPlayerStats stats = (IPlayerStats) player;
        if (!stats.hasSkill("royal_control_1")) return;

        // Свой же подконтрольный титан не должен ранить командира ни при каких обстоятельствах.
        cir.setReturnValue(false);
    }
}
