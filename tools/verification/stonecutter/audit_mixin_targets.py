import json,re,sys,subprocess,zipfile
from pathlib import Path
root=Path.cwd()
loader=sys.argv[1]
inputs=json.loads((root/f'build/port-{loader}-inputs.json').read_text())
sources=[]
classes=set()
known=set()
for entry in inputs['classpath']:
    if entry.endswith('.jar'):
        with zipfile.ZipFile(entry) as jar:known.update(x[:-6] for x in jar.namelist() if x.endswith('.class'))
for module in ['common',loader]:
    for config in (root/module/'src/main/resources').glob('*.mixins.json'):
        data=json.loads(config.read_text())
        for mixin in data.get('mixins',[]):
            relative=data['package'].replace('.','/')+'/'+mixin+'.java'
            candidates=[
                root/module/'versions/26.1.2/build/generated/stonecutter/main/java'/relative,
                root/module/'src/26.1.2/java'/relative,
            ]
            matches=[candidate for candidate in candidates if candidate.is_file()]
            if len(matches)!=1:
                raise ValueError(f'Expected one 26.1.2 mixin source for {relative}: {matches}')
            file=matches[0]
            src=re.sub(r'("(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')|/\*.*?\*/|//[^\n]*',lambda m:m.group(1) or '',file.read_text(),flags=re.S)
            imports={x.rsplit('.',1)[-1]:x for x in re.findall(r'import ([\w.]+);',src)}
            wildcards=re.findall(r'import ([\w.]+)\.\*;',src)
            def resolve(s):
                base,*parts=s.split('.')
                if base in imports:return imports[base].replace('.','/')+(''.join('$'+p for p in parts))
                if base in ['net','com','java','org']:
                    chunks=s.split('.');split=next((i for i,c in enumerate(chunks) if c[0].isupper()),len(chunks)-1)
                    return '/'.join(chunks[:split+1])+''.join('$'+p for p in chunks[split+1:])
                for wildcard in wildcards:
                    candidate=wildcard.replace('.','/')+'/'+base+''.join('$'+p for p in parts)
                    if candidate in known:return candidate
                return base+''.join('$'+p for p in parts)
            m=re.search(r'@Mixin\((.*?)\)\s*(?:public|abstract|final|class|interface)',src,re.S)
            if not m: raise ValueError(file)
            annotation=m.group(1)
            targets=[resolve(x) for x in re.findall(r'([\w.]+)\.class',annotation)]
            targets += [x.replace('.','/') for x in re.findall(r'"([\w.$]+)"',annotation)]
            classes.update(targets)
            classes.update(re.findall(r'target\s*=\s*"L([^;]+);',src))
            sources.append((str(file.relative_to(root)),src,targets))
listfile=root/f'build/mixin-{loader}-native-classes.txt'
listfile.write_text('\n'.join(sorted(classes)))
out=root/f'build/mixin-{loader}-native-api.json'
java=Path(inputs['javaHome'])/'bin'/('java.exe' if sys.platform=='win32' else 'java')
subprocess.run([str(java),'-cp',';'.join(inputs['classpath']),str(Path(__file__).with_name('NativeApiDump.java')),str(listfile),str(out)],check=True)
api=json.loads(out.read_text())
problems=[];checks=[]
for file,src,targets in sources:
    for target in targets:
        if api.get(target,{}).get('missing'):problems.append((file,'missing class',target));continue
        methods=api[target]['methods']
        # Each injection annotation ends before its method declaration. At targets are checked
        # in the selected native methods, so a method merely existing elsewhere cannot pass.
        for m in re.finditer(r'@(?:[\w.]+\.)?(Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|WrapOperation|WrapMethod|ModifyExpressionValue|ModifyReturnValue)\s*\(',src):
            start=m.end();depth=1;quote=False;escape=False;end=start
            for end in range(start,len(src)):
                c=src[end]
                if quote:
                    if escape:escape=False
                    elif c=='\\':escape=True
                    elif c=='"':quote=False
                elif c=='"':quote=True
                elif c=='(':depth+=1
                elif c==')':
                    depth-=1
                    if depth==0:break
            ann=src[start:end]
            selector=re.search(r'\bmethod\s*=\s*(\{.*?\}|"[^"]+")',ann,re.S)
            if not selector:continue
            names=re.findall(r'"([^"]+)"',selector.group(1))
            for name in names:
                matching=[method for method in methods if method['name']+method['desc']==name or method['name']==name]
                label=f'{m.group(1)} {name}'
                checks.append((file,target,label))
                if not matching:problems.append((file,'missing method',target+'::'+name));continue
                declaration=re.search(r'\b(?:private|protected|public)\s+(?:(?:static|final|synchronized)\s+)*(?:<[^>]+>\s+)?[\w.<>?]+\s+([\w$]+)\s*\(',src[end+1:])
                if declaration and m.group(1) in ['WrapMethod','Inject','WrapOperation','Redirect','ModifyVariable','ModifyReturnValue','ModifyExpressionValue']:
                    pstart=end+1+declaration.end();pdepth=1;pend=pstart
                    for pend in range(pstart,len(src)):
                        if src[pend]=='(':pdepth+=1
                        elif src[pend]==')':
                            pdepth-=1
                            if pdepth==0:break
                    rawparams=src[pstart:pend]
                    params=rawparams
                    params=re.sub(r'@[\w.]+(?:\([^)]*\))?\s*','',params)
                    while re.search(r'<[^<>]*>',params):params=re.sub(r'<[^<>]*>','',params)
                    parts=[p.strip() for p in params.split(',') if p.strip()]
                    captured=[]
                    for part in parts:
                        typ=part.split()[0]
                        if typ.rsplit('.',1)[-1] in ['Operation','CallbackInfo','CallbackInfoReturnable']:break
                        captured.append(typ)
                    for method in (matching if m.group(1) in ['WrapMethod','Inject'] else []):
                        native=re.findall(r'\[*(?:[ZBCSIJFD]|L[^;]+;)',method['desc'].split(')')[0][1:])
                        if (m.group(1)=='WrapMethod' or captured) and len(captured)!=len(native):
                            problems.append((file,'handler argument count',label+' '+declaration.group(1)+' '+str(captured)+' versus '+method['desc']))
                        elif captured:
                            primitive={'Z':'boolean','B':'byte','C':'char','S':'short','I':'int','J':'long','F':'float','D':'double'}
                            def simple(desc):
                                if desc.startswith('['):return simple(desc[1:])+'[]'
                                return primitive.get(desc,desc.strip('L;').split('/')[-1].split('$')[-1])
                            expected=[simple(x) for x in native]
                            actual=[('Object' if x in ['T','E','K','V'] else x.split('.')[-1]) for x in captured]
                            if expected!=actual:problems.append((file,'handler argument types',label+' '+str(actual)+' versus '+str(expected)))
                    if m.group(1) in ['WrapOperation','Redirect']:
                        for at in re.findall(r'\btarget\s*=\s*"L([^;]+);([^"(]+)(\([^"\n]+)"',ann):
                            owner,called,desc=at
                            calledMethods=api.get(owner,{}).get('methods',[])
                            calledMethod=next((method for method in calledMethods if method['name']==called and method['desc']==desc),None)
                            if calledMethod is None:continue # A call may use an inherited owner; existence is checked below.
                            native=re.findall(r'\[*(?:[ZBCSIJFD]|L[^;]+;)',desc.split(')')[0][1:])
                            if not calledMethod['access'] & 8:native=['L'+owner+';']+native
                            def opSimple(d):
                                if d.startswith('['):return opSimple(d[1:])+'[]'
                                return {'Z':'boolean','B':'byte','C':'char','S':'short','I':'int','J':'long','F':'float','D':'double'}.get(d,d.strip('L;').split('/')[-1].split('$')[-1])
                            actual=[('Object' if x in ['T','E','K','V'] else x.split('.')[-1]) for x in captured[:len(native)]]
                            expected=[opSimple(d) for d in native]
                            if actual!=expected:problems.append((file,'operation argument types',label+' '+str(actual)+' versus '+str(expected)))
                    # Explicit enclosing-method captures must retain the new complete argument prefix.
                    sugar=re.sub(r'@(?:Local|Share)(?:\([^)]*\))?\s*','SUGAR ',rawparams)
                    while re.search(r'<[^<>]*>',sugar):sugar=re.sub(r'<[^<>]*>','',sugar)
                    plain=[p.strip().split()[0] for p in sugar.split(',') if p.strip() and not p.strip().startswith('SUGAR ')]
                    tail=[]
                    if m.group(1)=='WrapOperation' and 'Operation' in plain:tail=plain[plain.index('Operation')+1:]
                    elif m.group(1) in ['ModifyVariable','ModifyReturnValue','ModifyExpressionValue']:tail=plain[1:]
                    if tail:
                        def tailSimple(d):
                            if d.startswith('['):return tailSimple(d[1:])+'[]'
                            return {'Z':'boolean','B':'byte','C':'char','S':'short','I':'int','J':'long','F':'float','D':'double'}.get(d,d.strip('L;').split('/')[-1].split('$')[-1])
                        for method in matching:
                            expected=[tailSimple(d) for d in re.findall(r'\[*(?:[ZBCSIJFD]|L[^;]+;)',method['desc'].split(')')[0][1:])]
                            actual=[t.split('.')[-1] for t in tail]
                            if actual!=expected[:len(actual)]:problems.append((file,'enclosing argument types',label+' '+str(actual)+' versus '+str(expected)))
                for at in re.findall(r'\btarget\s*=\s*"(L[^"]+)"',ann):
                    kind='fields' if ':' in at.split(';',1)[1] and '(' not in at else 'calls'
                    hits=sum(method[kind].count(at) for method in matching)
                    if not hits:problems.append((file,'missing '+kind,label+' -> '+at))
        # Shadow and accessor declarations are resolved through the target's actual hierarchy.
        def hierarchy(name):
            data=api.get(name,{})
            yield data
            if data.get('super'):yield from hierarchy(data['super'])
        fields=[f for data in hierarchy(target) for f in data.get('fields',[])]
        allmethods=[f for data in hierarchy(target) for f in data.get('methods',[])]
        flat=src
        while re.search(r'<[^<>]*>',flat):flat=re.sub(r'<[^<>]*>','',flat)
        def short(typ):
            typ=typ.replace('[]','Array').split('.')[-1]
            return 'Object' if typ in ['T','E','K','V'] else typ
        def simple(desc):
            if desc.startswith('['):return simple(desc[1:])+'Array'
            return {'Z':'boolean','B':'byte','C':'char','S':'short','I':'int','J':'long','F':'float','D':'double','V':'void'}.get(desc,desc.strip('L;').split('/')[-1].split('$')[-1])
        for shadow in re.finditer(r'@Shadow\b(?:\([^)]*\))?\s+(?:@[\w.]+\s+)*(?:(?:public|private|protected|static|final|abstract)\s+)+([\w.\[\]]+)\s+(\w+)\s*([;(])',flat):
            typ,name,kind=shadow.groups()
            if kind==';':
                hits=[f for f in fields if f['name']==name and simple(f['desc'])==short(typ)]
            else:
                params=flat[shadow.end():].split(')',1)[0]
                types=[short(p.strip().split()[0]) for p in params.split(',') if p.strip()]
                hits=[m for m in allmethods if m['name']==name and simple(m['desc'].split(')')[1])==short(typ) and
                      [simple(d) for d in re.findall(r'\[*(?:[ZBCSIJFD]|L[^;]+;)',m['desc'].split(')')[0][1:])]==types]
            if not hits:problems.append((file,'unresolved shadow',target+' '+typ+' '+name))
        for accessor in re.finditer(r'@(Accessor|Invoker)\s*\(\s*"([^"\n]+)"\s*\)\s*(?:(?:public|private|protected|static|abstract)\s+)*([\w.\[\]]+)\s+\w+\(([^)]*)\)',flat):
            kind,name,typ,params=accessor.groups()
            types=[short(p.strip().split()[0]) for p in params.split(',') if p.strip()]
            if kind=='Accessor':
                expected=types[0] if types else short(typ)
                hits=[f for f in fields if f['name']==name and simple(f['desc'])==expected]
            else:
                hits=[m for m in allmethods if m['name']==name and simple(m['desc'].split(')')[1])==short(typ) and
                      [simple(d) for d in re.findall(r'\[*(?:[ZBCSIJFD]|L[^;]+;)',m['desc'].split(')')[0][1:])]==types]
            if not hits:problems.append((file,'unresolved '+kind,target+' '+typ+' '+name+' '+params))
receipt={'loader':loader,'mixins':len(sources),'injectionSelectors':len(checks),'problems':problems,'checks':checks}
(root/f'build/mixin-{loader}-target-audit.json').write_text(json.dumps(receipt,indent=2))
print(loader,len(sources),'server/common mixins;',len(checks),'injection selectors;',len(problems),'unresolved targets')
for file,kind,detail in problems:print(Path(file).name,kind,detail)
