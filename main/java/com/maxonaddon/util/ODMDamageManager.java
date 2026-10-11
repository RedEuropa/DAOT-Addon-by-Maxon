package com.maxonaddon.util;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.network.SilentJamPayload;
import daot.DannysAot;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Random;

public class ODMDamageManager {
    private static final String DURABILITY_KEY = "CustomDurability";
    private static final String MAX_DURABILITY_KEY = "CustomMaxDurability";
    private static final Random RANDOM = new Random();

    // Инициализация кастомной прочности при создании/выдаче (например, по умолчанию 500 прочности)
    public static void initDurability(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();

        if (!tag.contains(MAX_DURABILITY_KEY)) {
            tag.putInt(MAX_DURABILITY_KEY, 500);
            tag.putInt(DURABILITY_KEY, 500);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public static int getDurability(ItemStack stack) {
        initDurability(stack);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag().getInt(DURABILITY_KEY) : 500;
    }

    public static int getMaxDurability(ItemStack stack) {
        initDurability(stack);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag().getInt(MAX_DURABILITY_KEY) : 500;
    }

    public static void setDurability(ItemStack stack, int durability) {
        initDurability(stack);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();

        int max = tag.getInt(MAX_DURABILITY_KEY);
        int clamped = Math.max(0, Math.min(durability, max));
        tag.putInt(DURABILITY_KEY, clamped);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // Нанесение урона предмету
    public static void damageOdm(ServerPlayer player, ItemStack stack, int amount) {
        // Если геймрул doODMCracked выключен, УПМ не ломается
        if (!player.level().getGameRules().getBoolean(MaxonConfig.DO_ODM_CRACKED)) {
            return;
        }

        // isODMGear(...) — та же группировка, что использует сам daot (ODM_GEAR + ODM_APG),
        // поэтому у АПГ теперь тоже настоящая кастомная прочность, а не только у УПМ.
        if (!DannysAot.isODMGear(stack.getItem())) return;

        int current = getDurability(stack);
        if (current <= 0) return;

        int next = current - amount;
        setDurability(stack, next);

        if (next <= 0) {
            player.displayClientMessage(Component.translatable("message.maxonaddon.odm.broken"), true);
        }
    }

    // Серверный тик для проверки воды, дождя и случайных клинов (Jammed)
    public static void serverTick(ServerPlayer player) {
        // Проверяем геймрул перед выполнением проверок сырости и клинов
        if (!player.level().getGameRules().getBoolean(MaxonConfig.DO_ODM_CRACKED)) {
            return;
        }

        ItemStack legs = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        if (!DannysAot.isODMGear(legs.getItem())) return;

        initDurability(legs);
        int durability = getDurability(legs);

        // === 1. ПОЛНАЯ БЛОКИРОВКА ПОЛЕТА ПРИ 0 ПРОЧНОСТИ ===
        if (durability <= 0) {
            // Каждую секунду обновляем тихий клин на 2 секунды.
            // Это моментально сбрасывает крюки и не дает выстрелить ими снова.
            if (player.tickCount % 20 == 0) {
                ServerPlayNetworking.send(player, new SilentJamPayload(2000));
            }
            return; // Если УПМ уже сломан, проверки на сырость и рандомные клины пропускаем
        }

        // === 2. Проверка воды и дождя (износ от сырости) ===
        boolean isRainingOrInWater = player.isInWaterOrRain() || (player.level().isRaining() && player.level().canSeeSky(player.blockPosition()));
        if (isRainingOrInWater) {
            // Каждые 4 секунды (80 тиков) сырость отнимает 1 единицу прочности
            if (player.tickCount % 80 == 0) {
                damageOdm(player, legs, 1);
            }
        }

        // === 3. Случайные клины (Jammed), если прочность меньше половины ===
        int maxDurability = getMaxDurability(legs);
        if (durability > 0 && durability <= maxDurability / 2) {
            if (player.tickCount % 200 == 0 && RANDOM.nextFloat() < 0.4f) {
                int jamSeconds = 3 + RANDOM.nextInt(3); // от 3 до 5 секунд
                int durationMs = jamSeconds * 1000;

                player.displayClientMessage(Component.translatable("message.maxonaddon.odm.jammed"), true);
                ServerPlayNetworking.send(player, new SilentJamPayload(durationMs));
            }
        }
    }}

// Интерфейс-маркер или проверка для совместимости с модом
interface DMGearCheck {}