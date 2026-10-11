package com.maxonaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TitanHitSoundPayload(double x, double y, double z, int soundType) implements CustomPacketPayload {
    public static final Type<TitanHitSoundPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "titan_hit_sound"));

    public static final StreamCodec<FriendlyByteBuf, TitanHitSoundPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.x());
                buf.writeDouble(payload.y());
                buf.writeDouble(payload.z());
                buf.writeInt(payload.soundType()); // Изменили на Int
            },
            buf -> new TitanHitSoundPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}