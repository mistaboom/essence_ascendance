package com.mistaboom.essence_ascendance.balance.capability;

import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.nbt.CompoundTag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Read-only loaded default-player contract. Never calls permissions, breaks blocks or constructs a player. */
public final class UltimineCapabilityProvider implements PackEvidenceProvider {
    public static final String SOURCE = "ftbultimine:shapeless_mining";
    private static final String API = "dev.ftb.mods.ftbultimine.";
    private static final String LIBRARY = "dev.ftb.mods.ftblibrary.config.manager.ConfigManager";
    public String id() { return "ftbultimine_capabilities"; }
    public List<String> dependencyModIds() { return List.of("ftbultimine"); }
    public Set<CapabilityAxis> capabilityAxes() { return Set.of(CapabilityAxis.VEIN_MINING); }
    public boolean requiredForGeneration() { return false; }
    public void collect(PackEvidenceContext context, EvidenceSink sink) { }
    public ProviderReadiness readiness(GenerationDataSnapshot inputs) {
        return InstalledCapabilityProviders.version(inputs.installedVersion("ftbultimine"), "2101.1.15",
                inputs.installedVersion("neoforge") != null,
                "Loaded standard-player shapeless batch limits, tool gates, exhaustion and cooldown; custom restrictions and rank overrides remain unproven");
    }
    public record Configuration(int maxBlocks, double exhaustionMultiplier, double experiencePerBlock,
                                long cooldownTicks, boolean requireTool, boolean requireCorrectTool, int preventToolBreak) {
        public boolean supported() {
            return maxBlocks > 1 && maxBlocks <= 32768 && Double.isFinite(exhaustionMultiplier) && exhaustionMultiplier >= 0
                    && experiencePerBlock == 0 && cooldownTicks >= 0 && preventToolBreak >= 0;
        }
        public double exhaustionPerTarget() { return .005 * (1 + exhaustionMultiplier); }
    }
    private record ToolWitness(String item, int capacity) { }

    public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
        sink.analyzed();
        String libraryVersion = context.inputs().installedVersion("ftblibrary");
        if (!Set.of("2101.1.35", "2101.1.36").contains(Objects.toString(libraryVersion, ""))) {
            unknown(sink, "Unaudited FTB Library effective-config API: " + libraryVersion); return;
        }
        Object manager = staticField(LIBRARY, "INSTANCE");
        Map<?, ?> tracked = (Map<?, ?>) privateField(manager, "trackedConfigs");
        Object loaded = tracked.get("ftbultimine-server");
        if (loaded == null || privateField(loaded, "loadedFrom") == null
                || privateField(loaded, "config") != staticField(API + "config.FTBUltimineServerConfig", "CONFIG")) {
            sink.candidate(SOURCE, id(), "Effective Ultimine server config has not been loaded; defaults are not evidence",
                    capabilityAxes(), CapabilitySink.Reason.DATA_NOT_READY); return;
        }
        Configuration config = new Configuration(integer("MAX_BLOCKS"), number("EXHAUSTION_PER_BLOCK"), number("EXPERIENCE_PER_BLOCK"),
                ((Number) setting("ULTIMINE_COOLDOWN")).longValue(), (boolean) setting("REQUIRE_TOOL"),
                (boolean) setting("REQUIRE_VALID_TOOL_FOR_BLOCK"), integer("PREVENT_TOOL_BREAK"));
        var definition = BalanceDocument.GSON.toJsonTree(config).getAsJsonObject();
        definition.addProperty("configFile", ((java.nio.file.Path) privateField(loaded, "loadedFrom")).getFileName().toString());
        definition.addProperty("exhaustionPerSuccessfulNativeTarget", config.exhaustionPerTarget());
        definition.addProperty("costMeaning", "0.005 native block-break exhaustion plus 0.005 * configured multiplier; not food points per block");
        sink.definition(id(), SOURCE, definition);
        if (!config.supported()) {
            sink.candidate(SOURCE, id(), "Disabled/invalid batch or positive XP cost without a proven XP operating path",
                    capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN); return;
        }
        Object permission = privateStatic(API + "FTBUltimine", "permissionOverride");
        if (!permission.getClass().isSynthetic() || !permission.getClass().getNestHost().getName().equals(API + "FTBUltimine")) {
            unknown(sink, "Custom Ultimine permission predicate; not invoked during generation"); return;
        }
        for (String registry : List.of("RestrictionHandlerRegistry", "BlockBreakingRegistry", "BlockSelectionRegistry")) {
            Collection<?> handlers = (Collection<?>) privateField(staticField(API + registry, "INSTANCE"), "handlers");
            var unsupported = handlers.stream().map(h -> h.getClass().getName())
                    .filter(type -> !passesNativeBlock(registry, type, context.inputs().installedVersion("enderio"))
                            && !preservesSameBlockSelection(registry, type, context.inputs().installedVersion("chisel"))).sorted().toList();
            if (!unsupported.isEmpty()) {
                unknown(sink, "Custom " + registry + " requires an operating contract: "
                        + unsupported); return;
            }
        }
        Object shapes = staticField(API + "shape.ShapeRegistry", "INSTANCE");
        if (((List<?>) privateField(shapes, "shapesList")).stream().noneMatch(shape -> shape.getClass().getName().equals(API + "shape.ShapelessShape"))) {
            unknown(sink, "Native shapeless selection is unavailable"); return;
        }
        String rankProblem = rankProblem(context, definition);
        if (rankProblem != null) { unknown(sink, rankProblem); return; }
        for (String attribute : List.of("max_blocks_modifier", "cooldown_modifier", "exhaustion_modifier", "experience_modifier")) {
            var key = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ftbultimine", attribute);
            var value = BuiltInRegistries.ATTRIBUTE.getOptional(key);
            if (value.isEmpty() || value.get().getDefaultValue() != 0) {
                unknown(sink, "Nonstandard/missing native Ultimine attribute baseline: " + key); return;
            }
        }
        if (ItemStack.EMPTY.is(itemTag("excluded_tools/strict"))
                || !config.requireTool() && ItemStack.EMPTY.is(itemTag("excluded_tools"))) {
            sink.candidate(SOURCE, id(), "Loaded excluded-tool tags block the audited empty-hand/offhand setup",
                    capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN); return;
        }
        var access = new ConfigurationAccess.Resolver(resources, context.inputs().configurationConstrainedItems());
        List<String> targets = new ArrayList<>();
        List<ToolWitness> tools = new ArrayList<>();
        boolean whitelist = BuiltInRegistries.BLOCK.getTag(blockTag("block_whitelist")).map(set -> set.size() > 0).orElse(false);
        for (var item : context.inputs().items()) {
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            if (item instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                // Audited static native block behavior only; block identity itself never supplies access.
                if ((block.getClass() == Block.class || block.getClass() == RotatedPillarBlock.class)
                        && !block.defaultBlockState().requiresCorrectToolForDrops()
                        && block.defaultBlockState().getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) >= 0
                        && !block.defaultBlockState().is(blockTag("excluded_blocks"))
                        && (!whitelist || block.defaultBlockState().is(blockTag("block_whitelist")))) {
                    targets.add(itemId);
                }
            }
            if (config.requireTool() && item != Items.AIR) {
                var stack = new ItemStack(item);
                if (!stack.is(itemTag("excluded_tools")) && !stack.is(itemTag("excluded_tools/strict"))
                        && (item instanceof TieredItem || stack.isDamageableItem() || stack.is(itemTag("included_tools")))) {
                    int capacity = stack.isDamageableItem() ? Math.max(0, stack.getMaxDamage() - config.preventToolBreak()) : config.maxBlocks();
                    if (capacity > 1) tools.add(new ToolWitness(itemId, capacity));
                }
            }
        }
        // As with the crouch-growth adapter, access to an enabled native action is separate
        // from acquiring its targets. Never turn registry target samples into item supply.
        if (targets.isEmpty()) {
            sink.candidate(SOURCE, id(), "Loaded block filters permit no audited native hand-harvestable target",
                    capabilityAxes(), CapabilitySink.Reason.NO_SUPPORTED_OPERATION); return;
        }
        definition.add("eligibleTargetSamples", BalanceDocument.GSON.toJsonTree(targets.stream().sorted().limit(16).toList()));
        if (!config.requireTool()) tools.add(new ToolWitness("", config.maxBlocks()));
        ConfigurationAccess.Proof best = null; int bestCapacity = 0;
        for (ToolWitness tool : tools) {
            var proof = tool.item().isEmpty() ? bareHandActionAccess(config) : access.require(List.of(List.of(tool.item())));
            int capacity = Math.min(config.maxBlocks(), tool.capacity());
            if (proof.placement().reachable() && (best == null || proof.placement().stage().ordinal() < best.placement().stage().ordinal()
                    || proof.placement().stage() == best.placement().stage() && capacity > bestCapacity)) { best = proof; bestCapacity = capacity; }
        }
        if (best == null) {
            sink.candidate(SOURCE, id(), "Configured tool requirement has no independent usable tool acquisition witness",
                    capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN); return;
        }
        definition.add("selectedSetup", BalanceDocument.GSON.toJsonTree(best.selected()));
        definition.addProperty("supportedBatchBound", bestCapacity);
        sink.definition(id(), SOURCE, definition);
        sink.add(operation(config, best, bestCapacity));
    }

    /** Only called after loaded permissions, handlers, attributes, shape and target filters are audited. */
    public static ConfigurationAccess.Proof bareHandActionAccess(Configuration config) {
        if (!config.supported() || config.requireTool()) throw new IllegalArgumentException("Bare-hand action is not enabled");
        String reason = "Loaded default-player action has no required item or XP charge; fresh survival player food permits a bounded action. "
                + "Target acquisition, drops and repeated food supply are not certified.";
        var source = new AcquisitionSource(SOURCE, AcquisitionSource.Kind.PLAYER_ACTION, ProgressionBand.ENTRY,
                1, false, false, 0, .85, List.of(), reason);
        return new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.ENTRY, true, .85, List.of(source)),
                List.of("native bare-hand action; positive food; connected permitted target blocks"), List.of());
    }

    public static Functional operation(Configuration config, ConfigurationAccess.Proof proof, int capacity) {
        if (!config.supported() || !proof.placement().reachable() || capacity <= 1 || capacity > config.maxBlocks())
            throw new IllegalArgumentException("Ultimine requires a supported attainable multi-target configuration");
        List<String> unknown = List.of("Conditional peak; neighboring eligible same-kind targets must be found or acquired, hold Ultimine and break an initial block; target samples certify no item supply or drops",
                "Food exhaustion, tool wear, native break events/protection and equal-or-lower hardness can stop the batch; no guaranteed max-size burst or blocks/second",
                "Default new-player permissions and unmodified attributes only; personal overrides, equipment modifiers and custom right-click harvesting are not composed",
                "Strength policy values additional targets within 4 exhaustion and a one-second action window; cooldown discounts the window; access is independent of this policy");
        var operation = new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.UNKNOWN, null, null, null,
                config.cooldownTicks() / 20.0, List.of(config.exhaustionPerTarget() + " exhaustion per successful native target; native durability per broken block",
                "Positive food required; replenish hunger for repeated operation; no XP charge"), proof.selected(), config.exhaustionPerTarget());
        var measurement = CompetitiveCapabilities.measurement(CapabilityAxis.VEIN_MINING, (double) capacity, "targets",
                "conditional=unprotected connected same-block batch; native shapeless selection; hand-harvestable targets",
                new Scope("hand_harvestable_blocks", "connected_same_block", null, (double) capacity), operation, "ftbultimine_capabilities", Origin.TYPED_ADAPTER, unknown);
        return new Functional(new CapabilityEvidence(SOURCE, proof.placement().stage(), Map.of(), true, Math.min(.85, proof.placement().confidence()),
                "Loaded batch configuration with default action access and any required tool proof; costs and target restrictions retained"),
                "default_player_shapeless", List.of(measurement), proof.placement().acquisition(), true);
    }
    public static boolean relevantRankPermission(String permission) {
        while (permission.endsWith(".*")) permission = permission.substring(0, permission.length() - 2);
        return permission.equals("*") || permission.equals("ftbultimine") || permission.startsWith("ftbultimine.");
    }
    /** Audited Ender IO handlers return PASS before conduit-specific work on our native non-entity blocks.
     * No handler is invoked here; unknown handlers/versions retain the conservative exclusion. */
    public static boolean passesNativeBlock(String registry, String type, String version) {
        if (!Set.of("8.2.11-beta", "8.2.12-beta").contains(Objects.toString(version, ""))) return false;
        return registry.equals("BlockBreakingRegistry") && type.equals("com.enderio.enderio.compat.ftb_ultimine.ConduitBlockBreakHandler")
                || registry.equals("BlockSelectionRegistry") && type.equals("com.enderio.enderio.compat.ftb_ultimine.ConduitBlockSelectionHandler");
    }
    /** Chisel 1.4.1 returns TRUE for equal block identities when its option and held chisel
     * apply, and PASS otherwise; it never rejects a target. This preserves our conditional
     * same-block contract without claiming cross-block grouping or invoking the handler. */
    public static boolean preservesSameBlockSelection(String registry, String type, String version) {
        return "1.4.1".equals(version) && registry.equals("BlockSelectionRegistry")
                && type.equals("com.leclowndu93150.chisel.compat.ftbultimine.ChiselBlockSelectionHandler");
    }
    private String rankProblem(PackEvidenceContext context, com.google.gson.JsonObject definition) {
        String version = context.inputs().installedVersion("ftbranks");
        if (version == null) return null;
        if (!Set.of("2101.1.4", "2101.1.5").contains(version)) return "Unaudited FTB Ranks version " + version;
        Object manager = invokeStatic("dev.ftb.mods.ftbranks.api.FTBRanksAPI", "manager");
        if (manager == null) return rankFileProblem(context, definition);
        if (invokeAs("dev.ftb.mods.ftbranks.api.RankManager", manager, "getServer") != context.server()) return "FTB Ranks policy belongs to another server";
        Collection<?> ranks = (Collection<?>) invokeAs("dev.ftb.mods.ftbranks.api.RankManager", manager, "getAllRanks");
        if (ranks.size() > 4096) return "Rank policy exceeds bounded census";
        for (Object rank : ranks) for (Object permission : (Collection<?>) invokeAs("dev.ftb.mods.ftbranks.api.Rank", rank, "getPermissions"))
            if (relevantRankPermission(permission.toString())) return "Player-dependent rank override requires access/cost proof: " + permission;
        definition.addProperty("rankPolicy", "Audited current-server FTB Ranks policy has no Ultimine override in any rank; missing source files use native name-format-only defaults");
        return null;
    }
    private String rankFileProblem(PackEvidenceContext context, com.google.gson.JsonObject definition) {
        // Audited RankManagerImpl.reload source selection before SERVER_STARTED. Do not
        // construct the manager, publish ranks, invoke conditions or write default files.
        Path serverFile = context.server().getWorldPath((net.minecraft.world.level.storage.LevelResource)
                staticField("dev.ftb.mods.ftbranks.impl.RankManagerImpl", "FOLDER_NAME")).resolve("ranks.snbt");
        Path defaults = (Path) privateStatic("dev.ftb.mods.ftbranks.impl.RankManagerImpl", "DEFAULT_RANK_FILE");
        Path selected = Files.exists(serverFile) ? serverFile : defaults;
        Path pack = dev.architectury.platform.Platform.getConfigFolder().resolve("ftbranks-pack.snbt");
        int files = 0;
        try {
            for (Path file : List.of(selected, pack)) {
                if (!Files.exists(file)) continue;
                if (!Files.isRegularFile(file) || Files.size(file) > 4 * 1024 * 1024) return "Rank file exceeds bounded definition contract: " + file.getFileName();
                Object raw = Class.forName("dev.ftb.mods.ftblibrary.snbt.SNBT").getMethod("tryRead", Path.class).invoke(null, file);
                if (!(raw instanceof CompoundTag tag)) return "Unreadable authoritative rank file: " + file.getFileName();
                String problem = rankDefinitionProblem(tag);
                if (problem != null) return problem;
                files++;
            }
        } catch (ReflectiveOperationException error) { throw changed(error); }
        catch (java.io.IOException error) { throw new IllegalStateException("Cannot read authoritative rank policy", error); }
        // Audited createDefaultRanks adds only name formatting when neither server nor
        // default file exists. Any pack-file permission still goes through the scan above.
        definition.addProperty("rankPolicy", "Audited current-server FTB Ranks policy has no Ultimine override in any rank; missing source files use native name-format-only defaults");
        return null;
    }
    public static String rankDefinitionProblem(CompoundTag definitions) {
        if (definitions.size() > 4096) return "Rank policy exceeds bounded census";
        int fields = 0;
        for (String id : definitions.getAllKeys()) {
            if (!(definitions.get(id) instanceof CompoundTag rank)) return "Malformed rank definition: " + id;
            for (String key : rank.getAllKeys()) {
                if (++fields > 32768) return "Rank permission census exceeds bound";
                if (relevantRankPermission(key)) return "Player-dependent rank override requires access/cost proof: " + key;
            }
        }
        return null;
    }
    private void unknown(CapabilitySink sink, String message) { sink.candidate(SOURCE, id(), message, capabilityAxes(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); }
    private static net.minecraft.tags.TagKey<Block> blockTag(String path) { return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ftbultimine", path)); }
    private static net.minecraft.tags.TagKey<Item> itemTag(String path) { return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ftbultimine", path)); }
    private static Object setting(String key) { Object value = staticField(API + "config.FTBUltimineServerConfig", key); return invokeAs(value.getClass().getName(), value, "get"); }
    private static int integer(String key) { return ((Number) setting(key)).intValue(); }
    private static double number(String key) { return ((Number) setting(key)).doubleValue(); }
    private static Object staticField(String type, String key) {
        try { return Class.forName(type).getField(key).get(null); } catch (ReflectiveOperationException e) { throw changed(e); }
    }
    private static Object privateStatic(String type, String key) {
        try { var field = Class.forName(type).getDeclaredField(key); field.setAccessible(true); return field.get(null); } catch (ReflectiveOperationException e) { throw changed(e); }
    }
    private static Object privateField(Object target, String key) {
        try { var field = target.getClass().getDeclaredField(key); field.setAccessible(true); return field.get(target); } catch (ReflectiveOperationException e) { throw changed(e); }
    }
    private static Object invokeStatic(String type, String method) { return invokeAs(type, null, method); }
    private static Object invokeAs(String type, Object target, String method) {
        try { return Class.forName(type).getMethod(method).invoke(target); } catch (ReflectiveOperationException e) { throw changed(e); }
    }
    private static IllegalStateException changed(ReflectiveOperationException error) { return new IllegalStateException("Audited Ultimine configuration API changed", error); }
}
