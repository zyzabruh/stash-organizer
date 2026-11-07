package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.hud.HudExample;
import com.example.addon.hud.StashHud;
import com.example.addon.modules.stashorganizer.StashOrganizer;
import com.example.addon.modules.stashorganizer.StashSessionStore;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Stash");
    public static final HudGroup HUD_GROUP = new HudGroup("Stash");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Stash Organizer");

        // Modules
        Modules.get().add(new StashOrganizer());

        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(HudExample.INFO);
        Hud.get().register(StashHud.INFO);

        MeteorClient.EVENT_BUS.subscribe(this);
    }

    /** Re-enables the stash organizer when a saved plan belongs to the world being joined. */
    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        StashOrganizer module = Modules.get().get(StashOrganizer.class);

        if (module == null || module.isActive() || !module.resumesSession()) return;
        if (StashSessionStore.canResume()) module.toggle();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
