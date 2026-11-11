package com.pro0101.stashorganizer.render;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Loads a panorama the user drops in their config folder.
 *
 * <p>The file is resolved under {@code config/stash-organizer/skybox/} and registered as a plain
 * game texture, so the sky pipeline can bind it like any other: no resource pack, no rebuild. Any
 * resolution works, since the shader samples it by heading and elevation; a 2:1 image is the
 * natural shape.
 *
 * <p>Only the currently requested file is held. Changing the name in the settings releases the old
 * texture and registers the new one on the next frame, which is also what makes swapping an image
 * possible without restarting the game.
 */
public final class SkyboxImage {
    /** Folder the panorama files are read from. */
    public static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir()
        .resolve("stash-organizer")
        .resolve("skybox");

    private static String loadedName;
    private static Identifier loadedId;
    private static boolean warned;

    private SkyboxImage() {
    }

    /**
     * Returns the texture of a panorama, loading it when the name changed.
     *
     * @param fileName file name inside the skybox folder
     * @return texture identifier, or null when the file is missing or unreadable
     */
    public static Identifier get(String fileName) {
        String name = fileName == null ? "" : fileName.trim();

        if (name.isEmpty()) return null;
        if (name.equals(loadedName)) return loadedId;

        unload();
        load(name);

        return loadedId;
    }

    /** Releases the texture currently held, if any. */
    public static void unload() {
        TextureManager manager = MinecraftClient.getInstance().getTextureManager();

        if (loadedId != null && manager != null) {
            manager.destroyTexture(loadedId);
        }

        loadedName = null;
        loadedId = null;
    }

    /**
     * Reads one file and registers it, reporting a single warning when it cannot be used.
     *
     * @param name file name inside the skybox folder
     */
    private static void load(String name) {
        Path file = DIRECTORY.resolve(name);
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.getTextureManager() == null) return;

        if (!Files.isRegularFile(file)) {
            warnOnce("No skybox image at " + file + ", the Image mode falls back to Sunset.");
            loadedName = name;
            return;
        }

        try (InputStream stream = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(stream);
            Identifier id = Identifier.of("stashorganizer", "skybox/" + sanitize(name));

            client.getTextureManager().registerTexture(
                id, new NativeImageBackedTexture(() -> "stash-organizer skybox " + name, image)
            );

            loadedName = name;
            loadedId = id;
            warned = false;

            StashOrganizerAddon.LOG.info(
                "Loaded skybox image {} ({}x{})", file, image.getWidth(), image.getHeight()
            );
        } catch (IOException | RuntimeException exception) {
            warnOnce("Could not read the skybox image " + file + ": " + exception.getMessage());
            loadedName = name;
        }
    }

    /**
     * Keeps a repeated failure to one line in the log.
     *
     * @param message warning to log
     */
    private static void warnOnce(String message) {
        if (warned) return;

        StashOrganizerAddon.LOG.warn(message);
        warned = true;
    }

    /**
     * Turns a file name into the path part of a texture identifier.
     *
     * @param name file name
     * @return identifier path, lowercase and free of separators
     */
    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
