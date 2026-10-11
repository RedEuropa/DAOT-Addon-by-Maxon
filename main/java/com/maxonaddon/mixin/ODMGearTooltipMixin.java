package com.maxonaddon.mixin;

import com.maxonaddon.util.ODMDamageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = {daot.ODMGearItem.class, daot.ODMAPGItem.class})
public class ODMGearTooltipMixin {

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void maxon$addDurabilityTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag, CallbackInfo ci) {
        int durability = ODMDamageManager.getDurability(stack);
        int maxDurability = ODMDamageManager.getMaxDurability(stack);

        if (durability <= 0) {
            tooltipComponents.add(Component.translatable("gui.maxonaddon.odm.tooltip.broken", maxDurability));
        } else if (durability <= maxDurability / 2) {
            tooltipComponents.add(Component.translatable("gui.maxonaddon.odm.tooltip.damaged", durability, maxDurability));
        } else {
            tooltipComponents.add(Component.translatable("gui.maxonaddon.odm.tooltip.normal", durability, maxDurability));
        }
    }
}