package com.maxonaddon.block.entity;

import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.menu.SwordBoxMenu;
import daot.BladeComponentItem;
import daot.BladeItem;
import daot.GasCanisterItem;
import daot.ThunderSpearItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import com.maxonaddon.block.SwordBoxBlock;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;


public class SwordBoxBlockEntity extends RandomizableContainerBlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private NonNullList<ItemStack> items = NonNullList.withSize(11, ItemStack.EMPTY); // <-- 11 слотов

    // === КВЕСТ "ДОСТАВИТЬ ПРИПАСЫ" (см. com.maxonaddon.DeliveryQuestHandler) ===
    // Персистится вместе с остальным NBT ящика (save/loadAdditional ниже) — а значит переживает
    // и постановку блока, и разбор обратно в предмет (BlockEntity#saveToItem копирует весь NBT
    // в компонент BLOCK_ENTITY_DATA предмета), поэтому "пломба" не слетает при перекладывании
    // ящика в инвентаре.
    private boolean maxonDeliveryBox = false;
    private String maxonDeliveryOwnerUuid = "";
    private boolean maxonSealBroken = false;

    public boolean isMaxonDeliveryBox() { return this.maxonDeliveryBox; }
    public void setMaxonDeliveryBox(boolean value) { this.maxonDeliveryBox = value; }
    public String getMaxonDeliveryOwnerUuid() { return this.maxonDeliveryOwnerUuid; }
    public void setMaxonDeliveryOwnerUuid(String uuid) { this.maxonDeliveryOwnerUuid = uuid == null ? "" : uuid; }
    public boolean isMaxonSealBroken() { return this.maxonSealBroken; }
    public void setMaxonSealBroken(boolean value) { this.maxonSealBroken = value; }

    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlayAndHold("up_door.model.new");
    private static final RawAnimation CLOSE_ANIM = RawAnimation.begin().thenPlayAndHold("down_door.model.new2");
    public SwordBoxBlockEntity(BlockPos pos, BlockState state) {
        super(MaxonDaotAddon.SWORD_BOX_ENTITY, pos, state);
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return this.items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    public int getContainerSize() { return 11; } // <-- 11 слотов

    @Override
    protected Component getDefaultName() { return Component.translatable("container.maxonaddon.sword_box"); }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SwordBoxMenu(containerId, inventory, this);
    }

    // Жесткие правила для 11 слотов
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot <= 3) return stack.getItem() instanceof BladeItem || stack.getItem() instanceof BladeComponentItem;
        if (slot == 4 || slot == 5) return stack.getItem() instanceof GasCanisterItem;
        if (slot >= 6 && slot <= 10) return stack.getItem() instanceof ThunderSpearItem;
        return false; // Ничего другого положить нельзя
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return this.saveWithoutMetadata(registries); }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);

        this.maxonDeliveryBox = tag.getBoolean("MaxonDeliveryBox");
        this.maxonDeliveryOwnerUuid = tag.getString("MaxonDeliveryOwnerUuid");
        this.maxonSealBroken = tag.getBoolean("MaxonSealBroken");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);

        tag.putBoolean("MaxonDeliveryBox", this.maxonDeliveryBox);
        tag.putString("MaxonDeliveryOwnerUuid", this.maxonDeliveryOwnerUuid);
        tag.putBoolean("MaxonSealBroken", this.maxonSealBroken);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            if (this.level != null) {
                BlockState blockState = this.getBlockState();

                // Проверяем, есть ли у блока свойство OPEN
                if (blockState.hasProperty(SwordBoxBlock.OPEN)) {
                    boolean isOpen = blockState.getValue(SwordBoxBlock.OPEN);

                    // Запускаем нужную анимацию
                    if (isOpen) {
                        state.setAnimation(OPEN_ANIM);
                    } else {
                        state.setAnimation(CLOSE_ANIM);
                    }
                    return PlayState.CONTINUE;
                }
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }
}