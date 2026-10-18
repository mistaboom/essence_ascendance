# Updating, backups, rebuilding and recovery

[Balance generation](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation) · [Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration)

## Moving from beta.8 to beta.9

Back up the world and configuration, then install matching beta.9 builds on the server and all clients. This update adds the Ascendance Glaive, its normal progression and Archive lessons; it introduces no additional saved-format break or automatic balance/profile migration over beta.8. No new world/profile reset is required by this update. Full saved-world recovery remains a manual integration check.

## Moving to beta.8

Start with fresh worlds and fresh Essence Ascendance configuration when upgrading from earlier betas. Old item identifiers, saved balance and Skill/Bonus configuration formats are not migrated. Keep the older world, settings and matching mod installation together if you want to continue that save.

This is different from tuning an existing beta.8 installation. Supported settings changes can be applied with a balance rebuild; they do not normally require starting a new world.

## Keep a recoverable set

Back up these together, preferably with the server stopped or through a consistent backup system:

| Content | Reason |
| --- | --- |
| Complete world, including player and machine data | Progression, inventories, shared Essence and world state |
| `config/essence_ascendance.toml` | Global balance choices |
| `config/essence_ascendance/skill_settings.toml` and `bonus_settings.toml` | Skill and Bonus choices |
| `config/essence_ascendance/balance_overrides.toml` | Advanced corrections |
| `config/essence_ascendance/generated_balance.json.gz` | Exact installed balance |
| Relevant reports, logs and mod/version list | Comparing changes and reproducing the setup |
| External team/provider data and configuration | Membership state, if you use an external provider |

Copying individual files while the world is changing can produce a mismatched backup. Do not distribute a test world's player data or logs as pack configuration.

## Multiplayer persistence and upgrades

Shared Essence, membership, machine bindings and paid work belong with the complete world backup. Restoring only a machine chunk or only a player/team balance can mix different points in that history.

Choose your team provider before creating a world. An intentional provider change in an existing world has a separate [membership procedure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#deliberately-changing-authority); a balance rebuild does not perform that migration. Old-provider machine bindings are not automatically reassigned to the new provider.

Use matching mod versions on all clients and the server. The earlier beta.5 multiplayer change also required a new world; do not try to force an old save to load by editing version fields or selectively deleting progression data.

## Before changing a live pack

1. Make a complete backup and keep the old version list and settings.
2. Apply the intended mod, recipe or configuration changes to a test copy.
3. Rebuild there, inspect the Archive and reports, and test representative players and equipment.
4. Schedule a maintenance window for the live rebuild.
5. Confirm that a later restart reuses the new saved balance.

## Explicit rebuild procedure

After saving your configuration edits:

```mcfunction
/essence admin balance rebuild
/essence debug balance summary
/essence debug balance validate
```

These require operator permission level 2 and work from the server console. A rebuild replaces the server's balance and can pause the server while it runs. Restarting or reloading datapacks alone does not apply the edits.

Use `/essence admin balance export` if you also need to recreate detailed reports. Export does not run a second balance generation.

If you want new automatic Skill or Bonus values, remember that existing settings are preserved. Remove the relevant overrides first; [instructions](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation#automatic-settings-versus-saved-choices).

## What happens to existing progression?

Within a supported balance update, already earned same-chapter seal progress is retained, while future activity uses the new targets. Stored Bonus investment is retained, with current caps controlling its effective benefit. Skill purchases retain their recorded historical costs for supported refunds; changed prices do not rewrite those past purchases.

New rank counts, prerequisites, caps and exclusive choices can affect which owned Skills are effective. Test important existing builds before changing a live server. Removing arbitrary mods or registered items can also make a saved profile incompatible.

## Recover from a rejected candidate

If an existing valid balance is installed and the replacement fails before installation, the previous balance remains active. Read the named configuration file, line or effect in the error; correct the request and rebuild again in your test copy.

If there is no valid balance at startup, balance-dependent features can remain unavailable even though the world opens. Check `/essence debug mappings status` and `/essence debug balance summary`. Grants, resets and wallet edits do not repair missing balance.

A report-write or synchronization error after installation is different: new balance can already be active. Check the summary and saved validation, then fix report permissions/export again or reconnect the affected client.

## Restore a known profile

For a full rollback, stop the server and restore the matching complete world/configuration/version set. A saved balance file alone does not reverse later purchases, inventory changes or world activity.

For a deliberate balance-only restore, use a compatible known-good saved profile with its matching settings and pack environment, then check summary, validation and Archive values after starting. Do not copy balance from an unrelated pack or hand-edit the compressed file.

Invalid existing profiles are rejected rather than silently replaced. Preserve the original and its error before choosing a compatible restore or an intentional rebuild. Deleting saved balance is not a routine repair step: it loses the old result and makes the next load generate anew.

## Recover a player's Archive

Use `/essence admin player guide give` in game, or provide an online player name from console. Players can also craft a replacement. [Archive guidance](https://github.com/mistaboom/essence_ascendance/wiki/Players-and-the-Archive).
