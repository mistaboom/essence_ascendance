# Installation and server setup

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [New pack walkthrough](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios#starting-a-new-modpack)

## Choose the right build

Choose the intended build from [GitHub releases](https://github.com/mistaboom/essence_ascendance/releases). Use its Fabric or NeoForge jar for **Minecraft 1.21.1** with **Java 21**, and install matching versions on the server and all clients. This balance/configuration guide targets beta.9; check the release notes when using a different version.

**When updating from beta.7 or earlier, use fresh worlds and fresh Essence Ascendance configuration, as required by beta.8.** Beta.9 introduces no additional saved-format change over beta.8; back up existing beta.8 installations before updating. Old profile/item/machine metadata and old Skill/Bonus schemas are not migrated. Keep a complete backup and the matching older build to continue historical worlds.

| Loader | Required components on client and server | Build reference |
| --- | --- | --- |
| Fabric | Fabric Loader **0.19.3 or newer**, Fabric API for 1.21.1, Architectury API **13.0.11 or newer** for 1.21.1 | Fabric API `0.116.15+1.21.1` |
| NeoForge | NeoForge **21.1**, Architectury API **13.0.11 or newer** for 1.21.1 | NeoForge `21.1.248` |

Install **Essence Ascendance and required dependencies on both sides**. Match the server's Minecraft version, loader and mod version on clients. Single-player still has an integrated server and follows the same balance lifecycle.

Use one loader build. Fabric and NeoForge jars are alternatives, not two required components. The permissive NeoForge metadata range is not evidence of support for later Minecraft releases.

## Optional integrations

JEI is optional for recipe/display integration and is not needed for balance generation. See [compatibility](https://github.com/mistaboom/essence_ascendance/wiki/Integrations-and-Compatibility#jei) for the checked versions. Dedicated REI/EMI integration is not advertised.

Other mods contribute ordinary loaded recipes, common tags and native properties. The generator does not run mod-specific acquisition/capability adapters, quests or script inspection. Functional membership integration is separate from balance generation.

## Prepare a server or pack

Native solo/teams need no external mod. Optional FTB Teams membership and JEI handle teams and recipe browsing; they do not tell the generator how difficult items are to obtain. Install the matching dependencies for your chosen build.

Choose your team provider before creating the world. An existing world keeps its saved provider when another mod is installed. Review [provider selection](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#selection-and-established-worlds) and [multiplayer backups](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery#multiplayer-persistence-and-upgrades) before deliberately changing it.

1. Assemble the intended mods, datapacks, world-generation settings and supported integration configs in a staging environment.
2. Start the server normally. Missing global settings receive defaults. With no saved balance, generation runs during world startup; complete Skill and Bonus files are written after it succeeds.
3. Review the balance summary, Archive and reports. A large pack can take longer to generate; allow it to finish.
4. Connect a matching client and check the Archive after synchronization.
5. Try representative survival acquisition and progression routes before distributing the pack.

If generation fails, check the reported error. A world can open while balance-dependent features remain unavailable. See [balance generation](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation).

## Server files and distribution

Editable inputs:

```text
config/essence_ascendance.toml
config/essence_ascendance/balance_overrides.toml
config/essence_ascendance/skill_settings.toml
config/essence_ascendance/bonus_settings.toml
```

`config/essence_ascendance/generated_balance.json.gz` is the saved balance the server uses. Reports and diagnostics in the same folder describe that result; they are not inputs.

Distribute the four human inputs with pack design after generating and reviewing the Skill/Bonus defaults. A pre-generated profile can be included for a deliberately fixed, matching server environment, but it prevents first-run regeneration merely because content has changed. Treat it as an environment-specific artifact, label the versions it was generated with, and verify it in staging. Do not copy a profile from an unrelated pack.

For client packs, player/world files and personal diagnostic logs are not distributable balance inputs. The server synchronizes runtime and yield data; do not instruct each client to rebuild the server economy.

## First checks

These permission-2 commands are diagnostics:

```mcfunction
/essence debug mappings status
/essence debug balance summary
/essence debug balance validate
```

They report install state, active saved data, and disk/active validation. Validation can be expensive. It does not repair a rejected file or regenerate anything. Use [updating and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery) for planned replacement.
