# Achievement implementation contract

This document defines the current behavior of the 69 selected achievements.
[catalog.json](catalog.json) owns approved IDs/names/actions;
[STATUS.md](STATUS.md) maps them to source hooks and verification methods;
[ACCEPTANCE.md](ACCEPTANCE.md) records completed checks and their limits.
Achievements observe existing gameplay and add no progression gate, reward,
skill, item or balance change.

## Observation guarantees

The 69 IDs and approved actions are fixed by [catalog.json](catalog.json). Native
completion authority, bounded partial saves and existing gameplay balance are
preserved. Execution evidence is in [ACCEPTANCE.md](ACCEPTANCE.md).

Rejected duplicate combat-victim callbacks allocate no action group. Movement
route rejection ends the challenge but baselines the live momentum reservoir;
unchanged maximum state cannot become a fresh threshold transition after revoke.
Persistent Latent Ore generation proof is cleared on attempted native block writes
(including same-state writes; invalidation may precede cancellation) and
current-version proof at a mismatched address is discarded on load/save/query,
preventing copied A-to-B-to-A proof from becoming valid again. Valid unchanged
worldgen proof still round-trips. No historical origin is inferred for old ore.

`CommittedReceiptWindow` indexes existing live keys by committed tick. Expiration
removes only expired/future tick buckets, preserving the original actor/action
identity, [0, 200)-tick age and 4,096-key fail-closed capacity. Late delivery and
clock-rewind behavior are unchanged. Both the key map and tick index are bounded;
the index contains at most 200 live tick buckets. There is no durable receipt log.

The review world's native `advancement grant/revoke ... from
essence_ascendance:achievements/a022` commands operate on all 69 selected entries.
They remain explicit administrator display operations, not successful gameplay
facts. They neither migrate legacy Water Skiing nor reset durable lifetime sets.
No new production command hierarchy is added.

## Native presentation

The selected ID 22 (`achievements/a022`, Equivalent Exchange) owns the native tab.
The tab heading/hover label is **Essence Ascendance**;
the root achievement's tooltip/toast still uses its approved **Equivalent Exchange**
name. A client-only native tab-title hook is scoped to that exact root ID.
Vanilla/other-mod tabs keep their own labels and discovery; Minecraft hides the
tab strip when only one tab is visible. No structural achievement is added.
All 69 shipped selections are visible on the first native player advancement flush,
without earning that root. Nine thematic branches are layout, never award
prerequisites. `AchievementPresentation` augments only native discovery output for
the explicit selected roster under that root. A narrow load/reload hook queues that
root in the existing `PlayerAdvancements.rootsToUpdate`; there is no tick scan,
automatic criterion grant, new save field or duplicate completion authority.
Hidden/missing displays, unselected children and reparenting outside the selected tab
keep native discovery. Datapack overrides do not imply new historical awards.

The existing `dissolved` / `achieved` criteria and all rule/save/reset policies stay
unchanged. In particular, native admin grant/revoke and parent completion are
independent of discovery. Legacy `water_skiing` keeps its resource and `travel`
criterion, with display and parent removed. It can retain old completion but cannot
be a seventieth visible selected entry or migrate to Jumped the Shark (ID 1).

[presentation.json](presentation.json) records the complete final display and icon
provenance inventory. Exact approved names/actions remain in catalog.json. English
descriptions retain 66 approved actions verbatim; ID 119 explicitly includes the
already-implemented same-cat/between-encounter damage condition, ID 120 explicitly
requires player and victims below Y=0 in the Overworld, and ID 215 describes the
authoritative Ledger tracking instead of claiming that the server observes the
client HUD. See [PRESENTATION.md](PRESENTATION.md). No rule is weakened.

Task/goal/challenge frames use native toasts and native challenge sound behavior.
Only 35, 72 and 184 opt into chat announcements, still subject to the native
gamerule. No rewards or new audio are added. The 1.21.1-only title adapter preserves
complete selected names before native tooltip sizing; 26.1.2 already wraps full
titles. Other advancements keep their native presentation.

Every icon reuses an existing vanilla or mod ItemStack. Tiered equipment and Focus
use their existing custom-data appearance contract. No fake item, bitmap, new
pipeline, authoring dependency or personal-workbook build dependency is introduced.
The existing resource processor translates the vanilla background identifier into
26.1's ResourceTexture format; generated assets remain inside each target's build.

## Design and resource ownership

[catalog.json](catalog.json) is the single checked-in machine-readable design owner.
It contains exactly the 69 selected numeric IDs, exact final names and actions from
Pool columns A/B/Q, and the associated Engineering and Selection notes joined by ID.
The source is `Essence_Ascendance_Selected_Achievements.xlsx` in the auxiliary
`Sources/Workbooks` folder. Its SHA-256 is
`dc52451bd59c84e98e49b450789146f6a3e51f0d01d1173b3f45c271a1f9dd70`.
Full provenance and source rows are in the catalog. Normal builds use the checked-in
catalog/resources and never read this personal workbook or require a spreadsheet app.

Resources are `essence_ascendance:achievements/aNNN`, where NNN is the zero-padded
approved numeric ID, independent of title, display order or workbook row number.
For example, ID 22 is `essence_ascendance:achievements/a022`. Renaming text must not
rename a save identity. Player-facing titles match the catalog. Descriptions preserve
the approved conditions, with the explicit clarifications for IDs 119, 120 and 215
documented below. Implementation status belongs here and in STATUS, never in those strings.

Only completed implementations receive advancement resources and dispatch routes.
The selected roster is distinct from structural roots, vanilla recipe-unlock
advancements, and the legacy `essence_ascendance:water_skiing` compatibility resource.
All 69 selected rules are registered, each with its concrete consumer and native
outcome hook. The 16 labels in the
catalog are planning categories; they do not require 16 new systems. The deleted
ITEM lifetime-history capability and deleted candidate IDs must not be restored.
`AscendanceAdvancement` is the existing player-tier transition model and is unrelated
to this feature. Achievements do not gate Ascension or modify balance/rewards.

## Completed progress and native administration

Minecraft `PlayerAdvancements` is the sole authority for completed progress. Its
normal per-world advancement save owns criteria and completion dates:
`world/advancements/<player UUID>.json` on 1.21.1 and
`world/players/advancements/<player UUID>.json` on 26.1.2.
There is no second completed-ID set in `PlayerEssenceData` or a new SavedData file.
Resolve the current advancement holder by stable resource ID when awarding; do not
retain a holder across datapack reload. Award only the implementation-owned criterion
after a valid server outcome, and check native progress for idempotence.

Native `/advancement grant` deliberately grants native completion, including an
administrator's intentional bypass. It is never evidence of a successful survival
operation, a purchase, an upgrade or any other gameplay fact for downstream rules.
Native `/advancement revoke` removes native completion and must not be undone by a
duplicate receipt or a stale cached completion set. A new legitimate operation can
earn the achievement again. No custom command shadows either native command.
The default for state-derived milestones is no automatic tick/login/reload
regrant from unchanged saved state after a native revoke. Require the next legitimate
qualifying native state transition or an explicit native admin grant. Native missing
progress alone cannot distinguish a never-earned achievement from a revoked one.
An optional existing-world reconciliation entrypoint must therefore have a concrete
policy that respects native authority; if it needs durable reconciliation/revoke
metadata, design and test that contract before enabling it. The implemented tier/equipment reconciliation policies are
specified in the progression section below.

A datapack reload uses the new resource definition and native progress reload behavior.
A missing advancement disables its award route without creating synthetic completion.
Do not replay historical operations after a resource is restored. Native Minecraft
can discard progress for absent resources when it saves advancement data; third-party
packs deleting this mod's definitions are outside a promise to preserve those removed
definitions. The shipped resources keep stable criterion names and IDs across versions.
Completion survives ordinary death, respawn, logout/rejoin, dimension changes, restart,
config reload and resource reload while the definition is retained.

## Minimal successful-outcome boundary

Observe a machine's committed server transaction after both inventory consumption
and positive reservoir credit succeed. `DissolutionOutcome` carries immutable
actor/action, address, tick, exact credited micro-units, consumed input identities,
physical machine identity, positive category mask and calendar-clock facts. The
machine section below defines their capture and bounds. A machine address is not
physical-instance identity. Never infer successful consumption from insertion,
current inventory or balance.

Never retain a live entity, block entity or mutable ItemStack in durable history.
Fact producers do not depend on achievement IDs, names, translated text, UI or the
award service. Typed dispatch and bounded active observations supply reusable
facts without a global event log, scripting engine or scanning rule loop.

The ID 22 rule consumes a positive successful dissolution attributed to its own player.
Canceled, rolled-back, empty, unsupported, zero-yield, full-capacity and unauthorized
operations produce no eligible outcome. Client inventory packets and input insertion
are not completion. A loaded supported item sitting in a machine is not historical
evidence of dissolution. Achievement eligibility is independent of Attunement award
acceptance; capped players remain eligible.

Commit identities belong to the producing operation. Repeated delivery of one receipt
must not double-count or regrant a revoked award. Deduplication is bounded and scoped
to the originating server/world and actor. `CommittedReceiptWindow` retains at most
4,096 `(actor UUID, action ID)` keys per server session. Receipt age must be in
`[0, 200)` server ticks; future or expired receipts reject. Delivery prunes expired
keys; capacity exhaustion rejects without evicting a live receipt. There is no tick
scan, logout replay or durable receipt queue. `AchievementService` drops this session
on server stop; only synchronous production commits publish, so restart never loads
old outcomes. New operations receive new identities even for the same item/machine/tick.
No identity is derived solely from display name or item type. Native
loader callbacks must converge on the same common commit rather than emit two receipts.
Do not persist arbitrary historical receipts solely to create achievement telemetry.

### Machine operator policy

Use the existing `MachineBinding.operator` as explicit machine-operation attribution.
A successful authorized survival manual input insertion or explicit survival Start
establishes the current operator. Automated input may subsequently earn credit only
for that recorded operator while online, survival-eligible and still authorized for
that machine at commit time. It does not infer an actor from a nearby player.
Creative/spectator or otherwise noneligible manual mutation clears achievement credit.
Portable pickup/replacement/rebinding resets operation attribution; a legacy null
operator remains null until a qualifying interaction. Merely opening the menu,
funding a pool, placing an old portable snapshot, team membership, or holding the
source item does not confer operation credit.

The explicit operator UUID is saved with the existing binding and remains recorded
across logout/rejoin. Operations completed while that player is offline earn no
achievement and are not queued. A new operation after the operator rejoins may earn
credit if the player is then alive, survival-eligible and authorized. This distinction
preserves operation attribution without backfilling offline history.

This is a current-operator policy, not an assertion about who crafted, supplied,
owned or inserted each item. Subsequent explicit operation can change that operator;
do not silently turn it into per-item supplier history. Shared financial credit still
uses the existing funding/pool policy; achievement credit does not change accounting.
Machine operator, machine owner, funding actor, item owner, pet owner and team member
remain separate facts. Infuser rules consume the established committed job
actor/receipt when available rather than overwrite it with the machine's latest viewer.

## Partial progress, identity and lifecycle

ID 22 has no durable partial progress: it is either uncompleted or natively completed.
Do not serialize an empty achievement ledger or duplicate reservoir, tier, equipment,
skill, death-prevention or Attunement state merely to satisfy a generic framework.
Only challenges that need durable state add bounded entries.

The partial-progress serialization contract is a namespaced optional
`achievement_progress` compound under the existing server/world-scoped player record,
keyed by player UUID. Its envelope has integer `version: 1` and an `entries` compound
keyed by stable `aNNN` IDs; each implemented entry has its own integer rule version.
Absence means empty progress. No entry stores native completion. Unknown future
envelope/rule versions must be retained opaque without interpreting or resetting
them; known-version malformed entries are rejected locally and diagnosed without
discarding unrelated player state. Serialize only bounded primitive values, registry
IDs, UUIDs and dimension/position keys. Preserve unknown fields during migrations.
Each implemented entry has a tested cap; the feature sections below define its
codec, reconciliation and reset behavior.

| Boundary | Durable partial policy | Transient challenge policy |
| --- | --- | --- |
| Actual death | Retain lifetime sets/counters, including already verified cat-bound kills; clear only explicitly same-life state on the committed native death exactly once. | End fight, movement, launch, the active cat-protection fight and ordered action sessions. Player death is not an additional same-life condition for ID 119. |
| Prevented death | No death reset on Second Wind, lethal-hit interception or Spirit Walk entry. | Follow the specific skill/session transition; a successful alive Spirit Walk reform is its own outcome. |
| Respawn | Reuse the same world/player UUID record; do not reset lifetime data a second time. | Start with no old entity references, movement samples or causal session. |
| Logout/rejoin or restart | Preserve lifetime and same-life partials if the life has not ended, including verified cat-bound kills; do not manufacture offline events. | End short fight/movement/causal sessions, including the active cat-protection fight. Receipt replay remains invalid. |
| Dimension change | Preserve lifetime and same-life data unless a rule explicitly scopes it by dimension. | End movement/fight/launch sessions; a day/machine collection cannot merge different machine identities. |
| Datapack/config reload | Preserve known bounded durable facts; validate registry IDs, versions and caps without granting new historical actions. | Invalidate sessions whose relevant definitions changed; read live authoritative limits when starting again. |
| Admin revoke/regrant | Native completion changes only. Do not synthesize gameplay receipts or regrant on tick/login/reload from unchanged state. A new legitimate qualifying native transition or explicit native grant is required. | No stale receipt replay; fresh valid action can re-earn ID 22. |

Actor-scoped sequences belong to player UUID within one server/world, and an action
ID groups only that actor's accepted outcomes. Same-mob/different-mob
consumers use immutable target UUID and action weapon snapshot, mode and accepted
contact facts; bounded sequence queries are independent of achievement awards.
Changing held stacks does not create lifetime weapon identity. Existing soulbinding
`bindingId` is equipment ownership/recovery identity, not proof of an item's entire
history. A future item-scoped consumer may supply a validated durable weapon key at
that fact boundary. Do not add item UUID history or new combat skills now.

## Shared semantic policy table

The source action text in catalog.json stays exact. These policies resolve its
ambiguous terms; the feature sections specify implementation details and bounds.
`gameplay.OutcomePolicies` supplies stateless predicates, and `OutcomePolicyTest`
runs in both normal common checks. Policy-only checks are distinct from native
source-hook evidence in [STATUS.md](STATUS.md).

| Term / IDs | Decision and authoritative evidence | Rejection / reset boundary |
| --- | --- | --- |
| One fight (110; protected fights in 119) | Begins with accepted hostile health/absorption loss dealt or received. Reuse `RecentHostileCombat` facts and live `InnerSustenance.combatTimeoutTicks` (default 200 ticks). Each accepted hostile damage renews; elapsed strictly less than timeout is active. | Ends at elapsed >= timeout, actual death, logout, restart or dimension change. Swings, target proximity and canceled damage do not start/renew. No subjective fight-ended detector. |
| Nearby cat (119) | Living, loaded, tamed cat owned by this player, within inclusive 8-block feet-to-feet 3D distance at each counted kill. Select nearest at the first qualifying kill, ties by lexicographic UUID; retain that same UUID for the five kills. Count a fight only with complete server damage evidence for that cat; unknown evidence rejects. Durable state is only the selected UUID and verified count <= 5. | Accepted health/absorption damage to that cat invalidates its collection, including between fights. Temporary distance alone does not erase verified prior kills. Player death/logout ends the active fight but retains verified cat-bound kills; no inferred offline events. Cat death or ownership loss invalidates its collection. No continuous-proximity or same-player-life requirement, and no substitution with a teammate's cat. |
| Melee range (4) | Live native player `ENTITY_INTERACTION_RANGE` (ordinary base 3 blocks), measured eye-to-target-box, after a meaningful causal rod pull from initial distance >= 8. | Player walking to a stationary mob is insufficient; same target, same pull, no teleport or identity replacement. |
| Pickup range (7) | Native server item-touch volume for the rod player (ordinary unmounted player AABB inflated 1 horizontally, 0 vertically). Same hooked item starts >= 8 blocks away and must be pulled into that volume. Native pickup is confirming evidence when it occurs. | A full inventory need not prevent range success. Another player's pickup, despawn or untracked merge cannot grant. Do not demand actual inventory insertion as an extra condition absent from the final action. |
| Uninterrupted movement (1, 95, 99, 101, 102, 145, 216) | Use accepted server movement and existing movement skill state; keep the live displacement validation limits. Sprint distance is horizontal path length; rod crossing/launch distances are start-to-end horizontal displacement. Glide distance is accepted 3D path length. Exposed-water checks sample the qualifying route. | Stop, relevant mode/buff loss, mount, teleport, dimension change, actual death or logout ends the relevant session. An accepted tick with no movement ends uninterrupted sprint; absence of a packet alone is not proof of stopping. Glides end on grounding or leaving Essence Wings glide. Never clip a teleport into valid distance. |
| Real death (107, 177, 193) | Only committed native death after prevention/reformation fails advances the life boundary. Second Wind procs respect its existing cooldown. Spirit Walk success is confirmed alive reformation. Damage Ceiling recovery follows its debt component, not absolute maximum-health growth. | Zero health before prevention, intercepted lethal damage, respawn callback alone or purchase of more Vitality must not fake a death or recovery. |
| One Minecraft day (190) | Calendar day `floorDiv(overworld dayTime, 24000)`, keyed with player and one physical Crucible identity; all six categories need positive attributed credits in that day. All dimensions use the same overworld clock. | New day, including a sleep jump, resets. A backward clock change invalidates the open day session so old buckets cannot be revisited. No merging different Crucibles merely because their financial pool is shared. |
| Machine credit (22, 24, 46, 49, 51, 56, 57, 63, 73, 190, 197, 199) | Committed operation actor as specified above; keep physical machine identity distinct from reservoir identity. Multiple Infusers for 56 must draw from the same physical Crucible supply, not merely any member of the same team pool. | No nearby/team-wide awards, no failed/paid-but-uncommitted work, no owner-as-operator substitution. Network state milestones need a documented real operator transition before implementation. |
| Natural ore (126) | Requires affirmative generated-origin evidence for that block plus actual successful harvest, ore identity, underwater state and effective Aquatic Body. `NaturalOreDropService` describes ore types/dimensions, not the historical origin of individual blocks. | Positive generated-origin evidence is available from observed native worldgen ore writes, with persistent proof for newly generated Latent Ore through its existing block entity. Legacy/unknown origin, player placement, relocated proof and command feature placement reject. Never equate an ore tag or absence of a placement record with natural origin. See the gathering section for exact coverage limits. |
| Hunter's Ledger target (215) | Capture pre-fatal membership in the authoritative `UtilitySenseService` threat snapshot with effective Ledger, same dimension, valid configured range/memory and health/armor data. Existing refresh is every 5 ticks, output cap 256 targets. | Client visibility/pixels, owned skill alone or post-death lookup is insufficient. Expired/removed/out-of-range targets cannot count. Add a narrow immutable query at the sensing owner, not another threat tracker. |
| Existing-world backfill | Keep existing native completions. Current legitimate saved state may support an explicitly controlled state-derived reconciliation entrypoint only with a defined, tested native-revocation policy; there is no automatic reconciliation by default. | Missing native progress does not prove never-earned versus revoked. Never reconstruct historical crafts, purchases, launches, kills, dissolutions or repairs from current inventory, current balance, gifted equipment or admin grants. Unknown provenance stays unknown. |

Additional workbook reconciliation: ID 120's Pool action says below Y=0; Engineering
explicitly specifies the Overworld. Keep exact Pool text and implement Overworld as
the documented environmental interpretation. ID 77's three
kills belong to the same direct swipe action; delayed unrelated skill-proc deaths
must not be silently pooled. ID 61 uses functional Ascendance armor, matching the
engineering recommendation; ID 72 explicitly requires four functional pieces plus
one functional held weapon, never a sixth gathering tool. ID 128 needs successful
growth of five types, not maturation. ID 197 needs Overworld plus Nether operations,
not moving a single machine or workpiece. ID 193 intentionally has no fight boundary.

## Legacy Water Skiing compatibility

`water_skiing.json` and criterion `travel` retain the historical resource identity.
`AscendanceFishingService.trackSkiing` accumulates accepted surface movement during
rod pulling with a configured distance threshold; leaving the water resets its
counter. It does not prove 20 horizontal blocks of exposed-water displacement in
one anchored pull with route validation. It therefore never grants selected ID 1,
`Jumped the Shark`. Preserve already-earned native `travel` progress independently;
there is no ID remap or completion migration into `achievements/a001`. The legacy
display and parent are removed; its criterion and save identity remain available.
A regression must load an earned legacy criterion and confirm selected ID 1 stays
unearned. The selected catalog count remains 69 regardless of this compatibility entry.

## Development and verification

Shared logic/resources/tests belong in `common/src/main` and `common/src/test`.
Run Gradle with Java 25 and the catalog's Java 21 toolchain for 1.21.1. Preserve
editor-active 1.21.1 source, never edit projections, and serialize Gradle runs.
Normal common checks include executable achievement invariants. Native fixtures
use disposable worlds. Distinguish rule tests, loader initialization, actual world
operations, native saves, socket clients and human visual review. Reproduction
commands and evidence boundaries are in [ACCEPTANCE.md](ACCEPTANCE.md).

## Progression and current equipment

This family implements IDs **19, 26, 28, 30, 31, 32, 33, 34, 35, 36, 61, 72, 139,
166, 169, 205, 208**.
Each new advancement uses criterion `achieved`; ID 22 retains `dissolved`.
No achievement is an Ascension prerequisite, skill, reward or balance modifier.

`gameplay.ProgressionOutcome` supplies typed immutable committed facts for Skill and
Bonus purchases, vanilla crafting, positive Attunement contributions, six earned seals,
Nexus use and player Ascensions. `ProgressionOutcomes` dispatches directly after a
successful common commit; neither fact producers nor these records know achievement
identities. Existing native action IDs are reused. A root transaction may commit
several Skill/Bonus/activity details; receipt identity includes its typed detail.
`ProgressionAchievementRules` hashes this identity into a bounded receipt key and uses
the same 4,096-key/200-tick server-session policy described above. Outcomes never replay
after restart, logout or datapack restoration. No live entities or stacks are persisted.
Crafting's before/after stacks are short-lived operation-local copies, discarded at return.

ID 19 interprets **build** as successful survival BlockItem placement, followed by the
same player's successful server menu opening at that physical Nexus. Its block entity
stores one optional `survival_builder` UUID plus its recorded placement dimension and
packed position (`survival_placement_dimension`, `survival_placement_position`). Credit
requires the loaded physical address to match; invalid copied provenance is cleared
on load and before serialization/query, so a native `/clone` copy and a clone round trip
back to the source address cannot inherit the source block's placement. Old unknown placements have no builder;
removal/replacement invalidates that instance, even at the same address. A new placement
replaces attribution, and creative placement clears it. Normal native block-entity
serialization preserves the UUID through unload/restart. Crafting a block without
placing it, proximity, teammate use, and menu construction without successful opening
are insufficient. This is not an item lifetime or inventory ownership ledger.

ID 26 requires a committed zero-to-owned Skill unlock. ID 208 records distinct
successfully purchased Utility Skill IDs, including actual rank purchases but never
counts ranks as additional identities. Refund/respec does not remove lifetime facts.
Existing ownership and administrative grants do not backfill purchase history.
ID 169 requires a positive purchased maximum-health Bonus investment, a positive
isolated Bonus effect with the old tier held fixed, and an actual native maximum-health
increase after commit. Another Bonus, tier growth alone, a rejected payment, a refund,
or a purchase whose effective health remains unchanged is insufficient.

ID 28 observes a real vanilla 2x2/3x3 result operation after ingredient consumption,
including shift-click; only the first confirmed output needs reporting for this rule.
Recipe previews, empty clicks, gift/pickup/creative output and stale requests do not count.
These are committed crafts rather than loader callbacks that can be delivered twice.

ID 30 counts only positive credited Attunement activity IDs from the live registry,
within one category and chapter, capped at three methods per category and six categories.
The chapter collection alone resets on Ascension, reconciled native chapter clear/change,
or a different chapter's first positive contribution. This includes admin reset/demotion
before returning to the same chapter ID, without merging methods from the earlier episode.
Lifetime Skill facts and completed advancements remain intact. A completed
seal or maximum tier cannot produce additional positive Attunement, so it cannot invent
ID 30 credit. Other progression/equipment actions work at maximum tier without an Attunement award.

IDs 31-35 retain every tier milestone. Committed normal Ascensions grant reached
milestones independently of the partial reconciliation checkpoint. Six-seal evidence
is captured before `setTier`/the atomic Nexus transaction clears the departing chapter.
ID 36 awards when the sixth earned seal is completed in a post-Latent chapter, and the
manual Ascension snapshot also checks that preclear state. Latent auto-promotion never
counts for ID 36. Admin seal edits clear method credit, so seal progress must also have
at least one full seal's aggregate live method credit per category. An Ascension needs
the normal required number of such earned seals to publish a survival Ascension fact.
This affects observation only: normal tier eligibility, pricing and balance are unchanged.

`EquipmentSnapshot`, `EquipmentSnapshots` and `EquipmentObservations` expose current
actor-scoped facts without inventory history, durable weapon IDs, or awards. They reuse
the existing equipment mutation/reconciliation and hand-policy boundaries. Fixed six-slot
samples dispatch only on relevant changed facts/eligibility or explicit reconciliation.
No inactive achievement sessions are ticked. Functional means intact/non-fractured and
below maximum durability, with actual armor slots and gameplay's hand compatibility.
Existing gameplay does not require soulbound ownership for wield/wear; achievements add
no owner restriction. Latent equipment retains its existing mundane functional baseline.
ID 61 allows four functional Ascendance armor pieces with mixed tiers. ID 72 requires
four functional Transcendent armor pieces and a functional Transcendent **main-hand
weapon**, with compatible hands; no gathering tool or sixth item is required. ID 139
requires both Transcendent player tier and a current functional Transcendent main-hand
glaive. ID 166 uses main-hand sword plus offhand shield, as specified by Engineering.

ID 205 captures the full functional armor set and night at committed Ascension,
including a legitimate automatic Latent promotion. **Night** is explicitly the current
dimension's cycling sky clock in `[13000, 23000)` modulo 24000; a fixed-time/no-sky
dimension and daytime weather darkness reject. Native `isNight`/`isDarkOutside` also
measure darkness, so the explicit clock predicate prevents a noon thunderstorm grant.

### Versioned partial save and reconciliation

`PlayerEssenceData.achievementProgress` is stored only when nonempty under the reserved
optional `achievement_progress` compound: `version: 1`, `entries`, then `aNNN` entries
with `version: 1`. It inherits `EssenceSavedData`'s world/server and player UUID scope.
It stores no completed-ID set; Minecraft native advancement progress remains sole
completion authority. No top-level development save-schema reset is introduced.

| Entry | Bounded primitive contents | Policy |
| --- | --- | --- |
| `a030` | Chapter string <=128 characters; <=6 category IDs, each <=3 distinct activity IDs, each ID <=128 characters | Positive contributions only; chapter change resets this entry's collection |
| `a208` | <=5 distinct Utility Skill registry IDs, each <=128 characters | Lifetime; refunds/respec/admin progression reset retain it |
| `a031` | Last observed tier order, 0..5 | Reconciliation checkpoint; never completion or an Ascension prerequisite |
| `a061`, `a072`, `a139`, `a166` | One current predicate `matching` boolean each | Reconciliation checkpoint; false-to-true eligible state can earn |

Unknown future envelopes and entry versions round-trip opaque and disable only the
tracking that cannot safely interpret them. Unknown fields remain intact during updates.
Known malformed entries are diagnosed and rejected locally without discarding other
entries/player state; the invalid entry is preserved opaque and disabled. A malformed
outer value also round-trips opaque without becoming a fresh awardable checkpoint.
All reads return copies to callers; bounded writes cannot accumulate arbitrary history.

The ordered runtime lifecycle calls `AchievementService.reconcile`. The **first eligible
observation** of legitimate saved tier state can backfill reached tier milestones once.
After the durable checkpoint exists, login/respawn/dimension/reload/tick from unchanged
saved state never regrants revoked milestones. Future tier awards require a committed
normal Ascension or explicit native admin grant. Equipment initial saved-state and
false-to-true matching observations can earn; unchanged matching equipment cannot
regrant after revoke, including across save/load. Ineligible/creative/admin mutations
establish checkpoints without awarding. Mod tier/held-tier/seal commands explicitly
baseline their changes before lifecycle reconciliation.

Narrow common native command wrappers cover entity `/item replace` and `/item modify`,
`/give`, `/clear`, and `/loot replace entity`/`give`. `AdministrativeEquipmentMutation`
suppresses survival outcome eligibility while each command mutates its explicit targets,
then observes their final equipment while suppression is still active. These operations
cannot masquerade as player equipment transitions, including clearing an incompatible
offhand. This does not suppress a later actual survival equip of a gifted item. No
global player scan, command-text parser, or achievement dependency is added to the native
producer boundary. Explicit native advancement grant/revoke continues to work normally.

Historical crafts, purchases, Nexus placements and Attunement variety are never
reconstructed from saved inventory/skills/balances/tier. A saturated lifetime collection
is retained after revoke and does not rebuild itself: use native admin grant to restore
ID 208, or earn a fresh chapter's collection for ID 30. First-unlock ID 26 requires a
fresh actual zero-to-owned purchase. Native grant/revoke never emits gameplay facts.

Actual/prevented death, respawn, logout/rejoin, dimension change and restart retain
these lifetime facts and the chapter collection if the chapter has not changed. Current
equipment checkpoints follow actual equipment changes; no same-life/session challenge
is added. Existing runtime observers are cleared/forgotten on lifecycle boundaries,
while durable predicates prevent unchanged-state replay. Config/datapack reload preserves
known partials and resolves current native advancement holders; absent resources receive
no awards and restoring them does not replay history. No crash-atomic journal is added
across player/block-entity/native-advancement files; Minecraft's normal save boundaries apply.

## Committed machines and physical infrastructure

This family implements **24, 46, 49, 51, 56, 57, 63, 73, 190, 197, 199** and preserves
ID 22. All eleven new resources use criterion `achieved`; 22 retains `dissolved`.
Exact names/actions come from Pool and the ID-joined Engineering/Selection notes in
the authoritative workbook and checked-in catalog. Existing ItemStack icons and
native toast/audio are reused. No rewards, skills, balance changes, new items or art.

`DissolutionOutcome` retains its original constructor and ID22 facts and adds physical
machine UUID, immutable set of actually consumed input registry IDs (at most nine input
slots), six-bit positive credited category mask, and the overworld calendar clock.
The producer captures consumed identities from the transaction's before-image and its
committed consume counts, never from slot insertion or Essence output identities.
The category mask uses strictly positive credited amounts in `EssenceTypes.ORDERED`
order (Offense, Defense, Vitality, Mobility, Gathering, Utility). Only successful
inventory consumption plus reservoir credit publishes it. Exact dissolution credit
remains BigInteger micro-units. New `MachineOutcome.Channel` reports positive whole
Essence units after the real personal-channel transaction; eligibility does not depend
on Attunement accepting another award or on player tier.

`MachineOutcome.Infusion` reports committed job/action UUID, captured operator UUID,
dimension/address, physical Infuser and source Crucible UUIDs, recipe-result kind,
output registry ID/count, positive filled category mask and final equipment tier.
`MachineOutcomes` dispatches synchronous typed observations after successful production
commits. It owns no achievements or partial tracking. Completion retrieval, repeated
callbacks, failed/blocked jobs, a recipe preview and rollback publish no success.
Rules use the existing 4096-receipt/200-tick server-session replay policy, indexed by
fact type and actor; no inactive session is ticked or arbitrary event history saved.

### Physical identity, attribution and pending jobs

`MachineInstanceIdentity` persists optional `physical_machine` scalar UUID, dimension
and original packed position in each Crucible/Infuser block entity. It identifies a
physical installed instance separately from `MachineBinding`'s financial pool. A
legitimate legacy machine gets a new UUID lazily without historical awards. Normal
unload/restart retains it; fresh placement/portable replacement gets a new one even
at the same position. A mismatched or malformed saved address invalidates identity
and operation attribution before processing/serialization; a clone A-to-B-to-A cannot
recover the original attribution. A team rebind does not turn one physical source
into a different Crucible, but existing binding/job-credit suppression still applies.
There is no durable equipment UUID or per-item lifetime history.

Successful authorized survival manual insertion or explicit Start selects the current
operator. Creative/spectator/admin-suppressed input changes clear achievement credit.
Infusers also persist `operator_operation_credit_suppressed`, a boolean about the
current operator selection, separately from current paid-job credit. An ineligible
Start (even with no recipe) or input mutation suppresses future job attribution until
another successful eligible insertion/Start. Switching back to Survival alone cannot
convert Creative Start into a survival operator selection. This flag affects credit,
not the existing gameplay binding/accounting; an absent legacy flag preserves the
existing explicit operator record. Physical replacement/copy/rebind also suppresses
credit until a new qualifying manual selection.
Menus observe actual native before/after mutation; canceled loader provisional writes
do not establish an actor or a success. Owner, funding actor, operator and soulbound
item recipient remain separate. Proximity, team membership, opening a menu, placing
portable saved data or retrieving output never selects a credited historical actor.

Timed `InfusionJobProgress` format **3** retains its existing job identity, payment and
funding facts and adds optional version-1 operation credit. Format **2** still loads
and completes normally with unknown achievement provenance. A fresh job captures the
explicit operator at its first committed payment; later Start/viewer/operator changes
do not steal that job. The credit contains only action/operator/source UUIDs and two
source-observation flags. An explicit operator recorded while offline may finish after
rejoining; final completion requires that original actor online, alive, survival-eligible
and currently authorized. Offline completion is never queued or replayed on rejoin.
Online ineligible first payment cannot create survival provenance. Current eligibility
reuses `AttunementGameplay.eligible`, independent of contribution caps or awards.

Streamed Focus/equipment work keeps machine-local operation credit through save/load.
A legitimate explicit finishing Start can credit a partially prepared workpiece without
claiming who paid earlier upgrades. Preexisting portable/unknown contributions cannot
prove a physical source for 56. Every positive committed payment records its physical
Crucible; mixed or unknown sources disable only the same-source assertion, not an
otherwise legitimate completion. Physical replacement, copied provenance, portable
repair restoration or binding changes suppress old operation credit while retaining
the paid job and its financial metadata. Rejected payments restore credit along with
the existing inventory/payment before-image. No cross-file crash journal is added;
Minecraft's normal block/player/advancement save boundaries still apply.

### Infrastructure and result semantics

46 observes a real inactive-to-functioning Pylon relation after authorized survival
Focus installation/menu mutation or explicit changed link. The installed Focus,
current loaded link/range/pool eligibility and current active-slot selection must all
work. An active-to-active swap on the same physical source is not another activation;
a new functioning relation to another physical source may count. Placement, nearby
players and passive automatic link/config reconciliation do not create credit.

57 samples the **current** functioning active Pylon set against the current configured
active-slot limit. The limit must be positive. A legitimate operator Start on the
Crucible, positive committed dissolution, personal channel or Infuser supply payment
is running work. Stored Essence may be transferred while dissolution is stopped;
such a real positive transfer still runs the Crucible. A config/datapack reload alone
never awards; the next legitimate operation samples fresh native structure and limit.

63 accepts only a committed final equipment upgrade to Transcendent of an existing
weapon profile (sword, glaive, ranged weapon or caster); gathering tools/armor/Focus,
repairs and possession of an already upgraded item do not count. 73 accepts the rod's
committed final upgrade separately. Neither needs earlier player upgrade history.
197 is actor-scoped successful operations in Overworld and Nether; the machines,
physical sources and workpieces may differ. No location cluster/moving-machine tracker.

### Version-1 bounded machine partials and lifecycle

These entries extend the existing optional `achievement_progress` envelope under the
same world/server/player UUID record; native advancement progress remains the sole
completion authority. All entries have rule version 1, retain unknown fields, and
reject malformed known values locally. Unknown future envelopes/entries stay opaque
and disable their writer rather than resetting saved facts.

| Entry | Persisted bounded facts | Reset/regrant policy |
| --- | --- | --- |
| `a051` | Integer six-category mask 0..63 | Lifetime filled-Essentium output categories; distinct transitions only |
| `a056` | At most 64 recent physical source UUID groups, each at most 3 distinct physical Infuser UUIDs; bounded long recency sequence | Lifetime candidates; replacement changes physical identity; saturated group does not replay |
| `a190` | Overworld day-time long, at most 64 recent physical Crucible UUID groups with six-category masks; recency sequence | Calendar day change or backward clock clears groups; same physical Crucible and actor only |
| `a197` | Two-bit Overworld/Nether completion mask 0..3 | Lifetime; ignores End/custom dimensions for this rule |
| `a199` | At most 42 distinct actually consumed input registry IDs, each at most 256 characters | Lifetime; input counts and Essence categories never substitute for item identities |

The explicit collection bound for 56/190 is the **64 most recently used physical
Crucibles per player**. At capacity, a new physical source evicts the least recently
observed candidate (stable UUID tie break); a retained source is refreshed on a
successful operation. This bounds persistent state without merging sources. A player
interleaving more than 64 source candidates must finish within the retained group;
eviction never invents missing facts. Individual groups stop at the challenge threshold.
Recency sequence rebases the bounded groups instead of overflowing. The cap is a
documented implementation limit, not an unbounded financial-network collection.

190 uses `floorDiv(overworldClock,24000)` in every dimension, including negative time.
Sleep/forward jumps change calendar buckets, not a rolling elapsed 24000-tick window.
The native clock observation boundary (`ServerLevel.setDayTime` on 1.21.1,
`ServerClockManager.modifyClock` on 26.1.2) invalidates all known player day groups on
any observed backwards change, including loaded offline player records. Thus a rewind
followed by forward restoration before the next dissolution cannot revisit old buckets.
Ordinary ticking has no achievement session scan; only a backwards change visits
durable player records. Restart retains only the saved same-day partial; next actual
positive credit checks the saved clock and physical source again.

Actual/prevented death, respawn, logout/rejoin and dimension changes retain lifetime
51/56/197/199 facts. 190 also retains its partial only while the shared clock remains
in its valid day and its physical source matches. Completion, award, missing resources
or advancement commands never generate operation receipts. Native revoke does not
clear partials: duplicates and unchanged saturated collections cannot restore awards.
A new one-shot operation may re-earn24/46/49/57/63/73; 190 may collect a fresh day.
For saturated lifetime51/56/197/199 collections, use native grant to intentionally
restore completion (56 can also complete a new physical-source candidate). Restoring a
datapack/config or logging in never backfills historical work from inventory/reservoirs.

## Accepted combat and bounded causal sequences

This family implements **76, 77, 85, 120, 136, 138, 184, 206, 207, 215**. All ten
resources use the existing `achieved` criterion and real existing ItemStack icons.
There are no new rewards, combat bonuses, skills, ascension gates, decorative items,
or art. `CombatAchievementRules` consumes observations only; native attack resolution,
mitigation, collision, skill application and damage amounts remain their existing owners.

### Observation, attribution and successful-operation boundary

`gameplay.CombatOutcome` contains immutable `Damage`, `Kill`, `Freeze` and `Pacified`
facts. Its bounded scalar context records the actor UUID, target UUID and registry type,
hostility, dimension, native action identity, server commit tick and captured weapon
item/profile/tier/mode. A `Cause` carries the actor/action/weapon identity and immutable
`eligibleOrigin` evidence through a real action or attributed skill propagation.
Original survival eligibility and current actor eligibility are separate facts: an
effect applied in Creative or under administrative suppression cannot become a
survival action by switching modes before its later damage pulse or projectile impact.
An ineligible original cause remains ineligible through condition refresh/propagation
unless a new eligible action supplies a new cause. The cause is not a mutable ItemStack,
entity, weapon ownership record, or lifetime equipment history. Unknown weapon provenance is
explicit and cannot satisfy weapon-specific conditions.

`CombatOutcomes` wraps the existing Attunement native-damage scope and consumes the
existing measured health/absorption loss after nested losses have been removed.
A `Damage` fact needs native acceptance and positive actual loss. A `Kill` additionally
needs the matching completed `LivingEntity.die` transition, captured through
`SkillEffectRuntime.onLivingDeath` before target cleanup. Source and target must match
the current synchronous damage frame. Requests, predicted attacks, hit attempts,
invulnerability, cancellation, prior contributors, standalone `die` calls, and stale
later hits do not become kills. The existing native dead-state transition and bounded
victim deduplication reject repeated callbacks. Observation capture/commit failures are
logged without changing the native operation's return value or damage resolution.

The actor must be connected, alive, survival-eligible and in the target's current
level. A nearby player, teammate, pet owner or item owner is not substituted for the
actual source. Eligibility reuses Attunement's server authority but does not depend
on its contribution/award result: capped players remain eligible. The narrow
`AdministrativeDamageCommandMixin` suppresses outcomes for the converged native
`/damage` operation even when an administrator supplies a survival player as `by`.
The command still deals its requested native damage. `/kill` has no accepted player
damage frame and cannot fabricate a weapon kill. Native advancement commands remain
intentional completion administration, not historical gameplay facts.

Glaive action acceptance captures its existing offense snapshot and the action's
STAB/SWIPE identity. Each collision passes the solved blade-front contact distance,
not configured maximum reach or a later eye-to-target sample. Changing held items
cannot replace that snapshot; the existing action validity/cancellation rules remain
in force. Ordinary melee captures its accepted attack snapshot through the full
successful-skill callback scope. Kindling/Grim Harvest periodic damage and bounded
propagation retain their actual originating cause; refreshing a condition with a new
hit replaces its cause instead of attributing that later hit to an old swipe.
Combustion achievement attribution additionally requires eligible provenance for its
consumed burning prerequisite. If another actor or environmental damage delivers the seed victim's final blow, the original
ignition cause owns that player's subsequent Combustion branch; it does not borrow
the other actor's damage frame or the ignition owner's current held item. When the
ignition owner delivers the final blow, that owner's current accepted cause is used,
while the burning prerequisite must still be eligible. A Creative/admin ignition
cannot be laundered through a later survival seed death.

`PropagationBudget` exposes a provenance view for such a causal branch. The view
shares the actual visited-target set, affected-target count and existing limits with
its original budget; changing observation provenance does not clone or replenish
gameplay propagation capacity. Its immutable per-branch cause can reject one branch
without changing legitimate sibling provenance. Missing offensive-proc provenance fails closed for
combat achievement facts. Verified defensive reflection retains its separately
attributed defensive actor and unknown weapon snapshot; it cannot inherit an offensive
weapon cause. Unrelated environmental damage does not manufacture an action identity.

Supported damaging projectiles capture the actual native launch weapon. The original
owner, life and dimension must still satisfy `ProjectileRuntime.validOwnership`, and
a redirected shot cannot inherit the former shooter's weapon history or eligibility.
A successful `ProjectileInterceptionService` redirect captures a new defensive actor,
action identity, `Weapon.UNKNOWN` and original eligibility at the defense commit;
the returned-shot proc carries that new cause. Switching from Creative to Survival
after redirect cannot rehabilitate an ineligible defensive cause. A known eligible
defensive final blow can satisfy generic kill conditions, but never a weapon-specific
condition; a restored returned shot with unknown defense provenance rejects. This observation
provenance is session-only: restored projectiles do not acquire the player's current
held weapon. Existing projectile saves and gameplay behavior remain intact, but restored
or unsupported projectiles with missing launch provenance produce **no combat achievement
facts**, including generic Ledger/Wither credit. Failing closed for all such facts avoids
turning a Creative/admin launch into a survival kill after reload. Genuine live supported
Arrow/Caster launches retain their original eligibility and weapon snapshot through
release, flight and item swaps. No historical launch/kill backfill is inferred from
inventory or an entity save.

### Concrete rule semantics

| ID | Successful condition and explicit interpretation |
| --- | --- |
| 76 | A confirmed hostile death whose causal weapon snapshot is a Glaive STAB. First qualifying completion is native-idempotent. |
| 77 | The third distinct hostile death belonging to one Glaive SWIPE cause within the bounded action window. Three contacts, three separate swipes or repeated death callbacks do not count. Proven delayed skill-proc deaths can join that cause; direct-only grouping is not imposed. The fourth death does not replay the threshold after a revoke. |
| 85 | Frostbite's actual chilled-to-fully-frozen transition, after the configured full-chill threshold installs an active frozen state on a living hostile. Partial chill, ordinary slow, an attack attempt and refreshing an already frozen target do not count. |
| 120 | Ten confirmed hostile kills with the originating action's Ascendance sword, glaive, ranged weapon or caster; both actor and victim below Y=0, in the Overworld, with the actor's feet slot empty at final damage. Engineering's Overworld recommendation is adopted explicitly. Pool's mob elevation and Engineering's player elevation are both retained; this conjunction is intentional. |
| 136 | Positive accepted Glaive damage at strict distance >4 and current action weapon tier >=Dormant. No previous personal upgrade receipt is needed. |
| 138 | Positive accepted Glaive damage to a hostile at strict distance >9. |
| 184 | The credited Wither final blow while Desperation is effective and actually active under its existing multiplier/HUD predicate (>1.000001), and the actor's health fraction is strictly below 0.10. Capture before the final native health write, before on-kill healing. Exactly 10% and configured zero maximum bonus reject. No weapon restriction or exclusive damage-contributor requirement is added. |
| 206 | Positive accepted Glaive damage at inclusive distance >=8. As in the approved action, 136/206 do not add 138's hostile-only restriction. |
| 207 | Successful Sanctuary pacification of the specific pillager that was targeting this player, with affirmative no-own-damage evidence for that encounter. Another player's target, merely nearby pillager, canceled targeting request, failed target clear and unknown encounter evidence reject. |
| 215 | Five hostile deaths whose targets were eligible members of the authoritative pre-fatal Hunter's Ledger snapshot, with health/maximum-health/armor data. Current effective Ledger and Threat Sense, same dimension, live configured range, refresh age and memory apply; merely owning Ledger is insufficient. |

The checked-in catalog retains the exact approved Pool action text. The ID 120 player
description uses the workbook's Engineering implementation text, adding **in the
Overworld**. ID 215 honestly describes the observable condition as **Defeat five hostile
mobs whose health and armor information is being tracked by Hunter's Ledger.** Server
snapshot membership is verified; client rendering, screen pixels and visible HUD state
are not claimed or accepted as authority. `AchievementFoundationTest` explicitly checks
these two documented description exceptions while retaining exact approved titles.

Hunter's Ledger reuses `UtilitySenseService`'s existing snapshot, refreshed every five
ticks and capped at 256 output targets. The narrow query does not force an extra scan,
create a new threat tracker, or accept a client assertion. A target in an internal
observation map but outside the actual capped output list does not qualify. It also
rejects removed, expired, out-of-range and other-dimension targets before fatal damage.

Sanctuary's reusable `SanctuaryEncounterTracker` stores scalar evidence for at most
256 pillagers per actor. An actual post-setter target acquisition starts the encounter;
continuous targeting retains any accepted own-damage flag. Target loss ends it, with
200 ticks of recent damage retained to cover retaliation/reacquisition. A fresh target
acquisition after that retention can start a new encounter. Active encounters expire
at 12,000 ticks and then fail closed until another confirmed acquisition; capacity
exhaustion does not invent a clean encounter. Target removal, actor lifecycle reset
and relevant runtime/config reset clear this transient evidence. Existing Sanctuary
pacification delay, range and damage behavior are unchanged.

### Reusable bounded query contract

`CombatOutcomes.sequences(server)` exposes the server/world-scoped `CombatSequences`
service. `killed` accepts only confirmed-death contexts; per-action distinct-victim
sets supply the causal hostile-kill count, while a separate ordered deque supplies
`recent`, `repeatedType` and `distinctTypes`. Queries are achievement-independent,
return immutable facts, and never call the award service. Focused consumer tests show
repeated entity types and distinct entity-type suffixes without adding a gameplay skill.

The service retains at most **256 actors**, **64 action groups per actor**, **64 victim
UUIDs per action**, and **32 ordered kills per actor**. Live group/capacity overflow
rejects rather than evicting a live victim deduplication record. Ordered history keeps
its newest 32 facts. Native synchronous observation-commit order supplies the explicit
increasing ordinal, including same-tick kills. A nested proc commits before its enclosing
damage frame publishes the outer death, so it appears first; there is no retrospective
timestamp sort. Action victim membership is not treated as sequence order. One victim
is deduplicated across the actor's retained action groups.

Action groups expire at **200 ticks from their first accepted death**; ordered facts
expire at age >=200 ticks, and an actor idle for 200 ticks is removed. Future timestamps
and backwards clocks reject/invalidate the relevant transient history. Delivery/query
prunes bounded active records; no inactive-player tick scan or unbounded event log is
introduced. A very long configured damage-over-time effect can outlive an earlier
same-action group: later deaths retain their true cause but cannot recover expired
counts. This is a deliberate bounded-window limitation, not a claim to retain an
entire lifetime of kills. The ordinary default 100-tick Kindling duration fits within
that window; arbitrary larger configuration values do not enlarge history.

Identity is **actor/action scoped**, not item scoped. Current weapon item/profile/tier
facts survive an item swap only as immutable action observations; matching item types
or soulbinding IDs do not prove physical lifetime identity. A future validated durable
weapon identity provider can extend `CombatOutcome.Cause`/the accepted snapshot boundary
without changing achievement completion or adding a per-item history now.

### Durable counters and lifecycle

`a120` and `a215` add rule-version-1 entries to the existing optional
`achievement_progress` envelope: one integer `count` bounded to 0..10 and 0..5,
respectively. They share existing server/world/player-UUID scope and store no completion
set. Only a fresh confirmed qualifying kill increments; only the threshold transition
awards. Future envelope/rule versions remain opaque, malformed known counts are preserved
and disabled locally, and unknown entry fields survive updates. Old worlds start with
no historical kill inference. Other player data and other achievement entries are preserved.

| Boundary | Durable lifetime counters/native completion | Transient combat and encounter history |
| --- | --- | --- |
| Actual death | Retain both counters and native completion. | Clear actor sequences, action runtime, owned effect sessions and encounter evidence once at committed death. |
| Prevented death | No death reset or fabricated kill. | Keep sessions subject to their existing runtime transitions/expiry. |
| Respawn | Same world/player UUID record; no lifetime reset. | Start without the previous entity/action/encounter session. |
| Logout/rejoin | Preserve partials and native completion; no offline replay. | End sessions and projectile owner life; fresh authoritative observations only. |
| Dimension change | Preserve lifetime counts; every 120 event still requires Overworld. | End actor action/sequence/encounter state; do not merge dimensions. |
| Restart | Preserve versioned counters and native advancement files. | No history or receipt replay; restored projectile launch provenance is unknown and cannot produce any combat achievement fact. |
| Datapack/config reload | Preserve known partials; resolve current advancement resources on award. Missing definitions never queue historical grants. | Relevant runtime/lifecycle reset invalidates old sessions; new events use current settings. |
| Admin revoke/regrant | Only native completion changes. Saturated 120/215 counters cannot regrant on login, tick, reload or new excess kills; use explicit native grant to restore them. | Duplicate receipts never replay. Fresh one-shot outcomes or a fresh qualifying three-kill action can re-earn their corresponding rules. |

Combat achievement delivery uses the existing bounded `CommittedReceiptWindow`: at
most 4,096 actor/event-kind/action/victim receipt keys per server, age [0,200), failing
closed at capacity. Different event kinds and victims in one action remain distinct;
callbacks for the same delivered fact deduplicate. Server stop discards runtime maps.
Native completion remains the only award authority; these counters and query sessions
never gate Ascension or encode an additional completed-ID ledger.

## Rod causality and movement sessions

This family implements **1, 2, 3, 4, 5, 7, 95, 99, 100, 101, 102, 145, 201, 216**.
All fourteen native definitions use `achieved`, exact approved title/action text, existing
ItemStack icons and normal native toast/audio. The legacy `essence_ascendance:water_skiing`
resource and its `travel` criterion remain independent. There are no new rewards, skills,
damage/speed/rod-force changes or save fields.

### Reusable observations and identity

`MovementSessionTracker` maintains a scalar start, accepted endpoint, horizontal and 3D
path, horizontal displacement and vertical displacement. It separates those quantities
instead of making unrelated challenges share thresholds. Duplicate server-tick samples
do not add distance. A successful rod release may checkpoint its final validated same-tick
endpoint for displacement, without adding path distance twice. Nonfinite positions, dimension/clock discontinuity or a step above
the live movement-validation maximum end the trajectory, without clipping the jump into
valid distance. Accepted native movement may supply the bent packet path independently
of its endpoint chord. Accumulated paths saturate at 1,000,000 blocks, a storage limit.

`MovementSessions` maintains ordered glide/airborne, sprint, supported-water and Rush
checkpoints. `MovementOutcomes` observes existing successful Wings start, positive Vector
Boost velocity write, real Rush death activation and accepted native movement. Input
intent alone does not add distance. `MovementOutcome` carries immutable typed scalar
checkpoints to the indexed `MovementAchievementRules` consumer. A native movement candidate
for Momentum Vault commits only after the movement handler accepts its endpoint. It
requires actual solid obstacle clearance exceeding the ordinary step height, the effective
Vault step modifier, grounded start, supported endpoint and normal collision/headroom.
Jumping, readiness alone, ordinary stairs and packet correction do not count.

`RodOutcomes` observes a positive force after the unchanged rod operation succeeds;
`RodTrajectoryTracker` shares the scalar trajectory sampler. It records actor, hook,
target UUID, dimension, initial range/position and action identity, never mutable stacks
or live targets. Resolve the same loaded UUID for each subsequent observation. Failed or
zero-force attempts never create a session. Survival eligibility is captured at cast,
attachment and first positive force; a Creative cast/hook cannot become survival credit
merely by switching mode. A competing accepted pull invalidates the previous
target attribution even when its new operator is ineligible. No nearby/team/owner credit
is inferred. This history is actor/action scoped, not persistent per-item history.

### Rule semantics

| IDs | Successful evidence and endpoint |
| --- | --- |
| 1 | During one held anchored pull, at least 20 horizontal blocks of displacement across a contiguous exposed-water route. Half-block route samples project onto loaded `WORLD_SURFACE` top-water columns and require the actor above the fluid surface. An airborne grapple over water qualifies; covered/solid/waterlogged/submerged columns do not. A nonwater segment clears that route checkpoint. There is no bank, island, shark or surface-support requirement, and cumulative legacy skiing never substitutes. |
| 2 | A positively rod-launched hostile with airborne/upward-launch evidence suffers accepted positive native FALL damage that commits its actual death while the bounded cause remains valid. The existing native damage frame supplies both loss and committed death. Other accepted damage, surviving landing, water entry, teleport, unload/removal or a competing pull invalidates the cause. Administrative `/damage`, standalone `die`, death prevention and an unrelated later kill cannot fabricate it. |
| 3 | Same released hostile's horizontal displacement from release, ending at landing, water, invalidation or timeout. Reeling distance before release and accumulated wandering do not contribute. |
| 4, 7 | Same target begins at least eight blocks away (hostile at force start; item at hook attachment), receives a meaningful causal pull and reaches current native melee/pickup range. Meaningful movement requires more than 0.1 block of target displacement toward the original pull direction; actor approach alone fails. The player may move during the pull. Melee uses live `ENTITY_INTERACTION_RANGE`, eye-to-box. Item range uses native player AABB inflated one horizontally, zero vertically; full inventory does not add a pickup requirement. Native item touch can confirm range before removal; another actor's pickup, merge, despawn or replacement cannot substitute. Residual pulled motion after release may reach range. An item's ground/water contact does not invalidate range attribution; a released hostile's surviving landing/water ends its session, while a held hostile may continue reeling after losing launch causality. |
| 5 | The anchored grapple transitions through airborne movement to actual supported dry landing at least ten blocks above force start. The underfoot support must be real non-liquid block collision with empty collision context, so Surface Tension's synthetic fluid support and midair height alone fail. Released ballistic arrival may finish the same grapple; no building classification is required. |
| 95, 99 | One owned Essence Wings glide, three distinct successful Vector Boost activations for 95; at least 1,000 accepted 3D path blocks plus a real boost for 99. Failed requests, normal Elytra, ability flight, vehicle travel and separate glides cannot pool. |
| 100, 216 | Effective Running Momentum uses its current configured maximum speed bonus. A fresh transition to that positive maximum earns 100. For 216, accumulate 500 accepted horizontal sprint-path blocks while the effective bonus remains positive; maximum momentum is not an additional condition. |
| 101 | Five distinct successful physical obstacle vaults during one uninterrupted qualifying sprint. Repeated callbacks/the same physical obstacle do not inflate the collection. |
| 102 | At least 32 accepted horizontal sprint-path blocks with effective Surface Tension and real native support at the exposed WATER surface. Quarter-block route samples check fluid height; lava, submerged/covered water, ordinary support elsewhere, riding and flight do not substitute. |
| 145 | Real owned Wings start establishes an airborne checkpoint, then an accepted glaive blade contact before grounding. The checkpoint can survive leaving Wings while still airborne; delayed elemental proc damage is not a blade hit. Teleport, water, riding, flight substitution and lifecycle reset end it. |
| 201 | A real attributed kill activates Rush, then intentional accepted sprint movement occurs strictly before the captured/live protection expiry. Merely owning Rush, a kill notification without activation, an expired timer or motion after an administrative kill cannot satisfy it. |

An accepted tick with zero horizontal movement stops an uninterrupted sprint. Missing
packets alone do not prove a stop; they add no distance. Buff loss ends the momentum
sprint, unsupported/nonwater route ends surface running, and grounding/leaving owned
Wings ends the glide. Sprint jumps may retain the live buff under normal gameplay.
No tracker changes physics, native movement acceptance, force, damage or skill tuning.

`PlayerMotionTracker` adds observer-only cause metadata for an actual grounded ordinary
sprint jump. Its existing `forcedUntil` and `Sample.forced` gameplay behavior remain intact.
The achievement observer distinguishes that jump from external knockback, rod/piston
transport and extra skill impulses applied outside the native jump call. External forces
cannot use the jump classification to preserve a qualifying momentum sprint.

Ineligible movement and Rush fills establish a transient current
momentum baseline without emitting success facts. Challenges and captured Rush activation
are cleared; an unchanged full reservoir cannot become ID100 by returning to Survival or
leaving an administrative command scope. Existing gameplay may still fill/retain that
reservoir. A subsequent legitimate below-cap-to-cap transition can earn normally. This
baseline is runtime observation metadata, not saved progress or a gameplay restriction.
If actor capacity has overflowed, one bounded server flag makes a later newly allocated
observer baseline any already-full reservoir once. Unknown history cannot become a new
award when a slot opens. A later observed below-cap transition, or a legitimate Rush fill
whose actual pre-fill amount was below maximum, can establish fresh evidence. Rush while
already full cannot replay a revoked maximum achievement. The flag clears on server stop;
no collection of rejected actor UUIDs is retained.
Native generic `/damage ... by player` is not a qualifying Rush source under the existing
primary-attack/projectile attribution policy and remains a negative case. A separate
controlled suppression-scope native attack tests the baseline boundary; actual Creative
running and Creative native kills exercise real ineligible gameplay fills.

Rod launch origins also record the target's native entity tick. A stale grounded flag
before the first physical step does not cancel a genuine opening launch; a surviving
grounded physical step does cancel launch causality even if airborne departure was never
sampled. Actual changed native knockback, push and explosion impulses invalidate the
target's old rod cause; canceled or no-op impulses do not. These narrow native boundaries
do not attempt to interpret arbitrary third-party direct velocity writes as new launches.
Every subsequent positive rod force also rechecks original/current eligibility. A brief
Creative force followed by Survival before the next actor tick invalidates the pull.
Actual item merge commits invalidate both UUIDs, including the surviving receiver. Actual
partial pickup by another player also invalidates a surviving UUID; a full-inventory
unsuccessful touch does not. Administrative damage invalidates the cause even when its
damage type is FALL and the target survives that command.

### Bounds, lifecycle, saves and administration

Each server owns at most 256 rod sessions and 256 movement actors; exhausted capacity
fails closed. Rod sessions expire at age **200 ticks from first positive force**, exclusive,
and require consecutive target samples. Released causes retain that original deadline.
Active sessions alone perform route/target work. Water route sampling fails closed above
1,024 subdivisions. Movement retains at most 1,024 distinct obstacles/boost count and 64
pending obstacle candidates; the threshold consumer checkpoints each session's crossings.
Rod crossing checkpoints retain at most 1,536 action/kind keys for 200 ticks. Both consumers
also use the existing 4,096-receipt, age `[0,200)` delivery policy. No offline replay queue,
unbounded event log, live durable target or item lifetime ledger is introduced.

Actual death, respawn, logout/rejoin, dimension transition, server restart and config
refresh discard transient rod/movement sessions. Prevented death does not invoke an actual
death reset, but physical grounding/water/mode/buff changes still end their applicable
challenge. Native coordinate and command teleports also invalidate rod target identity,
including short same-dimension jumps; actor teleport invalidates movement through the
existing accepted-movement discontinuity owner. Entity unload/removal/merge invalidates
rod target history. A committed death is observed at accepted damage return so premature
native entity removal cannot erase its legitimate fatal-fall receipt.

Datapack/native advancement reload preserves scalar sessions while gameplay definitions
are unchanged and resolves fresh native holders on award; missing definitions never queue
awards. Config/balance reload resets sessions before using current tuning. Native revoke
changes completion only: duplicate receipts, later checkpoints from an already-consumed
crossing and unchanged maximum momentum do not restore it. A fresh session/maximum
transition may re-earn; explicit native regrant remains available. No advancement command
produces a movement fact. All completed progress remains Minecraft's native authority;
these transient rules add no partial-state writer or save-version change.

## Confirmed survival and recovery

This family implements **107, 110, 114, 176, 177, 193, 200 and 209**.
All eight resources use the stable `achieved` criterion, exact final Pool text,
existing ItemStack icons and native presentation. There are no new skills, rewards,
damage/health/cooldown formulas, progression gates or item lifetime histories.

### Successful facts and causal boundaries

`SurvivalOutcome` and `SurvivalOutcomes` expose small typed actor-scoped facts for
confirmed Second Wind, alive Spirit reformation, completed automatic meals,
confirmed mirrored effects, own capacity-debt transitions, configured adaptation
and positive equipment repairs. Native producers contain no achievement IDs or
award calls. Facts carry server/world-local player UUID, dimension, operation
identity and commit tick; entities and mutable stacks never enter durable history.
Eligibility is independent of Attunement acceptance, including at its cap.

| IDs | Native success and rule endpoint |
| --- | --- |
| 107 | Spirit Walk captures eligible activation and emits only after its existing reformation finishes with the player alive. Entry, interception alone, canceled/failed reform, death and runtime removal do not qualify. There is no additional survival timer. |
| 110 | Two confirmed consumed automatic Feast Reflex meals in one hostile encounter. `RecentHostileCombat` owns a distinct accepted-health/absorption-loss checkpoint, using the live Inner Sustenance combat timeout (default 200 ticks, exclusive). Routed debt/food with no actual loss may still lock existing sustenance gameplay but cannot start this encounter. Accepted hostile damage renews it; ineligible damage invalidates it. Manual food, a no-op finish, failed selection and proximity do not count. The existing encounter owns a saturating 0..2 ordinal and emits only the first and second completions; further meals cannot replay a revoked completion. |
| 114 | Status Mirror must actually change the harmful-effect chain on the original source witch, or cause accepted health/absorption loss there for an instant harmful effect. Capture and recheck the responsible source at the native application, including projectile ownership. The exact source/target UUIDs must match. Pure State, blocking, failed/resisted transfers, a different witch and an unrelated later effect reject. Cause lifetime is the one synchronous application stack, expiring on return with existing `finally` cleanup; no witch history or delayed queue is retained. |
| 176, 177 | A successful Second Wind is published only after native setHealth actually preserves life. Its existing group cooldown, surviving health, healing, debt purge and effects remain authoritative. First proc earns 176; two committed procs in the same real life earn 177. A prevented death never resets that count. |
| 193 | Arm an episode after Damage Ceiling's own debt increases and its committed attribute reconciliation actually reduces maximum-health capacity. Complete only when the existing debt itself naturally transitions from positive to zero while alive. More Vitality, health healing, absolute maximum growth, old unobserved debt, death clearing and admin mutation cannot substitute. No hypothetical lethal-hit test or separate fight-ended detector is added. Existing debt recovery remains independent of current skill selection. |
| 200 | One damage type's accepted Adaptive Guard sequence crosses its positive effective configured resistance maximum. The live maximum derives from maximum stacks, minimum hits and resistance per stack, with origin eligibility retained across that sequence. No hardcoded generated balance endpoint, unchanged cap tick or ineligible precharge qualifies. |
| 209 | A completed food consumption actually reduces equipment damage through Metabolic Mending below Y=0. Collect the consumed food's registry ID only after positive actual repair. Undamaged gear, zero/negative repair, a no-op finish, five copies of one food and consumption at/above zero reject. No dimension restriction is added. |

Consumption observations retain a copied source only on the synchronous native
call stack and verify source/result item/count consumption. The completed meal
and repair facts are separate typed commits; nested native completion callbacks
cannot multiply either operation. Adaptive Guard retains at most 64 scalar
prepare/finish origins per existing actor state, expiring after one tick; overflow
fails closed for achievement credit. Gameplay state, damage resolution and cooldowns
continue through their existing owners.

### Bounded saves, lifecycle and native administration

The existing version-1 `achievement_progress` envelope gains three version-1 entries:

| Entry | Saved fields and bound | Reset policy |
| --- | --- | --- |
| `a177` | Integer `count`, inclusive 0..2 | Actual committed death alone resets to zero. |
| `a193` | Boolean `active`, one armed own-debt episode | Actual death or ineligible debt mutation clears; positive-to-zero eligible recovery consumes the episode. |
| `a209` | Compound `foods`, at most five canonical registry IDs (each <=256 characters) with true membership values | Lifetime collection; ordinary death and respec retain it. |

Unknown future envelope/entry versions and fields remain opaque and preserved;
malformed known entries reject locally without resetting unrelated data. Missing
entries mean no history. No completion is duplicated in this save: Minecraft's
advancement progress remains the sole completed authority. A full success-receipt
window cannot suppress the native death reset or ineligible debt invalidation.
Debt observations during Creative/admin/ineligible operations only disarm existing
episode evidence: they never arm, award or create an empty partial entry. This
prevents an old arm surviving an ineligible recovery and laundering later admin debt.
The existing committed-death hook
deduplicates loader/native callbacks; its committed native boundary is authoritative
even when an integration invokes native death with positive health. Delivery is
synchronous and there is no deferred death queue. Successful outcomes use the bounded 4,096-receipt, age `[0,200)` guard;
the rule kind is included in receipt identity.

Logout/rejoin, dimension transitions, restart and runtime/config refresh preserve
`a177`, `a193` and `a209`, as well as the existing saved debt and ability cooldowns.
They discard short encounters, Spirit windows and transient cause/adaptation state
through existing runtime owners. Respawn does not reset a second time. Datapack
reload resolves fresh advancement definitions without backfilling historical
survival actions; missing resources do not queue awards. Old worlds receive no
invented Second Wind, food, reflection or debt episode history.

Native grant/revoke changes completion only. Duplicate receipts, a saturated food
collection, a third meal in the consumed encounter, an unchanged adaptation cap,
login and reload do not restore revoked progress. Fresh valid transitions or a
new same-life threshold after actual death may re-earn under each rule's policy.
The lifetime food collection remains saturated after revoke, consistent with the
existing lifetime collections; explicit native regrant remains available.

## Gathering, farming and companions

This family implements **118, 119, 122, 126, 128, 131, 182, 204**. Exact final titles/actions remain in catalog.json
and native translations. Criterion `achieved`, native advancement authority and normal
toast/audio remain unchanged. No skill, reward, damage/stat tuning, progression gate,
bitmap, decorative item or persistent item history is introduced.

### Successful operations and reusable facts

`CompanionOutcome(s)`, `FarmingOutcome(s)` and `GatheringOutcome(s)` expose immutable,
actor-scoped successful operations independently of the award service and Attunement
acceptance. These carry UUIDs, registry IDs, ticks, scalar snapshots and operation IDs,
never mutable stacks or live entities in history. Existing native damage, ownership,
skill growth, loot delivery and successful harvest owners supply the facts. The shared
`CommittedItemDrops` synchronous scope observes actual accepted native item-entity spawns,
so a nonempty hypothetical loot list cannot fabricate a delivered bonus ore.

`CommittedHarvest` supplies the actual removal receipt inside the existing native
harvest wrapper. Minecraft's outer destroyBlock return can be true even when removal
fails. Fabric/common observes the exact ServerLevel.removeBlock result; the narrow
NeoForge adapter observes BlockState.onDestroyedByPlayer in its existing private
removal helper (the modern signature adds ItemStack). Both verify the same original
non-air source and immediate physical state change before later destroy/replant
callbacks. Failed removal cannot advance Mining Momentum, trigger Nature's Boon,
contribute Attunement or publish successful harvest facts. Native return values and
successful-operation formulas remain unchanged. The scoped receipt closes in finally,
retains no durable world/entity history and handles nested operations independently.

| ID | Actual rule and semantic boundary |
| --- | --- |
| 118 | Confirmed hostile native death caused by the Bonded Companion-empowered creature. Its real eligible owner, same at contact and commit, receives credit. Empowerment must be effective at both boundaries. A nearby player, item owner, teammate, another pet owner, attempted attack or standalone death callback cannot substitute. |
| 119 | At the first qualifying player hostile kill, select their living loaded tamed cat within inclusive eight-block 3D feet distance, nearest then lexicographic UUID. Bind exactly that UUID for all five kills; two cats cannot double count. Each qualifying encounter needs complete server loaded/damage observation from its accepted hostile-loss start. Actual cat health/absorption loss invalidates the collection, **including between encounters and while its owner is offline/ineligible**. Ownership loss and destructive removal/death also invalidate immediately. Modern tame uses setOwner(LivingEntity) independently of setOwnerReference, so both native setters are observed. Unknown/absent/unloaded cat evidence rejects a kill without substituting another cat. Temporary distance alone retains verified kills. Damage ends the collection, allowing a new nearest-cat challenge on a later fresh kill. |
| 122 | Observe the real effective positive maximum Mining Momentum crossing, then count twenty additional successfully mined blocks in the same actor-scoped rhythm. The cap-reaching block is excluded. Skill loss, ordinary timeout/reset, lifecycle or config refresh ends continuation. Ineligible precharge cannot become a survival maximum by mode switching. No hardcoded skill rank replaces the live cap. |
| 126 | Successful native ore harvest with correct tool, underwater player state and effective Aquatic Body, plus affirmative generated origin. The existing ore catalog/placed-block tracker alone cannot prove origin. Observed native OreFeature section writes and WorldGenRegion block writes supply bounded current-block proof for ordinary ores. Latent Ore worldgen additionally supplies version-1 address/dimension-bound persistent proof on its existing block entity. Unknown/legacy blocks, player-placed ore, moved proof and ServerLevel command-feature placement fail closed. Aquatic Body adds no breathing or new mining bonus. |
| 128 | Actual positive Verdant Stride-caused crop age advancement, including native torchflower completion, adds the original crop block registry ID to a five-type lifetime set. Maturity is unnecessary. Background random ticks, external bonemeal, proximity, failed/no-op growth, decreasing age, disappearance and eligible saplings cannot count. |
| 131 | A genuine positive Pocket Nets reward delivered to inventory or an accepted native dropped-item spawn while the player's native `isSwimming()` is true. Existing water+sprint skill activation is retained, but a reward without actual swimming cannot meet this achievement. No requested/empty/canceled delivery counts. |
| 182 | Actual Nature's Boon bonus ore native drops in each of exactly Overworld, Nether and End set three lifetime receipt bits. Dimension visits, ore inventory, missing rewards and custom dimensions do not substitute. No natural-origin requirement is added to this distinct bonus-reward rule. |
| 204 | The actual native farmland fallOn path reaches its selected turnToDirt operation, Verdant Stride prevents that operation, a supported mature crop is above it, and immediate exact farmland/crop states remain intact. Ordinary landing, immature crops, proximity or generic protection readiness do not count. No jump history or MOVE dependency and no cabbage assets are added. |

### Provenance and configuration coverage limits

`GeneratedOreObservations` observes successful WorldGenRegion ore writes and the native
OreFeature bulk section setter (which bypasses region.setBlock for ordinary ores). It
records only a current block identity at its dimension/position, with affirmative worldgen
context. It never records ore merely because a chunk loaded or a placement was not seen.
Every Level.setBlock attempt invalidates proof first, including same-state writes; native
chunk unload clears that chunk. Successful non-ore worldgen writes also clear old proof.
The synchronized worker-thread-safe owner retains at most 256 recent chunks with at most
1,024 ore positions each (262,144 total), evicts the least recently used chunk and expires
30 minutes of monotonic inactivity on access. Lost/evicted/expired evidence becomes unknown.
No tick scan or unbounded world history is added. Vanilla ores have explicit identity
fallbacks; common ore tags extend type coverage without supplying origin evidence.

`GeneratedOreOrigin` is a compact version-1 proof attached to the **existing** Latent Ore
block entity. Actual `AdaptiveOreVein` placement in `WorldGenRegion` initializes it after
successful native ore placement, binding original dimension and packed position. Existing
host serialization remains intact. Proof survives the normal block-entity save/reload;
ordinary player placement/setHost initialization clears it, moved address/dimension rejects it,
and unknown future proof versions remain untrusted. No per-block world-history database,
placement absence inference or old-terrain backfill is installed. Arbitrary direct save/NBT
editing has the same administrative trust boundary as Minecraft advancement files.

**Origin coverage is deliberately bounded:** legacy/unobserved ore stays unknown. Ordinary
ore proof is transient and is lost on chunk unload, expiry, eviction or server restart;
only observed native worldgen boundaries supply it again. Modded generation bypassing both
WorldGenRegion writes and the ordinary OreFeature bulk setter needs its own real adapter.
New observed vanilla ores and persistent proven Latent Ore can satisfy 126 in new chunks
of old worlds. This is a coverage limitation, not permission to accept all tagged ore.
Direct third-party chunk-section mutations bypassing Level.setBlock are outside the
observed mutation boundary. No complete all-mods or old-world origin ledger is claimed.

Default enabled End Latent Ore supplies Nature's Boon End bonus candidates. A modpack that
disables End Latent Ore generation/availability and supplies no supported eligible End ore
can make 182 unavailable. Disabling Nature's Boon, setting zero reward chance, empty eligible
ore pools or suppressing all native drops can also prevent it. Such configuration never
creates a synthetic completion or replacement reward. Unknown custom crop growth schemas
without a recognized positive age/identity advance fail closed for 128; ordinary default
eligible crops can complete five types without changing crops or skill settings.

### Bounded partial progress and lifecycle

The optional version-1 `achievement_progress` envelope gains three rule-version-1 entries:

| Entry | Saved primitive fields and bound | Reset/regrant policy |
| --- | --- | --- |
| `a119` | Canonical `cat` UUID plus integer `count` 0..5; cleared state has no cat and count zero | Preserve verified kills across player death/respawn, logout/rejoin, dimension change and restart. Cat damage, ownership loss or destructive removal resets this collection. No mid-collection substitution. |
| `a128` | `crops` compound, at most five canonical registry IDs <=256 characters with true byte values | Lifetime; retains through death, respec, logout, dimensions and reload. Saturation cannot replay a revoke. |
| `a182` | Integer `mask` 0..7, canonical Overworld/Nether/End bits 1/2/4 | Lifetime; only a new delivered receipt bit changes it. Saturation cannot replay a revoke. |

Known malformed entries reject locally without destroying unrelated state or unknown
fields. Future envelope/entry versions are preserved opaque, with their writer disabled.
Absent entries mean no historical evidence. Minecraft advancements remain the sole completed
authority. No migration infers past kills, growth, rewards or harvests from current state.

Cat observation stores at most 4,096 scalar loaded-cat records, with 200-tick stale
retention/pruning and contiguous native-tick evidence. More than 64 nearby candidates fails
closed instead of selecting from a truncated list. It uses real existing cat ticks and
native accepted damage, not a second pet simulation. Unload/temporary absence closes
observation continuity; it cannot establish a damage-free encounter across the gap.
Closed records retain their last accepted damage under the same existing cap/expiry,
so an unload or ownership transfer away/back in the same tick cannot erase known damage
and restart a falsely clean collection. They are not durable history. Valid
saved kills remain, and only a later fully observed encounter can add new kills. Config
refresh and player lifecycle discard active fight evidence through the existing runtime.
Actual prevented death is not a player-life reset and introduces no extra cat-life rule.

Mining continuation is transient, bounded to 4,096 actors, twenty counted blocks and the
current rhythm identity, with stale records pruned after 72,000 ticks (the configured upper
timeout bound). Its existing skill reset/deactivation and runtime lifecycle explicitly
notify the rule consumer. Actual death, respawn, logout, dimension transition, restart and
config reload discard it. Prevented death follows the ordinary skill rhythm state. No
inactive-session polling or durable mining history is added.

`CommittedItemDrops` is scoped to one synchronous delivery stack and retains at most
256 expected drop UUIDs and 256 accepted UUIDs per scope. Duplicate spawn callbacks do
not add items, counts saturate at Integer.MAX_VALUE, and `finally`/try-with-resources
removes the scope. Nature's Boon binds only each directly armed native popResource
supplier's UUID; unrelated nested spawns cannot replace a canceled reward. Pocket Nets
requires the exact returned overflow entity's successful spawn receipt. Capacity loss
fails closed for evidence; no deferred reward replay or durable live entity is kept.

Successful fact delivery uses the existing bounded 4,096-receipt age `[0,200)` policy;
cat invalidation remains effective even at capacity or with the owner offline/ineligible.
A native admin grant/revoke changes completion only. Duplicate receipts, unchanged saved
collections, login, datapack/config reload and saturated counters cannot restore revoked
completion. Fresh one-shot operations and fresh valid rhythms/challenges may re-earn their
rules; lifetime saturated 128/182 need explicit native regrant. Missing advancement resources
never queue historical awards. Datapack reload resolves fresh holders at the next operation.
