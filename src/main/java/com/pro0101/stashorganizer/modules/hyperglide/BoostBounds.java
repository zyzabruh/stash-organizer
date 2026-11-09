package com.pro0101.stashorganizer.modules.hyperglide;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Client;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Elytra;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Measures a firework boost against the gliding envelope of Grim issue #2794.
 *
 * <p>The reported bypass recomputes the gliding velocity the server should predict, derives per-axis bounds from the
 * previous motion plus that prediction, and compares the boosted motion against them. The same arithmetic runs here so
 * the excess can be read directly, with an option to recadre the client to the envelope.
 *
 * <p>The original also scales the motion <em>up</em> when it stays inside the envelope. That part is deliberately not
 * reproduced: growing the motion is exactly what this measurement exposes.
 */
public class BoostBounds extends Module {
    private static final double skip = 0.05;
    private static final double epsilon = 1.0E-6;

    private final SettingGroup sgLimits = this.settings.createGroup("Limits");
    private final SettingGroup sgReport = this.settings.createGroup("Report");

    private final Setting<Boolean> clamp = this.sgLimits.add(new BoolSetting.Builder()
        .name("clamp")
        .description("Shrinks the motion to the envelope when it exceeds the prediction.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> override = this.sgLimits.add(new BoolSetting.Builder()
        .name("override-velocity")
        .description("Replaces the velocity the rocket applies with the measured bound.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scaling = this.sgLimits.add(new DoubleSetting.Builder()
        .name("axis-scaling")
        .description("Largest per-axis offset allowed around the predicted velocity.")
        .defaultValue(0.05)
        .min(0.0)
        .sliderMax(0.35)
        .decimalPlaces(3)
        .build()
    );

    private final Setting<Double> vertical = this.sgLimits.add(new DoubleSetting.Builder()
        .name("vertical-scaling")
        .description("Extra vertical allowance added to the predicted bound.")
        .defaultValue(0.03)
        .min(0.0)
        .sliderMax(0.35)
        .decimalPlaces(3)
        .build()
    );

    private final Setting<Boolean> report = this.sgReport.add(new BoolSetting.Builder()
        .name("report")
        .description("Writes the measured envelope to chat at the chosen interval.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> interval = this.sgReport.add(new DoubleSetting.Builder()
        .name("interval")
        .description("Seconds between reports.")
        .defaultValue(1.0)
        .min(0.2)
        .sliderMax(10.0)
        .decimalPlaces(1)
        .build()
    );

    private Vec3d velocity = Vec3d.ZERO;
    private float yaw;
    private float pitch;

    private long stamp;
    private double scale;
    private double ratio;
    private boolean bounded;

    public BoostBounds() {
        super(StashOrganizerAddon.CATEGORY, "boost-bounds",
            "Measures firework boost against the predicted gliding envelope (Grim #2794)."
        );
    }

    /**
     * Clears stored motion when the module starts.
     */
    @Override
    public void onActivate() {
        this.velocity = Vec3d.ZERO;
        this.yaw = 0.0F;
        this.pitch = 0.0F;

        this.stamp = 0L;
        this.scale = 0.0;
        this.ratio = 0.0;
        this.bounded = false;
    }

    /**
     * Clears stored motion when the module stops.
     */
    @Override
    public void onDeactivate() {
        this.velocity = Vec3d.ZERO;
        this.bounded = false;

    }

    //region Event handling

    /**
     * Measures the boost of the current tick and applies the optional clamp.
     *
     * @param event post-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Client.ready() || this.mc.player == null) return;

        Vec3d motion = this.mc.player.getVelocity();
        Vec3d rotation = Vec3d.fromPolar(
            this.mc.player.getPitch(), this.mc.player.getYaw()
        ).normalize();

        if (this.mc.player.isGliding() && this.rocket()) {
            this.measure(motion, rotation);
        } else {
            this.bounded = false;
        }

        this.velocity = motion;
        this.yaw = this.mc.player.getYaw();
        this.pitch = this.mc.player.getPitch();
    }

    //endregion

    //region Envelope arithmetic

    /**
     * Measures the current motion against the predicted envelope.
     *
     * @param motion current client motion
     * @param rotation current look direction
     */
    private void measure(Vec3d motion, Vec3d rotation) {
        Result result = this.limits(motion, rotation);
        if (result == null) {
            this.bounded = false;
            return;
        }

        this.scale = result.scale;
        this.ratio = result.ratio;
        this.bounded = result.scale < result.ratio;

        if (this.clamp.get() && result.scale > 1.0) {
            this.mc.player.setVelocity(motion.multiply(1.0 / result.scale));
        }

        this.report();
    }

    /**
     * Computes the per-axis exceedance and the prediction ratio.
     *
     * @param motion current client motion
     * @param rotation current look direction
     * @return measured envelope, or null when there is nothing to compare
     */
    private Result limits(Vec3d motion, Vec3d rotation) {
        Vec3d previous = Vec3d.fromPolar(this.pitch, this.yaw).normalize();
        Vec3d predicted = Elytra.glide(this.velocity, rotation);

        if (motion.lengthSquared() < epsilon || predicted.lengthSquared() < epsilon) {
            return null;
        }

        double amount = Math.min(this.scaling.get(), motion.length());

        double minX = Math.min(-skip, rotation.x) + Math.min(-skip, previous.x);
        double minY = Math.min(-skip, rotation.y) + Math.min(-skip, previous.y);
        double minZ = Math.min(-skip, rotation.z) + Math.min(-skip, previous.z);
        double maxX = Math.max(skip, rotation.x) + Math.max(skip, previous.x);
        double maxY = Math.max(skip, rotation.y) + Math.max(skip, previous.y);
        double maxZ = Math.max(skip, rotation.z) + Math.max(skip, previous.z);

        minX = Math.max(-amount, minX * amount);
        maxX = Math.min(amount, maxX * amount);
        minY = Math.max(-amount, minY * amount);
        maxY = Math.min(amount, maxY * amount);
        minZ = Math.max(-amount, minZ * amount);
        maxZ = Math.min(amount, maxZ * amount);

        Vec3d base = this.velocity;
        double minOffsetX = Math.min(0.0, minX - base.x);
        double maxOffsetX = Math.max(0.0, maxX - base.x);
        double minOffsetY = Math.min(0.0, minY - base.y);
        double maxOffsetY = Math.max(0.0, maxY - base.y);
        double minOffsetZ = Math.min(0.0, minZ - base.z);
        double maxOffsetZ = Math.max(0.0, maxZ - base.z);

        double lowX = predicted.x + minOffsetX;
        double highX = predicted.x + maxOffsetX;
        double lowY = predicted.y + minOffsetY;
        double highY = predicted.y + maxOffsetY + this.vertical.get();
        double lowZ = predicted.z + minOffsetZ;
        double highZ = predicted.z + maxOffsetZ;

        double exceedX = this.exceed(motion.x, lowX, highX);
        double exceedY = this.exceed(motion.y, lowY, highY);
        double exceedZ = this.exceed(motion.z, lowZ, highZ);

        double scale = Math.max(exceedX, Math.max(exceedY, exceedZ));
        double ratio = motion.length() / predicted.length();

        return new Result(scale, ratio);
    }

    /**
     * Returns how far one axis exceeds its bounds.
     *
     * @param value current axis motion
     * @param low lower bound
     * @param high upper bound
     * @return exceedance ratio, 0 when inside the bounds
     */
    private double exceed(double value, double low, double high) {
        if (value > high) return high > epsilon ? value / high : Double.MAX_VALUE;
        if (value < low) return low < -epsilon ? value / low : Double.MAX_VALUE;

        return 0.0;
    }

    //endregion

    //region Reporting

    /**
     * Writes the measured envelope to chat at the configured interval.
     */
    private void report() {
        if (!this.report.get()) return;

        long now = System.currentTimeMillis();
        long wait = (long) (this.interval.get() * 1000.0);

        if (now - this.stamp < wait) return;
        this.stamp = now;

        this.info(
            "scale %.2f ratio %.2f %s",
            this.scale, this.ratio,
            this.bounded ? "inside" : "outside"
        );
    }

    /**
     * Checks whether a rocket is boosting the player.
     *
     * @return true when a firework rocket is close to the player
     */
    private boolean rocket() {
        return !this.mc.world.getEntitiesByClass(
            FireworkRocketEntity.class,
            this.mc.player.getBoundingBox().expand(12.0),
            rocket -> true
        ).isEmpty();
    }

    //endregion

    //region Accessors

    /**
     * Bounds the velocity before the rocket applies it.
     *
     * <p>The envelope is computed from the velocity the rocket is about to apply, not from the previous measurement, so
     * the bound and the boosted tick describe the same moment.
     *
     * @param vanilla velocity computed by the rocket
     * @return velocity bounded to the envelope, or the vanilla velocity when it already fits
     */
    public Vec3d override(Vec3d vanilla) {
        if (!this.override.get() || this.mc.player == null) return vanilla;

        Vec3d rotation = Vec3d.fromPolar(
            this.mc.player.getPitch(), this.mc.player.getYaw()
        ).normalize();

        Result result = this.limits(vanilla, rotation);
        if (result == null || result.scale <= 1.0) return vanilla;

        this.scale = result.scale;
        this.ratio = result.ratio;
        return vanilla.multiply(1.0 / result.scale);
    }

    /**
     * Returns the last computed exceedance scale.
     *
     * @return exceedance scale, above 1 when outside the envelope
     */
    public double scale() {
        return this.scale;
    }

    /**
     * Returns the last measured motion to prediction ratio.
     *
     * @return measured ratio
     */
    public double ratio() {
        return this.ratio;
    }

    /**
     * Checks whether the last boost stayed inside the envelope.
     *
     * @return true when the boost was inside the predicted bounds
     */
    public boolean bounded() {
        return this.bounded;
    }

    //endregion

    //region Data structures

    /**
     * Stores the measured exceedance and the prediction ratios.
     *
     * @param scale per-axis exceedance scale
     * @param ratio measured motion to prediction ratio
     * @param ratio measured motion to prediction ratio
     */
    private record Result(double scale, double ratio) {}

    //endregion
}
