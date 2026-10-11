package com.maxonaddon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.maxonaddon.MaxonDaotAddon;

public record OdmSupplyInteractPayload(int slotIndex) implements CustomPacketPayload {
    public static final Type<OdmSupplyInteractPayload> TYPE = new Type<>(MaxonDaotAddon.id("odm_supply_interact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OdmSupplyInteractPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.slotIndex()),
            buf -> new OdmSupplyInteractPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}