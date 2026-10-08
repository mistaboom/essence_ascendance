# Integrations and compatibility

[Procedural balance](https://github.com/mistaboom/essence_ascendance/wiki/Procedural-Balance) · [Incomplete-evidence walkthrough](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios#handling-incomplete-evidence-for-another-mod)

## Five kinds of support

| Kind | What it means |
| --- | --- |
| Generic loaded evidence | Ordinary supported recipes, native components/attributes, effective data and known source contracts can participate without a named mod adapter |
| Explicit provider | A version/readiness-checked reader supplies a narrowly audited contract |
| Optional display integration | JEI helps browsing; it is not a balance-evidence provider |
| Manual override | Pack author declares a supported fact or pins a validated field |
| Unsupported behavior | Unknown units, callbacks, compositions or access remain candidates/warnings or are excluded |

Installing a mod is not proof that every mechanic is understood. Recipe discovery may reveal item inputs/results while missing energy, components, conditional returns, automation behavior or operating setup.

Providers stage their output. Failed, unsupported or unready optional hooks are excluded with warnings rather than publishing half-read facts. Core profile validators can still reject generation.

## Loaded data and browsing

JEI is optional for both shipping loader builds; the compile API reference is **19.43.0.395**. It supplies recipe/equipment/ore display integration. This version documents no REI/EMI integration.

Datapacks and supported effective KubeJS crafting semantics can contribute loaded recipes. Script callbacks, dynamic components and unknown recipe families remain outside generic guarantees. Reloading recipes does not itself rebuild saved balance.

Typed production/evidence extension points exist for additional implementations. Their existence is not a built-in promise to model every machine from every mod.

## Starting sources, quests and loot

| Provider | Audited versions/context | Supported scope and limits |
| --- | --- | --- |
| Skyblock Builder | **NeoForge 21.1.36** | Copied declared starter items/template sources; starting stock does not prove renewability or all skyblock access |
| FTB Quests | Quests/Library/Teams **2101.1.36 / 2101.1.36 / 2101.1.11** or **2101.1.30 / 2101.1.35 / 2101.1.10**; Architectury **13.0.11** | Effective format-13 native definition source, supported task/reward/prerequisite/scope normalization. No team/player completion reads or reward execution. Opaque/scripted definitions stay unknown. This is a checked definition contract, not unrestricted cross-loader/version compatibility. |
| Lootr | **NeoForge 1.21.1-1.11.38.125 / .126** | Public settings/source scopes, player/team/refresh/blacklist distinctions. .125 lacks later team contract shapes. Private loot, conversion/positional/custom processors remain conditional or unknown. |
| Ars Unification | **1.2.21** | Audited late recipe-publication readiness; unready/unsupported families excluded. Does not provide general Ars spell balancing. |

Loading/uninitialized/foreign-server quest definitions are not authoritative empty evidence. Loot/source scopes do not simulate actual players opening containers.

## Capability providers

The following named capability adapters gate **specific audited NeoForge versions**. Fabric support for Essence Ascendance does not imply these NeoForge adapters activate there.

| Integration | Audited version(s) | Admitted scope / withheld knowledge |
| --- | --- | --- |
| Iron Jetpacks | **8.0.11**; Charging Gadgets **1.14.1** charging path | Configured identity, power/flight and constructive setup/upgrade/fuel evidence. Unknown charging access cannot become attainable flight. |
| Time in a Bottle | **6.5.4** | Public definitions, time budget and operating scope; not arbitrary accelerated callback behavior |
| FTB Ultimine | **2101.1.15**; Library **2101.1.35/36**, Ranks **2101.1.4/5** | Audited selection/rank/action/exhaustion restrictions; arbitrary callbacks/modified commands require evidence |
| Squat Grow | **21.1.4+mc1.21.1** | Supported growth contracts and conditions; not a universal throughput inference |
| Botany Pots | **21.1.44** | Basic crop/soil definitions and configured matching. Custom functions/predicates, ambiguous matching or opaque harvest tools withhold witnesses. |
| Torchmaster | **21.1.12** | Early detached filter/config proof and later loaded filters; natural/entity/geometry scope retained. No live spawn-rate experiment. |
| Forbidden Arcanus | **2.6.1** | Supported modifier-component-removal acquisition evidence; not all rituals/effects |
| Silent Gear | **4.2.1.1** | Supported material/native axes; unresolved composition/parts/configuration access remains candidates |

Operating conditions, units and access matter as much as a high headline number. A flight capability does not automatically support native flying-speed scaling; a durability-removal mechanic is not infinite effective health.

## Manual access and unresolved systems

| Integration | Version/context | Actual boundary |
| --- | --- | --- |
| Ex Deorum | **3.12**; CompoundIngredient checks NeoForge **21.1.248/250/251** | Loaded manual compost/sieve/hammer/crook/infested-leaf access with exact inputs/tools/meshes/stations and probabilities. Does not certify powered machines or every conservation chain. |
| Apotheosis | Candidate contract **8.8.0** | Full affix/gem/socket composition is unresolved |
| Draconic Evolution | Candidate contract **3.1.4.633** | Full module/shield/energy/grid composition is unresolved |
| Ars Nouveau | Candidate contract **5.13.1** | Full spell/glyph/source/automation composition is unresolved |

The last three are **unresolved-system candidates**, not comprehensive supported balancing. Separately exposed native components or narrowly audited loot behavior can still contribute.

Additional loot-condition/function contracts inspect specific loaded API behavior. They are not whole-mod loot certification. Unknown replacement predicates, custom callbacks, versions and sampled offers remain limitations.

## Conservative fallbacks

Unsupported configurations do not become measured references merely through names. Depending on the system, the generator excludes an unsupported axis, retains an unknown candidate, uses supported native reference envelopes, or rejects a required invariant.

PARTIAL/UNKNOWN coverage means the comparison is incomplete. It does not mean every earlier alternative was examined, nor that a low-confidence warning directly reduces gameplay power.

To improve evidence:

1. Find the provider/source ID and reason in saved diagnostics.
2. Verify loader, version, readiness, access and operating conditions.
3. Add truthful supported [facts](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization), disable the actual provider if appropriate, or seek a typed adapter for missing semantics.
4. Rebuild in staging and inspect matched provenance and survival routes.

An exact override can express pack intent; it cannot prove hidden mechanics are modeled. Report compatibility problems with a small sanitized reproduction and [version information](https://github.com/mistaboom/essence_ascendance/wiki/Version-and-Maintenance).

Source: [installed capability registry](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/capability/InstalledCapabilityProviders.java), [provider transactions](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/engine/GenerationProviders.java), [quests](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/quest/FtbQuestProvider.java), [Lootr](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/valuation/LootrProvider.java).
