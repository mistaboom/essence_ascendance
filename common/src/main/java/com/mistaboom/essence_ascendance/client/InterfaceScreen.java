package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.team.TeamMenu;
import com.mistaboom.essence_ascendance.network.TeamInterfacePayload;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;

/** Uses the actual machine framework: layout, widgets, scrollable semantic content and confirmations. */
public abstract class InterfaceScreen extends MachineContainerScreen<TeamMenu> {
    private final Button[] actions = new Button[TeamInterfacePayload.PAGE_SIZE];
    private Button previous, next;
    private EditBox name;
    private Button infoButton, infoClose;
    protected InterfaceScreen(TeamMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, new MachineScreenLayout.Spec(230, 240, new UiBounds(0, 0, 0, 0), 0, 0));
    }
    @Override protected void initMachineWidgets() {
        String existing = name == null ? "" : name.getValue();
        infoButton = addHeaderButton(Component.literal("i"), true, b -> toggleMachinePopup(INFO_POPUP));
        infoClose = addOverlayCloseButton(b -> closeMachinePopup(INFO_POPUP), infoBounds());
        name = addMachineTextField(EssenceText.gui("team.name"), new UiBounds(leftPos + 8, topPos + 82, 214, 18), 80);
        name.setValue(existing);
        for (int i = 0; i < actions.length; i++) { final int index = i;
            actions[i] = addMachineButton(Component.empty(), b -> activate(index), new UiBounds(leftPos + 8, topPos + 108 + i * 27, 214, 25));
        }
        previous = addMachineButton(Component.literal("<"), b -> page(-1), new UiBounds(leftPos + 8, topPos + 216, 26, 18));
        next = addMachineButton(Component.literal(">"), b -> page(1), new UiBounds(leftPos + 196, topPos + 216, 26, 18));
        TeamInterfaceClientState.request(menu.containerId, menu.view, 0);
    }
    private void page(int step) { var p = TeamInterfaceClientState.snapshot(menu.containerId); if (p != null) TeamInterfaceClientState.request(menu.containerId, p.view(), p.page() + step); }
    private void activate(int index) {
        var p = TeamInterfaceClientState.snapshot(menu.containerId);
        if (p == null || index >= p.controls().size()) return;
        var control = p.controls().get(index); String input = name.getValue();
        Runnable action = () -> TeamInterfaceClientState.action(p, index, input);
        if (control.confirmation()) confirmMachineAction(TeamInterfaceClientState.text(control.label()), TeamInterfaceClientState.text(control.warning()), action);
        else action.run();
    }
    @Override protected void updateMachineWidgets() {
        var p = TeamInterfaceClientState.snapshot(menu.containerId);
        for (int i = 0; i < actions.length; i++) {
            actions[i].visible = p != null && i < p.controls().size();
            actions[i].active = actions[i].visible && (!p.controls().get(i).input() || !name.getValue().isBlank());
            if (actions[i].visible) actions[i].setMessage(TeamInterfaceClientState.text(p.controls().get(i).label()));
        }
        infoButton.active = p != null; infoClose.visible = p != null && isMachinePopupOpen(INFO_POPUP); place(infoClose, overlayCloseBounds(infoBounds()));
        for (int i = 0; i < actions.length; i++) place(actions[i], actionBounds(i));
        place(name, new UiBounds(leftPos + 8, topPos + contentHeight() + 33, 214, 18));
        name.visible = p != null && p.controls().stream().anyMatch(TeamInterfacePayload.Control::input);
        previous.visible = next.visible = p != null && p.pages() > 1;
        previous.active = p != null && p.page() > 0; next.active = p != null && p.page() + 1 < p.pages();
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) { MachineScreenUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight); }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, title, 8, MachineScreenUi.TEXT);
        var p = TeamInterfaceClientState.snapshot(menu.containerId);
        if (p == null) { drawCentered(graphics, EssenceText.term("synchronizing"), 130, MachineScreenUi.MUTED); return; }
        int y = 30;
        for (var line : p.lines()) {
            var text = TeamInterfaceClientState.text(line);
            graphics.drawWordWrap(font, text, 8, y, 214, MachineScreenUi.TEXT);
            y += font.split(text, 214).size() * font.lineHeight + 5;
        }
        if (p.pages() > 1) graphics.drawCenteredString(font, EssenceText.gui("team.page", p.page() + 1, p.pages()), 115, 221, MachineScreenUi.MUTED);
    }
    private int contentHeight() {
        var state = TeamInterfaceClientState.snapshot(menu.containerId);
        int result = 0;
        if (state != null) for (var line : state.lines()) result += font.split(TeamInterfaceClientState.text(line), 214).size() * font.lineHeight + 5;
        return result;
    }
    protected UiBounds actionBounds(int index) {
        var state = TeamInterfaceClientState.snapshot(menu.containerId);
        boolean input = state != null && state.controls().stream().anyMatch(TeamInterfacePayload.Control::input);
        return new UiBounds(leftPos + 8, topPos + 37 + contentHeight() + (input ? 26 : 0) + index * 27, 214, 23);
    }
    @Override public void onClose() { TeamInterfaceClientState.clear(); super.onClose(); }
    private UiBounds infoBounds() { return sidePanel(MachineScreenLayout.INFO_PANEL_WIDTH, 238, MachineScreenLayout.SidePreference.RIGHT_FIRST).bounds(); }
    @Override protected List<MachineOverlay> machineOverlays() {
        var p = TeamInterfaceClientState.snapshot(menu.containerId); if (p == null || !isMachinePopupOpen(INFO_POPUP)) return List.of();
        var info = new MachineInfoPanel().title(title);
        for (var line : p.lines()) info.line(TeamInterfaceClientState.text(line));
        return List.of(infoOverlay("team_content", infoBounds(), info, List.of(infoClose)));
    }
}
