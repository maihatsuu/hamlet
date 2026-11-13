# Hamlet

A Fabric mod for Minecraft 1.21.1 that overhauls villagers. Villagers do real work, keep what they
produce in an inventory you can look through, and sell it to you. There are no services: everything a
villager sells, it made. Levels, trade rerolling and cure discounts are gone.

Needs to be installed on both server and client (custom trading screen).

## Core rules

- **Inventory:** villagers have two inventories.
  - **Stock** (27 slots): all work output. Players never reach it directly; it's only sold, through
    the trading screen. A full stock stops the villager working.
  - **Stock cap:** each kind of item stops at 3 stacks: 192 wheat or blocks, 48 eggs, 3 milk buckets
    or potions. Work that can tell what it would get skips capped output instead: the farmer leaves
    capped crops ripe in the field, the shepherd skips animals whose product is capped, and hives wait.
    Only random output, like fishing catches, is thrown away over the cap. The cartographer keeps one of
    each map regardless.
  - **Storage** (9 slots): anything players put in, and what the villager picks up off the ground: breeding food, plus their work containers and feed: buckets, bowls and breeding items for shepherds, glass bottles for farmers. Other items have to go in through the trading screen.
    Players can take it back freely. It's never for sale and doesn't affect work. It drops when the
    villager dies (including turning into a zombie); stock doesn't.
- **Trading screen:** laid out like vanilla's. On the left, a scrolling list of offers: one row for
  each kind of item in stock. Clicking a row selects it and moves its cost into the two payment slots;
  the result slot shows what you'll get, and taking it makes the trade (shift-click repeats it while
  you can pay). On the right: the villager's storage, the payment and result slots, and your
  inventory.
- **Prices:** one global list, the same for every villager whatever its profession, so changing or
  losing a job doesn't make its stock free. Data-driven: `data/<namespace>/hamlet/prices/*.json`
  files are merged, so mods and datapacks can add their own. The wandering trader's catalogue is
  separate (`hamlet/wandering_trader.json`). Anything without a price, such as modded fishing loot,
  sells for 1 emerald. A price is either emeralds per item, or a batch, `{"emeralds": M, "count": N}`:
  N items for M emeralds, sold together as one trade (the mason's blocks are 8 for 1 emerald).
- **Jobs:** the workstation gives a villager its job, and it keeps that job until the workstation is
  destroyed, whether or not it has been traded with. Moving a workstation (with a piston or Carry On)
  counts as destroying it: the villager loses the job, and any jobless villager nearby, often the same
  one, can claim it at its new spot. Its stock and storage stay either way.
- **No level progression.** Every villager can do everything its profession offers from the start.
- **Zombie curing doesn't affect prices.**
- **Discards:** work output in the item tag `#hamlet:villager_discards` is thrown away instead of going
  into stock, for every profession. It ships with wheat seeds, beetroot seeds and poisonous potatoes;
  datapacks can add to it to stop villagers gathering anything else, such as fishing junk.
- **Healing:** villagers slowly regenerate, half a heart every 5 seconds, so occasional damage from
  bad pathfinding doesn't add up.
- **Zombification:** a villager killed by a zombie always turns into a zombie villager, on every
  difficulty.

## Work requirements

A villager only works if it has claimed both a **bed** and a **workstation**. Trading has no
requirements: a villager will always sell what's already in its stock.

When a villager isn't working, its trade screen title says why (e.g. "Not working: no bed").

## Professions

Only these professions exist; all others are deprecated.

| Profession | Workstation | Work | Sells |
|---|---|---|---|
| Farmer | Composter | Spends its work period patrolling within 9 blocks of its composter. Whenever it spots a fully grown crop, it walks over and harvests just that crop, leaving it to regrow in place, so layouts stay exactly as the player planted them. No seed cost. Harvests crops, sweet berries and cocoa (reset to their first stage), sugar cane (everything above the bottom block), and pumpkins and melons (only with a stem attached); cactus, bamboo and nether wart are left alone. Seeds and poisonous potatoes are thrown away (see Discards). It also does the retired beekeeper's work: when a hive or bee nest within 12 blocks of its composter (and up to 4 blocks up or down) is full of honey and smoked by a lit campfire below it (vanilla's check for calm bees), it walks over and harvests it as a player would: with a glass bottle from its storage (1 honey bottle, using up the bottle) whenever it has one and isn't capped on honey, otherwise with shears (3 honeycomb). Unsmoked hives are left alone, so the bees are never angered. | Stock: harvest output, honey bottles, honeycomb. No longer makes bread or shares food. |
| Shepherd | Loom | Spends its work period wandering within 12 blocks of its loom. When an animal is ready, it walks up to it holding the right tool and stores what it gives: wool from sheep (when they have wool), and every 5 minutes milk from cows and goats, mushroom stew from mooshrooms, a scute from armadillos (not while rolled up) and an egg from chickens. Milk and stew need a bucket or bowl from its storage, used up as a player's would be; without one it skips cows, goats and mooshrooms and carries on with the rest. With nothing to tend, it breeds animals using feed from its storage (vanilla's breeding items: wheat for sheep, cows, mooshrooms and goats, seeds for chickens, spider eyes for armadillos, carrots, potatoes or beetroot for pigs, carrots or dandelions for rabbits). It feeds one animal at a time, as a player would, and only when another of its kind is ready to pair with it and the species has fewer than 8 in the pasture, counting babies and pairs already in love. | Stock: wool, milk, mushroom stew, armadillo scutes, eggs. |
| Fisherman | Barrel | Spends its work period at the shore of water within 9 blocks of its barrel. Casts a bobber 2 to 5 blocks out, waits for a bite (vanilla's 5 to 30 seconds), and stores a catch rolled from vanilla's `gameplay/fishing` loot table, the one players use, so mod and datapack changes to fishing loot apply. Treasure catches need open water around the bobber (vanilla's open-water check). | Stock: everything it catches, including treasure. |
| Librarian | Lectern | Stays near its lectern during its work period, and makes a book there about every 5 minutes of work: a copy of the book on its lectern, or a plain book if there's nothing it can copy, or its stock of that copy is capped. It copies enchanted books (which can now be placed on lecterns) and written books, one generation further as vanilla's copying does, so copies of copies can't be copied. Nothing is used up. Keeps up to 3 stacks of each book (3 enchanted books, 48 written copies, 192 plain books). | Stock: book copies, plain books. |
| Cartographer | Cartography table | Stays near its cartography table during its work period, and draws an explorer map there about every 5 minutes of work, of a random kind it doesn't have, keeping one of each kind in stock: ancient city, woodland mansion, desert pyramid, ocean monument, trial chambers, jungle temple, swamp hut, mineshaft and warm ocean ruins. Each new map points at the nearest structure of its kind that no map points to yet (vanilla's explorer map rule), so a replacement leads somewhere new. A kind with none in range is retried daily and doesn't hold up the others. Each kind has its own item texture. Ancient cities, desert pyramids, mineshafts and warm ocean ruins have no vanilla explorer map, so Hamlet adds them, with their own map icons. Those four, plus jungle temples and swamp huts (whose vanilla tags are only filled by an experimental datapack), use Hamlet structure tags: `#hamlet:on_ancient_city_maps`, `#hamlet:on_desert_pyramid_maps`, `#hamlet:on_mineshaft_maps`, `#hamlet:on_warm_ocean_ruins_maps`, `#hamlet:on_jungle_temple_maps`, `#hamlet:on_swamp_hut_maps`. The mansion map also gets Hamlet's own icon. Vanilla's buried treasure map (from shipwreck and underwater ruin chests) gets a matching item texture and X icon. Empty and ordinary filled maps get matching textures too. All of these map textures and icons are backported from a newer Minecraft version, not original art. | Stock: explorer maps. |
| Cleric | Brewing stand | Stays near its brewing stand during its work period. Whenever fully grown nether wart within 9 blocks of the stand is in sight, it walks over and harvests it, resetting it to its first stage as the farmer does with crops (farmers no longer touch nether wart). It also brews a potion at the stand about every 5 minutes of work, picked at random from `hamlet/cleric_potions.json` (fire resistance, night vision, water breathing, swiftness, leaping and slow falling; datapacks can change it). Keeps up to 3 of each potion in stock. All potions share one price. | Stock: potions, nether wart. |
| Mason | Stonecutter | Stays near its stonecutter during its work period, and cuts a batch of 8 blocks there about every minute of work, picked at random from `hamlet/mason_blocks.json` (terracotta, sandstone, red sandstone, bricks, tuff and calcite; datapacks can change it). Keeps up to 3 stacks (192) of each block in stock. Nothing is used up. | Stock: building blocks, 8 for 1 emerald. |
| Wandering Trader | None | | Sells saplings, coral blocks, seeds and tall flowers. |

Retired professions:

- **Archaeologist:** decorated pots are no longer a workstation, existing archaeologists become jobless, and
  vanilla's smithing template duplication recipes are back.
- **Beekeeper:** merged into the farmer. Existing beekeepers become jobless.
- **Butcher:** smokers are no longer a workstation, and existing butchers become jobless (they used to
  become beekeepers).

## Pasture compatibility

Pasture (an animal upkeep mod in the pack) is optional. When it's installed, the shepherd follows its
rules, through Pasture's API, so it can't get around them:

- **Milk:** cows and goats are milked with **glass bottles** from storage into Pasture's milk bottles
  (buckets aren't used), and only when they have milk: grazing refills them, not a timer. Mooshroom
  stew still uses bowls, and uses up the same milk.
- **Eggs:** chickens no longer lay, so the shepherd doesn't collect eggs on a timer. Breeding chickens
  with seeds drops eggs instead of chicks, and the shepherd picks them up into its stock.
- **Armadillos:** brushed the vanilla way, so Pasture's 2–6 scutes and one-day wait apply, shared
  with players. The scutes drop on the ground and the shepherd picks them up into its stock.
- Shepherds pick up glass bottles instead of buckets.

Hamlet calls Pasture by reflection, so it builds and runs without it. Dev runs include Pasture when a
build of it sits next to Hamlet (`../Pasture/pasture-1.21.1/build/libs/`).

## Breeding

Villagers breed when they have enough good food, provided by the player. Farmers no longer supply it.

Good food is **bread** and **baked potatoes**, read from the villager's storage. Players put it in
through the trading screen, or throw it and let the villager pick it up (adult villagers pick up bread and baked
potatoes into storage while it has room). Each parent eats 3 when a baby is born, so a pair needs 6 between
them; vanilla's other foods (raw potatoes, carrots, beetroot) no longer count.

Villagers share: when two meet, one with more than 3 throws a neighbour with fewer what it's missing,
keeping 3 for itself. So stocking one villager with plenty feeds the village.

## Emeralds

Nobody buys from the player, so emeralds come from loot, mining and raids. Emerald ore is rebalanced to
match:

- As common as diamonds, at diamond depth.
- Mountain biomes also get diamond-sized veins up in the peaks, at vanilla emerald's height range.

## Villages

Houses for retired professions are removed from village structures and replaced with new hand-made ones,
including campfire-smoked beehives near farms.

## Open questions

- **Work rates:** how often does each profession produce? (Keeps output from outpacing normal farms.)
  A fisherman on vanilla bite timing makes about 20 catches per working day.
- **Shepherd:** should follow the pack's milk and egg rules once those exist, so it can't bypass them.
- **Existing worlds:** what happens to villagers with retired professions, and already generated
  villages?
- **Raid farms:** raids are now a main emerald source, so a raid farm nerf matters more (see NOTES).
- **Emerald ore:** part of Hamlet, or Rebalanced Core worldgen?
- **Trade screen:** how to display a large inventory, and how Jade or other mods show villagers.

## Testing

- Kill a villager with items in storage: they drop on the ground; its stock doesn't.
- Add an item to `#hamlet:villager_discards` with a datapack: villagers that produce it stop storing it.
- Hurt a villager: it heals half a heart every 5 seconds until it's at full health.
- Right-click any adult villager, including jobless villagers and nitwits: the trading screen opens. Babies refuse.
- Throw bread near a villager: it picks it up into its storage, not its stock, and stops once storage is full. Babies pick up nothing. Other items, such as dirt, stay on the ground; a shepherd also picks up wheat, seeds, carrots and other breeding items.

- Place and break a workstation: job is gained and lost, with no trading involved.
- Remove a villager's bed: it stops working, its trade screen says "no bed", it still sells its
  stock, and it resumes once it claims a new bed.
- The offer list matches what the villager produced. Selecting a row moves its emeralds into the payment slots; taking the result buys one item (or one batch, such as 8 of a mason's blocks) and removes it from stock; shift-clicking the result buys repeatedly until the emeralds or stock run out.
- Closing the screen returns anything left in the payment slots.
- Any item can be put into and taken from the storage row, including by shift-click.
- An old save's unpriced stock items move into storage.
- Farmer patrols its field, harvests mature crops one at a time, and leaves a mixed crop layout
  unchanged. Crops more than 9 blocks from its composter are left alone.
- Decorated pots give no job, and an old archaeologist becomes jobless. A template, 7 diamonds and its base material craft 2 templates.
- Fisherman walks to the shore, holds a rod, casts a bobber, and each bite adds a catch to its stock.
- Fisherman only catches treasure near open water.
- Shepherd with wheat and seeds in storage, and nothing to tend, feeds pairs of sheep, cows and chickens one at a time, using one item each; they breed. It stops once a species has 8 in the pasture, and never feeds a lone animal with no partner.
- Shepherd with no buckets or bowls leaves cows, goats and mooshrooms alone but still shears and gathers eggs; give it buckets and it milks, using one up per milk bucket. Thrown buckets, bowls and feed are picked up by shepherds; thrown glass bottles by farmers.
- With Pasture: a shepherd with glass bottles milks a cow into a milk bottle, then leaves it until it grazes; makes stew from a mooshroom with a bowl, drying it; breeds chickens with seeds and picks up the eggs into stock; brushes an armadillo once for 2–6 scutes and picks them up, then waits a day. Without Pasture, the vanilla rules above apply.
- Shepherd walks up to woolly sheep with shears and shears them into its stock; also milks cows and goats, gets stew from mooshrooms, brushes armadillos and gathers eggs.
- Farmer walks to full hives with a lit campfire under them within 12 blocks of its composter and harvests them without angering the bees, with a glass bottle from its storage when it has one, else shears; full hives without a campfire are left alone. It leaves cactus and bamboo alone.
- Old beekeepers and butchers become jobless, and a jobless villager doesn't take a smoker as a job.
- An enchanted book can be placed on a lectern; right-clicking the lectern gives it straight back, with no book screen.
- Librarian with an empty lectern makes plain books, up to 192. With an enchanted book on it, it makes copies, up to 3, then plain books; with a written original, "Copy of original" copies; with a copy of a copy, plain books.
- Cleric makes potions from the list (never healing or regeneration), at most 3 of each, all at the potion price.
- Stock cap: a farmer with 192 wheat leaves ripe wheat in the field but still harvests other crops; a shepherd with 3 milk buckets stops milking but still shears; a fisherman's catches over the cap are thrown away. Selling some lets it resume.
- Cleric harvests ripe nether wart within 9 blocks of its brewing stand into its stock, and still brews; a farmer leaves nether wart in its field alone.
- No villager's offer list has service rows.
- A jobless villager claims a stonecutter and becomes a mason. Its stock fills with 8-block batches of the listed blocks, about one a minute, up to 192 of each. Its rows read 1 emerald → 8 blocks; buying takes 8 from stock, and shift-click buys batches until emeralds or stock run out.
- Cartographer with a bed and table fills its stock over its workday with up to one of each of the nine explorer maps, each a separate row at 8 emeralds with its own item texture. Buying one makes it draw a replacement, leading to a different structure. With no desert nearby, the other four kinds still appear.
- A buried treasure map from a shipwreck shows Hamlet's item texture and red X.
- Villagers killed by zombies always zombify, on Easy, Normal and Hard. Curing changes no prices.
- Two villagers with 3 bread or baked potatoes each in storage, and a free bed, breed and eat them. Raw potatoes, carrots or no food: no breeding.
- Give one villager 12 bread and leave the others empty: it throws bread to the villagers it meets until each has 3, keeping 3 itself.
- New villages contain the new houses and no retired ones.
- Emerald ore rates match diamonds at depth everywhere, and mountains also have veins in their peaks.
