package dev.essence.packtester;

import java.nio.file.*;
import java.util.*;
import javax.swing.*;

public final class Main {
    private static volatile boolean shuttingDown;
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception e) { e.printStackTrace(System.err); if (!shuttingDown) System.exit(1); }
    }
    static void run(String[] args) throws Exception {
        String action = args.length == 0 ? "gui" : args[0];
        Set<String> flags = Set.of("--confirm-closed", "--launch");
        Set<String> values = Set.of("--project", "--instance", "--root", "--prism");
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String key = args[i];
            if (options.containsKey(key) || !flags.contains(key) && !values.contains(key)) throw new IllegalArgumentException("Unknown/duplicate option " + key);
            if (flags.contains(key)) options.put(key, "true");
            else { if (++i == args.length) throw new IllegalArgumentException("Missing value: " + key); options.put(key, args[i]); }
        }
        if (action.equals("help") || action.equals("--help")) {
            System.out.println("""
                    Pack Tester (Java 21+)
                      gui [--project PATH]
                      list [--root PRISM_ROOT] [--project PATH]
                      inspect --instance INSTANCE [--root PRISM_ROOT] [--project PATH]
                      deploy --instance INSTANCE --confirm-closed [--launch --prism EXE --root PRISM_ROOT] [--project PATH]
                      recover --instance INSTANCE --confirm-closed [--project PATH]
                    Instance means the directory containing instance.cfg and mmc-pack.json.
                    Deployment always builds first. No arbitrary-JAR or compatibility-override switch.
                    --confirm-closed confirms this target is closed and will remain closed throughout the operation.
                    """); return;
        }
        Path project = Path.of(options.getOrDefault("--project", ".")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(project.resolve("gradle/wrapper/gradle-wrapper.jar"))) throw new IllegalArgumentException("Run from the project root or pass --project");
        LocalSettings settings = LocalSettings.load(project);
        if (options.containsKey("--root")) settings.roots.add(Path.of(options.get("--root")).toAbsolutePath().normalize().toString());
        if (action.equals("gui")) { SwingUtilities.invokeLater(() -> new PackTesterWindow(project, settings).show()); return; }
        PrismDiscovery discovery = new PrismDiscovery();
        if (action.equals("list")) {
            for (var instance : discovery.discover(settings, System.out::println)) describe(project, instance);
            return;
        }
        if (!Set.of("inspect", "deploy", "recover").contains(action)) throw new IllegalArgumentException("Unknown action " + action + "; use help");
        if (!options.containsKey("--instance")) throw new IllegalArgumentException("Explicit --instance is required; CLI never deploys a remembered selection");
        Path path = Path.of(options.get("--instance")).toAbsolutePath().normalize();
        var target = discovery.inspect(path, discovery.rootFor(path, settings));
        describe(project, target);
        if (action.equals("inspect")) {
            System.out.println(new RunningCheck().inspect(target).detail());
            int own = 0;
            if (Files.isDirectory(target.mods())) try (var files = Files.list(target.mods())) {
                for (Path jar : files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                    var meta = ModMetadata.read(jar, target.loader());
                    if (meta.containsOwn() || meta.nestedOwn()) {
                        own++; System.out.println("Existing own-mod metadata: " + jar + "\n" + meta.mods() + "\nSHA-256: " + FilesEx.hash(jar) + "\nUnambiguous: " + meta.onlyOwn());
                    }
                }
            }
            if (own == 0) System.out.println("No installed Essence Ascendance copy found by mod metadata.");
            return;
        }
        PackService service = new PackService(project);
        if (action.equals("recover")) { service.recover(target, options.containsKey("--confirm-closed"), System.out::println); return; }
        String exe = options.getOrDefault("--prism", settings.executable);
        Cancellation cancel = new Cancellation();
        Thread worker = Thread.currentThread();
        Thread hook = new Thread(() -> {
            shuttingDown = true;
            cancel.cancel();
            System.err.println("Cancellation requested; waiting for this build/rollback to finish safely. No shared processes are terminated.");
            try { worker.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "pack-tester-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        try { service.execute(target, options.containsKey("--confirm-closed"), options.containsKey("--launch"), exe == null ? null : Path.of(exe), cancel, System.out::println); }
        finally {
            try { Runtime.getRuntime().removeShutdownHook(hook); }
            catch (IllegalStateException ignored) { /* Shutdown hook is already waiting for this worker. */ }
        }
    }
    static void describe(Path project, PrismDiscovery.Instance i) {
        System.out.println(i + "\nMinecraft: " + i.minecraft() + " | Loader: " + i.loader() + " " + i.loaderVersion()
                + "\nMods: " + i.mods() + "\n" + new Compatibility(project).instance(i));
    }
}
