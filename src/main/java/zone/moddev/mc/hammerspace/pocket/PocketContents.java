package zone.moddev.mc.hammerspace.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

/**
 * What a pocket holds, stored on the pocket as a data component. Counts live beside the item because
 * {@link ItemStack#CODEC} only saves counts up to 99.
 *
 * @param entries  filled slots, any order
 * @param selected slot used for placing and shown on the HUD, -1 for none
 * @param locked   creative pockets only: contents can't change
 */
public record PocketContents(List<Entry> entries, int selected, boolean locked) {
    public static final PocketContents EMPTY = new PocketContents(List.of(), -1, false);

    public static final Codec<PocketContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(PocketContents::entries),
            Codec.INT.optionalFieldOf("selected", -1).forGetter(PocketContents::selected),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(PocketContents::locked)
    ).apply(i, PocketContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketContents> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), PocketContents::entries,
            ByteBufCodecs.VAR_INT, PocketContents::selected,
            ByteBufCodecs.BOOL, PocketContents::locked,
            PocketContents::new);

    public PocketContents {
        entries = List.copyOf(entries);
    }

    /** The entries laid out by slot; slots past {@code size} are dropped. */
    public Entry[] bySlot(int size) {
        Entry[] slots = new Entry[size];
        for (Entry entry : entries) {
            if (entry.slot() >= 0 && entry.slot() < size) slots[entry.slot()] = entry;
        }
        return slots;
    }

    public static PocketContents of(Entry[] slots, int selected, boolean locked) {
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : slots) {
            if (entry != null) entries.add(entry);
        }
        return new PocketContents(entries, selected, locked);
    }

    /**
     * One filled slot.
     *
     * @param item  the item, count 1
     * @param count how many are stored
     * @param unify incoming items sharing a unify tag with this one turn into it
     */
    public record Entry(int slot, ItemStack item, int count, int extract, int place, boolean unify) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("slot").forGetter(Entry::slot),
                ItemStack.SINGLE_ITEM_CODEC.fieldOf("item").forGetter(Entry::item),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Entry::count),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("extract").forGetter(Entry::extract),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("place").forGetter(Entry::place),
                Codec.BOOL.optionalFieldOf("unify", false).forGetter(Entry::unify)
        ).apply(i, Entry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::slot,
                ItemStack.STREAM_CODEC, Entry::item,
                ByteBufCodecs.VAR_INT, Entry::count,
                ByteBufCodecs.VAR_INT, Entry::extract,
                ByteBufCodecs.VAR_INT, Entry::place,
                ByteBufCodecs.BOOL, Entry::unify,
                Entry::new);

        public Entry {
            item = item.copyWithCount(1);
        }

        public static Entry of(int slot, ItemStack item, int count) {
            return new Entry(slot, item, count, KeepMode.first(), KeepMode.first(), false);
        }

        public Entry withCount(int newCount) {
            return new Entry(slot, item, newCount, extract, place, unify);
        }

        public Entry withExtract(int mode) {
            return new Entry(slot, item, count, mode, place, unify);
        }

        public Entry withPlace(int mode) {
            return new Entry(slot, item, count, extract, mode, unify);
        }

        public Entry withUnify(boolean on) {
            return new Entry(slot, item, count, extract, place, on);
        }

        public boolean holds(ItemStack stack) {
            return ItemStack.isSameItemSameComponents(item, stack);
        }

        /** A real stack of this entry's item; the count may be over the item's normal stack size. */
        public ItemStack stack(int amount) {
            return item.copyWithCount(amount);
        }

        // ItemStack has no value equality; data components must compare by value or stacks never match.
        @Override
        public boolean equals(Object o) {
            return o instanceof Entry e && slot == e.slot && count == e.count && extract == e.extract && place == e.place
                    && unify == e.unify && ItemStack.isSameItemSameComponents(item, e.item);
        }

        @Override
        public int hashCode() {
            return Objects.hash(slot, ItemStack.hashItemAndComponents(item), count, extract, place, unify);
        }
    }
}
