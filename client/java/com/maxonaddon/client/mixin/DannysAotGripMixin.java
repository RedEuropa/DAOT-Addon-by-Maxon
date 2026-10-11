package com.maxonaddon.client.mixin;

import com.maxonaddon.util.IPlayerStats;
import daot.DannysAot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DannysAot.class, remap = false)
public abstract class DannysAotGripMixin {

    @Inject(method = "isValidGripForLeggings", at = @At("HEAD"), cancellable = true)
    private static void allowMixedGripsWithSkill(ItemStack leggings, Item handItem, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (handItem instanceof com.maxonaddon.item.ThunderGunItem) {
            cir.setReturnValue(true);
        }
        if (player != null) {
            IPlayerStats stats = (IPlayerStats) player;

            // Если навык изучен
            if (stats.hasConnectorMastery()) {
                // Если в руке либо клинок, либо пистолет APG
                if (handItem == DannysAot.BLADE || handItem == DannysAot.APG_GUN) {
                    // Разрешаем использование в обход стандартной проверки!
                    cir.setReturnValue(true);
                }
            }
        }
    }
}