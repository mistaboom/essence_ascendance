# Progression, power and Attunement

[Procedural balance](https://github.com/mistaboom/essence_ascendance/wiki/Procedural-Balance) · [Skill investigation](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios#investigating-an-unexpectedly-gated-skill)

Use the [Archive](https://github.com/mistaboom/essence_ascendance/wiki/Players-and-the-Archive) for current per-rank effects, costs, recipes and equipment statistics. This page explains the pack-design relationships.

## Four related systems

| System | Progress stored | What governs it |
| --- | --- | --- |
| Player Ascension | Player tier and chapter seals | Eligible Category Attunement activity |
| Bonuses | Stored investment | Generated tier cap, function and equipment applicability |
| Skills | Owned rank receipts and selections | Generated meaningful ranks, prerequisites/gates and runtime conditions |
| Equipment infusion | Item tier and partial infusion | Generated Infuser/material/reservoir policy and item eligibility |

Player tier and item tier are different. Setting one does not silently set the other.

## Equipment and competition

External references must be supported and attainable, with usable slots and measurable native axes. Creative/test content and unsupported configurations do not automatically become the strongest comparator. The outlier policy controls supported extremes.

The generator builds progression-aware reference envelopes. Native attributes, consumables, enchantments, trades/anvils and audited providers can contribute compatible capability routes. Binary flight, area mining or indestructibility is not an exact speed or infinite effective-health measurement.

Conditional uptime, operating cost, units and source configuration remain attached to evidence. Comparisons use alternatives rather than adding every mod's best item into an impossible loadout.

## Power budgets

Equipment keeps its generated pack-reference foundation. Bonus/Nexus and Skill policy allocates added headroom and checks composed outcomes.

Default target envelopes at the final powered tier are equipment at reference parity, external gear plus developed Nexus at 2×, external gear plus ranked Skills at 2×, and combined builds at 3×. The modeled low-health Desperation burst can use 4× while sustained output remains 3×. Combined powered-tier ceilings progress 1.5/1.7/2/2.5/3× under default intent.

These are **modeled output envelopes**, not separate attack multipliers or guaranteed measured DPS. Power/band policy can change intent; model assumptions and native output validators still constrain generation. Budget shares are semantic allocation, not a tax deducted from live damage.

In practice, the shares guide which system carries a capability's emphasis. A 25% Skill share does not mean a Skill multiplies your attack by 0.25. Read the resolved effects and compatible build projections to see the actual outcome.

## Bonus tracks

Different Bonuses have different functions and supply categories. Track utility, native thresholds, compatible routes and power headroom can resolve different start/completion tiers, checkpoint shapes and costs.

Normal sliders purchase continuously; native thresholds inform useful checkpoints rather than forcing every purchase to snap. Stored investment survives a lower tier/cap, while effective investment follows the current cap. Equipment/action applicability can further limit the realized effect.

Bonus development for Attunement is normalized realized generated power, independent of merely making a track more expensive. A global price increase does not automatically represent more developed power.

## Skills and meaningful ranks

Catalog tier, candidate availability tier and final generated tier are distinct. Admitted functional evidence can move a candidate, then prerequisites and setup access clamp it. Rank-specific tier/requirement gates remain additional checks.

A rank must deliver a supported native improvement. Non-improving or sub-floor projections are removed; a binary ability has one meaningful state. The actual purchasable maximum is the published generated curve, not an assumed catalog rank count.

Owning a Skill does not guarantee effectiveness. Choices/exclusions, prerequisite ranks, replacements, permanent milestones and runtime conditions still matter. Zero-cost admin grants neither fund every prerequisite nor prove normal affordability.

Receipts preserve actual historical paid cost/category for supported refunds and Attunement acceleration after repricing. Committed evaluation determines effective state. Normal Nexus transactions validate server-side state/revision and affordability before commit.

## Category Attunement and player Ascension

Player Ascension requires **only enough current-chapter seals**, with valid authoritative adjacent-chapter policy. It consumes no wallet Essence, Bonus investment, Skill or equipment. Latent onboarding auto-promotes to Dormant; later powered transitions use normal Ascension/Nexus action.

Eligible confirmed outcomes include damage/defeats, incoming/prevented damage, health/hunger/status changes, movement/exploration, resource/crop/fishing work, and accepted XP/station/trading/repair operations. Event eligibility, attribution and supported adapters determine credit; merely triggering a callback is not enough.

Current category Bonus development and historically paid owned-Skill receipts accelerate **future** contribution. Wallet and ordinary gear value do not count. Zero-investment base methods remain part of reachability.

Repetition history uses reference work, not event count. A positive floor keeps legitimate repeated activity useful; bounded variety rewards changing sources. Attribution/deduplication avoids double-crediting a root outcome. Large legitimate outcomes are limited by remaining seal progress rather than an arbitrary per-action ceiling.

Earned normalized fractions survive target/rate changes within the same chapter. A chapter transition clears its method/history/discovery accounting. Respec changes future acceleration, not past earned progress. Default final breadth leaves a category optional.

## Equipment infusion remains separate

Items and Foci have their own tiers and incomplete infusion state. Linked Infuser operations consume their generated costs/materials/reservoir subject to ownership and item/equipment eligibility. Carrier conversion and repair also use saved policy.

Admin item-tier commands bypass this path for testing/recovery. They are not a gameplay recipe or a recommendation for balancing other mods' equipment.

## Read the decision chain

For a surprising gate, inspect generated availability and prerequisite clamps, then personal ownership, milestone state and selected loadout. For a surprising effect, inspect the generated Bonus/rank and relevant gameplay/activation snapshot.

[Reports and provenance](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance) · [Diagnostics](https://github.com/mistaboom/essence_ascendance/wiki/Diagnostics-and-Testing)

Source: [build targets](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/runtime/BuildPowerTargets.java), [Bonus generation](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/runtime/BonusTrackGenerator.java), [Skill generation](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/skill/balance/SkillBalanceGenerator.java), [Ascension](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/progression/AscendanceEngine.java), [Attunement accounting](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/attunement/AttunementService.java).
