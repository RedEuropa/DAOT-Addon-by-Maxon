package com.maxonaddon;

import com.maxonaddon.network.HorseActionPayload;
import com.maxonaddon.network.HorseShopPayload;
import com.maxonaddon.util.HorseOffer;
import com.maxonaddon.util.IPlayerStats;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Лавка лошадей у перевозчика: предложение, покупка, переброс статов и свист.
 */
public final class HorseTradeHandler {

    /** Дальше этого расстояния лошадь свист не слышит. */
    private static final double WHISTLE_RANGE = 250.0;

    /** Ближе этого лошадь не телепортируется — просто прискачет своим ходом. */
    private static final double NEAR_DISTANCE = 40.0;
    /** На таком удалении лошадь появляется по свисту — дальше она добегает сама. */
    private static final double ARRIVAL_SPAWN_DISTANCE = 30.0;
    /** Скорость бега на зов: галопом, а не прогулочным шагом. */
    private static final double WHISTLE_RUN_SPEED = 2.0;
    /** Подбежала на столько — считаем, что прискакала. */
    private static final double ARRIVAL_DISTANCE = 4.0;
    /** Метка «подозвана»: пока висит, лошадь ведут к хозяину. */
    private static final String WHISTLED_TAG = "maxon_whistled";

    private HorseTradeHandler() {}

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(HorseTradeHandler::tick);

        ServerPlayNetworking.registerGlobalReceiver(HorseActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player == null) return;

                switch (payload.action()) {
                    case HorseActionPayload.OPEN -> sendOffer(player);
                    case HorseActionPayload.BUY -> buy(player, payload.index());
                    case HorseActionPayload.REROLL -> reroll(player);
                    case HorseActionPayload.WHISTLE -> whistle(player);
                    default -> { }
                }
            });
        });
    }

    /**
     * Раз в секунду: запоминаем, на какой лошади игрок ехал последней, и доводим до него ту,
     * которую он подозвал.
     * <p>
     * Владельцем считается тот, кто лошадь приручил И на неё садился: свист зовёт последнюю
     * такую лошадь, а не только купленную у перевозчика.
     */
    private static void tick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            rememberRiddenHorse(player);
            leadWhistledHorse(player);
        }
    }

    private static void rememberRiddenHorse(ServerPlayer player) {
        if (!(player.getVehicle() instanceof Horse horse)) return;
        if (!horse.isTamed()) return;
        if (!player.getUUID().equals(horse.getOwnerUUID())) return;

        IPlayerStats stats = (IPlayerStats) player;
        String uuid = horse.getUUID().toString();
        if (!uuid.equals(stats.getOwnedHorseUuid())) {
            stats.setOwnedHorseUuid(uuid);
        }
    }

    /**
     * Ведёт подозванную лошадь к хозяину.
     * <p>
     * Одного moveTo в момент свиста мало: путь строится в пределах дальности слежения
     * (около 32 блоков) и теряется, стоит игроку отойти или лошади упереться в рельеф.
     * Поэтому цель обновляем, пока она не добежит, а добежав — снимаем метку.
     */
    private static void leadWhistledHorse(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        String uuidText = stats.getOwnedHorseUuid();
        if (uuidText.isEmpty()) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        Entity entity;
        try {
            entity = level.getEntity(UUID.fromString(uuidText));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!(entity instanceof Horse horse) || !horse.isAlive()) return;
        if (!horse.getTags().contains(WHISTLED_TAG)) return;

        // Прибежала или её оседлали — вести больше некуда.
        if (horse.distanceTo(player) <= ARRIVAL_DISTANCE || horse.isVehicle()) {
            horse.removeTag(WHISTLED_TAG);
            horse.getNavigation().stop();
            return;
        }
        horse.getNavigation().moveTo(player, WHISTLE_RUN_SPEED);
    }

    /** Отдаёт клиенту актуальный seed, при необходимости собрав предложение впервые. */
    public static void sendOffer(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        if (stats.getHorseOfferSeed() == 0L) {
            stats.setHorseOfferSeed(newSeed(player));
        }
        ServerPlayNetworking.send(player,
                new HorseShopPayload(stats.getHorseOfferSeed(), !stats.getOwnedHorseUuid().isEmpty()));
    }

    private static long newSeed(ServerPlayer player) {
        // Ноль зарезервирован под "предложения ещё нет", поэтому его избегаем.
        long seed = player.level().getGameTime() * 31L + player.getRandom().nextLong();
        return seed == 0L ? 1L : seed;
    }

    private static void reroll(ServerPlayer player) {
        int price = com.maxonaddon.perk.PerkManager.discountedPrice(player, HorseOffer.REROLL_PRICE);
        if (!take(player, Items.EMERALD, price)) {
            player.displayClientMessage(Component.translatable(
                    "message.maxonaddon.horse.no_emeralds", price).withStyle(ChatFormatting.RED), true);
            return;
        }

        IPlayerStats stats = (IPlayerStats) player;
        stats.setHorseOfferSeed(newSeed(player));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        sendOffer(player);
    }

    private static void buy(ServerPlayer player, int index) {
        if (index < 0 || index >= HorseOffer.COUNT) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        IPlayerStats stats = (IPlayerStats) player;
        if (stats.getHorseOfferSeed() == 0L) {
            sendOffer(player);
            return;
        }

        // Статы считаем на сервере из того же seed, что видел клиент. Подменить их пакетом
        // нельзя: в нём едет только номер лошади.
        HorseOffer offer = HorseOffer.generate(stats.getHorseOfferSeed(), index);
        int priceBlocks = com.maxonaddon.perk.PerkManager.discountedPrice(player, offer.priceBlocks());
        if (!take(player, Items.EMERALD_BLOCK, priceBlocks)) {
            player.displayClientMessage(Component.translatable(
                    "message.maxonaddon.horse.no_emerald_blocks", priceBlocks).withStyle(ChatFormatting.RED), true);
            return;
        }

        Horse horse = EntityType.HORSE.create(level);
        if (horse == null) return;

        Vec3 spawn = player.position();
        horse.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot(), 0.0F);

        horse.setVariant(net.minecraft.world.entity.animal.horse.Variant.values()[
                Math.floorMod(offer.variantIndex(), HorseOffer.VARIANT_COUNT)]);

        applyAttribute(horse, Attributes.MOVEMENT_SPEED, offer.speed());
        applyAttribute(horse, Attributes.JUMP_STRENGTH, offer.jump());
        applyAttribute(horse, Attributes.MAX_HEALTH, offer.health());
        horse.setHealth((float) offer.health());

        // Куплена — значит уже объезжена и принадлежит покупателю.
        horse.setTamed(true);
        horse.setOwnerUUID(player.getUUID());
        horse.setPersistenceRequired();

        level.addFreshEntity(horse);
        stats.setOwnedHorseUuid(horse.getUUID().toString());

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.maxonaddon.horse.bought").withStyle(ChatFormatting.GREEN), true);

        // Предложение пересобираем: иначе одну и ту же удачную лошадь можно было бы
        // скупать сколько угодно раз подряд.
        stats.setHorseOfferSeed(newSeed(player));
        sendOffer(player);
    }

    /** Свист: подтягивает купленную лошадь к игроку, если она в пределах слышимости. */
    public static void whistle(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        String uuidText = stats.getOwnedHorseUuid();
        if (uuidText.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.maxonaddon.horse.no_horse").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) return;

        UUID horseUuid;
        try {
            horseUuid = UUID.fromString(uuidText);
        } catch (IllegalArgumentException e) {
            stats.setOwnedHorseUuid("");
            return;
        }

        Entity entity = level.getEntity(horseUuid);
        // Лошадь мертва, в другом измерении или в невыгруженных чанках — для игрока это
        // всё один случай: не отзывается.
        if (!(entity instanceof Horse horse) || !horse.isAlive() || horse.distanceTo(player) > WHISTLE_RANGE) {
            player.displayClientMessage(Component.translatable("message.maxonaddon.horse.too_far").withStyle(ChatFormatting.RED), true);
            return;
        }

        // Лошадь и так неподалёку — незачем её дёргать телепортом, прискачет сама.
        // Телепорт нужен только чтобы сократить путь издалека, а не чтобы подменять дорогу.
        if (horse.distanceTo(player) > NEAR_DISTANCE) {
            Vec3 spot = findSpotBehind(level, player, horse);
            if (spot == null) {
                // Вокруг сплошные стены — честнее сказать, что лошадь не подойдёт, чем
                // впечатать её в блоки.
                player.displayClientMessage(
                        Component.translatable("message.maxonaddon.horse.no_room").withStyle(ChatFormatting.RED), true);
                return;
            }
            horse.teleportTo(spot.x, spot.y, spot.z);
        }

        // Дальше в обоих случаях одинаково: скачет к хозяину сама. Метка держит её на курсе
        // (см. leadWhistledHorse).
        horse.addTag(WHISTLED_TAG);
        horse.getNavigation().moveTo(player, WHISTLE_RUN_SPEED);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                com.maxonaddon.sound.ModSounds.WHISTLING, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.maxonaddon.horse.whistled").withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Ищет место примерно в ARRIVAL_SPAWN_DISTANCE блоках от игрока, откуда лошадь прискачет
     * сама.
     * <p>
     * Высоту берём heightmap'ом, а не перебором вокруг игрока: иначе на холме или в пещере
     * лошадь оказывалась бы в воздухе или внутри породы. Точка годится, только если в неё
     * целиком помещается габарит лошади, под копытами есть опора и это не вода — тонуть по
     * свисту она не должна.
     * <p>
     * Обходим круг целиком, начиная со стороны за спиной: появиться сзади приятнее, но если
     * там обрыв или скала — лучше любая другая сторона, чем отказ.
     */
    private static Vec3 findSpotBehind(ServerLevel level, ServerPlayer player, Horse horse) {
        Vec3 look = player.getLookAngle();
        double backX = -look.x;
        double backZ = -look.z;
        double length = Math.sqrt(backX * backX + backZ * backZ);
        if (length < 1.0E-4) {
            // Игрок смотрит строго вверх или вниз — направления «назад» по горизонтали нет,
            // берём любое.
            backX = 1.0;
            backZ = 0.0;
        } else {
            backX /= length;
            backZ /= length;
        }

        for (double angleDeg : new double[]{0.0, 30.0, -30.0, 60.0, -60.0, 90.0, -90.0,
                                            120.0, -120.0, 150.0, -150.0, 180.0}) {
            double angle = Math.toRadians(angleDeg);
            double dirX = backX * Math.cos(angle) - backZ * Math.sin(angle);
            double dirZ = backX * Math.sin(angle) + backZ * Math.cos(angle);

            for (double distance : new double[]{ARRIVAL_SPAWN_DISTANCE, 27.0, 33.0, 22.0, 38.0}) {
                int x = (int) Math.round(player.getX() + dirX * distance);
                int z = (int) Math.round(player.getZ() + dirZ * distance);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                if (fits(level, horse, x + 0.5, y, z + 0.5)) {
                    return new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    /** Помещается ли лошадь в точку, есть ли под ней опора и не вода ли это. */
    private static boolean fits(ServerLevel level, Horse horse, double x, double y, double z) {
        if (!level.noCollision(horse, horse.getType().getDimensions().makeBoundingBox(x, y, z))) {
            return false;
        }
        // Опора под копытами: иначе лошадь появится в воздухе и полетит вниз.
        net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(x, y - 0.1, z);
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;

        // Ни в воде, ни в лаве: подозванная лошадь не должна захлебнуться или сгореть.
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(x, y, z);
        return level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty()
                && level.getFluidState(below).isEmpty();
    }

    private static void applyAttribute(Horse horse, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = horse.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    /** Списывает count предметов item и только если их хватает; при нехватке не трогает ничего. */
    private static boolean take(ServerPlayer player, Item item, int count) {
        if (player.getInventory().countItem(item) < count) return false;
        player.getInventory().clearOrCountMatchingItems(
                (ItemStack stack) -> stack.is(item), count, player.inventoryMenu.getCraftSlots());
        return true;
    }
}
