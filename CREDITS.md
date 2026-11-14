# Credits

Stash Organizer includes modules taken from three GPL-3.0 addons. Their code stays under the GNU General Public
License v3.0, keeps its original authorship, and is distributed here under the same license (see `LICENSE`).

| Module | Source | Author | Commit | License |
|---|---|---|---|---|
| `fast-frame` (`FastFrame`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `fast-portal` (`FastPortal`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `easy-access` (`EasyAccess`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `overview` (`Overview`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `navigation` (`Navigation`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `auto-pilot` (`AutoPilot`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `elytra-tweaks` (`ElytraTweaks`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `bounce-fly` (`BounceFly`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `control-fly` (`ControlFly`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `air-place` (`AirPlace`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `mining-tweaks` (`MiningTweaks`) | [Ark223/Hyperglide](https://github.com/Ark223/Hyperglide) | Ark223 | `68a6768` | GPL-3.0 |
| `shulker-sorter` (`ShulkerSorter`) and its `sorter` support package | [Likon69/HunterBuddy](https://github.com/Likon69/HunterBuddy) | Likon69 | `4cae4b1` | GPL-3.0 |
| `sign-history` (`SignHistory`) and its `signs` support package | [Likon69/HunterBuddy](https://github.com/Likon69/HunterBuddy) | Likon69 | `4cae4b1` | GPL-3.0 |
| `trail-follower` (`TrailFollower`) | [Likon69/HunterBuddy](https://github.com/Likon69/HunterBuddy) | Likon69 | `4cae4b1` | GPL-3.0 |
| `chunk-radar` (`ChunkRadar`) | [Likon69/HunterBuddy](https://github.com/Likon69/HunterBuddy) | Likon69 | `4cae4b1` | GPL-3.0 |
| `pitch-40` (`Pitch40`), `elytra-recast` (`ElytraRecast`), `rocket-boost` (`RocketBoost`) and their `recast` / `elytraboost` helpers | [Likon69/HunterBuddy](https://github.com/Likon69/HunterBuddy) | Likon69 | `4cae4b1` | GPL-3.0 |
| `AutoDyeShulkers` | [FaxHack/BepHaxAddon](https://github.com/FaxHack/BepHaxAddon) | FaxHack, module by Tas `[0xTas]` | `d8a8c5f` | GPL-3.0 |
| `chest-tracker` (`ChestTracker`), its command and its `chesttracker` support package | [dekrom/BepHaxAddon](https://github.com/dekrom/BepHaxAddon) | dekrom | `5040d95` | GPL-3.0 |
| `stash-brander` (`StashBrander`) | [FaxHack/BepHaxAddon](https://github.com/FaxHack/BepHaxAddon) | FaxHack | `d8a8c5f` | GPL-3.0 |
| `better-stash-finder` (`BetterStashFinder`) | [FaxHack/BepHaxAddon](https://github.com/FaxHack/BepHaxAddon) | FaxHack | `d8a8c5f` | GPL-3.0 |
| `search-area` (`SearchArea`) and its `searcharea` modes | [FaxHack/BepHaxAddon](https://github.com/FaxHack/BepHaxAddon) | FaxHack | `d8a8c5f` | GPL-3.0 |

`base-finder` is a new implementation of the detection approach used by
[etianl/Trouser-Streak](https://github.com/etianl/Trouser-Streak)'s `BaseFinder` (1.21.11): it scans chunk packets,
counts seven configurable block lists, and runs block and entity detectors. No upstream source file is copied; the
module is written for this addon's settings and has no XaeroPlus dependency. Trouser Streak's repository does not
include a license file.

`control-fly` is based on Hyperglide's module; the `porpoise` swing, the `alignment` control and the `chest-swap`
delegation to `elytra-tweaks`' spoof mode are additions made here.

`elytra-mace` follows the behaviour documented for BepHax's module of the same name: it predicts the target, dives,
puts the chestplate on for the mace hit, restores the elytra and climbs out on rockets, with the `trigger`,
`min-drop`, `min-fall`, `ignore-friends`, `enemies-only` with its `enemies` name list, `repeat`, `steer`, `escape`,
`recover-pitch`, `recover-height` and `rocket-boost` controls. BepHax's current `ElytraMace` is not in the public GPL release, so this is an independent
reimplementation of the documented behaviour; no upstream source file is copied.

`flow-esp` follows the behaviour documented for BepHax's module of the same name: it looks for lava above the Nether
lava ocean and water below the Overworld sea level, keeps moving flows, draws colour-coded columns, and caches the
finds on disk. BepHax's current `FlowESP` is not in the public GPL release, so this is an independent reimplementation
of the documented behaviour; no upstream source file is copied.

The rest of the addon (the Stash Organizer module and its planner, sorting, labelling, report and chest-placement
code) is original work.

## Changes made to the imported code

- Java packages renamed to `com.pro0101.stashorganizer.modules.<origin>…`, all references updated.
- The addon categories of the three projects were merged into this addon's single `Stash` category.
- HunterBuddy's `SorterSafety` no longer pauses on `SpawnerDetector` and `AutoFlyingRegear`: those two modules are not
  part of this addon, so the hook now only looks for visible players.
- Hyperglide's cross-version `API` is imported in full, together with `EntityAccessor`, `InputAccessor` and
  `ClientAccessor`; `InteractionAccessor` and `SignEditScreenAccessor` complete the mixin set registered in
  `stash-organizer.mixins.json`.
- `AutoPilot` looks up `MiningTweaks`, `ElytraTweaks`, `BounceFly`, `AirPlace` and `ControlFly` directly, so those
  modules travel with their support code (`hyperglide.utilities`, `hyperglide.navigation`).
- BepHax's Stardust messaging was replaced by Meteor's `ChatUtils`; the unused `StardustUtil` dependency was dropped.
- `ChestTracker` was ported to this addon's category and package. Its screen no longer uses BepHax's `ItemSearchBar`,
  and its `UseBlockCallback` hook became a `PacketEvent.Send` listener on `PlayerInteractBlockC2SPacket` so it does not
  need the Fabric API callback. Its command is registered as `chesttracker` (aliases `ct` and `track`).
- `AutoDyeShulkers` was ported from Minecraft 1.21.4 to 1.21.11.
- `ChunkRadar` used `WaypointFollower.resolveOverworldWaypointWorld()`; that one lookup now lives in
  `util/WaypointWorlds`, so the radar does not drag the follower, its waypoint package and the area loader.
- `TrailFollower`'s `Pitch40` flight mode drives the imported `Pitch40` module, which in turn uses `ElytraRecast` and
  `RocketBoost`; all three are registered, so the mode works instead of finding no module to toggle. `HuntFeed` lost its
  companion-pet bridge, which would have pulled the whole pet package in.
- `BounceFly` keeps its Hyperglide origin. Its glide-clear recovery, `stop-near-entities`,
  the loaded-chunk guard on the Baritone goal, and the opt-in `hold-glide-clear` / `fake-lag` network emulation are
  ported from HunterBuddy's `ElytraBounce` and `BounceHazards` (`4cae4b1`, GPL-3.0). The clear hold itself is this
  addon's `GlideClearHold`, shared with `Pitch40`. Pitch solving from that module is not imported; its chestplate
  fakefly is, as the off-by-default `chestplate-fakefly` option.
- `HotbarRefill` and `InventoryCleaner` are ported from HunterBuddy with this addon's category, formatting and javadocs;
  their settings and behaviour are unchanged, except that `HotbarRefill` matches a firework by its whole component set
  instead of a plain-rocket helper that is not imported here.
- Xaero's Minimap, World Map and XaeroPlus are `modCompileOnly` dependencies (`25.3.10`, `1.40.11`, `2.30.8`): the
  two Xaero modules need them at runtime and none of their code is bundled in this jar. The addon checks for those
  classes and only then registers `trail-follower` and `chunk-radar` by name, so a missing Xaero disables two modules
  instead of breaking the jar.
