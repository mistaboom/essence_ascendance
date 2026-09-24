# Fullscreen composition

The fullscreen presentation layer lives in `client.ui.fullscreen`. Nexus is its shipping menu-backed consumer and the Ascendance Archive is its shipping ordinary-screen consumer. `FullscreenCompositionTest.ArchiveScreenExample` remains a small nonshipping composition fixture. The framework has no Nexus or Archive mode, essence/category, screen-ID switch, transaction, network, or menu dependency.

## Host and lifecycle boundary

`FullscreenContainerScreen<M>` still extends Minecraft's `AbstractContainerScreen<M>`. It preserves container initialization, normal ticking, menu validity, vanilla key handling, closing, and removal. It forwards presentation to `FullscreenComposition`, including input before vanilla fallback, reflow on initialization/resize, foreground overlays, and tooltip suppression. A slotless fullscreen menu is the intended container use; inventory-slot layout is not implemented by this adapter.

`FullscreenScreen` provides the same composition contract for an ordinary Minecraft `Screen`. It does not construct a menu or emulate container/transaction behavior. Both adapters expose:

- `composeFullscreen()`: return a fresh declaration using current dimensions, state, and availability.
- `beforeFullscreenFrame()`: optional host work before composing/rendering; Nexus consumes authoritative transaction results here.
- `renderFullscreenTooltips(...)`: host content tooltips, called only when the overlay stack allows them.
- `fullscreen`: the instance-owned composition controller. Neither adapter saves navigation or chooses a close policy for its host.

### Native background rendering contract

Minecraft 1.21.1 `Screen.render` calls the virtual `renderBackground` hook before widgets. The default ordinary-screen background runs the blur post-process. `FullscreenScreen` therefore renders the composition in its final `renderBackground` override and delegates to `Screen.render` once; it must never render the composition first and then invoke the default background. The composition already supplies the fullscreen surface. Widgets follow that surface, then shared overlays and tooltips render in the foreground.

`FullscreenContainerScreen` retains the native container `renderBackground` → `renderBg` dispatch. Fabric invokes that dispatch through `Screen.render`; NeoForge inlines it to insert its background event. Both draw it once. `MachineContainerScreen` also relies on this single native pass instead of calling `renderBackground` explicitly before `super.render`.

`FullscreenRenderContractTest`, included in `fullscreenCompositionInvariants`, checks these actual mapped loader methods and the production host call order without requiring an OpenGL context. `FullscreenScroll.ensureVisible(start, size)` provides the common measured selection-reveal operation used by entry lists and data tables.

Nexus continues to own `onClose`, `removed`, `submitDraft`, `consumeTransactionResult`, and the accepted ascension handoff. A generic control emits an action callback. It cannot authorize an allocation, commit a transaction, discard a draft, acknowledge ascension, or decide when the menu should close.

## Shared API and ownership

| API | Responsibility |
| --- | --- |
| `FullscreenLayout.Spec`, `frame` | Fullscreen bounds, centered mode band, optional secondary band, content region, margins and reflow. Dimensions are configurable. |
| `segments`, `tabs`, `bands`, `listDetail`, `fixedWidthsRow`, `centered` | Reusable layout policies returning the exact bounds used for rendering and input. Bands optionally reserve bounded gutters while retaining aligned widths. Arbitrary mode/tab/control counts; no mandatory secondary navigation. |
| `FullscreenComposition.Builder` | Explicit assembly of mode descriptors, optional sections, panels, content regions, controls, and overlays. |
| `Region` | Stable ID, bounds, render callback, content input callback, and focus participation. Content may be a list, detail page, field, tree, or custom view. |
| `Control` / `FullscreenControls` | Stable ID, styled `Component` label, enabled/selected state, bounds, accent, common skin and action. The controller owns hit testing, focus, keyboard activation and disabled-event consumption. |
| `Overlay` / `UiOverlayStack` | Z order, pointer policy, tooltip policy and keyboard scope; isolated flush/depth rendering above the page; input capture and restoration of underlying focus. |
| `FullscreenViewport` | Clamped fractional two-axis scroll, pan gestures, centered origins, pixel-snapped origins, coordinate transforms, keyboard/wheel policies, and exception-safe clipping. |
| `FullscreenScroll` / `UiViewport` | Measured one-axis scrolling in content-defined units, such as text rows. Restored offsets survive until measurement. |
| `FullscreenNavigation<M,S>` | Stable typed mode/section selection, independent per-mode tab windows, restoration, authoritative reconciliation and safe fallback. |
| `UiNavigationMemory<K,S>` | Bounded session-only typed snapshot storage, with stale-session read/write protection. No disk persistence. |

The shared layer reuses `UiBounds`, `UiFocusController`, `UiOverlayStack`, `UiViewport`, `StyledTextLayout`, procedural clipping, and the existing palettes. It preserves the established translucent tab surfaces and baseline. Labels are fitted without flattening their `Component` styles.

### Scene and input contracts

Give each scene a stable `pageKey` and each element a unique stable ID. Rebuilding a declaration after state refresh does not recreate the controller or lose focus. Change the page key when the content identity changes. The controller cancels a captured gesture when its target disappears, the page/scope changes, a blocking modal appears, or the host resizes/removes.

Controls take precedence over content regions. Disabled controls consume their hits without firing actions. Accepted region/overlay pointer presses retain the initiating input handler through drag and release, including outside the original bounds. Implement `Input.cancel()` to end a domain interaction without committing it. Scroll/key handlers should report handled input at their boundaries to avoid leaking a consumed event to a host.

Tab/Shift-Tab traverses declared enabled controls and focusable regions. Enter/Space activates a focused control. Content input receives its remaining keys, including content-specific directional navigation. `primaryInput` provides page-level commands such as Escape-to-Back when no focused content handler consumes them. It is not a second invocation of the same handler. `Input.focused` supports fields and other content that needs focus notification; `charTyped` only targets focused content. Overlay keyboard ownership captures all keys and text while active. Nested modal scopes restore their own preceding focus; changed pages discard stale restoration targets.

Regions render in declaration order, followed by common controls. Overlay rendering is sorted by z order, flushed and raised as an isolated pass after the host render. Covered content does not receive pointer hover coordinates or leak tooltips. A region may use `FullscreenViewport.withClip` or `renderContent` to clip only its scrollable body while keeping its own headers outside that clip.

## Nexus migration

`AscendanceNexusScreen.composeFullscreen` declares the Ascendance / Bonuses / Skills descriptors, optional essence tabs, page panel, specialized content, common footer actions and paging arrows, attunement Back control, synchronization/request input shields and exit confirmation overlay. The controller and shared host render and dispatch them.

- Mode, category identity, and tab-window ownership moved to `FullscreenNavigation`. Nexus's typed snapshot adapter stores independent mode locations; a selected category may remain outside a manually paged tab window.
- Frame geometry, tab measurement/paging geometry, page panel, modal centering/action rows and footer rows use shared policies. `NexusPageLayout` retains only whole-track-width, reservoir, title/footer reserve and track-specific layout decisions, inside the shared frame and bands.
- The skill tree uses shared viewport instances keyed by essence ID. Panning, clamping, Shift-wheel, horizontal fallback, origin translation and clipping no longer have private Nexus implementations. Node geometry, connections, choice/replacement groups, and the tree-specific scroll indicators remain domain presentation.
- Attunement retains `NexusAttunementView` and constellation geometry. It uses shared stable-ID focus, measured row scrolling, clipping, and a declared Back control. `prepare` reconciles authoritative content before the first/restored frame's input. Escape/Back remains a content intent.
- Allocation dragging and refunds remain Nexus logic, but their region captures and cancels through the shared router. Mouse release ends the gesture without submitting the staged plan.
- The exit modal uses `UiOverlayStack` pointer/keyboard/tooltip policies and shared foreground rendering. Apply, discard, return, transaction waiting, stale draft and rejection decisions remain Nexus callbacks. HUD circles still intercept every button before node purchases or panning.
- `NexusDraft`, `NexusAttunementView`, `NexusCategoryViewFactory`, `NexusProgressionTrack`, `NexusBonusTrackLayout`, `NexusSkillTreeLayout`, `NexusAscendanceAction`, `NexusAscensionHandoff`, `SkillTooltipPresentation`, `BonusTooltipPresentation`, and `SemanticTooltip` are retained.

Removed from the screen: the mode selector and secondary-tab render/hit implementations, private generic mouse/key routing pipelines, tab/frame layout records and algorithms, modal depth/flush/dispatch code, generic action/arrow/button rendering, manual primary/paging hit boxes, private two-axis scroll/origin/panning state, and the private outline implementation. There are no compatibility entry points for those removed paths.

Legitimate specializations remaining in Nexus include bonus affordability and snapping, staged allocation math, skill evaluation and purchase/choice/replacement semantics, tree routing geometry, HUD preference transport, tier/category presentation, semantic tooltip content and tooltip-local scroll conventions, synchronization interpretation, and every transaction/lifecycle decision. These cannot be inferred by a generic fullscreen host.

## Navigation and synchronization state

`NexusNavigationState` remains the domain's immutable typed snapshot API. It delegates bounded storage to `UiNavigationMemory`; `FullscreenNavigation` generalizes the useful mode/section mechanics. A snapshot contains IDs, offsets, typed pages, and attunement focus identity, never a player, menu, screen, draft, request, or transaction. The older three-argument attunement snapshot constructor remains usable; newer snapshots include a stable focused ID so reordering does not move focus to a different category.

Capture the session token at screen construction, recall using that token, and use the same token to remember on removal. `ClientPacketDispatch.preparePresentationConnection` establishes the current listener boundary before screen restoration or the first mod packet. Quit/new connections clear the memory and change the token. Late removal of an old screen cannot repopulate the new session. Reopening and resizing within the same connection retain the existing session-memory experience.

Do not reconcile a temporary unavailable list as authoritative content. Nexus only reconciles categories/attunement once its synchronized snapshot is ready. Generic navigation's `authoritative=false` and unmeasured scroll restoration preserve remembered locations during waits. Once authoritative content is available, removed sections fall back safely and scroll/windows clamp to current dimensions.

## Archive consumer

`AscendanceArchiveScreen` is the shipping ordinary-screen consumer. It declares Guide, Reference, and Search through `Builder.modes`, Guide/Reference sections through `Builder.sections`, shared list/detail regions for documents, and aligned field/results regions for Search. Search results distinguish selection from activation: arrows move selection, while a primary click or Enter/Space opens the selected entry. It has no menu or copied fullscreen router. Archive session state is typed and backed by its own `UiNavigationMemory` instance; Guide, Reference, and Search retain independent locations and offsets.

Archive's reusable content and read-only data-view APIs are documented in `docs/ascendance-archive.md`. Every Skill and Bonus article uses the shared typed table component, and illustrations resolve real registered item presentations inside shared fit/clip/render-state boundaries.

The earlier executable `FullscreenCompositionTest` proof remains as a deliberately small alternate-consumer fixture. The shipping Archive now exercises the same host with real localized content, catalog navigation, entry/article components and Search state. Neither introduces a shared screen-ID switch.

Register content input through `Region` and actions through `Control`; do not add a new screen-local generic tab, focus, pan, modal or scroll router. Supply content rendering and semantic actions; use shared layout/viewport policies to make draw/hit geometry agree. Use the existing styled/semantic tooltip systems for Archive content when appropriate.

## Validation

The new `fullscreenCompositionInvariants` and `fullscreenNavigationInvariants` tasks are part of the normal `balanceInvariants`/`check`/`build` graph. They test alternate consumers, optional tabs, arbitrary mode counts, narrow reflow, stable identities, independent mode memory, removed-content fallback, synchronization waits, session isolation and late saves, disabled controls, focus lifecycle, nested modal ownership, pointer captures, text input, viewport transforms and scrolling. Existing Nexus, shared presentation, tooltip and machine-foundation behavioral assertions remain in place.

Final validation: `./gradlew.bat build --offline --console=plain --continue` passed in 3m 21s (78 actionable tasks; 64 executed, 14 up-to-date). Fabric and NeoForge compiled, transformed, remapped and assembled. The normal graph passed, including fullscreen composition (278 checks), fullscreen navigation (22), Nexus attunement (2,494), Nexus bonus presentation (32,515), shared presentation (13,429), tooltip presentation (50), palette (131) and machine foundation (21). `git diff --check` passed. Separate source comparison confirmed the 13 retained transaction/allocation/purchase/activation methods were unchanged, and ordinary Nexus layout coordinates were compared with the original formulas.

In-game visual/gameplay testing still requires opening Nexus at wide and narrow GUI scales, exercising all modes, allocations/refunds, skill purchases and Shift previews, HUD circles, overflow/panning, exit apply/discard/back, rejected/stale transactions and accepted ascension. Automated tests do not substitute for that runtime acceptance pass.
