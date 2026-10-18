# Balance generation and saved profiles

[Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) · [Backups and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery)

## First setup

Use a fresh test world with the mods and recipes you plan to ship. The server generates balance during world startup if no saved balance exists. Single-player uses its integrated server in the same way.

After a successful first generation, you should have these editable files:

| File | Purpose |
| --- | --- |
| `config/essence_ascendance.toml` | Overall power, costs, activity pace, ore and other shared settings |
| `config/essence_ascendance/skill_settings.toml` | Skill unlock tiers, ranks and effects |
| `config/essence_ascendance/bonus_settings.toml` | Bonus starting/completion tiers and effect bounds |
| `config/essence_ascendance/balance_overrides.toml` | Advanced corrections for items, equipment and recipes |

Skill and Bonus files are created from a successfully generated result. If generation fails, fix the reported problem first; an absent settings file is not a reason to copy one from an unrelated pack.

## Why settings do not change on every restart

`config/essence_ascendance/generated_balance.json.gz` stores the balance the server actually uses. This keeps values stable between sessions. The editable files are instructions for the next generation; reports are summaries of the installed result.

| Action | What happens |
| --- | --- |
| Restart with a valid saved balance | Loads that balance |
| Edit a configuration file | Saves your requested change; does not apply it yet |
| Change mods, recipes or datapacks | Does not automatically replace saved balance |
| `/essence admin balance rebuild` | Generates, checks and installs a replacement using the current pack and settings |
| `/essence admin mappings reload` | Reloads saved balance; does not recalculate an existing profile |
| `/essence admin balance export` | Recreates reports without recalculating balance |
| `/essence debug balance validate` | Checks the saved balance and its agreement with the running server |

These commands require operator permission level 2. There is no `/essence admin config reload` command in beta.9; `/essence admin config` displays information only.

## Applying your edits

Back up the world and the whole Essence Ascendance configuration first. Apply substantial pack changes in a test copy before a live server.

```mcfunction
/essence admin balance rebuild
/essence debug balance summary
/essence debug balance validate
```

A rebuild can pause the server while it works. Wait for the result and check the Archive/Nexus on a matching client. The commands inspect and apply server balance; clients do not each need to rebuild it.

If a rebuild fails before installing the replacement, an existing valid balance stays active. Read the file/line or setting named in the error. Do not assume an edit worked merely because the file saved successfully.

If installation succeeds but report writing or client synchronization fails, the new balance can already be active. Check the summary, fix the reported problem, then export reports again or reconnect the affected client as appropriate.

## Automatic settings versus saved choices

Existing Skill and Bonus settings files are never overwritten by a rebuild. Their generated defaults become explicit choices once written. Raising global power therefore does not necessarily raise a Skill or Bonus whose effect values are already set in those files.

To let one Skill regenerate its rank count/effects, remove its `ranks` line and its complete `[[skill.stat]]` blocks, keeping its `id` and any desired `tier`. To let one Bonus regenerate completely, remove its whole `[[bonus]]` entry. Then rebuild. Removing a whole Skill entry restores the default unlock tier as well as automatic ranks/effects.

For a completely new set of automatic Skill or Bonus defaults, keep the test server running, back up that settings file, move it out of the configuration folder, and explicitly rebuild before restarting. A missing file is recreated from the newly installed result.

If you remove the file while stopped and then start with an existing saved profile, the mod can recreate it from that old saved balance. That restores the old choices rather than generating new values for your changed pack/settings. Move it aside again while the server is running and rebuild if you intended fresh automatic defaults.

Do not edit `generated_balance.json.gz` or use a report as a configuration file. For restoring earlier balance, use [a matching backup](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery).
