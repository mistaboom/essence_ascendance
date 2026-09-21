package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Native item components, copied stacks and domain-wide wear conservation. */
public final class DurabilityDomainTest {
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        stack.set(DataComponents.MAX_DAMAGE, 1000);
        EquipmentMaintenanceData.setOverdurability(stack, 200, 200);
        int paid = 0;
        for (int i = 0; i < 100; i++) {
            paid += EquipmentMaintenanceData.resolveWear(stack, 1, 33);
            stack = stack.copy();
        }
        near(paid + EquipmentMaintenanceData.wearDebt(stack), 67);
        near(EquipmentMaintenanceData.overdurability(stack), 200);
        EquipmentMaintenanceData.resolveWear(stack, 1, 50);
        double debt = EquipmentMaintenanceData.wearDebt(stack);
        check(EquipmentMaintenanceData.resolveWear(stack, 1, 0) == 1, "No bonus uses native wear");
        near(EquipmentMaintenanceData.wearDebt(stack), debt);
        stack.set(DataComponents.MAX_DAMAGE, 2000);
        EquipmentMaintenanceData.clampOverdurability(stack, .2);
        near(EquipmentMaintenanceData.overdurability(stack), 200);
        near(EquipmentMaintenanceData.overdurabilityCapacity(stack), 200);
        stack.set(DataComponents.MAX_DAMAGE, 500);
        EquipmentMaintenanceData.clampOverdurability(stack, .2);
        near(EquipmentMaintenanceData.overdurability(stack), 100);
        near(EquipmentMaintenanceData.overdurabilityCapacity(stack), 100);
        EquipmentMaintenanceData.clearOverdurability(stack);
        near(EquipmentMaintenanceData.wearDebt(stack), debt);
        for (int capacity : new int[]{80, 1000, 9000}) {
            ItemStack item = new ItemStack(Items.IRON_SWORD); item.set(DataComponents.MAX_DAMAGE, capacity);
            int debit = 0;
            for (int i=0;i<200;i++) debit += EquipmentMaintenanceData.resolveWear(item, 1, 75);
            check(debit == 50, "Efficiency is independent of native capacity");
        }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("DurabilityDomainTest PASS");
    }
    private static void near(double a, double b) { check(Math.abs(a-b)<1e-7, a+" != "+b); }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
