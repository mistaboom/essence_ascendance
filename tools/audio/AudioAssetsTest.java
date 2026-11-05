import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Offline build acceptance. Fixtures simulate review only inside a target build tree. */
public final class AudioAssetsTest {
    private static int assertions;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path root, fixture;
    public static void main(String[] args) throws Exception {
        root = Path.of(args[0]).toAbsolutePath(); fixture = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(root);
        Fixture f = new Fixture("approved");
        f.generate();
        Path sounds = f.output.resolve("assets/essence_ascendance/sounds.json");
        String original = Files.readString(sounds);
        check(original.contains("process.complete") && original.contains("subtitles.essence_ascendance.process.complete"), "Derived native catalog");
        byte[] ogg = Files.readAllBytes(f.output.resolve("assets/essence_ascendance/sounds/process/complete_a.ogg"));
        check(Arrays.equals(ogg, Files.readAllBytes(f.art.resolve("approved.ogg"))), "Approved bytes copied unchanged");
        long stamp = Files.getLastModifiedTime(sounds).toMillis();
        f.generate();
        check(Files.getLastModifiedTime(sounds).toMillis() == stamp, "Unchanged output reused");
        String runtime = Files.readString(f.output.resolve("assets/essence_ascendance/audio/catalog.json"));
        check(!runtime.contains("master.wav") && !runtime.contains("recipe.json") && !runtime.contains("AUTOMATED"), "No authoring provenance in runtime descriptor");
        reject("changed master", x -> writeBytes(x.art.resolve("master.wav"), new byte[]{0}));
        reject("changed approved", x -> writeBytes(x.art.resolve("approved.ogg"), new byte[]{0}));
        reject("changed recipe snapshot", x -> writeBytes(x.art.resolve("snapshot.json"), new byte[]{0}));
        reject("mismatched review", x -> x.receipt.getAsJsonObject("review").addProperty("status", "rejected"));
        reject("unknown asset", x -> x.sample().addProperty("asset", "missing"));
        reject("unknown policy", x -> x.cue().addProperty("playback", "missing"));
        reject("unknown semantic recipe", x -> x.catalog.getAsJsonObject("recipes").getAsJsonObject("process.complete").addProperty("body", "missing"));
        reject("unknown recipe predicate", x -> x.catalog.getAsJsonObject("recipes").getAsJsonObject("process.complete").addProperty("script", "anything"));
        reject("duplicate bare and namespaced semantic", x -> x.catalog.getAsJsonObject("recipes").add(
                "essence_ascendance:process.complete", x.catalog.getAsJsonObject("recipes").get("process.complete").deepCopy()));
        reject("contradictory recipe predicate", x -> {
            var recipe=x.catalog.getAsJsonObject("recipes").getAsJsonObject("process.complete");
            recipe.addProperty("minimumA",1); recipe.addProperty("maximumA",0);
        });
        reject("missing translation", x -> x.cue().addProperty("subtitleKey", "missing"));
        reject("blank translation", x -> x.language.addProperty("subtitles.essence_ascendance.process.complete", "  "));
        reject("non-text translation", x -> x.language.addProperty("subtitles.essence_ascendance.process.complete", 7));
        reject("approved orphan asset", x -> { x.sample().remove("asset"); x.sample().addProperty("event", "minecraft:ui.button.click"); });
        reject("final review without listening", x -> {
            x.receipt.getAsJsonObject("review").addProperty("listened", false);
            x.record.add("review", x.receipt.get("review").deepCopy());
        });
        reject("one-shot used as continuous body", x -> {
            x.catalog.getAsJsonObject("policies").getAsJsonObject("lifecycle").add("sustain", object("{\"attackTicks\":4,\"releaseTicks\":6,\"resume\":\"body_only\",\"restartBackoffTicks\":20}"));
            x.catalog.getAsJsonObject("sourceProfiles").add("process", object("{\"recipe\":\"process.complete\",\"lifecycle\":\"sustain\"}"));
        });
        Fixture contextual = new Fixture("contextual"); contextual.addAlternative(); contextual.generate();
        check(Files.readString(contextual.output.resolve("assets/essence_ascendance/audio/catalog.json")).contains("alternatives"), "Bounded contextual recipes remain shared runtime data");
        reject("overlapping contextual choices", x -> {
            x.addAlternative(); x.alternative().addProperty("minimumA", 0.5);
        });
        reject("unknown contextual cue", x -> { x.addAlternative(); x.alternative().addProperty("body", "missing"); });
        reject("nested contextual choices", x -> { x.addAlternative(); x.alternative().add("alternatives", new JsonArray()); });
        reject("cross-audience contextual choices", x -> {
            x.addAlternative(); x.catalog.getAsJsonObject("policies").getAsJsonObject("recipients").add("private", object("{\"scope\":\"player\"}"));
            x.catalog.getAsJsonObject("cues").getAsJsonObject("essence_ascendance:process.other").addProperty("recipients", "private");
        });
        reject("contextual continuous profile", x -> {
            x.addAlternative();
            x.catalog.getAsJsonObject("policies").getAsJsonObject("lifecycle").add("sustain", object("{\"attackTicks\":4,\"releaseTicks\":6,\"resume\":\"body_only\",\"restartBackoffTicks\":20}"));
            x.catalog.getAsJsonObject("sourceProfiles").add("process", object("{\"recipe\":\"process.complete\",\"lifecycle\":\"sustain\"}"));
        });
        Fixture integration = new Fixture("integration"); integration.authorizeIntegration(); integration.generate();
        check(Files.exists(integration.output.resolve("assets/essence_ascendance/sounds/process/complete_a.ogg")), "Explicit exact-selection authorization admits integration bytes without fabricating final listening");
        check(AudioAssets.string(integration.record.getAsJsonObject("review"), "status").equals("pending"), "Integration keeps final review pending");
        reject("unapproved creative integration", x -> {
            x.authorizeIntegration(); x.record.getAsJsonObject("creativeReview").addProperty("status", "rejected");
            x.receipt.add("creativeReview", x.record.get("creativeReview").deepCopy());
        });
        reject("integration authorization disagreement", x -> {
            x.authorizeIntegration(); x.receipt.getAsJsonObject("integrationPromotion").addProperty("authority", "different");
        });
        reject("changed integration selection", x -> {
            x.authorizeIntegration(); writeBytes(x.art.resolve("selection.json"), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        reject("demonstration integration", x -> { x.authorizeIntegration(); x.writeSelection("demonstration", false); });
        reject("duplicate integration selection", x -> { x.authorizeIntegration(); x.writeSelection("runtime", true); });
        reject("missing integration source links", x -> { x.authorizeIntegration(); x.receipt.remove("sourceLinks"); });
        reject("changed integration project link", x -> { x.authorizeIntegration(); x.receipt.getAsJsonObject("sourceLinks").addProperty("project", "projects/other.aup3"); });
        reject("integration cannot claim final review", x -> {
            x.authorizeIntegration(); x.record.getAsJsonObject("review").addProperty("status", "accepted");
            x.receipt.add("review", x.record.get("review").deepCopy());
        });
        reject("out of tree source", x -> x.asset().addProperty("master", "../outside.wav"));
        reject("absolute source", x -> x.asset().addProperty("master", x.art.resolve("master.wav").toString()));
        reject("bad level evidence", x -> {
            x.receipt.getAsJsonObject("measurements").addProperty("truePeakDb", 0);
            x.record.add("measurements", x.receipt.get("measurements").deepCopy());
        });
        reject("bad channel evidence", x -> {
            x.receipt.getAsJsonObject("measurements").addProperty("channels", 2);
            x.record.add("measurements", x.receipt.get("measurements").deepCopy());
        });
        reject("missing closed project", x -> x.record.getAsJsonObject("backup").addProperty("closed", false));
        reject("duplicate resource", x -> {
            x.catalog.getAsJsonObject("assets").add("second", x.asset().deepCopy());
            x.manifest.getAsJsonObject("assets").add("second", x.manifest.getAsJsonObject("assets").get("process_complete_a").deepCopy());
        });
        // Hashes alone cannot admit a renamed WAV or corrupt Ogg: framing still validates.
        reject("renamed WAV with matching receipt", x -> {
            try {
                Files.copy(x.art.resolve("master.wav"), x.art.resolve("approved.ogg"), StandardCopyOption.REPLACE_EXISTING);
                String hash = AudioAssets.sha(x.art.resolve("approved.ogg"));
                x.receipt.addProperty("approvedSha256", hash); x.record.addProperty("exportSha256", hash);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        reject("bad Ogg CRC with matching receipt", x -> {
            try {
                byte[] data = Files.readAllBytes(x.art.resolve("approved.ogg")); data[data.length - 1] ^= 1;
                Files.write(x.art.resolve("approved.ogg"), data);
                String hash = AudioAssets.sha(x.art.resolve("approved.ogg"));
                x.receipt.addProperty("approvedSha256", hash); x.record.addProperty("exportSha256", hash);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        Fixture pending = new Fixture("pending");
        pending.asset().addProperty("status", "authoring_only"); pending.generate();
        check(AudioAssets.read(pending.output.resolve("assets/essence_ascendance/sounds.json")).size() == 0, "Pending takes excluded");
        check(!Files.exists(pending.output.resolve("assets/essence_ascendance/sounds/process/complete_a.ogg")), "Pending bytes excluded");
        Fixture fallback = new Fixture("fallback");
        fallback.asset().addProperty("status", "authoring_only");
        fallback.cue().addProperty("fallback", "minecraft:block.beacon.ambient"); fallback.generate();
        String fallbackSound = Files.readString(fallback.output.resolve("assets/essence_ascendance/sounds.json"));
        check(fallbackSound.contains("block.beacon.ambient") && fallbackSound.contains("event"), "Pending asset uses explicitly declared native event fallback");
        check(!Files.exists(fallback.output.resolve("assets/essence_ascendance/sounds/process/complete_a.ogg")), "Fallback never promotes pending bytes");
        Fixture nativeOnly = new Fixture("native_only"); nativeOnly.asset().addProperty("status", "authoring_only");
        var nativeSample = new JsonObject(); nativeSample.addProperty("event", "minecraft:ui.button.click"); nativeSample.addProperty("weight", 2);
        var nativeSamples = new JsonArray(); nativeSamples.add(nativeSample); nativeOnly.cue().getAsJsonObject("variants").add("samples", nativeSamples); nativeOnly.generate();
        check(Files.readString(nativeOnly.output.resolve("assets/essence_ascendance/audio/catalog.json")).contains("ui.button.click"), "Native weighted events share runtime descriptor generation");
        reject("invalid native fallback", x -> x.cue().addProperty("fallback", "minecraft:../unsafe"));
        reject("ambiguous asset and native event", x -> x.sample().addProperty("event", "minecraft:ui.button.click"));
        reject("zero variant weight", x -> x.sample().addProperty("weight", 0));
        Path duplicates = root.resolve("duplicate.json"); Files.writeString(duplicates, "{\"x\":1,\"x\":2}");
        try { AudioAssets.read(duplicates); throw new AssertionError("Duplicate JSON accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        System.out.println("AudioAssetsTest: " + assertions + " audio build assertions PASS");
    }
    private static void reject(String name, Consumer<Fixture> edit) throws Exception {
        Fixture f = new Fixture(name.replace(' ', '_'));
        f.generate();
        Path nativeSounds = f.output.resolve("assets/essence_ascendance/sounds.json");
        byte[] before = Files.readAllBytes(nativeSounds);
        edit.accept(f); f.save();
        boolean rejected = false;
        try { f.run(); } catch (Exception expected) { rejected = true; }
        check(rejected, "Reject " + name);
        check(Arrays.equals(before, Files.readAllBytes(nativeSounds)), "Failed audio import leaves prior resources intact: " + name);
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message); assertions++;
    }
    private static void writeBytes(Path path, byte[] bytes) {
        try { Files.write(path, bytes); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void json(Path path, JsonObject value) throws Exception { Files.writeString(path, JSON.toJson(value)); }
    private static JsonObject object(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static final class Fixture {
        final Path dir, art, output, authored;
        final JsonObject catalog, manifest, receipt, record, language;
        Fixture(String name) throws Exception {
            dir = root.resolve(name); art = dir.resolve("art"); output = dir.resolve("build/generated"); authored = dir.resolve("authored");
            Files.createDirectories(art.resolve("source/models"));
            for (String[] pair : new String[][]{{"master.wav","master.wav"},{"sample.ogg","approved.ogg"},{"recipe.json","recipe.json"},{"recipe.json","snapshot.json"}})
                Files.copy(fixture.resolve(pair[0]), art.resolve(pair[1]), StandardCopyOption.REPLACE_EXISTING);
            receipt = AudioAssets.read(fixture.resolve("receipt.json"));
            catalog = object("""
                {"schema":1,"assets":{"process_complete_a":{"status":"approved","resource":"essence_ascendance:process/complete_a",
                "revision":"r0001","master":"master.wav","approved":"approved.ogg","recipe":"snapshot.json","receipt":"receipt.json"}},
                "policies":{"playback":{"process":{"category":"BLOCKS","gain":0.4,"pitch":1,"attenuation":"linear","priority":20}},
                "recipients":{"near":{"scope":"world","radius":16}},
                "admission":{"process":{"group":"process","perSource":1,"perGroup":8,"repeatTicks":10,"overflow":"prefer_near"}},"lifecycle":{}},
                "cues":{"essence_ascendance:process.complete":{"playback":"process","recipients":"near","admission":"process",
                "subtitleKey":"subtitles.essence_ascendance.process.complete","variants":{"selection":"native_weighted","samples":[{"asset":"process_complete_a","weight":1}]}}},
                "recipes":{"process.complete":{"body":"essence_ascendance:process.complete"}},"sourceProfiles":{}}
                """);
            manifest = object("""
                {"schema":1,"assets":{"process_complete_a":{"family":"process","scope":"world","recipe":"recipe.json","revisions":[]}}}
                """);
            record = receipt.deepCopy(); record.addProperty("id", "r0001");
            record.addProperty("exportSha256", AudioAssets.string(receipt,"approvedSha256"));
            record.addProperty("project", "projects/test/process_complete_a.aup3");
            record.add("backup", object("{\"closed\":true}"));
            record.getAsJsonObject("backup").add("projectSha256", receipt.get("projectSha256"));
            record.getAsJsonObject("backup").add("backupSha256", receipt.get("backupSha256"));
            manifest.getAsJsonObject("assets").getAsJsonObject("process_complete_a").getAsJsonArray("revisions").add(record);
            JsonObject imports = object("{\"schemaVersion\":1,\"namespace\":\"essence_ascendance\",\"assets\":[{\"source\":\"audio-catalog.json\",\"category\":\"audio\"}]}");
            json(art.resolve("asset-manifest.json"), imports);
            Path lang = authored.resolve("assets/essence_ascendance/lang/en_us.json"); Files.createDirectories(lang.getParent());
            language = object("{\"subtitles.essence_ascendance.process.complete\":\"Test cue\"}"); json(lang, language);
            json(dir.resolve("profiles.json"), object("{}"));
        }
        JsonObject cue() { return catalog.getAsJsonObject("cues").getAsJsonObject("essence_ascendance:process.complete"); }
        JsonObject sample() { return cue().getAsJsonObject("variants").getAsJsonArray("samples").get(0).getAsJsonObject(); }
        JsonObject asset() { return catalog.getAsJsonObject("assets").getAsJsonObject("process_complete_a"); }
        void addAlternative() {
            catalog.getAsJsonObject("cues").add("essence_ascendance:process.other", cue().deepCopy());
            var recipe = catalog.getAsJsonObject("recipes").getAsJsonObject("process.complete");
            recipe.addProperty("maximumA", 0.5);
            JsonArray choices = new JsonArray(); choices.add(object("{\"body\":\"essence_ascendance:process.other\",\"minimumA\":0.500001}"));
            recipe.add("alternatives", choices);
        }
        JsonObject alternative() { return catalog.getAsJsonObject("recipes").getAsJsonObject("process.complete").getAsJsonArray("alternatives").get(0).getAsJsonObject(); }
        void authorizeIntegration() {
            record.add("review", object("{\"status\":\"pending\"}")); receipt.add("review", record.get("review").deepCopy());
            record.add("creativeReview", object("{\"status\":\"accepted\",\"reviewer\":\"AUTOMATED FIXTURE\"}")); receipt.add("creativeReview", record.get("creativeReview").deepCopy());
            record.addProperty("master", "masters/test/master.wav"); record.addProperty("export", "exports/test/sample.ogg");
            record.addProperty("recipeSnapshot", "masters/test/recipe.json"); record.getAsJsonObject("backup").addProperty("path", "backups/test/project.aup3");
            JsonObject links = object("{\"root\":\"auxiliary_audio\"}");
            for (String field : List.of("project", "master", "export")) links.add(field, record.get(field));
            links.add("recipe", record.get("recipeSnapshot")); links.add("backup", record.getAsJsonObject("backup").get("path"));
            receipt.add("sourceLinks", links);
            record.add("integrationPromotion", object("{\"status\":\"authorized\",\"authority\":\"AUTOMATED FIXTURE\",\"notes\":\"Test integration only\",\"selection\":\"selection.json\",\"purpose\":\"runtime\"}"));
            record.getAsJsonObject("integrationPromotion").add("sourceExportSha256", record.get("exportSha256"));
            writeSelection("runtime", false);
        }
        void writeSelection(String purpose, boolean duplicate) {
            try {
                JsonObject item = object("{\"asset\":\"process_complete_a\",\"revision\":\"r0001\"}");
                item.addProperty("purpose", purpose); item.add("sourceExportSha256", record.get("exportSha256"));
                JsonArray items = new JsonArray(); items.add(item); if (duplicate) items.add(item.deepCopy());
                JsonObject selection = object("{\"schema\":1}"); selection.add("items", items); json(art.resolve("selection.json"), selection);
                record.getAsJsonObject("integrationPromotion").addProperty("selectionSha256", AudioAssets.sha(art.resolve("selection.json")));
                receipt.add("integrationPromotion", record.get("integrationPromotion").deepCopy());
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        void save() throws Exception {
            json(art.resolve("audio-catalog.json"), catalog); json(art.resolve("audio-manifest.json"), manifest); json(art.resolve("receipt.json"), receipt);
            json(authored.resolve("assets/essence_ascendance/lang/en_us.json"), language);
        }
        void generate() throws Exception { save(); run(); }
        void run() throws Exception {
            AssetPipeline.main(new String[]{art.toString(),dir.resolve("build").toString(),output.toString(),dir.resolve("build/work").toString(),
                dir.resolve("profiles.json").toString(),"1.21",authored.toString()});
        }
    }
}
