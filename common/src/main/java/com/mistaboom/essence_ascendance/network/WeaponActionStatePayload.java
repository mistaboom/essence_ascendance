package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.equipment.*;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Presentation consumes solved poses, including collision shortening. It never simulates authority a second time. */
public record WeaponActionStatePayload(ResourceLocation dimension,int entityId,UUID owner,WeaponActionRuntime.Snapshot state,
                                       EquipmentAppearance appearance)
        implements CustomPacketPayload {
    public WeaponActionStatePayload(ResourceLocation dimension,int entityId,UUID owner,WeaponActionRuntime.Snapshot state) {
        this(dimension,entityId,owner,state,new EquipmentAppearance(EquipmentTierVisuals.primaryRgb(state.tier()),
                EquipmentTierVisuals.armorAccentRgb(state.tier()),com.mistaboom.essence_ascendance.visual.ArmorEmission.alpha(state.tier())));
    }
    public static final Type<WeaponActionStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("essence_ascendance","weapon_action_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WeaponActionStatePayload> CODEC = StreamCodec.of((b,p) -> {
        b.writeResourceLocation(p.dimension); b.writeVarInt(p.entityId); b.writeUUID(p.owner);
        var s=p.state;
        b.writeVarLong(s.actionId()); b.writeEnum(s.mode()); b.writeEnum(s.phase());
        b.writeVarLong(s.acceptedAt()); b.writeVarLong(s.readyAt()); b.writeVarLong(s.sampledAt());
        b.writeVarInt(s.selectedSlot()); b.writeEnum(s.tier()); b.writeDouble(s.range());
        writePose(b,s.previous()); writePose(b,s.current());
        var knots=WeaponActionPresentation.wireKnots(s.knots());
        b.writeVarInt(knots.size()); for (var knot:knots) { b.writeDouble(knot.fraction()); writePose(b,knot.pose()); }
        b.writeBoolean(s.physicalContact()); b.writeVarInt(s.contactedEntity());
        b.writeInt(p.appearance.baseRgb()); b.writeInt(p.appearance.accentRgb()); b.writeVarInt(p.appearance.emissionAlpha());
    },b -> {
        var dimension=b.readResourceLocation(); int entity=b.readVarInt(); UUID owner=b.readUUID();
        long id=b.readVarLong(); var mode=b.readEnum(WeaponActionRuntime.Mode.class); var phase=b.readEnum(WeaponActionRuntime.Phase.class);
        long accepted=b.readVarLong(),ready=b.readVarLong(),sampled=b.readVarLong();
        int slot=b.readVarInt(); var tier=b.readEnum(EquipmentTier.class); double range=b.readDouble();
        var previous=readPose(b); var current=readPose(b);
        int count=b.readVarInt();
        if (count<0 || count>WeaponActionPresentation.MAX_KNOTS || count>b.readableBytes()/72) throw new IllegalArgumentException("Invalid solved pose count");
        var knots=new ArrayList<WeaponActionGeometry.Knot>(count);
        for (int i=0;i<count;i++) knots.add(new WeaponActionGeometry.Knot(b.readDouble(),readPose(b)));
        return new WeaponActionStatePayload(dimension,entity,owner,new WeaponActionRuntime.Snapshot(id,mode,phase,
                accepted,ready,sampled,slot,tier,range,previous,current,knots,b.readBoolean(),b.readVarInt()),
                new EquipmentAppearance(b.readInt(),b.readInt(),b.readVarInt()));
    });
    private static void writePose(RegistryFriendlyByteBuf b,WeaponActionGeometry.Pose pose) {
        b.writeDouble(pose.pivot().x); b.writeDouble(pose.pivot().y); b.writeDouble(pose.pivot().z);
        b.writeDouble(pose.yaw()); b.writeDouble(pose.pitch()); b.writeDouble(pose.tipDistance()); b.writeDouble(pose.sweepDegrees()); b.writeDouble(pose.roll());
    }
    private static WeaponActionGeometry.Pose readPose(RegistryFriendlyByteBuf b) {
        return new WeaponActionGeometry.Pose(new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble());
    }
    private static final Map<ServerPlayer,Set<ServerPlayer>> TRACKING=new WeakHashMap<>();
    private static final Map<ServerPlayer,Set<ServerPlayer>> REACHED=new WeakHashMap<>();
    public static final class ClientState {
        private final WeaponActionStatePayload payload;
        private final long receivedAt;
        private final WeaponActionPresentation.Timeline timeline;
        private WeaponActionPresentation.Timeline preceding;
        private ClientState(WeaponActionStatePayload payload,long receivedAt,ClientState previous) {
            this.payload=payload; this.receivedAt=receivedAt;
            if (previous!=null && previous.payload.dimension().equals(payload.dimension()) && previous.state().actionId()==payload.state().actionId()) {
                timeline=previous.timeline; timeline.add(payload.state());preceding=previous.preceding;
            } else {
                boolean continueReturn=previous!=null && previous.payload.dimension().equals(payload.dimension())
                        && previous.state().selectedSlot()==payload.state().selectedSlot()
                        && previous.state().phase()!=WeaponActionRuntime.Phase.CANCELLED
                        && Double.isFinite(previous.timeline.returnAt());
                preceding=continueReturn ? previous.timeline:null;
                timeline=new WeaponActionPresentation.Timeline(payload.state(),receivedAt,
                        continueReturn ? previous.timeline.returnAt():Double.NEGATIVE_INFINITY);
            }
        }
        public WeaponActionStatePayload payload() { return payload; }
        public long receivedAt() { return receivedAt; }
        public WeaponActionRuntime.Snapshot state() { return payload.state(); }
        public WeaponActionPresentation.Frame frame(long now,float partial) {
            var frame=timeline.frame(now,partial);
            if(preceding!=null) {
                if(frame.time()<timeline.prepareAt()) return preceding.frameAt(frame.time());
                preceding=null;
            }
            return frame;
        }
        public WeaponActionGeometry.Pose pose(long now,float partial) {
            return frame(now,partial).pose();
        }
        public WeaponActionRuntime.Phase phase(long now,float partial) { return frame(now,partial).phase(); }
        public double time(long now,float partial) { return frame(now,partial).time(); }
        public double retractAt() { return timeline.retractAt(); }
    }
    private static final Map<UUID,ClientState> CLIENT=new HashMap<>();
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void init() {
        if (dev.architectury.platform.Platform.getEnvironment()==dev.architectury.utils.Env.SERVER)
            NetworkManager.registerS2CPayloadType(TYPE,CODEC);
    }
    public static void sync(ServerPlayer owner) {
        var source=WeaponActionRuntime.snapshot(owner); if (source==null) return;
        var knots=WeaponActionPresentation.wireKnots(source.knots());
        var state=new WeaponActionRuntime.Snapshot(source.actionId(),source.mode(),source.phase(),source.acceptedAt(),
                source.readyAt(),source.sampledAt(),source.selectedSlot(),source.tier(),source.range(),
                source.previous(),source.current(),knots,source.physicalContact(),source.contactedEntity());
        var recipients=new HashSet<>(TRACKING.getOrDefault(owner,Set.of())); recipients.add(owner);
        var reached=REACHED.computeIfAbsent(owner,p -> Collections.newSetFromMap(new WeakHashMap<>()));
        if (WeaponActionPresentation.extended(state.phase())) {
            double radius=owner.server.getPlayerList().getViewDistance()*16.0+16;
            // Discover by physical shaft/blade vicinity, even when the owner entity is not tracked there.
            for (var observer:owner.serverLevel().players()) {
                if (near(state,observer.getEyePosition(),radius)) reached.add(observer);
            }
        }
        recipients.addAll(reached);
        for (var observer:recipients) if (observer.level()==owner.level()) send(owner,observer,state);
        if (!WeaponActionPresentation.extended(state.phase())) REACHED.remove(owner);
    }
    public static boolean near(WeaponActionRuntime.Snapshot state,Vec3 viewer,double radius) {
        for (var knot:state.knots()) {
            var pose=knot.pose(); Vec3 axis=pose.tip().subtract(pose.pivot());
            double t=axis.lengthSqr()==0 ? 0:Math.clamp(viewer.subtract(pose.pivot()).dot(axis)/axis.lengthSqr(),0,1);
            if (viewer.distanceToSqr(pose.pivot().add(axis.scale(t)))<=radius*radius) return true;
        }
        return false;
    }
    private static void send(ServerPlayer owner,ServerPlayer recipient,WeaponActionRuntime.Snapshot state) {
        if (recipient.connection!=null && recipient.connection.isAcceptingMessages() && NetworkManager.canPlayerReceive(recipient,TYPE))
            NetworkManager.sendToPlayer(recipient,new WeaponActionStatePayload(owner.level().dimension().location(),owner.getId(),owner.getUUID(),state,
                    EquipmentAppearance.resolve(owner,owner.getMainHandItem())));
    }
    public static void track(ServerPlayer owner,ServerPlayer observer) {
        TRACKING.computeIfAbsent(owner,p -> Collections.newSetFromMap(new WeakHashMap<>())).add(observer);
        var state=WeaponActionRuntime.snapshot(owner); if (state!=null) send(owner,observer,state);
    }
    public static void untrack(ServerPlayer owner,ServerPlayer observer) { var set=TRACKING.get(owner); if (set!=null) set.remove(observer); }
    public static void forget(ServerPlayer owner) { TRACKING.remove(owner); REACHED.remove(owner); }
    public static void accept(Player owner,WeaponActionRuntime.Snapshot state) {
        accept(new WeaponActionStatePayload(owner.level().dimension().location(),owner.getId(),owner.getUUID(),state,
                EquipmentAppearance.resolve(owner,owner.getMainHandItem())),owner.level().getGameTime());
    }
    public static boolean accept(WeaponActionStatePayload payload,long now) {
        var previous=CLIENT.get(payload.owner()); var state=payload.state();
        if (previous!=null && previous.payload.equals(payload)) return false;
        if (previous==null || !previous.payload.dimension().equals(payload.dimension()) || state.actionId()>previous.state().actionId()
                || state.actionId()==previous.state().actionId() && state.sampledAt()>=previous.state().sampledAt())
            { CLIENT.put(payload.owner(),new ClientState(payload,now,previous)); return true; }
        return false;
    }
    public static Collection<ClientState> presentations(ResourceLocation dimension,long now) {
        CLIENT.values().removeIf(s -> !s.payload.dimension().equals(dimension) || now-s.receivedAt>40);
        return CLIENT.values();
    }
    public static ClientState presentation(Player owner) {
        var state=CLIENT.get(owner.getUUID());
        return state!=null && state.payload.dimension().equals(owner.level().dimension().location()) ? state:null;
    }
    public static ClientState presentation(UUID owner) { return CLIENT.get(owner); }
    public static WeaponActionRuntime.Snapshot clientState(Player owner) { var s=presentation(owner); return s==null ? null : s.state(); }
    public static float readiness(Player player,float partialTick) {
        var cached=presentation(player);
        return cached==null ? 1 : cached.state().readiness(cached.state().sampledAt()+player.level().getGameTime()-cached.receivedAt+partialTick);
    }
    public static void clearClient() { CLIENT.clear(); }
}
