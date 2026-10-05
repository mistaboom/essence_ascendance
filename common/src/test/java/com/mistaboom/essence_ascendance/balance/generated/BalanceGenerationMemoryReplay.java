package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mistaboom.essence_ascendance.balance.config.BalanceInputs;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancements;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/** Opt-in constrained-heap generation serialization replay; never installs or changes live inputs.
 * Arguments: config-directory output-directory [--retain-prior] [--write-candidate].
 * This verifies complete canonical equality with the real profile, without constructing
 * a whole evidence/economy JsonObject first. It does not simulate a loaded client heap. */
public final class BalanceGenerationMemoryReplay {
    private static final List<String> SECTIONS = List.of("metadata", "settings", "overrides", "evidence",
            "runtime", "economy", "skills", "validation");

    private BalanceGenerationMemoryReplay() { }

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> error.printStackTrace(
                new PrintStream(new FileOutputStream(FileDescriptor.err))));
        if (args.length < 2) throw new IllegalArgumentException(
                "Usage: config-directory output-directory [--retain-prior] [--write-candidate]");
        Path config = Path.of(args[0]).toRealPath();
        Path output = outputDirectory(config, Path.of(args[1]));
        var options = new HashSet<String>();
        for (int index = 2; index < args.length; index++) {
            if (!Set.of("--retain-prior", "--write-candidate").contains(args[index]) || !options.add(args[index]))
                throw new IllegalArgumentException("Unknown or repeated replay option " + args[index]);
        }
        boolean retainPrior = options.contains("--retain-prior"), writeCandidate = options.contains("--write-candidate");
        Path profile = BalanceProfileStore.profilePath(config.resolve("essence_ascendance"));
        Map<Path, String> protectedHashes = new LinkedHashMap<>();
        for (Path path : List.of(profile, BalanceInputs.settingsPath(config), BalanceInputs.overridesPath(config)))
            protectedHashes.put(path, hash(path));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startedAt", Instant.now().toString());
        result.put("java", System.getProperty("java.runtime.version"));
        result.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        result.put("documentCodeSource", BalanceDocument.class.getProtectionDomain().getCodeSource().getLocation().toString());
        result.put("sourceProfile", profile.toString());
        result.put("sourceProfileSha256", protectedHashes.get(profile));
        result.put("retainPrior", retainPrior); result.put("writeCandidate", writeCandidate);
        result.put("boundary", "Isolated typed streaming, canonical sealing and candidate validation. No client/world, evidence recollection, runtime generation, installation or report export; sampled heap values are not peak memory.");
        Map<String, Object> before = new LinkedHashMap<>();
        protectedHashes.forEach((path, digest) -> before.put(path.toString(), digest));
        result.put("protectedHashesBefore", before);
        Map<String, Long> timings = new LinkedHashMap<>(); result.put("timingsNanos", timings);
        Map<String, Object> heap = new LinkedHashMap<>(); result.put("observedHeapSamples", heap);
        long started = System.nanoTime();
        Throwable failure = null;
        try {
            SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
            EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
            MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
            com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
            run(profile, output, retainPrior, writeCandidate, result, timings, heap);
            result.put("outcome", "PASS");
        } catch (Throwable error) {
            failure = error;
            result.put("outcome", "FAIL");
            result.put("failureClass", error.getClass().getName());
            result.put("failureMessage", String.valueOf(error.getMessage()));
        } finally {
            Map<String, Object> after = new LinkedHashMap<>();
            List<String> mismatches = new ArrayList<>();
            for (var entry : protectedHashes.entrySet()) {
                try {
                    String current = hash(entry.getKey()); after.put(entry.getKey().toString(), current);
                    if (!entry.getValue().equals(current)) mismatches.add(entry.getKey().toString());
                } catch (Throwable verificationFailure) {
                    mismatches.add(entry.getKey() + " (hash failed: " + verificationFailure + ")");
                    if (failure == null) failure = verificationFailure; else failure.addSuppressed(verificationFailure);
                }
            }
            result.put("protectedHashesAfter", after);
            result.put("protectedInputsUnchanged", mismatches.isEmpty());
            if (!mismatches.isEmpty()) {
                var changed = new AssertionError("Protected replay inputs changed or could not be verified: " + mismatches);
                if (failure == null) failure = changed; else failure.addSuppressed(changed);
                result.put("outcome", "FAIL"); result.put("protectedInputFailures", mismatches);
            }
            result.put("totalElapsedNanos", System.nanoTime() - started);
            result.put("finishedAt", Instant.now().toString());
            try (var writer = Files.newBufferedWriter(output.resolve("generation-memory-replay.json"))) {
                BalanceDocument.GSON.toJson(result, writer);
            } catch (Throwable outputFailure) {
                if (failure == null) failure = outputFailure; else failure.addSuppressed(outputFailure);
            }
        }
        if (failure instanceof Exception exception) throw exception;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new AssertionError(failure);
        new PrintStream(new FileOutputStream(FileDescriptor.out)).println("BalanceGenerationMemoryReplay PASS " + output);
    }

    private static void run(Path profile, Path output, boolean retainPrior, boolean writeCandidate,
                            Map<String, Object> result, Map<String, Long> timings, Map<String, Object> heap) throws Exception {
        GeneratedBalanceService.Active prior = null;
        if (retainPrior) {
            var priorSealed = sealProfile(profile, "prior", timings, heap);
            long started = System.nanoTime();
            prior = GeneratedBalanceService.decode(priorSealed.document());
            timings.put("priorTypedValidation", System.nanoTime() - started);
            heap.put("priorActiveRetained", heapSample());
        }
        var sealed = sealProfile(profile, "candidate", timings, heap);
        result.put("sourceIntegrity", sealed.sourceIntegrity());
        result.put("sourceEvidenceDigest", sealed.sourceEvidenceDigest());
        result.put("sourceStoredBytes", Files.size(profile));
        // sealProfile's frame and original typed records have returned. Only its
        // compressed document and small identity strings cross this boundary.
        long started = System.nanoTime();
        GeneratedBalanceService.Active candidate;
        try (var operation = BalancePerformance.begin("offline_generation_memory_replay", "streamed_real_profile")) {
            candidate = GeneratedBalanceService.decode(sealed.document());
            operation.complete("validated_offline");
        }
        timings.put("candidateTypedValidation", System.nanoTime() - started);
        result.put("validationOperation", BalancePerformance.lastSnapshot());
        heap.put("candidateActiveValidated", heapSample());
        if (!candidate.document().integrity().equals(sealed.sourceIntegrity()))
            throw new AssertionError("Candidate validation changed the full profile integrity");
        Map<String, String> semantic = new TreeMap<>();
        for (String section : SECTIONS) semantic.put(section, candidate.document().sectionHash(section));
        result.put("candidateSectionHashes", semantic);
        result.put("resources", candidate.economy().resources().size());
        result.put("evidenceFacts", candidate.evidence().facts().size());
        result.put("equipmentReferences", candidate.evidence().equipment().size());
        result.put("priorActiveKeptReachable", prior != null);
        if (writeCandidate) {
            Path saved = output.resolve("candidate-profile.json.gz");
            if (Files.exists(saved)) throw new IllegalArgumentException("Replay candidate output already exists: " + saved);
            started = System.nanoTime();
            BalanceProfileStore.replace(saved, candidate.document());
            timings.put("candidateFileWrite", System.nanoTime() - started);
            result.put("candidateFile", saved.toString());
            result.put("candidateFileSha256", hash(saved));
            result.put("candidateFileStoredBytes", Files.size(saved));
            // The optional prior remains reachable. Release the candidate typed
            // round trip before reading the output into a new typed graph.
            candidate = null; sealed = null;
            var reopened = sealProfile(saved, "reopened", timings, heap);
            if (!reopened.sourceIntegrity().equals(result.get("sourceIntegrity")))
                throw new AssertionError("Committed replay copy differs from original profile integrity");
            if (!reopened.sourceEvidenceDigest().equals(result.get("sourceEvidenceDigest")))
                throw new AssertionError("Committed replay copy differs from original evidence digest");
            started = System.nanoTime();
            candidate = GeneratedBalanceService.decode(reopened.document());
            timings.put("reopenedTypedValidation", System.nanoTime() - started);
            for (String section : SECTIONS)
                if (!semantic.get(section).equals(candidate.document().sectionHash(section)))
                    throw new AssertionError("Committed replay copy differs in section " + section);
            result.put("candidateFileRoundTrip", "PASS");
            heap.put("reopenedActiveValidated", heapSample());
        }
        if (prior != null && !prior.document().integrity().equals(result.get("sourceIntegrity")))
            throw new AssertionError("Retained prior Active changed");
        Reference.reachabilityFence(prior);
        Reference.reachabilityFence(candidate);
    }

    private record Sealed(BalanceDocument document, String sourceIntegrity, String sourceEvidenceDigest) { }

    private static Sealed sealProfile(Path profile, String label, Map<String, Long> timings,
                                      Map<String, Object> heap) throws Exception {
        long started = System.nanoTime();
        Source source = readTyped(profile);
        timings.put(label + "StreamingTypedRead", System.nanoTime() - started);
        heap.put(label + "OriginalTypedRead", heapSample());
        String expectedIntegrity = source.small().get("integrity").getAsString();
        String expectedEvidence = source.small().getAsJsonObject("metadata").get("evidenceDigest").getAsString();
        started = System.nanoTime();
        BalanceDocument document = BalanceDocument.sealGeneratedOwned(source.small(), source.evidence(), source.economy());
        timings.put(label + "StreamingSeal", System.nanoTime() - started);
        heap.put(label + "StreamedSeal", heapSample());
        if (!document.integrity().equals(expectedIntegrity))
            throw new AssertionError("Streaming complete envelope differs from original: " + expectedIntegrity + " != " + document.integrity());
        if (!document.sectionHash("evidence").equals(expectedEvidence))
            throw new AssertionError("Streaming evidence digest differs from original: " + expectedEvidence + " != " + document.sectionHash("evidence"));
        return new Sealed(document, expectedIntegrity, expectedEvidence);
    }

    private record Source(JsonObject small, PackEvidence evidence, EconomyProfile economy) { }

    private static Source readTyped(Path profile) throws IOException {
        if (Files.size(profile) > BalanceProfileStore.MAX_PROFILE_BYTES)
            throw new IOException("Replay source exceeds profile stored-byte limit");
        try (var stored = BalanceProfileStore.limitedInput(Files.newInputStream(profile), BalanceProfileStore.MAX_PROFILE_BYTES);
             var inflated = BalanceProfileStore.limitedInput(new GZIPInputStream(stored), BalanceProfileStore.MAX_JSON_BYTES);
             var input = new PooledReader(new InputStreamReader(inflated, StandardCharsets.UTF_8.newDecoder()))) {
            var small = new JsonObject(); var seen = new HashSet<String>();
            PackEvidence evidence = null; EconomyProfile economy = null;
            input.beginObject();
            while (input.hasNext()) {
                String key = input.nextName();
                if (!seen.add(key)) throw new IllegalArgumentException("Duplicate replay profile section " + key);
                if (key.equals("evidence")) evidence = BalanceDocument.GSON.fromJson(input, PackEvidence.class);
                else if (key.equals("economy")) economy = BalanceDocument.GSON.fromJson(input, EconomyProfile.class);
                else {
                    if (!SECTIONS.contains(key) && !Set.of("schema", "generator", "integrity").contains(key))
                        throw new IllegalArgumentException("Unknown replay profile section " + key);
                    small.add(key, JsonParser.parseReader(input));
                }
            }
            input.endObject();
            if (input.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing replay profile data");
            if (evidence == null || economy == null || !seen.containsAll(SECTIONS)
                    || !small.has("integrity") || !small.has("schema") || !small.has("generator"))
                throw new IllegalArgumentException("Incomplete replay profile");
            if (small.get("schema").getAsInt() != BalanceDocument.SCHEMA
                    || !small.get("generator").getAsString().equals(BalanceDocument.GENERATOR))
                throw new IllegalArgumentException("Incompatible replay profile envelope");
            return new Source(small, evidence, economy);
        }
    }

    /** Same bounded string reuse as the production reader, without a whole-section tree. */
    private static final class PooledReader extends JsonReader {
        private static final int MAX_CHARACTERS = 8 * 1024 * 1024, MAX_ENTRIES = 65_536;
        private final LinkedHashMap<String, String> strings = new LinkedHashMap<>(256, .75f, true);
        private int characters;
        PooledReader(Reader input) { super(input); }
        @Override public String nextName() throws IOException { return pooled(super.nextName()); }
        @Override public String nextString() throws IOException { return pooled(super.nextString()); }
        private String pooled(String value) {
            String existing = strings.get(value); if (existing != null) return existing;
            if (value.length() > MAX_CHARACTERS) return value;
            while (!strings.isEmpty() && (strings.size() >= MAX_ENTRIES || characters + value.length() > MAX_CHARACTERS)) {
                var entries = strings.entrySet().iterator(); characters -= entries.next().getKey().length(); entries.remove();
            }
            strings.put(value, value); characters += value.length(); return value;
        }
    }

    private static Map<String, Long> heapSample() {
        Runtime runtime = Runtime.getRuntime();
        return Map.of("usedBytes", runtime.totalMemory() - runtime.freeMemory(), "committedBytes", runtime.totalMemory(),
                "maxBytes", runtime.maxMemory());
    }

    private static Path outputDirectory(Path config, Path requested) throws IOException {
        Path output = requested.toAbsolutePath().normalize(), ancestor = output;
        while (!Files.exists(ancestor)) {
            ancestor = ancestor.getParent();
            if (ancestor == null) throw new IllegalArgumentException("Replay output has no existing parent");
        }
        Path resolved = ancestor.toRealPath().resolve(ancestor.relativize(output)).normalize();
        if (resolved.startsWith(config)) throw new IllegalArgumentException("Replay output must be outside live config");
        Files.createDirectories(resolved);
        resolved = resolved.toRealPath();
        if (resolved.startsWith(config)) throw new IllegalArgumentException("Replay output resolved inside live config");
        return resolved;
    }

    private static String hash(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(path), digest)) { input.transferTo(OutputStream.nullOutputStream()); }
        return HexFormat.of().formatHex(digest.digest());
    }
}
