"""Read-only audit of exact creatively approved takes; never author or promote."""
import argparse
import copy
import re
import sqlite3
import sys
from contextlib import closing
from pathlib import Path
from types import SimpleNamespace
import audio
import quality
from audacity import ensure_closed
from storage import REPO, digest, inside, read, write


def select(selection, manifest):
    if selection.get('schema') != 1 or not isinstance(selection.get('items'), list) or not selection['items']:
        raise ValueError('Expected schema 1 with a nonempty items list')
    selected, seen = [], set()
    for item in selection['items']:
        if not isinstance(item, dict) or item.get('purpose') not in {'runtime', 'demonstration'}:
            raise ValueError('Each selection needs a runtime or demonstration purpose')
        asset, revision = item.get('asset'), item.get('revision')
        if not isinstance(asset, str) or not re.fullmatch('[a-z0-9_]+', asset):
            raise ValueError('Invalid selected asset')
        if not isinstance(revision, str) or not re.fullmatch('r[0-9]{4,}', revision):
            raise ValueError('An exact revision is required')
        if (asset, revision) in seen:
            raise ValueError('Duplicate selected take')
        seen.add((asset, revision))
        if asset not in manifest['assets']:
            raise ValueError('Unknown selected asset: ' + asset)
        entry, record = audio.selected(manifest, SimpleNamespace(asset=asset, revision=revision))
        if record.get('creativeReview', {}).get('status') != 'accepted':
            raise ValueError(f'{asset} {revision} lacks creative acceptance')
        if record.get('design', {}).get('previewOnly') and item['purpose'] != 'demonstration':
            raise ValueError('A preview-only take cannot be selected for runtime')
        pinned = {'sourceExportSha256': record.get('exportSha256'),
                  'cueIds': record.get('cueIds', []),
                  'proposedCueIds': record.get('design', {}).get('proposedCueIds', [])}
        if any(key in item and item[key] != value for key, value in pinned.items()):
            raise ValueError('Selected export or cue association differs from exact take')
        selected.append((item, entry, record))
    return selected


def checked_file(root, relative, expected):
    if not isinstance(relative, str) or not relative:
        raise ValueError('Missing authoring file reference')
    if not isinstance(expected, str) or not re.fullmatch('[a-f0-9]{64}', expected):
        raise ValueError('Missing authoring file hash: ' + relative)
    path = inside(root, relative)
    if not path.is_file():
        raise ValueError('Missing authoring file: ' + str(path))
    actual = digest(path)
    if actual != expected:
        raise ValueError('Authoring file hash differs: ' + str(path))
    return {'path': relative, 'sha256': actual, 'bytes': path.stat().st_size}


def frozen_inputs(inputs, recipe, record, art):
    source = inputs.get('recipe', {})
    if not isinstance(source.get('content'), dict) or not re.fullmatch('[a-f0-9]{64}', source.get('sha256', '')):
        raise ValueError('Missing frozen source recipe or source hash')
    resolved = copy.deepcopy(source['content'])
    library = resolved.pop('presetLibrary', None)
    if library:
        inside(art, library)  # The frozen library is authoritative; current presets may have changed.
        presets = inputs.get('presets', {})
        if presets.get('path') != library or not re.fullmatch('[a-f0-9]{64}', presets.get('sha256', '')):
            raise ValueError('Missing frozen preset provenance')
        definitions = presets.get('content', {}).get('presets')
        if not isinstance(definitions, dict):
            raise ValueError('Missing frozen preset content')
        for index, layer in enumerate(resolved['layers']):
            layer = dict(layer)
            preset = layer.pop('preset', None)
            if preset and preset not in definitions:
                raise ValueError('Frozen layer references a missing preset')
            base = copy.deepcopy(definitions[preset]) if preset else {}
            base.update(layer)
            resolved['layers'][index] = base
    if resolved != recipe:
        raise ValueError('Frozen inputs do not resolve to the saved recipe')
    if record.get('sourceMethod', 'synthesis') == 'synthesis':
        seed = recipe.get('seed')
        if isinstance(seed, bool) or not isinstance(seed, int) or seed != record.get('provenance', {}).get('seed'):
            raise ValueError('Missing or inconsistent synthesis seed')


def project_integrity(path, expected):
    ensure_closed(path)
    if digest(path) != expected:
        raise ValueError('Closed project hash differs before inspection')
    with closing(sqlite3.connect(path.resolve().as_uri() + '?mode=ro&immutable=1', uri=True)) as database:
        if database.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
            raise ValueError('Audacity project SQLite integrity failure')
        sampleblocks = database.execute('SELECT count(*) FROM sampleblocks').fetchone()[0]
        if sampleblocks < 1:
            raise ValueError('Audacity project has no editable sample blocks')
    ensure_closed(path)
    if digest(path) != expected:
        raise ValueError('Project changed during immutable inspection')
    return {'integrity': 'ok', 'sampleblocks': sampleblocks, 'closedBeforeAndAfter': True,
            'unchanged': True, 'inspection': 'SQLite mode=ro&immutable=1; explicitly closed'}


def audit_take(item, entry, record, root, catalog, art):
    asset, revision = item['asset'], item['revision']
    backup, evidence = record.get('backup') or {}, record.get('projectEvidence') or {}
    if not backup.get('closed') or not evidence.get('tracks') or not record.get('stems'):
        raise ValueError(f'{asset} {revision} lacks closed layered-source evidence')
    files = {}
    for name, field, expected in [
            ('export', 'export', record.get('exportSha256')),
            ('master', 'master', record.get('masterSha256')),
            ('recipe', 'recipeSnapshot', record.get('recipeSha256')),
            ('inputs', 'authoringInputs', record.get('authoringInputsSha256')),
            ('preview', 'preview', record.get('exportSha256')),
            ('project', 'project', backup.get('projectSha256'))]:
        if not expected:
            raise ValueError('Missing source hash: ' + name)
        files[name] = checked_file(root, record.get(field), expected)
    files['backup'] = checked_file(root, backup.get('path'), backup.get('backupSha256'))
    files['projectRender'] = checked_file(root, evidence.get('render'), evidence.get('renderSha256'))
    for name in ('repeatPreview', 'auditionPreview'):
        if name in record:
            files[name] = checked_file(root, record[name], record.get(name + 'Sha256'))
    if files['backup']['sha256'] != files['project']['sha256']:
        raise ValueError('Backup is not identical to the closed project')
    stems = [checked_file(root, stem.get('path'), stem.get('sha256')) for stem in record['stems']]
    names = [Path(stem['path']).stem for stem in stems]
    if len(set(names)) != len(names) or names != [track.get('name') for track in evidence['tracks']]:
        raise ValueError('Project track organization differs from source stems')
    recipe, inputs = read(root / files['recipe']['path']), read(root / files['inputs']['path'])
    if recipe.get('assetId') != asset or recipe.get('sampleRate') != 48000:
        raise ValueError('Frozen recipe identity/rate differs')
    frozen_inputs(inputs, recipe, record, art)
    if record.get('cueIds') != recipe.get('design', {}).get('cueIds', []):
        raise ValueError('Cue association differs from frozen recipe')
    if any(cue not in catalog['cues'] for cue in record.get('cueIds', [])):
        raise ValueError('Unknown stable cue association')
    audio.verify_candidate(SimpleNamespace(root=root), record, entry['scope'])
    master = record['masterMeasurements']
    if (master['codec'], master['sampleRate'], master['channels']) != ('pcm_s24le', 48000, 1):
        raise ValueError('Expected a PCM24 48 kHz mono master')
    project = project_integrity(root / files['project']['path'], files['project']['sha256'])
    qc = quality.inspect(root / files['export']['path'], loop=record['loop'])
    if record['loop'] and (qc['decodedLoop']['repeats'] != 8 or qc['decodedLoop']['gapOrClickFlag']):
        raise ValueError('Repeated decoded-loop check failed')
    return {'asset': asset, 'revision': revision, 'purpose': item['purpose'], 'selection': item,
            'family': entry['family'], 'scope': entry['scope'], 'cueIds': record.get('cueIds', []),
            'proposedCueIds': record.get('design', {}).get('proposedCueIds', []),
            'runtimeIntended': item['purpose'] == 'runtime',
            'creativeReview': record['creativeReview'], 'finalReview': record['review'],
            'catalogStatus': catalog['assets'][asset]['status'], 'files': files, 'stems': stems,
            'measurements': record['measurements'], 'masterMeasurements': master, 'loop': record['loop'],
            'sourceMethod': record.get('sourceMethod', 'synthesis'), 'sourceInputs': inputs,
            'provenance': record['provenance'], 'encoder': record['encoder'],
            'projectAudit': project, 'projectEvidence': evidence, 'decodedQuality': qc,
            'proposedPromotion': {'master': f'source/audio/masters/{asset}/{revision}.wav',
                'approved': f'source/audio/approved/{asset}/{revision}.ogg',
                'recipe': f'source/audio/recipes/history/{asset}/{revision}.json',
                'receipt': f'source/audio/receipts/{asset}/{revision}.json'}}


def audit(selection_path, auxiliary, repo=REPO):
    selection_path, repo = Path(selection_path).resolve(), Path(repo).resolve()
    root, art = Path(auxiliary).resolve() / 'Audio', repo / 'art'
    manifest_path, catalog_path = art / 'audio-manifest.json', art / 'audio-catalog.json'
    hashes = {str(path): digest(path) for path in (selection_path, manifest_path, catalog_path)}
    selection, manifest, catalog = read(selection_path), read(manifest_path), read(catalog_path)
    items = [audit_take(i, e, r, root, catalog, art) for i, e, r in select(selection, manifest)]
    if any(digest(Path(path)) != expected for path, expected in hashes.items()):
        raise ValueError('Selection, manifest or catalog changed during audit; retry')
    return {'schema': 1, 'selectionId': selection.get('id'), 'selectionDate': selection.get('date'),
            'owner': 'Derived audit only; art/audio-manifest.json owns review',
            'repository': str(repo), 'auxiliaryAudio': str(root), 'sourceHashes': hashes,
            'auditTools': {name: digest(Path(__file__).parent / name) for name in
                ('audit.py', 'audio.py', 'quality.py', 'encoding.py', 'audacity.py', 'storage.py')},
            'totals': {'takes': len(items), 'stems': sum(len(i['stems']) for i in items),
                'loops': sum(i['loop'] for i in items),
                'runtime': sum(i['purpose'] == 'runtime' for i in items),
                'demonstration': sum(i['purpose'] == 'demonstration' for i in items)},
            'items': items, 'limitations': ['Numerical validation is not subjective listening or native mix approval.',
                'No synthesis, encoding, source editing, promotion or review changes occurred.']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--selection', required=True, type=Path)
    parser.add_argument('--auxiliary', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.suffix != '.json' or output.is_relative_to(args.auxiliary.resolve() / 'Audio'):
        raise ValueError('Write the derived JSON report outside Auxiliary Audio')
    protected = {args.selection.resolve(), REPO / 'art/audio-manifest.json', REPO / 'art/audio-catalog.json'}
    if output in protected or output.is_relative_to(REPO / 'art/source/audio'):
        raise ValueError('Audit output cannot replace selection or authoring inputs')
    result = audit(args.selection, args.auxiliary)
    write(output, result)
    print(f"Audited {result['totals']['takes']} exact takes; {output}")


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, RuntimeError, sqlite3.Error) as error:
        sys.exit(str(error))
