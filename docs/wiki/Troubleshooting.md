# Troubleshooting

[Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) · [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance) · [Recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery)

## Start here

| Symptom | Check |
| --- | --- |
| No yields, all Skills unaffordable, or equipment stuck at minimum stats | `/essence debug mappings status` and `/essence debug balance summary`; valid balance may not have installed |
| Archive keeps showing loading | Matching client/server versions, installed balance and client synchronization |
| Edited setting has no effect | Whether `/essence admin balance rebuild` succeeded |
| Global power changed but one Skill/Bonus did not | Its explicit values in `skill_settings.toml` / `bonus_settings.toml` |
| Item payout seems wrong | Exact item query, `valuation.csv`, recipe quantities and conversion limits |
| Reports are missing or stale | `diagnostics/report_manifest.json`, then export again if needed |
| Team or machine access fails | [Membership and machine status](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure), rather than repeated balance rebuilds |

Diagnostic and balance commands require operator permission level 2. Use the [command reference](https://github.com/mistaboom/essence_ascendance/wiki/Commands) for complete syntax.

## Why did restarting not apply my config change?

The server reuses its saved balance. After editing a balance input, use `/essence admin balance rebuild`. Datapack reload, mappings reload and report export do not recalculate an existing profile. There is no `/essence admin config reload` command in beta.9.

Make sure you edited the server instance's files. For single-player, use the instance running that world.

## Why did changing global power leave Skills or Bonuses the same?

Their generated settings files contain explicit values and are preserved. Use those files to tune individual effects. To request automatic values under the new global policy, remove the relevant Skill controls or complete Bonus entry before rebuilding. [Full reset-to-automatic instructions](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation#automatic-settings-versus-saved-choices).

## Why is a Skill or Bonus setting rejected?

Read the error's file/line and effect name. Common causes include duplicate entries, misspelled keys, an unknown ID/path, invalid tier order, too many ranks for a small improvement, or a value outside that effect's limits.

For a one-rank Skill, configured first and final values must match. An on/off Skill must keep one rank. Bonus entries need all four controls, with a positive first minimum no larger than the maximum. Bonus intermediate tiers also need useful improvements.

Check the generated unit comments: 10 percent and a fraction of 0.1 are different ways of writing the same proportion for different fields. Keep the format required by that effect.

## Why are the Skill or Bonus files missing?

They are written after valid balance is installed. Check generation or file-write errors first. A missing file can also be recreated from saved balance at startup, which restores those saved values rather than generating new ones.

For fresh automatic defaults, back up and move the file aside while the test server is running, then explicitly rebuild before restarting. For a smaller change, remove just the relevant controls or entries and rebuild.

## Why is a useful item worth little or no Essence?

The generator does not know every use, quest gate or farming difficulty. It estimates exposed properties and recipes. A cheap recognized recipe, multiple outputs, reversible conversion or whole-unit rounding can limit actual payouts. It does not automatically make an item worthless just because it can be automated.

Compare the actual payout with the estimate in `valuation.csv`, and use a supported [advanced correction](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization) if needed. A material estimate does not guarantee a particular payout because recipe checks still apply.

## Why does an item query reject my ID?

Use the full saved identifier in quotes:

```mcfunction
/essence debug balance item "minecraft:iron_ingot"
```

Substitute the affected item's full ID. If it is absent from the saved result, check whether you rebuilt after adding the item or changing recipes.

## Why is a granted Skill ineffective?

Granting a rank does not remove tier requirements, prerequisite Skills, milestones, exclusive choices, replacements or runtime conditions. Compare the Archive's requirements with the player's Skill status. Use [player diagnostics](https://github.com/mistaboom/essence_ascendance/wiki/Diagnostics-and-Testing) when necessary.

## Does Ascension spend Essence?

No. Player Ascension uses Category Attunement seals. Skill/Bonus development can speed future activity progress; wallet balances and equipment are not a payment to ascend. Point players to the Archive for the gameplay steps.

## Will rebuilding add ore to old chunks?

No. It changes supported future generation, without filling already generated chunks. Ore placement is random, so one short mining trip is not a reliable absence test. Check new terrain, the configured dimensions/heights and `latent_ore_worldgen.csv`.

An unsupported terrain generator can be skipped with an explanation. Exact host names do not make a void dimension contain mineable rock. [Ore settings](https://github.com/mistaboom/essence_ascendance/wiki/Configuration#latent-ore).

## JEI disappeared, or popup spacing is wrong

Beta.8 fixed a Fabric JEI startup failure in the sorting integration and makes machine popups move only the JEI area on their actual side. Restart the client after updating code; an already running development client keeps its old classes.

Confirm JEI is installed, its normal overlay is enabled, and enough screen space exists for its columns. If the issue persists, include JEI version, loader, GUI scale, window size and the relevant client error log when reporting it.

## The rebuild reported a report or client-sync error. Did it roll back?

Not necessarily. Failure before installation retains an existing valid balance, but a report/sync failure afterward can leave the new balance active. Check summary and saved validation. Fix the write problem and export again, or reconnect an affected client.

## What if the saved profile is corrupt or incompatible?

Preserve it and its error. Restore a compatible backup or deliberately rebuild after fixing the environment/settings. Do not hand-edit the compressed file or use player resets as a balance repair. Older beta formats are not migrated; see [recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery).

## Why does a team or machine behave unexpectedly?

Check `/essence admin membership status`, then the player's `/essence team status` and the appropriate machine status. Provider readiness, actual ownership, range, loaded chunks, selected Essence types and explicit Start are separate from balance generation.

Use [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure) for pack/server configuration, and the Archive for machine operation. Do not switch providers or delete balance as a generic machine fix.

## Why is generation slow?

Fresh generation and rebuilds read the pack's data, solve recipe estimates, generate progression, validate and write the saved result/reports. Larger packs can take longer and can pause the server. Ordinary restart reuses the result; detailed report export can also take time.

No single startup time or memory setting fits every pack. Use the timings in your own log when investigating an unusual delay.

## Report a useful issue

Include Minecraft, loader, Essence Ascendance and relevant integration versions; your reproduction; the affected setting/item; and the relevant error or warning. Add a screenshot for layout problems.

Remove credentials and private player/server details before sharing logs. [GitHub Issues](https://github.com/mistaboom/essence_ascendance/issues).
