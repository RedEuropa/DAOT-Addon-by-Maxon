package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Сервер -> клиент: в какой фазе обращения с винтовкой находится полицейский.
 * <p>
 * Шлётся всем, кто видит жителя, и только в момент СМЕНЫ фазы — клиент дальше отсчитывает
 * время сам (см. VillagerRifleAnimTracker). Иначе пришлось бы гнать пакет каждый тик на
 * каждого стрелка в зоне видимости.
 */
public record RifleAnimPayload(int entityId, int state) implements CustomPacketPayload {
    public static final Type<RifleAnimPayload> TYPE = new Type<>(MaxonDaotAddon.id("rifle_anim"));

    public static final int IDLE = 0;
    public static final int AIM = 1;
    public static final int SHOOT = 2;
    public static final int BOLT = 3;
    public static final int RELOAD = 4;

    public static final StreamCodec<FriendlyByteBuf, RifleAnimPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeVarInt(payload.state());
            },
            buf -> new RifleAnimPayload(buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
