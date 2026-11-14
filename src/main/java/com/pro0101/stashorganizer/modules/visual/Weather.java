package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws a local weather layer around the player: rain, snow, fireflies or embers.
 *
 * <p>This is drawing, not the game's weather: no particle entity is created, so nothing exists on
 * the server and nothing costs a tick. Each flake is a position, a velocity and a lifetime, advanced
 * by the frame time, and drawn as a line for rain and as a small box for everything else.
 *
 * <p>Flakes are spawned in the air above the player and dropped when they enter a solid block, so
 * the layer stops at the ground instead of raining through it. The list refills itself as flakes
 * expire, which keeps the density constant without ever rebuilding the whole set.
 */
public class Weather extends Module {
    /** Weather looks. */
    public enum Mode {
        /** Fast vertical streaks falling down. */
        Rain,
        /** Slow drifting flakes. */
        Snow,
        /** Dim yellow points that blink in place. */
        Fireflies,
        /** Small orange points rising and fading. */
        Embers
    }

    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup colours = this.settings.createGroup("Colours");

    private final Setting<Mode> mode = this.general.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Which weather is drawn.")
        .defaultValue(Mode.Snow)
        .build()
    );

    private final Setting<Integer> density = this.general.add(new IntSetting.Builder()
        .name("density")
        .description("How many flakes are alive at once.")
        .defaultValue(220)
        .range(20, 1200)
        .sliderRange(20, 600)
        .build()
    );

    private final Setting<Integer> radius = this.general.add(new IntSetting.Builder()
        .name("radius")
        .description("Radius of the area the flakes live in, in blocks.")
        .defaultValue(18)
        .range(4, 48)
        .build()
    );

    private final Setting<Double> speed = this.general.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Multiplier applied to every flake's velocity.")
        .defaultValue(1.0)
        .range(0.1, 4.0)
        .build()
    );

    private final Setting<Double> size = this.general.add(new DoubleSetting.Builder()
        .name("size")
        .description("Size of a flake, in blocks.")
        .defaultValue(1.0)
        .range(0.2, 3.0)
        .build()
    );

    private final Setting<SettingColor> colour = this.colours.add(new ColorSetting.Builder()
        .name("colour")
        .description("Flake colour.")
        .defaultValue(new SettingColor(235, 245, 255, 200))
        .build()
    );

    /** One flake, drawn every frame. */
    private static final class Flake {
        private final Vec3d velocity;
        private final float lifetime;
        private final float phase;

        private Vec3d pos;
        private float age;

        private Flake(Vec3d pos, Vec3d velocity, float lifetime, float phase) {
            this.pos = pos;
            this.velocity = velocity;
            this.lifetime = lifetime;
            this.phase = phase;
        }
    }

    private final List<Flake> flakes = new ArrayList<>();
    private final Color tint = new Color(0, 0, 0, 0);

    private long lastFrame = System.nanoTime();
    private Vec3d centre;

    public Weather() {
        super(StashOrganizerAddon.VISUALS, "weather", "Draws local rain, snow, fireflies or embers around you.");
    }

    @Override
    public void onDeactivate() {
        this.flakes.clear();
        this.centre = null;
    }

    /**
     * Advances the flakes and draws them.
     *
     * @param event world render event
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.world == null || this.mc.player == null) return;

        long now = System.nanoTime();
        double delta = Math.min((now - this.lastFrame) / 1.0e9, 0.1);
        this.lastFrame = now;

        Vec3d player = this.mc.player.getEntityPos();

        // A teleport or a respawn leaves the old flakes behind; starting over is cheaper than
        // dragging them across the world.
        if (this.centre == null || this.centre.squaredDistanceTo(player) > 4096.0) {
            this.flakes.clear();
            this.centre = player;
        }

        this.spawn(player);
        this.advance(player, delta);
        this.draw(event);
    }

    private void spawn(Vec3d player) {
        int wanted = this.density.get();
        int budget = Math.max(1, wanted / 10);

        for (int i = 0; i < budget && this.flakes.size() < wanted; i++) {
            Flake flake = this.create(player);
            if (flake != null) this.flakes.add(flake);
        }
    }

    private Flake create(Vec3d player) {
        double limit = this.radius.get();
        double angle = Math.random() * Math.PI * 2.0;
        double distance = Math.sqrt(Math.random()) * limit;

        double x = player.x + Math.cos(angle) * distance;
        double z = player.z + Math.sin(angle) * distance;

        Mode current = this.mode.get();
        double y;
        Vec3d velocity;
        float lifetime;

        switch (current) {
            case Rain -> {
                y = player.y + 2.0 + Math.random() * 14.0;
                velocity = new Vec3d(0.0, -18.0, 0.0);
                lifetime = 2.0F;
            }
            case Snow -> {
                y = player.y + Math.random() * 10.0;
                velocity = new Vec3d((Math.random() - 0.5) * 0.6, -1.1, (Math.random() - 0.5) * 0.6);
                lifetime = 12.0F;
            }
            case Fireflies -> {
                y = player.y + Math.random() * 4.0;
                velocity = new Vec3d((Math.random() - 0.5) * 0.25, (Math.random() - 0.5) * 0.15, (Math.random() - 0.5) * 0.25);
                lifetime = 10.0F;
            }
            default -> {
                y = player.y + Math.random() * 3.0;
                velocity = new Vec3d((Math.random() - 0.5) * 0.2, 0.55 + Math.random() * 0.35, (Math.random() - 0.5) * 0.2);
                lifetime = 6.0F;
            }
        }

        Vec3d pos = new Vec3d(x, y, z);
        BlockState state = this.mc.world.getBlockState(BlockPos.ofFloored(pos));

        if (!state.isAir() && state.isSolidBlock(this.mc.world, BlockPos.ofFloored(pos))) return null;

        return new Flake(pos, velocity, lifetime, (float) (Math.random() * Math.PI * 2.0));
    }

    private void advance(Vec3d player, double delta) {
        double limit = this.radius.get() * 1.5;
        double limitSquared = limit * limit;
        double speed = this.speed.get();
        float step = (float) delta;

        Iterator<Flake> iterator = this.flakes.iterator();

        while (iterator.hasNext()) {
            Flake flake = iterator.next();
            flake.age += step;

            if (flake.age > flake.lifetime) {
                iterator.remove();
                continue;
            }

            flake.pos = flake.pos.add(flake.velocity.multiply(speed * delta));

            if (flake.pos.squaredDistanceTo(player) > limitSquared) {
                iterator.remove();
                continue;
            }

            BlockPos pos = BlockPos.ofFloored(flake.pos);
            BlockState state = this.mc.world.getBlockState(pos);

            if (!state.isAir() && state.isSolidBlock(this.mc.world, pos)) iterator.remove();
        }
    }

    private void draw(Render3DEvent event) {
        if (this.flakes.isEmpty()) return;

        SettingColor base = this.colour.get();
        Mode current = this.mode.get();
        double scale = this.size.get();

        for (Flake flake : this.flakes) {
            float life = flake.age / flake.lifetime;
            float fade = life < 0.1F ? life / 0.1F : life > 0.7F ? (1.0F - life) / 0.3F : 1.0F;

            if (current == Mode.Fireflies) {
                fade *= 0.35F + 0.65F * (float) (Math.sin(flake.age * 6.0 + flake.phase) * 0.5 + 0.5);
            } else if (current == Mode.Embers) {
                fade *= 0.6F + 0.4F * (float) (Math.sin(flake.age * 9.0 + flake.phase) * 0.5 + 0.5);
            }

            this.tint.r = base.r;
            this.tint.g = base.g;
            this.tint.b = base.b;
            this.tint.a = Math.max(0, Math.min(255, Math.round(base.a * fade)));

            if (current == Mode.Rain) {
                double length = 0.55 * scale;

                event.renderer.line(
                    flake.pos.x, flake.pos.y, flake.pos.z,
                    flake.pos.x, flake.pos.y - length, flake.pos.z,
                    this.tint
                );
            } else {
                double half = 0.035 * scale;

                event.renderer.box(
                    flake.pos.x - half, flake.pos.y - half, flake.pos.z - half,
                    flake.pos.x + half, flake.pos.y + half, flake.pos.z + half,
                    this.tint, this.tint,
                    meteordevelopment.meteorclient.renderer.ShapeMode.Sides, 0
                );
            }
        }
    }
}
