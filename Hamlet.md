# Hamlet

A Fabric mod for Minecraft 1.21.1 that overhauls villagers. Villagers do real work, keep what they
produce in an inventory you can look through, and sell it to you. Many professions also offer
services. Levels, trade rerolling and cure discounts are gone.

Needs to be installed on both server and client (custom trading screen).

## Core rules

- **Inventory:** villagers have an expanded internal inventory. All work output is stored there.
- **Trading screen, two tabs:**
  - **Stock:** the villager's whole inventory; buy anything in it.
  - **Service:** that profession's services, if it has any.
- **Jobs:** the workstation gives a villager its job, and it keeps that job until the workstation is
  destroyed, whether or not it has been traded with.
- **No level progression.** Every villager can do everything its profession offers from the start.
- **Zombie curing doesn't affect prices.**
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
| Farmer | Composter | Farms automatically. Resets crops to stage 1 in place, so crop layouts stay exactly as the player planted them. No seed cost. | Stock: harvest output. No longer makes bread or shares food. |
| Shepherd | Loom | Shears sheep, milks cows, gathers eggs. | Stock: wool, milk, eggs. |
| Beekeeper | Beehive | Gathers from nearby hives. | Stock: honey bottles, honeycomb. |
| Fisherman | Barrel | Fishes nearby water using the fishing loot tables. Treasure catches need open water nearby (vanilla's open-water check). | Stock: everything it catches, including treasure. |
| Librarian | Lectern | Copies the book on its lectern. | Sells copies of written books and Archive tomes. Designed together with Archive. |
| Cartographer | Cartography table | | Sells explorer maps for the most important structures. |
| Archaeologist | Decorated pot | | Service: duplicate a smithing template. Template + 1 emerald = 2 templates. Vanilla's duplication recipes are removed. |
| Cleric | Brewing stand | | Service: bottle your experience. 1 emerald + 1 level = 1 Bottle o' Enchanting. With Archive this is effectively 1:1, so the only cost of storing a level is the emerald. |
| Wandering Trader | None | | Sells saplings, coral blocks, seeds and tall flowers. |

## Breeding

Villagers breed when they have enough good food, provided by the player. Farmers no longer supply it.

## Emeralds

Nobody buys from the player, so emeralds come from loot, mining and raids. Emerald ore is rebalanced to
match:

- As common as diamonds, at diamond depth.
- Mountain biomes also get diamond-sized veins up in the peaks, at vanilla emerald's height range.

## Villages

Houses for retired professions are removed from village structures and replaced with new hand-made ones,
including ones for the new professions (so beehives and decorated pots actually generate).

## Open questions

- **Prices:** what do Stock items and maps cost, and are they fixed or data-driven?
- **Stock limits:** does a full inventory stop a villager working? How much can each hold?
- **Work rates:** how often does each profession produce? (Keeps output from outpacing normal farms.)
- **Good food:** which foods count, and how much is enough?
- **Workstations:** decide what happens if a workstation is moved rather than destroyed. Carry On can
  lift lecterns, barrels and similar blocks (Carry On may be removed from the pack anyway). Decorated
  pots also shatter when hit by projectiles, which would cost the villager its job.
- **Beekeeper:** make sure villagers claiming a hive as a workstation doesn't interfere with bees.
- **Shepherd:** should follow the pack's milk and egg rules once those exist, so it can't bypass them.
- **Existing worlds:** what happens to villagers with retired professions, and already generated
  villages?
- **Raid farms:** raids are now a main emerald source, so a raid farm nerf matters more (see NOTES).
- **Emerald ore:** part of Hamlet, or Rebalanced Core worldgen?
- **Cartographer:** finding structures for maps can cause lag; search ahead of time or cache results?
- **Trade screen:** how to display a large inventory, and how Jade or other mods show villagers.

## Testing

- Place and break a workstation: job is gained and lost, with no trading involved.
- Remove a villager's bed: it stops working, its trade screen says "no bed", it still sells its
  stock, and it resumes once it claims a new bed.
- Stock tab matches what the villager actually produced; buying removes it.
- Farmer leaves a mixed crop layout unchanged after harvesting.
- Cleric takes exactly 1 level and 1 emerald per bottle. Archaeologist returns 2 templates, and the
  crafting recipe is gone.
- Fisherman only catches treasure near open water.
- Villagers killed by zombies always zombify, on Easy, Normal and Hard. Curing changes no prices.
- Breeding works with player food and doesn't happen without it.
- New villages contain the new houses and no retired ones.
- Emerald ore rates match diamonds at depth everywhere, and mountains also have veins in their peaks.
