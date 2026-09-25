package com.mistaboom.essence_ascendance.ore;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Real state NBT and item component codecs; native mining/placement remain in-game acceptance checks. */
public final class LatentOreHostTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (BlockState state : List.of(Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
                Blocks.NETHERRACK.defaultBlockState(), Blocks.END_STONE.defaultBlockState(),
                Blocks.DEEPSLATE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X))) {
            CompoundTag data = LatentOreHost.encode(state);
            check(data.getString("Name").equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()),
                    "Durable block identity is a registry name");
            check(LatentOreHost.decode(data).orElseThrow() == state, "All host state properties round-trip");
            // A vanilla registered carrier exercises the same standard component save/stack/network contracts
            // without starting a loader or registering production mod content in a plain JavaExec test.
            ItemStack original = new ItemStack(Items.STONE, 17);
            original.applyComponents(LatentOreHost.components(state));
            ItemStack saved = ItemStack.parse(registries, original.save(registries)).orElseThrow();
            check(ItemStack.isSameItemSameComponents(original, saved) && saved.getCount() == 17,
                    "Inventory save/load preserves exact host components and count");
            check(LatentOreHost.read(saved).orElseThrow() == state, "Item decoding uses the same state as BE encoding");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                ItemStack.STREAM_CODEC.encode(buffer, original);
                ItemStack received = ItemStack.STREAM_CODEC.decode(buffer);
                check(ItemStack.isSameItemSameComponents(original, received), "Normal item packet codec preserves identity");
            } finally { buffer.release(); }
        }

        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack deepslate = new ItemStack(Items.STONE);
        LatentOreHost.write(stone, Blocks.STONE.defaultBlockState());
        LatentOreHost.write(deepslate, Blocks.DEEPSLATE.defaultBlockState());
        check(!ItemStack.isSameItemSameComponents(stone, deepslate), "Different hosts never stack as indistinguishable ore");
        check(ItemStack.isSameItemSameComponents(stone, stone.copy()), "The same host stacks normally");
        ItemStack oriented = deepslate.copy();
        LatentOreHost.write(oriented, Blocks.DEEPSLATE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        check(!ItemStack.isSameItemSameComponents(oriented, deepslate), "Relevant orientation distinguishes stacks");
        check(LatentOreHost.read(ItemStack.EMPTY).isEmpty(), "Missing item data is handled without inventing a host");
        check(LatentOreHost.read(new ItemStack(Items.STONE)).isEmpty(), "Missing components remain missing");

        CompoundTag invalid = new CompoundTag();
        invalid.putString("Name", "fixture:absent_rock");
        check(LatentOreHost.decode(invalid).isEmpty(), "Unknown host is rejected");
        for (var block : List.of(Blocks.AIR, Blocks.WATER, Blocks.BEDROCK, Blocks.CHEST, Blocks.GLASS, Blocks.OAK_SLAB)) {
            invalid.putString("Name", BuiltInRegistries.BLOCK.getKey(block).toString());
            check(LatentOreHost.decode(invalid).isEmpty(), "Unsafe host is rejected: " + block);
        }
        invalid = LatentOreHost.encode(Blocks.DEEPSLATE.defaultBlockState());
        invalid.getCompound("Properties").putString("axis", "diagonal");
        check(LatentOreHost.decode(invalid).isEmpty(), "Unknown property value cannot change appearance silently");
        invalid = LatentOreHost.encode(Blocks.STONE.defaultBlockState());
        CompoundTag properties = new CompoundTag(); properties.putString("unknown", "true"); invalid.put("Properties", properties);
        check(LatentOreHost.decode(invalid).isEmpty(), "Unknown property is rejected");

        check(Arrays.stream(LatentOreBlock.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("getTicker")),
                "Ore inherits EntityBlock's non-ticking default");
        resourceContracts();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("LatentOreHostTest: " + checks + " identity, standard item codecs and content checks PASS");
    }

    private static void resourceContracts() throws Exception {
        String base = "/data/essence_ascendance/";
        var loot = json(base + "loot_table/blocks/adaptive_latent_ore.json");
        String serialized = loot.toString();
        check(serialized.contains("minecraft:silk_touch") && serialized.contains("minecraft:fortune"),
                "Shared native loot retains Silk Touch and Fortune");
        check(serialized.contains("essence_ascendance:raw_latent_ore"), "Shared native loot returns raw Latent Ore");
        for (String suffix : List.of("", "_blasting"))
            check(json(base + "recipe/latent_ingot_from_ore" + suffix + ".json").getAsJsonObject("ingredient")
                    .get("tag").getAsString().equals("essence_ascendance:latent_ores"), "All hosts share one cooking ingredient");
        for (String old : List.of("latent_ore", "deepslate_latent_ore", "netherrack_latent_ore", "end_stone_latent_ore")) {
            check(LatentOreHostTest.class.getResource("/assets/essence_ascendance/blockstates/" + old + ".json") == null,
                    "Placeholder blockstate is removed: " + old);
            check(LatentOreHostTest.class.getResource(base + "loot_table/blocks/" + old + ".json") == null,
                    "Placeholder loot is removed: " + old);
        }
        Path src = Path.of("src/main/java/com/mistaboom/essence_ascendance");
        if (!Files.isDirectory(src)) src = Path.of("common").resolve(src);
        String block = Files.readString(src.resolve("ore/LatentOreBlock.java"));
        check(block.contains("getCloneItemStack") && block.contains("LatentOreHost::stack")
                        && block.contains("LatentOreHost.read(stack).ifPresent(ore::setHost)")
                        && block.contains("LatentOreHost.write(stack, host)"),
                "Pick, placement and loot route through the same tested host codec");
        String gathering = Files.readString(src.resolve("gathering/NaturalOreDropService.java"));
        check(gathering.contains("ore.setHost(source)") && gathering.contains("pos, lootEntity, player"),
                "Gathering preserves its selected source host in native loot context");
    }

    private static com.google.gson.JsonObject json(String path) throws Exception {
        try (var stream = LatentOreHostTest.class.getResourceAsStream(path)) {
            check(stream != null, "Required resource exists: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
