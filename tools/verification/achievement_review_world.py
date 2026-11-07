"""Build an isolated native 1.21.1 Fabric achievement review world and Prism kit.

Uses catalog/production-manifest pins and the established packaged server cache.
Never opens a normal world or writes to an installed Prism instance. New labels
must be used for reruns, preserving previous review evidence.
"""
from __future__ import annotations

import argparse
import gzip
import importlib.util
import io
import json
from pathlib import Path
import shutil
import struct
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BOUNDARY = ROOT / "build/achievement-review-world"
WORLD_NAME = "Essence Ascendance Achievement Review"
spec = importlib.util.spec_from_file_location("ea_review_runtime", ROOT / "tools/audio/packaged_acceptance.py")
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


def isolated(path):
    path = Path(path).absolute()
    if ".." in path.parts or not path.is_relative_to(BOUNDARY) or not path.resolve().is_relative_to(BOUNDARY.resolve()):
        raise ValueError("Expected a fresh isolated review-world directory")
    for parent in (path, *path.parents):
        if parent == ROOT:
            break
        if parent.is_symlink() or getattr(parent, "is_junction", lambda: False)():
            raise ValueError("Review-world paths cannot be redirected")
    return path


# Small lossless NBT codec: preserve every unknown tag while changing only the
# native singleplayer defaults. Arrays remain raw bytes, including numeric bits.
class Nbt:
    def __init__(self, raw):
        self.stream = io.BytesIO(raw)

    def read(self, size):
        value = self.stream.read(size)
        if len(value) != size:
            raise ValueError("Truncated NBT")
        return value

    def number(self, fmt):
        return struct.unpack(fmt, self.read(struct.calcsize(fmt)))[0]

    def string(self):
        return self.read(self.number(">H"))

    def payload(self, kind):
        if kind in {1, 2, 3, 4, 5, 6}:
            return self.read({1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[kind])
        if kind == 8:
            return self.string()
        if kind in {7, 11, 12}:
            count = self.number(">i")
            if count < 0:
                raise ValueError("Negative NBT array length")
            return (count, self.read(count * {7: 1, 11: 4, 12: 8}[kind]))
        if kind == 9:
            child, count = self.number(">B"), self.number(">i")
            if count < 0:
                raise ValueError("Negative NBT list length")
            return (child, [self.payload(child) for _ in range(count)])
        if kind == 10:
            value = {}
            while child := self.number(">B"):
                name = self.string()
                if name in value:
                    raise ValueError("Duplicate NBT name")
                value[name] = (child, self.payload(child))
            return value
        raise ValueError(f"Unsupported NBT tag {kind}")


def nbt_string(value):
    return struct.pack(">H", len(value)) + value


def nbt_payload(kind, value):
    if kind in {1, 2, 3, 4, 5, 6}:
        return value
    if kind == 8:
        return nbt_string(value)
    if kind in {7, 11, 12}:
        return struct.pack(">i", value[0]) + value[1]
    if kind == 9:
        return struct.pack(">Bi", value[0], len(value[1])) + b"".join(nbt_payload(value[0], item) for item in value[1])
    if kind == 10:
        return b"".join(bytes([tag]) + nbt_string(name) + nbt_payload(tag, content)
                        for name, (tag, content) in value.items()) + b"\0"
    raise ValueError(f"Unsupported NBT tag {kind}")


def read_level(path):
    raw = gzip.decompress(path.read_bytes())
    reader = Nbt(raw)
    kind, name = reader.number(">B"), reader.string()
    root = reader.payload(kind)
    if kind != 10 or reader.stream.read():
        raise ValueError("Expected one complete native NBT root compound")
    if bytes([kind]) + nbt_string(name) + nbt_payload(kind, root) != raw:
        raise AssertionError("NBT round trip differs before editing")
    return name, root


def prepare_singleplayer(path):
    name, root = read_level(path)
    data = root[b"Data"][1]
    for key, value in {b"GameType": 1, b"SpawnX": 0, b"SpawnY": 64, b"SpawnZ": 2}.items():
        data[key] = (3, struct.pack(">i", value))
    data[b"allowCommands"] = (1, b"\1")
    data[b"LevelName"] = (8, WORLD_NAME.encode())
    data[b"SpawnAngle"] = (5, struct.pack(">f", 180.0))
    path.write_bytes(gzip.compress(b"\x0a" + nbt_string(name) + nbt_payload(10, root), mtime=0))


def verify_singleplayer(path):
    _, root = read_level(path)
    data = root[b"Data"][1]
    assert struct.unpack(">i", data[b"GameType"][1])[0] == 1
    assert data[b"allowCommands"] == (1, b"\1")
    assert data[b"LevelName"][1].decode() == WORLD_NAME
    assert [struct.unpack(">i", data[key][1])[0] for key in (b"SpawnX", b"SpawnY", b"SpawnZ")] == [0, 64, 2]


def snbt_string(value):
    return json.dumps(value, ensure_ascii=False)


def text_component(value):
    return snbt_string(json.dumps({"text": value}, separators=(",", ":")))


def sign(x, y, z, lines):
    messages = ",".join(text_component(line) for line in (lines + [""] * 4)[:4])
    return f'setblock {x} {y} {z} minecraft:oak_sign[rotation=0]{{front_text:{{messages:[{messages}],color:"black",has_glowing_text:1b}},is_waxed:1b}}'


def item(item_id, count=1, components=None):
    return "{id:" + snbt_string(item_id) + f",count:{count}" + (",components:" + components if components else "") + "}"


def chest(x, z, entries, name):
    values = []
    for slot, entry in enumerate(entries):
        values.append("{Slot:" + str(slot) + "b," + entry[1:])
    return f"setblock {x} 64 {z} minecraft:chest[facing=south]{{CustomName:{text_component(name)},Items:[{','.join(values)}]}}"


def build_commands():
    commands = [
        "gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
        "gamerule doFireTick false", "gamerule keepInventory true", "gamerule doInsomnia false",
        "time set 6000", "weather clear", "difficulty normal", "setworldspawn 0 64 2 180",
        "forceload add -48 -16 48 144",
        "fill -40 63 -12 40 63 40 minecraft:smooth_stone",
        "fill -2 63 -12 2 63 140 minecraft:quartz_block",
        "fill -40 63 5 40 63 7 minecraft:quartz_block",
        "fill -40 63 21 40 63 23 minecraft:quartz_block",
        sign(0, 64, 0, ["ACHIEVEMENT", "REVIEW WORLD", "69 selections", "Guide in chest"]),
        sign(0, 64, 8, ["Creative start", "Buttons: display", "Survival: mechanics", "Fresh world = reset"]),
    ]
    controls = [
        (-9, "Grant all 69", "execute as @p at @s run advancement grant @s from essence_ascendance:achievements/a022"),
        (-3, "Revoke all 69", "execute as @p at @s run advancement revoke @s from essence_ascendance:achievements/a022"),
        (3, "Survival mode", "gamemode survival @p"),
        (9, "Creative mode", "gamemode creative @p"),
    ]
    for x, label, command in controls:
        commands += [
            f"setblock {x} 64 -5 minecraft:command_block{{Command:{snbt_string(command)},TrackOutput:0b}}",
            f"setblock {x} 65 -5 minecraft:stone_button[face=floor]",
            sign(x, 64, -3, [label, "Press top button", "Display grants are", "not gameplay proof"]),
        ]
    pages = [
        "Essence Ascendance\nAchievement Review\n\nMinecraft 1.21.1 Fabric\n69 approved achievements.\n\nOpen Advancements (L). All selected entries are visible before earning any.",
        "DISPLAY CHECK\n\nButtons north of spawn grant or revoke all 69 selections. Native toasts may queue.\n\nGranting demonstrates display only. Revoke clears completion, not durable partial collections. Reimport the clean save for a complete reset.",
        "NATIVE COMMANDS\n\n/advancement grant @s from essence_ascendance:achievements/a022\n\n/advancement revoke @s from essence_ascendance:achievements/a022\n\nSingle ID: use only instead of from, e.g. achievements/a072.",
        "SETUP FOR MECHANICS\n\nUse Survival for real achievement actions. Take supplies from chests. Place your own Nexus and machines so operation attribution is legitimate. The showroom machines were placed by console and are examples, not owned production stations.",
        "EXISTING ADMIN TOOLS\n\n/essence admin player tier set transcendent\n/essence admin player skills grant all\n/essence admin player guide give\n/essence admin item tier set transcendent\n\nSetup commands themselves do not prove gameplay.",
        "AREAS\n\nWest: Nexus and machine supplies.\nEast: equipment and crops.\nSouthwest: combat pen and summon button.\nSouth: water, grapple ledge and 100-block marked sprint lane.\n\nControls and full command list are in README outside the world.",
    ]
    book = '{"minecraft:written_book_content":{title:"Achievement Review",author:"Essence Ascendance",pages:[' + ",".join(text_component(page) for page in pages) + "]}}"
    commands += [chest(3, 1, [item("minecraft:written_book", components=book), item("minecraft:cooked_beef", 64), item("minecraft:torch", 64)], "Start here: guide and food")]

    commands += [
        sign(-16, 64, 1, ["PROGRESSION", "Take your Nexus", "Place it yourself", "Then use it"]),
        "setblock -16 64 -2 essence_ascendance:ascendance_nexus",
        chest(-13, 1, [item("essence_ascendance:ascendance_nexus", 8), item("essence_ascendance:ascendance_matrix", 8),
                       item("essence_ascendance:ascendance_archive"), item("minecraft:crafting_table"), item("minecraft:oak_planks", 64)], "Progression supplies"),
        sign(-16, 64, 17, ["MACHINES", "Place your own", "Insert and Start", "One operator"]),
        "setblock -20 64 14 essence_ascendance:essence_crucible",
        "setblock -16 64 14 essence_ascendance:essence_infuser",
        "setblock -12 64 14 essence_ascendance:essence_pylon",
        chest(-13, 17, [item("essence_ascendance:essence_crucible", 4), item("essence_ascendance:essence_infuser", 4),
                        item("essence_ascendance:essence_pylon", 8), item("essence_ascendance:essence_focus", 8),
                        item("minecraft:iron_ingot", 64), item("minecraft:gold_ingot", 64), item("minecraft:diamond", 64),
                        item("minecraft:emerald", 64), item("minecraft:redstone", 64), item("minecraft:coal", 64)], "Machine blocks and inputs"),
        sign(16, 64, 1, ["EQUIPMENT", "Tools and armor", "Tier command in", "the guide book"]),
        chest(13, 1, [item("essence_ascendance:" + name) for name in ["ascendance_sword", "ascendance_glaive", "ascendance_ranged_weapon",
                       "ascendance_caster", "ascendance_shield", "ascendance_helmet", "ascendance_chestplate", "ascendance_leggings",
                       "ascendance_boots", "ascendance_pickaxe", "ascendance_axe", "ascendance_shovel", "ascendance_hoe", "ascendance_fishing_rod"]]
              + [item("minecraft:arrow", 64), item("minecraft:netherite_pickaxe"), item("minecraft:water_bucket"), item("minecraft:totem_of_undying")], "Equipment and movement supplies"),
        sign(16, 64, 17, ["FARMING", "Five crop types", "Skill growth test", "Mature trample row"]),
        chest(13, 17, [item("minecraft:" + name, 64) for name in ["wheat_seeds", "carrot", "potato", "beetroot_seeds", "torchflower_seeds", "bone_meal"]]
              + [item("minecraft:netherite_hoe"), item("minecraft:water_bucket")], "Crop supplies"),
    ]
    for row, crop in enumerate(["wheat", "carrots", "potatoes", "beetroots", "torchflower_crop"]):
        x = 20 + row * 3
        mature = "minecraft:torchflower" if crop == "torchflower_crop" else f"minecraft:{crop}[age={3 if crop == 'beetroots' else 7}]"
        commands += [f"fill {x} 63 13 {x} 63 19 minecraft:farmland[moisture=7]",
                     f"fill {x} 64 13 {x} 64 17 minecraft:{crop}[age=0]",
                     f"setblock {x} 64 19 {mature}",
                     f"fill {x+1} 63 13 {x+1} 63 19 minecraft:water"]
    commands += [
        "fill -34 63 26 -22 63 38 minecraft:stone_bricks", "fill -34 64 26 -22 66 38 minecraft:glass hollow",
        "fill -33 64 27 -23 66 37 minecraft:air", "setblock -28 64 26 minecraft:oak_fence_gate[facing=north]",
        "setblock -28 65 26 minecraft:air", "fill -34 67 26 -22 67 38 minecraft:stone_bricks",
        sign(-18, 64, 27, ["COMBAT PEN", "Button spawns", "one real zombie", "Survival to test"]),
        'setblock -18 64 30 minecraft:command_block{Command:"summon minecraft:zombie -28 64 32 {PersistenceRequired:1b,CanPickUpLoot:0b}",TrackOutput:0b}',
        "setblock -18 65 30 minecraft:stone_button[face=floor]",
        chest(-16, 30, [item("minecraft:zombie_spawn_egg", 16), item("minecraft:skeleton_spawn_egg", 16), item("minecraft:cat_spawn_egg", 8),
                        item("minecraft:wolf_spawn_egg", 8), item("minecraft:bone", 64), item("minecraft:cod", 64), item("minecraft:lead", 8)], "Controlled combat and companion setup"),
        "fill -22 60 46 -6 62 76 minecraft:water",
        "fill -23 59 45 -5 59 77 minecraft:stone_bricks", "fill -23 60 45 -23 63 77 minecraft:stone_bricks",
        "fill -5 60 45 -5 63 77 minecraft:stone_bricks", "fill -23 60 45 -5 63 45 minecraft:stone_bricks",
        "fill -23 60 77 -5 63 77 minecraft:stone_bricks", "fill -22 63 46 -6 63 76 minecraft:air",
        sign(-14, 64, 43, ["WATER COURSE", "30-block crossing", "Rod / swimming", "Reposition freely"]),
        "fill -23 76 79 -5 76 82 minecraft:stone_bricks", "fill -23 64 79 -23 75 82 minecraft:stone_bricks",
        sign(-14, 77, 80, ["GRAPPLE LEDGE", "13 blocks above", "starting surface", "Land on the top"]),
        sign(4, 64, 40, ["SPRINT LANE", "Start at Z 40", "Finish at Z 140", "100 horizontal"]),
        "fill -2 63 40 2 63 40 minecraft:lime_concrete", "fill -2 63 140 2 63 140 minecraft:red_concrete",
        sign(4, 64, 140, ["100 BLOCKS", "Finish line", "Continuous route", "per rule required"]),
        "fill 12 63 47 28 63 63 minecraft:stone", "fill 12 64 47 28 66 63 minecraft:stone",
        sign(20, 64, 44, ["MINING RHYTHM", "Placed stone only", "NOT natural ore", "Use fresh terrain"]),
        "forceload remove all", "say EA_REVIEW_BUILD_COMPLETE",
    ]
    return commands


def prepare(target, base):
    source = ROOT / "build/packaged-fabric1211-server"
    server = isolated(base / "server")
    server.mkdir(parents=True, exist_ok=False)
    for name in ("libraries", "fabric-server-launch.jar", "fabric-server-launcher.properties", "server.jar", "versions", ".fabric"):
        path = source / name
        if path.is_dir():
            shutil.copytree(path, server / name)
        elif path.is_file():
            runtime.copy_verified(path, server / name)
    (server / "mods").mkdir()
    runtime.copy_dependencies(target, source / "mods", server / "mods")
    archive = runtime.production_archive(target)
    runtime.copy_verified(archive, server / "mods" / archive.name)
    (server / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    flat = {"biome": "minecraft:plains", "layers": [{"block": "minecraft:bedrock", "height": 1},
            {"block": "minecraft:stone", "height": 124}, {"block": "minecraft:dirt", "height": 2},
            {"block": "minecraft:grass_block", "height": 1}], "lakes": False, "features": False}
    (server / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n"
        "view-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-tick-time=120000\n"
        "enable-command-block=true\ngamemode=creative\ndifficulty=normal\nlevel-name=world\n"
        "level-type=minecraft:flat\nlevel-seed=69\ngenerate-structures=false\ngenerator-settings="
        + json.dumps(flat, separators=(",", ":")) + "\n", encoding="utf-8")
    pack = server / "world/datapacks/achievement_review"
    functions = pack / "data/ea_review/function"
    functions.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": "Local achievement review area and native display controls"}}), encoding="utf-8")
    (functions / "build.mcfunction").write_text("\n".join(build_commands()) + "\n", encoding="utf-8")
    checks = [
        "forceload add -48 -16 48 144",
        "execute if block -16 64 -2 essence_ascendance:ascendance_nexus run say EA_REVIEW_NEXUS_OK",
        "execute if block -20 64 14 essence_ascendance:essence_crucible run say EA_REVIEW_CRUCIBLE_OK",
        "execute if block -16 64 14 essence_ascendance:essence_infuser run say EA_REVIEW_INFUSER_OK",
        "execute if block -12 64 14 essence_ascendance:essence_pylon run say EA_REVIEW_PYLON_OK",
        "execute if data block 3 64 1 Items[0].components.\"minecraft:written_book_content\" run say EA_REVIEW_GUIDE_OK",
        'execute if data block 13 64 1 Items[{id:"essence_ascendance:ascendance_ranged_weapon"}] run say EA_REVIEW_EQUIPMENT_OK',
        "execute if block -14 62 60 minecraft:water run say EA_REVIEW_WATER_OK",
        "execute if block 0 63 140 minecraft:red_concrete run say EA_REVIEW_SPRINT_OK",
        "execute if block 20 64 13 minecraft:wheat run say EA_REVIEW_CROPS_OK",
        "execute if data block -9 64 -5 Command run say EA_REVIEW_CONTROL_OK",
        "forceload remove all", "say EA_REVIEW_VERIFY_COMPLETE",
    ]
    (functions / "verify.mcfunction").write_text("\n".join(checks) + "\n", encoding="utf-8")
    return server, [str(runtime.java(21)), "-Xmx2G", "-XX:ActiveProcessorCount=4", "-jar", "fabric-server-launch.jar", "nogui"]


def run_server(server, command, stage, commands):
    log_path = server.parent / f"{stage}.log"
    native_errors = ("Failed to load function", "Whilst parsing command", "Unknown or incomplete command",
                     "Incorrect argument for command", "Unknown item", "Unknown block type")
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=server, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        try:
            def wait(marker, offset=0):
                deadline = time.monotonic() + 300
                while True:
                    output = log_path.read_text(encoding="utf-8", errors="replace")
                    for invalid in native_errors:
                        if invalid in output:
                            raise AssertionError(f"{stage}: native command error {invalid}; inspect {log_path}")
                    if marker in output[offset:]:
                        return
                    if process.poll() is not None:
                        raise RuntimeError(f"{stage}: exited {process.returncode} before {marker}; inspect {log_path}")
                    if time.monotonic() >= deadline:
                        raise TimeoutError(f"{stage}: missing {marker}; inspect {log_path}")
                    time.sleep(.25)
            wait("Done (")
            for text, marker in commands:
                offset = len(log_path.read_text(encoding="utf-8", errors="replace"))
                process.stdin.write(text + "\n")
                process.stdin.flush()
                wait(marker, offset)
            process.stdin.write("stop\n")
            process.stdin.flush()
            code = process.wait(timeout=120)
            if code:
                raise RuntimeError(f"{stage}: exit {code}")
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=15)
    text = log_path.read_text(encoding="utf-8", errors="replace")
    loader = json.loads((ROOT / "gradle/targets.json").read_text())["versions"]["1.21.1"]["fabricLoader"]
    if f"Loading Minecraft 1.21.1 with Fabric Loader {loader}" not in text:
        raise AssertionError("Native review runtime did not report the exact catalog loader")
    for invalid in native_errors:
        if invalid in text:
            raise AssertionError(f"{stage}: native command error {invalid}")
    if "All dimensions are saved" not in text:
        raise AssertionError("World did not stop cleanly")
    return {"log": str(log_path), "sha256": runtime.digest(log_path), "exit": code}


def zip_folder(folder, destination):
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(folder.rglob("*")):
            if path.is_file() and path.name != "session.lock":
                archive.write(path, path.relative_to(folder))


def package(base, server, target):
    output = isolated(base / "deliverables")
    output.mkdir()
    instance = isolated(base / "prism-instance")
    minecraft = instance / ".minecraft"
    (minecraft / "saves").mkdir(parents=True)
    shutil.copytree(server / "world", minecraft / "saves" / WORLD_NAME, ignore=shutil.ignore_patterns("session.lock"))
    shutil.copytree(server / "mods", minecraft / "mods")
    if (server / "config").exists():
        shutil.copytree(server / "config", minecraft / "config")
    profile = json.loads((ROOT / "gradle/targets.json").read_text())["versions"]["1.21.1"]
    (instance / "mmc-pack.json").write_text(json.dumps({"formatVersion": 1, "components": [
        {"uid": "net.minecraft", "version": "1.21.1", "important": True},
        {"uid": "net.fabricmc.fabric-loader", "version": profile["fabricLoader"]}]}, indent=2) + "\n", encoding="utf-8")
    (instance / "instance.cfg").write_text(
        "[General]\nConfigVersion=1.2\nInstanceType=OneSix\nname=Essence Ascendance - Achievement Review\n"
        "iconKey=default\nOverrideMemory=true\nMinMemAlloc=512\nMaxMemAlloc=3072\n"
        "OverrideJavaLocation=false\n", encoding="utf-8")
    (minecraft / "options.txt").write_text("renderDistance:8\nsimulationDistance:5\nautoJump:false\n", encoding="utf-8")
    readme = f"""# Essence Ascendance achievement review kit

Target: Minecraft 1.21.1, Fabric Loader {profile['fabricLoader']}, Java 21.
Exact production mod SHA-256: `{target['sha256']}`.

## Open the ready world

1. In Prism Launcher choose **Add Instance → Import** and select
   `Essence-Ascendance-Achievement-Review-Prism.zip`.
2. Launch the new **Essence Ascendance - Achievement Review** instance with Java 21.
   Prism may retrieve its normal Minecraft/loader runtime. All required mod jars
   are included, pinned to the production catalog.
3. Open Singleplayer → **{WORLD_NAME}**. You start in Creative at the review plaza
   with cheats enabled. The chest beside spawn contains an in-world guide.

The separate `Essence-Ascendance-Achievement-Review-Save.zip` is a save-only alternative:
extract its top-level world folder into an existing **matching 1.21.1 Fabric** game's
`saves` folder. Use the exact mod/dependency versions listed in `review-receipt.json`.
The Prism kit is the easiest way to keep this review separate from ordinary worlds.

## View all 69 selected achievements

Open Advancements (default L). The **Essence Ascendance** tab contains all 69
selected achievements and is visible before earning any. Its first achievement
is still named **Equivalent Exchange**. A fresh save may show only this tab:
vanilla tabs appear as you make their required progress, and Minecraft hides
the tab buttons when only one tab is visible.

To reveal the vanilla **Minecraft** tab, switch to Survival, take four planks
from the west progression chest, craft a crafting table in your inventory's
2×2 grid, and move the result into your inventory. Picking up a crafting table
in Survival also works: the vanilla root checks for a crafting table entering
your inventory. Reopen Advancements to compare the two tabs. The supplied save
does not pre-grant vanilla progress.

The four buttons north of spawn grant/revoke the selected tree and change game mode.
The same native commands work in chat:

```text
/advancement grant @s from essence_ascendance:achievements/a022
/advancement revoke @s from essence_ascendance:achievements/a022
```

`from` includes Equivalent Exchange (a022) and its 68 selected descendants. It does
not touch legacy `water_skiing` or other advancement trees. Native toast delivery
may queue after a bulk grant. For an individual toast, revoke then grant one ID:

```text
/advancement revoke @s only essence_ascendance:achievements/a072
/advancement grant @s only essence_ascendance:achievements/a072
```

Grant commands demonstrate display wiring. They do not prove gameplay conditions.
Revoke clears native completion; it does not clear durable lifetime partials.
Reimport the untouched save for a completely clean gameplay test.

## Playtest areas

- Spawn: guide chest and native grant/revoke/Creative/Survival controls.
- West: Nexus showcase and machine station, plus spare blocks and input supplies.
  Place your own Nexus, Crucible, Infuser and Pylons for legitimate ownership and
  operator testing. Console-placed showroom blocks are visual examples.
- East: equipment chest and five crop types, with immature growth and mature rows.
- Southwest: enclosed combat pen and a button that summons a real zombie.
  Spawn eggs, cat/wolf taming supplies and leads are in the adjacent chest.
- South: 30-block water course, a grapple ledge 13 blocks above the plaza, a marked
  100-block sprint lane, and placed stone for Mining Momentum. Placed stone/ore
  does not prove natural generation. This flat Overworld does not generate
  natural ore. Use a separate normally generated world for ID 126, or the
  dedicated native fixture for positive generated Latent Ore evidence.

Use Survival for real action checks. Existing setup commands are available:

```text
/gamemode survival
/gamemode creative
/essence admin player tier set transcendent
/essence admin player skills grant all
/essence admin player guide give
/essence admin item tier set transcendent
```

The item command changes the held item. Choose the relevant skill/loadout in the
existing UI; granting skills is setup, not an achievement success. This world
contains no new skill, balance change, or production command.

## Verification scope

The exact production jar starts the dedicated server, constructs this world using
native commands, saves it, then reloads the edited singleplayer defaults and checks
the stations, guide, control, water, crop and sprint endpoint before a final save.
`review-receipt.json` records hashes and logs. That verifies the saved artifact;
it is not a claim of human visual review or all 69 gameplay conditions.
"""
    (output / "README.md").write_text(readme, encoding="utf-8")
    (instance / "README.md").write_text(readme, encoding="utf-8")
    zip_folder(instance, output / "Essence-Ascendance-Achievement-Review-Prism.zip")
    zip_folder(minecraft / "saves", output / "Essence-Ascendance-Achievement-Review-Save.zip")
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", required=True)
    args = parser.parse_args()
    if not args.label.replace("-", "").isalnum():
        parser.error("Use a new alphanumeric/hyphen label")
    manifest = json.loads((ROOT / "build/production-artifacts-all.json").read_text())
    catalog = json.loads((ROOT / "gradle/targets.json").read_text())
    if {x["target"] for x in manifest["targets"]} != set(catalog["release"]["requiredTargetIds"]):
        raise ValueError("Production manifest differs from the four-target catalog")
    target = next(entry for entry in manifest["targets"] if entry["target"] == "1211Fabric")
    runtime.production_archive(target)
    base = isolated(BOUNDARY / args.label)
    base.mkdir(parents=True, exist_ok=False)
    server, command = prepare(target, base)
    receipt = {"target": target, "native_command": command, "mode": "native constructed review artifact, not 69-rule acceptance"}
    try:
        receipt["build"] = run_server(server, command, "build", [
            ("function ea_review:build", "EA_REVIEW_BUILD_COMPLETE"),
            ("save-all flush", "Saved the game")])
        prepare_singleplayer(server / "world/level.dat")
        verify_singleplayer(server / "world/level.dat")
        receipt["cold_load"] = run_server(server, command, "cold-load", [
            ("function ea_review:verify", "EA_REVIEW_VERIFY_COMPLETE"),
            ("save-all flush", "Saved the game")])
        log = Path(receipt["cold_load"]["log"]).read_text(encoding="utf-8", errors="replace")
        for marker in ("NEXUS", "CRUCIBLE", "INFUSER", "PYLON", "GUIDE", "EQUIPMENT", "WATER", "SPRINT", "CROPS", "CONTROL"):
            if "EA_REVIEW_" + marker + "_OK" not in log:
                raise AssertionError("Missing native saved-world check: " + marker)
        verify_singleplayer(server / "world/level.dat")
        runtime.production_archive(target)
        output = package(base, server, target)
    except Exception as failure:
        receipt.update(result="FAIL", failure=str(failure))
        (base / "review-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        raise
    receipt["result"] = "PASS"
    receipt["singleplayer_defaults"] = {"name": WORLD_NAME, "creative": True, "cheats": True, "spawn": [0, 64, 2]}
    receipt["mods"] = [{"file": path.name, "bytes": path.stat().st_size, "sha256": runtime.digest(path)} for path in sorted((server / "mods").iterdir()) if path.is_file()]
    receipt["artifacts"] = [{"file": str(path), "bytes": path.stat().st_size, "sha256": runtime.digest(path)} for path in sorted(output.iterdir()) if path.is_file()]
    (output / "review-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"result": "PASS", "output": str(output), "artifacts": receipt["artifacts"]}), flush=True)


if __name__ == "__main__":
    main()
