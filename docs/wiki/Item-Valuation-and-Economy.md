# Item valuation and the Essence economy

[Procedural balance](https://github.com/mistaboom/essence_ascendance/wiki/Procedural-Balance) · [Investigate an item](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios#investigating-an-unexpectedly-valuable-or-valueless-item)

## Value and yield answer different questions

**Opportunity value** describes supported acquisition effort and usefulness. **Dissolution yield** describes extractable Essence after eligibility, routing, supply policy and conservation.

A useful item can have zero ordinary yield. That may reflect an effectively infinite source, cheap automation, unsuitable evidence or a conversion constraint. It is not automatically a missing mapping.

Essence routing uses supported item/function/source evidence across Offense, Defense, Vitality, Mobility, Gathering and Utility. It is not a universal equal six-way split.

## Recipes and acquisition effort

The generator considers supported loaded recipes, ingredient alternatives, quantities, returned containers, reusable tools, byproducts and expected outputs. Crafting, cooking, smithing, stonecutting and modeled interactions participate under their supported contracts.

Acquisition paths carry ancestry. A cheaper path that depends on its own descendants cannot simply establish initial access. Unresolved routes remain visible rather than seeding a free entry resource.

Fully modeled acquisition uses its measured per-output effort. A material category's fallback estimate does not impose a minimum price on a cheap complete recipe or a multi-output harvest. Block harvesting shares both acquisition effort and reusable tool wear across the expected output of one event; for example, an average 3.5-item drop shares that effort across 3.5 items. Coarse ore-yield tags do not apply a second quantity adjustment to measured loot and are not treated as geological abundance evidence.

A block's loot table alone does not establish access to that block. Independent harvesting requires collected natural-placement evidence. Otherwise the generator must resolve and pay for the supplied block item through an independent acquisition path, including blocks consumed by unsuccessful harvest attempts. Missing source items, unknown prerequisites and craft/break cycles remain unresolved rather than creating an entry resource.

Downstream usefulness counts distinct applications, rather than recipe IDs. Alternate crafting methods and decorative shapes, colors or oxidation variants cannot multiply the same use. Construction and otherwise unproven components retain a small demand signal; native tools, food, operating blocks and other supported functions retain their functional signal. Crossing a mod namespace is reported as provenance and does not independently raise the price.

Access also includes supported natural terrain, crops/trees, biological drops, encounter conditions, tools/stations, trades, starting sources and quests. A renewable label alone does not prove that the setup, stock or recurring inputs exist.

**Illustrative example:** a recipe consuming a bucket and returning an empty bucket should account for the returned container. An opaque machine with the same item input/output can still lack sufficient evidence for its hidden energy or stochastic returns.

## Supply and automation

Source pressure distinguishes throughput, parallelism, attention, setup and marginal costs. One-time setup is not credited as a consumed input on every output. An expected drop per event is not automatically a measured items/second rate.

- **Balanced/conservative:** explicitly effectively infinite resources get zero generated dissolution.
- **Abundance-aware:** can permit discounted positive infinite-source yields.
- **All policies:** retain conservation constraints.

Stock/restock-limited trading can be productive without being modeled as instant unrestricted material conversion. Finite sampled offers describe conditional access, not a guaranteed villager distribution.

Use [economy policy](https://github.com/mistaboom/essence_ascendance/wiki/Configuration#economy) to choose response strength. Correct wrong source facts before compensating through global affordability.

## Whole Essence and conversion safety

Ordinary generated item yields use whole Essence. Final ordinary displayed yield matches ordinary credit; compression/decompression families are reconciled together instead of rounding each form into a profit opportunity.

The conservation solver lowers outputs monotonically through complete modeled production routes, including supported alternatives, catalysts, returns and probability. It has bounded passes; unresolved unsafe/downstream outputs can be suppressed if convergence fails. Incomplete unknown machine semantics are not proof of a safely modeled path.

Internal conservation is useful protection, but cannot detect every undocumented third-party callback or loop. Verify important pack production chains in staging.

Fractional transaction accounting still exists for supported cost/conversion operations. Its owner-specific carry is world data, separate from the ordinary whole-item yield policy.

## Names and unsupported evidence

Names can nominate candidates or aid classification; they do not establish a custom spell, machine rate, flight compatibility or hidden tool action. Generic recipe discovery is broader than complete operating knowledge.

A factual override can express a supported access/rate/axis correction. A typed integration is needed when production semantics require more than flat facts. Exact yields remain whole and can be reduced by conservation.

## Diagnose before changing a number

1. Check installed authority/integrity.
2. Explain the saved item using the exact full ID.
3. Compare opportunity value, final yield, access, source class, routing and warnings.
4. Export exhaustive sources when the summary does not explain the decision.
5. Correct false evidence or policy deliberately, rebuild in staging, and compare.

```mcfunction
/essence debug balance item "minecraft:iron_ingot"
/essence admin balance export
```

Both require permission 2 and support console. The first reads saved evidence; the second writes derived reports. Neither recalculates item value. The detailed [command reference](https://github.com/mistaboom/essence_ascendance/wiki/Diagnostics-and-Testing#saved-balance) describes missing-evidence cases.

Players should use **Archive → Reference → Essences → Item Yields** for their connected server's actual table.

Source: [valuation](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/valuation/ProceduralValuationEngine.java), [source pressure](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/economy/SourcePressurePolicy.java), [conservation](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/economy/EconomyConservationSolver.java).

## Enchantments and stack components

Enchanted-book yields use the actual stored enchantments and levels. Native effect records provide the primary category and strength evidence: damage, protection, attributes, repair, wear reduction, gathering and other supported effects retain their own physical units. The same factual measurements feed capability analysis. Economic normalization never feeds a price back into skill power. Duplicate effect components on one axis use the strongest measured contribution rather than multiplying the score. Conditional effects receive a conservative discount; their proc rate is not assumed. Native registry context preserves item/entity-tag predicates during factual decoding. A burning projectile is valued as a conditional capability; its own burning duration is not mistaken for victim-burning duration. Native level costs supply a small secondary effort term. Empty-effect definitions and definitions containing only known XP suppression can use supported equipment and anvil effort as explicitly uncertain evidence; an opaque custom effect receives no inferred premium. Curses receive no positive premium.

Each book retains one plain-book carrier, capped by its generated enchanted-book budget. Its additional value is `ceil((C - B) * S / (5 + S))`, where `C` is the conservation-checked book ceiling, `B` is the smaller of the plain-book yield and that ceiling, and `S` is the sum of the supported enchantment scores. The shared concave discount gives multi-enchantment books contributions from each effect without charging for several carriers. Exact whole-Essence category allocation follows that total. The half-saturation score of 5 is an economic policy constant, not a skill minimum or a claim about player throughput.

The native unit references are one damage/protection point, one effective extra lifetime from wear reduction, one durability-per-XP unit, two mining-efficiency points, four ignition seconds, one percentage point of equipment-drop probability, one quarter-second of crossbow charge reduction, and 0.1 for additive attribute multipliers. Distinct units remain distinct in the factual capability evidence. Actual stored levels above the normal definition maximum are evaluated up to the native 255 limit; the Archive lists only the definition's normal level range.

Native target relationships also affect categories. Victim-triggered damage returned to the attacker is defensive reflection. A non-damaging self-centered explosion uses its actual knockback multiplier as movement evidence. Harmful status effects applied to an opposing participant establish control; mixed status pools remain uncertain. Direct self damage and harmful self status do not earn a positive premium.

Equal-level anvil combination bounds each next-level score to at most twice the previous positive score. Definitions that have a positive level-I effect retain that clamp even after a later zero. A definition whose actual effect starts at a later level keeps its real positive magnitude; no synthetic strength is assigned to its zero-effect levels. For these definitions, the combined premium across the entire book is additionally limited to `B + F(R)`, where `F(x) = ceil((C - B) * x / (5 + x))` and `R` counts only definitions with a positive level-I effect. Thus the final premium is `min(F(S), B + F(R))`. All later-starting effects share one carrier allowance, including at stored levels above the definition maximum; they do not each receive another carrier. This deliberately conservative marginal limit admits real later effects while two input books always fund their combination. With a zero-value carrier, later-starting effects cannot create value from two zero-value inputs.

The concave total and upward whole-unit rounding preserve the combination bound. Removing enchantments cannot increase the total. Enchanted-book ingredients are valued at the plain-book component minimum during recipe conservation; a recipe cannot consume a cheap book while claiming its most valuable possible configuration as input. The bounded-cycle fallback tracks that carrier dependency as well.

Applied equipment enchantments can change Essence categories within the equipment's existing material budget; they do not add an unproven premium. Consequently native book application, equipment combination and enchantment removal cannot multiply the material total. Custom modded stored-enchantment carrier items and custom extraction/duplication callbacks need typed support; the generic policy does not claim to understand those operations.

Server dissolution, command projections, client tooltips, tooltip search, and the Archive share the same component resolver and whole-unit allocation. Minecraft synchronizes the effective enchantment registry; the server synchronizes generated base budgets. The item-only enchanted-book row in `valuation.csv` and balance inspection is explicitly an upper bound. `/essence test enchantments` writes `diagnostics/enchantment_yields.json`, including all loaded registry definitions and levels, categories, scores, exact outputs and unsupported definitions. It also identifies effects that first become positive after level I and verifies a positive premium whenever the carrier and ceiling can fund one. It needs no player and changes no progression or inventory. Archive delivery is unchanged.
