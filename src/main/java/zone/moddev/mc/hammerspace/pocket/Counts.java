package zone.moddev.mc.hammerspace.pocket;

/** Short counts for slots and tooltips: 950, 1.2k, 34k, 2.1M, 2.1B. */
public final class Counts {
    private Counts() {}

    public static String format(long n) {
        if (n < 1000) return Long.toString(n);
        String[] units = {"k", "M", "B"};
        double v = n;
        int unit = -1;
        while (v >= 1000 && unit < units.length - 1) {
            v /= 1000;
            unit++;
        }
        return (v < 10 ? String.format(java.util.Locale.ROOT, "%.1f", Math.floor(v * 10) / 10) : Long.toString((long) v)) + units[unit];
    }
}
