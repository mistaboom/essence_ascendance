package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gameplay.AdministrativeEquipmentMutation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.ItemCommands;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import org.spongepowered.asm.mixin.Mixin;
import java.util.Collection;

/** All entity-targeting /item replace variants converge on setEntityItem in both native APIs. */
@Mixin(ItemCommands.class)
abstract class AdministrativeItemCommandMixin {
    @WrapMethod(method = "setEntityItem")
    private static int essenceAscendance$replace(CommandSourceStack source, Collection<? extends Entity> targets,
                                                int slot, ItemStack stack, Operation<Integer> original) {
        try (var scope = AdministrativeEquipmentMutation.begin(targets)) { return original.call(source, targets, slot, stack); }
    }

    @WrapMethod(method = "modifyEntityItem")
    private static int essenceAscendance$modify(CommandSourceStack source, Collection<? extends Entity> targets,
                                               int slot, Holder<LootItemFunction> modifier, Operation<Integer> original) {
        try (var scope = AdministrativeEquipmentMutation.begin(targets)) { return original.call(source, targets, slot, modifier); }
    }
}
