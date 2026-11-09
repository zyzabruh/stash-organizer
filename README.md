# Stash Organizer

A Meteor Client addon that opens every nearby storage block and reorganises the stash in place: items are grouped,
chests are labelled with item frames, and shulker boxes are unpacked, packed or kept shut.

Author: **pro0101**

## Features

- Scans chests, barrels and shulker boxes in a configurable radius and reads their contents.
- Groups items by exact item, vanilla family, broad category or metal/gem material, and creates equipment kits.
- Reuses the existing containers as the chests of those groups, and places extra chests (or overflow shulkers) when a
  group has no room. When the stash runs out of chests, the groups that have to share one are packed by kind: redstone
  with redstone, blocks with blocks, ores with ores, instead of a bit of everything in every shared chest.
- Reads the item frame already standing on a chest and gives that group to the chest first, so a stash labelled by hand
  keeps its layout.
- Unpacks shulker boxes found in the stash, packs overflow boxes with a pickaxe, and collects boxes left on the floor.
- Never puts a shulker box inside another one.
- Keeps renamed boxes, bulk boxes, kits and boxes holding a protected item shut; the end-of-run report lists them with the
  rule that protected them.
- Labels every sorted chest with an item frame holding a representative item.
- Tidies the slots of every chest before labelling: the stacks of one item sit together, the groups keep the order of the
  plan and the empty slots end at the bottom of the chest (setting `arrange-chests`).
- Empties a chest that holds items of other groups when the buffer has nowhere to go, so a chest is not left half full
  while its own group waits elsewhere.
- Writes a plan that survives a disconnection, a report of the run and the group-to-chest layout of the previous runs.
  The report has a row per chest: stacks received, stacks given away, stacks taken out to make room, stacks still out of
  place and the reason they stayed.
- Walks with Baritone when it is installed, and falls back to Meteor's path manager otherwise.

## Install

1. Install [Meteor Client](https://meteorclient.com) for the matching Minecraft version.
2. Drop the built jar in the `mods` folder next to Meteor.
3. Join a world, open the Meteor GUI, go to the `Stash` category and enable `Stash Organizer`.

## Usage

1. Stand in the storage room, near the chests to organise, with at least 8 free inventory slots (or enable the inventory
   dump).
2. Enable the module. It scans, plans, places the chests it needs, sorts, arranges, labels and writes its report.
3. The run stops on its own. The plan, the report and the remembered layout of the previous runs are written in the
   Meteor folder, under `stash-organizer`.
4. If the connection drops mid-run, joining the same world re-enables the module and resumes the saved plan.

## Build

```bash
./gradlew build
```

The jar lands in `build/libs`.

## Imported modules

A dozen modules come from other Meteor addons and are distributed here under the GPL-3.0, with their authors credited:
the Hyperglide travel suite (`fast-frame`, `overview`, `navigation`, `auto-pilot`, `elytra-tweaks`, `bounce-fly`,
`control-fly`, `air-place`, `mining-tweaks`), `shulker-sorter`, `sign-history`, `trail-follower` and `chunk-radar`
from HunterBuddy, and `AutoDyeShulkers` from BepHax. See `CREDITS.md` for the repositories, commits and the changes made.

`ChunkRadar` and `TrailFollower` need [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap),
[Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) and
[XaeroPlus](https://modrinth.com/mod/xaeroplus) installed. Without them the addon still loads: those two modules are
simply not registered and the rest of the addon works normally.

Two HUD elements live in the `Stash` group: `stash-organizer`, and `flight-status`, which lists the running flight
modules with the `pitch-40` bounds and the `trail-follower` mode so that chain is not invisible.

## License

GNU General Public License v3.0 or later, see `LICENSE`. Modules imported from other addons keep their original
authorship; `CREDITS.md` lists them.
