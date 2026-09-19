package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.valuation.BiologicalAcquisitionSources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extensible gameplay registry for non-harmful renewable animal-product events.
 * Providers preserve native state transitions where practical, while factual passive
 * outputs reuse the valuation registry instead of maintaining a second item list.
 */
public final class RenewableAnimalProductRegistry {
    public interface Provider {
        ResourceLocation id();
        boolean canProvide(ServerPlayer player, Animal animal);
        boolean provide(ServerPlayer player, Animal animal, RandomSource random);
    }

    private static final List<Provider> BUILTIN = List.of(
            new PassiveProductionProvider(),
            new SheepShearingProvider(),
            new ContainerExchangeProvider("milk_container",
                    Set.of(EntityType.COW, EntityType.MOOSHROOM, EntityType.GOAT), Items.BUCKET, Items.MILK_BUCKET)
    );
    private static final Map<ResourceLocation, Provider> ADDITIONAL = new LinkedHashMap<>();

    private RenewableAnimalProductRegistry() { }

    /** Integrations may register native or modded renewable-product mechanics during startup. */
    public static synchronized void register(Provider provider) {
        if (provider == null || provider.id() == null || find(provider.id()) != null) {
            throw new IllegalArgumentException("Duplicate or missing renewable animal-product provider");
        }
        ADDITIONAL.put(provider.id(), provider);
    }

    public static synchronized boolean hasAvailableProduct(ServerPlayer player, Animal animal) {
        if (player == null || animal == null) return false;
        return providers().stream().anyMatch(provider -> provider.canProvide(player, animal));
    }

    /** Chooses one currently available production mechanic and executes exactly one product event. */
    public static synchronized boolean provideOne(ServerPlayer player, Animal animal, RandomSource random) {
        if (player == null || animal == null || random == null) return false;
        List<Provider> available = providers().stream()
                .filter(provider -> provider.canProvide(player, animal))
                .toList();
        if (available.isEmpty()) return false;
        return available.get(random.nextInt(available.size())).provide(player, animal, random);
    }

    private static Provider find(ResourceLocation id) {
        for (Provider provider : BUILTIN) if (provider.id().equals(id)) return provider;
        return ADDITIONAL.get(id);
    }

    private static List<Provider> providers() {
        List<Provider> providers = new ArrayList<>(BUILTIN);
        providers.addAll(ADDITIONAL.values());
        return providers;
    }

    private static final class PassiveProductionProvider implements Provider {
        private static final ResourceLocation ID = RenewableAnimalProductRegistry.id("passive_production");

        @Override public ResourceLocation id() { return ID; }

        @Override
        public boolean canProvide(ServerPlayer player, Animal animal) {
            return !BiologicalAcquisitionSources.periodicProducts(animal.getType()).isEmpty();
        }

        @Override
        public boolean provide(ServerPlayer player, Animal animal, RandomSource random) {
            List<BiologicalAcquisitionSources.Source> sources =
                    BiologicalAcquisitionSources.periodicProducts(animal.getType());
            if (sources.isEmpty()) return false;
            BiologicalAcquisitionSources.Source source = sources.get(random.nextInt(sources.size()));
            animal.spawnAtLocation(new ItemStack(source.output(), source.count()));
            return true;
        }
    }

    /** Uses vanilla shearing so wool color, count, sound and regrowth state remain native. */
    private static final class SheepShearingProvider implements Provider {
        private static final ResourceLocation ID = RenewableAnimalProductRegistry.id("sheep_shearing");

        @Override public ResourceLocation id() { return ID; }

        @Override
        public boolean canProvide(ServerPlayer player, Animal animal) {
            return animal instanceof Sheep sheep && sheep.readyForShearing();
        }

        @Override
        public boolean provide(ServerPlayer player, Animal animal, RandomSource random) {
            if (!(animal instanceof Sheep sheep) || !sheep.readyForShearing()) return false;
            sheep.shear(SoundSource.PLAYERS);
            return true;
        }
    }

    /** Reusable adapter for renewable products whose normal form incorporates a player-supplied container. */
    private static final class ContainerExchangeProvider implements Provider {
        private final ResourceLocation id;
        private final Set<EntityType<?>> producers;
        private final Item emptyContainer;
        private final Item filledProduct;

        private ContainerExchangeProvider(String path, Set<EntityType<?>> producers,
                                          Item emptyContainer, Item filledProduct) {
            this.id = RenewableAnimalProductRegistry.id(path);
            this.producers = Set.copyOf(producers);
            this.emptyContainer = emptyContainer;
            this.filledProduct = filledProduct;
        }

        @Override public ResourceLocation id() { return id; }

        @Override
        public boolean canProvide(ServerPlayer player, Animal animal) {
            return producers.contains(animal.getType()) && containerSlot(player, emptyContainer) >= 0;
        }

        @Override
        public boolean provide(ServerPlayer player, Animal animal, RandomSource random) {
            if (!producers.contains(animal.getType())) return false;
            int slot = containerSlot(player, emptyContainer);
            if (slot < 0) return false;
            ItemStack container = player.getInventory().getItem(slot);
            container.shrink(1);
            ItemStack product = new ItemStack(filledProduct);
            if (!player.getInventory().add(product)) animal.spawnAtLocation(product);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return true;
        }
    }

    private static int containerSlot(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "renewable_product/" + path);
    }
}
