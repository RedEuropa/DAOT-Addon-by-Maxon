package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuestGuiPayload(int commanderId) implements CustomPacketPayload {
    public static final Type<QuestGuiPayload> TYPE = new Type<>(MaxonDaotAddon.id("quest_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestGuiPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, QuestGuiPayload::commanderId,
            QuestGuiPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}