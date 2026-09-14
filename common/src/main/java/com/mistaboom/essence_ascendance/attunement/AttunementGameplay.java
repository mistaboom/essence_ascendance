package com.mistaboom.essence_ascendance.attunement;

import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.skill.effect.SkillProcDamageService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Native outcome adapters. No menu preview, loot query, item pickup or client amount is an outcome. */
public final class AttunementGameplay {
    private static long sequence;
    private static final String SESSION = java.util.UUID.randomUUID().toString();
    private static final ThreadLocal<Deque<DamageFrame>> DAMAGE = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<HarvestFrame>> HARVEST = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<ExertionScope>> EXERTION_SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<String> EXPERIENCE_ROOT = new ThreadLocal<>();
    private static final ThreadLocal<String> ROOT_ACTION = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESSED = ThreadLocal.withInitial(() -> false);
    private static final Map<ServerPlayer, Exertion> EXERTION = new WeakHashMap<>();
    private static final Map<ServerPlayer, Movement> MOVEMENT = new WeakHashMap<>();
    private static final Map<ServerPlayer, Map<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>, EffectSource>> HARMFUL_EFFECT_SOURCES = new WeakHashMap<>();
    private AttunementGameplay() { }

    public static String action(String family) {
        String active = ROOT_ACTION.get();
        return active == null ? SESSION + ":" + family + ":" + (++sequence) : active;
    }
    public static boolean eligible(ServerPlayer player) {
        return com.mistaboom.essence_ascendance.config.EssenceConfigManager.authoritativeReady()
                && !SUPPRESSED.get() && player != null && player.server.isSameThread() && player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && !player.hasInfiniteMaterials()
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
    public static void award(ServerPlayer player, String root, String activity, String source, double units) {
        if (eligible(player) && Double.isFinite(units) && units > 0)
            AttunementService.submit(player, root, activity, sourceSignature(source), units);
    }
    private static void reject(ServerPlayer player, String root, String activity, String source, String reason) {
        if (eligible(player)) AttunementService.submit(player, new AttunementEvent(root,
                List.of(AttunementEvent.Outcome.rejected(activity, sourceSignature(source), reason))));
    }
    public static String sourceSignature(String source) {
        if (source.length() <= 256) return source;
        try {
            String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return source.substring(0, 64) + "#" + digest;
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static void tick(ServerPlayer player) {
        Map<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>, EffectSource> effects = HARMFUL_EFFECT_SOURCES.get(player);
        if (effects != null) {
            effects.entrySet().removeIf(entry -> player.getEffect(entry.getKey()) != entry.getValue().instance);
            if (effects.isEmpty()) HARMFUL_EFFECT_SOURCES.remove(player);
        }
        AttunementService.tick(player);
    }
    public static void forget(ServerPlayer player) {
        EXERTION.remove(player); MOVEMENT.remove(player);
        HARMFUL_EFFECT_SOURCES.remove(player);
    }
    public static void clearAll() {
        EXERTION.clear(); MOVEMENT.clear(); HARMFUL_EFFECT_SOURCES.clear();
        DAMAGE.remove(); HARVEST.remove(); EXERTION_SCOPES.remove(); EXPERIENCE_ROOT.remove(); ROOT_ACTION.remove(); SUPPRESSED.remove();
    }
    /** Stale native container clicks may still execute in vanilla; they cannot be progression receipts. */
    public static void withMenuRequest(boolean current, Runnable original) {
        String root = ROOT_ACTION.get(); boolean suppressed = SUPPRESSED.get();
        ROOT_ACTION.set(action("menu_request")); SUPPRESSED.set(suppressed || !current);
        try { original.run(); } finally {
            if (root == null) ROOT_ACTION.remove(); else ROOT_ACTION.set(root);
            if (suppressed) SUPPRESSED.set(true); else SUPPRESSED.remove();
        }
    }
    public static void setMovementIntent(ServerPlayer player, boolean active) {
        if (!player.server.isSameThread()) return;
        Movement movement = MOVEMENT.computeIfAbsent(player, ignored -> new Movement());
        movement.intent = active;
        movement.intentTick = player.level().getGameTime();
    }
    /** Shared evidence only: consumers must independently corroborate native server displacement. */
    public static boolean movementIntent(ServerPlayer player, int timeoutTicks) {
        Movement movement = MOVEMENT.get(player);
        long elapsed = movement == null ? -1 : player.level().getGameTime() - movement.intentTick;
        return movement != null && movement.intent && elapsed >= 0 && elapsed <= timeoutTicks;
    }

    public static boolean damage(LivingEntity target, DamageSource source, float requested, BooleanSupplier original) {
        if (target.level().isClientSide || !com.mistaboom.essence_ascendance.config.EssenceConfigManager.authoritativeReady())
            return original.getAsBoolean();
        Deque<DamageFrame> frames = DAMAGE.get();
        DamageFrame frame = new DamageFrame(target, source, requested, action("damage"));
        frames.push(frame);
        try {
            boolean accepted = original.getAsBoolean();
            if (accepted || frame.blocked > 0) completeDamage(frame);
            else if (target instanceof ServerPlayer player)
                reject(player, frame.root, "take_damage", source.getMsgId(), "unconfirmed_damage");
            else reject(frame.owner, frame.root, "deal_damage", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString(), "unconfirmed_damage");
            return accepted;
        } finally {
            frames.pop();
            if (frames.isEmpty()) DAMAGE.remove();
        }
    }
    /** Called at the native health/absorption write, before a Totem can restore health. */
    public static void damageMeasured(LivingEntity target, DamageSource source, double ownLoss, double healthLoss) {
        DamageFrame frame = DAMAGE.get().peek();
        if (frame != null && frame.target == target && frame.source == source) {
            frame.loss += Math.max(0, ownLoss);
            frame.healthLoss += Math.max(0, healthLoss);
        }
    }
    public static void blocked(ServerPlayer player, DamageSource source, double units) {
        DamageFrame frame = DAMAGE.get().peek();
        if (frame != null && frame.target == player && frame.source == source) frame.blocked += Math.max(0, units);
    }
    public static void prevented(LivingEntity target, DamageSource source, double before, double after) {
        DamageFrame frame = DAMAGE.get().peek();
        if (frame != null && frame.target == target && frame.source == source && Double.isFinite(before) && Double.isFinite(after))
            frame.prevented += Math.max(0, before - after);
    }
    private static void completeDamage(DamageFrame frame) {
        ServerPlayer attacker = frame.owner;
        String enemy = BuiltInRegistries.ENTITY_TYPE.getKey(frame.target.getType()).toString();
        if (attacker != frame.target) {
            award(attacker, frame.root, "deal_damage", enemy, frame.loss * threat(attacker, frame.target));
            if (frame.reflected) award(attacker, frame.root, "reflect_damage", enemy, frame.loss);
            if (frame.loss > 0 && attacker != null) exert(attacker, "combat");
        }
        if (!(frame.target instanceof ServerPlayer player)) return;
        boolean self = frame.source.getEntity() == player || frame.source.getDirectEntity() == player || attacker == player
                || untrustedEffectDamage(player, frame.source);
        if (self && frame.healthLoss > 0) {
            var saved = EssenceSavedData.get(player.server);
            var state = saved.getPlayerData(player.getUUID()).attunement();
            long additional = micro(frame.healthLoss);
            state.setRejectedHealingMicros(Math.min(micro(player.getMaxHealth()),
                    Math.min(Long.MAX_VALUE - additional, state.rejectedHealingMicros()) + additional));
            saved.setDirty();
        }
        if (self) {
            reject(player, frame.root, "take_damage", frame.source.getMsgId(), "self_or_unattributed_damage");
            return;
        }
        String source = frame.source.getMsgId() + ":" + (frame.source.getEntity() == null ? "environment"
                : BuiltInRegistries.ENTITY_TYPE.getKey(frame.source.getEntity().getType()));
        award(player, frame.root, "take_damage", source, frame.loss);
        // A successful damage call or native shield commit is required. Invulnerability and cancellation never qualify.
        double prevented = frame.blocked + frame.prevented;
        award(player, frame.root, "prevent_damage", source, prevented);
        if (frame.source.is(DamageTypeTags.IS_FIRE) || frame.source.is(DamageTypeTags.WITCH_RESISTANT_TO))
            award(player, frame.root, "resist_harmful_effects", source, prevented);
        if (frame.loss > 0 && frame.source.getEntity() instanceof LivingEntity) exert(player, "combat");
        if (frame.loss > 0 && EXERTION_SCOPES.get().stream().noneMatch(scope -> scope.player == player))
            recordExhaustion(player, frame.exhaustionBefore, "combat");
    }
    public static void defeated(LivingEntity target, DamageSource source) {
        DamageFrame frame = DAMAGE.get().peek();
        ServerPlayer player = owner(source);
        if (!eligible(player)) return;
        if (player != target) award(player, frame != null && frame.target == target ? frame.root : action("defeat"),
                "defeat_enemies", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString(), target.getMaxHealth() * threat(player, target));
    }
    private static ServerPlayer owner(DamageSource source) {
        var proc = SkillProcDamageService.current();
        if (proc != null) return proc.owner();
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }
    private static boolean untrustedEffectDamage(ServerPlayer player, DamageSource source) {
        if (source.getEntity() != null) return false;
        var effect = source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                ? player.getEffect(net.minecraft.world.effect.MobEffects.POISON)
                : source.is(net.minecraft.world.damagesource.DamageTypes.WITHER) ? player.getEffect(net.minecraft.world.effect.MobEffects.WITHER) : null;
        return effect != null && effectSource(player, effect) == null;
    }
    private static double threat(ServerPlayer player, LivingEntity target) {
        if (player == null) return 1;
        var data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        var profile = com.mistaboom.essence_ascendance.config.EssenceConfigManager.runtime().attunement();
        if (profile.chapter(data.getTier().id().toString()) == null) return 1;
        String suffix = data.getTier().id().getPath();
        var attack = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double damage = attack == null ? 0 : Math.max(0, attack.getValue());
        return Math.clamp(Math.sqrt((1 + damage / reference("enemy_damage_" + suffix))
                        * (1 + Math.max(0, target.getArmorValue()) / reference("enemy_armor_" + suffix)) / 2),
                profile.policy().repetitionFloor(), reference("enemy_threat_cap"));
    }
    /** A completed operation keeps its undiscounted significance even when a real efficiency effect makes cost zero. */
    public static double experienceOperationValue(ServerPlayer player, double originalExperience) {
        if (!eligible(player)) return 0;
        if (!Double.isFinite(originalExperience) || originalExperience <= 0) return 0;
        var data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        var profile = com.mistaboom.essence_ascendance.config.EssenceConfigManager.runtime().attunement();
        if (profile.chapter(data.getTier().id().toString()) == null) return 0;
        return originalExperience * reference("material_value_" + data.getTier().id().getPath())
                / reference("experience_per_reference_operation_" + data.getTier().id().getPath());
    }
    /**
     * Converts a committed operation's undiscounted level price into native XP points.
     * Vanilla level spending preserves the fractional bar and usually leaves totalExperience
     * unchanged, so neither totalExperience deltas nor a constant XP/level conversion work.
     * These coefficients are Player.getXpNeededForNextLevel's mapped 1.21.1 mechanics,
     * not balance parameters. Sum each linear interval directly, including very large costs.
     */
    public static double experienceCost(int levelBefore, float progressBefore, int originalLevels) {
        if (originalLevels <= 0) return 0;
        // Efficiency can make a price affordable below its original level requirement.
        // Retain the full original operation at its minimum undiscounted affordable level.
        int upper = Math.max(Math.max(0, levelBefore), originalLevels);
        int lower = upper - originalLevels;
        double points = experienceInterval(lower, Math.min(upper, 15), 7, 2)
                + experienceInterval(Math.max(lower, 15), Math.min(upper, 30), -38, 5)
                + experienceInterval(Math.max(lower, 30), upper, -158, 9);
        double fraction = Float.isFinite(progressBefore) ? Math.clamp(progressBefore, 0, 1) : 0;
        return points + fraction * (experienceToNextLevel(upper) - experienceToNextLevel(lower));
    }
    private static double experienceInterval(int lower, int upper, double intercept, double slope) {
        if (upper <= lower) return 0;
        return (upper - (double) lower) * (2 * intercept + slope * (lower + (double) upper - 1)) / 2;
    }
    private static double experienceToNextLevel(int level) {
        return level >= 30 ? 112 + 9.0 * (level - 30)
                : level >= 15 ? 37 + 5.0 * (level - 15) : 7 + 2.0 * level;
    }
    public static void healed(ServerPlayer player, double before, String source) {
        double actual = Math.max(0, player.getHealth() - before);
        if (!(actual > 0) || !eligible(player)) return;
        var saved = EssenceSavedData.get(player.server);
        var state = saved.getPlayerData(player.getUUID()).attunement();
        long heal = micro(actual), rejected = Math.min(heal, state.rejectedHealingMicros());
        state.setRejectedHealingMicros(player.getHealth() >= player.getMaxHealth() ? 0 : state.rejectedHealingMicros() - rejected);
        saved.setDirty();
        String root = action("heal");
        if (rejected > 0) reject(player, root, "heal_health", source, "rejected_damage_recovery");
        award(player, root, "heal_health", source, (heal - rejected) / (double) FractionalAmountService.SCALE);
    }
    public static void effectApplied(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, Entity source) {
        if (!eligible(player)) return;
        var actual = player.getEffect(effect);
        if (actual == null || effect.value().isBeneficial()) return;
        var effects = HARMFUL_EFFECT_SOURCES.computeIfAbsent(player, ignored -> new java.util.HashMap<>());
        effects.remove(effect);
        // Unknown/self application is deliberately not a resistance farming route. Existing effects
        // restored from a save have no trustworthy source; new externally attributed applications do.
        if (source != null && source != player)
            effects.put(effect, new EffectSource(actual, effect.unwrapKey().map(key -> key.location().toString()).orElse("harmful_effect")
                    + ":" + BuiltInRegistries.ENTITY_TYPE.getKey(source.getType())));
    }
    public static void effectShortened(ServerPlayer player, net.minecraft.world.effect.MobEffectInstance effect, int before, int after) {
        if (!eligible(player)) return;
        String source = effectSource(player, effect);
        if (source == null || before <= 0 || after >= before) return;
        award(player, action("resist_effect"), "resist_harmful_effects", source,
                reference("player_health") * (before - after) / before);
    }
    private static String effectSource(ServerPlayer player, net.minecraft.world.effect.MobEffectInstance effect) {
        var sources = HARMFUL_EFFECT_SOURCES.get(player);
        var source = sources == null ? null : sources.get(effect.getEffect());
        return source != null && source.instance == effect ? source.signature : null;
    }
    private static long micro(double value) {
        return (long) Math.min(Long.MAX_VALUE / 2.0, Math.max(0, value) * FractionalAmountService.SCALE);
    }
    public static void exert(ServerPlayer player, String family) {
        if (eligible(player)) for (ExertionScope scope : EXERTION_SCOPES.get())
            if (scope.player == player) { scope.confirmed = true; break; }
    }
    public static ExertionScope beginExertion(ServerPlayer player, String family) {
        ExertionScope scope = new ExertionScope(player, family, player.getFoodData().getExhaustionLevel());
        EXERTION_SCOPES.get().push(scope);
        return scope;
    }
    public static void endExertion(ExertionScope scope) {
        Deque<ExertionScope> scopes = EXERTION_SCOPES.get();
        scopes.pop();
        if (scope.confirmed && scopes.stream().noneMatch(outer -> outer.player == scope.player))
            recordExhaustion(scope.player, scope.before, scope.family);
        if (scopes.isEmpty()) EXERTION_SCOPES.remove();
    }
    private static void recordExhaustion(ServerPlayer player, double before, String source) {
        double actual = player.getFoodData().getExhaustionLevel() - before;
        if (!eligible(player) || !(actual > 0)) return;
        Exertion previous = EXERTION.get(player);
        long tick = player.level().getGameTime();
        double budget = previous == null || tick - previous.tick > reference("exertion_recent_ticks") ? 0 : previous.foodUnits;
        // Four native exhaustion points consume one saturation/food point. Regeneration and harmful
        // effects never enter an eligible action scope, so their exhaustion cannot inflate this budget.
        budget = Math.min(reference("food_capacity"), budget + actual / reference("exhaustion_per_food"));
        EXERTION.put(player, new Exertion(tick, source, budget));
    }
    public static void hungerConsumed(ServerPlayer player, double before) {
        Exertion exertion = EXERTION.get(player);
        if (exertion == null || player.level().getGameTime() - exertion.tick > reference("exertion_recent_ticks")) return;
        double consumed = before - food(player);
        double eligible = Math.min(Math.max(0, consumed), exertion.foodUnits);
        EXERTION.put(player, new Exertion(exertion.tick, exertion.family, Math.max(0, exertion.foodUnits - eligible)));
        award(player, action("hunger"), "consume_hunger", exertion.family, eligible);
    }
    public static double food(ServerPlayer player) { return player.getFoodData().getFoodLevel() + player.getFoodData().getSaturationLevel(); }
    public static void foodRestored(ServerPlayer player, double before, ItemStack food) {
        award(player, action("food"), "restore_hunger", itemSignature(food), food(player) - before);
    }
    public static void experience(ServerPlayer player, int before) {
        award(player, experienceRoot(), "gain_experience", "earned_experience", Math.max(0L, (long) player.totalExperience - before));
    }
    public static String experienceRoot() { String root = EXPERIENCE_ROOT.get(); return root == null ? action("xp") : root; }
    public static void withExperienceRoot(String root, Runnable action) {
        String previous = EXPERIENCE_ROOT.get(); EXPERIENCE_ROOT.set(root);
        try { action.run(); } finally { if (previous == null) EXPERIENCE_ROOT.remove(); else EXPERIENCE_ROOT.set(previous); }
    }

    /** Observed displacement after the server accepted a native movement packet. */
    public static void moved(ServerPlayer player, Vec3 before, String dimension) {
        if (!eligible(player)) return;
        Movement movement = MOVEMENT.get(player);
        long tick = player.level().getGameTime();
        if (movement == null || !movement.intent || tick - movement.intentTick > reference("movement_intent_timeout_ticks")) return;
        if (!eligible(player) || player.isPassenger() || player.isSleeping()
                || !dimension.equals(player.level().dimension().location().toString()) || player.hurtTime > 0) return;
        Vec3 delta = player.position().subtract(before);
        String mode = player.isSwimming() ? "swim" : player.isFallFlying() || player.getAbilities().flying ? "fly"
                : player.isSprinting() && !player.isInWater() && !player.onClimbable() ? "run" : "walk";
        double distance = mode.equals("run") ? delta.horizontalDistance() : delta.length();
        // Native speed/collision validation already ran. One tick cannot claim more than a chunk width;
        // coordinate discontinuities/teleports are discarded, not clipped into movement progress.
        if (!validDistance(distance, reference("movement_max_blocks_per_tick")) || delta.dot(player.getLookAngle()) <= 0) return;
        if (movement.distanceTick != tick) { movement.distanceTick = tick; movement.distance = 0; }
        movement.distance += distance;
        if (movement.distance > reference("movement_max_blocks_per_tick")) return;
        int regionSize = (int) reference("movement_region_blocks");
        String region = dimension + ":" + Math.floorDiv(player.blockPosition().getX(), regionSize)
                + ":" + Math.floorDiv(player.blockPosition().getZ(), regionSize);
        String root = SESSION + ":movement:" + player.getUUID() + ":" + tick;
        if (!mode.equals("walk")) {
            award(player, root, mode, mode + ":" + region, movement.distance);
            exert(player, mode);
        }
        var saved = EssenceSavedData.get(player.server);
        var data = saved.getPlayerData(player.getUUID());
        var chapter = com.mistaboom.essence_ascendance.config.EssenceConfigManager.runtime().attunement().chapter(data.getTier().id().toString());
        if (chapter == null) return;
        var state = data.attunement(); state.chapter(chapter.id());
        if (state.discover("dimension:" + dimension)) {
            award(player, root, "explore_dimension", dimension, 1);
            saved.setDirty();
        }
        player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
            String biome = key.location().toString();
            if (state.discover("biome:" + biome)) {
                award(player, root, "explore_biome", biome, 1);
                saved.setDirty();
            }
        });
    }
    public static boolean validDistance(double distance, double maximum) {
        return Double.isFinite(distance) && distance > 0 && distance <= maximum;
    }
    public static void beginHarvest(ServerPlayer player, BlockPos position, BlockState state, boolean container) {
        HARVEST.get().push(new HarvestFrame(player, position.immutable(), state, action("harvest"), container, beginExertion(player, "harvest")));
    }
    public static void spawnedHarvest(Entity entity) {
        HarvestFrame frame = HARVEST.get().peek();
        if (frame != null && entity instanceof ItemEntity item) frame.value += value(item.getItem());
    }
    public static void finishHarvest(boolean completed) {
        Deque<HarvestFrame> frames = HARVEST.get();
        HarvestFrame frame = frames.poll();
        if (frames.isEmpty()) HARVEST.remove();
        if (frame == null) return;
        try {
            if (!completed || frame.container) return;
            boolean crop = PlayerAttributedBlockHarvestService.isEligibleMatureCrop(frame.state);
            if (!crop && PlayerAttributedBlockHarvestService.isCrop(frame.state)) return;
            award(frame.player, frame.root, crop ? "harvest_crops" : "gather_resource_blocks",
                    BuiltInRegistries.BLOCK.getKey(frame.state.getBlock()).toString(), frame.value);
            if (frame.value > 0) exert(frame.player, "harvest");
        } finally { endExertion(frame.exertion); }
    }
    /** Installed generated payout already incorporates yield eligibility, farm and conservation policy. */
    public static double value(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        double units = ItemEssenceMappingRegistry.resolveDissolution(stack).values().stream().mapToDouble(Long::doubleValue).sum();
        return Math.min(Long.MAX_VALUE, units / FractionalAmountService.SCALE * stack.getCount());
    }
    private static double reference(String key) {
        return com.mistaboom.essence_ascendance.config.EssenceConfigManager.runtime().attunement().references().get(key);
    }
    public static String itemSignature(ItemStack stack) {
        // Durability, arbitrary names and repair-cost counters are not new recipes/sources.
        if (stack.isEmpty()) return "empty";
        StringBuilder signature = new StringBuilder(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        for (var type : List.of(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)) {
            var enchantments = stack.get(type);
            if (enchantments != null) enchantments.keySet().stream()
                    .map(holder -> holderId(holder) + "=" + enchantments.getLevel(holder)).sorted()
                    .forEach(enchantment -> signature.append("|enchantment:").append(enchantment));
        }
        var potion = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (potion != null) {
            potion.potion().ifPresent(holder -> signature.append("|potion:").append(holderId(holder)));
            potion.customEffects().stream().map(effect -> holderId(effect.getEffect()) + ":" + effect.getAmplifier()
                    + ":" + effect.getDuration()).sorted().forEach(effect -> signature.append("|effect:").append(effect));
        }
        var banner = stack.get(net.minecraft.core.component.DataComponents.BANNER_PATTERNS);
        if (banner != null) for (var layer : banner.layers())
            signature.append("|pattern:").append(holderId(layer.pattern())).append(':').append(layer.color().getName());
        return sourceSignature(signature.toString());
    }
    private static String holderId(net.minecraft.core.Holder<?> holder) {
        return holder.unwrapKey().map(key -> key.location().toString()).orElse("unregistered");
    }
    private record Exertion(long tick, String family, double foodUnits) { }
    private record EffectSource(net.minecraft.world.effect.MobEffectInstance instance, String signature) { }
    public static final class ExertionScope {
        private final ServerPlayer player; private final String family; private final double before; private boolean confirmed;
        private ExertionScope(ServerPlayer player, String family, double before) { this.player = player; this.family = family; this.before = before; }
    }
    private static final class Movement {
        boolean intent; long intentTick; long distanceTick = Long.MIN_VALUE; double distance;
    }
    private static final class HarvestFrame {
        final ServerPlayer player; final BlockPos position; final BlockState state; final String root; final boolean container;
        final ExertionScope exertion; double value;
        HarvestFrame(ServerPlayer player, BlockPos position, BlockState state, String root, boolean container, ExertionScope exertion) {
            this.player = player; this.position = position; this.state = state; this.root = root; this.container = container; this.exertion = exertion;
        }
    }
    private static final class DamageFrame {
        final LivingEntity target; final DamageSource source; final double requested; final String root;
        final double exhaustionBefore;
        final ServerPlayer owner; final boolean reflected; double loss; double healthLoss; double blocked; double prevented;
        DamageFrame(LivingEntity target, DamageSource source, float requested, String root) {
            this.target = target; this.source = source; this.requested = Float.isFinite(requested) ? requested : 0;
            this.root = root; this.owner = owner(source); this.reflected = EquipmentDamageService.isReflectionInProgress()
                    || source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)
                    || SkillProcDamageService.current() != null && SkillProcDamageService.current().kind().reflectedOutcome();
            this.exhaustionBefore = target instanceof ServerPlayer player ? player.getFoodData().getExhaustionLevel() : 0;
        }
    }
}
