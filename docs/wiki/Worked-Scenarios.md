# Worked scenarios for pack makers

[Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) · [Reports](https://github.com/mistaboom/essence_ascendance/wiki/Reports-and-Provenance)

These examples show how to use beta.9's settings. They are illustrative choices, not recommended values for every pack. Edit the matching entry in your generated file rather than appending a second entry with the same ID. Commands require operator permission level 2.

## Starting a new modpack

1. Install the intended mods, datapacks and recipe changes in a test installation. Use matching Essence Ascendance versions on client and server.
2. Create a fresh world and allow generation to complete.
3. Open the Archive for item yields, equipment values and gameplay instructions. Review the Nexus for generated Skills and Bonuses.
4. Test common early materials, expensive recipe chains and the equipment players will actually use. The generator does not know your quests or farm rates.
5. Tune the four [human configuration files](https://github.com/mistaboom/essence_ascendance/wiki/Configuration#files-scope-and-timing), rebuild and retest.
6. Package the reviewed settings. Normally let the destination server generate its own saved balance; do not include your test world, player data or personal logs.

Use `/essence debug balance summary` and `/essence debug balance validate` to check that valid balance is installed. A pre-generated profile is an advanced option for an intentionally fixed pack; it will prevent automatic first-run recalculation when copied into another installation.

## Adjusting overall power and progression pace

Edit these existing sections in `config/essence_ascendance.toml`:

```toml
[power]
overall = 1.1

[progression]
cost_pressure = 0.9

[attunement]
pace = 1.25
```

This requests more automatic added power, cheaper generated investments and faster seal progress. It does not promise exactly 10% more damage or change item yields.

Existing Skill/Bonus endpoint settings remain explicit choices. If you want them to follow the new automatic power settings, remove the corresponding overrides before rebuilding; see [returning to automatic defaults](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation#automatic-settings-versus-saved-choices).

## Making a Bonus finish earlier

Find Breath Hold in `config/essence_ascendance/bonus_settings.toml`. For example:

```toml
[[bonus]]
id = "essence_ascendance:breath_hold"
min_tier = "essence_ascendance:awakened"
max_tier = "essence_ascendance:ascendant"
bonus_at_max = 30.0
minimum_bonus_at_lowest = 5.0
```

This requests at least five extra seconds at the first active tier and exactly thirty at completion. The mod fills in the intervening progression. Transcendent retains the completed benefit; it does not add another step.

Keep all four controls in the entry. A lower maximum or shorter interval must still leave room for useful improvements. Rebuild, then inspect the Bonus tooltip and `reports/bonus_tracks.csv`. An impossible request produces an error instead of quietly changing your chosen endpoints.

## Moving a Skill or changing its ranks

In `config/essence_ascendance/skill_settings.toml`, find the Skill's existing `[[skill]]` entry. Change `tier` to move its first unlock. To request three ranks, set `ranks = 3`; the generated stat blocks beneath that Skill supply its first and final effects.

Each `[[skill.stat]]` belongs to the preceding Skill. Change only the effect you intend, and retain its exact `path`. A `Percent` field uses percentage points, a `Chance`/`Fraction` field uses fractions such as `0.1` for 10%, and a `Ticks` field uses twenty ticks per second.

For an illustration of the file structure, this sets Kindling to one rank with equal first/final values:

```toml
[[skill]]
id = "essence_ascendance:kindling"
tier = "essence_ascendance:dormant"
ranks = 1

[[skill.stat]]
path = "kindling/burningDamageAmplificationPercent"
at_first_rank = 5.0
at_max_rank = 5.0

[[skill.stat]]
path = "kindling/burningDamagePercentPerSecond"
at_first_rank = 5.0
at_max_rank = 5.0
```

These are example values, not a universal generated default. One-rank effects need equal endpoints. On/off Skills must remain one rank; additional ranks need meaningful improvements. Tier changes must also respect prerequisite ordering.

After rebuilding, check the Skill in the Archive/Nexus and its rows in `reports/skill_rank_parameters.csv`. Effect edits do not automatically reprice an unchanged rank count. Changing rank count generates a new cost progression.

## Investigating an unexpectedly gated Skill

Start with its entry in `skill_settings.toml`, then check the Archive for prerequisite Skills, rank-specific tier requirements, milestones and mutually exclusive choices. A low base unlock does not remove the other requirements.

If needed, inspect the installed Skill with `/essence debug balance skill fatigue_flight 1`, using the appropriate Skill ID and rank. Player-specific diagnostic commands are listed on the [diagnostics page](https://github.com/mistaboom/essence_ascendance/wiki/Diagnostics-and-Testing). Granting a rank for testing does not remove prerequisites or prove that players can afford it normally.

## Investigating an unexpectedly valuable or valueless item

```mcfunction
/essence debug balance item "minecraft:iron_ingot"
/essence admin balance export
```

Use the item's real full ID in quotes. Find it in `reports/valuation.csv`. Compare the estimated material value with the actual Essence payout, and check output counts and reversible crafting forms. The cheapest recognized recipe can limit a payout even when another recipe is much more expensive.

The generator does not price the difficulty of your quests or automation. Use `balance_overrides.toml` for a specific supported correction rather than making every investment cheaper to compensate for one item. [Advanced overrides](https://github.com/mistaboom/essence_ascendance/wiki/Overrides-and-Customization) explains the available controls and their limits.

## Handling incomplete evidence for another mod

Ordinary recipes, common tags and exposed stats can work without a dedicated adapter. Hidden spells, upgrade systems, quest gates and machine running costs may not be represented. Test the actual items that matter to your pack, then correct supported measurements or set Skill/Bonus values to suit your design.

You do not need a bespoke integration to move a Skill or buff a Bonus. Changing a measurement does not implement another mod's hidden mechanic.

## Changing a pack after players have begun progressing

Keep a complete world/configuration/version backup. Try the update and rebuild in a copy, compare the Archive and reports, and test existing players before scheduling a live maintenance window.

This applies to supported balance edits within the current format. It does not provide migration from earlier beta worlds or item identifiers. Restoring a saved balance file alone does not reverse purchases, inventory changes or world activity; see [updating and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery).
