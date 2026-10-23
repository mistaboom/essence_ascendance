package com.mistaboom.essence_ascendance.ore;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.registration.ContentRegistrationCompat;
import dev.architectury.registry.registries.DeferredRegister;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Registered item identities for host-specific stacks of the one adaptive world block. */
public final class LatentOreItems {
    private static final Map<Block, LatentOreBlockItem> REGISTERED = new LinkedHashMap<>();

    private LatentOreItems() { }

    /** Flat vanilla IDs stay short; namespace qualification protects every nested/modded path. */
    public static ResourceLocation itemId(ResourceLocation host) {
        String path = host.getNamespace().equals("minecraft") && !host.getPath().contains("/")
                ? host.getPath() + "_latent_ore"
                : host.getNamespace() + "/" + host.getPath() + "_latent_ore";
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }

    /**
     * Static aliases cover the four known vanilla primary substrates only. A solid
     * block is not evidence of primary terrain: logs, building blocks and deposits
     * must never gain ore items just because their geometry can carry the overlay.
     */
    public static Optional<Registration> candidate(ResourceLocation hostId, Block block) {
        return LatentOreHost.baselineHosts().stream().filter(state -> state.getBlock() == block)
                .findFirst().map(state -> new Registration(hostId, itemId(hostId), state));
    }

    /** Use the existing common item registry; neither loader needs a block-registry scan. */
    public static void register(DeferredRegister<Item> items) {
        for (BlockState host : LatentOreHost.baselineHosts()) {
            var registration = candidate(BuiltInRegistries.BLOCK.getKey(host.getBlock()), host.getBlock())
                    .orElseThrow();
            items.register(registration.itemId().getPath(), () -> create(registration));
        }
    }

    /** Called by the existing Architectury item registry, exactly once per known primary host. */
    public static synchronized LatentOreBlockItem create(Registration registration) {
        Block hostBlock = registration.representative().getBlock();
        if (!BuiltInRegistries.BLOCK.getKey(hostBlock).equals(registration.hostId()))
            throw new IllegalStateException("Latent Ore host registry identity changed: " + registration.hostId());
        if (REGISTERED.containsKey(hostBlock))
            throw new IllegalStateException("Duplicate Latent Ore item for " + registration.hostId());

        CompoundTag tag = new CompoundTag();
        tag.put(LatentOreHost.HOST_TAG, LatentOreHost.encode(registration.representative()));
        // In 26, Item.Properties.finalizeInitializer writes ITEM_MODEL after ordinary
        // components. Override its native effective model so that default stacks (including
        // /give) select the authored adaptive cube before the host-quad mixin runs.
        //? if >=26.1.2 {
        /*Item.Properties properties = new Item.Properties() {
            @Override public ResourceLocation effectiveModel() {
                return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "adaptive_latent_ore");
            }
        };*/
        //? }
        //? if <26.1.2
        Item.Properties properties = new Item.Properties();
        properties.component(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        properties = ContentRegistrationCompat.blockItem(registration.itemId().getPath(), properties);
        LatentOreBlockItem item = new LatentOreBlockItem(
                EssenceInfuserContent.LATENT_ORE.get(), properties, registration.representative());
        REGISTERED.put(hostBlock, item);
        return item;
    }

    /** Fixed-host aliases only; the legacy adaptive item remains the world's canonical Block.asItem. */
    public static synchronized List<Item> items() {
        return List.copyOf(REGISTERED.values());
    }

    /** Host identities to prepare alongside the synchronized worldgen catalog's sprites. */
    public static synchronized Set<ResourceLocation> registeredHostIds() {
        return REGISTERED.keySet().stream().map(BuiltInRegistries.BLOCK::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean isOre(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(EssenceInfuserContent.LATENT_ORE_ITEM.get())
                || stack.getItem() instanceof LatentOreBlockItem item && item.fixedHost() != null);
    }

    /** Model loading must recognize registered aliases, not similarly named foreign items. */
    public static boolean isRegisteredAlias(ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item instanceof LatentOreBlockItem ore && ore.fixedHost() != null
                && BuiltInRegistries.ITEM.getKey(item).equals(itemId);
    }

    /**
     * Per-world modded terrain is discovered after the static item registry freezes.
     * Preserve its exact host in the existing adaptive carrier instead of registering
     * every solid block speculatively. Distinct modded registry IDs need an earlier
     * authoritative host declaration and are not supplied by structural guessing.
     */
    public static synchronized Item itemFor(BlockState host) {
        if (!LatentOreHost.isValid(host)) throw new IllegalArgumentException("Invalid Latent Ore host");
        Item item = REGISTERED.get(host.getBlock());
        if (item != null) return item;
        if (LatentOreHost.baselineHosts().stream().anyMatch(state -> state.getBlock() == host.getBlock()))
            throw new IllegalStateException("Missing primary Latent Ore item for "
                    + BuiltInRegistries.BLOCK.getKey(host.getBlock()));
        return EssenceInfuserContent.LATENT_ORE_ITEM.get();
    }

    public record Registration(ResourceLocation hostId, ResourceLocation itemId, BlockState representative) { }
}
