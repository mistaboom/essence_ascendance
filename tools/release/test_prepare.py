"""Release selection against copies of the real target JARs; no uploads or user installs."""

import copy
from contextlib import redirect_stdout
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import shutil
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("release_prepare", Path(__file__).with_name("prepare.py"))
prepare = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(prepare)


class ReleaseTargetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repository = prepare.ROOT
        cls.root = Path(tempfile.mkdtemp(prefix="release-targets-", dir=cls.repository / "build"))
        assert cls.root.resolve().is_relative_to((cls.repository / "build").resolve())
        (cls.root / "gradle").mkdir()
        (cls.root / "build").mkdir()
        shutil.copy2(cls.repository / "LICENSE", cls.root / "LICENSE")
        cls.catalog = json.loads((cls.repository / "gradle/targets.json").read_text())
        cls.manifest = json.loads((cls.repository / "build/production-artifacts-all.json").read_text())
        for entry in cls.manifest["targets"]:
            source = Path(entry["archive"])
            destination = cls.root / source.relative_to(cls.repository)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
            entry["archive"] = str(destination)
        cls.version = next(line.split("=", 1)[1].strip() for line in
                           (cls.repository / "gradle.properties").read_text().splitlines()
                           if line.split("=", 1)[0].strip() == "mod_version")
        prepare.ROOT = cls.root

    @classmethod
    def tearDownClass(cls):
        prepare.ROOT = cls.repository
        print(f"Release fixtures retained under {cls.root}")

    def setUp(self):
        for entry in self.manifest["targets"]:
            fixture = Path(entry["archive"])
            shutil.copy2(self.repository / fixture.relative_to(self.root), fixture)
        self.current = copy.deepcopy(self.manifest)
        self.write_manifest()
        (self.root / "gradle/targets.json").write_text(json.dumps(self.catalog))
        self.select("1.21.1")

    def select(self, minecraft):
        (self.root / "gradle.properties").write_text(
            f"mod_version={self.version}\narchives_name=essence_ascendance\n"
            f"enabled_platforms=fabric,neoforge\nminecraft_version={minecraft}\n")
        return prepare.metadata()

    def write_manifest(self):
        (self.root / "build/production-artifacts-all.json").write_text(json.dumps(self.current))

    def rewrite_jar(self, target, replacements):
        entry = self.entry(target)
        fixture = Path(entry["archive"])
        original = self.repository / fixture.relative_to(self.root)
        with zipfile.ZipFile(original) as source, zipfile.ZipFile(fixture, "w") as output:
            for member in source.infolist():
                content = source.read(member.filename)
                if member.filename in replacements:
                    content = replacements[member.filename](content)
                output.writestr(member, content)
        entry["sha256"] = hashlib.sha256(fixture.read_bytes()).hexdigest()
        self.write_manifest()

    def entry(self, target="1211Fabric"):
        return next(row for row in self.current["targets"] if row["target"] == target)

    def stage(self, info=None, suffix=""):
        return prepare.stage(self.root / "build" / (self._testMethodName + suffix),
                             info or prepare.metadata(), "Fixture notes\n")

    def test_all_targets_keep_exact_production_names_and_bytes(self):
        files = self.stage()
        staged = self.root / "build" / self._testMethodName
        self.assertEqual(len(files), len(self.catalog["targets"]))
        for row in self.current["targets"]:
            filename = Path(row["archive"]).name
            self.assertIn(filename, [item["file"] for item in files])
            self.assertEqual(hashlib.sha256((staged / filename).read_bytes()).hexdigest(), row["sha256"])
        self.assertEqual({path.name for path in staged.iterdir()},
                         {item["file"] for item in files} | {"SHA256SUMS", "metadata.json", "CHANGELOG.md"})
        checksums = {line.split("  ", 1)[1]: line.split("  ", 1)[0]
                     for line in (staged / "SHA256SUMS").read_text().splitlines()}
        self.assertEqual(checksums, {item["file"]: item["sha256"] for item in files})
        self.assertEqual(json.loads((staged / "metadata.json").read_text())["variants"], files)

    def test_publishing_inputs_cover_eight_distinct_uploads(self):
        variants = self.stage()
        self.assertEqual({item["target"] for item in variants}, set(self.catalog["release"]["requiredTargetIds"]))
        identities = {(platform, self.version + "+mc" + item["minecraft"] + "-" + item["loader"])
                      for platform in ("modrinth", "curseforge") for item in variants}
        self.assertEqual(len(identities), 8)
        for item in variants:
            for platform in ("modrinth", "curseforge"):
                self.assertEqual(item[platform + "Dependencies"], "\n".join(
                    self.catalog["release"]["publication"][item["loader"]][platform + "Dependencies"]))
            self.assertEqual(item["java"], self.catalog["versions"][item["minecraft"]]["java"])

    def test_reject_wrong_provider(self):
        self.entry()["task"] = ":fabric:1.21.1:jar"
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "provider"):
            self.stage()

    def test_reject_archive_outside_exact_target_output(self):
        self.entry()["archive"] = str(self.repository / Path(self.entry()["archive"]).relative_to(self.root))
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "escaped"):
            self.stage()

    def test_reject_hash_mismatch(self):
        self.entry()["sha256"] = "0" * 64
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.stage()

    def test_reject_duplicate_target_receipt(self):
        self.current["targets"].append(copy.deepcopy(self.entry()))
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.stage()

    def test_reject_wrong_receipt_java(self):
        self.entry()["java"] = 25
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "target mismatch"):
            self.stage()

    def test_reject_wrong_minecraft_jar(self):
        info = self.select("26.1.2")
        source = Path(self.entry()["archive"])
        entry = self.entry("2612Fabric")
        wrong = Path(entry["archive"])
        shutil.copy2(source, wrong)
        entry["sha256"] = hashlib.sha256(wrong.read_bytes()).hexdigest()
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "Java|Minecraft"):
            self.stage(info)

    def test_reject_wrong_loader_jar(self):
        source = Path(self.entry("1211NeoForge")["archive"])
        entry = self.entry()
        wrong = Path(entry["archive"])
        shutil.copy2(source, wrong)
        entry["sha256"] = hashlib.sha256(wrong.read_bytes()).hexdigest()
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "wrong loader"):
            self.stage()

    def test_reject_unimplemented_release_loader(self):
        catalog = copy.deepcopy(self.catalog)
        next(t for t in catalog["targets"] if t["id"] == "1211Fabric")["implemented"] = False
        (self.root / "gradle/targets.json").write_text(json.dumps(catalog))
        with self.assertRaisesRegex(ValueError, "implemented"):
            prepare.metadata()

    def test_reject_unknown_release_version(self):
        with self.assertRaisesRegex(ValueError, "Default Minecraft"):
            self.select("invalid-fixture-version")

    def test_reject_missing_target(self):
        self.current["targets"].pop()
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "Missing"):
            self.stage()

    def test_reject_catalog_target_omission(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["targets"].pop()
        (self.root / "gradle/targets.json").write_text(json.dumps(catalog))
        with self.assertRaisesRegex(ValueError, "acceptance set"):
            prepare.metadata()

    def test_reject_mislabelled_file(self):
        entry = self.entry()
        source = Path(entry["archive"])
        wrong = source.with_name("wrong-name.jar")
        shutil.copy2(source, wrong)
        entry["archive"] = str(wrong)
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "mislabelled"):
            self.stage()

    def test_reject_invalid_release_inputs(self):
        event_path = self.root / "build" / "invalid-release-event.json"
        valid = {"tag_name": "v" + self.version, "draft": False,
                 "prerelease": True, "body": "Player-facing changes"}
        for change, message in [({"tag_name": "wrong"}, "tag"),
                                ({"draft": True}, "Draft"),
                                ({"prerelease": False}, "pre-release"),
                                ({"body": ""}, "release notes")]:
            event_path.write_text(json.dumps({"release": valid | change}))
            with patch.dict("os.environ", {"GITHUB_EVENT_NAME": "release",
                                           "GITHUB_EVENT_PATH": str(event_path)}):
                with self.assertRaisesRegex(ValueError, message):
                    prepare.release_notes(prepare.metadata())

    def test_declared_java_toolchains_are_emitted_in_install_order(self):
        output = self.root / "build" / "github-output.txt"
        with patch.dict("os.environ", {"GITHUB_OUTPUT": str(output), "GITHUB_EVENT_NAME": "push"}), \
                patch("sys.argv", ["prepare.py"]), redirect_stdout(io.StringIO()):
            prepare.main()
        expected = sorted({self.catalog["versions"][target["minecraft"]]["java"]
                           for target in self.catalog["targets"]})
        content = output.read_text().splitlines()
        start = content.index("java_versions<<TARGET_JAVA_VERSIONS") + 1
        self.assertEqual(content[start:content.index("TARGET_JAVA_VERSIONS")], list(map(str, expected)))

    def test_reject_unsupported_loader_before_assuming_native_metadata(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["targets"][0]["loader"] = "unsupported-fixture-loader"
        (self.root / "gradle/targets.json").write_text(json.dumps(catalog))
        with self.assertRaisesRegex(ValueError, "No native archive validator"):
            prepare.metadata()

    def test_reject_malformed_platform_dependency_id(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["release"]["publication"]["fabric"]["modrinthDependencies"][0] = "architectury-api(required)"
        (self.root / "gradle/targets.json").write_text(json.dumps(catalog))
        with self.assertRaisesRegex(ValueError, "dependency identifier"):
            prepare.metadata()

    def test_reject_missing_native_access_policy(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["versions"]["1.21.1"]["nativeAccess"].pop("fabric")
        (self.root / "gradle/targets.json").write_text(json.dumps(catalog))
        with self.assertRaisesRegex(ValueError, "Native access policy"):
            prepare.metadata()

    def test_native_access_acceptance_follows_catalog(self):
        catalog = copy.deepcopy(self.catalog)
        catalog["versions"]["1.21.1"]["nativeAccess"]["fabric"] = False
        (self.root / "gradle/targets.json").write_text(json.dumps(catalog))
        with self.assertRaisesRegex(ValueError, "access widener"):
            self.stage()

    def test_reject_dangling_modern_access_declaration(self):
        self.rewrite_jar("2612Fabric", {"fabric.mod.json": lambda content: json.dumps(
            json.loads(content) | {"accessWidener": "missing.accesswidener"}).encode()})
        with self.assertRaisesRegex(ValueError, "access widener"):
            self.stage()

    def test_reject_unregistered_legacy_access_transformer(self):
        self.rewrite_jar("1211NeoForge", {"META-INF/neoforge.mods.toml": lambda content:
                         re.sub(rb"\[\[accessTransformers\]\][\s\S]*", b"", content)})
        with self.assertRaisesRegex(ValueError, "access transformer"):
            self.stage()

    def test_reject_unregistered_native_mixin_configs(self):
        self.rewrite_jar("1211Fabric", {"fabric.mod.json": lambda content: json.dumps(
            json.loads(content) | {"mixins": []}).encode()})
        with self.assertRaisesRegex(ValueError, "native mixin registrations"):
            self.stage()

    def test_reject_duplicate_mixin_class_registration(self):
        def duplicate_first_mixin(content):
            config = json.loads(content)
            config["mixins"].append(config["mixins"][0])
            return json.dumps(config).encode()
        self.rewrite_jar("1211Fabric", {"essence_ascendance.mixins.json": duplicate_first_mixin})
        with self.assertRaisesRegex(ValueError, "duplicate mixin registration"):
            self.stage()

    def test_reject_existing_staging_directory(self):
        destination = self.root / "build" / self._testMethodName
        destination.mkdir()
        sentinel = destination / "prior-build.txt"
        sentinel.write_text("Preserve the existing bundle")
        with self.assertRaises(FileExistsError):
            self.stage()
        self.assertEqual(sentinel.read_text(), "Preserve the existing bundle")


if __name__ == "__main__":
    unittest.main()
