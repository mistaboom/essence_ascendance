# Ascendance Archive foundation

The Ascendance Archive is a shipping `FullscreenScreen` consumer. It has no menu, nearby-block, transaction, or server-screen lifecycle. `AscendanceArchiveItem` calls the common-side `ArchiveClientBridge`; the client initializer installs `AscendanceArchiveScreen::open`, so the item class remains safe to load on a dedicated server.

## Ownership and APIs

- `archive.ArchiveCatalog` owns stable entry, subject, mode, and section identities. Guide and Reference documents that discuss one subject use separate entry IDs and share only the subject ID. `ArchiveDocuments` separately owns Archive-specific semantic article composition.
- `ArchiveEntry` owns localized title/summary metadata plus a semantic content provider. `SemanticDocument.searchableText()` walks the same declared blocks used by rendering, so Search does not render pages or maintain a duplicate article database.
- `client.ui.content.SemanticDocument` declares headings, paragraphs, ordered steps, callouts, illustrations/captions, stat rows, requirements/status rows, tables, and related/previous/next links.
- `ContentViewport`, `EntryListView`, and `IllustrationView` own reusable wrapping, measurement, clipping, visible content, scrolling, focus/input, and hit testing. Archive pages declare content and targets only.
- `ReadOnlyDataTable<R>` and `ReadOnlyDataTableView<R>` are shared typed data-view components. A column declares a typed extractor, localized presentation, alignment, sizing, and optional comparator. The view owns stable row-key selection, sort direction indicators, natural unsorted order, measured columns, clipping, keyboard/wheel overflow, scrollbars, and visible-row rendering. The live Skill Rank Reference uses this component with values from `SkillBalanceRuntime`; the future yield browser should supply its own typed rows and columns to the same API.
- `ItemIllustrationRenderer` resolves a registered item on every draw. Machine and Focus figures therefore use their real item rendering paths and current assets. `IllustrationView` owns fit, caption, clipping, pose, and flush boundaries. It creates no block entity, fake menu, world, or operational VFX. Client resource reload invalidates the shared machine and Focus mesh caches.

The ordinary fullscreen adapter owns Minecraft's background hook, so the vanilla background blur cannot run over the Archive content. See `docs/fullscreen-ui.md` for the native render-order contract and both-loader regression coverage.

Entry lists and tables reveal arrow-key selection through `FullscreenScroll.ensureVisible`. Only primary clicks change selection or sorting. Tables cache their sorted row order, preserve input order for equal values, and expose full styled truncated values through `overflowTextAt`. `ContentViewport.renderTooltips` presents those values in the host's foreground tooltip pass after all content clips are released. Embedded tables yield wheel input to the surrounding article when they reach the requested boundary; changing documents clears the old table focus.

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

`archiveFoundationInvariants` covers catalog topology, shared-subject/distinct-document identity, semantic/search content, styled Components, typed numeric/text sorting, disabled-sort natural order, measured widths, visible-row windows, stable table selection, independent navigation restoration, missing-target fallback, bounded history, item registration, and the generated book-texture model. `dormantGuidebookInvariants` covers eligibility, receipts, retry, inventory/drop behavior, creative full-inventory safety, reset rearming, and admin recovery.
