package com.maxonaddon;

import com.maxonaddon.network.SmugglerGlowPayload;
import com.maxonaddon.perk.ModPerk;
import com.maxonaddon.perk.PerkManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Перк "Торговец" подсвечивает контрабандистов — и видит эту подсветку только он сам.
 * <p>
 * Серверный эффект Свечения тут не годится: он свойство самой сущности, а значит контур
 * увидели бы все игроки рядом. Поэтому решение о подсветке принимает клиент: сервер шлёт
 * владельцу перка список сетевых id, клиент кладёт его в SmugglerGlowTracker, а миксин на
 * Entity#isCurrentlyGlowing отвечает по этому списку. Ровно так же в dannys-aot устроены
 * очки Зика (ZekesGlassesGlowTracker + ZekesGlassesMixin) — приём проверенный.
 */
public final class TraderPerkHandler {

    /** Радиус, в котором торговец «чует» контрабандистов. */
    private static final double DETECT_RANGE = 48.0;

    /** Перебор сущностей вокруг каждого игрока недёшев, раз в секунду достаточно. */
    private static final int CHECK_INTERVAL = 20;

    private static int tickCounter = 0;

    private TraderPerkHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TraderPerkHandler::tick);
    }

    private static void tick(MinecraftServer server) {
        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;

            // Перк сняли — шлём пустой список, чтобы подсветка погасла, а не осталась
            // висеть на клиенте до перезахода.
            if (!hasTraderPerk(player)) {
                if (!player.getTags().contains("maxon_trader_glow_sent")) continue;
                player.removeTag("maxon_trader_glow_sent");
                ServerPlayNetworking.send(player, new SmugglerGlowPayload(List.of()));
                continue;
            }

            AABB box = player.getBoundingBox().inflate(DETECT_RANGE);
            List<Villager> smugglers = level.getEntitiesOfClass(Villager.class, box, v ->
                    v.isAlive()
                            && v.getTags().contains("smuggler")
                            && !v.getTags().contains("smuggler_caught"));

            List<Integer> ids = new ArrayList<>(smugglers.size());
            for (Villager smuggler : smugglers) {
                ids.add(smuggler.getId());
            }

            player.addTag("maxon_trader_glow_sent");
            ServerPlayNetworking.send(player, new SmugglerGlowPayload(ids));
        }
    }

    private static boolean hasTraderPerk(ServerPlayer player) {
        return PerkManager.equipped(player).contains(ModPerk.TRADER);
    }
}
