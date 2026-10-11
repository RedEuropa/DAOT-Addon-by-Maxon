package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Сервер -> клиент: содержимое всех слотов перков. Пустая строка — слот свободен. */
public record SyncPerksPayload(List<String> slots) implements CustomPacketPayload {
    public static final Type<SyncPerksPayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_perks"));

    public static final StreamCodec<FriendlyByteBuf, SyncPerksPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.slots().size());
                for (String id : payload.slots()) {
                    buf.writeUtf(id == null ? "" : id);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<String> slots = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    slots.add(buf.readUtf());
                }
                return new SyncPerksPayload(slots);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
