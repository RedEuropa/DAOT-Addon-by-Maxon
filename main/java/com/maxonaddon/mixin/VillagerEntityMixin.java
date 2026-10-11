package com.maxonaddon.mixin;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.ai.FighterRetaliateGoal;
import com.maxonaddon.ai.PoliceDefendVillagersGoal;
import com.maxonaddon.ai.PoliceHuntSmugglerGoal;
import com.maxonaddon.ai.ScoutOdmCombatGoal;
import com.maxonaddon.ai.SmugglerFleePoliceGoal;
import com.maxonaddon.ai.TradingAwareStrollGoal;
import com.maxonaddon.util.DistrictHelper;
import com.maxonaddon.util.NightDutyHelper;
import com.maxonaddon.util.PoliceGearHelper;
import com.maxonaddon.util.SmugglerTradeHelper;
import com.maxonaddon.util.VillagerSkinAccessor;
import daot.BladeItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerEntityMixin extends AbstractVillager implements VillagerSkinAccessor {

    @Unique
    private static final EntityDataAccessor<Integer> SKIN_INDEX = SynchedEntityData.defineId(Villager.class, EntityDataSerializers.INT);

    @Unique
    private final int MAX_SKINS = 5;

    @Unique
    private boolean maxonaddon$combatGoalAdded = false;

    @Unique
    private int maxon$transformTimer = 0; // Состояние таймера

    public VillagerEntityMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    // --- РЕАЛИЗАЦИЯ ИНТЕРФЕЙСОВ ---
    @Override
    public int modid$getSkinIndex() {
        return this.entityData.get(SKIN_INDEX);
    }

    @Override
    public int maxon$getTransformTimer() {
        return this.maxon$transformTimer;
    }

    @Override
    public void maxon$setTransformTimer(int timer) {
        this.maxon$transformTimer = timer;
    }

    // --- ИМЯ В МЕНЮ ТОРГОВЛИ/НАД ГОЛОВОЙ ---
    // Villager.class сам по себе getDisplayName() не переопределяет (наследует от Entity),
    // поэтому в исходном classfile инжектить некуда — добавляем метод напрямую как оверрайд.
    // startTrading() берёт заголовок окна именно отсюда: openTradingScreen(player, getDisplayName(), level).
    @Override
    public Component getDisplayName() {
        Villager villager = (Villager) (Object) this;
        if (villager.getTags().contains("smuggler") && !villager.getTags().contains("smuggler_caught")) {
            return Component.translatable("entity.maxonaddon.smuggler");
        }
        if (villager.getTags().contains("police_hunter")) {
            return Component.translatable("entity.maxonaddon.police_hunter");
        }
        return super.getDisplayName();
    }

    // --- NBT СОХРАНЕНИЕ И ЗАГРУЗКА (Объединено) ---
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void onAddAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        compound.putInt("CustomPlayerSkin", this.entityData.get(SKIN_INDEX));
        compound.putInt("maxon$TransformTimer", this.maxon$transformTimer);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("CustomPlayerSkin")) {
            this.entityData.set(SKIN_INDEX, compound.getInt("CustomPlayerSkin"));
        }
        if (compound.contains("maxon$TransformTimer")) {
            this.maxon$transformTimer = compound.getInt("maxon$TransformTimer");
        }
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    protected void onDefineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(SKIN_INDEX, 0);
    }

    // --- ЛОГИКА ТАЙМЕРА ОСЛАБЛЕНИЯ ---
    @Inject(method = "tick", at = @At("TAIL"))
    private void maxon$weakenedVillagerTick(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;

        if (!villager.level().isClientSide() && villager.getTags().contains("weakened_shifter_vessel")) {

            // Если таймер равен 0, значит житель только что стал ослабленным.
            // Задаем ему начальное время (например, 3600 тиков = 3 минуты)
            if (this.maxon$transformTimer <= 0 && !villager.getTags().contains("timer_initialized")) {
                this.maxon$transformTimer = 3600;
                villager.addTag("timer_initialized"); // Флаг, чтобы больше не сбрасывать таймер
            }

            if (this.maxon$transformTimer > 0) {
                this.maxon$transformTimer--;

                // Эффекты обновляем раз в секунду (каждые 20 тиков), чтобы не спамить в сеть
                if (this.maxon$transformTimer % 20 == 0) {
                    villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
                    villager.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2, false, false));
                }
            } else {
                // Время вышло - трансформация
                villager.removeTag("weakened_shifter_vessel");
                villager.removeTag("timer_initialized");
                villager.addTag("shifter_boss_vessel");

                villager.hurt(villager.damageSources().generic(), 0.1f);
            }
        }
    }

    // --- AI И СПАВН (Оставлено без изменений) ---
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    protected void stopCowardice(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;

        if (!maxonaddon$combatGoalAdded) {
            // Стрелок должен встать РАНЬШЕ рукопашной FighterRetaliateGoal и с тем же
            // приоритетом: при равном приоритете GoalSelector не вытесняет уже работающую
            // задачу, так что удержит флаг движения та, что запустилась первой. Иначе
            // полицейский с винтовкой всё равно бежал бы махать кулаками — меча у него
            // теперь нет. Сама задача отсеет всех, кроме police_hunter с винтовкой.
            this.goalSelector.addGoal(0, new com.maxonaddon.ai.PoliceRifleAttackGoal(villager));
            this.goalSelector.addGoal(0, new FighterRetaliateGoal(villager));
            this.goalSelector.addGoal(0, new PoliceDefendVillagersGoal(villager));
            this.goalSelector.addGoal(0, new com.maxonaddon.ai.WallReputationGoal(villager));
            this.goalSelector.addGoal(1, new com.maxonaddon.ai.CadetPatrolGoal(villager));
            this.goalSelector.addGoal(1, new ScoutOdmCombatGoal(villager));
            this.goalSelector.addGoal(1, new PoliceHuntSmugglerGoal(villager));
            this.goalSelector.addGoal(2, new SmugglerFleePoliceGoal(villager));
            this.goalSelector.addGoal(5, new TradingAwareStrollGoal(villager, 0.6));
            this.goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(villager, net.minecraft.world.entity.player.Player.class, 8.0F));
            this.goalSelector.addGoal(7, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(villager));
            maxonaddon$combatGoalAdded = true;
        }

        boolean isScout = villager.getTags().contains("scout_legion");
        boolean isBraveGarrison = villager.getTags().contains("brave_garrison");
        boolean isPolice = villager.getTags().contains("police_hunter");
        boolean isSmuggler = villager.getTags().contains("smuggler") && !villager.getTags().contains("smuggler_caught");

        // === КАДЕТ НА ВЫШКЕ НЕ ПОКИДАЕТ ПОСТ ===
        // Житель ходит не задачами, а брейном по расписанию: он ушёл бы с балкона спать,
        // бездельничать и панковать при первом же титане. Стереть цель ходьбы дешевле, чем
        // перекраивать ему весь распорядок, — а водит кадета уже наша CadetPatrolGoal.
        if (villager.getTags().contains(com.maxonaddon.OutpostCadetHandler.CADET_TAG)) {
            var brain = villager.getBrain();
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.IS_PANICKING);
            brain.eraseMemory(MemoryModuleType.HURT_BY);
            brain.eraseMemory(MemoryModuleType.HOME);
            brain.eraseMemory(MemoryModuleType.MEETING_POINT);
        }

        // Старые миры: полицейские, назначенные до перевооружения, так и остались с клинком.
        // PoliceGearHelper для них уже не вызовется никогда (CityFactionGuard выбирает только
        // жителей БЕЗ тега police_hunter), а без винтовки боевая задача стрелка не стартует —
        // отсюда и «стоят, терпят удары, живут жизнью жителя». Довооружаем на месте.
        // Раз в секунду, а не каждый тик: две проверки слотов на каждого полицейского — уже
        // лишняя работа при большом городе.
        if (isPolice && villager.tickCount % 20 == 0) {
            ItemStack mainHand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
            ItemStack offHand = villager.getItemBySlot(EquipmentSlot.OFFHAND);
            boolean armed = mainHand.is(com.maxonaddon.MaxonDaotAddon.MARLEYAN_RIFLE_ITEM)
                    || offHand.is(com.maxonaddon.MaxonDaotAddon.MARLEYAN_RIFLE_ITEM);
            if (!armed) {
                ItemStack rifle = new ItemStack(com.maxonaddon.MaxonDaotAddon.MARLEYAN_RIFLE_ITEM);
                com.maxonaddon.item.MarleyanRifleItem.setLoadedRounds(
                        rifle, com.maxonaddon.item.MarleyanRifleItem.MAX_LOADED_ROUNDS);
                // Клинок из правой руки убираем: полиция перешла на винтовку целиком.
                villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                villager.setItemSlot(EquipmentSlot.OFFHAND, rifle);
                villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
                villager.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
            }
        }

        if (isScout || isBraveGarrison) {
            villager.fallDistance = 0.0f;
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING)) {
                villager.getBrain().eraseMemory(MemoryModuleType.IS_PANICKING);
            }
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)) {
                villager.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
            }
        }

        // === НОЧНОЕ ДЕЖУРСТВО: гарнизон/скауты/полиция каждую ночь заново решают,
        // остаться на патруле или "проспать" эту ночь как обычные жители ===
        if (isScout || isBraveGarrison || isPolice) {
            NightDutyHelper.rollIfNewNight(villager, NightDutyHelper.dutyChance());
        }

        // === КОНТРАБАНДИСТ НИКОГДА НЕ СПИТ ===
        // Это самый обычный Villager с ванильным Brain, и без этого он ночью честно идёт
        // к заявленной кровати и засыпает (расписание REST у него никак не отличается от
        // обычного жителя). А ванильный mobInteract() прямо отказывает в торговле, если
        // isSleeping() == true — поэтому контрабандист "не хотел торговать" именно ночью,
        // когда нам это как раз нужно. Полицию это не касается — вне дежурства ей можно спать.
        if (isSmuggler) {
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
                villager.getBrain().eraseMemory(MemoryModuleType.HOME);
            }
            if (villager.isSleeping()) {
                villager.stopSleeping();
            }
        }

        // === МАСКИРОВКА КОНТРАБАНДИСТА: капюшон + торговля ночью, обычный житель днём ===
        // Торговлю намеренно включаем/выключаем вместе с капюшоном: если днём предложения
        // остаются, игрок открывает чёрный рынок у "обычного жителя" средь бела дня, что
        // ломает маскировку. С пустым списком предложений ванильный NITWIT сам мотает
        // головой — "не знаю никаких торгов", как обычный житель.
        // !isTrading() — не трогаем список предложений (это тот же самый ArrayList, что уже
        // отдан открытому у игрока меню) пока сессия торговли активна, на всякий случай.
        if (isSmuggler && !villager.isTrading() && villager.tickCount % 20 == 0) {
            boolean shouldWearHood = NightDutyHelper.isNight(villager.level());
            boolean wearingHood = SmugglerTradeHelper.isHoodOn(villager);
            if (shouldWearHood && !wearingHood) {
                SmugglerTradeHelper.putHoodOn(villager);
                SmugglerTradeHelper.applyOffers(villager);
            } else if (!shouldWearHood && wearingHood) {
                SmugglerTradeHelper.takeHoodOff(villager);
                SmugglerTradeHelper.clearOffers(villager);
            }
        }
    }

    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void onFinalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnGroupData, CallbackInfoReturnable<SpawnGroupData> cir) {
        if (!this.level().isClientSide()) {
            this.entityData.set(SKIN_INDEX, this.random.nextInt(MAX_SKINS));

            Villager v = (Villager) (Object) this;

            if (this.level().getLevelData().getGameRules().getBoolean(MaxonConfig.DO_SOLDIER_SPAWN)) {
                if (this.random.nextFloat() < 0.40f) {
                    v.addTag("garrison_soldier");

                    boolean isBrave = this.random.nextFloat() < (MaxonConfig.get().braveGarrisonChance / 100.0f);
                    if (isBrave) {
                        v.addTag("brave_garrison");
                    }

                    Item uniform = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "garrison_uniform"));
                    Item gear = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "odm_gear"));
                    Item boots = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "odm_boots"));
                    Item blade = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "blade"));

                    ItemStack stackGear = new ItemStack(gear != Items.AIR ? gear : Items.LEATHER_LEGGINGS);
                    if (gear instanceof daot.ODMGearItem) {
                        daot.ODMGearItem.setGas(stackGear, 9999);
                    }

                    ItemStack stackBladeMain = new ItemStack(blade != Items.AIR ? blade : Items.IRON_SWORD);
                    ItemStack stackBladeOff = new ItemStack(blade != Items.AIR ? blade : Items.IRON_SWORD);

                    if (blade instanceof daot.BladeItem) {
                        if (isBrave) {
                            daot.BladeItem.setBladeState(stackBladeMain, daot.BladeItem.BladeState.FRESH);
                            daot.BladeItem.setBladeState(stackBladeOff, daot.BladeItem.BladeState.FRESH);
                        } else {
                            daot.BladeItem.setBladeState(stackBladeMain, BladeItem.BladeState.EMPTY);
                            daot.BladeItem.setBladeState(stackBladeOff, BladeItem.BladeState.EMPTY);
                        }
                    }

                    v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(uniform != Items.AIR ? uniform : Items.LEATHER_CHESTPLATE));
                    v.setItemSlot(EquipmentSlot.LEGS, stackGear);
                    v.setItemSlot(EquipmentSlot.FEET, new ItemStack(boots != Items.AIR ? boots : Items.LEATHER_BOOTS));
                    v.setItemSlot(EquipmentSlot.MAINHAND, stackBladeMain);
                    v.setItemSlot(EquipmentSlot.OFFHAND, stackBladeOff);

                    v.setDropChance(EquipmentSlot.CHEST, 0.0f);
                    v.setDropChance(EquipmentSlot.LEGS, 0.0f);
                    v.setDropChance(EquipmentSlot.FEET, 0.0f);
                    v.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
                    v.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
                } else {
                    // Полиция и контрабандисты — только в пределах реального города, т.е. в радиусе
                    // 200 блоков от одного из 12 округов (см. DistrictHelper) — на (0,0) города нет.
                    boolean inCity = DistrictHelper.isInAnyDistrict(v.getX(), v.getZ());
                    if (inCity && this.random.nextFloat() < (MaxonConfig.get().policeSpawnChance / 100.0f)) {
                        PoliceGearHelper.equipAsPolice(v);
                    } else if (inCity && this.random.nextFloat() < (MaxonConfig.get().smugglerSpawnChance / 100.0f)) {
                        SmugglerTradeHelper.markAsSmuggler(v);
                    }
                }
            }
        }
    }
}