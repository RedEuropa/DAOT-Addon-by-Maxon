package com.maxonaddon.mixin;

import com.maxonaddon.perk.ModPerk;
import com.maxonaddon.perk.PerkUnlockHandler;
import com.maxonaddon.util.IPlayerStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Считает закрытые сделки и на сотой выдаёт перк "Торговец".
 * <p>
 * notifyTrade вызывается, когда сделка уже состоялась — предметы обменены. Это и есть
 * единственная точка, общая для обычных жителей и контрабандистов: у последних торговля
 * идёт тем же ванильным механизмом, только список предложений собирается нашим
 * SmugglerTradeHelper.
 */
@Mixin(AbstractVillager.class)
public class VillagerTradeCountMixin {

    /** Сколько сделок нужно закрыть, чтобы получить перк. */
    private static final int TRADES_FOR_PERK = 100;

    @Inject(method = "notifyTrade", at = @At("TAIL"))
    private void maxon$countTrade(MerchantOffer offer, CallbackInfo ci) {
        AbstractVillager merchant = (AbstractVillager) (Object) this;
        if (merchant.level().isClientSide()) return;

        Player trader = merchant.getTradingPlayer();
        if (!(trader instanceof ServerPlayer player)) return;

        IPlayerStats stats = (IPlayerStats) player;
        stats.addTradeCompleted();

        // Ровно на сотой — grantPerk сам ничего не сделает, если перк уже выдан, но лишний
        // раз дёргать его на каждой последующей сделке всё равно незачем.
        if (stats.getTradesCompleted() == TRADES_FOR_PERK) {
            PerkUnlockHandler.grantPerk(player, ModPerk.TRADER);
        }
    }
}
