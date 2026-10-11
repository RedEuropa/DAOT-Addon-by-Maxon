package com.maxonaddon.perk;

import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.network.SyncPerksPayload;
import com.maxonaddon.network.SyncUnlockedPerksPayload;
import com.maxonaddon.util.IPlayerStats;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Сборка эффектов от вставленных перков.
 *
 * Скорость и урон вешаются модификаторами атрибутов с фиксированными ID — при каждом
 * пересчёте старый модификатор снимается по ID и ставится новый, поэтому бонусы не
 * накапливаются при повторных вызовах. Операция ADD_MULTIPLIED_TOTAL даёт честные проценты
 * от итогового значения.
 */
public final class PerkManager {

    /** Сколько слотов под перки — столько же, сколько ячеек брони. */
    public static final int SLOTS = 5;

    private static final ResourceLocation SPEED_ID = MaxonDaotAddon.id("perk_move_speed");
    private static final ResourceLocation DAMAGE_ID = MaxonDaotAddon.id("perk_attack_damage");

    private PerkManager() {}

    /** Все перки, реально стоящие в слотах игрока. */
    public static List<ModPerk> equipped(Player player) {
        // Система выключена — считаем, что надетых перков нет. Одной проверки хватает на все
        // бонусы разом: и газ, и скидки, и атрибуты идут через этот список. Сами перки при
        // этом остаются в сохранении и вернутся, если систему включить обратно.
        if (!com.maxonaddon.MaxonConfig.get().perksSystemEnabled) return List.of();

        List<ModPerk> result = new ArrayList<>(SLOTS);
        String[] slots = ((IPlayerStats) player).getEquippedPerks();
        for (String id : slots) {
            ModPerk perk = ModPerk.byId(id);
            if (perk != null) result.add(perk);
        }
        return result;
    }

    /**
     * Общая экономия газа долей от 0 до 1. Ограничена 0.9, чтобы даже при куче перков
     * расход не мог уйти в ноль и УПМ не стал бесконечным.
     */
    public static double gasEconomy(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getGasEconomy();
        }
        return Math.min(0.9, total);
    }

    /**
     * Насколько выгоднее сделки, долей от 0 до 1. Сейчас источник один — перк "Торговец",
     * но метод считает сумму по всем надетым, как и остальные бонусы, и режет её по 0.9:
     * бесплатных покупок быть не должно.
     */
    public static double tradeDiscount(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            if (perk == ModPerk.TRADER) total += 0.25;
        }
        return Math.min(0.9, total);
    }

    /** Цена со скидкой торговца, но не ниже одной единицы: даром не отдаём. */
    public static int discountedPrice(Player player, int basePrice) {
        if (basePrice <= 0) return basePrice;
        double discount = tradeDiscount(player);
        if (discount <= 0.0) return basePrice;
        return Math.max(1, (int) Math.round(basePrice * (1.0 - discount)));
    }

    /** Выручка с надбавкой торговца — для того, что скупают У игрока. */
    public static int boostedPayout(Player player, int basePayout) {
        if (basePayout <= 0) return basePayout;
        return (int) Math.round(basePayout * (1.0 + tradeDiscount(player)));
    }

    /** Суммарный бонус к скорости крюка/полёта УПМ-APG от всех надетых перков (см. PerkOdmSpeedMixin). */
    public static double odmSpeedBonus(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getOdmSpeedBonus();
        }
        return total;
    }

    /** Суммарный бонус к шансу крита по титанам от всех надетых перков (см. TitanMeleeNapeMixin). */
    public static double critChanceBonus(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getCritChanceBonus();
        }
        return total;
    }

    /**
     * Суммарный бонус к урону по титанам от перков (в долях, 0.10 = +10%). Используется
     * TitanMeleeNapeMixin отдельно от refreshAttributes() ниже: удар клинка по титану считает
     * урон от фиксированной базы (30.0f), а не от ванильного Attributes.ATTACK_DAMAGE, поэтому
     * бонус перка нужно ещё раз явно приплюсовать к тому же множителю, что и бонус навыков.
     */
    public static double attackDamageBonus(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getAttackDamageBonus();
        }
        return total;
    }

    /** Суммарный бонус к урону крита по титанам (см. TitanMeleeNapeMixin, тот же слот, что "Палач"). */
    public static double critDamageBonus(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getCritDamageBonus();
        }
        return total;
    }

    /** Суммарный бонус к потолку стамины титан-шифтера от перков (см. TitanEnduranceMixin). */
    public static double titanStaminaBonus(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getTitanStaminaBonus();
        }
        return total;
    }

    /** Суммарный бонус к MAX_HEALTH оседланного игроком титана (см. HeroPerkHandler). */
    public static double titanHealthBonus(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getTitanHealthBonus();
        }
        return total;
    }

    /** Суммарный бонус к MOVEMENT_SPEED оседланного игроком титана (см. HeroPerkHandler). */
    public static double titanSpeedBonus(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getTitanSpeedBonus();
        }
        return total;
    }

    /** Доп. снижение расхода заряда пробуждения Аккермана (см. цикл в MaxonDaotAddon.java). */
    public static double ackermanEnduranceBonus(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getAckermanEnduranceBonus();
        }
        return total;
    }

    /** Суммарное снижение урона от падения (доля 0..1, см. PlayerFallDamageMixin). */
    public static double fallDamageReduction(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getFallDamageReduction();
        }
        return Math.min(0.9, total);
    }

    /** Суммарное снижение расхода прочности клинков (доля 0..1, см. AckermanBladedanceMixin). */
    public static double bladeDurabilityBonus(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getBladeDurabilityBonus();
        }
        return Math.min(0.9, total);
    }

    /** Суммарное снижение истощения/голода от действий (доля 0..1, см. HungerReductionMixin). */
    public static double hungerReduction(Player player) {
        double total = 0.0;
        for (ModPerk perk : equipped(player)) {
            total += perk.getHungerReduction();
        }
        return Math.min(0.9, total);
    }

    /** Пересчитывает модификаторы атрибутов под текущий набор перков. */
    public static void refreshAttributes(ServerPlayer player) {
        double speed = 0.0;
        double damage = 0.0;
        for (ModPerk perk : equipped(player)) {
            speed += perk.getMoveSpeedBonus();
            damage += perk.getAttackDamageBonus();
        }

        applyModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, speed);
        applyModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, damage);
    }

    private static void applyModifier(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) return;

        // Снимаем всегда: иначе при смене перков остался бы старый бонус.
        attribute.removeModifier(id);

        if (amount != 0.0) {
            attribute.addTransientModifier(
                    new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            );
        }
    }

    /**
     * Пересчитывает эффекты и отправляет игроку и надетые слоты, и полный список
     * разблокированных перков — единая точка, вызываемая при заходе, возрождении и смене
     * перка, чтобы клиент всегда знал и что надето, и что вообще доступно надеть.
     */
    public static void refreshAndSync(ServerPlayer player) {
        refreshAttributes(player);

        IPlayerStats stats = (IPlayerStats) player;

        String[] slots = stats.getEquippedPerks();
        List<String> slotsCopy = new ArrayList<>(SLOTS);
        for (String id : slots) {
            slotsCopy.add(id == null ? "" : id);
        }
        ServerPlayNetworking.send(player, new SyncPerksPayload(slotsCopy));

        ServerPlayNetworking.send(player, new SyncUnlockedPerksPayload(new ArrayList<>(stats.getUnlockedPerks())));
    }
}
