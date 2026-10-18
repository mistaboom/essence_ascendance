# Integrations and compatibility

[Installation](https://github.com/mistaboom/essence_ascendance/wiki/Installation-and-Server-Setup) · [Pack balance](https://github.com/mistaboom/essence_ascendance/wiki/Procedural-Balance) · [Teams](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure)

## Other mods and item balance

The balance generator uses shared item properties, common tags, ordinary loaded recipes and exposed equipment/enemy stats. It does not need a separate price adapter for each content mod.

It does not inspect quest progress, starter-island definitions, bespoke KubeJS code, custom farming rates or hidden upgrade systems. A mod being installed is not a promise that all its mechanics are reflected in generated balance. Test important items and use the editable Skill/Bonus settings or advanced overrides where the starting values do not suit your pack.

Recipes added by datapacks or KubeJS can participate when they appear through the ordinary supported recipe interfaces. Dynamic callbacks or incomplete recipe displays may provide less usable information. Reloading recipes does not rebuild saved balance; use `/essence admin balance rebuild` after changes.

The old wiki's content-mod adapter and quest-evidence tables describe a retired balance system. They are not installation requirements or current support lists.

## JEI

JEI is optional on both loaders. It provides recipe browsing and displays Ascendance equipment and Latent Ore variants. It is not needed to generate balance, and the mod does not advertise a dedicated REI or EMI integration.

Beta.9 fixes an internal sorting hook that could prevent JEI from starting on Fabric. That hook was checked with JEI **19.53 on Fabric** and **19.54/19.57 on NeoForge**. These checks cover the specific integration paths, not every future JEI version. If the optional late-variant sorting hook is unavailable, it can leave JEI's normal ordering in place.

Info, Settings and membership popups reserve space on their actual side of the machine screen. They should not move the opposite JEI column.

## FTB Teams and shared Essence

Membership integration controls shared Stored Essence and machine access. It does not influence item valuation through quests.

Beta.9 supports native solo/teams without another mod, and the functional FTB Teams integration described in [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#membership-providers). Its checked combinations are Teams **2101.1.11** with Library **2101.1.30, 2101.1.36 or 2101.1.37**, on Fabric or NeoForge for Minecraft 1.21.1. Use the required dependencies for those builds; an arbitrary newer version is not automatically covered.

Choose the provider before creating your pack's world. Adding or replacing a provider in an existing world does not automatically change its saved membership authority or reassign every machine. Follow the [authority change procedure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure#deliberately-changing-authority) if you intend that change.

FTB Quests can remain in your pack, but its definitions and rewards are not used to infer item access or Skill placement. No quest-reading configuration is needed.

## Reporting a pack issue

Include the mod version, Minecraft/loader version, the affected item or Skill, relevant settings and a short reproduction. Explain what the pack lets players do, especially if it involves a hidden recipe or upgrade. The Archive and [reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance) show the installed values to compare.
