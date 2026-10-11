package com.maxonaddon.mixin;

import com.maxonaddon.util.ODMDamageManager;
import com.maxonaddon.util.OdmGasHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {

    // Ссылки на ванильные переменные наковальни
    @Shadow public int repairItemCountCost;
    @Shadow private String itemName;
    @Shadow @org.spongepowered.asm.mixin.Final public net.minecraft.world.inventory.DataSlot cost;

    // Внедряемся в самый конец метода создания результата
    @Inject(method = "createResult", at = @At("RETURN"))
    private void maxon$onAnvilUpdate(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack left = menu.getSlot(0).getItem();
        ItemStack right = menu.getSlot(1).getItem();

        // Если слева пусто или лежит не УПМ/АПГ — ничего не делаем
        if (left.isEmpty() || !daot.DannysAot.isODMGear(left.getItem())) {
            return;
        }

        // === УПМ + УПМ: собираем из двух комплектов один ===
        // Прочность складывается и сверху идёт бонус 50 за саму сборку, газ из обоих баков
        // сливается в один. И то, и другое режется по потолку: прочность — по CustomMaxDurability
        // предмета, газ — по лимиту своего типа (у АПГ он вдвое больше обычного УПМ).
        // Требуем именно одинаковый предмет: чинить АПГ обычным УПМ, как и в ванильной наковальне,
        // нельзя — иначе результат унаследовал бы чужой потолок газа.
        if (daot.DannysAot.isODMGear(right.getItem()) && right.getItem() == left.getItem()) {
            int maxDur = ODMDamageManager.getMaxDurability(left);
            int newDur = Math.min(maxDur,
                    ODMDamageManager.getDurability(left) + ODMDamageManager.getDurability(right) + 50);
            int newGas = OdmGasHelper.getGas(left) + OdmGasHelper.getGas(right);

            ItemStack merged = left.copy();
            merged.setCount(1);
            ODMDamageManager.initDurability(merged);
            ODMDamageManager.setDurability(merged, newDur);
            OdmGasHelper.setGas(merged, newGas);

            menu.getSlot(2).set(merged);
            this.cost.set(maxon$applyRename(merged, left, 2));
            this.repairItemCountCost = 1;
            return;
        }

        // Проверяем, что справа лежит нужный слиток (учитываем возможные опечатки в id)
        ResourceLocation ingotId = ResourceLocation.fromNamespaceAndPath("dannys-aot", "ultrahard_steal_ingot");
        ResourceLocation ingotIdAlt = ResourceLocation.fromNamespaceAndPath("dannys-aot", "ultrahard_steel_ingot");
        ResourceLocation rightId = BuiltInRegistries.ITEM.getKey(right.getItem());

        if (rightId.equals(ingotId) || rightId.equals(ingotIdAlt)) {
            int currentDur = ODMDamageManager.getDurability(left);
            int maxDur = ODMDamageManager.getMaxDurability(left);

            // Если УПМ полностью целое, чинить нечего
            if (currentDur >= maxDur) {
                return;
            }

            // Считаем, сколько слитков нужно для полной починки (по 100 прочности за штуку)
            int neededToFull = maxDur - currentDur;
            int ingotsNeeded = (int) Math.ceil((double) neededToFull / 100.0);

            // Берем минимум: сколько нужно vs сколько игрок реально положил в слот
            int ingotsToConsume = Math.min(right.getCount(), ingotsNeeded);

            ItemStack result = left.copy();
            int newDur = Math.min(maxDur, currentDur + (ingotsToConsume * 100));

            // Записываем новую кастомную прочность
            ODMDamageManager.setDurability(result, newDur);

            // Базовая цена в уровнях опыта (1 слиток = 1 уровень)
            int cost = ingotsToConsume;

            // Устанавливаем итоговый предмет в 3-й слот (результат)
            menu.getSlot(2).set(result);

            // Устанавливаем цену в уровнях опыта (переименование добавляет уровень сверху)
            this.cost.set(maxon$applyRename(result, left, cost));
            // Говорим наковальне, сколько именно предметов нужно забрать из 2-го слота при клике
            this.repairItemCountCost = ingotsToConsume;
        }
    }

    /**
     * Переносит на результат имя из поля ввода наковальни (или снимает старое, если поле
     * очистили) и возвращает итоговую цену. Общий для обеих веток: чинить и переименовывать
     * за один заход можно и слитком, и вторым комплектом УПМ.
     */
    @org.spongepowered.asm.mixin.Unique
    private int maxon$applyRename(ItemStack result, ItemStack left, int cost) {
        if (this.itemName != null && !StringUtils.isBlank(this.itemName)) {
            if (!this.itemName.equals(left.getHoverName().getString())) {
                result.set(DataComponents.CUSTOM_NAME, Component.literal(this.itemName));
                return cost + 1;
            }
        } else if (left.has(DataComponents.CUSTOM_NAME)) {
            result.remove(DataComponents.CUSTOM_NAME);
            return cost + 1;
        }
        return cost;
    }
}