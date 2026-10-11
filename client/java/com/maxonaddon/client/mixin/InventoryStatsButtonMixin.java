package com.maxonaddon.client.mixin;

import com.maxonaddon.client.ClientModState;
import com.maxonaddon.client.gui.StatsButtonWidget;
import com.maxonaddon.client.gui.TraumaButtonWidget;
import com.maxonaddon.client.gui.QuestJournalButton; // Импортируем нашу кнопку
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({InventoryScreen.class, CreativeModeInventoryScreen.class})
public abstract class InventoryStatsButtonMixin extends EffectRenderingInventoryScreen {

    public InventoryStatsButtonMixin() {
        super(null, null, null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void maxon$addStatsButton(CallbackInfo ci) {
        // Кнопки идут вертикальным столбиком вдоль правого края окна — как вкладки в креативе.
        //
        // Раньше в выживании они выстраивались в ряд вправо от leftPos + 126 с шагом 22, и
        // последняя начиналась на leftPos + 192 при ширине окна инвентаря 176 — то есть висела
        // заметно правее самой рамки. Столбик снаружи выглядит намеренно, а не как съехавшая
        // вёрстка, и не зависит от того, сколько кнопок включено конфигом.
        final int buttonSize = 20;
        final int step = buttonSize + 2;

        int columnX = this.leftPos + this.imageWidth + 4;
        // Крупный GUI-скейл или узкое окно — прижимаем столбик к правому краю экрана,
        // иначе кнопки уехали бы за пределы видимой области и стали недоступны.
        if (columnX + buttonSize > this.width) {
            columnX = this.width - buttonSize - 2;
        }

        int y = this.topPos + 4;

        // Кнопка статистики (если включена)
        if (ClientModState.isStatisticsEnabled()) {
            this.addRenderableWidget(new StatsButtonWidget(columnX, y, button -> {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.maxonaddon.network.RequestStatsPayload());
            }));
            y += step;
        }

        // Кнопка травм
        this.addRenderableWidget(new TraumaButtonWidget(columnX, y, button -> {
            this.minecraft.setScreen(new com.maxonaddon.client.gui.TraumaScreen());
        }));
        y += step;

        // Кнопка перков. Скрыта, если система выключена — конфиг тут уже серверный
        // (ConfigSyncPayload), так что на сервере без перков кнопки не будет ни у кого.
        if (com.maxonaddon.MaxonConfig.get().perksSystemEnabled) {
            this.addRenderableWidget(new com.maxonaddon.client.gui.PerkButtonWidget(columnX, y, button -> {
                this.minecraft.setScreen(new com.maxonaddon.client.gui.PerksScreen());
            }));
            y += step;
        }

        // Журнал заданий. Скрыт целиком, если сервер выключил систему квестов и репутации
        // (MaxonConfig.reputationAllow) — заданий не бывает, журнал всегда был бы пуст.
        // Сдвиг по y идёт только за реально добавленными кнопками, поэтому дырок в столбике
        // не остаётся, какие бы части системы ни были выключены.
        if (com.maxonaddon.client.ClientModState.isReputationEnabled()) {
            this.addRenderableWidget(new QuestJournalButton(columnX, y, button -> {
                this.minecraft.setScreen(new com.maxonaddon.client.gui.QuestJournalScreen());
            }));
        }
    }
}