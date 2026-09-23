package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.equipment.EnchantingMenuCostView;
import com.mistaboom.essence_ascendance.network.MicroVisualFeedback;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EnchantmentMenu.class)
public abstract class AttunementEnchantMixin {
    @WrapMethod(method = "clickMenuButton")
    private boolean essenceAscendance$committedEnchant(Player actor, int offer, Operation<Boolean> original) {
        EnchantmentMenu menu = (EnchantmentMenu) (Object) this;
        ItemStack before = menu.getSlot(0).getItem().copy(), lapis = menu.getSlot(1).getItem().copy();
        int experience = actor.experienceLevel;
        float experienceProgress = actor.experienceProgress;
        boolean visualContribution = actor instanceof ServerPlayer player && offer >= 0 && offer < menu.costs.length
                && (((EnchantingMenuCostView) menu).essenceAscendance$experienceCost(offer) < offer + 1
                || ((EnchantingMenuCostView) menu).essenceAscendance$lapisCost(offer) < offer + 1
                || SkillEffectRuntime.context(player).isEffective(SkillIds.ENCHANTING_INSIGHT));
        boolean success = original.call(actor, offer);
        ItemStack after = menu.getSlot(0).getItem();
        if (success && actor instanceof ServerPlayer player && !before.isEmpty() && !after.isEmpty()
                && !ItemStack.isSameItemSameComponents(before, after)) {
            int consumed = com.mistaboom.essence_ascendance.attunement.AttunementWorkstations.consumed(lapis, menu.getSlot(1).getItem());
            double value = AttunementGameplay.value(lapis.copyWithCount(consumed));
            // Efficient zero costs retain the value of the actual completed item transformation.
            value = Math.max(value, AttunementGameplay.value(after));
            int levelCost = Math.max(offer + 1, Math.max(0, experience - actor.experienceLevel));
            double points = AttunementGameplay.experienceCost(experience, experienceProgress, levelCost);
            value = Math.max(value, AttunementGameplay.experienceOperationValue(player, points));
            AttunementGameplay.award(player, AttunementGameplay.action("enchant"), "enchant_items", AttunementGameplay.itemSignature(after), value);
            if (visualContribution) {
                MicroVisualFeedback.guiSlot(player, TransientVisualIds.GUI_ENCHANTING_GLYPH, 0,
                        SemanticVisualColor.UTILITY, player.level().getGameTime() ^ after.hashCode());
            }
        }
        return success;
    }
}
