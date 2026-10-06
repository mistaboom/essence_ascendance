package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.balance.engine.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.*;

/** External equipment prerequisites are read independently of Essence prices/unlocks.
 * Missing equipment is reported as missing evidence, never as proof of absence or an invented route. */
public final class SkillOperatingAccess {
    private SkillOperatingAccess() { }
    public record Decision(ProgressionBand earliestSupportedSetup, List<String> witnesses, List<String> unresolved) { }
    public static Decision evaluate(SkillBalanceSemantics.Descriptor skill, PackEvidence evidence) {
        return new Resolver(evidence).evaluate(skill);
    }
    /** Shared within one generation only; no environment cache or load-time scan. */
    public static final class Resolver {
        private final PackEvidence evidence;
        private final ConfigurationAccess.Resolver resolver;
        private final Map<SkillBalanceSemantics.Equipment, ConfigurationAccess.Proof> roles = new EnumMap<>(SkillBalanceSemantics.Equipment.class);
        public Resolver(PackEvidence evidence) { this.evidence = evidence; resolver = new ConfigurationAccess.Resolver(evidence.resources()); }
        public Decision evaluate(SkillBalanceSemantics.Descriptor skill) {
        var stage = ProgressionBand.ENTRY; var witnesses = new ArrayList<String>(); var unresolved = new ArrayList<String>();
        for (var role : skill.equipment().stream().sorted().toList()) {
            if (role == SkillBalanceSemantics.Equipment.ASCENDANCE_TOOL) {
                unresolved.add("Essence equipment is an internal operating condition, never external acquisition evidence"); continue;
            }
            var proof = roles.computeIfAbsent(role, ignored -> {
            var candidates = evidence.resources().keySet().stream().filter(id -> {
                var key = ResourceLocation.tryParse(id); if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return false;
                var item = BuiltInRegistries.ITEM.get(key);
                return switch (role) {
                    case SHIELD -> item instanceof ShieldItem;
                    case MELEE_WEAPON -> item instanceof SwordItem || item instanceof AxeItem;
                    case RANGED_WEAPON -> item instanceof ProjectileWeaponItem;
                    case PICKAXE -> item instanceof PickaxeItem;
                    case FISHING_ROD -> item instanceof FishingRodItem;
                    case REPAIRABLE_GEAR -> new ItemStack(item).isDamageableItem();
                    case CASTER_FOCUS, ASCENDANCE_TOOL -> false;
                };
            }).toList();
            return resolver.require(List.of(candidates));
            });
            if (proof.placement().reachable()) {
                stage = ProgressionBand.at(Math.max(stage.ordinal(), proof.placement().stage().ordinal())); witnesses.addAll(proof.selected());
            } else unresolved.add("No supported external " + role + " setup in captured native resources; gameplay requirement remains");
        }
        return new Decision(stage, List.copyOf(witnesses), List.copyOf(unresolved));
        }
    }
}
