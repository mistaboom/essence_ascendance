"""Read-only development inventory and isolated all-skill comparison; never edits a game directory."""
from pathlib import Path
import csv, gzip, hashlib, json, struct, sys, zipfile

PROJECT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1]).resolve()
OUT.mkdir(parents=True, exist_ok=True)
def write(name, value):
    (OUT/name).write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
def identity(path):
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
def nbt(path):
    data = memoryview(gzip.decompress(path.read_bytes())); cursor = 0
    def read(fmt):
        nonlocal cursor
        size = struct.calcsize(fmt); value = struct.unpack_from(fmt, data, cursor); cursor += size
        return value[0] if len(value)==1 else value
    def string():
        nonlocal cursor
        size = read('>H'); value = bytes(data[cursor:cursor+size]).decode('utf-8', errors='replace'); cursor += size
        return value
    def value(tag):
        nonlocal cursor
        if tag in (1,2,3,4,5,6): return read({1:'>b',2:'>h',3:'>i',4:'>q',5:'>f',6:'>d'}[tag])
        if tag==8: return string()
        if tag==9:
            kind, size = read('>B'), read('>i'); return [value(kind) for _ in range(size)]
        if tag==10:
            result = {}
            while (kind := read('>B')):
                name=string(); result[name]=value(kind)
            return result
        if tag in (7,11,12):
            size=read('>i'); cursor += size*{7:1,11:4,12:8}[tag]; return f'<array:{size}>'
        raise ValueError(f'Unsupported NBT tag {tag}')
    tag=read('>B'); string(); return value(tag)

environments={}; protected={}
for loader in ('fabric','neoforge'):
    run=PROJECT/loader/'run'; mods=[]
    for jar in sorted((run/'mods').glob('*.jar')):
        with zipfile.ZipFile(jar) as archive:
            data=[p for p in archive.namelist() if p.startswith('data/') and not p.endswith('/')]
            entry={'file':jar.name, 'sha256':identity(jar)['sha256'], 'dataResources':data}
            if 'fabric.mod.json' in archive.namelist():
                descriptor=json.loads(archive.read('fabric.mod.json')); entry.update(id=descriptor.get('id'),version=descriptor.get('version'))
            entry['recipes']={p:json.loads(archive.read(p)) for p in data if '/recipe/' in p and p.endswith('.json')}
            mods.append(entry)
    worlds=[]
    for world in sorted((run/'saves').iterdir()):
        if not world.is_dir(): continue
        entry={'name':world.name,'datapackFiles':[str(p.relative_to(world)) for p in (world/'datapacks').rglob('*') if p.is_file()]}
        level=world/'level.dat'
        if level.exists():
            try:
                settings=nbt(level)['Data']; entry['enabledPacks']=settings.get('DataPacks',{})
                entry['generatorSettings']=settings.get('WorldGenSettings',{})
            except Exception as exc: entry['readFailure']=repr(exc)
        worlds.append(entry)
    profiles=[]
    for p in (run/'config'/'essence_ascendance').glob('generated_balance*'):
        if not p.is_file(): continue
        item=identity(p)
        if p.suffix=='.json' or p.suffix=='.gz':
            try:
                document=json.loads(gzip.decompress(p.read_bytes()) if p.suffix=='.gz' else p.read_bytes())
                item.update(schema=document.get('schema'),integrity=document.get('integrity'),revision=document.get('metadata',{}).get('generatorRevision'))
            except Exception as exc: item['readFailure']=repr(exc)
        profiles.append(item)
    for p in (run/'config').rglob('*'):
        if p.is_file() and (p.suffix=='.toml' or p.name.startswith('generated_balance')): protected[str(p)]=identity(p)
    environments[loader]={'workingDirectory':str(run),'mods':mods,'worlds':worlds,'profiles':profiles,
        'latestLog':identity(run/'logs'/'latest.log') if (run/'logs'/'latest.log').exists() else None}
write('development-environment.json',environments)
write('protected-observation.json',protected)
print(json.dumps({loader:{'mods':len(e['mods']),'worlds':[(w['name'],w.get('enabledPacks')) for w in e['worlds']],
        'profiles':[(p.get('schema'),p.get('revision')) for p in e['profiles']]} for loader,e in environments.items()},indent=2))

if len(sys.argv)>3:
    before=json.loads(Path(sys.argv[2]).read_text()); after=json.loads(Path(sys.argv[3]).read_text())
    b=before['runtime']['skillCurves']; a=after['runtime']['skillCurves']; rows=[]; ranks=[]
    for skill in after['skills']:
        key=skill['skill']; current=a[key]; old=b[key]; decision=skill.get('intrinsicDecision',{})
        row={'skill':key,'category':skill['category'],'catalogTier':skill['catalogTier'],
             'beforeTier':old.get('requiredTierId',skill['catalogTier']),'afterTier':current['requiredTierId'],
             'beforePrices':[r['cost'] for r in old['ranks']],'afterPrices':[r['cost'] for r in current['ranks']],
             'beforeRanks':old['maximumRank'],'afterRanks':current['maximumRank'],'mechanics':skill['mechanics'],
             'semantics':skill['semantics'],'intrinsicDecision':decision,'prerequisites':skill['prerequisites'],
             'requirements':skill['requirements'],'implementation':skill.get('implementation'),
             'scope':'Bootstrap comparison only; native current development evidence and gameplay acceptance pending'}
        rows.append(row)
        for rank in current['ranks']: ranks.append({'skill':key,'tier':current['requiredTierId'],**rank})
    write('all-skill-comparison.json',rows); write('all-rank-comparison.json',ranks)
    with (OUT/'all-skill-comparison.csv').open('w',newline='',encoding='utf-8-sig') as stream:
        writer=csv.DictWriter(stream,fieldnames=rows[0].keys()); writer.writeheader()
        for row in rows: writer.writerow({k:json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list)) else v for k,v in row.items()})
    text=['# All registered skills — isolated reference comparison','',after['scope'],
          '', 'These are computed test-reference results, not a freshly collected vanilla profile. Policy assumptions and exact native rank parameters are in the accompanying JSON/CSV.',
          '', '| Skill | Before → after | Prices before → after | Published ranks |','|---|---|---|---|']
    for row in rows: text.append(f"| {row['skill'].split(':')[1]} | {row['beforeTier'].split(':')[1]} → {row['afterTier'].split(':')[1]} | {row['beforePrices']} → {row['afterPrices']} | {row['beforeRanks']} → {row['afterRanks']} |")
    (OUT/'all-skill-comparison.md').write_text('\n'.join(text)+'\n',encoding='utf-8')
