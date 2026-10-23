package com.mistaboom.essence_ascendance.fabric.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.AscendanceShieldSpecialRenderer2612;
import com.mistaboom.essence_ascendance.client.ClientItemProperty2612;
import com.mistaboom.essence_ascendance.client.ClientItemTint2612;
import com.mistaboom.essence_ascendance.client.EssenceMachineItemRenderer;
import com.mistaboom.essence_ascendance.fabric.mixin.ItemTintSourcesAccessor;
import com.mistaboom.essence_ascendance.fabric.mixin.RangeSelectItemModelPropertiesAccessor;
import com.mistaboom.essence_ascendance.fabric.mixin.SpecialModelRenderersAccessor;
import net.minecraft.resources.Identifier;

/** Bind the same generated item-model codecs as NeoForge on Fabric 26.1.2. */
final class FabricItemModelCodecs2612 {
    private FabricItemModelCodecs2612() { }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }

    static void register() {
        var models = SpecialModelRenderersAccessor.essenceAscendance$models();
        models.put(id("machine_item"), EssenceMachineItemRenderer.Unbaked.MAP_CODEC);
        models.put(id("ascendance_shield"), AscendanceShieldSpecialRenderer2612.Unbaked.MAP_CODEC);

        var properties = RangeSelectItemModelPropertiesAccessor.essenceAscendance$properties();
        properties.put(id("tier"), ClientItemProperty2612.TIER.type());
        properties.put(id("essentium_essence"), ClientItemProperty2612.ESSENTIUM_ESSENCE.type());
        properties.put(id("blocking"), ClientItemProperty2612.BLOCKING.type());
        properties.put(id("cast"), ClientItemProperty2612.CAST.type());
        properties.put(id("pulling"), ClientItemProperty2612.PULLING.type());
        properties.put(id("pull"), ClientItemProperty2612.PULL.type());

        var tints = ItemTintSourcesAccessor.essenceAscendance$tints();
        for (int layer = 0; layer < ClientItemTint2612.EQUIPMENT.length; layer++)
            tints.put(id("equipment_layer_" + layer), ClientItemTint2612.EQUIPMENT[layer].type());
        for (int layer = 0; layer < ClientItemTint2612.CARRIER.length; layer++)
            tints.put(id("carrier_layer_" + layer), ClientItemTint2612.CARRIER[layer].type());
    }
}
