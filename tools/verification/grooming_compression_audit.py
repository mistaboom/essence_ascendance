"""Trace exact loaded reversible compression chains and the shared whole/micro bound."""
import argparse
import gzip
import json
from pathlib import Path


def matches(process, source, target, consumed, produced):
    inputs, outputs = process["inputs"], process["outputs"]
    return (process["metadata"].get("conservation_complete") == "true"
            and inputs and all(i["consumed"] and i["alternatives"] == [source] for i in inputs)
            and sum(i["count"] for i in inputs) == consumed
            and len(outputs) == 1 and outputs[0]["itemId"] == target
            and outputs[0]["count"] == produced and outputs[0]["probability"] == 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with gzip.open(args.profile, "rt", encoding="utf-8") as stream:
        profile = json.load(stream)
    producers = {}
    for process in profile["economy"]["processes"]:
        for output in process["outputs"]:
            producers.setdefault(output["itemId"], []).append(process)
    rows = []
    for family, base in [("iron", ["minecraft:iron_nugget", "minecraft:iron_ingot", "minecraft:iron_block"]),
                         ("coal", ["minecraft:coal", "minecraft:coal_block"]),
                         ("copper", ["minecraft:copper_ingot", "minecraft:copper_block"]),
                         ("wheat", ["minecraft:wheat", "minecraft:hay_block"])]:
        compressed_name = base[-1].split(":")[1]
        chain = base + [f"allthecompressed:{compressed_name}_{n}x" for n in range(1, 10)]
        edges = []
        for source, target in zip(chain, chain[1:]):
            forward = sorted(p["id"] for p in producers.get(target, []) if matches(p, source, target, 9, 1))
            reverse = sorted(p["id"] for p in producers.get(source, []) if matches(p, target, source, 1, 9))
            edges.append(dict(source=source, target=target, ratio=9, forward=forward, reverse=reverse,
                              exactLoadedPair=bool(forward and reverse)))
        values = []
        for index, item in enumerate(chain):
            value = profile["economy"]["resources"].get(item)
            evidence = profile["evidence"]["resources"].get(item, {})
            values.append(dict(item=item, primitiveUnits=9 ** index,
                              economicValue=value["economicValue"] if value else None,
                              wholeYield=value["dissolutionYield"]["microUnits"] // 1_000_000 if value else None,
                              reachable=evidence.get("reachable"), stage=evidence.get("stage")))
        rows.append(dict(family=family, allPairsProven=all(e["exactLoadedPair"] for e in edges),
                         maximumPrimitiveUnits=9 ** (len(chain) - 1), edges=edges, forms=values))
    limit = (2 ** 63 - 1) // 1_000_000
    report = dict(profile=str(args.profile.resolve()), integrity=profile["integrity"],
                  previousValuationBound=2_000_000_000, exactWholeMicroBound=limit,
                  contract="Exact deterministic loaded process pairs only. Missing pairs remain unproved; chain ratios do not establish practical acquisition quantities or farming rates. Prior valuation rejected whole families above its arbitrary ratio bound. This report does not itself regenerate the candidate.",
                  families=rows)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(dict(output=str(args.output.resolve()), families=[
        dict(family=r["family"], exact=r["allPairsProven"], maximumUnits=r["maximumPrimitiveUnits"])
        for r in rows])))


if __name__ == "__main__":
    main()
