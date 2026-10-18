# Version information

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Installation](https://github.com/mistaboom/essence_ascendance/wiki/Installation-and-Server-Setup) · [Updating and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery)

## Documented scope

| Reference | Version |
| --- | --- |
| This wiki's balance/configuration guide | **1.0.0-beta.9**, refreshed October 18, 2026 |
| Minecraft / Java | **1.21.1 / 21** |
| Loaders | Fabric and NeoForge |
| Release notes and downloads | [Beta.9](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.9) |

Use matching mod versions on the server and clients. The wiki describes the published beta.9 balance system; unreleased work in the repository may differ.

## Updating from earlier betas

When moving from beta.7 or earlier, the beta.8 requirement for fresh worlds and fresh Essence Ascendance configuration still applies. Beta.9 introduces no additional saved-format break over beta.8; back up existing installations before updating. The balance overhaul, item identifiers and generated Skill/Bonus settings are not migrated. Keep complete backups and the matching older installation if you want to continue older worlds.

The current editable system files are `skill_settings.toml` and `bonus_settings.toml`. The old `skill_layout.toml` and `bonus_progression.toml` names are not read or converted.

## Added in beta.9

The Ascendance Glaive is a normal six-tier two-handed weapon with separate swipe/stab actions, passive-offhand policy, authored extending shaft and eligible personalized presentation. The Archive adds its Guide lesson and live Reference/action details, plus navigation for sections containing multiple lessons. The pack-maker wiki has been refreshed and reorganized. Search the Archive for **glaive**, **swipe**, **stab** or **offhand** for gameplay instructions.

## Evidence and test claims

Beta.9 passes the full common regression suite, both loader builds and native fixtures for combat, policy, presentation, progression and shared systems. Real multiplayer visuals, GPU performance and full saved-world recovery remain manual checks. The prior beta.8 balance release passed its GitHub checks and both loader builds. Isolated vanilla and normal ATM10 checks exercised balance generation, explicit rebuild, emitted settings and saved-profile restart. JEI hook checks covered 19.53 on Fabric and 19.54/19.57 on NeoForge.

Those checks do not mean every item value, farm, hidden upgrade or combat combination is correct in every pack. Use the generated defaults as a starting point, then test and tune the systems that matter to your pack. Current values belong in the Archive and generated reports, not universal wiki stat tables.

## Keeping the guide current

The maintained pages live in the repository's `docs/wiki/` folder. GitHub's live wiki is a separate publication; a normal repository push alone does not update it. Maintainers can follow the [wiki publishing instructions](https://github.com/mistaboom/essence_ascendance/blob/main/docs/wiki-maintenance/PUBLISHING.md).

For a pack problem, include your mod/loader version, the relevant settings, what you expected and what happened. [Report an issue](https://github.com/mistaboom/essence_ascendance/issues).
