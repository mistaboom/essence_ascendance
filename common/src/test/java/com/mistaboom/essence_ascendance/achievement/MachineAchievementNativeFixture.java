package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.*;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.ReservoirService;
import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.essence.*;
import com.mistaboom.essence_ascendance.infuser.*;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.machine.MachineLink;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.pylon.*;
import com.mistaboom.essence_ascendance.verification.BlockEntityTestNbtCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Actual production commits in the explicitly disposable achievement native world.
 * Stock and equipment tiers are fixture preparation, never synthetic success receipts. */
public final class MachineAchievementNativeFixture {
    private static int checks;
    private static final List<Integer> IDS = List.of(24, 46, 49, 51, 56, 57, 63, 73, 190, 197, 199);
    private MachineAchievementNativeFixture() { }

    public static void run(ServerLevel level, ServerPlayer owner, ServerPlayer operator) throws Exception {
        checks = 0;
        var runtime = EssenceConfigManager.serverRuntime();
        var positions = new ArrayList<BlockPos>();
        //? if >=26.1.2
        /*long dayTime = level.getServer().overworld().getDefaultClockTime();*/
        //? if <26.1.2
        long dayTime = level.getServer().overworld().getDayTime();
        try {
            for (int id : IDS) {
                var advancement = operator.serverLevel().getServer().getAdvancements().get(AchievementService.resource(id));
                check(advancement != null && advancement.value().criteria().containsKey("achieved"),
                        "Native machine achievement resource decodes: " + id);
                //? if >=26.1.2
                /*check(!advancement.value().display().orElseThrow().getIcon().create().isEmpty(),*/
                //? if <26.1.2
                check(!advancement.value().display().orElseThrow().getIcon().isEmpty(),
                        "Native machine advancement resolves its actual existing ItemStack icon: " + id);
                revoke(operator, id); revoke(owner, id);
            }
            BlockPos sourcePos = new BlockPos(20, 120, 20);
            owner.setPos(21, 120, 20); operator.setPos(21, 120, 21);
            level.getChunkAt(sourcePos);
            positions.add(sourcePos);
            level.setBlock(sourcePos, EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState(), 3);
            var source = (EssenceCrucibleBlockEntity) level.getBlockEntity(sourcePos);
            check(source != null && source.bindOnPlacement(owner), "Fixture Crucible establishes real owner binding");
            var physical = source.physicalMachineId();
            var nbt = source.saveWithFullMetadata(level.registryAccess());
            BlockEntityTestNbtCompat.load(source, nbt, level.registryAccess());
            check(physical.equals(source.physicalMachineId()), "Crucible native NBT roundtrip preserves physical instance identity");
            var replacement = new EssenceCrucibleBlockEntity(sourcePos, source.getBlockState());
            replacement.setLevel(level);
            check(!physical.equals(replacement.physicalMachineId()), "Fresh replacement at the same block address has distinct physical identity");

            capacity(runtime, 0);
            check(source.setProcessingEnabled(operator, true) && !done(operator, 57),
                    "Explicit survival Start with zero configured active slots cannot grant vacuously");
            capacity(runtime, 2);
            var pylons = new ArrayList<EssencePylonBlockEntity>();
            for (int i = 0; i < 2; i++) {
                BlockPos pos = sourcePos.offset(2 + i * 2, 0, 0); positions.add(pos);
                level.setBlock(pos, EssencePylonContent.ESSENCE_PYLON.get().defaultBlockState(), 3);
                var pylon = (EssencePylonBlockEntity) level.getBlockEntity(pos);
                check(pylon != null && pylon.bindOnPlacement(owner), "Placed Pylon binds without granting an activation");
                operator.setPos(pos.getX(), pos.getY(), pos.getZ() + 1);
                check(!done(operator, 46), "A placed empty Pylon is insufficient");
                operator.setItemInHand(InteractionHand.MAIN_HAND, focus());
                pylon.installOrSwapFocus(operator, InteractionHand.MAIN_HAND);
                pylon.refreshLink(); pylons.add(pylon);
                check(pylon.hasInstalledFocus() && pylon.isLinkedTo(sourcePos), "Actual survival Focus installation creates a valid Crucible link");
                if (i == 0) {
                    check(done(operator, 46) && !done(owner, 46), "Pylon activation credits installing operator separately from owner");
                    check(!done(operator, 57), "One functioning Pylon does not fill a configured two-slot network");
                    revoke(operator, 46);
                    var swapped = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get());
                    EssenceFocusData.setTier(swapped, EssenceFocusTier.DORMANT);
                    operator.setItemInHand(InteractionHand.MAIN_HAND, swapped);
                    pylon.installOrSwapFocus(operator, InteractionHand.MAIN_HAND);
                    check(!done(operator, 46), "Continuous same-source active Focus swap does not replay an activation after revoke");
                    pylon.removeItemNoUpdate(EssencePylonBlockEntity.FOCUS_SLOT);
                    operator.setItemInHand(InteractionHand.MAIN_HAND, focus());
                    pylon.installOrSwapFocus(operator, InteractionHand.MAIN_HAND);
                    check(done(operator, 46), "Actual inactive-to-functioning Focus reinstall is a fresh activation");
                    revoke(operator, 46);
                }
            }
            operator.setPos(21, 120, 21);
            source.setProcessingEnabled(operator, true);
            check(done(operator, 57) && !done(owner, 57), "Explicit running Crucible transition sees all current functioning Pylon slots");
            revoke(operator, 57);
            capacity(runtime, 3); source.invalidateStructureCache(); source.setProcessingEnabled(operator, true);
            check(!done(operator, 57), "Increased live configuration capacity rejects a previously complete network");
            capacity(runtime, 1); source.invalidateStructureCache(); source.setProcessingEnabled(operator, true);
            check(done(operator, 57), "Fresh explicit running transition uses decreased positive current capacity");

            dissolveVariety(level, source, operator);
            stock(level, owner, 10000);
            var data = EssenceSavedData.get(operator.serverLevel().getServer()).getPlayerData(operator.getUUID());
            data.setCrucibleChannelSelection(CrucibleChannelSelection.ALL);
            long before = data.getAvailable(EssenceTypes.OFFENSE);
            check(source.startChanneling(operator), "Native personal channel is authorized and registered");
            for (int tick = 0; tick < 40 && !done(operator, 24); tick++)
                EssenceCrucibleChannelService.tick(operator.serverLevel().getServer(), operator.serverLevel().getServer().getTickCount() + 1000L + tick);
            check(done(operator, 24) && data.getAvailable(EssenceTypes.OFFENSE) > before && !done(owner, 24),
                    "Real committed reservoir-to-personal scheduler transfer earns only its recipient");
            source.stopChanneling();

            var infusers = new ArrayList<EssenceInfuserBlockEntity>();
            for (int i = 0; i < 3; i++) {
                var machine = infuser(level, source, owner, operator, sourcePos.offset(-2 - i * 2, 0, 0), positions);
                infusers.add(machine);
                carrier(level, machine, owner, operator, i, i == 0, true);
                if (i < 2) check(!done(operator, 56), "Fewer than three distinct completed Infuser instances cannot satisfy factory");
                if (i == 0) {
                    var record = EssenceSavedData.get(operator.serverLevel().getServer()).getPlayerData(operator.getUUID());
                    var restored = PlayerEssenceData.load(record.save());
                    var partials = restored.achievementProgress().save();
                    var entries = NbtVersionCompat.getCompound(partials, "entries");
                    check(partials.equals(record.achievementProgress().save()) && entries.contains("a051")
                                    && entries.contains("a056") && entries.contains("a197")
                                    && !done(operator, 51) && !done(operator, 56) && !done(operator, 197),
                            "Actual incomplete category, physical Infuser source and dimension facts survive full player-record native NBT roundtrip");
                }
            }
            check(done(operator, 49) && !done(owner, 49), "Actual Infuser committed completion credits captured job actor");
            check(done(operator, 56) && !done(owner, 56), "Three distinct actual Infuser completions share one physical Crucible supply");
            for (int category = 3; category < 6; category++) carrier(level, infusers.get(0), owner, operator, category, false, false);
            check(done(operator, 51) && !done(owner, 51), "Six completed positive filled Essentium target categories earn collection");
            check(!done(operator, 197), "Only Overworld completions cannot satisfy cross-dimension workshop");
            suppressedCompletion(level, infusers.get(0), owner, operator, true);
            suppressedCompletion(level, infusers.get(0), owner, operator, false);
            creativeStartBeforePayment(level, infusers.get(0), owner, operator);
            upgrade(level, infusers.get(0), owner, operator, false);
            check(done(operator, 63) && !done(owner, 63), "Actual final weapon upgrade credits operator while preserving another player's item ownership");
            upgrade(level, infusers.get(0), owner, operator, true);
            check(done(operator, 73) && !done(owner, 73), "Actual final rod upgrade independently earns rod milestone");

            var nether = operator.serverLevel().getServer().getLevel(Level.NETHER);
            check(nether != null, "Native fixture has Nether dimension");
            // Infuser operation authorization uses the player UUID and machine binding; the helper
            // performs native same-dimension menu interaction after the fixture player is transferred.
            move(operator, nether, 21, 120, 21);
            move(owner, nether, 21, 120, 20);
            var netherPos = new BlockPos(20, 120, 20); nether.getChunkAt(netherPos);
            nether.setBlock(netherPos, EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState(), 3);
            var netherSource = (EssenceCrucibleBlockEntity) nether.getBlockEntity(netherPos);
            try {
                check(netherSource.bindOnPlacement(owner), "Native Nether source binds normally");
                var netherPositions = new ArrayList<BlockPos>();
                try {
                    var machine = infuser(nether, netherSource, owner, operator, netherPos.offset(-2, 0, 0), netherPositions);
                    carrier(nether, machine, owner, operator, 0, false, false);
                    check(done(operator, 197) && !done(owner, 197), "Actual operations in different Overworld and Nether machines earn workshop");
                    nativeCompletionPersistence(operator);
                } finally { for (var pos : netherPositions) nether.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); }
            } finally { nether.setBlock(netherPos, Blocks.AIR.defaultBlockState(), 3); }
        } finally {
            // Shared fixture returns its real test players to the original native world.
            move(operator, level, 2, 120, 2);
            move(owner, level, 1, 120, 2);
            operator.serverLevel().getServer().getCommands().performPrefixedCommand(operator.serverLevel().getServer().createCommandSourceStack(), "time set " + dayTime);
            EssenceConfigManager.install(runtime);
            for (var pos : positions) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            EssenceCrucibleChannelService.clear(operator.serverLevel().getServer());
            operator.containerMenu = operator.inventoryMenu; owner.containerMenu = owner.inventoryMenu;
        }
        System.out.println("MachineAchievementNativeFixture: " + checks + " assertions passed; actual machine commits, native NBT roundtrips, live capacity changes, two native dimensions");
    }

    private static void dissolveVariety(ServerLevel level, EssenceCrucibleBlockEntity source, ServerPlayer actor) {
        command(level, "time set 10000");
        var candidates = new ArrayList<ItemStack>();
        for (var item : BuiltInRegistries.ITEM) {
            var stack = new ItemStack(item);
            var outputs = ItemEssenceMappingRegistry.resolveDissolution(stack);
            if (!outputs.isEmpty() && outputs.values().stream().allMatch(value -> value > 0)
                    && outputs.values().stream().map(BigInteger::valueOf).reduce(BigInteger.ZERO, BigInteger::add)
                    .compareTo(source.localFillLimit().multiply(ReservoirService.SCALE)) <= 0) candidates.add(stack);
        }
        candidates.sort(Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        check(candidates.size() >= 42, "Actual runtime mapping supports at least 42 distinct inputs fitting the real Crucible capacity");
        int mask = 0, consumed = 0;
        for (var stack : candidates) {
            if (consumed >= 42 && mask == 63) break;
            var saved = EssenceSavedData.get(actor.serverLevel().getServer());
            saved.reservoirs().administer(actor.getUUID(), zero());
            actor.setPos(source.getBlockPos().getX() + 1, source.getBlockPos().getY(), source.getBlockPos().getZ() + 1);
            var menu = new EssenceCrucibleMenu(91, actor.getInventory(), source); actor.containerMenu = menu;
            menu.setCarried(stack.copy()); menu.clicked(0, 0, ClickType.PICKUP, actor);
            check(menu.getCarried().isEmpty() && source.getItem(0).is(stack.getItem()), "Actual survival menu inserts supported distinct item");
            int ticks = source.structureStats().dissolutionTicksPerItem() + 1;
            for (int i = 0; i < ticks; i++) EssenceCrucibleBlockEntity.serverTick(level, source.getBlockPos(), source.getBlockState(), source);
            check(source.getItem(0).isEmpty(), "Production transaction consumed supported input before collection credit");
            var outputs = ItemEssenceMappingRegistry.resolveDissolution(stack);
            for (int i = 0; i < 6; i++) if (outputs.getOrDefault(EssenceTypes.ORDERED.get(i), 0L) > 0) mask |= 1 << i;
            consumed++;
            if (consumed == 1) {
                var day = NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(
                        EssenceSavedData.get(actor.serverLevel().getServer()).getPlayerData(actor.getUUID()).achievementProgress().save(), "entries"), "a190");
                var group = NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(day, "groups"), source.physicalMachineId().toString());
                int partial = NbtVersionCompat.getInt(group, "mask");
                check(partial > 0 && partial < 63 && !done(actor, 190),
                        "Actual first committed dissolution creates an incomplete native one-day category partial");
                command(level, "time set 9999");
                command(level, "time set 10000");
                day = NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(
                        EssenceSavedData.get(actor.serverLevel().getServer()).getPlayerData(actor.getUUID()).achievementProgress().save(), "entries"), "a190");
                check(NbtVersionCompat.getCompound(day, "groups").isEmpty() && !done(actor, 190),
                        "Native backward and forward time commands invalidate open day buckets even when no machine works between commands");
                mask = 0;
            }
            if (consumed == 2) {
                check(!done(actor, 190), "Next real dissolution cannot complete a day collection using categories invalidated by native time commands");
                command(level, "time set 24000"); mask = 0;
            }
            if (consumed == 3) {
                var day = NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(
                        EssenceSavedData.get(actor.serverLevel().getServer()).getPlayerData(actor.getUUID()).achievementProgress().save(), "entries"), "a190");
                var group = NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(day, "groups"), source.physicalMachineId().toString());
                check(NbtVersionCompat.getLong(day, "day_time") == 24000 && NbtVersionCompat.getInt(group, "mask") == mask,
                        "First actual dissolution after native calendar rollover starts from its own positive categories");
            }
        }
        System.out.println("Machine dissolution fixture: mask=" + mask + ", consumed=" + consumed
                + ", a190=" + NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(
                EssenceSavedData.get(actor.serverLevel().getServer()).getPlayerData(actor.getUUID()).achievementProgress().save(), "entries"), "a190"));
        check(mask == 63 && consumed >= 42 && done(actor, 190) && done(actor, 199),
                "Actual same-Crucible calendar-day dissolutions produce six positive categories and consume 42 distinct registry item identities");
    }

    private static EssenceInfuserBlockEntity infuser(ServerLevel level, EssenceCrucibleBlockEntity source,
            ServerPlayer owner, ServerPlayer actor, BlockPos pos, List<BlockPos> positions) {
        positions.add(pos); level.getChunkAt(pos);
        level.setBlock(pos, EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState(), 3);
        var machine = (EssenceInfuserBlockEntity) level.getBlockEntity(pos);
        check(machine != null && machine.bindOnPlacement(owner), "Actual Infuser establishes owner binding");
        actor.setPos(pos.getX(), pos.getY(), pos.getZ() + 1);
        machine.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT, focus());
        check(machine.setLink(actor, MachineLink.Mode.LOCKED, new MachineLink.Source(level.dimension().location(), source.getBlockPos())),
                "Actual Infuser locks a valid physical Crucible link");
        EssenceInfuserBlockEntity.serverTick(level, pos, machine.getBlockState(), machine);
        return machine;
    }

    private static void carrier(ServerLevel level, EssenceInfuserBlockEntity machine, ServerPlayer owner,
            ServerPlayer actor, int category, boolean reload, boolean changeOperator) throws Exception {
        actor.setPos(machine.getBlockPos().getX(), machine.getBlockPos().getY(), machine.getBlockPos().getZ() + 1);
        owner.setPos(actor.getX() + 1, actor.getY(), actor.getZ());
        machine.setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        if (machine.sourceEssence().equals(EssenceTypes.ORDERED.get(category))) machine.cycleSource(actor, 1);
        for (int i = 0; i < 6 && !machine.targetEssence().equals(EssenceTypes.ORDERED.get(category)); i++) machine.cycleTarget(actor, 1);
        check(machine.targetEssence().equals(EssenceTypes.ORDERED.get(category)), "Native selection sets desired Essentium output category");
        var menu = new EssenceInfuserMenu(92, actor.getInventory(), machine); actor.containerMenu = menu;
        menu.setCarried(new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()));
        menu.clicked(EssenceInfuserBlockEntity.INPUT_SLOT, 0, ClickType.PICKUP, actor);
        check(menu.getCarried().isEmpty() && actor.getUUID().equals(machine.machineBinding().operator()),
                "Actual survival Infuser menu input establishes explicit operator independently of owner");
        machine.setProcessingEnabled(actor, true);
        long required = machine.sourceRequired();
        stock(level, owner, Math.max(1_000_000, required));
        if (reload) {
            machine.setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT, new ItemStack(Items.BARRIER));
            check(machine.deliverChannelGrant(required) == 0 && !done(actor, 49) && !machine.getItem(0).isEmpty(),
                    "Output-blocked production job cannot debit, consume or grant");
            machine.setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
            var before = machine.saveWithFullMetadata(level.registryAccess());
            var original = machine.getItem(EssenceInfuserBlockEntity.INPUT_SLOT).copy();
            machine.setItemFromTransferSnapshot(EssenceInfuserBlockEntity.INPUT_SLOT, new ItemStack(Items.BARRIER));
            machine.setItemFromTransferSnapshot(EssenceInfuserBlockEntity.INPUT_SLOT, original);
            check(machine.saveWithFullMetadata(level.registryAccess()).equals(before) && !done(actor, 49),
                    "Provisional loader inventory snapshot followed by canceled transfer restores state without success");
        }
        check(required > 1 && machine.deliverChannelGrant(required - 1) == required - 1,
                "Actual timed job pays positive partial cost before completion");
        if (reload) {
            var id = machine.physicalMachineId();
            var nbt = machine.saveWithFullMetadata(level.registryAccess());
            BlockEntityTestNbtCompat.load(machine, nbt, level.registryAccess());
            check(id.equals(machine.physicalMachineId()) && !machine.getItem(0).isEmpty() && !done(actor, 49),
                    "Native mid-job NBT roundtrip preserves physical identity and paid partial without awarding");
        }
        if (changeOperator) machine.setProcessingEnabled(owner, true);
        check(machine.deliverChannelGrant(1) == 1, "Actual timed job commits final missing Essence payment");
        int ticks = machine.requiredProcessingTicks() + 1;
        for (int i = 0; i < ticks; i++) EssenceInfuserBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
        check(machine.getItem(0).isEmpty() && !machine.getItem(1).isEmpty()
                        && EssentiumCarrierData.read(machine.getItem(1)).orElseThrow().essence().equals(EssenceTypes.ORDERED.get(category)),
                "Production timed job consumes input and publishes correct filled carrier");
        if (reload) {
            check(done(actor, 49) && !done(owner, 49), "Changing current machine operator after partial payment does not steal committed job credit");
            revoke(actor, 49);
            EssenceInfuserBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
            machine.removeItemNoUpdate(EssenceInfuserBlockEntity.OUTPUT_SLOT);
            check(!done(actor, 49), "Duplicate completion tick and output retrieval cannot regrant revoked native completion");
            // A second real fresh job can re-earn after native administrative revocation.
            carrier(level, machine, owner, actor, category, false, false);
        }
    }

    private static void upgrade(ServerLevel level, EssenceInfuserBlockEntity machine, ServerPlayer owner,
            ServerPlayer actor, boolean rod) {
        actor.setPos(machine.getBlockPos().getX(), machine.getBlockPos().getY(), machine.getBlockPos().getZ() + 1);
        var work = new ItemStack(rod ? AscendanceItems.ASCENDANCE_FISHING_ROD.get() : AscendanceItems.ASCENDANCE_SWORD.get());
        EquipmentTierData.setTier(work, EquipmentTier.ASCENDANT);
        SoulboundEquipmentData.bind(work, owner.getUUID(), "Native item owner");
        var recipe = EquipmentInfusionRecipe.forWorkpiece(work).orElseThrow();
        machine.setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        machine.setItem(EssenceInfuserBlockEntity.INPUT_SLOT, work);
        machine.setItem(EssenceInfuserBlockEntity.COMPONENT_SLOT, new ItemStack(EssenceInfuserContent.ASCENDANCE_MATRIX.get(), recipe.matrixCount()));
        machine.setProcessingEnabled(actor, true); stock(level, owner, Math.max(10_000_000, recipe.totalRequired()));
        check(machine.deliverChannelGrant(recipe.totalRequired() - 1) == recipe.totalRequired() - 1, "Actual streamed final upgrade commits partial costs");
        EssenceInfuserBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
        check(!done(actor, rod ? 73 : 63) && EquipmentTierData.tier(machine.getItem(0)) == EquipmentTier.ASCENDANT,
                "Incomplete paid upgrade does not produce final-tier milestone");
        check(machine.deliverChannelGrant(1) == 1, "Actual streamed final upgrade commits its last required Essence");
        EssenceInfuserBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
        check(EquipmentTierData.tier(machine.getItem(1)) == EquipmentTier.TRANSCENDENT
                        && machine.getItem(0).isEmpty() && machine.getItem(3).isEmpty()
                        && SoulboundEquipmentData.isBoundTo(machine.getItem(1), owner.getUUID()),
                "Production final upgrade consumes Matrix, emits Transcendent output and retains distinct item owner");
    }

    @SuppressWarnings("unchecked")
    private static void suppressedCompletion(ServerLevel level, EssenceInfuserBlockEntity machine,
            ServerPlayer owner, ServerPlayer actor, boolean offline) throws Exception {
        revoke(actor, 49);
        actor.setPos(machine.getBlockPos().getX(), machine.getBlockPos().getY(), machine.getBlockPos().getZ() + 1);
        machine.setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        machine.setItem(EssenceInfuserBlockEntity.INPUT_SLOT, new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()));
        machine.setProcessingEnabled(actor, true);
        long required = machine.sourceRequired(); stock(level, owner, Math.max(required, 1_000_000));
        check(required > 0 && machine.deliverChannelGrant(required) == required,
                "Suppressed-completion scenario starts with a real fully paid survival Infuser job");
        var playersField = PlayerList.class.getDeclaredField("players"); playersField.setAccessible(true);
        var idsField = PlayerList.class.getDeclaredField("playersByUUID"); idsField.setAccessible(true);
        var online = (List<ServerPlayer>) playersField.get(actor.serverLevel().getServer().getPlayerList());
        var byId = (Map<UUID, ServerPlayer>) idsField.get(actor.serverLevel().getServer().getPlayerList());
        int previousIndex = online.indexOf(actor);
        check(previousIndex >= 0 && byId.get(actor.getUUID()) == actor, "Suppressed-completion actor begins in the actual native online player registry");
        if (offline) { online.remove(actor); byId.remove(actor.getUUID()); }
        else actor.setGameMode(GameType.CREATIVE);
        try {
            int ticks = machine.requiredProcessingTicks() + 1;
            for (int i = 0; i < ticks; i++) EssenceInfuserBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
            check(machine.getItem(0).isEmpty() && !machine.getItem(1).isEmpty() && !done(actor, 49),
                    offline ? "Actual job completed while actor is absent from native online registry earns no queued achievement"
                            : "Actual job completed while actor is creative earns no survival achievement");
        } finally {
            if (offline) { online.add(previousIndex, actor); byId.put(actor.getUUID(), actor); }
            else actor.setGameMode(GameType.SURVIVAL);
        }
        EssenceInfuserBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
        check(!done(actor, 49), "Restoring online survival eligibility cannot replay an already completed job");
        carrier(level, machine, owner, actor, 0, false, false);
        check(done(actor, 49), "A fresh real successful operation earns after online survival eligibility returns");
    }

    private static void nativeCompletionPersistence(ServerPlayer actor) throws Exception {
        for (int id : IDS) check(done(actor, id), "Machine completion exists before native disk persistence: " + id);
        actor.getAdvancements().save();
        var pathField = PlayerAdvancements.class.getDeclaredField("playerSavePath"); pathField.setAccessible(true);
        Path path = (Path) pathField.get(actor.getAdvancements());
        String json = Files.readString(path);
        for (int id : IDS) {
            check(json.contains(AchievementService.resource(id).toString()), "Real native advancement JSON contains stable machine resource: " + id);
            revoke(actor, id);
        }
        actor.getAdvancements().reload(actor.serverLevel().getServer().getAdvancements());
        for (int id : IDS) check(done(actor, id), "Native disk reload restores authoritative machine achievement completion: " + id);
    }

    private static void creativeStartBeforePayment(ServerLevel level, EssenceInfuserBlockEntity machine,
            ServerPlayer owner, ServerPlayer actor) {
        revoke(actor, 49);
        actor.setPos(machine.getBlockPos().getX(), machine.getBlockPos().getY(), machine.getBlockPos().getZ() + 1);
        machine.setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        machine.setItem(EssenceInfuserBlockEntity.INPUT_SLOT, new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(), 2));
        actor.setGameMode(GameType.CREATIVE);
        try { machine.setProcessingEnabled(actor, true); }
        finally { actor.setGameMode(GameType.SURVIVAL); }
        var persisted = machine.saveWithFullMetadata(level.registryAccess());
        BlockEntityTestNbtCompat.load(machine, persisted, level.registryAccess());
        check(actor.getUUID().equals(machine.machineBinding().operator()),
                "Native block-entity NBT roundtrip preserves the real accounting operator after creative Start");
        check(machine.processingEnabled(), "Creative Start can still arm ordinary machine work without survival achievement credit");
        long required = machine.sourceRequired(); stock(level, owner, Math.max(1_000_000, Math.multiplyExact(required, 3)));
        for (int job = 0; job < 2; job++) {
            check(required > 0 && machine.deliverChannelGrant(required) == required,
                    "Returning to survival after creative Start still permits the real production debit");
            int ticks = machine.requiredProcessingTicks() + 1;
            for (int tick = 0; tick < ticks; tick++) EssenceInfuserBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
            check(machine.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).getCount() == job + 1 && !done(actor, 49),
                    "Creative Start before first payment cannot earn survival credit on this or the next automatically started job");
        }
        check(machine.getItem(EssenceInfuserBlockEntity.INPUT_SLOT).isEmpty(), "Both uncredited real jobs consume their actual carrier inputs");
        machine.setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        machine.setItem(EssenceInfuserBlockEntity.INPUT_SLOT, new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()));
        machine.setProcessingEnabled(actor, true);
        check(machine.deliverChannelGrant(required) == required, "Fresh explicit survival Start permits a newly credited paid job");
        int ticks = machine.requiredProcessingTicks() + 1;
        for (int tick = 0; tick < ticks; tick++) EssenceInfuserBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
        check(!machine.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).isEmpty() && done(actor, 49),
                "Fresh successful survival Start restores achievement eligibility for the next actual completed operation");
    }

    private static ItemStack focus() {
        var focus = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get());
        EssenceFocusData.setTier(focus, EssenceFocusTier.TRANSCENDENT); return focus;
    }
    private static void move(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        //? if >=26.1.2
        /*player.teleportTo(level, x, y, z, Set.of(), 0, 0, false);*/
        //? if <26.1.2
        player.teleportTo(level, x, y, z, Set.of(), 0, 0);
    }
    private static void stock(ServerLevel level, ServerPlayer owner, long amount) {
        var values = new LinkedHashMap<net.minecraft.resources.ResourceLocation, BigInteger>();
        for (var essence : EssenceTypes.ORDERED) values.put(essence.id(), BigInteger.valueOf(amount).multiply(ReservoirService.SCALE));
        EssenceSavedData.get(level.getServer()).reservoirs().administer(owner.getUUID(), values);
    }
    private static Map<net.minecraft.resources.ResourceLocation, BigInteger> zero() {
        var values = new LinkedHashMap<net.minecraft.resources.ResourceLocation, BigInteger>();
        for (var essence : EssenceTypes.ORDERED) values.put(essence.id(), BigInteger.ZERO); return values;
    }
    private static void capacity(RuntimeBalanceDefinition original, int slots) {
        var json = original.toJson(); json.addProperty("maxActivePylons", slots);
        EssenceConfigManager.install(RuntimeBalanceDefinition.fromJson(json));
    }
    private static void command(ServerLevel level, String text) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), text);
    }
    private static boolean done(ServerPlayer player, int id) {
        var advancement = player.serverLevel().getServer().getAdvancements().get(AchievementService.resource(id));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
    private static void revoke(ServerPlayer player, int id) {
        var advancement = player.serverLevel().getServer().getAdvancements().get(AchievementService.resource(id));
        if (advancement != null) player.getAdvancements().revoke(advancement, "achieved");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
