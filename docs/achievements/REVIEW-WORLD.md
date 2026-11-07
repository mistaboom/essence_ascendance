# Achievement review world

`tools/verification/achievement_review_world.py --label <new-label>` creates a
fresh disposable native 1.21.1 Fabric world under
`build/achievement-review-world/<new-label>`. Run with the local Python interpreter
after the final production manifest is present. It uses the catalog-selected
Java 21 runtime and exact manifest production jar, and never opens an ordinary
world or writes to installed Prism instances.

The script builds a review plaza using native commands, stops and saves, sets the
native singleplayer Creative/cheats/spawn/name defaults, then cold-loads and checks
the saved stations, guide, equipment chest and routes. It leaves server logs and
a receipt, plus these review deliverables:

- `deliverables/Essence-Ascendance-Achievement-Review-Prism.zip`: import into a new
  Prism instance; includes exact production mod, catalog-pinned dependencies,
  loader metadata, generated config and the ready save.
- `deliverables/Essence-Ascendance-Achievement-Review-Save.zip`: the save alone for
  a matching 1.21.1 Fabric installation.
- `deliverables/README.md`: user-facing installation, areas, command and reset guide.
- `deliverables/review-receipt.json`: hashes, native logs, target and verification scope.

The world includes native command-block buttons for:

```text
/advancement grant @s from essence_ascendance:achievements/a022
/advancement revoke @s from essence_ascendance:achievements/a022
```

The tree contains exactly the 69 selected achievements. The compatibility
`water_skiing` resource and other native trees are excluded. `only` can replace
`from` to preview an individual ID's toast, for example `achievements/a072`.
Bulk grant is display wiring evidence. Revoke changes completion, not durable
partial achievement history. Reimport the untouched save for a complete reset.

Machinery displayed in the plaza was created by console. For genuine operator,
builder and success testing, the guide instructs players to take blocks from the
supplies and place/use their own. The review supplies and setup commands do not
claim a successful survival action. No production command or skill was added.

This artifact's native loading and station checks are separate from the all-rule
achievement fixtures, real-client visual review and multiplayer verification.
[ACCEPTANCE.md](ACCEPTANCE.md) records the accepted review kit, compact hashes
and verification scope. Build outputs are disposable; regenerate a fresh label
when a prior output has been cleaned.

## Review boundaries

The native tab is **Essence Ascendance**; ID 22's achievement title remains
**Equivalent Exchange**. Vanilla discovery is unchanged. Minecraft shows tab
buttons only with multiple visible tabs; obtaining a crafting table in Survival
reveals its own tab. No vanilla advancement progress is pre-granted.

The flat review Overworld has no natural ore generation. Test **Under Pressure**
(ID 126) in a separate normally generated world. The prepared machines and gear
are convenient review supplies; genuine builder/operator challenges require the
player actions described in the generated guide. Synthetic grant/revoke is useful
for display and toast review, not proof of historical gameplay.

A new ZIP is a fresh review instance, not an automatic update to an existing
Prism import. Close Minecraft before replacing a mod jar in a matching imported
instance. Reimport the untouched save when a complete partial-history reset is
needed. Never use this generator as a way to rewrite a normal player world.
