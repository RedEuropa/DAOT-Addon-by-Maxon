package com.maxonaddon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.maxonaddon.MaxonDaotAddon;

public record ReloadGearPayload() implements CustomPacketPayload {
    public static final Type<ReloadGearPayload> TYPE = new Type<>(MaxonDaotAddon.id("reload_gear"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadGearPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {},
            buf -> new ReloadGearPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}