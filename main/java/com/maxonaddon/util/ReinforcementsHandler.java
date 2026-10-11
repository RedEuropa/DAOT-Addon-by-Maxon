package com.maxonaddon.util;

import com.maxonaddon.MaxonConfig;
import daot.BladeItem;
import daot.FlareProjectileEntity;
import daot.ODMGearItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ReinforcementsHandler {

    // Хранит время последнего вызова отряда в миллисекундах для каждого игрока
    private static final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    public static void callScouts(ServerPlayer player, ServerLevel level) {
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = MaxonConfig.get().scoutReinforcementCooldown * 1000L;
        long lastUsed = playerCooldowns.getOrDefault(player.getUUID(), 0L);

        // Проверяем, прошел ли кулдаун
        if (currentTime - lastUsed < cooldownMillis) {
            long remainingSec = (cooldownMillis - (currentTime - lastUsed)) / 1000L;

            // Отправляем мультиязычное сообщение
            player.displayClientMessage(Component.translatable("maxonaddon.message.scout_cooldown", remainingSec), true);
            return; // Отменяем спавн отряда
        }

        // Обновляем время вызова
        playerCooldowns.put(player.getUUID(), currentTime);

        float yaw = player.getYRot();
        double rad = Math.toRadians(yaw);

        double distance = 20.0;
        double offsetX = Math.sin(rad) * distance;
        double offsetZ = -Math.cos(rad) * distance;

        BlockPos spawnPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(player.getX() + offsetX, player.getY(), player.getZ() + offsetZ));

        fireBlueFlare(level, spawnPos);

        Item cloak = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "green_scout_cloak"));
        Item uniform = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "scout_uniform"));
        Item gear = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "odm_gear"));
        Item boots = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "odm_boots"));
        Item blade = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "blade"));

        ItemStack stackGear = new ItemStack(gear != Items.AIR ? gear : Items.LEATHER_LEGGINGS);
        if (gear instanceof ODMGearItem) ODMGearItem.setGas(stackGear, 9999);

        ItemStack stackBladeMain = new ItemStack(blade != Items.AIR ? blade : Items.IRON_SWORD);
        ItemStack stackBladeOff = new ItemStack(blade != Items.AIR ? blade : Items.IRON_SWORD);
        if (blade instanceof BladeItem) {
            BladeItem.setBladeState(stackBladeMain, BladeItem.BladeState.FRESH);
            BladeItem.setBladeState(stackBladeOff, BladeItem.BladeState.FRESH);
        }

        // === СПАВНИМ 3 РАЗВЕДЧИКОВ ДЛЯ БАЛАНСА ===
        int groupSize = MaxonConfig.get().squadGroupSize;
        for (int i = 0; i < groupSize; i++) {
            BlockPos offsetPos = spawnPos.offset((int)(Math.random() * 8 - 4), 0, (int)(Math.random() * 8 - 4));
            BlockPos finalPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, offsetPos);

            Horse horse = EntityType.HORSE.create(level);
            Villager scout = EntityType.VILLAGER.create(level);

            if (horse != null && scout != null) {
                horse.setPos(finalPos.getX() + 0.5, finalPos.getY(), finalPos.getZ() + 0.5);
                horse.setTamed(true);
                level.addFreshEntity(horse);

                scout.setPos(finalPos.getX() + 0.5, finalPos.getY(), finalPos.getZ() + 0.5);
                scout.addTag("scout_legion");
                scout.setVillagerData(scout.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NITWIT));

                scout.setItemSlot(EquipmentSlot.HEAD, new ItemStack(cloak != Items.AIR ? cloak : Items.LEATHER_HELMET));
                scout.setItemSlot(EquipmentSlot.CHEST, new ItemStack(uniform != Items.AIR ? uniform : Items.LEATHER_CHESTPLATE));
                scout.setItemSlot(EquipmentSlot.LEGS, stackGear.copy());
                scout.setItemSlot(EquipmentSlot.FEET, new ItemStack(boots != Items.AIR ? boots : Items.LEATHER_BOOTS));
                scout.setItemSlot(EquipmentSlot.MAINHAND, stackBladeMain.copy());
                scout.setItemSlot(EquipmentSlot.OFFHAND, stackBladeOff.copy());

                level.addFreshEntity(scout);
                scout.startRiding(horse);

                double targetX = player.getX() + (Math.random() * 10 - 5);
                double targetZ = player.getZ() + (Math.random() * 10 - 5);
                horse.getNavigation().moveTo(targetX, player.getY(), targetZ, 1.8);
            }
        }
    }

    private static void fireBlueFlare(ServerLevel level, BlockPos pos) {
        EntityType<?> flareType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "flare_projectile"));
        if (flareType != null && flareType != EntityType.PIG) {
            FlareProjectileEntity flare = new FlareProjectileEntity((EntityType<? extends net.minecraft.world.entity.projectile.AbstractArrow>) flareType, level);
            flare.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            flare.setFlareColorOrdinal(3);
            flare.shoot(0, 1.0, 0, 2.5F, 1.0F);
            level.addFreshEntity(flare);
        }
    }
}