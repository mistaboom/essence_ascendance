package com.mistaboom.essence_ascendance.audio;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable build-validated program data. Parsed once, never from a packet or resource reload. */
public final class AudioCatalog {
    public enum Scope { WORLD, PLAYER, UI }
    public record Playback(SoundSource category, float gain, float pitch, boolean spatial, int priority) { }
    public record Audience(Scope scope, double radius) { }
    public record Admission(String group, int perSource, int perGroup, int repeatTicks) { }
    public record Cue(ResourceLocation id, Playback playback, Audience audience, Admission admission, String subtitle) { }
    public record Lifecycle(int attackTicks, int releaseTicks, int restartBackoffTicks) { }
    public record Profile(Cue body, Lifecycle lifecycle) { }
    /** Bounded semantic predicates, shared by server audience selection and client presentation. */
    private record Choice(Cue body, float minimumA, float minimumB, float maximumA) {
        Cue resolve(float a, float b) { return Float.isFinite(a) && Float.isFinite(b) && a>=minimumA && b>=minimumB && a<=maximumA ? body:null; }
    }
    private record Recipe(List<Choice> choices) {
        Cue resolve(float a, float b) {
            for (var choice : choices) { var cue = choice.resolve(a,b); if (cue != null) return cue; }
            return null;
        }
    }
    private static final class Holder { private static final AudioCatalog INSTANCE = load(); }
    private final Map<ResourceLocation, Cue> cues;
    private final Map<String, Recipe> recipes;
    private final Map<String, Profile> profiles;
    public static AudioCatalog get() { return Holder.INSTANCE; }
    public Collection<Cue> cues() { return cues.values(); }
    public Cue cue(ResourceLocation id) { return cues.get(id); }
    public Cue recipe(String semantic) { return recipe(semantic,0,0); }
    public Cue recipe(String semantic,float a,float b) {
        var recipe=recipes.get(semantic);
        // Bare program semantics and their EA wire ResourceLocations share one definition.
        if(recipe==null && semantic.startsWith("essence_ascendance:"))
            recipe=recipes.get(semantic.substring("essence_ascendance:".length()));
        return recipe==null?null:recipe.resolve(a,b);
    }
    public Profile profile(String id) { return profiles.get(id); }
    public Set<String> profileIds() { return profiles.keySet(); }
    public Set<String> recipeIds() { return recipes.keySet(); }
    private static AudioCatalog load() {
        try (var stream = AudioCatalog.class.getResourceAsStream("/assets/essence_ascendance/audio/catalog.json")) {
            if (stream == null) throw new IOException("Missing generated audio catalog");
            return new AudioCatalog(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException e) { throw new IllegalStateException("Cannot load audio definitions", e); }
    }
    public AudioCatalog(JsonObject root) {
        if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Audio schema");
        var policies = root.getAsJsonObject("policies");
        Map<String, Playback> playback = new HashMap<>();
        policies.getAsJsonObject("playback").entrySet().forEach(e -> {
            var p = e.getValue().getAsJsonObject();
            playback.put(e.getKey(), new Playback(SoundSource.valueOf(s(p,"category")), f(p,"gain"), f(p,"pitch"), s(p,"attenuation").equals("linear"), i(p,"priority")));
        });
        Map<String, Audience> audiences = new HashMap<>();
        policies.getAsJsonObject("recipients").entrySet().forEach(e -> {
            var p = e.getValue().getAsJsonObject();
            audiences.put(e.getKey(), new Audience(Scope.valueOf(s(p,"scope").toUpperCase(Locale.ROOT)), p.has("radius") ? p.get("radius").getAsDouble() : 0));
        });
        Map<String, Admission> admission = new HashMap<>();
        policies.getAsJsonObject("admission").entrySet().forEach(e -> {
            var p = e.getValue().getAsJsonObject();
            admission.put(e.getKey(), new Admission(s(p,"group"), i(p,"perSource"), i(p,"perGroup"), i(p,"repeatTicks")));
        });
        Map<ResourceLocation, Cue> definitions = new LinkedHashMap<>();
        root.getAsJsonObject("cues").entrySet().forEach(e -> {
            var p = e.getValue().getAsJsonObject(); var id = ResourceLocation.parse(e.getKey());
            definitions.put(id, new Cue(id, Objects.requireNonNull(playback.get(s(p,"playback"))), Objects.requireNonNull(audiences.get(s(p,"recipients"))), Objects.requireNonNull(admission.get(s(p,"admission"))), s(p,"subtitleKey")));
        });
        cues = Collections.unmodifiableMap(definitions);
        Map<String, Recipe> resolvedRecipes = new HashMap<>();
        root.getAsJsonObject("recipes").entrySet().forEach(e -> {
            var p=e.getValue().getAsJsonObject();
            List<Choice> choices = new ArrayList<>();
            choices.add(choice(p));
            if (p.has("alternatives")) for (var alternative : p.getAsJsonArray("alternatives"))
                choices.add(choice(alternative.getAsJsonObject()));
            resolvedRecipes.put(e.getKey(),new Recipe(List.copyOf(choices)));
        });
        recipes = Map.copyOf(resolvedRecipes);
        Map<String, Profile> resolvedProfiles = new HashMap<>();
        root.getAsJsonObject("sourceProfiles").entrySet().forEach(e -> {
            var p = e.getValue().getAsJsonObject(); var l = policies.getAsJsonObject("lifecycle").getAsJsonObject(s(p,"lifecycle"));
            resolvedProfiles.put(e.getKey(), new Profile(Objects.requireNonNull(recipe(s(p,"recipe"))), new Lifecycle(i(l,"attackTicks"),i(l,"releaseTicks"),i(l,"restartBackoffTicks"))));
        });
        profiles = Map.copyOf(resolvedProfiles);
    }
    private Choice choice(JsonObject p) {
        return new Choice(Objects.requireNonNull(cue(ResourceLocation.parse(s(p,"body")))),
                p.has("minimumA")?f(p,"minimumA"):-Float.MAX_VALUE,
                p.has("minimumB")?f(p,"minimumB"):-Float.MAX_VALUE,
                p.has("maximumA")?f(p,"maximumA"):Float.MAX_VALUE);
    }
    private static String s(JsonObject p, String k) { return p.get(k).getAsString(); }
    private static int i(JsonObject p, String k) { return p.get(k).getAsInt(); }
    private static float f(JsonObject p, String k) { return p.get(k).getAsFloat(); }
}
