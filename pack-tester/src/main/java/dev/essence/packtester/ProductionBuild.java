package dev.essence.packtester;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

final class ProductionBuild {
    record Artifact(Path path, String hash, String loader, String minecraft) { }
    record Receipt(String request, String loader, String minecraft, String archive, String sha256) { }
    static List<String> command(Path project, String loader, String request) throws IOException {
        if (!Set.of("fabric", "neoforge").contains(loader)) throw new IOException("Unsupported build loader: " + loader);
        Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        // Invoke exactly the wrapper's Java entry point. No cmd.exe, shell interpolation or path quoting.
        return Commands.checked(List.of(java.toString(), "-Dorg.gradle.appname=gradlew", "-classpath", project.resolve("gradle/wrapper/gradle-wrapper.jar").toString(),
                "org.gradle.wrapper.GradleWrapperMain", "--console=plain", "--no-configuration-cache",
                ":packTester" + (loader.equals("fabric") ? "Fabric" : "Neoforge") + "Artifact", "-PpackTesterRequest=" + request));
    }
    Artifact build(Path project, PrismDiscovery.Instance target, Cancellation cancel, Consumer<String> log) throws Exception {
        cancel.check();
        String request = UUID.randomUUID().toString();
        Path receiptPath = project.resolve(".pack-tester/results/" + request + ".json");
        log.accept("Building :" + target.loader() + ":remapJar using this repository's Gradle wrapper.");
        Process process = new ProcessBuilder(command(project, target.loader(), request)).directory(project.toFile()).redirectErrorStream(true).start();
        // Cancellation suppresses installation immediately, but drains this invocation to completion.
        // Killing a shared daemon could interrupt unrelated work; this tool never does that.
        try (var reader = process.inputReader()) { for (String line; (line = reader.readLine()) != null;) log.accept(line); }
        int exit = process.waitFor();
        try {
            cancel.check();
            if (exit != 0) throw new IOException("Gradle failed (exit " + exit + "). Installed mod was not changed.");
            Receipt receipt = FilesEx.JSON.fromJson(Files.readString(receiptPath), Receipt.class);
            if (receipt == null || !request.equals(receipt.request()) || !target.loader().equals(receipt.loader()) || !target.minecraft().equals(receipt.minecraft()))
                throw new IOException("Missing/mismatched build receipt");
            Path jar = Path.of(receipt.archive()).toRealPath();
            Path outputRoot = project.resolve(target.loader()).resolve("build").toRealPath();
            if (!jar.startsWith(outputRoot) || !FilesEx.hash(jar).equals(receipt.sha256())) throw new IOException("Build artifact changed or escaped its loader build directory");
            ModMetadata.production(jar, target.loader());
            return new Artifact(jar, receipt.sha256(), receipt.loader(), receipt.minecraft());
        } finally { Files.deleteIfExists(receiptPath); }
    }
}
