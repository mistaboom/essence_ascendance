"""Read-only installed evidence audit; outputs go only to the explicit receipt directory."""
import collections, csv, hashlib, json, pathlib, sys, zipfile

root = pathlib.Path(__file__).resolve().parents[2]
game = pathlib.Path(r'C:\Users\rcboo\AppData\Roaming\PrismLauncher\instances\All the Mods 10\minecraft')
out = pathlib.Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
def save(name, value):
    (out / name).write_text(json.dumps(value, indent=2, ensure_ascii=False), encoding='utf-8')
def rows(name):
    with (game / 'config/essence_ascendance/reports' / name).open(encoding='utf-8-sig', newline='') as f:
        return list(csv.DictReader(f))
prior = pathlib.Path(r'C:\Users\rcboo\Documents\Essence Ascendance Auxiliary Files\pack-compatibility\validation\chat-07c\20261005-194633Z-native-acceptance\protected-current.json')
protected = []
for record in json.loads(prior.read_text(encoding='utf-8-sig')):
    p = pathlib.Path(record['path'])
    protected.append(dict(path=str(p), bytes=p.stat().st_size, sha256=hashlib.file_digest(p.open('rb'), 'sha256').hexdigest()))
save('protected-' + ('after' if '--after' in sys.argv else 'before') + '.json', protected)
if '--after' in sys.argv:
    assert protected == json.loads((out / 'protected-before.json').read_text()), 'Live authority changed'
    print('All six protected live files unchanged'); sys.exit()
summary = {}
for name in ['evidence.csv', 'valuation.csv', 'competitive_frontiers.csv', 'adaptive_balance.csv', 'invariants.csv', 'runtime_parameters.csv', 'latent_ore_supply.csv', 'attunement_targets.csv']:
    data = rows(name)
    summary[name] = dict(count=len(data), columns=list(data[0]) if data else [], sample=data[:2])
    if name in ['evidence.csv', 'valuation.csv']:
        summary[name]['essence_items'] = [r for r in data if any(str(v).startswith('essence_ascendance:') for v in r.values())]
    if name == 'valuation.csv':
        summary[name]['six_essence'] = {k: dict(nonzero=sum(float(r[k] or 0) > 0 for r in data), total=sum(float(r[k] or 0) for r in data)) for k in data[0] if k.lower() in ['offense','defense','vitality','mobility','gathering','utility']}
    if name == 'competitive_frontiers.csv':
        summary[name]['axes'] = dict(collections.Counter(r.get('axis', '') for r in data))
    if name == 'adaptive_balance.csv':
        summary[name]['largest_factors'] = sorted(data, key=lambda r: float(r.get('factor') or 0), reverse=True)[:20]
summary['unsupported'] = sorted(rows('competitive_unsupported_counts.csv'), key=lambda r: int(r['count']), reverse=True)
save('native-report-audit.json', summary)
cap = json.loads((game/'config/essence_ascendance/diagnostics/competitive_capabilities.json').read_text(encoding='utf-8-sig'))
save('accepted-competitive-capabilities.json', cap)
generation = json.loads((game/'config/essence_ascendance/diagnostics/generation_evidence.json').read_text(encoding='utf-8-sig'))
save('accepted-generation-evidence.json', generation)
save('accepted-adaptive-balance.json', json.loads((game/'config/essence_ascendance/diagnostics/adaptive_balance.json').read_text(encoding='utf-8-sig')))
sources = []
for jar in sorted((game/'mods').glob('*.jar')):
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        metadata = next((n for n in ['META-INF/neoforge.mods.toml','META-INF/mods.toml','fabric.mod.json'] if n in names), None)
        record = dict(jar=jar.name, bytes=jar.stat().st_size, recipe_count=sum('/recipe/' in n and n.endswith('.json') for n in names))
        if metadata: record['metadata'] = z.read(metadata).decode('utf-8', errors='replace')
        sources.append(record)
        if any(term in jar.name.lower() for term in ['forbidden_arcanus','botanypots-neoforge','ars_nouveau','draconic-evolution','apotheosis','silent-gear','justdirethings']):
            matches = [n for n in names if n.endswith('.json') and any(t in n.lower() for t in ['eternal','indestruct','crop/wheat','crop/minecraft/wheat','time','glyph_accelerate','glyph_harvest','glyph_crush','mega','grow','repair','module','affix'])]
            # Diagnostic examples, bounded per jar; never treat raw jar definitions as effective recipes.
            save('jar-' + jar.stem + '.json', {n: z.read(n).decode('utf-8', errors='replace') for n in matches[:40]})
save('installed-jar-inventory.json', sources)
print(json.dumps({k: {'count': v['count'], 'columns': v['columns']} for k,v in summary.items() if isinstance(v,dict) and 'count' in v}, indent=2))
print('Capability counts:', cap.get('counts')); print('Generation sections:', list(generation))
