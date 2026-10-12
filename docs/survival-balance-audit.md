# Durability and hunger balance audit — October 11, 2026

Reference: the saved vanilla headless profile from `validation/performance/20261008-engineering-completion/vanilla/final-1/profile-1.json.gz` in the auxiliary workspace. The first table is the **before** state. Regenerated results and native test evidence appear below; arithmetic gameplay comparisons are projections from native rules, not measured player sessions.

| Feature | Vanilla published ranks before this change | Practical effect and finding |
| --- | --- | --- |
| Restful Mending | 4.1469%, 4.7945%, 5.3896%, 5.9224% maximum durability/second; 4.5-second idle delay | Rank one repaired 100 damage on a 250-durability iron pickaxe in about 9.65 seconds, or on a 1,561-durability diamond pickaxe in about 1.54 seconds, after the delay. Capacity multiplied free throughput. Approved replacement: at least 1 native durability point/second. |
| Metabolic Mending | 1.1008%, 1.8329% maximum durability per intrinsic food point | Steak has 8 nutrition plus 12.8 intrinsic saturation, so one steak repairs 22.90% / 38.12% of an item's maximum. That is about 357 / 595 points on a diamond pickaxe. Approved replacement: 1 point per intrinsic food point, +0.5 point for another meaningful rank. At the floor a steak repairs 20.8 durability, including fractional carry. |
| Masterwork Tempering | Buffer 20.7095%, 30.1166%, 39.5026%; full-buffer performance 11.6992%, 17.9395%, 24.8816%; material reinforcement 11.1111%, 17.0984%, 23.8095% | Paid finite capacity, full-native-durability prerequisite, performance decreases as the buffer wears. Keep its 10% buffer / 5% performance first minimum and +5 percentage-point later alternatives. Free repair does not refill Overdurability; inactive paid buffer persists without granting protection. |
| Feast Reflex | Food use multipliers 0.238096, 0.116145: ordinary 32-tick food takes 8 / 4 ticks | Automatic meals ignored this time entirely and could complete one meal every tick, i.e. 20 meals/second. Correct successive automatic meals to use a persistent player cooldown equal to the consumed food's generated use duration; the first useful meal remains immediate. The generator also used food's exhaustion-drop constant as a fourfold eating-speed budget; replace that with shared skill headroom while preserving the 25% first reduction / +10 percentage-point later minimum. |
| Inner Sustenance | 1 food and 0.5 saturation every 178, 137, 108, 87 ticks; 200-tick hostile-combat timeout | At full food, food recovery is discarded: only 0.0562–0.1149 saturation/second remains. The floor is 0.05 saturation/second, below ordinary level-ground sprint drain of about 0.140. The separate full-reserve capability cancels ordinary exertion indefinitely once both bars are full; changing refill minimum alone cannot address that capability. Approved change: keep the useful refill minimum and remove the full-reserve exhaustion immunity. Sleep/phantom protection remains. |
| Hunger Ward | 10% of post-absorption damage; 1.3 HP redirected per food/saturation point | A 10-HP hit redirects 1 HP for about 0.769 resource point and leaves 9 HP damage. The share remains binding regardless reservoir size; a full 40-point reservoir is not 52 freely granted HP. Explicit debit bypasses Hunger Efficiency. Keep minimum. |
| Metabolic Conversion | 0.25 HP per nutrition at full hunger; 1 food point per eligible independent overflow HP | Steak gives 2 HP. Without Feast cadence, repeated automatic steak could theoretically supply 40 HP/second while food lasts. Resource provenance prevents food-origin healing from returning food, including native food regeneration and food-applied effects. The conversion pair is strictly lossy. Keep minimum. |
| Hunger Efficiency | 52% published endpoint; meaningful floor 10%, later +10 percentage points | Multiplies exhaustion by 0.48 at endpoint. It does not multiply meal nutrition/saturation or discount Hunger Ward debit. Keep minimum. |
| Durability Efficiency | 55% published endpoint; meaningful floor 10%, later +10 percentage points | Leaves 45% native wear before native enchantment and active buffer handling. Per-item fractional carry realizes one-point wear without silently dropping it. Keep minimum. |
| Anvil Efficiency | 52% published endpoint; meaningful floor 10%, later +10 percentage points | Persistent independent material/XP cost carries conserve small savings. Does not make repair or tempering free in expectation. Keep minimum. |
| Salvager's Craft | Material recovery 21.5686%, 36.0511%; XP bonus 13.0435%, 23.5182% | Destructive equipment salvage returns only partial construction materials. Paid tempering and salvage use the shared factual material family; no free repair-to-material creation identified. Keep minimum. |

Vanilla sprint comparison uses the native exhaustion constant `EXHAUSTION_SPRINT = 0.1` per block and `EXHAUSTION_DROP = 4` per resource point, with approximately 5.61 blocks/second on ordinary ground. Jumping, combat, healing, terrain, and equipment change actual expenditure. When both food and saturation are deficient, Inner can restore both; adding those values is not a valid estimate of steady-state saturation recovery at full food.

## Legal combinations and accounting

- Feast Reflex and Inner Sustenance are mutually exclusive. Restful Mending, Metabolic Mending, and Masterwork Tempering are mutually exclusive. Hunger Ward, Staggered Pain, and Damage Ceiling are mutually exclusive. The audit does not assume impossible simultaneous branches.
- Feast can combine with Metabolic Conversion and Metabolic Mending. Every meal must consume the actual food and complete its native effects/container path once. Faster manual use and automatic cadence use the same generated duration.
- Inner refill is not a completed meal, so it cannot trigger Metabolic Mending or food-origin conversion. Independent healing overflow can refill resources; food-origin healing cannot create its own food.
- Restful idle tracking observes durability-use attempts even when efficiency or native enchantments prevent the actual debit. Repair fractional carry is local to its item and never multiplies by maximum durability.
- Saved purchase receipts remain exact. If regeneration publishes fewer meaningful ranks, effect resolution uses the current final rank while preserving historical payments and refunds. Purchase validation remains strict; extra obsolete ranks are not offered again.

## Publication and approved decisions

Restful's generated native work budget allocates durability points over the existing survival window. It explicitly assumes no XP-collection rate: a competitor's durability-per-XP measurement is not a measured free durability-per-second rate. Competitive pressure can adjust nominal allocation, but the approved minimum publishes last and is never recycled into sibling allocations. A profile can therefore expose one meaningful rank at the approved floor rather than inventing additional purchasable gains.

Approved user decisions: Restful first 1 durability point/second and +1 point/second for a later meaningful purchase; Metabolic first 1 durability point/intrinsic food point and +0.5 for a later meaningful purchase; Inner retains its refill minimum and loses full-reserve exertion immunity. All unrelated meaningful minimums stay in place. Archive delivery is unchanged; shared skill presentation supplies the new units to the Archive and other skill views.

Headless checks cover generated bounds/meaningful ranks, saved-rank compatibility, native item durability on unequal capacity items, exact idle-delay reset, persistent automatic meal cadence, inventory/container conservation, food/healing provenance, and hunger/durability efficiency. Existing installations require explicit profile regeneration to publish new native units; old fractional values must never be interpreted as point rates.

## Regenerated vanilla results

Final readback: `validation/performance/20261011-balance-completion/vanilla/final-6/profile-0.json.gz` in the auxiliary workspace, revision `enchantment-native-maintenance-47`. The accepted NeoForge JAR SHA-256 is `276a07cf92885a00df1660064fff5b5372e2dd07090d2683ffdb896e9cb31522`. The server's `initial-validation.json` reports saved profile integrity, schema, runtime curves, and economy validation passed; `rank-console-summary.json` reports all 236 published skill ranks passed. `accepted.json` confirms fresh generation, one rebuild and a saved restart: the semantic comparison matches across evidence, economy, runtime, skills, validation and capabilities; the restart preserved 61 fixture files and reused the saved profile in 2.356 seconds. The offline saved-profile audit also passed for 90 skills, six tree layouts and 19,135 transaction-state checks. These are measured generated settings. Times and resource rates below are calculated from those settings and the native 20-tick/second clock.

| Feature | Published vanilla result after regeneration | Interpretation |
| --- | --- | --- |
| Restful Mending | One rank: 1 durability point/second, 90-tick idle delay, purchase cost 62 | Repairs 100 damage in 100 seconds after the 4.5-second idle delay on either an iron or diamond pickaxe. One eligible carried item receives work at a time. Nominal vanilla later candidates do not justify another +1 point/second purchase; no extra ranks are manufactured. Stronger generated profiles may exceed the minimum increment. |
| Metabolic Mending | One rank: 1 durability point per intrinsic food point, purchase cost 62 | A steak provides 20.8 points across the shared damaged-equipment budget, with subpoint carry. Maximum durability does not multiply this work. No later vanilla candidate earns another +0.5 point/food purchase. |
| Feast Reflex | Four duration multipliers: 0.714286, 0.549451, 0.432901, 0.285715 | Native 32-tick foods take 23, 18, 14, 10 ticks, rounded up. The first useful automatic meal is immediate; the next waits that same duration. At the endpoint, sustained automatic steak consumption is capped by cadence at 2 meals/second, rather than the former 20. With Metabolic Conversion's base 2 HP/steak, that is at most 4 HP/second before other healing modifiers and meal-eligibility limits, paid for with actual food. |
| Inner Sustenance | Four ranks: 1 food plus 0.5 saturation every 178, 137, 108, 87 ticks; combat timeout 200 ticks | Recovery amount and meaningful minima are unchanged. At full food, only 0.5 saturation can be restored: approximately 0.0562, 0.0730, 0.0926, 0.1149 saturation/second. Full reserves no longer cancel exertion. |
| Masterwork Tempering | Three buffer ranks: 19.0465%, 27.9647%, 37.0354%; performance 9.8472%, 15.2705%, 21.4498%; reinforcement 11.1111%, 17.0984%, 23.8095% | New economy/reference inputs modestly reduce buffer/performance relative to the saved before profile. Paid capacity, diminishing performance as the buffer wears, material accounting, and meaningful minima remain intact. |
| Hunger Ward | One rank: 10% damage share, 1.3 HP/resource point | Same native defensive outcome as before. The generated tier is Ascendant; it does not multiply the player's reservoir or receive Hunger Efficiency's discount. |
| Metabolic Conversion | One rank: 0.25 HP/nutrition, 1 food point/eligible independent overflow HP | Same conversion floors, with Transcendent generated tier. Food-origin healing still cannot loop back into food. |
| Hunger / Durability / Anvil Efficiency | Endpoints 58% / 58% / 52% | Hunger Efficiency now leaves 42% of ordinary exhaustion; Durability Efficiency leaves 42% of native wear before enchantment/buffer handling. Anvil Efficiency matches the before profile. All retain the previously approved first/later meaningful minima. |
| Salvager's Craft | Two material recovery ranks: 21.5686%, 36.0511%; XP bonus 13.0435%, 23.5182% | Unchanged fractional recovery; destructive salvage and paid material repair remain separate transactions. |

The legal Inner Sustenance plus Hunger Efficiency combination deserves an explicit distinction. Ordinary flat sprinting at about 5.61 blocks/second consumes approximately 0.14025 saturation/second without efficiency, or 0.058905 at the new 58% efficiency endpoint. Inner rank one falls slightly short of that reduced rate; ranks two through four can cover it while out of hostile combat. With the generated 95% Movement Speed endpoint, the same simple distance-based calculation rises to about 0.114865 saturation/second, close to Inner's final 0.114943 recovery rate. Terrain, jumps, actual speed, combat, healing, and other costs change the outcome. This is a conditional combination of purchased recovery and efficiency; full reserves no longer grant unconditional activity immunity. No additional minimum change was made.

The accepted profile also confirms `onboardingEffortFraction = 1.0`: each Latent category's target is 32,768 accounting units, compared with 16,384 in the saved October 8 profile at 0.5. The required category count remains one, later chapter effort fractions remain unchanged, and Archive delivery is unchanged. These accounting targets are not a fixed action count or measured completion time.

## Native and invariant evidence

`validation/performance/20261011-balance-completion/full-check-build-1.log` records a successful complete common/generation check and both loader builds: `VitalityBalanceTest` **832 passed**, `SharedPresentationTest` **13,406 passed**, the rank-ledger suite passed, `VitalityBatchContractTest` **162 passed**, and generated-profile integration **3,933,776 passed**. The stale October 9 test fixtures are corrected; their failed invocations are superseded by these successful checks.

The final source corrections were separately rechecked in the successful `native-source-final-build-2.log`: Fabric's transformed native recovery **149 passed**, sustenance **51 passed**, and resource chains **68 passed**, plus natural placement **77 passed**, block-loot tool inputs **3,636 passed**, loot acquisition **150 passed**, material valuation **42 passed**, and enchanted stack yields **317 passed**. Native recovery includes unequal maximum-durability items, single-item Restful throughput, fractional Metabolic carry, idle resets, and discarded unused repair; native sustenance includes exact meal cadence, persisted cooldown, item switching, real consumption, containers, and ordinary exertion at full reserves. The accepted vanilla server additionally reports all **42 native enchantment definitions**, with zero unsupported positive definitions, through 17 diagnostic cases.

The subsequent optional tool-reflection correction passed **3,641 block-loot tool input checks** and **150 loot acquisition checks** in `native-reflection-final-build-4.log`. The ingredient snapshot regression suite passed **155 checks** in `source-closure-final-build-9.log`; that invocation's unrelated source-closure failure is superseded by the successful `renewable-chance-final-build-11.log`, which records **46 authoritative acquisition closure checks**, **68 quest acquisition checks**, both loader builds and all **321 runtime assets per loader** matching their processed resources. The accepted `final-6` profile's evidence, economy, runtime, skills and validation exactly match the preceding vanilla `final-5` profile. The final ingredient snapshot and authoritative-source corrections leave these measured vanilla values unchanged. Its saved-profile presentation audit is recorded in `vanilla/final-6/layout-audit.log` and `native-skill-layout-audit.json`.

The final native condition correction passed **45,614 enchantment checks** and **156 capability checks** in `impossible-condition-final-build-13.log`; both loader builds and all **321 runtime assets per loader** passed. An explicit empty item set cannot satisfy native `MatchTool`, so its impossible branch contributes no benefit. Create Potato's custom consumer remains unsupported consistently; no positive first-level benefit is invented.

All five final receipts are accepted on NeoForge SHA256
`276a07cf92885a00df1660064fff5b5372e2dd07090d2683ffdb896e9cb31522`.
Each covers fresh generation, one independent identical rebuild, every published
rank, native skill/enchantment diagnostics, an offline skill-tree audit, and a
saved restart with zero generation/evidence/solver/calibration/report work.
Every owned server confirmed all dimensions saved and stopped.

| Environment | Receipt | Ranks | Saved files | Saved load | Enchantments / unsupported |
| --- | --- | ---: | ---: | ---: | ---: |
| Vanilla | `vanilla/final-6` | 236 | 61 | 2.356s | 42 / 0 |
| ATM10 | `atm10/final-5` | 229 | 63 | 28.861s | 139 / 48 |
| ATM10 Sky | `atm10-sky/final-2` | 229 | 65 | 15.947s | 85 / 23 |
| StoneBlock 4 | `stoneblock/final-1` | 231 | 60 | 7.813s | 90 / 33 |
| OceanBlock 2 | `oceanblock/final-1` | 236 | 59 | 6.808s | 55 / 4 |

Across the five profiles, all 1,161 published ranks and 95,355 transaction-state
checks pass. All six skill trees per profile pass prerequisite/rank checks.
Enchantment diagnostics cover 1,269 defined level rows, with no decreasing
payouts or failed diagnostic bounds. Unsupported custom effects remain explicit.
The source recipe snapshot is byte-identical, and all 321 assets match in both
loader jars. `completion-summary.json` and `final-enchantment-summary.json`
contain the consolidated receipts.

Custom effect coverage is not universal: the final ATM10 report lists 48
unsupported positive definitions among 139, including Apothic Life Mending's
`repair_with_hp` and Create Potato Recovery's custom projectile consumer.
Impossible native item conditions no longer invent a level-I benefit. The
MineColonies effect that starts at level II is recognized, but ATM10's zero
plain-book carrier budget limits its credited premium to zero; funded cases
are covered by the focused tests. These are explicit coverage/conservation
limits, not fabricated native power. Native headless checks do not establish
live multiplayer presentation or subjective player feel.
