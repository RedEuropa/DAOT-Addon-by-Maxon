package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Сервер -> клиент: seed текущего предложения лавки и признак того, есть ли у игрока лошадь.
 * Сами статы не шлём — клиент выведет их из seed'а тем же HorseOffer.generate.
 */
public record HorseShopPayload(long seed, boolean ownsHorse) implements CustomPacketPayload {
    public static final Type<HorseShopPayload> TYPE = new Type<>(MaxonDaotAddon.id("horse_shop"));

    public static final StreamCodec<FriendlyByteBuf, HorseShopPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeLong(payload.seed());
                buf.writeBoolean(payload.ownsHorse());
            },
            buf -> new HorseShopPayload(buf.readLong(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
