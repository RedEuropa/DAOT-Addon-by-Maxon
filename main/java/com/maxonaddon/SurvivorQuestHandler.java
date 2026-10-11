package com.maxonaddon;

import com.maxonaddon.network.SurvivorGuiPayload;
import com.maxonaddon.network.SyncQuestPayload;
import com.maxonaddon.network.SyncSurvivorPayload;
import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.Wall;
import com.maxonaddon.util.WallHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * Квест "Раненый выживший": на аванпостах разбойников (см. BanditOutpostGenerator) сидит на
 * крыше один раненый житель и периодически пускает фиолетовую сигнальную ракету. Игрок может
 * заговорить с ним, взять задание (донести до любого командира в городе) или отказать —
 * отказ стоит репутации.
 * <p>
 * ФИОЛЕТОВАЯ РАКЕТА СПЕЦИАЛЬНО БЕЗ ВЛАДЕЛЬЦА: в базовом моде фиолетовая ракета — это вызов
 * подкрепления разведки, но наш же FlareProjectileEntityMixin вызывает
 * ReinforcementsHandler.callScouts ТОЛЬКО если getOwner() — ServerPlayer. Ракета, выпущенная
 * жителем (owner остаётся null), выглядит и звучит как настоящий сигнал бедствия, но отряд
 * скаутов не призывает — ровно то поведение, которое тут нужно.
 * <p>
 * ПОЗА СИДЕНИЯ до принятия задания сделана посадкой жителя на невидимый ArmorStand-"стул"
 * (marker, без хитбокса — чтобы клик попадал по жителю, а не по стенду): жители в этом моде
 * рендерятся моделью игрока (PlayerVillagerRenderer), а LivingEntityRenderer сам ставит
 * модель в позу сидения для любого пассажира — отдельной анимации не требуется.
 * <p>
 * ПЕРЕНОСКА НА СПИНЕ переиспользует наручники базового мода: HandcuffsTracker.cuffEntity
 * останавливает жителю AI и вешает тег "handcuffed", а daot.mixin.HandcuffsPassengerPosition
 * Mixin уже умеет правильно располагать жителя-пассажира на спине игрока (в т.ч. с учётом
 * УПМ в слоте поножей). Саму посадку (startRiding) базовый мод ни от чего не инициирует —
 * это делаем мы по интеракту.
 */
public final class SurvivorQuestHandler {

    public static final String QUEST_ID = "survivor_quest";

    /** Тег раненого жителя — по нему его узнают интеракт и периодический сигнал бедствия. */
    public static final String SURVIVOR_TAG = "wounded_survivor";
    /** Житель уже обещан другому игроку. Тег (а не карта в памяти) — чтобы пережить рестарт сервера. */
    private static final String BUSY_TAG = "survivor_busy";
    /** Невидимый ArmorStand-"стул" под сидящим жителем. */
    private static final String SEAT_TAG = "survivor_seat";

    private static final int DECLINE_REP_PENALTY = 5;
    private static final int REWARD_REP = 8;
    private static final int REWARD_EMERALDS = 2;

    private static final int FLARE_INTERVAL_TICKS = 600; // раз в 30 секунд
    private static final double FLARE_PLAYER_RADIUS = 160.0;
    private static final int LOCATOR_INTERVAL_TICKS = 20;
    private static final double TURN_IN_RADIUS = 12.0;
    private static final float WOUNDED_HEALTH = 6.0F;

    private static final ResourceKey<Level> PARADIS_KEY =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));

    private static int flareTickCounter = 0;
    private static int locatorTickCounter = 0;

    private SurvivorQuestHandler() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!com.maxonaddon.MaxonConfig.isReputationEnabled()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!(entity instanceof Villager villager) || !villager.getTags().contains(SURVIVOR_TAG)) return InteractionResult.PASS;

            onInteract(serverPlayer, villager);
            return InteractionResult.CONSUME;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++flareTickCounter >= FLARE_INTERVAL_TICKS) {
                flareTickCounter = 0;
                fireDistressFlares(server);
            }
            if (++locatorTickCounter >= LOCATOR_INTERVAL_TICKS) {
                locatorTickCounter = 0;
                updateLocators(server);
            }
        });
    }

    // ===============================================
    // СПАВН (зовётся из BanditOutpostGenerator при постройке аванпоста)
    // ===============================================

    /** Сажает раненого выжившего в позу сидения на pos и сразу пускает первый сигнал бедствия. */
    public static void spawnWoundedSurvivor(ServerLevel level, BlockPos pos) {
        // Квесты выключены конфигом — на аванпостах просто не будет никого (сама башня с
        // лутом при этом генерируется как обычно, см. BanditOutpostGenerator).
        if (!com.maxonaddon.MaxonConfig.isReputationEnabled()) return;

        Villager survivor = EntityType.VILLAGER.create(level);
        if (survivor == null) return;

        survivor.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        survivor.setVillagerData(survivor.getVillagerData().setProfession(VillagerProfession.NITWIT));
        survivor.setNoAi(true);
        survivor.setPersistenceRequired();
        survivor.addTag(SURVIVOR_TAG);
        survivor.setItemSlot(EquipmentSlot.CHEST, new ItemStack(daot.DannysAot.UNIFORM));
        survivor.setHealth(WOUNDED_HEALTH); // он ранен — это видно по полоске здоровья
        level.addFreshEntity(survivor);

        ArmorStand seat = createSeat(level, pos);
        if (seat != null) {
            survivor.startRiding(seat, true);
        }

        fireDistressFlare(level, pos);
    }

    /**
     * Невидимый "стул". Marker выставляем через NBT, а не через ArmorStand#setMarker — тот
     * приватный, а рефлексия по имени метода сломалась бы после ремапа в проде (имена
     * NBT-ключей, в отличие от имён методов, не ремапятся никогда).
     */
    private static ArmorStand createSeat(ServerLevel level, BlockPos pos) {
        ArmorStand seat = EntityType.ARMOR_STAND.create(level);
        if (seat == null) return null;

        seat.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        seat.setInvisible(true);
        seat.setNoGravity(true);
        seat.setInvulnerable(true);
        seat.setSilent(true);
        seat.setNoBasePlate(true);
        seat.addTag(SEAT_TAG);

        CompoundTag tag = new CompoundTag();
        seat.saveWithoutId(tag);
        tag.putBoolean("Marker", true);
        seat.load(tag);

        level.addFreshEntity(seat);
        return seat;
    }

    /** Снимает жителя со "стула" и убирает сам стул — зовётся при принятии задания. */
    private static void removeSeat(Villager survivor) {
        Entity vehicle = survivor.getVehicle();
        survivor.stopRiding();
        if (vehicle instanceof ArmorStand && vehicle.getTags().contains(SEAT_TAG)) {
            vehicle.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    // ===============================================
    // СИГНАЛ БЕДСТВИЯ
    // ===============================================

    /** Ищем свободных раненых рядом с игроками (а не по всей карте) и пускаем им ракету. */
    private static void fireDistressFlares(MinecraftServer server) {
        ServerLevel paradis = server.getLevel(PARADIS_KEY);
        if (paradis == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != paradis) continue;

            AABB box = player.getBoundingBox().inflate(FLARE_PLAYER_RADIUS);
            List<Villager> survivors = paradis.getEntitiesOfClass(Villager.class, box, v ->
                    v.isAlive() && v.getTags().contains(SURVIVOR_TAG) && !v.getTags().contains(BUSY_TAG));
            for (Villager survivor : survivors) {
                fireDistressFlare(paradis, survivor.blockPosition());
            }
        }
    }

    private static void fireDistressFlare(ServerLevel level, BlockPos pos) {
        @SuppressWarnings("unchecked")
        EntityType<? extends net.minecraft.world.entity.projectile.AbstractArrow> flareType =
                (EntityType<? extends net.minecraft.world.entity.projectile.AbstractArrow>) daot.DannysAot.FLARE_PROJECTILE;
        daot.FlareProjectileEntity flare = new daot.FlareProjectileEntity(flareType, level);
        flare.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
        flare.setFlareColorOrdinal(daot.FlareCartridgeItem.FlareColor.PURPLE.ordinal());
        // Владельца НЕ ставим — см. шапку класса: иначе FlareProjectileEntityMixin призовёт скаутов.
        flare.shoot(0, 1.0, 0, 2.5F, 1.0F);
        level.addFreshEntity(flare);
    }

    // ===============================================
    // ИНТЕРАКТ
    // ===============================================

    private static void onInteract(ServerPlayer player, Villager survivor) {
        IPlayerStats stats = (IPlayerStats) player;
        boolean mine = QUEST_ID.equals(stats.getActiveQuestId())
                && survivor.getUUID().toString().equals(stats.getSurvivorUuid());

        if (mine) {
            toggleCarry(player, survivor);
            return;
        }

        // Обещан кому-то другому — этот игрок помочь уже не может.
        if (survivor.getTags().contains(BUSY_TAG)) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.taken_by_other").withStyle(ChatFormatting.RED));
            return;
        }

        if (!stats.getActiveQuestId().isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.already_has_quest").withStyle(ChatFormatting.RED));
            return;
        }

        ServerPlayNetworking.send(player, new SurvivorGuiPayload(survivor.getId()));
    }

    /** Посадить на спину или снять со спины — тот же интеракт, что и разговор, но когда задание уже взято. */
    private static void toggleCarry(ServerPlayer player, Villager survivor) {
        if (survivor.getVehicle() == player) {
            survivor.stopRiding();
            player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.put_down").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (player.isVehicle()) { // уже кого-то везёт (или сам на чём-то) — двоих не унести
            player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.hands_full").withStyle(ChatFormatting.RED));
            return;
        }

        survivor.stopRiding(); // на всякий случай слезаем со "стула", если он ещё остался
        if (survivor.startRiding(player, true)) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.picked_up").withStyle(ChatFormatting.GREEN));
        }
    }

    // ===============================================
    // ПРИНЯТЬ / ОТКАЗАТЬ (зовётся из обработчика SurvivorActionPayload)
    // ===============================================

    public static void handleAction(ServerPlayer player, int action, int survivorId) {
        // Тот же гейт, что и на интеракте — пакет может прийти от подменённого клиента.
        if (!com.maxonaddon.MaxonConfig.isReputationEnabled()) return;

        Entity entity = player.level().getEntity(survivorId);
        if (!(entity instanceof Villager survivor) || !survivor.getTags().contains(SURVIVOR_TAG)) return;
        if (survivor.distanceToSqr(player) > 100.0) return; // отошёл дальше 10 блоков — диалог уже неактуален

        if (action == 0) {
            accept(player, survivor);
        } else if (action == 1) {
            decline(player, survivor);
        }
    }

    private static void accept(ServerPlayer player, Villager survivor) {
        IPlayerStats stats = (IPlayerStats) player;
        if (!stats.getActiveQuestId().isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.already_has_quest").withStyle(ChatFormatting.RED));
            return;
        }
        if (survivor.getTags().contains(BUSY_TAG)) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.taken_by_other").withStyle(ChatFormatting.RED));
            return;
        }

        survivor.addTag(BUSY_TAG);
        removeSeat(survivor);
        // Наручники базового мода: они же останавливают жителю AI и дают базовому моду понять,
        // что этот пассажир — "конвоируемый" (см. шапку класса).
        daot.HandcuffsTracker.cuffEntity(survivor, player);

        stats.setActiveQuest(QUEST_ID, "");
        stats.setQuestTitansKilled(0);
        stats.setActiveQuestWall("");
        stats.setSurvivorUuid(survivor.getUUID().toString());
        stats.setSurvivorPos(survivor.blockPosition().getX(), survivor.blockPosition().getY(), survivor.blockPosition().getZ());

        sendLocator(player, survivor, false);
        syncGenericQuest(player, stats);
        player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.accepted").withStyle(ChatFormatting.GOLD));
    }

    private static void decline(ServerPlayer player, Villager survivor) {
        // Стену определяем по геометрии кольца, на котором стоит сам житель. Метод назван
        // "ForCommander", но по сути это и есть "стена по позиции сущности вне города" —
        // теги стены у выжившего не проставлены, поэтому сработает его fallback по радиусу.
        Wall wall = WallHelper.getWallForCommander(survivor);
        if (wall != null) {
            ((IPlayerStats) player).addReputation(wall, -DECLINE_REP_PENALTY);
        }
        player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.declined").withStyle(ChatFormatting.DARK_RED));
    }

    // ===============================================
    // СДАЧА (зовётся из MaxonDaotAddon при action == 2)
    // ===============================================

    public static void tryTurnIn(ServerPlayer player, int commanderId) {
        IPlayerStats stats = (IPlayerStats) player;

        Entity commanderEntity = commanderId >= 0 ? player.level().getEntity(commanderId) : null;
        if (commanderEntity == null || !commanderEntity.getTags().contains("quest_commander")) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.wrong_recipient").withStyle(ChatFormatting.RED));
            return;
        }

        Villager survivor = findSurvivor(player, stats);
        if (survivor == null || survivor.distanceToSqr(player) > TURN_IN_RADIUS * TURN_IN_RADIUS) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.not_with_you").withStyle(ChatFormatting.RED));
            return;
        }

        Wall wall = WallHelper.getWallForCommander(commanderEntity);
        if (wall != null) {
            stats.addReputation(wall, REWARD_REP);
        }
        giveOrDrop(player, new ItemStack(Items.EMERALD, REWARD_EMERALDS));
        refillWornOdm(player);

        // Доставлен — снимаем наручники и убираем жителя (его "приняли" в городе).
        daot.HandcuffsTracker.forceRemoveCuffed(survivor.getUUID());
        survivor.stopRiding();
        survivor.remove(Entity.RemovalReason.DISCARDED);

        clearLocator(player);
        stats.addQuestCompleted();
        clearSurvivorState(stats);
        syncGenericQuest(player, stats);
        player.sendSystemMessage(Component.translatable("message.maxonaddon.survivor.delivered").withStyle(ChatFormatting.GREEN));
    }

    private static Villager findSurvivor(ServerPlayer player, IPlayerStats stats) {
        if (stats.getSurvivorUuid().isEmpty()) return null;
        if (!(player.level() instanceof ServerLevel level)) return null;
        try {
            Entity entity = level.getEntity(UUID.fromString(stats.getSurvivorUuid()));
            return entity instanceof Villager villager && villager.isAlive() ? villager : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ===============================================
    // ЛОКАТОР
    // ===============================================

    private static void updateLocators(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IPlayerStats stats = (IPlayerStats) player;
            if (!QUEST_ID.equals(stats.getActiveQuestId())) continue;

            Villager survivor = findSurvivor(player, stats);
            if (survivor == null) {
                // Чанк с жителем выгрузился (или его убили) — стрелку не гасим, показываем
                // последнюю известную позицию, чтобы игрок мог вернуться и проверить.
                sendLocatorRaw(player, stats.getSurvivorX() + 0.5, stats.getSurvivorY(), stats.getSurvivorZ() + 0.5, false);
                continue;
            }

            BlockPos pos = survivor.blockPosition();
            stats.setSurvivorPos(pos.getX(), pos.getY(), pos.getZ());
            sendLocator(player, survivor, survivor.getVehicle() == player);
        }
    }

    private static void sendLocator(ServerPlayer player, Villager survivor, boolean carried) {
        sendLocatorRaw(player, survivor.getX(), survivor.getY(), survivor.getZ(), carried);
    }

    private static void sendLocatorRaw(ServerPlayer player, double x, double y, double z, boolean carried) {
        ServerPlayNetworking.send(player, new SyncSurvivorPayload(
                true, PARADIS_KEY.location().toString(), x, y, z, carried));
    }

    private static void clearLocator(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SyncSurvivorPayload(false, "", 0.0, 0.0, 0.0, false));
    }

    /** Зовётся при заходе игрока — восстановить маркер, если квест пережил перезаход. */
    public static void resendOnJoin(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        if (!QUEST_ID.equals(stats.getActiveQuestId())) return;
        sendLocatorRaw(player, stats.getSurvivorX() + 0.5, stats.getSurvivorY(), stats.getSurvivorZ() + 0.5, false);
    }

    // ===============================================
    // ПРОЧЕЕ
    // ===============================================

    private static void refillWornOdm(ServerPlayer player) {
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (legs.getItem() instanceof daot.ODMGearItem) {
            daot.ODMGearItem.setGas(legs, daot.ODMGearItem.MAX_GAS);
        } else if (legs.getItem() instanceof daot.ODMAPGItem) {
            daot.ODMAPGItem.setGas(legs, daot.ODMAPGItem.MAX_GAS);
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void syncGenericQuest(ServerPlayer player, IPlayerStats stats) {
        ServerPlayNetworking.send(player, new SyncQuestPayload(
                stats.getActiveQuestId(), stats.getActiveQuestTarget(), stats.getQuestTitansKilled(),
                stats.getTestQuestCompletions(), stats.getDeliveryDeadlineTick(), stats.getTestQuestCommanderMask()
        ));
    }

    /** Отказ от уже взятого задания (кнопка "Отказаться" в журнале) — освобождаем жителя для других. */
    public static void abandon(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        Villager survivor = findSurvivor(player, stats);
        if (survivor != null) {
            survivor.stopRiding();
            survivor.removeTag(BUSY_TAG);
            daot.HandcuffsTracker.forceRemoveCuffed(survivor.getUUID());
        }
        clearLocator(player);
        clearSurvivorState(stats);
    }

    private static void clearSurvivorState(IPlayerStats stats) {
        stats.setActiveQuest("", "");
        stats.setQuestTitansKilled(0);
        stats.setActiveQuestWall("");
        stats.setSurvivorUuid("");
        stats.setSurvivorPos(0, 0, 0);
    }
}
