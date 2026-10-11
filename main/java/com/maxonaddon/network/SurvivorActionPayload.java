package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: ответ игрока раненому выжившему. action: 0 = помочь (взять задание), 1 = отказать
 * (штраф репутацией, см. SurvivorQuestHandler.decline). survivorId — сетевой id жителя,
 * сервер сам перепроверяет, что это действительно раненый и что он рядом.
 */
public record SurvivorActionPayload(int action, int survivorId) implements CustomPacketPayload {
    public static final Type<SurvivorActionPayload> TYPE = new Type<>(MaxonDaotAddon.id("survivor_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SurvivorActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SurvivorActionPayload::action,
            ByteBufCodecs.INT, SurvivorActionPayload::survivorId,
            SurvivorActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
