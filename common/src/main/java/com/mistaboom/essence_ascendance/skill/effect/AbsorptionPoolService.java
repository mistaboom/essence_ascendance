package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * Shared transient absorption ownership. Native yellow hearts still absorb damage and synchronize normally.
 * Only this service's remaining points/modifiers are removed on expiry or lifecycle cleanup. Status-effect
 * callbacks see the external reservoir alone, so native effect refresh/removal cannot consume owned hearts.
 */
public final class AbsorptionPoolService {
    private static final Map<ServerPlayer, Account> ACCOUNTS = new WeakHashMap<>();
    private static final ThreadLocal<Deque<DamageFrame>> DAMAGE = ThreadLocal.withInitial(ArrayDeque::new);
    private AbsorptionPoolService() { }
    private static final class Account {
        final AbsorptionPoolLedger<ResourceLocation> ledger = new AbsorptionPoolLedger<>();
        int editing, suspended;
    }
    private record DamageFrame(ServerPlayer player, DamageSource source, LinkedHashSet<ResourceLocation> broken) { }

    public static double amount(ServerPlayer player, ResourceLocation source) {
        Account account = ACCOUNTS.get(player);
        if (account == null) return 0;
        if (account.suspended == 0) account.ledger.fit(player.getAbsorptionAmount());
        return account.ledger.amount(source);
    }
    public static double capacity(ServerPlayer player, ResourceLocation source) {
        Account account = ACCOUNTS.get(player);
        return account == null ? 0 : account.ledger.capacity(source);
    }
    public static void configure(ServerPlayer player, ResourceLocation source, double capacity) {
        Account account = ACCOUNTS.computeIfAbsent(player, ignored -> new Account());
        double before = player.getAbsorptionAmount();
        if (account.suspended == 0) account.ledger.fit(before);
        double removed = account.ledger.configure(source, capacity);
        write(player, account, before - removed);
    }
    public static double grant(ServerPlayer player, ResourceLocation source, double requested) {
        Account account = ACCOUNTS.get(player);
        if (account == null || account.suspended > 0 || !player.isAlive() || player.isRemoved()) return 0;
        double before = player.getAbsorptionAmount();
        account.ledger.fit(before);
        double previousOwned = account.ledger.amount(source);
        double added = account.ledger.grant(source, requested);
        account.editing++;
        try {
            modifiers(player, account);
            player.setAbsorptionAmount((float) (before + added));
            double actual = Math.max(0, player.getAbsorptionAmount() - before);
            // Return rejected capacity to the GRANTING source before a general fit. Otherwise a
            // hard-cap clamp could silently consume an older pool and turn it into unowned hearts.
            account.ledger.withdraw(source, Math.max(0, added - actual));
            account.ledger.fit(player.getAbsorptionAmount());
            modifiers(player, account);
            return Math.max(0, account.ledger.amount(source) - previousOwned);
        } finally { account.editing--; }
    }

    /** Administrative expiry/decay/capacity trim: explicitly not an accepted damage event. */
    public static double withdraw(ServerPlayer player, ResourceLocation source, double requested) {
        Account account = ACCOUNTS.get(player);
        if (account == null) return 0;
        double before = player.getAbsorptionAmount();
        if (account.suspended == 0) account.ledger.fit(before);
        double removed = account.ledger.withdraw(source, requested);
        write(player, account, before - removed);
        return removed;
    }
    public static void remove(ServerPlayer player, ResourceLocation source) {
        Account account = ACCOUNTS.get(player);
        if (account == null) { modifier(player, source, 0); return; }
        double before = player.getAbsorptionAmount();
        if (account.suspended == 0) account.ledger.fit(before);
        double removed = account.ledger.remove(source);
        modifier(player, source, 0);
        write(player, account, before - removed);
        if (account.ledger.empty() && account.suspended == 0) ACCOUNTS.remove(player);
    }

    /**
     * Observe the setter on its declaring LivingEntity class, including inherited player calls.
     * Non-server-player writes pass through untouched; nested hits retain separate break identities.
     */
    public static void observeWrite(LivingEntity entity, Runnable nativeWrite) {
        if (!(entity instanceof ServerPlayer player)) { nativeWrite.run(); return; }
        Account account = ACCOUNTS.get(player);
        if (account == null || account.editing > 0 || account.suspended > 0) { nativeWrite.run(); return; }
        double before = player.getAbsorptionAmount();
        nativeWrite.run();
        double after = player.getAbsorptionAmount();
        DamageFrame frame = DAMAGE.get().peek();
        if (frame != null && frame.player() == player && after < before) {
            frame.broken().addAll(account.ledger.consume(before - after).depleted());
        } else account.ledger.fit(after);
        modifiers(player, account);
    }
    public static boolean damage(LivingEntity entity, DamageSource source, BooleanSupplier nativeDamage) {
        if (!(entity instanceof ServerPlayer player)) return nativeDamage.getAsBoolean();
        Deque<DamageFrame> stack = DAMAGE.get();
        DamageFrame frame = new DamageFrame(player, source, new LinkedHashSet<>());
        stack.push(frame);
        boolean accepted;
        try { accepted = nativeDamage.getAsBoolean(); }
        finally { stack.pop(); if (stack.isEmpty()) DAMAGE.remove(); }
        if (accepted && player.isAlive() && !player.isRemoved())
            for (ResourceLocation pool : frame.broken()) SkillEffectRuntime.onAbsorptionDepleted(player, source, pool);
        return accepted;
    }

    /** Scope only absorption-capacity effects; ordinary potion callbacks still see all native hearts. */
    public static boolean externalEffectResult(LivingEntity entity, MobEffectInstance effect, BooleanSupplier nativeEffect) {
        return changesAbsorption(entity, effect) ? externalEffectResult(entity, nativeEffect) : nativeEffect.getAsBoolean();
    }
    public static void externalEffect(LivingEntity entity, MobEffectInstance effect, Runnable nativeEffect) {
        if (changesAbsorption(entity, effect)) externalEffect(entity, nativeEffect);
        else nativeEffect.run();
    }
    private static boolean changesAbsorption(LivingEntity entity, MobEffectInstance effect) {
        if (!(entity instanceof ServerPlayer player)) return false;
        Account account = ACCOUNTS.get(player);
        if (account == null || account.suspended > 0 || account.ledger.total() <= 0) return false;
        boolean[] affected = new boolean[1];
        // Discover the mechanic from native modifiers, not a hard-coded vanilla effect ID. Explicit
        // non-potion integrations may use the unqualified externalEffect scope below.
        effect.getEffect().value().createModifiers(effect.getAmplifier(), (attribute, modifier) -> {
            if (attribute.equals(Attributes.MAX_ABSORPTION)) affected[0] = true;
        });
        return affected[0];
    }

    public static boolean externalEffectResult(LivingEntity entity, BooleanSupplier nativeEffect) {
        boolean[] result = new boolean[1];
        externalEffect(entity, () -> result[0] = nativeEffect.getAsBoolean());
        return result[0];
    }

    /** Native potion/effect changes cannot mistake a skill's borrowed hearts/capacity for their own. */
    public static void externalEffect(LivingEntity entity, Runnable nativeEffect) {
        if (!(entity instanceof ServerPlayer player)) { nativeEffect.run(); return; }
        Account account = ACCOUNTS.get(player);
        if (account == null || account.suspended > 0 || account.ledger.total() <= 0) { nativeEffect.run(); return; }
        account.ledger.fit(player.getAbsorptionAmount());
        double external = account.ledger.external(player.getAbsorptionAmount());
        account.suspended++;
        try {
            account.ledger.keys().forEach(key -> modifier(player, key, 0));
            player.setAbsorptionAmount((float) external);
            nativeEffect.run();
        } finally {
            double externalAfter = player.getAbsorptionAmount();
            account.suspended--;
            write(player, account, externalAfter + account.ledger.total());
            // At the engine's hard absorption limit, preserve existing external points first.
            account.ledger.fit(Math.max(0, player.getAbsorptionAmount() - externalAfter));
            modifiers(player, account);
        }
    }
    /** Transient pools must not reload as unowned permanent absorption after a save/restart. */
    public static void saveExternal(Player entity, CompoundTag tag) {
        if (!(entity instanceof ServerPlayer player)) return;
        Account account = ACCOUNTS.get(player);
        if (account != null && account.suspended == 0) {
            account.ledger.fit(player.getAbsorptionAmount());
            tag.putFloat("AbsorptionAmount", (float) account.ledger.external(player.getAbsorptionAmount()));
        }
    }
    public static void clearAll() {
        for (var entry : List.copyOf(ACCOUNTS.entrySet()))
            for (ResourceLocation key : entry.getValue().ledger.keys()) remove(entry.getKey(), key);
        ACCOUNTS.clear();
        DAMAGE.remove();
    }
    private static void write(ServerPlayer player, Account account, double total) {
        if (account.suspended > 0) return;
        account.editing++;
        try {
            modifiers(player, account);
            float value = (float) Math.max(0, total);
            if (Float.compare(player.getAbsorptionAmount(), value) != 0) player.setAbsorptionAmount(value);
            account.ledger.fit(player.getAbsorptionAmount());
            modifiers(player, account);
        } finally { account.editing--; }
    }
    private static void modifiers(ServerPlayer player, Account account) {
        if (account.suspended == 0)
            for (ResourceLocation key : account.ledger.keys()) modifier(player, key, account.ledger.amount(key));
    }
    private static void modifier(ServerPlayer player, ResourceLocation source, double owned) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(source.getNamespace(), "absorption_pool/" + source.getPath());
        SkillEffectAttributes.apply(player, Attributes.MAX_ABSORPTION, id, owned, AttributeModifier.Operation.ADD_VALUE);
    }
}
