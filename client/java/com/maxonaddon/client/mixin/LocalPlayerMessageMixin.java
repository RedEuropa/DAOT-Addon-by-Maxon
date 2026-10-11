package com.maxonaddon.client.mixin;

import com.maxonaddon.util.IPlayerStats;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMessageMixin {

    @ModifyVariable(method = "displayClientMessage", at = @At("HEAD"), argsOnly = true)
    private Component replaceJamMessage(Component original) {
        // Кастуем LocalPlayer к нашему интерфейсу, так как он наследует его от Player
        IPlayerStats stats = (IPlayerStats) this;

        // Перехватываем сообщение
        if (!stats.hasOdmMastery() && original.getString().equals("ODM jammed!")) {
            // Подменяем на наш мультиязычный ключ
            return Component.translatable("gui.maxonaddon.skills.odm_required").withStyle(ChatFormatting.RED);
        }

        return original;
    }
}