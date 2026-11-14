package com.pro0101.stashorganizer.modules.hyperglide;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.huntbuddy.util.GlideClearHold;
import com.pro0101.stashorganizer.modules.huntbuddy.util.GliderSwap;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Baritone;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Client;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Elytra;
import com.pro0101.stashorganizer.modules.hyperglide.utilities.Player;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
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
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    private final Setting<Integer> stuckTicks = this.general.add(new IntSetting.Builder()
        .name("stuck-ticks")
        .description("Ticks of slow flight before the bounce hands over to Baritone. Lower values switch faster; the walk itself keeps the counter stopped, so a request cannot repeat until the previous pass ends.")
        .defaultValue(10)
        .min(5)
        .sliderRange(5, 40)
        .visible(this.passer::get)
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

    private final Setting<Boolean> holdClear = this.general.add(new BoolSetting.Builder()
        .name("hold-glide-clear")
        .description("Answers the server's pings itself and holds a glide clear until the first tick the re-deploy is free, instead of letting vanilla apply it the moment it arrives. It hides a server decision from the server: on a server that logs metadata, it is visible.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> clearCap = this.general.add(new IntSetting.Builder()
        .name("clear-hold-cap")
        .description("Ticks a held clear waits for a free re-deploy tick before being applied anyway, so a clear behind an obstacle pass or a stuck bounce cannot sit in a frozen transaction indefinitely.")
        .defaultValue(10)
        .min(2)
        .sliderMax(20)
        .visible(this.holdClear::get)
        .build()
    );

    private final Setting<Boolean> fakeLag = this.general.add(new BoolSetting.Builder()
        .name("fake-lag")
        .description("Holds outgoing movement while skimming the floor and releases it in bursts. The server sees one position every few ticks instead of one per tick; the client keeps moving at full speed meanwhile. Off by default: it is a desync by design.")
        .defaultValue(false)
        .onChanged(value -> {
            if (!value) this.flush();
        })
        .build()
    );

    private final Setting<Integer> lagFlush = this.general.add(new IntSetting.Builder()
        .name("fake-lag-flush-ticks")
        .description("How many ticks outgoing movement is held before it is released in a burst.")
        .defaultValue(10)
        .min(2)
        .sliderRange(2, 40)
        .visible(this.fakeLag::get)
        .build()
    );

    private final Setting<Double> lagHeight = this.general.add(new DoubleSetting.Builder()
        .name("fake-lag-height")
        .description("How close to the floor the player has to be gliding for the hold to apply. The burst only matters while skimming, which is where a bounce spends its hop.")
        .defaultValue(0.163)
        .min(0.05)
        .sliderMax(4.0)
        .decimalPlaces(3)
        .visible(this.fakeLag::get)
        .build()
    );

    private final Setting<Boolean> fakeFly = this.general.add(new BoolSetting.Builder()
        .name("chestplate-fakefly")
        .description("Bounces with a chestplate worn and the elytra in a hand: each re-deploy swaps the elytra in, starts the glide and swaps the chestplate straight back inside the same tick, so the elytra is never worn across a landing and takes no durability. Needs the pair, one worn and the other in either hand. Off by default: the swap is a desync the server can log.")
        .defaultValue(false)
        .onChanged(value -> {
            if (!value) this.cleanFakeFly();
        })
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
    private boolean passStarted;
    private int passWait;
    private boolean launch;
    private boolean enabled;
    private boolean started;

    private boolean wasGliding;
    private boolean force;

    private final Queue<Packet<?>> queue = new ConcurrentLinkedQueue<>();
    private boolean flushing;
    private int lagTicks;
    private double groundY;
    private int pressAge;
    private boolean held;

    private Hand fakeHand;
    private int fakePending = -1;
    private long fakeAcked = -1L;
    private int fakeSent = -1;
    private long fakeSentAge = Long.MIN_VALUE / 2;
    private int fakeServer = -1;
    private boolean fakeDesync;
    private boolean fakeArmed;
    private boolean fakeWant;
    private String fakeBlocked;
    private boolean fakePair = true;
    private boolean fakeLastGround = true;
    private boolean fakePrevGround = true;
    private long fakeStartAge = Long.MIN_VALUE / 2;

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

        this.pressAge = 0;
        this.held = false;
        this.lagTicks = 0;
        this.groundY = this.mc.player.getY();

        this.cleanFakeFly();
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

        this.pressAge = 0;
        this.held = false;
        this.lagTicks = 0;

        GlideClearHold.release(this);
        this.cleanFakeFly();
        this.flush();
    }


    /**
     * Controls normal bounce movement and obstacle handling.
     *
     * @param event pre-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Client.ready()) return;

        this.camera(this.freelook.get());

        this.serviceClear();
        this.serviceLag();
        this.serviceFakeFly();

        if (!this.available()) return;

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

        if (this.launch && !this.held) {
            this.pressAge = this.mc.player.age;
        }

        this.held = this.launch;

        if (!this.takeoff()) return;
        if (this.blocked()) return;

        this.stuck();
    }

    /**
     * Handles the packets that affect a running bounce.
     *
     * <p>A position correction only drops a held glide-clear and clears the stuck counter. The
     * bounce itself is left alone, so the next tick presses its inputs again the way Hyperglide
     * does instead of releasing them or dropping the glide. Meteor fires the receive event on the
     * netty thread, so the counter is changed on the main thread.
     *
     * @param event packet receive event
     */
    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!this.isActive()) return;

        this.intakeFakeFly(event);

        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            GlideClearHold.release(this);

            // A correction is not a stuck bounce: give the recovery its full window instead of
            // handing a teleported player straight to Baritone, which cannot path unloaded chunks.
            this.mc.execute(() -> this.slow = 0);
        }

        // A ping that arrives behind a held clear has to be answered from here: the clear is only
        // applied on a free tick, and the connection stalls behind whatever answer comes last.
        GlideClearHold.intake(this, event);
    }

    /**
     * Queues outgoing movement while fake lag is on.
     *
     * <p>Only movement, actions and client commands are held. Chat, commands, inventory and
     * interactions go out as they arrive, so a burst can never swallow something the player typed
     * or a click that changes the inventory.
     *
     * @param event packet send event
     */
    @EventHandler
    private void onSend(PacketEvent.Send event) {
        this.trackFakeFly(event);

        if (this.flushing) return;
        if (!this.holding()) return;

        if (!(event.packet instanceof PlayerMoveC2SPacket)
            && !(event.packet instanceof PlayerActionC2SPacket)
            && !(event.packet instanceof ClientCommandC2SPacket)) {
            return;
        }

        this.queue.add(event.packet);
        event.cancel();
    }

    /**
     * Swaps the chestplate back in before the tick's movement packets leave.
     *
     * <p>The burst has to be undone in the same tick it starts in: the glide is already running,
     * and the next tick cannot begin with the elytra still worn or the landing would cost it
     * durability, which is the whole point of the option.
     *
     * @param event movement packet event
     */
    @EventHandler
    private void onSendMovement(SendMovementPacketsEvent.Pre event) {
        if (this.fakeHand == null || this.mc.player == null) return;

        Hand hand = this.fakeHand;
        this.fakeHand = null;

        this.swapFakeFly(hand);
    }

    /**
     * Silences the armour equip sound while the pair is being swapped.
     *
     * <p>Only the local sound is dropped; the server still sees the swap.
     *
     * @param event sound event
     */
    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (!this.fakeFly.get() || event.sound == null) return;

        if (GliderSwap.isSwapSound(event.sound.getId())) event.cancel();
    }



    /**
     * Releases a held glide clear on the first tick the re-deploy is free.
     *
     * <p>Applying the clear on the tick the jump is pressed costs the whole hop; applying it on a
     * tick where nothing is pressed costs nothing, because the takeoff that follows is a fresh
     * press anyway. The hold is capped so a clear cannot outlive an obstacle pass.
     */
    private void serviceClear() {
        if (!this.holdClear.get() || !this.enabled()) {
            GlideClearHold.release(this);
            return;
        }

        if (!GlideClearHold.claim(this)) return;

        if (!GlideClearHold.holding()) {
            GlideClearHold.answerPings();
            return;
        }

        long age = this.mc.player.age;
        boolean free = !this.mc.player.isOnGround()
            && age > this.pressAge && !this.held;

        if (free || age - GlideClearHold.heldSince() >= this.clearCap.get()) {
            GlideClearHold.release(this);

            this.force = true;
            this.wasGliding = false;
        }
    }

    /**
     * Holds outgoing movement while skimming the floor and releases it in bursts.
     */
    private void serviceLag() {
        if (this.mc.player.isOnGround()) {
            this.groundY = this.mc.player.getY();
        }

        if (!this.holding()) {
            this.lagTicks = 0;
            this.flush();
        } else if (++this.lagTicks >= this.lagFlush.get()) {
            this.lagTicks = 0;
            this.flush();
        }
    }

    /**
     * Checks whether outgoing movement is being held back.
     *
     * @return true while fake lag applies
     */
    private boolean holding() {
        return this.fakeLag.get() && this.isActive() && this.enabled()
            && this.mc.player != null && this.mc.player.isGliding()
            && this.mc.player.getY() - this.groundY < this.lagHeight.get();
    }

    /**
     * Sends everything fake lag is holding.
     */
    private void flush() {
        if (this.queue.isEmpty()) return;

        if (this.mc.getNetworkHandler() == null) {
            this.queue.clear();
            return;
        }

        this.flushing = true;

        try {
            ClientConnection connection = this.mc.getNetworkHandler().getConnection();
            Packet<?> packet;

            while ((packet = this.queue.poll()) != null) {
                connection.send(packet, null, true);
            }
        } finally {
            this.flushing = false;
        }
    }



    /**
     * Runs the chestplate fakefly one tick at a time.
     *
     * <p>The elytra spends the hop in a hand and the chestplate on the chest. A re-deploy swaps the
     * elytra in, starts the glide and swaps the chestplate back before the tick's packets leave, so
     * the elytra is never worn across a landing and takes no durability. The pair has to stay one
     * worn and the other in a hand; while it is not, the module stops pressing, says why once, and
     * resumes on its own once the pair is back.
     */
    private void serviceFakeFly() {
        if (!this.fakeFly.get() || !this.enabled()) {
            this.fakeHand = null;
            this.fakePair = true;
            return;
        }

        long age = this.mc.player.age;
        boolean screen = this.mc.currentScreen instanceof HandledScreen<?>;

        // A swap the server answered without the chest slot ending up where the swap asked for it
        // is a refusal. Every burst after it would be built on an inventory the server does not
        // have, so the module stops pressing until the player resyncs by hand.
        if (this.fakePending >= 0 && this.fakeAcked >= 0 && age > this.fakeAcked) {
            if (this.swapped()) {
                this.fakePending = -1;
                this.fakeAcked = -1L;
            } else {
                this.fakePending = -1;
                this.fakeAcked = -1L;
                this.fakeDesync = true;
            }
        }

        if (this.fakeDesync) {
            this.fakePair = false;
            this.blocked(
                "the server refused the chest swap, right-click the item in your hand to resync"
            );
            return;
        }

        ItemStack worn = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);

        if (GliderSwap.isGlider(worn)) {
            this.fakePair = true;
            this.fakeBlocked = null;

            // Sending another swap on top of one the server has not acknowledged yet is what the
            // check above ends up reading as a refusal, so the next one waits for the answer.
            if (this.fakeSent > this.fakeServer && age - this.fakeSentAge < 10L) return;
            if (screen || age % 2L != 0L) return;

            Hand chest = GliderSwap.chestPieceHand(this.mc.player);

            if (chest != null) {
                this.swapFakeFly(chest);
            } else if (this.mc.player.isOnGround()) {
                int slot = this.chestSlot();

                if (slot >= 0) {
                    this.mc.player.getInventory().setSelectedSlot(slot);
                    this.swapFakeFly(Hand.MAIN_HAND);
                } else {
                    this.blocked("chest piece must be in the hotbar");
                }
            }

            return;
        }

        String problem = GliderSwap.pairProblem(this.mc.player);
        Hand hand = GliderSwap.gliderHand(this.mc.player);

        this.fakePair = problem == null && hand != null;

        if (!this.fakePair) {
            this.blocked(problem != null ? problem
                : "chestplate-fakefly needs a glider and a chest piece, one worn and the other in a hand");
            return;
        }

        if (!this.free() || this.mc.player.isGliding() || screen) return;

        // Bursting on the tick the player left the ground, or straight after the last glide start,
        // asks for a re-deploy the server is still answering.
        if (age - this.fakeStartAge < 2L) return;
        if (this.fakePrevGround || this.fakeLastGround) return;

        this.swapFakeFly(hand);
        this.fakeHand = hand;
        this.force = true;
    }

    /**
     * Swaps a hand item into the chest slot and remembers which side the swap asked for.
     *
     * @param hand hand holding the elytra or the chestplate
     */
    private void swapFakeFly(Hand hand) {
        this.fakeWant = GliderSwap.isGlider(this.mc.player.getStackInHand(hand));
        this.fakeArmed = true;

        try {
            GliderSwap.swapSilently(hand);
        } finally {
            this.fakeArmed = false;
        }
    }

    /**
     * Checks whether the chest slot holds what the last swap asked for.
     *
     * @return true when the swap took effect
     */
    private boolean swapped() {
        ItemStack worn = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return this.fakeWant ? GliderSwap.isGlider(worn) : GliderSwap.isChestPiece(worn);
    }

    /**
     * Tracks the swap transactions the server has answered.
     *
     * @param event packet send event
     */
    private void trackFakeFly(PacketEvent.Send event) {
        if (!this.fakeFly.get() || this.mc.player == null) return;

        if (this.fakeArmed && event.packet instanceof PlayerInteractItemC2SPacket interact) {
            this.fakePending = interact.getSequence();
            this.fakeAcked = -1L;
            this.fakeSent = interact.getSequence();
            this.fakeSentAge = this.mc.player.age;
        }

        if (event.packet instanceof PlayerMoveC2SPacket move) {
            this.fakePrevGround = this.fakeLastGround;
            this.fakeLastGround = move.isOnGround();
        } else if (event.packet instanceof ClientCommandC2SPacket command
            && command.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
            this.fakeStartAge = this.mc.player.age;
        }
    }

    /**
     * Reads the server's answer to a swap.
     *
     * <p>Meteor fires the receive event on the netty thread, so the sequence is handed to the main
     * thread before it is stored.
     *
     * @param event packet receive event
     */
    private void intakeFakeFly(PacketEvent.Receive event) {
        if (!this.fakeFly.get() || this.mc.player == null) return;

        if (event.packet instanceof InventoryS2CPacket inventory && inventory.syncId() == 0) {
            this.fakePending = -1;
            this.fakeAcked = -1L;
            this.fakeDesync = false;
        } else if (event.packet instanceof PlayerActionResponseS2CPacket ack) {
            if (this.fakePending >= 0 && ack.sequence() >= this.fakePending) {
                this.fakeAcked = this.mc.player.age;
            }

            int sequence = ack.sequence();
            this.mc.execute(() -> this.fakeServer = sequence);
        }
    }

    /**
     * Checks whether fakefly has a glider to work with at all.
     *
     * <p>Without one the option is not a reason to keep driving the player forward: the elytra is
     * either worn, in a hand, or in the inventory somewhere reachable.
     *
     * @return true when a usable glider is available
     */
    private boolean glider() {
        return this.fakeFly.get() && GliderSwap.hasGlider(this.mc.player);
    }

    /**
     * Checks whether bounce input may be produced with the pair as it stands.
     *
     * @return true when the fakefly pair is ready, or the option is off
     */
    private boolean ready() {
        return !this.fakeFly.get() || this.fakePair;
    }

    /**
     * Checks whether a re-deploy can be made on this tick.
     *
     * @return true when nothing is holding the jump
     */
    private boolean free() {
        return !this.mc.player.isOnGround()
            && this.mc.player.age > this.pressAge
            && !this.held;
    }

    /**
     * Finds a chestplate in the hotbar.
     *
     * @return matching hotbar slot, or -1 when unavailable
     */
    private int chestSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (GliderSwap.isChestPiece(this.mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    /**
     * Reports a fakefly problem once per change.
     *
     * @param reason reason the pair cannot be used
     */
    private void blocked(String reason) {
        if (reason.equals(this.fakeBlocked)) return;

        this.fakeBlocked = reason;
        this.warning("%s", reason);
    }

    /**
     * Clears every fakefly transaction state.
     */
    private void cleanFakeFly() {
        this.fakeHand = null;
        this.fakePending = -1;
        this.fakeAcked = -1L;
        this.fakeSent = -1;
        this.fakeSentAge = Long.MIN_VALUE / 2;
        this.fakeServer = -1;
        this.fakeDesync = false;
        this.fakeArmed = false;
        this.fakeWant = false;
        this.fakeBlocked = null;
        this.fakePair = true;
        this.fakeLastGround = true;
        this.fakePrevGround = true;
        this.fakeStartAge = Long.MIN_VALUE / 2;
    }



    /**
     * Checks whether bounce input should be forced.
     *
     * @return true while normal bounce movement is active
     */
    public boolean enabled() {
        return this.isActive() && this.mc.player != null
            && !this.pass && (Elytra.equipped() || this.glider());
    }

    /**
     * Returns the current bounce jump input.
     *
     * @return forced jump state
     */
    public boolean jumping() {
        // A stale launch flag used to survive a handover to Baritone, and the key mixin kept answering the
        // jump as pressed while the player was walking. The input is only ours while the module drives movement.
        return this.launch && this.enabled() && this.ready();
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
        this.passStarted = false;
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
        if (Elytra.equipped() || this.glider()) {
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
        // With the chestplate worn the elytra sits in a hand until the burst swaps it in. Pressing
        // while the pair is not ready sends a glide start with no glider on, so the inputs stay
        // released until the module has the pair back.
        if (!this.ready()) {
            this.launch = false;
            this.jump = 0;
            this.force = false;
            this.wasGliding = this.mc.player.isGliding();
            return;
        }

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
            this.passStarted = true;
            this.release();
            this.reset();
            this.passWait = 0;
            return true;
        }

        // Baritone computes its path off the main thread: for a moment after walk() it reports neither a
        // custom goal nor pathing, which is not the same as having finished. Only that opening window is
        // covered by the timeout. Once Baritone has actually walked, the first tick it stops is the end of
        // the pass, and waiting out the whole timeout again is what made the handover back to the bounce
        // take five seconds.
        if (this.pass && !this.passStarted && this.passWait++ < this.passTimeout.get()) {
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

        if (++this.slow >= this.stuckTicks.get()) {
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

        this.flush();
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

        int px = this.dx * reach;
        int pz = this.dz * reach;

        pos = this.block(pos.getX(), pos.getZ());
        this.walk(this.safe(pos.add(px, 0, pz)));
    }

    /**
     * Hands a position to Baritone and holds the bounce until the walk is done.
     *
     * <p>{@code pathing()} keeps the inputs released while Baritone owns the movement and sets
     * {@code started} again once the walk ends, so the bounce resumes without a special case.
     *
     * @param pos goal to walk to
     */
    private void walk(BlockPos pos) {
        this.release();
        this.fakeHand = null;
        this.reset();
        this.clear();

        this.pass = true;
        this.passStarted = false;
        this.passWait = 0;
        this.started = false;
        this.wasGliding = false;

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
        this.passStarted = false;
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

}
