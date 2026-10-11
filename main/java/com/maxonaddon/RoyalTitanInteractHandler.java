package com.maxonaddon;

import com.maxonaddon.util.IPlayerStats;
import daot.BloodlineData;
import daot.BloodlineType;
import daot.DannysAot;
import daot.VillagerTransformTracker;
import daot.CrawlerTitanEntity;
import daot.CrawlerTitanNapeEntity;
import daot.CrawlerTitanEyeEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RoyalTitanInteractHandler {

    private static final Map<UUID, Long> zekeHealCooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 3000L * 1000L;

    public static final Map<UUID, Integer> sacrificingTitans = new ConcurrentHashMap<>();

    // === СИСТЕМА ИММУНИТЕТА К ПАДЕНИЮ ===
    public static final Map<UUID, Integer> fallImmunity = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> wasRidingTitan = new ConcurrentHashMap<>();

    public static void completeHealing(LivingEntity titan) {
        sacrificingTitans.remove(titan.getUUID());

        String ownerName = VillagerTransformTracker.getOwnerName(titan);
        if (ownerName != null) {
            ServerPlayer player = titan.level().getServer().getPlayerList().getPlayerByName(ownerName);
            if (player != null) {
                player.setHealth(player.getMaxHealth());
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 4, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 2, false, false, true));

                // Выдаем неуязвимость к падению на 3 секунды
                fallImmunity.put(player.getUUID(), 60);
                player.fallDistance = 0.0f;

                ((ServerLevel) titan.level()).sendParticles(
                        DannysAot.NAPE_STEAM_PARTICLE,
                        titan.getX(), titan.getY() + (titan.getBbHeight() / 2.0), titan.getZ(),
                        250, 3.0, 4.0, 3.0, 0.1
                );
                player.displayClientMessage(Component.translatable("message.maxonaddon.royal.titan_sacrificed").withStyle(ChatFormatting.GREEN), true);
            }
        }
        titan.kill(); // Убиваем титана
    }

    public static void register() {
        // Трекер езды и выдачи иммунитета
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();

                // Обновление таймера
                if (fallImmunity.containsKey(uuid)) {
                    int left = fallImmunity.get(uuid) - 1;
                    if (left <= 0) fallImmunity.remove(uuid);
                    else fallImmunity.put(uuid, left);
                }

                boolean isRidingTitan = player.getVehicle() != null && player.getVehicle().getClass().getName().contains("Titan");
                boolean wasRiding = wasRidingTitan.getOrDefault(uuid, false);

                // Если слезли или титан умер
                if (wasRiding && !isRidingTitan) {
                    fallImmunity.put(uuid, 60); // Даем 3 сек бессмертия от падения
                    player.fallDistance = 0.0f;
                }
                wasRidingTitan.put(uuid, isRidingTitan);
            }
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            // === БЛОКИРОВКА ЕЗДЫ НА ПОЛЗАЮЩЕМ ТИТАНЕ ===
            if (entity instanceof CrawlerTitanEntity || entity instanceof CrawlerTitanNapeEntity || entity instanceof CrawlerTitanEyeEntity) {
                return InteractionResult.PASS;
            }

            Monster targetTitan = null;
            if (entity instanceof Monster monster) {
                String name = monster.getClass().getName();
                if (name.startsWith("daot.") && !name.contains("Grab") && !name.contains("Shell") && !name.contains("Projectile") && !name.contains("Shifter")) {
                    if (name.contains("Eye") || name.contains("Nape")) {
                        try {
                            Method m = monster.getClass().getMethod("getParentTitan");
                            Object parent = m.invoke(monster);
                            if (parent instanceof Monster pMonster) targetTitan = pMonster;
                        } catch (Exception ignored) {}
                    } else {
                        targetTitan = monster;
                    }
                }
            }

            if (targetTitan != null) {
                if (world.isClientSide()) {
                    return InteractionResult.SUCCESS;
                }

                if (player instanceof ServerPlayer serverPlayer) {
                    BloodlineData data = BloodlineData.get((ServerLevel) world);

                    if (data.getBloodline(serverPlayer.getUUID()) == BloodlineType.ROYAL) {
                        String ownerName = VillagerTransformTracker.getOwnerName(targetTitan);

                        if (ownerName != null && ownerName.equals(serverPlayer.getGameProfile().getName())) {

                            IPlayerStats stats = (IPlayerStats) serverPlayer;

                            // Жертвоприношение (при зажатом Шифте)
                            if (serverPlayer.isShiftKeyDown()) {
                                if (!stats.hasSkill("royal_sacrifice_1")) {
                                    serverPlayer.displayClientMessage(Component.translatable("gui.maxonaddon.skills.need_sacrifice").withStyle(ChatFormatting.RED), true);
                                    return InteractionResult.SUCCESS;
                                }

                                if (serverPlayer.getHealth() <= serverPlayer.getMaxHealth() * 0.3f) {
                                    long currentTime = System.currentTimeMillis();
                                    long lastUsed = zekeHealCooldowns.getOrDefault(serverPlayer.getUUID(), 0L);
                                    long currentCooldown = stats.hasSkill("royal_sacrifice_2") ? (COOLDOWN_MS / 2) : COOLDOWN_MS;

                                    if (currentTime - lastUsed >= currentCooldown) {
                                        zekeHealCooldowns.put(serverPlayer.getUUID(), currentTime);
                                        serverPlayer.stopRiding();

                                        for (Method method : targetTitan.getClass().getMethods()) {
                                            if ((method.getName().equals("performEat") || method.getName().equals("startEat")) && method.getParameterCount() == 1) {
                                                try {
                                                    method.invoke(targetTitan, serverPlayer);
                                                    break;
                                                } catch (Exception ignored) {}
                                            }
                                        }
                                        sacrificingTitans.put(targetTitan.getUUID(), 0);
                                        serverPlayer.displayClientMessage(Component.translatable("message.maxonaddon.royal.sacrificing").withStyle(ChatFormatting.YELLOW), true);

                                    } else {
                                        long secondsLeft = (COOLDOWN_MS - (currentTime - lastUsed)) / 1000L;
                                        serverPlayer.displayClientMessage(Component.translatable("message.maxonaddon.royal.cooldown", secondsLeft).withStyle(ChatFormatting.RED), true);
                                    }
                                } else {
                                    serverPlayer.displayClientMessage(Component.translatable("message.maxonaddon.royal.health_full").withStyle(ChatFormatting.YELLOW), true);
                                }
                                return InteractionResult.SUCCESS;
                            }
                            // Обычная посадка на титана (без шифта)
                            else {
                                if (!targetTitan.hasPassenger(serverPlayer) && !sacrificingTitans.containsKey(targetTitan.getUUID())) {
                                    serverPlayer.startRiding(targetTitan, true);
                                    serverPlayer.displayClientMessage(Component.translatable("message.maxonaddon.royal.riding").withStyle(ChatFormatting.GREEN), true);
                                }
                                return InteractionResult.SUCCESS;
                            }
                        } else {
                            serverPlayer.displayClientMessage(Component.translatable("message.maxonaddon.royal.not_yours").withStyle(ChatFormatting.RED), true);
                            return InteractionResult.SUCCESS;
                        }
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }
}