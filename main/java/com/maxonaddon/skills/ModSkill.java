package com.maxonaddon.skills;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public enum ModSkill {
    // === СОЛДАТСКИЕ НАВЫКИ ===
    ODM_MASTERY("odm_mastery", "gui.maxonaddon.skills.odm_mastery", 1, Category.SOLDIER, null, "ANY"),
    DASH("dash_mastery", "gui.maxonaddon.skills.dash", 1, Category.SOLDIER, ODM_MASTERY, "ANY"),
    CONNECTOR("connector_mastery", "gui.maxonaddon.skills.connector", 1, Category.SOLDIER, ODM_MASTERY, "ANY"),
    UNGRABING("ungrabing_mastery", "gui.maxonaddon.skills.ungrabing", 2, Category.SOLDIER, null, "ANY"),
    // Верховая езда с ОДМ: сначала право пользоваться тросами в седле, потом спрыг рывком.
    // Без первого второй бессмысленен — спрыг завязан на живой зацеп, поэтому он и потомок.
    RIDER_HOOKS("rider_hooks", "gui.maxonaddon.skills.rider_hooks", 2, Category.SOLDIER, ODM_MASTERY, "ANY"),
    RIDER_DISMOUNT("rider_dismount", "gui.maxonaddon.skills.rider_dismount", 3, Category.SOLDIER, RIDER_HOOKS, "ANY"),
    FAST_HOOK_1("fast_hook_1", "gui.maxonaddon.skills.fast_hook_1", 3, Category.SOLDIER, null, "ANY"),
    FAST_HOOK_2("fast_hook_2", "gui.maxonaddon.skills.fast_hook_2", 4, Category.SOLDIER, FAST_HOOK_1, "ANY"),
    AGILITY_1("agility_1", "gui.maxonaddon.skills.agility_1", 1, Category.SOLDIER, null, "ANY"),
    AGILITY_2("agility_2", "gui.maxonaddon.skills.agility_2", 1, Category.SOLDIER, AGILITY_1, "ANY"),
    IRON_BONES_1("iron_bones_1", "gui.maxonaddon.skills.iron_bones_1", 1, Category.SOLDIER, null, "ANY"),
    IRON_BONES_2("iron_bones_2", "gui.maxonaddon.skills.iron_bones_2", 1, Category.SOLDIER, IRON_BONES_1, "ANY"),
    FAST_REGEN_1("fast_regen_1", "gui.maxonaddon.skills.fast_regen_1", 1, Category.SOLDIER, null, "ANY"),
    FAST_REGEN_2("fast_regen_2", "gui.maxonaddon.skills.fast_regen_2", 1, Category.SOLDIER, FAST_REGEN_1, "ANY"),

    // === БОЕВЫЕ НАВЫКИ ПО ТИТАНАМ (эффект в TitanMeleeNapeMixin) ===
    // Урон по титанам, суммируется: +10% и +15% (вместе +25%)
    TITAN_MASTERY_1("titan_mastery_1", "gui.maxonaddon.skills.titan_mastery_1", 1, Category.SOLDIER, null, "ANY"),
    TITAN_MASTERY_2("titan_mastery_2", "gui.maxonaddon.skills.titan_mastery_2", 2, Category.SOLDIER, TITAN_MASTERY_1, "ANY"),
    // Шанс крита, суммируется: +5% и +10% (вместе +15%)
    STEADY_HAND_1("steady_hand_1", "gui.maxonaddon.skills.steady_hand_1", 1, Category.SOLDIER, null, "ANY"),
    STEADY_HAND_2("steady_hand_2", "gui.maxonaddon.skills.steady_hand_2", 2, Category.SOLDIER, STEADY_HAND_1, "ANY"),
    // Урон крита, суммируется: +5% и +10% (вместе +15%)
    EXECUTIONER_1("executioner_1", "gui.maxonaddon.skills.executioner_1", 1, Category.SOLDIER, null, "ANY"),
    EXECUTIONER_2("executioner_2", "gui.maxonaddon.skills.executioner_2", 2, Category.SOLDIER, EXECUTIONER_1, "ANY"),

    // === НАВЫКИ ТИТАНА ===
    SHIFTER_REGEN("shifter_regen", "gui.maxonaddon.skills.shifter_regen", 50, Category.TITAN, null, "ANY"),

    // Снижает урон по затылку своего титана: -25% и -40% (см. SteelNapeMixin).
    // Заменил "Титаническую волю": та упиралась в запрет превращения на стороне dannys-aot.
    STEEL_NAPE_1("steel_nape_1", "gui.maxonaddon.skills.steel_nape_1", 20, Category.TITAN, null, "ANY"),
    STEEL_NAPE_2("steel_nape_2", "gui.maxonaddon.skills.steel_nape_2", 30, Category.TITAN, STEEL_NAPE_1, "ANY"),
    // Увеличивает максимум шкалы стамины титан-шифтера (см. TitanEnduranceMixin).
    TITAN_ENDURANCE("titan_endurance", "gui.maxonaddon.skills.titan_endurance", 15, Category.TITAN, null, "ANY"),
    // Авто-исцеление на пол-сердечка ценой сломанных конечностей (см. WillToLiveHandler).
    WILL_TO_LIVE_1("will_to_live_1", "gui.maxonaddon.skills.will_to_live_1", 25, Category.TITAN, null, "ANY"),
    WILL_TO_LIVE_2("will_to_live_2", "gui.maxonaddon.skills.will_to_live_2", 35, Category.TITAN, WILL_TO_LIVE_1, "ANY"),

    // === НАВЫКИ РОДОСЛОВНОЙ (ОБЩИЕ) ===
    BLOODLINE_AWAKENING("bloodline_awakening", "gui.maxonaddon.skills.learned_awakening", 1, Category.BLOODLINE, null, "ANY"),

    // === КОРОЛЕВСКАЯ КРОВЬ ===
    ROYAL_CONTROL_1("royal_control_1", "gui.maxonaddon.skills.royal_control_1", 1, Category.BLOODLINE, null, "ROYAL"),
    ROYAL_CONTROL_2("royal_control_2", "gui.maxonaddon.skills.royal_control_2", 1, Category.BLOODLINE, ROYAL_CONTROL_1, "ROYAL"),
    ROYAL_SACRIFICE_1("royal_sacrifice_1", "gui.maxonaddon.skills.royal_sacrifice_1", 1, Category.BLOODLINE, null, "ROYAL"),
    ROYAL_SACRIFICE_2("royal_sacrifice_2", "gui.maxonaddon.skills.royal_sacrifice_2", 1, Category.BLOODLINE, ROYAL_SACRIFICE_1, "ROYAL"),

    // === АККЕРМАНЫ ===
    ACKERMAN_CONTROL_1("ackerman_control_1", "gui.maxonaddon.skills.ackerman_control_1", 1, Category.BLOODLINE, null, "ACKERMAN"),
    ACKERMAN_CONTROL_2("ackerman_control_2", "gui.maxonaddon.skills.ackerman_control_2", 1, Category.BLOODLINE, ACKERMAN_CONTROL_1, "ACKERMAN"),
    ACKERMAN_ENDURANCE_1("ackerman_endurance_1", "gui.maxonaddon.skills.ackerman_endurance_1", 1, Category.BLOODLINE, null, "ACKERMAN"),
    ACKERMAN_ENDURANCE_2("ackerman_endurance_2", "gui.maxonaddon.skills.ackerman_endurance_2", 1, Category.BLOODLINE, ACKERMAN_ENDURANCE_1, "ACKERMAN"),
    // Во время пробуждения -25% к расходу прочности клинков (см. AckermanBladedanceMixin).
    BLADEDANCE("bladedance", "gui.maxonaddon.skills.bladedance", 2, Category.BLOODLINE, null, "ACKERMAN");

    public final String id;
    public final String translationKey;
    public final int cost;
    public final Category category;
    @Nullable public final ModSkill parent;
    public final String bloodlineReq; // <-- НОВЫЙ ФИЛЬТР

    ModSkill(String id, String translationKey, int cost, Category category, @Nullable ModSkill parent, String bloodlineReq) {
        this.id = id;
        this.translationKey = translationKey;
        this.cost = cost;
        this.category = category;
        this.parent = parent;
        this.bloodlineReq = bloodlineReq;
    }

    public enum Category { SOLDIER, TITAN, BLOODLINE }

    public static List<ModSkill> getByCategory(Category category) {
        List<ModSkill> list = new ArrayList<>();
        for (ModSkill skill : values()) {
            if (skill.category == category) list.add(skill);
        }
        return list;
    }

    public static ModSkill getById(String id) {
        for (ModSkill skill : values()) {
            if (skill.id.equals(id)) return skill;
        }
        return null;
    }

    // ===============================================
    // СКИЛЫ, ВЫДАВАЕМЫЕ АВТОМАТИЧЕСКИ ПРИ MaxonConfig.skillsSystemEnabled = false.
    // Сюда входят только скилы-способности (не пассивные бонусы к статам) —
    // те, что дают прирост стата (agility_1/2, iron_bones_1/2, fast_regen_1/2,
    // ackerman_endurance_1/2, а также "вторые" улучшающие тиры royal_control_2,
    // royal_sacrifice_2, ackerman_control_2) сюда НЕ входят и остаются платными.
    // ===============================================
    private static final Set<String> AUTO_GRANT_IDS = Set.of(
            "odm_mastery",
            "dash_mastery",
            "connector_mastery",
            "ungrabing_mastery",
            "shifter_regen",
            "bloodline_awakening",
            "royal_control_1",
            "royal_sacrifice_1",
            "ackerman_control_1"
    );

    public boolean isAutoGrantedWhenSkillsDisabled() {
        return AUTO_GRANT_IDS.contains(this.id);
    }

    // ===============================================
    // СКИЛЫ, ЗАВИСЯЩИЕ ОТ УЛУЧШЕННОЙ МЕХАНИКИ КРЮКА.
    // Ускорение крюка работает через нашу формулу времени полёта (см. HookSpeedHelper),
    // а она включается только при enableAdvancedHookMechanics. С выключенным конфигом
    // покупать такой навык было бы обманом — очки списались бы, а эффекта не было.
    // ===============================================
    private static final Set<String> ADVANCED_HOOK_IDS = Set.of(
            "fast_hook_1",
            "fast_hook_2"
    );

    /**
     * Показывать ли навык в дереве и разрешать ли покупку при текущем конфиге.
     * Уже купленный навык из сохранения не стирается — он просто перестаёт отображаться
     * и действовать, пока механика выключена, и вернётся, если её включить обратно.
     */
    public boolean isAvailable() {
        if (ADVANCED_HOOK_IDS.contains(this.id)) {
            return com.maxonaddon.MaxonConfig.get().enableAdvancedHookMechanics;
        }
        return true;
    }
}