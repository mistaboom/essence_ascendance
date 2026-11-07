# Essence Ascendance for pack makers

Essence Ascendance adds character progression, evolving equipment and an Essence-powered machine network to **Minecraft 1.21.1 and 26.1.2**, on **Fabric and NeoForge**. It generates a starting balance from the items, recipes and equipment in your pack, then lets you adjust the results through configuration files.

**Playing the mod? Use the Ascendance Archive in game.** Its lessons and reference values reflect the server you are connected to. This wiki focuses on installing the mod, fitting it into a pack and maintaining the server.

**Published release: [1.0.0-beta.15](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.15).** Choose the jar for your Minecraft version and loader. Each configuration preset lists its own tested pack version and mod configuration version. When moving from beta.7 or earlier, the earlier requirement for fresh worlds and Essence Ascendance configuration still applies.

## Downloads

- [Mod downloads](https://github.com/mistaboom/essence_ascendance/releases) — Essence Ascendance jars for each supported Minecraft version and loader.
- [Configuration presets](https://github.com/mistaboom/essence_ascendance/wiki/Config-Presets) — premade pack tuning, versioned ZIP downloads, installation instructions and testing status.

## Start here

| You want to… | Read |
| --- | --- |
| Add the mod to a pack | [Installation and server setup](https://github.com/mistaboom/essence_ascendance/wiki/Installation-and-Server-Setup) |
| Start with premade pack tuning | [Configuration presets](https://github.com/mistaboom/essence_ascendance/wiki/Config-Presets) |
| Understand what is automatic | [How pack balance works](https://github.com/mistaboom/essence_ascendance/wiki/Procedural-Balance) |
| Choose the right file to edit | [Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) |
| Move a Skill, change its ranks or adjust its effects | [Skill settings](https://github.com/mistaboom/essence_ascendance/wiki/Configuration#skill-settings) |
| Buff or nerf one Bonus, or move its active tiers | [Bonus settings](https://github.com/mistaboom/essence_ascendance/wiki/Configuration#bonus-settings) |
| Follow a complete tuning example | [Worked scenarios](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios) |
| Investigate an item value | [Item valuation and economy](https://github.com/mistaboom/essence_ascendance/wiki/Item-Valuation-and-Economy) |
| Apply changes or recover an earlier setup | [Rebuilding and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery) |
| Configure shared Essence and teams | [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure) |
| Find player instructions | [The Ascendance Archive](https://github.com/mistaboom/essence_ascendance/wiki/Players-and-the-Archive) |

## The balance approach

The generator uses shared item properties, common tags, ordinary recipe data and exposed equipment/enemy stats. It estimates item values and creates equipment progression, Bonus tracks, Skill ranks, costs and activity targets. It does not work out quest gates, farming speed, automation cost or every other mod's special mechanics.

The result is a starting point to test in your pack. Useful but unusual items can be misvalued, and matching equipment stats do not guarantee matching performance in combat. You can tune individual Skills and Bonuses without maintaining a price list for every item.

**Generated balance is saved.** Editing a setting or restarting the server does not replace it. Run `/essence admin balance rebuild` as an operator after making changes, then check the Archive and reports.

## Project links

[Mod downloads](https://github.com/mistaboom/essence_ascendance/releases) · [Configuration presets](https://github.com/mistaboom/essence_ascendance/wiki/Config-Presets) · [Report an issue](https://github.com/mistaboom/essence_ascendance/issues) · [Version information](https://github.com/mistaboom/essence_ascendance/wiki/Version-and-Maintenance)
