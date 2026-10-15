"""Compact saved-profile source/conservation/capacity trace; no native access assertion."""
import argparse
import collections
import gzip
import json
import math
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--item", action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with gzip.open(args.profile, "rt", encoding="utf-8") as stream:
        profile = json.load(stream)
    runtime = profile["runtime"]
    base = runtime["crucible"]["reservoirCapacity"]
    capacities = {tier: base + runtime["maxActivePylons"] * values["reservoirCapacityBonus"]
                  for tier, values in runtime["pylons"].items()}
    max_capacity = max(capacities.values(), default=base)
    producers = collections.defaultdict(list)
    by_id = {}
    for process in profile["economy"]["processes"]:
        by_id[process["id"]] = process
        for output in process["outputs"]:
            producers[output["itemId"]].append(process)
    resources, evidence = profile["economy"]["resources"], profile["evidence"]["resources"]
    pending, seen, rows = [(item, 0) for item in args.item], set(), []
    while pending and len(seen) < 96:
        item, depth = pending.pop(0)
        if item in seen or item not in resources:
            continue
        seen.add(item)
        resource, proof = resources[item], evidence.get(item, {})
        total = resource["dissolutionYield"]["microUnits"] / 1000000
        sources = proof.get("sources", [])
        by_kind = collections.defaultdict(list)
        for source in sources:
            by_kind[source["kind"]].append(source)
        representative = []
        for kind, choices in sorted(by_kind.items()):
            choices.sort(key=lambda s: (-bool(s.get("availability", {}).get("accessProven")), -s.get("confidence", 0), s["id"]))
            for source in choices[:2]:
                representative.append({k: source[k] for k in ("id", "kind", "stage", "renewable", "confidence", "dependencies", "reason", "availability") if k in source})
        processes = []
        for process in producers.get(item, []):
            if len(processes) >= 24:
                break
            processes.append(dict(id=process["id"], family=process["family"], inputs=process["inputs"], outputs=process["outputs"], metadata=process["metadata"]))
            if depth < 3:
                for ingredient in process["inputs"]:
                    for alternative in ingredient["alternatives"][:8]:
                        if len(pending) < 256:
                            pending.append((alternative, depth + 1))
        rows.append(dict(item=item, traceDepth=depth, totalYield=total, routedYields=resource["routedYields"],
            economicValue=resource["economicValue"], reachable=proof.get("reachable"), stage=proof.get("stage"),
            availability=proof.get("availability"), warnings=resource.get("warnings", []),
            sourceCount=len(sources), sourceKinds={kind: len(values) for kind, values in by_kind.items()},
            representativeSources=representative, producerCount=len(producers.get(item, [])), producers=processes,
            fitsSoloBase=total <= base, fitsSoloMaximum=total <= max_capacity,
            minimumMembersAtMaximumStructure=math.ceil(total / max_capacity)))
    result = dict(profile=str(args.profile.resolve()), integrity=profile["integrity"],
        soloBaseCapacity=base, soloCapacityAtMaximumPylonsByTier=capacities,
        capacityContract="Atomic full-item payout must fit available local space. Structure bonus is additive; runtime MachineCapacity multiplies by bound membership count. These are empty solo capacities, not acquisition claims.",
        traceContract="Breadth-first producer/input trace: depth3,96items,24producerrows,8alternatives/input,256pending. Truncation means unknown, never absent. Representative sources omit duplicates; original profile remains authority.",
        truncated=bool(pending), items=rows)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(dict(items=len(rows), output=str(args.output.resolve()), capacity=max_capacity)))


if __name__ == "__main__":
    main()
