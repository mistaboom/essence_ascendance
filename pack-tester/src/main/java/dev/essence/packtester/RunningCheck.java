package dev.essence.packtester;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

final class RunningCheck {
    record State(boolean running, String detail) { }
    State inspect(PrismDiscovery.Instance target) {
        // A positive path match is instance-specific evidence. No argument/command line is logged.
        // Prism can send launch details via stdin, and Windows may hide process arguments.
        String directory = normalize(target.directory().toString()) + "/";
        try (var processes = ProcessHandle.allProcesses()) {
            for (ProcessHandle process : processes.toList()) {
                if (process.pid() == ProcessHandle.current().pid()) continue;
                var info = process.info();
                String command = info.command().orElse("").toLowerCase(Locale.ROOT);
                if (!(command.endsWith("java.exe") || command.endsWith("javaw.exe") || command.endsWith("/java"))) continue;
                for (String argument : info.arguments().orElse(new String[0])) {
                    String normalized = normalize(argument);
                    if (normalized.contains(directory) || normalized.equals(normalize(target.game().toString())))
                        return new State(true, "Java process " + process.pid() + " references this instance. Close its game before deploying.");
                }
            }
        } catch (RuntimeException ignored) { /* Unknown is never treated as stopped. */ }
        Path log = target.game().resolve("logs/latest.log");
        if (Files.isRegularFile(log)) {
            try (var channel = FileChannel.open(log, StandardOpenOption.READ); var lock = channel.tryLock(0, Long.MAX_VALUE, true)) {
                if (lock == null) return new State(true, "This instance's latest.log is locked. Close the game and retry.");
            } catch (IOException | java.nio.channels.OverlappingFileLockException e) {
                return new State(true, "Cannot safely access this instance's latest.log: " + e.getMessage());
            }
        }
        return new State(false, "Running state is UNKNOWN: Prism may hide launch arguments. An unlocked file does not prove Minecraft is stopped. Confirm this target is closed and keep it closed until completion.");
    }
    void requireClosed(PrismDiscovery.Instance target, boolean confirmed) throws IOException {
        var state = inspect(target);
        if (state.running() || !confirmed) throw new IOException(state.detail());
    }
    private static String normalize(String text) { return text.replace('\\', '/').toLowerCase(Locale.ROOT); }
}
