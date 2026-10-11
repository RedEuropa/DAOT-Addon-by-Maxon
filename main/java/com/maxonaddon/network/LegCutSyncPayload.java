package com.maxonaddon.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record LegCutSyncPayload(int entityId, boolean isCut) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("dassodm", "sync_leg_cut");
    public static final Type<LegCutSyncPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, LegCutSyncPayload> CODEC = CustomPacketPayload.codec(
            LegCutSyncPayload::write,
            LegCutSyncPayload::new
    );

    public LegCutSyncPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId());
        buf.writeBoolean(this.isCut());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Отправка с сервера на клиент — это безопасно
    public static void send(ServerPlayer player, int entityId, boolean isCut) {
        ServerPlayNetworking.send(player, new LegCutSyncPayload(entityId, isCut));
    }
}