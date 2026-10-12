# Progression follow-up — beta.3

This pass uses generated curves and dimensionless budgets rather than fixed
item prices. No additional full modpack runs were required after the preceding
five-environment verification; focused synthetic-pack checks and native vanilla
readback cover these changes.

## Mining

The loaded iron-pickaxe mining-speed observation anchors Dormant, with the
native iron tier as the bootstrap fallback. Final unit rounding cannot lower
it below that benchmark. After pack competition determines the apex, the
existing curve's relative powered-tier positions are fitted between the entry
benchmark and that apex. Latent retains its existing fraction of Dormant.
A flat input curve uses tier position; a pack apex below the requested iron
minimum cannot lower the entry benchmark.

Measured vanilla speeds: Latent4, Dormant6, Awakened8, Resonant16, Ascendant17,
Transcendent18. Dormant equals native iron6 and remains below native diamond8.
Synthetic endpoint checks include6,12,120 and1200; the calibrated apex is kept.

## Purchase budgets

For N progression bands, the shared purchase-budget share is1/sqrt(N). Bonus
costs multiply their existing supply/utility/adjacent-tier calculation by that
share. Skill rank costs use its square times sqrt(mechanical utility), bounded
by the existing2% minimum and25% maximum tier-budget shares. This increases
small early purchases without creating an arbitrary fixed essence minimum or
raising the previous high-authority ceiling. Effects and meaningful minimums
are unchanged. Costs remain monotonic with utility, resource supply and ranks.

Measured first vanilla purchases:

| Purchase | Before | After |
| --- | ---: | ---: |
| Restful Mending |62|274|
| Feast Reflex |32|139|
| Inner Sustenance |39|170|
| Mining Speed Bonus |651|291|
| Movement Speed Bonus |1541|689|
| Hunger Efficiency Bonus |425|190|

Tool Instinct is retained but all three outgoing prerequisite links are removed.
Those skills now receive their own generated availability; removing a dependency
can also move a skill earlier and reduce its absolute price. Purchase/refund
receipts continue to preserve amounts actually paid.

## Crucible

One shared storage multiplier scales generated base and every pylon contribution
by10. Supply-derived tier shape remains intact; transfer rates and processing
speed are independent. Eight Dormant pylons produce4,032,000 total capacity
(previously403,200); eight Transcendent pylons produce404,712,000
(previously40,471,200). No item-specific block limit is introduced.

## Verification and updating

Focused runtime/adversarial profiles,97,584 Bonus checks,10,926 skill-progression
checks,28 access checks and both loader builds passed. Native vanilla verifies
fresh generation, an identical independent rebuild, all published rank lookups,
skill/enchantment diagnostics, saved restart and skill-layout audit. Receipts:
auxiliary `pack-compatibility/validation/performance/20261011-beta3-progression`;
local build logs `beta3-focused.log`, `beta3-prerequisites.log` and
`beta3-layout-audit.log`.

Generated revision is`progression-purchase-mining-48`; existing installations
need `/essence admin balance rebuild`. Author overrides remain authoritative.
The public release workflow independently runs the complete project checks.
