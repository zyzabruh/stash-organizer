package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Highlights unusual fluid flows that often reveal player bases.
 *
 * <p>In the Nether it looks for lava above the lava ocean, and in the Overworld for water below sea
 * level. A flow is a connected group of fluid blocks, not a single block: the scan only keeps a
 * group when it contains at least one moving block, so a still ocean or lake does not fill the
 * screen. Player water streams, water drops and lava casts are moving at their edges and are kept.
 *
 * <p>Every flow is drawn as a box and as a vertical column. The colour is chosen by fluid, and an
 * optional gradient fades fresh flows to a mature colour by age, by flow length, or by both. Flows
 * are cached per world on disk and stay after their chunk unloads, so turning the module off and on
 * does not lose a find.
 */
public class FlowESP extends Module {
    /** The fluid a flow is made of. */
    public enum Kind {
        Water,
        Lava
    }

    /** What the maturity gradient uses. */
    public enum Gradient {
        None,
        Age,
        Length,
        Both
    }

    /** What is drawn for a flow. */
    public enum Draw {
        Box,
        Column,
        Both
    }

    /** Safety bound for one flood fill, so a flow running into an ocean cannot fill memory. */
    private static final int MAX_COMPONENT = 8192;

    /** One connected fluid flow. */
    private static class Flow {
        private final String dimension;
        private final int chunkX;
        private final int chunkZ;
        private final Kind kind;
        private final int index;
        private final long firstSeen;

        private int minX;
        private int minY;
        private int minZ;
        private int maxX;
        private int maxY;
        private int maxZ;
        private int size;
        private long lastSeen;

        private Flow(String dimension, int chunkX, int chunkZ, Kind kind, int index, long firstSeen) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.kind = kind;
            this.index = index;
            this.firstSeen = firstSeen;
        }

        private String key() {
            return FlowESP.key(this.dimension, this.chunkX, this.chunkZ, this.kind, this.index);
        }

        private void update(Component component, long now) {
            this.minX = component.minX;
            this.minY = component.minY;
            this.minZ = component.minZ;
            this.maxX = component.maxX;
            this.maxY = component.maxY;
            this.maxZ = component.maxZ;
            this.size = component.size;
            this.lastSeen = now;
        }

        private double centerX() {
            return (this.minX + this.maxX + 1) / 2.0;
        }

        private double centerZ() {
            return (this.minZ + this.maxZ + 1) / 2.0;
        }
    }

    /** A connected group found during one chunk scan. */
    private record Component(
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        int size, boolean flowing
    ) {
    }

    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup render = this.settings.createGroup("Render");
    private final SettingGroup colours = this.settings.createGroup("Colours");
    private final SettingGroup cache = this.settings.createGroup("Cache");

    private final Setting<Integer> range = this.general.add(new IntSetting.Builder()
        .name("range")
        .description("How many blocks around you are scanned.")
        .defaultValue(80)
        .range(16, 256)
        .sliderRange(16, 192)
        .build()
    );

    private final Setting<Integer> interval = this.general.add(new IntSetting.Builder()
        .name("interval")
        .description("Ticks between two scans. Lower finds flows sooner and costs more.")
        .defaultValue(40)
        .range(10, 200)
        .sliderRange(10, 100)
        .build()
    );

    private final Setting<Integer> minFlow = this.general.add(new IntSetting.Builder()
        .name("min-flow")
        .description("Smallest connected flow, in fluid blocks, that is kept.")
        .defaultValue(4)
        .range(1, 64)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Integer> netherY = this.general.add(new IntSetting.Builder()
        .name("nether-ocean-y")
        .description("Lava at or below this Nether height is the normal lava ocean and is ignored.")
        .defaultValue(31)
        .range(-64, 127)
        .sliderRange(0, 64)
        .build()
    );

    private final Setting<Integer> seaLevel = this.general.add(new IntSetting.Builder()
        .name("sea-level")
        .description("Water at or above this Overworld height is normal sea water and is ignored.")
        .defaultValue(63)
        .range(-64, 320)
        .sliderRange(0, 128)
        .build()
    );

    private final Setting<Boolean> scanNether = this.general.add(new BoolSetting.Builder()
        .name("scan-nether")
        .description("Look for lava above the Nether ocean.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> scanOverworld = this.general.add(new BoolSetting.Builder()
        .name("scan-overworld")
        .description("Look for water below the Overworld sea level.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireFlowing = this.general.add(new BoolSetting.Builder()
        .name("require-flowing")
        .description("Keep only flows with at least one moving block. Turning this off also shows still seas and lakes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> draw = this.render.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw the flows that were found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> renderDistance = this.render.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("How far from you a flow is still drawn.")
        .defaultValue(128.0)
        .range(16.0, 512.0)
        .sliderRange(16.0, 256.0)
        .visible(this.draw::get)
        .build()
    );

    private final Setting<ShapeMode> shape = this.render.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape")
        .description("Draw the sides, the outline or both.")
        .defaultValue(ShapeMode.Both)
        .visible(this.draw::get)
        .build()
    );

    private final Setting<Draw> drawMode = this.render.add(new EnumSetting.Builder<Draw>()
        .name("draw-mode")
        .description("Draw the flow bounds, a column at its centre, or both.")
        .defaultValue(Draw.Both)
        .visible(this.draw::get)
        .build()
    );

    private final Setting<Boolean> tracers = this.render.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw a line from the camera to each flow.")
        .defaultValue(true)
        .visible(this.draw::get)
        .build()
    );

    private final Setting<Boolean> nearestTracer = this.render.add(new BoolSetting.Builder()
        .name("nearest-only")
        .description("Draw only the tracer to the closest flow.")
        .defaultValue(false)
        .visible(() -> this.draw.get() && this.tracers.get())
        .build()
    );

    private final Setting<Double> tracerDistance = this.render.add(new DoubleSetting.Builder()
        .name("tracer-distance")
        .description("How far from you a tracer is still drawn.")
        .defaultValue(128.0)
        .range(16.0, 512.0)
        .sliderRange(16.0, 256.0)
        .visible(() -> this.draw.get() && this.tracers.get())
        .build()
    );

    private final Setting<Boolean> fade = this.render.add(new BoolSetting.Builder()
        .name("fade")
        .description("Fade a flow in when it is found and out at the edge of the render distance.")
        .defaultValue(true)
        .visible(this.draw::get)
        .build()
    );

    private final Setting<Double> fadeDistance = this.render.add(new DoubleSetting.Builder()
        .name("fade-distance")
        .description("Length of the fade at the edge of the render distance.")
        .defaultValue(24.0)
        .range(4.0, 128.0)
        .sliderRange(4.0, 64.0)
        .visible(() -> this.draw.get() && this.fade.get())
        .build()
    );

    private final Setting<SettingColor> waterColor = this.colours.add(new ColorSetting.Builder()
        .name("water-colour")
        .description("Colour of a water flow.")
        .defaultValue(new SettingColor(65, 145, 255, 70))
        .build()
    );

    private final Setting<SettingColor> lavaColor = this.colours.add(new ColorSetting.Builder()
        .name("lava-colour")
        .description("Colour of a lava flow.")
        .defaultValue(new SettingColor(255, 110, 30, 70))
        .build()
    );

    private final Setting<SettingColor> lineColor = this.colours.add(new ColorSetting.Builder()
        .name("line-colour")
        .description("Outline and tracer colour.")
        .defaultValue(new SettingColor(255, 255, 255, 190))
        .build()
    );

    private final Setting<Gradient> gradient = this.colours.add(new EnumSetting.Builder<Gradient>()
        .name("gradient")
        .description("Fade fresh flows to the mature colour by age, by length, or by both.")
        .defaultValue(Gradient.None)
        .build()
    );

    private final Setting<SettingColor> matureColor = this.colours.add(new ColorSetting.Builder()
        .name("mature-colour")
        .description("Colour a flow reaches when it is fully mature.")
        .defaultValue(new SettingColor(190, 190, 190, 50))
        .visible(() -> this.gradient.get() != Gradient.None)
        .build()
    );

    private final Setting<Integer> gradientAge = this.colours.add(new IntSetting.Builder()
        .name("gradient-age")
        .description("Seconds a flow takes to become mature by age.")
        .defaultValue(120)
        .range(5, 3600)
        .sliderRange(5, 600)
        .visible(() -> this.gradient.get() == Gradient.Age || this.gradient.get() == Gradient.Both)
        .build()
    );

    private final Setting<Integer> gradientLength = this.colours.add(new IntSetting.Builder()
        .name("gradient-length")
        .description("Flow size, in blocks, at which the length gradient is fully mature.")
        .defaultValue(16)
        .range(2, 512)
        .sliderRange(2, 64)
        .visible(() -> this.gradient.get() == Gradient.Length || this.gradient.get() == Gradient.Both)
        .build()
    );

    private final Setting<Integer> maxFlows = this.cache.add(new IntSetting.Builder()
        .name("max-flows")
        .description("Upper bound on remembered flows. The oldest are dropped first.")
        .defaultValue(8192)
        .range(128, 65536)
        .sliderRange(128, 16384)
        .build()
    );

    private final Setting<Boolean> save = this.cache.add(new BoolSetting.Builder()
        .name("save")
        .description("Write discovered flows to disk when the module is disabled or you leave the world.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> load = this.cache.add(new BoolSetting.Builder()
        .name("load")
        .description("Load previously discovered flows when the module is enabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clearOnDisable = this.cache.add(new BoolSetting.Builder()
        .name("clear-on-disable")
        .description("Drop the flows from memory when the module is disabled. The saved file is kept.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> clearOnWorldChange = this.cache.add(new BoolSetting.Builder()
        .name("clear-on-world-change")
        .description("Drop the flows from memory when you leave the world or change dimension. The saved file is kept.")
        .defaultValue(false)
        .build()
    );

    private final Map<String, Flow> flows = new HashMap<>();
    private final BlockPos.Mutable mutable = new BlockPos.Mutable();

    private String lastDimension;
    private int ticks;
    private int saveTicks;

    public FlowESP() {
        super(StashOrganizerAddon.CATEGORY, "flow-esp", "Marks unusual lava and water flows that reveal player bases.");
    }

    @Override
    public void onActivate() {
        this.flows.clear();
        if (this.mc.world != null && this.load.get()) this.load();

        this.lastDimension = this.mc.world == null ? null : this.dimension();
        this.ticks = 0;
        this.saveTicks = 0;
    }

    @Override
    public void onDeactivate() {
        if (this.save.get()) this.save();
        if (this.clearOnDisable.get()) this.flows.clear();
    }

    /**
     * Scans the loaded chunks around the player and saves the cache now and then.
     *
     * @param event post-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world == null || this.mc.player == null) return;

        String dimension = this.dimension();

        if (this.lastDimension == null) {
            this.lastDimension = dimension;
        } else if (!this.lastDimension.equals(dimension)) {
            this.lastDimension = dimension;
            if (this.clearOnWorldChange.get()) this.flows.clear();
        }

        if (this.save.get() && ++this.saveTicks >= 600) {
            this.saveTicks = 0;
            this.save();
        }

        if (++this.ticks < this.interval.get()) return;
        this.ticks = 0;

        boolean nether = dimension.equals(World.NETHER.getValue().toString());
        boolean overworld = dimension.equals(World.OVERWORLD.getValue().toString());

        if (nether && !this.scanNether.get()) return;
        if (overworld && !this.scanOverworld.get()) return;
        if (!nether && !overworld) return;

        int radius = (int) Math.ceil(this.range.get() / 16.0);
        ChunkPos centre = this.mc.player.getChunkPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centre.x + dx;
                int chunkZ = centre.z + dz;

                if (!this.mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) continue;

                WorldChunk chunk = this.mc.world.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                this.scanChunk(chunk, chunkX, chunkZ, dimension, nether);
            }
        }

        this.trim();
    }

    /**
     * Finds every connected flow in one chunk and replaces that chunk's old records.
     *
     * @param chunk chunk to scan
     * @param chunkX chunk x
     * @param chunkZ chunk z
     * @param dimension dimension id
     * @param nether whether this is the Nether
     */
    private void scanChunk(WorldChunk chunk, int chunkX, int chunkZ, String dimension, boolean nether) {
        Kind kind = nether ? Kind.Lava : Kind.Water;
        TagKey<Fluid> tag = nether ? FluidTags.LAVA : FluidTags.WATER;

        int bottom = this.mc.world.getBottomY();
        int top = this.mc.world.getTopYInclusive();
        int seedFromY = nether ? Math.max(bottom, this.netherY.get() + 1) : bottom;
        int seedToY = nether ? top : Math.min(top, this.seaLevel.get() - 1);
        int scanFromY = seedFromY;
        int scanToY = top;

        if (seedFromY > seedToY) {
            this.removeChunkFlows(dimension, chunkX, chunkZ, kind);
            return;
        }

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        boolean flowingOnly = this.requireFlowing.get();
        Set<BlockPos> seeds = new HashSet<>();

        // The seeds are the moving blocks. Starting from them instead of every fluid block keeps
        // a still ocean out of memory, and the flood fill below still finds the source blocks that
        // belong to the same stream.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = seedFromY; y <= seedToY; y++) {
                    FluidState fluid = this.fluidAt(chunk, startX + x, y, startZ + z, tag);
                    if (fluid == null) continue;
                    if (flowingOnly && fluid.isStill()) continue;

                    seeds.add(new BlockPos(startX + x, y, startZ + z));
                }
            }
        }

        List<Component> components = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        for (BlockPos start : seeds) {
            if (!visited.add(start)) continue;

            queue.add(start);

            int minX = start.getX();
            int minY = start.getY();
            int minZ = start.getZ();
            int maxX = minX;
            int maxY = minY;
            int maxZ = minZ;
            int size = 0;
            boolean flowing = false;

            while (!queue.isEmpty() && size < MAX_COMPONENT) {
                BlockPos pos = queue.poll();
                size++;
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());

                FluidState fluid = this.fluidAt(chunk, pos.getX(), pos.getY(), pos.getZ(), tag);
                if (fluid != null && !fluid.isStill()) flowing = true;

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue;

                            int x = pos.getX() + dx;
                            int y = pos.getY() + dy;
                            int z = pos.getZ() + dz;

                            if (x < startX || x >= startX + 16) continue;
                            if (z < startZ || z >= startZ + 16) continue;
                            if (y < scanFromY || y > scanToY) continue;
                            if (this.fluidAt(chunk, x, y, z, tag) == null) continue;

                            BlockPos next = new BlockPos(x, y, z);
                            if (visited.add(next)) queue.add(next);
                        }
                    }
                }
            }

            queue.clear();

            if (size >= this.minFlow.get() && (!flowingOnly || flowing)) {
                components.add(new Component(minX, minY, minZ, maxX, maxY, maxZ, size, flowing));
            }
        }

        components.sort(Comparator
            .comparingInt(Component::minX)
            .thenComparingInt(Component::minZ)
            .thenComparingInt(Component::minY));

        long now = System.currentTimeMillis();
        String prefix = keyPrefix(dimension, chunkX, chunkZ, kind);

        for (int i = 0; i < components.size(); i++) {
            Component component = components.get(i);
            String key = prefix + i;
            Flow flow = this.flows.get(key);

            if (flow == null) {
                flow = new Flow(dimension, chunkX, chunkZ, kind, i, now);
            }

            flow.update(component, now);
            this.flows.put(key, flow);
        }

        this.flows.keySet().removeIf(key -> {
            if (!key.startsWith(prefix)) return false;
            try {
                return Integer.parseInt(key.substring(prefix.length())) >= components.size();
            } catch (NumberFormatException ignored) {
                return true;
            }
        });
    }

    /**
     * Tests one block for the fluid being scanned.
     *
     * @param chunk chunk holding the block
     * @param x world x
     * @param y world y
     * @param z world z
     * @param tag fluid tag to match
     * @return true when the block holds that fluid
     */
    private FluidState fluidAt(WorldChunk chunk, int x, int y, int z, TagKey<Fluid> tag) {
        BlockState state = chunk.getBlockState(this.mutable.set(x, y, z));
        FluidState fluid = state.getFluidState();

        return !fluid.isEmpty() && fluid.isIn(tag) ? fluid : null;
    }

    /**
     * Draws the cached flows and their columns.
     *
     * @param event render event
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!this.draw.get() || this.mc.world == null || this.mc.player == null || this.flows.isEmpty()) return;

        String dimension = this.dimension();
        double limit = this.renderDistance.get();
        double limitSquared = limit * limit;
        double tracerLimit = this.tracerDistance.get();
        double tracerSquared = tracerLimit * tracerLimit;
        long now = System.currentTimeMillis();

        Flow nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        if (this.tracers.get() && this.nearestTracer.get()) {
            for (Flow flow : this.flows.values()) {
                if (!flow.dimension.equals(dimension)) continue;

                double dx = flow.centerX() - this.mc.player.getX();
                double dy = (flow.minY + flow.maxY) / 2.0 - this.mc.player.getY();
                double dz = flow.centerZ() - this.mc.player.getZ();
                double distance = dx * dx + dy * dy + dz * dz;

                if (distance <= tracerSquared && distance < nearestDistance) {
                    nearest = flow;
                    nearestDistance = distance;
                }
            }
        }

        for (Flow flow : this.flows.values()) {
            if (!flow.dimension.equals(dimension)) continue;

            double centerX = flow.centerX();
            double centerY = (flow.minY + flow.maxY + 1) / 2.0;
            double centerZ = flow.centerZ();
            double dx = centerX - this.mc.player.getX();
            double dy = centerY - this.mc.player.getY();
            double dz = centerZ - this.mc.player.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;

            if (distanceSquared > limitSquared) continue;

            double visibility = this.visibility(flow, distanceSquared, limit, now);
            Color side = this.colour(flow, now, visibility);
            Color line = this.colour(this.lineColor.get(), visibility);

            Box box = new Box(
                flow.minX, flow.minY, flow.minZ,
                flow.maxX + 1, flow.maxY + 1, flow.maxZ + 1
            );

            if (this.drawMode.get() == Draw.Box || this.drawMode.get() == Draw.Both) {
                event.renderer.box(box, side, line, this.shape.get(), 0);
            }

            if (this.drawMode.get() == Draw.Column || this.drawMode.get() == Draw.Both) {
                event.renderer.box(
                    centerX - 0.5, flow.minY, centerZ - 0.5,
                    centerX + 0.5, flow.maxY + 1, centerZ + 0.5,
                    side, line, this.shape.get(), 0
                );
            }

            if (!this.tracers.get()) continue;
            if (this.nearestTracer.get() && flow != nearest) continue;
            if (distanceSquared > tracerSquared) continue;

            event.renderer.line(
                RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                centerX, flow.maxY + 0.5, centerZ, line
            );
        }
    }

    /**
     * Returns the alpha multiplier for a flow at a given distance.
     *
     * @param flow flow being drawn
     * @param distanceSquared squared distance from the player
     * @param limit render distance
     * @param now current time
     * @return multiplier between zero and one
     */
    private double visibility(Flow flow, double distanceSquared, double limit, long now) {
        if (!this.fade.get()) return 1.0;

        double visibility = Math.min(1.0, (now - flow.firstSeen) / 400.0);
        double distance = Math.sqrt(distanceSquared);
        double fadeStart = Math.max(0.0, limit - this.fadeDistance.get());

        if (distance > fadeStart) {
            visibility *= Math.max(0.0, (limit - distance) / Math.max(1.0, limit - fadeStart));
        }

        return visibility;
    }

    /**
     * Picks the side colour for a flow, applying the maturity gradient when enabled.
     *
     * @param flow flow being drawn
     * @param now current time
     * @param visibility alpha multiplier
     * @return colour to draw
     */
    private Color colour(Flow flow, long now, double visibility) {
        SettingColor base = flow.kind == Kind.Water ? this.waterColor.get() : this.lavaColor.get();
        int r = base.r;
        int g = base.g;
        int b = base.b;
        int a = base.a;

        if (this.gradient.get() != Gradient.None) {
            double fraction = this.maturity(flow, now);
            SettingColor mature = this.matureColor.get();

            r = (int) Math.round(r + (mature.r - r) * fraction);
            g = (int) Math.round(g + (mature.g - g) * fraction);
            b = (int) Math.round(b + (mature.b - b) * fraction);
            a = (int) Math.round(a + (mature.a - a) * fraction);
        }

        return new Color(r, g, b, clamp((int) Math.round(a * visibility)));
    }

    /**
     * Blends an existing colour with the visibility multiplier.
     *
     * @param setting source colour
     * @param visibility alpha multiplier
     * @return faded colour
     */
    private Color colour(SettingColor setting, double visibility) {
        return new Color(setting.r, setting.g, setting.b, clamp((int) Math.round(setting.a * visibility)));
    }

    /**
     * Returns how mature a flow is, between zero and one.
     *
     * @param flow flow being measured
     * @param now current time
     * @return maturity fraction
     */
    private double maturity(Flow flow, long now) {
        double age = clamp01((now - flow.firstSeen) / (this.gradientAge.get() * 1000.0));
        double length = clamp01((flow.size - this.minFlow.get())
            / (double) Math.max(1, this.gradientLength.get() - this.minFlow.get()));

        return switch (this.gradient.get()) {
            case Age -> age;
            case Length -> length;
            case Both -> (age + length) / 2.0;
            case None -> 0.0;
        };
    }

    /** Removes every remembered flow for a chunk and kind. */
    private void removeChunkFlows(String dimension, int chunkX, int chunkZ, Kind kind) {
        this.flows.keySet().removeIf(key -> key.startsWith(keyPrefix(dimension, chunkX, chunkZ, kind)));
    }

    /** Drops the least recently seen flows when the cache grows past its bound. */
    private void trim() {
        int limit = this.maxFlows.get();
        if (this.flows.size() <= limit) return;

        List<Flow> sorted = new ArrayList<>(this.flows.values());
        sorted.sort(Comparator.comparingLong(flow -> flow.lastSeen));

        int remove = this.flows.size() - limit;
        for (int i = 0; i < remove; i++) this.flows.remove(sorted.get(i).key());
    }

    /** Returns the current dimension id. */
    private String dimension() {
        return this.mc.world.getRegistryKey().getValue().toString();
    }

    private static String keyPrefix(String dimension, int chunkX, int chunkZ, Kind kind) {
        return dimension + "|" + chunkX + "|" + chunkZ + "|" + kind + "|";
    }

    private static String key(String dimension, int chunkX, int chunkZ, Kind kind, int index) {
        return keyPrefix(dimension, chunkX, chunkZ, kind) + index;
    }

    /** Directory holding the flow cache for the current world. */
    private File file() {
        return new File(new File(new File(MeteorClient.FOLDER, "flow-esp"), Utils.getFileWorldName()), "flows.txt");
    }

    /** Writes every cached flow to disk. */
    private void save() {
        if (this.mc.world == null) return;

        try {
            File file = this.file();
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();

            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                for (Flow flow : this.flows.values()) {
                    writer.write(flow.dimension);
                    writer.write('|');
                    writer.write(Integer.toString(flow.chunkX));
                    writer.write('|');
                    writer.write(Integer.toString(flow.chunkZ));
                    writer.write('|');
                    writer.write(flow.kind.name());
                    writer.write('|');
                    writer.write(Integer.toString(flow.index));
                    writer.write('|');
                    writer.write(Integer.toString(flow.minX));
                    writer.write('|');
                    writer.write(Integer.toString(flow.minY));
                    writer.write('|');
                    writer.write(Integer.toString(flow.minZ));
                    writer.write('|');
                    writer.write(Integer.toString(flow.maxX));
                    writer.write('|');
                    writer.write(Integer.toString(flow.maxY));
                    writer.write('|');
                    writer.write(Integer.toString(flow.maxZ));
                    writer.write('|');
                    writer.write(Integer.toString(flow.size));
                    writer.write('|');
                    writer.write(Long.toString(flow.firstSeen));
                    writer.newLine();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // A cache file that cannot be written must not break the module.
        }
    }

    /** Reads the cache written by a previous run. */
    private void load() {
        File file;

        try {
            file = this.file();
        } catch (RuntimeException ignored) {
            return;
        }

        if (!file.exists()) return;

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length != 13) continue;

                try {
                    String dimension = parts[0];
                    int chunkX = Integer.parseInt(parts[1]);
                    int chunkZ = Integer.parseInt(parts[2]);
                    Kind kind = Kind.valueOf(parts[3]);
                    int index = Integer.parseInt(parts[4]);
                    int minX = Integer.parseInt(parts[5]);
                    int minY = Integer.parseInt(parts[6]);
                    int minZ = Integer.parseInt(parts[7]);
                    int maxX = Integer.parseInt(parts[8]);
                    int maxY = Integer.parseInt(parts[9]);
                    int maxZ = Integer.parseInt(parts[10]);
                    int size = Integer.parseInt(parts[11]);
                    long firstSeen = Long.parseLong(parts[12]);

                    Flow flow = new Flow(dimension, chunkX, chunkZ, kind, index, firstSeen);
                    flow.update(new Component(minX, minY, minZ, maxX, maxY, maxZ, size, true), System.currentTimeMillis());
                    this.flows.put(flow.key(), flow);
                } catch (IllegalArgumentException ignored) {
                    // Skip a malformed record instead of discarding the rest of the cache.
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // A missing or unreadable cache simply starts empty.
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public String getInfoString() {
        return Integer.toString(this.flows.size());
    }

    /**
     * Saves the cache when the connection ends.
     *
     * @param event game left event
     */
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (this.save.get()) this.save();
        if (this.clearOnWorldChange.get()) this.flows.clear();
        this.lastDimension = null;
    }
}
