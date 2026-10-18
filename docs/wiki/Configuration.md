# Configuration reference

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Overrides](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization) · [Worked examples](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios)

## Files, scope and timing

| File | Edit? | Purpose |
| --- | --- | --- |
| `config/essence_ascendance.toml` | **Yes** | Policy settings on this page |
| `config/essence_ascendance/skill_settings.toml` | **Yes** | Skill unlock tiers, rank counts and native stat endpoints |
| `config/essence_ascendance/bonus_settings.toml` | **Yes** | Bonus active tiers and effect bounds |
| `config/essence_ascendance/balance_overrides.toml` | **Yes** | [Facts and exact overrides](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization) |
| `config/essence_ascendance/generated_balance.json.gz` | **No** | Validated saved server authority |
| `config/essence_ascendance/reports/` and `diagnostics/` | **No, as inputs** | Inspect decisions; edits do not tune gameplay |

**Global policy settings below belong in `config/essence_ascendance.toml`. Skill and Bonus settings have their own files, described next. All apply on generation or explicit `/essence admin balance rebuild`.** Restart, mappings reload of an existing profile and datapack reload do not apply edited policy to saved balance. The separately documented membership JVM property follows its own server/world lifecycle.

Defaults below are **input defaults**, not fixed gameplay values. Omitted keys use defaults. Numeric ranges are inclusive and require finite numbers. “Number” accepts a decimal or integer; “integer” requires a whole integer token. Ratios, shares and exponents are dimensionless unless stated.

Each input is limited to 4,000,000 bytes. Unknown keys/tables, wrong types and invalid combinations reject generation with file/line information. Supported TOML uses named tables, decimal numbers, booleans, quoted strings and the documented arrays; inline tables, dates, hex numbers, multiline strings and dotted bare keys are unsupported.

## Skill settings

Edit `config/essence_ascendance/skill_settings.toml`. The generated file lists every Skill and each applicable native parameter. Use its exact identifiers and parameter paths.

| Table / key | Type | Meaning |
| --- | --- | --- |
| Root `schema_version` | Integer `1` | Input format |
| `[[skill]] id` | Namespaced Skill ID | Registered Skill to configure |
| `tier` | Optional powered tier ID | Required player tier; Dormant through Transcendent; omitted uses authored placement |
| `ranks` | Optional integer `1..64` | Purchasable rank count; omitted remains automatic; binary Skills require one |
| `[[skill.stat]] path` | Native parameter path | One of the parameter paths emitted for this Skill |
| `at_first_rank` | Finite number | Native value at rank one |
| `at_max_rank` | Finite number | Native value at the final rank |

Parameters retain their own units and limits. A chance can be a fraction while a displayed Bonus uses percent; ticks, seconds, distances and damage are also distinct. Read the generated comments. A cooldown can improve by decreasing. Every extra rank must still make a meaningful native improvement. A configured one-rank stat needs equal first and maximum endpoints. Setting only `ranks = 1`, with no stat overrides, puts the automatic final values into that single rank.

Configured endpoints apply after automatic allocation and may exceed its soft power budgets. Changing stats alone preserves generated prices; changing the rank count regenerates that Skill's cost progression. Unchanged generated settings preserve the exact resolved intermediate ranks; custom counts or endpoints interpolate and then face native validation. Tier edits do not remove prerequisites, milestone requirements, replacements or exclusive choices. Invalid tier ordering, impossible rank counts, unsupported parameters or native limit violations reject the candidate.

Absent entries use authored unlock tiers and generated ranks/effects. Missing Skill and Bonus files are written only after a validated profile is durably published. Existing files are never overwritten. Remove an entry to request its automatic values on the next rebuild, or preserve then remove a whole system file to request complete new defaults.

## Bonus settings

Edit `config/essence_ascendance/bonus_settings.toml`. Root `schema_version = 1` is followed by repeated `[[bonus]]` entries.

| Key | Type / bounds | Meaning |
| --- | --- | --- |
| `id` | Namespaced Bonus ID | Registered Bonus |
| `min_tier` | Powered tier ID | First active player tier |
| `max_tier` | Powered tier ID, not before min_tier | Completion tier; it can be before Transcendent |
| `bonus_at_max` | Finite number, `0 < value <= 1000000` | Exact completion effect in the Bonus's displayed unit |
| `minimum_bonus_at_lowest` | Finite number, positive and at most bonus_at_max | Lower bound for the first active effect |

All four controls are required in a configured entry. Native minima, useful caps and meaningful completed-tier improvements can impose stricter limits. Impossible intervals reject the rebuild. Omitting a Bonus lets the generator select its active span and effects.

Melee, ranged and magic damage Bonuses use added damage points. Other Bonus units are identified in generated comments and `bonus_tracks.csv`; do not assume every value is a percentage. Funding is continuous between completed endpoints. Eligible intact equipment channels the player's earned Bonus independently of equipment tier.

These filenames and schemas replace earlier development inputs without migration. Start with fresh defaults for this revision, as described in [the current model](https://github.com/mistaboom/essence_ascendance/blob/main/docs/simple-balance.md).

## Membership authority: server JVM property

**Added in beta.5.** This is a JVM system property, not a balance TOML section/key. No separate teams configuration file or policy switch is registered.

| Property / location | Type / default | Valid values / units | Effect and timing |
| --- | --- | --- | --- |
| `essence.membershipAuthority`, server JVM arguments | String / `auto` | `auto`, `native`, `ftb_teams`; case-insensitive enum; no units | Read when the Overworld membership session starts. AUTO initially prefers supported FTB Teams, then eligible native; afterward it retains saved world authority. A different explicit selection pauses access pending reviewed activation. Restart to apply an edited launch argument; datapack/profile reload does not change authority. |

Place the option with the server's JVM arguments **before** its jar/main-class launch target. Valid launch-option examples:

```text
-Dessence.membershipAuthority=auto
-Dessence.membershipAuthority=ftb_teams
```

Use one selection. Invalid strings block selection with a diagnostic; they do not fall back silently. Missing/unsupported/unready external membership is not permission to treat everyone as solo. Accepted provider dependencies, preparation/activation/cancellation and ordinary roster settlement are canonical in [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#selection-and-established-worlds). This setting changes membership selection, not balance generation or saved personal receiving preferences.

Current source: `team/MembershipRuntime.java` and `team/MembershipAuthority.java` under `common/src/main/java/com/mistaboom/essence_ascendance/`. Balance-generation checks do not establish multiplayer release coverage.

## Root

| Key | Type / default | Allowed | Meaning |
| --- | --- | --- | --- |
| `schema_version` | Integer / `1` | Exactly `1` | Input grammar version; also defaults to 1 when omitted. This is different from generated document schema 2. |

## Power

Section `[power]`. Controls position/headroom relative to measured catalog references, not a universal multiplier of the strongest registered item.

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
| `cost_pressure` | Number / `1.0` | `0.1..10`, ratio | Affordability pressure on generated investments; lower requests cheaper costs. Does not correct item-value estimates. |

Use `attunement.pace` for contribution-speed changes without requesting different Essence costs.

## Generation assumptions

Section `[generation]`. These are explicit model assumptions, not measured encounter times.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `routine_seconds` | Number / `4.0` | `0.1..300` seconds | Routine enemy health/encounter window informs DPS pressure |
| `boss_seconds` | Number / `40.0` | `0.1..3600` seconds | Boss encounter window |
| `survival_seconds` | Number / `10.0` | `0.1..300` seconds | Sustained survival/recovery window |
| `entry_resource_effort` | Number / `80.0` | `1..100000`, effort ratio | Entry resource-relative cost baseline, using the stable common material value unit |
| `effort_growth` | Number / `3.5` | `1..20`, growth factor | Progression-band cost growth; combines with length and cost pressure |

Review combat assumptions and resolved cost reports when adjusting these.

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
| `onboarding_effort_fraction` | Number / `1.0` | `0.1..1`, effort fraction | Automatic Latent onboarding; full entry effort by default |
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
| `conversion_loss_pressure` | Number / `0.10` | `0..0.95`, strength | Cross-Essence flexibility pressure; not one shared loss percent for every transaction |

Complete static material recipes constrain final payouts; acquisition, farm rates and automation are not inferred.

## Policies

Section `[policies]`. Types are quoted enum strings; accepted choices are shown in full.

| Key | Default | Allowed values | Meaning and interactions |
| --- | --- | --- | --- |
| `flight` | `"preserve_progression"` | `preserve_progression`, `restrict` | Retain milestone progression or reduce flight-oriented budget. Prerequisites remain. |
| `mining` | `"preserve_progression"` | `preserve_progression`, `restrict` | Vein/area-mining capability emphasis; native tool properties supply reference measurements. |
| `outliers` | `"exclude_unsupported"` | `exclude_unsupported`, `winsorize`, `include_attainable` | Exclude statistical extremes, cap their influence, or include measured catalog extremes. These choices do not prove survival access. Disabled/creative exclusions still apply. |

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

These are configured placement baselines with explicit minimums for enabled nonzero automatic distributions. Acquisition and category scarcity are not evaluated. Attempts are not a guarantee of ores found in a chunk.

### Exact dimensions

Use repeated `[[latent_ore.dimension]]` tables in the same policy file.

| Key | Type / default | Allowed | Meaning and interactions |
| --- | --- | --- | --- |
| `dimension` | String / required | Exact namespaced dimension ID | One rule per dimension; duplicate IDs fail |
| `hosts` | String array / `[]` | At most 32 distinct exact namespaced block IDs; no tags | Empty/omitted uses automatic supported host discovery; explicit hosts replace selection |
| `enabled` | Boolean / `true` | Boolean | Explicit exclusion/inclusion; false always disables this rule |
| Distribution keys above | Same types/bounds; omitted inherit selected distribution | Same bounds | Setting any distribution key other than enabled makes an exact distribution owner. Vanilla dimensions inherit their base table; ordinary custom dimensions use Overworld abundance over usable heights. |

Exact dimension distributions and exact runtime worldgen overrides bypass automatic distribution floors. Adding hosts does not prove an unsupported generator produces those blocks. Host discovery reads supported terrain data and does not scan/generate chunks. Rebuilding does not populate existing chunks retroactively.

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

Current source: `balance/config/BalanceSettings.java`, `SkillSettingsConfig.java`, `BonusSettingsConfig.java`, `AttunementPolicy.java` and `LatentOreSettingsParser.java` under `common/src/main/java/com/mistaboom/essence_ascendance/`. Bundled global defaults are in `common/src/main/resources/balance/essence_ascendance.toml`.
