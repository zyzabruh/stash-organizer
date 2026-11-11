package com.pro0101.stashorganizer.hud;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Counts double and single chests within the render distance.
 *
 * <p>A stash is sized by its chests, and a double chest is two blocks that one block entity shares
 * with its neighbour. Counting block entities alone would therefore report a double chest twice, so
 * the chest type from the block state decides: a {@code LEFT} half is one double chest, its
 * {@code RIGHT} half is skipped, and {@code SINGLE} is a single chest.
 *
 * <p>Scanning walks the loaded chunks and reads their block entity map instead of iterating blocks:
 * a radius of twelve chunks costs a few hundred map lookups, not millions of block reads. The result
 * is cached for a second, and again until the player has moved eight blocks, so the HUD never turns
 * into a per-frame scan.
 */
public class ChestCounterHud extends HudElement {
    public static final HudElementInfo<ChestCounterHud> INFO = new HudElementInfo<>(
        StashOrganizerAddon.HUD_GROUP,
        "chest-counter",
        "Counts double and single chests inside the render distance, to size a stash.",
        ChestCounterHud::new
    );

    private static final Color BACKGROUND = new Color(0, 0, 0, 120);
    private static final Color LINE_COLOR = new Color(170, 170, 170);
    private static final Color DOUBLE_COLOR = new Color(120, 230, 160);
    private static final Color SINGLE_COLOR = new Color(220, 220, 220);
    private static final double PADDING = 2;

    /** How long a scan stays valid, in milliseconds. */
    private static final long RESCAN_MS = 1000;

    /** How far the player may move before the scan is redone, in blocks. */
    private static final int MOVE_THRESHOLD = 8;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> barrels = this.sgGeneral.add(new BoolSetting.Builder()
        .name("barrels")
        .description("Count barrels as single chests.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> enderChests = this.sgGeneral.add(new BoolSetting.Builder()
        .name("ender-chests")
        .description("Count ender chests as single chests.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> radius = this.sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Chunk radius to scan. Zero uses the render distance.")
        .defaultValue(0)
        .range(0, 32)
        .build()
    );

    private int doubles;
    private int singles;

    private long lastScan;
    private BlockPos lastPos;

    public ChestCounterHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos pos = client.player == null ? null : client.player.getBlockPos();

        if (pos == null) {
            this.doubles = 0;
            this.singles = 0;
        } else if (shouldScan(pos)) {
            scan(client, pos);
            this.lastScan = System.currentTimeMillis();
            this.lastPos = pos;
        }

        String doubleLine = "double  " + this.doubles;
        String singleLine = "single  " + this.singles;

        double lineHeight = renderer.textHeight(true) + 1;
        double width = Math.max(renderer.textWidth(doubleLine, true), renderer.textWidth(singleLine, true));

        setSize(width + PADDING * 2, lineHeight * 3 + PADDING * 2);

        renderer.quad(x, y, getWidth(), getHeight(), BACKGROUND);

        double textX = x + PADDING;
        double textY = y + PADDING;

        renderer.text("Chests", textX, textY, Color.WHITE, true);
        textY += lineHeight;

        renderer.text(doubleLine, textX, textY, DOUBLE_COLOR, true);
        textY += lineHeight;

        renderer.text(singleLine, textX, textY, SINGLE_COLOR, true);
    }

    /**
     * Reports whether the cached count is stale.
     *
     * @param pos current player position
     * @return true when the world should be scanned again
     */
    private boolean shouldScan(BlockPos pos) {
        if (this.lastPos == null) return true;
        if (System.currentTimeMillis() - this.lastScan >= RESCAN_MS) return true;

        int dx = pos.getX() - this.lastPos.getX();
        int dz = pos.getZ() - this.lastPos.getZ();

        return dx * dx + dz * dz >= MOVE_THRESHOLD * MOVE_THRESHOLD;
    }

    /**
     * Counts the chests of every loaded chunk around the player.
     *
     * @param client client, for the world and the view distance
     * @param center player position, which picks the chunk radius
     */
    private void scan(MinecraftClient client, BlockPos center) {
        this.doubles = 0;
        this.singles = 0;

        ClientWorld world = client.world;
        if (world == null) return;

        int chunks = this.radius.get() > 0
            ? this.radius.get()
            : Math.max(2, client.options.getViewDistance().getValue());

        int centerX = center.getX() >> 4;
        int centerZ = center.getZ() >> 4;

        for (int chunkX = centerX - chunks; chunkX <= centerX + chunks; chunkX++) {
            for (int chunkZ = centerZ - chunks; chunkZ <= centerZ + chunks; chunkZ++) {
                WorldChunk chunk = world.getChunk(chunkX, chunkZ);

                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    count(blockEntity);
                }
            }
        }
    }

    /**
     * Adds one block entity to the counts, skipping the second half of a double chest.
     *
     * @param blockEntity block entity found in a chunk
     */
    private void count(BlockEntity blockEntity) {
        BlockState state = blockEntity.getCachedState();

        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);

            if (type == ChestType.SINGLE) this.singles++;
            else if (type == ChestType.LEFT) this.doubles++;

            return;
        }

        if (this.barrels.get() && blockEntity instanceof BarrelBlockEntity) {
            this.singles++;
            return;
        }

        if (this.enderChests.get() && blockEntity instanceof EnderChestBlockEntity) {
            this.singles++;
        }
    }
}
