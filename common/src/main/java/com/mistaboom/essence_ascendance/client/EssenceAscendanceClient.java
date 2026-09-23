package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
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

public final class EssenceAscendanceClient {

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

    private static boolean initialized = false;

    private EssenceAscendanceClient() {
    }

    /*
     * ============================================================
     * CLIENT INITIALIZATION
     * ============================================================
     *
     * IMPORTANT:
     * BowItem behavior does NOT automatically copy the vanilla BOW item's
     * model-property registrations to a custom BowItem.  The JSON override
     * predicates only work after minecraft:pulling and minecraft:pull are
     * explicitly registered for ASCENDANCE_RANGED_WEAPON.
     */
    public static void init(
            ItemPropertyRegistrar registrar
    ) {
        Objects.requireNonNull(
                registrar,
                "Item property registrar cannot be null"
        );

        if (initialized) {
            return;
        }

        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                com.mistaboom.essence_ascendance.projectile.ProjectileContent.MAGIC_BOLT,
                context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 0.35F, true));
        ClientPacketDispatch.init();
        RuntimeBalanceClientState.init();
        ClientEssenceState.init();
        com.mistaboom.essence_ascendance.skill.CommittedSkillAccess.installClientPrediction((player, skill) ->
                player == net.minecraft.client.Minecraft.getInstance().player && ClientCommittedSkills.isEffective(skill));
        AscendanceNexusTransactionClientState.init();
        EssenceCrucibleClientState.init();
        EssencePylonClientState.init();
        EquipmentTooltipClientState.init();
        ItemEssenceTooltipClientState.init();
        SkillEffectHudClientState.init();
        GatheringSurveyClientState.init();
        UtilitySenseClientState.init();
        com.mistaboom.essence_ascendance.client.transientfx.TransientWorldVisuals.init();
        com.mistaboom.essence_ascendance.client.transientfx.TransientGuiVisuals.init();

        Item[] tierVisualItems = {
                AscendanceItems.ASCENDANCE_MELEE_WEAPON.get(),
                AscendanceItems.ASCENDANCE_RANGED_WEAPON.get(),
                AscendanceItems.ASCENDANCE_CASTER.get(),
                AscendanceItems.ASCENDANCE_PICKAXE.get(),
                AscendanceItems.ASCENDANCE_AXE.get(),
                AscendanceItems.ASCENDANCE_SHOVEL.get(),
                AscendanceItems.ASCENDANCE_HOE.get(),
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

        initialized = true;

        EssenceAscendance.LOGGER.info(
                "Registered Ascendance equipment tier, Essentium Essence, and bow pulling model properties"
        );
    }

    @FunctionalInterface
    public interface ItemPropertyRegistrar {
        void register(
                Item item,
                ResourceLocation propertyId,
                ClampedItemPropertyFunction property
        );
    }
}
