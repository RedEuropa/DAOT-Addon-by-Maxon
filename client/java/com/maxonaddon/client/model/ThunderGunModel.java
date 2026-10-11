package com.maxonaddon.client.model;

import com.maxonaddon.item.ThunderGunItem;
import daot.ThunderSpearItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.animation.AnimationState;

public class ThunderGunModel extends GeoModel<ThunderGunItem> {

    @Override
    public ResourceLocation getModelResource(ThunderGunItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "geo/thunder_gun.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ThunderGunItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/item/thunder_gun.png");
    }



    @Override
    public ResourceLocation getAnimationResource(ThunderGunItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "animations/thunder_gun.animation.json");
    }


    @Override
    public void setCustomAnimations(ThunderGunItem animatable, long instanceId, AnimationState<ThunderGunItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // 1. Получаем количество ЗАКАРЯЖЕННЫХ копий из текущего предмета (0, 1 или 2)
        int loadedSpears = ThunderGunItem.getLoadedSpears(animatable.getDefaultInstance()); // Обрати внимание, здесь лучше использовать реальный ItemStack, если он доступен в контексте, но для анимации часто приходится хитрить.

        // 2. Считаем количество ОБЫЧНЫХ копий в инвентаре
        int inventorySpears = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof ThunderSpearItem) {
                inventorySpears += player.getInventory().getItem(i).getCount();
            }
        }
        // Заряженные копья уже не в инвентаре (мы их потратили), так что они автоматически не считаются!

        // 3. Получаем кости из модели
        GeoBone spearTop = this.getAnimationProcessor().getBone("spear_top");
        GeoBone spearBottom = this.getAnimationProcessor().getBone("spear_bottom");
        GeoBone spearSide1 = this.getAnimationProcessor().getBone("spear_side_1");
        GeoBone spearSide2 = this.getAnimationProcessor().getBone("spear_side_2");
        GeoBone spearSide3 = this.getAnimationProcessor().getBone("spear_side_3");

        // 4. Логика скрытия/показа

        // --- ЗАРЯЖЕННЫЕ КОПЬЯ (В пушке) ---
        if (spearTop != null) spearTop.setHidden(loadedSpears < 1);
        if (spearBottom != null) spearBottom.setHidden(loadedSpears < 2);

        // --- КОПЬЯ В ИНВЕНТАРЕ (На руке) ---
        // Показываем до 3 копий на руке в зависимости от того, сколько их в инвентаре
        if (spearSide1 != null) spearSide1.setHidden(inventorySpears < 1);
        if (spearSide2 != null) spearSide2.setHidden(inventorySpears < 2);
        if (spearSide3 != null) spearSide3.setHidden(inventorySpears < 3);
    }

}