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
 * keeps it. The phases are driven by the vertical speed it produces - the climb ends when the rocket has actually
 * lifted the player, the dive when the fall has been given back - so the tick counts only guard against a stall.
 * The module drives the rotation and fires the rocket, and leaves the multiplier to rocket-boost: with auto-speed on
 * it solves the per-axis bound for the look it is given, which is why the cycle changes the angle.
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

    private final Setting<Double> climbRate = this.sgCycle.add(new DoubleSetting.Builder()
        .name("climb-rate")
        .description("Vertical speed, in blocks per tick, that ends the climb. Ten ticks at a shallow pitch buy far less height than ten at a steep one, so the speed decides and the tick count only guards the phase.")
        .defaultValue(0.35)
        .min(0.05)
        .sliderRange(0.1, 1.0)
        .decimalPlaces(2)
        .build()
    );

    private final Setting<Integer> minClimbTicks = this.sgCycle.add(new IntSetting.Builder()
        .name("min-climb-ticks")
        .description("Ticks to stay in the climb even when the speed target is already reached.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> climbTimeout = this.sgCycle.add(new IntSetting.Builder()
        .name("climb-timeout")
        .description("Ticks after which the climb ends anyway, so a rocket that never lifts the player cannot stall the cycle.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Double> diveRelease = this.sgCycle.add(new DoubleSetting.Builder()
        .name("dive-release")
        .description("Fall rate, as a positive number, at which the dive ends: the player is no longer dropping faster than this, so the height has been spent.")
        .defaultValue(0.2)
        .min(0.0)
        .sliderRange(0.0, 1.0)
        .decimalPlaces(2)
        .build()
    );

    private final Setting<Integer> minDiveTicks = this.sgCycle.add(new IntSetting.Builder()
        .name("min-dive-ticks")
        .description("Ticks to stay in the dive even when the fall has already slowed.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> diveTimeout = this.sgCycle.add(new IntSetting.Builder()
        .name("dive-timeout")
        .description("Ticks after which the dive ends anyway.")
        .defaultValue(40)
        .min(1)
        .sliderRange(1, 80)
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

        if (this.done()) {
            this.phase = this.next(this.phase);
            this.ticks = 0;
        }
    }

    /**
     * Whether the current phase is over.
     *
     * <p>Climb and dive end on the vertical speed, with the tick count as the fallback; the cruise is the only phase
     * that is still purely about time, because it has nothing to reach - it just holds what the dive gained.
     *
     * @return true when the cycle should advance
     */
    private boolean done() {
        double vertical = this.mc.player.getVelocity().y;

        return switch (this.phase) {
            case CLIMB -> RocketCycleLogic.climbDone(
                vertical, this.ticks, this.minClimbTicks.get(), this.climbTimeout.get(), this.climbRate.get()
            );
            case DIVE -> RocketCycleLogic.diveDone(
                vertical, this.ticks, this.minDiveTicks.get(), this.diveTimeout.get(), this.diveRelease.get()
            );
            case CRUISE -> this.ticks >= this.cruiseTicks.get();
        };
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
