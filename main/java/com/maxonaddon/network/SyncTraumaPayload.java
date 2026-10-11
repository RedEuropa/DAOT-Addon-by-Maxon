package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Переименовали 'type' в 'traumaType', чтобы избежать конфликта с методом type() из CustomPacketPayload
public record SyncTraumaPayload(String traumaType, int timer) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncTraumaPayload> TYPE = new CustomPacketPayload.Type<>(MaxonDaotAddon.id("sync_trauma"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTraumaPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncTraumaPayload::traumaType,
            ByteBufCodecs.INT, SyncTraumaPayload::timer,
            SyncTraumaPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}