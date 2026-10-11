package com.maxonaddon;

import com.maxonaddon.block.entity.SwordBoxBlockEntity;
import com.maxonaddon.event.QuestCommanderSpawner;
import com.maxonaddon.network.SyncDeliveryTargetPayload;
import com.maxonaddon.network.SyncQuestPayload;
import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.Wall;
import com.maxonaddon.util.WallHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Квест "Доставить припасы": берётся у любого командира, получатель — случайный ДРУГОЙ
 * командир (см. startDeliveryQuest), выдаётся запечатанный ящик (ITEM_BOX со случайным
 * лутом в нужных слотах — см. SwordBoxBlockEntity), плюс индикатор-локатор над XP-баром
 * (см. SyncDeliveryTargetPayload/DeliveryLocatorClient) — компас больше не выдаётся,
 * локатора достаточно. Сдать можно только получателю, только пока пломба не сорвана
 * (SHIFT+ПКМ по закрытому ящику где-то между принятием и сдачей — см.
 * onSealBroken/SwordBoxBlock#checkDeliverySeal) и только в течение 10 минут (см.
 * periodic-проверку дедлайна в register()).
 * <p>
 * Провал (сорванная пломба или истёкшее время) снимает -10 репутации у стены, где квест
 * взяли, и ДОПОЛНИТЕЛЬНО -10 у стены получателя, если она отличается от исходной.
 * <p>
 * Все сообщения и подписи предметов идут через Component.translatable — переводятся у
 * КАЖДОГО игрока под его собственный язык клиента (сервер не знает и не должен знать,
 * на каком языке играет получатель).
 */
public final class DeliveryQuestHandler {

    public static final String QUEST_ID = "delivery_quest";

    private static final int DELIVERY_TIME_TICKS = 10 * 60 * 20; // 10 минут
    private static final int FAIL_REP_PENALTY = 10;
    private static final int REWARD_ICE = 32;
    private static final int REWARD_BLADES = 4;
    private static final int REWARD_EMERALDS = 5;
    private static final int DEADLINE_CHECK_INTERVAL_TICKS = 100; // раз в 5 секунд — дедлайну точность до тика не нужна

    private static final ResourceKey<Level> PARADIS_KEY =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));

    private static int tickCounter = 0;

    private DeliveryQuestHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < DEADLINE_CHECK_INTERVAL_TICKS) return;
            tickCounter = 0;
            checkDeadlines(server);
        });
    }

    private static void checkDeadlines(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IPlayerStats stats = (IPlayerStats) player;
            if (!QUEST_ID.equals(stats.getActiveQuestId())) continue;
            if (player.level().getGameTime() < stats.getDeliveryDeadlineTick()) continue;

            failQuest(player, "message.maxonaddon.delivery.failed_timeout");
        }
    }

    /**
     * Принять квест у originCommander. Возвращает false (и сама уже отправила игроку
     * сообщение об ошибке), если выдать нечего — квест в этом случае НЕ активируется,
     * вызывающий код (MaxonDaotAddon) должен просто прервать обработку action==0.
     */
    public static boolean startDeliveryQuest(ServerPlayer player, Villager originCommander) {
        ServerLevel paradis = player.server.getLevel(PARADIS_KEY);
        if (paradis == null) return false;

        // Цель выбирается по ФИКСИРОВАННЫМ координатам QuestCommanderSpawner.ALL_POINTS,
        // а не сканированием живых сущностей по карте: level.getEntities()/getChunk(...,true)
        // грузит ТЕРРЕЙН, но не гарантирует синхронную загрузку entity (они хранятся в
        // отдельных region-файлах entities/r.X.Z.mca и подгружаются PersistentEntitySection
        // Manager асинхронно) — именно поэтому даже после форс-загрузки чанков всех 12 точек
        // getEntities() мог не находить ни одного командира, кроме того, что физически рядом
        // с игроком. Координаты же известны заранее на этапе компиляции — entity вообще не
        // нужен для ВЫБОРА цели, он понадобится только в момент СДАЧИ (tryTurnIn), когда
        // игрок и так стоит рядом и чанк гарантированно загружен.
        int originIndex = QuestCommanderSpawner.nearestPointIndex(originCommander.getX(), originCommander.getZ());
        int destIndex;
        RandomSource random = player.getRandom();
        do {
            destIndex = random.nextInt(QuestCommanderSpawner.ALL_POINTS.length);
        } while (destIndex == originIndex);

        BlockPos destPos = QuestCommanderSpawner.ALL_POINTS[destIndex];
        Wall originWall = WallHelper.getWallForCommander(originCommander);
        Wall destWall = QuestCommanderSpawner.wallForPointIndex(destIndex);

        IPlayerStats stats = (IPlayerStats) player;

        // Храним просто ИМЯ стены (язык-нейтральный идентификатор), а не готовую фразу —
        // готовую фразу собирает уже КАЖДЫЙ клиент сам под свой язык (см. QuestJournalScreen).
        stats.setActiveQuest(QUEST_ID, destWall.name());
        stats.setQuestTitansKilled(0);
        stats.setActiveQuestWall(originWall != null ? originWall.name() : "");
        stats.setDeliveryTarget(destPos.getX(), destPos.getY(), destPos.getZ());
        stats.setDeliveryTargetWall(destWall.name());
        stats.setDeliveryDeadlineTick(paradis.getGameTime() + DELIVERY_TIME_TICKS);

        giveOrDrop(player, buildSupplyCrate(player));
        sendLocator(player, destPos);

        Component targetName = Component.translatable(destWall.getTranslationKey());
        player.sendSystemMessage(Component.translatable("message.maxonaddon.delivery.started", targetName).withStyle(ChatFormatting.GOLD));

        // Явно показываем координаты цели — чтобы можно было проверить, что локатор
        // действительно ведёт туда же (полезно и для отладки, и просто как ориентир).
        String coords = destPos.getX() + ", " + destPos.getY() + ", " + destPos.getZ();
        player.sendSystemMessage(Component.literal(coords).withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }

    private static ItemStack buildSupplyCrate(ServerPlayer owner) {
        SwordBoxBlockEntity boxEntity = new SwordBoxBlockEntity(BlockPos.ZERO, MaxonDaotAddon.ITEM_BOX.defaultBlockState());
        RandomSource random = owner.getRandom();

        int bladeCount = 1 + random.nextInt(3); // 1..3, слоты 0-3
        for (int i = 0; i < bladeCount && i < 4; i++) {
            ItemStack blade = new ItemStack(daot.DannysAot.BLADE);
            if (blade.getItem() instanceof daot.BladeItem) {
                daot.BladeItem.setBladeState(blade, daot.BladeItem.BladeState.FRESH);
            }
            boxEntity.setItem(i, blade);
        }

        int canisterCount = 1 + random.nextInt(2); // 1..2, слоты 4-5
        for (int i = 0; i < canisterCount && i < 2; i++) {
            ItemStack canister = new ItemStack(daot.DannysAot.GAS_CANISTER);
            if (canister.getItem() instanceof daot.GasCanisterItem) {
                int maxGas = daot.GasCanisterItem.MAX_GAS;
                daot.GasCanisterItem.setGas(canister, Math.min(maxGas, 60 + random.nextInt(Math.max(1, maxGas - 59))));
            }
            boxEntity.setItem(4 + i, canister);
        }

        int spearCount = 2 + random.nextInt(4); // 2..5, слоты 6-10
        for (int i = 0; i < spearCount && i < 5; i++) {
            boxEntity.setItem(6 + i, new ItemStack(daot.DannysAot.THUNDER_SPEAR));
        }

        boxEntity.setMaxonDeliveryBox(true);
        boxEntity.setMaxonDeliveryOwnerUuid(owner.getUUID().toString());

        ItemStack crateStack = new ItemStack(MaxonDaotAddon.ITEM_BOX_ITEM);
        boxEntity.saveToItem(crateStack, owner.level().registryAccess());

        crateStack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("item.maxonaddon.delivery_crate").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)));
        crateStack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.maxonaddon.delivery_crate.lore1").withStyle(ChatFormatting.GRAY),
                Component.translatable("item.maxonaddon.delivery_crate.lore2").withStyle(ChatFormatting.RED)
        )));

        return crateStack;
    }

    private static void sendLocator(ServerPlayer player, BlockPos destPos) {
        ServerPlayNetworking.send(player, new SyncDeliveryTargetPayload(
                true,
                PARADIS_KEY.location().toString(),
                destPos.getX() + 0.5, destPos.getY(), destPos.getZ() + 0.5
        ));
    }

    private static void clearLocator(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SyncDeliveryTargetPayload(false, "", 0.0, 0.0, 0.0));
    }

    /** Зовётся из SwordBoxBlock#checkDeliverySeal в момент, когда владелец срывает пломбу. */
    public static void onSealBroken(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        if (!QUEST_ID.equals(stats.getActiveQuestId())) return;

        failQuest(player, "message.maxonaddon.delivery.failed_seal");
    }

    /** Зовётся из MaxonDaotAddon при action == 2 (Сдать), если активен именно этот квест. */
    public static void tryTurnIn(ServerPlayer player, int commanderId) {
        IPlayerStats stats = (IPlayerStats) player;

        // Валидация по КООРДИНАТАМ, а не по UUID сущности: цель выбирается по фиксированной
        // точке (см. startDeliveryQuest), сущность там разрешается только сейчас, когда
        // игрок и так стоит рядом с ней (чанк гарантированно загружен).
        Entity commanderEntity = commanderId >= 0 ? player.level().getEntity(commanderId) : null;
        double dx = commanderEntity != null ? commanderEntity.getX() - stats.getDeliveryTargetX() : Double.MAX_VALUE;
        double dz = commanderEntity != null ? commanderEntity.getZ() - stats.getDeliveryTargetZ() : Double.MAX_VALUE;
        final double MAX_TURN_IN_DISTANCE = 50.0;
        boolean isTarget = commanderEntity != null
                && commanderEntity.getTags().contains("quest_commander")
                && (dx * dx + dz * dz) <= MAX_TURN_IN_DISTANCE * MAX_TURN_IN_DISTANCE;
        if (!isTarget) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.delivery.wrong_recipient").withStyle(ChatFormatting.RED));
            return;
        }

        ItemStack crate = findSealedCrate(player);
        if (crate == null) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.delivery.missing_crate").withStyle(ChatFormatting.RED));
            return;
        }

        crate.shrink(1);
        clearLocator(player);

        Wall destWall = Wall.fromNameSafe(stats.getDeliveryTargetWall());
        if (destWall != null) {
            stats.addReputation(destWall, ReputationHandler.QUEST_REP_GAIN);
        }

        giveReward(player);
        // Задание засчитывается в статистику — раньше addQuestCompleted() стоял только на
        // сдаче вводного задания, и доставка, спасение и выживший не считались вовсе.
        stats.addQuestCompleted();
        clearDeliveryState(stats);

        player.sendSystemMessage(Component.translatable("message.maxonaddon.delivery.success").withStyle(ChatFormatting.GREEN));
    }

    @Nullable
    private static ItemStack findSealedCrate(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (isSealedCrate(stack, player)) return stack;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isSealedCrate(stack, player)) return stack;
        }
        return null;
    }

    private static boolean isSealedCrate(ItemStack stack, ServerPlayer owner) {
        if (!stack.is(MaxonDaotAddon.ITEM_BOX_ITEM)) return false;
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return false;

        CompoundTag tag = data.copyTag();
        if (!tag.getBoolean("MaxonDeliveryBox")) return false;
        if (tag.getBoolean("MaxonSealBroken")) return false;
        return owner.getUUID().toString().equals(tag.getString("MaxonDeliveryOwnerUuid"));
    }

    private static void giveReward(ServerPlayer player) {
        giveOrDrop(player, new ItemStack(daot.DannysAot.ICE_BURST_CLUSTER, REWARD_ICE));

        for (int i = 0; i < REWARD_BLADES; i++) {
            ItemStack blade = new ItemStack(daot.DannysAot.BLADE);
            if (blade.getItem() instanceof daot.BladeItem) {
                daot.BladeItem.setBladeState(blade, daot.BladeItem.BladeState.FRESH);
            }
            giveOrDrop(player, blade);
        }

        giveOrDrop(player, new ItemStack(Items.EMERALD, REWARD_EMERALDS));
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void failQuest(ServerPlayer player, String messageKey) {
        IPlayerStats stats = (IPlayerStats) player;

        Wall originWall = Wall.fromNameSafe(stats.getActiveQuestWall());
        Wall destWall = Wall.fromNameSafe(stats.getDeliveryTargetWall());

        if (originWall != null) {
            stats.addReputation(originWall, -FAIL_REP_PENALTY);
        }
        if (destWall != null && destWall != originWall) {
            stats.addReputation(destWall, -FAIL_REP_PENALTY);
        }

        clearLocator(player);
        clearDeliveryState(stats);

        player.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_RED));

        ServerPlayNetworking.send(player, new SyncQuestPayload(
                stats.getActiveQuestId(), stats.getActiveQuestTarget(), stats.getQuestTitansKilled(),
                stats.getTestQuestCompletions(), stats.getDeliveryDeadlineTick(), stats.getTestQuestCommanderMask()
        ));
    }

    /**
     * Отказ от уже взятого задания (кнопка "Отказаться" в журнале). Ящик припасов забираем
     * обратно — иначе отказ был бы способом бесплатно оставить себе клинки/газ/копья из него.
     * Репутационный штраф за отказ начисляет вызывающий код (см. MaxonDaotAddon, action == 1):
     * он единый для всех заданий и заметно меньше штрафа за сорванную пломбу.
     */
    public static void abandon(ServerPlayer player) {
        removeDeliveryCrate(player);
        clearLocator(player);
        clearDeliveryState((IPlayerStats) player);
    }

    /** В отличие от findSealedCrate — забирает ящик независимо от того, сорвана пломба или нет. */
    private static void removeDeliveryCrate(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (isDeliveryCrateOf(stack, player)) {
                stack.setCount(0);
                return;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isDeliveryCrateOf(stack, player)) {
                stack.setCount(0);
                return;
            }
        }
    }

    private static boolean isDeliveryCrateOf(ItemStack stack, ServerPlayer owner) {
        if (!stack.is(MaxonDaotAddon.ITEM_BOX_ITEM)) return false;
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return false;

        CompoundTag tag = data.copyTag();
        if (!tag.getBoolean("MaxonDeliveryBox")) return false;
        return owner.getUUID().toString().equals(tag.getString("MaxonDeliveryOwnerUuid"));
    }

    private static void clearDeliveryState(IPlayerStats stats) {
        stats.setActiveQuest("", "");
        stats.setQuestTitansKilled(0);
        stats.setActiveQuestWall("");
        stats.setDeliveryTarget(0, 0, 0);
        stats.setDeliveryTargetWall("");
        stats.setDeliveryDeadlineTick(0L);
    }
}
