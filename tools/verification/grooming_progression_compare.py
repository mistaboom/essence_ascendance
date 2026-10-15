"""Compare exhaustive saved-profile progression audits without claiming live gameplay coverage."""
import argparse
import csv
import gzip
import hashlib
import json
from collections import Counter
from pathlib import Path


def read(path):
    data = path.read_bytes()
    return json.loads(gzip.decompress(data) if path.suffix == ".gz" else data)


def verified(audit_path, profile_path):
    audit, profile = read(audit_path), read(profile_path)
    assert audit["runtime"] == profile["runtime"], f"Runtime mismatch: {audit_path}"
    assert f"integrity={profile['integrity']}" in audit["scope"], f"Integrity mismatch: {audit_path}"
    summary = audit["coverageSummary"]
    assert len(audit["skillRankCoverage"]) == summary["purchasedSkillRanks"]
    assert len({r["skill"] for r in audit["skillRankCoverage"]}) == summary["registeredSkills"]
    assert len(audit["bonusTierCoverage"]) == summary["allBonusTierRows"]
    assert len({r["bonus"] for r in audit["bonusTierCoverage"]}) == summary["registeredBonuses"]
    return audit, {"audit": str(audit_path.resolve()), "profile": str(profile_path.resolve()),
                   "profileSha256": hashlib.sha256(profile_path.read_bytes()).hexdigest(),
                   "integrity": profile["integrity"], "coverage": summary}


def comparison(before, after, section, identity):
    left = {tuple(r[k] for k in identity): r for r in before[section]}
    right = {tuple(r[k] for k in identity): r for r in after[section]}
    assert len(left) == len(before[section]) and len(right) == len(after[section]), "Duplicate state"
    rows = []
    for key in sorted(left.keys() | right.keys()):
        old, new = left.get(key), right.get(key)
        fields = sorted(k for k in (old or {}).keys() | (new or {}).keys()
                        if (old or {}).get(k) != (new or {}).get(k))
        status = "added" if old is None else "removed" if new is None else "changed" if fields else "unchanged"
        row = {**dict(zip(identity, key)), "status": status, "changedFields": fields, "before": old, "after": new}
        row["numericDeltas"] = {k: new[k] - old[k] for k in fields
                                if old is not None and new is not None
                                and type(old.get(k)) in (int, float) and type(new.get(k)) in (int, float)}
        rows.append(row)
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("baseline-audit", "baseline-profile", "candidate-audit", "candidate-profile", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    before, baseline = verified(args.baseline_audit, args.baseline_profile)
    after, candidate = verified(args.candidate_audit, args.candidate_profile)
    skills = comparison(before, after, "skillRankCoverage", ("skill", "rank"))
    bonuses = comparison(before, after, "bonusTierCoverage", ("bonus", "tier"))
    result = {"scope": "Every registry-derived saved rank and tier row. Costs, prerequisites, declared outcomes and named native probes are retained verbatim. Pure probes are not live pack gameplay evidence.",
              "baseline": baseline, "candidate": candidate,
              "summary": {"skillRanks": dict(Counter(r["status"] for r in skills)),
                          "bonusTiers": dict(Counter(r["status"] for r in bonuses))},
              "skillRankComparison": skills, "bonusTierComparison": bonuses}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix(".json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    with args.output.with_suffix(".csv").open("w", newline="", encoding="utf-8-sig") as stream:
        writer = csv.DictWriter(stream, fieldnames=["kind", "id", "state", "status", "changedFields",
                "beforeCost", "afterCost", "beforeCumulativeCost", "afterCumulativeCost",
                "beforeNativeFloors", "afterNativeFloors", "beforeOutcomes", "afterOutcomes",
                "beforeNativeProbes", "afterNativeProbes"])
        writer.writeheader()
        for kind, rows, id_key, state_key, cost_key in (("skill", skills, "skill", "rank", "cost"),
                                                       ("bonus", bonuses, "bonus", "tier", "segmentCost")):
            for row in rows:
                out = {"kind": kind, "id": row[id_key], "state": row[state_key], "status": row["status"],
                       "changedFields": ";".join(row["changedFields"])}
                for prefix in ("before", "after"):
                    state = row[prefix] or {}
                    out[prefix + "Cost"] = state.get(cost_key)
                    out[prefix + "CumulativeCost"] = state.get("cumulativeCost")
                    out[prefix + "NativeFloors"] = state.get("nativeOutcomeFloorsSatisfied")
                    out[prefix + "Outcomes"] = json.dumps(state.get("outcomes", {k: state.get(k)
                        for k in ("completedEffect", "previousCompletedEffect", "increment", "requiredIncrement", "unit")}))
                    out[prefix + "NativeProbes"] = json.dumps(state.get("nativeOutcomeProbes"))
                writer.writerow(out)
    print(json.dumps(result["summary"]))


if __name__ == "__main__":
    main()
