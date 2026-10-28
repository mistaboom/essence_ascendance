package com.mistaboom.essence_ascendance.ore;

import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
//? if >=26.1.2
/*import net.minecraft.util.ProblemReporter;*/
//? if >=26.1.2
/*import net.minecraft.world.level.storage.TagValueInput;*/

import java.util.List;

/** Loaded client mixin/native serialization probe; no gameplay world or chunk scheduler. */
public final class LatentOreClientModelFixture {
    private LatentOreClientModelFixture() { }

    public static void verify() throws ReflectiveOperationException {
        Class<?> adapter;
        try { adapter = Class.forName("com.mistaboom.essence_ascendance.neoforge.client.LatentOreNeoForgeModels"); }
        catch (ClassNotFoundException fabric) { return; }
        var propertyField = adapter.getDeclaredField("HOST");
        propertyField.setAccessible(true);
        Object property = propertyField.get(null);
        var entity = new LatentOreBlockEntity(BlockPos.ZERO, EssenceInfuserContent.LATENT_ORE.get().defaultBlockState());
        var modelData = entity.getClass().getMethod("getModelData");
        Object empty = modelData.invoke(entity);
        var propertyValue = empty.getClass().getMethod("get", propertyField.getType());
        if (propertyValue.invoke(empty, property) != null) throw new AssertionError("Uninitialized host must start empty");
        Object previous = empty;
        BlockState previousHost = null;
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (BlockState host : List.of(Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
                Blocks.NETHERRACK.defaultBlockState(), Blocks.END_STONE.defaultBlockState())) {
            CompoundTag tag = new CompoundTag();
            tag.put(LatentOreHost.HOST_TAG, LatentOreHost.encode(host));
            //? if >=26.1.2 {
            /*ProblemReporter.Collector problems = new ProblemReporter.Collector();
            entity.loadWithComponents(TagValueInput.create(problems, registries, tag));
            if (!problems.isEmpty()) throw new AssertionError(problems.getReport());*/
            //? }
            //? if <26.1.2
            entity.loadWithComponents(tag, registries);
            Object current = modelData.invoke(entity);
            if (entity.hostState().orElse(null) != host || propertyValue.invoke(current, property) != host)
                throw new AssertionError("Native serialized host did not reach the applied NeoForge model-data mixin: " + host);
            if (propertyValue.invoke(previous, property) != previousHost)
                throw new AssertionError("Published model-data snapshots must stay immutable");
            if (LatentOreModels.forHost(host, null) == null)
                throw new AssertionError("Decoded host did not resolve a prepared native ore model: " + host);
            previous = current;
            previousHost = host;
        }
        // Placement invokes the same transformed render-update boundary even on this detached BE.
        entity.setHost(Blocks.STONE.defaultBlockState());
        if (propertyValue.invoke(modelData.invoke(entity), property) != Blocks.STONE.defaultBlockState())
            throw new AssertionError("Placed host did not reach NeoForge model data");
        System.out.println("LatentOreClientModelFixture: applied NeoForge mixin, native serialized empty-to-host updates,"
                + " immutable snapshots and prepared host models PASS; detached BE, no chunk scheduler claim");
    }
}
