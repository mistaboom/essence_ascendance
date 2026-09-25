package com.mistaboom.essence_ascendance.client.archive;

import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Archive and Item Yields share the generic fullscreen editor and native clipboard. */
public final class ArchiveSearchField extends FullscreenTextField {
    public ArchiveSearchField(String initial, Component placeholder, Consumer<String> changed) {
        super(initial, placeholder, changed, () -> Minecraft.getInstance().keyboardHandler.getClipboard(),
                value -> Minecraft.getInstance().keyboardHandler.setClipboard(value));
    }
}
