"""Regression tests for the standalone, dependency-free preset publisher."""

from __future__ import annotations

import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
import zipfile


_SPEC = importlib.util.spec_from_file_location(
    "preset_builder_under_test", Path(__file__).with_name("build.py")
)
assert _SPEC is not None and _SPEC.loader is not None
preset_builder = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = preset_builder
_SPEC.loader.exec_module(preset_builder)


class PresetBuildTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="essence-preset-tests-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.entries: list[dict] = []
        self.entry = self.add_preset()
        self.save_catalog()
        # These fixtures have no repository. Never inspect the real Git index.
        tracked = mock.patch.object(preset_builder, "is_tracked", return_value=False)
        self.is_tracked = tracked.start()
        self.addCleanup(tracked.stop)

    def add_preset(
        self, preset_id: str = "test-pack", revision: str = "1.0-r1", **metadata
    ) -> dict:
        entry = {
            "id": preset_id,
            "revision": revision,
            "pack_name": "Test Pack",
            "pack_version": "1.0",
            "minecraft": "26.1.2",
            "loader": "NeoForge",
            "config_format": "1.0.0-beta.15",
            "status": "Playtest",
            "tested_on": "2026-11-07",
            "summary": "A test preset.",
            "compatibility_notes": [],
            "source": f"{preset_id}/{revision}",
            "page": f"Preset-{preset_id.title()}-{revision.replace('.', '-')}",
        }
        entry.update(metadata)
        source = self.root / "presets" / entry["source"]
        self.write(source / "README.txt", "# Test preset\n\nInstall the supplied files.\n")
        self.write(source / "LICENSE.txt", "Test fixture license: redistribution is permitted.\n")
        self.write(
            source / "files/config/essence_ascendance.toml",
            "[general]\nenabled = true\nessenceMultiplier = 1.25\n",
        )
        self.write(
            source / "files/datapacks/test_pack/pack.mcmeta",
            json.dumps({"pack": {"pack_format": 101, "description": "Test preset"}})
            + "\n",
        )
        self.write(
            source / "files/datapacks/test_pack/data/example/tags/item/test.json",
            '{"values": ["minecraft:stone"]}\n',
        )
        self.entries.append(entry)
        return entry

    @staticmethod
    def write(path: Path, content: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")

    def save_catalog(self) -> None:
        self.write(
            self.root / "presets/catalog.json",
            json.dumps({"schema": 1, "entries": self.entries}, indent=2) + "\n",
        )

    def source(self, entry: dict | None = None) -> Path:
        return self.root / "presets" / (entry or self.entry)["source"]

    def archive(self, entry: dict | None = None) -> Path:
        chosen = entry or self.entry
        return self.root / "presets/downloads" / f"{chosen['id']}-{chosen['revision']}.zip"

    def snapshot(self) -> dict[str, bytes | None]:
        return {
            path.relative_to(self.root).as_posix(): None if path.is_dir() else path.read_bytes()
            for path in self.root.rglob("*")
        }

    def assert_rejected_without_writes(self) -> None:
        before = self.snapshot()
        with self.assertRaises(preset_builder.PresetError):
            preset_builder.build(self.root, write=True)
        self.assertEqual(before, self.snapshot())

    @staticmethod
    def checksums(content: bytes) -> dict[str, str]:
        result = {}
        for line in content.decode("utf-8").splitlines():
            digest, name = line.split("  ", 1)
            if name in result:
                raise AssertionError(f"Duplicate checksum for {name}")
            result[name] = digest
        return result

    def test_wiki_normalizes_readme_newlines_but_archive_preserves_bytes(self) -> None:
        readme = b"# Test preset\r\n\r\nInstall these files.\r\n"
        (self.source() / "README.txt").write_bytes(readme)
        preset_builder.build(self.root, write=True)
        catalog = json.loads((self.root / "presets/catalog.json").read_text(encoding="utf-8"))
        page = self.root / "docs/wiki" / (catalog["entries"][0]["page"] + ".md")
        self.assertNotIn(b"\r", page.read_bytes())
        self.assertIn(b"# Test preset\n\nInstall these files.\n", page.read_bytes())
        with zipfile.ZipFile(self.archive()) as bundle:
            self.assertEqual(readme, bundle.read("README.txt"))
            self.assertEqual(hashlib.sha256(readme).hexdigest(),
                             self.checksums(bundle.read("SHA256SUMS.txt"))["README.txt"])
        preset_builder.build(self.root)

    def test_kubegroovy_data_round_trip_and_checksums(self) -> None:
        name = "kubegroovy/data/essence_ascendance/tags/item/low_value_bulk_materials.json"
        content = '{"replace": false, "values": ["minecraft:calcite"]}\n'
        self.write(self.source() / "files" / name, content)
        preset_builder.build(self.root, write=True)
        with zipfile.ZipFile(self.archive()) as bundle:
            self.assertEqual(content.encode(), bundle.read(name))
            self.assertEqual(hashlib.sha256(content.encode()).hexdigest(),
                             self.checksums(bundle.read("SHA256SUMS.txt"))[name])
        preset_builder.build(self.root)

    def test_kubegroovy_scripts_and_non_data_files_rejected(self) -> None:
        for name in ("kubegroovy/server_scripts/setup.groovy",
                     "kubegroovy/startup_scripts/setup.json",
                     "kubegroovy/assets/example/lang/en_us.json",
                     "kubegroovy/data/private.json"):
            with self.subTest(name=name):
                file = self.source() / "files" / name
                self.write(file, "{}\n")
                self.assert_rejected_without_writes()
                file.unlink()

    def test_archive_round_trip_checksums_and_reproducibility(self) -> None:
        preset_builder.build(self.root, write=True)
        archive = self.archive()
        expected = {
            name: (self.source() / name).read_bytes()
            for name in ("README.txt", "LICENSE.txt")
        }
        expected.update(
            {
                path.relative_to(self.source() / "files").as_posix(): path.read_bytes()
                for path in (self.source() / "files").rglob("*")
                if path.is_file()
            }
        )
        with zipfile.ZipFile(archive) as bundle:
            self.assertEqual(set(expected) | {"SHA256SUMS.txt"}, set(bundle.namelist()))
            self.assertEqual(len(bundle.namelist()), len(set(bundle.namelist())))
            for name, content in expected.items():
                self.assertEqual(content, bundle.read(name), name)
            self.assertEqual(
                {name: hashlib.sha256(content).hexdigest() for name, content in expected.items()},
                self.checksums(bundle.read("SHA256SUMS.txt")),
            )
        aggregate = self.root / "presets/downloads/SHA256SUMS.txt"
        self.assertEqual(
            {archive.name: hashlib.sha256(archive.read_bytes()).hexdigest()},
            self.checksums(aggregate.read_bytes()),
        )
        before = self.snapshot()
        # Source filesystem timestamps must not enter reproducible ZIP contents.
        for path in self.source().rglob("*"):
            if path.is_file():
                os.utime(path, (946684800, 946684800))
        preset_builder.build(self.root, write=True)
        self.assertEqual(before, self.snapshot())
        preset_builder.build(self.root)

    def test_check_missing_outputs_does_not_create_files_or_directories(self) -> None:
        before = self.snapshot()
        with self.assertRaises(preset_builder.PresetError):
            preset_builder.build(self.root)
        self.assertEqual(before, self.snapshot())

    def test_check_stale_page_does_not_repair_it(self) -> None:
        preset_builder.build(self.root, write=True)
        self.write(self.root / "docs/wiki/Config-Presets.md", "Stale page\n")
        before = self.snapshot()
        with self.assertRaises(preset_builder.PresetError):
            preset_builder.build(self.root)
        self.assertEqual(before, self.snapshot())

    def test_check_stale_archive_does_not_replace_it(self) -> None:
        preset_builder.build(self.root, write=True)
        self.archive().write_bytes(b"stale archive")
        before = self.snapshot()
        with self.assertRaises(preset_builder.PresetError):
            preset_builder.build(self.root)
        self.assertEqual(before, self.snapshot())

    def test_malformed_toml_is_rejected(self) -> None:
        self.write(self.source() / "files/config/essence_ascendance.toml", "[broken\n")
        self.assert_rejected_without_writes()

    def test_readme_and_license_must_be_present_nonempty_utf8(self) -> None:
        for name in ("README.txt", "LICENSE.txt"):
            path = self.source() / name
            original = path.read_bytes()
            for content in (None, b"", b" \n\t", b"\xff"):
                with self.subTest(name=name, content=content):
                    try:
                        if content is None:
                            path.unlink()
                        else:
                            path.write_bytes(content)
                        self.assert_rejected_without_writes()
                    finally:
                        path.write_bytes(original)

    def test_malformed_json_and_pack_metadata_are_rejected(self) -> None:
        paths = (
            "files/datapacks/test_pack/pack.mcmeta",
            "files/datapacks/test_pack/data/example/tags/item/test.json",
        )
        for relative in paths:
            with self.subTest(path=relative):
                path = self.source() / relative
                original = path.read_bytes()
                self.write(path, '{"broken": }\n')
                self.assert_rejected_without_writes()
                path.write_bytes(original)

    def test_nonfinite_toml_numbers_are_rejected(self) -> None:
        for number in ("nan", "+inf", "-inf"):
            with self.subTest(number=number):
                self.write(
                    self.source() / "files/config/essence_ascendance.toml",
                    f"[general]\nvalues = [1, {number}]\n",
                )
                self.assert_rejected_without_writes()

    def test_nonfinite_json_numbers_are_rejected(self) -> None:
        for number in ("NaN", "Infinity", "-Infinity", "1e999"):
            with self.subTest(number=number):
                self.write(
                    self.source() / "files/datapacks/test_pack/data/example/tags/item/test.json",
                    '{"nested": {"values": [1, ' + number + "]}}\n",
                )
                self.assert_rejected_without_writes()

    def test_source_path_traversal_is_rejected(self) -> None:
        for source in ("../outside", "test-pack/../../outside", "/outside", "..\\outside"):
            with self.subTest(source=source):
                self.entry["source"] = source
                self.save_catalog()
                self.assert_rejected_without_writes()

    def test_duplicate_pack_revision_is_rejected(self) -> None:
        duplicate = dict(self.entry, page="Preset-Another-Page")
        self.entries.append(duplicate)
        self.save_catalog()
        self.assert_rejected_without_writes()

    def test_duplicate_detail_page_is_rejected(self) -> None:
        self.add_preset(revision="1.0-r2", page=self.entry["page"])
        self.save_catalog()
        self.assert_rejected_without_writes()

    def test_disallowed_payloads_are_rejected(self) -> None:
        for relative in (
            "files/mods/unexpected.jar",
            "files/options.txt",
            "files/config/essence_ascendance/balance-profile.json",
        ):
            with self.subTest(path=relative):
                path = self.source() / relative
                self.write(path, "{}\n")
                self.assert_rejected_without_writes()
                path.unlink()

    def test_changed_archive_requires_explicit_unpublished_replacement(self) -> None:
        preset_builder.build(self.root, write=True)
        original = self.archive().read_bytes()
        self.write(self.source() / "files/config/essence_ascendance.toml", "enabled = false\n")
        before = self.snapshot()
        with self.assertRaises(preset_builder.PresetError):
            preset_builder.build(self.root, write=True)
        self.assertEqual(before, self.snapshot())
        preset_builder.build(self.root, write=True, replace_unpublished=True)
        self.assertNotEqual(original, self.archive().read_bytes())
        preset_builder.build(self.root)

    def test_tracked_archive_cannot_be_replaced_even_with_override(self) -> None:
        preset_builder.build(self.root, write=True)
        self.write(self.source() / "files/config/essence_ascendance.toml", "enabled = false\n")
        self.is_tracked.reset_mock()
        self.is_tracked.return_value = True
        before = self.snapshot()
        with self.assertRaises(preset_builder.PresetError):
            preset_builder.build(self.root, write=True, replace_unpublished=True)
        self.assertTrue(self.is_tracked.called)
        self.assertEqual(before, self.snapshot())

    def test_missing_tracked_archive_must_not_be_recreated(self) -> None:
        preset_builder.build(self.root, write=True)
        self.archive().unlink()
        self.is_tracked.return_value = True
        before = self.snapshot()
        with self.assertRaises(preset_builder.PresetError):
            preset_builder.build(self.root, write=True)
        self.assertFalse(self.archive().exists())
        self.assertEqual(before, self.snapshot())

    def test_archive_incidental_metadata_is_rejected_without_repair(self) -> None:
        preset_builder.build(self.root, write=True)
        original = self.archive().read_bytes()
        valid_state = self.snapshot()
        preset_builder.build(self.root)
        for metadata in ("entry_comment", "entry_extra", "archive_comment"):
            with self.subTest(metadata=metadata):
                rewritten = io.BytesIO()
                with zipfile.ZipFile(io.BytesIO(original)) as source:
                    expected_payloads = {name: source.read(name) for name in source.namelist()}
                    with zipfile.ZipFile(rewritten, mode="w") as destination:
                        for index, info in enumerate(source.infolist()):
                            if index == 0 and metadata == "entry_comment":
                                info.comment = b"Incidental author-machine metadata"
                            if index == 0 and metadata == "entry_extra":
                                info.extra = b"\xfe\xca\x00\x00"
                            destination.writestr(info, expected_payloads[info.filename])
                        if metadata == "archive_comment":
                            destination.comment = b"Incidental author-machine metadata"
                with zipfile.ZipFile(io.BytesIO(rewritten.getvalue())) as mutated:
                    self.assertEqual(
                        expected_payloads,
                        {name: mutated.read(name) for name in mutated.namelist()},
                    )
                try:
                    self.archive().write_bytes(rewritten.getvalue())
                    before = self.snapshot()
                    with self.assertRaises(preset_builder.PresetError):
                        preset_builder.build(self.root)
                    self.assertEqual(before, self.snapshot())
                    with self.assertRaises(preset_builder.PresetError):
                        preset_builder.build(self.root, write=True)
                    self.assertEqual(before, self.snapshot())
                finally:
                    # A rejected regression can mutate generated pages/checksums;
                    # restore every fixture file before the next metadata case.
                    for relative, content in valid_state.items():
                        if content is not None:
                            (self.root / relative).write_bytes(content)
        preset_builder.build(self.root)

    def test_multiple_packs_and_versions_get_distinct_outputs(self) -> None:
        self.add_preset(revision="1.1-r1", pack_version="1.1")
        self.add_preset("other-pack", pack_name="Other Pack", minecraft="1.21.1", loader="Fabric")
        self.save_catalog()
        preset_builder.build(self.root, write=True)
        index = (self.root / "docs/wiki/Config-Presets.md").read_text(encoding="utf-8")
        aggregate = self.checksums((self.root / "presets/downloads/SHA256SUMS.txt").read_bytes())
        self.assertEqual({self.archive(entry).name for entry in self.entries}, set(aggregate))
        for entry in self.entries:
            with self.subTest(preset=entry["source"]):
                self.assertTrue(self.archive(entry).is_file())
                detail = self.root / "docs/wiki" / (entry["page"] + ".md")
                self.assertTrue(detail.is_file())
                self.assertIn(entry["page"], index)
                self.assertIn(entry["pack_name"], detail.read_text(encoding="utf-8"))
        preset_builder.build(self.root)

    def test_payload_symlink_is_rejected(self) -> None:
        outside = self.root / "outside.toml"
        self.write(outside, "enabled = true\n")
        path = self.source() / "files/config/essence_ascendance.toml"
        path.unlink()
        try:
            path.symlink_to(outside)
        except (NotImplementedError, OSError) as error:
            self.skipTest(f"File symlinks are unavailable: {error}")
        self.assert_rejected_without_writes()


if __name__ == "__main__":
    unittest.main()
