# Hamlet

A villager overhaul for Fabric 1.21.1. Villagers do real work, keep what they make, and sell it to you.
Everything a villager sells, it made: there are no services, no levels, no trade rerolling and no cure
discounts.

Install it on both the client and the server (it has its own trading screen). Requires
[Fabric API](https://modrinth.com/mod/fabric-api).

## How villagers work

- **Bed and workstation.** A villager only works once it has claimed both. Trading has no requirements:
  it always sells what's already in its stock. When it isn't working, its trading screen title says why
  (e.g. "Not working: no bed").
- **Jobs.** The workstation gives a villager its job, and it keeps the job until the workstation is
  destroyed, whether or not you've traded with it. Moving a workstation (with a piston or Carry On)
  counts as destroying it: the villager loses the job, and any jobless villager nearby, often the same
  one, can claim it at its new spot. Stock and storage stay either way. There are no levels: every
  villager can do everything its job offers from the start.
- **Stock** (27 slots) holds everything a villager makes. You can't reach it directly; it's only sold,
  through the trading screen. A full stock stops the villager working.
  - **Cap:** each kind of item stops at 3 stacks (192 wheat or blocks, 48 eggs, 3 milk buckets or
    potions). Work that can tell what it would get skips capped output instead: a farmer with 192 wheat
    leaves ripe wheat in the field but keeps harvesting other crops, a shepherd skips animals whose
    product is capped, and full hives wait. Only random output, like a fisherman's catches, is thrown
    away over the cap. Selling some lets the villager carry on. Exceptions: the cartographer keeps one of
    each map, and the librarian one stack of each book.
  - **Discards:** items in `#hamlet:villager_discards` are thrown away instead of stocked, for every job.
    By default that's wheat seeds, beetroot seeds and poisonous potatoes.
- **Storage** (9 slots) holds anything you put in, and you can take it back freely. It's never sold and
  doesn't affect work, except for the containers and feed a job uses (below). Villagers pick up off the
  ground only what they'd use, into storage while it has room: bread and baked potatoes, plus buckets,
  bowls and breeding feed for shepherds and glass bottles for farmers. Babies pick up nothing. Storage
  drops when the villager dies (including turning into a zombie); stock doesn't.
- **Trading screen.** Laid out like vanilla's. On the left, a scrolling list with one row for each kind
  of item in stock. Selecting a row moves its cost into the two payment slots, and taking the result buys
  it (shift-click repeats while you can pay). On the right are the villager's storage, the payment and
  result slots, and your inventory. Every adult villager opens it, including jobless villagers and
  nitwits.
- **Prices** are one global list, the same for every villager whatever its job, so changing or losing a
  job doesn't make its stock free. Anything without a price, such as modded fishing loot, sells for 1
  emerald. Curing a zombie villager changes no prices.
- **Healing:** villagers regenerate half a heart every 5 seconds, so occasional bumps from bad
  pathfinding don't add up.
- **Zombies:** a villager killed by a zombie always becomes a zombie villager, on every difficulty.

## Professions

### Farmer (composter)

Patrols within 9 blocks of its composter during its work period. When it spots a fully grown crop, it
walks over and harvests just that one, resetting it to its first stage, so your layout stays exactly as
you planted it. No seeds are used up.

- Harvests crops, sweet berries and cocoa; sugar cane (everything above the bottom block); and pumpkins
  and melons (only with a stem attached). Leaves cactus, bamboo and nether wart alone.
- **Beehives:** it also does the old beekeeper's job. A hive or bee nest within 12 blocks of its
  composter (and up to 4 blocks up or down) that's full of honey and smoked by a lit campfire below it
  gets harvested as a player would: with a glass bottle from its storage when it has one and isn't capped
  on honey (one honey bottle, using up the bottle), otherwise with shears (3 honeycomb). Unsmoked hives
  are left alone, so the bees never get angry.
- It no longer makes bread or shares food.

### Shepherd (loom)

Wanders within 12 blocks of its loom during its work period. When an animal is ready, it walks up holding
the right tool and stocks what it gives:

- wool from sheep, whenever they have wool;
- every 5 minutes: milk from cows and goats (needs a bucket from storage), mushroom stew from mooshrooms
  (needs a bowl), a scute from armadillos (not while rolled up), and an egg from chickens.

Containers are used up as a player's would be. Without them it skips cows, goats and mooshrooms and
carries on with the rest.

**Breeding:** with nothing to tend, it breeds animals using vanilla breeding items from its storage:
wheat for sheep, cows, mooshrooms and goats; seeds for chickens; spider eyes for armadillos; carrots,
potatoes or beetroot for pigs; carrots or dandelions for rabbits. It feeds one animal at a time, only
when another of its kind is ready to pair with it, and only while that species has fewer than 8 nearby
(counting babies and pairs already in love).

### Fisherman (barrel)

Goes to the shore of water within 9 blocks of its barrel, casts 2 to 5 blocks out, waits for a bite
(vanilla's 5 to 30 seconds) and stocks a catch from vanilla's fishing loot table, so mods and datapacks
that change fishing loot apply. Treasure needs open water around the bobber, as for players.

### Librarian (lectern)

Makes a book at its lectern about every 5 minutes of work: a copy of the book on the lectern, or a plain
book if there's nothing to copy or that copy is capped. Nothing is used up.

- It copies enchanted books, which can now be placed on lecterns (right-clicking the lectern hands one
  straight back), and written books, one generation further as crafting does, so a copy of a copy can't
  be copied.
- It keeps one stack of each book (64 plain books, 16 written copies) but 3 of an enchanted book.

### Cartographer (cartography table)

Draws an explorer map about every 5 minutes of work, of a kind it doesn't have, keeping one of each:
ancient city, woodland mansion, desert pyramid, ocean monument, trial chambers, jungle temple, swamp hut,
mineshaft and warm ocean ruins. Each new map leads to the nearest structure of its kind that no map
points to yet, so a replacement leads somewhere new. A kind with none in range is retried daily and
doesn't hold up the others.

Each kind has its own item texture and map icon. The buried treasure map, and empty and ordinary filled
maps, get matching textures too. These textures and icons are backported from a newer Minecraft version.

### Cleric (brewing stand)

Harvests fully grown nether wart within 9 blocks of its stand, replanting it as the farmer does. Brews a
random potion about every 5 minutes of work: fire resistance, night vision, water breathing, swiftness,
leaping or slow falling. Keeps up to 3 of each.

### Mason (stonecutter)

Cuts a batch of 8 blocks about every minute of work: terracotta, sandstone, red sandstone, bricks, tuff
or calcite, picked at random. Keeps up to 192 of each, and sells them 8 for an emerald. Nothing is used
up.

### Wandering trader

Sells saplings, coral blocks, seeds and tall flowers.

### Retired jobs

Armorer, butcher, fletcher, leatherworker, toolsmith and weaponsmith are gone. Their workstations no
longer give a job, villagers that had one become jobless, and their houses no longer generate in new
villages (existing villages keep theirs). Hamlet's own beekeeper was folded into the farmer, and the
archaeologist is retired too, which brings back vanilla's smithing template duplication recipes.

## Breeding

Villagers breed on **bread** and **baked potatoes** from their storage. Each parent eats 3 when a baby is
born, so a pair needs 6 between them; raw potatoes, carrots and beetroot no longer count. Farmers no
longer supply food, so it's up to you: put it in through the trading screen, or throw it to them.

Villagers share: when two meet, one with more than 3 throws a neighbour with fewer what it's missing,
keeping 3 for itself, so stocking one villager feeds the village.

## Emeralds

Nobody buys from you, so emeralds come from loot, mining and raids. Emerald ore is rebalanced to match:
as common as diamonds at diamond depth, and mountains also get diamond-sized veins up in their peaks, at
vanilla emerald's height.

## Compatibility

- **[Pasture](https://github.com/maihatsuu/pasture)** (optional): shepherds follow its rules through its
  API, so they can't get around them.
  - Cows and goats are milked with glass bottles from storage into Pasture's milk bottles (no buckets),
    and only when they have milk: grazing refills them, not a timer. Mooshroom stew still uses bowls, and
    uses up the same milk.
  - Chickens don't lay, so there are no eggs on a timer. Breeding chickens drops eggs, which the shepherd
    picks up into its stock.
  - Armadillos are brushed the vanilla way, so Pasture's 2–6 scutes and one-day wait apply, shared with
    players. The shepherd picks up the scutes.
  - Shepherds pick up glass bottles instead of buckets.
- **[Archive](https://github.com/maihatsuu/archive)** (optional): librarians copy a tome on their lectern
  like an enchanted book, keeping its enchant, 16 of each, sold for 16 emeralds. The tome still opens
  Archive's page on the lectern.

## Datapacks

Files of each kind are merged across datapacks and mods, so you can add to Hamlet's.

- **Prices:** `data/<namespace>/hamlet/prices/*.json`. A price is emeralds per item, or a batch of N items
  for M emeralds, sold together as one trade:
  ```json
  { "prices": { "minecraft:wheat": 1, "minecraft:bricks": { "emeralds": 1, "count": 8 } } }
  ```
- **Wandering trader:** `data/<namespace>/hamlet/wandering_trader.json`,
  `{"catalogue": {"<item>": <emeralds>}}`.
- **Cleric potions:** `data/<namespace>/hamlet/cleric_potions.json`, `{"potions": [...]}`.
- **Mason blocks:** `data/<namespace>/hamlet/mason_blocks.json`, `{"blocks": [...]}`.
- **Discards:** add items to `#hamlet:villager_discards` to stop villagers stocking them, such as fishing
  junk.
- **Map structures:** `#hamlet:on_ancient_city_maps`, `#hamlet:on_desert_pyramid_maps`,
  `#hamlet:on_mineshaft_maps`, `#hamlet:on_warm_ocean_ruins_maps`, `#hamlet:on_jungle_temple_maps` and
  `#hamlet:on_swamp_hut_maps` choose what those maps lead to.
- **Villages:** Hamlet replaces vanilla's `village/<biome>/houses` and `village/<biome>/zombie/houses`
  template pools.

## Development

Run from `hamlet-1.21.1/`:

```sh
./gradlew build       # jar in build/libs/
./gradlew runClient   # dev client
```

Dev runs also load Pasture and Archive when their builds sit next to this repo.
