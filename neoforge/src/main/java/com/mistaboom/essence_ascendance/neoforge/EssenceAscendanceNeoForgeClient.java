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
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import com.mistaboom.essence_ascendance.client.armor.AscendanceArmorModel;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

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

    private void registerMenuScreens(
            RegisterMenuScreensEvent event
    ) {
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
    }

    private void onClientSetup(
            FMLClientSetupEvent event
    ) {
        event.enqueueWork(
                () -> EssenceAscendanceClient.init(
                        ItemProperties::register
                )
        );
    }
}
