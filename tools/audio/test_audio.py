"""Authoring-only acceptance tests; never run during normal Minecraft builds."""
import copy
import tempfile
import unittest
from pathlib import Path
import encoding
import synthesis
from storage import REPO, Storage, digest, inside, lease, read, write

class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.recipe = read(REPO / 'art/source/audio/recipes/process_complete_a.json')

    def tearDown(self):
        self.temp.cleanup()

    def master(self, recipe=None):
        rate, stems = synthesis.render(recipe or self.recipe)
        path = self.root / 'master.wav'
        synthesis.wav(path, sum(stems.values()), rate)
        return path

    def test_two_recipe_shapes_and_determinism(self):
        for name in ('process_complete_a', 'resonance_body_a'):
            recipe = read(REPO / f'art/source/audio/recipes/{name}.json')
            first = synthesis.render(recipe)
            second = synthesis.render(recipe)
            self.assertEqual(list(first[1]), list(second[1]))
            for key in first[1]:
                self.assertTrue((first[1][key] == second[1][key]).all())
            self.assertGreater(len(first[1]), 1)

    def test_encoding_and_real_decode_validation(self):
        master = self.master()
        a, b = self.root / 'a.ogg', self.root / 'b.ogg'
        encoding.encode(master, a)
        encoding.encode(master, b)
        self.assertEqual(digest(a), digest(b))
        measured = encoding.validate(a, expected_duration=0.65)
        self.assertEqual(measured['codec'], 'vorbis')
        self.assertEqual(measured['channels'], 1)
        self.assertLessEqual(measured['truePeakDb'], -1)
        disguised = self.root / 'disguised.ogg'
        disguised.write_bytes(master.read_bytes())
        with self.assertRaises(ValueError):
            encoding.validate(disguised)
        truncated = self.root / 'truncated.ogg'
        truncated.write_bytes(a.read_bytes()[:80])
        with self.assertRaises(ValueError):
            encoding.validate(truncated)

    def test_opus_stereo_rate_levels_and_duration_rejected(self):
        master = self.master()
        cases = [(['-c:a', 'libopus'], 'opus'),
                 (['-c:a', 'libvorbis', '-ac', '2'], 'stereo'),
                 (['-c:a', 'libvorbis', '-ar', '44100'], 'rate'),
                 (['-af', 'volume=8', '-c:a', 'libvorbis'], 'levels')]
        for options, name in cases:
            with self.subTest(name=name):
                path = self.root / (name + '.ogg')
                encoding.run(['-v', 'error', '-i', master, *options, path])
                with self.assertRaises(ValueError):
                    encoding.validate(path)
                if name == 'opus':
                    with self.assertRaises(ValueError):
                        encoding.validate(path, vorbis=False)
        good = self.root / 'good.ogg'
        encoding.encode(master, good)
        with self.assertRaises(ValueError):
            encoding.validate(good, expected_duration=4)

    def test_loop_seam(self):
        recipe = read(REPO / 'art/source/audio/recipes/resonance_body_a.json')
        master = self.master(recipe)
        output = self.root / 'loop.ogg'
        encoding.encode(master, output)
        measured = encoding.validate(output, loop=True, expected_duration=2.75)
        self.assertLess(measured['seamDelta'], 0.02)

    def test_short_transient_loudness_is_labeled(self):
        recipe = copy.deepcopy(self.recipe)
        recipe['duration'] = 0.08
        master = self.master(recipe)
        output = self.root / 'short.ogg'
        encoding.encode(master, output)
        result = encoding.validate(output, expected_duration=0.08)
        self.assertEqual(result['loudnessMethod'], 'zero_padded_to_400ms')

    def test_storage_boundaries_and_revision_reservation(self):
        store = Storage(self.root)
        self.assertEqual(store.revision('machine', 'body'), 'r0001')
        self.assertEqual(store.revision('machine', 'body'), 'r0002')
        for path in ('../escape', str(self.root / 'absolute')):
            with self.assertRaises(ValueError):
                inside(store.root, path)
        with lease(store.root):
            with self.assertRaises(FileExistsError):
                with lease(store.root):
                    pass
        duplicate = self.root / 'duplicate.json'
        duplicate.write_text('{"x":1,"x":2}')
        with self.assertRaises(ValueError):
            read(duplicate)

    def test_review_promotion_and_invalidation(self):
        import audio
        from argparse import Namespace
        old = audio.REPO
        audio.REPO = self.root / 'repo'
        try:
            art = audio.REPO / 'art'
            recipe_path = art / 'source/audio/recipes/process_complete_a.json'
            write(recipe_path, self.recipe)
            write(art / 'audio-manifest.json', {'schema':1,'assets':{
                'process_complete_a': {'family':'process','scope':'world',
                'recipe':'source/audio/recipes/process_complete_a.json','revisions':[]}}})
            write(art / 'audio-catalog.json', {'schema':1,'assets':{
                'process_complete_a':{'status':'authoring_only','resource':'essence_ascendance:process/complete_a'}}})
            args = Namespace(asset='process_complete_a', auxiliary=str(self.root / 'aux'),
                             batch='test', from_project=None, without_project=True, revision='r0001')
            audio.author(args)
            with self.assertRaises(ValueError):
                audio.promote(args)
            args.accept=True
            args.reviewer='Automated fixture only'
            args.notes='Test data; never production listening approval'
            args.license='Test fixture'
            args.attribution='Generated test fixture'
            args.listened=False
            args.mix_reviewed=False
            args.loop_reviewed=False
            with self.assertRaises(ValueError):
                audio.review(args)
            args.listened=True
            args.mix_reviewed=True
            audio.review(args)
            # DSP-only take still cannot promote despite its simulated acceptance.
            with self.assertRaises(ValueError):
                audio.promote(args)
            manifest=read(art/'audio-manifest.json')
            record=manifest['assets'][args.asset]['revisions'][0]
            store=Storage(args.auxiliary)
            # Simulate a finalized editable session in this isolated test repository.
            import sqlite3
            from audacity import closed_backup
            project=inside(store.root,'projects/process/process_complete_a/r0001/test.aup3')
            project.parent.mkdir(parents=True, exist_ok=True)
            connection=sqlite3.connect(project)
            connection.execute('CREATE TABLE fixture(value)')
            connection.commit()
            connection.close()
            backup=inside(store.root,'backups/process/process_complete_a/r0001/test.aup3')
            backup.parent.mkdir(parents=True)
            record['project']=project.relative_to(store.root).as_posix()
            record['backup']=closed_backup(project,backup)
            record['backup']['path']=backup.relative_to(store.root).as_posix()
            accepted_review=copy.deepcopy(record['review'])
            record['review']={'status':'pending'}
            record['creativeReview']={'status':'accepted','reviewer':'Fixture user',
                                      'at':'fixture','notes':'Simulated creative acceptance'}
            write(art/'audio-manifest.json',manifest)
            selection={'schema':1,'items':[{'asset':args.asset,'revision':args.revision,
                'purpose':'runtime','sourceExportSha256':record['exportSha256']}]}
            selection_path=art/'source/audio/handoffs/fixture.json'
            write(selection_path,selection)
            args.integration_selection='source/audio/handoffs/fixture.json'
            args.integration_authority='Integrate the approved fixture audio.'
            for field,value in [('purpose','demonstration'),('sourceExportSha256','0'*64)]:
                with self.subTest(invalid_selection=field):
                    invalid=copy.deepcopy(selection)
                    invalid['items'][0][field]=value
                    write(selection_path,invalid)
                    with self.assertRaises(ValueError):
                        audio.promote(args)
            write(selection_path,selection)
            for status in ('pending','revise_requested'):
                with self.subTest(creative_review=status):
                    record['creativeReview']['status']=status
                    write(art/'audio-manifest.json',manifest)
                    with self.assertRaises(ValueError):
                        audio.promote(args)
            record['creativeReview']['status']='accepted'
            write(art/'audio-manifest.json',manifest)
            audio.promote(args)
            audio.promote(args)
            integrated=read(art/'audio-catalog.json')['assets'][args.asset]
            integration_receipt=art/integrated['receipt']
            receipt=read(integration_receipt)
            receipt_hash=digest(integration_receipt)
            self.assertEqual(receipt['review'],{'status':'pending'})
            self.assertEqual(receipt['sourceLinks']['project'],record['project'])
            self.assertEqual(receipt['integrationPromotion']['selectionSha256'],digest(selection_path))
            self.assertEqual(digest(art/integrated['approved']),record['exportSha256'])
            manifest=read(art/'audio-manifest.json')
            record=manifest['assets'][args.asset]['revisions'][0]
            self.assertEqual(record['review'],{'status':'pending'})
            # A later real final review selects a new receipt and preserves the
            # exact integration record. Normal promotion never accepts pending.
            args.integration_selection=None
            args.integration_authority=None
            with self.assertRaises(ValueError):
                audio.promote(args)
            record['review']=accepted_review
            write(art/'audio-manifest.json',manifest)
            audio.promote(args)
            audio.promote(args)  # idempotent promotion does not replace historical files
            promoted=read(art/'audio-catalog.json')['assets'][args.asset]
            self.assertEqual(promoted['status'],'approved')
            self.assertEqual(digest(art/promoted['approved']),record['exportSha256'])
            self.assertEqual(digest(integration_receipt),receipt_hash)
            self.assertNotEqual(integrated['receipt'],promoted['receipt'])
            inside(store.root, record['master']).write_bytes(b'changed source')
            with self.assertRaises(ValueError):
                audio.verify_candidate(store,record,'world')
        finally:
            audio.REPO = old

    def test_backup_requires_closed_database(self):
        import sqlite3
        from audacity import closed_backup, ensure_closed
        project = self.root / 'project.aup3'
        connection = sqlite3.connect(project)
        connection.execute('PRAGMA journal_mode=WAL')
        connection.execute('CREATE TABLE test(value)')
        connection.commit()
        with self.assertRaises(RuntimeError):
            ensure_closed(project)
        connection.close()
        result = closed_backup(project, self.root / 'backup.aup3')
        self.assertTrue(result['closed'])
        self.assertEqual(result['projectSha256'], result['backupSha256'])

if __name__ == '__main__':
    unittest.main()
