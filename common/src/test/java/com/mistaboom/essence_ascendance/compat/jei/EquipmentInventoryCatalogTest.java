package com.mistaboom.essence_ascendance.compat.jei;

import com.mistaboom.essence_ascendance.equipment.EquipmentCatalogStacks;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData;
import com.mistaboom.essence_ascendance.infuser.EquipmentInfusionData;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

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
                    "Sword nochange layer remains untinted");

            Object identity = EssenceAscendanceJeiPlugin.EQUIPMENT_TIER_SUBTYPE
                    .getSubtypeData(stack, UidContext.Ingredient);
            check(identity == tier && identities.add(identity), "JEI receives one stable identity per tier");
            check(EssenceAscendanceJeiPlugin.EQUIPMENT_TIER_SUBTYPE
                            .getSubtypeData(stack, UidContext.Recipe) == null,
                    "Tier samples do not split ordinary recipe matching");
        }
        check(variants.getFirst().get(DataComponents.CUSTOM_DATA) == null,
                "Latent sample remains the canonical untagged crafted form");

        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("EquipmentInventoryCatalogTest: " + checks + " catalog, tint, JEI and ownership checks PASS");
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
