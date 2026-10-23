package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class EssenceAscendanceClient {

    private static boolean initialized = false;

    private EssenceAscendanceClient() {
    }

    public static void init(
            //? if <26.1.2
            ItemModelRegistration1211.Registrar registrar
    ) {
        //? if <26.1.2
        java.util.Objects.requireNonNull(registrar, "Item property registrar cannot be null");

        if (initialized) {
            return;
        }

        dev.architectury.registry.client.level.entity.EntityRendererRegistry.register(
                com.mistaboom.essence_ascendance.projectile.ProjectileContent.MAGIC_BOLT,
                MagicBoltRenderer::new);
        ClientPacketDispatch.init();
        RodForceClient.init();
        dev.architectury.networking.NetworkManager.registerReceiver(dev.architectury.networking.NetworkManager.Side.S2C,
                com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.TYPE,
                com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.CODEC,
                (payload,context) -> ClientPacketDispatch.queue(context,() -> {
                    var level=net.minecraft.client.Minecraft.getInstance().level;
                    if (level!=null && level.dimension().location().equals(payload.dimension()))
                        GlaiveRenderer.accept(payload,level.getGameTime());
                }));
        dev.architectury.networking.NetworkManager.registerReceiver(dev.architectury.networking.NetworkManager.Side.S2C,
                com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.TYPE,
                com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.CODEC,
                (payload, context) -> ClientPacketDispatch.queue(context, () -> {
                    var level = net.minecraft.client.Minecraft.getInstance().level;
                    if (level != null && level.dimension().location().equals(payload.dimension())
                            && level.getEntity(payload.entityId()) instanceof net.minecraft.world.entity.player.Player holder
                            && holder.getUUID().equals(payload.owner()))
                        com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.accept(holder, payload.decision());
                }));
        PreciseMobMotionClient.init();
        com.mistaboom.essence_ascendance.client.ore.LatentOreClientCatalog.init();
        com.mistaboom.essence_ascendance.archive.ArchiveClientBridge.install(AscendanceArchiveScreen::open);
        ReloadListenerRegistry.register(
                PackType.CLIENT_RESOURCES,
                (ResourceManagerReloadListener) resources -> {
                    EssenceMachineMeshes.invalidateAll();
                    FocusVisuals.invalidateResources();
                    AscendanceShieldRenderer.invalidateResources();
                    GlaiveRenderer.invalidateResources();
                    ClientPacketDispatch.resourcesReloaded();
                },
                ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "machine_meshes")
        );
        RuntimeBalanceClientState.init();
        ClientEssenceState.init();
        com.mistaboom.essence_ascendance.skill.CommittedSkillAccess.installClientPrediction((player, skill) ->
                player == net.minecraft.client.Minecraft.getInstance().player && ClientCommittedSkills.isEffective(skill));
        AscendanceNexusTransactionClientState.init();
        EssenceCrucibleClientState.init();
        TeamInterfaceClientState.init();
        InfuserSoulbindClientState.init();
        ChannelSessionsClientState.init();
        EssencePylonClientState.init();
        EquipmentTooltipClientState.init();
        ItemEssenceTooltipClientState.init();
        SkillEffectHudClientState.init();
        GatheringSurveyClientState.init();
        UtilitySenseClientState.init();
        com.mistaboom.essence_ascendance.client.transientfx.TransientWorldVisuals.init();
        com.mistaboom.essence_ascendance.client.transientfx.TransientGuiVisuals.init();

        //? if <26.1.2
        ItemModelRegistration1211.register(registrar);

        //? if >=26.1.2
        /*ClientBlockTint2612.register();*/

        initialized = true;

        EssenceAscendance.LOGGER.info(
                "Initialized shared client presentation and networking"
        );
    }

}
