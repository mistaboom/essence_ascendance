package com.mistaboom.essence_ascendance.skill.balance;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.nio.file.*;

/** Isolated development receipt. Bootstrap is a reference fixture, never native environment evidence. */
public final class SkillProgressionAudit {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var saved = args.length > 1 ? com.mistaboom.essence_ascendance.balance.generated.BalanceProfileStore.read(Path.of(args[1])) : null;
        var runtime = saved == null ? RuntimeBalanceDefinition.bootstrap() : RuntimeBalanceDefinition.fromJson(saved.section("runtime"));
        runtime.validate();
        var root = new JsonObject();
        root.addProperty("scope", saved == null ? "Isolated native-class bootstrap reference; no server, world, native census or measured gameplay"
                : "Validated saved native profile inspected without regeneration; source=" + Path.of(args[1]).toAbsolutePath() + "; integrity=" + saved.integrity());
        if (saved != null) root.add("nativeGeneration", saved.section("metadata").get("generation"));
        root.add("runtime", runtime.toJson());
        com.mistaboom.essence_ascendance.client.SharedPresentationTest.verify(runtime);
        root.addProperty("transactionStateChecks", NativeProfileStateAudit.verify(runtime));
        var rows = new JsonArray(); var gson = com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.GSON;
        SkillBalanceRuntime.withCurves(runtime.skillCurves(), () -> {
        for (var skill : SkillRegistry.values()) {
            var row = new JsonObject();
            row.addProperty("skill", skill.id().toString());
            row.addProperty("catalogTier", skill.catalogRequiredTierId().toString());
            row.addProperty("category", skill.essenceId().toString());
            row.addProperty("resolvedTier", skill.requiredTierId().toString());
            row.add("resolvedRanks", gson.toJsonTree(runtime.skillCurves().get(skill.id().toString()).ranks()));
            row.add("mechanics", gson.toJsonTree(skill.progressionRequirements()));
            row.add("semantics", gson.toJsonTree(SkillBalanceSemantics.require(skill.id())));
            row.add("intrinsicDecision", gson.toJsonTree(SkillProgressionPolicy.evaluate(skill, SkillBalanceSemantics.require(skill.id()),
                    gson.toJsonTree(com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings.defaults()).getAsJsonObject())));
            row.addProperty("implementation", com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry.get(skill.id()).getClass().getName());
            row.add("rankGates", gson.toJsonTree(skill.rankPolicy().rankGates()));
            row.add("prerequisites", gson.toJsonTree(skill.prerequisiteRanks(1)));
            row.add("requirements", gson.toJsonTree(skill.requirements(1).stream().map(r -> r.id().toString()).toList()));
            rows.add(row);
        }
        return null;
        });
        root.add("skills", rows);
        var layouts = new JsonObject();
        SkillBalanceRuntime.withCurves(runtime.skillCurves(), () -> {
            for (var essence : EssenceTypes.ORDERED) {
                var definitions = SkillRegistry.values(essence.id());
                var layout = com.mistaboom.essence_ascendance.client.nexus.NexusSkillTreeLayout.build(definitions);
                if (layout.nodes().size() != definitions.size()) throw new AssertionError("Missing/duplicate native layout nodes");
                for (var a : layout.nodes()) for (var b : layout.nodes()) if (a != b) {
                    int width = com.mistaboom.essence_ascendance.client.nexus.NexusSkillTreeLayout.NODE_WIDTH;
                    int height = com.mistaboom.essence_ascendance.client.nexus.NexusSkillTreeLayout.NODE_HEIGHT;
                    if (!(a.x() + width <= b.x() || b.x() + width <= a.x() || a.y() + height <= b.y() || b.y() + height <= a.y()))
                        throw new AssertionError("Overlapping native layout nodes");
                }
                layouts.add(essence.id().toString(), gson.toJsonTree(layout));
            }
            return null;
        });
        root.add("treeLayouts", layouts);
        Path output = Path.of(args[0]).toAbsolutePath(); Files.createDirectories(output.getParent());
        Files.writeString(output, gson.toJson(root));
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("SkillProgressionAudit: " + rows.size() + " registered skills written to " + output);
    }
}
