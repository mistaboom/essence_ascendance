package com.mistaboom.essence_ascendance.pylon;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance;
import com.mistaboom.essence_ascendance.infuser.FocusInfusionData;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;
import java.util.Locale;

/** Single evolving machine-upgrade focus. Tier is stored on the ItemStack. */
public final class EssenceFocusItem extends Item {

    public EssenceFocusItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Object blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (!(blockEntity instanceof EssencePylonBlockEntity)
                && !(blockEntity instanceof EssenceInfuserBlockEntity)) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (blockEntity instanceof EssencePylonBlockEntity pylon) {
            return pylon.installOrSwapFocus(player, context.getHand());
        }

        EssenceInfuserBlockEntity infuser = (EssenceInfuserBlockEntity) blockEntity;
        return infuser.installOrSwapFocus(player, context.getHand())
                ? InteractionResult.CONSUME
                : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        EssenceFocusTier tier = EssenceFocusData.tier(stack);
        Component tierName = tier == null
                ? EssenceText.equipmentTier(com.mistaboom.essence_ascendance.equipment.EquipmentTier.LATENT)
                : EssenceText.focusTier(tier);
        tooltipComponents.add(
                Component.literal("  ")
                        .append(EssenceText.tooltip("tier", tierName))
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
        );
        tooltipComponents.add(Component.empty());

        tooltipComponents.add(section(EssenceText.term("pylon"), ChatFormatting.GREEN));
        if (tier == null) {
            tooltipComponents.add(muted(EssenceText.tooltip("focus.latent_baseline")));
        } else {
            EssencePylonContribution contribution = tier.contribution();
            tooltipComponents.add(value(EssenceText.tooltip("focus.pylon.reservoir", format(contribution.reservoirCapacityBonus()))));
            tooltipComponents.add(value(EssenceText.tooltip("focus.pylon.channel_rate", format(contribution.transferRatePerSecondBonus()))));
            tooltipComponents.add(value(EssenceText.tooltip(
                    "focus.pylon.channel_range",
                    String.format(Locale.ROOT, "%.2f", contribution.transferRangeBonus())
            )));
            tooltipComponents.add(value(EssenceText.tooltip(
                    "focus.pylon.dissolution_speed",
                    String.format(Locale.ROOT, "%.0f", contribution.dissolutionSpeedBonus() * 100.0D)
            )));
            tooltipComponents.add(value(EssenceText.tooltip("focus.pylon.items_batch", contribution.simultaneousItemProcessesBonus())));
        }

        tooltipComponents.add(Component.empty());
        tooltipComponents.add(section(EssenceText.term("infuser"), ChatFormatting.AQUA));
        if (tier == null) {
            tooltipComponents.add(muted(EssenceText.tooltip("focus.latent_baseline")));
            EssenceInfuserBalance.Profile profile = EssenceInfuserBalance.profile((EssenceFocusTier) null);
            tooltipComponents.add(value(EssenceText.tooltip("focus.infuser.grade", EssenceText.focusTier(profile.grade()))));
            tooltipComponents.add(value(EssenceText.tooltip(
                    "focus.infuser.efficiency",
                    String.format(Locale.ROOT, "%.1f", profile.efficiencyPercent())
            )));
            tooltipComponents.add(value(EssenceText.tooltip("focus.infuser.rate",
                    format(profile.infusionThroughputPerSecond()))));
        } else {
            EssenceInfuserBalance.Profile profile = EssenceInfuserBalance.profile(tier);
            tooltipComponents.add(value(EssenceText.tooltip("focus.infuser.grade", EssenceText.focusTier(profile.grade()))));
            tooltipComponents.add(value(EssenceText.tooltip(
                    "focus.infuser.efficiency",
                    String.format(Locale.ROOT, "%.1f", profile.efficiencyPercent())
            )));
            tooltipComponents.add(value(EssenceText.tooltip(
                    "focus.infuser.rate",
                    format(profile.infusionThroughputPerSecond())
            )));
        }

        FocusInfusionData.appendTooltip(stack, tooltipComponents);
    }

    private static Component section(Component title, ChatFormatting color) {
        return title.copy().withStyle(color, ChatFormatting.BOLD);
    }

    private static Component value(Component text) {
        return Component.literal("  ").append(text).withStyle(ChatFormatting.WHITE);
    }

    private static Component muted(Component text) {
        return Component.literal("  ").append(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }
}
