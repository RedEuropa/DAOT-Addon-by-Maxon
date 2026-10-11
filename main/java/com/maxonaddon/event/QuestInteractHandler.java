package com.maxonaddon.event;

import com.maxonaddon.network.QuestGuiPayload;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;

public class QuestInteractHandler {
    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            // Система квестов/репутации выключена конфигом — командиры остаются стоять как
            // декорация, но меню заданий не открывают (см. MaxonConfig.reputationAllow).
            if (!com.maxonaddon.MaxonConfig.isReputationEnabled()) return InteractionResult.PASS;

            if (!world.isClientSide() && hand == InteractionHand.MAIN_HAND) {
                if (entity instanceof Villager villager && villager.getTags().contains("quest_commander")) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        ServerPlayNetworking.send(serverPlayer, new QuestGuiPayload(villager.getId()));
                    }
                    return InteractionResult.CONSUME;
                }
            }
            return InteractionResult.PASS;
        });
    }
}