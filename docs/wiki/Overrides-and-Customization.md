# Overrides and customization

[Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) · [Worked scenarios](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios) · [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance)

Edit **`config/essence_ascendance/balance_overrides.toml`**. It supports factual corrections and exact generated-value overrides. Every change applies during generation or explicit rebuild, never as a live overlay or through saved-profile reuse.

Prefer a factual correction when the model misunderstands access, supply or an observable capability. Use an exact value when you deliberately want to pin a particular result. Both still face final validation.

## Root and grammar

Root `schema_version` is integer `1`, defaulting to 1 when omitted. This file shares the [bounded TOML grammar](https://github.com/mistaboom/essence_ascendance/wiki/Configuration#files-scope-and-timing), including its 4,000,000-byte limit. Unknown tables/keys, duplicate fact IDs, bad types or contradictions reject generation.

Supported tables are repeated `[[fact]]` and one `[exact]`. Examples below are complete small inputs; when combining them, keep one root schema and one exact table.

## Factual overrides

At most **10,000 facts** are accepted. Use tags or recipe families when many entries share one correction.

### Identity and precedence

All following keys belong to `[[fact]]` in the overrides file.

| Key | Type / default | Allowed values / meaning |
| --- | --- | --- |
| `kind` | String / required | `item`, `block`, `item_tag`, `block_tag`, `recipe`, `recipe_family`, `source`, `enemy`, `provider`, `capability` |
| `selector` | String / required | Nonempty identifier without whitespace. Use exact registry/tag/type/source/provider IDs appropriate to kind. A recipe family is a registered recipe type. |
| `id` | String / derived `kind:selector` | Nonblank unique diagnostic identifier; explicit stable IDs make changes easier to trace |
| `priority` | Integer / `1000` | `-100000..100000`; higher priority wins |
| `reason` | String / omitted | Nonblank plain-language factual explanation retained with file/line provenance |
| `confidence` | Number / omitted | Finite `0..1`; your evidence certainty, not desired power |

Equal-priority conflicts are resolved deterministically and reported. Unmatched selectors warn. Omitted factual values do not universally mean false or zero: supported native/provider evidence and consumer fallbacks remain relevant. At least one factual field is required except for provider facts.

### Classification and access

Each is a nonblank string, omitted by default.

| Key | Allowed values / units | Meaning and interactions |
| --- | --- | --- |
| `stage` | `entry`, `early`, `mid`, `late`, `apex` | Supported acquisition stage; interacts with capability/tier decisions |
| `availability` | `finite`, `renewable_manual`, `renewable_automated`, `effectively_infinite`, `unknown`, `administrative` | Supply class; resource policy determines extraction response |
| `automation` | `none`, `player_gated`, `bounded`, `scalable`, `passive`, `infinite`, `unknown` | Operating supply class, separate from exact rate/cost |
| `classification` | Descriptive string; enemies require `routine`, `elite`, `boss`, `apex` or `unknown` | Source/resource classification or encounter role |
| `dimension` | Identifier string | Dimension/source annotation for consumers that support it; does not create a dimension |
| `gate` | Identifier string | Access-gate annotation; does not implement a quest or change player completion |
| `slot` | `head`, `chest`, `legs`, `feet`, `body`, `offhand`, `mainhand_melee`, `mainhand_bow`, `mainhand_crossbow`, `mainhand_caster`, `mainhand_tool` | Native reference family; `body` is evidence but excluded from player equipment frontiers |

A custom equipment reference needs a registered item, supported reachable acquisition, a usable slot and measurable axes. Explicitly included enemies need supported registry identity and the required health/stage/confidence evidence. Labels alone do not establish a fight or a fully composed loadout.

### Boolean facts

Each key below is **boolean**, accepts `true`/`false`, defaults to **omitted**, and has no numerical unit.

| Key | Meaning and interactions |
| --- | --- |
| `attainable` | Declared supported access; must reflect real pack access |
| `disabled` | Excludes affected evidence/recipe/provider where consumed |
| `creative_only` | Marks creative-only content; cannot also include it as a normal reference |
| `administrative` | Marks administration/test content; excluded from ordinary references |
| `joke` | Marks novelty content for evidence policy |
| `quest_gated` | Access depends on a gate; use stage/gate/reason to explain |
| `include_reference` | Explicit reference inclusion/exclusion, subject to supported axes/access and validators |
| `renewable` | Supply can recur; does not establish free output or exact throughput |
| `passive_generation` | Unattended source behavior; interacts with attention/automation pressure |
| `flight` | Supported flight capability; does not alone prove operating power or speed response |
| `flying_speed_compatible` | Verified response to the native flying-speed control, not Elytra velocity; needs the same reachable flight source |
| `vein_mining` | Supported multi-block vein action |
| `area_mining` | Supported area action, separate from simple tool speed |

### Numeric resource and production facts

All are finite numbers, **omitted by default**. Unknown rates/costs should stay omitted; 0 is an affirmative value.

| Key | Bounds / units | Meaning and interactions |
| --- | --- | --- |
| `resource_value` | `>=0`, normalized opportunity-value units | Underlying acquisition/usefulness value, not final Essence yield |
| `throughput` | `>=0`, items/second | Supported production rate, not expected loot per event |
| `startup_cost` | `>=0`, normalized value units | One-time setup burden; not consumed/credited again for every output |
| `marginal_cost` | `>=0`, normalized value units | Recurring burden per output |
| `parallelizability` | `>=0`, relative producer scale | 1 represents one producer; scalable production influences pressure |
| `player_attention` | `0..1`, fraction | 0 passive, 1 continuous player action |
| `processing_time` | `>=0`, **server ticks** | Supported production duration; 20 ticks = 1 second |
| `output_count` | Whole number `1..2,147,483,647`, item count | Nominal output count; fractional expected output belongs in probability |
| `probability` | `0..1`, probability | Expected output chance, combined with count |

### Numeric equipment and enemy facts

All are finite **nonnegative numbers**, omitted by default. Use the actual supported native axis; a measurement under a buff or operating condition needs a truthful reason and scope.

| Key | Units / meaning |
| --- | --- |
| `health` | Native health points; 2 health points = 1 heart |
| `armor` | Native armor points |
| `damage` | Native damage points in supported measurement scope |
| `attack_speed` | Native attack-rate axis, attacks/second |
| `toughness` | Native armor-toughness points |
| `mining_speed` | Native tool destroy-speed factor; actual breaking time still depends on block/tool rules |
| `harvest_level` | Native harvest-tier level |
| `durability` | Durability points |
| `enchantability` | Native enchantability score |

A damage number does not model every spell's targeting, uptime, energy or conditional payload. Facts can fill supported gaps; they do not execute unknown code.

### String arrays

`capabilities`, `dependencies` and `acquisition_sources` are arrays of nonblank strings, omitted by default. They declare identifiers for supported consumers. No universal rate or power is inferred from the count of labels.

### Invalid combinations

The parser rejects these paired claims:

- disabled and attainable;
- creative-only, administrative, unattainable or disabled **and** included as reference;
- effectively infinite availability and explicitly nonrenewable;
- passive generation and player-gated automation.

Unsupported/unused fields remain limitations in diagnostics. A syntactically accepted correction is not proof of complete integration support.

## Valid factual examples

**Conditional pack fact:** enable this only when the pack truly supplies inexpensive entry-stage cobblestone generation. Under balanced/conservative policy, effectively infinite supply can resolve zero dissolution. The output is illustrative, not a tested benchmark.

```toml
schema_version = 1

[[fact]]
id = "entry_cobblestone_supply"
kind = "item"
selector = "minecraft:cobblestone"
priority = 1000
stage = "entry"
availability = "effectively_infinite"
automation = "scalable"
renewable = true
confidence = 1.0
reason = "This pack makes an inexpensive cobblestone generator available at entry."
```

**Exclude known administrative content:** this uses a real vanilla identifier and does not invent a custom mod item.

```toml
schema_version = 1

[[fact]]
id = "exclude_barrier_reference"
kind = "item"
selector = "minecraft:barrier"
attainable = false
creative_only = true
administrative = true
include_reference = false
reason = "Barrier is administrative content rather than survival equipment."
```

To address a named provider, find its **actual provider ID** in generation diagnostics and use `kind = "provider"` with that selector and the appropriate supported fact, such as `disabled = true`. A mod display name is not a substitute for its provider identifier.

## Exact generated-value overrides

Section `[exact]` accepts **quoted JSON Pointer keys** mapped to primitive values or primitive arrays. Supported existing targets are under `/runtime/` and `/economy/`. New fields, protected metadata/profile identity/composition changes and unsupported paths are rejected.

| Setting component | Type / default | Bounds, units and behavior |
| --- | --- | --- |
| Pointer key | Quoted string / required | Existing permitted path; `~0` escapes `~` and `~1` escapes `/` inside keys; zero-based array indices |
| Value | Primitive or primitive array / omitted | Must match target type, native unit/grid and all typed/runtime/build/economy validators |
| Baseline when omitted | Generated value | Pack-dependent, not a fixed default. Inspect `runtime_parameters.csv` or saved data. |
| Dissolution yield targets | Numeric whole units | Nonnegative whole Essence; fractional exact yields fail and conservation can reduce requested output |

Exact customization is an open set of existing profile fields, not a second fixed list of input keys. The target's range/units belong to its typed field; the [bundled annotated override examples](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/resources/balance/balance_overrides.toml) document supported system contracts. Generated reports locate actual paths and values. Do not assume all numbers share one range or that every scalar can be freely changed.

This confirmed example pins the generated maximum for the registered melee-damage Bonus:

```toml
schema_version = 1

[exact]
"/runtime/statMaxBonuses/essence_ascendance:melee_damage" = 12.0
# Illustrative magnitude, not a universal recommendation.
# Rebuild still checks native output/headroom and can reject incompatible composition.
```

Changing intrinsic Bonus magnitude can reprice its track; separately requested exact cost fields are reapplied afterward. Exact pins remain pins during future adaptation, so remove obsolete ones when pack intent changes.

## Apply and inspect

Back up inputs/profile/world and try corrections in staging. Rebuild explicitly, then compare matched facts, warnings, final values and report integrity. A rejected candidate preserves previous valid authority; a successful installation is not undone by later report failure.

[Maintenance procedure](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery) · [Interpreting evidence](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance)

Source: [schema and contradictions](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/config/BalanceOverrides.java), [fact collection](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/engine/PackEvidenceCollector.java), [production duration and overrides](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/economy/EconomyGenerator.java).
