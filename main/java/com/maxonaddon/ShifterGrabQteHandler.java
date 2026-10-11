package com.maxonaddon;

import com.maxonaddon.network.JawQteStatePayload;
import daot.BladeItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

public class ShifterGrabQteHandler {

    public static class ShifterQteSession {
        public LivingEntity realTitan;
        public Entity grabEntity;
        public boolean hasBlade;
        public int requiredClicks = 20;
        public int currentClicks = 0;
        public int requiredKey = 0;
        public int ticksRemaining = 100;
        public int decayTimer = 0;
    }

    public static final Map<UUID, ShifterQteSession> activeShifterQtes = new ConcurrentHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Entity vehicle = player.getVehicle();
                boolean isRidingGrab = vehicle != null && vehicle.getClass().getSimpleName().contains("Grab");
                UUID pUuid = player.getUUID();

                // 1. ИГРОК ВЫПАЛ ИЗ ЗАХВАТА (например, титану порезали глаза)
                if (!isRidingGrab) {
                    player.removeTag("shifter_qte_success");
                    ShifterQteSession session = activeShifterQtes.remove(pUuid);

                    if (session != null) {
                        ServerPlayNetworking.send(player, new JawQteStatePayload(false, 0, 0));
                        player.displayClientMessage(Component.empty(), true);

                        // РАЗБЛОКИРУЕМ ТИТАНА!
                        if (session.realTitan != null) {
                            try {
                                Method setProtecting = session.realTitan.getClass().getMethod("setProtecting", boolean.class);
                                setProtecting.invoke(session.realTitan, false);
                            } catch (Exception ignored) {}
                        }
                    }
                    continue;
                }

                if (!activeShifterQtes.containsKey(pUuid)) {
                    String grabName = vehicle.getClass().getSimpleName();

                    if (grabName.contains("Armored") || grabName.contains("Beast") || grabName.contains("Female") || grabName.contains("Attack")) {
                        LivingEntity parentTitan = null;

                        try {
                            Method getParent = vehicle.getClass().getMethod("getParentTitan");
                            parentTitan = (LivingEntity) getParent.invoke(vehicle);
                        } catch (Exception ignored) {}

                        if (parentTitan != null) {
                            boolean hasBlade = player.getMainHandItem().getItem() instanceof BladeItem ||
                                    player.getOffhandItem().getItem() instanceof BladeItem;

                            ShifterQteSession session = new ShifterQteSession();
                            session.realTitan = parentTitan;
                            session.grabEntity = vehicle;
                            session.hasBlade = hasBlade;
                            activeShifterQtes.put(pUuid, session);

                            if (hasBlade) {
                                player.displayClientMessage(Component.translatable("message.maxonaddon.shifter_grab.mash_keys").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                ServerPlayNetworking.send(player, new JawQteStatePayload(true, session.currentClicks, session.requiredKey));
                            } else {
                                player.displayClientMessage(Component.translatable("message.maxonaddon.shifter_grab.no_blades").withStyle(ChatFormatting.DARK_RED), true);
                            }
                        }
                    }
                }
            }

            for (Map.Entry<UUID, ShifterQteSession> entry : activeShifterQtes.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                ShifterQteSession session = entry.getValue();

                // 2. ИГРОК ВЫШЕЛ ИЛИ ТИТАН УМЕР ВО ВРЕМЯ QTE
                if (player == null || session.realTitan == null || session.realTitan.isDeadOrDying()) {
                    activeShifterQtes.remove(entry.getKey());
                    if (player != null) {
                        ServerPlayNetworking.send(player, new JawQteStatePayload(false, 0, 0));
                        player.displayClientMessage(Component.empty(), true);
                    }
                    // РАЗБЛОКИРУЕМ ТИТАНА НА ВСЯКИЙ СЛУЧАЙ
                    if (session.realTitan != null && !session.realTitan.isDeadOrDying()) {
                        try {
                            Method setProtecting = session.realTitan.getClass().getMethod("setProtecting", boolean.class);
                            setProtecting.invoke(session.realTitan, false);
                        } catch (Exception ignored) {}
                    }
                    continue;
                }

                // Заморозка титана на время QTE
                session.realTitan.setDeltaMovement(0, session.realTitan.getDeltaMovement().y, 0);
                session.realTitan.setYRot(session.realTitan.yRotO);
                session.realTitan.setXRot(session.realTitan.xRotO);

                try {
                    Method setProtecting = session.realTitan.getClass().getMethod("setProtecting", boolean.class);
                    setProtecting.invoke(session.realTitan, true);
                } catch (Exception ignored) {}

                // Убывание полоски при бездействии
                if (session.hasBlade) {
                    session.decayTimer++;
                    if (session.decayTimer >= 20) {
                        session.decayTimer = 0;
                        if (session.currentClicks > 0) {
                            session.currentClicks--;
                            ServerPlayNetworking.send(player, new JawQteStatePayload(true, session.currentClicks, session.requiredKey));
                        }
                    }
                }

                session.ticksRemaining--;

                // 3. ПРОВАЛ QTE ПО ВРЕМЕНИ
                if (session.ticksRemaining <= 0) {
                    activeShifterQtes.remove(entry.getKey());
                    ServerPlayNetworking.send(player, new JawQteStatePayload(false, 0, 0));
                    player.displayClientMessage(Component.empty(), true);

                    if (session.hasBlade) {
                        player.displayClientMessage(Component.translatable("message.maxonaddon.qte.failed").withStyle(ChatFormatting.DARK_RED), true);
                    }

                    // РАЗБЛОКИРУЕМ ТИТАНА
                    try {
                        Method setProtecting = session.realTitan.getClass().getMethod("setProtecting", boolean.class);
                        setProtecting.invoke(session.realTitan, false);
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    public static void handleQtePress(ServerPlayer player, int key) {
        ShifterQteSession session = activeShifterQtes.get(player.getUUID());

        if (session == null || !session.hasBlade) return;

        if (key == session.requiredKey) {
            session.currentClicks++;
            session.requiredKey = (session.requiredKey == 0) ? 1 : 0;
            session.decayTimer = 0;

            if (session.currentClicks >= session.requiredClicks) {
                // 4. УСПЕХ QTE
                activeShifterQtes.remove(player.getUUID());
                ServerPlayNetworking.send(player, new JawQteStatePayload(false, 0, 0));

                player.displayClientMessage(Component.empty(), true);
                player.displayClientMessage(Component.translatable("message.maxonaddon.shifter_grab.cut_to_escape").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);

                player.addTag("shifter_qte_success");

                // РАЗБЛОКИРУЕМ ТИТАНА, чтобы он мог реагировать на следующий удар
                try {
                    Method setProtecting = session.realTitan.getClass().getMethod("setProtecting", boolean.class);
                    setProtecting.invoke(session.realTitan, false);
                } catch (Exception ignored) {}
            } else {
                ServerPlayNetworking.send(player, new JawQteStatePayload(true, session.currentClicks, session.requiredKey));
            }
        }
    }
}