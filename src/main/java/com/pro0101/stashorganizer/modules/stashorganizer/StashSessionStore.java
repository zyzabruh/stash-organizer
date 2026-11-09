package com.pro0101.stashorganizer.modules.stashorganizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.pro0101.stashorganizer.modules.stashorganizer.StashPlan.pos;

/**
 * Saves the planned work (containers, group assignments, remaining groups, phase) so an
 * interrupted run can continue after a disconnection. The live contents of every container are
 * refreshed when the session resumes; only the plan is persisted.
 * <p>
 * The state that would otherwise be lost is saved as well: the stacks already carried in the
 * inventory, the shulker boxes already emptied, and the counters shown by the HUD. A run stopped
 * in the middle of the unpack phase therefore neither re-opens an emptied box nor forgets the
 * items it was about to deliver.
 */
public final class StashSessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FOLDER = MeteorClient.FOLDER.toPath().resolve("stash-organizer");
    private static final Path FILE = FOLDER.resolve("session.json");

    private StashSessionStore() {
    }

    /** World identity: server (or singleplayer level) plus dimension. */
    public static String worldKey() {
        MinecraftClient mc = MeteorClient.mc;
        if (mc == null || mc.world == null) return null;

        String server;

        if (mc.getCurrentServerEntry() != null) {
            server = mc.getCurrentServerEntry().address;
        } else if (mc.getServer() != null) {
            server = "sp:" + mc.getServer().getSaveProperties().getLevelName();
        } else {
            server = "singleplayer";
        }

        return server + "|" + mc.world.getRegistryKey().getValue();
    }

    public static boolean canResume() {
        StashPlan.Data data = load();
        String current = worldKey();

        return data != null && current != null && current.equals(data.world)
            && isResumable(data.phase)
            && data.containers != null && !data.containers.isEmpty();
    }

    /** Loads the saved plan into a fresh session. Returns false when there is nothing to resume. */
    public static boolean apply(StashSession session) {
        StashPlan.Data data = load();
        String current = worldKey();

        if (data == null || current == null || !current.equals(data.world) || !isResumable(data.phase)) return false;
        if (data.containers == null || data.containers.isEmpty()) return false;
        if (data.mode != null && !data.mode.equals(session.host.mode().name())) return false;

        restore(session, data);
        return true;
    }

    public static void save(StashSession session) {
        if (session == null || session.phase == StashPhase.Done || session.phase == StashPhase.Idle) return;
        if (session.worldKey == null || session.containers.isEmpty()) return;

        StashPhase target = session.phase;

        if ((target == StashPhase.Dump || target == StashPhase.Rescan) && session.resumePhase != null) {
            target = session.resumePhase;
        }

        if (!isResumable(target.name())) return;

        write(capture(session, session.worldKey, target));
    }

    /** Builds the saved form of a session; kept separate so a test can round-trip it. */
    static StashPlan.Data capture(StashSession session, String world, StashPhase phase) {
        StashPlan.Data data = new StashPlan.Data();
        data.version = StashPlan.VERSION;
        data.world = world;
        data.mode = session.host.mode().name();
        data.phase = phase.name();
        data.dumpPos = pos(session.dumpPos);
        data.totalMoves = session.totalMoves;
        data.placedChests = session.placedChests;
        data.placedShulkers = session.placedShulkers;
        data.packedShulkers = session.packedShulkers;
        data.resumeAfterCollect = session.resumeAfterCollect == null ? null : session.resumeAfterCollect.name();

        for (StashContainer container : session.containers) {
            StashPlan.ContainerData saved = new StashPlan.ContainerData();
            saved.pos = pos(container.pos);
            saved.block = container.block == null ? null : Registries.BLOCK.getId(container.block).toString();
            saved.size = container.size;
            saved.reserved = container.reserved;
            saved.scanned = container.scanned;
            saved.failed = container.failed;
            saved.packing = container.packing;
            saved.partner = pos(container.partner);
            saved.groups = new ArrayList<>(container.groups);
            data.containers.add(saved);
        }

        for (StashGroup group : session.groups.values()) {
            StashPlan.GroupData saved = new StashPlan.GroupData();
            saved.key = group.key;
            saved.slots = group.slots;
            data.groups.add(saved);
        }

        for (UnassignedGroup entry : session.unassigned) {
            StashPlan.GroupData saved = new StashPlan.GroupData();
            saved.key = entry.group.key;
            saved.slots = entry.slots;
            data.unassigned.add(saved);
        }

        for (Map.Entry<Integer, Carried> entry : session.carried.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            StashPlan.CarriedData saved = new StashPlan.CarriedData();
            saved.slot = entry.getKey();
            saved.item = Registries.ITEM.getId(entry.getValue().item()).toString();
            saved.group = entry.getValue().group();
            data.carried.add(saved);
        }

        for (ShulkerRef ref : session.shulkers) {
            StashPlan.ShulkerData saved = new StashPlan.ShulkerData();
            saved.pos = pos(ref.pos);
            saved.slot = ref.slot;
            saved.done = ref.done;
            data.shulkers.add(saved);
        }

        return data;
    }

    /** Fills a session from a saved plan; kept separate so a test can round-trip it. */
    static void restore(StashSession session, StashPlan.Data data) {
        session.containers.clear();
        session.groups.clear();
        session.unassigned.clear();
        session.shulkers.clear();
        session.restoredShulkers.clear();
        session.worldKey = data.world;
        session.dumpPos = pos(data.dumpPos);
        session.totalMoves = data.totalMoves;
        session.placedChests = data.placedChests;
        session.placedShulkers = data.placedShulkers;
        session.packedShulkers = data.packedShulkers;

        for (StashPlan.GroupData saved : data.groups == null ? List.<StashPlan.GroupData>of() : data.groups) {
            if (saved.key == null) continue;
            StashGroup group = new StashGroup(saved.key);
            group.slots = saved.slots;
            session.groups.put(saved.key, group);
        }

        for (StashPlan.ContainerData saved : data.containers) {
            if (saved.pos == null) continue;

            Identifier id = saved.block == null ? null : Identifier.tryParse(saved.block);
            Block block = id == null ? Blocks.AIR : Registries.BLOCK.get(id);

            StashContainer container = new StashContainer(pos(saved.pos), block);
            container.size = saved.size > 0 ? saved.size : 27;
            container.partner = pos(saved.partner);
            container.reserved = saved.reserved;
            container.scanned = saved.scanned;
            container.packing = saved.packing;
            // Only a vanished block is permanently failed; a previous open failure is retried by the rescan.
            container.failed = block == Blocks.AIR;

            if (saved.groups != null) container.groups.addAll(saved.groups);

            container.clearCache();
            session.containers.add(container);
        }

        // The frame on a chest survives a reconnection: the group it names is read again, unless
        // the setting that reads labels is off.
        for (StashContainer container : session.containers) {
            if (!session.readsFrameLabels()) {
                container.frameGroup = null;
                continue;
            }

            ItemStack label = ChestLabeler.existingLabel(session, container.pos);
            container.frameGroup = label.isEmpty() ? null : session.groupOf(label);
        }

        for (StashPlan.GroupData saved : data.unassigned == null ? List.<StashPlan.GroupData>of() : data.unassigned) {
            StashGroup group = session.groups.get(saved.key);
            if (group == null) continue;
            session.unassigned.add(new UnassignedGroup(group, saved.slots));
        }

        for (StashPlan.ShulkerData saved : data.shulkers == null ? List.<StashPlan.ShulkerData>of() : data.shulkers) {
            if (saved.pos == null) continue;

            ShulkerRef ref = new ShulkerRef(pos(saved.pos), saved.slot);
            ref.done = saved.done;
            session.shulkers.add(ref);
            session.restoredShulkers.put(StashSession.refKey(ref.pos, ref.slot), saved.done);
        }

        for (StashPlan.CarriedData saved : data.carried == null ? List.<StashPlan.CarriedData>of() : data.carried) {
            Item item = item(saved.item);
            if (item == Items.AIR) continue;

            // The slot may hold something else after a reconnection: only a stack that is still
            // there is delivered, the rest is sorted like any other item.
            ItemStack held = session.inventory.stack(saved.slot);
            if (held.isEmpty() || held.getItem() != item) continue;

            session.carried.put(saved.slot, new Carried(item, saved.group));
        }

        session.resumePhase = StashPhase.valueOf(data.phase);
        session.resumeAfterCollect = data.resumeAfterCollect == null ? null : StashPhase.valueOf(data.resumeAfterCollect);
        session.resuming = true;
    }

    public static void delete() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException e) {
            MeteorClient.LOG.warn("Could not delete the stash organizer session", e);
        }
    }

    private static void write(StashPlan.Data data) {
        try {
            Files.createDirectories(FOLDER);
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MeteorClient.LOG.warn("Could not save the stash organizer session", e);
        }
    }

    private static StashPlan.Data load() {
        if (!Files.isRegularFile(FILE)) return null;

        try {
            StashPlan.Data data = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), StashPlan.Data.class);

            if (data == null) {
                MeteorClient.LOG.warn("Stash organizer: the saved plan is empty, starting a fresh run.");
                return null;
            }

            if (data.version != StashPlan.VERSION) {
                // A plan from another version is dropped, but the player is told instead of the
                // run silently starting over.
                MeteorClient.LOG.warn("Stash organizer: saved plan version {} is not supported (expected {}), starting a fresh run.",
                    data.version, StashPlan.VERSION);
                return null;
            }

            return data;
        } catch (Exception e) {
            MeteorClient.LOG.warn("Stash organizer: could not read the saved plan, starting a fresh run.", e);
            return null;
        }
    }

    private static boolean isResumable(String phase) {
        try {
            return StashPhase.valueOf(phase).resumable;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Item item(String id) {
        if (id == null) return Items.AIR;

        Identifier identifier = Identifier.tryParse(id);
        return identifier == null ? Items.AIR : Registries.ITEM.get(identifier);
    }
}
