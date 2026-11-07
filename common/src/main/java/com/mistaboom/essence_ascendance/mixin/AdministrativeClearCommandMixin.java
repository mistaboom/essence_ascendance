package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gameplay.AdministrativeEquipmentMutation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.ClearInventoryCommands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import java.util.Collection;
import java.util.function.Predicate;

/** Removing an incompatible offhand through /clear must not count as a survival equip. */
@Mixin(ClearInventoryCommands.class)
abstract class AdministrativeClearCommandMixin {
    @WrapMethod(method = "clearInventory")
    private static int essenceAscendance$clear(CommandSourceStack source, Collection<ServerPlayer> targets,
                                              Predicate<ItemStack> predicate, int maximum, Operation<Integer> original) {
        try (var scope = AdministrativeEquipmentMutation.begin(targets)) { return original.call(source, targets, predicate, maximum); }
    }
}
