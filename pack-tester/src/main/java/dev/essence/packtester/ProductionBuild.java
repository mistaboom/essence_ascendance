package dev.essence.packtester;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

final class ProductionBuild {
    record Artifact(Path path, String hash, String loader, String minecraft) { }
    record Receipt(String request, String target, String task, String loader, String minecraft, String archive, String sha256) { }
    static List<String> command(Path project, String minecraft, String loader, String request) throws IOException {
        var target = TargetCatalog.require(project, minecraft, loader);
        Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        // Invoke exactly the wrapper's Java entry point. No cmd.exe, shell interpolation or path quoting.
        return Commands.checked(List.of(java.toString(), "-Dorg.gradle.appname=gradlew", "-classpath", project.resolve("gradle/wrapper/gradle-wrapper.jar").toString(),
                "org.gradle.wrapper.GradleWrapperMain", "--console=plain", "--no-configuration-cache",
                target.receiptTask(), "-PpackTesterRequest=" + request));
    }
    Artifact build(Path project, PrismDiscovery.Instance target, Cancellation cancel, Consumer<String> log) throws Exception {
        cancel.check();
        var definition = TargetCatalog.require(project, target.minecraft(), target.loader());
        String request = UUID.randomUUID().toString();
        Path receiptPath = project.resolve(".pack-tester/results/" + request + ".json");
        log.accept("Building Minecraft " + target.minecraft() + " " + target.loader() + " using this repository's Gradle wrapper.");
        Process process = new ProcessBuilder(command(project, target.minecraft(), target.loader(), request)).directory(project.toFile()).redirectErrorStream(true).start();
        // Cancellation suppresses installation immediately, but drains this invocation to completion.
        // Killing a shared daemon could interrupt unrelated work; this tool never does that.
        try (var reader = process.inputReader()) { for (String line; (line = reader.readLine()) != null;) log.accept(line); }
        int exit = process.waitFor();
        try {
            cancel.check();
            if (exit != 0) throw new IOException("Gradle failed (exit " + exit + "). Installed mod was not changed.");
            Receipt receipt = FilesEx.JSON.fromJson(Files.readString(receiptPath), Receipt.class);
            return validateReceipt(project, definition, request, receipt);
        } finally { Files.deleteIfExists(receiptPath); }
    }
    static Artifact validateReceipt(Path project, TargetCatalog.Target target, String request, Receipt receipt) throws IOException {
        if (receipt == null || !request.equals(receipt.request()) || !target.id().equals(receipt.target())
                || !target.loader().equals(receipt.loader()) || !target.minecraft().equals(receipt.minecraft())
                || !(":" + target.loader() + ":" + target.minecraft() + ":" + target.archiveTask()).equals(receipt.task()))
            throw new IOException("Missing/mismatched build receipt");
        Path jar = Path.of(receipt.archive()).toRealPath();
        Path outputRoot = project.resolve(target.loader()).resolve("versions").resolve(target.minecraft()).resolve("build/libs").toRealPath();
        if (!jar.getParent().equals(outputRoot) || !Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")
                || !FilesEx.hash(jar).equals(receipt.sha256())) throw new IOException("Build artifact changed or escaped its target build/libs directory");
        ModMetadata.production(jar, target.loader(), target.minecraft());
        return new Artifact(jar, receipt.sha256(), receipt.loader(), receipt.minecraft());
    }
}
