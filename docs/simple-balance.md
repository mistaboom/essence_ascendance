# Simple balance generation

This development revision requires fresh Essence Ascendance saves, worlds and configs. It does not migrate previous generated profiles or old item and machine metadata.

Values are estimates from shared item properties, common tags and published recipes. The engine performs a bounded recipe pass, handles output quantities and returned containers, and reconciles reversible material conversions. Complete static recipes constrain final payouts. Opaque recipe displays may inform estimates, but do not prove acquisition, fuel use, processing time or automation costs.

Unknown acquisition no longer means zero value. There is no quest traversal, loot sampling, demand scoring, mod-specific valuation adapter or bespoke script inspection. Recipes exposed through the ordinary Minecraft interfaces participate regardless of which mod or datapack created them. JEI and functional FTB Teams integrations remain.

Equipment calibration uses measured default attributes grouped by relative strength. Those groups are estimates, not claims about when a player can obtain the equipment. Purchase effort and material activity use a stable common value unit, so adding decorative variants does not change every price. Latent Ore uses configured distributions without inferred source abundance.

## Pack configuration

The files below are in the server's `config` directory. Edit them and run `/essence admin balance rebuild`. Normal startup loads the saved generated profile. An invalid rebuild retains the active profile and reports the problem.

| File | Purpose |
| --- | --- |
| `essence_ascendance.toml` | Overall power, progression effort, Attunement and ore settings |
| `essence_ascendance/skill_layout.toml` | Skill unlock tiers, initially the authored layout |
| `essence_ascendance/bonus_progression.toml` | Generated Bonus endpoints for pack authors to adjust |
| `essence_ascendance/balance_overrides.toml` | Advanced generic factual corrections and exact values |

Skill ranks and effect magnitudes remain generated. Changing a skill's tier does not replace its prerequisites; invalid prerequisite ordering is rejected.

The Bonus file is created after the first successful profile installation and is never overwritten. Every available Bonus has four controls: `min_tier`, `max_tier`, `bonus_at_max`, and `minimum_bonus_at_lowest`. A Bonus may complete before Transcendent. Omitting an entry lets the generator choose it again.

Minimum meaningful effects still apply even when they exceed soft power budgets. Native limits, such as actual cooldown ticks and shield caps, remain mechanical limits. An impossible configured interval produces an error rather than silently moving its endpoints or reducing its minimum.

Reports under `config/essence_ascendance/reports` show the installed values. They are inspection files, not additional configuration. Editing the human configuration does not change existing reports until a successful explicit rebuild.
