package com.maxonaddon.mixin;

import com.maxonaddon.util.IChestOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Помечает контейнер (сундук и т.п.) как "поставлен игроком" в момент размещения — отличает его
 * от уже сгенерированных структурных сундуков (у тех этого флага никогда не будет, см.
 * ChestOwnershipMixin/IChestOwnership). Нужно для репутации: "жителя обнесли" считаем только
 * по НЕ поставленным игроком контейнерам.
 */
@Mixin(Block.class)
public abstract class ChestPlacementMixin {

    @Inject(method = "setPlacedBy", at = @At("TAIL"))
    private void maxon$markPlayerPlacedContainer(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (level.isClientSide() || !(placer instanceof Player)) return;

        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity containerBe) {
            ((IChestOwnership) containerBe).maxon$setPlayerPlaced(true);
        }
    }
}
