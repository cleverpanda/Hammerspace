package zone.moddev.mc.hammerspace.pocket;

import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import zone.moddev.mc.hammerspace.HSRegistries;
import zone.moddev.mc.hammerspace.pocket.PocketContents.Entry;

/**
 * Reads and writes a pocket's contents. Every change writes a new {@link PocketContents} back onto the pocket stack.
 * Each item type lives in exactly one slot; per-slot modes belong to that item.
 */
public final class PocketHandler implements IItemHandler {
    private final Supplier<ItemStack> pocket;
    private final Runnable onChange;

    public PocketHandler(Supplier<ItemStack> pocket, Runnable onChange) {
        this.pocket = pocket;
        this.onChange = onChange;
    }

    public static PocketHandler of(ItemStack pocket) {
        return new PocketHandler(() -> pocket, () -> {});
    }

    public ItemStack pocket() {
        return pocket.get();
    }

    public PocketTier tier() {
        return PocketTier.of(pocket());
    }

    public PocketContents contents() {
        return pocket().getOrDefault(HSRegistries.CONTENTS, PocketContents.EMPTY);
    }

    private Entry[] slots() {
        return contents().bySlot(tier().slots());
    }

    private void save(Entry[] slots, int selected, boolean locked) {
        if (selected < 0 || selected >= slots.length || slots[selected] == null) selected = nearestFilled(slots, selected);
        pocket().set(HSRegistries.CONTENTS, PocketContents.of(slots, selected, locked));
        onChange.run();
    }

    private void save(Entry[] slots) {
        PocketContents c = contents();
        save(slots, c.selected(), c.locked());
    }

    /** The filled slot at or before {@code from}, else the first filled slot, else -1. */
    private static int nearestFilled(Entry[] slots, int from) {
        for (int i = Math.min(from, slots.length - 1); i >= 0; i--) {
            if (slots[i] != null) return i;
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) return i;
        }
        return -1;
    }

    public @Nullable Entry entry(int slot) {
        return slot >= 0 && slot < tier().slots() ? slots()[slot] : null;
    }

    public static boolean canStore(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof PocketItem) && !stack.is(HSRegistries.NOT_STORABLE);
    }

    /** The slot holding this item (after unifying), or -1. */
    public int find(ItemStack stack) {
        ItemStack unified = unify(stack, slots());
        Entry[] slots = slots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && slots[i].holds(unified)) return i;
        }
        return -1;
    }

    /** An incoming item turns into a stored item flagged for unifying when they share an allowed tag. */
    private static ItemStack unify(ItemStack stack, Entry[] slots) {
        if (stack.is(HSRegistries.NEVER_UNIFY)) return stack;
        for (Entry entry : slots) {
            if (entry != null && entry.unify() && !entry.holds(stack) && UnifyTags.share(entry.item(), stack)) {
                return entry.stack(stack.getCount());
            }
        }
        return stack;
    }

    // --- IItemHandler: pipes, the dock, the menu

    @Override
    public int getSlots() {
        return tier().slots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        Entry entry = entry(slot);
        return entry == null ? ItemStack.EMPTY : entry.stack(entry.count());
    }

    @Override
    public int getSlotLimit(int slot) {
        return tier().maxPerSlot();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!canStore(stack)) return false;
        Entry[] slots = slots();
        ItemStack unified = unify(stack, slots);
        Entry entry = slots[slot];
        return entry == null ? find(unified) < 0 : entry.holds(unified);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // The source can turn empty under an open screen or a cached pipe handler; never write onto ItemStack.EMPTY.
        if (!(pocket().getItem() instanceof PocketItem)) return stack;
        PocketContents contents = contents();
        if (stack.isEmpty() || contents.locked() || !canStore(stack)) return stack;
        Entry[] slots = contents.bySlot(tier().slots());
        if (slot < 0 || slot >= slots.length) return stack;
        ItemStack unified = unify(stack, slots);
        Entry entry = slots[slot];
        if (entry == null && find(unified) >= 0 || entry != null && !entry.holds(unified)) return stack;

        PocketTier tier = tier();
        long total = (entry == null ? 0L : entry.count()) + unified.getCount();
        int kept = tier.infinite() ? tier.maxPerSlot() : (int) Math.min(total, tier.maxPerSlot());
        long left = tier.infinite() ? 0 : total - kept;
        if (!simulate) {
            slots[slot] = entry == null ? Entry.of(slot, unified, kept) : entry.withCount(kept);
            save(slots, contents.selected() < 0 ? slot : contents.selected(), contents.locked());
        }
        return left == 0 ? ItemStack.EMPTY : stack.copyWithCount((int) left);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        Entry entry = entry(slot);
        if (entry == null) return ItemStack.EMPTY;
        return take(slot, Math.min(amount, entry.item().getMaxStackSize()), false, simulate);
    }

    // --- Pocket operations

    /** Puts a stack in the slot that already holds it, else the first empty slot. Returns what didn't fit. */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (!canStore(stack)) return stack;
        int existing = find(stack);
        if (existing >= 0) return insertItem(existing, stack, simulate);
        Entry[] slots = slots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) return insertItem(i, stack, simulate);
        }
        return stack;
    }

    /**
     * Takes up to {@code amount} from a slot. Unless {@code ignoreMode}, the slot's extract mode keeps some back.
     * Creative pockets hand out copies without running down.
     */
    public ItemStack take(int slot, int amount, boolean ignoreMode, boolean simulate) {
        Entry[] slots = slots();
        Entry entry = slot >= 0 && slot < slots.length ? slots[slot] : null;
        if (entry == null || amount < 1) return ItemStack.EMPTY;
        if (tier().infinite()) return entry.stack(amount);
        long available = entry.count() - (ignoreMode ? 0L : entry.extract());
        int n = (int) Math.min(amount, available);
        if (n < 1) return ItemStack.EMPTY;
        if (!simulate) {
            slots[slot] = entry.count() == n ? null : entry.withCount(entry.count() - n);
            save(slots);
        }
        return entry.stack(n);
    }

    /** Removes a slot's entry entirely (creative pockets), returning nothing. */
    public void clear(int slot) {
        Entry[] slots = slots();
        if (slot < 0 || slot >= slots.length || slots[slot] == null || contents().locked()) return;
        slots[slot] = null;
        save(slots);
    }

    public void cycleExtract(int slot) {
        edit(slot, e -> e.withExtract(KeepMode.next(e.extract())));
    }

    public void cyclePlace(int slot) {
        edit(slot, e -> e.withPlace(KeepMode.next(e.place())));
    }

    public void toggleUnify(int slot) {
        edit(slot, e -> e.withUnify(!e.unify()));
    }

    private void edit(int slot, java.util.function.UnaryOperator<Entry> change) {
        Entry[] slots = slots();
        if (slot < 0 || slot >= slots.length || slots[slot] == null) return;
        slots[slot] = change.apply(slots[slot]);
        save(slots);
    }

    public int selected() {
        return contents().selected();
    }

    public void select(int slot) {
        Entry[] slots = slots();
        if (slot >= 0 && slot < slots.length && slots[slot] != null) save(slots, slot, contents().locked());
    }

    /** Moves the selection to the next (or previous) filled slot, wrapping around. */
    public void cycleSelected(boolean forward) {
        Entry[] slots = slots();
        int current = selected();
        for (int step = 1; step <= slots.length; step++) {
            int i = Math.floorMod(current + (forward ? step : -step), slots.length);
            if (slots[i] != null) {
                if (i != current) save(slots, i, contents().locked());
                return;
            }
        }
    }

    public @Nullable Entry selectedEntry() {
        return entry(selected());
    }

    public void setLocked(boolean locked) {
        if (!tier().infinite()) return;
        save(slots(), selected(), locked);
    }
}
