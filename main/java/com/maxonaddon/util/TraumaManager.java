package com.maxonaddon.util;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.network.SyncTraumaPayload;
import com.maxonaddon.network.TraumaAnimationPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TraumaManager {

    private static final ResourceLocation BODY_TRAUMA_ID = MaxonDaotAddon.id("body_trauma");
    private static final ResourceLocation LEGS_TRAUMA_ID = MaxonDaotAddon.id("legs_trauma");
    private static final ResourceLocation LEGS_TRAUMA_SPEED_ID = MaxonDaotAddon.id("legs_trauma_speed");
    private static final ResourceLocation LEGS_TRAUMA_JUMP_ID = MaxonDaotAddon.id("legs_trauma_jump");

    // Храним, сколько тиков игрок находится БЕЗ стелса подряд
    private static final Map<UUID, Integer> stealthOffTicks = new ConcurrentHashMap<>();
    // Храним, какие именно травмы уже "зафиксировались" для регенерации шифтера
    private static final Map<UUID, Set<TraumaType>> lockedInTraumas = new ConcurrentHashMap<>();

    private static Set<TraumaType> getLockedTraumas(UUID uuid) {
        return lockedInTraumas.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public static void applyTrauma(ServerPlayer player, TraumaType type, int durationTicks) {
        IPlayerStats stats = (IPlayerStats) player;
        stats.setTraumaTimer(type, durationTicks);

        switch (type) {
            case BODY:
                applyBodyDebuff(player);
                break;
            case ARMS:
                applyArmsDebuff(player);
                break;
            case LEGS:
                applyLegsDebuff(player);
                break;
            case HEAD:
                break;
        }
    }

    public static void syncTrauma(ServerPlayer player, TraumaType type) {
        IPlayerStats stats = (IPlayerStats) player;
        ServerPlayNetworking.send(player, new SyncTraumaPayload(type.name(), stats.getTraumaTimer(type)));
    }

    private static void applyBodyDebuff(ServerPlayer player) {
        AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null && healthAttr.getModifier(BODY_TRAUMA_ID) == null) {
            healthAttr.addPermanentModifier(new AttributeModifier(
                    BODY_TRAUMA_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    public static void applyMonsterTrauma(ServerPlayer player, TraumaType type) {
        if (!player.level().getGameRules().getBoolean(MaxonConfig.ALLOW_INJURES)) {
            return;
        }

        IPlayerStats stats = (IPlayerStats) player;

        // Выдаем изначально долгое время (как обычному человеку).
        // Если игрок уже 3 секунды вне стелса, tickTraumas срежет это время
        // до шифтерского буквально в следующий тик.
        int seconds = switch (type) {
            case HEAD -> MaxonConfig.get().headTraumaDurationSeconds;
            case BODY -> MaxonConfig.get().bodyTraumaDurationSeconds;
            case ARMS -> MaxonConfig.get().armsTraumaDurationSeconds;
            case LEGS -> MaxonConfig.get().legsTraumaDurationSeconds;
        };

        double reduction = 0.0;
        if (stats.hasSkill("fast_regen_1")) {
            reduction += 0.15;
        }
        if (stats.hasSkill("fast_regen_2")) {
            reduction += 0.25;
        }

        double durationMultiplier = 1.0 - reduction;
        int durationTicks = (int) (seconds * 20 * durationMultiplier);

        applyTrauma(player, type, durationTicks);
        syncTrauma(player, type);

        player.displayClientMessage(
                Component.translatable("message.maxonaddon.trauma.broken", type.name()),
                true
        );
    }

    public static void applyRandomTrauma(ServerPlayer player) {
        if (!player.level().getGameRules().getBoolean(MaxonConfig.ALLOW_INJURES)) {
            return;
        }

        MaxonConfig config = MaxonConfig.get();
        double totalWeight = config.headTraumaChance + config.bodyTraumaChance + config.armsTraumaChance + config.legsTraumaChance;
        if (totalWeight <= 0) return;

        double randomValue = player.getRandom().nextDouble() * totalWeight;
        TraumaType chosenType = TraumaType.LEGS;

        if (randomValue < config.headTraumaChance) {
            chosenType = TraumaType.HEAD;
        } else if (randomValue < config.headTraumaChance + config.bodyTraumaChance) {
            chosenType = TraumaType.BODY;
        } else if (randomValue < config.headTraumaChance + config.bodyTraumaChance + config.armsTraumaChance) {
            chosenType = TraumaType.ARMS;
        }

        applyMonsterTrauma(player, chosenType);
    }

    private static void applyArmsDebuff(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            player.drop(offhand.copy(), true, false);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static void applyLegsDebuff(ServerPlayer player) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(LEGS_TRAUMA_SPEED_ID) == null) {
            speedAttr.addPermanentModifier(new AttributeModifier(
                    LEGS_TRAUMA_SPEED_ID, -0.7, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }

        AttributeInstance jumpAttr = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jumpAttr != null && jumpAttr.getModifier(LEGS_TRAUMA_JUMP_ID) == null) {
            jumpAttr.addPermanentModifier(new AttributeModifier(
                    LEGS_TRAUMA_JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
    }

    public static void tickTraumas(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        UUID uuid = player.getUUID();

        boolean isShifter = stats.hasShifterRegen();
        boolean isStealthOn = player.getTags().contains("titan_stealth");

        // Обновляем счетчик времени без стелса
        if (isStealthOn) {
            stealthOffTicks.put(uuid, 0); // Сброс таймера выхода, если вошел в стелс
        } else {
            stealthOffTicks.put(uuid, stealthOffTicks.getOrDefault(uuid, 0) + 1);
        }

        int ticksOut = stealthOffTicks.getOrDefault(uuid, 0);
        Set<TraumaType> lockedSet = getLockedTraumas(uuid);

        boolean anySteam = false;

        // Вычисляем макс. время для шифтера с учетом его прокачки fast_regen
        double reduction = 0.0;
        if (stats.hasSkill("fast_regen_1")) reduction += 0.15;
        if (stats.hasSkill("fast_regen_2")) reduction += 0.25;
        int shifterTicksMax = (int) (MaxonConfig.get().shifterTraumaDurationSeconds * 20 * (1.0 - reduction));

        if (stats.hasTrauma(TraumaType.HEAD) || stats.hasTrauma(TraumaType.BODY)) {
            if (player.getRandom().nextInt(300) == 0) {
                player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
                String animToPlay = stats.hasTrauma(TraumaType.HEAD) ? "injure_head" : "injure_body";
                ServerPlayNetworking.send(player, new TraumaAnimationPayload(animToPlay));
            }
        }

        for (TraumaType type : TraumaType.values()) {
            int currentTimer = stats.getTraumaTimer(type);

            if (currentTimer > 0) {
                // Если мы без стелса 3 секунды (60 тиков), фиксируем эту конкретную травму навсегда
                if (ticksOut >= 60) {
                    lockedSet.add(type);
                }

                // Лечение активно, если игрок шифтер И эта конкретная травма зафиксирована
                boolean activeShifterRegen = isShifter && lockedSet.contains(type);

                if (activeShifterRegen) {
                    anySteam = true;
                    // Если таймер долгий человеческий, мгновенно срезаем до шифтерского
                    if (currentTimer > shifterTicksMax) {
                        currentTimer = shifterTicksMax;
                        stats.setTraumaTimer(type, currentTimer);
                    }
                }

                // Таймер идет вниз всегда
                currentTimer--;
                stats.setTraumaTimer(type, currentTimer);

                if (currentTimer <= 0) {
                    healTrauma(player, type);
                    syncTrauma(player, type);
                } else if (currentTimer % 20 == 0) {
                    syncTrauma(player, type);
                }
            }
        }

        // === ПАР И ЗВУК ДЛЯ ШИФТЕРА ПРИ РЕГЕНЕРАЦИИ ТРАВМЫ ===
        if (anySteam) {
            net.minecraft.server.level.ServerLevel serverLevel = player.serverLevel();
            int tickCount = serverLevel.getServer().getTickCount();

            if (tickCount % 4 == 0) {
                serverLevel.sendParticles(
                        daot.DannysAot.NAPE_STEAM_PARTICLE,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        2,
                        0.2, 0.4, 0.2,
                        0.02
                );
            }

            if (tickCount % 20 == 0) {
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                        daot.ModSounds.STEAM_POOF,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        0.05F, 1.8F);
            }
        }
    }

    public static void reapplyTraumas(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;

        if (stats.hasTrauma(TraumaType.BODY)) applyBodyDebuff(player);
        if (stats.hasTrauma(TraumaType.LEGS)) applyLegsDebuff(player);
    }

    public static void healTrauma(ServerPlayer player, TraumaType type) {
        IPlayerStats stats = (IPlayerStats) player;
        stats.setTraumaTimer(type, 0);

        // ВАЖНО: Удаляем травму из зафиксированных при излечении
        getLockedTraumas(player.getUUID()).remove(type);

        if (type == TraumaType.BODY) {
            AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) healthAttr.removeModifier(BODY_TRAUMA_ID);

        } else if (type == TraumaType.LEGS) {
            AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.removeModifier(LEGS_TRAUMA_SPEED_ID);

            AttributeInstance jumpAttr = player.getAttribute(Attributes.JUMP_STRENGTH);
            if (jumpAttr != null) jumpAttr.removeModifier(LEGS_TRAUMA_JUMP_ID);
        }
    }
}