package com.maxonaddon;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;

public class BerserkConfig {

    public static GameRules.Key<GameRules.BooleanValue> VILLAGER_SHIFTER_TRANSFORM;
    public static GameRules.Key<GameRules.BooleanValue> DO_SHIFTER_BERSERK;
    public static GameRules.Key<GameRules.BooleanValue> BERSERK_TITAN_BAR;

    public static void register() {
        DO_SHIFTER_BERSERK = GameRuleRegistry.register("doShifterBerserk", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));
        BERSERK_TITAN_BAR = GameRuleRegistry.register("BerserkTitanBar", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));

        VILLAGER_SHIFTER_TRANSFORM = GameRuleRegistry.register("villagerShifterTransform", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));

        // Загружаем общий JSON-конфиг, если он еще не был загружен
        MaxonConfig.load();
    }

    // === УТИЛИТЫ ДЛЯ ПОЛУЧЕНИЯ ДАННЫХ ИЗ ЕДИНОГО КОНФИГА ===
    public static double getChanceForTitan(String titanName) {
        if (titanName.contains("AttackTitan")) return MaxonConfig.get().attackChance;
        if (titanName.contains("ArmoredTitan")) return MaxonConfig.get().armoredChance;
        if (titanName.contains("FemaleTitan")) return MaxonConfig.get().femaleChance;
        if (titanName.contains("BeastTitan")) return MaxonConfig.get().beastChance;
        if (titanName.contains("ColossalTitan")) return MaxonConfig.get().colossalChance;
        if (titanName.contains("WarhammerTitan")) return MaxonConfig.get().warhammerChance;
        return 100.0;
    }

    public static double getHealthForTitan(String titanName) {
        if (titanName.contains("AttackTitan")) return MaxonConfig.get().attackHealth;
        if (titanName.contains("ArmoredTitan")) return MaxonConfig.get().armoredHealth;
        if (titanName.contains("FemaleTitan")) return MaxonConfig.get().femaleHealth;
        if (titanName.contains("BeastTitan")) return MaxonConfig.get().beastHealth;
        if (titanName.contains("ColossalTitan")) return MaxonConfig.get().colossalHealth;
        if (titanName.contains("WarhammerTitan")) return MaxonConfig.get().warhammerHealth;
        return 600.0;
    }
}