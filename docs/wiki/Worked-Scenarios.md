> **Historical workflows from the earlier generator.** The acquisition/provider examples and retired report names below are not current configuration instructions. Use [Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration), [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance) and [the current model](https://github.com/mistaboom/essence_ascendance/blob/main/docs/simple-balance.md) for the supported inputs. These examples remain as dated documentation, not new test receipts.

# Worked scenarios for pack makers

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) · [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance)

These are prospective workflows with valid syntax and confirmed settings. **Outcomes are illustrative; the examples were not run against a pack or world.** Administrative commands require permission 2. Server commands work from console; player-target examples here use an in-game operator.

## Adding cooperative infrastructure to a development pack

**Beta.5 multiplayer; illustrative workflow, not a live test result.** Use matching server/client beta.5 builds and a fresh staging world. Keep player lessons in the Archive; use [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure) for administrative rules.

1. Choose native/no-provider or an exact accepted external dependency tuple. To use both FTB Teams membership and FTB Quests evidence, use Teams **2101.1.11**, Library **2101.1.36 or 2101.1.37**, Quests **2101.1.36**, and Architectury **13.0.11**, with artifacts matching the loader. The integrations have separate scopes; this accepted tuple does not replace live-world acceptance checks.
2. Inspect `/essence admin membership status` and the player's `/essence team status`. Fresh AUTO and established-world authority have different behavior. Normal external party members need no second enrollment.
3. Explain the wealth trust boundary before native approval/external joining: all existing Stored is communal; Available/invested progression stays personal. Rehearse departure and offline membership on dedicated state.
4. Place normally bound machinery and inspect `/essence machine ~ ~-1 ~ status` only if a matching machine is below the sender. Automatic linking excludes foreign teams and keeps a valid source stable. Do not add an ownership-adoption step.
5. Check simultaneous eligible consumers, the aggregate fill limit, selected/required types and explicit equipment Start. Additional Infusers can share one source; one player still has one active source. Do not infer guaranteed rates from receiver maxima.
6. Rehearse coordinated backups and the actual schema/provider-change boundary before considering a production rollout. Real FTB activation and new Crucible team ownership have a narrow user-confirmed success; offline-member events, unloaded machinery, two-client fairness, restarts, graphics and interrupted saves remain pending. [Verification scope](https://github.com/mistaboom/essence_ascendance/wiki/Version-and-Maintenance#evidence-and-test-claims).

**Illustrative accounting outcome:** the 900 + 300, channel 200, then depart with 500 each example on the team page explains equal remaining shares. It is not a contribution refund or evidence that this scenario was run with two actual clients.

## Starting a new modpack

**Goal:** generate a coherent starting economy for the content you intend to distribute.

1. Assemble the final intended mods, datapacks, world-generation settings and integration configs in staging.
2. Install matching loader/dependencies on client and server. Let a new server without saved authority perform first generation.
3. Check authority and disk agreement:

```mcfunction
/essence debug mappings status
/essence debug balance summary
/essence debug balance validate
```

4. Read the overview and manifest. Review low-confidence/unresolved evidence, extreme resources and early category coverage.
5. Export detailed saved sources when needed:

```mcfunction
/essence admin balance export
```

Export writes reports; it does not regenerate. Check representative survival access and client Archive values before distribution. Ship inputs as pack intent; include saved authority only for a deliberately matching fixed environment.

**Illustrative outcome:** early supply may differ among categories, leading to different investment costs and ore supply decisions. Do not demand identical category prices merely because there are six Essences.

## Adjusting overall power and progression pace

**Goal:** request modestly stronger added power, cheaper investment and faster seals without replacing resource facts.

Edit the existing tables in `config/essence_ascendance.toml`:

```toml
schema_version = 1

[power]
overall = 1.1 # More added headroom; not 10% guaranteed live DPS.

[progression]
length = 1.0 # Retain the baseline journey scale.
cost_pressure = 0.9 # Request cheaper generated investment.

[attunement]
pace = 1.25 # Increase contribution speed without directly changing Essence costs.
```

Back up the old inputs/profile/world, rebuild in staging, then compare Bonus tracks, Skill parameters, composed output checks and Attunement targets/pacing.

```mcfunction
/essence admin balance rebuild
/essence debug balance validate
```

**Illustrative outcome:** faster activity completion and cheaper costs can be requested together, but exact rank/effect changes depend on native grids, supply and headroom. If you only want a longer journey, adjust `progression.length` deliberately rather than also increasing pace and obscuring the result.

## Investigating an unexpectedly valuable or valueless item

**Goal:** explain the decision before pinning a yield. Iron ingots are a real identifier used for syntax, not a claim that iron is wrong in your pack.

```mcfunction
/essence debug balance summary
/essence debug balance item "minecraft:iron_ingot"
/essence admin balance export
```

Filter `valuation.csv` to the item. Compare opportunity value, yield, stage, availability, routing and warnings. Join acquisition details on `item_id` + `source_index`. Ask:

- Is the saved profile older than the recipe/source change?
- Did supported setup/tool/input evidence establish access?
- Is supply effectively infinite or cheaply automated?
- Did a complete modeled conversion route lower the yield?
- Is the item missing evidence rather than intentionally zero?

If a pack genuinely provides entry-stage unlimited cobblestone, the [conditional fact example](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization#valid-factual-examples) describes that evidence. Do not invent free throughput to force a lower number. Correct facts, rebuild in staging, and compare.

**Illustrative outcome:** an item can retain high crafting usefulness while yielding little or no Essence. That is a value-versus-extraction distinction, not necessarily a bug.

## Investigating an unexpectedly gated Skill

**Goal:** separate generated availability from a particular player's effectiveness. Fatigue Flight is a registered example.

```mcfunction
/essence debug balance skill fatigue_flight 1
/essence debug player skills show fatigue_flight @s
/essence debug player milestones show sky_limit @s
/essence debug player skills loadout @s
```

Use the active Archive for rank-specific gates. In `skill_availability.csv` compare catalog, candidate and final tier, witnesses, coverage and prerequisite/setup clamps. The chat balance view's base tier is not every rank gate.

Then check owned prerequisite ranks, milestone resolution, choice selection, replacements and current conditions. A granted receipt does not bypass those checks.

**Illustrative outcome:** a pack may expose earlier flight capability, yet a supported operating setup or permanent prerequisite can keep final access later than the candidate. PARTIAL coverage does not prove all earlier alternatives were considered.

If evidence is wrong, correct it. Changing `policies.flight` can change relative emphasis but does not waive prerequisites. Avoid fixing a pack-level decision by silently granting every live player a Skill.

## Handling incomplete evidence for another mod

**Goal:** supply truthful evidence without claiming the generator understands hidden mechanics.

1. Read provider/version/readiness diagnostics and unsupported candidate reasons.
2. Separate ordinary recipe access from operating energy, components, callbacks, rates and composition.
3. Check the [audited integration versions](https://github.com/mistaboom/essence_ascendance/wiki/Integrations-and-Compatibility).
4. Use supported factual fields only for measurements/access you can substantiate. Leave unknown rate/cost absent; zero means known zero.
5. For complex missing production behavior, use/request a typed adapter. A flat damage or throughput fact does not implement a complete machine.
6. Rebuild in staging; verify the fact actually matched and contributed.

If a provider must be excluded, use the **actual provider ID from diagnostics**, not the mod's display name. That selector is environment-specific, so this walkthrough does not invent a runnable ID.

**Illustrative outcome:** generic recipes may establish a crafting ingredient while the mod's full spell/module composition remains unresolved. This is useful partial support with an explicit boundary.

## Changing a pack after players have begun progressing

**Goal:** update intentionally while preserving a recoverable history.

1. Back up complete world/config/profile and record old versions/integrity.
2. Export old reports if needed for comparison; preserve their sidecars.
3. Stage the content/input update. Existing authority is reused until explicit rebuild, so a restart alone is not a recalibration.
4. Rebuild the staged copy, inspect changed prices/ranks/gates/yields/ore and try representative existing players/loadouts.
5. Review same-chapter Attunement preservation, historical receipts and effective investment under new caps.
6. Schedule production maintenance, rebuild deliberately and check install, disk, reports and clients separately.
7. Verify restart reuse and retain the old coherent backup for rollback.

**Illustrative outcome:** current earned seal fractions can survive new target rates while future activity uses new policy. Historical Skill receipts remain historical paid amounts; newly generated prices do not automatically rewrite refunds.

A profile-only rollback does not undo purchases or activity after installation. Follow [updating and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery) for a consistent restore. No full reset is needed merely because balance is being regenerated.
