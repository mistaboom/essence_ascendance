package com.mistaboom.essence_ascendance.compat.jei;

import com.mistaboom.essence_ascendance.equipment.EquipmentCatalogStacks;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData;
import com.mistaboom.essence_ascendance.infuser.EquipmentInfusionData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.HashSet;

/** Tier catalog samples stay visually distinct without impersonating invested artifacts. */
public final class EquipmentInventoryCatalogTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        permitTestItemRegistration();
        var testItem = Registry.register(
                BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath("essence_ascendance", "catalog_test_item"),
                new TestEquipmentItem()
        );

        var variants = EquipmentCatalogStacks.allTiers(testItem);
        check(variants.size() == EquipmentTier.values().length, "Catalog exposes all six tiers");

        var identities = new HashSet<Object>();
        for (int index = 0; index < variants.size(); index++) {
            var tier = EquipmentTier.values()[index];
            var stack = variants.get(index);
            check(EquipmentTierData.tier(stack) == tier, "Catalog tier order is stable");
            check(stack.getCount() == 1, "Every catalog entry is removable as one ordinary item");
            check(!SoulboundEquipmentData.isSoulbound(stack), "Catalog entries never carry an owner");
            check(EquipmentInfusionData.read(stack).isEmpty(), "Catalog entries carry no invested Essence progress");
            check(EquipmentTierVisuals.itemTint(stack, 0)
                            == (0xFF000000 | EquipmentTierVisuals.primaryRgb(tier)),
                    "Base layer uses the tier primary tint");
            check(EquipmentTierVisuals.itemTint(stack, 1)
                            == (0xFF000000 | EquipmentTierVisuals.armorAccentRgb(tier)),
                    "Accent layer uses the tier metal tint");
            check(EquipmentTierVisuals.itemTint(stack, 2) == EquipmentTierVisuals.CLEAR_TINT,
                    "Equipment nochange layers remain untinted");

            Object identity = EssenceAscendanceJeiPlugin.EQUIPMENT_TIER_SUBTYPE
                    .getSubtypeData(stack, UidContext.Ingredient);
            check(identity == tier && identities.add(identity), "JEI receives one stable identity per tier");
            check(EssenceAscendanceJeiPlugin.EQUIPMENT_TIER_SUBTYPE
                            .getSubtypeData(stack, UidContext.Recipe) == tier,
                    "Crafting and Infuser acquisition recipes distinguish equipment tiers");
        }
        check(variants.getFirst().get(DataComponents.CUSTOM_DATA) == null,
                "Latent sample remains the canonical untagged crafted form");
        orderedCatalog(testItem, variants);
        optionalSortRefresh(testItem);
        var focusItem = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath("essence_ascendance", "catalog_test_focus"),
                new com.mistaboom.essence_ascendance.pylon.EssenceFocusItem(new Item.Properties()));
        focusTiers(focusItem);
        focusRegistration();

        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("EquipmentInventoryCatalogTest: " + checks + " catalog, tint, JEI and ownership checks PASS");
    }

    private static void optionalSortRefresh(Item item) {
        var order = new JeiCatalogOrder(() -> java.util.Set.of(item), () -> 0L);
        var stack = new ItemStack(item);
        var filter = new SortFilter();
        order.createdIndex(stack, 10);
        order.runtimeAvailable();
        order.refreshSort(filter);
        check(filter.calls == 0, "Unchanged catalogs do not invalidate JEI sorting");
        order.createdIndex(stack, 20);
        order.refreshSort(filter);
        order.refreshSort(filter);
        check(filter.calls == 1, "Late variants request one lightweight sort, without a public config callback");

        filter.fail = true;
        for (int index = 0; index < 3; index++) {
            order.createdIndex(stack, 30 + index);
            order.refreshSort(filter);
        }
        check(filter.calls == 2 && !order.takePendingSort(),
                "A failed optional sort leaves JEI running and does not retry each variant");

        order.clear();
        order.createdIndex(stack, 10);
        order.runtimeAvailable();
        order.createdIndex(stack, 20);
        order.refreshSort(new Object()); // Models a JEI version without this internal hook.
        check(!order.takePendingSort(), "Missing JEI internals cannot propagate a startup/runtime failure");

        order.clear();
        order.createdIndex(stack, 10);
        order.runtimeAvailable();
        order.createdIndex(stack, 20);
        filter.fail = false;
        order.refreshSort(filter);
        check(filter.calls == 3, "A new JEI runtime resets optional-hook failure state");
    }

    private static final class SortFilter {
        private int calls;
        private boolean fail;
        private void markSortIndexesDirty() {
            calls++;
            if (fail) throw new IllegalStateException("Expected optional sort fixture failure");
        }
    }

    private static void orderedCatalog(Item item, java.util.List<ItemStack> variants) {
        record Entry(ItemStack stack, int anchor) { }
        var nativeOrder = java.util.Comparator.comparingInt(Entry::anchor);
        var creativeOrder = JeiCatalogOrder.creativeComparator(nativeOrder, Entry::stack);
        for (int seed = 0; seed < 24; seed++) {
            var shuffled = new java.util.ArrayList<>(variants);
            java.util.Collections.shuffle(shuffled, new java.util.Random(seed));
            var order = new JeiCatalogOrder(() -> java.util.Set.of(item), () -> 0L);
            var entries = new java.util.ArrayList<Entry>();
            int index = 10;
            for (var stack : shuffled) entries.add(new Entry(stack, order.createdIndex(stack, index++)));
            entries.add(new Entry(new ItemStack(net.minecraft.world.item.Items.COAL), 9));
            entries.add(new Entry(new ItemStack(net.minecraft.world.item.Items.DIRT), 30));
            entries.sort(creativeOrder);
            check(entries.getFirst().stack().is(net.minecraft.world.item.Items.COAL)
                            && entries.getLast().stack().is(net.minecraft.world.item.Items.DIRT),
                    "Tier tie-breaking preserves unrelated native creative positions");
            for (int i = 0; i < variants.size(); i++)
                check(EquipmentTierData.tier(entries.get(i + 1).stack()) == EquipmentTier.values()[i],
                        "Arbitrary JEI subtype creation order always resolves Latent through Transcendent");
        }
        var accepted = new java.util.ArrayList<ItemStack>();
        com.mistaboom.essence_ascendance.item.CreativeVariantRegistry.register(
                net.minecraft.world.item.CreativeModeTabs.COMBAT, () -> EquipmentCatalogStacks.allTiers(item));
        com.mistaboom.essence_ascendance.item.CreativeVariantRegistry.append(
                net.minecraft.world.item.CreativeModeTabs.COMBAT, (stack, visibility) -> accepted.add(stack));
        for (int i = 0; i < variants.size(); i++)
            check(EquipmentTierData.tier(accepted.get(i)) == EquipmentTier.values()[i],
                    "Native creative append retains the same six-tier order");
    }

    @SuppressWarnings("removal")
    private static void focusTiers(Item item) {
        var interpreter = EssenceAscendanceJeiPlugin.FOCUS_TIER_SUBTYPE;
        var identities = new HashSet<Object>();
        for (int index = -1; index < EssenceFocusTier.values().length; index++) {
            var tier = index < 0 ? null : EssenceFocusTier.values()[index];
            // The interpreter is registered only for Focus. Detached components exercise its
            // actual identity contract without booting a loader just to resolve that item supplier.
            var stack = new ItemStack(item);
            if (tier != null) stack.set(DataComponents.CUSTOM_DATA, EssenceFocusData.tierData(tier));
            String expected = tier == null ? "latent" : tier.serializedName();
            Object identity = interpreter.getSubtypeData(stack, UidContext.Ingredient);
            check(JeiCatalogOrder.tierOrder(stack) == index + 1, "Focus catalog follows the same six-tier ordering");
            check(expected.equals(identity) && identities.add(identity),
                    "Latent and all five completed Focus tiers have distinct browsing identities: " + expected);
            for (var context : UidContext.values()) {
                check(identity.equals(interpreter.getSubtypeData(stack, context))
                                && expected.equals(interpreter.getLegacyStringSubtypeInfo(stack, context)),
                        "Focus acquisition and use matching preserve the completed tier: " + expected + "/" + context);
            }
            CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> {
                CompoundTag progress = new CompoundTag(), contributions = new CompoundTag();
                progress.putString("target_tier", "transcendent");
                progress.putUUID("session", new java.util.UUID(17, 23));
                contributions.putLong("vitality", 17);
                progress.put("contributions", contributions);
                outer.put("essence_ascendance_focus_infusion", progress);
            });
            com.mistaboom.essence_ascendance.infuser.FocusInfusionData.recordFunding(stack,
                    new com.mistaboom.essence_ascendance.team.PoolId(
                            com.mistaboom.essence_ascendance.team.MembershipAuthority.NATIVE,
                            com.mistaboom.essence_ascendance.team.PoolId.Kind.SOLO, new java.util.UUID(29, 31)),
                    java.util.Map.of(ResourceLocation.parse("essence_ascendance:vitality"), 17L));
            stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Named Focus"));
            for (var context : UidContext.values())
                check(identity.equals(interpreter.getSubtypeData(stack.copy(), context)),
                        "Partial infusion, committed receipts, names and copies do not change Focus tier identity");
        }
        check(identities.size() == 6, "Focus has exactly six completed/Latent identities");
        check(interpreter.getSubtypeData(ItemStack.EMPTY, UidContext.Recipe) == null,
                "Empty stacks have no Focus subtype");
    }

    private static void focusRegistration() throws Exception {
        var node = new ClassNode();
        try (var source = EssenceAscendanceJeiPlugin.class.getResourceAsStream("EssenceAscendanceJeiPlugin.class")) {
            check(source != null, "Compiled JEI plugin is available for registration verification");
            new ClassReader(source).accept(node, 0);
        }
        var method = node.methods.stream().filter(candidate -> candidate.name.equals("registerItemSubtypes"))
                .findFirst().orElseThrow();
        boolean focus = false, interpreter = false;
        int registrations = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field) {
                if (field.owner.equals("com/mistaboom/essence_ascendance/pylon/EssencePylonContent")
                        && field.name.equals("ESSENCE_FOCUS")) focus = true;
                if (field.owner.equals(node.name) && field.name.equals("FOCUS_TIER_SUBTYPE")) interpreter = true;
            } else if (instruction instanceof MethodInsnNode call && call.owner.equals("mezz/jei/api/registration/ISubtypeRegistration")
                    && call.name.equals("registerSubtypeInterpreter")) {
                if (focus || interpreter) {
                    check(focus && interpreter, "Focus supplier is paired with its completed-tier interpreter");
                    registrations++;
                }
                focus = interpreter = false;
            }
        }
        check(registrations == 1, "The real plugin registers the single Focus item exactly once");
    }

    /** This isolated JVM needs one synthetic profile item after vanilla freezes built-ins. */
    private static void permitTestItemRegistration() throws Exception {
        var frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(BuiltInRegistries.ITEM, false);
        var intrusive = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        intrusive.setAccessible(true);
        intrusive.set(BuiltInRegistries.ITEM, new java.util.IdentityHashMap<>());
    }

    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }

    private static final class TestEquipmentItem extends Item implements EquipmentProfileItem {
        private TestEquipmentItem() {
            super(new Item.Properties().durability(100));
        }

        @Override
        public ResourceLocation equipmentProfileId() {
            return ResourceLocation.fromNamespaceAndPath("essence_ascendance", "catalog_test");
        }
    }
}
