package com.maxonaddon.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * "Воля к жизни" — на пол-сердечка (1 HP) автоматически исцеляет титан-шифтера до полного
 * здоровья, ломая ему случайные конечности взамен (1 уровень — 3 конечности, 2 уровень —
 * только 1, и вдвое короче откат).
 */
public final class WillToLiveHandler {

    private WillToLiveHandler() {}

    public static void tickCooldowns(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        if (stats.getWillToLiveCooldown() > 0) {
            stats.setWillToLiveCooldown(stats.getWillToLiveCooldown() - 1);
        }
    }

    public static void checkLowHealth(ServerPlayer player) {
        if (!player.isAlive() || player.getHealth() > 1.0F) return;

        IPlayerStats stats = (IPlayerStats) player;
        if (!stats.hasSkill("will_to_live_1")) return;
        if (stats.getWillToLiveCooldown() > 0) return;

        boolean tier2 = stats.hasSkill("will_to_live_2");
        int cooldownSeconds = tier2 ? 1500 : 3000;
        stats.setWillToLiveCooldown(cooldownSeconds * 20);

        // Полное исцеление ценой сломанных конечностей.
        player.setHealth(player.getMaxHealth());

        // --- ЖЕСТКИЙ УДАР ПО СТАМИНЕ И ЗАПРЕТ ПРЕВРАЩЕНИЯ ---
        // 1. Полностью сжигаем стамину игрока через внутренний метод DAOT
        float maxStamina = daot.network.ModNetworking.getMaxStaminaForPlayer(player);
        daot.network.ModNetworking.drainStamina(player.getUUID(), maxStamina);

        // 2. Мод DAOT жестко блокирует шифт, если на игроке висит эффект WEAKNESS с силой 4 и выше.
        // Накидываем Слабость 5 на 3 минуты (3600 тиков), чтобы игрок точно не мог превратиться.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 3600, 4));

        int limbsToBreak = tier2 ? 1 : 3;
        List<TraumaType> pool = new ArrayList<>(List.of(TraumaType.values()));

        // Тасуем вручную (Фишер-Йейтс).
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = player.getRandom().nextInt(i + 1);
            TraumaType tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }

        for (int i = 0; i < Math.min(limbsToBreak, pool.size()); i++) {
            TraumaManager.applyMonsterTrauma(player, pool.get(i));
        }

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(
                Component.translatable("message.maxonaddon.will_to_live.triggered"),
                true
        );
    }
}
