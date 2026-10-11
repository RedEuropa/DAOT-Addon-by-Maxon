package com.maxonaddon.mixin;

import com.maxonaddon.util.IChestOwnership;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Хранит "поставлен игроком?" (см. ChestPlacementMixin — выставляет флаг при размещении) и
 * "уже засветился репутацией?" — на BaseContainerBlockEntity, а не на самом
 * RandomizableContainerBlockEntity (сундук и т.п.), потому что именно BaseContainerBlockEntity
 * реально ДЕКЛАРИРУЕТ saveAdditional/loadAdditional (проверено байткодом — у
 * RandomizableContainerBlockEntity своих переопределений этих методов нет, они только
 * унаследованы, а Mixin инжектит строго в класс, где метод физически объявлен).
 * IChestOwnership при этом используется только там, где реально нужно (см. ChestPlacementMixin/
 * ReputationHandler — оба фильтруют именно по instanceof RandomizableContainerBlockEntity).
 */
@Mixin(BaseContainerBlockEntity.class)
public abstract class ChestOwnershipMixin implements IChestOwnership {

    @Unique private boolean maxonPlayerPlaced = false;
    @Unique private boolean maxonNoticedAlready = false;

    @Override
    public boolean maxon$isPlayerPlaced() {
        return this.maxonPlayerPlaced;
    }

    @Override
    public void maxon$setPlayerPlaced(boolean value) {
        this.maxonPlayerPlaced = value;
    }

    @Override
    public boolean maxon$isNoticedAlready() {
        return this.maxonNoticedAlready;
    }

    @Override
    public void maxon$setNoticedAlready(boolean value) {
        this.maxonNoticedAlready = value;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void maxon$saveOwnership(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putBoolean("MaxonPlayerPlaced", this.maxonPlayerPlaced);
        tag.putBoolean("MaxonNoticedAlready", this.maxonNoticedAlready);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void maxon$loadOwnership(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.maxonPlayerPlaced = tag.getBoolean("MaxonPlayerPlaced");
        this.maxonNoticedAlready = tag.getBoolean("MaxonNoticedAlready");
    }
}
