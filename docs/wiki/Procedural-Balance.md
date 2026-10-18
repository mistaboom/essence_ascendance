# How pack balance works

[Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) · [Tuning examples](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios)

You do not need to assign a value to every item before adding Essence Ascendance. Let the mod generate a starting balance in a test world, review the result, and change the parts that do not fit your pack.

## What is generated?

| Area | Automatic starting point | Your main controls |
| --- | --- | --- |
| Item yields | Material estimates and Essence payouts from shared properties, tags and recipes | Advanced item/recipe corrections in `balance_overrides.toml` |
| Equipment | Latent starts around iron equipment; higher tiers scale toward the pack's measured equipment | Global power/reference settings and advanced overrides |
| Bonuses | Starting tier, completion tier, effect progression and prices | `bonus_settings.toml` |
| Skills | Default unlock layout, generated rank counts, effects and prices | `skill_settings.toml` |
| Ascension pace | Category activity targets and seal requirements | `[progression]` and `[attunement]` in the main config |
| Machines | Generated operating values and costs | Global settings and advanced overrides |
| Latent Ore | Default distributions and supported host blocks | `[latent_ore]` in the main config |

Skill placement starts from the mod's default tree layout. It is not moved around by quest or capability discovery. You can change its unlock tiers explicitly. Skill ranks and effects still receive generated defaults.

## What the generator can see

It uses ordinary recipe inputs, outputs and quantities; common material tags; item type, rarity and other exposed properties; and native equipment and hostile-mob stats. Mining requirements and hardness help distinguish materials. Exact reversible recipes help keep ingots, blocks and similar forms consistent.

It does **not** infer how hard an item is to obtain from quests, loot, farms, automation, energy use or bespoke scripts. It has no item-valuation rules written for individual content mods. A KubeJS-created recipe can participate if it appears as an ordinary supported loaded recipe; the script itself is not inspected.

Two items with similar exposed information can receive similar estimates even when your pack gives them very different uses or rarity. A plain item used in a powerful ritual may look ordinary to the generator. Test those important cases yourself.

## Meaningful minimums and limits

Generated effects aim to make each offered tier or rank do something useful. A Bonus does not have to begin at Dormant or end at Transcendent. Some effects need fewer tiers or ranks because smaller steps would have no useful gameplay effect.

The automatic power budget is a planning target. Meaningful minimums and explicit Skill/Bonus settings can exceed it. Mechanical limits still apply: configuration cannot invent extra ranks for an on/off ability or make an unsupported effect work.

## A practical tuning loop

1. Finalize the pack's mods, datapacks and recipes in a test installation.
2. Create a fresh world and let balance generation finish.
3. Check early materials, important crafting chains, equipment, Skills and Bonuses in the Archive and Nexus.
4. Edit the appropriate human configuration file. Change a small group of related settings at a time.
5. Run `/essence admin balance rebuild`, check for errors, and test the result.
6. Distribute your reviewed settings with the pack. Keep a complete backup before applying changes to an existing server.

[Saved profiles and rebuilds](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation) explains why a restart alone does not apply edits. [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance) help investigate a particular result; players should use the Archive for gameplay guidance and current values.
