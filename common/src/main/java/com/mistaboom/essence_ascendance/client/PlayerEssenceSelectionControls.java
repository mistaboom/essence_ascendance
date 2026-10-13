package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.crucible.*;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.IntConsumer;

/** The Crucible’s six-type personal essence selector. */
public final class PlayerEssenceSelectionControls {
    @FunctionalInterface public interface Factory { Button add(Component text, Button.OnPress action, UiBounds bounds); }
    private final Button all, none;
    private final List<Button> types = new ArrayList<>();
    public PlayerEssenceSelectionControls(Factory factory, IntConsumer change, UiBounds allBounds, UiBounds noneBounds, int x, int y, int spacing) {
        all = factory.add(EssenceText.gui("crucible.channel_selection.all"), b -> change.accept(CrucibleChannelSelection.SELECT_ALL), allBounds);
        none = factory.add(EssenceText.gui("crucible.channel_selection.none"), b -> change.accept(CrucibleChannelSelection.SELECT_NONE), noneBounds);
        all.setTooltip(Tooltip.create(EssenceText.gui("crucible.channel_selection.all_tooltip")));
        none.setTooltip(Tooltip.create(EssenceText.gui("crucible.channel_selection.none_tooltip")));
        for (int i = 0; i < EssenceCrucibleEssences.ORDERED.size(); i++) {
            final int index = i;
            var button = factory.add(Component.empty(), b -> change.accept(index), new UiBounds(x, y + i * spacing, 12, 11));
            button.setTooltip(Tooltip.create(EssenceText.gui("crucible.channel_selection.toggle_tooltip", EssenceText.essenceShort(EssenceCrucibleEssences.ORDERED.get(i)))));
            types.add(button);
        }
    }
    public void update(int mask, boolean allowed, boolean visible) {
        all.active = allowed && mask != CrucibleChannelSelection.ALL; none.active = allowed && mask != 0;
        all.visible = none.visible = visible;
        for (int i = 0; i < types.size(); i++) {
            var button = types.get(i); button.visible = visible; button.active = allowed;
            button.setMessage(CrucibleChannelSelection.selected(mask, i) ? Component.literal("\u2713").withStyle(net.minecraft.ChatFormatting.GREEN) : Component.empty());
        }
    }
}
