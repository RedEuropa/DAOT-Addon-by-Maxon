package com.maxonaddon.util;

import daot.DannysAot;
import daot.ODMAPGItem;
import daot.ODMGearItem;
import net.minecraft.world.item.ItemStack;

/**
 * Единая точка работы с газом снаряжения.
 * <p>
 * Обычное УПМ и АПГ — это два разных класса предметов с одинаковым набором статических
 * методов, но с разными лимитами: ODMGearItem.MAX_GAS = 500, ODMAPGItem.MAX_GAS = 1000.
 * Без этой обёртки любой код, работающий с газом, вынужден каждый раз выбирать ветку сам
 * и рискует залить в АПГ по лимиту обычного УПМ (или наоборот).
 */
public final class OdmGasHelper {

    private OdmGasHelper() {}

    /** Это снаряжение с баком (УПМ или АПГ)? */
    public static boolean isOdm(ItemStack stack) {
        return !stack.isEmpty() && DannysAot.isODMGear(stack.getItem());
    }

    public static int getMaxGas(ItemStack stack) {
        return stack.getItem() == DannysAot.ODM_APG ? ODMAPGItem.MAX_GAS : ODMGearItem.MAX_GAS;
    }

    public static int getGas(ItemStack stack) {
        return stack.getItem() == DannysAot.ODM_APG ? ODMAPGItem.getGas(stack) : ODMGearItem.getGas(stack);
    }

    /** Записывает газ, обрезая по лимиту именно этого типа снаряжения. */
    public static void setGas(ItemStack stack, int gas) {
        int clamped = Math.max(0, Math.min(getMaxGas(stack), gas));
        if (stack.getItem() == DannysAot.ODM_APG) {
            ODMAPGItem.setGas(stack, clamped);
        } else {
            ODMGearItem.setGas(stack, clamped);
        }
    }
}
