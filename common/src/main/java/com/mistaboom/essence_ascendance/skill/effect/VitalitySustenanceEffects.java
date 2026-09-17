package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.attunement.AttunementEvent;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

import java.util.List;
import java.util.function.Predicate;

/** Manual food remains native use; Feast's automatic meals complete virtually in the shared runtime. */
public final class VitalitySustenanceEffects {
    public static final TagKey<Item> EXCLUDED_AUTO_FOODS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("essence_ascendance", "feast_reflex_excluded_foods"));
    private VitalitySustenanceEffects() { }
    public static List<SkillEffectHandler> handlers() { return List.of(new Feast(), new Inner()); }

    public static int useDuration(ItemStack stack, LivingEntity entity, int nativeDuration) {
        if (!(entity instanceof ServerPlayer player) || !player.isAlive() || player.isRemoved()
                || stack.isEmpty() || stack.getUseAnimation() != UseAnim.EAT && stack.getUseAnimation() != UseAnim.DRINK)
            return nativeDuration;
        var context = SkillEffectRuntime.context(player);
        return context.isEffective(SkillIds.FEAST_REFLEX)
                ? SustenanceMath.useDuration(nativeDuration, context.settings().vitality().feastReflex().useDurationMultiplier())
                : nativeDuration;
    }

    /** Cheap committed-state query, also safe during runtime deactivation and sleep-list recomputation. */
    public static boolean fullySustained(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && CommittedSkillService.isEffective(player, SkillIds.INNER_SUSTENANCE)
                && SustenanceMath.full(player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel());
    }

    /** Called only at identified native exertion call sites, never the general exhaustion entrypoint. */
    public static float passiveExhaustion(ServerPlayer player, float amount) {
        return Float.isFinite(amount) && amount > 0 && fullySustained(player) ? 0 : amount;
    }

    public static boolean suitable(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (stack.isEmpty() || stack.is(EXCLUDED_AUTO_FOODS) || food == null || food.nutrition() <= 0
                || stack.getUseAnimation() != UseAnim.EAT && stack.getUseAnimation() != UseAnim.DRINK
                || stack.useOnRelease() || food.effects().stream().anyMatch(effect -> effect.probability() > 0
                && harmful(effect.effect().getEffect()))) return false;
        var stew = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (stew != null && stew.effects().stream().anyMatch(effect -> harmful(effect.effect()))) return false;
        var potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) for (var effect : potion.getAllEffects()) if (harmful(effect.getEffect())) return false;
        return true;
    }

    private static boolean harmful(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        return effect.value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL;
    }

    /** Native nutrition descending, saturation descending, then leftmost hotbar slot. */
    public static int chooseFood(Inventory inventory, Predicate<ItemStack> permitted) {
        int selected = -1;
        FoodProperties best = null;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!suitable(stack) || !permitted.test(stack)) continue;
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (best == null || food.nutrition() > best.nutrition()
                    || food.nutrition() == best.nutrition() && food.saturation() > best.saturation()) {
                selected = slot; best = food;
            }
        }
        return selected;
    }

    static final class FeastState implements SkillEffectState {
        long lastAttempt = Long.MIN_VALUE;
        boolean claim(long tick) {
            if (lastAttempt == tick) return false;
            lastAttempt = tick;
            return true;
        }
        boolean active() { return false; }
        @Override public void clear() { lastAttempt = Long.MIN_VALUE; }
    }

    private static final class Feast implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.FEAST_REFLEX; }
        @Override public void acceptedDamage(SkillEffectRuntime.Context context, DamageSource source,
                                             double healthLost, double absorptionLost) {
            double acceptedLoss = Math.max(healthLost, 0) + Math.max(absorptionLost, 0);
            if (!Double.isFinite(acceptedLoss) || acceptedLoss <= 0) return;
            tryStart(context);
        }
        private void tryStart(SkillEffectRuntime.Context context) {
            ServerPlayer player = context.player();
            if (player.isSpectator() || player.getAbilities().instabuild || player.isUsingItem()) return;
            FeastState state = context.state(id(), FeastState::new);
            if (state.active() || !state.claim(context.now())) return;
            int foodLevel = player.getFoodData().getFoodLevel();
            boolean healingNeeded = com.mistaboom.essence_ascendance.vitality.HealingRecoveryService.needsRecovery(player);
            boolean foodCanHeal = context.isEffective(SkillIds.METABOLIC_CONVERSION)
                    && context.settings().vitality().damage().metabolicConversion().healthPerNutrition() > 0;
            int slot = chooseFood(player.getInventory(), stack -> {
                FoodProperties food = stack.get(DataComponents.FOOD);
                return food != null
                        && player.canEat(food.canAlwaysEat())
                        && !player.getCooldowns().isOnCooldown(stack.getItem())
                        && stack.getUseDuration(player) > 0
                        && SustenanceMath.automaticMealOpportunity(healingNeeded, foodCanHeal, foodLevel,
                        food.nutrition(), player.isUsingItem());
            });
            if (slot < 0) return;
            virtualEat(player, slot);
        }
        @Override public void reconcile(SkillEffectRuntime.Context context) {
            // Virtual meals have no native use state or temporary slot claim
            // to reconcile across a dimension boundary.
        }
        @Override public void tick(SkillEffectRuntime.Context context) {
            // Hunger-only meals have no damage callback to initiate them;
            // evaluate the same deterministic opportunity on each player
            // tick so a low food bar can be topped up naturally. Automatic
            // consumption itself is virtual and never enters item use.
            tryStart(context);
        }
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            context.discardState(id());
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Food/drink duration factor=" + context.settings().vitality().feastReflex().useDurationMultiplier(),
                    "HP=" + context.player().getHealth() + "/" + context.player().getMaxHealth()
                            + "; food=" + context.player().getFoodData().getFoodLevel() + "/" + net.minecraft.world.food.FoodConstants.MAX_FOOD
                            + "; saturation=" + context.player().getFoodData().getSaturationLevel(),
                    "Needs recovery=" + com.mistaboom.essence_ascendance.vitality.HealingRecoveryService.needsRecovery(context.player())
                            + "; Metabolic effective=" + context.isEffective(SkillIds.METABOLIC_CONVERSION)
                            + "; food HP/nutrition=" + context.settings().vitality().damage().metabolicConversion().healthPerNutrition(),
                    "Native canEat=" + context.player().canEat(false) + "; manual item use=" + context.player().isUsingItem()
                            + "; safe hotbar food slot=" + chooseFood(context.player().getInventory(), stack -> true),
                    "Automatic selection=safe useful meals below natural regeneration, or through full hunger until HP is full with effective Metabolic Conversion; healthy meals waste less than half nutrition; never interrupts item use.");
        }
    }

    /**
     * Complete one automatic meal through the item's normal finish path,
     * without starting a hand-use action. Player.eat/LivingEntity.eat still
     * apply nutrition, saturation, effects, sounds, statistics, criteria,
     * containers and game events; the returned stack is written back to the
     * original hotbar slot.
     */
    private static boolean virtualEat(ServerPlayer player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (!suitable(stack) || food == null || !player.canEat(food.canAlwaysEat())
                || player.getCooldowns().isOnCooldown(stack.getItem()) || stack.getUseDuration(player) <= 0)
            return false;
        ItemStack result = stack.finishUsingItem(player.level(), player);
        player.getInventory().setItem(slot, result);
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    static final class InnerState implements SkillEffectState {
        final SustenanceMath.RecoveryClock clock = new SustenanceMath.RecoveryClock();
        boolean wasFull;
        @Override public void clear() { clock.clear(); wasFull = false; }
    }
    private static final class Inner implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.INNER_SUSTENANCE; }
        @Override public void tick(SkillEffectRuntime.Context context) {
            var player = context.player();
            var state = context.state(id(), InnerState::new);
            var settings = context.settings().vitality().innerSustenance();
            boolean combat = RecentHostileCombat.remaining(context, settings.combatTimeoutTicks()) > 0;
            if (state.clock.tick(context.now(), combat || player.isSpectator(), settings.hungerRecoveryIntervalTicks())) {
                var food = player.getFoodData();
                double before = AttunementGameplay.food(player);
                food.setFoodLevel(Math.min(net.minecraft.world.food.FoodConstants.MAX_FOOD, food.getFoodLevel() + settings.hungerPerRecovery()));
                food.setSaturation((float) Math.min(food.getFoodLevel(), food.getSaturationLevel() + settings.saturationPerRecovery()));
                double restored = AttunementGameplay.food(player) - before;
                if (restored > 0) SkillEffectRuntime.reportOutcome(player,
                        new AttunementEvent(AttunementGameplay.action("inner_sustenance"), List.of(
                                AttunementEvent.Outcome.eligible("restore_hunger", id().toString(), restored))));
            }
            boolean full = fullySustained(player);
            if (full != state.wasFull) {
                state.wasFull = full;
                player.serverLevel().updateSleepingPlayerList();
            }
        }
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            InnerState state = context.existingState(id());
            // A native sleep update may have excluded this owner between selection and the first skill tick.
            boolean update = context.isEffective(id()) || state != null;
            context.discardState(id());
            if (update) context.player().serverLevel().updateSleepingPlayerList();
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var settings = context.settings().vitality().innerSustenance();
            return List.of("Combat recovery lock ticks=" + RecentHostileCombat.remaining(context, settings.combatTimeoutTicks()),
                    "Recovery=" + settings.hungerPerRecovery() + " hunger + " + settings.saturationPerRecovery()
                            + " saturation / " + settings.hungerRecoveryIntervalTicks() + " quiet ticks",
                    "Full saturation protection=" + fullySustained(context.player()));
        }
    }
}
