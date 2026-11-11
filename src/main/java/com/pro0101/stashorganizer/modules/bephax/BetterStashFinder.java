package com.pro0101.stashorganizer.modules.bephax;

import com.pro0101.stashorganizer.util.WebhookQueue;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.lenni0451.lambdaevents.EventHandler;
//import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import xaero.common.minimap.waypoints.Waypoint;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;

import java.io.*;
import java.util.*;


public class BetterStashFinder extends Module
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWaypoints = settings.createGroup("Waypoints");
    private final SettingGroup sgNotify = settings.createGroup("Notifications");
    private final SettingGroup sgBlocks = settings.createGroup("Block Lists");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgCache = settings.createGroup("Cache");

    public List<Chunk> chunks = new ArrayList<>();

    private final Setting<List<BlockEntityType<?>>> storageBlocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("storage-blocks")
        .description("Select the storage blocks to search for.")
        .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
        .build()
    );

    private final Setting<Integer> minimumStorageCount = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-storage-count")
        .description("The minimum amount of storage blocks in a chunk to record the chunk.")
        .defaultValue(4)
        .min(1)
        .sliderMin(1)
        .build()
    );

    private final Setting<Boolean> shulkerInstantHit = sgGeneral.add(new BoolSetting.Builder()
        .name("shulker-instant-hit")
        .description("If a single shulker counts as a stash.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> minimumDistance = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-distance")
        .description("The minimum distance you must be from spawn to record a certain chunk.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Boolean> onlyOldchunks = sgGeneral.add(new BoolSetting.Builder()
        .name("only-old-chunks")
        .description("Checks that the chunks it scans have already been loaded.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> saveToWaypoints = sgWaypoints.add(new BoolSetting.Builder()
        .name("save-to-waypoints")
        .description("Creates xaeros minimap waypoints for stash finds.")
        .defaultValue(false)
        .onChanged(this::waypointSettingChanged)
        .build()
    );

    private final Setting<Boolean> sendNotifications = sgNotify.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Sends Minecraft notifications when new stashes are found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> notificationMode = sgNotify.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("The mode to use for notifications.")
        .defaultValue(Mode.Both)
        .visible(sendNotifications::get)
        .build()
    );

    private final Setting<Boolean> sendWebhook = sgNotify.add(new BoolSetting.Builder()
        .name("Send Webhook")
        .description("Sends a webhook when a stash is found.")
        .defaultValue(false)
        .build()
    );

    public final Setting<String> webhookLink = sgNotify.add(new StringSetting.Builder()
        .name("Webhook Link")
        .description("A discord webhook link. Looks like this: https://discord.com/api/webhooks/webhookUserId/webHookTokenOrSomething")
        .defaultValue("")
        .visible(sendWebhook::get)
        .build()
    );

    public final Setting<Boolean> advancedLogging = sgNotify.add(new BoolSetting.Builder()
        .name("advanced-logging")
        .description("Will log more information, including the amount of each container found.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> ping = sgNotify.add(new BoolSetting.Builder()
        .name("Ping For Stash Finder")
        .description("Pings you for stash finder and base finder messages")
        .defaultValue(false)
        .visible(sendWebhook::get)
        .build()
    );

    public final Setting<String> discordId = sgNotify.add(new StringSetting.Builder()
        .name("Discord ID")
        .description("Your discord ID")
        .defaultValue("")
        .visible(() -> sendWebhook.get() && ping.get())
        .build()
    );

    private final Setting<Boolean> findBlocks = sgBlocks.add(new BoolSetting.Builder()
        .name("block-lists")
        .description("Also counts blocks from the lists below, so a stash whose containers were missed still shows up.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> scanBottom = sgBlocks.add(new IntSetting.Builder()
        .name("scan-bottom-offset")
        .description("Blocks above the world floor where the block scan starts.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 319)
        .visible(findBlocks::get)
        .build()
    );

    private final Setting<Integer> scanTop = sgBlocks.add(new IntSetting.Builder()
        .name("scan-top-offset")
        .description("Blocks below the world ceiling where the block scan stops.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 319)
        .visible(findBlocks::get)
        .build()
    );

    private final Setting<Boolean> firstList = sgBlocks.add(new BoolSetting.Builder()
        .name("list-1")
        .description("Counts the blocks of the first list.")
        .defaultValue(true)
        .visible(findBlocks::get)
        .build()
    );

    private final Setting<List<Block>> firstBlocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("blocks-1")
        .description("A chunk holding this many of any of these blocks is recorded.")
        .defaultValue(
            Blocks.SHULKER_BOX, Blocks.BLACK_SHULKER_BOX, Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX,
            Blocks.CYAN_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX,
            Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.LIME_SHULKER_BOX, Blocks.MAGENTA_SHULKER_BOX,
            Blocks.ORANGE_SHULKER_BOX, Blocks.PINK_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX, Blocks.RED_SHULKER_BOX,
            Blocks.WHITE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX, Blocks.ENDER_CHEST, Blocks.CRAFTER
        )
        .visible(() -> findBlocks.get() && firstList.get())
        .build()
    );

    private final Setting<Integer> firstCount = sgBlocks.add(new IntSetting.Builder()
        .name("count-1")
        .description("How many blocks of the first list a chunk needs to be recorded.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 100)
        .visible(() -> findBlocks.get() && firstList.get())
        .build()
    );

    private final Setting<Boolean> secondList = sgBlocks.add(new BoolSetting.Builder()
        .name("list-2")
        .description("Counts the blocks of the second list.")
        .defaultValue(true)
        .visible(findBlocks::get)
        .build()
    );

    private final Setting<List<Block>> secondBlocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("blocks-2")
        .description("A chunk holding this many of any of these blocks is recorded.")
        .defaultValue(
            Blocks.DIAMOND_BLOCK, Blocks.NETHERITE_BLOCK, Blocks.EMERALD_BLOCK, Blocks.GOLD_BLOCK,
            Blocks.IRON_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.QUARTZ_BLOCK
        )
        .visible(() -> findBlocks.get() && secondList.get())
        .build()
    );

    private final Setting<Integer> secondCount = sgBlocks.add(new IntSetting.Builder()
        .name("count-2")
        .description("How many blocks of the second list a chunk needs to be recorded.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 100)
        .visible(() -> findBlocks.get() && secondList.get())
        .build()
    );

    private final Setting<Boolean> thirdList = sgBlocks.add(new BoolSetting.Builder()
        .name("list-3")
        .description("Counts the blocks of the third, custom list.")
        .defaultValue(false)
        .visible(findBlocks::get)
        .build()
    );

    private final Setting<List<Block>> thirdBlocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("blocks-3")
        .description("A chunk holding this many of any of these blocks is recorded.")
        .defaultValue(List.of())
        .visible(() -> findBlocks.get() && thirdList.get())
        .build()
    );

    private final Setting<Integer> thirdCount = sgBlocks.add(new IntSetting.Builder()
        .name("count-3")
        .description("How many blocks of the third list a chunk needs to be recorded.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 100)
        .visible(() -> findBlocks.get() && thirdList.get())
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draws a box around every recorded chunk near you.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
        .name("render-distance")
        .description("How many chunks from the player a recorded chunk is drawn.")
        .defaultValue(128)
        .min(6)
        .sliderRange(6, 128)
        .visible(render::get)
        .build()
    );

    private final Setting<Integer> renderTop = sgRender.add(new IntSetting.Builder()
        .name("render-top-y")
        .description("Top of the drawn box.")
        .defaultValue(256)
        .sliderRange(-128, 512)
        .visible(render::get)
        .build()
    );

    private final Setting<Integer> renderBottom = sgRender.add(new IntSetting.Builder()
        .name("render-bottom-y")
        .description("Bottom of the drawn box.")
        .defaultValue(150)
        .sliderRange(-128, 512)
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws a line from the player to the recorded chunks.")
        .defaultValue(true)
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> nearestTracer = sgRender.add(new BoolSetting.Builder()
        .name("nearest-only")
        .description("Draws only one tracer, to the closest recorded chunk.")
        .defaultValue(true)
        .visible(() -> render.get() && tracers.get())
        .build()
    );

    private final Setting<Integer> tracerDistance = sgRender.add(new IntSetting.Builder()
        .name("tracer-distance")
        .description("How many chunks from the player a tracer is still drawn.")
        .defaultValue(32)
        .min(1)
        .sliderRange(1, 1024)
        .visible(() -> render.get() && tracers.get())
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color of the drawn boxes.")
        .defaultValue(new SettingColor(255, 127, 0, 40))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline and tracer color.")
        .defaultValue(new SettingColor(255, 127, 0, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> clearDisable = sgCache.add(new BoolSetting.Builder()
        .name("clear-on-disable")
        .description("Drops the recorded chunks when the module is turned off. The saved files are kept.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> clearWorld = sgCache.add(new BoolSetting.Builder()
        .name("clear-on-world-change")
        .description("Drops the recorded chunks when you leave the world or change dimension. The saved files are kept.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> clearOutside = sgCache.add(new BoolSetting.Builder()
        .name("clear-outside-render-distance")
        .description("Drops a recorded chunk once it leaves the render distance above, so the list only holds what is around you.")
        .defaultValue(false)
        .build()
    );

    private Chunk lastFound;

    private RegistryKey<World> lastDimension;

    public BetterStashFinder()
    {
        super(StashOrganizerAddon.CATEGORY, "better-stash-finder", "Meteors StashFinder but with more features.");
    }

    @Override
    public void onActivate() {
        XaeroPlus.EVENT_BUS.register(this);
        load();
        this.lastDimension = this.mc.world == null ? null : this.mc.world.getRegistryKey();
    }

    @Override
    public void onDeactivate() {
        XaeroPlus.EVENT_BUS.unregister(this);
        if (clearDisable.get()) chunks.clear();
        this.lastDimension = null;
    }

    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event) {
        if (event.seenChunk()) return;
        // Check the distance.
        double chunkXAbs = Math.abs(event.chunk().getPos().x * 16);
        double chunkZAbs = Math.abs(event.chunk().getPos().z * 16);
        if (Math.sqrt(chunkXAbs * chunkXAbs + chunkZAbs * chunkZAbs) < minimumDistance.get()) return;

        Chunk chunk = new Chunk(event.chunk().getPos());

        RegistryKey<World> currentDimension = mc.world.getRegistryKey();

        // Check that the chunk is in old chunks
        if (onlyOldchunks.get())
        {
            ChunkPos chunkPos = chunk.chunkPos;
            PaletteNewChunks paletteNewChunks = ModuleManager.getModule(PaletteNewChunks.class);
            boolean is119NewChunk = paletteNewChunks
                .isNewChunk(
                    chunkPos.x,
                    chunkPos.z,
                    currentDimension
                );

            boolean is112OldChunk = ModuleManager.getModule(OldChunks.class)
                .isOldChunk(
                    chunkPos.x,
                    chunkPos.z,
                    currentDimension
                );
            if (is119NewChunk && !is112OldChunk) return;
        }

        for (BlockEntity blockEntity : event.chunk().getBlockEntities().values()) {
            if (!storageBlocks.get().contains(blockEntity.getType())) continue;

            if (blockEntity instanceof ChestBlockEntity) chunk.chests++;
            else if (blockEntity instanceof BarrelBlockEntity) chunk.barrels++;
            else if (blockEntity instanceof ShulkerBoxBlockEntity) chunk.shulkers++;
            else if (blockEntity instanceof EnderChestBlockEntity) chunk.enderChests++;
            else if (blockEntity instanceof AbstractFurnaceBlockEntity) chunk.furnaces++;
            else if (blockEntity instanceof DispenserBlockEntity) chunk.dispensersDroppers++;
            else if (blockEntity instanceof HopperBlockEntity) chunk.hoppers++;
        }

        boolean listed = this.scan(event.chunk(), chunk);

        if (chunk.getTotal() >= minimumStorageCount.get()
            || (shulkerInstantHit.get() && chunk.shulkers > 0)
            || listed) {
            Chunk prevChunk = null;
            int i = chunks.indexOf(chunk);

            lastFound = chunk;
            if (i < 0) chunks.add(chunk);
            else prevChunk = chunks.set(i, chunk);

            saveJson();
            saveCsv();

            if (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk)) {
                if (sendNotifications.get())
                {
                    switch (notificationMode.get())
                    {
                        case Chat -> info("Found stash at (highlight)%s(default), (highlight)%s(default).", chunk.x, chunk.z);
                        case Toast -> mc.getToastManager().add(new MeteorToast.Builder(title).text("Found Stash!").icon(Items.CHEST).build());
                        case Both -> {
                            info("Found stash at (highlight)%s(default), (highlight)%s(default).", chunk.x, chunk.z);
                            mc.getToastManager().add(new MeteorToast.Builder(title).text("Found Stash!").icon(Items.CHEST).build());
                        }
                    }
                }

                if (sendWebhook.get() && !webhookLink.get().isEmpty())
                {
                    if (advancedLogging.get())
                    {
                        String json = "{\"embeds\": [{" +
                            "\"title\": \"Stash Found!\"," +
                            "\"color\": 2154012," +
                            "\"description\": \"Coordinates: || X: " + chunk.x + " Z: " + chunk.z + "||\"," +
                            "\"fields\": [" +
                            "{" +
                            "\"name\": \"Chests\"," +
                            "\"value\": " + chunk.chests + "," +
                            "\"inline\": true" +
                            "}," +
                            "{" +
                            "\"name\": \"Barrels\"," +
                            "\"value\": " + chunk.barrels + "," +
                            "\"inline\": true" +
                            "}," +
                            "{" +
                            "\"name\": \"Shulkers\"," +
                            "\"value\": " + chunk.shulkers + "," +
                            "\"inline\": true" +
                            "}," +
                            "{" +
                            "\"name\": \"Ender Chests\"," +
                            "\"value\": " + chunk.enderChests + "," +
                            "\"inline\": true" +
                            "}," +
                            "{" +
                            "\"name\": \"Hoppers\"," +
                            "\"value\": " + chunk.hoppers + "," +
                            "\"inline\": true" +
                            "}," +
                            "{" +
                            "\"name\": \"Dispensers/Droppers\"," +
                            "\"value\": " + chunk.dispensersDroppers + "," +
                            "\"inline\": true" +
                            "}," +
                            "{" +
                            "\"name\": \"Furnaces\"," +
                            "\"value\": " + chunk.furnaces + "," +
                            "\"inline\": true" +
                            "}" +
                            "]" +
                            "}]}";

                        WebhookQueue.submit("stash-json", () -> info(webhookLink.get(), json, ping.get() ? discordId.get() : null));
                    }
                    else
                    {
                        String message = "Found stash at " + chunk.x + ", " + chunk.z + ".";
                        WebhookQueue.submit("stash-summary", () -> info(webhookLink.get(), title, message, ping.get() ? discordId.get() : null, mc.getSession().getUsername()));
                    }
                }

                if (saveToWaypoints.get())
                {
                    WaypointSet waypointSet = getWaypointSet();
                    if (waypointSet == null) return;
                    addToWaypoints(waypointSet, chunk);
                    SupportMods.xaeroMinimap.requestWaypointsRefresh();
                }
            }
        }
    }

    /**
     * Counts the blocks of every enabled list inside a chunk.
     *
     * <p>The container count only sees storage that is still a block entity. A stash emptied into
     * shulker boxes, or a room whose chests were pulled out, leaves blocks behind that the storage
     * pass cannot see, and those blocks are what tells the two apart.
     *
     * @param chunk chunk that was just received
     * @param record record built for that chunk
     * @return true when one of the lists reached its threshold
     */
    private boolean scan(WorldChunk chunk, Chunk record) {
        if (!findBlocks.get()) return false;
        if (this.mc.world == null) return false;

        int bottom = this.mc.world.getBottomY() + scanBottom.get();
        int top = this.mc.world.getTopYInclusive() - scanTop.get();

        List<List<Block>> lists = List.of(firstBlocks.get(), secondBlocks.get(), thirdBlocks.get());
        boolean[] active = {firstList.get(), secondList.get(), thirdList.get()};
        int[] limits = {firstCount.get(), secondCount.get(), thirdCount.get()};
        int[] counts = new int[lists.size()];

        int base = this.mc.world.getBottomY();
        ChunkSection[] sections = chunk.getSectionArray();

        for (int index = 0; index < sections.length; index++) {
            ChunkSection section = sections[index];
            if (section == null || section.isEmpty()) continue;

            int origin = base + index * 16;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int worldY = origin + y;
                    if (worldY < bottom || worldY > top) continue;

                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;

                        Block block = state.getBlock();

                        for (int list = 0; list < lists.size(); list++) {
                            if (!active[list] || lists.get(list).isEmpty()) continue;
                            if (!lists.get(list).contains(block)) continue;

                            counts[list]++;
                        }
                    }
                }
            }
        }

        int best = -1;

        for (int list = 0; list < counts.length; list++) {
            if (!active[list] || lists.get(list).isEmpty()) continue;
            if (counts[list] < limits[list]) continue;

            if (best == -1 || counts[list] > counts[best]) best = list;
        }

        if (best == -1) return false;

        record.blocks = counts[best];
        record.list = best + 1;
        return true;
    }

    /**
     * Draws the recorded chunks around the player.
     *
     * @param event render event
     */
    @meteordevelopment.orbit.EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || this.mc.player == null || chunks.isEmpty()) return;

        int top = renderTop.get();
        int bottom = renderBottom.get();
        double reach = renderDistance.get() * 16.0;

        Chunk nearest = null;
        double closest = Double.MAX_VALUE;

        for (Chunk chunk : chunks) {
            double dx = chunk.x - this.mc.player.getX();
            double dz = chunk.z - this.mc.player.getZ();
            double distance = dx * dx + dz * dz;

            if (distance <= reach * reach && distance < closest) {
                nearest = chunk;
                closest = distance;
            }
        }

        if (nearest == null) return;

        for (Chunk chunk : chunks) {
            double dx = chunk.x - this.mc.player.getX();
            double dz = chunk.z - this.mc.player.getZ();
            if (dx * dx + dz * dz > reach * reach) continue;

            Box box = new Box(
                chunk.x - 8, bottom, chunk.z - 8,
                chunk.x + 8, top, chunk.z + 8
            );

            event.renderer.box(box, sideColor.get(), lineColor.get(), ShapeMode.Sides, 0);
        }

        if (!tracers.get()) return;

        double line = tracerDistance.get() * 16.0;

        for (Chunk chunk : chunks) {
            if (nearestTracer.get() && chunk != nearest) continue;

            double dx = chunk.x - this.mc.player.getX();
            double dz = chunk.z - this.mc.player.getZ();
            if (dx * dx + dz * dz > line * line) continue;

            event.renderer.line(
                RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                chunk.x, (bottom + top) / 2.0, chunk.z, lineColor.get()
            );
        }
    }

    /**
     * Applies the cache policies that need a tick.
     *
     * @param event pre-tick event
     */
    @meteordevelopment.orbit.EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.world == null || this.mc.player == null) return;

        RegistryKey<World> dimension = this.mc.world.getRegistryKey();

        if (this.lastDimension == null) {
            this.lastDimension = dimension;
        } else if (!this.lastDimension.equals(dimension)) {
            this.lastDimension = dimension;
            if (clearWorld.get()) chunks.clear();
        }

        if (!clearOutside.get()) return;

        double reach = renderDistance.get() * 16.0;

        chunks.removeIf(chunk -> {
            double dx = chunk.x - this.mc.player.getX();
            double dz = chunk.z - this.mc.player.getZ();
            return dx * dx + dz * dz > reach * reach;
        });
    }

    /**
     * Drops the recorded chunks when the connection ends.
     *
     * @param event game left event
     */
    @meteordevelopment.orbit.EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (clearWorld.get()) chunks.clear();
        this.lastDimension = null;
    }

    public int stashCount() {
        return chunks.size();
    }

    /** Coordinates of the last stash recorded, or null when none was seen yet. */
    public String lastFound() {
        if (lastFound == null) return null;
        return lastFound.x + ", " + lastFound.z;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        // Sort
        chunks.sort(Comparator.comparingInt(value -> -value.getTotal()));

        WVerticalList list = theme.verticalList();

        // Clear
        WButton clear = list.add(theme.button("Clear")).widget();

        WTable table = new WTable();
        if (!chunks.isEmpty()) list.add(table);

        clear.action = () -> {
            removeAllStashWaypoints(chunks);
            chunks.clear();
            table.clear();
        };

        // Chunks
        fillTable(theme, table);

        return list;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        for (Chunk chunk : chunks) {
            table.add(theme.label("Pos: " + chunk.x + ", " + chunk.z));
            table.add(theme.label("Total: " + chunk.getTotal()));
            if (chunk.blocks > 0) table.add(theme.label("Listed: " + chunk.blocks));

            WButton open = table.add(theme.button("Open")).widget();
            open.action = () -> mc.setScreen(new ChunkScreen(theme, chunk));

            WButton gotoBtn = table.add(theme.button("Goto")).widget();
            gotoBtn.action = () -> PathManagers.get().moveTo(new BlockPos(chunk.x, 0, chunk.z), true);

            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                if (chunks.remove(chunk)) {
                    table.clear();
                    fillTable(theme, table);

                    saveJson();
                    saveCsv();
                    Waypoint waypoint = getWaypointByCoordinate(chunk.x, chunk.z);
                    if (waypoint != null)
                    {
                        WaypointSet waypointSet = getWaypointSet();
                        if (waypointSet != null)
                        {
                            waypointSet.remove(waypoint);
                            SupportMods.xaeroMinimap.requestWaypointsRefresh();
                        }
                    }
                }
            };

            table.row();
        }
    }

    private void load() {
        boolean loaded = false;

        // Try to load json
        File file = getJsonFile();
        if (file.exists()) {
            try {
                FileReader reader = new FileReader(file);
                chunks = GSON.fromJson(reader, new TypeToken<List<Chunk>>() {}.getType());
                reader.close();

                for (Chunk chunk : chunks) chunk.calculatePos();

                loaded = true;
            } catch (Exception ignored) {
                if (chunks == null) chunks = new ArrayList<>();
            }
        }

        // Try to load csv
        file = getCsvFile();
        if (!loaded && file.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                reader.readLine();

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(" ");
                    Chunk chunk = new Chunk(new ChunkPos(Integer.parseInt(values[0]), Integer.parseInt(values[1])));

                    chunk.chests = Integer.parseInt(values[2]);
                    chunk.shulkers = Integer.parseInt(values[3]);
                    chunk.enderChests = Integer.parseInt(values[4]);
                    chunk.furnaces = Integer.parseInt(values[5]);
                    chunk.dispensersDroppers = Integer.parseInt(values[6]);
                    chunk.hoppers = Integer.parseInt(values[7]);

                    chunks.add(chunk);
                }

                reader.close();
            } catch (Exception ignored) {
                if (chunks == null) chunks = new ArrayList<>();
            }
        }
        // TODO: Add all stashes as waypoints
    }

    private void saveCsv() {
        try {
            File file = getCsvFile();
            file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);

            writer.write("X,Z,Chests,Barrels,Shulkers,EnderChests,Furnaces,DispensersDroppers,Hoppers\n");
            for (Chunk chunk : chunks) chunk.write(writer);

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveJson() {
        try {
            File file = getJsonFile();
            file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);
            GSON.toJson(chunks, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getJsonFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "better-stash-finder"), Utils.getFileWorldName()), "stashes.json");
    }

    private File getCsvFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "better-stash-finder"), Utils.getFileWorldName()), "stashes.csv");
    }

    @Override
    public String getInfoString() {
        return String.valueOf(chunks.size());
    }

    private Waypoint getWaypointByCoordinate(int x, int z)
    {
        WaypointSet waypointSet = getWaypointSet();
        if (waypointSet == null) return null;
        for (Waypoint waypoint : waypointSet.getWaypoints())
        {
            if (waypoint.getX() == x && waypoint.getZ() == z)
            {
                return waypoint;
            }
        }
        return null;
    }

    private void removeAllStashWaypoints(List<Chunk> chunks)
    {
        WaypointSet waypointSet = getWaypointSet();
        if (waypointSet == null) return;
        for (Chunk chunk : chunks)
        {
            Waypoint waypoint = getWaypointByCoordinate(chunk.x, chunk.z);
            if (waypoint != null)
            {
                waypointSet.remove(waypoint);
            }
        }
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
    }

    private WaypointSet getWaypointSet()
    {
        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return null;
        MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
        if (currentWorld == null) return null;
        return currentWorld.getCurrentWaypointSet();
    }

    private void addToWaypoints(WaypointSet waypointSet, Chunk chunk)
    {
        int x = chunk.x;
        int z = chunk.z;

        // dont add waypoint that already exists
        if (getWaypointByCoordinate(x, z) != null) return;

        String waypointName = getWaypointName(chunk);

        // set color based on total storage blocks
        int color = 0;
        if (chunk.getTotal() < 15) color = 10; // green
        else if (chunk.getTotal() < 50) color = 14; // i forgot what these are lmao
        else if (chunk.getTotal() < 100) color = 12;
        else if (chunk.getTotal() >= 100) color = 4; // red i think

        Waypoint waypoint = new Waypoint(
            x,
            70,
            z,
            waypointName,
            "S",
            xaero.hud.minimap.waypoint.WaypointColor.fromIndex(color),
            xaero.hud.minimap.waypoint.WaypointPurpose.NORMAL);

        waypointSet.add(waypoint);
    }

    private static String getWaypointName(Chunk chunk) {
        String waypointName = "";
        if (chunk.chests > 0) waypointName += "C:" + chunk.chests;
        if (chunk.barrels > 0) waypointName += "B:" + chunk.barrels;
        if (chunk.shulkers > 0) waypointName += "S:" + chunk.shulkers;
        if (chunk.enderChests > 0) waypointName += "E:" + chunk.enderChests;
        if (chunk.hoppers > 0) waypointName += "H:" + chunk.hoppers;
        if (chunk.dispensersDroppers > 0) waypointName += "D:" + chunk.dispensersDroppers;
        if (chunk.furnaces > 0) waypointName += "F:" + chunk.furnaces;
        return waypointName;
    }

    private void waypointSettingChanged(boolean enabled)
    {
        if (!enabled) {
            removeAllStashWaypoints(chunks);
        } else {
            WaypointSet waypointSet = getWaypointSet();
            if (waypointSet == null) return;
            for (Chunk chunk : chunks) {
                addToWaypoints(waypointSet, chunk);
            }
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
        }
    }


    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public static class Chunk {
        private static final StringBuilder sb = new StringBuilder();

        public ChunkPos chunkPos;
        public transient int x, z;
        public int chests, barrels, shulkers, enderChests, furnaces, dispensersDroppers, hoppers;
        public int blocks, list;

        public Chunk(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;

            calculatePos();
        }

        public void calculatePos() {
            x = chunkPos.x * 16 + 8;
            z = chunkPos.z * 16 + 8;
        }

        public int getTotal() {
            return chests + barrels + shulkers + enderChests + furnaces + dispensersDroppers + hoppers;
        }

        public void write(Writer writer) throws IOException {
            sb.setLength(0);
            sb.append(x).append(',').append(z).append(',');
            sb.append(chests).append(',').append(barrels).append(',').append(shulkers).append(',').append(enderChests).append(',').append(furnaces).append(',').append(dispensersDroppers).append(',').append(hoppers).append('\n');
            writer.write(sb.toString());
        }

        public boolean countsEqual(Chunk c) {
            if (c == null) return false;
            return chests != c.chests || barrels != c.barrels || shulkers != c.shulkers || enderChests != c.enderChests || furnaces != c.furnaces || dispensersDroppers != c.dispensersDroppers || hoppers != c.hoppers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Chunk chunk = (Chunk) o;
            return Objects.equals(chunkPos, chunk.chunkPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkPos);
        }
    }

    private static class ChunkScreen extends WindowScreen {
        private final Chunk chunk;

        public ChunkScreen(GuiTheme theme, Chunk chunk) {
            super(theme, "Chunk at " + chunk.x + ", " + chunk.z);

            this.chunk = chunk;
        }

        @Override
        public void initWidgets() {
            WTable t = add(theme.table()).expandX().widget();

            // Total
            t.add(theme.label("Total:"));
            t.add(theme.label(chunk.getTotal() + ""));
            t.row();

            t.add(theme.horizontalSeparator()).expandX();
            t.row();

            // Separate
            t.add(theme.label("Chests:"));
            t.add(theme.label(chunk.chests + ""));
            t.row();

            t.add(theme.label("Barrels:"));
            t.add(theme.label(chunk.barrels + ""));
            t.row();

            t.add(theme.label("Shulkers:"));
            t.add(theme.label(chunk.shulkers + ""));
            t.row();

            t.add(theme.label("Ender Chests:"));
            t.add(theme.label(chunk.enderChests + ""));
            t.row();

            t.add(theme.label("Furnaces:"));
            t.add(theme.label(chunk.furnaces + ""));
            t.row();

            t.add(theme.label("Dispensers and droppers:"));
            t.add(theme.label(chunk.dispensersDroppers + ""));
            t.row();

            t.add(theme.label("Hoppers:"));
            t.add(theme.label(chunk.hoppers + ""));
            t.row();

            if (chunk.blocks > 0) {
                t.add(theme.label("Listed blocks:"));
                t.add(theme.label(chunk.blocks + " (list " + chunk.list + ")"));
                t.row();
            }
        }
    }
}
