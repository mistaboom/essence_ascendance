"""Join preserved reference and native generation receipts without touching live inputs.

Usage: python tools/verification/procedural_native_receipt.py BEFORE_AUDIT NATIVE_AUDIT NATIVE_PROFILE OUTPUT
BEFORE defaults to a bootstrap reference. Pass --reference-kind native for a preserved native profile/audit.
"""
from pathlib import Path
import argparse, collections, csv, gzip, hashlib, json


def read(path):
    data = Path(path).read_bytes()
    return json.loads(gzip.decompress(data) if str(path).endswith('.gz') else data)


def identity(path):
    p = Path(path).resolve()
    return dict(path=str(p), bytes=p.stat().st_size, sha256=hashlib.sha256(p.read_bytes()).hexdigest())


def emit(out, name, value):
    (out / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def csv_file(out, name, rows):
    with (out / name).open('w', newline='', encoding='utf-8-sig') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        for row in rows:
            writer.writerow({k: json.dumps(v, ensure_ascii=False) if isinstance(v, (dict, list)) else v for k, v in row.items()})


def main(before_path, audit_path, native_path, output, reference_kind='bootstrap'):
    before, audit, native = map(read, (before_path, audit_path, native_path))
    is_native = reference_kind == 'native'
    if is_native:
        assert 'nativeGeneration' in before or 'generation' in before.get('metadata', {}), 'Native reference requires native generation evidence'
    reference_label = 'preserved native development profile' if is_native else 'preserved pre-change bootstrap'
    comparison_scope = ('Preserved native development profile before versus native after; same isolated fixture; not gameplay acceptance'
                        if is_native else 'Pre-change bootstrap reference versus isolated native development profile; not a controlled native before/after or gameplay acceptance')
    out = Path(output).resolve(); out.mkdir(parents=True, exist_ok=True)
    generation = native['metadata']['generation']
    decisions = {row['skill']: row for row in generation['adaptiveBalance']['skillAvailability']}
    curves = native['runtime']['skillCurves']; old = before['runtime']['skillCurves']
    assert set(decisions) == set(curves) == {s['skill'] for s in audit['skills']}
    assert audit['runtime']['skillCurves'] == curves, 'The inspected audit and native saved profile disagree'
    rows, ranks = [], []
    tiers = ['latent', 'dormant', 'awakened', 'resonant', 'ascendant', 'transcendent']
    for skill in audit['skills']:
        key = skill['skill']; curve = curves[key]; reference = old.get(key); decision = decisions[key]
        prior = reference.get('requiredTierId', skill['catalogTier']) if reference else None
        movement = 'new' if prior is None else 'earlier' if tiers.index(curve['requiredTierId'].split(':')[1]) < tiers.index(prior.split(':')[1]) else 'later' if tiers.index(curve['requiredTierId'].split(':')[1]) > tiers.index(prior.split(':')[1]) else 'same'
        rows.append(dict(skill=key, category=skill['category'], referenceTier=prior, nativeTier=curve['requiredTierId'], movement=movement,
                         referencePrices=[r['cost'] for r in reference['ranks']] if reference else [],
                         nativePrices=[r['cost'] for r in curve['ranks']], referenceRankCount=reference['maximumRank'] if reference else 0,
                         nativeRankCount=curve['maximumRank'], decisionReason=decision['decisionReason'],
                         decision=decision['decision'], intrinsicPolicy=decision['intrinsicDecision'],
                         operatingAccess=decision['operatingAccess'], externalEvidenceStatus=decision['evidenceStatus'],
                         evidenceGaps=decision['evidenceGaps'], nativeAlternatives=decision['admittedCompetitionSamples'],
                         rejectedAlternatives=decision['rejectedEarlierSamples'], prerequisiteClamps=decision['prerequisiteClamps'],
                         mechanics=decision['mechanics'], semantics=decision['semantics'], implementation=decision['implementation'],
                         comparisonScope=comparison_scope))
        assert len(decision['rankDecisions']) == len(curve['ranks'])
        for rank, detail in zip(curve['ranks'], decision['rankDecisions']):
            assert rank['rank'] == detail['rank'] and rank['cost'] == detail['publishedPrice']
            assert isinstance(rank['cost'], int) and rank['cost'] > 0
            ranks.append(dict(skill=key, category=skill['category'], referencePrice=reference['ranks'][rank['rank']-1]['cost']
                              if reference and len(reference['ranks']) >= rank['rank'] else None, **detail))
    summary = dict(scope='Native isolated development-server evidence with eight matching development mod jars; not pure vanilla.',
                   comparisonScope=comparison_scope,
                   profileIntegrity=native['integrity'], schema=native['schema'], generatorRevision=native['metadata']['generatorRevision'],
                   inputs=dict(reference=identity(before_path), audit=identity(audit_path), nativeProfile=identity(native_path)),
                   skills=len(rows), ranks=len(ranks), categories=len(audit['treeLayouts']),
                   movement=dict(collections.Counter(r['movement'] for r in rows)),
                   nativeTiers=dict(collections.Counter(r['nativeTier'] for r in rows)),
                   evidenceStatus=dict(collections.Counter(r['externalEvidenceStatus'] for r in rows)),
                   reasons=dict(collections.Counter(r['decisionReason'] for r in rows)),
                   policyCoverage='Every dynamically registered skill and published rank participates. This does not certify exhaustive vanilla alternative acquisition or survival pace.',
                   treeScope='Saved curves loaded in the shared layout algorithm: count, uniqueness and rectangle overlap checks. No screenshot, clipping, tooltip or player-input acceptance.')
    emit(out, 'comparison-summary.json', summary)
    emit(out, 'all-skill-comparison.json', rows); csv_file(out, 'all-skill-comparison.csv', rows)
    emit(out, 'all-rank-comparison.json', ranks); csv_file(out, 'all-rank-comparison.csv', ranks)
    emit(out, 'native-tree-layouts.json', audit['treeLayouts'])
    emit(out, 'native-full-decisions.json', list(decisions.values()))
    lines = ['# Procedural skill comparison', '', summary['scope'], '',
             '**Reference:** ' + reference_label + '. **After:** saved native profile `' + native['integrity'] + '`.', '',
             'The table covers all changes in this iteration, rather than attributing changes to one feature. Native acquisition gaps remain explicit in the JSON and CSV.', '',
             f"{len(rows)} registered skills; {len(ranks)} published ranks; {len(audit['treeLayouts'])} category trees.", '',
             '| Skill | Reference → native tier | Reference → native prices | Reason |', '|---|---|---|---|']
    for row in rows:
        lines.append(f"| {row['skill'].split(':')[1]} | {row['referenceTier'].split(':')[1] if row['referenceTier'] else 'new'} → {row['nativeTier'].split(':')[1]} | {row['referencePrices']} → {row['nativePrices']} | {row['decisionReason']} |")
    (out / 'all-skill-comparison.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('before_path', 'audit_path', 'native_path', 'output'):
        parser.add_argument(name)
    parser.add_argument('--reference-kind', choices=('bootstrap', 'native'), default='bootstrap')
    main(**vars(parser.parse_args()))
