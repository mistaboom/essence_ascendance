# Simple balance generation

This development revision requires fresh Essence Ascendance saves, worlds and configs. It does not migrate previous generated profiles or old item and machine metadata.

Values are estimates from shared item properties, common tags and published recipes. The engine performs a bounded recipe pass, handles output quantities and returned containers, and reconciles reversible material conversions. Complete static recipes constrain final payouts. Opaque recipe displays may inform estimates, but do not prove acquisition, fuel use, processing time or automation costs.

Exact reversible conversions also recognize tagged ingredients when the same concrete item can fill every input slot. Only item pairs with an exact reverse recipe share a denomination; unrelated alternatives remain separate, and mixed ingredient combinations are not enumerated.

Ore material estimates use a bounded native mining requirement and hardness factor. Matching common ore, raw-material, ingot, nugget, gem and dust tags share the cheapest ore variant's factor. Common dense/sparse ore-yield bands adjust this estimate by one quarter/four times; these are deliberately coarse policy weights, not measured drop counts or world rarity. Missing metadata retains the generic estimate. Exact material conversion ratios still come only from recipes.

Declared item rarity supplies stronger minimum estimates before recipe solving, and an explicit default glint component adds a bounded premium. This helps rare ingredients and their crafted products without a list of special items. These signals do not establish an item's gameplay function: an otherwise unclassified rare item can still supply Utility. Items with identical available metadata can remain tied, and rare items marked Common can remain undervalued.

Loader-added rarities retain the item's ordinary property and recipe estimate with no assumed rarity premium. Their names and enum order do not imply value; diagnostics record the neutral policy so an unfamiliar rarity cannot stop the entire profile from generating.

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

Damage Bonuses use additional damage points, not percentages. The amount is added to the physical melee, ranged or caster damage before ordinary attack scaling, skill amplification and mitigation. A fully charged, unmitigated attack receives the same additional damage on a weak eligible tool as on a strong eligible weapon. Partial funding remains continuous; the meaningful minimum applies to each completed active tier. Lower-tier equipment still limits the active Bonus tier.

Automatic Bonus placement uses a shared increasing impact allowance and a shared growth curve. Health, damage and prevention use their gameplay baselines to measure relative impact; essence category does not decide placement. The same applicability breadth used in pricing also weights placement: one divided by one plus the number of declared conditions. This is a coarse balance policy for conditional benefits, not a measured activation probability. Recovery rates are compared over the configured survival window, assuming missing health throughout that window; this measures potential recovery, not guaranteed uptime. The first reward satisfies its native minimum, and the remaining value is spread across the remaining tiers with geometric weights instead of collecting at the final tier. Each completed tier must improve the actual mechanic on the same equipment and under the same conditions as its previous endpoint. Equipment upgrades cannot supply that improvement. Useful caps can end a track before Transcendent; there is no final-tier anchor and no item or mod-specific placement table.

Bonus tooltips list tiers from Transcendent down to Dormant. Tooltip and Archive tier rows show `Max reached` after a Bonus completes. The earned benefit remains active at higher tiers. Existing Bonus configuration entries retain their explicit start and stop tiers; remove an entry or regenerate the file to request new automatic boundaries.

The damage-unit change requires fresh generated balance and Bonus defaults. Preserve any desired human edits separately, remove the previous `bonus_progression.toml` to request new automatic defaults, and explicitly rebuild balance. Existing files are never silently converted from percentage values to damage points. Use a fresh test world when comparing purchase progression from the earlier percentage-based revision.

Minimum meaningful effects still apply even when they exceed soft power budgets. Native limits, such as actual cooldown ticks and shield caps, remain mechanical limits. An impossible configured interval produces an error rather than silently moving its endpoints or reducing its minimum.

Reports under `config/essence_ascendance/reports` show the installed values. They are inspection files, not additional configuration. Editing the human configuration does not change existing reports until a successful explicit rebuild.
