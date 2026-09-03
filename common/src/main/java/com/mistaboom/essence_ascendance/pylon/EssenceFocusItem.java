package com.mistaboom.essence_ascendance.pylon;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance;
import com.mistaboom.essence_ascendance.infuser.FocusInfusionData;
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
        tooltipComponents.add(
                Component.literal("  Tier: " + EssenceFocusData.displayTier(stack))
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
        );
        tooltipComponents.add(Component.empty());

        tooltipComponents.add(section("Pylon", ChatFormatting.GREEN));
        if (tier == null) {
            tooltipComponents.add(muted("Inactive until infused to Dormant."));
        } else {
            EssencePylonContribution contribution = tier.contribution();
            tooltipComponents.add(value("Reservoir: +" + format(contribution.reservoirCapacityBonus())));
            tooltipComponents.add(value("Channel Rate: +" + format(contribution.transferRatePerSecondBonus()) + "/sec"));
            tooltipComponents.add(value(String.format(
                    Locale.ROOT,
                    "Channel Range: +%.2f blocks",
                    contribution.transferRangeBonus()
            )));
            tooltipComponents.add(value(String.format(
                    Locale.ROOT,
                    "Dissolution Speed: +%.0f%%",
                    contribution.dissolutionSpeedBonus() * 100.0D
            )));
            tooltipComponents.add(value("Items/Batch: +" + contribution.simultaneousItemProcessesBonus()));
        }

        tooltipComponents.add(Component.empty());
        tooltipComponents.add(section("Infuser", ChatFormatting.AQUA));
        if (tier == null) {
            tooltipComponents.add(muted("Inactive until infused to Dormant."));
        } else {
            EssenceInfuserBalance.Profile profile = EssenceInfuserBalance.profile(tier);
            tooltipComponents.add(value("Infusion Grade: " + profile.grade().displayName()));
            tooltipComponents.add(value(String.format(
                    Locale.ROOT,
                    "Efficiency: %.1f%%",
                    profile.efficiencyPercent()
            )));
            tooltipComponents.add(value(
                    "Infusion Rate: " + format(profile.infusionThroughputPerSecond()) + " Essence/sec"
            ));
        }

        FocusInfusionData.appendTooltip(stack, tooltipComponents);
    }

    private static Component section(String title, ChatFormatting color) {
        return Component.literal(title).withStyle(color, ChatFormatting.BOLD);
    }

    private static Component value(String text) {
        return Component.literal("  " + text).withStyle(ChatFormatting.WHITE);
    }

    private static Component muted(String text) {
        return Component.literal("  " + text).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }
}
