package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TraumaAnimationPayload(String animName) implements CustomPacketPayload {
    public static final Type<TraumaAnimationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaxonDaotAddon.MOD_ID, "trauma_anim"));
    public static final StreamCodec<FriendlyByteBuf, TraumaAnimationPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, TraumaAnimationPayload::animName,
            TraumaAnimationPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}