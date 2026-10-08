# Wiki publication verification — 2026-10-08

Live wiki: https://github.com/mistaboom/essence_ascendance/wiki

## Scope and publication

- Reviewed implementation: `59e27446afbb6c9e73be38d0d407915fe15354fa`.
- Published mod scope: `v1.0.0-beta.1`, release source `9caef8477c8aa95a5f61b7fc008d1659e2445e79`, Minecraft 1.21.1, Java 21, Fabric and NeoForge.
- Release-to-implementation comparison found no changes to command/gameplay/config/resource/integration/test implementations. Later changes concern descriptions, documentation, branding and build tooling; released jars were not independently reproduced.
- Canonical documentation was committed to main in `3ab8e32`; the live rendering repair is in `73ab3ac`.
- The wiki was uninitialized. GitHub's authorized first-page flow created the complete reviewed Home page in `296b456`; that initial history was preserved.
- The separate wiki checkout's inspected default branch is `master`. Full publication is `a1520ed`, followed by diagram readability repair `6c06ee5`.
- Both repositories were pushed normally. No existing wiki pages were deleted. The maintained set is 19 content pages plus `_Sidebar.md` and `_Footer.md`.

## Independent source and documentation review

- Rechecked the production command-registration entry point, all six command classes and shared permission/target/identifier handling against the inventory and command pages. Reviewed handler effects and supporting services, including purchases, reset/grants, milestone overrides, held-item mutation, Attunement, profile operations and diagnostic reconciliation.
- Compared policy keys/defaults with bundled TOML and typed ranges, enum choices, budget-sum tolerance, generation assumptions, Attunement bounds and ore distribution validation.
- Compared all 47 factual override keys with parser types, enums, bounds and contradiction checks; checked priority resolution, exact-value restrictions and processing-time units.
- Rechecked saved-profile selection, revision/integrity validation, transport preflight, durable replacement/install order, post-install report/sync failure behavior and recipe-reload boundaries.
- Rechecked Archive gift/replacement/opening/navigation/search and the active server presentation boundary; reviewed integration loader/version/readiness limits and released scope.
- Corrected the maximum-tier Attunement query description: queries show completed seals at Transcendent without requiring a next chapter. Updated the audit as well as player/diagnostic pages.

## Focused checks

Static checks found no missing internal destinations or heading anchors across **127 cross-page links**, no missing files among **50 unique implementation/maintenance references**, and complete sidebar coverage for all 19 content pages. Source references pinned to a commit were checked against Git objects.

The static key-coverage comparison covered **36 bundled policy/root keys**, **47 fact keys** and **9 ore-parser keys**. All **five TOML examples** parsed with Python's standard TOML reader; their keys/types/ranges and budget constraints were compared with the actual bounded parser and typed validators. This was not an execution of the Java parser or balance generator.

The audit-to-page syntax comparison found no missing ungrouped command forms. Consolidated alternatives, help endpoints, optional targets, numeric ranges and examples were also reviewed against registrations. Privacy/placeholder scans found no matches. Staged whitespace checks passed.

## Live verification

Opened the actual published GitHub wiki and verified Home, sidebar/footer, Administration Commands, Configuration, Procedural Balance, Balance Generation and Players and the Archive. Checked rendered command tables and warnings, configuration tables and highlighted TOML, heading-anchor navigation, active-server guidance and both Mermaid diagrams.

The first overview diagram rendered too small in the wiki column. Changed its direction from horizontal to vertical in the canonical source, committed/pushed both repositories, and rechecked the live diagram's readable labels. The lifecycle diagram rendered without a syntax error.

Compared all 21 maintained Markdown files between canonical sources and the wiki checkout, and checked remote branch commits and clean working-tree state after publication. The manual republishing procedure is in [PUBLISHING.md](PUBLISHING.md), with a reader-facing summary on the live Version and Maintenance page.

## Verification limits

These are source, documentation, Git synchronization and live rendering checks. No Minecraft instance was launched, no gameplay/admin/test command was executed, no balance profile was rebuilt, and no installed instance, world, gameplay code, release jar, platform token or unrelated project setting was changed. This publication establishes no new gameplay benchmark, fresh Java test result, universal mod compatibility or perfect automatic balance.
