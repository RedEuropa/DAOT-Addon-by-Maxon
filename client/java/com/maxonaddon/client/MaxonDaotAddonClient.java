package com.maxonaddon.client;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.client.gui.QuestScreen;
import com.maxonaddon.client.gui.StatsScreen;
import com.maxonaddon.client.render.*;
import com.maxonaddon.client.util.OdmAnimHelper;
import com.maxonaddon.entity.ModEntities;
import com.maxonaddon.network.*;
import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.LegCutState;
import daot.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import com.maxonaddon.client.particle.TitanSteamParticle;
import net.minecraft.world.phys.Vec3;
import com.maxonaddon.menu.SwordBoxMenu;
import com.maxonaddon.client.gui.SwordBoxScreen;
import net.minecraft.client.gui.screens.MenuScreens;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class MaxonDaotAddonClient implements ClientModInitializer {
    private static boolean wasAttackKeyDown = false;
    private static boolean wasUseKeyDown = false;
    public static net.minecraft.client.KeyMapping thunderGunLoadKey;
    private static String maxonLoadKeyName = "R";
    private static String maxonCachedKeyId = "";

    // === МАРЛИЙСКАЯ ВИНТОВКА: "combat mode" — своя кнопка перезарядки (настраиваемая, видна
    // в Управлении), ЛКМ/ПКМ вместо ванильного удара/использования переопределены на
    // выстрел/прицел (см. тик-обработчик ниже). ===
    public static net.minecraft.client.KeyMapping marleyanRifleReloadKey;
    /** Свист — подзывает купленную у перевозчика лошадь. */
    public static net.minecraft.client.KeyMapping whistleKey;
    private static boolean wasRifleAttackKeyDown = false;
    private static boolean wasRifleAimKeyDown = false;
    /** Плавный 0..1 прогресс прицеливания — читает GameRendererMixin для приближения FOV. */
    public static float rifleAimProgress = 0.0F;
    /** Тики до конца перезарядки — пока > 0, прицел принудительно снят и не включается заново. */
    private static int rifleReloadLockTicks = 0;
    /** Была ли винтовка в главной руке НА ПРОШЛОМ тике — фронт для анимации "доставания". */
    private static boolean wasHoldingRifleMain = false;
    // === АНИМАЦИЯ РУК ВИНТОВКИ: явный стейт-машин, без слоёв-таймеров ===
    // Раньше idle и одноразовые действия (draw/shoot/reload) жили на РАЗНЫХ слоях
    // PlayerAnimationLib и должны были сами разобраться по таймеру, кто сейчас главный —
    // из-за гонки между "одноразовый слой ещё не отпустил" и "idle-слой должен перехватить"
    // винтовка иногда так и оставалась висеть не в idle (особенно при смене предмета
    // хотбаром/скроллом). Теперь ОДИН слой (OdmAnimHelper.setRifleAnim), а какую именно
    // анимацию туда подавать прямо сейчас — решает вот этот код ниже, каждый тик заново,
    // по простому приоритету: доставание > выстрел > перезарядка > затвор > прицел > idle.
    /** Тики до конца "доставания" ("draw", ~0.9с). */
    private static int rifleDrawLockTicks = 0;
    /** Тики до конца анимации самого выстрела ("shoot"/"aim_shoot", ~1.3с). */
    private static int rifleShotAnimTicks = 0;
    /**
     * Тики до конца передёргивания затвора ("bolt_cycle") — идёт сразу ПОСЛЕ выстрела и
     * ровно до конца кулдауна: сумма выставляется так, чтобы shoot+bolt_cycle заняли все
     * MarleyanRifleItem.FIRE_COOLDOWN_TICKS. Модель самой винтовки такую связку играет уже
     * давно (см. triggerableAnim("fire") — thenPlay("shoot").thenPlay("bolt_cycle")), а руки
     * игрока после выстрела сразу возвращались в idle/aim — рассинхрон и убираем.
     */
    private static int rifleBoltAnimTicks = 0;
    /** Стреляли ли держа ПКМ в последний раз — выбирает "aim_shoot" вместо "shoot" на время rifleShotAnimTicks. */
    private static boolean lastShotWasAiming = false;

    @Override
    public void onInitializeClient() {
        registerWelcomeMessage();
        registerDisconnectReset();

        com.maxonaddon.item.ThunderGunItem.loadKeyNameSupplier = () -> maxonLoadKeyName;
        thunderGunLoadKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new net.minecraft.client.KeyMapping(
                        "key.maxonaddon.thunder_gun_load",
                        com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                        com.mojang.blaze3d.platform.InputConstants.KEY_R,
                        "category.maxonaddon.main"
                )
        );
        marleyanRifleReloadKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new net.minecraft.client.KeyMapping(
                        "key.maxonaddon.marleyan_rifle_reload",
                        com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                        com.mojang.blaze3d.platform.InputConstants.KEY_R,
                        "category.maxonaddon.main"
                )
        );
        // Свист по умолчанию не на клавише: в настройках управления он будет виден как
        // несвязанный, чтобы не отбирать кнопку у уже привычных биндов игрока.
        whistleKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new net.minecraft.client.KeyMapping(
                        "key.maxonaddon.whistle",
                        com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                        com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(),
                        "category.maxonaddon.main"
                )
        );
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(pluginContext -> {
            pluginContext.addModels(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "item/thunder_gun_gui"));
            pluginContext.addModels(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "item/item_box_gui"));
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                QuestGuiPayload.TYPE, (payload, context) -> {
                    context.client().execute(() -> {
                        net.minecraft.client.Minecraft.getInstance().setScreen(new QuestScreen(payload.commanderId()));
                    });
                }
        );
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.maxonaddon.network.SyncQuestPayload.TYPE, (payload, context) -> {
                    context.client().execute(() -> {
                        com.maxonaddon.client.gui.ClientQuestManager.currentQuestId = payload.questId();
                        com.maxonaddon.client.gui.ClientQuestManager.currentTarget = payload.target();
                        com.maxonaddon.client.gui.ClientQuestManager.questTitansKilled = payload.questTitansKilled();
                        com.maxonaddon.client.gui.ClientQuestManager.testQuestCompletions = payload.testQuestCompletions();
                        com.maxonaddon.client.gui.ClientQuestManager.deliveryDeadlineTick = payload.deliveryDeadlineTick();
                        com.maxonaddon.client.gui.ClientQuestManager.testQuestCommanderMask = payload.testQuestCommanderMask();
                    });
                }
        );
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.maxonaddon.network.SyncDeliveryTargetPayload.TYPE, (payload, context) -> {
                    context.client().execute(() -> {
                        com.maxonaddon.client.DeliveryLocatorClient.active = payload.active();
                        com.maxonaddon.client.DeliveryLocatorClient.dimension = payload.dimension();
                        com.maxonaddon.client.DeliveryLocatorClient.x = payload.x();
                        com.maxonaddon.client.DeliveryLocatorClient.y = payload.y();
                        com.maxonaddon.client.DeliveryLocatorClient.z = payload.z();
                    });
                }
        );
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.maxonaddon.network.SyncRescuePayload.TYPE, (payload, context) -> {
                    context.client().execute(() -> {
                        com.maxonaddon.client.RescueLocatorClient.active = payload.active();
                        com.maxonaddon.client.RescueLocatorClient.dimension = payload.dimension();
                        com.maxonaddon.client.RescueLocatorClient.x = payload.x();
                        com.maxonaddon.client.RescueLocatorClient.y = payload.y();
                        com.maxonaddon.client.RescueLocatorClient.z = payload.z();
                        com.maxonaddon.client.RescueLocatorClient.titanMode = payload.titanMode();
                    });
                }
        );
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.maxonaddon.network.SyncRescueStatusPayload.TYPE, (payload, context) -> {
                    context.client().execute(() -> {
                        com.maxonaddon.client.RescueLocatorClient.arrived = payload.arrived();
                        com.maxonaddon.client.RescueLocatorClient.cadetsTotal = payload.cadetsTotal();
                        com.maxonaddon.client.RescueLocatorClient.cadetsAlive = payload.cadetsAlive();
                        com.maxonaddon.client.RescueLocatorClient.titansRequired = payload.titansRequired();
                        com.maxonaddon.client.RescueLocatorClient.deadlineTick = payload.deadlineTick();
                    });
                }
        );
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.maxonaddon.network.SurvivorGuiPayload.TYPE, (payload, context) -> {
                    context.client().execute(() ->
                            net.minecraft.client.Minecraft.getInstance().setScreen(
                                    new com.maxonaddon.client.gui.SurvivorQuestScreen(payload.survivorId())));
                }
        );
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.maxonaddon.network.OutpostGuiPayload.TYPE, (payload, context) -> {
                    context.client().execute(() ->
                            net.minecraft.client.Minecraft.getInstance().setScreen(
                                    new com.maxonaddon.client.gui.OutpostQuestScreen(
                                            payload.cadetId(), payload.titansRequired(),
                                            payload.wallName(), payload.cooldownTicks())));
                }
        );
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.maxonaddon.network.SyncSurvivorPayload.TYPE, (payload, context) -> {
                    context.client().execute(() -> {
                        com.maxonaddon.client.SurvivorLocatorClient.active = payload.active();
                        com.maxonaddon.client.SurvivorLocatorClient.dimension = payload.dimension();
                        com.maxonaddon.client.SurvivorLocatorClient.x = payload.x();
                        com.maxonaddon.client.SurvivorLocatorClient.y = payload.y();
                        com.maxonaddon.client.SurvivorLocatorClient.z = payload.z();
                        com.maxonaddon.client.SurvivorLocatorClient.carried = payload.carried();
                    });
                }
        );
        // === СЛОЙ ТРОСОВ ДЛЯ ЖИТЕЛЕЙ ===
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof PlayerVillagerRenderer villagerRenderer) {
                registrationHelper.register(new com.maxonaddon.client.render.VillagerCableLayer<>(villagerRenderer));
            }
        });

        PayloadTypeRegistry.playS2C().register(VillagerTransformPayload.TYPE, VillagerTransformPayload.STREAM_CODEC);
        registerRenderers();
        JawQteClient.register();
        OdmAnimHelper.registerLayer();
        com.maxonaddon.client.HeadTraumaClient.register();
        ParticleFactoryRegistry.getInstance().register(MaxonDaotAddon.TITAN_STEAM, TitanSteamParticle.Provider::new);
        MenuScreens.register(MaxonDaotAddon.SWORD_BOX_MENU, SwordBoxScreen::new);
        MenuScreens.register(MaxonDaotAddon.GAS_CISTERNA_MENU, com.maxonaddon.client.gui.GasCisternaScreen::new);
        // Централизованно регистрируем всю клиентскую логику
        BlockEntityRenderers.register(MaxonDaotAddon.GAS_CISTERNA_ENTITY, GasCisternaRenderer::new);
        BlockEntityRenderers.register(MaxonDaotAddon.SWORD_BOX_ENTITY, SwordBoxRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(MaxonDaotAddon.GAS_CISTERNA_ITEM, new GasCisternaItemRenderer()::renderByItem);
        BuiltinItemRendererRegistry.INSTANCE.register(MaxonDaotAddon.THUNDER_GUN_ITEM, new ThunderGunRenderer()::renderByItem);
        BuiltinItemRendererRegistry.INSTANCE.register(MaxonDaotAddon.MARLEYAN_RIFLE_ITEM, new com.maxonaddon.client.render.MarleyanRifleRenderer()::renderByItem);
        BuiltinItemRendererRegistry.INSTANCE.register(MaxonDaotAddon.ITEM_BOX_ITEM, new SwordBoxItemRenderer()::renderByItem);
        DashOdmClient.register();
        EscapeQteClient.register();
        BerserkQteClient.register();
        EntityRendererRegistry.register(EntityType.VILLAGER, PlayerVillagerRenderer::new);

        // === РЕГИСТРАЦИЯ СЛОЯ ТРАВМ (ПУЛЬСАЦИИ КОНЕЧНОСТЕЙ) ===
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof PlayerRenderer playerRenderer) {
                registrationHelper.register(new TraumaLayer(playerRenderer));
                registrationHelper.register(new com.maxonaddon.client.render.BloodLayer(playerRenderer));
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            JawQteClient.tickInput(client);
        });

        // ЖЕСТКИЙ ПЕРЕХВАТ КЛИКОВ (Убирает спам, делает отдачу и запускает анимацию игрока)
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            Player player = client.player;
            if (player == null || client.level == null) return;
            String keyId = thunderGunLoadKey.saveString();
            if (!keyId.equals(maxonCachedKeyId)) {
                maxonCachedKeyId = keyId;
                maxonLoadKeyName = net.minecraft.network.chat.Component.translatable(keyId).getString();
            }
            // === ЖЕСТКИЙ СТАН ПРИ ПЕРЕЗАРЯДКЕ ГАЗА ===
            if (player.getTags().contains("gas_reloading")) {
                // Блокируем ЛКМ и ПКМ
                while(client.options.keyAttack.consumeClick()) {}
                client.options.keyAttack.setDown(false);
                while(client.options.keyUse.consumeClick()) {}
                client.options.keyUse.setDown(false);

                // Блокируем WASD и пробел
                client.options.keyUp.setDown(false);
                client.options.keyDown.setDown(false);
                client.options.keyLeft.setDown(false);
                client.options.keyRight.setDown(false);
                client.options.keyJump.setDown(false);

                // Фиксируем камеру (игрок не может отвернуться от баллона)
                player.setYRot(player.yRotO);
                player.setXRot(player.xRotO);
            }
            // --- ПЕРЕХВАТ ВАНИЛЬНЫХ КНОПОК ПЕРЕЗАРЯДКИ DANNYS-AOT ---
            for (net.minecraft.client.KeyMapping key : client.options.keyMappings) {
                // Ищем оригинальную кнопку мода Dannys-AOT
                String keyName = key.getName().toLowerCase();
                String category = key.getCategory().toLowerCase();
                if ((keyName.contains("reload") || keyName.contains("eject")) && (category.contains("daot") || category.contains("dannys"))) {
                    while (key.consumeClick()) {
                        // Кнопка Dannys-AOT нажата! Блокируем ее и шлем НАШ пакет перезарядки:
                        ClientPlayNetworking.send(new com.maxonaddon.network.ReloadGearPayload());
                    }
                }
            }

            ItemStack mainhand = player.getMainHandItem();
            ItemStack offhand = player.getOffhandItem();


            // Блокировка ударов в захвате (если QTE не активно, например, нет клинков)
            boolean isRidingGrab = player.getVehicle() != null && player.getVehicle().getClass().getSimpleName().contains("Grab");
            if (isRidingGrab && !JawQteClient.active) {
                Entity rootVehicle = player.getRootVehicle();
                String rootName = rootVehicle.getClass().getSimpleName();
                if (rootName.contains("Armored") || rootName.contains("Beast") || rootName.contains("Female") || rootName.contains("Attack")) {
                    while(client.options.keyAttack.consumeClick()) {}
                    client.options.keyAttack.setDown(false);
                    while(client.options.keyUse.consumeClick()) {}
                    client.options.keyUse.setDown(false);
                }
            }

            // --- 1. ЛКМ -> СТРЕЛЬБА ИЗ ЛЕВОЙ РУКИ ---
            boolean isAttackDown = client.options.keyAttack.isDown();
            if (isAttackDown && !wasAttackKeyDown) {
                if (offhand.getItem() == MaxonDaotAddon.THUNDER_GUN_ITEM) {
                    int loaded = com.maxonaddon.item.ThunderGunItem.getLoadedSpears(offhand);
                    if (loaded > 0 || player.isCreative()) {
                        ClientPlayNetworking.send(new com.maxonaddon.network.ThunderGunActionPayload(1, 1));
                        player.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
                        player.setXRot(player.getXRot() - 4.5F);
                        com.maxonaddon.client.util.OdmAnimHelper.playAction(player, "left_thunder_spear");
                        client.options.keyAttack.setDown(false);
                        while(client.options.keyAttack.consumeClick()) {}
                    }
                }
            }
            wasAttackKeyDown = isAttackDown;

            // --- 2. ПКМ -> СТРЕЛЬБА ИЗ ПРАВОЙ РУКИ ---
            boolean isUseDown = client.options.keyUse.isDown();
            if (isUseDown && !wasUseKeyDown) {
                if (mainhand.getItem() == MaxonDaotAddon.THUNDER_GUN_ITEM) {
                    int loaded = com.maxonaddon.item.ThunderGunItem.getLoadedSpears(mainhand);
                    if (loaded > 0 || player.isCreative()) {
                        ClientPlayNetworking.send(new com.maxonaddon.network.ThunderGunActionPayload(1, 0));
                        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                        player.setXRot(player.getXRot() - 4.5F);
                        com.maxonaddon.client.util.OdmAnimHelper.playAction(player, "right_thunder_spear");
                        client.options.keyUse.setDown(false);
                        while(client.options.keyUse.consumeClick()) {}
                    }
                }
            }
            wasUseKeyDown = isUseDown;

            // --- 3. НАША КНОПКА ЗАРЯДКИ ---
            while (thunderGunLoadKey.consumeClick()) {
                boolean mainHasGun = mainhand.getItem() == MaxonDaotAddon.THUNDER_GUN_ITEM;
                boolean offHasGun = offhand.getItem() == MaxonDaotAddon.THUNDER_GUN_ITEM;

                // Перезарядка Thunder Gun
                if (mainHasGun || offHasGun) {
                    ClientPlayNetworking.send(new com.maxonaddon.network.ThunderGunActionPayload(0, 0));
                }
                // ПЕРЕХВАТ ПЕРЕЗАРЯДКИ КЛИНКОВ И АПГ
                else if (mainhand.getItem() instanceof daot.BladeItem || offhand.getItem() instanceof daot.BladeItem ||
                        mainhand.getItem() instanceof daot.APGGunItem || offhand.getItem() instanceof daot.APGGunItem) {
                    ClientPlayNetworking.send(new com.maxonaddon.network.ReloadGearPayload());
                }
            }

            // --- 4. МАРЛИЙСКАЯ ВИНТОВКА: "combat mode" ---
            // Пока винтовка в главной руке, ЛКМ/ПКМ полностью переопределены (выстрел/прицел)
            // вместо ванильного удара/использования.
            boolean holdingRifleMain = mainhand.getItem() == MaxonDaotAddon.MARLEYAN_RIFLE_ITEM;
            if (holdingRifleMain) {
                if (!wasHoldingRifleMain) {
                    // Только что достали винтовку в руку — разовая анимация "доставания" и у
                    // самого предмета (сервер триггерит GeckoLib "draw" всем, кто видит игрока),
                    // и у рук игрока (~0.9с = длина "draw" в marleyan_rifle_hand.animation.json).
                    ClientPlayNetworking.send(new com.maxonaddon.network.MarleyanRifleActionPayload(4, false));
                    rifleDrawLockTicks = 18;
                }

                if (rifleReloadLockTicks > 0) {
                    rifleReloadLockTicks--;
                }
                // Во время перезарядки прицел принудительно снят и повторно не включается,
                // даже если ПКМ всё ещё физически зажата — по просьбе, "когда релоад, зум
                // сбрасывается и начинается перезарядка".
                boolean aimHeld = rifleReloadLockTicks <= 0 && client.options.keyUse.isDown();

                if (aimHeld != wasRifleAimKeyDown) {
                    // ВЫХОД из прицела во время анимации выстрела НЕ отправляем: "unaim" — это
                    // triggerableAnim("unaim") = thenLoop("idle") у самой винтовки (см.
                    // MarleyanRifleItem), и он обрывал цепочку "aim_shoot -> bolt_cycle" на
                    // полуслове — передёргивание затвора просто не успевало показаться, если
                    // отпустить ПКМ сразу после выстрела. Отдельный unaim тут и не нужен:
                    // базовый предикат того же контроллера — thenLoop("idle"), так что по
                    // окончании цепочки винтовка возвращается в idle сама.
                    boolean shotChainBusy = rifleShotAnimTicks > 0 || rifleBoltAnimTicks > 0;
                    if (aimHeld || !shotChainBusy) {
                        ClientPlayNetworking.send(new com.maxonaddon.network.MarleyanRifleActionPayload(aimHeld ? 2 : 3, false));
                    }
                }
                wasRifleAimKeyDown = aimHeld;
                rifleAimProgress += ((aimHeld ? 1.0F : 0.0F) - rifleAimProgress) * 0.3F;

                // ВАЖНО: гасим только накопленные "клики" (не даём ПКМ открыть сундук/меню
                // и т.п.), но НЕ трогаем сам isDown() — принудительный setDown(false) каждый
                // тик рвал отслеживание удержания: без нового события нажатия isDown()
                // навсегда становился false, и зум срабатывал импульсом на один тик вместо
                // "держим — зумим, отпустили — вышли".
                while (client.options.keyUse.consumeClick()) {}

                boolean attackDown = rifleReloadLockTicks <= 0 && client.options.keyAttack.isDown();
                if (attackDown && !wasRifleAttackKeyDown) {
                    int loaded = com.maxonaddon.item.MarleyanRifleItem.getLoadedRounds(mainhand);
                    boolean onCooldown = player.getCooldowns().isOnCooldown(mainhand.getItem());
                    if (!onCooldown && (loaded > 0 || player.isCreative())) {
                        ClientPlayNetworking.send(new com.maxonaddon.network.MarleyanRifleActionPayload(0, aimHeld));
                        // player.swing() здесь нарочно НЕТ — это ванильная анимация удара
                        // ("дёргается как инструмент"), она конфликтует с собственной
                        // GeckoLib-анимацией "fire"/"fire_aim" самого предмета.
                        lastShotWasAiming = aimHeld;
                        // Длины разные: "shoot" 1.2667с (~26 тиков), "aim_shoot" 1.0с (20).
                        rifleShotAnimTicks = aimHeld ? 20 : 26;
                        // Остаток кулдауна докручиваем передёргиванием затвора — так руки
                        // заняты ровно до момента, когда снова можно стрелять.
                        rifleBoltAnimTicks = Math.max(0,
                                com.maxonaddon.item.MarleyanRifleItem.FIRE_COOLDOWN_TICKS - rifleShotAnimTicks);
                    }
                }
                wasRifleAttackKeyDown = attackDown;

                // ЛКМ для добычи/удара блокируем принудительно (тут держать isDown() не
                // нужно — выстрел одноразовый по нажатию, а не по удержанию).
                while (client.options.keyAttack.consumeClick()) {}
                client.options.keyAttack.setDown(false);

                // === ЕДИНОЕ РЕШЕНИЕ: какая анимация рук должна идти ПРЯМО СЕЙЧАС ===
                // Приоритет сверху вниз: доставание > выстрел (пока не отыграл) > перезарядка
                // (пока не отыграла) > передёргивание затвора (остаток кулдауна) >
                // прицел (пока держим) > обычный idle. Перезарядка выше передёргивания
                // намеренно: игрок нажал R явно, ждать доигрывания затвора неотзывчиво. Пересчитывается
                // заново каждый тик — де-дубликация внутри setRifleAnim сама не даст дёргать
                // одну и ту же анимацию заново, так что "просто вызывать каждый тик" безопасно.
                if (rifleDrawLockTicks > 0) {
                    com.maxonaddon.client.util.OdmAnimHelper.setRifleAnim(player, "draw", false);
                } else if (rifleShotAnimTicks > 0) {
                    com.maxonaddon.client.util.OdmAnimHelper.setRifleAnim(player, lastShotWasAiming ? "aim_shoot" : "shoot", false);
                } else if (rifleReloadLockTicks > 0) {
                    com.maxonaddon.client.util.OdmAnimHelper.setRifleAnim(player, "rifle_reload", false);
                } else if (rifleBoltAnimTicks > 0) {
                    com.maxonaddon.client.util.OdmAnimHelper.setRifleAnim(player, "bolt_cycle", false);
                } else if (aimHeld) {
                    com.maxonaddon.client.util.OdmAnimHelper.setRifleAnim(player, "aim", false);
                } else {
                    com.maxonaddon.client.util.OdmAnimHelper.setRifleAnim(player, "idle", true);
                }

                if (rifleDrawLockTicks > 0) rifleDrawLockTicks--;
                if (rifleShotAnimTicks > 0) rifleShotAnimTicks--;
                else if (rifleBoltAnimTicks > 0) rifleBoltAnimTicks--; // затвор идёт ПОСЛЕ выстрела
            } else {
                // Оружие убрали, не отпустив ПКМ, — гасим прицел плавно, а не дёргано.
                wasRifleAimKeyDown = false;
                wasRifleAttackKeyDown = false;
                rifleAimProgress *= 0.7F;
                rifleReloadLockTicks = 0;
                rifleDrawLockTicks = 0;
                rifleShotAnimTicks = 0;
                rifleBoltAnimTicks = 0;

                // Если винтовка на мгновение оказалась в левой руке (до того, как её вернёт
                // серверный тик-обработчик, см. MaxonDaotAddon.java) — не пустая анимация,
                // а отдельная "idle2" ("пассивный" хват), а не полное отсутствие анимации.
                if (offhand.getItem() == MaxonDaotAddon.MARLEYAN_RIFLE_ITEM) {
                    com.maxonaddon.client.util.OdmAnimHelper.setRifleAnim(player, "idle2", true);
                } else {
                    com.maxonaddon.client.util.OdmAnimHelper.clearRifleAnim(player);
                }
            }
            wasHoldingRifleMain = holdingRifleMain;

            // Отдельная настраиваемая кнопка перезарядки винтовки (см. key.maxonaddon.marleyan_rifle_reload).
            while (marleyanRifleReloadKey.consumeClick()) {
                if (holdingRifleMain && rifleReloadLockTicks <= 0) {
                    ClientPlayNetworking.send(new com.maxonaddon.network.MarleyanRifleActionPayload(1, false));
                    // ~3.2с анимации "rifle_reload" (marleyan_rifle_hand.animation.json) — на это время
                    // прицел заблокирован и не пересобирается сам собой. Саму анимацию рук
                    // отдельно триггерить не нужно — единый приоритетный стейт-машин выше
                    // подхватит "rifle_reload" сам, увидев rifleReloadLockTicks > 0 на следующем тике.
                    rifleReloadLockTicks = 64;
                }
            }
        });

        // Добавь это в метод onInitializeClient
        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.CarriageGuiPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                // Открываем наш экран, передавая данные из пакета
                net.minecraft.client.Minecraft.getInstance().setScreen(
                        new com.maxonaddon.client.gui.CarriageScreen(payload.unlockedCities(), payload.forestUnlocked(), payload.currentCityId())
                );
            });
        });

        // === ПЕРЕХВАТ ВАНИЛЬНОГО ПАКЕТА ТРОСОВ ДЛЯ ЖИТЕЛЕЙ ===
        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.VillagerHookSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().level != null) {
                    Entity entity = context.client().level.getEntity(payload.entityId());
                    if (entity instanceof Villager) {
                        Vec3 target = new Vec3(payload.x(), payload.y(), payload.z());
                        com.maxonaddon.client.VillagerHookTracker.updateHook(payload.entityId(), payload.active(), target);
                    }
                }
            });
        });

        // Внутри метода onInitializeClient()
        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.TitanHitSoundPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().level != null) {
                    net.minecraft.sounds.SoundEvent sound;
                    // Звук смерти титана заметно громче остальных на слух — приглушаем его
                    // на 30% (2.0 -> 1.4), удар и крит остаются как были.
                    float volume = 2.0F;

                    // Выбираем звук на основе soundType
                    switch (payload.soundType()) {
                        case 1 -> {
                            sound = com.maxonaddon.sound.ModSounds.NAPE_DEATH;
                            volume = 1.4F;
                        }
                        case 2 -> sound = com.maxonaddon.sound.ModSounds.CRIT_HIT;
                        default -> sound = com.maxonaddon.sound.ModSounds.NAPE_HIT;
                    }

                    context.client().level.playLocalSound(
                            payload.x(), payload.y(), payload.z(),
                            sound, net.minecraft.sounds.SoundSource.HOSTILE,
                            volume, 1.0F, false
                    );
                }
            });

        });

        ClientPlayNetworking.registerGlobalReceiver(VillagerPreshiftPayload.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.level != null) {
                    Entity entity = mc.level.getEntity(payload.entityId());
                    if (entity instanceof Villager villager) {
                        try {
                            daot.ShiftParticleHelper.spawnPreshiftParticleBoundToEntity(mc.level, villager);
                        } catch (Exception ignored) {}
                        VillagerAnimationClientTracker.startBite(villager.getId());
                        mc.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                                (net.minecraft.sounds.SoundEvent) net.minecraft.sounds.SoundEvents.TRIDENT_THUNDER.value(),
                                net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.7F, mc.level.random, villager.getX(), villager.getY(), villager.getZ()
                        ));
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(VillagerTransformPayload.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    daot.ShiftLightningClientTracker.mark(payload.titanId());
                } catch (Exception ignored) {}
                playTitanRoarOnly(payload.x(), payload.y(), payload.z(), payload.titanType());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncFearStatsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                FearManager.clientTitansKilled = payload.titansKilled();
                int immunityThreshold = MaxonConfig.get().titansForFearImmunity;
                if (FearManager.clientTitansKilled >= immunityThreshold) {
                    FearManager.targetFearLevel = 0.0;
                    FearManager.currentFearLevel = 0.0;
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.SyncTraumaPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    com.maxonaddon.util.IPlayerStats stats = (com.maxonaddon.util.IPlayerStats) context.client().player;
                    stats.setTraumaTimer(com.maxonaddon.util.TraumaType.valueOf(payload.traumaType()), payload.timer());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TraumaAnimationPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Player targetPlayer = context.player();
                if (targetPlayer != null) {
                    com.maxonaddon.client.util.OdmAnimHelper.play(targetPlayer, payload.animName());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.GasReloadAnimPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Player targetPlayer = context.player();
                if (targetPlayer != null) {
                    com.maxonaddon.client.util.OdmAnimHelper.playReload(targetPlayer, payload.animName(), payload.durationTicks());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(LegCutSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().level != null) {
                    Entity entity = context.client().level.getEntity(payload.entityId());
                    if (entity instanceof LegCutState legCutEntity) {
                        legCutEntity.setLegCut(payload.isCut());
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SilentJamPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                net.minecraft.world.entity.player.Player player = context.player();
                if (player != null) {
                    daot.ODMTickHandler.jamOdmForDuration(player.getUUID(), payload.durationMs());
                    daot.HookPoint leftHook = daot.ODMTickHandler.getLeftHook(player.getUUID());
                    daot.HookPoint rightHook = daot.ODMTickHandler.getRightHook(player.getUUID());
                    if (leftHook != null && leftHook.active) leftHook.release();
                    if (rightHook != null && rightHook.active) rightHook.release();
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.RifleAnimPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> VillagerRifleAnimTracker.set(payload.entityId(), payload.state()));
        });

        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.SmugglerGlowPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> com.maxonaddon.client.SmugglerGlowTracker.update(payload.entityIds()));
        });

        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.HorseShopPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                com.maxonaddon.client.HorseShopClient.seed = payload.seed();
                com.maxonaddon.client.HorseShopClient.ownsHorse = payload.ownsHorse();
            });
        });

        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VillagerAnimationClientTracker.tick();
            VillagerHookTracker.tick();
            VillagerRifleAnimTracker.tick();

            Player player = client.player;
            if (player == null || client.level == null) return;
            com.maxonaddon.client.CameraDynamics.update();

            // Навыки "Ускоренный крюк": каждый уровень даёт +50% к скорости полёта крюка.
            // Считаем здесь, потому что миксины крюка до локального игрока не дотягиваются.
            IPlayerStats hookStats = (IPlayerStats) player;
            double hookSpeed = 1.0;
            if (hookStats.hasSkill("fast_hook_1")) hookSpeed += 0.5;
            if (hookStats.hasSkill("fast_hook_2")) hookSpeed += 0.5;
            com.maxonaddon.util.HookSpeedHelper.setSpeedMultiplier(hookSpeed);

            // Свист. consumeClick() сам гасит очередь нажатий, поэтому зажатая клавиша
            // не будет слать пакет каждый тик.
            while (whistleKey != null && whistleKey.consumeClick()) {
                ClientPlayNetworking.send(new com.maxonaddon.network.HorseActionPayload(
                        com.maxonaddon.network.HorseActionPayload.WHISTLE, 0));
            }
            if (player instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer) {
                IPlayerStats stats = (IPlayerStats) player;
                boolean hasLegs = stats.hasTrauma(com.maxonaddon.util.TraumaType.LEGS);
                boolean hasArms = stats.hasTrauma(com.maxonaddon.util.TraumaType.ARMS);
                boolean isMoving = player.walkDist != player.walkDistO;
                boolean isCrouching = player.isCrouching();

                if (hasLegs && hasArms) {
                    if (isMoving || isCrouching) {
                        com.maxonaddon.client.util.OdmAnimHelper.play(player, "injure_legarm");
                    } else {
                        com.maxonaddon.client.util.OdmAnimHelper.play(player, "injure_legarm_stay");
                    }
                } else if (hasLegs) {
                    if (isMoving || isCrouching) {
                        com.maxonaddon.client.util.OdmAnimHelper.play(player, "injure_leg");
                    } else {
                        com.maxonaddon.client.util.OdmAnimHelper.play(player, "injure_leg_stay");
                    }
                } else if (hasArms) {
                    if (isMoving) {
                        String armAnim = player.isSprinting() ? "injure_arm_running" : "injure_arm";
                        com.maxonaddon.client.util.OdmAnimHelper.play(player, armAnim);
                    } else {
                        com.maxonaddon.client.util.OdmAnimHelper.play(player, "injure_arm_stay");
                    }
                } else {
                    com.maxonaddon.client.util.OdmAnimHelper.stop(player);
                }
            }

            // Источник истины — сервер (см. SyncModFlagsPayload / MaxonDaotAddon.broadcastModFlags).
            // Раньше здесь читался client.level.getGameRules() напрямую, что не гарантировало
            // мгновенное и надёжное обновление у всех уже подключённых игроков.
            if (!ClientModState.isFearSystemEnabled()) {
                FearManager.targetFearLevel = 0.0;
                FearManager.currentFearLevel = 0.0;
                return;
            }

            int immunityThreshold = MaxonConfig.get().titansForFearImmunity;
            boolean isAwakened = player.getTags().contains(AwakenedPowerTracker.AWAKENED_POWER_TAG) ||
                    AwakenedPowerTracker.isClientPlayerActive(player.getUUID());

            if (player.getVehicle() != null && player.getVehicle().getClass().getSimpleName().contains("Titan")) {
                FearManager.targetFearLevel = 0.0;
                FearManager.lastOwnedTitanUuid = player.getVehicle().getUUID();
            } else if (isAwakened) {
                FearManager.targetFearLevel = 0.0;
            } else if (FearManager.clientTitansKilled >= immunityThreshold) {
                FearManager.targetFearLevel = 0.0;
            } else {
                FearManager.scanTickCounter++;
                if (FearManager.scanTickCounter >= 10) {
                    FearManager.scanTickCounter = 0;
                    AABB searchBox = player.getBoundingBox().inflate(50.0);
                    List<LivingEntity> nearbyEntities = client.level.getEntitiesOfClass(LivingEntity.class, searchBox);
                    double maxFear = 0.0;

                    for (LivingEntity entity : nearbyEntities) {
                        String className = entity.getClass().getSimpleName();
                        if (!className.contains("Titan") && !className.contains("ConnieFather")) continue;
                        if (className.contains("Nape") || className.contains("Eye") || className.contains("Grab") || className.contains("Dummy")) continue;
                        if (player.getVehicle() == entity || entity.hasPassenger(player)) continue;
                        if (entity.getUUID().equals(FearManager.lastOwnedTitanUuid)) continue;

                        if (entity instanceof ShifterTitan) {
                            UUID ownerUuid = getTitanOwnerUUID(entity);
                            if (ownerUuid != null && ownerUuid.equals(player.getUUID())) {
                                continue;
                            }
                        }

                        double distance = player.distanceTo(entity);
                        if (distance < 50.0) {
                            double fear = 1.0 - (distance / 50.0);
                            if (fear > maxFear) maxFear = fear;
                        }
                    }
                    double progressionMultiplier = 1.0 - ((double) FearManager.clientTitansKilled / immunityThreshold);
                    if (progressionMultiplier < 0) progressionMultiplier = 0.0;
                    FearManager.targetFearLevel = maxFear * progressionMultiplier;
                }
            }

            FearManager.currentFearLevel += (FearManager.targetFearLevel - FearManager.currentFearLevel) * 0.1;
            if (FearManager.currentFearLevel < 0.01) {
                FearManager.currentFearLevel = 0.0;
            }

            // === ДЫХАНИЕ ОТ СТРАХА: start_breath -> breath (зациклен) -> stop_breath, 30%
            // громкости файла. Личный, не позиционный звук (Attenuation.NONE, relative=true) —
            // это дыхание САМОГО игрока, оно не должно тише слышаться при повороте камеры.
            boolean breathShouldPlay = FearManager.currentFearLevel > 0.05;
            net.minecraft.client.sounds.SoundManager soundManager = client.getSoundManager();
            if (breathShouldPlay && !FearManager.breathActive) {
                FearManager.breathActive = true;
                soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.maxonaddon.sound.ModSounds.FEAR_START_BREATH, 1.0F, 0.3F));

                net.minecraft.client.resources.sounds.SimpleSoundInstance loop = new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                        com.maxonaddon.sound.ModSounds.FEAR_BREATH.getLocation(),
                        net.minecraft.sounds.SoundSource.MASTER,
                        0.3F, 1.0F,
                        net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom(),
                        true, 0,
                        net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                        0.0, 0.0, 0.0, true
                );
                FearManager.breathLoopInstance = loop;
                soundManager.play(loop);
            } else if (!breathShouldPlay && FearManager.breathActive) {
                FearManager.breathActive = false;
                if (FearManager.breathLoopInstance != null) {
                    soundManager.stop(FearManager.breathLoopInstance);
                    FearManager.breathLoopInstance = null;
                }
                soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.maxonaddon.sound.ModSounds.FEAR_STOP_BREATH, 1.0F, 0.3F));
            }

            // === ТЕКСТ "ВНУТРЕННЕГО ГОЛОСА" — только когда страх ощутимо выше среднего ===
            // (страх сам по себе уже зависит от расстояния до титана, поэтому "близко к
            // титану" отдельно проверять не нужно — оно и так заложено в currentFearLevel).
            com.maxonaddon.client.FearTextManager.tick(FearManager.currentFearLevel > 0.5);
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenStatsScreenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new StatsScreen(
                        payload.titansKilled(),
                        payload.fearResist(),
                        payload.totalHours(),
                        payload.hoursWithoutDeath(),
                        payload.bloodline(),
                        payload.shifterStatus(),
                        payload.ymirCurseTime(),
                        payload.level(),
                        payload.exp(),
                        payload.requiredExp(),
                        payload.skillPoints(),
                        payload.bloodlinePoints(),
                        payload.titanPoints(),
                        payload.questsCompleted(),
                        new HashSet<>(payload.unlockedSkills()),
                        payload.repMaria(),
                        payload.repRose(),
                        payload.repSina()
                ));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncSkillsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    IPlayerStats stats = (IPlayerStats) context.client().player;
                    stats.setUnlockedSkills(new HashSet<>(payload.unlockedSkills()));
                }
            });
        });

        // Синхронизация перков: сервер шлёт содержимое всех слотов, кладём прямо в поля
        // клиентского зеркала PlayerStatsMixin — тот же приём, что используется для навыков.
        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.SyncPerksPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) return;
                IPlayerStats stats = (IPlayerStats) context.client().player;
                java.util.List<String> slots = payload.slots();
                for (int i = 0; i < com.maxonaddon.perk.PerkManager.SLOTS; i++) {
                    stats.setEquippedPerk(i, i < slots.size() ? slots.get(i) : null);
                }

                if (context.client().screen instanceof com.maxonaddon.client.gui.PerksScreen perksScreen) {
                    perksScreen.refreshSlotIcons();
                }
            });
        });

        // Синхронизация РАЗБЛОКИРОВАННЫХ перков — отдельно от того, что надето в слотах.
        // Список нужен PerkListScreen, чтобы показывать плитку недоступной, пока перк не получен.
        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.SyncUnlockedPerksPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) return;
                IPlayerStats stats = (IPlayerStats) context.client().player;
                stats.setUnlockedPerks(new HashSet<>(payload.unlockedPerks()));

                if (context.client().screen instanceof com.maxonaddon.client.gui.PerkListScreen listScreen) {
                    listScreen.refreshLockedState();
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncBloodPayload.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    com.maxonaddon.client.BloodClientTracker.setStage(payload.entityId(), payload.bloodStage()));
        });

        // Конфиг сервера полностью замещает клиентский — до выхода из игры (см. DISCONNECT ниже).
        ClientPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.ConfigSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                // В одиночной игре подменять нечего: интегрированный сервер живёт в этом же
                // процессе и читает тот же maxon_addon.json, то есть прислал бы нам наши же
                // настройки. Трогать INSTANCE там незачем — и заодно не блокируется save().
                if (context.client().hasSingleplayerServer()) return;
                MaxonConfig.applyFromServer(payload.json());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncModFlagsPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientModState.setStatisticsEnabled(payload.statisticsEnabled());
                ClientModState.setFearSystemEnabled(payload.fearSystemEnabled());
                ClientModState.setReputationEnabled(payload.reputationEnabled());
                if (!payload.fearSystemEnabled()) {
                    FearManager.targetFearLevel = 0.0;
                    FearManager.currentFearLevel = 0.0;
                }
            });
        });

    }

    private void registerRenderers() {
        EntityRendererRegistry.register(ModEntities.TITAN_BOULDER, TitanBoulderRenderer::new);
    }

    /**
     * Приветственное сообщение в чат при заходе на сервер — со ссылками на CurseForge/ТГ/Дискорд.
     * Полностью клиентское (никакого сетевого взаимодействия): вставляется прямо в чат-виджет,
     * а не отправляется через сервер.
     */
    /**
     * Статические поля клиентских "зеркал" серверного состояния (квест, локатор доставки и
     * т.п.) живут столько же, сколько сам процесс игры — сервер их не сбрасывает при выходе,
     * только при явных событиях (сдача/провал квеста). Из-за этого при заходе в ДРУГОЙ мир
     * (без общего сервера, без нового SyncQuestPayload с активным квестом от старого мира)
     * оставался "призрак" — например, локатор квеста "Доставить припасы" из прошлого мира
     * молча продолжал показываться, будто квест всё ещё активен. Сбрасываем всё это на
     * отключении от сервера — заход в следующий мир (или на другой сервер) начинается с чистого листа,
     * а если квест реально активен, актуальный SyncQuestPayload/SyncDeliveryTargetPayload
     * придёт заново сразу при подключении.
     */
    private void registerDisconnectReset() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // Вышли с сервера — возвращаем собственный maxon_addon.json, иначе чужие настройки
            // остались бы действовать и в одиночной игре до перезапуска.
            MaxonConfig.restoreLocal();
            // Состояние ОДМ статическое: без сброса недоигранный рывок и «недавний зацеп»
            // перешли бы в следующий мир.
            com.maxonaddon.client.OdmClientState.reset();

            com.maxonaddon.client.gui.ClientQuestManager.currentQuestId = "";
            com.maxonaddon.client.gui.ClientQuestManager.currentTarget = "";
            com.maxonaddon.client.gui.ClientQuestManager.questTitansKilled = 0;
            com.maxonaddon.client.gui.ClientQuestManager.testQuestCompletions = 0;
            com.maxonaddon.client.gui.ClientQuestManager.testQuestCommanderMask = 0;
            com.maxonaddon.client.gui.ClientQuestManager.deliveryDeadlineTick = 0L;

            com.maxonaddon.client.DeliveryLocatorClient.active = false;
            com.maxonaddon.client.DeliveryLocatorClient.dimension = "";
            com.maxonaddon.client.DeliveryLocatorClient.x = 0.0;
            com.maxonaddon.client.DeliveryLocatorClient.y = 0.0;
            com.maxonaddon.client.DeliveryLocatorClient.z = 0.0;

            com.maxonaddon.client.RescueLocatorClient.active = false;
            com.maxonaddon.client.RescueLocatorClient.dimension = "";
            com.maxonaddon.client.RescueLocatorClient.x = 0.0;
            com.maxonaddon.client.RescueLocatorClient.y = 0.0;
            com.maxonaddon.client.RescueLocatorClient.z = 0.0;
            com.maxonaddon.client.RescueLocatorClient.titanMode = false;
            com.maxonaddon.client.RescueLocatorClient.arrived = false;
            com.maxonaddon.client.RescueLocatorClient.cadetsTotal = 0;
            com.maxonaddon.client.RescueLocatorClient.cadetsAlive = 0;
            com.maxonaddon.client.RescueLocatorClient.titansRequired = 0;
            com.maxonaddon.client.RescueLocatorClient.deadlineTick = 0L;

            com.maxonaddon.client.SurvivorLocatorClient.active = false;
            com.maxonaddon.client.SurvivorLocatorClient.dimension = "";
            com.maxonaddon.client.SurvivorLocatorClient.x = 0.0;
            com.maxonaddon.client.SurvivorLocatorClient.y = 0.0;
            com.maxonaddon.client.SurvivorLocatorClient.z = 0.0;
            com.maxonaddon.client.SurvivorLocatorClient.carried = false;
        });
    }

    private void registerWelcomeMessage() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player == null) return;

            net.minecraft.network.chat.MutableComponent welcomeMessage = net.minecraft.network.chat.Component
                    .translatable("message.maxonaddon.welcome.line1")
                    .withStyle(net.minecraft.ChatFormatting.GREEN)
                    .append(net.minecraft.network.chat.Component.translatable("message.maxonaddon.welcome.curseforge")
                            .withStyle(net.minecraft.network.chat.Style.EMPTY
                                    .withColor(net.minecraft.ChatFormatting.GOLD)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                                            "https://www.curseforge.com/minecraft/mc-mods/maxon-daot-rework"))))
                    .append(net.minecraft.network.chat.Component.translatable("message.maxonaddon.welcome.line2")
                            .withStyle(net.minecraft.ChatFormatting.GREEN))
                    .append(net.minecraft.network.chat.Component.translatable("message.maxonaddon.welcome.telegram")
                            .withStyle(net.minecraft.network.chat.Style.EMPTY
                                    .withColor(net.minecraft.ChatFormatting.AQUA)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                                            "https://t.me/y4kto"))))
                    .append(net.minecraft.network.chat.Component.translatable("message.maxonaddon.welcome.line3")
                            .withStyle(net.minecraft.ChatFormatting.GREEN))
                    .append(net.minecraft.network.chat.Component.translatable("message.maxonaddon.welcome.discord")
                            .withStyle(net.minecraft.network.chat.Style.EMPTY
                                    .withColor(net.minecraft.ChatFormatting.LIGHT_PURPLE)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                                            "https://discord.gg/2XzGcvQdh8"))));

            // Пишем прямо в чат-виджет клиента, а не через player.sendMessage/displayClientMessage —
            // те шлют пакет на сервер, а сообщение чисто локальное, без сетевого туда-обратно.
            client.gui.getChat().addMessage(welcomeMessage);
        });
    }

    public static void playTitanRoarOnly(double x, double y, double z, int titanType) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.level != null) {
            net.minecraft.world.phys.Vec3 playerPos = client.player.position();
            double distance = playerPos.distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
            float volume = 15.0F * (float)Math.max(0.1, 1.0F / (1.0F + distance / 100.0F));
            float distanceAttenuation = (float)Math.max(0.1, 1.0F / (1.0F + distance / 100.0F));
            float finalVolume = volume * distanceAttenuation;
            float shiftPitch = (titanType == 2) ? 0.75F : 0.8F + (float)(Math.random() * 0.2);

            client.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(daot.ModSounds.TITAN_SHIFT, net.minecraft.sounds.SoundSource.HOSTILE, finalVolume, shiftPitch, client.level.random, x, y, z));
            client.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance((net.minecraft.sounds.SoundEvent)net.minecraft.sounds.SoundEvents.TRIDENT_THUNDER.value(), net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.5F, client.level.random, x, y, z));

            net.minecraft.sounds.SoundEvent roar = null;
            float pitch = 0.9F + (float)(Math.random() * 0.2);
            float vol = volume;

            switch (titanType) {
                case 1 -> roar = daot.ModSounds.ATTACK_TITAN_ROAR;
                case 2 -> { roar = daot.ModSounds.ARMORED_TITAN_ROAR; vol *= 4.0F; }
                case 3 -> roar = ModSounds.FEMALE_TITAN_ROAR;
                case 5 -> { roar = daot.ModSounds.ATTACK_TITAN_ROAR; pitch = 1.3F; }
            }

            if (roar != null) {
                client.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                        roar, net.minecraft.sounds.SoundSource.HOSTILE, vol, pitch, client.level.random, x, y, z
                ));
            }
        }
    }

    private static UUID getTitanOwnerUUID(LivingEntity entity) {
        if (entity instanceof AttackTitanEntity t) return t.getShifterUUID();
        if (entity instanceof ArmoredTitanEntity t) return t.getShifterUUID();
        if (entity instanceof ColossalTitanEntity t) return t.getShifterUUID();
        if (entity instanceof FemaleTitanEntity t) return t.getShifterUUID();
        if (entity instanceof BeastTitanEntity t) return t.getShifterUUID();
        if (entity instanceof WarhammerTitanEntity t) return t.getShifterUUID();
        if (entity instanceof CartShifterTitanEntity t) return t.getShifterUUID();
        if (entity instanceof OgreShifterTitanEntity t) return t.getShifterUUID();
        if (entity instanceof TestShifterTitanEntity t) return t.getShifterUUID();
        return null;
    }
}