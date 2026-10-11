package com.maxonaddon;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ApgConfig {

    // Регистрация геймрула: /gamerule doCustomApg true/false
    public static GameRules.Key<GameRules.BooleanValue> DO_CUSTOM_APG;

    // Хранение данных для механики "двойного выстрела" в шею
    public static final Map<UUID, Long> lastApgHitTime = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> apgHitCount = new ConcurrentHashMap<>();

    public static void register() {
        DO_CUSTOM_APG = GameRuleRegistry.register(
                "doCustomApg",
                GameRules.Category.PLAYER,
                GameRuleFactory.createBooleanRule(true) // Включено по умолчанию
        );
    }
}