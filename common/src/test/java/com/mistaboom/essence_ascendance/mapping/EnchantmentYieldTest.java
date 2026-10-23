package com.mistaboom.essence_ascendance.mapping;

import com.mistaboom.essence_ascendance.balance.economy.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.client.ItemEssenceTooltipClientState;
import com.mistaboom.essence_ascendance.client.archive.ItemYieldBrowser;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.*;
import java.util.*;

public final class EnchantmentYieldTest {
    private static int checks;
    private static final long SCALE = FractionalAmountService.SCALE;
    private static final ResourceLocation UTILITY = EssenceTypes.UTILITY.id(), OFFENSE = EssenceTypes.OFFENSE.id(), DEFENSE = EssenceTypes.DEFENSE.id();
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var registry = new MappedRegistry<Enchantment>(Registries.ENCHANTMENT, Lifecycle.stable());
        var sharp = register(registry, "sharp", EnchantmentEffectComponents.DAMAGE, new AddValue(LevelBasedValue.perLevel(1, .5f)), 5);
        var protection = register(registry, "protection", EnchantmentEffectComponents.DAMAGE_PROTECTION, new AddValue(LevelBasedValue.perLevel(1)), 4);
        var mending = register(registry, "mending", EnchantmentEffectComponents.REPAIR_WITH_XP, new MultiplyValue(LevelBasedValue.constant(2)), 1);
        var unknown = register(registry, "opaque", EnchantmentEffectComponents.DAMAGE, new UnknownEffect(), 5);
        registry.freeze();
        zeroOnsetBooks(sharp);
        impossibleToolConditions();
        var toolCondition = net.minecraft.world.level.storage.loot.predicates.MatchTool.toolMatches(
                //? if >=26.1.2
                /*net.minecraft.advancements.criterion.ItemPredicate.Builder.item().of(net.minecraft.core.registries.BuiltInRegistries.ITEM, Items.IRON_SWORD)).build();*/
                //? if <26.1.2
                net.minecraft.advancements.critereon.ItemPredicate.Builder.item().of(Items.IRON_SWORD)).build();
        var conditionalPreservation = Holder.direct(new Enchantment(Component.literal("conditional preservation"), sharp.value().definition(), HolderSet.direct(),
                DataComponentMap.builder().set(EnchantmentEffectComponents.ITEM_DAMAGE, List.of(
                        new ConditionalEffect<EnchantmentValueEffect>(new RemoveBinomial(LevelBasedValue.constant(.5f)), Optional.of(toolCondition)))).build()));
        var conditionalSink = new CapabilitySink();
        var conditionalFacts = NativeCapabilityReader.enchantmentMeasurements("fixture:conditional_preservation", conditionalPreservation.value(), 1, true, conditionalSink);
        check(conditionalFacts.stream().anyMatch(fact -> fact.axis() == CapabilityAxis.DURABILITY_REDUCTION
                        && fact.magnitude() != null && fact.magnitude() == .5 && fact.applicability().contains("minecraft:iron_sword")),
                "Registry-bound native item predicate lost readable conditional wear probability");
        check(EnchantmentSemantics.analyze(conditionalPreservation, 1).strength() > 0, "Known conditional wear effect has no economic evidence");
        var harvesting = Holder.direct(new Enchantment(Component.literal("harvesting"), sharp.value().definition(), HolderSet.direct(),
                DataComponentMap.builder().set(EnchantmentEffectComponents.BLOCK_EXPERIENCE, List.of(
                        new ConditionalEffect<EnchantmentValueEffect>(new SetValue(LevelBasedValue.constant(0)), Optional.empty()))).build()));
        check(EnchantmentSemantics.analyze(harvesting, 1).strength() > 0, "Recognized XP suppression blocked external-loot supported-item fallback");
        var litArrow = Holder.direct(new Enchantment(Component.literal("lit projectile"), sharp.value().definition(), HolderSet.direct(),
                DataComponentMap.builder().set(EnchantmentEffectComponents.PROJECTILE_SPAWNED, List.of(
                        new ConditionalEffect<EnchantmentEntityEffect>(new Ignite(LevelBasedValue.constant(100)), Optional.empty()))).build()));
        var litFacts = NativeCapabilityReader.enchantmentMeasurements("fixture:lit_projectile", litArrow.value(), 1, true, new CapabilitySink());
        check(litFacts.stream().anyMatch(fact -> fact.unit().equals("ignited_projectile_presence"))
                        && litFacts.stream().noneMatch(fact -> fact.unit().equals("ignite_seconds")),
                "Projectile ignition duration leaked into victim-burning duration");
        check(EnchantmentSemantics.analyze(litArrow, 1).strength() <= 1, "Projectile-burning duration inflated economic effect strength");
        targetSemantics(sharp.value());
        var identitySensitive = Holder.direct(new Enchantment(Component.literal("opaque identity"), sharp.value().definition(), HolderSet.direct(),
                DataComponentMap.builder().set(EnchantmentEffectComponents.DAMAGE,
                        List.of(new ConditionalEffect<EnchantmentValueEffect>(new HashBomb(), Optional.empty()))).build()));
        check(EnchantmentSemantics.analyze(identitySensitive, 1).strength() == 0, "Opaque effect was not conservative");
        var nested = Holder.direct(new Enchantment(Component.literal("opaque nested"), sharp.value().definition(), HolderSet.direct(),
                DataComponentMap.builder().set(EnchantmentEffectComponents.KNOCKBACK,
                        List.of(new ConditionalEffect<EnchantmentValueEffect>(new UnknownEffect(), Optional.empty()))).build()));
        var candidateSink = new CapabilitySink();
        NativeCapabilityReader.enchantmentMeasurements("fixture:opaque_nested", nested.value(), 1, true, candidateSink);
        check(candidateSink.candidates().stream().anyMatch(candidate -> candidate.detail().contains("Unsupported nested effect")),
                "Recognized component concealed its opaque nested effect");
        Map<ResourceLocation, Long> cap = Map.of(UTILITY, 869 * SCALE), carrier = Map.of(UTILITY, 16 * SCALE);
        ItemStack blank = new ItemStack(Items.ENCHANTED_BOOK);
        check(EnchantedStackYields.resolve(blank, cap, carrier).equals(carrier), "Blank book got enchantment value");
        var one = book(Map.of(sharp, 1)); var five = book(Map.of(sharp, 5));
        var oneValue = EnchantedStackYields.resolve(one, cap, carrier);
        var fiveValue = EnchantedStackYields.resolve(five, cap, carrier);
        check(total(fiveValue) > total(oneValue) && oneValue.get(OFFENSE) > 0, "Level or effect routing lost");
        check(total(EnchantedStackYields.resolve(book(Map.of(mending, 1)), cap, carrier)) > total(oneValue), "Actual repair magnitude flattened to enchantment count");
        check(EnchantedStackYields.resolve(book(Map.of(unknown, 5)), cap, carrier).equals(carrier), "Custom callback inferred positive value");
        var mixed = book(Map.of(sharp, 1, protection, 1));
        var mixedValue = EnchantedStackYields.resolve(mixed, cap, carrier);
        check(mixedValue.get(OFFENSE) > 0 && mixedValue.get(DEFENSE) > 0, "Mixed categories lost");
        check(total(mixedValue) > total(oneValue), "Second positive enchantment lost");
        check(total(mixedValue) <= total(oneValue) + total(EnchantedStackYields.resolve(book(Map.of(protection, 1)), cap, carrier)), "Book combination multiplied value");
        for (int rank = 2; rank <= 5; rank++)
            check(total(EnchantedStackYields.resolve(book(Map.of(sharp, rank)), cap, carrier))
                    <= 2 * total(EnchantedStackYields.resolve(book(Map.of(sharp, rank - 1)), cap, carrier)), "Level upgrade multiplied value");
        check(total(EnchantedStackYields.resolve(book(Map.of(sharp, 10)), cap, carrier)) > total(fiveValue),
                "Actual above-definition stored levels were silently flattened");
        for (long ceiling : new long[]{0, 1, 2, 3, 7, 16, 137, 869}) {
            for (long base : new long[]{0, Math.min(1, ceiling), Math.min(3, ceiling), ceiling}) {
                Map<ResourceLocation, Long> smallCap = ceiling > 0 ? Map.of(UTILITY, ceiling*SCALE) : Map.of();
                Map<ResourceLocation, Long> smallCarrier = base > 0 ? Map.of(UTILITY, base*SCALE) : Map.of();
                for (int level = 1; level <= 4; level++) {
                    long first = total(EnchantedStackYields.resolve(book(Map.of(sharp, level)), smallCap, smallCarrier));
                    long second = total(EnchantedStackYields.resolve(book(Map.of(protection, level)), smallCap, smallCarrier));
                    long combined = total(EnchantedStackYields.resolve(book(Map.of(sharp, level, protection, level)), smallCap, smallCarrier));
                    long upgraded = total(EnchantedStackYields.resolve(book(Map.of(sharp, level + 1)), smallCap, smallCarrier));
                    check(combined <= first + second && combined <= ceiling*SCALE, "Mixed rounding creates Essence from small/zero carrier");
                    check(upgraded <= first * 2 && upgraded >= first, "Level rounding creates Essence from small/zero carrier");
                }
            }
        }
        var sword = new ItemStack(Items.IRON_SWORD); sword.enchant(sharp, 3);
        check(total(EnchantedStackYields.resolve(sword, cap, carrier)) == total(cap), "Equipment premium bypassed material conservation");
        check(EnchantedStackYields.resolve(five, Map.of(), carrier).isEmpty(), "Blocked item gained enchantment payout");
        check(total(EnchantedStackYields.resolve(one.copyWithCount(32), cap, carrier)) == total(oneValue), "Per-item yield multiplied by stack size twice");
        var yields = new LinkedHashMap<ResourceLocation, Map<com.mistaboom.essence_ascendance.essence.EssenceDefinition, Long>>();
        yields.put(ResourceLocation.parse("minecraft:book"), Map.of(EssenceTypes.UTILITY, 16 * SCALE));
        yields.put(ResourceLocation.parse("minecraft:enchanted_book"), Map.of(EssenceTypes.UTILITY, 869 * SCALE));
        ItemEssenceMappingRegistry.installResolved(yields, new ItemEssenceMappingRegistry.LoadSummary(0,0,0,0,0,List.of()), () -> {});
        Map<ResourceLocation, List<ItemEssenceTooltipClientState.Yield>> client = new LinkedHashMap<>();
        yields.forEach((id, outputs) -> client.put(id, outputs.entrySet().stream().map(e -> new ItemEssenceTooltipClientState.Yield(e.getKey().id(), e.getValue())).toList()));
        var snapshot = new ItemEssenceTooltipClientState.YieldSnapshot(1, true, client, List.of());
        for (ItemStack stack : List.of(blank, one, five, mixed, book(Map.of(unknown, 5)))) {
            Map<ResourceLocation, Long> authoritative = new TreeMap<>();
            ItemEssenceMappingRegistry.resolveDissolution(stack).forEach((e,v) -> authoritative.put(e.id(),v));
            Map<ResourceLocation, Long> tooltip = new TreeMap<>();
            ItemEssenceTooltipClientState.outputsForStack(stack, snapshot).forEach(e -> tooltip.put(e.essenceId(), e.microUnits()));
            check(authoritative.equals(tooltip), "Client/dissolution drift");
            long command = ItemEssenceMappingRegistry.resolve(stack).outputs().values().stream().mapToLong(Long::longValue).sum() * SCALE;
            check(command == total(authoritative), "Command projection drift");
        }
        var rows = ItemYieldBrowser.enchantmentRows(snapshot, registry);
        check(rows.size() == 15 && rows.stream().map(ItemYieldBrowser.BrowserRow::rowId).distinct().count() == 15, "Archive level rows lost or keys collide");
        for (var row : rows) {
            var stack = row.item().stack();
            Map<ResourceLocation, Long> tooltip = new TreeMap<>();
            ItemEssenceTooltipClientState.outputsForStack(stack, snapshot).forEach(e -> tooltip.put(e.essenceId(),e.microUnits()));
            check(row.yields().equals(tooltip), "Archive/tooltip drift");
        }
        var graph = new ProductionGraph(List.of(new ProductionGraph.Process("fixture:book_recycling", "crafting",
                List.of(new ProductionGraph.Input(List.of("minecraft:enchanted_book"), 1, true)),
                List.of(new ProductionGraph.Output("fixture:product", 1, 1, false)), 0, 0, "test", 1, Map.of())), List.of());
        var resolved = EconomyConservationSolver.solveWholeUnits(graph, Map.of("minecraft:book", new DissolutionYield(16*SCALE),
                "minecraft:enchanted_book", new DissolutionYield(869*SCALE), "fixture:product", new DissolutionYield(500*SCALE)));
        check(resolved.yields().get("fixture:product").microUnits() == 16*SCALE, "Recipe consumed full book cap instead of component minimum");
        boundedCarrierFallback();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("EnchantmentYieldTest: " + checks + " checks PASS");
    }
    private static void impossibleToolConditions() {
        var ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        // Decode a whole native definition: retain an explicitly impossible requirement,
        // including a level curve whose zero first value previously resembled free ammo.
        var serialized = com.google.gson.JsonParser.parseString("""
                {
                  "description": {"text":"impossible native tool fixture"},
                  "supported_items": "minecraft:iron_sword",
                  "weight": 10, "max_level": 3, "anvil_cost": 1,
                  "min_cost": {"base":15,"per_level_above_first":15},
                  "max_cost": {"base":45,"per_level_above_first":15},
                  "slots": ["mainhand"],
                  "effects": {"minecraft:ammo_use":[{
                    "effect":{"type":"minecraft:set","value":{
                      "type":"minecraft:linear","base":0.0,"per_level_above_first":33.333332}},
                    "requirements":{"condition":"minecraft:match_tool","predicate":{"items":[]}}
                  }]}
                }
                """).getAsJsonObject();
        var enchantment = Enchantment.DIRECT_CODEC.parse(ops, serialized).getOrThrow();
        var holder = Holder.direct(enchantment);
        var impossible = enchantment.getEffects(EnchantmentEffectComponents.AMMO_USE).getFirst().requirements().orElseThrow();
        var nativePredicate = ((net.minecraft.world.level.storage.loot.predicates.MatchTool) impossible).predicate().orElseThrow();
        check(nativePredicate.items().isPresent() && nativePredicate.items().get().size() == 0
                        && !nativePredicate.test(new ItemStack(Items.IRON_SWORD)),
                "Explicit native empty item alternatives unexpectedly matched a supported tool");
        var cap = Map.of(UTILITY, 869*SCALE); var carrier = Map.of(UTILITY, 16*SCALE);
        for (int level : new int[]{1, 2, 3, 255}) {
            var sink = new CapabilitySink();
            check(NativeCapabilityReader.enchantmentMeasurements("fixture:impossible", enchantment, level, true, sink).isEmpty(),
                    "Impossible native condition retained a measured effect at level " + level);
            check(sink.candidates().stream().anyMatch(candidate -> candidate.detail().contains("Impossible native match_tool")),
                    "Impossible condition exclusion lost its diagnostic reason");
            var facts = EnchantmentSemantics.analyze(holder, level);
            check(facts.strength() == 0 && facts.routes().isEmpty()
                            && facts.evidence().stream().anyMatch(reason -> reason.contains("Unsupported effect definition")),
                    "Impossible whole definition borrowed supported-equipment or level-effort value");
            check(EnchantedStackYields.resolve(book(Map.of(holder, level)), cap, carrier).equals(carrier),
                    "Impossible whole definition received an enchantment premium");
        }
        for (String condition : List.of(
                "{\"condition\":\"minecraft:match_tool\",\"predicate\":{}}",
                "{\"condition\":\"minecraft:match_tool\"}",
                "{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":[\"minecraft:iron_sword\"]}}",
                "{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":[]}}}")) {
            var variant = serialized.deepCopy();
            variant.getAsJsonObject("effects").getAsJsonArray("minecraft:ammo_use").get(0).getAsJsonObject()
                    .add("requirements", com.google.gson.JsonParser.parseString(condition));
            var allowed = Enchantment.DIRECT_CODEC.parse(ops, variant).getOrThrow();
            check(EnchantmentSemantics.analyze(Holder.direct(allowed), 1).strength() > 0,
                    "Empty item alternatives were confused with an omitted, populated or inverted restriction");
            if (condition.contains("\"predicate\":{}")) {
                var unrestricted = (net.minecraft.world.level.storage.loot.predicates.MatchTool)
                        allowed.getEffects(EnchantmentEffectComponents.AMMO_USE).getFirst().requirements().orElseThrow();
                check(unrestricted.predicate().orElseThrow().items().isEmpty()
                                && unrestricted.predicate().orElseThrow().test(new ItemStack(Items.IRON_SWORD)),
                        "Omitted native item restriction was not unrestricted");
            }
        }
        // The same exclusion applies to primary numeric, targeted and entity effects.
        var mixed = new Enchantment(Component.literal("impossible mixed effects"), enchantment.definition(), HolderSet.direct(),
                DataComponentMap.builder()
                        .set(EnchantmentEffectComponents.DAMAGE, List.of(new ConditionalEffect<EnchantmentValueEffect>(
                                new AddValue(LevelBasedValue.constant(3)), Optional.of(impossible))))
                        .set(EnchantmentEffectComponents.PROJECTILE_SPAWNED, List.of(new ConditionalEffect<EnchantmentEntityEffect>(
                                new Ignite(LevelBasedValue.constant(100)), Optional.of(impossible))))
                        .set(EnchantmentEffectComponents.POST_ATTACK, List.of(new TargetedConditionalEffect<>(
                                EnchantmentTarget.ATTACKER, EnchantmentTarget.VICTIM,
                                new Ignite(LevelBasedValue.constant(4)), Optional.of(impossible))))
                        .build());
        check(NativeCapabilityReader.enchantmentMeasurements("fixture:impossible_mixed", mixed, 1, true, new CapabilitySink()).isEmpty()
                        && EnchantmentSemantics.analyze(Holder.direct(mixed), 1).strength() == 0,
                "Impossible native requirements escaped through another effect component");
        var xp = new Enchantment(Component.literal("impossible XP suppression"), enchantment.definition(), HolderSet.direct(),
                DataComponentMap.builder().set(EnchantmentEffectComponents.BLOCK_EXPERIENCE,
                        List.of(new ConditionalEffect<EnchantmentValueEffect>(new SetValue(LevelBasedValue.constant(0)), Optional.of(impossible)))).build());
        check(EnchantmentSemantics.analyze(Holder.direct(xp), 1).strength() == 0,
                "Impossible XP suppression triggered the external-loot supported-equipment fallback");
    }
    private static void zeroOnsetBooks(Holder<Enchantment> regular) {
        var template = regular.value();
        var laterDamage = directEffect(template, EnchantmentEffectComponents.DAMAGE,
                new MultiplyValue(LevelBasedValue.perLevel(1, .2f)));
        var laterProtection = directEffect(template, EnchantmentEffectComponents.DAMAGE_PROTECTION,
                new AddValue(LevelBasedValue.perLevel(0, 10)));
        var rebounds = directEffect(template, EnchantmentEffectComponents.DAMAGE,
                new AddValue(LevelBasedValue.lookup(List.of(1f, 0f, 2f), LevelBasedValue.constant(2))));
        check(EnchantmentSemantics.analyze(laterDamage, 1).strength() == 0
                        && EnchantmentSemantics.analyze(laterDamage, 2).strength() > 0,
                "Native 1.0 to 1.2 damage multiplier lost its real later effect");
        check(EnchantmentSemantics.analyze(rebounds, 3).strength() == 0,
                "Positive-start definition escaped conservation after a later zero");
        var cap = Map.of(UTILITY, 869*SCALE); var carrier = Map.of(UTILITY, 16*SCALE);
        check(total(EnchantedStackYields.resolve(book(Map.of(laterDamage, 2)), cap, carrier)) > 16*SCALE,
                "Recognized later-onset damage did not earn a funded positive premium");
        var types = List.of(regular, laterDamage, laterProtection);
        for (long[] budget : new long[][]{{0,0},{1,0},{1,1},{3,1},{7,3},{137,0},{137,1},{137,16},{137,137},{869,16}}) {
            long ceiling = budget[0], base = budget[1];
            Map<ResourceLocation,Long> limit = ceiling == 0 ? Map.of() : Map.of(UTILITY, ceiling*SCALE);
            Map<ResourceLocation,Long> plain = base == 0 ? Map.of() : Map.of(UTILITY, base*SCALE);
            var values = new HashMap<List<Integer>,Long>();
            for (int a=0; a<=4; a++) for (int b=0; b<=4; b++) for (int c=0; c<=4; c++) {
                var levels = List.of(a,b,c); var contents = new LinkedHashMap<Holder<Enchantment>,Integer>();
                for (int i=0; i<3; i++) if (levels.get(i)>0) contents.put(types.get(i), levels.get(i));
                long value = total(EnchantedStackYields.resolve(book(contents),limit,plain));
                check(value >= base*SCALE && value <= ceiling*SCALE, "Zero-onset book escaped carrier/ceiling bounds");
                values.put(levels,value);
                for (int i=0; i<3; i++) if (levels.get(i)>0) {
                    var lower = new ArrayList<>(levels); lower.set(i,levels.get(i)-1);
                    check(value >= values.get(lower), "Adding/upgrading a monotone zero-onset effect reduced total value");
                }
            }
            // Exhaust every partition of ordinary and two distinct zero-onset effects.
            // Equal ranks upgrade together; unequal ranks carry their maximum.
            for (int a=0; a<64; a++) for (int b=0; b<64; b++) {
                var left = List.of(a%4,a/4%4,a/16);
                var right = List.of(b%4,b/4%4,b/16);
                var merged = new ArrayList<Integer>();
                for (int i=0; i<3; i++) merged.add(left.get(i)>0 && left.get(i).equals(right.get(i))
                        ? left.get(i)+1 : Math.max(left.get(i),right.get(i)));
                check(values.get(merged) <= values.get(left)+values.get(right), "A mixed zero-onset merge partition multiplied value");
            }
            for (int rank : new int[]{2,3,5,10,254,255}) {
                long previous = total(EnchantedStackYields.resolve(book(Map.of(regular,rank-1,laterDamage,rank-1,laterProtection,rank-1)),limit,plain));
                long current = total(EnchantedStackYields.resolve(book(Map.of(regular,rank,laterDamage,rank,laterProtection,rank)),limit,plain));
                check(current >= previous && current <= 2*previous && current <= ceiling*SCALE,
                        "Higher stored levels escaped the shared zero-onset carrier allowance");
            }
        }
    }
    private static Holder<Enchantment> directEffect(Enchantment template,
            DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> component, EnchantmentValueEffect effect) {
        return Holder.direct(new Enchantment(Component.literal("native level curve"), template.definition(), HolderSet.direct(),
                DataComponentMap.builder().set(component,List.of(new ConditionalEffect<>(effect,Optional.empty()))).build()));
    }
    private static void targetSemantics(Enchantment template) {
        var damage = new DamageEntity(LevelBasedValue.constant(1), LevelBasedValue.constant(5),
                Holder.direct(new net.minecraft.world.damagesource.DamageType("fixture", 0)));
        var reflected = targeted(template, EnchantmentTarget.VICTIM, EnchantmentTarget.ATTACKER, AllOf.entityEffects(damage));
        var reflectedFacts = NativeCapabilityReader.enchantmentMeasurements("fixture:reflection", reflected.value(), 1, true, new CapabilitySink());
        check(reflectedFacts.stream().anyMatch(fact -> fact.axis() == CapabilityAxis.REFLECTION && fact.magnitude() == 1),
                "Nested victim-triggered counterattack lost its reflection semantics");
        check(EnchantmentSemantics.analyze(reflected, 1).routes().containsKey("defense")
                        && !EnchantmentSemantics.analyze(reflected, 1).routes().containsKey("offense"),
                "Reflected damage routed to offense");
        var attack = targeted(template, EnchantmentTarget.ATTACKER, EnchantmentTarget.VICTIM, damage);
        check(EnchantmentSemantics.analyze(attack, 1).routes().containsKey("offense"), "Opposing direct damage lost offense routing");
        var selfDamage = targeted(template, EnchantmentTarget.ATTACKER, EnchantmentTarget.ATTACKER, damage);
        check(EnchantmentSemantics.analyze(selfDamage, 1).strength() == 0, "Self damage received a positive capability premium");
        var explosion = new ExplodeEffect(false, Optional.empty(), Optional.of(LevelBasedValue.perLevel(1.2f, .5f)), Optional.empty(),
                net.minecraft.world.phys.Vec3.ZERO, LevelBasedValue.constant(3.5f), false,
                net.minecraft.world.level.Level.ExplosionInteraction.TRIGGER,
                net.minecraft.core.particles.ParticleTypes.POOF, net.minecraft.core.particles.ParticleTypes.POOF,
                //? if >=26.1.2
                /*net.minecraft.util.random.WeightedList.of(),*/
                Holder.direct(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ResourceLocation.parse("fixture:burst"))));
        var selfBurst = targeted(template, EnchantmentTarget.ATTACKER, EnchantmentTarget.ATTACKER, explosion);
        var burstOne = NativeCapabilityReader.enchantmentMeasurements("fixture:self_burst", selfBurst.value(), 1, true, new CapabilitySink());
        var burstTwo = NativeCapabilityReader.enchantmentMeasurements("fixture:self_burst", selfBurst.value(), 2, true, new CapabilitySink());
        check(burstOne.stream().anyMatch(fact -> fact.axis() == CapabilityAxis.VERTICAL_MOVEMENT
                        && Math.abs(fact.magnitude() - 1.2) < .00001), "Self burst lost actual native knockback multiplier");
        check(burstTwo.stream().anyMatch(fact -> fact.axis() == CapabilityAxis.VERTICAL_MOVEMENT
                        && Math.abs(fact.magnitude() - 1.7) < .00001), "Self burst ignored its native level curve");
        check(EnchantmentSemantics.analyze(selfBurst, 2).routes().containsKey("mobility")
                        && !EnchantmentSemantics.analyze(selfBurst, 2).routes().containsKey("offense"),
                "Self burst radius displaced movement routing");
        //? if >=26.1.2
        /*var hostileStatus = new ApplyMobEffect(HolderSet.direct(net.minecraft.world.effect.MobEffects.SLOWNESS),*/
        //? if <26.1.2
        var hostileStatus = new ApplyMobEffect(HolderSet.direct(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                LevelBasedValue.constant(1), LevelBasedValue.constant(2), LevelBasedValue.constant(0), LevelBasedValue.constant(0));
        var control = targeted(template, EnchantmentTarget.ATTACKER, EnchantmentTarget.VICTIM, hostileStatus);
        check(EnchantmentSemantics.analyze(control, 1).routes().containsKey("offense"), "Opposing harmful native status lost control routing");
        check(EnchantmentSemantics.analyze(targeted(template, EnchantmentTarget.ATTACKER, EnchantmentTarget.ATTACKER, hostileStatus), 1).strength() == 0,
                "Harmful self status received a positive capability premium");
        //? if >=26.1.2
        /*var mixedStatus = new ApplyMobEffect(HolderSet.direct(net.minecraft.world.effect.MobEffects.SLOWNESS, net.minecraft.world.effect.MobEffects.SPEED),*/
        //? if <26.1.2
        var mixedStatus = new ApplyMobEffect(HolderSet.direct(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                        //? if <26.1.2
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED),
                LevelBasedValue.constant(1), LevelBasedValue.constant(2), LevelBasedValue.constant(0), LevelBasedValue.constant(0));
        check(!EnchantmentSemantics.analyze(targeted(template, EnchantmentTarget.ATTACKER, EnchantmentTarget.VICTIM, mixedStatus), 1).routes().containsKey("offense"),
                "Mixed native status pool was assumed wholly harmful");
    }
    private static Holder<Enchantment> targeted(Enchantment template, EnchantmentTarget enchanted,
                                                EnchantmentTarget affected, EnchantmentEntityEffect effect) {
        return Holder.direct(new Enchantment(Component.literal("target semantics"), template.definition(), HolderSet.direct(),
                DataComponentMap.builder().set(EnchantmentEffectComponents.POST_ATTACK, List.of(
                        new TargetedConditionalEffect<>(enchanted, affected, effect, Optional.empty()))).build()));
    }
    private static void boundedCarrierFallback() {
        List<ProductionGraph.Process> processes = new ArrayList<>();
        Map<String, DissolutionYield> proposed = new TreeMap<>();
        proposed.put("fixture:chain_0", new DissolutionYield(0));
        proposed.put("minecraft:enchanted_book", new DissolutionYield(869*SCALE));
        proposed.put("fixture:dependent", new DissolutionYield(16*SCALE));
        processes.add(new ProductionGraph.Process("fixture:0000_consume_book", "crafting",
                List.of(new ProductionGraph.Input(List.of("minecraft:enchanted_book"),1,true)),
                List.of(new ProductionGraph.Output("fixture:dependent",1,1,false)),0,0,"test",1,Map.of()));
        for (int i = 1; i <= 300; i++) {
            String input = "fixture:chain_" + (i - 1), output = i == 300 ? "minecraft:book" : "fixture:chain_" + i;
            proposed.put(output, new DissolutionYield(16*SCALE));
            // Descending traversal advances the zero one link per solver pass.
            processes.add(new ProductionGraph.Process("fixture:chain_" + String.format(java.util.Locale.ROOT, "%03d", 300-i), "crafting",
                    List.of(new ProductionGraph.Input(List.of(input),1,true)),
                    List.of(new ProductionGraph.Output(output,1,1,false)),0,0,"test",1,Map.of()));
        }
        var result = EconomyConservationSolver.solveWholeUnits(new ProductionGraph(processes,List.of()), proposed);
        check(result.passes() == 256 && result.yields().get("minecraft:book").microUnits() == 0,
                "Fixture failed to reach bounded relaxation with an implicit carrier dependency");
        check(result.yields().get("fixture:dependent").microUnits() == 0 && result.invariants().stream().allMatch(EconomyConservationSolver.Invariant::passed),
                "Bounded fallback lost the plain-book dependency of enchanted-book input floors");
    }

    private static Holder.Reference<Enchantment> register(MappedRegistry<Enchantment> registry, String name,
            DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> component, EnchantmentValueEffect effect, int max) {
        var definition = Enchantment.definition(HolderSet.direct(Items.IRON_SWORD.builtInRegistryHolder()), 10, max,
                Enchantment.constantCost(1), Enchantment.constantCost(50), 1, EquipmentSlotGroup.MAINHAND);
        return registry.register(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath("fixture", name)),
                new Enchantment(Component.literal(name), definition, HolderSet.direct(),
                        DataComponentMap.builder().set(component, List.of(new ConditionalEffect<>(effect, Optional.empty()))).build()), RegistrationInfo.BUILT_IN);
    }
    private static final class HashBomb implements EnchantmentValueEffect {
        @Override public int hashCode() { throw new AssertionError("Opaque hashCode executed"); }
        @Override public boolean equals(Object other) { throw new AssertionError("Opaque equals executed"); }
        public float process(int level, net.minecraft.util.RandomSource random, float input) { throw new AssertionError("Opaque effect executed"); }
        public com.mojang.serialization.MapCodec<HashBomb> codec() { throw new AssertionError("Opaque effect codec executed"); }
    }
    private record UnknownEffect() implements EnchantmentValueEffect {
        public float process(int level, net.minecraft.util.RandomSource random, float input) { throw new AssertionError("Custom effect executed"); }
        public com.mojang.serialization.MapCodec<UnknownEffect> codec() { throw new AssertionError("Custom effect codec executed"); }
    }
    private static ItemStack book(Map<? extends Holder<Enchantment>, Integer> values) { return EnchantmentYieldDiagnostics.book(values); }
    private static long total(Map<ResourceLocation, Long> values) { return EnchantedStackYields.total(values); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
