# Adaptive Latent Ore

## Minecraft 26.1.2 presentation boundary

World blocks and item icons share the host-model remapping in `LatentOreModels`.
Native host geometry, UVs, shading and texture pixels are preserved. Each vertical
face selects an ore-mask orientation from its actual UV direction, including
quarter-turns and half-turns; only the ore and transition masks rotate. The top
and bottom retain their native orientation. Source artwork stays unchanged.

The item adapter also appends the saved host state to Minecraft's tracked model
identity. The 26 GUI atlas caches rendered icons by that identity, so changing
quads alone would reuse an image when two states of the same registered host item
differ, such as a pillar axis. The separate item IDs distinguish stone, deepslate,
netherrack and End Stone, while the state identity preserves further variants.

The isolated NeoForge OpenGL fixture checks native world/item composites, distinct
GUI identities, repeated-host cache reuse and upright vertical overlays across
32 block-model seeds, while asserting native positions and UVs remain unchanged.
It is a loaded-resource fixture, not a joined gameplay session or JEI acceptance.

## Initial switch from placeholder ores

The transition notes below describe the earlier development format. Current pack profiles use `generated_balance.json.gz`; follow [pack compatibility](pack-compatibility.md) for current storage, explicit rebuilds and preservation of existing worlds and human inputs.

Delete the old development world and create a new one. All four placeholder ore
registrations and their worldgen features were removed. There is no save migration
or retrogen. No existing world was deleted by this implementation.

If the world already uses the adaptive ore implementation, keep it. The later
balance-driven supply update requires only regenerating the derived profile below;
its new ore distribution applies to newly generated chunks.

Current development runs use `<loader>/versions/<minecraft>/run`; see
[the development directory map](DEVELOPMENT.md#current-run-folders-and-intellij-entries).
Valid saved profiles are reused. When a rebuild is intended, use the explicit
`/essence admin balance rebuild` command in that target's running game. Preserve
human settings and existing worlds; the historical profile-deletion instructions
from the placeholder transition do not apply to current runs.

New worlds save `data/essence_ascendance_latent_ore_hosts.dat`. Keep that file when
changing generation policy: it retains the resource catalog needed to display ore
already mined under the new implementation. It contains named hosts, not old-save
conversion information.

## Selection and distribution

Both loaders schedule one `adaptive_latent_ore` placed feature once per biome. The
feature uses the actual generating server dimension to select one policy. Biome
identity never selects the ore's substrate or distribution.

The supported automatic evidence is a `NoiseBasedChunkGenerator`'s configured
`defaultBlock`, plus the actual vanilla basal deepslate surface-rule branch where
present. The basal rule is checked through Minecraft's codec; a noise-settings ID
alone does not establish that the layer exists. Ordinary vanilla terrain selects:

| Dimension | Primary hosts | Vein size | Attempts/chunk | Y interval | Air discard |
| --- | --- | ---: | ---: | --- | ---: |
| Overworld | stone, deepslate | 9 | 12..24 | -48..64 | 0 |
| Nether | netherrack | 9 | 12..24 | 16..112 | 0 |
| End | end stone | 9 | 12..24 | 0..80 | 0 |

The balance generator starts with 16 attempts, increasing to 24 when an Essence
category lacks usable early sources and reducing to 12 only when every category
has broad supply. Other supported dimensions inherit the generated Overworld
abundance over the intersection of generator and dimension heights. No dimension
name is treated as evidence of progression or scarcity. Every actual replacement must
match an eligible host block and retain its encountered state. Granite, diorite,
andesite, soil and other incidental deposits are skipped in the Overworld. No
chunks are sampled or force-loaded for discovery.

Selection is cached per live level and policy, with invalidation for world
shutdown, resource reload and profile changes. Logs identify the dimension,
automatic/overridden status, selected hosts, evidence and rejection reasons.
The complete balance transaction loads or generates once when the Overworld is
available, before initial spawn search can generate chunks. There is no temporary
ore policy that changes after spawn. Evidence providers must not generate chunks
or mutate a world during this phase; unsafe exploration-map trade sampling is
excluded with a diagnostic.

Supply uses final post-conservation routed Essence yields and the existing source
evidence. Only known, sufficiently confident, reachable external ENTRY/EARLY
resources and accessible source prerequisites count. Manufactured/conditional
block breaking, zero yields, late gates and recipe-only paths cannot establish
abundance. Source events and exact reversible material families jointly cap
diversity: each counted route must use its own event and material family. Two
geological forms of the same ore do not count as two fuels. Four weighted
source families cover a category, and the least-covered category controls supply.
Analysis stops once a category reaches full coverage: effective scores are capped
at four, and reported family counts describe the independent routes used to
establish that coverage.
These weights express cautious policy, not measured farm output. Unsupported
processed-only evidence conservatively requests more ore.

The automatic floor is 12 size-9 attempts with default air discard zero, compared
with vanilla iron_middle's 10 size-9 attempts. This is an accessible construction
budget, not a guarantee of blocks per chunk or mining time. Ore still requires
eligible terrain. Extra Latent supplies conversion carriers; it cannot fix a pack
with no usable external Essence fuel at all, which reports flag separately.

The generated profile records the decision under `validation.latentOre`, with
`latent_ore_supply.csv`, `latent_ore_worldgen.csv`, and `latent_ore_policy.csv` in
the existing reports directory. Rebuild after changing pack evidence or inputs.
Existing profiles from before this supply policy require regeneration; keep the
human TOML inputs. Existing adaptive worlds may be retained, but only new chunks
use changed generation. No retrogen is performed.

## Configuration

Use the existing `config/essence_ascendance.toml` input and balance rebuild lifecycle.
Optional `[latent_ore.overworld]`, `[latent_ore.nether]` and `[latent_ore.end] tables
accept `enabled`, `vein_size`, `veins_per_chunk`, `min_y`, `max_y` and
`discard_chance_on_air_exposure`. These are baseline inputs: enabled nonzero
distributions have attempts scaled by balance, with minima of 12 attempts and
size 9. Explicit zero/disabled baselines remain off.
`[latent_ore].automatic_dimensions` controls
automatic generation in other dimensions.

An exact dimension entry owns that dimension's policy. Its `enabled` value takes
precedence over the automatic switch and baseline enabled value. Nonempty `hosts`
replace automatic host selection; unknown/unsafe explicit hosts are rejected
without falling back to another host. Empty/omitted hosts request discovery.
Explicit dimension distribution fields inherit input baselines regardless of TOML
table order and remain authoritative, bypassing automatic scaling and its floor.
Each dimension may appear once; entries never add another generation pass.

```toml
[latent_ore]
automatic_dimensions = true

[[latent_ore.dimension]]
dimension = "example:caverns"
enabled = true
hosts = ["example:upper_rock", "example:lower_rock"]

[[latent_ore.dimension]]
dimension = "example:void_realm"
enabled = false
```

No mod named `example` is required or specially supported; these entries illustrate
the optional escape hatch. Existing exact `/runtime/worldgen/...` balance
overrides remain the final resolved-value layer. After editing generator inputs,
use the existing `/essence admin balance rebuild` command. Only subsequently
generated chunks receive the changed distribution.

## Representation and gameplay

Every host uses the one `essence_ascendance:adaptive_latent_ore` world block. Its
non-ticking block entity persists `latent_ore_host` as a registry name and state
properties. Standard update tags/packets synchronize the same data. Inventory
items have four fixed registered IDs, one per known vanilla primary terrain layer:
`essence_ascendance:stone_latent_ore`, `essence_ascendance:deepslate_latent_ore`,
`essence_ascendance:netherrack_latent_ore`, and
`essence_ascendance:end_stone_latent_ore`. These fixed-host aliases carry the exact
state in standard `minecraft:custom_data`, so orientation and other properties
survive save/load, networking and stack comparison. A plain `/give` of one of
these items resolves its representative state without custom data.

The aliases are registered through the existing common Architectury item
registry. No loader scans the block registry to invent additional variants.
Structural solidity permits the shared ore representation to carry a host; it
is not evidence that logs, building blocks, incidental deposits or every solid
modded block are primary dimension terrain.

Actual modded terrain still comes from the existing per-world geological
discovery and exact profile overrides. Those settings become authoritative after
the static item registry freezes, so dynamically selected hosts retain their
namespace-qualified state in the existing
`essence_ascendance:adaptive_latent_ore` carrier. This preserves their placement,
world appearance, drops, pick-block and catalog entries. Separate command IDs
for modded hosts remain pending an authoritative earlier host declaration; the
code does not guess from block names or manufacture an item for every block.
The same adaptive carrier continues to read saved legacy stacks.

The isolated headless native fixture checks the exact four-item registration,
absence of the reported log/slab/bamboo aliases, the real vanilla dimension
selection, native placement/pick/Silk Touch, item tags and standard serialization.
Two fixture modded blocks exercise namespace-distinct component carriers and
explicit catalog additions without speculative registry entries. These are
server and catalog checks, not proof of final GUI appearance.

`LatentOreHost` is the common identity boundary for worldgen, block entities, items,
Silk Touch, pick-block, creative variants and Gathering's synthetic loot context.
Invalid data produces an unplaceable, clearly labeled item rather than an invented
host. The intentional default presentation stack is a generated stone-host ore.

All hosts share Latent Ore behavior: pickaxe harvesting, fixed hardness, one native
loot table retaining raw drops/Fortune/Silk Touch, common ore tags and two
tag-based cooking recipes. The registered host items enter
`essence_ascendance:latent_ores`, `c:ores` and `c:ores/latent` before native tag
resolution; those additions respect data packs that replace a tag and remove its
original adaptive item. The recipe advancement uses the same ore item tag.
Host-specific machinery or ticking behavior is never
copied. Pistons cannot move the host-carrying block. The generated placement policy
remains independent of the item IDs; Gathering supplies the exact primary source state when
manufacturing a Silk Touch drop. Creative Natural Blocks/Search access includes
the baseline hosts and the synchronized selected-host catalog.
When an existing generated balance profile is loaded, a saved adaptive-ore payout
also applies to new fixed-host IDs that have no saved row. An explicit saved
host-item route, including a zero route, remains authoritative. The profile itself
is not rewritten and needs no rebuild for the item-ID change.
The catalog revision participates in native `ItemDisplayParameters.needsUpdate`,
so both Minecraft's global cache and independent per-tab parameter caches rebuild
when the selected hosts change. It does not depend on any cache-owning mod.

JEI uses that same creative variant list and distinguishes host state properties
when browsing and bookmarking. It removes unused pre-registered aliases from its
ingredient list, then refreshes selected hosts even if the server's catalog arrives
after JEI starts. Recipe lookups share the ore item tag, so every host finds the
common smelting/blasting recipes. The four baseline hosts remain browsable in
flat/test worlds too; additional selected and retained hosts appear automatically.
This menu change needs only a client restart, not another world/profile reset if
already using the adaptive ore implementation.
For JEI's creative-order sorting, explicitly registered Essence variant families
retain their first subtype's position when later subtypes arrive. Other sorting
stages and unrelated ingredients keep JEI's own behavior. Runtime additions mark
the native sort dirty; they do not force an additional resource or search rebuild.

## Rendering lifecycle

The original `latent_ore_base.png` and `latent_ore_transition.png` are copied into
normal source resources under `textures/block/latent_ore/`. Distributed jars do
not access the auxiliary folder.

At the block atlas's pre-stitch boundary, the client resolves only requested hosts'
ordinary blockstate variants and resource-backed models. Minecraft's `BlockModel`
parent and texture-variable resolver handles model indirection. Each distinct face
image gets one composited sprite. The alpha-weighted host average `C`, rounded once
per channel, supplies its color. Every host uses the same host-relative rule: the
mask's brightness relative to its own average supplies shading, while `C` supplies
hue and saturation. The colorizer normalizes the visible transition to a luminance
16 levels above the host average. This small consistent separation leaves the border
visible on colored hosts and avoids a disproportionately bright border on dark hosts.
It consults no block or dimension IDs. The mask retains its alpha and internal
shading. Composition is untinted ore over the transition over
the original host. Whole masks scale to the host
resolution with nearest-neighbor sampling. Supported host images are fully opaque.

Composition occurs before atlas stitching and model baking. It needs no baked
model or GPU access. Later, the shared `LatentOreModels` remaps the native baked
quads' UVs to the finished sprites, preserving orientation, face textures, geometry,
culling and lighting. Models/quads are cached by host state/native quad. Atlas
ownership closes generated images on reload; model application clears stale
sprite references.

Fabric passes immutable render-data snapshots through its normal baked-model API.
NeoForge snapshots the BE's `ModelData` before worker meshing. Items use their own
saved components. No block-entity renderer or per-ore ticker exists. Debris uses
the saved host, with a bounded two-second removal snapshot for destruction packets
that arrive after the BE is gone; this cache contains no textures or models.

Vanilla hosts are prepared during normal startup. At connection/profile changes,
the server sends selected and retained host IDs. A changed catalog requests one
coalesced resource reload before the new sprites can be used. Encountering or
placing ore never rebuilds an atlas. Connection guards discard stale reload
completion work.

## Reuse and extension boundaries

- Extended the existing Latent Ore feature registration, loader biome injection,
  worldgen settings, TOML validation and generated balance lifecycle.
- Reused vanilla `OreFeature` origin/height/exposure behavior. Its sphere traversal
  is adapted in `AdaptiveOreVein` because vanilla's direct section write cannot
  initialize host block entities.
- Extracted `TexturePixels` from `EssenceBlockTextureGenerator`; both Essentium's
  build tool and runtime ore composition call it. Image I/O remains in separate
  build/runtime adapters. All 61 preexisting generated Essentium files were
  byte-identical after extraction.
- Reused the existing runtime identifier JSON adapter through
  `ResourceLocationJsonAdapter`, now shared with saved balance-input documents.
- Kept `ProceduralNaturalBlockIndex` unchanged. Its vegetation, fluid, surface and
  deposit evidence remains useful for valuation, but cannot authorize primary-rock
  replacement. There is no second broad natural-block scanner.

Discovery extensions belong in `PrimarySubstrateDiscovery`; supported resource
cases belong in `LatentOreSprites` and the shared baked-model boundary. Persistent
identity and gameplay do not depend on either boundary.

## Current limits and manual acceptance

Unknown/custom non-noise generators, including flat generators, need explicit
primary hosts. Extra geological layers beyond the recognized vanilla basal layer
need explicit evidence/overrides. Animated images, tint-index faces, multipart or
custom-loader models, non-cube geometry, transparent host images and cropped/tiled
face UVs are deliberately rejected with a log diagnostic and generated
magenta/black diagnostic ore. Connected textures, shaders and mod-specific renderers
are not claimed supported. No external modpack was installed or tested.

After the reset above, verify on **both loaders**:

1. Generate fresh Overworld/Nether/End chunks. Check host-selection logs, rarity,
   stone/deepslate transitions and untouched granite/dirt/bedrock pockets.
2. Compare all six ore faces with native hosts, including deepslate orientation;
   inspect lighting, culling, breaking overlays and particles for seams/pinholes.
3. Mine with ordinary/Fortune/Silk Touch tools. Check raw drops, distinct stacks,
   pick-block, inventory/drop/held rendering and smelting/blasting.
4. Place Silk Touch ore in another dimension, save, exit and reload. Confirm the
   original host/state remains. Repeat with another client on a disposable server.
5. Reload resources, including a static higher-resolution host replacement. Verify
   native face textures change and ore artwork remains untinted.
6. Try a small local custom noise dimension with basalt as its default block and
   different heights; then test exact disabling/host overrides in new chunks.

## Initial adaptive implementation validation

From the project root, these commands completed successfully:

```powershell
.\gradlew.bat :common:test :fabric:build :neoforge:build --console=plain
.\gradlew.bat :common:primarySubstrateInvariants :common:latentOreHostInvariants :common:latentOreResourceInvariants :common:texturePixelInvariants :common:balanceConfigInvariants :common:balanceLifecycleInvariants :fabric:build :neoforge:build --console=plain
.\gradlew.bat :common:balanceDocumentInvariants :neoforge:runClient -I build/latent-ore-smoke/init.gradle --console=plain
.\gradlew.bat :fabric:runClient -I build/latent-ore-smoke/init.gradle --console=plain
.\gradlew.bat :fabric:build :neoforge:build --console=plain
```

The full common suite and both loader builds passed. After final review changes,
the focused run passed 47 primary-host/config assertions, 57 host-data/item codec
and content assertions, 12 resource-model assertions, 10,133 pixel assertions,
60 balance-config assertions and 28 lifecycle assertions. The shared balance
document serialization/integrity suite also passed. The host tests exercise real
Minecraft state/item codecs; source-shape checks for native pick/placement/loot
routing are not claimed to be simulated player mining.

Both actual development clients completed resource loading and atlas upload using
isolated `build/latent-ore-smoke/{fabric,neoforge}` game directories. Each logged
`Prepared Latent Ore sprites: 4/4 hosts, 5 distinct face/particle textures`. Both
clients were closed normally. No game world was created or opened for these smoke
checks. In-world generation, multiplayer synchronization, mining/placement,
resource-pack visual comparison and appearance after world reload remain the
manual acceptance items above.

The temporary Gradle init script only changes the two client run directories; it
lives under ignored build output. Validation logs are in
`build/latent-ore-validation.log`, `build/latent-ore-final-validation.log`, and the
two isolated clients' `logs/latest.log`. Source artwork hashes matched the auxiliary
originals. `git diff --check` passed. These checks do not substitute for native
gameplay and visual acceptance.

## Balance-driven supply and adaptive-color validation

The focused validation and both loader builds passed:

```powershell
.\gradlew.bat :common:texturePixelInvariants :common:latentOreBalanceInvariants :common:primarySubstrateInvariants :common:balanceConfigInvariants :common:balanceLifecycleInvariants :common:balanceBiologicalSourceInvariants :common:balanceTradeInvariants :common:balanceDocumentInvariants :common:balanceReportInvariants :fabric:build :neoforge:build --console=plain
```

This passed 10,136 pixel, 185 ore-supply, 46 primary-substrate, 60 configuration,
33 lifecycle, 58 biological-source, 28 trade and 47 report assertions, plus the
balance document suite. Fixtures cover missing categories, monopolies, duplicate
drop events, reversible material forms, inaccessible sources, monotonic supply,
explicit overrides, neutral 50% tinting and host-relative chromatic correction with
preserved mask shading.
The complete generated-profile integration suite also passed 3,215,827 checks,
including saved-evidence regeneration and the new supply/report diagnostics.
The full runtime balance suite passed its deterministic generation, strict codecs,
exact overrides, category supply and five adversarial profile cases:

```powershell
.\gradlew.bat :common:balanceIntegrationInvariants :common:balanceRuntimeInvariants :fabric:build :neoforge:build --console=plain
```

After final review tightened joint source/material matching, this focused run and
both final loader builds passed:

```powershell
.\gradlew.bat :common:latentOreBalanceInvariants :common:texturePixelInvariants :common:balanceReportInvariants :fabric:build :neoforge:build --console=plain
```

The ore-supply suite now passes 293 checks, including overlapping-output
bottlenecks, weighted reassignment, order independence, monotonic additions and
20 small sparse graphs checked against exhaustive enumeration. Pixel and report
checks still pass. Read-only replay of each smoke world's saved evidence through
the final matcher confirms all six categories reach coverage 1 and retain the
0.75 multiplier; neither saved profile was modified. The final replay took 149 ms
for Fabric evidence and 19 ms for NeoForge evidence in the warmed process.

Fresh normal-preset worlds were created and loaded on both Fabric and NeoForge in
isolated `build/latent-ore-balance-smoke/{fabric,neoforge}` directories. In both,
the complete generated balance installed before initial spawn preparation and
the first ore policy resolution; the player joined and received the generated
profile. Each client closed normally and saved all dimensions. The vanilla
environment selected the abundant-source minimum of 12 size-9 attempts per chunk
with no air discard. All four baseline hosts and five distinct textures prepared.

Native inspection of 32 complete saved Fabric chunks found 2,103 adaptive ore
blocks and exactly 2,103 matching host block entities: 1,122 deepslate and 981
stone. This verifies actual initial generation and persisted host data; it is not
a statistical distribution benchmark. Neither smoke opened a user world or
changed user configuration. Visual appearance, multiplayer and external modpack
acceptance remain manual checks.

Logs are in `build/latent-ore-supply-validation.log`,
`build/latent-ore-supply-integration.log`, `build/latent-ore-supply-final.log`,
and the isolated smoke directory's `launch.log`, `launch-neoforge.log`,
`chunk-inspection.log`, `saved-supply-analysis.log` and client
`logs/latest.log` files. The fixture and inspection utilities remain ignored
build output.
