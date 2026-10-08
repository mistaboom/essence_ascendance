# Administration commands

[Commands index](https://github.com/mistaboom/essence_ascendance/wiki/Commands) · [Backups and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery)

**Permission: level 2 for every command and help endpoint on this page.**

“Target” means self if the optional trailing `[player]` is omitted, or one online player if supplied. Console callers **must** supply it. “Server” supports console and players. Successful targeted actions refresh progression. See [target syntax](https://github.com/mistaboom/essence_ascendance/wiki/Commands#syntax-and-source-rules).

Examples using `@s` are for an in-game operator in a staging world. They never mean “all players.” None of these commands was executed to prepare this wiki.

## Quick index

| Task | Section | Main effect |
| --- | --- | --- |
| Read config / install saved data | [Profile operations](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#profile-operations) | Read or reinstall authority |
| Regenerate / export | [Profile operations](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#profile-operations) | Replace authority / write derived reports |
| Edit a player's tier or reset | [Player tier and reset](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#player-tier-and-reset) | Changes progression |
| Edit currency or reservoir | [Wallets](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#wallets), [Crucible reservoir](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#crucible-reservoir) | Grants or removes stored value |
| Edit Bonuses, Skills, milestones | Sections below | Bypasses normal purchases or changes gates |
| Edit seals / held equipment | [Attunement](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#attunement-overrides), [item tier](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#held-item-tier) | Changes player chapter progress / item |
| Replace a book | [Archive recovery](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#replace-the-archive) | Gives an Archive |

## Profile operations

These commands require a running server. Their file paths are relative to its configuration directory.

| Exact syntax | Source | Prerequisites, effects and output | Valid example |
| --- | --- | --- | --- |
| `/essence admin config` | Server | Prints config path/version/profile, Pylon settings, definition counts and harvest safety warnings. No reload or full-key dump; can show provisional config without installed authority. | `/essence admin config` |
| `/essence admin mappings reload` | Server | Validates/reinstalls the saved profile, refreshes players and syncs runtime/tooltips. Existing file means no new environment generation; a missing file can generate. Rejection keeps previous valid state. Prints status/counts/paths. **Reinstalls authority:** back up profile/inputs first when replacing disk data. | `/essence admin mappings reload` |
| `/essence admin balance rebuild` | Server | Generates from current inputs and environment, validates/preflights, durably replaces the profile, installs/syncs and writes routine reports. **Replaces balance and may stall the server:** stage changes and back up the complete configuration plus world before a maintenance window. Rejection before commit retains old valid authority. | `/essence admin balance rebuild` |
| `/essence admin balance export` | Server | Requires active saved snapshot. Writes exhaustive derived reports including acquisition sources/dependencies; no provider scan or price change. **Writes report files:** retain any reports needed for before/after comparison. | `/essence admin balance export` |

A report or client-sync failure after successful install does not roll back that install. Inspect manifest/client readiness separately. [Lifecycle details](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation).

## Player tier and reset

`<tier>` is a registered player-tier ID: `latent` through `transcendent`. Full IDs must be quoted.

| Exact syntax | Source | Prerequisites, effects and output | Valid example |
| --- | --- | --- | --- |
| `/essence admin player tier set <tier> [player]` | Target | Known tier; sets it without earning seals, forgets transient activity tracking, refreshes and may deliver the Archive. **Changes earned progression:** stage and back up world/player data. | `/essence admin player tier set dormant @s` |
| `/essence admin player reset [player]` | Target | **Destructive full mod reset.** Clears wallets, investments, reservoir, Skill receipts/selections/cooldowns, mod milestone flags, Attunement and all owner fractional accounts; returns to Latent and rearms Archive delivery. **Back up the world and player data before use.** | `/essence admin player reset @s` |

Reset preserves physical inventory/equipment and vanilla advancement progress. Provider-complete gates can become effective again. It does not delete a world or regenerate a profile. Reset is far broader than book replacement.

## Wallets

`<essence>` is a registered Essence ID. Give uses a positive integer **1..9,223,372,036,854,775,807**; set uses a nonnegative integer **0..9,223,372,036,854,775,807**. Amounts are wallet Essence units.

| Exact syntax | Source | Prerequisites, effects and output | Valid example |
| --- | --- | --- | --- |
| `/essence admin player essence give <essence> <amount> [player]` | Target | Known Essence; adds wallet value and reports new balance. Overflow fails before storing, without partial credit. **Grant:** use a staging player for economy tests. | `/essence admin player essence give offense 100 @s` |
| `/essence admin player essence set <essence> <amount> [player]` | Target | Known Essence; directly sets wallet value. **Can erase funds:** back up player/world data. | `/essence admin player essence set offense 100 @s` |
| `/essence admin player essence clear <essence> [player]` | Target | Known Essence; zeroes one wallet. **No refund:** back up player/world data. | `/essence admin player essence clear offense @s` |
| `/essence admin player essence clear all [player]` | Target | Zeroes all registered wallets. **No refund:** back up player/world data. Investments and reservoir are preserved. | `/essence admin player essence clear all @s` |

## Bonuses

`<bonus>` is a registered stat ID such as `melee_damage`. Set's amount is **stored investment**, a nonnegative integer **0..9,223,372,036,854,775,807**, not a damage/effect value.

| Exact syntax | Source | Prerequisites, effects and output | Valid example |
| --- | --- | --- | --- |
| `/essence admin player bonuses set <bonus> <amount> [player]` | Target | Known Bonus; sets stored investment without spending/cap purchase checks. Effective power remains tier/applicability-limited. **Direct overwrite:** back up player data; use staging for tuning. | `/essence admin player bonuses set melee_damage 100 @s` |
| `/essence admin player bonuses max <bonus> [player]` | Target | Known Bonus/resolvable current cap; sets investment to that cap at no cost. **Free investment:** test in staging. | `/essence admin player bonuses max melee_damage @s` |
| `/essence admin player bonuses max all [player]` | Target | Same for every registered Bonus. **Free investments:** test in staging. | `/essence admin player bonuses max all @s` |
| `/essence admin player bonuses clear <bonus> [player]` | Target | Known Bonus; zeroes investment. **No refund:** back up world/player data. | `/essence admin player bonuses clear melee_damage @s` |
| `/essence admin player bonuses clear all [player]` | Target | Zeroes all investments, preserving wallets. **No refund:** back up world/player data. | `/essence admin player bonuses clear all @s` |

These commands do not change generated Bonus magnitude or costs for the pack. Use [configuration/overrides](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization) for that.

## Skills

`<skill>` is a current catalog ID such as `frenzy`. Ownership, activation and effectiveness are separate.

| Exact syntax | Source | Prerequisites, effects and output | Valid example |
| --- | --- | --- | --- |
| `/essence admin player skills grant <skill> [player]` | Target | Known Skill; creates a missing rank-one receipt at zero paid cost. Existing receipt is preserved. **Free grant:** stage it; not every rank or guaranteed effectiveness. | `/essence admin player skills grant frenzy @s` |
| `/essence admin player skills grant all [player]` | Target | Grants missing zero-cost rank-one receipts for catalog; reports newly granted count. **Mass grant:** stage and back up player data. | `/essence admin player skills grant all @s` |
| `/essence admin player skills clear all [player]` | Target | Deletes all receipts, selections and ability cooldowns. **No refund:** back up world/player data. No single-Skill clear branch exists. | `/essence admin player skills clear all @s` |
| `/essence admin player skills activate <skill> [player]` | Target | Known owned Skill and valid plan using owned prerequisites. Changes relevant suppressions/prerequisite and choice selections. **Loadout mutation:** record choices or use staging. Does not buy ranks or waive tier/effective gates. | `/essence admin player skills activate frenzy @s` |

A zero-cost grant has no historical paid investment to contribute to Skill-spending acceleration. It is not a substitute for a normal affordability test.

## Milestones

`<milestone>` must be a configured INTERNAL milestone or a catalog-referenced permanent Skill milestone ID. `defeat_wither` is an internal example; `sky_limit` is a Skill-gate example.

| Exact syntax | Source | Prerequisites, effects and output | Valid example |
| --- | --- | --- | --- |
| `/essence admin player milestones grant <milestone> [player]` | Target | Accepted gate; stores mod completion and reports effective state. **Changes unlock state:** stage and back up player data. Does not award a provider's own vanilla advancement. | `/essence admin player milestones grant defeat_wither @s` |
| `/essence admin player milestones revoke <milestone> [player]` | Target | Removes mod stored flag, refreshes and checks effective completion. **Changes unlock state:** back up player data. It can report failure after mutation if the provider still completes the gate. | `/essence admin player milestones revoke defeat_wither @s` |

Internal milestones use their configured target flag; permanent Skill overrides use the definition ID. Provider progress belongs to its own system. Do not treat a failed revoke as proof nothing changed.

## Crucible reservoir

`<essence>` is registered. This storage is separate from the player's wallet.

| Exact syntax | Source | Prerequisites, effects and output | Valid example |
| --- | --- | --- | --- |
| `/essence admin player crucible clear <essence> [player]` | Target | Clears that owner's shared reservoir and `dissolution/<essence-id>` fractional carry. **Destroys stored value:** back up world/player and fractional data. Wallet preserved. | `/essence admin player crucible clear gathering @s` |
| `/essence admin player crucible clear all [player]` | Target | Same for all registered Essences of that owner. **Destroys stored value:** back up world/player and fractional data. Not every owner's machines. | `/essence admin player crucible clear all @s` |

## Replace the Archive

**Exact syntax:** `/essence admin player guide give [player]`\
**Permission/source:** level 2; Target.

No tier, cost or prior-gift restriction. Gives the Archive in inventory or drops it with target pickup ownership; successful delivery records receipt, while failure can retry. This is a low-impact recovery operation; a staging player suffices for delivery testing.

**Valid example:** `/essence admin player guide give @s`. Do not reset progress merely to replace a book.

## Attunement overrides

These require active authority and the target's current chapter. `<category>` is one registered Essence ID; the reserved value `all` selects every category and is case-insensitive. `<percent>` is an integer **0..100**.

**Progress overwrite:** stage first and back up player/world data for every entry below. Overrides clear selected method/repetition/recent/root tracking and relevant exploration/healing bookkeeping, then mark/refresh saved state. They can auto-promote a ready Latent player to Dormant. They do not auto-ascend later powered chapters.

| Exact syntax | Source | Effect | Valid example |
| --- | --- | --- | --- |
| `/essence admin player attunement set <category> <percent> [player]` | Target | Set selected normalized seal progress | `/essence admin player attunement set mobility 50 @s` |
| `/essence admin player attunement set all <percent> [player]` | Target | Set every category | `/essence admin player attunement set all 50 @s` |
| `/essence admin player attunement fill <category> [player]` | Target | Set selected category to 100% | `/essence admin player attunement fill gathering @s` |
| `/essence admin player attunement fill all [player]` | Target | Set every category to 100% | `/essence admin player attunement fill all @s` |
| `/essence admin player attunement reset <category> [player]` | Target | Set selected category to 0% | `/essence admin player attunement reset gathering @s` |
| `/essence admin player attunement reset all [player]` | Target | Set every category to 0% | `/essence admin player attunement reset all @s` |

## Held item tier

**Exact syntax:** `/essence admin item tier set <tier> [player]`\
**Permission/source:** level 2; Target.

The target must hold first-party Ascendance equipment or an Essence Focus in the main hand. `<tier>` accepts `latent`, `dormant`, `awakened`, `resonant`, `ascendant` or `transcendent`, case-insensitively; qualified IDs are not accepted.

Sets the item tier and clears incomplete infusion data without material/Essence cost. Non-Latent unbound equipment becomes soulbound to the target; existing binding is preserved even on demotion. Focus uses its own tier mapping and special Latent handling. This does not edit arbitrary third-party gear or set player tier.

**Item overwrite:** back up the world/inventory and test with a staging item.\
**Valid example:** `/essence admin item tier set dormant @s`, while holding supported equipment.

## Help endpoints

Every endpoint below is **level 2, Server**, takes no arguments and prints a submenu without spending, reset or generation. In each row **both displayed forms are exact executable commands and valid examples**.

### General and item menus
| Exact menu example | Exact help example |
| --- | --- |
| `/essence admin` | `/essence admin help` |
| `/essence admin player` | `/essence admin player help` |
| `/essence admin item` | `/essence admin item help` |
| `/essence admin item tier` | `/essence admin item tier help` |
| `/essence admin item tier set` | `/essence admin item tier set help` |
| `/essence admin mappings` | `/essence admin mappings help` |
| `/essence admin balance` | `/essence admin balance help` |
| `/essence admin player attunement` | `/essence admin player attunement help` |

### Player tier and wallet menus

| Exact menu example | Exact help example |
| --- | --- |
| `/essence admin player tier` | `/essence admin player tier help` |
| `/essence admin player tier set` | `/essence admin player tier set help` |
| `/essence admin player essence` | `/essence admin player essence help` |
| `/essence admin player essence give` | `/essence admin player essence give help` |
| `/essence admin player essence set` | `/essence admin player essence set help` |
| `/essence admin player essence clear` | `/essence admin player essence clear help` |
| `/essence admin player bonuses` | `/essence admin player bonuses help` |
| `/essence admin player bonuses set` | `/essence admin player bonuses set help` |
| `/essence admin player bonuses max` | `/essence admin player bonuses max help` |
| `/essence admin player bonuses clear` | `/essence admin player bonuses clear help` |

### Skills, gates and storage menus

| Exact menu example | Exact help example |
| --- | --- |
| `/essence admin player skills` | `/essence admin player skills help` |
| `/essence admin player skills grant` | `/essence admin player skills grant help` |
| `/essence admin player skills clear` | `/essence admin player skills clear help` |
| `/essence admin player skills activate` | `/essence admin player skills activate help` |
| `/essence admin player milestones` | `/essence admin player milestones help` |
| `/essence admin player milestones grant` | `/essence admin player milestones grant help` |
| `/essence admin player milestones revoke` | `/essence admin player milestones revoke help` |
| `/essence admin player crucible` | `/essence admin player crucible help` |
| `/essence admin player crucible clear` | `/essence admin player crucible clear help` |
| `/essence admin player guide` | `/essence admin player guide help` |

The bare commands `/essence admin player attunement set`, `/essence admin player attunement fill` and `/essence admin player attunement reset` also execute that menu (same permission/source/effects; each is a valid example). They **do not** accept an appended `help` child: it is parsed as an invalid category. There is no `guide give help`.

Source: [admin registrations and handlers](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceAdminCommands.java), [Attunement mutations](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/AttunementCommands.java), [profile operations](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/GeneratedBalanceService.java).
