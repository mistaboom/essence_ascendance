# Overrides and customization

[Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) · [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance)

Use `skill_settings.toml` for Skill tiers, ranks and native stat endpoints, and `bonus_settings.toml` for Bonus active tiers and effect bounds. Global policy stays in `config/essence_ascendance.toml`.

The advanced file **`config/essence_ascendance/balance_overrides.toml`** corrects supported measurements or pins permitted final values. It applies during fresh generation or explicit rebuild, never as a live overlay on saved balance.

## Grammar and scope

Root `schema_version = 1` is followed by repeated `[[fact]]` tables and an optional `[exact]` table. The bounded TOML parser rejects unknown keys/tables, duplicate fact IDs, wrong types and contradictory facts. Each file is limited to 4,000,000 bytes; at most 10,000 facts are accepted.

The current engine does not infer quests, acquisition routes, renewable supply, automation rates or hidden operating costs. Old source/provider/capability facts and acquisition fields are not supported inputs.

## Factual overrides

| Key | Type / default | Meaning |
| --- | --- | --- |
| `kind` | Required string | `item`, `block`, `item_tag`, `block_tag`, `enemy`, `recipe` or `recipe_family` |
| `selector` | Required nonblank string | Registry/tag/type identifier; item/block/enemy/recipe selectors support `*` wildcards |
| `id` | String; derived from kind and selector if omitted | Unique diagnostic identifier |
| `priority` | Integer `-100000..100000`; default `1000` | Higher wins, then confidence and stable fact ID |
| `reason` | Optional nonblank string | Explanation retained with file/line provenance |
| `confidence` | Optional finite `0..1` | Measurement certainty |

At least one factual field is required. Omitted measurements remain omitted. Unmatched selectors warn. A fact corrects the generator's model; it does not change the referenced item's gameplay behavior.

### Item, block and tag facts

| Fields | Type / meaning |
| --- | --- |
| `disabled`, `creative_only`, `administrative`, `joke`, `include_reference` | Booleans; exclusion cannot be combined with explicit inclusion |
| `resource_value` | Nonnegative finite intrinsic-value estimate; not guaranteed final Essence payout |
| `stage` | `entry`, `early`, `mid`, `late`, `apex`; relative catalog strength, not acquisition timing |
| `slot` | `head`, `chest`, `legs`, `feet`, `body`, `offhand`, `mainhand_melee`, `mainhand_bow`, `mainhand_crossbow`, `mainhand_caster`, `mainhand_tool` |
| `armor`, `toughness`, `damage` | Nonnegative finite native points |
| `attack_speed` | Nonnegative finite attacks/second |
| `mining_speed`, `harvest_level`, `durability` | Nonnegative finite native tool measurements |

Equipment corrections apply to measurable registered references. The `body` slot is retained as evidence but excluded from player equipment frontiers. An assigned slot or number does not implement a custom item's hidden ability.

### Enemy facts

Enemy facts support the exclusion/inclusion flags, `stage`, `health`, `armor`, `toughness` and `damage`, plus confidence/reason. Numbers are nonnegative and finite. Health is native points: two points equal one heart. Only registered native hostile references are considered.

### Recipe facts

Recipe and recipe-family facts support `disabled`, confidence/reason, `processing_time` (nonnegative server ticks), `output_count` (whole count 1–2,147,483,647) and `probability` (0–1). Twenty ticks equal one second. These corrections do not prove a station, ingredient supply, fuel or energy cost.

### Example

This is a factual estimate example, not a recommended price or a measured pack result:

```toml
schema_version = 1

[[fact]]
id = "material_estimate"
kind = "item_tag"
selector = "example:refined_materials"
resource_value = 24.0
reason = "Pack author supplied an intrinsic material estimate."
```

Complete recipe and conversion constraints can still lower the final payout.

## Exact generated-value overrides

`[exact]` uses quoted JSON Pointer keys under supported `/runtime/` and `/economy/` paths. The target must already exist and accept the supplied primitive value or array. Escape `~` as `~0` and `/` inside a key as `~1`. These are not dotted TOML paths.

Use `runtime_parameters.csv` to inspect resolved scalar pointers. Native units, discrete steps, meaningful-rank checks, composition and accounting constraints remain enforced. Whole Essence payout requests must be nonnegative integers and can be reduced by conservation. Metadata and protected profile identity are not customization targets.

Prefer the system settings files for Skill and Bonus progression. Exact overrides are advanced final-value corrections, and conflicting edits can reject a rebuild. Inspect the installed reports after success; editing a report never changes gameplay.

Current source: `balance/config/BalanceOverrides.java` and the annotated `common/src/main/resources/balance/balance_overrides.toml`.
