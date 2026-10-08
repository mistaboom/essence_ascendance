# Commands

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Troubleshooting](https://github.com/mistaboom/essence_ascendance/wiki/Troubleshooting)

All mod chat commands start with `/essence`. Use Tab completion to discover registered branches and identifiers. Start with `/essence help`; operator menus appear to sources with permission level 2.

| Reference | Permission | Typical work |
| --- | --- | --- |
| [Player commands](https://github.com/mistaboom/essence_ascendance/wiki/Player-Commands) | No operator requirement | Wallets, Bonuses, Attunement, Ascension and milestones |
| [Administration commands](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands) | Level **2** | Grants, resets, tier/item edits, profile reload/rebuild and export |
| [Diagnostics and testing](https://github.com/mistaboom/essence_ascendance/wiki/Diagnostics-and-Testing) | Level **2** | Saved balance explanations, player/machine snapshots and scoped invariant checks |

## Syntax and source rules

`<argument>` is required; `[argument]` is optional. Angle/square brackets are notation, never characters to type. `all` is accepted only by operations that explicitly offer it; some register it as a branch and Attunement handles it as a reserved category value. Entries identify one of these source contexts:

| Context | Player | Console |
| --- | --- | --- |
| **Server** | Yes | Yes |
| **Player** | Yes, own context | No |
| **Target** | Self if omitted, or one explicit online player | Supply the trailing player argument |

Targets use Minecraft's single-player entity selector argument. Online names and selectors resolving to exactly one player are accepted. There is no offline-player or mass-edit support. `@s` selects the sender in game; it does not select a player from the server console. A console can use a suitable single-player selector such as `@p`; verify which player it resolves to before a mutation. Sender permissions remain in force when targeting someone else.

Examples on mutation pages target the in-game operator with `@s`. Use them only in a staging world or after the backup recommendation beside the operation.

## Identifiers

For Essence, Bonus/stat, Skill, milestone and player-tier arguments, short IDs normally default to `essence_ascendance:`. Thus `offense` means `essence_ascendance:offense`. They do not silently default to `minecraft:` or correct letter case.

Use quoted full IDs:

```mcfunction
/essence balance "essence_ascendance:offense"
/essence bonuses show "essence_ascendance:melee_damage"
/essence debug balance item "minecraft:iron_ingot"
```

Quotes matter: the command string argument does not accept `:` or `/` in an unquoted token. Completion quotes qualified IDs for you. Minecraft resource identifiers use lowercase letters, digits, `_`, `-` and `.`; their path can also contain `/`.

Two exceptions deserve attention:

- `debug balance item <id>` directly looks up the **full saved item ID**; `iron_ingot` does not acquire a `minecraft:` prefix.
- `admin item tier set <tier>` takes a serialized item-tier name, not a namespaced ID.

Essence categories are `offense`, `defense`, `vitality`, `mobility`, `gathering` and `utility`. Bonus list/gameplay category arguments accept these case-insensitively. Attunement category arguments resolve Essence IDs. Tier names are `latent`, `dormant`, `awakened`, `resonant`, `ascendant` and `transcendent`; individual handlers still require a known registered tier.

## Numeric arguments

| Term used in the references | Exact accepted command range |
| --- | --- |
| Positive amount | Integer **1..9,223,372,036,854,775,807** |
| Nonnegative amount | Integer **0..9,223,372,036,854,775,807** |
| Attunement percent | Integer **0..100** |
| History page | Integer **1..2,147,483,647**, then bounded by actual stored pages |
| Balance Skill rank | Integer **1..1000**, then bounded by the saved generated curve |

Accepted syntax does not guarantee funds, storage capacity, a valid purchase, or an existing rank. Large amounts can fail overflow or cap validation.

## Three different meanings of balance

| Command | Meaning | Consequence |
| --- | --- | --- |
| `/essence balance` | Your available wallet Essence | Reads currency |
| `/essence debug balance summary` | The installed generated profile | Reads resolved balance data |
| `/essence admin balance rebuild` | Generate a replacement from current inputs/environment | Writes files and replaces runtime authority |

## Effects and output

Diagnostic entries say when normal reconciliation occurs: chapter alignment, equipment attribute synchronization or machine link refresh. These do not spend Essence or grant progress, but are not a promise of absolutely no incidental state effects. Attribute synchronization can clamp health to its current maximum.

Admin target mutations refresh the player's progression state. Output goes to the command sender; ordinary successes are not broadcast to all operators. Sanitize player names, ownership, locations and local paths before sharing output.

There are no separate `/archive`, `/essence skills`, `/essence stat` or top-level `/essence rebuild` aliases. Skills are purchased through the Nexus; administrative Skill commands are testing/recovery tools.

Implementation: [root registration](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceCommands.java), [arguments, IDs and permissions](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceCommandUtil.java).
