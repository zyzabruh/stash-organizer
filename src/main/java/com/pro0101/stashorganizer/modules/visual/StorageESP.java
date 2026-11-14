package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Boxes every container around the player, one colour per kind.
 *
 * <p>The scan walks the block entities of the loaded chunks rather than every block: a chunk knows
 * its own containers, so the cost is the number of chests in the area instead of 4096 block reads
 * per section. It is redone every {@code interval} ticks, which is fast enough for a container to
 * vanish after it is broken and cheap enough to leave on.
 */
public class StorageESP extends Module {
    /** Container kinds, each with its own colour. */
    private enum Kind {
        Chest,
        Ender,
        Shulker,
        Other
    }

    private record Found(BlockPos pos, Kind kind) {
    }

    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup colours = this.settings.createGroup("Colours");

    private final Setting<Double> range = this.general.add(new DoubleSetting.Builder()
        .name("range")
        .description("Only containers within this distance are boxed.")
        .defaultValue(64.0)
        .range(8.0, 256.0)
        .build()
    );

    private final Setting<Integer> interval = this.general.add(new IntSetting.Builder()
        .name("interval")
        .description("Ticks between two scans. Lower reacts faster and costs more.")
        .defaultValue(10)
        .range(1, 60)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<ShapeMode> shape = this.general.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape")
        .description("Draw the sides, the outline or both.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> labels = this.general.add(new BoolSetting.Builder()
        .name("labels")
        .description("Show the distance to each container above it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> chestColor = this.colours.add(new ColorSetting.Builder()
        .name("chest-colour")
        .description("Chests and trapped chests.")
        .defaultValue(new SettingColor(255, 190, 60, 90))
        .build()
    );

    private final Setting<SettingColor> enderColor = this.colours.add(new ColorSetting.Builder()
        .name("ender-colour")
        .description("Ender chests.")
        .defaultValue(new SettingColor(160, 90, 255, 90))
        .build()
    );

    private final Setting<SettingColor> shulkerColor = this.colours.add(new ColorSetting.Builder()
        .name("shulker-colour")
        .description("Shulker boxes.")
        .defaultValue(new SettingColor(255, 90, 200, 90))
        .build()
    );

    private final Setting<SettingColor> otherColor = this.colours.add(new ColorSetting.Builder()
        .name("other-colour")
        .description("Barrels, hoppers, dispensers, droppers, furnaces and brewing stands.")
        .defaultValue(new SettingColor(120, 200, 255, 90))
        .build()
    );

    private final Setting<SettingColor> lineColor = this.colours.add(new ColorSetting.Builder()
        .name("line-colour")
        .description("Outline of every box.")
        .defaultValue(new SettingColor(255, 255, 255, 200))
        .build()
    );

    private final List<Found> found = new ArrayList<>();
    private final Vector3d point = new Vector3d();

    private int ticks;

    public StorageESP() {
        super(StashOrganizerAddon.VISUALS, "stash-esp", "Boxes chests, shulkers, barrels and the other containers around you.");
    }

    @Override
    public void onActivate() {
        this.found.clear();
        this.ticks = 0;
    }

    @Override
    public void onDeactivate() {
        this.found.clear();
    }

    /**
     * Rescans the area at the configured interval.
     *
     * @param event post-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world == null || this.mc.player == null) return;
        if (++this.ticks < this.interval.get()) return;

        this.ticks = 0;
        this.found.clear();

        double limit = this.range.get();
        int reach = (int) Math.ceil(limit / 16.0);
        ChunkPos centre = this.mc.player.getChunkPos();
        double limitSquared = limit * limit;

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = centre.x + dx;
                int z = centre.z + dz;

                if (!this.mc.world.getChunkManager().isChunkLoaded(x, z)) continue;

                WorldChunk chunk = this.mc.world.getChunk(x, z);
                if (chunk == null) continue;

                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (this.mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > limitSquared) continue;

                    BlockEntity entity = chunk.getBlockEntity(pos);
                    Kind kind = this.kindOf(entity);
                    if (kind != null) this.found.add(new Found(pos.toImmutable(), kind));
                }
            }
        }
    }

    /**
     * Draws the boxes and their labels.
     *
     * @param event world render event
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.player == null || this.found.isEmpty()) return;

        boolean showLabels = this.labels.get();

        for (Found entry : this.found) {
            SettingColor side = this.colourOf(entry.kind());

            event.renderer.box(entry.pos(), side, this.lineColor.get(), this.shape.get(), 0);

            if (showLabels) this.label(event, entry.pos());
        }
    }

    private void label(Render3DEvent event, BlockPos pos) {
        Vec3d eye = this.mc.player.getEyePos();
        double distance = Math.sqrt(pos.getSquaredDistance(eye));

        this.point.set(pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5);

        if (!NametagUtils.to2D(this.point, 1.0)) return;

        TextRenderer font = TextRenderer.get();
        String text = String.format("%.0f m", distance);

        font.begin(1, false, true);
        font.render(text, this.point.x - font.getWidth(text, true) / 2, this.point.y, Color.WHITE, true);
        font.end();
    }

    private SettingColor colourOf(Kind kind) {
        return switch (kind) {
            case Chest -> this.chestColor.get();
            case Ender -> this.enderColor.get();
            case Shulker -> this.shulkerColor.get();
            case Other -> this.otherColor.get();
        };
    }

    /**
     * Classifies a block entity.
     *
     * <p>The order matters: a trapped chest is a chest, and a dropper is a dispenser.
     *
     * @param entity block entity to classify
     * @return its kind, or null when it holds no loot
     */
    private Kind kindOf(BlockEntity entity) {
        if (entity == null) return null;

        if (entity instanceof EnderChestBlockEntity) return Kind.Ender;
        if (entity instanceof ShulkerBoxBlockEntity) return Kind.Shulker;
        if (entity instanceof ChestBlockEntity) return Kind.Chest;

        if (entity instanceof BarrelBlockEntity || entity instanceof HopperBlockEntity
            || entity instanceof DispenserBlockEntity || entity instanceof AbstractFurnaceBlockEntity
            || entity instanceof BrewingStandBlockEntity) {
            return Kind.Other;
        }

        return null;
    }
}
