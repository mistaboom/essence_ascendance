# Teams and shared infrastructure

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Commands](https://github.com/mistaboom/essence_ascendance/wiki/Commands) · [Updating and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery)

**Beta.9 membership scope:** native authority and functional FTB Teams membership. Use matching builds on the server and clients. Upgrading from earlier betas requires fresh worlds and fresh Essence Ascendance configuration; older formats are not migrated. Keep historical worlds with their matching build and inputs. Install the intended supported team provider before creating a new world. Dated beta.5 receipts retain their original scope in [version history](https://github.com/mistaboom/essence_ascendance/wiki/Version-and-Maintenance).

This is the pack-maker and administrator reference. Players should use the connected server's [Ascendance Archive](https://github.com/mistaboom/essence_ascendance/wiki/Players-and-the-Archive) for lessons, machine operation and resolved values.

The completed Archive continuation's central player entry is **Reference → Mechanics → Teams and Shared Essence**. It is read-only guidance, not a team manager or live-roster dashboard. Existing Guide lessons link to it; see the [confirmed Archive routes](https://github.com/mistaboom/essence_ascendance/wiki/Players-and-the-Archive#where-to-look).

## What cooperation changes

Solo works by default. Joining a team lets members operate shared gathering, dissolution and infusion infrastructure. **Stored Essence becomes communal; Available Essence and invested progression stay personal.** A member can spend the communal reservoir rather than being limited to their original contribution.

Approval or external party membership shares existing accumulated Stored Essence as well as future deposits. Treat membership as permission to use that wealth. This is not a contribution-protected bank, and going offline does not remove a member from the roster.

The reservoir is an aggregate view of authoritative player-backed stored shares. There is no independently spendable team treasury. Joining equalizes the new roster's stored shares; a departure or removal retains the departing player's equal remaining share from the **pre-departure roster**. Exact fractional units and conserved remainder ownership participate in the split. An indivisible remainder is retained in player shares, not discarded or duplicated by repeated reads.

**Hypothetical accounting example:** two players bring 900 and 300 Stored Essence, giving 1,200 communal Stored. One channels 200 into their personal Available wallet, leaving 1,000 Stored. A departure leaves 500 Stored with each player; the channeled 200 remains personal. This example assumes exact divisible amounts and no other spending. It matches an existing accounting fixture, not a live two-client test.

Already-paid infusion work is outside spendable Stored Essence. Leaving does not refund completed spending or committed partial work. Machine pickup and narrow equipment recovery preserve the existing work identity where supported; they do not pay it back into the reservoir.

## Membership providers

Essence Ascendance uses one membership authority per world. External support means the actual complete party roster participates automatically, including offline members. There is no second Essence Ascendance enrollment or per-machine member list.

| Authority | Accepted installation in current source | Meaning |
| --- | --- | --- |
| Native | No external provider needed | Solo default; explicit native team creation, requests and owner decisions |
| FTB Teams | **FTB Teams 2101.1.11 + FTB Library 2101.1.30, 2101.1.36 or 2101.1.37**, Fabric or NeoForge, Minecraft 1.21.1 | Actual party members; officers/owner receive management capability. Player/default teams remain solo; administrative server groupings are excluded. |

FTB Teams invitations, allies and claims trust are not financial membership. A starting or unavailable supported provider is not confirmation that every party disappeared. FTB Teams is the only external membership adapter; arbitrary team mods do not control Essence Ascendance membership.

### Dependencies and evidence limits

Install the selected loader's provider and dependencies on **both server and clients**. Essence Ascendance's provider APIs are compile-only and are not bundled or automatically installed.

- FTB Teams' matching metadata requires FTB Library **at least 2101.1.30**, but the Essence Ascendance membership adapter accepts **exactly 2101.1.30, 2101.1.36 or 2101.1.37** with Teams **2101.1.11**. Architectury is required; Essence Ascendance's own **13.0.11** minimum satisfies these inspected provider metadata requirements. Fabric also needs Fabric API. Use the Fabric builds of Teams/Library on Fabric and the NeoForge builds on NeoForge; FTB Teams itself is available on both loaders.
- Historical beta.5 initialized-loader receipts included adapters later removed. They remain dated evidence in the maintenance audit, not the current provider roster. Use the current build's dependency declarations and the FTB tuples above.
- Compilation and API-interface fixtures passed for the accepted versions. The fixtures used synthetic rosters and memory-backed machines and exited before opening a world/provider manager. Actual provider managers, live events, world/process restarts, two-client fairness and graphical interaction remain pending. Other versions and arbitrary dependency combinations are not verified.

FTB Teams membership is a functional integration, separate from balance generation. The current generator does not read FTB Quests definitions or use quest completion as acquisition evidence. Historical quest/provider receipts do not add current membership support. Live-provider behavior remains a narrower claim than compilation or synthetic-roster fixtures.

## Selection and established worlds

The server JVM property is **`essence.membershipAuthority`**, default **`auto`**. This is not a TOML key or a separate teams configuration file. [Exact setting reference](https://github.com/mistaboom/essence_ascendance/wiki/Configuration#membership-authority-server-jvm-property).

On an initial world selection, AUTO prefers supported **FTB Teams**, then native when FTB Teams is absent. A supported FTB provider that is starting or failed remains selected. An installed but unsupported FTB Teams version blocks native AUTO rather than silently granting solo access. Other team mods are not registered membership authorities.

AUTO retains an established world's saved authority. Installing, removing or replacing a provider does not silently switch it. An explicit different selection pauses access until an operator reviews an authority change. Missing, unsupported, failed and initializing membership are not empty rosters and do not grant solo permissions.

Ordinary external joins, departures, kicks, role changes and confirmed party dissolution use normal reconciliation automatically. They do not require an administrator migration command. Captures must provide two matching complete observations before publishing changed rosters. Event invalidation can settle promptly; the shared recovery/poll schedule is **100 server ticks**. Actual timing depends on server ticks and provider readiness, not a guaranteed wall-clock deadline.

### Deliberately changing authority

Back up the coordinated world and provider state first, and rehearse the change in an isolated environment. Use the exact [membership administration commands](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#membership-authority-beta5).

Prepare an explicit provider, inspect status, then activate the actual issued token within **6,000 server ticks** of preparation (about 5 minutes at 20 TPS). Preparation pauses financial/machine access while complete target observations settle. Status uses labeled lines for the established authority, access readiness, last observed provider states, remaining review time and, when ready, target party/member counts and policy. Its clickable activation suggestion inserts the actual token into chat for review and submission; clicking alone does not activate it. Reading status does not extend the deadline.

Activation revalidates the token, target roster/readiness, established authority and financial/roster revisions. Stale evidence rejects. Successful activation detaches old recorded shares and equalizes the target rosters' Stored Essence, while preserving personal Available/invested progression. Solo identities change; existing machine bindings from the old authority are **not remapped** into the new provider's pools. An authority switch is not proof that old teams dissolved. Record affected infrastructure and do not assume the narrow recovery command can claim it. New physical placement after successful activation uses the new authority.

Cancellation or expiry retains the persisted authority and resumes reconciliation only when selection allows it. Expiry is reported explicitly by subsequent status/activation attempts, with the preparation command needed to retry. `reconcile` retries the existing authority; it does not choose a new provider. Use AUTO or the successfully activated provider ID on subsequent starts.

## Machine ownership and permissions

Physical placement binds the machine to its placer and their current effective team or solo pool. Personal infrastructure automatically becomes team infrastructure on create/join. Persisted ownership-event receipts let unloaded machines follow the same lifecycle when they load; they do not need a separate ownership-management step.

An ordinary departure revokes access and leaves team infrastructure with the team, even if the departing member originally placed it. Native owners transfer management before leaving. Native disband requires the owner to be the final member; it returns infrastructure through the personal ownership lifecycle. These native restrictions do not prohibit an external provider's own party controls. A complete external dissolution records the former recovery recipient/owner and machinery resolves through that recipient's personal lifecycle, including a later team join. Incomplete evidence pauses instead of declaring a dissolution.

| Action | Current boundary |
| --- | --- |
| Open inventory, configure, install Focus, Start work, link | Current solo owner or authorized actual team member; live membership and machine identity must be ready |
| Spend shared Stored | Authorized participant; funding identity is separate from an equipment recipient |
| Vent a Stored category | Management capability; **clears that category from the whole effective reservoir**, including fractions. Stage or back up before intentional use. |
| Approve/decline native requests, remove members, transfer management | Native owner; membership capability and request validity are checked server-side |
| Outsider interaction | Slot-free access/request screen; no inventory browsing or settings access |
| Break a machine or extract ordinary output | Vanilla, recipe and exposed-slot rules; external protection mods can impose their own rules |

EA has no owner-only machine breaking/output extraction policy and no insertion/extraction enable-disable controls. A carried machine preserves legitimate contents and paid work once, clears the removed block entity, and strips old binding, link and placer provenance. Fresh placement, including a stolen machine item or an older portable item, establishes fresh ownership. Soulbound equipment inside still belongs to its existing equipment owner.

The native **M** interface is a compact popup on the actual machine. A request to a personal machine leaves both players solo until owner approval; both GUI and command approval require the owner to name a newly created team. Only the native owner decides requests. The command is `/essence team approve <request> [name]`; an existing-team approval needs no name and preserves its existing name. Missing or invalid creation names leave the request, membership and funds unchanged. See [native command details](https://github.com/mistaboom/essence_ascendance/wiki/Player-Commands#native-teams-beta5). Installing FTB Teams hides M even when saved authority remains native; use provider-aware guidance and the established-world flow rather than assuming authority changed. `/essence team interface` supplies guidance; it does not open a separate management screen. Info, M and supported Settings share one active popup.

## Links, storage limits and bandwidth

Pylons and Infusers default to **Automatic** linking without a routing GUI. They retain an eligible current source, otherwise choose the nearest eligible loaded Crucible in range. A closer foreign source is ineligible; a newly closer eligible source does not replace a still-valid link. Each Infuser has one specific source; many Infusers can use the same source.

An unloaded target is retained as unavailable, without forcing a chunk load. A loaded, confirmed removed or ineligible target is invalid; Automatic may find another eligible source. The retained advanced commands support **Locked** and **Disconnected** modes. Old pre-version-2 routing settings migrate to Automatic while preserving a usable recorded target. Modern advanced-command modes remain intentional. [Machine commands](https://github.com/mistaboom/essence_ascendance/wiki/Player-Commands#machine-controls-beta5).

The local fill limit is **aggregate across all six Stored types**, multiplied by the complete effective roster size. Offline members count. All same-pool stations see the same stored shares; another display is not another reserve. Dissolution cannot add beyond that station's local aggregate limit. If membership/structure shrinks, over-capacity stock remains available for withdrawal/channeling/eligible spending; it is not truncated. A larger roster changes fill capacity, not transfer rate.

One physical Crucible supplies one Pylon-derived outgoing budget. Usable player and Infuser demand shares it, subject to receiver limits, required types and output readiness. A unique channel counts once whether it selects one Essence or six. Unused allocations can serve consumers that can use them; an Infuser Focus maximum is a receiver cap, not a guaranteed allocation. More active consumers may slow infusion without creating an exclusive player mode that stops automation.

Additional stations can supply other consumers concurrently from the communal stock. **Each player has at most one active Crucible source across the server.** Building or switching stations cannot accumulate simultaneous player budgets or bypass Pylon progression.

| Player action | Result |
| --- | --- |
| Valid Start at another source | Replaces that player's previous connection |
| Invalid, out-of-range or None-selected Start | Leaves the previous valid connection in place |
| Duplicate Start at the same current source | Retains one channel; adds no share |
| Stop at the old source | Cannot stop a newer source or unrelated Infusers |

## Saved personal receiving selections

The Crucible has six personal receiving toggles plus **All** and **None**. All is the initial selection. The preference is saved per player with a revision and applies to that player's active incoming source; it is not a team-wide filter or administrator recipe rule.

Player operation is explained in **Reference → Machines → Essence Crucible**, with the introductory model in **Guide → Essence → Collecting Essence**. The actual selectors remain in the Crucible's inventory interface, outside the native M popup.

The selection does not change dissolution inputs, Archive Item Yields filters, Infuser requirements, other players' channels or already-owned Available Essence. None or a lack of selected stock can leave an existing channel waiting with no usable demand. Enabling a type can resume an existing valid waiting channel when stock is available, but changing preferences does not start a channel that no longer exists. A fresh Start with None is rejected before replacing the previous source.

## Equipment work and automation

Every new equipment workpiece needs explicit **Start**, including already-soulbound gear. Insertion never binds or starts it. Solo Start has no soulbinding prompt. Unbound team equipment uses **Soulbind equipment? → Soulbind & Start** and a one-use server receipt tied to the actor, live menu, binding/membership and exact workpiece/Focus/components/context. Stale context and replay reject. Existing soulbound ownership never changes when another member upgrades it.

Approved work retains authorization through Matrix/Latent component updates. Recipe readiness and payment checks still apply; workpiece/installed Focus changes disarm or invalidate the applicable context. There is no automation-recipient selector. The machine's funding pool and operator are not a new owner for an already-bound item.

Automation has no general authenticated player actor. The shared insertion guard checks soulbound input ownership against the current receiving pool for vanilla hoppers and both loader adapters. Foreign/departed-owner bound inputs reject; unbound inputs stay unbound. Infuser automation exposes input, output and component slots, with recipe limits; installed Focus is not an automated slot. Crucible automation is insertion-only. Ordinary output extraction follows its exposed-slot rules.

Bounded repair components must fit the recipe capacity. Loader adapters report actual accepted quantities and retain surplus; Fabric transaction abort and NeoForge simulation are covered. An oversized vanilla hopper batch rejects as a whole rather than silently discarding excess. This does not establish compatibility with every third-party pipe or code that bypasses the exposed APIs.

For trapped equipment, the existing own-item recovery command returns only the caller's soulbound input/output, including its supported portable paid-work identity. It grants no general foreign inventory access. Operator machine recovery is restricted to an actually unbound or confirmed dissolved machine, within reach and with ready authority; it is not a way to seize any current team machine. [Recovery commands](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#narrow-machine-recovery-beta5).

Exact Archive amounts, storage, extraction and accounting retain fractions. Item tooltip/JEI whole-number half-up rounding and **<1** for positive sub-unit values are presentation only, not a currency migration.

## Diagnose and recover safely

Check membership before economy generation: [targeted troubleshooting](https://github.com/mistaboom/essence_ascendance/wiki/Troubleshooting#why-does-a-team-or-machine-behave-unexpectedly) distinguishes readiness, authority, authorization, source availability, selected/required types and bandwidth. A normal ownership transition does not require recovery, and rebuilding balance cannot repair an unavailable membership provider.

Back up coordinated world data, inventories/chunks, effective provider data/configuration and matching mod/loader builds. [Persistence and schema limits](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery#multiplayer-persistence-and-upgrades) include the beta.5 save compatibility boundary. Runtime transaction rollback is covered by inherited fixtures; player/chunk cross-file crash atomicity is unjournaled and unproven. No live-world restart, two-client or graphical pass is claimed here.

## Source evidence

Current multiplayer implementation is in the [tagged beta.9 source](https://github.com/mistaboom/essence_ascendance/tree/v1.0.0-beta.9/common/src/main/java/com/mistaboom/essence_ascendance). The paths below are relative to the repository root. The dated [maintenance audit](https://github.com/mistaboom/essence_ascendance/blob/main/docs/wiki-maintenance/AUDIT.md) records historical line references and receipt scope.

- `common/src/main/java/com/mistaboom/essence_ascendance/team/`: `MembershipAuthority`, `MembershipRuntime`, `MembershipProviders`, `MembershipSettlement`, `MembershipActivation`, `NativeMembershipService` and `compat/` adapters.
- `common/src/main/java/com/mistaboom/essence_ascendance/data/`: `ReservoirService`, `EssenceSavedData`, `PlayerEssenceData`.
- `common/src/main/java/com/mistaboom/essence_ascendance/machine/`: `MachineBinding`, `MachineCapacity`, `MachineLink`, `BoundMachine`, `MachineProtection`.
- `common/src/main/java/com/mistaboom/essence_ascendance/crucible/`: `ConcurrentChannelScheduler`, `EssenceCrucibleChannelService`, `CrucibleChannelSelection`, `EssenceCrucibleBlockEntity`.
- `common/src/main/java/com/mistaboom/essence_ascendance/infuser/`: `EssenceInfuserBlockEntity`, `EssenceInfuserMenu`, recipe implementations, `InfusionJobProgress`, `InfusionPayments`.
- `common/src/main/java/com/mistaboom/essence_ascendance/command/`: `EssenceCommands`, `EssenceTeamCommands`, `EssenceMachineCommands`, `EssenceAdminCommands`; `network/TeamInterfaceNetworkService.java` and `network/ServerMenuAccess.java`.
