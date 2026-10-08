"""Validate release inputs and stage only the two distributable mod jars.

Uses Python's standard library. Never reads publishing tokens or uploads files.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SEMVER = re.compile(r"\d+\.\d+\.\d+(?:-(alpha|beta)\.\d+)?")


def metadata():
    properties = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    version = properties["mod_version"]
    match = SEMVER.fullmatch(version)
    if not match:
        raise ValueError("mod_version must be X.Y.Z, X.Y.Z-alpha.N, or X.Y.Z-beta.N")
    if properties["archives_name"] != "essence_ascendance":
        raise ValueError("Update the release workflow before changing archives_name")
    if set(properties["enabled_platforms"].split(",")) != {"fabric", "neoforge"}:
        raise ValueError("Update the release workflow before changing enabled_platforms")
    minecraft = properties["minecraft_version"]
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", minecraft):
        raise ValueError("Release workflow currently supports Minecraft release versions only")
    return {"version": version, "minecraft": minecraft,
            "release_type": match.group(1) or "release"}


def release_notes(info):
    if os.environ.get("GITHUB_EVENT_NAME") != "release":
        return "CI build for validation. This is not a published release.\n"
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    release = event["release"]
    expected = "v" + info["version"]
    if release["tag_name"] != expected:
        raise ValueError(f"Release tag must be {expected}, matching mod_version at the tagged commit")
    if release.get("draft"):
        raise ValueError("Draft releases cannot be published by this workflow")
    if bool(release.get("prerelease")) != (info["release_type"] != "release"):
        raise ValueError("GitHub pre-release checkbox must match an alpha/beta mod_version")
    notes = (release.get("body") or "").strip()
    if not notes:
        raise ValueError("Add player-facing release notes before publishing the GitHub Release")
    return notes + "\n"


def validate_jar(path, loader, version):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError(f"{path.name}: duplicate ZIP entries")
        corrupt_entry = archive.testzip()
        if corrupt_entry:
            raise ValueError(f"{path.name}: corrupt ZIP entry {corrupt_entry}")
        if archive.read("LICENSE") != (ROOT / "LICENSE").read_bytes():
            raise ValueError(f"{path.name}: missing or stale repository license notice")
        if not any(name.startswith("com/mistaboom/essence_ascendance/")
                   and name.endswith(".class") for name in names):
            raise ValueError(f"{path.name}: missing compiled mod classes")
        if loader == "fabric":
            mod = json.loads(archive.read("fabric.mod.json"))
            mod_id, mod_version = mod["id"], mod["version"]
            license_id = mod["license"]
            if not {"architectury", "fabric-api"}.issubset(mod.get("depends", {})):
                raise ValueError("Fabric publishing dependencies no longer match the mod metadata")
        else:
            data = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode("utf-8"))
            mod = next(mod for mod in data["mods"] if mod["modId"] == "essence_ascendance")
            mod_id, mod_version = mod["modId"], mod["version"]
            license_id = data["license"]
            required = {dep["modId"] for dep in data["dependencies"][mod_id]
                        if dep.get("type") == "required"}
            if "architectury" not in required:
                raise ValueError("NeoForge publishing dependencies no longer match the mod metadata")
        if mod_id != "essence_ascendance" or mod_version != version:
            raise ValueError(f"{path.name}: mod ID/version mismatch (possibly a stale jar)")
        if license_id != "MIT":
            raise ValueError(f"{path.name}: mod metadata must match the repository's MIT license")


def stage(destination, info, notes):
    # A fresh directory prevents stale jars from becoming release artifacts.
    destination.mkdir(parents=True, exist_ok=False)
    checksums = []
    for loader in ("fabric", "neoforge"):
        filename = f"essence_ascendance-{loader}-{info['version']}.jar"
        source = ROOT / loader / "build/libs" / filename
        validate_jar(source, loader, info["version"])
        target = destination / filename
        shutil.copy2(source, target)
        with target.open("rb") as stream:
            checksum = hashlib.file_digest(stream, "sha256").hexdigest()
        checksums.append(f"{checksum}  {filename}\n")
    (destination / "SHA256SUMS").write_text("".join(checksums), encoding="utf-8")
    (destination / "CHANGELOG.md").write_text(notes, encoding="utf-8")
    (destination / "metadata.json").write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", type=Path)
    args = parser.parse_args()
    info = metadata()
    notes = release_notes(info)
    if args.stage:
        stage(args.stage, info, notes)
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with Path(output).open("a", encoding="utf-8") as stream:
            for key, value in info.items():
                stream.write(f"{key}={value}\n")
    print(json.dumps(info, indent=2))
    if args.stage:
        print(f"Validated production jars and staged artifacts in {args.stage}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, StopIteration, zipfile.BadZipFile) as error:
        print(f"Release validation failed: {error}", file=sys.stderr)
        sys.exit(1)
