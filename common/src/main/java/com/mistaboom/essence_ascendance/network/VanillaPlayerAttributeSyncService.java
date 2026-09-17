package com.mistaboom.essence_ascendance.network;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pushes a server-resolved player attribute through Minecraft's own attribute
 * packet immediately after an Essence runtime modifier changes. The client
 * still owns its normal vanilla cooldown/rendering logic; no client-side
 * attribute shadow or alternate HUD indicator is involved.
 *
 * <p>This is deliberately generic so future permanent equipment effects and
 * transient skills can use the same native synchronization path for any
 * client-visible player attribute.</p>
 */
public final class VanillaPlayerAttributeSyncService {
    private VanillaPlayerAttributeSyncService() { }

    /** Native vitals snapshot after an atomic capacity/health update; never invent a second health account. */
    public static void syncOwnerHealth(ServerPlayer player) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
    }

    public static void syncOwner(ServerPlayer player, Holder<Attribute> attribute) {
        syncOwner(player, List.of(attribute));
    }

    public static void syncOwner(ServerPlayer player, Collection<Holder<Attribute>> attributes) {
        if (attributes.isEmpty()) return;
        List<AttributeInstance> instances = new ArrayList<>();
        for (Holder<Attribute> attribute : new LinkedHashSet<>(attributes)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) instances.add(instance);
        }
        if (!instances.isEmpty()) {
            player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(), instances));
        }
    }
}
