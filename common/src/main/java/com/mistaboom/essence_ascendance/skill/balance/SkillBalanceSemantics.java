package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.*;

/**
 * Non-localized balance intent for the curated catalog. Values are dimensionless
 * budget weights, not damage, percentages, or promises of implemented effects.
 * Timing, trigger frequency, and setup are explicit first-pass estimates. The
 * report must distinguish these estimates from observed pack evidence.
 */
public final class SkillBalanceSemantics {
    public enum Form { FLAT, MULTIPLIER, CAPABILITY }
    public enum Delivery { MELEE, RANGED, CASTER }
    public enum Equipment { MELEE_WEAPON, RANGED_WEAPON, CASTER_FOCUS, SHIELD, ASCENDANCE_TOOL, PICKAXE, FISHING_ROD, REPAIRABLE_GEAR }
    public enum Action { ATTACK, FULL_ATTACK, REPEATED_HIT, SAME_TARGET, KILL, BLOCK, PERFECT_BLOCK,
        SWING, MOVE, SPRINT, STAND_STILL, FACE_THREAT, JUMP, SNEAK, SWIM, GLIDE,
        MINE, FISH, EAT, DRINK, REPAIR, REST, TRADE, BREED, ENCHANT, PROCESS, TAKE_DAMAGE, LETHAL_DAMAGE }

    /** Flat and multiplier weights are composed separately; capabilities never become DPS. */
    public record Contribution(CapabilityAxis axis, Form form, double weight, int targets) {
        public Contribution {
            Objects.requireNonNull(axis);
            Objects.requireNonNull(form);
            if (!Double.isFinite(weight) || weight < 0 || targets < 1 || targets > 64) {
                throw new IllegalArgumentException("Invalid semantic contribution");
            }
        }
    }

    public record Descriptor(ResourceLocation skillId, List<Contribution> contributions,
                             Set<Delivery> deliveries, Set<Equipment> equipment, Set<Action> actions,
                             double uptime, double triggerReliability, double cooldownSeconds,
                             double durationSeconds, double rangeBlocks, double areaRadiusBlocks,
                             double resourceCost, double setupRisk, String condition,
                             double confidence, String provenance) {
        public Descriptor {
            Objects.requireNonNull(skillId);
            contributions = List.copyOf(contributions);
            deliveries = Set.copyOf(deliveries);
            equipment = Set.copyOf(equipment);
            actions = Set.copyOf(actions);
            if (contributions.isEmpty() || condition == null || condition.isBlank()
                    || provenance == null || provenance.isBlank()) {
                throw new IllegalArgumentException("Incomplete semantics for " + skillId);
            }
            unit(uptime); unit(triggerReliability); unit(confidence);
            for (double value : new double[]{cooldownSeconds, durationSeconds, rangeBlocks,
                    areaRadiusBlocks, resourceCost, setupRisk}) {
                if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid semantic condition");
            }
        }

        public boolean implemented() { return SkillEffectRegistry.isImplemented(skillId); }

        /** Rank-independent reference pressure used to allocate a shared axis budget. */
        public Map<CapabilityAxis, Double> weights() {
            Map<CapabilityAxis, Double> result = new EnumMap<>(CapabilityAxis.class);
            for (Contribution c : contributions) result.merge(c.axis(), c.weight(), Double::sum);
            return Collections.unmodifiableMap(result);
        }

        public double expectedAvailability() {
            double duty = cooldownSeconds > 0 && durationSeconds > 0
                    ? Math.min(uptime, durationSeconds / cooldownSeconds) : uptime;
            return duty * triggerReliability / (1.0 + resourceCost + setupRisk);
        }
    }

    private static final Map<ResourceLocation, Descriptor> REGISTRY = defaults();
    private SkillBalanceSemantics() { }

    /** Providers can register future skills without changing projection algorithms. */
    public static synchronized void register(Descriptor descriptor) {
        if (REGISTRY.putIfAbsent(descriptor.skillId(), descriptor) != null) {
            throw new IllegalArgumentException("Duplicate skill semantics: " + descriptor.skillId());
        }
    }

    public static synchronized Descriptor require(ResourceLocation id) {
        Descriptor descriptor = REGISTRY.get(id);
        if (descriptor == null) throw new IllegalArgumentException("Missing semantic balance descriptor: " + id);
        return descriptor;
    }

    public static synchronized List<Descriptor> descriptors() { return List.copyOf(REGISTRY.values()); }

    public static void validate(Collection<SkillDefinition> definitions) {
        definitions.forEach(definition -> require(definition.id()));
    }

    private static Map<ResourceLocation, Descriptor> defaults() {
        Map<ResourceLocation, Descriptor> result = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        List<Builder> all = new ArrayList<>();
        offense(all); defense(all); vitality(all); mobility(all); gathering(all); utility(all);
        for (Builder builder : all) {
            Descriptor descriptor = builder.build();
            if (result.put(descriptor.skillId(), descriptor) != null) throw new IllegalStateException("Duplicate semantics");
        }
        return result;
    }

    private static void offense(List<Builder> all) {
        all.add(skill(SkillIds.FRENZY, multi(SUSTAINED_DAMAGE, .40), multi(ATTACK_RATE, .30))
                .attacks().actions(Action.REPEATED_HIT).availability(.65, .9).risk(.15)
                .condition("Maintain accepted attack chain; miss or timeout clears stacks"));
        all.add(skill(SkillIds.ARMOR_CRACK, flat(ARMOR_PENETRATION, .60))
                .attacks().actions(Action.SAME_TARGET, Action.REPEATED_HIT).availability(.55, .8).risk(.2)
                .condition("Frenzy branch active; repeated attacks on one armored target"));
        all.add(skill(SkillIds.DESPERATION, multi(SUSTAINED_DAMAGE, .60), multi(BURST_DAMAGE, .45))
                .attacks().actions(Action.ATTACK).availability(.35, 1).risk(.65)
                .condition("Low current health; increasing benefit trades against survival margin"));
        all.add(skill(SkillIds.DEATH_RUSH, multi(ATTACK_RATE, .65))
                .attacks().actions(Action.KILL).availability(.25, .7).risk(.6).timing(0, 8)
                .condition("Desperation branch, below-half-health kills, limited-duration stacks"));
        all.add(skill(SkillIds.KINDLING, flat(DAMAGE_OVER_TIME, .55), multi(SUSTAINED_DAMAGE, .25))
                .attacks().actions(Action.REPEATED_HIT).availability(.6, .85).risk(.1).timing(0, 5)
                .condition("Repeated hits fill source-owned heat; burning vulnerability and periodic damage"));
        all.add(skill(SkillIds.COMBUSTION, area(AREA_DAMAGE, .45, 3), flat(DAMAGE_OVER_TIME, .2))
                .attacks().actions(Action.KILL).availability(.25, .65).area(4)
                .condition("Kill a burning target; bounded chain propagation, no terrain damage"));
        all.add(skill(SkillIds.FROSTBITE, flat(CROWD_CONTROL, .65))
                .attacks().actions(Action.REPEATED_HIT).availability(.55, .85).timing(0, 2)
                .condition("Accumulate chill before freeze; expiry and freeze immunity limit control"));
        all.add(skill(SkillIds.SHATTER, area(AREA_DAMAGE, .55, 3))
                .attacks().actions(Action.KILL).availability(.2, .65).area(4)
                .condition("Frozen target must die before freeze expires; bounded shard targets"));
        all.add(skill(SkillIds.STATIC_CHARGE, flat(BURST_DAMAGE, .8))
                .attacks().actions(Action.MOVE, Action.FULL_ATTACK).availability(.4, .85).risk(.15)
                .condition("Movement stores charge consumed once by a fully charged melee, bow, or caster attack"));
        all.add(skill(SkillIds.CHAIN_STRIKE, area(AREA_DAMAGE, .5, 3))
                .attacks().actions(Action.MOVE, Action.FULL_ATTACK).availability(.35, .8).range(6)
                .condition("Static-charge branch; nearby distinct targets; damage decays at each hop"));
        all.add(skill(SkillIds.HOMING_PROJECTILE, flat(DELIVERY_RELIABILITY, .6))
                .projectiles().actions(Action.ATTACK).availability(1, .9).range(24)
                .condition("Aim-cone acquisition and line of sight; one selected projectile path"));
        all.add(skill(SkillIds.RICOCHET, area(AREA_DAMAGE, .45, 3))
                .projectiles().actions(Action.ATTACK).availability(.65, .85).range(6)
                .condition("Initial accepted hit plus a nearby unvisited target; hop and damage limits"));
        all.add(skill(SkillIds.PIERCING_PROJECTILE, area(AREA_DAMAGE, .5, 3), flat(SHIELD_INTERACTION, .45))
                .projectiles().actions(Action.ATTACK).availability(.45, .8)
                .condition("Aligned targets or a blocking shield; reduced penetrating damage; adapter-ready bypass"));
        all.add(skill(SkillIds.EXPLOSIVE_PAYLOAD, area(AREA_DAMAGE, .6, 3))
                .projectiles().actions(Action.ATTACK).availability(.65, .85).area(3)
                .condition("Confirmed hostile creature impacts; bounded per-shot payload budget composes with selected path; safe creature-only secondary damage"));
        all.add(skill(SkillIds.ROOTING_PAYLOAD, flat(CROWD_CONTROL, .6))
                .projectiles().actions(Action.ATTACK).availability(.55, .85).timing(0, 2)
                .condition("Confirmed victim only; bounded root duration and refresh window; translation suppressed while attacks remain available"));
    }

    private static void defense(List<Builder> all) {
        all.add(skill(SkillIds.GUARDED_ADVANCE, multi(GROUND_SPEED, .45), flat(JUMP, .2))
                .equipment(Equipment.SHIELD).actions(Action.BLOCK, Action.SPRINT).availability(.6, 1)
                .condition("Functional registered shield actively raised; native sprint/jump and short steps; slowdown resolves once, no inactive movement bonus"));
        all.add(skill(SkillIds.SHIELD_RAM, flat(CROWD_CONTROL, .55))
                .equipment(Equipment.SHIELD).actions(Action.BLOCK, Action.SPRINT).availability(.35, .75).risk(.25)
                .condition("Forward guarded sprint swept contact, speed threshold and terrain clipping; bounded stagger/push, repeat guard, no damage or block reward"));
        all.add(skill(SkillIds.STORED_FORCE, flat(BURST_DAMAGE, .65), flat(CROWD_CONTROL, .25))
                .deliveries(Delivery.MELEE).equipment(Equipment.SHIELD).actions(Action.BLOCK, Action.ATTACK).availability(.35, .8).risk(.2)
                .condition("Measured positive block prevention adds capped charge, refreshes idle expiry; one accepted positive primary melee hit consumes; exclusive with Guard Amplifier"));
        all.add(skill(SkillIds.GUARD_AMPLIFIER, multi(REFLECTION, .7))
                .equipment(Equipment.SHIELD).actions(Action.BLOCK, Action.PERFECT_BLOCK).availability(.45, .8)
                .condition("Positive blocks grow and refresh reflection multiplier before their reflection; native-ready perfect timing reaches maximum; exclusive with Stored Force"));
        all.add(skill(SkillIds.REFLEXIVE_WARD, flat(REFLECTION, .5), flat(CROWD_CONTROL, .2))
                .actions(Action.TAKE_DAMAGE).availability(.6, .8)
                .condition("Valid fully prevented positive hostile force extends existing equipment reflection; attempted knockback echoes toward responsible living source; no secondary recursion"));
        all.add(skill(SkillIds.CROWD_REPRISAL, area(AREA_DAMAGE, .4, 3))
                .actions(Action.TAKE_DAMAGE).availability(.35, .75).area(4)
                .condition("Confirmed primary reflected damage only; reduced secondary damage to bounded visible hostiles excludes defender and primary, never amplified again"));
        all.add(skill(SkillIds.INTERCEPTOR, flat(BLOCKING, .55))
                .equipment(Equipment.MELEE_WEAPON).actions(Action.SWING).availability(.4, .6).risk(.2).range(3)
                .condition("Ready ordinary server melee swing must intersect supported hostile projectile in the forward weapon arc; no speculative prevention credit"));
        all.add(skill(SkillIds.TRAJECTORY_THEFT, flat(REFLECTION, .55), flat(DELIVERY_RELIABILITY, .2))
                .equipment(Equipment.MELEE_WEAPON).actions(Action.SWING).availability(.35, .6).range(24)
                .condition("Interceptor redirects once to valid crosshair creature then responsible source; defender owns native damage, original path and payload are cleared; no speculative activity"));
        all.add(skill(SkillIds.PROJECTILE_DRAG_FIELD, flat(AVOIDANCE, .45))
                .availability(.5, .9).area(5).condition("Supported approaching hostile projectiles; strongest field wins, bounded reference speed restores on exit; avoidance only"));
        all.add(skill(SkillIds.EVASIVE_CURRENT, flat(AVOIDANCE, .55))
                .actions(Action.MOVE).availability(.6, .85).risk(.15)
                .condition("Server-correlated intentional movement builds evasion; one roll per eligible native hit; success and taken hits drain, stopping drains; forced motion cannot charge"));
        all.add(skill(SkillIds.BULWARK_STANCE, flat(DAMAGE_REDUCTION, .6), capability(CROWD_CONTROL, .3), flat(BURST_SURVIVAL, .3))
                .actions(Action.STAND_STILL, Action.FACE_THREAT).availability(.55, .8).risk(.3)
                .condition("Stationary frontal defense facing a valid visible hostile threat; movement/turning drain posture; thresholded correlated knockback immunity is control, not harmful-status immunity"));
        all.add(skill(SkillIds.ADAPTIVE_GUARD, flat(DAMAGE_REDUCTION, .55), flat(SUSTAINED_SURVIVAL, .35))
                .actions(Action.TAKE_DAMAGE).availability(.55, .7).risk(.2)
                .condition("Exact registry DamageType identity; first eligible hit seeds, repeated hits begin mitigation at the generated threshold; type switch reseeds before that hit; secondary/rejected hits never build"));
        all.add(skill(SkillIds.STATUS_MIRROR, flat(STATUS_RESISTANCE, .55), flat(REFLECTION, .25))
                .availability(.5, .75).timing(com.mistaboom.essence_ascendance.config.StatusBalanceSettings.defaults().mirrorCooldownTicks() / 20.0, 0)
                .condition("First actual eligible harmful effect from a valid hostile responsible living source is removed and copied through native semantics; confirmed interception starts cooldown once, transfer rejection does not revoke protection; no secondary recursion"));
        all.add(skill(SkillIds.PURE_STATE, capability(STATUS_RESISTANCE, 1))
                .condition("New harmful-effect immunity including source-less effects; preserves existing effects and beneficial/neutral applications; exclusive with status reflection; binary capability has no honest numeric rank consumer"));
        all.add(skill(SkillIds.RIPOSTE, flat(BURST_DAMAGE, .7), flat(REACH, .3))
                .deliveries(Delivery.MELEE).equipment(Equipment.SHIELD, Equipment.MELEE_WEAPON)
                .actions(Action.PERFECT_BLOCK, Action.ATTACK).availability(.25, .6).risk(.25)
                .condition("Native-ready perfect block arms one timed primary melee counter; accepted positive hit consumes; bounded attack-only reach and protection, ordinary cooldown; combines with Stored Force"));
    }

    private static void vitality(List<Builder> all) {
        all.add(skill(SkillIds.RISING_RECOVERY, multi(REGENERATION, .6))
                .availability(.5, .9).risk(.25).condition("Eligible vanilla natural regeneration keeps its native food, exhaustion, difficulty and gamerule checks; the generated missing-health power curve also multiplies the existing Nexus passive health-regeneration bonus, while other healing remains unchanged"));
        all.add(skill(SkillIds.LIFE_STEAL, flat(HEALING, .6), flat(SUSTAINED_SURVIVAL, .3))
                .attacks().actions(Action.REPEATED_HIT, Action.SAME_TARGET).availability(.65, .9).risk(.15)
                .condition("Actual accepted positive direct weapon damage only; generated same-target chain, inactivity/miss and lifecycle resets; missing-health cap; reflected, secondary, status and recursive proc damage excluded"));
        all.add(skill(SkillIds.FEAST_REFLEX, flat(RECOVERY, .5), flat(CONVENIENCE, .3))
                .actions(Action.EAT, Action.DRINK).availability(.5, .85).cost(.25)
                .condition("Native food/drink duration multiplier; while hurt and below vanilla natural regeneration, successive nonharmful nutritious hotbar meals restore food without requiring a single serving to fill the gap; effective Metabolic Conversion allows meals through full hunger until health is full; at full health, food is consumed only when less than half its nutrition would be wasted; no active-use interruption or repeated event"));
        all.add(skill(SkillIds.INNER_SUSTENANCE, flat(RESOURCE_CONSUMPTION, .45), capability(CONVENIENCE, .5))
                .actions(Action.REST).availability(.65, 1)
                .condition("Generated food/saturation increments only outside accepted hostile combat; full native food/saturation protects passive activity exhaustion; explicit costs persist; optional sleep and owner-only phantom pressure immunity are binary capabilities"));
        all.add(skill(SkillIds.HUNGER_WARD, flat(EFFECTIVE_HEALTH, .6))
                .actions(Action.TAKE_DAMAGE).availability(.75, 1).cost(.35)
                .condition("Only a generated share of each hit spends saturation and food; the rest reaches health, and unpayable redirected damage returns to health; finite reservoir and share-limited survival"));
        all.add(skill(SkillIds.STAGGERED_PAIN, flat(BURST_SURVIVAL, .65))
                .actions(Action.TAKE_DAMAGE).timing(0, 10)
                .condition("Ten-second damage delay redistributes damage; does not itself reduce total damage"));
        all.add(skill(SkillIds.DAMAGE_CEILING, multi(EFFECTIVE_HEALTH, .75))
                .actions(Action.TAKE_DAMAGE).availability(1, 1).risk(.45).timing(0, 10)
                .condition("Every post-mitigation/absorption hit takes one generated fraction of incoming health damage; that identical fraction of prevented damage removes temporary maximum-health capacity; no hit-size threshold, conversion bank or delayed damage; native capacity minimum and surviving HP bound only the cost; capacity returns empty after the generated quiet-combat duration"));
        all.add(skill(SkillIds.METABOLIC_CONVERSION, flat(CONVERSION, .5), flat(HEALING, .3))
                .actions(Action.EAT).availability(.45, .9).cost(.2)
                .condition("Healing overflow and full-hunger food convert resources, not duplicate them"));
        all.add(skill(SkillIds.PAIN_PURGE, flat(SUSTAINED_SURVIVAL, 1))
                .actions(Action.EAT, Action.DRINK, Action.REST, Action.ATTACK).availability(.45, .85).cost(.3)
                .condition("Accepted healing also cancels delayed damage at a generated ratio up to one-for-one, including at full HP; native food regeneration, passive regeneration, direct life steal and healing effects share one observer; debt recovery is not another healing event"));
        all.add(skill(SkillIds.ADRENALINE, multi(GROUND_SPEED, .3), multi(ATTACK_RATE, .35))
                .attacks().actions(Action.TAKE_DAMAGE).availability(.25, .8).risk(.5).timing(0, 5)
                .condition("A single accepted hit that actually removes more than the generated quarter-health target of pre-hit current maximum HP grants one refreshable timed buff; excludes absorption, prevented damage, max-HP cost and deferred payments; no lethal-save requirement"));
        all.add(skill(SkillIds.DEEP_WARD, multi(EFFECTIVE_HEALTH, .55))
                .actions(Action.KILL).availability(.6, .8)
                .condition("Extends Soul Ward capacity and combat retention; decay resumes out of combat"));
        all.add(skill(SkillIds.SOUL_WARD, flat(EFFECTIVE_HEALTH, .55))
                .actions(Action.KILL).availability(.55, .8)
                .condition("Kills build capped temporary ward hearts using victim vitality"));
        all.add(skill(SkillIds.SHATTERING_WARD, area(CROWD_CONTROL, .4, 3), flat(REGENERATION, .45))
                .actions(Action.TAKE_DAMAGE).availability(.3, .8).area(4)
                .condition("Final Soul Ward heart must break; no reward per absorbed damage tick"));
        all.add(skill(SkillIds.SECOND_WIND, capability(BURST_SURVIVAL, 1), flat(REGENERATION, .4))
                .actions(Action.LETHAL_DAMAGE).availability(.15, 1).timing(120, 0).risk(.5)
                .condition("Lethal interception with cooldown; recovery is not permanent invulnerability"));
        all.add(skill(SkillIds.SPIRIT_WALK, capability(BURST_SURVIVAL, 1), capability(CONVENIENCE, .4))
                .actions(Action.LETHAL_DAMAGE).availability(.15, 1).timing(120, 0).risk(.5)
                .condition("Temporary intangible escape prohibits dealing damage before safe reform"));
    }

    private static void mobility(List<Builder> all) {
        all.add(skill(SkillIds.RUNNING_MOMENTUM, multi(GROUND_SPEED, .5))
                .actions(Action.SPRINT).availability(.7, .9).risk(.1)
                .condition("Continuous sprinting; sharp turns and stopping lose accumulated speed"));
        all.add(skill(SkillIds.MOMENTUM_VAULT, capability(CONVENIENCE, .5), flat(VERTICAL_MOVEMENT, .25))
                .actions(Action.SPRINT).availability(.6, .85).condition("Native step-height floor over short collision obstacles during a charged sprint; no jump impulse or airborne lift"));
        all.add(skill(SkillIds.RUSH, flat(CONVENIENCE, .4))
                .actions(Action.KILL, Action.SPRINT).availability(.3, .75)
                .condition("Attributed kills fill existing Running Momentum and retain it; no second speed multiplier or invented kill rate"));
        all.add(skill(SkillIds.TERRAIN_FREEDOM, capability(CONVENIENCE, .55), capability(STATUS_RESISTANCE, .3))
                .actions(Action.MOVE).availability(.5, 1).condition("Terrain slow, contact, freezing and sinking hazards only"));
        all.add(skill(SkillIds.AQUATIC_BODY, capability(MINING_SPEED, .35), capability(INFORMATION, .25))
                .actions(Action.SWIM).availability(.4, 1).condition("Binary restoration of native underwater mining efficiency and visibility only; swimming speed remains with bonuses/equipment, with no movement-efficiency floor or off-ground movement bypass; no breathing immunity"));
        all.add(skill(SkillIds.WATER_WALKING, capability(CONVENIENCE, .55))
                .actions(Action.SPRINT, Action.SNEAK).availability(.4, 1).condition("Exposed native liquid-surface collision while sprinting; native entry/stepping, never an immersed lift. Sneak or stopping resumes ordinary swimming"));
        all.add(skill(SkillIds.LAVABORN, capability(STATUS_RESISTANCE, .8), capability(GROUND_SPEED, .4))
                .actions(Action.SWIM).availability(.3, 1).condition("Lava-immersion protection and inside-out visibility; native swimming access uses actual bonus/equipment speed. Lava ignition is prevented at its source; unrelated fire and damage remain normal outside immersion"));
        all.add(skill(SkillIds.IMPACT_CONTROL, flat(FALL_CONTROL, .65))
                .actions(Action.MOVE).availability(.6, 1).condition("Generated fall/tagged movement-impact reduction; reuse player/equipment Fall Resistance once, transfer native fall multiplier to non-fall collisions only, never general damage immunity"));
        all.add(skill(SkillIds.CHARGED_JUMP, flat(JUMP, .7), flat(VERTICAL_MOVEMENT, .5))
                .actions(Action.JUMP).availability(.55, 1).risk(.15).condition("Elapsed server-tick charge on native solid support; release ordinary Jump for a movement-key-steered native leap; cancelled input never auto-launches"));
        all.add(skill(SkillIds.DOUBLE_JUMP, capability(JUMP, .65), flat(FALL_CONTROL, .3))
                .actions(Action.JUMP).availability(.75, 1).condition("One fresh ordinary Jump press spends a directional air jump; native landing resets the resource, never holding the key or changing skill in midair"));
        all.add(skill(SkillIds.VECTOR_JUMP, capability(JUMP, .85), flat(VERTICAL_MOVEMENT, .55), flat(FALL_CONTROL, .4))
                .actions(Action.JUMP).availability(.75, 1).condition("Replaces Double Jump with one look-directed native impulse; upward climbs, forward accelerates, downward brakes without a downward kick"));
        all.add(skill(SkillIds.ESSENCE_WINGS, capability(GLIDING, 1))
                .actions(Action.JUMP, Action.GLIDE).availability(.85, 1)
                .condition("Elytra milestone and replacement of Fatigue Flight; equipment-free gliding"));
        all.add(skill(SkillIds.FATIGUE_FLIGHT, capability(FLIGHT, .6), flat(VERTICAL_MOVEMENT, .5))
                .actions(Action.JUMP).availability(.45, 1).cost(.35)
                .condition("Flight stamina and grounded recharge limit sustained airborne use"));
        all.add(skill(SkillIds.VECTOR_BOOST, flat(GROUND_SPEED, .5), flat(VERTICAL_MOVEMENT, .4))
                .actions(Action.GLIDE, Action.JUMP).availability(.45, 1).cost(.2)
                .condition("Wings plus Vector Jump; rechargeable glide impulse replaces firework consumption"));
        all.add(skill(SkillIds.UNTETHERED_FLIGHT, capability(FLIGHT, 1), capability(VERTICAL_MOVEMENT, .8))
                .actions(Action.JUMP).condition("Elytra milestone; replaces Fatigue Flight with sustained flight and air recharge"));
    }

    private static void gathering(List<Builder> all) {
        all.add(skill(SkillIds.TOOL_INSTINCT, capability(TOOL_VERSATILITY, .6), multi(MINING_SPEED, .3))
                .equipment(Equipment.ASCENDANCE_TOOL).actions(Action.MINE).availability(.85, 1)
                .condition("Appropriate carried Ascendance tool and harvest tier; no free harvest-level bypass"));
        all.add(skill(SkillIds.MINING_MOMENTUM, multi(MINING_SPEED, .6))
                .equipment(Equipment.ASCENDANCE_TOOL).actions(Action.MINE).availability(.7, .9)
                .condition("Uninterrupted mining rhythm, especially repeated material"));
        all.add(skill(SkillIds.NATURES_BOON, flat(DROP_YIELD, .65), capability(AUTOMATION_INTERACTION, .7))
                .equipment(Equipment.ASCENDANCE_TOOL).actions(Action.MINE).availability(.45, .7)
                .condition("Matching natural dimension stone can generate local ores; requires economy conservation analysis"));
        all.add(skill(SkillIds.ORE_SIGHT, capability(INFORMATION, .65))
                .equipment(Equipment.PICKAXE).actions(Action.MINE).availability(.7, 1).range(12)
                .condition("Natural nearby ores, weighted deposit visibility, holding a pickaxe"));
        all.add(skill(SkillIds.TREASURE_SENSE, capability(INFORMATION, .65))
                .availability(.5, .9).range(12).condition("Nearby unopened natural loot containers only"));
        all.add(skill(SkillIds.HUNTERS_STUDY, multi(DROP_YIELD, .6))
                .actions(Action.KILL).availability(.65, .9).condition("Repeated creature type kills improve ordinary drops; scalable farming exposure"));
        all.add(skill(SkillIds.ESSENCE_BLOOM, flat(CONVERSION, .45), flat(EXPERIENCE, .4))
                .actions(Action.KILL).availability(.6, .8).condition("Bonus Essence and experience on kills; mutually exclusive with ordinary-drop improvement"));
        all.add(skill(SkillIds.TORCHBEARER, flat(CONVENIENCE, .45))
                .equipment(Equipment.ASCENDANCE_TOOL).actions(Action.MINE).availability(.5, .9).cost(.2)
                .condition("Dark mining locations consume a carried hotbar torch"));
        all.add(skill(SkillIds.VERDANT_STRIDE, flat(CROP_YIELD, .55), capability(CONVENIENCE, .25))
                .actions(Action.MOVE).availability(.6, .9).area(4)
                .condition("Nearby crop growth and trampling prevention; farming throughput interaction"));
        all.add(skill(SkillIds.HERDKEEPER, flat(THROUGHPUT, .5))
                .actions(Action.BREED).availability(.5, .9).area(8).condition("Livestock proximity and breeding recovery"));
        all.add(skill(SkillIds.ANIMAL_GIFT, flat(DROP_YIELD, .5), capability(AUTOMATION_INTERACTION, .65))
                .actions(Action.BREED).availability(.45, .8).cost(.15).area(8)
                .condition("Well-fed adult livestock provide renewable products; proximity and feeding cost"));
        all.add(skill(SkillIds.FISHING_INSTINCT, multi(THROUGHPUT, .45), flat(DROP_YIELD, .35))
                .equipment(Equipment.FISHING_ROD).actions(Action.FISH).availability(.8, .9)
                .condition("Bite time, reel-window reliability and treasure quality while actively fishing"));
        all.add(skill(SkillIds.FISHERS_CALL, multi(THROUGHPUT, .45), flat(DROP_YIELD, .3))
                .equipment(Equipment.FISHING_ROD).actions(Action.FISH).availability(.55, .8)
                .condition("Consecutive catches build streak; a miss resets it; bounded extra-catch chance"));
        all.add(skill(SkillIds.SALVAGERS_CRAFT, flat(CONVERSION, .55), flat(EXPERIENCE, .3))
                .actions(Action.REPAIR).availability(.45, 1).cost(.4)
                .condition("Grindstone destroys unwanted equipment for partial material and enchantment-XP recovery"));
        all.add(skill(SkillIds.POCKET_NETS, flat(DROP_YIELD, .4))
                .actions(Action.SWIM).availability(.3, .6).condition("Rare passive swimming fishing drop; resource-source budget required"));
    }

    private static void utility(List<Builder> all) {
        all.add(skill(SkillIds.THREAT_SENSE, capability(INFORMATION, .55))
                .availability(.8, .95).range(24).condition("Threat targets, approaching projectile paths and active explosion danger"));
        all.add(skill(SkillIds.HUNTERS_LEDGER, flat(INFORMATION, .4))
                .availability(.8, .95).condition("Threat Sense extension adds persistent outline and observed health/armor information"));
        all.add(skill(SkillIds.WAYLIGHT, capability(INFORMATION, .5), flat(CONVENIENCE, .2))
                .availability(.7, 1).area(8).condition("Personal visibility and local spawnable-block guidance"));
        all.add(skill(SkillIds.RESTFUL_MENDING, flat(REPAIR, .55))
                .equipment(Equipment.REPAIRABLE_GEAR).actions(Action.REST).availability(.55, 1)
                .condition("Ascendance gear must remain unused; repair pauses on use"));
        all.add(skill(SkillIds.DURABILITY_REVERSAL, flat(DURABILITY, .55))
                .equipment(Equipment.REPAIRABLE_GEAR).availability(.8, 1)
                .condition("Durability loss builds charge for a later reversal; bounded net durability gain"));
        all.add(skill(SkillIds.TEMPERED_REPAIR, flat(DURABILITY, .45), flat(REPAIR, .2))
                .equipment(Equipment.REPAIRABLE_GEAR).actions(Action.REPAIR).availability(.6, 1).cost(.2)
                .condition("Anvil or smithing repair grants a finite consumable temper buffer"));
        all.add(skill(SkillIds.VILLAGE_PATRON, flat(CONVERSION, .45), flat(THROUGHPUT, .35))
                .actions(Action.TRADE).availability(.55, 1).condition("Hero-of-the-village milestone; discounts and local trade restock"));
        all.add(skill(SkillIds.BONDED_COMPANION, flat(SUSTAINED_DAMAGE, .35), capability(TELEPORTATION, .4))
                .availability(.55, .8).condition("Milestone-gated allied creature stats and safe companion catch-up; not player teleportation"));
        all.add(skill(SkillIds.POTION_DURATION, flat(RESOURCE_CONSUMPTION, .5))
                .actions(Action.DRINK).availability(.55, 1).cost(.2).condition("Milestone-gated compatible beneficial potion duration; long effects diminish"));
        all.add(skill(SkillIds.POTION_RELAY, area(CONVENIENCE, .45, 3))
                .actions(Action.DRINK).availability(.4, .85).cost(.2).area(8)
                .condition("Shares reduced beneficial effect duration with nearby allies; solo target count is one"));
        all.add(skill(SkillIds.SANCTUARY, capability(CONVENIENCE, .8))
                .availability(.8, 1).area(16).condition("Enabled toggle, beacon milestone; prevents nearby natural hostile spawns outside combat"));
        all.add(skill(SkillIds.INDUSTRIOUS_PRESENCE, multi(THROUGHPUT, .65), capability(AUTOMATION_INTERACTION, .6))
                .actions(Action.PROCESS).availability(.65, 1).area(8)
                .condition("Enabled proximity processing toggle; finite loaded block work and economy throughput budget"));
        all.add(skill(SkillIds.CONTAINMENT_FIELD, capability(CONVENIENCE, .65), flat(BURST_SURVIVAL, .45))
                .availability(.4, 1).area(8).condition("Enabled toggle; local explosion protection preserves normal damage to other creatures"));
        all.add(skill(SkillIds.FRIENDLY_FIRE_WARD, capability(CONVENIENCE, .5))
                .attacks().actions(Action.ATTACK).availability(.55, 1).condition("Allied players and tames protected from melee, projectile and ability friendly fire"));
        all.add(skill(SkillIds.ENCHANTING_INSIGHT, capability(INFORMATION, .6), flat(ENCHANTING_EFFICIENCY, .3))
                .actions(Action.ENCHANT).availability(.6, 1)
                .condition("Enchanter milestone; full preview and stable offers until inserted item changes"));
    }

    private static Contribution flat(CapabilityAxis axis, double weight) { return new Contribution(axis, Form.FLAT, weight, 1); }
    private static Contribution multi(CapabilityAxis axis, double weight) { return new Contribution(axis, Form.MULTIPLIER, weight, 1); }
    private static Contribution capability(CapabilityAxis axis, double weight) { return new Contribution(axis, Form.CAPABILITY, weight, 1); }
    private static Contribution area(CapabilityAxis axis, double weight, int targets) { return new Contribution(axis, Form.FLAT, weight, targets); }
    private static Builder skill(ResourceLocation id, Contribution... contributions) { return new Builder(id, List.of(contributions)); }
    private static void unit(double number) {
        if (!Double.isFinite(number) || number < 0 || number > 1) throw new IllegalArgumentException("Expected a fraction in [0,1]");
    }

    private static final class Builder {
        private final ResourceLocation id;
        private final List<Contribution> contributions;
        private Set<Delivery> deliveries = Set.of();
        private Set<Equipment> equipment = Set.of();
        private Set<Action> actions = Set.of();
        private double uptime = 1, reliability = 1, cooldown, duration, range, area, cost, risk;
        private String condition;
        Builder(ResourceLocation id, List<Contribution> contributions) { this.id = id; this.contributions = contributions; }
        Builder attacks() { deliveries = Set.of(Delivery.values()); return this; }
        Builder projectiles() { deliveries = Set.of(Delivery.RANGED, Delivery.CASTER); return this; }
        Builder deliveries(Delivery... values) { deliveries = Set.of(values); return this; }
        Builder equipment(Equipment... values) { equipment = Set.of(values); return this; }
        Builder actions(Action... values) { actions = Set.of(values); return this; }
        Builder availability(double uptime, double reliability) { this.uptime = uptime; this.reliability = reliability; return this; }
        Builder timing(double cooldown, double duration) { this.cooldown = cooldown; this.duration = duration; return this; }
        Builder range(double value) { range = value; return this; }
        Builder area(double value) { area = value; return this; }
        Builder cost(double value) { cost = value; return this; }
        Builder risk(double value) { risk = value; return this; }
        Builder condition(String value) { condition = value; return this; }
        Descriptor build() {
            return new Descriptor(id, contributions, deliveries, equipment, actions, uptime, reliability,
                    cooldown, duration, range, area, cost, risk, condition, .45,
                    "Registered catalog semantics; normalized policy weights and representative trigger estimates, not measured gameplay");
        }
    }
}
