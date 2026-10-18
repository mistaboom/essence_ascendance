# Simple balance generation

This development revision requires fresh Essence Ascendance saves, worlds and configs. It does not migrate previous generated profiles or old item and machine metadata.

Values are estimates from shared item properties, common tags and published recipes. The engine performs a bounded recipe pass, handles output quantities and returned containers, and reconciles reversible material conversions. Complete static recipes constrain final payouts. Opaque recipe displays may inform estimates, but do not prove acquisition, fuel use, processing time or automation costs.

Exact reversible conversions also recognize tagged ingredients when the same concrete item can fill every input slot. Only item pairs with an exact reverse recipe share a denomination; unrelated alternatives remain separate, and mixed ingredient combinations are not enumerated.

A complete construction bill takes precedence over an incomplete recipe display for the same product. Complete disassembly quantities can also estimate the material contained in a refinement whose construction recipe is opaque. This estimates recoverable content, not the cost or wear of an unknown catalyst. These estimates follow cheaper inputs during the existing bounded propagation pass. Final dissolution payouts have their own conservation pass; an advisory economic estimate is not necessarily the amount a crucible pays.

Ore material estimates use a bounded native mining requirement and hardness factor. Matching common ore, raw-material, ingot, nugget, gem and dust tags share the cheapest ore variant's factor. Common dense/sparse ore-yield bands adjust this estimate by one quarter/four times; these are deliberately coarse policy weights, not measured drop counts or world rarity. Missing metadata retains the generic estimate. Exact material conversion ratios still come only from recipes.

Declared item rarity supplies stronger minimum estimates before recipe solving, and an explicit default glint component adds a bounded premium. This helps rare ingredients and their crafted products without a list of special items. These signals do not establish an item's gameplay function: an otherwise unclassified rare item can still supply Utility. Items with identical available metadata can remain tied, and rare items marked Common can remain undervalued.

Loader-added rarities retain the item's ordinary property and recipe estimate with no assumed rarity premium. Their names and enum order do not imply value; diagnostics record the neutral policy so an unfamiliar rarity cannot stop the entire profile from generating.

Unknown acquisition no longer means zero value. There is no quest traversal, loot sampling, demand scoring, mod-specific valuation adapter or bespoke script inspection. Recipes exposed through the ordinary Minecraft interfaces participate regardless of which mod or datapack created them. JEI and functional FTB Teams integrations remain.

Equipment calibration uses measured default attributes grouped by relative strength. Those groups are estimates, not claims about when a player can obtain the equipment. Purchase effort and material activity use a stable common value unit, so adding decorative variants does not change every price. Latent Ore uses configured distributions without inferred source abundance.

Mining references include native Tool rule speeds as well as the representative block probes. This prevents a tool specialized for leaves or other unprobed blocks from appearing to have only empty-hand speed. The value is a declared block-specific peak, not a measured universal rate; actual harvesting still obeys the equipped tool's rules and block tags.

Combat and mining strength are ranked separately. Native weapon classes and functional sword/melee-weapon tags distinguish weapons from mining tools; a tool is a melee reference only when no usable weapon reference exists. A weapon's default mining speed cannot make it entry-level combat gear, and a mining tool's attack damage does not place its mining band. Outlier bounds use distinct measurements across the compatible equipment slot before strength bands are assigned, so repeated variants do not vote and an extreme item cannot validate itself by landing alone in the highest band. With at least five distinct measurements the bound also allows the upper quartile plus three interquartile ranges, preserving ordinary wide material ladders such as durability; smaller samples retain the median-relative guard. This is a coarse extreme-outlier policy, not proof that every retained item is attainable. Each weapon family retains its own measured damage and cadence.

## Pack configuration

The files below are in the server's `config` directory. Edit them and run `/essence admin balance rebuild`. Normal startup loads the saved generated profile. An invalid rebuild retains the active profile and reports the problem.

| File | Purpose |
| --- | --- |
| `essence_ascendance.toml` | Overall power, progression effort, Attunement and ore settings |
| `essence_ascendance/skill_settings.toml` | Skill unlock tiers, rank counts and native stat first/maximum values |
| `essence_ascendance/bonus_settings.toml` | Bonus active tiers and effect bounds |
| `essence_ascendance/balance_overrides.toml` | Advanced generic factual corrections and exact values |

The main file retains global power, progression effort, activity and ore policy. The two system files expose the generated progression for direct editing; reports and saved JSON remain outputs.

The Skill file uses `[[skill]]` entries with `id`, `tier` and `ranks`, followed by each applicable `[[skill.stat]]` entry with `path`, `at_first_rank` and `at_max_rank`. Values use the parameter's native unit, which can differ from a displayed percentage. A decreasing cooldown improves as its number falls. Rank counts are 1–64; binary skills have one rank. Configured one-rank stat endpoints must be equal. Changing a tier does not remove prerequisites, choices or milestone requirements. Invalid ordering, nonmeaningful ranks or violations of native floors/caps reject the rebuild.

Absent entries use authored unlock tiers and generated ranks/effects. After the first validated profile is durably published, missing Skill and Bonus files receive complete generated defaults. Existing files are never overwritten. Replaying unchanged generated Skill settings preserves the resolved intermediate ranks; custom rank counts or endpoints interpolate and are validated. Configured Skill endpoints apply after automatic allocation and may exceed its soft power budgets. Costs remain generated.

Every available Bonus has four controls: `min_tier`, `max_tier`, `bonus_at_max`, and `minimum_bonus_at_lowest`. A Bonus may complete before Transcendent. Omitting an entry lets the generator choose it again.

Damage Bonuses use additional damage points, not percentages. The amount is added to the physical melee, ranged or caster damage before ordinary attack scaling, skill amplification and mitigation. A fully charged, unmitigated attack receives the same additional damage on a weak eligible tool as on a strong eligible weapon. Partial funding remains continuous; the meaningful minimum applies to each completed active player tier. Any intact, infused eligible equipment can channel the player's earned Bonus regardless of its own tier: a Transcendent player can use a Transcendent Bonus on Dormant gear. Equipment tier sets innate stats, while Latent or fractured equipment does not channel Bonuses.

Automatic flat-damage requests use the larger of their authored baseline and half the matching weapon family's measured apex hit, before the normal power controls and composition limits. The half-hit share is a common balance policy, not an acquisition estimate. Melee, ranged and caster requests recover available headroom independently, so a weak caster reference cannot cap melee. Percentages remain dimensionless: the same percentage already grants more actual damage with a stronger weapon. Shared flat skill procs scale only when all their supported weapon families exceed the authored ten-damage reference hit; a stronger melee weapon alone does not inflate a proc also usable with a weak bow or caster.

The generic scan includes standard armor health modifiers, combining compatible equipment slots with the native player base applied once. Armor, toughness and health retain native modifier operations and identities. The outfit search keeps a bounded, deterministic shortlist rather than enumerating every possible outfit; unusual cross-piece synergies can be missed. Existing outlier settings apply, and winsorized measurements are explicitly capped estimates. Flat health and recovery requests compare against measured raw health; armor-derived effective health is not counted again as raw health. The scan still cannot infer custom progression upgrades or callbacks. Pack authors can adjust the generated health Bonus endpoint for those systems.

Automatic Bonus placement uses a shared increasing impact allowance and a shared growth curve. Health, damage and prevention use their gameplay baselines to measure relative impact; essence category does not decide placement. The same applicability breadth used in pricing also weights placement: one divided by one plus the number of declared conditions. This is a coarse balance policy for conditional benefits, not a measured activation probability. Recovery rates are compared over the configured survival window, assuming missing health throughout that window; this measures potential recovery, not guaranteed uptime. The first reward satisfies its native minimum, and the remaining value is spread across the remaining tiers with geometric weights instead of collecting at the final tier. Each completed tier must improve the actual mechanic on the same equipment and under the same conditions as its previous endpoint. Equipment upgrades cannot supply that improvement. Useful caps can end a track before Transcendent; there is no final-tier anchor and no item or mod-specific placement table.

Bonus tooltips list tiers from Transcendent down to Dormant. Tooltip and Archive tier rows show `N/A` before a Bonus starts and after it completes. The earned benefit remains active at higher tiers. Existing Bonus configuration entries retain their explicit start and stop tiers; remove an entry or regenerate the file to request new automatic boundaries and amounts.

This configuration contract uses `skill_settings.toml` and `bonus_settings.toml`. Earlier filenames and schemas are not migrated. Begin with fresh defaults for this development revision; preserve historical inputs separately if needed for comparison. Removing a current entry requests automatic values for that entry on the next explicit rebuild.

Minimum meaningful effects still apply even when they exceed soft power budgets. Native limits, such as actual cooldown ticks and shield caps, remain mechanical limits. An impossible configured interval produces an error rather than silently moving its endpoints or reducing its minimum.

Reports under `config/essence_ascendance/reports` show the installed values. They are inspection files, not additional configuration. Editing the human configuration does not change existing reports until a successful explicit rebuild.
