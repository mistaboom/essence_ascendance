# Configuration reference

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Overrides](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization) · [Worked examples](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios)

## Files, scope and timing

| File | Edit? | Purpose |
| --- | --- | --- |
| `config/essence_ascendance.toml` | **Yes** | Policy settings on this page |
| `config/essence_ascendance/balance_overrides.toml` | **Yes** | [Facts and exact overrides](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization) |
| `config/essence_ascendance/generated_balance.json.gz` | **No** | Validated saved server authority |
| `config/essence_ascendance/reports/` and `diagnostics/` | **No, as inputs** | Inspect decisions; edits do not tune gameplay |

**Every setting below is in `config/essence_ascendance.toml` and takes effect on generation or explicit `/essence admin balance rebuild`.** Restart, mappings reload of an existing profile and datapack reload do not apply edited policy to saved balance.

Defaults below are **input defaults**, not fixed gameplay values. Omitted keys use defaults. Numeric ranges are inclusive and require finite numbers. “Number” accepts a decimal or integer; “integer” requires a whole integer token. Ratios, shares and exponents are dimensionless unless stated.

Each input is limited to 4,000,000 bytes. Unknown keys/tables, wrong types and invalid combinations reject generation with file/line information. Supported TOML uses named tables, decimal numbers, booleans, quoted strings and the documented arrays; inline tables, dates, hex numbers, multiline strings and dotted bare keys are unsupported.

## Root

| Key | Type / default | Allowed | Meaning |
| --- | --- | --- | --- |
| `schema_version` | Integer / `1` | Exactly `1` | Input grammar version; also defaults to 1 when omitted. This is different from generated document schema 2. |

## Power

Section `[power]`. Controls position/headroom relative to supported attainable references, not a universal multiplier of the strongest registered item.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `overall` | Number / `1.0` | `0.1..4`, ratio | Overall added-power intent; combines with band emphasis and checked output envelopes |
| `early` | Number / `0.8` | `0.1..4`, ratio | Early progression emphasis |
| `mid` | Number / `0.9` | `0.1..4`, ratio | Mid progression emphasis |
| `late` | Number / `1.0` | `0.1..4`, ratio | Late progression emphasis |
| `apex` | Number / `1.1` | `0.1..4`, ratio | Apex emphasis; lower values rein in developed endgame contributions |

Bands need not be monotonic. Equipment retains its pack-reference foundation; these policies do not tax gear for assumed ownership of every Skill or Bonus. [Power budgets](https://github.com/mistaboom/essence_ascendance/wiki/Progression-and-Power).

## Progression

Section `[progression]`.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `length` | Number / `1.0` | `0.1..10`, ratio | Journey/effort scale; affects generated investment budgets and activity journey. Not an Ascension wallet requirement. |
| `cost_pressure` | Number / `1.0` | `0.1..10`, ratio | Affordability pressure on generated investments; lower requests cheaper costs. Does not correct acquisition facts. |

Use `attunement.pace` for contribution-speed changes without requesting different Essence costs.

## Generation assumptions

Section `[generation]`. These are explicit model assumptions, not measured encounter times.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `routine_seconds` | Number / `4.0` | `0.1..300` seconds | Routine enemy health/encounter window informs DPS pressure |
| `boss_seconds` | Number / `40.0` | `0.1..3600` seconds | Boss encounter window |
| `survival_seconds` | Number / `10.0` | `0.1..300` seconds | Sustained survival/recovery window |
| `entry_resource_effort` | Number / `80.0` | `1..100000`, effort ratio | Entry resource-relative cost baseline, using observed median payout |
| `effort_growth` | Number / `3.5` | `1..20`, growth factor | Progression-band cost growth; combines with length and cost pressure |

Review combat assumptions and category supply reports when adjusting these.

## Attunement

Section `[attunement]`.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `pace` | Number / `1.0` | `0.1..10`, speed ratio | Higher completes seals faster; no direct Essence-price change |
| `maximum_acceleration` | Number / `1.0` | `0..4`, added multiplier | At generated category development reference, default adds up to 1 to base: up to 2× development multiplier. Zero investment retains base credit. |
| `repetition_floor` | Number / `0.15` | `0.01..1`, fraction | Minimum efficiency of legitimate repeated sources; interacts with recent source exposure |
| `variety_strength` | Number / `0.25` | `0..1`, extra fraction | Bounded variety benefit; changing activity is rewarded, not mandatory |
| `history_window` | Integer / `64` | `8..256` reference outcomes | Per-seal recent exposure measured in reference work, not callback count |
| `early_effort_fraction` | Number / `0.75` | `0.1..1`, effort fraction | First powered chapter effort, easing toward 1 by final chapter |
| `onboarding_effort_fraction` | Number / `0.50` | `0.1..1`, effort fraction | Automatic Latent onboarding |
| `breadth_exponent` | Number / `1.0` | `0.25..4`, exponent | Shape of required category breadth; smaller asks broader early participation. Default final progression leaves one category optional. |

Length, pace, chapter effort and breadth jointly shape the journey. Investment affects future credit, not already earned normalized fractions.

## Build policy

Section `[builds]`.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `partial_viability` | Number / `0.65` | `0.1..1`, weight | Semantic capability allocation for partial participation; not a direct numeric discount on live damage |
| `composition_safeguard` | Number / `0.75` | `0..1`, strength | Higher leaves less headroom for stacked multipliers. Zero relaxes this policy but does not disable validators. |

Section `[budget]`. All three shares **must sum to 1**, within `0.000001`. Changing one may require changing the others.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `equipment` | Number / `0.40` | `0.01..0.98`, share | Semantic equipment allocation; keeps its generated foundation |
| `nexus` | Number / `0.35` | `0.01..0.98`, share | Semantic Bonus/Nexus allocation |
| `skills` | Number / `0.25` | `0.01..0.98`, share | Semantic Skill allocation |

Shares are not percentages multiplied onto final attack or defense.

## Economy

Section `[economy]`.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `automation_pressure` | Number / `0.65` | `0..1`, strength | Response to cheap/scalable/unattended supply; affects extraction rather than rewriting opportunity facts |
| `bulk_resource_penalty` | Number / `0.60` | `0..1`, strength | Extra pressure on renewable/abundant resources; still applies when abundance-aware permits infinite-source yields |
| `conversion_loss_pressure` | Number / `0.10` | `0..0.95`, strength | Cross-Essence flexibility pressure; not one shared loss percent for every transaction |

Conservation remains active under every resource policy.

## Policies

Section `[policies]`. Types are quoted enum strings; accepted choices are shown in full.

| Key | Default | Allowed values | Meaning and interactions |
| --- | --- | --- | --- |
| `flight` | `"preserve_progression"` | `preserve_progression`, `match_pack`, `restrict` | Retain progression, adapt relative emphasis using supported capabilities, or reduce flight-oriented budget. Prerequisites remain. |
| `mining` | `"preserve_progression"` | `preserve_progression`, `match_pack`, `restrict` | Vein/area-mining capability emphasis; not merely tool speed. Hidden capabilities need evidence. |
| `resources` | `"balanced"` | `conservative`, `balanced`, `abundance_aware` | Balanced/conservative give zero dissolution to explicitly effectively infinite resources; abundance-aware can permit discounted output. |
| `outliers` | `"exclude_unsupported"` | `exclude_unsupported`, `winsorize`, `include_attainable` | Exclude unsupported extremes, cap their influence, or include supported attainable extremes. Disabled/creative exclusions still apply. |

## Diagnostics

Section `[diagnostics]`.

| Key | Type / default | Allowed / units | Meaning and interactions |
| --- | --- | --- | --- |
| `warning_confidence` | Number / `0.60` | `0..1`, confidence threshold | Highlights low-confidence evidence for review; not a power multiplier or compatibility guarantee |
| `expanded` | Boolean / `true` | `true` or `false` | Expanded generation evidence. False does not remove stable CSV/compact report facilities. |

## Latent Ore

Section `[latent_ore]`:

| Key | Type / default | Allowed | Meaning |
| --- | --- | --- | --- |
| `automatic_dimensions` | Boolean / `true` | `true` or `false` | Discover supported ordinary custom dimensions. Explicit dimension rules and vanilla distribution settings are separate. |

Each of `[latent_ore.overworld]`, `[latent_ore.nether]` and `[latent_ore.end]` accepts the following keys:

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `enabled` | Boolean / `true` | Boolean | Enables the distribution; explicit disabled dimension wins |
| `vein_size` | Integer / `9` | `1..64` blocks/vein attempt | Baseline size; ordinary automatic generation maintains a size-9 minimum for enabled nonzero supply |
| `veins_per_chunk` | Integer / `16` | `0..128` attempts/chunk | Baseline attempts; 0 stays off. Automatic nonzero supply tuning maintains at least 12 attempts. |
| `min_y` | Integer / `-48`, `16`, `0` respectively | `-2048..2048` Y coordinate | Must be at most max_y; target dimension height/support still matters |
| `max_y` | Integer / `64`, `112`, `80` respectively | `-2048..2048` Y coordinate | Must be at least min_y |
| `discard_chance_on_air_exposure` | Number / `0.0` | `0..1` probability | Chance to discard exposed placement |

These are baseline inputs. Final usable early Essence supply controls automatic tuning, especially weakest-category coverage. Scarce versus broad coverage can scale attempts; these are generation decisions, not guaranteed ores found per chunk.

### Exact dimensions

Use repeated `[[latent_ore.dimension]]` tables in the same policy file.

| Key | Type / default | Allowed | Meaning and interactions |
| --- | --- | --- | --- |
| `dimension` | String / required | Exact namespaced dimension ID | One rule per dimension; duplicate IDs fail |
| `hosts` | String array / `[]` | At most 32 distinct exact namespaced block IDs; no tags | Empty/omitted uses automatic supported host discovery; explicit hosts replace selection |
| `enabled` | Boolean / `true` | Boolean | Explicit exclusion/inclusion; false always disables this rule |
| Distribution keys above | Same types/bounds; omitted inherit selected distribution | Same bounds | Setting any distribution key other than enabled makes an exact distribution owner. Vanilla dimensions inherit their base table; ordinary custom dimensions use Overworld abundance over usable heights. |

Exact dimension distributions and exact runtime worldgen overrides bypass automatic tuning/floors. Adding hosts does not prove an unsupported generator produces those blocks. Host discovery reads supported terrain data and does not scan/generate chunks. Rebuilding does not populate existing chunks retroactively.

## Valid policy example

This standalone input requests modestly stronger added contributions, cheaper investment and faster seals. It is an illustrative policy, not a tested pack outcome. In an existing file **edit the existing tables** instead of appending duplicate tables.

```toml
schema_version = 1

[power]
overall = 1.1 # Supported range 0.1..4; final values remain validated.

[progression]
cost_pressure = 0.9 # Affordability; does not make false resource facts true.

[attunement]
pace = 1.25 # Faster activity credit; does not directly change prices.

[budget]
equipment = 0.40
nexus = 0.35
skills = 0.25 # The three shares sum to 1.

[[latent_ore.dimension]]
dimension = "minecraft:the_end"
enabled = false # Explicitly omit Latent Ore from this dimension.
```

Review [worked scenarios](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios) and back up before rebuilding.

Source: [bundled policy](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/resources/balance/essence_ascendance.toml), [key/default/range validation](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/config/BalanceSettings.java), [Attunement policy](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/config/AttunementPolicy.java), [ore parser](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/config/LatentOreSettingsParser.java).
