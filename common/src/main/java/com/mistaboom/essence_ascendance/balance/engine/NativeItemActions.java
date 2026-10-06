package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Audited 1.21.1 item action contracts. Exact native classes, effective acquisition and target
 * registries establish functionality; names never do. No gameplay callbacks or random actions run. */
public final class NativeItemActions {
    private NativeItemActions() { }
    public record Action(CapabilityAxis axis, String target, List<String> costs, List<String> conditions, boolean consumesItem) { }
    public static List<Action> describe(Item item) {
        if (item.getClass() == BoneMealItem.class) return List.of(
                new Action(CapabilityAxis.CROP_ACCELERATION, "growable_crop", List.of("one bone meal per successful native use"),
                        List.of("native eligible planted crop, immature state and valid soil; growth increment and harvest yield vary by target"), true),
                new Action(CapabilityAxis.TREE_ACCELERATION, "growable_sapling", List.of("one bone meal per accepted native use, including failed growth attempts"),
                        List.of("native planted sapling, valid soil, sufficient space; success is probabilistic and is not a log-production rate"), true));
        if (item.getClass() == EnderpearlItem.class) return List.of(new Action(CapabilityAxis.TELEPORTATION, "self",
                List.of("one ender pearl; native impact damage and item cooldown"),
                List.of("projectile impact, valid landing and survival required; does not permit hovering or sustained flight"), true));
        if (item.getClass() == CompassItem.class) return List.of(new Action(CapabilityAxis.INFORMATION, "world_spawn",
                List.of(), List.of("default compass points to world spawn in its native valid dimension; no ore or threat detection"), false));
        if (item == Items.CLOCK && item.getClass() == Item.class) return List.of(new Action(CapabilityAxis.INFORMATION, "day_phase",
                List.of(), List.of("native time-of-day display in dimensions with a natural sky"), false));
        if (item.getClass() == SpyglassItem.class) return List.of(new Action(CapabilityAxis.INFORMATION, "distant_visual",
                List.of("player use action"), List.of("line-of-sight magnification; does not locate hidden targets"), false));
        return List.of();
    }
    public static void collect(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
        var access = OptionalIntegration.attempt("native_item_actions", "normalize native crafting access",
                () -> ConfiguredRecipeAccess.nativeCrafting(context, resources));
        if (!access.succeeded()) {
            sink.candidate("native_item_actions", "native_item_actions", access.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED); return;
        }
        for (var item : context.inputs().items()) {
            var actions = describe(item); if (actions.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            var result = OptionalIntegration.attempt("native_item_actions", "read " + id, () -> {
                var staged = new CapabilitySink();
                var proof = actions.getFirst().consumesItem() ? access.value().orElseThrow().requireItem(id)
                        : ConfigurationAccess.require(resources, List.of(List.of(id)));
                for (var action : actions) {
                    var requirements = new ArrayList<ConfigurationAccess.Proof>(); requirements.add(proof);
                    if (action.axis() == CapabilityAxis.CROP_ACCELERATION || action.axis() == CapabilityAxis.TREE_ACCELERATION) {
                        boolean crop = action.axis() == CapabilityAxis.CROP_ACCELERATION;
                        var targets = context.inputs().items().stream().filter(candidate -> candidate instanceof BlockItem block
                                && block.getBlock().getClass().getPackageName().equals("net.minecraft.world.level.block")
                                && (crop ? block.getBlock() instanceof CropBlock : block.getBlock().getClass() == SaplingBlock.class))
                                .map(candidate -> BuiltInRegistries.ITEM.getKey(candidate).toString()).toList();
                        requirements.add(ConfigurationAccess.require(resources, List.of(targets, List.of("minecraft:dirt", "minecraft:grass_block"))));
                        if (crop) requirements.add(ConfigurationAccess.require(resources, List.of(context.inputs().items().stream()
                                .filter(candidate -> candidate instanceof HoeItem).map(candidate -> BuiltInRegistries.ITEM.getKey(candidate).toString()).toList())));
                    }
                    var combined = ConfiguredRecipeAccess.combine(requirements, "native action " + id, .9);
                    var definition = BalanceDocument.GSON.toJsonTree(action).getAsJsonObject();
                    definition.addProperty("runtimeClass", item.getClass().getName());
                    definition.add("access", BalanceDocument.GSON.toJsonTree(combined)); staged.definition("native_item_actions", id + "/" + action.axis(), definition);
                    if (!combined.placement().reachable()) {
                        staged.candidate(id, "native_item_actions", String.join("; ", combined.unknown()), Set.of(action.axis()), CapabilitySink.Reason.ACCESS_UNPROVEN); continue;
                    }
                    var operation = new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.UNKNOWN,
                            null, null, null, null, action.costs(), action.conditions());
                    var measurement = CompetitiveCapabilities.measurement(action.axis(), 1.0, "presence", "native item action; " + id,
                            new Scope(action.target(), "single", null, 1.0), operation, "native_item_actions", Origin.NATIVE,
                            List.of("Functional action only; player cadence, target renewal, sustained production, and survival outcomes are unmeasured"));
                    staged.add(new Functional(new CapabilityEvidence(id, combined.placement().stage(), Map.of(), true,
                            combined.placement().confidence(), "Effective native action with supported item supply and setup"),
                            "native_action/" + action.axis(), List.of(measurement), combined.placement().acquisition(), true));
                }
                return staged;
            });
            result.value().ifPresentOrElse(sink::merge, () -> sink.candidate(id, "native_item_actions", result.failure(),
                    actions.stream().map(Action::axis).collect(java.util.stream.Collectors.toSet()), CapabilitySink.Reason.READ_FAILED));
        }
    }
}
