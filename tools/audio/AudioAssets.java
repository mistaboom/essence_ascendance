import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiConsumer;

/** Build-only audio importer. No DAW, decoder, encoder, Python or auxiliary lookup.
 * Encoding/decode/level evidence is bound to exact approved bytes by SHA-256.
 */
public final class AudioAssets {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NS = "essence_ascendance";
    private static final String ROOT = "assets/" + NS + "/";

    public static void generate(Path art, Path catalogFile, Path authored, BiConsumer<String, byte[]> add) throws Exception {
        JsonObject catalog = read(catalogFile), manifest = read(art.resolve("audio-manifest.json"));
        keys(catalog, "schema", "assets", "policies", "cues", "recipes", "sourceProfiles");
        keys(manifest, "schema", "assets");
        require(catalog.get("schema").getAsInt() == 1 && manifest.get("schema").getAsInt() == 1, "Audio schema");
        JsonObject assets = catalog.getAsJsonObject("assets"), authors = manifest.getAsJsonObject("assets");
        require(assets.keySet().equals(authors.keySet()), "Catalog/authoring asset identities disagree");
        Set<String> resources = new HashSet<>(), approved = new HashSet<>();
        for (String id : assets.keySet()) {
            require(id.matches("[a-z0-9_]+"), "Invalid asset identity: " + id);
            JsonObject a = assets.getAsJsonObject(id), author = authors.getAsJsonObject(id);
            keys(a, "status", "resource", "revision", "master", "approved", "recipe", "receipt");
            keys(author, "family", "scope", "recipe", "revisions");
            require(string(author, "family").matches("[a-z0-9_]+")
                    && Set.of("world", "ui").contains(string(author, "scope")), "Authoring family/scope");
            JsonObject recipe = read(path(art, string(author, "recipe")));
            require(id.equals(string(recipe, "assetId")) && recipe.get("schema").getAsInt() == 1, "Recipe identity: " + id);
            String resource = string(a, "resource");
            require(resource.matches(NS + ":[a-z0-9_/]+") && !resource.contains("..") && resources.add(resource), "Duplicate/invalid audio resource");
            Set<String> revisions = new HashSet<>();
            for (JsonElement value : author.getAsJsonArray("revisions")) {
                JsonObject r = value.getAsJsonObject();
                require(string(r, "id").matches("r[0-9]{4,}") && revisions.add(string(r, "id")), "Duplicate/invalid revision");
                require(Set.of("pending", "accepted", "rejected").contains(string(r.getAsJsonObject("review"), "status")), "Review state");
            }
            String state = string(a, "status");
            require(Set.of("authoring_only", "approved").contains(state), "Asset status");
            if (!state.equals("approved")) continue;
            String revision = string(a, "revision");
            JsonObject record = null;
            for (JsonElement value : author.getAsJsonArray("revisions"))
                if (string(value.getAsJsonObject(), "id").equals(revision)) record = value.getAsJsonObject();
            require(record != null, "Approval references missing revision");
            JsonObject receipt = read(path(art, string(a, "receipt")));
            require(receipt.get("schema").getAsInt() == 1 && string(receipt, "assetId").equals(id)
                    && string(receipt, "revision").equals(revision), "Receipt identity mismatch");
            JsonObject review = receipt.getAsJsonObject("review");
            require(review.equals(record.getAsJsonObject("review")), "Review/authoring disagreement");
            boolean integration = receipt.has("integrationPromotion");
            if (integration) validateIntegration(art, id, revision, record, receipt);
            else {
                require(string(review, "status").equals("accepted")
                        && review.get("listened").getAsBoolean() && review.get("mixReviewed").getAsBoolean(), "Unreviewed audio");
                for (String field : List.of("license", "attribution", "reviewer", "notes"))
                    require(!string(review, field).isBlank(), "Missing review " + field);
                require(!receipt.get("loop").getAsBoolean() || review.get("loopReviewed").getAsBoolean(), "Unreviewed loop");
            }
            require(!record.get("project").isJsonNull() && record.getAsJsonObject("backup").get("closed").getAsBoolean(), "Missing closed project");
            JsonObject backup = record.getAsJsonObject("backup");
            require(string(receipt, "projectSha256").matches("[0-9a-f]{64}")
                    && string(receipt, "projectSha256").equals(string(backup, "projectSha256"))
                    && string(receipt, "backupSha256").equals(string(backup, "backupSha256"))
                    && string(receipt, "projectSha256").equals(string(receipt, "backupSha256")), "Closed project provenance");
            require(Set.of("synthesis", "audacity_edit").contains(string(receipt, "sourceMethod")), "Source method");
            if (receipt.has("sourceLinks")) validateSourceLinks(record, receipt.getAsJsonObject("sourceLinks"));
            for (String field : List.of("master", "approved", "recipe")) {
                String hash = string(receipt, field + "Sha256");
                require(hash.matches("[0-9a-f]{64}") && hash.equals(sha(path(art, string(a, field)))), "Changed approved " + field);
            }
            require(string(receipt, "masterSha256").equals(string(record, "masterSha256"))
                    && string(receipt, "approvedSha256").equals(string(record, "exportSha256"))
                    && string(receipt, "recipeSha256").equals(string(record, "recipeSha256"))
                    && receipt.get("encoder").equals(record.get("encoder"))
                    && receipt.get("measurements").equals(record.get("measurements"))
                    && receipt.get("loop").equals(record.get("loop")), "Receipt/authoring disagreement");
            JsonObject encoder = receipt.getAsJsonObject("encoder");
            require(!string(encoder, "version").isBlank() && string(encoder, "sha256").matches("[0-9a-f]{64}")
                    && encoder.getAsJsonArray("options").toString().contains("libvorbis"), "Encoder provenance");
            byte[] bytes = Files.readAllBytes(path(art, string(a, "approved")));
            Vorbis info = inspect(bytes);
            JsonObject measured = receipt.getAsJsonObject("measurements");
            require(string(measured, "codec").equals("vorbis") && info.rate == measured.get("sampleRate").getAsInt()
                    && info.channels == measured.get("channels").getAsInt() && info.rate == 48000
                    && (string(author, "scope").equals("ui") || info.channels == 1)
                    && Math.abs(info.duration - measured.get("duration").getAsDouble()) <= 0.03
                    && info.duration >= 0.02 && info.duration <= 30
                    && measured.get("truePeakDb").getAsDouble() <= -1
                    && measured.get("samplePeakDb").getAsDouble() <= -1
                    && measured.get("rmsDb").getAsDouble() > -120, "Invalid audio measurements");
            approved.add(id);
            add.accept("sounds/" + resource.substring(resource.indexOf(':') + 1) + ".ogg", bytes);
        }
        JsonObject policies = catalog.getAsJsonObject("policies"), cues = catalog.getAsJsonObject("cues");
        validatePolicies(policies);
        JsonObject sounds = new JsonObject(), runtimeCues = new JsonObject();
        JsonObject language = authored == null ? new JsonObject() : read(authored.resolve(ROOT + "lang/en_us.json"));
        for (String id : cues.keySet()) {
            require(id.matches(NS + ":[a-z0-9_./-]+") && !id.contains(".."), "Invalid cue identity");
            JsonObject cue = cues.getAsJsonObject(id);
            keys(cue, "playback", "recipients", "admission", "subtitleKey", "subtitleReason", "variants", "fallback");
            for (String kind : List.of("playback", "recipients", "admission"))
                require(policies.getAsJsonObject(kind).has(string(cue, kind)), "Unknown " + kind + " policy");
            String subtitle = string(cue, "subtitleKey");
            require(subtitle.equals("none") ? !string(cue, "subtitleReason").isBlank()
                    : language.has(subtitle) && language.get(subtitle).isJsonPrimitive()
                    && language.getAsJsonPrimitive(subtitle).isString() && !language.get(subtitle).getAsString().isBlank(), "Missing subtitle translation");
            if (cue.has("fallback")) require(string(cue, "fallback").matches("minecraft:[a-z0-9_./]+") && !string(cue, "fallback").contains(".."), "Invalid fallback event");
            JsonObject variants = cue.getAsJsonObject("variants");
            keys(variants, "selection", "samples");
            require(string(variants, "selection").equals("native_weighted") && variants.getAsJsonArray("samples").size() > 0, "Variant rule");
            boolean ready = false;
            JsonArray samples = new JsonArray();
            JsonArray runtimeSamples = new JsonArray();
            Set<String> used = new HashSet<>();
            for (JsonElement value : variants.getAsJsonArray("samples")) {
                JsonObject sample = value.getAsJsonObject();
                keys(sample, "asset", "event", "weight");
                require(sample.has("asset") != sample.has("event"), "Choose asset or native event");
                require(sample.get("weight").getAsInt() > 0 && sample.get("weight").getAsDouble() == sample.get("weight").getAsInt(), "Invalid sample weight");
                if (sample.has("event")) {
                    String event = string(sample, "event");
                    require(event.matches("minecraft:[a-z0-9_./]+") && !event.contains("..") && used.add(event), "Invalid native event");
                    JsonObject nativeSample = new JsonObject();
                    nativeSample.addProperty("name", event); nativeSample.addProperty("type", "event");
                    nativeSample.add("weight", sample.get("weight")); samples.add(nativeSample); runtimeSamples.add(sample); ready = true;
                    continue;
                }
                String asset = string(sample, "asset");
                require(assets.has(asset) && used.add(asset) && sample.get("weight").getAsInt() > 0
                        && sample.get("weight").getAsDouble() == sample.get("weight").getAsInt(), "Unknown/duplicate sample or weight");
                String scope = string(authors.getAsJsonObject(asset), "scope");
                require(scope.equals("world") || scope.equals("ui"), "Unsupported authoring scope");
                require(!string(policies.getAsJsonObject("recipients").getAsJsonObject(string(cue, "recipients")), "scope").equals("world")
                        || scope.equals("world"), "Positional cue requires world/mono asset");
                if (!approved.contains(asset)) continue;
                ready = true;
                JsonObject nativeSample = new JsonObject();
                nativeSample.addProperty("name", string(assets.getAsJsonObject(asset), "resource"));
                nativeSample.add("weight", sample.get("weight"));
                samples.add(nativeSample);
                runtimeSamples.add(sample);
            }
            if (!ready && cue.has("fallback")) {
                String fallback = string(cue, "fallback");
                require(fallback.matches("minecraft:[a-z0-9_./]+") && !fallback.contains(".."), "Invalid fallback event");
                JsonObject nativeSample = new JsonObject(); nativeSample.addProperty("name", fallback); nativeSample.addProperty("type", "event"); samples.add(nativeSample);
                JsonObject runtimeSample = new JsonObject(); runtimeSample.addProperty("event", fallback); runtimeSample.addProperty("weight", 1); runtimeSamples.add(runtimeSample); ready = true;
            }
            if (!ready) continue; // pending custom assets remain excluded; explicit vanilla fallback can serve the cue
            JsonObject sound = new JsonObject();
            if (!subtitle.equals("none")) sound.addProperty("subtitle", subtitle);
            sound.add("sounds", samples);
            sounds.add(id.substring(id.indexOf(':') + 1), sound);
            JsonObject resolvedCue = cue.deepCopy();
            resolvedCue.getAsJsonObject("variants").add("samples", runtimeSamples);
            runtimeCues.add(id, resolvedCue);
        }
        Set<String> semanticOwners = new HashSet<>();
        for (var event : catalog.getAsJsonObject("recipes").entrySet()) {
            String semantic = event.getKey().startsWith(NS + ":") ? event.getKey().substring(NS.length() + 1) : event.getKey();
            require(semanticOwners.add(semantic), "Duplicate bare/namespaced semantic owner");
            JsonObject recipe = event.getValue().getAsJsonObject();
            keys(recipe, "body", "minimumA", "minimumB", "maximumA", "alternatives");
            List<JsonObject> choices = recipeChoices(recipe);
            String audience = null;
            for (int i = 0; i < choices.size(); i++) {
                JsonObject choice = choices.get(i);
                if (i > 0) keys(choice, "body", "minimumA", "minimumB", "maximumA");
                require(cues.has(string(choice, "body")), "Unknown recipe cue");
                String recipients = string(cues.getAsJsonObject(string(choice, "body")), "recipients");
                if (audience == null) audience = recipients;
                require(audience.equals(recipients), "Recipe alternatives must share recipients");
                for (String bound : List.of("minimumA", "minimumB", "maximumA"))
                    if (choice.has(bound)) require(Double.isFinite(choice.get(bound).getAsDouble()), "Non-finite recipe bound");
                require(bound(choice, "minimumA", Double.NEGATIVE_INFINITY) <= bound(choice, "maximumA", Double.POSITIVE_INFINITY), "Contradictory recipe bounds");
                for (int j = 0; j < i; j++) {
                    JsonObject other = choices.get(j);
                    // Both predicates only have a lower bound for B, so B intervals always intersect.
                    require(bound(choice, "maximumA", Double.POSITIVE_INFINITY) < bound(other, "minimumA", Double.NEGATIVE_INFINITY)
                            || bound(other, "maximumA", Double.POSITIVE_INFINITY) < bound(choice, "minimumA", Double.NEGATIVE_INFINITY), "Ambiguous recipe alternatives");
                }
            }
        }
        for (var profile : catalog.getAsJsonObject("sourceProfiles").entrySet()) {
            JsonObject p = profile.getValue().getAsJsonObject();
            keys(p, "recipe", "lifecycle");
            require(catalog.getAsJsonObject("recipes").has(string(p, "recipe"))
                    && policies.getAsJsonObject("lifecycle").has(string(p, "lifecycle")), "Unknown source profile reference");
            require(!catalog.getAsJsonObject("recipes").getAsJsonObject(string(p, "recipe")).has("alternatives"), "Continuous profiles cannot select contextual alternatives");
            for (JsonObject choice : recipeChoices(catalog.getAsJsonObject("recipes").getAsJsonObject(string(p, "recipe")))) {
                String body = string(choice, "body");
                String recipients = string(cues.getAsJsonObject(body), "recipients");
                require(!string(policies.getAsJsonObject("recipients").getAsJsonObject(recipients), "scope").equals("ui"), "Continuous profiles require an owned world/player source");
                for (JsonElement value : cues.getAsJsonObject(body).getAsJsonObject("variants").getAsJsonArray("samples")) {
                    JsonObject sample = value.getAsJsonObject();
                    if (!sample.has("asset") || !approved.contains(string(sample, "asset"))) continue;
                    JsonObject asset = assets.getAsJsonObject(string(sample, "asset"));
                    require(read(path(art, string(asset, "receipt"))).get("loop").getAsBoolean(), "Continuous body requires a loop asset");
                }
            }
        }
        // Keep only assets used by available cues (no orphan OGGs).
        Set<String> usedAssets = new HashSet<>();
        for (var cue : runtimeCues.entrySet())
            for (JsonElement sample : cue.getValue().getAsJsonObject().getAsJsonObject("variants").getAsJsonArray("samples"))
                if (sample.getAsJsonObject().has("asset")) usedAssets.add(sample.getAsJsonObject().get("asset").getAsString());
        // add is staged by AssetPipeline, so unused approved assets are rejected rather than packaged.
        require(usedAssets.equals(approved), "Approved audio must be referenced by an available cue");
        JsonObject runtime = new JsonObject();
        runtime.addProperty("schema", 1);
        runtime.add("cues", runtimeCues);
        JsonObject runtimeAssets = new JsonObject();
        for (String id : usedAssets.stream().sorted().toList()) {
            JsonObject value = new JsonObject();
            value.add("resource", assets.getAsJsonObject(id).get("resource"));
            runtimeAssets.add(id, value);
        }
        runtime.add("assets", runtimeAssets);
        runtime.add("policies", policies);
        JsonObject runtimeRecipes = new JsonObject(), profiles = new JsonObject();
        for (var entry : catalog.getAsJsonObject("recipes").entrySet()) {
            boolean ready = recipeChoices(entry.getValue().getAsJsonObject()).stream().allMatch(choice -> runtimeCues.has(string(choice, "body")));
            if (ready) runtimeRecipes.add(entry.getKey(), entry.getValue());
        }
        for (var entry : catalog.getAsJsonObject("sourceProfiles").entrySet())
            if (runtimeRecipes.has(string(entry.getValue().getAsJsonObject(), "recipe"))) profiles.add(entry.getKey(), entry.getValue());
        runtime.add("recipes", runtimeRecipes);
        runtime.add("sourceProfiles", profiles);
        add.accept("sounds.json", (JSON.toJson(sounds) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        add.accept("audio/catalog.json", (JSON.toJson(runtime) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static List<JsonObject> recipeChoices(JsonObject recipe) {
        List<JsonObject> choices = new ArrayList<>(); choices.add(recipe);
        if (recipe.has("alternatives")) {
            JsonArray alternatives = recipe.getAsJsonArray("alternatives");
            require(!alternatives.isEmpty() && alternatives.size() <= 32, "Empty/oversized recipe alternatives");
            for (JsonElement value : alternatives) choices.add(value.getAsJsonObject());
        }
        return choices;
    }

    private static double bound(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }

    /** Integration authorization is distinct from final native listening/rights acceptance. */
    private static void validateIntegration(Path art, String id, String revision, JsonObject record, JsonObject receipt) throws Exception {
        JsonObject authorization = receipt.getAsJsonObject("integrationPromotion");
        keys(authorization, "status", "authority", "notes", "selection", "selectionSha256", "purpose", "sourceExportSha256");
        require(authorization.equals(record.getAsJsonObject("integrationPromotion"))
                && string(authorization, "status").equals("authorized")
                && !string(authorization, "authority").isBlank() && !string(authorization, "notes").isBlank()
                && string(authorization, "purpose").equals("runtime"), "Missing integration authorization");
        require(string(receipt.getAsJsonObject("review"), "status").equals("pending"), "Integration receipt cannot claim final acceptance");
        JsonObject creative = receipt.getAsJsonObject("creativeReview");
        require(creative != null && creative.equals(record.getAsJsonObject("creativeReview"))
                && string(creative, "status").equals("accepted"), "Unapproved creative selection");
        Path selectionPath = path(art, string(authorization, "selection"));
        require(string(authorization, "selectionSha256").equals(sha(selectionPath)), "Changed integration selection");
        require(string(authorization, "sourceExportSha256").equals(string(record, "exportSha256")), "Integration export disagreement");
        JsonObject selection = read(selectionPath);
        require(selection.get("schema").getAsInt() == 1, "Integration selection schema");
        int matches = 0;
        for (JsonElement value : selection.getAsJsonArray("items")) {
            JsonObject item = value.getAsJsonObject();
            if (!id.equals(string(item, "asset")) || !revision.equals(string(item, "revision"))) continue;
            matches++;
            require(string(item, "purpose").equals("runtime")
                    && string(item, "sourceExportSha256").equals(string(authorization, "sourceExportSha256")), "Selection does not authorize these runtime bytes");
        }
        require(matches == 1 && receipt.has("sourceLinks"), "Missing/duplicate integration selection or source links");
    }

    private static void validateSourceLinks(JsonObject record, JsonObject links) {
        require(string(links, "root").equals("auxiliary_audio"), "Source links root");
        for (String field : List.of("project", "master", "export")) {
            require(string(links, field).equals(string(record, field)), "Source link disagreement: " + field);
            relativeSourceLink(string(links, field));
        }
        require(string(links, "backup").equals(string(record.getAsJsonObject("backup"), "path"))
                && string(links, "recipe").equals(string(record, "recipeSnapshot")), "Backup/recipe source link disagreement");
        relativeSourceLink(string(links, "backup")); relativeSourceLink(string(links, "recipe"));
        for (String field : List.of("authoringInputs", "authoringInputsSha256", "stems"))
            require(Objects.equals(links.get(field), record.get(field)), "Source link disagreement: " + field);
        if (links.has("authoringInputs")) relativeSourceLink(string(links, "authoringInputs"));
        if (links.has("stems")) for (JsonElement value : links.getAsJsonArray("stems"))
            relativeSourceLink(string(value.getAsJsonObject(), "path"));
    }

    private static void relativeSourceLink(String path) {
        require(!path.isBlank() && !Path.of(path).isAbsolute() && !path.contains(":") && !path.contains("\\") && !path.contains(".."), "Unsafe authoring source link");
    }

    static Path path(Path root, String relative) throws IOException {
        Path p = root.resolve(relative).normalize();
        require(!Path.of(relative).isAbsolute() && !relative.contains("\\") && !relative.contains("..")
                && p.startsWith(root) && Files.isRegularFile(p), "Unsafe/missing audio source: " + relative);
        require(p.toRealPath().startsWith(root.toRealPath()), "Redirected audio source");
        return p;
    }
    static JsonObject read(Path p) throws IOException {
        // Gson's tree reader accepts duplicate keys; reject them before parsing.
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(p))) {
            unique(reader);
            require(reader.peek() == JsonToken.END_DOCUMENT, "Trailing JSON");
        }
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }
    private static void unique(JsonReader r) throws IOException {
        switch (r.peek()) {
            case BEGIN_OBJECT -> {
                r.beginObject(); Set<String> keys = new HashSet<>();
                while (r.hasNext()) { require(keys.add(r.nextName()), "Duplicate JSON key"); unique(r); }
                r.endObject();
            }
            case BEGIN_ARRAY -> { r.beginArray(); while (r.hasNext()) unique(r); r.endArray(); }
            case NUMBER -> require(Double.isFinite(r.nextDouble()), "Non-finite audio number");
            case STRING -> r.nextString();
            case BOOLEAN -> r.nextBoolean();
            case NULL -> r.nextNull();
            default -> throw new IOException("Malformed audio JSON");
        }
    }
    static String string(JsonObject o, String key) { return o.get(key).getAsString(); }
    static void keys(JsonObject object, String... names) {
        require(Set.of(names).containsAll(object.keySet()), "Unknown audio fields: " + object.keySet());
    }
    private static void number(JsonObject object, String field, double low, double high, boolean integer) {
        double value = object.get(field).getAsDouble();
        require(Double.isFinite(value) && value >= low && value <= high && (!integer || value == Math.rint(value)), "Invalid audio " + field);
    }
    private static void validatePolicies(JsonObject policies) {
        keys(policies, "playback", "recipients", "admission", "lifecycle");
        for (var entry : policies.getAsJsonObject("playback").entrySet()) {
            JsonObject p = entry.getValue().getAsJsonObject();
            keys(p, "category", "gain", "pitch", "attenuation", "priority");
            require(Set.of("BLOCKS", "PLAYERS", "MASTER").contains(string(p, "category"))
                    && Set.of("linear", "none").contains(string(p, "attenuation")), "Playback policy");
            number(p, "gain", 0, 1, false); number(p, "pitch", 0.25, 4, false); number(p, "priority", 0, 100, true);
        }
        for (var entry : policies.getAsJsonObject("recipients").entrySet()) {
            JsonObject p = entry.getValue().getAsJsonObject(); keys(p, "scope", "radius");
            String scope = string(p, "scope"); require(Set.of("world", "player", "ui").contains(scope), "Recipient scope");
            if (scope.equals("world")) number(p, "radius", 1, 128, false);
            else require(!p.has("radius"), "Private/UI policy cannot declare a world radius");
        }
        for (var entry : policies.getAsJsonObject("admission").entrySet()) {
            JsonObject p = entry.getValue().getAsJsonObject();
            keys(p, "group", "perSource", "perGroup", "repeatTicks", "overflow");
            require(!string(p, "group").isBlank() && string(p, "overflow").equals("prefer_near"), "Admission policy");
            number(p, "perSource", 1, 32, true); number(p, "perGroup", 1, 32, true); number(p, "repeatTicks", 0, 100, true);
        }
        for (var entry : policies.getAsJsonObject("lifecycle").entrySet()) {
            JsonObject p = entry.getValue().getAsJsonObject();
            keys(p, "attackTicks", "releaseTicks", "resume", "restartBackoffTicks");
            require(string(p, "resume").equals("body_only"), "Lifecycle resume");
            number(p, "attackTicks", 0, 100, true); number(p, "releaseTicks", 0, 100, true);
            number(p, "restartBackoffTicks", 20, 100, true);
        }
    }
    static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    record Vorbis(int channels, int rate, double duration) {}
    /** Validate Ogg framing, page CRCs, sequence, EOS and actual Vorbis identification.
     * Full decode is authoring-time evidence tied to these exact bytes in the receipt. */
    static Vorbis inspect(byte[] bytes) {
        int pos = 0, sequence = 0, serial = 0, channels = 0, rate = 0;
        long granule = -1; boolean eos = false;
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        int packets = 0;
        while (pos < bytes.length) {
            require(!eos && pos + 27 <= bytes.length, "Truncated/trailing Ogg page");
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            require(b.getInt(pos) == 0x5367674f && bytes[pos + 4] == 0, "Not Ogg");
            int flags = bytes[pos + 5] & 255, count = bytes[pos + 26] & 255;
            require(pos + 27 + count <= bytes.length, "Ogg segment table");
            int payload = 0;
            for (int i = 0; i < count; i++) payload += bytes[pos + 27 + i] & 255;
            int length = 27 + count + payload;
            require(pos + length <= bytes.length && b.getInt(pos + 18) == sequence, "Ogg page length/sequence");
            if (sequence == 0) { serial = b.getInt(pos + 14); require((flags & 2) != 0, "Missing Ogg BOS"); }
            require(serial == b.getInt(pos + 14), "Multiple Ogg streams");
            require(((flags & 1) != 0) == (packet.size() > 0), "Ogg continuation");
            int crc = 0;
            for (int i = 0; i < length; i++) {
                int value = i >= 22 && i < 26 ? 0 : bytes[pos + i] & 255;
                crc ^= value << 24;
                for (int bit = 0; bit < 8; bit++) crc = (crc << 1) ^ ((crc < 0) ? 0x04c11db7 : 0);
            }
            require(crc == b.getInt(pos + 22), "Ogg checksum");
            int at = pos + 27 + count;
            for (int i = 0; i < count; i++) {
                int size = bytes[pos + 27 + i] & 255;
                packet.write(bytes, at, size); at += size;
                if (size == 255) continue;
                byte[] data = packet.toByteArray();
                if (packets < 3) {
                    require(data.length >= 7 && data[0] == new int[]{1, 3, 5}[packets]
                            && new String(data, 1, 6, java.nio.charset.StandardCharsets.US_ASCII).equals("vorbis"), "Not Vorbis headers");
                    if (packets == 0) {
                        require(data.length == 30 && ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(7) == 0 && data[29] == 1, "Vorbis identification");
                        channels = data[11] & 255;
                        rate = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
                    }
                }
                packets++; packet.reset();
            }
            granule = b.getLong(pos + 6);
            eos = (flags & 4) != 0;
            sequence++; pos += length;
        }
        require(eos && packets > 3 && packet.size() == 0 && rate > 0 && granule > 0 && (channels == 1 || channels == 2), "Incomplete Vorbis stream");
        return new Vorbis(channels, rate, (double) granule / rate);
    }
}
