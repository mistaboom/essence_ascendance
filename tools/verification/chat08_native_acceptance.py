"""Read-only native acceptance capture; writes only to the requested receipt folder."""
import collections
import gzip
import hashlib
import json
import pathlib
import shutil
import sys

game = pathlib.Path(sys.argv[1])
out = pathlib.Path(sys.argv[2])
baseline = pathlib.Path(sys.argv[3])
out.mkdir(parents=True, exist_ok=True)
events = []
for name in ['2026-10-05-7.log.gz', 'latest.log']:
    path = game / 'logs' / name
    shutil.copy2(path, out / name)
    opener = gzip.open if name.endswith('.gz') else open
    with opener(path, 'rt', encoding='utf-8') as lines:
        for line in lines:
            if 'Balance performance ' not in line:
                continue
            event = json.loads(line.split('Balance performance ', 1)[1])
            if event.get('event') == 'end':
                events.append(dict(log=name, **event['result']))

(out / 'operations.json').write_text(json.dumps(events, indent=2), encoding='utf-8')
rebuild = next(e for e in events if e['operation'] == 'explicit_rebuild')
validation = next(e for e in events if e['operation'] == 'explicit_profile_validation')
restart = next(e for e in events if e['operation'] == 'saved_profile_load' and e['log'] == 'latest.log')
restart_validation = next((e for e in events if e['operation'] == 'explicit_profile_validation'
                          and e['log'] == 'latest.log'), None)
assert rebuild['outcome'] == 'rebuilt' and validation['outcome'] == 'validated_matches_active'
assert restart['outcome'] == 'loaded_validated'
assert rebuild['details']['profile_integrity'] == restart['details']['profile_integrity']
assert all(not e['failureType'] for e in [rebuild, validation, restart])
if restart_validation:
    assert restart_validation['outcome'] == 'validated_matches_active' and not restart_validation['failureType']
assert all(restart['counts'][key] == 1 for key in ['profile_reads', 'profile_json_parses', 'profile_decodes'])
assert not restart['flags']['environment_rescanned'] and not restart['flags']['reports_regenerated']
assert all(restart['counts'].get(key, 0) == 0 for key in [
    'generation_snapshot_captures', 'competitive_capability_runs', 'adaptive_calibration_runs',
    'runtime_generation_runs', 'report_export_runs'])

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

protected = json.loads(baseline.read_text(encoding='utf-8'))
config_receipts = {}
# Compare only human configuration; the user's rebuild intentionally replaces authority.
for record in (protected.values() if isinstance(protected, dict) else protected):
    if not isinstance(record, dict):
        continue
    path = pathlib.Path(record.get('path', ''))
    if path.suffix != '.toml':
        continue
    config_receipts[str(path)] = dict(sha256=digest(path), unchanged=digest(path) == record['sha256'])
    assert config_receipts[str(path)]['unchanged']
assert len(config_receipts) == 4

config = game / 'config/essence_ascendance'
manifest_path = config / 'diagnostics/report_manifest.json'
manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
assert manifest['state'] == 'complete'
assert manifest['profileIntegrity'] == restart['details']['profile_integrity']
shutil.copy2(manifest_path, out / manifest_path.name)
diagnostics = {}
for name in ['competitive_capabilities.json', 'adaptive_balance.json']:
    path = config / 'diagnostics' / name
    shutil.copy2(path, out / name)
    diagnostics[name] = json.loads(path.read_text(encoding='utf-8'))
generation = json.loads((config / 'diagnostics/generation_evidence.json').read_text(encoding='utf-8'))
(out / 'native-generation-summary.json').write_text(json.dumps({key: generation[key] for key in [
    'workloads', 'providers', 'recipeReadiness', 'phaseTimingsBeforeSerializationMillis', 'limitations'
]}, indent=2), encoding='utf-8')
competitive = diagnostics['competitive_capabilities.json']
adaptive = diagnostics['adaptive_balance.json']
profile_path = config / 'generated_balance.json.gz'
profile = json.load(gzip.open(profile_path))
assert profile['schema'] == 2
assert profile['integrity'] == restart['details']['profile_integrity']
infuser = profile['runtime']['infuser']
assert all(grade['infusionThroughputPerSecond'] >= infuser['noFocusInfusionThroughputPerSecond']
           for grade in infuser['grades'].values())
summary = dict(
    status='PASS: native rebuild, separate validation and automatic restart validation',
    post_restart_command_observed=restart_validation is not None,
    post_restart_validation=restart_validation,
    schema=profile['schema'], integrity=profile['integrity'],
    profile=dict(sha256=digest(profile_path), bytes=profile_path.stat().st_size),
    installed_jar_sha256=digest(game / 'mods/essence_ascendance-neoforge.jar'),
    human_config_receipts=config_receipts,
    timings_seconds={e['operation']: e['elapsedNanos'] / 1e9 for e in [rebuild, validation, restart]},
    restart_counts=restart['counts'], restart_flags=restart['flags'],
    competitive_counts=competitive['counts'],
    candidate_samples_by_provider=dict(collections.Counter(row['provider'] for row in competitive['candidates'])),
    native_planter_candidates=[row for row in competitive['candidates'] if row['provider'] == 'botanypots_capabilities'],
    adaptive=dict(calibrationMillis=adaptive['calibrationNanos'] / 1e6,
                  rows=len(adaptive['rows']), warnings=len(adaptive['warnings']),
                  sanctuary=[{key: row[key] for key in ['band', 'baseline', 'adaptiveTarget', 'factor', 'unit', 'finalGeneratedValue']}
                             for row in adaptive['rows'] if row['feature'] == 'skill.sanctuary']),
    infuser=dict(latentThroughput=infuser['noFocusInfusionThroughputPerSecond'], grades=infuser['grades']),
    rebuild_memory=rebuild['memory'], restart_memory=restart['memory'])
(out / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print(json.dumps({key: summary[key] for key in ['status', 'post_restart_command_observed', 'timings_seconds', 'profile', 'human_config_receipts', 'competitive_counts', 'adaptive']}, indent=2))
