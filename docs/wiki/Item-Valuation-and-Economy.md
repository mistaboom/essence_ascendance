# Item valuation and the Essence economy

[Procedural balance](https://github.com/mistaboom/essence_ascendance/wiki/Procedural-Balance) · [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance)

## Estimate and payout are different

**Economic value** is an intrinsic estimate from item properties, common tags and loaded recipes. **Dissolution yield** is the actual whole Essence budget after routing, eligibility and complete-recipe conservation.

Unknown acquisition does not force value to zero. A zero payout can follow a complete cheap/multi-output recipe, whole-unit rounding or another conservation constraint. It does not prove that the item is useless or impossible to obtain. Advisory estimates can remain above a material bound while actual payouts satisfy it.

Routes use common tags, item types and exposed components across Offense, Defense, Vitality, Mobility, Gathering and Utility. They are not an equal six-way split, and a name alone does not establish function.

## Shared material rules

A bounded recipe pass accounts for ingredient alternatives, quantities, output counts and returned containers. Complete construction bills take precedence over incomplete display getters. Supported disassembly can estimate recoverable contents of an opaque refinement; this does not invent the wear or cost of an unknown catalyst.

Exact reciprocal conversions share conservative material denominations. Tagged ingredients qualify only when the same concrete item can fill every consumed input slot and a matching reverse recipe exists. Mixed alternatives are not expanded into every possible combination.

Ore/raw-material/gem estimates use common family tags, native mining requirements and bounded hardness/yield-band policy. These are coarse metadata signals, not measured world rarity or drop rates. Declared rarity and default glint can supply bounded premiums before recipe solving. Identical available properties can leave items tied.

Opaque recipe displays may inform estimates, but incomplete bills are excluded from hard payout conservation. There is no quest traversal, loot sampling, demand graph, arbitrary-script inspection or mod-specific valuation adapter. Fuel, energy, hidden components, farm rates and acquisition remain unexamined.

## Components and conversions

The enchanted-book catalog payout is a conserved upper bound. Actual enchantments and levels determine a stack's value within that budget; blank books retain the ordinary carrier. Equipment enchantments redistribute the material budget rather than adding free value. Use tooltips, the Archive and `/essence test enchantments` for native component examples.

Complete recipe checks share one consumed input budget across outputs and byproducts. Whole-unit limits and checked arithmetic remain accounting constraints. A passing modeled check is not proof about an opaque third-party operation.

## Investigate a surprising result

Use `/essence debug balance item "minecraft:iron_ingot"` and inspect `valuation.csv`, warnings and saved normalized processes. Compare the actual payout with the cheapest complete material bill; do not substitute the advisory economic estimate for spendable Essence.

`/essence admin balance export` adds exhaustive advisory recipe/source tables from the saved profile. These report structure, not access proof. Correct supported measurements through `balance_overrides.toml` or adjust broad policy, then explicitly rebuild. Exact payout requests still face conservation.

Current implementation: `valuation/SimpleValuationSolver.java`, `valuation/EffectiveProduction.java` and `balance/economy/EconomyConservationSolver.java`.
