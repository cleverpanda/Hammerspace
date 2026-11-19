package zone.moddev.mc.hammerspace.pocket;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import zone.moddev.mc.hammerspace.HSConfig;

/**
 * How many of a slot's items to keep back when pipes extract or the player places from it, stored as a plain count.
 * The amounts between {@link #ALL} and {@link #NONE} come from the server config.
 */
public final class KeepMode {
    public static final int ALL = Integer.MAX_VALUE;
    public static final int NONE = 0;

    private KeepMode() {}

    /** The mode new slots start on: the smallest configured amount. */
    public static int first() {
        return amounts().stream().findFirst().orElse(NONE);
    }

    /** All, then each configured amount from smallest to largest, then none. */
    public static int next(int keep) {
        if (keep == NONE) return ALL;
        for (int amount : amounts()) {
            if (keep == ALL || amount > keep) return amount;
        }
        return NONE;
    }

    private static List<Integer> amounts() {
        return HSConfig.get(HSConfig.KEEP_AMOUNTS).stream().map(Integer::intValue).sorted().distinct().toList();
    }

    /** "Extract: all but 16" style text; {@code action} is "extract" or "place". */
    public static MutableComponent describe(int keep, String action) {
        String key = "hammerspace.mode." + action;
        if (keep == ALL) return Component.translatable(key + ".all");
        if (keep == NONE) return Component.translatable(key + ".none");
        return Component.translatable(key + ".some", String.format("%,d", keep));
    }
}
