"""Finalize the read-only Sky progression audit after coordinated checks; no game writes."""
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys
from compatibility_receipt import protected

out = Path(sys.argv[1]).resolve()
project = Path(__file__).resolve().parents[2]
instances = Path(r"C:\Users\rcboo\AppData\Roaming\PrismLauncher\instances")
handoff = out.parents[2]
game = instances / "All the Mods 10- To the Sky   ATM10SKY/minecraft"
read = lambda path: json.loads(path.read_text(encoding="utf-8"))
write = lambda path, value: path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
logs = {name: (out / name).read_text(encoding="utf-8", errors="replace")
        for name in ("verification.log", "final-verification.log")}
assert all("BUILD SUCCESSFUL" in log for log in logs.values()), "Coordinated build incomplete"
assert not any("BUILD FAILED" in log for log in logs.values()), "Build failure retained in final logs"
all_log = "\n".join(logs.values())
checks = {}
for name in ["SkillAccessTest", "GatheringGrowthTest", "ConfiguredRecipeAccessTest", "StarterTemplateEvidenceTest",
             "CompetitiveCapabilitiesTest", "AdaptiveCompetitionCalibrationTest", "GeneratedBalanceIntegrationTest",
             "GeneratedSkillAvailabilityTest", "RuntimeBalancePayloadTest"]:
    matches = re.findall(re.escape(name) + r": (\d+)", all_log)
    assert matches, f"Missing completed verification: {name}"
    checks[name] = int(matches[-1])
assert checks["GeneratedBalanceIntegrationTest"] > 3_000_000
baseline = read(out / "protected-before.json")
after = protected(instances)
write(out / "protected-after.json", after)
assert baseline == after, "Installed files changed during audit; inspect before claiming unchanged"
load = read(out / "saved-load/load.json")
assert load["outcome"] == "validated_offline" and not load["failureType"]
assert all(load["counts"][name] == 1 for name in ["profile_reads", "profile_json_parses", "profile_decodes"])
for name in ["full_generation_runs", "evidence_collection_runs", "runtime_generation_runs", "competitive_capability_runs", "adaptive_calibration_runs", "report_export_runs"]:
    assert load["counts"].get(name, 0) == 0, f"Saved load invoked {name}"
with gzip.open(game / "config/essence_ascendance/generated_balance.json.gz", "rt", encoding="utf-8") as stream:
    profile = json.load(stream)
assert profile["schema"] == 2
capabilities = profile["metadata"]["generation"]["competitiveCapabilities"]
artifacts = {}
for loader in ["fabric", "neoforge"]:
    path = project / f"{loader}/build/libs/essence_ascendance-{loader}-1.0.0.jar"
    artifacts[loader] = {"path": str(path), "bytes": path.stat().st_size,
                         "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
summary = {
    "status": "Source, focused/comprehensive checks, both production loaders and old saved-profile reuse PASS; new native generation pending user deployment/rebuild",
    "checks": checks, "artifacts": artifacts, "protected_files_unchanged": len(after),
    "installed_profile_integrity": profile["integrity"], "saved_load_seconds": load["elapsedNanos"] / 1e9,
    "saved_load_counts": load["counts"], "saved_load_flags": load["flags"],
    "installed_competitive_counts": capabilities["counts"],
    "availability_policy": "All registered skills evaluated using declared mechanical axes and generic substitutes, supported units, independent earlier attainable witnesses and prerequisite closure; unknowns retain catalog fallback",
    "native_acceptance": "Not performed for new adapters or generated access tiers; existing profile and crop action acceptance remain historical evidence",
    "assistant_game_or_installed_writes": False,
}
write(out / "summary.json", summary)
hash_rows = "\n".join(f"| {loader} | {value['bytes']} | `{value['sha256']}` |" for loader, value in artifacts.items())
check_rows = "\n".join(f"| {name} | {count:,} |" for name, count in checks.items())
receipt = f'''# Sky progression audit and reusable availability repair

The installed profile passed structural validation and one ordinary crop-action check, but those checks did not establish competitive Sky progression. The saved census admits only 15 of 82 generic axes. This audit inspected the actual pack's loaded-data snapshots, recipes, configs, templates and native implementations, and reviewed all 90 skills. It identified reusable coverage and availability defects and repaired them in development. New native capture and gameplay acceptance remain pending.

## Reusable decisions

Every registered skill now receives a generated access-tier decision from its declared mechanical axes and supported comparable functions. An earlier attainable witness with confidence at least 0.5 can lower placement; ordinary weapon/armor statistics, unsupported units, late-only sources and unknown setups cannot. Dependency closure preserves prerequisite ranks. Rank-specific gates, milestones and live requirements remain required. Prices, runtime projections, saved curves, client data and reports use the same decision. `skill_availability.csv` records catalog/candidate/final tiers, considered axes, the actual witness and retained requirements. Catalog tiers remain conservative defaults when evidence is insufficient. There is no pack-name branch or Sky-specific skill tier override.

Squat Grow's actual free, default-enabled grounded crouch action provides 0.5 native bonemeal chance per eligible target/action from ENTRY. The engine can therefore choose Dormant Verdant Stride and its corresponding price, native crop/sapling bonemeal mode and a bounded one-action/second passive scenario. This cadence is a declared design assumption, not measured player crouch frequency or a promise of identical sustained throughput. Loaded-chunk/target-work/overlap budgets and native success conditions remain enforced. Old saved growth JSON retains its previous shape and random-tick behavior.

Iron Jetpacks reads loaded definitions and exact default-retaining native component craft predicates. The capability-only solver requires independent renewable material and crafting-grid setup, plus audited charging/fuel proof. Finite initial logs cannot certify arbitrarily large craft bills; native velocity flight is not a standard flight-speed attribute contract. Each configuration is guarded independently. Complete wood flight access remains unproved in the old snapshot; the engine must not force an early tier based on a quest label or pack category.

The SkyblockBuilder start adapter is gated by the active native generator and default team-creation permission. It intersects every exclusive loaded template/palette, reads literal configured inventory and uses pure native hand-drop evidence. All seven configured islands have at least 4 oak logs in common. Those are a finite TEAM-scoped initial budget, with no renewable tree, callback contents, economic price or throughput claim.

TIAB captures its actual 128 extra ticker calls, 30-second funded burst and 76,800-stored-tick ladder. Useful host work, fuel/input access and sustained uptime remain conditional. It can inform Industrious Presence's access and bounded strength without making machines continuously 129× productive. Torchmaster captures both filters and actual hostile/passive categories; the passive Dread Lamp is complementary to hostile Sanctuary. Source-bound unbreakability requires positive native wear and cannot strengthen universal tool preservation or mending.

## Verification

| Suite | Passed checks |
|---|---:|
{check_rows}

[Coordinated checks/build](verification.log) · [Final candidate projection/profile/load/build checks](final-verification.log). Real native game dependencies are used; installed optional API capture remains a separate native acceptance step. Final focused checks include legacy shape, native crop/sapling behavior, exact defaults, finite-only rejection, crafting setup, isolated candidates, earlier flight price, unknown/late evidence, finite/exclusive templates, typed spawn domains and optional compatibility failure continuation.

| Loader artifact | Bytes | SHA-256 |
|---|---:|---|
{hash_rows}

The old installed Sky profile still decodes and validates in an isolated read-only replay in {summary['saved_load_seconds']:.4f}s, with exactly one read/parse/decode and zero generation/capability/adaptive/report work. [Load](saved-load/load.json) · [Identity](saved-load/identity.json). The profile, human configs, installed JARs and saved-world file identities stayed unchanged across all {len(after)} protected paths. [Before](protected-before.json) · [After](protected-after.json). No assistant deployment, game/world/config write, RAM change, Git staging or history change occurred.

## Actual audit coverage and limitations

[All 90 skills functional matrix](broader-capability-audit.md) · [Flight evidence](flight-audit.md) · [Growth/gathering evidence](growth-gathering-audit.md) · [Finite starter proof](starter-sources-audit.md). Automation, renewable tree/setup closure, custom machine/recipe callbacks, food/status consumption, complete spell/affix/module builds and survival pacing still contain explicit unknowns. The new native generation must confirm which actual configurations are admitted and the resulting tiers/prices; the current running profile still has the previous balance. This report makes no claim that every mechanic is fully balanced.

## User-operated native next step

Close the game; Build & Deploy through Pack Tester; reopen the existing Sky world. Run `/essence admin balance rebuild`, then `/essence debug balance validate`. Restart and validate again. Preserve the existing profile and human configuration; the rebuild publishes a validated candidate atomically and retains the previous profile on failure. Inspect generated availability rows and new provider/candidate diagnostics before calling Sky competitive progression accepted. Native ability behavior and normal Survival chapter pace remain separate tests. Ocean and StoneBlock should retain their own independently generated profiles.
'''
(out / "README.md").write_text(receipt, encoding="utf-8")
relative = f"validation/chat-08/{out.name}"
status = f'''# Current status — Sky competitive progression audit repaired; new native balance pending

The previous native profile and ordinary crop action remain accepted, but they did not prove competitive Sky progression. Actual installed mechanics exposed missing early growth/flight/acceleration evidence and false preservation/spawn comparisons. All 90 skills now pass through reusable evidence-driven availability selection; matching earlier functions can lower tiers and prices, with prerequisite closure and conservative catalog fallback. No ATM/Sky pack-name balance exception was added.

Both loader artifacts, focused native dependency checks, complete-profile checks and read-only reuse of the old Sky profile pass. The source changes are built, not deployed/native-accepted. User next: close Sky, Pack Tester Build & Deploy, explicit `/essence admin balance rebuild`, validate, restart, validate; then inspect generated skill availability/providers and test Survival behavior. Native wood flight remains unknown until complete renewable material/charger proof is admitted. Review of all 90 skills retains automation, tree/setup, food, spell/affix/module and pacing gaps. [Audit/verification]({relative}/README.md) · [Full skill-family matrix]({relative}/broader-capability-audit.md). Earlier status blocks are historical.

'''
for name in ["README.md", "HANDOFF.md"]:
    path = handoff / name
    old = path.read_text(encoding="utf-8-sig")
    assert not old.startswith("# Current status — Sky competitive progression audit repaired"), "Status already finalized"
    path.write_text(status + old, encoding="utf-8")
path = handoff / "chats/chat-08.md"
path.write_text(status.replace(f"]({relative}/", f"](../{relative}/") + path.read_text(encoding="utf-8-sig"), encoding="utf-8")
path = project / "docs/pack-compatibility.md"
old = path.read_text(encoding="utf-8-sig")
intro = '''## Generated skill availability and Sky progression audit (2026-10-05)

All registered skills now receive an access-tier decision from declared mechanical axes and supported earlier attainable functional witnesses. Prerequisite closure, explicit rank gates, milestones and live requirements remain required; insufficient or unrelated evidence keeps the catalog fallback. Generated prices, projections, saved/client curves and `skill_availability.csv` share the same placement. No pack-name-specific balance tier exists. Candidate generation and diagnostics are isolated from previously installed curves; legacy absent availability/growth fields retain their original decode and JSON shape.

Optional adapters now capture actual free crouch bonemeal growth, configured powered flight and its exact static crafting/charging proof, bounded stored-time acceleration, finite exclusive starter templates and typed spawn-filter domains. Verdant can gain early native crop/sapling growth from genuine free growth evidence. Finite starter logs are not renewable craft supply; an unbreakable energy item is not universal tool preservation; passive spawn control is not hostile protection. Each optional provider/configuration remains guarded, warning and excluding failed evidence while independent integrations continue.

The coordinated checks, complete-profile checks, both loader builds and read-only old Sky saved-profile load pass. New native generation/ability gameplay remain pending user Build & Deploy and explicit rebuild/validate/restart/validate. Earlier native profile/crop acceptance below describes the previous installed authority and does not prove full competitive balance. The auxiliary `validation/chat-08/20261005-sky-progression-audit` receipt includes all 90 skills, actual pack mechanics, build identities, protected files and explicit remaining automation/tree/setup/food/spell/affix/module/pacing gaps.

'''
assert old.startswith("# Pack Tester\n")
path.write_text(old.replace("# Pack Tester\n\n", "# Pack Tester\n\n" + intro, 1), encoding="utf-8")
print(json.dumps({"status": "PASS", "checks": checks, "protected_unchanged": len(after), "old_profile_load_seconds": summary["saved_load_seconds"]}, indent=2))
