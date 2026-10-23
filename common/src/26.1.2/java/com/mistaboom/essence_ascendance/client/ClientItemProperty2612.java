package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import com.mistaboom.essence_ascendance.gathering.RodHookAccess;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The 26.1 item-model equivalents of the six 1.21.1 item property predicates. */
public final class ClientItemProperty2612 implements RangeSelectItemModelProperty {
    public static final ClientItemProperty2612 TIER = new ClientItemProperty2612(Kind.TIER);
    public static final ClientItemProperty2612 ESSENTIUM_ESSENCE = new ClientItemProperty2612(Kind.ESSENTIUM_ESSENCE);
    public static final ClientItemProperty2612 BLOCKING = new ClientItemProperty2612(Kind.BLOCKING);
    public static final ClientItemProperty2612 CAST = new ClientItemProperty2612(Kind.CAST);
    public static final ClientItemProperty2612 PULLING = new ClientItemProperty2612(Kind.PULLING);
    public static final ClientItemProperty2612 PULL = new ClientItemProperty2612(Kind.PULL);

    private enum Kind { TIER, ESSENTIUM_ESSENCE, BLOCKING, CAST, PULLING, PULL }
    private final Kind kind;
    private final MapCodec<ClientItemProperty2612> codec;

    private ClientItemProperty2612(Kind kind) {
        this.kind = kind;
        this.codec = MapCodec.unit(this);
    }

    @Override public MapCodec<ClientItemProperty2612> type() { return codec; }

    @Override public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
        LivingEntity entity = owner == null ? null : owner.asLivingEntity();
        return switch (kind) {
            case TIER -> EquipmentTierVisuals.modelPropertyValue(stack);
            case ESSENTIUM_ESSENCE -> EssentiumCarrierVisuals.modelPropertyValue(stack);
            case BLOCKING -> entity != null && entity.getUseItem() == stack
                    && EquipmentShieldService.isUsingShield(entity) ? 1.0F : 0.0F;
            case CAST -> cast(stack, entity);
            case PULLING -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                    ? 1.0F : 0.0F;
            case PULL -> pull(stack, entity);
        };
    }

    private static float cast(ItemStack stack, LivingEntity entity) {
        if (!(entity instanceof Player player) || player.fishing == null) return 0.0F;
        if (player.fishing instanceof RodHookAccess hook && hook.essenceAscendance$rodHand() != 0) {
            InteractionHand hand = hook.essenceAscendance$rodHand() == 1
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            return player.getItemInHand(hand) == stack ? 1.0F : 0.0F;
        }
        return 0.0F;
    }

    private static float pull(ItemStack stack, LivingEntity entity) {
        if (entity == null || !entity.isUsingItem() || entity.getUseItem() != stack) return 0.0F;
        int usedTicks = stack.getUseDuration(entity) - entity.getUseItemRemainingTicks();
        int fullDrawTicks = EquipmentWeaponService.syncedRangedFullDrawTicks(stack);
        return Math.min(1.0F, usedTicks / (float) Math.max(1, fullDrawTicks));
    }
}
