package com.maxonaddon;

import com.maxonaddon.perk.PerkManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Бонусы перков к САМОМУ титану, пока игрок им управляет (MAX_HEALTH/MOVEMENT_SPEED —
 * см. ModPerk#getTitanHealthBonus/getTitanSpeedBonus, сейчас реально ненулевые только у
 * "Героя Человечества"). У титана, в отличие от игрока, нет единой точки "сел за штурвал" —
 * PerkManager.refreshAttributes() перевешивает модификаторы на ИГРОКЕ при смене перков, а
 * тут нужен модификатор на ЖИВОЙ СУЩНОСТИ ТИТАНА, которая создаётся/удаляется при
 * шифте/дешифте отдельно от смены перков. Поэтому просто перевешиваем модификатор на
 * текущего оседланного шифтера каждую секунду — дёшево и не зависит от того, в какой
 * момент игрок сел за штурвал или сменил перк.
 */
public final class HeroPerkHandler {

    private static final ResourceLocation TITAN_HEALTH_ID = MaxonDaotAddon.id("perk_titan_health");
    private static final ResourceLocation TITAN_SPEED_ID = MaxonDaotAddon.id("perk_titan_speed");
    private static final int CHECK_INTERVAL_TICKS = 20; // раз в секунду — точность до тика тут не нужна

    private static int tickCounter = 0;

    private HeroPerkHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < CHECK_INTERVAL_TICKS) return;
            tickCounter = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!(player.getRootVehicle() instanceof LivingEntity titan)) continue;
                if (!(titan instanceof daot.ShifterTitan)) continue;
                if (titan.getControllingPassenger() != player) continue;

                double healthBonus = PerkManager.titanHealthBonus(player);
                double speedBonus = PerkManager.titanSpeedBonus(player);

                applyModifier(titan.getAttribute(Attributes.MAX_HEALTH), TITAN_HEALTH_ID, healthBonus);
                applyModifier(titan.getAttribute(Attributes.MOVEMENT_SPEED), TITAN_SPEED_ID, speedBonus);
            }
        });
    }

    private static void applyModifier(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) return;

        attribute.removeModifier(id);
        if (amount != 0.0) {
            attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
