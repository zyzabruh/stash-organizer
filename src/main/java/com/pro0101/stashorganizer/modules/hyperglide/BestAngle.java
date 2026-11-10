package com.pro0101.stashorganizer.modules.hyperglide;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.huntbuddy.RocketBoost;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Client;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Player;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Holds the glide at the pitch that keeps speed up.
 *
 * <p>Horizontal elytra speed comes from trading altitude for velocity: flying level is fast until the sink rate grows,
 * looking up converts speed into height. A proportional controller on vertical velocity keeps the glide near a chosen
 * sink rate, which is the practical way to stay fast without reaching an angle that stalls the flight.
 */
public class BestAngle extends Module {
    private final SettingGroup sgAngle = this.settings.createGroup("Angle");
    private final SettingGroup sgControl = this.settings.createGroup("Control");

    private final Setting<Double> fall = this.sgAngle.add(new DoubleSetting.Builder()
        .name("sink-rate")
        .description("Vertical speed to hold, in blocks per tick, as a positive sink.")
        .defaultValue(0.02)
        .min(0.0)
        .sliderMax(0.3)
        .decimalPlaces(3)
        .build()
    );

    private final Setting<Double> response = this.sgAngle.add(new DoubleSetting.Builder()
        .name("response")
        .description("Degrees of pitch per block per tick of vertical error.")
        .defaultValue(60.0)
        .min(1.0)
        .sliderMax(200.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Double> lowest = this.sgAngle.add(new DoubleSetting.Builder()
        .name("min-pitch")
        .description("Highest the nose may point, in degrees.")
        .defaultValue(-25.0)
        .min(-90.0)
        .sliderMax(0.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Double> highest = this.sgAngle.add(new DoubleSetting.Builder()
        .name("max-pitch")
        .description("Lowest the nose may point, in degrees.")
        .defaultValue(15.0)
        .min(0.0)
        .sliderMax(90.0)
        .decimalPlaces(1)
        .build()
    );

    private final SettingGroup sgBoost = this.settings.createGroup("Rocket Boost");

    private final Setting<Boolean> boostSteering = this.sgBoost.add(new BoolSetting.Builder()
        .name("boost-steering")
        .description("While rocket-boost reports a boost, hold boost-pitch instead of the sink controller. Level puts the whole push on the horizontal axes, which is where the speed is and where the envelope can be measured.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> boostPitch = this.sgBoost.add(new DoubleSetting.Builder()
        .name("boost-pitch")
        .description("Pitch held during a boost, in degrees. 0 keeps the firework push flat; a few degrees down trades height for speed.")
        .defaultValue(0.0)
        .min(-90.0)
        .max(90.0)
        .sliderRange(-30.0, 30.0)
        .decimalPlaces(1)
        .visible(this.boostSteering::get)
        .build()
    );

    private final Setting<Boolean> boostHeading = this.sgBoost.add(new BoolSetting.Builder()
        .name("boost-heading")
        .description("Aligns the yaw on horizontal motion during a boost. On a cardinal heading the boost lands on a single axis, which is the case Grim's per-axis box judges most tightly.")
        .defaultValue(false)
        .visible(this.boostSteering::get)
        .build()
    );

    private final Setting<Boolean> gliding = this.sgControl.add(new BoolSetting.Builder()
        .name("only-gliding")
        .description("Leaves the pitch alone until the player is gliding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> idle = this.sgControl.add(new BoolSetting.Builder()
        .name("only-idle")
        .description("Stops steering while a movement key is held.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> heading = this.sgControl.add(new BoolSetting.Builder()
        .name("lock-heading")
        .description("Points the yaw along the current horizontal motion.")
        .defaultValue(false)
        .build()
    );

    public BestAngle() {
        super(StashOrganizerAddon.CATEGORY, "best-angle",
            "Holds the glide at the pitch that keeps horizontal speed up."
        );
    }

    /**
     * Adjusts pitch, and optionally yaw, for the current motion.
     *
     * @param event pre-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Client.ready() || this.mc.player == null) return;
        if (this.gliding.get() && !this.mc.player.isGliding()) return;

        Vec3d velocity = this.mc.player.getVelocity();

        // A boost is the one moment where the angle is decided for us: rocket-boost measures its
        // per-axis box against the look vector this tick, so holding it flat is what turns the
        // firework push into horizontal speed instead of altitude.
        if (this.boosting() && this.boostSteering.get()) {
            this.mc.player.setPitch(this.boostPitch.get().floatValue());

            if (this.boostHeading.get()) {
                this.aim(velocity);
            }

            return;
        }

        // Manual steering wins over the glide controller, but not over a boost: the boost is what
        // this module is being combined with, and it is also when the player is holding a key.
        if (this.idle.get() && this.pressed()) return;

        double error = velocity.y + this.fall.get();

        float pitch = (float) (this.mc.player.getPitch() + error * this.response.get());
        pitch = MathHelper.clamp(
            pitch, this.lowest.get().floatValue(), this.highest.get().floatValue()
        );

        this.mc.player.setPitch(pitch);

        if (this.heading.get()) {
            this.aim(velocity);
        }
    }

    /**
     * Checks whether rocket-boost is currently holding a boost open.
     *
     * @return true when a tracked rocket is pushing the player
     */
    private boolean boosting() {
        RocketBoost rocket = Modules.get().get(RocketBoost.class);

        return rocket != null && rocket.isActive() && rocket.isBoosting();
    }

    /**
     * Points the yaw along the horizontal motion.
     *
     * @param velocity current motion
     */
    private void aim(Vec3d velocity) {
        if (velocity.horizontalLengthSquared() < 1.0E-4) return;

        float yaw = (float) (MathHelper.atan2(velocity.z, velocity.x) * 180.0 / Math.PI) - 90.0F;
        Player.rotate(yaw);
    }

    /**
     * Checks whether a movement key is held.
     *
     * @return true when the player is steering
     */
    private boolean pressed() {
        return this.mc.options.forwardKey.isPressed()
            || this.mc.options.backKey.isPressed()
            || this.mc.options.leftKey.isPressed()
            || this.mc.options.rightKey.isPressed();
    }
}
