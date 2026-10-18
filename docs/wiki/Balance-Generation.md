# Balance generation and saved profiles

[Procedural balance](https://github.com/mistaboom/essence_ascendance/wiki/Procedural-Balance) · [Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) · [Updating and recovery](https://github.com/mistaboom/essence_ascendance/wiki/Updating-and-Recovery)

## The saved profile is the authority

`config/essence_ascendance/generated_balance.json.gz` stores validated server balance. Human TOMLs are generation inputs; reports are derived views. Existing valid saved data is loaded rather than recalculated. Missing data or an explicit rebuild invokes generation. Invalid existing data is rejected rather than silently replaced.

This development model requires fresh saves/worlds/configs when coming from the older generator and metadata formats. There is no compatibility migration. Keep historical worlds with their matching versions and inputs.

## Generation inputs and order

Global settings stay in `config/essence_ascendance.toml`. Skill and Bonus progression live in `config/essence_ascendance/skill_settings.toml` and `bonus_settings.toml`. Advanced facts and exact overrides live in `balance_overrides.toml` in the same folder.

Generation reads shared item properties, common tags, loaded standard recipe interfaces, measured native equipment/enemy attributes and configured policy. It uses bounded recipe estimates and complete material constraints. It does not traverse quests, sample loot, infer farms, inspect arbitrary scripts or run mod-specific valuation providers.

A missing Skill entry uses authored unlock placement and generated ranks/effects; a missing Bonus entry uses automatic bounds. Missing system files receive complete defaults only after a validated profile is durably published. Existing files are never overwritten. Generated settings can then be edited and explicitly rebuilt.

Generation runs synchronously during early Overworld loading. Native registry/recipe availability matters, and larger catalogs can take longer. No universal duration or heap size is promised.

## What triggers recalculation?

| Change or action | Behavior |
| --- | --- |
| Restart with a valid saved profile | Loads saved authority; no recalibration |
| Edit a human TOML | No live effect; explicit rebuild applies it |
| Add/remove/update mods or datapacks | Does not automatically replace saved balance |
| Datapack/resource reload | Does not install a newly generated profile |
| `/essence admin mappings reload` | Reinstalls saved authority; a missing profile can generate |
| `/essence admin balance rebuild` | Generates and validates a replacement using current inputs |
| `/essence debug balance validate` | Validates disk data and compares active integrity; installs nothing |
| `/essence admin balance export` | Recreates reports from active saved data; no recalculation |

Commands require permission 2. Rebuild deliberately changes the server's balance and can block the server thread. Test meaningful pack changes in staging.

## Validation and publication

The candidate must pass integrity/schema, typed runtime, reference, rank, composition, economy and transport checks. Native floors, caps and prerequisites still constrain edited Skill/Bonus settings.

A generated candidate is validated, transport-preflighted and mapping-prepared before durable replacement and installation. Atomic storage uses a temporary sibling file and requires atomic replacement. Pre-publication validation/write failure preserves the previous active authority when present.

Reports and synchronization follow publication. A report-write or client-sync failure does not undo installed balance. Check active/disk agreement, report manifest and client readiness separately. Failed/uncommitted diagnostic candidates are never evidence of successful installation.

On fresh startup with no usable profile, guarded balance-dependent systems remain unavailable. A working preview alone is not proof that validated server balance has installed.

## Saved identity and clients

Use `/essence debug balance summary` and the installed profile's metadata for the actual generator revision and integrity. Documentation of an older revision does not certify a newer file. Editing the compressed JSON directly invalidates its integrity.

Clients receive resolved runtime, player state, item yields and ore-host data through their synchronization channels. Use matching client/server builds. The Archive waits for required synchronized data rather than presenting bootstrap values as server authority.

Restore an already installed change only from a coordinated backup of the world, all human inputs, profile and matching versions. Restoring a profile alone does not undo purchases or gameplay after it was installed.

Current source: `balance/generated/GeneratedBalanceService.java`, `BalanceProfileStore.java` and `ProfileGenerationSelection.java`.
