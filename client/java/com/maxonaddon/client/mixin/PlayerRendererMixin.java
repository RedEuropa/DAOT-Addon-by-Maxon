package com.maxonaddon.client.mixin;

import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.client.render.TraumaLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    // Говорим миксину "подтянуть" защищенный метод из оригинального класса
    @Shadow
    protected abstract boolean addLayer(RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void maxon$addTraumaBlinkLayer(EntityRendererProvider.Context context, boolean useSmallArms, CallbackInfo ci) {
        // Теперь мы можем вызывать addLayer прямо через this,
        // так как в рантайме этот код будет внутри PlayerRenderer
        this.addLayer(new TraumaLayer((PlayerRenderer) (Object) this));
    }

    // --- НОВЫЙ ИНЖЕКТ ДЛЯ БЛОКИРОВКИ РУК ПРИ ПЕРЕНОСКЕ КОРОБКИ ---
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void onGetArmPose(AbstractClientPlayer player, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        // Проверяем, держит ли игрок коробку в любой из рук
        if (player.getMainHandItem().getItem() == MaxonDaotAddon.ITEM_BOX_ITEM ||
                player.getOffhandItem().getItem() == MaxonDaotAddon.ITEM_BOX_ITEM) {

            // Фиксируем обе руки перед собой (как при удержании заряженного арбалета)
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
            return;
        }

        // Марлийская винтовка: без этого ваниль сама каждый кадр пересчитывает позу руки
        // (для неопознанного предмета — ArmPose.ITEM, обычная "поднятая" стойка) параллельно
        // с нашей анимацией из player_animations/marleyan_rifle_hand.animation.json — в
        // некоторых сценариях смены предмета в руке (например, переключение хотбаром) это
        // может пересилить наш слой обратно на дефолтную стойку. Фиксируем позу статично —
        // как у item_box.
        if (player.getMainHandItem().getItem() instanceof com.maxonaddon.item.MarleyanRifleItem) {
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
        }
    }
}