# Economy/source grooming — October 13, 2026

This is an active engineering record, not final acceptance. Current native receipts
belong under the auxiliary `grooming/20261013/<environment>/<run>/` directory.
Historical October 11 profiles below are comparison evidence only. No rate was
inferred from item counts, renewability labels, recipes, or registry size.

## Current checkpoint — candidate 6

Vanilla and ATM10 passed the native initial/rebuild/restart lifecycle. Matching
baseline/candidate audits now have 45 role loadouts each for vanilla, ATM10,
ATM10 Sky, StoneBlock and OceanBlock: 450 scenarios total. All five full catalog
comparisons are complete. Remaining native lifecycle acceptance stays with the
coordinating task; report completion alone does not establish lifecycle acceptance.
Promotion is withheld: ordinary ATM10 supply and practical affordability remain
unresolved despite passing mechanical checks. Production is frozen. The reviewed
performance fixture change is only 36 diagnostic whole-number formatting strings.

All 229 ATM10 rank costs change; 13 rank tier gates and 25 outcome rows differ.
Fisher's Call 1 rises 1,069 → 6,984 (6.5332×), and Pocket Nets 2 rises
1,434 → 9,368 (6.5328×). Adaptive Guard 1 falls 376 → 143 (0.3803×).
The two largest increases have unchanged native outcomes and utility: fishing
equipment setup moves ENTRY → EARLY, moving Fishing Instinct to Awakened and
its strict descendants to Resonant. The saved witness changes from Aquaculture's
gold rod to its iron rod; both rods now have EARLY aggregate acquisition.
The later tier changes the price budget 2,520 → 13,720. Gathering's category
factor separately rises 3⅓ → 4, producing the 6.5333× raw-price multiplier before
ceil rounding. All ten saved prices for these two skills were independently
recomputed. No stronger fishing effect or measured fishing rate caused this rise.

The shared price inputs are medians over reachable external positive-payout item
variants. Gathering's median rises 40 → 105, while the all-family APEX median
rises 12 → 18; the ratio is capped at 4. Baseline central rows include MA fish
and other seed variants, now unproved and removed from positive supply; current
central rows include the same convertible cinnabar/cinnabar-dust material at 105.
Mobility has the largest category-factor increase: 13/12 → 54/18, or 1.0833 → 3.
Its 238 → 261 positive variants include 130 → 140 Cooking for Blockheads and
Dyenamics-and-Friends variants sharing recoloring inputs. For example, rose
counters and sinks each rise from 13 to 105 Mobility; the current median lies at
the 54-Mobility spell scroll. These are correlated item forms, not independent
farm throughput. All supported-rate lists remain empty and rate pressure is 1.
`economy/atm10-candidate-6-price-causality.json` retains exact median cohorts,
selected source records, operating gates, all rank deltas, and price arithmetic.

All 45 ATM10 selected-role bills increase, while both common-material basket
models remain unfunded. The complete zero-input traces and proposed explicit
unknown/hard-zero distinction below are the next economy work; no conservation
constraint or source assumption was relaxed to make these bills appear payable.

The closing comparison adds all five tiers for Sky, StoneBlock and OceanBlock, including
per-family and flexible-core differences and cumulative skill/Bonus/gear/focus
components. All 45 scenarios per profile pass independent sum and source-stage
label checks. Sky's 45 bills all increase; its Dormant one-gear gatherer/explorer/
fighter totals move 17,434 / 13,539 / 13,497 → 45,668 / 63,988 / 40,913.
Transcendent totals move 2,732,540 / 2,507,097 / 2,629,611 →
13,373,807 / 14,343,530 / 13,175,153. Both fixed common-material basket models
remain unfunded in every Sky scenario. These larger bills are not acceptance
of practical affordability.

StoneBlock leaves 36 bills unchanged and reduces nine fighter variants through
the corrected Bonus purchases. One-gear fighter totals at Resonant/Ascendant/
Transcendent fall 160,541 / 560,996 / 2,185,885 →
160,357 / 559,809 / 2,181,221; Dormant remains 3,320. Its direct-renewal baskets
fund 0/45 and broader opportunity baskets fund 3/45, before and after. A funded
opportunity basket establishes only arithmetic quantities, not finite inventory,
farm time or setup feasibility.

OceanBlock leaves 33 bills unchanged and reduces 12 fighter variants. One-gear
fighter totals at Awakened/Resonant/Ascendant/Transcendent move
59,315 / 173,258 / 629,582 / 2,418,796 →
58,257 / 172,199 / 625,878 / 2,406,893; Dormant remains 24,073. Both common-material
basket models remain unfunded in all 45 scenarios. All 90 OceanBlock baseline/
candidate scenarios pass the same arithmetic and source-stage label checks.

The coordinator's full catalogs find Sky changes 15,193/42,667 rows and raises
positive-payout variants 3,200 → 4,040 (987 recover; 147 lose payout). StoneBlock
changes only 5/30,840 rows: three IE gun parts lose unsupported reroll-trade
payouts, the processing plate loses its finite joint-access projection, and vine
source metadata changes while its payout stays 146. Positive variants fall
4,835 → 4,832. Exact catalog records are separate from spending comparisons.
OceanBlock changes 24/24,266 catalog rows, entirely evidence/diagnostic changes:
no total or routed-family payout changes, with 3,195 positive variants unchanged.
Its candidate scan finds no inventory flags, saved conservation failures or
declared source rates, with 15 positive Mobility variants and one direct
constructive renewal variant. These checks do not establish practical supply.
See `economy/remaining-three-candidate-6-comparison.json`,
`economy/remaining-packs-candidate-6-loadout-summary.json`, and the per-environment
`*-candidate-6-loadout-comparison.json` files. All native profiles remain untouched;
all five environments now have complete baseline/candidate loadout comparisons.

## Reproducible inventory

`tools/verification/grooming_economy_audit.py` reads every economy resource and its
source records. Repeat `--profile environment=absolute-profile-path` for a matrix,
provide `--output absolute-auxiliary-directory`, and explicitly select
`--evidence-status historical_comparison`, `fresh_candidate`, or
`accepted_candidate`. It writes a compressed full catalog per environment, a
numerical economy report, and `matrix.json`. Each report identifies the source
profile SHA-256/integrity, complete Mobility alternatives, family/stage item
counts, source provenance, unknown versus declared rates, outliers, saved
conservation results, skill/Bonus costs, equipment/focus costs and conversion
quantities. It does not itself regenerate a profile or attest native acceptance.

`tools/verification/grooming_loadout_audit.py --profile <saved-profile>
--skill-audit <expanded-coverage.json> --output <auxiliary-directory>` adds 45
plausible gatherer, explorer and fighter bundles across all five power tiers, each with one
gear item, two gear items, or two gear items plus sequential focus upgrades. It
requires the exact profile integrity/runtime matching the registry audit.
`loadouts.json` retains actual rank prerequisites, choice restrictions, later-tier
deferrals, marginal/cumulative costs, per-family bills, explicit focus flexibility,
and physical setup recipes. Full per-source quantities are compressed separately.
Dormant uses selected first ranks; later tiers train only those role skills to
their maximum available saved ranks. Source filters follow the engine's matching
five acquisition-band ordinals. Explicit milestones remain conditions, not
assumed player achievements.
Mobility groups use actual shared source/process IDs and bounded input lineage;
they overlap and cannot be summed as independent farms. Two illustrative baskets
distinguish direct conditional-renewal proof from all reachable opportunities.
Each basket counts an item once and retains every routed family credit. They are
quantity comparisons, not independent proofs of player stock or farming time.

The initial comparison scan is in auxiliary
`pack-compatibility/validation/performance/20261013-grooming-economy/historical-comparison/`.

| Historical environment | Items scanned | Positive Mobility item variants | Variants with a direct constructive renewable source | Declared measured source rates |
| --- | ---: | ---: | ---: | ---: |
| Vanilla final-6 | 1,361 | 61 | 6 | 0 |
| ATM10 final-5 | 64,341 | 238 | 0 | 0 |
| ATM10 Sky final-2 | 42,666 | 256 | 2 | 0 |
| StoneBlock 4 final-1 | 30,839 | 65 | 0 | 0 |
| OceanBlock 2 final-1 | 24,265 | 15 | 1 | 0 |

All 163,472 rows had consistent routed/total payout and no positive payout for an
unreachable resource. Saved native conservation invariants reported no failure.
These checks do not establish all source classifications or operating burdens.
Direct source counts omit crafted descendants and must not be read as independent
farm counts or proof that other farms are absent. Colors, wood variants and shared
underlying ingredients are retained for tracing, never summed as supply diversity.
The machine-readable `constructiveRenewal` flag is specifically a conservative
direct `SourceAvailability` contract, not a census of all supported renewable
pathways. A declared `rateKnown` flag also does not independently certify empirical
measurement.

The fresh startup-repaired vanilla baseline and exploratory candidate1 inventory
is under `grooming/20261013/economy/vanilla-initial/`. Both contain 1,362 resources,
61 positive Mobility variants, six direct constructive Mobility variants, no
declared rates, no inventory flags and no failed saved conservation invariant.
Their economy outputs are identical; candidate1 predates the current vine and
subsequent balance fixes, so this is not acceptance of the final candidate.

## Corrections and focused verification

**Joint finite acquisition.** `AcquisitionProgressionGraph` previously checked
per-item finite quantities without retaining ancestry. One finite ingot could
independently prove A and B; a later A+B recipe was then admitted, despite needing
two ingots. The candidate retains selected witnesses and replays a shared finite
bill before publishing a placement. Deterministic coproducts and leftovers from a
paid batch are retained. One-time rewards cannot be redrawn; reusable setup and
consumed inputs compete for the same source stock. Chance production remains
conditional access only with renewable recurring inputs; it does not create
guaranteed stock from finite trials. No public profile schema or player history
changed.

Root's candidate1 verification passed both loader builds and
`balanceQuestAcquisitionInvariants` (76 checks),
`authoritativeAcquisitionClosureInvariants` (46), and
`configuredRecipeAccessInvariants` (122). New adversarial cases distinguish
insufficient shared ancestry from sufficient stock, shared coproducts, shared
batch leftovers, and catalyst setup competing with consumed material.

Follow-up review identified and corrected conservative misses: nested prerequisite
operations can reuse an already reserved finite catalyst, and a lazy alternative
search can recover a feasible joint assignment. Per-operation output counts no
longer masquerade as the entire finite stock: replay can pay multiple batches.
The selected joint inputs are passed to authoritative price bounds so a separate
cheapest-input selection cannot reintroduce conflicting ancestry. Search is bounded to 64
assignments sharing 4,096 replay visits and 128 levels; exhaustion is explicitly
reported as unknown access. It does not materialize Cartesian combinations.
The same bounded search now considers prerequisite threshold subsets: an OR
quest can choose the prerequisite that leaves finite payment stock available.
Random reward probabilities are retained; one-time random rewards remain
opportunities with an explicit unknown guaranteed-stock diagnostic. Repeatable
chance rewards require independently renewable reset payments. Already-projected
quest opportunities cannot re-enter as unrestricted recipe rules.

Root's `candidate-focused-3.log` subsequently passed both loader builds,
quest acquisition (90 checks), authoritative closure (46), configured recipe
access (122), natural blocks (87), loot acquisition and salvage. This covers the
follow-up joint/probability and vine source corrections above. Native final
generation/acceptance remains a separate requirement.

The witness retains one preferred proof per intermediate node. Bounds and missing
witnesses conservatively leave access unproved; this is not a complete planner
over all possible operation schedules or alternative intermediate recipes.

**Native vines.** Historical vanilla confirms 65 Mobility/vine and a single
wandering-trader row with no rate. The aggregate `RENEWABLE_MANUAL` label does not
prove trader access, payments, stock or a cultivation cycle. The native placement
scanner handled cocoa decorators but omitted `trunk_vine` and positive
`leave_vine` decorators. The candidate adds those exact native placement facts,
then separately proves a retained-parent cultivation/harvest cycle. It requires
starting Overworld natural placement or an independently acquired starter,
joint support/shears setup, independently renewable replacement shears, enabled
growth/drop gamerules, and effective unenchanted shears loot. Native code requires
a horizontal parent attachment and clear lower growth space above minimum world
height. The parent remains; lower grown blocks are harvested. Shears wear is one
durability per harvested block. No farm rate, repair credit or item price is added.

The narrow adapter is needed because these native mechanics are not encoded as
ordinary recipes. It does not treat custom plants, lookalike decorator names,
unreachable dimensions, unsupported loot callbacks or disabled growth as proof.
Focused tasks are `balanceNaturalBlockInvariants` and
`balanceLootAcquisitionInvariants`; native candidate verification must confirm
the emitted source, conditions and stage on both loaders. Those new changes were
not part of the earlier candidate1 artifact.

## Supply, conversion and competing purchases

Historical vanilla provides meaningful alternatives: bamboo yields 22 Mobility
plus 12 Utility; Ender pearls yield 59 Mobility; slime balls 10; breeze rods 47
at MID. Fishing/mob opportunities also include nautilus shells and saddles.
Availability and throughput are different: encounter/search probabilities,
growth area, drop conditions and harvesting still constrain each route. Boats,
rafts, ladders and rails must be charged their actual recipes and inputs rather
than counted as independent farms. A bamboo raft yields 535 Mobility against
five bamboo planks (22.5 bamboo amortized; finite batches must round crafting
operations and keep leftovers). This moves some Utility opportunity into
Mobility without increasing total value. Vines remain a distinctive route.

As a **historical quantity scenario**, Dormant Movement Speed completion (1,541),
Step Height completion (254), Running Momentum rank 1 (75), and the Mobility
share of Dormant boots (4,200) total 6,070 Mobility. At the saved payouts that is
94 vines or 276 bamboo; boots additionally require Defense/Utility, a matrix and
the actual machine progression. This is one plausible purchase bundle, not all
upgrades and not measured play time. New candidate costs/payouts must replace
these comparison numbers before acceptance.

The fresh vanilla `initial.json.gz` baseline and matching expanded registry audit
give a more accurate early explorer bundle: Running Momentum 1, Impact Control 1,
Waylight 1, Dormant Movement Speed/Step Height endpoints and one Dormant pair of
boots require 5,648 Mobility, 2,100 Defense and 1,012 Utility (8,760 total). The
Mobility amount alone is 87 vines or 257 bamboo. The baseline does not yet have a
direct constructive vine-cycle proof. The quantity-only conditional-source
basket instead uses 96 pearls, 37 leather and 34 feathers, retaining all other credits;
this is not a claim that pearl gathering is the fastest or easiest route.

Adding Dormant leggings and a Dormant focus changes that bundle to 49,360 essence:
25,840 fixed family minima/purchases plus one flexible core remainder of 23,520.
Charging that remainder six times would be incorrect. In the same baseline the
Dormant fighter's requested Frenzy, Kindling and Life Steal are deferred to
Awakened, and Melee Damage is unavailable at Dormant. The diagnostic does not
price those as early purchases; the Awakened fighter with one sequentially
upgraded weapon and maximum available selected skill ranks costs 39,021 total,
including 27,025 Offense. These receipts live
under `grooming/20261013/economy/vanilla-baseline-loadouts/`; final candidates must
be compared against their own matching coverage.

Fresh ATM10 initial evidence has 64,342 resources and 238 positive Mobility
variants, but no direct constructive source contracts in any of the six families.
This is a coverage boundary, not proof that farms are absent. Effective harvest
callbacks, independently paid setup and unsupported mod cultivation must be
checked before a direct cycle can be certified. No measured source rate is
present. Both conditional-renewal and common-material opportunity baskets remain
unresolved across all 45 scenarios because the selected ordinary materials have
zero payouts. Positive item comparisons alone do not establish affordability:
some early-tagged alternatives require dozens of rare artifacts or mod materials
whose practical early acquisition is not demonstrated. Current initial receipts
are under `economy/atm10-baseline/` and
`economy/atm10-baseline-loadouts/` beneath the unified grooming root.

ATM10 vine is zero because the complete native crafting recipe consumes three
`mysticalagriculture:nature_essence` to make twelve vines. Nature essence has
economic value 19 but UNKNOWN/unreachable acquisition and zero payout. Its two
captured machine producers, IE cloche and MA seed reprocessor, explicitly have
incomplete acquisition/conservation and unknown energy/quantities; they are
correctly excluded. The downstream complete crafting route still conservatively
caps vine at `floor(3 × 0 / 12) = 0`. This is the recorded zero-credit conservation
policy, not evidence that nature essence is free or a measured farm. Factual MA
crop/seed coverage or an explicitly justified general policy for unknown-valued
inputs is needed; an independent positive vine override would violate the known
conversion constraint. See `economy/atm10-source-trace.json`.

**A separate systemic numeric failure is proven.** The old valuation cap of
2,000,000,000 served both as a price saturation limit and the maximum exact
conversion-family ratio. The fresh ATM10 loaded graph contains complete,
deterministic recipe pairs at every step from iron nugget through iron block and
nine compressed forms: the largest form represents 9^11 = 31,381,059,609 nuggets.
Coal, copper and wheat chains each reach 9^10 = 3,486,784,401 primitive units.
Every one exceeds the arbitrary old bound, so the engine invalidated its entire
family, including ordinary ingots/coal/wheat. Independently saturating compressed
acquisition costs also makes reverse crafting appear artificially cheap.
`grooming_compression_audit.py` reproduces these exact native graph chains;
`economy/atm10-compression-bound.json` retains every forward/reverse recipe ID and
baseline value. This proof does not infer the same cause for every zero item.

The candidate keeps rounded acquisition estimates unsaturated internally and
applies the actual whole/micro ledger bound only to final payouts:
`Long.MAX_VALUE / 1,000,000 = 9,223,372,036,854`. Rational conversion units remain
exact; one bounded primitive payout scales every form and essence category.
Nonfinite internal estimates fail generation instead of becoming a cheap anchor.
Public saved schemas are unchanged. `materialValuationInvariants` now exercises
the loaded chain lengths, near-boundary families, unrepresentable wider families,
unsaturated effort beyond long range, nonfinite estimates and category equality;
its existing sub-unit and unresolved-anchor cases remain required. These changes
passed the coordinated `materialValuationInvariants` run (179 checks). Unknown MA
inputs retain their separate zero-credit conservation policy; no item exception
or unsupported free input was introduced.

The full suite's valuation-performance snapshot mismatch was independently
reviewed: exactly 36 factor strings (18 items in both traversal orders) changed
`N Essence/output` to `N.000 Essence/output`; all values, routes, confidence,
recipe choices and other fields were identical. Applying only those inspected
string substitutions to `common/src/test/resources/valuation/valuation-reference.json`
produced byte-for-byte equality with the observed snapshot. The reviewed
reference was updated for those strings only; strict complete-reference equality
remains enforced, with no test exception or new numeric expectation. The exact
field diff, original/observed SHA-256 and byte-equality receipt are saved at
`economy/valuation-performance-candidate6-diff.json`. Production sources and
candidate artifacts remain unchanged; root coordinates the focused rerun.

## Candidate 6 native evaluation and remaining boundary

The accepted vanilla candidate's complete 1,362-item comparison changes only vine
among payout/stage/reachability/direct-renewal fields: 65 becomes 28 Mobility and
the retained-parent source is now proved. The new source is EARLY, while aggregate
item placement remains ENTRY. All 36 conversion rows, equipment/focus costs and
Crucible settings are unchanged. All 45 role scenarios were regenerated against
matching expanded native audits. Gatherer/explorer spending is unchanged; fighter
totals decrease by 48/111/527/2,026 at Awakened/Resonant/Ascendant/Transcendent due
to melee Bonus coalescing, with selected skill costs unchanged. The corrected
25% melee endpoint begins at Resonant; later segments are not repurchased.

The loadout diagnostic now requires the constructive source itself to meet the
scenario stage. It no longer promotes later source proof through an earlier
aggregate item placement. Seven Dormant common-material portfolio baskets retain
an explicit Mobility gap. This is a portfolio limitation, not absence of every
route: ENTRY fishing proofs for shells/saddles remain in the full alternatives.
For example, 135 shells plus 37 leather numerically cover the Dormant explorer's
fixed bill, but no collection/fishing rate is known. Candidate explorer Mobility
quantities are 202/1,027/3,737/14,790/57,944 vines across the five tiers; the Dormant
number is only an item opportunity, not an ENTRY cultivation proof. See
`economy/vanilla-candidate-6-comparison.json` for all checked stages and bills.

ATM10 candidate 6 initial generation, independent rebuild and restart are now
accepted. Matching expanded coverage contains 229 ranks and 148 active Bonus
endpoints, with zero declared/native floor failures. It contains 64,342
resources, zero saved conservation failures and no declared source rates. Positive
item payouts increase from 8,761 to 10,098 (1,602 recover, 265 become zero). Positive
Mobility variants increase 238 to 261. All six families still have zero strict
direct constructive source contracts, so practical affordability remains unproved.
The entire changed catalog is retained in `economy/atm10-candidate-6-comparison.json`.
`economy/atm10-candidate-6-compression.json` confirms the complete loaded forward
and reverse chain at every step for iron/coal/copper/wheat.

The numeric correction restores economic values, but separate final constraints
still zero the ordinary payouts:

| Item | Baseline → candidate economic value | Candidate payout | Recorded limiting input/route |
| --- | ---: | ---: | --- |
| Iron ingot | 0 → 36 | 0 | Four unproved HNN overworld predictions plus unproved rotten flesh make eight ingots |
| Coal | 0 → 43 | 0 | Eight tagged tiny coals include an unproved Actually Additions alternative with no captured producer |
| Copper ingot | 0 → 27 | 0 | Tagged nuggets include unproved alternatives; four zero-credit emeralds also buy eight ingots |
| Wheat | 0 → 39 | 0 | Three unproved MA nature essence make eight wheat |
| Vine | 3 → 95 | 0 | Three unproved MA nature essence make twelve vines |
| Leather | 0 → 153 | 0 | An unproved Wilden wing converts to leather |
| Feather | 22 → 22 | 0 | An unproved Silent Gear fluffy feather converts to a feather |
| String | 1 → 41 | 0 | Three unproved IE hemp fiber convert to string |

These limiting crafting definitions are structurally complete. Their input
acquisition is not established: nature essence has only incomplete cloche and
reprocessor captures; its seed is also unproved in this candidate. HNN prediction
has only an incomplete quest-reward producer. Hemp fiber has only an incomplete
cloche capture. Tagged pearl/slime/nugget alternatives similarly retain numerous
unproved zero-credit variants. This is not evidence of verified cheap late farms.
See `economy/atm10-candidate-6-zero-input-trace.json` for exact counted inputs,
every alternative, outputs and producer completeness. The source-uncertainty
receipt records common mob drops with unresolved loot conditions/functions;
`RuntimeLootAudit.mark` currently marks any applicable unmodeled modifier on a
table. The block-specific preservation contracts do not establish equivalent
mob/fishing semantics. Their installed implementations require separate factual
audit before that marker can be narrowed.

The accepted ATM10 profile and matching expanded audit now have all 45 loadouts,
using the corrected source-stage classifier. Exact arithmetic and every published
conditional-source stage were checked. All 45 total purchase bills increase from
the startup baseline; all 45 conditional-renewal **and** fixed common-material
opportunity baskets remain unfunded because the portfolio's ordinary payouts
remain zero. This is not a proof that every possible pack route is unavailable.
Current one-gear totals, including only the selected role skills and their paid
prerequisites, are:

| ATM10 candidate role | Dormant | Awakened | Resonant | Ascendant | Transcendent |
| --- | ---: | ---: | ---: | ---: | ---: |
| Gatherer | 10,901 | 57,655 | 110,543 | 380,475 | 1,427,425 |
| Explorer | 12,468 | 41,784 | 96,491 | 374,446 | 1,446,477 |
| Fighter | 8,338 | 22,630 | 74,496 | 336,672 | 1,357,985 |

Baseline Dormant totals were 8,474 / 7,699 / 6,760; Transcendent totals were
941,869 / 899,206 / 913,433, respectively. Current cumulative Transcendent gear
alone costs 1,284,865, accounting for about 89–95% of these late bills. Restoring
economic values affects generated spending before the separate final-payout
blockers restore usable supply; these increases are not evidence of affordability.
The current Dormant explorer needs 9,241 Mobility, 2,220 Defense and 1,007 Utility.
Comparisons such as two teleportation amulets, 21 PneumaticCraft speed upgrades,
or large compressed building forms are explicit quantity opportunities; their
ENTRY aggregate labels do not establish realistic early inventory or farm time.

The current ATM10 Latent cross-family nugget transaction costs 1,538 source
essence and stores 657, extracting 643.86 with persistent carry in addition to
14 ticks of infusion work. The baseline was 1,248 → 533 → 522.34. Fifteen current
transactions could numerically cover that early explorer's Mobility bill for
23,070 source essence and 9,657.9 target credit, but still require the matching
latent materials, installed focus, machine, source supply and transfer/extraction
work. No collection rate or startup circularity resolution is inferred from this
conversion option. `economy/atm10-candidate-6-loadouts/` retains all scenarios and
source comparisons; `economy/atm10-candidate-6-loadout-comparison.json` retains
every baseline/candidate total, cost component, unpaid family/flexible balance,
stage check and top item opportunity. Production remained frozen for this report.

### Proposed next change — no production implementation in candidate 6

The shared boundary is explicit in code: `EconomyGenerator` creates an empty
payout for unknown acquisition; `EconomyConservationSolver` converts it to scalar
zero; `inputUnits` takes the minimum over every tag alternative.
`WholeUnitConversionFamilies.reconcile` also defaults missing values to zero.
Thus absent payout evidence becomes a purported zero material-credit bound.
Skipping such constraints would be unsafe and is not proposed.

Retain separate **payable yield**, **value-evidence state**, and **constraint
result**. Proposed evidence states are:

| State | Meaning and required provenance |
| --- | --- |
| Known bound | A supported source/paid recipe supplies a numerical bound with its selected inputs and source identity |
| Hard zero | An explicit existing zero-credit policy, verified free-production constraint, or actual whole-unit precision/cycle result supplies a documented zero; never infer it merely from missing acquisition |
| Unresolved | Source access, quantity, cost or producer semantics is missing; preserve the cause and dependencies, with no numerical zero assertion |

Constraint outcomes need PROVED / DISPROVED / UNKNOWN rather than representing
unknown as `passed=true` or silently omitting the edge. Existing saved boolean
invariants cannot express this alone; an additive diagnostic schema/compatibility
decision is required. Internally excluded carriers and explicit overrides also
need classified reasons: "not directly payable" does not by itself prove free
material production. Preserve existing deliberate zero policies until their
intent is represented explicitly.

A possible conservative second step is a **symbolic material-potential
certificate**, distinct from item price or payout. Unknown items keep zero actual
direct credit but receive unresolved variables in the proof. Every complete
recipe, every ingredient alternative, maximum realized output and shared
coproduct budget must satisfy one common assignment; known payout cannot exceed
its variable, and proven hard zeros remain fixed at zero. Exact reversible-family
ratios apply to that assignment. A valid finite certificate can prove absence of
positive closed conversion gain within the captured complete graph without
pretending unknown acquisition costs are measured. Unknown numerical source
bounds still remain UNKNOWN, and incomplete runtime producers remain outside a
global proof. A finite witness chosen only for proof must never be exported as an
inferred price or farm rate. Failure/exhaustion of a bounded certificate search is
UNKNOWN, not proof of arbitrage. No expensive unbounded optimizer is authorized.

Before any payout policy changes, narrow tests must establish:

1. Known A(10) → unknown U → known B(20) still caps B at 10; unknown nodes cannot
   erase a paid path through the graph.
2. Known A → two U → A, and wholly unknown amplifying cycles with a payable outlet,
   cannot fund positive closed gain. Simply skipping U edges fails these cases.
3. A tag with a known cheap alternative retains that bound even alongside unknown
   alternatives; a genuinely hard-zero alternative still constrains the output.
4. An orphan unknown ingredient does not assert a zero-cost route, yet its recipe
   remains an UNKNOWN value constraint with explicit provenance.
5. Shared outputs, catalysts, exact compression, component-sensitive inputs and
   bounded finite-source allowances retain existing conservation semantics.
6. A finite accessible unknown reward remains one consumable opportunity; neither
   a virtual proof value nor its conversion establishes repeatability or farm rate.
7. Every published witness is rechecked with exact arithmetic; bounded search,
   overflow and unsupported edges remain explicit unknowns.

First implement the evidence/status distinction and diagnostic regressions while
keeping payout behavior unchanged. Only after reviewing the certificate and
runtime-loop regressions should policy decide whether an independently supported
positive output can remain payable while a numerical input bound is unresolved.
A MA crop adapter alone cannot repair the orphan tag alternatives, HNN, hemp and
mob-source gaps. Candidate 6 is a partial checkpoint, not completion of the full
economy/source grooming request.

The five-tier vanilla loadout receipts now retain the same selected role skills,
training only their available ranks at later tiers, plus sequential gear costs:

| One gear item bundle | Dormant | Awakened | Resonant | Ascendant | Transcendent |
| --- | ---: | ---: | ---: | ---: | ---: |
| Gatherer | 8,131 | 54,584 | 174,899 | 668,406 | 2,605,241 |
| Explorer | 8,760 | 45,279 | 167,824 | 669,366 | 2,634,007 |
| Fighter | 7,295 | 39,021 | 158,287 | 648,034 | 2,571,919 |

At Transcendent, cumulative gear accounts for roughly 95–97% of these bills.
The explorer's Mobility share grows from 5,648 to 1,622,423: at baseline yields,
87 to 24,961 vines, or 257 to 73,747 bamboo, before other required families.
These are quantity comparisons with explicit stage/milestone conditions, not a
buy-every-skill total or measured farm effort. Base-machine processing columns
are reference lower bounds at the stated base rates; expanded structures and
parallel processing must be considered before interpreting late-stage duration.

Large compressed payouts are denominations, not evidence of easy supply. ATM10
9x ancient stone and terracotta pay 1,937,102,445 each; 9x smooth stone pays
387,420,489. One 9x form represents 9^9 base blocks. The base solo Crucible capacity
is 1,152,000; eight maximum-grade pylons increase it to 139,449,600. Insertion only
checks that a mapping exists, but dissolution atomically requires the entire
item's credit to fit. Oversized items remain unconsumed; transfer speed cannot
make them fit an empty reservoir. Decompression provides smaller processable
forms: 7x ancient stone/terracotta (23,914,845) fit Ascendant expansion and 8x
smooth stone (43,046,721) fits Transcendent expansion. Bound team membership can
also increase capacity; those are not solo capacities or source multipliers.
No payout reduction merely to fit a base machine is justified. ENTRY alone
describes type access and does not establish hundreds of millions of base items.

Conversion uses successive ceiling costs for fabrication and family conversion,
then a separate extraction loss with persistent fractional carry. Historical
vanilla ingot transactions retain about 47.18% at Dormant, 54.24% Awakened,
61.30% Resonant, 68.35% Ascendant and 75.41% Transcendent. These figures include
the 90% cross-family policy and 98% extraction policy, but require the grade
focus, latent carrier, machine and source essence. They do not include farming,
transfer, extraction or setup time. Native throughput limits are reported
separately. Small startup transfers should additionally inspect nugget and
Latent-focus quantities, not assume an entire high-grade ingot is required.
The report now enumerates all 36 installed-focus/form/family combinations: Latent
and five grades, nugget/ingot/block, and same-family/cross-family transactions.
Latent uses the Dormant denomination with its own configured efficiency and
throughput; it still requires an installed Latent focus.

For the fresh vanilla startup baseline, a Latent-focus cross-family nugget costs
1,456 source essence and stores 622 target essence. Extraction credits 609.56
target essence including persistent fractional carry (41.87% retained), with 14
ticks of infusion work at the saved rate. Same-family minting costs 1,310. These
quantities exclude latent-material construction and source/transfer/extraction
time; they describe a new mint and never reprice an existing carrier.

The Infuser recipe uses eight channelstone, and the initial focus uses four glass
and a diamond. Neither directly requires Mobility essence; channelstone and
carrier recipes still require latent materials and ordinary acquisition. Exact
paid requirements reject nonpositive/overflow conversion calculations, so a
zero returned by overflow handling cannot become a free carrier transaction.
Existing conservation tests cover extraction splits/compression and independent
fabrication/conversion losses. No profitable conversion loop was demonstrated.

## Prioritized continuation after this checkpoint

1. Retain the passed joint/probability regression receipts and compare the final
   native acquisition projections, especially quest-limited supplies.
2. Complete the coordinating task's remaining current pack lifecycle acceptance.
   All five inventory comparisons are complete. Vanilla and ATM10 are accepted;
   vanilla vine setup and the seventh direct Mobility renewal proof are recorded.
3. Inspect observed zeroed useful dyes and their rare flower/Sniffer/Wither sources.
   Establish factual source coverage before any price policy change.
4. Trace apparently early late-material descendants (historical netherite block
   was ENTRY) and very large compressed items against actual prerequisites and
   crucible capacity; a conservative payout total alone does not prove practical
   acquisition or processability.
5. Use the completed five-environment, five-tier spending comparisons to evaluate
   actual source feasibility after the shared unknown-input boundary is corrected.
   Resolve unknown material-credit constraints with the proposed explicit evidence
   states before claiming useful ordinary supply or affordability. No historical
   scan or unit test substitutes for fresh native lifecycle receipts.
