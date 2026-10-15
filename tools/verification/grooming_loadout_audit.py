"""Plausible early purchase bundles from a saved profile and matching expanded skill audit.

python tools/verification/grooming_loadout_audit.py --profile profile.json.gz
    --skill-audit coverage.json --output <dated auxiliary directory>
This computes quantities and machine-only bounds, never measured farm times.
"""
import argparse
import collections
import gzip
import hashlib
import json
import math
import sys
from pathlib import Path
sys.dont_write_bytecode = True
from grooming_economy_audit import constructive, CONSTRUCTIVE_RENEWAL_DEFINITION

PREFIX = "essence_ascendance:"
TIERS = ["latent", "dormant", "awakened", "resonant", "ascendant", "transcendent"]
STAGES = ["ENTRY", "EARLY", "MID", "LATE", "APEX"]
FAMILIES = [PREFIX + f for f in ("offense", "defense", "vitality", "mobility", "gathering", "utility")]
SCENARIOS = {
    "gatherer": dict(skills=["tool_instinct", "waylight", "mining_momentum"],
                     bonuses=["mining_speed", "durability_efficiency"], gear=["pickaxe", "axe"]),
    "explorer": dict(skills=["running_momentum", "impact_control", "waylight"],
                     bonuses=["movement_speed", "step_height"], gear=["boots", "leggings"]),
    "fighter": dict(skills=["frenzy", "kindling", "life_steal"],
                    bonuses=["melee_damage", "max_health"], gear=["melee_weapon", "chestplate"]),
}
PORTFOLIO = ["bamboo", "wheat", "sugar_cane", "oak_log", "feather", "leather", "string", "bone",
             "rotten_flesh", "iron_ingot", "copper_ingot", "coal", "slime_ball", "ender_pearl", "vine"]


def read(path):
    with gzip.open(path, "rt", encoding="utf-8") if path.suffix == ".gz" else path.open(encoding="utf-8") as stream:
        return json.load(stream)


def tier_index(tier):
    return TIERS.index(tier.split(":")[-1])


def stage_allowed(stage, tier):
    return stage in STAGES and STAGES.index(stage) <= tier_index(tier) - 1


def cost_sum(rows):
    result = collections.Counter()
    for row in rows:
        result.update(row)
    return dict(sorted(result.items()))


def skill_closure(targets, target_tier, rank_rows):
    selected, excluded, visiting = {}, [], set()

    def include(skill, rank):
        key = (skill, rank)
        if key in selected:
            return
        if key in visiting:
            raise ValueError("Cyclic skill prerequisite: " + str(key))
        row = rank_rows.get(key)
        if row is None or tier_index(row["requiredTier"]) > tier_index(target_tier):
            raise ValueError("Skill rank not available by " + target_tier + ": " + str(key))
        visiting.add(key)
        if rank > 1:
            include(skill, rank - 1)
        for dependency, needed in row["prerequisites"].items():
            include(dependency, needed)
        visiting.remove(key)
        selected[key] = row

    for target in targets:
        snapshot = selected.copy()
        try:
            available_ranks = [rank for (skill, rank), row in rank_rows.items() if skill == PREFIX + target
                               and tier_index(row["requiredTier"]) <= tier_index(target_tier)]
            # First checkpoint is a small starter selection. Later checkpoints
            # train the same role skills to their maximum available rank.
            rank = max(available_ranks, default=1) if target_tier != "dormant" else 1
            include(PREFIX + target, rank)
        except ValueError as error:
            selected = snapshot
            visiting.clear()
            excluded.append(str(error))
    groups = collections.defaultdict(set)
    replaced = {row["replacementTarget"] for row in selected.values() if row.get("replacementTarget")}
    for row in selected.values():
        if row.get("choiceGroup") and row["skill"] not in replaced:
            groups[row["choiceGroup"]].add(row["skill"])
    if any(len(skills) > 1 for skills in groups.values()):
        raise ValueError("Scenario selects incompatible active skills: " + str(dict(groups)))
    rows = [{key: row[key] for key in ("skill", "rank", "essence", "cost", "cumulativeCost", "requiredTier",
            "prerequisites", "requirements", "activationPolicy", "choiceGroup", "replacementTarget") if key in row}
            for _, row in sorted(selected.items())]
    return rows, excluded


def equipment_bill(infuser, gear, target):
    rows = []
    for tier in TIERS[1:tier_index(target) + 1]:
        settings = infuser["equipmentUpgrades"][tier]
        for item in gear:
            weights = infuser["equipmentEssenceWeights"][item]
            total, assigned, requirements = settings["totalEssenceRequired"], 0, {}
            for index, (family, weight) in enumerate(weights.items()):
                value = total - assigned if index == len(weights) - 1 else total * weight // sum(weights.values())
                requirements[PREFIX + family] = value
                assigned += value
            rows.append(dict(item=item, targetTier=tier, requirements=requirements, total=total,
                             matrixCount=settings["matrixCount"], weightOrder=list(weights),
                             rounding="Native recipe floors each weight in saved runtime iteration order; final family receives remainder"))
    return rows


def source_status(proof, tier=None):
    renewable = [s for s in proof.get("sources", []) if constructive(s)]
    if any(tier is None or stage_allowed(s.get("stage"), tier) for s in renewable):
        return "conditional_renewable_direct_proof"
    if renewable:
        return "renewal_proof_later_or_unknown_stage_opportunity_only"
    categories = {s.get("availability", {}).get("category") for s in proof.get("sources", [])}
    if categories.intersection({"FINITE_SHARED", "FINITE_PERSONALIZED"}) or proof.get("availability") == "FINITE":
        return "finite_or_exploration_opportunity_quantity_unproved"
    return "renewal_or_setup_not_proved_by_direct_source_contract"


def candidate(item, family, demand, profile, tier):
    resource, proof = profile["economy"]["resources"][item], profile["evidence"]["resources"][item]
    payout = resource["routedYields"].get(family, 0)
    count = math.ceil(demand / payout)
    crucible = profile["runtime"]["crucible"]
    sources = proof.get("sources", [])
    return dict(item=item, perItem=payout, requiredItems=count, stacksOf64Equivalent=count / 64,
        stage=proof["stage"], availability=proof["availability"], sourceStatus=source_status(proof, tier),
        constructiveSourceStages=sorted({s.get("stage", "UNKNOWN") for s in sources if constructive(s)}),
        scenarioCompatibleConstructiveSources=sorted({s["id"] for s in sources
            if constructive(s) and stage_allowed(s.get("stage"), tier)}),
        sourceIds=sorted({s["id"] for s in sources}), allFamilyCredits={f: count * v for f, v in resource["routedYields"].items()},
        dissolutionSecondsLowerBound=math.ceil(count / crucible["simultaneousItemProcesses"]) * crucible["dissolutionTicksPerItem"] / 20,
        oneItemFitsBaseCrucible=sum(resource["routedYields"].values()) <= crucible["reservoirCapacity"],
        collectionSeconds=None, declaredSourceRates=[dict(source=s["id"], unitsPerSecond=s["unitsPerSecond"])
            for s in sources if s.get("rateKnown")])


def route_inventory(profile):
    evidence, resources = profile["evidence"]["resources"], profile["economy"]["resources"]
    producers = collections.defaultdict(list)
    for process in profile["economy"]["processes"]:
        for output in process["outputs"]:
            producers[output["itemId"]].append(process)
    groups, variants = collections.defaultdict(set), []
    for item, resource in resources.items():
        if resource["routedYields"].get(PREFIX + "mobility", 0) <= 0:
            continue
        routes, pending, seen, truncated = set(), [(item, 0)], set(), False
        while pending:
            dependency, depth = pending.pop()
            if dependency in seen:
                continue
            if len(seen) >= 128 or depth > 8:
                truncated = True
                continue
            seen.add(dependency)
            for source in evidence.get(dependency, {}).get("sources", []):
                routes.add("source/" + source["id"])
            for process in producers.get(dependency, []):
                routes.add("process/" + process["id"])
                for ingredient in process["inputs"]:
                    for alternative in ingredient["alternatives"]:
                        if len(pending) + len(seen) >= 256:
                            truncated = True
                            break
                        pending.append((alternative, depth + 1))
        for route in routes:
            groups[route].add(item)
        variants.append(dict(item=item, stage=evidence.get(item, {}).get("stage"), mobility=resource["routedYields"][PREFIX + "mobility"],
                             directSourceStatus=source_status(evidence.get(item, {})), routeIds=sorted(routes), lineageTruncated=truncated))
    return dict(semantics="Overlapping exact source/process IDs and all published input alternatives; lineage is not a chosen joint bill. "
                "Shared route groups are not independent farms and must never be added as supply. Depth 8, 128 visited nodes and 256 queued/visited nodes per variant; truncation explicit.",
                variants=variants, groups=[dict(route=route, variants=sorted(items)) for route, items in sorted(groups.items())])


def basket(fixed, flexible, profile, conditional_only, tier):
    """Illustrative paid item counts with every routed family credit retained once."""
    remaining = dict(fixed)
    paid, credits = collections.Counter(), collections.Counter()
    eligible = []
    for short in PORTFOLIO:
        item = "minecraft:" + short
        proof = profile["evidence"]["resources"].get(item, {})
        resource = profile["economy"]["resources"].get(item, {})
        if (proof.get("reachable") and stage_allowed(proof.get("stage"), tier) and resource.get("routedYields")
                and (not conditional_only or source_status(proof, tier) == "conditional_renewable_direct_proof")):
            eligible.append((item, resource["routedYields"]))
    for _ in range(24):
        positive = {family: amount for family, amount in remaining.items() if amount > 0}
        if not positive:
            break
        choices = [(sum(min(yields.get(f, 0), amount) for f, amount in positive.items()), item, yields) for item, yields in eligible]
        score, item, yields = max(choices, default=(0, "", {}), key=lambda x: (x[0], x[1]))
        if score <= 0:
            break
        count = min(math.ceil(amount / yields[f]) for f, amount in positive.items() if yields.get(f, 0) > 0)
        paid[item] += count
        for family, value in yields.items():
            credits[family] += count * value
            remaining[family] = remaining.get(family, 0) - count * value
    spare = sum(max(0, credits[f] - fixed.get(f, 0)) for f in FAMILIES)
    if flexible > spare and eligible:
        item, yields = max(eligible, key=lambda pair: (sum(v for f, v in pair[1].items() if f in FAMILIES), pair[0]))
        count = math.ceil((flexible - spare) / sum(v for f, v in yields.items() if f in FAMILIES))
        paid[item] += count
        for family, value in yields.items():
            credits[family] += count * value
    crucible = profile["runtime"]["crucible"]
    return dict(policy="Illustrative common-material portfolio; quantity accounting, not optimized farming or a joint acquisition witness.",
                conditionalRenewableDirectSourcesOnly=conditional_only,
                items=[dict(item=i, count=n, stacksOf64Equivalent=n / 64, sourceStatus=source_status(profile["evidence"]["resources"][i], tier)) for i, n in sorted(paid.items())],
                routedCredits=dict(credits), unresolvedFixed={f: amount - credits[f] for f, amount in fixed.items() if amount > credits[f]},
                flexibleCoreRemainder=flexible,
                unpaidFlexibleCore=max(0, flexible - sum(max(0, credits[f] - fixed.get(f, 0)) for f in FAMILIES)),
                totalItems=sum(paid.values()), collectionSeconds=None,
                dissolutionSecondsLowerBound=math.ceil(sum(paid.values()) / crucible["simultaneousItemProcesses"]) * crucible["dissolutionTicksPerItem"] / 20,
                allRoutedCreditsTransferSecondsAtBaseRate=sum(credits.values()) / crucible["transferRatePerSecond"],
                acquisitionQuantityFeasibility="Not established: profile opportunities contain no player finite stock, complete farm setup, or operating rates")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", required=True, type=Path)
    parser.add_argument("--skill-audit", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    profile, coverage = read(args.profile), read(args.skill_audit)
    if profile["integrity"] not in coverage.get("scope", "") or coverage["runtime"] != profile["runtime"]:
        parser.error("Skill audit must reference this exact saved profile integrity and runtime")
    if not coverage.get("skillRankCoverage") or not coverage.get("bonusTierCoverage"):
        parser.error("Expanded registry rank/choice/prerequisite and Bonus endpoint coverage is required")
    args.output.mkdir(parents=True, exist_ok=True)
    ranks = {(r["skill"], r["rank"]): r for r in coverage["skillRankCoverage"]}
    runtime = profile["runtime"]
    infuser = runtime["infuser"]
    scenarios = []
    candidates_path = args.output / "loadout-source-quantities.jsonl.gz"
    with gzip.open(candidates_path, "wt", encoding="utf-8") as candidates:
        for tier in TIERS[1:]:
            for role, specification in SCENARIOS.items():
                skills, exclusions = skill_closure(specification["skills"], tier, ranks)
                bonuses = [r for r in coverage["bonusTierCoverage"] if r["bonus"] in {PREFIX + b for b in specification["bonuses"]}
                           and r["tier"] == PREFIX + tier and r["available"] and r["cumulativeCost"] > 0]
                missing_bonuses = [b for b in specification["bonuses"] if not any(r["bonus"] == PREFIX + b for r in bonuses)]
                for gear_count, add_focus in ((1, False), (2, False), (2, True)):
                    label = f"{tier}-{role}-{gear_count}gear" + ("-focus" if add_focus else "")
                    gear = equipment_bill(infuser, specification["gear"][:gear_count], tier)
                    focus = [dict(tier=t, **infuser["focusUpgrades"][t]) for t in TIERS[1:tier_index(tier) + 1]] if add_focus else []
                    fixed = cost_sum([{r["essence"]: r["cost"]} for r in skills]
                                     + [{r["essence"]: r["cumulativeCost"]} for r in bonuses]
                                     + [r["requirements"] for r in gear]
                                     + [{f: r["minimumPerAttributeEssence"] for f in FAMILIES} for r in focus])
                    flexible = sum(r["totalEssenceRequired"] - 6 * r["minimumPerAttributeEssence"] for r in focus)
                    alternatives = {}
                    for family, demand in fixed.items():
                        rows = []
                        for item, resource in profile["economy"]["resources"].items():
                            proof = profile["evidence"]["resources"].get(item, {})
                            if resource["routedYields"].get(family, 0) <= 0 or not proof.get("reachable"):
                                continue
                            row = candidate(item, family, demand, profile, tier)
                            row.update(scenario=label, family=family, fixedFamilyDemand=demand)
                            candidates.write(json.dumps(row, separators=(",", ":")) + "\n")
                            if stage_allowed(proof.get("stage"), tier):
                                rows.append(row)
                        rows.sort(key=lambda r: (r["sourceStatus"] != "conditional_renewable_direct_proof", not r["oneItemFitsBaseCrucible"], r["requiredItems"], r["item"]))
                        alternatives[family] = rows[:8] + [r for r in rows[8:] if r["item"] in {"minecraft:vine", "minecraft:bamboo"}]
                    infusion_total = sum(r["total"] for r in gear) + sum(r["totalEssenceRequired"] for r in focus)
                    scenarios.append(dict(id=label, tier=tier, skills=skills, deferredSkills=exclusions,
                        bonuses=[{key: row[key] for key in ("bonus", "essence", "tier", "purchasable", "segmentCost", "cumulativeCost", "completedEffect", "unit")} for row in bonuses],
                        unavailableBonuses=missing_bonuses, equipment=gear, focus=focus, fixedFamilyDemand=fixed,
                        flexibleCoreDemand=flexible, totalEssenceDemand=sum(fixed.values()) + flexible,
                        infusionOnlySecondsAtLatentThroughput=infusion_total / infuser["noFocusInfusionThroughputPerSecond"],
                        infusionOnlySecondsAtTargetFocusThroughput=infusion_total / infuser["grades"][tier]["infusionThroughputPerSecond"],
                        focusThroughputAssumption="Continuous supplied work at named installed focus; no fabrication loss on streamed equipment/focus; setup, transfer, prerequisite focus installation and collection time separate",
                        sourceComparisons=alternatives, conditionalRenewalBasket=basket(fixed, flexible, profile, True, tier),
                        opportunityBasket=basket(fixed, flexible, profile, False, tier)))
    routes = route_inventory(profile)
    with gzip.open(args.output / "mobility-route-groups.json.gz", "wt", encoding="utf-8") as stream:
        json.dump(routes, stream, separators=(",", ":"))
    result = dict(profile=str(args.profile.resolve()), profileIntegrity=profile["integrity"],
        profileSha256=hashlib.sha256(args.profile.read_bytes()).hexdigest(), skillAudit=str(args.skill_audit.resolve()),
        constructiveRenewalDefinition=CONSTRUCTIVE_RENEWAL_DEFINITION,
        caveats=["Hypothetical zero-investment bundles, not player inventory or all-catalog purchases; rank-one wishes deferred when actual saved gates are later.",
                 "Dormant selects first ranks; subsequent checkpoints train only the same selected role skills to their maximum available saved ranks, retaining prerequisites. This is a plausible sustained selection, not every registry skill.",
                 "Prerequisites and paid earlier ranks retained once; conflicting active choice groups rejected. Milestone/live requirements remain explicit, never assumed met.",
                 "Equipment includes each sequential upgrade from Latent; latent equipment construction, matrices, machine/network setup and repair supplies are additional physical bills.",
                 "Optional Focus includes each prior tier, all six minima and one shared flexible remainder; the latter is never duplicated across families.",
                 "Per-source comparisons satisfy one family alone and are alternatives, not additive supplies. Mixed basket retains all family credits once.",
                 "Stacks of 64 are quantity equivalents; actual item stack limits vary, notably boats/pearls. No storage-slot claim is made.",
                 "Source stage is saved evidence, not independent validation of prerequisites. Source filters use the engine's ordinal mapping of ENTRY/EARLY/MID/LATE/APEX to the five power tiers.",
                 "A conditional-renewal label requires an actual constructive source at or before the scenario stage; aggregate item placement cannot promote a later renewable source. Later/unknown-stage proofs remain quantity opportunities, never measured rates.",
                 "Mixed baskets use a fixed illustrative common-material portfolio. An unresolved basket does not rule out other item alternatives such as native fishing rewards retained in full source comparisons.",
                 "Machine times are ideal processing bounds, exclude collection and cannot be added as a measured play-time estimate; base crucible assumes no pylon extension."],
        setupAndLatentMaterialRecipes=[dict(id=p["id"], inputs=p["inputs"], outputs=p["outputs"], metadata=p["metadata"])
            for p in profile["economy"]["processes"] if any(o["itemId"] in {PREFIX + item for item in (
                "essence_crucible", "essence_infuser", "ascendance_nexus", "essence_focus", "ascendance_matrix", "channelstone",
                "latent_ingot", "latent_nugget", "ascendance_pickaxe", "ascendance_axe", "ascendance_boots",
                "ascendance_leggings", "ascendance_melee_weapon", "ascendance_chestplate")} for o in p["outputs"])],
        scenarios=scenarios, allSourceQuantities=str(candidates_path.resolve()),
        mobilityVariants=len(routes["variants"]), mobilityRouteGroups=len(routes["groups"]))
    (args.output / "loadouts.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(dict(scenarios=len(scenarios), mobilityVariants=len(routes["variants"]), output=str(args.output.resolve())), indent=2))


if __name__ == "__main__":
    main()
