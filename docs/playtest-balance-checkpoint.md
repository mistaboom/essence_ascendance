# Playtest balance completion — October 11, 2026

The approved implementation and final headless verification are complete.
Stopped with account usage below the user's 50% ceiling; final observed reading
is recorded in the chat. No test servers remain running. No original Prism
worlds were touched. This records the earlier balance pass; the subsequent beta.3 release includes
these changes. See `progression-balance-followup.md` and the beta.3 release notes.

## Delivered

- Shared native enchantment type/level/effect evidence for real-stack yields,
  Archive/tooltips and skill/Bonus competition, with conserved multi-book values.
- General material demand, native harvest counts and acquisition dependency fixes
  reduce copper without an item-specific price rule.
- Restful minimum 1 durability point/second, later meaningful increment 1;
  stronger generated ranks remain allowed. Metabolic minimum 1 point per
  intrinsic food point, later increment 0.5. Other meaningful minima preserved.
- Inner Sustenance keeps recovery and loses full-reserve exhaustion immunity.
  Feast Reflex automatic meals obey persistent native eating cadence.
- Default opening effort doubled; Archive delivery unchanged.
- Matrix art uses the Archive asset exporter; all seven user recipes preserved.

## Final artifacts and verification

NeoForge SHA256:
`276a07cf92885a00df1660064fff5b5372e2dd07090d2683ffdb896e9cb31522`

Fabric SHA256:
`0334c1b488413d3042f2325acd7c8cd957cc03be131b2d4b429886c34a7061a9`

Auxiliary receipt root:
`pack-compatibility/validation/performance/20261011-balance-completion`.

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

The full common/generation checks and native Fabric recovery/sustenance/resource
checks passed earlier in this pass. Final focused build-13 passed 45,614
enchantment checks, 156 capability checks, both loader builds and packaged
assets. Acquisition closure46, quest68, ingredients155, material52 and the
related source/conservation suites passed after their corresponding fixes.
Superseded failed/interrupted candidates remain as traceable receipts and are
not final acceptance evidence.

## Existing installations

Revision `enchantment-native-maintenance-47` requires explicit
`/essence admin balance rebuild`. Existing human configuration is preserved:
set `onboarding_effort_fraction = 1.0` before rebuilding to adopt the doubled
opening if it is currently 0.5. Existing progression, paid ranks and refunds
remain preserved. Old percentage repair values are not reinterpreted as points.

## Remaining limits

Custom effect coverage is not universal: the final ATM10 report lists 48
unsupported positive definitions among 139, including Apothic Life Mending's
`repair_with_hp` and Create Potato Recovery's custom projectile consumer.
Impossible native item conditions no longer invent a level-I benefit. The
MineColonies effect that starts at level II is recognized, but ATM10's zero
plain-book carrier budget limits its credited premium to zero; funded cases
are covered by the focused tests. These are explicit coverage/conservation
limits, not fabricated native power. Native headless checks do not establish
live multiplayer presentation or subjective player feel.

Unsupported source actions such as Sniffer digging and wither-kill flowers
remain conservative. Full measured details are in `playtest-balance-followup.md`
and `survival-balance-audit.md`. No further work is required for this agreed pass;
future subjective playtesting or additional custom-mod adapters are separate.
