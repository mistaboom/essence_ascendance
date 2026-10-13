# Development

Current multiplayer behavior is defined by **Multiplayer interfaces and solo-first behavior
(2026-10-13)** and its popup/tooltip follow-up below, plus the stage-9 audit notes. Earlier
stage-5/6/7 sections are historical wherever they conflict: they do not authorize multiple
player sources, adoption/privatization, port toggles, break protection, removed screens or data
resets. Each player intentionally has one active source; one source may serve many consumers.

The root README is the shared player-facing project description. Keep build,
publishing, and maintainer instructions in `docs/` so they do not appear in mod
store descriptions.

With Java 21 installed, build and check both loader variants:

```powershell
./gradlew.bat :common:check :fabric:build :neoforge:build
```

On macOS/Linux, use `bash gradlew` instead of `./gradlew.bat`.
Production jars are written to `fabric/build/libs` and `neoforge/build/libs`.

## Isolated team development in IntelliJ

Refresh the Gradle project to generate the `Teams baseline` and `Teams ftb` server,
client 1, and client 2 run configurations for each loader. These use the normal
Minecraft/Architectury initialization and existing Java 21 target. Launch graphical
clients through IntelliJ. Each client has its own username, UUID, and working
directory; keep the generated identity arguments when running both together.

| Mode | Working directories, relative to the loader project |
| --- | --- |
| Provider-free baseline | `run/teams/baseline/server`, `client-1`, `client-2` |
| Provider runs (default FTB) | `run/teams/ftb/server`, `client-1`, `client-2` |

Set the Gradle project property `teamDevProvider=openpac` or `teamDevProvider=both`
and refresh Gradle to select a separate provider directory and generated run names.
The baseline configurations always remain available. Install optional mods and
their required dependencies manually in the selected run's own `mods` folder;
the build adds only optional compile APIs, never provider runtime dependencies. Keep baseline folders minimal. Existing
main development runs and installed mods are untouched. Do not copy a financial
world between provider modes except for an intentional authority-change test.

For a dedicated-server test, accept the Minecraft EULA yourself in that server's
directory and configure its port/online identity mode deliberately. Join the
server with both clients through IntelliJ; separate directories alone do not
prove distinct server-side identities. Confirm both UUIDs in the server before
testing financial behavior. Use different ports for simultaneous servers.

Stage 5 provides native membership commands and optional FTB Teams/OpenPAC adapters using
the same exact reservoir service, capabilities, and settled-roster reconciler.
New machines bind to the effective pool. Existing personal machines pause reservoir work
while their owner belongs to a team until explicitly adopted. Saved player data schema 10
rejects older development worlds; create a fresh test world deliberately, never automatically. Reservoir carry now shares the player save; personal reward carry stays
separate. Valid saved balance profiles need no regeneration/deletion. Crucible packet
schema 11 preserves exact six-type amounts and local fill limits beyond signed-long range.

Focused checks are `./gradlew.bat :common:machineBindingInvariants :common:membershipProviderInvariants :common:reservoirAccountingInvariants
:common:multiplayerFoundationInvariants :common:balanceFractionalLedgerInvariants`;
they also run in normal `:common:check`. Whole balances, reservoir fractions and roster
receipts share one save. Inventory/chunk files are runtime-transactional but have no
cross-file crash journal. Provider fixtures and compilation do not verify actual
multiplayer integration. See the auxiliary multiplayer specification/passdown for the
settled-roster API and exact stage boundaries.

For each loader, the minimal FTB run adds just `ftb-teams-<loader>-2101.1.11.jar`
and `ftb-library-<loader>-2101.1.30.jar` to each selected server/client `mods` folder.
Minecraft 1.21.1, Architectury 13.0.11, and Fabric Loader 0.19.3 / Fabric API
0.116.15+1.21.1 or NeoForge 21.1.248 are already resolved by Gradle. Do not manually
duplicate Architectury/Fabric API or add FTB with Gradle runtime resolution too.
FTB Library **2101.1.36 or 2101.1.37** is also accepted with Teams **2101.1.11** on both loaders.
For simultaneous membership and quest evidence, use Library **2101.1.36 or 2101.1.37** and Quests
**2101.1.36** with Teams **2101.1.11** / Architectury **13.0.11**, all matching the loader.
Both loaders passed initialized no-world API fixtures and real early quest-definition
reading/malformed rejection; actual managers, client screens, events and restarts remain pending.
Other FTB/Library versions are unverified and pause membership access.

Shared-tuple verification reuses `gradle/membership-native-hooks.init.gradle`, adding
`-PmembershipFixtureFtbLibraryVersion=2101.1.36 -PmembershipFixtureFtbQuests=true` alongside
`-PmembershipFixtureFtb=true` for either loader's `runServer`. Quote each `-P...` argument
in PowerShell, including the dotted version. The new Library/Quests jars live only in
`build/ftb-shared-compat-audit`; separate isolated run directories keep the older fixture
mods folders intact. Receipts: `common-checks.log`, `fabric-shared-runtime.log`,
`neoforge-shared-runtime.log`, `artifact-checksums.json` in that audit folder. These fixtures
exit before world creation and do not accept an EULA or change an installed game instance.
The same fixture also accepts `-PmembershipFixtureFtbLibraryVersion=2101.1.37`; verified
receipts are `fabric-library-37-verified-runtime.log` and `neoforge-library-37-verified-runtime.log`.
The initial Library-37 logs failed on a fixture's implicit native team name and are retained;
the fixture now supplies a name. `followup-common-checks.log` covers 655 membership and
69 quest assertions; the verified Fabric receipt also covers 1,097 command assertions.
Library-37 artifact checksums and the unchanged 24 common SNBT Java sources versus Library-36
are recorded in `library-2101.1.37-checksums.json` and `library-snbt-source-comparison.json`.

An existing native world retains its authority when FTB is added. Installing a provider hides M
but cannot silently switch saved membership. After loading a supported build/installation, use
`/essence admin membership prepare ftb_teams`, inspect `status`, then explicitly `activate <token>`
if the proposed change is intended. Existing other-authority machine bindings are not remapped;
new physical placement after activation binds to the effective FTB pool. Native personal-request
approval now uses `/essence team approve <request> [name]`; creation requires a valid explicit
name in the shared service, while existing-team approval retains the current name.

New worlds default to AUTO: supported FTB Teams, supported OpenPAC, then native.
Supported providers that are starting/unavailable remain selected. If only unsupported external
installations exist, AUTO reports unsupported and pauses instead of enabling native. Persisted
authority wins AUTO after establishment; additions/removal/replacement never silently switch it.
Set server JVM `-Dessence.membershipAuthority=auto|native|ftb_teams|openpac` for explicit selection.
A different explicit setting pauses an established world until an operator reviews activation.

OpenPAC support is pinned to **0.32.7**, Minecraft 1.21.1, Fabric/NeoForge. Manually add
`open-parties-and-claims-<loader>-1.21.1-0.32.7.jar` to selected server/client mods folders.
Fabric also needs Forge Config API Port >=21.0.5 (tested 21.1.6); NeoForge needs no config port.
Get these from [Xaero's Maven](https://chocolateminecraft.com/maven/xaero/pac/) and
[Fuzs' Maven](https://raw.githubusercontent.com/Fuzss/modresources/main/maven/).
No Forge/other-version support is claimed. Existing runs and baseline folders stay independent.

Operator flow: `/essence admin membership prepare <provider>` pauses access and waits for two
complete target observations. Read `status` for the counts/policy/token, then `activate <token>`
within 6,000 server ticks (about 5 minutes at 20 TPS). Status shows remaining time and a
clickable activation suggestion; expiry reports an explicit retry instruction. Activation revalidates target evidence and saved financial revisions
before the shared transaction changes authority. Departures retain their shares; target rosters
equalize stored essence. Solo pool identities change explicitly; old team IDs are not remapped
onto infrastructure. Available/progression remain personal. `cancel`/expiry resumes persisted
authority if configuration permits; `reconcile` retries the same authority. Use AUTO or the new
provider ID on subsequent starts. Stage 5 requires schema 10: preserve old development worlds
and deliberately create a new test world. No automatic deletion or legacy migration is implemented.

Native commands: `/essence team create "Name"`, `status`, `requests`,
`request <team-UUID> share-stored`, `cancel|approve|decline <request-UUID>`, `leave`,
`remove|transfer <member-UUID>`, and `disband`. The applicant's `share-stored` token
acknowledges sharing all existing Stored essence at approval. Any current member can
approve; management controls removal/transfer/disband. Transfer management before a
manager leaves a multi-member team. Stale requests need cancellation and a fresh request.
Native writes reject server-side under either external authority. Actual provider party members,
including offline members, participate automatically with no EA acknowledgment/approval.
Use the provider's own GUI/commands to change external membership.

FTB callbacks invalidate access immediately; two equal complete observations on successive
server ticks publish one settled batch. Public live membership/role probes also invalidate
before authorization. Stable/unavailable full captures are bounded to once per 100 ticks.
OpenPAC has no party-change listener; lifecycle events, bounded polling and live probes share
the same scheduler/settlement. Its exact-version public PartyManager.isLoaded getter prevents
disabled parties from appearing as confirmed deletion; that guard is outside the documented API. Pending truth pauses
all reservoir/capability access conservatively. Player/default teams are solo; server teams,
allies and invitations never participate. Rename/role/leadership changes preserve money
and actual pool identity. A standalone singleton PARTY is a TEAM, so personal machines
use the same team binding/access implementation; previously placed personal machines require explicit adoption.

Headless loading checks use `-I gradle/membership-native-hooks.init.gradle
:fabric:runServer` or `:neoforge:runServer`. `-PmembershipFixtureFtb=true` uses the exact
verified binaries under `build/ftb-stage3-verification`. Add `-PmembershipFixtureOpenpac=true`
for the verified binaries/config port under `build/openpac-stage4-verification`; both properties
exercise coexistence. Obtain missing artifacts from the official Maven sources when needed. These runs initialize real loaders/APIs then exit before
world/manager creation, without accepting the EULA. They use isolated build directories;
normal and IntelliJ run folders remain untouched. The real-interface roster fixtures are
synthetic evidence, not a claim of real-world multiplayer integration.

GitHub Actions runs the checks on pull requests and updates to `main`. Publishing
a GitHub Release starts automated jar distribution to GitHub, CurseForge, and
Modrinth. See [the release guide](RELEASING.md) for versioning, setup, and recovery.

## Historical stage 5 machine controls and minimal manual scenarios

The authoritative shared operations are available through `/essence machine <x y z>`:
`status`, `adopt`, `personal`, `start`, `stop`, `link automatic`, `link disconnected`, and
`link locked <source x y z>`. All require a loaded machine within reach and current capabilities.
Adoption requires the personal owner/source manager plus destination management; `personal`
privatization requires management and leaves the machine paused. `recover <recipient UUID>`
requires operator level 2 and a confirmed dissolved binding. These are development controls
using the same server operations that stage 8 will present through the GUI; no wrench or artwork.
After ownership transfer, reopen the menu and explicitly start processing. Temporary provider
pauses retain armed jobs; known revoked operators/funding changes disarm the affected machine.

Use a fresh schema-10 world in the existing baseline/native or FTB IntelliJ runs, with two
verified distinct server UUIDs. The user performs launches/clicking. Keep normal installed
mods and existing run worlds; no Prism or modpack is required. Check these short scenarios:

1. Make neighboring teams. Place a Crucible as the leader and Infuser/Pylon as a teammate.
   Verify shared amounts/access, foreign denial, nearest eligible source, and stable links after
   adding a closer same-team Crucible. Reassign the Pylon with `link locked` and check both
   stations' structures immediately; remove/swap its Focus and check contribution changes.
2. Switch Locked/Disconnected/Automatic. Unload a selected source's chunk and confirm its
   target is retained and work pauses. Reload it and resume; actually remove or privatize it
   and verify only Automatic selects another eligible source. No force loading is expected.
3. Place personal infrastructure before joining. Confirm inventory remains retrievable but
   reservoir work pauses. Explicitly adopt with appropriate source/destination management,
   reopen stale menus and start. Transfer leadership and let the placer leave; team machines
   stay with the team. Changes involving unrelated members leave the existing job armed.
4. Leave a team machine unloaded, disband as the last leader/sole member, restart, then load it.
   Confirm paused personal ownership and cleared operator/recipient. Join the recipient to a
   new team and confirm no automatic donation. Unknown provider truth must remain locked.
5. Use two Crucibles with different Focus/structure capacities. Both show the same exact
   reservoir but different member-scaled local fill limits. Fill past the smaller limit,
   try adding input there, and confirm no input is consumed; withdraw normally. Let a member
   leave and confirm retained shares/over-capacity balances are never clamped. Pending
   requests do not raise capacity; offline actual members do. Member count does not raise rates.

Automated no-world fixture commands remain `-I gradle/membership-native-hooks.init.gradle
:fabric:runServer` or `:neoforge:runServer`, optionally `-PmembershipFixtureFtb=true`.
They now run real registered machine/structure/menu tests using the existing memory-storage
fixture and existing native Infuser payment rollback checks, then exit before world creation.
They do not prove live provider events, multiplayer persistence or graphical UI. Explicit
explicit team automation/destruction policy remains later work. Stage 6 implements shared outgoing bandwidth.

### Personal channel selection follow-up

Use the six checkboxes beside the Crucible's essence amounts; All / None select or deselect
all types. These choices are remembered per player across Crucibles, reconnects and restarts.
Only selected types channel to you; an empty selection disables Start. A running channel waits
when selected types are empty (or None is selected), then resumes as they refill or you select
another type. No unselected essence is withdrawn as a fallback. Stop stays available while
waiting. This keeps conversion input stored while you collect only its output. Players and Infusers now operate concurrently and share one outgoing budget per physical
Crucible. Waiting player demand consumes no share; player controls do not stop an Infuser.

This follow-up keeps SavedData schema 10; missing personal preference defaults to All, so a
stage-5 world needs no additional reset. Crucible packet schema 12 carries viewer selection, link-specific receiving/pause state,
channel count and permission state. Run `:common:crucibleChannelSelectionInvariants`
for preference/codec checks. The existing isolated membership native loader fixture also tests
actual selected transfers, automatic refill resume, stale-menu rejection and payment rollback.
Graphical placement/hover/overlay/reopen checks remain for the user's minimal IntelliJ client.

### Stage-5 linking clarification / stage-6 handoff

Several Infusers can already link to the same Crucible. Automatic/Locked links use the shared
MachineLink validator and loaded discovery; another consumer's processing or a player channel
does not reserve a source. Existing pool/range/dimension/valid-source checks and unloaded-target
retention remain. To check manually, link two same-team Infusers to one station (one Automatic,
one Locked), run both, add a closer source and verify stable targets. Disconnect one and verify
the other keeps its link. Foreign, distant and wrong-dimension targets remain ineligible.

All prior blanket single-channel design rules, including one receiving channel per player, are
superseded. Stage 6 allows distinct source-to-receiver channels concurrently and deduplicates
identical registrations. The former singleton player fields/session index and Infuser priority
gates are removed. Every incoming player channel uses the existing persisted player selector.
See the auxiliary handoffs for the completed gate audit and selection/API contracts.

### Stage 6 concurrent scheduling and manual checks

`EssenceCrucibleChannelService` owns one `ConcurrentChannelScheduler` per server. Source keys
are dimension + physical Crucible position; receiver keys are player UUID or physical Infuser
address. Registrations carry the current generation and binding/context receipt. Block ticks
register loaded machines; server-post scheduling never discovers consumers by scanning worlds
or provider rosters. Source/receiver validity remains checked against current loaded objects,
authorization, configured links, dimension and range.

A physical Crucible's evaluated structure supplies its single outgoing rate. Sources have
separate budgets while their receivers spend the same communal ReservoirService. Rates accrue
with the existing exact twentieth arithmetic; delivery batches every four ticks, retaining the
Infuser interval. Whole unused credits expire each batch. Source fractional carry and rotating
fairness survive player Start/Stop/selection changes. A real Infuser Focus cap limits total
accepted input. The allocator redistributes caps, unusable stock and failed payments; order
rotates within stations and between stations competing for scarce stock.

At 120/s, adequate usable stock and receiver caps at least 120/s: two Infusers get 60/s each;
adding one player gives 40/s each; adding a second gives 30/s each. A 20/s receiver alongside
two uncapped receivers gives 20/50/50. These are total essence input rates, across all types.
All versus one enabled type receives the same channel share when that selection has usable
stock. None and unavailable selected types retain explicit running sessions with zero demand.
Changing preferences cannot start stopped channels; disabling invalidates prepared incoming
payments. Player settings never filter Infuser costs or change reservoir equalization.

All Infuser recipe payments now use grants: exact portable Focus/equipment contributions remain
on their existing workpieces. Timed Essentium/repair adds one bounded `paid_job` in the machine
save, tied to exact recipe, input components/count, output and requirements. It is already-paid
work, with no refund/withdrawal/general-purpose tank. Removing/changing input or recipe context
pauses this job; restore the same context to resume it. Moving/copying an input stack cannot copy
machine job payments. Stop, link loss and unload retain paid work. Completed timed output waits
for both full payment and the original processing duration. The progress bar reflects the lower
of elapsed processing and exact paid progress. All final inventory/output publication uses the
existing reversible reservoir participant (zero new debit when the job is already paid).

Run focused checks with `:common:concurrentChannelSchedulerInvariants
:common:crucibleChannelSelectionInvariants :common:reservoirAccountingInvariants
:common:machineBindingInvariants :common:machineUiFoundationInvariants`. The existing isolated
`gradle/membership-native-hooks.init.gradle` fixture exercises actual machine callers on both
loaders, including eight Infusers, all four recipe paths and completed Essentium under toggles.
It exits before a world opens, with normal EA initialization. Production release builds and
asset/JAR checks follow the fixture runs.

User-run minimal IntelliJ checks remain:
1. Run two Infusers and two distinct player UUIDs on one same-team Crucible; compare exact total
   withdrawals over several seconds. Repeat with a Focus cap and blocked output. Use an actual
   valid structure to set the desired source rate; member count does not raise that rate.
2. Start one player on two sources, Stop only one, reopen both menus, repeat Start and confirm
   one receiving state per pair. Another player's screen must retain independent Start/Stop.
3. Test All, one type, None, empty selected stock and refill. Toggle while Essentium runs; jobs
   remain armed and receive grants. A stopped player's preference change must leave them stopped.
4. Complete Essentium, Focus, equipment and repair; block output mid-job, save/restart and unload
   the source. Restore the original timed job context after changing/removing its input. Confirm
   no early output, repeated charge, lost paid progress, double output or idle-credit burst.
5. Test logout, death, source removal, adoption/rebinding and permission revocation; unrelated
   player sources and team automation continue. Inspect multiple observer tethers and GUI
   waiting status, selection persistence, overlays and JEI reopen guards.
6. Repeat ordinary membership/authorization scenarios with the existing optional provider runs.
   Headless native fixtures do not claim real FTB/OpenPAC event or two-client verification.

SavedData remains schema 10 and Pylon packet 3; stage-5 schema-10 worlds need no additional reset.
Crucible packet is 12, so both endpoints need the matching build. Earlier reset requirements stay
in effect. No reset/deletion, profile rebuild, installed-mod removal, deployment or Git writes
are performed by this stage.

## Historical stage 7 continuity, recipient and pipeline checks

Stage 7 supersedes older automation/loose-drop and timed-job-format statements above. The same
stage-6 scheduler serves Essentium, repair, Focus and equipment payments; player selections
remain the stage-5 personal preference. Completion refreshes demand for the next Essentium carrier
even when no other receiver changes money. Waiting for bandwidth never cancels a job.

Use the existing minimal IntelliJ baseline/native or FTB runs with distinct server UUIDs. Use a
new isolated development world if the old world contains stage-6 timed `paid_job` records: their
version-2 identity/recipient/funding format is deliberately incompatible. Schema-10 financial data,
Crucible/Pylon packet versions 12/3 and membership metadata 1 are unchanged. Infuser menu properties
are now 46; both endpoints require this build. Preserve existing worlds/configs/balance profiles.

The existing `/essence machine <x y z>` common control surface adds:

- `automation insertion on|off` and `automation extraction on|off` independently expose ports.
  Insertion defaults on; extraction defaults OFF. Enabled generic ports are accessible to attached
  hoppers/pipes, with no invented player identity. Installed Focus and partial input extraction stay
  protected; output extraction must be explicitly enabled. Crucible remains insertion-only.
- `recipient <UUID>` / `recipient clear` sets the explicit unbound automation recipient. Actual
  current members are eligible; already-soulbound ownership remains permanent. Manual Start claims
  unbound equipment for its starter, independently of placer. Focus/Essentium need no recipient.
- `recover-item input|output` retrieves only the caller's actual soulbound equipment, including
  after departure. Empty-hand sneak-use calls the same recovery method. This gives no shared spending,
  general inventory access or refund. Paid repair GUI extraction is guarded; recovery moves the same
  job record/timer with the workpiece. Put it into another authorized Infuser and explicitly Start.

Manual sequence:

1. Produce several consecutive Essentium carriers with no player channel, then run multiple Infusers
   plus player channels. Change Start/Stop/All/None/types repeatedly; jobs retain requirements, links,
   recipients, elapsed time and paid progress. Distinct player/source links coexist; repeated Start
   for the same link cannot multiply throughput. Check the existing bandwidth-waiting status.
2. Block output, exhaust/refill stock, Stop/resume, unload/reload and restart during a partial job.
   Restore exact timed context after a swap. Rebind funding: the affected job disarms but retains
   committed work/time, and needs explicit authorized restart/current link. Other jobs keep working.
3. Configure insertion and extraction separately and test actual hoppers. Confirm closed output cannot
   be pulled, enabled output can, and unbound equipment automation waits for an explicit recipient.
   Test existing bindings, manual starter ownership, invalid recipient and starter/recipient logout.
4. Leave/kick during work and recover only your own input/output by sneak-use or command; a stale
   general menu must remain denied. Recover/move a partial repair once, with no refund/duplicate credit.
5. Test manager versus ordinary member/outsider breaking and creative removal; a machine pickup
   retains binding/inventory/Focus/paid job and pauses processing. Let an outsider place it: ownership
   must stay with the original pool. Test TNT/pistons and external claim protections. Existing
   administrator recovery applies to dissolved bindings. Arbitrary third-party forced edits remain
   outside native protection guarantees; do not treat handler checks as every pipe mod's interoperability.
6. Reopen the existing GUI/JEI and verify actual recipient, authoritative port flags, status and info
   reflow. All display changes use MachineInfoPanel/EssenceInfuserMenu, not a second GUI framework.

Automated receipts: `build/multiplayer-stage7-build.log`, `build/multiplayer-stage7-native-final.log`,
`build/multiplayer-stage7-ftb-final.log`, `build/multiplayer-stage7-final-packaging.log` and
`build/multiplayer-stage7-jar-audit.json`. Native fixtures include real vanilla hopper transfers,
simulated/aborted loader adapters, actual BlockItem snapshot application and existing payment rollback.
They exit before creating a world; live two-client FTB events/claims/GUI clicking remain user checks.
Runtime rollback remains distinct from unimplemented chunk/account cross-file crash atomicity.

## Multiplayer interfaces and solo-first behavior (2026-10-13)

This section supersedes the earlier stage-8 multi-source player links, adoption/privatization,
three-screen navigation, configurable item ports and break protection. Latest user instructions
explicitly changed those policies. Solo use is the default; multiplayer remains optional.

### Gameplay and accounting

A player has at most one active Crucible source across a server. A valid Start at another source
replaces the prior link; duplicate Start does not multiply throughput, and invalid/None/out-of-range
Start preserves the prior link. Stop remains scoped to the addressed source/player. Other players
and Infusers may use additional Crucibles. One physical source still shares its Pylon-derived
bandwidth among its consuming channels; player hopping cannot accumulate multiple source budgets.
Actual callers use ConcurrentChannelScheduler.registerExclusive through EssenceCrucibleChannelService.

Keep the existing financial implementation: ReservoirService aggregates/equalizes authoritative
stored shares in PlayerEssenceData, including fractional units and existing conserved remainder
rules. There is no second independently spendable team treasury. Earlier descriptions of an actual
separate pool were imprecise: the pool is a shared accounting view over these records. Joining
shares all stored reservoir essence; already-channeled Available Essence stays personal (explicitly
confirmed by the user). Departure retains the equal remaining stored share based on the pre-departure
roster. No contribution refunds, balance resets or currency migration were introduced.

### Ownership, placement and linking

MachineBinding follows settled membership automatically. Personal machines become team infrastructure
when their owner creates/joins a team. An ordinary member's departure leaves that infrastructure
with the team and immediately revokes the departing member's inventory/settings authority. Only the
final native owner may disband; owner Leave is rejected until management transfers, or the owner
uses Disband after all others have left. Final disband returns team infrastructure to usable personal
ownership. Adoption-needed status and independent adoption/privatization/rebind operations are gone.

ReservoirService persists ordered machine_join receipts and dissolution revisions; each binding
persists its ownership roster cursor. Unloaded machines replay the same rules on load, including a
provider publication that deletes an old team and joins its successor to another team. These receipts
carry ownership metadata only, never a new balance. Legacy missing cursors use current authoritative
membership, never old owner/provenance text to claim an unbound machine. Physical placement during
unavailable provider membership records a pending placement owner, clears old access, and completes
only after that membership is ready. Existing unbound placements still have explicit admin recovery;
normal personal/team placements never need recovery/adoption steps.

Crucible/Pylon/Infuser placement uses the placer and their current team. A stolen portable machine
gets fresh ownership when placed, preserving inventory and already-paid work. Break permissions
follow vanilla/other protection mods; EA no longer blocks opposing players from breaking machines.
Physical removal produces one machine item carrying its contents/paid work and clears the removed
block entity to avoid duplicate drops. Its old binding/link/provenance are stripped; placement also
replaces bindings in older portable items. Infuser paid progress/timers remain preserved while
transient work authorization is refreshed through the existing processing lifecycle.

Pylons and Infusers immediately use Automatic linking after placement: retain one eligible loaded
source, otherwise discover the nearest eligible same-owner/team Crucible in range. Exclude foreign
sources even if closer. Existing automatic links survive a newer closer station. The Pylon and Infuser
routing popups are removed. Legacy saved Locked/Disconnected GUI settings migrate to Automatic,
retaining a valid recorded source. Advanced source commands still use MachineLink and never force loads.

All insertion/extraction enable-disable toggles, saved settings, commands and ownership gates are
removed. Normal input/output/recipe rules still apply, including the Crucible's existing insertion-only
automation. Soulbound equipment cannot enter through hoppers or pipes unless its owner currently belongs
to the receiving device's player/team pool. Unbound inputs remain normal, and extraction is unchanged.
The shared ThroughFace policy covers vanilla hoppers and Fabric/NeoForge item adapters, including
simulation/rollback. Direct player inventory and settings remain owner/member-only.

### Interfaces and provider behavior

M opens a compact left-side native team popup on the actual machine inventory. Solo home contains
Playing solo, a team-name field and Create team; ordinary member home has Leave; owners have Manage
or final-member Disband. Requests appears only while pending, with outgoing cancellation and one
incoming applicant per page. Only the native owner can approve/decline. Solo-owner approval requires
a name and explicitly creates the shared team. Obsolete requests clear after membership changes;
remaining applicants to an existing team survive another approval. Closing M invalidates its receipt.
The independent membership GUI command only gives device/provider guidance. My incoming links,
Infuser/Pylon S popups, automation-recipient controls, Authority and membership-ready copy are gone.
Personal selectors and the Crucible S popup remain in their existing screen. Team/player names replace
normal UUID owner labels, including native, FTB and OpenPAC display names. No second GUI framework.

Popup policy follow-up (2026-10-13): M uses the shared 180px side-panel width and 4px gap.
MachineContainerScreen owns one active side popup across both sides: Info, M and Settings are
mutually exclusive on every machine. New popup types use its final toggleMachinePopup,
isMachinePopupOpen and closeMachinePopup methods instead of independent booleans. Replacing M
invalidates its server receipt and releases field focus. Escape closes the active popup first.
Confirmation dialogs remain modal above their originating popup so pending actions retain their
valid context. Resize preserves the active popup and typed team name.
Crucible Info Channeling again has only Rate and Range, following the pre-multiplayer panel.
The shared storage indicator is beside the main total as Total (shared); the long selection,
link, allocation and transfer-sample explanations are removed from Info. Underlying statistics
and channel/accounting behavior are unchanged.
Essentium and other item essence tooltips use shared whole-number display rounding (half up),
with <1 for positive sub-unit yields. JEI search terms match; exact Archive values, extraction
and fractional accounting stay unchanged. Both loader builds, existing machine UI (21), tooltip
formatting, palette (131), tooltip presentation (78) and 349 asset checks per loader passed in
build/popup-exclusivity-tooltip-build.log (21s). Visual checks remain user-driven.

Every new equipment workpiece waits for explicit Start, including already soulbound gear. First Start
on unbound team equipment uses the shared machine confirmation: Soulbind equipment? / Soulbind & Start.
The server issues a one-use receipt scoped to the live menu, actor, binding, membership and exact
input/Focus/components/context. Replaced equipment, changed membership, stale menus and replay reject.
Solo Start has no confirmation. Existing soulbinding always wins regardless of who upgrades the item.
Focus/Essentium pipelines and paid-job provenance/timers retain existing behavior. Equipment insertion
never automatically binds or starts work; component delivery to approved work does not disarm it.

Outsiders receive the separate slot-free MachineAccessScreen: “You don't have access to this device.”
A native-team request targets that machine's team. A personal-machine request leaves both players solo
until its owner explicitly approves and names the resulting shared team. No placement/use silently
creates teams and no unsolicited join prompts occur. Membership approval always shares stored essence
only. Existing unavailable/initializing states deny access rather than pretending the player is solo.

When FTB Teams or OpenPAC is installed, M is hidden; the native GUI command directs the user to the
provider. Existing-world authority is not silently changed: if the installed provider differs from
persisted authority, guidance explains the established `/essence admin membership` prepare/status/
activation flow. When both providers are installed, guidance honors the selected installed authority.
Native membership writes remain denied whenever an external authority is active.

### Compatibility and verification

SavedData stays version 10 and paid work stays version 2. Ownership history/cursors are optional new
metadata. Crucible state packet remains 14; Pylon remains 3. Infuser menu properties decrease from 46
to 44 after deleting port flags. Menu registrations/actions changed; restart all endpoints on matching
builds. MachineLink saves version 2 for automatic-routing migration; the new InfuserSoulbindPayload
uses a bounded one-use receipt without changing accounting. Existing worlds/configs/balance profiles
were not edited, deleted or regenerated.

Latest automated evidence lives under build/: multiplayer-popup-checks-final.log,
multiplayer-popup-native-verified.log, multiplayer-popup-ftb.log, multiplayer-popup-openpac.log,
multiplayer-popup-build.log and multiplayer-popup-jar-audit.json. Native fixtures initialize the
actual loader/mod and exit before opening a world/provider manager. They exercise registered
5 C2S and 3 S2C transport cases, real popup/access handlers (including approval and menu renewal),
20-source switching, automatic ownership/linking, exact paid-work recovery, theft placement,
vanilla/platform hopper insertion rejection and simulation, and existing payment rollback.

Verified latest receipts: ordinary builds/assets PASS (13s, 349 matching assets each); base native
PASS (28s, 184/185 actual machine assertions plus 80 automation-ownership, 21 Infuser Start and
109 single-source checks per loader). Minimal FTB PASS (43s, 20 API checks each including team
names); minimal OpenPAC PASS (28s, 19 API checks each including custom/default party names).
Focused checks: membership 626, binding 153 (including legacy routing migration), interface 276,
shared UI 21. Earlier unchanged scheduler/accounting receipts remain under multiplayer-simplification.
Final production jars include the confirmation payload/client/server code and owner-name adapter,
and contain no fixture/provider API leaks. SHA-256 receipts are in multiplayer-popup-jar-audit.json.
Earlier failing logs are retained: request pruning on a rejected transition, immediate receipt cleanup,
and missing synthetic-player menu initialization were resolved before the verified runs.

Pending user-run graphical checks: restart Fabric; verify normal solo Crucible use with no adoption
message, immediate same-owner Pylon/Infuser links, Start on a second Crucible stopping the first,
team create/join/depart/final disband, outsider request prompts, personal-owner explicit approval,
FTB M suppression/guidance, compact M/request/soulbinding popup layout at different GUI scales, and multiplayer
visual/HUD behavior. No graphical client launched or controlled, no full pack/Prism work, and no
staging, commit, push or deployment performed.
## Stage 9 multiplayer audit (2026-10-13)

The accepted simplification and popup/tooltip contract remain. The audit fixed actual Infuser
menu component changes disarming approved work, inconsistent repair-component transfer capacities,
and scheduler budget records retained for departed/uncapped receivers. These use the existing
menu, item-aware container capacity/ThroughFace policy, two loader adapters and shared scheduler.
Player source exclusivity, source-wide fair sharing, exact player-backed stored shares, explicit
equipment Start, original soulbound owners, automatic-link migration and normal extraction/breaking
remain. No UI redesign, accounting change or save/packet migration.

Existing tests were extended for actual menu component insertion/removal, bounded hopper/adapter
repair insertion and surplus conservation, receiver lifecycle/carry, and execution-time soulbinding
rejection after Focus replacement, lost reach, same-address replacement and unresolved membership.
Two stale diagnostic guards were corrected: the command test now requires the existing reviewed
membership activation branch and operator gating; the gameplay diagnostic's exact field/type list
includes the two existing persisted personal selection fields. No assertion was weakened to restore
an obsolete gameplay rule.

Final full common check, Fabric/NeoForge builds and all 349 packaged assets per loader pass in
`build/stage9-verified-checks-build.log` (7m 51s). Current jar hashes and packaging checks are in
`build/stage9-jar-audit.json`. Stage-9 receipts are `build/stage9-baseline-checks.log`, `stage9-native-final.log`,
`stage9-both-providers.log`, `stage9-command-checks.log`, `stage9-diagnostics-checks.log`,
`stage9-verified-checks-build.log`, `stage9-environment-{fabric,neoforge}.json`, and
`stage9-jar-audit.json`. Earlier failing receipts remain separate. Resolved normal runtime classpaths
contain neither provider dependencies nor duplicate artifact paths. Initialized both-provider runs
use FTB Teams 2101.1.11 / FTB Library 2101.1.30, OpenPAC 0.32.7 and Fabric config port 21.1.6;
the existing loader/API versions above are verified by actual runtime and dependency receipts.

Native checks initialize real loaders, run memory-backed machine callers and actual API-interface
fixtures, then exit before opening a world/provider manager. They do not prove real roster events,
authority activation in a world, process restart persistence, every third-party pipe or graphical
multiplayer. The auxiliary `multiplayer_teams_manual_test.md` records remaining checks with explicit
outcomes and actual IntelliJ configurations. The user launches/controls clients; actual server UUIDs
must be distinct. Authority testing uses dedicated state. Runtime rollback is covered; player/chunk
cross-file crash atomicity is unjournaled/unproven. No instance installation, data reset or Git writes.

## Activation timeout and command readability follow-up — 2026-10-13

The next user-run Fabric log confirmed that Teams 2101.1.11 / Library 2101.1.37 was accepted and the real target roster was captured. Preparation at 10:28:15 produced a valid one-party/one-member receipt shown at 10:28:33; activation at 10:29:35 was after the old 1,200-tick deadline. This explains the generic rejection without claiming a successful authority switch or machine migration. The original log is retained at `build/membership-command-followup/user-activation-expiry.log`.

Preparation now allows 6,000 server ticks (about five minutes), with a visible countdown and an explicit expiry/retry notice. Status reads do not extend it. Token, financial/roster revision, readiness and current-roster validation remain required. Readable membership, team and machine commands reuse the established title/section/label styling; balances retain exact decimal shares, names replace record dumps, and command suggestions fill chat without executing mutations. Membership bare/help now displays guidance. Provider rows use last observed readiness, and failures/unobserved-adapter details remain visible to console users. Existing machine bindings from another authority are still not remapped; use a new physical placement after successful activation when testing.

`build/membership-command-followup/verified-checks-and-packaging.log` passed: 678 membership assertions (including real runtime prepare/tick/status/activate with a fake provider and no world, 80-second review, exact expiry, unchanged saved state, expired-token rejection and new preparation), 1,155 command checks, 13,452 shared presentation checks, 70,083 headless Archive integration checks, both production jars, and 349 asset checks per loader. The initial `checks-and-packaging.log` failed on a test-only trailing space in the new bare-help case; it is retained separately. `final-jar-audit.json` records the new development jar hashes, current command resources and absence of fixture/FTB class leaks. Earlier jar hashes predate this change. Live activation, real multiplayer events, graphical review and restarts remain pending; no client/world, installed mods, Git staging/commit/push or publication was changed.

## Beta.5 release validation — 2026-10-13

The complete local multiplayer/Archive source passed `:common:check :fabric:build :neoforge:build assetJarInvariants` in 8m 4s before the version-only bump from beta.4 to beta.5 (`build/beta5-release/full-checks-and-build.log`). A separate versioned package build validates beta.5 metadata; GitHub CI and the release workflow independently check the committed/tagged version before distribution. Public release notes and wiki explain the schema-6-to-10 new-world requirement. Existing-world provider migration remains outside this beta's scope. Canonical wiki checks passed for 22 pages, 173 internal links/anchors and five TOML examples; disposable publisher tests and all four workflow syntax checks passed. Earlier local receipts and pending live multiplayer scenarios retain their documented limits.
