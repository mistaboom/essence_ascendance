package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.attunement.AttunementBrewingOwner;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(BrewingStandBlockEntity.class)
public abstract class AttunementBrewingMixin implements AttunementBrewingOwner {
    @Unique private UUID essenceAscendance$brewer;
    @Unique private String essenceAscendance$ingredient = "";
    @Unique private int essenceAscendance$manualIngredients;
    @Override public void essenceAscendance$invalidateBrewing() {
        essenceAscendance$manualIngredients = 0;
        essenceAscendance$brewer = null;
        essenceAscendance$ingredient = "";
        ((BrewingStandBlockEntity) (Object) this).setChanged();
    }
    @Override public void essenceAscendance$claimBrewing(ServerPlayer player, ItemStack ingredient, int added) {
        if (!AttunementGameplay.eligible(player)) return;
        String id = BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString();
        if (!player.getUUID().equals(essenceAscendance$brewer) || !id.equals(essenceAscendance$ingredient))
            essenceAscendance$manualIngredients = 0;
        essenceAscendance$brewer = player.getUUID();
        essenceAscendance$ingredient = id;
        essenceAscendance$manualIngredients = Math.min(ingredient.getCount(), essenceAscendance$manualIngredients + added);
        ((BrewingStandBlockEntity) (Object) this).setChanged();
    }
    @WrapMethod(method = "doBrew")
    private static void essenceAscendance$completedBrew(Level level, BlockPos pos, NonNullList<ItemStack> items, Operation<Void> original) {
        ItemStack ingredient = items.get(3).copy();
        ItemStack[] before = {items.get(0).copy(), items.get(1).copy(), items.get(2).copy()};
        original.call(level, pos, items);
        if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand)) return;
        AttunementBrewingMixin owner = (AttunementBrewingMixin) (Object) stand;
        if (owner.essenceAscendance$brewer == null || owner.essenceAscendance$manualIngredients <= 0
                || !BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString().equals(owner.essenceAscendance$ingredient)
                || items.get(3).getCount() >= ingredient.getCount() && items.get(3).is(ingredient.getItem())) return;
        double outputValue = 0;
        StringBuilder signature = new StringBuilder(owner.essenceAscendance$ingredient);
        for (int slot = 0; slot < before.length; slot++) {
            ItemStack after = items.get(slot);
            if (!before[slot].isEmpty() && !after.isEmpty() && !ItemStack.isSameItemSameComponents(before[slot], after)) {
                outputValue += Math.max(AttunementGameplay.value(after), AttunementGameplay.value(before[slot]));
                signature.append(':').append(AttunementGameplay.itemSignature(after));
            }
        }
        owner.essenceAscendance$manualIngredients--;
        stand.setChanged();
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(owner.essenceAscendance$brewer);
        if (outputValue > 0) AttunementGameplay.award(player, AttunementGameplay.action("brew"), "brew_potions",
                signature.toString(), Math.max(outputValue, AttunementGameplay.value(ingredient.copyWithCount(1))));
    }
    @Inject(method = "saveAdditional", at = @At("RETURN"))
    private void essenceAscendance$saveBrewer(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (essenceAscendance$brewer == null || essenceAscendance$manualIngredients <= 0) return;
        CompoundTag attribution = new CompoundTag();
        attribution.putUUID("Player", essenceAscendance$brewer);
        attribution.putString("Ingredient", essenceAscendance$ingredient);
        attribution.putInt("Remaining", essenceAscendance$manualIngredients);
        tag.put("EssenceAscendanceBrewing", attribution);
    }
    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void essenceAscendance$loadBrewer(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        CompoundTag attribution = tag.getCompound("EssenceAscendanceBrewing");
        essenceAscendance$brewer = attribution.hasUUID("Player") ? attribution.getUUID("Player") : null;
        essenceAscendance$ingredient = attribution.getString("Ingredient");
        essenceAscendance$manualIngredients = Math.clamp(attribution.getInt("Remaining"), 0,
                ((BrewingStandBlockEntity) (Object) this).getItem(3).getMaxStackSize());
    }
}
