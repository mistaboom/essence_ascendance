package com.mistaboom.essence_ascendance.client.ui.content;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Resolves the current registered item/model for every draw; no fake world or ticking machine is constructed. */
public final class ItemIllustrationRenderer implements IllustrationView.Renderer {
    public static final ItemIllustrationRenderer INSTANCE = new ItemIllustrationRenderer();
    private ItemIllustrationRenderer() { }

    @Override
    //? if >=26.1.2
    /*public void render(GuiGraphicsExtractor graphics, SemanticDocument.Illustration illustration,*/
    //? if <26.1.2
    public void render(GuiGraphics graphics, SemanticDocument.Illustration illustration,
                       UiBounds figureBounds, float partialTick) {
        Item item = BuiltInRegistries.ITEM.get(illustration.resource());
        if (item == null) return;
        ItemStack stack = item.getDefaultInstance();
        renderStack(graphics, stack, figureBounds);
    }

    //? if >=26.1.2
    /*public static void renderStack(GuiGraphicsExtractor graphics, ItemStack stack, UiBounds figureBounds) {*/
    //? if <26.1.2
    public static void renderStack(GuiGraphics graphics, ItemStack stack, UiBounds figureBounds) {
        if (stack.isEmpty()) return;
        float scale = Math.max(0, Math.min(figureBounds.width(), figureBounds.height())) / 18.0F;
        int rendered = Math.round(16 * scale);
        int x = figureBounds.x() + (figureBounds.width() - rendered) / 2;
        int y = figureBounds.y() + (figureBounds.height() - rendered) / 2;
        //? if >=26.1.2
        /*graphics.nextStratum();*/
        //? if <26.1.2
        graphics.flush();
        //? if >=26.1.2
        /*graphics.pose().pushMatrix();*/
        //? if <26.1.2
        graphics.pose().pushPose();
        try {
            //? if >=26.1.2
            /*graphics.nextStratum(); graphics.pose().translate(x, y);*/
            //? if <26.1.2
            graphics.pose().translate(x, y, 150);
            //? if >=26.1.2
            /*graphics.pose().scale(scale, scale);*/
            //? if <26.1.2
            graphics.pose().scale(scale, scale, 1.0F);
            //? if >=26.1.2
            /*graphics.item(stack, 0, 0);*/
            //? if <26.1.2
            graphics.renderItem(stack, 0, 0);
            //? if >=26.1.2
            /*graphics.nextStratum();*/
            //? if <26.1.2
            graphics.flush();
        } finally {
            //? if >=26.1.2
            /*graphics.pose().popMatrix();*/
            //? if <26.1.2
            graphics.pose().popPose();
        }
    }
}
