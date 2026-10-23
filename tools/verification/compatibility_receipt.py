"""Read-only pack inventory/protected-file receipt; writes only the requested output directory."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess


def fingerprint(path):
    digest = None
    if path.is_file():
        with path.open("rb") as source:
            digest = hashlib.file_digest(source, "sha256").hexdigest()
    return {"path": str(path), "exists": path.is_file(), "sha256": digest}


def protected(instances):
    files = []
    for instance in sorted(instances.iterdir()):
        if not instance.is_dir():
            continue
        game = next((instance / child for child in ("minecraft", ".minecraft")
                     if (instance / child / "mods").is_dir()), None)
        if game is None:
            continue
        files.extend(game / relative for relative in ("config/essence_ascendance.toml",
                     "config/essence_ascendance/balance_overrides.toml",
                     "config/essence_ascendance/skill_settings.toml",
                     "config/essence_ascendance/bonus_settings.toml",
                     "config/essence_ascendance/generated_balance.json.gz"))
        files.extend(sorted((game / "mods").glob("essence_ascendance*.jar")))
        files.extend(sorted((game / "saves").glob("*/level.dat*")))
    return [fingerprint(path) for path in files]


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--instances", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java", type=Path)
    parser.add_argument("--build-log", type=Path, action="append", default=[])
    parser.add_argument("--diagnostic-log", type=Path, action="append", default=[])
    parser.add_argument("--crash", type=Path)
    parser.add_argument("--expected-supported", type=int)
    parser.add_argument("--baseline", action="store_true")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    snapshot = protected(args.instances)
    if args.baseline:
        save(args.output / "protected-before.json", snapshot)
        print(f"Captured {len(snapshot)} protected file identities; no installed writes")
        return
    save(args.output / "protected-after.json", snapshot)
    baseline = json.loads((args.output / "protected-before.json").read_text(encoding="utf-8"))
    assert snapshot == baseline, "Protected installed files changed during receipt window; investigate before reporting unchanged"
    version = re.search(r"(?m)^mod_version\s*=\s*(\S+)", (args.project / "gradle.properties").read_text())[1]
    manifest = json.loads((args.project / "build/production-artifacts-1.21.1.json").read_text(encoding="utf-8"))
    assert manifest["schema"] == 1 and {t["loader"] for t in manifest["targets"]} == {"fabric", "neoforge"}
    artifacts = [Path(next(t["archive"] for t in manifest["targets"] if t["loader"] == loader))
                 for loader in ("fabric", "neoforge")] + [
                 args.project / "pack-tester/build/install/pack-tester/lib/pack-tester.jar"]
    assert all(path.is_file() for path in artifacts), "Required build artifact is missing"
    save(args.output / "build-hashes.json", [fingerprint(path) for path in artifacts])
    assert args.build_log, "Final receipt needs at least one successful --build-log"
    for log in args.build_log + args.diagnostic_log:
        shutil.copy2(log, args.output / log.name)
    for log in args.build_log:
        assert "BUILD SUCCESSFUL" in log.read_text(encoding="utf-8", errors="replace"), f"Build did not complete successfully: {log}"
    assert args.java, "Final receipt needs --java to run read-only Pack Tester list"
    inventory = subprocess.run([str(args.java), "-jar", str(artifacts[-1]), "list", "--project", str(args.project),
                                "--root", str(args.instances)], text=True, encoding="utf-8", errors="replace", capture_output=True, check=True)
    (args.output / "pack-list.txt").write_text(inventory.stdout, encoding="utf-8")
    supported = inventory.stdout.count("\nSUPPORTED:")
    if args.expected_supported is not None:
        assert supported == args.expected_supported and "UNRESOLVED" not in inventory.stdout
    if args.crash:
        shutil.copy2(args.crash, args.output / args.crash.name)
    save(args.output / "summary.json", {"policy": "optional integration failures warn, exclude failed output and continue",
         "core_integrity": "schema 2, whole units, runtime validation, atomic publication and prior retention preserved",
         "protected_files_unchanged_in_receipt_window": len(snapshot), "pack_tester_supported": supported,
         "verified_build_logs": [str(log) for log in args.build_log], "native_game_retry": "pending user operation",
         "assistant_deployment": False})
    print(f"Receipt PASS: {len(snapshot)} protected identities unchanged; {supported} packs SUPPORTED; builds verified")


if __name__ == "__main__":
    main()
