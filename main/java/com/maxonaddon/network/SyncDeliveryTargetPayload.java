package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: включает/выключает клиентский индикатор-локатор (полоска над XP-баром, см.
 * DeliveryLocatorClient/GuiMixin) на координаты получателя квеста "Доставить припасы".
 * active=false — квест закончился (сдан/провален/отменён), индикатор прячется.
 */
public record SyncDeliveryTargetPayload(boolean active, String dimension, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<SyncDeliveryTargetPayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_delivery_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDeliveryTargetPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncDeliveryTargetPayload::active,
            ByteBufCodecs.STRING_UTF8, SyncDeliveryTargetPayload::dimension,
            ByteBufCodecs.DOUBLE, SyncDeliveryTargetPayload::x,
            ByteBufCodecs.DOUBLE, SyncDeliveryTargetPayload::y,
            ByteBufCodecs.DOUBLE, SyncDeliveryTargetPayload::z,
            SyncDeliveryTargetPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
