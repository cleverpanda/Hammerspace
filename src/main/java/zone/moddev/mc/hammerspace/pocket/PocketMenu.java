package zone.moddev.mc.hammerspace.pocket;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import zone.moddev.mc.hammerspace.HSRegistries;
import zone.moddev.mc.hammerspace.dock.PocketDockBlockEntity;
import zone.moddev.mc.hammerspace.pocket.PocketContents.Entry;

/**
 * The pocket screen's menu. Pocket slots ignore vanilla's click handling (which caps slots at the item's
 * stack size) and go through {@link PocketHandler}. The pocket itself sits in a hidden last slot so its
 * contents reach the client, whether it's in the player's inventory or in a dock.
 */
public class PocketMenu extends AbstractContainerMenu {
    public enum Action { CYCLE_EXTRACT, CYCLE_PLACE, TOGGLE_UNIFY, SELECT, TOGGLE_LOCK }

    private final Player player;
    private final Slot source;
    private final int lockedInventorySlot;
    private final @Nullable PocketDockBlockEntity dock;
    private final PocketHandler handler;
    private final int pocketSlots;

    /** Client side, from the buffer the server wrote in {@link #openHeld} or {@link #openDock}. */
    public static PocketMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int rows = buf.readVarInt();
        if (buf.readBoolean()) {
            return new PocketMenu(id, inventory, rows, new SimpleContainer(1), 0, -1, null);
        }
        int slot = buf.readVarInt();
        return new PocketMenu(id, inventory, rows, inventory, slot, slot, null);
    }

    public PocketMenu(int id, Inventory inventory, int rows, Container sourceContainer, int sourceIndex, int lockedInventorySlot,
                      @Nullable PocketDockBlockEntity dock) {
        super(HSRegistries.POCKET_MENU.get(), id);
        this.player = inventory.player;
        this.lockedInventorySlot = lockedInventorySlot;
        this.dock = dock;
        this.source = new HiddenSlot(sourceContainer, sourceIndex);
        this.handler = new PocketHandler(source::getItem, () -> {
            if (dock != null) dock.setChanged();
        });

        // Rows come from the opening packet: on the client the pocket may not have synced yet.
        this.pocketSlots = rows * 9;
        for (int i = 0; i < pocketSlots; i++) {
            addSlot(new PocketSlot(handler, i, 8 + i % 9 * 18, 18 + i / 9 * 18));
        }
        int top = rows * 18 + 31;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, top + 58));
        }
        addSlot(source);
    }

    public PocketHandler handler() {
        return handler;
    }

    public PocketTier tier() {
        return handler.tier();
    }

    public int pocketSlots() {
        return pocketSlots;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(source.getItem().getItem() instanceof PocketItem)) return false;
        return dock == null || !dock.isRemoved() && player.distanceToSqr(dock.getBlockPos().getCenter()) < 64;
    }

    private boolean isLocked(int slotId) {
        return slotId >= 0 && slotId < slots.size() && slots.get(slotId).container == player.getInventory()
                && slots.get(slotId).getContainerSlot() == lockedInventorySlot;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < pocketSlots) {
            clickPocketSlot(slotId, button, type, player);
            return;
        }
        // The open pocket can't be moved while its screen is open.
        if (isLocked(slotId) || type == ClickType.SWAP && button == lockedInventorySlot) return;
        super.clicked(slotId, button, type, player);
    }

    private void clickPocketSlot(int slot, int button, ClickType type, Player player) {
        Entry entry = handler.entry(slot);
        boolean infinite = handler.tier().infinite();
        switch (type) {
            case PICKUP -> {
                ItemStack carried = getCarried();
                if (!carried.isEmpty()) {
                    ItemStack offered = button == 1 ? carried.copyWithCount(1) : carried;
                    ItemStack left = handler.insert(offered, false);
                    carried.shrink(offered.getCount() - left.getCount());
                } else if (entry != null) {
                    int amount = Math.min(entry.count(), entry.item().getMaxStackSize());
                    setCarried(handler.take(slot, button == 1 ? (amount + 1) / 2 : amount, true, false));
                }
            }
            case QUICK_MOVE -> {
                if (entry == null) return;
                if (infinite) {
                    handler.clear(slot);
                    return;
                }
                ItemStack moving = handler.take(slot, entry.item().getMaxStackSize(), true, true);
                int before = moving.getCount();
                moveItemStackTo(moving, pocketSlots, pocketSlots + 36, true);
                if (moving.getCount() < before) handler.take(slot, before - moving.getCount(), true, false);
            }
            case CLONE -> {
                if (entry != null && player.hasInfiniteMaterials() && getCarried().isEmpty()) {
                    setCarried(entry.stack(entry.item().getMaxStackSize()));
                }
            }
            case THROW -> {
                if (entry != null && getCarried().isEmpty()) {
                    player.drop(handler.take(slot, button == 0 ? 1 : entry.item().getMaxStackSize(), true, false), true);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index >= pocketSlots && slot.hasItem() && !isLocked(index) && slot != source) {
            slot.set(handler.insert(slot.getItem(), false));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof PocketSlot) && slot != source;
    }

    /** Server side, from a {@code SlotActionPayload}. */
    public void apply(int slot, Action action, Player player) {
        if (action == Action.TOGGLE_LOCK) {
            if (player.hasInfiniteMaterials()) handler.setLocked(!handler.contents().locked());
            return;
        }
        if (slot < 0 || slot >= pocketSlots) return;
        switch (action) {
            case CYCLE_EXTRACT -> handler.cycleExtract(slot);
            case CYCLE_PLACE -> handler.cyclePlace(slot);
            case TOGGLE_UNIFY -> handler.toggleUnify(slot);
            case SELECT -> handler.select(slot);
            default -> {
            }
        }
    }

    /** A pocket slot: shows the handler's entry; all clicks are handled by the menu. */
    public static final class PocketSlot extends Slot {
        private static final Container NONE = new SimpleContainer(0);
        private final PocketHandler handler;

        PocketSlot(PocketHandler handler, int index, int x, int y) {
            super(NONE, index, x, y);
            this.handler = handler;
        }

        @Override
        public ItemStack getItem() {
            return handler.getStackInSlot(getContainerSlot());
        }

        @Override
        public boolean hasItem() {
            return handler.entry(getContainerSlot()) != null;
        }

        @Override
        public void set(ItemStack stack) {
            // The pocket stack in the hidden slot is the source of truth; slot updates are ignored.
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return handler.tier().maxPerSlot();
        }

        @Override
        public void setChanged() {
        }
    }

    /** Holds the pocket so it syncs to the client; never drawn or clickable. */
    private static final class HiddenSlot extends Slot {
        HiddenSlot(Container container, int index) {
            super(container, index, -10000, -10000);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }

    // --- Opening

    public static void openHeld(net.minecraft.server.level.ServerPlayer player, int inventorySlot) {
        ItemStack pocket = player.getInventory().getItem(inventorySlot);
        if (!(pocket.getItem() instanceof PocketItem)) return;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new PocketMenu(id, inv, PocketTier.of(pocket).rows(), inv, inventorySlot, inventorySlot, null),
                pocket.getHoverName()),
                buf -> {
                    buf.writeVarInt(PocketTier.of(pocket).rows());
                    buf.writeBoolean(false);
                    buf.writeVarInt(inventorySlot);
                });
    }

    public static void openDock(net.minecraft.server.level.ServerPlayer player, PocketDockBlockEntity dock) {
        if (dock.getPocket().isEmpty()) return;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new PocketMenu(id, inv, PocketTier.of(dock.getPocket()).rows(), dock.asContainer(), 0, -1, dock),
                dock.getPocket().getHoverName()),
                buf -> {
                    buf.writeVarInt(PocketTier.of(dock.getPocket()).rows());
                    buf.writeBoolean(true);
                });
    }
}
