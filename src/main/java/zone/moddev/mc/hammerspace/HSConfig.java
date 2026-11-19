package zone.moddev.mc.hammerspace;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client and server options. Tier sizes and unify tags are datapack data (see the README). */
public final class HSConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_HUD = BUILDER
            .comment("Show the selected item of a held pocket next to the hotbar. The HUD key toggles this.")
            .translation("hammerspace.config.show_hud")
            .define("showHud", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> KEEP_AMOUNTS = SERVER_BUILDER
            .comment("Amounts a pocket slot can keep back from pipes and from placing, cycled between \"keep all\" and \"keep none\".",
                    "New slots start on the smallest. /dank/null used 1, 16 and 64.")
            .translation("hammerspace.config.keep_amounts")
            .defineListAllowEmpty("keepAmounts", List.of(1, 16, 64), () -> 1, o -> o instanceof Integer i && i > 0 && i < Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue FALLBACK_ROWS = SERVER_BUILDER
            .comment("Rows of 9 slots for a pocket missing from the hammerspace:pocket_tier data map.")
            .translation("hammerspace.config.fallback_rows")
            .defineInRange("fallbackRows", 1, 1, 6);

    public static final ModConfigSpec.IntValue FALLBACK_MAX_PER_SLOT = SERVER_BUILDER
            .comment("How many of one item a slot holds, for a pocket missing from the hammerspace:pocket_tier data map.")
            .translation("hammerspace.config.fallback_max_per_slot")
            .defineInRange("fallbackMaxPerSlot", 64, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    private HSConfig() {}

    /** Server values are read before a world loads (tooltips, creative search); use the default until then. */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SERVER_SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
