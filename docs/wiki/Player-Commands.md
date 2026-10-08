# Player commands

[Commands index](https://github.com/mistaboom/essence_ascendance/wiki/Commands) · [The Archive](https://github.com/mistaboom/essence_ascendance/wiki/Players-and-the-Archive)

**Permission: no operator requirement for every entry on this page.** “Server” supports players and console; “Player” requires the sender to be a player. None accepts a trailing player target.

Use the Archive for gameplay explanations and resolved values. These commands provide chat summaries and a normal Bonus investment/Ascension path.

## Help

No arguments or gameplay prerequisites; each prints the relevant menu and changes no progression.

| Exact syntax | Source | Valid example |
| --- | --- | --- |
| `/essence` | Server | `/essence` |
| `/essence help` | Server | `/essence help` |
| `/essence bonuses` | Server | `/essence bonuses` |
| `/essence bonuses help` | Server | `/essence bonuses help` |
| `/essence attunement help` | Server | `/essence attunement help` |

## Status and wallet

`<essence>` is one of the six registered Essence IDs. Short IDs and quoted full IDs work as described in [identifier syntax](https://github.com/mistaboom/essence_ascendance/wiki/Commands#identifiers).

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence status` | Player | Own tier, total wallet, stored/effective investments, capacity and next Ascension state. Evaluating readiness can align an old Attunement ledger to the current chapter; it neither earns activity nor ascends. | `/essence status` |
| `/essence balance` | Player | All six available wallet balances. No active-generation request; excludes invested Essence and the Crucible reservoir. | `/essence balance` |
| `/essence balance <essence>` | Player | Known Essence; prints its available wallet balance. Unknown ID fails. | `/essence balance offense` |

## Bonus queries

`<category>` is `offense`, `defense`, `vitality`, `mobility`, `gathering` or `utility`, case-insensitive. `<stat>` is a registered Bonus/stat ID such as `melee_damage`. Use completion to discover others.

These queries change no investment and spend no Essence. They resolve current config; some core views can show provisional data if no authoritative profile is installed.

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence bonuses list` | Player | Category overview, effective investment/capacity, stat counts and Latent lock notice | `/essence bonuses list` |
| `/essence bonuses list all` | Player | Every registered Bonus grouped by category | `/essence bonuses list all` |
| `/essence bonuses list <category>` | Player | Accepted category; its Bonuses and current effective values | `/essence bonuses list mobility` |
| `/essence bonuses show <stat>` | Player | Known stat; ID, category, Essence, stored/effective investment, cap, scaling and equipment applicability | `/essence bonuses show melee_damage` |

### Invest in a Bonus — spends Essence

**Exact syntax:** `/essence bonuses invest <stat> <amount>`\
**Permission/source:** no operator requirement; player only.

`<stat>` must be registered. `<amount>` is a positive integer, **1..9,223,372,036,854,775,807**, measured in that stat's required Essence.

The transaction requires enough wallet Essence, a resolvable tier investment policy, available capacity and a valid target. At/over-cap storage, an oversized request, numeric overflow, configuration-resolution failure or failed commit rejects the request. It does not partially buy the remaining capacity.

Success spends the requested currency and increases stored investment. Output shows the amount invested, resulting total/cap and remaining wallet. Effectiveness remains tier- and equipment-dependent. There is no chat refund/respec branch; use the Nexus's supported gameplay interface.

```mcfunction
/essence bonuses invest melee_damage 100
```

This is valid syntax, with success conditional on your funds and current cap. Check `bonuses show` and `balance offense` first; use a staging player for purchase experiments.

**Authority limitation:** this command resolves the general config accessor without an explicit installed-profile readiness check. It can reach bootstrap policy when authority is absent. Administrators should repair a rejected profile before inviting progression transactions; this command is not a profile-health check.

## Attunement and Ascension

`<category>` here is a registered Essence ID, not `all`. These queries require the active authoritative profile. Below maximum tier they use the current chapter; querying a stale ledger can align it to that chapter. At Transcendent they show the maximum-tier state with all category seals complete, without requiring a next chapter.

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence attunement` | Player | Current chapter's seals, category percentages and readiness | `/essence attunement` |
| `/essence attunement <category>` | Player | Known Essence; progress, investment acceleration, activity methods and up to three recent actions | `/essence attunement gathering` |

### Ascend — changes player tier

**Exact syntax/example:** `/essence ascend`\
**Permission/source:** no operator requirement; player only.

Requires a valid adjacent chapter in the authoritative profile and enough completed seals. Max tier, not-ready progress or unavailable configuration fails.

Success advances to the next player tier, refreshes runtime state and changes chapter tracking. It may deliver the first powered-tier Archive. **It spends no wallet Essence and consumes no Bonus investment, Skill or equipment.** Latent onboarding can auto-promote to Dormant without this command.

Use `attunement` first to review readiness. Administrators testing transitions should use a staging world rather than changing a live player's earned chapter.

## Milestones

`<milestone>` is a known configured milestone ID, such as `obtain_diamonds`. These report provider state without granting or permanently capturing completion. Milestones can gate Skills; they are not the current requirement for player Ascension.

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence milestones` | Player | All configured states: complete, incomplete or unresolved | `/essence milestones` |
| `/essence milestones <milestone>` | Player | Known configured ID; provider, target and state. Does not accept an arbitrary old saved flag. | `/essence milestones obtain_diamonds` |

Source: [player handlers](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceCommands.java), [Attunement handlers](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/AttunementCommands.java), [investment validation](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/progression/StatProgressionService.java).
