# Diagnostics and testing

[Commands index](https://github.com/mistaboom/essence_ascendance/wiki/Commands) · [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance) · [Troubleshooting](https://github.com/mistaboom/essence_ascendance/wiki/Troubleshooting)

**Permission: level 2 for every entry, including help and tests.** “Server,” “Player” and “Target” follow the [source rules](https://github.com/mistaboom/essence_ascendance/wiki/Commands#syntax-and-source-rules). Target commands support self when omitted; console needs one explicit online player. Examples with `@s` are in-game.

These commands investigate current state or saved data; they do not rebuild, spend Essence, grant Skills or reset progress. Specific reconciliation effects are identified below. Queries may initialize ordinary caches/data records and write logs. Debug targeting does not run the Admin mutation-refresh wrapper.

## Choose a starting point

| Question | Start with |
| --- | --- |
| Is a profile installed? | `debug mappings status` and `debug balance summary` |
| Does disk match the installed profile? | `debug balance validate` |
| Why is an item's yield surprising? | `debug balance item "minecraft:iron_ingot"` |
| Why is a Skill ineffective? | `debug player skills show frenzy` |
| Is a seal crediting correctly? | `debug player attunement show gathering` and history |
| Is a machine connected? | Look at it, then the matching `debug machine` command |
| Did a narrow runtime invariant fail? | The appropriate `test` command; interpret its limited scope |

Each fragment above follows `/essence `. Exact runnable examples appear in the entries below.

## Saved balance

These read the installed snapshot rather than recapturing recipes/providers. The item argument is an **exact quoted full saved item ID**. Bonus/Skill arguments accept registered mod-default short IDs. `<path>` is a quoted JSON Pointer beginning `/runtime/` and resolving to a scalar; escape `/` in keys as `~1`, `~` as `~0`, and use zero-based array indices.

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence debug balance summary` | Server | Active snapshot; integrity prefix/generator, resource/equipment/enemy/curve counts, conservation passes/paths and last load time | `/essence debug balance summary` |
| `/essence debug balance validate` | Server | Disk profile must exist/read/validate. Full decode and active-integrity comparison; mismatch fails with reload/rebuild guidance. **Potentially expensive file read**, no install/write. | `/essence debug balance validate` |
| `/essence debug balance item` | Player | Active snapshot; main-hand registry ID. Empty hand selects air and normally lacks saved evidence. | `/essence debug balance item` |
| `/essence debug balance item <id>` | Server | Exact saved ID; supported reachability, stage, supply, confidence, value/yield, fact/warning samples and equipment axes. Missing evidence fails. | `/essence debug balance item "minecraft:iron_ingot"` |
| `/essence debug balance bonus <bonus>` | Server | Known Bonus/active curve; maximum effect, exponent, style/applicability, per-tier caps and effects | `/essence debug balance bonus melee_damage` |
| `/essence debug balance skill <skill>` | Server | Known Skill/active saved curve; rank 1 price/power/base tier and maximum purchasable rank | `/essence debug balance skill frenzy` |
| `/essence debug balance skill <skill> <rank>` | Server | Integer rank **1..1000** and within saved curve length; selected rank's cost/power. Does not buy a rank. | `/essence debug balance skill frenzy 1` |
| `/essence debug balance cost <path>` | Server | Existing saved runtime scalar; prints it. Objects, arrays and nonexistent paths fail. | `/essence debug balance cost "/runtime/statMaxBonuses/essence_ascendance:melee_damage"` |

Skill output's base required tier is not a complete list of rank-specific gates. Use the Archive or rank reports for those. Conservation counts and “validate passed” describe modeled/internal checks, not every possible third-party exploit.

## Player progression

`<skill>` and `<milestone>` are syntax-valid short or quoted full mod IDs.

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence debug player summary [player]` | Target | Tier/profile, stored/effective investment/capacity, registry/provider counts and next Ascension state. Can align a stale chapter. Does not enumerate wallets/reservoir. | `/essence debug player summary @s` |
| `/essence debug player equipment [player]` | Target | Equipment activation slots/profiles and same-stat strength merging | `/essence debug player equipment @s` |
| `/essence debug player skills summary [player]` | Target | Catalog/implemented, owned/effective/suspended/replaced counts, selections and revision | `/essence debug player skills summary @s` |
| `/essence debug player skills owned [player]` | Target | Summary plus receipts, ranks and actual paid costs, including preserved unknown definitions | `/essence debug player skills owned @s` |
| `/essence debug player skills loadout [player]` | Target | Summary plus saved choice groups/selections; no loadout mutation | `/essence debug player skills loadout @s` |
| `/essence debug player skills show <skill> [player]` | Target | Current definition or preserved owned receipt; relationships, ownership, requirements, gates and evaluation. Unknown/unowned fails. No rank argument. | `/essence debug player skills show frenzy @s` |
| `/essence debug player milestones list [player]` | Target | Stored flags including preserved unknown IDs | `/essence debug player milestones list @s` |
| `/essence debug player milestones show <milestone> [player]` | Target | Configured provider/permanent state, known Skill gate, or already-stored unknown ID. Read-only resolution; does not capture. | `/essence debug player milestones show sky_limit @s` |

## Gameplay snapshots

`<category>` accepts `offense`, `defense`, `vitality`, `mobility`, `gathering` or `utility`, case-insensitively. Special literals take their own branches.

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence debug player gameplay <category> [player]` | Target | Applied Bonus/equipment/native attributes and category runtime details. **Reconciles equipment attributes; may clamp health to the new maximum.** Mobility/Utility also reconcile movement. Use a staging player if preserving transient health matters. | `/essence debug player gameplay defense @s` |
| `/essence debug player gameplay shield [player]` | Target | Blocking and combat-prevention snapshot | `/essence debug player gameplay shield @s` |
| `/essence debug player gameplay projectiles [player]` | Target | Projectile adapters/control/outcome diagnostics; launches nothing | `/essence debug player gameplay projectiles @s` |
| `/essence debug player gameplay posture [player]` | Target | Evasive/Bulwark/Adaptive posture snapshot | `/essence debug player gameplay posture @s` |
| `/essence debug player gameplay status [player]` | Target | Harmful-status interception and mirror diagnostics | `/essence debug player gameplay status @s` |

## Attunement diagnostics

`<category>` resolves a registered Essence ID. `<tier>` is a registered **from-tier** with a chapter; `dormant` is an example. A terminal tier without a next chapter fails.

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence debug player attunement summary [player]` | Target | Authority; current chapter seals/readiness, or completed maximum-tier state. Snapshot may align a stale chapter below maximum tier. | `/essence debug player attunement summary @s` |
| `/essence debug player attunement show <category> [player]` | Target | Authority/known category; progress, generated target, acceleration and methods. May align chapter. At maximum tier, seals/methods show complete, target 0 and multiplier 1; there is no next chapter. | `/essence debug player attunement show gathering @s` |
| `/essence debug player attunement history <category>` | Player | Authority; own stored action page 1 | `/essence debug player attunement history gathering` |
| `/essence debug player attunement history <category> <page> [player]` | Target | Authority; integer page **1..2,147,483,647**, within actual page count, four entries/page. Source signatures, accepted/rejected reasons and contribution factors. | `/essence debug player attunement history gathering 1 @s` |
| `/essence debug player attunement activities` | Server | Registered category menu; no active profile needed | `/essence debug player attunement activities` |
| `/essence debug player attunement activities <category>` | Server | Registered category; method IDs, calibration families and units; no active profile needed | `/essence debug player attunement activities utility` |
| `/essence debug player attunement reachability` | Server | Authority; generated chapter breadth/menu | `/essence debug player attunement reachability` |
| `/essence debug player attunement reachability <tier>` | Server | Authority/known chapter; estimated stage-accessible and base methods | `/essence debug player attunement reachability dormant` |

**Targeted history requires the page first.** `history gathering @s` is not a valid targeted form. A reachability estimate is not proof that a particular survival player has encountered every source.

## Held items

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence debug item inspect` | Player | Main-hand first-party Ascendance equipment with a known activation profile. Player-tier reference, held strengths and applicable ranged/caster details. Not general Focus/third-party inspection. | `/essence debug item inspect` while holding supported equipment |
| `/essence debug item mapping` | Player | Nonempty main-hand item; current mapping/source/eligibility/output. No fresh valuation. | `/essence debug item mapping` while holding iron ingots |
| `/essence debug item baselines` | Player | Baseline reference at **player tier**, with harvest/armor weights. Actual gear uses its own item tier. | `/essence debug item baselines` |

## Machines and installed mappings

For machine entries, the player must look directly at the matching block entity within **8 blocks**; the pick does not target fluid. There are no target-player arguments.

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence debug machine crucible` | Player | Correct ray target; input lanes/mappings, storage/owner/access, structure and processing diagnostics. May refresh structure caches; no dissolution run. | `/essence debug machine crucible` while looking at a Crucible |
| `/essence debug machine pylon` | Player | Correct ray target; **refreshes link**, then shows Focus/owner/range/limits/contribution/eligibility | `/essence debug machine pylon` while looking at a Pylon |
| `/essence debug machine infuser` | Player | Correct ray target; **refreshes link**, then shows owner/Focus/operations, policy and reservoir context | `/essence debug machine infuser` while looking at an Infuser |
| `/essence debug mappings status` | Server | Last install/rejection, generation/counts/warnings/errors and paths; no scan/reload | `/essence debug mappings status` |
| `/essence debug mappings list` | Server | Active rule IDs/priorities/selectors and outputs/BLOCK; no reload | `/essence debug mappings list` |

Machine snapshots reconcile ordinary links/caches but do not spend materials or Essence. Use a staging network if you need a snapshot without affecting live link state.

## Test commands and their meaning

Tests have no target-player arguments. “PASS” describes only the check listed.

| Exact syntax | Source | Prerequisites, output and actual scope | Valid example |
| --- | --- | --- | --- |
| `/essence test luck` | Player | Reconciles normal mobility/Luck modifiers; checks expected modifier presence and finite native Luck. PASS/FAIL, no loot rolls or statistical luck measurement. Use staging for transient modifier checks. | `/essence test luck` |
| `/essence test activation` | Player | Current worn-passive plus main-hand activation; MAX merge, active/inactive/zero counts. Not simulated combat or every action/offhand context. | `/essence test activation` |
| `/essence test skills milestones` | Player | Read-only resolution of all catalog-referenced permanent gates and live provider comparison. Reports resolvability/completed projection; no capture/grant. | `/essence test skills milestones` |
| `/essence test skills effects` | Server | Pure implementation invariants for handlers/relationships/config/math/payload contracts. Failure list or PASS; no world combat or universal compatibility test. | `/essence test skills effects` |

## Help endpoints

All are **level 2, Server**, no arguments/prerequisites beyond access. They print menus and perform no test, mutation or rebuild. Both forms in each row are exact executable syntax and valid examples.

| Menu example | Help example |
| --- | --- |
| `/essence debug` | `/essence debug help` |
| `/essence debug player` | `/essence debug player help` |
| `/essence debug item` | `/essence debug item help` |
| `/essence debug machine` | `/essence debug machine help` |
| `/essence debug mappings` | `/essence debug mappings help` |
| `/essence debug balance` | `/essence debug balance help` |

### Player-detail and test menus

| Menu example | Help example |
| --- | --- |
| `/essence debug player skills` | `/essence debug player skills help` |
| `/essence debug player milestones` | `/essence debug player milestones help` |
| `/essence debug player gameplay` | `/essence debug player gameplay help` |
| `/essence debug player attunement` | `/essence debug player attunement help` |
| `/essence test` | `/essence test help` |
| `/essence test skills` | `/essence test skills help` |

The bare `/essence debug player attunement show` and `/essence debug player attunement history` also print Attunement help (same permission/source; exact examples as shown). Other incomplete leaves such as `debug balance cost` or `debug player skills show` require their arguments and are not menus.

Source: [debug handlers](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceDebugCommands.java), [balance queries](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceBalanceCommands.java), [tests](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceTestCommands.java), [health reconciliation](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/equipment/EquipmentAttributeService.java).
