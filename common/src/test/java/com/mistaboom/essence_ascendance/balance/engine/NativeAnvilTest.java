package com.mistaboom.essence_ascendance.balance.engine;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import java.util.*;

/** Differential against the actual native menu, with an inert player and no world/menu interaction. */
public final class NativeAnvilTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var registry = new MappedRegistry<Enchantment>(Registries.ENCHANTMENT, Lifecycle.stable());
        var mining = register(registry, "mining", Items.IRON_PICKAXE, 5, 8);
        var sword = register(registry, "sword", Items.IRON_SWORD, 5, 4);
        var expensive = register(registry, "expensive", Items.IRON_PICKAXE, 5, 20);
        var measured = register(registry, "measured", Items.IRON_SWORD, 5, 4,
                DataComponentMap.builder().set(EnchantmentEffectComponents.DAMAGE, List.of(
                        new ConditionalEffect<net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect>(
                                new net.minecraft.world.item.enchantment.effects.AddValue(LevelBasedValue.constant(2)), Optional.empty()))).build());
        registry.freeze();
        check(NativeTradeProgression.bookEffects(book(Map.of(mining, 1))).evidenceCount() == 0,
                "An enchantment with no projected effects triggered acquisition work");
        var projection = NativeTradeProgression.bookEffects(book(Map.of(mining, 1, measured, 3)));
        check(projection.evidenceCount() > 0 && projection.evidence().stream().noneMatch(e -> e.attainable() || e.source().reachable()),
                "Supported mod-namespace or mixed-book effect lost, or semantic projection invented acquisition");
        var excessive = NativeTradeProgression.bookEffects(book(Map.of(measured, 9)));
        check(excessive.evidence().getFirst().configuration().endsWith(":5"), "Trade effect probe did not use the native anvil level clamp");
        check(!NativeAnvil.target(Items.AIR, java.util.stream.Stream.generate(() -> {
            throw new AssertionError("Empty target reached a modded enchantment callback");
        })), "Air must be excluded before querying enchantment compatibility");
        check(!NativeAnvil.target(Items.ANVIL, registry.stream()) && !NativeAnvil.target(Items.IRON_INGOT, registry.stream())
                && NativeAnvil.target(Items.IRON_PICKAXE, registry.stream()), "Ordinary material/default enchantment component confused with an applicable target");
        var player = allocate(PlayerStub.class);
        for (int level = 1; level <= 5; level++) {
            var book = book(Map.of(mining, level)); var target = new ItemStack(Items.IRON_PICKAXE);
            var application = NativeAnvil.apply(target, book).orElseThrow();
            var menu = menu(player, target, book);
            check(menu.getCost() == application.requiredAndSpentLevels(), "Derived book cost differs from native AnvilMenu");
            var output = menu.getSlot(2).getItem();
            check(!output.isEmpty() && EnchantmentHelper.getEnchantmentsForCrafting(output).getLevel(mining) == application.applied().get("fixture:mining"), "Applied enchantment differs from actual menu output");
            check(output.getOrDefault(DataComponents.REPAIR_COST, 0) == application.resultingRepairCost(), "Prior-work output differs from native menu");
            check(target.getComponents().equals(new ItemStack(Items.IRON_PICKAXE).getComponents()) && book.getOrDefault(DataComponents.REPAIR_COST, 0) == 0, "Analysis mutated caller stacks");
        }
        var mixed = book(Map.of(mining, 2, sword, 4));
        var app = NativeAnvil.apply(new ItemStack(Items.IRON_PICKAXE), mixed).orElseThrow();
        check(app.applied().equals(Map.of("fixture:mining", 2)) && menu(player, new ItemStack(Items.IRON_PICKAXE), mixed).getCost() == app.requiredAndSpentLevels(), "Inapplicable book effect increased cost or became an applied capability");
        var tooExpensive = book(Map.of(expensive, 4));
        check(NativeAnvil.apply(new ItemStack(Items.IRON_PICKAXE), tooExpensive).isEmpty()
                && menu(player, new ItemStack(Items.IRON_PICKAXE), tooExpensive).getSlot(2).getItem().isEmpty(), "40-level survival cutoff bypassed");
        var used = book(Map.of(mining, 1)); used.set(DataComponents.REPAIR_COST, 1);
        check(NativeAnvil.apply(new ItemStack(Items.IRON_PICKAXE), used).isEmpty(), "Prior-work book admitted by fresh-book contract");
        var damaged = new ItemStack(Items.IRON_PICKAXE); damaged.setDamageValue(1);
        check(NativeAnvil.apply(damaged, mixed).isEmpty(), "Damaged equipment borrowed a fresh exact crafting proof");
        check(NativeAnvil.apply(new ItemStack(Items.IRON_PICKAXE, 2), mixed).isEmpty(), "Multiple targets bypassed one-item native anvil limit");
        check(NativeAnvil.apply(new ItemStack(Items.IRON_AXE), mixed).isEmpty(), "Unsupported item borrowed another item's book");
        bookSelection(registry, List.of(mining, sword), player);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("NativeAnvilTest: " + checks + " checks PASS (native menu differential; no world)");
    }
    private static void bookSelection(net.minecraft.core.Registry<Enchantment> registry, List<Holder<Enchantment>> entries, PlayerStub player) {
        for (var offer : NativeEnchanting.bookOffers(entries, 15, 128)) {
            var stack = new ItemStack(Items.BOOK); var random = net.minecraft.util.RandomSource.create((long)offer.seed() + offer.slot());
            var selected = EnchantmentHelper.selectEnchantment(random, stack, offer.requiredPlayerLevel(), entries.stream());
            if (selected.size() > 1) selected.remove(random.nextInt(selected.size()));
            var expected = new TreeMap<String, Integer>(); selected.forEach(e -> expected.put(e.enchantment.unwrapKey().orElseThrow().location().toString(), e.level));
            check(offer.accompanyingEnchantments().equals(expected), "Book-specific random removal lost or used a reset RNG");
            check(offer.accompanyingEnchantments().containsKey(offer.enchantment()), "Removed book enchantment still published");
            Item item = offer.enchantment().equals("fixture:mining") ? Items.IRON_PICKAXE : Items.IRON_SWORD;
            var book = NativeAnvil.book(offer, registry); var application = NativeAnvil.apply(new ItemStack(item), book).orElseThrow();
            check(menu(player, new ItemStack(item), book).getCost() == application.requiredAndSpentLevels(), "Witnessed table book application differs from native menu");
            var bill = NativeAnvil.bill(offer, application);
            check(bill.stream().anyMatch(b -> b.get("id").getAsString().equals("minecraft:book"))
                    && bill.stream().anyMatch(b -> b.get("id").getAsString().equals("minecraft:anvil"))
                    && bill.stream().anyMatch(b -> b.get("id").getAsString().equals(application.item())), "Joint bill omitted book, anvil or equipment");
        }
    }
    private static Holder<Enchantment> register(MappedRegistry<Enchantment> registry, String name, Item item, int max, int anvilCost) {
        return register(registry, name, item, max, anvilCost, DataComponentMap.EMPTY);
    }
    private static Holder<Enchantment> register(MappedRegistry<Enchantment> registry, String name, Item item, int max, int anvilCost, DataComponentMap effects) {
        var definition = Enchantment.definition(HolderSet.direct(item.builtInRegistryHolder()), 10, max,
                Enchantment.dynamicCost(1, 10), Enchantment.dynamicCost(50, 10), anvilCost, EquipmentSlotGroup.MAINHAND);
        return registry.register(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("fixture:" + name)),
                new Enchantment(Component.literal(name), definition, HolderSet.direct(), effects), RegistrationInfo.BUILT_IN);
    }
    private static ItemStack book(Map<Holder<Enchantment>, Integer> entries) {
        var result = new ItemStack(Items.ENCHANTED_BOOK); var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        entries.forEach(stored::set); result.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable()); return result;
    }
    private static AnvilMenu menu(PlayerStub player, ItemStack target, ItemStack book) {
        var menu = new AnvilMenu(1, new Inventory(player), ContainerLevelAccess.NULL) { @Override public void broadcastChanges() { } };
        menu.getSlot(0).set(target.copy()); menu.getSlot(1).set(book.copy()); menu.createResult(); return menu;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)field.get(null)).allocateInstance(type));
    }
    private static final class PlayerStub extends Player {
        private PlayerStub() { super(null, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "unused")); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public Abilities getAbilities() { return new Abilities(); }
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
