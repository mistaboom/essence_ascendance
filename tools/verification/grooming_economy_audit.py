"""Read complete saved catalogs without treating source labels as measured farm rates.

Usage: python tools/verification/grooming_economy_audit.py --profile name=profile.json.gz
       --output <dated auxiliary directory> --evidence-status historical_comparison
Repeat --profile for a matrix. This does not generate or validate a native profile.
"""
import argparse
import collections
import gzip
import hashlib
import json
import math
from pathlib import Path

CONSTRUCTIVE_RENEWAL_DEFINITION = (
    "Conservative direct SourceAvailability proof: renewable source, confidence >= 0.5, "
    "accessProven, no uncertainty, and CONDITIONAL_RENEWABLE or REFRESHABLE category. "
    "This omits crafted descendants and some supported renewable pathways; false does "
    "not mean nonrenewable. Counts are item variants, not independent farms or rates."
)


def required(target, efficiency):
    return (target * 10000 + efficiency - 1) // efficiency


def source_row(source):
    availability = source.get("availability", {})
    return {key: source.get(key) for key in (
        "id", "kind", "stage", "expectedOutput", "renewable", "rateKnown",
        "unitsPerSecond", "confidence", "dependencies", "reason"
    )} | {"access": availability}


def constructive(source):
    availability = source.get("availability", {})
    return (source.get("renewable", False) and source.get("confidence", 0) >= .5
            and availability.get("accessProven", False)
            and not availability.get("uncertainty", [])
            and availability.get("category") in ("CONDITIONAL_RENEWABLE", "REFRESHABLE"))


def conversion_rows(infuser):
    # Installed Latent uses the Dormant denomination with its own slower,
    # less efficient startup profile. A missing focus is not a valid machine.
    profiles = [("latent", "dormant", infuser["grades"]["dormant"]["ingotCapacity"],
                 infuser["noFocusEfficiencyBasisPoints"], infuser["noFocusInfusionThroughputPerSecond"])]
    profiles += [(grade, grade, definition["ingotCapacity"], definition["efficiencyBasisPoints"],
                  definition["infusionThroughputPerSecond"]) for grade, definition in infuser["grades"].items()]
    rows = []
    for focus, grade, ingot, efficiency, rate in profiles:
        for form, numerator, denominator in (("nugget", 1, 9), ("ingot", 1, 1), ("block", 9, 1)):
            scaled = ingot * numerator
            for cross_family in (False, True):
                row = dict(installedFocus=focus, grade=grade, carrier="one " + form,
                           crossFamily=cross_family, ingotDenomination=ingot,
                           fabricationEfficiencyBasisPoints=efficiency)
                rows.append(row)
                if (scaled % denominator or not 0 < efficiency < 10000 or rate <= 0
                        or not 0 < infuser["conversionEfficiencyBasisPoints"] <= 10000):
                    row.update(available=False, reason="Native denomination, efficiency or throughput contract cannot be satisfied")
                    continue
                capacity = scaled // denominator
                source = required(capacity, efficiency)
                if cross_family:
                    source = required(source, infuser["conversionEfficiencyBasisPoints"])
                credited_micro = capacity * infuser["carrierExtractionEfficiencyBasisPoints"] * 100
                ticks = min(2147483647, max(1, (20 * capacity + rate - 1) // rate))
                row.update(available=True, storedTargetEssence=capacity, sourceEssenceRequired=source,
                           targetEssenceWithPersistentCarry=credited_micro / 1000000,
                           creditedTargetMicroUnits=credited_micro, fractionRetained=credited_micro / (source * 1000000),
                           infusionTicks=ticks, infusionThroughputPerSecond=rate,
                           caveat="Requires installed focus, matching latent carrier, machine and source essence; "
                           "farm, setup, transfer and extraction times additional; describes a new mint, not repricing stored carriers")
    return rows


def audit(label, path, output, status):
    with gzip.open(path, "rt", encoding="utf-8") if path.suffix == ".gz" else path.open(encoding="utf-8") as stream:
        profile = json.load(stream)
    resources = profile["economy"]["resources"]
    evidence = profile["evidence"]["resources"]
    rows, families, flags, measured = [], collections.defaultdict(list), [], []
    for item, resource in sorted(resources.items()):
        proof = evidence.get(item, {})
        yields = resource.get("routedYields", {})
        sources = proof.get("sources", [])
        row = dict(item=item, total=resource["dissolutionYield"]["microUnits"] / 1000000,
                   yields=yields, economicValue=resource.get("economicValue"),
                   stage=proof.get("stage"), availability=proof.get("availability"),
                   reachable=proof.get("reachable"), confidence=proof.get("confidence"),
                   constructiveRenewal=any(constructive(s) for s in sources),
                   sources=[source_row(s) for s in sources], warnings=resource.get("warnings", []))
        rows.append(row)
        if abs(sum(yields.values()) - row["total"]) > 1e-6:
            flags.append(dict(item=item, kind="routed_total_mismatch", total=row["total"], yields=yields))
        if row["total"] > 0 and not proof.get("reachable"):
            flags.append(dict(item=item, kind="positive_unreachable", total=row["total"]))
        for source in sources:
            if source.get("rateKnown"):
                measured.append(dict(item=item, source=source_row(source)))
                if not source.get("unitsPerSecond", 0) > 0:
                    flags.append(dict(item=item, kind="invalid_declared_rate", source=source_row(source)))
        for family, value in yields.items():
            if value > 0:
                families[family].append(dict(item=item, yieldPerItem=value, stage=proof.get("stage"),
                    availability=proof.get("availability"), constructiveRenewal=row["constructiveRenewal"],
                    sourceIds=[s["id"] for s in sources], inputItemsFor1000=math.ceil(1000 / value)))
    with gzip.open(output / (label + "-catalog.jsonl.gz"), "wt", encoding="utf-8") as stream:
        for row in rows:
            stream.write(json.dumps(row, separators=(",", ":")) + "\n")
    runtime = profile["runtime"]
    infuser = runtime["infuser"]
    conversion = conversion_rows(infuser)
    bonuses = []
    for track, definition in runtime["balanceProfile"]["bonusTracks"].items():
        for checkpoint in definition["checkpoints"]:
            if checkpoint.get("purchasable"):
                bonuses.append(dict(track=track, family=definition["category"].lower(), **checkpoint))
    curves = runtime["skillCurves"]
    summary = dict(label=label, evidenceStatus=status, profile=str(path.resolve()),
        profileSha256=hashlib.sha256(path.read_bytes()).hexdigest(), integrity=profile["integrity"],
        generator=profile["generator"], schema=profile["schema"],
        constructiveRenewalDefinition=CONSTRUCTIVE_RENEWAL_DEFINITION,
        caveats=["Saved-profile analysis is not fresh native acceptance or farm measurement.",
                 "Item variants and shared source IDs are not independent farms. Full alternatives are retained for investigation.",
                 "Resource item counts are quantities; no time estimate is inferred from unknown rates.",
                 "The rateKnown flag is a declaration, not independent certification of empirical farm measurement.",
                 "Full catalog purchase totals are an upper-bound inventory, not a plausible player loadout."],
        resources=len(resources), reachableZeroYield=sum(r["reachable"] and r["total"] == 0 for r in rows),
        flags=flags, declaredMeasuredSources=measured,
        familySummary={family: dict(itemVariants=len(entries),
            constructiveRenewalVariants=sum(e["constructiveRenewal"] for e in entries),
            stages=dict(collections.Counter(e["stage"] for e in entries)),
            highestYield=sorted(entries, key=lambda e: -e["yieldPerItem"])[:15]) for family, entries in families.items()},
        mobilityAlternatives=families.get("essence_ascendance:mobility", []),
        vine=next((r for r in rows if r["item"] == "minecraft:vine"), None),
        largestYields=sorted((r for r in rows if r["total"] > 0), key=lambda r: -r["total"])[:25],
        nativeConservation=dict(count=len(profile["economy"]["invariants"]),
            failed=[i for i in profile["economy"]["invariants"] if not i["passed"]]),
        bonusPurchases=bonuses,
        skillCosts={skill: dict(tier=curve["requiredTierId"],
            marginal=[r["cost"] for r in curve["ranks"]], cumulative=sum(r["cost"] for r in curve["ranks"]))
            for skill, curve in curves.items()},
        conversion=conversion, crucible=runtime["crucible"],
        equipmentCosts=infuser["equipmentUpgrades"], equipmentWeights=infuser["equipmentEssenceWeights"],
        focusCosts=infuser["focusUpgrades"], processingPolicy=profile["economy"]["processingPolicy"])
    (output / (label + "-economy.json")).write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return dict(label=label, resources=len(resources), flags=len(flags),
        declaredMeasuredSources=len(measured), conservationFailures=len(summary["nativeConservation"]["failed"]),
        mobilityVariants=len(summary["mobilityAlternatives"]),
        constructiveMobilityVariants=sum(r["constructiveRenewal"] for r in summary["mobilityAlternatives"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", action="append", required=True, help="environment=profile.json.gz")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--evidence-status", required=True,
                        choices=("historical_comparison", "fresh_candidate", "accepted_candidate"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    results = []
    for specification in args.profile:
        label, path = specification.split("=", 1)
        if not label or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_" for c in label):
            parser.error("Environment labels may contain only letters, numbers, hyphens and underscores")
        results.append(audit(label, Path(path), args.output, args.evidence_status))
    (args.output / "matrix.json").write_text(json.dumps(dict(evidenceStatus=args.evidence_status, results=results), indent=2), encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
