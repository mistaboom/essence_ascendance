import json,re,sys
from pathlib import Path
api=json.loads(Path(f'build/mixin-{sys.argv[1]}-native-api.json').read_text())
pattern=sys.argv[2];methods=sys.argv[3] if len(sys.argv)>3 else '.'
for name,data in api.items():
    if not re.search(pattern,name):continue
    print(name)
    for m in data.get('methods',[]):
        if re.search(methods,m['name']):
            print(' ',m['name']+m['desc'])
            if '--calls' in sys.argv:
                print('\n'.join('    '+c for c in m['calls']))
