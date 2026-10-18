# Settings and equipment follow-up — 2026-10-17

This follows the earlier grooming checkpoint. No release or remote publication is part of this pass.

## Behavior

- Latent equipment uses an iron-era foundation: sword 6 damage at 1.6 speed, armor 15 with no toughness, mining speed 6, harvest level 2 and tool durability 250. Shields and fishing rods retain their corresponding native starting durability. Higher tiers retain their relative curve toward the measured pack endpoint, floored at the starting value.
- The caster follows the corresponding bow with less damage, faster firing and shorter range. Native rounding applies; Latent resolves to 4 damage at 1.7 shots/second, versus the bow's 6 at 1. Its range is 20 blocks versus 160.
- JEI's creative-order comparison resolves equal gear-family anchors by tier. Recipe subtype distinctions remain intact; earlier user-selected sort stages retain precedence.
- Maximum Ascendance displays one completed tier and a completion tooltip, with no duplicate destination or instruction to earn another tier.
- Shared allocation no longer rejects a safe nominal contribution simply because it is below 2% of an apex request. Actual native meaningful floors still apply at publication. Recovering a Bonus after zero quantization restores its purchase checkpoints before evaluating its power.

## Settings and reports

`config/essence_ascendance.toml` holds global policy. `config/essence_ascendance/skill_settings.toml` holds Skill unlock tiers, ranks and first/maximum native stat values. `bonus_settings.toml` holds Bonus active tiers and effect bounds; `balance_overrides.toml` remains for advanced exact overrides and generic facts.

Skill and Bonus settings are emitted only after validated durable publication. Existing files are preserved. Edit and explicitly rebuild to apply changes; normal restart reuses saved balance. Omitted entries or controls retain automatic generation and authored Skill placement. Native limits and prerequisites remain enforced. The old filenames and schemas are not migrated or read.

Current documentation, report descriptions, diagnostic provenance and verification helpers now use these roles. Reports distinguish item estimates from spendable payouts and mark acquisition, renewable supply and automation as unexamined. Historical release and verification receipts remain historical.

## Verification

- Skill settings: 938 checks, including exact replay of every generated rank, custom ranks/endpoints, invalid controls and human-file preservation. Bonus settings: 239 checks. Global settings, report layout/content and failed-generation evidence also passed.
- Runtime generation, native equipment headroom, pack combat scaling and low/high-budget adversarial profiles passed. Equipment catalog, creative variants, JEI ore handling, fishing, completed Ascendance and screen-composition checks passed.
- Both loaders passed initialization-only native gameplay fixtures. NeoForge with JEI 19.57 passed the actual transformed comparator for every gear family; Fabric without JEI correctly skipped that optional probe. These are not rendered-screen screenshots or a full in-world JEI index audit.
- Production builds passed asset verification: 349 runtime assets per loader. The glaive authoring file remains unchanged and excluded from runtime assets. No compiled test classes or retired config classes/resources appear in the production jars.
- Isolated vanilla and normal ATM10 each passed fresh generation, one explicit rebuild, all installed Skill-rank/Bonus readouts, Skill effect checks and saved-profile restart. Rebuilds reproduced evidence, economy, runtime, Skills and validation exactly. All four human settings files remained unchanged after publication. Restart ran no generation or report export and preserved the saved files.

| Fixture | Resources | Skills | Ranks | Bonus tracks | Bonus tier rows | Active endpoints |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Vanilla | 1,362 | 90 | 230 | 41 | 246 | 118 |
| ATM10 | 64,342 | 90 | 227 | 41 | 246 | 125 |

Actual emitted settings and parameter reports matched both saved profiles. Pack defaults differ: ATM10 has three different Skill rank counts, fifteen different Skill endpoint sets, twelve different Bonus maxima and seven different Bonus spans. These observations establish functioning generic generation, not an exhaustive balance endorsement of every pack item or native gameplay activation of every rank.

Detailed logs, profiles, preserved inputs and artifact receipts are under `Essence Ascendance Auxiliary Files/grooming/20261017/settings-followup`. Prism installations were not modified.
