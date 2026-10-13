package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.*;
import com.mistaboom.essence_ascendance.essence.*;
import com.mistaboom.essence_ascendance.infuser.*;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;

import java.util.*;

/** Registered items, real custom recipes, NBT roundtrips and native Crucible consumption. */
public final class NativeCarrierCompatibilityTest {
    private static int checks;
    private static final String ROOT = "essence_ascendance_essentium";
    private NativeCarrierCompatibilityTest() { }

    static void run(ProjectileNativeInterceptionTest.Fixture f) {
        var previous = EssenceConfigManager.serverRuntime();
        try {
            var oldProfile = capacity(5400);
            var nugget = (EssentiumItem) EssenceInfuserContent.ESSENTIUM_NUGGET.get();
            var ingot = (EssentiumItem) EssenceInfuserContent.ESSENTIUM_INGOT.get();
            var block = (EssentiumItem) EssenceInfuserContent.ESSENTIUM_BLOCK.get();
            var nuggetCompact = new EssentiumNuggetCompactingRecipe(CraftingBookCategory.MISC);
            var nuggetSplit = new EssentiumNuggetUncompactingRecipe(CraftingBookCategory.MISC);
            var blockCompact = new EssentiumBlockCompactingRecipe(CraftingBookCategory.MISC);
            var blockSplit = new EssentiumBlockUncompactingRecipe(CraftingBookCategory.MISC);
            for (long newCapacity : new long[]{2700, 10800}) {
                var newProfile = capacity(newCapacity);
                for (var essence : EssenceRegistry.values()) for (var grade : EssenceFocusTier.values()) {
                    EssenceConfigManager.install(oldProfile);
                    var minted = List.of(EssentiumCarrierData.createFull(nugget, essence, grade),
                            EssentiumCarrierData.createFull(ingot, essence, grade), EssentiumCarrierData.createFull(block, essence, grade));
                    EssenceConfigManager.install(newProfile);
                    for (boolean legacy : new boolean[]{false, true}) {
                        var old = minted.stream().map(s -> legacy ? legacy(s) : s.copy()).toList();
                        long[] amounts = {600, 5400, 48600};
                        for (int i = 0; i < old.size(); i++) {
                            var saved = old.get(i).save(f.level.registryAccess());
                            var restored = ItemStack.parse(f.level.registryAccess(), saved).orElseThrow();
                            check(ItemStack.matches(old.get(i), restored), "Native item save/load preserves original bytes and count");
                            check(EssentiumCarrierData.readValidated(restored).orElseThrow().amount() == amounts[i],
                                    "Old denomination remains usable after capacity change " + newCapacity + "/legacy=" + legacy);
                            check(EssenceCrucibleBlockEntity.isValidNewInput(restored), "Native Crucible accepts old saved face value");
                        }
                        var compressed = craft(nuggetCompact, old.get(0), 9, f);
                        check(amount(compressed) == 5400 && compressed.getCount() == 1, "Nine old nuggets retain exact total value");
                        var split = craft(nuggetSplit, compressed, 1, f);
                        check(amount(split) == 600 && split.getCount() == 9, "Old ingot splits into its own denomination");
                        var compressedBlock = craft(blockCompact, old.get(1), 9, f);
                        check(amount(compressedBlock) == 48600, "Nine old ingots retain exact total value");
                        var splitBlock = craft(blockSplit, old.get(2), 1, f);
                        check(amount(splitBlock) == 5400 && splitBlock.getCount() == 9, "Old block splits into its own denomination");
                        check(amount(craft(blockCompact, splitBlock, 9, f)) == amount(old.get(2)), "Block roundtrip conserves exact value");
                        var mixed = copies(old.get(0), 9);
                        mixed.set(8, EssentiumCarrierData.createFull(nugget, essence, grade));
                        var mixedInput = CraftingInput.of(3, 3, mixed);
                        check(!nuggetCompact.matches(mixedInput, f.level) && nuggetCompact.assemble(mixedInput, f.level.registryAccess()).isEmpty(),
                                "Mixed old/new face values cannot be repriced through compression");
                        var entity = new EssentiumBlockEntity(new BlockPos(0, 0, 0), EssenceInfuserContent.ESSENTIUM_BLOCK_BLOCK.get().defaultBlockState());
                        entity.setCarrier(old.get(2));
                        var restoredEntity = new EssentiumBlockEntity(entity.getBlockPos(), entity.getBlockState());
                        restoredEntity.loadWithComponents(entity.saveWithFullMetadata(f.level.registryAccess()), f.level.registryAccess());
                        check(ItemStack.matches(old.get(2), restoredEntity.carrierStack()), "Placed block save/load retains its exact denomination");
                    }
                }
            }
            EssenceConfigManager.install(oldProfile);
            var full = EssentiumCarrierData.createFull(ingot, EssenceTypes.OFFENSE, EssenceFocusTier.DORMANT);
            var fullNugget = EssentiumCarrierData.createFull(nugget, EssenceTypes.OFFENSE, EssenceFocusTier.DORMANT);
            var partial = full.copy();
            EssentiumCarrierData.write(partial, new EssentiumCarrierData.Value(EssenceTypes.OFFENSE, EssenceFocusTier.DORMANT, 2700, 5400));
            check(EssentiumCarrierData.readValidated(partial).orElseThrow().amount() == 2700, "Partial declared carrier retains exact dissolution value");
            check(nuggetSplit.assemble(CraftingInput.of(1, 1, List.of(partial)), f.level.registryAccess()).isEmpty(), "Partial declared carrier cannot turn into full outputs");
            var legacyPartial = legacy(partial);
            check(amount(craft(nuggetSplit, legacyPartial, 1, f)) * 9 == 2700, "Ambiguous legacy amount is authoritative and transforms only by exact conservation");
            for (long invalid : new long[]{0, -1, Long.MAX_VALUE}) {
                var bad = edit(full, t -> t.putLong("amount", invalid));
                check(EssentiumCarrierData.readValidated(bad).isEmpty() && !EssenceCrucibleBlockEntity.isValidNewInput(bad), "Invalid/overflow amount is rejected");
            }
            check(EssentiumCarrierData.readValidated(edit(full, t -> t.putLong("ingot_denomination", Long.MAX_VALUE))).isEmpty(), "Denomination overflow is rejected");
            check(EssentiumCarrierData.readValidated(edit(full, t -> t.putLong("ingot_denomination", 5401))).isEmpty(), "Nonintegral form family is rejected");
            check(EssentiumCarrierData.readValidated(edit(full, t -> t.putInt("schema", 2))).isEmpty(), "Unknown carrier schema is rejected");
            check(EssentiumCarrierData.readValidated(edit(full, t -> t.putDouble("amount", 5400.5))).isEmpty(), "Fractional/wrong-type saved amount is rejected without truncation");
            var wrongForm = fullNugget.copy(); wrongForm.set(DataComponents.CUSTOM_DATA, full.get(DataComponents.CUSTOM_DATA));
            check(EssentiumCarrierData.readValidated(wrongForm).isEmpty(), "A new ingot tag cannot overfill a nugget of the same denomination");
            var tooLarge = legacy(full); tooLarge = edit(tooLarge, t -> t.putLong("amount", EssentiumCarrierData.MAX_STORED_AMOUNT));
            check(nuggetSplit.assemble(CraftingInput.of(1, 1, List.of(tooLarge)), f.level.registryAccess()).isEmpty(), "Legacy out-of-family values cannot overflow crafting");
            EssenceConfigManager.install(capacity(2700));
            nativeDissolution(f, legacy(fullNugget));
            System.out.println("Native carrier compatibility checks passed: " + checks);
        } finally { if (previous == null) EssenceConfigManager.reset(); else EssenceConfigManager.install(previous); }
    }

    private static RuntimeBalanceDefinition capacity(long amount) {
        var json = RuntimeBalanceDefinition.bootstrap().toJson();
        json.getAsJsonObject("infuser").getAsJsonObject("grades").entrySet()
                .forEach(e -> e.getValue().getAsJsonObject().addProperty("ingotCapacity", amount));
        return RuntimeBalanceDefinition.fromJson(json);
    }
    private static long amount(ItemStack stack) { return EssentiumCarrierData.readValidated(stack).orElseThrow().amount(); }
    private static List<ItemStack> copies(ItemStack stack, int count) {
        var list = new ArrayList<ItemStack>();
        for (int i = 0; i < count; i++) list.add(stack.copyWithCount(1));
        return list;
    }
    private static ItemStack craft(CustomRecipe recipe, ItemStack ingredient, int count, ProjectileNativeInterceptionTest.Fixture f) {
        var input = CraftingInput.of(count == 9 ? 3 : 1, count == 9 ? 3 : 1, copies(ingredient, count));
        check(recipe.matches(input, f.level), "Actual carrier recipe accepts saved denomination");
        var result = recipe.assemble(input, f.level.registryAccess());
        check(!result.isEmpty(), "Actual carrier recipe returns data-bearing output");
        return result;
    }
    private static ItemStack legacy(ItemStack stack) {
        return edit(stack, t -> { t.remove("schema"); t.remove("ingot_denomination"); });
    }
    private static ItemStack edit(ItemStack stack, java.util.function.Consumer<CompoundTag> edit) {
        var result = stack.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, result, outer -> { var tag = outer.getCompound(ROOT); edit.accept(tag); outer.put(ROOT, tag); });
        return result;
    }
    private static void nativeDissolution(ProjectileNativeInterceptionTest.Fixture f, ItemStack oldNugget) {
        if (f.level.memoryMachines == null) f.level.memoryMachines = new HashMap<>();
        var pos = new BlockPos(20, 0, 0);
        var source = new EssenceCrucibleBlockEntity(pos, EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState());
        source.setLevel(f.level); f.level.memoryMachines.put(pos, source); f.level.memoryBlocks.put(pos, source.getBlockState()); source.bindOnPlacement(f.player);
        long before = source.storedEssence(EssenceTypes.OFFENSE);
        long expected = EssentiumCarrierData.extractionYieldMicroUnits(EssentiumCarrierData.readValidated(oldNugget).orElseThrow()) / 1_000_000;
        source.setItem(0, oldNugget.copy());
        for (int tick = 0; tick <= source.structureStats().dissolutionTicksPerItem(); tick++) {
            f.level.tick++; EssenceCrucibleBlockEntity.serverTick(f.level, pos, source.getBlockState(), source);
        }
        check(source.getItem(0).isEmpty() && source.storedEssence(EssenceTypes.OFFENSE) - before == expected,
                "Native Crucible consumes one legacy old-capacity nugget and deposits its exact current-policy extraction value");
        f.level.memoryMachines.remove(pos); f.level.memoryBlocks.remove(pos);
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
