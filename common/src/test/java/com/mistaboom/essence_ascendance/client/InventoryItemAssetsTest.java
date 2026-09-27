package com.mistaboom.essence_ascendance.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime resource contract for the Blockbench-authored inventory sprites. */
public final class InventoryItemAssetsTest {
    private static final String ROOT = "/assets/essence_ascendance/";
    private static final List<String> TIERS = List.of(
            "latent", "dormant", "awakened", "resonant", "ascendant", "transcendent"
    );
    private static int checks;

    public static void main(String[] args) throws Exception {
        checkGenerated("raw_latent_ore", "item/raw_latent_ore");
        checkBlock("raw_latent_ore_block", "block/raw_latent_ore_block");

        checkLayered("ascendance_melee_weapon", "minecraft:item/handheld", linked(
                "layer0", "essence_ascendance:item/ascendance_melee_weapon/base",
                "layer1", "essence_ascendance:item/ascendance_melee_weapon/accent",
                "layer2", "essence_ascendance:item/ascendance_melee_weapon/nochange"
        ));
        checkBowState("ascendance_ranged_weapon", "minecraft:item/bow", "");
        checkBowState("ascendance_ranged_weapon_pulling_0", "minecraft:item/bow_pulling_0", "pulling_0_");
        checkBowState("ascendance_ranged_weapon_pulling_1", "minecraft:item/bow_pulling_1", "pulling_1_");
        checkBowState("ascendance_ranged_weapon_pulling_2", "minecraft:item/bow_pulling_2", "pulling_2_");
        checkLayered("ascendance_caster", "minecraft:item/handheld", linked(
                "layer0", "essence_ascendance:item/ascendance_caster/base",
                "layer1", "essence_ascendance:item/ascendance_caster/accent",
                "layer2", "essence_ascendance:item/ascendance_caster/nochange"
        ));
        checkOpaquePixel(ROOT + "textures/item/ascendance_caster/nochange.png", 12, 12,
                "Caster grip remains at its authored pivot");
        checkCasterHandOffset();
        checkLayered("ascendance_pickaxe", "minecraft:item/handheld", linked(
                "layer0", "essence_ascendance:item/ascendance_pickaxe/base",
                "layer1", "essence_ascendance:item/ascendance_pickaxe/accent",
                "layer2", "essence_ascendance:item/ascendance_pickaxe/nochange"
        ));
        checkLayered("ascendance_axe", "minecraft:item/handheld", linked(
                "layer0", "essence_ascendance:item/ascendance_axe/base",
                "layer1", "essence_ascendance:item/ascendance_axe/accent",
                "layer2", "essence_ascendance:item/ascendance_axe/nochange"
        ));
        checkLayered("ascendance_shovel", "minecraft:item/handheld", linked(
                "layer0", "essence_ascendance:item/ascendance_shovel/base",
                "layer1", "essence_ascendance:item/ascendance_shovel/accent",
                "layer2", "essence_ascendance:item/ascendance_shovel/nochange"
        ));
        checkLayered("ascendance_hoe", "minecraft:item/handheld", linked(
                "layer0", "essence_ascendance:item/ascendance_hoe/base",
                "layer1", "essence_ascendance:item/ascendance_hoe/accent",
                "layer2", "essence_ascendance:item/ascendance_hoe/nochange"
        ));
        checkLayered("ascendance_helmet", "minecraft:item/generated", armorLayers("ascendance_helmet"));
        checkLayered("ascendance_chestplate", "minecraft:item/generated", armorLayers("ascendance_chestplate"));
        checkLayered("ascendance_leggings", "minecraft:item/generated", armorLayers("ascendance_leggings"));
        checkLayered("ascendance_boots", "minecraft:item/generated", armorLayers("ascendance_boots"));

        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("InventoryItemAssetsTest: " + checks + " sprite and model checks PASS");
    }

    private static Map<String, String> armorLayers(String item) {
        return linked(
                "layer0", "essence_ascendance:item/" + item + "/base",
                "layer1", "essence_ascendance:item/" + item + "/accent"
        );
    }

    private static void checkBowState(String model, String parent, String texturePrefix) throws Exception {
        String textureRoot = "essence_ascendance:item/ascendance_ranged_weapon/" + texturePrefix;
        checkLayered(model, parent, linked(
                "layer0", textureRoot + "base",
                "layer1", textureRoot + "accent",
                "layer2", textureRoot + "nochange"
        ));
    }

    private static LinkedHashMap<String, String> linked(String... keysAndValues) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            result.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return result;
    }

    private static void checkGenerated(String modelId, String texture) throws Exception {
        JsonObject model = json(ROOT + "models/item/" + modelId + ".json");
        check(model.get("parent").getAsString().equals("minecraft:item/generated"),
                modelId + " uses a flat generated item model");
        check(model.getAsJsonObject("textures").get("layer0").getAsString()
                        .equals("essence_ascendance:" + texture),
                modelId + " uses its authored untinted sprite");
        checkPng(ROOT + "textures/" + texture + ".png");
    }

    private static void checkBlock(String blockId, String texture) throws Exception {
        JsonObject block = json(ROOT + "models/block/" + blockId + ".json");
        check(block.get("parent").getAsString().equals("minecraft:block/cube_all"),
                blockId + " uses a full cube world model");
        check(block.getAsJsonObject("textures").get("all").getAsString()
                        .equals("essence_ascendance:" + texture),
                blockId + " uses its authored texture on every world face");
        JsonObject item = json(ROOT + "models/item/" + blockId + ".json");
        check(item.get("parent").getAsString().equals("essence_ascendance:block/" + blockId),
                blockId + " inventory model inherits the corner-view cube");
        checkPng(ROOT + "textures/" + texture + ".png");
    }

    private static void checkLayered(String item, String parent, Map<String, String> layers) throws Exception {
        for (String tier : TIERS) {
            JsonObject model = json(ROOT + "models/item/tier/" + tier + "/" + item + ".json");
            check(model.get("parent").getAsString().equals(parent), item + " preserves its item transform");
            JsonObject textures = model.getAsJsonObject("textures");
            check(textures.size() == layers.size(), item + " has only its labeled layers");
            for (Map.Entry<String, String> layer : layers.entrySet()) {
                check(textures.get(layer.getKey()).getAsString().equals(layer.getValue()),
                        item + " maps " + layer.getKey() + " consistently at " + tier);
            }
        }
        for (String texture : layers.values()) {
            String path = texture.substring("essence_ascendance:".length());
            checkPng(ROOT + "textures/" + path + ".png");
        }
    }

    private static JsonObject json(String resource) throws Exception {
        try (var stream = InventoryItemAssetsTest.class.getResourceAsStream(resource)) {
            check(stream != null, "Missing resource " + resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void checkPng(String resource) throws Exception {
        try (var stream = InventoryItemAssetsTest.class.getResourceAsStream(resource)) {
            check(stream != null, "Missing texture " + resource);
            var image = ImageIO.read(stream);
            check(image != null && image.getWidth() == 16 && image.getHeight() == 16,
                    resource + " is a readable 16x16 PNG");
        }
    }

    private static void checkOpaquePixel(String resource, int x, int y, String message) throws Exception {
        try (var stream = InventoryItemAssetsTest.class.getResourceAsStream(resource)) {
            check(stream != null, "Missing texture " + resource);
            var image = ImageIO.read(stream);
            check(image != null && x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()
                    && (image.getRGB(x, y) >>> 24) != 0, message);
        }
    }

    private static void checkCasterHandOffset() throws Exception {
        for (String tier : TIERS) {
            JsonObject display = json(ROOT + "models/item/tier/" + tier + "/ascendance_caster.json")
                    .getAsJsonObject("display");
            check(display.getAsJsonObject("thirdperson_righthand").getAsJsonArray("translation")
                            .equals(JsonParser.parseString("[0,-2,-3.5]")),
                    "Caster right-hand model keeps its vertical offset and moves one additional unit behind the arm");
            check(display.getAsJsonObject("thirdperson_lefthand").getAsJsonArray("translation")
                            .equals(JsonParser.parseString("[0,-2,-3.5]")),
                    "Caster left-hand model mirrors the same arm-relative offset");
        }
    }

    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }
}
