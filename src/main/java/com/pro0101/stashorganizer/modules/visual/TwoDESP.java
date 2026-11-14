package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws a screen-space box around other players.
 *
 * <p>The eight corners of the hitbox are projected one by one, then the smallest rectangle that
 * contains them all is drawn. That is what makes the box hug the model at any angle, where a
 * billboard quad would drift away from the silhouette near the edge of the screen.
 *
 * <p>Everything is drawn in flat 2D: corner brackets and a health bar, no depth test, so the box
 * stays visible through walls.
 */
public class TwoDESP extends Module {
    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup colours = this.settings.createGroup("Colours");

    private final Setting<Double> range = this.general.add(new DoubleSetting.Builder()
        .name("range")
        .description("Only players within this distance are boxed.")
        .defaultValue(64.0)
        .range(8.0, 256.0)
        .build()
    );

    private final Setting<Integer> limit = this.general.add(new IntSetting.Builder()
        .name("limit")
        .description("Maximum number of boxes, nearest first.")
        .defaultValue(16)
        .range(1, 128)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Boolean> corners = this.general.add(new BoolSetting.Builder()
        .name("corners")
        .description("Draw corner brackets instead of a full rectangle.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> cornerLength = this.general.add(new DoubleSetting.Builder()
        .name("corner-length")
        .description("Length of a bracket, in pixels.")
        .defaultValue(10.0)
        .range(2.0, 40.0)
        .visible(this.corners::get)
        .build()
    );

    private final Setting<Boolean> healthBar = this.general.add(new BoolSetting.Builder()
        .name("health-bar")
        .description("Draw a health bar above the box.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> boxColour = this.colours.add(new ColorSetting.Builder()
        .name("box-colour")
        .description("Box and brackets.")
        .defaultValue(new SettingColor(255, 70, 70, 230))
        .build()
    );

    private final Setting<SettingColor> healthHigh = this.colours.add(new ColorSetting.Builder()
        .name("health-high")
        .description("Health bar colour at full health.")
        .defaultValue(new SettingColor(90, 230, 120))
        .build()
    );

    private final Setting<SettingColor> healthLow = this.colours.add(new ColorSetting.Builder()
        .name("health-low")
        .description("Health bar colour near zero.")
        .defaultValue(new SettingColor(230, 70, 70))
        .build()
    );

    /** Screen rectangle of one projected player. */
    private record Rect(double left, double top, double width, double height, double health) {
    }

    private final List<Rect> rects = new ArrayList<>();
    private final Color start = new Color(0, 0, 0, 0);
    private final Color end = new Color(0, 0, 0, 0);

    public TwoDESP() {
        super(StashOrganizerAddon.VISUALS, "two-d-esp", "Draws a 2D box and a health bar around other players.");
    }

    /**
     * Projects every player in range while the 3D matrices are current.
     *
     * <p>The projection cannot happen in the 2D pass: {@link NametagUtils} works from the matrices
     * the world pass left behind, and by the time 2D is drawn they are gone. The rectangles are
     * therefore computed here and only painted later.
     *
     * @param event world render event
     */
    @EventHandler
    private void onProject(Render3DEvent event) {
        this.rects.clear();

        if (this.mc.world == null || this.mc.player == null) return;

        double limitDistance = this.range.get();
        List<PlayerEntity> players = new ArrayList<>();

        for (PlayerEntity player : this.mc.world.getPlayers()) {
            if (player == this.mc.player) continue;
            if (player.squaredDistanceTo(this.mc.player) > limitDistance * limitDistance) continue;

            players.add(player);
        }

        players.sort(Comparator.comparingDouble(player -> player.squaredDistanceTo(this.mc.player)));

        int drawn = 0;

        for (PlayerEntity player : players) {
            if (drawn++ >= this.limit.get()) break;
            this.project(player);
        }
    }

    /**
     * Paints the rectangles the projection stored.
     *
     * @param event 2D render event
     */
    @EventHandler
    private void onRender(Render2DEvent event) {
        if (this.rects.isEmpty()) return;

        for (Rect rect : this.rects) this.draw(rect);
    }

    private void project(PlayerEntity player) {
        double half = player.getWidth() / 2.0;
        double height = player.getHeight();

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (int i = 0; i < 8; i++) {
            double x = player.getX() + ((i & 1) == 0 ? -half : half);
            double y = player.getY() + ((i & 2) == 0 ? 0.0 : height);
            double z = player.getZ() + ((i & 4) == 0 ? -half : half);

            Vector3d point = new Vector3d(x, y, z);

            // A corner behind the camera has no screen position; the whole box is dropped rather
            // than drawn with a wrong rectangle.
            if (!NametagUtils.to2D(point, 1.0)) return;

            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }

        double width = maxX - minX;
        double tall = maxY - minY;

        if (width <= 0.0 || tall <= 0.0) return;

        double health = player.getHealth() / Math.max(player.getMaxHealth(), 1.0F);
        this.rects.add(new Rect(minX, minY, width, tall, health));
    }

    private void draw(Rect rect) {
        double minX = rect.left();
        double minY = rect.top();
        double width = rect.width();
        double tall = rect.height();
        double maxX = minX + width;
        double maxY = minY + tall;

        SettingColor colour = this.boxColour.get();

        Renderer2D.COLOR.begin();

        if (this.corners.get()) {
            double length = Math.min(this.cornerLength.get(), Math.min(width, tall) / 2.0);

            // Top left, top right, bottom left, bottom right, two lines each.
            Renderer2D.COLOR.quad(minX, minY, length, 1, colour);
            Renderer2D.COLOR.quad(minX, minY, 1, length, colour);
            Renderer2D.COLOR.quad(maxX - length, minY, length, 1, colour);
            Renderer2D.COLOR.quad(maxX - 1, minY, 1, length, colour);
            Renderer2D.COLOR.quad(minX, maxY - 1, length, 1, colour);
            Renderer2D.COLOR.quad(minX, maxY - length, 1, length, colour);
            Renderer2D.COLOR.quad(maxX - length, maxY - 1, length, 1, colour);
            Renderer2D.COLOR.quad(maxX - 1, maxY - length, 1, length, colour);
        } else {
            Renderer2D.COLOR.quad(minX, minY, width, 1, colour);
            Renderer2D.COLOR.quad(minX, maxY - 1, width, 1, colour);
            Renderer2D.COLOR.quad(minX, minY, 1, tall, colour);
            Renderer2D.COLOR.quad(maxX - 1, minY, 1, tall, colour);
        }

        if (this.healthBar.get()) {
            double ratio = Math.max(0.0, Math.min(1.0, rect.health()));
            double barWidth = Math.max(width, 20.0);
            double left = minX - (barWidth - width) / 2.0;
            double top = minY - 6.0;

            this.start.r = 0;
            this.start.g = 0;
            this.start.b = 0;
            this.start.a = 160;

            Renderer2D.COLOR.quad(left - 1, top - 1, barWidth + 2, 5, this.start);

            SettingColor high = this.healthHigh.get();
            SettingColor low = this.healthLow.get();

            this.end.r = (int) Math.round(low.r + (high.r - low.r) * ratio);
            this.end.g = (int) Math.round(low.g + (high.g - low.g) * ratio);
            this.end.b = (int) Math.round(low.b + (high.b - low.b) * ratio);
            this.end.a = 255;

            Renderer2D.COLOR.quad(left, top, barWidth * ratio, 3, this.end);
        }

        Renderer2D.COLOR.render();
    }
}
