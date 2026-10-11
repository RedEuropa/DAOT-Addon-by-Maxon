package com.maxonaddon.util;

import com.maxonaddon.MaxonConfig;
import daot.BladeItem;
import daot.DannysAot;
import daot.HoodTracker;
import daot.ODMGearItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.Mob;

/**
 * Чёрный рынок контрабандистов: капюшон-маскировка (ночью) + ассортимент торговли.
 * УПМ продаётся уже подбитым — по НАШЕЙ кастомной прочности (ODMDamageManager, максимум 500),
 * а не по ванильной. Заряженный клинок как самый дорогой товар из продажи убран —
 * вместо него баллон с газом на 100 единиц, дешевле УПМ.
 */
public final class SmugglerTradeHelper {

    private static final String HOOD_UP_TAG = "hood_up";

    private SmugglerTradeHelper() {}

    /**
     * Единая точка правды "стать контрабандистом" — используется автоспавном
     * (VillagerEntityMixin), командой /spawnsmuggler и CityFactionGuard, чтобы
     * не дублировать (и не рассинхронизировать) логику по трём местам.
     *
     * Сразу же, а не только на следующем тике, стирает память о заявленной кровати
     * и будит жителя, если он спит — это важно для команды/гварда, которые создают
     * сущность одним махом: без этого ванильный mobInteract() отказывает в торговле,
     * если isSleeping() == true (проверено байткодом Villager.mobInteract), и до первого
     * customServerAiStep() был крошечный, но реальный зазор.
     */
    public static void markAsSmuggler(Villager v) {
        v.addTag("smuggler");
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NITWIT));

        var speedAttr = v.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(MaxonConfig.get().smugglerFleeSpeed);
        }

        if (v.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
            v.getBrain().eraseMemory(MemoryModuleType.HOME);
        }
        if (v.isSleeping()) {
            v.stopSleeping();
        }

        if (NightDutyHelper.isNight(v.level())) {
            putHoodOn(v);
            applyOffers(v);
        }
    }

    /** Надевает капюшон-маскировку (чёрный плащ) на голову и синхронизирует визуал капюшона. */
    public static void putHoodOn(LivingEntity entity) {
        ItemStack cloak = new ItemStack(DannysAot.BLACK_CLOAK);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(HOOD_UP_TAG, true);
        cloak.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        entity.setItemSlot(EquipmentSlot.HEAD, cloak);

        // Проверяем, является ли сущность мобом, у которого можно настроить шанс дропа
        if (entity instanceof Mob mob) {
            mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }

        HoodTracker.setClientHoodState(entity.getUUID(), true);
    }

    /** Снимает маскировку — днём контрабандист неотличим от обычного жителя. */
    public static void takeHoodOff(LivingEntity entity) {
        entity.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        HoodTracker.setClientHoodState(entity.getUUID(), false);
    }

    public static boolean isHoodOn(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(DannysAot.BLACK_CLOAK);
    }

    /**
     * Собирает ассортимент чёрного рынка и сразу выставляет его как торговые предложения жителя.
     *
     * ВАЖНО: Villager#overrideOffers() в этой версии игры — пустая заглушка (проверено байткодом
     * AbstractVillager.class: тело метода буквально "return;", ничего не делает). Именно поэтому
     * торговля никогда не открывалась. MerchantOffers extends ArrayList<MerchantOffer> — это
     * настоящий изменяемый список, и getOffers() возвращает ссылку на РЕАЛЬНОЕ внутреннее поле
     * сущности (не копию), поэтому правильный способ — мутировать сам список напрямую.
     * Можно звать только на сервере — getOffers() кидает исключение на клиенте.
     */
    public static void applyOffers(Villager smuggler) {
        applyOffers(smuggler, false);
    }

    /**
     * betterQuality — дружеская репутация (>= ReputationHelper.FRIEND_THRESHOLD) со стеной этого
     * контрабандиста: товар лучше (больше газа/прочности) и дешевле (см. WallHelper/ReputationHandler,
     * который зовёт это при взаимодействии игрока с уже открытым капюшоном контрабандистом).
     */
    public static void applyOffers(Villager smuggler, boolean betterQuality) {
        applyOffers(smuggler, betterQuality, 0.0);
    }

    /**
     * Тот же ассортимент, но с учётом скидки торговца: покупки дешевле, скупка щедрее.
     * Скидка приходит долей от 0 до 1 (см. PerkManager#tradeDiscount) — сам helper про
     * перки ничего не знает и остаётся пригодным для вызова без игрока.
     */
    public static void applyOffers(Villager smuggler, boolean betterQuality, double tradeDiscount) {
        MerchantOffers current = smuggler.getOffers();
        current.clear();
        current.addAll(buildOffers(betterQuality, tradeDiscount));
    }

    /**
     * Убирает все предложения (днём). Без этого профессия NITWIT всё равно работала бы
     * как настоящий торговец — а с пустым списком vanilla-код сам делает то самое
     * "не знаю никаких торгов" (мотание головой), как у обычного нитвита.
     */
    public static void clearOffers(Villager smuggler) {
        smuggler.getOffers().clear();
    }

    private static MerchantOffers buildOffers(boolean betterQuality) {
        return buildOffers(betterQuality, 0.0);
    }

    private static MerchantOffers buildOffers(boolean betterQuality, double tradeDiscount) {
        MerchantOffers offers = new MerchantOffers();

        // УПМ — подбитое, наша кастомная прочность (не ванильная), см. ODMDamageManager.
        Item odmGear = DannysAot.ODM_GEAR;
        ItemStack odmStack = new ItemStack(odmGear != Items.AIR ? odmGear : Items.LEATHER_LEGGINGS);
        if (odmGear instanceof ODMGearItem) {
            int maxGas = ODMGearItem.MAX_GAS;
            int baseGas = MaxonConfig.get().smugglerOdmGearGas;
            int gas = Math.min(maxGas, betterQuality ? baseGas * 2 : baseGas);
            ODMGearItem.setGas(odmStack, gas);
        }
        ODMDamageManager.initDurability(odmStack);
        int baseDurability = MaxonConfig.get().smugglerOdmGearDurability;
        ODMDamageManager.setDurability(odmStack, betterQuality ? Math.min(500, baseDurability * 2) : baseDurability);
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, priceFor(6, betterQuality, tradeDiscount)), odmStack, 999, 0, 0.02F));

        // Ботинки УПМ — целые, отдельного износа у них нет.
        Item odmBoots = DannysAot.ODM_BOOTS;
        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, priceFor(3, betterQuality, tradeDiscount)),
                new ItemStack(odmBoots != Items.AIR ? odmBoots : Items.LEATHER_BOOTS),
                999, 0, 0.02F
        ));

        // Клинок — с дружеской репутацией сразу заряженный (FRESH), иначе только рукоять (EMPTY).
        Item blade = DannysAot.BLADE;
        ItemStack bladeStack = new ItemStack(blade != Items.AIR ? blade : Items.IRON_SWORD);
        if (blade instanceof BladeItem) {
            BladeItem.setBladeState(bladeStack, betterQuality ? BladeItem.BladeState.FRESH : BladeItem.BladeState.EMPTY);
        }
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, priceFor(2, betterQuality, tradeDiscount)), bladeStack, 999, 0, 0.02F));

        // Баллон с газом — на 100 единиц обычно, полный при дружеской репутации.
        Item gasCanister = DannysAot.GAS_CANISTER;
        ItemStack gasStack = new ItemStack(gasCanister != Items.AIR ? gasCanister : Items.GLASS_BOTTLE);
        if (gasCanister instanceof daot.GasCanisterItem) {
            int maxGas = daot.GasCanisterItem.MAX_GAS;
            daot.GasCanisterItem.setGas(gasStack, betterQuality ? maxGas : Math.min(maxGas, 100));
        }
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, priceFor(4, betterQuality, tradeDiscount)), gasStack, 999, 0, 0.02F));

        // СКУПКА: контрабандист берёт УПМ в любом состоянии — хоть пустое, хоть убитое в ноль.
        // ItemCost без предиката компонентов сверяет только сам предмет, а газ и наша кастомная
        // прочность лежат в CustomData, поэтому состояние на сделку не влияет. Цена фиксированная
        // и не пляшет от репутации (priceMultiplier = 0), иначе скупка то и дело съезжала бы
        // на изумруд в обе стороны.
        if (odmGear != Items.AIR) {
            offers.add(new MerchantOffer(new ItemCost(odmGear, 1), new ItemStack(Items.EMERALD, payoutFor(2, tradeDiscount)), 999, 0, 0.0F));
        }

        // Баллон скупают по той же схеме — за изумруд и тоже независимо от остатка газа:
        // он лежит в CustomData и на сверку предмета в ItemCost не влияет.
        if (gasCanister != Items.AIR) {
            offers.add(new MerchantOffer(new ItemCost(gasCanister, 1), new ItemStack(Items.EMERALD, payoutFor(1, tradeDiscount)), 999, 0, 0.0F));
        }

        return offers;
    }

    /** Цена покупки: сперва скидка за дружбу, затем доля скидки торговца. Ниже 1 не падает. */
    private static int priceFor(int base, boolean betterQuality, double tradeDiscount) {
        int price = discounted(base, betterQuality);
        if (tradeDiscount <= 0.0) return price;
        return Math.max(1, (int) Math.round(price * (1.0 - tradeDiscount)));
    }

    /** Выручка за сданный игроком товар — торговец выторговывает больше. */
    private static int payoutFor(int base, double tradeDiscount) {
        if (tradeDiscount <= 0.0) return base;
        return (int) Math.round(base * (1.0 + tradeDiscount));
    }

    /** Скидка на 1 изумруд (не ниже 1) при дружеской репутации со стеной этого контрабандиста. */
    private static int discounted(int basePrice, boolean betterQuality) {
        return betterQuality ? Math.max(1, basePrice - 1) : basePrice;
    }
}
