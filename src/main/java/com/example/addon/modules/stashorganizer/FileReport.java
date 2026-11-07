package com.example.addon.modules.stashorganizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the end-of-run report next to the Meteor folder and prints its summary in chat: which
 * container holds which group, how long each phase took, which boxes were kept shut (and why) and
 * which stacks could not be delivered. The dry run writes the same report, so a plan can be
 * reviewed before moving a single item.
 */
public final class FileReport implements StashReport {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FOLDER = MeteorClient.FOLDER.toPath().resolve("stash-organizer");
    private static final Path FILE = FOLDER.resolve("report.json");

    private final StashSession session;
    private final long startedAt = System.currentTimeMillis();
    private final Map<String, Long> phaseMillis = new LinkedHashMap<>();
    private final Map<String, List<Pos>> homes = new LinkedHashMap<>();
    private final Map<String, Integer> undelivered = new LinkedHashMap<>();
    private final List<ProtectedBox> protectedBoxes = new ArrayList<>();
    private final Set<String> renamed = new LinkedHashSet<>();

    private StashPhase phase;
    private long phaseSince = startedAt;
    private boolean finished;

    public FileReport(StashSession session) {
        this.session = session;
    }

    @Override
    public void phase(StashPhase next) {
        if (finished) return;

        long now = System.currentTimeMillis();
        if (phase != null) phaseMillis.merge(phase.name(), now - phaseSince, Long::sum);

        phase = next;
        phaseSince = now;
    }

    @Override
    public void undelivered(String reason, int stacks) {
        if (finished || stacks <= 0) return;
        undelivered.merge(reason, stacks, Integer::sum);
    }

    @Override
    public void protectedBox(BlockPos pos, String name, String reason) {
        if (finished || pos == null) return;

        for (ProtectedBox box : protectedBoxes) {
            if (box.pos.equals(pos) && box.reason.equals(reason)) return;
        }

        protectedBoxes.add(new ProtectedBox(pos, name, reason));
    }

    @Override
    public void renamed(String name) {
        if (finished || name == null || name.isEmpty()) return;
        renamed.add(name);
    }

    @Override
    public void finish(String outcome) {
        if (finished) return;
        finished = true;

        long now = System.currentTimeMillis();
        if (phase != null) phaseMillis.merge(phase.name(), now - phaseSince, Long::sum);
        phase = null;

        collectHomes();
        write(outcome, now);
        summary(outcome);
    }

    /** The final assignment of every container, read from the containers themselves. */
    private void collectHomes() {
        for (StashContainer container : session.containers) {
            if (container.failed || container.groups.isEmpty()) continue;

            for (String group : container.groups) {
                List<Pos> positions = homes.computeIfAbsent(group, key -> new ArrayList<>());
                if (!positions.contains(pos(container.pos))) positions.add(pos(container.pos));
            }
        }
    }

    private void write(String outcome, long now) {
        Data data = new Data();
        data.version = VERSION;
        data.writtenAt = Instant.ofEpochMilli(now).toString();
        data.world = session.worldKey;
        data.mode = session.host.mode().name();
        data.outcome = outcome;
        data.durationMillis = now - startedAt;
        data.containers = session.containers.size();
        data.groups = session.groups.size();
        data.totalMoves = session.totalMoves;
        data.placedChests = session.placedChests;
        data.placedShulkers = session.placedShulkers;
        data.packedShulkers = session.packedShulkers;

        for (Map.Entry<String, Long> entry : phaseMillis.entrySet()) {
            PhaseData saved = new PhaseData();
            saved.phase = entry.getKey();
            saved.millis = entry.getValue();
            data.phases.add(saved);
        }

        for (Map.Entry<String, List<Pos>> entry : homes.entrySet()) {
            HomeData saved = new HomeData();
            saved.group = entry.getKey();
            saved.containers = new ArrayList<>(entry.getValue());
            data.homes.add(saved);
        }

        for (Map.Entry<String, Integer> entry : undelivered.entrySet()) {
            UndeliveredData saved = new UndeliveredData();
            saved.reason = entry.getKey();
            saved.stacks = entry.getValue();
            data.undelivered.add(saved);
        }

        data.protectedBoxes.addAll(protectedBoxes);
        data.renamed.addAll(renamed);

        try {
            Files.createDirectories(FOLDER);
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MeteorClient.LOG.warn("Could not write the stash organizer report", e);
        }
    }

    private void summary(String outcome) {
        session.info("Report (%s): %d group(s), %d container(s), %d move(s), %d chest(s) and %d shulker(s) placed, %d packed.",
            outcome, session.groups.size(), session.containers.size(), session.totalMoves,
            session.placedChests, session.placedShulkers, session.packedShulkers);

        if (!protectedBoxes.isEmpty()) {
            Map<String, Integer> reasons = new LinkedHashMap<>();
            for (ProtectedBox box : protectedBoxes) reasons.merge(box.reason, 1, Integer::sum);

            session.info("Kept %d shulker box(es) shut: %s.", protectedBoxes.size(), join(reasons));
        }

        if (!undelivered.isEmpty()) {
            session.info("Left in place: %s.", join(undelivered));
        }

        if (!renamed.isEmpty()) {
            session.info("Renamed box(es) found: %s; add a word to keep-names to keep them shut.", String.join(", ", renamed));
        }
    }

    private static String join(Map<String, Integer> counts) {
        StringBuilder text = new StringBuilder();

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (text.length() > 0) text.append(", ");
            text.append(entry.getValue()).append("x ").append(entry.getKey());
        }

        return text.toString();
    }

    private static Pos pos(BlockPos pos) {
        return new Pos(pos.getX(), pos.getY(), pos.getZ());
    }

    public static final class Data {
        public int version;
        public String writtenAt;
        public String world;
        public String mode;
        public String outcome;
        public long durationMillis;
        public int containers;
        public int groups;
        public int totalMoves;
        public int placedChests;
        public int placedShulkers;
        public int packedShulkers;
        public List<PhaseData> phases = new ArrayList<>();
        public List<HomeData> homes = new ArrayList<>();
        public List<UndeliveredData> undelivered = new ArrayList<>();
        public List<ProtectedBox> protectedBoxes = new ArrayList<>();
        public List<String> renamed = new ArrayList<>();
    }

    public static final class PhaseData {
        public String phase;
        public long millis;
    }

    public static final class HomeData {
        public String group;
        public List<Pos> containers = new ArrayList<>();
    }

    public static final class UndeliveredData {
        public String reason;
        public int stacks;
    }

    public static final class ProtectedBox {
        public Pos pos;
        public String name;
        public String reason;

        public ProtectedBox() {
        }

        public ProtectedBox(BlockPos pos, String name, String reason) {
            this.pos = pos(pos);
            this.name = name;
            this.reason = reason;
        }
    }

    public static final class Pos {
        public int x;
        public int y;
        public int z;

        public Pos() {
        }

        public Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Pos otherPos)) return false;
            return x == otherPos.x && y == otherPos.y && z == otherPos.z;
        }

        @Override
        public int hashCode() {
            return (x * 31 + y) * 31 + z;
        }
    }
}
