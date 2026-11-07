"""Acceptance launcher regression checks; no Minecraft process or user world is opened."""
import itertools
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import achievement_packaged_acceptance as launcher


class AcceptanceLauncherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.root_patch = patch.object(launcher, "ROOT", self.root)
        self.root_patch.start()

    def tearDown(self):
        self.root_patch.stop()
        self.temp.cleanup()

    def test_only_disposable_boundary_is_writable(self):
        allowed = self.root / "build/achievement-packaged-acceptance/test/server"
        self.assertEqual(launcher.isolated(allowed), allowed)
        for path in (self.root / "normal-world", allowed / "../../../ordinary-world"):
            with self.assertRaises(ValueError):
                launcher.isolated(path)

    def test_fixture_cannot_override_production_class(self):
        path = self.root / "fixture.jar"
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr("com/mistaboom/essence_ascendance/achievementfixture/AchievementSocketFixture.class", b"test")
            jar.writestr("com/mistaboom/essence_ascendance/achievement/AchievementService.class", b"override")
        with self.assertRaisesRegex(ValueError, "production override"):
            launcher.socket_fixture(path)

    def test_missing_fixture_entrypoint_rejects(self):
        path = self.root / "fixture.jar"
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr("fabric.mod.json", "{}")
        with self.assertRaisesRegex(ValueError, "entrypoint"):
            launcher.socket_fixture(path)

    def test_native_player_storage_layout_is_version_specific(self):
        expected = str(launcher.runtime.offline_player_uuid("EA_B")) + ".json"
        server = self.root / "server"
        self.assertEqual(launcher.advancement_path({"minecraft": "1.21.1"}, server, "EA_B"), server / "world/advancements" / expected)
        self.assertEqual(launcher.advancement_path({"minecraft": "26.1.2"}, server, "EA_B"), server / "world/players/advancements" / expected)

    def test_repeated_old_console_text_cannot_satisfy_new_command(self):
        directory = self.root / "server"
        directory.mkdir()
        class Process:
            pid = 123
            returncode = None
            def __init__(self, *args, **kwargs):
                self.log = kwargs["stdout"]
                self.stdin = self
                self.log.write("Done (1s)!\n")
                self.log.flush()
            def write(self, command):
                if command == "first\n":
                    self.log.write("ACK\n")
                    self.log.flush()
            def flush(self): pass
            def poll(self): return self.returncode
            def terminate(self): self.returncode = -1
            def wait(self, timeout=None): return self.returncode
        with patch.object(launcher.subprocess, "Popen", Process), patch.object(launcher.time, "sleep"), \
                patch.object(launcher.time, "monotonic", side_effect=itertools.count()):
            with self.assertRaisesRegex(TimeoutError, "missing console result ACK"):
                launcher.run_stage(directory, ["unused"], "reused", [("first", "ACK"), ("second", "ACK")], [])

    def test_setup_failure_still_leaves_failed_receipt(self):
        (self.root / "build").mkdir()
        (self.root / "gradle").mkdir()
        (self.root / "build/production-artifacts-all.json").write_text(json.dumps({"targets": [{"target": "1211Fabric"}]}))
        (self.root / "gradle/targets.json").write_text(json.dumps({"release": {"requiredTargetIds": ["1211Fabric"]}}))
        with patch("sys.argv", ["launcher", "--label", "setup-failure"]), \
                patch.object(launcher.runtime, "production_archive"), \
                patch.object(launcher, "accept", side_effect=FileNotFoundError("Missing exact dependency")), \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(launcher.main(), 1)
        receipt = json.loads((self.root / "build/achievement-packaged-acceptance/setup-failure/1211Fabric/receipt.json").read_text())
        self.assertEqual(receipt["result"], "FAIL")
        self.assertEqual(receipt["stage"], "setup")
        self.assertTrue(Path(receipt["log"]).is_file())


if __name__ == "__main__":
    unittest.main()
