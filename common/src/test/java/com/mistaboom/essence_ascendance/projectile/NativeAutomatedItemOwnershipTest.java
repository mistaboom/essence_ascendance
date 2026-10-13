package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.machine.BoundMachine;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.team.MembershipPublication;
import com.mistaboom.essence_ascendance.team.MembershipRoster;
import com.mistaboom.essence_ascendance.team.NativeMembershipService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Actual hopper and active loader adapter checks in the existing isolated machine fixture. */
public final class NativeAutomatedItemOwnershipTest {
    private static int checks;

    public static void run() throws Exception {
        checks = 0;
        var runtime = EssenceConfigManager.serverRuntime();
        EssenceConfigManager.install(RuntimeBalanceDefinition.bootstrap());
        var mappingState = ItemEssenceMappingRegistry.class.getDeclaredField("state");
        var mappingReport = ItemEssenceMappingRegistry.class.getDeclaredField("lastReload");
        mappingState.setAccessible(true); mappingReport.setAccessible(true);
        var priorMappings = mappingState.get(null); var priorReport = mappingReport.get(null);
        var f = new ProjectileNativeInterceptionTest.Fixture();
        try {
            NativeGuardOutcomeTest.registry(f);
            f.level.memoryMachines = new HashMap<>(); f.level.memoryBlocks = new HashMap<>();
            var owner = f.player;
            var solo = f.player(Vec3.ZERO, "automation_solo");
            var foreignOwner = f.player(Vec3.ZERO, "automation_other_team");
            var foreignMember = f.player(Vec3.ZERO, "automation_other_member");
            var otherPool = f.saved.nativeMembership().create(foreignOwner.getUUID(), "Other team");
            var otherRequest = f.saved.nativeMembership().request(foreignMember.getUUID(), otherPool, true);
            f.saved.nativeMembership().decide(foreignOwner.getUUID(), otherRequest.token(), true);
            f.saved.resolvePool(owner.getUUID()); f.saved.resolvePool(solo.getUUID());

            // Supply a real dissolution route so rejection cannot accidentally pass as an unmapped-item check.
            ItemEssenceMappingRegistry.installResolved(Map.of(
                    BuiltInRegistries.ITEM.getKey(AscendanceItems.ASCENDANCE_PICKAXE.get()),
                    Map.of(EssenceTypes.OFFENSE, 1_000_000L)),
                    new ItemEssenceMappingRegistry.LoadSummary(0, 0, 0, 0, 0, List.of()), () -> { });
            var crucible = new EssenceCrucibleBlockEntity(new BlockPos(1, 0, 0),
                    EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState());
            var infuser = new EssenceInfuserBlockEntity(new BlockPos(0, 0, 1),
                    EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState());
            install(f, crucible); install(f, infuser);
            crucible.bindOnPlacement(owner); infuser.bindOnPlacement(owner);
            var owned = gear(owner.getUUID()); var otherSolo = gear(solo.getUUID());
            var otherTeam = gear(foreignOwner.getUUID()); var otherTeamMember = gear(foreignMember.getUUID());
            var unbound = new ItemStack(AscendanceItems.ASCENDANCE_PICKAXE.get());
            check(EssenceCrucibleBlockEntity.isValidNewInput(owned), "Ownership test workpiece has an actual dissolution recipe");
            for (var machine : List.<BlockEntity>of(crucible, infuser)) {
                var inventory = (WorldlyContainer) machine;
                check(inventory.canPlaceItem(0, otherSolo), "Manual workpiece validation remains recipe-based");
                check(inventory.canPlaceItemThroughFace(0, owned, Direction.UP), "Personal machine accepts its owner's gear");
                check(!inventory.canPlaceItemThroughFace(0, otherSolo, Direction.UP), "Personal machine rejects another solo player's gear");
                check(!inventory.canPlaceItemThroughFace(0, otherTeam, Direction.UP)
                        && !inventory.canPlaceItemThroughFace(0, otherTeamMember, Direction.UP), "Personal machine rejects another team's owners and members");
                check(inventory.canPlaceItemThroughFace(0, unbound, Direction.UP), "Unbound equipment has no invented owner restriction");
                var state = snapshot(machine);
                check(ItemStack.matches(HopperBlockEntity.addItem(null, inventory, otherSolo.copy(), Direction.UP), otherSolo)
                        && snapshot(machine).equals(state), "Actual vanilla hopper rejects foreign gear without changing machine state");
                check(HopperBlockEntity.addItem(null, inventory, owned.copy(), Direction.UP).isEmpty()
                        && SoulboundEquipmentData.isBoundTo(inventory.getItem(0), owner.getUUID()), "Actual vanilla hopper accepts owner gear without changing its binding");
                inventory.setItem(0, ItemStack.EMPTY);
                platformInsertion(machine, owned, otherSolo);
                platformInsertion(machine, unbound, otherTeam);
            }

            var pool = f.saved.nativeMembership().create(owner.getUUID(), "Receiving team");
            var request = f.saved.nativeMembership().request(solo.getUUID(), pool, true);
            f.saved.nativeMembership().decide(owner.getUUID(), request.token(), true);
            for (var machine : List.<BlockEntity>of(crucible, infuser)) {
                var bound = (BoundMachine) machine;
                var state = snapshot(machine);
                check(!bound.canAcceptAutomatedStack(owned) && snapshot(machine).equals(state),
                        "Stale personal binding fails closed until normal refresh without simulation changes");
                check(bound.authorize(owner, com.mistaboom.essence_ascendance.machine.MachineAction.USE),
                        "Normal authorized use refreshes a personal machine into its owner's team");
                var inventory = (WorldlyContainer) machine;
                check(inventory.canPlaceItemThroughFace(0, owned, Direction.UP)
                        && inventory.canPlaceItemThroughFace(0, otherSolo, Direction.UP), "Team machine accepts every current teammate's gear");
                check(!inventory.canPlaceItemThroughFace(0, otherTeam, Direction.UP)
                        && !inventory.canPlaceItemThroughFace(0, otherTeamMember, Direction.UP), "Team machine rejects other teams' gear");
                platformInsertion(machine, otherSolo, otherTeam);
            }
            f.saved.nativeMembership().leave(solo.getUUID());
            for (var machine : List.<BlockEntity>of(crucible, infuser)) {
                check(!((WorldlyContainer) machine).canPlaceItemThroughFace(0, otherSolo, Direction.UP),
                        "Departed teammate's equipment is rejected immediately");
            }
            f.saved.reservoirs().pause(new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "fixture", "Pending roster"));
            for (var machine : List.<BlockEntity>of(crucible, infuser)) {
                check(!((BoundMachine) machine).canAcceptAutomatedStack(owned), "Unresolved membership fails closed for soulbound gear");
                check(((BoundMachine) machine).canAcceptAutomatedStack(unbound), "Membership unavailability adds no ownership to unbound materials");
            }
            MembershipPublication.publish(f.saved, NativeMembershipService.READY,
                    f.saved.reservoirs().settledTeams().stream().map(MembershipRoster::from).toList());
            infuser.setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT, otherTeam.copy());
            platformExtraction(infuser, otherTeam);
            var focus = new ItemStack(com.mistaboom.essence_ascendance.pylon.EssencePylonContent.ESSENCE_FOCUS.get());
            com.mistaboom.essence_ascendance.pylon.EssenceFocusData.setTier(focus,
                    com.mistaboom.essence_ascendance.pylon.EssenceFocusTier.DORMANT);
            infuser.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT, focus);
            infuser.setItem(EssenceInfuserBlockEntity.INPUT_SLOT, owned.copy());
            infuser.setProcessingEnabled(owner, true);
            check(infuser.processingEnabled(), "Owner explicitly starts already bound equipment");
            platformComponentTopUp(infuser);
            platformRepairComponents(infuser, owned);
            check(!crucible.canTakeItemThroughFace(0, owned, Direction.DOWN), "Crucible retains its existing insertion-only automation rule");
            check(SoulboundEquipmentData.isBoundTo(owned, owner.getUUID())
                    && SoulboundEquipmentData.isBoundTo(otherSolo, solo.getUUID())
                    && !SoulboundEquipmentData.isSoulbound(unbound), "All transfer probes preserve original binding identities");
            System.out.println("NativeAutomatedItemOwnershipTest: " + checks + " actual automation ownership assertions passed");
        } finally {
            f.close();
            mappingState.set(null, priorMappings); mappingReport.set(null, priorReport);
            if (runtime == null) EssenceConfigManager.reset(); else EssenceConfigManager.install(runtime);
        }
    }

    private static void platformInsertion(BlockEntity machine, ItemStack accepted, ItemStack rejected) throws Exception {
        var inventory = (WorldlyContainer) machine;
        var state = snapshot(machine);
        var sourceAccepted = accepted.copy(); var sourceRejected = rejected.copy();
        Object adapter = adapter(machine);
        if (fabric()) {
            var variant = Class.forName("net.fabricmc.fabric.api.transfer.v1.item.ItemVariant");
            var transaction = Class.forName("net.fabricmc.fabric.api.transfer.v1.transaction.Transaction");
            var context = Class.forName("net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext");
            var insert = adapter.getClass().getMethod("insert", variant, long.class, context); insert.setAccessible(true);
            var good = variant.getMethod("of", ItemStack.class).invoke(null, accepted);
            var bad = variant.getMethod("of", ItemStack.class).invoke(null, rejected);
            try (var tx = (AutoCloseable) transaction.getMethod("openOuter").invoke(null)) {
                check((long) insert.invoke(adapter, bad, 1L, tx) == 0, "Fabric rejects foreign equipment within an open transaction");
                transaction.getMethod("commit").invoke(tx);
            }
            check(snapshot(machine).equals(state), "Rejected Fabric insertion has no inventory, binding, recipient, or timer effects");
            try (var tx = (AutoCloseable) transaction.getMethod("openOuter").invoke(null)) {
                check((long) insert.invoke(adapter, good, 1L, tx) == 1, "Fabric accepts eligible workpieces provisionally");
            }
            check(snapshot(machine).equals(state), "Aborted Fabric transfer restores exact machine state");
            try (var tx = (AutoCloseable) transaction.getMethod("openOuter").invoke(null)) {
                check((long) insert.invoke(adapter, good, 1L, tx) == 1, "Fabric accepts eligible workpiece on commit");
                transaction.getMethod("commit").invoke(tx);
            }
        } else {
            var insert = adapter.getClass().getMethod("insertItem", int.class, ItemStack.class, boolean.class); insert.setAccessible(true);
            var valid = adapter.getClass().getMethod("isItemValid", int.class, ItemStack.class); valid.setAccessible(true);
            check(!(boolean) valid.invoke(adapter, 0, rejected) && (boolean) valid.invoke(adapter, 0, accepted), "NeoForge validity uses the same owner boundary");
            check(ItemStack.matches((ItemStack) insert.invoke(adapter, 0, rejected, true), rejected)
                    && ItemStack.matches((ItemStack) insert.invoke(adapter, 0, rejected, false), rejected), "NeoForge simulation and execution both reject foreign equipment");
            check(snapshot(machine).equals(state), "Rejected NeoForge insertion has no machine state effects");
            check(((ItemStack) insert.invoke(adapter, 0, accepted, true)).isEmpty()
                    && snapshot(machine).equals(state), "NeoForge accepted simulation preserves exact machine state");
            check(((ItemStack) insert.invoke(adapter, 0, accepted, false)).isEmpty(), "NeoForge execution accepts eligible equipment");
        }
        check(ItemStack.matches(inventory.getItem(0), accepted), "Successful platform insertion preserves complete workpiece data");
        check(ItemStack.matches(accepted, sourceAccepted) && ItemStack.matches(rejected, sourceRejected), "Platform calls never alter caller-held stacks");
        inventory.setItem(0, ItemStack.EMPTY);
    }

    private static void platformExtraction(EssenceInfuserBlockEntity machine, ItemStack foreignGear) throws Exception {
        Object adapter = adapter(machine); var state = snapshot(machine);
        if (fabric()) {
            var variant = Class.forName("net.fabricmc.fabric.api.transfer.v1.item.ItemVariant");
            var transaction = Class.forName("net.fabricmc.fabric.api.transfer.v1.transaction.Transaction");
            var context = Class.forName("net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext");
            var extract = adapter.getClass().getMethod("extract", variant, long.class, context); extract.setAccessible(true);
            Object resource = variant.getMethod("of", ItemStack.class).invoke(null, foreignGear);
            try (var tx = (AutoCloseable) transaction.getMethod("openOuter").invoke(null)) {
                check((long) extract.invoke(adapter, resource, 1L, tx) == 1, "Fabric can still extract another team's soulbound output");
            }
            check(snapshot(machine).equals(state), "Aborted foreign-output extraction preserves state");
            try (var tx = (AutoCloseable) transaction.getMethod("openOuter").invoke(null)) {
                extract.invoke(adapter, resource, 1L, tx); transaction.getMethod("commit").invoke(tx);
            }
        } else {
            var extract = adapter.getClass().getMethod("extractItem", int.class, int.class, boolean.class); extract.setAccessible(true);
            check(ItemStack.matches((ItemStack) extract.invoke(adapter, 1, 1, true), foreignGear)
                    && snapshot(machine).equals(state), "NeoForge can simulate extraction of another team's gear without mutations");
            check(ItemStack.matches((ItemStack) extract.invoke(adapter, 1, 1, false), foreignGear), "NeoForge can still extract another team's soulbound output");
        }
        check(machine.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).isEmpty(), "Foreign gear is never prevented from leaving the Infuser output");
    }

    private static void platformComponentTopUp(EssenceInfuserBlockEntity machine) throws Exception {
        Object adapter = adapter(machine); var state = snapshot(machine);
        var matrix = new ItemStack(EssenceInfuserContent.ASCENDANCE_MATRIX.get());
        if (fabric()) {
            var variant = Class.forName("net.fabricmc.fabric.api.transfer.v1.item.ItemVariant");
            var transaction = Class.forName("net.fabricmc.fabric.api.transfer.v1.transaction.Transaction");
            var context = Class.forName("net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext");
            var insert = adapter.getClass().getMethod("insert", variant, long.class, context); insert.setAccessible(true);
            Object resource = variant.getMethod("of", ItemStack.class).invoke(null, matrix);
            try (var tx = (AutoCloseable) transaction.getMethod("openOuter").invoke(null)) {
                check((long) insert.invoke(adapter, resource, 1L, tx) == 1, "Fabric provisionally accepts a component for explicitly started gear");
            }
            check(snapshot(machine).equals(state), "Aborted Fabric component top-up preserves active work exactly");
            try (var tx = (AutoCloseable) transaction.getMethod("openOuter").invoke(null)) {
                insert.invoke(adapter, resource, 1L, tx); transaction.getMethod("commit").invoke(tx);
            }
        } else {
            var insert = adapter.getClass().getMethod("insertItem", int.class, ItemStack.class, boolean.class); insert.setAccessible(true);
            check(((ItemStack) insert.invoke(adapter, 2, matrix, true)).isEmpty()
                    && snapshot(machine).equals(state), "NeoForge component simulation preserves active work exactly");
            check(((ItemStack) insert.invoke(adapter, 2, matrix, false)).isEmpty(), "NeoForge accepts an explicit gear job's component");
        }
        check(machine.processingEnabled() && machine.getItem(EssenceInfuserBlockEntity.COMPONENT_SLOT).is(matrix.getItem()),
                "Automatic component top-up does not require another Start for the same equipment");
    }

    private static void platformRepairComponents(EssenceInfuserBlockEntity machine, ItemStack owned) throws Exception {
        machine.setItem(EssenceInfuserBlockEntity.COMPONENT_SLOT, ItemStack.EMPTY);
        var fractured = owned.copy();
        com.mistaboom.essence_ascendance.equipment.FracturedEquipmentData.markFractured(fractured);
        machine.setItem(EssenceInfuserBlockEntity.INPUT_SLOT, fractured);
        int limit = machine.repairLatentIngotRequired();
        check(limit > 0 && limit < 64, "Repair fixture has a bounded Latent component requirement");
        var offered = new ItemStack(EssenceInfuserContent.LATENT_INGOT.get(), limit + 3);
        var remainder = HopperBlockEntity.addItem(null, machine, offered.copy(), Direction.UP);
        check(machine.getItem(EssenceInfuserBlockEntity.COMPONENT_SLOT).isEmpty()
                        && remainder.getCount() == offered.getCount(),
                "Vanilla bulk insertion rejects a batch that cannot fit and conserves all items");
        for (int i = 0; i < limit; i++)
            check(HopperBlockEntity.addItem(null, machine, offered.copyWithCount(1), Direction.UP).isEmpty(),
                    "Vanilla one-item hopper transfers fill legitimate repair component room");
        check(HopperBlockEntity.addItem(null, machine, offered.copy(), Direction.UP).getCount() == offered.getCount(),
                "A full repair component slot rejects further hopper insertion");
        check(HopperBlockEntity.addItem(null, machine, offered.copyWithCount(1), Direction.UP).getCount() == 1
                        && machine.getItem(EssenceInfuserBlockEntity.COMPONENT_SLOT).getCount() == limit,
                "Vanilla direct stack growth cannot overfill a repair component slot");
        for (int already : new int[]{0, limit - 1}) {
            machine.setItem(EssenceInfuserBlockEntity.COMPONENT_SLOT,
                    already == 0 ? ItemStack.EMPTY : new ItemStack(EssenceInfuserContent.LATENT_INGOT.get(), already));
            Object adapter = adapter(machine); var state = snapshot(machine);
            int room = limit - already;
            if (fabric()) {
                var variant = Class.forName("net.fabricmc.fabric.api.transfer.v1.item.ItemVariant");
                var transaction = Class.forName("net.fabricmc.fabric.api.transfer.v1.transaction.Transaction");
                var context = Class.forName("net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext");
                var insert = adapter.getClass().getMethod("insert", variant, long.class, context); insert.setAccessible(true);
                Object resource = variant.getMethod("of", ItemStack.class).invoke(null, offered);
                try (var tx = (AutoCloseable) transaction.getMethod("openOuter").invoke(null)) {
                    check((long) insert.invoke(adapter, resource, (long) offered.getCount(), tx) == room,
                            "Fabric provisional insertion reports only actual repair component room");
                }
                check(snapshot(machine).equals(state), "Aborted bounded repair insertion restores exact state");
                try (var tx = (AutoCloseable) transaction.getMethod("openOuter").invoke(null)) {
                    check((long) insert.invoke(adapter, resource, (long) offered.getCount(), tx) == room,
                            "Fabric committed insertion respects repair component room");
                    transaction.getMethod("commit").invoke(tx);
                }
            } else {
                var insert = adapter.getClass().getMethod("insertItem", int.class, ItemStack.class, boolean.class); insert.setAccessible(true);
                check(((ItemStack) insert.invoke(adapter, 2, offered, true)).getCount() == offered.getCount() - room
                                && snapshot(machine).equals(state),
                        "NeoForge simulation reports bounded repair insertion without mutation");
                check(((ItemStack) insert.invoke(adapter, 2, offered, false)).getCount() == offered.getCount() - room,
                        "NeoForge returns every repair component it did not actually store");
            }
            check(machine.getItem(EssenceInfuserBlockEntity.COMPONENT_SLOT).getCount() == limit
                            && offered.getCount() == limit + 3,
                    "Bounded repair transfer preserves the slot limit and caller-held stack");
        }
    }

    private static Object adapter(BlockEntity machine) throws Exception {
        boolean crucible = machine instanceof EssenceCrucibleBlockEntity;
        String type = "Essence" + (crucible ? "Crucible" : "Infuser")
                + (fabric() ? "FabricItemStorage" : "NeoForgeItemHandler");
        var clazz = Class.forName("com.mistaboom.essence_ascendance." + (fabric() ? "fabric." : "neoforge.") + type);
        var constructor = crucible ? clazz.getDeclaredConstructor(EssenceCrucibleBlockEntity.class, Direction.class)
                : clazz.getDeclaredConstructor(EssenceInfuserBlockEntity.class);
        constructor.setAccessible(true);
        return crucible ? constructor.newInstance(machine, Direction.UP) : constructor.newInstance(machine);
    }

    private static boolean fabric() { return dev.architectury.platform.Platform.isModLoaded("fabricloader"); }
    private static ItemStack gear(UUID owner) {
        var gear = new ItemStack(AscendanceItems.ASCENDANCE_PICKAXE.get());
        SoulboundEquipmentData.bind(gear, owner, ""); return gear;
    }
    private static CompoundTag snapshot(BlockEntity machine) { return machine.saveWithFullMetadata(machine.getLevel().registryAccess()); }
    private static void install(ProjectileNativeInterceptionTest.Fixture f, BlockEntity machine) {
        machine.setLevel(f.level); f.level.memoryMachines.put(machine.getBlockPos(), machine);
        f.level.memoryBlocks.put(machine.getBlockPos(), machine.getBlockState());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
