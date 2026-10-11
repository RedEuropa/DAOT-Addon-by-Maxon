package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.TraumaType;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void maxon$preventItemSwap(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            IPlayerStats stats = (IPlayerStats) this.player;
            if (stats.hasTrauma(TraumaType.ARMS)) {
                // Отменяем обработку пакета, предмет не переложится
                ci.cancel();
                return;
            }

            // Марлийская винтовка — блок только ОДНОСТОРОННИЙ: пока активно держим её в правой
            // руке, хоткей (F) не даёт спихнуть её в левую (сломало бы combat mode на ЛКМ/ПКМ
            // посреди боя). Обратно — если она уже в левой (положили туда вручную через
            // инвентарь) — хоткей нормально возвращает её в правую, это не блокируется.
            if (this.player.getMainHandItem().getItem() instanceof com.maxonaddon.item.MarleyanRifleItem) {
                ci.cancel();
            }
        }
    }
}