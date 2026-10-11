package com.maxonaddon;

import com.maxonaddon.network.BerserkControlLossPayload;
import com.maxonaddon.network.BerserkQteHitPayload;
import com.maxonaddon.network.BerserkQteStatePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QTE "вырывания" из берсерк-режима (см. BerserkBossHandler — тег "berserk_boss" на титане
 * отключает игроку управление и отдаёт ИИ TitanBerserkMeleeGoal).
 *
 * Пока игрок сидит на своём же берсерк-титане (тег "berserk_owner_<uuid>" на нём), периодически
 * (раз в QTE_MIN..QTE_MAX_INTERVAL_TICKS) на 1.5 секунды (QTE_DURATION_TICKS) показываем одну
 * случайную клавишу. Успешное нажатие срезает часть стамины игрока (через daot же API) — когда
 * стамина полностью истощена, снимаем оба тега с титана: TitanBerserkMeleeGoal.canUse() перестаёт
 * проходить проверку, а BerserkDismountMixin снова отдаёт getControllingPassenger реальному
 * игроку — управление возвращается ровно так же, как если бы бросок на берсерк изначально не
 * сработал.
 */
public final class BerserkEscapeQteHandler {

    private static final int QTE_DURATION_TICKS = 30; // 1.5 секунды
    private static final int QTE_MIN_INTERVAL_TICKS = 80;  // 4 секунды
    private static final int QTE_MAX_INTERVAL_TICKS = 140; // 7 секунд
    private static final float DRAIN_FRACTION = 0.25F; // доля максимума стамины за одно успешное QTE

    private static final class Session {
        boolean qteActive = false;
        int qteTicksRemaining = 0;
        int nextQteInTicks;
    }

    private static final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private BerserkEscapeQteHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();
                boolean inControlLoss = isInBerserkControlLoss(player);
                Session session = sessions.get(uuid);

                if (!inControlLoss) {
                    if (session != null) {
                        sessions.remove(uuid);
                        if (session.qteActive) {
                            ServerPlayNetworking.send(player, new BerserkQteStatePayload(false));
                        }
                        ServerPlayNetworking.send(player, new BerserkControlLossPayload(false));
                    }
                    continue;
                }

                if (session == null) {
                    session = new Session();
                    session.nextQteInTicks = randomInterval(player);
                    sessions.put(uuid, session);
                    ServerPlayNetworking.send(player, new BerserkControlLossPayload(true));
                }

                if (session.qteActive) {
                    session.qteTicksRemaining--;
                    if (session.qteTicksRemaining <= 0) {
                        session.qteActive = false;
                        session.nextQteInTicks = randomInterval(player);
                        ServerPlayNetworking.send(player, new BerserkQteStatePayload(false));
                    }
                } else {
                    session.nextQteInTicks--;
                    if (session.nextQteInTicks <= 0) {
                        session.qteActive = true;
                        session.qteTicksRemaining = QTE_DURATION_TICKS;
                        ServerPlayNetworking.send(player, new BerserkQteStatePayload(true));
                    }
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(BerserkQteHitPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> handleHit(context.player()));
        });
    }

    private static boolean isInBerserkControlLoss(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return false;
        return vehicle.getTags().contains("berserk_boss")
                && vehicle.getTags().contains("berserk_owner_" + player.getUUID());
    }

    private static int randomInterval(ServerPlayer player) {
        return QTE_MIN_INTERVAL_TICKS + player.getRandom().nextInt(QTE_MAX_INTERVAL_TICKS - QTE_MIN_INTERVAL_TICKS + 1);
    }

    private static void handleHit(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Session session = sessions.get(uuid);
        if (session == null || !session.qteActive) return; // опоздал/читер — тик уже закрыл окно

        session.qteActive = false;
        session.nextQteInTicks = randomInterval(player);
        ServerPlayNetworking.send(player, new BerserkQteStatePayload(false));

        float maxStamina = daot.network.ModNetworking.getMaxStaminaForPlayer(player);
        daot.network.ModNetworking.drainStamina(uuid, maxStamina * DRAIN_FRACTION);
        float remaining = daot.network.ModNetworking.getStamina(uuid);

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 0.7F);

        if (remaining > 0.01F) {
            player.displayClientMessage(Component.translatable("message.maxonaddon.berserk_qte.resisted").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        // === ПОЛНОЕ ВЫРЫВАНИЕ ИЗ РЕЖИМА ===
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            vehicle.removeTag("berserk_boss");
            vehicle.removeTag("berserk_owner_" + uuid);
            if (vehicle instanceof Mob mobVehicle) {
                mobVehicle.setTarget(null);
            }
        }

        daot.TranslucentTracker.setToggled(player, false);

        // Силу обуздали — это навсегда. Без этого тега BerserkBossHandler бросает кубик при
        // КАЖДОМ превращении, пока игрок его не получил, а шансы берсерка в конфиге по
        // умолчанию стоят на 100 — то есть вырваться было в принципе невозможно: следующее
        // же превращение снова делало игрока берсерком. Ровно тот же тег выдаётся, когда
        // игроку повезло с броском при первом превращении (см. BerserkBossHandler).
        player.addTag("has_shifted_before");

        sessions.remove(uuid);
        ServerPlayNetworking.send(player, new BerserkControlLossPayload(false));

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.maxonaddon.berserk_qte.escaped").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
    }
}
