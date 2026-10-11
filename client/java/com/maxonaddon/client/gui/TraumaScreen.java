package com.maxonaddon.client.gui;

import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.TraumaType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class TraumaScreen extends Screen {

    public TraumaScreen() {
        super(Component.translatable("gui.maxonaddon.trauma.title"));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.trauma.header"), centerX, centerY - 100, 0xFFFFFF);

        if (this.minecraft != null && this.minecraft.player != null) {
            net.minecraft.world.entity.player.Player player = this.minecraft.player;
            IPlayerStats stats = (IPlayerStats) player;

            int boxLeft = centerX - 50;
            int boxTop = centerY - 50;
            int boxRight = centerX + 50;
            int boxBottom = centerY + 70;
            int scale = 55;
            float yOffset = 0.0f;

            net.minecraft.world.item.ItemStack headItem = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            net.minecraft.world.item.ItemStack chestItem = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            net.minecraft.world.item.ItemStack legsItem = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
            net.minecraft.world.item.ItemStack feetItem = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
            net.minecraft.world.item.ItemStack mainHand = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            net.minecraft.world.item.ItemStack offHand = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);

            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, boxLeft, boxTop, boxRight, boxBottom, scale, yOffset, (float)mouseX, (float) mouseY, player
            );

            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, headItem);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chestItem);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, legsItem);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, feetItem);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, mainHand);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, offHand);

            int headX = centerX - 100, headY = centerY - 70;
            int bodyX = centerX + 100, bodyY = centerY - 70;
            int armsX = centerX - 100, armsY = centerY + 20;
            int legsX = centerX + 100, legsY = centerY + 20;

            drawStatus(graphics, TraumaType.HEAD, Component.translatable("gui.maxonaddon.trauma.head"), headX, headY, stats);
            drawStatus(graphics, TraumaType.BODY, Component.translatable("gui.maxonaddon.trauma.body"), bodyX, bodyY, stats);
            drawStatus(graphics, TraumaType.ARMS, Component.translatable("gui.maxonaddon.trauma.arms"), armsX, armsY, stats);
            drawStatus(graphics, TraumaType.LEGS, Component.translatable("gui.maxonaddon.trauma.legs"), legsX, legsY, stats);

            checkAndDrawTooltip(graphics, mouseX, mouseY, headX, headY, TraumaType.HEAD, Component.translatable("gui.maxonaddon.trauma.head.desc"), stats);
            checkAndDrawTooltip(graphics, mouseX, mouseY, bodyX, bodyY, TraumaType.BODY, Component.translatable("gui.maxonaddon.trauma.body.desc"), stats);
            checkAndDrawTooltip(graphics, mouseX, mouseY, armsX, armsY, TraumaType.ARMS, Component.translatable("gui.maxonaddon.trauma.arms.desc"), stats);
            checkAndDrawTooltip(graphics, mouseX, mouseY, legsX, legsY, TraumaType.LEGS, Component.translatable("gui.maxonaddon.trauma.legs.desc"), stats);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void checkAndDrawTooltip(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, TraumaType type, Component description, IPlayerStats stats) {
        if (mouseX >= x - 50 && mouseX <= x + 50 && mouseY >= y && mouseY <= y + 25) {
            java.util.List<Component> tooltip = new java.util.ArrayList<>();
            tooltip.add(description);

            int timer = stats != null ? stats.getTraumaTimer(type) : 0;
            if (timer > 0) {
                tooltip.add(Component.translatable("gui.maxonaddon.trauma.status.damaged", timer / 20));
            } else {
                tooltip.add(Component.translatable("gui.maxonaddon.trauma.status.healthy"));
            }

            graphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private void drawStatus(GuiGraphics graphics, TraumaType type, Component name, int x, int y, IPlayerStats stats) {
        int timer = stats != null ? stats.getTraumaTimer(type) : 0;

        graphics.drawCenteredString(this.font, name.copy().withStyle(net.minecraft.ChatFormatting.RESET).withStyle(net.minecraft.ChatFormatting.BOLD), x, y, 0xFFFFFF);

        if (timer > 0) {
            int seconds = timer / 20;
            graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.trauma.status.damaged", seconds), x, y + 12, 0xFFFFFF);
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.trauma.status.healthy"), x, y + 12, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}