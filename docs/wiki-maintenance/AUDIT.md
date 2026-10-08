# Essence Ascendance wiki documentation audit

Research date: **2026-10-08**. Status: **local research and page plan; nothing published**.

## 1. Source, release scope, and audit boundaries

| Item | Verified scope |
| --- | --- |
| Audited source | `5ea335be01ba9fd6fc80fec950029400cac3ecd0` ([commit](https://github.com/mistaboom/essence_ascendance/commit/5ea335be01ba9fd6fc80fec950029400cac3ecd0)) |
| Checkout observed at final review | `59e27446afbb6c9e73be38d0d407915fe15354fa`; unrelated branding/build commit arrived during research and was preserved |
| Declared mod version | `1.0.0-beta.1` |
| Minecraft / Java | Minecraft **1.21.1**, Java **21** |
| Supported loader projects | **Fabric and NeoForge**; common code is shared through Architectury |
| Build dependency versions | Fabric Loader `0.19.3`, Fabric API `0.116.15+1.21.1`, NeoForge `21.1.248`, Architectury `13.0.11`; optional JEI compile API `19.43.0.395` |
| Latest published GitHub release | [Essence Ascendance 1.0.0-beta.1](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.1), tag `v1.0.0-beta.1`, **prerelease**, published `2026-10-08T20:11:50Z` (13:11:50 PDT) |
| Release source | `9caef8477c8aa95a5f61b7fc008d1659e2445e79` |
| Published assets | `essence_ascendance-fabric-1.0.0-beta.1.jar`, `essence_ascendance-neoforge-1.0.0-beta.1.jar`, `SHA256SUMS` |
| Initial working tree | Clean (`git status --short` produced no entries) |
| Repository instructions | No applicable `AGENTS.md` found in the repository, documentation subtree, or checked ancestor directories. Existing development/release documentation was inspected. |

Version and dependency evidence: [V1], [V2], [V3]. Release metadata was checked against GitHub's public releases API as well as the release page; `/releases/latest` redirects to the release list because the available release is a prerelease. Do not describe this as a stable release.

**Released versus unreleased:** the release-tag-to-audited-source diff contains only `.github/workflows/description.yml`, `README.md`, `docs/DEVELOPMENT.md`, `docs/RELEASING.md`, and `tools/release/sync-description.py`. There are **no command, gameplay, balance, config, resource, or test changes** in that diff. Thus the implementation audited here describes the published beta's source behavior. The revised README and listing synchronization workflow are post-release changes. During research the checkout advanced to `59e27446afbb6c9e73be38d0d407915fe15354fa`, adding `art/branding/` assets/documentation, `tools/LogoGenerator.java`, `gradle/branding.gradle`, and its root build-script inclusion. The original audited commit remains the evidence anchor; comparison confirmed no changes to `common/src`, declared versions or loader descriptors in that later commit. Branding/build tooling is unreleased scope beyond this implementation audit. This comparison establishes source equality for the implementation, not independent binary reproducibility or verification of every published jar.

The public wiki URL redirected to the repository during inspection. No wiki page corpus was retrievable there. This is a proposed content structure and source audit, not a claim to have compared existing private or inaccessible wiki pages. The repository's engineering documents are secondary evidence; current registration and handler code takes precedence.

This task adds only this audit. Unrelated concurrent repository work was preserved. No gameplay code was edited, Minecraft was not launched, no profile was regenerated, and no installed instance, world, or player record was inspected or changed. No test suite or live command was executed for this documentation task. Source/test inspection and release checks must not be presented as new gameplay acceptance results. All runtime file paths below are **portable relative paths**, never personal installation paths.

## 2. Wiki purpose and proposed pages

The wiki should explain administration and pack design. It should direct players to the **Ascendance Archive** for lessons, gameplay reference, and the values synchronized from their actual server. Avoid copying generated prices, yield tables, rank counts, tier decisions, or equipment statistics into supposedly universal wiki tables.

| Proposed page | Intended content / boundary |
| --- | --- |
| Home | Audience split; beta/version scope; installation links; a prominent player link to Getting the Ascendance Archive |
| Getting the Ascendance Archive | Automatic gift, replacement recipe, operator recovery, opening, Guide / Reference / Search, synchronized values |
| Installation and Environment | Minecraft, Java, loaders, required dependencies, both sides, optional JEI, environment-specific adapter limits |
| Commands | Syntax conventions and complete inventory below; permissions, targeting, restrictions, consequences; links to maintenance procedures |
| How Procedural Balance Works | Evidence → acquisition → economy → competition → runtime → validated saved authority → clients; opportunity value versus yield |
| Configuration and Overrides | Every accepted policy key; ore tables; facts and exact pointers; precedence, units, errors; rebuild required |
| Integrations and Evidence Limits | Generic native support, audited adapters and versions, unsupported evidence, confidence, constructive acquisition, fallbacks |
| Profiles, Reloads, and Maintenance | First generation, saved reuse, explicit reload/rebuild, failed candidates, backup/recovery, recipe changes, client sync |
| Reports and Diagnostics | File inventory, report joins/detail levels, manifest, provenance, useful diagnostic commands, sanitizing evidence |
| Troubleshooting | No authority, rejected rebuild, missing item evidence, zero yields, stale reports, client loading, unsupported adapters, performance |
| Maintainer Coverage and Release Notes | Source revision, release-scoped changes, automated versus manual coverage, future documentation refresh checklist |

Do not create a parallel gameplay manual on the wiki. Brief system definitions are needed for administrators, but player build choices and current values belong in the Archive/Nexus. Publish maintenance commands only with their effects immediately visible.

## 3. Command inventory

### 3.1 Registration, notation, and permissions

All shipping chat commands are under **`/essence`**. `EssenceAscendance` registers `EssenceCommands` through Architectury's `CommandRegistrationEvent`; that tree attaches Admin, Debug, Test, and Attunement branches. A repository-wide search of production Java in common and loader projects found no additional command roots or client command registrations. There are no registered legacy aliases such as `/essence stat`, `/essence skills`, `/essence rebuild`, or `/archive`. Help branches are executable commands, not aliases to mutation handlers. Development Gradle tools are discussed separately. ([C1]–[C7], [L1])

Inventory conventions:

- `<x>` is required; `[x]` is optional; `a|b` represents **separate literal branches** or alternatives described in the row, not text to type.
- **P**: player source required. **T**: player source, or a console/operator with a trailing single online player target. **S**: console-compatible server operation. Help/menu commands are console-compatible unless explicitly noted.
- **R**: diagnostic intent, no spending/grants/reset/rebuild. **R+**: diagnostic with normal reconciliation, cache, chapter alignment, or link refresh effects. **W**: state-changing operation. **F**: generated/report-file operation. Diagnostics can still write normal server logs.
- Every Admin, Debug, and Test branch requires `source.hasPermission(2)` (including its menus). Player branches have no operator requirement. There is no separate mod permission configuration in this tree. Permission remains attached to the original sender during targeting.
- `[player]` uses `EntityArgument.player()`: exactly **one online player**; an online name or a selector resolving to one player. This is not offline editing or mass targeting. With no target, the handler uses the source player; a console must supply one. Targeting preserves sender/output and permission, changing the entity context used by the handler. Successful Admin target actions refresh the target's progression state.
- `<essence>`, `<stat>`/`<bonus>`, `<skill>`, `<milestone>`, and player `<tier>` generally use `StringArgumentType.string()` and the mod's ID resolver. Short IDs default to **`essence_ascendance:`**, not `minecraft:`. Resource IDs follow Minecraft identifier syntax; use **quoted full IDs**, e.g. `"essence_ascendance:offense"`, because Brigadier's unquoted string grammar excludes `:` and `/`. Suggestions deliberately quote qualified IDs. These registry ID resolvers do not lowercase arbitrary input.
- `<category>` in Bonus lists and gameplay diagnostics is a `word()` parsed case-insensitively as `offense`, `defense`, `vitality`, `mobility`, `gathering`, or `utility`. Attunement categories instead resolve Essence IDs. `all` is only accepted where explicitly registered.
- Held **item tier** is a case-insensitive serialized name: `latent`, `dormant`, `awakened`, `resonant`, `ascendant`, `transcendent`; it does not accept a qualified tier ID. Player tiers accept registered short or quoted full IDs.
- Positive long arguments accept **1..9,223,372,036,854,775,807**; nonnegative long arguments accept **0..9,223,372,036,854,775,807**. Registration range is not a promise that a transaction can store or spend that much; overflow, funds, and cap checks remain.
- Success/failure text is sent to the command source; normal helper successes use `sendSuccess(..., false)`, not operator broadcast. Do not publish personal output, target names, UUIDs, owner details, locations, or private config paths when showing examples.

### 3.2 Player operations

Evidence: [C1], [C6], [C7], `progression/StatProgressionService.java`, `progression/TierInvestmentPolicy.java`, [A1], [A2].

| Executable syntax | Source / effect | Prerequisites, output, and consequences |
| --- | --- | --- |
| `/essence` | S / R | General help; operator menus only shown to permission level 2 sources |
| `/essence help` | S / R | Same help as root |
| `/essence status` | P / R+ | Own tier, total available Essence, effective/stored investments and capacity, next Ascension readiness/config failure. Ascension evaluation aligns a stale Attunement ledger with its current chapter; it does not award activity or ascend. |
| `/essence balance` | P / R | Available player wallet totals for all six Essences; excludes Crucible reservoir and invested amounts |
| `/essence balance <essence>` | P / R | One registered Essence wallet balance; unknown ID fails |
| `/essence bonuses` and `/essence bonuses help` | S / R | Bonus command menu |
| `/essence bonuses list` | P / R | Category overview, effective investment/capacity and stat counts; Latent lock notice |
| `/essence bonuses list all` | P / R | Every registered Bonus grouped by category |
| `/essence bonuses list <category>` | P / R | Registered Bonuses in that category, current effective investment and effects |
| `/essence bonuses show <stat>` | P / R | Known stat's ID/category/Essence, stored/effective investment/cap, scaling/equipment applicability details |
| `/essence bonuses invest <stat> <amount>` | P / W | Positive long. Normal investment transaction consumes that stat's Essence from the player's wallet and increases stored investment. Requires funds, a resolvable tier investment policy and room under its cap; rejects at/over-cap, excess amount, overflow, configuration resolution failure, or failed transaction. It does not silently clamp an oversized request. **There is no explicit authoritative-profile readiness check in this command/service**; `TierInvestmentPolicy` uses `EssenceConfigManager.get()`, which can supply bootstrap config when no runtime is installed. Do not claim this command always fails closed with a rejected profile. Outputs amount invested, resulting total/cap and remaining wallet. No command refund/respec branch exists. |
| `/essence attunement` | P / R+ | Own current chapter's seals, category percentages and readiness; requires authoritative server profile; snapshot aligns chapter if stale. At maximum tier shows all seals complete without a next chapter. |
| `/essence attunement help` | S / R | Attunement help |
| `/essence attunement <category>` | P / R+ | Registered Essence category; progress, investment acceleration, activity-method percentages and up to three recent actions; requires authority, using current chapter below maximum tier. At maximum tier shows complete seals/methods and multiplier 1. Does not accept `all`. |
| `/essence ascend` | P / W | Requires valid authoritative adjacent chapter and enough completed seals; changes player tier, resets transient Attunement tracking via lifecycle, refreshes state, may deliver Archive. **Consumes no wallet Essence, Bonus investment, equipment, or Skill.** Fails when not ready, max tier, or configuration unavailable. |
| `/essence milestones` | P / R | All configured milestone provider states: complete/incomplete/unresolved. These are not the requirement for player Ascension. |
| `/essence milestones <milestone>` | P / R | Known configured milestone ID, provider, target and state; not arbitrary preserved saved flags |

**The two meanings of balance must be separated prominently:** `/essence balance` reads a player's **available currency**. `/essence debug balance ...` explains the **saved generated balance profile**. `/essence admin balance rebuild` **recalculates and replaces files/runtime authority**. They are different branches with different permissions and effects.

### 3.3 Admin help endpoints (exhaustive)

Every `EssenceAdminCommands.group(name, help)` below executes a menu both at the listed path and with a trailing **`help`**. All are permission 2, S, R. An unfinished argument chain is not otherwise automatically executable. ([C2])

| Prefix | Executable group/menu paths relative to prefix |
| --- | --- |
| `/essence` | `admin` |
| `/essence admin` | `player`; `item`; `item tier`; `item tier set`; `mappings` |
| `/essence admin player` | `tier`; `tier set`; `essence`; `essence give`; `essence set`; `essence clear`; `bonuses`; `bonuses set`; `bonuses max`; `bonuses clear`; `skills`; `skills grant`; `skills clear`; `skills activate`; `milestones`; `milestones grant`; `milestones revoke`; `crucible`; `crucible clear`; `guide` |

Additional explicitly registered menus: `/essence admin balance` and `... balance help`; `/essence admin player attunement` and `... attunement help`. Bare `... attunement set`, `... fill`, and `... reset` also execute Attunement help. **They do not have registered trailing `help` children**; `help` there is parsed as a category and rejected. There is no separate `guide give help` branch.

### 3.4 Admin actions

All rows require permission 2. Evidence: [C2], [C5]–[C7], [D1], [D2], [A1], [A2], [B1].

| Syntax | Source / effect | Prerequisites, output, and consequences |
| --- | --- | --- |
| `/essence admin player reset [player]` | T / W | Full mod progression reset: wallets, Bonus investments, Crucible reservoir, Skill receipts, selections, ability cooldowns, completed mod milestone flags, Attunement ledger, fractional accounts; returns to Latent, rearms Archive gift, refreshes runtime. Does not remove physical inventory/equipment, reset vanilla advancement progress, regenerate profiles, or delete worlds. Provider-complete milestones can later become effective again. |
| `/essence admin player tier set <tier> [player]` | T / W | Sets known player tier without paying or earning seals. Clears transient Attunement gameplay tracking and refreshes progression; may deliver powered-tier gift. This changes the player, not the held item's tier. |
| `/essence admin player essence give <essence> <amount> [player]` | T / W | Positive long; adds registered Essence wallet amount, reports new balance. Uses `Math.addExact`: numeric overflow fails before storing the updated balance; this command does not saturate or partially credit. |
| `/essence admin player essence set <essence> <amount> [player]` | T / W | Nonnegative long; directly sets wallet amount; no cost |
| `/essence admin player essence clear <essence> [player]` | T / W | Sets one wallet to zero |
| `/essence admin player essence clear all [player]` | T / W | Clears all registered Essence wallets; preserves Bonus investment and Crucible reservoir |
| `/essence admin player bonuses set <bonus> <amount> [player]` | T / W | Nonnegative long; directly sets **stored investment**, not displayed effect magnitude; bypasses normal spending and tier-cap transaction checks. Effective power still follows tier/applicability policy. |
| `/essence admin player bonuses max <bonus> [player]` | T / W | Sets stored investment to current tier cap for that Bonus; no wallet spending |
| `/essence admin player bonuses max all [player]` | T / W | Same for every registered Bonus; no wallet spending |
| `/essence admin player bonuses clear <bonus> [player]` | T / W | Zeroes one investment; no refund |
| `/essence admin player bonuses clear all [player]` | T / W | Zeroes all investments; wallets preserved; no refund |
| `/essence admin player skills grant <skill> [player]` | T / W | Requires current catalog skill; creates missing rank-one purchase receipt at **zero paid cost**. Does not purchase every rank, replace an existing receipt, or guarantee effectiveness under tier/prerequisite/loadout gates. |
| `/essence admin player skills grant all [player]` | T / W | Creates missing zero-cost rank-one receipts for entire current catalog; counts newly granted receipts |
| `/essence admin player skills clear all [player]` | T / W | Removes all receipts, loadout selections and ability cooldowns; no refund; no single-skill clear branch |
| `/essence admin player skills activate <skill> [player]` | T / W | Requires known owned skill and valid activation plan using owned prerequisites. Clears required automatic suppressions and sets selectable prerequisite/choice assignments. Does not buy skills/ranks or bypass tier/runtime effectiveness checks. |
| `/essence admin player milestones grant <milestone> [player]` | T / W | Only configured INTERNAL milestones or catalog-referenced permanent Skill milestone IDs. Internal definition resolves its internal target flag; Skill gate override uses definition ID. Cannot directly grant a vanilla advancement/provider's own progress. Reports effective completion. |
| `/essence admin player milestones revoke <milestone> [player]` | T / W | Same identifier restriction. Removes mod stored flag, refreshes, then checks effective state. Can report failure when provider already completes the gate; that failure does not mean no flag mutation occurred. Reset provider progress through its own system if intended. |
| `/essence admin player crucible clear <essence> [player]` | T / W | Clears target's shared Crucible reservoir of one Essence **and** its `dissolution/<essence-id>` fractional carry; wallet and investments are separate |
| `/essence admin player crucible clear all [player]` | T / W | Same for all registered Essences; not all owners or all machines |
| `/essence admin player guide give [player]` | T / W | Gives Archive regardless of prior receipt or tier; places in inventory or drops with target pickup ownership. Records successful gift receipt; failure leaves it pending. No Essence cost. |
| `/essence admin player attunement set <category|all> <percent> [player]` | T / W | Percent integer **0..100**; requires authoritative profile and current chapter; sets selected normalized seal progress, clears associated method/repetition/recent/root state; relevant exploration/healing bookkeeping is reset. Marks saved/revision state and refreshes. Can automatically promote Latent to Dormant when enough seals result. No automatic paid-tier Ascension. |
| `/essence admin player attunement fill <category|all> [player]` | T / W | Same service with 100% |
| `/essence admin player attunement reset <category|all> [player]` | T / W | Same service with 0%; not full progression reset |
| `/essence admin item tier set <tier> [player]` | T / W | Target must hold Ascendance equipment or an Essence Focus in main hand. Sets that item's tier and clears incomplete infusion data. Non-Latent unbound equipment becomes soulbound to target; an existing binding is preserved, including on demotion. Focus mutation handles Latent separately and maps other equipment-tier names to Focus tiers. No material/Essence cost; no arbitrary third-party equipment support here. |
| `/essence admin config` | S / R | Active config path/version/profile, pylon settings, definition counts, harvest progression safety warnings. It is not a config reload and is not a dump of all keys. It can display bootstrap/provisional config if server authority is unavailable. |
| `/essence admin mappings reload` | S / F | Requires running server/thread. Validates/reinstalls the saved profile and mappings, refreshes players and synchronizes runtime/tooltips; **no fresh environment generation if file exists**. Missing file follows normal generation selection. Rejected reload keeps previous installed state. Reports mapping generation/status and paths. Does not load a separate mapping override directory in this implementation. |
| `/essence admin balance rebuild` | S / F, W | Explicit current-environment generation, inputs, validators, durable profile replacement, mapping/runtime install, player refresh and client sync. Replaces generated authority and produces routine reports; may block server thread significantly. Rejection retains previous valid authority. |
| `/essence admin balance export` | S / F | Requires active snapshot. Rewrites derived reports/diagnostics from saved active data, with exhaustive acquisition source/dependency CSVs. No provider scan, solver, balance recalibration, or gameplay price change. Does not reread edited TOML as live config. |

### 3.5 Debug help endpoints (exhaustive)

Permission 2, S, R: `/essence debug` and `... debug help`; the following paths and each path with `help`: `debug player`, `debug item`, `debug machine`, `debug mappings`, `debug balance`, `debug player skills`, `debug player milestones`, `debug player gameplay`, `debug player attunement`. Bare `debug player attunement show` and `... history` also show help. Other incomplete leaves such as `debug balance cost`, `... bonus`, `... skill`, `debug player skills show`, and `... milestones show` require their argument and do not execute help. ([C3], [C5], [C6])

### 3.6 Debug actions

All permission 2. Debug targeting reads with a changed entity context and **does not invoke the Admin mutation refresh wrapper**. Diagnostic intent still permits normal cache/reconciliation effects explicitly described here. Evidence: [C3], [C5], [C6], [B1], [B2].

| Syntax | Source / effect | Preconditions and output |
| --- | --- | --- |
| `/essence debug player summary [player]` | T / R+ | Player tier/profile, stored/effective investment and capacity, registry/profile/provider counts and next Ascension state; evaluation can align current chapter. This summary does not enumerate wallets or reservoir balances. |
| `/essence debug player equipment [player]` | T / R | Equipment activation profiles, slots and same-stat strength merging; not a grant or infusion |
| `/essence debug player skills summary [player]` | T / R | Catalog/implemented counts, receipts, effective/suspended/replaced counts, selections and revision |
| `/essence debug player skills owned [player]` | T / R | Summary plus saved receipts/ranks/actual paid costs, including preserved unknown definitions |
| `/essence debug player skills loadout [player]` | T / R | Summary plus saved selection groups; does not alter choices |
| `/essence debug player skills show <skill> [player]` | T / R | Syntax-valid mod-default ID; current definition or preserved owned receipt. Reports ownership, gates, relationships, requirements and evaluation; unknown/unowned ID fails. No rank argument here. |
| `/essence debug player milestones list [player]` | T / R | Stored permanent/internal flags, including unknown preserved flags |
| `/essence debug player milestones show <milestone> [player]` | T / R | Configured provider state and permanent capture; also known Skill gate or already-stored unknown ID without config. `resolveAll` is read-only. |
| `/essence debug player gameplay <category> [player]` | T / R+ | Six accepted category enum values. Current applied Bonus/equipment/native attribute context; calls normal equipment attribute sync (Mobility/Utility also mobility sync). **That sync can clamp current health down to the resolved maximum** when attributes were stale. No spending or progression grants. Category-specific runtime details include combat, healing, movement, gathering and utility. See `equipment/EquipmentAttributeService.java`. |
| `/essence debug player gameplay shield [player]` | T / R | Shield/blocking/combat prevention runtime snapshot |
| `/essence debug player gameplay projectiles [player]` | T / R | Projectile diagnostics/adapters/control/outcomes; no projectile launch |
| `/essence debug player gameplay posture [player]` | T / R | Current Evasive/Bulwark/Adaptive posture runtime diagnostics |
| `/essence debug player gameplay status [player]` | T / R | Harmful-status interception/mirror policy and live diagnostic state |
| `/essence debug player attunement summary [player]` | T / R+ | Requires authority; seals/current chapter or completed maximum-tier state; snapshot can align ledger chapter below maximum tier |
| `/essence debug player attunement show <category> [player]` | T / R+ | Requires authority; category progress, multiplier, generated target, method progress. Maximum tier shows complete seals/methods, target 0 and multiplier 1 without a next chapter. |
| `/essence debug player attunement history <category>` | P / R | Own page 1, authority required |
| `/essence debug player attunement history <category> <page> [player]` | T / R | Page integer **1..2,147,483,647**, four stored actions/page; rejects beyond actual page count. Reports credited/rejected reasons, action/source signature, base value, investment/repetition/variety and final contribution. **A target requires the page argument first.** Help's `[page] [player]` notation overstates optionality. |
| `/essence debug player attunement activities` | S / R | Category menu from registered methods; does not require active profile |
| `/essence debug player attunement activities <category>` | S / R | Registered activity ID/calibration family/units for selected Essence category |
| `/essence debug player attunement reachability` | S / R | Requires authority; generated chapter breadth/menu |
| `/essence debug player attunement reachability <tier>` | S / R | Known **from-tier** with a chapter, stage-accessible and base-method counts; max tier without chapter fails. Generated accessibility estimate is not a survival-play proof. |
| `/essence debug item inspect` | P / R | Requires a **first-party `EquipmentProfileItem`** in main hand and a known profile; otherwise fails. Reports player-tier baseline reference, held profile strengths and relevant ranged/caster runtime details. This is not general inspection of every registered item or Focus; use mapping/item-tier/Archive views for their separate scopes. |
| `/essence debug item mapping` | P / R | Nonempty main-hand stack; active stack mapping/result/source/output eligibility. Does not rerun procedural valuation. |
| `/essence debug item baselines` | P / R | Equipment profile baseline reference at **player tier**; explicit warning that real equipment uses its own item tier; harvest level and armor weights |
| `/essence debug machine crucible` | P / R+ | Look directly at correct block entity within **8 blocks**, non-fluid pick. Input lanes, mapping summaries, storage, ownership/access, structure/pylon processing diagnostics. Structure queries may refresh normal cached calculations; no recipe run or Essence spending. |
| `/essence debug machine pylon` | P / R+ | Same 8-block requirement; explicitly refreshes link; Focus, owner, radius/active limit, contribution and link eligibility |
| `/essence debug machine infuser` | P / R+ | Same 8-block requirement; explicitly refreshes link; owner/focus, processing/channel state, recipes/operations, source/target Essence, policy and linked reservoir diagnostics |
| `/essence debug mappings status` | S / R | Last install/rejection, mapping generation/counts/warnings/errors and paths; no scan or reload |
| `/essence debug mappings list` | S / R | Active rule IDs, priorities, item/tag selector and visible outputs/BLOCK; no reload |
| `/essence debug balance summary` | S / R | Active integrity prefix/generator, resources/equipment/enemies, curves, modeled conservation paths/passes and last load milliseconds |
| `/essence debug balance validate` | S / R | Reads **disk** profile, verifies/decodes all required sections, validates it, compares integrity against active snapshot. Different disk/active profile fails with reload/rebuild guidance. No install, rebuild, or report export. Can be expensive. |
| `/essence debug balance item` | P / R | Uses main-hand item's full registry ID to explain saved evidence; empty hand selects air and normally has no saved evidence |
| `/essence debug balance item <id>` | S / R | **Exact full saved item ID**, quoted, e.g. `"minecraft:iron_ingot"`. This handler uses direct saved-map lookup, **not** the short mod-default resolver. Reachability/stage/confidence/supply, opportunity value versus actual yield, bounded evidence/fact/warning samples and equipment axes; no live reanalysis |
| `/essence debug balance bonus <bonus>` | S / R | Registered stat via mod-default resolver; generated maximum effect, curve exponent/purchase style/applicability, per-tier cumulative/segment caps and effects |
| `/essence debug balance skill <skill>` | S / R | Registered skill; rank 1 saved curve/cost/power/base required tier |
| `/essence debug balance skill <skill> <rank>` | S / R | Registration integer **1..1000**; also must fit generated curve length. Reports selected cost/power and maximum purchasable rank. Does not purchase a rank. Base tier displayed here does not enumerate all rank-specific prerequisite/tier gates; use Archive/report for those. |
| `/essence debug balance cost <path>` | S / R | Quoted JSON Pointer beginning **`/runtime/`**, resolving to one primitive scalar. `~1` and `~0` escape `/` and `~`, array indices are zero-based. Nonexistent/object/array targets fail. Prints saved scalar, not a recomputed cost. |

### 3.7 Test commands

Permission 2. `/essence test` and `... test help` execute the test menu; `/essence test skills` and `... test skills help` execute the Skill-test menu. These are S/R. There are no player targets on the test leaf registrations. ([C4])

| Syntax | Source / effect | Actual exercised scope |
| --- | --- | --- |
| `/essence test luck` | P / R+ | Reconciles normal mobility/Luck modifier, then checks expected modifier presence and finite actual native Luck. Reports PASS/FAIL. Does not roll loot, grant Essence, or measure a statistical luck distribution. |
| `/essence test activation` | P / R | Snapshot of nonzero Bonus activation for worn passive equipment plus main hand; same-stat contexts merge by MAX. Shows active/inactive/zero counts. Durability is item-local and action/offhand contexts can differ; this is not simulated combat. |
| `/essence test skills milestones` | P / R | Resolves all catalog-referenced permanent milestones and independently evaluates live providers; checks resolvability and completed projection. **Does not capture/grant completion** (`resolveAll`, not `captureCompleted`). |
| `/essence test skills effects` | S / R | Runs pure implementation invariant diagnostics (registered handlers/relationships/config/math/payload contracts). Reports failure list or PASS. Does not simulate combat, execute all skills in a world, or prove integration compatibility. |

### 3.8 Operational classification and examples

Safe diagnostic starting points: `debug mappings status`, `debug balance summary`, explicit `debug balance item "minecraft:iron_ingot"`, `debug balance bonus melee_damage`, and a quoted scalar pointer discovered in `runtime_parameters.csv`. Disk validation reads files and logs its work; export **writes derived files**; reload **reinstalls authority**; rebuild **recalculates authority**. Admin mutations are not diagnostics even if their menu calls them testing shortcuts.

Do not place reset, grant, max, clear, tier-edit, Attunement override, or rebuild commands in a copy-and-paste diagnostic block. Use invented player placeholders only in syntax explanations, never captured player identifiers in published output.

## 4. Configuration and generated-file inventory

### 4.1 Editable inputs and authority

| Portable path | Role / lifecycle |
| --- | --- |
| `config/essence_ascendance.toml` | High-level generator policy, not a live runtime overlay |
| `config/essence_ascendance/balance_overrides.toml` | Factual corrections and exact generated-value overrides; consumed during generation |
| `config/essence_ascendance/generated_balance.json.gz` | **Sole saved server balance authority**, sealed and validated; do not edit as a config file |
| `config/essence_ascendance/README_REPORTS.txt` | Generated file guide after successful report export |
| `config/essence_ascendance/reports/` | Derived Markdown/CSV values and explanations |
| `config/essence_ascendance/diagnostics/` | Detailed provenance, report state, long text, optional compatibility evidence, failure artifacts and archived exports |
| `<world>/data/essence_ascendance_players.dat` | Overworld SavedData: player currency/progression/receipts/Attunement; sensitive, not public balance evidence |
| `<world>/data/essence_ascendance_fractional_accounts.dat` | Owner/account fractional cost/conversion carry; sensitive, not generated profile authority |

Input scaffolding copies bundled `/balance/essence_ascendance.toml` and `/balance/balance_overrides.toml` **only when missing**, using create-new behavior. Existing inputs are not overwritten. Each input is bounded to **4,000,000 bytes**. Unknown keys/tables, duplicate fact IDs, wrong types, contradictions, invalid exact pointers and invalid resulting profiles fail generation. The supported TOML subset intentionally excludes inline tables, dates, hexadecimal numbers, multiline strings and dotted bare keys. Numeric values must be finite. ([F1], [F2], [F3], [F4], [B1], [B2], [D1])

The Java `config/` package contains typed runtime structures, not independently editable config files: `EssenceServerConfig`, `InfuserBalanceSettings`, `LatentOreWorldgenSettings`, `SkillEffectBalanceSettings`, `ShieldBalanceSettings`, `ProjectileBalanceSettings`, `GuardBalanceSettings`, `PostureBalanceSettings`, `StatusBalanceSettings`, `GatheringBalanceSettings`, `UtilityBalanceSettings`, `MobilityBalanceSettings`, and the Vitality settings family (`VitalityBalanceSettings`, `VitalityDamageBalanceSettings`, `VitalityWardBalanceSettings`, `VitalityDeathDefianceBalanceSettings`). Defaults in these classes support generation/bootstrap; they must not be advertised as fixed values installed on every server. Inspect the resolved profile's `/runtime/...` fields instead.

### 4.2 Policy key inventory

These are **input defaults**, not guaranteed resolved gameplay results. Ranges come from parser/record validation. All listed numeric ranges are inclusive. Root `schema_version` accepts integer `1` (omitted uses current input schema). ([F2], [F3])

| Table / keys | Input default(s) | Accepted bounds / meaning |
| --- | --- | --- |
| `[power] overall` | `1.0` | `0.1..4`; added-power positioning relative to attainable references |
| `[power] early`, `mid`, `late`, `apex` | `0.8`, `0.9`, `1.0`, `1.1` | Each `0.1..4`; relative band emphasis, not fixed item statistics |
| `[progression] length`, `cost_pressure` | `1.0`, `1.0` | Each `0.1..10`; generated journey length and investment affordability; neither is an Ascension wallet prerequisite |
| `[generation] routine_seconds`, `boss_seconds`, `survival_seconds` | `4`, `40`, `10` | `0.1..300`, `0.1..3600`, `0.1..300` seconds; explicit modeled encounter/survival assumptions |
| `[generation] entry_resource_effort`, `effort_growth` | `80`, `3.5` | `1..100000`, `1..20`; resource-relative budget construction |
| `[attunement] pace` | `1` | `0.1..10`; activity speed, higher means faster seals |
| `[attunement] maximum_acceleration` | `1` | `0..4`; added maximum future-credit multiplier at category development reference |
| `[attunement] repetition_floor` | `0.15` | `0.01..1`; legitimate repeated source stays productive |
| `[attunement] variety_strength` | `0.25` | `0..1`; bounded bonus for source variety |
| `[attunement] history_window` | `64` | Integer `8..256`; reference-work history, not event callback count |
| `[attunement] early_effort_fraction`, `onboarding_effort_fraction` | `0.75`, `0.50` | Each `0.1..1`; early powered chapter and automatic Latent onboarding |
| `[attunement] breadth_exponent` | `1` | `0.25..4`; shape of increasing required category breadth |
| `[builds] partial_viability` | `0.65` | `0.1..1`; semantic partial-participation allocation |
| `[builds] composition_safeguard` | `0.75` | `0..1`; stacking safeguards; zero does not remove all validators |
| `[budget] equipment`, `nexus`, `skills` | `0.40`, `0.35`, `0.25` | Each `0.01..0.98`; sum must equal 1 within `0.000001`. Semantic allocation, not percentages multiplied onto live damage. |
| `[economy] automation_pressure`, `bulk_resource_penalty` | `0.65`, `0.60` | Each `0..1`; supply/abundance pressure |
| `[economy] conversion_loss_pressure` | `0.10` | `0..0.95`; generation policy, not one loss percent reused for every transaction |
| `[policies] flight`, `mining` | `"preserve_progression"` | `preserve_progression`, `match_pack`, `restrict`; retains prerequisite checks |
| `[policies] resources` | `"balanced"` | `conservative`, `balanced`, `abundance_aware` |
| `[policies] outliers` | `"exclude_unsupported"` | `exclude_unsupported`, `winsorize`, `include_attainable` |
| `[diagnostics] warning_confidence` | `0.60` | `0..1`; threshold highlighted for review, not a compatibility promise |
| `[diagnostics] expanded` | `true` | Boolean; expanded generation evidence; stable report/diagnostic facilities remain |

### 4.3 Latent Ore keys

`[latent_ore] automatic_dimensions = true` controls ordinary custom-dimension discovery. Optional `[latent_ore.overworld]`, `.nether`, `.end` tables accept **`enabled`, `vein_size`, `veins_per_chunk`, `min_y`, `max_y`, `discard_chance_on_air_exposure`**. Defaults: enabled, size 9, 16 attempts/chunk, zero exposure discard; Y ranges Overworld `-48..64`, Nether `16..112`, End `0..80`. Bounds: size integer `1..64`; attempts integer `0..128`; Y ordered within `-2048..2048`; discard `0..1`. ([F5], `config/LatentOreWorldgenSettings.java`)

`[[latent_ore.dimension]]` additionally accepts required **`dimension`**, optional **`hosts`** (up to 32 distinct exact block IDs), and the same distribution keys. Omitted/empty hosts means automatic discovery; duplicates and invalid IDs fail. Exact dimension overrides own distribution when specified; `enabled=false` excludes the dimension. These declarations must match real supported substrates; block names alone do not prove generated terrain exists.

`LatentOreBalanceGenerator` scales baseline supply using **final usable early Essence yields**, especially weakest-category coverage: scarce versus broadly supplied factors, with automatic enabled nonzero distributions maintaining the ordinary minimum attempt/vein floor. Exact dimension distributions and exact runtime worldgen overrides bypass automatic tuning/floor. `PrimarySubstrateDiscovery` inspects supported generator/default terrain data; it does not generate/scan chunks. Unknown generators are skipped with diagnostics. Existing chunks are not retroactively populated simply by rebuilding. Reports expose supply reasoning and selected distributions; actual accessibility in a void, ocean, enclosed or custom world still needs survival verification. ([W1], [W2])

### 4.4 Fact and exact override schema

`[[fact]]` requires `kind` and `selector`, with at most **10,000 facts** per input. `id` is optional, derived from kind/selector when omitted; explicit stable unique IDs are preferable. Priority integer **-100000..100000**, default **1000**. Higher-priority facts win; equal-priority conflicts are deterministic and reported, not inferred away. Sources, reasons, confidence and conflict information remain in provenance. Match selectors to real registry/source/provider IDs; unmatched facts warn. ([F4], [E1])

| Field group | Accepted keys / constraints |
| --- | --- |
| Kinds | `item`, `block`, `item_tag`, `block_tag`, `recipe`, `recipe_family`, `source`, `enemy`, `provider`, `capability` |
| Boolean facts | `attainable`, `disabled`, `creative_only`, `administrative`, `joke`, `quest_gated`, `include_reference`, `renewable`, `passive_generation`, `flight`, `flying_speed_compatible`, `vein_mining`, `area_mining` |
| Numeric facts | `confidence`, `resource_value`, `throughput`, `health`, `armor`, `damage`, `attack_speed`, `toughness`, `mining_speed`, `harvest_level`, `durability`, `enchantability`, `startup_cost`, `marginal_cost`, `parallelizability`, `player_attention`, `processing_time`, `output_count`, `probability`; nonnegative finite; confidence/probability/attention also at most 1. `output_count` must be whole and **1..2,147,483,647**; use `probability` for fractional expected output. |
| Text facts | `stage`, `availability`, `automation`, `classification`, `reason`, `dimension`, `gate`, `slot` |
| Array facts | `capabilities`, `dependencies`, `acquisition_sources` (strings) |
| Stage | `entry`, `early`, `mid`, `late`, `apex` |
| Availability | `finite`, `renewable_manual`, `renewable_automated`, `effectively_infinite`, `unknown`, `administrative` |
| Automation | `none`, `player_gated`, `bounded`, `scalable`, `passive`, `infinite`, `unknown` |
| Equipment slots | `head`, `chest`, `legs`, `feet`, `offhand`, `mainhand_melee`, `mainhand_bow`, `mainhand_crossbow`, `mainhand_caster`, `mainhand_tool`; `body` accepted as evidence but excluded from player equipment frontiers |
| Enemy classification | `routine`, `elite`, `boss`, `apex`, `unknown` |

Resource opportunity value, startup burden and marginal cost use the same normalized value units. Throughput is items/sec; `processing_time` is server ticks (20 ticks/second), passed directly to `ProductionGraph.Process.processingTicks` by `EconomyGenerator`; attention 0 means passive and 1 continuous active work. Per-event expected loot is not a measured throughput. Unknown costs/rates should stay omitted, not be entered as zero. Contradictions such as disabled+attainable, creative-only+included reference, or infinite+nonrenewable reject generation. Some accepted fields are meaningful only to particular consumers; unused/unsupported declarations remain diagnostic limitations. ([Q1])

`[exact]` uses **quoted JSON Pointer keys** and primitive or primitive-array values. Only supported existing paths under `/runtime/` and `/economy/` are allowed by generation; protected metadata/composition/profile-identity changes are rejected. Escape `~` as `~0`, `/` inside a key as `~1`; use zero-based array indices. Read `runtime_parameters.csv` or saved JSON for paths. Example syntax:

```toml
[exact]
"/runtime/statMaxBonuses/essence_ascendance:melee_damage" = 12.0
```

This is an illustrative exact value, not a recommended universal balance setting. Exact overrides are generation inputs, not live edits. They pin that field against future adaptation, but still must pass native unit/grid, rank/build, runtime and economy validators. Exact item dissolution routes must be **nonnegative whole Essence**; fractional requests fail. Conservation can reduce a requested yield. Changing intrinsic Bonus magnitude can reprice the track before separately requested exact cost fields are reapplied. Prefer correcting a false acquisition/automation/capability fact before pinning a symptom numerically. ([B1], [R1], [Q1])

### 4.5 Generated report and diagnostic files

All following paths are beneath `config/essence_ascendance/`. CSV files live in `reports/`. Conditional diagnostics/tables are written only when the saved profile contains the relevant evidence. No native XLSX report is generated; CSVs are spreadsheet-readable. ([F6], [F7], [F8])

| Files | Content / caveats |
| --- | --- |
| `README_REPORTS.txt`; `reports/balance_report.md` | Generated guide and overview; routine Markdown lists are capped, not exhaustive proof |
| `valuation.csv`, `warnings.csv` | Final item opportunity values/yields, resource/source state and complete diagnostic warnings |
| `valuation_sources.csv`, `valuation_source_dependencies.csv` | **Explicit exhaustive export only**; source details and dependencies, joined by `item_id` + `source_index`. Ordinary rebuild archives prior copies instead of presenting stale detail as current. |
| `equipment.csv`, `equipment_capabilities.csv` | External references and axes, joined by item/slot/stage |
| `generated_equipment.csv` | Ascendance native baselines by tier; armor/toughness represent baseline full set, not each armor piece |
| `curves.csv`, `bonus_tracks.csv`, `skill_rank_parameters.csv` | Saved Bonus/Skill costs, effects/checkpoints, meaningful native rank parameters |
| `builds.csv`, `build_skill_ranks.csv`, `build_selections.csv`, `build_category_pressure.csv` | Build projections, active/contributing ranks, choice selections and category pressures; join projection/scenario. Conservative axis maxima may belong to different builds, not one simultaneous loadout. |
| `combat_builds.csv`, `combat_assumptions.csv`, `combat_defense_pressure.csv` | Numeric model limits and assumptions; these are not observed fights |
| `evidence.csv`, `evidence_dependencies.csv`, `quest_evidence.csv` | Resolved fact/source origin, dependencies and normalized quest evidence/unknowns |
| `runtime_parameters.csv`, `ascension.csv`, `invariants.csv` | Scalar runtime pointers, player chapter/harvest-access summary, modeled production/conservation results |
| `attunement_targets.csv`, `attunement_breadth.csv`, `attunement_methods.csv`, `attunement_calibration.csv`, `attunement_pacing.csv`, `attunement_investment.csv`, `attunement_repetition.csv`, `attunement_reachability.csv` | Category targets, required/optional seals, activity units/rates, single-source estimates, development acceleration and reachability scope; chapter joins |
| `latent_ore_supply.csv`, `latent_ore_policy.csv`, `latent_ore_worldgen.csv` | Final early-resource coverage, ore assumptions and distributions |
| `projectile_policy.csv`, `guard_policy.csv`, `posture_status_policy.csv`, `vitality_policy.csv` | Saved system contract explanations; no separate tuning authority |
| `competitive_capabilities.csv`, `competitive_frontiers.csv`, `competitive_candidates.csv`, `competitive_unsupported_counts.csv`; `reports/competitive_capabilities.md` | Admitted competition, frontier representatives, unsupported candidates and complete unsupported counts; optional saved metadata |
| `adaptive_balance.csv`, `skill_availability.csv`, `skill_ranks.csv`; `reports/adaptive_balance.md` | Generated feature values, catalog/candidate/final tier decisions, prerequisite clamps, admitted witnesses, PARTIAL/UNKNOWN coverage and pricing/rank explanation |
| `diagnostics/report_manifest.json` | Installed integrity, export state/detail level; incomplete/stale/missing reports must not be mistaken for authoritative active data |
| `diagnostics/report_text.json` | Lossless text sidecar for CSV cells exceeding 32,767 characters; keep with CSVs whose cells reference it |
| `diagnostics/pack_metadata.json`, `bonus_tracks.json`, `generation_evidence.json`, `routing_classification.json` | Saved generation metadata, track reasoning, environment/provider evidence and routing classifications |
| `diagnostics/competitive_capabilities.json`, `adaptive_balance.json` | Full optional saved competition/adaptation projections |
| `diagnostics/generation_comparison.json` | Archived comparison information when present; not a second profile |
| `diagnostics/legacy_reports/` | Preserved old root exports/comparisons and older exhaustive acquisition CSVs with referenced text sidecars |
| `diagnostics/failed-providers-<random>.json` | Candidate collection/provider failure evidence |
| `diagnostics/failed-generation-<random>.json.gz` | Diagnostic-only failed runtime-generation evidence for isolated investigation; cannot install as live authority |
| `diagnostics/uncommitted-profile-<random>.json.gz` | Validated candidate captured when durable commit failed; does not prove installation |

Atomic writes use sibling temporary **`.pending`** files, fsync and atomic replacement. A filesystem without the required atomic move fails closed; temporary files are cleaned in the writer's finally path. Profile compressed-size limit is **256 MiB**, inflated JSON bound **4 GiB**. These are safety limits, not recommended memory sizing. Performance telemetry is emitted to the normal logger and retained in bounded operation snapshots; there is no separate promised `performance.json` authority. ([B2], `balance/generated/BalancePerformance.java`)

## 5. Procedural balance: end-to-end implementation

### 5.1 Environment capture and acquisition

Generation runs on the server thread after the Overworld is inserted into the level map, **before initial spawn search can generate chunks**. A missing profile triggers generation; saved profiles bypass this work. `GenerationRecipeReadiness` can prepare audited late Ars Unification recipe publication for current resource/recipe epoch, and excludes unsupported/unready recipe namespaces with diagnostics. It is not a generic promise to execute arbitrary scripts or machine behavior. ([L2], [B1], `balance/generated/GenerationRecipeReadiness.java`)

`GenerationDataSnapshot` captures the current registry/resource/recipe/dimension context and installed-version facts; consumers share it and require it to remain current. It supports natural block, effective loot, structures, spawn evidence, trades, recipes/interactions, native capability actions, quests and provider facts. Ordinary data inspection does not mean generating terrain or operating a farm. Missing/unreadable optional data is not an authoritative empty dataset. ([E1], [E2])

`ProceduralValuationEngine` constructs a shared cached index, solves acquisition by bounded synchronous previous-round relaxation, then routes values to Essences. It visits ingredient alternatives, carries acquisition ancestry and rejects a cheaper path that depends on its own descendants; unresolved paths remain diagnostic instead of seeding access. Native loaded recipes include crafting, cooking, smithing, stonecutting and modeled interactions; quantities, expected outputs and reusable/returned inputs matter. Registered result/name/rarity alone is not proof of attainable acquisition or equipment power. ([Q2], [Q3])

Acquisition includes constructive setup/input/tool/station paths where supported. Renewable classification by itself does not supply exact finite stock or a recurring recipe input. Natural terrain, crops/trees, biological drops, encounter conditions, native trading stock/restock and supported quests have separate access contracts. Finite trades or sampled offers establish conditional possibilities, not guaranteed villagers/offers or an exhaustive distribution. Shared stock claims and exact components matter for composed configurations such as enchanted equipment or charged jetpacks.

Six Essence routes use native item/function/source evidence and classifications rather than a universal equally divided payout. Routing and eligibility can suppress unavailable, unsupported or unsuitable items. Names can nominate candidates; they do not establish a custom spell, machine rate, flight compatibility, or a hidden tool action. Full saved routing diagnostics explain the accepted channels. Relevant files: `valuation/EssenceRoutingPolicy.java`, `GeneratedYieldEligibility.java`, `RoutingGenerationDiagnostics.java`, `ProceduralItemNomenclature.java`.

### 5.2 Economic value, supply pressure, and conversion safety

**Opportunity value is not dissolution yield.** Economy generation starts from resolved resource acquisition value and applies suitability, abundance/automation policy and quantified source pressure before producing extractable Essence. Throughput, parallelism, attention, marginal and setup cost are separately visible facts. Unknown throughput stays unknown; per-event output does not silently become items/sec. Startup costs are sunk setup, not a consumable budget credited on every output. Pressure can lower proposed yield, not inflate it above the underlying base. ([Q1], [Q4])

Balanced and conservative policies give zero generated dissolution to explicitly **effectively infinite** resources. Abundance-aware can allow discounted positive yields; conservation still applies. A useful item may legitimately yield zero when its renewable/automation/conversion evidence requires it. This does not necessarily mean a missing mapping bug.

`ProductionGraphAdapter` shares loaded recipe/trade/interaction evidence, including counts, alternative ingredients, catalysts, returned containers/byproducts, probabilities, stock constraints and source metadata. Generic third-party recipe inputs/results are partial evidence: hidden energy, machine behavior, quantities, components and byproducts require a typed production contract. The extension registry for `ProductionProvider` exists; it is not evidence that a built-in adapter models every machine from every mod. ([Q3], [Q1])

Generated ordinary item yields follow **`whole_essence_v1`**. Proposed totals round to whole Essence, conservation and proportional category routing use whole units, and final displayed yield equals actual ordinary credit. Reversible compression/decompression families are reconciled together, avoiding invented profit from rounding individual forms. Exact fractional dissolution overrides are rejected. Fractional accounting remains separately relevant to costs/conversions; it must not be used to claim hidden fractions are ordinary item yields. ([Q1], [Q5], [D1])

`EconomyConservationSolver` monotonically reduces outputs over complete modeled processes (up to 256 relaxation passes), handling alternatives, expected multiple outputs, catalysts/returns and conversion families. If gainful paths still violate bounds, unsafe outputs and downstream dependents are disabled; final invariants must pass. Incomplete processes are excluded with explicit warnings: unknown operating costs/variants are **not zero**. Finite stock/restock and proven native resource production can supply a bounded source allowance; trading is not automatically an instantaneous unrestricted material conversion. Conversion/carrier extraction runtime efficiencies must match the canonical economy processing policy. ([Q5], `balance/economy/BoundedProductionPolicy.java`, [B1])

The guarantee is limited to **modeled evidence and invariants**. An opaque scripted recipe, machine, custom component action, or unsupported conversion cycle can lie outside the model. Do not claim perfect exploit elimination across arbitrary modpacks.

### 5.3 External equipment, competition and power budgets

Pack equipment references use supported native attributes/components, equipment slot and **reachable acquisition**. Disabled, administrative, creative-only and unsupported cases are excluded; body/non-player gear is not a player armor reference. Custom items need explicit supported slot and measurable axes plus access evidence. Enemy references likewise need valid registry identity and supported measured/default or explicit attributes; a name containing “boss” is insufficient. ([E1])

`RobustFrontiers` builds progression-aware comparators under outlier policy. `CompetitiveCapabilities` adds supported native consumables, enchantments, trades/anvils, production, factual declarations and audited optional providers. Measurements retain units, source configuration, scope, operation, conditionality/uptime, cost and access. Unknown units and unsupported configurations are candidates/warnings, not admitted measured power. Binary flight, area mining or indestructibility is not a measured speed/rate or infinite EHP. ([E3], `balance/engine/RobustFrontiers.java`, `NativeCapabilityReader.java`, `NativeEnchanting.java`, `NativeTradeProgression.java`, `NativeAnvil.java`)

`AdaptiveCompetitionCalibration` consumes **already collected** admitted representatives, not a new census of the world. Functional axes/relationships determine response; alternatives are compared rather than summing independent mods. Confidence, supported units, conditional uptime and diminishing response limit pressure. A source/mod ID is provenance, not a special pack-name multiplier. PARTIAL/UNKNOWN availability coverage does not certify that all earlier alternatives were checked. ([R2])

`RuntimeBalanceGenerator` resolves category affordability, tier budgets, equipment, Bonus tracks, Skills, machine/infusion policy, effects, worldgen and Attunement. Resource-relative budgets use observed median supply, entry effort, effort growth, journey length and cost pressure; they are not a hardcoded universal currency total. Encounter DPS and survival windows are explicit assumptions. Native-unit quantization and composed output validators constrain the result. ([R1])

The current documented default target policy is Transcendent equipment at pack-reference parity, external gear plus developed Nexus at 2x, external gear plus ranked Skills at 2x, combined build at 3x; only the modeled low-health Desperation burst can use 4x while sustained output remains 3x. Combined powered-tier ceilings progress 1.5/1.7/2/2.5/3x. These are **checked modeled output envelopes under the configured policy**, not unconditional promises of player DPS or separate multipliers applied to each attack. Budget shares allocate semantic headroom; equipment keeps its foundation without a tax for imaginary Nexus/Skill ownership. ([R1], `balance/runtime/BuildPowerTargets.java`, tests `NativeEquipmentHeadroomTest.java`, `BuildPowerTargetsTest.java`)

### 5.4 Bonuses, Skills, prerequisites and ranks

Bonus tracks are resolved per stat using intrinsic function/utility, category economy, native response and compatible competitive routes. They can have different costs, start/completion tiers and tier segment shapes. `BonusTrackCurve` is the common purchase/projection curve. Sliders purchase continuously; native thresholds inform meaningful checkpoints and valuation, not snapping every purchase to threshold steps. Stored investment is distinct from tier-limited effective investment and equipment applicability. ([R3], `progression/BonusTrackCurve.java`, `progression/TierInvestmentPolicy.java`)

Skills retain catalog relationships, prerequisite **ranks**, milestones, rank-tier/requirement gates, choices/exclusions, replacements and runtime conditions. Generated functional availability can change candidate/base required tier when admitted evidence supports it, then clamps prerequisites/setup access. Catalog tier, candidate tier and final generated tier are separate report columns. Earlier competition does not waive an operating setup or permanent milestone. `SkillDefinition.requiredTierId()` consults resolved runtime; rank-specific gates are additional. ([R2], [R4], `skill/SkillDefinition.java`, `skill/SkillStateEvaluator.java`)

Generated rank curves publish native gameplay parameters and costs. A rank must deliver a meaningful supported improvement on its native grid; invalid/sub-floor/non-improving projections are removed, binary abilities remain one meaningful state, and maximum purchasable rank matches published ranks. No wiki promise should turn catalog projection count into guaranteed purchasable ranks. Build projections account for prerequisite funding, active versus contributing ranks, choice selection and actual compatible routes. ([R4], `skill/ProgressionRequirements.java`, `skill/balance/SkillLoadoutProjection.java`)

Normal Nexus transactions validate server-side saved state/revisions and affordability before committing; committed Skill evaluation supplies effective state. This is distinct from an explicit generated-profile readiness guard. Admin grants bypass purchase costs but do not imply effectiveness. Historical receipts retain actual paid costs/category for refunds and Attunement acceleration across repricing. Milestone providers ship for vanilla advancements and mod internal flags; the provider registry is extensible. Permanent Skill-gate resolution is read-only; capture is a separate explicit lifecycle/gameplay operation. Existing captures remain valid after configured targets change. ([D2], `skill/CommittedSkillService.java`, `progression/AscendanceNexusTransactionService.java`)

### 5.5 Category Attunement, player Ascension and infusion

Player Ascension depends **exclusively on current-chapter Category Attunement seals**. There is no wallet, total Bonus investment, owned-Skill or item requirement and no Essence payment. Latent onboarding auto-promotes to Dormant when ready; subsequent powered transitions use normal Ascension/Nexus action. The next adjacent chapter must exist in the authoritative profile. Final breadth leaves a category optional under default policy. ([A1], [A2], `balance/runtime/AttunementGenerator.java`)

Activities are registered with category, calibration family and units. Core examples: damage/defeats (Offense), real incoming/prevented/reflected damage (Defense), health/hunger/status outcomes (Vitality), running/swimming/flying/exploration (Mobility), resource/crop/fishing outcomes (Gathering), XP and accepted station/trading/repair operations (Utility). Method registration is not proof every outcome is eligible; server hooks validate actual work/health/value changes, actor/source and supported event attribution. Reflection/flying can be non-base methods; zero-investment base routes remain required. ([A3], `attunement/AttunementGameplay.java`)

Current-category developed Bonus power and **historically paid owned-Skill receipts** accelerate **future** contribution. Bonus development is normalized realized generated power, not simply raw money spent. Wallet and ordinary equipment value do not count. Repetition history advances in reference-work units, not packets/callbacks. Positive repeated-source floor preserves farms; bounded variety rewards diversification. Legitimate large outcomes have no arbitrary per-action contribution cap beyond the remaining seal. Attribution/deduplication prevents double-crediting one root outcome. ([A2], `attunement/AttunementLedger.java`, `progression/BonusDevelopment.java`)

Earned normalized fractions survive balance rate/target changes within the same chapter. A real chapter transition clears that chapter's method/history/discovery accounting. Respecs change future acceleration, not retroactively earned progress. Generated reachability distinguishes base registered routes, saved effective-yield evidence and estimated stage access; it does not assert a specific player has actually encountered or can operate every route.

**Equipment infusion is separate:** Ascendance items and Foci have their own item tiers and partially completed infusion state; linked Infuser operations use generated costs/materials/reservoir and owner/equipment eligibility. Player tier does not silently become item tier. Infusion, Essentium carriers, conversion and repair consume their own generated policy; ordinary item tier commands are testing shortcuts around that gameplay path. Read Archive Equipment/Infuser references for actual values. ([C2], `infuser/EquipmentInfusionData.java`, `infuser/EssenceInfuserRecipeRegistry.java`, `equipment/EquipmentTierData.java`, `config/InfuserBalanceSettings.java`)

### 5.6 Saved authority, validation and client consumption

Candidate generation assembles metadata, input settings/overrides, evidence, economy, runtime, Skills and validation diagnostics. Current envelope: **schema 2**, generator **`pack-balance-1`**, implementation revision **`native-equipment-headroom-46`**, dissolution policy **`whole_essence_v1`**. Metadata retains environment/provider diagnostics and evidence digest. Canonical integrity plus typed round-trip/runtime/economy/reference checks reject corruption and incompatible revision. Diagnostic-only failed candidates cannot install. ([B1], [B2], [B3])

Before publication: validate candidate, preflight compressed runtime transport, prepare item mappings, atomically persist authority, install runtime and active snapshot, then synchronize players. A failed candidate/commit preserves previous valid authority. Derived report failure occurs **after** successful install and does not undo it; command success can coexist with failed report export. Client sync failure similarly does not rollback authority and logs reconnect guidance. This is why report/currentness and client readiness should be checked separately from rebuild success. ([B1])

Server runtime supplies stat scaling, equipment baselines, meaningful Skill curves, machine/infuser policy, effect parameters, Attunement and ore generation through `EssenceConfigManager` and dedicated resolvers. The bootstrap supports early registration/provisional views; it is not authoritative dissolution/Ascension/worldgen. If initial profile load is rejected, the world can continue but dissolution, player Ascension/Attunement and Latent Ore generation remain unavailable until a valid install. **This guard is not universal:** the standalone Bonus investment command resolves `EssenceConfigManager.get()` without checking authority, and some other core views/services also use that fallback. Document the guarded systems specifically rather than promising that every progression operation is disabled. The Archive's presentation boundary excludes bootstrap values. ([L2], [L3], [P1], `progression/StatProgressionService.java`, `progression/TierInvestmentPolicy.java`)

`RuntimeBalanceSyncService` sends resolved runtime on join/replacement, caches encoded payload per runtime identity, and retries pending eligible sends every five player ticks. Payload channel `runtime_balance_v2` bounds compressed bytes at **524,288** and inflated JSON at **16 MiB**; it does not negotiate the old uncompressed protocol. Player state and item-yield tooltip tables synchronize separately, as does selected ore-host catalog. The complete server acquisition/evidence graph is not sent as runtime gameplay configuration. Disconnect/resource/language/profile/player revisions invalidate relevant client presentation caches. ([N1], [N2], `network/ItemEssenceTooltipSyncService.java`, `network/PlayerEssenceSyncService.java`, [P1])

## 6. Integrations: what can and cannot be claimed

Native generic evidence is the broad foundation. A registered item with ordinary native components and a supported acquisition recipe can participate without a named adapter. Conversely, installing a named mod does not prove its hidden spell/module/affix/production configurations are understood. `GenerationProviders` probes dependencies/readiness once, records versions/status/confidence/timings, stages hook outputs and publishes only completed reads. Failed/unsupported/unready hooks are excluded and warned rather than treating partial output as facts. Core profile validators remain mandatory. ([E4], [E3])

| Integration / evidence provider | Audited scope and limits at this source |
| --- | --- |
| JEI | Optional browsing/display integration for recipes, equipment and ore catalog. Neither balance-generation provider nor required dependency; no claim of REI/EMI integration. |
| FTB Quests | Exact Quests/Library/Teams tuples **2101.1.36 / 2101.1.36 / 2101.1.11** or **2101.1.30 / 2101.1.35 / 2101.1.10**, Architectury **13.0.11**. Reads effective format-13 definition source/native SNBT, normalized supported tasks/rewards/prerequisites/scopes. No player/team completion read or reward execution. Opaque/scripted types remain unknown; uninitialized/loading/foreign-server definitions are not empty evidence. |
| Lootr | NeoForge **1.21.1-1.11.38.125** and **1.21.1-1.11.38.126** public settings/source-scope APIs. Per-player/team/refresh/blacklist distinctions are evidence, not a loot simulator; .125 lacks later team setting/callback shape. Container conversion, positional conditions, custom processors and private loot remain conditional/unknown. |
| Skyblock Builder | NeoForge **21.1.36** starting-source provider; inspects copied declared starter items/template sources. Starting stock/access remains configuration-specific, not blanket proof all skyblock resources renew. |
| Ars Unification | **1.2.21** audited late recipe-publication readiness handling; unsupported/unready families excluded. Not general Ars spell balancing. |
| Iron Jetpacks | NeoForge **8.0.11** definitions/configured jetpack identity, operating power/flight facts with constructive setup/upgrade/input evidence; Charging Gadgets **1.14.1** path audited for charging setup/fuel. Unknown charging/access cannot become attainable flight. |
| Time in a Bottle | NeoForge TIAB **6.5.4** public native definition/config capability scope; saved/time budget and operating scope retained. Does not operate a machine or certify arbitrary accelerated callback behavior. |
| FTB Ultimine | NeoForge **2101.1.15**, with checked Library **2101.1.35/36**, Ranks **2101.1.4/5** and audited optional selection contracts. Player-action limits/exhaustion/selection/rank restrictions are retained; arbitrary callbacks or modified commands require evidence. |
| Squat Grow | NeoForge **21.1.4+mc1.21.1** supported growth contracts and operating conditions. Other registered behavior remains candidates; no universal growth-throughput inference. |
| Botany Pots | NeoForge **21.1.44** supported basic crop/soil definitions and configured matching. Custom crop functions, predicates, ambiguous/shadowing matching or opaque default harvest tool withhold witnesses; installed hopper/pot alone is not a measured automatic farm. |
| Torchmaster | NeoForge **21.1.12** audited detached early filter/config proof and later actual loaded filters; natural-only/entity-filter/geometry distinctions retained. No executing live spawn service to infer exact rates. |
| Forbidden Arcanus | NeoForge **2.6.1** supported modifier-component-removal recipe evidence (e.g. indestructibility), requiring configuration/acquisition proof. Not every ritual/item effect. |
| Silent Gear | NeoForge **4.2.1.1** supported material/native axis evidence; unresolved compositions/part/configuration access remain candidates rather than strongest achievable gear. |
| Ex Deorum | **3.12** manual access enrichment for loaded compost/sieve/hammer/crook/infested-leaf contracts with exact inputs/tools/meshes/stations and yield probability. CompoundIngredient handling checks NeoForge **21.1.248/250/251**. Does not authorize all powered machines or extend every production/conservation path. |
| Apotheosis / Draconic Evolution / Ars Nouveau | Explicit unresolved-system capability candidates for audited versions **8.8.0 / 3.1.4.633 / 5.13.1**. Whole affix/gem/socket, module/shield/energy/grid, or spell/glyph/source/automation composition is **not normalized**. Separately exposed native components or narrowly audited loot behavior can still contribute; naming these in the provider list is not comprehensive support. |
| KubeJS / datapacks / custom recipe families | Loaded effective data and audited Kube crafting semantics/stable recipe IDs can be modeled; unsupported dynamic callbacks, components, hidden operating costs and machine families require factual/typed providers. This is not generic execution/validation of all scripts. |

Evidence: [I1]–[I8], [E4]; provider version checks in `balance/capability/`, `valuation/ExDeorumManualAccess.java`, `valuation/KubeCraftingSemantics.java`, `balance/generated/GenerationRecipeReadiness.java`. Most named capability adapters explicitly gate **NeoForge audited versions**. Shipping on Fabric does not imply those NeoForge-specific adapters work on Fabric.

Additional narrow loot-condition contracts exist in `valuation/RuntimeLootAudit.java`, `BlockLootModifierAudit.java`, `LootSemanticsAudit.java` and native source helpers. They inspect **specific** loaded API/function/condition behavior, not an entire mod's loot economy. Unsupported replacement/drop predicates, custom callback semantics, unknown versions and possible-offer sampling stay visible. Evidence units and confidence must accompany screenshots/exports. A low confidence warning is not a gameplay multiplier by itself or proof an adapter is broken.

Fallbacks are conservative and scoped: exclude unsupported reference axes/configurations, retain explicit unknown candidates, use supported native reference envelopes, and fail required core invariants rather than inventing authority. Pack authors can supply facts for hidden access/power, disable a provider with its actual provider ID, register typed evidence/production providers, or use validated exact overrides. None proves universal mod compatibility or ideal balance.

## 7. Maintenance lifecycle and performance

| Trigger | What actually happens |
| --- | --- |
| First server load without saved profile | Scaffold missing inputs; capture current ready environment; generate/validate/persist/install; routine decision reports; client/runtime refresh |
| Restart with existing profile | Read/verify/decode saved authority; no environment fingerprint scan, graph solve, competitive census, adaptation, or routine report regeneration |
| Existing but invalid/incompatible profile | Rejected; **no automatic regeneration/migration**. Previous in-memory valid authority retained when available; on fresh startup no authority, with guarded systems unavailable. Fix evidence/input issue and explicitly rebuild. |
| TOML edits | No live effect on saved values until explicit rebuild; normal restart/reload preserves saved authority |
| Datapack/recipe/resource manager reload | Detect changed manager identity and clear valuation/substrate caches; does **not** automatically replace generated authority or rerun balance |
| `admin mappings reload` | Validate/reinstall saved authority; missing-file case can generate; sync and refresh. Does not apply changed generator intent to an existing profile. |
| `debug balance validate` | Compare fully validated saved disk document to active integrity; no install |
| `admin balance export` | Exhaustive saved-snapshot derived export; no discovery or recalibration |
| `admin balance rebuild` | Explicit new generation from current inputs/environment; validate/preflight/commit/install/sync; ordinary decision export |
| Server stop | Clears active server mappings/runtime and generation/presentation lifecycle caches |

Evidence: [L2], [B1], [B2], [B4].

Suggested **future** maintenance procedure (not executed in this audit): record installed mod/loader/MC versions and profile integrity; back up both human inputs and generated authority alongside normal world backups; review affected facts/recipes/configuration; schedule explicit rebuild during a maintenance window; check successful installation, saved-file validation, report manifest/currentness, client Archive values and selected survival routes; then verify restart reuses authority. Do not delete worlds/profile files or copy an unrelated pack's profile as a troubleshooting shortcut. Backups of player/world files require privacy controls. Saved integrity validates bytes/structure and internal invariants; it does not prove the current pack still matches the environment used to generate it.

Performance implications:

- Rebuild is synchronous on the server thread. Acquisition, loot, structures, trades, optional configs, competition, solver, curve generation, serialization, full decode and report writes can take substantial time/memory in a large pack. Avoid promising a fixed duration or cost based only on registry count.
- Saved reuse skips discovery/solving but still reads compressed authority, checks integrity, decodes/validates typed data, prepares mappings/payloads and refreshes players. It is not zero-cost startup. Validation repeats meaningful read/decode work; export can produce large CSVs.
- Generation-scoped caches/indexes/snapshots are shared and released before later calibration/serialization; persisted/active sections are compacted to avoid retaining duplicate graphs. Loot/tool-context caches have bounded scopes. Telemetry distinguishes full generation, saved load, export and sync with timing/count/memory evidence.
- Transport is preflighted before replacing authority. Unknown adapter evidence need not stop all generation, but a required runtime/economy/codec/headroom failure does. Reports or client sync can fail after an otherwise committed install.
- Summarized Markdown has bounded samples; exhaustive source rows require explicit export. File-size safety bounds and synthetic low-memory tests are not recommended heap settings for every native pack.

Public diagnostic requests should ask for relevant sanitized error text, versions, integrity, manifest state and bounded provider/warning rows. Avoid raw personal logs, complete player SavedData, owner/target identifiers, coordinates, private filesystem paths or credentials. Generated provenance may include config/source locations; review before attaching publicly.

## 8. Ascendance Archive: verified player route

### Obtain and replace

1. Normal player onboarding from Latent completes enough seals to auto-reach Dormant. Authoritative progression/lifecycle reconciliation delivers one **Ascendance Archive** on first powered tier, including direct admin tier changes. The receipt is marked only on successful delivery; failed delivery can retry. ([G1], [A2])
2. Delivery uses a receiving inventory slot first; if full, drops the book with no pickup delay and target ownership. Creative inventory handling explicitly avoids silent loss. Later loss/destruction does **not** automatically rearm the one-time receipt. ([G1])
3. Craft a replacement through the shipped **shapeless** recipe: **one Raw Latent Ore + two sticks → one Archive**, any arrangement. A separate recipe discovery advancement triggers on Raw Latent Ore, including players whose older recipe advancement already completed. No Dormant gate is enforced by the Archive item or crafting recipe. Datapack changes can of course alter effective recipes. ([G2], `data/essence_ascendance/advancement/recipes/ascendance_archive.json`)
4. Operator recovery: `/essence admin player guide give [player]`, permission 2, no cost, regardless of past receipt. It records successful delivery. Full progression reset rearms the normal reward but is far broader than book replacement. ([C2], [G1])

### Open and navigate

Use the Archive item in either hand to open the full-screen client screen. No nearby Nexus/machine, server menu transaction, operator permission or extra command is required. The common item calls `ArchiveClientBridge`; client initialization installs the screen opener, keeping dedicated-server item loading safe. ([G3], [G4])

Choose **Guide**, **Reference**, or **Search**. Guide supplies six linked lessons/sections; Reference supplies sections and entry lists, with related/previous/next links and a scrollable article. Skill and Bonus lists enumerate registries directly and sort localized names; all entries remain visible regardless of owned Skills, current tier or Nexus visibility. Back/Forward retains bounded history (32 visits). Mouse/wheel scrolling and shared keyboard focus controls work through the screen widgets; Tab changes focus and Enter/Space activates focused controls where supported. Close through normal screen/Escape behavior. Guide does not need the Reference entry-list pane. ([G4], [G5], [G6])

Navigation/page/scroll/search and Item Yields filter state are held in **session-only bounded UI memory**, not saved into a book or player world data. Reopening in the same valid presentation session restores it; stale connection/context is reconciled. Do not promise persistence across app/game restarts or offline portability of a server's resolved profile.

### Search and Item Yields

Global Search indexes the same semantic article blocks rendered by the Archive plus synchronized Item Yield metadata. Matching is case-insensitive normalized Unicode/whitespace with **all query terms** present; title/phrase matches rank higher, explanatory pages precede the item group, and there is no arbitrary result cap hiding later matches. This is not fuzzy web search. Selecting a result opens its canonical entry or yields row. Search availability distinguishes incomplete yield synchronization from an empty result. ([G7])

Reference → Essences → **Item Yields** supplies localized names/IDs, Essence filter selection (Any/All), sortable columns, row selection and scrolling. The table is built from synchronized active item-yield snapshot rather than a private independently regenerated client value table. Unsupported/zero ordinary output items are not a universal promise of dissolvability. Article-wide stat tables and bounded yield results have different scroll ownership. ([G8], `client/ItemEssenceTooltipClientState.java`)

### Which values are server-dependent?

The Archive's `PresentationContext` explicitly reads installed synchronized client runtime or active integrated-server runtime plus synchronized player state. It **does not call the bootstrap fallback**. Loading/unavailable/context-required/true-zero values remain distinct. Runtime/profile/player/connection/language changes invalidate cached semantic content/search. ([P1])

Active environment values include item dissolution yields; equipment tier baselines and native characteristics; infusion/Focus/material/repair/conversion costs and efficiencies; Crucible/Pylon capacity, rates, ranges and processing; Bonus maximum effects, costs, generated checkpoints and applicability; Skill availability, meaningful maximum ranks, per-rank native effects/costs, prerequisites/gates; and generated Attunement targets/rates/breadth/policy where the page presents them. Player ownership, current/next eligibility, balances, effective investments and progress require the separate ready player snapshot. General concepts, names and registered relationships remain catalog content. ([G4], [G8], [P1], `archive/ArchiveDocuments.java`, `client/presentation/SkillPresentationData.java`, `BonusPresentationData.java`)

The wiki should tell players: **“Open your Ascendance Archive for the values used by this server.”** A screenshot from one pack/profile is an example, never the universal default table.

## 9. Documentation discrepancies and publication priorities

| Finding | Required wiki treatment |
| --- | --- |
| Older investment-threshold Ascension descriptions | Superseded by current `AscendanceEngine`: seals only, no currency payment. The opening comments in bundled `balance_overrides.toml` still refer to total-investment multipliers/positive increasing thresholds; do not copy that historical claim into player eligibility documentation. |
| Old standalone mapping/config narratives | Current manager installs mappings solely from generated economy and reused profile. Debug output still has generic item/tag/config counts; that UI shape is not evidence of an active JSON override directory. |
| Historical engineering acceptance chronology | `docs/pack-compatibility.md` and `docs/procedural-skill-progression.md` record evolving decisions and prior native runs. Reconcile each section with current code; do not publish private auxiliary paths or raw receipts embedded in those notes. |
| Optional providers formerly described as fatal | Current provider transactions exclude unsupported/unready/failed hooks with diagnostics; core candidate validators can still reject. Earlier “required provider stops everything” language needs current-scope correction. |
| Adaptive/rank/tier evolution | Describe catalog/candidate/generated tier, retained prerequisites and actual native meaningful ranks. Earlier fixed tiers, filler ranks, cost shares or identity compensation examples are not universal current values. |
| Attunement history help | Targeted history requires a page number. Show the two actual executable forms rather than independent `[page] [player]`. |
| Item tier versus player tier | Explicitly different operations; only held Ascendance equipment/Foci supported by admin item tier mutation |
| Diagnostics labeled “read-only” | Explain ordinary attribute reconciliation/link refresh and chapter alignment; neither spending nor perfect absence of incidental cache/state effects |
| Blanket “all progression disabled without authority” claims | Ascension/Attunement, dissolution and ore generation have explicit guards; the Bonus investment command instead resolves the general config fallback. Record this source limitation without silently assuming a readiness check or changing gameplay in this documentation stage. |
| “Validate passed” or “All tests passed” | Qualify as codec/model/fixture checks, not survival pacing, every loader/mod version, or multiplayer acceptance |
| Broad NeoForge metadata range | NeoForge descriptor permits Minecraft `[1.21.1,)`, but supported/built target is **1.21.1**; do not advertise every later Minecraft release from permissive metadata alone |
| Loader metadata promotional description | “Consume vast quantities of items to ascend” persists in loader descriptors; current player Ascension is activity/seal-based. README at HEAD provides updated player description. |
| Missing public wiki corpus | Confirm target visibility/destination before later publication; this stage only saves audit and plan |

Highest publication priorities: (1) scope/version and player Archive routing; (2) exhaustive command reference with effects; (3) saved authority/rebuild distinction and inputs; (4) integration boundaries and report troubleshooting; (5) explanations of generated power/economy/rank decisions with scoped validation evidence.

## 10. Test evidence and coverage limits

**No new tests were run during this audit.** The following source suites were inspected as implementation evidence; their existence is not a fresh PASS result. Existing engineering acceptance claims are historical reports, not reproduced here. Most suites are Java main-based invariant programs wired into `common/build.gradle`, rather than ordinary JUnit discovery alone. ([T1])

| Relevant suite(s) | What its source actually exercises / does not establish |
| --- | --- |
| `command/EssenceCommandTest` | Real Brigadier parsing, permissions, short/quoted IDs, optional targeting, help suggestions and standalone operator data/NBT changes. Does not construct/modify a world or execute every player/server handler against live entities. |
| `balance/config/BalanceConfigTest` | Supported TOML/parser errors, policies/overrides and boundaries; not a live configuration reload |
| `balance/generated/GeneratedBalanceIntegrationTest`, `GenerationSelectionChecks` | Real Minecraft bootstrap plus synthetic evidence/runtime/profile codec/storage/lifecycle selection checks. Saved fixture reuse avoids generation; explicit fixture selection observes changed data. Not a live optional-mod pack launch/client session. |
| `BalanceDocumentTest`, `BalanceDocumentStreamingTest`, `GeneratedBalanceSectionsTest`, `FailureEvidenceTest` | Integrity, schema/streaming/retention/failure evidence and bounded storage contracts |
| `BalanceReportsTest`, `BalanceReportLayoutTest` | CSV/report sidecars/layout/archive/manifest behavior, not quality of every factual source |
| `balance/economy/EconomyInvariantTest`, `FractionalLedgerTest` | Synthetic compression, alternatives, containers/catalysts/probability, bounded cycles, source pressure, whole yield/conversion safety and fractional transaction contracts; not discovery of unknown third-party loops |
| `valuation/*` (e.g. `TradeGraphTest`, `NaturalBlockEvidenceTest`, `LootAcquisitionTest`, `EffectiveProductionTest`, `ExDeorumManualAccessTest`) | Targeted acquisition/loot/source/recipe contracts and explicit unsupported cases, not complete survival acquisition in all worlds |
| `ValuationPerformanceTest`, `BlockSourceMemoryTest`, `LootAuditMemoryTest`, `BlockLootToolInputsTest` | Differential fixture behavior/cache scope/bounded memory/tool-context equivalence. Their synthetic datasets and heap limits do not predict all native pack timings. |
| `balance/engine/GenerationProviderContractTest`, `ProviderReferenceTest`, `CompetitiveCapabilitiesTest`, native enchanting/trade/anvil/consumable/villager tests | Provider failure exclusion, access/config/measurement and frontier contracts under constructed evidence |
| `balance/runtime/*`, `GeneratedSkillAvailabilityTest`, `skill/balance/*` | Generated budgets, exact fields, power envelopes, meaningful rank policies, prerequisite/loadout/refund/NBT and resolved availability contracts; not balanced real fights or exhaustive adapter gameplay |
| `attunement/AttunementAccountingTest`, `AttunementAdapterTest`, `RuntimeAscensionPolicyTest` | Normalized seals, repetition/variety/root attribution, generated chapter policy and currency-free Ascension logic; not every live event hook in every mod |
| `archive/ArchiveAuditTest`, presentation/search/foundation suites | Catalog/semantic content, actual localization/native wrapping with conservative glyph metrics, navigation/search/presentation readiness and source contracts. No OpenGL or live multiplayer-render session. |

Native profile/quest/export/replay development tools can read saved evidence, write offline files, or run invariant assertions when explicitly invoked. They are **not additional chat commands** and should not be mixed into a player command list. This audit did not invoke `savedEvidenceRegenerate`, saved export, generation replay, quest replay, pack tester/deploy, or live validation tools. Their input paths and write destinations require separate review for any future maintenance task.

## 11. Coverage checklist and genuine uncertainties

- [x] Repository/ancestor instructions checked; initial Git status clean; unrelated work preserved.
- [x] Source commit, declared version, MC/Java/loaders/dependencies identified; latest published beta and tag/HEAD diff verified.
- [x] Actual registrations, argument types/ranges, permission checks, target wrapper, handlers and major downstream services inspected for all six requested command classes plus utilities.
- [x] Production command registration searched outside command package and in both loader projects; no extra roots/aliases found.
- [x] Every executable action/optional argument form and help endpoint inventoried, including Attunement history ordering and string-ID quoting.
- [x] Wallet balance separated from generated balance diagnostics/rebuild/export.
- [x] Diagnostic intent separated from spending, grants/clears/resets, item mutation, saved install, rebuild and derived-file export.
- [x] Input TOML keys/ranges, fact schema, exact pointer restrictions, Java runtime config roles and output/report inventories traced.
- [x] Acquisition, economic value/yield distinction, supply/automation/renewability, whole-unit accounting and modeled conversion invariants traced.
- [x] Equipment/competition confidence/scope/outliers, Bonus tracks, meaningful Skills/ranks/prerequisites, Attunement/Ascension and infusion distinctions traced.
- [x] Startup authority timing, saved reuse, explicit reload/rebuild, recipe cache invalidation, validation/atomic commit and failure modes traced.
- [x] Runtime/player/item-yield/ore-catalog synchronization and provisional-versus-authoritative presentation checked.
- [x] Archive delivery, drop/full-inventory behavior, replacement recipe, item opening, navigation/search and active-profile values verified against source.
- [x] Existing notes reconciled where material claims conflict; test scope kept separate from new test results and live acceptance.
- [x] Audit contains no private absolute installation paths, credentials, real player identifiers, raw personal logs or world captures.
- [ ] Existing wiki page-by-page comparison: public URL yielded a repository redirect; accessible page content is needed if there is an existing unpublished/private corpus to retain.
- [ ] Live player/server acceptance in intended pack: deliberately outside this stage. Needed later for survival acquisition, pacing, machine/loot/callback compatibility and actual UI/client behavior.
- [ ] Release jar/source reproducibility and distribution-platform approval state: outside this source audit; GitHub release/tag verified, not every external platform binary/listing.

No user answer is needed to complete this local audit. For a later publication stage, the only immediate external uncertainty is whether an existing wiki corpus/destination is available and should be preserved. Selecting a concrete supported pack/environment for future live acceptance is separate work; universal compatibility or perfect automatic balance cannot be inferred from this audit.

## 12. Evidence index

Links below are pinned to the audited source commit. Java paths without a full prefix elsewhere in this audit are relative to `common/src/main/java/com/mistaboom/essence_ascendance/`; test paths are relative to the matching `common/src/test/java/...` package. These are public repository paths, not personal filesystem paths. For released behavior, the tag has identical implementation files as established in section 1.

[V1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/gradle.properties
[V2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/fabric/src/main/resources/fabric.mod.json
[V3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/neoforge/src/main/resources/META-INF/neoforge.mods.toml
[C1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceCommands.java
[C2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceAdminCommands.java
[C3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceDebugCommands.java
[C4]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceTestCommands.java
[C5]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceBalanceCommands.java
[C6]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/command/AttunementCommands.java
[C7]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceCommandUtil.java
[L1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/EssenceAscendance.java
[L2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/mapping/ItemEssenceMappingManager.java
[L3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/config/EssenceConfigManager.java
[D1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/data/PlayerEssenceData.java
[D2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/progression/PermanentMilestoneService.java
[A1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/progression/AscendanceEngine.java
[A2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/attunement/AttunementService.java
[A3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/attunement/AttunementActivityRegistry.java
[F1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/config/BalanceInputs.java
[F2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/resources/balance/essence_ascendance.toml
[F3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/config/BalanceSettings.java
[F4]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/config/BalanceOverrides.java
[F5]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/config/LatentOreSettingsParser.java
[F6]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/BalanceReports.java
[F7]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/BalanceReportLayout.java
[F8]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/SpreadsheetReports.java
[B1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/GeneratedBalanceService.java
[B2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/BalanceProfileStore.java
[B3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/BalanceDocument.java
[B4]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/ProfileGenerationSelection.java
[E1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/engine/PackEvidenceCollector.java
[E2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/valuation/GenerationDataSnapshot.java
[E3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/engine/CompetitiveCapabilities.java
[E4]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/engine/GenerationProviders.java
[Q1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/economy/EconomyGenerator.java
[Q2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/valuation/ProceduralValuationEngine.java
[Q3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/valuation/ProductionGraphAdapter.java
[Q4]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/economy/SourcePressurePolicy.java
[Q5]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/economy/EconomyConservationSolver.java
[R1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/runtime/RuntimeBalanceGenerator.java
[R2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/runtime/AdaptiveCompetitionCalibration.java
[R3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/runtime/BonusTrackGenerator.java
[R4]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/skill/balance/SkillBalanceGenerator.java
[W1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/runtime/LatentOreBalanceGenerator.java
[W2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/worldgen/PrimarySubstrateDiscovery.java
[N1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/network/RuntimeBalanceSyncService.java
[N2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/network/RuntimeBalancePayload.java
[P1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/client/presentation/PresentationContext.java
[G1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/progression/DormantGuidebookService.java
[G2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/resources/data/essence_ascendance/recipe/ascendance_archive.json
[G3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/item/AscendanceArchiveItem.java
[G4]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/archive/ArchiveCatalog.java
[G5]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/client/AscendanceArchiveScreen.java
[G6]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/client/archive/ArchiveNavigator.java
[G7]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/client/archive/ArchiveSearch.java
[G8]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/client/archive/ItemYieldBrowser.java
[I1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/capability/InstalledCapabilityProviders.java
[I2]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/quest/FtbQuestProvider.java
[I3]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/valuation/LootrProvider.java
[I4]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/capability/SkyblockBuilderStartingSourcesProvider.java
[I5]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/capability/IronJetpackCapabilityProvider.java
[I6]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/capability/TimeBottleCapabilityProvider.java
[I7]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/capability/UltimineCapabilityProvider.java
[I8]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/src/main/java/com/mistaboom/essence_ascendance/balance/capability/SquatGrowCapabilityProvider.java
[T1]: https://github.com/mistaboom/essence_ascendance/blob/5ea335be01ba9fd6fc80fec950029400cac3ecd0/common/build.gradle
