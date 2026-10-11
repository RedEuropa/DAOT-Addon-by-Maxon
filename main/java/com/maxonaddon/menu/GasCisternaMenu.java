package com.maxonaddon.menu;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Своё меню газовой цистерны — РОВНО 2 реальных слота (лёд, баллон), те же индексы, что
 * читает GasCisternaBlockEntity.serverTickCisterna. Раньше цистерна открывала УНАСЛЕДОВАННОЕ
 * от daot.IceburstFurnaceBlockEntity родное меню печки (createMenu никогда не был
 * переопределён) — там ещё 2 слота "топливо"/"выход" от оригинального рецепта печки, которые
 * наша логика вообще не читает. Игрок мог не понимать, куда класть лёд, и решить, что тот
 * "пропадает" — визуально это была печка, а не цистерна.
 *
 * BlockPos нужен клиенту, чтобы GasCisternaScreen мог напрямую читать актуальный storedGas
 * из клиентского зеркала блок-сущности (оно и так синхронизируется через обычный
 * block-entity-data пакет при каждом setChanged()) — отдельный сетевой пакет не нужен.
 */
public class GasCisternaMenu extends AbstractContainerMenu {
    private final Container container;
    private final BlockPos pos;

    /** Клиентский конструктор — вызывается фабрикой ExtendedScreenHandlerType при получении пакета открытия. */
    public GasCisternaMenu(int id, Inventory playerInv, BlockPos pos) {
        this(id, playerInv, new SimpleContainer(2), pos);
    }

    public GasCisternaMenu(int id, Inventory playerInv, Container container, BlockPos pos) {
        super(MaxonDaotAddon.GAS_CISTERNA_MENU, id);
        this.container = container;
        this.pos = pos;
        container.startOpen(playerInv.player);

        // Слот 0 — лёд, слот 1 — баллон. Ограничения по типу предмета уже работают через
        // SlotMixin (он патчит Slot.mayPlace на GasCisternaBlockEntity.canPlaceItem).
        this.addSlot(new Slot(container, 0, 44, 42));
        this.addSlot(new Slot(container, 1, 114, 42));

        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 9; ++c) {
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; ++c) {
            this.addSlot(new Slot(playerInv, c, 8 + c * 18, 142));
        }
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();

            if (index < 2) {
                if (!this.moveItemStackTo(itemStack2, 2, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (!this.moveItemStackTo(itemStack2, 0, 2, false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack2.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return itemStack;
    }
}
