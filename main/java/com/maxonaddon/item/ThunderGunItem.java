package com.maxonaddon.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import daot.ThunderSpearEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.ChatFormatting;
import java.util.List;

public class ThunderGunItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public static final String LOADED_SPEARS_KEY = "LoadedSpears";
    public static final int MAX_LOADED_SPEARS = 2;


    public static java.util.function.Supplier<String> loadKeyNameSupplier = () -> "Unbound";

    public ThunderGunItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static int getLoadedSpears(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains(LOADED_SPEARS_KEY)) {
                return tag.getInt(LOADED_SPEARS_KEY);
            }
        }
        return 0;
    }

    public static void setLoadedSpears(ItemStack stack, int amount) {
        int clampedAmount = Math.max(0, Math.min(MAX_LOADED_SPEARS, amount));
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> tag.putInt(LOADED_SPEARS_KEY, clampedAmount)));
    }

    // ==== МАГИЧЕСКОЕ ИСПРАВЛЕНИЕ АНИМАЦИИ В ВЫЖИВАНИИ ====
    // Запрещает игре "переодевать" предмет (дергать рукой вниз) при изменении патронов
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return 0;
    }



    public static void shootSpear(Level level, Player player, ItemStack stack, int loadedSpears) {
        // САМАЯ СТРОГАЯ ПРОВЕРКА: Работаем ТОЛЬКО если это конкретно наш предмет!
        if (stack.getItem() != com.maxonaddon.MaxonDaotAddon.THUNDER_GUN_ITEM) {
            return;
        }

        if (!level.isClientSide) {
            ThunderSpearEntity spear = new ThunderSpearEntity(level, player);

            Vec3 look = player.getLookAngle();
            boolean isMainHand = player.getMainHandItem() == stack;
            // Раньше было ±0.35 (разлёт всего 0.7 блока) — при быстром ЛКМ+ПКМ оба копья
            // спавнились почти вплотную и на первом же тике засчитывали хитбокс друг друга
            // как цель (взрыв "врезание"). Разносим шире, чтобы хитбоксы не пересекались.
            double handOffset = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT == isMainHand) ? 0.6 : -0.6;
            Vec3 sideVec = new Vec3(-look.z, 0, look.x).normalize().scale(handOffset);

            spear.setPos(player.getX() + look.x * 1.0 + sideVec.x, player.getY() + player.getBbHeight() * 0.65 + look.y * 1.0, player.getZ() + look.z * 1.0 + sideVec.z);
            spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.8F, 1.0F);
            level.addFreshEntity(spear);

            if (!player.isCreative()) {
                setLoadedSpears(stack, loadedSpears - 1);
            }

            String animName = (loadedSpears == 2) ? "fire2" : "fire";
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && stack.getItem() instanceof GeoItem geoItem) {
                geoItem.triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "controller", animName);
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);

        // === ОТДАЧА (вертикальный подъём вдвое слабее прежнего, по просьбе) ===
        Vec3 look = player.getLookAngle();
        Vec3 recoil = look.normalize().scale(-1.5);

        double pushY = recoil.y * 0.5;
        if (player.onGround()) {
            pushY = Math.max(0.15, pushY + 0.15);
        }

        player.setDeltaMovement(player.getDeltaMovement().add(recoil.x, pushY, recoil.z));
        player.hurtMarked = true;
        player.hasImpulse = true;
    }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("Loaded Spears: " + getLoadedSpears(stack) + "/" + MAX_LOADED_SPEARS).withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(Component.literal("Press (" + loadKeyNameSupplier.get() + ") to load Thunder Gun").withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.literal("Right Click to shoot (Right Hand)").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Left Click to shoot (Left Hand)").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.STOP)
                .triggerableAnim("fire", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("fire"))
                .triggerableAnim("fire2", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("fire2"))
                .triggerableAnim("load", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("load"))
                .triggerableAnim("load2", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("load2"))
                .triggerableAnim("unload", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("unload"))
                .triggerableAnim("unload2", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("unload2"))
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}