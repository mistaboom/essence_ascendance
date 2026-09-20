package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.attunement.AttunementBrewingOwner;
import com.mistaboom.essence_ascendance.attunement.AttunementWorkstations;
import com.mistaboom.essence_ascendance.gathering.GatheringSalvageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractContainerMenu.class)
public abstract class AttunementMenuMixin {
    @WrapMethod(method = "clicked")
    private void essenceAscendance$completedOperation(int slot, int button, ClickType type, Player actor, Operation<Void> original) {
        if (!(actor instanceof ServerPlayer player)) { original.call(slot, button, type, actor); return; }
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        var before = AttunementWorkstations.before(menu, slot, player);
        ItemStack ingredient = menu instanceof BrewingStandMenu ? menu.getSlot(3).getItem().copy() : ItemStack.EMPTY;
        original.call(slot, button, type, actor);
        AttunementWorkstations.completed(menu, player, before);
        if (menu instanceof BrewingStandMenu && menu == player.containerMenu && menu.stillValid(player)
                && ((AttunementBrewingMenuAccess) menu).essenceAscendance$brewingStand() instanceof AttunementBrewingOwner owner) {
            ItemStack after = menu.getSlot(3).getItem();
            if (AttunementWorkstations.consumed(ingredient, after) > 0) owner.essenceAscendance$invalidateBrewing();
            int added = AttunementWorkstations.consumed(after, ingredient);
            if (added > 0) owner.essenceAscendance$claimBrewing(player, after, added);
        }
    }

    @WrapMethod(method = "clickMenuButton")
    private boolean essenceAscendance$menuButton(Player actor, int buttonId, Operation<Boolean> original) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (GatheringSalvageService.clickMenuButton(menu, actor, buttonId)) return true;
        return original.call(actor, buttonId);
    }
}
