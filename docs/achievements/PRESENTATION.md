# Achievement presentation and icon provenance

The 69 approved selections use one native advancement tab,
named **Essence Ascendance**, rooted at **Equivalent Exchange** (ID 22). The tab-only
label follows the accepted user review; the root's approved tooltip/toast
name remains unchanged. Vanilla and other mods retain their own native tab labels
and discovery behavior. Minecraft hides tab buttons when only one tab is visible;
fresh players unlock vanilla tabs through their normal starting advancements.
There are no added structural or earnable
IDs. Nine branches group related activities, with at most nine children along a
branch. Parent lines are layout only; every criterion remains its existing
server-owned impossible criterion. All selected entries are discoverable before
completion, including on a fresh player join and datapack reload. No root grant
or fabricated progress is used. Explicit datapack hidden/missing displays and
reparenting outside this tab retain native discovery rules.

`catalog.json` remains the exact workbook design. [presentation.json](presentation.json)
records the final display map, descriptions, frame choices and source paths.
The approved English titles are unchanged; en_us is the repository's only locale.
Descriptions 119, 120 and 215 make the documented conditions explicit; the other
66 retain the approved actions verbatim. The exact clarifications are listed below.

Task frames mark a successful action/activation; goal frames mark collections,
sustained actions and intermediate milestones; challenge frames mark demanding
sequences/conjunctions and final milestones. Every selection uses a native toast.
Only **Break Through the Heavens** (35), **Exodia, Assemble!** (72) and **Limit Break**
(184) announce to chat, subject to Minecraft's normal gamerule. Native challenge
sound behavior is unchanged. There are no reward blocks, additional audio or skills.

## Description clarifications

The source actions in catalog.json remain unchanged. Three display descriptions
state their existing conditions more explicitly:

- **119:** Defeat five hostile mobs near your tamed cat. Keep the same cat
  unharmed during and between fights.
- **120:** Defeat ten hostile mobs with Ascendance weapons while you and the mobs
  are below Y=0 in the Overworld. Wear no boots.
- **215:** Defeat five hostile mobs whose health and armor information is being
  tracked by Hunter's Ledger.

ID 119 includes the same-cat and between-encounter damage restrictions. ID 120
includes the Engineering source's Overworld interpretation and both actor/victim
positions. ID 215 uses authoritative server tracking, not an assertion about
client HUD pixels. Functional equipment and native hand compatibility remain
the interpretation of equip/wield for 61, 72, 139, 166 and 205; the
[player guide](PLAYER-GUIDE.md) explains those requirements.

## Reused assets

All 69 icons are real existing ItemStacks. No image was created, downloaded,
painted or added. The dark background is Minecraft's built-in deepslate tiles.
Source uses `minecraft:textures/block/deepslate_tiles.png`; the established resource
processing step emits `minecraft:block/deepslate_tiles` for 26.1's ResourceTexture
codec. No generated resource is checked into source.

Mod assets below are existing entries in [art/asset-manifest.json](../../art/asset-manifest.json).
Their BBModel embedded layers/meshes, dimensions and established transforms remain
unchanged. The existing AssetPipeline, TexturePixels and CanonicalPaletteValues
continue generating both targets' native formats under their build directories.
Equipment uses the existing `minecraft:custom_data.essence_ascendance_equipment.tier`
contract; Focus uses `essence_ascendance_focus.tier`. These detached icon components
select appearance only and never create a gameplay item, owner or progress record.
Default means the item's existing unmodified stack appearance, not a missing texture.

The table records asset provenance. The user accepted the final reviewed visual
result on 2026-11-07; native rendering checks and the limits of that review are
recorded in [ACCEPTANCE.md](ACCEPTANCE.md). This does not claim a human playthrough
of all 69 or approval of every GUI scale/resource pack.

## Full display map

| ID / approved name | Branch / parent ID | Frame | Existing icon / tier | Source |
| --- | --- | --- | --- | --- |
| 1 — Jumped the Shark | Fishing rod / 5 | task | `essence_ascendance:ascendance_fishing_rod` / default | [ascendance_rod.bbmodel](../../art/source/models/item/ascendance_rod.bbmodel) |
| 2 — Catch and Decease | Fishing rod / 3 | challenge | `essence_ascendance:ascendance_fishing_rod` / default | [ascendance_rod.bbmodel](../../art/source/models/item/ascendance_rod.bbmodel) |
| 3 — So Long, and Thanks for All the Fish | Fishing rod / 1 | goal | `essence_ascendance:ascendance_fishing_rod` / default | [ascendance_rod.bbmodel](../../art/source/models/item/ascendance_rod.bbmodel) |
| 4 — Get Over Here! | Fishing rod / 7 | task | `essence_ascendance:ascendance_fishing_rod` / default | [ascendance_rod.bbmodel](../../art/source/models/item/ascendance_rod.bbmodel) |
| 5 — Friendly Neighborhood Fisherman | Fishing rod / 4 | goal | `essence_ascendance:ascendance_fishing_rod` / default | [ascendance_rod.bbmodel](../../art/source/models/item/ascendance_rod.bbmodel) |
| 7 — Accio, Loot! | Fishing rod / 22 | task | `essence_ascendance:ascendance_fishing_rod` / default | [ascendance_rod.bbmodel](../../art/source/models/item/ascendance_rod.bbmodel) |
| 19 — The Path of Ascendance | Ascension / 22 | task | `essence_ascendance:ascendance_nexus` / default | [nexus.bbmodel](../../art/source/models/block/nexus.bbmodel) |
| 22 — Equivalent Exchange | Entry / root | task | `essence_ascendance:essence_crucible` / default | [crucible.bbmodel](../../art/source/models/block/crucible.bbmodel) |
| 24 — Tapped for Essence | Machines and Essence / 22 | task | `essence_ascendance:essence_crucible` / default | [crucible.bbmodel](../../art/source/models/block/crucible.bbmodel) |
| 26 — Skill Issue: Resolved | Skills and companions / 22 | task | `essence_ascendance:ascendance_archive` / default | [ascendance_archive.bbmodel](../../art/source/models/item/ascendance_archive.bbmodel) |
| 28 — Enter the Matrix | Equipment / 22 | task | `essence_ascendance:ascendance_matrix` / default | [ascendance_matrix.bbmodel](../../art/source/models/item/ascendance_matrix.bbmodel) |
| 30 — Training Montage | Ascension / 19 | goal | `essence_ascendance:ascendance_archive` / default | [ascendance_archive.bbmodel](../../art/source/models/item/ascendance_archive.bbmodel) |
| 31 — I Have the Power! | Ascension / 30 | goal | `essence_ascendance:essence_focus` / dormant | [focus.bbmodel](../../art/source/models/item/focus.bbmodel) |
| 32 — The Force Awakens | Ascension / 31 | goal | `essence_ascendance:essence_focus` / awakened | [focus.bbmodel](../../art/source/models/item/focus.bbmodel) |
| 33 — Resonance Cascade | Ascension / 32 | goal | `essence_ascendance:essence_focus` / resonant | [focus.bbmodel](../../art/source/models/item/focus.bbmodel) |
| 34 — Plus Ultra! | Ascension / 33 | goal | `essence_ascendance:essence_focus` / ascendant | [focus.bbmodel](../../art/source/models/item/focus.bbmodel) |
| 35 — Break Through the Heavens | Ascension / 34 | challenge | `essence_ascendance:essence_focus` / transcendent | [focus.bbmodel](../../art/source/models/item/focus.bbmodel) |
| 36 — With Your Powers Combined | Ascension / 35 | challenge | `essence_ascendance:ascendance_archive` / default | [ascendance_archive.bbmodel](../../art/source/models/item/ascendance_archive.bbmodel) |
| 46 — You Must Construct Additional Pylons | Machines and Essence / 24 | task | `essence_ascendance:essence_pylon` / default | [pylon.bbmodel](../../art/source/models/block/pylon.bbmodel) |
| 49 — Now You’re Thinking with Essence | Machines and Essence / 46 | task | `essence_ascendance:essence_infuser` / default | [infuser.bbmodel](../../art/source/models/block/infuser.bbmodel) |
| 51 — Gotta Infuse ’Em All | Machines and Essence / 49 | goal | `essence_ascendance:essentium_ingot` / default | [essence_ingot.bbmodel](../../art/source/models/item/essence_ingot.bbmodel) |
| 56 — The Factory Must Grow | Machines and Essence / 51 | goal | `essence_ascendance:essence_infuser` / default | [infuser.bbmodel](../../art/source/models/block/infuser.bbmodel) |
| 57 — Fully Operational | Machines and Essence / 56 | goal | `essence_ascendance:essence_pylon` / default | [pylon.bbmodel](../../art/source/models/block/pylon.bbmodel) |
| 61 — Fullmetal Ascendant | Equipment / 28 | goal | `essence_ascendance:ascendance_chestplate` / latent | [ascendance_chestplate.bbmodel](../../art/source/models/item/ascendance_chestplate.bbmodel) |
| 63 — This Is My Final Form | Equipment / 166 | challenge | `essence_ascendance:ascendance_sword` / transcendent | [ascendance_sword.bbmodel](../../art/source/models/item/ascendance_sword.bbmodel) |
| 72 — Exodia, Assemble! | Equipment / 63 | challenge | `essence_ascendance:ascendance_helmet` / transcendent | [ascendance_helmet.bbmodel](../../art/source/models/item/ascendance_helmet.bbmodel) |
| 73 — One Rod to Reel Them All | Equipment / 72 | challenge | `essence_ascendance:ascendance_fishing_rod` / transcendent | [ascendance_rod.bbmodel](../../art/source/models/item/ascendance_rod.bbmodel) |
| 76 — Stick Them with the Pointy End | Glaive / 22 | task | `essence_ascendance:ascendance_glaive` / default | [ascendance_glaive.bbmodel](../../art/source/models/item/ascendance_glaive.bbmodel) |
| 77 — The Three-Body Problem | Glaive / 138 | challenge | `essence_ascendance:ascendance_glaive` / default | [ascendance_glaive.bbmodel](../../art/source/models/item/ascendance_glaive.bbmodel) |
| 85 — Let It Go | Skills and companions / 209 | task | `minecraft:ice` / default | Minecraft built-in resources |
| 95 — The Name of the Wind | Movement / 216 | goal | `minecraft:firework_rocket` / default | Minecraft built-in resources |
| 99 — To Infinity and Beyond! | Movement / 95 | challenge | `minecraft:elytra` / default | Minecraft built-in resources |
| 100 — Gotta Go Fast | Movement / 22 | task | `essence_ascendance:ascendance_boots` / dormant | [ascendance_boots.bbmodel](../../art/source/models/item/ascendance_boots.bbmodel) |
| 101 — Parkour! | Movement / 201 | goal | `essence_ascendance:ascendance_boots` / dormant | [ascendance_boots.bbmodel](../../art/source/models/item/ascendance_boots.bbmodel) |
| 102 — HM03: Surf | Movement / 101 | goal | `minecraft:water_bucket` / default | Minecraft built-in resources |
| 107 — Mostly Dead | Survival / 177 | task | `minecraft:ghast_tear` / default | Minecraft built-in resources |
| 110 — Second Breakfast | Survival / 107 | goal | `minecraft:cooked_beef` / default | Minecraft built-in resources |
| 114 — UNO Reverse | Survival / 200 | task | `minecraft:fermented_spider_eye` / default | Minecraft built-in resources |
| 118 — I Choose You! | Skills and companions / 208 | task | `minecraft:bone` / default | Minecraft built-in resources |
| 119 — Princess Donut’s Bodyguard | Skills and companions / 118 | challenge | `minecraft:cod` / default | Minecraft built-in resources |
| 120 — Dungeon Crawler: Barefoot Edition | Skills and companions / 215 | goal | `essence_ascendance:ascendance_sword` / latent | [ascendance_sword.bbmodel](../../art/source/models/item/ascendance_sword.bbmodel) |
| 122 — Diggy Diggy Flow | Gathering and farming / 22 | goal | `essence_ascendance:ascendance_pickaxe` / dormant | [ascendance_pickaxe.bbmodel](../../art/source/models/item/ascendance_pickaxe.bbmodel) |
| 126 — Under Pressure | Gathering and farming / 122 | task | `essence_ascendance:ascendance_pickaxe` / dormant | [ascendance_pickaxe.bbmodel](../../art/source/models/item/ascendance_pickaxe.bbmodel) |
| 128 — My Neighbor Grows Things | Gathering and farming / 126 | goal | `essence_ascendance:ascendance_hoe` / dormant | [ascendance_hoe.bbmodel](../../art/source/models/item/ascendance_hoe.bbmodel) |
| 131 — Just Keep Swimming | Gathering and farming / 204 | task | `minecraft:tropical_fish` / default | Minecraft built-in resources |
| 136 — Power Pole, Extend! | Glaive / 76 | task | `essence_ascendance:ascendance_glaive` / dormant | [ascendance_glaive.bbmodel](../../art/source/models/item/ascendance_glaive.bbmodel) |
| 138 — It’s Over Nine… Blocks! | Glaive / 206 | challenge | `essence_ascendance:ascendance_glaive` / transcendent | [ascendance_glaive.bbmodel](../../art/source/models/item/ascendance_glaive.bbmodel) |
| 139 — Great Sage, Equal to Heaven | Equipment / 73 | challenge | `essence_ascendance:ascendance_glaive` / transcendent | [ascendance_glaive.bbmodel](../../art/source/models/item/ascendance_glaive.bbmodel) |
| 145 — Flying Nimbus Not Included | Glaive / 77 | task | `essence_ascendance:ascendance_glaive` / ascendant | [ascendance_glaive.bbmodel](../../art/source/models/item/ascendance_glaive.bbmodel) |
| 166 — It's Dangerous to Go Alone | Equipment / 61 | task | `essence_ascendance:ascendance_shield` / latent | [ascendance_shield.bbmodel](../../art/source/models/item/ascendance_shield.bbmodel) |
| 169 — Heart Container | Survival / 22 | task | `minecraft:golden_apple` / default | Minecraft built-in resources |
| 176 — Filled with Determination | Survival / 169 | task | `minecraft:totem_of_undying` / default | Minecraft built-in resources |
| 177 — Death Defiance | Survival / 176 | challenge | `minecraft:totem_of_undying` / default | Minecraft built-in resources |
| 182 — Terrarian at Heart | Gathering and farming / 131 | challenge | `minecraft:diamond_ore` / default | Minecraft built-in resources |
| 184 — Limit Break | Survival / 114 | challenge | `minecraft:nether_star` / default | Minecraft built-in resources |
| 190 — The Colour of Magic | Machines and Essence / 57 | challenge | `essence_ascendance:essence_crucible` / default | [crucible.bbmodel](../../art/source/models/block/crucible.bbmodel) |
| 193 — Strength Before Weakness | Survival / 110 | goal | `essence_ascendance:ascendance_chestplate` / dormant | [ascendance_chestplate.bbmodel](../../art/source/models/item/ascendance_chestplate.bbmodel) |
| 197 — Howl's Moving Workshop | Machines and Essence / 190 | goal | `essence_ascendance:essence_infuser` / default | [infuser.bbmodel](../../art/source/models/block/infuser.bbmodel) |
| 199 — The Answer Is 42 | Machines and Essence / 197 | goal | `minecraft:book` / default | Minecraft built-in resources |
| 200 — Resistance Is Useful | Survival / 193 | goal | `essence_ascendance:ascendance_shield` / dormant | [ascendance_shield.bbmodel](../../art/source/models/item/ascendance_shield.bbmodel) |
| 201 — Allons-y! | Movement / 100 | task | `essence_ascendance:ascendance_boots` / dormant | [ascendance_boots.bbmodel](../../art/source/models/item/ascendance_boots.bbmodel) |
| 204 — My Cabbages Are Fine! | Gathering and farming / 128 | task | `minecraft:wheat` / default | Minecraft built-in resources |
| 205 — Moon Prism Power! | Ascension / 36 | challenge | `essence_ascendance:ascendance_chestplate` / awakened | [ascendance_chestplate.bbmodel](../../art/source/models/item/ascendance_chestplate.bbmodel) |
| 206 — Gum-Gum Glaive! | Glaive / 136 | task | `essence_ascendance:ascendance_glaive` / ascendant | [ascendance_glaive.bbmodel](../../art/source/models/item/ascendance_glaive.bbmodel) |
| 207 — Talk-No-Jutsu | Skills and companions / 119 | task | `minecraft:white_banner` / default | Minecraft built-in resources |
| 208 — Frieren Would Approve | Skills and companions / 26 | goal | `essence_ascendance:ascendance_archive` / default | [ascendance_archive.bbmodel](../../art/source/models/item/ascendance_archive.bbmodel) |
| 209 — The Dungeon Is Delicious | Skills and companions / 120 | goal | `minecraft:mushroom_stew` / default | Minecraft built-in resources |
| 215 — Eye of the Tiger | Skills and companions / 207 | goal | `minecraft:spyglass` / default | Minecraft built-in resources |
| 216 — Don't Stop Me Now | Movement / 102 | challenge | `essence_ascendance:ascendance_boots` / dormant | [ascendance_boots.bbmodel](../../art/source/models/item/ascendance_boots.bbmodel) |
