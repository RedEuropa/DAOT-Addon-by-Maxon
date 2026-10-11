package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StopHostileQtePayload() implements CustomPacketPayload {
    public static final Type<StopHostileQtePayload> TYPE = new Type<>(MaxonDaotAddon.id("stop_hostile_qte"));
    public static final StreamCodec<FriendlyByteBuf, StopHostileQtePayload> CODEC = StreamCodec.unit(new StopHostileQtePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}