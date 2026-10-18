# Procedural balance

[Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) · [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance)

Essence Ascendance uses a small shared model to generate pack-relative equipment, progression and prices. It provides an editable starting point, not a simulation of every pack's survival economy.

## What the model uses

Item properties, common semantic tags and loaded standard recipe interfaces provide intrinsic material estimates. Quantities, returned containers and exact reversible conversions constrain actual dissolution payouts. Native equipment and hostile attributes provide measured combat/tool references.

Relative equipment bands describe measured strength. They do not prove when an item is obtainable. Outlier policy limits extreme catalog measurements; it cannot inspect hidden upgrades, arbitrary callbacks or all equipment synergies.

There is no acquisition graph, quest traversal, loot sampling, demand graph or mod-specific valuation adapter. Unknown acquisition does not automatically make an item worthless. Rates, operating costs and survival access remain unknown when they are not observed.

## Generated systems

| System | Resolved output |
| --- | --- |
| Items | Intrinsic estimates, Essence routes and conserved whole-unit payouts |
| Equipment | Native tier baselines from compatible catalog references |
| Bonuses | Active player-tier span, effects, costs and native limits |
| Skills | Unlock tiers, meaningful ranks, native parameters and prices |
| Attunement | Activity targets, category breadth and development references |
| Machines | Costs, capacities, rates and effect policy |
| Latent Ore | Configured distributions and supported terrain hosts |

Skill unlocks/ranks/stat endpoints are editable in `skill_settings.toml`. Bonus active tiers and effect bounds are editable in `bonus_settings.toml`. The main config retains global power, effort, activity and ore policy. Advanced measurement corrections belong in `balance_overrides.toml`.

Default generation and configured overrides still face native floors/caps, prerequisite rules and conservation checks. A binary skill cannot gain invented numeric ranks. Bonus minimums apply at completed player tiers; partial funding remains continuous.

## Read the result correctly

The saved validated profile supplies gameplay. Restart loads it; configuration edits require explicit rebuild. Reports are inspection outputs, and the Archive displays synchronized installed values.

Economic estimates and spendable Essence are different. Only complete modeled material bills impose hard recipe conservation. A passing recipe check does not prove every opaque machine loop, acquisition route or gameplay interaction.

Equipment references preserve compatible slots and paired measurements. Axis maxima from different alternatives are not one simultaneous build. Skill projections assume their declared prerequisites, milestones and selected choices.

Use current reports to inspect unexpected output, adjust broad policy or emitted system settings, then rebuild and test representative gameplay. The [current model notes](https://github.com/mistaboom/essence_ascendance/blob/main/docs/simple-balance.md) describe deliberate limitations. Older acquisition/provider reports are historical.

## Terms in reports

| Term | Meaning |
| --- | --- |
| Authority | The validated installed profile used by gameplay |
| Native parameter | A mechanic's actual unit, such as damage points, ticks or a probability fraction |
| Catalog strength band | Relative measured equipment strength, not an acquisition stage |
| Frontier | Selected compatible reference measurements after outlier policy |
| Native minimum | Smallest meaningful supported effect or rank improvement |
| Headroom | Allowed added contribution in a modeled composition |
| Complete recipe | A supported static material bill suitable for payout conservation |
| Advisory estimate | A policy estimate that does not prove access or a spendable payout |
