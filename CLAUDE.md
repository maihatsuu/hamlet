# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Hamlet is a Fabric mod for Minecraft 1.21.1 that overhauls villagers. The mod is in `hamlet-1.21.1/`
(mod id `hamlet`, package `maihatsuu.hamlet`). The git repo is this directory (remote
`git@github.com:maihatsuu/hamlet.git`, branch `main`; commit directly on main, and only when the user
says so).

`README.md` is the source of truth for what the mod does: behaviour, exact numbers and data formats. Keep
it updated with every change, along with the testing checklist below. The original design spec
(`Hamlet.md`) has been retired into those two places.

Archive, Pasture, Rebalanced Core, Carry On and "NOTES" are other mods or documents from the wider
modpack. Archive and Pasture are the user's own mods, in sibling directories (`../Archive`,
`../Pasture`).

## Design decisions

Settled with the user; ask before changing them or adding features.

- Villagers only sell what they made: no services, levels, rerolling or cure discounts. Nobody buys
  from the player.
- One global price list for every villager. Batch prices exist (`{"emeralds", "count"}`).
- Stock is capped at 3 stacks per item; predictable work skips capped output rather than wasting it.
  Exceptions: the cartographer keeps 1 of each map, and the librarian one stack of each book but 3 of an
  enchanted book.
- Jobs come from the workstation alone and are lost only when it's destroyed; moving it counts.
- Retired jobs (armorer, butcher, fletcher, leatherworker, toolsmith, weaponsmith, plus Hamlet's
  beekeeper and archaeologist) stay registered but jobless, and their village houses don't generate.
- Containers are the only work inputs: shepherds use buckets (or Pasture's glass bottles) and bowls,
  farmers glass bottles for honey (preferred over shears). The cleric supplies its own bottles.
- Villagers pick up only what they'd use: breeding food, and their job's containers and feed.
- Compatibility with other mods is optional and never a build dependency.

## Open questions

Unmade decisions; raise them with the user rather than assuming.

- **Raid farms:** raids are now a main emerald source, so a raid farm nerf matters more (see NOTES).
- **Trade screen:** how to display a large inventory, and how Jade or other mods show villagers.
- **Mod compatibility** beyond Pasture and Archive comes later.
- **Village houses:** hand-made replacements for the removed job houses, including campfire-smoked
  beehives near farms, are planned.

## Commands

Run from `hamlet-1.21.1/`:

```sh
./gradlew build       # compile and produce jars in build/libs/
./gradlew runClient   # launch a dev client
./gradlew runServer   # launch a dev dedicated server
```

There is no automated test suite. Verification is manual and in game, using the checklist below. On
world load the log prints "Loaded N villager prices, …"; data files that fail to parse log "Skipping …".
`/hamlet stock <villager> <item> <count>` seeds a villager's stock for testing, and `/hamlet count_ores`
counts ores around you.

CI (`.github/workflows/build.yml`) runs `./gradlew build` on JDK 25. The mod compiles to Java 21.
Minecraft, Fabric Loader, Loom and Fabric API versions are set in `gradle.properties`.

## Architecture

- **Mojang mappings** (`loom.officialMojangMappings()`): use Mojang names (`ResourceLocation`,
  `Villager`), not Yarn names, and take care with Yarn-based examples.
- **Split source sets:** `src/main` (common and server) can't reference `src/client`. The trading screen
  is in `client`; villager logic, inventories and networking registration are in `main`. The mod runs on
  both sides (`"environment": "*"`).
- **Entrypoints:** `maihatsuu.hamlet.Hamlet` and `maihatsuu.hamlet.client.HamletClient`. Use
  `Hamlet.id(path)` for namespaced ids.
- **Mixins:** list each in `hamlet.mixins.json` or `hamlet.client.mixins.json`. Both set
  `defaultRequire: 1` (an injector that matches nothing crashes at startup) and `requireAnnotations`.
  MixinExtras is available.
- **Villager state:** `VillagerMixin` adds the stock and storage inventories (`villager/HamletVillager`),
  cancels vanilla trades and restocking, and controls item pickup. Retired professions are registered
  with predicates that match nothing (`HamletProfessions.registerRetired`), and `VillagerRules.RETIRED`
  makes villagers with them jobless.
- **Work:** `VillagerGoalPackagesMixin` swaps vanilla's work behaviours for Hamlet's: `FarmCrops`
  (farmer and cleric, with `Crops` and `Hives`), `TendAnimals` (shepherd), `GoFishing` (fisherman), and
  `ProfessionWork` (librarian, cartographer, cleric brewing, mason), which produces on a timer of work
  time at the workstation. All output goes through `ProfessionWork.store`, which applies discards and the
  cap; predictable work checks `ProfessionWork.isCapped` first.
- **Data:** `data/PriceLoader` is one reload listener for `hamlet/prices/*.json`,
  `wandering_trader.json`, `cleric_potions.json` and `mason_blocks.json`. A prices file with an unknown
  item fails as a whole, so prices for optional mods go in their own file behind a Fabric
  `fabric:load_conditions` (`prices/archive.json`).
- **Trading:** `trade/VillagerTradeMenu` builds rows from the stock and the prices synced in
  `TradeOpeningData`; the screen is `client`.
- **Compatibility** (`compat/`): `PastureCompat` calls Pasture's API (`maihatsuu.pasture.Pasture`) by
  reflection; `ArchiveCompat` only looks up `archive:tome` by id. Neither is needed to build. Dev runs
  add their jars (and Cloth Config, which Archive needs) through guarded `modLocalRuntime` lines in
  `build.gradle` when `../Pasture/pasture-1.21.1/build/libs/` or `../Archive/archive-1.21.1/build/libs/`
  has a build; rebuild them there after changing those mods.
- **Village houses:** `data/minecraft/worldgen/template_pool/village/*/houses.json` (and `zombie/`) are
  full copies of vanilla's, minus the retired jobs' houses, written by `tools/strip_job_houses.py` from
  the Minecraft jar (`~/.gradle/caches/fabric-loom/1.21.1/minecraft-common.jar`). Rerun it rather than
  editing them by hand.
- **Emerald ore:** `worldgen/HamletWorldgen` and `data/hamlet/worldgen` replace vanilla's emerald ore.

## Testing checklist

Update this with every change.

**General**
- Kill a villager with items in storage: they drop; its stock doesn't.
- Add an item to `#hamlet:villager_discards` with a datapack: villagers that produce it stop storing it.
- Hurt a villager: it heals half a heart every 5 seconds until full.
- Right-click any adult villager, including jobless villagers and nitwits: the trading screen opens. Babies refuse.
- Throw bread near a villager: it picks it up into storage, not stock, and stops once storage is full. Babies pick up nothing. Dirt stays on the ground; a shepherd also picks up wheat, seeds, carrots and other feed.
- Place and break a workstation: the job is gained and lost, with no trading involved.
- Remove a villager's bed: it stops working, its screen says "no bed", it still sells its stock, and it resumes once it claims a new bed.
- No villager's offer list has service rows.
- Stock cap: a farmer with 192 wheat leaves ripe wheat but still harvests other crops; a shepherd with 3 milk buckets stops milking but still shears; a fisherman's catches over the cap are thrown away. Selling some lets it resume.

**Trading**
- The offer list matches what the villager made. Selecting a row moves its emeralds into the payment slots; taking the result buys one item (or one batch, such as 8 mason blocks) and removes it from stock; shift-click buys until emeralds or stock run out.
- Closing the screen returns anything left in the payment slots.
- Any item can be put into and taken from storage, including by shift-click.
- An old save's unpriced stock items move into storage.

**Professions**
- Farmer patrols its field, harvests mature crops one at a time, and leaves a mixed layout unchanged. Crops more than 9 blocks from its composter, cactus, bamboo and nether wart are left alone.
- Farmer harvests full hives and bee nests with a lit campfire under them within 12 blocks of its composter without angering the bees: with a glass bottle from storage when it has one, else shears. Unsmoked hives are left alone.
- Shepherd shears woolly sheep into its stock, milks cows and goats, gets stew from mooshrooms, brushes armadillos and gathers eggs.
- Shepherd with no buckets or bowls leaves cows, goats and mooshrooms alone but still shears and gathers eggs; give it buckets and it milks, using one per milk bucket. Thrown buckets, bowls and feed are picked up by shepherds; glass bottles by farmers.
- Shepherd with wheat and seeds in storage and nothing to tend feeds pairs of sheep, cows and chickens one at a time, one item each; they breed. It stops at 8 of a species and never feeds a lone animal.
- Fisherman walks to the shore, holds a rod, casts a bobber, and each bite adds a catch. Treasure only near open water.
- An enchanted book can be placed on a lectern; right-clicking the lectern gives it straight back, with no book screen.
- Librarian with an empty lectern makes plain books, up to 64. With an enchanted book, copies up to 3, then plain books; with a written original, "Copy of original" copies up to 16; with a copy of a copy, plain books.
- Cartographer with a bed and table fills its stock with up to one of each of the nine explorer maps, each a separate row at 8 emeralds with its own texture. Buying one makes it draw a replacement leading somewhere different. With no desert nearby, the other kinds still appear.
- A buried treasure map from a shipwreck shows Hamlet's item texture and red X.
- Cleric harvests ripe nether wart within 9 blocks of its stand, and brews potions from the list (never healing or regeneration), at most 3 of each, all at one price.
- A jobless villager claims a stonecutter and becomes a mason. Its stock fills with 8-block batches, about one a minute, up to 192 of each. Rows read 1 emerald → 8 blocks.
- Old beekeepers, butchers and archaeologists become jobless; smokers and decorated pots give no job. A template, 7 diamonds and its base material craft 2 templates.

**Breeding, zombies, world**
- Two villagers with 3 bread or baked potatoes each in storage, and a free bed, breed and eat them. Raw potatoes, carrots or no food: no breeding.
- Give one villager 12 bread and leave the others empty: it throws bread to villagers it meets until each has 3, keeping 3.
- Villagers killed by zombies always zombify, on Easy, Normal and Hard. Curing changes no prices.
- New villages (each biome, and zombie villages) have no armorer, butcher, fletcher, tannery, toolsmith or weaponsmith houses.
- Emerald ore rates match diamonds at depth everywhere, and mountains also have veins in their peaks.

**Compatibility**
- With Pasture: a shepherd with glass bottles milks a cow into a milk bottle, then leaves it until it grazes; makes stew from a mooshroom with a bowl, drying it; breeds chickens with seeds and picks up the eggs; brushes an armadillo once for 2–6 scutes, picks them up, then waits a day. Without Pasture, the vanilla rules apply.
- With Archive: a tome on a lectern opens Archive's tome page; the librarian copies it with the same enchant, up to 16, then plain books, sold for 16 emeralds each. The log counts one more price than without Archive.
