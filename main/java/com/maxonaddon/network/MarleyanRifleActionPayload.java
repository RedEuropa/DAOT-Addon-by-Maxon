package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * action: 0 = выстрел, 1 = перезарядка, 2 = начало прицеливания, 3 = конец прицеливания,
 * 4 = "доставание" (только что появилась в руке) — 2/3/4 лишь триггерят GeckoLib-анимацию
 * на сервере (видно всем, кто видит игрока), само приближение FOV и локальная анимация рук
 * игрока — чисто клиентские, см. GameRendererMixin и OdmAnimHelper.
 * aiming: для action == 0 — стрелял ли игрок держа ПКМ (прицел), выбирает анимацию
 * "aim_shoot" вместо обычной "fire" (см. shootMarleyanRifle в MaxonDaotAddon.java).
 */
public record MarleyanRifleActionPayload(int action, boolean aiming) implements CustomPacketPayload {
    public static final Type<MarleyanRifleActionPayload> TYPE = new Type<>(MaxonDaotAddon.id("marleyan_rifle_action"));

    public static final StreamCodec<FriendlyByteBuf, MarleyanRifleActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.action());
                buf.writeBoolean(payload.aiming());
            },
            buf -> new MarleyanRifleActionPayload(buf.readInt(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
