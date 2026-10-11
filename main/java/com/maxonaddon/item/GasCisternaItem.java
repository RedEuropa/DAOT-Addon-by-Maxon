package com.maxonaddon.item;

import com.maxonaddon.block.entity.GasCisternaBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class GasCisternaItem extends BlockItem implements software.bernie.geckolib.animatable.GeoItem {
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    public GasCisternaItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        // Контроллеры анимации (если они нужны предмету)
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * Запас газа, сохранённый в предмете — тот же NBT-блок, что loot-таблица
     * (minecraft:copy_components, source: block_entity) копирует из блок-сущности при
     * разрушении цистерны (см. GasCisternaBlockEntity.saveAdditional — ключ "StoredGas").
     * У свежесделанной цистерны (никогда не стоявшей в мире) этого компонента ещё нет — 0.
     */
    public static int getStoredGas(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return 0;
        CompoundTag tag = data.copyTag();
        return tag.contains("StoredGas") ? tag.getInt("StoredGas") : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        int gas = getStoredGas(stack);
        ChatFormatting color = gas > GasCisternaBlockEntity.TANK_CAPACITY / 2 ? ChatFormatting.AQUA
                : gas > GasCisternaBlockEntity.TANK_CAPACITY / 5 ? ChatFormatting.YELLOW
                : ChatFormatting.RED;

        tooltip.add(Component.literal("Газ: " + gas + " / " + GasCisternaBlockEntity.TANK_CAPACITY).withStyle(color));
    }
}
