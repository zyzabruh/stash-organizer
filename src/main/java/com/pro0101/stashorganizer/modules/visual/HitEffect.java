package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

/**
 * Draws a short marker at the crosshair whenever an attack lands.
 *
 * <p>The marker is four lines that open outward from the centre and fade, so it marks the hit
 * without covering the target. It is drawn in 2D on the crosshair rather than at the entity: a
 * particle on a moving mob is easy to miss, while the centre of the screen is exactly where the eye
 * already is.
 */
public class HitEffect extends Module {
    private final SettingGroup general = this.settings.getDefaultGroup();

    private final Setting<Double> duration = this.general.add(new DoubleSetting.Builder()
        .name("duration")
        .description("Seconds the marker stays on screen.")
        .defaultValue(0.3)
        .range(0.05, 1.5)
        .build()
    );

    private final Setting<Double> size = this.general.add(new DoubleSetting.Builder()
        .name("size")
        .description("Distance from the crosshair the lines reach, in pixels.")
        .defaultValue(14.0)
        .range(4.0, 60.0)
        .build()
    );

    private final Setting<SettingColor> colour = this.general.add(new ColorSetting.Builder()
        .name("colour")
        .description("Marker colour; its alpha is the opacity at the start.")
        .defaultValue(new SettingColor(255, 60, 60, 230))
        .build()
    );

    private final Color tip = new Color(0, 0, 0, 0);

    private long hitTime;
    private boolean hit;

    public HitEffect() {
        super(StashOrganizerAddon.VISUALS, "hit-effect", "Marks each hit with a short burst at the crosshair.");
    }

    /**
     * Starts the animation on a hit.
     *
     * @param event attack event
     */
    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        this.hitTime = System.currentTimeMillis();
        this.hit = true;
    }

    /**
     * Draws the four lines while the marker is alive.
     *
     * @param event 2D render event
     */
    @EventHandler
    private void onRender(Render2DEvent event) {
        if (!this.hit) return;

        long elapsed = System.currentTimeMillis() - this.hitTime;
        long lifetime = (long) (this.duration.get() * 1000.0);

        if (elapsed > lifetime) {
            this.hit = false;
            return;
        }

        double progress = (double) elapsed / lifetime;
        double inner = 3.0 + this.size.get() * progress;
        double outer = inner + 4.0;

        SettingColor base = this.colour.get();
        double alpha = base.a * (1.0 - progress);

        this.tip.r = base.r;
        this.tip.g = base.g;
        this.tip.b = base.b;
        this.tip.a = (int) Math.round(alpha);

        double centreX = event.screenWidth / 2.0;
        double centreY = event.screenHeight / 2.0;

        Renderer2D.COLOR.begin();

        Renderer2D.COLOR.line(centreX - outer, centreY, centreX - inner, centreY, this.tip);
        Renderer2D.COLOR.line(centreX + inner, centreY, centreX + outer, centreY, this.tip);
        Renderer2D.COLOR.line(centreX, centreY - outer, centreX, centreY - inner, this.tip);
        Renderer2D.COLOR.line(centreX, centreY + inner, centreX, centreY + outer, this.tip);

        Renderer2D.COLOR.render();
    }
}
