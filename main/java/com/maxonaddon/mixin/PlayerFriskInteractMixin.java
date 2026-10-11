package com.maxonaddon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

import daot.HandcuffsTracker;
import com.maxonaddon.inventory.FriskContainer;

@Mixin(Player.class)
public class PlayerFriskInteractMixin {

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void onFriskCuffedPlayer(Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Player searcher = (Player) (Object) this;

        // Работаем только на сервере и только основной рукой
        if (!searcher.level().isClientSide() && hand == InteractionHand.MAIN_HAND) {

            // Рука должна быть пустой
            if (searcher.getItemInHand(hand).isEmpty()) {

                // Цель - игрок, и он в наручниках
                if (target instanceof ServerPlayer cuffedPlayer && HandcuffsTracker.isCuffed(cuffedPlayer)) {

                    // Открываем интерфейс большого сундука, куда передаем наш кастомный контейнер
                    searcher.openMenu(new SimpleMenuProvider(
                            (syncId, playerInv, p) -> new ChestMenu(MenuType.GENERIC_9x6, syncId, playerInv, new FriskContainer(cuffedPlayer), 6),
                            Component.translatable("gui.maxonaddon.frisk.title", cuffedPlayer.getScoreboardName())
                    ));

                    cir.setReturnValue(InteractionResult.SUCCESS);
                }
            }
        }
    }
}