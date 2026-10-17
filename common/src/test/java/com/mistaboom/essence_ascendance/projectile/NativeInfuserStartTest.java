package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData;
import com.mistaboom.essence_ascendance.infuser.*;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.network.InfuserSoulbindNetworkService;
import com.mistaboom.essence_ascendance.network.InfuserSoulbindPayload;
import com.mistaboom.essence_ascendance.pylon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.UUID;

/** Actual menu receipts, ownership and input lifecycle in the initialized-loader machine fixture. */
public final class NativeInfuserStartTest {
    private static int checks;
    public static void run() throws Exception {
        checks = 0;
        var f = new ProjectileNativeInterceptionTest.Fixture();
        NativeGuardOutcomeTest.registry(f);
        f.level.memoryMachines = new HashMap<>(); f.level.memoryBlocks = new HashMap<>();
        try {
            var owner = f.player;
            var member = f.player(Vec3.ZERO, "infuser_starter");
            var outsider = f.player(Vec3.ZERO, "infuser_outsider");
            var machine = place(f, owner);
            machine.setItem(0, gear());
            check(!machine.processingEnabled() && !SoulboundEquipmentData.isSoulbound(machine.getItem(0)), "Inserting solo equipment does not bind or start it");
            var solo = menu(machine, owner);
            check(solo.clickMenuButton(owner, EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING) && receipt(solo) == null
                            && SoulboundEquipmentData.isBoundTo(machine.getItem(0), owner.getUUID()) && machine.processingEnabled(),
                    "Solo Start binds directly to the sole authorized machine owner without a prompt");
            var denied = menu(machine, outsider);
            check(!denied.clickMenuButton(outsider, EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING) && !denied.stillValid(outsider),
                    "Another solo player cannot operate a personal Infuser");
            ItemStack first = machine.removeItemNoUpdate(0);
            check(!machine.processingEnabled(), "Taking started equipment disarms its machine");
            machine.setItem(0, first.copy());
            check(!machine.processingEnabled(), "Already bound replacement equipment still requires its own Start");
            var team = f.saved.nativeMembership().create(owner.getUUID(), "Infuser confirmation");
            var request = f.saved.nativeMembership().request(member.getUUID(), team, true);
            f.saved.nativeMembership().decide(owner.getUUID(), request.token(), true);
            machine.canPlayerUse(member);
            machine.setItem(0, gear());
            machine.setProcessingEnabled(member, true);
            check(!machine.processingEnabled() && !SoulboundEquipmentData.isSoulbound(machine.getItem(0)),
                    "Direct or command Start cannot bypass team soulbinding confirmation");
            var current = menu(machine, member);
            check(!current.confirmSoulbind(member, UUID.randomUUID()), "Unrequested confirmation is rejected");
            UUID token = request(current, member);
            check(token != null && !machine.processingEnabled() && !SoulboundEquipmentData.isSoulbound(machine.getItem(0)),
                    "Team Start prepares a receipt without binding or spending");
            check(!InfuserSoulbindNetworkService.handle(member, new InfuserSoulbindPayload(current.containerId + 1, token)),
                    "A receipt for a different menu cannot start the machine");
            check(InfuserSoulbindNetworkService.handle(member, new InfuserSoulbindPayload(current.containerId, token))
                            && SoulboundEquipmentData.isBoundTo(machine.getItem(0), member.getUUID()),
                    "Matching confirmation binds to the clicking member, independently of the placer");
            check(!InfuserSoulbindNetworkService.handle(member, new InfuserSoulbindPayload(current.containerId, token)),
                    "A consumed soulbinding receipt cannot be replayed");
            var binding = SoulboundEquipmentData.read(machine.getItem(0)).orElseThrow();
            machine.setProcessingEnabled(owner, false); machine.setProcessingEnabled(owner, true);
            check(SoulboundEquipmentData.read(machine.getItem(0)).orElseThrow().equals(binding),
                    "Another teammate's Start preserves both the original soulbound owner and binding identity");
            machine.setItemFromTransferSnapshot(3, new ItemStack(EssenceInfuserContent.ASCENDANCE_MATRIX.get()));
            machine.finishItemTransfer(true, false);
            check(machine.processingEnabled(), "Committed component delivery does not disarm the already approved equipment");
            machine.setItem(3, ItemStack.EMPTY);

            var componentMenu = menu(machine, owner);
            componentMenu.setCarried(new ItemStack(EssenceInfuserContent.ASCENDANCE_MATRIX.get()));
            componentMenu.clicked(EssenceInfuserBlockEntity.COMPONENT_SLOT, 0,
                    net.minecraft.world.inventory.ClickType.PICKUP, owner);
            check(componentMenu.getCarried().isEmpty() && !machine.getItem(3).isEmpty()
                            && machine.processingEnabled(),
                    "Actual menu component insertion preserves approved equipment work");
            componentMenu.clicked(EssenceInfuserBlockEntity.COMPONENT_SLOT, 0,
                    net.minecraft.world.inventory.ClickType.PICKUP, owner);
            check(machine.getItem(3).isEmpty() && machine.processingEnabled()
                            && SoulboundEquipmentData.read(machine.getItem(0)).orElseThrow().equals(binding),
                    "Actual menu component removal preserves Start approval and original soulbinding");
            componentMenu.setCarried(ItemStack.EMPTY);

            machine.setItem(0, gear()); current = menu(machine, member); token = request(current, member);
            ItemStack identical = machine.removeItemNoUpdate(0); machine.setItem(0, identical);
            check(!current.confirmSoulbind(member, token) && !SoulboundEquipmentData.isSoulbound(machine.getItem(0)),
                    "Removing and replacing even equivalent equipment invalidates the prompt");
            current = menu(machine, member); token = request(current, member);
            machine.setItem(3, new ItemStack(EssenceInfuserContent.ASCENDANCE_MATRIX.get()));
            check(!current.confirmSoulbind(member, token), "Changing recipe components invalidates the prompt");
            current = menu(machine, member); token = request(current, member);
            var installedFocus = machine.removeItemNoUpdate(EssenceInfuserBlockEntity.FOCUS_SLOT);
            machine.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT, installedFocus);
            check(!current.confirmSoulbind(member, token), "Replacing even the same Focus invalidates the prompt");
            current = menu(machine, member); token = request(current, member);
            member.setPos(100, 0, 0);
            check(!current.confirmSoulbind(member, token) && !SoulboundEquipmentData.isSoulbound(machine.getItem(0)),
                    "Leaving interaction range rejects soulbinding at execution");
            member.setPos(0, 0, 0);
            current = menu(machine, member); token = request(current, member);
            var replacement = new EssenceInfuserBlockEntity(machine.getBlockPos(), machine.getBlockState());
            f.level.memoryMachines.put(machine.getBlockPos(), replacement);
            check(!current.confirmSoulbind(member, token) && !SoulboundEquipmentData.isSoulbound(machine.getItem(0)),
                    "A replacement block at the same address cannot execute the old menu's receipt");
            f.level.memoryMachines.put(machine.getBlockPos(), machine);
            current = menu(machine, member); token = request(current, member);
            f.saved.reservoirs().pause(new com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness(
                    com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.NOT_READY, "fixture", "Pending membership"));
            check(!current.confirmSoulbind(member, token) && !SoulboundEquipmentData.isSoulbound(machine.getItem(0)),
                    "Unresolved membership rejects a previously issued soulbinding receipt");
            com.mistaboom.essence_ascendance.team.MembershipPublication.publish(f.saved,
                    com.mistaboom.essence_ascendance.team.NativeMembershipService.READY,
                    f.saved.reservoirs().settledTeams().stream().map(com.mistaboom.essence_ascendance.team.MembershipRoster::from).toList());
            current = menu(machine, member); token = request(current, member);
            var entrant = f.player(Vec3.ZERO, "new_member");
            var join = f.saved.nativeMembership().request(entrant.getUUID(), team, true);
            f.saved.nativeMembership().decide(owner.getUUID(), join.token(), true);
            check(!current.confirmSoulbind(member, token), "Roster changes require a fresh soulbinding decision");
            current = menu(machine, member); token = request(current, member);
            current.removed(member);
            check(!current.confirmSoulbind(member, token), "Closing a menu discards its pending receipt");
            current = menu(machine, member); token = request(current, member);
            f.saved.nativeMembership().leave(member.getUUID());
            check(!current.confirmSoulbind(member, token) && !machine.processingEnabled(), "Departure revokes pending Start authorization");

            machine.setItem(0, ItemStack.EMPTY); machine.setItem(3, ItemStack.EMPTY);
            machine.setProcessingEnabled(owner, true);
            machine.setItem(0, new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()));
            check(machine.processingEnabled(), "Essentium input retains its existing continuous processing behavior");
            machine.setItem(0, ItemStack.EMPTY); machine.setProcessingEnabled(owner, true);
            machine.setItemFromTransferSnapshot(0, gear()); machine.finishItemTransfer(true);
            check(!machine.processingEnabled() && !SoulboundEquipmentData.isSoulbound(machine.getItem(0)),
                    "Committed Fabric equipment insertion disarms an already running machine");
            var saved = machine.saveWithFullMetadata(f.level.registryAccess()); saved.putBoolean("processing_enabled", true);
            machine.loadWithComponents(saved, f.level.registryAccess());
            check(!machine.processingEnabled(), "Legacy armed unbound equipment cannot bind itself on load");
            System.out.println("NativeInfuserStartTest: " + checks + " assertions passed");
        } finally { f.close(); }
    }

    /** Existing integration fixtures use the exact menu Start/confirm sequence for unbound team gear. */
    public static void start(EssenceInfuserBlockEntity machine, ServerPlayer player) throws Exception {
        var previous = player.containerMenu;
        var menu = menu(machine, player);
        try {
            if (!menu.clickMenuButton(player, EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING)) throw new AssertionError("Actual Infuser Start rejected");
            var receipt = receipt(menu);
            if (receipt != null && !InfuserSoulbindNetworkService.handle(player, new InfuserSoulbindPayload(menu.containerId, receipt)))
                throw new AssertionError("Actual Infuser confirmation rejected");
            if (!machine.processingEnabled()) throw new AssertionError("Actual Infuser did not start");
        } finally { menu.removed(player); player.containerMenu = previous; }
    }

    private static UUID request(EssenceInfuserMenu menu, ServerPlayer player) throws Exception {
        if (!menu.clickMenuButton(player, EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING)) throw new AssertionError("Start did not prepare confirmation");
        return receipt(menu);
    }
    private static UUID receipt(EssenceInfuserMenu menu) throws Exception {
        var field = EssenceInfuserMenu.class.getDeclaredField("pendingSoulbind"); field.setAccessible(true);
        Object pending = field.get(menu);
        if (pending == null) return null;
        var method = pending.getClass().getDeclaredMethod("receipt"); method.setAccessible(true);
        return (UUID) method.invoke(pending);
    }
    private static EssenceInfuserMenu menu(EssenceInfuserBlockEntity machine, ServerPlayer player) {
        var menu = new EssenceInfuserMenu(87, player.getInventory(), machine); player.containerMenu = menu; return menu;
    }
    private static ItemStack gear() { return new ItemStack(AscendanceItems.ASCENDANCE_SWORD.get()); }
    private static EssenceInfuserBlockEntity place(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player) {
        var machine = new EssenceInfuserBlockEntity(new BlockPos(1, 0, 0), EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState());
        machine.setLevel(f.level); f.level.memoryMachines.put(machine.getBlockPos(), machine); f.level.memoryBlocks.put(machine.getBlockPos(), machine.getBlockState());
        machine.bindOnPlacement(player);
        var focus = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get()); EssenceFocusData.setTier(focus, EssenceFocusTier.DORMANT); machine.setItem(2, focus);
        return machine;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
