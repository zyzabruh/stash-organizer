package com.pro0101.stashorganizer.modules.hyperglide;

/**
 * Decides when a rocket-cycle phase is finished, from the vertical speed rather than from a tick count.
 *
 * <p>The climb is worth ending once the rocket has actually bought height, which is a vertical speed above the target,
 * not a number of ticks: the same ten ticks climb far less at a shallow pitch than at a steep one. The dive is worth
 * ending once the fall has been given back, so the phase ends when the player is no longer dropping faster than the
 * release rate. Both keep a minimum and a timeout, because the vertical speed alone can leave a phase stuck when a
 * server correction flattens it.
 */
public final class RocketCycleLogic {
    private RocketCycleLogic() {}

    /**
     * Whether the climb has bought enough height to be worth diving for.
     *
     * @param verticalSpeed current vertical speed, positive upwards
     * @param ticks ticks spent in the climb so far
     * @param minTicks ticks to stay in the climb regardless of speed
     * @param maxTicks tick at which the climb ends anyway
     * @param rise vertical speed that finishes the climb
     * @return true when the phase is over
     */
    public static boolean climbDone(double verticalSpeed, int ticks, int minTicks, int maxTicks, double rise) {
        if (ticks < minTicks) return false;

        return verticalSpeed >= rise || ticks >= maxTicks;
    }

    /**
     * Whether the dive has converted the height it was given.
     *
     * @param verticalSpeed current vertical speed, positive upwards
     * @param ticks ticks spent in the dive so far
     * @param minTicks ticks to stay in the dive regardless of speed
     * @param maxTicks tick at which the dive ends anyway
     * @param release fall rate at which the dive ends, as a positive number
     * @return true when the phase is over
     */
    public static boolean diveDone(double verticalSpeed, int ticks, int minTicks, int maxTicks, double release) {
        if (ticks < minTicks) return false;

        return verticalSpeed >= -release || ticks >= maxTicks;
    }
}
