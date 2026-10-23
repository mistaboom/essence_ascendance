package com.mistaboom.essence_ascendance.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Codec-backed item tints for the authored layered models in 26.1.2. */
public final class ClientItemTint2612 implements ItemTintSource {
    public static final ClientItemTint2612[] EQUIPMENT = array(Kind.EQUIPMENT, 5);
    public static final ClientItemTint2612[] CARRIER = array(Kind.CARRIER, 2);

    private enum Kind { EQUIPMENT, CARRIER }
    private final Kind kind;
    private final int layer;
    private final MapCodec<ClientItemTint2612> codec;

    private ClientItemTint2612(Kind kind, int layer) {
        this.kind = kind;
        this.layer = layer;
        this.codec = MapCodec.unit(this);
    }

    private static ClientItemTint2612[] array(Kind kind, int count) {
        ClientItemTint2612[] values = new ClientItemTint2612[count];
        for (int layer = 0; layer < count; layer++) values[layer] = new ClientItemTint2612(kind, layer);
        return values;
    }

    @Override public MapCodec<ClientItemTint2612> type() { return codec; }

    @Override public int calculate(ItemStack stack, ClientLevel level, LivingEntity holder) {
        if (kind == Kind.CARRIER) return EssentiumCarrierVisuals.itemTint(stack, layer);
        if (holder instanceof Player player)
            return PersonalizedEquipmentClient.itemTint(List.of(player), stack, layer);
        return PersonalizedEquipmentClient.itemTint(stack, layer);
    }
}
