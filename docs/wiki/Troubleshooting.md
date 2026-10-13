# Troubleshooting and frequently asked questions

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Diagnostics](https://github.com/mistaboom/essence_ascendance/wiki/Diagnostics-and-Testing) · [Recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery)

## Start with the right layer

| Symptom | First check | Likely distinction |
| --- | --- | --- |
| Commands/items unavailable | Installed mod, loader, MC and dependencies | Installation before balance |
| Archive values loading | Matching client version and synchronized context | Loading is not zero |
| Dissolution/Ascension unavailable | `debug mappings status` and `debug balance summary` | World loaded does not imply valid authority |
| Edited config appears ignored | Existing saved profile | Inputs apply on explicit rebuild |
| Unexpected item yield | `debug balance item` with quoted full ID | Opportunity value differs from extraction |
| Unexpected Skill gate/effect | Saved curve, availability report and player Skill view | Catalog/candidate/final tier and ownership/effectiveness differ |
| Stale/missing reports | Manifest integrity/state | Export failure can follow successful install |
| Machine seems disconnected | Correct looked-at machine diagnostic | Link/owner/Focus/reservoir context, not just price |

See the [full syntax](https://github.com/mistaboom/essence_ascendance/wiki/Commands) before using command fragments. Administrative diagnostics require permission 2.

## Teams and machines: beta.5

These checks apply to beta.5 multiplayer. Start with **`/essence admin membership status`** (level 2), then the authorized player's **`/essence team status`** and **`/essence machine <machine> status`**. Machine commands need a loaded target within normal menu reach. The operator ray-targeted `debug machine` commands remain useful for structure/Focus/recipe state and can refresh links/caches. None of these observations requires an economy rebuild.

| Symptom | Check in order | Appropriate next step |
| --- | --- | --- |
| External provider installed, access unavailable | Established authority; exact provider/Library versions; readiness and complete roster evidence | Correct the actual installation/readiness issue. Missing/unsupported/initializing does not mean empty teams or solo access. Use provider-change commands only for an intentional authority change. |
| Native M is missing | FTB Teams/OpenPAC installation, then saved authority/status | Installation hides M even in a still-native established world. Follow provider-aware guidance and the explicit prepare/status/activate flow if changing authority; do not assume the world already switched. |
| FTB party exists but Crucible stays personal | Exact Teams/Library gate, then established authority in membership status | Current source accepts Teams 2101.1.11 with Library 2101.1.30, 2101.1.36 or 2101.1.37. Restart after updating code. An existing native world needs the reviewed `prepare ftb_teams`, `status`, `activate <token>` flow; installation or breaking/replacing before activation cannot switch authority. Existing bindings from another authority are not automatically remapped; test a new physical placement after activation. |
| Activation says the preparation expired | Remaining review time in membership status | Prepare the target again, wait for settled status, then use its current clickable activation suggestion. The current window is 6,000 server ticks (about 5 minutes); older development builds allowed only 1,200 ticks (about a minute). Expiry retains the established authority. |
| Nearby machine denies inventory/settings | Player's effective pool; machine binding; ready membership; exact current block/reach | Proximity is not authorization. Normal personal machinery converts on create/join, including unloaded history; no ownership-adoption step is required. |
| Infuser/Pylon stays on a different source | Automatic versus Locked/Disconnected; source pool, dimension, range and loaded state | Valid Automatic targets remain stable. A Locked target will not switch just because a nearer station appears. Use the documented advanced link command only after inspection. |
| Source appears missing | Chunk loaded versus loaded-and-confirmed removed | Unloaded source waits without forced loading. Automatic may replace a confirmed invalid/removed source; an unloaded source is not proof of removal. |
| Infusion slowed or stopped | Processing/explicit Start; installed Focus; linked source; required types; output space; active consumers | Shared bandwidth can reduce a usable receiver's grant without stopping every Infuser. Missing required types and blocked output are different waits. A Focus maximum is not guaranteed throughput. |
| Player receives nothing | None or unavailable selected types; channel Start; range; actual source; authorization | Re-enable needed types and stock. A still-valid waiting channel can resume; changed preferences do not create a new channel. Invalid/None/out-of-range Start elsewhere preserves the old connection; Stop there does not stop a newer source. |
| Stored exceeds current capacity | Aggregate local fill limit and current roster/structure | Preserve stock. Channel/withdraw/spend eligible Stored; additions wait until room exists. Member-scaled capacity does not increase transfer rate. |
| Personal machine did not appear to follow a join | Current owner's ready membership and loaded ownership-history refresh | Allow the normal authoritative lifecycle to resolve. Old placer/provenance text alone is not authority. An actually unbound legacy machine can use narrow operator recovery after backup/status review. |
| Equipment recipient invalid or workpiece trapped | Existing soulbound owner; current receiving pool; input/Focus context; paid work | Automated foreign-bound insertion is rejected. Unbound team equipment needs GUI confirmation and every new workpiece needs Start. An item's owner may use narrow `recover-item input/output`; that grants no foreign inventory access or refund. |
| Repair components do not all transfer | Required recipe slot cap and remaining room | Surplus remains with sender. Loader adapters can partially accept; oversized vanilla batches reject whole. Do not add a fictitious port-toggle workaround. |

Some operator debug text retains old labels such as `cap/family` and `player-owned`. Interpret capacity through the current aggregate limit/membership implementation, not as a new per-Essence allowance or a claim that every reservoir is private. Debug text is not the authority for storage rules.

Avoid player resets, provider switching, deleting data/profiles or repeated balance rebuilding as generic membership/machine fixes. [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure) supplies the canonical permission, linking and accounting rules; [coordinated recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery#multiplayer-persistence-and-upgrades) explains backups and schema limits.

## Why did restarting not apply my config change?

Human TOML is generation input. A present profile follows saved reuse. Explicit `/essence admin balance rebuild` applies current policy/evidence after backups and staging. `mappings reload` reinstalls existing saved data; export rewrites views.

Datapack/recipe reload clears relevant caches but does not automatically replace balance. Adding a mod does not trigger an environment-fingerprint rebuild.

## Does a valid profile prove my current pack matches it?

No. Validation checks bytes, structure, revision, required references and modeled invariants. It does not recensus the environment to prove it still matches the original generation. Record environment versions and rebuild deliberately after meaningful changes.

## What if the profile is corrupt or incompatible?

Existing invalid data is rejected; no silent migration or automatic replacement occurs. Preserve the file and diagnose the actual error. Restore a compatible known-good set or fix inputs/environment and explicitly rebuild.

If a previous valid authority is in memory, a rejected candidate retains it. Fresh startup without authority can leave guarded systems unavailable while the world remains playable. General previews and standalone Bonus investment are not reliable readiness checks.

## The rebuild reported a report or client-sync error. Did it roll back?

Not necessarily. Candidate/commit failure preserves old authority, but report/sync errors after successful installation do not undo the new profile. Check installed summary, disk validation and manifest. Re-export reports after fixing write problems; reconnect clients as appropriate.

## Why is a useful item worth zero Essence?

Eligibility, routing, effectively infinite supply, automation pressure or modeled conservation can suppress output. Inspect saved item evidence and warnings before calling it a mapping bug. Unknown evidence and intentional zero are different.

Under balanced/conservative policy explicitly effectively infinite resources get zero generated dissolution. Abundance-aware permits discounted positive proposals, still constrained by conservation.

## Why does an item query reject my ID?

`debug balance item` requires the **exact full saved ID**:

```mcfunction
/essence debug balance item "minecraft:iron_ingot"
```

Use quotes around IDs containing `:` or `/`. Short mod IDs work on many Bonus/Skill/Essence commands, but this saved item lookup does not infer `minecraft:`. If the item is absent from saved evidence, investigate profile age and supported acquisition.

## Why is a granted Skill ineffective?

A grant creates a missing rank-one receipt with zero paid cost. Tier/rank prerequisites, permanent gates, choice selections, replacements and runtime conditions still apply. Activation is not a purchase or a tier bypass.

Compare `debug player skills show`, loadout and milestone state with the Archive and availability/rank reports.

## Why did revoking a milestone report failure?

The mod stored flag may have been removed while its provider still reports completion. That result is not proof no mutation occurred. Vanilla advancement progress belongs to its provider, and reset/revoke does not automatically undo it.

## Why is Attunement not crediting an action?

Credit requires an eligible confirmed outcome, supported attribution and relevant value/health/work change. Duplicate roots, unsupported event attribution or rejected action conditions can prevent credit.

Read category details/history and accepted/rejected reasons. Repetition reduces efficiency but keeps a positive legitimate floor. Credit history measures reference work, not network packets or callback count.

## Does Ascension spend Essence?

No. Current player Ascension uses chapter seals. Bonus/Skill development accelerates future activity; wallet balance, owned Skills and equipment are not payment requirements. Equipment infusion is separate and does consume its generated gameplay costs.

## Can console target a player with every command?

No. Target-enabled Admin/Debug commands need one explicit online target from console; player-only held-item/machine/test commands need a player source. Attunement history requires a page before a target. `@s` in console is not a player.

## Why does a diagnostic appear to change something?

Some views align chapters or refresh ordinary machine links/caches. Gameplay-category diagnostics synchronize equipment attributes and can clamp health to the resolved maximum. These are not grants, purchases or rebuilds. Use staging if preserving transient state matters.

## Where is my Archive?

Check inventory/dropped delivery and whether the first powered-tier gift already occurred. Loss does not rearm it. Craft one Raw Latent Ore + two sticks, or request `admin player guide give`. Do not reset a player merely to get another book.

The Archive's values reflect the **connected server's actual resolved data**. Persistent loading requires installation/authority/synchronization checks; it is not a fixed-default reference.

## Will rebuilding add ore to old chunks?

It changes supported future generation policy. It does not retroactively populate existing chunks. Unsupported terrain generators can be skipped with diagnostics; explicit block names alone do not prove they produce mineable terrain.

## Will every mod work automatically?

Ordinary native evidence offers broad discovery, but explicit adapters have loader/version/scope boundaries. Hidden spell/module/machine/callback behavior can remain unknown. Check [compatibility](https://github.com/mistaboom/essence_ascendance/wiki/Integrations-and-Compatibility), correct supported facts and test representative pack routes.

## Why is generation slow?

Generation is synchronous and includes evidence capture, acquisition solving, compatibility, conservation, curve generation, serialization/validation and export. Saved reuse skips discovery/solving but still performs meaningful read/decode/preparation work. Large exhaustive exports and validation also cost time/memory.

Use telemetry and counts from the actual environment. Synthetic fixture timings or file bounds are not universal performance promises.

## Report a useful issue

Use [GitHub Issues](https://github.com/mistaboom/essence_ascendance/issues). Include version/loader/MC, relevant integration versions, reproduction, active integrity, report state and bounded sanitized error/warning rows.

Do not upload credentials, raw personal logs, private absolute paths, player identifiers or complete world/player data. [Version reference](https://github.com/mistaboom/essence_ascendance/wiki/Version-and-Maintenance).
