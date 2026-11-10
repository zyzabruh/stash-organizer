package com.pro0101.stashorganizer.modules.hyperglide;

/**
 * Envelope arithmetic behind the firework boost check of Grim issue #2794.
 *
 * <p>Kept free of game types on purpose: the numbers are what the issue argues about, so the arithmetic can be checked
 * on plain values instead of through a running client.
 */
public final class BoostEnvelope {
    /** Allowance added to each axis before the per-axis bounds are clamped. */
    public static final double skip = 0.05;

    private static final double epsilon = 1.0E-6;

    private BoostEnvelope() {}

    /**
     * Measures a motion against the bounds derived from a prediction.
     *
     * @param motion motion being checked
     * @param last velocity of the previous tick
     * @param rotation current look direction
     * @param previous look direction of the previous tick
     * @param predicted gliding velocity the server should expect
     * @param scaling largest per-axis offset around the prediction
     * @param vertical extra allowance added above the prediction
     * @return measured envelope, or null when either side is effectively zero
     */
    public static Result measure(Axis motion, Axis last, Axis rotation,
        Axis previous, Axis predicted, double scaling, double vertical) {

        if (motion.lengthSquared() < epsilon || predicted.lengthSquared() < epsilon) {
            return null;
        }

        double amount = Math.min(scaling, motion.length());

        double minX = Math.min(-skip, rotation.x()) + Math.min(-skip, previous.x());
        double minY = Math.min(-skip, rotation.y()) + Math.min(-skip, previous.y());
        double minZ = Math.min(-skip, rotation.z()) + Math.min(-skip, previous.z());
        double maxX = Math.max(skip, rotation.x()) + Math.max(skip, previous.x());
        double maxY = Math.max(skip, rotation.y()) + Math.max(skip, previous.y());
        double maxZ = Math.max(skip, rotation.z()) + Math.max(skip, previous.z());

        minX = Math.max(-amount, minX * amount);
        maxX = Math.min(amount, maxX * amount);
        minY = Math.max(-amount, minY * amount);
        maxY = Math.min(amount, maxY * amount);
        minZ = Math.max(-amount, minZ * amount);
        maxZ = Math.min(amount, maxZ * amount);

        double lowX = predicted.x() + Math.min(0.0, minX - last.x());
        double highX = predicted.x() + Math.max(0.0, maxX - last.x());
        double lowY = predicted.y() + Math.min(0.0, minY - last.y());
        double highY = predicted.y() + Math.max(0.0, maxY - last.y()) + vertical;
        double lowZ = predicted.z() + Math.min(0.0, minZ - last.z());
        double highZ = predicted.z() + Math.max(0.0, maxZ - last.z());

        double scale = Math.max(
            exceed(motion.x(), lowX, highX),
            Math.max(exceed(motion.y(), lowY, highY), exceed(motion.z(), lowZ, highZ))
        );

        return new Result(scale, motion.length() / predicted.length());
    }

    /**
     * Returns how far one axis exceeds its bounds.
     *
     * @param value current axis motion
     * @param low lower bound
     * @param high upper bound
     * @return exceedance ratio, 0 when inside the bounds
     */
    private static double exceed(double value, double low, double high) {
        if (value > high) return high > epsilon ? value / high : Double.MAX_VALUE;
        if (value < low) return low < -epsilon ? value / low : Double.MAX_VALUE;

        return 0.0;
    }

    /**
     * A plain three-axis value.
     *
     * @param x x component
     * @param y y component
     * @param z z component
     */
    public record Axis(double x, double y, double z) {
        /**
         * Returns the squared length of the vector.
         *
         * @return x² + y² + z²
         */
        public double lengthSquared() {
            return this.x * this.x + this.y * this.y + this.z * this.z;
        }

        /**
         * Returns the length of the vector.
         *
         * @return magnitude
         */
        public double length() {
            return Math.sqrt(this.lengthSquared());
        }
    }

    /**
     * Measured exceedance and the prediction ratio.
     *
     * @param scale per-axis exceedance scale
     * @param ratio motion length divided by prediction length
     */
    public record Result(double scale, double ratio) {
        /**
         * Checks whether the motion stayed inside the envelope.
         *
         * @return true when the exceedance is below the prediction ratio
         */
        public boolean bounded() {
            return this.scale < this.ratio;
        }

        /**
         * Checks whether any axis exceeded its bounds.
         *
         * @return true when the exceedance is above one
         */
        public boolean exceeded() {
            return this.scale > 1.0;
        }
    }
}
