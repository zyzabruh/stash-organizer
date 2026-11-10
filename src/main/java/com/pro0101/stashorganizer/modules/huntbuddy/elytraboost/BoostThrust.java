package com.pro0101.stashorganizer.modules.huntbuddy.elytraboost;

/**
 * Thrust model of a firework boost, kept apart from the module so a bench can run it on plain numbers.
 *
 * <p>Vanilla leaves every boosted tick at {@code v = 0.5 v + look (0.1 + 0.5 multiplier)} per axis, which is a
 * geometric series along the look direction: the speed after {@code n} ticks has a closed form, and the loop in the
 * tests is the same recurrence written out one tick at a time.
 */
public final class BoostThrust {
    /** One tick of boost, in milliseconds, as the duration slider counts it. */
    public static final int TICK_MS = 50;

    private BoostThrust() {}

    /**
     * Converts a boost duration into the number of ticks it buys.
     *
     * @param durationMs duration in milliseconds
     * @return whole ticks, never negative
     */
    public static int ticks(double durationMs) {
        return (int) Math.max(0, Math.floor(durationMs / TICK_MS));
    }

    /**
     * Applies one boosted tick to the speed along the look direction.
     *
     * @param speed speed before the tick
     * @param multiplier firework multiplier
     * @return speed after the tick
     */
    public static double step(double speed, double multiplier) {
        return 0.5 * speed + 0.1 + 0.5 * multiplier;
    }

    /**
     * Speed along the look direction after a whole boost.
     *
     * @param ticks number of boosted ticks
     * @param initial speed before the first tick
     * @param multiplier firework multiplier
     * @return speed after the last tick
     */
    public static double after(int ticks, double initial, double multiplier) {
        double plateau = 2.0 * (0.1 + 0.5 * multiplier);
        double decay = Math.pow(0.5, ticks);

        return decay * initial + plateau * (1.0 - decay);
    }
}
