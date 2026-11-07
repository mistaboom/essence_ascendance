### What this preset aims for

This is an early playtest preset for FTB Evolution 2 1.5.1, Minecraft 26.1.2 on NeoForge, using Essence Ascendance 1.0.0-beta.15 configuration.

It targets solo play on Normal difficulty with automation expected. Essence Ascendance starts near ordinary early gear and grows into a full progression path that competes with the pack's strong endgame equipment at comparable effort. It retains the pack's recipes and progression.

The tuning covers all 90 Skills and 41 Bonuses, progression pace, equipment durability, and pack-specific conversion/value corrections. In particular, it limits overpriced decorative-material conversions and selected freely renewable outputs while keeping automation useful. The companion datapack adds 11 infinite-cell outputs to Essence Ascendance's low-value bulk-material tag.

Rebuild and saved-profile validation passed, and the resulting settings were checked. Full progression, boss kill times and every possible loadout have not been playtested. Treat this as a starting point for your own testing.

### Files included

- `config/essence_ascendance.toml` — global progression and balance settings.
- `config/essence_ascendance/skill_settings.toml` — Skill placement, ranks and effects.
- `config/essence_ascendance/bonus_settings.toml` — Bonus tiers and effect bounds.
- `config/essence_ascendance/balance_overrides.toml` — corrections specific to this pack's items and recipes.
- `datapacks/essence_ascendance_ftb_evolution_2/` — the required additive bulk-material tag datapack.

The download contains editable inputs, not a saved generated profile, world, player data, mod jars or internal audit files. Generate the result against the pack you actually run.

### Install in FTB Evolution 2

1. Install the matching Minecraft/loader build of Essence Ascendance on the server and clients. Stop the server or single-player game before replacing configuration files.
2. Extract the download. Copy its `config` directory into the server's game directory, or the instance's `.minecraft` directory for single-player. This replaces the four listed Essence Ascendance TOMLs. Include the same inputs in a client pack that will also be used for single-player.
3. Copy its `datapacks/essence_ascendance_ftb_evolution_2` folder into the game directory's `datapacks` directory. Keep it separate from the pack-managed `datapacks/ftb` folder so pack updates do not overwrite this addition. FTB's global datapack loader discovers folders in this directory.
4. Start the server or world. As an operator, run `/reload`, then `/datapack list enabled`. Confirm the Essence Ascendance FTB Evolution 2 datapack is enabled before rebuilding. If it is not listed, use the world-specific installation below and check again.
5. Run `/essence admin balance rebuild` and wait for the installed result. Then run `/essence debug balance validate`. Check the Ascendance Archive and machine values in game before shipping the pack.

A restart alone does not replace an existing saved balance profile. Do not copy a generated profile from a different installation to avoid the rebuild.

### World-specific datapack installation

If the pack has no global datapack loader, copy the same `essence_ascendance_ftb_evolution_2` folder into the world's `datapacks` directory instead. On a dedicated server this is normally `<level-name>/datapacks`; in single-player it is `saves/<world>/datapacks` under the instance's game directory.

Use one datapack location. After `/reload`, check `/datapack list enabled`. If the world-local pack is listed as available but disabled, enable it with `/datapack enable "file/essence_ascendance_ftb_evolution_2"`, then rebuild and validate balance.

### Adapting it for another pack

The Skill and Bonus settings describe a particular power target; the overrides and datapack name exact items from FTB Evolution 2. Review those item IDs, recipes, equipment comparisons and renewable-resource assumptions before using them elsewhere. Rebuild against the new mod list and test progression, automation and combat.

This revision was audited for pack version 1.5.1. It is not a completed 1.7.0 balance pass. Published Essence Ascendance beta.15 also has an FTB Teams version gate that affects newer FTB installations; updated packs need a mod build containing the later FTB compatibility fix. A configuration download does not change that runtime code.

### Revision history

- `1.5.1-r1` — Initial public playtest preset. Preserves the four verified TOMLs and original tag contents; packages the tag as a standalone datapack instead of editing FTB's managed datapack.

### Reuse

These preset files use the project's MIT license, included as `LICENSE.txt`. You may adapt and redistribute them in your modpack while retaining that notice.
