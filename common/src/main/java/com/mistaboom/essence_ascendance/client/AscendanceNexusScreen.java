package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.nexus.NexusCategoryView;
import com.mistaboom.essence_ascendance.client.nexus.NexusCategoryViewFactory;
import com.mistaboom.essence_ascendance.client.nexus.NexusDraft;
import com.mistaboom.essence_ascendance.client.nexus.NexusMode;
import com.mistaboom.essence_ascendance.client.nexus.NexusProgressionTrack;
import com.mistaboom.essence_ascendance.client.nexus.NexusSkillTreeLayout;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.BalanceProfileRegistry;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusMenu;
import com.mistaboom.essence_ascendance.network.AscendanceNexusTransactionPayload;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.skill.requirement.BonusInvestmentRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import com.mistaboom.essence_ascendance.skill.SkillActivationPolicy;
import com.mistaboom.essence_ascendance.skill.SkillChoiceGroup;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillDisplayState;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationContext;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationResult;
import com.mistaboom.essence_ascendance.skill.SkillPrerequisiteStatus;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.SkillRequirementStatus;
import com.mistaboom.essence_ascendance.skill.SkillStateEvaluator;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.client.gui.GuiGraphics;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
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
        extends AbstractContainerScreen<AscendanceNexusMenu> {

    private static final int BACKGROUND_TOP = 0xFF15131D;
    private static final int BACKGROUND_BOTTOM = 0xFF292432;
    private static final int PANEL = 0xCC1C1B25;
    private static final int PANEL_INNER = 0xCC292733;
    private static final int BORDER = 0xFF77658E;
    private static final int BORDER_BRIGHT = 0xFFA58AC6;
    private static final int TEXT = 0xFFF0EDF4;
    private static final int MUTED = 0xFFB6AFBF;
    private static final int DIM = 0xFF7F7889;
    private static final int TRACK = 0xFF494352;
    private static final int TRACK_FILL = 0xFF9E80C0;
    private static final int TRACK_PREVIEW = 0xFFC3A8DF;
    private static final int TIER_LINE = 0x665F5969;
    private static final int LOCKED = 0x552F2B35;
    private static final int GAUGE_FILL = 0xFF7B62A2;
    private static final int COMPLETE = 0xFF78C69A;
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
    private static final int MODE_SELECTOR_WIDTH = 306;
    private static final int MODE_SELECTOR_HEIGHT = 24;
    private static final int MODE_SELECTOR_Y = 5;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 3;
    private static final int TAB_ARROW_WIDTH = 18;
    private static final int TAB_MIN_WIDTH = 64;
    private static final int TAB_MAX_WIDTH = 102;
    private static final int TRACK_PREFERRED_WIDTH = 66;
    private static final int MIN_SIDE_SECTION_WIDTH = 58;
    private static final int TRACK_GAP = 4;
    private static final int TRACK_ARROW_WIDTH = 20;
    private static final int TRACK_KNOB_HEIGHT = 7;
    private static final int TRACK_HIT_PADDING = 4;
    private static final int ALLOCATE_BUTTON_WIDTH = 104;
    private static final int ASCEND_BUTTON_WIDTH = 84;
    private static final int BOTTOM_CONTROL_HEIGHT = 16;
    private static final int BOTTOM_CONTROL_GAP = 5;
    private static final int GAUGE_TEXT_GAP = 4;
    private static final int SECTION_HEIGHT = 34;
    private static final int NARROW_SECTION_HEIGHT = 30;
    private static final int SKILL_LEGEND_HEIGHT = 22;
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
    /*
     * Vanilla tooltips render above ordinary GUI content.  The pending-change
     * confirmation is a true modal, so it must sit above both the page and any
     * tooltip that AbstractContainerScreen may already have queued.
     */
    private static final float MODAL_RENDER_DEPTH = 500.0F;
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong(1L);

    private int selectedCategoryIndex = 0;
    private int tabWindowStart = 0;
    private int trackWindowStart = 0;

    private int draggingTrackIndex = -1;
    private NexusMode mode = NexusMode.BONUSES;
    private final NexusDraft draft = new NexusDraft();
    private PendingDecision pendingDecision = PendingDecision.NONE;
    private PendingCompletion pendingCompletion = PendingCompletion.NONE;
    private long pendingRequestId = -1L;
    private long acceptedRevision = -1L;
    private Component transactionFeedback;
    private final Map<ResourceLocation, Double> skillScrollX =
            new LinkedHashMap<>();
    private final Map<ResourceLocation, Double> skillScrollY =
            new LinkedHashMap<>();
    private final Map<ResourceLocation, NexusSkillTreeLayout.Layout> skillLayouts =
            new LinkedHashMap<>();
    private boolean panningSkills;
    private double lastSkillPanX;
    private double lastSkillPanY;
    private ResourceLocation hoveredSkillId;

    private ResourceLocation selectedEssenceId;

    public AscendanceNexusScreen(
            AscendanceNexusMenu menu,
            Inventory playerInventory,
            Component title
    ) {
        super(menu, playerInventory, title);
        imageWidth = 0;
        imageHeight = 0;
        titleLabelX = 0;
        titleLabelY = 0;
        inventoryLabelX = 0;
        inventoryLabelY = 0;
    }

    @Override
    protected void init() {
        super.init();
        draggingTrackIndex = -1;
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
        hoveredSkillId = null;
        graphics.fillGradient(
                0,
                0,
                width,
                height,
                BACKGROUND_TOP,
                BACKGROUND_BOTTOM
        );

        draft.synchronize(ClientEssenceState.snapshot());

        List<NexusCategoryView> categories =
                categories();

        renderModeSelector(
                graphics,
                mouseX,
                mouseY
        );

        if (!ClientEssenceState.ready()) {
            renderWaitingState(graphics);
            return;
        }

        if (mode != NexusMode.ASCENDANCE
                && !categories.isEmpty()) {
            stabilizeSelection(categories);
            renderTabs(graphics, categories);
        }

        if (mode == NexusMode.ASCENDANCE) {
            renderAscensionPage(
                    graphics,
                    mouseX,
                    mouseY
            );
            return;
        }

        if (categories.isEmpty()) {
            renderWaitingState(graphics);
            return;
        }

        NexusCategoryView category =
                categories.get(selectedCategoryIndex);

        if (mode == NexusMode.SKILLS) {
            renderSkillsCategory(graphics, category, mouseX, mouseY);
        } else {
            renderEssenceCategory(
                    graphics,
                    category,
                    mouseX,
                    mouseY
            );
        }
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        /* Fullscreen shell renders all labels in absolute screen coordinates. */
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        consumeTransactionResult();
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        if (pendingDecision != PendingDecision.NONE) {
            /*
             * GuiGraphics batches fills and glyphs by render type.  Flush the
             * page first so a delayed skill label or vanilla tooltip cannot be
             * submitted after the modal backdrop, then render the complete
             * modal at tooltip-level depth as one isolated layer.
             */
            hoveredSkillId = null;
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, MODAL_RENDER_DEPTH);
            try {
                renderPendingDecision(
                        graphics,
                        mouseX,
                        mouseY
                );
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        } else {
            if (mode == NexusMode.SKILLS && hoveredSkillId != null) {
                renderSkillTooltip(graphics, hoveredSkillId, mouseX, mouseY);
            } else {
                renderPrimaryActionTooltip(
                        graphics,
                        mouseX,
                        mouseY
                );
            }
        }
    }

    private void renderModeSelector(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int selectorWidth = Math.min(
                MODE_SELECTOR_WIDTH,
                Math.max(3, width - 2 * SAFE_MARGIN)
        );
        int left = (width - selectorWidth) / 2;
        int segmentWidth = selectorWidth / NexusMode.values().length;

        for (int i = 0; i < NexusMode.values().length; i++) {
            NexusMode candidate = NexusMode.values()[i];
            int x = left + i * segmentWidth;
            int segmentRight = i == NexusMode.values().length - 1
                    ? left + selectorWidth
                    : x + segmentWidth;
            int segmentActualWidth = segmentRight - x;
            boolean selected = candidate == mode;
            boolean hovered = inside(
                    mouseX,
                    mouseY,
                    x,
                    MODE_SELECTOR_Y,
                    segmentActualWidth,
                    MODE_SELECTOR_HEIGHT
            );

            graphics.fill(
                    x,
                    MODE_SELECTOR_Y,
                    segmentRight,
                    MODE_SELECTOR_Y + MODE_SELECTOR_HEIGHT,
                    selected ? PANEL_INNER : hovered ? 0xAA292733 : 0x44292733
            );
            outline(
                    graphics,
                    x,
                    MODE_SELECTOR_Y,
                    segmentActualWidth,
                    MODE_SELECTOR_HEIGHT,
                    selected || hovered ? BORDER_BRIGHT : 0x8877658E
            );
            graphics.drawCenteredString(
                    font,
                    trimToWidth(
                            EssenceText.gui(candidate.translationPath()).getString(),
                            Math.max(1, segmentActualWidth - 8)
                    ),
                    x + segmentActualWidth / 2,
                    MODE_SELECTOR_Y + Math.max(
                            2,
                            (MODE_SELECTOR_HEIGHT - font.lineHeight) / 2
                    ),
                    selected ? TEXT : MUTED
            );
        }
    }

    private void renderWaitingState(
            GuiGraphics graphics
    ) {
        int panelWidth =
                Math.min(320, Math.max(180, width - 2 * SAFE_MARGIN));
        int panelHeight = 54;
        int left = (width - panelWidth) / 2;
        int top = Math.max(52, (height - panelHeight) / 2);

        graphics.fill(
                left,
                top,
                left + panelWidth,
                top + panelHeight,
                PANEL
        );
        outline(
                graphics,
                left,
                top,
                panelWidth,
                panelHeight,
                BORDER
        );

        graphics.drawCenteredString(
                font,
                EssenceText.gui("nexus.synchronizing").getString(),
                width / 2,
                top + 24,
                TEXT
        );
    }

    private void renderTabs(
            GuiGraphics graphics,
            List<NexusCategoryView> categories
    ) {
        TabLayout layout =
                tabLayout(categories);

        boolean canPageLeft =
                tabWindowStart > 0;
        boolean canPageRight =
                tabWindowStart + layout.visibleCount()
                        < categories.size();

        renderArrow(
                graphics,
                layout.leftArrowX(),
                layout.y(),
                TAB_ARROW_WIDTH,
                TAB_HEIGHT,
                "‹",
                canPageLeft
        );

        renderArrow(
                graphics,
                layout.rightArrowX(),
                layout.y(),
                TAB_ARROW_WIDTH,
                TAB_HEIGHT,
                "›",
                canPageRight
        );

        int x = layout.tabsLeft();
        int end = Math.min(
                categories.size(),
                tabWindowStart + layout.visibleCount()
        );

        for (int i = tabWindowStart; i < end; i++) {
            NexusCategoryView category =
                    categories.get(i);

            boolean selected =
                    i == selectedCategoryIndex;

            graphics.fill(
                    x,
                    layout.y(),
                    x + layout.tabWidth(),
                    layout.y() + TAB_HEIGHT,
                    selected ? PANEL_INNER : PANEL
            );

            outline(
                    graphics,
                    x,
                    layout.y(),
                    layout.tabWidth(),
                    TAB_HEIGHT,
                    selected ? BORDER_BRIGHT : BORDER
            );

            String tabName =
                    trimToWidth(
                            EssenceText.essenceShort(category.essence()).getString(),
                            layout.tabWidth() - 10
                    );

            graphics.drawCenteredString(
                    font,
                    tabName,
                    x + layout.tabWidth() / 2,
                    layout.y() + 6,
                    selected ? TEXT : MUTED
            );

            x += layout.tabWidth() + TAB_GAP;
        }
    }

    private void renderAscensionPage(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        ContentLayout layout =
                contentLayout();

        graphics.fill(
                layout.left(),
                layout.top(),
                layout.right(),
                layout.bottom(),
                PANEL
        );
        outline(
                graphics,
                layout.left(),
                layout.top(),
                layout.width(),
                layout.height(),
                BORDER_BRIGHT
        );

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

        switch (progress.status()) {
            case AVAILABLE ->
                    renderAscensionRequirements(
                            graphics,
                            progress,
                            layout,
                            mouseX,
                            mouseY
                    );
            case MAX_TIER ->
                    renderAscensionMaxTier(
                            graphics,
                            snapshot,
                            layout
                    );
            case CONFIGURATION_ERROR ->
                    renderAscensionConfigurationError(
                            graphics,
                            layout
                    );
        }

        renderAscendControls(
                graphics,
                progress,
                layout,
                mouseX,
                mouseY
        );
    }

    private void renderAscensionTransition(
            GuiGraphics graphics,
            ClientEssenceState.Snapshot snapshot,
            ClientEssenceState.ProgressSnapshot progress,
            ContentLayout layout
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

    private void renderAscensionRequirements(
            GuiGraphics graphics,
            ClientEssenceState.ProgressSnapshot progress,
            ContentLayout layout,
            int mouseX,
            int mouseY
    ) {
        int innerLeft = layout.left() + 8;
        int innerRight = layout.right() - 8;
        int y = layout.middleTop() + 5;
        int rowHeight =
                height < 260 ? 25 : 30;
        int rowGap = 3;
        ProjectedAscension projected = projectedAscension(progress);

        y = renderAscensionRequirementRow(
                graphics,
                innerLeft,
                innerRight,
                y,
                rowHeight,
                EssenceText.gui("nexus.total_bonus_investment").getString(),
                formatLong(projected.effectiveInvestment())
                        + " / "
                        + formatLong(progress.requiredInvestment()),
                projected.effectiveInvestment(),
                progress.requiredInvestment(),
                projected.effectiveInvestment()
                        >= progress.requiredInvestment()
        ) + rowGap;

        y = renderAscensionRequirementRow(
                graphics,
                innerLeft,
                innerRight,
                y,
                rowHeight,
                developedStatsRequirementLabel(progress),
                projected.developedStats()
                        + " / "
                        + progress.requiredDevelopedStats(),
                projected.developedStats(),
                progress.requiredDevelopedStats(),
                projected.developedStats()
                        >= progress.requiredDevelopedStats()
        ) + rowGap;

        y = renderAscensionRequirementRow(
                graphics,
                innerLeft,
                innerRight,
                y,
                rowHeight,
                developedCategoryRequirementLabel(progress),
                projected.representedCategories()
                        + " / "
                        + progress.requiredRepresentedCategories(),
                projected.representedCategories(),
                progress.requiredRepresentedCategories(),
                projected.representedCategories()
                        >= progress.requiredRepresentedCategories()
        ) + rowGap;

        renderWorldRequirements(
                graphics,
                progress,
                innerLeft,
                innerRight,
                y,
                layout.middleBottom() - 5
        );

    }

    private String developedStatsRequirementLabel(
            ClientEssenceState.ProgressSnapshot progress
    ) {
        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();
        Long sharedThreshold = null;

        for (ClientEssenceState.StatSnapshot stat :
                snapshot.stats().values()) {
            if (stat.currentInvestmentCap() <= 0L) {
                continue;
            }

            long threshold =
                    (long) Math.ceil(
                            stat.currentInvestmentCap()
                                    * progress.developedStatThreshold()
                    );

            if (sharedThreshold == null) {
                sharedThreshold = threshold;
                continue;
            }

            if (sharedThreshold.longValue() != threshold) {
                int thresholdPercent =
                        (int) Math.round(
                                progress.developedStatThreshold() * 100.0D
                        );

                return EssenceText.gui(
                        "nexus.developed_stats_percent",
                        thresholdPercent
                ).getString();
            }
        }

        if (sharedThreshold == null) {
            return EssenceText.gui("nexus.developed_stats").getString();
        }

        return EssenceText.gui(
                "nexus.developed_stats_value",
                formatLong(sharedThreshold)
        ).getString();
    }

    private String developedCategoryRequirementLabel(
            ClientEssenceState.ProgressSnapshot progress
    ) {
        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();

        Long sharedThreshold = null;

        for (ClientEssenceState.StatSnapshot stat :
                snapshot.stats().values()) {

            if (stat.currentInvestmentCap() <= 0L) {
                continue;
            }

            long threshold =
                    (long) Math.ceil(
                            stat.currentInvestmentCap()
                                    * progress.developedStatThreshold()
                    );

            if (sharedThreshold == null) {
                sharedThreshold = threshold;
                continue;
            }

            if (sharedThreshold.longValue() != threshold) {
                int thresholdPercent =
                        (int) Math.round(
                                progress.developedStatThreshold() * 100.0D
                        );

                return EssenceText.gui(
                        "nexus.developed_categories_percent",
                        thresholdPercent
                ).getString();
            }
        }

        if (sharedThreshold == null) {
            return EssenceText.gui("nexus.developed_categories").getString();
        }

        return EssenceText.gui(
                "nexus.developed_categories_value",
                formatLong(sharedThreshold)
        ).getString();
    }

    private ProjectedAscension projectedAscension(
            ClientEssenceState.ProgressSnapshot progress
    ) {
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        long investment = 0L;
        int developedStats = 0;
        Set<ResourceLocation> developedEssences = new java.util.LinkedHashSet<>();
        Map<ResourceLocation, ResourceLocation> statEssences = statEssenceIds();

        for (Map.Entry<ResourceLocation, ClientEssenceState.StatSnapshot> entry :
                snapshot.stats().entrySet()) {
            ClientEssenceState.StatSnapshot state = entry.getValue();
            long effective = Math.min(
                    Math.max(0L, draft.bonusTarget(entry.getKey(), snapshot)),
                    Math.max(0L, state.currentInvestmentCap())
            );
            investment = safeAddNonNegative(investment, effective);

            long threshold = (long) Math.ceil(
                    state.currentInvestmentCap()
                            * progress.developedStatThreshold()
            );
            if (state.currentInvestmentCap() > 0L
                    && effective >= threshold) {
                developedStats++;
                ResourceLocation essenceId = statEssences.get(entry.getKey());
                if (essenceId != null) {
                    developedEssences.add(essenceId);
                }
            }
        }

        return new ProjectedAscension(
                investment,
                developedStats,
                developedEssences.size()
        );
    }

    private int renderAscensionRequirementRow(
            GuiGraphics graphics,
            int left,
            int right,
            int top,
            int rowHeight,
            String label,
            String value,
            long current,
            long required,
            boolean complete
    ) {
        int bottom = top + rowHeight;

        graphics.fill(
                left,
                top,
                right,
                bottom,
                0x66292733
        );
        outline(
                graphics,
                left,
                top,
                right - left,
                rowHeight,
                complete ? COMPLETE : 0x665F5969
        );

        String renderedLabel =
                trimToWidth(
                        label,
                        Math.max(20, (right - left) * 2 / 3)
                );
        String renderedValue =
                formatRequirementValueForWidth(
                        value,
                        Math.max(20, (right - left) / 3 - 8)
                );

        graphics.drawString(
                font,
                renderedLabel,
                left + 6,
                top + 5,
                complete ? COMPLETE : TEXT,
                false
        );
        graphics.drawString(
                font,
                renderedValue,
                right - 6 - font.width(renderedValue),
                top + 5,
                complete ? COMPLETE : MUTED,
                false
        );

        int barLeft = left + 6;
        int barRight = right - 6;
        int barTop = bottom - 7;
        graphics.fill(
                barLeft,
                barTop,
                barRight,
                barTop + 3,
                TRACK
        );

        double fraction =
                required <= 0L
                        ? 1.0D
                        : clamp01(current / (double) required);
        int fill =
                (int) Math.round(
                        (barRight - barLeft) * fraction
                );
        graphics.fill(
                barLeft,
                barTop,
                barLeft + fill,
                barTop + 3,
                complete ? COMPLETE : TRACK_PREVIEW
        );

        return bottom;
    }

    private String formatRequirementValueForWidth(
            String value,
            int maximumWidth
    ) {
        if (font.width(value) <= maximumWidth) {
            return value;
        }

        int separator = value.indexOf(" / ");
        if (separator > 0) {
            try {
                long current =
                        Long.parseLong(
                                value.substring(0, separator)
                                        .replace(",", "")
                        );
                long required =
                        Long.parseLong(
                                value.substring(separator + 3)
                                        .replace(",", "")
                        );
                String compact =
                        formatCompactLong(current, 1)
                                + " / "
                                + formatCompactLong(required, 1);
                if (font.width(compact) <= maximumWidth) {
                    return compact;
                }
            } catch (NumberFormatException ignored) {
                /* Count-based requirements fall through to normal trimming. */
            }
        }

        return trimToWidth(
                value,
                maximumWidth
        );
    }

    private void renderWorldRequirements(
            GuiGraphics graphics,
            ClientEssenceState.ProgressSnapshot progress,
            int left,
            int right,
            int top,
            int bottom
    ) {
        if (bottom <= top + font.lineHeight + 4) {
            return;
        }

        graphics.fill(
                left,
                top,
                right,
                bottom,
                0x44292733
        );
        outline(
                graphics,
                left,
                top,
                right - left,
                bottom - top,
                progress.worldProgressComplete()
                        ? COMPLETE
                        : 0x665F5969
        );

        graphics.drawString(
                font,
                EssenceText.gui("nexus.world_progression").getString(),
                left + 6,
                top + 4,
                progress.worldProgressComplete()
                        ? COMPLETE
                        : TEXT,
                false
        );

        List<ClientEssenceState.WorldRequirementSnapshot> requirements =
                progress.worldRequirements();

        if (requirements.isEmpty()) {
            graphics.drawString(
                    font,
                    progress.worldProgressComplete()
                            ? EssenceText.gui("nexus.no_world_milestone_required").getString()
                            : EssenceText.gui("nexus.no_resolvable_world_requirement").getString(),
                    left + 10,
                    top + 16,
                    progress.worldProgressComplete()
                            ? COMPLETE
                            : ERROR,
                    false
            );
            return;
        }

        int lineY = top + 16;
        int lineHeight = 10;
        int maximumLines =
                Math.max(
                        1,
                        (bottom - lineY - 3) / lineHeight
                );
        int renderedLines =
                Math.min(
                        maximumLines,
                        requirements.size()
                );

        for (int i = 0; i < renderedLines; i++) {
            ClientEssenceState.WorldRequirementSnapshot requirement =
                    requirements.get(i);

            int indent =
                    Math.min(
                            36,
                            requirement.depth() * 10
                    );
            int textLeft = left + 10 + indent;
            int textWidth =
                    Math.max(
                            1,
                            right - 8 - textLeft
                    );

            String prefix;
            if (!requirement.resolvable()) {
                prefix = "! ";
            } else if (requirement.complete()) {
                prefix = "✓ ";
            } else if (requirement.kind()
                    == PlayerEssenceSyncPayload.WorldRequirementKind.MILESTONE) {
                prefix = "• ";
            } else {
                prefix = "";
            }

            int color =
                    !requirement.resolvable()
                            ? ERROR
                            : requirement.complete()
                            ? COMPLETE
                            : requirement.kind()
                            == PlayerEssenceSyncPayload.WorldRequirementKind.MILESTONE
                            ? MUTED
                            : DIM;

            graphics.drawString(
                    font,
                    trimToWidth(
                            prefix + worldRequirementLabel(requirement),
                            textWidth
                    ),
                    textLeft,
                    lineY + i * lineHeight,
                    color,
                    false
            );
        }

        if (renderedLines < requirements.size()) {
            String more = EssenceText.gui(
                    "nexus.more_requirements",
                    requirements.size() - renderedLines
            ).getString();
            graphics.drawString(
                    font,
                    trimToWidth(
                            more,
                            Math.max(1, right - left - 20)
                    ),
                    left + 10,
                    bottom - font.lineHeight - 2,
                    DIM,
                    false
            );
        }
    }

    private String worldRequirementLabel(
            ClientEssenceState.WorldRequirementSnapshot requirement
    ) {
        return switch (requirement.kind()) {
            case ALL_OF -> EssenceText.gui("nexus.world.all_of").getString();
            case ANY_OF -> EssenceText.gui("nexus.world.any_of").getString();
            case ALWAYS -> EssenceText.gui("nexus.world.none_required").getString();
            case MILESTONE -> requirement.label();
        };
    }

    private void renderAscendControls(
            GuiGraphics graphics,
            ClientEssenceState.ProgressSnapshot progress,
            ContentLayout layout,
            int mouseX,
            int mouseY
    ) {
        boolean staged =
                hasStagedChanges();
        boolean projectedReady = projectedAscension(progress).ready(progress);
        boolean enabled =
                pendingRequestId < 0L
                        && (staged || projectedReady);
        int buttonY =
                layout.bottom()
                        - BOTTOM_CONTROL_HEIGHT
                        - 3;
        int buttonX =
                (layout.left() + layout.right() - ASCEND_BUTTON_WIDTH) / 2;

        if (layout.sectionHeight() >= 28) {
            String status;
            if (staged) {
                status = EssenceText.gui("nexus.pending_before_ascending").getString();
            } else if (progress.status()
                    == PlayerEssenceSyncPayload.ProgressStatus.MAX_TIER) {
                status = EssenceText.gui("nexus.maximum").getString();
            } else if (progress.status()
                    == PlayerEssenceSyncPayload.ProgressStatus.CONFIGURATION_ERROR) {
                status = EssenceText.gui("nexus.unavailable").getString();
            } else if (projectedReady) {
                status = EssenceText.gui("nexus.ready_to_ascend").getString();
            } else {
                status = EssenceText.gui("nexus.requirements_incomplete").getString();
            }
            graphics.drawCenteredString(
                    font,
                    trimToWidth(
                            status,
                            Math.max(40, layout.width() - 20)
                    ),
                    (layout.left() + layout.right()) / 2,
                    layout.middleBottom() + 3,
                    enabled ? COMPLETE : MUTED
            );
        }

        renderAscendButton(
                graphics,
                buttonX,
                buttonY,
                enabled,
                inside(
                        mouseX,
                        mouseY,
                        buttonX,
                        buttonY,
                        ASCEND_BUTTON_WIDTH,
                        BOTTOM_CONTROL_HEIGHT
                )
        );
    }

    private void renderAscendButton(
            GuiGraphics graphics,
            int x,
            int y,
            boolean enabled,
            boolean hovered
    ) {
        graphics.fill(
                x,
                y,
                x + ASCEND_BUTTON_WIDTH,
                y + BOTTOM_CONTROL_HEIGHT,
                enabled
                        ? hovered ? 0xFF41364F : PANEL_INNER
                        : 0x88201E26
        );
        outline(
                graphics,
                x,
                y,
                ASCEND_BUTTON_WIDTH,
                BOTTOM_CONTROL_HEIGHT,
                enabled
                        ? hovered ? 0xFFD0ADEB : BORDER_BRIGHT
                        : 0xFF4A4650
        );
        graphics.drawCenteredString(
                font,
                EssenceText.gui("nexus.ascend").getString(),
                x + ASCEND_BUTTON_WIDTH / 2,
                y + Math.max(
                        2,
                        (BOTTOM_CONTROL_HEIGHT - font.lineHeight) / 2
                ),
                enabled ? TEXT : DIM
        );
    }

    private void renderAscensionMaxTier(
            GuiGraphics graphics,
            ClientEssenceState.Snapshot snapshot,
            ContentLayout layout
    ) {
        int centerX =
                (layout.left() + layout.right()) / 2;
        int centerY =
                (layout.middleTop() + layout.middleBottom()) / 2;

        graphics.drawCenteredString(
                font,
                tierDisplayName(snapshot.tierId()).toUpperCase(Locale.ROOT),
                centerX,
                centerY - 14,
                COMPLETE
        );
        graphics.drawCenteredString(
                font,
                EssenceText.gui("nexus.maximum_achieved").getString(),
                centerX,
                centerY + 4,
                TEXT
        );
        graphics.drawCenteredString(
                font,
                EssenceText.gui("nexus.no_higher_tier").getString(),
                centerX,
                centerY + 18,
                MUTED
        );
    }

    private void renderAscensionConfigurationError(
            GuiGraphics graphics,
            ContentLayout layout
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
                ERROR
        );
        graphics.drawCenteredString(
                font,
                trimToWidth(
                        EssenceText.gui("nexus.ascension_config_error").getString(),
                        Math.max(80, layout.width() - 24)
                ),
                centerX,
                centerY + 5,
                MUTED
        );
    }

    private void renderEssenceCategory(
            GuiGraphics graphics,
            NexusCategoryView category,
            int mouseX,
            int mouseY
    ) {
        ContentLayout layout =
                contentLayout();

        graphics.fill(
                layout.left(),
                layout.top(),
                layout.right(),
                layout.bottom(),
                PANEL
        );
        outline(
                graphics,
                layout.left(),
                layout.top(),
                layout.width(),
                layout.height(),
                BORDER
        );

        renderCategoryTitle(
                graphics,
                EssenceText.essenceShort(category.essence()).getString(),
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
        ContentLayout layout = contentLayout();
        graphics.fill(
                layout.left(),
                layout.top(),
                layout.right(),
                layout.bottom(),
                PANEL
        );
        outline(
                graphics,
                layout.left(),
                layout.top(),
                layout.width(),
                layout.height(),
                BORDER
        );

        renderCategoryTitle(
                graphics,
                EssenceText.essenceShort(category.essence()).getString(),
                layout
        );
        renderAvailableGauge(graphics, category, layout);
        renderSkillLegend(graphics, layout);
        renderSkillTree(graphics, category, layout, mouseX, mouseY);

        BottomControls controls = bottomControls(layout);
        renderAllocateButton(
                graphics,
                controls.allocateX(),
                controls.y(),
                primaryActionEnabled()
        );
        renderSkillsFooter(graphics, layout);
    }

    private void renderSkillLegend(
            GuiGraphics graphics,
            ContentLayout layout
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
    }

    private void renderSkillTree(
            GuiGraphics graphics,
            NexusCategoryView category,
            ContentLayout layout,
            int mouseX,
            int mouseY
    ) {
        SkillViewport viewport = skillViewport(layout);
        graphics.fill(
                viewport.left(),
                viewport.top(),
                viewport.right(),
                viewport.bottom(),
                PANEL_INNER
        );
        outline(
                graphics,
                viewport.left(),
                viewport.top(),
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
                    (viewport.left() + viewport.right()) / 2,
                    (viewport.top() + viewport.bottom()) / 2,
                    MUTED
            );
            return;
        }

        NexusSkillTreeLayout.Layout tree = skillLayout(category.essence().id());
        SkillScroll scroll = clampedSkillScroll(
                category.essence().id(),
                tree,
                viewport
        );
        Map<ResourceLocation, SkillEvaluationResult> evaluations =
                skillEvaluations();
        long projectedBalance = stagedAvailableEssence(category);

        graphics.enableScissor(
                viewport.left() + 1,
                viewport.top() + 1,
                viewport.right() - 1,
                viewport.bottom() - 1
        );

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

        graphics.disableScissor();
        renderSkillScrollIndicators(graphics, tree, viewport, scroll);
    }

    private List<SkillChoiceFrame> skillChoiceFrames(
            List<SkillDefinition> definitions,
            NexusSkillTreeLayout.Layout tree,
            SkillViewport viewport,
            SkillScroll scroll
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
            SkillViewport viewport,
            SkillScroll scroll,
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
            outline(
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
            SkillViewport viewport,
            SkillScroll scroll,
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
            SkillViewport viewport,
            SkillScroll scroll
    ) {
        for (int column = 0; column < tree.tiers().size(); column++) {
            AscendanceTierDefinition tier = tree.tiers().get(column);
            int centerX = skillOriginX(viewport, tree, scroll)
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
                    skillOriginY(viewport, tree, scroll)
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
        outline(
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

        String renderedCost = formatLongForWidth(
                cost,
                Math.max(1, bounds.width() - 8)
        );
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
            SkillViewport viewport,
            SkillScroll scroll
    ) {
        boolean canScrollLeft = scroll.maximumX() > 0.0
                && scroll.x() > 0.0;
        boolean canScrollRight = scroll.maximumX() > 0.0
                && scroll.x() < scroll.maximumX();

        if (canScrollLeft || canScrollRight) {
            int arrowY = (viewport.top() + viewport.bottom()
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
                            viewport.left() + SKILL_SCROLL_ARROW_INSET,
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
                    viewport.left() + 4,
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
            ContentLayout layout
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

    private SkillViewport skillViewport(ContentLayout layout) {
        int left = Math.min(layout.right() - 2, layout.tracksLeft() + 3);
        int top = Math.min(
                layout.middleBottom() - 2,
                layout.middleTop() + SKILL_LEGEND_HEIGHT + 2
        );
        return new SkillViewport(
                left,
                top,
                Math.max(left + 1, layout.right() - 4),
                Math.max(top + 1, layout.middleBottom() - 2)
        );
    }

    private SkillScroll clampedSkillScroll(
            ResourceLocation essenceId,
            NexusSkillTreeLayout.Layout tree,
            SkillViewport viewport
    ) {
        double maximumX = Math.max(0.0, tree.contentWidth() - viewport.width());
        double maximumY = Math.max(0.0, tree.contentHeight() - viewport.height());
        double x = Math.max(
                0.0,
                Math.min(maximumX, skillScrollX.getOrDefault(essenceId, 0.0))
        );
        double y = Math.max(
                0.0,
                Math.min(maximumY, skillScrollY.getOrDefault(essenceId, 0.0))
        );
        skillScrollX.put(essenceId, x);
        skillScrollY.put(essenceId, y);
        return new SkillScroll(x, y, maximumX, maximumY);
    }

    private int skillOriginX(
            SkillViewport viewport,
            NexusSkillTreeLayout.Layout tree,
            SkillScroll scroll
    ) {
        return viewport.left()
                + Math.max(0, (viewport.width() - tree.contentWidth()) / 2)
                - (int) Math.round(scroll.x());
    }

    private int skillOriginY(
            SkillViewport viewport,
            NexusSkillTreeLayout.Layout tree,
            SkillScroll scroll
    ) {
        return viewport.top()
                + Math.max(0, (viewport.height() - tree.contentHeight()) / 2)
                - (int) Math.round(scroll.y());
    }

    private Rect skillNodeBounds(
            NexusSkillTreeLayout.Node node,
            SkillViewport viewport,
            SkillScroll scroll
    ) {
        NexusSkillTreeLayout.Layout tree = skillLayout(node.definition().essenceId());
        int left = skillOriginX(viewport, tree, scroll) + node.x();
        int top = skillOriginY(viewport, tree, scroll) + node.y();
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

        Set<ResourceLocation> authoritativeOwned =
                new java.util.LinkedHashSet<>(snapshot.ownedSkills().keySet());
        Set<ResourceLocation> projectedOwned =
                new java.util.LinkedHashSet<>(authoritativeOwned);
        projectedOwned.addAll(draft.stagedPurchaseIds());

        SkillEvaluationContext context = new SkillEvaluationContext(
                snapshot.tierId(),
                authoritativeOwned,
                projectedOwned,
                snapshot.loadoutSelections(),
                draft.finalLoadouts(snapshot),
                snapshot.completedAttunements(),
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
            return definition.cost(profile);
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
            String name,
            ContentLayout layout
    ) {
        String value =
                name.toUpperCase(Locale.ROOT);

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
                TEXT
        );
        graphics.pose().popPose();
    }

    private void renderAvailableGauge(
            GuiGraphics graphics,
            NexusCategoryView category,
            ContentLayout layout
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
        outline(
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
                GAUGE_FILL
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
            ContentLayout layout
    ) {
        List<AscendanceTierDefinition> tiers =
                orderedTiers();

        if (tiers.isEmpty()) {
            return;
        }

        int trackHeight =
                layout.trackBottom() - layout.trackTop();

        /*
         * The progression track uses equal-height visual tier bands even though
         * the underlying Essence requirements grow rapidly. Put the actual cap
         * represented by each internal boundary directly beside that line so
         * the nonlinear slider behavior is explicit instead of mysterious.
         *
         * There is intentionally no 0 label at the bottom and no final cap at
         * the top: those endpoints are already represented by the slider's
         * allocation/current-cap readouts. Only the four internal tier caps are
         * shown here (Dormant through Ascendant in the built-in five-tier set).
         */
        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();
        BalanceProfileDefinition profile =
                currentBalanceProfile(snapshot);

        for (int boundary = 1; boundary < tiers.size(); boundary++) {
            double fraction =
                    boundary / (double) tiers.size();
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

            if (profile != null) {
                AscendanceTierDefinition completedTier =
                        tiers.get(boundary - 1);
                long tierCap;

                /*
                 * Prefer the actual stat cap when every track in the selected
                 * category agrees. If a future category mixes per-stat cap
                 * overrides, fall back to the profile's shared default because
                 * one common right-side guide cannot represent multiple values.
                 */
                if (!category.tracks().isEmpty()) {
                    long firstCap =
                            profile.getInvestmentCap(
                                    completedTier,
                                    category.tracks().get(0).stat()
                            );
                    boolean shared = true;

                    for (int i = 1; i < category.tracks().size(); i++) {
                        long candidate =
                                profile.getInvestmentCap(
                                        completedTier,
                                        category.tracks().get(i).stat()
                                );
                        if (candidate != firstCap) {
                            shared = false;
                            break;
                        }
                    }

                    tierCap =
                            shared
                                    ? firstCap
                                    : profile.getDefaultInvestmentCap(completedTier);
                } else {
                    tierCap =
                            profile.getDefaultInvestmentCap(completedTier);
                }

                int tierLabelWidth =
                        Math.max(
                                1,
                                layout.right() - layout.tracksRight() - 6
                        );

                graphics.drawCenteredString(
                        font,
                        formatLongForWidth(
                                tierCap,
                                tierLabelWidth
                        ),
                        (layout.tracksRight() + layout.right()) / 2,
                        y - font.lineHeight / 2,
                        MUTED
                );
            }
        }
    }

    private void renderTracks(
            GuiGraphics graphics,
            NexusCategoryView category,
            ContentLayout layout,
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
            renderBottomControls(
                    graphics,
                    0,
                    1,
                    layout
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

        renderBottomControls(
                graphics,
                tracks.size(),
                visibleCount,
                layout
        );

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
            ContentLayout layout,
            int mouseX,
            int mouseY
    ) {
        int centerX =
                left + trackWidth / 2;

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
                TEXT
        );
        if (!nameLines[1].isBlank()) {
            graphics.drawCenteredString(
                    font,
                    nameLines[1],
                    centerX,
                    layout.middleTop() + 13,
                    TEXT
            );
        }

        String cap =
                formatLong(track.state().currentInvestmentCap());
        graphics.drawCenteredString(
                font,
                cap,
                centerX,
                layout.trackTop() - 11,
                DIM
        );

        int top = layout.trackTop();
        int bottom = layout.trackBottom();
        int height = bottom - top;
        int railLeft = centerX - 3;
        int railRight = centerX + 3;

        double tierCeiling =
                currentTierCeiling();

        if (tierCeiling < 1.0) {
            int ceilingY =
                    bottom - (int) Math.round(height * tierCeiling);
            graphics.fill(
                    left + 4,
                    top,
                    left + trackWidth - 4,
                    ceilingY,
                    LOCKED
            );
        }

        graphics.fill(
                railLeft,
                top,
                railRight,
                bottom,
                TRACK
        );

        long stagedTarget =
                stagedInvestment(track);
        boolean changed =
                stagedTarget != track.state().storedInvestment();
        boolean dragging =
                draggingTrackIndex == absoluteIndex;

        double progress =
                stagedProgression(track, stagedTarget);
        progress = clamp01(progress);

        int knobY =
                bottom - (int) Math.round(height * progress);

        graphics.fill(
                railLeft,
                knobY,
                railRight,
                bottom,
                changed || dragging ? TRACK_PREVIEW : TRACK_FILL
        );

        int knobWidth = 14;
        graphics.fill(
                centerX - knobWidth / 2,
                knobY - TRACK_KNOB_HEIGHT / 2,
                centerX + knobWidth / 2,
                knobY + (TRACK_KNOB_HEIGHT + 1) / 2,
                changed || dragging ? TRACK_PREVIEW : BORDER_BRIGHT
        );

        int hitTop =
                top - TRACK_HIT_PADDING;
        int hitBottom =
                bottom + TRACK_HIT_PADDING;

        boolean hovered =
                mouseX >= left
                        && mouseX < left + trackWidth
                        && mouseY >= hitTop
                        && mouseY <= hitBottom;

        if (hovered && !dragging) {
            outline(
                    graphics,
                    left + 1,
                    hitTop,
                    Math.max(1, trackWidth - 2),
                    Math.max(1, hitBottom - hitTop),
                    0x668E7AA7
            );
        }

        String investment =
                formatLong(stagedTarget);

        graphics.drawCenteredString(
                font,
                investment,
                centerX,
                bottom + 8,
                changed || dragging ? TEXT : MUTED
        );

        String bonus =
                formatBonus(
                        track.stat().unit(),
                        stagedScaledBonus(track, stagedTarget)
                );
        graphics.drawCenteredString(
                font,
                trimToWidth(bonus, trackWidth - 2),
                centerX,
                bottom + 19,
                TEXT
        );
    }

    private void renderBottomControls(
            GuiGraphics graphics,
            int trackCount,
            int visibleCount,
            ContentLayout layout
    ) {
        BottomControls controls =
                bottomControls(layout);

        renderArrow(
                graphics,
                controls.leftArrowX(),
                controls.y(),
                TRACK_ARROW_WIDTH,
                BOTTOM_CONTROL_HEIGHT,
                "‹",
                trackWindowStart > 0
        );

        renderAllocateButton(
                graphics,
                controls.allocateX(),
                controls.y(),
                primaryActionEnabled()
        );

        renderArrow(
                graphics,
                controls.rightArrowX(),
                controls.y(),
                TRACK_ARROW_WIDTH,
                BOTTOM_CONTROL_HEIGHT,
                "›",
                trackCount > visibleCount
                        && trackWindowStart + visibleCount < trackCount
        );
    }

    private void renderAllocateButton(
            GuiGraphics graphics,
            int x,
            int y,
            boolean enabled
    ) {
        graphics.fill(
                x,
                y,
                x + ALLOCATE_BUTTON_WIDTH,
                y + BOTTOM_CONTROL_HEIGHT,
                enabled ? PANEL_INNER : 0x88201E26
        );
        outline(
                graphics,
                x,
                y,
                ALLOCATE_BUTTON_WIDTH,
                BOTTOM_CONTROL_HEIGHT,
                enabled ? BORDER_BRIGHT : 0xFF4A4650
        );
        graphics.drawCenteredString(
                font,
                primaryActionLabel().getString(),
                x + ALLOCATE_BUTTON_WIDTH / 2,
                y + Math.max(
                        2,
                        (BOTTOM_CONTROL_HEIGHT - font.lineHeight) / 2
                ),
                enabled ? TEXT : DIM
        );
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
                && mode == NexusMode.BONUSES
                && bonusChangesBelongTo(visibleEssence, snapshot)) {
            return EssenceText.gui("nexus.allocate");
        }
        if (kinds == 1
                && purchase
                && mode == NexusMode.SKILLS
                && purchaseChangesBelongTo(visibleEssence)) {
            return EssenceText.gui("nexus.purchase");
        }
        if (kinds == 1
                && loadout
                && mode == NexusMode.SKILLS
                && loadoutChangesBelongTo(visibleEssence, snapshot)) {
            return EssenceText.gui("nexus.apply_loadout");
        }
        if (kinds > 0) {
            return EssenceText.gui("nexus.apply_changes");
        }
        return mode == NexusMode.SKILLS
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

    private BottomControls bottomControls(
            ContentLayout layout
    ) {
        int centerX =
                (layout.left() + layout.right()) / 2;
        int allocateX =
                centerX - ALLOCATE_BUTTON_WIDTH / 2;
        int leftArrowX =
                allocateX - BOTTOM_CONTROL_GAP - TRACK_ARROW_WIDTH;
        int rightArrowX =
                allocateX + ALLOCATE_BUTTON_WIDTH + BOTTOM_CONTROL_GAP;

        return new BottomControls(
                bottomControlsY(layout),
                leftArrowX,
                allocateX,
                rightArrowX
        );
    }

    private int bottomControlsY(
            ContentLayout layout
    ) {
        return layout.bottom()
                - BOTTOM_CONTROL_HEIGHT
                - 3;
    }

    private void renderFooter(
            GuiGraphics graphics,
            NexusCategoryView category,
            ContentLayout layout
    ) {
        String instruction;
        int color = MUTED;
        if (transactionFeedback != null) {
            instruction = transactionFeedback.getString();
            color = ERROR;
        } else if (draft.invalidated()) {
            instruction = EssenceText.gui("nexus.draft.outdated").getString();
            color = ERROR;
        } else if (pendingRequestId >= 0L) {
            instruction = EssenceText.gui("nexus.transaction.waiting").getString();
            color = INCOMPLETE;
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
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (pendingDecision != PendingDecision.NONE) {
            return handlePendingDecisionClick(
                    mouseX,
                    mouseY,
                    button
            );
        }
        if (pendingRequestId >= 0L) {
            return true;
        }

        if (button != 0) {
            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        if (handleModeSelectorClick(
                mouseX,
                mouseY
        )) {
            return true;
        }

        List<NexusCategoryView> categories =
                categories();

        if (mode != NexusMode.ASCENDANCE
                && !categories.isEmpty()) {
            stabilizeSelection(categories);

            if (handleTabClick(
                    mouseX,
                    mouseY,
                    categories
            )) {
                return true;
            }
        }

        if (mode == NexusMode.ASCENDANCE) {
            if (handleAscendClick(
                    mouseX,
                    mouseY
            )) {
                return true;
            }

            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        if (categories.isEmpty()) {
            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        NexusCategoryView category =
                categories.get(selectedCategoryIndex);

        ContentLayout layout =
                contentLayout();

        if (mode == NexusMode.SKILLS) {
            if (handleAllocateClick(mouseX, mouseY, layout)) {
                return true;
            }

            SkillDefinition clickedSkill = skillAt(
                    category,
                    mouseX,
                    mouseY,
                    layout
            );
            if (clickedSkill != null) {
                handleSkillClick(clickedSkill, category);
                return true;
            }

            SkillViewport viewport = skillViewport(layout);
            if (viewport.contains(mouseX, mouseY)) {
                panningSkills = true;
                lastSkillPanX = mouseX;
                lastSkillPanY = mouseY;
                return true;
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        int visibleCount =
                effectiveVisibleTrackCount(
                        category.tracks().size(),
                        layout
                );

        if (handleTrackPagingClick(
                mouseX,
                mouseY,
                category.tracks().size(),
                visibleCount,
                layout
        )) {
            return true;
        }

        if (handleAllocateClick(
                mouseX,
                mouseY,
                layout
        )) {
            return true;
        }

        int clickedTrack =
                trackAt(
                        mouseX,
                        mouseY,
                        category,
                        layout,
                        visibleCount
                );

        if (clickedTrack >= 0) {
            draggingTrackIndex = clickedTrack;
            updateStagedDrag(
                    mouseY,
                    category,
                    category.tracks().get(clickedTrack),
                    layout
            );
            return true;
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (keyCode == 256) {
            if (pendingDecision != PendingDecision.NONE) {
                if (pendingRequestId < 0L) {
                    pendingDecision = PendingDecision.NONE;
                    pendingCompletion = PendingCompletion.NONE;
                }
                return true;
            }
            onClose();
            return true;
        }

        if (pendingDecision != PendingDecision.NONE) {
            return true;
        }
        if (pendingRequestId >= 0L) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (pendingDecision != PendingDecision.NONE) {
            return true;
        }
        if (pendingRequestId >= 0L) {
            return true;
        }

        if (mode == NexusMode.SKILLS
                && button == 0
                && panningSkills) {
            List<NexusCategoryView> categories = categories();
            if (!categories.isEmpty()) {
                stabilizeSelection(categories);
                ResourceLocation essenceId = categories
                        .get(selectedCategoryIndex)
                        .essence()
                        .id();
                skillScrollX.put(
                        essenceId,
                        skillScrollX.getOrDefault(essenceId, 0.0)
                                - (mouseX - lastSkillPanX)
                );
                skillScrollY.put(
                        essenceId,
                        skillScrollY.getOrDefault(essenceId, 0.0)
                                - (mouseY - lastSkillPanY)
                );
                lastSkillPanX = mouseX;
                lastSkillPanY = mouseY;
                return true;
            }
        }

        if (mode == NexusMode.BONUSES
                && button == 0
                && draggingTrackIndex >= 0) {
            List<NexusCategoryView> categories =
                    categories();

            if (!categories.isEmpty()) {
                stabilizeSelection(categories);
                NexusCategoryView category =
                        categories.get(selectedCategoryIndex);

                if (draggingTrackIndex < category.tracks().size()) {
                    updateStagedDrag(
                            mouseY,
                            category,
                            category.tracks().get(draggingTrackIndex),
                            contentLayout()
                    );
                    return true;
                }
            }
        }

        return super.mouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (pendingDecision != PendingDecision.NONE) {
            return true;
        }
        if (pendingRequestId >= 0L) {
            return true;
        }

        if (button == 0 && panningSkills) {
            panningSkills = false;
            return true;
        }

        if (button == 0
                && draggingTrackIndex >= 0) {
            /*
             * Released sliders keep their staged target. The complete staged
             * reallocation is sent only when ALLOCATE is pressed.
             */
            draggingTrackIndex = -1;
            return true;
        }

        return super.mouseReleased(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollXAmount,
            double scrollYAmount
    ) {
        if (pendingDecision != PendingDecision.NONE) {
            return true;
        }
        if (pendingRequestId >= 0L) {
            return true;
        }
        if (mode != NexusMode.SKILLS) {
            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    scrollXAmount,
                    scrollYAmount
            );
        }

        List<NexusCategoryView> categories = categories();
        if (categories.isEmpty()) {
            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    scrollXAmount,
                    scrollYAmount
            );
        }
        stabilizeSelection(categories);
        NexusCategoryView category = categories.get(selectedCategoryIndex);
        SkillViewport viewport = skillViewport(contentLayout());
        if (!viewport.contains(mouseX, mouseY)) {
            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    scrollXAmount,
                    scrollYAmount
            );
        }

        ResourceLocation essenceId = category.essence().id();
        NexusSkillTreeLayout.Layout tree = skillLayout(essenceId);
        SkillScroll scroll = clampedSkillScroll(essenceId, tree, viewport);
        double horizontal = scrollXAmount;
        double vertical = scrollYAmount;
        if (hasShiftDown()) {
            horizontal += vertical;
            vertical = 0.0;
        } else if (scroll.maximumY() <= 0.0 && scroll.maximumX() > 0.0) {
            horizontal += vertical;
            vertical = 0.0;
        }

        skillScrollX.put(
                essenceId,
                scroll.x() - horizontal * SKILL_SCROLL_STEP
        );
        skillScrollY.put(
                essenceId,
                scroll.y() - vertical * SKILL_SCROLL_STEP
        );
        clampedSkillScroll(essenceId, tree, viewport);
        return true;
    }

    private boolean handleModeSelectorClick(
            double mouseX,
            double mouseY
    ) {
        int selectorWidth = Math.min(
                MODE_SELECTOR_WIDTH,
                Math.max(3, width - 2 * SAFE_MARGIN)
        );
        int left = (width - selectorWidth) / 2;

        if (!inside(
                mouseX,
                mouseY,
                left,
                MODE_SELECTOR_Y,
                selectorWidth,
                MODE_SELECTOR_HEIGHT
        )) {
            return false;
        }

        int relativeX = Math.max(0, (int) mouseX - left);
        int selected = Math.min(
                NexusMode.values().length - 1,
                relativeX * NexusMode.values().length / Math.max(1, selectorWidth)
        );
        mode = NexusMode.values()[selected];
        draggingTrackIndex = -1;
        panningSkills = false;
        return true;
    }

    private SkillDefinition skillAt(
            NexusCategoryView category,
            double mouseX,
            double mouseY,
            ContentLayout layout
    ) {
        SkillViewport viewport = skillViewport(layout);
        if (!viewport.contains(mouseX, mouseY)) {
            return null;
        }

        NexusSkillTreeLayout.Layout tree = skillLayout(category.essence().id());
        SkillScroll scroll = clampedSkillScroll(
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

        if (!evaluation.projectedOwned()) {
            long cost = skillCost(skill);
            long available = stagedAvailableEssence(category);
            if (!evaluation.eligibleToPurchase() || cost > available) {
                return;
            }

            draft.stagePurchase(skill.id(), skill.essenceId(), cost);
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
        Set<ResourceLocation> projectedOwned = new java.util.LinkedHashSet<>(
                snapshot.ownedSkills().keySet()
        );
        projectedOwned.addAll(draft.stagedPurchaseIds());
        return SkillStateEvaluator.activationPlan(
                skill.id(),
                projectedOwned,
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
                for (ResourceLocation prerequisiteId : staged.prerequisites()) {
                    if (removed.contains(prerequisiteId)
                            && !snapshot.ownedSkills().containsKey(prerequisiteId)) {
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

    private boolean handleAscendClick(
            double mouseX,
            double mouseY
    ) {
        ContentLayout layout =
                contentLayout();
        int buttonY =
                layout.bottom()
                        - BOTTOM_CONTROL_HEIGHT
                        - 3;
        int buttonX =
                (layout.left() + layout.right() - ASCEND_BUTTON_WIDTH) / 2;

        if (!inside(
                mouseX,
                mouseY,
                buttonX,
                buttonY,
                ASCEND_BUTTON_WIDTH,
                BOTTOM_CONTROL_HEIGHT
        )) {
            return false;
        }

        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();
        ClientEssenceState.ProgressSnapshot progress =
                snapshot.progress();

        if (!snapshot.ready() || pendingRequestId >= 0L) {
            return true;
        }

        if (hasStagedChanges()) {
            openAscendDecision();
            return true;
        }

        if (progress.status()
                != PlayerEssenceSyncPayload.ProgressStatus.AVAILABLE
                || !projectedAscension(progress).ready(progress)) {
            return true;
        }

        submitDraft(true, PendingCompletion.NONE);

        return true;
    }

    private boolean handleTabClick(
            double mouseX,
            double mouseY,
            List<NexusCategoryView> categories
    ) {
        TabLayout layout =
                tabLayout(categories);

        if (!inside(
                mouseX,
                mouseY,
                layout.leftArrowX(),
                layout.y(),
                TAB_ARROW_WIDTH,
                TAB_HEIGHT
        )) {
            if (inside(
                    mouseX,
                    mouseY,
                    layout.rightArrowX(),
                    layout.y(),
                    TAB_ARROW_WIDTH,
                    TAB_HEIGHT
            )) {
                if (tabWindowStart + layout.visibleCount()
                        < categories.size()) {
                    tabWindowStart++;
                }
                return true;
            }
        } else {
            if (tabWindowStart > 0) {
                tabWindowStart--;
            }
            return true;
        }

        int x = layout.tabsLeft();
        int end = Math.min(
                categories.size(),
                tabWindowStart + layout.visibleCount()
        );

        for (int i = tabWindowStart; i < end; i++) {
            if (inside(
                    mouseX,
                    mouseY,
                    x,
                    layout.y(),
                    layout.tabWidth(),
                    TAB_HEIGHT
            )) {
                selectedCategoryIndex = i;
                selectedEssenceId =
                        categories.get(i).essence().id();
                trackWindowStart = 0;
                draggingTrackIndex = -1;
                return true;
            }

            x += layout.tabWidth() + TAB_GAP;
        }

        return false;
    }

    private boolean handleTrackPagingClick(
            double mouseX,
            double mouseY,
            int trackCount,
            int visibleCount,
            ContentLayout layout
    ) {
        BottomControls controls =
                bottomControls(layout);

        if (inside(
                mouseX,
                mouseY,
                controls.leftArrowX(),
                controls.y(),
                TRACK_ARROW_WIDTH,
                BOTTOM_CONTROL_HEIGHT
        )) {
            if (trackWindowStart > 0) {
                trackWindowStart =
                        Math.max(
                                0,
                                trackWindowStart - visibleCount
                        );
            }
            return true;
        }

        if (inside(
                mouseX,
                mouseY,
                controls.rightArrowX(),
                controls.y(),
                TRACK_ARROW_WIDTH,
                BOTTOM_CONTROL_HEIGHT
        )) {
            if (trackCount > visibleCount) {
                int maximumStart =
                        Math.max(0, trackCount - visibleCount);
                trackWindowStart =
                        Math.min(
                                maximumStart,
                                trackWindowStart + visibleCount
                        );
            }
            return true;
        }

        return false;
    }

    private boolean handleAllocateClick(
            double mouseX,
            double mouseY,
            ContentLayout layout
    ) {
        BottomControls controls =
                bottomControls(layout);

        if (!inside(
                mouseX,
                mouseY,
                controls.allocateX(),
                controls.y(),
                ALLOCATE_BUTTON_WIDTH,
                BOTTOM_CONTROL_HEIGHT
        )) {
            return false;
        }

        if (!primaryActionEnabled()) {
            return true;
        }

        if (draft.invalidated()) {
            draft.clearAndCapture(ClientEssenceState.snapshot());
            transactionFeedback = null;
            return true;
        }

        submitDraft(false, PendingCompletion.NONE);

        return true;
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

        List<String> purchases = draft.stagedPurchaseIds()
                .stream()
                .map(ResourceLocation::toString)
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
                if (result.ascended()) {
                    mode = NexusMode.ASCENDANCE;
                }
            });
        }

        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        if (acceptedRevision < 0L
                || !snapshot.ready()
                || snapshot.nexusRevision() < acceptedRevision) {
            return;
        }

        PendingCompletion completion = pendingCompletion;
        draft.clearAndCapture(snapshot);
        pendingRequestId = -1L;
        acceptedRevision = -1L;
        pendingCompletion = PendingCompletion.NONE;
        pendingDecision = PendingDecision.NONE;
        transactionFeedback = null;

        if (completion == PendingCompletion.EXIT) {
            super.onClose();
        }
    }

    private int trackAt(
            double mouseX,
            double mouseY,
            NexusCategoryView category,
            ContentLayout layout,
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
            ContentLayout layout
    ) {
        long cap =
                Math.max(
                        0L,
                        track.state().currentInvestmentCap()
                );
        double ceiling =
                currentTierCeiling();

        if (ceiling <= 0.0
                || cap <= 0L) {
            transactionFeedback = null;
            draft.stageBonus(
                    track.stat().id(),
                    0L
            );
            return;
        }

        int height =
                Math.max(1, layout.trackBottom() - layout.trackTop());
        double requestedProgress =
                Math.max(
                        0.0,
                        Math.min(
                                ceiling,
                                (layout.trackBottom() - mouseY) / height
                        )
                );

        long requestedTarget =
                stagedInvestmentForProgression(
                        track,
                        requestedProgress
                );
        requestedTarget =
                Math.max(
                        0L,
                        Math.min(
                                cap,
                                requestedTarget
                        )
                );

        long currentStaged =
                stagedInvestment(track);

        if (requestedTarget > currentStaged) {
            long affordableTarget =
                    safeAddNonNegative(
                            currentStaged,
                            stagedAvailableEssence(category)
                    );
            requestedTarget =
                    Math.min(
                            requestedTarget,
                            Math.min(cap, affordableTarget)
                    );
        }

        transactionFeedback = null;
        draft.stageBonus(
                track.stat().id(),
                requestedTarget
        );
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
        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();
        AscendanceTierDefinition tier =
                currentTier(snapshot);
        BalanceProfileDefinition profile =
                currentBalanceProfile(snapshot);

        if (tier == null
                || profile == null) {
            return track.state().progression();
        }

        long effectiveTarget =
                Math.min(
                        Math.max(0L, stagedTarget),
                        Math.max(0L, track.state().currentInvestmentCap())
                );

        return StatScalingService.progressionForInvestment(
                track.stat(),
                effectiveTarget,
                tier,
                profile
        );
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

    private long stagedInvestmentForProgression(
            NexusProgressionTrack track,
            double progression
    ) {
        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();
        AscendanceTierDefinition tier =
                currentTier(snapshot);
        BalanceProfileDefinition profile =
                currentBalanceProfile(snapshot);

        if (tier == null
                || profile == null) {
            double ceiling =
                    Math.max(0.000001, currentTierCeiling());
            return Math.round(
                    track.state().currentInvestmentCap()
                            * clamp01(progression / ceiling)
            );
        }

        return StatScalingService.investmentForProgression(
                track.stat(),
                progression,
                tier,
                profile
        );
    }

    private AscendanceTierDefinition currentTier(
            ClientEssenceState.Snapshot snapshot
    ) {
        if (!snapshot.ready()
                || snapshot.tierId() == null) {
            return null;
        }

        for (AscendanceTierDefinition tier : orderedTiers()) {
            if (tier.id().equals(snapshot.tierId())) {
                return tier;
            }
        }

        return null;
    }

    private BalanceProfileDefinition currentBalanceProfile(
            ClientEssenceState.Snapshot snapshot
    ) {
        if (!snapshot.ready()
                || snapshot.balanceProfileId() == null) {
            return null;
        }

        return BalanceProfileRegistry
                .get(snapshot.balanceProfileId())
                .orElse(null);
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

    private void stabilizeSelection(
            List<NexusCategoryView> categories
    ) {
        if (categories.isEmpty()) {
            selectedCategoryIndex = 0;
            selectedEssenceId = null;
            return;
        }

        if (selectedEssenceId != null) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).essence().id()
                        .equals(selectedEssenceId)) {
                    selectedCategoryIndex = i;
                    break;
                }
            }
        }

        selectedCategoryIndex =
                Math.max(
                        0,
                        Math.min(
                                selectedCategoryIndex,
                                categories.size() - 1
                        )
                );
        selectedEssenceId =
                categories.get(selectedCategoryIndex).essence().id();

        TabLayout layout =
                tabLayout(categories);

        /*
         * Tab paging is independent of the active category. The selected tab is
         * allowed to scroll completely off-screen so the player can browse the
         * rest of a long category list without the active tab forcing the
         * window back into view every frame.
         */
        tabWindowStart =
                Math.max(
                        0,
                        Math.min(
                                tabWindowStart,
                                Math.max(0, categories.size() - layout.visibleCount())
                        )
                );
    }

    private TabLayout tabLayout(
            List<NexusCategoryView> categories
    ) {
        int y = 34;
        int leftArrowX = SAFE_MARGIN;
        int rightArrowX = width - SAFE_MARGIN - TAB_ARROW_WIDTH;
        int tabsLeft = leftArrowX + TAB_ARROW_WIDTH + TAB_GAP;
        int tabsRight = rightArrowX - TAB_GAP;
        int available =
                Math.max(1, tabsRight - tabsLeft);

        int desiredWidth = TAB_MIN_WIDTH;
        for (NexusCategoryView category : categories) {
            desiredWidth =
                    Math.max(
                            desiredWidth,
                            font.width(EssenceText.essenceShort(category.essence()).getString()) + 14
                    );
        }
        desiredWidth =
                Math.max(
                        1,
                        Math.min(
                                Math.min(TAB_MAX_WIDTH, desiredWidth),
                                available
                        )
                );

        int visibleCount =
                Math.max(
                        1,
                        (available + TAB_GAP)
                                / (desiredWidth + TAB_GAP)
                );
        visibleCount =
                Math.min(
                        visibleCount,
                        Math.max(1, categories.size())
                );

        int usedWidth =
                desiredWidth * visibleCount
                        + TAB_GAP * Math.max(0, visibleCount - 1);
        int centeredLeft =
                tabsLeft + Math.max(0, (available - usedWidth) / 2);

        return new TabLayout(
                y,
                leftArrowX,
                rightArrowX,
                centeredLeft,
                desiredWidth,
                visibleCount
        );
    }

    private ContentLayout contentLayout() {
        int left = SAFE_MARGIN;
        int right = Math.max(left + 1, width - SAFE_MARGIN);
        int top = mode == NexusMode.ASCENDANCE ? 34 : 58;
        int bottom = Math.max(top + 1, height - 18);

        int totalWidth = right - left;

        /*
         * Horizontal layout is intentionally step-driven around whole stat
         * columns. The middle section is exactly as wide as N preferred-width
         * tracks (plus their gaps). Any width that is not yet enough to add the
         * next complete track is split evenly between the left and right
         * sections. Once another complete track fits, that space is reclaimed by
         * the middle in one column-sized step.
         *
         * This keeps stat widths stable, prevents tier guides from extending
         * through a useless partial-column area, and guarantees symmetric side
         * sections whose contents can simply stay centered.
         */
        int centerBudget =
                Math.max(
                        1,
                        totalWidth - 2 * MIN_SIDE_SECTION_WIDTH
                );

        int wholeTrackCount;
        int centerWidth;

        if (centerBudget < TRACK_PREFERRED_WIDTH) {
            wholeTrackCount = 1;
            centerWidth = centerBudget;
        } else {
            wholeTrackCount =
                    Math.max(
                            1,
                            (centerBudget + TRACK_GAP)
                                    / (TRACK_PREFERRED_WIDTH + TRACK_GAP)
                    );
            centerWidth =
                    TRACK_PREFERRED_WIDTH * wholeTrackCount
                            + TRACK_GAP * Math.max(0, wholeTrackCount - 1);
        }

        centerWidth =
                Math.max(
                        1,
                        Math.min(centerBudget, centerWidth)
                );

        int sideSectionWidth =
                Math.max(
                        0,
                        (totalWidth - centerWidth) / 2
                );

        /*
         * Preserve the established 42 px reservoir whenever the side section has
         * enough room. Only genuinely tiny windows are allowed to compress it.
         */
        int gaugeWidth;
        if (sideSectionWidth >= 20) {
            gaugeWidth =
                    Math.min(
                            42,
                            Math.max(
                                    20,
                                    sideSectionWidth - 12
                            )
                    );
        } else {
            gaugeWidth =
                    Math.max(
                            1,
                            sideSectionWidth
                    );
        }

        int gaugeLeft =
                left + (sideSectionWidth - gaugeWidth) / 2;
        int gaugeRight = gaugeLeft + gaugeWidth;
        int tracksLeft = left + sideSectionWidth;
        int tracksRight = Math.max(
                tracksLeft + 1,
                right - sideSectionWidth
        );

        /*
         * The panel is also split vertically into three explicit bands:
         *
         *   category title | slider content | paging/allocation controls
         *
         * The top and bottom bands are always the same height. Everything that
         * belongs to a stat track is constrained to the middle band.
         */
        int desiredSectionHeight =
                height < 230 ? NARROW_SECTION_HEIGHT : SECTION_HEIGHT;
        int maximumSectionHeight =
                Math.max(
                        18,
                        (bottom - top - 72) / 2
                );
        int sectionHeight =
                Math.max(
                        18,
                        Math.min(desiredSectionHeight, maximumSectionHeight)
                );

        int middleTop = top + sectionHeight;
        int middleBottom = Math.max(
                middleTop + 1,
                bottom - sectionHeight
        );

        int trackHeaderReserve = height < 230 ? 32 : 38;
        int trackInfoReserve = height < 230 ? 29 : 33;

        int trackTop = Math.min(
                middleBottom - 1,
                middleTop + trackHeaderReserve
        );
        int trackBottom = Math.max(
                trackTop + 1,
                middleBottom - trackInfoReserve
        );
        trackBottom = Math.min(
                middleBottom - 1,
                trackBottom
        );

        return new ContentLayout(
                left,
                top,
                right,
                bottom,
                gaugeLeft,
                gaugeRight,
                tracksLeft,
                tracksRight,
                sectionHeight,
                middleTop,
                middleBottom,
                trackTop,
                trackBottom
        );
    }

    private int visibleTrackCount(
            ContentLayout layout
    ) {
        int available =
                Math.max(
                        1,
                        layout.tracksRight() - layout.tracksLeft()
                );

        /*
         * contentLayout() already quantizes the center section to whole preferred
         * columns. Deriving the visible count from that viewport therefore stays
         * stable until the panel is actually wide enough for one more complete
         * stat column.
         */
        return Math.max(
                1,
                (available + TRACK_GAP)
                        / (TRACK_PREFERRED_WIDTH + TRACK_GAP)
        );
    }

    private int effectiveVisibleTrackCount(
            int trackCount,
            ContentLayout layout
    ) {
        if (trackCount <= 0) {
            return 1;
        }

        return Math.max(
                1,
                Math.min(
                        trackCount,
                        visibleTrackCount(layout)
                )
        );
    }

    private int trackWidth(
            int viewportWidth,
            int visibleCount
    ) {
        int count = Math.max(1, visibleCount);
        int usable =
                Math.max(
                        1,
                        viewportWidth
                                - TRACK_GAP * Math.max(0, count - 1)
                );

        /*
         * Normal layouts use one fixed preferred width. Only a genuinely tiny
         * viewport is allowed to compress a single track so it stays on-screen.
         */
        return Math.max(
                1,
                Math.min(
                        TRACK_PREFERRED_WIDTH,
                        usable / count
                )
        );
    }

    private int trackContentLeft(
            ContentLayout layout,
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

    private void clampTrackWindow(
            int trackCount,
            int visibleCount
    ) {
        trackWindowStart =
                Math.max(
                        0,
                        Math.min(
                                trackWindowStart,
                                Math.max(0, trackCount - visibleCount)
                        )
                );
    }

    private double currentTierCeiling() {
        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();
        List<AscendanceTierDefinition> tiers =
                orderedTiers();

        if (!snapshot.ready()
                || snapshot.tierId() == null
                || tiers.isEmpty()) {
            return 1.0;
        }

        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).id().equals(snapshot.tierId())) {
                return clamp01(
                        (i + 1.0) / tiers.size()
                );
            }
        }

        return 1.0;
    }

    private List<AscendanceTierDefinition> orderedTiers() {
        List<AscendanceTierDefinition> tiers =
                new ArrayList<>(AscendanceTierRegistry.values());
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

    private void openAscendDecision() {
        pendingDecision = PendingDecision.ASCEND;
        pendingCompletion = PendingCompletion.NONE;
        transactionFeedback = null;
    }

    private void renderSkillTooltip(
            GuiGraphics graphics,
            ResourceLocation skillId,
            int mouseX,
            int mouseY
    ) {
        SkillDefinition skill = SkillRegistry.get(skillId).orElse(null);
        SkillEvaluationResult evaluation = skillEvaluations().get(skillId);
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

        List<Component> components = new ArrayList<>();
        components.add(Component.translatable(skill.nameTranslationKey()));
        components.add(Component.translatable(skill.descriptionTranslationKey()));
        components.add(Component.empty());
        components.add(
                EssenceText.gui(
                        "nexus.skills.tooltip.cost",
                        formatLong(cost),
                        EssenceText.essenceShort(category.essence())
                )
        );
        components.add(
                EssenceText.gui(
                        "nexus.skills.tooltip.required_tier",
                        tierNameComponent(skill.requiredTierId())
                )
        );
        components.add(
                EssenceText.gui(
                        "nexus.skills.tooltip.activation",
                        EssenceText.gui(
                                "nexus.skills.activation."
                                        + skill.activationPolicy().name().toLowerCase(Locale.ROOT)
                        )
                )
        );
        components.add(
                EssenceText.gui(
                        "nexus.skills.tooltip.status",
                        EssenceText.gui(
                                "nexus.skills.state."
                                        + visual.name().toLowerCase(Locale.ROOT)
                        )
                )
        );
        components.add(EssenceText.gui("nexus.skills.tooltip.state_details"));
        components.add(
                EssenceText.gui(
                        "nexus.skills.tooltip.owned_detail",
                        skillBooleanStatus(evaluation.owned()),
                        skillBooleanStatus(evaluation.projectedOwned())
                )
        );
        components.add(
                EssenceText.gui(
                        "nexus.skills.tooltip.selected_detail",
                        skillSelectionStatus(skill, evaluation.selected()),
                        skillSelectionStatus(skill, evaluation.projectedSelected())
                )
        );
        components.add(
                EssenceText.gui(
                        "nexus.skills.tooltip.effective_detail",
                        skillBooleanStatus(evaluation.effective()),
                        skillBooleanStatus(evaluation.projectedEffective())
                )
        );
        components.add(
                EssenceText.gui(
                        "nexus.skills.tooltip.suspended_detail",
                        skillBooleanStatus(evaluation.suspended()),
                        skillBooleanStatus(evaluation.projectedSuspended())
                )
        );

        if (!evaluation.prerequisites().isEmpty()) {
            components.add(EssenceText.gui("nexus.skills.tooltip.prerequisites"));
            for (com.mistaboom.essence_ascendance.skill.SkillPrerequisiteStatus status :
                    evaluation.prerequisites()) {
                String statusPath = status.authoritativeOwned()
                        ? "nexus.skills.tooltip.prerequisite_met"
                        : status.projectedOwned()
                        ? "nexus.skills.tooltip.prerequisite_staged"
                        : "nexus.skills.tooltip.prerequisite_missing";
                components.add(
                        EssenceText.gui(
                                statusPath,
                                skillName(status.skillId())
                        )
                );
            }
        }

        if (!evaluation.requirements().isEmpty()) {
            components.add(EssenceText.gui("nexus.skills.tooltip.requirements"));
            for (com.mistaboom.essence_ascendance.skill.SkillRequirementStatus status :
                    evaluation.requirements()) {
                String statusPath = status.authoritativeSatisfied()
                        ? "nexus.skills.tooltip.requirement_met"
                        : status.projectedSatisfied()
                        ? "nexus.skills.tooltip.requirement_projected"
                        : "nexus.skills.tooltip.requirement_missing";
                Component requirement = requirementDescription(skill, status.requirementId());
                components.add(EssenceText.gui(statusPath, requirement));
            }
        }

        skill.choiceGroupId().ifPresent(groupId -> {
            SkillChoiceGroup group = SkillRegistry.choiceGroup(groupId).orElse(null);
            Component groupName = group == null
                    ? Component.literal(groupId.getPath())
                    : Component.translatable(group.translationKey());
            components.add(
                    EssenceText.gui("nexus.skills.tooltip.choice_group", groupName)
            );
        });
        skill.replacementTargetId().ifPresent(
                targetId -> components.add(
                        EssenceText.gui(
                                "nexus.skills.tooltip.replaces",
                                skillName(targetId)
                        )
                )
        );
        if (!evaluation.projectedReplacedBy().isEmpty()) {
            for (ResourceLocation replacementId : evaluation.projectedReplacedBy()) {
                components.add(
                        EssenceText.gui(
                                "nexus.skills.tooltip.replaced_by",
                                skillName(replacementId)
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
        if (clickHint != null) {
            components.add(Component.empty());
            components.add(clickHint);
        }

        int maximumWidth = Math.min(320, Math.max(120, width - 40));
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component component : components) {
            if (component.getString().isEmpty()) {
                lines.add(Component.empty().getVisualOrderText());
            } else {
                lines.addAll(font.split(component, maximumWidth));
            }
        }
        graphics.renderTooltip(font, lines, mouseX, mouseY);
    }

    private Component skillBooleanStatus(boolean value) {
        return EssenceText.gui(
                value
                        ? "nexus.skills.tooltip.value.yes"
                        : "nexus.skills.tooltip.value.no"
        );
    }

    private Component skillSelectionStatus(
            SkillDefinition skill,
            boolean selected
    ) {
        if (skill.activationPolicy() == SkillActivationPolicy.AUTOMATIC) {
            return EssenceText.gui("nexus.skills.tooltip.value.not_applicable");
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

    private Component requirementDescription(
            SkillDefinition skill,
            ResourceLocation requirementId
    ) {
        for (SkillRequirement requirement : skill.requirements()) {
            if (!requirement.id().equals(requirementId)) {
                continue;
            }
            if (requirement instanceof BonusInvestmentRequirement bonus) {
                NexusCategoryView category = categoryForEssence(bonus.essenceId());
                Component essence = category == null
                        ? Component.literal(bonus.essenceId().getPath())
                        : EssenceText.essenceShort(category.essence());
                long projectedInvestment = draft.projectedBonusInvestment(
                        bonus.essenceId(),
                        ClientEssenceState.snapshot(),
                        statEssenceIds()
                );
                return Component.translatable(
                        requirement.translationKey(),
                        formatLong(projectedInvestment),
                        formatLong(bonus.minimumInvestment()),
                        essence
                );
            }
            return Component.translatable(requirement.translationKey());
        }
        return Component.literal(requirementId.getPath());
    }

    private Component skillName(ResourceLocation skillId) {
        return SkillRegistry.get(skillId)
                .map(skill -> (Component) Component.translatable(skill.nameTranslationKey()))
                .orElseGet(() -> Component.literal(skillId.getPath()));
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
        graphics.fill(0, 0, width, height, 0xB0000000);

        ModalLayout layout = modalLayout();
        graphics.fill(
                layout.left(),
                layout.top(),
                layout.right(),
                layout.bottom(),
                0xFF1C1B25
        );
        outline(
                graphics,
                layout.left(),
                layout.top(),
                layout.width(),
                layout.height(),
                BORDER_BRIGHT
        );

        String title = EssenceText.gui(
                pendingDecision == PendingDecision.EXIT
                        ? "nexus.modal.exit_title"
                        : "nexus.modal.ascend_title"
        ).getString();
        graphics.drawCenteredString(
                font,
                trimToWidth(title, layout.width() - 16),
                width / 2,
                layout.top() + 10,
                TEXT
        );

        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();
        String summary = EssenceText.gui(
                "nexus.modal.pending_summary",
                draft.bonusChangeCount(snapshot),
                draft.purchaseCount(),
                draft.loadoutChangeCount(snapshot)
        ).getString();
        graphics.drawCenteredString(
                font,
                trimToWidth(summary, layout.width() - 20),
                width / 2,
                layout.top() + 28,
                MUTED
        );

        if (transactionFeedback != null) {
            graphics.drawCenteredString(
                    font,
                    trimToWidth(
                            transactionFeedback.getString(),
                            layout.width() - 20
                    ),
                    width / 2,
                    layout.top() + 43,
                    ERROR
            );
        } else if (pendingRequestId >= 0L) {
            graphics.drawCenteredString(
                    font,
                    EssenceText.gui("nexus.transaction.waiting").getString(),
                    width / 2,
                    layout.top() + 43,
                    INCOMPLETE
            );
        }

        boolean enabled = pendingRequestId < 0L && !draft.invalidated();
        ModalButtons buttons = modalButtons(layout);
        String applyPath = pendingDecision == PendingDecision.EXIT
                ? "nexus.modal.apply_exit"
                : "nexus.modal.apply_ascend";
        String discardPath = pendingDecision == PendingDecision.EXIT
                ? "nexus.modal.discard_exit"
                : "nexus.modal.discard_ascend";

        renderModalButton(
                graphics,
                buttons.apply(),
                EssenceText.gui(applyPath).getString(),
                enabled,
                buttons.apply().contains(mouseX, mouseY)
        );
        renderModalButton(
                graphics,
                buttons.discard(),
                EssenceText.gui(discardPath).getString(),
                pendingRequestId < 0L,
                buttons.discard().contains(mouseX, mouseY)
        );
        renderModalButton(
                graphics,
                buttons.goBack(),
                EssenceText.gui("nexus.modal.go_back").getString(),
                pendingRequestId < 0L,
                buttons.goBack().contains(mouseX, mouseY)
        );
    }

    private void renderModalButton(
            GuiGraphics graphics,
            Rect rect,
            String label,
            boolean enabled,
            boolean hovered
    ) {
        graphics.fill(
                rect.left(),
                rect.top(),
                rect.right(),
                rect.bottom(),
                enabled
                        ? hovered ? 0xFF41364F : 0xFF292733
                        : 0xFF201E26
        );
        outline(
                graphics,
                rect.left(),
                rect.top(),
                rect.width(),
                rect.height(),
                enabled
                        ? hovered ? 0xFFD0ADEB : BORDER_BRIGHT
                        : 0xFF4A4650
        );
        graphics.drawCenteredString(
                font,
                trimToWidth(label, rect.width() - 6),
                (rect.left() + rect.right()) / 2,
                rect.top() + Math.max(2, (rect.height() - font.lineHeight) / 2),
                enabled ? TEXT : DIM
        );
    }

    private boolean handlePendingDecisionClick(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button != 0 || pendingRequestId >= 0L) {
            return true;
        }

        ModalButtons buttons = modalButtons(modalLayout());
        ClientEssenceState.Snapshot snapshot = ClientEssenceState.snapshot();

        if (buttons.apply().contains(mouseX, mouseY)) {
            if (!draft.invalidated()) {
                submitDraft(
                        pendingDecision == PendingDecision.ASCEND,
                        pendingDecision == PendingDecision.EXIT
                                ? PendingCompletion.EXIT
                                : PendingCompletion.NONE
                );
            }
            return true;
        }

        if (buttons.discard().contains(mouseX, mouseY)) {
            PendingDecision decision = pendingDecision;
            draft.clearAndCapture(snapshot);
            transactionFeedback = null;
            if (decision == PendingDecision.EXIT) {
                pendingDecision = PendingDecision.NONE;
                super.onClose();
            } else {
                submitDraft(true, PendingCompletion.NONE);
            }
            return true;
        }

        if (buttons.goBack().contains(mouseX, mouseY)) {
            pendingDecision = PendingDecision.NONE;
            pendingCompletion = PendingCompletion.NONE;
            transactionFeedback = null;
            return true;
        }

        return true;
    }

    private boolean canPromptForExit() {
        return minecraft != null
                && minecraft.getConnection() != null
                && minecraft.player != null
                && minecraft.player.containerMenu == menu;
    }

    private ModalLayout modalLayout() {
        int modalWidth = Math.min(344, Math.max(210, width - 28));
        int modalHeight = 94;
        int left = (width - modalWidth) / 2;
        int top = Math.max(8, (height - modalHeight) / 2);
        return new ModalLayout(left, top, left + modalWidth, top + modalHeight);
    }

    private ModalButtons modalButtons(ModalLayout modal) {
        int gap = 5;
        int available = modal.width() - 16 - gap * 2;
        int buttonWidth = Math.max(50, available / 3);
        int used = buttonWidth * 3 + gap * 2;
        int left = modal.left() + (modal.width() - used) / 2;
        int top = modal.bottom() - BOTTOM_CONTROL_HEIGHT - 9;
        return new ModalButtons(
                new Rect(left, top, left + buttonWidth, top + BOTTOM_CONTROL_HEIGHT),
                new Rect(
                        left + buttonWidth + gap,
                        top,
                        left + buttonWidth * 2 + gap,
                        top + BOTTOM_CONTROL_HEIGHT
                ),
                new Rect(
                        left + buttonWidth * 2 + gap * 2,
                        top,
                        left + buttonWidth * 3 + gap * 2,
                        top + BOTTOM_CONTROL_HEIGHT
                )
        );
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
        } else if (mode == NexusMode.ASCENDANCE) {
            lines.add(ascensionActionStatus(snapshot));
        } else if (!draft.hasChanges(snapshot)) {
            lines.add(EssenceText.gui("nexus.action.no_changes"));
        }

        if (draft.hasChanges(snapshot)) {
            lines.add(
                    EssenceText.gui(
                            "nexus.action.pending_counts",
                            draft.bonusChangeCount(snapshot),
                            draft.purchaseCount(),
                            draft.loadoutChangeCount(snapshot)
                    )
            );
            Map<ResourceLocation, ResourceLocation> statEssences = statEssenceIds();
            for (NexusCategoryView category : categories()) {
                ResourceLocation essenceId = category.essence().id();
                long projected = draft.projectedAvailable(
                        essenceId,
                        snapshot,
                        statEssences
                );
                lines.add(
                        EssenceText.gui(
                                "nexus.action.pending_category",
                                EssenceText.essenceShort(category.essence()),
                                bonusChangeCountForEssence(
                                        essenceId,
                                        snapshot,
                                        statEssences
                                ),
                                purchaseCountForEssence(essenceId),
                                loadoutChangeCountForEssence(
                                        essenceId,
                                        snapshot
                                )
                        )
                );
                lines.add(
                        EssenceText.gui(
                                "nexus.action.projected_balance",
                                EssenceText.essenceShort(category.essence()),
                                formatLong(projected)
                        )
                );
            }
        }

        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private int bonusChangeCountForEssence(
            ResourceLocation essenceId,
            ClientEssenceState.Snapshot snapshot,
            Map<ResourceLocation, ResourceLocation> statEssences
    ) {
        int count = 0;
        for (ResourceLocation statId : draft.changedBonusIds(snapshot)) {
            if (essenceId.equals(statEssences.get(statId))) {
                count++;
            }
        }
        return count;
    }

    private int purchaseCountForEssence(ResourceLocation essenceId) {
        int count = 0;
        for (NexusDraft.SkillPurchaseDraft purchase : draft.stagedPurchases()) {
            if (essenceId.equals(purchase.essenceId())) {
                count++;
            }
        }
        return count;
    }

    private int loadoutChangeCountForEssence(
            ResourceLocation essenceId,
            ClientEssenceState.Snapshot snapshot
    ) {
        Map<ResourceLocation, ResourceLocation> projected =
                draft.finalLoadouts(snapshot);
        Set<ResourceLocation> slots = new java.util.LinkedHashSet<>();
        slots.addAll(snapshot.loadoutSelections().keySet());
        slots.addAll(projected.keySet());

        int count = 0;
        for (ResourceLocation slotId : slots) {
            ResourceLocation committedSkill =
                    snapshot.loadoutSelections().get(slotId);
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
            if (skill != null && essenceId.equals(skill.essenceId())) {
                count++;
            }
        }
        return count;
    }

    private Component ascensionActionStatus(
            ClientEssenceState.Snapshot snapshot
    ) {
        ClientEssenceState.ProgressSnapshot progress = snapshot.progress();
        return switch (progress.status()) {
            case AVAILABLE -> projectedAscension(progress).ready(progress)
                    ? EssenceText.gui("nexus.ready_to_ascend")
                    : EssenceText.gui("nexus.requirements_incomplete");
            case MAX_TIER -> EssenceText.gui("nexus.maximum_achieved");
            case CONFIGURATION_ERROR ->
                    EssenceText.gui("nexus.ascension_config_error");
        };
    }

    private Rect primaryActionRect() {
        ContentLayout layout = contentLayout();
        if (mode == NexusMode.ASCENDANCE) {
            int x = (layout.left() + layout.right() - ASCEND_BUTTON_WIDTH) / 2;
            int y = layout.bottom() - BOTTOM_CONTROL_HEIGHT - 3;
            return new Rect(
                    x,
                    y,
                    x + ASCEND_BUTTON_WIDTH,
                    y + BOTTOM_CONTROL_HEIGHT
            );
        }

        BottomControls controls = bottomControls(layout);
        return new Rect(
                controls.allocateX(),
                controls.y(),
                controls.allocateX() + ALLOCATE_BUTTON_WIDTH,
                controls.y() + BOTTOM_CONTROL_HEIGHT
        );
    }

    private void renderArrow(
            GuiGraphics graphics,
            int x,
            int y,
            int arrowWidth,
            int arrowHeight,
            String glyph,
            boolean enabled
    ) {
        graphics.fill(
                x,
                y,
                x + arrowWidth,
                y + arrowHeight,
                enabled ? PANEL_INNER : 0x88201E26
        );
        outline(
                graphics,
                x,
                y,
                arrowWidth,
                arrowHeight,
                enabled ? BORDER : 0xFF4A4650
        );
        graphics.drawCenteredString(
                font,
                glyph,
                x + arrowWidth / 2,
                y + Math.max(2, (arrowHeight - font.lineHeight) / 2),
                enabled ? TEXT : DIM
        );
    }

    private void outline(
            GuiGraphics graphics,
            int x,
            int y,
            int outlineWidth,
            int outlineHeight,
            int color
    ) {
        if (outlineWidth <= 0
                || outlineHeight <= 0) {
            return;
        }

        graphics.fill(x, y, x + outlineWidth, y + 1, color);
        graphics.fill(x, y + outlineHeight - 1, x + outlineWidth, y + outlineHeight, color);
        graphics.fill(x, y, x + 1, y + outlineHeight, color);
        graphics.fill(x + outlineWidth - 1, y, x + outlineWidth, y + outlineHeight, color);
    }

    private boolean inside(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int areaWidth,
            int areaHeight
    ) {
        return mouseX >= x
                && mouseX < x + areaWidth
                && mouseY >= y
                && mouseY < y + areaHeight;
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

    private record TabLayout(
            int y,
            int leftArrowX,
            int rightArrowX,
            int tabsLeft,
            int tabWidth,
            int visibleCount
    ) {
    }

    private record BottomControls(
            int y,
            int leftArrowX,
            int allocateX,
            int rightArrowX
    ) {
    }

    private record ContentLayout(
            int left,
            int top,
            int right,
            int bottom,
            int gaugeLeft,
            int gaugeRight,
            int tracksLeft,
            int tracksRight,
            int sectionHeight,
            int middleTop,
            int middleBottom,
            int trackTop,
            int trackBottom
    ) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
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

    private record ModalLayout(
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
    }

    private record ModalButtons(
            Rect apply,
            Rect discard,
            Rect goBack
    ) {
    }

    private record ProjectedAscension(
            long effectiveInvestment,
            int developedStats,
            int representedCategories
    ) {
        boolean ready(ClientEssenceState.ProgressSnapshot progress) {
            return progress.status()
                    == PlayerEssenceSyncPayload.ProgressStatus.AVAILABLE
                    && effectiveInvestment >= progress.requiredInvestment()
                    && developedStats >= progress.requiredDevelopedStats()
                    && representedCategories >= progress.requiredRepresentedCategories()
                    && progress.worldProgressComplete();
        }
    }

    private record SkillViewport(
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

    private record SkillScroll(
            double x,
            double y,
            double maximumX,
            double maximumY
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
        REPLACED("↻", 0xCC262F3A, 0xFF7FA7C6, 0xFFD1E2EF);

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
        EXIT,
        ASCEND
    }

    private enum PendingCompletion {
        NONE,
        EXIT
    }
}
