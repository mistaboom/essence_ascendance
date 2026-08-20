package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.nexus.NexusCategoryView;
import com.mistaboom.essence_ascendance.client.nexus.NexusCategoryViewFactory;
import com.mistaboom.essence_ascendance.client.nexus.NexusProgressionTrack;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.BalanceProfileRegistry;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusMenu;
import com.mistaboom.essence_ascendance.network.AscendanceAllocationPayload;
import com.mistaboom.essence_ascendance.network.AscendanceAscendPayload;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.client.gui.GuiGraphics;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Fullscreen, data-driven progression shell for the Ascendance Nexus.
 *
 * Dragging an Attribute track stages a proposed reallocation locally, including
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

    private static final int SAFE_MARGIN = 14;
    private static final int ASCENDANCE_HEADER_WIDTH = 150;
    private static final int ASCENDANCE_HEADER_HEIGHT = 24;
    private static final int ASCENDANCE_HEADER_Y = 5;
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
    private static final int ALLOCATE_BUTTON_WIDTH = 70;
    private static final int ASCEND_BUTTON_WIDTH = 84;
    private static final int BOTTOM_CONTROL_HEIGHT = 16;
    private static final int BOTTOM_CONTROL_GAP = 5;
    private static final int GAUGE_TEXT_GAP = 4;
    private static final int SECTION_HEIGHT = 34;
    private static final int NARROW_SECTION_HEIGHT = 30;

    private int selectedCategoryIndex = 0;
    private int tabWindowStart = 0;
    private int trackWindowStart = 0;

    private int draggingTrackIndex = -1;
    private boolean ascensionView = false;

    private final Map<ResourceLocation, Long> stagedInvestments =
            new LinkedHashMap<>();
    private final Map<ResourceLocation, Long> stagedBaseStoredInvestments =
            new LinkedHashMap<>();
    private final Map<ResourceLocation, Long> stagedBaseInvestmentCaps =
            new LinkedHashMap<>();

    private long stagedBaseRevision = Long.MIN_VALUE;
    private ResourceLocation stagedBaseTierId;
    private ResourceLocation stagedBaseProfileId;

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
        graphics.fillGradient(
                0,
                0,
                width,
                height,
                BACKGROUND_TOP,
                BACKGROUND_BOTTOM
        );

        synchronizeStagedRevision();

        List<NexusCategoryView> categories =
                categories();

        renderHeader(
                graphics,
                mouseX,
                mouseY
        );

        if (!ClientEssenceState.ready()) {
            renderWaitingState(graphics);
            return;
        }

        if (!categories.isEmpty()) {
            stabilizeSelection(categories);
            renderTabs(graphics, categories);
        }

        if (ascensionView) {
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

        if (category.presentationType()
                == NexusCategoryView.PresentationType.ATTRIBUTE_SLIDERS) {
            renderAttributeCategory(
                    graphics,
                    category,
                    mouseX,
                    mouseY
            );
        } else {
            renderPlaceholderCategory(
                    graphics,
                    category
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
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private void renderHeader(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int left =
                (width - ASCENDANCE_HEADER_WIDTH) / 2;
        boolean hovered =
                inside(
                        mouseX,
                        mouseY,
                        left,
                        ASCENDANCE_HEADER_Y,
                        ASCENDANCE_HEADER_WIDTH,
                        ASCENDANCE_HEADER_HEIGHT
                );

        graphics.fill(
                left,
                ASCENDANCE_HEADER_Y,
                left + ASCENDANCE_HEADER_WIDTH,
                ASCENDANCE_HEADER_Y + ASCENDANCE_HEADER_HEIGHT,
                ascensionView
                        ? PANEL_INNER
                        : hovered ? 0xAA292733 : 0x44292733
        );
        outline(
                graphics,
                left,
                ASCENDANCE_HEADER_Y,
                ASCENDANCE_HEADER_WIDTH,
                ASCENDANCE_HEADER_HEIGHT,
                ascensionView || hovered ? BORDER_BRIGHT : 0x8877658E
        );

        float scale = 1.28F;
        graphics.pose().pushPose();
        graphics.pose().translate(
                width / 2.0F,
                ASCENDANCE_HEADER_Y + 5.0F,
                0.0F
        );
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(
                font,
                "ASCENDANCE",
                0,
                0,
                ascensionView || hovered ? TEXT : MUTED
        );
        graphics.pose().popPose();
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
                "Synchronizing progression data…",
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
                    !ascensionView
                            && i == selectedCategoryIndex;

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
                            category.shortDisplayName(),
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
            transition = current + "  •  MAXIMUM";
        } else {
            transition = current + "  •  UNAVAILABLE";
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

        y = renderAscensionRequirementRow(
                graphics,
                innerLeft,
                innerRight,
                y,
                rowHeight,
                "TOTAL INVESTMENT",
                formatLong(progress.effectiveInvestment())
                        + " / "
                        + formatLong(progress.requiredInvestment()),
                progress.effectiveInvestment(),
                progress.requiredInvestment(),
                progress.effectiveInvestment()
                        >= progress.requiredInvestment()
        ) + rowGap;

        y = renderAscensionRequirementRow(
                graphics,
                innerLeft,
                innerRight,
                y,
                rowHeight,
                developedStatsRequirementLabel(progress),
                progress.developedStats()
                        + " / "
                        + progress.requiredDevelopedStats(),
                progress.developedStats(),
                progress.requiredDevelopedStats(),
                progress.developedStats()
                        >= progress.requiredDevelopedStats()
        ) + rowGap;

        y = renderAscensionRequirementRow(
                graphics,
                innerLeft,
                innerRight,
                y,
                rowHeight,
                developedCategoryRequirementLabel(progress),
                progress.representedCategories()
                        + " / "
                        + progress.requiredRepresentedCategories(),
                progress.representedCategories(),
                progress.requiredRepresentedCategories(),
                progress.representedCategories()
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

        renderAscendControls(
                graphics,
                progress,
                layout,
                mouseX,
                mouseY
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

                return "DEVELOPED STATS (≥ "
                        + thresholdPercent
                        + "% of current cap)";
            }
        }

        if (sharedThreshold == null) {
            return "DEVELOPED STATS";
        }

        return "DEVELOPED STATS (≥ "
                + formatLong(sharedThreshold)
                + ")";
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

                return "DEVELOPED CATEGORIES (1 stat ≥ "
                        + thresholdPercent
                        + "% of its cap)";
            }
        }

        if (sharedThreshold == null) {
            return "DEVELOPED CATEGORIES";
        }

        return "DEVELOPED CATEGORIES (1 stat ≥ "
                + formatLong(sharedThreshold)
                + ")";
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
                "WORLD PROGRESSION",
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
                            ? "No world milestone required"
                            : "No resolvable world requirement",
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
                            prefix + requirement.label(),
                            textWidth
                    ),
                    textLeft,
                    lineY + i * lineHeight,
                    color,
                    false
            );
        }

        if (renderedLines < requirements.size()) {
            String more =
                    "… +"
                            + (requirements.size() - renderedLines)
                            + " more";
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

    private void renderAscendControls(
            GuiGraphics graphics,
            ClientEssenceState.ProgressSnapshot progress,
            ContentLayout layout,
            int mouseX,
            int mouseY
    ) {
        boolean staged =
                hasStagedChanges();
        boolean enabled =
                progress.readyToAscend()
                        && !staged;
        int buttonY =
                layout.bottom()
                        - BOTTOM_CONTROL_HEIGHT
                        - 3;
        int buttonX =
                (layout.left() + layout.right() - ASCEND_BUTTON_WIDTH) / 2;

        if (layout.sectionHeight() >= 28) {
            String status =
                    staged
                            ? "ALLOCATE staged changes before Ascending"
                            : progress.readyToAscend()
                            ? "READY TO ASCEND"
                            : "Requirements incomplete";
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
                "ASCEND",
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
                "Maximum Ascendance achieved.",
                centerX,
                centerY + 4,
                TEXT
        );
        graphics.drawCenteredString(
                font,
                "No higher registered tier exists.",
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
                "ASCENSION UNAVAILABLE",
                centerX,
                centerY - 12,
                ERROR
        );
        graphics.drawCenteredString(
                font,
                trimToWidth(
                        "The server could not resolve the configured Ascension requirements.",
                        Math.max(80, layout.width() - 24)
                ),
                centerX,
                centerY + 5,
                MUTED
        );
    }

    private void renderAttributeCategory(
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
                category.shortDisplayName(),
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
                        "AVAILABLE",
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
                    "No synchronized stats are registered for this category.",
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
                        track.stat().displayName(),
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
                hasStagedChanges()
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
            boolean hasChanges
    ) {
        graphics.fill(
                x,
                y,
                x + ALLOCATE_BUTTON_WIDTH,
                y + BOTTOM_CONTROL_HEIGHT,
                hasChanges ? PANEL_INNER : 0x88201E26
        );
        outline(
                graphics,
                x,
                y,
                ALLOCATE_BUTTON_WIDTH,
                BOTTOM_CONTROL_HEIGHT,
                hasChanges ? BORDER_BRIGHT : 0xFF4A4650
        );
        graphics.drawCenteredString(
                font,
                "ALLOCATE",
                x + ALLOCATE_BUTTON_WIDTH / 2,
                y + Math.max(
                        2,
                        (BOTTOM_CONTROL_HEIGHT - font.lineHeight) / 2
                ),
                hasChanges ? TEXT : DIM
        );
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
        String instruction =
                hasStagedChanges()
                        ? "Essence reallocation staged. Press ALLOCATE to commit the changes."
                        : "Move sliders to redistribute Essence. Closing the Nexus discards unallocated changes.";

        graphics.drawCenteredString(
                font,
                trimToWidth(
                        instruction,
                        Math.max(80, layout.width() - 20)
                ),
                (layout.left() + layout.right()) / 2,
                height - 12,
                MUTED
        );
    }

    private void renderPlaceholderCategory(
            GuiGraphics graphics,
            NexusCategoryView category
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
                category.shortDisplayName(),
                layout
        );
        renderAvailableGauge(
                graphics,
                category,
                layout
        );

        int centerX =
                (layout.tracksLeft() + layout.right()) / 2;
        int centerY =
                (layout.top() + layout.bottom()) / 2;

        graphics.drawCenteredString(
                font,
                category.essence().displayName(),
                centerX,
                centerY - 20,
                TEXT
        );
        graphics.drawCenteredString(
                font,
                "This Essence uses a separate progression presentation.",
                centerX,
                centerY,
                MUTED
        );
        graphics.drawCenteredString(
                font,
                "Its skill/passive/ability progression model is not defined yet.",
                centerX,
                centerY + 13,
                MUTED
        );
        graphics.drawCenteredString(
                font,
                "The Nexus shell and dynamic navigation are already ready for it.",
                centerX,
                centerY + 26,
                DIM
        );
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button != 0) {
            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        if (handleAscendanceHeaderClick(
                mouseX,
                mouseY
        )) {
            return true;
        }

        List<NexusCategoryView> categories =
                categories();

        if (!categories.isEmpty()) {
            stabilizeSelection(categories);

            if (handleTabClick(
                    mouseX,
                    mouseY,
                    categories
            )) {
                return true;
            }
        }

        if (ascensionView) {
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

        if (category.presentationType()
                != NexusCategoryView.PresentationType.ATTRIBUTE_SLIDERS) {
            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        ContentLayout layout =
                contentLayout();
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
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (!ascensionView
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

    private boolean handleAscendanceHeaderClick(
            double mouseX,
            double mouseY
    ) {
        int left =
                (width - ASCENDANCE_HEADER_WIDTH) / 2;

        if (!inside(
                mouseX,
                mouseY,
                left,
                ASCENDANCE_HEADER_Y,
                ASCENDANCE_HEADER_WIDTH,
                ASCENDANCE_HEADER_HEIGHT
        )) {
            return false;
        }

        ascensionView = true;
        draggingTrackIndex = -1;
        return true;
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

        if (!snapshot.ready()
                || progress.status()
                != PlayerEssenceSyncPayload.ProgressStatus.AVAILABLE
                || !progress.readyToAscend()
                || hasStagedChanges()) {
            return true;
        }

        NetworkManager.sendToServer(
                new AscendanceAscendPayload(
                        menu.containerId,
                        snapshot.tierId().toString()
                )
        );

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
                ascensionView = false;
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

        if (!hasStagedChanges()) {
            return true;
        }

        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();

        if (!snapshot.ready()) {
            return true;
        }

        List<AscendanceAllocationPayload.Target> targets =
                new ArrayList<>();

        for (Map.Entry<ResourceLocation, Long> entry :
                stagedInvestments.entrySet()) {
            ClientEssenceState.StatSnapshot state =
                    snapshot.stats().get(entry.getKey());

            if (state == null) {
                continue;
            }

            long target = Math.max(0L, entry.getValue());

            if (target == state.storedInvestment()) {
                continue;
            }

            targets.add(
                    new AscendanceAllocationPayload.Target(
                            entry.getKey().toString(),
                            state.storedInvestment(),
                            target
                    )
            );
        }

        if (targets.isEmpty()) {
            return true;
        }

        long baseRevision =
                stagedBaseRevision == Long.MIN_VALUE
                        ? snapshot.playerRevision()
                        : stagedBaseRevision;

        NetworkManager.sendToServer(
                new AscendanceAllocationPayload(
                        menu.containerId,
                        baseRevision,
                        snapshot.tierId().toString(),
                        snapshot.balanceProfileId().toString(),
                        targets
                )
        );

        return true;
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
            stagedInvestments.put(
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

        stagedInvestments.put(
                track.stat().id(),
                requestedTarget
        );
    }

    private long stagedInvestment(
            NexusProgressionTrack track
    ) {
        return Math.max(
                0L,
                stagedInvestments.getOrDefault(
                        track.stat().id(),
                        track.state().storedInvestment()
                )
        );
    }

    private long stagedAvailableEssence(
            NexusCategoryView category
    ) {
        long additionalSpending = 0L;
        long refunds = 0L;

        for (NexusProgressionTrack track : category.tracks()) {
            long stored =
                    Math.max(0L, track.state().storedInvestment());
            long staged =
                    stagedInvestment(track);

            if (staged >= stored) {
                additionalSpending =
                        safeAddNonNegative(
                                additionalSpending,
                                staged - stored
                        );
            } else {
                refunds =
                        safeAddNonNegative(
                                refunds,
                                stored - staged
                        );
            }
        }

        long budget =
                safeAddNonNegative(
                        category.availableEssence(),
                        refunds
                );
        long spent =
                Math.min(
                        budget,
                        additionalSpending
                );

        return budget - spent;
    }

    private double stagedTotalInvestment(
            NexusCategoryView category
    ) {
        double total = 0.0;

        for (NexusProgressionTrack track : category.tracks()) {
            total += stagedInvestment(track);
        }

        return total;
    }

    private boolean hasStagedChanges() {
        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();

        for (Map.Entry<ResourceLocation, Long> entry :
                stagedInvestments.entrySet()) {
            ClientEssenceState.StatSnapshot state =
                    snapshot.stats().get(entry.getKey());

            if (state != null
                    && Math.max(0L, entry.getValue())
                    != state.storedInvestment()) {
                return true;
            }
        }

        return false;
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

    private void synchronizeStagedRevision() {
        ClientEssenceState.Snapshot snapshot =
                ClientEssenceState.snapshot();

        if (!snapshot.ready()) {
            return;
        }

        if (stagedBaseRevision == Long.MIN_VALUE) {
            captureStagedAuthoritativeBaseline(snapshot);
            return;
        }

        if (stagedBaseRevision == snapshot.playerRevision()) {
            return;
        }

        /*
         * AVAILABLE Essence is allowed to change underneath an open Nexus.
         * Crucible channeling does exactly that and bumps the normal player
         * synchronization revision every time Essence is transferred.
         *
         * A balance-only update must therefore rebase the request revision
         * without throwing away the user's staged slider positions. If the
         * authoritative tier/profile, stored investments, or investment caps
         * changed, the staged plan is no longer based on the same progression
         * state and is discarded instead.
         */
        if (matchesStagedAuthoritativeBaseline(snapshot)) {
            stagedBaseRevision = snapshot.playerRevision();
            return;
        }

        stagedInvestments.clear();
        draggingTrackIndex = -1;
        captureStagedAuthoritativeBaseline(snapshot);
    }

    private void captureStagedAuthoritativeBaseline(
            ClientEssenceState.Snapshot snapshot
    ) {
        stagedBaseRevision = snapshot.playerRevision();
        stagedBaseTierId = snapshot.tierId();
        stagedBaseProfileId = snapshot.balanceProfileId();

        stagedBaseStoredInvestments.clear();
        stagedBaseInvestmentCaps.clear();

        for (Map.Entry<ResourceLocation, ClientEssenceState.StatSnapshot> entry :
                snapshot.stats().entrySet()) {
            stagedBaseStoredInvestments.put(
                    entry.getKey(),
                    entry.getValue().storedInvestment()
            );
            stagedBaseInvestmentCaps.put(
                    entry.getKey(),
                    entry.getValue().currentInvestmentCap()
            );
        }
    }

    private boolean matchesStagedAuthoritativeBaseline(
            ClientEssenceState.Snapshot snapshot
    ) {
        if (!Objects.equals(
                stagedBaseTierId,
                snapshot.tierId()
        ) || !Objects.equals(
                stagedBaseProfileId,
                snapshot.balanceProfileId()
        )) {
            return false;
        }

        if (stagedBaseStoredInvestments.size()
                != snapshot.stats().size()
                || stagedBaseInvestmentCaps.size()
                != snapshot.stats().size()) {
            return false;
        }

        for (Map.Entry<ResourceLocation, ClientEssenceState.StatSnapshot> entry :
                snapshot.stats().entrySet()) {
            Long stored =
                    stagedBaseStoredInvestments.get(entry.getKey());
            Long cap =
                    stagedBaseInvestmentCaps.get(entry.getKey());

            if (stored == null
                    || cap == null
                    || stored.longValue()
                    != entry.getValue().storedInvestment()
                    || cap.longValue()
                    != entry.getValue().currentInvestmentCap()) {
                return false;
            }
        }

        return true;
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
                            font.width(category.shortDisplayName()) + 14
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
        int top = 58;
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
            return "Unknown";
        }

        return AscendanceTierRegistry
                .get(tierId)
                .map(AscendanceTierDefinition::displayName)
                .orElse(tierId.getPath());
    }

    private String formatBonus(
            StatUnit unit,
            double value
    ) {
        String number =
                formatDecimal(value);

        return switch (unit) {
            case PERCENT -> "+" + number + "%";
            case HEARTS -> "+" + number + " hearts";
            case HEARTS_PER_SECOND -> "+" + number + " hearts/s";
            case BLOCKS -> "+" + number + " blocks";
            case SECONDS -> "+" + number + " s";
            case LEVELS -> "+" + number + " levels";
            case FLAT -> "+" + number;
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
}
