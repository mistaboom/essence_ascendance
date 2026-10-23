"""Record every compiler diagnostic from normal common and full-source loader probes."""
import collections
import json
from pathlib import Path
import re
import sys

log = Path(sys.argv[1])
destination = Path(sys.argv[2])
lines = log.read_text(encoding='utf-8', errors='replace').split('FAILURE:', 1)[0].splitlines()
scope = None
records = []
reported = {}
for index, line in enumerate(lines):
    task = re.match(r'> Task :(common|fabric|neoforge):26\.1\.2:(compileJava|portAllSourceDiagnostics)$', line)
    if task:
        scope = task.group(1)
    total = re.fullmatch(r'(\d+) errors', line.strip())
    if total and scope:
        reported[scope] = int(total.group(1))
    match = re.match(r'.*[\\/](common|fabric|neoforge)[\\/]versions[\\/]26\.1\.2[\\/]build[\\/]generated[\\/]stonecutter[\\/]main[\\/]java[\\/](.+\.java):(\d+): error: (.*)', line)
    if not match:
        continue
    module, path, number, message = match.groups()
    detail = []
    for following in lines[index + 1:]:
        if ': error: ' in following or following.startswith('> Task') or re.fullmatch(r'\d+ errors', following.strip()):
            break
        if following.startswith('Note:'):
            break
        detail.append(following)
    records.append(dict(scope=scope, module=module, path=path.replace('\\', '/'), line=int(number),
                        message=message, detail='\n'.join(detail).rstrip()))

clients = {}
for module in ['common', 'fabric', 'neoforge']:
    clients[module] = set()
    for config in Path(module, 'src/main/resources').glob('*.mixins.json'):
        data = json.loads(config.read_text())
        clients[module].update(data['package'].replace('.', '/') + '/' + name + '.java' for name in data.get('client', []))
for record in records:
    path = record['path']
    if '/client/' in path or Path(path).stem.endswith('Client'):
        category = 'Client presentation'
    elif path in clients[record['module']]:
        category = 'Presentation mixin'
    elif path.endswith('/compat/jei/InfuserJeiCategory.java'):
        category = 'JEI category rendering'
    else:
        raise RuntimeError('Unclassified diagnostic requires review: ' + str(record))
    record['category'] = category
for scope, count in reported.items():
    assert sum(r['scope'] == scope for r in records) == count, (scope, count)
assert set(reported) == {'common', 'fabric', 'neoforge'}, 'All three compiler probes are required'

out = [f'''# Minecraft 26.1.2 compiler ledger

Updated 2026-10-20 from `{log.as_posix()}`. **Prompt 4: Shared/server port ready for client integration (provisional).** All production sources remain included. These are failing compiler probes, not complete builds or working 26 JARs.

The normal common task uses Java 25 and `-Xmaxerrs 10000`. Each loader diagnostic task compiles every common and loader production source together against that loader's actual dependencies. It does not consume partial common class output; it uses `-proc:none` and therefore does not validate Mixin injection. Normal loader builds remain blocked by common compilation.

## Responsibility and cause

- GUI and HUD: port `GuiGraphics` to the extraction/render-state APIs, input events, text/layout and tooltip contracts.
- Models and rendering: port entity/block-entity render states, submission collectors, model layers, item-model/atlas/sprite access, armor, shields, glaives, casters, fishing and machine visuals.
- Presentation mixins: inspect each native client target and its new locals/arguments; preserve required injections and every feature. Input/camera/overlay hooks are included here because their actual responsibilities are client presentation/control.
- JEI: the common catalog and registration API boundaries have been adapted. The remaining category compiler messages concern `GuiGraphics`; port category drawing and then verify display/search/refresh with the actual target JEI runtime.
- Loader presentation: Fabric and NeoForge client entrypoints, renderer extensions, render layers and loader client mixins need their own adapters.

No diagnostic below identifies an independent shared/server implementation failure in these passes. Missing presentation types cause downstream symbol and override errors; counts are compiler emissions, not separate defects. More errors may become visible after those causes are fixed. Required server hooks have a separate static target audit; all native runtime behavior remains an integration gate.

## Counts

| Compiler context | Source module | Category | Diagnostics |
| --- | --- | --- | ---: |
''']
counts = collections.Counter((r['scope'], r['module'], r['category']) for r in records)
for (scope, module, category), count in sorted(counts.items()):
    out.append(f'| {scope} | {module} | {category} | {count} |\n')
out.append('\nCompiler totals: ' + ', '.join(f'**{scope}: {count}**' for scope, count in reported.items()) + '.\n')
out.append('''
## Exact diagnostics grouped by compiler context and source subsystem

Locations below are exact **projected source** locations relative to `<module>/versions/26.1.2/build/generated/stonecutter/main/java/`. Edit the corresponding authored file under `<module>/src/main/java/`; never edit generated sources. Repeated common diagnostics under different loader contexts are intentionally retained, as are multiple errors at one line. The JSON companion preserves complete compiler caret/context text.
''')
groups = collections.defaultdict(list)
for record in records:
    groups[(record['scope'], record['module'], str(Path(record['path']).parent))].append(record)
for (scope, module, group), values in sorted(groups.items()):
    out.append(f'\n### {scope} compiler — {module}/{group} ({len(values)})\n\n')
    for r in values:
        clues = [line.strip() for line in r['detail'].splitlines() if line.strip().startswith(('symbol:', 'required:', 'found:', 'reason:'))]
        suffix = '; ' + '; '.join(clues) if clues else ''
        out.append(f"- `{r['path']}:{r['line']}` — {r['message']}{suffix}\n")
destination.write_text(''.join(out), encoding='utf-8')
destination.with_suffix('.json').write_text(json.dumps(dict(log=str(log), compilerTotals=reported, diagnostics=records), indent=2), encoding='utf-8')
print(json.dumps(dict(totals=reported, classification={str(k): v for k,v in counts.items()}), indent=2))
