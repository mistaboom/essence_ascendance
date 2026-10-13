# Installation and server setup

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [New pack walkthrough](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios#starting-a-new-modpack)

## Choose the right build

Download **1.0.0-beta.5** from the [GitHub release](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.5). Choose the Fabric or NeoForge jar for **Minecraft 1.21.1** and use **Java 21**. Install matching beta.5 builds on the server and all clients.

**Beta.5 requires a new world when upgrading from beta.4 or earlier.** The multiplayer save format changes player balances, membership and machine ownership, with no automatic migration. Keep a complete backup and the matching older mod version to continue an existing world. Install your chosen team provider before creating the new world.

| Loader | Required components on client and server | Build reference |
| --- | --- | --- |
| Fabric | Fabric Loader **0.19.3 or newer**, Fabric API for 1.21.1, Architectury API **13.0.11 or newer** for 1.21.1 | Fabric API `0.116.15+1.21.1` |
| NeoForge | NeoForge **21.1**, Architectury API **13.0.11 or newer** for 1.21.1 | NeoForge `21.1.248` |

Install **Essence Ascendance and required dependencies on both sides**. Match the server's Minecraft version, loader and mod version on clients. Single-player still has an integrated server and follows the same balance lifecycle.

Use one loader build. Fabric and NeoForge jars are alternatives, not two required components. The permissive NeoForge metadata range is not evidence of support for later Minecraft releases.

## Optional integrations

JEI is optional and supplies recipe/display integration; the build uses the `19.43.0.395` API. It is not required to generate a balance profile. This source does not advertise REI or EMI integration.

Other installed mods can contribute ordinary loaded recipes and native attributes. Named evidence providers have narrower version/loader checks; most capability adapters audited here target specific NeoForge versions. Read [integrations and compatibility](https://github.com/mistaboom/essence_ascendance/wiki/Integrations-and-Compatibility) before assuming that choosing Fabric gives the same optional evidence coverage. This is separate from the beta.5 membership adapters, which accept their documented Fabric and NeoForge tuples.

## Prepare a server or pack

For optional beta.5 membership support, follow [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#membership-providers). Native solo/team authority needs no external mod. Accepted FTB Teams/Library and OpenPAC loader/version combinations are listed there with dependency metadata and fixture limits. Install the chosen provider/dependencies on both sides; they are not bundled. FTB Quests balance evidence is a different version-gated contract.

Fresh AUTO selection and an established world's retained authority differ. Adding a provider to a running pack is not an automatic authority migration. Review [selection](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#selection-and-established-worlds) and the [beta.5 save compatibility boundary](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery#multiplayer-persistence-and-upgrades) before testing against copied world state.

1. Assemble the intended mods, datapacks, world-generation settings and supported integration configs in a staging environment.
2. Start the server normally. Missing input files are scaffolded; when no saved profile exists, generation occurs early in Overworld loading before initial spawn terrain generation.
3. Review installation status, profile summary and reports. A new large pack can require substantial synchronous generation time; no fixed startup duration is promised.
4. Connect a matching client and check the Archive after synchronization.
5. Try representative survival acquisition and progression routes before distributing the pack.

See [balance generation](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation) for failure behavior. A server can continue loading after an initial profile rejection, but that does not mean the balance systems have valid authority.

## Server files and distribution

Editable inputs:

```text
config/essence_ascendance.toml
config/essence_ascendance/balance_overrides.toml
```

The saved server authority is `config/essence_ascendance/generated_balance.json.gz`. Reports and diagnostics beneath that directory are derived views.

Distribute the two inputs with pack design. A pre-generated profile can be included for a deliberately fixed, matching server environment, but it prevents first-run regeneration merely because content has changed. Treat it as an environment-specific artifact, label the versions it was generated with, and verify it in staging. Do not copy a profile from an unrelated pack.

For client packs, player/world files and personal diagnostic logs are not distributable balance inputs. The server synchronizes runtime and yield data; do not instruct each client to rebuild the server economy.

## First checks

These permission-2 commands are diagnostics:

```mcfunction
/essence debug mappings status
/essence debug balance summary
/essence debug balance validate
```

They report install state, active saved data, and disk/active validation. Validation can be expensive. It does not repair a rejected file or regenerate anything. Use [updating and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery) for planned replacement.

Source: [version properties](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/gradle.properties), [Fabric dependencies](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/fabric/src/main/resources/fabric.mod.json), [NeoForge dependencies](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/neoforge/src/main/resources/META-INF/neoforge.mods.toml).
