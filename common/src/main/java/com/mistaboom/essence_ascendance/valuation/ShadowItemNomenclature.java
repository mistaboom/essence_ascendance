package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Low-confidence semantic hints derived from common Minecraft/modded item names.
 *
 * This parser is intentionally subordinate to tags, runtime item classes,
 * recipes, loot, progression, and other structured data. Names are useful for
 * unfamiliar modded items that do not participate in conventional tags, but a
 * name must never be treated as proof of rarity or acquisition difficulty.
 */
final class ShadowItemNomenclature {

    private static final Set<String> OFFENSE = Set.of(
            "sword", "greatsword", "longsword", "shortsword", "blade", "dagger",
            "spear", "halberd", "glaive", "katana", "mace", "weapon",
            "bow", "crossbow", "rifle", "pistol", "gun", "cannon", "launcher",
            "arrow", "bolt", "bullet", "ammo", "grenade", "bomb", "explosive"
    );

    private static final Set<String> DEFENSE = Set.of(
            "helmet", "chestplate", "leggings", "greaves", "armor", "armour",
            "shield", "buckler", "barrier", "ward", "protector"
    );

    private static final Set<String> VITALITY = Set.of(
            "food", "meal", "stew", "soup", "bread", "apple", "berry", "berries",
            "meat", "steak", "heart", "health", "healing", "regeneration", "regen",
            "elixir", "tonic"
    );

    private static final Set<String> MOBILITY = Set.of(
            "elytra", "jetpack", "glider", "wings", "wing", "minecart", "boat",
            "raft", "saddle", "vehicle", "hoverboard", "teleporter", "teleport",
            "waystone", "warp", "portal", "thruster"
    );

    private static final Set<String> GATHERING = Set.of(
            "pickaxe", "shovel", "hoe", "axe", "hatchet", "sickle", "scythe",
            "drill", "excavator", "quarry", "miner", "mining", "harvester",
            "harvest", "saw", "hammer"
    );

    private static final Set<String> UTILITY = Set.of(
            "machine", "generator", "processor", "controller", "circuit", "component",
            "upgrade", "matrix", "focus", "infuser", "crucible", "nexus", "pylon",
            "altar", "ritual", "anvil", "forge", "workbench", "assembler", "assembly",
            "beacon", "conduit", "enchant", "enchanter", "brewing", "brewer", "potion",
            "furnace", "smelter", "crusher", "pulverizer", "grinder", "mixer", "press",
            "pump", "pipe", "tube", "cable", "wire", "battery", "capacitor", "tank",
            "storage", "barrel", "backpack", "wrench", "motor", "engine", "gearbox",
            "redstone", "repeater", "comparator", "observer", "hopper", "piston",
            "enchanting"
    );

    private ShadowItemNomenclature() {
    }

    static Analysis analyze(ResourceLocation itemId) {
        if (itemId == null) {
            return Analysis.EMPTY;
        }

        String path = normalize(itemId.getPath());
        Set<String> tokens = tokenize(path);
        MutableAnalysis result = new MutableAnalysis();

        collect(result, path, tokens, OFFENSE, Semantic.OFFENSE);
        collect(result, path, tokens, DEFENSE, Semantic.DEFENSE);
        collect(result, path, tokens, VITALITY, Semantic.VITALITY);
        collect(result, path, tokens, MOBILITY, Semantic.MOBILITY);
        collect(result, path, tokens, GATHERING, Semantic.GATHERING);
        collect(result, path, tokens, UTILITY, Semantic.UTILITY);

        if (result.matches.isEmpty()) {
            return Analysis.EMPTY;
        }

        double confidenceBonus = Math.min(0.08, 0.035 + result.matches.size() * 0.008);
        return new Analysis(
                result.offense,
                result.defense,
                result.vitality,
                result.mobility,
                result.gathering,
                result.utility,
                result.baseline,
                confidenceBonus,
                List.copyOf(result.matches)
        );
    }

    private static void collect(
            MutableAnalysis result,
            String path,
            Set<String> tokens,
            Set<String> vocabulary,
            Semantic semantic
    ) {
        for (String term : vocabulary) {
            if (!matches(path, tokens, term)) {
                continue;
            }
            result.add(semantic, term);
        }
    }

    private static boolean matches(String path, Set<String> tokens, String term) {
        if (tokens.contains(term)) {
            return true;
        }
        // Modded registries commonly concatenate equipment nouns (e.g.
        // "greatsword", "powereddrill") or add prefixes/suffixes around them.
        // Restrict fuzzy matching to reasonably distinctive nouns to avoid
        // turning arbitrary substrings into semantic evidence.
        if (term.length() < 5) {
            return false;
        }
        return path.equals(term)
                || path.startsWith(term + "_")
                || path.endsWith("_" + term)
                || path.contains("_" + term + "_")
                || path.endsWith(term);
    }

    private static String normalize(String path) {
        return path.toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace('.', '_');
    }

    private static Set<String> tokenize(String path) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : path.split("_+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    enum Baseline {
        NONE,
        WEAPON,
        ARMOR,
        TOOL,
        FOOD,
        TRANSPORT,
        AUTOMATION
    }

    enum Semantic {
        OFFENSE,
        DEFENSE,
        VITALITY,
        MOBILITY,
        GATHERING,
        UTILITY
    }

    record Analysis(
            double offense,
            double defense,
            double vitality,
            double mobility,
            double gathering,
            double utility,
            Baseline baseline,
            double confidenceBonus,
            List<String> matches
    ) {
        private static final Analysis EMPTY = new Analysis(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                Baseline.NONE,
                0.0,
                List.of()
        );

        boolean present() {
            return !matches.isEmpty();
        }
    }

    private static final class MutableAnalysis {
        private double offense;
        private double defense;
        private double vitality;
        private double mobility;
        private double gathering;
        private double utility;
        private Baseline baseline = Baseline.NONE;
        private final List<String> matches = new ArrayList<>();

        private void add(Semantic semantic, String term) {
            String signal = semantic.name().toLowerCase(Locale.ROOT) + ":" + term;
            if (matches.contains(signal)) {
                return;
            }
            matches.add(signal);

            switch (semantic) {
                case OFFENSE -> {
                    offense += 2.4;
                    chooseBaseline(Baseline.WEAPON);
                }
                case DEFENSE -> {
                    defense += 2.4;
                    vitality += 0.8;
                    chooseBaseline(Baseline.ARMOR);
                }
                case VITALITY -> {
                    vitality += 2.2;
                    chooseBaseline(Baseline.FOOD);
                }
                case MOBILITY -> {
                    mobility += 2.4;
                    utility += 0.4;
                    chooseBaseline(Baseline.TRANSPORT);
                }
                case GATHERING -> {
                    gathering += 2.4;
                    utility += 0.6;
                    chooseBaseline(Baseline.TOOL);
                }
                case UTILITY -> {
                    utility += 2.2;
                    chooseBaseline(Baseline.AUTOMATION);
                }
            }
        }

        private void chooseBaseline(Baseline candidate) {
            if (baseline == Baseline.NONE) {
                baseline = candidate;
                return;
            }
            // Equipment/tool identities are more informative as a floor than
            // generic machine/food words when a modded name contains several
            // role nouns.
            if (priority(candidate) > priority(baseline)) {
                baseline = candidate;
            }
        }

        private static int priority(Baseline baseline) {
            return switch (baseline) {
                case WEAPON, ARMOR, TOOL -> 3;
                case TRANSPORT, AUTOMATION -> 2;
                case FOOD -> 1;
                case NONE -> 0;
            };
        }
    }
}
