package com.example.addon.modules.stashorganizer;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State of one organizer run. It owns the containers, the item groups and the inventory buffer,
 * and wires the different components (scanner, planner, sorter, unpacker, labeler) together.
 * <p>
 * Everything that needs the game is reached through a service ({@link Game}, {@link ContainerAccess},
 * {@link Navigator}, {@link InventoryTools}, {@link BlockSpots}), so a test can drive the scripts
 * of the components on fakes with {@link #forTest}.
 */
public final class StashSession {
    public final SessionHost host;
    public final StashMover mover;
    /** Waits scaled by the latency profile; read on every call so a change applies immediately. */
    public final Timings timings;

    public final List<StashContainer> containers = new ArrayList<>();
    public final List<ShulkerRef> shulkers = new ArrayList<>();
    public final Map<String, StashGroup> groups = new LinkedHashMap<>();
    public final List<UnassignedGroup> unassigned = new ArrayList<>();
    public final ArrayDeque<BlockPos> scanQueue = new ArrayDeque<>();
    public final Map<Integer, Carried> carried = new LinkedHashMap<>();
    public final Set<BlockPos> exhaustedSources = new HashSet<>();
    /**
     * Progress of the boxes restored from a saved plan, by {@link #refKey}. The references are
     * rebuilt during the rescan, so the flags are kept here until then.
     */
    public final Map<String, Boolean> restoredShulkers = new HashMap<>();

    public Game game;
    public BlockSpots spots;
    public ContainerAccess access;
    public Navigator navigator;
    public InventoryTools inventory;
    public Persistence persistence = new FilePersistence();
    public StashReport report = StashReport.NONE;
    public StashHistory history = StashHistory.NONE;

    public final ShulkerProtection protection;
    public final ChestPlacer placer;
    public final StashScanner scanner;
    public final StashPlanner planner;
    public final ItemSorter sorter;
    public final ShulkerUnpacker unpacker;
    public final ShulkerPacker packer;
    public final ChestLabeler labeler;
    public final InventoryDumper dumper;
    public final ShulkerCollector collector;

    public StashPhase phase = StashPhase.Idle;
    public StashPhase resumePhase;
    /** Phase a resumed run has to reach after the collect pass (unpack or pack), or null. */
    public StashPhase resumeAfterCollect;
    public boolean resuming;
    public String worldKey;
    public BlockPos dumpPos;
    public int delayTicks;
    public int totalMoves;
    public int placedChests;
    public int placedShulkers;
    public int packedShulkers;

    private long startedAt;
    private long phaseStartedAt;

    public StashSession(SessionHost host, StashMover mover) {
        this.host = host;
        this.mover = mover;
        this.timings = new Timings(host::latency);

        this.game = new MinecraftGame();
        this.spots = new MinecraftBlockSpots(this);
        this.access = new MinecraftContainerAccess(this);
        this.protection = new ShulkerProtection(this);
        this.navigator = new MinecraftNavigator(this);
        this.inventory = new MinecraftInventory();
        this.placer = new ChestPlacer(this);
        this.scanner = new StashScanner(this);
        this.planner = new StashPlanner(this);
        this.sorter = new ItemSorter(this);
        this.unpacker = new ShulkerUnpacker(this);
        this.packer = new ShulkerPacker(this);
        this.labeler = new ChestLabeler(this);
        this.dumper = new InventoryDumper(this);
        this.collector = new ShulkerCollector(this);
    }

    /**
     * Builds a session whose services are fakes, so the scripts of the components can run outside
     * the game. The components themselves are the real ones.
     */
    public static StashSession forTest(SessionHost host, StashMover mover, Game game, ContainerAccess access,
                                       Navigator navigator, InventoryTools inventory, BlockSpots spots,
                                       Persistence persistence) {
        StashSession session = new StashSession(host, mover);
        session.game = game;
        session.access = access;
        session.navigator = navigator;
        session.inventory = inventory;
        session.spots = spots;
        session.persistence = persistence;
        return session;
    }

    /** Saves the plan with the session persistence (nothing at all in a test). */
    public void persist() {
        persistence.save(this);
    }

    public void clearSaved() {
        persistence.delete();
    }

    /** Validates the starting conditions and starts the scan (or the inventory dump). */
    public boolean start() {
        if (!dumpsInventory() && inventory.freeSlots() < 8) {
            host.error("Free at least 8 inventory slots before starting, or enable dump-inventory.");
            return false;
        }

        if (!scanner.discover()) {
            host.error("No chest or barrel found in a %d block radius.", radius());
            return false;
        }

        worldKey = StashSessionStore.worldKey();
        resuming = false;
        resumeAfterCollect = null;
        startedAt = System.currentTimeMillis();
        phaseStartedAt = startedAt;
        phase = dumpsInventory() && dumper.hasDumpableItems() ? StashPhase.Dump : StashPhase.Scan;
        delayTicks = 0;
        report.phase(phase);
        return true;
    }

    /** Resumes a plan loaded by {@link StashSessionStore#apply(StashSession)}. */
    public boolean startResume() {
        if (resumePhase == null) return false;

        startedAt = System.currentTimeMillis();
        phaseStartedAt = startedAt;

        // A box left on the floor by an interrupted unpack or pack is collected first: the plan
        // does not know about it, but the stash must not keep boxes on its floor.
        resumeAfterCollect = resumePhase == StashPhase.Unpack || resumePhase == StashPhase.Pack ? resumePhase : null;

        if (dumpsInventory() && dumper.hasDumpableItems()) {
            phase = StashPhase.Dump;
            delayTicks = 0;
            report.phase(phase);
        } else {
            beginRescan();
        }

        return true;
    }

    /** Refreshes the contents of the saved containers, then returns to the saved phase. */
    public void beginRescan() {
        if (resumePhase == null) {
            stop("Nothing left to resume.");
            return;
        }

        scanner.beginRescan();
        phase = StashPhase.Rescan;
        delayTicks = 0;
        report.phase(phase);
    }

    public void shutdown() {
        mover.shutdown();
        access.close();
        phase = StashPhase.Done;
    }

    public boolean isRunning() {
        return phase != StashPhase.Idle && phase != StashPhase.Done;
    }

    public void act() {
        if (isRunning()) delayTicks = actionDelay();
    }

    public void nextPhase(StashPhase next) {
        if (!isRunning()) return;

        // The references recorded while scanning are already stale once the sort has moved a
        // protected box: rebuild them from the cached contents before the unpacker starts.
        if (next == StashPhase.Unpack) rebuildShulkerRefs();

        StashPhase previous = phase;
        phase = next;
        phaseStartedAt = System.currentTimeMillis();
        delayTicks = Math.max(1, actionDelay());
        report.phase(next);

        // Leaving the collect pass drops the target it was only inserted for.
        if (previous == StashPhase.Collect && next != StashPhase.Collect) resumeAfterCollect = null;

        // The sort has placed every group: this is the layout a later run should reuse.
        if (next == StashPhase.Collect) rememberHomes();

        if (next.resumable) {
            resumePhase = next;
            persist();
        }
    }

    /** Phase the collect pass hands over to: unpack normally, or the resumed phase. */
    public StashPhase collectTarget() {
        return resumeAfterCollect == null ? StashPhase.Unpack : resumeAfterCollect;
    }

    /**
     * Rebuilds {@link #shulkers} from the cached contents of every container, keeping only the
     * boxes the unpack phase still has to open. Doing it here (instead of only while scanning)
     * drops the references the sort has invalidated and de-duplicates the double chests, so the
     * unpacker never walks to and opens a container just to find an empty slot.
     * <p>
     * The progress saved with a plan survives the rebuild: a resumed run does not empty the same
     * boxes twice.
     */
    public void rebuildShulkerRefs() {
        Map<String, Boolean> done = new HashMap<>(restoredShulkers);
        for (ShulkerRef ref : shulkers) {
            if (ref.done) done.put(refKey(ref.pos, ref.slot), true);
        }

        unpacker.reset();
        shulkers.clear();

        if (!unpacksShulkers()) return;

        for (StashContainer container : containers) {
            if (container.failed || !container.scanned) continue;

            for (int slot = 0; slot < container.contents.size(); slot++) {
                if (!isBoxToUnpack(container.contents.get(slot))) continue;

                ShulkerRef ref = new ShulkerRef(container.pos, slot);
                ref.done = done.getOrDefault(refKey(ref.pos, ref.slot), false);
                shulkers.add(ref);
            }
        }
    }

    /** Stable identity of a shulker reference in the saved plan. */
    public static String refKey(BlockPos pos, int slot) {
        return pos.asLong() + "|" + slot;
    }

    /** True for a shulker box the unpack phase must open (the single unpack predicate). */
    public boolean isBoxToUnpack(ItemStack stack) {
        return unpacksShulkers()
            && stack.isIn(ItemTags.SHULKER_BOXES)
            && !InventoryTools.isEmptyShulker(stack)
            && !protection.protectsShulkerAsItem(stack);
    }

    /** Records the group of every sorted container, so the next run reuses the same chests. */
    public void rememberHomes() {
        for (StashContainer container : containers) {
            if (container.failed || container.groups.isEmpty()) continue;

            for (String group : container.groups) history.remember(group, container.pos);
        }
    }

    public void stop(String message, Object... args) {
        if (!isRunning()) return;
        phase = StashPhase.Done;
        clearSaved();
        report.finish("stopped");
        history.save();
        host.error(message, args);
        mover.stop();
        access.close();
        host.toggle();
    }

    /** Stops without deleting the saved plan, so a later connection can retry it. */
    public void pause(String message, Object... args) {
        if (!isRunning()) return;
        phase = StashPhase.Done;
        report.finish("paused");
        history.save();
        host.warning(message, args);
        mover.stop();
        access.close();
        host.toggle();
    }

    public void finish(String message, Object... args) {
        if (!isRunning()) return;
        phase = StashPhase.Done;
        rememberHomes();
        history.save();
        report.finish("finished");
        clearSaved();
        host.info(message, args);
        mover.stop();
        access.close();
        host.toggle();
    }

    public void info(String message, Object... args) {
        host.info(message, args);
    }

    public void warning(String message, Object... args) {
        host.warning(message, args);
    }

    /** Wall clock time of the run so far, in milliseconds. */
    public long elapsedMillis() {
        return startedAt == 0 ? 0 : Math.max(0, System.currentTimeMillis() - startedAt);
    }

    /** Delivered stacks per minute, measured over the whole run. */
    public double movesPerMinute() {
        long elapsed = elapsedMillis();
        if (elapsed < 1000) return 0;

        return totalMoves * 60000.0 / elapsed;
    }

    /**
     * Estimated seconds left in the current phase, or -1 while it cannot be measured. The estimate
     * assumes the rest of the phase runs at the pace of the part already done.
     */
    public int etaSeconds() {
        double progress = progress();
        long phaseElapsed = System.currentTimeMillis() - phaseStartedAt;

        if (progress <= 0 || progress >= 1 || phaseElapsed < 1000) return -1;

        return (int) Math.round(phaseElapsed / 1000.0 * (1 - progress) / progress);
    }

    /**
     * Group of a stack. A shulker box joins the group of the item it mostly holds (or of its kit),
     * so boxes are spread over the stash with their items instead of all landing in one chest. An
     * empty box keeps the group of its own item and stays with the other boxes.
     */
    public String groupOf(ItemStack stack) {
        if (stack.isIn(ItemTags.SHULKER_BOXES)) {
            ItemStack dominant = ItemGrouping.dominantContent(stack);
            if (dominant != null) return groupOfItem(dominant);
        }

        return groupOfItem(stack);
    }

    private String groupOfItem(ItemStack stack) {
        if (createsKits() && ItemGrouping.isGear(stack)) return ItemGrouping.kitKey(stack);
        return ItemGrouping.key(stack, mode());
    }

    public boolean createsKits() {
        return host.createsKits();
    }

    public int radius() {
        return host.radius();
    }

    public ItemGrouping.Mode mode() {
        return host.mode();
    }

    public boolean unpacksShulkers() {
        return host.unpacksShulkers();
    }

    public List<String> protectNames() {
        return host.protectNames();
    }

    public boolean protectSingleItem() {
        return host.protectSingleItem();
    }

    public boolean protectGear() {
        return host.protectGear();
    }

    public List<String> protectItems() {
        return host.protectItems();
    }

    public boolean keepsRenamed() {
        return host.keepsRenamed();
    }

    public List<String> extraContainers() {
        return host.extraContainers();
    }

    public boolean labelsChests() {
        return host.labelsChests();
    }

    public int batch() {
        return host.batch();
    }

    public int actionDelay() {
        return host.actionDelay();
    }

    public int timeoutTicks() {
        return host.timeoutTicks();
    }

    public boolean isDryRun() {
        return host.isDryRun();
    }

    public boolean dumpsInventory() {
        return host.dumpsInventory();
    }

    public boolean allowOverflowShulkers() {
        return host.allowOverflowShulkers();
    }

    public boolean packsShulkers() {
        return host.packsShulkers();
    }

    /**
     * Blocks the overlay should outline: the container the current phase is working on, and the
     * container the carried items are going to. The session decides, so the renderer only draws.
     */
    public List<StashHighlight> highlights() {
        List<StashHighlight> highlights = new ArrayList<>(2);

        switch (phase) {
            case Dump -> add(highlights, dumpPos, StashHighlight.Kind.Target);
            case Scan, Rescan -> add(highlights, scanner.currentTarget(), StashHighlight.Kind.Target);
            case Place -> add(highlights, placer.currentTarget(), StashHighlight.Kind.Target);
            case Sort -> {
                add(highlights, sorter.currentSource(), StashHighlight.Kind.Target);
                add(highlights, sorter.currentDestination(), StashHighlight.Kind.Destination);
            }
            case Collect -> add(highlights, collector.currentTarget(), StashHighlight.Kind.Target);
            case Unpack -> add(highlights, unpacker.currentTarget(), StashHighlight.Kind.Target);
            case Pack -> {
                add(highlights, packer.currentTarget(), StashHighlight.Kind.Target);
                add(highlights, packer.currentOutput(), StashHighlight.Kind.Destination);
            }
            case Label -> add(highlights, labeler.currentChest(), StashHighlight.Kind.Target);
            default -> { }
        }

        return highlights;
    }

    private static void add(List<StashHighlight> highlights, BlockPos pos, StashHighlight.Kind kind) {
        if (pos != null) highlights.add(new StashHighlight(pos, kind));
    }

    /** Completion of the current phase in {@code [0, 1]}, or -1 when it has no measurable total. */
    public double progress() {
        return switch (phase) {
            case Scan, Rescan -> {
                int scanned = 0;
                for (StashContainer container : containers) if (container.scanned) scanned++;

                int total = scanned + scanQueue.size();
                yield total == 0 ? 1 : (double) scanned / total;
            }
            case Collect -> collector.fraction();
            case Unpack -> unpacker.fraction();
            case Pack -> packer.fraction();
            case Label -> labeler.fraction();
            default -> -1;
        };
    }

    /** Short, human readable state used by the HUD and the action bar. */
    public String progressText() {
        if (!isRunning()) return null;

        int scanned = 0;
        for (StashContainer container : containers) if (container.scanned) scanned++;

        return switch (phase) {
            case Dump -> "Dump " + dumper.status();
            case Scan, Rescan -> "Scan " + scanned + "/" + (scanned + scanQueue.size()) + " containers";
            case Place -> "Place " + unassigned.size() + " group(s) left, "
                + placedChests + " chest(s), " + placedShulkers + " shulker(s)";
            case Sort -> "Sort " + totalMoves + " moves, " + carried.size() + " carried";
            case Collect -> "Collect " + collector.progress();
            case Unpack -> "Unpack " + unpacker.progress();
            case Pack -> "Pack " + packer.progress();
            case Label -> "Label " + labeler.progress();
            default -> phase.name();
        };
    }
}
