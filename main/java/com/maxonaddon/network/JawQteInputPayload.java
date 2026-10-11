package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record JawQteInputPayload(int pressedKey) implements CustomPacketPayload {
    public static final Type<JawQteInputPayload> TYPE = new Type<>(MaxonDaotAddon.id("jaw_qte_input"));

    public static final StreamCodec<FriendlyByteBuf, JawQteInputPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, JawQteInputPayload::pressedKey,
            JawQteInputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}