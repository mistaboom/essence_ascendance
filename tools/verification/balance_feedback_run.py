"""Run the existing isolated native fixtures and retain a reproducible balance receipt.

Never opens Prism installations. RCON credentials are read internally by the
existing console helper. Every JVM is an owned, hidden child and is stopped
after saving its worlds. Run fixtures sequentially for useful timings.
"""
from pathlib import Path
import argparse
import gzip
import hashlib
import json
import re
import shutil
import socket
import subprocess
import sys
import time

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import native_server_console as console


def save(path, value):
    path.write_text(json.dumps(value, indent=2), encoding="utf-8")


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture", choices=["vanilla", "atm10", "atm10-sky", "stoneblock", "oceanblock"])
    parser.add_argument("label")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--fresh", action="store_true")
    parser.add_argument("--repeat", type=int, default=2)
    parser.add_argument("--restart", action="store_true")
    parser.add_argument("--command", action="append", default=[])
    parser.add_argument("--onboarding-effort", type=float)
    parser.add_argument("--jar", type=Path, help="Explicit baseline/candidate artifact; default is this checkout's NeoForge build")
    args = parser.parse_args()
    fixture = json.loads((ROOT / "build/pack-matrix/fixtures.json").read_text())[args.fixture]
    server = Path(fixture["server"]).resolve()
    assert server.is_relative_to((ROOT / "build").resolve()), server
    assert args.repeat >= 0
    destination = args.output.resolve() / args.fixture / args.label
    destination.mkdir(parents=True, exist_ok=False)
    console.SERVER = server
    save(destination / "arguments.json", {**vars(args), "output": str(args.output),
                                          "jar": str(args.jar) if args.jar else None, "server": str(server)})
    with socket.socket() as probe:
        assert probe.connect_ex(("127.0.0.1", fixture["consolePort"])) != 0, "Fixture is already running"
    java = shutil.which("java")
    assert java, "Java 21 required"
    version = re.search(r"(?m)^mod_version\s*=\s*(\S+)", (ROOT / "gradle.properties").read_text())[1]
    jar = args.jar.resolve() if args.jar else ROOT / "neoforge/build/libs" / f"essence_ascendance-neoforge-{version}.jar"
    assert jar.is_file(), jar
    old_jars = list((server / "mods").glob("essence_ascendance*.jar"))
    (destination / "prior-jars").mkdir()
    for old in old_jars:
        assert old.resolve().is_relative_to(server / "mods")
        old.replace(destination / "prior-jars" / old.name)
    installed_jar = server / "mods" / jar.name
    shutil.copy2(jar, installed_jar)
    installed_sha = digest(installed_jar)
    save(destination / "jar.json", {"path": str(jar), "installedPath": str(installed_jar),
                                     "sha256": installed_sha, "version": version})
    config = server / "config/essence_ascendance"
    profile = config / "generated_balance.json.gz"
    if profile.exists():
        shutil.copy2(profile, destination / "prior-saved-profile.json.gz")
        if args.fresh:
            assert profile.resolve().is_relative_to(server)
            profile.unlink()
    if args.onboarding_effort is not None:
        assert 0.1 <= args.onboarding_effort <= 1
        candidates = [p for p in (server / "config").glob("*.toml") if "onboarding_effort_fraction" in p.read_text(encoding="utf-8")]
        assert len(candidates) == 1, candidates
        human = candidates[0]
        shutil.copy2(human, destination / ("prior-" + human.name))
        value, count = re.subn(r"(?m)^onboarding_effort_fraction\s*=.*$",
                              f"onboarding_effort_fraction = {args.onboarding_effort}", human.read_text(encoding="utf-8"))
        assert count == 1
        human.write_text(value, encoding="utf-8")

    def commands(values, name):
        rows = list(console.commands(values))
        save(destination / name, rows)
        for row in rows:
            command, response = row["command"], row["response"]
            if command.endswith("balance rebuild"):
                assert "Generated balance installed" in response, row
            if command.endswith("balance validate"):
                assert "validation passed" in response, row
            if command.startswith("essence test "):
                assert "PASS" in response and "FAIL" not in response, row
        print(json.dumps([{"command": r["command"], "seconds": r["seconds"]} for r in rows]), flush=True)
        return rows

    def capture(name):
        shutil.copy2(profile, destination / f"{name}.json.gz")
        document = json.loads(gzip.decompress(profile.read_bytes()))
        curves = document["runtime"]["skillCurves"]
        save(destination / f"{name}-census.json", {"skills": len(curves), "ranks": sum(len(c["ranks"]) for c in curves.values()),
                                                   "resources": len(document["evidence"]["resources"]), "integrity": document["integrity"]})

    def launch(suffix):
        log = destination / f"startup{suffix}.log"
        handle = log.open("w", encoding="utf-8")
        process = subprocess.Popen([java, "@user_jvm_args.txt", f"@libraries/net/neoforged/neoforge/{fixture['version']}/win_args.txt", "nogui"],
                                   cwd=server, stdout=handle, stderr=subprocess.STDOUT, stdin=subprocess.PIPE,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        save(destination / f"owned-process{suffix}.json", {"pid": process.pid, "server": str(server), "log": str(log)})
        print(json.dumps({"phase": "starting", "fixture": args.fixture, "pid": process.pid}), flush=True)
        deadline, last = time.monotonic() + 1800, time.monotonic()
        while time.monotonic() < deadline:
            content = log.read_text(encoding="utf-8", errors="replace")
            if "RCON running on 127.0.0.1:" in content:
                return process, handle, log
            if process.poll() is not None:
                handle.close()
                raise RuntimeError(f"Native startup failed: {log}")
            if time.monotonic() - last >= 30:
                print(json.dumps({"phase": "startup", "fixture": args.fixture, "lastLine": content.splitlines()[-1][-300:] if content else ""}), flush=True)
                last = time.monotonic()
            time.sleep(1)
        try:
            process.stdin.write(b"stop\n")
            process.stdin.flush()
            process.wait(timeout=45)
        except (BrokenPipeError, subprocess.TimeoutExpired):
            assert "All dimensions are saved" in log.read_text(encoding="utf-8", errors="replace"), "Startup timeout with unconfirmed world save; owned JVM retained"
            process.terminate()
            process.wait(timeout=15)
        finally:
            handle.close()
        raise TimeoutError(f"Startup exceeded deadline: {log}")

    def stop(process, handle, log):
        try:
            if process.poll() is None:
                try:
                    commands(["stop"], log.stem + "-stop.json")
                except (ConnectionError, OSError) as error:
                    save(destination / (log.stem + "-disconnect.json"), {"error": str(error)})
                try:
                    process.wait(timeout=45)
                except subprocess.TimeoutExpired:
                    assert "All dimensions are saved" in log.read_text(encoding="utf-8", errors="replace"), "World save incomplete; owned JVM retained"
                    process.terminate()
                    process.wait(timeout=15)
        finally:
            handle.close()
        assert "All dimensions are saved" in log.read_text(encoding="utf-8", errors="replace"), "Missing clean world save"
        print(json.dumps({"phase": "stopped", "fixture": args.fixture, "pid": process.pid}), flush=True)

    def hashes():
        files = [p for p in config.rglob("*") if p.is_file()]
        files.append(server / "config/essence_ascendance.toml")
        return {str(p.relative_to(server / "config")): digest(p) for p in files}

    process, handle, log = launch("")
    try:
        commands(["essence debug balance validate"], "initial-validation.json")
        capture("initial")
        for iteration in range(args.repeat):
            commands(["essence admin balance rebuild", "essence debug balance validate"], f"rebuild-{iteration}.json")
            capture(f"profile-{iteration}")
        commands(["essence test skills effects", *args.command], "effects.json")
        document = json.loads(gzip.decompress(profile.read_bytes()))
        expected = [(skill, rank, curve, point) for skill, curve in sorted(document["runtime"]["skillCurves"].items())
                    for rank, point in enumerate(curve["ranks"], 1)]
        rows = list(console.commands([f'essence debug balance skill "{s}" {r}' for s, r, _, _ in expected]))
        save(destination / "rank-console.json", rows)
        for row, (skill, rank, curve, point) in zip(rows, expected, strict=True):
            response = re.sub(r"\u00a7.", "", row["response"])
            assert skill in response and curve["requiredTierId"] in response, (skill, rank, response)
            cost_match = re.search(r"(?m)^\s*Cost:\s*([\d,]+)\s*$", response)
            rank_match = re.search(r"(?m)^\s*Rank:\s*Selected\s+(\d+)\s*\|", response)
            assert cost_match and int(cost_match[1].replace(",", "")) == point["cost"], (skill, rank, response)
            assert rank_match and int(rank_match[1]) == rank, (skill, rank, response)
        save(destination / "rank-console-summary.json", {"ranks": len(rows), "passed": True})
        tracks = sorted(document["runtime"]["balanceProfile"]["bonusTracks"].items())
        bonus_rows = list(console.commands([f'essence debug balance bonus "{bonus}"' for bonus, _ in tracks]))
        save(destination / "bonus-console.json", bonus_rows)
        tier_rows = active_endpoints = 0
        for row, (bonus, track) in zip(bonus_rows, tracks, strict=True):
            response = re.sub(r"\u00a7.", "", row["response"])
            assert bonus in response and track["applicability"] in response, (bonus, response)
            values = re.findall(r"Cumulative / segment\s+([\d,]+)\s*/\s*([\d,]+)\s*\|\s*effect\s+(-?[\d.]+)", response)
            assert len(values) == len(track["checkpoints"]), (bonus, response)
            for (cap, segment, effect), point in zip(values, track["checkpoints"], strict=True):
                assert int(cap.replace(",", "")) == point["cumulativeCap"], (bonus, point, response)
                assert int(segment.replace(",", "")) == point["segmentCost"], (bonus, point, response)
                # Native text rounds to two or three decimals depending on the unit.
                # This checks the installed readout, not an actual purchase or gameplay activation.
                precision = len(effect.partition(".")[2])
                assert abs(float(effect) - track["maximumEffect"] * point["effectFraction"]) <= .5 * 10 ** -precision + 1e-9, (bonus, point, response)
                tier_rows += 1
                active_endpoints += int(point["purchasable"])
        save(destination / "bonus-console-summary.json", {"tracks": len(bonus_rows), "tierRows": tier_rows,
             "activeEndpoints": active_endpoints, "passed": True,
             "evidence": "Installed native console readout of every saved tier cost and endpoint; not purchase/activation simulation"})
        for folder in ["reports", "diagnostics"]:
            if (config / folder).exists():
                shutil.copytree(config / folder, destination / folder)
        before = hashes()
        save(destination / "saved-before.json", before)
    finally:
        stop(process, handle, log)
    if args.restart:
        process, handle, log = launch("-saved")
        try:
            after = hashes()
            assert before == after, "Saved load changed balance files"
            events = [json.loads(line.split("Balance performance ", 1)[1]) for line in log.read_text(encoding="utf-8", errors="replace").splitlines() if "Balance performance {" in line]
            loads = [event for event in events if event.get("event") == "end" and event.get("operation") == "saved_profile_load"]
            assert len(loads) == 1, loads
            counts = loads[0]["result"]["counts"]
            for name in ["full_generation_runs", "evidence_collection_runs", "conservation_solve_runs", "runtime_generation_runs", "report_export_runs"]:
                assert counts.get(name, 0) == 0, (name, counts)
            commands(["essence debug balance validate", "essence test skills effects", *args.command], "saved-validation.json")
            save(destination / "saved-restart.json", {"filesPreserved": len(before), "seconds": loads[0]["elapsedMillis"] / 1000, "counts": counts})
        finally:
            stop(process, handle, log)
    profiles = [destination / "initial.json.gz", *[destination / f"profile-{i}.json.gz" for i in range(args.repeat)]]
    documents = [json.loads(gzip.decompress(p.read_bytes())) for p in profiles]
    comparisons = []
    for left, right in zip(documents, documents[1:]):
        comparison = {name: left[name] == right[name] for name in ["evidence", "economy", "runtime", "skills", "validation"]}
        comparisons.append(comparison)
    save(destination / "semantic-comparison.json", comparisons)
    assert all(all(c.values()) for c in comparisons), comparisons
    assert digest(installed_jar) == installed_sha, "Installed fixture JAR changed during verification"
    save(destination / "accepted.json", {"fixture": args.fixture, "rebuilds": args.repeat, "restart": args.restart, "jarSha256": installed_sha})
    print(args.fixture + " " + args.label + " PASS", flush=True)


if __name__ == "__main__":
    main()
