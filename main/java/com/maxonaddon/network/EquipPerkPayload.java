package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Клиент -> сервер: поставить перк в слот. Пустой perkId означает "очистить слот".
 * Сервер обязан проверить и номер слота, и существование перка — клиенту тут не верим.
 */
public record EquipPerkPayload(int slot, String perkId) implements CustomPacketPayload {
    public static final Type<EquipPerkPayload> TYPE = new Type<>(MaxonDaotAddon.id("equip_perk"));

    public static final StreamCodec<FriendlyByteBuf, EquipPerkPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.slot());
                buf.writeUtf(payload.perkId() == null ? "" : payload.perkId());
            },
            buf -> new EquipPerkPayload(buf.readVarInt(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
