package com.maxonaddon.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(daot.ThunderSpearEntityRenderer.class)
public class SpearRenderMixin {

    // Перехватываем обращение к mc.player внутри метода render
    @Redirect(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;"))
    private LocalPlayer trickRendererForOurGun(Minecraft instance) {
        LocalPlayer player = instance.player;

        if (player != null) {
            boolean hasOurGun = player.getMainHandItem().getItem() instanceof com.maxonaddon.item.ThunderGunItem ||
                    player.getOffhandItem().getItem() instanceof com.maxonaddon.item.ThunderGunItem;

            // Если в руках наша пушка, возвращаем null.
            // Условие мода (owner != mc.player) превратится в (owner != null) -> TRUE!
            // Мод сам отрисует свою красивую модельку копья и нить специально для нас.
            if (hasOurGun) {
                return null;
            }
        }

        // Если в руках оригинальный ODM Grip, возвращаем настоящего игрока,
        // и мод спрячет физическое копье (не будет дубликатов).
        return player;
    }
}