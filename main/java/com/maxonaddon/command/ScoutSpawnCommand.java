package com.maxonaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import daot.BladeItem;
import daot.FlareCartridgeItem;
import daot.FlareProjectileEntity;
import daot.ODMGearItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ScoutSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnscouts")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    BlockPos pos = BlockPos.containing(context.getSource().getPosition());
                    spawnScoutSquad(level, pos);
                    return 1;
                }));
    }

    public static void spawnScoutSquad(ServerLevel level, BlockPos pos) {
        boolean hasAbnormal = false;
        List<Entity> nearby = level.getEntities(null, new net.minecraft.world.phys.AABB(pos).inflate(100));
        for (Entity e : nearby) {
            if (e.getClass().getSimpleName().toLowerCase().contains("abnormal")) {
                hasAbnormal = true;
                break;
            }
        }

        // Запускаем сигнальную ракету
        fireSignalFlare(level, pos, hasAbnormal);

        // Точные названия предметов из мода
        Item cloak = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "green_scout_cloak"));
        Item uniform = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "scout_uniform"));
        Item gear = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "odm_gear"));
        Item boots = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "odm_boots"));
        Item blade = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "blade"));

        // Создаем стаки брони
        ItemStack stackCloak = new ItemStack(cloak != Items.AIR ? cloak : Items.LEATHER_HELMET);
        ItemStack stackUniform = new ItemStack(uniform != Items.AIR ? uniform : Items.LEATHER_CHESTPLATE);
        ItemStack stackBoots = new ItemStack(boots != Items.AIR ? boots : Items.LEATHER_BOOTS);

        // Создаем и заправляем УПМ газом
        ItemStack stackGear = new ItemStack(gear != Items.AIR ? gear : Items.LEATHER_LEGGINGS);
        if (gear instanceof ODMGearItem) {
            ODMGearItem.setGas(stackGear, 9999); // Бесконечный/максимальный газ
        }

        // Создаем и заряжаем лезвия
        ItemStack stackBladeMain = new ItemStack(blade != Items.AIR ? blade : Items.IRON_SWORD);
        ItemStack stackBladeOff = new ItemStack(blade != Items.AIR ? blade : Items.IRON_SWORD);
        if (blade instanceof BladeItem) {
            BladeItem.setBladeState(stackBladeMain, BladeItem.BladeState.FRESH);
            BladeItem.setBladeState(stackBladeOff, BladeItem.BladeState.FRESH);
        }

        for (int i = 0; i < 5; i++) {
            Villager scout = EntityType.VILLAGER.create(level);
            if (scout != null) {
                scout.setPos(pos.getX() + (Math.random() * 6 - 3), pos.getY(), pos.getZ() + (Math.random() * 6 - 3));

                // Главный тег: без него ИИ не заработает
                scout.addTag("scout_legion");
                scout.setVillagerData(scout.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NITWIT));

                // Надеваем экипировку
                scout.setItemSlot(EquipmentSlot.HEAD, stackCloak.copy());
                scout.setItemSlot(EquipmentSlot.CHEST, stackUniform.copy());
                scout.setItemSlot(EquipmentSlot.LEGS, stackGear.copy());
                scout.setItemSlot(EquipmentSlot.FEET, stackBoots.copy());
                scout.setItemSlot(EquipmentSlot.MAINHAND, stackBladeMain.copy());
                scout.setItemSlot(EquipmentSlot.OFFHAND, stackBladeOff.copy());

                // Запрещаем дропать эти вещи после смерти (по желанию)
                scout.setDropChance(EquipmentSlot.HEAD, 0.0f);
                scout.setDropChance(EquipmentSlot.CHEST, 0.0f);
                scout.setDropChance(EquipmentSlot.LEGS, 0.0f);
                scout.setDropChance(EquipmentSlot.FEET, 0.0f);
                scout.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
                scout.setDropChance(EquipmentSlot.OFFHAND, 0.0f);

                level.addFreshEntity(scout);
            }
        }
    }

    private static void fireSignalFlare(ServerLevel level, BlockPos pos, boolean isAbnormal) {
        EntityType<?> flareType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "flare_projectile"));
        if (flareType != null && flareType != EntityType.PIG) {
            FlareProjectileEntity flare = new FlareProjectileEntity((EntityType<? extends net.minecraft.world.entity.projectile.AbstractArrow>) flareType, level);
            flare.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            FlareCartridgeItem.FlareColor color = isAbnormal ? FlareCartridgeItem.FlareColor.BLACK : FlareCartridgeItem.FlareColor.RED;
            flare.setFlareColorOrdinal(color.ordinal());
            Vec3 shootVec = new Vec3(0, 1.0, 0);
            flare.shoot(shootVec.x, shootVec.y, shootVec.z, 2.5F, 1.0F);
            level.addFreshEntity(flare);
        }
    }
}