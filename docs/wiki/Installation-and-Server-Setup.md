# Installation and server setup

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [New pack walkthrough](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios#starting-a-new-modpack)

## Choose the right build

Choose the intended build from [GitHub releases](https://github.com/mistaboom/essence_ascendance/releases). Use its Fabric or NeoForge jar for **Minecraft 1.21.1** with **Java 21**, and install matching versions on the server and all clients. These source documents can describe development changes beyond a published release; check that release's notes.

**The current simple-generator development revision requires fresh saves, worlds and configs.** Old profile/item/machine metadata and old Skill/Bonus schemas are not migrated. Keep a complete backup and the matching older build to continue historical worlds.

| Loader | Required components on client and server | Build reference |
| --- | --- | --- |
| Fabric | Fabric Loader **0.19.3 or newer**, Fabric API for 1.21.1, Architectury API **13.0.11 or newer** for 1.21.1 | Fabric API `0.116.15+1.21.1` |
| NeoForge | NeoForge **21.1**, Architectury API **13.0.11 or newer** for 1.21.1 | NeoForge `21.1.248` |

Install **Essence Ascendance and required dependencies on both sides**. Match the server's Minecraft version, loader and mod version on clients. Single-player still has an integrated server and follows the same balance lifecycle.

Use one loader build. Fabric and NeoForge jars are alternatives, not two required components. The permissive NeoForge metadata range is not evidence of support for later Minecraft releases.

## Optional integrations

JEI is optional and supplies recipe/display integration; the build uses the `19.43.0.395` API. It is not required to generate a balance profile. This source does not advertise REI or EMI integration.

Other mods contribute ordinary loaded recipes, common tags and native properties. The generator does not run mod-specific acquisition/capability adapters, quests or script inspection. Functional membership integration is separate from balance generation.

## Prepare a server or pack

Native solo/team authority needs no external mod. Optional FTB Teams membership and JEI are functional integrations; they do not establish acquisition facts for the generator. Install matching dependencies for your chosen build. Older provider/version audit tables describe their dated releases, not a new compatibility guarantee.

Fresh AUTO selection and an established world's retained authority differ. Adding a provider to a running pack is not an automatic authority migration. Review [selection](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#selection-and-established-worlds) and the [beta.5 save compatibility boundary](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery#multiplayer-persistence-and-upgrades) before testing against copied world state.

1. Assemble the intended mods, datapacks, world-generation settings and supported integration configs in a staging environment.
2. Start the server normally. Missing global inputs receive defaults. With no saved profile, generation runs early in Overworld loading; complete Skill and Bonus settings are emitted only after validated durable publication.
3. Review installation status, profile summary and reports. A new large pack can require substantial synchronous generation time; no fixed startup duration is promised.
4. Connect a matching client and check the Archive after synchronization.
5. Try representative survival acquisition and progression routes before distributing the pack.

See [balance generation](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation) for failure behavior. A server can continue loading after an initial profile rejection, but that does not mean the balance systems have valid authority.

## Server files and distribution

Editable inputs:

```text
config/essence_ascendance.toml
config/essence_ascendance/balance_overrides.toml
config/essence_ascendance/skill_settings.toml
config/essence_ascendance/bonus_settings.toml
```

The saved server authority is `config/essence_ascendance/generated_balance.json.gz`. Reports and diagnostics beneath that directory are derived views.

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

Current packaging references: `gradle.properties`, `fabric/src/main/resources/fabric.mod.json` and `neoforge/src/main/resources/META-INF/neoforge.mods.toml`. Release-specific dependency requirements come from the chosen artifact.
