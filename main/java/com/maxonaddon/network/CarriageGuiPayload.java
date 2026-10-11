package com.maxonaddon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record CarriageGuiPayload(List<String> unlockedCities, boolean forestUnlocked, String currentCityId) implements CustomPacketPayload {

    // Уникальный идентификатор пакета
    public static final CustomPacketPayload.Type<CarriageGuiPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("maxonaddon", "carriage_gui"));

    // Кодек для автоматической записи и чтения списка строк, boolean и ID текущего города —
    // нужен клиенту, чтобы посчитать и показать цену проезда (см. CarriagePricing).
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriageGuiPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CarriageGuiPayload::unlockedCities,
            ByteBufCodecs.BOOL, CarriageGuiPayload::forestUnlocked,
            ByteBufCodecs.STRING_UTF8, CarriageGuiPayload::currentCityId,
            CarriageGuiPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}