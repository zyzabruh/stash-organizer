package com.pro0101.stashorganizer.modules.hyperglide;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.huntbuddy.RocketBoost;
import com.pro0101.stashorganizer.modules.huntbuddy.recast.RocketLauncher;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Client;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Player;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;

/**
 * Runs one glide cycle: climb on a rocket, dive at the angle that turns height into speed, then hold level.
 *
 * <p>A firework pushed while looking up buys altitude and speed at the same time; pointing down afterwards converts
 * that altitude into horizontal speed, because the glide step redirects the fall along the look. Level flight then
 * keeps it. The module drives the rotation and fires the rocket, and leaves the multiplier to rocket-boost: with
 * auto-speed on it solves the per-axis bound for the look it is given, which is why the cycle changes the angle.
 */
public class RocketCycle extends Module {
    /** Where the cycle currently is. */
    private enum Phase {
        /** Looking up, rocket in the air. */
        CLIMB,
        /** Looking down, trading height for speed. */
        DIVE,
        /** Level, keeping what the dive gained. */
        CRUISE
    }

    private final SettingGroup sgCycle = this.settings.createGroup("Cycle");
    private final SettingGroup sgLaunch = this.settings.createGroup("Launch");
    private final SettingGroup sgControl = this.settings.createGroup("Control");

    private final Setting<Integer> climbTicks = this.sgCycle.add(new IntSetting.Builder()
        .name("climb-ticks")
        .description("Ticks spent looking up after the rocket is fired.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> diveTicks = this.sgCycle.add(new IntSetting.Builder()
        .name("dive-ticks")
        .description("Ticks spent looking down, converting the height the climb bought into speed.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Integer> cruiseTicks = this.sgCycle.add(new IntSetting.Builder()
        .name("cruise-ticks")
        .description("Ticks spent level before the next rocket. 0 skips the phase and dives straight into the next climb.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 100)
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
        .description("Fires a rocket at the start of every climb, from the hotbar or from the inventory.")
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

    private Phase phase = Phase.CLIMB;
    private int ticks;
    private long lastLaunchMs;

    public RocketCycle() {
        super(StashOrganizerAddon.CATEGORY, "rocket-cycle",
            "Climbs on a rocket, dives for speed, then holds level, on a loop."
        );
    }

    @Override
    public void onActivate() {
        this.phase = Phase.CLIMB;
        this.ticks = 0;
        this.lastLaunchMs = 0L;

        RocketBoost boost = Modules.get().get(RocketBoost.class);
        if (boost == null || !boost.isActive()) {
            this.info("rocket-boost is off: the cycle will climb on vanilla 1.5 and gain less");
        }
    }

    @Override
    public void onDeactivate() {
        this.phase = Phase.CLIMB;
        this.ticks = 0;
    }

    /**
     * Advances the cycle and applies its angle for this tick.
     *
     * @param event pre-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Client.ready() || this.mc.player == null) return;

        if (this.gliding.get() && !this.mc.player.isGliding()) {
            this.phase = Phase.CLIMB;
            this.ticks = 0;
            return;
        }

        // The angle is set before the rocket is used, because the rocket pushes along the look it is fired with.
        this.mc.player.setPitch((float) MathHelper.clamp(this.pitch(), -90.0, 90.0));

        if (this.diagonal.get()) {
            Player.rotate(diagonal(this.mc.player.getYaw()));
        }

        if (this.phase == Phase.CLIMB && this.ticks == 0 && this.launch.get()) {
            this.fire();
        }

        this.ticks++;

        if (this.ticks >= this.length(this.phase)) {
            this.phase = this.next(this.phase);
            this.ticks = 0;
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
     * Ticks the given phase lasts.
     *
     * @param phase phase to measure
     * @return duration in ticks
     */
    private int length(Phase phase) {
        return switch (phase) {
            case CLIMB -> this.climbTicks.get();
            case DIVE -> this.diveTicks.get();
            case CRUISE -> this.cruiseTicks.get();
        };
    }

    /**
     * Phase that follows the given one.
     *
     * @param phase current phase
     * @return next phase, skipping an empty cruise
     */
    private Phase next(Phase phase) {
        return switch (phase) {
            case CLIMB -> Phase.DIVE;
            case DIVE -> this.cruiseTicks.get() > 0 ? Phase.CRUISE : Phase.CLIMB;
            case CRUISE -> Phase.CLIMB;
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
