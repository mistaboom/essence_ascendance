package com.mistaboom.essence_ascendance.config;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.equipment.AscendanceToolMiningService;
import com.mistaboom.essence_ascendance.progression.HarvestProgressionSafety;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import dev.architectury.platform.Platform;
import java.nio.file.Path;
import java.util.Objects;

/** Access to the resolved profile. Human TOML is consumed only by explicit generation. */
public final class EssenceConfigManager {
    public static final int CURRENT_CONFIG_VERSION = 1;
    private static volatile RuntimeBalanceDefinition serverRuntime;
    private static volatile RuntimeBalanceDefinition clientRuntime;
    private static RuntimeBalanceDefinition bootstrap;
    private EssenceConfigManager() {}

    public static EssenceServerConfig get() { return runtime().config(); }
    public static RuntimeBalanceDefinition runtime() {
        RuntimeBalanceDefinition active=serverRuntime;
        if(active!=null)return active;
        active=clientRuntime;
        if(active!=null)return active;
        if(bootstrap==null)bootstrap=RuntimeBalanceDefinition.bootstrap();
        return bootstrap;
    }
    public static RuntimeBalanceDefinition serverRuntime() { return serverRuntime; }
    public static RuntimeBalanceDefinition clientRuntime() { return clientRuntime; }
    public static boolean authoritativeReady() { return serverRuntime!=null; }
    public static SkillEffectBalanceSettings skillEffects() { return get().skillEffects(); }
    public static Path getConfigPath() { return Platform.getConfigFolder().resolve("essence_ascendance.toml"); }
    /** Registry initialization may require a harmless seed before recipes/loot are ready. */
    public static void load() {
        try { com.mistaboom.essence_ascendance.balance.config.BalanceInputs.scaffold(Platform.getConfigFolder()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException("Could not create balance TOML inputs",failure); }
        get();
        com.mistaboom.essence_ascendance.network.RuntimeBalanceSyncService.init();
    }
    public static void install(RuntimeBalanceDefinition runtime) {
        Objects.requireNonNull(runtime).validate();
        runtime.validateServerReferences();
        SkillBalanceRuntime.install(runtime.skillCurves());
        serverRuntime=runtime;
        com.mistaboom.essence_ascendance.balance.BalanceProfileRegistry.installRuntime(runtime.config().balanceProfile());
        AscendanceToolMiningService.invalidateCache();
        HarvestProgressionSafety.logWarnings(runtime.config());
    }
    /** The physical client never changes the integrated server's authoritative reference. */
    public static void installClient(RuntimeBalanceDefinition runtime) {
        Objects.requireNonNull(runtime).validate();
        clientRuntime=runtime;
        if(serverRuntime==null)SkillBalanceRuntime.install(runtime.skillCurves());
    }
    public static void clearClient() {
        clientRuntime=null;
        if(serverRuntime==null)SkillBalanceRuntime.clear();
    }
    public static void reset() {
        serverRuntime=null;
        com.mistaboom.essence_ascendance.balance.BalanceProfileRegistry.clearRuntime();
        com.mistaboom.essence_ascendance.network.RuntimeBalanceSyncService.clear();
        bootstrap=null;
        if(clientRuntime==null)SkillBalanceRuntime.clear();
        else SkillBalanceRuntime.install(clientRuntime.skillCurves());
        AscendanceToolMiningService.invalidateCache();
    }
}
