"""Launcher isolation and evidence checks; never launch Minecraft or touch real caches."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import packaged_acceptance as launcher


class PackagedAcceptanceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.root_patch = patch.object(launcher, 'ROOT', self.root)
        self.root_patch.start()
        executable=self.root/'jdk/bin/java.exe'; executable.parent.mkdir(parents=True)
        executable.write_bytes(b'isolated test Java executable')
        (executable.parent.parent/'release').write_text('JAVA_VERSION="21-test"\n')
        self.java_patch=patch.object(launcher,'java',return_value=executable)
        self.java_patch.start()
        self.base = self.root / 'build/audio-packaged-acceptance/new-run'
        archive = self.root / 'fabric/versions/1.21.1/build/libs/essence_ascendance-test.jar'
        archive.parent.mkdir(parents=True)
        archive.write_bytes(b'exact synthetic production archive')
        self.target = {'target':'1211Fabric','loader':'fabric','minecraft':'1.21.1','java':21,
                       'archive':str(archive),'sha256':launcher.digest(archive)}
        self.fixture = self.root / 'audio-fixture.jar'
        self.make_fixture()

    def tearDown(self):
        self.java_patch.stop()
        self.root_patch.stop()
        self.temp.cleanup()

    def make_fixture(self, extra=None):
        with zipfile.ZipFile(self.fixture, 'w') as jar:
            jar.writestr('fabric.mod.json', '{}')
            jar.writestr('com/mistaboom/essence_ascendance/audiofixture/AudioLoaderTest.class', b'fixture')
            if extra: jar.writestr(extra, b'not allowed')

    def test_escape_rejected_before_game_directory_is_created(self):
        for path in (self.root/'outside', self.base/'../../../outside'):
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, 'remain inside'):
                launcher.prepare_client(self.target, path)
        self.assertFalse((self.root/'outside').exists())

    def test_redirected_run_parent_is_rejected(self):
        boundary=self.root/'build/audio-packaged-acceptance'
        boundary.mkdir(parents=True)
        outside=self.root/'outside'; outside.mkdir()
        try:
            os.symlink(outside, boundary/'redirect', target_is_directory=True)
        except OSError as exc:
            self.skipTest('Host does not permit test directory symlinks: '+str(exc))
        with self.assertRaises(ValueError): launcher.isolated_path(boundary/'redirect'/'EA_A')
        self.assertEqual(list(outside.iterdir()), [])

    def test_cli_rejects_path_as_player_name_before_loading_manifest(self):
        with patch('sys.argv', ['packaged_acceptance.py','--target','1211Fabric','--label','new','--name','../outside']), \
             contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as caught:
            launcher.main()
        self.assertEqual(caught.exception.code, 2)
        self.assertFalse((self.root/'build').exists())

    def test_offline_identity_matches_native_server_known_players(self):
        self.assertEqual(str(launcher.offline_player_uuid('EA_A')),'40811a2a-1bb1-3be7-aea5-ed3c75f031ff')
        self.assertEqual(str(launcher.offline_player_uuid('EA_B')),'1f09c5e9-ff9b-38d9-8f56-de74ace9681e')
        self.assertNotEqual(launcher.offline_player_uuid('EA_A'),launcher.offline_player_uuid('EA_B'))
        self.assertNotEqual(launcher.offline_player_uuid('EA_A'),launcher.offline_player_uuid('ea_a'))

    def test_client_command_uses_same_offline_identity_as_dedicated_server(self):
        (self.root/'gradle').mkdir()
        (self.root/'gradle/targets.json').write_text(json.dumps({'versions':{'1.21.1':{'fabricLoader':'test'}}}))
        cache=self.root/'loom'; version=cache/'1.21.1'; version.mkdir(parents=True)
        client=version/'minecraft-client.jar'; client.write_bytes(b'isolated command test client')
        (version/'mojang_minecraft_info.json').write_text(json.dumps({'libraries':[],
            'downloads':{'client':{'sha1':launcher.hashlib.sha1(client.read_bytes()).hexdigest()}},'assetIndex':{'id':'test'}}))
        loader=self.root/'loader.jar'
        with zipfile.ZipFile(loader,'w') as archive: archive.writestr('fabric-installer.json',json.dumps({'libraries':{}}))
        game=self.base/'EA_B'; game.mkdir(parents=True)
        with patch.object(launcher,'LOOM',cache),patch.object(launcher,'library',return_value=loader), \
             patch.object(launcher,'offline_player_uuid',wraps=launcher.offline_player_uuid) as identity:
            launcher.client_command(self.target,game,multiplayer='127.0.0.1:25565')
        identity.assert_called_once_with('EA_B')
        args=[json.loads(line) for line in (game/'launch.args').read_text().splitlines()]
        self.assertEqual(args[args.index('--username')+1],'EA_B')
        self.assertEqual(args[args.index('--uuid')+1],'1f09c5e9ff9b38d98f56de74ace9681e')

    def test_changed_archive_fails_before_installation(self):
        Path(self.target['archive']).write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'hash differs'):
            launcher.prepare_client(self.target, self.base/'EA_A')
        self.assertFalse(self.base.exists())

    def test_archive_from_another_target_cannot_claim_provider_identity(self):
        outside=self.root/'wrong.jar'; outside.write_bytes(b'wrong')
        target={**self.target,'archive':str(outside),'sha256':launcher.digest(outside)}
        with self.assertRaisesRegex(ValueError, 'exact target'):
            launcher.production_archive(target)

    def test_preserved_archive_copy_keeps_exact_provider_identity_and_hash(self):
        copy=self.root/'preserved.jar'
        copy.write_bytes(Path(self.target['archive']).read_bytes())
        target={**self.target,'provider_archive':self.target['archive'],'archive':str(copy)}
        Path(self.target['archive']).unlink()
        self.assertEqual(launcher.production_archive(target),copy)
        copy.write_bytes(b'other build')
        with self.assertRaisesRegex(ValueError,'hash differs'):
            launcher.production_archive(target)

    def test_fixture_cannot_replace_production_classes(self):
        launcher.fixture_archive(self.fixture)
        self.make_fixture('com/mistaboom/essence_ascendance/audio/AudioEngine.class')
        with self.assertRaisesRegex(ValueError, 'Unapproved packaged fixture entry'):
            launcher.prepare_client(self.target,self.base/'EA_A',self.fixture)
        self.assertFalse(self.base.exists())

    def test_copy_dependencies_uses_exact_catalog_allowlist(self):
        (self.root/'gradle').mkdir()
        (self.root/'gradle/targets.json').write_text(json.dumps({'versions':{'1.21.1':{
            'architecturyApi':'13.0.11','fabricApi':'0.116.15+1.21.1'}}}))
        source=self.root/'staged-mods'; source.mkdir()
        destination=self.root/'copy'; destination.mkdir()
        names=['architectury-fabric-13.0.11.jar','fabric-api-0.116.15+1.21.1.jar']
        for name in names+['rogue-fixture.jar','essence_ascendance-stale.jar']:
            (source/name).write_bytes(name.encode())
        launcher.copy_dependencies(self.target,source,destination)
        self.assertEqual(sorted(p.name for p in destination.iterdir()),sorted(names))
        for name in names: self.assertEqual(launcher.digest(source/name),launcher.digest(destination/name))

    def test_optional_membership_uses_only_pinned_loader_specific_dependencies(self):
        (self.root/'gradle').mkdir()
        profile={'architecturyApi':'13.0.11','fabricApi':'0.116.15+1.21.1','ftbTeams':'2101.1.11','ftbLibrary':'2101.1.30'}
        (self.root/'gradle/targets.json').write_text(json.dumps({'versions':{'1.21.1':profile}}))
        source=self.root/'staged-mods'; source.mkdir(); destination=self.root/'copy'; destination.mkdir()
        for name in ('architectury-fabric-13.0.11.jar','fabric-api-0.116.15+1.21.1.jar'): (source/name).write_bytes(name.encode())
        modules=self.root/'modules'
        for name,version in (('teams',profile['ftbTeams']),('library',profile['ftbLibrary'])):
            for loader in ('fabric','neoforge'):
                module=f'ftb-{name}-{loader}'; path=modules/'dev.ftb.mods'/module/version/'hash'/f'{module}-{version}.jar'
                path.parent.mkdir(parents=True); path.write_bytes(module.encode())
        with patch.object(launcher,'MODULES',modules): launcher.copy_dependencies(self.target,source,destination,ftb=True)
        self.assertEqual({p.name for p in destination.glob('ftb*.jar')},{'ftb-teams-fabric-2101.1.11.jar','ftb-library-fabric-2101.1.30.jar'})
        module='ftb-teams-fabric'; alternate=modules/'dev.ftb.mods'/module/profile['ftbTeams']/'other'/f'{module}-{profile["ftbTeams"]}.jar'
        alternate.parent.mkdir(); alternate.write_bytes(b'conflicting cached bytes')
        with patch.object(launcher,'MODULES',modules),self.assertRaisesRegex(ValueError,'Conflicting cached bytes'):
            launcher.copy_dependencies(self.target,source,destination,ftb=True)

    def test_reconnect_arguments_are_bound_before_and_after_execution(self):
        game,_=self.prepared_evidence()
        reconnect=game/'reconnect-launch.args'
        reconnect.write_text('"-Dessence.audio.connectionGeneration=1"\n'+(game/'launch.args').read_text())
        before=launcher.evidence(self.base,self.target,self.fixture)
        self.assertIn(str(reconnect.resolve()),before)
        reconnect.write_text(reconnect.read_text().replace('Generation=1','Generation=0'))
        with self.assertRaisesRegex(ValueError,'changed during execution'):
            launcher.verify_inputs(before,launcher.evidence(self.base,self.target,self.fixture))

    def test_gameplay_requires_six_real_windows_per_process_and_actual_disconnect(self):
        self.base.mkdir(parents=True)
        for name in ('EA_A','EA_B','server'):
            token='AUDIO_NATIVE_SERVER_PERFORMANCE ' if name=='server' else 'AUDIO_NATIVE_PERFORMANCE '
            records=[]
            for index in range(6):
                record={'phase':str(index),'wallNanos':20_000_000_000,'clientTicks':400,'actualNativeSamples':20,'actualCaptionFrames':400,'observerMetricsRevision':2,'settings':{'actualGlfwVisible':0}}
                records.append(token+json.dumps(record))
            (self.base/(name+'.log')).write_text('\n'.join(records)+'\nAudioGameplaySession: SERVER PASS 1\n')
        (self.base/'EA_B-first.log').write_text('AudioGameplaySession: OBSERVER DISCONNECT PASS\n')
        for marker in ('launch-observer','EA_B.disconnected-0','reconnect-observer','reconnected-server'): (self.base/marker).write_text('verified native barrier\n')
        self.assertEqual(len(launcher.gameplay_evidence(self.base)['clients']['EA_A']),6)
        device='OpenAL Soft on Test Speakers'
        for name in ('EA_A','EA_B'):
            path=self.base/(name+'.log'); lines=[]
            for line in path.read_text().splitlines():
                if line.startswith('AUDIO_NATIVE_PERFORMANCE '):
                    record=json.loads(line.split(' ',1)[1]); record['settings'].update({'expectedSoundDevice':device,'actualSoundDevice':device})
                    record.update({'actualNativeDevicesDuringWindow':[device],'nativeDeviceStableDuringWindow':True,'soundDeviceChangesInWindow':0})
                    line='AUDIO_NATIVE_PERFORMANCE '+json.dumps(record)
                lines.append(line)
            path.write_text('\n'.join(lines)+'\n')
        launcher.gameplay_evidence(self.base,sound_device=device)
        with self.assertRaisesRegex(ValueError,'pinned stable device'): launcher.gameplay_evidence(self.base,sound_device='different device')
        server=self.base/'server.log'; server_original=server.read_text()
        server.write_text(server_original.replace('SERVER PASS','SERVER PERFORMANCE PASS'))
        performance=launcher.gameplay_evidence(self.base,performance_only=True)
        self.assertIn('performance only',performance['scope'])
        self.assertNotIn('reconnected-server',performance)
        with self.assertRaisesRegex(ValueError,'server final assertions missing'): launcher.gameplay_evidence(self.base)
        server.write_text(server_original)
        path=self.base/'EA_B.log'; original=path.read_text(); path.write_text(original.replace('20000000000','19000000000',1))
        with self.assertRaisesRegex(ValueError,'shorter than twenty seconds'): launcher.gameplay_evidence(self.base)
        path.write_text(original.split('\n',1)[1])
        with self.assertRaisesRegex(ValueError,'exactly six'): launcher.gameplay_evidence(self.base)

    def test_optional_device_is_local_json_quoted_and_bound_in_configuration(self):
        device='OpenAL Soft on Test "Device"'
        with patch.object(launcher,'copy_dependencies'),patch.object(launcher,'client_command',return_value=['native test command']) as command:
            launcher.prepare_client(self.target,self.base/'EA_A',self.fixture,sound_device=device)
        options=(self.base/'EA_A/options.txt').read_text()
        self.assertIn('soundDevice:'+json.dumps(device)+'\n',options)
        config=json.loads((self.base/'EA_A/configured-options.json').read_text())
        self.assertEqual(config['sound_device'],device)
        self.assertEqual(command.call_args.args[-1],device)

    def prepared_evidence(self):
        game=self.base/'EA_A'; (game/'mods').mkdir(parents=True)
        archive=Path(self.target['archive'])
        launcher.copy_verified(archive,game/'mods'/archive.name)
        launcher.copy_verified(self.fixture,game/'mods'/self.fixture.name)
        library=self.root/'cached.jar'; library.write_bytes(b'runtime library')
        (game/'launch.args').write_text('\n'.join(map(launcher.quote,['-cp',str(library),'Main'])))
        return game,library

    def test_evidence_rejects_development_classpath(self):
        game,_=self.prepared_evidence()
        classes=self.root/'classes'; classes.mkdir()
        (game/'launch.args').write_text('\n'.join(map(launcher.quote,['-cp',str(classes),'Main'])))
        with self.assertRaisesRegex(ValueError,'never development classes'):
            launcher.evidence(self.base,self.target,self.fixture)

    def test_installed_copy_and_runtime_input_change_cannot_pass(self):
        game,library=self.prepared_evidence()
        before=launcher.evidence(self.base,self.target,self.fixture)
        library.write_bytes(b'changed after launch')
        with self.assertRaisesRegex(ValueError,'changed during execution'):
            launcher.verify_inputs(before,launcher.evidence(self.base,self.target,self.fixture))
        (game/'mods'/Path(self.target['archive']).name).write_bytes(b'replaced mod')
        with self.assertRaisesRegex(ValueError,'Installed production archive differs'):
            launcher.evidence(self.base,self.target,self.fixture)

    def test_stale_completion_marker_cannot_pass_new_multiplayer_run(self):
        self.base.mkdir(parents=True)
        (self.base/'complete').write_text('old PASS')
        with self.assertRaisesRegex(ValueError,'fresh empty run directory'), patch.object(launcher.subprocess,'Popen') as launch:
            launcher.run_multiplayer(self.target,self.base,self.fixture)
        launch.assert_not_called()

    def test_cached_library_with_mismatched_manifest_hash_is_rejected(self):
        cache=self.root/'prism'; modules=self.root/'modules'
        relative=launcher.coordinate_path('test:library:1')
        cached=cache/'libraries'/relative; cached.parent.mkdir(parents=True); cached.write_bytes(b'wrong cached file')
        with patch.object(launcher,'PRISM',cache),patch.object(launcher,'MODULES',modules),self.assertRaises(FileNotFoundError):
            launcher.library({'name':'test:library:1','downloads':{'artifact':{'sha1':'0'*40}}})


if __name__=='__main__': unittest.main()
