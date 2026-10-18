# Updating, backups, rebuilding and recovery

[Balance lifecycle](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation) · [Administration commands](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands)

A pack update can change prices, measured catalog references, ranks or activity calibration. Plan that change; the saved-profile system deliberately avoids automatic recalibration on every restart.

## Keep a recoverable set

Back up the following together with the installed mod/loader/MC version list:

| Content | Why |
| --- | --- |
| `config/essence_ascendance.toml` | Global policy |
| `config/essence_ascendance/skill_settings.toml` and `bonus_settings.toml` | Editable Skill ranks/stats/unlocks and Bonus bounds |
| `config/essence_ascendance/balance_overrides.toml` | Advanced measurement and exact-value corrections |
| `config/essence_ascendance/generated_balance.json.gz` | Exact old server authority |
| Reports/diagnostics you need | Before/after reasoning and integrity; derived, not substitutes for the profile |
| Complete world backup | Player progression, inventories, entities, machines and world state |
| World player/fractional SavedData | Included in complete world backup; important when auditing restore completeness |

Relevant Overworld files are `<world>/data/essence_ascendance_players.dat` and `<world>/data/essence_ascendance_fractional_accounts.dat`. Here `<world>` means the server's actual world directory. Do not publish those files as public balance evidence.

Use a stopped server or your backup system's consistent snapshot procedure. Copying changing world files independently can produce an inconsistent backup.

The current simple-generator development revision requires fresh saves, worlds and configs when replacing the older generation/metadata formats. It does not migrate old Skill/Bonus filenames or schemas. Preserve old inputs with their matching build; see [the current model](https://github.com/mistaboom/essence_ascendance/blob/main/docs/simple-balance.md).

## Historical beta.5 multiplayer persistence boundary

**Beta.5 persistence:** shared Stored shares, reservoir fractional carry/remainder cursors, settled roster/departure/dissolution/ownership receipts, native names/requests, saved authority/solo identities and player receiving selections live in **`<world>/data/essence_ascendance_players.dat`**. This is one authoritative SavedData file, not a separate team-bank file. The fractional-accounts file above continues to hold other non-reservoir accounting. Machine bindings, links, inventory and paid-job state also live in chunk/block-entity data; portable jobs and inventory must remain coordinated with their world/player state. External providers persist their own party/configuration state, which belongs in the same complete backup.

Use a stopped server or a consistent complete snapshot including world chunks, inventories, both EA SavedData files, provider state, configuration and matching builds. Restoring only a pool record or machine chunk can mix financial and custody revisions. Runtime transaction rollback has inherited fixture coverage, but **cross-file player/chunk crash atomicity has no journal and is unproven**. No process restart or live two-client backup/restore pass is claimed.

**Start a new world when upgrading from beta.4 or earlier to beta.5.** Beta.5 expects EA player **schema 10**, while beta.4 uses **schema 6**. The new multiplayer accounting, membership and ownership format is incompatible with older EA saves; the loader rejects them and no migration is provided. Keep the previous complete world/build set to continue older progress. Do not edit the version field, selectively delete player data or regenerate balance to force compatibility.

Personal machinery normally follows create/join, ordinary departures and confirmed final dissolution automatically, including unloaded history. Native ownership-transfer/final-member restrictions and external lifecycle consequences are in [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#machine-ownership-and-permissions). Narrow machine recovery is for actually unbound/confirmed dissolved bindings, not normally bound infrastructure or an automatic old-provider remap after authority activation.

Changing providers is a separately reviewed operation that can share existing Stored and leave existing machine bindings from the old authority unresolved. Follow the [authority change procedure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#deliberately-changing-authority), including its backup, two-observation settlement and one-use token. Routine provider roster changes do not require it. Run matching beta.5 builds on all endpoints; mixed versions are unsupported.

Already-paid work is not Stored and is not a departure refund. Pickup or own-item recovery preserves supported workpiece/provenance/timers through existing custody paths. Breaking a machine follows vanilla/external protection rules and fresh placement establishes new binding/link/provenance; equipment soulbinding remains intact. Do not use theft placement as an administrator access/recovery recommendation.

Current persistence evidence: `data/EssenceSavedData.java`, `data/PlayerEssenceData.java`, `data/ReservoirService.java`, `machine/MachineBinding.java`, `machine/MachineLink.java`, `machine/MachineProtection.java`, `infuser/InfusionJobProgress.java`, under `common/src/main/java/com/mistaboom/essence_ascendance/`.

## Before changing a live pack

1. Record installed profile identity and validate disk/active agreement.
2. Preserve the old configuration/profile and a consistent complete world snapshot.
3. Reproduce the intended mod/datapack/input changes in staging.
4. Rebuild there and inspect changes to yields, costs, meaningful ranks, gates, ore and Attunement.
5. Check representative survival acquisition, client Archive values and restart reuse.
6. Announce any intentional progression changes, then use a production maintenance window.

These are recommended procedures, not acceptance tests claimed to have been run for this wiki.

## Explicit rebuild procedure

After backing up and staging:

```mcfunction
/essence admin balance rebuild
/essence debug balance summary
/essence debug balance validate
/essence admin balance export
```

All require permission 2 and support console. **Rebuild replaces saved/runtime balance and can block the server thread.** Export writes derived reports, including advisory loaded-recipe tables. It does not perform a second generation.

Check install success, saved integrity, report manifest and client synchronization separately. Reconnect an affected client when synchronization has failed. A report failure after install does not mean the old balance is still active.

Restart afterward to verify saved reuse. TOML edits or datapack reload alone do not substitute for this process.

## What happens to existing progression?

Current same-chapter normalized Attunement fractions survive rate/target changes; future credit uses new calibration and development references. A real chapter transition clears chapter-specific tracking.

Stored Bonus investment remains stored; current tier caps limit effective investment. Skill receipts retain historical paid cost/category for supported refunds and acceleration. Current meaningful ranks, tier/requirements, replacements and loadout evaluation determine effectiveness.

This is why repricing is not equivalent to refunding everyone at today's price. Inspect preserved unknown receipts and effective/suspended state where content changed. Do not promise that any arbitrary catalog or mod removal is harmless: required references can reject profiles, and native world content has its own update constraints.

## Recover from a rejected candidate

If a valid profile was already installed, a pre-commit failure preserves it. Diagnose the input line, unsupported references, transport, invariant or storage issue; fix it in staging and retry explicit generation.

On fresh startup with no usable authority, the world can load while guarded systems remain unavailable. Do not mistake provisional config for a repaired server. Keep the rejected profile/failure evidence for diagnosis.

Do not use reset, grants or wallet edits as a fix for missing balance authority.

## Restore a known profile

For an intentional rollback:

1. Stop progression activity and shut down the server.
2. Restore a **compatible known-good** profile and its matching inputs/version environment from your backup.
3. Start the server so it validates and installs saved authority.
4. Check summary, disk validation, manifest and connected-client Archive data.
5. Export current reports if needed.

A live `mappings reload` can reinstall a deliberately restored compatible saved file, but a stopped-server restore is easier to coordinate. A missing file can instead trigger generation, so verify the restore path before using reload.

Profile-only restore does **not** undo player purchases, activity, inventory changes or world generation after the backup. If those must also be rolled back, restore the consistent world/config/version set. Do not overwrite later player progress casually.

## Incompatible or corrupt profiles

There is no automatic migration or silent regeneration of an existing invalid file. Preserve it, inspect the rejection, and choose either compatible restore or deliberate rebuild after fixing inputs/environment.

Deleting authority is not a routine troubleshooting shortcut: it discards the ability to reproduce the old balance and makes the next load take the missing-file generation path. Never copy an unrelated pack's profile as a “fix,” and never hand-edit compressed JSON or failure candidates into live authority.

## Recover a player's Archive

Use `/essence admin player guide give` in game, or supply one online target from console. It delivers another Archive without full reset. Recipe replacement is also available. [Player guide](https://github.com/mistaboom/essence_ascendance/wiki/Players-and-the-Archive).

Source: [saved selection](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/balance/generated/ProfileGenerationSelection.java), [preserved player data](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/data/PlayerEssenceData.java), [Attunement](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/attunement/AttunementService.java).
