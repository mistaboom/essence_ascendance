package com.mistaboom.essence_ascendance.valuation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bounded, deterministic FUNCTION hints for poorly tagged vanilla/modded items.
 *
 * This class deliberately has no Minecraft/client dependency and knows nothing
 * about prices, rarity, progression, acquisition confidence or numeric tiers.
 * Only the registry path is read; a standard description-key path is a fallback
 * for opaque registry names. Neither mod namespaces nor translated display text
 * are evidence. In particular, an anvil-renamed item cannot influence valuation.
 */
final class ProceduralItemNomenclature {
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern SEPARATORS = Pattern.compile("[^a-z]+");
    // Remove only a color-modifying occurrence; a later actual light/lamp remains.
    private static final Pattern COLOR_LIGHT = Pattern.compile(
            "\\blight (?=blue\\b|gray\\b|grey\\b|green\\b|red\\b|brown\\b|pink\\b|purple\\b|cyan\\b|yellow\\b)");

    // Only these whole prefixes may be peeled from a concatenated tool noun.
    // They do NOT carry a tier/value vote. Arbitrary suffix matching would make
    // e.g. 'television' or decorative names appear to be functional equipment.
    private static final Set<String> COMPOUND_PREFIXES = Set.of(
            "wood", "wooden", "stone", "iron", "gold", "golden", "diamond", "netherite",
            "copper", "bronze", "steel", "silver", "tin", "lead", "brass", "osmium",
            "cobalt", "ruby", "sapphire", "emerald", "obsidian", "bone", "flint",
            "powered", "electric", "energy", "steam", "pneumatic", "mechanical",
            "basic", "advanced", "elite", "ultimate", "superior", "reinforced"
    );
    private static final List<String> COMPOUND_NOUNS = List.of(
            "greatsword", "longsword", "shortsword", "crossbow", "pickaxe", "chestplate",
            "jetpack", "chainsaw", "paxel", "helmet", "leggings", "shovel", "sword", "shield",
            "drill", "glider", "boots", "sickle", "scythe", "hatchet", "hammer", "axe", "hoe"
    );

    private ProceduralItemNomenclature() {
    }

    static Analysis analyze(String registryId) {
        return analyze(registryId, null);
    }

    static Analysis analyze(String registryId, String descriptionId) {
        String path = registryId == null ? "" : registryId;
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        Analysis primary = analyzePath(path, "registry_path");
        if (primary.present()) {
            return primary;
        }

        // Standard keys: item.<namespace>.<path> or block.<namespace>.<path>.
        // Strip BOTH the category and the namespace. Do not guess at custom
        // key layouts or feed the whole key through a noun matcher.
        if (descriptionId != null && (descriptionId.startsWith("item.")
                || descriptionId.startsWith("block."))) {
            int namespaceEnd = descriptionId.indexOf('.', descriptionId.indexOf('.') + 1);
            if (namespaceEnd >= 0 && namespaceEnd + 1 < descriptionId.length()) {
                String fallback = descriptionId.substring(namespaceEnd + 1);
                if (!normalize(path).equals(normalize(fallback))) {
                    return analyzePath(fallback, "description_key");
                }
            }
        }
        return Analysis.EMPTY;
    }

    private static Analysis analyzePath(String path, String source) {
        Words words = new Words(path);
        MutableAnalysis out = new MutableAnalysis(source);
        if (words.tokens.isEmpty()) {
            return Analysis.EMPTY;
        }

        // Object FORM takes precedence over artwork/material/creature names.
        // These early returns stop blade/heart/miner sherds, music_disc_ward,
        // tube coral, armor stands and spawn eggs impersonating useful gear.
        if (words.phrase("pottery sherd") || words.phrase("pottery shard")) {
            out.add("form:pottery_decoration", 0, 0, 0, 0, 0, 1);
            return out.finish();
        }
        if (words.phrase("music disc") || words.phrase("music disk") || words.has("record")) {
            out.add("form:music", 0, 0, 0, 0, 0, 1);
            return out.finish();
        }
        if (words.phrase("spawn egg") || words.has("spawnegg")) {
            out.add("form:spawn_egg", 0, 0, 0, 0, 0, 1);
            return out.finish();
        }
        if (words.has("coral")) {
            if (words.has("dead")) {
                out.add("form:dead_coral_decoration", 0, 0, 0, 0, 0, 0.8);
            } else {
                out.add("form:living_coral", 0, 0, 1.4, 0, 0.6, 0.4);
            }
            return out.finish();
        }
        if (words.phrase("armor stand") || words.phrase("armour stand")) {
            out.add("form:equipment_display", 0, 0, 0, 0, 0, 1);
            return out.finish();
        }
        if (words.has("banner") || words.has("painting")) {
            out.add("form:artwork", 0, 0, 0, 0, 0, 1);
            return out.finish();
        }
        if (words.phrase("smithing template")) {
            out.add("form:smithing_template", 0, 1.2, 0, 0, 0, 1.2);
            return out.finish();
        }
        if (words.any("chair seat bench stool sofa couch cabinet cupboard drawer drawers")
                || words.phrase("display case")) {
            out.add("form:furnishing", 0, 0, 0, 0, 0, 1);
            return out.finish();
        }

        // The object form wins over the depicted weapon or the machine it fits.
        // A module/unit is not the equipped machine; a blade/head is not a tool.
        if (words.any("statue statuette trophy plush plushie replica decorative ornament")) {
            out.add("form:depiction", 0, 0, 0, 0, 0, 1);
            return out.finish();
        }
        if (words.any("part parts component components module attachment casing housing frame blueprint pattern")
                || words.phrase("upgrade unit") || words.phrase("jetpack unit")
                || words.phrase("sword blade") || words.phrase("turbine blade")
                || words.phrase("rotary blade") || words.phrase("windmill blade")
                || words.phrase("pickaxe head") || words.phrase("axe head")
                || words.phrase("shovel head") || words.phrase("hammer head")
                || words.any("sawblade turbinerotorblade capacitorcell")) {
            out.add("form:component", 0, 0, 0, 0, 0, 0.6);
            return out.finish();
        }
        // Singular wings in this pack are anatomy or crafting parts. Even the
        // plural is merely a routing hypothesis, never a measured flight axis.
        if (words.has("wing")) {
            out.add("form:wing_component", 0, 0, 0, 0, 0, 0.6);
            return out.finish();
        }

        // A compound's functional meaning wins over a homonymous component.
        if (words.phrase("bone meal") || words.has("bonemeal")) {
            out.add("agriculture:bone_meal", 0, 0, 0.3, 0, 3, 0.3);
            return out.finish();
        }

        out.collect(words, "weapon", 3, 0, 0, 0, 0, 0,
                "sword greatsword longsword shortsword blade dagger spear halberd glaive katana "
                        + "mace rapier saber sabre cleaver weapon bow crossbow rifle pistol gun cannon "
                        + "launcher arrow bullet ammunition ammo grenade bomb explosive explosives dynamite tnt");
        out.collect(words, "armor", 0, 3, 0.6, 0, 0, 0,
                "helmet helm chestplate leggings greaves armor armour shield buckler barrier ward protector "
                        + "gauntlet gauntlets cuirass breastplate");
        out.collect(words, "footwear", 0, 2.2, 0, 0.8, 0, 0,
                "boots shoes sandals");
        out.collect(words, "food", 0, 0, 2.4, 0, 0, 0,
                "food meal stew soup bread apple berry berries meat steak elixir tonic cake cookie "
                        + "pie sandwich salad cheese yogurt sausage bacon jerky ration rations juice");
        out.collect(words, "recovery", 0, 0.6, 3, 0, 0, 0,
                "health healing regeneration regen rejuvenation restoration undying resurrection bandage "
                        + "bandages medkit antidote medicine");
        if (words.has("heart") && !words.has("sea")) {
            out.add("recovery:heart", 0, 0.4, 2.4, 0, 0, 0);
        }
        out.collect(words, "transport", 0, 0, 0, 3, 0, 0.4,
                "elytra jetpack glider wings wing minecart boat raft saddle vehicle hoverboard "
                        + "teleporter teleport teleportation waystone warp portal thruster rail rails "
                        + "ladder scaffolding grappling parachute balloon locomotive elevator");
        out.collect(words, "movement", 0, 0, 0, 2.4, 0, 0,
                "speed agility swiftness sprint sprinting leap jumping flight flying levitation");
        if (words.phrase("jet pack") || words.phrase("jump boots")) {
            out.add("transport:powered_movement", 0, 0, 0, 3, 0, 0.4);
        }
        out.collect(words, "navigation", 0, 0, 0, 2.4, 0, 0.6,
                "compass map sextant navigator navigation lodestone");
        out.collect(words, "harvesting", 0, 0, 0, 0, 3, 0.3,
                "pickaxe shovel hoe axe hatchet sickle scythe drill excavator quarry miner mining "
                        + "harvester harvest saw chainsaw buzzsaw hammer shears brush mattock lumberjack paxel aiot chisel crook");
        out.collect(words, "agriculture", 0, 0, 1, 0, 2, 0.3,
                "seed seeds sapling saplings propagule crop crops planter fertilizer fertiliser "
                        + "composter apiary beehive");
        out.collect(words, "protection_material", 0, 1.6, 0.8, 0, 0, 0.4,
                "leather hide scute carapace wool");
        out.collect(words, "barrier", 0, 2, 0, 0, 0, 1,
                "fence fences wall walls door trapdoor gate railing");
        out.collect(words, "rest", 0, 0, 2.4, 0.6, 0, 0.4,
                "bed hammock bedroll sleepingbag respawn");
        out.collect(words, "textile", 0, 0.8, 1.2, 0, 0, 0.8,
                "carpet blanket cushion");
        out.collect(words, "processing", 0, 0, 0, 0, 1.4, 1.6,
                "crusher pulverizer grinder smelter furnace foundry mill sawmill extractor sifter sieve "
                        + "macerator centrifuge electrolyzer crystallizer washer evaporator cutting sawing enriching crushing smelting");
        out.collect(words, "automation", 0, 0, 0, 0, 0, 2.4,
                "generator processor controller circuit infuser crucible nexus pylon altar ritual "
                        + "anvil forge workbench assembler assembly enchanter enchanting brewing brewer "
                        + "mixer press pump pipe tube cable wire battery capacitor storage barrel chest "
                        + "backpack wrench gearbox redstone repeater comparator observer hopper piston "
                        + "crafter dispenser dropper sensor detector switch lever button factory conduit transmitter "
                        + "transporter conveyor connector router terminal disk drive cell coil wirecoil computer monitor");
        // Generic component nouns are deliberately weaker than a specific role.
        out.collect(words, "component", 0, 0, 0, 0, 0, 0.6,
                "machine component upgrade matrix focus motor engine tank gear capacitorcell");
        out.collect(words, "knowledge", 0, 0, 0, 0, 0, 2,
                "book bookshelf lectern scroll tome experience potion wand grimoire tablet glyph rune");
        out.collect(words, "light", 0, 0, 0, 0, 0, 2,
                "torch lantern lamp bulb candle glowstone light");
        out.collect(words, "decoration", 0, 0, 0, 0, 0, 1.4,
                "banner painting dye pigment terracotta vase");
        out.collect(words, "plant", 0, 0, 1.2, 0, 0.8, 0.4,
                "flower flowers leaves foliage petal petals vine vines moss");
        out.collect(words, "elastic", 0, 0, 0, 1.6, 0, 1.6,
                "slime spring trampoline");

        if (words.phrase("fishing rod")) {
            out.add("harvesting:fishing_rod", 0, 0, 0, 0, 3, 0.4);
        }
        if (words.phrase("ender pearl") || words.phrase("ender eye")) {
            out.add("transport:ender_travel", 0, 0, 0, 3, 0, 0.4);
        }
        if (words.phrase("wind charge") || words.phrase("breeze rod")) {
            out.add("transport:wind_propulsion", 1, 0, 0, 2.4, 0, 0.2);
        }
        if (words.has("firework") || words.has("fireworks")) {
            out.add("transport:firework", 0.6, 0, 0, 2, 0, 0.8);
        } else if (words.has("rocket")) {
            out.add("propulsion:rocket", 1.8, 0, 0, 1.4, 0, 0.4);
        }
        if (words.phrase("fire charge") || words.phrase("end crystal")) {
            out.add("weapon:explosive_charge", 2.8, 0, 0, 0, 0.4, 0.6);
        }
        // A transport/energy conduit is not the standalone aquatic support object.
        if (words.has("conduit") && words.tokens.size() == 1) {
            out.add("support:conduit", 0, 0, 1.4, 1.4, 1, 1);
        }
        if (words.has("beacon")) {
            out.add("support:beacon", 0.6, 0.6, 1, 1, 1, 1.4);
        }
        if (words.has("campfire") || words.has("smoker")) {
            out.add("processing:cooking", 0, 0, 1.4, 0, 0, 1.4);
        }
        if (words.phrase("target dummy")) {
            out.add("training:target_dummy", 2, 1, 0, 0, 0, 0.5);
        }
        if (words.phrase("milk bucket")) {
            out.add("recovery:milk", 0, 0.5, 2.4, 0, 0, 0.4);
        }
        return out.finish();
    }

    private static String normalize(String value) {
        String camelSeparated = CAMEL_BOUNDARY.matcher(value).replaceAll("$1_$2");
        return SEPARATORS.matcher(camelSeparated.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    record Analysis(double offense, double defense, double vitality, double mobility,
                    double gathering, double utility, List<String> matches, String source) {
        static final Analysis EMPTY = new Analysis(0, 0, 0, 0, 0, 0, List.of(), "none");

        Analysis {
            matches = List.copyOf(matches);
        }

        boolean present() {
            return !matches.isEmpty();
        }

        boolean suppressesInheritedFunction() {
            return matches.contains("form:component") || matches.contains("form:depiction")
                    || matches.contains("form:wing_component") || matches.contains("form:furnishing");
        }
    }

    private static final class Words {
        private final String normalized;
        private final Set<String> tokens = new LinkedHashSet<>();

        Words(String path) {
            normalized = COLOR_LIGHT.matcher(normalize(path)).replaceAll("");
            if (!normalized.isBlank()) {
                for (String token : normalized.split(" +")) {
                    tokens.add(token);
                    // Add at most one matched compound noun per token. This
                    // cannot turn 'greatsword' into two independent sword votes.
                    for (String noun : COMPOUND_NOUNS) {
                        if (token.length() > noun.length() && token.endsWith(noun)
                                && COMPOUND_PREFIXES.contains(token.substring(0, token.length() - noun.length()))) {
                            tokens.add(noun);
                            break;
                        }
                    }
                }
            }
        }

        boolean has(String term) {
            return tokens.contains(term);
        }

        boolean any(String vocabulary) {
            for (String term : vocabulary.split(" ")) if (has(term)) return true;
            return false;
        }

        boolean phrase(String phrase) {
            return (" " + normalized + " ").contains(" " + phrase + " ");
        }
    }

    private static final class MutableAnalysis {
        private final String source;
        private final double[] weights = new double[6];
        private final List<String> matches = new ArrayList<>();

        MutableAnalysis(String source) {
            this.source = source;
        }

        void collect(Words words, String role, double offense, double defense, double vitality,
                     double mobility, double gathering, double utility, String vocabulary) {
            for (String term : vocabulary.split(" ")) {
                if (words.has(term)) {
                    add(role + ":" + term, offense, defense, vitality, mobility, gathering, utility);
                }
            }
        }

        void add(String match, double offense, double defense, double vitality,
                 double mobility, double gathering, double utility) {
            if (!matches.contains(match)) {
                matches.add(match);
            }
            // Synonyms cannot accumulate unlimited strength. The engine also
            // normalizes the ENTIRE name-hint vector to one small evidence vote.
            double[] incoming = {offense, defense, vitality, mobility, gathering, utility};
            for (int i = 0; i < weights.length; i++) {
                weights[i] = Math.max(weights[i], incoming[i]);
            }
        }

        Analysis finish() {
            if (matches.isEmpty()) {
                return Analysis.EMPTY;
            }
            return new Analysis(weights[0], weights[1], weights[2], weights[3], weights[4],
                    weights[5], matches, source);
        }
    }
}
