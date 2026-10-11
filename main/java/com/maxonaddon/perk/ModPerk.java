package com.maxonaddon.perk;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Перки. В отличие от навыков, перки не покупаются, а вставляются в 5 слотов
 * (см. PerkManager и экран PerksScreen) и действуют, пока стоят в слоте.
 *
 * Числовые бонусы храним прямо здесь, чтобы добавление нового перка было одной строкой.
 * moveSpeedBonus и attackDamageBonus применяются модификаторами атрибутов, gasEconomy —
 * скидкой при списании газа УПМ (см. PerkGasEconomyMixin). odmSpeedBonus — отдельно от
 * moveSpeedBonus: скорость крюка/полёта УПМ-APG в daot считается не через ванильный
 * Attributes.MOVEMENT_SPEED, а через свой daot.ODMConfig (см. PerkOdmSpeedMixin), поэтому
 * это отдельная величина. critChanceBonus прибавляется к шансу крита в TitanMeleeNapeMixin.
 *
 * Добавленные для "Героя Человечества" бонусы:
 *  - critDamageBonus — множитель урона крита (TitanMeleeNapeMixin, тот же слот, что "Палач").
 *  - titanStaminaBonus — потолок стамины титан-шифтера (TitanEnduranceMixin).
 *  - titanHealthBonus / titanSpeedBonus — модификаторы MAX_HEALTH/MOVEMENT_SPEED САМОГО
 *    титана, пока игрок им управляет (см. HeroPerkHandler — периодически перевешивает
 *    модификаторы на текущего оседланного шифтера, т.к. у титана нет единой точки "игрок
 *    только что сел за штурвал", как у обычных навыков/перков на самом игроке).
 *  - ackermanEnduranceBonus — доп. снижение расхода "заряда пробуждения" Аккермана
 *    (см. цикл в MaxonDaotAddon.java рядом с AwakenedPowerTrackerAccessor) — тот же заряд,
 *    что определяет и длительность пробуждения Аккермана, и по факту "время контроля" при
 *    активной силе — здесь это один и тот же ресурс, отдельного второго не заводим.
 *  - fallDamageReduction — доля снижения урона от падения (PlayerFallDamageMixin).
 *  - bladeDurabilityBonus — доля снижения расхода прочности клинков (AckermanBladedanceMixin).
 *  - hungerReduction — доля снижения истощения/голода от действий (HungerReductionMixin).
 */
public enum ModPerk {

    /**
     * "Циклоп" (бывший тестовый перк): +5% скорости, -5% расхода газа, +10% урона.
     * Старый id "test" мигрируется в "cyclops" при загрузке сохранения — см.
     * PlayerStatsMixin#maxon$migratePerkId, иначе у всех, кто его уже открыл, перк пропал бы.
     */
    CYCLOPS("cyclops", 0.05, 0.05, 0.10, 0.0, 0.0, null,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Items.NETHER_STAR),

    /** Награда за первое тестовое задание: +5% скорости УПМ/APG, +5% шанс крита. */
    HUMANITY_FIGHTER("humanity_fighter", 0.0, 0.0, 0.0, 0.05, 0.05, net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Items.NETHER_STAR),

    /**
     * "Герой Человечества" — высшая награда: дружеская (>=70) репутация одновременно у ВСЕХ
     * трёх стен (см. ReputationHandler#checkHeroPerkUnlock). +10% почти ко всему сразу.
     * Иконка — Тотем Бессмертия (иначе плитка-нетер-звезда по умолчанию).
     */
    HUMANITY_HERO("humanity_hero",
            0.10,  // moveSpeedBonus — обычная ходьба
            0.10,  // gasEconomy — расход газа УПМ
            0.10,  // attackDamageBonus — урон по титанам
            0.10,  // odmSpeedBonus — скорость крюка/полёта УПМ-APG
            0.10,  // critChanceBonus — шанс крита
            null,  // effectIcon — используем предметную иконку (тотем), не спрайт эффекта
            0.10,  // critDamageBonus — урон крита
            0.10,  // titanStaminaBonus — потолок стамины титана
            0.10,  // titanHealthBonus — максимум ХП титана
            0.10,  // titanSpeedBonus — скорость передвижения в форме титана
            0.10,  // ackermanEnduranceBonus — доп. экономия заряда пробуждения Аккермана
            0.10,  // fallDamageReduction — снижение урона от падения
            0.10,  // bladeDurabilityBonus — снижение расхода прочности клинков
            0.10,  // hungerReduction — снижение затрат сытости
            Items.TOTEM_OF_UNDYING),
    /**
     * Путешественник:
     * Пассивно (проверяется отдельно): бесплатный проезд в повозках.
     * При надевании в слот: +5% скорости (ноги/лошадь), +5% скорости УПМ, -10% расхода газа.
     */
    TRAVELER("traveler",
                     0.05,  // moveSpeedBonus — скорость ходьбы/бега
                     0.10,  // gasEconomy — снижение расхода газа УПМ на 10%
                     0.0,   // attackDamageBonus
                     0.05,  // odmSpeedBonus — скорость полета на УПМ
                     0.0,   // critChanceBonus
             net.minecraft.world.effect.MobEffects.JUMP, // Иконка кролика от эффекта прыгучести
            0.0,   // critDamageBonus
                     0.0,   // titanStaminaBonus
                     0.0,   // titanHealthBonus
                     0.0,   // titanSpeedBonus
                     0.0,   // ackermanEnduranceBonus
                     0.0,   // fallDamageReduction
                     0.0,   // bladeDurabilityBonus
                     0.0,   // hungerReduction
             Items.RABBIT_FOOT), // Предмет-иконка

    /**
     * "Торговец": сделки на 25% выгоднее — дешевле проезд, лавка лошадей и товары
     * чёрного рынка, дороже то, что скупают у игрока. Плюс подсвечивает контрабандистов
     * красным свечением (см. TraderPerkHandler).
     * <p>
     * Числовых бонусов к бою и движению нет: скидка живёт не модификатором атрибута, а
     * отдельным множителем в местах, где считаются изумруды (PerkManager#tradeDiscount).
     */
    TRADER("trader",
                     0.0,   // moveSpeedBonus
                     0.0,   // gasEconomy
                     0.0,   // attackDamageBonus
                     0.0,   // odmSpeedBonus
                     0.0,   // critChanceBonus
             net.minecraft.world.effect.MobEffects.BAD_OMEN, // знак дурного знамени
                     0.0,   // critDamageBonus
                     0.0,   // titanStaminaBonus
                     0.0,   // titanHealthBonus
                     0.0,   // titanSpeedBonus
                     0.0,   // ackermanEnduranceBonus
                     0.0,   // fallDamageReduction
                     0.0,   // bladeDurabilityBonus
                     0.0,   // hungerReduction
             Items.EMERALD);


    private final String id;
    private final double moveSpeedBonus;
    private final double gasEconomy;
    private final double attackDamageBonus;
    private final double odmSpeedBonus;
    private final double critChanceBonus;
    @Nullable private final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effectIcon;

    private final double critDamageBonus;
    private final double titanStaminaBonus;
    private final double titanHealthBonus;
    private final double titanSpeedBonus;
    private final double ackermanEnduranceBonus;
    private final double fallDamageReduction;
    private final double bladeDurabilityBonus;
    private final double hungerReduction;
    private final Item iconItem;

    ModPerk(String id, double moveSpeedBonus, double gasEconomy, double attackDamageBonus,
            double odmSpeedBonus, double critChanceBonus,
            @Nullable net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effectIcon,
            double critDamageBonus, double titanStaminaBonus, double titanHealthBonus,
            double titanSpeedBonus, double ackermanEnduranceBonus, double fallDamageReduction,
            double bladeDurabilityBonus, double hungerReduction, Item iconItem) {
        this.id = id;
        this.moveSpeedBonus = moveSpeedBonus;
        this.gasEconomy = gasEconomy;
        this.attackDamageBonus = attackDamageBonus;
        this.odmSpeedBonus = odmSpeedBonus;
        this.critChanceBonus = critChanceBonus;
        this.effectIcon = effectIcon;
        this.critDamageBonus = critDamageBonus;
        this.titanStaminaBonus = titanStaminaBonus;
        this.titanHealthBonus = titanHealthBonus;
        this.titanSpeedBonus = titanSpeedBonus;
        this.ackermanEnduranceBonus = ackermanEnduranceBonus;
        this.fallDamageReduction = fallDamageReduction;
        this.bladeDurabilityBonus = bladeDurabilityBonus;
        this.hungerReduction = hungerReduction;
        this.iconItem = iconItem;
    }

    public String getId() { return this.id; }
    public double getMoveSpeedBonus() { return this.moveSpeedBonus; }
    public double getGasEconomy() { return this.gasEconomy; }
    public double getAttackDamageBonus() { return this.attackDamageBonus; }
    public double getOdmSpeedBonus() { return this.odmSpeedBonus; }
    public double getCritChanceBonus() { return this.critChanceBonus; }
    public double getCritDamageBonus() { return this.critDamageBonus; }
    public double getTitanStaminaBonus() { return this.titanStaminaBonus; }
    public double getTitanHealthBonus() { return this.titanHealthBonus; }
    public double getTitanSpeedBonus() { return this.titanSpeedBonus; }
    public double getAckermanEnduranceBonus() { return this.ackermanEnduranceBonus; }
    public double getFallDamageReduction() { return this.fallDamageReduction; }
    public double getBladeDurabilityBonus() { return this.bladeDurabilityBonus; }
    public double getHungerReduction() { return this.hungerReduction; }

    /** Иконка перка в виде спрайта зелья/эффекта (например, "Герой деревни"). Null — рисуем iconItem вместо неё. */
    @Nullable
    public net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> getEffectIcon() { return this.effectIcon; }

    /** Предмет-иконка, если у перка нет своего спрайта эффекта (getEffectIcon() == null). */
    public Item getIconItem() { return this.iconItem; }

    public String getNameKey() { return "gui.maxonaddon.perks." + this.id; }
    public String getDescKey() { return "gui.maxonaddon.perks." + this.id + ".desc"; }
    /** Подсказка "как получить" — показывается вместо описания, пока перк не разблокирован. */
    public String getUnlockHintKey() { return "gui.maxonaddon.perks." + this.id + ".unlock"; }

    @Nullable
    public static ModPerk byId(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        for (ModPerk perk : values()) {
            if (perk.id.equals(id)) return perk;
        }
        return null;
    }
}
