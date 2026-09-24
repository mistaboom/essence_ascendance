package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.nexus.NexusCategoryView;
import com.mistaboom.essence_ascendance.client.nexus.NexusBonusTrackLayout;
import com.mistaboom.essence_ascendance.client.nexus.NexusAscendanceAction;
import com.mistaboom.essence_ascendance.client.nexus.NexusCategoryViewFactory;
import com.mistaboom.essence_ascendance.client.nexus.NexusDraft;
import com.mistaboom.essence_ascendance.client.nexus.NexusMode;
import com.mistaboom.essence_ascendance.client.nexus.NexusNavigationState;
import com.mistaboom.essence_ascendance.client.nexus.NexusProgressionTrack;
import com.mistaboom.essence_ascendance.client.nexus.NexusSkillTreeLayout;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusMenu;
import com.mistaboom.essence_ascendance.network.AscendanceNexusTransactionPayload;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload;
import com.mistaboom.essence_ascendance.progression.BonusTrackCurve;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import com.mistaboom.essence_ascendance.client.procedural.GuiProceduralGeometry;
import com.mistaboom.essence_ascendance.skill.requirement.BonusInvestmentRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.PermanentMilestoneRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import com.mistaboom.essence_ascendance.skill.SkillActivationPolicy;
import com.mistaboom.essence_ascendance.skill.SkillChoiceGroup;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillDisplayState;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationContext;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationResult;
import com.mistaboom.essence_ascendance.skill.SkillPrerequisiteStatus;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.SkillRequirementKind;
import com.mistaboom.essence_ascendance.skill.SkillRequirementStatus;
import com.mistaboom.essence_ascendance.skill.SkillStateEvaluator;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import dev.architectury.networking.NetworkManager;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.UiViewport;
import com.mistaboom.essence_ascendance.client.ui.UiOverlayStack;
import com.mistaboom.essence_ascendance.client.ui.UiNavigationMemory;
import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenComposition;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenContainerScreen;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenControls;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenLayout;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenNavigation;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenViewport;
import com.mistaboom.essence_ascendance.client.nexus.NexusPageLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fullscreen, data-driven progression shell for the Ascendance Nexus.
 *
 * Dragging an Essence track stages a proposed reallocation locally, including
 * an affordable-Essence ceiling. Released sliders remain staged and can refund
 * Essence back into their category budget. Pressing ALLOCATE sends one complete
 * staged plan to the server; the server validates and commits it atomically.
 */
public final class AscendanceNexusScreen
        extends FullscreenContainerScreen<AscendanceNexusMenu> {

    private static final int PANEL = 0xCC1C1C20;
    private static final int PANEL_INNER = 0xCC29292D;
    private static final int BORDER = 0xFF777780;
    private static final int BORDER_BRIGHT = 0xFFA5A5AD;
    private static final int TEXT = 0xFFF0EDF4;
    private static final int MUTED = 0xFFB6AFBF;
    private static final int DIM = 0xFF7F7889;
    private static final int TRACK = 0xFF49494F;
    private static final int TRACK_LOCKED = 0xFF242428;
    private static final int TRACK_LOCKED_TICK = 0xFF626269;
    private static final int TIER_LINE = 0x66606068;
    private static final int LOCKED = 0x552F2F34;
    // These legacy warning/error accents belong to the protected skill presentation.
    private static final int INCOMPLETE = 0xFFD1B36A;
    private static final int ERROR = 0xFFD47A7A;
    private static final int[] SKILL_CHOICE_GROUP_COLORS = {
            0xFF3FA7B5,
            0xFFD28A3D,
            0xFF55A96B,
            0xFFD16464,
            0xFF4F9BD8
    };

    private static final int SAFE_MARGIN = 14;
    private static final int TRACK_PREFERRED_WIDTH = 66;
    private static final int TRACK_GAP = 4;
    private static final int TRACK_ARROW_WIDTH = 20;
    private static final int TRACK_KNOB_HEIGHT = 7;
    private static final int TRACK_HIT_PADDING = 4;
    private static final int ALLOCATE_BUTTON_WIDTH = 104;
    private static final int ASCEND_BUTTON_WIDTH = ALLOCATE_BUTTON_WIDTH;
    private static final int BOTTOM_CONTROL_HEIGHT = 16;
    private static final int BOTTOM_CONTROL_GAP = 5;
    private static final int GAUGE_TEXT_GAP = 4;
    private static final int SKILL_LEGEND_HEIGHT = 34;
    private static final int SKILL_SCROLL_STEP = 28;
    private static final int SKILL_ROUTE_CLEARANCE =
            NexusSkillTreeLayout.ROUTE_SPACING;
    private static final int SKILL_ROUTE_LANE_SPACING =
            NexusSkillTreeLayout.ROUTE_SPACING;
    private static final int SKILL_ROUTE_OUTER_GAP =
            NexusSkillTreeLayout.ROUTE_SPACING * 2;
    private static final int SKILL_ROUTE_BEND_COST = 120;
    private static final int SKILL_ROUTE_NEAR_PARALLEL_COST = 12_000;
    private static final int SKILL_REPLACEMENT_FALLBACK_COLOR = 0xFFE0A15A;
    private static final int SKILL_SCROLL_ARROW_WIDTH = 24;
    private static final int SKILL_SCROLL_ARROW_HEIGHT = 30;
    private static final int SKILL_SCROLL_ARROW_INSET = 2;
    private static final float SKILL_SCROLL_ARROW_GLYPH_SCALE = 2.0F;
    private static final float SKILL_OVERLAY_RENDER_DEPTH = 100.0F;
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong(1L);

    private int selectedCategoryIndex = 0;
    private int trackWindowStart = 0;

    private int draggingTrackIndex = -1;
    private final FullscreenNavigation<NexusMode, ResourceLocation> navigation = new FullscreenNavigation<>(NexusMode.ASCENDANCE);
    private final UUID navigationPlayer;
    private final UiNavigationMemory.Session navigationSession;
    private final Map<NexusMode, NexusNavigationState.Page> navigationPages = new EnumMap<>(NexusMode.class);
    private final NexusDraft draft = new NexusDraft();
    private final NexusAttunementView attunementView = new NexusAttunementView();
    private boolean acceptedAscension;
    private final com.mistaboom.essence_ascendance.client.nexus.NexusAscensionHandoff ascensionHandoff =
            new com.mistaboom.essence_ascendance.client.nexus.NexusAscensionHandoff();
    private PendingDecision pendingDecision = PendingDecision.NONE;
    private PendingCompletion pendingCompletion = PendingCompletion.NONE;
    private long pendingRequestId = -1L;
    private long acceptedRevision = -1L;
    private Component transactionFeedback;
    private final Map<ResourceLocation, FullscreenViewport> skillViewports = new LinkedHashMap<>();
    private final Map<ResourceLocation, NexusSkillTreeLayout.Layout> skillLayouts =
            new LinkedHashMap<>();
    private ResourceLocation hoveredSkillId;
    private final SkillTooltipPresentation skillTooltipPresentation = new SkillTooltipPresentation();
    private ResourceLocation tooltipSkillId;
    private int skillTooltipScroll;
    private int skillTooltipMaximumScroll;
    private NexusProgressionTrack hoveredTrack;

    public AscendanceNexusScreen(
            AscendanceNexusMenu menu,
            Inventory playerInventory,
            Component title
    ) {
        super(menu, playerInventory, title);
        navigationPlayer = playerInventory.player.getUUID();
        ClientPacketDispatch.preparePresentationConnection();
        navigationSession = NexusNavigationState.session();
        NexusNavigationState.Snapshot remembered = NexusNavigationState.recall(navigationSession, navigationPlayer);
        navigation.selectMode(remembered.mode());
        remembered.pages().forEach((key, page) -> navigation.restoreMode(key, page.category(), page.tabWindow()));
        navigationPages.putAll(remembered.pages());
        restoreCategoryNavigation();
        attunementView.restoreNavigation(remembered.attunement());
        Set<ResourceLocation> scrolled = new HashSet<>(remembered.skillScrollX().keySet());
        scrolled.addAll(remembered.skillScrollY().keySet());
        for (ResourceLocation id : scrolled) {
            FullscreenViewport viewport = new FullscreenViewport();
            viewport.restore(remembered.skillScrollX().getOrDefault(id, 0.0), remembered.skillScrollY().getOrDefault(id, 0.0));
            skillViewports.put(id, viewport);
        }
    }

    @Override
    public void removed() {
        rememberCategoryNavigation();
        Map<ResourceLocation, Double> scrollX = new LinkedHashMap<>(), scrollY = new LinkedHashMap<>();
        skillViewports.forEach((id, viewport) -> { scrollX.put(id, viewport.x()); scrollY.put(id, viewport.y()); });
        NexusNavigationState.remember(navigationSession, navigationPlayer,
                new NexusNavigationState.Snapshot(navigation.mode(), navigationPages,
                        attunementView.navigation(), scrollX, scrollY));
        super.removed();
    }

    private void rememberCategoryNavigation() {
        if (navigation.mode() != NexusMode.ASCENDANCE) navigationPages.put(navigation.mode(),
                new NexusNavigationState.Page(navigation.section(), navigation.sectionWindow(), trackWindowStart));
    }

    private void restoreCategoryNavigation() {
        selectedCategoryIndex = 0;
        trackWindowStart = navigationPages.getOrDefault(navigation.mode(), NexusNavigationState.Page.initial()).trackWindow();
    }

    private void changeMode(NexusMode requested) {
        if (requested == navigation.mode()) return;
        rememberCategoryNavigation();
        navigation.selectMode(requested);
        restoreCategoryNavigation();
        cancelContentInteraction();
    }

    private FullscreenLayout.Frame fullscreenFrame() {
        return FullscreenLayout.frame(FullscreenLayout.Spec.standard(), width, height,
                navigation.mode() != NexusMode.ASCENDANCE);
    }

    @Override
    protected FullscreenComposition.Scene composeFullscreen() {
        draft.synchronize(ClientEssenceState.snapshot());
        changeMode(NexusNavigationState.availableMode(navigation.mode(), ClientEssenceState.ready(), this::modeAvailable));
        List<NexusCategoryView> categories = categories();
        boolean ready = ClientEssenceState.ready();
        if (ready && navigation.mode() != NexusMode.ASCENDANCE) stabilizeSelection(categories);
        boolean attunementVisible = ready && navigation.mode() == NexusMode.ASCENDANCE
                && ClientEssenceState.snapshot().progress().status() != PlayerEssenceSyncPayload.ProgressStatus.CONFIGURATION_ERROR;
        if (attunementVisible) attunementView.prepare(ClientEssenceState.snapshot().attunement(), attunementBounds());
        var frame = fullscreenFrame();
        var builder = new FullscreenComposition.Builder(
                List.of(navigation.mode(), navigation.section() == null ? "" : navigation.section(), ready,
                        attunementView.navigation().category() == null ? "" : attunementView.navigation().category()), frame);
        if (modeAvailable(NexusMode.BONUSES)) {
            builder.modes(java.util.Arrays.stream(NexusMode.values())
                    .map(mode -> new FullscreenComposition.Mode<>(mode, EssenceText.gui(mode.translationPath()),
                            modeAvailable(mode), AscendanceUiPalette.INTERACTIVE)).toList(), navigation.mode(), this::changeMode);
        }
        if (ready && navigation.mode() != NexusMode.ASCENDANCE && !categories.isEmpty()) {
            builder.sections(navigation, categories.stream().map(category -> new FullscreenComposition.Section<>(
                            category.essence().id(), EssenceText.essenceShort(category.essence()),
                            AscendancePalette.categoryArgb(category.essence().id()))).toList(),
                    tabLayout(categories), id -> {
                        navigation.selectSection(id);
                        trackWindowStart = 0;
                        cancelContentInteraction();
                    });
        }
        if (ready && (navigation.mode() == NexusMode.ASCENDANCE || !categories.isEmpty())) {
            builder.panel("nexus/panel", frame.content(), PANEL, BORDER);
        }
        builder.region(new FullscreenComposition.Region("nexus/content", frame.content(),
                (graphics, x, y, tick) -> renderNexusPage(graphics, x, y), nexusInput(), true))
                .primaryInput("nexus/content");
        if (ready && (navigation.mode() == NexusMode.ASCENDANCE || !categories.isEmpty())) {
            declareActions(builder, categories);
        }
        if (attunementVisible) attunementView.controls(attunementBounds(), font.lineHeight).forEach(builder::control);
        // These overlays express presentation ownership only. Nexus owns their exit/request semantics.
        if (!ready || pendingRequestId >= 0L) {
            builder.overlay(new FullscreenComposition.Overlay(new UiOverlayStack.Layer("nexus/wait", frame.screen(),
                    400, UiOverlayStack.PointerPolicy.MODAL, UiOverlayStack.TooltipPolicy.SUPPRESS_ALL, true),
                    (graphics, x, y, tick) -> { }, List.of(), new FullscreenComposition.Input() {
                        @Override public boolean key(int key, int scan, int modifiers) {
                            if (key == 256) onClose();
                            return true;
                        }
                    }));
        }
        if (pendingDecision != PendingDecision.NONE) builder.overlay(exitOverlay());
        return builder.build();
    }

    private void renderNexusPage(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredSkillId = null;
        hoveredTrack = null;
        if (!ClientEssenceState.ready()) { renderWaitingState(graphics); return; }
        if (navigation.mode() == NexusMode.ASCENDANCE) { renderAscensionPage(graphics, mouseX, mouseY); return; }
        List<NexusCategoryView> categories = categories();
        if (categories.isEmpty()) { renderWaitingState(graphics); return; }
        NexusCategoryView category = categories.get(selectedCategoryIndex);
        if (navigation.mode() == NexusMode.SKILLS) renderSkillsCategory(graphics, category, mouseX, mouseY);
        else {
            renderEssenceCategory(graphics, category, mouseX, mouseY);
            hoveredTrack = NexusProgressionTrack.tooltipTrack(category.tracks(), draggingTrackIndex, hoveredTrack);
        }
    }

    @Override protected void beforeFullscreenFrame() { consumeTransactionResult(); }

    @Override
    protected void renderFullscreenTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (navigation.mode() == NexusMode.ASCENDANCE) {
            attunementView.renderTooltip(graphics, font, mouseX, mouseY, width);
            renderPrimaryActionTooltip(graphics, mouseX, mouseY);
        } else if (navigation.mode() == NexusMode.SKILLS && hoveredSkillId != null) {
            renderSkillTooltip(graphics, hoveredSkillId, mouseX, mouseY);
        } else if (navigation.mode() == NexusMode.BONUSES && hoveredTrack != null) {
            renderTrackTooltip(graphics, hoveredTrack, mouseX, mouseY);
        } else renderPrimaryActionTooltip(graphics, mouseX, mouseY);
    }

    private void declareActions(FullscreenComposition.Builder builder, List<NexusCategoryView> categories) {
        if (navigation.mode() == NexusMode.ASCENDANCE) {
            NexusAscendanceAction action = ascendanceAction();
            ResourceLocation tier = ClientEssenceState.snapshot().progress().nextTierId();
            int accent = action.kind() == NexusAscendanceAction.Kind.ASCEND && tier != null
                    ? AscendancePalette.tierMetalRgb(tier) : AscendanceUiPalette.INTERACTIVE;
            builder.control(new FullscreenComposition.Control("nexus/primary", actionBounds(), ascendanceActionLabel(action),
                    action.enabled(), false, FullscreenControls.Style.PRIMARY, accent, this::activateAscendanceAction));
            return;
        }
        NexusPageLayout layout = contentLayout();
        var row = footerControls(layout);
        builder.control(new FullscreenComposition.Control("nexus/primary", row.get(1), primaryActionLabel(),
                primaryActionEnabled(), false, FullscreenControls.Style.PRIMARY, AscendanceUiPalette.INTERACTIVE, () -> {
                    if (draft.invalidated()) {
                        draft.clearAndCapture(ClientEssenceState.snapshot()); transactionFeedback = null;
                    } else submitDraft(false, PendingCompletion.NONE);
                }));
        if (navigation.mode() != NexusMode.BONUSES) return;
        int count = categories.get(selectedCategoryIndex).tracks().size();
        int visible = effectiveVisibleTrackCount(count, layout);
        clampTrackWindow(count, visible);
        builder.control(new FullscreenComposition.Control("tracks/previous", row.get(0), Component.literal("‹"),
                trackWindowStart > 0, false, FullscreenControls.Style.ARROW, AscendanceUiPalette.INTERACTIVE,
                () -> trackWindowStart = UiViewport.create(count, visible, trackWindowStart).scrollBy(-visible).offset()));
        builder.control(new FullscreenComposition.Control("tracks/next", row.get(2), Component.literal("›"),
                trackWindowStart + visible < count, false, FullscreenControls.Style.ARROW, AscendanceUiPalette.INTERACTIVE,
                () -> trackWindowStart = UiViewport.create(count, visible, trackWindowStart).scrollBy(visible).offset()));
    }

    private FullscreenComposition.Overlay exitOverlay() {
        UiBounds modal = modalLayout();
        int buttonWidth = Math.max(0, (modal.width() - 26) / 3);
        var row = FullscreenLayout.fixedWidthsRow(new UiBounds(modal.x() + 8, modal.bottom() - BOTTOM_CONTROL_HEIGHT - 9,
                Math.max(0, modal.width() - 16), BOTTOM_CONTROL_HEIGHT), List.of(buttonWidth, buttonWidth, buttonWidth), 5);
        boolean idle = pendingRequestId < 0L;
        List<FullscreenComposition.Control> controls = List.of(
                modalControl("apply", row.get(0), "nexus.modal.apply_exit", idle && !draft.invalidated(),
                        () -> submitDraft(false, PendingCompletion.EXIT)),
                modalControl("discard", row.get(1), "nexus.modal.discard_exit", idle, () -> {
                    draft.clearAndCapture(ClientEssenceState.snapshot()); transactionFeedback = null;
                    pendingDecision = PendingDecision.NONE; super.onClose();
                }),
                modalControl("back", row.get(2), "nexus.modal.go_back", idle, () -> {
                    pendingDecision = PendingDecision.NONE; pendingCompletion = PendingCompletion.NONE; transactionFeedback = null;
                }));
        return new FullscreenComposition.Overlay(new UiOverlayStack.Layer("nexus/exit", modal, 500,
                UiOverlayStack.PointerPolicy.MODAL, UiOverlayStack.TooltipPolicy.SUPPRESS_ALL, true),
                (graphics, x, y, tick) -> renderPendingDecision(graphics, x, y), controls,
                new FullscreenComposition.Input() {
                    @Override public boolean key(int key, int scan, int modifiers) {
                        if (key == 256 && pendingRequestId < 0L) {
                            pendingDecision = PendingDecision.NONE; pendingCompletion = PendingCompletion.NONE;
                        }
                        return true;
                    }
                });
    }

    private FullscreenComposition.Control modalControl(String id, UiBounds bounds, String label, boolean enabled, Runnable action) {
        return new FullscreenComposition.Control("exit/" + id, bounds, EssenceText.gui(label), enabled, false,
                FullscreenControls.Style.ACTION, AscendanceUiPalette.INTERACTIVE, action);
    }

    /** Only domain hit targets live here: crystals, skill nodes/HUD circles, and allocation tracks. */
    private FullscreenComposition.Input nexusInput() {
        return new FullscreenComposition.Input() {
            @Override public boolean click(double x, double y, int button) {
                if (handleHudControlClick(x, y, button)) return true;
                if (button != 0) return false;
                if (navigation.mode() == NexusMode.ASCENDANCE) {
                    var action = attunementView.click(x, y);
                    if (action == NexusAttunementView.Click.ASCEND) requestAscension();
                    return action != NexusAttunementView.Click.NONE;
                }
                var categories = categories();
                if (categories.isEmpty()) return false;
                var category = categories.get(selectedCategoryIndex);
                var layout = contentLayout();
                if (navigation.mode() == NexusMode.SKILLS) {
                    SkillDefinition clicked = skillAt(category, x, y, layout);
                    if (clicked != null) { handleSkillClick(clicked, category); return true; }
                    var viewport = activeSkillViewport();
                    if (viewport != null && viewport.bounds().contains(x, y)) { viewport.beginPan(x, y); return true; }
                    return false;
                }
                int clicked = trackAt(x, y, category, layout, effectiveVisibleTrackCount(category.tracks().size(), layout));
                if (clicked < 0) return false;
                draggingTrackIndex = clicked;
                updateStagedDrag(y, category, category.tracks().get(clicked), layout);
                return true;
            }
            @Override public boolean drag(double x, double y, int button, double dx, double dy) {
                if (navigation.mode() == NexusMode.SKILLS) {
                    var viewport = activeSkillViewport();
                    if (viewport != null) viewport.pan(x, y);
                    return true;
                }
                var categories = categories();
                if (draggingTrackIndex >= 0 && !categories.isEmpty()) {
                    var category = categories.get(selectedCategoryIndex);
                    if (draggingTrackIndex < category.tracks().size())
                        updateStagedDrag(y, category, category.tracks().get(draggingTrackIndex), contentLayout());
                }
                return true;
            }
            @Override public boolean release(double x, double y, int button) {
                // Release retains the staged allocation; only the primary action submits it.
                cancelContentInteraction(); return true;
            }
            @Override public void cancel() { cancelContentInteraction(); }
            @Override public boolean scroll(double x, double y, double dx, double dy) {
                if (navigation.mode() == NexusMode.ASCENDANCE) return attunementView.scroll(x, y, dy);
                if (navigation.mode() != NexusMode.SKILLS) return false;
                var viewport = activeSkillViewport();
                if (viewport == null || !viewport.bounds().contains(x, y)) return false;
                if (hoveredSkillId != null && hoveredSkillId.equals(tooltipSkillId) && skillTooltipMaximumScroll > 0 && dy != 0) {
                    skillTooltipScroll = UiViewport.create(skillTooltipMaximumScroll + 1, 1, skillTooltipScroll)
                            .scrollBy(-(int) Math.signum(dy) * 3).offset();
                } else viewport.scroll(dx, dy, SKILL_SCROLL_STEP, hasShiftDown(), true);
                return true;
            }
            @Override public boolean key(int key, int scan, int modifiers) {
                if (navigation.mode() == NexusMode.ASCENDANCE) {
                    var action = attunementView.key(key, (modifiers & 1) != 0);
                    if (action == NexusAttunementView.Click.ASCEND) requestAscension();
                    return action != NexusAttunementView.Click.NONE;
                }
                var viewport = activeSkillViewport();
                return viewport != null && viewport.keyboard(key, (modifiers & 1) != 0, SKILL_SCROLL_STEP);
            }
        };
    }

    private FullscreenViewport activeSkillViewport() {
        if (navigation.mode() != NexusMode.SKILLS || !ClientEssenceState.ready()) return null;
        var categories = categories();
        if (categories.isEmpty()) return null;
        ResourceLocation id = categories.get(selectedCategoryIndex).essence().id();
        return clampedSkillScroll(id, skillLayout(id), skillViewport(contentLayout()));
    }

    private void cancelContentInteraction() {
        draggingTrackIndex = -1;
        skillViewports.values().forEach(FullscreenViewport::endPan);
    }

    private UiBounds attunementBounds() {
        NexusPageLayout layout = contentLayout();
        return new UiBounds(layout.left() + 4, layout.middleTop(), Math.max(0, layout.width() - 8),
                Math.max(1, layout.middleBottom() - layout.middleTop()));
    }

    private boolean modeAvailable(NexusMode candidate) {
        if (candidate == NexusMode.ASCENDANCE) return true;
        ClientEssenceState.Snapshot state = ClientEssenceState.snapshot();
        return state.ready() && com.mistaboom.essence_ascendance.client.nexus.NexusHudControl.progressionAvailable(state.tierId());
    }

    private void renderWaitingState(GuiGraphics graphics) {
        UiBounds bounds = FullscreenLayout.centered(fullscreenFrame().screen(), 320, 54, SAFE_MARGIN);
        FullscreenControls.panel(graphics, bounds, PANEL, BORDER);
        graphics.drawCenteredString(font, EssenceText.gui("nexus.synchronizing"),
                bounds.x() + bounds.width() / 2, bounds.y() + 24, TEXT);
    }

    private void renderAscensionPage(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        NexusPageLayout layout =
                contentLayout();

        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();
        ClientEssenceState.ProgressSnapshot progress =
                snapshot.progress();

        renderAscensionTransition(
                graphics,
                snapshot,
                progress,
                layout
        );

        if (progress.status() == PlayerEssenceSyncPayload.ProgressStatus.CONFIGURATION_ERROR) {
            renderAscensionConfigurationError(graphics, layout);
        } else {
            Component currentTier = Component.literal(tierDisplayName(snapshot.tierId()));
            Component nextTier = progress.nextTierId() == null ? currentTier
                    : Component.literal(tierDisplayName(progress.nextTierId()));
            double time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
            UiBounds bounds = attunementBounds();
            attunementView.render(graphics, font, snapshot.attunement(), currentTier, nextTier,
                    bounds.x(), bounds.y(), bounds.width(), bounds.height(), mouseX, mouseY, time);
        }

        renderAscendanceStatus(graphics, progress, layout);
    }

    private void renderAscensionTransition(
            GuiGraphics graphics,
            ClientEssenceState.Snapshot snapshot,
            ClientEssenceState.ProgressSnapshot progress,
            NexusPageLayout layout
    ) {
        String current =
                tierDisplayName(snapshot.tierId());
        String transition;

        if (progress.status()
                == PlayerEssenceSyncPayload.ProgressStatus.AVAILABLE
                && progress.nextTierId() != null) {
            transition =
                    current
                            + "  →  "
                            + tierDisplayName(progress.nextTierId());
        } else if (progress.status()
                == PlayerEssenceSyncPayload.ProgressStatus.MAX_TIER) {
            transition = current + "  •  " + EssenceText.gui("nexus.maximum").getString();
        } else {
            transition = current + "  •  " + EssenceText.gui("nexus.unavailable").getString();
        }

        float scale = 1.12F;
        int maximumWidth =
                Math.max(
                        40,
                        (int) ((layout.width() - 20) / scale)
                );
        String rendered =
                trimToWidth(
                        transition.toUpperCase(Locale.ROOT),
                        maximumWidth
                );

        float lineHeight =
                font.lineHeight * scale;
        float y =
                layout.top()
                        + Math.max(
                                1.0F,
                                (layout.sectionHeight() - lineHeight) / 2.0F
                        );

        graphics.pose().pushPose();
        graphics.pose().translate(
                (layout.left() + layout.right()) / 2.0F,
                y,
                0.0F
        );
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(
                font,
                rendered,
                0,
                0,
                TEXT
        );
        graphics.pose().popPose();
    }

    private boolean attunementReady() {
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        return snapshot.ready() && snapshot.progress().status() == PlayerEssenceSyncPayload.ProgressStatus.AVAILABLE
                && snapshot.attunement().ready();
    }

    private void renderAscendanceStatus(GuiGraphics graphics, ClientEssenceState.ProgressSnapshot progress,
                                         NexusPageLayout layout) {
        NexusAscendanceAction action = ascendanceAction();
        if (layout.sectionHeight() >= 28) {
            Component status;
            int statusColor;
            if (transactionFeedback != null) {
                status = transactionFeedback;
                statusColor = AscendanceUiPalette.argb(AscendanceUiPalette.ERROR);
            } else if (action.kind() == NexusAscendanceAction.Kind.APPLYING) {
                status = EssenceText.gui("nexus.transaction.waiting");
                statusColor = AscendanceUiPalette.argb(AscendanceUiPalette.INFORMATION);
            } else if (action.kind() == NexusAscendanceAction.Kind.DISCARD_DRAFT) {
                status = EssenceText.gui("nexus.draft.outdated");
                statusColor = AscendanceUiPalette.argb(AscendanceUiPalette.ERROR);
            } else if (action.kind() == NexusAscendanceAction.Kind.APPLY_CHANGES) {
                status = EssenceText.gui("nexus.pending_ready_to_apply");
                statusColor = AscendanceUiPalette.argb(AscendanceUiPalette.SUCCESS);
            } else if (progress.status()
                    == PlayerEssenceSyncPayload.ProgressStatus.MAX_TIER) {
                status = EssenceText.gui("nexus.maximum");
                statusColor = AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT);
            } else if (progress.status()
                    == PlayerEssenceSyncPayload.ProgressStatus.CONFIGURATION_ERROR) {
                status = EssenceText.gui("nexus.unavailable");
                statusColor = AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT);
            } else if (action.enabled()) {
                status = EssenceText.gui("nexus.ready_to_ascend");
                statusColor = AscendanceUiPalette.argb(AscendanceUiPalette.SUCCESS);
            } else {
                status = EssenceText.gui("nexus.requirements_incomplete");
                statusColor = AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT);
            }
            graphics.drawCenteredString(
                    font,
                    StyledTextLayout.fit(
                            font, status,
                            Math.max(40, layout.width() - 20)
                    ),
                    (layout.left() + layout.right()) / 2,
                    layout.middleBottom() + 3,
                    statusColor
            );
        }

    }

    private NexusAscendanceAction ascendanceAction() {
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        return NexusAscendanceAction.resolve(
                snapshot.progress().status(),
                attunementReady(),
                draft.hasChanges(snapshot),
                draft.invalidated(),
                pendingRequestId >= 0L
        );
    }

    private Component ascendanceActionLabel(NexusAscendanceAction action) {
        return switch (action.kind()) {
            case APPLYING -> EssenceText.gui("nexus.action.applying");
            case DISCARD_DRAFT -> EssenceText.gui("nexus.discard_draft_short");
            case APPLY_CHANGES -> EssenceText.gui("nexus.apply_changes");
            case ASCEND -> EssenceText.gui("nexus.ascend");
            case MAXIMUM_TIER -> EssenceText.gui("nexus.maximum_tier");
            case UNAVAILABLE -> EssenceText.gui("nexus.unavailable");
        };
    }

    private void renderAscensionConfigurationError(
            GuiGraphics graphics,
            NexusPageLayout layout
    ) {
        int centerX =
                (layout.left() + layout.right()) / 2;
        int centerY =
                (layout.middleTop() + layout.middleBottom()) / 2;

        graphics.drawCenteredString(
                font,
                EssenceText.gui("nexus.ascension_unavailable").getString(),
                centerX,
                centerY - 12,
                AscendanceUiPalette.argb(AscendanceUiPalette.ERROR)
        );
        List<FormattedCharSequence> guidance = new SemanticTooltip()
                .description(EssenceText.gui("nexus.ascension_config_error"))
                .wrap(font, Math.max(80, layout.width() - 24));
        for (int index = 0; index < guidance.size(); index++) {
            FormattedCharSequence line = guidance.get(index);
            graphics.drawString(font, line, centerX - font.width(line) / 2,
                    centerY + 5 + index * (font.lineHeight + 2), MUTED, false);
        }
    }

    private void renderEssenceCategory(
            GuiGraphics graphics,
            NexusCategoryView category,
            int mouseX,
            int mouseY
    ) {
        NexusPageLayout layout =
                contentLayout();

        renderCategoryTitle(
                graphics,
                category,
                layout
        );
        renderAvailableGauge(
                graphics,
                category,
                layout
        );
        renderTierGuides(
                graphics,
                category,
                layout
        );
        renderTracks(
                graphics,
                category,
                layout,
                mouseX,
                mouseY
        );
        renderFooter(
                graphics,
                category,
                layout
        );
    }

    private void renderSkillsCategory(
            GuiGraphics graphics,
            NexusCategoryView category,
            int mouseX,
            int mouseY
    ) {
        NexusPageLayout layout = contentLayout();

        renderCategoryTitle(
                graphics,
                category,
                layout
        );
        renderAvailableGauge(graphics, category, layout);
        renderSkillLegend(graphics, layout);
        renderSkillTree(graphics, category, layout, mouseX, mouseY);

        renderSkillsFooter(graphics, layout);
    }

    private void renderSkillLegend(
            GuiGraphics graphics,
            NexusPageLayout layout
    ) {
        int center = (layout.tracksLeft() + layout.right()) / 2;
        int maximumWidth = Math.max(20, layout.right() - layout.tracksLeft() - 8);
        graphics.drawCenteredString(
                font,
                trimToWidth(
                        EssenceText.gui("nexus.skills.legend.one").getString(),
                        maximumWidth
                ),
                center,
                layout.middleTop() + 2,
                MUTED
        );
        graphics.drawCenteredString(
                font,
                trimToWidth(
                        EssenceText.gui("nexus.skills.legend.two").getString(),
                        maximumWidth
                ),
                center,
                layout.middleTop() + 12,
                MUTED
        );
        graphics.drawCenteredString(font, EssenceText.gui("nexus.skills.legend.hud"),
                center, layout.middleTop() + 22, MUTED);
    }

    private void renderSkillTree(
            GuiGraphics graphics,
            NexusCategoryView category,
            NexusPageLayout layout,
            int mouseX,
            int mouseY
    ) {
        UiBounds viewport = skillViewport(layout);
        graphics.fill(
                viewport.x(),
                viewport.y(),
                viewport.right(),
                viewport.bottom(),
                PANEL_INNER
        );
        FullscreenControls.outline(
                graphics,
                viewport.x(),
                viewport.y(),
                viewport.width(),
                viewport.height(),
                BORDER
        );

        List<SkillDefinition> definitions =
                SkillRegistry.values(category.essence().id());
        if (definitions.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    EssenceText.gui("nexus.skills.none").getString(),
                    (viewport.x() + viewport.right()) / 2,
                    (viewport.y() + viewport.bottom()) / 2,
                    MUTED
            );
            return;
        }

        NexusSkillTreeLayout.Layout tree = skillLayout(category.essence().id());
        FullscreenViewport scroll = clampedSkillScroll(
                category.essence().id(),
                tree,
                viewport
        );
        Map<ResourceLocation, SkillEvaluationResult> evaluations =
                skillEvaluations();
        long projectedBalance = stagedAvailableEssence(category);

        FullscreenViewport.withClip(graphics, viewport.inset(1), () -> {
        List<SkillChoiceFrame> choiceFrames = skillChoiceFrames(
                definitions,
                tree,
                viewport,
                scroll
        );
        renderSkillChoiceGroups(graphics, choiceFrames);
        renderSkillConnections(graphics, tree, viewport, scroll, choiceFrames);
        renderSkillTierHeaders(graphics, tree, viewport, scroll);

        for (NexusSkillTreeLayout.Node node : tree.nodes()) {
            SkillEvaluationResult evaluation = evaluations.get(node.definition().id());
            if (evaluation == null) {
                continue;
            }

            long cost = skillCost(node.definition());
            SkillNodeVisualState visualState = skillVisualState(
                    evaluation,
                    cost,
                    projectedBalance
            );
            Rect bounds = skillNodeBounds(node, viewport, scroll);
            renderSkillNode(
                    graphics,
                    node.definition(),
                    visualState,
                    cost,
                    bounds,
                    bounds.contains(mouseX, mouseY)
            );

            if (viewport.contains(mouseX, mouseY)
                    && bounds.contains(mouseX, mouseY)) {
                hoveredSkillId = node.definition().id();
            }
        }

        });
        renderSkillScrollIndicators(graphics, tree, viewport, scroll);
    }

    private List<SkillChoiceFrame> skillChoiceFrames(
            List<SkillDefinition> definitions,
            NexusSkillTreeLayout.Layout tree,
            UiBounds viewport,
            FullscreenViewport scroll
    ) {
        List<SkillChoiceFrame> frames = new ArrayList<>();
        Set<ResourceLocation> visibleIds = new LinkedHashSet<>();
        for (SkillDefinition definition : definitions) {
            visibleIds.add(definition.id());
        }

        int visibleGroupIndex = 0;
        for (SkillChoiceGroup group : SkillRegistry.choiceGroups()) {
            Set<ResourceLocation> memberIds = new LinkedHashSet<>();
            for (ResourceLocation memberId : group.memberIds()) {
                if (!visibleIds.contains(memberId)) {
                    continue;
                }
                NexusSkillTreeLayout.Node node = tree.node(memberId);
                if (node != null) {
                    memberIds.add(memberId);
                }
            }

            // A replacement target is the earlier form of the same loadout
            // choice. Include it in the visual family without changing its
            // catalog membership or serialized selection semantics.
            Set<ResourceLocation> replacementTargets = new LinkedHashSet<>();
            for (ResourceLocation memberId : memberIds) {
                NexusSkillTreeLayout.Node member = tree.node(memberId);
                if (member == null) {
                    continue;
                }
                member.definition().replacementTargetId().ifPresent(targetId -> {
                    if (visibleIds.contains(targetId) && tree.node(targetId) != null) {
                        replacementTargets.add(targetId);
                    }
                });
            }
            memberIds.addAll(replacementTargets);
            if (memberIds.size() < 2) {
                continue;
            }

            int color = SKILL_CHOICE_GROUP_COLORS[
                    visibleGroupIndex % SKILL_CHOICE_GROUP_COLORS.length
            ];
            int markerCount = visibleGroupIndex + 1;

            Map<ResourceLocation, List<NexusSkillTreeLayout.Node>> nodesByTier =
                    new LinkedHashMap<>();
            for (NexusSkillTreeLayout.Node node : tree.nodes()) {
                nodesByTier.computeIfAbsent(
                                node.definition().requiredTierId(),
                                ignored -> new ArrayList<>()
                        )
                        .add(node);
            }

            for (List<NexusSkillTreeLayout.Node> tierNodes : nodesByTier.values()) {
                tierNodes.sort(Comparator.comparingInt(NexusSkillTreeLayout.Node::y));
                List<NexusSkillTreeLayout.Node> contiguousMembers = new ArrayList<>();
                for (NexusSkillTreeLayout.Node node : tierNodes) {
                    if (memberIds.contains(node.definition().id())) {
                        contiguousMembers.add(node);
                        continue;
                    }
                    addSkillChoiceFrame(
                            frames,
                            contiguousMembers,
                            viewport,
                            scroll,
                            color,
                            markerCount
                    );
                    contiguousMembers.clear();
                }
                addSkillChoiceFrame(
                        frames,
                        contiguousMembers,
                        viewport,
                        scroll,
                        color,
                        markerCount
                );
            }
            visibleGroupIndex++;
        }
        return List.copyOf(frames);
    }

    private void addSkillChoiceFrame(
            List<SkillChoiceFrame> frames,
            List<NexusSkillTreeLayout.Node> members,
            UiBounds viewport,
            FullscreenViewport scroll,
            int color,
            int markerCount
    ) {
        if (members.isEmpty()) {
            return;
        }

        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        Set<ResourceLocation> memberIds = new LinkedHashSet<>();
        for (NexusSkillTreeLayout.Node member : members) {
            Rect bounds = skillNodeBounds(member, viewport, scroll);
            int padding = NexusSkillTreeLayout.CHOICE_FRAME_PADDING;
            left = Math.min(left, bounds.left() - padding);
            top = Math.min(top, bounds.top() - padding);
            right = Math.max(right, bounds.right() + padding);
            bottom = Math.max(bottom, bounds.bottom() + padding);
            memberIds.add(member.definition().id());
        }

        frames.add(new SkillChoiceFrame(
                new Rect(left, top, right, bottom),
                color,
                markerCount,
                Set.copyOf(memberIds)
        ));
    }

    private void renderSkillChoiceGroups(
            GuiGraphics graphics,
            List<SkillChoiceFrame> frames
    ) {
        for (SkillChoiceFrame frame : frames) {
            Rect bounds = frame.bounds();
            int color = frame.color();
            graphics.fill(
                    bounds.left(),
                    bounds.top(),
                    bounds.right(),
                    bounds.bottom(),
                    0x18000000 | (color & 0x00FFFFFF)
            );
            FullscreenControls.outline(
                    graphics,
                    bounds.left(),
                    bounds.top(),
                    bounds.width(),
                    bounds.height(),
                    color
            );

            int renderedMarkers = Math.min(frame.markerCount(), 5);
            for (int marker = 0; marker < renderedMarkers; marker++) {
                int markerLeft = bounds.left() + 3 + marker * 4;
                graphics.fill(
                        markerLeft,
                        bounds.top() - 1,
                        markerLeft + 2,
                        bounds.top() + 2,
                        color
                );
            }
        }
    }

    private void renderSkillConnections(
            GuiGraphics graphics,
            NexusSkillTreeLayout.Layout tree,
            UiBounds viewport,
            FullscreenViewport scroll,
            List<SkillChoiceFrame> choiceFrames
    ) {
        Map<ResourceLocation, Rect> boundsById = new LinkedHashMap<>();
        for (NexusSkillTreeLayout.Node node : tree.nodes()) {
            boundsById.put(
                    node.definition().id(),
                    skillNodeBounds(node, viewport, scroll)
            );
        }

        List<SkillRouteObstacle> obstacles = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Rect> entry : boundsById.entrySet()) {
            obstacles.add(new SkillRouteObstacle(
                    entry.getValue(),
                    Set.of(entry.getKey())
            ));
        }
        for (SkillChoiceFrame frame : choiceFrames) {
            obstacles.add(new SkillRouteObstacle(
                    frame.bounds(),
                    frame.memberIds()
            ));
        }

        Set<SkillConnection> replacementConnections =
                new LinkedHashSet<>();
        for (NexusSkillTreeLayout.Node node : tree.nodes()) {
            node.definition().replacementTargetId().ifPresent(targetId ->
                    replacementConnections.add(new SkillConnection(
                            targetId,
                            node.definition().id()
                    ))
            );
        }

        List<SkillRoute> routes = new ArrayList<>();
        // Prerequisites are rendered first so the distinctive replacement
        // route remains legible anywhere two independent routes meet.
        for (NexusSkillTreeLayout.Node node : tree.nodes()) {
            for (ResourceLocation prerequisiteId : node.definition().prerequisites()) {
                SkillConnection connection = new SkillConnection(
                        prerequisiteId,
                        node.definition().id()
                );
                if (replacementConnections.contains(connection)) {
                    // A replacement commonly also requires its target. It is
                    // one semantic edge, not a gray line plus an orange copy.
                    continue;
                }
                if (isVisuallyImpliedPrerequisite(
                        node.definition(),
                        prerequisiteId,
                        tree
                )) {
                    // Keep the full dependency in data/tooltips, but omit a
                    // redundant shortcut when another direct prerequisite
                    // already reaches this ancestor in the visible graph.
                    continue;
                }
                NexusSkillTreeLayout.Node prerequisite = tree.node(prerequisiteId);
                if (prerequisite == null) {
                    continue;
                }
                routes.add(new SkillRoute(connection, 0xAA8D8795, false));
            }
        }

        for (NexusSkillTreeLayout.Node node : tree.nodes()) {
            node.definition().replacementTargetId().ifPresent(targetId -> {
                if (!boundsById.containsKey(targetId)) {
                    return;
                }
                routes.add(new SkillRoute(
                        new SkillConnection(targetId, node.definition().id()),
                        replacementRouteColor(
                                node.definition().id(),
                                choiceFrames
                        ),
                        true
                ));
            });
        }

        Set<Integer> directRoutes = new HashSet<>();
        for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
            SkillRoute route = routes.get(routeIndex);
            Rect parent = boundsById.get(route.connection().parent());
            Rect child = boundsById.get(route.connection().child());
            ConnectionEndpoints endpoints = centeredConnectionEndpoints(parent, child);
            List<ConnectionPoint> direct = List.of(
                    endpoints.start(),
                    endpoints.end()
            );
            if (directSkillGeometryAllowed(endpoints.start(), endpoints.end())
                    && skillRouteIsClear(
                    direct,
                    obstacles,
                    route.connection(),
                    SKILL_ROUTE_CLEARANCE
            )) {
                directRoutes.add(routeIndex);
            }
        }
        Set<SkillRoutePort> crowdedDiagonalPorts =
                orthogonalizeCrowdedDiagonalFanOuts(
                        routes,
                        directRoutes,
                        boundsById
                );
        List<Integer> routeDrawOrder = skillRouteDrawOrder(
                routes,
                crowdedDiagonalPorts,
                boundsById
        );

        List<SkillRouteSegment> occupiedSegments = new ArrayList<>();
        for (int routeIndex : routeDrawOrder) {
            SkillRoute route = routes.get(routeIndex);
            Rect parent = boundsById.get(route.connection().parent());
            Rect child = boundsById.get(route.connection().child());
            drawSkillConnection(
                    graphics,
                    routeIndex,
                    route,
                    parent,
                    child,
                    routes,
                    directRoutes,
                    crowdedDiagonalPorts,
                    boundsById,
                    obstacles,
                    occupiedSegments
            );
        }
    }

    /**
     * Replacement links inherit the generated choice family's exact frame
     * color. Looking up the replacing child is deliberate: an earlier form
     * can be drawn inside the family without directly belonging to its
     * serialized choice group.
     */
    private static int replacementRouteColor(
            ResourceLocation replacementSkillId,
            List<SkillChoiceFrame> choiceFrames
    ) {
        for (SkillChoiceFrame frame : choiceFrames) {
            if (frame.memberIds().contains(replacementSkillId)) {
                return frame.color();
            }
        }
        return SKILL_REPLACEMENT_FALLBACK_COLOR;
    }

    /**
     * A pair of short, symmetric diagonals reads cleanly as one fork. With
     * three or more edges on the same side, however, even one diagonal makes
     * the remaining legs intersect the fork awkwardly. Convert the complete
     * port-side fan-out to orthogonal routes so every leg uses the same stable
     * six-pixel lane rhythm.
     */
    private Set<SkillRoutePort> orthogonalizeCrowdedDiagonalFanOuts(
            List<SkillRoute> routes,
            Set<Integer> directRoutes,
            Map<ResourceLocation, Rect> boundsById
    ) {
        Map<SkillRoutePort, List<Integer>> routesByPort =
                new LinkedHashMap<>();
        for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
            SkillConnection connection = routes.get(routeIndex).connection();
            addSkillRoutePort(
                    routesByPort,
                    routeIndex,
                    connection.parent(),
                    connection.child(),
                    boundsById
            );
            addSkillRoutePort(
                    routesByPort,
                    routeIndex,
                    connection.child(),
                    connection.parent(),
                    boundsById
            );
        }

        Set<Integer> orthogonalRoutes = new LinkedHashSet<>();
        Set<SkillRoutePort> crowdedPorts = new LinkedHashSet<>();
        for (Map.Entry<SkillRoutePort, List<Integer>> entry
                : routesByPort.entrySet()) {
            List<Integer> sharingPort = entry.getValue();
            if (sharingPort.size() < 3) {
                continue;
            }

            boolean hasDirectDiagonal = false;
            for (int routeIndex : sharingPort) {
                if (!directRoutes.contains(routeIndex)) {
                    continue;
                }
                SkillConnection connection =
                        routes.get(routeIndex).connection();
                ConnectionEndpoints endpoints = centeredConnectionEndpoints(
                        boundsById.get(connection.parent()),
                        boundsById.get(connection.child())
                );
                if (endpoints.start().y() != endpoints.end().y()) {
                    hasDirectDiagonal = true;
                    break;
                }
            }
            if (hasDirectDiagonal) {
                orthogonalRoutes.addAll(sharingPort);
                crowdedPorts.add(entry.getKey());
            }
        }
        directRoutes.removeAll(orthogonalRoutes);
        return Set.copyOf(crowdedPorts);
    }

    /**
     * Preserve the catalog's global edge order, but draw each crowded fork's
     * local legs before its farther-tier legs. The long leg can then route
     * around the completed local fork instead of becoming a divider that a
     * later short leg would have to cross.
     */
    private List<Integer> skillRouteDrawOrder(
            List<SkillRoute> routes,
            Set<SkillRoutePort> crowdedDiagonalPorts,
            Map<ResourceLocation, Rect> boundsById
    ) {
        List<Integer> result = new ArrayList<>(routes.size());
        for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
            result.add(routeIndex);
        }

        List<SkillRoutePort> stablePorts =
                new ArrayList<>(crowdedDiagonalPorts);
        stablePorts.sort(
                Comparator.comparing(
                                (SkillRoutePort port) ->
                                        port.nodeId().toString()
                        )
                        .thenComparing(SkillRoutePort::rightSide)
        );
        for (SkillRoutePort port : stablePorts) {
            List<Integer> members = new ArrayList<>();
            for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
                SkillConnection connection =
                        routes.get(routeIndex).connection();
                ResourceLocation otherId;
                if (connection.parent().equals(port.nodeId())) {
                    otherId = connection.child();
                } else if (connection.child().equals(port.nodeId())) {
                    otherId = connection.parent();
                } else {
                    continue;
                }
                if (usesRightPort(
                        port.nodeId(),
                        otherId,
                        boundsById
                ) == port.rightSide()) {
                    members.add(routeIndex);
                }
            }
            if (members.size() < 2) {
                continue;
            }

            Rect node = boundsById.get(port.nodeId());
            members.sort(
                    Comparator.comparingInt((Integer routeIndex) -> {
                                SkillConnection connection =
                                        routes.get(routeIndex).connection();
                                ResourceLocation otherId =
                                        connection.parent().equals(port.nodeId())
                                                ? connection.child()
                                                : connection.parent();
                                Rect other = boundsById.get(otherId);
                                return Math.abs(other.left() - node.left());
                            })
                            .thenComparingInt(routeIndex -> {
                                SkillConnection connection =
                                        routes.get(routeIndex).connection();
                                ResourceLocation otherId =
                                        connection.parent().equals(port.nodeId())
                                                ? connection.child()
                                                : connection.parent();
                                Rect other = boundsById.get(otherId);
                                return (other.top() + other.bottom()) / 2;
                            })
                            .thenComparingInt(Integer::intValue)
            );

            Set<Integer> memberSet = new LinkedHashSet<>(members);
            List<Integer> memberSlots = new ArrayList<>(members.size());
            for (int position = 0; position < result.size(); position++) {
                if (memberSet.contains(result.get(position))) {
                    memberSlots.add(position);
                }
            }
            for (int position = 0; position < members.size(); position++) {
                result.set(memberSlots.get(position), members.get(position));
            }
        }
        return List.copyOf(result);
    }

    private void addSkillRoutePort(
            Map<SkillRoutePort, List<Integer>> routesByPort,
            int routeIndex,
            ResourceLocation nodeId,
            ResourceLocation otherId,
            Map<ResourceLocation, Rect> boundsById
    ) {
        SkillRoutePort port = new SkillRoutePort(
                nodeId,
                usesRightPort(nodeId, otherId, boundsById)
        );
        routesByPort.computeIfAbsent(port, ignored -> new ArrayList<>())
                .add(routeIndex);
    }

    private boolean isVisuallyImpliedPrerequisite(
            SkillDefinition child,
            ResourceLocation prerequisiteId,
            NexusSkillTreeLayout.Layout tree
    ) {
        for (ResourceLocation siblingId : child.prerequisites()) {
            if (siblingId.equals(prerequisiteId)) {
                continue;
            }
            NexusSkillTreeLayout.Node sibling = tree.node(siblingId);
            if (hasVisiblePrerequisiteAncestor(
                    sibling,
                    prerequisiteId,
                    tree,
                    new java.util.LinkedHashSet<>()
            )) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVisiblePrerequisiteAncestor(
            NexusSkillTreeLayout.Node node,
            ResourceLocation ancestorId,
            NexusSkillTreeLayout.Layout tree,
            Set<ResourceLocation> visited
    ) {
        if (node == null || !visited.add(node.definition().id())) {
            return false;
        }
        for (ResourceLocation prerequisiteId : node.definition().prerequisites()) {
            if (prerequisiteId.equals(ancestorId)) {
                return true;
            }
            if (hasVisiblePrerequisiteAncestor(
                    tree.node(prerequisiteId),
                    ancestorId,
                    tree,
                    visited
            )) {
                return true;
            }
        }
        return false;
    }

    /**
     * Draws one semantic edge. Only a horizontal line or an adjacent-tier
     * exact 45-degree line may be drawn directly. Every other edge is routed
     * orthogonally through the tier gutters on a deterministic visibility
     * grid. Choice frames are first-class obstacles, not decoration.
     */
    private void drawSkillConnection(
            GuiGraphics graphics,
            int routeIndex,
            SkillRoute skillRoute,
            Rect parent,
            Rect child,
            List<SkillRoute> allRoutes,
            Set<Integer> directRoutes,
            Set<SkillRoutePort> crowdedDiagonalPorts,
            Map<ResourceLocation, Rect> boundsById,
            List<SkillRouteObstacle> obstacles,
            List<SkillRouteSegment> occupiedSegments
    ) {
        ConnectionEndpoints centered = centeredConnectionEndpoints(parent, child);
        List<ConnectionPoint> centeredRoute = List.of(
                centered.start(),
                centered.end()
        );
        boolean direct = directRoutes.contains(routeIndex)
                && !skillRouteConflictsWithOccupied(
                centeredRoute,
                occupiedSegments
        );
        Set<Integer> portDirectRoutes = directRoutes;
        if (!direct && directRoutes.contains(routeIndex)) {
            portDirectRoutes = new HashSet<>(directRoutes);
            portDirectRoutes.remove(routeIndex);
        }
        int startOffset = skillPortOffset(
                routeIndex,
                true,
                allRoutes,
                portDirectRoutes,
                crowdedDiagonalPorts,
                boundsById
        );
        int endOffset = skillPortOffset(
                routeIndex,
                false,
                allRoutes,
                portDirectRoutes,
                crowdedDiagonalPorts,
                boundsById
        );
        ConnectionPoint start = new ConnectionPoint(
                centered.start().x(),
                centered.start().y() + startOffset
        );
        ConnectionPoint end = new ConnectionPoint(
                centered.end().x(),
                centered.end().y() + endOffset
        );

        List<ConnectionPoint> route = List.of(start, end);
        if (!direct) {
            route = routeSkillOrthogonally(
                    start,
                    end,
                    skillRoute.connection(),
                    boundsById,
                    obstacles,
                    occupiedSegments,
                    crowdedDiagonalPorts
            );
        }

        int dashStep = 0;
        for (int index = 1; index < route.size(); index++) {
            ConnectionPoint from = route.get(index - 1);
            ConnectionPoint to = route.get(index);
            dashStep = drawLine(
                    graphics,
                    from.x(),
                    from.y(),
                    to.x(),
                    to.y(),
                    skillRoute.color(),
                    skillRoute.dashed(),
                    dashStep,
                    index == 1
            );
            occupiedSegments.add(new SkillRouteSegment(from, to));
        }
    }

    private ConnectionEndpoints centeredConnectionEndpoints(
            Rect parent,
            Rect child
    ) {
        if (parent.left() == child.left()) {
            return new ConnectionEndpoints(
                    new ConnectionPoint(
                            parent.right(),
                            (parent.top() + parent.bottom()) / 2
                    ),
                    new ConnectionPoint(
                            child.right(),
                            (child.top() + child.bottom()) / 2
                    )
            );
        }
        boolean leftToRight = parent.left() <= child.left();
        return new ConnectionEndpoints(
                new ConnectionPoint(
                        leftToRight ? parent.right() : parent.left(),
                        (parent.top() + parent.bottom()) / 2
                ),
                new ConnectionPoint(
                        leftToRight ? child.left() : child.right(),
                        (child.top() + child.bottom()) / 2
                )
        );
    }

    private boolean directSkillGeometryAllowed(
            ConnectionPoint start,
            ConnectionPoint end
    ) {
        int deltaX = Math.abs(end.x() - start.x());
        int deltaY = Math.abs(end.y() - start.y());
        if (deltaY == 0) {
            return true;
        }
        return deltaX == NexusSkillTreeLayout.COLUMN_GAP
                && deltaY == NexusSkillTreeLayout.COLUMN_GAP;
    }

    /**
     * Direct edges keep the center port. Orthogonal fan-outs receive stable
     * six-pixel ports. Crowded fan-outs keep local legs together before their
     * farther-tier legs; smaller fan-outs follow endpoint vertical order.
     */
    private int skillPortOffset(
            int routeIndex,
            boolean parentEndpoint,
            List<SkillRoute> routes,
            Set<Integer> directRoutes,
            Set<SkillRoutePort> crowdedDiagonalPorts,
            Map<ResourceLocation, Rect> boundsById
    ) {
        if (directRoutes.contains(routeIndex)) {
            return 0;
        }

        SkillConnection connection = routes.get(routeIndex).connection();
        ResourceLocation nodeId = parentEndpoint
                ? connection.parent()
                : connection.child();
        ResourceLocation otherId = parentEndpoint
                ? connection.child()
                : connection.parent();
        boolean rightPort = usesRightPort(nodeId, otherId, boundsById);

        List<Integer> sharing = new ArrayList<>();
        for (int candidateIndex = 0;
             candidateIndex < routes.size();
             candidateIndex++) {
            if (directRoutes.contains(candidateIndex)) {
                continue;
            }
            SkillConnection candidate = routes.get(candidateIndex).connection();
            ResourceLocation candidateOther;
            if (candidate.parent().equals(nodeId)) {
                candidateOther = candidate.child();
            } else if (candidate.child().equals(nodeId)) {
                candidateOther = candidate.parent();
            } else {
                continue;
            }
            if (usesRightPort(nodeId, candidateOther, boundsById) == rightPort) {
                sharing.add(candidateIndex);
            }
        }

        Comparator<Integer> portOrder = Comparator.comparingInt(index -> {
            SkillConnection candidate = routes.get(index).connection();
            ResourceLocation candidateOther =
                    candidate.parent().equals(nodeId)
                            ? candidate.child()
                            : candidate.parent();
            Rect other = boundsById.get(candidateOther);
            return (other.top() + other.bottom()) / 2;
        });
        SkillRoutePort currentPort = new SkillRoutePort(nodeId, rightPort);
        if (crowdedDiagonalPorts.contains(currentPort)) {
            // Keep the two local fork legs together and place a farther-tier
            // jog outside them. This avoids forcing the long leg between the
            // two short legs immediately after they leave the skill box.
            Rect node = boundsById.get(nodeId);
            portOrder = Comparator.comparingInt((Integer index) -> {
                        SkillConnection candidate =
                                routes.get(index).connection();
                        ResourceLocation candidateOther =
                                candidate.parent().equals(nodeId)
                                        ? candidate.child()
                                        : candidate.parent();
                        Rect other = boundsById.get(candidateOther);
                        return Math.abs(other.left() - node.left());
                    })
                    .thenComparing(portOrder);
        }
        sharing.sort(
                portOrder
                        .thenComparing(index ->
                                routes.get(index).connection().parent().toString())
                        .thenComparing(index ->
                                routes.get(index).connection().child().toString())
                        .thenComparingInt(Integer::intValue)
        );
        int position = sharing.indexOf(routeIndex);
        if (position < 0) {
            return 0;
        }

        boolean centerReserved = false;
        for (int candidateIndex : directRoutes) {
            SkillConnection candidate = routes.get(candidateIndex).connection();
            ResourceLocation candidateOther;
            if (candidate.parent().equals(nodeId)) {
                candidateOther = candidate.child();
            } else if (candidate.child().equals(nodeId)) {
                candidateOther = candidate.parent();
            } else {
                continue;
            }
            if (usesRightPort(nodeId, candidateOther, boundsById) == rightPort) {
                centerReserved = true;
                break;
            }
        }
        if (centerReserved) {
            if (sharing.size() == 1) {
                Rect node = boundsById.get(nodeId);
                Rect other = boundsById.get(otherId);
                int nodeCenter = (node.top() + node.bottom()) / 2;
                int otherCenter = (other.top() + other.bottom()) / 2;
                return otherCenter < nodeCenter
                        ? -SKILL_ROUTE_LANE_SPACING
                        : SKILL_ROUTE_LANE_SPACING;
            }
            int centeredIndex = position - sharing.size() / 2;
            if (centeredIndex >= 0) {
                centeredIndex++;
            }
            return centeredIndex * SKILL_ROUTE_LANE_SPACING;
        }

        int doubledOffset = position * 2 - (sharing.size() - 1);
        return doubledOffset * SKILL_ROUTE_LANE_SPACING / 2;
    }

    private boolean usesRightPort(
            ResourceLocation nodeId,
            ResourceLocation otherId,
            Map<ResourceLocation, Rect> boundsById
    ) {
        Rect node = boundsById.get(nodeId);
        Rect other = boundsById.get(otherId);
        return node.left() <= other.left();
    }

    private List<ConnectionPoint> routeSkillOrthogonally(
            ConnectionPoint start,
            ConnectionPoint end,
            SkillConnection connection,
            Map<ResourceLocation, Rect> boundsById,
            List<SkillRouteObstacle> obstacles,
            List<SkillRouteSegment> occupiedSegments,
            Set<SkillRoutePort> crowdedDiagonalPorts
    ) {
        SkillRoutePort startPort = new SkillRoutePort(
                connection.parent(),
                usesRightPort(
                        connection.parent(),
                        connection.child(),
                        boundsById
                )
        );
        SkillRoutePort endPort = new SkillRoutePort(
                connection.child(),
                usesRightPort(
                        connection.child(),
                        connection.parent(),
                        boundsById
                )
        );
        int startStubLength = skillRouteStubLength(
                start,
                boundsById.get(connection.parent()),
                crowdedDiagonalPorts.contains(startPort)
        );
        int endStubLength = skillRouteStubLength(
                end,
                boundsById.get(connection.child()),
                crowdedDiagonalPorts.contains(endPort)
        );
        ConnectionPoint gridStart;
        ConnectionPoint gridEnd;
        if (start.x() == end.x()) {
            gridStart = new ConnectionPoint(
                    start.x() + startStubLength,
                    start.y()
            );
            gridEnd = new ConnectionPoint(
                    end.x() + endStubLength,
                    end.y()
            );
        } else {
            int horizontalDirection = start.x() < end.x() ? 1 : -1;
            gridStart = new ConnectionPoint(
                    start.x() + horizontalDirection * startStubLength,
                    start.y()
            );
            gridEnd = new ConnectionPoint(
                    end.x() - horizontalDirection * endStubLength,
                    end.y()
            );
        }
        List<Integer> gridXs = skillRouteGridXs(
                gridStart,
                gridEnd,
                boundsById
        );
        List<Integer> gridYs = skillRouteGridYs(
                gridStart,
                gridEnd,
                obstacles,
                occupiedSegments
        );
        int startX = gridXs.indexOf(gridStart.x());
        int startY = gridYs.indexOf(gridStart.y());
        int endX = gridXs.indexOf(gridEnd.x());
        int endY = gridYs.indexOf(gridEnd.y());
        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
            return fallbackOrthogonalRoute(
                    start,
                    gridStart,
                    gridEnd,
                    end,
                    connection,
                    obstacles,
                    occupiedSegments
            );
        }

        SkillRouteGridState initial =
                new SkillRouteGridState(startX, startY, 0);
        Map<SkillRouteGridState, Long> distances = new HashMap<>();
        Map<SkillRouteGridState, SkillRouteGridState> previous = new HashMap<>();
        PriorityQueue<SkillRouteGridVisit> queue = new PriorityQueue<>(
                Comparator.comparingLong(SkillRouteGridVisit::distance)
                        .thenComparingInt(visit -> visit.state().yIndex())
                        .thenComparingInt(visit -> visit.state().xIndex())
                        .thenComparingInt(visit -> visit.state().direction())
        );
        distances.put(initial, 0L);
        queue.add(new SkillRouteGridVisit(initial, 0L));

        SkillRouteGridState destination = null;
        while (!queue.isEmpty()) {
            SkillRouteGridVisit visit = queue.remove();
            SkillRouteGridState state = visit.state();
            if (visit.distance() != distances.getOrDefault(state, Long.MAX_VALUE)) {
                continue;
            }
            if (state.xIndex() == endX && state.yIndex() == endY) {
                destination = state;
                break;
            }

            int[][] moves = {
                    {1, 0, 1},
                    {-1, 0, 1},
                    {0, 1, 2},
                    {0, -1, 2}
            };
            for (int[] move : moves) {
                int nextX = state.xIndex() + move[0];
                int nextY = state.yIndex() + move[1];
                if (nextX < 0 || nextX >= gridXs.size()
                        || nextY < 0 || nextY >= gridYs.size()) {
                    continue;
                }

                ConnectionPoint from = new ConnectionPoint(
                        gridXs.get(state.xIndex()),
                        gridYs.get(state.yIndex())
                );
                ConnectionPoint to = new ConnectionPoint(
                        gridXs.get(nextX),
                        gridYs.get(nextY)
                );
                if (!skillRouteSegmentIsClear(
                        from,
                        to,
                        obstacles,
                        connection,
                        SKILL_ROUTE_CLEARANCE
                )) {
                    continue;
                }

                int interactionPenalty = skillRouteInteractionPenalty(
                        from,
                        to,
                        occupiedSegments
                );
                if (interactionPenalty == Integer.MAX_VALUE) {
                    continue;
                }

                int length = Math.abs(to.x() - from.x())
                        + Math.abs(to.y() - from.y());
                long stepCost = length * 16L + interactionPenalty;
                if (state.direction() != 0
                        && state.direction() != move[2]) {
                    stepCost += SKILL_ROUTE_BEND_COST;
                }
                if (move[2] == 1) {
                    int middleX = (from.x() + to.x()) / 2;
                    int preferredY = interpolatedSkillRouteY(
                            start,
                            end,
                            middleX
                    );
                    stepCost += (long) Math.abs(from.y() - preferredY)
                            * Math.max(1, length);
                }

                SkillRouteGridState next =
                        new SkillRouteGridState(nextX, nextY, move[2]);
                long candidateDistance = visit.distance() + stepCost;
                if (candidateDistance >= distances.getOrDefault(
                        next,
                        Long.MAX_VALUE
                )) {
                    continue;
                }
                distances.put(next, candidateDistance);
                previous.put(next, state);
                queue.add(new SkillRouteGridVisit(next, candidateDistance));
            }
        }

        if (destination == null) {
            return fallbackOrthogonalRoute(
                    start,
                    gridStart,
                    gridEnd,
                    end,
                    connection,
                    obstacles,
                    occupiedSegments
            );
        }

        List<ConnectionPoint> reversed = new ArrayList<>();
        SkillRouteGridState cursor = destination;
        while (cursor != null) {
            reversed.add(new ConnectionPoint(
                    gridXs.get(cursor.xIndex()),
                    gridYs.get(cursor.yIndex())
            ));
            cursor = previous.get(cursor);
        }
        java.util.Collections.reverse(reversed);
        reversed.add(0, start);
        reversed.add(end);
        return compactSkillRoute(reversed);
    }

    private int skillRouteStubLength(
            ConnectionPoint endpoint,
            Rect endpointBounds,
            boolean spreadCrowdedFanOut
    ) {
        int centeredStub = NexusSkillTreeLayout.CHOICE_FRAME_PADDING
                + SKILL_ROUTE_CLEARANCE;
        if (!spreadCrowdedFanOut) {
            return centeredStub;
        }
        int centeredY = (endpointBounds.top() + endpointBounds.bottom()) / 2;
        int portOffset = endpoint.y() - centeredY;
        return Math.max(
                SKILL_ROUTE_CLEARANCE,
                centeredStub - portOffset
        );
    }

    private List<Integer> skillRouteGridXs(
            ConnectionPoint start,
            ConnectionPoint end,
            Map<ResourceLocation, Rect> boundsById
    ) {
        TreeSet<Integer> columns = new TreeSet<>();
        for (Rect bounds : boundsById.values()) {
            columns.add(bounds.left());
        }

        TreeSet<Integer> coordinates = new TreeSet<>();
        coordinates.add(start.x());
        coordinates.add(end.x());
        List<Integer> columnLefts = new ArrayList<>(columns);
        int inset = NexusSkillTreeLayout.CHOICE_FRAME_PADDING
                + SKILL_ROUTE_CLEARANCE;
        for (int index = 1; index < columnLefts.size(); index++) {
            int gapStart = columnLefts.get(index - 1)
                    + NexusSkillTreeLayout.NODE_WIDTH;
            int gapEnd = columnLefts.get(index);
            int firstLane = gapStart + inset;
            int secondLane = gapEnd - inset;
            if (firstLane <= secondLane) {
                coordinates.add(firstLane);
                coordinates.add(secondLane);
            } else {
                coordinates.add((gapStart + gapEnd) / 2);
            }
        }
        int outerOffset = inset + SKILL_ROUTE_OUTER_GAP;
        coordinates.add(columnLefts.get(0) - outerOffset);
        coordinates.add(
                columnLefts.get(columnLefts.size() - 1)
                        + NexusSkillTreeLayout.NODE_WIDTH
                        + outerOffset
        );
        return List.copyOf(coordinates);
    }

    private List<Integer> skillRouteGridYs(
            ConnectionPoint start,
            ConnectionPoint end,
            List<SkillRouteObstacle> obstacles,
            List<SkillRouteSegment> occupiedSegments
    ) {
        TreeSet<Integer> coordinates = new TreeSet<>();
        coordinates.add(start.y());
        coordinates.add(end.y());

        int minimum = Math.min(start.y(), end.y());
        int maximum = Math.max(start.y(), end.y());
        for (SkillRouteObstacle obstacle : obstacles) {
            Rect bounds = obstacle.bounds();
            int above = bounds.top() - SKILL_ROUTE_CLEARANCE;
            int below = bounds.bottom() + SKILL_ROUTE_CLEARANCE;
            coordinates.add(above);
            coordinates.add(below);
            minimum = Math.min(minimum, above);
            maximum = Math.max(maximum, below);
        }
        for (SkillRouteSegment occupied : occupiedSegments) {
            if (occupied.from().y() != occupied.to().y()) {
                continue;
            }
            coordinates.add(
                    occupied.from().y() - SKILL_ROUTE_LANE_SPACING
            );
            coordinates.add(
                    occupied.from().y() + SKILL_ROUTE_LANE_SPACING
            );
        }
        coordinates.add(minimum - SKILL_ROUTE_OUTER_GAP);
        coordinates.add(maximum + SKILL_ROUTE_OUTER_GAP);
        return List.copyOf(coordinates);
    }

    private int interpolatedSkillRouteY(
            ConnectionPoint start,
            ConnectionPoint end,
            int x
    ) {
        if (start.x() == end.x()) {
            return (start.y() + end.y()) / 2;
        }
        double progress = (double) (x - start.x())
                / (end.x() - start.x());
        return (int) Math.round(
                start.y() + (end.y() - start.y()) * progress
        );
    }

    private List<ConnectionPoint> fallbackOrthogonalRoute(
            ConnectionPoint start,
            ConnectionPoint gridStart,
            ConnectionPoint gridEnd,
            ConnectionPoint end,
            SkillConnection connection,
            List<SkillRouteObstacle> obstacles,
            List<SkillRouteSegment> occupiedSegments
    ) {
        int minimumY = Math.min(start.y(), end.y());
        int maximumY = Math.max(start.y(), end.y());
        for (SkillRouteObstacle obstacle : obstacles) {
            minimumY = Math.min(
                    minimumY,
                    obstacle.bounds().top() - SKILL_ROUTE_CLEARANCE
            );
            maximumY = Math.max(
                    maximumY,
                    obstacle.bounds().bottom() + SKILL_ROUTE_CLEARANCE
            );
        }

        for (int ring = 1; ring <= 4; ring++) {
            int offset = SKILL_ROUTE_OUTER_GAP
                    + (ring - 1) * SKILL_ROUTE_LANE_SPACING;
            int[] candidateYs = {
                    minimumY - offset,
                    maximumY + offset
            };
            for (int candidateY : candidateYs) {
                List<ConnectionPoint> candidate = compactSkillRoute(List.of(
                        start,
                        gridStart,
                        new ConnectionPoint(gridStart.x(), candidateY),
                        new ConnectionPoint(gridEnd.x(), candidateY),
                        gridEnd,
                        end
                ));
                if (!skillRouteIsClear(
                        candidate,
                        obstacles,
                        connection,
                        SKILL_ROUTE_CLEARANCE
                )) {
                    continue;
                }
                if (!skillRouteConflictsWithOccupied(
                        candidate,
                        occupiedSegments
                )) {
                    return candidate;
                }
            }
        }
        // Never trade one geometry invariant for another in an emergency.
        // Production layouts have a complete visibility grid, so reaching
        // this guard indicates a corrupt or unsupported future catalog.
        return List.of();
    }

    private List<ConnectionPoint> compactSkillRoute(
            List<ConnectionPoint> route
    ) {
        List<ConnectionPoint> compact = new ArrayList<>(route.size());
        for (ConnectionPoint point : route) {
            if (!compact.isEmpty()
                    && compact.get(compact.size() - 1).equals(point)) {
                continue;
            }
            while (compact.size() >= 2) {
                ConnectionPoint before =
                        compact.get(compact.size() - 2);
                ConnectionPoint last =
                        compact.get(compact.size() - 1);
                boolean sameVertical = before.x() == last.x()
                        && last.x() == point.x();
                boolean sameHorizontal = before.y() == last.y()
                        && last.y() == point.y();
                if (!sameVertical && !sameHorizontal) {
                    break;
                }
                compact.remove(compact.size() - 1);
            }
            compact.add(point);
        }
        return List.copyOf(compact);
    }

    private boolean skillRouteIsClear(
            List<ConnectionPoint> route,
            List<SkillRouteObstacle> obstacles,
            SkillConnection connection,
            int clearance
    ) {
        for (int index = 1; index < route.size(); index++) {
            if (!skillRouteSegmentIsClear(
                    route.get(index - 1),
                    route.get(index),
                    obstacles,
                    connection,
                    clearance
            )) {
                return false;
            }
        }
        return true;
    }

    private boolean skillRouteSegmentIsClear(
            ConnectionPoint from,
            ConnectionPoint to,
            List<SkillRouteObstacle> obstacles,
            SkillConnection connection,
            int clearance
    ) {
        for (SkillRouteObstacle obstacle : obstacles) {
            if (obstacle.endpointIds().contains(connection.parent())
                    || obstacle.endpointIds().contains(connection.child())) {
                continue;
            }
            if (lineIntersectsRect(
                    from,
                    to,
                    obstacle.bounds(),
                    clearance
            )) {
                return false;
            }
        }
        return true;
    }

    private boolean skillRouteConflictsWithOccupied(
            List<ConnectionPoint> route,
            List<SkillRouteSegment> occupiedSegments
    ) {
        for (int index = 1; index < route.size(); index++) {
            if (skillRouteInteractionPenalty(
                    route.get(index - 1),
                    route.get(index),
                    occupiedSegments
            ) == Integer.MAX_VALUE) {
                return true;
            }
        }
        return false;
    }

    private int skillRouteInteractionPenalty(
            ConnectionPoint from,
            ConnectionPoint to,
            List<SkillRouteSegment> occupiedSegments
    ) {
        int penalty = 0;
        for (SkillRouteSegment occupied : occupiedSegments) {
            ConnectionPoint occupiedFrom = occupied.from();
            ConnectionPoint occupiedTo = occupied.to();
            boolean horizontal = from.y() == to.y();
            boolean occupiedHorizontal =
                    occupiedFrom.y() == occupiedTo.y();
            boolean vertical = from.x() == to.x();
            boolean occupiedVertical =
                    occupiedFrom.x() == occupiedTo.x();

            if (!sharesEndpoint(from, to, occupiedFrom, occupiedTo)
                    && skillRouteSegmentDistanceSquared(
                    from,
                    to,
                    occupiedFrom,
                    occupiedTo
            ) < (double) SKILL_ROUTE_LANE_SPACING
                    * SKILL_ROUTE_LANE_SPACING) {
                return Integer.MAX_VALUE;
            }

            if (horizontal && occupiedHorizontal) {
                int overlap = intervalOverlap(
                        from.x(),
                        to.x(),
                        occupiedFrom.x(),
                        occupiedTo.x()
                );
                if (overlap > 0) {
                    int separation = Math.abs(from.y() - occupiedFrom.y());
                    if (separation < SKILL_ROUTE_LANE_SPACING) {
                        return Integer.MAX_VALUE;
                    }
                    if (separation > SKILL_ROUTE_LANE_SPACING
                            && separation < SKILL_ROUTE_LANE_SPACING * 2) {
                        penalty += SKILL_ROUTE_NEAR_PARALLEL_COST;
                    }
                }
                continue;
            }
            if (vertical && occupiedVertical) {
                int overlap = intervalOverlap(
                        from.y(),
                        to.y(),
                        occupiedFrom.y(),
                        occupiedTo.y()
                );
                if (overlap > 0) {
                    int separation = Math.abs(from.x() - occupiedFrom.x());
                    if (separation < SKILL_ROUTE_LANE_SPACING) {
                        return Integer.MAX_VALUE;
                    }
                    if (separation > SKILL_ROUTE_LANE_SPACING
                            && separation < SKILL_ROUTE_LANE_SPACING * 2) {
                        penalty += SKILL_ROUTE_NEAR_PARALLEL_COST;
                    }
                }
                continue;
            }
            if (!sharesEndpoint(from, to, occupiedFrom, occupiedTo)
                    && lineSegmentsIntersect(
                    from,
                    to,
                    occupiedFrom,
                    occupiedTo
            )) {
                return Integer.MAX_VALUE;
            }
        }
        return penalty;
    }

    private double skillRouteSegmentDistanceSquared(
            ConnectionPoint firstStart,
            ConnectionPoint firstEnd,
            ConnectionPoint secondStart,
            ConnectionPoint secondEnd
    ) {
        if (lineSegmentsIntersect(
                firstStart,
                firstEnd,
                secondStart,
                secondEnd
        )) {
            return 0.0D;
        }
        return Math.min(
                Math.min(
                        pointSegmentDistanceSquared(
                                firstStart,
                                secondStart,
                                secondEnd
                        ),
                        pointSegmentDistanceSquared(
                                firstEnd,
                                secondStart,
                                secondEnd
                        )
                ),
                Math.min(
                        pointSegmentDistanceSquared(
                                secondStart,
                                firstStart,
                                firstEnd
                        ),
                        pointSegmentDistanceSquared(
                                secondEnd,
                                firstStart,
                                firstEnd
                        )
                )
        );
    }

    private double pointSegmentDistanceSquared(
            ConnectionPoint point,
            ConnectionPoint start,
            ConnectionPoint end
    ) {
        double deltaX = end.x() - start.x();
        double deltaY = end.y() - start.y();
        double lengthSquared = deltaX * deltaX + deltaY * deltaY;
        if (lengthSquared == 0.0D) {
            double pointDeltaX = point.x() - start.x();
            double pointDeltaY = point.y() - start.y();
            return pointDeltaX * pointDeltaX
                    + pointDeltaY * pointDeltaY;
        }
        double projection = (
                (point.x() - start.x()) * deltaX
                        + (point.y() - start.y()) * deltaY
        ) / lengthSquared;
        projection = Math.max(0.0D, Math.min(1.0D, projection));
        double nearestX = start.x() + projection * deltaX;
        double nearestY = start.y() + projection * deltaY;
        double pointDeltaX = point.x() - nearestX;
        double pointDeltaY = point.y() - nearestY;
        return pointDeltaX * pointDeltaX
                + pointDeltaY * pointDeltaY;
    }

    private int intervalOverlap(
            int firstStart,
            int firstEnd,
            int secondStart,
            int secondEnd
    ) {
        int start = Math.max(
                Math.min(firstStart, firstEnd),
                Math.min(secondStart, secondEnd)
        );
        int end = Math.min(
                Math.max(firstStart, firstEnd),
                Math.max(secondStart, secondEnd)
        );
        return Math.max(0, end - start);
    }

    private boolean sharesEndpoint(
            ConnectionPoint firstStart,
            ConnectionPoint firstEnd,
            ConnectionPoint secondStart,
            ConnectionPoint secondEnd
    ) {
        return firstStart.equals(secondStart)
                || firstStart.equals(secondEnd)
                || firstEnd.equals(secondStart)
                || firstEnd.equals(secondEnd);
    }

    private boolean lineSegmentsIntersect(
            ConnectionPoint firstStart,
            ConnectionPoint firstEnd,
            ConnectionPoint secondStart,
            ConnectionPoint secondEnd
    ) {
        long first = orientation(firstStart, firstEnd, secondStart);
        long second = orientation(firstStart, firstEnd, secondEnd);
        long third = orientation(secondStart, secondEnd, firstStart);
        long fourth = orientation(secondStart, secondEnd, firstEnd);
        boolean strictIntersection =
                ((first > 0L && second < 0L)
                        || (first < 0L && second > 0L))
                && ((third > 0L && fourth < 0L)
                || (third < 0L && fourth > 0L));
        if (strictIntersection) {
            return true;
        }
        return first == 0L
                && pointWithinSegment(secondStart, firstStart, firstEnd)
                || second == 0L
                && pointWithinSegment(secondEnd, firstStart, firstEnd)
                || third == 0L
                && pointWithinSegment(firstStart, secondStart, secondEnd)
                || fourth == 0L
                && pointWithinSegment(firstEnd, secondStart, secondEnd);
    }

    private boolean pointWithinSegment(
            ConnectionPoint point,
            ConnectionPoint start,
            ConnectionPoint end
    ) {
        return point.x() >= Math.min(start.x(), end.x())
                && point.x() <= Math.max(start.x(), end.x())
                && point.y() >= Math.min(start.y(), end.y())
                && point.y() <= Math.max(start.y(), end.y());
    }

    private long orientation(
            ConnectionPoint start,
            ConnectionPoint end,
            ConnectionPoint point
    ) {
        return (long) (end.x() - start.x()) * (point.y() - start.y())
                - (long) (end.y() - start.y()) * (point.x() - start.x());
    }

    private boolean lineIntersectsRect(
            ConnectionPoint start,
            ConnectionPoint end,
            Rect rect,
            int clearance
    ) {
        // The expanded boundary itself is a valid lane: a line exactly six
        // pixels from a frame satisfies the six-pixel visual rhythm.
        double epsilon = 0.001D;
        double left = rect.left() - clearance + epsilon;
        double right = rect.right() + clearance - epsilon;
        double top = rect.top() - clearance + epsilon;
        double bottom = rect.bottom() + clearance - epsilon;
        double deltaX = end.x() - start.x();
        double deltaY = end.y() - start.y();
        double minimumT = 0.0D;
        double maximumT = 1.0D;

        if (deltaX == 0.0D) {
            if (start.x() <= left || start.x() >= right) {
                return false;
            }
        } else {
            double firstT = (left - start.x()) / deltaX;
            double secondT = (right - start.x()) / deltaX;
            minimumT = Math.max(minimumT, Math.min(firstT, secondT));
            maximumT = Math.min(maximumT, Math.max(firstT, secondT));
            if (minimumT > maximumT) {
                return false;
            }
        }

        if (deltaY == 0.0D) {
            return start.y() > top && start.y() < bottom;
        }
        double firstT = (top - start.y()) / deltaY;
        double secondT = (bottom - start.y()) / deltaY;
        minimumT = Math.max(minimumT, Math.min(firstT, secondT));
        maximumT = Math.min(maximumT, Math.max(firstT, secondT));
        return minimumT <= maximumT;
    }

    private void renderSkillTierHeaders(
            GuiGraphics graphics,
            NexusSkillTreeLayout.Layout tree,
            UiBounds viewport,
            FullscreenViewport scroll
    ) {
        for (int column = 0; column < tree.tiers().size(); column++) {
            AscendanceTierDefinition tier = tree.tiers().get(column);
            int centerX = scroll.pixelOriginX(true)
                    + NexusSkillTreeLayout.CONTENT_PADDING
                    + column * (NexusSkillTreeLayout.NODE_WIDTH
                    + NexusSkillTreeLayout.COLUMN_GAP)
                    + NexusSkillTreeLayout.NODE_WIDTH / 2;
            graphics.drawCenteredString(
                    font,
                    trimToWidth(
                            EssenceText.ascendanceTier(tier).getString(),
                            NexusSkillTreeLayout.NODE_WIDTH
                    ),
                    centerX,
                    scroll.pixelOriginY(true)
                            + NexusSkillTreeLayout.CONTENT_PADDING,
                    MUTED
            );
        }
    }

    private void renderSkillNode(
            GuiGraphics graphics,
            SkillDefinition definition,
            SkillNodeVisualState state,
            long cost,
            Rect bounds,
            boolean hovered
    ) {
        graphics.fill(
                bounds.left(),
                bounds.top(),
                bounds.right(),
                bounds.bottom(),
                state.fillColor()
        );
        FullscreenControls.outline(
                graphics,
                bounds.left(),
                bounds.top(),
                bounds.width(),
                bounds.height(),
                hovered ? BORDER_BRIGHT : state.borderColor()
        );

        String name = Component.translatable(definition.nameTranslationKey()).getString();
        String[] lines = wrapTwoLines(
                name,
                Math.max(1, bounds.width() - 18)
        );
        graphics.drawString(
                font,
                state.glyph(),
                bounds.left() + 4,
                bounds.top() + 4,
                state.textColor(),
                false
        );
        graphics.drawCenteredString(
                font,
                lines[0],
                (bounds.left() + bounds.right()) / 2 + 4,
                bounds.top() + 3,
                state.textColor()
        );
        if (!lines[1].isBlank()) {
            graphics.drawCenteredString(
                    font,
                    lines[1],
                    (bounds.left() + bounds.right()) / 2 + 4,
                    bounds.top() + 12,
                    state.textColor()
            );
        }

        if (hudControlVisible(definition)) {
            var receipt = ClientEssenceState.snapshot().ownedSkills().get(definition.id());
            graphics.drawString(font, com.mistaboom.essence_ascendance.client.nexus.NexusHudControl.glyph(receipt.hudEnabled()),
                    bounds.right() - 11, bounds.bottom() - 11, MUTED, false);
        }

        var snapshot = ClientEssenceState.snapshot();
        var purchase = snapshot.ownedSkills().get(definition.id());
        String renderedCost = SkillTooltipPresentation.nodePurchaseLabel(definition, currentBalanceProfile(snapshot),
                purchase == null ? 0 : purchase.rank(),
                amount -> formatLongForWidth(amount, Math.max(1, bounds.width() - 28))).getString();
        graphics.drawCenteredString(
                font,
                renderedCost,
                (bounds.left() + bounds.right()) / 2,
                bounds.bottom() - font.lineHeight - 2,
                state == SkillNodeVisualState.UNAFFORDABLE ? ERROR : DIM
        );
    }

    private void renderSkillScrollIndicators(
            GuiGraphics graphics,
            NexusSkillTreeLayout.Layout tree,
            UiBounds viewport,
            FullscreenViewport scroll
    ) {
        boolean canScrollLeft = scroll.maximumX() > 0.0
                && scroll.x() > 0.0;
        boolean canScrollRight = scroll.maximumX() > 0.0
                && scroll.x() < scroll.maximumX();

        if (canScrollLeft || canScrollRight) {
            int arrowY = (viewport.y() + viewport.bottom()
                    - SKILL_SCROLL_ARROW_HEIGHT) / 2;

            /*
             * GUI primitives can be batched by render type, so call order by
             * itself is not enough to guarantee that a late glyph stays over
             * skill and choice-frame rectangles. Isolate these generated-tree
             * indicators on a foreground layer below the modal layer.
             */
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, SKILL_OVERLAY_RENDER_DEPTH);
            try {
                if (canScrollLeft) {
                    renderSkillScrollArrow(
                            graphics,
                            viewport.x() + SKILL_SCROLL_ARROW_INSET,
                            arrowY,
                            "‹"
                    );
                }
                if (canScrollRight) {
                    renderSkillScrollArrow(
                            graphics,
                            viewport.right()
                                    - SKILL_SCROLL_ARROW_INSET
                                    - SKILL_SCROLL_ARROW_WIDTH,
                            arrowY,
                            "›"
                    );
                }
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
        if (tree.contentHeight() > viewport.height()) {
            String progress = EssenceText.gui(
                    "nexus.skills.scroll_position",
                    (int) Math.round(scroll.y()),
                    tree.contentHeight() - viewport.height()
            ).getString();
            graphics.drawString(
                    font,
                    trimToWidth(progress, Math.max(1, viewport.width() - 8)),
                    viewport.x() + 4,
                    viewport.bottom() - font.lineHeight - 2,
                    DIM,
                    false
            );
        }
    }

    private void renderSkillScrollArrow(
            GuiGraphics graphics,
            int x,
            int y,
            String glyph
    ) {
        float glyphY = y + (
                SKILL_SCROLL_ARROW_HEIGHT
                        - font.lineHeight * SKILL_SCROLL_ARROW_GLYPH_SCALE
        ) / 2.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(
                x + SKILL_SCROLL_ARROW_WIDTH / 2.0F,
                glyphY,
                0.0F
        );
        graphics.pose().scale(
                SKILL_SCROLL_ARROW_GLYPH_SCALE,
                SKILL_SCROLL_ARROW_GLYPH_SCALE,
                1.0F
        );
        graphics.drawCenteredString(font, glyph, 0, 0, TEXT);
        graphics.pose().popPose();
    }

    private void renderSkillsFooter(
            GuiGraphics graphics,
            NexusPageLayout layout
    ) {
        String text;
        int color = MUTED;
        if (transactionFeedback != null) {
            text = transactionFeedback.getString();
            color = ERROR;
        } else if (draft.invalidated()) {
            text = EssenceText.gui("nexus.draft.outdated").getString();
            color = ERROR;
        } else if (pendingRequestId >= 0L) {
            text = EssenceText.gui("nexus.transaction.waiting").getString();
            color = INCOMPLETE;
        } else if (hasStagedChanges()) {
            text = EssenceText.gui("nexus.skills.footer_staged").getString();
        } else {
            text = EssenceText.gui("nexus.skills.footer_default").getString();
        }

        graphics.drawCenteredString(
                font,
                trimToWidth(text, Math.max(80, layout.width() - 20)),
                (layout.left() + layout.right()) / 2,
                height - 12,
                color
        );
    }

    private UiBounds skillViewport(NexusPageLayout layout) {
        int left = Math.min(layout.right() - 2, layout.tracksLeft() + 3);
        int top = Math.min(layout.middleBottom() - 2, layout.middleTop() + SKILL_LEGEND_HEIGHT + 2);
        return new UiBounds(left, top, Math.max(1, layout.right() - 4 - left),
                Math.max(1, layout.middleBottom() - 2 - top));
    }

    private FullscreenViewport clampedSkillScroll(ResourceLocation essenceId,
                                                   NexusSkillTreeLayout.Layout tree, UiBounds bounds) {
        FullscreenViewport viewport = skillViewports.computeIfAbsent(essenceId, ignored -> new FullscreenViewport());
        viewport.configure(bounds, tree.contentWidth(), tree.contentHeight());
        return viewport;
    }

    private Rect skillNodeBounds(
            NexusSkillTreeLayout.Node node,
            UiBounds viewport,
            FullscreenViewport scroll
    ) {
        NexusSkillTreeLayout.Layout tree = skillLayout(node.definition().essenceId());
        int left = scroll.pixelOriginX(true) + node.x();
        int top = scroll.pixelOriginY(true) + node.y();
        return new Rect(
                left,
                top,
                left + NexusSkillTreeLayout.NODE_WIDTH,
                top + NexusSkillTreeLayout.NODE_HEIGHT
        );
    }

    private NexusSkillTreeLayout.Layout skillLayout(ResourceLocation essenceId) {
        return skillLayouts.computeIfAbsent(
                essenceId,
                id -> NexusSkillTreeLayout.build(SkillRegistry.values(id))
        );
    }

    private Map<ResourceLocation, SkillEvaluationResult> skillEvaluations() {
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        if (!snapshot.ready() || snapshot.tierId() == null) {
            return Map.of();
        }

        Map<ResourceLocation, Integer> authoritativeOwned = new LinkedHashMap<>();
        snapshot.ownedSkills().forEach((id, receipt) -> authoritativeOwned.put(id, receipt.rank()));
        Map<ResourceLocation, Integer> projectedOwned = draft.projectedRanks(snapshot);

        SkillEvaluationContext context = new SkillEvaluationContext(
                snapshot.tierId(),
                authoritativeOwned,
                projectedOwned,
                snapshot.loadoutSelections(),
                draft.finalLoadouts(snapshot),
                snapshot.completedMilestones(),
                Set.of(),
                bonusTotals(snapshot, false),
                bonusTotals(snapshot, true)
        );
        return SkillStateEvaluator.evaluateAll(SkillRegistry.values(), context);
    }

    private Map<ResourceLocation, Long> bonusTotals(
            ClientEssenceState.Snapshot snapshot,
            boolean projected
    ) {
        Map<ResourceLocation, Long> totals = new LinkedHashMap<>();
        for (NexusCategoryView category : categories()) {
            totals.put(
                    category.essence().id(),
                    projected
                            ? draft.projectedBonusInvestment(
                            category.essence().id(),
                            snapshot,
                            statEssenceIds()
                    )
                            : authoritativeBonusInvestment(category)
            );
        }
        return totals;
    }

    private long authoritativeBonusInvestment(NexusCategoryView category) {
        long total = 0L;
        for (NexusProgressionTrack track : category.tracks()) {
            total = safeAddNonNegative(
                    total,
                    Math.max(0L, track.state().storedInvestment())
            );
        }
        return total;
    }

    private long skillCost(SkillDefinition definition) {
        BalanceProfileDefinition profile = currentBalanceProfile(
                ClientEssenceState.snapshot()
        );
        if (profile == null) {
            return Long.MAX_VALUE;
        }
        try {
            var receipt = ClientEssenceState.snapshot().ownedSkills().get(definition.id());
            var runtime = EssenceConfigManager.clientRuntime();
            if (runtime == null) runtime = EssenceConfigManager.serverRuntime();
            int maximum = com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData
                    .maximumRank(runtime, definition);
            int targetRank = Math.min(maximum, receipt == null ? 1 : receipt.rank() + 1);
            return com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData
                    .rankCost(runtime, definition, targetRank);
        } catch (RuntimeException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private SkillNodeVisualState skillVisualState(
            SkillEvaluationResult evaluation,
            long cost,
            long projectedBalance
    ) {
        SkillDisplayState state = evaluation.projectedDisplayState();
        if (state == SkillDisplayState.ELIGIBLE
                && cost > projectedBalance) {
            return SkillNodeVisualState.UNAFFORDABLE;
        }
        return switch (state) {
            case LOCKED -> SkillNodeVisualState.LOCKED;
            case ELIGIBLE -> SkillNodeVisualState.ELIGIBLE;
            case STAGED -> SkillNodeVisualState.STAGED;
            case OWNED_ACTIVE -> SkillNodeVisualState.OWNED_ACTIVE;
            case OWNED_INACTIVE -> SkillNodeVisualState.OWNED_INACTIVE;
            case SUSPENDED -> SkillNodeVisualState.SUSPENDED;
            case WILL_SUSPEND -> SkillNodeVisualState.WILL_SUSPEND;
            case REPLACED -> SkillNodeVisualState.REPLACED;
        };
    }

    private int drawLine(
            GuiGraphics graphics,
            int startX,
            int startY,
            int endX,
            int endY,
            int color,
            boolean dashed,
            int initialStep,
            boolean includeStart
    ) {
        int dx = Math.abs(endX - startX);
        int dy = Math.abs(endY - startY);
        int sx = startX < endX ? 1 : -1;
        int sy = startY < endY ? 1 : -1;
        int error = dx - dy;
        int x = startX;
        int y = startY;
        int step = initialStep;
        boolean first = true;

        while (true) {
            if ((!first || includeStart)
                    && (!dashed || (step / 3) % 2 == 0)) {
                graphics.fill(x, y, x + 1, y + 1, color);
            }
            if (x == endX && y == endY) {
                return step;
            }
            int doubled = error * 2;
            if (doubled > -dy) {
                error -= dy;
                x += sx;
            }
            if (doubled < dx) {
                error += dx;
                y += sy;
            }
            step++;
            first = false;
        }
    }

    private void renderCategoryTitle(
            GuiGraphics graphics,
            NexusCategoryView category,
            NexusPageLayout layout
    ) {
        String value =
                EssenceText.essenceShort(category.essence()).getString().toUpperCase(Locale.ROOT);

        /*
         * Slightly enlarge the category heading without changing the fixed
         * top/bottom band geometry. Scaling around the calculated center keeps
         * the title visually centered in the existing top section.
         */
        float scale = 1.20F;
        int availableWidth =
                Math.max(40, layout.width() - 20);
        String rendered =
                trimToWidth(
                        value,
                        Math.max(
                                1,
                                (int) Math.floor(availableWidth / scale)
                        )
                );

        float scaledLineHeight =
                font.lineHeight * scale;
        float titleY =
                layout.top()
                        + Math.max(
                                1.0F,
                                (layout.sectionHeight() - scaledLineHeight) / 2.0F
                        );

        graphics.pose().pushPose();
        graphics.pose().translate(
                (layout.left() + layout.right()) / 2.0F,
                titleY,
                0.0F
        );
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(
                font,
                rendered,
                0,
                0,
                AscendancePalette.categoryArgb(category.essence().id())
        );
        graphics.pose().popPose();
    }

    private void renderAvailableGauge(
            GuiGraphics graphics,
            NexusCategoryView category,
            NexusPageLayout layout
    ) {
        int left = layout.gaugeLeft();
        int right = layout.gaugeRight();
        int top = layout.trackTop();
        int bottom = layout.trackBottom();
        int centerX = (left + right) / 2;

        int availableLabelWidth =
                Math.max(
                        1,
                        layout.tracksLeft() - layout.left() - 6
                );
        String availableLabel =
                trimToWidth(
                        EssenceText.gui("nexus.available").getString(),
                        availableLabelWidth
                );

        graphics.drawCenteredString(
                font,
                availableLabel,
                centerX,
                top - font.lineHeight - GAUGE_TEXT_GAP,
                MUTED
        );

        graphics.fill(
                left,
                top,
                right,
                bottom,
                PANEL_INNER
        );
        FullscreenControls.outline(
                graphics,
                left,
                top,
                right - left,
                bottom - top,
                BORDER
        );

        long available =
                stagedAvailableEssence(category);

        double invested =
                stagedTotalInvestment(category);

        double fillFraction;
        if (available <= 0L) {
            fillFraction = 0.0;
        } else if (invested <= 0.0) {
            fillFraction = 1.0;
        } else {
            double total =
                    (double) available + invested;
            fillFraction =
                    total <= 0.0
                            ? 0.0
                            : Math.min(1.0, available / total);
        }

        int innerTop = top + 3;
        int innerBottom = bottom - 3;
        int fillHeight =
                (int) Math.round(
                        (innerBottom - innerTop) * fillFraction
                );

        graphics.fill(
                left + 3,
                innerBottom - fillHeight,
                right - 3,
                innerBottom,
                AscendancePalette.categoryArgb(category.essence().id())
        );

        graphics.drawCenteredString(
                font,
                formatLongForWidth(
                        available,
                        availableLabelWidth
                ),
                centerX,
                bottom + GAUGE_TEXT_GAP,
                TEXT
        );
    }

    private void renderTierGuides(
            GuiGraphics graphics,
            NexusCategoryView category,
            NexusPageLayout layout
    ) {
        List<AscendanceTierDefinition> tiers =
                orderedTiers();

        if (tiers.isEmpty()) {
            return;
        }

        int trackHeight =
                layout.trackBottom() - layout.trackTop();

        if (category.tracks().isEmpty()) return;
        var resolved = category.tracks().getFirst().state().track();
        var geometry = new NexusBonusTrackLayout(resolved, layout.trackTop(), layout.trackBottom());

        for (int boundary = 0; boundary < resolved.checkpoints().size(); boundary++) {
            double fraction = resolved.tierPositions().get(boundary);
            if (fraction <= 0) continue;
            int y =
                    layout.trackBottom()
                            - (int) Math.round(trackHeight * fraction);

            graphics.fill(
                    layout.tracksLeft(),
                    y,
                    layout.tracksRight(),
                    y + 1,
                    TIER_LINE
            );

            int tierLabelWidth = Math.max(1, layout.right() - layout.tracksRight() - 6);
            String label = tierDisplayName(resolved.checkpoints().get(boundary).tierId());
            float labelScale = Math.min(1, tierLabelWidth / (float) Math.max(1, font.width(label)));
            graphics.pose().pushPose();
            graphics.pose().translate((layout.tracksRight() + layout.right()) / 2.0F,
                    geometry.bandCenterY(boundary) - font.lineHeight * labelScale / 2.0F, 0);
            graphics.pose().scale(labelScale, labelScale, 1);
            graphics.drawCenteredString(font, label, 0, 0,
                    AscendancePalette.tierMetalArgb(resolved.checkpoints().get(boundary).tierId()));
            graphics.pose().popPose();
        }
    }

    private void renderTracks(
            GuiGraphics graphics,
            NexusCategoryView category,
            NexusPageLayout layout,
            int mouseX,
            int mouseY
    ) {
        List<NexusProgressionTrack> tracks =
                category.tracks();

        int visibleCount =
                effectiveVisibleTrackCount(
                        tracks.size(),
                        layout
                );

        clampTrackWindow(
                tracks.size(),
                visibleCount
        );

        if (tracks.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    EssenceText.gui("nexus.no_stats").getString(),
                    (layout.tracksLeft() + layout.tracksRight()) / 2,
                    layout.trackTop() + 24,
                    MUTED
            );
            return;
        }

        int viewportWidth =
                layout.tracksRight() - layout.tracksLeft();
        int trackWidth =
                trackWidth(
                        viewportWidth,
                        visibleCount
                );

        int end =
                Math.min(
                        tracks.size(),
                        trackWindowStart + visibleCount
                );

        int x =
                trackContentLeft(
                        layout,
                        visibleCount,
                        trackWidth
                );

        for (int i = trackWindowStart; i < end; i++) {
            NexusProgressionTrack track =
                    tracks.get(i);

            renderTrack(
                    graphics,
                    track,
                    i,
                    x,
                    trackWidth,
                    layout,
                    mouseX,
                    mouseY
            );

            if (i + 1 < end) {
                int dividerX =
                        x + trackWidth + TRACK_GAP / 2;
                int dividerBottom =
                        Math.min(
                                layout.middleBottom() - 2,
                                layout.trackBottom() + 31
                        );
                graphics.fill(
                        dividerX,
                        layout.middleTop() + 2,
                        dividerX + 1,
                        Math.max(
                                layout.middleTop() + 3,
                                dividerBottom
                        ),
                        0x554C4655
                );
            }

            x += trackWidth + TRACK_GAP;
        }

        if (tracks.size() > visibleCount) {
            String page =
                    (trackWindowStart + 1)
                            + "–"
                            + end
                            + " / "
                            + tracks.size();
            graphics.drawCenteredString(
                    font,
                    page,
                    (layout.tracksLeft() + layout.tracksRight()) / 2,
                    bottomControlsY(layout) - 11,
                    DIM
            );
        }
    }

    private void renderTrack(
            GuiGraphics graphics,
            NexusProgressionTrack track,
            int absoluteIndex,
            int left,
            int trackWidth,
            NexusPageLayout layout,
            int mouseX,
            int mouseY
    ) {
        int centerX =
                left + trackWidth / 2;
        var resolved = track.state().track();
        boolean available = resolved.available();
        int accent = AscendancePalette.categoryArgb(track.stat().essenceType().id());
        var geometry = track.layout(layout.trackTop(), layout.trackBottom());
        int capColor = AscendancePalette.tierMetalArgb(geometry.capUnlockTier(ClientEssenceState.snapshot().tierId()));

        String[] nameLines =
                wrapTwoLines(
                        EssenceText.stat(track.stat()).getString(),
                        trackWidth - 6
                );

        graphics.drawCenteredString(
                font,
                nameLines[0],
                centerX,
                layout.middleTop() + 3,
                available ? accent : DIM
        );
        if (!nameLines[1].isBlank()) {
            graphics.drawCenteredString(
                    font,
                    nameLines[1],
                    centerX,
                    layout.middleTop() + 13,
                    available ? accent : DIM
            );
        }

        String cap = formatLongForWidth(track.state().currentInvestmentCap(), Math.max(1, trackWidth - 4));
        graphics.drawCenteredString(
                font,
                trimToWidth(cap, trackWidth - 2),
                centerX,
                layout.trackTop() - 11,
                capColor
        );

        int top = layout.trackTop();
        int bottom = layout.trackBottom();
        int railLeft = centerX - 3;
        int railRight = centerX + 3;
        int startY = geometry.startY();
        int completionY = geometry.completionY();
        double maximumProgress = BonusTrackCurve.maximumProgression(resolved.checkpoints(), ClientEssenceState.snapshot().tierId());
        int ceilingY = geometry.yForEffect(maximumProgress);

        if (available) {
            // A rail occupies only this Bonus's useful span. No continuation implies later purchases.
            graphics.fill(railLeft - 1, completionY, railRight + 1, startY, 0xFF1A1A1E);
            graphics.fill(railLeft, completionY, railRight, startY, TRACK);
            if (ceilingY > completionY) {
                graphics.fill(railLeft, completionY, railRight, ceilingY, TRACK_LOCKED);
                for (int y = completionY + 2; y < ceilingY - 1; y += 6)
                    graphics.fill(railLeft, y, railRight, y + 1, TRACK_LOCKED_TICK);
            }
            for (int i = 0; i < resolved.checkpoints().size(); i++) {
                var checkpoint = resolved.checkpoints().get(i);
                if (!checkpoint.purchasable() || checkpoint.tierId().equals(resolved.completionTier())) continue;
                int y = geometry.yForPosition(resolved.tierPositions().get(i));
                graphics.fill(railLeft - 2, y, railRight + 2, y + 1,
                        AscendancePalette.tierMetalArgb(geometry.boundaryUnlockTier(i)));
            }
            // Wider than the knob so the unlocking tier remains visible at zero/full investment.
            int startColor = AscendancePalette.tierMetalArgb(resolved.startTier());
            int completionColor = AscendancePalette.tierMetalArgb(resolved.completionTier());
            graphics.fill(railLeft - 7, startY, railRight + 7, startY + 1, startColor);
            graphics.fill(railLeft - 7, startY - 3, railLeft - 5, startY + 1, startColor);
            graphics.fill(railRight + 5, startY - 3, railRight + 7, startY + 1, startColor);
            graphics.fill(railLeft - 7, completionY - 1, railRight + 7, completionY + 1, completionColor);
            graphics.fill(railLeft - 7, completionY + 3, railRight + 7, completionY + 4, completionColor);
            graphics.fill(railLeft - 7, completionY, railLeft - 5, completionY + 4, completionColor);
            graphics.fill(railRight + 5, completionY, railRight + 7, completionY + 4, completionColor);
            if (track.state().currentInvestmentCap() > 0) {
                graphics.fill(left + 5, ceilingY, left + trackWidth - 5, ceilingY + 1, capColor);
                graphics.fill(railLeft - 4, ceilingY - 2, railLeft - 2, ceilingY + 3, capColor);
                graphics.fill(railRight + 2, ceilingY - 2, railRight + 4, ceilingY + 3, capColor);
            }
        } else {
            for (int y = top + 2; y < bottom; y += 7)
                graphics.fill(centerX - 2, y, centerX + 2, y + 1, TRACK_LOCKED_TICK);
        }

        long stagedTarget =
                stagedInvestment(track);
        boolean changed =
                stagedTarget != track.state().storedInvestment();
        boolean dragging =
                draggingTrackIndex == absoluteIndex;

        var tierId = ClientEssenceState.snapshot().tierId();
        int knobY = track.handleY(stagedTarget, tierId, top, bottom);
        int currentY = track.handleY(track.state().storedInvestment(), tierId, top, bottom);
        int earnedY = geometry.yForEffect(stagedProgression(track, stagedTarget));
        int fill = accent;

        graphics.fill(
                railLeft,
                knobY,
                railRight,
                startY,
                available ? (fill & 0x00FFFFFF) | 0x66000000 : TRACK_LOCKED
        );

        // Solid fill is earned power; the softer extension and handle show funding toward the next benefit.
        if (available) graphics.fill(railLeft, earnedY, railRight, startY, fill);

        if (changed) graphics.fill(railLeft - 3, currentY, railRight + 3, currentY + 1, accent);

        int knobWidth = 14;
        graphics.fill(
                centerX - knobWidth / 2,
                knobY - TRACK_KNOB_HEIGHT / 2,
                centerX + knobWidth / 2,
                knobY + (TRACK_KNOB_HEIGHT + 1) / 2,
                !available || track.state().currentInvestmentCap() == 0 ? DIM : fill
        );
        graphics.fill(centerX - 4, knobY, centerX + 4, knobY + 1, 0xAAFFFFFF);
        if (changed || dragging) FullscreenControls.outline(graphics, centerX - knobWidth / 2 - 1,
                knobY - TRACK_KNOB_HEIGHT / 2 - 1, knobWidth + 2, TRACK_KNOB_HEIGHT + 2, 0xBBFFFFFF);

        int hitTop =
                top - TRACK_HIT_PADDING;
        int hitBottom =
                bottom + TRACK_HIT_PADDING;

        boolean hovered =
                mouseX >= left
                        && mouseX < left + trackWidth
                        && mouseY >= hitTop
                        && mouseY <= hitBottom;

        if (mouseX >= left && mouseX < left + trackWidth
                && mouseY >= top - 12 && mouseY <= hitBottom) {
            hoveredTrack = track;
        }

        if (hovered && !dragging) {
            FullscreenControls.outline(
                    graphics,
                    left + 1,
                    hitTop,
                    Math.max(1, trackWidth - 2),
                    Math.max(1, hitBottom - hitTop),
                    (accent & 0x00FFFFFF) | 0x66000000
            );
        }

        String bonus = compactBonus(resolved.unit(), stagedScaledBonus(track, stagedTarget));
        float bonusScale = Math.min(1.0F, Math.max(.7F, (trackWidth - 2.0F) / Math.max(1, font.width(bonus))));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, bottom + 9, 0);
        graphics.pose().scale(bonusScale, bonusScale, 1);
        graphics.drawCenteredString(font, trimToWidth(bonus, (int) ((trackWidth - 2) / bonusScale)),
                0, 0, available ? TEXT : DIM);
        graphics.pose().popPose();
    }

    private void renderTrackTooltip(
            GuiGraphics graphics,
            NexusProgressionTrack track,
            int mouseX,
            int mouseY
    ) {
        var tooltip = BonusTooltipPresentation.tooltip(track, ClientEssenceState.snapshot().tierId(), stagedInvestment(track));
        graphics.renderTooltip(font, tooltip.wrap(font, TooltipLayout.compactWidth(280, width)), mouseX, mouseY);
    }

    private boolean primaryActionEnabled() {
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        return snapshot.ready()
                && pendingRequestId < 0L
                && draft.hasChanges(snapshot);
    }

    private Component primaryActionLabel() {
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        if (pendingRequestId >= 0L) {
            return EssenceText.gui("nexus.action.applying");
        }
        if (draft.invalidated()) {
            return EssenceText.gui("nexus.discard_draft");
        }

        boolean bonus = draft.hasBonusChanges(snapshot);
        boolean purchase = draft.hasPurchaseChanges();
        boolean loadout = draft.hasLoadoutChanges(snapshot);
        int kinds = (bonus ? 1 : 0) + (purchase ? 1 : 0) + (loadout ? 1 : 0);
        ResourceLocation visibleEssence = selectedEssenceId();

        if (kinds == 1
                && bonus
                && navigation.mode() == NexusMode.BONUSES
                && bonusChangesBelongTo(visibleEssence, snapshot)) {
            return EssenceText.gui("nexus.allocate");
        }
        if (kinds == 1
                && purchase
                && navigation.mode() == NexusMode.SKILLS
                && purchaseChangesBelongTo(visibleEssence)) {
            return EssenceText.gui("nexus.purchase");
        }
        if (kinds == 1
                && loadout
                && navigation.mode() == NexusMode.SKILLS
                && loadoutChangesBelongTo(visibleEssence, snapshot)) {
            return EssenceText.gui("nexus.apply_loadout");
        }
        if (kinds > 0) {
            return EssenceText.gui("nexus.apply_changes");
        }
        return navigation.mode() == NexusMode.SKILLS
                ? EssenceText.gui("nexus.purchase")
                : EssenceText.gui("nexus.allocate");
    }

    private ResourceLocation selectedEssenceId() {
        List<NexusCategoryView> categories = categories();
        if (categories.isEmpty()) {
            return null;
        }
        stabilizeSelection(categories);
        return categories.get(selectedCategoryIndex).essence().id();
    }

    private boolean bonusChangesBelongTo(
            ResourceLocation essenceId,
            ClientEssenceState.Snapshot snapshot
    ) {
        if (essenceId == null) {
            return false;
        }
        Map<ResourceLocation, ResourceLocation> statEssences = statEssenceIds();
        for (ResourceLocation statId : draft.changedBonusIds(snapshot)) {
            if (!essenceId.equals(statEssences.get(statId))) {
                return false;
            }
        }
        return true;
    }

    private boolean purchaseChangesBelongTo(ResourceLocation essenceId) {
        if (essenceId == null) {
            return false;
        }
        for (NexusDraft.SkillPurchaseDraft purchase : draft.stagedPurchases()) {
            if (!essenceId.equals(purchase.essenceId())) {
                return false;
            }
        }
        return true;
    }

    private boolean loadoutChangesBelongTo(
            ResourceLocation essenceId,
            ClientEssenceState.Snapshot snapshot
    ) {
        if (essenceId == null) {
            return false;
        }

        Map<ResourceLocation, ResourceLocation> projected =
                draft.finalLoadouts(snapshot);
        Set<ResourceLocation> slots = new java.util.LinkedHashSet<>();
        slots.addAll(snapshot.loadoutSelections().keySet());
        slots.addAll(projected.keySet());
        for (ResourceLocation slotId : slots) {
            ResourceLocation committedSkill = snapshot.loadoutSelections().get(slotId);
            ResourceLocation projectedSkill = projected.get(slotId);
            if (java.util.Objects.equals(committedSkill, projectedSkill)) {
                continue;
            }
            ResourceLocation representative = projectedSkill != null
                    ? projectedSkill
                    : committedSkill;
            SkillDefinition skill = representative == null
                    ? null
                    : SkillRegistry.get(representative).orElse(null);
            if (skill == null || !essenceId.equals(skill.essenceId())) {
                return false;
            }
        }
        return true;
    }

    private List<UiBounds> footerControls(NexusPageLayout layout) {
        return FullscreenLayout.fixedWidthsRow(new UiBounds(layout.left(), bottomControlsY(layout), layout.width(), BOTTOM_CONTROL_HEIGHT),
                List.of(TRACK_ARROW_WIDTH, ALLOCATE_BUTTON_WIDTH, TRACK_ARROW_WIDTH), BOTTOM_CONTROL_GAP);
    }

    private UiBounds actionBounds() {
        NexusPageLayout layout = contentLayout();
        if (navigation.mode() != NexusMode.ASCENDANCE) return footerControls(layout).get(1);
        return FullscreenLayout.fixedWidthsRow(new UiBounds(layout.left(), bottomControlsY(layout), layout.width(), BOTTOM_CONTROL_HEIGHT),
                List.of(ASCEND_BUTTON_WIDTH), 0).getFirst();
    }

    private int bottomControlsY(
            NexusPageLayout layout
    ) {
        return layout.bottom()
                - BOTTOM_CONTROL_HEIGHT
                - 3;
    }

    private void renderFooter(
            GuiGraphics graphics,
            NexusCategoryView category,
            NexusPageLayout layout
    ) {
        String instruction;
        int color = AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT);
        if (transactionFeedback != null) {
            instruction = transactionFeedback.getString();
            color = AscendanceUiPalette.argb(AscendanceUiPalette.ERROR);
        } else if (draft.invalidated()) {
            instruction = EssenceText.gui("nexus.draft.outdated").getString();
            color = AscendanceUiPalette.argb(AscendanceUiPalette.ERROR);
        } else if (pendingRequestId >= 0L) {
            instruction = EssenceText.gui("nexus.transaction.waiting").getString();
            color = AscendanceUiPalette.argb(AscendanceUiPalette.INFORMATION);
        } else {
            instruction = hasStagedChanges()
                    ? EssenceText.gui("nexus.footer_staged_global").getString()
                    : EssenceText.gui("nexus.footer_default").getString();
        }

        graphics.drawCenteredString(
                font,
                trimToWidth(
                        instruction,
                        Math.max(80, layout.width() - 20)
                ),
                (layout.left() + layout.right()) / 2,
                height - 12,
                color
        );
    }

    @Override
    public void onClose() {
        if (!canPromptForExit()) {
            super.onClose();
            return;
        }

        if (pendingRequestId >= 0L) {
            pendingDecision = PendingDecision.EXIT;
            pendingCompletion = PendingCompletion.EXIT;
            return;
        }

        if (hasStagedChanges()) {
            pendingDecision = PendingDecision.EXIT;
            pendingCompletion = PendingCompletion.NONE;
            transactionFeedback = null;
            return;
        }

        super.onClose();
    }

    private boolean hudControlVisible(SkillDefinition skill) {
        var evaluation = skillEvaluations().get(skill.id());
        return evaluation != null && com.mistaboom.essence_ascendance.client.nexus.NexusHudControl.visible(
                modeAvailable(NexusMode.SKILLS), evaluation.owned(), evaluation.effective());
    }

    private boolean handleHudControlClick(double mouseX, double mouseY, int button) {
        if (navigation.mode() != NexusMode.SKILLS || !modeAvailable(NexusMode.SKILLS)) return false;
        var categories = categories();
        if (categories.isEmpty()) return false;
        stabilizeSelection(categories);
        var category = categories.get(selectedCategoryIndex);
        var viewport = skillViewport(contentLayout());
        if (!viewport.contains(mouseX, mouseY)) return false;
        var tree = skillLayout(category.essence().id());
        var scroll = clampedSkillScroll(category.essence().id(), tree, viewport);
        for (var node : tree.nodes()) {
            var bounds = skillNodeBounds(node, viewport, scroll);
            if (!hudControlVisible(node.definition()) || !com.mistaboom.essence_ascendance.client.nexus.NexusHudControl.hit(
                    mouseX, mouseY, bounds.right(), bounds.bottom())) continue;
            if (button == 0) {
                var receipt = ClientEssenceState.snapshot().ownedSkills().get(node.definition().id());
                dev.architectury.networking.NetworkManager.sendToServer(
                        new com.mistaboom.essence_ascendance.network.SkillHudPreferencePayload(
                                menu.containerId, node.definition().id(), !receipt.hudEnabled()));
            }
            return true; // Consume every button here before node selection, purchasing or panning.
        }
        return false;
    }

    private SkillDefinition skillAt(
            NexusCategoryView category,
            double mouseX,
            double mouseY,
            NexusPageLayout layout
    ) {
        UiBounds viewport = skillViewport(layout);
        if (!viewport.contains(mouseX, mouseY)) {
            return null;
        }

        NexusSkillTreeLayout.Layout tree = skillLayout(category.essence().id());
        FullscreenViewport scroll = clampedSkillScroll(
                category.essence().id(),
                tree,
                viewport
        );
        for (NexusSkillTreeLayout.Node node : tree.nodes()) {
            if (skillNodeBounds(node, viewport, scroll).contains(mouseX, mouseY)) {
                return node.definition();
            }
        }
        return null;
    }

    private void handleSkillClick(
            SkillDefinition skill,
            NexusCategoryView category
    ) {
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        if (!snapshot.ready()
                || pendingRequestId >= 0L
                || draft.invalidated()) {
            return;
        }

        Map<ResourceLocation, SkillEvaluationResult> evaluations =
                skillEvaluations();
        SkillEvaluationResult evaluation = evaluations.get(skill.id());
        if (evaluation == null) {
            return;
        }

        transactionFeedback = null;
        if (evaluation.staged()) {
            unstagePurchaseCascade(skill.id(), snapshot);
            return;
        }

        if (!evaluation.projectedOwned() || (hasShiftDown() && evaluation.eligibleToPurchase())) {
            long cost = skillCost(skill);
            long available = stagedAvailableEssence(category);
            if (!evaluation.eligibleToPurchase() || cost > available) {
                return;
            }

            draft.stagePurchase(skill.id(), skill.essenceId(), cost, evaluation.currentRank() + 1);
            stageSelectionForNewPurchase(skill, snapshot);
            return;
        }

        switch (skill.activationPolicy()) {
            case AUTOMATIC -> {
                if (evaluation.projectedReplaced()) {
                    stageReplacementFallback(skill, evaluation, snapshot);
                } else if (evaluation.projectedEffective()) {
                    draft.stageLoadout(skill.id(), skill.id());
                } else if (!evaluation.projectedSuspended()) {
                    stageAutomaticActivation(skill, snapshot);
                }
            }
            case SELECTABLE -> skill.choiceGroupId().ifPresent(groupId -> {
                ResourceLocation selected = draft.projectedLoadout(groupId, snapshot);
                if (skill.id().equals(selected)) {
                    draft.stageLoadout(groupId, null);
                } else {
                    stageActivationSelections(skill, snapshot);
                }
            });
            case TOGGLE -> {
                ResourceLocation selected = draft.projectedLoadout(skill.id(), snapshot);
                if (skill.id().equals(selected)) {
                    draft.stageLoadout(skill.id(), null);
                } else if (stageActivationSelections(skill, snapshot)) {
                    draft.stageLoadout(skill.id(), skill.id());
                }
            }
        }
    }

    private boolean stageReplacementFallback(
            SkillDefinition target,
            SkillEvaluationResult evaluation,
            ClientEssenceState.Snapshot snapshot
    ) {
        java.util.Optional<Set<ResourceLocation>> groups =
                replacementGroupsToClear(target, evaluation, snapshot);
        java.util.Optional<SkillStateEvaluator.ActivationPlan> activationPlan =
                activationPlan(target, snapshot);
        if (groups.isEmpty() || activationPlan.isEmpty()) {
            return false;
        }
        SkillStateEvaluator.ActivationPlan plan = activationPlan.orElseThrow();
        if (plan.selectableAssignments().keySet().stream()
                .anyMatch(groups.orElseThrow()::contains)) {
            return false;
        }

        for (ResourceLocation groupId : groups.orElseThrow()) {
            draft.stageLoadout(groupId, null);
        }
        applyActivationPlan(plan, snapshot);
        return true;
    }

    private java.util.Optional<Set<ResourceLocation>> replacementGroupsToClear(
            SkillDefinition target,
            SkillEvaluationResult evaluation,
            ClientEssenceState.Snapshot snapshot
    ) {
        if (!evaluation.projectedReplaced()
                || evaluation.projectedReplacedBy().isEmpty()) {
            return java.util.Optional.empty();
        }

        Set<ResourceLocation> groups = new java.util.LinkedHashSet<>();
        for (ResourceLocation replacementId : evaluation.projectedReplacedBy()) {
            SkillDefinition replacement = SkillRegistry.get(replacementId).orElse(null);
            if (replacement == null
                    || !target.id().equals(replacement.replacementTarget())
                    || replacement.choiceGroup() == null
                    || !replacementId.equals(draft.projectedLoadout(
                    replacement.choiceGroup(),
                    snapshot
            ))) {
                return java.util.Optional.empty();
            }
            SkillChoiceGroup group = SkillRegistry.choiceGroup(
                    replacement.choiceGroup()
            ).orElse(null);
            if (group == null
                    || !group.allowNoSelection()
                    || !group.memberIds().contains(replacementId)) {
                return java.util.Optional.empty();
            }
            groups.add(group.id());
        }
        return groups.isEmpty()
                ? java.util.Optional.empty()
                : java.util.Optional.of(Set.copyOf(groups));
    }

    private boolean stageActivationSelections(
            SkillDefinition skill,
            ClientEssenceState.Snapshot snapshot
    ) {
        java.util.Optional<SkillStateEvaluator.ActivationPlan> plan =
                activationPlan(skill, snapshot);
        if (plan.isEmpty()) {
            return false;
        }
        applyActivationPlan(plan.orElseThrow(), snapshot);
        return true;
    }

    private boolean stageAutomaticActivation(
            SkillDefinition skill,
            ClientEssenceState.Snapshot snapshot
    ) {
        java.util.Optional<SkillStateEvaluator.ActivationPlan> plan =
                activationPlan(skill, snapshot);
        if (plan.isEmpty()) {
            return false;
        }

        SkillStateEvaluator.ActivationPlan activation = plan.orElseThrow();
        boolean changed = !activation.automaticSuppressionsToClear().isEmpty();
        for (Map.Entry<ResourceLocation, ResourceLocation> selection :
                activation.selectableAssignments().entrySet()) {
            if (!selection.getValue().equals(
                    draft.projectedLoadout(selection.getKey(), snapshot)
            )) {
                changed = true;
            }
        }
        if (!changed) {
            return false;
        }

        applyActivationPlan(activation, snapshot);
        return true;
    }

    private void applyActivationPlan(
            SkillStateEvaluator.ActivationPlan plan,
            ClientEssenceState.Snapshot snapshot
    ) {
        for (Map.Entry<ResourceLocation, ResourceLocation> selection :
                plan.selectableAssignments().entrySet()) {
            if (!selection.getValue().equals(
                    draft.projectedLoadout(selection.getKey(), snapshot)
            )) {
                draft.stageLoadout(selection.getKey(), selection.getValue());
            }
        }
        for (ResourceLocation skillId : plan.automaticSuppressionsToClear()) {
            draft.stageLoadout(skillId, null);
        }
    }

    private boolean isAutomaticSuppressed(
            SkillDefinition skill,
            ClientEssenceState.Snapshot snapshot
    ) {
        return SkillStateEvaluator.isAutomaticSuppressed(
                skill,
                draft.finalLoadouts(snapshot)
        );
    }

    private java.util.Optional<SkillStateEvaluator.ActivationPlan>
    activationPlan(
            SkillDefinition skill,
            ClientEssenceState.Snapshot snapshot
    ) {
        return SkillStateEvaluator.activationPlan(
                skill.id(),
                draft.projectedRanks(snapshot),
                draft.finalLoadouts(snapshot)
        );
    }

    private void stageSelectionForNewPurchase(
            SkillDefinition skill,
            ClientEssenceState.Snapshot snapshot
    ) {
        if (skill.activationPolicy() == SkillActivationPolicy.SELECTABLE) {
            skill.choiceGroupId().ifPresent(
                    groupId -> draft.stageLoadout(groupId, skill.id())
            );
        } else if (skill.activationPolicy() == SkillActivationPolicy.TOGGLE) {
            draft.stageLoadout(skill.id(), skill.id());
        }
    }

    private void unstagePurchaseCascade(
            ResourceLocation firstSkillId,
            ClientEssenceState.Snapshot snapshot
    ) {
        Set<ResourceLocation> removed = new java.util.LinkedHashSet<>();
        removed.add(firstSkillId);

        boolean changed;
        do {
            changed = false;
            for (ResourceLocation stagedId : draft.stagedPurchaseIds()) {
                if (removed.contains(stagedId)) {
                    continue;
                }
                SkillDefinition staged = SkillRegistry.get(stagedId).orElse(null);
                if (staged == null) {
                    continue;
                }
                int targetRank = draft.projectedRanks(snapshot).getOrDefault(stagedId, 1);
                for (var prerequisite : staged.prerequisiteRanks(targetRank).entrySet()) {
                    ResourceLocation prerequisiteId = prerequisite.getKey();
                    var authoritative = snapshot.ownedSkills().get(prerequisiteId);
                    if (removed.contains(prerequisiteId)
                            && (authoritative == null || authoritative.rank() < prerequisite.getValue())) {
                        removed.add(stagedId);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        for (ResourceLocation skillId : removed) {
            draft.unstagePurchase(skillId);
            SkillDefinition definition = SkillRegistry.get(skillId).orElse(null);
            if (definition == null) {
                continue;
            }
            ResourceLocation slot = switch (definition.activationPolicy()) {
                case AUTOMATIC -> null;
                case SELECTABLE -> definition.choiceGroup();
                case TOGGLE -> definition.id();
            };
            if (slot != null
                    && skillId.equals(draft.projectedLoadout(slot, snapshot))) {
                draft.stageLoadout(slot, snapshot.loadoutSelections().get(slot));
            }
        }
    }

    private void activateAscendanceAction() {
        if (!ClientEssenceState.ready()) return;

        NexusAscendanceAction action = ascendanceAction();
        if (!action.enabled()) return;

        switch (action.kind()) {
            case DISCARD_DRAFT -> {
                draft.clearAndCapture(ClientEssenceState.snapshot());
                transactionFeedback = null;
            }
            case APPLY_CHANGES -> submitDraft(false, PendingCompletion.NONE);
            case ASCEND -> submitDraft(true, PendingCompletion.NONE);
            default -> {
            }
        }
    }

    private void requestAscension() {
        if (!ClientEssenceState.ready()) return;

        NexusAscendanceAction action = ascendanceAction();
        if (action.kind() == NexusAscendanceAction.Kind.ASCEND
                && action.enabled()) {
            submitDraft(true, PendingCompletion.NONE);
        }
    }

    private void submitDraft(
            boolean ascend,
            PendingCompletion completion
    ) {
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        if (!snapshot.ready()
                || pendingRequestId >= 0L
                || draft.invalidated()
                || (!ascend && !draft.hasChanges(snapshot))) {
            return;
        }

        List<AscendanceNexusTransactionPayload.BonusTarget> bonusTargets =
                new ArrayList<>();
        for (Map.Entry<ResourceLocation, Long> entry :
                draft.finalBonusTargets(snapshot).entrySet()) {
            bonusTargets.add(
                    new AscendanceNexusTransactionPayload.BonusTarget(
                            entry.getKey().toString(),
                            entry.getValue()
                    )
            );
        }

        List<AscendanceNexusTransactionPayload.SkillRankTarget> purchases = draft.stagedPurchases()
                .stream()
                .map(purchase -> new AscendanceNexusTransactionPayload.SkillRankTarget(
                        purchase.skillId().toString(), purchase.targetRank()))
                .toList();

        Map<ResourceLocation, ResourceLocation> finalLoadouts =
                draft.finalLoadouts(snapshot);
        List<AscendanceNexusTransactionPayload.LoadoutSelection> loadouts =
                new ArrayList<>();
        for (Map.Entry<ResourceLocation, ResourceLocation> entry :
                finalLoadouts.entrySet()) {
            if (java.util.Objects.equals(
                    snapshot.loadoutSelections().get(entry.getKey()),
                    entry.getValue()
            )) {
                continue;
            }
            loadouts.add(
                    new AscendanceNexusTransactionPayload.LoadoutSelection(
                            entry.getKey().toString(),
                            entry.getValue().toString()
                    )
            );
        }
        for (ResourceLocation groupId : draft.clearedLoadoutGroups()) {
            if (!finalLoadouts.containsKey(groupId)
                    && snapshot.loadoutSelections().containsKey(groupId)) {
                loadouts.add(
                        new AscendanceNexusTransactionPayload.LoadoutSelection(
                                groupId.toString(),
                                ""
                        )
                );
            }
        }

        long requestId = nextRequestId();
        long baseRevision = draft.baseRevision() == Long.MIN_VALUE
                ? snapshot.nexusRevision()
                : draft.baseRevision();

        pendingRequestId = requestId;
        acceptedAscension = false;
        ascensionHandoff.begin(snapshot.tierId().toString());
        pendingCompletion = completion;
        acceptedRevision = -1L;
        transactionFeedback = null;

        NetworkManager.sendToServer(
                new AscendanceNexusTransactionPayload(
                        menu.containerId,
                        requestId,
                        baseRevision,
                        snapshot.tierId().toString(),
                        snapshot.balanceProfileId().toString(),
                        bonusTargets,
                        purchases,
                        loadouts,
                        ascend
                )
        );
    }

    private long nextRequestId() {
        return NEXT_REQUEST_ID.getAndUpdate(
                current -> current == Long.MAX_VALUE ? 0L : current + 1L
        );
    }

    private void consumeTransactionResult() {
        if (pendingRequestId < 0L) {
            return;
        }

        if (acceptedRevision < 0L) {
            AscendanceNexusTransactionClientState.consume(
                    menu.containerId,
                    pendingRequestId
            ).ifPresent(result -> {
                if (!result.accepted()) {
                    transactionFeedback = Component.translatable(
                            result.status().translationKey()
                    );
                    pendingRequestId = -1L;
                    pendingCompletion = PendingCompletion.NONE;
                    return;
                }

                acceptedRevision = result.nexusRevision();
                acceptedAscension = result.ascended();
                ascensionHandoff.acknowledge(result.accepted(), result.ascended(), result.nexusRevision());
            });
        }

        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        if (acceptedRevision < 0L
                || !snapshot.ready()
                || snapshot.nexusRevision() < acceptedRevision
                || (acceptedAscension && !ascensionHandoff.ready(snapshot.ready(), snapshot.nexusRevision(), snapshot.tierId().toString()))) {
            return;
        }

        PendingCompletion completion = pendingCompletion;
        draft.clearAndCapture(snapshot);
        pendingRequestId = -1L;
        acceptedRevision = -1L;
        pendingCompletion = PendingCompletion.NONE;
        pendingDecision = PendingDecision.NONE;
        transactionFeedback = null;

        if (acceptedAscension) {
            acceptedAscension = false;
            super.onClose();
            AscensionAnimation.confirmed(snapshot.tierId());
        } else if (completion == PendingCompletion.EXIT) {
            super.onClose();
        }
    }

    private int trackAt(
            double mouseX,
            double mouseY,
            NexusCategoryView category,
            NexusPageLayout layout,
            int visibleCount
    ) {
        int hitTop =
                layout.trackTop() - TRACK_HIT_PADDING;
        int hitBottom =
                layout.trackBottom() + TRACK_HIT_PADDING;

        if (mouseY < hitTop
                || mouseY > hitBottom
                || mouseX < layout.tracksLeft()
                || mouseX >= layout.tracksRight()) {
            return -1;
        }

        int viewportWidth =
                layout.tracksRight() - layout.tracksLeft();
        int trackWidth =
                trackWidth(
                        viewportWidth,
                        visibleCount
                );

        int x =
                trackContentLeft(
                        layout,
                        visibleCount,
                        trackWidth
                );
        int end = Math.min(
                category.tracks().size(),
                trackWindowStart + visibleCount
        );

        for (int i = trackWindowStart; i < end; i++) {
            if (mouseX >= x
                    && mouseX < x + trackWidth) {
                return i;
            }
            x += trackWidth + TRACK_GAP;
        }

        return -1;
    }

    private void updateStagedDrag(
            double mouseY,
            NexusCategoryView category,
            NexusProgressionTrack track,
            NexusPageLayout layout
    ) {
        var tierId = ClientEssenceState.snapshot().tierId();
        double requestedProgress = track.layout(layout.trackTop(), layout.trackBottom()).effectForY(mouseY);
        long affordable = safeAddNonNegative(stagedInvestment(track), stagedAvailableEssence(category));
        long target = track.dragTarget(requestedProgress, affordable, tierId);
        transactionFeedback = null;
        draft.stageBonus(track.stat().id(), target);
    }

    private long stagedInvestment(
            NexusProgressionTrack track
    ) {
        return draft.bonusTarget(
                track.stat().id(),
                ClientEssenceState.snapshot()
        );
    }

    private long stagedAvailableEssence(
            NexusCategoryView category
    ) {
        return draft.projectedAvailable(
                category.essence().id(),
                ClientEssenceState.snapshot(),
                statEssenceIds()
        );
    }

    private double stagedTotalInvestment(
            NexusCategoryView category
    ) {
        return draft.projectedBonusInvestment(
                category.essence().id(),
                ClientEssenceState.snapshot(),
                statEssenceIds()
        );
    }

    private boolean hasStagedChanges() {
        return draft.hasChanges(ClientEssenceState.snapshot());
    }

    private double stagedProgression(
            NexusProgressionTrack track,
            long stagedTarget
    ) {
        return track.progression(stagedTarget, ClientEssenceState.snapshot().tierId());
    }

    private double stagedScaledBonus(
            NexusProgressionTrack track,
            long stagedTarget
    ) {
        return track.state().transcendentMaximumBonus()
                * stagedProgression(
                        track,
                        stagedTarget
                );
    }

    private BalanceProfileDefinition currentBalanceProfile(
            ClientEssenceState.Snapshot snapshot
    ) {
        if (!snapshot.ready()
                || snapshot.balanceProfileId() == null) {
            return null;
        }

        var runtime = com.mistaboom.essence_ascendance.config.EssenceConfigManager.clientRuntime();
        if (runtime == null || !runtime.config().balanceProfile().id().equals(snapshot.balanceProfileId())) return null;
        return runtime.config().balanceProfile();
    }

    private long safeAddNonNegative(
            long left,
            long right
    ) {
        long safeLeft =
                Math.max(0L, left);
        long safeRight =
                Math.max(0L, right);

        if (Long.MAX_VALUE - safeLeft < safeRight) {
            return Long.MAX_VALUE;
        }

        return safeLeft + safeRight;
    }

    private List<NexusCategoryView> categories() {
        return NexusCategoryViewFactory.build(
                ClientEssenceState.snapshot()
        );
    }

    private Map<ResourceLocation, ResourceLocation> statEssenceIds() {
        Map<ResourceLocation, ResourceLocation> result =
                new LinkedHashMap<>();
        for (NexusCategoryView category : categories()) {
            for (NexusProgressionTrack track : category.tracks()) {
                result.put(
                        track.stat().id(),
                        category.essence().id()
                );
            }
        }
        return result;
    }

    private void stabilizeSelection(List<NexusCategoryView> categories) {
        if (!ClientEssenceState.ready()) return;
        var tabs = tabLayout(categories);
        navigation.reconcileSections(categories.stream().map(category -> category.essence().id()).toList(),
                tabs.visibleCount(), true);
        selectedCategoryIndex = NexusNavigationState.categoryIndex(navigation.section(),
                categories.stream().map(category -> category.essence().id()).toList());
    }

    private FullscreenLayout.Tabs tabLayout(List<NexusCategoryView> categories) {
        return FullscreenLayout.tabs(fullscreenFrame().secondary(),
                categories.stream().map(category -> font.width(EssenceText.essenceShort(category.essence()))).toList(),
                64, 102, 18, 3);
    }

    private NexusPageLayout contentLayout() {
        return NexusPageLayout.compose(fullscreenFrame().content(), height);
    }

    private int visibleTrackCount(NexusPageLayout layout) {
        return com.mistaboom.essence_ascendance.client.nexus.NexusBonusTrackLayout.visibleCount(
                layout.tracksRight() - layout.tracksLeft(), Integer.MAX_VALUE, TRACK_PREFERRED_WIDTH, TRACK_GAP);
    }

    private int effectiveVisibleTrackCount(int trackCount, NexusPageLayout layout) {
        return com.mistaboom.essence_ascendance.client.nexus.NexusBonusTrackLayout.visibleCount(
                layout.tracksRight() - layout.tracksLeft(), trackCount, TRACK_PREFERRED_WIDTH, TRACK_GAP);
    }

    private int trackWidth(int viewportWidth, int visibleCount) {
        return com.mistaboom.essence_ascendance.client.nexus.NexusBonusTrackLayout.trackWidth(
                viewportWidth, visibleCount, TRACK_PREFERRED_WIDTH, TRACK_GAP);
    }

    private int trackContentLeft(
            NexusPageLayout layout,
            int visibleCount,
            int trackWidth
    ) {
        int count = Math.max(1, visibleCount);
        int usedWidth =
                trackWidth * count
                        + TRACK_GAP * Math.max(0, count - 1);
        int viewportWidth =
                Math.max(1, layout.tracksRight() - layout.tracksLeft());

        return layout.tracksLeft()
                + Math.max(0, (viewportWidth - usedWidth) / 2);
    }

    private void clampTrackWindow(int trackCount, int visibleCount) {
        trackWindowStart = UiViewport.create(trackCount, visibleCount, trackWindowStart).offset();
    }

    private List<AscendanceTierDefinition> orderedTiers() {
        List<AscendanceTierDefinition> tiers =
                new ArrayList<>(AscendanceTierRegistry.powerTiers());
        tiers.sort(
                Comparator.comparingInt(
                        AscendanceTierDefinition::order
                )
        );
        return tiers;
    }

    private String tierDisplayName(
            ResourceLocation tierId
    ) {
        if (tierId == null) {
            return EssenceText.term("unknown").getString();
        }

        return AscendanceTierRegistry
                .get(tierId)
                .map(tier -> EssenceText.ascendanceTier(tier).getString())
                .orElse(tierId.getPath());
    }

    private String formatBonus(
            StatUnit unit,
            double value
    ) {
        String number =
                formatDecimal(value);

        return switch (unit) {
            case PERCENT -> EssenceText.gui("nexus.bonus.percent", number).getString();
            case HEARTS -> EssenceText.gui("nexus.bonus.hearts", number).getString();
            case HEARTS_PER_SECOND -> EssenceText.gui("nexus.bonus.hearts_per_second", number).getString();
            case BLOCKS -> EssenceText.gui("nexus.bonus.blocks", number).getString();
            case SECONDS -> EssenceText.gui("nexus.bonus.seconds", number).getString();
            case LEVELS -> EssenceText.gui("nexus.bonus.levels", number).getString();
            case FLAT -> EssenceText.gui("nexus.bonus.flat", number).getString();
        };
    }

    private String compactBonus(StatUnit unit, double value) {
        return NexusProgressionTrack.effectLabel(unit, value);
    }

    private String formatDecimal(
            double value
    ) {
        double rounded =
                Math.rint(value * 100.0) / 100.0;

        if (Math.abs(rounded - Math.rint(rounded)) < 0.000001) {
            return String.format(
                    Locale.ROOT,
                    "%.0f",
                    rounded
            );
        }

        if (Math.abs(rounded * 10.0 - Math.rint(rounded * 10.0))
                < 0.000001) {
            return String.format(
                    Locale.ROOT,
                    "%.1f",
                    rounded
            );
        }

        return String.format(
                Locale.ROOT,
                "%.2f",
                rounded
        );
    }

    private String formatLongForWidth(
            long value,
            int maximumWidth
    ) {
        String full =
                formatLong(value);

        if (maximumWidth <= 0) {
            return "";
        }

        if (font.width(full) <= maximumWidth) {
            return full;
        }

        for (int decimals = 2; decimals >= 0; decimals--) {
            String compact =
                    formatCompactLong(
                            value,
                            decimals
                    );
            if (font.width(compact) <= maximumWidth) {
                return compact;
            }
        }

        return trimToWidth(
                formatCompactLong(value, 0),
                maximumWidth
        );
    }

    private String formatCompactLong(
            long value,
            int decimals
    ) {
        double absolute =
                Math.abs((double) value);
        double divisor;
        String suffix;

        if (absolute >= 1_000_000_000.0) {
            divisor = 1_000_000_000.0;
            suffix = "B";
        } else if (absolute >= 1_000_000.0) {
            divisor = 1_000_000.0;
            suffix = "M";
        } else if (absolute >= 1_000.0) {
            divisor = 1_000.0;
            suffix = "k";
        } else {
            return formatLong(value);
        }

        double scaled =
                value / divisor;

        /* Avoid awkward rounded values such as 1000k or 1000M. */
        if (Math.abs(scaled) >= 999.995) {
            if ("k".equals(suffix)) {
                divisor = 1_000_000.0;
                suffix = "M";
                scaled = value / divisor;
            } else if ("M".equals(suffix)) {
                divisor = 1_000_000_000.0;
                suffix = "B";
                scaled = value / divisor;
            }
        }

        int safeDecimals =
                Math.max(0, Math.min(2, decimals));
        String number =
                String.format(
                        Locale.ROOT,
                        "%." + safeDecimals + "f",
                        scaled
                );

        while (number.contains(".")
                && number.endsWith("0")) {
            number = number.substring(0, number.length() - 1);
        }
        if (number.endsWith(".")) {
            number = number.substring(0, number.length() - 1);
        }

        return number + suffix;
    }

    private String formatLong(
            long value
    ) {
        return String.format(
                Locale.ROOT,
                "%,d",
                value
        );
    }

    private String trimToWidth(
            String text,
            int maximumWidth
    ) {
        if (maximumWidth <= 0) {
            return "";
        }

        if (font.width(text) <= maximumWidth) {
            return text;
        }

        String ellipsis = "…";
        int allowed =
                Math.max(1, maximumWidth - font.width(ellipsis));

        return font.plainSubstrByWidth(
                text,
                allowed
        ) + ellipsis;
    }

    private String[] wrapTwoLines(
            String text,
            int maximumWidth
    ) {
        String normalized =
                text == null ? "" : text.trim();

        if (maximumWidth <= 0
                || normalized.isEmpty()) {
            return new String[]{"", ""};
        }

        if (font.width(normalized) <= maximumWidth) {
            return new String[]{normalized, ""};
        }

        /*
         * Wrap first, then ellipsize only the final visible line. The old helper
         * required BOTH candidate lines to fit completely; if the second line
         * was a little too long it abandoned wrapping entirely and produced a
         * single "Melee Atta…" line.
         *
         * Instead, use the furthest word boundary that still fits on line one,
         * then let trimToWidth() preserve as much as possible on line two.
         */
        int split = -1;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) != ' ') {
                continue;
            }

            String firstCandidate =
                    normalized.substring(0, i).trim();
            if (!firstCandidate.isEmpty()
                    && font.width(firstCandidate) <= maximumWidth) {
                split = i;
            } else if (!firstCandidate.isEmpty()) {
                break;
            }
        }

        if (split >= 0) {
            String first =
                    normalized.substring(0, split).trim();
            String second =
                    normalized.substring(split + 1).trim();

            return new String[]{
                    first,
                    trimToWidth(second, maximumWidth)
            };
        }

        /*
         * A single word can itself exceed the column. Split by rendered width so
         * the second line is still available instead of discarding it.
         */
        String first =
                font.plainSubstrByWidth(
                        normalized,
                        maximumWidth
                );
        if (first.isEmpty()) {
            return new String[]{
                    trimToWidth(normalized, maximumWidth),
                    ""
            };
        }

        String second =
                normalized.substring(
                        Math.min(first.length(), normalized.length())
                ).stripLeading();

        return new String[]{
                first,
                trimToWidth(second, maximumWidth)
        };
    }

    private void renderSkillTooltip(
            GuiGraphics graphics,
            ResourceLocation skillId,
            int mouseX,
            int mouseY
    ) {
        SkillDefinition skill = SkillRegistry.get(skillId).orElse(null);
        Map<ResourceLocation, SkillEvaluationResult> evaluations = skillEvaluations();
        SkillEvaluationResult evaluation = evaluations.get(skillId);
        if (skill == null || evaluation == null) {
            return;
        }

        NexusCategoryView category = categoryForEssence(skill.essenceId());
        if (category == null) {
            return;
        }
        long cost = skillCost(skill);
        long projectedBalance = stagedAvailableEssence(category);
        SkillNodeVisualState visual = skillVisualState(
                evaluation,
                cost,
                projectedBalance
        );

        // Every definition uses the same semantic styles; no individual skill IDs or layouts.
        SemanticTooltip tooltip = new SemanticTooltip();
        tooltip.title(Component.translatable(skill.nameTranslationKey()),
                AscendancePalette.categoryRgb(skill.essenceId()));
        boolean shift = hasShiftDown();
        skillTooltipPresentation.append(tooltip, skill, evaluations, shift);
        tooltip.field(EssenceText.gui("nexus.skills.tooltip.status",
                EssenceText.gui("nexus.skills.state." + visual.name().toLowerCase(Locale.ROOT))
                        .withStyle(style -> style.withColor(visual.textColor() & 0xFFFFFF))));

        var unmetPrerequisites = SkillTooltipPresentation.unmetPrerequisites(evaluation, evaluations, shift);
        if (!unmetPrerequisites.isEmpty()) {
            tooltip.gap().section(EssenceText.gui("nexus.skills.tooltip.prerequisites"));
            for (com.mistaboom.essence_ascendance.skill.SkillPrerequisiteStatus status :
                    unmetPrerequisites) {
                String statusPath = status.projectedOwned()
                        ? "nexus.skills.tooltip.prerequisite_inactive"
                        : "nexus.skills.tooltip.prerequisite_missing";
                tooltip.requirement(
                        EssenceText.gui(
                                statusPath,
                                EssenceText.gui("nexus.skills.tooltip.prerequisite_rank", skillName(status.skillId()), status.requiredRank())
                        ), SemanticTooltip.State.MISSING
                );
            }
        }

        var unmetRequirements = SkillTooltipPresentation.unmetRequirements(evaluation, shift);
        if (!unmetRequirements.isEmpty()) {
            var installedRuntime = EssenceConfigManager.clientRuntime();
            if (installedRuntime == null) installedRuntime = EssenceConfigManager.serverRuntime();
            int eligibilityRank = Math.min(
                    com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData
                            .maximumRank(installedRuntime, skill),
                    evaluation.currentRank() + 1);
            tooltip.gap().section(EssenceText.gui("nexus.skills.tooltip.requirements"));
            for (com.mistaboom.essence_ascendance.skill.SkillRequirementStatus status :
                    unmetRequirements) {
                ClientEssenceState.MilestoneSnapshot milestone =
                        status.kind() == SkillRequirementKind.PERMANENT_MILESTONE
                                 ? milestoneRequirementState(
                                         skill,
                                         eligibilityRank,
                                         status.requirementId()
                                )
                                : null;
                boolean unavailable = milestone != null
                        ? !milestone.resolvable()
                        : status.kind() == SkillRequirementKind.PERMANENT_MILESTONE;
                String statusPath = unavailable
                        ? "nexus.skills.tooltip.requirement_unavailable"
                        : "nexus.skills.tooltip.requirement_missing";
                Component requirement = requirementDescription(skill, eligibilityRank, status.requirementId());
                tooltip.requirement(EssenceText.gui(statusPath, requirement),
                        unavailable ? SemanticTooltip.State.UNAVAILABLE
                                : SemanticTooltip.State.MISSING);
            }
        }

        if (skill.choiceGroupId().isPresent() || skill.replacementTargetId().isPresent()
                || !evaluation.projectedReplacedBy().isEmpty()) tooltip.gap();
        skill.choiceGroupId().ifPresent(groupId -> {
            SkillChoiceGroup group = SkillRegistry.choiceGroup(groupId).orElse(null);
            Component groupName = group == null
                    ? Component.literal(groupId.getPath())
                    : Component.translatable(group.translationKey());
            tooltip.field(
                    EssenceText.gui("nexus.skills.tooltip.choice_group", SemanticTooltip.value(groupName))
            );
        });
        skill.replacementTargetId().ifPresent(
                targetId -> tooltip.field(
                        EssenceText.gui(
                                "nexus.skills.tooltip.replaces",
                                SemanticTooltip.value(skillName(targetId))
                        )
                )
        );
        if (!evaluation.projectedReplacedBy().isEmpty()) {
            for (ResourceLocation replacementId : evaluation.projectedReplacedBy()) {
                tooltip.field(
                        EssenceText.gui(
                                "nexus.skills.tooltip.replaced_by",
                                SemanticTooltip.value(skillName(replacementId))
                        )
                );
            }
        }

        Component clickHint = skillClickHint(
                skill,
                evaluation,
                visual,
                projectedBalance,
                cost
        );
        boolean nextRankHint = SkillTooltipPresentation.previewsNext(evaluation.currentRank(), skill.maximumRank(), true);
        if (clickHint != null || nextRankHint) {
            tooltip.gap();
            if (nextRankHint) tooltip.hint(SkillTooltipPresentation.rankPurchaseHint(shift));
            if (clickHint != null) tooltip.hint(clickHint);
        }

        int maximumWidth = Math.max(1, Math.min(300, width - 32));
        List<FormattedCharSequence> lines = tooltip.wrap(font, maximumWidth);
        if (!skillId.equals(tooltipSkillId)) {
            tooltipSkillId = skillId;
            skillTooltipScroll = 0;
        }
        // Vanilla text-tooltip rows occupy ten pixels. Preserve every requirement
        // in a scrollable viewport on short windows instead of drawing off-screen.
        int maximumLines = Math.max(3, (height - 24) / 10);
        TooltipLayout.Viewport<FormattedCharSequence> viewport =
                TooltipLayout.viewport(lines, maximumLines, skillTooltipScroll);
        skillTooltipScroll = viewport.offset();
        skillTooltipMaximumScroll = viewport.maximumOffset();
        List<FormattedCharSequence> visible = new ArrayList<>(viewport.lines());
        if (viewport.maximumOffset() > 0) {
            visible.add(EssenceText.gui("nexus.skills.tooltip.scroll",
                    viewport.offset() + 1, viewport.offset() + visible.size() - 1, lines.size() - 1)
                    .withStyle(ChatFormatting.GOLD).getVisualOrderText());
        }
        graphics.renderTooltip(font, visible, mouseX, mouseY);
    }

    private Component skillBooleanStatus(boolean value) {
        return EssenceText.gui(
                value
                        ? "nexus.skills.tooltip.value.yes"
                        : "nexus.skills.tooltip.value.no"
        ).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    private Component projectedSkillBooleanStatus(boolean current, boolean projected, boolean positiveMeaning) {
        return skillBooleanStatus(projected).copy().withStyle(current != projected ? ChatFormatting.AQUA
                : projected == positiveMeaning ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private Component skillSelectionStatus(
            SkillDefinition skill,
            boolean selected
    ) {
        if (skill.activationPolicy() == SkillActivationPolicy.AUTOMATIC) {
            return EssenceText.gui("nexus.skills.tooltip.value.not_applicable").withStyle(ChatFormatting.DARK_GRAY);
        }
        return skillBooleanStatus(selected);
    }

    private Component skillClickHint(
            SkillDefinition skill,
            SkillEvaluationResult evaluation,
            SkillNodeVisualState visual,
            long projectedBalance,
            long cost
    ) {
        if (pendingRequestId >= 0L || draft.invalidated()) {
            return null;
        }
        if (evaluation.staged()) {
            return EssenceText.gui("nexus.skills.tooltip.click_unstage");
        }
        if (!evaluation.projectedOwned()) {
            if (evaluation.eligibleToPurchase() && cost <= projectedBalance) {
                return EssenceText.gui("nexus.skills.tooltip.click_purchase");
            }
            return null;
        }
        return switch (skill.activationPolicy()) {
            case AUTOMATIC -> {
                ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
                if (replacementGroupsToClear(
                        skill,
                        evaluation,
                        snapshot
                ).isPresent()) {
                    yield EssenceText.gui(
                            "nexus.skills.tooltip.click_restore_replaced"
                    );
                }
                if (evaluation.projectedEffective()) {
                    yield EssenceText.gui("nexus.skills.tooltip.click_disable");
                }
                if (evaluation.projectedSuspended()) {
                    yield null;
                }
                java.util.Optional<SkillStateEvaluator.ActivationPlan> plan =
                        activationPlan(skill, snapshot);
                if (plan.isEmpty()) {
                    yield null;
                }
                SkillStateEvaluator.ActivationPlan activation = plan.orElseThrow();
                boolean branchAssignment = activation
                        .selectableAssignments()
                        .entrySet()
                        .stream()
                        .anyMatch(entry -> !entry.getValue().equals(
                                draft.projectedLoadout(entry.getKey(), snapshot)
                        ));
                boolean ancestorSuppression = activation
                        .automaticSuppressionsToClear()
                        .stream()
                        .anyMatch(skillId -> !skill.id().equals(skillId));
                if (branchAssignment || ancestorSuppression) {
                    yield EssenceText.gui(
                            "nexus.skills.tooltip.click_activate_branch"
                    );
                }
                yield isAutomaticSuppressed(skill, snapshot)
                        ? EssenceText.gui("nexus.skills.tooltip.click_enable")
                        : null;
            }
            case SELECTABLE -> evaluation.projectedSelected()
                    ? EssenceText.gui("nexus.skills.tooltip.click_deselect")
                    : EssenceText.gui("nexus.skills.tooltip.click_select");
            case TOGGLE -> evaluation.projectedSelected()
                    ? EssenceText.gui("nexus.skills.tooltip.click_disable")
                    : EssenceText.gui("nexus.skills.tooltip.click_enable");
        };
    }

    private ClientEssenceState.MilestoneSnapshot milestoneRequirementState(
            SkillDefinition skill,
            int rank,
            ResourceLocation requirementId
    ) {
        return com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData.milestoneState(
                skill, rank, requirementId, ClientEssenceState.snapshot());
    }

    private Component requirementDescription(
            SkillDefinition skill,
            int rank,
            ResourceLocation requirementId
    ) {
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        return com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData.requirementDescription(
                skill, rank, requirementId, snapshot, bonusTotals(snapshot, true));
    }

    private Component skillName(ResourceLocation skillId) {
        return com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData.skillName(skillId);
    }

    private Component tierNameComponent(ResourceLocation tierId) {
        return AscendanceTierRegistry.get(tierId)
                .map(tier -> (Component) EssenceText.ascendanceTier(tier))
                .orElseGet(() -> Component.literal(tierId.getPath()));
    }

    private NexusCategoryView categoryForEssence(ResourceLocation essenceId) {
        for (NexusCategoryView category : categories()) {
            if (category.essence().id().equals(essenceId)) {
                return category;
            }
        }
        return null;
    }

    private void renderPendingDecision(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        UiBounds layout = modalLayout();
        FullscreenControls.modalBackdrop(graphics, fullscreenFrame().screen(), layout);

        Component title = EssenceText.gui("nexus.modal.exit_title");
        graphics.drawCenteredString(
                font,
                StyledTextLayout.fit(font, title, layout.width() - 16),
                width / 2,
                layout.y() + 10,
                AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT)
        );

        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        Component summary = EssenceText.gui(
                "nexus.modal.pending_summary",
                draft.bonusChangeCount(snapshot),
                draft.purchaseCount(),
                draft.loadoutChangeCount(snapshot)
        );
        graphics.drawCenteredString(
                font,
                StyledTextLayout.fit(font, summary, layout.width() - 20),
                width / 2,
                layout.y() + 28,
                AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT)
        );

        if (transactionFeedback != null) {
            graphics.drawCenteredString(
                    font,
                    StyledTextLayout.fit(
                            font, transactionFeedback,
                            layout.width() - 20
                    ),
                    width / 2,
                    layout.y() + 43,
                    AscendanceUiPalette.argb(AscendanceUiPalette.ERROR)
            );
        } else if (pendingRequestId >= 0L) {
            graphics.drawCenteredString(
                    font,
                    EssenceText.gui("nexus.transaction.waiting"),
                    width / 2,
                    layout.y() + 43,
                    AscendanceUiPalette.argb(AscendanceUiPalette.WARNING)
            );
        }

    }

    private boolean canPromptForExit() {
        return minecraft != null
                && minecraft.getConnection() != null
                && minecraft.player != null
                && minecraft.player.containerMenu == menu;
    }

    private UiBounds modalLayout() {
        return FullscreenLayout.centered(fullscreenFrame().screen(), 344, 94, 14);
    }

    private void renderPrimaryActionTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        if (!ClientEssenceState.ready()) {
            return;
        }

        Rect action = primaryActionRect();
        if (action == null || !action.contains(mouseX, mouseY)) {
            return;
        }

        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        List<Component> lines = new ArrayList<>();
        if (transactionFeedback != null) {
            lines.add(transactionFeedback);
        }
        if (draft.invalidated()) {
            lines.add(EssenceText.gui("nexus.draft.outdated"));
        } else if (pendingRequestId >= 0L) {
            lines.add(EssenceText.gui("nexus.transaction.waiting"));
        } else if (navigation.mode() == NexusMode.ASCENDANCE) {
            NexusAscendanceAction ascendanceActionState = ascendanceAction();
            if (ascendanceActionState.kind()
                    == NexusAscendanceAction.Kind.APPLY_CHANGES) {
                lines.add(EssenceText.gui("nexus.action.apply_without_ascending"));
            } else {
                lines.add(ascensionActionStatus(snapshot));
                lines.add(EssenceText.gui("nexus.attunement.choice"));
                lines.add(EssenceText.gui("nexus.attunement.no_payment"));
            }
        } else if (!draft.hasChanges(snapshot)) {
            lines.add(EssenceText.gui("nexus.action.no_changes"));
        }

        if (draft.hasChanges(snapshot)) {
            appendPendingChangeLines(lines, snapshot);
        }

        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void appendPendingChangeLines(
            List<Component> lines,
            ClientEssenceState.Snapshot snapshot
    ) {
        lines.add(EssenceText.gui("nexus.action.pending_header"));

        for (ResourceLocation statId : draft.changedBonusIds(snapshot)) {
            ClientEssenceState.StatSnapshot state = snapshot.stats().get(statId);
            if (state == null) {
                continue;
            }

            Component statName = EssenceStatRegistry.get(statId)
                    .map(stat -> (Component) EssenceText.stat(stat))
                    .orElseGet(() -> Component.literal(statId.getPath()));
            lines.add(
                    EssenceText.gui(
                            "nexus.action.pending_bonus",
                            statName,
                            formatLong(state.storedInvestment()),
                            formatLong(draft.bonusTarget(statId, snapshot))
                    )
            );
        }

        for (NexusDraft.SkillPurchaseDraft purchase : draft.stagedPurchases()) {
            NexusCategoryView category = categoryForEssence(purchase.essenceId());
            Component essenceName = category == null
                    ? Component.literal(purchase.essenceId().getPath())
                    : EssenceText.essenceShort(category.essence());
            lines.add(
                    EssenceText.gui(
                            "nexus.action.pending_purchase",
                            skillName(purchase.skillId()),
                            formatLong(purchase.projectedCost()),
                            essenceName
                    )
            );
        }

        Map<ResourceLocation, ResourceLocation> projected =
                draft.finalLoadouts(snapshot);
        Set<ResourceLocation> slots = new java.util.LinkedHashSet<>();
        slots.addAll(snapshot.loadoutSelections().keySet());
        slots.addAll(projected.keySet());

        for (ResourceLocation slotId : slots) {
            ResourceLocation committedSkill =
                    snapshot.loadoutSelections().get(slotId);
            ResourceLocation projectedSkill = projected.get(slotId);
            if (java.util.Objects.equals(committedSkill, projectedSkill)) {
                continue;
            }

            Component from = committedSkill == null
                    ? EssenceText.gui("nexus.action.pending_none")
                    : skillName(committedSkill);
            Component to = projectedSkill == null
                    ? EssenceText.gui("nexus.action.pending_none")
                    : skillName(projectedSkill);
            lines.add(
                    EssenceText.gui(
                            "nexus.action.pending_loadout",
                            from,
                            to
                    )
            );
        }
    }

    private Component ascensionActionStatus(
            ClientEssenceState.Snapshot snapshot
    ) {
        ClientEssenceState.ProgressSnapshot progress = snapshot.progress();
        return switch (progress.status()) {
            case AVAILABLE -> attunementReady()
                    ? EssenceText.gui("nexus.ready_to_ascend")
                    : EssenceText.gui("nexus.requirements_incomplete");
            case MAX_TIER -> EssenceText.gui("nexus.maximum_achieved");
            case CONFIGURATION_ERROR ->
                    EssenceText.gui("nexus.ascension_config_error");
        };
    }

    private Rect primaryActionRect() {
        UiBounds bounds = actionBounds();
        return new Rect(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
    }

    private double clamp01(
            double value
    ) {
        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        value
                )
        );
    }

    private record Rect(
            int left,
            int top,
            int right,
            int bottom
    ) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    private record ConnectionPoint(int x, int y) {
    }

    private record ConnectionEndpoints(
            ConnectionPoint start,
            ConnectionPoint end
    ) {
    }

    private record SkillConnection(
            ResourceLocation parent,
            ResourceLocation child
    ) {
    }

    private record SkillChoiceFrame(
            Rect bounds,
            int color,
            int markerCount,
            Set<ResourceLocation> memberIds
    ) {
    }

    private record SkillRouteObstacle(
            Rect bounds,
            Set<ResourceLocation> endpointIds
    ) {
    }

    private record SkillRoute(
            SkillConnection connection,
            int color,
            boolean dashed
    ) {
    }

    private record SkillRoutePort(
            ResourceLocation nodeId,
            boolean rightSide
    ) {
    }

    private record SkillRouteSegment(
            ConnectionPoint from,
            ConnectionPoint to
    ) {
    }

    /** direction: 0 = start, 1 = horizontal, 2 = vertical. */
    private record SkillRouteGridState(
            int xIndex,
            int yIndex,
            int direction
    ) {
    }

    private record SkillRouteGridVisit(
            SkillRouteGridState state,
            long distance
    ) {
    }

    private enum SkillNodeVisualState {
        LOCKED("×", 0xAA24212A, 0xFF514B58, 0xFF807986),
        ELIGIBLE("+", 0xCC30283C, 0xFFC0A0DD, 0xFFF0EDF4),
        UNAFFORDABLE("$", 0xCC35282B, 0xFFD47A7A, 0xFFD7A0A0),
        STAGED("+", 0xCC293A42, 0xFF79C8D8, 0xFFD9F5FA),
        OWNED_ACTIVE("✓", 0xCC24372D, 0xFF78C69A, 0xFFE1F5E8),
        OWNED_INACTIVE("○", 0xCC2C2A31, 0xFF96909D, 0xFFC5C0CA),
        SUSPENDED("!", 0xCC3B2929, 0xFFD47A7A, 0xFFF0C4C4),
        WILL_SUSPEND("!", 0xCC3D3425, 0xFFD1B36A, 0xFFF4DFAD),
        // Replaced skills remain owned but inactive. Reserve blue for staged purchases;
        // the replacement glyph and localized tooltip distinguish this from ordinary inactivity.
        REPLACED("↻", 0xCC2C2A31, 0xFF96909D, 0xFFC5C0CA);

        private final String glyph;
        private final int fillColor;
        private final int borderColor;
        private final int textColor;

        SkillNodeVisualState(
                String glyph,
                int fillColor,
                int borderColor,
                int textColor
        ) {
            this.glyph = glyph;
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.textColor = textColor;
        }

        String glyph() {
            return glyph;
        }

        int fillColor() {
            return fillColor;
        }

        int borderColor() {
            return borderColor;
        }

        int textColor() {
            return textColor;
        }
    }

    private enum PendingDecision {
        NONE,
        EXIT
    }

    private enum PendingCompletion {
        NONE,
        EXIT
    }
}
