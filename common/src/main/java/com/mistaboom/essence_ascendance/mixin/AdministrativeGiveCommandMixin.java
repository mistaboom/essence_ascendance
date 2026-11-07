package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gameplay.AdministrativeEquipmentMutation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import java.util.Collection;

/** /give may directly populate the currently selected empty hand. */
@Mixin(GiveCommand.class)
abstract class AdministrativeGiveCommandMixin {
    @WrapMethod(method = "giveItem")
    private static int essenceAscendance$give(CommandSourceStack source, ItemInput item,
                                             Collection<ServerPlayer> targets, int count, Operation<Integer> original) {
        try (var scope = AdministrativeEquipmentMutation.begin(targets)) { return original.call(source, item, targets, count); }
    }
}
