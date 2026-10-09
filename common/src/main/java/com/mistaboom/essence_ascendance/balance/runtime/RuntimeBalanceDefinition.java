package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.ResourceLocationJsonAdapter;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.attunement.AttunementProfile;
import com.mistaboom.essence_ascendance.config.*;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureStats;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineConfig;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContribution;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.*;

/** Fully resolved, immutable runtime subset. Evidence never travels to clients. */
public final class RuntimeBalanceDefinition {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping()
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocationJsonAdapter())
            .registerTypeAdapter(MilestoneRequirement.class, new RequirementAdapter()).create();
    private final EssenceServerConfig config;
    private final EssenceCrucibleStructureStats crucible;
    private final Map<String, EssencePylonContribution> pylons;
    private final Map<String, SkillBalanceRuntime.ResolvedSkill> skillCurves;
    private final Map<String, Double> composition;
    private final RuntimeBuildScenarios.Analysis generationAnalysis;
    private final AttunementProfile attunement;
    private volatile com.mistaboom.essence_ascendance.network.RuntimeBalancePayload networkPayload;

    public RuntimeBalanceDefinition(EssenceServerConfig config, EssenceCrucibleStructureStats crucible,
            Map<String, EssencePylonContribution> pylons,
            Map<String, SkillBalanceRuntime.ResolvedSkill> skillCurves, Map<String, Double> composition) {
        this(config,crucible,pylons,skillCurves,composition,AttunementGenerator.bootstrap(config.balanceProfile()),null);
    }
    public RuntimeBalanceDefinition(EssenceServerConfig config, EssenceCrucibleStructureStats crucible,
            Map<String, EssencePylonContribution> pylons, Map<String, SkillBalanceRuntime.ResolvedSkill> skillCurves,
            Map<String, Double> composition, AttunementProfile attunement) {
        this(config,crucible,pylons,skillCurves,composition,attunement,null);
    }
    private RuntimeBalanceDefinition(EssenceServerConfig config, EssenceCrucibleStructureStats crucible,
            Map<String, EssencePylonContribution> pylons, Map<String,SkillBalanceRuntime.ResolvedSkill> skillCurves,
            Map<String,Double> composition, AttunementProfile attunement, RuntimeBuildScenarios.Analysis generationAnalysis) {
        this.generationAnalysis=generationAnalysis;
        this.attunement=Objects.requireNonNull(attunement, "Missing Category Attunement calibration; explicitly rebuild generated balance");
        this.config = Objects.requireNonNull(config);
        this.crucible = Objects.requireNonNull(crucible);
        this.pylons = Collections.unmodifiableMap(new TreeMap<>(pylons));
        this.skillCurves = Collections.unmodifiableMap(new TreeMap<>(skillCurves));
        this.composition = Collections.unmodifiableMap(new TreeMap<>(composition));
        validate();
    }
    public RuntimeBuildScenarios.Analysis generationAnalysis() { return generationAnalysis; }
    /** Immutable profile: encode once for preflight and reuse bounded bytes on joins. */
    public com.mistaboom.essence_ascendance.network.RuntimeBalancePayload networkPayload() {
        var current = networkPayload;
        if (current == null) networkPayload = current = new com.mistaboom.essence_ascendance.network.RuntimeBalancePayload(toJson().toString());
        return current;
    }
    public RuntimeBalanceDefinition withAnalysis(RuntimeBuildScenarios.Analysis analysis) {
        return new RuntimeBalanceDefinition(config,crucible,pylons,skillCurves,composition,attunement,analysis);
    }
    public EssenceServerConfig config() { return config; }
    public EssenceCrucibleStructureStats crucible() { return crucible; }
    public Map<String, EssencePylonContribution> pylons() { return pylons; }
    public Map<String, SkillBalanceRuntime.ResolvedSkill> skillCurves() { return skillCurves; }
    public Map<String, Double> composition() { return composition; }
    public AttunementProfile attunement() { return attunement; }
    public EssencePylonContribution pylon(String tier) {
        EssencePylonContribution result = pylons.get(tier);
        if (result == null) throw new IllegalArgumentException("Missing generated pylon tier " + tier);
        return result;
    }
    public static RuntimeBalanceDefinition generate(PackEvidence evidence, BalanceSettings settings, BalanceOverrides overrides) {
        return RuntimeBalanceGenerator.generate(evidence, settings, overrides);
    }
    public static RuntimeBalanceDefinition generate(PackEvidence evidence, EconomyProfile economy, BalanceSettings settings, BalanceOverrides overrides) {
        return RuntimeBalanceGenerator.generate(evidence, economy, settings, overrides);
    }
    public RuntimeBalanceDefinition withContentIdentity() {
        JsonObject json=toJson();
        json.getAsJsonObject("balanceProfile").addProperty("id","essence_ascendance:generated");
        try {
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            json.getAsJsonObject("balanceProfile").addProperty("id","essence_ascendance:generated_"+java.util.HexFormat.of().formatHex(digest).substring(0,16));
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return fromJson(json).withAnalysis(generationAnalysis);
    }
    public static RuntimeBalanceDefinition bootstrap() { return RuntimeBalanceGenerator.bootstrap(); }

    public void validate() {
        if (config.configVersion() != 1) throw new IllegalArgumentException("Unsupported runtime schema; rebuild generated profile");
        SkillBalanceRuntime.validate(skillCurves);
        var profile = config.balanceProfile();
        var validEquipment = new HashSet<ResourceLocation>();
        long previous = 0;
        double fraction = 0;
        var tiers = AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(t -> t.order())).toList();
        if (!profile.defaultTierCaps().keySet().equals(new LinkedHashSet<>(tiers.stream().map(t -> t.id()).toList())))
            throw new IllegalArgumentException("Generated tier cap identifiers do not match the registered tiers; rebuild profile");
        EquipmentBaselineConfig.TierBaseline previousEquipment = null;
        for (var tier : tiers) {
            validEquipment.add(tier.id());
            long cap = profile.getDefaultInvestmentCap(tier);
            Double next = profile.tierFractions().get(tier.id());
            if (!tier.grantsPower()) {
                if (cap != 0 || next == null || next != 0)
                    throw new IllegalArgumentException("Non-powered tiers must have zero Bonus cap and power fraction: " + tier.id());
            } else {
                if (cap <= previous || cap > Long.MAX_VALUE / 10000)
                    throw new IllegalArgumentException("Powered tier caps must strictly increase within safe accounting limits");
                if (next == null || !Double.isFinite(next) || next <= fraction || next > 1)
                    throw new IllegalArgumentException("Missing/non-monotonic generated tier fraction " + tier.id());
                previous = cap;
                fraction = next;
            }
            var baseline = config.equipmentBaselineConfig().baselineFor(tier);
            if (previousEquipment != null) {
                for (var property : com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.values())
                    if (baseline.value(property) < previousEquipment.value(property))
                        throw new IllegalArgumentException("Equipment curve decreases at " + tier.id() + ": " + property);
                if(baseline.harvestLevel()<previousEquipment.harvestLevel())throw new IllegalArgumentException("Equipment harvest curve decreases at "+tier.id());
            }
            previousEquipment=baseline;
        }
        if(!config.equipmentBaselineConfig().tierBaselines().keySet().equals(validEquipment))
            throw new IllegalArgumentException("Generated equipment tier identifiers are incomplete or obsolete");
        for(var baseline:config.equipmentBaselineConfig().tierBaselines().values()) {
            if(baseline.durability()>Integer.MAX_VALUE/64||baseline.harvestLevel()>32)
                throw new IllegalArgumentException("Equipment durability/harvest tier exceeds supported bounds");
        }
        if (fraction != 1.0) throw new IllegalArgumentException("Final tier fraction must equal one");
        var statIds = new HashSet<ResourceLocation>();
        for (var stat : EssenceStatRegistry.values()) {
            statIds.add(stat.id());
            double max = config.statMaxBonus(stat);
            if (!Double.isFinite(max) || max < 0 || max > 1_000_000) throw new IllegalArgumentException("Invalid generated stat maximum " + stat.id());
            var track=Objects.requireNonNull(profile.bonusTrack(stat.id()), "Missing resolved Bonus " + stat.id());
            if (!track.statId().equals(stat.id()) || track.category()!=stat.category() || track.unit()!=stat.unit()
                    || track.maximumEffect()!=max || !track.checkpoints().stream().map(BonusTrackDefinition.Checkpoint::tierId).toList()
                            .equals(tiers.stream().map(t->t.id()).toList()))
                throw new IllegalArgumentException("Inconsistent resolved Bonus identity/maximum/topology " + stat.id());
            for(var tier:tiers) {
                var point=track.checkpoint(tier.id());
                if (!tier.grantsPower() && (point.cumulativeCap()!=0 || point.effectFraction()!=0 || point.available()))
                    throw new IllegalArgumentException("Onboarding tier grants Bonus " + stat.id());
                if(!Objects.equals(profile.statOverrides().getOrDefault(stat.id(),Map.of()).get(tier.id()),point.cumulativeCap()))
                    throw new IllegalArgumentException("Bonus cap mirror differs from authoritative checkpoint " + stat.id());
            }
        }
        if (!config.statMaxBonuses().keySet().equals(statIds) || !profile.statOverrides().keySet().equals(statIds)
                || !profile.bonusTracks().keySet().equals(statIds))
            throw new IllegalArgumentException("Generated stat identifiers are incomplete or obsolete; rebuild profile");
        if (config.maxActivePylons() > 8 || config.pylonRadius() > 32)
            throw new IllegalArgumentException("Generated pylon scan exceeds supported capacity/radius");
        if (crucible.usableItemSlots() != 1 || crucible.activePylonCount() != 0 || crucible.automationConnectionPorts() != 6)
            throw new IllegalArgumentException("Base Crucible must preserve one input, zero pylons and six automation faces");
        pylon("empty");
        long lastCapacity=0,lastThroughput=0,lastUpgrade=0,lastFocus=0;
        for (EssenceFocusTier tier : EssenceFocusTier.values()) {
            pylon(tier.serializedName());
            var grade=config.infuserBalance().grade(tier.serializedName());
            var upgrade=config.infuserBalance().equipmentUpgrade(tier.serializedName());
            var focus=config.infuserBalance().focusUpgrade(tier.serializedName());
            if(grade.ingotCapacity()<lastCapacity||grade.infusionThroughputPerSecond()<lastThroughput
                    ||upgrade.totalEssenceRequired()<lastUpgrade||focus.totalEssenceRequired()<lastFocus)
                throw new IllegalArgumentException("Processing/equipment economy curves must be monotonic");
            if(grade.ingotCapacity()>Long.MAX_VALUE/100_000_000||grade.infusionThroughputPerSecond()>Long.MAX_VALUE/10000)
                throw new IllegalArgumentException("Carrier capacity/throughput exceeds safe fractional accounting bounds");
            lastCapacity=grade.ingotCapacity();lastThroughput=grade.infusionThroughputPerSecond();
            lastUpgrade=upgrade.totalEssenceRequired();lastFocus=focus.totalEssenceRequired();
        }
        for(var pylon:pylons.values()) {
            if(pylon.reservoirCapacityBonus()>Long.MAX_VALUE/16||pylon.transferRatePerSecondBonus()>Long.MAX_VALUE/16
                    ||pylon.simultaneousItemProcessesBonus()>128||pylon.transferRangeBonus()>64||pylon.dissolutionSpeedBonus()>100)
                throw new IllegalArgumentException("Generated pylon contribution exceeds safe structure bounds");
        }
        for(var entry:config.milestones().entrySet()) {
            var milestone=entry.getValue();
            if(!entry.getKey().equals(milestone.id()))throw new IllegalArgumentException("Milestone key/id mismatch");
        }
        for(var entry:config.advancements().entrySet()) {
            var advancement=entry.getValue();
            if(!entry.getKey().equals(advancement.id()))throw new IllegalArgumentException("Advancement key/id mismatch");
            var from=AscendanceTierRegistry.get(advancement.fromTierId()).orElseThrow(()->
                    new IllegalArgumentException("Unknown source tier for advancement "+advancement.id()+": "+advancement.fromTierId()));
            var to=AscendanceTierRegistry.get(advancement.toTierId()).orElseThrow(()->
                    new IllegalArgumentException("Unknown target tier for advancement "+advancement.id()+": "+advancement.toTierId()));
            if(to.order()<=from.order())throw new IllegalArgumentException("Advancement must increase tier: "+advancement.id());
            try {
                advancement.getRequiredInvestment(profile.getDefaultInvestmentCap(from));
            } catch(ArithmeticException overflow) {
                throw new IllegalArgumentException("Advancement investment requirement exceeds supported accounting bounds: "+advancement.id(),overflow);
            }
            validateMilestoneReferences(advancement.worldRequirement(),advancement.id());
        }
        var skillIds = new HashSet<String>();
        RuntimeAscensionPolicy.validate(this);
        for (var skill : SkillRegistry.values()) skillIds.add(skill.id().toString());
        if (!skillCurves.keySet().equals(skillIds)) throw new IllegalArgumentException("Generated skill curve identifiers do not match the registry");
        for (var entry : skillCurves.entrySet()) {
            var curve = entry.getValue();
            if (curve.maximumRank() < 1 || curve.ranks().size() < curve.maximumRank()) throw new IllegalArgumentException("Incomplete rank curve " + entry.getKey());
            long lastCost = 0; double lastPower = -1; int rank = 0;
            for (var point : curve.ranks()) {
                if (point.rank() != ++rank || point.cost() < lastCost || point.cost() <= 0 || !Double.isFinite(point.powerMultiplier()) || point.powerMultiplier() < lastPower)
                    throw new IllegalArgumentException("Invalid rank curve " + entry.getKey());
                lastCost = point.cost(); lastPower = point.powerMultiplier();
            }
        }
        composition.forEach((key,value) -> { if (value == null || !Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Invalid composition value " + key); });
        for (String required : List.of("equipment_share", "nexus_share", "skill_share", "rank_safe_skill_scale"))
            if (!composition.containsKey(required)) throw new IllegalArgumentException("Missing composition budget " + required);
        config.skillEffects().validate();
        if (composition.getOrDefault("meaningful_progression", 0.0) == 1)
            com.mistaboom.essence_ascendance.skill.balance.SkillBalanceGenerator.validatePublished(config.skillEffects(), skillCurves);
        com.mistaboom.essence_ascendance.network.RuntimeBalancePayload.validateJsonSize(toJson().toString());
    }

    /** Provider implementations may be installed only on the logical server; clients need only their resolved display data. */
    public void validateServerReferences() {
        for(var milestone:config.milestones().values())
            if(MilestoneProviderRegistry.get(milestone.providerId()).isEmpty())
                throw new IllegalArgumentException("Unknown milestone provider "+milestone.providerId()+" for "+milestone.id());
    }

    private void validateMilestoneReferences(MilestoneRequirement requirement, ResourceLocation advancementId) {
        if(requirement instanceof MilestoneRequirement.Milestone milestone) {
            if(!config.milestones().containsKey(milestone.milestoneId()))
                throw new IllegalArgumentException("Unknown milestone "+milestone.milestoneId()+" referenced by advancement "+advancementId);
        } else if(requirement instanceof MilestoneRequirement.AllOf all) {
            all.children().forEach(child->validateMilestoneReferences(child,advancementId));
        } else if(requirement instanceof MilestoneRequirement.AnyOf any) {
            any.children().forEach(child->validateMilestoneReferences(child,advancementId));
        }
    }

    public JsonObject toJson() {
        var profile = config.balanceProfile();
        var worldgen = config.latentOreWorldgen();
        Wire wire = new Wire(config.configVersion(), config.pylonRadius(), config.maxActivePylons(),
                config.infuserBalance(), config.shieldBalance(), config.skillEffects(),
                worldgen,
                new ProfileWire(profile.id(), profile.displayName(), profile.defaultTierCaps(), profile.statOverrides(), profile.tierFractions(), profile.investmentExponent(), profile.bonusTracks()),
                config.milestones(), config.advancements(), config.statMaxBonuses(), config.equipmentBaselineConfig().tierBaselines(),
                crucible, pylons, skillCurves, composition, attunement);
        return JSON.toJsonTree(wire).getAsJsonObject();
    }

    /** Uses the same validated format before spawn generation, without resolving unrelated gameplay data. */
    public static JsonObject worldgenJson(LatentOreWorldgenSettings settings) {
        return JSON.toJsonTree(settings).getAsJsonObject();
    }

    public static LatentOreWorldgenSettings worldgenFromJson(JsonObject json) {
        var settings = Objects.requireNonNull(JSON.fromJson(json, LatentOreWorldgenSettings.class), "Missing worldgen policy");
        if (!worldgenJson(settings).equals(json))
            throw new IllegalArgumentException("Worldgen policy has missing, unknown, or invalid fields; regenerate generated_balance.json.gz");
        return settings;
    }
    /** Strict round trip, allowing only the iron harvest floor upgrade for saved profiles. */
    public static RuntimeBalanceDefinition fromJson(JsonObject json) {
        Wire wire = JSON.fromJson(json, Wire.class);
        Objects.requireNonNull(wire, "Missing runtime profile");
        ProfileWire p = Objects.requireNonNull(wire.balanceProfile);
        LatentOreWorldgenSettings w = Objects.requireNonNull(wire.worldgen);
        var profile = new BalanceProfileDefinition(p.id, p.displayName, p.defaultTierCaps, p.statOverrides, p.tierFractions, p.investmentExponent, Objects.requireNonNull(p.bonusTracks, "Missing resolved Bonus tracks; explicitly rebuild generated balance"));
        var config = new EssenceServerConfig(wire.configVersion, wire.pylonRadius, wire.maxActivePylons,
                wire.infuser, wire.shield, wire.effects, w,
                profile, wire.milestones, wire.advancements, wire.statMaxBonuses, new EquipmentBaselineConfig(wire.equipment));
        var result = new RuntimeBalanceDefinition(config, wire.crucible, wire.pylons, wire.skillCurves, wire.composition,
                Objects.requireNonNull(wire.attunement, "Missing Category Attunement calibration; use /essence admin balance rebuild"));
        JsonObject expected = json.deepCopy();
        for (var entry : expected.getAsJsonObject("equipment").entrySet()) {
            JsonObject baseline = entry.getValue().getAsJsonObject();
            JsonElement level = baseline.get("harvestLevel");
            // Only the two formerly valid levels below iron may change. Missing,
            // fractional, negative and unknown fields still fail the round trip.
            if (new JsonPrimitive(0).equals(level) || new JsonPrimitive(1).equals(level))
                baseline.addProperty("harvestLevel", EquipmentBaselineConfig.MINIMUM_HARVEST_LEVEL);
        }
        if (!result.toJson().equals(expected)) throw new IllegalArgumentException("Runtime profile has missing, unknown, or out-of-range fields; rebuild it instead of editing generated JSON");
        return result;
    }
    private record Wire(int configVersion, double pylonRadius, int maxActivePylons, InfuserBalanceSettings infuser,
            ShieldBalanceSettings shield, SkillEffectBalanceSettings effects, LatentOreWorldgenSettings worldgen, ProfileWire balanceProfile,
            Map<ResourceLocation,MilestoneDefinition> milestones, Map<ResourceLocation,AscendanceAdvancementDefinition> advancements,
            Map<ResourceLocation,Double> statMaxBonuses, Map<ResourceLocation,EquipmentBaselineConfig.TierBaseline> equipment,
            EssenceCrucibleStructureStats crucible, Map<String,EssencePylonContribution> pylons,
            Map<String,SkillBalanceRuntime.ResolvedSkill> skillCurves, Map<String,Double> composition, AttunementProfile attunement) {}
    private record ProfileWire(ResourceLocation id, String displayName, Map<ResourceLocation,Long> defaultTierCaps,
            Map<ResourceLocation,Map<ResourceLocation,Long>> statOverrides, Map<ResourceLocation,Double> tierFractions, double investmentExponent,
            Map<ResourceLocation,BonusTrackDefinition> bonusTracks) {}
    private static final class RequirementAdapter implements JsonSerializer<MilestoneRequirement>, JsonDeserializer<MilestoneRequirement> {
        public JsonElement serialize(MilestoneRequirement value, Type type, JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            if (value instanceof MilestoneRequirement.Milestone node) { result.addProperty("kind","milestone"); result.addProperty("id",node.milestoneId().toString()); }
            else if (value instanceof MilestoneRequirement.AllOf node) { result.addProperty("kind","all"); result.add("children",children(node.children(),context)); }
            else if (value instanceof MilestoneRequirement.AnyOf node) { result.addProperty("kind","any"); result.add("children",children(node.children(),context)); }
            else result.addProperty("kind","always");
            return result;
        }
        private JsonArray children(List<MilestoneRequirement> children,JsonSerializationContext context) {
            JsonArray result = new JsonArray(); children.forEach(c -> result.add(context.serialize(c,MilestoneRequirement.class))); return result;
        }
        public MilestoneRequirement deserialize(JsonElement value,Type type,JsonDeserializationContext context) {
            return parse(value.getAsJsonObject(),0);
        }
        private MilestoneRequirement parse(JsonObject value,int depth) {
            if (depth > 32) throw new JsonParseException("Milestone requirement tree exceeds 32 levels");
            String kind = value.get("kind").getAsString();
            if (kind.equals("always")) return MilestoneRequirement.always();
            if (kind.equals("milestone")) return MilestoneRequirement.milestone(ResourceLocation.parse(value.get("id").getAsString()));
            List<MilestoneRequirement> children = new ArrayList<>();
            for (JsonElement child : value.getAsJsonArray("children")) children.add(parse(child.getAsJsonObject(),depth+1));
            return switch (kind) { case "all" -> new MilestoneRequirement.AllOf(children); case "any" -> new MilestoneRequirement.AnyOf(children); default -> throw new JsonParseException("Unknown milestone requirement kind " + kind); };
        }
    }
}
