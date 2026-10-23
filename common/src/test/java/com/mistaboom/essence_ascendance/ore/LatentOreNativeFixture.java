package com.mistaboom.essence_ascendance.ore;

import com.mistaboom.essence_ascendance.data.ItemStackNbtCompat;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.item.CreativeVariantRegistry;
import com.mistaboom.essence_ascendance.compat.jei.JeiCatalogOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/** Explicit native world fixture. Never included in a production archive. */
public final class LatentOreNativeFixture {
    private static final List<Block> MODDED = new ArrayList<>();
    private static int checks;

    public static void registerBlocks(BiConsumer<ResourceLocation, Block> registrar) {
        for (String namespace : List.of("essence_ore_fixture_a", "essence_ore_fixture_b")) {
            var id = ResourceLocation.fromNamespaceAndPath(namespace, "test_stone");
            var properties = BlockBehaviour.Properties.ofFullCopy(Blocks.STONE);
            //? if >=26.1.2
            /*properties.setId(ResourceKey.create(Registries.BLOCK, id));*/
            Block block = new RotatedPillarBlock(properties);
            registrar.accept(id, block);
            MODDED.add(block);
        }
    }

    public static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var ore = EssenceInfuserContent.LATENT_ORE.get();
        check(LatentOreItems.itemId(ResourceLocation.parse("minecraft:stone"))
                        .equals(ResourceLocation.parse("essence_ascendance:stone_latent_ore")),
                "Flat vanilla hosts keep the short command identity");
        check(!LatentOreItems.itemId(ResourceLocation.parse("minecraft:fixture/stone"))
                        .equals(LatentOreItems.itemId(ResourceLocation.parse("fixture:stone"))),
                "A namespaced Minecraft path cannot collide with another modded host");
        check(ore.asItem() == EssenceInfuserContent.LATENT_ORE_ITEM.get(),
                "Host aliases do not overwrite the canonical adaptive block item");
        check(MODDED.size() == 2, "Two modded blocks registered before item freeze");
        var expectedHosts = Set.of(ResourceLocation.parse("minecraft:stone"),
                ResourceLocation.parse("minecraft:deepslate"), ResourceLocation.parse("minecraft:netherrack"),
                ResourceLocation.parse("minecraft:end_stone"));
        check(LatentOreItems.items().size() == 4 && LatentOreItems.registeredHostIds().equals(expectedHosts),
                "Only the four known vanilla primary substrates have registered aliases");
        for (Block incidental : List.of(Blocks.ACACIA_LOG, Blocks.ANDESITE_SLAB, Blocks.BAMBOO_BLOCK,
                Blocks.ANDESITE, Blocks.POLISHED_DEEPSLATE, Blocks.IRON_ORE))
            check(!BuiltInRegistries.ITEM.containsKey(LatentOreItems.itemId(BuiltInRegistries.BLOCK.getKey(incidental))),
                    "No speculative ore item exists for non-primary blocks: " + incidental);
        check(com.mistaboom.essence_ascendance.worldgen.PrimarySubstrateDiscovery.selectedHostIds(server)
                        .equals(expectedHosts),
                "Actual vanilla dimensions select only their four primary terrain layers");
        LatentOreHost.setCreativeHosts(Set.of());
        check(LatentOreHost.creativeStacks().size() == 4,
                "Vanilla creative/JEI catalog contains exactly four ore stacks");
        List<BlockState> hosts = new ArrayList<>(LatentOreHost.baselineHosts());
        MODDED.forEach(block -> hosts.add(block.defaultBlockState()));
        var ids = new HashSet<ResourceLocation>();
        int hostIndex = 0;
        for (BlockState host : hosts) {
            hostIndex++;
            ItemStack stack = LatentOreHost.stack(host);
            var hostId = BuiltInRegistries.BLOCK.getKey(host.getBlock());
            var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            boolean fixed = expectedHosts.contains(hostId);
            if (fixed) {
                check(itemId.equals(LatentOreItems.itemId(hostId)) && ids.add(itemId),
                        "Each known vanilla primary host has its own registered item ID: " + hostId);
            } else {
                check(stack.is(EssenceInfuserContent.LATENT_ORE_ITEM.get())
                                && !BuiltInRegistries.ITEM.containsKey(LatentOreItems.itemId(hostId)),
                        "Late-discovered modded terrain retains its component carrier without speculative aliases: " + hostId);
            }
            ItemStack given = fixed ? new ItemStack(stack.getItem(), 17) : stack.copyWithCount(17);
            check(LatentOreHost.read(given).orElseThrow() == host,
                    "Fixed /give or component-bearing dynamic stack resolves its exact host: " + hostId);
            //? if >=26.1.2
            /*check(ResourceLocation.parse("essence_ascendance:adaptive_latent_ore").equals(given.get(DataComponents.ITEM_MODEL)), "Final native item defaults use the authored adaptive model for every alias");*/
            if (fixed)
                check(LatentOreHost.read(stack.getItem().getDefaultInstance()).orElseThrow() == host,
                        "Native default ingredient retains the fixed host: " + itemId);
            for (String tag : List.of("essence_ascendance:latent_ores", "c:ores", "c:ores/latent",
                    "essence_latent_ore_item_test:nested_ores"))
                check(given.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(tag))),
                        "Real loaded item tag includes the host alias: " + tag + " / " + itemId);
            ItemStack saved = ItemStackNbtCompat.parse(level.registryAccess(),
                    ItemStackNbtCompat.save(given, level.registryAccess())).orElseThrow();
            check(ItemStack.isSameItemSameComponents(given, saved) && saved.getCount() == 17,
                    "Native item serialization retains alias, count and host");
            var placedPos = new BlockPos(72 + hostIndex, 100, 72);
            ItemStack placed = given.copy();
            check(place(placed, level, placedPos).consumesAction(),
                    "The registered host stack succeeds through native BlockItem.place: " + hostId);
            check(level.getBlockState(placedPos).is(ore)
                            && LatentOreHost.host(level, placedPos).orElseThrow() == host,
                    "Native item placement stores its exact host in the world: " + hostId);
            var pos = new BlockPos(48 + hostIndex, 100, 48);
            // The disposable world persists between runs; restore only this fixture's
            // source block so placement is tested against terrain every time.
            level.setBlock(pos, host, Block.UPDATE_ALL);
            check(LatentOreHost.place(level, pos, host, Block.UPDATE_ALL), "Host ore places in a real world");
            check(LatentOreHost.host(level, pos).orElseThrow() == host, "World block entity stores the same host");
            //? if >=26.1.2
            /*ItemStack picked = ore.getCloneItemStack(level, pos, ore.defaultBlockState(), true);*/
            //? if <26.1.2
            ItemStack picked = ore.getCloneItemStack(level, pos, ore.defaultBlockState());
            check(ItemStack.isSameItemSameComponents(stack, picked), "Pick-block returns the matching host stack");
            var silk = new ItemStack(Items.DIAMOND_PICKAXE);
            //? if >=26.1.2
            /*silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);*/
            //? if <26.1.2
            silk.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
            var loot = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.BLOCK_STATE, ore.defaultBlockState())
                    .withParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(pos))
                    .withParameter(LootContextParams.TOOL, silk);
            var drops = ore.getDrops(ore.defaultBlockState(), loot);
            check(drops.size() == 1 && ItemStack.isSameItemSameComponents(drops.getFirst(), stack),
                    "Real native Silk Touch loot returns the matching host stack");
            loot.withParameter(LootContextParams.TOOL, new ItemStack(Items.DIAMOND_PICKAXE));
            drops = ore.getDrops(ore.defaultBlockState(), loot);
            check(drops.size() == 1 && drops.getFirst().is(EssenceInfuserContent.RAW_LATENT_ORE.get()),
                    "Normal native loot still returns raw Latent Ore");
        }
        check(!ItemStack.isSameItemSameComponents(LatentOreHost.stack(MODDED.getFirst().defaultBlockState()),
                        LatentOreHost.stack(MODDED.getLast().defaultBlockState())),
                "Dynamic hosts with the same path retain different namespace-qualified component identities");
        LatentOreHost.setCreativeHosts(MODDED.stream().map(BuiltInRegistries.BLOCK::getKey)
                .collect(java.util.stream.Collectors.toSet()));
        check(LatentOreHost.creativeStacks().size() == 6,
                "An exact server-selected modded catalog adds only its two host appearances");
        catalogPresentation(server);
        LatentOreHost.setCreativeHosts(Set.of());
        CreativeVariantRegistry.invalidate();
        check(LatentOreHost.creativeStacks().size() == 4,
                "Disconnecting clears dynamic hosts and restores the exact vanilla catalog");
        BlockState oriented = MODDED.getFirst().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        ItemStack orientedStack = LatentOreHost.stack(oriented);
        check(LatentOreHost.read(orientedStack).orElseThrow() == oriented,
                "A modded component carrier retains non-default host state properties");
        ItemStack stripped = new ItemStack(LatentOreItems.itemFor(Blocks.STONE.defaultBlockState()));
        stripped.remove(DataComponents.CUSTOM_DATA);
        check(LatentOreHost.read(stripped).orElseThrow() == Blocks.STONE.defaultBlockState(),
                "Alias item identity resolves its representative even without CUSTOM_DATA");
        var strippedPos = new BlockPos(90, 100, 72);
        check(place(stripped, level, strippedPos).consumesAction()
                        && LatentOreHost.host(level, strippedPos).orElseThrow() == Blocks.STONE.defaultBlockState(),
                "Missing CUSTOM_DATA alias still places its fixed host through native BlockItem.place");
        ItemStack malformed = new ItemStack(LatentOreItems.itemFor(Blocks.STONE.defaultBlockState()));
        var mismatched = new net.minecraft.nbt.CompoundTag();
        mismatched.put(LatentOreHost.HOST_TAG, LatentOreHost.encode(Blocks.END_STONE.defaultBlockState()));
        malformed.set(DataComponents.CUSTOM_DATA, CustomData.of(mismatched));
        check(LatentOreHost.read(malformed).isEmpty(), "Alias rejects explicit data naming a different host block");
        var malformedPos = new BlockPos(91, 100, 72);
        check(!place(malformed, level, malformedPos).consumesAction()
                        && level.getBlockState(malformedPos).isAir(),
                "Mismatched alias cannot place any ore through native BlockItem.place");
        ItemStack legacy = new ItemStack(EssenceInfuserContent.LATENT_ORE_ITEM.get());
        LatentOreHost.write(legacy, Blocks.END_STONE.defaultBlockState());
        check(LatentOreHost.read(legacy).orElseThrow() == Blocks.END_STONE.defaultBlockState(),
                "Existing adaptive stacks still decode their saved host");
        check(ids.contains(ResourceLocation.parse("essence_ascendance:end_stone_latent_ore")),
                "End Stone has the requested command identity");
        System.out.println("LatentOreNativeFixture: " + checks
                + " exact four-item registry, commands, dynamic hosts, catalog, tags, serialization, native item placement, pick and loot checks PASS; items=" + ids);
    }

    private static void catalogPresentation(MinecraftServer server) {
        var order = JeiCatalogOrder.INSTANCE;
        order.clear();
        try {
            // Real registry Items are important here: the fixed aliases and the
            // adaptive modded carrier must not acquire independent JEI anchors.
            LatentOreHost.setCreativeHosts(Set.of());
            CreativeVariantRegistry.invalidate();
            nativeCreativeTab(server, false);
            int first = 10;
            int index = first;
            for (var stack : LatentOreHost.creativeStacks())
                check(order.createdIndex(stack, index++) == first,
                        "Every real vanilla alias shares the original ore family's JEI position");
            check(!order.takePendingSort(), "Initial ore aliases do not request a runtime re-sort");
            order.runtimeAvailable();

            LatentOreHost.setCreativeHosts(MODDED.stream().map(BuiltInRegistries.BLOCK::getKey)
                    .collect(java.util.stream.Collectors.toSet()));
            CreativeVariantRegistry.invalidate();
            nativeCreativeTab(server, true);
            for (Block host : MODDED)
                check(order.createdIndex(LatentOreHost.stack(host.defaultBlockState()), 90000 + index++) == first,
                        "A late modded carrier stays beside the four vanilla aliases in JEI");
            check(order.takePendingSort() && !order.takePendingSort(),
                    "A batch of late modded ores requests exactly one native JEI re-sort");
            ItemStack foreign = new ItemStack(Items.STONE);
            LatentOreHost.write(foreign, Blocks.STONE.defaultBlockState());
            check(order.createdIndex(foreign, 100000) == 100000 && !order.takePendingSort(),
                    "Unrelated Items with host-like components retain their native JEI position");

            LatentOreHost.setCreativeHosts(Set.of());
            CreativeVariantRegistry.invalidate();
            nativeCreativeTab(server, false);
            LatentOreHost.setCreativeHosts(MODDED.stream().map(BuiltInRegistries.BLOCK::getKey)
                    .collect(java.util.stream.Collectors.toSet()));
            CreativeVariantRegistry.invalidate();
            check(order.createdIndex(LatentOreHost.stack(MODDED.getFirst().defaultBlockState()), 200000) == first
                            && order.takePendingSort(),
                    "A later server catalog reuses the ore family's stable position");
        } finally {
            order.clear();
        }
    }

    /**
     * Exercise actual server-safe tab generators and loader events. Alternate the
     * native permission key between catalogs to force reconstruction here: the
     * catalog-revision cache mixin is client-only and has separate client coverage.
     */
    private static void nativeCreativeTab(MinecraftServer server, boolean hasPermissions) {
        check(CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), hasPermissions, server.registryAccess()),
                "Changed native parameters rebuild the actual creative tabs for this catalog");
        var natural = creativeTab(CreativeModeTabs.NATURAL_BLOCKS);
        var entries = List.copyOf(natural.getDisplayItems());
        var expected = LatentOreHost.creativeStacks();
        var ores = entries.stream().filter(LatentOreItems::isOre).toList();
        check(ores.size() == expected.size(), "Natural Blocks contains exactly the selected vanilla and modded ore hosts");
        int first = -1;
        for (int i = 0; i < entries.size(); i++) if (LatentOreItems.isOre(entries.get(i))) { first = i; break; }
        check(first >= 0, "The native Natural Blocks tab contains the Latent Ore family");
        for (int i = 0; i < expected.size(); i++)
            check(ItemStack.isSameItemSameComponents(entries.get(first + i), expected.get(i)),
                    "Native tab order keeps every selected ore contiguous before raw blocks");
        check(entries.get(first + expected.size()).is(EssenceInfuserContent.RAW_LATENT_ORE_BLOCK_ITEM.get()),
                "The raw block follows the complete ore family, including dynamic hosts");
        for (var key : List.of(CreativeModeTabs.INGREDIENTS, CreativeModeTabs.BUILDING_BLOCKS,
                CreativeModeTabs.FUNCTIONAL_BLOCKS, CreativeModeTabs.COMBAT, CreativeModeTabs.TOOLS_AND_UTILITIES))
            check(creativeTab(key).getDisplayItems().stream()
                            .noneMatch(LatentOreItems::isOre),
                    "Latent Ore variants are not appended to an unrelated native creative tab: " + key);
        check(!CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), hasPermissions, server.registryAccess()),
                "Unchanged native parameters reuse the rebuilt tab cache");
    }

    private static CreativeModeTab creativeTab(ResourceKey<CreativeModeTab> key) {
        //? if >=26.1.2
        /*return BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(key);*/
        //? if <26.1.2
        return BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(key);
    }

    private static InteractionResult place(ItemStack stack, ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        BlockHitResult hit = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                Direction.UP, pos.below(), false);
        return ((BlockItem) stack.getItem()).place(new FixturePlaceContext(level, stack, hit));
    }

    /** Both native versions expose this constructor for non-player placement contexts. */
    private static final class FixturePlaceContext extends BlockPlaceContext {
        private FixturePlaceContext(ServerLevel level, ItemStack stack, BlockHitResult hit) {
            super(level, null, InteractionHand.MAIN_HAND, stack, hit);
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
