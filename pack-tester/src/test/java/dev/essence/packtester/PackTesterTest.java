package dev.essence.packtester;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

/** Real filesystem fixtures plus injected build/launch boundaries. Never touches a user's pack. */
public final class PackTesterTest {
    private static Path project, fixtures;
    private static int checks;
    private static final java.util.function.Consumer<String> QUIET = s -> { };
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("echo-args")) {
            for (int i = 1; i < args.length; i++) System.out.println(Base64.getEncoder().encodeToString(args[i].getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return;
        }
        project = Path.of(args[0]).toAbsolutePath().normalize();
        fixtures = Files.createTempDirectory(project.resolve("pack-tester/build"), "fixtures & (space) ! % ü ");
        if (args.length > 1 && args[1].equals("production-smoke")) { productionSmoke(); return; }
        discovery(); compatibility(); deployment(); orchestration(); lockedDestination(); arguments();
        System.out.println("PASS: " + checks + " Pack Tester checks. Fixtures: " + fixtures);
    }
    private static void productionSmoke() throws Exception {
        for (String loader : List.of("neoforge", "fabric")) {
            var target = instance(fixtures.resolve("production " + loader), null, loader, "1.21.1", "minecraft");
            // Run this main directly, outside Gradle, to exercise the actual wrapper backend.
            var service = new PackService(project, new ProductionBuild()::build, new Deployment(), new PackService.Launcher() {
                public void prepare(PrismDiscovery.Instance t, Path exe, java.util.function.Consumer<String> log) { throw new AssertionError("No Prism launch in smoke tests"); }
                public void launch(PrismDiscovery.Instance t, Path exe, java.util.function.Consumer<String> log) { throw new AssertionError("No Prism launch in smoke tests"); }
            }, (t, c) -> { if (!t.directory().startsWith(fixtures)) throw new AssertionError("Only fixtures may be deployed"); });
            var result = service.execute(target, true, false, null, new Cancellation(), System.out::println);
            ModMetadata.production(result.destination(), loader);
            try (var zip = new ZipFile(result.destination().toFile())) {
                check(zip.stream().noneMatch(e -> e.getName().startsWith("dev/essence/packtester/") || e.getName().startsWith("org/tomlj/")), loader + " production artifact excludes developer tooling");
            }
            check(FilesEx.hash(result.destination()).equals(result.hash()), loader + " real wrapper build + receipt + fixture deployment");
        }
        System.out.println("PASS: both real production workflows; only fixture destinations written. " + fixtures);
    }
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks++; System.out.println("PASS " + label);
    }
    @FunctionalInterface interface Throwing { void run() throws Exception; }
    private static void fails(Throwing action, String label) throws Exception {
        try { action.run(); } catch (Exception expected) { checks++; System.out.println("PASS " + label + " [" + expected.getClass().getSimpleName() + "]"); return; }
        throw new AssertionError("Expected refusal: " + label);
    }
    private static PrismDiscovery.Instance instance(Path path, Path root, String loader, String minecraft, String layout) throws IOException {
        Files.createDirectories(path.resolve(layout).resolve("mods"));
        Files.writeString(path.resolve("instance.cfg"), "[General]\nConfigVersion=1.3\nInstanceType=OneSix\nname=Duplicate display name\n");
        String uid = switch (loader) { case "fabric" -> "net.fabricmc.fabric-loader"; case "neoforge" -> "net.neoforged"; case "forge" -> "net.minecraftforge"; default -> "org.quiltmc.quilt-loader"; };
        String version = loader.equals("fabric") ? "0.19.3" : "21.1.251";
        Files.writeString(path.resolve("mmc-pack.json"), "{\"formatVersion\":1,\"components\":[{\"uid\":\"net.minecraft\",\"version\":\"" + minecraft + "\"},{\"uid\":\"" + uid + "\",\"version\":\"" + version + "\"}]}");
        var target = new PrismDiscovery().inspect(path, root);
        jar(target.mods().resolve("arbitrary-dependency-name.jar"), loader, "architectury", "13.0.11", "dep", false);
        if (loader.equals("fabric")) jar(target.mods().resolve("fabric-api.jar"), loader, "fabric-api", "0.116.15+1.21.1", "dep", false);
        return target;
    }
    private static PrismDiscovery.Instance fixture(String label) throws IOException {
        return instance(fixtures.resolve(label), null, "neoforge", "1.21.1", "minecraft");
    }
    private static Path jar(Path path, String loader, String id, String version, String content, boolean bundle) throws IOException {
        Files.createDirectories(path.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            if (loader.equals("fabric")) entry(zip, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\"}");
            else entry(zip, loader.equals("neoforge") ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml", "modLoader=\"javafml\"\nloaderVersion=\"[4,)\"\n[[mods]]\nmodId=\"" + id + "\"\nversion=\"" + version + "\"\n" + (bundle ? "[[mods]]\nmodId=\"another_mod\"\nversion=\"1\"\n" : ""));
            entry(zip, "content.txt", content);
            if (id.equals(ModMetadata.OWN)) entry(zip, "com/mistaboom/essence_ascendance/" + loader + "/EssenceAscendance" + (loader.equals("fabric") ? "Fabric" : "NeoForge") + ".class", "fixture-only");
        }
        return path;
    }
    private static void entry(ZipOutputStream zip, String name, String content) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry(); }
    private static ProductionBuild.Artifact artifact(String loader) throws IOException {
        Path jar = jar(fixtures.resolve("artifacts").resolve(UUID.randomUUID() + ".jar"), loader, ModMetadata.OWN, "1.0.0", UUID.randomUUID().toString(), false);
        return new ProductionBuild.Artifact(jar, FilesEx.hash(jar), loader, "1.21.1");
    }
    private static void discovery() throws Exception {
        Path root = fixtures.resolve("Portable Prism & test"), custom = fixtures.resolve("custom, instances");
        Files.createDirectories(root);
        Files.writeString(root.resolve("prismlauncher.cfg"), "[General]\nConfigVersion=1.3\nInstanceDir=\"" + custom.toString().replace('\\', '/') + "\"\nAdditionalInstanceDirs=more, \"more, too\"\n");
        var a = instance(custom.resolve("id-one"), root, "neoforge", "1.21.1", "minecraft");
        var b = instance(custom.resolve("id-two"), root, "fabric", "1.21.1", ".minecraft");
        LocalSettings settings = new LocalSettings(); settings.roots.add(root.toString()); settings.selected = a.directory().toString();
        var results = new PrismDiscovery().discover(settings, QUIET).stream().filter(i -> i.root() != null && i.root().equals(root)).toList();
        check(results.size() == 2 && results.get(0).name().equals(results.get(1).name()), "Duplicate display names preserve separate folder identities");
        check(b.game().getFileName().toString().equals(".minecraft"), "Dot-minecraft layout");
        Files.createDirectory(b.directory().resolve("minecraft"));
        check(new PrismDiscovery().inspect(b.directory(), root).game().getFileName().toString().equals("minecraft"), "Prism precedence when both game directories exist");
        check(PrismDiscovery.instanceDirectories(root).contains(root.resolve("more, too")), "Configured additional instance directories with quoted commas");
        check(PrismDiscovery.iniList("\"C:\\\\Packs\\\\test\"").getFirst().equals("C:\\Packs\\test"), "QSettings escaped Windows paths");
        check(PrismDiscovery.iniList("\"M\\xfcde\"").getFirst().equals("M\ufcde"), "QSettings hex escapes");
        settings.browsed.add(a.directory().toString());
        check(new PrismDiscovery().rootFor(a.directory(), settings).equals(root), "Custom root association for direct instance browse");
        Path missing = fixtures.resolve("missing metadata"); Files.createDirectories(missing); Files.writeString(missing.resolve("instance.cfg"), "InstanceType=OneSix\n");
        check(!new PrismDiscovery().inspect(missing, null).problem().isEmpty(), "Missing metadata is unresolved");
        Files.delete(a.directory().resolve("instance.cfg"));
        Files.delete(a.directory().resolve("mmc-pack.json"));
        var afterRemoval = new PrismDiscovery().discover(settings, QUIET);
        check(afterRemoval.stream().noneMatch(i -> i.directory().equals(a.directory())) && settings.selected.equals(a.directory().toString()), "Discovery never replaces a disappeared selection");
        settings.save(fixtures); check(LocalSettings.load(fixtures).selected.equals(settings.selected), "Absolute selection round trips through local settings");
    }
    private static void compatibility() throws Exception {
        Compatibility c = new Compatibility(project);
        check(c.instance(fixture("compatible")).status().equals("SUPPORTED"), "NeoForge metadata and dependencies supported");
        check(c.instance(instance(fixtures.resolve("fabric"), null, "fabric", "1.21.1", ".minecraft")).status().equals("SUPPORTED"), "Fabric remains supported");
        check(c.instance(instance(fixtures.resolve("wrong mc"), null, "neoforge", "1.21.2", "minecraft")).status().equals("INCOMPATIBLE"), "Different Minecraft target blocked despite broad mod range");
        check(c.instance(instance(fixtures.resolve("forge"), null, "forge", "1.21.1", "minecraft")).status().equals("INCOMPATIBLE"), "Forge is not NeoForge");
        var old = fixture("old loader"); Files.writeString(old.directory().resolve("mmc-pack.json"), Files.readString(old.directory().resolve("mmc-pack.json")).replace("21.1.251", "20.4.1"));
        check(c.instance(new PrismDiscovery().inspect(old.directory(), null)).status().equals("INCOMPATIBLE"), "Old loader blocked");
        var missing = fixture("missing dependency"); Files.delete(missing.mods().resolve("arbitrary-dependency-name.jar"));
        check(c.instance(missing).status().equals("INCOMPATIBLE"), "Missing required Architectury blocks deployment");
        var placeholder = fixture("empty legacy descriptor");
        Path mixed = placeholder.mods().resolve("dual-descriptor.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(mixed))) {
            entry(zip, "META-INF/neoforge.mods.toml", "modLoader=\"javafml\"\nloaderVersion=\"[4,)\"\n[[mods]]\nmodId=\"ordinary_mod\"\nversion=\"1\"\n");
            entry(zip, "META-INF/mods.toml", " \n\t");
        }
        check(c.instance(placeholder).status().equals("SUPPORTED"), "Valid NeoForge identity accepts empty legacy Forge placeholder");
        try (var zip = new ZipOutputStream(Files.newOutputStream(mixed))) {
            entry(zip, "META-INF/mods.toml", "");
        }
        check(c.instance(placeholder).status().equals("UNRESOLVED"), "Empty descriptor alone cannot establish mod identity");
        try (var zip = new ZipOutputStream(Files.newOutputStream(mixed))) {
            entry(zip, "META-INF/neoforge.mods.toml", "[[mods]]\nmodId=\"ordinary_mod\"\nversion=\"1\"\n");
            entry(zip, "META-INF/mods.toml", "modLoader=\"javafml\"\n");
        }
        check(c.instance(placeholder).status().equals("UNRESOLVED"), "Nonempty incomplete legacy metadata is still refused");
        // Recreate with the placeholder and a second identity to retain bundle refusal.
        try (var zip = new ZipOutputStream(Files.newOutputStream(mixed))) {
            entry(zip, "META-INF/neoforge.mods.toml", "modLoader=\"javafml\"\nloaderVersion=\"[4,)\"\n[[mods]]\nmodId=\"essence_ascendance\"\nversion=\"1\"\n[[mods]]\nmodId=\"ordinary_mod\"\nversion=\"1\"\n");
            entry(zip, "META-INF/mods.toml", "");
        }
        check(c.instance(placeholder).status().equals("UNRESOLVED"), "Empty placeholder does not bypass ambiguous own-mod bundle refusal");
        legacyTemplates(c);
        check(Compatibility.satisfies("13.0.11", "[13.0.11,)"), "Maven inclusive minimum");
        check(!Compatibility.satisfies("0.19.2", ">=0.19.3"), "Fabric minimum enforced");
        fails(() -> Compatibility.satisfies("13.0.11-beta", "[13.0.11,)"), "Unknown version qualifier is unresolved");
        fails(() -> Compatibility.satisfies("1.0", "^1.0"), "Unknown range does not silently pass");
    }
    private static void legacyTemplates(Compatibility compatibility) throws Exception {
        String active = "modLoader=\"javafml\"\nloaderVersion=\"[4,)\"\n[[mods]]\nmodId=\"ordinary_mod\"\nversion=\"1\"\n";
        String template = "modLoader=\"javafml\"\nloaderVersion=\"${loader_version_range}\"\n[[mods]]\nmodId=\"${mod_id}\"\nversion=\"${mod_version}\"\n"
                + "[[dependencies.${mod_id}]] # retained Forge MDK template\nmodId=\"forge\"\nversionRange=\"${forge_version_range}\"\n"
                + "[[dependencies.${mod_id}]]\nmodId=\"minecraft\"\nversionRange=\"${minecraft_version_range}\"\n";
        var target = fixture("unexpanded inactive descriptor");
        Path archive = target.mods().resolve("dual-template.jar");
        descriptors(archive, active, template);
        check(compatibility.instance(target).status().equals("SUPPORTED"), "Valid NeoForge identity accepts an inactive unexpanded Forge template");
        var metadata = ModMetadata.read(archive, "neoforge");
        check(metadata.mods().size() == 1 && metadata.mods().getFirst().id().equals("ordinary_mod"), "Inactive template contributes no invented identity or dependency version");
        String hash = FilesEx.hash(archive);
        var deployment = new Deployment();
        try (var lease = deployment.acquire(target)) { deployment.install(lease, target, artifact("neoforge"), new Cancellation(), QUIET); }
        check(FilesEx.hash(archive).equals(hash), "Fixture deployment preserves unrelated JAR with inactive template metadata");
        fails(() -> ModMetadata.read(archive, "forge"), "Unexpanded template remains invalid when Forge is selected");
        descriptors(archive, null, template);
        check(compatibility.instance(target).status().equals("UNRESOLVED"), "Unexpanded template alone cannot establish active mod identity");
        descriptors(archive, template, null);
        check(compatibility.instance(target).status().equals("UNRESOLVED"), "An active NeoForge template remains unresolved");
        descriptors(archive, active, template.replace("${mod_version}", "1"));
        check(compatibility.instance(target).status().equals("UNRESOLVED"), "Inactive template allowance requires unresolved identity and version together");
        descriptors(archive, active, template + "broken = $invalid\n");
        check(compatibility.instance(target).status().equals("UNRESOLVED"), "Other malformed inactive TOML remains unresolved");
        descriptors(archive, active, template + "[[mods]]\nmodId=\"essence_ascendance\"\nversion=\"1\"\n");
        check(compatibility.instance(target).status().equals("UNRESOLVED"), "Resolved own identity beside template cannot bypass ambiguity checks");
        descriptors(archive, active.replace("ordinary_mod", "essence_ascendance") + "[[mods]]\nmodId=\"ordinary_mod\"\nversion=\"1\"\n", template);
        check(compatibility.instance(target).status().equals("UNRESOLVED"), "Inactive template does not bypass an active multi-mod own bundle");
        descriptors(archive, active, template);
        Files.delete(target.mods().resolve("arbitrary-dependency-name.jar"));
        check(compatibility.instance(target).status().equals("INCOMPATIBLE"), "Inactive template allowance retains required Architectury gate");
    }
    private static void descriptors(Path archive, String neoforge, String forge) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            if (neoforge != null) entry(zip, "META-INF/neoforge.mods.toml", neoforge);
            if (forge != null) entry(zip, "META-INF/mods.toml", forge);
        }
    }
    private static void deployment() throws Exception {
        var target = fixture("safe deploy"); var artifact = artifact("neoforge");
        Path oldA = jar(target.mods().resolve("renamed-own.jar"), "neoforge", ModMetadata.OWN, "0.9", "old A", false);
        Path oldB = jar(target.mods().resolve("own-copy.jar"), "fabric", ModMetadata.OWN, "0.9", "old B", false);
        Path unrelated = jar(target.mods().resolve("essence_ascendance-lookalike.jar"), "neoforge", "unrelated", "1", "keep", false);
        String unrelatedHash = FilesEx.hash(unrelated), hashA = FilesEx.hash(oldA), hashB = FilesEx.hash(oldB);
        Deployment d = new Deployment();
        try (var lease = d.acquire(target)) {
            fails(() -> d.acquire(target), "Concurrent deployment to one target blocked");
            var result = d.install(lease, target, artifact, new Cancellation(), QUIET);
            check(FilesEx.hash(result.destination()).equals(artifact.hash()), "Deployed bytes and SHA-256 match staged artifact");
            check(FilesEx.hash(result.backup().resolve("renamed-own.jar")).equals(hashA) && FilesEx.hash(result.backup().resolve("own-copy.jar")).equals(hashB), "Duplicate own copies from either loader backed up outside mods");
            check(!Files.exists(oldA) && !Files.exists(oldB), "Only previous own copies removed from active mods");
        }
        check(FilesEx.hash(unrelated).equals(unrelatedHash), "Filename lookalike unrelated JAR preserved");
        var ambiguous = fixture("ambiguous bundle"); jar(ambiguous.mods().resolve("bundle.jar"), "neoforge", ModMetadata.OWN, "1", "bundle", true);
        try (var lease = d.acquire(ambiguous)) { fails(() -> d.install(lease, ambiguous, artifact, new Cancellation(), QUIET), "Multi-mod bundle is never removed"); }
        var nested = fixture("nested bundle");
        try (var zip = new ZipOutputStream(Files.newOutputStream(nested.mods().resolve("host.jar")))) {
            entry(zip, "fabric.mod.json", "{\"id\":\"host\",\"version\":\"1\"}"); zip.putNextEntry(new ZipEntry("META-INF/jarjar/own.jar")); zip.write(Files.readAllBytes(artifact.path())); zip.closeEntry();
        }
        check(new Compatibility(project).instance(nested).status().equals("UNRESOLVED"), "Nested own mod blocks duplicate installation");
        for (String point : List.of("after-backup", "before-install", "after-install")) {
            var t = fixture("rollback " + point); Path old = jar(t.mods().resolve("original.jar"), "neoforge", ModMetadata.OWN, "0.9", "original", false); String hash = FilesEx.hash(old);
            Deployment broken = new Deployment(p -> { if (p.equals(point)) throw new IOException("Injected disk failure"); });
            try (var lease = broken.acquire(t)) { fails(() -> broken.install(lease, t, artifact, new Cancellation(), QUIET), "Failure at " + point); }
            check(FilesEx.hash(old).equals(hash) && !Files.exists(t.mods().resolve("essence_ascendance-neoforge.jar")), "Rollback restores old bytes at " + point);
        }
        var cancelled = fixture("cancel during replacement"); Path original = jar(cancelled.mods().resolve("original.jar"), "neoforge", ModMetadata.OWN, "1", "original", false); String originalHash = FilesEx.hash(original);
        Cancellation token = new Cancellation(); Deployment cancelling = new Deployment(p -> { if (p.equals("after-backup")) token.cancel(); });
        try (var lease = cancelling.acquire(cancelled)) { fails(() -> cancelling.install(lease, cancelled, artifact, token, QUIET), "Cancellation while replacing rolls back"); }
        check(FilesEx.hash(original).equals(originalHash), "Cancellation preserves installed original");
        var crashed = fixture("crash recovery"); Path before = jar(crashed.mods().resolve("original.jar"), "neoforge", ModMetadata.OWN, "1", "recover me", false); String beforeHash = FilesEx.hash(before);
        Deployment crash = new Deployment(p -> { if (p.equals("after-install")) throw new AssertionError("Simulated process death"); });
        try (var lease = crash.acquire(crashed)) { try { crash.install(lease, crashed, artifact, new Cancellation(), QUIET); } catch (AssertionError expected) { } }
        try (var lease = d.acquire(crashed)) {
            fails(() -> d.requireRecovered(lease), "Interrupted journal blocks new deployments"); d.recover(lease, crashed, QUIET);
        }
        check(FilesEx.hash(before).equals(beforeHash), "Journal recovers a process death after replacement");
        var committed = fixture("committed recovery");
        Deployment commitCrash = new Deployment(p -> { if (p.equals("after-commit")) throw new IOException("Crash while finalizing receipt"); });
        try (var lease = commitCrash.acquire(committed)) { fails(() -> commitCrash.install(lease, committed, artifact, new Cancellation(), QUIET), "Failure after durable commit retains recoverable journal"); }
        try (var lease = d.acquire(committed)) { d.recover(lease, committed, QUIET); }
        check(FilesEx.hash(committed.mods().resolve("essence_ascendance-neoforge.jar")).equals(artifact.hash()), "Committed recovery retains verified new artifact");
        var conflict = fixture("recovery conflict");
        Path conflictOld = jar(conflict.mods().resolve("old.jar"), "neoforge", ModMetadata.OWN, "1", "previous", false);
        try (var lease = crash.acquire(conflict)) { try { crash.install(lease, conflict, artifact, new Cancellation(), QUIET); } catch (AssertionError expected) { } }
        jar(conflictOld, "neoforge", "another_mod", "1", "do not overwrite", false); String conflictHash = FilesEx.hash(conflictOld);
        try (var lease = d.acquire(conflict)) { fails(() -> d.recover(lease, conflict, QUIET), "Recovery refuses a file changed by another program"); }
        check(FilesEx.hash(conflictOld).equals(conflictHash), "Conflicting recovery preserves unrelated bytes and journal");
        var mismatch = fixture("artifact changed");
        var bad = new ProductionBuild.Artifact(artifact.path(), "0".repeat(64), "neoforge", "1.21.1");
        try (var lease = d.acquire(mismatch)) { fails(() -> d.install(lease, mismatch, bad, new Cancellation(), QUIET), "Changed artifact hash blocked before modifying mods"); }
        var collision = fixture("destination collision"); jar(collision.mods().resolve("essence_ascendance-neoforge.jar"), "neoforge", "unrelated", "1", "keep", false);
        try (var lease = d.acquire(collision)) { fails(() -> d.install(lease, collision, artifact, new Cancellation(), QUIET), "Unrelated exact destination collision never overwritten"); }
    }
    private static void orchestration() throws Exception {
        var artifact = artifact("neoforge"); AtomicInteger launches = new AtomicInteger(), builds = new AtomicInteger();
        PackService.Launcher launch = new PackService.Launcher() {
            public void prepare(PrismDiscovery.Instance t, Path exe, java.util.function.Consumer<String> log) { }
            public void launch(PrismDiscovery.Instance t, Path exe, java.util.function.Consumer<String> log) throws Exception {
                check(FilesEx.hash(t.mods().resolve("essence_ascendance-neoforge.jar")).equals(artifact.hash()), "Launch observes successful verified deployment"); launches.incrementAndGet();
            }
        };
        PackService.ClosedCheck closed = (t, confirmed) -> { if (!confirmed) throw new IOException("Need explicit closed confirmation"); };
        var target = fixture("service failure"); Path old = jar(target.mods().resolve("old.jar"), "neoforge", ModMetadata.OWN, "1", "keep", false); String oldHash = FilesEx.hash(old);
        var failed = new PackService(project, (p,t,c,l) -> { builds.incrementAndGet(); throw new IOException("Build failed"); }, new Deployment(), launch, closed);
        fails(() -> failed.execute(target, true, true, null, new Cancellation(), QUIET), "Failed build blocks install and launch");
        check(FilesEx.hash(old).equals(oldHash) && launches.get() == 0, "Failed build leaves installed JAR untouched and does not launch");
        var service = new PackService(project, (p,t,c,l) -> { builds.incrementAndGet(); return artifact; }, new Deployment(), launch, closed);
        fails(() -> service.execute(target, false, true, null, new Cancellation(), QUIET), "Unknown running state requires explicit confirmation");
        check(builds.get() == 1, "No build before running-state confirmation");
        Cancellation token = new Cancellation(); token.cancel();
        fails(() -> service.execute(target, true, true, null, token, QUIET), "Cancelled operation never launches");
        service.execute(target, true, false, null, new Cancellation(), QUIET);
        check(launches.get() == 0, "Build & Deploy does not launch");
        service.execute(target, true, true, null, new Cancellation(), QUIET);
        check(launches.get() == 1, "Explicit launch runs exactly once after success");
        var changing = fixture("changed metadata");
        var changeService = new PackService(project, (p,t,c,l) -> { Files.writeString(t.directory().resolve("instance.cfg"), "InstanceType=OneSix\nname=Changed\n"); return artifact; }, new Deployment(), launch, closed);
        fails(() -> changeService.execute(changing, true, true, null, new Cancellation(), QUIET), "Metadata changes during build cannot redirect frozen operation");
        check(launches.get() == 1, "No launch after metadata change");
        var runningService = new PackService(project, (p,t,c,l) -> artifact, new Deployment(), launch, (t,c) -> { throw new IOException("Known running target"); });
        fails(() -> runningService.execute(target, true, true, null, new Cancellation(), QUIET), "Known running target cannot be overridden by confirmation");
        var cancelledBuild = new PackService(project, (p,t,c,l) -> { c.cancel(); return artifact; }, new Deployment(), launch, closed);
        fails(() -> cancelledBuild.execute(target, true, true, null, new Cancellation(), QUIET), "Cancellation after build suppresses deployment and launch");
        check(launches.get() == 1, "All failure and cancellation paths preserve launch gate");
        Path brokenProject = fixtures.resolve("broken wrapper project & spaces");
        Files.createDirectories(brokenProject.resolve("gradle/wrapper"));
        Files.writeString(brokenProject.resolve("gradle/wrapper/gradle-wrapper.jar"), "invalid wrapper fixture");
        var realFailedBuild = new PackService(project, (p,t,c,l) -> new ProductionBuild().build(brokenProject, t, c, l), new Deployment(), launch, closed);
        String installedHash = FilesEx.hash(target.mods().resolve("essence_ascendance-neoforge.jar"));
        fails(() -> realFailedBuild.execute(target, true, true, null, new Cancellation(), QUIET), "Actual wrapper subprocess failure leaves deployment untouched");
        check(FilesEx.hash(target.mods().resolve("essence_ascendance-neoforge.jar")).equals(installedHash) && launches.get() == 1, "Actual build failure preserves bytes and never launches");
    }
    private static void lockedDestination() throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows")) { System.out.println("SKIP Windows exclusive file-lock test"); return; }
        var target = fixture("exclusive Windows lock"); Path old = jar(target.mods().resolve("locked-own.jar"), "neoforge", ModMetadata.OWN, "1", "locked", false); String hash = FilesEx.hash(old);
        Path ready = target.directory().resolve("ready"), release = target.directory().resolve("release");
        String script = "$f=[IO.File]::Open($env:PACK_TEST_FILE,'Open','Read','None'); try { [IO.File]::WriteAllText($env:PACK_TEST_READY,'ready'); while(-not [IO.File]::Exists($env:PACK_TEST_RELEASE)) { Start-Sleep -Milliseconds 100 } } finally { $f.Dispose() }";
        var pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
        pb.environment().put("PACK_TEST_FILE", old.toString()); pb.environment().put("PACK_TEST_READY", ready.toString()); pb.environment().put("PACK_TEST_RELEASE", release.toString());
        Process process = pb.start();
        try {
            long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < until) Thread.sleep(50);
            check(Files.exists(ready), "Windows fixture acquired exclusive file lock");
            Deployment d = new Deployment(); var a = artifact("neoforge");
            try (var lease = d.acquire(target)) { fails(() -> d.install(lease, target, a, new Cancellation(), QUIET), "Locked installed JAR refuses replacement"); }
        } finally { Files.writeString(release, "release"); if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) process.destroy(); }
        check(FilesEx.hash(old).equals(hash), "Locked destination retains original bytes");
    }
    private static void arguments() throws Exception {
        check(PrismLaunch.identifiesPrism("PrismLauncher 11.1.0") && PrismLaunch.identifiesPrism("Prism Launcher 11.1.0"), "Verified real Prism version output accepted with either product spelling");
        check(!PrismLaunch.identifiesPrism("Not Prism Launcher") && !PrismLaunch.identifiesPrism("Other Launcher 11"), "Unidentified executable output rejected");
        String request = UUID.randomUUID().toString();
        var command = ProductionBuild.command(fixtures, "neoforge", request);
        check(command.get(4).equals("org.gradle.wrapper.GradleWrapperMain") && command.contains(fixtures.resolve("gradle/wrapper/gradle-wrapper.jar").toString()), "Wrapper invoked without a shell, with exact path argument");
        check(command.stream().noneMatch(s -> s.equals("clean") || s.equals("build") || s.equals("check")), "Routine production command excludes clean/build/check");
        List<String> tricky = List.of("spaces & (parens) ! %PATH% ü", "C:\\trailing slash\\", "$(should-not-execute)");
        List<String> echo = new ArrayList<>(List.of(command.getFirst(), "-cp", System.getProperty("java.class.path"), PackTesterTest.class.getName(), "echo-args")); echo.addAll(tricky);
        Process p = new ProcessBuilder(Commands.checked(echo)).start(); List<String> actual = p.inputReader().lines().map(s -> new String(Base64.getDecoder().decode(s), java.nio.charset.StandardCharsets.UTF_8)).toList();
        check(p.waitFor() == 0 && actual.equals(tricky), "Actual Windows ProcessBuilder argument round trip with spaces and metacharacters: expected=" + tricky + ", actual=" + actual);
        if (System.getProperty("os.name").startsWith("Windows")) fails(() -> Commands.checked(List.of("quote\"text")), "Illegal embedded Windows path quotes rejected before process creation");
        Path root = fixtures.resolve("launch root"); Files.createDirectories(root); Files.writeString(root.resolve("prismlauncher.cfg"), "InstanceDir=instances\nAdditionalInstanceDirs=extra\n");
        var target = instance(root.resolve("instances/actual ID & 10"), root, "neoforge", "1.21.1", "minecraft");
        Path exe = root.resolve("prismlauncher.exe"); Files.writeString(exe, "fixture");
        var args = new PrismLaunch().command(target, exe);
        check(args.equals(List.of(exe.toString(), "--dir", root.toString(), "--launch", "actual ID & 10")), "Prism uses verified application root and actual folder ID");
        instance(root.resolve("extra/actual ID & 10"), root, "neoforge", "1.21.1", "minecraft");
        fails(() -> new PrismLaunch().command(target, exe), "Duplicate IDs across configured directories block ambiguous launch");
    }
}
