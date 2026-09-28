package dev.essence.packtester;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

final class Deployment {
    static final String STATE = ".essence-ascendance-pack-tester";
    record Previous(String name, String hash) { }
    record Journal(String transaction, String modsDirectory, String destination, String hash, List<Previous> previous, String status) { }
    record Result(Path destination, String hash, Path backup) { }
    interface Fault { void at(String point) throws IOException; }
    private final Fault fault;
    Deployment() { this(point -> { }); }
    Deployment(Fault fault) { this.fault = fault; }
    static final class Lease implements AutoCloseable {
        final Path state; private final FileChannel channel; private final FileLock lock;
        Lease(Path state, FileChannel channel, FileLock lock) { this.state = state; this.channel = channel; this.lock = lock; }
        public void close() throws IOException { try { lock.close(); } finally { channel.close(); } }
    }
    Lease acquire(PrismDiscovery.Instance target) throws IOException {
        FilesEx.plainDirectory(target.directory()); FilesEx.plainDirectory(target.game());
        Path state = target.directory().resolve(STATE);
        Files.createDirectories(state); FilesEx.plainDirectory(state);
        Path lockPath = FilesEx.child(state, "deployment.lock");
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("Another Pack Tester owns this target: " + target.directory());
            return new Lease(state, channel, lock);
        } catch (IOException | RuntimeException e) { channel.close(); throw new IOException("Target deployment is locked: " + target.directory(), e); }
    }
    void requireRecovered(Lease lease) throws IOException {
        if (Files.exists(lease.state.resolve("pending.json"))) throw new IOException("An interrupted deployment needs recovery. Use Recover in the GUI or the recover command after closing the game.");
    }
    Result install(Lease lease, PrismDiscovery.Instance target, ProductionBuild.Artifact artifact, Cancellation cancel, Consumer<String> log) throws Exception {
        requireRecovered(lease); cancel.check();
        if (!target.loader().equals(artifact.loader()) || !target.minecraft().equals(artifact.minecraft())) throw new IOException("Artifact does not match frozen target");
        FilesEx.plainDirectory(target.game());
        if (!Files.exists(target.mods())) Files.createDirectory(target.mods());
        Path mods = FilesEx.plainDirectory(target.mods());
        String tx = UUID.randomUUID().toString();
        Path backup = lease.state.resolve(tx); Files.createDirectory(backup);
        Path stage = backup.resolve("replacement.jar.disabled");
        Files.copy(artifact.path(), stage); FilesEx.force(stage);
        ModMetadata.production(stage, target.loader());
        if (!FilesEx.hash(stage).equals(artifact.hash())) throw new IOException("Staged archive hash mismatch; installed mod was not changed");
        List<Previous> old = new ArrayList<>();
        try (var files = Files.list(mods)) {
            for (Path jar : files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                FilesEx.child(mods, jar.getFileName().toString());
                var meta = ModMetadata.read(jar, target.loader());
                if (meta.nestedOwn() || meta.containsOwn() && !meta.onlyOwn()) throw new IOException("Ambiguous own-mod bundle must be resolved manually: " + jar);
                if (meta.onlyOwn()) old.add(new Previous(jar.getFileName().toString(), FilesEx.hash(jar)));
            }
        }
        String name = "essence_ascendance-" + target.loader() + ".jar";
        Path destination = FilesEx.child(mods, name);
        if (Files.exists(destination) && old.stream().noneMatch(p -> p.name().equals(name))) throw new IOException("Destination is occupied by an unrelated/unidentified file: " + destination);
        var journal = new Journal(tx, mods.toString(), name, artifact.hash(), old, "PREPARED");
        Path pending = FilesEx.child(lease.state, "pending.json");
        cancel.check(); FilesEx.json(pending, journal);
        try {
            for (Previous previous : old) {
                cancel.check();
                Path source = FilesEx.child(mods, previous.name());
                if (!FilesEx.hash(source).equals(previous.hash())) throw new IOException("Installed mod changed while preparing replacement");
                Files.move(source, backup.resolve(previous.name()), StandardCopyOption.ATOMIC_MOVE);
                fault.at("after-backup");
            }
            cancel.check(); fault.at("before-install");
            // No REPLACE_EXISTING: never overwrite a new file created by another program.
            if (Files.exists(destination)) throw new IOException("Destination appeared during replacement: " + destination);
            Files.move(stage, destination, StandardCopyOption.ATOMIC_MOVE);
            fault.at("after-install");
            if (!FilesEx.hash(destination).equals(artifact.hash())) throw new IOException("Deployed hash mismatch");
            cancel.check();
            FilesEx.json(pending, new Journal(tx, mods.toString(), name, artifact.hash(), old, "COMMITTED"));
        } catch (Exception e) {
            try { rollback(lease, target, journal, log); }
            catch (Exception recovery) { e.addSuppressed(recovery); throw new IOException("Replacement failed; recovery is incomplete. Keep Minecraft closed. Journal: " + pending, e); }
            throw e;
        }
        // A crash here is handled as a committed transaction, never mistaken for a failed copy.
        fault.at("after-commit");
        finish(lease, new Journal(tx, mods.toString(), name, artifact.hash(), old, "COMMITTED"), log);
        log.accept("Deployed SHA-256 " + artifact.hash() + "\nDestination: " + destination + "\nPrevious copies: " + backup);
        return new Result(destination, artifact.hash(), backup);
    }
    void recover(Lease lease, PrismDiscovery.Instance target, Consumer<String> log) throws IOException {
        Path pending = FilesEx.child(lease.state, "pending.json");
        if (!Files.exists(pending)) { log.accept("No interrupted deployment to recover."); return; }
        Journal j;
        try { j = FilesEx.JSON.fromJson(Files.readString(pending), Journal.class); }
        catch (RuntimeException e) { throw new IOException("Unreadable recovery journal; preserve it for manual review", e); }
        validateJournal(j);
        if (!target.mods().toString().equals(j.modsDirectory())) throw new IOException("Instance game directory changed since this transaction; preserve the journal and restore the original layout before recovery.");
        if (j.status().equals("COMMITTED")) {
            Path deployed = FilesEx.child(FilesEx.plainDirectory(target.mods()), j.destination());
            if (!Files.exists(deployed) || !FilesEx.hash(deployed).equals(j.hash())) throw new IOException("Committed artifact was changed; recovery needs manual review");
            finish(lease, j, log);
        } else rollback(lease, target, j, log);
    }
    private void validateJournal(Journal j) throws IOException {
        if (j == null || j.transaction() == null || !j.transaction().matches("[0-9a-f-]{36}") || j.hash() == null || !j.hash().matches("[0-9a-f]{64}")
                || j.modsDirectory() == null || j.previous() == null || !Set.of("PREPARED", "COMMITTED").contains(j.status()) || j.destination() == null
                || !j.destination().matches("essence_ascendance-(fabric|neoforge)\\.jar")) throw new IOException("Invalid recovery journal");
        Set<String> names = new HashSet<>();
        for (Previous p : j.previous()) if (p.name() == null || p.hash() == null || !p.hash().matches("[0-9a-f]{64}") || !names.add(p.name())) throw new IOException("Invalid previous-file journal");
    }
    private void rollback(Lease lease, PrismDiscovery.Instance target, Journal j, Consumer<String> log) throws IOException {
        validateJournal(j);
        Path mods = FilesEx.plainDirectory(target.mods());
        if (!mods.toString().equals(j.modsDirectory())) throw new IOException("Recovery destination differs from the recorded game directory");
        Path backup = FilesEx.plainDirectory(lease.state.resolve(j.transaction()));
        Path destination = FilesEx.child(mods, j.destination());
        // Validate all recoverable paths/hashes before making any recovery change.
        for (Previous p : j.previous()) {
            Path saved = FilesEx.child(backup, p.name()), original = FilesEx.child(mods, p.name());
            if (Files.exists(saved) && !FilesEx.hash(saved).equals(p.hash())) throw new IOException("Backup changed: " + saved);
            if (Files.exists(original) && !FilesEx.hash(original).equals(p.hash()) && !(original.equals(destination) && FilesEx.hash(original).equals(j.hash())))
                throw new IOException("Recovery would overwrite a changed file: " + original);
            if (!Files.exists(saved) && (!Files.exists(original) || !FilesEx.hash(original).equals(p.hash()))) throw new IOException("Missing recoverable original: " + p.name());
        }
        if (Files.exists(destination)) {
            String hash = FilesEx.hash(destination);
            boolean isOriginal = j.previous().stream().anyMatch(p -> p.name().equals(j.destination()) && p.hash().equals(hash));
            if (!hash.equals(j.hash()) && !isOriginal) throw new IOException("Recovery destination changed; refusing deletion");
            // If old and new are identical, an absent backup means the original never moved.
            boolean backedUp = j.previous().stream().anyMatch(p -> p.name().equals(j.destination()) && Files.exists(backup.resolve(p.name())));
            if (hash.equals(j.hash()) && (!isOriginal || backedUp)) Files.delete(destination);
        }
        for (Previous p : j.previous()) {
            Path saved = FilesEx.child(backup, p.name()), original = FilesEx.child(mods, p.name());
            if (Files.exists(saved) && !Files.exists(original)) Files.move(saved, original, StandardCopyOption.ATOMIC_MOVE);
        }
        FilesEx.json(backup.resolve("rollback.json"), j);
        Files.delete(lease.state.resolve("pending.json"));
        log.accept("Rolled back interrupted replacement; previous copies restored.");
    }
    private void finish(Lease lease, Journal j, Consumer<String> log) throws IOException {
        Path backup = FilesEx.plainDirectory(lease.state.resolve(j.transaction()));
        FilesEx.json(backup.resolve("deployment.json"), j);
        Files.deleteIfExists(lease.state.resolve("pending.json"));
        log.accept("Transaction committed at " + Instant.now());
    }
}
