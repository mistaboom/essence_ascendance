package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Shared server decision for presentation; the client never classifies offhand equipment. */
public record TwoHandedPolicyPayload(ResourceLocation dimension, int entityId, UUID owner,
                                    TwoHandedEquipmentService.Decision decision) implements CustomPacketPayload {
    public static final Type<TwoHandedPolicyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("essence_ascendance", "two_handed_policy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TwoHandedPolicyPayload> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeResourceLocation(p.dimension()); b.writeVarInt(p.entityId()); b.writeUUID(p.owner());
                var d = p.decision(); b.writeEnum(d.status()); b.writeBoolean(d.twoHanded()); b.writeBoolean(d.actionWeapon());
                b.writeBoolean(d.offhandUse()); b.writeBoolean(d.offhandVisible()); b.writeVarLong(d.revision()); b.writeVarLong(d.generation());
            }, b -> new TwoHandedPolicyPayload(b.readResourceLocation(), b.readVarInt(), b.readUUID(), new TwoHandedEquipmentService.Decision(
                    b.readEnum(TwoHandedEquipmentService.Status.class), b.readBoolean(), b.readBoolean(),
                    b.readBoolean(), b.readBoolean(), b.readVarLong(), b.readVarLong())));
    private static final Map<ServerPlayer, TwoHandedEquipmentService.Decision> SENT = new WeakHashMap<>();
    private static final Map<ServerPlayer, Set<ServerPlayer>> TRACKING = new WeakHashMap<>();
    private static final Map<ServerPlayer, TwoHandedEquipmentService.Decision> BROADCAST = new WeakHashMap<>();
    private static final Map<Player, TwoHandedEquipmentService.Decision> CLIENT = new WeakHashMap<>();
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void init() {
        if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.SERVER)
            NetworkManager.registerS2CPayloadType(TYPE, CODEC);
    }
    public static void sync(ServerPlayer player, TwoHandedEquipmentService.Decision decision) {
        if (!decision.equals(BROADCAST.get(player))) {
            for (var observer : TRACKING.getOrDefault(player, Set.of())) send(player, observer, decision);
            BROADCAST.put(player, decision);
        }
        if (!decision.equals(SENT.get(player)) && send(player, player, decision)) SENT.put(player, decision);
    }
    private static boolean send(ServerPlayer owner, ServerPlayer recipient, TwoHandedEquipmentService.Decision decision) {
        // Automation players may have a listener with no channel; capability negotiation needs a live play connection.
        if (recipient.connection == null || !recipient.connection.isAcceptingMessages()
                || !NetworkManager.canPlayerReceive(recipient, TYPE)) return false;
        NetworkManager.sendToPlayer(recipient, new TwoHandedPolicyPayload(owner.level().dimension().location(),
                owner.getId(), owner.getUUID(), decision));
        return true;
    }
    public static void track(ServerPlayer owner, ServerPlayer observer) {
        TRACKING.computeIfAbsent(owner, p -> Collections.newSetFromMap(new WeakHashMap<>())).add(observer);
        TwoHandedEquipmentService.reconcile(owner);
        send(owner, observer, TwoHandedEquipmentService.decision(owner));
    }
    public static void untrack(ServerPlayer owner, ServerPlayer observer) {
        var tracking = TRACKING.get(owner); if (tracking != null) tracking.remove(observer);
    }
    public static void forget(ServerPlayer player) { SENT.remove(player); BROADCAST.remove(player); TRACKING.remove(player); }
    public static void accept(Player player, TwoHandedEquipmentService.Decision decision) { CLIENT.put(player, decision); }
    public static void clearClient() { CLIENT.clear(); }
    public static TwoHandedEquipmentService.Decision clientDecision(Player player) {
        return CLIENT.getOrDefault(player, new TwoHandedEquipmentService.Decision(
                TwoHandedEquipmentService.Status.PENDING, false, false, false, true, 0, -1));
    }
}
