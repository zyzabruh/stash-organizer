package com.pro0101.stashorganizer.modules.stashorganizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Group to chest assignments of the previous runs, stored next to the Meteor folder. The planner
 * prefers a container that already held the group, so a stash keeps its layout between two runs
 * instead of rotating its contents.
 */
public final class FileHistory implements StashHistory {
    private static final int VERSION = 1;
    /** Homes kept per group: enough to survive a stash extension without growing forever. */
    private static final int MAX_HOMES = 8;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FOLDER = MeteorClient.FOLDER.toPath().resolve("stash-organizer");
    private static final Path FILE = FOLDER.resolve("groups.json");

    private final Map<String, List<Pos>> homes = new LinkedHashMap<>();

    public FileHistory() {
        load();
    }

    @Override
    public boolean isHome(String group, BlockPos pos) {
        if (group == null || pos == null) return false;

        List<Pos> positions = homes.get(group);
        if (positions == null) return false;

        return positions.contains(new Pos(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public void remember(String group, BlockPos pos) {
        if (group == null || pos == null) return;

        List<Pos> positions = homes.computeIfAbsent(group, key -> new ArrayList<>());
        Pos home = new Pos(pos.getX(), pos.getY(), pos.getZ());

        // The newest home comes first: a group that moved is looked up where it now lives.
        positions.remove(home);
        positions.add(0, home);

        while (positions.size() > MAX_HOMES) positions.remove(positions.size() - 1);
    }

    @Override
    public void save() {
        Data data = new Data();
        data.version = VERSION;
        data.homes.putAll(homes);

        try {
            Files.createDirectories(FOLDER);
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MeteorClient.LOG.warn("Could not save the stash organizer group history", e);
        }
    }

    private void load() {
        if (!Files.isRegularFile(FILE)) return;

        try {
            Data data = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.version != VERSION || data.homes == null) return;

            for (Map.Entry<String, List<Pos>> entry : data.homes.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                homes.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        } catch (Exception e) {
            MeteorClient.LOG.warn("Could not read the stash organizer group history", e);
        }
    }

    public static final class Data {
        public int version;
        public Map<String, List<Pos>> homes = new LinkedHashMap<>();
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
