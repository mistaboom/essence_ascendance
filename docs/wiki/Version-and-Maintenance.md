# Version information

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Installation](https://github.com/mistaboom/essence_ascendance/wiki/Installation-and-Server-Setup) · [Updating and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery)

## Documented scope

| Reference | Version |
| --- | --- |
| Current command guidance, membership behavior and release targets | **1.0.0-beta.17**, refreshed November 7, 2026 |
| This wiki's balance/configuration guide | **1.0.0-beta.9**, refreshed October 18, 2026 |
| Minecraft / Java | **1.21.1 / 21** and **26.1.2 / 25** |
| Loaders | Fabric and NeoForge for each Minecraft version; four separate builds |
| Release notes and downloads | [Releases](https://github.com/mistaboom/essence_ascendance/releases) |

Use the build matching both Minecraft and loader, with matching mod versions on
the server and clients. This pack-maker guide retains its beta.9 balance refresh
and dated evidence; current commands, membership behavior and release targets are updated for beta.17.
Player gameplay instructions remain in the Ascendance Archive.

## Added in beta.17

Fixes an equipment-rule refresh race that could stop a single-player world from
reopening in the same client session, presenting a datapack-loading failure.
Connected players still receive refreshed equipment rules after a data reload.

## Added in beta.16

FTB Teams compatibility now attempts the installed API without an FTB Teams or
FTB Library version allowlist. A compatible provider update needs no version-list
change. Solo players do not need to create a party to use their personal devices.

If the membership API fails or an established provider is removed, Essence
Ascendance switches to its built-in teams while preserving the last confirmed
rosters, ownership history and exact Essence balances. This recovery includes
unloaded devices and persists through restart. Built-in team controls follow the
server's active authority even when FTB Teams remains installed. Normal startup
and ordinary roster changes still wait for confirmed membership; unknown device
ownership is not guessed.

Use `/essence admin membership status` to inspect the active authority and any
fallback reason. Returning to a repaired provider uses the existing explicit
[membership preparation and activation commands](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#membership-authority-beta5).

## Pack-testing commands in beta.14

Local `/essence audio` commands preview a cue or a timed continuous sound on the
requesting client without triggering gameplay. Operators can use
`/essence debug audio play <semantic>` to exercise normal server sound routing.
Minecraft's volume and subtitle settings apply. See [local previews](https://github.com/mistaboom/essence_ascendance/wiki/Player-Commands#local-audio)
and [server diagnostics](https://github.com/mistaboom/essence_ascendance/wiki/Diagnostics-and-Testing#audio-playback)
for exact syntax, permissions and audience limits.

## Updating from earlier betas

Updating from beta.15 or beta.16 within the same Minecraft version requires no world,
player-progress or configuration reset, and no balance rebuild. Update both the
server and clients. Existing settings and generated balance remain in place.

When moving from beta.7 or earlier, the beta.8 requirement for fresh worlds and fresh Essence Ascendance configuration still applies. Beta.9 introduces no additional saved-format break over beta.8; back up existing installations before updating. The balance overhaul, item identifiers and generated Skill/Bonus settings are not migrated. Keep complete backups and the matching older installation if you want to continue older worlds.

The current editable system files are `skill_settings.toml` and `bonus_settings.toml`. The old `skill_layout.toml` and `bonus_progression.toml` names are not read or converted.

## Added in beta.9

The Ascendance Glaive is a normal six-tier two-handed weapon with separate swipe/stab actions, passive-offhand policy, authored extending shaft and eligible personalized presentation. The Archive adds its Guide lesson and live Reference/action details, plus navigation for sections containing multiple lessons. The pack-maker wiki has been refreshed and reorganized. Search the Archive for **glaive**, **swipe**, **stab** or **offhand** for gameplay instructions.

## Evidence and test claims

The membership fix passed the four-target production gate and regression checks
for API failures, solo recovery, exact balances, ownership history and persistent
fallback. Isolated NeoForge 26.1.2 checks exercised FTB absent and the installed
Teams 26.1.2.5 / Library 26.1.2.11 APIs. Deployment to the FTB Evolution 2 server
restored available membership and passed saved-balance validation without
regeneration. These checks do not establish universal compatibility with future
APIs or a complete multiplayer playthrough. [Current verification scope](https://github.com/mistaboom/essence_ascendance/blob/main/docs/VALIDATION.md).

Beta.9 passes the full common regression suite, both loader builds and native fixtures for combat, policy, presentation, progression and shared systems. Real multiplayer visuals, GPU performance and full saved-world recovery remain manual checks. The prior beta.8 balance release passed its GitHub checks and both loader builds. Isolated vanilla and normal ATM10 checks exercised balance generation, explicit rebuild, emitted settings and saved-profile restart. JEI hook checks covered 19.53 on Fabric and 19.54/19.57 on NeoForge.

Those checks do not mean every item value, farm, hidden upgrade or combat combination is correct in every pack. Use the generated defaults as a starting point, then test and tune the systems that matter to your pack. Current values belong in the Archive and generated reports, not universal wiki stat tables.

## Keeping the guide current

The maintained pages live in the repository's `docs/wiki/` folder. GitHub's live wiki is a separate publication; a normal repository push alone does not update it. Maintainers can follow the [wiki publishing instructions](https://github.com/mistaboom/essence_ascendance/blob/main/docs/wiki-maintenance/PUBLISHING.md).

For a pack problem, include your mod/loader version, the relevant settings, what you expected and what happened. [Report an issue](https://github.com/mistaboom/essence_ascendance/issues).
