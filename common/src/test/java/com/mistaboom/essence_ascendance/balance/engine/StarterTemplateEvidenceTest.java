package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.capability.SkyblockBuilderStartingSourcesProvider;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Real native structure NBT, exclusive alternatives, finite source projection and conservative callback scope. */
public final class StarterTemplateEvidenceTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var first = new SkyblockBuilderStartingSourcesProvider.Bundle("island-a", Map.of("minecraft:oak_log",4.0,"minecraft:diamond",1.0),0);
        var second = new SkyblockBuilderStartingSourcesProvider.Bundle("island-b", Map.of("minecraft:oak_log",7.0,"minecraft:emerald",1.0),0);
        var common = SkyblockBuilderStartingSourcesProvider.commonMinimum(List.of(first,second), Map.of());
        check(common.equals(Map.of("minecraft:oak_log",4.0)), "Template resources use common minima; exclusive diamond/emerald alternatives are not unioned");
        check(SkyblockBuilderStartingSourcesProvider.commonMinimum(List.of(first,
                new SkyblockBuilderStartingSourcesProvider.Bundle("unresolved",Map.of(),1)),Map.of()).isEmpty(),
                "Unresolved alternative blocks false global starting guarantees");
        check(SkyblockBuilderStartingSourcesProvider.literalStarterItems(List.of()).isEmpty(), "Empty configured inventory supplies no invented sapling or tool");
        var literals = SkyblockBuilderStartingSourcesProvider.literalStarterItems(List.of(new ItemStack(Items.OAK_LOG,2),new ItemStack(Items.OAK_LOG,3),ItemStack.EMPTY));
        check(literals.equals(Map.of("minecraft:oak_log",5.0)), "Literal common starter stacks retain real finite counts");
        check(SkyblockBuilderStartingSourcesProvider.commonMinimum(List.of(first,second),literals).get("minecraft:oak_log") == 9,
                "Common starter inventory adds once to each exclusive template minimum");
        check(!SkyblockBuilderStartingSourcesProvider.isPlainNativeState(Blocks.CHEST.defaultBlockState()), "Native block-entity storage excluded before drop proof");
        check(!SkyblockBuilderStartingSourcesProvider.isPlainNativeState(Blocks.GOLD_ORE.defaultBlockState()), "Required-tool blocks do not become empty-hand sources");
        AtomicInteger calls = new AtomicInteger();
        var single = structure(List.of(List.of(Blocks.OAK_LOG,Blocks.CHEST)),new int[]{0,0,0,0,1},-1);
        var singleBundles = SkyblockBuilderStartingSourcesProvider.captureTemplate("a",single,state -> {
            calls.incrementAndGet(); return Map.of(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),1.0);
        });
        check(singleBundles.size() == 1 && singleBundles.getFirst().items().equals(Map.of("minecraft:oak_log",4.0)), "Native stored-content block cannot enter finite material census");
        check(calls.get() == 4 && singleBundles.getFirst().unresolvedBlocks() == 1, "Storage is rejected without executing callback/drop proof");
        var palettes = structure(List.of(List.of(Blocks.OAK_LOG),List.of(Blocks.BIRCH_LOG)),new int[]{0,0,0,0},-1);
        var exclusive = SkyblockBuilderStartingSourcesProvider.captureTemplate("palette-choice",palettes,
                state -> Map.of(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),1.0));
        check(exclusive.size() == 2 && SkyblockBuilderStartingSourcesProvider.commonMinimum(exclusive,Map.of()).isEmpty(),
                "Selectable palettes remain exclusive; oak/birch cannot be combined");
        var leaves = structure(List.of(List.of(Blocks.OAK_LEAVES)), new int[]{0,0,0,0}, -1);
        var chance = SkyblockBuilderStartingSourcesProvider.captureSeedOpportunities("tree", leaves, state -> Map.of(
                "minecraft:oak_sapling", new com.mistaboom.essence_ascendance.valuation.StartingBlockDrops.ExpectedDrop(.05, .05),
                "minecraft:apple", new com.mistaboom.essence_ascendance.valuation.StartingBlockDrops.ExpectedDrop(.1, .1)));
        check(chance.equals(List.of(Map.of("minecraft:oak_sapling", .05))), "Starter seed opportunity cannot become four seeds, guaranteed stock, or arbitrary food supply");
        var seedChoices = structure(List.of(List.of(Blocks.OAK_LEAVES), List.of(Blocks.BIRCH_LEAVES)), new int[]{0}, -1);
        var seedBundles = SkyblockBuilderStartingSourcesProvider.captureSeedOpportunities("trees", seedChoices, state -> Map.of(
                state.is(Blocks.OAK_LEAVES) ? "minecraft:oak_sapling" : "minecraft:birch_sapling",
                new com.mistaboom.essence_ascendance.valuation.StartingBlockDrops.ExpectedDrop(.05, .05)))
                .stream().map(seeds -> new SkyblockBuilderStartingSourcesProvider.Bundle("seed alternative", seeds, 0)).toList();
        check(SkyblockBuilderStartingSourcesProvider.commonMinimum(seedBundles, Map.of()).isEmpty(), "Exclusive starter seeds cannot bootstrap one common cultivation route");
        var withNbt = structure(List.of(List.of(Blocks.OAK_LOG)),new int[]{0,0},0);
        check(SkyblockBuilderStartingSourcesProvider.captureTemplate("nbt",withNbt,state -> Map.of("minecraft:oak_log",1.0))
                .getFirst().items().equals(Map.of("minecraft:oak_log",1.0)), "Placed NBT is excluded even when supplied palette claims an ordinary block");
        var unregistered = structure(List.of(List.of(Blocks.OAK_LOG)),new int[]{0},-1);
        unregistered.getList("palette",Tag.TAG_COMPOUND).getCompound(0).putString("Name","opaque_mod:custom_wood");
        calls.set(0);
        check(SkyblockBuilderStartingSourcesProvider.captureTemplate("opaque",unregistered,state -> {
            calls.incrementAndGet(); return Map.of("minecraft:diamond",1.0);
        }).getFirst().items().isEmpty() && calls.get() == 0, "Unresolved custom state does not fall back to a native item or invoke proof");
        check(SkyblockBuilderStartingSourcesProvider.captureTemplate("missing",new CompoundTag(),state -> Map.of()).getFirst().items().isEmpty(),
                "Missing palettes supply an unresolved alternative rather than false access");
        var sink = new EvidenceSink();
        startingFact(sink,"initial_source",EvidenceFact.Value.text("fixture:common_start"),"fixture",ProgressionBand.ENTRY);
        startingFact(sink,"initial_count",EvidenceFact.Value.number(4),"fixture",ProgressionBand.ENTRY);
        var source = PackEvidenceCollector.initialAcquisition(sink,"minecraft:oak_log").orElseThrow();
        check(source.expectedOutput() == 4 && !source.renewable() && !source.rateKnown() && source.unitsPerSecond() == 0,
                "Finite initial stock never becomes renewable throughput");
        check(source.stage() == ProgressionBand.ENTRY && source.dependencies().isEmpty() && source.confidence() == .95,
                "Native initial stock supplies an attributable independent access witness");
        check(source.availability().category() == SourceAvailability.Category.FINITE_SHARED
                && source.availability().scope() == SourceAvailability.Scope.SHARED
                && source.availability().expectedPerEvent() == 4 && source.availability().accessProven()
                && !source.availability().provenRenewable(), "Typed finite default scope preserves stock for downstream configuration proofs");
        startingFact(sink,"initial_scope",EvidenceFact.Value.text("TEAM"),"fixture",ProgressionBand.ENTRY);
        check(PackEvidenceCollector.initialAcquisition(sink,"minecraft:oak_log").orElseThrow().availability().scope() == SourceAvailability.Scope.TEAM,
                "Provider-supplied team scope retains its finite bundle");
        startingFact(sink,"initial_scope",EvidenceFact.Value.text("invented_scope"),"fixture",ProgressionBand.ENTRY);
        check(PackEvidenceCollector.initialAcquisition(sink,"minecraft:oak_log").isEmpty(), "Invalid scope cannot silently become global availability");
        check(PackEvidenceCollector.initialAcquisition(new EvidenceSink(),"minecraft:oak_log").isEmpty(), "Absent witness does not invent access");
        var mixed = new EvidenceSink();
        startingFact(mixed,"initial_source",EvidenceFact.Value.text("fixture:common_start"),"fixture",ProgressionBand.ENTRY);
        startingFact(mixed,"initial_count",EvidenceFact.Value.number(4),"other_provider",ProgressionBand.ENTRY);
        check(PackEvidenceCollector.initialAcquisition(mixed,"minecraft:oak_log").isEmpty(), "Unrelated provider fields cannot be composed into a source");
        for (double invalid : new double[]{0,-1,.5}) {
            var bad = new EvidenceSink();
            startingFact(bad,"initial_source",EvidenceFact.Value.text("fixture:common_start"),"fixture",ProgressionBand.ENTRY);
            startingFact(bad,"initial_count",EvidenceFact.Value.number(invalid),"fixture",ProgressionBand.ENTRY);
            check(PackEvidenceCollector.initialAcquisition(bad,"minecraft:oak_log").isEmpty(), "Invalid/fractional finite stock excluded");
        }
        check(PackEvidenceProviders.all().stream().anyMatch(provider -> provider.id().equals("skyblockbuilder_starting_sources")),
                "Starting source provider is registered on the early before-acquisition path");
        check(!SkyblockBuilderStartingSourcesProvider.supportsGenerator("net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator")
                && SkyblockBuilderStartingSourcesProvider.supportsGenerator("de.melanx.skyblockbuilder.world.chunkgenerators.SkyblockNoiseBasedChunkGenerator"),
                "Installed templates alone cannot grant starting resources under an unrelated normal world generator");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("StarterTemplateEvidenceTest: " + checks + " exclusive native starters and finite source checks PASS");
    }
    private static CompoundTag structure(List<List<Block>> alternatives, int[] references, int withNbt) {
        CompoundTag template = new CompoundTag(); ListTag palettes = new ListTag();
        for (var alternative : alternatives) {
            ListTag palette = new ListTag();
            for (Block block : alternative) { CompoundTag state = new CompoundTag(); state.putString("Name",BuiltInRegistries.BLOCK.getKey(block).toString()); palette.add(state); }
            palettes.add(palette);
        }
        if (alternatives.size() == 1) template.put("palette",palettes.get(0)); else template.put("palettes",palettes);
        ListTag blocks = new ListTag();
        for (int i = 0; i < references.length; i++) {
            CompoundTag placed = new CompoundTag(); placed.putInt("state",references[i]);
            if (i == withNbt) placed.put("nbt",new CompoundTag()); blocks.add(placed);
        }
        template.put("blocks",blocks); return template;
    }
    private static void startingFact(EvidenceSink sink,String property,EvidenceFact.Value value,String provider,ProgressionBand stage) {
        sink.add(new EvidenceFact(EvidenceFact.Subject.ITEM,"minecraft:oak_log",property,value,provider,EvidenceFact.Origin.OBSERVED,.95,0,stage,List.of(),"finite fixture"));
    }
    private static void check(boolean passed,String message) { checks++; if (!passed) throw new AssertionError(message); }
}
