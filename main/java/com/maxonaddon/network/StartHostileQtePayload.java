package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StartHostileQtePayload() implements CustomPacketPayload {
    public static final Type<StartHostileQtePayload> TYPE = new Type<>(MaxonDaotAddon.id("start_hostile_qte"));
    public static final StreamCodec<FriendlyByteBuf, StartHostileQtePayload> CODEC = StreamCodec.unit(new StartHostileQtePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}