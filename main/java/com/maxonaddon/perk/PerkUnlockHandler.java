package com.maxonaddon.perk;

import com.maxonaddon.util.IPlayerStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Проверка условий получения перков за достижения (пока — только перк ТЕСТ).
 *
 * Перк ТЕСТ выдаётся за убийство Огра Титана (daot.OgreTitanEntity) громовым копьём
 * (daot.ThunderSpearEntity) ИЛИ клинком УПМ (daot.BladeItem) в руке.
 *
 * ВАЖНО про killedEntity: у чистых титанов (Огр — один из них, реализует GrabbingTitan,
 * не ShifterTitan) урон в бою идёт через отдельную сущность-затылок (OgreTitanNapeEntity),
 * а не через самого титана напрямую — см. TitanMeleeNapeMixin. На смертельном ударе этот
 * миксин НЕ отменяет вызов, и решение "оторвать шею и убить родительского титана" в итоге
 * принимает сам daot (см. комментарий в UniversalTitanWeakPointMixin). Поэтому нет полной
 * гарантии, придёт ли в это событие сам OgreTitanEntity или его OgreTitanNapeEntity —
 * проверяем оба варианта, для затылка родителя достаём через публичный getParentTitan().
 *
 * Определение оружия:
 *  - Копьё: смотрим killedEntity.getLastDamageSource().getDirectEntity() — если это
 *    ThunderSpearEntity, титана убило именно оно. Тот же способ уже используется в
 *    daot.AttackTitanDamageMixin для проверки "это удар копья, бьём в обход брони".
 *  - Клинок: смотрим, что у игрока в руке В МОМЕНТ смерти титана. Это тот же критерий,
 *    которым сам TitanMeleeNapeMixin в этом аддоне проверяет "можно ли вообще атаковать
 *    титана" (mainHand/offHand instanceof BladeItem) — клинок наносит урон именно так,
 *    отдельного DamageSource для него daot не заводит.
 */
public final class PerkUnlockHandler {

    private PerkUnlockHandler() {}

    /**
     * Основной, надёжный путь — вызывается напрямую из TitanMeleeNapeMixin в момент
     * добивающего удара, когда оружие известно ТОЧНО (isThunderSpear), а не угадывается
     * постфактум по getLastDamageSource(): для Огра этот путь ненадёжен, поскольку убийство
     * там выполняется через parentTitan.die(source) в обход штатного hurt(), и
     * lastDamageSource у родителя может остаться не выставлен (см. onTitanKilled ниже).
     */
    public static void onWeaponTitanKill(Player player, LivingEntity titan, boolean isThunderSpear) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!(titan instanceof daot.OgreTitanEntity)) return;

        IPlayerStats stats = (IPlayerStats) serverPlayer;
        if (stats.hasUnlockedPerk(ModPerk.CYCLOPS.getId())) return;

        unlock(serverPlayer, stats, ModPerk.CYCLOPS);
    }

    /**
     * Запасной путь через общее событие AFTER_KILLED_OTHER_ENTITY в MaxonDaotAddon.java —
     * на случай, если титан умрёт как-то иначе, не через onWeaponTitanKill выше. Проверка
     * идемпотентна (hasUnlockedPerk), так что повторный вызов после onWeaponTitanKill безвреден.
     */
    public static void onTitanKilled(ServerPlayer player, LivingEntity killedEntity) {
        if (!isOgreTitanKill(killedEntity)) return;

        IPlayerStats stats = (IPlayerStats) player;
        if (stats.hasUnlockedPerk(ModPerk.CYCLOPS.getId())) return;

        if (killedByThunderSpear(killedEntity) || killedByBlade(player)) {
            unlock(player, stats, ModPerk.CYCLOPS);
        }
    }

    private static boolean isOgreTitanKill(LivingEntity killedEntity) {
        if (killedEntity instanceof daot.OgreTitanEntity) return true;
        // getParentTitan() у OgreTitanNapeEntity — обычный публичный метод, не рефлексия
        // (сигнатура подтверждена: public daot.OgreTitanEntity getParentTitan()).
        return killedEntity instanceof daot.OgreTitanNapeEntity nape && nape.getParentTitan() != null;
    }

    private static boolean killedByThunderSpear(LivingEntity killedEntity) {
        DamageSource source = killedEntity.getLastDamageSource();
        return source != null && source.getDirectEntity() instanceof daot.ThunderSpearEntity;
    }

    private static boolean killedByBlade(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        return mainHand.getItem() instanceof daot.BladeItem || offHand.getItem() instanceof daot.BladeItem;
    }

    /**
     * Публичная точка выдачи перка НЕ за убийство титана — например, награда за сдачу квеста
     * (см. QuestActionPayload-обработчик в MaxonDaotAddon.java). Идемпотентна, как и остальные
     * пути выдачи: повторный вызов на уже разблокированный перк ничего не делает.
     */
    public static void grantPerk(ServerPlayer player, ModPerk perk) {
        IPlayerStats stats = (IPlayerStats) player;
        if (stats.hasUnlockedPerk(perk.getId())) return;
        unlock(player, stats, perk);
    }

    private static void unlock(ServerPlayer player, IPlayerStats stats, ModPerk perk) {
        // Единственная точка выдачи перков — гейт стоит здесь, чтобы не расставлять его по
        // всем счётчикам (убийства титанов, сделки, маршруты, репутация).
        if (!com.maxonaddon.MaxonConfig.get().perksSystemEnabled) return;

        stats.unlockPerk(perk.getId());
        PerkManager.refreshAndSync(player);

        player.displayClientMessage(
                Component.translatable("message.maxonaddon.perk.unlocked", Component.translatable(perk.getNameKey()))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false
        );

        // playNotifySound — звук слышит ТОЛЬКО этот игрок (пакет лично ему), а не все рядом,
        // так же, как звук надевания брони.
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
    }
}
