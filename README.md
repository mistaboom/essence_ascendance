# Essence Ascendance

**Turn what you gather into who you become.**

Build a character, build an Essence network, and work your way from Latent beginnings to Transcendent power. Essence Ascendance brings character progression, branching Skills, and evolving equipment to **Minecraft 1.21.1 and 26.1.2** on **Fabric and NeoForge**.

Explore for materials. Dissolve supported items into six Essences. Invest in the abilities that suit your playstyle, then put them to work as you earn your next Ascension.

[Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/essence-ascendance) · [Download on Modrinth](https://modrinth.com/mod/essence-ascendance) · [GitHub Releases](https://github.com/mistaboom/essence_ascendance/releases)

---

## Shape your character

Want to hit harder, survive longer, move faster, or get more from every expedition? Six Essence categories let you choose where to invest:

- **Offense** — Strengthen your combat abilities.
- **Defense** — Improve protection and resistance to incoming damage.
- **Vitality** — Build health, recovery, and staying power.
- **Mobility** — Enhance movement and traversal.
- **Gathering** — Improve mining, harvesting, and resource collection.
- **Utility** — Develop useful capabilities such as reach, efficiency, and experience gain.

Allocate character Bonuses and explore **90 Skills** across the six categories. Skills have their own ranks, prerequisites, and tier requirements; review your choices and apply your build at the **Ascendance Nexus**.

## Earn your Ascension

**Latent → Dormant → Awakened → Resonant → Ascendant → Transcendent**

Each tier opens a new chapter of **Category Attunement**. Complete seals through the activities listed at your Nexus, choose which categories to pursue, and ascend when you've earned enough seals. Mixing different activity sources rewards a varied journey.

Essence strengthens your build along the way. Your next player tier is earned through Attunement, while equipment follows its own infusion path.

## Build your Essence network

Give the materials you collect a new purpose with a network of specialized machines:

- **Essence Crucible** — Dissolve supported items, store their Essences, and channel Essence to your character.
- **Essence Pylons** — Extend a linked Crucible's capacity, range, and processing capabilities with an installed Focus.
- **Essence Infuser** — Create infused materials, upgrade Foci and compatible equipment, and repair artifacts using a linked Essence reservoir.
- **Ascendance Nexus** — Plan your Bonuses and Skills, track Attunement, and apply your next Ascension.

Start with a small setup, then upgrade your Foci and expand your network as your needs grow.

## Forge equipment worth keeping

Weapons, armor, and tools have their own progression. Use infusion to develop compatible equipment through increasingly powerful tiers, and build gear around the character you want to play.

Your character and equipment progress together through two connected systems: earn your player tier through gameplay, and invest Essence and materials into your equipment.

## Find your first step

1. Find **Latent ore** and smelt it into Latent Ingots.
2. Craft **Channelstone** and an **Ascendance Nexus**.
3. Check Category Attunement at the Nexus and work toward your first Ascension.
4. Reach **Dormant** to receive the **Ascendance Archive**, your portable guide to machines, Essence yields, Skills, and equipment.

The Archive includes a guided route and detailed reference pages, so you can explore the systems as you unlock them.

## Bring your modpack along

Essence Ascendance estimates item values from shared item properties and recipes. Pack authors can edit Skill unlock tiers, rank counts and native stat endpoints in `skill_settings.toml`, and Bonus active tiers and effect bounds in `bonus_settings.toml`. See [simple balance generation](docs/simple-balance.md) for the current development model and fresh-world requirement.

**JEI integration** provides recipe browsing when JEI is installed.

---

## Installation

**Install the matching Minecraft version and loader build on both client and server.**

| Minecraft | Java | Fabric Loader | NeoForge | Architectury API |
| --- | --- | --- | --- | --- |
| 1.21.1 | 21 or newer | 0.19.3 or newer | 21.1 or newer | 13.0.11 or newer |
| 26.1.2 | 25 or newer | 0.19.5 or newer | 26.1.2.99 or newer | 20.1.17 or newer |

- Both loaders require [Architectury API](https://modrinth.com/mod/architectury-api). Fabric also requires [Fabric API](https://modrinth.com/mod/fabric-api).
- Download the Essence Ascendance jar for your Minecraft version and loader and place it, with its required dependencies, in your instance's `mods` folder. Choose dependency versions for the same loader and Minecraft version.
- **JEI is optional.**

**Updating from beta.7 or earlier requires a fresh world and fresh configuration.** See the [beta.8 release notes](https://github.com/mistaboom/essence_ascendance/releases/tag/v1.0.0-beta.8) for that upgrade boundary. Beta.13 requires no world or configuration reset when updating from beta.12 within the same Minecraft version. Run `/essence admin balance rebuild` after updating to apply the corrected cheap bulk-material values to an existing balance profile.

## Help shape the beta

Essence Ascendance is currently in **beta**. [Report a bug or suggest a feature](https://github.com/mistaboom/essence_ascendance/issues), and include your mod version, Minecraft version, loader, and relevant logs when reporting a problem.

[Source code](https://github.com/mistaboom/essence_ascendance) · [MIT license](https://github.com/mistaboom/essence_ascendance/blob/main/LICENSE) · [Development guide](https://github.com/mistaboom/essence_ascendance/blob/main/docs/DEVELOPMENT.md)

## Animation acknowledgments

Earlier glaive animation prototypes referenced [Better Combat](https://github.com/ZsoltMolnarrr/BetterCombat), by Zsolt Molnár and contributors, as used by [Simply Swords](https://github.com/Sweenus/SimplySwords), by Sweeney / Timefall Development and contributors. The current presentation uses Minecraft's native trident rest placement and an original shoulder-driven aiming implementation; neither mod nor an additional animation library is required. [Detailed acknowledgment](common/src/main/resources/META-INF/animation-credits.txt).
