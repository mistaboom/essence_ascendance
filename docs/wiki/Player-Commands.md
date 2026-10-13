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

## Native teams: beta.5

**Added in beta.5.** All entries here require a player sender and **no operator level**. Console and trailing player targets are unsupported. Mutations require established native authority and ready membership; selected external providers own their own membership interface. [Cooperation and trust boundary](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure).

`<team>`, `<request>` and `<member>` are UUID arguments, not player names, selectors or namespaced item IDs. Obtain current team/request IDs from status/requests; member UUIDs must identify actual members for management operations. There are no custom UUID completion suggestions. The synthetic UUID in examples is valid syntax only: replace it with the actual ID in a staging session. `<name>` is a Brigadier string; quote spaces, use 1..80 characters and avoid blank names.

| Exact syntax | Prerequisites, output and effects | Valid syntax example |
| --- | --- | --- |
| `/essence team` | Same status handler as `team status`; own effective pool, actual members/capabilities and stored millionths, or unavailable reason. Does not spend; normal membership probes/solo-identity initialization can occur. | `/essence team` |
| `/essence team status` | Status alias; external authority also explains complete-roster sharing. No native mutation prerequisite. Sanitize identifiers before sharing output. | `/essence team status` |
| `/essence team interface` | Prints provider-aware or machine-M guidance; opens no independent team GUI and changes no membership. | `/essence team interface` |
| `/essence team create <name>` | Actor must be solo. Creates a native team with the actor as owner; prints new ID. Personal machinery follows team ownership automatically. **Changes authority/ownership context:** stage membership experiments. | `/essence team create "Workshop Team"` |
| `/essence team request <team> share-stored` | Solo applicant, existing native team, no other pending request. Exact `share-stored` acknowledgment is required. Stores and prints a request token; approval later shares existing Stored and personal infrastructure. No automatic join. | `/essence team request 00000000-0000-0000-0000-000000000001 share-stored` |
| `/essence team requests` | Lists actor's outgoing and owner-manageable incoming requests. Prunes/refreshes stale requests, so this is a query with normal metadata reconciliation. | `/essence team requests` |
| `/essence team cancel <request>` | Only that request's applicant. Removes the pending request; prints completion, without transferring funds. | `/essence team cancel 00000000-0000-0000-0000-000000000001` |
| `/essence team approve <request> [name]` | Only destination native owner; applicant/context must still be valid. A name of 1..80 characters is **required when approval creates a new team**; quote names containing spaces. Missing/invalid names preserve the request, membership and funds. Existing-team approvals need no name and retain their team's name even if one is supplied. **Shares existing Stored and joins infrastructure:** back up/rehearse if wealth matters. Prints completion. | `/essence team approve 00000000-0000-0000-0000-000000000001 "Shared Workshop"` |
| `/essence team decline <request>` | Only destination native owner; deletes request and reports completion. No pooling. | `/essence team decline 00000000-0000-0000-0000-000000000001` |
| `/essence team leave` | Current non-management member. Detaches remaining Stored share and revokes team infrastructure access; prints completion. Owner must transfer or finally disband. **Membership/wealth change:** stage or preserve a coordinated backup. | `/essence team leave` |
| `/essence team remove <member>` | Native owner, current non-management member. Detaches member's remaining Stored share; leaves infrastructure with team; reports completion. **Removal changes access/wealth:** verify the actual member UUID first. | `/essence team remove 00000000-0000-0000-0000-000000000001` |
| `/essence team transfer <member>` | Native owner; successor must be a current member. Transfers management, preserves roster/funds, and reports completion. **Changes control:** verify successor. | `/essence team transfer 00000000-0000-0000-0000-000000000001` |
| `/essence team disband` | Native owner must be the final member. Ends team and returns stored/device ownership through personal lifecycle; reports completion. **Dissolves team:** stage or back up first. | `/essence team disband` |

Rejected native operations return the handler's reason and do not silently fall back to external or solo membership. A request is not a membership grant. The personal request/approval GUI is on the actual machine; external FTB Teams/OpenPAC installations hide M even if saved authority is still native.

## Machine controls: beta.5

These commands require a live player in the machine's dimension, a **loaded** matching block entity and normal menu reach: at least the vanilla 8-block allowance, or current block-interaction range plus 1.5 blocks when greater. They never load a target chunk. No console or trailing player target is accepted. Ordinary actions need current owner/team permission; operators do not gain a general inventory bypass.

`<machine>` and `<source>` are three-coordinate block-position arguments. Absolute, `~` relative and Minecraft `^` local coordinate forms follow Brigadier's coordinate grammar; do not mix local and world coordinates. Examples assume the relevant machine is one block beneath the sender. Branch completion is available; there is no mod-specific machine/UUID picker.

| Exact syntax | Permission, prerequisites, effects and output | Valid syntax example |
| --- | --- | --- |
| `/essence machine <machine> status` | No operator level for authorized users; **level 2** can inspect an otherwise inaccessible machine. Reports binding/provenance/operator/recipient/pause, plus link or shared amount/local fill limit. Normal ownership refresh can occur; no spend. | `/essence machine ~ ~-1 ~ status` |
| `/essence machine <machine> start` | No operator level; Configure permission. Crucible enables **dissolution**, not the player's incoming channel. Infuser additionally needs installed Focus and valid spending/recipient context. Unbound team equipment needs the GUI soulbinding confirmation; this command cannot supply it. **Enables paid processing:** stage or check inputs first. | `/essence machine ~ ~-1 ~ start` |
| `/essence machine <machine> stop` | No operator level; Configure permission. Stops addressed Crucible dissolution or Infuser processing, without refunding paid work or stopping unrelated consumers. | `/essence machine ~ ~-1 ~ stop` |
| `/essence machine <machine> link automatic` | No operator level; authorized Infuser/Pylon. Explicitly clears the current target and immediately refreshes eligible nearest-source discovery. Subsequent normal Automatic refresh keeps a valid target stable. **Routing change:** inspect status first. | `/essence machine ~ ~-1 ~ link automatic` |
| `/essence machine <machine> link disconnected` | Same permission/machine requirements. Clears target and disables its source link. **Pauses supply:** inspect before changing a live pipeline. | `/essence machine ~ ~-1 ~ link disconnected` |
| `/essence machine <machine> link locked <source>` | Same permission; supplied same-dimension source must be a loaded, eligible, in-range Crucible. Retains that specific source rather than replacing it with nearest discovery. **Routing change.** | `/essence machine ~ ~-1 ~ link locked ~2 ~-1 ~` |
| `/essence machine <machine> recover-item input` | No operator level; Infuser input must be soulbound to the sender. Returns only that item to inventory/drop fallback, carrying supported paid-work recovery data. Grants no inventory/spending authority. **Custody change:** preserve a backup for valuable partial-work recovery. | `/essence machine ~ ~-1 ~ recover-item input` |
| `/essence machine <machine> recover-item output` | Same narrow ownership/reach rule for output; returns only sender's soulbound output. | `/essence machine ~ ~-1 ~ recover-item output` |

Pylons reject processing Start/Stop; Crucibles reject source-link and workpiece-recovery actions. Mutations return command success or a denial reason; they do not all print a success summary. Player receiving Start/Stop is the Crucible's existing UI operation, with one active source per player; `machine start` is not its chat alias.

The ninth machine branch, **`/essence machine <machine> recover <recipient>`**, requires level 2 and is documented canonically under [narrow administrator recovery](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#narrow-machine-recovery-beta5). There are no adoption, privatization, automation-port or recipient-selection command branches.

Current sources: `command/EssenceTeamCommands.java`, `command/EssenceMachineCommands.java`, `team/NativeMembershipService.java`, `network/TeamInterfaceNetworkService.java`, `network/ServerMenuAccess.java`, `machine/BoundMachine.java`, and `infuser/EssenceInfuserBlockEntity.java`, under `common/src/main/java/com/mistaboom/essence_ascendance/`. These handlers are in the [beta.5 source](https://github.com/mistaboom/essence_ascendance/tree/v1.0.0-beta.5/common/src/main/java/com/mistaboom/essence_ascendance); the links below retain their historical baseline scope.

## Milestones

`<milestone>` is a known configured milestone ID, such as `obtain_diamonds`. These report provider state without granting or permanently capturing completion. Milestones can gate Skills; they are not the current requirement for player Ascension.

| Exact syntax | Source | Prerequisites and result | Valid example |
| --- | --- | --- | --- |
| `/essence milestones` | Player | All configured states: complete, incomplete or unresolved | `/essence milestones` |
| `/essence milestones <milestone>` | Player | Known configured ID; provider, target and state. Does not accept an arbitrary old saved flag. | `/essence milestones obtain_diamonds` |

Source: [player handlers](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/EssenceCommands.java), [Attunement handlers](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/command/AttunementCommands.java), [investment validation](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/progression/StatProgressionService.java).
