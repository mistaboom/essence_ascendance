package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Native self-use food/potion effects. Measurements describe one successful use,
 * never infinite supply, permanent uptime, or a simulated player outcome. */
public final class NativeConsumables {
    public static final String PROVIDER = "native_consumables";
    private NativeConsumables() { }

    public static void collect(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
        var attempt = OptionalIntegration.attempt(PROVIDER, "native consumable acquisition",
                () -> ConfiguredRecipeAccess.nativeCrafting(context, resources));
        if (!attempt.succeeded()) {
            sink.candidate(PROVIDER, PROVIDER, attempt.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED); return;
        }
        var access = attempt.value().orElseThrow();
        for (var item : context.inputs().items()) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("essence_ascendance")) continue;
            ItemStack stack = new ItemStack(item);
            if (!stack.has(DataComponents.FOOD)) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            var captured = OptionalIntegration.attempt(PROVIDER, id, () -> {
                var staged = new CapabilitySink();
                // The joint planner preserves quantities/stations. Native unlocked loot may
                // supply a conditional successful exploration serving, never guaranteed stock.
                var proof = access.requireExploration(List.of(request(id, 1))).access();
                readStack(stack, id, "food_serving", proof.placement(), staged);
                staged.definition(PROVIDER, id + "/access", BalanceDocument.GSON.toJsonTree(proof));
                naturalRegeneration(stack, id, context.server().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION),
                        bill -> access.requireExploration(bill).access(), staged);
                return staged;
            });
            captured.value().ifPresentOrElse(sink::merge, () -> sink.candidate(id, PROVIDER,
                    captured.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED));
        }
        NativeBrewing.collect(context, access, sink);
    }

    public static JsonObject request(String id, int count) {
        var result = new JsonObject(); result.addProperty("id", id); result.addProperty("count", count); return result;
    }

    public record Meal(int servings, int foodLevel, float saturation, int pulseTicks, double pulseHealth,
                       double pulseExhaustion, float eatingSeconds) { }

    /** A counted meal from zero food/saturation, at rest with no intervening exhaustion.
     * Native FoodData performs the actual nutrition/saturation clamp. This is a
     * conditional first pulse, not total healing, perpetual uptime or a survival simulation. */
    public static Optional<Meal> meal(ItemStack stack, boolean naturalRegeneration) {
        var food = stack.get(DataComponents.FOOD);
        Class<?> itemClass = stack.getItem().getClass();
        if (!naturalRegeneration || food == null || food.nutrition() <= 0 || !food.effects().isEmpty()
                || !Float.isFinite(food.saturation()) || food.saturation() < 0
                || !Float.isFinite(food.eatSeconds()) || food.eatSeconds() <= 0
                || (itemClass != Item.class && itemClass != ItemNameBlockItem.class && itemClass != BlockItem.class)) return Optional.empty();
        var state = new FoodData(); state.setFoodLevel(0); state.setSaturation(0);
        int servings = 0;
        while (state.getFoodLevel() < 20 && servings < 20) { state.eat(food); servings++; }
        if (state.getFoodLevel() != 20) return Optional.empty();
        double saturationPulse = Math.min(6, state.getSaturationLevel());
        return Optional.of(new Meal(servings, state.getFoodLevel(), state.getSaturationLevel(), saturationPulse > 0 ? 10 : 80,
                saturationPulse > 0 ? saturationPulse / 6 : 1, saturationPulse > 0 ? saturationPulse : 6,
                servings * food.eatSeconds()));
    }

    public static void naturalRegeneration(ItemStack stack, String id, boolean enabled,
            java.util.function.Function<List<JsonObject>, ConfigurationAccess.Proof> acquisition, CapabilitySink sink) {
        var scenario = meal(stack, enabled);
        if (scenario.isEmpty()) return;
        var meal = scenario.orElseThrow();
        var proof = acquisition.apply(List.of(request(id, meal.servings())));
        var definition = new JsonObject(); definition.add("meal", BalanceDocument.GSON.toJsonTree(meal));
        definition.add("access", BalanceDocument.GSON.toJsonTree(proof));
        definition.addProperty("naturalRegeneration", enabled);
        definition.addProperty("scenario", "Ordinary food without status effects; fill from zero food/saturation while at rest; missing health; no intervening exhaustion or damage; one conditional native FoodData pulse");
        sink.definition(PROVIDER, id + "/natural_regeneration", definition);
        var operation = new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.FINITE, null,
                meal.pulseTicks() / 20.0, null, null,
                List.of(meal.servings() + " consumed servings; " + meal.eatingSeconds() + " seconds of eating; pulse adds " + meal.pulseExhaustion() + " exhaustion"),
                List.of("naturalRegeneration=true; missing health; full hunger after counted meal; no intervening exhaustion",
                        "Native pulse potential only; food/saturation deplete; no total healing, safe eating time, sustained cadence or uptime inferred"));
        var measured = new ArrayList<Measurement>();
        add(measured, CapabilityAxis.REGENERATION, meal.pulseHealth() * 20 / meal.pulseTicks(), "health_points_per_second", "self",
                operation, "Conditional natural regeneration first pulse after a counted full meal; capped by missing health", 1);
        var placement = proof.placement();
        sink.add(new Functional(new CapabilityEvidence(id, placement.stage(), Map.of(), placement.reachable(), Math.min(.9, placement.confidence()),
                "Effective native food/saturation rules with independently acquired complete meal"), "natural_regeneration", measured,
                placement.acquisition(), placement.reachable()));
        if (!placement.reachable()) sink.candidate(id, PROVIDER, "Complete meal acquisition unproven; one serving is not a full hunger bar",
                Set.of(CapabilityAxis.REGENERATION), CapabilitySink.Reason.ACCESS_UNPROVEN);
    }

    public static void readStack(ItemStack stack, String id, String configuration,
            CompetitiveCapabilities.Placement placement, CapabilitySink sink) {
        var food = stack.get(DataComponents.FOOD);
        var potion = stack.get(DataComponents.POTION_CONTENTS);
        if (food == null && potion == null) return;
        if (food != null && stack.getItem().getClass() != Item.class
                && stack.getItem().getClass() != ItemNameBlockItem.class && stack.getItem().getClass() != BlockItem.class
                && stack.getItem().getClass() != HoneyBottleItem.class
                && stack.getItem().getClass() != SuspiciousStewItem.class && stack.getItem().getClass() != ChorusFruitItem.class) {
            sink.candidate(id, PROVIDER, "Custom food use callback requires an adapter", Set.of(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); return;
        }
        // Configured ammunition, splash and lingering delivery need their own target model.
        if (potion != null && stack.getItem().getClass() != PotionItem.class) {
            sink.candidate(id, PROVIDER, "Non-drinkable potion delivery is not a self-use effect", Set.of(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR);
            return;
        }
        var definition = new JsonObject();
        definition.addProperty("runtimeClass", stack.getItem().getClass().getName());
        definition.addProperty("servings", 1);
        var effects = new JsonArray();
        if (food != null) {
            definition.addProperty("nutrition", food.nutrition()); definition.addProperty("saturation", food.saturation());
            definition.addProperty("eatSeconds", food.eatSeconds()); definition.addProperty("alwaysEdible", food.canAlwaysEat());
            definition.addProperty("naturalRegeneration", "Conditional on food/saturation, missing health and gamerule; nutrition is not unconditional healing");
            if (stack.getItem() instanceof HoneyBottleItem || stack.getItem() instanceof SuspiciousStewItem || stack.getItem() instanceof ChorusFruitItem)
                sink.candidate(id, PROVIDER, "Only FOOD component effects read here; additional native item actions need separate witnesses",
                        Set.of(), CapabilitySink.Reason.NO_SUPPORTED_OPERATION);
            for (var possible : food.effects()) effect(id, configuration, possible.effect(), possible.probability(),
                    food.canAlwaysEat() ? "Native eating action" : "Native eating action requires hunger", placement, sink, effects);
        }
        if (potion != null) for (var instance : potion.getAllEffects())
            effect(id, configuration, instance, 1, "Drink one potion; native use duration; returned bottle is empty", placement, sink, effects);
        definition.add("effects", effects); sink.definition(PROVIDER, id + "/" + configuration, definition);
        if (!placement.reachable()) sink.candidate(id, PROVIDER, "Finite serving acquisition unproven; registry/default components are not supply",
                Set.of(), CapabilitySink.Reason.ACCESS_UNPROVEN);
    }

    private static void effect(String id, String configuration, MobEffectInstance instance, double chance, String action,
            CompetitiveCapabilities.Placement placement, CapabilitySink sink, JsonArray effects) {
        String effectId = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value()).toString();
        var detail = new JsonObject(); detail.addProperty("effect", effectId); detail.addProperty("amplifier", instance.getAmplifier());
        detail.addProperty("durationTicks", instance.getDuration()); detail.addProperty("chance", chance); effects.add(detail);
        if (!(chance > 0 && chance <= 1) || !Double.isFinite(chance)) return;
        int amplifier = instance.getAmplifier();
        if (amplifier < 0 || amplifier > 16 || instance.isInfiniteDuration()) {
            sink.candidate(id, PROVIDER, "Effect level/duration outside audited consumable contract: " + effectId,
                    Set.of(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); return;
        }
        var effect = instance.getEffect();
        if (!effect.equals(MobEffects.HEAL) && !effect.equals(MobEffects.HARM) && instance.getDuration() <= 0) return;
        if (effect.equals(MobEffects.REGENERATION) && instance.getDuration() < Math.max(1, 50 >> amplifier)) return;
        double duration = Math.max(0, instance.getDuration()) / 20.0;
        var operation = new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.FINITE, null,
                duration, null, null, List.of("One consumed serving per use; activation chance=" + chance),
                List.of(action, "No ongoing use cadence, stacking, passive supply or full-time uptime assumed"));
        var measured = new ArrayList<Measurement>();
        if (effect.equals(MobEffects.REGENERATION)) add(measured, CapabilityAxis.REGENERATION,
                20.0 / Math.max(1, 50 >> amplifier), "health_points_per_second", "self", operation, "While active and health is missing", chance);
        else if (effect.equals(MobEffects.HEAL)) add(measured, CapabilityAxis.HEALING, 4.0 * (1 << amplifier),
                "health_points", "self", operation, "One direct heal of a non-inverted recipient, capped by missing health", chance);
        else if (effect.equals(MobEffects.DAMAGE_RESISTANCE)) add(measured, CapabilityAxis.DAMAGE_REDUCTION,
                Math.min(1, .2 * (amplifier + 1)), "fraction", "resistance_eligible_damage", operation,
                "Native bypasses_resistance damage excluded", chance);
        else if (effect.equals(MobEffects.ABSORPTION)) add(measured, CapabilityAxis.SHIELD_CAPACITY,
                4.0 * (amplifier + 1), "health_points", "absorption", operation, "Temporary native absorption; not a regenerating ward", chance);
        else if (effect.equals(MobEffects.FIRE_RESISTANCE)) add(measured, CapabilityAxis.STATUS_RESISTANCE,
                1, "presence", "fire_damage", operation, "Native fire damage immunity; no general harmful-effect immunity", chance);
        else if (effect.equals(MobEffects.WATER_BREATHING)) add(measured, CapabilityAxis.STATUS_RESISTANCE,
                1, "presence", "drowning", operation, "Prevents native underwater air depletion", chance);
        else if (effect.equals(MobEffects.SLOW_FALLING)) add(measured, CapabilityAxis.FALL_CONTROL,
                1, "presence", "fall_damage", operation, "Native descent and fall-distance handling; not sustained flight", chance);
        else if (effect.equals(MobEffects.NIGHT_VISION)) add(measured, CapabilityAxis.INFORMATION,
                1, "presence", "darkness_visibility", operation, "Native visibility; does not discover hidden targets", chance);
        else if (effect.equals(MobEffects.JUMP)) add(measured, CapabilityAxis.JUMP,
                .1 * (amplifier + 1), "attribute_addition", "ground_jump", operation, "Native additional ground-jump impulse; no midair jump", chance);
        // Base native attribute templates are data. Never invoke a custom effect subclass's callbacks.
        if (effect.value().getClass() == MobEffect.class) effect.value().createModifiers(amplifier, (attribute, modifier) -> {
            var axis = NativeCapabilityReader.attributeAxis(attribute.unwrapKey().orElseThrow().location().toString());
            if (axis == null || modifier.amount() <= 0) return;
            String unit = modifier.operation() == AttributeModifier.Operation.ADD_VALUE ? "attribute_addition"
                    : modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL ? "attribute_total_factor" : "attribute_multiplier_addition";
            double amount = modifier.amount() + (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL ? 1 : 0);
            add(measured, axis, amount, unit, "self", operation, "Native temporary attribute modifier " + attribute.unwrapKey().orElseThrow().location(), chance);
        });
        if (measured.isEmpty()) {
            sink.candidate(id, PROVIDER, "Effect retained in definition but no supported benefit response: " + effectId,
                    Set.of(), CapabilitySink.Reason.NO_SUPPORTED_OPERATION); return;
        }
        sink.add(new Functional(new CapabilityEvidence(id, placement.stage(), Map.of(), placement.reachable(),
                Math.min(.9, placement.confidence()), "Effective native serving effect with separately proven supply"),
                configuration + "/" + effectId, measured, placement.acquisition(), placement.reachable()));
    }
    private static void add(List<Measurement> out, CapabilityAxis axis, double value, String unit, String target,
            Operation operation, String applicability, double chance) {
        out.add(CompetitiveCapabilities.measurement(axis, value, unit, applicability + "; activation chance=" + chance,
                new Scope(target, "single", null, 1.0), operation, PROVIDER, Origin.NATIVE,
                List.of("Conditional peak; single-use benefit; sustained uptime and throughput unmeasured")));
        out.add(CompetitiveCapabilities.measurement(axis, 1.0, "consumable_function", applicability,
                new Scope(target, "single", null, 1.0), operation, PROVIDER, Origin.NATIVE,
                List.of("Function on successful use only; not an equivalent-strength or continuous-operation claim")));
    }
}
