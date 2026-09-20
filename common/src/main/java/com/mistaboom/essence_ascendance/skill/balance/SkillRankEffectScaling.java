package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.config.MobilityBalanceSettings;
import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.config.UtilityBalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.config.GuardBalanceSettings;
import com.mistaboom.essence_ascendance.config.PostureBalanceSettings;
import com.mistaboom.essence_ascendance.config.StatusBalanceSettings;
import com.mistaboom.essence_ascendance.config.VitalityBalanceSettings;
import com.mistaboom.essence_ascendance.config.VitalityDamageBalanceSettings;
import com.mistaboom.essence_ascendance.config.VitalityWardBalanceSettings;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Typed parameter consumers, evaluated only after profile or effective-rank changes. */
public final class SkillRankEffectScaling {
    private static final Map<ResourceLocation, BiConsumer<Parameters, Double>> RULES = new LinkedHashMap<>();
    static {
        register(SkillIds.FRENZY, (p, f) -> {
            var v = p.frenzy;
            p.frenzy = new SkillEffectBalanceSettings.Frenzy(v.maxStacks(), v.chainTimeoutTicks(),
                    scale(v.damageBonusPercentPerStack(), f, 100), scale(v.attackSpeedBonusPercentPerStack(), f, 100));
        });
        register(SkillIds.ARMOR_CRACK, (p, f) -> {
            var v = p.armorCrack;
            p.armorCrack = new SkillEffectBalanceSettings.ArmorCrack(v.maxStacks(), v.stackTimeoutTicks(),
                    scale(v.armorReductionPerStack(), f, 1024), scale(v.toughnessReductionPerStack(), f, 1024));
        });
        register(SkillIds.DESPERATION, (p, f) -> p.desperation = new SkillEffectBalanceSettings.Desperation(
                scale(p.desperation.maxDamageBonusPercent(), f, 1000)));
        register(SkillIds.DEATH_RUSH, (p, f) -> {
            var v = p.deathRush;
            p.deathRush = new SkillEffectBalanceSettings.DeathRush(v.killHealthThreshold(), v.nearDeathHealthThreshold(),
                    v.maxStacks(), v.stackDurationTicks(), scale(v.attackSpeedBonusPercentPerStack(), f, 100),
                    scale(v.bowDrawSpeedBonusPercentPerStack(), f, 100), scale(v.castSpeedBonusPercentPerStack(), f, 100));
        });
        register(SkillIds.KINDLING, (p, f) -> {
            var v = p.kindling;
            p.kindling = new SkillEffectBalanceSettings.Kindling(v.maxHeat(), v.heatPerHit(), v.heatExpiryTicks(),
                    v.ignitionDurationTicks(), scale(v.burningDamagePercentPerSecond(), f, 1000),
                    scale(v.burningDamageAmplificationPercent(), f, 1000));
        });
        register(SkillIds.COMBUSTION, (p, f) -> {
            var v = p.combustion;
            p.combustion = new SkillEffectBalanceSettings.Combustion(scale(v.damage(), f, 1024), v.radius(),
                    v.targetsPerBurst(), v.maximumGeneration(), v.rootTargetBudget(), v.generationDamageFalloff(), v.seededIgnitionTicks());
        });
        register(SkillIds.FROSTBITE, (p, f) -> {
            var v = p.frostbite;
            p.frostbite = new SkillEffectBalanceSettings.Frostbite(v.maxChill(), v.chillPerHit(), v.chillExpiryTicks(),
                    v.slowPerStack(), v.maximumProgressiveSlow(), (int) scale(v.freezeDurationTicks(), f, 72000), v.frozenSlow());
        });
        register(SkillIds.SHATTER, (p, f) -> {
            var v = p.shatter;
            p.shatter = new SkillEffectBalanceSettings.Shatter(scale(v.shardDamage(), f, 1024), v.radius(), v.maximumTargets());
        });
        register(SkillIds.STATIC_CHARGE, (p, f) -> {
            var v = p.staticCharge;
            p.staticCharge = new SkillEffectBalanceSettings.StaticCharge(v.maximumCharge(), v.sprintPerTick(), v.jumpCharge(),
                    v.fallPerTick(), v.flightPerTick(), v.glidePerTick(), v.idleGraceTicks(), v.decayPerTick(),
                    scale(v.lightningDamage(), f, 1024));
        });
        register(SkillIds.CHAIN_STRIKE, (p, f) -> {
            var v = p.chainStrike;
            p.chainStrike = new SkillEffectBalanceSettings.ChainStrike(v.maximumJumps(), v.radius(), retained(v.damageFalloff(), f));
        });
        register(SkillIds.HOMING_PROJECTILE, (p, f) -> {
            var v = p.projectiles;
            p.projectiles = projectile(v, homing(v.arrow(), f), homing(v.caster(), f),
                    v.ricochetDamageMultiplier(), v.piercingDamageMultiplier(), v.piercingShieldDamageMultiplier());
        });
        register(SkillIds.RICOCHET, (p, f) -> {
            var v = p.projectiles;
            p.projectiles = projectile(v, v.arrow(), v.caster(), retained(v.ricochetDamageMultiplier(), f),
                    v.piercingDamageMultiplier(), v.piercingShieldDamageMultiplier());
        });
        register(SkillIds.PIERCING_PROJECTILE, (p, f) -> {
            var v = p.projectiles;
            p.projectiles = projectile(v, v.arrow(), v.caster(), v.ricochetDamageMultiplier(),
                    retained(v.piercingDamageMultiplier(), f), retained(v.piercingShieldDamageMultiplier(), f));
        });
        register(SkillIds.EXPLOSIVE_PAYLOAD, (p, f) -> {
            var v = p.projectiles.payload();
            p.projectiles = withPayload(p.projectiles, new ProjectileBalanceSettings.Payload(v.triggerBudget(),
                    v.explosiveRadius(), scale(v.explosiveDamageScale(), f, 4), v.rootDurationTicks(),
                    v.rootMaxDurationTicks(), v.rootMovementTolerance(), v.particleCount()));
        });
        register(SkillIds.ROOTING_PAYLOAD, (p, f) -> {
            var v = p.projectiles.payload();
            p.projectiles = withPayload(p.projectiles, new ProjectileBalanceSettings.Payload(v.triggerBudget(),
                    v.explosiveRadius(), v.explosiveDamageScale(), (int) scale(v.rootDurationTicks(), f, 200),
                    (int) scale(v.rootMaxDurationTicks(), f, 400), v.rootMovementTolerance(), v.particleCount()));
        });
        register(SkillIds.PROJECTILE_DRAG_FIELD, (p, f) -> {
            var v = p.projectiles.control();
            p.projectiles = withControl(p.projectiles, new ProjectileBalanceSettings.Control(v.outerRadius(), v.innerRadius(),
                    Math.max(.05, v.minimumSpeedFactor() / f), v.responseExponent(), v.scanCadenceTicks(),
                    v.swingRange(), v.swingRadius(), v.swingHalfAngleDegrees(), v.readinessThreshold(), v.swingBudget(),
                    v.theftSpeedMultiplier(), v.theftTargetRange(), v.theftAimConeDegrees(), v.theftTurnDegreesPerTick(), v.redirectBudget()));
        });
        register(SkillIds.INTERCEPTOR, (p, f) -> {
            var v = p.projectiles.control();
            p.projectiles = withControl(p.projectiles, new ProjectileBalanceSettings.Control(v.outerRadius(), v.innerRadius(),
                    v.minimumSpeedFactor(), v.responseExponent(), v.scanCadenceTicks(), v.swingRange(),
                    scale(v.swingRadius(), f, 2), v.swingHalfAngleDegrees(), v.readinessThreshold(), v.swingBudget(),
                    v.theftSpeedMultiplier(), v.theftTargetRange(), v.theftAimConeDegrees(), v.theftTurnDegreesPerTick(), v.redirectBudget()));
        });
        register(SkillIds.TRAJECTORY_THEFT, (p, f) -> {
            var v = p.projectiles.control();
            p.projectiles = withControl(p.projectiles, new ProjectileBalanceSettings.Control(v.outerRadius(), v.innerRadius(),
                    v.minimumSpeedFactor(), v.responseExponent(), v.scanCadenceTicks(), v.swingRange(), v.swingRadius(),
                    v.swingHalfAngleDegrees(), v.readinessThreshold(), v.swingBudget(), scale(v.theftSpeedMultiplier(), f, 2),
                    v.theftTargetRange(), v.theftAimConeDegrees(), scale(v.theftTurnDegreesPerTick(), f, 45), v.redirectBudget()));
        });
        register(SkillIds.GUARDED_ADVANCE, (p, f) -> p.mobility = new GuardBalanceSettings.Mobility(
                retained(p.mobility.slowdownRemoval(), f), p.mobility.stepHeight()));
        register(SkillIds.SHIELD_RAM, (p, f) -> {
            var v = p.ram;
            p.ram = new GuardBalanceSettings.Ram(v.minimumSpeed(), v.maximumSweep(), v.staggerTicks(),
                    v.staggerMovementMultiplier(), scale(v.knockback(), f, 2), v.contactLimit(), v.repeatCooldownTicks());
        });
        register(SkillIds.REFLEXIVE_WARD, (p, f) -> {
            var v = p.ward;
            p.ward = new GuardBalanceSettings.Ward(scale(v.preventedReflectionScale(), f, 1),
                    scale(v.knockbackEchoScale(), f, 2), v.knockbackEchoCap());
        });
        register(SkillIds.STORED_FORCE, (p, f) -> {
            var v = p.storedForce;
            p.storedForce = new GuardBalanceSettings.StoredForce(v.conversion(), v.capacity(), v.durationTicks(),
                    scale(v.damageScale(), f, 4), v.knockbackScale());
        });
        register(SkillIds.GUARD_AMPLIFIER, (p, f) -> {
            var v = p.amplifier;
            double maximum = 1 + scale(v.maximumMultiplier() - 1, f, 3);
            p.amplifier = new GuardBalanceSettings.Amplifier(scale(v.perBlockGrowth(), f, maximum - 1),
                    maximum, v.durationTicks());
        });
        register(SkillIds.CROWD_REPRISAL, (p, f) -> {
            var v = p.reprisal;
            p.reprisal = new GuardBalanceSettings.Reprisal(v.radius(), scale(v.damageScale(), f, 1),
                    v.maximumTargets(), v.particleCount());
        });
        register(SkillIds.RIPOSTE, (p, f) -> {
            var v = p.riposte;
            p.riposte = new GuardBalanceSettings.Riposte(v.durationTicks(), scale(v.bonusReach(), f, 2),
                    scale(v.damageScale(), f, 4), v.protectionTicks());
        });
        register(SkillIds.EVASIVE_CURRENT, (p, f) -> {
            var v = p.evasive;
            p.evasive = new PostureBalanceSettings.Evasive(v.buildTicks(), v.drainTicks(),
                    scale(v.maximumDodgeChance(), f, .75), v.successDrainFraction(), v.hitDrainFraction());
        });
        register(SkillIds.BULWARK_STANCE, (p, f) -> {
            var v = p.bulwark;
            p.bulwark = new PostureBalanceSettings.Bulwark(v.buildTicks(), v.drainTicks(), v.threatRange(),
                    v.facingDegrees(), scale(v.maximumResistance(), f, .75), v.knockbackThreshold(), v.maximumThreats());
        });
        register(SkillIds.ADAPTIVE_GUARD, (p, f) -> {
            var v = p.adaptive;
            p.adaptive = new PostureBalanceSettings.Adaptive(v.windowTicks(), v.maximumStacks(), v.minimumHits(),
                    scale(v.resistancePerStack(), f, .75 / Math.max(1, v.maximumStacks() - v.minimumHits() + 1)));
        });
        register(SkillIds.STATUS_MIRROR, (p, f) -> {
            var v = p.status;
            p.status = new StatusBalanceSettings(Math.max(20, (int) Math.ceil(v.mirrorCooldownTicks() / f)),
                    v.mirrorMaximumDurationTicks(), v.mirrorMaximumAmplifier());
        });
        register(SkillIds.RISING_RECOVERY, (p, f) -> p.risingRecovery = new VitalityBalanceSettings.RisingRecovery(
                scale(p.risingRecovery.maxSpeedBonus(), f, 9), p.risingRecovery.recoveryCurveExponent()));
        register(SkillIds.LIFE_STEAL, (p, f) -> {
            var v = p.lifeSteal;
            double bounded = Math.min(f, 1 / Math.max(.000001, v.baseHealingFraction() + (v.maxChainHits() - 1) * v.perHitHealingFraction()));
            p.lifeSteal = new VitalityBalanceSettings.LifeSteal(v.baseHealingFraction() * bounded,
                    v.perHitHealingFraction() * bounded, v.maxChainHits(), v.chainTimeoutTicks());
        });
        register(SkillIds.FEAST_REFLEX, (p, f) -> p.feastReflex = new VitalityBalanceSettings.FeastReflex(
                Math.max(.05, p.feastReflex.useDurationMultiplier() / f)));
        register(SkillIds.INNER_SUSTENANCE, (p, f) -> {
            var v = p.innerSustenance;
            p.innerSustenance = new VitalityBalanceSettings.InnerSustenance(v.combatTimeoutTicks(),
                    Math.max(20, (int) Math.ceil(v.hungerRecoveryIntervalTicks() / f)), v.hungerPerRecovery(), v.saturationPerRecovery());
        });
        register(SkillIds.SOUL_WARD, (p, f) -> {
            var v = p.soulWard;
            p.soulWard = new VitalityWardBalanceSettings.SoulWard(scale(v.victimHealthFraction(), f, 1),
                    scale(v.capacityHealthFraction(), f, 1024), v.durationTicks());
        });
        register(SkillIds.DEEP_WARD, (p, f) -> {
            var v = p.deepWard;
            p.deepWard = new VitalityWardBalanceSettings.DeepWard(scale(v.capacityBonusFraction(), f, 1024),
                    v.combatTimeoutTicks(), v.decayTicks());
        });
        register(SkillIds.SHATTERING_WARD, (p, f) -> {
            var v = p.shatteringWard;
            p.shatteringWard = new VitalityWardBalanceSettings.ShatteringWard(v.radius(), v.maximumTargets(),
                    scale(v.knockback(), f, 1024), scale(v.healingFractionPerSecond(), f, 1024), v.regenerationTicks());
        });
        register(SkillIds.HUNGER_WARD, (p, f) -> p.hungerWard = new VitalityDamageBalanceSettings.HungerWard(
                p.hungerWard.healthPerFoodPoint(),
                com.mistaboom.essence_ascendance.vitality.DamageRoutingMath.scaleShare(p.hungerWard.damageShare(), f)));
        register(SkillIds.DAMAGE_CEILING, (p, f) -> {
            var v = p.damageCeiling;
            p.damageCeiling = new VitalityDamageBalanceSettings.DamageCeiling(
                    com.mistaboom.essence_ascendance.vitality.DamageRoutingMath.scaleTakenFraction(v.damageTakenFraction(), f),
                    v.combatTimeoutTicks());
        });
        register(SkillIds.METABOLIC_CONVERSION, (p, f) -> {
            var v = p.metabolicConversion;
            double product = v.foodPointsPerOverflowHealth() * v.healthPerNutrition();
            double bounded = product > 0 ? Math.min(f, Math.sqrt(Math.nextDown(1.0) / product)) : f;
            double food = Math.min(1024, v.foodPointsPerOverflowHealth() * bounded);
            double health = Math.min(1024, v.healthPerNutrition() * bounded);
            if (food * health >= 1) health = Math.nextDown(Math.nextDown(1.0) / food);
            p.metabolicConversion = new VitalityDamageBalanceSettings.MetabolicConversion(food, health);
        });
        register(SkillIds.PAIN_PURGE, (p, f) -> p.painPurge = new VitalityDamageBalanceSettings.PainPurge(
                scale(p.painPurge.queuePerHealing(), f, 1)));
        register(SkillIds.ADRENALINE, (p, f) -> {
            var v = p.adrenaline;
            p.adrenaline = new VitalityDamageBalanceSettings.Adrenaline(
                    v.triggerHealthLossFraction(), v.durationTicks(), scale(v.movementSpeedBonus(), f, 10), scale(v.attackSpeedBonus(), f, 10),
                    scale(v.knockbackResistance(), f, 1));
        });
        register(SkillIds.RUNNING_MOMENTUM, (p, f) -> {
            var v = p.runningMomentum;
            p.runningMomentum = new MobilityBalanceSettings.RunningMomentum(scale(v.maximumSpeedBonus(), f, 16),
                    v.buildTicks(), v.drainTicks(), v.sharpTurnDegrees());
        });
        register(SkillIds.MOMENTUM_VAULT, (p, f) -> p.momentumVault = new MobilityBalanceSettings.MomentumVault(
                p.momentumVault.stepHeight(), p.momentumVault.minimumMomentum() / f));
        register(SkillIds.RUSH, (p, f) -> p.rush = new MobilityBalanceSettings.Rush(
                (int) Math.ceil(scale(p.rush.durationTicks(), f, 72_000))));
        register(SkillIds.IMPACT_CONTROL, (p, f) -> p.impactControl = new MobilityBalanceSettings.ImpactControl(
                retained(p.impactControl.damageReduction(), f)));
        register(SkillIds.CHARGED_JUMP, (p, f) -> p.chargedJump = new MobilityBalanceSettings.ChargedJump(
                p.chargedJump.chargeTicks(), scale(p.chargedJump.heightBonus(), f, 64), scale(p.chargedJump.steeringBonus(), f, 64)));
        register(SkillIds.DOUBLE_JUMP, (p, f) -> p.doubleJump = new MobilityBalanceSettings.AirJump(
                scale(p.doubleJump.heightBonus(), f, 64), scale(p.doubleJump.steeringBonus(), f, 64)));
        register(SkillIds.VECTOR_JUMP, (p, f) -> p.vectorJump = new MobilityBalanceSettings.VectorJump(
                scale(p.vectorJump.impulseBonus(), f, 64), retained(p.vectorJump.brakeFraction(), f)));
        register(SkillIds.ESSENCE_WINGS, (p, f) -> p.essenceWings = new MobilityBalanceSettings.EssenceWings(
                scale(p.essenceWings.horizontalDragCompensation(), f, 1)));
        register(SkillIds.FATIGUE_FLIGHT, (p, f) -> p.fatigueFlight = new MobilityBalanceSettings.FatigueFlight(
                (int) Math.ceil(scale(p.fatigueFlight.enduranceTicks(), f, 72_000)),
                Math.max(1, (int) Math.ceil(p.fatigueFlight.groundRechargeTicks() / f)),
                1 + scale(p.fatigueFlight.thrustGravityMultiplier() - 1, f, 64)));
        register(SkillIds.VECTOR_BOOST, (p, f) -> p.vectorBoost = new MobilityBalanceSettings.VectorBoost(
                Math.max(1, (int) Math.ceil(p.vectorBoost.rechargeTicks() / f)), p.vectorBoost.rocketSpeedBonus()));
        register(SkillIds.UNTETHERED_FLIGHT, (p, f) -> p.untetheredFlight = new MobilityBalanceSettings.UntetheredFlight(
                Math.max(1, (int) Math.ceil(p.untetheredFlight.airRechargeTicks() / f))));
        register(SkillIds.TOOL_INSTINCT, (p, f) -> p.toolInstinct = new GatheringBalanceSettings.ToolInstinct(
                scale(p.toolInstinct.maximumLowerTierSpeedBonus(), f, 16)));
        register(SkillIds.MINING_MOMENTUM, (p, f) -> p.miningMomentum = new GatheringBalanceSettings.MiningMomentum(
                scale(p.miningMomentum.maximumSpeedBonus(), f, 16), p.miningMomentum.buildBreaks(),
                p.miningMomentum.chainTimeoutTicks(), p.miningMomentum.crossMaterialBuildFraction()));
        register(SkillIds.NATURES_BOON, (p, f) -> p.naturesBoon = new GatheringBalanceSettings.NaturesBoon(
                scaleOdds(p.naturesBoon.dropChance(), f)));
        register(SkillIds.ORE_SIGHT, (p, f) -> p.oreSight = new GatheringBalanceSettings.Survey(
                scale(p.oreSight.rangeBlocks(), f, 128)));
        register(SkillIds.TREASURE_SENSE, (p, f) -> p.treasureSense = new GatheringBalanceSettings.Survey(
                scale(p.treasureSense.rangeBlocks(), f, 128)));
        register(SkillIds.HUNTERS_STUDY, (p, f) -> p.huntersStudy = new GatheringBalanceSettings.HuntersStudy(
                p.huntersStudy.killsToFullStudy(), scale(p.huntersStudy.maximumVirtualLootingLevels(), f, 255)));
        register(SkillIds.ESSENCE_BLOOM, (p, f) -> p.essenceBloom = new GatheringBalanceSettings.EssenceBloom(
                scaleOdds(p.essenceBloom.triggerChance(), f),
                scale(p.essenceBloom.essencePerExperiencePoint(), f, 1_024),
                scale(p.essenceBloom.bonusExperienceFraction(), f, 16)));
        register(SkillIds.VERDANT_STRIDE, (p, f) -> p.verdantStride = new GatheringBalanceSettings.VerdantStride(
                p.verdantStride.radiusBlocks(), p.verdantStride.growthPulseTicks(),
                scaleOdds(p.verdantStride.growthChance(), f)));
        register(SkillIds.HERDKEEPER, (p, f) -> p.herdkeeper = new GatheringBalanceSettings.Herdkeeper(
                p.herdkeeper.radiusBlocks(), 1.0 + scale(p.herdkeeper.breedingRecoveryMultiplier() - 1.0, f, 127)));
        register(SkillIds.ANIMAL_GIFT, (p, f) -> p.animalGift = new GatheringBalanceSettings.AnimalGift(
                p.animalGift.radiusBlocks(), p.animalGift.giftPulseTicks(), scaleOdds(p.animalGift.giftChance(), f)));
        register(SkillIds.FISHING_INSTINCT, (p, f) -> p.fishingInstinct = new GatheringBalanceSettings.FishingInstinct(
                1.0 + scale(p.fishingInstinct.biteSpeedMultiplier() - 1.0, f, 127),
                1.0 + scale(p.fishingInstinct.reelWindowMultiplier() - 1.0, f, 127),
                scale(p.fishingInstinct.virtualLuckLevels(), f, 255)));
        register(SkillIds.FISHERS_CALL, (p, f) -> p.fishersCall = new GatheringBalanceSettings.FishersCall(
                p.fishersCall.catchesToFullShoal(), p.fishersCall.chainTimeoutTicks(),
                1.0 + scale(p.fishersCall.maximumBiteSpeedMultiplier() - 1.0, f, 127),
                scaleOdds(p.fishersCall.maximumExtraCatchChance(), f)));
        register(SkillIds.SALVAGERS_CRAFT, (p, f) -> p.salvagersCraft = new GatheringBalanceSettings.SalvagersCraft(
                scaleOdds(p.salvagersCraft.materialRecoveryFraction(), f),
                scaleOdds(p.salvagersCraft.bonusExperienceFraction(), f)));
        register(SkillIds.POCKET_NETS, (p, f) -> p.pocketNets = new GatheringBalanceSettings.PocketNets(
                p.pocketNets.pulseTicks(), scaleOdds(p.pocketNets.dropChance(), f)));
        register(SkillIds.THREAT_SENSE, (p, f) -> p.threatSense = new UtilityBalanceSettings.ThreatSense(
                scale(p.threatSense.rangeBlocks(), f, 128)));
        register(SkillIds.HUNTERS_LEDGER, (p, f) -> p.huntersLedger = new UtilityBalanceSettings.HuntersLedger(
                (int) Math.ceil(scale(p.huntersLedger.memoryTicks(), f, 72_000))));
        register(SkillIds.WAYLIGHT, (p, f) -> p.waylight = new UtilityBalanceSettings.Waylight(
                scale(p.waylight.searchRadiusBlocks(), f, 128)));
        register(SkillIds.RESTFUL_MENDING, (p, f) -> p.restfulMending = new UtilityBalanceSettings.RestfulMending(
                p.restfulMending.idleDelayTicks(), scaleRestfulMendingRate(p.restfulMending, f)));
        register(SkillIds.METABOLIC_MENDING, (p, f) -> p.metabolicMending = new UtilityBalanceSettings.MetabolicMending(
                scaleMetabolicMendingRate(p.metabolicMending, f)));
        register(SkillIds.MASTERWORK_TEMPERING, (p, f) -> p.masterworkTempering = new UtilityBalanceSettings.MasterworkTempering(
                scaleOdds(p.masterworkTempering.reinforcementFractionPerMaterialUnit(), f),
                scaleOdds(p.masterworkTempering.maximumOverdurabilityFraction(), f),
                scaleOdds(p.masterworkTempering.maximumPerformanceBonus(), f)));
        register(SkillIds.VILLAGE_PATRON, (p, f) -> p.villagePatron = scaleVillagePatron(p.villagePatron, f));
        register(SkillIds.BONDED_COMPANION, (p, f) -> p.bondedCompanion = scaleBondedCompanion(p.bondedCompanion, f));
        register(SkillIds.ALCHEMICAL_AMPLIFICATION, (p, f) -> p.alchemicalAmplification = new UtilityBalanceSettings.AlchemicalAmplification(
                scaleOdds(p.alchemicalAmplification.maximumBonusFraction(), f), p.alchemicalAmplification.diminishingWindowTicks()));
        register(SkillIds.POTION_RELAY, (p, f) -> p.potionRelay = new UtilityBalanceSettings.PotionRelay(
                p.potionRelay.radiusBlocks(), scaleOdds(p.potionRelay.durationFraction(), f), p.potionRelay.maximumTargets()));
        register(SkillIds.SANCTUARY, (p, f) -> p.sanctuary = new UtilityBalanceSettings.Sanctuary(
                scale(p.sanctuary.radiusBlocks(), f, 128), p.sanctuary.disengageDelayTicks()));
        register(SkillIds.INDUSTRIOUS_PRESENCE, (p, f) -> p.industriousPresence = new UtilityBalanceSettings.IndustriousPresence(
                p.industriousPresence.radiusBlocks(), 1.0 + scale(p.industriousPresence.processingSpeedMultiplier() - 1.0, f, 127)));
        register(SkillIds.CONTAINMENT_FIELD, (p, f) -> p.containmentField = new UtilityBalanceSettings.ContainmentField(
                scale(p.containmentField.radiusBlocks(), f, 128)));
        // Staggered Pain is a binary timing capability, not an invented damage-reduction multiplier.
        // Pure State is binary. Its capability pressure remains one at every
        // projected rank; no inert numeric rule pretends to improve immunity.
    }
    private SkillRankEffectScaling() { }

    /** A future effect registers its typed numeric parameter consumer alongside its handler. */
    public static void register(ResourceLocation id, BiConsumer<Parameters, Double> rule) {
        if (id == null || rule == null || RULES.putIfAbsent(id, rule) != null)
            throw new IllegalArgumentException("Duplicate or invalid rank effect consumer: " + id);
    }

    public static double factor(ResourceLocation id, int rank) {
        if (rank <= 1) return 1.0;
        return SkillBalanceRuntime.require(id.toString(), rank).powerMultiplier()
                / SkillBalanceRuntime.require(id.toString(), 1).powerMultiplier();
    }

    public static boolean supports(ResourceLocation id) { return RULES.containsKey(id); }

    /** Resolved defensive magnitudes retain their own calibration, independent of offensive output. */
    public static double generatedPressureFactor(SkillEffectBalanceSettings settings, ResourceLocation id, double offenseScale) {
        var reference = PostureBalanceSettings.defaults();
        if (id.equals(SkillIds.EVASIVE_CURRENT)) return settings.posture().evasive().maximumDodgeChance()
                / reference.evasive().maximumDodgeChance();
        if (id.equals(SkillIds.BULWARK_STANCE)) return settings.posture().bulwark().maximumResistance()
                / reference.bulwark().maximumResistance();
        if (id.equals(SkillIds.ADAPTIVE_GUARD)) return settings.posture().adaptive().resistancePerStack()
                / reference.adaptive().resistancePerStack();
        if (id.equals(SkillIds.STATUS_MIRROR)) return StatusBalanceSettings.defaults().mirrorCooldownTicks()
                / (double) settings.status().mirrorCooldownTicks();
        if (id.equals(SkillIds.PURE_STATE)) return 1;
        if (com.mistaboom.essence_ascendance.movement.TraversalCapabilities.isBinarySkill(id)) return 1;
        var vitality = VitalityBalanceSettings.defaults();
        if (id.equals(SkillIds.RISING_RECOVERY)) return settings.vitality().risingRecovery().maxSpeedBonus() / vitality.risingRecovery().maxSpeedBonus();
        if (id.equals(SkillIds.LIFE_STEAL)) return settings.vitality().lifeSteal().baseHealingFraction() / vitality.lifeSteal().baseHealingFraction();
        if (id.equals(SkillIds.FEAST_REFLEX)) return vitality.feastReflex().useDurationMultiplier() / settings.vitality().feastReflex().useDurationMultiplier();
        if (id.equals(SkillIds.INNER_SUSTENANCE)) return vitality.innerSustenance().hungerRecoveryIntervalTicks() / (double) settings.vitality().innerSustenance().hungerRecoveryIntervalTicks();
        var damage = settings.vitality().damage();
        var referenceDamage = vitality.damage();
        if (id.equals(SkillIds.STAGGERED_PAIN)) return 1;
        if (id.equals(SkillIds.HUNGER_WARD)) {
            double base = referenceDamage.hungerWard().damageShare();
            double share = damage.hungerWard().damageShare();
            return base > 0 ? (share / (1 - share)) / (base / (1 - base)) : 1;
        }
        if (id.equals(SkillIds.DAMAGE_CEILING)) return (1 / damage.damageCeiling().damageTakenFraction() - 1)
                / (1 / referenceDamage.damageCeiling().damageTakenFraction() - 1);
        if (id.equals(SkillIds.METABOLIC_CONVERSION)) return damage.metabolicConversion().healthPerNutrition() / referenceDamage.metabolicConversion().healthPerNutrition();
        if (id.equals(SkillIds.PAIN_PURGE)) return damage.painPurge().queuePerHealing() / referenceDamage.painPurge().queuePerHealing();
        if (id.equals(SkillIds.ADRENALINE)) return damage.adrenaline().attackSpeedBonus() / referenceDamage.adrenaline().attackSpeedBonus();
        var wards = settings.vitality().wards();
        // Actual dimensional magnitude against the existing semantic weight, never a neutral fixture divisor.
        if (id.equals(SkillIds.SOUL_WARD)) return wards.soulWard().capacityHealthFraction()
                / SkillBalanceSemantics.require(id).weights().get(com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.EFFECTIVE_HEALTH);
        if (id.equals(SkillIds.DEEP_WARD)) return wards.deepWard().capacityBonusFraction()
                / SkillBalanceSemantics.require(id).weights().get(com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.EFFECTIVE_HEALTH);
        if (id.equals(SkillIds.SHATTERING_WARD)) {
            var weights = SkillBalanceSemantics.require(id).weights();
            // The reachable capability projection has one scalar per skill. Keep its largest
            // normalized component so healing calibration cannot erase still-active crowd control.
            double healing = wards.shatteringWard().healingFractionPerSecond()
                    * wards.shatteringWard().regenerationTicks() / 20.0
                    / weights.get(com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.REGENERATION);
            double control = wards.shatteringWard().knockback()
                    / weights.get(com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.CROWD_CONTROL);
            return Math.max(healing, control);
        }
        var movement = settings.mobility();
        if (id.equals(SkillIds.RUNNING_MOMENTUM)) return movement.runningMomentum().maximumSpeedBonus()
                / SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.GROUND_SPEED);
        if (id.equals(SkillIds.MOMENTUM_VAULT)) {
            // Height is a native obstacle capability. Earlier activation improves availability, not jump impulse.
            if (movement.momentumVault().stepHeight() <= 0) return 0;
            return movement.momentumVault().stepHeight() * (2 - movement.momentumVault().minimumMomentum())
                    / SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.VERTICAL_MOVEMENT);
        }
        if (id.equals(SkillIds.RUSH)) {
            // Retained time relative to the shared build window is convenience, not an additional speed multiplier.
            if (movement.runningMomentum().maximumSpeedBonus() <= 0) return 0;
            return (1 + movement.rush().durationTicks() / (double) movement.runningMomentum().buildTicks())
                    / SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.CONVENIENCE);
        }
        if (id.equals(SkillIds.IMPACT_CONTROL)) return movement.impactControl().damageReduction()
                / Math.max(Math.ulp(1.0), 1 - movement.impactControl().damageReduction())
                / SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.FALL_CONTROL);
        if (id.equals(SkillIds.CHARGED_JUMP)) return movement.chargedJump().heightBonus()
                / SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.JUMP);
        if (id.equals(SkillIds.DOUBLE_JUMP)) return 1 + movement.doubleJump().heightBonus()
                / SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.JUMP);
        if (id.equals(SkillIds.VECTOR_JUMP)) return 1 + movement.vectorJump().impulseBonus()
                / (SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.JUMP)
                + SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.VERTICAL_MOVEMENT));
        if (id.equals(SkillIds.ESSENCE_WINGS)) return 1 + movement.essenceWings().horizontalDragCompensation()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.GLIDING));
        if (id.equals(SkillIds.FATIGUE_FLIGHT)) {
            double availability = SkillBalanceSemantics.require(id).expectedAvailability();
            double ratio = movement.fatigueFlight().enduranceTicks() / (double) movement.fatigueFlight().groundRechargeTicks();
            double generated = Math.max(0, Math.sqrt(ratio * (1 - availability) / Math.max(Math.ulp(1.0), availability)) - 1);
            double weight = SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.FLIGHT)
                    + SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.VERTICAL_MOVEMENT);
            return 1 + generated / Math.max(Math.ulp(1.0), weight);
        }
        if (id.equals(SkillIds.VECTOR_BOOST)) return 1 + movement.vectorBoost().rocketSpeedBonus()
                / (SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.GROUND_SPEED)
                + SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.VERTICAL_MOVEMENT));
        if (id.equals(SkillIds.UNTETHERED_FLIGHT)) {
            double generated = Math.max(0, movement.fatigueFlight().enduranceTicks()
                    / (double) movement.untetheredFlight().airRechargeTicks() - 1);
            double weight = SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.FLIGHT)
                    + SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.VERTICAL_MOVEMENT);
            return 1 + generated / Math.max(Math.ulp(1.0), weight);
        }
        var gathering = settings.gathering();
        if (id.equals(SkillIds.TOOL_INSTINCT)) return gathering.toolInstinct().maximumLowerTierSpeedBonus()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.MINING_SPEED));
        if (id.equals(SkillIds.MINING_MOMENTUM)) return gathering.miningMomentum().maximumSpeedBonus()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.MINING_SPEED));
        if (id.equals(SkillIds.NATURES_BOON)) {
            double chance = gathering.naturesBoon().dropChance();
            return chance <= 0 ? 0 : (chance / Math.max(Math.ulp(1.0), 1 - chance))
                    / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.DROP_YIELD));
        }
        if (id.equals(SkillIds.ORE_SIGHT)) return gathering.oreSight().rangeBlocks()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).rangeBlocks());
        if (id.equals(SkillIds.TREASURE_SENSE)) return gathering.treasureSense().rangeBlocks()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).rangeBlocks());
        if (id.equals(SkillIds.HUNTERS_STUDY)) return gathering.huntersStudy().maximumVirtualLootingLevels()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.DROP_YIELD));
        if (id.equals(SkillIds.ESSENCE_BLOOM)) return gathering.essenceBloom().essencePerExperiencePoint()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.CONVERSION));
        if (id.equals(SkillIds.TORCHBEARER)) return 1;
        if (id.equals(SkillIds.VERDANT_STRIDE)) {
            double chance = gathering.verdantStride().growthChance();
            double availability = SkillBalanceSemantics.require(id).expectedAvailability();
            double rawChance = availability <= 0 ? 0 : Math.min(Math.nextDown(1.0), chance / availability);
            return rawChance <= 0 ? 0 : (rawChance / Math.max(Math.ulp(1.0), 1 - rawChance))
                    / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.CROP_YIELD));
        }
        if (id.equals(SkillIds.HERDKEEPER)) return (gathering.herdkeeper().breedingRecoveryMultiplier() - 1.0)
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.THROUGHPUT));
        if (id.equals(SkillIds.ANIMAL_GIFT)) {
            double chance = gathering.animalGift().giftChance();
            double availability = SkillBalanceSemantics.require(id).expectedAvailability();
            double rawChance = availability <= 0 ? 0 : Math.min(Math.nextDown(1.0), chance / availability);
            double combinedWeight = SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.DROP_YIELD)
                    + SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.AUTOMATION_INTERACTION);
            return rawChance <= 0 ? 0 : (rawChance / Math.max(Math.ulp(1.0), 1 - rawChance))
                    / Math.max(Math.ulp(1.0), combinedWeight);
        }
        if (id.equals(SkillIds.FISHING_INSTINCT)) return (gathering.fishingInstinct().biteSpeedMultiplier() - 1.0)
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.THROUGHPUT));
        if (id.equals(SkillIds.FISHERS_CALL)) return (gathering.fishersCall().maximumBiteSpeedMultiplier() - 1.0)
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.THROUGHPUT));
        if (id.equals(SkillIds.SALVAGERS_CRAFT)) return inverseOdds(gathering.salvagersCraft().materialRecoveryFraction())
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.CONVERSION));
        if (id.equals(SkillIds.POCKET_NETS)) {
            double chance = gathering.pocketNets().dropChance();
            double availability = SkillBalanceSemantics.require(id).expectedAvailability();
            double rawChance = availability <= 0 ? 0 : Math.min(Math.nextDown(1.0), chance / availability);
            return inverseOdds(rawChance)
                    / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.DROP_YIELD));
        }
        var utility = settings.utility();
        if (id.equals(SkillIds.THREAT_SENSE)) return utility.threatSense().rangeBlocks()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).rangeBlocks());
        // Hunter's Ledger is an information extension. Its generated memory is rank-scaled,
        // while the capability-pressure projection remains binary like other information gates.
        if (id.equals(SkillIds.HUNTERS_LEDGER)) return 1;
        if (id.equals(SkillIds.WAYLIGHT)) return utility.waylight().searchRadiusBlocks()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).areaRadiusBlocks());
        if (id.equals(SkillIds.RESTFUL_MENDING)) {
            var semantics = SkillBalanceSemantics.require(id);
            double inactive = Math.max(Math.ulp(1.0), 1.0 - semantics.expectedAvailability());
            double window = utility.restfulMending().idleDelayTicks() / 20.0 / inactive;
            double bounded = Math.clamp(utility.restfulMending().repairFractionPerSecond() * window, 0, Math.nextDown(1.0));
            return inverseOdds(bounded)
                    / Math.max(Math.ulp(1.0), semantics.weights().get(CapabilityAxis.REPAIR));
        }
        if (id.equals(SkillIds.METABOLIC_MENDING)) {
            double bounded = Math.clamp(utility.metabolicMending().repairFractionPerFoodPoint()
                    * net.minecraft.world.food.FoodConstants.MAX_FOOD, 0, Math.nextDown(1.0));
            return inverseOdds(bounded)
                    / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.REPAIR));
        }
        if (id.equals(SkillIds.MASTERWORK_TEMPERING)) {
            var weights = SkillBalanceSemantics.require(id).weights();
            double reinforcement = inverseOdds(utility.masterworkTempering().reinforcementFractionPerMaterialUnit())
                    / Math.max(Math.ulp(1.0), weights.get(CapabilityAxis.CONVERSION));
            double capacity = inverseOdds(utility.masterworkTempering().maximumOverdurabilityFraction())
                    / Math.max(Math.ulp(1.0), weights.get(CapabilityAxis.DURABILITY));
            double performance = inverseOdds(utility.masterworkTempering().maximumPerformanceBonus())
                    / Math.max(Math.ulp(1.0), weights.get(CapabilityAxis.THROUGHPUT));
            return Math.max(reinforcement, Math.max(capacity, performance));
        }
        if (id.equals(SkillIds.VILLAGE_PATRON)) return inverseOdds(utility.villagePatron().discountFraction())
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.CONVERSION));
        if (id.equals(SkillIds.BONDED_COMPANION)) return inverseOdds(utility.bondedCompanion().statBonusFraction())
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.SUSTAINED_DAMAGE));
        if (id.equals(SkillIds.ALCHEMICAL_AMPLIFICATION)) return inverseOdds(utility.alchemicalAmplification().maximumBonusFraction())
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.RESOURCE_CONSUMPTION));
        if (id.equals(SkillIds.POTION_RELAY)) return inverseOdds(utility.potionRelay().durationFraction())
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.CONVENIENCE));
        if (id.equals(SkillIds.SANCTUARY)) return utility.sanctuary().radiusBlocks()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).areaRadiusBlocks());
        if (id.equals(SkillIds.INDUSTRIOUS_PRESENCE)) return (utility.industriousPresence().processingSpeedMultiplier() - 1.0)
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).weights().get(CapabilityAxis.THROUGHPUT));
        if (id.equals(SkillIds.CONTAINMENT_FIELD)) return utility.containmentField().radiusBlocks()
                / Math.max(Math.ulp(1.0), SkillBalanceSemantics.require(id).areaRadiusBlocks());
        if (id.equals(SkillIds.FRIENDLY_FIRE_WARD) || id.equals(SkillIds.ENCHANTING_INSIGHT)) return 1;
        return offenseScale;
    }

    public static SkillEffectBalanceSettings apply(SkillEffectBalanceSettings base, Map<ResourceLocation, Integer> effectiveRanks) {
        return apply(base, effectiveRanks, SkillRankEffectScaling::factor);
    }

    /** Generation must resolve against its candidate curves, never an installed/older world profile. */
    public static SkillEffectBalanceSettings apply(SkillEffectBalanceSettings base, Map<ResourceLocation, Integer> effectiveRanks,
            java.util.function.ToDoubleBiFunction<ResourceLocation, Integer> factors) {
        if (effectiveRanks.values().stream().noneMatch(rank -> rank > 1)) return base;
        Parameters parameters = new Parameters(base);
        effectiveRanks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getValue() <= 1) return;
            var rule = RULES.get(entry.getKey());
            if (rule != null) rule.accept(parameters, factors.applyAsDouble(entry.getKey(), entry.getValue()));
        });
        return parameters.build();
    }

    private static double scale(double value, double factor, double maximum) { return Math.min(maximum, value * factor); }
    private static UtilityBalanceSettings.VillagePatron scaleVillagePatron(UtilityBalanceSettings.VillagePatron value,
                                                                            double factor) {
        var weights = SkillBalanceSemantics.require(SkillIds.VILLAGE_PATRON).weights();
        double headroom = inverseOdds(value.discountFraction())
                / Math.max(Math.ulp(1.0), weights.get(CapabilityAxis.CONVERSION));
        double baseRate = odds(headroom * weights.get(CapabilityAxis.THROUGHPUT));
        double scaledRate = odds(headroom * factor * weights.get(CapabilityAxis.THROUGHPUT));
        int interval = baseRate <= 0 || scaledRate <= 0 ? value.restockIntervalTicks()
                : Math.max(1, (int) Math.ceil(value.restockIntervalTicks() * baseRate / scaledRate));
        return new UtilityBalanceSettings.VillagePatron(scaleOdds(value.discountFraction(), factor),
                value.restockRadiusBlocks(), interval);
    }
    private static UtilityBalanceSettings.BondedCompanion scaleBondedCompanion(UtilityBalanceSettings.BondedCompanion value,
                                                                                 double factor) {
        var semantics = SkillBalanceSemantics.require(SkillIds.BONDED_COMPANION);
        var weights = semantics.weights();
        double headroom = inverseOdds(value.statBonusFraction())
                / Math.max(Math.ulp(1.0), weights.get(CapabilityAxis.SUSTAINED_DAMAGE));
        double teleportStrength = odds(headroom * factor * weights.get(CapabilityAxis.TELEPORTATION));
        double catchup = teleportStrength <= 0 ? value.catchupDistanceBlocks()
                : semantics.rangeBlocks() / Math.sqrt(teleportStrength);
        return new UtilityBalanceSettings.BondedCompanion(scaleOdds(value.statBonusFraction(), factor),
                Math.clamp(catchup, semantics.rangeBlocks(), 128));
    }
    private static double scaleRestfulMendingRate(UtilityBalanceSettings.RestfulMending value, double factor) {
        double inactive = Math.max(Math.ulp(1.0),
                1.0 - SkillBalanceSemantics.require(SkillIds.RESTFUL_MENDING).expectedAvailability());
        double window = value.idleDelayTicks() / 20.0 / inactive;
        double bounded = Math.clamp(value.repairFractionPerSecond() * window, 0, Math.nextDown(1.0));
        return Math.min(1.0, scaleOdds(bounded, factor) / Math.max(Math.ulp(1.0), window));
    }
    private static double scaleMetabolicMendingRate(UtilityBalanceSettings.MetabolicMending value, double factor) {
        double barRepair = Math.clamp(value.repairFractionPerFoodPoint()
                * net.minecraft.world.food.FoodConstants.MAX_FOOD, 0, Math.nextDown(1.0));
        return scaleOdds(barRepair, factor) / net.minecraft.world.food.FoodConstants.MAX_FOOD;
    }
    private static double inverseOdds(double probability) {
        if (probability <= 0) return 0;
        if (probability >= 1) return Double.MAX_VALUE;
        return probability / (1.0 - probability);
    }
    private static double odds(double power) { return power <= 0 ? 0 : power / (1.0 + power); }
    // The sum of a geometric continuation remains bounded by the rank factor.
    private static double retained(double value, double factor) { return value <= 0 ? 0 : 1.0 - (1.0 - value) / factor; }
    /** Scales independent-event odds without treating probability as a linear damage fraction. */
    private static double scaleOdds(double probability, double factor) {
        if (probability <= 0) return 0;
        if (probability >= 1) return 1;
        double odds = probability / (1.0 - probability);
        double scaled = odds * factor;
        return scaled / (1.0 + scaled);
    }
    private static ProjectileBalanceSettings.Profile homing(ProjectileBalanceSettings.Profile v, double factor) {
        return new ProjectileBalanceSettings.Profile(v.range(), v.speed(), v.lifetimeTicks(), v.acquisitionRange(),
                v.acquisitionConeDegrees(), scale(v.turnDegreesPerTick(), factor, 45));
    }
    private static ProjectileBalanceSettings projectile(ProjectileBalanceSettings v, ProjectileBalanceSettings.Profile arrow,
            ProjectileBalanceSettings.Profile caster, double ricochet, double piercing, double shield) {
        return new ProjectileBalanceSettings(arrow, caster, v.ricochets(), v.ricochetRadius(), ricochet,
                v.penetrations(), piercing, shield, v.maximumImpacts(), v.maximumSpeed(), v.payload(), v.control());
    }
    private static ProjectileBalanceSettings withPayload(ProjectileBalanceSettings v, ProjectileBalanceSettings.Payload payload) {
        return new ProjectileBalanceSettings(v.arrow(), v.caster(), v.ricochets(), v.ricochetRadius(), v.ricochetDamageMultiplier(),
                v.penetrations(), v.piercingDamageMultiplier(), v.piercingShieldDamageMultiplier(),
                v.maximumImpacts(), v.maximumSpeed(), payload, v.control());
    }
    private static ProjectileBalanceSettings withControl(ProjectileBalanceSettings v, ProjectileBalanceSettings.Control control) {
        return new ProjectileBalanceSettings(v.arrow(), v.caster(), v.ricochets(), v.ricochetRadius(), v.ricochetDamageMultiplier(),
                v.penetrations(), v.piercingDamageMultiplier(), v.piercingShieldDamageMultiplier(),
                v.maximumImpacts(), v.maximumSpeed(), v.payload(), control);
    }

    public static final class Parameters {
        public SkillEffectBalanceSettings.Frenzy frenzy;
        public SkillEffectBalanceSettings.ArmorCrack armorCrack;
        public SkillEffectBalanceSettings.Desperation desperation;
        public SkillEffectBalanceSettings.DeathRush deathRush;
        public SkillEffectBalanceSettings.Kindling kindling;
        public SkillEffectBalanceSettings.Combustion combustion;
        public SkillEffectBalanceSettings.Frostbite frostbite;
        public SkillEffectBalanceSettings.Shatter shatter;
        public SkillEffectBalanceSettings.StaticCharge staticCharge;
        public SkillEffectBalanceSettings.ChainStrike chainStrike;
        public ProjectileBalanceSettings projectiles;
        public MobilityBalanceSettings.RunningMomentum runningMomentum;
        public MobilityBalanceSettings.MomentumVault momentumVault;
        public MobilityBalanceSettings.Rush rush;
        public MobilityBalanceSettings.ImpactControl impactControl;
        public MobilityBalanceSettings.ChargedJump chargedJump;
        public MobilityBalanceSettings.AirJump doubleJump;
        public MobilityBalanceSettings.VectorJump vectorJump;
        public MobilityBalanceSettings.EssenceWings essenceWings;
        public MobilityBalanceSettings.FatigueFlight fatigueFlight;
        public MobilityBalanceSettings.VectorBoost vectorBoost;
        public MobilityBalanceSettings.UntetheredFlight untetheredFlight;
        public GatheringBalanceSettings.ToolInstinct toolInstinct;
        public GatheringBalanceSettings.MiningMomentum miningMomentum;
        public GatheringBalanceSettings.NaturesBoon naturesBoon;
        public GatheringBalanceSettings.Survey oreSight;
        public GatheringBalanceSettings.Survey treasureSense;
        public GatheringBalanceSettings.HuntersStudy huntersStudy;
        public GatheringBalanceSettings.EssenceBloom essenceBloom;
        public GatheringBalanceSettings.VerdantStride verdantStride;
        public GatheringBalanceSettings.Herdkeeper herdkeeper;
        public GatheringBalanceSettings.AnimalGift animalGift;
        public GatheringBalanceSettings.FishingInstinct fishingInstinct;
        public GatheringBalanceSettings.FishersCall fishersCall;
        public GatheringBalanceSettings.SalvagersCraft salvagersCraft;
        public GatheringBalanceSettings.PocketNets pocketNets;
        public UtilityBalanceSettings.ThreatSense threatSense;
        public UtilityBalanceSettings.HuntersLedger huntersLedger;
        public UtilityBalanceSettings.Waylight waylight;
        public UtilityBalanceSettings.RestfulMending restfulMending;
        public UtilityBalanceSettings.MetabolicMending metabolicMending;
        public UtilityBalanceSettings.MasterworkTempering masterworkTempering;
        public UtilityBalanceSettings.VillagePatron villagePatron;
        public UtilityBalanceSettings.BondedCompanion bondedCompanion;
        public UtilityBalanceSettings.AlchemicalAmplification alchemicalAmplification;
        public UtilityBalanceSettings.PotionRelay potionRelay;
        public UtilityBalanceSettings.Sanctuary sanctuary;
        public UtilityBalanceSettings.IndustriousPresence industriousPresence;
        public UtilityBalanceSettings.ContainmentField containmentField;
        public GuardBalanceSettings.Mobility mobility;
        public GuardBalanceSettings.Ram ram;
        public GuardBalanceSettings.Ward ward;
        public GuardBalanceSettings.StoredForce storedForce;
        public GuardBalanceSettings.Amplifier amplifier;
        public GuardBalanceSettings.PerfectGuard perfectGuard;
        public GuardBalanceSettings.Reprisal reprisal;
        public GuardBalanceSettings.Riposte riposte;
        public PostureBalanceSettings.Movement postureMovement;
        public PostureBalanceSettings.Evasive evasive;
        public PostureBalanceSettings.Bulwark bulwark;
        public PostureBalanceSettings.Adaptive adaptive;
        public StatusBalanceSettings status;
        public VitalityBalanceSettings.RisingRecovery risingRecovery;
        public VitalityBalanceSettings.LifeSteal lifeSteal;
        public VitalityBalanceSettings.FeastReflex feastReflex;
        public VitalityBalanceSettings.InnerSustenance innerSustenance;
        public VitalityDamageBalanceSettings.HungerWard hungerWard;
        public VitalityDamageBalanceSettings.StaggeredPain staggeredPain;
        public VitalityDamageBalanceSettings.DamageCeiling damageCeiling;
        public VitalityDamageBalanceSettings.MetabolicConversion metabolicConversion;
        public VitalityDamageBalanceSettings.PainPurge painPurge;
        public VitalityDamageBalanceSettings.Adrenaline adrenaline;
        public VitalityWardBalanceSettings.SoulWard soulWard;
        public VitalityWardBalanceSettings.DeepWard deepWard;
        public VitalityWardBalanceSettings.ShatteringWard shatteringWard;
        private Parameters(SkillEffectBalanceSettings v) {
            frenzy=v.frenzy(); armorCrack=v.armorCrack(); desperation=v.desperation(); deathRush=v.deathRush();
            kindling=v.kindling(); combustion=v.combustion(); frostbite=v.frostbite(); shatter=v.shatter();
            staticCharge=v.staticCharge(); chainStrike=v.chainStrike(); projectiles=v.projectiles();
            mobility=v.guard().mobility(); ram=v.guard().ram(); ward=v.guard().ward(); storedForce=v.guard().storedForce();
            amplifier=v.guard().amplifier(); perfectGuard=v.guard().perfectGuard(); reprisal=v.guard().reprisal(); riposte=v.guard().riposte();
            postureMovement=v.posture().movement(); evasive=v.posture().evasive();
            bulwark=v.posture().bulwark(); adaptive=v.posture().adaptive(); status=v.status();
            risingRecovery=v.vitality().risingRecovery(); lifeSteal=v.vitality().lifeSteal();
            feastReflex=v.vitality().feastReflex(); innerSustenance=v.vitality().innerSustenance();
            hungerWard=v.vitality().damage().hungerWard();
            staggeredPain=v.vitality().damage().staggeredPain();
            damageCeiling=v.vitality().damage().damageCeiling();
            metabolicConversion=v.vitality().damage().metabolicConversion();
            painPurge=v.vitality().damage().painPurge();
            adrenaline=v.vitality().damage().adrenaline();
            soulWard=v.vitality().wards().soulWard();
            deepWard=v.vitality().wards().deepWard();
            shatteringWard=v.vitality().wards().shatteringWard();
            runningMomentum=v.mobility().runningMomentum(); momentumVault=v.mobility().momentumVault(); rush=v.mobility().rush();
            impactControl=v.mobility().impactControl(); chargedJump=v.mobility().chargedJump();
            doubleJump=v.mobility().doubleJump(); vectorJump=v.mobility().vectorJump(); essenceWings=v.mobility().essenceWings();
            fatigueFlight=v.mobility().fatigueFlight(); vectorBoost=v.mobility().vectorBoost();
            untetheredFlight=v.mobility().untetheredFlight();
            toolInstinct=v.gathering().toolInstinct(); miningMomentum=v.gathering().miningMomentum();
            naturesBoon=v.gathering().naturesBoon(); oreSight=v.gathering().oreSight();
            treasureSense=v.gathering().treasureSense(); huntersStudy=v.gathering().huntersStudy();
            essenceBloom=v.gathering().essenceBloom(); verdantStride=v.gathering().verdantStride();
            herdkeeper=v.gathering().herdkeeper(); animalGift=v.gathering().animalGift();
            fishingInstinct=v.gathering().fishingInstinct(); fishersCall=v.gathering().fishersCall();
            salvagersCraft=v.gathering().salvagersCraft(); pocketNets=v.gathering().pocketNets();
            threatSense=v.utility().threatSense(); huntersLedger=v.utility().huntersLedger(); waylight=v.utility().waylight();
            restfulMending=v.utility().restfulMending(); metabolicMending=v.utility().metabolicMending();
            masterworkTempering=v.utility().masterworkTempering(); villagePatron=v.utility().villagePatron();
            bondedCompanion=v.utility().bondedCompanion(); alchemicalAmplification=v.utility().alchemicalAmplification();
            potionRelay=v.utility().potionRelay(); sanctuary=v.utility().sanctuary();
            industriousPresence=v.utility().industriousPresence(); containmentField=v.utility().containmentField();

        }
        private SkillEffectBalanceSettings build() {
            return new SkillEffectBalanceSettings(frenzy, armorCrack, desperation, deathRush, kindling,
                    combustion, frostbite, shatter, staticCharge, chainStrike, projectiles,
                    new GuardBalanceSettings(mobility, ram, ward, storedForce, amplifier, perfectGuard, reprisal, riposte),
                    new PostureBalanceSettings(postureMovement, evasive, bulwark, adaptive), status,
                    new VitalityBalanceSettings(risingRecovery, lifeSteal, feastReflex, innerSustenance,
                            new VitalityDamageBalanceSettings(hungerWard, staggeredPain, damageCeiling, metabolicConversion, painPurge, adrenaline),
                            new VitalityWardBalanceSettings(soulWard, deepWard, shatteringWard)),
                    new MobilityBalanceSettings(runningMomentum, momentumVault, rush, impactControl, chargedJump, doubleJump, vectorJump,
                            essenceWings, fatigueFlight, vectorBoost, untetheredFlight),
                    new GatheringBalanceSettings(toolInstinct, miningMomentum, naturesBoon, oreSight, treasureSense,
                            huntersStudy, essenceBloom, verdantStride, herdkeeper, animalGift, fishingInstinct,
                            fishersCall, salvagersCraft, pocketNets),
                    new UtilityBalanceSettings(threatSense, huntersLedger, waylight, restfulMending,
                            metabolicMending, masterworkTempering, villagePatron, bondedCompanion, alchemicalAmplification, potionRelay,
                            sanctuary, industriousPresence, containmentField));
        }
    }
}
