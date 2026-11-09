package com.pro0101.stashorganizer.modules.stashorganizer;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Locale;

/**
 * Opens every nearby chest/barrel/shulker, then reorganises the whole stash in place: items are
 * grouped (exact item, vanilla family, broad category or material) and the existing containers are reused as
 * the chests of those groups. When the existing containers do not provide enough room, additional
 * chests are placed in a free area next to the stash. Every sorted chest finally gets an item
 * frame label with a representative item.
 * <p>
 * Shulker boxes found in the stash are opened and unpacked, so shulkers holding several different
 * items are sorted item by item. The empty shulkers are stored with the other shulker boxes.
 * Overflow shulkers can additionally be filled, mined with a pickaxe and stored as packed shulkers.
 * Boxes kept by the protection rules (renamed ones such as "tools" or "kit", boxes holding a
 * single item type, boxes containing gear and boxes holding a protected item) are never opened.
 * They are still sorted into the chests like normal items, so the stash stays organised without
 * scattering their contents; each box joins the chest of the item it holds when possible.
 * <p>
 * Shulker boxes found lying on the floor are mined, picked up and stored with the other boxes, and
 * a box taken out to be emptied is placed, opened, emptied, mined again and stored as an empty box.
 * <p>
 * Movement uses Baritone's API directly when Baritone is installed, and falls back to Meteor's
 * path manager otherwise.
 * <p>
 * The work is split between components: {@link StashScanner} (discovery and reading),
 * {@link StashPlanner} (group assignment), {@link ChestPlacer} (extra chests),
 * {@link ItemSorter} (moving stacks), {@link ShulkerUnpacker}, {@link ShulkerPacker} and
 * {@link ChestLabeler}. They share a {@link StashSession}.
 */
public class StashOrganizer extends Module implements SessionHost {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgProtection = settings.createGroup("Shulker protection");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Radius (in blocks) used to look for containers to organise.")
        .defaultValue(32)
        .range(8, 64)
        .sliderRange(8, 64)
        .build()
    );

    private final Setting<ItemGrouping.Mode> groupMode = sgGeneral.add(new EnumSetting.Builder<ItemGrouping.Mode>()
        .name("grouping")
        .description("How items are grouped: exact item, vanilla family, broad category or metal/gem material.")
        .defaultValue(ItemGrouping.Mode.Family)
        .build()
    );

    private final Setting<Preset> preset = sgGeneral.add(new EnumSetting.Builder<Preset>()
        .name("preset")
        .description("Applies a ready-made tuning when the module starts: fast server, laggy server or dry run.")
        .defaultValue(Preset.Custom)
        .build()
    );

    private final Setting<Timings.Latency> latency = sgGeneral.add(new EnumSetting.Builder<Timings.Latency>()
        .name("latency")
        .description("Scales every wait between two actions for the server's response time.")
        .defaultValue(Timings.Latency.Normal)
        .build()
    );

    private final Setting<List<String>> extraContainers = sgGeneral.add(new StringListSetting.Builder()
        .name("extra-containers")
        .description("Additional block ids to treat as storage (e.g. somemod:crate).")
        .build()
    );

    private final Setting<Boolean> excludeEnderChest = sgGeneral.add(new BoolSetting.Builder()
        .name("exclude-ender-chest")
        .description("Never open or count the ender chest: it is your own storage, not part of the stash.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> createKits = sgGeneral.add(new BoolSetting.Builder()
        .name("create-kits")
        .description("Group armour, tools and weapons by material into kits; pack a kit into a shulker when no chest has room.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> unpackShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("unpack-shulkers")
        .description("Open shulker boxes found in the stash and sort their contents.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> protectNames = sgProtection.add(new StringListSetting.Builder()
        .name("keep-names")
        .description("Shulker boxes whose name contains one of these words are never opened (e.g. tools, kit).")
        .defaultValue("tools", "kit")
        .build()
    );

    private final Setting<Boolean> protectSingleItem = sgProtection.add(new BoolSetting.Builder()
        .name("keep-single-item")
        .description("Never open shulker boxes that hold a single item type (bulk storage).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> protectGear = sgProtection.add(new BoolSetting.Builder()
        .name("keep-gear")
        .description("Never open shulker boxes that contain armour, tools or weapons (kits).")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> protectItems = sgProtection.add(new StringListSetting.Builder()
        .name("keep-items")
        .description("Never open shulker boxes that contain one of these items (ids, e.g. minecraft:diamond_block).")
        .build()
    );

    private final Setting<Boolean> keepRenamed = sgProtection.add(new BoolSetting.Builder()
        .name("keep-renamed")
        .description("Never open shulker boxes the player renamed (they are reported at the end of the run).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> frameLabels = sgGeneral.add(new BoolSetting.Builder()
        .name("read-frame-labels")
        .description("Detect the item already displayed on each chest's item frame and store the items related to it in that chest first.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> arrangeChests = sgGeneral.add(new BoolSetting.Builder()
        .name("arrange-chests")
        .description("Tidy each chest at the end: the stacks of one item together, the groups in plan order, empty slots at the bottom.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> labelChests = sgGeneral.add(new BoolSetting.Builder()
        .name("item-frames")
        .description("Put an item frame with a representative item on every sorted chest.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> dumpInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("dump-inventory")
        .description("Empty the player inventory into staging chests before sorting, so it can be used as a clean buffer.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shulkerOverflow = sgGeneral.add(new BoolSetting.Builder()
        .name("shulker-overflow")
        .description("Use empty shulker boxes as extra storage when there are not enough chests for a group.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> packShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("pack-shulkers")
        .description("Fill overflow shulkers, mine them and store the filled shulker in a chest (needs a pickaxe).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> resumeSession = sgGeneral.add(new BoolSetting.Builder()
        .name("resume-session")
        .description("Resume the saved plan after a disconnection or a crash.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> batchSize = sgGeneral.add(new IntSetting.Builder()
        .name("batch-size")
        .description("Maximum number of stacks carried between two chest visits.")
        .defaultValue(6)
        .range(1, 18)
        .sliderRange(1, 18)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between two actions.")
        .defaultValue(3)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> actionTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("action-timeout")
        .description("Seconds before walking to a block or opening a chest is considered failed.")
        .defaultValue(25)
        .range(5, 120)
        .sliderRange(5, 120)
        .build()
    );

    private final Setting<Boolean> dryRun = sgGeneral.add(new BoolSetting.Builder()
        .name("dry-run")
        .description("Only scan and report the plan, without moving a single item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> highlight = sgRender.add(new BoolSetting.Builder()
        .name("highlight")
        .description("Outline the container the organizer is working on and the delivery destination.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> targetColor = sgRender.add(new ColorSetting.Builder()
        .name("target-color")
        .description("Colour of the container the organizer is working on.")
        .defaultValue(new SettingColor(90, 200, 120, 120))
        .build()
    );

    private final Setting<SettingColor> destinationColor = sgRender.add(new ColorSetting.Builder()
        .name("destination-color")
        .description("Colour of the container the carried items are going to.")
        .defaultValue(new SettingColor(90, 150, 255, 120))
        .build()
    );

    private StashSession session;
    private int saveTicks;
    private int statusTicks;

    public StashOrganizer() {
        super(StashOrganizerAddon.CATEGORY, "stash-organizer", "Opens nearby storages and reorganises them by item groups, with item frame labels, shulker packing and Baritone movement.");
    }

    @Override
    public void onActivate() {
        // Meteor calls onActivate once when toggled and again on GameJoined for active modules.
        if (session != null) return;

        applyPreset();

        if (mc.player == null || mc.world == null) {
            error("No world loaded.");
            toggle();
            return;
        }

        StashMover mover = createMover();
        if (mover == null || !mover.isAvailable()) {
            error("No pathing backend available. Install Baritone first.");
            toggle();
            return;
        }

        mover.prepare();

        StashSession started = new StashSession(this, mover);
        started.report = new FileReport(started);
        started.history = new FileHistory();

        boolean resumed = resumeSession.get() && StashSessionStore.apply(started);

        if (resumed) {
            if (!started.startResume()) {
                started.shutdown();
                toggle();
                return;
            }

            info("Resuming the saved stash plan.");
        } else if (!started.start()) {
            started.shutdown();
            toggle();
            return;
        }

        saveTicks = 0;
        statusTicks = 0;
        session = started;
    }

    @Override
    public void onDeactivate() {
        if (session != null) {
            // A running session is always kept: on a disconnect the world is still loaded when
            // Meteor calls this, so a world check cannot tell a disconnect from a manual toggle.
            if (session.isRunning()) StashSessionStore.save(session);

            session.shutdown();
            session = null;
        }

        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.empty(), false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (session == null || !session.isRunning()) return;

        if (mc.player == null || mc.world == null) {
            error("World unloaded, stopping (the plan is kept for the next connection).");
            toggle();
            return;
        }

        if (++saveTicks % 200 == 0) StashSessionStore.save(session);

        if (++statusTicks % 20 == 0) {
            String status = session.progressText();
            if (status != null && mc.inGameHud != null) {
                mc.inGameHud.setOverlayMessage(Text.literal("[Stash] " + status), false);
            }
        }

        if (session.delayTicks > 0) {
            session.delayTicks--;
            return;
        }

        session.phase.tick(session).run();
    }

    /** Outlines the container the current phase is working on and the delivery destination. */
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!highlight.get() || session == null || !session.isRunning()) return;

        for (StashHighlight marker : session.highlights()) {
            Color color = marker.kind() == StashHighlight.Kind.Destination
                ? destinationColor.get()
                : targetColor.get();

            event.renderer.box(new Box(marker.pos()), color, color, ShapeMode.Both, 0);
        }
    }

    /** Loads the direct Baritone mover when possible, otherwise falls back to Meteor's pathing. */
    private StashMover createMover() {
        if (BaritoneUtils.IS_AVAILABLE) {
            try {
                return (StashMover) Class.forName("com.pro0101.stashorganizer.modules.stashorganizer.BaritoneMover")
                    .getDeclaredConstructor()
                    .newInstance();
            } catch (Throwable ignored) {
                // Fall through to the path manager.
            }
        }

        PathManagerMover fallback = new PathManagerMover();
        return fallback.isAvailable() ? fallback : null;
    }

    @Override
    public int radius() {
        return range.get();
    }

    @Override
    public Timings.Latency latency() {
        return latency.get();
    }

    @Override
    public List<String> extraContainers() {
        return extraContainers.get();
    }

    @Override
    public boolean excludesEnderChest() {
        return excludeEnderChest.get();
    }

    @Override
    public boolean readsFrameLabels() {
        return frameLabels.get();
    }

    @Override
    public boolean keepsRenamed() {
        return keepRenamed.get();
    }

    /** Ready-made tuning applied when the module starts; {@code Custom} keeps the settings as they are. */
    public enum Preset {
        Custom,
        /** Low latency server: shorter waits, bigger batches. */
        Fast,
        /** High latency server: long waits, small batches and a generous timeout. */
        Laggy,
        /** Only scan and report, never move an item. */
        DryRun
    }

    /** Applies the selected preset to the settings it owns. */
    private void applyPreset() {
        switch (preset.get()) {
            case Fast -> {
                latency.set(Timings.Latency.Fast);
                delay.set(1);
                batchSize.set(8);
                actionTimeout.set(15);
                dryRun.set(false);
            }
            case Laggy -> {
                latency.set(Timings.Latency.VerySlow);
                delay.set(6);
                batchSize.set(4);
                actionTimeout.set(60);
                dryRun.set(false);
            }
            case DryRun -> dryRun.set(true);
            case Custom -> {
            }
        }
    }

    @Override
    public ItemGrouping.Mode mode() {
        return groupMode.get();
    }

    @Override
    public boolean createsKits() {
        return createKits.get();
    }

    @Override
    public boolean unpacksShulkers() {
        return unpackShulkers.get();
    }

    @Override
    public List<String> protectNames() {
        return protectNames.get();
    }

    @Override
    public boolean protectSingleItem() {
        return protectSingleItem.get();
    }

    @Override
    public boolean protectGear() {
        return protectGear.get();
    }

    @Override
    public List<String> protectItems() {
        return protectItems.get();
    }

    @Override
    public boolean labelsChests() {
        return labelChests.get();
    }

    @Override
    public boolean arrangesChests() {
        return arrangeChests.get();
    }

    @Override
    public int batch() {
        return batchSize.get();
    }

    @Override
    public int actionDelay() {
        return delay.get();
    }

    @Override
    public int timeoutTicks() {
        return actionTimeout.get() * 20;
    }

    @Override
    public boolean isDryRun() {
        return dryRun.get();
    }

    @Override
    public boolean dumpsInventory() {
        return dumpInventory.get();
    }

    @Override
    public boolean allowOverflowShulkers() {
        return shulkerOverflow.get();
    }

    public boolean resumesSession() {
        return resumeSession.get();
    }

    @Override
    public boolean packsShulkers() {
        return packShulkers.get() && shulkerOverflow.get();
    }

    /** Lines shown by the stash HUD: the phase detail, then the stash counters. */
    public List<String> hudLines() {
        if (session == null || !session.isRunning()) return List.of();

        int scanned = 0;
        for (StashContainer container : session.containers) if (container.scanned) scanned++;

        return List.of(
            session.progressText(),
            scanned + " containers, " + session.groups.size() + " groups",
            session.totalMoves + " moves, " + session.carried.size() + " carried",
            throughputLine()
        );
    }

    /** Throughput of the run, with the estimated time left in the current phase. */
    private String throughputLine() {
        String rate = String.format(Locale.ROOT, "%.1f moves/min", session.movesPerMinute());
        int eta = session.etaSeconds();

        return eta < 0 ? rate : rate + ", eta " + duration(eta);
    }

    private static String duration(int seconds) {
        int minutes = seconds / 60;
        int rest = seconds % 60;

        return minutes > 0 ? minutes + "m " + rest + "s" : rest + "s";
    }

    /** Completion of the current phase in {@code [0, 1]}, or -1 when it cannot be measured. */
    public double hudProgress() {
        return session != null && session.isRunning() ? session.progress() : -1;
    }

    @Override
    public String getInfoString() {
        return session != null && session.isRunning() ? session.progressText() : null;
    }
}
