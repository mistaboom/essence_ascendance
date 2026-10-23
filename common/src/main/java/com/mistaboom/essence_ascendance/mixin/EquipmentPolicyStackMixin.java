package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.*;
import net.minecraft.core.component.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import java.lang.ref.WeakReference;
import java.util.Objects;

/** Only local revisions and a weak hand owner. No global inventory lookup from ItemStack. */
@Mixin(ItemStack.class)
abstract class EquipmentPolicyStackMixin implements EquipmentPolicyStack {
    @Unique private long essenceAscendance$policyRevision;
    @Unique private WeakReference<ServerPlayer> essenceAscendance$handOwner;
    public long essenceAscendance$equipmentRevision() { return essenceAscendance$policyRevision; }
    public void essenceAscendance$watchHand(ServerPlayer player) { essenceAscendance$handOwner = new WeakReference<>(player); }
    @Unique private ServerPlayer essenceAscendance$owner() {
        var player = essenceAscendance$handOwner == null ? null : essenceAscendance$handOwner.get();
        if (player != null && player.getMainHandItem() != (Object)this && player.getOffhandItem() != (Object)this) {
            essenceAscendance$handOwner = null;
            return null;
        }
        return player;
    }
    @Unique private void essenceAscendance$changed(boolean becameEmpty) {
        essenceAscendance$policyRevision++;
        var player = essenceAscendance$owner();
        if (player != null) TwoHandedEquipmentService.stackChanged(player, (ItemStack)(Object)this, becameEmpty);
    }
    @Unique private <T> T essenceAscendance$components(java.util.function.Supplier<T> mutation) {
        var player = essenceAscendance$owner();
        if (player == null) {
            // Unheld stacks need no semantic projection, owner lookup or classification.
            try { return mutation.get(); } finally { essenceAscendance$policyRevision++; }
        }
        Object before = TwoHandedEquipmentPolicy.semanticKey((ItemStack)(Object)this);
        TwoHandedEquipmentService.begin(player);
        try { return mutation.get(); }
        finally {
            try {
                if (!Objects.equals(before, TwoHandedEquipmentPolicy.semanticKey((ItemStack)(Object)this))) essenceAscendance$changed(false);
            } finally { TwoHandedEquipmentService.end(player); }
        }
    }
    @WrapMethod(method = "set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;")
    private <T> T essenceAscendance$component(DataComponentType<? super T> type, T value, Operation<T> original) {
        if (!TwoHandedEquipmentPolicy.relevant(type)) return original.call(type, value);
        return essenceAscendance$components(() -> original.call(type, value));
    }
    @WrapMethod(method = "remove")
    private <T> T essenceAscendance$remove(DataComponentType<? extends T> type, Operation<T> original) {
        if (!TwoHandedEquipmentPolicy.relevant(type)) return original.call(type);
        return essenceAscendance$components(() -> original.call(type));
    }
    @WrapMethod(method = {"applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V", "applyComponentsAndValidate"})
    private void essenceAscendance$patch(DataComponentPatch patch, Operation<Void> original) {
        essenceAscendance$components(() -> original.call(patch));
    }
    @WrapMethod(method = "applyComponents(Lnet/minecraft/core/component/DataComponentMap;)V")
    private void essenceAscendance$map(DataComponentMap map, Operation<Void> original) {
        essenceAscendance$components(() -> original.call(map));
    }
    @WrapMethod(method = "setCount")
    private void essenceAscendance$count(int count, Operation<Void> original) {
        boolean empty = ((ItemStack)(Object)this).isEmpty();
        original.call(count);
        boolean after = ((ItemStack)(Object)this).isEmpty();
        if (empty != after) essenceAscendance$changed(after);
    }
}
