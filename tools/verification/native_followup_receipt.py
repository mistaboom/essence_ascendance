"""Read-only native profile/log capture for the tier and fullscreen follow-up."""
import gzip
import hashlib
import json
from pathlib import Path
import sys
from compatibility_receipt import protected

out = Path(sys.argv[1]).resolve()
phase = sys.argv[2]
game = Path(r'C:\Users\rcboo\AppData\Roaming\PrismLauncher\instances\All the Mods 10- To the Sky   ATM10SKY\minecraft')
instances = game.parents[1]
out.mkdir(parents=True, exist_ok=True)
write = lambda name, value: (out / name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
# Worlds/logs remain under the user's active restart/testing; protect installed authority/config/JAR only.
identities = [record for record in protected(instances) if '\\saves\\' not in record['path']]
write('protected-' + phase + '.json', identities)
if phase == 'before':
    print(f'Captured {len(identities)} installed config/profile/JAR identities; no game writes')
    raise SystemExit(0)
baseline = json.loads((out / 'protected-before.json').read_text(encoding='utf-8'))
write('protected-comparison.json', {'unchanged': identities == baseline,
       'changes': [record for record in identities if record not in baseline], 'boundary': 'World/log files excluded during user restart'})
logs = sorted((game / 'logs').glob('2026-10-05-*.log.gz'), key=lambda p: p.stat().st_mtime)[-3:]
logs.append(game / 'logs/latest.log')
operations, warnings, commands = [], [], []
for path in logs:
    if not path.is_file(): continue
    opener = gzip.open if path.suffix == '.gz' else open
    with opener(path, 'rt', encoding='utf-8', errors='replace') as stream:
        for line in stream:
            if 'Balance performance ' in line:
                event = json.loads(line.split('Balance performance ', 1)[1])
                if event.get('event') == 'end': operations.append({'log': path.name, **event['result']})
            if '[essence_ascendance/]' in line and ('/WARN]' in line or '/ERROR]' in line): warnings.append(line.rstrip())
            if 'issued server command:' in line and '/essence' in line: commands.append(line.rstrip())
write('operations-' + phase + '.json', operations)
write('warnings-' + phase + '.json', warnings)
write('commands-' + phase + '.json', commands)
profile_path = game / 'config/essence_ascendance/generated_balance.json.gz'
with gzip.open(profile_path, 'rt', encoding='utf-8') as stream: profile = json.load(stream)
generation = profile['metadata']['generation']
write('native-summary-' + phase + '.json', {
    'schema': profile['schema'], 'integrity': profile['integrity'],
    'profile_sha256': hashlib.sha256(profile_path.read_bytes()).hexdigest(),
    'providers': generation['providers'], 'competitive_counts': generation['competitiveCapabilities']['counts'],
    'availability': generation['adaptiveBalance']['skillAvailability'],
    'bonus_tracks': {key: value for key, value in profile['runtime']['balanceProfile']['bonusTracks'].items()
                     if 'enchant' in key or 'flight' in key},
    'warnings_captured': len(warnings), 'commands': commands,
    'operations': [{key: event[key] for key in ['log', 'operation', 'outcome', 'startedAt', 'elapsedNanos', 'failureType', 'details']}
                   for event in operations]})
print(json.dumps({'phase': phase, 'profile_integrity': profile['integrity'],
     'unchanged_installed_inputs': identities == baseline,
     'operations': [(event['operation'], event['outcome'], round(event['elapsedNanos'] / 1e9, 4)) for event in operations],
     'warning_count': len(warnings)}, indent=2))
