"""Audit selection and source-preservation failures without generating audio."""
import copy
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path
from unittest.mock import patch
import audit
from storage import digest, write


class AuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.item = {'asset': 'test_tap', 'revision': 'r0001', 'purpose': 'runtime'}
        recipe = {'assetId': 'test_tap', 'sampleRate': 48000, 'seed': 7,
                  'layers': [{'name': 'contact'}], 'design': {'cueIds': ['test:tap']}}
        write(self.root / 'recipe.json', recipe)
        write(self.root / 'inputs.json', {'recipe': {'sha256': '0' * 64, 'content': recipe}})
        for name in ['master.wav', 'export.ogg', 'contact.wav', 'project.aup3', 'project-render.wav']:
            (self.root / name).write_bytes(('Fixture only: ' + name).encode())
        self.record = {'id': 'r0001', 'creativeReview': {'status': 'accepted'}, 'review': {'status': 'pending'},
            'master': 'master.wav', 'masterSha256': digest(self.root / 'master.wav'),
            'export': 'export.ogg', 'exportSha256': digest(self.root / 'export.ogg'), 'preview': 'export.ogg',
            'recipeSnapshot': 'recipe.json', 'recipeSha256': digest(self.root / 'recipe.json'),
            'authoringInputs': 'inputs.json', 'authoringInputsSha256': digest(self.root / 'inputs.json'),
            'project': 'project.aup3', 'backup': {'closed': True, 'path': 'project.aup3',
                'projectSha256': digest(self.root / 'project.aup3'), 'backupSha256': digest(self.root / 'project.aup3')},
            'stems': [{'path': 'contact.wav', 'sha256': digest(self.root / 'contact.wav')}],
            'projectEvidence': {'tracks': [{'name': 'contact'}], 'render': 'project-render.wav',
                'renderSha256': digest(self.root / 'project-render.wav')},
            'masterMeasurements': {'codec': 'pcm_s24le', 'sampleRate': 48000, 'channels': 1},
            'measurements': {}, 'loop': False, 'cueIds': ['test:tap'], 'sourceMethod': 'synthesis',
            'provenance': {'seed': 7}, 'encoder': {}}
        newer = {**self.record, 'id': 'r0002', 'creativeReview': {'status': 'revise_requested'}}
        self.entry = {'family': 'test', 'scope': 'world', 'revisions': [self.record, newer]}
        self.manifest = {'assets': {'test_tap': self.entry}}
        self.catalog = {'assets': {'test_tap': {'status': 'authoring_only'}}, 'cues': {'test:tap': {}}}

    def tearDown(self):
        self.temp.cleanup()

    def test_exact_accepted_revision_is_selected_instead_of_latest(self):
        selected = audit.select({'schema': 1, 'items': [self.item]}, self.manifest)
        self.assertIs(selected[0][2], self.record)
        with self.assertRaisesRegex(ValueError, 'lacks creative acceptance'):
            audit.select({'schema': 1, 'items': [{**self.item, 'revision': 'r0002'}]}, self.manifest)

    def test_duplicate_purpose_and_unpinned_revision_are_rejected(self):
        selections = [[self.item, self.item], [{**self.item, 'purpose': 'approved'}],
                      [{**self.item, 'revision': 'latest'}]]
        for items in selections:
            with self.subTest(items=items), self.assertRaises(ValueError):
                audit.select({'schema': 1, 'items': items}, self.manifest)

    def test_pinned_export_and_cue_associations_must_match(self):
        for key, value in [('sourceExportSha256', 'f' * 64), ('cueIds', []), ('proposedCueIds', ['test:new'])]:
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, 'association differs'):
                audit.select({'schema': 1, 'items': [{**self.item, key: value}]}, self.manifest)

    def test_preview_only_take_cannot_become_runtime_selection(self):
        self.record['design'] = {'previewOnly': True}
        with self.assertRaisesRegex(ValueError, 'preview-only'):
            audit.select({'schema': 1, 'items': [self.item]}, self.manifest)
        result = audit.select({'schema': 1, 'items': [{**self.item, 'purpose': 'demonstration'}]}, self.manifest)
        self.assertEqual(result[0][0]['purpose'], 'demonstration')

    def test_missing_frozen_inputs_cannot_pass_even_with_shared_validation_mocked(self):
        (self.root / 'inputs.json').unlink()
        with patch.object(audit.audio, 'verify_candidate') as verify, \
                patch.object(audit, 'project_integrity'), patch.object(audit.quality, 'inspect'):
            with self.assertRaisesRegex(ValueError, 'Missing authoring file'):
                audit.audit_take(self.item, self.entry, self.record, self.root, self.catalog, self.root)
            verify.assert_not_called()

    def test_source_snapshot_mismatch_and_track_flattening_are_rejected(self):
        for mutate in ('seed', 'track'):
            record = copy.deepcopy(self.record)
            if mutate == 'seed':
                record['provenance']['seed'] = 99
            else:
                record['projectEvidence']['tracks'][0]['name'] = 'Flattened export'
            with self.subTest(mutate=mutate), patch.object(audit.audio, 'verify_candidate'), \
                    patch.object(audit, 'project_integrity'), patch.object(audit.quality, 'inspect'):
                with self.assertRaises(ValueError):
                    audit.audit_take(self.item, self.entry, record, self.root, self.catalog, self.root)

    def test_immutable_sqlite_inspection_preserves_file_and_creates_no_sidecars(self):
        path = self.root / 'synthetic-database.aup3'
        with closing(sqlite3.connect(path)) as database:
            database.execute('CREATE TABLE sampleblocks (id INTEGER)')
            database.execute('INSERT INTO sampleblocks VALUES (1)')
            database.commit()
        before = digest(path)
        result = audit.project_integrity(path, before)
        self.assertEqual(result['sampleblocks'], 1)
        self.assertEqual(before, digest(path))
        for suffix in ('-wal', '-shm', '-journal'):
            self.assertFalse(Path(str(path) + suffix).exists())


if __name__ == '__main__':
    unittest.main()
