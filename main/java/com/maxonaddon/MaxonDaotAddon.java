package com.maxonaddon;

import java.util.ArrayList;
import java.util.UUID;

import com.maxonaddon.command.SpawnCarriageCommand;
import com.maxonaddon.event.CarriageInteractHandler;
import com.maxonaddon.event.CarriageSpawner;
import com.maxonaddon.skills.ModSkill;
import com.maxonaddon.item.ThunderGunItem;
import com.maxonaddon.mixin.TestShifterTitanQteMixin;
import com.maxonaddon.network.VillagerPreshiftPayload;
import com.maxonaddon.util.IJawQteHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import com.maxonaddon.block.GasCisternaBlock;
import com.maxonaddon.block.entity.GasCisternaBlockEntity;
import com.maxonaddon.command.GarrisonSpawnCommand;
import com.maxonaddon.command.ScoutSpawnCommand;
import com.maxonaddon.command.StatsCommand;
import com.maxonaddon.entity.ModEntities;
import com.maxonaddon.entity.TitanBoulderEntity;
import com.maxonaddon.item.GasCisternaItem;
import com.maxonaddon.network.*;
import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.TraumaManager;
import daot.*;
import daot.network.ODMJamPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.maxonaddon.network.CarriageTeleportPayload;
import com.maxonaddon.util.ICarriageData;
import daot.BladeBlockTracker;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import com.maxonaddon.block.entity.SwordBoxBlockEntity;
import com.maxonaddon.menu.SwordBoxMenu;



public class MaxonDaotAddon implements ModInitializer {
	public static final String MOD_ID = "maxon-daot-addon";

	/**
	 * Штраф репутацией за ОТКАЗ от уже взятого задания (кнопка "Отказаться" в журнале).
	 * Намеренно мягче, чем за провал: например, сорванная пломба у доставки стоит 10 очков
	 * (и ещё 10 стене получателя), а честный отказ — только это.
	 */
	public static final int QUEST_ABANDON_REP_PENALTY = 3;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// У какого водителя-проводника игрок стоял последним — источник истины для расчёта
	// цены проезда (см. CarriagePricing), не доверяем origin, присланному с клиента.
	public static final java.util.Map<java.util.UUID, String> lastCarriageCityId = new java.util.concurrent.ConcurrentHashMap<>();


	public static final SimpleParticleType TITAN_STEAM = FabricParticleTypes.simple();
	public static final Block GAS_CISTERNA = new GasCisternaBlock(BlockBehaviour.Properties.of().noOcclusion());
	public static final Block ITEM_BOX = new com.maxonaddon.block.SwordBoxBlock(BlockBehaviour.Properties.of().noOcclusion());
	public static final Item GAS_CISTERNA_ITEM = new GasCisternaItem(GAS_CISTERNA, new Item.Properties());
	// Замените старую строчку ITEM_BOX_ITEM на эту:
	public static final Item ITEM_BOX_ITEM = new com.maxonaddon.item.SwordBoxItem(ITEM_BOX, new Item.Properties());
	public static final Item THUNDER_GUN_ITEM = new ThunderGunItem(new Item.Properties().stacksTo(1));
	public static final Item MARLEYAN_RIFLE_ITEM = new com.maxonaddon.item.MarleyanRifleItem(new Item.Properties().stacksTo(1));
	public static final BlockEntityType<GasCisternaBlockEntity> GAS_CISTERNA_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ResourceLocation.fromNamespaceAndPath(MOD_ID, "gas_cisterna"),
			FabricBlockEntityTypeBuilder.create(GasCisternaBlockEntity::new, GAS_CISTERNA).build()
	);
	public static final BlockEntityType<SwordBoxBlockEntity> SWORD_BOX_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ResourceLocation.fromNamespaceAndPath(MOD_ID, "item_box"),
			FabricBlockEntityTypeBuilder.create(SwordBoxBlockEntity::new, ITEM_BOX).build()
	);

	public static final MenuType<SwordBoxMenu> SWORD_BOX_MENU = Registry.register(
			BuiltInRegistries.MENU,
			ResourceLocation.fromNamespaceAndPath(MOD_ID, "item_box_menu"),
			new MenuType<>(SwordBoxMenu::new, FeatureFlags.DEFAULT_FLAGS)
	);

	// ExtendedScreenHandlerType, а не обычный MenuType: клиенту при открытии нужно передать
	// BlockPos цистерны (через net.minecraft.core.BlockPos.STREAM_CODEC), чтобы
	// GasCisternaScreen мог читать актуальный storedGas напрямую с клиентской сущности блока.
	public static final net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<com.maxonaddon.menu.GasCisternaMenu, net.minecraft.core.BlockPos> GAS_CISTERNA_MENU = Registry.register(
			BuiltInRegistries.MENU,
			ResourceLocation.fromNamespaceAndPath(MOD_ID, "gas_cisterna_menu"),
			new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
					com.maxonaddon.menu.GasCisternaMenu::new,
					net.minecraft.core.BlockPos.STREAM_CODEC
			)
	);

	public static final java.util.Map<java.util.UUID, Integer> blockStartTicks = new java.util.HashMap<>();
	public static final java.util.Map<java.util.UUID, Integer> activeReloads = new java.util.concurrent.ConcurrentHashMap<>();

	// === КУЛДАУН ДЛЯ ТИХОГО КЛИНА УПМ ===
	private static final java.util.Map<java.util.UUID, Long> silentJamCooldowns = new java.util.HashMap<>();

	@Override
	public void onInitialize() {
		LOGGER.info("Загрузка MaxonDaotAddon...");
		ModEntities.register();
		registerCommands();
		PayloadTypeRegistry.playS2C().register(LegCutSyncPayload.TYPE, LegCutSyncPayload.CODEC);
		com.maxonaddon.sound.ModSounds.init();
		PayloadTypeRegistry.playC2S().register(ThunderGunActionPayload.TYPE, ThunderGunActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.maxonaddon.network.MarleyanRifleActionPayload.TYPE, com.maxonaddon.network.MarleyanRifleActionPayload.CODEC);

		BiomeModifications.addSpawn(BiomeSelectors.all(), MobCategory.WATER_CREATURE, EntityType.SALMON, 15, 2, 5);
		BiomeModifications.addSpawn(BiomeSelectors.all(), MobCategory.WATER_CREATURE, EntityType.COD, 10, 3, 6);

		// Рыба только в открытой воде. Ванильное правило спавна водных животных смотрит лишь на
		// глубину относительно уровня моря, а генератор Парадиза копает подземные озёра и
		// затопленные пещеры — они в этот диапазон попадают, и рыба заводилась под землёй.
		// Проверяем небо над головой: в пещере его нет.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof net.minecraft.world.entity.animal.WaterAnimal fish)) return;
			if (entity.getType() != EntityType.SALMON && entity.getType() != EntityType.COD) return;
			// Заспавненную вручную (спавн-яйцом, командой) рыбу не трогаем.
			if (fish.hasCustomName() || fish.isPersistenceRequired()) return;

			if (!world.canSeeSky(fish.blockPosition())) {
				fish.discard();
			}
		});
		// Живность Парадиза. Веса и размеры групп взяты близкими к ванильным, чтобы стада
		// выглядели привычно и не забивали лимит спавна: овца и курица — обычные, корова
		// пореже, лошади — редкость, зато сразу табунком.
		//
		// Это ЕСТЕСТВЕННЫЙ спавн, он идёт всё время. Отдельно от него животные ставятся при
		// самой генерации чанка (см. WorldBoundaryMixin) — иначе новый мир был бы пустым до
		// первых спавнов.
		BiomeModifications.addSpawn(BiomeSelectors.all(), MobCategory.CREATURE, EntityType.CHICKEN, 10, 2, 4);
		BiomeModifications.addSpawn(BiomeSelectors.all(), MobCategory.CREATURE, EntityType.SHEEP, 12, 2, 4);
		BiomeModifications.addSpawn(BiomeSelectors.all(), MobCategory.CREATURE, EntityType.PIG, 10, 2, 4);
		BiomeModifications.addSpawn(BiomeSelectors.all(), MobCategory.CREATURE, EntityType.COW, 8, 2, 4);
		BiomeModifications.addSpawn(BiomeSelectors.all(), MobCategory.CREATURE, EntityType.HORSE, 5, 2, 5);



		ServerPlayNetworking.registerGlobalReceiver(ThunderGunActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				if (player == null) return;

				if (payload.actionType() == 0) {
					tryLoadThunderGun(player, InteractionHand.MAIN_HAND);
					tryLoadThunderGun(player, InteractionHand.OFF_HAND);
				} else if (payload.actionType() == 2) {
					tryUnloadThunderGun(player, InteractionHand.MAIN_HAND);
					tryUnloadThunderGun(player, InteractionHand.OFF_HAND);
				} else if (payload.actionType() == 1) {
					InteractionHand hand = (payload.hand() == 1) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
					ItemStack stack = player.getItemInHand(hand);

					// СТРОГАЯ ПРОВЕРКА: Разрешаем выстрел ТОЛЬКО нашему предмету
					if (stack.getItem() == MaxonDaotAddon.THUNDER_GUN_ITEM) {
						int loaded = com.maxonaddon.item.ThunderGunItem.getLoadedSpears(stack);
						if (loaded > 0 || player.isCreative()) {
							com.maxonaddon.item.ThunderGunItem.shootSpear(player.level(), player, stack, loaded);
						}
					}
				}
			});
		});

		// action: 0 = выстрел, 1 = перезарядка (см. MarleyanRifleActionPayload).
		ServerPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.MarleyanRifleActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				if (player == null) return;

				if (payload.action() == 0) {
					shootMarleyanRifle(player, InteractionHand.MAIN_HAND, payload.aiming());
				} else if (payload.action() == 1) {
					reloadMarleyanRifle(player, InteractionHand.MAIN_HAND);
				} else if (payload.action() == 2) {
					aimMarleyanRifle(player, InteractionHand.MAIN_HAND);
				} else if (payload.action() == 3) {
					unaimMarleyanRifle(player, InteractionHand.MAIN_HAND);
				} else if (payload.action() == 4) {
					drawMarleyanRifle(player, InteractionHand.MAIN_HAND);
				}
			});
		});




		// === 1. РЕГИСТРАЦИЯ СЕТЕВЫХ ПАКЕТОВ (СНАЧАЛА ЭТО) ===
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.SyncPerksPayload.TYPE, com.maxonaddon.network.SyncPerksPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.maxonaddon.network.EquipPerkPayload.TYPE, com.maxonaddon.network.EquipPerkPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.SyncUnlockedPerksPayload.TYPE, com.maxonaddon.network.SyncUnlockedPerksPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TitanHitSoundPayload.TYPE, TitanHitSoundPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestStatsPayload.TYPE, RequestStatsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ForceDetachPayload.TYPE, ForceDetachPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DashPayload.TYPE, DashPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TraumaAnimationPayload.TYPE, TraumaAnimationPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.GasReloadAnimPayload.TYPE, com.maxonaddon.network.GasReloadAnimPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(BackDashPayload.TYPE, BackDashPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestJamPayload.TYPE, RequestJamPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.maxonaddon.network.PullEntityPayload.TYPE, com.maxonaddon.network.PullEntityPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StartHostileQtePayload.TYPE, StartHostileQtePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StopHostileQtePayload.TYPE, StopHostileQtePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(HitQteKeyPayload.TYPE, HitQteKeyPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncFearStatsPayload.TYPE, SyncFearStatsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenStatsScreenPayload.TYPE, OpenStatsScreenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncModFlagsPayload.TYPE, SyncModFlagsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.ConfigSyncPayload.TYPE, com.maxonaddon.network.ConfigSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncBloodPayload.TYPE, SyncBloodPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BuySkillPayload.TYPE, BuySkillPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncSkillsPayload.TYPE, SyncSkillsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ResetSkillsPayload.TYPE, ResetSkillsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SilentJamPayload.TYPE, SilentJamPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncTraumaPayload.TYPE, SyncTraumaPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(VillagerPreshiftPayload.TYPE, VillagerPreshiftPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(JawQteStatePayload.TYPE, JawQteStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(JawQteInputPayload.TYPE, JawQteInputPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(VillagerHookSyncPayload.TYPE, VillagerHookSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CarriageGuiPayload.TYPE, CarriageGuiPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(CarriageTeleportPayload.TYPE, CarriageTeleportPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(OdmSupplyInteractPayload.TYPE, OdmSupplyInteractPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ReloadGearPayload.TYPE, ReloadGearPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(QuestGuiPayload.TYPE, QuestGuiPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.maxonaddon.network.QuestActionPayload.TYPE, com.maxonaddon.network.QuestActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.SyncQuestPayload.TYPE, com.maxonaddon.network.SyncQuestPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.SyncDeliveryTargetPayload.TYPE, com.maxonaddon.network.SyncDeliveryTargetPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.SyncRescuePayload.TYPE, com.maxonaddon.network.SyncRescuePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.SyncRescueStatusPayload.TYPE, com.maxonaddon.network.SyncRescueStatusPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.SurvivorGuiPayload.TYPE, com.maxonaddon.network.SurvivorGuiPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.maxonaddon.network.SurvivorActionPayload.TYPE, com.maxonaddon.network.SurvivorActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.SyncSurvivorPayload.TYPE, com.maxonaddon.network.SyncSurvivorPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.OutpostGuiPayload.TYPE, com.maxonaddon.network.OutpostGuiPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.maxonaddon.network.OutpostActionPayload.TYPE, com.maxonaddon.network.OutpostActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.RifleAnimPayload.TYPE, com.maxonaddon.network.RifleAnimPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.SmugglerGlowPayload.TYPE, com.maxonaddon.network.SmugglerGlowPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.HorseShopPayload.TYPE, com.maxonaddon.network.HorseShopPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.maxonaddon.network.HorseActionPayload.TYPE, com.maxonaddon.network.HorseActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.BerserkControlLossPayload.TYPE, com.maxonaddon.network.BerserkControlLossPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.maxonaddon.network.BerserkQteStatePayload.TYPE, com.maxonaddon.network.BerserkQteStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(com.maxonaddon.network.BerserkQteHitPayload.TYPE, com.maxonaddon.network.BerserkQteHitPayload.CODEC);


		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			com.maxonaddon.command.SpawnBossCommand.register(dispatcher);
			com.maxonaddon.command.StatsCommand.register(dispatcher);
			com.maxonaddon.command.RumblingCommand.register(dispatcher);
			com.maxonaddon.command.SpawnPoweredVillagerCommand.register(dispatcher);
			ScoutSpawnCommand.register(dispatcher);
			GarrisonSpawnCommand.register(dispatcher);
			com.maxonaddon.command.PoliceSpawnCommand.register(dispatcher);
			com.maxonaddon.command.SmugglerSpawnCommand.register(dispatcher);
			com.maxonaddon.command.ReputationCommand.register(dispatcher);

		});

		// Гарантирует минимум 1 полицейского и минимум 1 контрабандиста в городе (не просто шанс).
		com.maxonaddon.world.CityFactionGuard.register();
		// Полиция заступается за мирных жителей, если по ним ударил игрок.
		com.maxonaddon.world.PoliceDefendVillagersHandler.register();
		com.maxonaddon.event.QuestCommanderSpawner.register();
		com.maxonaddon.event.QuestInteractHandler.register();
		// Редкие (5% на регион ~192x192 блока) аванпосты разбойников — только структура и
		// лут, без самих мобов-разбойников.
		com.maxonaddon.world.BanditOutpostGenerator.register();
		com.maxonaddon.world.PillagerOutpostGenerator.register();
		com.maxonaddon.OutpostCadetHandler.register();
		com.maxonaddon.util.RunawayHorseTracker.register();

		// === ПЕРЕХВАТ ОРИГИНАЛЬНОЙ ПЕРЕЗАРЯДКИ ИЗ DANNYS-AOT ===
		// Откладываем до старта сервера, а не делаем прямо здесь: мы перехватываем ЧУЖИЕ
		// пакеты (dannys-aot:blade_reload / apg_reload), а зарегистрировать обработчик можно
		// только после того, как сам dannys-aot зарегистрировал их тип. На Fabric нам везло с
		// порядком инициализации, но под Sinytra Connector наш мод стартует РАНЬШЕ базового,
		// и попытка перехвата падала с "no payload type has been registered with name
		// dannys-aot:blade_reload". К моменту SERVER_STARTING все моды заведомо отработали.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(
				server -> installDaotReloadInterception());


		// 2. Перезарядка (когда нажали R)
		ServerPlayNetworking.registerGlobalReceiver(ReloadGearPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				if (player == null) return;

				ItemStack legItem = player.getItemBySlot(EquipmentSlot.LEGS);
				boolean isOdm = legItem.getItem() instanceof daot.ODMGearItem;
				boolean isApg = legItem.getItem() instanceof daot.ODMAPGItem;
				if (!isOdm && !isApg) return;

				net.minecraft.world.item.component.ItemContainerContents contents = legItem.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
				net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(2, ItemStack.EMPTY);
				contents.copyInto(items);

				boolean inventoryChanged = false;

				// Проходим по главной и левой руке
				InteractionHand[] hands = {InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND};
				for (InteractionHand hand : hands) {
					ItemStack handStack = player.getItemInHand(hand);

					// Перезарядка УПМ (Мечи)
					if (isOdm && handStack.getItem() instanceof daot.BladeItem) {
						if (daot.BladeItem.getBladeState(handStack) == daot.BladeItem.BladeState.EMPTY) {
							for (int i = 0; i < items.size(); i++) {
								ItemStack supplyStack = items.get(i);
								// Теперь мы ищем BladeComponentItem!
								if (supplyStack.getItem() instanceof daot.BladeComponentItem && supplyStack.getCount() > 0) {
									supplyStack.shrink(1); // Тратим клинок
									inventoryChanged = true;
									daot.BladeItem.reloadBlade(handStack); // Восстанавливаем клинок
									player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.5F);
									break;
								}
							}
						}
					}

					// Перезарядка АПГ (Огнестрел)
					if (isApg && handStack.getItem() instanceof daot.APGGunItem) {
						if (!daot.APGGunItem.isLoaded(handStack)) {
							for (int i = 0; i < items.size(); i++) {
								ItemStack supplyStack = items.get(i);
								// Ищем картридж APGCartridgeItem
								if (supplyStack.getItem() instanceof daot.APGCartridgeItem && supplyStack.getCount() > 0) {
									supplyStack.shrink(1);
									inventoryChanged = true;
									daot.APGGunItem.setLoaded(handStack, true); // Заряжаем пушку
									player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.CROSSBOW_LOADING_END, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.5F);
									break;
								}
							}
						}
					}
				}

				if (inventoryChanged) {
					legItem.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
				}
			});
		});



// 1. Положить/Взять предметы из брони
		ServerPlayNetworking.registerGlobalReceiver(OdmSupplyInteractPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				if (player == null) return;

				com.maxonaddon.util.IPlayerStats stats = (com.maxonaddon.util.IPlayerStats) player;
				ItemStack legItem = player.getItemBySlot(EquipmentSlot.LEGS);
				boolean isOdm = legItem.getItem() instanceof daot.ODMGearItem;
				boolean isApg = legItem.getItem() instanceof daot.ODMAPGItem;
				if (!isOdm && !isApg) return;

				net.minecraft.world.item.component.ItemContainerContents contents = legItem.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
				net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(2, ItemStack.EMPTY);
				contents.copyInto(items);

				ItemStack cursorStack = player.containerMenu.getCarried();
				ItemStack slotStack = items.get(payload.slotIndex());

				if (cursorStack.isEmpty()) {
					if (!slotStack.isEmpty()) {
						ItemStack toGive = slotStack.copy();
						items.set(payload.slotIndex(), ItemStack.EMPTY);
						if (!player.getInventory().add(toGive)) {
							player.drop(toGive, false);
						}
					}
				} else {
					boolean isBlade = cursorStack.getItem() instanceof daot.BladeComponentItem;
					boolean isCartridge = cursorStack.getItem() instanceof daot.APGCartridgeItem;

					int maxLimit = 0;

					ItemStack otherSlot = items.get(payload.slotIndex() == 0 ? 1 : 0);
					boolean otherIsBlade = otherSlot.getItem() instanceof daot.BladeComponentItem;
					boolean otherIsCartridge = otherSlot.getItem() instanceof daot.APGCartridgeItem;

					// СИММЕТРИЧНАЯ ЛОГИКА
					if (isOdm) {
						if (isBlade && !otherIsCartridge) maxLimit = 2; // Стандартный УПМ
						else if (isCartridge && stats.hasConnectorMastery() && !otherIsBlade) maxLimit = 3; // УПМ с огнестрелом
					} else if (isApg) {
						if (isCartridge && !otherIsBlade) maxLimit = 3; // Стандартный АПГ
						else if (isBlade && stats.hasConnectorMastery() && !otherIsCartridge) maxLimit = 2; // АПГ с клинками
					}

					if (maxLimit == 0) return; // Блокируем неверный предмет или смешивание

					if (slotStack.isEmpty()) {
						int amountToPut = Math.min(cursorStack.getCount(), maxLimit);
						items.set(payload.slotIndex(), cursorStack.split(amountToPut));
					} else if (ItemStack.isSameItemSameComponents(cursorStack, slotStack)) {
						int space = maxLimit - slotStack.getCount();
						if (space > 0) {
							int toAdd = Math.min(cursorStack.getCount(), space);
							slotStack.grow(toAdd);
							cursorStack.shrink(toAdd);
						}
					} else {
						if (cursorStack.getCount() <= maxLimit) {
							ItemStack temp = slotStack.copy();
							items.set(payload.slotIndex(), cursorStack.copy());
							cursorStack = temp;
						}
					}
				}

				legItem.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.copyOf(items)));
				player.containerMenu.setCarried(cursorStack);
				player.containerMenu.broadcastChanges();

				player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-1, -1, 0, cursorStack));
				player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
						player.containerMenu.containerId,
						player.containerMenu.incrementStateId(), 7, legItem
				));
			});
		});


		// Прием кликов от клиента и передача в миксин титана
		ServerPlayNetworking.registerGlobalReceiver(JawQteInputPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				if (player == null) return;

				// 1. Если игрок бьет молот Челюстями
				if (player.getVehicle() instanceof IJawQteHandler jawTitan) {
					jawTitan.handleQtePress(payload.pressedKey());
				}

				// 2. Если игрок пытается вырваться из захвата шифтера
				ShifterGrabQteHandler.handleQtePress(player, payload.pressedKey());
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(CarriageTeleportPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var player = context.player();
				ICarriageData playerData = (ICarriageData) player;
				String cityId = payload.cityId();

				// С активной доставкой припасов возница отказывается везти: иначе весь смысл
				// задания (довезти опечатанный ящик своим ходом до срока) обходился бы одной
				// поездкой на карете. Маршруты у водителя при этом открывать по-прежнему можно —
				// блокируется именно проезд.
				com.maxonaddon.util.IPlayerStats carriageQuestStats = (com.maxonaddon.util.IPlayerStats) player;
				if (com.maxonaddon.DeliveryQuestHandler.QUEST_ID.equals(carriageQuestStats.getActiveQuestId())) {
					player.displayClientMessage(
							Component.translatable("message.maxonaddon.carriage.delivery_blocked").withStyle(ChatFormatting.RED), true);
					return;
				}

				ResourceKey<net.minecraft.world.level.Level> paradisKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));
				ServerLevel paradisLevel = player.getServer().getLevel(paradisKey);

				if (paradisLevel == null) return;

				// Источник истины для цены — водитель, у которого игрок реально стоял
				// (запомнили в CarriageInteractHandler), а не то, что прислал клиент.
				String originCityId = lastCarriageCityId.get(player.getUUID());
				int price = com.maxonaddon.util.CarriagePricing.priceTo(originCityId, cityId);
				if (price < 0) price = 3; // не смогли определить маршрут — берём как за чужую ветку

				// --- ЛОГИКА ПЕРКА "ПУТЕШЕСТВЕННИК" ---
				com.maxonaddon.util.IPlayerStats stats = (com.maxonaddon.util.IPlayerStats) player;
				// Проверяем, есть ли перк (ID перка из ModPerk.java)
				if (stats.hasUnlockedPerk("traveler")) {
					price = 0; // Делаем проезд бесплатным
					// Мультиязычное сообщение + звук
					player.sendSystemMessage(Component.translatable("message.maxonaddon.carriage.traveler_free").withStyle(ChatFormatting.GOLD));
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.VILLAGER_YES, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
				}
				// -------------------------------------

				// Скидка торговца — после бесплатного проезда путешественника, чтобы не
				// пытаться удешевлять уже нулевую цену.
				price = com.maxonaddon.perk.PerkManager.discountedPrice(player, price);

				int emeraldsHeld = player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD);
				if (emeraldsHeld < price) {
					// true = ActionBar (над хотбаром) — легко пропустить обычное сообщение
					// в чате сразу после закрытия GUI, а тут причина отказа видна сразу.
					player.displayClientMessage(Component.translatable("message.maxonaddon.carriage.not_enough_emeralds", price).withStyle(ChatFormatting.RED), true);
					return;
				}

				// Если это лес
				if (cityId.equals("giant_forest") && playerData.maxon$isForestUnlocked()) {
					BlockPos forestPos = com.maxonaddon.util.ForestLocator.getCachedForestPos();

					if (forestPos != null) {
						// Пересчитываем высоту земли ПРЯМО СЕЙЧАС, а не доверяем закешированный Y —
						// тот считался один раз при самом первом поиске леса и мог протухнуть
						// (чанк тогда мог быть не до конца сгенерирован, рельеф мог измениться).
						// Устаревший Y мог заводить игрока под землю/в воздух — деньги списывались,
						// а сам телепорт выглядел как "ничего не произошло".
						paradisLevel.getChunk(forestPos.getX() >> 4, forestPos.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
						int freshY = paradisLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, forestPos.getX(), forestPos.getZ()) + 1;

						// ВАЖНО: третий параметр НЕ может быть null — Inventory.clearOrCountMatchingItems(...)
					// безусловно вызывает container.getContainerSize() на нём (проверено байткодом).
					// Ваниль (ClearInventoryCommands) всегда передаёт сюда player.inventoryMenu.getCraftSlots() —
					// раньше здесь стоял null, из-за чего при каждой попытке списать изумруды сервер падал
					// с NullPointerException в отдельной задаче (и платёж/телепорт срывался целиком).
					player.getInventory().clearOrCountMatchingItems(stack -> stack.is(net.minecraft.world.item.Items.EMERALD), price, player.inventoryMenu.getCraftSlots());
						try {
							player.teleportTo(paradisLevel, forestPos.getX() + 0.5, freshY, forestPos.getZ() + 0.5, player.getYRot(), player.getXRot());
							player.displayClientMessage(Component.translatable("message.maxonaddon.carriage.arrived").withStyle(ChatFormatting.GREEN), true);
						} catch (Exception e) {
							// Деньги уже списаны — честно сообщаем и возвращаем их, а не молчим.
							player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.EMERALD, price));
							player.displayClientMessage(Component.translatable("message.maxonaddon.carriage.teleport_failed").withStyle(ChatFormatting.RED), true);
							LOGGER.error("Carriage teleport to forest failed for {}", player.getGameProfile().getName(), e);
						}
					} else {
						player.sendSystemMessage(Component.translatable("message.maxonaddon.carriage.forest_found_not").withStyle(ChatFormatting.RED));					}
					return;
				}
				// Если это открытый город
				if (playerData.maxon$getUnlockedCities().containsKey(cityId)) {
					// Берем координаты прямо из нашего словаря!
					BlockPos targetPos = playerData.maxon$getUnlockedCities().get(cityId);

					// ВАЖНО: третий параметр НЕ может быть null — Inventory.clearOrCountMatchingItems(...)
					// безусловно вызывает container.getContainerSize() на нём (проверено байткодом).
					// Ваниль (ClearInventoryCommands) всегда передаёт сюда player.inventoryMenu.getCraftSlots() —
					// раньше здесь стоял null, из-за чего при каждой попытке списать изумруды сервер падал
					// с NullPointerException в отдельной задаче (и платёж/телепорт срывался целиком).
					player.getInventory().clearOrCountMatchingItems(stack -> stack.is(net.minecraft.world.item.Items.EMERALD), price, player.inventoryMenu.getCraftSlots());

					// Телепортируем на блок выше (targetPos.getY() + 1), чтобы не застрять в НПС
					try {
						player.teleportTo(paradisLevel, targetPos.getX() + 0.5, targetPos.getY() + 1, targetPos.getZ() + 0.5, player.getYRot(), player.getXRot());
						player.displayClientMessage(Component.translatable("message.maxonaddon.carriage.arrived").withStyle(ChatFormatting.GREEN), true);
					} catch (Exception e) {
						player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.EMERALD, price));
						player.displayClientMessage(Component.translatable("message.maxonaddon.carriage.teleport_failed").withStyle(ChatFormatting.RED), true);
						LOGGER.error("Carriage teleport to {} failed for {}", cityId, player.getGameProfile().getName(), e);
					}
				} else {
					// Раньше здесь не было ветки else вообще — если по каким-то причинам
					// (рассинхрон, устаревший пакет с GUI) город не значился разблокированным
					// на сервере именно в этот момент, происходило ПОЛНОЕ МОЛЧАНИЕ: ни телепорта,
					// ни сообщения об ошибке — с точки зрения игрока "жму Поехали и ничего не происходит".
					player.displayClientMessage(Component.translatable("gui.maxonaddon.carriage.locked").withStyle(ChatFormatting.RED), true);
				}
			});
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			IPlayerStats stats = (IPlayerStats) player;
			ServerPlayNetworking.send(player, new SyncFearStatsPayload(stats.getTitansKilled()));
			ServerPlayNetworking.send(player, new SyncSkillsPayload(new java.util.ArrayList<>(stats.getUnlockedSkills())));
			ServerPlayNetworking.send(player, new com.maxonaddon.network.SyncQuestPayload(
					stats.getActiveQuestId(),
					stats.getActiveQuestTarget(),
					stats.getQuestTitansKilled(),
					stats.getTestQuestCompletions(),
					stats.getDeliveryDeadlineTick(),
					stats.getTestQuestCommanderMask() // <-- Add this 6th argument
			));

			// Квест "Доставить припасы" пережил перезаход — переотправляем клиенту индикатор-
			// локатор (см. DeliveryLocatorClient), иначе после релога он молчит до конца квеста.
			if (com.maxonaddon.DeliveryQuestHandler.QUEST_ID.equals(stats.getActiveQuestId())) {
				// Цель хранится координатами (см. IPlayerStats.getDeliveryTargetX/Y/Z) — никакого
				// поиска сущности не требуется, командир всегда в Paradis.
				ServerPlayNetworking.send(player, new com.maxonaddon.network.SyncDeliveryTargetPayload(
						true, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis").toString(),
						stats.getDeliveryTargetX() + 0.5, stats.getDeliveryTargetY(), stats.getDeliveryTargetZ() + 0.5
				));
			}

			// То же самое для "Спасение застрявших" — локатор шлём, только пока не добрались
			// (после arrived он и так спрятан, см. RescueQuestHandler.checkArrivals).
			if (com.maxonaddon.RescueQuestHandler.QUEST_ID.equals(stats.getActiveQuestId())) {
				ServerPlayNetworking.send(player, new com.maxonaddon.network.SyncRescueStatusPayload(
						true, stats.isRescueArrived(), stats.getRescueCadetsTotal(), stats.getRescueCadetsAlive(),
						stats.getRescueTitansRequired(), stats.getRescueDeadlineTick()
				));
				if (!stats.isRescueArrived()) {
					// Ещё не дошёл — жёлтый локатор на здание. Если уже дошёл и дерётся с
					// титанами, красный локатор на ближайшего титана подхватится сам в течение
					// секунды (см. RescueQuestHandler.updateTitanLocators — она проходит по ВСЕМ
					// онлайн-игрокам с активным квестом, включая только что зашедшего).
					ServerPlayNetworking.send(player, new com.maxonaddon.network.SyncRescuePayload(
							true, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis").toString(),
							stats.getRescueTargetX() + 0.5, stats.getRescueTargetY(), stats.getRescueTargetZ() + 0.5, false
					));
				}
			}

			// Маркер на раненого выжившего тоже переживает перезаход (метод сам проверяет,
			// активен ли именно этот квест).
			com.maxonaddon.SurvivorQuestHandler.resendOnJoin(player);

			// Перки при заходе в мир клиенту не уходили вообще: refreshAndSync стоял в
			// AFTER_RESPAWN и AFTER_PLAYER_CHANGE_WORLD, а в JOIN его не было. На сервере
			// данные при этом целы (см. MaxonEquippedPerks / MaxonUnlockedPerks в
			// PlayerStatsMixin), но клиентский список приходил пустым — и после перезахода
			// все перки показывались закрытыми, а слоты пустыми.
			com.maxonaddon.perk.PerkManager.refreshAndSync(player);

			boolean fearEnabled = server.getGameRules().getBoolean(MaxonConfig.ALLOW_FEAR_SYSTEM);
			ServerPlayNetworking.send(player, new SyncModFlagsPayload(MaxonConfig.get().statisticsSystemEnabled, fearEnabled, MaxonConfig.isReputationEnabled()));

			// Весь конфиг целиком: на сервере с модом клиентский maxon_addon.json не значит
			// ничего, клиент живёт по серверному до самого выхода.
			ServerPlayNetworking.send(player, new com.maxonaddon.network.ConfigSyncPayload(MaxonConfig.toJson()));

			// Стадия крови — и себе, и всем, кто уже видит этого игрока.
			com.maxonaddon.util.BloodManager.sync(player);

			// === НОВАЯ ЛОГИКА ПЕРВОГО ЗАХОДА В ПАРАДИЗ ===
			// Настройка выключена — не трогаем игрока вообще: он остаётся там, куда его
			// поместила игра. Раньше здесь менялась только точка назначения, а сам заброс в
			// Парадиз происходил всё равно, и выключить его было нечем.
			ServerLevel paradisLevel = MaxonConfig.get().enableRandomParadisSpawn ? getParadisLevel(server) : null;
			if (paradisLevel != null && !player.getTags().contains("has_joined")) {
				BlockPos spawnPos = getOrAssignPersonalParadisSpawn(player, paradisLevel);

				player.teleportTo(paradisLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
				// Устанавливаем эту точку как "кровать"
				player.setRespawnPosition(paradisLevel.dimension(), spawnPos, 0.0F, true, false);
				player.addTag("has_joined");
				player.sendSystemMessage(Component.translatable("message.maxonaddon.welcome_paradis").withStyle(ChatFormatting.YELLOW));
			}
		});


		Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "gas_cisterna"), GAS_CISTERNA);
		Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "gas_cisterna"), GAS_CISTERNA_ITEM);
		Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "item_box"), ITEM_BOX);
		Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "item_box"), ITEM_BOX_ITEM);
		Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "thunder_gun"), THUNDER_GUN_ITEM);
		Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "marleyan_rifle"), MARLEYAN_RIFLE_ITEM);
		// Регистрация кастомного партикла
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "titan_steam"), TITAN_STEAM);

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
			entries.accept(GAS_CISTERNA_ITEM);
			entries.accept(ITEM_BOX);
			entries.accept(THUNDER_GUN_ITEM);
			entries.accept(MARLEYAN_RIFLE_ITEM);
		});

		ResourceKey<CreativeModeTab> daotBlocksTab = ResourceKey.create(
				Registries.CREATIVE_MODE_TAB,
				ResourceLocation.fromNamespaceAndPath("dannys-aot", "blocks")
		);


		ItemGroupEvents.modifyEntriesEvent(daotBlocksTab).register(entries -> {
			entries.accept(ITEM_BOX);
			entries.accept(THUNDER_GUN_ITEM); // <--- ДОБАВЛЯЕМ СЮДА
			entries.accept(MARLEYAN_RIFLE_ITEM);
		});

		// Клик по нашей коробке разбираем ДО ванильного маршрута взаимодействия — он смотрит,
		// чем заняты руки, и разводит вызов по useWithoutItem / useItemOn в зависимости от того,
		// крадётся ли игрок и в какой руке лежит предмет. Из-за этого коробку приходилось
		// открывать с пустыми руками. Здесь рука роли не играет: перехватываем главную,
		// сразу выполняем действие и гасим событие, чтобы предмет из руки не сработал следом.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResult.PASS;

			BlockPos boxPos = hitResult.getBlockPos();
			net.minecraft.world.level.block.state.BlockState boxState = level.getBlockState(boxPos);
			if (!(boxState.getBlock() instanceof com.maxonaddon.block.SwordBoxBlock box)) {
				return net.minecraft.world.InteractionResult.PASS;
			}

			net.minecraft.world.InteractionResult result = box.interact(boxState, level, boxPos, player);
			// PASS оставляем ванили: это случай "заглушка без главной половины" — пусть ведёт
			// себя как обычный блок, а не глотает клик молча.
			return result;
		});

		ServerPlayNetworking.registerGlobalReceiver(ResetSkillsPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				IPlayerStats stats = (IPlayerStats) player;

				int refundedSoldierSp = 0;
				int refundedBloodlinePoints = 0;
				int refundedTitanPoints = 0;

				// Автоматически проходим по всем навыкам из реестра ModSkill
				for (ModSkill skill : ModSkill.values()) {
					if (stats.hasSkill(skill.id)) {
						// Возвращаем очки в зависимости от категории навыка
						switch (skill.category) {
							case SOLDIER -> refundedSoldierSp += skill.cost;
							case BLOODLINE -> refundedBloodlinePoints += skill.cost;
							case TITAN -> refundedTitanPoints += skill.cost;
						}
					}
				}

				// Очищаем все изученные навыки разом
				stats.getUnlockedSkills().clear();

				// Сбрасываем старые булевы флаги (на всякий случай для обратной совместимости)
				stats.setOdmMastery(false);
				stats.setDashMastery(false);
				stats.setConnectorMastery(false);
				stats.setUngrabingMastery(false);
				stats.setBloodlineAwakening(false);
				stats.setShifterRegen(false);

				// Возвращаем очки игроку (солдатские SP = базовые за уровень + всё, что вернулось со скилов)
				stats.setSkillPoints((stats.getLevel() - 1) + refundedSoldierSp);
				stats.setBloodlinePoints(stats.getBloodlinePoints() + refundedBloodlinePoints);
				stats.setTitanPoints(stats.getTitanPoints() + refundedTitanPoints);

				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
				player.displayClientMessage(Component.translatable("message.maxonaddon.skills.reset_success"), false);
				StatsCommand.sendPlayerStats(player);

				ServerPlayNetworking.send(player, new SyncSkillsPayload(new ArrayList<>()));
			});
		});

		// Смена перка в слоте. Клиенту не верим: проверяем и номер слота, и сам перк.
		ServerPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.EquipPerkPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				int slot = payload.slot();
				if (slot < 0 || slot >= com.maxonaddon.perk.PerkManager.SLOTS) return;

				String perkId = payload.perkId();
				boolean clearing = perkId == null || perkId.isEmpty();
				if (!clearing && com.maxonaddon.perk.ModPerk.byId(perkId) == null) return;

				IPlayerStats stats = (IPlayerStats) player;

				// Надеть можно только то, что игрок реально разблокировал — клиент здесь
				// не проверка, а просто список того, что показывать; настоящая проверка тут.
				if (!clearing && !stats.hasUnlockedPerk(perkId)) return;

				// Один и тот же перк нельзя вставить в два слота сразу.
				if (!clearing) {
					String[] slots = stats.getEquippedPerks();
					for (int i = 0; i < slots.length; i++) {
						if (i != slot && perkId.equals(slots[i])) {
							stats.setEquippedPerk(i, null);
						}
					}
				}

				stats.setEquippedPerk(slot, clearing ? null : perkId);
				com.maxonaddon.perk.PerkManager.refreshAndSync(player);
			});
		});

		// Действия с квестом: 0 = принять, 1 = отказаться, 2 = сдать. Сервер — единственный
		// источник истины (IPlayerStats), клиент только получает SyncQuestPayload в ответ.
		ServerPlayNetworking.registerGlobalReceiver(QuestActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				net.minecraft.server.level.ServerPlayer player = context.player();
				com.maxonaddon.util.IPlayerStats stats = (com.maxonaddon.util.IPlayerStats) player;

				// Клиент при выключенной системе меню и не покажет, но пакет может прийти и
				// от подменённого клиента — источник истины всё равно сервер.
				if (!MaxonConfig.isReputationEnabled()) return;

				if (payload.action() == 0) { // Принять
					// Одноразовые задания второй раз не выдаём. Тестовое из этой проверки
					// исключено: оно теперь ПОВТОРЯЕМОЕ и растущее (см. IPlayerStats), а в
					// старых сохранениях оно ещё помечено сданным — без исключения такой
					// игрок не смог бы взять его снова.
					if (!"test_quest".equals(payload.questId()) && stats.hasCompletedQuest(payload.questId())) {
						player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.maxonaddon.quest.already_done").withStyle(net.minecraft.ChatFormatting.RED));
						return; // Прерываем приём — квест не меняем, синк не шлём
					}

					// Остальные задания у командиров открываются только после первой сдачи
					// тестового — оно вводное. "Раненый выживший" сюда не попадает: он берётся
					// не у командира, а случайной встречей в мире (см. SurvivorQuestHandler).
					boolean gatedQuest = payload.questId().equals(com.maxonaddon.DeliveryQuestHandler.QUEST_ID)
							|| payload.questId().equals(com.maxonaddon.RescueQuestHandler.QUEST_ID);
					if (gatedQuest && stats.getTestQuestCompletions() <= 0) {
						player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.maxonaddon.quest.locked_until_test").withStyle(net.minecraft.ChatFormatting.RED));
						return;
					}

					if (payload.questId().equals(com.maxonaddon.DeliveryQuestHandler.QUEST_ID)) {
						// "Доставить припасы" — вся логика (случайный получатель, ящик, компас,
						// стены) в DeliveryQuestHandler; она сама выставляет активный квест.
						// Если выдать нечего (нет других командиров) — квест не активируется,
						// сообщение об этом уже отправлено внутри, просто ничего не синкаем.
						net.minecraft.world.entity.Entity originEntity = payload.commanderId() >= 0
								? player.level().getEntity(payload.commanderId()) : null;
						if (!(originEntity instanceof net.minecraft.world.entity.npc.Villager originCommander)
								|| !com.maxonaddon.DeliveryQuestHandler.startDeliveryQuest(player, originCommander)) {
							return;
						}
					} else if (payload.questId().equals(com.maxonaddon.RescueQuestHandler.QUEST_ID)) {
						// "Спасение застрявших" — сцена (здание/кадеты/титаны) создаётся с нуля
						// прямо тут, а не ищется, см. RescueQuestHandler.startRescueQuest.
						net.minecraft.world.entity.Entity originEntity = payload.commanderId() >= 0
								? player.level().getEntity(payload.commanderId()) : null;
						if (!(originEntity instanceof net.minecraft.world.entity.npc.Villager originCommander)
								|| !com.maxonaddon.RescueQuestHandler.startRescueQuest(player, originCommander)) {
							return;
						}
					} else {
						// Запоминаем стену командира, у которого взяли квест — она получит репутацию
						// при успешной сдаче (см. action == 2 ниже). commanderId < 0 или сущность не
						// найдена/не жива — просто не будет награды репутацией за этот квест.
						String questWall = "";
						int commanderIndex = -1;
						if (payload.commanderId() >= 0) {
							net.minecraft.world.entity.Entity commanderEntity = player.level().getEntity(payload.commanderId());
							if (commanderEntity != null && commanderEntity.isAlive()) {
								com.maxonaddon.util.Wall wall = com.maxonaddon.util.WallHelper.getWallForCommander(commanderEntity);
								if (wall != null) questWall = wall.name();
								commanderIndex = com.maxonaddon.event.QuestCommanderSpawner.nearestPointIndex(
										commanderEntity.getX(), commanderEntity.getZ());
							}
						}

						// Вводное задание берётся у каждого командира ровно ОДИН раз: сдал у
						// этого — иди к следующему, там оно будет уже с поднятой планкой.
						if ("test_quest".equals(payload.questId()) && stats.hasCompletedTestQuestAt(commanderIndex)) {
							player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.maxonaddon.quest.done_at_this_commander").withStyle(net.minecraft.ChatFormatting.RED));
							return;
						}

						stats.setActiveQuest(payload.questId(), payload.target());
						stats.setQuestTitansKilled(0); // Сбрасываем счетчик убийств
						stats.setActiveQuestWall(questWall);
						stats.setTestQuestCommanderIndex(commanderIndex);
					}

				} else if (payload.action() == 1) { // Отказаться — от КОНКРЕТНОГО квеста
					// (пустой questId в пакете — для обратной совместимости со старым клиентом,
					// который ещё не шлёт id; в этом случае отказываемся от того, что активно).
					if (payload.questId().isEmpty() || payload.questId().equals(stats.getActiveQuestId())) {
						String abandonedId = stats.getActiveQuestId();

						// Штраф за отказ — единый и заметно меньше, чем за проваленное задание
						// (например, за сорванную пломбу у доставки снимают куда больше).
						// Начисляем ДО уборки: она обнуляет activeQuestWall.
						com.maxonaddon.util.Wall abandonWall = com.maxonaddon.util.Wall.fromNameSafe(stats.getActiveQuestWall());
						if (!abandonedId.isEmpty() && abandonWall != null) {
							stats.addReputation(abandonWall, -QUEST_ABANDON_REP_PENALTY);
						}

						// Уборка, специфичная для каждого задания: у доставки забираем ящик
						// припасов, у спасения — убираем кадетов, у выжившего — снимаем с него
						// наручники и метку "занят" (иначе он навсегда остался бы недоступен).
						if (com.maxonaddon.DeliveryQuestHandler.QUEST_ID.equals(abandonedId)) {
							com.maxonaddon.DeliveryQuestHandler.abandon(player);
						} else if (com.maxonaddon.RescueQuestHandler.QUEST_ID.equals(abandonedId)) {
							com.maxonaddon.RescueQuestHandler.abandon(player);
						} else if (com.maxonaddon.SurvivorQuestHandler.QUEST_ID.equals(abandonedId)) {
							com.maxonaddon.SurvivorQuestHandler.abandon(player);
						}

						if (!abandonedId.isEmpty()) {
							player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
									"message.maxonaddon.quest.abandoned", QUEST_ABANDON_REP_PENALTY).withStyle(net.minecraft.ChatFormatting.DARK_RED));
						}

						stats.setActiveQuest("", "");
						stats.setQuestTitansKilled(0);
						stats.setActiveQuestWall("");
						// Отказ НЕ помечает командира выполненным — вводное у него можно
						// будет взять снова (штрафом за отказ игрок уже расплатился).
						stats.setTestQuestCommanderIndex(-1);
					}

				} else if (payload.action() == 3) { // Обновить — пересчёт состояния по факту
					// Пока только у спасения: именно там счётчик держится на событиях смерти,
					// которые могут не прийти (см. RescueQuestHandler.refresh).
					if (com.maxonaddon.RescueQuestHandler.QUEST_ID.equals(stats.getActiveQuestId())) {
						com.maxonaddon.RescueQuestHandler.refresh(player);
					}

				} else if (payload.action() == 2) { // Сдать
					if (stats.getActiveQuestId().equals("test_quest")) {
						// Планка растёт с каждой сдачей — в следующем городе командир попросит
						// больше (см. IPlayerStats#testQuestTitansRequired/WheatRequired).
						int completions = stats.getTestQuestCompletions();
						int titansRequired = com.maxonaddon.util.IPlayerStats.testQuestTitansRequired(completions);
						int wheatRequired = com.maxonaddon.util.IPlayerStats.testQuestWheatRequired(completions);
						int wheatCount = player.getInventory().countItem(net.minecraft.world.item.Items.WHEAT);

						if (stats.getQuestTitansKilled() >= titansRequired && wheatCount >= wheatRequired) {

							// БЕЗОПАСНО забираем ровно нужное количество пшеницы (как мы делали с изумрудами для повозки)
							player.getInventory().clearOrCountMatchingItems(
									stack -> stack.is(net.minecraft.world.item.Items.WHEAT),
									wheatRequired,
									player.inventoryMenu.getCraftSlots()
							);

							// Награда репутацией стене, у чьего командира взяли этот квест.
							// ВАЖНО: читаем стену ДО setActiveQuestWall("") — раньше строчки шли
							// в обратном порядке и награда репутацией не начислялась никогда.
							com.maxonaddon.util.Wall questWall = com.maxonaddon.util.Wall.fromNameSafe(stats.getActiveQuestWall());
							if (questWall != null) {
								stats.addReputation(questWall, com.maxonaddon.ReputationHandler.QUEST_REP_GAIN);
							}

							// Помечаем ИМЕННО этого командира: у него вводное больше не взять,
							// а у следующего — можно, уже с поднятой планкой.
							stats.markTestQuestCompletedAt(stats.getTestQuestCommanderIndex());
							stats.setTestQuestCommanderIndex(-1);

							stats.setActiveQuest("", "");
							stats.setQuestTitansKilled(0);
							stats.setActiveQuestWall("");

							// Задание повторяемое: markQuestCompleted больше НЕ ставим (иначе
							// проверка в action == 0 закрыла бы его навсегда) — вместо этого
							// растим счётчик выполнений, он же поднимает планку и открывает
							// остальные задания у командиров.
							stats.setTestQuestCompletions(completions + 1);
							stats.addQuestCompleted();

							// Награда: 1 очко навыка, 3 изумруда, перк "Боец Человечества".
							stats.setSkillPoints(stats.getSkillPoints() + 1);

							net.minecraft.world.item.ItemStack emeralds = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 3);
							if (!player.getInventory().add(emeralds)) {
								player.drop(emeralds, false);
							}

							com.maxonaddon.perk.PerkUnlockHandler.grantPerk(player, com.maxonaddon.perk.ModPerk.HUMANITY_FIGHTER);

							player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.maxonaddon.quest.test_completed").withStyle(net.minecraft.ChatFormatting.GREEN));
							// Сразу говорим, какой будет следующая планка — иначе рост заметен
							// только когда игрок откроет детали у следующего командира.
							player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
									"message.maxonaddon.quest.test_next_goal",
									com.maxonaddon.util.IPlayerStats.testQuestTitansRequired(completions + 1),
									com.maxonaddon.util.IPlayerStats.testQuestWheatRequired(completions + 1)
							).withStyle(net.minecraft.ChatFormatting.GRAY));
						} else {
							player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.maxonaddon.quest.test_conditions_not_met").withStyle(net.minecraft.ChatFormatting.RED));
							return; // Прерываем сдачу
						}
					} else if (stats.getActiveQuestId().equals(com.maxonaddon.DeliveryQuestHandler.QUEST_ID)) {
						com.maxonaddon.DeliveryQuestHandler.tryTurnIn(player, payload.commanderId());
					} else if (stats.getActiveQuestId().equals(com.maxonaddon.RescueQuestHandler.QUEST_ID)) {
						// В отличие от доставки, тут не важно, У КАКОГО именно командира сдавать
						// (нет понятия "получатель") — как и у тестового квеста.
						com.maxonaddon.RescueQuestHandler.tryTurnIn(player);
					} else if (stats.getActiveQuestId().equals(com.maxonaddon.SurvivorQuestHandler.QUEST_ID)) {
						// "Раненый выживший" — сдаётся любому командиру, но житель должен быть
						// при игроке (на спине или рядом), см. tryTurnIn.
						com.maxonaddon.SurvivorQuestHandler.tryTurnIn(player, payload.commanderId());
					}
				}

				// Синхронизируем обновленные данные с клиентом
				ServerPlayNetworking.send(player, new SyncQuestPayload(stats.getActiveQuestId(), stats.getActiveQuestTarget(), stats.getQuestTitansKilled(), stats.getTestQuestCompletions(), stats.getDeliveryDeadlineTick(), stats.getTestQuestCommanderMask()));
			});
		});

		// Ответ раненому выжившему: 0 = помочь (взять задание), 1 = отказать (штраф репутацией).
		ServerPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.SurvivorActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() ->
					com.maxonaddon.SurvivorQuestHandler.handleAction(context.player(), payload.action(), payload.survivorId()));
		});

		ServerPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.OutpostActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() ->
					com.maxonaddon.OutpostHuntQuestHandler.handleAction(context.player(), payload.action(), payload.cadetId()));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestStatsPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				if (!MaxonConfig.get().statisticsSystemEnabled) {
					return; // Статистика отключена в конфиге сервера
				}
				com.maxonaddon.command.StatsCommand.sendPlayerStats(context.player());
			});
		});

		// 1. Засчитываем убийство титана и выдаём награду за него.
		//
		// Награда зависит от того, в каком облике игрок дрался:
		//   человек -> опыт (expPerTitanKill), опыт копится в уровень, уровень даёт очко навыка;
		//   титан   -> очки титана (titanPointsPerKill).
		// Оба значения берутся из конфига — раньше они там лежали, но в коде не читались:
		// стояли жёстко зашитые 25 опыта и 1 очко.
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, entity, killedEntity) -> {
			ServerPlayer killer = resolveTitanKillCredit(entity);
			if (killer != null) {
				ServerPlayer player = killer;
				IPlayerStats stats = (IPlayerStats) player;

				String className = killedEntity.getClass().getSimpleName();
				if (className.contains("Titan") || className.contains("Fritz") || className.contains("ConnieFather")) {
					stats.addTitanKill();
					if (!stats.getActiveQuestId().isEmpty()) {
						stats.setQuestTitansKilled(stats.getQuestTitansKilled() + 1);
						ServerPlayNetworking.send(player, new SyncQuestPayload(stats.getActiveQuestId(), stats.getActiveQuestTarget(), stats.getQuestTitansKilled(), stats.getTestQuestCompletions(), stats.getDeliveryDeadlineTick(), stats.getTestQuestCommanderMask()));
						// "Спасение застрявших" — проверяем, не хватило ли только что убитого
						// титана до требуемого числа (зелёная ракета + кадеты пропадают).
						com.maxonaddon.RescueQuestHandler.onTitanKillCountChanged(level, player);
						// «Зачистка у вышки» — сообщает, сколько целей ещё осталось.
						com.maxonaddon.OutpostHuntQuestHandler.onTitanKillCountChanged(player);
					}

					// Кровь титана пачкает снаряжение только при убийстве клинком.
					if (com.maxonaddon.util.BloodManager.isHoldingBlade(player)) {
						com.maxonaddon.util.BloodManager.onTitanKilledByBlade(player);
					}

					// Проверка получения перка ТЕСТ (убийство Огра Титана копьём или клинком).
					com.maxonaddon.perk.PerkUnlockHandler.onTitanKilled(player, killedEntity);

					// В облике титана игрок сидит на собственном шифтере. Проверяем по интерфейсу
					// daot.ShifterTitan: прежний список из шести классов пропускал Челюсть,
					// Повозку, Огра, Основателя и Тройного — за них очки не начислялись вообще.
					boolean inTitanForm = player.getRootVehicle() instanceof daot.ShifterTitan;

					if (inTitanForm) {
						stats.setTitanPoints(stats.getTitanPoints() + MaxonConfig.get().titanPointsPerKill);
					} else {
						stats.setExp(stats.getExp() + MaxonConfig.get().expPerTitanKill);
						int requiredExp = 100 + (stats.getLevel() - 1) * 25;

						if (stats.getExp() >= requiredExp) {
							stats.setExp(stats.getExp() - requiredExp);
							stats.setLevel(stats.getLevel() + 1);
							stats.setSkillPoints(stats.getSkillPoints() + 1);

							player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
									net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
						}
					}

					ServerPlayNetworking.send(player, new SyncFearStatsPayload(stats.getTitansKilled()));
				}
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(BuySkillPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				IPlayerStats stats = (IPlayerStats) player;
				ModSkill skillToBuy = ModSkill.getById(payload.skillId());

				// isAvailable отсекает навыки, выключенные конфигом (сейчас это ускорение
				// крюка при enableAdvancedHookMechanics = false): в дереве их не видно, но
				// пакет на покупку можно прислать и руками.
				if (skillToBuy != null && skillToBuy.isAvailable() && !stats.hasSkill(skillToBuy.id)) {
					// Проверка стоимости в зависимости от категории
					boolean canAfford = switch (skillToBuy.category) {
						case SOLDIER -> stats.getSkillPoints() >= skillToBuy.cost;
						case TITAN -> stats.getTitanPoints() >= skillToBuy.cost;
						case BLOODLINE -> stats.getBloodlinePoints() >= skillToBuy.cost;
					};

					if (canAfford) {
						// Списываем очки
						switch (skillToBuy.category) {
							case SOLDIER -> stats.setSkillPoints(stats.getSkillPoints() - skillToBuy.cost);
							case TITAN -> stats.setTitanPoints(stats.getTitanPoints() - skillToBuy.cost);
							case BLOODLINE -> stats.setBloodlinePoints(stats.getBloodlinePoints() - skillToBuy.cost);
						}

						// Выдаем скилл
						stats.unlockSkill(skillToBuy.id);

						player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
								SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 2.0F);

						// Синхронизируем
						StatsCommand.sendPlayerStats(player);
						ServerPlayNetworking.send(player, new SyncSkillsPayload(
								new ArrayList<>(stats.getUnlockedSkills())
						));
					}
				}
			});
		});

		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			IPlayerStats oldStats = (IPlayerStats) oldPlayer;
			IPlayerStats newStats = (IPlayerStats) newPlayer;

			newStats.setTitansKilled(oldStats.getTitansKilled());
			newStats.setTicksPlayedTotal(oldStats.getTicksPlayedTotal());

			if (alive) {
				newStats.setTicksSinceDeath(oldStats.getTicksSinceDeath());
			} else {
				newStats.resetTicksSinceDeath();
			}
			for (com.maxonaddon.util.TraumaType type : com.maxonaddon.util.TraumaType.values()) {
				newStats.setTraumaTimer(type, oldStats.getTraumaTimer(type));
			}

			// === КОПИРУЕМ СИСТЕМУ ПРОКАЧКИ ===
			newStats.setLevel(oldStats.getLevel());
			newStats.setExp(oldStats.getExp());
			// Перки переносим — они не сгорают при смерти. Разблокировку (что вообще
			// доступно надеть) переносим тоже, иначе достижение "убей огра" пришлось бы
			// получать заново после каждой смерти.
			String[] oldPerks = oldStats.getEquippedPerks();
			for (int i = 0; i < oldPerks.length; i++) {
				newStats.setEquippedPerk(i, oldPerks[i]);
			}
			newStats.setUnlockedPerks(oldStats.getUnlockedPerks());

			// Активный квест тоже переносим — смерть сама по себе не проваливает задание.
			newStats.setActiveQuest(oldStats.getActiveQuestId(), oldStats.getActiveQuestTarget());
			newStats.setActiveQuestWall(oldStats.getActiveQuestWall());
			newStats.setDeliveryTarget(oldStats.getDeliveryTargetX(), oldStats.getDeliveryTargetY(), oldStats.getDeliveryTargetZ());
			newStats.setDeliveryTargetWall(oldStats.getDeliveryTargetWall());
			newStats.setDeliveryDeadlineTick(oldStats.getDeliveryDeadlineTick());

			newStats.setRescueTarget(oldStats.getRescueTargetX(), oldStats.getRescueTargetY(), oldStats.getRescueTargetZ());
			newStats.setRescueArrived(oldStats.isRescueArrived());
			newStats.setRescueTitansRequired(oldStats.getRescueTitansRequired());
			newStats.setRescueCadetsTotal(oldStats.getRescueCadetsTotal());
			newStats.setRescueCadetsAlive(oldStats.getRescueCadetsAlive());
			newStats.setRescueDeadlineTick(oldStats.getRescueDeadlineTick());

			newStats.setSurvivorUuid(oldStats.getSurvivorUuid());
			newStats.setSurvivorPos(oldStats.getSurvivorX(), oldStats.getSurvivorY(), oldStats.getSurvivorZ());

			// Счётчик сданных заданий и список их id при смерти не переносились: поля миксина
			// у нового ServerPlayer начинаются с нуля, а NBT на возрождении не перечитывается.
			// Из-за этого статистика откатывалась после каждой смерти — сдал задание в одном
			// городе, погиб, сдал во втором, и вместо двух в статистике снова единица.
			newStats.setTradesCompleted(oldStats.getTradesCompleted());

			// Лавка лошадей: и текущее предложение, и уже купленная лошадь переживают смерть.
			newStats.setHorseOfferSeed(oldStats.getHorseOfferSeed());
			// Кулдаун вышки — иначе смерть обнуляла бы его и задание брали бы бесконечно.
			newStats.setOutpostQuestReadyAt(oldStats.getOutpostQuestReadyAt());
			newStats.setOwnedHorseUuid(oldStats.getOwnedHorseUuid());

			newStats.setQuestsCompleted(oldStats.getQuestsCompleted());
			newStats.setCompletedQuests(oldStats.getCompletedQuests());

			newStats.setTestQuestCompletions(oldStats.getTestQuestCompletions());
			newStats.setTestQuestCommanderMask(oldStats.getTestQuestCommanderMask());
			newStats.setTestQuestCommanderIndex(oldStats.getTestQuestCommanderIndex());

			// Репутация у стен — раньше не копировалась при смерти/возрождении, из-за чего
			// у нового объекта ServerPlayer поля откатывались к дефолтным 50 (см.
			// PlayerStatsMixin — это НЕ NBT с диска, а просто java-поля нового инстанса).
			for (com.maxonaddon.util.Wall wall : com.maxonaddon.util.Wall.values()) {
				newStats.setReputation(wall, oldStats.getReputation(wall));
			}

			newStats.setSkillPoints(oldStats.getSkillPoints());
			newStats.setBloodlinePoints(oldStats.getBloodlinePoints());
			newStats.setTitanPoints(oldStats.getTitanPoints());

			// НАВЫКИ — переносим ВЕСЬ набор разом.
			//
			// Раньше здесь стояли шесть отдельных setXxx на конкретные навыки, и это молча
			// ломалось при каждом пополнении дерева: в ModSkill сейчас 32 навыка, а список
			// копирования так и остался на шести. Всё остальное — ловкость, железные кости,
			// регенерация, мастерство титанов, твёрдая рука, палач, стальной затылок, воля к
			// жизни и ВСЯ ветка родословной кроме bloodline_awakening — при смерти просто
			// исчезало: unlockedSkills у нового ServerPlayer создаётся пустым, а NBT на
			// возрождении не перечитывается.
			//
			// Копия набора целиком лечит и это, и любые будущие навыки — руками сюда больше
			// ничего дописывать не нужно.
			newStats.setUnlockedSkills(oldStats.getUnlockedSkills());

			// Открытые маршруты перевозчика хранятся не в IPlayerStats, а в отдельном
			// миксине PlayerCarriageMixin, и в этот блок раньше не попадали. Поля миксина
			// у нового ServerPlayer создаются пустыми (new HashMap<>(), forestUnlocked=false),
			// а NBT при возрождении не перечитывается — так что после каждой смерти игрок
			// терял все открытые города и лес и должен был открывать маршруты заново.
			ICarriageData oldCarriage = (ICarriageData) oldPlayer;
			ICarriageData newCarriage = (ICarriageData) newPlayer;
			newCarriage.maxon$getUnlockedCities().clear();
			newCarriage.maxon$getUnlockedCities().putAll(oldCarriage.maxon$getUnlockedCities());
			newCarriage.maxon$setForestUnlocked(oldCarriage.maxon$isForestUnlocked());

			// ПАКЕТЫ ОТСЮДА УБРАНЫ (Они отправляются в AFTER_RESPAWN)
		});

		// === НОВОЕ СОБЫТИЕ: СИНХРОНИЗАЦИЯ ПОСЛЕ ВОЗРОЖДЕНИЯ ===
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			IPlayerStats newStats = (IPlayerStats) newPlayer;
			ServerPlayNetworking.send(newPlayer, new SyncFearStatsPayload(newStats.getTitansKilled()));
			ServerPlayNetworking.send(newPlayer, new SyncSkillsPayload(new java.util.ArrayList<>(newStats.getUnlockedSkills())));
			ServerPlayNetworking.send(newPlayer, new com.maxonaddon.network.SyncQuestPayload(
					newStats.getActiveQuestId(),
					newStats.getActiveQuestTarget(),
					newStats.getQuestTitansKilled(),
					newStats.getTestQuestCompletions(),
					newStats.getDeliveryDeadlineTick(),
					newStats.getTestQuestCommanderMask()
			));
			com.maxonaddon.perk.PerkManager.refreshAndSync(newPlayer);

			// === ЛЕЧЕНИЕ ТРАВМ ПРИ ВОЗРОЖДЕНИИ ===
			if (!alive) {
				// Если игрок умер, полностью вылечиваем все травмы
				for (com.maxonaddon.util.TraumaType type : com.maxonaddon.util.TraumaType.values()) {
					newStats.setTraumaTimer(type, 0); // Ставим таймер в 0
					com.maxonaddon.util.TraumaManager.syncTrauma(newPlayer, type); // Синхронизируем с клиентом
				}
			} else {
				// Если игрок просто прыгнул через портал (из Энда), возвращаем ему его травмы
				com.maxonaddon.util.TraumaManager.reapplyTraumas(newPlayer);
			}

			// === ЛОГИКА ВОЗВРАТА ПРИ СЛОМАННОЙ КРОВАТИ ===
			if (!alive) {
				// Та же оговорка, что и на первом входе: выключенная настройка означает «не
				// возвращать в Парадиз», а не «возвращать в другую точку».
				ServerLevel paradisLevel = MaxonConfig.get().enableRandomParadisSpawn
						? getParadisLevel(newPlayer.server) : null;
				if (paradisLevel != null) {
					String currentDim = newPlayer.level().dimension().location().toString();
					// Если игра откинула игрока в Оверворлд из-за сломанной кровати
					if (!currentDim.equals("dannys-aot:paradis")) {
						BlockPos spawnPos = getOrAssignPersonalParadisSpawn(newPlayer, paradisLevel);

						newPlayer.teleportTo(paradisLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
						newPlayer.setRespawnPosition(paradisLevel.dimension(), spawnPos, 0.0F, true, false);
						newPlayer.sendSystemMessage(Component.translatable("message.maxonaddon.bed_broken_return").withStyle(ChatFormatting.RED));
					}
				}
			}
		});

		net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			IPlayerStats stats = (IPlayerStats) player;
			ServerPlayNetworking.send(player, new SyncFearStatsPayload(stats.getTitansKilled()));
			ServerPlayNetworking.send(player, new SyncSkillsPayload(new ArrayList<>(stats.getUnlockedSkills())));
			com.maxonaddon.perk.PerkManager.refreshAndSync(player);
		});

		// === 2. ИНИЦИАЛИЗАЦИЯ МЕХАНИК ===
		HorseTradeHandler.register();
		TraderPerkHandler.register();
		HostileQteHandler.register();
		ShifterGrabQteHandler.register();
		CarriageSpawner.register();
		RoyalTitanInteractHandler.register();
		BerserkBossHandler.register();
		BerserkConfig.register();
		BerserkEscapeQteHandler.register();
		RedTitanCommand.register();
		CarriageInteractHandler.register();
		ApgConfig.register();
		com.maxonaddon.util.ServerInstanceHolder.register();
		com.maxonaddon.util.StuckTitanCleaner.register();
		com.maxonaddon.util.ParadisAnimalUnstucker.register();
		com.maxonaddon.ReputationHandler.register();
		com.maxonaddon.DeliveryQuestHandler.register();
		com.maxonaddon.RescueQuestHandler.register();
		com.maxonaddon.SurvivorQuestHandler.register();
		com.maxonaddon.OutpostHuntQuestHandler.register();
		com.maxonaddon.HeroPerkHandler.register();
		ScoutSpawner.init();
		MaxonConfig.registerGameRules();
		MaxonConfig.load();

		// Тикаем отложенные звуки (сейчас — лязг затвора винтовки с задержкой, см.
		// scheduleDelayedSound/PendingSound выше).
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (pendingSounds.isEmpty()) return;
			for (PendingSound pending : pendingSounds) {
				pending.ticksLeft--;
				if (pending.ticksLeft <= 0) {
					pending.level.playSound(null, pending.x, pending.y, pending.z, pending.sound, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
					pendingSounds.remove(pending);
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Map<UUID, Float> charges = com.maxonaddon.mixin.AwakenedPowerTrackerAccessor.getCharges();

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (charges.containsKey(player.getUUID()) && player.getTags().contains("dannys-aot:awakened_power")) {
					IPlayerStats stats = (IPlayerStats) player;


					final float BASE_DRAIN = 0.6666667f;
					float extraDrain = 0.0f;

					if (!stats.hasSkill("ackerman_control_2")) {
						if (stats.hasSkill("ackerman_control_1")) {
							extraDrain = BASE_DRAIN * 0.5f; // x1.5 суммарно
						} else {
							extraDrain = BASE_DRAIN; // x2 суммарно (без скилла контроля вообще)
						}
					}

					// Перк "Герой Человечества" — доп. экономия заряда пробуждения (та же
					// "стамина Аккермана"/"время контроля титана" на активной силе) поверх
					// скиллов "Твёрдая рука Аккермана".
					double perkEndurance = com.maxonaddon.perk.PerkManager.ackermanEnduranceBonus(player);
					if (perkEndurance > 0.0 && extraDrain > 0.0f) {
						extraDrain *= (1.0f - (float) Math.min(0.9, perkEndurance));
					}

					if (extraDrain > 0.0f) {
						float current = charges.get(player.getUUID());
						charges.put(player.getUUID(), Math.max(0.0f, current - extraDrain));
					}
				}
			}
		});

		// === ПРОВЕРКА ЭФФЕКТА РЕГЕНЕРАЦИИ ДЛЯ СБРОСА ТРАВМ ДО 30 СЕК ===
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.tickCount % 20 == 0) {
					if (player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)) {
						IPlayerStats stats = (IPlayerStats) player;
						int targetTicks = 600; // 30 секунд
						boolean healedAny = false;

						for (com.maxonaddon.util.TraumaType type : com.maxonaddon.util.TraumaType.values()) {
							if (stats.getTraumaTimer(type) > targetTicks) {
								stats.setTraumaTimer(type, targetTicks);
								TraumaManager.syncTrauma(player, type);
								healedAny = true;
							}
						}

						if (healedAny) {
							player.displayClientMessage(
									Component.translatable("message.maxonaddon.trauma.healed_by_apple").withStyle(ChatFormatting.GOLD),
									true
							);
						}
					}
				}
				// === БЛОКИРОВКА ПРИ ПЕРЕЗАРЯДКЕ ГАЗА ===
				if (activeReloads.containsKey(player.getUUID())) {
					int ticks = activeReloads.get(player.getUUID());
					if (ticks > 0) {
						activeReloads.put(player.getUUID(), ticks - 1);
						// Жестко фиксируем на месте
						player.setDeltaMovement(0, 0, 0);
						player.hurtMarked = true;

						// Сообщаем клиенту, что он заблокирован (чтобы отключить мышку и кнопки)
						if (!player.getTags().contains("gas_reloading")) {
							player.addTag("gas_reloading");
						}
					} else {
						// Анимация закончилась, отпускаем
						activeReloads.remove(player.getUUID());
						player.removeTag("gas_reloading");
					}
				}
			}
		});

		// === ГЛОБАЛЬНАЯ ПРОВЕРКА НАВЫКА УПМ (ТИХИЙ ДЖЕМ) ===
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				((IPlayerStats) player).incrementTicksPlayed();
				com.maxonaddon.util.TraumaManager.tickTraumas(player);
				com.maxonaddon.util.BloodManager.serverTick(player);
				com.maxonaddon.util.WillToLiveHandler.tickCooldowns(player);
				com.maxonaddon.util.WillToLiveHandler.checkLowHealth(player);

				IPlayerStats stats = (IPlayerStats) player;

				// === ЖЕСТКАЯ БЛОКИРОВКА НОГ (БЕГ) ПРИ ТРАВМЕ ===
				if (stats.hasTrauma(com.maxonaddon.util.TraumaType.LEGS)) {
					if (player.isSprinting()) {
						player.setSprinting(false);
					}
				}

				// === ЖЕСТКАЯ БЛОКИРОВКА ЛЕВОЙ РУКИ ПРИ ТРАВМЕ ===
				if (stats.hasTrauma(com.maxonaddon.util.TraumaType.ARMS)) {
					ItemStack offhand = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
					if (!offhand.isEmpty()) {
						player.drop(offhand.copy(), true, false);
						player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
					}
				}

				// === ПРОВЕРКА НАВЫКА И ПРОЧНОСТИ УПМ/АПГ ===
				// isODMGear (не только буквально ODMGearItem) — иначе тик износа/поломки
				// для АПГ не вызывался бы вообще, и кастомная прочность у него не работала.
				ItemStack legs = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
				if (daot.DannysAot.isODMGear(legs.getItem())) {
					// 1. Сначала проверяем, есть ли вообще навык на УПМ
					if (!stats.hasOdmMastery()) {
						// Если навыка нет — перманентный клин (отцепляет крюки)
						if (player.tickCount % 20 == 0) {
							ServerPlayNetworking.send(player, new SilentJamPayload(2000));

							// Пишем над панелью быстрого доступа (ActionBar, true), что нужен навык
							player.displayClientMessage(
									net.minecraft.network.chat.Component.translatable("gui.maxonaddon.skills.odm_required"),
									true
							);
						}
					} else {
						// 2. Если навык есть — проверяем износ, сырость и прочность (твоя утилита)
						com.maxonaddon.util.ODMDamageManager.serverTick(player);
					}
				}
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(DashPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				IPlayerStats stats = (IPlayerStats) player;
				ItemStack legItem = player.getItemBySlot(EquipmentSlot.LEGS);

				boolean isCrackedRuleEnabled = player.level().getGameRules().getBoolean(MaxonConfig.DO_ODM_CRACKED);
				if (isCrackedRuleEnabled && com.maxonaddon.util.ODMDamageManager.getDurability(legItem) <= 0) {
					player.displayClientMessage(Component.translatable("message.maxonaddon.odm.broken"), true);
					return;
				}

				if (!DannysAot.isODMGear(legItem.getItem())) return;
				if (!hasOdmTriggers(player)) return;
				// Верхом рывок разрешён — это спрыг с седла: спешиваемся, а лошадь по инерции
				// уносится дальше (см. RunawayHorseTracker). Если игрок сидит не на лошади,
				// а на чём-то другом, рывка не будет вовсе.
				if (player.isPassenger()) {
					// Пакет мог прийти и в обход клиентской проверки — навык и настройку
					// сверяем здесь тоже.
					if (!MaxonConfig.get().enableOdmPhysics) return;
					if (!stats.hasRiderDismount()) {
						player.displayClientMessage(Component.translatable(
								"message.maxonaddon.skills.rider_dismount_required"), true);
						return;
					}
					if (!com.maxonaddon.util.RunawayHorseTracker.dismountAndRun(player)) return;
				}

				if (!stats.hasDashMastery()) {
					player.displayClientMessage(Component.translatable("gui.maxonaddon.skills.dash_required"), true);
					return;
				}

				int currentGas = DannysAot.getGasFromGear(legItem);
				if (currentGas < 20) {
					player.displayClientMessage(Component.translatable("message.maxonaddon.dash.not_enough_gas"), true);
					return;
				}

				DannysAot.consumeGasFromGear(legItem, 20, player);
				Vec3 look = player.getLookAngle();
				Vec3 dashVec = new Vec3(look.x * 2, look.y * 2 + 0.3, look.z * 2);

				player.setDeltaMovement(dashVec);
				player.hurtMarked = true;
				player.fallDistance = 0.0F;
				player.addTag("odm_dashing");

				ServerLevel level = (ServerLevel) player.level();
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.5F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.4F, 2.0F);

				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
						player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
				level.sendParticles(ParticleTypes.CLOUD,
						player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.3, 0.3, 0.3, 0.2);

				detachHooksFromPlayer(player);
			});
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);

			if (world.isClientSide()) {
				return InteractionResultHolder.pass(stack);
			}

			String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

			if (itemId.contains("syringe") || itemId.contains("spinal_fluid")) {
				player.removeTag("has_shifted_before");
				player.removeTag("berserk_boss");
			}

			return InteractionResultHolder.pass(stack);
		});

		ServerPlayNetworking.registerGlobalReceiver(BackDashPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				IPlayerStats stats = (IPlayerStats) player;
				ItemStack legItem = player.getItemBySlot(EquipmentSlot.LEGS);

				if (!DannysAot.isODMGear(legItem.getItem())) return;
				if (!hasOdmTriggers(player)) return;
				// Верхом рывок разрешён — это спрыг с седла: спешиваемся, а лошадь по инерции
				// уносится дальше (см. RunawayHorseTracker). Если игрок сидит не на лошади,
				// а на чём-то другом, рывка не будет вовсе.
				if (player.isPassenger()) {
					// Пакет мог прийти и в обход клиентской проверки — навык и настройку
					// сверяем здесь тоже.
					if (!MaxonConfig.get().enableOdmPhysics) return;
					if (!stats.hasRiderDismount()) {
						player.displayClientMessage(Component.translatable(
								"message.maxonaddon.skills.rider_dismount_required"), true);
						return;
					}
					if (!com.maxonaddon.util.RunawayHorseTracker.dismountAndRun(player)) return;
				}

				boolean isCrackedRuleEnabled = player.level().getGameRules().getBoolean(MaxonConfig.DO_ODM_CRACKED);
				if (isCrackedRuleEnabled && com.maxonaddon.util.ODMDamageManager.getDurability(legItem) <= 0) {
					player.displayClientMessage(Component.translatable("message.maxonaddon.odm.broken"), true);
					return;
				}

				if (!stats.hasDashMastery()) {
					player.displayClientMessage(Component.translatable("gui.maxonaddon.skills.dash_required"), true);
					return;
				}

				int currentGas = DannysAot.getGasFromGear(legItem);
				if (currentGas < 20) {
					player.displayClientMessage(Component.translatable("message.maxonaddon.dash.not_enough_gas"), true);
					return;
				}

				DannysAot.consumeGasFromGear(legItem, 20, player);
				Vec3 look = player.getLookAngle();
				Vec3 dashVec = new Vec3(-look.x * 2, 0.4, -look.z * 2);

				player.setDeltaMovement(dashVec);
				player.hurtMarked = true;
				player.fallDistance = 0.0F;
				player.addTag("odm_dashing");

				ServerLevel level = (ServerLevel) player.level();
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.5F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.4F, 2.0F);

				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
						player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
				level.sendParticles(ParticleTypes.CLOUD,
						player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.3, 0.3, 0.3, 0.2);

				detachHooksFromPlayer(player);
			});
		});

		// Подтягивание зацепленной тросом сущности. Физику считает клиент (он владеет крюками),
		// сервер только применяет импульс — и проверяет его, потому что пакет клиентский:
		// далёкую цель не трогаем, силу режем по модулю, иначе мобов уносило бы за карту.
		ServerPlayNetworking.registerGlobalReceiver(com.maxonaddon.network.PullEntityPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				if (player == null) return;

				net.minecraft.world.entity.Entity target = player.level().getEntity(payload.entityId());
				if (target == null || target.distanceTo(player) > 150.0) return;

				net.minecraft.world.phys.Vec3 force = payload.force();
				if (force.lengthSqr() > 9.0) force = force.normalize().scale(3.0);

				target.setDeltaMovement(target.getDeltaMovement().add(force));
				target.hurtMarked = true; // без этого сдвиг увидит только сам клиент
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestJamPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayer player = context.player();
				ServerPlayNetworking.send(player, new ODMJamPayload(1000));
			});
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				net.minecraft.world.entity.Entity attacker = source.getEntity();

				if (attacker instanceof ShifterTitan || attacker instanceof OgreTitanEntity || attacker instanceof net.minecraft.world.entity.monster.Monster) {

					// Наносим урон прочности УПМ/АПГ при ударе титана (damageOdm сам фильтрует
					// по DannysAot.isODMGear — вызывать безопасно на любом предмете в ногах).
					ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
					com.maxonaddon.util.ODMDamageManager.damageOdm(player, legs, 15);

					// Берем порог урона и шанс из конфига

					MaxonConfig config = MaxonConfig.get();
					double traumaChance = config.traumaChanceOnDamage;
					IPlayerStats stats = (IPlayerStats) player;

// Наслаиваем Железные кости: снижаем базовую вероятность травмы
					if (stats.hasSkill("iron_bones_1")) {
						traumaChance -= 0.02; // -2% вероятности
					}
					if (stats.hasSkill("iron_bones_2")) {
						traumaChance -= 0.02; // еще -2% вероятности (в сумме -4%)
					}

// Защита от ухода в отрицательный процент
					if (traumaChance < 0) traumaChance = 0.0;

					if (amount >= config.traumaDamageThreshold && player.getRandom().nextDouble() < traumaChance) {
						TraumaManager.applyRandomTrauma(player);
					}
				}
			}
			return true;
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {

				if (player.getTags().contains("titan_blocking_damage")) {
					return true;
				}

				if (player.getTags().contains("odm_dashing") && source.is(DamageTypes.FALL)) {
					player.removeTag("odm_dashing");
					return false;
				}

				if (source.is(DamageTypes.FALL) && RoyalTitanInteractHandler.fallImmunity.containsKey(player.getUUID())) {
					return false;
				}

				net.minecraft.world.entity.Entity attacker = source.getEntity();



				if (attacker instanceof ServerPlayer attackingPlayer) {
					ItemStack attackerMain = attackingPlayer.getMainHandItem();
					ItemStack attackerOff = attackingPlayer.getOffhandItem();

					if (attackerMain.getItem() instanceof daot.BladeItem || attackerOff.getItem() instanceof daot.BladeItem) {
						if (daot.BladeBlockTracker.isBlocking(player)) {
							ItemStack defenderMain = player.getMainHandItem();
							ItemStack defenderOff = player.getOffhandItem();

							if (defenderMain.getItem() instanceof daot.BladeItem && defenderOff.getItem() instanceof daot.BladeItem) {
								if (daot.BladeItem.getBladeState(defenderMain) != daot.BladeItem.BladeState.EMPTY &&
										daot.BladeItem.getBladeState(defenderOff) != daot.BladeItem.BladeState.EMPTY) {

									Integer blockStartTick = blockStartTicks.get(player.getUUID());
									int currentTick = player.serverLevel().getServer().getTickCount();

									if (blockStartTick != null && (currentTick - blockStartTick) <= 20) {
										net.minecraft.world.item.Item bladeItem = attackerMain.getItem() instanceof daot.BladeItem ? attackerMain.getItem() : attackerOff.getItem();
										attackingPlayer.getCooldowns().addCooldown(bladeItem, 40);

										if (player.level() instanceof ServerLevel serverLevel) {
											serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
													net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
											serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
													net.minecraft.sounds.SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.4F, 2.0F);

											serverLevel.sendParticles(ParticleTypes.CRIT,
													player.getX(), player.getY() + 1.0, player.getZ(),
													15, 0.5, 0.5, 0.5, 0.2);
										}

										Vec3 pushBack = attackingPlayer.position().subtract(player.position()).normalize().scale(0.5).add(0.0, 0.2, 0.0);
										attackingPlayer.setDeltaMovement(pushBack);
										attackingPlayer.hurtMarked = true;

										daot.BladeItem.damageBladeByAmount(defenderMain, 5, player);
										daot.BladeItem.damageBladeByAmount(defenderOff, 5, player);

										return false;
									}
								}
							}
						}
					}
				}

				if (attacker instanceof ShifterTitan || attacker instanceof OgreTitanEntity) {
					if (BladeBlockTracker.isBlocking(player)) {
						ItemStack mainHand = player.getMainHandItem();
						ItemStack offHand = player.getOffhandItem();

						if (mainHand.getItem() instanceof BladeItem && offHand.getItem() instanceof BladeItem) {
							if (BladeItem.getBladeState(mainHand) != BladeItem.BladeState.EMPTY &&
									BladeItem.getBladeState(offHand) != BladeItem.BladeState.EMPTY) {

								BladeItem.damageBladeByAmount(mainHand, 40, player);
								BladeItem.damageBladeByAmount(offHand, 40, player);
								BladeBlockTracker.breakBlock(player);

								Vec3 knockbackDir = player.position().subtract(attacker.position()).normalize();
								player.setDeltaMovement(knockbackDir.scale(4.5).add(0.0, 0.9, 0.0));
								player.hurtMarked = true;
								player.hasImpulse = true;

								if (player.level() instanceof ServerLevel serverLevel) {
									serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
											net.minecraft.sounds.SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 2.0F, 0.8F);

									serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()),
											player.getX(), player.getY() + 1.0, player.getZ(),
											60, 0.5, 0.5, 0.5, 0.15);
								}

								player.addTag("titan_blocking_damage");
								player.hurt(source, amount * 0.5F);
								player.removeTag("titan_blocking_damage");

								return false;
							}
						}
					}
				}
			}

			return true;
		});
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			com.maxonaddon.command.TraumaCommand.register(dispatcher);
			com.maxonaddon.command.PerkCommand.register(dispatcher);
			SpawnCarriageCommand.register(dispatcher);
			dispatcher.register(Commands.literal("spawnboulder")
					.requires(source -> source.hasPermission(2))
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayer();
						if (player != null) {
							TitanBoulderEntity boulder = new TitanBoulderEntity(ModEntities.TITAN_BOULDER, player.level());
							boulder.setPos(player.getX(), player.getY(), player.getZ());
							player.level().addFreshEntity(boulder);
							context.getSource().sendSuccess(() -> Component.translatable("command.maxonaddon.spawnboulder.success"), false);
						}
						return 1;
					}));
		});
	}


	private static boolean hasOdmTriggers(ServerPlayer player) {
		net.minecraft.world.item.Item mainItem = player.getMainHandItem().getItem();
		net.minecraft.world.item.Item offItem = player.getOffhandItem().getItem();

		// Проверяем мечи и обычные пушки через instanceof
		boolean mainIsBladeOrGun = mainItem instanceof daot.BladeItem || mainItem instanceof daot.APGGunItem;
		boolean offIsBladeOrGun = offItem instanceof daot.BladeItem || offItem instanceof daot.APGGunItem;

		// Проверяем THUNDER_GUN_ITEM через точноеравнение (==)
		boolean mainIsThunder = mainItem == MaxonDaotAddon.THUNDER_GUN_ITEM;
		boolean offIsThunder = offItem == MaxonDaotAddon.THUNDER_GUN_ITEM;

		return mainIsBladeOrGun || offIsBladeOrGun || mainIsThunder || offIsThunder;
	}

	/**
	 * Единая точка правды по флагам скилов/статистики/страха. Рассылается каждому
	 * онлайн-игроку при входе на сервер, а также немедленно всем игрокам при изменении
	 * геймрула allowFearSystem (см. changedCallback в MaxonConfig.registerGameRules()).
	 * Раньше клиент сам читал client.level.getGameRules(), что не гарантирует мгновенную
	 * и надёжную синхронизацию у всех уже подключённых игроков — теперь сервер сам явно
	 * толкает актуальное значение.
	 */
	public static void broadcastModFlags(net.minecraft.server.MinecraftServer server) {
		boolean fearEnabled = server.getGameRules().getBoolean(MaxonConfig.ALLOW_FEAR_SYSTEM);
		SyncModFlagsPayload payload = new SyncModFlagsPayload(MaxonConfig.get().statisticsSystemEnabled, fearEnabled, MaxonConfig.isReputationEnabled());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static void detachHooksFromPlayer(ServerPlayer dashingPlayer) {
		ServerLevel level = dashingPlayer.serverLevel();

		for (ServerPlayer attacker : level.players()) {
			if (attacker == dashingPlayer) continue;

			if (ServerHookTracker.hasActiveHook(attacker.getUUID())) {
				ServerPlayNetworking.send(attacker, new ForceDetachPayload(dashingPlayer.getId()));
			}
		}
	}

	// === МАРЛИЙСКАЯ ВИНТОВКА ===
	// По той же схеме, что Thunder Gun выше: клиент только шлёт "хочу выстрелить"/"хочу
	// перезарядить" (MarleyanRifleActionPayload), а сервер — единственный источник истины
	// по патронам (NBT предмета, см. MarleyanRifleItem) и единственный, кто реально
	// наносит урон.
	private static void shootMarleyanRifle(ServerPlayer player, InteractionHand hand, boolean aiming) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() != MaxonDaotAddon.MARLEYAN_RIFLE_ITEM) return;
		// Кулдаун — он же не даёт стрелять быстрее, чем отыгрывает анимация "shoot"+"bolt_cycle",
		// он же бесплатно даёт стандартную диагональную полоску перезарядки на иконке в хотбаре.
		if (player.getCooldowns().isOnCooldown(stack.getItem())) return;

		int loaded = com.maxonaddon.item.MarleyanRifleItem.getLoadedRounds(stack);
		if (loaded <= 0 && !player.isCreative()) return;

		if (!player.isCreative()) {
			com.maxonaddon.item.MarleyanRifleItem.setLoadedRounds(stack, loaded - 1);
		}
		player.getCooldowns().addCooldown(stack.getItem(), com.maxonaddon.item.MarleyanRifleItem.FIRE_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		double range = 64.0;
		Vec3 eyePos = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 endPos = eyePos.add(look.scale(range));

		// Урезаем дистанцию до первой преграды из блоков...
		net.minecraft.world.phys.BlockHitResult blockHit = level.clip(new net.minecraft.world.level.ClipContext(
				eyePos, endPos,
				net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE,
				player
		));
		if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			endPos = blockHit.getLocation();

			// Стекло пуля прошибает насквозь — но на нём же и гаснет. Отдельно "гасить"
			// ничего не нужно: endPos уже обрезан ровно этим блоком, а поиск сущностей ниже
			// идёт только до endPos, так что тот, кто стоит за стеклом, урона не получит.
			net.minecraft.core.BlockPos hitPos = blockHit.getBlockPos();
			if (isBulletBreakableGlass(level.getBlockState(hitPos))) {
				// dropBlock=false: стекло и в ванили без "Шёлкового касания" ничего не роняет,
				// а осколки от пули тем более собирать нечего. Сам destroyBlock уже проигрывает
				// звук и частицы разрушения, отдельный эффект не нужен.
				level.destroyBlock(hitPos, false, player);
			}
		}

		// ...и ищем первую живую сущность на этом отрезке (хитскан — пуля, не снаряд с полётом).
		net.minecraft.world.phys.AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		net.minecraft.world.phys.EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
				level, player, eyePos, endPos, searchBox,
				e -> e instanceof net.minecraft.world.entity.LivingEntity && !e.isSpectator() && e.isPickable() && e != player
		);

		if (entityHit != null) {
			entityHit.getEntity().hurt(player.damageSources().playerAttack(player), 10.0F);
		}

		// Дульная вспышка: точную мировую позицию кости "particle" с сервера не достать — она
		// существует только в render-контексте GeckoLib на клиенте. Берём точку перед глазами
		// игрока по направлению взгляда — для партиклов выстрела точности блока хватает с запасом.
		Vec3 muzzlePos = eyePos.add(look.scale(1.2));
		level.sendParticles(ParticleTypes.FLAME, muzzlePos.x, muzzlePos.y, muzzlePos.z, 6, 0.05, 0.05, 0.05, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzlePos.x, muzzlePos.y, muzzlePos.z, 4, 0.05, 0.05, 0.05, 0.01);

		// "Бах" — сразу; лязг затвора — с задержкой в 0.2с (4 тика) от начала анимации
		// bolt_reload, как и просили (см. scheduleDelayedSound ниже).
		level.playSound(null, player.getX(), player.getY(), player.getZ(), com.maxonaddon.sound.ModSounds.RIFLE_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
		scheduleDelayedSound(level, player.getX(), player.getY(), player.getZ(), com.maxonaddon.sound.ModSounds.RIFLE_BOLT_RELOAD, 4);

		if (stack.getItem() instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
			// Если стреляли держа ПКМ (прицел) — "fire_aim" (aim_shoot+bolt_cycle), иначе
			// обычная "fire" (shoot+bolt_cycle).
			String animName = aiming ? "fire_aim" : "fire";
			geoItem.triggerAnim(player, software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, level), "controller", animName);
		}

		// Лёгкая отдача — по образцу ThunderGunItem.shootSpear(), но заметно слабее (винтовка
		// легче громовой пушки и не толкает игрока в воздух).
		Vec3 recoil = look.normalize().scale(-0.4);
		player.setDeltaMovement(player.getDeltaMovement().add(recoil.x, Math.max(0.0, recoil.y * 0.2), recoil.z));
		player.hurtMarked = true;
		player.hasImpulse = true;
	}

	/**
	 * Стекло ли это с точки зрения пули. Тег minecraft:impermeable — это ровно все стеклянные
	 * БЛОКИ ванили (обычное, все 16 цветных, затемнённое), панели в него не входят, поэтому
	 * они добавлены отдельно. Через тег, а не через instanceof, ещё и потому, что модовое
	 * стекло обычно тоже помечают этим тегом — такое разобьётся само собой, без правок здесь.
	 * Намеренно НЕ ловим всё подряд с SoundType.GLASS: туда попадают, например, редстоун-лампы,
	 * дырявить их выстрелом никто не просил.
	 */
	private static boolean isBulletBreakableGlass(net.minecraft.world.level.block.state.BlockState state) {
		return state.is(net.minecraft.tags.BlockTags.IMPERMEABLE)
				|| state.is(net.minecraft.world.level.block.Blocks.GLASS_PANE)
				|| state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock;
	}

	// Только анимация "aim" (см. controller в MarleyanRifleItem) — само приближение экрана
	// (FOV) чисто клиентское и сервер не трогает, см. GameRendererMixin + rifleAimProgress.
	private static void aimMarleyanRifle(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() != MaxonDaotAddon.MARLEYAN_RIFLE_ITEM) return;

		if (stack.getItem() instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
			geoItem.triggerAnim(player, software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, player.serverLevel()), "controller", "aim");
		}
	}

	// Выход из зафиксированной позы прицела (thenPlayAndHold сама никогда не заканчивается —
	// см. controller в MarleyanRifleItem) обратно на обычный looping idle.
	private static void unaimMarleyanRifle(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() != MaxonDaotAddon.MARLEYAN_RIFLE_ITEM) return;

		if (stack.getItem() instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
			geoItem.triggerAnim(player, software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, player.serverLevel()), "controller", "unaim");
		}
	}

	// Анимация "доставания" при появлении винтовки в главной руке (см. клиентский edge-детект
	// wasHoldingRifleMain в MaxonDaotAddonClient) — только анимация самого предмета, ту же
	// "draw" для рук игрока клиент запускает локально сам через OdmAnimHelper.playReload.
	private static void drawMarleyanRifle(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() != MaxonDaotAddon.MARLEYAN_RIFLE_ITEM) return;

		if (stack.getItem() instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
			geoItem.triggerAnim(player, software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, player.serverLevel()), "controller", "draw");
		}
	}

	// Перезарядка сразу ЦЕЛОЙ обоймой (не по одному патрону за нажатие) — анимация "reload"
	// достаточно долгая (~3.2с), чтобы это было правдоподобно, да и по факту так и просили.
	// Забирает патроны из ВСЕХ подходящих стаков в инвентаре, если они лежат россыпью
	// по нескольким слотам, а не только из первого найденного.
	/**
	 * Кому засчитать убийство титана.
	 * <p>
	 * В облике титана удар наносит САМ шифтер, а игрок сидит в нём пассажиром — событие
	 * приходит от титана, и проверка "убийца это ServerPlayer" его не пропускала. Из-за
	 * этого за бой в облике титана не начислялось вообще ничего: ни фраг в статистику, ни
	 * очки титана, ни прогресс квеста. Ветка начисления очков при этом существовала и была
	 * верной — до неё просто не доходило исполнение.
	 * <p>
	 * Возвращает игрока-наездника (getIndirectPassengers, чтобы поймать и посадку через
	 * промежуточную сущность) или null, если убийца к игроку отношения не имеет.
	 */
	private static ServerPlayer resolveTitanKillCredit(net.minecraft.world.entity.Entity killer) {
		if (killer instanceof ServerPlayer player) return player;
		if (!(killer instanceof daot.ShifterTitan)) return null;

		for (net.minecraft.world.entity.Entity passenger : killer.getIndirectPassengers()) {
			if (passenger instanceof ServerPlayer player) return player;
		}
		return null;
	}

	private static void reloadMarleyanRifle(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() != MaxonDaotAddon.MARLEYAN_RIFLE_ITEM) return;

		int loaded = com.maxonaddon.item.MarleyanRifleItem.getLoadedRounds(stack);
		int needed = com.maxonaddon.item.MarleyanRifleItem.MAX_LOADED_ROUNDS - loaded;
		if (needed <= 0) return;

		int consumed;
		if (player.isCreative()) {
			consumed = needed;
		} else {
			int available = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).getItem() instanceof daot.APGCartridgeItem) {
					available += player.getInventory().getItem(i).getCount();
				}
			}
			if (available <= 0) return;

			consumed = Math.min(needed, available);
			int toRemove = consumed;
			for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
				ItemStack invStack = player.getInventory().getItem(i);
				if (invStack.getItem() instanceof daot.APGCartridgeItem) {
					int shrinkAmount = Math.min(toRemove, invStack.getCount());
					invStack.shrink(shrinkAmount);
					toRemove -= shrinkAmount;
				}
			}
		}

		com.maxonaddon.item.MarleyanRifleItem.setLoadedRounds(stack, loaded + consumed);

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				com.maxonaddon.sound.ModSounds.RIFLE_FULL_RELOAD, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

		if (stack.getItem() instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
			geoItem.triggerAnim(player, software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, player.serverLevel()), "controller", "reload");
		}
	}

	// === ОТЛОЖЕННЫЕ ЗВУКИ (сейчас только лязг затвора винтовки — см. shootMarleyanRifle) ===
	// Минимальный самодельный планировщик: своего готового механизма "выполнить через N
	// тиков" в проекте не было, а лезть в чужой activeReloads (у него другая семантика —
	// блокировка ввода игрока) не хотелось. CopyOnWriteArrayList — чтобы безопасно удалять
	// элементы прямо во время обхода в тик-обработчике ниже, частота событий тут низкая.
	private static final class PendingSound {
		final ServerLevel level;
		final double x, y, z;
		final net.minecraft.sounds.SoundEvent sound;
		int ticksLeft;

		PendingSound(ServerLevel level, double x, double y, double z, net.minecraft.sounds.SoundEvent sound, int ticksLeft) {
			this.level = level;
			this.x = x;
			this.y = y;
			this.z = z;
			this.sound = sound;
			this.ticksLeft = ticksLeft;
		}
	}

	private static final java.util.List<PendingSound> pendingSounds = new java.util.concurrent.CopyOnWriteArrayList<>();

	private static void scheduleDelayedSound(ServerLevel level, double x, double y, double z, net.minecraft.sounds.SoundEvent sound, int delayTicks) {
		pendingSounds.add(new PendingSound(level, x, y, z, sound, delayTicks));
	}

	private static void tryLoadThunderGun(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() instanceof com.maxonaddon.item.ThunderGunItem) {
			int loaded = com.maxonaddon.item.ThunderGunItem.getLoadedSpears(stack);

			// 1. Если пушка полностью заряжена (2 копья) -> ВЫТАСКИВАЕМ ВСЁ
			if (loaded == com.maxonaddon.item.ThunderGunItem.MAX_LOADED_SPEARS) {
				unloadAllSpears(player, stack, loaded);
				return;
			}

			// 2. Если пушка не полная -> Ищем копье в инвентаре для дозарядки
			int spearSlot = -1;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).getItem() instanceof daot.ThunderSpearItem) {
					spearSlot = i;
					break;
				}
			}

			if (spearSlot != -1 || player.isCreative()) {
				// Если копье есть -> Заряжаем 1 штуку
				if (!player.isCreative()) {
					player.getInventory().getItem(spearSlot).shrink(1);
				}
				com.maxonaddon.item.ThunderGunItem.setLoadedSpears(stack, loaded + 1);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.CROSSBOW_LOADING_END, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

				// Анимация зарядки: 0 -> load, 1 -> load2
				String animName = (loaded == 0) ? "load" : "load2";
				if (stack.getItem() instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
					geoItem.triggerAnim(player, software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, player.serverLevel()), "controller", animName);
				}
			} else if (loaded > 0) {
				// Если копий в инвентаре больше нет, но 1 заряд уже в пушке -> ВЫТАСКИВАЕМ ЕГО
				unloadAllSpears(player, stack, loaded);
			}
		}
	}

	private static void unloadAllSpears(ServerPlayer player, ItemStack stack, int loaded) {
		// Создаем стак с количеством копий, равным текущим зарядам
		ItemStack spearsToGive = new ItemStack(daot.DannysAot.THUNDER_SPEAR, loaded);

		// Пытаемся положить в инвентарь. Если места нет - бросаем на землю
		if (!player.getInventory().add(spearsToGive)) {
			player.drop(spearsToGive, false);
		}

		// Обнуляем заряды пушки
		com.maxonaddon.item.ThunderGunItem.setLoadedSpears(stack, 0);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

		// Анимация разрядки: 2 -> unload2, 1 -> unload
		String animName = (loaded == 2) ? "unload2" : "unload";
		if (stack.getItem() instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
			geoItem.triggerAnim(player, software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, player.serverLevel()), "controller", animName);
		}
	}

	/** Перехват уже установлен — SERVER_STARTING срабатывает на каждый вход в мир, а не один раз. */
	private static boolean daotReloadInterceptionInstalled = false;

	/**
	 * Подменяет обработчики перезарядки базового мода на свои (см. performMaxonReload).
	 * Ошибки здесь намеренно не роняют мод целиком: если dannys-aot однажды переименует или
	 * уберёт эти пакеты, разумнее остаться без своей перезарядки и записать это в лог, чем
	 * не дать игре запуститься.
	 */
	private static void installDaotReloadInterception() {
		if (daotReloadInterceptionInstalled) return;
		daotReloadInterceptionInstalled = true;

		try {
			// 1. Снимаем регистрацию с оригинальных обработчиков мода
			ServerPlayNetworking.unregisterGlobalReceiver(daot.network.BladeReloadPayload.TYPE.id());
			ServerPlayNetworking.unregisterGlobalReceiver(daot.network.APGReloadPayload.TYPE.id());

			// 2. Регистрируем наши собственные обработчики на те же самые пакеты
			ServerPlayNetworking.registerGlobalReceiver(daot.network.BladeReloadPayload.TYPE, (payload, context) -> {
				context.server().execute(() -> {
					if (context.player() != null) performMaxonReload(context.player());
				});
			});

			ServerPlayNetworking.registerGlobalReceiver(daot.network.APGReloadPayload.TYPE, (payload, context) -> {
				context.server().execute(() -> {
					if (context.player() != null) performMaxonReload(context.player());
				});
			});
		} catch (Exception e) {
			LOGGER.warn("[MaxonAddon] Не удалось перехватить перезарядку dannys-aot: {}", e.toString());
		}
	}

	/**
	 * Ставит в руку свежий клинок из УПМ. В творческом режиме — просто ставит.
	 *
	 * @return true, если из снаряжения был израсходован компонент
	 */
	private static boolean maxon$loadBladeFromGear(ServerPlayer player, ItemStack handStack,
	                                               net.minecraft.core.NonNullList<ItemStack> items) {
		if (player.isCreative()) {
			daot.BladeItem.reloadBlade(handStack);
			maxon$playBladeReloadSound(player);
			return false;
		}

		boolean hasLeft = items.get(0).getItem() instanceof daot.BladeComponentItem && items.get(0).getCount() > 0;
		boolean hasRight = items.get(1).getItem() instanceof daot.BladeComponentItem && items.get(1).getCount() > 0;

		// Берём из того слота, где клинков больше — так запас расходуется равномерно.
		int takeIndex = -1;
		if (hasLeft && hasRight) takeIndex = (items.get(0).getCount() >= items.get(1).getCount()) ? 0 : 1;
		else if (hasLeft) takeIndex = 0;
		else if (hasRight) takeIndex = 1;

		if (takeIndex == -1) return false;

		items.get(takeIndex).shrink(1);
		daot.BladeItem.reloadBlade(handStack);
		maxon$playBladeReloadSound(player);
		return true;
	}

	private static void maxon$playBladeReloadSound(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.5F);
	}

	private static void performMaxonReload(ServerPlayer player) {
		com.maxonaddon.util.IPlayerStats stats = (com.maxonaddon.util.IPlayerStats) player;
		ItemStack legItem = player.getItemBySlot(EquipmentSlot.LEGS);
		boolean isOdm = legItem.getItem() instanceof daot.ODMGearItem;
		boolean isApg = legItem.getItem() instanceof daot.ODMAPGItem;
		if (!isOdm && !isApg) return;

		net.minecraft.world.item.component.ItemContainerContents contents = legItem.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
		net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(2, ItemStack.EMPTY);
		contents.copyInto(items);

		boolean inventoryChanged = false;

		boolean hasCartridges = items.get(0).getItem() instanceof daot.APGCartridgeItem || items.get(1).getItem() instanceof daot.APGCartridgeItem;
		boolean hasBlades = items.get(0).getItem() instanceof daot.BladeComponentItem || items.get(1).getItem() instanceof daot.BladeComponentItem;

		boolean canUseBlades = (isOdm && !hasCartridges) || (isApg && stats.hasConnectorMastery() && !hasCartridges);
		boolean canUseGuns = (isApg && !hasBlades) || (isOdm && stats.hasConnectorMastery() && !hasBlades);

		InteractionHand[] hands = {InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND};
		for (InteractionHand hand : hands) {
			ItemStack handStack = player.getItemInHand(hand);

			// --- ЛОГИКА МЕЧЕЙ ---
			if (canUseBlades && handStack.getItem() instanceof daot.BladeItem) {
				if (daot.BladeItem.getBladeState(handStack) == daot.BladeItem.BladeState.EMPTY) {
					if (maxon$loadBladeFromGear(player, handStack, items)) inventoryChanged = true;
				} else {
					// Клинок в руке ещё не пуст. Изношенный меняем СРАЗУ, одним нажатием:
					// выбрасываем и тут же ставим свежий. Раньше выброс и установка были
					// разными нажатиями, и со стороны это выглядело как «лезвия просто
					// выпали и не зарядились» — приходилось жать второй раз.
					// Целый клинок при этом только снимается: иначе его нельзя было бы убрать,
					// он возвращался бы в руку в тот же тик.
					boolean wornOut = daot.BladeItem.getBladeDamage(handStack) > 0;
					if (!player.isCreative() && daot.BladeItem.getBladeDamage(handStack) == 0) {
						boolean canPutLeft = !hasCartridges && (items.get(0).isEmpty() || (items.get(0).getItem() instanceof daot.BladeComponentItem && items.get(0).getCount() < 2));
						boolean canPutRight = !hasCartridges && (items.get(1).isEmpty() || (items.get(1).getItem() instanceof daot.BladeComponentItem && items.get(1).getCount() < 2));

						int putIndex = -1;
						if (canPutLeft && canPutRight) putIndex = (items.get(0).getCount() <= items.get(1).getCount()) ? 0 : 1;
						else if (canPutLeft) putIndex = 0;
						else if (canPutRight) putIndex = 1;

						if (putIndex != -1) {
							ItemStack slotStack = items.get(putIndex);
							if (slotStack.isEmpty()) items.set(putIndex, new ItemStack(daot.DannysAot.BLADE_COMPONENT, 1));
							else slotStack.grow(1);
							inventoryChanged = true;
						} else {
							ItemStack toReturn = new ItemStack(daot.DannysAot.BLADE_COMPONENT, 1);
							if (!player.getInventory().add(toReturn)) player.drop(toReturn, false);
						}
					}
					daot.BladeItem.ejectBlade(handStack, player, hand);

					if (wornOut) {
						ItemStack emptied = player.getItemInHand(hand);
						if (emptied.getItem() instanceof daot.BladeItem
								&& daot.BladeItem.getBladeState(emptied) == daot.BladeItem.BladeState.EMPTY
								&& maxon$loadBladeFromGear(player, emptied, items)) {
							inventoryChanged = true;
						}
					}
				}
			}

			// --- ЛОГИКА ОГНЕСТРЕЛА ---
			if (canUseGuns && handStack.getItem() instanceof daot.APGGunItem apgGun) {
				if (!daot.APGGunItem.isLoaded(handStack)) {
					if (player.isCreative()) {
						daot.APGGunItem.setLoaded(handStack, true);
						daot.APGGunItem.triggerAnim(player, handStack, "load");
						player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.CROSSBOW_LOADING_END, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.5F);
					} else {
						boolean hasLeft = items.get(0).getItem() instanceof daot.APGCartridgeItem && items.get(0).getCount() > 0;
						boolean hasRight = items.get(1).getItem() instanceof daot.APGCartridgeItem && items.get(1).getCount() > 0;

						int takeIndex = -1;
						if (hasLeft && hasRight) takeIndex = (items.get(0).getCount() >= items.get(1).getCount()) ? 0 : 1;
						else if (hasLeft) takeIndex = 0;
						else if (hasRight) takeIndex = 1;

						if (takeIndex != -1) {
							items.get(takeIndex).shrink(1);
							inventoryChanged = true;
							daot.APGGunItem.setLoaded(handStack, true);
							daot.APGGunItem.triggerAnim(player, handStack, "load");
							player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.CROSSBOW_LOADING_END, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.5F);
						}
					}
				} else {
					if (!player.isCreative()) {
						boolean canPutLeft = !hasBlades && (items.get(0).isEmpty() || (items.get(0).getItem() instanceof daot.APGCartridgeItem && items.get(0).getCount() < 3));
						boolean canPutRight = !hasBlades && (items.get(1).isEmpty() || (items.get(1).getItem() instanceof daot.APGCartridgeItem && items.get(1).getCount() < 3));

						int putIndex = -1;
						if (canPutLeft && canPutRight) putIndex = (items.get(0).getCount() <= items.get(1).getCount()) ? 0 : 1;
						else if (canPutLeft) putIndex = 0;
						else if (canPutRight) putIndex = 1;

						if (putIndex != -1) {
							ItemStack slotStack = items.get(putIndex);
							if (slotStack.isEmpty()) items.set(putIndex, new ItemStack(daot.DannysAot.APG_CARTRIDGE, 1));
							else slotStack.grow(1);
							inventoryChanged = true;
						} else {
							ItemStack toReturn = new ItemStack(daot.DannysAot.APG_CARTRIDGE, 1);
							if (!player.getInventory().add(toReturn)) player.drop(toReturn, false);
						}
					}
					daot.APGGunItem.setLoaded(handStack, false);
					daot.APGGunItem.triggerAnim(player, handStack, "unload");
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.5F);
				}
			}
		}

		if (inventoryChanged) {
			legItem.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.copyOf(items)));
		}
	}
	private static void tryUnloadThunderGun(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() instanceof com.maxonaddon.item.ThunderGunItem) {
			int loaded = com.maxonaddon.item.ThunderGunItem.getLoadedSpears(stack);
			if (loaded > 0) {
				// Возвращаем копье в инвентарь (или выкидываем на землю, если места нет)
				ItemStack spearToGive = new ItemStack(daot.DannysAot.THUNDER_SPEAR, 1);
				if (!player.getInventory().add(spearToGive)) {
					player.drop(spearToGive, false);
				}

				com.maxonaddon.item.ThunderGunItem.setLoadedSpears(stack, loaded - 1);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

				// Анимация разрядки: 2 -> unload2, 1 -> unload
				String animName = (loaded == 2) ? "unload2" : "unload";
				if (stack.getItem() instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
					geoItem.triggerAnim(player, software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, player.serverLevel()), "controller", animName);
				}
			}
		}
	}
	/**
	 * Куда закидывает игрока при первом входе в Парадиз (enableRandomParadisSpawn).
	 * <p>
	 * Только внутреннее кольцо — четыре округа Сины на ~1300 блоков от центра. Дальние кольца
	 * (~7300 и ~13300) отсюда убраны намеренно: спавн там означал, что игрок с первой секунды
	 * оказывается в городе, чьи чанки генерируются впервые, и ловит все огрехи дальней
	 * генерации. Дойти или доехать туда по-прежнему можно — просто мир начинается ближе.
	 * <p>
	 * Это НЕ трогает CarriageSpawner.SPAWN_POINTS: все 12 округов, возницы, маршруты и деление
	 * на три стены остаются на месте.
	 */
	private static final BlockPos[] PARADIS_SPAWN_POINTS = {
			new BlockPos(1295, 0, 13),
			new BlockPos(-1307, 0, 25),
			new BlockPos(-7, 0, 1331),
			new BlockPos(-9, 0, -1274)
	};

	private static ServerLevel getParadisLevel(net.minecraft.server.MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().location().toString().equals("dannys-aot:paradis")) {
				return level;
			}
		}
		return null;
	}

	// Вычисляет или назначает персональную точку спавна для конкретного игрока
	private static BlockPos getOrAssignPersonalParadisSpawn(ServerPlayer player, ServerLevel level) {
		int spawnIndex = -1;

		// Проверяем, есть ли у игрока уже закрепленная за ним точка
		for (String tag : player.getTags()) {
			if (tag.startsWith("paradis_spawn_idx_")) {
				try {
					spawnIndex = Integer.parseInt(tag.substring("paradis_spawn_idx_".length()));
					break;
				} catch (NumberFormatException ignored) {}
			}
		}

		// Если нет, выдаем случайную
		if (spawnIndex < 0 || spawnIndex >= PARADIS_SPAWN_POINTS.length) {
			spawnIndex = player.getRandom().nextInt(PARADIS_SPAWN_POINTS.length);

			java.util.List<String> tagsToRemove = new java.util.ArrayList<>();
			for (String tag : player.getTags()) {
				if (tag.startsWith("paradis_spawn_idx_")) tagsToRemove.add(tag);
			}
			for (String tag : tagsToRemove) player.removeTag(tag);

			player.addTag("paradis_spawn_idx_" + spawnIndex);
		}

		BlockPos basePos = PARADIS_SPAWN_POINTS[spawnIndex];

		// НАЧИНАЕМ ПОИСК СВЕРХУ (Y = 319), чтобы не застрять в пещере!
		BlockPos startSearchPos = new BlockPos(basePos.getX(), 319, basePos.getZ());
		return findGround(level, startSearchPos);
	}
	// Умный поиск пола
	private static BlockPos findGround(ServerLevel level, BlockPos startPos) {
		BlockPos.MutableBlockPos mut = startPos.mutable();

		// Если стартовая точка внутри твердого блока (в земле), поднимаемся вверх
		while (mut.getY() < 320 && !level.getBlockState(mut).getCollisionShape(level, mut).isEmpty()) {
			mut.move(Direction.UP);
		}

		// Спускаемся вниз до ближайшего твердого блока (пропуская воздух, траву, цветы без коллизии)
		while (mut.getY() > -64 && level.getBlockState(mut.below()).getCollisionShape(level, mut.below()).isEmpty()) {
			mut.move(Direction.DOWN);
		}

		return mut.immutable();
	}
	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}