# Ascendance Archive foundation

The Ascendance Archive is a shipping `FullscreenScreen` consumer. It has no menu, nearby-block, transaction, or server-screen lifecycle. `AscendanceArchiveItem` calls the common-side `ArchiveClientBridge`; the client initializer installs `AscendanceArchiveScreen::open`, so the item class remains safe to load on a dedicated server.

## Ownership and APIs

- `archive.ArchiveCatalog` owns stable entry, subject, mode, and section identities. Guide and Reference documents that discuss one subject use separate entry IDs and share only the subject ID. `ArchiveDocuments` separately owns Archive-specific semantic article composition.
- `ArchiveEntry` owns localized title/summary metadata plus a semantic content provider. `SemanticDocument.searchableText()` walks the same declared blocks used by rendering, so Search does not render pages or maintain a duplicate article database.
- `client.ui.content.SemanticDocument` declares headings, paragraphs, ordered steps, callouts, compact registered-item icons, illustrations/captions, stat rows, requirements/status rows, tables, and related/previous/next links.
- `ContentViewport`, `EntryListView`, and `IllustrationView` own reusable wrapping, measurement, clipping, visible content, scrolling, focus/input, and hit testing. Archive pages declare content and targets only.
- `ReadOnlyDataTable<R>` and `ReadOnlyDataTableView<R>` are shared typed data-view components. A column declares a typed extractor, localized presentation, alignment, schema width/weight, and optional comparator. Equivalent schemas retain identical column geometry regardless of row text. Values wrap, and every row in one table uses the uniform height required by that table's longest wrapped value. The shared rank-column width prevents compact rank headers from being squeezed. The view also owns stable row-key selection, sort direction indicators, natural unsorted order, clipping, keyboard/wheel overflow, scrollbars, and visible-row rendering.
- `SemanticDocument.TableLayoutPolicy` makes scrolling ownership explicit. `ARTICLE_FLOW` expands ordinary Reference tables into the article viewport; `BOUNDED_RESULTS` is reserved for independently scrolling result sets such as the Item Yields browser.
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

### Player-facing Reference content and shared extensions

Reference Essences, Machines, and Equipment contain real entries without temporary overview pages. `ReferenceDocuments` composes the remaining articles from `EssencePresentationData`, `MachinePresentationData`, `EquipmentPresentationData`, and `MechanicsPresentationData`. `reference/essences/item_yields` now opens a dedicated browser over the completed server-provided dissolution snapshot. The retired investment/milestone progression entry is removed. Current Ascension, chapter seal requirements, investment acceleration, and normalized activity rates are documented together under Category Attunement.

`ArchiveEntry.navigationSummary` is a concise sidebar description independent of its richer searchable `summary`. Search still traverses catalog metadata and semantic content; it does not maintain a second index of authored facts.

Shared content additions:

- `ItemPresentation` describes a registered resource, immutable component patch, searchable/hover label, and optional caption. It resolves a detached stack only for rendering. `EquipmentTierData.tierData` and `EssentiumCarrierData.carrierData` are the same pure encoders used by their gameplay writers, so equipment tiers and filled Essentium categories/grades select the real native models without installing configuration or mutating a live item.
- `SemanticDocument.ItemRow` lays out ordered model variants. `ReadOnlyDataTable.Column.item` supports image cells with optional tier captions. Item labels remain searchable and appear in the foreground hover pass.
- Table rows may carry a canonical navigation `target`. Primary click or Enter/Space activates it; stable row identity keeps targets attached through sorting. Existing row hover/focus signals clickability without adding underlines or replacing category colors. Standalone links retain directional arrows; neither renderer adds hyperlink underlining. Explicitly authored emphasis is preserved.
- `SemanticDocument.Table(data, TableLayoutPolicy.ARTICLE_FLOW)` expands to all rows. The article owns wheel/Page/Home/End scrolling; row-key keyboard selection reveals its row using the article scroll. The boolean compatibility constructor maps to the same explicit policy, and the one-argument constructor retains the bounded-results contract.
- Requirement rows now stack their label above a full-width explanation. Links wrap with the same measured bounds used for drawing and activation.

Armor tables show per-slot physical allocation and worn Bonus share. `ArmorStatWeights.pointsForSlot` is shared with `EquipmentBaselineResult`; Latent armor has zero Essence share. Other equipment uses compact per-tier item/stat rows and grouped clickable Bonus lists. Shield reflection uses `ShieldMath` and synchronized shield settings. Maximum durability displays the actual non-shield item value, or the shield's applied tier value—not the currently unused non-shield generated durability target. No durability progression gameplay change is included.

Essence/category labels retain canonical category colors; all new tier text uses the same tier-metal palette as existing Skills and Bonuses. Player prose omits registry/persistence/synchronization implementation explanations while preserving useful formulas, limitations, and failure conditions.

Focus cells use only `None`, `Latent`, or their upgraded tier name beneath the Focus header; the provider's three distinct operational states are unchanged. Upgrade tier columns receive usable minimum widths and share spare width, with concise cost headers. Footer navigation is authored beneath a localized `See Also` section heading/divider; applicable Bonus groups remain under their own headings. Soulbound and Fractured labels use `AscendanceUiPalette.SOULBOUND`/`FRACTURED`, semantic aliases for the existing canonical SPECIAL/ERROR tooltip colors. Equipment tooltips share those aliases.

`ArchiveNavigator` stores canonical `ArchiveLocation` values in immutable `Visit` snapshots, never screens or runtime objects. Back and Forward each have a bounded 32-visit stack. Visits capture article/list scroll, search query/selection/scroll, and Item Yields query/categories/Any-or-All policy/sort/selection/results scroll, so history restores the actual viewport. New navigation clears the forward branch; no-op/invalid targets do not. Both stacks survive closing and reopening within the same session. Guide and Reference independently remember section, entry, legacy section-tab window, entry-list scroll, and article scroll. First use resolves to Guide → Beginning; missing sections, entries, or results reconcile safely.

Archive history controls always occupy the left/right ends of the section row, including Search; unavailable actions are visibly disabled. `FullscreenLayout.sectionBar` wraps complete section navigation between them and moves the article below all rows. The unpaged `FullscreenComposition.Builder.sections` overload consumes those bounds. Other consumers retain the existing paged-tabs overload; Archive no longer exposes section-paging arrows or a bottom Back control.

`ArchiveNavigationState` uses `UiNavigationMemory` and captures its session token when the screen opens. `ClientPacketDispatch` clears the Archive session on a connection boundary, and a late screen removal cannot write into the new session. `ArchiveLocation.YieldBrowser` is the current typed destination. `yield:<item-id>` is its canonical reveal target; revealing a hidden item clears restrictive filters in the new history visit while preserving the previous browser state behind Back.

## Item Yields data and browser

`ItemEssenceTooltipClientState.YieldSnapshot` is the typed, read-only client owner for the resolved item mapping already synchronized for acquisition tooltips. Chunk assembly still begins only at chunk zero, rejects stale/mismatched generations, and publishes only after every chunk validates. The tooltip lookup map and ordered browser rows are replaced by one volatile snapshot assignment. Disconnect clears readiness and rows, so an absent current-world mapping is never rendered as zero or replaced with local defaults.

`ItemYieldBrowser` projects each synchronized item ID to its registered default item model and localized hover name. It filters localized names, supports one or more category toggles with exact-positive Any/All semantics, sorts localized names or raw micro-unit amounts in both directions, formats only at presentation time, and virtualizes only visible rows in one results viewport. Stable item IDs provide deterministic ties and selection. Category headers and values retain canonical Essence colors, and item cells expose their localized hover labels.

The server mapping intentionally excludes data-bearing Essentium from fixed mapping rows. Filled carriers dissolve according to their actual stored category/amount and current extraction efficiency; the Infuser article retains that mechanic instead of inventing representative fixed yields. `ItemYieldBrowser.searchMetadata` exposes a lightweight localized row name and canonical reveal target for future global Search integration without creating one Archive article per item.

The Item Yields filter uses the same full-width 24-pixel shared band as Archive Search. Explanatory lines and the See Also footer are omitted; the table fills the remaining content height. Its image header says `1 item` to preserve per-individual-item context. Six Essence columns opt into the shared table's equal-width group; localized header measurements determine their common desired width, and the Name column absorbs leftover pixels. Other table schemas retain their existing allocation. Clicking the selected Essences tab from this dedicated browser returns to the real parent article/list and participates in normal Back/Forward history.

Shared fullscreen buttons no longer draw the inner white focus rectangle. Existing colored hover/selection treatment and input behavior remain. Nexus sliders retain their separate white outline while actively held and after release only when the target differs from the stored investment. Handle grabs preserve the exact investment and apply relative movement from an unrounded anchor; stationary clicks no longer convert a rounded pixel back into a different target. Clicking the rail still selects its position.

The current Search foundation edits and remembers a query, derives simple matching results directly from catalog metadata and semantic content, and remembers a stable selected result. Clicking a result opens it directly; keyboard arrows move selection and Enter/Space opens it. Search uses shared gap-aware bands so the field and result list have identical horizontal bounds and a consistent gutter. Ranking, filters, highlighting, and expanded search behavior remain later work.

Every explicit location transition captures current offsets and invalidates the displayed location, including Search's Open action and history traversal. Opening the current entry is a navigation no-op. Selecting another entry within a section preserves its list offset and focus; a mode/section change starts a new shared interaction scope. Missing/null catalog targets resolve safely.

## Item and delivery

`essence_ascendance:ascendance_archive` is a real, non-stackable item with a normal generated item model. Its current `layer0` is `minecraft:item/book`; replacing the art later is resource-only work.

`DormantGuidebookService` retains the existing persisted `dormant_guidebook_received` receipt for save compatibility. Its former placeholder factory is replaced by `createArchive()`. Eligibility, successful receipts, failed-delivery retries, full-inventory drops, progression-reset rearming, and explicit operator replacement all use the same established pipeline.

Players whose old receipt was consumed by the former ordinary-book placeholder are intentionally not granted a second item on login. An operator can recover the real Archive without clearing progression or the old receipt:

`/essence admin player guide give [player]`

The command uses the same inventory/drop fallback and records a receipt only after successful delivery.

## Validation

`archiveFoundationInvariants` covers catalog topology, shared-subject/distinct-document identity, semantic/search content, styled Components, typed numeric/text sorting, disabled-sort natural order, measured widths, visible-row windows, stable table selection, independent navigation restoration, missing-target fallback, bounded history, item registration, and the generated book-texture model. `referencePresentationInvariants` covers all registered Skills/Bonuses, native rank/checkpoint parity, rank-specific gate fixtures, semantic units/precision, pure projections, readiness/profile changes, localization, current-player context, and an isolated changed-value article fixture. `dormantGuidebookInvariants` covers eligibility, receipts, retry, inventory/drop behavior, creative full-inventory safety, reset rearming, and admin recovery.
