# Hamlet

A Fabric mod for Minecraft 1.21.1 that overhauls villagers. Villagers do real work, keep what they
produce in an inventory you can look through, and sell it to you. Many professions also offer
services. Levels, trade rerolling and cure discounts are gone.

Needs to be installed on both server and client (custom trading screen).

## Core rules

- **Inventory:** villagers have two inventories.
  - **Stock** (27 slots): all work output. Players never reach it directly; it's only sold, through
    the trading screen. A full stock stops the villager working.
  - **Storage** (9 slots): anything players put in, and anything the villager picks up off the ground.
    Players can take it back freely. It's never for sale and doesn't affect work. It drops when the
    villager dies (including turning into a zombie); stock doesn't.
- **Trading screen:** laid out like vanilla's. On the left, a scrolling list of offers: the
  profession's services first, then one row for each kind of item in stock. Clicking a row selects it
  and moves its cost into the two payment slots; the result slot shows what you'll get, and taking it
  makes the trade (shift-click repeats it while you can pay). On the right: the villager's storage,
  the payment and result slots, and your inventory. A service that copies an item gets a row for each
  matching item you carry, so you pick which one.
- **Prices:** one global list, the same for every villager whatever its profession, so changing or
  losing a job doesn't make its stock free. Data-driven: `data/<namespace>/hamlet/prices/*.json`
  files are merged, so mods and datapacks can add their own. The wandering trader's catalogue is
  separate (`hamlet/wandering_trader.json`). Anything without a price, such as modded fishing loot,
  sells for 1 emerald.
- **Jobs:** the workstation gives a villager its job, and it keeps that job until the workstation is
  destroyed, whether or not it has been traded with.
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

| Profession | Workstation | Work | Sells / services |
|---|---|---|---|
| Farmer | Composter | Spends its work period patrolling within 9 blocks of its composter. Whenever it spots a fully grown crop, it walks over and harvests just that crop, leaving it to regrow in place, so layouts stay exactly as the player planted them. No seed cost. Harvests crops, sweet berries, cocoa and nether wart (reset to their first stage), sugar cane, cactus and bamboo (everything above the bottom block), and pumpkins and melons (only with a stem attached). Seeds and poisonous potatoes are thrown away (see Discards). | Stock: harvest output. No longer makes bread or shares food. |
| Shepherd | Loom | Spends its work period wandering within 12 blocks of its loom. When an animal is ready, it walks up to it holding the right tool and stores what it gives: wool from sheep (when they have wool), and every 5 minutes milk from cows and goats, mushroom stew from mooshrooms, a scute from armadillos (not while rolled up) and an egg from chickens. | Stock: wool, milk, mushroom stew, armadillo scutes, eggs. |
| Beekeeper | Smoker | Spends its work period wandering within 12 blocks of its smoker. When a hive or bee nest nearby is full of honey and smoked by a lit campfire below it (vanilla's check for calm bees), it walks over and harvests it, picking at random between shears (3 honeycomb) and a glass bottle (1 honey bottle), as a player would. Unsmoked hives are left alone, so the bees are never angered. Smokers are free because the butcher is retired: a villager that would become a butcher becomes a beekeeper. | Stock: honey bottles, honeycomb. |
| Fisherman | Barrel | Spends its work period at the shore of water within 9 blocks of its barrel. Casts a bobber 2 to 5 blocks out, waits for a bite (vanilla's 5 to 30 seconds), and stores a catch rolled from vanilla's `gameplay/fishing` loot table, the one players use, so mod and datapack changes to fishing loot apply. Treasure catches need open water around the bobber (vanilla's open-water check). | Stock: everything it catches, including treasure. |
| Librarian | Lectern | Stays near its lectern during its work period. | Service: copy an enchanted book. Enchanted book + 8 emeralds = 2 enchanted books. Archive tomes to follow, designed together with Archive. |
| Cartographer | Cartography table | Stays near its cartography table during its work period, and uses its visits to the table to look up nearby structures ahead of time. | Service: an explorer map to the nearest ocean monument, woodland mansion or trial chambers, 8 emeralds each. |
| Archaeologist | Decorated pot | | Service: duplicate a smithing template. Template + 1 emerald = 2 templates. Vanilla's duplication recipes are removed. |
| Cleric | Brewing stand | | Service: bottle your experience. 1 emerald + 1 level = 1 Bottle o' Enchanting. With Archive this is effectively 1:1, so the only cost of storing a level is the emerald. |
| Wandering Trader | None | | Sells saplings, coral blocks, seeds and tall flowers. |

## Breeding

Villagers breed when they have enough good food, provided by the player. Farmers no longer supply it.

Villagers pick up any item off the ground into their storage while it has room. Vanilla's breeding
reads vanilla's inventory, so breeding stays off until Hamlet decides what counts as good food in
storage.

## Emeralds

Nobody buys from the player, so emeralds come from loot, mining and raids. Emerald ore is rebalanced to
match:

- As common as diamonds, at diamond depth.
- Mountain biomes also get diamond-sized veins up in the peaks, at vanilla emerald's height range.

## Villages

Houses for retired professions are removed from village structures and replaced with new hand-made ones,
including ones for the new professions (so smokers, campfire-smoked beehives and decorated pots actually generate).

## Open questions

- **Stock limits:** does a full inventory stop a villager working? How much can each hold?
- **Work rates:** how often does each profession produce? (Keeps output from outpacing normal farms.)
  A fisherman on vanilla bite timing makes about 20 catches per working day.
- **Good food:** players give villagers food by putting it in their storage or throwing it to them.
  Which foods count, and how much is enough?
- **Workstations:** decide what happens if a workstation is moved rather than destroyed. Carry On can
  lift lecterns, barrels and similar blocks (Carry On may be removed from the pack anyway). Decorated
  pots also shatter when hit by projectiles, which would cost the villager its job.
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
- Throw any item near a villager: it picks it up into its storage, not its stock, and stops once storage is full.

- Place and break a workstation: job is gained and lost, with no trading involved.
- Remove a villager's bed: it stops working, its trade screen says "no bed", it still sells its
  stock, and it resumes once it claims a new bed.
- The offer list matches what the villager produced. Selecting a row moves its emeralds into the payment slots; taking the result buys one and removes it from stock; shift-clicking the result buys repeatedly until the emeralds or stock run out.
- Closing the screen returns anything left in the payment slots.
- Any item can be put into and taken from the storage row, including by shift-click.
- An old save's unpriced stock items move into storage.
- Farmer patrols its field, harvests mature crops one at a time, and leaves a mixed crop layout
  unchanged. Crops more than 9 blocks from its composter are left alone.
- Villagers ignore items dropped on the ground.
- Cleric takes exactly 1 level and 1 emerald per bottle. Archaeologist returns 2 templates, and the
  crafting recipe is gone.
- Fisherman walks to the shore, holds a rod, casts a bobber, and each bite adds a catch to its stock.
- Fisherman only catches treasure near open water.
- Shepherd walks up to woolly sheep with shears and shears them into its stock; also milks cows and goats, gets stew from mooshrooms, brushes armadillos and gathers eggs.
- Beekeeper claims a smoker, walks to full hives with a lit campfire under them and harvests them without angering the bees; full hives without a campfire are left alone.
- Librarian lists a copy row for each enchanted book you carry; selecting one moves the book and 8 emeralds in; the result is a copy, and the original stays in the payment slot.
- Cleric's bottle can't be taken without a level.
- Cartographer lists each explorer map at 8 emeralds, pointing at the nearest structure.
- Villagers killed by zombies always zombify, on Easy, Normal and Hard. Curing changes no prices.
- Breeding works with player food and doesn't happen without it.
- New villages contain the new houses and no retired ones.
- Emerald ore rates match diamonds at depth everywhere, and mountains also have veins in their peaks.
