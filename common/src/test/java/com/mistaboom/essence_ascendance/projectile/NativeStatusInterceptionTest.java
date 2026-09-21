package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.mixin.StatusEffectInstanceAccess;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.status.StatusInterceptionService;
import com.mistaboom.essence_ascendance.status.StatusOutcome;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Real native add/merge/remove/force and splash dispatch; only network callbacks and world storage are controlled. */
public final class NativeStatusInterceptionTest {
    private static int checks;
    private NativeStatusInterceptionTest() { }
    public static void run() throws ReflectiveOperationException {
        if (!Boolean.getBoolean("essence.projectile.nativeHookTest")) throw new IllegalStateException("Explicit no-world harness required");
        var fixture = new ProjectileNativeInterceptionTest.Fixture(EffectPlayer.class);
        var player = (EffectPlayer)fixture.player;
        var source = fixture.player(EffectPlayer.class, new Vec3(0, 0, 2), "status_source");
        setup(fixture, player); setup(fixture, source);
        select(fixture, player, SkillIds.STATUS_MIRROR);
        var poison = MobEffects.POISON;
        check(!player.addEffect(new MobEffectInstance(poison, 100, 1, true, false, true), source), "confirmed Mirror returns final not-retained result");
        var event = last(player);
        check(event.removed() && event.prevented() && !player.hasEffect(poison), "actual native first addition is immediately removed");
        check(source.hasEffect(poison) && source.getEffect(poison).getDuration() == 100 && source.getEffect(poison).getAmplifier() == 1,
                "normal target native addition receives exact bounded copy");
        check(source.lastSource == player && event.attribution().equals(player.getUUID()), "copy preserves defender attribution through native API");
        check(event.requested().getFirst().ambient() && !event.copied().getFirst().particles() && event.copied().getFirst().icon(), "presentation flags preserved");
        long until = event.cooldownUntil();
        check(until == fixture.level.tick + SkillEffectRuntime.resolvedSettings(player).status().mirrorCooldownTicks(), "one cooldown committed at confirmed interception");
        check(player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100), source), "next harmful effect lands during cooldown");
        check(last(player).cooldownUntil() == until && !last(player).removed(), "no duplicate cooldown from second application");
        check(SkillEffectRuntime.hudSnapshot(player).entries().stream().anyMatch(e -> e.sourceSkill().equals(SkillIds.STATUS_MIRROR) && e.active()), "Mirror exposes shared active cooldown card");
        player.removeAllEffects(); source.removeAllEffects(); fixture.level.tick = until;
        check(!player.addEffect(new MobEffectInstance(poison, -1, 30), source), "exact cooldown boundary allows next interception");
        check(source.getEffect(poison).getDuration() == SkillEffectRuntime.resolvedSettings(player).status().mirrorMaximumDurationTicks()
                && source.getEffect(poison).getAmplifier() == SkillEffectRuntime.resolvedSettings(player).status().mirrorMaximumAmplifier(), "infinite duration and amplifier transfer bounded");

        reset(fixture, player); source.removeAllEffects();
        check(player.addEffect(new MobEffectInstance(poison, 100), null), "sourceless command/environment effect remains with Mirror");
        check(last(player).cooldownUntil() == 0 && last(player).relationship().equals("no_responsible_living_source"), "sourceless Mirror grants no cooldown");
        check(!player.addEffect(new MobEffectInstance(poison, 50), source), "weaker shorter native rejection remains false");
        check(!last(player).removed() && last(player).cooldownUntil() == 0 && player.getEffect(poison).getDuration() == 100,
                "rejected same-effect probe cannot intercept or cleanse existing effect");
        check(!player.addEffect(new MobEffectInstance(poison, 200), source), "same-effect refresh can trigger Mirror");
        check(player.getEffect(poison).getDuration() == 100 && source.getEffect(poison).getDuration() == 200,
                "refresh interception restores prior defender state and transfers only request");

        try {
            var cures = MobEffectInstance.class.getMethod("getCures");
            reset(fixture, player); source.removeAllEffects();
            player.addEffect(new MobEffectInstance(poison, 100), null);
            ((java.util.Set<?>)cures.invoke(player.getEffect(poison))).clear();
            player.addEffect(new MobEffectInstance(poison, 200), source);
            check(player.getEffect(poison).getDuration() == 100 && ((java.util.Set<?>)cures.invoke(player.getEffect(poison))).isEmpty(),
                    "NeoForge prior per-instance custom cure set survives native restoration");
        } catch (NoSuchMethodException absentOnFabric) {
            check(!MobEffectInstance.class.getName().isEmpty(), "Fabric has no NeoForge cure API dependency");
        }

        reset(fixture, player); source.removeAllEffects();
        player.addEffect(new MobEffectInstance(poison, 100, 2), null);
        check(!player.addEffect(new MobEffectInstance(poison, 200, 0), source), "hidden weaker native insertion has false return");
        event = last(player);
        check(event.nativeAcceptance().equals("native_false_hidden_chain_changed") && event.removed(), "observable hidden insertion is a real Mirror outcome despite native false");
        check(player.getEffect(poison).getAmplifier() == 2 && hidden(player.getEffect(poison)) == null,
                "hidden insertion removed while stronger prior state remains");
        check(source.getEffect(poison).getAmplifier() == 0 && source.getEffect(poison).getDuration() == 200, "only received hidden instance copied");

        reset(fixture, player); source.removeAllEffects(); source.reject = true;
        player.addEffect(new MobEffectInstance(poison, 100), source);
        check(!player.hasEffect(poison) && !source.hasEffect(poison) && last(player).cooldownUntil() > fixture.level.tick,
                "defender remains protected and spends exactly one cooldown when source native acceptance rejects copy");
        check(last(player).targetAcceptance().equals("native_rejected_unchanged"), "target native rejection is explicit");
        source.reject = false;

        reset(fixture, player); source.removeAllEffects();
        ProjectileNativeInterceptionTest.set(net.minecraft.server.MinecraftServer.class, fixture.server, "pvp", false);
        check(player.addEffect(new MobEffectInstance(poison, 100), source) && last(player).cooldownUntil() == 0,
                "PvP-disabled source is never reflected or removed");
        ProjectileNativeInterceptionTest.set(net.minecraft.server.MinecraftServer.class, fixture.server, "pvp", true);
        reset(fixture, player);
        var team = fixture.level.scoreboard.addPlayerTeam("status_allies");
        fixture.level.scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        fixture.level.scoreboard.addPlayerToTeam(source.getScoreboardName(), team);
        check(player.addEffect(new MobEffectInstance(poison, 100), source) && last(player).cooldownUntil() == 0,
                "native team relationship prevents reflecting an ally");
        fixture.level.scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
        fixture.level.scoreboard.removePlayerFromTeam(source.getScoreboardName(), team);
        reset(fixture, player);
        check(player.addEffect(new MobEffectInstance(poison, 100), player) && !last(player).removed(), "self source never mirrored");
        reset(fixture, player);
        var arrow = fixture.arrow(net.minecraft.world.entity.projectile.Arrow.class); arrow.setOwner(source);
        player.addEffect(new MobEffectInstance(poison, 100), arrow);
        check(last(player).responsibleSource().equals(source.getUUID()) && last(player).directEntity().equals(arrow.getUUID())
                && last(player).removed(), "native projectile owner resolved separately from direct arrow identity");
        reset(fixture, player); source.removeAllEffects();
        var cloud = ProjectileNativeInterceptionTest.instance(net.minecraft.world.entity.AreaEffectCloud.class);
        ProjectileNativeInterceptionTest.initializeEntity(cloud, EntityType.AREA_EFFECT_CLOUD, fixture.level, Vec3.ZERO, new AABB(-1, 0, -1, 1, 1, 1));
        cloud.setOwner(source);
        player.addEffect(new MobEffectInstance(poison, 100), cloud);
        check(last(player).responsibleSource().equals(source.getUUID()) && last(player).directEntity().equals(cloud.getUUID())
                && last(player).removed(), "native cloud living owner resolved through explicit owner contract");
        reset(fixture, player);
        cloud.setOwner(null);
        check(player.addEffect(new MobEffectInstance(poison, 100), cloud) && last(player).cooldownUntil() == 0,
                "ownerless cloud is not assigned an invented shooter");
        reset(fixture, player);
        var pet = ProjectileNativeInterceptionTest.instance(Pet.class);
        ProjectileNativeInterceptionTest.initializeEntity(pet, EntityType.WOLF, fixture.level, new Vec3(1, 0, 0), new AABB(.5, 0, -.5, 1.5, 1, .5));
        ProjectileNativeInterceptionTest.defineData(pet, net.minecraft.world.entity.TamableAnimal.class);
        pet.setTame(true, false);
        pet.owner = player;
        check(player.addEffect(new MobEffectInstance(poison, 100), pet) && last(player).cooldownUntil() == 0,
                "owned pet source uses shared owner relationship and never reflects against player");
        reset(fixture, player);

        reset(fixture, player); player.reject = true;
        check(!player.addEffect(new MobEffectInstance(poison, 100), source) && last(player).cooldownUntil() == 0,
                "defender native rejection has no intercepted outcome or cooldown");
        player.reject = false;
        player.addEffect(new MobEffectInstance(poison, 0), source);
        check(last(player).cooldownUntil() == 0 && !last(player).removed(), "zero duration follows native inert semantics without Mirror consumption");

        reset(fixture, player); select(fixture, source, SkillIds.STATUS_MIRROR);
        player.addEffect(new MobEffectInstance(poison, 100), source);
        check(!player.hasEffect(poison) && source.hasEffect(poison), "two Mirror players cannot bounce a copied status");
        check(last(source).recursionDepth() == 1 && last(source).cooldownUntil() == 0 && !last(source).removed(), "secondary recipient starts no Mirror cooldown");
        reset(fixture, player); source.removeAllEffects(); select(fixture, source, SkillIds.PURE_STATE);
        player.addEffect(new MobEffectInstance(poison, 100), source);
        check(!player.hasEffect(poison) && !source.hasEffect(poison) && last(player).cooldownUntil() > fixture.level.tick,
                "Pure State source rejects copied effect while Mirror defender remains protected on cooldown");
        check(last(source).prevented() && last(source).recursionDepth() == 1 && last(source).cooldownUntil() == 0,
                "recipient Pure State prevention has no Mirror cooldown or copy chain");
        select(fixture, source, SkillIds.STATUS_MIRROR);
        reset(fixture, player); source.removeAllEffects();
        EquipmentDamageService.withSecondarySkillDamage(() -> player.addEffect(new MobEffectInstance(poison, 100), source));
        check(player.hasEffect(poison) && last(player).cooldownUntil() == 0, "other secondary skill effects cannot trigger Mirror");

        select(fixture, player, SkillIds.PURE_STATE);
        var existing = player.getEffect(poison);
        check(existing != null && existing.getDuration() == 100, "activating Pure State does not cleanse existing harmful effects");
        check(!player.addEffect(new MobEffectInstance(poison, 500, 4), source) && player.getEffect(poison) == existing,
                "Pure State prevents same-effect upgrade while preserving prior instance and hidden chain");
        player.removeAllEffects();
        check(!player.addEffect(new MobEffectInstance(MobEffects.WITHER, -1), null), "Pure State blocks sourceless infinite harmful effects");
        check(player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100), source), "beneficial native effects preserved");
        check(player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100), source), "neutral native effects preserved");
        check(!last(player).prevented() && last(player).category().equals("NEUTRAL"), "neutral category distinct from harmful");
        EquipmentDamageService.withSecondarySkillDamage(() -> player.addEffect(new MobEffectInstance(poison, 100), source));
        check(!player.hasEffect(poison) && last(player).recursionDepth() == 1 && last(player).cooldownUntil() == 0,
                "Pure State also prevents secondary harm without cooldown or transfer");
        player.forceAddEffect(new MobEffectInstance(poison, 100), source);
        check(!player.hasEffect(poison), "common native forceAddEffect boundary cannot bypass Pure State");
        check(SkillEffectRuntime.hudSnapshot(player).entries().stream().anyMatch(e -> e.sourceSkill().equals(SkillIds.PURE_STATE) && e.active()),
                "Pure State briefly reports actual prevented harmful effects");
        check(SkillEffectRuntime.hudSnapshot(player).entries().stream().noneMatch(e -> e.sourceSkill().equals(SkillIds.STATUS_MIRROR)),
                "Switching immediately removes Mirror card");

        var registry = new MappedRegistry<MobEffect>(Registries.MOB_EFFECT, Lifecycle.stable());
        var modded = registry.register(ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.fromNamespaceAndPath("test", "harmful")),
                new CustomEffect(MobEffectCategory.HARMFUL), RegistrationInfo.BUILT_IN);
        registry.freeze();
        check(!player.addEffect(new MobEffectInstance(modded, 100), source) && last(player).effect().equals("test:harmful"),
                "arbitrary registered modded harmful category participates by exact registry identity");

        // Controlled native instantaneous callback: direct fallback for the vanilla implementation.
        MobEffects.HARM.value().applyInstantenousEffect(source, source, player, 0, 1);
        check(last(player).nativeAcceptance().equals("eligible_instant_preflight_intercepted"), "vanilla overriding instant method intercepted before irreversible damage");
        check(!last(player).removed() && last(player).prevented() && last(player).cooldownUntil() == 0, "instant Pure State uses prevention not fake removal/cooldown");

        var customInstant = new CustomInstant();
        var potion = ProjectileNativeInterceptionTest.instance(ThrownPotion.class);
        ProjectileNativeInterceptionTest.initializeEntity(potion, EntityType.POTION, fixture.level, Vec3.ZERO, new AABB(-.2, 0, -.2, .2, .4, .2));
        potion.setOwner(source);
        var splash = ThrownPotion.class.getDeclaredMethod("applySplash", Iterable.class, Entity.class); splash.setAccessible(true);
        // Move all other fixture entities outside the splash; source remains the real owner.
        source.setPos(10, 0, 0);
        splash.invoke(potion, List.of(new MobEffectInstance(Holder.direct(customInstant), 1)), player);
        check(customInstant.calls == 0 && last(player).prevented(), "real native splash bridge intercepts a modded override that never calls the base method");
        select(fixture, player, SkillIds.STATUS_MIRROR);
        splash.invoke(potion, List.of(new MobEffectInstance(Holder.direct(customInstant), 1)), player);
        check(customInstant.calls == 1 && customInstant.lastTarget == source && customInstant.lastOwner == player,
                "real generic splash bridge copies modded instant directly to responsible source with defender attribution");
        check(customInstant.secondary && last(player).cooldownUntil() > fixture.level.tick, "instant transfer remains secondary and starts one preflight cooldown");

        var periodic = new PeriodicEffect(MobEffectCategory.HARMFUL);
        var periodicInstance = new MobEffectInstance(Holder.direct(periodic), 20);
        check(periodicInstance.tick(source, () -> { }), "actual native periodic instance continues normally");
        check(periodic.calls == 1 && periodic.secondary, "periodic native callbacks are derived secondary even without persisted origin");
        check(!EquipmentDamageService.isSecondarySkillDamage(), "periodic secondary scope always closes after native callback");
        for (var category : List.of(MobEffectCategory.BENEFICIAL, MobEffectCategory.NEUTRAL)) {
            var ordinary = new PeriodicEffect(category);
            new MobEffectInstance(Holder.direct(ordinary), 20).tick(source, () -> { });
            check(ordinary.calls == 1 && !ordinary.secondary, "ordinary " + category + " callback remains outside secondary scope");
        }

        SkillEffectRuntime.reset(player);
        check(StatusInterceptionService.lastOutcome(player).isEmpty(), "lifecycle reset clears transient cooldown/outcome without clearing native effects");
        fixture.close(); SkillEffectRuntime.forget(source); CommittedSkillService.forget(source);
        System.out.println("Native status interception checks passed: " + checks + " (native effect merge/removal/forced application/splash; no live world)");
    }
    private static MobEffectInstance hidden(MobEffectInstance instance) { return ((StatusEffectInstanceAccess)(Object)instance).essenceAscendance$hiddenEffect(); }
    private static StatusOutcome last(ServerPlayer player) { return StatusInterceptionService.lastOutcome(player).orElseThrow(); }
    private static void setup(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player) {
        var data = f.saved.getPlayerData(player.getUUID()); data.setTier(AscendanceTiers.TRANSCENDENT);
        data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.STATUS_MIRROR), SkillRegistry.require(SkillIds.PURE_STATE)));
        SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);
    }
    private static void select(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player, ResourceLocation skill) {
        f.saved.getPlayerData(player.getUUID()).setLoadoutSelection(SkillGroups.DEFENSE_STATUS, skill);
        SkillEffectRuntime.refresh(player); check(CommittedSkillService.effectiveIds(player).contains(skill), "real committed status selection effective " + skill);
    }
    private static void reset(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player) {
        player.removeAllEffects(); SkillEffectRuntime.reset(player); select(f, player, SkillIds.STATUS_MIRROR);
    }
    private static void check(boolean okay, String message) { checks++; if (!okay) throw new AssertionError(message); }
    /** Packet delivery is replaced; real native effect map/merge/removal and attribute callbacks still execute. */
    static class EffectPlayer extends ServerPlayer {
        boolean reject; Entity lastSource;
        private EffectPlayer() { super(null, null, null, null); throw new AssertionError("No world/player construction"); }
        @Override public boolean canBeAffected(MobEffectInstance effect) { return !reject && super.canBeAffected(effect); }
        @Override protected void onEffectAdded(MobEffectInstance effect, Entity source) {
            lastSource = source; effect.getEffect().value().addAttributeModifiers(getAttributes(), effect.getAmplifier());
        }
        @Override protected void onEffectUpdated(MobEffectInstance effect, boolean refresh, Entity source) {
            lastSource = source;
            if (refresh) { effect.getEffect().value().removeAttributeModifiers(getAttributes()); effect.getEffect().value().addAttributeModifiers(getAttributes(), effect.getAmplifier()); }
        }
        @Override protected void onEffectRemoved(MobEffectInstance effect) { effect.getEffect().value().removeAttributeModifiers(getAttributes()); }
    }
    static class CustomEffect extends MobEffect { CustomEffect(MobEffectCategory category) { super(category, 0); } }
    static final class CustomInstant extends CustomEffect {
        int calls; LivingEntity lastTarget; Entity lastOwner; boolean secondary;
        CustomInstant() { super(MobEffectCategory.HARMFUL); }
        @Override public boolean isInstantenous() { return true; }
        @Override public void applyInstantenousEffect(Entity direct, Entity owner, LivingEntity target, int amplifier, double potency) {
            calls++; lastTarget = target; lastOwner = owner; secondary = EquipmentDamageService.isSecondarySkillDamage();
        }
    }
    static final class PeriodicEffect extends CustomEffect {
        int calls; boolean secondary;
        PeriodicEffect(MobEffectCategory category) { super(category); }
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }
        @Override public boolean applyEffectTick(LivingEntity target, int amplifier) {
            calls++; secondary = EquipmentDamageService.isSecondarySkillDamage(); return true;
        }
    }
    /** Ownership boundary only; avoids constructing or ticking a native creature/world. */
    static final class Pet extends net.minecraft.world.entity.TamableAnimal {
        LivingEntity owner;
        private Pet() { super(EntityType.WOLF, null); throw new AssertionError("No creature/world construction"); }
        @Override public LivingEntity getOwner() { return owner; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean isFood(net.minecraft.world.item.ItemStack stack) { return false; }
        @Override public net.minecraft.world.entity.AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level,
                net.minecraft.world.entity.AgeableMob other) { return null; }
    }
}
