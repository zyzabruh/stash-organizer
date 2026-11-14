package com.pro0101.stashorganizer.modules.huntbuddy;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.mixins.PlayerInventoryAccessor;
import com.pro0101.stashorganizer.modules.huntbuddy.util.GliderSwap;
import com.pro0101.stashorganizer.modules.huntbuddy.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/**
 * Dives out of an elytra glide onto a player below, swaps the chestplate and
 * mace in for one hit, then puts the elytra back and climbs away on rockets.
 *
 * <p>The module keeps the target prediction deliberately small: the target's
 * current velocity is extrapolated for a few ticks and the glide is aimed at
 * the resulting point. The attack itself is sent on the tick after the gear
 * swap so the server has the mace and chestplate before the swing.
 */
public class ElytraMace extends Module {
    private static final double ATTACK_RANGE = 3.2;
    private static final int MAX_APPROACH_TICKS = 120;
    private static final int MAX_ESCAPE_TICKS = 90;
    private static final int ROCKET_INTERVAL = 10;
    private static final int PREDICT_TICKS = 4;
    private static final int RESTORE_INTERVAL = 4;
    private static final int MAX_RESTORE_ATTEMPTS = 10;

    private static boolean diving;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgEscape = this.settings.createGroup("Escape");

    public final Setting<Trigger> trigger = this.sgGeneral.add(new EnumSetting.Builder<Trigger>()
        .name("trigger")
        .description("Look dives on the target your camera points at; Auto picks the nearest target below in range.")
        .defaultValue(Trigger.Look)
        .build()
    );

    public final Setting<Double> targetRange = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("How far away a target may be.")
        .defaultValue(48.0)
        .min(8.0)
        .sliderRange(8.0, 128.0)
        .build()
    );

    public final Setting<Double> minDrop = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("min-drop")
        .description("How far below you a target must be before a dive starts.")
        .defaultValue(6.0)
        .min(2.0)
        .sliderRange(2.0, 64.0)
        .build()
    );

    public final Setting<Double> minFall = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("min-fall")
        .description("Fall distance the server must have counted before the mace swings. 1.5 is where the smash starts.")
        .defaultValue(1.5)
        .min(1.5)
        .sliderRange(1.5, 10.0)
        .build()
    );

    public final Setting<Boolean> ignoreFriends = this.sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Never dive on Meteor friends.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> enemiesOnly = this.sgGeneral.add(new BoolSetting.Builder()
        .name("enemies-only")
        .description("Only dive on players named in the enemy list.")
        .defaultValue(false)
        .build()
    );

    public final Setting<List<String>> enemies = this.sgGeneral.add(new StringListSetting.Builder()
        .name("enemies")
        .description("Player names treated as enemies by enemies-only.")
        .visible(this.enemiesOnly::get)
        .build()
    );

    public final Setting<Boolean> repeat = this.sgGeneral.add(new BoolSetting.Builder()
        .name("repeat")
        .description("Keep diving on the same target until it dies. Stops after two dives in a row without a hit.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> steer = this.sgGeneral.add(new BoolSetting.Builder()
        .name("steer")
        .description("Fly the approach and the dive for you. With it off you fly and the module only drops, strikes and escapes.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> escape = this.sgEscape.add(new BoolSetting.Builder()
        .name("escape")
        .description("Climb away on rockets after the hit.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> recoverPitch = this.sgEscape.add(new DoubleSetting.Builder()
        .name("recover-pitch")
        .description("Pitch in degrees used for the climb-out.")
        .defaultValue(40.0)
        .min(5.0)
        .sliderRange(5.0, 80.0)
        .visible(this.escape::get)
        .build()
    );

    public final Setting<Double> recoverHeight = this.sgEscape.add(new DoubleSetting.Builder()
        .name("recover-height")
        .description("Height above the dive start that ends the climb-out.")
        .defaultValue(25.0)
        .min(5.0)
        .sliderRange(5.0, 100.0)
        .visible(this.escape::get)
        .build()
    );

    public final Setting<Boolean> rocketBoost = this.sgEscape.add(new BoolSetting.Builder()
        .name("rocket-boost")
        .description("Turn RocketBoost on while this module is flying, and back off afterwards if this module enabled it.")
        .defaultValue(false)
        .build()
    );

    private State state = State.IDLE;
    private PlayerEntity target;
    private int stateTicks;
    private int misses;
    private int rocketCooldown;
    private int previousSlot = -1;
    private double diveStartY;
    private boolean struckThisDive;
    private boolean rocketBoostEnabledByUs;
    private boolean pendingElytraRestore;
    private int restoreAttempts;
    private int restoreCooldown;
    private boolean bindingWarned;

    public ElytraMace() {
        super(StashOrganizerAddon.CATEGORY, "elytra-mace",
            "Dives out of an elytra glide and smashes a target with a mace."
        );
    }

    @Override
    public void onActivate() {
        this.reset();

        if (this.mc.player == null) return;

        if (!GliderSwap.hasGlider(this.mc.player)
            || !InvUtils.find(stack -> GliderSwap.isSafeChestPiece(stack)).found()
            || !InvUtils.find(Items.MACE).found()) {
            this.error("Elytra Mace needs an elytra, a chestplate without Curse of Binding and a mace in your inventory.");
            this.toggle();
            return;
        }

        if (!this.mc.player.isCreative()
            && GliderSwap.hasBindingCurse(this.mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
            this.error("The worn elytra has Curse of Binding and cannot be swapped out.");
            this.toggle();
            return;
        }

        if (this.rocketBoost.get()) {
            RocketBoost boost = Modules.get().get(RocketBoost.class);
            if (boost != null && !boost.isActive()) {
                boost.toggle();
                this.rocketBoostEnabledByUs = true;
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.restoreElytra();
        if (this.pendingElytraRestore) {
            this.warning("Elytra Mace could not put the elytra back on before stopping.");
        }
        diving = false;

        RocketBoost boost = Modules.get().get(RocketBoost.class);
        if (this.rocketBoostEnabledByUs && boost != null && boost.isActive()) {
            boost.toggle();
        }

        this.rocketBoostEnabledByUs = false;
        this.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        ClientPlayerEntity player = this.mc.player;
        if (player == null || this.mc.world == null) return;

        // A previous swap may have been refused or arrived too early for the
        // server; retry, spaced out, while we are not wearing the elytra.
        if (this.pendingElytraRestore
            && (this.state == State.IDLE || this.state == State.ESCAPE)
            && --this.restoreCooldown <= 0) {
            this.restoreElytra();
        }

        if (this.state == State.IDLE) {
            PlayerEntity found = this.findTarget();
            if (found != null) this.beginDive(found, player);
            return;
        }

        if (this.target == null || !this.target.isAlive() || this.target.isRemoved()) {
            this.finish(this.struckThisDive);
            return;
        }

        this.stateTicks++;

        switch (this.state) {
            case APPROACH -> this.approach(player);
            case STRIKE -> this.strike(player);
            case ESCAPE -> this.escape(player);
            default -> {
            }
        }
    }

    private void approach(ClientPlayerEntity player) {
        if (!player.isGliding()) {
            this.finish(false);
            return;
        }

        if (this.steer.get()) this.aim(player);

        double distance = player.distanceTo(this.target);
        if (distance <= ATTACK_RANGE && player.fallDistance >= this.minFall.get()) {
            this.prepareStrike(player);
        } else if (this.stateTicks > MAX_APPROACH_TICKS) {
            this.finish(false);
        }
    }

    private void prepareStrike(ClientPlayerEntity player) {
        ItemStack worn = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!GliderSwap.isGlider(worn)) {
            this.finish(false);
            return;
        }

        if (!player.isCreative() && GliderSwap.hasBindingCurse(worn)) {
            this.warnBinding();
            this.finish(false);
            return;
        }

        if (!this.stageChestplate(player) || !this.stageMace(player)) {
            this.warning("Elytra Mace needs a free offhand for a chestplate without Curse of Binding and a mace in the hotbar.");
            this.finish(false);
            return;
        }

        Hand chest = GliderSwap.chestPieceHand(player);
        if (chest == null) {
            this.finish(false);
            return;
        }

        // Put the chestplate on now and keep the elytra in the offhand. The
        // next tick sends the attack, so the server sees the gear swap first.
        GliderSwap.swapSilently(chest);
        this.state = State.STRIKE;
        this.stateTicks = 0;
    }

    private void strike(ClientPlayerEntity player) {
        if (this.stateTicks < 2) {
            if (this.steer.get()) this.aim(player);
            return;
        }

        boolean hit = false;
        if (this.target != null
            && this.target.isAlive()
            && player.distanceTo(this.target) <= ATTACK_RANGE + 1.0
            && this.mc.interactionManager != null
            && player.getMainHandStack().isOf(Items.MACE)) {
            this.mc.interactionManager.attackEntity(player, this.target);
            player.swingHand(Hand.MAIN_HAND);
            hit = true;
            this.struckThisDive = true;
        }

        this.restoreElytra();
        this.diving = false;
        this.state = State.ESCAPE;
        this.stateTicks = 0;
        this.rocketCooldown = 0;

        if (!this.escape.get() || !hit) this.finish(hit);
    }

    private void escape(ClientPlayerEntity player) {
        if (!player.isGliding()) {
            this.restoreElytra();
            if (!this.pendingElytraRestore || this.stateTicks > MAX_ESCAPE_TICKS) {
                this.finish(this.struckThisDive);
            }
            return;
        }

        player.setPitch(-this.recoverPitch.get().floatValue());

        if (this.rocketCooldown > 0) {
            this.rocketCooldown--;
        } else {
            Utils.firework(this.mc, true);
            this.rocketCooldown = ROCKET_INTERVAL;
        }

        boolean highEnough = player.getY() >= this.diveStartY + this.recoverHeight.get();
        if (highEnough || this.stateTicks > MAX_ESCAPE_TICKS) {
            this.finish(this.struckThisDive);
        }
    }

    private void beginDive(PlayerEntity found, ClientPlayerEntity player) {
        this.target = found;
        this.state = State.APPROACH;
        this.stateTicks = 0;
        this.diveStartY = player.getY();
        this.previousSlot = player.getInventory().getSelectedSlot();
        this.struckThisDive = false;
        diving = true;
    }

    private void aim(ClientPlayerEntity player) {
        Vec3d predicted = this.target.getEntityPos().add(
            this.target.getVelocity().multiply(PREDICT_TICKS)
        );
        Vec3d eyes = player.getEyePos();
        Vec3d direction = predicted.subtract(eyes);

        if (direction.lengthSquared() < 1.0E-4) return;

        direction = direction.normalize();
        float yaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(
            Math.atan2(direction.y, direction.horizontalLength())
        );

        player.setYaw(yaw);
        player.setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
    }

    private PlayerEntity findTarget() {
        ClientPlayerEntity player = this.mc.player;
        if (player == null || this.mc.world == null || !player.isGliding()) return null;

        return this.mc.world.getPlayers().stream()
            .filter(candidate -> candidate != player)
            .filter(PlayerEntity::isAlive)
            .filter(candidate -> !candidate.isSpectator())
            .filter(candidate -> !this.ignoreFriends.get() || !Friends.get().isFriend(candidate))
            .filter(candidate -> !this.enemiesOnly.get() || this.isEnemy(candidate))
            .filter(candidate -> player.getY() - candidate.getY() >= this.minDrop.get())
            .filter(candidate -> player.distanceTo(candidate) <= this.targetRange.get())
            .filter(this::inLook)
            .min(Comparator.comparingDouble(player::distanceTo))
            .orElse(null);
    }

    private boolean isEnemy(PlayerEntity candidate) {
        String name = candidate.getName().getString();
        for (String enemy : this.enemies.get()) {
            if (enemy.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private boolean inLook(PlayerEntity candidate) {
        if (this.trigger.get() == Trigger.Auto) return true;

        Vec3d direction = candidate.getEyePos().subtract(this.mc.player.getEyePos());
        if (direction.lengthSquared() < 1.0E-4) return false;

        return this.mc.player.getRotationVec(1.0F)
            .dotProduct(direction.normalize()) >= Math.cos(Math.toRadians(35.0));
    }

    private boolean stageChestplate(ClientPlayerEntity player) {
        if (GliderSwap.isSafeChestPiece(player.getOffHandStack())) return true;

        FindItemResult chest = InvUtils.find(stack -> GliderSwap.isSafeChestPiece(stack));
        if (!chest.found()) return false;
        if (chest.isOffhand()) return true;

        InvUtils.move().from(chest.slot()).toOffhand();
        return GliderSwap.isSafeChestPiece(player.getOffHandStack());
    }

    private boolean stageMace(ClientPlayerEntity player) {
        FindItemResult mace = InvUtils.findInHotbar(Items.MACE);

        if (!mace.found()) {
            mace = InvUtils.find(Items.MACE);
            if (!mace.found()) return false;

            int hotbar = player.getInventory().getSelectedSlot();
            if (mace.isOffhand()) {
                InvUtils.move().fromOffhand().toHotbar(hotbar);
            } else {
                InvUtils.move().from(mace.slot()).toHotbar(hotbar);
            }

            mace = InvUtils.findInHotbar(Items.MACE);
            if (!mace.found()) return false;
        }

        return InvUtils.swap(mace.slot(), false)
            && player.getMainHandStack().isOf(Items.MACE);
    }

    private void restoreElytra() {
        ClientPlayerEntity player = this.mc.player;
        if (player == null) return;

        if (this.previousSlot >= 0) {
            ((PlayerInventoryAccessor) player.getInventory())
                .setSelectedSlot(this.previousSlot);
            this.previousSlot = -1;
        }

        if (!GliderSwap.isGlider(player.getEquippedStack(EquipmentSlot.CHEST))) {
            if (!player.isCreative() && GliderSwap.hasBindingCurse(player.getEquippedStack(EquipmentSlot.CHEST))) {
                // The chest slot is locked; retrying would only spam the
                // server with refused clicks, so stop right here.
                this.warnBinding();
                this.pendingElytraRestore = false;
                this.restoreAttempts = 0;
                return;
            }

            this.putGliderBack(player);
        }

        if (GliderSwap.isGlider(player.getEquippedStack(EquipmentSlot.CHEST))) {
            this.pendingElytraRestore = false;
            this.restoreAttempts = 0;
        } else if (++this.restoreAttempts >= MAX_RESTORE_ATTEMPTS) {
            this.pendingElytraRestore = false;
            this.warning("Elytra Mace could not put the elytra back on; check that a free elytra is in your inventory.");
        } else {
            this.pendingElytraRestore = true;
            this.restoreCooldown = RESTORE_INTERVAL;
        }

        if (!player.isGliding() && !player.isOnGround()
            && GliderSwap.isGlider(player.getEquippedStack(EquipmentSlot.CHEST))) {
            this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                player, ClientCommandC2SPacket.Mode.START_FALL_FLYING
            ));
            player.startGliding();
        }
    }

    /**
     * Puts a glider back on the player: straight from the offhand when the
     * chestplate is still there, otherwise from a hand or the inventory. The
     * caller retries on later ticks when the click has not landed yet.
     */
    private void putGliderBack(ClientPlayerEntity player) {
        Hand hand = GliderSwap.gliderHand(player);
        if (hand != null) {
            GliderSwap.swapSilently(hand);
        } else {
            GliderSwap.wearGlider(player);
        }
    }

    private void warnBinding() {
        if (this.bindingWarned) return;
        this.bindingWarned = true;
        this.warning("Curse of Binding on the chest slot: Elytra Mace will not swap armour.");
    }

    private void finish(boolean hit) {
        this.restoreElytra();
        this.misses = hit ? 0 : this.misses + 1;

        if (this.repeat.get() && this.misses >= 2) {
            this.warning("Elytra Mace stopped after two dives without a hit.");
            this.toggle();
        }

        this.target = null;
        this.state = State.IDLE;
        this.stateTicks = 0;
        this.rocketCooldown = 0;
        this.struckThisDive = false;
        diving = false;
    }

    private void reset() {
        this.state = State.IDLE;
        this.target = null;
        this.stateTicks = 0;
        this.misses = 0;
        this.rocketCooldown = 0;
        this.previousSlot = -1;
        this.diveStartY = 0.0;
        this.struckThisDive = false;
        this.pendingElytraRestore = false;
        this.restoreAttempts = 0;
        this.restoreCooldown = 0;
        this.bindingWarned = false;
        diving = false;
    }

    public static boolean isDiving() {
        return diving;
    }

    public enum Trigger {
        Look,
        Auto
    }

    private enum State {
        IDLE,
        APPROACH,
        STRIKE,
        ESCAPE
    }
}
