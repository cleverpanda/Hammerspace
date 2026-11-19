package zone.moddev.mc.hammerspace.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.hammerspace.HSConfig;
import zone.moddev.mc.hammerspace.HSRegistries;

/**
 * A pocket's size, from the {@code hammerspace:pocket_tier} item data map.
 *
 * @param rows       rows of 9 slots, 1 to 6
 * @param maxPerSlot how many of one item a slot holds
 * @param infinite   creative pocket: taking items out never uses them up
 */
public record PocketTier(int rows, int maxPerSlot, boolean infinite) {
    public static final Codec<PocketTier> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 6).fieldOf("rows").forGetter(PocketTier::rows),
            ExtraCodecs.POSITIVE_INT.fieldOf("max_per_slot").forGetter(PocketTier::maxPerSlot),
            Codec.BOOL.optionalFieldOf("infinite", false).forGetter(PocketTier::infinite)
    ).apply(i, PocketTier::new));

    public int slots() {
        return rows * 9;
    }

    public static PocketTier of(ItemStack pocket) {
        PocketTier tier = pocket.getItemHolder().getData(HSRegistries.POCKET_TIER);
        return tier != null ? tier : new PocketTier(HSConfig.get(HSConfig.FALLBACK_ROWS), HSConfig.get(HSConfig.FALLBACK_MAX_PER_SLOT), false);
    }
}
