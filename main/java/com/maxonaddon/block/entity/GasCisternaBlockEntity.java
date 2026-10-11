package com.maxonaddon.block.entity;

import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.block.GasCisternaBlock;
import com.maxonaddon.menu.GasCisternaMenu;
import daot.DannysAot;
import daot.GasCanisterItem;
import daot.IceburstFurnaceBlockEntity;
import daot.ModSounds;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;

public class GasCisternaBlockEntity extends IceburstFurnaceBlockEntity
        implements GeoBlockEntity, ExtendedScreenHandlerFactory<BlockPos> {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Внутренний бак цистерны — реальный запас газа, который лёд накапливает независимо
    // от того, стоит ли в слоте 2 баллон. Раньше "наличие газа" фактически подменялось
    // "наличием баллона" (газ жил только в NBT вставленного баллона), из-за чего забор
    // газа (shift+ПКМ) ошибочно завязывался на присутствие баллона. Теперь источник
    // истины — именно этот бак.
    public static final int TANK_CAPACITY = 5000;
    private int storedGas = 0;

    public GasCisternaBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    public int getStoredGas() {
        return this.storedGas;
    }

    public int takeGas(int amount) {
        int toTake = Math.min(amount, this.storedGas);
        this.storedGas -= toTake;
        if (toTake > 0) this.setChanged();
        return toTake;
    }

    // Название окна: без переопределения показывалось бы унаследованное от
    // IceburstFurnaceBlockEntity "block.dannys-aot.iceburst_furnace" — своё название блока
    // подходит куда больше для нашего собственного меню.
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    // Своё меню (2 слота: лёд, баллон) вместо унаследованного от IceburstFurnaceBlockEntity —
    // у того ещё 2 слота "топливо"/"выход" от рецепта обычной печки, которые наша логика
    // вообще не читает и которые визуально сбивали с толку (см. GasCisternaMenu).
    // ExtendedScreenHandlerFactory нужен, чтобы передать BlockPos клиенту при открытии —
    // GasCisternaScreen читает по нему актуальный storedGas напрямую с клиентской сущности.
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GasCisternaMenu(containerId, inventory, this, this.getBlockPos());
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.getBlockPos();
    }

    @Override
    public BlockEntityType<?> getType() {
        return MaxonDaotAddon.GAS_CISTERNA_ENTITY;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // === НАШИ НОВЫЕ АПГРЕЙДЫ ===

    // 1. Увеличиваем лимит инвентаря до 256
    @Override
    public int getMaxStackSize() {
        return 99;
    }

    // 2. Блокируем весь мусор (дерево, уголь, бамбук), разрешаем только нужное
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // В слот загрузки кроме льда кладём и баллон — оттуда газ сливается в бак
        // (в отличие от второго слота, где баллон, наоборот, заправляется).
        if (slot == 0) return stack.is(DannysAot.ICE_BURST_CLUSTER)
                || stack.getItem() instanceof GasCanisterItem;
        // Во второй слот кроме баллона кладём и само снаряжение — цистерна умеет качать
        // газ из него обратно в бак (см. serverTickCisterna).
        if (slot == 1) return stack.getItem() instanceof GasCanisterItem
                || com.maxonaddon.util.OdmGasHelper.isOdm(stack);
        return false;
    }
    // 1. Отправляем NBT данные блока клиенту при загрузке чанка
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.storedGas = tag.getInt("StoredGas");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("StoredGas", this.storedGas);
    }

    // === СОХРАНЕНИЕ ГАЗА В ПРЕДМЕТЕ ПРИ РАЗРУШЕНИИ БЛОКА ===
    //
    // loot-таблица (gas_cisterna.json) копирует "минус NBT" через minecraft:copy_components ->
    // Source.BLOCK_ENTITY -> blockEntity.collectComponents(). НО collectComponents() = поле
    // components (пустое у обычной блок-сущности) + то, что явно добавит
    // collectImplicitComponents() — а по умолчанию оно НИЧЕГО не кладёт в
    // DataComponents.BLOCK_ENTITY_DATA (тот выставляется отдельно, только через
    // BlockItem.setBlockEntityData()/saveToItem(), а не сюда). Поэтому просто
    // saveAdditional()+loot-функция сами по себе не работали: копировать было нечего.
    //
    // Явно кладём тот же NBT (saveCustomOnly — то, что строит saveAdditional выше, включая
    // "StoredGas") в BLOCK_ENTITY_DATA здесь — теперь loot-функции есть что копировать.
    // Восстановление на установке уже работает "из коробки": BlockItem.place() читает
    // BLOCK_ENTITY_DATA предмета и вызывает loadWithComponents() -> loadAdditional() на новой
    // блок-сущности — тот же метод, что мы уже переопределили выше.
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (this.level != null) {
            // ВАЖНО: DataComponents.BLOCK_ENTITY_DATA сериализуется кодеком, который требует
            // поле "id" (какой именно BlockEntityType это данные) — без него игра крашится не
            // сразу при разрушении блока, а при первой же попытке сохранить ItemStack с этим
            // компонентом (автосейв игрока, если предмет лежит в инвентаре): "Missing id for
            // entity in: {...}". saveCustomOnly() сам "id" не пишет (это отдельный шаг
            // saveMetadata/saveId, вызываемый только в saveCustomAndMetadata()). Повторяем
            // здесь ровно то же, что делает штатный BlockItem.setBlockEntityData(): чистим
            // остаточный "id", если после этого тег не пуст — дописываем актуальный через
            // BlockEntity.addEntityType() и только тогда кладём компонент.
            CompoundTag tag = this.saveCustomOnly(this.level.registryAccess());
            this.removeComponentsFromTag(tag);
            tag.remove("id");
            if (!tag.isEmpty()) {
                net.minecraft.world.level.block.entity.BlockEntity.addEntityType(tag, this.getType());
                builder.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            }
        }
    }

    // 2. Создаем пакет обновлений для передачи по сети
    @Nullable
    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // 3. Каждый раз, когда инвентарь меняется (положили/забрали предмет),
    // принудительно рассылаем пакет обновлений всем клиентам вокруг
    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            // Флаг 3 означает: обновить блок и отправить изменения клиенту
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    // 3. Ускоренная логика работы без угля
    public static void serverTickCisterna(Level level, BlockPos pos, BlockState state, GasCisternaBlockEntity entity) {
        boolean wasLit = entity.dataAccess.get(0) > 0;
        boolean changed = false;

        ItemStack primary = entity.getItem(0);
        ItemStack secondary = entity.getItem(1);

        // Этап 1: лёд плавится в ВНУТРЕННИЙ бак цистерны (storedGas). Баллон в слоте 2
        // для этого больше не нужен — раньше рецепт требовал баллон, из-за чего "газ есть"
        // фактически означало "баллон вставлен", а не "цистерна реально накопила газ".
        boolean canFillTank = !primary.isEmpty() && primary.is(DannysAot.ICE_BURST_CLUSTER)
                && entity.storedGas < TANK_CAPACITY;

        if (canFillTank) {
            // Имитируем, что печка "горит", чтобы шли партиклы
            entity.dataAccess.set(0, 1);

            // УСКОРЯЕМ В 2 РАЗА (вместо +1 делаем +2)
            int progress = entity.dataAccess.get(2) + 2;
            entity.dataAccess.set(2, progress);

            // 80 - это длина полоски UI в оригинальном моде, оставляем её
            if (progress >= 80) {
                entity.dataAccess.set(2, 0); // Сбрасываем прогресс

                entity.storedGas = Math.min(entity.storedGas + 25, TANK_CAPACITY);
                primary.shrink(1); // Тратим 1 кластер льда

                level.playSound(null, pos, ModSounds.STEAM_POOF, SoundSource.BLOCKS, 1.0F, 2.0F);
                changed = true;
            }
        } else {
            // Если рецепта нет - всё выключаем
            entity.dataAccess.set(2, 0);
            entity.dataAccess.set(0, 0);
        }

        // Этап 1б: баллон, положенный в слот ЗАГРУЗКИ, работает наоборот — не заправляется,
        // а сливает свой газ в бак. Так остатки из нескольких полупустых баллонов собираются
        // в один, а сам баллон остаётся в слоте пустым (не расходуется, как лёд).
        // Плавку это не имитирует: прогресс-бар и режим "горит" здесь не трогаем, они
        // относятся только ко льду.
        if (!primary.isEmpty() && primary.getItem() instanceof GasCanisterItem) {
            int loadedCanisterGas = GasCanisterItem.getGas(primary);
            int freeSpace = TANK_CAPACITY - entity.storedGas;
            int transfer = Math.min(Math.min(5, loadedCanisterGas), freeSpace);
            if (transfer > 0) {
                GasCanisterItem.setGas(primary, loadedCanisterGas - transfer);
                entity.storedGas += transfer;
                changed = true;
            }
        }

        // Этап 2: если в слоте стоит баллон и в баке есть газ — потихоньку переливаем его
        // в баллон (прежнее поведение "баллон в цистерне заряжается сам"), но источником
        // теперь служит storedGas, а не генерация газа "из ниоткуда".
        if (!secondary.isEmpty() && secondary.getItem() instanceof GasCanisterItem && entity.storedGas > 0) {
            int canisterGas = GasCanisterItem.getGas(secondary);
            if (canisterGas < 500) {
                int transfer = Math.min(Math.min(5, entity.storedGas), 500 - canisterGas);
                if (transfer > 0) {
                    GasCanisterItem.setGas(secondary, canisterGas + transfer);
                    entity.storedGas -= transfer;
                    entity.dataAccess.set(1, 1);
                    changed = true;
                }
            } else {
                entity.dataAccess.set(1, 0);
            }
        } else if (com.maxonaddon.util.OdmGasHelper.isOdm(secondary)) {
            // Обратное направление: УПМ в слоте не заправляется, а СДАЁТ газ в бак цистерны.
            // Полезно для трофейного снаряжения — слить остатки и залить их в свой комплект
            // или в баллон. Скорость та же, что у заправки баллона (5 единиц за тик работы).
            int odmGas = com.maxonaddon.util.OdmGasHelper.getGas(secondary);
            int freeSpace = TANK_CAPACITY - entity.storedGas;
            int transfer = Math.min(Math.min(5, odmGas), freeSpace);
            if (transfer > 0) {
                com.maxonaddon.util.OdmGasHelper.setGas(secondary, odmGas - transfer);
                entity.storedGas += transfer;
                entity.dataAccess.set(1, 1);
                changed = true;
            } else {
                entity.dataAccess.set(1, 0);
            }
        } else {
            entity.dataAccess.set(1, 0);
        }

        // Обновляем состояние блока (вкл/выкл) для анимаций и партиклов
        boolean isLit = entity.dataAccess.get(0) > 0;
        if (wasLit != isLit) {
            changed = true;
            level.setBlock(pos, state.setValue(GasCisternaBlock.LIT, isLit), 3);
        }

        if (changed) {
            entity.setChanged();
        }
    }
}