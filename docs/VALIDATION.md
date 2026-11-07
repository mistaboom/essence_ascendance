# Validation and runtime boundaries

This is the maintained summary of verification scope and runtime limits.
[DEVELOPMENT.md](DEVELOPMENT.md) owns build/source contracts;
[MAINTENANCE.md](MAINTENANCE.md) owns current release and cleanup status.
Feature guides remain authoritative for behavior. Dated grooming and checkpoint
reports have been consolidated here; their old measurements and abandoned
generator proposals are not current settings or pending implementation tasks.

## Completed evidence and its scope

The achievement integration passed the full four-target production gate, both
normal common checks, controlled native mechanics, separate-process persistence,
packaged startup, real two-client ID 22 transactions and native presentation.
The subsequent tab-title-only change passed affected checks and rendering on all
four targets, and the user accepted the reviewed visual result on 2026-11-07.
[Achievement acceptance](achievements/ACCEPTANCE.md) preserves exact dated hashes,
counts and the distinction between those two archive sets. A later version/package
has its own release checks; earlier exact-byte evidence is not relabeled.

The 2026-11-07 membership compatibility fix passed `checkSupportedTargets` on
all four targets. Both common suites include 482 provider, 169 binding and 281
interface assertions: arbitrary installed versions, actual API failures,
fresh-world solo recovery, exact balances, historical ownership and durable
native fallback. Isolated NeoForge 26.1.2 servers passed with FTB absent and with
the pack's Teams 26.1.2.5 / Library 26.1.2.11 binaries. The installed FTB manager
returned a complete available roster and accepted a party-free UUID as solo;
71 actual-interface fixture assertions covered controlled parties and transitions.
After deployment to the FTB Evolution 2 server, membership reported FTB Teams
`AVAILABLE` and saved-profile validation passed without regeneration. Server and
Prism jar hashes matched; settings and the saved generated profile were unchanged.
These checks do not substitute for a connected client's graphical playthrough.

| Evidence | What it establishes | Limit |
| --- | --- | --- |
| Normal `checkSupportedTargets` | Both versioned common suites, all four catalog-selected production/archive asset checks and Pack Tester regression | Compilation, invariant and packaging success do not prove every live interaction. |
| Native mechanics fixtures | Actual transformed Minecraft hooks, native item/recipe/menu/damage/movement behavior with controlled inputs | Some exit during initialization; others use disposable worlds. Read each fixture's lifecycle. |
| Packaged achievement socket runs | Two real clients, an actual machine transaction/toast, operator isolation, reconnect, dimension transfer, full advancement-manager replacement and revoke/re-earn on all four targets | One representative gameplay award; the separate all-69 grant/revoke phase is synthetic display wiring. It is not a live FTB roster-change test. |
| Native presentation captures | Actual native tree/widget/tooltip/toast rendering and geometry; selected human inspection and user acceptance | Not a human playthrough of every achievement, audio-listening approval or every GUI scale/resource pack. |
| Generated-profile native checks | Fresh generation, identical explicit rebuild, installed rank/Bonus readouts, saved-profile restart without regeneration | Readout of a rank is not native activation of every possible loadout. Historical pack snapshots do not certify a current pack revision. |
| Native source/shape tests | The selected API and transformed hook shape are present | Hook existence alone is not a successful gameplay operation. |

Earlier regression runs retained the following useful evidence without treating
their old profile values as current balance defaults:

- The October 20 runtime pass exercised bounded Glaive input and removed-player
  sequence rejection on both loaders: 2,054 Fabric / 2,089 NeoForge action
  assertions, 1,573 carrier checks and 100,032 salvage checks per loader. Native
  movement checks passed 50 / 53 assertions. These were initialized-loader
  fixtures, not joined two-client worlds. Current tests retain those regressions.
- The October 17 settings pass used isolated vanilla and normal ATM10 worlds.
  Each passed fresh generation, one semantically identical rebuild, installed
  Skill/Bonus readouts and a saved restart with no generation/report export;
  all four human settings files remained unchanged. Its 90 Skill curves yielded
  230 / 227 ranks and 118 / 125 active Bonus endpoints in those exact snapshots.
  These counts are historical fixture observations, not prescribed current values.
- The October multiversion work exercised packaged server saves/dimensions,
  registry-bound transfer/membership probes, and an isolated Fabric 26.1.2 world
  with and without JEI. Title-screen Glaive captures and initialized optional
  provider APIs remain distinct from in-world observer animation or live provider
  lifecycle acceptance. Current dependency versions come from source/catalog,
  not those old runs.

Audio has its own [maintained evidence and listening scope](audio/PASSDOWN.md).
No old audio/multiplayer/pack-generation count is a substitute for an affected
current test. Build logs and fixture worlds are disposable outputs; reusable
test sources, current contracts and compact accepted results are maintained.

## Reusable regression coverage

Tests below are maintained under `common/src/test/java/com/mistaboom/essence_ascendance`.
Normal suite registration and opt-in work are defined in `common/build.gradle`.
Native fixture sources remain separate from production archives.

| Area | Representative maintained checks | Required boundary |
| --- | --- | --- |
| Membership, machines and sharing | `MembershipProviderTest`, `MembershipNativeHookTest`, `MachineBindingTest`, `MachinePlacementTest`, `ConcurrentChannelSchedulerTest`; native `NativeMachineBindingTest`, `NativeSinglePlayerChannelTest`, `NativeAutomatedItemOwnershipTest`, `NativeInfuserStartTest` | Current menu/player/reach/physical machine and binding, settled ownership, one player source, fair source sharing, paid-work conservation, stale confirmation and automation rollback |
| Input, combat and lifecycle | `NativeGlaiveActionTest`, `NativeTwoHandedEquipmentTest`, `ProjectileNativeInterceptionTest`, native hook-shape suites and `RuntimeLifecycleTest` | Dead/removed actors cannot poison input sequence; bounded observations/restarts, spent cooldown preservation, no discarded-backlog damage, accepted damage and lifecycle cleanup |
| Carrier and salvage conservation | `NativeCarrierCompatibilityTest`, `NativeResourceChainsTest`, `NativeSalvageTest`, `DurabilityDomainTest` | Exact stored denominations, conversion equality, alternative recipes, returned containers, bounded random recovery and incompatible material bills |
| Survival and recovery | `RecoveryTest`, `SustenanceTest`, `FoodEffectOriginCodecTest`, `NativeVitalityRecoveryTest`, `NativeVitalitySustenanceTest`, `VitalityBalanceTest` | Native durability-point work, fractional carry, idle reset, real meal cadence/consumption, food-origin healing and ordinary exhaustion |
| Generated progression and native limits | `NativeBonusMechanicsTest`, `BonusTrackGeneratorTest`, `BonusTrackProgressionTest`, `SkillRankCurveTest`, `SkillRankLedgerTest`, `NativeEquipmentHeadroomTest`, `SkillSettingsConfigTest`, `BonusSettingsConfigTest` | Meaningful increments on the same context, native caps/quantization, saved payment history, settings validation and unchanged human files |
| Item/enchantment value and presentation | `EnchantmentYieldTest`, `BalanceReportsTest`, `BalanceReportLayoutTest`, `MachineUiFoundationTest`, `TooltipPresentationTest` | Shared payment/display facts, impossible or unsupported effects, bounded outputs, exact versus rounded display and one shared UI owner |
| Achievements | [69-ID traceability](achievements/STATUS.md) and [contract](achievements/CONTRACT.md) | Success attribution independent of Attunement caps, native completion authority, bounded partials, lifecycle, replay and provenance |

## Runtime contracts retained from review

**Input and transactions.** Clients request actions; server state owns costs,
eligibility, equipment, targets and damage. `WeaponActionRuntime` permits at most
16 aim observations per active action between authority ticks; the next cancels
the action while retaining spent recovery and without deferred damage/wear.
Same-tick action starts are separately bounded at 16. Dead, removed and spectator
actors reject before sequence advancement. This bounds input-amplified work;
it is not a whole-server network-flood or worst-case latency guarantee. Machine
operations must revalidate live authorization at execution, not only when a menu
opens or a request is decoded.

**Stored carriers.** `EssentiumCarrierData` preserves exact stored face value
across generated-capacity changes. New carriers record schema 1 and an ingot
denomination; valid legacy amounts remain usable without rewriting tags on read.
Compression/splitting requires compatible Essence, grade and denomination and
exact 9:1 equality. Declared partial carriers cannot mint full outputs. Unknown
schemas, wrong types, negative/overflow amounts and impossible denominations
reject. Structural validation is not cryptographic mint provenance. Current
extraction efficiency may change policy; it does not reprice stored face value.

**Salvage.** `ConstructionMaterialResolver` intersects unambiguously consumed
materials across supported native construction routes and output quantities.
Returned containers, incomplete custom ingredient displays, stack-sensitive
remainders and opaque consumption do not invent recoverable material. First-party
equipment uses the same proof rules. One-for-one self-upgrades do not erase
construction evidence, while target-producing duplication must participate.
The recycled target is never its own returned material.

`GatheringSalvageService` selects a bounded total then allocates it proportionally.
For a proven bill of `U > 1` units and recovery fraction `f`, expected recovered
units are `min(ceil(U)-1, max(1, U*f))`. This is expected per-material conservation,
not a per-roll guarantee that no individual ingredient can exceed its fractional
bill. The input is consumed before rolling; there is no rerollable preview.
When guaranteed one-item recovery and strict material loss cannot both be proved,
custom recycling rejects the item intact; ordinary disenchanting remains separate.
Two-unit bills necessarily saturate at one item at every rank. Such contexts are
not evidence of a later meaningful rank improvement. The opt-in salvage census
reports supported, saturated and unknown contexts without running on ordinary ticks.

**Recovery and food.** Restful and Metabolic Mending operate in native durability
points, not fractions of maximum durability. Their authored first/later floors
are respectively 1 / 1 point per second and 1 / 0.5 point per intrinsic food point.
Fractional work is conserved; free repair does not replenish paid Overdurability.
Actual use attempts reset Restful idleness even when efficiency avoids damage.
Automatic Feast meals honor the generated native eating duration, consume food
and deliver native effects/containers once. Inner Sustenance does not make full
reserves immune to exhaustion. Food-origin healing cannot regenerate its own food;
independent overflow retains its separate provenance. Mutually exclusive branches
must never be combined to claim a legal balance outcome.

**Native progression.** Authored meaningful minima take priority over soft power
budgets; actual native caps and whole-tick/state boundaries remain binding. Compare
each rank or completed active Bonus endpoint with the previous one on the same
equipment/context. Earlier excess or a gear upgrade cannot supply the next
increment. Saturation coalesces states or reports incompatibility rather than
inventing purchasable gains. Shield timing, bow/caster whole-tick cadence and
melee partial-charge readiness have distinct consumers. Saved history is not
silently repriced; an explicit rebuild adopts changed generation policy. Current
units, flat-damage behavior and configuration belong in
[simple balance](simple-balance.md), not old percentage-only reports.

**Enchantment evidence.** Payment, tooltips, search and Archive rows share factual
effect/level/condition resolution. Economic weights do not become native power.
Curses and impossible empty-item conditions add no invented benefit; unknown
custom effects remain unsupported. An enchanted book retains its carrier value
and a bounded combined premium. Ordinary enchanted equipment routes its existing
yield without increasing the total. A conditional effect is not assumed always on.

## Limits that remain explicit

Current generic balance uses properties, tags and published recipe evidence.
It does not infer quest access, renewable stock, acquisition difficulty, farming
time, fuel/catalyst costs or universal affordability. The abandoned acquisition
candidate's rates, zero-value policy and proposed solver changes are not current
requirements. [Simple balance](simple-balance.md) documents current estimates,
complete-recipe constraints, reversible conversion and manual settings.

Native default equipment references are bounded estimates. They can miss custom
health progression, opaque callbacks, accessories and unusual cross-piece synergy.
A Tool rule peak is not universal mining speed. Unknown custom crops, effects or
recipe semantics require an actual adapter; a permissive display cannot prove
successful runtime consumption. Conditional or sampled results are not universal
mechanical guarantees or subjective player-feel acceptance.

Real achievement socket evidence does not establish live FTB roster publication,
authority switching, all team management interactions or every third-party pipe.
Use isolated worlds and actual distinct client UUIDs for those scenarios. Runtime
rollback and serialization preserve documented state, but player/inventory/chunk/
advancement saves are not one cross-file crash-atomic journal. Forced termination
between saves is outside that guarantee. Human presentation and audio review stay
separate where the corresponding feature record does not explicitly accept them.
