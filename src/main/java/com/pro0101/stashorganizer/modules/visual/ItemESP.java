package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Boxes dropped items and prints what they are.
 *
 * <p>Items are read straight from the entity list each frame: there are few of them and they move,
 * so caching would only add a frame of lag. The label carries the stack size when there is more than
 * one, and the distance in metres.
 */
public class ItemESP extends Module {
    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup colours = this.settings.createGroup("Colours");

    private final Setting<Double> range = this.general.add(new DoubleSetting.Builder()
        .name("range")
        .description("Only items within this distance are shown.")
        .defaultValue(48.0)
        .range(8.0, 256.0)
        .build()
    );

    private final Setting<Integer> limit = this.general.add(new IntSetting.Builder()
        .name("limit")
        .description("Maximum number of items drawn, nearest first.")
        .defaultValue(32)
        .range(1, 256)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<ShapeMode> shape = this.general.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape")
        .description("Draw the sides, the outline or both.")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final Setting<Boolean> labels = this.general.add(new BoolSetting.Builder()
        .name("labels")
        .description("Show the item name and its distance.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = this.colours.add(new ColorSetting.Builder()
        .name("side-colour")
        .description("Fill colour of the box.")
        .defaultValue(new SettingColor(255, 255, 255, 40))
        .build()
    );

    private final Setting<SettingColor> lineColor = this.colours.add(new ColorSetting.Builder()
        .name("line-colour")
        .description("Outline colour of the box.")
        .defaultValue(new SettingColor(255, 255, 255, 190))
        .build()
    );

    public ItemESP() {
        super(StashOrganizerAddon.VISUALS, "item-esp", "Boxes dropped items and labels them.");
    }

    /**
     * Draws the nearest items, one box and label each.
     *
     * @param event world render event
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.world == null || this.mc.player == null) return;

        double limitDistance = this.range.get();
        List<ItemEntity> items = new ArrayList<>();

        for (ItemEntity item : this.mc.world.getEntitiesByClass(ItemEntity.class, this.mc.player.getBoundingBox().expand(limitDistance), candidate -> true)) {
            if (item.squaredDistanceTo(this.mc.player) <= limitDistance * limitDistance) items.add(item);
        }

        items.sort(Comparator.comparingDouble(item -> item.squaredDistanceTo(this.mc.player)));

        int drawn = 0;

        for (ItemEntity item : items) {
            if (drawn++ >= this.limit.get()) break;

            event.renderer.box(item.getBoundingBox(), this.sideColor.get(), this.lineColor.get(), this.shape.get(), 0);

            if (this.labels.get()) this.label(item);
        }
    }

    private void label(ItemEntity entity) {
        ItemStack stack = entity.getStack();
        if (stack.isEmpty()) return;

        String name = stack.getName().getString();
        if (stack.getCount() > 1) name += " x" + stack.getCount();

        double distance = Math.sqrt(entity.squaredDistanceTo(this.mc.player));
        String text = name + String.format("  %.0f m", distance);

        Vector3d point = new Vector3d(entity.getX(), entity.getBoundingBox().maxY + 0.35, entity.getZ());

        if (!NametagUtils.to2D(point, 1.0)) return;

        TextRenderer font = TextRenderer.get();

        font.begin(1, false, true);
        font.render(text, point.x - font.getWidth(text, true) / 2, point.y, Color.WHITE, true);
        font.end();
    }
}
