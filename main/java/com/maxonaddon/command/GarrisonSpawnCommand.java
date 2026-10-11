package com.maxonaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import daot.BladeItem;
import daot.ODMGearItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GarrisonSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawngarrison")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    BlockPos pos = BlockPos.containing(context.getSource().getPosition());
                    spawnBraveGarrison(level, pos);
                    return 1;
                }));
    }

    public static void spawnBraveGarrison(ServerLevel level, BlockPos pos) {
        Item uniform = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "garrison_uniform"));
        Item gear = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "odm_gear"));
        Item boots = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "odm_boots"));
        Item blade = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "blade"));

        // Заправляем УПМ
        ItemStack stackGear = new ItemStack(gear != Items.AIR ? gear : Items.LEATHER_LEGGINGS);
        if (gear instanceof ODMGearItem) {
            ODMGearItem.setGas(stackGear, 9999);
        }

        // Храбрый гарнизонец в команде получает заряженные лезвия, чтобы не был "пустышкой"
        ItemStack stackBladeMain = new ItemStack(blade != Items.AIR ? blade : Items.IRON_SWORD);
        ItemStack stackBladeOff = new ItemStack(blade != Items.AIR ? blade : Items.IRON_SWORD);
        if (blade instanceof BladeItem) {
            BladeItem.setBladeState(stackBladeMain, BladeItem.BladeState.FRESH);
            BladeItem.setBladeState(stackBladeOff, BladeItem.BladeState.FRESH);
        }

        Villager soldier = EntityType.VILLAGER.create(level);
        if (soldier != null) {
            soldier.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            // === ЖЕСТКО ВЫДАЕМ ТЕГИ ХРАБРОГО ГАРНИЗОНА ===
            soldier.addTag("garrison_soldier");
            soldier.addTag("brave_garrison");

            // Чтобы он не пытался с нами торговать
            soldier.setVillagerData(soldier.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NITWIT));

            // Надеваем экипировку (без капюшона!)
            soldier.setItemSlot(EquipmentSlot.CHEST, new ItemStack(uniform != Items.AIR ? uniform : Items.LEATHER_CHESTPLATE));
            soldier.setItemSlot(EquipmentSlot.LEGS, stackGear);
            soldier.setItemSlot(EquipmentSlot.FEET, new ItemStack(boots != Items.AIR ? boots : Items.LEATHER_BOOTS));
            soldier.setItemSlot(EquipmentSlot.MAINHAND, stackBladeMain);
            soldier.setItemSlot(EquipmentSlot.OFFHAND, stackBladeOff);

            // Отключаем дроп вещей
            soldier.setDropChance(EquipmentSlot.CHEST, 0.0f);
            soldier.setDropChance(EquipmentSlot.LEGS, 0.0f);
            soldier.setDropChance(EquipmentSlot.FEET, 0.0f);
            soldier.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            soldier.setDropChance(EquipmentSlot.OFFHAND, 0.0f);

            level.addFreshEntity(soldier);
        }
    }
}