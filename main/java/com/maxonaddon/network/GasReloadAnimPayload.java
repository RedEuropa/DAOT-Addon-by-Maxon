package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Запускает разовую (не зацикленную) анимацию заправки газа у цистерны — "odm_reload"/"apg_reload"
 * из player_animations/gas_fill.animation.json. Отдельный пакет от TraumaAnimationPayload,
 * потому что тот жёстко зациклен (годится для травм, но не для одноразового действия) и не
 * несёт длительность — а клиенту нужно знать, когда самостоятельно остановить анимацию.
 */
public record GasReloadAnimPayload(String animName, int durationTicks) implements CustomPacketPayload {
    public static final Type<GasReloadAnimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaxonDaotAddon.MOD_ID, "gas_reload_anim"));
    public static final StreamCodec<FriendlyByteBuf, GasReloadAnimPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GasReloadAnimPayload::animName,
            ByteBufCodecs.VAR_INT, GasReloadAnimPayload::durationTicks,
            GasReloadAnimPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
