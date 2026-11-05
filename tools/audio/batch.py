"""Shared data-driven batch preparation, authoring, finalization and local review."""
import argparse
import re
from pathlib import Path
from types import SimpleNamespace
import audio
from storage import REPO, Storage, inside, lease, read, write, digest


def prepare(definition):
    art = REPO / 'art'
    with lease(art / 'source/audio'):
        manifest, catalog = read(art/'audio-manifest.json'), read(art/'audio-catalog.json')
        for item in definition['items']:
            asset = item['asset']
            if not re.fullmatch('[a-z0-9_]+',asset) or not re.fullmatch('[a-z0-9_]+',item['family']) or item['scope'] not in {'ui','world'}:
                raise ValueError('Invalid asset ID')
            recipe = f'source/audio/recipes/{asset}.json'
            from recipes import resolve
            resolved, _ = resolve(inside(art,recipe))
            import synthesis
            synthesis.validate(resolved)
            for cue in resolved.get('design',{}).get('cueIds',[]):
                if cue not in catalog['cues']:
                    raise ValueError('Unknown intended cue')
            entry = {'family':item['family'],'scope':item['scope'],'recipe':recipe,'revisions':[]}
            if asset in manifest['assets']:
                if any(manifest['assets'][asset][k]!=entry[k] for k in ('family','scope','recipe')):
                    raise ValueError('Conflicting existing asset owner')
            else:
                manifest['assets'][asset]=entry
                catalog['assets'][asset]={'status':'authoring_only',
                    'resource':f"essence_ascendance:{item['family']}/{asset}"}
        write(art/'audio-manifest.json',manifest)
        write(art/'audio-catalog.json',catalog)


def records(definition, store, available=False):
    manifest=read(REPO/'art/audio-manifest.json')
    result=[]
    for item in definition['items']:
        entry=manifest['assets'][item['asset']]
        matches=[r for r in entry['revisions'] if r['preview'].startswith(f"previews/{definition['id']}/")]
        if not matches:
            if available:
                continue
            raise ValueError(f"No batch take: {item['asset']}")
        result.append((item,entry,matches[-1]))
    return result


def author_chunk(definition, auxiliary, limit=8, new_revisions=False):
    """Bound open DAW projects; resume from completed manifest takes next invocation."""
    if not isinstance(limit,int) or limit < 1:
        raise ValueError('Authoring limit must be a positive integer')
    authored=0
    for item in definition['items']:
        manifest=read(REPO/'art/audio-manifest.json')
        done=[r for r in manifest['assets'][item['asset']]['revisions']
              if r['preview'].startswith(f"previews/{definition['id']}/")]
        if done and not new_revisions:
            print('Preserving completed take '+item['asset']+' '+done[-1]['id'],flush=True)
            continue
        print('Authoring '+item['asset'],flush=True)
        audio.author(SimpleNamespace(asset=item['asset'],auxiliary=auxiliary,
            batch=definition['id'],from_project=None,without_project=False))
        authored+=1
        if authored >= limit:
            break
    print(f'Saved {authored} projects; close them normally, finalize available takes, rerun to resume.',flush=True)
    return authored


def report(definition,store):
    summary=[]
    entries=records(definition,store)
    takes={item['asset']:r for item,entry,r in entries}
    from quality import comparison, inspect
    from player import page
    root=inside(store.root,'previews/'+definition['id'])
    root.mkdir(parents=True,exist_ok=True)
    groups=[dict(g) for g in definition.get('reviewGroups',[])]
    assignments={}
    for group in groups:
        for asset in group.get('assets',[]):
            if asset in assignments:
                raise ValueError('Asset assigned to multiple listening groups')
            if asset not in takes:
                raise ValueError('Unknown listening-group asset')
            assignments[asset]=group['id']
    def ensure_group(identifier,title,description=''):
        if not any(g['id']==identifier for g in groups):
            groups.append({'id':identifier,'title':title,'description':description})
    def source(path):
        resolved=inside(store.root,path)
        if not resolved.is_file():
            raise ValueError('Missing listening file: '+str(resolved))
        return resolved.as_uri()
    cards=[]
    indexes={}
    for group in definition.get('comparisons',[]):
        dest=inside(root,group['id']+'.wav')
        index=comparison([(asset,store.root/takes[asset]['export']) for asset in group['assets']],
                         dest,gap=group.get('gapSeconds',.45),repeats=group.get('repeats',1),
                         interval=group.get('intervalSeconds'))
        indexes[group['id']]=index
        group_id=group.get('group','comparisons')
        ensure_group(group_id,'Start here · comparisons','Ordered listening clips preserve the individual source levels.')
        cards.append({'id':'comparison-'+group['id'],'title':group['label'],
            'description':group.get('description','Listen to the selected sounds in order.'),
            'group':group_id,'src':dest.as_uri(),'status':'demonstration',
            'sequence':' → '.join(takes[a].get('design',{}).get('title',a.replace('_',' ')) for a in group['assets']),
            'technical':{'segments':index,'spacingSeconds':group.get('gapSeconds',.45),
                'note':'Derived comparison; review decisions belong to the underlying takes.'}})
    for item,entry,r in entries:
        audio.verify_candidate(store,r,entry['scope'])
        current_quality=inspect(store.root/r['export'],r['loop'])
        m=r['measurements']
        design=r.get('design',{})
        cards.append({'id':item['asset'],'title':item.get('title',design.get('title',item['asset'].replace('_',' ').title())),
            'description':item.get('description',design.get('description','')),
            'group':assignments.get(item['asset'],item.get('group','candidates')),
            'src':source(r.get('auditionPreview',r['preview'])),'repeatSrc':source(r['repeatPreview']),
            'status':r['review']['status'],'revision':r['id'],'duration':m['duration'],
            'creativeReview':r.get('creativeReview',{}),
            'tag':'Listening demonstration' if design.get('previewOnly') else ('Continuous loop' if r['loop'] else 'One-shot'),
            'links':[('Audacity project',source(r['project'])),('Lossless master',source(r['master'])),('Ogg candidate',source(r['export']))],
            'notes':r['review'].get('notes',''),
            'technical':{'assetId':item['asset'],'cueIds':r.get('cueIds',[]),'measurements':m,
                'quality':current_quality,'review':r['review'],'creativeReview':r.get('creativeReview',{}),
                'creativeReviewHistory':r.get('creativeReviewHistory',[]),'projectMix':r.get('projectEvidence',{}),
                'provenance':r.get('provenance',{}),'encoder':r.get('encoder',{}),
                'backupClosed':bool(r.get('backup',{}) and r['backup']['closed'])}})
        summary.append({'assetId':item['asset'],'revision':r['id'],'cueIds':r.get('cueIds'),
            'manifest':'art/audio-manifest.json','preview':r['preview'],'project':r['project'],
            'measurements':m,'quality':current_quality,'review':r['review'],'creativeReview':r.get('creativeReview',{}),
            'backupClosed':bool(r.get('backup',{} ) and r['backup']['closed'])})
    if any(c['group']=='candidates' for c in cards):
        ensure_group('candidates','Individual sounds','Each candidate has its own editable project, lossless master and review record.')
    listening=definition.get('listening',[])
    for clip in listening:
        group_id=clip.get('group','demonstrations')
        ensure_group(group_id,'Listening demonstrations')
        path=inside(store.root,clip['path'])
        if clip.get('sha256') and digest(path)!=clip['sha256']:
            raise ValueError('Demonstration file hash differs')
        cards.append({'id':'demo-'+clip['id'],'title':clip['title'],'description':clip.get('description',''),
            'group':group_id,'src':source(clip['path']),'status':'demonstration',
            'technical':{**clip,'sha256':digest(path),'note':'Supplementary listening demonstration; does not approve source candidates.'}})
    references=[]
    if definition.get('nativeReferences'):
        reference_document=read(inside(store.root,definition['nativeReferences']))
        references=reference_document['items']
        for clip in references:
            path=inside(store.root,clip['path'])
            if clip.get('sha256') and digest(path)!=clip['sha256']:
                raise ValueError('Native reference file hash differs')
            group_id=clip.get('group','native')
            ensure_group(group_id,'Retained Minecraft sounds','Existing game sounds for reference. These are not new authored candidates and are not copied into the mod.')
            links=[('Local reference',source(clip['path']))]
            if clip.get('otherTarget',{}).get('path'):
                links.append(('Other Minecraft version',source(clip['otherTarget']['path'])))
            cards.append({'id':'native-'+clip['id'],'title':clip['title'],'description':clip.get('description',''),
                'group':group_id,'src':source(clip['path']),'status':'reference','tag':'Minecraft audio',
                'links':links,'technical':{'event':clip['event'],'reference':clip,
                    'packProvenance':{k:v for k,v in reference_document.items() if k!='items'}}})
    write(root/'comparison-index.json',indexes)
    write(root/'technical-summary.json',{'schema':1,'batch':definition['id'],'candidates':summary,
        'supplementaryListening':listening,'nativeReferences':references,
        'reviewOwner':'art/audio-manifest.json; report is derived, never approval'})
    rendered=page(definition.get('title','Essence Ascendance · sound review'),
        definition.get('description',definition.get('preference','')),groups,cards,definition['id'])
    (root/'review.html').write_text(rendered,encoding='utf-8')
    print(root/'review.html',flush=True)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=['prepare','author','finalize','report'])
    p.add_argument('definition',type=Path)
    p.add_argument('--auxiliary',required=True)
    p.add_argument('--new-revisions',action='store_true',help='Create new takes even for completed items in this batch')
    p.add_argument('--limit',type=int,default=8,help='Maximum new Audacity projects per author invocation (default: 8); close before resuming')
    p.add_argument('--available',action='store_true',help='Finalize completed takes while unfinished batch items are skipped')
    args=p.parse_args()
    if args.limit < 1:
        p.error('--limit must be a positive integer')
    if args.available and args.action!='finalize':
        p.error('--available applies only to finalize; reports require the complete batch')
    definition=read(args.definition)
    if definition['schema']!=1 or not re.fullmatch('[a-z0-9][a-z0-9_-]*',definition['id']) or len({i['asset'] for i in definition['items']})!=len(definition['items']):
        raise ValueError('Invalid batch or duplicate asset')
    store=Storage(args.auxiliary)
    if args.action=='prepare':
        prepare(definition)
    elif args.action=='author':
        author_chunk(definition,args.auxiliary,args.limit,args.new_revisions)
    elif args.action=='finalize':
        for item,entry,r in records(definition,store,available=args.available):
            audio.finalize(SimpleNamespace(asset=item['asset'],revision=r['id'],
                auxiliary=args.auxiliary,batch=None))
    else:
        report(definition,store)


if __name__=='__main__':
    main()
