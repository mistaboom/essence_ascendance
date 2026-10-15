"""Copy inspected isolated server inputs into this worktree; never copy/open worlds."""
from pathlib import Path
import argparse
import hashlib
import json
import secrets
import shutil
import socket
import subprocess

ROOT = Path(__file__).resolve().parents[2]
GROUPS = ["mods", "libraries", "config", "defaultconfigs", "configureddefaults", "kubejs",
          "coremods", "datapacks", "moonlight-global-datapacks", "local",
          "libraries-integratedscripting", "ftbteambases/pregen", "ftbteambases/pregen_initial",
          "skyblockbuilder"]


def digest(path):
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_checkout", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source_root = args.source_checkout.resolve()
    target_root = (ROOT / "build/pack-matrix").resolve()
    assert ROOT != source_root and target_root.is_relative_to(ROOT)
    args.output.mkdir(parents=True, exist_ok=True)
    source_manifest = source_root / "build/pack-matrix/fixtures.json"
    original = json.loads(source_manifest.read_text())
    fixtures, receipts = {}, {}
    for index, label in enumerate(["vanilla", "atm10", "atm10-sky", "stoneblock", "oceanblock"]):
        item = original[label]
        source = Path(item["server"]).resolve()
        assert source.is_relative_to(source_root / "build"), source
        target = target_root / label / "server"
        assert not target.exists(), target
        ports = (25700 + index, 25710 + index)
        for port in ports:
            with socket.socket() as probe:
                assert probe.connect_ex(("127.0.0.1", port)) != 0, port
        target.mkdir(parents=True)
        files = []
        for group in GROUPS:
            for path in sorted((source / group).rglob("*")):
                if not path.is_file():
                    continue
                assert not path.is_symlink() and path.resolve().is_relative_to(source), path
                relative = path.relative_to(source)
                # Saved balance/report authority belongs to the earlier run. Fresh generation
                # uses its explicit human inputs only, never an inherited generated profile.
                if relative.parts[:2] == ("config", "essence_ascendance") and path.suffix != ".toml":
                    continue
                if group == "mods" and path.name.startswith("essence_ascendance"):
                    continue
                copied = target / relative
                copied.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(path, copied)
                files.append({"path": str(relative), "bytes": path.stat().st_size,
                              "sha256": digest(path)})
        # Reuse the already accepted disposable fixture's EULA; do not change consent.
        assert "eula=true" in (source / "eula.txt").read_text()
        shutil.copy2(source / "eula.txt", target / "eula.txt")
        props = dict(line.split("=", 1) for line in (source / "server.properties").read_text().splitlines()
                     if "=" in line and not line.startswith("#"))
        props.update({"server-ip": "127.0.0.1", "server-port": str(ports[0]),
                      "enable-rcon": "true", "rcon.port": str(ports[1]),
                      "rcon.password": secrets.token_hex(24), "level-name": "grooming-isolated-world",
                      "max-tick-time": "600000", "view-distance": "2", "simulation-distance": "2"})
        (target / "server.properties").write_text("\n".join(f"{k}={v}" for k, v in props.items()) + "\n")
        shutil.copy2(source / "user_jvm_args.txt", target / "user_jvm_args.txt")
        arguments = target / f"libraries/net/neoforged/neoforge/{item['version']}/win_args.txt"
        assert arguments.is_file(), arguments
        # Absolute launcher paths would defeat isolation; inspected fixture args are relative.
        assert str(source_root).lower() not in arguments.read_text().lower()
        fixtures[label] = {**item, "server": str(target), "gamePort": ports[0], "consolePort": ports[1],
                           "copiedIsolatedFixture": str(source), "freshWorld": True}
        receipts[label] = {"source": str(source), "destination": str(target), "files": files,
                           "serverPropertiesSha256": digest(target / "server.properties"),
                           "jvmArguments": (target / "user_jvm_args.txt").read_text(),
                           "loader": item["version"]}
        print(json.dumps({"fixture": label, "copiedFiles": len(files), "bytes": sum(f["bytes"] for f in files)}), flush=True)
    (target_root / "fixtures.json").write_text(json.dumps(fixtures, indent=2))
    (args.output / "fixtures.json").write_text(json.dumps(fixtures, indent=2))
    (args.output / "fixture-inputs.json").write_text(json.dumps(receipts, indent=2))
    (args.output / "starting-commit.txt").write_text(subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source_root, text=True))


if __name__ == "__main__":
    main()
