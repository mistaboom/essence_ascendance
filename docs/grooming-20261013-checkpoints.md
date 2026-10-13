# Grooming checkpoints — October 13, 2026

## Checkpoint 1: dedicated-server startup

The starting commit `b42fe3a` passes common checks and both builds but fails native NeoForge startup in all five isolated environments. Architectury registers `infuser_soulbind` separately for both directions; NeoForge rejects the duplicate identifier before creating a world. The server prompt now uses `infuser_soulbind_prompt`; the existing client confirmation retains its receipt, menu scope and server authorization.

Verification: complete starting common checks/both builds; changed codec suite279 assertions/both production builds; actual Fabric and NeoForge initialization fixtures including registered packet transports and27 Infuser authorization checks; production NeoForge vanilla fresh generation, identical independent rebuild, validation, all236 rank lookups,90 effect diagnostics, and saved restart. The restart preserved all58 balance files and performed no generation, evidence scan, solving or report export. Both owned vanilla processes saved every dimension and stopped.

Evidence root: `C:/Users/rcboo/Documents/Essence Ascendance Auxiliary Files/grooming/20261013`.

- Build: `startup-fix-build.log`; native hooks: `startup-fix-fabric-native.log`, `startup-fix-neoforge-native.log`.
- Profile lifecycle: `vanilla/baseline-startup-fixed`; four additional startup-corrected baseline environments continue separately.
- Fabric artifact: `startup-fixed-artifacts/essence_ascendance-fabric-1.0.0-beta.5.jar`, SHA256 `2365bdce0be857a410fd489862ae3e603bf53a2db40ab9ec935777154130c6b4`.
- NeoForge artifact: `startup-fixed-artifacts/essence_ascendance-neoforge-1.0.0-beta.5.jar`, SHA256 `5f885a3f6e5f9e151291cd2fec6db979498f6f066d7d7543272e145e15513928`.
- Exact checkpoint commit and promotion state: `checkpoint-log.json` at the evidence root.

No balance rebuild is required for this network-only correction. Use matching corrected client/server artifacts for the distinct prompt. Existing generated profiles, configuration, progression and paid transaction history are unchanged. This checkpoint does not claim subjective playtest acceptance or completion of the broader balance pass.

## Checkpoint 2: stored Essentium face value

Previously, changing generated ingot capacity could reject legitimately saved carriers or prevent their compression/splitting. Stored essence amounts now remain authoritative across balance rebuilds. Newly minted carriers record their ingot denomination; legacy carriers retain their exact amount. Compression requires matching family, grade and face value, and every 9:1 transformation conserves that amount. Partial declared carriers cannot become full outputs. Stable accounting bounds reject malformed or unrepresentable values without clamping them to a new balance profile.

Both production loader builds pass. Actual registered items, custom recipes, item/block save-load and Crucible consumption pass3,135 carrier checks on each of Fabric and NeoForge. Cases cover all six families and five grades, lower/higher replacement capacity, legacy/new data, mixed denominations, partial carriers, invalid schemas/types and overflow. The production vanilla server then loaded the existing generated profile byte-for-byte, passed validation/all236 rank lookups/90 effect diagnostics, saved cleanly, and restarted with all58 balance files unchanged and no regeneration. Native controlled fixtures establish hook behavior; the server run establishes saved profile lifecycle, not graphical survival playtesting.

- Evidence: `carrier-fix-build.log`, `carrier-fix-fabric-native.log`, `carrier-fix-neoforge-native.log`, `vanilla/carrier-checkpoint`, and `carrier-fix-artifact-hashes.json` under the same evidence root.
- Fabric artifact: `carrier-fixed-artifacts/essence_ascendance-fabric-1.0.0-beta.5.jar`, SHA256 `80128ec0cfad9c285fae432cb5d1e3672cc4344d93d850fe130dc7c48765aad2`.
- NeoForge artifact: `carrier-fixed-artifacts/essence_ascendance-neoforge-1.0.0-beta.5.jar`, SHA256 `5b84e52b90c48fac1bb989205039118cb96344b966e3906f158cbdb6a409abac`.
- Exact commit and promotion receipt: auxiliary `checkpoint-log.json`.

No balance rebuild is required. This is a compatibility correction; it does not change generated costs, extraction efficiency, player progression or exact paid purchase history. It does not rewrite existing carrier tags merely when reading them. Broad balance and valuation experiments remain separate.

## Continued isolated work

Experimental balance work lives on `codex/balance-grooming-20261013`, outside the IntelliJ checkout. Pending investigations include native damage/Life Steal floors, Bonus saturation, finite joint acquisition, vine cultivation evidence, salvage conservation and saved Essentium carrier compatibility. Preserve failed candidate receipts. Promote another checkpoint only after its required builds, generated profile and native checks pass.
