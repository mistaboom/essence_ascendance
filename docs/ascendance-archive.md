# Ascendance Archive foundation

The Ascendance Archive is a shipping `FullscreenScreen` consumer. It has no menu, nearby-block, transaction, or server-screen lifecycle. `AscendanceArchiveItem` calls the common-side `ArchiveClientBridge`; the client initializer installs `AscendanceArchiveScreen::open`, so the item class remains safe to load on a dedicated server.

## Ownership and APIs

- `archive.ArchiveCatalog` owns stable entry, subject, mode, and section identities. Guide and Reference documents that discuss one subject use separate entry IDs and share only the subject ID. `ArchiveDocuments` separately owns Archive-specific semantic article composition.
- `ArchiveEntry` owns localized title/summary metadata plus a semantic content provider. `SemanticDocument.searchableText()` walks the same declared blocks used by rendering, so Search does not render pages or maintain a duplicate article database.
- `client.ui.content.SemanticDocument` declares headings, paragraphs, ordered steps, callouts, compact registered-item icons, illustrations/captions, stat rows, requirements/status rows, tables, and related/previous/next links.
- `ContentViewport`, `EntryListView`, and `IllustrationView` own reusable wrapping, measurement, clipping, visible content, scrolling, focus/input, and hit testing. Archive pages declare content and targets only.
- `ReadOnlyDataTable<R>` and `ReadOnlyDataTableView<R>` are shared typed data-view components. A column declares a typed extractor, localized presentation, alignment, schema width/weight, and optional comparator. Equivalent schemas retain identical column geometry regardless of row text. Values wrap, and every row in one table uses the uniform height required by that table's longest wrapped value. The shared rank-column width prevents compact rank headers from being squeezed. The view also owns stable row-key selection, sort direction indicators, natural unsorted order, clipping, keyboard/wheel overflow, scrollbars, and visible-row rendering.
- `ItemIllustrationRenderer` resolves a registered item on every draw. Machine and Focus figures therefore use their real item rendering paths and current assets. `IllustrationView` owns fit, caption, clipping, pose, and flush boundaries. It creates no block entity, fake menu, world, or operational VFX. Client resource reload invalidates the shared machine and Focus mesh caches.

## Shared Skills and Bonuses presentation

The Reference catalog enumerates `SkillRegistry` and `EssenceStatRegistry` directly. Every registered Skill and Bonus remains browsable regardless of ownership, Nexus visibility, or current tier. The two side-panel lists contain only real registry entries and sort their localized titles alphabetically.

- `PresentationMetric` and `PresentationBehavior` provide stable semantic identities, localized labels, native readers, applicability, units, conversion, and precision. Skill prose and rank-table effects consume the same `SkillTooltipRegistry.Binding`; no formatted tooltip is parsed.
- `SkillPresentationData` owns immutable rank projections, native generated costs/effects, rank-specific tier/prerequisite/requirement gates, choice/replacement relationships, and optional committed-player eligibility. Nexus effect prose, requirement descriptions, Skill names, and node costs now use these same bindings.
- `BonusPresentationData` owns typed stat metrics, synchronized/current values, pure staged projections, authoritative checkpoint rows, start/completion availability, and next meaningful changes through `BonusTrackCurve`. `BonusTooltipPresentation` is a concise Nexus renderer over this model.
- `PresentationContext` explicitly separates installed synchronized runtime data, synchronized player state, and general catalog context. Loading, unavailable, context-required, and real zero states remain distinct. It never calls `EssenceConfigManager.runtime()`, so bootstrap defaults cannot be displayed as server authority.
- Article and Search caches key on runtime identity, player revision/readiness, connection epoch, and language identity. Profile/player/language/connection changes rebuild semantic content; no preview mutates ranks, installs a temporary profile, or performs a transaction.

Skill articles show all rank costs/effects and rank-specific gates, activation policy, choices/exclusions/replacements, plus current/next ownership and eligibility when synchronized state is ready. Bonus articles show the resolved purchase style, genuine tier checkpoints/costs/caps/benefits, start/completion tiers, and current/next player progression.

Skill and Bonus titles use their canonical Essence-category colors in both lists and articles. Category facts and Essence amounts reuse the same hues, while every concrete tier reference uses its canonical tier-metal color. Cost cells contain only the number because their localized headers already establish the unit.

The ordinary fullscreen adapter owns Minecraft's background hook, so the vanilla background blur cannot run over the Archive content. See `docs/fullscreen-ui.md` for the native render-order contract and both-loader regression coverage.

Entry lists and tables reveal arrow-key selection through `FullscreenScroll.ensureVisible`. Only primary clicks change selection or sorting. Tables cache their sorted row order, preserve input order for equal values, wrap styled row values instead of truncating them, and expose any compressed localized header through `overflowTextAt`. `ContentViewport.renderTooltips` presents header overflow in the host's foreground tooltip pass after all content clips are released. Embedded tables yield wheel input to the surrounding article when they reach the requested boundary; changing documents clears the old table focus.

## Navigation and Search contracts

`ArchiveNavigator` stores canonical `ArchiveLocation` values, never screens or runtime objects. Its bounded history is limited to 32 locations. Guide and Reference independently remember section, entry, section-tab window, entry-list scroll, and article scroll. Search independently remembers query, selected result, and result scroll. First use resolves to Guide → Beginning; missing sections, entries, or results reconcile safely.

`ArchiveNavigationState` uses `UiNavigationMemory` and captures its session token when the screen opens. `ClientPacketDispatch` clears the Archive session on a connection boundary, and a late screen removal cannot write into the new session. `ArchiveLocation.YieldBrowser` reserves a typed future destination without adding a current browser screen.

The current Search foundation edits and remembers a query, derives simple matching results directly from catalog metadata and semantic content, and remembers a stable selected result. Clicking a result opens it directly; keyboard arrows move selection and Enter/Space opens it. Search uses shared gap-aware bands so the field and result list have identical horizontal bounds and a consistent gutter. Ranking, filters, highlighting, and expanded search behavior remain later work.

Every explicit location transition captures current offsets and invalidates the displayed location, including Search's Open action and revisits to the same entry. Selecting another entry within a section preserves its list offset and focus; a mode/section change starts a new shared interaction scope. Missing/null catalog targets resolve safely.

## Item and delivery

`essence_ascendance:ascendance_archive` is a real, non-stackable item with a normal generated item model. Its current `layer0` is `minecraft:item/book`; replacing the art later is resource-only work.

`DormantGuidebookService` retains the existing persisted `dormant_guidebook_received` receipt for save compatibility. Its former placeholder factory is replaced by `createArchive()`. Eligibility, successful receipts, failed-delivery retries, full-inventory drops, progression-reset rearming, and explicit operator replacement all use the same established pipeline.

Players whose old receipt was consumed by the former ordinary-book placeholder are intentionally not granted a second item on login. An operator can recover the real Archive without clearing progression or the old receipt:

`/essence admin player guide give [player]`

The command uses the same inventory/drop fallback and records a receipt only after successful delivery.

## Validation

`archiveFoundationInvariants` covers catalog topology, shared-subject/distinct-document identity, semantic/search content, styled Components, typed numeric/text sorting, disabled-sort natural order, measured widths, visible-row windows, stable table selection, independent navigation restoration, missing-target fallback, bounded history, item registration, and the generated book-texture model. `referencePresentationInvariants` covers all registered Skills/Bonuses, native rank/checkpoint parity, rank-specific gate fixtures, semantic units/precision, pure projections, readiness/profile changes, localization, current-player context, and an isolated changed-value article fixture. `dormantGuidebookInvariants` covers eligibility, receipts, retry, inventory/drop behavior, creative full-inventory safety, reset rearming, and admin recovery.
