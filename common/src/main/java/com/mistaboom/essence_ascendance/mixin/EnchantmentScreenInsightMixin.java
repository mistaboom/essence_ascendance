package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.client.ClientCommittedSkills;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/** Replaces vanilla's single-clue enchanting tooltip with the complete deterministic result while
 * retaining vanilla's own cost/requirement lines. Exactly one tooltip is rendered. */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenInsightMixin {
    //? if >=26.1.2
    /*@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target =*/
    //? if <26.1.2
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            //? if >=26.1.2
            /*"Lnet/minecraft/client/gui/GuiGraphicsExtractor;setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"))*/
            //? if <26.1.2
            "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"))
    //? if >=26.1.2
    /*private void essenceAscendance$renderFullEnchantingResult(GuiGraphicsExtractor graphics, Font font,*/
    //? if <26.1.2
    private void essenceAscendance$renderFullEnchantingResult(GuiGraphics graphics, Font font,
                                                               List<Component> vanillaLines,
                                                               int mouseX, int mouseY,
                                                               Operation<Void> original) {
        if (!ClientCommittedSkills.isEffective(SkillIds.ENCHANTING_INSIGHT)) {
            original.call(graphics, font, vanillaLines, mouseX, mouseY);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            original.call(graphics, font, vanillaLines, mouseX, mouseY);
            return;
        }
        EnchantmentMenu menu = ((EnchantmentScreen) (Object) this).getMenu();
        ItemStack item = menu.getSlot(0).getItem();
        int offer = essenceAscendance$hoveredOffer(mouseX, mouseY, menu);
        if (item.isEmpty() || offer < 0) {
            original.call(graphics, font, vanillaLines, mouseX, mouseY);
            return;
        }

        List<EnchantmentInstance> result = ((EnchantmentMenuInsightAccess) menu)
                .essenceAscendance$getEnchantmentList(minecraft.level.registryAccess(), item, offer, menu.costs[offer]);
        if (result.isEmpty()) {
            original.call(graphics, font, vanillaLines, mouseX, mouseY);
            return;
        }

        List<Component> lines = new ArrayList<>(result.size() + vanillaLines.size() + 1);
        lines.add(Component.translatable("screen.essence_ascendance.enchanting_insight.preview")
                .withStyle(style -> style.withColor(AscendancePalette.UTILITY)));
        for (EnchantmentInstance enchantment : result) {
            lines.add(Enchantment.getFullname(
                    //? if >=26.1.2
                    /*enchantment.enchantment(), enchantment.level()));*/
                    //? if <26.1.2
                    enchantment.enchantment, enchantment.level));
        }
        // Vanilla's first line is the deliberately incomplete single-enchantment clue. Replace only that line;
        // preserve its blank separator and all lapis/level requirement text verbatim.
        if (vanillaLines.size() > 1) lines.addAll(vanillaLines.subList(1, vanillaLines.size()));
        original.call(graphics, font, lines, mouseX, mouseY);
    }

    private int essenceAscendance$hoveredOffer(int mouseX, int mouseY, EnchantmentMenu menu) {
        AbstractContainerScreenPositionAccess position = (AbstractContainerScreenPositionAccess) (Object) this;
        int x = position.essenceAscendance$getLeftPos() + 60;
        for (int offer = 0; offer < menu.costs.length; offer++) {
            if (menu.costs[offer] <= 0) continue;
            int y = position.essenceAscendance$getTopPos() + 14 + offer * 19;
            if (mouseX >= x && mouseX < x + 108 && mouseY >= y && mouseY < y + 19) return offer;
        }
        return -1;
    }
}
