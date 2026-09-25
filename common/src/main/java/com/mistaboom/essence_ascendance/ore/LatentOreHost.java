package com.mistaboom.essence_ascendance.ore;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Optional;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/** One stable host identity boundary for worldgen, saves, items, rendering and Gathering. */
public final class LatentOreHost {
    public static final String HOST_TAG = "latent_ore_host";
    private static volatile List<BlockState> creativeHosts = baselineHosts();

    private LatentOreHost() { }

    /** Structural safety only; discovery separately requires evidence of a primary geological role. */
    public static boolean isValid(BlockState state) {
        return state != null && !state.isAir() && !state.hasBlockEntity()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 0
                && state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static CompoundTag encode(BlockState host) {
        if (!isValid(host)) throw new IllegalArgumentException("Invalid Latent Ore host");
        return NbtUtils.writeBlockState(host);
    }

    /** Rejects unknown blocks/properties instead of silently changing an invalid identity into stone. */
    public static Optional<BlockState> decode(CompoundTag tag) {
        if (!tag.contains("Name", Tag.TAG_STRING)) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Name"));
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return Optional.empty();
        Block block = BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();
        if (tag.contains("Properties")) {
            if (!tag.contains("Properties", Tag.TAG_COMPOUND)) return Optional.empty();
            CompoundTag properties = tag.getCompound("Properties");
            for (String name : properties.getAllKeys()) {
                Property<?> property = block.getStateDefinition().getProperty(name);
                if (property == null || !properties.contains(name, Tag.TAG_STRING)) return Optional.empty();
                state = withProperty(state, property, properties.getString(name));
                if (state == null) return Optional.empty();
            }
        }
        return isValid(state) ? Optional.of(state) : Optional.empty();
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(null);
    }

    public static Optional<BlockState> read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        CompoundTag tag = data.copyTag();
        return tag.contains(HOST_TAG, Tag.TAG_COMPOUND) ? decode(tag.getCompound(HOST_TAG)) : Optional.empty();
    }

    public static void write(ItemStack stack, BlockState host) {
        CompoundTag data = encode(host);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(HOST_TAG, data));
    }

    public static DataComponentPatch components(BlockState host) {
        CompoundTag tag = new CompoundTag();
        tag.put(HOST_TAG, encode(host));
        return DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build();
    }

    public static ItemStack stack(BlockState host) {
        ItemStack stack = new ItemStack(EssenceInfuserContent.LATENT_ORE_ITEM.get());
        write(stack, host);
        return stack;
    }

    private static List<BlockState> baselineHosts() {
        return List.of(Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
                Blocks.NETHERRACK.defaultBlockState(), Blocks.END_STONE.defaultBlockState());
    }

    /** Server-selected catalog synchronized at connection; an empty set clears world-specific entries. */
    public static void setCreativeHosts(Set<ResourceLocation> ids) {
        Set<BlockState> hosts = new LinkedHashSet<>(baselineHosts());
        ids.stream().sorted().forEach(id -> {
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                BlockState host = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
                if (isValid(host)) hosts.add(host);
            }
        });
        creativeHosts = List.copyOf(hosts);
    }

    public static List<ItemStack> creativeStacks() { return creativeHosts.stream().map(LatentOreHost::stack).toList(); }

    public static Optional<BlockState> host(BlockGetter level, BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof LatentOreBlockEntity ore
                ? ore.hostState() : Optional.empty();
    }

    /** The exact eligible state being replaced supplies identity, independent of dimension. */
    public static boolean place(WorldGenLevel level, BlockPos pos, BlockState host, int flags) {
        if (!isValid(host)) return false;
        if (!level.setBlock(pos, EssenceInfuserContent.LATENT_ORE.get().defaultBlockState(), flags)) return false;
        if (level.getBlockEntity(pos) instanceof LatentOreBlockEntity ore) {
            ore.initializeHost(host);
            return true;
        }
        level.setBlock(pos, host, flags);
        return false;
    }
}
