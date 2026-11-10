package com.pro0101.stashorganizer.modules.hyperglide;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Baritone;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Client;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Elytra;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Player;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

public class BounceFly extends Module {
    private static final double ticks = 20.0;
    private static final double range = 5.0;
    private static final double stop = 8.0;
    private static final int grid = 10;

    private static final int reach = 2;
    private static final int ahead = 8;
    private static final int sample = 16;
    private static final int span = 192;

    private static final int low = 119;
    private static final int high = 120;
    private static final int delay = 3;
    private static final int wait = 20;

    private static final int[] dxs = {0, -1, -1, -1, 0, 1, 1, 1};
    private static final int[] dzs = {1, 1, 0, -1, -1, -1, 0, 1};

    private static final double hazardWidth = 4.0;
    private static final double hazardHeight = 6.0;

    private static final Set<EntityType<?>> hazards = Set.of(
        EntityType.TNT, EntityType.END_CRYSTAL, EntityType.WARDEN,
        EntityType.WITHER, EntityType.FIREBALL, EntityType.SMALL_FIREBALL,
        EntityType.WITHER_SKULL, EntityType.SHULKER_BULLET
    );

    private final SettingGroup general = this.settings.getDefaultGroup();

    private final Setting<Boolean> acceleration = this.general.add(new BoolSetting.Builder()
        .name("acceleration")
        .description("Uses dynamic pitch to build up speed while bouncing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> pitch = this.general.add(new DoubleSetting.Builder()
        .name("standard-pitch")
        .description("Camera pitch used while bouncing in standard mode.")
        .defaultValue(72.4)
        .min(0.0)
        .sliderMax(90.0)
        .decimalPlaces(2)
        .visible(() -> !this.acceleration.get())
        .build()
    );

    private final Setting<Double> threshold = this.general.add(new DoubleSetting.Builder()
        .name("fall-threshold")
        .description("Downward velocity used to switch acceleration pitch.")
        .defaultValue(0.193)
        .min(0.0)
        .sliderMax(1.0)
        .decimalPlaces(3)
        .visible(this.acceleration::get)
        .build()
    );

    private final Setting<Boolean> passer = this.general.add(new BoolSetting.Builder()
        .name("obstacle-passer")
        .description("Uses Baritone to pass through detected obstacles.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> avoid = this.general.add(new BoolSetting.Builder()
        .name("avoid-collisions")
        .description("Uses raycasts to detect obstacles along the highway.")
        .defaultValue(true)
        .visible(this.passer::get)
        .build()
    );

    private final Setting<Boolean> digger = this.general.add(new BoolSetting.Builder()
        .name("mine-obstacles")
        .description("Clears obstacles on the path before resuming travel.")
        .defaultValue(false)
        .visible(() -> this.passer.get() && this.avoid.get())
        .build()
    );

    private final Setting<Integer> collision = this.general.add(new IntSetting.Builder()
        .name("collision-ticks")
        .description("How many movement ticks ahead to scan for obstacles.")
        .defaultValue(8)
        .min(5)
        .sliderMax(10)
        .visible(() -> this.passer.get() && this.avoid.get())
        .build()
    );

    private final Setting<Integer> passTimeout = this.general.add(new IntSetting.Builder()
        .name("pass-timeout")
        .description("Ticks to keep bouncing paused after asking Baritone for a path. Baritone reports nothing while it calculates, and treating that silence as finished is what resumed the bounce on top of the walk.")
        .defaultValue(100)
        .min(20)
        .sliderRange(20, 200)
        .visible(() -> this.passer.get())
        .build()
    );

    private final Setting<Integer> setbackPause = this.general.add(new IntSetting.Builder()
        .name("pause-after-setback")
        .description("Ticks bouncing stays off once the server moves you back. The inputs are released and the client-side glide is dropped either way; pressing the same thing again on the tick a setback lands is what turns one rubber band into two.")
        .defaultValue(1)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> recover = this.general.add(new BoolSetting.Builder()
        .name("recover-glide-clear")
        .description("Presses jump again on the tick the server clears the glide mid-hop, instead of waiting for the toggle sequence to line up and losing a hop.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopNear = this.general.add(new BoolSetting.Builder()
        .name("stop-near-entities")
        .description("Stops the module when a dangerous entity is ahead on the flight path. Bouncing at highway speed cannot outrun a crystal or a warden.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> hazardRange = this.general.add(new DoubleSetting.Builder()
        .name("hazard-range")
        .description("How far ahead a dangerous entity stops the module.")
        .defaultValue(24.0)
        .min(4.0)
        .sliderMax(64.0)
        .visible(this.stopNear::get)
        .build()
    );

    private final Setting<Boolean> freelook = this.general.add(new BoolSetting.Builder()
        .name("freelook")
        .description("Keeps the camera on its own rotation while the module steers the player. Meteor's freecam cannot do this here: it derives its angle from the player rotation, which this module rewrites every tick.")
        .defaultValue(false)
        .build()
    );

    private final Deque<BlockPos> blocks = new ArrayDeque<>();

    private MiningTweaks mining;
    private BlockPos focus;
    private BlockPos goal;

    private int px, pz;
    private int dx, dz;
    private double nx, nz;
    private double yaw;

    private int slow;
    private int jump;
    private int level = low;

    private boolean pass;
    private int passWait;
    private boolean launch;
    private boolean enabled;
    private boolean started;

    private boolean wasGliding;
    private boolean force;
    private int pause;

    private float viewYaw;
    private float viewPitch;
    private boolean viewActive;

    public BounceFly() {
        super(StashOrganizerAddon.CATEGORY, "bounce-fly",
            "Enables elytra bouncing for fast highway travel."
        );
    }

    /**
     * Prepares flight state and required modules.
     */
    @Override
    public void onActivate() {
        if (!Client.ready()) return;

        this.face();
        this.center();

        this.viewYaw = this.mc.player.getYaw();
        this.viewPitch = this.mc.player.getPitch();
        this.viewActive = false;

        this.level = this.level();
        this.reset();

        this.mining = Modules.get().get(MiningTweaks.class);

        if (this.mining != null) {
            this.enabled = this.mining.isActive();
            if (!this.enabled) this.mining.toggle();
        }

        this.clear();
        this.pass = false;
        this.started = false;

        this.wasGliding = false;
        this.force = false;
        this.pause = 0;
    }

    /**
     * Stops active movement and restores module state.
     */
    @Override
    public void onDeactivate() {
        this.release();

        if (this.pass) Baritone.stop();

        this.restore();
        this.reset();
        this.clear();

        this.mining = null;
        this.pass = false;
        this.enabled = false;
        this.started = false;
        this.viewActive = false;

        this.wasGliding = false;
        this.force = false;
        this.pause = 0;
    }

    //region Event handlers

    /**
     * Controls normal bounce movement and obstacle handling.
     *
     * @param event pre-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Client.ready()) return;

        this.camera(this.freelook.get());

        if (!this.available()) return;

        // A setback is the server rejecting the last movement. Pressing the same inputs again on
        // the tick it lands is what turns one rubber band into two, so they stay released until
        // the client has been put back where the server put it.
        if (this.pause > 0) {
            this.pause--;
            this.release();
            this.reset();
            return;
        }

        if (this.hazard()) return;

        if (!Baritone.pathing()) this.rotate();
        Vec3d vel = this.mc.player.getVelocity();

        float pitch = !this.acceleration.get()
            ? this.pitch.get().floatValue()
            : vel.y > -this.threshold.get()
            ? 90.0F : 0.0F;

        this.mc.player.setPitch(pitch);

        this.launch(vel);
        this.mc.player.setSprinting(true);

        if (!this.takeoff()) return;
        if (this.blocked()) return;

        this.stuck();
    }

    /**
     * Notes a server correction so the bounce can answer it on the next tick.
     *
     * <p>Meteor fires the receive event on the netty thread, so nothing is touched here: the
     * state change is handed to the main thread.
     *
     * @param event packet receive event
     */
    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!this.isActive()) return;
        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;

        this.mc.execute(this::setback);
    }

    //endregion

    //region State management

    /**
     * Checks whether bounce input should be forced.
     *
     * @return true while normal bounce movement is active
     */
    public boolean enabled() {
        return this.isActive() && this.mc.player != null
            && !this.pass && Elytra.equipped();
    }

    /**
     * Returns the current bounce jump input.
     *
     * @return forced jump state
     */
    public boolean jumping() {
        // A stale launch flag used to survive a handover to Baritone, and the key mixin kept answering the
        // jump as pressed while the player was walking. The input is only ours while the module drives movement.
        return this.launch && this.enabled();
    }

    /**
     * Checks whether acceleration mode is enabled.
     *
     * @return true when acceleration mode is enabled
     */
    public boolean accelerated() {
        return this.acceleration.get();
    }

    /**
     * Clears bounce movement state.
     */
    private void reset() {
        this.slow = 0;
        this.jump = 0;
        this.launch = false;
        this.force = false;
    }

    /**
     * Clears obstacle passing state.
     */
    private void clear() {
        this.blocks.clear();
        this.goal = null;
        this.focus = null;
        this.passWait = 0;
    }

    /**
     * Keeps Mining Tweaks enabled while mining obstacles.
     */
    private void setup() {
        if (this.mining != null && !this.mining.isActive()) {
            this.mining.toggle();
        }
    }

    /**
     * Restores the Mining Tweaks state from before activation.
     */
    private void restore() {
        if (this.mining != null && !this.enabled &&
            this.mining.isActive()) {
            this.mining.toggle();
        }
    }

    //endregion

    //region Bounce control

    /**
     * Checks whether bounce movement is available.
     *
     * @return true when Bounce Fly can control movement
     */
    private boolean available() {
        return !this.pathing() && this.equipped();
    }

    /**
     * Validates equipped elytra and clears bounce state.
     *
     * @return true when an elytra is equipped
     */
    private boolean equipped() {
        if (Elytra.equipped()) {
            return true;
        }

        this.release();
        this.reset();

        this.started = false;
        return false;
    }

    /**
     * Updates jump input for the current bounce state.
     * 
     * @param velocity current player velocity
     */
    private void launch(Vec3d velocity) {
        boolean gliding = this.mc.player.isGliding();

        // The server can clear the glide on its own schedule, usually mid-hop and with nothing in
        // the client asking for it. The previous toggle is a tick out of phase by then, so the
        // press is made deliberately instead of waiting for the sequence to line up again.
        if (this.recover.get() && this.wasGliding && !gliding
            && !this.mc.player.isOnGround()) {
            this.force = true;
            this.jump = 0;
        }

        this.wasGliding = gliding;

        if (this.mc.player.isOnGround()) {
            this.launch = this.started ||
                this.mc.player.isSprinting() &&
                velocity.horizontalLength() > 0.05;
            this.jump = 0;
            this.force = false;
            return;
        }

        if (gliding) {
            this.launch = false;
            this.jump = 0;
            return;
        }

        if (this.force) {
            this.force = false;
            this.launch = true;
            return;
        }

        if (!this.started && ++this.jump < delay) {
            this.launch = false;
            return;
        }

        this.launch = !this.launch;
    }

    /**
     * Waits for the first elytra flight before bouncing begins.
     *
     * @return true after the initial flight has started
     */
    private boolean takeoff() {
        if (this.started) return true;

        this.slow = 0;
        if (!this.mc.player.isGliding()) {
            return false;
        }

        this.started = true;
        return true;
    }

    /**
     * Releases forced movement and sprint state.
     */
    private void release() {
        this.mc.options.forwardKey.setPressed(false);
        this.mc.options.jumpKey.setPressed(false);

        if (this.mc.player != null) {
            this.mc.player.setSprinting(false);
        }
    }

    /**
     * Drops the bounce state after the server moved the player back.
     *
     * <p>The client is left holding a glide the server no longer agrees with, so it is dropped
     * here and the inputs stay released for {@code pause-after-setback} ticks. The first takeoff
     * after the pause starts the sequence over rather than resuming a stale one.
     */
    private void setback() {
        if (!Client.ready()) return;

        this.release();
        this.reset();

        this.wasGliding = false;
        this.started = false;
        this.pause = this.setbackPause.get();

        this.mc.player.stopGliding();
    }

    //endregion

    //region Travel control

    /**
     * Manages active obstacle passing and mining.
     *
     * @return true while obstacle handling is active
     */
    private boolean pathing() {
        if (!this.passer.get()) {
            if (this.pass) {
                Baritone.stop();
                this.reset();
                this.clear();
                this.pass = false;
            }
            return false;
        }

        if (this.goal != null) {
            this.release();
            this.reset();
            this.mine();
            return true;
        }

        if (this.pass && Baritone.pathing()) {
            this.release();
            this.reset();
            this.passWait = 0;
            return true;
        }

        // Baritone computes its path off the main thread: for a moment after walk() it reports neither a
        // custom goal nor pathing, which is not the same as having finished. Keep the movement released for
        // that window, otherwise the bounce resumes underneath the walk.
        if (this.pass && this.passWait++ < this.passTimeout.get()) {
            this.release();
            this.reset();
            return true;
        }

        if (this.pass) {
            this.rotate();
            this.reset();
            this.clear();

            this.pass = false;
            this.started = true;
        }

        return false;
    }

    /**
     * Starts obstacle handling when a collision is detected.
     *
     * @return true when obstacle handling was started
     */
    private boolean blocked() {
        if (!this.passer.get() || !this.avoid.get()) {
            return false;
        }

        BlockPos hit = this.collision();
        if (hit == null) return false;

        this.mc.player.stopGliding();

        this.level = this.level();
        BlockPos goal = this.trace(hit);

        if (this.mining != null && this.digger.get() &&
            !this.blocks.isEmpty()) {
            this.mine(goal);
        } else {
            this.path(goal);
        }

        return true;
    }

    /**
     * Detects slow movement and starts recovery pathing.
     */
    private void stuck() {
        Vec3d motion = this.mc.player.getVelocity();
        double speed = motion.horizontalLength() * ticks;

        if (!this.passer.get() || speed >= stop) {
            this.slow = 0;
            return;
        }

        if (++this.slow >= wait) {
            this.slow = 0;
            this.path();
        }
    }

    /**
     * Stops the bounce when something dangerous is ahead on the flight path.
     *
     * <p>Only entities in front of the player and inside a narrow box around the heading count: a
     * bounce covers blocks in ticks and cannot outrun a crystal or a warden, so the module hands
     * control back instead of flying into one.
     *
     * @return true when the module was stopped
     */
    private boolean hazard() {
        if (!this.stopNear.get()) return false;

        Vec3d origin = this.mc.player.getEntityPos();
        Vec3d heading = new Vec3d(this.nx, 0.0, this.nz);

        double range = this.hazardRange.get();
        Entity nearest = null;

        for (Entity entity : this.mc.world.getEntities()) {
            if (!hazards.contains(entity.getType())) continue;

            Vec3d offset = entity.getEntityPos().subtract(origin);
            double ahead = offset.x * heading.x + offset.z * heading.z;
            double lateral = Math.abs(
                offset.x * heading.z - offset.z * heading.x
            );

            if (ahead <= 0.0 || ahead > range) continue;
            if (lateral > hazardWidth) continue;
            if (Math.abs(offset.y) > hazardHeight) continue;

            nearest = entity;
            range = ahead;
        }

        if (nearest == null) return false;

        this.release();
        this.reset();

        this.mc.player.stopGliding();

        this.warning(
            "Approaching %s, stopping.",
            nearest.getType().getName().getString()
        );

        this.toggle();
        return true;
    }

    //endregion

    //region Obstacle pathing

    /**
     * Starts recovery toward a safe highway position.
     */
    private void path() {
        this.level = this.level();
        BlockPos goal = this.trace();

        if (this.mining != null && this.avoid.get() &&
            this.digger.get() && !this.blocks.isEmpty()) {
            this.mine(goal);
        } else {
            this.path(goal);
        }
    }

    /**
     * Starts Baritone pathing beyond the selected goal.
     *
     * @param pos pathing goal
     */
    private void path(BlockPos pos) {
        this.level = this.level();

        this.release();
        this.reset();
        this.clear();

        this.pass = true;
        this.passWait = 0;
        this.started = false;
        this.wasGliding = false;

        int px = this.dx * reach;
        int pz = this.dz * reach;

        pos = this.block(pos.getX(), pos.getZ());
        pos = this.safe(pos.add(px, 0, pz));

        Baritone.walk(pos);
    }

    /**
     * Starts obstacle mining before pathing onward.
     *
     * @param goal safe pathing goal after the obstacle
     */
    private void mine(BlockPos goal) {
        this.release();
        this.reset();
        this.setup();

        this.pass = true;
        this.passWait = 0;
        this.goal = goal;
        this.focus = null;
        this.started = false;

        this.mine();
    }

    /**
     * Queues obstacles for mining while approaching them.
     */
    private void mine() {
        this.blocks.removeIf(pos -> !this.solid(pos));

        if (this.blocks.isEmpty()) {
            BlockPos goal = this.goal;
            this.goal = null;
            this.focus = null;

            if (goal != null) this.path(goal);
            return;
        }

        BlockPos pos = this.blocks.peekFirst();
        if (!pos.equals(this.focus)) {
            this.focus = pos;
            Baritone.near(pos, 2);
        }

        this.setup();

        for (BlockPos block : this.blocks) {
            if (this.mining.reachable(block, range)) {
                this.mining.mine(block, Direction.UP);
            }
        }
    }

    //endregion

    //region Obstacle scanning

    /**
     * Keeps a Baritone goal inside terrain the client already has.
     *
     * <p>The recovery goal used to be projected further along the highway whether or not those
     * chunks were loaded. Baritone's pathfinder is native and answers a chunk missing in front of
     * it with a segfault rather than an exception (cabaletta/baritone#4830), so the goal now stops
     * at the last loaded, standable, non-portal block between the player and the request.
     *
     * @param target requested goal
     * @return furthest safe goal before the request, or the last loaded block when none is safe
     */
    private BlockPos safe(BlockPos target) {
        BlockPos start = this.base();

        int sx = Integer.signum(target.getX() - start.getX());
        int sz = Integer.signum(target.getZ() - start.getZ());

        if (sx == 0 && sz == 0) return target;

        BlockPos last = null;
        BlockPos loaded = start;
        BlockPos pos = start;

        for (int idx = 0; idx < span; idx++) {
            pos = pos.add(sx, 0, sz);

            if (!this.loaded(pos)) break;

            loaded = pos;
            if (this.standable(pos)) last = pos;
            if (pos.equals(target)) break;
        }

        return last != null ? last : loaded;
    }

    /**
     * Checks whether the client has the chunk a block belongs to.
     *
     * @param pos block position to check
     * @return true when the chunk is loaded
     */
    private boolean loaded(BlockPos pos) {
        return this.mc.world.getChunkManager().isChunkLoaded(
            pos.getX() >> 4, pos.getZ() >> 4
        );
    }

    /**
     * Checks whether Baritone may stand on a block.
     *
     * @param pos block position to check
     * @return true when the block is clear, supported and not a portal
     */
    private boolean standable(BlockPos pos) {
        BlockState state = this.mc.world.getBlockState(pos);

        if (state.isOf(Blocks.NETHER_PORTAL)) return false;
        if (state.isOf(Blocks.VOID_AIR)) return false;

        return this.clear(pos);
    }

    /**
     * Finds a safe center position for recovery pathing.
     *
     * @return recovery pathing goal
     */
    private BlockPos trace() {
        this.blocks.clear();

        BlockPos start = this.base();
        BlockPos pos = start, goal;

        for (int idx = 0; idx < span; idx++) {
            goal = pos.add(this.dx * reach, 0, this.dz * reach);

            boolean mine = this.mining != null && this.digger.get();
            if (mine && this.avoid.get()) this.step(goal);

            if (!this.clear(goal)) {
                pos = pos.add(this.dx, 0, this.dz);
            } else {
                return pos;
            }
        }

        return start.add(this.dx * span, 0, this.dz * span);
    }

    /**
     * Finds a safe pathing goal beyond the detected obstacle.
     *
     * @param hit detected collision block
     * @return pathing goal
     */
    private BlockPos trace(BlockPos hit) {
        this.blocks.clear();

        BlockPos start = this.base();
        BlockPos point = this.point(hit.getX(), hit.getZ());

        int distance = Math.max(
            Math.abs(point.getX() - start.getX()),
            Math.abs(point.getZ() - start.getZ())
        );

        BlockPos pos = start;
        BlockPos last = null;
        int clear = 0;

        for (int idx = 0; idx < span; idx++) {
            if (this.step(pos)) {
                last = pos;
                clear = 0;
            } else if (
                last != null && ++clear >= ahead ||
                last == null && idx >= distance) {
                return last != null ? last : pos;
            }
            pos = pos.add(this.dx, 0, this.dz);
        }

        return start.add(this.dx * span, 0, this.dz * span);
    }

    /**
     * Checks the player-sized space for one trail step.
     *
     * @param pos center block of the trail step
     * @return true when the step is obstructed
     */
    private boolean step(BlockPos pos) {
        boolean blocked = this.column(pos);
        if (this.digger.get()) this.collect(pos);

        if (this.dx != 0 && this.dz != 0) {
            BlockPos px = pos.add(this.dx, 0, 0);
            BlockPos pz = pos.add(0, 0, this.dz);

            blocked |= this.column(px);
            blocked |= this.column(pz);

            if (this.digger.get()) {
                this.collect(px);
                this.collect(pz);
            }
        }

        return blocked;
    }

    /**
     * Saves the breakable blocks from a trail column.
     *
     * @param pos lower block of the column
     */
    private void collect(BlockPos pos) {
        for (int py = 0; py <= 2; py++) {
            BlockPos block = pos.up(py);
            if (!this.solid(block) || this.blocks.contains(block)) {
                continue;
            }

            BlockState state = this.mc.world.getBlockState(block);
            if (state.getHardness(this.mc.world, block) < 0) {
                continue;
            }

            this.blocks.addLast(block);
        }
    }

    //endregion

    //region Collision detection

    /**
     * Finds the closest obstacle along the highway direction.
     *
     * @return closest collision block, or null when undetected
     */
    private BlockPos collision() {
        Vec3d front = new Vec3d(this.nx, 0, this.nz);
        Vec3d side = new Vec3d(-front.z, 0, front.x);
        Vec3d vel = this.mc.player.getVelocity();

        double px = this.mc.player.getX();
        double py = Math.floor(this.mc.player.getY());
        double pz = this.mc.player.getZ();

        double scan = vel.horizontalLength();
        scan = Math.max(1.0, scan * this.collision.get());

        double width = this.mc.player.getWidth() / 2.0;
        width *= Math.abs(side.x) + Math.abs(side.z);

        BlockPos closest = null;
        double distance = Double.MAX_VALUE;

        for (int idx = -1; idx <= 1; idx++) {
            for (double off = 0.5; off <= 1.5; off += 1.0) {
                Vec3d start = new Vec3d(px, py + off, pz);

                start = start.add(side.multiply(width * idx));
                Vec3d end = start.add(front.multiply(scan));

                BlockHitResult hit = this.ray(start, end);
                if (hit.getType() != HitResult.Type.BLOCK) {
                    continue;
                }

                Vec3d center = hit.getBlockPos().toCenterPos();
                double current = start.squaredDistanceTo(center);

                if (current < distance) {
                    closest = hit.getBlockPos();
                    distance = current;
                }
            }
        }

        return closest;
    }

    /**
     * Raycasts between two points using block collision shapes.
     *
     * @param start raycast start position
     * @param end raycast end position
     * @return block raycast result
     */
    private BlockHitResult ray(Vec3d start, Vec3d end) {
        return this.mc.world.raycast(new RaycastContext(
            start, end, RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE, this.mc.player
        ));
    }

    //endregion

    //region Direction control

    /**
     * Stores the highway direction from the player's yaw.
     */
    private void face() {
        float yaw = this.mc.player.getYaw();
        float sector = (yaw + 22.5F) / 45.0F;

        int face = MathHelper.floor(sector) & 7;

        this.dx = dxs[face];
        this.dz = dzs[face];

        double len = Math.hypot(this.dx, this.dz);

        this.yaw = face > 4 ? face - 8 : face;
        this.yaw *= 45.0;

        this.nx = this.dx / len;
        this.nz = this.dz / len;
    }

    /**
     * Stores the snapped center line of the current highway.
     */
    private void center() {
        double px = this.mc.player.getX();
        double pz = this.mc.player.getZ();

        if (this.dx == 0) {
            this.px = this.snap(px);
            this.pz = 0;
        } else if (this.dz == 0) {
            this.px = 0;
            this.pz = this.snap(pz);
        } else {
            boolean equal = this.dx == this.dz;
            this.px = this.snap(equal ? px - pz : px + pz);
            this.pz = 0;
        }
    }

    /**
     * Rotates the player toward the stored highway direction.
     */
    private void rotate() {
        Player.rotate((float) this.yaw);
    }

    /**
     * Enables or disables the independent camera rotation.
     *
     * <p>The first enabled tick copies the player angle, so turning the setting on does not snap the view; after that
     * the camera keeps whatever the mouse gives it.
     *
     * @param enabled whether the camera may look away from the flight direction
     */
    private void camera(boolean enabled) {
        if (!enabled) {
            this.viewActive = false;
            return;
        }

        if (this.viewActive) return;

        this.viewYaw = this.mc.player.getYaw();
        this.viewPitch = this.mc.player.getPitch();
        this.viewActive = true;
    }

    /**
     * Checks whether mouse look belongs to the camera instead of the player.
     *
     * @return true when look input must be routed to {@link #look(double, double)}
     */
    public boolean view() {
        return this.isActive() && this.mc.player != null && this.freelook.get();
    }

    /**
     * Checks whether the custom camera rotation should be applied.
     *
     * @return true when the camera owns its rotation
     */
    public boolean camera() {
        return this.isActive() && this.mc.player != null && this.viewActive;
    }

    /**
     * Returns the independent camera yaw.
     *
     * @return camera yaw
     */
    public float yaw() {
        return this.viewActive ? this.viewYaw : this.mc.player.getYaw();
    }

    /**
     * Returns the independent camera pitch.
     *
     * @return camera pitch
     */
    public float pitch() {
        return this.viewActive ? this.viewPitch : this.mc.player.getPitch();
    }

    /**
     * Applies mouse movement to the independent camera rotation.
     *
     * @param mx horizontal mouse movement
     * @param my vertical mouse movement
     */
    public void look(double mx, double my) {
        this.viewYaw += (float) (mx * 0.15);
        this.viewPitch = MathHelper.clamp(
            this.viewPitch + (float) (my * 0.15), -90.0F, 90.0F
        );
    }

    /**
     * Finds the current block on the stored highway line.
     *
     * @return current center block
     */
    private BlockPos base() {
        double px = this.mc.player.getX();
        double pz = this.mc.player.getZ();
        return this.point(px, pz);
    }

    /**
     * Projects a position onto the stored highway line.
     *
     * @param px world X coordinate
     * @param pz world Z coordinate
     * @return projected center block
     */
    private BlockPos point(double px, double pz) {
        return this.dx == 0 || this.dz == 0 ?
            this.cardinal(px, pz) : this.diagonal(px, pz);
    }

    /**
     * Projects a position onto a cardinal highway line.
     *
     * @param px world X coordinate
     * @param pz world Z coordinate
     * @return projected center block
     */
    private BlockPos cardinal(double px, double pz) {
        if (this.dx == 0) return this.block(this.px, pz);
        return this.block(px, this.pz);
    }

    /**
     * Projects a position onto the diagonal highway line.
     *
     * @param px world X coordinate
     * @param pz world Z coordinate
     * @return projected center block
     */
    private BlockPos diagonal(double px, double pz) {
        boolean equal = this.dx == this.dz;
        double axis = equal ? px + pz : px - pz;

        double bx = axis + this.px;
        double bz = equal ? axis - this.px : this.px - axis;

        return this.block(bx / 2.0, bz / 2.0);
    }

    //endregion

    //region Height correction

    /**
     * Finds the most likely highway level being followed.
     *
     * @return detected highway level
     */
    private int level() {
        return this.trail(high) >= this.trail(low) ? high : low;
    }

    /**
     * Scores clear positions along the highway center line.
     *
     * @param level highway level to check
     * @return number of valid center positions
     */
    private int trail(int level) {
        int score = 0;

        BlockPos pos = this.base();
        pos = this.block(pos.getX(), level, pos.getZ());

        for (int idx = 0; idx < sample; idx++) {
            score += this.clear(pos) ? 1 : 0;
            pos = pos.add(this.dx, 0, this.dz);
        }

        return score;
    }

    //endregion

    //region Utilities and validation

    /**
     * Creates a block position on the highway level.
     *
     * @param px world X coordinate
     * @param pz world Z coordinate
     * @return block position on the highway level
     */
    private BlockPos block(double px, double pz) {
        return this.block(px, this.level, pz);
    }

    /**
     * Creates a block position at a specific level.
     *
     * @param px world X coordinate
     * @param py world Y coordinate
     * @param pz world Z coordinate
     * @return block position
     */
    private BlockPos block(double px, int py, double pz) {
        return new BlockPos(
            (int) Math.round(px), py, (int) Math.round(pz)
        );
    }

    /**
     * Checks whether a trail position is clear and supported.
     *
     * @param pos trail position
     * @return true when the position is valid
     */
    private boolean clear(BlockPos pos) {
        return !this.column(pos) && this.solid(pos.down());
    }

    /**
     * Checks a three-block-high trail column for collision.
     *
     * @param pos lower block of the column
     * @return true when any block can collide
     */
    private boolean column(BlockPos pos) {
        if (this.solid(pos)) return true;
        return this.solid(pos.up()) || this.solid(pos.up(2));
    }

    /**
     * Checks whether a block can collide with the player.
     *
     * @param pos block position to check
     * @return true when the block is solid and collidable
     */
    private boolean solid(BlockPos pos) {
        BlockState state = this.mc.world.getBlockState(pos);
        return !state.getCollisionShape(this.mc.world, pos).isEmpty();
    }

    /**
     * Rounds a coordinate to the nearest highway grid position.
     *
     * @param value coordinate to round
     * @return coordinate aligned to the highway grid
     */
    private int snap(double value) {
        return (int) Math.round(value / grid) * grid;
    }

    //endregion
}
