# Item valuation and the Essence economy

[Procedural balance](https://github.com/mistaboom/essence_ascendance/wiki/Procedural-Balance) · [Investigate an item](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios#investigating-an-unexpectedly-valuable-or-valueless-item)

## Value and yield answer different questions

**Opportunity value** describes supported acquisition effort and usefulness. **Dissolution yield** describes extractable Essence after eligibility, routing, supply policy and conservation.

A useful item can have zero ordinary yield. That may reflect an effectively infinite source, cheap automation, unsuitable evidence or a conversion constraint. It is not automatically a missing mapping.

Essence routing uses supported item/function/source evidence across Offense, Defense, Vitality, Mobility, Gathering and Utility. It is not a universal equal six-way split.

## Recipes and acquisition effort

The generator considers supported loaded recipes, ingredient alternatives, quantities, returned containers, reusable tools, byproducts and expected outputs. Crafting, cooking, smithing, stonecutting and modeled interactions participate under their supported contracts.

Acquisition paths carry ancestry. A cheaper path that depends on its own descendants cannot simply establish initial access. Unresolved routes remain visible rather than seeding a free entry resource.

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
