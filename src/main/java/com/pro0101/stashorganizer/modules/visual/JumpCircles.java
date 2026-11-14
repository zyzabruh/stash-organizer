package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Draws an expanding ring on the ground each time a player leaves it.
 *
 * <p>The ring is drawn as a thin band of quads rather than as a fan of lines. Lines are one pixel
 * wide whatever the distance and stay that way through the projection, so the ring looked like a
 * hairline both underfoot and far away; a band has a width in blocks, which keeps it readable at
 * any distance and makes its thickness a setting instead of a side effect of the renderer.
 *
 * <p>A jump is detected from the transition of {@code isOnGround}, so a player who walks off a ledge
 * does not trigger it.
 */
public class JumpCircles extends Module {
    private record Ring(double x, double y, double z, long time) {
    }

    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup colours = this.settings.createGroup("Colours");

    private final Setting<Boolean> self = this.general.add(new BoolSetting.Builder()
        .name("self")
        .description("Draw the ring for your own jumps too.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> duration = this.general.add(new DoubleSetting.Builder()
        .name("duration")
        .description("Seconds the ring takes to expand and fade.")
        .defaultValue(0.55)
        .range(0.1, 3.0)
        .build()
    );

    private final Setting<Double> radius = this.general.add(new DoubleSetting.Builder()
        .name("radius")
        .description("Radius reached at the end of the animation, in blocks.")
        .defaultValue(1.6)
        .range(0.2, 6.0)
        .build()
    );

    private final Setting<Double> thickness = this.general.add(new DoubleSetting.Builder()
        .name("thickness")
        .description("Width of the ring, in blocks.")
        .defaultValue(0.08)
        .range(0.01, 0.4)
        .sliderRange(0.02, 0.25)
        .build()
    );

    private final Setting<SettingColor> colour = this.colours.add(new ColorSetting.Builder()
        .name("colour")
        .description("Ring colour; its alpha is the opacity at the start.")
        .defaultValue(new SettingColor(255, 255, 255, 170))
        .build()
    );

    private static final int SEGMENTS = 48;

    private final Map<UUID, Boolean> grounded = new HashMap<>();
    private final List<Ring> rings = new ArrayList<>();
    private final Color start = new Color(0, 0, 0, 0);

    public JumpCircles() {
        super(StashOrganizerAddon.VISUALS, "jump-circles", "Draws a ring on the ground each time a player jumps.");
    }

    @Override
    public void onDeactivate() {
        this.grounded.clear();
        this.rings.clear();
    }

    /**
     * Detects the jumps and drops the rings that have finished.
     *
     * @param event post-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world == null) return;

        long now = System.currentTimeMillis();
        long lifetime = (long) (this.duration.get() * 1000.0);

        for (PlayerEntity player : new ArrayList<>(this.mc.world.getPlayers())) {
            if (!this.self.get() && player == this.mc.player) continue;

            boolean onGround = player.isOnGround();
            Boolean previous = this.grounded.put(player.getUuid(), onGround);

            if (previous != null && previous && !onGround) {
                this.rings.add(new Ring(player.getX(), player.getY(), player.getZ(), now));
            }
        }

        this.rings.removeIf(ring -> now - ring.time() > lifetime);
    }

    /**
     * Draws the rings, each expanding from zero to the configured radius.
     *
     * @param event world render event
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.rings.isEmpty()) return;

        long now = System.currentTimeMillis();
        long lifetime = (long) (this.duration.get() * 1000.0);
        SettingColor base = this.colour.get();

        for (Ring ring : this.rings) {
            double progress = (double) (now - ring.time()) / lifetime;

            if (progress < 0.0 || progress > 1.0) continue;

            double current = this.radius.get() * progress;
            double alpha = base.a * (1.0 - progress);

            this.start.r = base.r;
            this.start.g = base.g;
            this.start.b = base.b;
            this.start.a = (int) Math.round(alpha);

            double half = this.thickness.get() * 0.5;
            double outer = current + half;
            double inner = Math.max(0.0, current - half);
            double y = ring.y() + 0.02;

            for (int i = 0; i < SEGMENTS; i++) {
                double from = Math.PI * 2.0 * i / SEGMENTS;
                double to = Math.PI * 2.0 * (i + 1) / SEGMENTS;

                double cosFrom = Math.cos(from);
                double sinFrom = Math.sin(from);
                double cosTo = Math.cos(to);
                double sinTo = Math.sin(to);

                event.renderer.quad(
                    ring.x() + cosFrom * outer, y, ring.z() + sinFrom * outer,
                    ring.x() + cosTo * outer, y, ring.z() + sinTo * outer,
                    ring.x() + cosTo * inner, y, ring.z() + sinTo * inner,
                    ring.x() + cosFrom * inner, y, ring.z() + sinFrom * inner,
                    this.start
                );
            }
        }
    }
}
