package com.mistaboom.essence_ascendance.attunement;

import java.util.*;

/** Common registry shared by generation, gameplay, skills, commands and presentation. */
public final class AttunementActivityRegistry {
    private static final Map<String, AttunementActivity> ACTIVITIES = new LinkedHashMap<>();
    static {
        register(new AttunementActivity("deal_damage", "essence_ascendance:offense", "health", "damage", true));
        register(new AttunementActivity("defeat_enemies", "essence_ascendance:offense", "health", "deaths", true));
        register(new AttunementActivity("take_damage", "essence_ascendance:defense", "health", "incoming_damage", true));
        register(new AttunementActivity("prevent_damage", "essence_ascendance:defense", "health", "incoming_damage", true));
        register(new AttunementActivity("reflect_damage", "essence_ascendance:defense", "health", "incoming_damage", false));
        register(new AttunementActivity("heal_health", "essence_ascendance:vitality", "health", "health", true));
        register(new AttunementActivity("restore_hunger", "essence_ascendance:vitality", "food", "food_restoration", true));
        register(new AttunementActivity("consume_hunger", "essence_ascendance:vitality", "food", "hunger", true));
        register(new AttunementActivity("resist_harmful_effects", "essence_ascendance:vitality", "health", "health", true));
        register(new AttunementActivity("run", "essence_ascendance:mobility", "blocks", "run", true));
        register(new AttunementActivity("swim", "essence_ascendance:mobility", "blocks", "swim", true));
        register(new AttunementActivity("fly", "essence_ascendance:mobility", "blocks", "fly", false));
        register(new AttunementActivity("explore_biome", "essence_ascendance:mobility", "discoveries", "exploration", true));
        register(new AttunementActivity("explore_dimension", "essence_ascendance:mobility", "discoveries", "exploration", true));
        register(new AttunementActivity("gather_resource_blocks", "essence_ascendance:gathering", "value", "gathering_value", true));
        register(new AttunementActivity("harvest_crops", "essence_ascendance:gathering", "value", "crop_value", true));
        register(new AttunementActivity("catch_fish", "essence_ascendance:gathering", "value", "fish_value", true));
        register(new AttunementActivity("gain_experience", "essence_ascendance:utility", "experience", "experience", true));
        register(new AttunementActivity("enchant_items", "essence_ascendance:utility", "value", "material_value", true, "minecraft:enchanting_table"));
        register(new AttunementActivity("use_anvils", "essence_ascendance:utility", "value", "material_value", true, "minecraft:anvil"));
        register(new AttunementActivity("repair_equipment", "essence_ascendance:utility", "durability", "durability", true, "minecraft:anvil"));
        register(new AttunementActivity("trade_villagers", "essence_ascendance:utility", "value", "material_value", true));
        register(new AttunementActivity("brew_potions", "essence_ascendance:utility", "value", "material_value", true, "minecraft:brewing_stand"));
        register(new AttunementActivity("use_looms", "essence_ascendance:utility", "value", "material_value", true, "minecraft:loom"));
        register(new AttunementActivity("use_grindstones", "essence_ascendance:utility", "value", "material_value", true, "minecraft:grindstone"));
        register(new AttunementActivity("use_smithing_tables", "essence_ascendance:utility", "value", "material_value", true, "minecraft:smithing_table"));
        register(new AttunementActivity("use_cartography_tables", "essence_ascendance:utility", "value", "material_value", true, "minecraft:cartography_table"));
        register(new AttunementActivity("use_stonecutters", "essence_ascendance:utility", "value", "material_value", true, "minecraft:stonecutter"));
    }
    private AttunementActivityRegistry() {}
    public static synchronized void register(AttunementActivity activity) {
        if (ACTIVITIES.putIfAbsent(activity.id(), activity) != null)
            throw new IllegalArgumentException("Duplicate Attunement activity: " + activity.id());
    }
    public static synchronized List<AttunementActivity> values() { return List.copyOf(ACTIVITIES.values()); }
    public static synchronized AttunementActivity get(String id) { return ACTIVITIES.get(id); }
}
