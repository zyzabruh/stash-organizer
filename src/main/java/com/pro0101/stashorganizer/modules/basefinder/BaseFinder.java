package com.pro0101.stashorganizer.modules.basefinder;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scans every chunk packet for the traces a stash or a base leaves behind.
 *
 * <p>The scan happens on the chunk data itself, section by section, instead of relying on the
 * block-entity list alone. That catches containers the client has not turned into block entities
 * yet, and the block lists catch the furniture and materials a stash keeps after its chests are
 * emptied. Seven lists with their own thresholds keep a single decorative block from flagging a
 * chunk while still letting a real room through.
 *
 * <p>The detection approach is ported from Trouser Streak's BaseFinder; the implementation here is
 * adapted to this addon's settings and does not depend on XaeroPlus.
 */
public class BaseFinder extends Module {
    private static final int LISTS = 7;
    private static final String[] LIST_NAMES = {
        "storage", "valuables", "furniture", "decorative", "redstone", "lighting", "custom"
    };
    private static final int[] LIST_COUNTS = {4, 4, 5, 12, 8, 12, 1};

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetectors = settings.createGroup("Detectors");
    private final SettingGroup sgEntities = settings.createGroup("Entities");
    private final SettingGroup sgLists = settings.createGroup("Block Lists");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgCache = settings.createGroup("Cache");

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Reports each new base or stash in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> displayCoords = sgGeneral.add(new BoolSetting.Builder()
        .name("display-coords")
        .description("Includes the exact coordinates and the block that triggered the find.")
        .defaultValue(true)
        .visible(chatFeedback::get)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y-offset")
        .description("Blocks above the world floor where the scan starts.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 319)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y-offset")
        .description("Blocks below the world ceiling where the scan stops.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 319)
        .build()
    );

    private final Setting<Integer> messageDelay = sgGeneral.add(new IntSetting.Builder()
        .name("message-delay")
        .description("Ticks between two reports, so a chunk batch does not flood chat.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 300)
        .build()
    );

    private final Setting<Boolean> spawnerFinder = sgDetectors.add(new BoolSetting.Builder()
        .name("spawner-finder")
        .description("Flags a chunk holding a spawner.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> portalFinder = sgDetectors.add(new BoolSetting.Builder()
        .name("portal-finder")
        .description("Flags a chunk holding a nether or end portal.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bedrockFinder = sgDetectors.add(new BoolSetting.Builder()
        .name("bedrock-finder")
        .description("Flags bedrock above the normal floor, the mark of a hand-made hole or roof.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> bedrockOffset = sgDetectors.add(new IntSetting.Builder()
        .name("bedrock-offset")
        .description("How many blocks above the world floor bedrock stops being natural.")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 64)
        .visible(bedrockFinder::get)
        .build()
    );

    private final Setting<Boolean> roofFinder = sgDetectors.add(new BoolSetting.Builder()
        .name("nether-roof-finder")
        .description("Flags non-natural blocks above the nether roof.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> skyFinder = sgDetectors.add(new BoolSetting.Builder()
        .name("sky-build-finder")
        .description("Flags non-natural blocks above the sky-build height.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> skyHeight = sgDetectors.add(new IntSetting.Builder()
        .name("sky-build-y")
        .description("Y level above which a non-natural block is reported as a sky build.")
        .defaultValue(220)
        .sliderRange(-64, 512)
        .visible(skyFinder::get)
        .build()
    );

    private final Setting<Boolean> bubbleFinder = sgDetectors.add(new BoolSetting.Builder()
        .name("bubble-column-finder")
        .description("Flags bubble columns made by soul sand, which are not natural.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> signFinder = sgEntities.add(new BoolSetting.Builder()
        .name("written-sign-finder")
        .description("Flags a chunk with a sign that has text on it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> frameFinder = sgEntities.add(new BoolSetting.Builder()
        .name("item-frame-finder")
        .description("Flags item frames that do not hold an elytra.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pearlFinder = sgEntities.add(new BoolSetting.Builder()
        .name("ender-pearl-finder")
        .description("Flags dropped ender pearls.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> villagerFinder = sgEntities.add(new BoolSetting.Builder()
        .name("villager-finder")
        .description("Flags villagers above level one.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> nameFinder = sgEntities.add(new BoolSetting.Builder()
        .name("nametag-finder")
        .description("Flags named entities.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> boatFinder = sgEntities.add(new BoolSetting.Builder()
        .name("boat-finder")
        .description("Flags boats and chest boats.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clusterFinder = sgEntities.add(new BoolSetting.Builder()
        .name("entity-cluster-finder")
        .description("Flags a chunk holding too many of the selected entities.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> clusterTypes = sgEntities.add(new EntityTypeListSetting.Builder()
        .name("cluster-entities")
        .description("Entities counted by the cluster detector.")
        .defaultValue(defaultCreatures())
        .visible(clusterFinder::get)
        .build()
    );

    private final Setting<Integer> clusterCount = sgEntities.add(new IntSetting.Builder()
        .name("cluster-threshold")
        .description("How many selected entities a chunk needs to be reported.")
        .defaultValue(14)
        .min(1)
        .sliderRange(1, 100)
        .visible(clusterFinder::get)
        .build()
    );

    private final Setting<Integer> entityDelay = sgEntities.add(new IntSetting.Builder()
        .name("entity-scan-delay")
        .description("Ticks between two passes over the loaded chunks.")
        .defaultValue(40)
        .min(0)
        .sliderRange(0, 300)
        .build()
    );

    @SuppressWarnings("unchecked")
    private final Setting<Boolean>[] listEnabled = new Setting[LISTS];
    @SuppressWarnings("unchecked")
    private final Setting<List<Block>>[] listBlocks = new Setting[LISTS];
    @SuppressWarnings("unchecked")
    private final Setting<Integer>[] listCount = new Setting[LISTS];

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
        .defaultValue(0)
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
        .description("Draws only the tracer to the closest recorded chunk.")
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
        .description("Fill colour of the drawn boxes.")
        .defaultValue(new SettingColor(255, 127, 0, 40))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline and tracer colour.")
        .defaultValue(new SettingColor(255, 127, 0, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> save = sgCache.add(new BoolSetting.Builder()
        .name("save")
        .description("Writes every find to the base-finder folder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> load = sgCache.add(new BoolSetting.Builder()
        .name("load")
        .description("Reloads the saved finds when the module is enabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clearDisable = sgCache.add(new BoolSetting.Builder()
        .name("clear-on-disable")
        .description("Drops the recorded chunks when the module is turned off. The files are kept.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> clearWorld = sgCache.add(new BoolSetting.Builder()
        .name("clear-on-world-change")
        .description("Drops the recorded chunks when you leave the world.")
        .defaultValue(false)
        .build()
    );

    private final Set<ChunkPos> baseChunks = Collections.synchronizedSet(new LinkedHashSet<>());

    private RegistryKey<World> lastDimension;
    private ChunkPos lastFound;
    private int entityTicks;
    private int messageTicks;

    public BaseFinder() {
        super(StashOrganizerAddon.CATEGORY, "base-finder",
            "Scans chunk packets for stashes and bases, with block lists and entity detectors.");
        initLists();
    }

    private void initLists() {
        Block[][] defaults = defaultLists();

        for (int i = 0; i < LISTS; i++) {
            int index = i;

            listEnabled[index] = sgLists.add(new BoolSetting.Builder()
                .name("list-" + (index + 1))
                .description("Enables the " + LIST_NAMES[index] + " block list.")
                .defaultValue(true)
                .build()
            );

            listBlocks[index] = sgLists.add(new BlockListSetting.Builder()
                .name("blocks-" + (index + 1))
                .description("Blocks counted by the " + LIST_NAMES[index] + " list.")
                .defaultValue(defaults[index])
                .visible(() -> listEnabled[index].get())
                .build()
            );

            listCount[index] = sgLists.add(new IntSetting.Builder()
                .name("count-" + (index + 1))
                .description("How many blocks of the " + LIST_NAMES[index] + " list a chunk needs.")
                .defaultValue(LIST_COUNTS[index])
                .min(1)
                .sliderRange(1, 100)
                .visible(() -> listEnabled[index].get())
                .build()
            );
        }
    }

    @Override
    public void onActivate() {
        lastDimension = mc.world == null ? null : mc.world.getRegistryKey();
        lastFound = null;
        entityTicks = 0;
        messageTicks = 0;

        baseChunks.clear();
        if (load.get()) loadFinds();
    }

    @Override
    public void onDeactivate() {
        if (clearDisable.get()) baseChunks.clear();
        lastDimension = null;
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ChunkDataS2CPacket packet) || mc.world == null) return;
        if (mc.world.getChunkManager().getChunk(packet.getChunkX(), packet.getChunkZ()) != null) return;

        ChunkPos pos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
        World world = mc.world;

        mc.execute(() -> {
            if (mc.world != world) return;

            WorldChunk chunk = new WorldChunk(world, pos);

            try {
                ChunkData data = packet.getChunkData();
                chunk.loadFromPacket(
                    data.getSectionsDataBuf(),
                    data.getHeightmap(),
                    data.getBlockEntities(packet.getChunkX(), packet.getChunkZ())
                );
            } catch (Exception ignored) {
                return;
            }

            scanBlocks(chunk);
        });
    }

    private void scanBlocks(WorldChunk chunk) {
        if (mc.world == null) return;

        ChunkPos pos = chunk.getPos();
        if (baseChunks.contains(pos)) return;

        int bottom = mc.world.getBottomY() + minY.get();
        int top = mc.world.getTopYInclusive() - maxY.get();
        int[] counts = new int[LISTS];
        BlockPos[] firstPos = new BlockPos[LISTS];
        String[] firstName = new String[LISTS];
        Map<Block, Integer> lookup = buildLookup();

        boolean spawner = false;
        int spawnerY = 0;
        String special = null;
        int specialY = 0;
        String specialBlock = null;

        int base = mc.world.getBottomY();
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

                        if (special == null) {
                            if (spawnerFinder.get() && block == Blocks.SPAWNER) {
                                spawner = true;
                                spawnerY = worldY;
                            }

                            if (portalFinder.get() && (block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL)) {
                                special = "portal";
                                specialY = worldY;
                                specialBlock = block.getName().getString();
                            } else if (bedrockFinder.get() && block == Blocks.BEDROCK && isUnnaturalBedrock(worldY)) {
                                special = "unnatural bedrock";
                                specialY = worldY;
                                specialBlock = block.getName().getString();
                            } else if (roofFinder.get() && isNetherRoof(worldY) && !isNatural(block)
                                && block != Blocks.RED_MUSHROOM && block != Blocks.BROWN_MUSHROOM) {
                                special = "nether roof";
                                specialY = worldY;
                                specialBlock = block.getName().getString();
                            } else if (skyFinder.get() && worldY > skyHeight.get() && !isNatural(block)) {
                                special = "sky build";
                                specialY = worldY;
                                specialBlock = block.getName().getString();
                            } else if (bubbleFinder.get() && block instanceof BubbleColumnBlock
                                && !state.get(BubbleColumnBlock.DRAG)) {
                                special = "bubble column";
                                specialY = worldY;
                                specialBlock = block.getName().getString();
                            }
                        }

                        Integer mask = lookup.get(block);
                        if (mask == null) continue;

                        for (int list = 0; list < LISTS; list++) {
                            if ((mask & (1 << list)) == 0) continue;

                            counts[list]++;
                            if (firstPos[list] == null) {
                                firstPos[list] = new BlockPos(x, worldY, z);
                                firstName[list] = block.getName().getString();
                            }
                        }
                    }
                }
            }
        }

        if (special != null) {
            addFinding(pos, special, specialY, specialBlock);
            return;
        }

        if (spawner) {
            addFinding(pos, "spawner", spawnerY, Blocks.SPAWNER.getName().getString());
            return;
        }

        int best = -1;

        for (int list = 0; list < LISTS; list++) {
            if (!listEnabled[list].get() || listBlocks[list].get().isEmpty()) continue;
            if (counts[list] < listCount[list].get()) continue;

            if (best == -1 || counts[best] < counts[list]) best = list;
        }

        if (best == -1) return;

        addFinding(pos, "list " + (best + 1) + " (" + LIST_NAMES[best] + ")", firstPos[best].getY(), firstName[best]);
    }

    private Map<Block, Integer> buildLookup() {
        Map<Block, Integer> map = new HashMap<>();

        for (int list = 0; list < LISTS; list++) {
            if (!listEnabled[list].get()) continue;

            for (Block block : listBlocks[list].get()) {
                map.merge(block, 1 << list, (a, b) -> a | b);
            }
        }

        return map;
    }

    private void scanEntities() {
        if (mc.world == null || mc.player == null) return;

        int radius = Math.min(mc.options.getViewDistance().getValue(), 8);
        ChunkPos center = mc.player.getChunkPos();

        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                WorldChunk chunk = mc.world.getChunk(x, z);
                if (chunk == null || !chunk.getStatus().isAtLeast(ChunkStatus.FULL)) continue;
                if (baseChunks.contains(chunk.getPos())) continue;

                scanEntities(chunk);
            }
        }
    }

    private void scanEntities(WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();

        if (signFinder.get()) {
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (!(blockEntity instanceof SignBlockEntity sign)) continue;
                if (hasText(sign.getFrontText()) || hasText(sign.getBackText())) {
                    BlockPos signPos = sign.getPos();
                    addFinding(pos, "written sign", signPos.getY(), "sign");
                    return;
                }
            }
        }

        Box box = new Box(
            pos.getStartX(), mc.world.getBottomY(), pos.getStartZ(),
            pos.getEndX() + 1, mc.world.getTopYInclusive(), pos.getEndZ() + 1
        );

        AtomicInteger cluster = new AtomicInteger();

        for (Entity entity : mc.world.getEntitiesByClass(Entity.class, box, entity -> true)) {
            if (frameFinder.get() && entity instanceof ItemFrameEntity frame) {
                if (frame.getHeldItemStack().getItem() != Items.ELYTRA) {
                    addFinding(pos, "item frame", (int) entity.getY(), "item frame");
                    return;
                }
            } else if (pearlFinder.get() && entity instanceof EnderPearlEntity) {
                addFinding(pos, "ender pearl", (int) entity.getY(), "ender pearl");
                return;
            } else if (villagerFinder.get() && entity instanceof VillagerEntity villager) {
                if (villager.getVillagerData().level() > 1) {
                    addFinding(pos, "villager level " + villager.getVillagerData().level(), (int) entity.getY(), "villager");
                    return;
                }
            } else if (nameFinder.get() && entity.hasCustomName()) {
                addFinding(pos, "named entity", (int) entity.getY(), entity.getName().getString());
                return;
            } else if (boatFinder.get() && (entity instanceof BoatEntity || entity instanceof ChestBoatEntity)) {
                addFinding(pos, "boat", (int) entity.getY(), entity.getType().getName().getString());
                return;
            }

            if (clusterFinder.get() && clusterTypes.get().contains(entity.getType())) {
                cluster.incrementAndGet();
            }
        }

        if (clusterFinder.get() && cluster.get() >= clusterCount.get()) {
            addFinding(pos, "entity cluster (" + cluster.get() + ")", (int) mc.player.getY(), "entities");
        }
    }

    private static boolean hasText(SignText text) {
        int line = 0;
        for (Text message : text.getMessages(false)) {
            String value = message.getString();
            boolean iglooArrow = (line == 1 && value.equals("<----")) || (line == 2 && value.equals("---->"));
            if (!value.isBlank() && !iglooArrow) return true;
            line++;
        }
        return false;
    }

    private boolean isUnnaturalBedrock(int y) {
        if (mc.world == null) return false;
        if (mc.world.getRegistryKey() == World.OVERWORLD) return y > mc.world.getBottomY() + bedrockOffset.get();
        if (mc.world.getRegistryKey() == World.NETHER) {
            return y > mc.world.getBottomY() + bedrockOffset.get() && (y < 123 || y > 127);
        }
        return false;
    }

    private boolean isNetherRoof(int y) {
        return mc.world != null && mc.world.getRegistryKey() == World.NETHER && y >= 128;
    }

    private static boolean isNatural(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR
            || block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.DIRT
            || block == Blocks.GRASS_BLOCK || block == Blocks.SAND || block == Blocks.GRAVEL
            || block == Blocks.WATER || block == Blocks.LAVA || block == Blocks.BEDROCK
            || block == Blocks.NETHERRACK || block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL
            || block == Blocks.ANDESITE || block == Blocks.DIORITE || block == Blocks.GRANITE
            || block == Blocks.TUFF || block == Blocks.CALCITE || block == Blocks.BASALT
            || block == Blocks.BLACKSTONE || block == Blocks.END_STONE || block == Blocks.SANDSTONE
            || block == Blocks.MAGMA_BLOCK || block == Blocks.OBSIDIAN;
    }

    private void addFinding(ChunkPos pos, String reason, int y, String block) {
        if (!baseChunks.add(pos)) return;

        lastFound = pos;
        if (save.get()) saveFind(pos);

        if (!chatFeedback.get() || messageTicks > 0) return;
        messageTicks = messageDelay.get();

        if (displayCoords.get()) {
            info("Possible base at (highlight)%s(default), (highlight)%s(default), (highlight)%s(default) — %s (%s).",
                pos.getCenterX(), y, pos.getCenterZ(), reason, block == null ? "block" : block);
        } else {
            info("Possible base found — %s.", reason);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (messageTicks > 0) messageTicks--;

        RegistryKey<World> dimension = mc.world.getRegistryKey();
        if (lastDimension == null) {
            lastDimension = dimension;
        } else if (!lastDimension.equals(dimension)) {
            lastDimension = dimension;
            baseChunks.clear();
            if (load.get()) loadFinds();
        }

        if (entityDelay.get() > 0) {
            entityTicks++;
            if (entityTicks < entityDelay.get()) return;
        }
        entityTicks = 0;

        scanEntities();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (clearWorld.get()) baseChunks.clear();
        lastDimension = null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || mc.player == null || baseChunks.isEmpty()) return;

        int top = renderTop.get();
        int bottom = renderBottom.get();
        double reach = renderDistance.get() * 16.0;
        double lineReach = tracerDistance.get() * 16.0;

        ChunkPos nearest = null;
        double closest = Double.MAX_VALUE;

        synchronized (baseChunks) {
            for (ChunkPos pos : baseChunks) {
                double dx = pos.getCenterX() - mc.player.getX();
                double dz = pos.getCenterZ() - mc.player.getZ();
                double distance = dx * dx + dz * dz;

                if (distance <= reach * reach && distance < closest) {
                    nearest = pos;
                    closest = distance;
                }
            }

            for (ChunkPos pos : baseChunks) {
                double dx = pos.getCenterX() - mc.player.getX();
                double dz = pos.getCenterZ() - mc.player.getZ();
                if (dx * dx + dz * dz > reach * reach) continue;

                Box box = new Box(
                    pos.getStartX(), bottom, pos.getStartZ(),
                    pos.getEndX() + 1, top, pos.getEndZ() + 1
                );

                event.renderer.box(box, sideColor.get(), lineColor.get(), ShapeMode.Sides, 0);
            }
        }

        if (!tracers.get() || nearest == null) return;

        synchronized (baseChunks) {
            for (ChunkPos pos : baseChunks) {
                if (nearestTracer.get() && !pos.equals(nearest)) continue;

                double dx = pos.getCenterX() - mc.player.getX();
                double dz = pos.getCenterZ() - mc.player.getZ();
                if (dx * dx + dz * dz > lineReach * lineReach) continue;

                event.renderer.line(
                    RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    pos.getCenterX(), (bottom + top) / 2.0, pos.getCenterZ(), lineColor.get()
                );
            }
        }
    }

    private Path directory() {
        String server = "singleplayer";

        if (mc.getCurrentServerEntry() != null) {
            server = mc.getCurrentServerEntry().address.replaceAll("[^a-zA-Z0-9._-]", "_");
        } else if (!mc.isInSingleplayer()) {
            server = "unknown";
        }

        String world = mc.world == null ? "unknown" : mc.world.getRegistryKey().getValue().toString().replace(':', '_');

        return FabricLoader.getInstance().getGameDir()
            .resolve("stash-organizer")
            .resolve("base-finder")
            .resolve(server)
            .resolve(world);
    }

    private void loadFinds() {
        Path file = directory().resolve("bases.txt");
        if (!Files.exists(file)) return;

        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split(",");
                if (parts.length < 2) continue;

                try {
                    baseChunks.add(new ChunkPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void saveFind(ChunkPos pos) {
        try {
            Path directory = directory();
            Files.createDirectories(directory);

            Files.writeString(
                directory.resolve("bases.txt"),
                pos.x + "," + pos.z + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(baseChunks.size());
    }

    /** Coordinates of the last find, or null when none was seen yet. */
    public String lastFound() {
        return lastFound == null ? null : lastFound.getCenterX() + ", " + lastFound.getCenterZ();
    }

    private static Set<EntityType<?>> defaultCreatures() {
        Set<EntityType<?>> creatures = new HashSet<>();

        Registries.ENTITY_TYPE.forEach(type -> {
            if (type.getSpawnGroup() == SpawnGroup.CREATURE) creatures.add(type);
        });

        return creatures;
    }

    private static Block[][] defaultLists() {
        return new Block[][]{
            {
                Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.HOPPER,
                Blocks.DROPPER, Blocks.DISPENSER, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
                Blocks.BREWING_STAND, Blocks.CRAFTER,
                Blocks.SHULKER_BOX, Blocks.BLACK_SHULKER_BOX, Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX,
                Blocks.CYAN_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX,
                Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.LIME_SHULKER_BOX,
                Blocks.MAGENTA_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX, Blocks.PINK_SHULKER_BOX,
                Blocks.PURPLE_SHULKER_BOX, Blocks.RED_SHULKER_BOX, Blocks.WHITE_SHULKER_BOX,
                Blocks.YELLOW_SHULKER_BOX
            },
            {
                Blocks.DIAMOND_BLOCK, Blocks.NETHERITE_BLOCK, Blocks.EMERALD_BLOCK, Blocks.GOLD_BLOCK,
                Blocks.IRON_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.QUARTZ_BLOCK,
                Blocks.BEACON, Blocks.CONDUIT, Blocks.LODESTONE, Blocks.RESPAWN_ANCHOR, Blocks.ANCIENT_DEBRIS
            },
            {
                Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
                Blocks.JUKEBOX, Blocks.NOTE_BLOCK, Blocks.BOOKSHELF, Blocks.CHISELED_BOOKSHELF,
                Blocks.CRAFTING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.SMITHING_TABLE,
                Blocks.FLETCHING_TABLE, Blocks.LOOM, Blocks.STONECUTTER,
                Blocks.RED_BED, Blocks.WHITE_BED, Blocks.BLACK_BED, Blocks.CAKE
            },
            {
                Blocks.WHITE_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE,
                Blocks.WHITE_TERRACOTTA, Blocks.BLACK_TERRACOTTA, Blocks.GRAY_TERRACOTTA,
                Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK, Blocks.POLISHED_ANDESITE,
                Blocks.POLISHED_DIORITE, Blocks.POLISHED_GRANITE, Blocks.BRICKS, Blocks.STONE_BRICKS,
                Blocks.GLASS, Blocks.TINTED_GLASS, Blocks.SEA_LANTERN, Blocks.PRISMARINE,
                Blocks.DARK_PRISMARINE, Blocks.PURPUR_BLOCK
            },
            {
                Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.REPEATER, Blocks.COMPARATOR,
                Blocks.REDSTONE_TORCH, Blocks.REDSTONE_LAMP, Blocks.RAIL, Blocks.POWERED_RAIL,
                Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL, Blocks.TRIPWIRE_HOOK, Blocks.DAYLIGHT_DETECTOR,
                Blocks.TARGET, Blocks.LEVER, Blocks.STONE_BUTTON, Blocks.OAK_PRESSURE_PLATE,
                Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE
            },
            {
                Blocks.TORCH, Blocks.SOUL_TORCH, Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.GLOWSTONE,
                Blocks.SHROOMLIGHT, Blocks.OCHRE_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT,
                Blocks.VERDANT_FROGLIGHT, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.CANDLE,
                Blocks.WHITE_CANDLE, Blocks.BLACK_CANDLE
            },
            {}
        };
    }
}
