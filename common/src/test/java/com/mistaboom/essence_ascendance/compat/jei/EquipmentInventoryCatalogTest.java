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
        focusTiers(testItem);
        focusRegistration();

        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("EquipmentInventoryCatalogTest: " + checks + " catalog, tint, JEI and ownership checks PASS");
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
