package com.mistaboom.essence_ascendance.client.ui.content;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Resolves the current registered item/model for every draw; no fake world or ticking machine is constructed. */
public final class ItemIllustrationRenderer implements IllustrationView.Renderer {
    public static final ItemIllustrationRenderer INSTANCE = new ItemIllustrationRenderer();
    private ItemIllustrationRenderer() { }

    @Override
    public void render(GuiGraphics graphics, SemanticDocument.Illustration illustration,
                       UiBounds figureBounds, float partialTick) {
        Item item = BuiltInRegistries.ITEM.get(illustration.resource());
        if (item == null) return;
        ItemStack stack = item.getDefaultInstance();
        if (stack.isEmpty()) return;
        int scale = Math.max(1, Math.min(figureBounds.width(), figureBounds.height()) / 18);
        int rendered = 16 * scale;
        int x = figureBounds.x() + (figureBounds.width() - rendered) / 2;
        int y = figureBounds.y() + (figureBounds.height() - rendered) / 2;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 150);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }
}
