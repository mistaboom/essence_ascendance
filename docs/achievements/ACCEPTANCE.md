# Achievement acceptance evidence

All 69 selected achievements are implemented and passed the automated and
controlled native verification described below on Minecraft 1.21.1 and 26.1.2,
Fabric and NeoForge. The user accepted the final reviewed visual result
(“looks great”) on 2026-11-07. The tab reads **Essence Ascendance**; the selected
root, ID 22, retains **Equivalent Exchange** in its tooltip and toast.

This records completed verification, not a human gameplay run of all 69.
[STATUS.md](STATUS.md) retains every ID's source hook, exact action, reset policy,
icon and positive/negative test references. [CONTRACT.md](CONTRACT.md) defines
behavior. Disposable logs, worlds and screenshots may be cleaned; the compact
counts and exact archive identities below preserve the scope of these results.

## Design and source verification

The unchanged source workbook SHA-256 is
`dc52451bd59c84e98e49b450789146f6a3e51f0d01d1173b3f45c271a1f9dd70`.
The source audit reconciled 69 IDs/names/actions, 414 annotation cells,
207 source-row references and 16 capability labels against [catalog.json](catalog.json).
Normal builds consume checked-in definitions and do not require that workbook.
The coverage audit checks 69 unique IDs and 91 existing method references;
reference validation is separate from executing those tests.

ID 22 is the selected root, with no additional structural achievement. Legacy
Water Skiing remains outside the 69, retains `travel`, has no display and does
not migrate to ID 1. All icons reuse existing ItemStacks. No gameplay reward,
skill, progression gate, balance change or new art was added.

## Full gameplay verification

These 2026-11-06/07 runs used the **full-gameplay archive set** below. All four
native targets passed; none was skipped. Java 25 drove sequential Gradle runs
with the catalog's Java 21 toolchain for 1.21.1 and editor-active source 1.21.1.

| Verification | Result and scope |
| --- | --- |
| `checkSupportedTargets` | PASS, 21m37s; 324 tasks, 319 executed and 5 up-to-date. Both common checks, all four production/archive asset checks and Pack Tester regression passed. |
| Selected common suite counts, each version | Foundation 214,839; combat sequences 100,289; movement sessions 60; gathering rules 53. Presentation 3,079 / 3,128 for 1.21.1 / 26.1.2. |
| Controlled native mechanics, all four targets | PASS. Foundation/progression 523; machine 540 / 566; combat 257; rod 257; movement 2,733 / 2,742; survival 138 / 142; companion 79; farming 38; gathering 275 assertions per target. Paired counts are 1.21.1 / 26.1.2. |
| Separate-process native achievement restart, all four | PASS, 14 prepare and 17 resume assertions per target; all eight processes saved/stopped normally. |
| Exact packaged startup/world persistence, all four | PASS, two processes per target; scoreboard/storage plus Nether/End block persistence. A data-load log alone was not treated as proof of advancement-manager replacement. |
| Packaged two-client session, all four | PASS, 88 server assertions per target, actual native toast rendering and independent inspection of both players' saved advancements. Each server, owner client, operator client and reconnected client process exited zero. |
| Release-selection / packaged-launcher regression | PASS, 25 tests / 6 tests. |

Native mechanics use real source hooks, transactions, damage/death, movement
handlers and world operations with controlled players, skills, terrain, clocks
and trajectories. Per-ID limitations stay explicit in STATUS. Restart tests
cover genuine pre-shutdown dissolution and consumed-item partials, earned native
completion, seeded legacy progress without ID 1 migration, original unpaid
Infuser job attribution/physical source/receipt, final payment/production and
revoke persistence. Same-life state has codec/native player-record coverage;
this restart fixture does not establish every same-life challenge across a JVM restart.

The packaged session uses two real loopback clients and an independently hashed
external fixture, with no production classes overridden and unchanged installed
mod hashes. Native inventory/menu clicks feed a shared Crucible. Input alone
cannot grant; actual dissolution awards ID 22 and renders a toast only for the
capped operator, not the teammate/machine owner. A new process/socket/player body
reconnects with the same UUID, transfers to the Nether, retains progress across
full advancement-manager/holder replacement, and re-earns after revoke through
a new genuine transaction. A **separate synthetic phase** grants/revokes the
selected tree while preserving legacy progress and the other player's genuine
completion. That phase proves all-69 command scope/display wiring, not gameplay
for all 69. Saved advancement paths are `world/advancements/<uuid>.json` on
1.21.1 and `world/players/advancements/<uuid>.json` on 26.1.2.

Integration regressions cover duplicate combat victims allocating no empty action
groups; route gaps baselining the live momentum reservoir without regranting
unchanged maximum momentum; generated Latent Ore proof invalidation on attempted
same-state writes and A-to-B-to-A copies; and indexed receipt expiry preserving
actor isolation, out-of-order delivery, rewind and replay policy. The native Rush
case observes each tick and verifies the full-reservoir premise before testing
the gap. The ore positive uses a distinct genuinely generated block because
attempt-based invalidation can precede a canceled native write.

`CombatSequenceTest.EntityTypeConsumer` demonstrates a non-achievement consumer
of repeated-type/distinct-type queries with immutable actor-scoped facts and no
achievement imports or award changes. Sequence limits are 256 actors, 64 groups
per actor, 64 victims per group and 32 ordered kills per actor. Rod state is at
most 256 sessions for less than 200 ticks. Movement has at most 256 observers,
three trajectories per actor, 1,024 obstacles/boosts and 64 pending candidates.
Dispatch/query work is bounded, not claimed constant-time at every capacity.
The receipt workload exercised 100,000 commits plus 100,000 duplicate deliveries,
peaked at 2,000 keys and expired to one fresh key after idle. Its limits remain
4,096 keys and 200 tick buckets. The combat workload rejected 100,000 duplicates
without new groups. Measured full-gate times were 95 / 42 ms for receipt work and
58 / 37 ms for combat duplicate work; these are observations, not performance guarantees.

## Final title and visual acceptance

The only subsequent production change in the **title-update archive set** was
the client tab label for the exact selected root. It did not change the root
resource, criterion, native saves or gameplay. Full gameplay/socket/restart
results above are not relabeled as runs on these later hashes.

Affected checks passed in 1m7s: 72 tasks, 47 executed and 25 up-to-date;
foundation 214,839 per version, presentation 3,079 / 3,128, production manifest
and all four packaged asset checks. Native client rendering then passed on all
four targets, with 69 tree/widget/tooltip/toast captures each:

| Target | Native checks | Time |
| --- | ---: | --- |
| 1.21.1 Fabric | 4,393 | 54 s |
| 1.21.1 NeoForge | 4,393 | 48 s |
| 26.1.2 Fabric | 1,445 | 40 s |
| 26.1.2 NeoForge | 1,445 | 42 s |

These fixtures render in a hidden native window at the title screen, not a new
joined-world playthrough. They check vanilla/other-mod control-tab names and all
selected widget/toast names. The final 1.21.1 Fabric root image was visually
inspected, and the user subsequently accepted the reviewed visual result.
Human audio listening, every preferred GUI scale, third-party resource packs and
a human gameplay run of every achievement are not claimed.

The refreshed `phase9-tab-title` review kit passed native construction, normal
save, cold-load, ten saved station/guide/equipment/route checks and final
Creative/cheats/spawn/name validation using the title-update 1.21.1 Fabric jar.
Both server processes exited zero. Its Prism archive had 79 unique safe entries,
the native save and exactly three runtime mods whose hashes matched the receipt.
The exact accepted kit identities are:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Prism import ZIP | 13237405 | `23c93e5c59ab11a112e8a13aab77deaad1b5b872534d9a738a0a1027e3b7a37a` |
| Save-only ZIP | 399968 | `8bf4a6a717b0cc5c1abf707b4d7fafaa2873c79285518aacd6028c0db94b2421` |
| README | 4779 | `e0a53483c9af2414aac56f9fd62ef5458901ee271adbdea48bf5a4b69cc18d12` |

These are dated evidence identities, not promises that disposable build outputs
remain present. [REVIEW-WORLD.md](REVIEW-WORLD.md) describes regeneration.

## Exact tested archive identities

Both sets were packaged as `essence_ascendance-1.0.0-beta.14+mc<version>-<loader>.jar`.
Providers are `:<loader>:1.21.1:remapJar` and `:<loader>:26.1.2:shadowJar`, selected
from the production manifest rather than a filename glob. Subsequent release
packaging has its own version/hash; these dated results do not silently certify
new bytes.

| Set | Target | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Full gameplay | 1.21.1 Fabric | 7845940 | `6be6c818fd6df644784906a0883b71f91230717b4fe7f114b687844e19431785` |
| Full gameplay | 1.21.1 NeoForge | 7896854 | `a180ae24e40e909461b41a7c4f5eeebdd7493162c2b63055a97eee4f84dffb09` |
| Full gameplay | 26.1.2 Fabric | 8703255 | `750aaba92cfd252ea945faa08cea754ab0179e42f8723d6d7d8e3f8c40f23744` |
| Full gameplay | 26.1.2 NeoForge | 8709917 | `6c71086ad4bd9f995fd99f3f0a7bc67816394bab0bb34efa21c44ea51cd74fc8` |
| Title update | 1.21.1 Fabric | 7846987 | `724fb2986aae8eafc608f50b2bbb362a421086abc649954ed5202bb295b6f6a3` |
| Title update | 1.21.1 NeoForge | 7897950 | `e78243f969815f72042ebec56c906240f90ee5e0883af9f485668a6ff688f910` |
| Title update | 26.1.2 Fabric | 8704338 | `331648d4120d3cd26e5fbc1c4a0fdcbb93cd89cd6683cc5ac249ed2b85c8de96` |
| Title update | 26.1.2 NeoForge | 8711000 | `3766b44a6272788cdace77a73e6e3c1f1f4ed9e85ca069bbeca2058f9f5f73f8` |

## Reproducing checks

Run Gradle invocations sequentially. Substitute each supported loader/version
pair for the native commands. Use fresh labels for disposable launcher worlds.

```text
python tools/verification/achievement_source_audit.py <workbook.xlsx>
python tools/verification/achievement_coverage_audit.py
gradlew.bat :checkSupportedTargets --console=plain
gradlew.bat -I gradle/achievement-native-hooks.init.gradle :<loader>:<version>:runServer --console=plain
gradlew.bat -I gradle/achievement-native-hooks.init.gradle :<loader>:<version>:runServer -PachievementRestartStage=prepare --console=plain
gradlew.bat -I gradle/achievement-native-hooks.init.gradle :<loader>:<version>:runServer -PachievementRestartStage=resume --console=plain
gradlew.bat -I gradle/achievement-presentation.init.gradle :<loader>:<version>:runClient --console=plain
gradlew.bat -I gradle/achievement-socket.init.gradle :<loader>:<version>:achievementSocketFixtureJar --console=plain
python tools/verification/achievement_packaged_acceptance.py --help
python -m unittest discover -s tools/verification -p test_achievement_packaged_acceptance.py
```

The source audit is optional when the personal workbook is unavailable. The
packaged launcher defaults to exact-manifest startup/persistence checks; pass
`--target <target> --socket-fixture <exact fixture jar> --label <fresh-label>`
for the two-client scenario. Fabric 1.21.1's fixture is
`build/native-fixtures/achievement-socket-fixture.jar` under that target;
the other targets use `achievement-socket-native-fixture.jar` there. The external
fixture is test-only and is never a production runtime dependency.

Natural-origin evidence for ordinary ore expires or unloads; unknown old ore
cannot be inferred natural. Generated Latent Ore proof persists. Disabling
eligible End ore or Nature's Boon can make ID 182 unavailable. Unknown crop
mutation schemas need an adapter. Datapacks deleting advancement definitions can
cause native saved progress for those definitions to be dropped. Normal Minecraft
save boundaries apply; there is no cross-file crash-atomic journal.
