# Configuration for pack makers

[First setup](https://github.com/mistaboom/essence_ascendance/wiki/Installation-and-Server-Setup) Â· [Tuning examples](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios) Â· [Applying changes](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation)

## Files, scope and timing

Let the mod finish generating balance in a fresh test world before tuning it. The Skill and Bonus files will contain entries based on your pack, with comments describing units and limits. Paths below are relative to the server installation; in single-player, use that instance's `config` folder.

| File | Edit it forâ€¦ |
| --- | --- |
| `config/essence_ascendance.toml` | Overall power, generated costs, Ascension pace, ore generation and diagnostics |
| `config/essence_ascendance/skill_settings.toml` | Each Skill's first unlock tier, rank count and first/final effects |
| `config/essence_ascendance/bonus_settings.toml` | Each Bonus's first/completion tier and minimum/maximum effect |
| `config/essence_ascendance/balance_overrides.toml` | Advanced corrections for specific items, equipment, enemies or recipes |
| `config/essence_ascendance/generated_balance.json.gz` | Saved result used by the server; **do not edit** |
| `config/essence_ascendance/reports/` and `diagnostics/` | Inspection and troubleshooting; editing them does not change gameplay |

**After editing any balance input, run `/essence admin balance rebuild` as an operator.** Restarting, reloading datapacks, `/essence admin mappings reload` and exporting reports do not recalculate an existing saved balance. `/essence admin config` displays information; beta.9 has no `config reload` subcommand.

Back up the complete configuration and world first, and try substantial changes in a copy. Check for a successful rebuild, then inspect the Archive/Nexus and relevant reports. An invalid setting produces an error; an existing valid balance stays active if the replacement fails before installation.

The main config and advanced overrides receive defaults during setup. Skill and Bonus defaults are written after valid balance is installed; a missing file can also be recreated from existing saved balance during startup. **Existing files are preserved.** To request new automatic values, remove the relevant controls or entries and explicitly rebuild; see [automatic settings versus saved choices](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation#automatic-settings-versus-saved-choices).

## Common adjustments

| You want toâ€¦ | Change |
| --- | --- |
| Make one Skill stronger/weaker | Its `[[skill.stat]]` values in `skill_settings.toml` |
| Move a Skill earlier/later | Its `tier`, while respecting prerequisites |
| Change a Skill's number of ranks | Its `ranks`, with enough improvement between ranks |
| Buff/nerf one Bonus | Its `bonus_at_max` and, if needed, `minimum_bonus_at_lowest` |
| Make a Bonus start or finish earlier | Its `min_tier` / `max_tier` |
| Request stronger automatic effects overall | `[power]`; existing explicit Skill/Bonus values remain set |
| Make generated investments cheaper | `[progression] cost_pressure` |
| Complete Ascension seals faster | `[attunement] pace` |
| Change Latent Ore placement | `[latent_ore]` and its dimension settings |

Edit the existing entry or table instead of adding a duplicate. Use the IDs and stat paths already written in your generated files. Keep quotes around IDs, and keep `schema_version = 1` at the top of each file.

## Skill settings

Find the Skill's `[[skill]]` entry in `config/essence_ascendance/skill_settings.toml`. All following `[[skill.stat]]` blocks belong to it, until the next `[[skill]]` entry.

| Setting | Meaning |
| --- | --- |
| `id` | The Skill's identifier; keep the generated value |
| `tier` | First unlock tier; use a full ID such as `"essence_ascendance:awakened"` |
| `ranks` | Number of purchasable ranks, from 1 to 64, subject to the Skill's limits |
| `path` inside `[[skill.stat]]` | The listed effect to adjust; keep the generated path |
| `at_first_rank` | Effect value at rank one |
| `at_max_rank` | Effect value at the last rank |

Unlock choices are Dormant, Awakened, Resonant, Ascendant and Transcendent, written as full lowercase IDs. Moving the first unlock does not remove prerequisite Skills, later-rank tier requirements, milestones or exclusive choices.

The mod generates the ranks between the first and final values. Every extra rank must improve something meaningfully. An on/off Skill must stay at one rank; for any configured one-rank stat, first and maximum values must be equal. Setting only `ranks = 1`, without stat overrides, uses the automatic final effects in that single rank.

**Units matter.** The generated comments describe them. `Percent` uses percentage points; `Chance`/`Fraction` uses fractions (`0.1` means 10%); `Ticks` uses twenty per second; `Blocks` uses blocks. Multipliers use factors. A cooldown can improve by getting smaller.

You can set effects above the automatic soft power budget, but supported effect limits and prerequisites still apply. Changing effects alone preserves prices when rank count stays the same. Changing the number of ranks generates a new cost progression. Review the installed rows in `reports/skill_rank_parameters.csv` as well as the in-game Skill.

For a partial edit, omit controls you want generated automatically. Removing a whole Skill entry restores its default unlock tier and automatic ranks/effects. [Skill example](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios#moving-a-skill-or-changing-its-ranks).

## Bonus settings

Find the Bonus's `[[bonus]]` entry in `config/essence_ascendance/bonus_settings.toml`.

| Setting | Meaning |
| --- | --- |
| `id` | The Bonus's identifier; keep the generated value |
| `min_tier` | First active player tier |
| `max_tier` | Completion player tier, at or after the starting tier |
| `bonus_at_max` | Exact effect at completion |
| `minimum_bonus_at_lowest` | Minimum effect at the first active completed tier; the result can be higher |

Keep all four controls in a configured Bonus entry. Tiers use full IDs, from `"essence_ascendance:dormant"` through `"essence_ascendance:transcendent"`. A Bonus can finish before Transcendent; the earned completed benefit is retained at higher tiers.

Read the entry's unit comment. Melee, ranged and magic damage Bonuses use added damage points. Other Bonuses may use percent, seconds, blocks or another stated unit. The first minimum must be positive and no larger than the maximum. The file accepts a maximum up to 1,000,000, but each effect can have a much stricter gameplay limit.

The mod fills in intermediate progression and requires useful completed-tier improvements. A long tier interval with a tiny maximum may be impossible. If your request fails validation, adjust the interval or effect values described in the error rather than expecting the mod to move your chosen tiers silently.

To return one Bonus to automatic generation, remove its entire entry and rebuild. Compare the installed result in the tooltip and `reports/bonus_tracks.csv`. [Bonus example](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios#making-a-bonus-finish-earlier).

## Main configuration reference

The remaining sections describe `config/essence_ascendance.toml`. Most packs can start with its defaults and tune a few controls. Values below are input defaults, not guaranteed in-game outputs. Omitted keys use defaults; misspelled/unknown keys are errors.

Use ordinary numbers, quoted strings and the existing table layout. Whole-number settings must use integers. Avoid duplicate tables, inline tables and unfamiliar TOML syntax; the mod accepts only its documented subset. The separate membership JVM option below follows its own startup procedure.

## Membership authority: server JVM property

Most packs can leave team-provider selection on its default, `auto`. To select it explicitly, add one JVM argument in your server launch settings. This is separate from the balance TOML files and requires a server restart.

| Property / location | Type / default | Valid values / units | Effect and timing |
| --- | --- | --- | --- |
| `essence.membershipAuthority`, server JVM arguments | String / `auto` | `auto`, `native`, `ftb_teams` | On a fresh world, `auto` prefers supported FTB Teams, otherwise native teams. Existing worlds retain their saved provider; changing the launch option alone does not migrate them. |

Use one of these choices, not both. Place it with the JVM options before the server's jar/main-class argument:

```text
-Dessence.membershipAuthority=auto
-Dessence.membershipAuthority=ftb_teams
```

If you intentionally change an existing world's provider, follow [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#selection-and-established-worlds) first. Missing or unsupported providers can pause access rather than treating every player as solo. A balance rebuild does not switch the team provider.


## Root

| Key | Type / default | Allowed | Meaning |
| --- | --- | --- | --- |
| `schema_version` | Integer / `1` | Exactly `1` | File format version; leave it at 1. |

## Power

Section `[power]`. Guides automatic added power relative to measured pack equipment. It is not a direct multiplier on every stat.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `overall` | Number / `1.0` | `0.1..4`, ratio | Overall automatic added-power target |
| `early` | Number / `0.8` | `0.1..4`, ratio | Early progression emphasis |
| `mid` | Number / `0.9` | `0.1..4`, ratio | Mid progression emphasis |
| `late` | Number / `1.0` | `0.1..4`, ratio | Late progression emphasis |
| `apex` | Number / `1.1` | `0.1..4`, ratio | Endgame emphasis; lower values request less added power |

Early emphasis can be higher than late emphasis. Equipment keeps its pack-relative foundation; these settings do not reduce its stats to make room for every possible Skill or Bonus. [Power budgets](https://github.com/mistaboom/essence_ascendance/wiki/Progression-and-Power).

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

These are advanced assumptions. Start with the default values unless you have a specific reason to change them.

## Attunement

Section `[attunement]`.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `pace` | Number / `1.0` | `0.1..10`, speed ratio | Higher completes seals faster; no direct Essence-price change |
| `maximum_acceleration` | Number / `1.0` | `0..4`, added multiplier | Extra activity speed from category development; default allows up to 2× base credit. No investment still earns base credit. |
| `repetition_floor` | Number / `0.15` | `0.01..1`, fraction | Minimum efficiency of legitimate repeated sources; interacts with recent source exposure |
| `variety_strength` | Number / `0.25` | `0..1`, extra fraction | Bounded variety benefit; changing activity is rewarded, not mandatory |
| `history_window` | Integer / `64` | `8..256` reference outcomes | How much recent activity affects repetition and variety; measures work rather than individual clicks |
| `early_effort_fraction` | Number / `0.75` | `0.1..1`, effort fraction | First powered chapter effort, easing toward 1 by final chapter |
| `onboarding_effort_fraction` | Number / `1.0` | `0.1..1`, effort fraction | Automatic Latent onboarding; full entry effort by default |
| `breadth_exponent` | Number / `1.0` | `0.25..4`, exponent | Shape of required category breadth; smaller asks broader early participation. Default final progression leaves one category optional. |

These settings work together. Investment affects future credit rather than removing already earned seal progress.

## Build policy

Section `[builds]`.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `partial_viability` | Number / `0.65` | `0.1..1`, weight | Helps automatic generation support builds using only part of the mod; not a direct damage multiplier |
| `composition_safeguard` | Number / `0.75` | `0..1`, strength | Higher requests more caution when combining multipliers. Zero relaxes that planning rule but retains validity checks. |

Section `[budget]`. All three shares **must sum to 1**, within `0.000001`. Changing one may require changing the others.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `equipment` | Number / `0.40` | `0.01..0.98`, share | Equipment share of automatic planning; equipment keeps its generated foundation |
| `nexus` | Number / `0.35` | `0.01..0.98`, share | Bonus/Nexus share of automatic planning |
| `skills` | Number / `0.25` | `0.01..0.98`, share | Skill share of automatic planning |

These shares are planning weights, not percentages multiplied onto attack or defense. For precise Skill/Bonus changes, use their own settings files.

## Economy

Section `[economy]`.

| Key | Type / default | Range / units | Meaning and interactions |
| --- | --- | --- | --- |
| `conversion_loss_pressure` | Number / `0.10` | `0..0.95`, strength | Guides conversion losses between Essences; not a fixed loss percentage for every transaction |

Recognized complete recipes can limit payouts. The generator does not estimate acquisition difficulty, farm rates or automation costs.

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
cost_pressure = 0.9 # Cheaper generated investments; does not change item yields.

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
