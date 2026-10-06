"""Read-only traces from the accepted profile, not a new generation or throughput claim."""
import collections, gzip, json, pathlib, sys

game = pathlib.Path(r'C:\Users\rcboo\AppData\Roaming\PrismLauncher\instances\All the Mods 10\minecraft')
out = pathlib.Path(sys.argv[1])
d = json.load(gzip.open(game / 'config/essence_ascendance/generated_balance.json.gz'))
resources = d['economy']['resources']
evidence = d['evidence']['resources']
selected = ['minecraft:' + s for s in ['oak_log', 'iron_ingot', 'coal', 'wheat', 'cobblestone', 'amethyst_shard', 'ender_pearl', 'leather', 'wheat_seeds']]
traces = {s: dict(economy=resources.get(s), evidence=evidence.get(s)) for s in selected}
processes = d['economy']['processes']
# Retain named limiting paths, not every process touching a common material.
for s, trace in traces.items():
    reasons = str(trace['economy'])
    trace['limiting_processes'] = [p for p in processes if p['id'] in reasons]
samples = {}
for essence in ['offense','defense','vitality','mobility','gathering','utility']:
    key = 'essence_ascendance:' + essence
    candidates = [(s, r) for s,r in resources.items() if r.get('routedYields',{}).get(key,0)>0]
    if not candidates:
        # Expose actual serializer names for audit readers.
        samples[essence] = dict(resource_shape=next(iter(resources.values())))
        continue
    candidates = [(s,r) for s,r in candidates if evidence.get(s,{}).get('stage') in ['ENTRY','EARLY']]
    candidates.sort(key=lambda pair: (not pair[0].startswith('minecraft:'), evidence[pair[0]]['stage'] != 'ENTRY', pair[0]))
    samples[essence] = [dict(item=s, economy=r, evidence=evidence.get(s)) for s,r in candidates[:20]]
result = dict(integrity=d['integrity'], schema=d['schema'], traces=traces, early_sources=samples,
              warnings=d['economy']['warnings'], settings=d['settings'], overrides=d['overrides'],
              runtime_bootstrap={k:d['runtime'].get(k) for k in ['crucible','pylons','infuser','equipment']})
result['production_summary'] = dict(
    families=dict(collections.Counter(p['family'] for p in processes)),
    providers=dict(collections.Counter(p['provider'] for p in processes)),
    acquisition_complete=sum(p['metadata'].get('acquisition_complete') == 'true' for p in processes),
    conservation_complete=sum(p['metadata'].get('conservation_complete') == 'true' for p in processes),
    measured_source_rates=sum(s.get('rateKnown',False) for r in evidence.values() for s in r['sources']))
early = [(s,r) for s,r in resources.items() if evidence[s]['stage'] in ['ENTRY','EARLY'] and r['dissolutionYield']['microUnits'] > 92160_000000]
early.sort(key=lambda pair: -pair[1]['dissolutionYield']['microUnits'])
result['early_items_exceeding_base_reservoir'] = dict(count=len(early), capacity=92160,
    examples=[dict(item=s, whole_payout=r['dissolutionYield']['microUnits']//1000000,
                   stage=evidence[s]['stage'], confidence=evidence[s]['confidence']) for s,r in early[:20]])
(out/'economy-bootstrap-audit.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
replay_name = 'runtime-final-replay' if '--final' in sys.argv else 'runtime-replay'
replay = out / replay_name / 'runtime.json'
if replay.exists():
    differences = []
    def compare(a, b, path='runtime'):
        if isinstance(a,dict) and isinstance(b,dict):
            for k in sorted(a.keys() | b.keys()): compare(a.get(k),b.get(k),path+'/'+k)
        elif isinstance(a,list) and isinstance(b,list) and len(a)==len(b):
            for i,(left,right) in enumerate(zip(a,b)): compare(left,right,path+'/'+str(i))
        elif a != b: differences.append(dict(path=path,accepted=a,replay=b))
    compare(d['runtime'],json.loads(replay.read_text(encoding='utf-8')))
    (out/(replay_name+'-differences.json')).write_text(json.dumps(differences,indent=2),encoding='utf-8')
    print('Complete runtime comparison differences:',len(differences))
print('Resource shape:', list(next(iter(resources.values())))); print('Source evidence shape:', list(next(iter(evidence.values()))))
print('Traces and early sources saved; accepted integrity:',d['integrity'])
