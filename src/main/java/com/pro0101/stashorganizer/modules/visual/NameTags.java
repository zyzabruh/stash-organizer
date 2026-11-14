package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws a custom name tag above other players.
 *
 * <p>The vanilla tag is left alone; this one is added on top, at a higher position, and it carries
 * what the vanilla one cannot: the health number, the distance, and the items the player is holding.
 * The text is drawn in screen space after a projection, so it stays readable at any distance instead
 * of shrinking with the model.
 */
public class NameTags extends Module {
    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup colours = this.settings.createGroup("Colours");

    private final Setting<Double> range = this.general.add(new DoubleSetting.Builder()
        .name("range")
        .description("Only players within this distance get a tag.")
        .defaultValue(64.0)
        .range(8.0, 256.0)
        .build()
    );

    private final Setting<Integer> limit = this.general.add(new IntSetting.Builder()
        .name("limit")
        .description("Maximum number of tags drawn, nearest first.")
        .defaultValue(16)
        .range(1, 128)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Double> scale = this.general.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale multiplier.")
        .defaultValue(1.0)
        .range(0.5, 3.0)
        .build()
    );

    private final Setting<Boolean> health = this.general.add(new BoolSetting.Builder()
        .name("health")
        .description("Print the health number.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> distance = this.general.add(new BoolSetting.Builder()
        .name("distance")
        .description("Print the distance in metres.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hands = this.general.add(new BoolSetting.Builder()
        .name("hands")
        .description("Print the items held in both hands.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> armour = this.general.add(new BoolSetting.Builder()
        .name("armour")
        .description("Print the four armour pieces.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> nameColor = this.colours.add(new ColorSetting.Builder()
        .name("name-colour")
        .description("Player name.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> infoColor = this.colours.add(new ColorSetting.Builder()
        .name("info-colour")
        .description("Health, distance and equipment lines.")
        .defaultValue(new SettingColor(180, 220, 255))
        .build()
    );

    public NameTags() {
        super(StashOrganizerAddon.VISUALS, "name-tags", "Adds health, distance and held items to the name tags of other players.");
    }

    /**
     * Draws one tag per visible player.
     *
     * @param event world render event
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
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
            this.draw(player);
        }
    }

    private void draw(PlayerEntity player) {
        List<String> lines = new ArrayList<>();
        lines.add(player.getName().getString());

        double health = player.getHealth();
        double metres = Math.sqrt(player.squaredDistanceTo(this.mc.player));

        StringBuilder info = new StringBuilder();

        if (this.health.get()) info.append(String.format("%.1f hp", health));
        if (this.distance.get()) {
            if (!info.isEmpty()) info.append("  ");
            info.append(String.format("%.0f m", metres));
        }

        if (!info.isEmpty()) lines.add(info.toString());

        if (this.hands.get()) {
            addStack(lines, player.getMainHandStack());
            addStack(lines, player.getOffHandStack());
        }

        if (this.armour.get()) {
            for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
            }) {
                addStack(lines, player.getEquippedStack(slot));
            }
        }

        Vector3d point = new Vector3d(player.getX(), player.getBoundingBox().maxY + 0.55, player.getZ());

        if (!NametagUtils.to2D(point, 1.0)) return;

        TextRenderer font = TextRenderer.get();
        double scale = this.scale.get();

        font.begin(scale, false, true);

        double lineHeight = font.getHeight(true);
        double y = point.y - lines.size() * lineHeight / 2;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Color color = i == 0 ? this.nameColor.get() : this.infoColor.get();

            font.render(line, point.x - font.getWidth(line, true) / 2, y, color, true);

            y += lineHeight;
        }

        font.end();
    }

    private static void addStack(List<String> lines, ItemStack stack) {
        if (stack.isEmpty()) return;

        String name = stack.getName().getString();
        if (stack.getCount() > 1) name += " x" + stack.getCount();

        lines.add(name);
    }
}
