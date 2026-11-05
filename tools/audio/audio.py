"""Reusable authoring CLI. See docs/audio/AUTHORING.md for storage and review."""
import argparse
import shutil
import sys
from pathlib import Path
from storage import REPO, Storage, digest, inside, lease, read, write

def author(args):
    import encoding
    import synthesis
    manifest_path = REPO / 'art/audio-manifest.json'
    store = Storage(args.auxiliary)
    with lease(store.root), lease(REPO / 'art/source/audio'):
        manifest = read(manifest_path)
        asset = manifest['assets'][args.asset]
        recipe_path = inside(REPO / 'art', asset['recipe'])
        from recipes import resolve
        recipe, inputs = resolve(recipe_path, REPO / 'art')
        if recipe.get('assetId') != args.asset:
            raise ValueError('Manifest/recipe asset identity mismatch')
        revision = store.revision(asset['family'], args.asset)
        master_dir = store.path('masters', asset['family'], args.asset, revision)
        project_dir = store.path('projects', asset['family'], args.asset, revision)
        export_dir = store.path('exports', asset['family'], args.asset, revision)
        project_dir.mkdir(parents=True)
        export_dir.mkdir(parents=True)
        master = master_dir / 'master.wav'
        project = project_dir / (args.asset + '.aup3')
        backup_info = None
        write(master_dir / 'recipe.json', recipe)
        write(master_dir / 'authoring-inputs.json', inputs)
        # Preserve executable authoring sources, not just version labels/hashes.
        for name in ('audio.py','batch.py','player.py','synthesis.py','recipes.py','quality.py','encoding.py','audacity.py','storage.py'):
            source = Path(__file__).parent / name
            archive = store.root / 'references/tools' / digest(source) / name
            archive.parent.mkdir(parents=True, exist_ok=True)
            if not archive.exists():
                shutil.copy2(source, archive)
        if args.from_project:
            from audacity import Audacity
            source = inside(store.root, args.from_project)
            raw_render = project_dir / 'audacity-render.wav'
            backup_info = Audacity(log=project_dir / 'automation.log').capture(source, raw_render, project)
            encoding.lossless_master(raw_render, master)
        else:
            rate, stems = synthesis.render(recipe)
            stem_paths = []
            for name, samples in stems.items():
                path = master_dir / (name + '.wav')
                synthesis.wav(path, samples, rate)
                stem_paths.append(path)
            synthesis.wav(master, sum(stems.values()), rate)
            if not args.without_project:
                from audacity import Audacity
                backup_info = Audacity(log=project_dir / 'automation.log').project(stem_paths, project)
        record = candidate(store, asset, args.asset, revision, args.batch, bool(backup_info))
        if args.from_project:
            record['parentProject'] = args.from_project
        record['sourceMethod'] = 'audacity_edit' if args.from_project else 'synthesis'
        import platform
        import numpy
        from audacity import installation
        from quality import inspect, repeated_preview
        record['cueIds'] = recipe.get('design', {}).get('cueIds', [])
        record['design'] = recipe.get('design', {})
        record['provenance'] = {'origin':('Original deterministic oscillator and seeded-noise synthesis; no third-party samples'
            if not args.from_project else 'Manual Audacity edit; inspect parent project and frozen ancestry for source rights'),
            'rightsReview':'pending user confirmation', 'seed':recipe['seed'],
            'python':platform.python_version(), 'numpy':numpy.__version__,
            'tools':{p.name:digest(p) for p in (Path(__file__).parent / n for n in
                ('audio.py','batch.py','player.py','synthesis.py','recipes.py','quality.py','encoding.py','audacity.py','storage.py'))},
            'audacity':installation(Path('C:/Program Files/Audacity/Audacity.exe')) if record['project'] else None}
        record['authoringInputs'] = (master_dir / 'authoring-inputs.json').relative_to(store.root).as_posix()
        record['authoringInputsSha256'] = digest(master_dir / 'authoring-inputs.json')
        record['stems'] = [{'path':p.relative_to(store.root).as_posix(), 'sha256':digest(p)}
                           for p in master_dir.glob('*.wav') if p.name != 'master.wav']
        if backup_info and not args.from_project:
            from quality import decode
            import numpy as np
            project_render = project_dir / 'project-render.wav'
            a, b = decode(master), decode(project_render)
            # Export2 uses the user's lossless WAV preferences (often 16-bit with
            # shaped dither). Bound both peak and RMS error without changing them.
            if a.shape != b.shape or np.max(np.abs(a-b)) > 0.0005 or np.sqrt(np.mean((a-b)**2)) > 0.0001:
                raise ValueError('Audacity project mix differs from the preserved master')
            record['projectEvidence'] = {'tracks':backup_info['tracks'],
                'render':project_render.relative_to(store.root).as_posix(),
                'renderSha256':digest(project_render), 'maximumMasterDelta':float(np.max(np.abs(a-b))),
                'rmsMasterDelta':float(np.sqrt(np.mean((a-b)**2))),
                'comparisonTolerance':{'peak':0.0005,'rms':0.0001,'reason':'lossless Audacity WAV preference may use 16-bit shaped dither'}}
        record['quality'] = inspect(store.root / record['export'], record['loop'])
        repeat = store.root / 'previews' / args.batch / f'{args.asset}-{revision}-repeat.wav'
        repeated_preview(store.root / record['export'], repeat, record['loop'],
                         interval=recipe.get('design',{}).get('repetitionIntervalSeconds'))
        record['repeatPreview'] = repeat.relative_to(store.root).as_posix()
        record['repeatPreviewSha256'] = digest(repeat)
        if record['loop']:
            from quality import loop_audition
            audition = repeat.with_name(f'{args.asset}-{revision}-audition.wav')
            loop_audition(store.root / record['export'], audition)
            record['auditionPreview'] = audition.relative_to(store.root).as_posix()
            record['auditionPreviewSha256'] = digest(audition)
        asset['revisions'].append(record)
        write(export_dir / 'candidate.json', {"schema": 1, "assetId": args.asset, "revision": revision,
                                              "owner": "art/audio-manifest.json"})
        write(manifest_path, manifest)
        print(f'{args.asset} {revision}: {store.root / record["preview"]}')

def candidate(store, asset, asset_id, revision, batch, has_project):
    import encoding
    master_dir = store.path('masters', asset['family'], asset_id, revision)
    master = master_dir / 'master.wav'
    recipe_path = master_dir / 'recipe.json'
    recipe = read(recipe_path)
    if recipe.get('assetId') != asset_id:
        raise ValueError('Frozen recipe identity mismatch')
    export_dir = store.path('exports', asset['family'], asset_id, revision)
    export_dir.mkdir(parents=True, exist_ok=True)
    master_measurements = encoding.validate(master, vorbis=False, scope=asset['scope'])
    export = export_dir / (asset_id + '.ogg')
    if export.exists():
        raise ValueError('Export already exists without a completed candidate; inspect the failed revision')
    encoder = encoding.encode(master, export)
    measurements = encoding.validate(export, scope=asset['scope'],
        expected_duration=master_measurements['duration'], loop=bool(recipe.get('loop')))
    preview = inside(store.root, f'previews/{batch}/{asset_id}-{revision}.ogg')
    preview.parent.mkdir(parents=True, exist_ok=True)
    if preview.exists():
        raise FileExistsError(preview)
    shutil.copy2(export, preview)
    relative = lambda path: path.relative_to(store.root).as_posix()
    return {"id": revision, "recipeSha256": digest(recipe_path),
        "recipeSnapshot": relative(recipe_path),
        "master": relative(master), "masterSha256": digest(master),
        "export": relative(export), "exportSha256": digest(export),
        "preview": relative(preview), "encoder": encoder, "measurements": measurements,
        "masterMeasurements": master_measurements, "loop": bool(recipe.get('loop')),
        "project": relative(store.path('projects', asset['family'], asset_id, revision) / (asset_id + '.aup3')) if has_project else None,
        "backup": None, "review": {"status": "pending"}}

def finalize(args):
    from audacity import closed_backup
    store = Storage(args.auxiliary)
    path = REPO / 'art/audio-manifest.json'
    with lease(store.root), lease(REPO / 'art/source/audio'):
        manifest = read(path)
        asset = manifest['assets'][args.asset]
        matches = [r for r in asset['revisions'] if r['id'] == args.revision]
        if not matches:
            if not args.batch:
                raise ValueError('Recovering an interrupted revision requires --batch')
            from audacity import ensure_closed
            ensure_closed(store.path('projects', asset['family'], args.asset, args.revision) / (args.asset + '.aup3'))
            # Resume saved artifacts from an interrupted author operation. No DSP
            # regeneration, database repair or sidecar removal is attempted.
            record = candidate(store, asset, args.asset, args.revision, args.batch, True)
            asset['revisions'].append(record)
        else:
            _, record = selected(manifest, args)
        if record['backup'] and record['backup'].get('closed'):
            verify_candidate(store, record, asset['scope'])
            print('Revision already finalized')
            return
        if not record['project']:
            raise ValueError('DSP-only revision has no project to finalize')
        verify_candidate(store, record, asset['scope'])
        backup_dir = store.path('backups', asset['family'], args.asset, args.revision)
        backup_dir.mkdir(parents=True, exist_ok=True)
        project = inside(store.root, record['project'])
        backup = backup_dir / project.name
        record['backup'] = closed_backup(project, backup)
        record['backup']['path'] = backup.relative_to(store.root).as_posix()
        write(path, manifest)
        write(store.path('exports', asset['family'], args.asset, args.revision) / 'candidate.json',
              {"schema": 1, "assetId": args.asset, "revision": args.revision,
               "owner": "art/audio-manifest.json"})
        print(f'Finalized {args.asset} {args.revision}; closed backup verified')

def selected(manifest, args):
    asset = manifest['assets'][args.asset]
    matches = [r for r in asset['revisions'] if r['id'] == args.revision]
    if len(matches) != 1:
        raise ValueError('Unknown or duplicate revision')
    return asset, matches[0]

def verify_candidate(store, record, scope):
    import encoding
    for name in ('master', 'export'):
        if digest(inside(store.root, record[name])) != record[name + 'Sha256']:
            raise ValueError(f'Changed {name}; author a new revision')
    if digest(inside(store.root, record['recipeSnapshot'])) != record['recipeSha256']:
        raise ValueError('Recipe snapshot mismatch')
    for item in record.get('stems', []):
        if digest(inside(store.root, item['path'])) != item['sha256']:
            raise ValueError('Stem changed; author a new revision')
    for name, expected in record.get('provenance',{}).get('tools',{}).items():
        if digest(inside(store.root,f'references/tools/{expected}/{name}')) != expected:
            raise ValueError('Missing or changed archived authoring tool version')
    for path, hash_key in [('authoringInputs','authoringInputsSha256'), ('repeatPreview','repeatPreviewSha256'), ('auditionPreview','auditionPreviewSha256')]:
        if path in record and digest(inside(store.root, record[path])) != record[hash_key]:
            raise ValueError('Changed frozen authoring input or repeat preview')
    master = encoding.validate(inside(store.root, record['master']), vorbis=False, scope=scope)
    if master != record['masterMeasurements']:
        raise ValueError('Master measurements changed')
    actual = encoding.validate(inside(store.root, record['export']), scope=scope,
                              expected_duration=record['masterMeasurements']['duration'], loop=record['loop'])
    if actual != record['measurements']:
        raise ValueError('Measurements changed; use the recorded encoder environment')
    if record.get('backup') and record['backup'].get('closed'):
        from audacity import closed_reader
        import hashlib
        with closed_reader(inside(store.root, record['project'])) as source:
            if hashlib.sha256(source.read()).hexdigest() != record['backup']['projectSha256']:
                raise ValueError('Project changed after finalization; capture it as a new revision')
        backup = record['backup'].get('path')
        if not backup or digest(inside(store.root, backup)) != record['backup']['backupSha256']:
            raise ValueError('Missing or changed project backup')

def review(args):
    path = REPO / 'art/audio-manifest.json'
    store = Storage(args.auxiliary)
    with lease(REPO / 'art/source/audio'):
        manifest = read(path)
        asset, record = selected(manifest, args)
        verify_candidate(store, record, asset['scope'])
        if record['review']['status'] != 'pending':
            raise ValueError('Review is immutable; create a new revision')
        if any(not getattr(args, field).strip() for field in ('reviewer', 'notes', 'license', 'attribution')):
            raise ValueError('Review fields cannot be blank')
        if args.accept and (not args.listened or not args.mix_reviewed or (record['loop'] and not args.loop_reviewed)):
            raise ValueError('Listening, native mix and loop review (when applicable) are required')
        record['review'] = {"status": "accepted" if args.accept else "rejected",
            "reviewer": args.reviewer, "notes": args.notes, "license": args.license,
            "attribution": args.attribution, "listened": args.listened,
            "mixReviewed": args.mix_reviewed, "loopReviewed": args.loop_reviewed}
        write(path, manifest)


def creative_review(args):
    """Record listening feedback without asserting native mix or release approval."""
    if args.status not in {'accepted', 'revise_requested'}:
        raise ValueError('Creative status must be accepted or revise_requested')
    if any(not getattr(args, field).strip() for field in ('reviewer', 'at', 'notes')):
        raise ValueError('Creative review fields cannot be blank')
    decision = {key: getattr(args, key).strip() for key in ('status', 'reviewer', 'at', 'notes')}
    path = REPO / 'art/audio-manifest.json'
    store = Storage(args.auxiliary)
    with lease(REPO / 'art/source/audio'):
        manifest = read(path)
        asset, record = selected(manifest, args)
        if record['review']['status'] != 'pending':
            raise ValueError('Final review is immutable; create a new revision')
        verify_candidate(store, record, asset['scope'])
        previous = record.get('creativeReview')
        if previous == decision:
            print(f'Creative review already recorded for {args.asset} {args.revision}')
            return
        if previous:
            record.setdefault('creativeReviewHistory', []).append(previous)
        record['creativeReview'] = decision
        write(path, manifest)
        print(f'Creative review {args.status}: {args.asset} {args.revision}; final review remains pending')


def integration_authorization(art, manifest, record, args):
    """Bind integration permission to approved creative work without inventing audition."""
    selection_path = getattr(args, 'integration_selection', None)
    authority = getattr(args, 'integration_authority', None)
    if not selection_path:
        if authority:
            raise ValueError('--integration-authority requires --integration-selection')
        return None
    if not authority or not authority.strip():
        raise ValueError('Integration promotion requires the explicit user instruction')
    if record['review']['status'] != 'pending':
        raise ValueError('Integration promotion requires pending final review; accepted takes use normal promotion')
    from audit import select
    source = inside(art, selection_path)
    matches = [item for item, _, _ in select(read(source), manifest)
               if item['asset'] == args.asset and item['revision'] == args.revision]
    if len(matches) != 1 or matches[0]['purpose'] != 'runtime':
        raise ValueError('Integration selection must name this exact runtime take')
    if matches[0].get('sourceExportSha256') != record['exportSha256']:
        raise ValueError('Integration selection must pin the exact export hash')
    authorization = {'status': 'authorized', 'authority': 'User', 'notes': authority.strip(),
        'selection': source.relative_to(art).as_posix(), 'selectionSha256': digest(source),
        'purpose': 'runtime', 'sourceExportSha256': record['exportSha256']}
    if record.get('integrationPromotion', authorization) != authorization:
        raise ValueError('Integration authorization is immutable')
    return authorization


def promote(args):
    store = Storage(args.auxiliary)
    art = REPO / 'art'
    with lease(art / 'source/audio'):
        manifest = read(art / 'audio-manifest.json')
        asset, record = selected(manifest, args)
        integration = integration_authorization(art, manifest, record, args)
        if (not integration and record['review']['status'] != 'accepted') or not record['project'] or not record['backup'] or not record['backup']['closed']:
            raise ValueError('Promotion requires an accepted take and a safely closed editable project')
        verify_candidate(store, record, asset['scope'])
        catalog_path = art / 'audio-catalog.json'
        catalog = read(catalog_path)
        entry = catalog['assets'][args.asset]
        revision = record['id']
        # New immutable destinations. Write catalog last: a failed copy cannot expose
        # an incomplete take to builds; prior approved revisions remain untouched.
        paths = {"master": f'source/audio/masters/{args.asset}/{revision}.wav',
                 "approved": f'source/audio/approved/{args.asset}/{revision}.ogg',
                 "recipe": f'source/audio/recipes/history/{args.asset}/{revision}.json',
                 "receipt": f'source/audio/receipts/{args.asset}/{revision}{".integration" if integration else ""}.json'}
        for kind, source in [('master', record['master']), ('approved', record['export']),
                             ('recipe', record['recipeSnapshot'])]:
            dest = inside(art, paths[kind])
            dest.parent.mkdir(parents=True, exist_ok=True)
            if dest.exists() and digest(dest) != digest(inside(store.root, source)):
                raise ValueError('Immutable promoted revision already differs')
            if not dest.exists():
                shutil.copy2(inside(store.root, source), dest)
        receipt = {"schema": 1, "assetId": args.asset, "revision": revision,
            "masterSha256": record['masterSha256'], "approvedSha256": record['exportSha256'],
            "recipeSha256": record['recipeSha256'], "encoder": record['encoder'],
            "measurements": record['measurements'], "loop": record['loop'], "review": record['review'],
            "sourceMethod": record.get('sourceMethod', 'synthesis'),
            "projectSha256": record['backup']['projectSha256'],
            "backupSha256": record['backup']['backupSha256'],
            "sourceLinks": {"root": "auxiliary_audio", "project": record['project'],
                "backup": record['backup']['path'], "master": record['master'],
                "export": record['export'], "recipe": record['recipeSnapshot']}}
        for key in ('authoringInputs', 'authoringInputsSha256', 'stems'):
            if key in record:
                receipt['sourceLinks'][key] = record[key]
        if integration:
            receipt['creativeReview'] = record['creativeReview']
            receipt['integrationPromotion'] = integration
        receipt_path = inside(art, paths['receipt'])
        if receipt_path.exists() and read(receipt_path) != receipt:
            raise ValueError('Immutable receipt differs')
        write(receipt_path, receipt)
        if integration and 'integrationPromotion' not in record:
            record['integrationPromotion'] = integration
            write(art / 'audio-manifest.json', manifest)
        entry.update(paths, status='approved', revision=revision)
        write(catalog_path, catalog)
        stage = ' for integration; final review remains pending' if integration else ''
        print(f'Promoted {args.asset} {revision}{stage}; run generateAssets / assetOwnershipInvariants')

def doctor(args):
    from audacity import Audacity
    import encoding
    from audacity import installation
    print(installation(args.audacity))
    with lease(REPO / 'art/source/audio'):
        pipe = Audacity()
        print(pipe.command('Help: Command=SaveProject2'))
        print(pipe.command('GetInfo: Type=Tracks'))
    print(encoding.run(['-version']).stdout.decode().splitlines()[0])

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    for name, action in [('author', author), ('finalize', finalize), ('review', review), ('creative-review', creative_review), ('promote', promote)]:
        p = commands.add_parser(name)
        p.set_defaults(action=action)
        p.add_argument('--auxiliary', required=True, help='Auxiliary root (Audio/ is appended)')
        p.add_argument('asset')
        if name == 'author':
            p.add_argument('--batch', required=True)
            source = p.add_mutually_exclusive_group()
            source.add_argument('--without-project', action='store_true', help='DSP experiment only; cannot promote')
            source.add_argument('--from-project', help='Closed edited .aup3 path relative to Auxiliary Audio/')
        else:
            p.add_argument('revision')
        if name == 'finalize':
            p.add_argument('--batch', help='Preview batch when resuming an interrupted saved revision')
        if name == 'review':
            decision = p.add_mutually_exclusive_group(required=True)
            decision.add_argument('--accept', action='store_true')
            decision.add_argument('--reject', action='store_true')
            for key in ('reviewer', 'notes', 'license', 'attribution'):
                p.add_argument('--' + key, required=True)
            for key in ('listened', 'mix-reviewed', 'loop-reviewed'):
                p.add_argument('--' + key, action='store_true')
        if name == 'creative-review':
            p.add_argument('--status', required=True, choices=['accepted', 'revise_requested'])
            for key in ('reviewer', 'at', 'notes'):
                p.add_argument('--' + key, required=True)
        if name == 'promote':
            p.add_argument('--integration-selection', help='Frozen art-relative selection for explicit creative-approved integration; final review remains pending')
            p.add_argument('--integration-authority', help='Exact user instruction authorizing integration, required with --integration-selection')
    p = commands.add_parser('doctor')
    p.add_argument('--audacity', type=Path, default=Path('C:/Program Files/Audacity/Audacity.exe'))
    p.set_defaults(action=doctor)
    p = commands.add_parser('validate')
    p.add_argument('path', type=Path)
    p.set_defaults(action=lambda args: print(__import__('encoding').validate(args.path)))
    args = parser.parse_args()
    args.action(args)

if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, OSError, TimeoutError) as error:
        sys.exit(str(error))
