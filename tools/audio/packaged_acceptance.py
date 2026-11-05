"""Isolated production audio acceptance launcher (Windows, existing local caches).

This never edits Prism instances, downloads an account token, or adds development
classes to Minecraft. A manifest hash pins the exact production mod installed.
Runtime dependencies are read from the local Mojang/loader cache; all writable
game state belongs to the explicitly named build acceptance directory.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
import socket
from pathlib import Path
import shutil
import subprocess
import time
import tomllib
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
HOME = Path.home()
PRISM = HOME / 'AppData/Roaming/PrismLauncher'
LOOM = HOME / '.gradle/caches/fabric-loom'
MODULES = HOME / '.gradle/caches/modules-2/files-2.1'

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def offline_player_uuid(name):
    """Match Minecraft's UUID.nameUUIDFromBytes(UTF-8 'OfflinePlayer:' + name)."""
    return uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+name).encode('utf-8')).digest(),version=3)

def isolated_path(path):
    """Reject lexical escape and every existing redirected parent before writing."""
    path = Path(path).absolute()
    boundary = ROOT.resolve() / 'build/audio-packaged-acceptance'
    if '..' in path.parts or not path.is_relative_to(boundary) or not path.resolve().is_relative_to(boundary):
        raise ValueError('Acceptance state must remain inside build/audio-packaged-acceptance')
    for parent in (path, *path.parents):
        if parent == ROOT.resolve(): break
        if parent.is_symlink() or getattr(parent, 'is_junction', lambda: False)():
            raise ValueError('Acceptance state cannot use redirected directories: ' + str(parent))
    return path

def production_archive(target):
    source = Path(target['archive']).resolve()
    provider = Path(target.get('provider_archive', target['archive'])).resolve()
    expected = ROOT.resolve() / target['loader'] / 'versions' / target['minecraft'] / 'build/libs'
    if provider.parent != expected or provider.suffix != '.jar' or source.suffix != '.jar':
        raise ValueError('Production archive is outside its exact target build/libs')
    if digest(source) != target['sha256']:
        raise ValueError('Production archive hash differs from provider manifest')
    return source

def fixture_archive(fixture):
    """The external test mod may observe production code, never replace it."""
    fixture = Path(fixture).resolve()
    allowed_metadata = {'META-INF/MANIFEST.MF', 'fabric.mod.json', 'META-INF/neoforge.mods.toml', 'audio-fixture.mixins.json'}
    with zipfile.ZipFile(fixture) as jar:
        names = [entry.filename for entry in jar.infolist() if not entry.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError('Duplicate entries in packaged fixture')
        for name in names:
            permitted = (name in allowed_metadata
                or re.fullmatch(r'com/mistaboom/essence_ascendance/audiofixture/[A-Za-z0-9_$]+\.class', name)
                or name in {'com/mistaboom/essence_ascendance/audiofixture/mixin/'+kind+'.class' for kind in
                            ('AudioCaptionFrameMixin','AudioHiddenWindowMixin','AudioRuntimePerformanceMixin','AudioSoundEnginePerformanceMixin','AudioSoundBufferPerformanceMixin','AudioNetworkPerformanceMixin','AudioPacketEncoderPerformanceMixin')}
                or name == 'com/mistaboom/essence_ascendance/verification/BlockEntityTestNbtCompat.class')
            if not permitted:
                raise ValueError('Unapproved packaged fixture entry: ' + name)
        if 'com/mistaboom/essence_ascendance/audiofixture/AudioLoaderTest.class' not in names:
            raise ValueError('Packaged fixture is missing its test entrypoint')
    return fixture

def copy_verified(source, destination):
    expected = digest(source)
    shutil.copy2(source, destination)
    if digest(destination) != expected:
        raise ValueError('Copied acceptance input differs: ' + str(destination))

def verify_inputs(before, after):
    changed = [path for path, expected in before.items() if after.get(path) != expected]
    if changed:
        raise ValueError('Acceptance inputs changed during execution: ' + ', '.join(changed))

def coordinate_path(name):
    name, _, extension = name.partition('@')
    extension = extension or 'jar'
    group, artifact, version, *classifier = name.split(':')
    suffix = '-' + classifier[0] if classifier else ''
    return Path(group.replace('.', '/')) / artifact / version / f'{artifact}-{version}{suffix}.{extension}'

def library(entry):
    name = entry['name']
    relative = coordinate_path(name)
    artifact = entry.get('downloads', {}).get('artifact', {})
    if artifact.get('path'): relative = Path(artifact['path'])
    group, module, version, *_ = name.split('@')[0].split(':')
    candidates = [PRISM / 'libraries' / relative]
    candidates.extend(ROOT / 'build' / server / 'libraries' / relative for server in ('packaged-neo1211-server','packaged-neo2612-server'))
    candidates.extend((MODULES / group / module / version).glob('*/' + relative.name))
    for path in candidates:
        if path.is_file():
            expected = artifact.get('sha1')
            if expected and hashlib.sha1(path.read_bytes()).hexdigest() != expected:
                continue
            return path.resolve()
    raise FileNotFoundError(f'Missing exact cached dependency {name}: {relative}')

def allowed(entry):
    rules = entry.get('rules')
    if not rules: return True
    result = False
    for rule in rules:
        os_rule = rule.get('os', {})
        if os_rule.get('name', 'windows') not in ('windows',): continue
        if os_rule.get('arch', 'x86_64') not in ('x86_64', 'amd64'): continue
        result = rule['action'] == 'allow'
    return result and not any(x in entry['name'] for x in ('natives-windows-arm64', 'natives-windows-x86'))

def java(major):
    candidates = ([Path('C:/Program Files/Microsoft/jdk-21.0.12.8-hotspot/bin/java.exe')] if major == 21 else
                  [HOME / '.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe'])
    return next(p for p in candidates if p.is_file())

def quote(value):
    return '"' + str(value).replace('\\', '/').replace('"', '\\"') + '"'

def client_command(target, game, fixture=None, multiplayer=None, world=False, resource_recovery=False, gameplay=False, hidden=False, membership='native', performance_only=False, sound_device=None):
    version = target['minecraft']
    profile = json.loads((ROOT / 'gradle/targets.json').read_text())['versions'][version]
    info = json.loads((LOOM / version / 'mojang_minecraft_info.json').read_text())
    libraries = [library(e) for e in info['libraries'] if allowed(e)]
    official = LOOM / version / 'minecraft-client.jar'
    if hashlib.sha1(official.read_bytes()).hexdigest() != info['downloads']['client']['sha1']:
        raise ValueError('Cached Minecraft client differs from its official manifest hash')
    libraries.append(official)
    main='net.fabricmc.loader.impl.launch.knot.KnotClient'
    extra_jvm=[]
    extra_game=[]
    if target['loader']=='fabric':
        loader = library({'name':f"net.fabricmc:fabric-loader:{profile['fabricLoader']}"})
        with zipfile.ZipFile(loader) as jar:
            installer = json.loads(jar.read('fabric-installer.json'))
        libraries.extend(library(e) for group in ('common','client') for e in installer['libraries'].get(group, []))
        libraries.append(loader)
        if version == '1.21.1':
            libraries.append(library({'name': 'net.fabricmc:intermediary:1.21.1'}))
    else:
        installer=library({'name':f"net.neoforged:neoforge:{profile['neoforge']}:installer"})
        with zipfile.ZipFile(installer) as archive:
            meta=json.loads(archive.read('version.json'))
            install=json.loads(archive.read('install_profile.json'))
        local_lib=game/'libraries'
        for entry in install['libraries']+meta['libraries']:
            if not allowed(entry): continue
            src=library(entry); dst=local_lib/coordinate_path(entry['name'])
            dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(src,dst)
        libraries.extend(local_lib/coordinate_path(e['name']) for e in meta['libraries'] if allowed(e))
        libraries.append(library({'name':'io.github.zekerzhayard:ForgeWrapper:prism-2026-08-01'}))
        main='io.github.zekerzhayard.forgewrapper.installer.Main'
        extra_jvm=['-Dforgewrapper.librariesDir='+str(local_lib),'-Dforgewrapper.minecraft='+str(official),
                   '-Dforgewrapper.installer='+str(installer)]
        extra_game=meta['arguments']['game']
    args = ['-Xmx2G', '-XX:ActiveProcessorCount=4', '--enable-native-access=ALL-UNNAMED',
            '-Dfabric.development=false', '-Dfabric.defaultModDistributionNamespace=' + ('official' if version != '1.21.1' else 'intermediary'),
            '-Djava.library.path=' + str(game / 'natives'), '-Dorg.lwjgl.system.SharedLibraryExtractPath=' + str(game / 'natives'),
            *extra_jvm, '-cp', ';'.join(map(str,dict.fromkeys(libraries))), main,
            '--username', game.name, '--uuid', offline_player_uuid(game.name).hex,
            '--accessToken', '0', '--version', version, '--versionType', 'release', '--gameDir', str(game),
            '--assetsDir', str(LOOM / 'assets'), '--assetIndex', version + '-' + info['assetIndex']['id'], '--width','854','--height','480']
    args+=extra_game
    if multiplayer: args += ['--quickPlayMultiplayer',multiplayer]
    if fixture:
        args.insert(0, '-Dessence.audio.'+('worldAcceptance' if world else 'multiplayer')+'=true')
        args.insert(0, '-Dessence.audio.fixtureClient=true')
        args.insert(0, '-Dessence.audio.acceptanceDirectory=' + str(game.parent))
        if version=='1.21.1':
            mapping=LOOM/version/'loom.mappings.1_21_1.layered+hash.40359-v2/mappings.tiny'
            if not mapping.is_file(): raise FileNotFoundError('Exact 1.21.1 fixture mappings unavailable')
            args.insert(0,'-Dessence.audio.fixtureMappings='+str(mapping))
    if resource_recovery: args.insert(0,'-Dessence.audio.resourceAcceptance=true')
    if gameplay:
        args[:0]=['-Dessence.audio.gameplay=true','-Dessence.audio.membership='+membership]
    if performance_only: args.insert(0,'-Dessence.audio.performanceOnly=true')
    if sound_device is not None: args.insert(0,'-Dessence.audio.expectedSoundDevice='+sound_device)
    if hidden: args.insert(0,'-Dessence.audio.hiddenWindow=true')
    argfile = game / 'launch.args'
    argfile.write_text('\n'.join(map(quote,args)), encoding='utf-8')
    return [str(java(target['java'])), '@' + str(argfile)]

def prepare_client(target, directory, fixture=None, multiplayer=None, world=False, resource_recovery=False, gameplay=False, hidden=False, membership='native', performance_only=False, sound_device=None):
    directory = isolated_path(directory)
    source = production_archive(target)
    if fixture: fixture = fixture_archive(fixture)
    directory.mkdir(parents=True,exist_ok=False)
    snapshot=isolated_path(directory.parent/'acceptance-launcher.py')
    if not snapshot.exists(): copy_verified(Path(__file__),snapshot)
    elif digest(snapshot)!=digest(__file__): raise ValueError('Launcher recipe changed while preparing clients')
    (directory/'mods').mkdir()
    (directory/'natives').mkdir()
    copy_verified(source,directory/'mods'/source.name)
    folder = f"packaged-{'fabric' if target['loader']=='fabric' else 'neo'}{'1211' if target['minecraft']=='1.21.1' else '2612'}-server"
    copy_dependencies(target,ROOT/'build'/folder/'mods',directory/'mods',membership=='ftb')
    if fixture: copy_verified(fixture,directory/'mods'/fixture.name)
    (directory/'options.txt').write_text('onboardAccessibility:false\ntutorialStep:none\npauseOnLostFocus:false\nmaxFps:30\nrenderDistance:3\nsimulationDistance:5\nsoundCategory_music:0.0\nshowSubtitles:true\n',encoding='utf-8')
    if sound_device is not None:
        with (directory/'options.txt').open('a',encoding='utf-8') as options: options.write('soundDevice:'+json.dumps(sound_device)+'\n')
    if hidden and target['loader']=='neoforge':
        (directory/'config').mkdir()
        (directory/'config/fml.toml').write_text('earlyWindowControl = false\n',encoding='utf-8')
    (directory/'configured-options.json').write_text(json.dumps({'hidden_window':hidden,'early_window_control':False if hidden else None,'membership':membership,'sound_device':sound_device},indent=2)+'\n',encoding='utf-8')
    if world: shutil.copytree(ROOT/'build'/folder/'world',directory/'saves/world')
    command = client_command(target,directory,fixture,multiplayer,world,resource_recovery,gameplay,hidden,membership,performance_only,sound_device)
    (directory/'production-input.json').write_text(json.dumps(target,indent=2)+'\n',encoding='utf-8')
    return command

def copy_dependencies(target,source,destination,ftb=False):
    profile=json.loads((ROOT/'gradle/targets.json').read_text())['versions'][target['minecraft']]
    expected=[f"architectury-{target['loader']}-{profile['architecturyApi']}.jar"]
    if target['loader']=='fabric': expected.append(f"fabric-api-{profile['fabricApi']}.jar")
    for name in expected:
        dependency=source/name
        if not dependency.is_file(): raise FileNotFoundError('Missing exact required runtime mod '+str(dependency))
        copy_verified(dependency,destination/name)
    if ftb:
        for name in ('teams','library'):
            version=profile['ftb'+name.title()]
            module=f"ftb-{name}-{target['loader']}"
            candidates=list((MODULES/'dev.ftb.mods'/module/version).glob('*/'+module+'-'+version+'.jar'))
            if not candidates: raise FileNotFoundError('Missing catalog-pinned optional dependency '+module+':'+version)
            if len({digest(p) for p in candidates})!=1: raise ValueError('Conflicting cached bytes for '+module+':'+version)
            copy_verified(candidates[0],destination/candidates[0].name)

def evidence(base,target,fixture):
    base=isolated_path(base)
    archive=production_archive(target)
    inputs={str(archive):digest(archive),str(Path(__file__).resolve()):digest(__file__)}
    snapshot=base/'acceptance-launcher.py'
    if snapshot.is_file(): inputs[str(snapshot.resolve())]=digest(snapshot)
    executable=java(target['java']).resolve()
    inputs[str(executable)]=digest(executable)
    release=executable.parent.parent/'release'
    if release.is_file(): inputs[str(release)]=digest(release)
    if fixture:
        fixture=fixture_archive(fixture)
        inputs[str(fixture)]=digest(fixture)
    for argfile in base.glob('*/*launch.args'):
        lines=[line.strip('"').replace('\\"','"') for line in argfile.read_text().splitlines()]
        cp=lines[lines.index('-cp')+1].split(';')
        if any(Path(path).suffix!='.jar' or not Path(path).is_file() for path in cp):
            raise ValueError('Client classpath must contain cached runtime jars only, never development classes')
        for path in cp:
            p=Path(path); inputs[str(p.resolve())]=digest(p)
        for line in lines:
            if line.startswith('-Dessence.audio.fixtureMappings='):
                path=Path(line.split('=',1)[1]); inputs[str(path.resolve())]=digest(path)
    for mods in base.glob('*/mods'):
        for path in mods.glob('*.jar'): inputs[str(path.resolve())]=digest(path)
        installed=mods/archive.name
        if not installed.is_file() or digest(installed)!=target['sha256']:
            raise ValueError('Installed production archive differs from provider manifest')
        if fixture and digest(mods/fixture.name)!=digest(fixture):
            raise ValueError('Installed fixture differs from inspected test archive')
    for pattern in ('*/*launch.args','*/configured-options.json','server/configured-runtime.json'):
        for path in base.glob(pattern): inputs[str(path.resolve())]=digest(path)
    for settings in base.glob('*/configured-options.json'):
        if json.loads(settings.read_text())['hidden_window'] and target['loader']=='neoforge':
            if tomllib.loads((settings.parent/'config/fml.toml').read_text())['earlyWindowControl'] is not False:
                raise ValueError('Native hidden acceptance must disable the NeoForge early window')
    # Server launch args and local runtime libraries are independent of client classpaths.
    for pattern in ('server/libraries/**/*.jar','server/libraries/**/win_args.txt','server/versions/**/*.jar',
                    'server/*.jar','server/fabric-server-launcher.properties'):
        for path in base.glob(pattern): inputs[str(path.resolve())]=digest(path)
    return inputs

def runtime_info(target):
    executable=java(target['java']).resolve()
    release=executable.parent.parent/'release'
    metadata={}
    if release.is_file():
        for line in release.read_text(encoding='utf-8').splitlines():
            if '=' in line:
                key,value=line.split('=',1)
                if key in ('JAVA_VERSION','IMPLEMENTOR','OS_ARCH'): metadata[key]=value.strip('"')
    return {'executable':str(executable),'sha256':digest(executable),**metadata}

def gameplay_evidence(base,performance_only=False,sound_device=None):
    """Reject partial native sessions and retain measured observations, not just a completion barrier."""
    result={'clients':{},'server':[]}
    for name in ('EA_A','EA_B','server'):
        contents=(base/(name+'.log')).read_text(encoding='utf-8',errors='replace')
        token='AUDIO_NATIVE_SERVER_PERFORMANCE ' if name=='server' else 'AUDIO_NATIVE_PERFORMANCE '
        records=[json.loads(line.split(token,1)[1]) for line in contents.splitlines() if token in line]
        if len(records)!=6 or len({record['phase'] for record in records})!=6:
            raise ValueError(name+' must provide exactly six distinct native performance windows')
        if any(record['wallNanos']<20_000_000_000 for record in records):
            raise ValueError(name+' native performance window shorter than twenty seconds')
        if any(record.get('observerMetricsRevision',0)<2 for record in records):
            raise ValueError(name+' payload classification must use the actual type identifier (metrics revision2)')
        if name=='server':
            if ('AudioGameplaySession: SERVER PERFORMANCE PASS ' if performance_only else 'AudioGameplaySession: SERVER PASS ') not in contents:
                raise ValueError('Native gameplay server final assertions missing')
            result['server']=records
        else:
            for record in records:
                if record['clientTicks']<400 or record['actualNativeSamples']<10 or record['actualCaptionFrames']<1:
                    raise ValueError(name+' incomplete real tick/frame/channel observations')
                if record['settings']['actualGlfwVisible']!=0:
                    raise ValueError(name+' native gameplay window was visible')
                if sound_device is not None and (record['settings'].get('actualSoundDevice')!=sound_device
                        or record['settings'].get('expectedSoundDevice')!=sound_device or record.get('nativeDeviceStableDuringWindow') is not True
                        or record.get('actualNativeDevicesDuringWindow')!=[sound_device] or record.get('soundDeviceChangesInWindow')!=0):
                    raise ValueError(name+' native sound device differs from the pinned stable device')
            result['clients'][name]=records
    if performance_only:
        result['scope']='Actual loaded/paid/stopped native performance only; no membership transition, late connection or reconnect claim'
        return result
    first=(base/'EA_B-first.log').read_text(encoding='utf-8',errors='replace')
    if 'AudioGameplaySession: OBSERVER DISCONNECT PASS' not in first:
        raise ValueError('Actual first observer disconnect cleanup missing')
    for marker in ('launch-observer','EA_B.disconnected-0','reconnect-observer','reconnected-server'):
        path=base/marker
        if not path.is_file() or not path.read_text().strip(): raise ValueError('Missing true connection barrier '+marker)
        result[marker]=path.read_text().strip()
    return result

def run_multiplayer(target, base, fixture, gameplay=False, hidden=False, membership='native', performance_only=False, profile_server=False, sound_device=None):
    """A dedicated process plus two real rendered clients with distinct offline UUIDs."""
    base=isolated_path(base)
    fixture=fixture_archive(fixture)
    archive=production_archive(target)
    if any(base.iterdir()): raise ValueError('Use a fresh empty run directory; stale barriers cannot establish acceptance')
    suffix='1211' if target['minecraft']=='1.21.1' else '2612'
    loader='fabric' if target['loader']=='fabric' else 'neo'
    source=ROOT/f'build/packaged-{loader}{suffix}-server'
    server=base/'server'; server.mkdir()
    for name in (('libraries',) if gameplay else ('libraries','world')):
        shutil.copytree(source/name,server/name)
    for name in ('fabric-server-launch.jar','fabric-server-launcher.properties','server.jar','versions','.fabric'):
        path=source/name
        if path.is_dir(): shutil.copytree(path,server/name)
        elif path.is_file(): shutil.copy2(path,server/name)
    (server/'mods').mkdir()
    copy_dependencies(target,source/'mods',server/'mods',membership=='ftb')
    copy_verified(archive,server/'mods'/archive.name); copy_verified(fixture,server/'mods'/fixture.name)
    with socket.socket() as reservation:
        reservation.bind(('127.0.0.1',0)); port=reservation.getsockname()[1]
    (server/'eula.txt').write_text('eula=true\n')
    (server/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\nmax-tick-time=120000\n')
    if gameplay:
        flat={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'lakes':False,'features':False}
        with (server/'server.properties').open('a') as settings: settings.write('level-type=minecraft:flat\nlevel-seed=0\ngenerator-settings='+json.dumps(flat,separators=(',',':'))+'\n')
    profile=json.loads((ROOT/'gradle/targets.json').read_text())['versions'][target['minecraft']]
    jvm=['-Xmx2G','-XX:ActiveProcessorCount=4','-Dessence.audio.multiplayer=true','-Dessence.audio.fixtureClient=false','-Dessence.audio.acceptanceDirectory='+str(base)]
    if gameplay: jvm+=['-Dessence.audio.gameplay=true','-Dessence.audio.membership='+membership,'-Dessence.membershipAuthority='+('ftb_teams' if membership=='ftb' else 'native')]
    if performance_only: jvm+=['-Dessence.audio.performanceOnly=true']
    if profile_server: jvm+=['-XX:StartFlightRecording=filename='+str(server/'diagnostic.jfr')+',settings=profile,dumponexit=true,maxsize=256m']
    launch=['-jar','fabric-server-launch.jar','nogui'] if loader=='fabric' else [f"@libraries/net/neoforged/neoforge/{profile['neoforge']}/win_args.txt",'nogui']
    (server/'configured-runtime.json').write_text(json.dumps({'jvm_arguments':jvm,'launch_arguments':launch,'initial_server_properties':(server/'server.properties').read_text()},indent=2)+'\n',encoding='utf-8')
    commands={name:prepare_client(target,base/name,fixture,f'127.0.0.1:{port}',gameplay=gameplay,hidden=hidden,membership=membership,performance_only=performance_only,sound_device=sound_device) for name in ('EA_A','EA_B')}
    reconnect=base/'EA_B/reconnect-launch.args'
    if gameplay and not performance_only: reconnect.write_text(quote('-Dessence.audio.connectionGeneration=1')+'\n'+(base/'EA_B/launch.args').read_text(),encoding='utf-8')
    before=evidence(base,target,fixture)
    (base/'inputs-before.json').write_text(json.dumps(before,indent=2)+'\n',encoding='utf-8')
    processes=[]; logs=[]; failure=None; observed_gameplay=None
    try:
        log=(base/'server.log').open('w',encoding='utf-8'); logs.append(log)
        host=subprocess.Popen([str(java(target['java'])),*jvm,*launch],cwd=server,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
        processes.append(host); deadline=time.monotonic()+180
        while 'Done (' not in (base/'server.log').read_text(encoding='utf-8',errors='replace'):
            if host.poll() is not None: raise RuntimeError('Dedicated server exited before readiness')
            if time.monotonic()>deadline: raise TimeoutError('Dedicated server readiness')
            time.sleep(.5)
        def spawn(name,log_name=None,command=None):
            log=(base/(log_name or name+'.log')).open('w',encoding='utf-8'); logs.append(log)
            process=subprocess.Popen(command or commands[name],cwd=base/name,stdout=log,stderr=subprocess.STDOUT)
            processes.append(process); return process
        def wait_barrier(name,finished=()):
            deadline=time.monotonic()+300
            while not (base/name).exists():
                for process in processes:
                    if process.poll() is not None and process not in finished: raise RuntimeError('Native process exited before '+name)
                if time.monotonic()>deadline: raise TimeoutError('Native barrier '+name)
                time.sleep(.25)
        finished=[]
        spawn('EA_A')
        if gameplay and not performance_only:
            wait_barrier('launch-observer')
            observer=spawn('EA_B','EA_B-first.log')
            wait_barrier('reconnect-observer',(observer,))
            if observer.wait(timeout=45)!=0 or 'AudioGameplaySession: OBSERVER DISCONNECT PASS' not in (base/'EA_B-first.log').read_text(encoding='utf-8',errors='replace'):
                raise RuntimeError('First observer did not prove native disconnect cleanup')
            finished.append(observer)
            spawn('EA_B',command=[str(java(target['java'])),'@'+str(reconnect)])
        else: spawn('EA_B')
        deadline=time.monotonic()+(1800 if gameplay else 250)
        while not (base/'complete').exists():
            if any(p.poll() is not None for p in processes if p not in finished): raise RuntimeError('A process exited before acceptance completed')
            if time.monotonic()>deadline: raise TimeoutError('Socket acceptance')
            time.sleep(.5)
        for client in processes[1:]:
            if client.wait(timeout=45)!=0: raise RuntimeError('Client failed acceptance cleanup')
        for name in ('EA_A','EA_B'):
            contents=(base/(name+'.log')).read_text(encoding='utf-8',errors='replace')
            markers=(('AudioGameplaySession: CLIENT PERFORMANCE PASS' if performance_only else 'AudioGameplaySession: CLIENT PASS'),'AUDIO_NATIVE_PERFORMANCE','Sound engine started') if gameplay else ('AudioMultiplayerAcceptance: CLIENT PASS','AUDIO_CONTROLLER_MICROBENCHMARK','Sound engine started','actual HUD frame order PASS',
                           'real paid CLIENT observation 7-paid-start','real paid CLIENT observation 8-paid-late-observer',
                           'real paid CLIENT observation 9-paid-stop','real paid CLIENT observation 10-paid-restart','real paid CLIENT observation 11-paid-remove')
            if hidden: markers+=('AudioHiddenWindow: PASS',)
            for marker in markers:
                if marker not in contents: raise RuntimeError(name+' missing required evidence: '+marker)
        if gameplay: observed_gameplay=gameplay_evidence(base,performance_only,sound_device)
        host.stdin.write('stop\n'); host.stdin.flush()
        if host.wait(timeout=60)!=0: raise RuntimeError('Server failed graceful shutdown')
    except Exception as exc:
        failure=str(exc)
    finally:
        for process in processes:
            if process.poll() is None:
                if process is processes[0]:
                    try: process.stdin.write('stop\n'); process.stdin.flush(); process.wait(timeout=20)
                    except Exception: process.kill()
                else: process.kill()
        for log in logs: log.close()
    after={}
    try:
        after=evidence(base,target,fixture); verify_inputs(before,after)
    except Exception as exc:
        failure=(failure+'; ' if failure else '')+str(exc)
    receipt={'target':target,'fixture_sha256':digest(fixture),'result':'FAIL' if failure else 'PASS','failure':failure,
             'process_exit':[p.wait() for p in processes], 'mode':'production loader; exact packaged mod; dedicated loopback server; two socket clients',
             'logs':{p.name:digest(p) for p in base.glob('*.log')},'limitations':['Paid Crucible covers native placement/binding, real reservoir grant, late chunk observer, stop/restart/removal; paid Infuser and socket reconnect pending','No human listening or HUD layout approval','Performance microbenchmark is controlled controller work only']}
    receipt['scenario']='native-performance-only' if performance_only else 'native-gameplay' if gameplay else 'baseline-socket'
    receipt['diagnostic_profiled']=profile_server
    receipt['sound_device']=sound_device
    receipt['membership']=membership; receipt['hidden_windows']=hidden
    if gameplay:
        receipt['mode']='exact packaged dedicated server; native client gameplay; real late connection and reconnect; native CPU/allocation/network/audio observations'
        receipt['limitations']=['No human listening, perceived seam/mix or visual HUD approval','Same host loopback transport; no latency/loss/long-soak or broader hardware coverage']
        receipt['native_gameplay_evidence']=observed_gameplay
        if performance_only:
            receipt['mode']='exact packaged dedicated server; native loaded/paid/stopped performance only'
            receipt['limitations'].append('No gameplay, membership transition, late connection or reconnect acceptance in performance-only mode')
    if profile_server:
        receipt['limitations'].append('Server JFR profiling enabled: diagnostic attribution run, not unprofiled baseline timing acceptance')
        path=server/'diagnostic.jfr'
        receipt['diagnostic_artifacts']={str(path):digest(path)} if path.is_file() else {}
    receipt['inputs']=before
    receipt['inputs_after']=after
    receipt['java_runtime']=runtime_info(target)
    (base/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt),flush=True)
    return 1 if failure else 0

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target',required=True)
    parser.add_argument('--label',required=True)
    parser.add_argument('--fixture',type=Path)
    parser.add_argument('--archive-copy',type=Path,help='Existing exact provider archive copy; SHA-256 must match the selected provider manifest')
    parser.add_argument('--server')
    parser.add_argument('--name',default='EA_A')
    parser.add_argument('--run',action='store_true')
    parser.add_argument('--multiplayer',action='store_true')
    parser.add_argument('--gameplay',action='store_true',help='Actual native gameplay, late connection/reconnect and performance acceptance')
    parser.add_argument('--performance-only',action='store_true',help='Reuse the real paid-source native measurements without gameplay/membership/reconnect phases')
    parser.add_argument('--profile-server',action='store_true',help='Diagnostic JFR server attribution; timings are explicitly marked profiled')
    parser.add_argument('--sound-device',help='Optional exact native OpenAL device for controlled test windows; does not change system/default human settings')
    parser.add_argument('--hidden',action='store_true',help='Create native rendering contexts without desktop windows; requires the external fixture')
    parser.add_argument('--membership',choices=('native','ftb'),default='native')
    parser.add_argument('--world',action='store_true')
    parser.add_argument('--resource-recovery',action='store_true',help='World fixture also checks isolated missing/corrupt resource pack recovery')
    args=parser.parse_args()
    if args.performance_only: args.gameplay=True
    if not args.label.replace('-','').isalnum(): parser.error('Use an alphanumeric run label')
    if args.name not in ('EA_A','EA_B'): parser.error('--name must be EA_A or EA_B')
    if args.resource_recovery and (not args.world or not args.fixture or args.multiplayer): parser.error('--resource-recovery requires --world and --fixture')
    if args.hidden and not args.fixture: parser.error('--hidden requires --fixture')
    if args.gameplay and (not args.fixture or args.world): parser.error('--gameplay requires --fixture and cannot be combined with --world')
    if args.profile_server and not args.gameplay: parser.error('--profile-server requires --gameplay or --performance-only')
    target=next(t for t in json.loads((ROOT/'build/production-artifacts-all.json').read_text())['targets'] if t['target']==args.target)
    if args.archive_copy:
        target={**target,'provider_archive':target['archive'],'archive':str(args.archive_copy.resolve())}
    production_archive(target)
    base=isolated_path(ROOT/'build/audio-packaged-acceptance'/args.label)
    base.mkdir(parents=True,exist_ok=False)
    if args.multiplayer or args.gameplay:
        if not args.fixture: parser.error('--multiplayer requires --fixture')
        return run_multiplayer(target,base,args.fixture.resolve(),args.gameplay,args.hidden or args.gameplay,args.membership,args.performance_only,args.profile_server,args.sound_device)
    command=prepare_client(target,base/args.name,args.fixture,args.server,args.world,args.resource_recovery,hidden=args.hidden,membership=args.membership,sound_device=args.sound_device)
    print(json.dumps({'directory':str(base/args.name),'command':command}))
    if args.run:
        before=evidence(base,target,args.fixture)
        (base/'inputs-before.json').write_text(json.dumps(before,indent=2)+'\n',encoding='utf-8')
        failure=None; result=None
        with (base/(args.name+'.log')).open('w',encoding='utf-8') as log:
            try: result=subprocess.run(command,cwd=base/args.name,stdout=log,stderr=subprocess.STDOUT,timeout=300)
            except subprocess.TimeoutExpired: failure='Client acceptance timed out'
        contents=(base/(args.name+'.log')).read_text(encoding='utf-8',errors='replace')
        after={}
        try:
            after=evidence(base,target,args.fixture); verify_inputs(before,after)
        except Exception as exc:
            failure=(failure+'; ' if failure else '')+str(exc)
        passed=failure is None and result is not None and result.returncode==0 and (
            'AudioWorldAcceptance: PASS' in contents and 'audio device loaded=true' in contents if args.world else False)
        if args.resource_recovery: passed=passed and 'AudioResourceAcceptance: PASS' in contents
        if args.hidden: passed=passed and 'AudioHiddenWindow: PASS' in contents
        receipt={'target':target,'result':'PASS' if passed else 'FAIL','failure':failure,'process_exit':None if result is None else result.returncode,
                 'mode':'exact packaged joined world with isolated resource recovery' if args.resource_recovery else 'exact packaged joined world' if args.world else 'title-screen smoke only', 'inputs':before,'inputs_after':after,
                 'java_runtime':runtime_info(target),
                 'logs':{p.name:digest(p) for p in base.glob('*.log')},'limitations':['Controlled fixture snapshots are not paid transfer gameplay','No human listening or HUD layout approval']}
        receipt['hidden_windows']=args.hidden; receipt['membership']=args.membership
        receipt['sound_device']=args.sound_device
        (base/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
        print(json.dumps(receipt),flush=True)
        return 0 if passed else 1
    return 0

if __name__=='__main__': raise SystemExit(main())
