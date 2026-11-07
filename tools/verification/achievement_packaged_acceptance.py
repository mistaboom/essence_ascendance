"""Exact four-target packaged dedicated-server smoke and cold persistence acceptance.

Uses only catalog/manifest-selected production jars and established local runtime
installations. All writes stay in a new build/achievement-packaged-acceptance run.
This is startup, resource reload and native world persistence evidence, not a
connected-client achievement playthrough. Gameplay assertions use the separate
achievement-native-hooks fixture.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import socket
import subprocess
import time
import traceback
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("ea_packaged_runtime", ROOT / "tools/audio/packaged_acceptance.py")
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


def isolated(path):
    path = Path(path).absolute()
    boundary = ROOT.resolve() / "build/achievement-packaged-acceptance"
    if ".." in path.parts or not path.is_relative_to(boundary) or not path.resolve().is_relative_to(boundary):
        raise ValueError("Expected a disposable achievement acceptance path")
    for parent in (path, *path.parents):
        if parent == ROOT.resolve():
            break
        if parent.is_symlink() or getattr(parent, "is_junction", lambda: False)():
            raise ValueError("Redirected acceptance directories are not permitted")
    return path


def prepare(target, directory):
    directory = isolated(directory)
    archive = runtime.production_archive(target)
    suffix = "1211" if target["minecraft"] == "1.21.1" else "2612"
    loader = "fabric" if target["loader"] == "fabric" else "neo"
    source = ROOT / f"build/packaged-{loader}{suffix}-server"
    directory.mkdir(parents=True, exist_ok=False)
    for name in ("libraries", "fabric-server-launch.jar", "fabric-server-launcher.properties", "server.jar", "versions", ".fabric"):
        path = source / name
        if path.is_dir():
            shutil.copytree(path, directory / name)
        elif path.is_file():
            runtime.copy_verified(path, directory / name)
    (directory / "mods").mkdir()
    runtime.copy_dependencies(target, source / "mods", directory / "mods")
    runtime.copy_verified(archive, directory / "mods" / archive.name)
    (directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    flat = {"biome": "minecraft:plains", "layers": [{"block": "minecraft:bedrock", "height": 1}], "lakes": False, "features": False}
    (directory / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n"
        "view-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-tick-time=120000\n"
        "level-type=minecraft:flat\nlevel-seed=0\ngenerate-structures=false\ngenerator-settings="
        + json.dumps(flat, separators=(",", ":")) + "\n", encoding="utf-8")
    profile = json.loads((ROOT / "gradle/targets.json").read_text())["versions"][target["minecraft"]]
    launch = ["-jar", "fabric-server-launch.jar", "nogui"] if loader == "fabric" else [f"@libraries/net/neoforged/neoforge/{profile['neoforge']}/win_args.txt", "nogui"]
    command = [str(runtime.java(target["java"])), "-Xmx2G", "-XX:ActiveProcessorCount=4", *launch]
    (directory / "launch-recipe.json").write_text(json.dumps(command, indent=2) + "\n", encoding="utf-8")
    return command


def run_stage(directory, command, stage, commands, expected):
    log_path = directory.parent / f"{stage}.log"
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=directory, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + 240
            while "Done (" not in log_path.read_text(encoding="utf-8", errors="replace"):
                if process.poll() is not None:
                    raise RuntimeError(f"{stage}: server exited before readiness ({process.returncode})")
                if time.monotonic() >= deadline:
                    raise TimeoutError(f"{stage}: server readiness")
                time.sleep(0.25)
            for entry in commands:
                command_text, marker = entry[:2]
                retry = len(entry) == 3 and entry[2] == "until-loaded"
                offset = len(log_path.read_text(encoding="utf-8", errors="replace"))
                process.stdin.write(command_text + "\n")
                process.stdin.flush()
                deadline = time.monotonic() + 120
                retry_at = time.monotonic() + 1
                while marker not in log_path.read_text(encoding="utf-8", errors="replace")[offset:]:
                    if process.poll() is not None:
                        raise RuntimeError(f"{stage}: server exited waiting for {marker}")
                    if time.monotonic() >= deadline:
                        raise TimeoutError(f"{stage}: missing console result {marker}")
                    if retry and time.monotonic() >= retry_at:
                        process.stdin.write(command_text + "\n")
                        process.stdin.flush()
                        retry_at = time.monotonic() + 1
                    time.sleep(0.25)
            process.stdin.write("stop\n")
            process.stdin.flush()
            code = process.wait(timeout=120)
            if code != 0:
                raise RuntimeError(f"{stage}: process exit {code}")
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=15)
    contents = log_path.read_text(encoding="utf-8", errors="replace")
    for marker in (*expected, "Saving chunks", "All dimensions are saved"):
        if marker not in contents:
            raise AssertionError(f"{stage}: missing {marker}")
    return {"log": str(log_path), "sha256": runtime.digest(log_path), "exit": code, "pid": process.pid}


def accept(target, base):
    folder = isolated(base / target["target"])
    command = prepare(target, folder / "server")
    before = {str(path): runtime.digest(path) for path in (folder / "server/mods").iterdir() if path.is_file()}
    receipt = {"target": target, "mode": "exact packaged dedicated server; cold world persistence and datapack reload", "inputs": before}
    try:
        receipt["prepare"] = run_stage(folder / "server", command, "prepare", [
            ("scoreboard objectives add ea_phase9 dummy", "Created new objective"),
            ("scoreboard players set ea_persist ea_phase9 69", "to 69"),
            ('data modify storage essence_ascendance:phase9 marker set value "achievement-persistence"', "Modified storage"),
            ("execute in minecraft:the_nether run forceload add 0 0", "force loaded"),
            ("execute in minecraft:the_nether if loaded 0 90 0 run say EA_NETHER_READY", "EA_NETHER_READY", "until-loaded"),
            ("execute in minecraft:the_nether run setblock 0 90 0 minecraft:gold_block", "Changed the block"),
            ("execute in minecraft:the_end run forceload add 0 0", "force loaded"),
            ("execute in minecraft:the_end if loaded 0 90 0 run say EA_END_READY", "EA_END_READY", "until-loaded"),
            ("execute in minecraft:the_end run setblock 0 90 0 minecraft:diamond_block", "Changed the block"),
            ("save-all flush", "Saved the game")], ["Saved the game"])
        receipt["resume"] = run_stage(folder / "server", command, "resume", [
            ("scoreboard players get ea_persist ea_phase9", "ea_persist has 69"),
            ("data get storage essence_ascendance:phase9 marker", "achievement-persistence"),
            ("execute in minecraft:the_nether if loaded 0 90 0 run say EA_NETHER_READY", "EA_NETHER_READY", "until-loaded"),
            ("execute in minecraft:the_nether if block 0 90 0 minecraft:gold_block run say EA_PHASE9_NETHER_PERSISTED", "EA_PHASE9_NETHER_PERSISTED"),
            ("execute in minecraft:the_end if loaded 0 90 0 run say EA_END_READY", "EA_END_READY", "until-loaded"),
            ("execute in minecraft:the_end if block 0 90 0 minecraft:diamond_block run say EA_PHASE9_END_PERSISTED", "EA_PHASE9_END_PERSISTED"),
            ("reload", "advancements"),
            ("save-all flush", "Saved the game")], ["ea_persist has 69", "achievement-persistence", "EA_PHASE9_NETHER_PERSISTED", "EA_PHASE9_END_PERSISTED"])
        after = {path: runtime.digest(path) for path in before}
        runtime.verify_inputs(before, after)
        receipt["inputs_after"] = after
        receipt["result"] = "PASS"
    except Exception as failure:
        receipt["result"] = "FAIL"
        receipt["failure"] = str(failure)
    receipt["limitations"] = ["No connected clients or client toasts", "No gameplay achievement asserted in this startup harness", "Cold achievement native progress is tested separately by MachineRestartNativeFixture"]
    (folder / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"target": target["target"], "result": receipt["result"], "failure": receipt.get("failure"), "receipt": str(folder / "receipt.json")}), flush=True)
    return receipt["result"] == "PASS"


def socket_fixture(path):
    path = Path(path).resolve()
    with zipfile.ZipFile(path) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError("Duplicate external fixture classes")
        if "com/mistaboom/essence_ascendance/achievementfixture/AchievementSocketFixture.class" not in names:
            raise ValueError("Missing external achievement socket entrypoint")
        metadata = {"META-INF/MANIFEST.MF", "META-INF/neoforge.mods.toml", "fabric.mod.json", "achievement-socket.mixins.json", "achievement-hidden.mixins.json"}
        for name in names:
            if name not in metadata and not (name.startswith("com/mistaboom/essence_ascendance/achievementfixture/") and name.endswith(".class")) and name != "com/mistaboom/essence_ascendance/audiofixture/mixin/AudioHiddenWindowMixin.class":
                raise ValueError("Unexpected production override in external fixture: " + name)
    return path


def advancement_path(target, server, player_name):
    # 26.1 moved player-owned files into the native world/players tree.
    folder = "world/advancements" if target["minecraft"] == "1.21.1" else "world/players/advancements"
    return server / folder / (str(runtime.offline_player_uuid(player_name)) + ".json")


def accept_sockets(target, base, fixture):
    fixture = socket_fixture(fixture)
    folder = isolated(base / target["target"])
    server = folder / "server"
    command = prepare(target, server)
    runtime.copy_verified(fixture, server / "mods" / fixture.name)
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    settings = (server / "server.properties").read_text()
    (server / "server.properties").write_text(settings.replace("server-port=0\n", f"server-port={port}\n"))
    command.insert(1, "-Dessence.achievement.socketDirectory=" + str(folder))
    processes = []
    logs = []
    receipt = {"target": target, "fixture": str(fixture), "fixture_sha256": runtime.digest(fixture), "mode": "exact packaged server and two rendered socket clients; real survival menu transaction and native toast"}
    try:
        clients = {}
        for name in ("EA_A", "EA_B"):
            directory = isolated(folder / name)
            directory.mkdir()
            (directory / "mods").mkdir()
            (directory / "natives").mkdir()
            for path in (server / "mods").iterdir():
                runtime.copy_verified(path, directory / "mods" / path.name)
            (directory / "options.txt").write_text("onboardAccessibility:false\ntutorialStep:none\npauseOnLostFocus:false\nmaxFps:30\nrenderDistance:3\nsimulationDistance:3\nsoundCategory_master:0.0\n")
            if target["loader"] == "neoforge":
                (directory / "config").mkdir()
                (directory / "config/fml.toml").write_text("earlyWindowControl = false\n")
            clients[name] = runtime.client_command(target, directory, multiplayer=f"127.0.0.1:{port}", hidden=True)
            clients[name][1:1] = ["-Dessence.achievement.socketClient=true", "-Dessence.achievement.socketDirectory=" + str(folder)]
        before = {str(path): runtime.digest(path) for directory in (server, folder / "EA_A", folder / "EA_B") for path in (directory / "mods").iterdir() if path.is_file()}
        receipt["inputs"] = before
        def spawn(name, args, directory):
            log = (folder / (name + ".log")).open("w", encoding="utf-8")
            logs.append(log)
            process = subprocess.Popen(args, cwd=directory, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
            processes.append(process)
            return process
        host = spawn("server", command, server)
        def wait_for(predicate, timeout=600):
            deadline = time.monotonic() + timeout
            while not predicate():
                if list(folder.glob("failed-*")):
                    raise RuntimeError("Native socket fixture reported assertion failure")
                if host.poll() is not None:
                    raise RuntimeError("Dedicated server exited before completion")
                if any(p.poll() not in (None, 0) for p in processes):
                    raise RuntimeError("Native client process failed before acceptance barrier")
                if time.monotonic() > deadline:
                    raise TimeoutError("Native socket acceptance barrier")
                time.sleep(0.25)
        wait_for(lambda: "Done (" in (folder / "server.log").read_text(errors="replace"), 240)
        a = spawn("EA_A", clients["EA_A"], folder / "EA_A")
        first_b = spawn("EA_B-first", clients["EA_B"], folder / "EA_B")
        wait_for(lambda: (folder / "reconnect").is_file())
        if first_b.wait(timeout=45) != 0:
            raise RuntimeError("First operator client failed during disconnect")
        reconnect = clients["EA_B"].copy()
        reconnect.insert(1, "-Dessence.achievement.socketGeneration=1")
        b = spawn("EA_B-reconnected", reconnect, folder / "EA_B")
        wait_for(lambda: (folder / "complete").is_file())
        if a.wait(timeout=60) != 0 or b.wait(timeout=60) != 0:
            raise RuntimeError("Joined native client failed")
        host.stdin.write("save-all flush\nstop\n")
        host.stdin.flush()
        if host.wait(timeout=120) != 0:
            raise RuntimeError("Server failed normal save/stop")
        saved_advancements = {}
        for name in ("EA_A", "EA_B"):
            path = advancement_path(target, server, name)
            progress = json.loads(path.read_text(encoding="utf-8"))
            selected = progress.get("essence_ascendance:achievements/a022", {}).get("done", False)
            if selected != (name == "EA_B"):
                raise AssertionError("Saved native advancement JSON violates actor isolation: " + name)
            if name == "EA_A" and not progress.get("essence_ascendance:water_skiing", {}).get("done", False):
                raise AssertionError("Native legacy progress did not survive selected subtree revoke")
            saved_advancements[name] = {"path": str(path), "sha256": runtime.digest(path), "selected_a022_done": selected}
        receipt["saved_advancements"] = saved_advancements
        for log_name, expected in (("server", "AchievementSocketFixture: SERVER PASS"), ("EA_A", "AchievementSocketFixture: CLIENT PASS"), ("EA_B-first", "AchievementSocketFixture: TOAST RENDER PASS"), ("EA_B-reconnected", "AchievementSocketFixture: CLIENT PASS")):
            contents = (folder / (log_name + ".log")).read_text(errors="replace")
            if expected not in contents:
                raise AssertionError("Missing native acceptance evidence: " + expected)
            if log_name != "server" and "AudioHiddenWindow: PASS" not in contents:
                raise AssertionError("Native rendering window was not verified hidden")
        if (folder / "EA_A.toast").exists():
            raise AssertionError("Observer received operator-only achievement toast")
        after = {path: runtime.digest(path) for path in before}
        runtime.verify_inputs(before, after)
        receipt["inputs_after"] = after
        receipt["processes"] = [{"pid": p.pid, "exit": p.returncode} for p in processes]
        receipt["result"] = "PASS"
    except Exception as failure:
        receipt["result"] = "FAIL"
        receipt["failure"] = str(failure)
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=15)
        for log in logs:
            log.close()
    receipt["logs"] = {path.name: runtime.digest(path) for path in folder.glob("*.log")}
    receipt["limitations"] = ["One representative earned achievement; other rule families retain separate native fixture evidence", "Controlled setup supplies team, tier, supported input and platform", "No human navigation, audio listening or all-69 gameplay playthrough"]
    (folder / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"target": target["target"], "result": receipt["result"], "failure": receipt.get("failure"), "receipt": str(folder / "receipt.json")}), flush=True)
    return receipt["result"] == "PASS"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", required=True)
    parser.add_argument("--target", choices=("1211Fabric", "1211NeoForge", "2612Fabric", "2612NeoForge"))
    parser.add_argument("--socket-fixture", type=Path, help="Exact external achievementSocketFixtureJar result; requires one --target")
    args = parser.parse_args()
    if not args.label.replace("-", "").isalnum():
        parser.error("Use a new alphanumeric/hyphen label")
    if args.socket_fixture and not args.target:
        parser.error("--socket-fixture requires --target")
    manifest = json.loads((ROOT / "build/production-artifacts-all.json").read_text())
    catalog = json.loads((ROOT / "gradle/targets.json").read_text())
    if len(manifest["targets"]) != len(catalog["release"]["requiredTargetIds"]) or {entry["target"] for entry in manifest["targets"]} != set(catalog["release"]["requiredTargetIds"]):
        raise ValueError("Production manifest does not contain the exact required catalog targets")
    targets = [entry for entry in manifest["targets"] if args.target is None or entry["target"] == args.target]
    # Validate every selected input before creating the run, never choose a jar by glob.
    for target in targets:
        runtime.production_archive(target)
    base = isolated(ROOT / "build/achievement-packaged-acceptance" / args.label)
    base.mkdir(parents=True, exist_ok=False)
    results = []
    for target in targets:
        try:
            results.append(accept_sockets(target, base, args.socket_fixture) if args.socket_fixture else accept(target, base))
        except Exception as failure:
            # Setup failures are evidence too. Preserve a receipt even if a missing
            # runtime dependency or invalid fixture prevented process creation.
            directory = isolated(base / target["target"])
            directory.mkdir(parents=True, exist_ok=True)
            log = directory / "setup-failure.log"
            log.write_text(traceback.format_exc(), encoding="utf-8")
            receipt = {"target": target, "result": "FAIL", "stage": "setup", "failure": str(failure), "log": str(log), "log_sha256": runtime.digest(log)}
            (directory / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
            print(json.dumps({"target": target["target"], "result": "FAIL", "failure": str(failure), "receipt": str(directory / "receipt.json")}), flush=True)
            results.append(False)
    return 0 if all(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
