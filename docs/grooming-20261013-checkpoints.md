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

## Continued isolated work

Experimental balance work lives on `codex/balance-grooming-20261013`, outside the IntelliJ checkout. Pending investigations include native damage/Life Steal floors, Bonus saturation, finite joint acquisition, vine cultivation evidence, salvage conservation and saved Essentium carrier compatibility. Preserve failed candidate receipts. Promote another checkpoint only after its required builds, generated profile and native checks pass.
