package com.pro0101.stashorganizer;

import com.mojang.logging.LogUtils;
import com.pro0101.stashorganizer.hud.ChestCounterHud;
import com.pro0101.stashorganizer.hud.DiscoveryHud;
import com.pro0101.stashorganizer.hud.PresetPreviewHud;
import com.pro0101.stashorganizer.hud.FlightHud;
import com.pro0101.stashorganizer.hud.StashHud;
import com.pro0101.stashorganizer.modules.bephax.AutoDyeShulkers;
import com.pro0101.stashorganizer.modules.bephax.BetterStashFinder;
import com.pro0101.stashorganizer.modules.bephax.ChestIndex;
import com.pro0101.stashorganizer.modules.bephax.searcharea.SearchArea;
import com.pro0101.stashorganizer.modules.bephax.StashBrander;
import com.pro0101.stashorganizer.modules.huntbuddy.ElytraRecast;
import com.pro0101.stashorganizer.modules.huntbuddy.HotbarRefill;
import com.pro0101.stashorganizer.modules.huntbuddy.InventoryCleaner;
import com.pro0101.stashorganizer.modules.huntbuddy.Pitch40;
import com.pro0101.stashorganizer.modules.huntbuddy.RocketBoost;
import com.pro0101.stashorganizer.modules.huntbuddy.RocketFly;
import com.pro0101.stashorganizer.modules.huntbuddy.SignHistory;
import com.pro0101.stashorganizer.util.XaeroCompat;
import com.pro0101.stashorganizer.modules.huntbuddy.sorter.ShulkerSorter;
import com.pro0101.stashorganizer.modules.hyperglide.AirPlace;
import com.pro0101.stashorganizer.modules.hyperglide.AutoPilot;
import com.pro0101.stashorganizer.modules.hyperglide.BounceFly;
import com.pro0101.stashorganizer.modules.hyperglide.BoostBounds;
import com.pro0101.stashorganizer.modules.hyperglide.RocketCycle;
import com.pro0101.stashorganizer.modules.hyperglide.ControlFly;
import com.pro0101.stashorganizer.modules.hyperglide.ElytraTweaks;
import com.pro0101.stashorganizer.modules.hyperglide.EasyAccess;
import com.pro0101.stashorganizer.modules.render.Shaders;
import com.pro0101.stashorganizer.modules.render.Skybox;
import com.pro0101.stashorganizer.modules.hyperglide.FastPortal;
import com.pro0101.stashorganizer.modules.hyperglide.FastFrame;
import com.pro0101.stashorganizer.modules.hyperglide.MiningTweaks;
import com.pro0101.stashorganizer.modules.hyperglide.Navigation;
import com.pro0101.stashorganizer.modules.hyperglide.Overview;
import com.pro0101.stashorganizer.modules.stashorganizer.StashOrganizer;
import com.pro0101.stashorganizer.modules.stashorganizer.StashSessionStore;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import org.slf4j.Logger;

public class StashOrganizerAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Stash");
    public static final HudGroup HUD_GROUP = new HudGroup("Stash");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Stash Organizer");

        // Modules
        Modules.get().add(new StashOrganizer());

        // Modules imported from other addons, GPL-3.0, see CREDITS.md
        Modules.get().add(new FastFrame());
        Modules.get().add(new Overview());
        Modules.get().add(new Navigation());
        Modules.get().add(new AutoPilot());
        Modules.get().add(new AirPlace());
        Modules.get().add(new BounceFly());
        Modules.get().add(new ControlFly());
        Modules.get().add(new ElytraTweaks());
        Modules.get().add(new BoostBounds());
        Modules.get().add(new RocketCycle());
        Modules.get().add(new MiningTweaks());
        Modules.get().add(new ShulkerSorter());
        Modules.get().add(new SignHistory());
        Modules.get().add(new HotbarRefill());
        Modules.get().add(new InventoryCleaner());
        Modules.get().add(new Skybox());
        Modules.get().add(new Shaders());
        Modules.get().add(new RocketFly());
        Modules.get().add(new Pitch40());
        Modules.get().add(new ElytraRecast());
        Modules.get().add(new RocketBoost());
        Modules.get().add(new AutoDyeShulkers());
        Modules.get().add(new StashBrander());
        Modules.get().add(new ChestIndex());
        Modules.get().add(new BetterStashFinder());
        Modules.get().add(new SearchArea());
        Modules.get().add(new FastPortal());
        Modules.get().add(new EasyAccess());

        // Built against Xaero's Minimap, World Map and XaeroPlus. Registered by name so their classes are never loaded
        // when those mods are missing, instead of failing to verify and taking the whole addon down with them.
        if (XaeroCompat.isAvailable()) {
            XaeroCompat.register("com.pro0101.stashorganizer.modules.huntbuddy.TrailFollower");
            XaeroCompat.register("com.pro0101.stashorganizer.modules.huntbuddy.ChunkRadar");
        } else {
            LOG.info(
                "Xaero's Minimap, Xaero's World Map and XaeroPlus not found: trail-follower and chunk-radar stay disabled."
            );
        }

        // HUD
        Hud.get().register(StashHud.INFO);
        Hud.get().register(FlightHud.INFO);
        Hud.get().register(DiscoveryHud.INFO);
        Hud.get().register(PresetPreviewHud.INFO);
        Hud.get().register(ChestCounterHud.INFO);

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
        return "com.pro0101.stashorganizer";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("zyzabruh", "stash-organizer");
    }
}
