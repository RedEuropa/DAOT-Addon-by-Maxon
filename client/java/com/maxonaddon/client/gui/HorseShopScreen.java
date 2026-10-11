package com.maxonaddon.client.gui;

import com.maxonaddon.network.HorseActionPayload;
import com.maxonaddon.util.HorseOffer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Лавка лошадей. Три предложения, между которыми переключаются стрелками; в центре модель
 * лошади, под ней — полоски статов.
 * <p>
 * Статы берутся не из сети напрямую, а выводятся из seed'а через HorseOffer.generate — тем же
 * кодом, которым сервер посчитает их при покупке. Поэтому показанное и купленное совпадают
 * всегда, а по сети едет всего одно число.
 */
public class HorseShopScreen extends Screen {

    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 6;

    private static final int COLOR_SPEED = 0xFFFFD24A;   // жёлтый
    private static final int COLOR_JUMP = 0xFF4A90FF;    // синий
    private static final int COLOR_HEALTH = 0xFFFF4A4A;  // красный
    private static final int COLOR_BAR_BG = 0xFF2B2B2B;

    // Прямоугольник витрины — по нему же ловим перетаскивание.
    private static final int VIEW_HALF_WIDTH = 40;
    private static final int VIEW_TOP_OFFSET = -70;
    private static final int VIEW_BOTTOM_OFFSET = 10;

    /** Насколько градусов поворачивается лошадь на один пиксель движения мыши. */
    private static final float DRAG_SENSITIVITY = 2.0F;

    private final Screen parent;
    private int index = 0;

    /** Демонстрационная лошадь — существует только в GUI, в мир не попадает. */
    private Horse preview;

    /** Текущий угол поворота витрины. Лошадь стоит смирно, пока её не крутят мышью. */
    private float previewYaw = 150.0F;
    private boolean dragging = false;

    public HorseShopScreen(Screen parent) {
        super(Component.translatable("gui.maxonaddon.horse.title"));
        this.parent = parent;
    }

    private HorseOffer offer() {
        return HorseOffer.generate(com.maxonaddon.client.HorseShopClient.seed, index);
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        rebuildPreview();

        // Переключение между лошадьми
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            index = (index + HorseOffer.COUNT - 1) % HorseOffer.COUNT;
            rebuildPreview();
        }).bounds(centerX - 110, centerY - 20, 20, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            index = (index + 1) % HorseOffer.COUNT;
            rebuildPreview();
        }).bounds(centerX + 90, centerY - 20, 20, 20).build());

        // Согласие — изумруд, отказ — барьер. Обе кнопки подписаны предметом, а не текстом,
        // поэтому это ItemButtonWidget, а не обычная Button.
        this.addRenderableWidget(new ItemButtonWidget(centerX - 60, centerY + 76, new ItemStack(Items.EMERALD),
                Component.translatable("gui.maxonaddon.horse.buy"), b -> {
            ClientPlayNetworking.send(new HorseActionPayload(HorseActionPayload.BUY, index));
            this.onClose();
        }));

        this.addRenderableWidget(new ItemButtonWidget(centerX + 40, centerY + 76, new ItemStack(Items.BARRIER),
                Component.translatable("gui.maxonaddon.horse.decline"), b -> this.onClose()));

        // Переброс статов
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.maxonaddon.horse.reroll", HorseOffer.REROLL_PRICE).withStyle(ChatFormatting.AQUA), b -> {
            ClientPlayNetworking.send(new HorseActionPayload(HorseActionPayload.REROLL, 0));
        }).bounds(centerX - 60, centerY + 100, 120, 20).build());
    }

    /** Пересобирает модель под текущее предложение — и статы, и масть. */
    private void rebuildPreview() {
        if (this.minecraft == null || this.minecraft.level == null) return;

        HorseOffer current = offer();
        Horse horse = EntityType.HORSE.create(this.minecraft.level);
        if (horse == null) return;

        horse.setVariant(Variant.values()[Math.floorMod(current.variantIndex(), HorseOffer.VARIANT_COUNT)]);

        var speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(current.speed());
        var jump = horse.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) jump.setBaseValue(current.jump());
        var health = horse.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(current.health());
        horse.setHealth((float) current.health());

        this.preview = horse;
    }

    private boolean isOverPreview(double mouseX, double mouseY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        return mouseX >= centerX - VIEW_HALF_WIDTH && mouseX <= centerX + VIEW_HALF_WIDTH
                && mouseY >= centerY + VIEW_TOP_OFFSET && mouseY <= centerY + VIEW_BOTTOM_OFFSET;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Зажали ЛКМ на самой лошади — дальше движения мыши крутят витрину.
        // Зона витрины с кнопками не пересекается, так что перехват им не мешает.
        if (button == 0 && isOverPreview(mouseX, mouseY)) {
            this.dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging && button == 0) {
            this.previewYaw += (float) dragX * DRAG_SENSITIVITY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто нарочно — Screen.render() сам вызывает renderBackground(), а повторный вызов
        // удваивал бы затемнение (та же история, что и в остальных наших экранах).
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xD9000000);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        HorseOffer current = offer();

        graphics.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                centerX, centerY - 100, 0xFFFFFF);

        // Какая из трёх лошадей сейчас на витрине
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.maxonaddon.horse.counter", index + 1, HorseOffer.COUNT).withStyle(ChatFormatting.GRAY),
                centerX, centerY - 84, 0xFFFFFF);

        renderPreview(graphics, centerX, centerY);

        int barX = centerX - BAR_WIDTH / 2;
        int barY = centerY + 18;

        drawStat(graphics, barX, barY, "gui.maxonaddon.horse.speed", current.speedFraction(), COLOR_SPEED);
        drawStat(graphics, barX, barY + 18, "gui.maxonaddon.horse.jump", current.jumpFraction(), COLOR_JUMP);
        drawStat(graphics, barX, barY + 36, "gui.maxonaddon.horse.health", current.healthFraction(), COLOR_HEALTH);

        // Цена — в блоках изумруда, как и списывает сервер.
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.maxonaddon.horse.price", current.priceBlocks()).withStyle(ChatFormatting.GREEN),
                centerX, centerY + 62, 0xFFFFFF);
    }

    /**
     * Рисует лошадь неподвижно.
     * <p>
     * Ванильный renderEntityInInventoryFollowsMouse сам разворачивает модель вслед за курсором —
     * для витрины это лишнее, лошадь должна просто стоять. Поэтому зовём renderEntityInInventory
     * напрямую и выставляем углы сами: тело, голова и её прошлый кадр (yHeadRotO, иначе шея
     * будет дёргаться при интерполяции) смотрят в одну сторону.
     */
    private void renderPreview(GuiGraphics graphics, int centerX, int centerY) {
        if (this.preview == null) return;

        this.preview.yBodyRot = this.previewYaw;
        this.preview.setYRot(this.previewYaw);
        this.preview.yHeadRot = this.previewYaw;
        this.preview.yHeadRotO = this.previewYaw;
        this.preview.setXRot(0.0F);

        // Разворот на 180 по Z — модель в GUI рисуется «вверх ногами» без него;
        // небольшой наклон по X даёт вид чуть сверху, как в инвентаре.
        Quaternionf cameraAngle = new Quaternionf().rotateX(12.0F * ((float) Math.PI / 180.0F));
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        pose.mul(cameraAngle);

        int viewCenterY = centerY + (VIEW_TOP_OFFSET + VIEW_BOTTOM_OFFSET) / 2;
        Vector3f offset = new Vector3f(0.0F, this.preview.getBbHeight() / 2.0F, 0.0F);

        InventoryScreen.renderEntityInInventory(graphics, centerX, viewCenterY, 26.0F, offset, pose, cameraAngle, this.preview);
    }

    /** Подпись слева, полоска справа — заполнение показывает стат относительно предела породы. */
    private void drawStat(GuiGraphics graphics, int x, int y, String key, double fraction, int color) {
        graphics.drawString(this.font, Component.translatable(key), x, y - 10, 0xFFFFFF);

        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, COLOR_BAR_BG);
        int filled = (int) Math.round(BAR_WIDTH * Math.max(0.0, Math.min(1.0, fraction)));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + BAR_HEIGHT, color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
