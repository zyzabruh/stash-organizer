package com.pro0101.stashorganizer.modules.render;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.render.Colours;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
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
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a coloured box around the blocks the user lists, the block shader ESP of utility clients.
 *
 * <p>The blocks are found by scanning loaded chunks rather than by hanging on chunk load events:
 * the list can change at any time, and a scan of one chunk per tick is cheap enough to redo the
 * whole area every few seconds. Chunks are scanned nearest first and their result replaces the
 * previous one, so a chest that was emptied or a vein that was mined disappears on the next sweep
 * instead of staying on screen.
 *
 * <p>The colour is not fixed: the styles below recolour every box from its distance and from the
 * clock, which is what makes the difference between a flat ESP and the animated shader look.
 */
public class BlockShader extends Module {
    /** How the boxes are coloured. */
    public enum Style {
        /** The configured colours, unchanged. */
        Solid,
        /** Hue cycles with time and position. */
        Rainbow,
        /** Fades from the near colour to the far colour with distance. */
        Gradient,
        /** Brightness pulses from each box's distance. */
        Pulse
    }

    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup colours = this.settings.createGroup("Colours");

    private final Setting<List<Block>> blocks = this.general.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks that get a shader box.")
        .defaultValue(
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE, Blocks.ANCIENT_DEBRIS, Blocks.SPAWNER,
            Blocks.CHEST, Blocks.BARREL, Blocks.SHULKER_BOX
        )
        .build()
    );

    private final Setting<Double> range = this.general.add(new DoubleSetting.Builder()
        .name("range")
        .description("Distance the boxes and the chunk scan reach. Zero removes the limit.")
        .defaultValue(64.0)
        .range(0.0, 512.0)
        .build()
    );

    private final Setting<Style> style = this.general.add(new EnumSetting.Builder<Style>()
        .name("style")
        .description("How the boxes are coloured.")
        .defaultValue(Style.Rainbow)
        .build()
    );

    private final Setting<ShapeMode> shape = this.general.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape")
        .description("Draw the sides, the outline or both.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Double> opacity = this.general.add(new DoubleSetting.Builder()
        .name("opacity")
        .description("Multiplier applied to the side colour's alpha.")
        .defaultValue(1.0)
        .range(0.0, 1.0)
        .build()
    );

    private final Setting<Integer> chunksPerTick = this.general.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("Chunks rescanned each tick. Every chunk costs a full 16x16x16 walk, so raise this with care.")
        .defaultValue(2)
        .range(1, 16)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<SettingColor> sideColor = this.colours.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill colour near the camera.")
        .defaultValue(new SettingColor(0, 255, 255, 70))
        .build()
    );

    private final Setting<SettingColor> lineColor = this.colours.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline colour near the camera.")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> farColor = this.colours.add(new ColorSetting.Builder()
        .name("far-color")
        .description("Colour at the far edge of the range, used by the gradient style.")
        .defaultValue(new SettingColor(120, 60, 255, 255))
        .visible(() -> this.style.get() == Style.Gradient)
        .build()
    );

    private final Map<ChunkPos, List<BlockPos>> chunks = new HashMap<>();
    private final List<ChunkPos> queue = new ArrayList<>();
    private final Color side = new Color(0, 0, 0, 0);
    private final Color line = new Color(0, 0, 0, 0);

    private int cursor;
    private ChunkPos centre;
    private double queuedRange = -1.0;

    public BlockShader() {
        super(StashOrganizerAddon.CATEGORY, "block-shader", "Boxes the blocks you list, with animated shader colours.");
    }

    @Override
    public void onActivate() {
        this.clear();
    }

    @Override
    public void onDeactivate() {
        this.clear();
    }

    /**
     * Drops the scan when the world changes, so positions from the old world are not drawn.
     *
     * @param event world leave event
     */
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.clear();
    }

    /**
     * Rescans a few chunks per tick, nearest first.
     *
     * @param event post-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world == null || this.mc.player == null) return;
        if (this.blocks.get().isEmpty()) return;

        this.refill();

        int wanted = this.chunksPerTick.get();
        int count = Math.min(wanted, this.queue.size() - this.cursor);

        for (int i = 0; i < count; i++) this.scan(this.queue.get(this.cursor++));
    }

    /**
     * Draws every box that is still in range.
     *
     * @param event world render event
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.player == null || this.chunks.isEmpty()) return;

        double limit = this.range.get();
        Vec3d eye = this.mc.player.getEyePos();
        double time = System.currentTimeMillis() / 1000.0;

        for (List<BlockPos> positions : this.chunks.values()) {
            for (BlockPos pos : positions) {
                double distance = Math.sqrt(pos.getSquaredDistance(eye));
                if (limit > 0.0 && distance > limit) continue;

                this.colour(pos, distance, limit, time);
                event.renderer.box(pos, this.side, this.line, this.shape.get(), 0);
            }
        }
    }

    /**
     * Rebuilds the scan queue when it is exhausted, the range changed or the player walked away.
     */
    private void refill() {
        ChunkPos here = this.mc.player.getChunkPos();
        boolean moved = this.centre != null
            && (Math.abs(here.x - this.centre.x) > 2 || Math.abs(here.z - this.centre.z) > 2);

        if (!this.queue.isEmpty() && this.cursor < this.queue.size()
            && !moved && this.queuedRange == this.range.get()) {
            return;
        }

        this.queue.clear();
        this.cursor = 0;
        this.centre = here;
        this.queuedRange = this.range.get();

        int reach = this.queuedRange <= 0.0 ? 8 : (int) Math.ceil(this.queuedRange / 16.0);
        int limit = reach * reach;

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz > limit) continue;
                this.queue.add(new ChunkPos(here.x + dx, here.z + dz));
            }
        }

        // Nearest first: the chunks the player is looking at are refreshed before the far ones.
        this.queue.sort((first, second) -> {
            int a = (first.x - here.x) * (first.x - here.x) + (first.z - here.z) * (first.z - here.z);
            int b = (second.x - here.x) * (second.x - here.x) + (second.z - here.z) * (second.z - here.z);
            return Integer.compare(a, b);
        });
    }

    /**
     * Rescans one chunk and replaces its previous result.
     *
     * @param pos chunk to scan
     */
    private void scan(ChunkPos pos) {
        if (this.mc.world == null) return;

        if (!this.mc.world.getChunkManager().isChunkLoaded(pos.x, pos.z)) {
            this.chunks.remove(pos);
            return;
        }

        WorldChunk chunk = this.mc.world.getChunk(pos.x, pos.z);

        if (chunk == null) {
            this.chunks.remove(pos);
            return;
        }

        List<Block> wanted = this.blocks.get();
        List<BlockPos> found = new ArrayList<>();
        int base = this.mc.world.getBottomY();
        ChunkSection[] sections = chunk.getSectionArray();

        for (int index = 0; index < sections.length; index++) {
            ChunkSection section = sections[index];
            if (section == null || section.isEmpty()) continue;

            int origin = base + index * 16;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        if (!wanted.contains(state.getBlock())) continue;

                        found.add(new BlockPos(pos.getStartX() + x, origin + y, pos.getStartZ() + z));
                    }
                }
            }
        }

        if (found.isEmpty()) this.chunks.remove(pos);
        else this.chunks.put(pos, found);
    }

    /**
     * Fills the reusable colours for one box.
     *
     * @param pos block being drawn
     * @param distance distance from the camera
     * @param limit configured range, zero when unlimited
     * @param time seconds, for the animated styles
     */
    private void colour(BlockPos pos, double distance, double limit, double time) {
        SettingColor near = this.sideColor.get();
        SettingColor stroke = this.lineColor.get();
        float ratio = limit > 0.0 ? (float) Math.min(1.0, distance / limit) : 0.0F;

        switch (this.style.get()) {
            case Solid -> {
                this.side.r = near.r;
                this.side.g = near.g;
                this.side.b = near.b;
                this.line.r = stroke.r;
                this.line.g = stroke.g;
                this.line.b = stroke.b;
            }
            case Gradient -> {
                SettingColor far = this.farColor.get();
                this.side.r = this.mix(near.r, far.r, ratio);
                this.side.g = this.mix(near.g, far.g, ratio);
                this.side.b = this.mix(near.b, far.b, ratio);
                this.line.r = this.mix(stroke.r, far.r, ratio);
                this.line.g = this.mix(stroke.g, far.g, ratio);
                this.line.b = this.mix(stroke.b, far.b, ratio);
            }
            case Rainbow -> {
                float hue = (float) ((time * 0.15 + pos.getX() * 0.02 + pos.getY() * 0.01 + pos.getZ() * 0.02) % 1.0);
                if (hue < 0.0F) hue += 1.0F;

                int rgb = Colours.hsv(hue);
                this.side.r = (rgb >> 16) & 0xFF;
                this.side.g = (rgb >> 8) & 0xFF;
                this.side.b = rgb & 0xFF;
                this.line.r = this.side.r;
                this.line.g = this.side.g;
                this.line.b = this.side.b;
            }
            case Pulse -> {
                float pulse = (float) (0.6 + 0.4 * Math.sin(time * 3.0 - distance * 0.25));
                this.side.r = Math.round(near.r * pulse);
                this.side.g = Math.round(near.g * pulse);
                this.side.b = Math.round(near.b * pulse);
                this.line.r = Math.round(stroke.r * pulse);
                this.line.g = Math.round(stroke.g * pulse);
                this.line.b = Math.round(stroke.b * pulse);
            }
        }

        this.side.a = Math.round(near.a * this.opacity.get().floatValue());
        this.line.a = stroke.a;
    }

    private int mix(int from, int to, float ratio) {
        return Math.round(from + (to - from) * ratio);
    }

    private void clear() {
        this.chunks.clear();
        this.queue.clear();
        this.cursor = 0;
        this.centre = null;
        this.queuedRange = -1.0;
    }
}
