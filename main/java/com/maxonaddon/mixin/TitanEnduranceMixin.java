package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * "Выносливость титана" — увеличивает потолок шкалы стамины титан-шифтера на 25%.
 *
 * daot.network.ModNetworking.getMaxStaminaForPlayer/getMaxStaminaForUUID вычисляют максимум
 * ИЗ КОНФИГА (daot.ModConfig.attackMaxStamina и т.д.) по тегу силы шифтера — своего сеттера
 * у них нет. Обе точки используются внутри самого мода для регена/потолка стамины, поэтому
 * бустим ОБЕ независимо на RETURN — так итог совпадает, откуда бы стамина ни считалась.
 */
@Mixin(targets = "daot.network.ModNetworking", remap = false)
public abstract class TitanEnduranceMixin {

    private static final float BONUS_MULTIPLIER = 1.25F;

    @Inject(method = "getMaxStaminaForPlayer", at = @At("RETURN"), cancellable = true)
    private static void maxon$boostMaxStaminaForPlayer(ServerPlayer player, CallbackInfoReturnable<Float> cir) {
        IPlayerStats stats = (IPlayerStats) player;
        float value = cir.getReturnValue();
        if (stats.hasSkill("titan_endurance")) {
            value *= BONUS_MULTIPLIER;
        }
        double perkBonus = com.maxonaddon.perk.PerkManager.titanStaminaBonus(player);
        if (perkBonus != 0.0) {
            value *= (1.0F + (float) perkBonus);
        }
        cir.setReturnValue(value);
    }

    @Inject(method = "getMaxStaminaForUUID", at = @At("RETURN"), cancellable = true)
    private static void maxon$boostMaxStaminaForUUID(UUID uuid, CallbackInfoReturnable<Float> cir) {
        MinecraftServer server = com.maxonaddon.util.ServerInstanceHolder.get();
        if (server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return;

        IPlayerStats stats = (IPlayerStats) player;
        float value = cir.getReturnValue();
        if (stats.hasSkill("titan_endurance")) {
            value *= BONUS_MULTIPLIER;
        }
        double perkBonus = com.maxonaddon.perk.PerkManager.titanStaminaBonus(player);
        if (perkBonus != 0.0) {
            value *= (1.0F + (float) perkBonus);
        }
        cir.setReturnValue(value);
    }
}
