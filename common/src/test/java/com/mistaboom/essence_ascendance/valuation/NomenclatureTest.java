package com.mistaboom.essence_ascendance.valuation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Lexical contract and read-only registry-ID replay; no pack/world startup. */
public final class NomenclatureTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            long started = System.nanoTime();
            StringBuilder out = new StringBuilder("item\toffense\tdefense\tvitality\tmobility\tgathering\tutility\trules\n");
            for (String id : Files.readAllLines(Path.of(args[0]))) {
                var a = ProceduralItemNomenclature.analyze(id);
                out.append(id).append('\t').append(a.offense()).append('\t').append(a.defense()).append('\t')
                        .append(a.vitality()).append('\t').append(a.mobility()).append('\t').append(a.gathering())
                        .append('\t').append(a.utility()).append('\t').append(String.join(";", a.matches())).append('\n');
            }
            Files.writeString(Path.of(args[1]), out);
            System.out.println("Lexical ID replay ms=" + (System.nanoTime() - started) / 1_000_000);
            return;
        }
        for (String id : List.of("x:ironpaxel", "x:electricChainsaw", "x:bronze_aiot", "x:buzzsaw", "x:crook"))
            check(ProceduralItemNomenclature.analyze(id).gathering() > 0, id);
        for (String id : List.of("x:sword_statue", "x:shattered_sword_blade", "x:turbine_blade", "x:windmill_blade",
                "x:module_jetpack_unit", "x:glider_wing", "x:pickaxe_head", "x:framing_saw_pattern")) {
            var a = ProceduralItemNomenclature.analyze(id);
            check(a.offense() == 0 && a.mobility() == 0 && a.gathering() == 0 && a.utility() > 0, id);
        }
        check(ProceduralItemNomenclature.analyze("x:bone_meal").gathering() == 3, "phrase precedence");
        check(ProceduralItemNomenclature.analyze("x:blade_pottery_sherd").offense() == 0, "sherd form");
        check(ProceduralItemNomenclature.analyze("x:light_blue_wool").matches().stream().noneMatch(r -> r.startsWith("light:")), "color context");
        check(ProceduralItemNomenclature.analyze("x:shield_buckler_shield").defense()
                == ProceduralItemNomenclature.analyze("x:shield").defense(), "duplicate synonym budget");
        check(!ProceduralItemNomenclature.analyze("sword:television").present(), "namespace and whole tokens");
        check(!ProceduralItemNomenclature.analyze("x:ultimate_supreme_diamond").present(), "tier/marketing is no function");
        check(ProceduralItemNomenclature.analyze("x:opaque", "item.other.paxel").gathering() > 0, "standard key fallback");
        check(!ProceduralItemNomenclature.analyze("x:opaque", "translated Sword").present(), "display text excluded");
        check(ProceduralItemNomenclature.analyze("x:shield", "item.other.sword")
                .equals(ProceduralItemNomenclature.analyze("x:shield")), "registry precedence/localization independence");
        check(ProceduralItemNomenclature.analyze("x:basic_sawing_factory").utility() > 0, "machine role");
        check(ProceduralItemNomenclature.analyze("x:wand").utility() > 0, "spell implement is no combat measure");
        check(ProceduralItemNomenclature.analyze("x:energy_conduit").vitality() == 0, "conduit role disambiguation");
        check(ProceduralItemNomenclature.analyze("x:sword_display_case").offense() == 0, "furnishing form overrides contents");
        check(ProceduralItemNomenclature.analyze("x:oak_seat").utility() == 1, "observed furniture family");
        System.out.println("NomenclatureTest PASS: compound/alias/context/boundary/budget/localization contracts");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
