package com.mistaboom.essence_ascendance.skill;

import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import com.google.gson.JsonObject;

/** Approved design inputs exposed by the existing definitions, not an alternative balance calculator.
 * Skill floors retain native outcome units for final generated-state publication and validation.
 * A floor is only a lower bound. Excess may never fund a reduction elsewhere. */
public final class ProgressionRequirements {
    public record Skill(String home, String firstStateFloor, String additionalImprovementFloor,
                        String endpointPolicy, String mechanicalConstraints, String rankModel,
                        String domains, String bonusCompatibility, List<Outcome> outcomes, boolean alternativeImprovements) {
        public Skill { outcomes = outcomes == null ? List.of() : List.copyOf(outcomes); }
    }
    /** Native parameter measurement. Negative scales express shorter delays or less remaining damage.
     * Referenced factors describe a fixed gameplay context, e.g. full stacks or one pulse per minute. */
    public record Outcome(String path, double first, double later, double scale, double offset,
                          double minimum, double maximum, double quantum,
                          String multiplyBy, String divideBy, String addFrom, double addScale) {
        public double coefficient(JsonObject effects) {
            if (path.equals("posture/adaptive/resistancePerStack"))
                return read(effects, "posture/adaptive/maximumStacks") - read(effects, "posture/adaptive/minimumHits") + 1;
            if (path.equals("vitality/risingRecovery/maxSpeedBonus"))
                return Math.pow(.5, read(effects, "vitality/risingRecovery/recoveryCurveExponent"));
            return scale * (multiplyBy == null ? 1 : read(effects, multiplyBy))
                    / (divideBy == null ? 1 : read(effects, divideBy));
        }
        public double measure(JsonObject effects) {
            return read(effects, path) * coefficient(effects) + offset
                    + (addFrom == null ? 0 : read(effects, addFrom) * addScale);
        }
        public void grantFirst(JsonObject effects) {
            if (measure(effects) + 1e-9 >= first) return;
            double coefficient = coefficient(effects);
            double raw = read(effects, path) + (first - measure(effects)) / coefficient;
            raw = coefficient > 0 ? Math.ceil((raw - 1e-10) / quantum) * quantum
                    : Math.floor((raw + 1e-10) / quantum) * quantum;
            if (!Double.isFinite(raw) || raw < minimum - 1e-9 || raw > maximum + 1e-9)
                throw new IllegalArgumentException("First floor incompatible with native ceiling: " + path);
            write(effects, path, raw);
        }
        public void quantize(JsonObject effects) {
            double raw = Math.clamp(read(effects, path), minimum, maximum);
            raw = coefficient(effects) > 0 ? Math.floor((raw + 1e-10) / quantum) * quantum
                    : Math.ceil((raw - 1e-10) / quantum) * quantum;
            write(effects, path, Math.clamp(raw, minimum, maximum));
        }
    }
    public static double read(JsonObject root, String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) root = root.getAsJsonObject(parts[i]);
        return root.get(parts[parts.length - 1]).getAsDouble();
    }
    public static void write(JsonObject root, String path, double value) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) root = root.getAsJsonObject(parts[i]);
        root.addProperty(parts[parts.length - 1], value);
    }
    public record Bonus(String home, double firstStateFloor, double tierImprovementFloor, String unit,
                        String quantization, String mechanicalCap, String earliestConstraint,
                        String latestConstraint, String powerDesirability, String compatibility) {
        public boolean meetsFirstFloor(double benefit) { return Double.isFinite(benefit) && benefit >= firstStateFloor; }
        public boolean meetsImprovementFloor(double improvement) { return Double.isFinite(improvement) && improvement >= tierImprovementFloor; }
    }
    private record Document(Map<String, Skill> skills, Map<String, Bonus> bonuses) { }
    private static final Document DATA = load();
    private ProgressionRequirements() { }
    public static Skill skill(ResourceLocation id) { return Objects.requireNonNull(DATA.skills().get(id.getPath()), "Missing skill progression requirements: " + id); }
    public static Bonus bonus(ResourceLocation id) { return Objects.requireNonNull(DATA.bonuses().get(id.getPath()), "Missing bonus progression requirements: " + id); }
    private static Document load() {
        try (var stream = ProgressionRequirements.class.getResourceAsStream("/data/essence_ascendance/progression_requirements.json")) {
            if (stream == null) throw new IllegalStateException("Missing approved progression requirements");
            return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Document.class);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
