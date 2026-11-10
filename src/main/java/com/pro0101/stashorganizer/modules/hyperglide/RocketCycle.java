package com.pro0101.stashorganizer.modules.hyperglide;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.huntbuddy.RocketBoost;
import com.pro0101.stashorganizer.modules.huntbuddy.recast.RocketLauncher;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Client;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Player;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;

/**
 * Runs a glide cycle: climb on a rocket, dive for speed, then hold level.
 *
 * <p>A firework pushed while looking up buys altitude and speed at the same time; pointing down afterwards converts
 * that altitude into horizontal speed, because the glide step redirects the fall along the look. Two ways decide
 * which of the three phases is being flown: the automatic one holds a height band around a target, so a dive can no
 * longer leave the player low with nothing bringing them back up, and the manual one follows the jump and sneak keys
 * the way a controlled flight would. The multiplier stays with rocket-boost: with auto-speed on it solves the
 * per-axis bound for whatever look this module hands it.
 */
public class RocketCycle extends Module {
    /** How the phase is chosen. */
    public enum Mode {
        /** Holds a height band around the target altitude. */
        AUTOMATIC,
        /** Follows the jump and sneak keys. */
        MANUAL
    }

    private final SettingGroup sgCycle = this.settings.createGroup("Cycle");
    private final SettingGroup sgLaunch = this.settings.createGroup("Launch");
    private final SettingGroup sgControl = this.settings.createGroup("Control");

    private final Setting<Mode> mode = this.sgCycle.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("AUTOMATIC holds a height band and fires the rockets it needs; MANUAL follows jump and sneak.")
        .defaultValue(Mode.AUTOMATIC)
        .build()
    );

    private final Setting<Double> target = this.sgCycle.add(new DoubleSetting.Builder()
        .name("target-altitude")
        .description("Altitude the automatic mode holds, in blocks.")
        .defaultValue(120.0)
        .min(-64.0)
        .max(320.0)
        .sliderRange(0.0, 256.0)
        .decimalPlaces(0)
        .visible(() -> this.mode.get() == Mode.AUTOMATIC && !this.capture.get())
        .build()
    );

    private final Setting<Boolean> capture = this.sgCycle.add(new BoolSetting.Builder()
        .name("start-from-current-altitude")
        .description("Starts the band around the altitude you activate at instead of the fixed target.")
        .defaultValue(true)
        .visible(() -> this.mode.get() == Mode.AUTOMATIC)
        .build()
    );

    private final Setting<Double> band = this.sgCycle.add(new DoubleSetting.Builder()
        .name("height-band")
        .description("Half-height of the band, in blocks. Inside it the cycle holds level; above it dives, below it climbs.")
        .defaultValue(8.0)
        .min(1.0)
        .sliderRange(2.0, 40.0)
        .decimalPlaces(1)
        .visible(() -> this.mode.get() == Mode.AUTOMATIC)
        .build()
    );

    private final Setting<Double> floor = this.sgCycle.add(new DoubleSetting.Builder()
        .name("minimum-altitude")
        .description("Altitude that always climbs, whatever the band says. This is what keeps a long dive from ending in the ground.")
        .defaultValue(40.0)
        .min(-64.0)
        .max(256.0)
        .sliderRange(0.0, 128.0)
        .decimalPlaces(0)
        .visible(() -> this.mode.get() == Mode.AUTOMATIC)
        .build()
    );

    private final Setting<Double> upPitch = this.sgCycle.add(new DoubleSetting.Builder()
        .name("up-pitch")
        .description("Pitch while climbing, in degrees. Negative looks up; -90 is straight up.")
        .defaultValue(-55.0)
        .min(-90.0)
        .max(0.0)
        .sliderRange(-90.0, -10.0)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Double> downPitch = this.sgCycle.add(new DoubleSetting.Builder()
        .name("down-pitch")
        .description("Pitch while diving. Positive looks down; the glide converts the fall along the look into speed.")
        .defaultValue(35.0)
        .min(0.0)
        .max(90.0)
        .sliderRange(5.0, 80.0)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Double> levelPitch = this.sgCycle.add(new DoubleSetting.Builder()
        .name("level-pitch")
        .description("Pitch while cruising. 0 puts the whole motion on the horizontal axes.")
        .defaultValue(0.0)
        .min(-40.0)
        .max(40.0)
        .sliderRange(-20.0, 20.0)
        .decimalPlaces(0)
        .build()
    );

    private final Setting<Boolean> launch = this.sgLaunch.add(new BoolSetting.Builder()
        .name("launch")
        .description("Fires a rocket while climbing, from the hotbar or from the inventory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cooldown = this.sgLaunch.add(new IntSetting.Builder()
        .name("launch-cooldown")
        .description("Minimum ms between two rockets.")
        .defaultValue(1000)
        .min(0)
        .sliderRange(0, 5000)
        .visible(this.launch::get)
        .build()
    );

    private final Setting<Boolean> gliding = this.sgControl.add(new BoolSetting.Builder()
        .name("only-gliding")
        .description("Does nothing until the player is gliding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> diagonal = this.sgControl.add(new BoolSetting.Builder()
        .name("diagonal-heading")
        .description("Snaps the yaw to the nearest 45 degree diagonal while the cycle runs. Grim checks each axis against its own 1.7, so splitting the motion over two axes allows a larger total speed than a cardinal heading does.")
        .defaultValue(false)
        .build()
    );

    private final RocketLauncher launcher = new RocketLauncher();

    private HeightBand.Phase phase = HeightBand.Phase.CRUISE;
    private double targetAltitude;
    private long lastLaunchMs;

    public RocketCycle() {
        super(StashOrganizerAddon.CATEGORY, "rocket-cycle",
            "Holds a height band or follows your keys, climbing on a rocket and diving for speed."
        );
    }

    @Override
    public void onActivate() {
        this.phase = HeightBand.Phase.CRUISE;
        this.lastLaunchMs = 0L;
        this.targetAltitude = this.mc.player != null && this.capture.get()
            ? this.mc.player.getY()
            : this.target.get();

        RocketBoost boost = Modules.get().get(RocketBoost.class);
        if (boost == null || !boost.isActive()) {
            this.info("rocket-boost is off: the cycle will climb on vanilla 1.5 and gain less");
        }
    }

    @Override
    public void onDeactivate() {
        this.phase = HeightBand.Phase.CRUISE;
    }

    /**
     * Chooses the phase and applies its angle for this tick.
     *
     * @param event pre-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Client.ready() || this.mc.player == null) return;
        if (this.gliding.get() && !this.mc.player.isGliding()) return;

        this.phase = this.mode.get() == Mode.MANUAL
            ? HeightBand.manual(
                this.mc.options.jumpKey.isPressed(),
                this.mc.options.sneakKey.isPressed()
            )
            : HeightBand.automatic(
                this.mc.player.getY(), this.targetAltitude, this.band.get(), this.floor.get()
            );

        // The angle is set before the rocket is used, because the rocket pushes along the look it is fired with.
        this.mc.player.setPitch((float) MathHelper.clamp(this.pitch(), -90.0, 90.0));

        if (this.diagonal.get()) {
            Player.rotate(diagonal(this.mc.player.getYaw()));
        }

        // The cooldown does the throttling: while the phase is a climb, keep rockets coming until it ends.
        if (this.phase == HeightBand.Phase.CLIMB && this.launch.get()) {
            this.fire();
        }
    }

    /**
     * Pitch of the current phase.
     *
     * @return degrees, positive looking down
     */
    private double pitch() {
        return switch (this.phase) {
            case CLIMB -> this.upPitch.get();
            case DIVE -> this.downPitch.get();
            case CRUISE -> this.levelPitch.get();
        };
    }

    /**
     * Fires a rocket when the cooldown allows it.
     */
    private void fire() {
        long now = System.currentTimeMillis();
        if (now - this.lastLaunchMs < this.cooldown.get()) return;
        if (!this.launcher.use()) return;

        this.lastLaunchMs = now;
    }

    /**
     * Moves a yaw onto the nearest diagonal.
     *
     * @param yaw current yaw
     * @return yaw at 45 degrees to both axes
     */
    private static float diagonal(float yaw) {
        return Math.round((yaw - 45.0F) / 90.0F) * 90.0F + 45.0F;
    }
}
