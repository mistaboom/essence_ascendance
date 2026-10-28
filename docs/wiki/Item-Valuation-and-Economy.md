# Item valuation and the Essence economy

[Pack balance](https://github.com/mistaboom/essence_ascendance/wiki/Procedural-Balance) · [Advanced overrides](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization) · [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance)

## How items receive values

The generator estimates material value from item properties, common tags and loaded recipes. Mining requirements, hardness, declared rarity and supported material-family tags help distinguish ores and materials. Recipe quantities and exact reversible conversions help keep related forms consistent.

This is a broad estimate, not a measurement of your pack's survival difficulty. It does not know quest gates, how quickly a farm runs, how much machine energy costs, or how useful an unclassified ingredient is in a hidden ritual. Similar available information can produce tied values.

Essence categories follow common tags, item types and exposed components. An item name alone does not prove its use. An otherwise unclassified item can receive Utility even if your pack gives it an important combat use.

## Estimated value and actual payout

The estimate helps generate balance. The actual dissolution payout is a separate result, divided among Essence categories and limited by eligible recipe/material relationships. `economic_value` in a report is not the amount the Crucible pays; use `total_essence` and the routed amounts for base-item payouts.

When a complete recipe can make an item cheaply, its payout can be limited by those ingredients and the output count. A recognized recipe's budget must cover its outputs and byproducts. This helps prevent crafting and dissolving a reversible chain from creating Essence. Returned containers matter too.

Some recipes expose only a partial display. Those can help estimate material content without supplying a complete enough bill to enforce a hard payout limit. Passing the known-recipe checks does not cover every hidden machine loop.

A low or zero payout can follow cheap recipes, conversion limits or rounding to whole Essence units. It does not automatically mean the item has no use. The current generator does not apply a special zero-value rule simply because an item can be automated.

Cobblestone, stone and basalt have a low bulk-material estimate ceiling of one Essence because native generators produce them repeatedly without consuming material inputs. Expensive alternative recipes cannot raise that ceiling. Compressed forms preserve their material ratios, and complete recipes carry the cheap value into processed forms; whole-unit conservation can reduce the final payout further. Packs can add their own materials through the item tag `essence_ascendance:low_value_bulk_materials`. Existing current profiles need `/essence admin balance rebuild` to receive this correction; saves and player progress do not need to be reset.

## Item components

Different enchantments and levels can change an enchanted book's actual payout within its catalog budget. A base-item report row does not describe every possible component combination. Check the actual stack's tooltip or the Crucible's result when testing component-dependent items.

Players can browse current item yields in **Ascendance Archive → Reference → Essences → Item Yields**. Use that guide for gameplay, rather than a fixed wiki price list that would only fit one pack.

## Correcting an important mismatch

1. Confirm you rebuilt after changing recipes or settings.
2. Query the exact item with `/essence debug balance item "minecraft:iron_ingot"`, substituting its full ID.
3. Find the item in `reports/valuation.csv`; compare estimate, payout and warning reasons.
4. Check inexpensive alternative recipes, output counts and reversible crafting forms.
5. If the starting estimate is unsuitable, use a supported [advanced correction](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization), rebuild in a test copy, and compare the actual payout again.

An estimate override is not a guaranteed payout override. Recipe checks can still lower the final result. Do not change all investment prices just to compensate for one material whose estimate needs attention.
