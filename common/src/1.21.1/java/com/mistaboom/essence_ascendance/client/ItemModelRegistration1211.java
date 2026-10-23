package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.infuser.EssentiumBlock;
import dev.architectury.registry.client.rendering.ColorHandlerRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;

/** Native 1.21.1 predicates, tint callbacks and block render-layer registration. */
public final class ItemModelRegistration1211 {
    private static final ResourceLocation PULLING =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "pulling"
            );

    private static final ResourceLocation PULL =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "pull"
            );

    private static final ResourceLocation EQUIPMENT_TIER =
            EquipmentTierVisuals.MODEL_PROPERTY;

    private static final ResourceLocation ESSENTIUM_ESSENCE =
            EssentiumCarrierVisuals.MODEL_PROPERTY;

    private ItemModelRegistration1211() { }

    public static void register(Registrar registrar) {
        Objects.requireNonNull(registrar, "Item property registrar cannot be null");
        Item[] tierVisualItems = {
                AscendanceItems.ASCENDANCE_SWORD.get(),
                AscendanceItems.ASCENDANCE_GLAIVE.get(),
                AscendanceItems.ASCENDANCE_RANGED_WEAPON.get(),
                AscendanceItems.ASCENDANCE_CASTER.get(),
                AscendanceItems.ASCENDANCE_PICKAXE.get(),
                AscendanceItems.ASCENDANCE_AXE.get(),
                AscendanceItems.ASCENDANCE_SHOVEL.get(),
                AscendanceItems.ASCENDANCE_HOE.get(),
                AscendanceItems.ASCENDANCE_FISHING_ROD.get(),
                AscendanceItems.ASCENDANCE_HELMET.get(),
                AscendanceItems.ASCENDANCE_CHESTPLATE.get(),
                AscendanceItems.ASCENDANCE_LEGGINGS.get(),
                AscendanceItems.ASCENDANCE_BOOTS.get(),
                AscendanceItems.ASCENDANCE_SHIELD.get()
        };

        for (Item item : tierVisualItems) {
            registrar.register(
                    item,
                    EQUIPMENT_TIER,
                    (stack, level, entity, seed) ->
                            EquipmentTierVisuals.modelPropertyValue(stack)
            );
        }

        registrar.register(AscendanceItems.ASCENDANCE_SHIELD.get(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "blocking"),
                (stack, level, entity, seed) -> entity != null && entity.getUseItem() == stack
                        && com.mistaboom.essence_ascendance.equipment.EquipmentShieldService.isUsingShield(entity)
                        ? 1.0F : 0.0F);

        registrar.register(AscendanceItems.ASCENDANCE_FISHING_ROD.get(), ResourceLocation.fromNamespaceAndPath("minecraft", "cast"),
                (stack, level, entity, seed) -> {
                    if (!(entity instanceof net.minecraft.world.entity.player.Player player) || player.fishing == null) return 0;
                    if (player.fishing instanceof com.mistaboom.essence_ascendance.gathering.RodHookAccess hook && hook.essenceAscendance$rodHand() != 0)
                        return player.getItemInHand(hook.essenceAscendance$rodHand() == 1 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND) == stack ? 1 : 0;
                    return 0;
                });

        Item[] essentiumVisualItems = {
                EssenceInfuserContent.ESSENTIUM_NUGGET.get(),
                EssenceInfuserContent.ESSENTIUM_INGOT.get(),
                EssenceInfuserContent.ESSENTIUM_BLOCK.get()
        };

        Item[] essenceCarrierTintItems = {
                EssenceInfuserContent.LATENT_NUGGET.get(),
                EssenceInfuserContent.LATENT_INGOT.get(),
                EssenceInfuserContent.LATENT_BLOCK_ITEM.get(),
                EssenceInfuserContent.ESSENTIUM_NUGGET.get(),
                EssenceInfuserContent.ESSENTIUM_INGOT.get(),
                EssenceInfuserContent.ESSENTIUM_BLOCK.get()
        };

        ColorHandlerRegistry.registerItemColors(
                EssentiumCarrierVisuals::itemTint,
                essenceCarrierTintItems
        );
        ColorHandlerRegistry.registerItemColors(
                PersonalizedEquipmentClient::itemTint,
                AscendanceItems.tieredInventoryArtItems().toArray(Item[]::new)
        );
        ColorHandlerRegistry.registerBlockColors(
                (state, level, pos, tintIndex) -> EssentiumCarrierVisuals.CLEAR_TINT,
                EssenceInfuserContent.LATENT_BLOCK.get()
        );
        ColorHandlerRegistry.registerBlockColors(
                EssentiumBlock::tint,
                EssenceInfuserContent.ESSENTIUM_BLOCK_BLOCK.get()
        );
        RenderTypeRegistry.register(
                RenderType.cutout(),
                EssenceInfuserContent.LATENT_BLOCK.get(),
                EssenceInfuserContent.ESSENTIUM_BLOCK_BLOCK.get()
        );

        for (Item item : essentiumVisualItems) {
            registrar.register(
                    item,
                    ESSENTIUM_ESSENCE,
                    (stack, level, entity, seed) ->
                            EssentiumCarrierVisuals.modelPropertyValue(stack)
            );
        }

        Item rangedWeapon = AscendanceItems
                .ASCENDANCE_RANGED_WEAPON
                .get();

        /*
         * minecraft:pulling
         * -----------------
         * Controls whether ascendance_ranged_weapon.json switches from its
         * resting model into pulling_0.
         */
        registrar.register(
                rangedWeapon,
                PULLING,
                (stack, level, entity, seed) -> {
                    if (entity == null) {
                        return 0.0F;
                    }

                    return entity.isUsingItem()
                            && entity.getUseItem() == stack
                            ? 1.0F
                            : 0.0F;
                }
        );

        /*
         * minecraft:pull
         * --------------
         * Controls progression through pulling_0 -> pulling_1 -> pulling_2.
         *
         * The server continuously synchronizes the resolved Ascendance full
         * draw time onto the concrete bow ItemStack.  Using that same value
         * here means the rendered bow reaches full draw at exactly the same
         * time gameplay does instead of always using vanilla's 20 ticks.
         */
        registrar.register(
                rangedWeapon,
                PULL,
                (stack, level, entity, seed) -> {
                    if (entity == null
                            || !entity.isUsingItem()
                            || entity.getUseItem() != stack) {
                        return 0.0F;
                    }

                    int usedTicks =
                            stack.getUseDuration(entity)
                                    - entity.getUseItemRemainingTicks();

                    int fullDrawTicks =
                            EquipmentWeaponService
                                    .syncedRangedFullDrawTicks(stack);

                    return Math.min(
                            1.0F,
                            usedTicks / (float) Math.max(1, fullDrawTicks)
                    );
                }
        );

        /*
         * Fail loudly during development if a loader bridge silently failed
         * to attach the predicates.  This prevents us from debugging JSON or
         * projectile code when the real problem is client registration.
         */
        ItemStack probe = new ItemStack(rangedWeapon);

        if (ItemProperties.getProperty(probe, PULLING) == null
                || ItemProperties.getProperty(probe, PULL) == null
                || ItemProperties.getProperty(probe, EQUIPMENT_TIER) == null) {
            throw new IllegalStateException(
                    "Essence Ascendance failed to register required model properties "
                            + "for the Ascendance ranged weapon"
            );
        }

        for (Item item : tierVisualItems) {
            if (ItemProperties.getProperty(new ItemStack(item), EQUIPMENT_TIER) == null) {
                throw new IllegalStateException(
                        "Essence Ascendance failed to register equipment tier model property for "
                                + item
                );
            }
        }

        for (Item item : essentiumVisualItems) {
            if (ItemProperties.getProperty(new ItemStack(item), ESSENTIUM_ESSENCE) == null) {
                throw new IllegalStateException(
                        "Essence Ascendance failed to register Essentium Essence model property for "
                                + item
                );
            }
        }

    }

    @FunctionalInterface
    public interface Registrar {
        void register(
                Item item,
                ResourceLocation propertyId,
                ClampedItemPropertyFunction property
        );
    }
}
