package com.maxonaddon.mixin;

import com.maxonaddon.perk.PerkManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Экономия газа от перков.
 *
 * daot.DannysAot.consumeGasFromGear(ItemStack, int, Player) — единая точка списания газа УПМ:
 * через неё ходит и ODMTickHandler самого мода, и наш собственный код. Поэтому достаточно
 * урезать здесь запрошенное количество.
 *
 * Сигнатура обработчика: сначала изменяемое значение, затем ВСЕ параметры метода-цели в их
 * порядке (ItemStack, int, Player) — так Mixin отдаёт доступ к остальным аргументам.
 *
 * Округляем вниз, но не ниже 1: иначе при большой экономии мелкие списания давали бы 0,
 * и газ перестал бы тратиться совсем.
 */
@Mixin(targets = "daot.DannysAot", remap = false)
public abstract class PerkGasEconomyMixin {

    @ModifyVariable(method = "consumeGasFromGear", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static int maxon$applyPerkGasEconomy(int value, ItemStack gear, int amount, Player player) {
        if (player == null || player.level().isClientSide() || value <= 0) return value;

        double economy = PerkManager.gasEconomy(player);
        if (economy <= 0.0) return value;

        return Math.max(1, (int) Math.floor(value * (1.0 - economy)));
    }
}
