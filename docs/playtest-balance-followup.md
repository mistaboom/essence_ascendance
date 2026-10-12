# Enchantments, material value, and survival balance

This pass addresses vanilla playtest feedback through shared mechanics. It does
not assign a special price to copper or alter the Ascendance Archive delivery
trigger. Final verification receipts belong under auxiliary
`pack-compatibility/validation/performance/20261011-balance-completion`;
October 9 receipts retain the earlier implementation and pause history.

## Accepted balance decisions

- Restful Mending uses durability points per second: minimum first benefit 1,
  minimum additional meaningful rank benefit 1. An item with greater maximum
  durability does not automatically receive faster repair. Existing idle and
  single-target requirements remain. These are floors: the generator may publish
  stronger later ranks and larger increments when supported by its calibration.
- Metabolic Mending uses durability points per intrinsic food point: minimum
  first benefit 1, additional meaningful rank benefit 0.5. Nutrition and native
  saturation contribution retain their existing intrinsic-food meaning.
- Inner Sustenance retains its meaningful recovery minimum. Full hunger and
  saturation no longer grant indefinite immunity to ordinary exhaustion.
- Automatic Feast Reflex meals honor the generated native eating cadence.
  The first useful meal remains immediate; a tick loop cannot consume twenty
  meals per second independently of the skill's actual eating speed.
- Automatic Latent onboarding uses full entry effort by default, changing
  `attunement.onboarding_effort_fraction` from 0.5 to 1.0. This doubles the
  target for the same environment and inputs without changing category breadth,
  eligible actions, the Archive trigger, or the later chapter policy.
- Other meaningful first benefits and meaningful later increments remain
  requirements. A generated endpoint that cannot support another meaningful
  increment does not acquire fabricated ranks merely to retain an old count.

## Shared valuation rules

Enchantment facts must distinguish actual effects, native level curves,
conditions, and supported equipment. Item-stack dissolution and presentation
must use the same facts. Skill competition may reuse native measurements, but
economic prices never feed back as enchantment power. Unsupported behavior
remains explicit rather than acquiring invented numerical strength.

Material demand follows distinct applications. Cosmetic forms and repeated
recipe methods must not repeatedly inflate their shared material. Known harvest
quantities divide the cost of the harvest among its outputs, including tool wear.
An intrinsic category estimate must not override a cheaper fully modeled source.
Unresolved sources retain conservative fallback handling. Recipe and reversible
form conservation remain final constraints.

## Enchanted stacks and the Matrix asset

The same native enchantment facts feed competitive skill/Bonus evidence and
stack valuation. Only the economic projection normalizes those facts into price
weights; prices never become enchantment power. Actual component levels are
read from the stack, including legal stored levels above a definition's normal
maximum. Curses do not add positive value. Unknown custom positive effects stay
explicitly unsupported; empty-effect native definitions can use weak supported
equipment evidence without inventing throughput. Native effects with an explicitly
empty allowed-item set are impossible and supply no power or empty-effect
fallback. This prevents misleading level comparisons from placeholder data
whose actual gameplay is handled by unsupported custom code.

Every enchanted book retains its plain-book carrier value. Recognized effects
add category-routed value inside the generated enchanted-book ceiling, with
diminishing returns when several enchantments share a book. Normal enchanted
equipment redirects part of its existing yield without increasing its total.
Server payment, tooltips, search and Archive level rows use the same resolver.

An effect that starts after level I receives its real later-level strength.
All such effects on a book share one carrier allowance, so combining several
zero-starting effects cannot mint extra essence. Ordinary positive-start curves
retain their existing anvil bound. The focused suite checks 45,614 cases,
including mixed partitions, tiny/zero carrier budgets and stored levels to255.

The accepted vanilla `final-6` diagnostic covers all 42 definitions and 125
ordinary defined levels, with no unsupported positive vanilla definitions,
decreasing levels or failed bounds. Its plain carrier is 128 and book ceiling
381. These are measured generated examples, not hardcoded prices:

| Stack | Offensive | Defensive | Utility | Total |
| --- | ---: | ---: | ---: | ---: |
| Sharpness I book | 43 | 0 | 128 | 171 |
| Sharpness II book | 66 | 0 | 128 | 194 |
| Sharpness V book | 103 | 0 | 128 | 231 |
| Protection I book | 0 | 23 | 128 | 151 |
| Sharpness I + Protection I book | 39 | 20 | 128 | 187 |

Reflected damage from Thorns routes to Defense; Wind Burst's measured self
knockback routes to Mobility. Conditional effects remain conditional rather
than acquiring assumed universal uptime. Item-only recipes use the cheapest
book-component input state and conserved output ceilings.

`art/source/models/item/ascendance_matrix.bbmodel` is owned by the same
manifest-driven `layered_item` exporter as the Archive. Its untinted embedded
16×16 PNG is copied byte-for-byte into generated runtime resources, with a
generated item model replacing the old amethyst placeholder. The user source
is preserved. Both loader jars contain the matching runtime assets and exclude
authoring files; the generated PNG SHA256 is
`8682e534e65ca1ba88baf2f2920326bb753d57e7ae1a309a24075def68331abf`.

## Final vanilla material evidence — October 11

These measurements compare the October 8 baseline
`20261008-engineering-completion/vanilla/final-1/profile-1.json.gz` with
`20261011-balance-completion/vanilla/final-6/profile-0.json.gz`, under the auxiliary
performance receipt root. The final vanilla receipt used NeoForge JAR SHA256
`276a07cf92885a00df1660064fff5b5372e2dd07090d2683ffdb896e9cb31522`.
Its independent rebuild reproduced evidence, economy, runtime, skills,
validation and competitive capabilities; its saved restart was accepted.
The complete final pack matrix is recorded below.

The following numbers are total dissolution essence per item, not acquisition
estimates or category-specific prices:

| Item | October 8 | Final vanilla |
| --- | ---: | ---: |
| Raw copper | 116 | 48 |
| Copper ingot | 54 | 22 |
| Iron ingot | 45 | 45 |
| Gold ingot | 45 | 27 |
| Plain book | 16 | 128 |
| Charcoal | 18 | 31 |
| Wheat / wheat seeds | 18 / 55 | 23 / 33 |
| Carrot / potato / beetroot | 26 / 26 / 42 | 26 / 25 / 35 |
| Melon slice | 41 | 11 |

Copper's collected ore drop is 3.5 raw items per ordinary harvest, versus one
for iron and gold. Actual output count now divides harvest effort and wear;
the dense-ore yield tag does not apply the same quantity benefit twice. Distinct
functional applications replace repeated cosmetic recipes and namespace premiums
as demand evidence. The result follows these general rules, without copper IDs
or fixed copper prices. All eight checked raw/ingot/nugget/block metal pairs
retain exact 9:1 economic value, total yield and individual category yields.

Bookshelf and campfire drops now depend on independently acquiring the supplied
block. They cannot bootstrap cheap books or charcoal merely because their loot
tables exist. Plain books yield 128; an enchanted-book carrier has a conserved
item-only ceiling of 381, with actual stack contents determining its payout.
All 1,674 reported production invariants pass. Additional craft-and-break checks
compare the cheapest recipe-input yield with the maximum tested dropped yield:
bookshelf 420→384, campfire 92→62, clay 24→24, melon 99→99 (including its native
Fortune limit of nine slices), plain decorated pot 16→16 and flower pot 12→12.
These focused salvage checks do not claim that every native block action is
represented in the production graph.

Loaded native chorus and Nether-vine feature implementations now establish
natural placement. The audited NeoForge `shears_dig` predicate restores both
shears-only collection and inverted leaf-drop conditions. Relative to the first
October 11 run, 13 resources regain reachability: chorus fruit, popped chorus
fruit, the four purpur forms, end rods, both Nether vines, both azaleas, hanging
roots and Nether sprouts. Wheat, beetroot and other independently obtainable
crops retain their seed/planting dependencies; an unavailable seed is not an
independent source.

The exact remaining new reachability losses against October 8 are wither rose,
torchflower and pitcher plant. Wither-kill flower production and Sniffer digging
are unsupported entity actions. Torchflower seeds and pitcher pods were already
unresolved in the baseline. Potted flowers cannot supply their own missing
bootstrap item. Black, orange and cyan dyes remain reachable, but their yields
are zero because recipes using those zero-yield flowers conservatively constrain
their shared output. These six newly zero-yield items are explicit coverage
limits; no replacement price or invented source was added.

The seven user-edited recipes were preserved exactly. Each saved native recipe
matches these ingredient counts and produces one item:

| Output | Ingredients |
| --- | --- |
| Ascendance Caster | 2 Latent Ingots, 1 Amethyst Shard, 1 Stick |
| Ascendance Matrix | 4 Latent Nuggets, 1 Diamond |
| Ascendance Nexus | 7 Channelstone |
| Essence Crucible | 7 Channelstone, 1 Furnace |
| Essence Focus | 4 Glass, 1 Diamond |
| Essence Infuser | 8 Channelstone |
| Essence Pylon | 2 Latent Ingots, 1 Channelstone |

The final pack checks exposed two general acquisition boundaries. Known plain
item/tag recipe inputs now resolve from the captured tag membership, preventing
optional ingredient interners and soft caches from changing a rebuild's inputs.
Audited machine quantities and custom ingredient limitations remain intact.

A valid acquisition route remains modeled when its conserved whole payout
rounds to zero. Native source proofs established after the initial graph pass
now unlock complete recipe descendants through the existing acquisition/quest
graph. This closure retains independent ordinary routes, finite quantities,
exclusive reward choices, progression stages and human overrides. New recipe
values are bounded by paid consumed inputs; unknown cycles and unsupported
source mechanics do not become free acquisition routes. Positive-chance outputs
can establish repeatable access when consumed inputs are proven renewable and
reusable setup is obtainable. Finite probabilistic rewards do not become
guaranteed stock, and expected output still bounds economic cost.

## Existing installations

Generated revision `enchantment-native-maintenance-47` requires an explicit
`/essence admin balance rebuild`. Revision 46 percentage-based repair fields
must never be interpreted as native durability points. The existing strict
saved-profile policy remains: no implicit regeneration or migration on startup,
and a rejected candidate cannot replace a valid active profile.

Existing human configuration is not overwritten. An existing
`onboarding_effort_fraction = 0.5` remains an explicit override; set it to `1.0`
before rebuilding to adopt the new opening effort. Player tiers, earned
Attunement progress, purchase receipts, and refund history must be preserved.

## Verification

The new `tools/verification/balance_feedback_run.py` reuses the isolated native
vanilla, ATM10, ATM10 Sky, StoneBlock 4, and OceanBlock 2 servers. It archives
previous fixture artifacts, launches only an owned hidden server process,
captures generated profiles, checks every published rank's console result,
validates rebuild reproducibility, and verifies saved restarts without generation.
Normal shutdown requires the server to confirm that all dimensions were saved.

Native headless checks establish implementation and generated-profile behavior.
They do not establish live multiplayer presentation or subjective player feel.
All five final receipts are accepted on NeoForge SHA256
`276a07cf92885a00df1660064fff5b5372e2dd07090d2683ffdb896e9cb31522`.
Each covers fresh generation, one independent identical rebuild, every published
rank, native skill/enchantment diagnostics, an offline skill-tree audit, and a
saved restart with zero generation/evidence/solver/calibration/report work.
Every owned server confirmed all dimensions saved and stopped.

| Environment | Receipt | Ranks | Saved files | Saved load | Enchantments / unsupported |
| --- | --- | ---: | ---: | ---: | ---: |
| Vanilla | `vanilla/final-6` | 236 | 61 | 2.356s | 42 / 0 |
| ATM10 | `atm10/final-5` | 229 | 63 | 28.861s | 139 / 48 |
| ATM10 Sky | `atm10-sky/final-2` | 229 | 65 | 15.947s | 85 / 23 |
| StoneBlock 4 | `stoneblock/final-1` | 231 | 60 | 7.813s | 90 / 33 |
| OceanBlock 2 | `oceanblock/final-1` | 236 | 59 | 6.808s | 55 / 4 |

Across the five profiles, all 1,161 published ranks and 95,355 transaction-state
checks pass. All six skill trees per profile pass prerequisite/rank checks.
Enchantment diagnostics cover 1,269 defined level rows, with no decreasing
payouts or failed diagnostic bounds. Unsupported custom effects remain explicit.
The source recipe snapshot is byte-identical, and all 321 assets match in both
loader jars. `completion-summary.json` and `final-enchantment-summary.json`
contain the consolidated receipts.

Custom effect coverage is not universal: the final ATM10 report lists 48
unsupported positive definitions among 139, including Apothic Life Mending's
`repair_with_hp` and Create Potato Recovery's custom projectile consumer.
Impossible native item conditions no longer invent a level-I benefit. The
MineColonies effect that starts at level II is recognized, but ATM10's zero
plain-book carrier budget limits its credited premium to zero; funded cases
are covered by the focused tests. These are explicit coverage/conservation
limits, not fabricated native power. Native headless checks do not establish
live multiplayer presentation or subjective player feel.


The final ATM10 material readback preserves all nine restored compressed-lapis
routes, brush/acacia reachability and 49,141 production invariants. Relative to
the preceding accepted candidate, acquisition evidence, runtime, skills and
validation match exactly; actual economy values and process inputs/outputs also
match. Only retained recipe-definition metadata differs between JVM sessions;
that comparison is recorded in `atm10/final-5/prior-economy-metadata-check.json`.
The final run's own initial/rebuild comparison matches all checked sections.
