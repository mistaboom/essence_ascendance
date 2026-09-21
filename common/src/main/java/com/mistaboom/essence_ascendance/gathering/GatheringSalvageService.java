package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.ConstructionMaterialResolver;
import com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectMath;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit grindstone recycle action backed by loaded construction recipes and real enchantment data. */
public final class GatheringSalvageService {
    public static final int BUTTON_RECYCLE = 0xEA51;
    private static final int INPUT_SLOT = 0;
    private static final int ADDITIONAL_SLOT = 1;

    private GatheringSalvageService() { }

    public record HudState(boolean grindstoneOpen, boolean salvageReady, int lastMaterials, int lastExperience) { }

    /**
     * Handles the dedicated recycle button sent through Minecraft's normal inventory-button packet.
     * Returning true means the button id was ours, even when server validation rejects the action.
     */
    public static boolean clickMenuButton(AbstractContainerMenu menu, Player actor, int buttonId) {
        if (!(menu instanceof GrindstoneMenu) || buttonId != BUTTON_RECYCLE) return false;
        if (!(actor instanceof ServerPlayer player) || menu != player.containerMenu || !menu.stillValid(player)) return true;

        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.SALVAGERS_CRAFT)) return true;

        int slotIndex = salvageSlot(menu, player);
        if (slotIndex < 0) return true;

        Slot slot = menu.getSlot(slotIndex);
        ItemStack source = slot.getItem();
        if (source.isEmpty()) return true;
        ItemStack salvaged = source.copyWithCount(1);

        List<ConstructionMaterialResolver.MaterialAmount> materials =
                ConstructionMaterialResolver.resolve(salvaged, player.getServer());
        double storedExperience = disenchantExperience(salvaged);
        if (materials.isEmpty() && storedExperience <= 0.0D) return true;

        ItemStack removed = slot.remove(1);
        if (removed.isEmpty()) return true;
        slot.setChanged();

        GatheringBalanceSettings.SalvagersCraft tuning = context.settings().gathering().salvagersCraft();
        int recoveredMaterials = recoverMaterials(player, materials, tuning.materialRecoveryFraction());
        int recoveredExperience = SkillEffectMath.stochasticWhole(
                storedExperience * tuning.bonusExperienceFraction(), player.getRandom());
        if (recoveredExperience > 0) {
            ExperienceOrb.award(player.serverLevel(), player.position(), recoveredExperience);
        }

        SalvageState state = context.state(SkillIds.SALVAGERS_CRAFT, SalvageState::new);
        state.lastMaterials = recoveredMaterials;
        state.lastExperience = recoveredExperience;

        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        menu.broadcastChanges();
        return true;
    }

    public static HudState hudState(SkillEffectRuntime.Context context) {
        AbstractContainerMenu menu = context.player().containerMenu;
        boolean open = menu instanceof GrindstoneMenu && menu.stillValid(context.player());
        boolean ready = open && salvageSlot(menu, context.player()) >= 0;
        SalvageState state = context.existingState(SkillIds.SALVAGERS_CRAFT);
        return new HudState(open, ready, state == null ? 0 : state.lastMaterials,
                state == null ? 0 : state.lastExperience);
    }

    private static int salvageSlot(AbstractContainerMenu menu, ServerPlayer player) {
        if (salvageable(menu.getSlot(INPUT_SLOT).getItem(), player)) return INPUT_SLOT;
        if (salvageable(menu.getSlot(ADDITIONAL_SLOT).getItem(), player)) return ADDITIONAL_SLOT;
        return -1;
    }

    private static boolean salvageable(ItemStack stack, ServerPlayer player) {
        if (!EquipmentMaintenanceService.eligible(stack)) return false;
        return !ConstructionMaterialResolver.resolve(stack, player.getServer()).isEmpty()
                || disenchantExperience(stack) > 0.0D;
    }

    private static int recoverMaterials(ServerPlayer player,
                                        List<ConstructionMaterialResolver.MaterialAmount> materials,
                                        double recoveryFraction) {
        if (materials == null || materials.isEmpty() || recoveryFraction <= 0.0D) return 0;

        Map<Item, Double> expected = new LinkedHashMap<>();
        for (ConstructionMaterialResolver.MaterialAmount material : materials) {
            expected.merge(material.item(), material.amount() * recoveryFraction, Double::sum);
        }

        int recovered = 0;
        // Retain a tangible first item, but never return every input of a repeatable recipe.
        // Ascendance's one-ingot decomposition also consumes Essence in its independent native construction.
        int maximum = materialRecoveryLimit(materials.stream().mapToDouble(ConstructionMaterialResolver.MaterialAmount::amount).sum());
        for (Map.Entry<Item, Double> entry : expected.entrySet()) {
            int amount = Math.min(maximum - recovered, SkillEffectMath.stochasticWhole(entry.getValue(), player.getRandom()));
            recovered += giveMaterial(player, entry.getKey(), amount);
        }

        // Recycling is destructive. A valid material-bearing item must never disappear for zero materials.
        // The generated fraction still controls all recovery above this tangible minimum.
        if (recovered == 0 && !expected.isEmpty()) {
            Item fallback = weightedMaterial(expected, player);
            if (fallback != null) recovered += giveMaterial(player, fallback, 1);
        }
        return recovered;
    }

    public static int materialRecoveryLimit(double constructionUnits) {
        if (!Double.isFinite(constructionUnits) || constructionUnits <= 0) return 0;
        return Math.max(1, (int)Math.ceil(constructionUnits) - 1);
    }

    private static Item weightedMaterial(Map<Item, Double> expected, ServerPlayer player) {
        double total = 0.0D;
        for (double weight : expected.values()) total += Math.max(0.0D, weight);
        if (total <= 0.0D) return expected.keySet().stream().findFirst().orElse(null);
        double roll = player.getRandom().nextDouble() * total;
        for (Map.Entry<Item, Double> entry : expected.entrySet()) {
            roll -= Math.max(0.0D, entry.getValue());
            if (roll <= 0.0D) return entry.getKey();
        }
        return expected.keySet().stream().reduce((first, second) -> second).orElse(null);
    }

    private static int giveMaterial(ServerPlayer player, Item item, int amount) {
        int given = 0;
        while (amount > 0) {
            ItemStack sample = item.getDefaultInstance();
            int batch = Math.min(amount, sample.getMaxStackSize());
            ItemStack stack = new ItemStack(item, batch);
            int original = stack.getCount();
            player.getInventory().add(stack);
            int inserted = original - stack.getCount();
            given += inserted;
            if (!stack.isEmpty()) {
                int dropped = stack.getCount();
                player.drop(stack, false);
                given += dropped;
            }
            amount -= batch;
        }
        return given;
    }

    private static double disenchantExperience(ItemStack stack) {
        double total = enchantmentExperience(stack.get(DataComponents.ENCHANTMENTS));
        total += enchantmentExperience(stack.get(DataComponents.STORED_ENCHANTMENTS));
        return total;
    }

    private static double enchantmentExperience(ItemEnchantments enchantments) {
        if (enchantments == null || enchantments.isEmpty()) return 0.0D;
        double total = 0.0D;
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(EnchantmentTags.CURSE)) continue;
            int level = enchantments.getLevel(enchantment);
            if (level > 0) total += Math.max(0, enchantment.value().getMinCost(level));
        }
        return total;
    }

    private static final class SalvageState implements SkillEffectState {
        private int lastMaterials;
        private int lastExperience;
        @Override public void clear() { lastMaterials = 0; lastExperience = 0; }
    }
}
