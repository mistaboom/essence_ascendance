# Development

Start with [MAINTENANCE.md](MAINTENANCE.md) for the maintained documentation map
and cleanup policy. This guide owns current build, source and runtime boundaries.
[VALIDATION.md](VALIDATION.md) distinguishes invariant checks, native fixtures,
packaged/world evidence and remaining limits. Release commands belong in
[RELEASING.md](RELEASING.md); the root README remains player-facing.

## Targets, source ownership and builds

`gradle/targets.json` owns the four supported targets: Minecraft **1.21.1** and
**26.1.2**, each on **Fabric** and **NeoForge**. It declares dependencies, Java
levels, native source selections, resource formats, access requirements and
production archive providers. Run Gradle with Java 25; the 1.21.1 nodes select
Java 21 through their toolchain. Keep Gradle invocations sequential.

Keep editor-active authored source at **1.21.1**. Exact version-node tasks select
builds independently of the editor. The public Stonecutter editor switch copies
projections back into authored files; the current one-way API substitutions do
not provide a lossless editor round trip.

Shared gameplay, GUI composition, resources and tests belong in `common/src/main`
and `common/src/test`. Small API differences use existing `//?` branches. The
catalog's `nativeSources` selects disjoint native adapters from authored roots
such as `common/src/1.21.1` and `common/src/26.1.2`, with matching loader roots.
Their names identify an API implementation, not a requirement to copy a feature
for every target. Selected native roots compile verbatim: Stonecutter conditions
there are comments. Generated projections live under
`<module>/versions/<minecraft>/build/generated`; never edit them as source.

```powershell
./gradlew.bat :checkSupportedTargets --console=plain
./gradlew.bat :common:1.21.1:check --console=plain
./gradlew.bat :common:26.1.2:check --console=plain
./gradlew.bat :production2612FabricJar :production2612NeoForgeJar :assetJarInvariants2612 --console=plain
```

Run each invocation separately. On macOS/Linux use `bash gradlew`.
`checkSupportedTargets` selects both normal common checks, the four exact
production/archive asset checks and Pack Tester regression. The 1.21.1 archive
providers are `remapJar`; 26.1.2 uses `shadowJar`. The complete exact provider,
path, Java and hash receipt is `build/production-artifacts-all.json`.
`:writeProduction1211Manifest` retains the 1.21.1-only receipt. Select archives
through these providers, never by glob or a convenient shared-cache entry.

For a fresh gate, run `:cleanProductionOutputs --console=plain` separately before
`:checkSupportedTargets --console=plain`. That narrow cleanup preserves fixture
worlds, normal run folders, dependency caches and root receipts. Root `clean`
is inappropriate when those verification artifacts need to remain available.

## Adding or maintaining a target

1. Add actual supported dependencies, implementation flags and production tasks
   to `gradle/targets.json`, including `release.requiredTargetIds`. `settings.gradle`
   derives nodes/loader branches; plugin versions live in `stonecutter.gradle.kts`.
   Dependency resolution alone does not establish native compatibility.
2. Select existing `nativeSources`, `resourceFormat` and `nativeAccess` contracts
   where compatible. Compare native signatures, event order/cancellation,
   components, serialization and rendering before adding a disjoint adapter.
   Shared behavior stays in one owner. Source/resource gates reject competing
   duplicate paths; copying an entire feature is not a porting step.
3. Keep native differences at their actual boundary: rendering extraction/models,
   block-entity value input/output, loader transfer transactions, attachments and
   patched loader signatures. Common Minecraft API code must compile on both
   versions. `gradle/native-sources.gradle` attaches selected roots;
   `gradle/mixin-resources.gradle` projects structured mixin resources and Java
   compatibility without silently removing missing hooks.
4. Add the target's exact Client/Server `.run/` entries. `verifyDevelopmentRuns`
   rejects missing, duplicate or wrong-target entries. `gradle/assets.gradle`
   generates assets into target outputs; `gradle/production-targets.gradle`
   binds the real archives. Pack Tester remains a standalone Java 21 application.
5. Feed tests exact dependency/source/compiler providers through
   `gradle/test-inputs.gradle`. Native fixtures remain explicit where lifecycle
   or APIs differ. Expensive generation/replay suites remain opt-in. A new loader
   also needs real entrypoints, metadata validation, lifecycle integration and
   release publication IDs; catalog selection alone is insufficient.
6. Run the full gate, affected native fixtures, release-selection regressions,
   packaged startup/save/load and affected client/world behavior. Network changes
   need real-client evidence. Keep compilation, initialization, real operations,
   persistence, visuals and multiplayer claims separate. See VALIDATION and
   RELEASING for evidence scope and publication.

## Normal runs and isolated fixtures

IntelliJ has eight `Minecraft <version> <loader> Client/Server` entries and
**Essence Ascendance — Pack Tester**. Each targets an exact version node. Special
fixtures/team tasks remain in Gradle instead of duplicating the ordinary dropdown.

| Target | Game directory | Production archive directory |
| --- | --- | --- |
| 1.21.1 Fabric | `fabric/versions/1.21.1/run` | `fabric/versions/1.21.1/build/libs` |
| 1.21.1 NeoForge | `neoforge/versions/1.21.1/run` | `neoforge/versions/1.21.1/build/libs` |
| 26.1.2 Fabric | `fabric/versions/26.1.2/run` | `fabric/versions/26.1.2/build/libs` |
| 26.1.2 NeoForge | `neoforge/versions/26.1.2/run` | `neoforge/versions/26.1.2/build/libs` |

For example, `./gradlew.bat :fabric:1.21.1:runClient` runs that target;
`:neoforge:26.1.2:runClient` selects the other version/loader explicitly.
Optional mods belong in the selected game directory's `mods` folder, alongside
its own config, saves, screenshots and logs. Gradle already provides required
loader/Architectury/Fabric API dependencies; do not duplicate them there.
Optional JEI/FTB versions must match the actual target's supported API contract.
Root `versions/` is Stonecutter infrastructure, not a game directory. Pack Tester
uses `pack-tester/build` and local settings in `.pack-tester`.

Native fixture init scripts use target-local `build/native-fixtures` and separate
fixture common archives. Use one init script per invocation and inspect its
actual lifecycle before interpreting a pass. Examples:

```powershell
./gradlew.bat -I gradle/membership-native-hooks.init.gradle :fabric:1.21.1:runServer --console=plain
./gradlew.bat -I gradle/projectile-native-hooks.init.gradle :fabric:26.1.2:runServer --console=plain
./gradlew.bat -I gradle/guard-movement-native-hooks.init.gradle :neoforge:26.1.2:runClient --console=plain
./gradlew.bat -I gradle/glaive-visual.init.gradle :fabric:26.1.2:runClient --console=plain
```

Some fixtures exercise transformed classes and exit during initialization; others
need registry-bound disposable worlds. Glaive title-screen captures do not verify
in-world observer animation. The achievement fixtures include real world mechanics,
separate-process restart and an external packaged socket scenario; their commands
and exact scope are in [achievement acceptance](achievements/ACCEPTANCE.md).
[Audio guidance](audio/PASSDOWN.md) owns audio fixtures and listening boundaries.
Production archives must contain no test entrypoints/classes.

Isolated team tasks use the same node/toolchain, for example
`:fabric:1.21.1:runTeamBaselineServer`, `runTeamBaselineClientOne` and
`runTeamBaselineClientTwo`; replace `Baseline` with `Provider` for FTB Teams.
Working directories are `run/teams/baseline/{server,client-1,client-2}` and
`run/teams/ftb/{server,client-1,client-2}` under the selected loader/version node.
Each client has a separate username, UUID and directory. Add optional provider
runtime mods manually to those isolated `mods` folders; they are not bundled.
Configure EULA, port and online identity mode deliberately. Confirm distinct
server-side UUIDs before financial tests; separate directories alone are not proof.
Use dedicated state for authority-change tests instead of copying a financial
world between provider modes.

## Multiplayer and machine contracts

Solo is the default. Membership supports native teams and the registered **FTB
Teams** adapter; OpenPAC is not a supported current provider. Installed FTB APIs
are attempted without version allowlists. Temporary manager initialization
pauses access until two complete roster observations agree. Actual API failure
or removal automatically switches to native teams with the last confirmed
roster, exact player balances and recorded machine ownership preserved. The
fallback persists across restart; unknown old bindings remain unavailable.
Installation alone does not change an existing world's authority. The explicit
`/essence admin membership` prepare/status/activate flow validates its token,
financial/roster revisions, readiness and current roster. Preparation expires
at 6,000 server ticks; status does not extend it. Machine bindings from another
authority are not silently remapped by manual activation. Automatic failure
fallback has its own exact identity aliases and replays recorded ownership
history, including for unloaded machines; it does not equalize Stored again.

A player has **one active Crucible source** across a server. Valid Start at another
source replaces the previous link; duplicate Start cannot multiply throughput,
and invalid/None/out-of-range Start preserves the old link. Stop is scoped to
its source/player. One physical source fairly shares its Pylon-derived bandwidth
among all consumers. Player hopping cannot accumulate multiple source budgets.

`ReservoirService` aggregates/equalizes authoritative stored shares in
`PlayerEssenceData`, including conserved fractional remainders. There is no
second independently spendable treasury. Joining shares stored reservoir Essence;
already-channeled Available Essence remains personal. Departure keeps the equal
remaining stored share based on the pre-departure roster, not a contribution refund.

`MachineBinding` follows settled membership. An ordinary member's departure
leaves team infrastructure with the team and revokes that member's access. Final
native disband returns infrastructure to personal ownership. Ordered join and
dissolution metadata plus each binding's ownership cursor allow unloaded machines
to reconcile on load without storing another balance. Missing legacy cursors use
current authoritative membership. Placement while membership is unavailable
records a pending placer and clears old access until readiness returns.

Placement uses the placer/current team. Portable removal produces one item with
contents and paid work, clears the removed block entity and strips old binding,
link and provenance. Placement gives fresh ownership, including for older portable
items; paid Infuser work persists while transient authorization is refreshed.
Breaking follows vanilla and other protection mods. Normal placements have no
adoption/privatization step; explicit admin recovery is for unbound placements.

Pylons/Infusers automatically retain one eligible loaded source or discover the
nearest eligible same-owner/team Crucible in range. A newer closer machine does
not replace a valid link. Legacy locked/disconnected GUI state migrates to
Automatic, retaining a valid source. Advanced source commands never force loads.
Direct inventory/settings access remains owner/member-only. Automation follows
normal input/output/recipe rules, including Crucible insertion-only behavior.
Soulbound inputs require the owner to belong to the receiving pool; vanilla
hoppers and both loader adapters share the same simulation/rollback-aware policy.
There are no configurable port toggles or automation-recipient controls.

Each new equipment workpiece waits for explicit Start. Unbound team equipment
uses a one-use Soulbind & Start confirmation bound to the live menu, actor,
membership, machine binding, exact workpiece, Focus and context. Stale/replayed
requests reject. Solo Start needs no confirmation. Existing soulbound ownership
wins regardless of the upgrading player; component delivery to approved work
does not disarm it. Payment, output and inventory mutations remain transactional.

M opens native team controls on the machine screen and is hidden under external
authority. Outsiders get a slot-free access/request screen. A personal-machine
request leaves both players solo until explicit owner approval and team naming.
`MachineContainerScreen` owns one active side popup; Info, M and Settings are
mutually exclusive, Escape closes it first, replacement invalidates receipts,
resize retains typed input, and confirmation stays modal over its live context.
No second GUI framework or independent popup-state booleans should bypass this owner.

## Balance, assets and persistence

[Simple balance](simple-balance.md) and
[procedural Skill progression](procedural-skill-progression.md) own current
estimation and editable progression. Saved profiles load without regeneration;
explicit rebuild publishes validated changes while preserving existing human
settings and exact paid purchase history. Old candidate acquisition/quest
experiments are not current generator policy. Current estimates do not prove
acquisition, renewable supply, farming rates or affordability.

The asset manifest and shared AssetPipeline own generated resources; normal
builds use repository-promoted art/audio and never require a personal authoring
workspace. See [art guidance](../art/README.md), [audio guidance](audio/PASSDOWN.md)
and each feature guide for source provenance.

Recheck server authority at execution: live player, current menu, reach, loaded
physical block, ownership/binding revision and exact inputs. Preserve conserved
balances, refunds and fractional carry through failure/rollback. End transient
combat/movement actions on their documented lifecycle boundaries while retaining
spent cooldowns and durable player facts. Bound collections and input-amplified
work. Runtime rollback and serialization do not establish crash atomicity across
player, inventory, chunk and advancement files; Minecraft's normal save boundaries
remain. Current regression evidence and remaining coverage limits are in
[VALIDATION.md](VALIDATION.md).
