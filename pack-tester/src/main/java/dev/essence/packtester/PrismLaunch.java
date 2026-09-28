package dev.essence.packtester;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class PrismLaunch {
    List<String> command(PrismDiscovery.Instance target, Path executable) throws IOException {
        if (executable == null || !Files.isRegularFile(executable) || !executable.getFileName().toString().equalsIgnoreCase("prismlauncher.exe"))
            throw new IOException("Choose the installed prismlauncher.exe using Prism Settings.");
        if (target.root() == null) throw new IOException("This directly browsed instance is not associated with a verified Prism application root. Add its root using Browse.");
        List<Path> directories = PrismDiscovery.instanceDirectories(target.root());
        List<Path> matches = new ArrayList<>();
        for (Path directory : directories) {
            Path candidate = directory.resolve(target.id());
            if (Files.exists(candidate.resolve("instance.cfg"))) matches.add(candidate.toRealPath());
        }
        if (matches.size() != 1 || !matches.getFirst().equals(target.directory().toRealPath()))
            throw new IOException("Prism instance ID is missing/ambiguous in this application's configured directories: " + target.id());
        return Commands.checked(List.of(executable.toAbsolutePath().toString(), "--dir", target.root().toString(), "--launch", target.id()));
    }
    void verifyExecutable(Path executable, Consumer<String> log) throws Exception {
        // --version does not launch an instance or inspect account data.
        Path output = Files.createTempFile("pack-tester-prism-version-", ".txt");
        try {
            Process process = new ProcessBuilder(Commands.checked(List.of(executable.toString(), "--version"))).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                throw new IOException("Prism version probe timed out. It was left running; close that probe before retrying. No Prism process is terminated by this tool.");
            }
            String version = Files.readString(output).strip();
            if (process.exitValue() != 0 || !identifiesPrism(version)) throw new IOException("Executable did not identify itself as Prism Launcher: " + version);
            log.accept("Verified executable: " + version);
        } finally {
            try { Files.deleteIfExists(output); }
            catch (IOException ignored) { log.accept("Version-probe output retained at " + output); }
        }
    }
    static boolean identifiesPrism(String version) {
        return java.util.regex.Pattern.compile("(?im)^prism\\s*launcher\\s+\\d[^\\r\\n]*$").matcher(version).find();
    }
    void launch(PrismDiscovery.Instance target, Path executable, Consumer<String> log) throws Exception {
        List<String> command = command(target, executable);
        new ProcessBuilder(command).directory(target.root().toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        log.accept("Prism launch requested for instance ID " + target.id() + " using application root " + target.root() + ". Check latest.log for the game outcome.");
    }
}
