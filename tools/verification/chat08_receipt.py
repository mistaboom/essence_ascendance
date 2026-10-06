"""Finalize the explicitly selected local audit receipt and auxiliary passdown status."""
import hashlib, json, pathlib, sys

out = pathlib.Path(sys.argv[1]).resolve()
handoff = out.parents[2]
project = pathlib.Path(__file__).resolve().parents[2]
read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
assert 'BUILD SUCCESSFUL' in (out/'focus-throughput-final-build.log').read_text(encoding='utf-8-sig')
assert 'BUILD SUCCESSFUL' in (out/'final-regressions-build.log').read_text(encoding='utf-8-sig')
assert read(out/'protected-before.json') == read(out/'protected-after.json')
replay = out/'runtime-final-replay'
load, timing = read(replay/'load.json'), read(replay/'runtime-timing.json')
assert load['counts']['profile_reads'] == load['counts']['profile_json_parses'] == load['counts']['profile_decodes'] == 1
for key in ['full_generation_runs','evidence_collection_runs','runtime_generation_runs','competitive_capability_runs','adaptive_calibration_runs','report_export_runs']:
    assert load['counts'].get(key,0) == 0
semantic = read(replay/'semantic-profile.json')
assert len(semantic) == 8 and semantic == read(out/'saved-load-final/semantic-profile.json') == read(out/'runtime-replay/semantic-profile.json')
runtime, adaptive = read(replay/'runtime.json'), read(replay/'diagnostics/adaptive_balance.json')
assert runtime['infuser']['grades']['dormant']['infusionThroughputPerSecond'] >= runtime['infuser']['noFocusInfusionThroughputPerSecond']
builds = []
for loader in ['fabric','neoforge']:
    p = project/loader/'build/libs'/f'essence_ascendance-{loader}-1.0.0.jar'
    builds.append(dict(loader=loader,path=str(p),bytes=p.stat().st_size,sha256=hashlib.file_digest(p.open('rb'),'sha256').hexdigest()))
summary = dict(status='source_build_replay_pass_native_chat8_pending', builds=builds,
    protected_live_files_unchanged=6, saved_semantic_sections_unchanged=8,
    final_load_seconds=load['elapsedNanos']/1e9, final_runtime_replay_seconds=timing['elapsedNanos']/1e9,
    final_adaptive_millis=adaptive['calibrationNanos']/1e6,
    latent_throughput=runtime['infuser']['noFocusInfusionThroughputPerSecond'],
    dormant_throughput=runtime['infuser']['grades']['dormant']['infusionThroughputPerSecond'],
    tests=dict(capability=90,adaptive=7208,complete_profile=3482559,selection=35,economy=360,
        quest=53,loot=81,lifecycle=33,effective_production=90,provider=39,environment=90,performance=71,report=52),
    native_chat8_generation=False, gameplay=False, dedicated_server=False, multiplayer=False)
(out/'summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
hash_rows = '\n'.join(f"| {b['loader']} | {b['bytes']:,} | `{b['sha256']}` |" for b in builds)
receipt = f'''# Chat 8 validation — source, builds and isolated replay PASS

The final source fixes are built for Fabric and NeoForge. Native Chat 8 rebuild/gameplay acceptance is pending; no deployment, world/config/profile edit or Git staging/history action occurred. Existing Chat 7C native authority remains intact.

[Generic coverage/economy and ranked gaps](coverage-and-economy.md) · [Chat 8 passdown](../../../chats/chat-08.md) · [Machine-readable summary](summary.json).

## Verification

Final capability 90, adaptive 7,208, complete-profile 3,482,559, selection 35, economy 360, quest 53, loot 81, lifecycle 33, effective production 90, provider 39, environment 90, performance 71 and report 52 checks passed. Runtime generation/roundtrip/strict rejection, composition and new Focus baseline-floor regressions passed. These are deterministic/vanilla-bootstrap fixtures, not survival, Fabric gameplay, dedicated-server or multiplayer evidence.

Logs: [focused invariants](focused-final-retry.log), [integration and both builds](integration-build.log), [final lineage/scalability/adaptive/builds](final-regressions-build.log), [final Focus/runtime/complete-profile/builds](focus-throughput-final-build.log). Initial task lookup/cache-access errors were corrected; final tasks succeeded. No tests were disabled. Ordinary deployment does not run these opt-in heavy suites.

4096 independent hosts retained in 31.9623 ms with zero cross-host dominance comparisons; stage/confidence/cost tradeoffs and the 4096 bound are covered separately. This is synthetic pruning time, not native census time.

| Final loader | Bytes | SHA-256 |
| --- | ---: | --- |
{hash_rows}

## Read-only accepted-profile replay

Final replay load **{summary['final_load_seconds']:.4f} s**, runtime generation/diagnostics **{summary['final_runtime_replay_seconds']:.4f} s**, adaptive consumer work **{summary['final_adaptive_millis']:.4f} ms**. [Log](runtime-final-replay.log) · [load](runtime-final-replay/load.json) · [runtime timing](runtime-final-replay/runtime-timing.json) · [adaptive details](runtime-final-replay/diagnostics/adaptive_balance.json) · [identity](runtime-final-replay/identity.json).

Exactly one read/parse/decode; zero environment/provider/capability generation, adaptive recalculation or report export during saved loading. The runtime phase separately recalculates from accepted saved evidence and writes only this isolated directory. It cannot capture new optional-provider readiness or native configurations. Final installed-Latent throughput **{summary['latent_throughput']}**, Dormant **{summary['dormant_throughput']}** Essence/s in replay. Empty Focus slot remains zero. [Complete runtime comparison](runtime-final-replay-differences.json) retains every differing field without exclusions; offline reference/provenance differences are not a new native profile. Eight loaded saved-document semantic sections match all prior replay loads.

Initial replay loads were 37.1974 / 39.3357 s; first runtime replay 45.9925 s with 63.1418 ms adaptive work. All replay JVMs use 5 GiB heap. Historical Chat 2B optimized native missing generation/load 190.925 / 23.0178 s; historical controlled isolated optimized load median 25.025 s. Current accepted Chat 7C native rebuild/validation/restart 282.0865 / 39.8789 / 34.1827 s, adaptive 118.5033 ms. Workload/coverage/heap differ, so these are contextual observations, not a matched speedup claim. New native capability census/full generation/new-profile restart remain pending.

[Before](protected-before.json) and [after](protected-after.json) match for all six protected live files. Profile integrity remains `db64eaf656899f5ac98ccb796f7d10012b15098d22bd8e7a0dc9a858ae2f5ae8`; schema 2 only, direct reuse, missing automatic generation, explicit rebuild, whole units, checksums, atomic publication and previous retention remain preserved. No fingerprints, stale warnings, current-pack comparisons or migration were added.

## Native next step

User-operated Pack Tester **Build & Deploy**, existing-world saved reuse, one `/essence admin balance rebuild`, separate `/essence debug balance validate`, then full restart. Follow [passdown acceptance steps](../../../chats/chat-08.md). Keep the profile and human configuration; do not alter RAM. Survival first Essence/Latent/Focus/matrix/Dormant and six-category pacing require actual gameplay. Fabric launch, dedicated server, two-player Lootr and UI checks remain separate. Complete gear/spell/repair/acceleration operating providers remain important limitations; no universal modpack claim is made.
'''
(out/'README.md').write_text(receipt,encoding='utf-8')
relative = 'validation/chat-08/' + out.name
status = f'''# Current status — Chat 8 generic audit/repairs source, builds and replay PASS; native acceptance pending

Progression placement, static configured stacks, finite/exclusive setup lineage, fair unknown diagnostics, bounded pruning, spawn-radius calibration, guarded planter presence and Latent-to-Dormant Infuser throughput are repaired. Final capability 90 / adaptive 7,208 / complete-profile 3,482,559 checks and both production loaders PASS. [Coverage/economy and ranked gaps]({relative}/coverage-and-economy.md) · [Validation]({relative}/README.md) · [Chat 8 passdown](chats/chat-08.md).

All six protected files and eight saved semantic sections remain unchanged. Final isolated load {summary['final_load_seconds']:.4f} s; adaptive consumer {summary['final_adaptive_millis']:.4f} ms; saved reuse performs no scans/recalculation. Full native authority is still Chat 7C, not this replay. New provider capture, survival/category pacing, complete modular/spell/repair/acceleration support, Fabric gameplay, dedicated server and multiplayer remain explicitly limited. **Built, not deployed/native-accepted.** No assistant world/profile/human-config/RAM/Git history/staging change occurred. Prior statuses below are historical.

'''
for name in ['README.md','HANDOFF.md']:
    p = handoff/name
    old = p.read_text(encoding='utf-8-sig')
    assert not old.startswith('# Current status — Chat 8'), 'Already finalized; avoid duplicate status'
    p.write_text(status+old,encoding='utf-8')
print(json.dumps(summary,indent=2))
