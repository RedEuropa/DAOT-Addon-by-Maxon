package com.maxonaddon.util;

import com.maxonaddon.MaxonConfig;
import daot.BladeItem;
import daot.DannysAot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Экипировка "чистого" полицейского, ловящего контрабандистов: униформа + ботинки УПМ,
 * БЕЗ самого УПМ (не летает), клинок в правой руке. Используется и автоспавном
 * (VillagerEntityMixin), и командой /spawnpolice — единая точка правды.
 */
public final class PoliceGearHelper {

    private PoliceGearHelper() {}

    public static void equipAsPolice(Villager v) {
        v.addTag("police_hunter");
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NITWIT));

        // Скорость бега — как у игрока, а не завышенный множитель поверх стандартного
        // жителя (см. PoliceHuntSmugglerGoal/PoliceDefendVillagersGoal — там speedModifier = 1.0).
        var speedAttr = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(MaxonConfig.get().policeChaseSpeed);
        }

        Item uniform = DannysAot.MILITARY_POLICE_UNIFORM;
        Item boots = DannysAot.ODM_BOOTS;

        // Полиция вооружена марлийской винтовкой вместо клинков. Вне боя она в ЛЕВОЙ руке
        // (offhand) — как оружие «на плече»; в бой её перекладывает в правую сам
        // PoliceRifleAttackGoal, он же считает патроны и перезарядку.
        ItemStack rifle = new ItemStack(com.maxonaddon.MaxonDaotAddon.MARLEYAN_RIFLE_ITEM);
        com.maxonaddon.item.MarleyanRifleItem.setLoadedRounds(rifle, com.maxonaddon.item.MarleyanRifleItem.MAX_LOADED_ROUNDS);

        v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(uniform != Items.AIR ? uniform : Items.LEATHER_CHESTPLATE));
        v.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY); // без УПМ — не летает
        v.setItemSlot(EquipmentSlot.FEET, new ItemStack(boots != Items.AIR ? boots : Items.LEATHER_BOOTS));
        v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        v.setItemSlot(EquipmentSlot.OFFHAND, rifle);

        v.setDropChance(EquipmentSlot.CHEST, 0.0F);
        v.setDropChance(EquipmentSlot.FEET, 0.0F);
        v.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        v.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
    }
}
