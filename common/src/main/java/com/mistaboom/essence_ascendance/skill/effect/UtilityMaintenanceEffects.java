package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.config.UtilityBalanceSettings;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineResult;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService;
import com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData;
import com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceService;
import com.mistaboom.essence_ascendance.equipment.MasterworkTemperingRoles;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileDefinition;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards.compact;

/** Utility maintenance branch built on shared inventory, durability, food, and equipment-attribute hooks. */
public final class UtilityMaintenanceEffects {
    private static final double EPSILON = 1.0E-9;

    private UtilityMaintenanceEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new RestfulMending(), new MetabolicMending(), new MasterworkTempering());
    }

    private static UtilityBalanceSettings settings(SkillEffectRuntime.Context context) {
        return context.settings().utility();
    }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.utility." + key, args);
    }

    private static ItemStack highestOverdurabilityItem(SkillEffectRuntime.Context context) {
        ItemStack main = context.player().getMainHandItem();
        if (EquipmentMaintenanceService.eligible(main) && EquipmentMaintenanceData.overdurability(main) > 0) return main;
        ItemStack off = context.player().getOffhandItem();
        if (EquipmentMaintenanceService.eligible(off) && EquipmentMaintenanceData.overdurability(off) > 0) return off;

        ItemStack best = ItemStack.EMPTY;
        double bestScore = 0;
        for (ItemStack stack : EquipmentMaintenanceService.carriedItems(context.player())) {
            if (!EquipmentMaintenanceService.eligible(stack)) continue;
            double score = EquipmentMaintenanceData.overdurability(stack);
            if (score > bestScore) {
                best = stack;
                bestScore = score;
            }
        }
        return best;
    }

    private static final class RestfulMending implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.RESTFUL_MENDING; }

        @Override public void tick(SkillEffectRuntime.Context context) {
            RestfulState state = context.state(id(), RestfulState::new);
            UtilityBalanceSettings.RestfulMending tuning = settings(context).restfulMending();
            Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            ItemStack repairTarget = ItemStack.EMPTY;
            double targetWear = -1;

            for (ItemStack stack : EquipmentMaintenanceService.carriedItems(context.player())) {
                if (!EquipmentMaintenanceService.eligible(stack) || stack.getDamageValue() <= 0) continue;
                seen.add(stack);
                RestfulItemState item = state.items.computeIfAbsent(stack, ignored -> new RestfulItemState(context.now()));
                if (context.now() - item.lastUseAt < tuning.idleDelayTicks()) continue;
                double wear = (double) stack.getDamageValue() / stack.getMaxDamage();
                if (wear > targetWear) {
                    targetWear = wear;
                    repairTarget = stack;
                }
            }

            state.items.keySet().removeIf(stack -> !seen.contains(stack));
            state.activeTarget = repairTarget.isEmpty() ? null : repairTarget;
            if (repairTarget.isEmpty() || tuning.repairFractionPerSecond() <= 0) return;

            RestfulItemState item = state.items.get(repairTarget);
            double repair = repairTarget.getMaxDamage() * tuning.repairFractionPerSecond() / 20.0 + item.repairCarry;
            int generatedRepair = (int) Math.floor(repair + EPSILON);
            int wholeRepair = Math.min(repairTarget.getDamageValue(), generatedRepair);
            item.repairCarry = Math.max(0, repair - generatedRepair);
            if (wholeRepair <= 0) return;

            repairTarget.setDamageValue(repairTarget.getDamageValue() - wholeRepair);
            if (repairTarget.getDamageValue() <= 0) item.repairCarry = 0;
        }

        @Override public void durabilityAttempt(SkillEffectRuntime.Context context, ItemStack stack) {
            if (!EquipmentMaintenanceService.eligible(stack)) return;
            RestfulState state = context.state(id(), RestfulState::new);
            RestfulItemState item = state.items.computeIfAbsent(stack, ignored -> new RestfulItemState(context.now()));
            item.lastUseAt = context.now();
            item.repairCarry = 0;
            if (state.activeTarget == stack) state.activeTarget = null;
        }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            RestfulState state = context.existingState(id());
            ItemStack target = state == null ? ItemStack.EMPTY : state.activeTarget;
            if (target == null || target.isEmpty() || !EquipmentMaintenanceService.eligible(target)
                    || target.getDamageValue() <= 0) {
                List<ItemStack> damaged = EquipmentMaintenanceService.prioritizedDamagedItems(context.player());
                target = damaged.isEmpty() ? ItemStack.EMPTY : damaged.getFirst();
            }
            if (target.isEmpty()) {
                return SkillEffectHudEntry.skill(id(), false, AscendancePalette.UTILITY,
                        text("restful_mending"), List.of(), SkillEffectHudEntry.Meter.none());
            }

            UtilityBalanceSettings.RestfulMending tuning = settings(context).restfulMending();
            RestfulItemState item = state == null ? null : state.items.get(target);
            long lastUse = item == null ? context.now() : item.lastUseAt;
            long remaining = Math.max(0, tuning.idleDelayTicks() - (context.now() - lastUse));
            double rate = target.getMaxDamage() * tuning.repairFractionPerSecond();
            List<Text> details = List.of(Text.literal(target.getHoverName().getString()),
                    text("restful_rate", compact(rate)),
                    text("restful_missing", Integer.toString(target.getDamageValue())));

            if (remaining > 0) {
                return SkillEffectHudEntry.skill(id(), true, AscendancePalette.UTILITY,
                        text("restful_waiting"), details,
                        SkillEffectHudEntry.Meter.timer("hud.essence_ascendance.utility.restful_idle", context.now() + remaining));
            }
            double repairedFraction = 1.0 - (double) target.getDamageValue() / target.getMaxDamage();
            return SkillEffectHudCards.progress(id(), true, AscendancePalette.UTILITY,
                    text("restful_mending"), details, repairedFraction);
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            UtilityBalanceSettings.RestfulMending tuning = settings(context).restfulMending();
            RestfulState state = context.existingState(id());
            ItemStack target = state == null || state.activeTarget == null ? ItemStack.EMPTY : state.activeTarget;
            return List.of("Idle delay=" + tuning.idleDelayTicks() + " ticks; repair fraction/second="
                            + tuning.repairFractionPerSecond(),
                    "Current repair target=" + (target.isEmpty() ? "none" : target.getHoverName().getString())
                            + "; one idle carried damageable item is repaired at a time; Fractured Ascendance artifacts are excluded.");
        }
    }

    private static final class RestfulState implements SkillEffectState {
        final Map<ItemStack, RestfulItemState> items = new IdentityHashMap<>();
        ItemStack activeTarget;
        @Override public void clear() { items.clear(); activeTarget = null; }
    }

    private static final class RestfulItemState {
        long lastUseAt;
        double repairCarry;
        RestfulItemState(long now) { lastUseAt = now; }
    }

    /** Food is the resource: native nutrition plus its intrinsic vanilla saturation contribution buys immediate repair. */
    private static final class MetabolicMending implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.METABOLIC_MENDING; }

        @Override public void foodConsumed(SkillEffectRuntime.Context context, ItemStack source, int nutrition,
                                           double saturationPoints) {
            UtilityBalanceSettings.MetabolicMending tuning = settings(context).metabolicMending();
            double foodValue = Math.max(0, nutrition) + Math.max(0, saturationPoints);
            if (foodValue <= 0 || tuning.repairFractionPerFoodPoint() <= 0) return;

            MetabolicState state = context.state(id(), MetabolicState::new);
            double budget = foodValue * tuning.repairFractionPerFoodPoint() + Math.max(0, state.repairCarryFraction);
            int repaired = 0;
            String lastTarget = "";

            for (ItemStack target : EquipmentMaintenanceService.prioritizedDamagedItems(context.player())) {
                if (budget <= EPSILON) break;
                int missing = target.getDamageValue();
                if (missing <= 0) continue;
                int generated = (int) Math.min(missing, Math.floor(budget * target.getMaxDamage() + EPSILON));
                if (generated <= 0) continue;
                target.setDamageValue(missing - generated);
                budget = Math.max(0, budget - (double) generated / target.getMaxDamage());
                repaired += generated;
                lastTarget = target.getHoverName().getString();
            }

            // Only carry sub-point rounding while damage still exists; unused repair from an oversized meal is not banked.
            state.repairCarryFraction = EquipmentMaintenanceService.hasDamagedItem(context.player()) ? budget : 0;
            state.lastFoodValue = foodValue;
            state.lastRepaired = repaired;
            state.lastTarget = lastTarget;
            state.lastAt = context.now();
        }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            MetabolicState state = context.existingState(id());
            UtilityBalanceSettings.MetabolicMending tuning = settings(context).metabolicMending();
            boolean active = state != null && state.lastRepaired > 0 && context.now() - state.lastAt <= 1;
            if (!active) {
                return SkillEffectHudEntry.skill(id(), false, AscendancePalette.UTILITY,
                        text("metabolic_mending"),
                        List.of(text("metabolic_rate", compact(tuning.repairFractionPerFoodPoint() * 100.0))),
                        SkillEffectHudEntry.Meter.none()).asEvent();
            }
            return SkillEffectHudEntry.skill(id(), true, AscendancePalette.UTILITY,
                    text("metabolic_repaired", Integer.toString(state.lastRepaired)),
                    List.of(Text.literal(state.lastTarget),
                            text("metabolic_food_value", compact(state.lastFoodValue)),
                            text("metabolic_rate", compact(tuning.repairFractionPerFoodPoint() * 100.0))),
                    SkillEffectHudEntry.Meter.none()).asEvent();
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            UtilityBalanceSettings.MetabolicMending tuning = settings(context).metabolicMending();
            MetabolicState state = context.existingState(id());
            return List.of("Repair fraction per intrinsic food point=" + tuning.repairFractionPerFoodPoint()
                            + "; food value = nutrition + native saturation contribution.",
                    "Last repair=" + (state == null ? 0 : state.lastRepaired)
                            + "; repair order is equipped/active gear, hotbar, then inventory, most worn first within each band; Feast Reflex may auto-eat while repairable damage remains.");
        }
    }

    private static final class MetabolicState implements SkillEffectState {
        double repairCarryFraction;
        double lastFoodValue;
        int lastRepaired;
        String lastTarget = "";
        long lastAt;
        @Override public void clear() { repairCarryFraction = 0; lastFoodValue = 0; lastRepaired = 0; lastTarget = ""; lastAt = 0; }
    }

    private static final class MasterworkHudState implements SkillEffectState {
        String targetName = "";
        double remaining;
        double capacity;
        double bonus;
        int absorbed;
        long lastAt = Long.MIN_VALUE;

        @Override public void clear() {
            targetName = "";
            remaining = 0;
            capacity = 0;
            bonus = 0;
            absorbed = 0;
            lastAt = Long.MIN_VALUE;
        }
    }

    /** Anvil-created Overdurability is item-local; its active benefit is gated by this mutually-exclusive skill. */
    private static final class MasterworkTempering implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.MASTERWORK_TEMPERING; }

        @Override public void reconcile(SkillEffectRuntime.Context context) {
            applyPerformance(context);
        }

        @Override public int durabilityLoss(SkillEffectRuntime.Context context, ItemStack stack, int actualDamage) {
            if (!EquipmentMaintenanceService.eligible(stack) || actualDamage <= 0) return actualDamage;
            double capacity = EquipmentMaintenanceData.clampOverdurability(stack,
                    settings(context).masterworkTempering().maximumOverdurabilityFraction());
            double overdurability = Math.min(capacity, EquipmentMaintenanceData.overdurability(stack));
            if (capacity <= 0 || overdurability <= 0) return actualDamage;

            int absorbed = Math.min(actualDamage, (int) Math.floor(overdurability + EPSILON));
            if (absorbed <= 0) {
                // Fractional residue can never absorb a native durability point, so the item is no longer tempered.
                EquipmentMaintenanceData.clearOverdurability(stack);
                return actualDamage;
            }

            double remaining = overdurability - absorbed;
            // Durability damage is discrete. A sub-point residue cannot absorb another wear event and therefore
            // is no longer a meaningful tempered state; discard it immediately rather than leaving inert item data.
            boolean exhausted = remaining < 1.0 - EPSILON;
            if (exhausted) EquipmentMaintenanceData.clearOverdurability(stack);
            else EquipmentMaintenanceData.setOverdurability(stack, remaining, capacity);
            double displayedRemaining = exhausted ? 0 : remaining;
            double bonus = exhausted ? 0 : performance(stack, settings(context).masterworkTempering());
            recordHudUse(context, stack, displayedRemaining, capacity, bonus, absorbed);
            return actualDamage - absorbed;
        }

        @Override public double damageMultiplier(SkillEffectRuntime.Context context, LivingEntity target,
                                                 DamageSource source, AttackCategory primaryCategory) {
            if (primaryCategory != AttackCategory.RANGED && primaryCategory != AttackCategory.CASTER) return 1.0;
            double bonus = performance(context.player().getMainHandItem(), settings(context).masterworkTempering());
            return 1.0 + bonus;
        }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            MasterworkHudState state = context.existingState(id());
            boolean active = state != null && state.lastAt != Long.MIN_VALUE
                    && context.now() >= state.lastAt && context.now() - state.lastAt <= 1;
            if (!active) {
                ItemStack target = highestOverdurabilityItem(context);
                double remaining = target.isEmpty() ? 0 : EquipmentMaintenanceData.overdurability(target);
                double capacity = target.isEmpty() ? 0 : EquipmentMaintenanceData.overdurabilityCapacity(target);
                return SkillEffectHudCards.progress(id(), remaining > 0, AscendancePalette.UTILITY,
                        text("masterwork_overdurability", compact(remaining), compact(capacity)),
                        target.isEmpty() ? List.of() : List.of(Text.literal(target.getHoverName().getString())),
                        capacity <= 0 ? 0 : Math.clamp(remaining / capacity, 0, 1));
            }
            return SkillEffectHudCards.progress(id(), true, AscendancePalette.UTILITY,
                    text("masterwork_overdurability", compact(state.remaining), compact(state.capacity)),
                    List.of(Text.literal(state.targetName),
                            text("masterwork_absorbed", Integer.toString(state.absorbed)),
                            text("masterwork_bonus", compact(state.bonus * 100.0))),
                    state.capacity <= 0 ? 0 : Math.clamp(state.remaining / state.capacity, 0, 1));
        }

        @Override public void deactivate(SkillEffectRuntime.Context context) {
            clearPerformance(context);
            context.discardState(id());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            UtilityBalanceSettings.MasterworkTempering tuning = settings(context).masterworkTempering();
            ItemStack target = highestOverdurabilityItem(context);
            return List.of("Reinforcement fraction/material unit=" + tuning.reinforcementFractionPerMaterialUnit()
                            + "; maximum overdurability fraction=" + tuning.maximumOverdurabilityFraction()
                            + "; maximum performance bonus=" + tuning.maximumPerformanceBonus(),
                    "Tempering requires full native durability; Ascendance artifacts use Latent nugget/ingot/block. Highest carried Overdurability=" + (target.isEmpty() ? "none"
                            : EquipmentMaintenanceData.overdurability(target) + "/"
                            + EquipmentMaintenanceData.overdurabilityCapacity(target) + " on "
                            + target.getHoverName().getString())
                            + "; benefits are active only while Masterwork Tempering is effective.");
        }

        private void applyPerformance(SkillEffectRuntime.Context context) {
            UtilityBalanceSettings.MasterworkTempering tuning = settings(context).masterworkTempering();
            ItemStack main = context.player().getMainHandItem();
            double mainBonus = performance(main, tuning);

            double attackBonus = MasterworkTemperingRoles.melee(main) ? mainBonus : 0;
            double attackSpeedBonus = MasterworkTemperingRoles.attackSpeed(main) ? mainBonus : 0;
            double miningBonus = MasterworkTemperingRoles.mining(main) ? mainBonus : 0;
            SkillEffectAttributes.apply(context.player(), Attributes.ATTACK_DAMAGE, id(), attackBonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            SkillEffectAttributes.apply(context.player(), Attributes.ATTACK_SPEED, id(), attackSpeedBonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            SkillEffectAttributes.apply(context.player(), Attributes.BLOCK_BREAK_SPEED, id(), miningBonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

            PlayerEssenceData playerData = EssenceSavedData.get(context.player().server)
                    .getPlayerData(context.player().getUUID());
            double armorBonus = 0;
            double toughnessBonus = 0;
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                ItemStack stack = context.player().getItemBySlot(slot);
                double piecePerformance = performance(stack, tuning);
                if (piecePerformance <= 0) continue;
                armorBonus += armorBaseline(stack, slot, playerData, EquipmentBaselineProperty.ARMOR) * piecePerformance;
                toughnessBonus += armorBaseline(stack, slot, playerData, EquipmentBaselineProperty.TOUGHNESS) * piecePerformance;
            }
            SkillEffectAttributes.apply(context.player(), Attributes.ARMOR, id(), armorBonus,
                    AttributeModifier.Operation.ADD_VALUE);
            SkillEffectAttributes.apply(context.player(), Attributes.ARMOR_TOUGHNESS, id(), toughnessBonus,
                    AttributeModifier.Operation.ADD_VALUE);
        }

        private void clearPerformance(SkillEffectRuntime.Context context) {
            SkillEffectAttributes.apply(context.player(), Attributes.ATTACK_DAMAGE, id(), 0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            SkillEffectAttributes.apply(context.player(), Attributes.ATTACK_SPEED, id(), 0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            SkillEffectAttributes.apply(context.player(), Attributes.BLOCK_BREAK_SPEED, id(), 0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            SkillEffectAttributes.apply(context.player(), Attributes.ARMOR, id(), 0,
                    AttributeModifier.Operation.ADD_VALUE);
            SkillEffectAttributes.apply(context.player(), Attributes.ARMOR_TOUGHNESS, id(), 0,
                    AttributeModifier.Operation.ADD_VALUE);
        }

        private static double performance(ItemStack stack, UtilityBalanceSettings.MasterworkTempering tuning) {
            if (!EquipmentMaintenanceService.eligible(stack)) return 0;
            double capacity = EquipmentMaintenanceData.clampOverdurability(stack, tuning.maximumOverdurabilityFraction());
            double overdurability = Math.min(capacity, EquipmentMaintenanceData.overdurability(stack));
            if (capacity <= 0 || overdurability <= 0) return 0;
            return Math.clamp(overdurability / capacity, 0, 1) * tuning.maximumPerformanceBonus();
        }

        private static double armorBaseline(ItemStack stack, EquipmentSlot slot, PlayerEssenceData playerData,
                                            EquipmentBaselineProperty property) {
            Holder<Attribute> attribute = property == EquipmentBaselineProperty.ARMOR
                    ? Attributes.ARMOR : Attributes.ARMOR_TOUGHNESS;
            double nativeValue = MasterworkTemperingRoles.nativePositiveAdditive(stack, attribute, slot);
            if (nativeValue > 0) return nativeValue;

            EquipmentProfileDefinition profile = MasterworkTemperingRoles.firstPartyProfile(stack);
            if (profile == null || profile.baselineMultiplier(property) <= 0) return 0;
            EquipmentBaselineResult baseline = EquipmentBaselineService.evaluateForStack(
                    playerData, profile.id(), stack);
            return property == EquipmentBaselineProperty.ARMOR
                    ? baseline.armorForSlot(slot)
                    : baseline.toughnessForSlot(slot);
        }

        private void recordHudUse(SkillEffectRuntime.Context context, ItemStack stack,
                                  double remaining, double capacity, double bonus, int absorbed) {
            MasterworkHudState state = context.state(id(), MasterworkHudState::new);
            state.targetName = stack.getHoverName().getString();
            state.remaining = Math.max(0, remaining);
            state.capacity = Math.max(0, capacity);
            state.bonus = Math.max(0, bonus);
            state.absorbed = Math.max(0, absorbed);
            state.lastAt = context.now();
        }

    }
}
