package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leaves a fading trail behind players.
 *
 * <p>One point per tick, per player, kept for a few seconds. Rendering turns each point into a small
 * box whose alpha falls off with its age, so the trail thins out instead of ending on a hard edge.
 * The points are stored per player UUID, which keeps two players from sharing a single line when
 * they cross.
 */
public class Trails extends Module {
    private record Point(double x, double y, double z, long time) {
    }

    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup colours = this.settings.createGroup("Colours");

    private final Setting<Boolean> self = this.general.add(new BoolSetting.Builder()
        .name("self")
        .description("Leave a trail behind your own player too.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> duration = this.general.add(new DoubleSetting.Builder()
        .name("duration")
        .description("Seconds a point stays visible.")
        .defaultValue(3.0)
        .range(0.5, 20.0)
        .build()
    );

    private final Setting<Double> size = this.general.add(new DoubleSetting.Builder()
        .name("size")
        .description("Half size of each point, in blocks.")
        .defaultValue(0.12)
        .range(0.02, 0.6)
        .build()
    );

    private final Setting<Integer> rate = this.general.add(new IntSetting.Builder()
        .name("rate")
        .description("Ticks between two points. Higher leaves a coarser trail.")
        .defaultValue(1)
        .range(1, 10)
        .build()
    );

    private final Setting<SettingColor> colour = this.colours.add(new ColorSetting.Builder()
        .name("colour")
        .description("Trail colour; its alpha is the opacity at the head.")
        .defaultValue(new SettingColor(80, 200, 255, 200))
        .build()
    );

    private final Map<UUID, Deque<Point>> trails = new HashMap<>();
    private final List<UUID> stale = new ArrayList<>();
    private final Color step = new Color(0, 0, 0, 0);

    private int ticks;

    public Trails() {
        super(StashOrganizerAddon.VISUALS, "trails", "Draws a fading trail behind players.");
    }

    @Override
    public void onDeactivate() {
        this.trails.clear();
    }

    /**
     * Records one point per player every {@code rate} ticks.
     *
     * @param event post-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world == null) return;
        if (++this.ticks < this.rate.get()) return;

        this.ticks = 0;

        long now = System.currentTimeMillis();
        long lifetime = (long) (this.duration.get() * 1000.0);

        List<PlayerEntity> players = new ArrayList<>(this.mc.world.getPlayers());

        for (PlayerEntity player : players) {
            if (!this.self.get() && player == this.mc.player) continue;

            Deque<Point> points = this.trails.computeIfAbsent(player.getUuid(), key -> new ArrayDeque<>());
            points.addLast(new Point(player.getX(), player.getY() + 0.1, player.getZ(), now));

            while (!points.isEmpty() && now - points.peekFirst().time() > lifetime) points.removeFirst();
        }

        this.stale.clear();

        for (Map.Entry<UUID, Deque<Point>> entry : this.trails.entrySet()) {
            if (entry.getValue().isEmpty()) this.stale.add(entry.getKey());
        }

        for (UUID uuid : this.stale) this.trails.remove(uuid);
    }

    /**
     * Draws every stored point, older ones dimmer.
     *
     * @param event world render event
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.trails.isEmpty()) return;

        long now = System.currentTimeMillis();
        long lifetime = (long) (this.duration.get() * 1000.0);
        double half = this.size.get();
        SettingColor base = this.colour.get();

        for (Deque<Point> points : this.trails.values()) {
            for (Point point : points) {
                long age = now - point.time();

                if (age > lifetime) continue;

                double fade = 1.0 - (double) age / lifetime;
                this.step.r = base.r;
                this.step.g = base.g;
                this.step.b = base.b;
                this.step.a = (int) Math.round(base.a * fade);

                event.renderer.box(
                    point.x() - half, point.y() - half, point.z() - half,
                    point.x() + half, point.y() + half, point.z() + half,
                    this.step, this.step,
                    ShapeMode.Sides, 0
                );
            }
        }
    }
}
