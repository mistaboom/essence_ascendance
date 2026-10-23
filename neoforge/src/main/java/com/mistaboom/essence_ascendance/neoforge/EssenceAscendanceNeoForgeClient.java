package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.EssenceAscendanceClient;
import com.mistaboom.essence_ascendance.client.AscendanceNexusScreen;
import com.mistaboom.essence_ascendance.client.AscendanceNexusRenderer;
import com.mistaboom.essence_ascendance.client.EssenceCrucibleRenderer;
import com.mistaboom.essence_ascendance.client.EssenceCrucibleScreen;
import com.mistaboom.essence_ascendance.client.EssenceInfuserRenderer;
import com.mistaboom.essence_ascendance.client.EssenceMachineItemRenderer;
import com.mistaboom.essence_ascendance.client.EssenceInfuserScreen;
import com.mistaboom.essence_ascendance.client.EssencePylonRenderer;
import com.mistaboom.essence_ascendance.client.EssencePylonScreen;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusContent;
import com.mistaboom.essence_ascendance.neoforge.client.LatentOreNeoForgeModels;
//? if <26.1.2
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import com.mistaboom.essence_ascendance.client.armor.AscendanceArmorModel;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import net.minecraft.client.model.HumanoidModel;
//? if >=26.1.2
/*import net.minecraft.client.model.Model;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.entity.state.HumanoidRenderState;*/
//? if >=26.1.2
/*import net.minecraft.client.resources.model.EquipmentClientInfo;*/
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
//? if <26.1.2
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//? if >=26.1.2
/*import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;*/
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
//? if >=26.1.2
/*import com.mistaboom.essence_ascendance.client.ClientItemProperty2612;*/
//? if >=26.1.2
/*import com.mistaboom.essence_ascendance.client.ClientItemTint2612;*/
//? if >=26.1.2
/*import net.minecraft.resources.Identifier;*/
//? if >=26.1.2
/*import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;*/
//? if >=26.1.2
/*import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;*/

import java.util.EnumMap;
import java.util.Map;

@Mod(
        value = EssenceAscendance.MOD_ID,
        dist = Dist.CLIENT
)
public final class EssenceAscendanceNeoForgeClient {

    public EssenceAscendanceNeoForgeClient(
            IEventBus modBus
    ) {
        //? if >=26.1.2
        /*modBus.addListener(this::registerItemModelProperties);*/
        //? if >=26.1.2
        /*modBus.addListener(this::registerItemTintSources);*/
        //? if >=26.1.2
        /*modBus.addListener(this::registerSpecialModels);*/
        modBus.addListener(LatentOreNeoForgeModels::modifyBakingResult);
        modBus.addListener(
                this::onClientSetup
        );
        modBus.addListener(
                this::registerMenuScreens
        );
        modBus.addListener(
                this::registerRenderers
        );
        modBus.addListener(
                this::registerClientExtensions
        );
    }

    //? if >=26.1.2
    /*private void registerItemModelProperties(RegisterRangeSelectItemModelPropertyEvent event) { String mod = EssenceAscendance.MOD_ID; event.register(Identifier.fromNamespaceAndPath(mod, "tier"), ClientItemProperty2612.TIER.type()); event.register(Identifier.fromNamespaceAndPath(mod, "essentium_essence"), ClientItemProperty2612.ESSENTIUM_ESSENCE.type()); event.register(Identifier.fromNamespaceAndPath(mod, "blocking"), ClientItemProperty2612.BLOCKING.type()); event.register(Identifier.fromNamespaceAndPath(mod, "cast"), ClientItemProperty2612.CAST.type()); event.register(Identifier.fromNamespaceAndPath(mod, "pulling"), ClientItemProperty2612.PULLING.type()); event.register(Identifier.fromNamespaceAndPath(mod, "pull"), ClientItemProperty2612.PULL.type()); }*/

    //? if >=26.1.2 {
    /*private void registerSpecialModels(RegisterSpecialModelRendererEvent event) {
        event.register(Identifier.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "machine_item"), EssenceMachineItemRenderer.Unbaked.MAP_CODEC);
        event.register(Identifier.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "ascendance_shield"),
                com.mistaboom.essence_ascendance.client.AscendanceShieldSpecialRenderer2612.Unbaked.MAP_CODEC);
    }
    *///? }

    //? if >=26.1.2
    /*private void registerItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {*/
        //? if >=26.1.2
        /*String mod = EssenceAscendance.MOD_ID;*/
        //? if >=26.1.2
        /*for (int layer = 0; layer < ClientItemTint2612.EQUIPMENT.length; layer++)*/
            //? if >=26.1.2
            /*event.register(Identifier.fromNamespaceAndPath(mod, "equipment_layer_" + layer), ClientItemTint2612.EQUIPMENT[layer].type());*/
        //? if >=26.1.2
        /*for (int layer = 0; layer < ClientItemTint2612.CARRIER.length; layer++)*/
            //? if >=26.1.2
            /*event.register(Identifier.fromNamespaceAndPath(mod, "carrier_layer_" + layer), ClientItemTint2612.CARRIER[layer].type());*/
    //? if >=26.1.2
    /*}*/

    private void registerMenuScreens(
            RegisterMenuScreensEvent event
    ) {
        event.register(com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent.TEAM_MENU.get(),
                com.mistaboom.essence_ascendance.client.TeamScreen::new);
        event.register(com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent.MACHINE_ACCESS_MENU.get(),
                com.mistaboom.essence_ascendance.client.MachineAccessScreen::new);
        event.register(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_MENU.get(),
                EssenceCrucibleScreen::new
        );
        event.register(
                EssencePylonContent.ESSENCE_PYLON_MENU.get(),
                EssencePylonScreen::new
        );
        event.register(
                EssenceInfuserContent.ESSENCE_INFUSER_MENU.get(),
                EssenceInfuserScreen::new
        );
        event.register(
                AscendanceNexusContent.ASCENDANCE_NEXUS_MENU.get(),
                AscendanceNexusScreen::new
        );
    }

    private void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event
    ) {
        event.registerBlockEntityRenderer(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_BLOCK_ENTITY.get(),
                EssenceCrucibleRenderer::new
        );
        event.registerBlockEntityRenderer(
                EssencePylonContent.ESSENCE_PYLON_BLOCK_ENTITY.get(),
                EssencePylonRenderer::new
        );
        event.registerBlockEntityRenderer(
                AscendanceNexusContent.ASCENDANCE_NEXUS_BLOCK_ENTITY.get(),
                AscendanceNexusRenderer::new
        );
        event.registerBlockEntityRenderer(
                EssenceInfuserContent.ESSENCE_INFUSER_BLOCK_ENTITY.get(),
                EssenceInfuserRenderer::new
        );
    }

    private void registerClientExtensions(
            RegisterClientExtensionsEvent event
    ) {
        //? if >=26.1.2 {
        /*event.registerItem(new IClientItemExtensions() {
            private final Map<EquipmentSlot, AscendanceArmorModel<HumanoidRenderState>> armorModels =
                    new EnumMap<>(EquipmentSlot.class);

            @Override public Model getGenericArmorModel(ItemStack stack,
                    EquipmentClientInfo.LayerType layerType, Model original) {
                if (!(stack.getItem() instanceof AscendanceArmorItem armor)) return original;
                return armorModels.computeIfAbsent(armor.ascendanceSlot(), AscendanceArmorModel::new);
            }

            @Override public Identifier getArmorTexture(ItemStack stack,
                    EquipmentClientInfo.LayerType layerType, EquipmentClientInfo.Layer layer,
                    Identifier original) {
                Identifier authored = EquipmentTierVisuals.armorTexture(
                        stack, layerType == EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS);
                return authored == null ? original : authored;
            }
        }, AscendanceItems.ASCENDANCE_HELMET.get(), AscendanceItems.ASCENDANCE_CHESTPLATE.get(),
                AscendanceItems.ASCENDANCE_LEGGINGS.get(), AscendanceItems.ASCENDANCE_BOOTS.get());
        *///?} else {
        event.registerItem(new IClientItemExtensions() {
            private final Map<EquipmentSlot, HumanoidModel<LivingEntity>> armorModels =
                    new EnumMap<>(EquipmentSlot.class);

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                    EquipmentSlot slot, HumanoidModel<?> original) {
                if (!(stack.getItem() instanceof AscendanceArmorItem armor)
                        || armor.ascendanceSlot() != slot) return original;
                // The default getGenericArmorModel copies the original pose and visibility.
                return armorModels.computeIfAbsent(slot, AscendanceArmorModel::new);
            }
        }, AscendanceItems.ASCENDANCE_HELMET.get(), AscendanceItems.ASCENDANCE_CHESTPLATE.get(),
                AscendanceItems.ASCENDANCE_LEGGINGS.get(), AscendanceItems.ASCENDANCE_BOOTS.get());
        //?}

        //? if <26.1.2 {
        IClientItemExtensions machineRenderer = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return EssenceMachineItemRenderer.instance();
            }
        };

        event.registerItem(
                machineRenderer,
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_ITEM.get(),
                EssencePylonContent.ESSENCE_PYLON_ITEM.get(),
                EssencePylonContent.ESSENCE_FOCUS.get(),
                AscendanceNexusContent.ASCENDANCE_NEXUS_ITEM.get(),
                EssenceInfuserContent.ESSENCE_INFUSER_ITEM.get()
        );
        //?}
    }

    private void onClientSetup(
            FMLClientSetupEvent event
    ) {
        event.enqueueWork(
                //? if >=26.1.2
                /*EssenceAscendanceClient::init*/
                //? if <26.1.2
                () -> EssenceAscendanceClient.init(ItemProperties::register)
        );
    }
}
