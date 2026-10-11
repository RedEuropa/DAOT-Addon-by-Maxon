package com.maxonaddon.client.mixin;

import com.maxonaddon.network.BackDashPayload;
import com.maxonaddon.network.DashPayload;
import daot.DannysAot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting; // Не забудь добавить этот импорт
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    private int jumpCooldown = 0;
    private int jumpCount = 0;
    private boolean wasJumpPressed = false;

    // === ТАЙМЕРЫ ДЛЯ РЫВКА НАЗАД ===
    private int backCooldown = 0;
    private int backCount = 0;
    private boolean wasBackPressed = false;

    // === ТАЙМЕР ПЕРЕЗАРЯДКИ РЫВКА ===
    private int dashCooldown = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;

        // Обновление таймеров пробела
        if (jumpCooldown > 0) {
            jumpCooldown--;
        } else if (jumpCount > 0) {
            jumpCount = 0;
        }

        // Обновление таймеров кнопки S (Назад)
        if (backCooldown > 0) {
            backCooldown--;
        } else if (backCount > 0) {
            backCount = 0;
        }

        if (dashCooldown > 0) {
            dashCooldown--;
        }

        // === ПРОВЕРКА ПРОБЕЛА (Рывок вперед) ===
        boolean isJumpPressed = Minecraft.getInstance().options.keyJump.isDown();
        boolean justPressed = isJumpPressed && !wasJumpPressed;
        this.wasJumpPressed = isJumpPressed;

        // === ПРОВЕРКА КНОПКИ "S" (Рывок назад) ===
        boolean isBackPressed = Minecraft.getInstance().options.keyDown.isDown();
        boolean justBackPressed = isBackPressed && !wasBackPressed;
        this.wasBackPressed = isBackPressed;

        // === ГЛАВНАЯ ПРОВЕРКА ===
        // Верхом рывок тоже доступен — но только пока держит трос: это спрыг с седла на
        // тросах, а не способ бесплатно катапультироваться с лошади посреди поля.
        // Транспорт читаем прямо из поля (EntityVehicleAccessor): ванильный getVehicle() для
        // вызовов из daot намеренно врёт (см. EntityVehicleMixin), а здесь нужна правда.
        boolean onHorse = ((com.maxonaddon.mixin.EntityVehicleAccessor) player).maxon$getVehicleField()
                instanceof net.minecraft.world.entity.animal.horse.AbstractHorse;
        boolean canDash = onHorse
                // В седле нужен свой навык — и живой зацеп: это спрыг на тросах, а не
                // бесплатная катапульта посреди поля. С выключенным своим УПМ спрыга нет
                // вовсе: без нашей физики верхом и зацепиться-то нечем.
                ? (com.maxonaddon.MaxonConfig.get().enableOdmPhysics
                        && com.maxonaddon.client.OdmClientState.hookedRecently()
                        && ((com.maxonaddon.util.IPlayerStats) player).hasRiderDismount())
                : (!player.onGround() && !player.isPassenger());

        if (canDash && maxonaddon$hasOdmTriggersClient(player)) {
            ItemStack legItem = player.getItemBySlot(EquipmentSlot.LEGS);

            if (DannysAot.isODMGear(legItem.getItem())) {

                // ЛОГИКА РЫВКА ВПЕРЕД
                if (justPressed) {
                    jumpCount++;
                    if (jumpCount == 2) {
                        handleDash(player, true, onHorse);
                        jumpCount = 0;
                        jumpCooldown = 20;
                    } else {
                        jumpCooldown = 12;
                    }
                }

                // ЛОГИКА РЫВКА НАЗАД
                if (justBackPressed) {
                    backCount++;
                    if (backCount == 2) {
                        handleDash(player, false, onHorse);
                        backCount = 0;
                        backCooldown = 20;
                    } else {
                        backCooldown = 12;
                    }
                }
            }
        }
    }

    // Вспомогательный метод
    private void handleDash(LocalPlayer player, boolean isForward, boolean onHorse) {
        if (dashCooldown <= 0) {
            if (isForward) {
                ClientPlayNetworking.send(new DashPayload());
            } else {
                ClientPlayNetworking.send(new BackDashPayload());
            }

            if (onHorse) {
                // Слезаем сразу и локально, не дожидаясь ответа сервера: иначе игрок ещё
                // несколько тиков «сидит» в седле и рывок выглядит рваным. Сам импульс
                // придёт с сервера — там спешивание происходит перед ним (см. DashPayload).
                player.stopRiding();
                player.setDeltaMovement(player.getDeltaMovement().add(0, 0.4, 0));
            }

            dashCooldown = 100; // КД 5 секунд
        } else {
            float timeLeft = dashCooldown / 20.0F;
            // Оставляем только само число с десятыми долями для передачи в транслейт
            String formattedTime = String.format(Locale.US, "%.1f", timeLeft);

            // Используем транслейт с передачей времени и накладываем желтый цвет
            player.displayClientMessage(
                    Component.translatable("message.maxonaddon.odm_cooldown", formattedTime)
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );
        }
    }

    // === ВСПОМОГАТЕЛЬНЫЙ КЛИЕНТСКИЙ МЕТОД ДЛЯ ПРОВЕРКИ РУКОЯТОК ===
    @Unique
    private boolean maxonaddon$hasOdmTriggersClient(LocalPlayer player) {
        net.minecraft.world.item.Item mainItem = player.getMainHandItem().getItem();
        net.minecraft.world.item.Item offItem = player.getOffhandItem().getItem();

        return mainItem instanceof daot.BladeItem || offItem instanceof daot.BladeItem ||
                mainItem instanceof daot.APGGunItem || offItem instanceof daot.APGGunItem ||
                mainItem == com.maxonaddon.MaxonDaotAddon.THUNDER_GUN_ITEM ||
                offItem == com.maxonaddon.MaxonDaotAddon.THUNDER_GUN_ITEM;
    }
}