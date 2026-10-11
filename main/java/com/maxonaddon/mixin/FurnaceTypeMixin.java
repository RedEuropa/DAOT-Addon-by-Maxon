package com.maxonaddon.mixin;

import com.maxonaddon.MaxonDaotAddon;
import daot.DannysAot;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityType.class)
public class FurnaceTypeMixin {
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void allowOurCisterna(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == DannysAot.ICEBURST_FURNACE_BLOCK_ENTITY) {
            if (state.is(MaxonDaotAddon.GAS_CISTERNA)) cir.setReturnValue(true);
        }
    }
}