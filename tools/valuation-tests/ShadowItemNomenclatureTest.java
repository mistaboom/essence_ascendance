package com.mistaboom.essence_ascendance.valuation;

import java.util.List;
import java.util.Locale;

/** Dependency-free regression checks; run tools/test_shadow_nomenclature.ps1 with JDK 21. */
public final class ShadowItemNomenclatureTest {
    private static int checks;

    private ShadowItemNomenclatureTest() {
    }

    public static void main(String[] args) {
        for (String noun : List.of("sword", "dagger", "crossbow", "mace", "tnt", "dynamite", "bullet")) {
            positive(noun, 0);
        }
        for (String noun : List.of("shield", "helmet", "chestplate", "leggings", "boots", "leather")) {
            positive(noun, 1);
        }
        for (String noun : List.of("medkit", "bandage", "antidote", "healing_tonic", "totem_of_undying", "red_bed")) {
            positive(noun, 2);
        }
        for (String noun : List.of("rail", "ladder", "scaffolding", "compass", "jet_pack", "jetpack",
                "ender_pearl", "wind_charge", "glider", "elevator", "agility_charm")) {
            positive(noun, 3);
        }
        for (String noun : List.of("drill", "pickaxe", "quarry", "miner", "harvester", "shears", "fishing_rod")) {
            positive(noun, 4);
        }
        for (String noun : List.of("circuit", "battery", "capacitor", "pipe", "cable", "processor", "controller")) {
            positive(noun, 5);
        }

        for (String prefix : List.of("basic", "advanced", "elite", "ultimate", "superior", "reinforced")) {
            absent(prefix);
            equalWeights(parse(prefix + "_sword"), parse("sword"), "tier adjective must not add strength");
            equalWeights(parse(prefix + "drill"), parse("drill"), "concatenated prefix only identifies a noun");
        }
        for (String prefix : List.of("wooden", "iron", "diamond", "electric", "powered")) {
            equalWeights(parse(prefix + "drill"), parse("drill"), "safe compound");
        }
        equalWeights(parse("sword_blade_dagger_sword"), parse("sword"), "synonyms saturate");
        equalWeights(parse("pipe_cable_wire_battery"), parse("pipe"), "automation synonyms saturate");
        equalWeights(parse("factory/tools/poweredDrill_02"), parse("drill"), "slash/camel/digit tokenization");
        equalWeights(parse("iron-pickaxe"), parse("iron.pickaxe"), "separator invariance");
        equalWeights(parse("iron/pickaxe"), parse("iron_pickaxe"), "path invariance");
        equalWeights(parse("IRON_PICKAXE"), parse("iron_pickaxe"), "case invariance");

        for (String path : List.of("bowl", "rainbow", "crossbowling", "airshipwreck", "unrelatedhelmet",
                "unknown_crystal", "heart_of_the_sea", "opaque_0042", "", "advanced_elite_ultimate")) {
            absent(path);
        }
        check(!ShadowItemNomenclature.analyze(null, null).present(), "null inputs");
        check(!ShadowItemNomenclature.analyze("sword:opaque").present(), "namespace is not evidence");
        check(!ShadowItemNomenclature.analyze("mod:opaque", "item.sword.opaque").present(),
                "description namespace is not evidence");
        check(!ShadowItemNomenclature.analyze("mod:opaque", "unrecognized.mod.sword").present(),
                "nonstandard description keys are not guessed");

        for (String path : List.of("blade_pottery_sherd", "heart_pottery_sherd", "miner_pottery_sherd",
                "music_disc_ward", "wolf_spawn_egg", "armor_stand", "sword_banner_pattern", "dagger_painting")) {
            ShadowItemNomenclature.Analysis result = parse(path);
            check(result.utility() > 0, "object form: " + path);
            check(result.offense() == 0 && result.defense() == 0 && result.vitality() == 0
                    && result.mobility() == 0 && result.gathering() == 0, "decorative false-positive guard: " + path);
        }
        check(parse("tube_coral").matches().stream().noneMatch(match -> match.equals("automation:tube")),
                "tube coral is not a pipe");
        check(parse("dead_tube_coral").vitality() == 0, "dead coral is not living material");
        check(parse("iron_sword").matches().equals(parse("iron_sword").matches()), "stable match order");
        check(parse("wind_charge").mobility() > parse("wind_charge").offense(), "wind propulsion");
        check(parse("totem_of_undying").vitality() > parse("totem_of_undying").defense(), "revival");

        ShadowItemNomenclature.Analysis fallback = ShadowItemNomenclature.analyze("mod:device_07", "item.mod.jetpack");
        check(fallback.source().equals("description_key") && fallback.mobility() > 0, "opaque-key fallback");
        ShadowItemNomenclature.Analysis primary = ShadowItemNomenclature.analyze("mod:sword", "item.mod.medkit");
        check(primary.source().equals("registry_path") && primary.vitality() == 0, "registry evidence wins");
        equalWeights(ShadowItemNomenclature.analyze("mod:sword", "item.mod.sword"), parse("sword"),
                "description key cannot double count");
        boolean immutable = false;
        try {
            primary.matches().add("invented");
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "immutable analysis");

        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            equalWeights(parse("IRON_PICKAXE"), parse("iron_pickaxe"), "locale-independent semantics");
        } finally {
            Locale.setDefault(previous);
        }
        System.out.println("PASS: " + checks + " nomenclature regression checks.");
    }

    private static ShadowItemNomenclature.Analysis parse(String path) {
        return ShadowItemNomenclature.analyze("example:" + path);
    }

    private static void positive(String path, int axis) {
        check(weights(parse(path))[axis] > 0, "expected role: " + path);
    }

    private static void absent(String path) {
        check(!parse(path).present(), "no lexical evidence: " + path);
    }

    private static void equalWeights(ShadowItemNomenclature.Analysis first,
                                     ShadowItemNomenclature.Analysis second, String message) {
        double[] a = weights(first);
        double[] b = weights(second);
        for (int i = 0; i < a.length; i++) {
            check(Math.abs(a[i] - b[i]) < 0.000001, message + " / axis " + i);
        }
    }

    private static double[] weights(ShadowItemNomenclature.Analysis a) {
        return new double[]{a.offense(), a.defense(), a.vitality(), a.mobility(), a.gathering(), a.utility()};
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
