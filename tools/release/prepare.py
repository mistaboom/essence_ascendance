"""Validate release inputs and stage every declared production artifact.

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
SUPPORTED_LOADERS = {"fabric", "neoforge"}

def release_targets():
    catalog = json.loads((ROOT / "gradle/targets.json").read_text(encoding="utf-8"))
    if catalog.get("schema") != 1:
        raise ValueError("Unsupported target catalog")
    targets = catalog["targets"]
    release = catalog["release"]
    required_ids = release["requiredTargetIds"]
    if len(required_ids) != len(set(required_ids)) or set(required_ids) != {t["id"] for t in targets}:
        raise ValueError("Declared targets differ from the required release acceptance set")
    if not targets or any(not t["implemented"] for t in targets):
        raise ValueError("Every declared release target must be implemented")
    identities = [(t["minecraft"], t["loader"]) for t in targets]
    if len(set(identities)) != len(targets) or len({t["id"] for t in targets}) != len(targets):
        raise ValueError("Duplicate release target identity")
    if any(t["minecraft"] not in catalog["versions"] for t in targets):
        raise ValueError("Release target has no version profile")
    for target in targets:
        loader = target["loader"]
        if loader not in SUPPORTED_LOADERS:
            raise ValueError(f"No native archive validator for loader {loader}")
        profile = catalog["versions"][target["minecraft"]]
        if type(profile["java"]) is not int or profile["java"] < 1:
            raise ValueError("Target Java version must be a positive integer")
        if type(profile.get("nativeAccess", {}).get(loader)) is not bool:
            raise ValueError(f"Native access policy is missing for {target['id']}")
    publication = release["publication"]
    for loader in {t["loader"] for t in targets}:
        if loader not in publication:
            raise ValueError(f"Publishing dependencies are missing for {loader}")
        for platform in ("modrinth", "curseforge"):
            dependencies = publication[loader][platform + "Dependencies"]
            expected = {"architectury-api(required)", "jei(optional)"}
            if loader == "fabric":
                expected.add("fabric-api(required)")
            if platform == "curseforge":
                expected.add("ftb-teams(optional)")
            pattern = r"([a-z0-9-]+\((?:required|optional)\))\{" + platform + r":([A-Za-z0-9]+)\}"
            parsed = [re.fullmatch(pattern, entry) for entry in dependencies]
            if any(entry is None for entry in parsed):
                raise ValueError(f"Invalid {platform} dependency identifier for {loader}")
            declared = {entry.group(1) for entry in parsed}
            if declared != expected or len(dependencies) != len(declared):
                raise ValueError(f"Incorrect {platform} dependencies for {loader}")
    return catalog["versions"], targets, publication


def java_versions():
    """Install each target JDK once; the newest remains the Gradle launcher."""
    versions, targets, _ = release_targets()
    return sorted({versions[target["minecraft"]]["java"] for target in targets})


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
    versions, targets, _ = release_targets()
    if {t["loader"] for t in targets} != set(properties["enabled_platforms"].split(",")):
        raise ValueError("enabled_platforms disagrees with declared release loaders")
    if properties["minecraft_version"] not in versions:
        raise ValueError("Default Minecraft version has no target profile")
    if any(not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", t["minecraft"]) for t in targets):
        raise ValueError("Release workflow supports Minecraft release versions only")
    return {"version": version, "release_type": match.group(1) or "release"}


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


def validate_jar(path, loader, version, minecraft_version, java, profile):
    if loader not in SUPPORTED_LOADERS:
        raise ValueError(f"No native archive validator for loader {loader}")
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError(f"{path.name}: duplicate ZIP entries")
        corrupt_entry = archive.testzip()
        if corrupt_entry:
            raise ValueError(f"{path.name}: corrupt ZIP entry {corrupt_entry}")
        if archive.read("LICENSE") != (ROOT / "LICENSE").read_bytes():
            raise ValueError(f"{path.name}: missing or stale repository license notice")
        if any("${" in archive.read(name).decode("utf-8") for name in names
               if name in ("fabric.mod.json", "META-INF/neoforge.mods.toml")):
            raise ValueError(f"{path.name}: unresolved metadata placeholder")
        if any(name.startswith(("art/", "tools/", "dev/essence/packtester/", "dev/ftb/mods/", "mezz/jei/"))
               or name.endswith((".java", ".bbmodel")) or "/fixture/" in name
               or "/membershipfixture/" in name for name in names):
            raise ValueError(f"{path.name}: authoring, fixture, tool, or optional API files packaged")
        if not any(name.startswith("com/mistaboom/essence_ascendance/")
                   and name.endswith(".class") for name in names):
            raise ValueError(f"{path.name}: missing compiled mod classes")
        own_classes = [name for name in names if name.startswith("com/mistaboom/essence_ascendance/") and name.endswith(".class")]
        for name in own_classes:
            with archive.open(name) as stream:
                header = stream.read(8)
            if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(header[6:8]) != java + 44:
                raise ValueError(f"{path.name}: wrong Java class level in {name}")
        if loader == "fabric":
            if "META-INF/neoforge.mods.toml" in names:
                raise ValueError(f"{path.name}: wrong loader metadata")
            mod = json.loads(archive.read("fabric.mod.json"))
            mod_id, mod_version = mod["id"], mod["version"]
            license_id = mod["license"]
            required = mod.get("depends", {})
            if not {"architectury", "fabric-api", "fabricloader", "minecraft", "java"}.issubset(required):
                raise ValueError("Fabric publishing dependencies no longer match the mod metadata")
            if (required.get("minecraft") != minecraft_version or required.get("java") != f">={java}"
                    or required.get("fabricloader") != f">={profile['fabricLoader']}"
                    or required.get("architectury") != f">={profile['architecturyApi']}"
                    or required.get("fabric-api") != "*"):
                raise ValueError("Fabric Minecraft/Java target metadata is incorrect")
        else:
            if "fabric.mod.json" in names:
                raise ValueError(f"{path.name}: wrong loader metadata")
            data = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode("utf-8"))
            mod = next(mod for mod in data["mods"] if mod["modId"] == "essence_ascendance")
            mod_id, mod_version = mod["modId"], mod["version"]
            license_id = data["license"]
            required = {dep["modId"] for dep in data["dependencies"][mod_id]
                        if dep.get("type") == "required"}
            if required != {"architectury", "neoforge", "minecraft"}:
                raise ValueError("NeoForge publishing dependencies no longer match the mod metadata")
            minecraft = next(dep for dep in data["dependencies"][mod_id]
                             if dep["modId"] == "minecraft")
            if minecraft["versionRange"] != f"[{minecraft_version}]":
                raise ValueError("NeoForge Minecraft target metadata is incorrect")
            minimum = next(dep for dep in data["dependencies"][mod_id] if dep["modId"] == "neoforge")
            if minimum["versionRange"] != f"[{profile['neoforgeMinimum']},)":
                raise ValueError("NeoForge loader requirement is incorrect")
            architectury = next(dep for dep in data["dependencies"][mod_id] if dep["modId"] == "architectury")
            if architectury["versionRange"] != f"[{profile['architecturyApi']},)":
                raise ValueError("NeoForge Architectury requirement is incorrect")
        if mod_id != "essence_ascendance" or mod_version != version:
            raise ValueError(f"{path.name}: mod ID/version mismatch (possibly a stale jar)")
        if license_id != "MIT":
            raise ValueError(f"{path.name}: mod metadata must match the repository's MIT license")
        mixins = ["essence_ascendance.mixins.json", "essence_ascendance.damage.mixins.json"
                  if loader == "fabric" else "essence_ascendance.vitality.mixins.json"]
        registered_mixins = (mod.get("mixins", []) if loader == "fabric"
                             else [entry["config"] for entry in data.get("mixins", [])])
        if len(registered_mixins) != len(mixins) or set(registered_mixins) != set(mixins):
            raise ValueError(f"{path.name}: incorrect native mixin registrations")
        configured_classes = set()
        for name in mixins:
            config = json.loads(archive.read(name))
            if config.get("required") is not True or config.get("injectors", {}).get("defaultRequire") != 1 \
                    or config.get("compatibilityLevel") != f"JAVA_{java}":
                raise ValueError(f"{path.name}: incorrect mixin configuration {name}")
            for mixin in config.get("mixins", []) + config.get("client", []):
                class_name = config["package"].replace(".", "/") + "/" + mixin.replace(".", "/") + ".class"
                if class_name in configured_classes:
                    raise ValueError(f"{path.name}: duplicate mixin registration {class_name}")
                configured_classes.add(class_name)
                if class_name not in names:
                    raise ValueError(f"{path.name}: missing mixin class {class_name}")
        requires_access = profile["nativeAccess"][loader]
        if loader == "fabric":
            widener = "essence_ascendance.accesswidener"
            if ((requires_access and mod.get("accessWidener") != widener)
                    or (not requires_access and "accessWidener" in mod)
                    or (widener in names) != requires_access):
                raise ValueError(f"{path.name}: incorrect access widener")
        else:
            transformer = "META-INF/accesstransformer.cfg"
            declarations = data.get("accessTransformers", [])
            expected = [{"file": transformer}] if requires_access else []
            if declarations != expected or (transformer in names) != requires_access:
                raise ValueError(f"{path.name}: incorrect access transformer")


def stage(destination, info, notes):
    versions, targets, publication = release_targets()
    manifest = json.loads((ROOT / "build/production-artifacts-all.json").read_text(encoding="utf-8"))
    if manifest.get("schema") != 1:
        raise ValueError("Unsupported Gradle production artifact manifest")
    artifacts = {entry["target"]: entry for entry in manifest["targets"]}
    if len(artifacts) != len(manifest["targets"]):
        raise ValueError("Duplicate production target in Gradle manifest")
    if set(artifacts) != {target["id"] for target in targets}:
        raise ValueError("Missing or unexpected production target in Gradle manifest")
    checksums, sources = [], []
    for target in targets:
        artifact = artifacts[target["id"]]
        loader, minecraft = target["loader"], target["minecraft"]
        profile = versions[minecraft]
        if artifact["minecraft"] != minecraft or artifact["loader"] != loader or artifact["java"] != profile["java"]:
            raise ValueError(f"Gradle manifest target mismatch for {target['id']}")
        if artifact["task"] != f":{loader}:{minecraft}:{target['archiveTask']}":
            raise ValueError(f"Wrong production provider for {target['id']}")
        source = Path(artifact["archive"]).resolve(strict=True)
        expected_root = (ROOT / loader / "versions" / minecraft / "build/libs").resolve(strict=True)
        if not expected_root.is_relative_to(ROOT.resolve()) or source.parent != expected_root or source.suffix != ".jar":
            raise ValueError(f"Gradle manifest archive escaped the {loader} production output")
        expected_name = f"essence_ascendance-{info['version']}+mc{minecraft}-{loader}.jar"
        if source.name != expected_name:
            raise ValueError(f"{target['id']}: mislabelled production filename {source.name}; expected {expected_name}")
        with source.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != artifact["sha256"]:
                raise ValueError(f"Gradle manifest checksum mismatch for {loader}")
        validate_jar(source, loader, info["version"], minecraft, profile["java"], profile)
        sources.append((target, source, artifact["sha256"]))
    if len({source.name for _, source, _ in sources}) != len(sources):
        raise ValueError("Duplicate production filenames")
    destination.mkdir(parents=True, exist_ok=False)
    variants = []
    for target, source, digest in sources:
        staged = destination / source.name
        shutil.copy2(source, staged)
        with staged.open("rb") as stream:
            checksum = hashlib.file_digest(stream, "sha256").hexdigest()
        if checksum != digest:
            raise ValueError(f"Copy changed {target['id']} artifact")
        checksums.append(f"{checksum}  {source.name}\n")
        variants.append({"target": target["id"], "minecraft": target["minecraft"],
                         "loader": target["loader"], "java": versions[target["minecraft"]]["java"],
                         "file": source.name, "sha256": checksum,
                         "modrinthDependencies": "\n".join(publication[target["loader"]]["modrinthDependencies"]),
                         "curseforgeDependencies": "\n".join(publication[target["loader"]]["curseforgeDependencies"])})
    (destination / "SHA256SUMS").write_text("".join(checksums), encoding="utf-8")
    (destination / "CHANGELOG.md").write_text(notes, encoding="utf-8")
    (destination / "metadata.json").write_text(json.dumps(info | {"variants": variants}, indent=2) + "\n", encoding="utf-8")
    return variants


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", type=Path)
    args = parser.parse_args()
    info = metadata()
    notes = release_notes(info)
    if args.stage:
        variants = stage(args.stage, info, notes)
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with Path(output).open("a", encoding="utf-8") as stream:
            for key, value in info.items():
                stream.write(f"{key}={value}\n")
            stream.write("java_versions<<TARGET_JAVA_VERSIONS\n")
            stream.write("\n".join(map(str, java_versions())) + "\nTARGET_JAVA_VERSIONS\n")
            if args.stage:
                stream.write(f"variants={json.dumps(variants, separators=(',', ':'))}\n")
    print(json.dumps(info, indent=2))
    if args.stage:
        print(f"Validated production jars and staged artifacts in {args.stage}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, StopIteration, zipfile.BadZipFile) as error:
        print(f"Release validation failed: {error}", file=sys.stderr)
        sys.exit(1)
