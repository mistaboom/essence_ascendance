### What this preset aims for

This is a playtest preset for All the Mods 11 (ATM11) 0.14.0-beta, Minecraft 26.1.2 on NeoForge, using Essence Ascendance 1.0.0-beta.17 configuration.

It targets solo play on Normal difficulty with automation expected. Essence Ascendance begins near ordinary early equipment and develops into a full progression path intended to compete with the pack's endgame gear at comparable effort. Investment is heavier overall, with steeper growth later in progression. These are tuning goals, not measured completion times.

The preset covers all 90 Skills and 41 Bonuses, equipment progression, activity-based ascension, payout limits for 6,934 resources, 12 low-value bulk-material entries, and 124 equipment-reference corrections. It limits excessive rewards from decorative conversions and selected renewable outputs while keeping resource automation useful.

The automated audit covered the pack's 298 original mod jars and its supplied and generated configuration files. Installed recipes, progression changes, equipment behavior and relevant mod data were also inspected. The final preset passed an in-game rebuild, reload, saved-profile validation and reopening the same world without restarting Minecraft. Post-reopen validation passed with the saved profile unchanged. All 90 Skill definitions, 41 Bonus tracks and 66 equipment values matched the inputs; all 6,934 resource limits and the 12 bulk-material entries were checked in the generated result. All 148 reviewed decorative conversion families passed the follow-up value check. A complete progression playthrough, boss kill-time testing and testing every possible loadout remain pending.

### Combat and progression choices

Early equipment follows the pack's measured references. Late melee comparisons include the obtainable Allthemodium alloy sword and its dragonforge and Hephaestus Forge progression. Named Allthemodium ore gates remain in place: Essence Ascendance's ordinary harvest levels do not replace the specialized tools required by the pack.

The ranged comparison includes a fully developed Vigor Bow with 40 installed heart canisters. Its reference is 66 precritical impact damage at one fully drawn shot per second; an empty bow does not have that damage. This comparison changes Essence Ascendance's balance inputs, not the Vigor Bow itself.

Essence casters bypass ordinary armor and the usual damage cooldown. Their raw damage is deliberately lower than melee damage. Displayed attack speed is also not a guarantee that every swing damages the same target. These differences are considered in the tuning; actual combat still needs playtesting.

Equipment-reference corrections omit conditional instant-break rules from general mining comparisons and exclude machine tools and extreme mace attack-rate measurements from player-equipment calibration. They do not remove those items, change their native stats or declare them unobtainable.

### Files included

- `config/essence_ascendance.toml` — shared progression, economy and balance settings.
- `config/essence_ascendance/skill_settings.toml` — Skill placement, ranks and effects.
- `config/essence_ascendance/bonus_settings.toml` — Bonus tiers and effect bounds.
- `config/essence_ascendance/balance_overrides.toml` — pack-specific valuation and equipment-reference corrections.
- `kubegroovy/data/essence_ascendance/tags/item/low_value_bulk_materials.json` — the required additive bulk-material tag, loaded by ATM11's KubeGroovy installation.

The download contains editable inputs. It does not include a generated balance profile, reports, worlds, player records or mod jars. Generate the result against the pack you actually run.

### Install in ATM11

1. Install Essence Ascendance beta.17 or later for the matching Minecraft/loader on the server and clients. Beta.17 fixes a world-reopen failure found during this preset's acceptance check. Stop the server or single-player game before replacing configuration files.
2. Extract the download. Copy both its `config` and `kubegroovy` directories into the server's or instance's game directory, alongside `mods`. Merge the directories and replace the four listed Essence Ascendance TOMLs. The game directory may be named `minecraft` or `.minecraft`; use the directory that contains this pack's existing `mods`, `config` and `kubegroovy` folders.
3. Include both directories in a client pack that will also be used for single-player. The companion JSON must be at `kubegroovy/data/essence_ascendance/tags/item/low_value_bulk_materials.json`. ATM11's KubeGroovy supplies the server data loader; this file does not need a separate `pack.mcmeta` or world datapack folder.
4. Start the server or world. As an operator, run `/reload` and wait for it to finish. Resolve any reported KubeGroovy or data-loading errors before continuing.
5. Run `/essence admin balance rebuild` and wait for the installed result. Then run `/essence debug balance validate`. Check the Ascendance Archive, equipment, machine values and bulk-material behavior in game before distributing your pack.

Both folders are required. A restart or `/reload` alone does not replace an existing saved balance profile; the explicit rebuild applies these inputs. Do not substitute a generated profile copied from another installation.

### Adapting it for another pack

This revision targets ATM11 0.14.0-beta and its audited mod list. Later ATM11 releases, ATM10, other loaders and other Minecraft versions require their own review. The overrides name exact item IDs and depend on this pack's recipes, equipment and renewable-resource assumptions.

Review the reference states and resource corrections before reusing them. If the destination pack does not use KubeGroovy, provide the companion tag through its own server datapack-loading mechanism. Rebuild against the destination mod list, then test early affordability, automation, progression and combat. Other presets can coexist in the downloads catalog; install only the inputs intended for the pack you are running.

### Revision history

- `0.14.0-beta-r1` — Initial public ATM11 playtest preset, with a complete Skill and Bonus tuning pass, resource corrections and required KubeGroovy bulk-material data. Native rebuild and saved-profile validation passed.

### Reuse

These preset files use the project's MIT license, included as `LICENSE.txt`. You may adapt and redistribute them in your modpack while retaining that notice.
