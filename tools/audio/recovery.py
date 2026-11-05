"""Reopen and verify saved, unfinished candidates through Audacity itself.

Does not manipulate databases or recovery sidecars. Close the opened projects
normally in the GUI, then use the regular finalize command for closed backups.
"""
import argparse
from datetime import datetime, timezone
from pathlib import Path
import json
import numpy as np
import audio
from audacity import Audacity
from quality import decode
from storage import REPO, Storage, inside, read, write, lease, digest


def reopen(definition, store, start=0, limit=8):
    if start < 0 or not 1 <= limit <= 12:
        raise ValueError('Recovery needs a nonnegative offset and 1..12 projects')
    with lease(store.root), lease(REPO/'art/source/audio'):
        path = REPO/'art/audio-manifest.json'
        manifest = read(path)
        items = definition['items'][start:start+limit]
        pipe = Audacity()
        for item in items:
            asset = manifest['assets'][item['asset']]
            takes = [r for r in asset['revisions'] if r['preview'].startswith('previews/'+definition['id']+'/')]
            if not takes:
                continue
            take = takes[-1]
            if (take.get('backup') or {}).get('closed'):
                continue
            audio.verify_candidate(store, take, asset['scope'])
            project = inside(store.root, take['project'])
            receipt_dir = project.parent / ('recovery-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
            receipt_dir.mkdir()
            (receipt_dir/'recovery-tool.py').write_bytes(Path(__file__).read_bytes())
            pipe.log = receipt_dir/'commands.log'
            # Installed 3.7.9 may open another project yet report Failed when
            # OpenProject2 starts from a populated frame. Give it an empty one.
            pipe.command('New:')
            pipe.command('OpenProject2: Filename='+pipe.quoted(project))
            answer = pipe.command('GetInfo: Type=Tracks')
            tracks = json.loads(answer.split('BatchCommand finished:')[0])
            expected = take.get('projectEvidence', {}).get('tracks', [])
            if [t['name'] for t in tracks] != [t['name'] for t in expected]:
                raise ValueError('Recovered track organization differs; project left open')
            pipe.command('SelectAll:')
            rendered = receipt_dir/'project-render.wav'
            pipe.command('Export2: Filename='+pipe.quoted(rendered)+' NumChannels=1')
            a, b = decode(inside(store.root,take['master'])), decode(rendered)
            if a.shape != b.shape or np.max(np.abs(a-b)) > .0005 or np.sqrt(np.mean((a-b)**2)) > .0001:
                raise ValueError('Recovered audio differs; project left open')
            pipe.command('Save:')
            receipt = {'method':'Audacity OpenProject2 / Export2 / Save; no database editing',
                'tracks':tracks, 'maximumMasterDelta':float(np.max(np.abs(a-b))),
                'rmsMasterDelta':float(np.sqrt(np.mean((a-b)**2))),
                'render':rendered.relative_to(store.root).as_posix(),'renderSha256':digest(rendered),
                'toolSha256':digest(Path(__file__)),'saved':True,'closure':'pending normal GUI close'}
            write(receipt_dir/'receipt.json',receipt)
            take.setdefault('recoveryReceipts',[]).append((receipt_dir/'receipt.json').relative_to(store.root).as_posix())
            write(path,manifest)
            print('Reopened, compared and saved '+item['asset'],flush=True)


if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('definition',type=Path)
    p.add_argument('--auxiliary',required=True)
    p.add_argument('--start',type=int,default=0)
    p.add_argument('--limit',type=int,default=8)
    a=p.parse_args()
    reopen(read(a.definition),Storage(a.auxiliary),a.start,a.limit)
