package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: локатор квеста "Спасение застрявших" (тот же приём, что и SyncDeliveryTargetPayload,
 * но отдельным типом, чтобы не путать с локатором доставки: активен может быть только один
 * квест разом, но клиентские "зеркала" для них разные). active=false — квест закончился ИЛИ
 * указывать больше не на что (локатор прячется).
 * <p>
 * До прибытия ведёт на здание (titanMode=false, обычный жёлто-зелёный трек). После
 * прибытия и до зачистки всех титанов ведёт на БЛИЖАЙШЕГО ещё живого квестового титана
 * (titanMode=true, красный трек — см. GuiMixin#renderRescueLocator) и периодически
 * переотправляется (см. RescueQuestHandler#updateTitanLocators), потому что титан двигается.
 * <p>
 * Живые счётчики (сколько кадетов уцелело, сколько нужно титанов, дедлайн прибытия) — в
 * ОТДЕЛЬНОМ SyncRescueStatusPayload: StreamCodec.composite поддерживает максимум 6 полей.
 */
public record SyncRescuePayload(boolean active, String dimension, double x, double y, double z, boolean titanMode) implements CustomPacketPayload {
    public static final Type<SyncRescuePayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_rescue_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRescuePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncRescuePayload::active,
            ByteBufCodecs.STRING_UTF8, SyncRescuePayload::dimension,
            ByteBufCodecs.DOUBLE, SyncRescuePayload::x,
            ByteBufCodecs.DOUBLE, SyncRescuePayload::y,
            ByteBufCodecs.DOUBLE, SyncRescuePayload::z,
            ByteBufCodecs.BOOL, SyncRescuePayload::titanMode,
            SyncRescuePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
