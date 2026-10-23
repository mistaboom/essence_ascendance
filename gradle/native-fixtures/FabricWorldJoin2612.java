package com.mistaboom.essence_ascendance.fixture;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import com.mistaboom.essence_ascendance.ore.LatentOreItems;
import com.mistaboom.essence_ascendance.visual.FlightVisualState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Disposable integrated-world join probe; stops after the player exists on the client. */
public final class FabricWorldJoin2612 implements ClientModInitializer {
    private static final List<BlockState> BASELINE_HOSTS = List.of(Blocks.STONE.defaultBlockState(),
            Blocks.DEEPSLATE.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(),
            Blocks.END_STONE.defaultBlockState());
    private int ticks;
    private boolean opening;
    private boolean joined;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            if (!joined && client.level != null && client.player != null) {
                joined = true;
                verifyOreInventoryModels(client);
                int flags = ((FlightVisualState) client.player).essenceAscendance$flightFlags();
                System.out.println("FabricWorldJoin2612: joined integrated world; flight flags=" + flags);
                client.stop();
            } else if (!opening && ticks > 20) {
                opening = true;
                System.out.println("FabricWorldJoin2612: opening isolated world");
                client.createWorldOpenFlows().openWorld("world", () -> { });
            } else if (ticks > 2400) {
                throw new AssertionError("Fabric client did not join the isolated world within 2400 ticks");
            }
        });
    }

    /** Exercise the actual native item resolver and its transformed render layers. */
    private static void verifyOreInventoryModels(net.minecraft.client.Minecraft client) {
        try {
            Field layersField = ItemStackRenderState.class.getDeclaredField("layers");
            Field countField = ItemStackRenderState.class.getDeclaredField("activeLayerCount");
            Field quadsField = ItemStackRenderState.LayerRenderState.class.getDeclaredField("quads");
            layersField.setAccessible(true);
            countField.setAccessible(true);
            quadsField.setAccessible(true);
            Set<Set<String>> variants = new HashSet<>();
            Set<Object> guiIdentities = new HashSet<>();
            List<ItemStack> catalog = LatentOreHost.creativeStacks();
            ItemStack registeredDefault = EssenceInfuserContent.LATENT_ORE_ITEM.get().getDefaultInstance();
            if (LatentOreHost.read(registeredDefault).orElse(null) != Blocks.STONE.defaultBlockState())
                throw new AssertionError("Registered Latent Ore default item must carry its Stone host");
            if (registeredDefault.is(Blocks.END_STONE.asItem()))
                throw new AssertionError("Vanilla End Stone is not the registered adaptive Latent Ore item");
            for (BlockState host : BASELINE_HOSTS) {
                var state = new TrackingItemStackRenderState();
                ItemStack stack = catalog.stream().filter(candidate -> LatentOreHost.read(candidate).orElse(null) == host)
                        .findFirst().orElseThrow(() -> new AssertionError(
                                "Creative Latent Ore catalog is missing its registered host stack: " + host));
                var alias = LatentOreItems.itemFor(host);
                var aliasId = BuiltInRegistries.ITEM.getKey(alias);
                var expectedId = LatentOreItems.itemId(BuiltInRegistries.BLOCK.getKey(host.getBlock()));
                ItemStack giveStack = new ItemStack(alias);
                if (!aliasId.equals(expectedId) || !stack.is(alias)
                        || !ItemStack.isSameItemSameComponents(stack, LatentOreHost.stack(host)))
                    throw new AssertionError("Creative Latent Ore item differs from its registered host alias: "
                            + host + " alias=" + aliasId);
                if (LatentOreHost.read(giveStack).orElse(null) != host)
                    throw new AssertionError("/give Latent Ore alias lacks its default host: " + aliasId);
                client.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, client.level, null, 0);
                var givenState = new TrackingItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(
                        givenState, giveStack, ItemDisplayContext.GUI, client.level, null, 0);
                if (!state.getModelIdentity().equals(givenState.getModelIdentity())
                        || !guiIdentities.add(state.getModelIdentity()))
                    throw new AssertionError("Latent Ore /give item has a wrong or shared GUI atlas identity: "
                            + aliasId + " creative=" + state.getModelIdentity()
                            + " given=" + givenState.getModelIdentity());
                int layers = countField.getInt(state);
                var entries = (ItemStackRenderState.LayerRenderState[]) layersField.get(state);
                Set<String> sprites = new HashSet<>();
                int count = 0;
                for (int i = 0; i < layers; i++) {
                    @SuppressWarnings("unchecked")
                    List<BakedQuad> quads = (List<BakedQuad>) quadsField.get(entries[i]);
                    count += quads.size();
                    quads.forEach(quad -> sprites.add(quad.materialInfo().sprite().contents().name().toString()));
                }
                if (count < 6 || sprites.isEmpty() || sprites.stream().anyMatch(id ->
                        !id.startsWith("essence_ascendance:block/latent_ore/item/")))
                    throw new AssertionError("Latent Ore inventory item did not resolve item host faces: host="
                            + host + ", quads=" + count + ", sprites=" + sprites);
                if (!variants.add(sprites))
                    throw new AssertionError("Latent Ore inventory items share a host sprite: " + host + " " + sprites);
                int givenLayers = countField.getInt(givenState);
                var givenEntries = (ItemStackRenderState.LayerRenderState[]) layersField.get(givenState);
                Set<String> givenSprites = new HashSet<>();
                for (int i = 0; i < givenLayers; i++) {
                    @SuppressWarnings("unchecked")
                    List<BakedQuad> quads = (List<BakedQuad>) quadsField.get(givenEntries[i]);
                    quads.forEach(quad -> givenSprites.add(quad.materialInfo().sprite().contents().name().toString()));
                }
                if (!sprites.equals(givenSprites))
                    throw new AssertionError("/give Latent Ore alias selected different host sprite faces: "
                            + aliasId + " creative=" + sprites + " given=" + givenSprites);
                System.out.println("FabricWorldJoin2612: actual inventory alias " + aliasId + " sprites=" + sprites);
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not inspect native inventory item render layers", error);
        }
    }

}
