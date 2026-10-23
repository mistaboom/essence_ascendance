package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.infuser.EssentiumBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Set;

/** Block-state tint sources replace the old block color callback in 26.1. */
public final class ClientBlockTint2612 {
    private ClientBlockTint2612() {}

    public static void register() {
        var colors = Minecraft.getInstance().getBlockColors();
        // Fabric's client entrypoint runs before Minecraft constructs BlockColors.
        // Its createDefault hook registers with the returned instance instead.
        if (colors == null) return;
        register(colors);
    }

    public static void register(BlockColors colors) {
        colors.register(List.of(BlockTintSources.constant(EssentiumCarrierVisuals.CLEAR_TINT)),
                EssenceInfuserContent.LATENT_BLOCK.get());
        colors.register(List.of(source(0), source(1)),
                EssenceInfuserContent.ESSENTIUM_BLOCK_BLOCK.get());
    }

    private static BlockTintSource source(int layer) {
        return new BlockTintSource() {
            @Override public int color(BlockState state) { return EssentiumBlock.tint(state, layer); }
            @Override public Set<Property<?>> relevantProperties() {
                return Set.of(EssentiumBlock.TIER, EssentiumBlock.ESSENCE);
            }
        };
    }
}
