package dev.essence.packtester;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads the same explicit target definitions used by Gradle, independent of the IDE active version. */
final class TargetCatalog {
    record Target(String id, String minecraft, String loader, String archiveTask, int javaVersion,
                  String fabricLoader, String architecturyApi, String neoforgeMinimum) {
        String receiptTask() { return ":packTester" + id + "Artifact"; }
    }
    static final class UnsupportedTarget extends IOException {
        UnsupportedTarget(String message) { super(message); }
    }
    static Target require(Path project, String minecraft, String loader) throws IOException {
        var catalog = JsonParser.parseString(Files.readString(project.resolve("gradle/targets.json"))).getAsJsonObject();
        if (catalog.get("schema").getAsInt() != 1) throw new IOException("Unsupported target catalog schema");
        Target selected = null;
        for (var value : catalog.getAsJsonArray("targets")) {
            var target = value.getAsJsonObject();
            if (!minecraft.equals(target.get("minecraft").getAsString()) || !loader.equals(target.get("loader").getAsString())) continue;
            if (!target.get("implemented").getAsBoolean())
                throw new UnsupportedTarget("Minecraft " + minecraft + " " + loader + " is planned but not implemented.");
            if (selected != null) throw new IOException("Duplicate target definition: " + minecraft + " " + loader);
            var profile = catalog.getAsJsonObject("versions").getAsJsonObject(minecraft);
            String id = target.get("id").getAsString();
            if (!id.matches("[A-Za-z0-9]+")) throw new IOException("Invalid target task identifier");
            selected = new Target(id, minecraft, loader, target.get("archiveTask").getAsString(), profile.get("java").getAsInt(),
                    profile.get("fabricLoader").getAsString(), profile.get("architecturyApi").getAsString(),
                    profile.get("neoforgeMinimum").getAsString());
        }
        if (selected == null) throw new UnsupportedTarget("Unsupported Minecraft/loader pair: " + minecraft + " " + loader);
        return selected;
    }
}
