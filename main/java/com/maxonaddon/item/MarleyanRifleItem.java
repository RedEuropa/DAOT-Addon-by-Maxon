package com.maxonaddon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Марлийская винтовка. Сделана по той же схеме, что и ThunderGunItem: патроны (заряженные
 * выстрелы) хранятся прямо в NBT предмета, use()/useOn() — пустые заглушки (стрельба и
 * перезарядка идут не через ванильный "use", а через MarleyanRifleActionPayload — клиент сам
 * решает, когда стрелять/перезаряжать, по ЛКМ/кнопке R, см. MaxonDaotAddonClient), сама
 * стрельба и списание патрона — на сервере (см. MaxonDaotAddon.shootMarleyanRifle).
 *
 * Затворная механика: после выстрела на предмет вешается ванильный кулдаун (ItemCooldowns) —
 * это и не даёт стрелять чаще, чем успевает отыграть анимация "shoot"+"bolt_cycle"
 * (см. registerControllers), и заодно бесплатно даёт стандартную диагональную полоску
 * перезарядки на иконке предмета в хотбаре.
 */
public class MarleyanRifleItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final String LOADED_ROUNDS_KEY = "LoadedRounds";
    public static final int MAX_LOADED_ROUNDS = 8;

    /** ~1.2с "shoot" + ~1.5с "bolt_cycle" из marleyan_rifle.animation.json, округлено вверх. */
    public static final int FIRE_COOLDOWN_TICKS = 54;

    public MarleyanRifleItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static int getLoadedRounds(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains(LOADED_ROUNDS_KEY)) {
                return tag.getInt(LOADED_ROUNDS_KEY);
            }
        }
        return 0;
    }

    public static void setLoadedRounds(ItemStack stack, int amount) {
        int clamped = Math.max(0, Math.min(MAX_LOADED_ROUNDS, amount));
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> tag.putInt(LOADED_ROUNDS_KEY, clamped)));
    }

    // Не даём игре "переодевать" предмет (дёргать рукой вниз) при каждом изменении патронов —
    // тот же фикс, что и у ThunderGunItem.
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
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
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 0;
    }

    // === ДИАГОНАЛЬНАЯ ПОЛОСКА НА ИКОНКЕ ("отображать на actionbar") — вместо прочности
    // показывает запас патронов, тот же приём, что у арбалета/лука с зарядом. ===
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getLoadedRounds(stack) < MAX_LOADED_ROUNDS;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getLoadedRounds(stack) / MAX_LOADED_ROUNDS);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float fraction = (float) getLoadedRounds(stack) / MAX_LOADED_ROUNDS;
        return net.minecraft.util.Mth.hsvToRgb(fraction / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Патронов: " + getLoadedRounds(stack) + "/" + MAX_LOADED_ROUNDS).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("ЛКМ — выстрел").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("ПКМ (удержание) — прицел").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Перезарядка — клавиша из настроек (dannys-aot:apg_cartridge)").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop("idle")))
                // Выстрел и цикл затвора — одна цепочка: GeckoLib проигрывает "shoot", а следом
                // сразу "bolt_cycle", без отдельного запроса на сервере под каждую фазу.
                .triggerableAnim("fire", RawAnimation.begin().thenPlay("shoot").thenPlay("bolt_cycle"))
                // Выстрел из прицела — та же логика, но "aim_shoot" вместо обычного "shoot".
                .triggerableAnim("fire_aim", RawAnimation.begin().thenPlay("aim_shoot").thenPlay("bolt_cycle"))
                // thenPlayAndHold, а не thenPlay: поза прицеливания держится, пока игрок не
                // отпустит ПКМ (или не начнёт перезарядку/выстрел) — не одноразовая вспышка.
                .triggerableAnim("aim", RawAnimation.begin().thenPlayAndHold("aim"))
                // Явный возврат из зафиксированной позы прицела: thenPlayAndHold сам никогда
                // не заканчивается, поэтому "отпустить прицел" — это отдельный триггер, а не
                // самопроизвольный возврат к базовому idle-предикату контроллера.
                .triggerableAnim("unaim", RawAnimation.begin().thenLoop("idle"))
                .triggerableAnim("reload", RawAnimation.begin().thenPlay("reload"))
                .triggerableAnim("draw", RawAnimation.begin().thenPlay("draw"))
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
