package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.audio.*;
import com.mistaboom.essence_ascendance.network.FeedbackDispatch;
import com.mistaboom.essence_ascendance.network.WorldVisualEventPayload;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleChannelService;
import com.mistaboom.essence_ascendance.infuser.*;
import com.mistaboom.essence_ascendance.pylon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Actual dispatch and accepted gameplay in the established in-memory native world/network fixture. */
public final class NativeAudioRoutingTest {
    private static int checks;
    public static void run() throws Exception {
        var f=new ProjectileNativeInterceptionTest.Fixture(); var other=new ProjectileNativeInterceptionTest.Fixture();
        try {
            NativeGuardOutcomeTest.registry(f); NativeGuardOutcomeTest.registry(other);
            f.player(new Vec3(8,0,0),"near_listener");
            f.player(new Vec3(16,0,0),"edge_listener");
            f.player(new Vec3(17,0,0),"far_listener");
            ProjectileNativeInterceptionTest.set(net.minecraft.world.level.Level.class,other.level,"dimension",net.minecraft.world.level.Level.NETHER);
            other.level.memoryServer=f.server;
            ProjectileNativeInterceptionTest.set(net.minecraft.server.level.ServerPlayer.class,other.player,"server",f.server);
            ProjectileNativeInterceptionTest.set(net.minecraft.server.MinecraftServer.class,f.server,"levels",
                    Map.of(net.minecraft.world.level.Level.OVERWORLD,f.level,net.minecraft.world.level.Level.NETHER,other.level));
            // The base fixture aliases global/level lists for its single-world tests. Separate them here.
            ProjectileNativeInterceptionTest.set(net.minecraft.server.level.ServerLevel.class,f.level,"players",new ArrayList<>(f.onlinePlayers));
            f.onlinePlayers.add(other.player); f.players.put(other.player.getUUID(),other.player);
            check(other.player.server==f.player.server && other.player.level()!=f.player.level(),"different-dimension listener belongs to the same native server");
            drain(f); drain(other);
            FeedbackDispatch.player(f.player,"interface.acknowledge");
            FeedbackDispatch.world(f.level,"weapon.cast.release",Vec3.ZERO);
            FeedbackDispatch.world(f.level,"weapon.cast.release",f.player,Vec3.ZERO);
            check(drain(f)==0,"private, block and entity feedback all honor negotiated shared-envelope capability");
            for(var actor:f.onlinePlayers) advertiseFeedback(actor);
            FeedbackDispatch.player(f.player,"interface.acknowledge");
            check(sounds(f,0).size()==1 && sounds(f,1).isEmpty() && sounds(f,2).isEmpty() && sounds(f,3).isEmpty() && sounds(other,0).isEmpty(),"private acknowledgement reaches only exact actor");
            FeedbackDispatch.world(f.level,"weapon.cast.release",Vec3.ZERO);
            check(sounds(f,0).size()==1 && sounds(f,1).size()==1 && sounds(f,2).size()==1 && sounds(f,3).isEmpty() && sounds(other,0).isEmpty(),"world routing includes policy edge, excludes far and other dimension");
            FeedbackDispatch.world(f.level,"interface.acknowledge",Vec3.ZERO); FeedbackDispatch.player(f.player,"weapon.cast.release");
            check(drain(f)==0,"audience mismatches cannot widen private/world scope");
            entitySources(f,other);

            var caster=new ItemStack(AscendanceItems.ASCENDANCE_CASTER.get());
            EquipmentTierData.setTier(caster,EquipmentTier.TRANSCENDENT);
            f.player.setItemInHand(InteractionHand.MAIN_HAND,caster); EquipmentAttributeService.sync(f.player);
            f.level.rejectEntitySpawns=true;
            check(!EquipmentWeaponService.castMagic(f.player,InteractionHand.MAIN_HAND,caster).castPerformed(),"failed real projectile spawn rejects cast");
            check(sounds(f,0).isEmpty() && sounds(f,1).isEmpty(),"spawn rejection has no release feedback");
            f.level.rejectEntitySpawns=false;
            check(EquipmentWeaponService.castMagic(f.player,InteractionHand.MAIN_HAND,caster).castPerformed(),"real accepted caster spawn");
            var cast=sounds(f,0);
            check(count(cast,"weapon.cast.release")==1 && count(sounds(f,1),"weapon.cast.release")==1
                    && cast.getFirst().envelope()!=null && cast.getFirst().source()==f.player.getId(),"accepted cast releases once with authoritative actor identity for actor and observer");
            check(!EquipmentWeaponService.castMagic(f.player,InteractionHand.MAIN_HAND,caster).castPerformed() && sounds(f,0).isEmpty(),"cooldown rejection cannot replay release");
            drain(f);

            f.level.memoryMachines=new HashMap<>(); f.level.memoryBlocks=new HashMap<>();
            var source=NativeMachineBindingTest.crucible(f,new BlockPos(1,0,0),f.player);
            source.setChannelingRequested(f.player,true);
            var first=EssenceCrucibleChannelService.links(f.player).getFirst();
            check(sounds(f,0).isEmpty() && sounds(f,1).isEmpty(),"accepted channel Start remains intentionally silent");
            source.setChannelingRequested(f.player,true);
            check(EssenceCrucibleChannelService.links(f.player).getFirst().generation()==first.generation() && sounds(f,0).isEmpty(),"duplicate Start retains authoritative session and stays quiet");
            f.saved.getPlayerData(f.player.getUUID()).setCrucibleChannelSelection(0);
            source.setChannelingRequested(f.player,true);
            check(count(sounds(f,0),"interface.denied")==1 && EssenceCrucibleChannelService.isChanneling(source,f.player.getUUID()),"invalid Start denies privately and preserves existing source");
            f.saved.getPlayerData(f.player.getUUID()).setCrucibleChannelSelection(63);
            source.setChannelingRequested(f.player,false);
            check(sounds(f,0).isEmpty() && sounds(f,1).isEmpty(),"explicit committed Stop remains intentionally silent");
            source.setChannelingRequested(f.player,false); check(sounds(f,0).isEmpty(),"duplicate Stop stays quiet");

            // Normal registered-machine work: empty source cannot complete; a real committed batch can.
            var machine=NativeMachineBindingTest.preparedInfuser(f,new BlockPos(0,0,1),source,
                    new ItemStack(com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent.LATENT_NUGGET.get()));
            infuserProcessingButtons(f,other,machine);
            drain(f);
            NativeMachineBindingTest.tickChannels(f,source,List.of(machine),20);
            check(count(sounds(f,0),"process.complete")==0,"waiting Infuser cannot announce completion");
            var units=new LinkedHashMap<net.minecraft.resources.ResourceLocation,java.math.BigInteger>();
            for(var essence:com.mistaboom.essence_ascendance.essence.EssenceTypes.ORDERED)
                units.put(essence.id(),java.math.BigInteger.valueOf(100000).multiply(com.mistaboom.essence_ascendance.data.ReservoirService.SCALE));
            f.saved.reservoirs().administer(f.player.getUUID(),units);
            int ticks=0;
            while(machine.getItem(com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity.OUTPUT_SLOT).isEmpty() && ticks++<5000)
                NativeMachineBindingTest.tickChannels(f,source,List.of(machine),1);
            check(ticks<5000 && count(sounds(f,0),"process.complete")==0,"real paid output commit preserves approved completion silence");
            NativeMachineBindingTest.tickChannels(f,source,List.of(machine),20);
            check(count(sounds(f,0),"process.complete")==0,"idle output retention cannot replay completion");
            focusInstallation(f, other);
            System.out.println("NativeAudioRoutingTest: PASS; "+checks+" assertions; real packet recipients, accepted/rejected cast, channel/completion silence and Focus hand/menu outcomes on both hosts; no socket/world/device/audition claim");
        } finally { f.close(); other.close(); }
    }
    private static void infuserProcessingButtons(ProjectileNativeInterceptionTest.Fixture f,
                                                 ProjectileNativeInterceptionTest.Fixture other,
                                                 EssenceInfuserBlockEntity machine) {
        var menu=new EssenceInfuserMenu(92,f.player.getInventory(),machine);
        f.player.containerMenu=menu;
        drain(f); drain(other);
        for(boolean requested:new boolean[]{false,true,false}) {
            check(menu.clickMenuButton(f.player,EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING)
                    && machine.processingEnabled()==requested,"live Infuser menu commits requested Start/Stop");
            check(drain(f)==0 && drain(other)==0,"Infuser Start/Stop adds no custom success cue to its native button click");
        }
        var focus=machine.getItem(EssenceInfuserBlockEntity.FOCUS_SLOT).copy();
        machine.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT,ItemStack.EMPTY);
        check(menu.clickMenuButton(f.player,EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING)
                && !machine.processingEnabled(),"missing Focus rejects live Infuser Start");
        check(count(sounds(f,0),"interface.denied")==1 && sounds(f,1).isEmpty() && sounds(other,0).isEmpty(),
                "rejected Infuser Start retains actor-only denial");
        machine.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT,focus);
        check(menu.clickMenuButton(f.player,EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING)
                && machine.processingEnabled() && drain(f)==0,"restored Focus starts without a custom success cue");
        f.player.containerMenu=f.player.inventoryMenu;
        check(!menu.clickMenuButton(f.player,EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING)
                && machine.processingEnabled() && drain(f)==0,"stale Infuser menu cannot toggle or announce");
        FeedbackDispatch.player(f.player,"interface.acknowledge");
        check(count(sounds(f,0),"interface.acknowledge")==1 && sounds(f,1).isEmpty(),
                "positive acknowledgement stays available for other semantic actions");
    }
    /** Advertise exactly the existing shared feedback channel in the real loader-owned negotiation state. */
    @SuppressWarnings("unchecked")
    private static void advertiseFeedback(net.minecraft.server.level.ServerPlayer player) throws Exception {
        try {
            var addonType=Class.forName("net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon");
            var addonField=Arrays.stream(net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredFields())
                    .filter(field->field.getType()==addonType).findFirst().orElseThrow(); addonField.setAccessible(true);
            var channels=Class.forName("net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon").getDeclaredField("sendableChannels");
            channels.setAccessible(true); ((Set<Object>)channels.get(addonField.get(player.connection))).add(WorldVisualEventPayload.TYPE.id());
        } catch(ClassNotFoundException notFabric) {
            var connection=net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredField("connection"); connection.setAccessible(true);
            var attributes=Class.forName("net.neoforged.neoforge.network.registration.ChannelAttributes");
            ((Set<Object>)attributes.getMethod("getOrCreateAdHocChannels",net.minecraft.network.Connection.class)
                    .invoke(null,connection.get(player.connection))).add(WorldVisualEventPayload.TYPE.id());
        }
        check(dev.architectury.networking.NetworkManager.canPlayerReceive(player,WorldVisualEventPayload.TYPE),"fixture advertises the existing shared envelope through its actual loader");
    }
    private static void entitySources(ProjectileNativeInterceptionTest.Fixture f,ProjectileNativeInterceptionTest.Fixture other) {
        var second=f.onlinePlayers.get(1); var former=second.position(); second.setPos(f.player.position());
        for(String semantic:List.of("weapon.cast.release","weapon.glaive.swipe","weapon.glaive.contact","projectile.intercept","weapon.bolt.impact")) {
            drain(f); drain(other);
            Vec3 emission=new Vec3(.125,.25,.375);
            FeedbackDispatch.world(f.level,semantic,f.player,emission);
            FeedbackDispatch.world(f.level,semantic,second,emission);
            var packets=sounds(f,0); var observers=sounds(f,1); var edge=sounds(f,2); var far=sounds(f,3); var foreign=sounds(other,0);
            check(packets.size()==2 && observers.size()==2 && edge.size()==2 && far.isEmpty() && foreign.isEmpty(),semantic+": concurrent co-located sources route once each within policy range and dimension"
                    +" [actor="+packets.size()+", observer="+observers.size()+", edge="+edge.size()+", far="+far.size()+", otherDimension="+foreign.size()+"]");
            var first=packets.get(0); var next=packets.get(1); var cue=AudioCatalog.get().recipe(semantic);
            check(first.envelope()!=null && next.envelope()!=null && first.source()==f.player.getId() && next.source()==second.getId()
                    && first.source()!=next.source() && first.occurrence()!=next.occurrence(),semantic+": transport preserves distinct authoritative source and occurrence identities");
            check(first.position().equals(emission) && next.position().equals(emission)
                    && first.cue()==cue && next.cue()==cue && first.envelope().visualRadius()==0
                    && first.envelope().originatingTick()==f.level.getGameTime(),semantic+": exact emission position and shared catalog category/levels remain independent of entity position");
        }
        second.setPos(former); drain(f); drain(other);
    }
    private record FocusHost(String name, net.minecraft.world.level.block.entity.BlockEntity block,
                             net.minecraft.world.Container inventory, int slot,
                             java.util.function.Function<net.minecraft.server.level.ServerPlayer,net.minecraft.world.inventory.AbstractContainerMenu> menu,
                             java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> hand) { }
    private static void focusInstallation(ProjectileNativeInterceptionTest.Fixture f, ProjectileNativeInterceptionTest.Fixture other) throws Exception {
        var infuser = new EssenceInfuserBlockEntity(new BlockPos(0,0,2), EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState());
        var pylon = new EssencePylonBlockEntity(new BlockPos(0,0,3), EssencePylonContent.ESSENCE_PYLON.get().defaultBlockState());
        NativeMachineBindingTest.install(f, infuser); NativeMachineBindingTest.install(f, pylon);
        infuser.bindOnPlacement(f.player); pylon.bindOnPlacement(f.player);
        var outsider = f.onlinePlayers.get(1); outsider.setPos(2,0,0);
        var hosts = List.of(
                new FocusHost("Infuser",infuser,infuser,EssenceInfuserBlockEntity.FOCUS_SLOT,
                        actor -> new EssenceInfuserMenu(91,actor.getInventory(),infuser), actor -> infuser.installOrSwapFocus(actor,InteractionHand.MAIN_HAND)),
                new FocusHost("Pylon",pylon,pylon,EssencePylonBlockEntity.FOCUS_SLOT,
                        actor -> new EssencePylonMenu(92,actor.getInventory(),pylon), actor -> pylon.installOrSwapFocus(actor,InteractionHand.MAIN_HAND)));
        for (var host : hosts) {
            drain(f); drain(other);
            var held = focus(EssenceFocusTier.DORMANT); held.setCount(2); f.player.setItemInHand(InteractionHand.MAIN_HAND,held);
            host.hand().accept(f.player);
            check(ItemStack.isSameItemSameComponents(host.inventory().getItem(host.slot()),focus(EssenceFocusTier.DORMANT))
                    && held.getCount()==1,host.name()+": authorized hand installation changes actual inventory");
            focusHeardOnce(f,other,host,": hand installation");
            host.hand().accept(f.player);
            check(drain(f)==0,host.name()+": identical hand Focus may return success without replaying feedback");
            f.player.setItemInHand(InteractionHand.MAIN_HAND,focus(EssenceFocusTier.AWAKENED));
            host.hand().accept(f.player); focusHeardOnce(f,other,host,": hand replacement");

            var before = host.inventory().getItem(host.slot()).copy();
            outsider.setItemInHand(InteractionHand.MAIN_HAND,focus(EssenceFocusTier.RESONANT));
            host.hand().accept(outsider);
            check(ItemStack.matches(before,host.inventory().getItem(host.slot())) && drain(f)==0,host.name()+": foreign hand cannot mutate or announce installation");
            var denied = host.menu().apply(outsider); outsider.containerMenu=denied;
            denied.setCarried(focus(EssenceFocusTier.RESONANT)); denied.clicked(host.slot(),0,net.minecraft.world.inventory.ClickType.PICKUP,outsider);
            check(ItemStack.matches(before,host.inventory().getItem(host.slot())) && drain(f)==0,host.name()+": foreign menu cannot mutate or announce installation");
            outsider.containerMenu=outsider.inventoryMenu;

            host.inventory().setItem(host.slot(),ItemStack.EMPTY);
            check(drain(f)==0,host.name()+": administrative inventory setter has no installation occurrence");
            var menu = host.menu().apply(f.player); f.player.containerMenu=menu;
            menu.setCarried(focus(EssenceFocusTier.DORMANT)); menu.clicked(host.slot(),0,net.minecraft.world.inventory.ClickType.PICKUP,f.player);
            check(!host.inventory().getItem(host.slot()).isEmpty(),host.name()+": real menu click installed Focus");
            focusHeardOnce(f,other,host,": menu installation");
            menu.setCarried(focus(EssenceFocusTier.DORMANT)); menu.clicked(host.slot(),0,net.minecraft.world.inventory.ClickType.PICKUP,f.player);
            check(drain(f)==0,host.name()+": equivalent menu Focus is quiet");
            menu.setCarried(focus(EssenceFocusTier.AWAKENED)); menu.clicked(host.slot(),0,net.minecraft.world.inventory.ClickType.PICKUP,f.player);
            focusHeardOnce(f,other,host,": menu replacement");
            var saved = host.block().saveWithFullMetadata(f.level.registryAccess());
            com.mistaboom.essence_ascendance.verification.BlockEntityTestNbtCompat.load(host.block(),saved,f.level.registryAccess());
            check(drain(f)==0,host.name()+": saved Focus reload cannot replay installation");
            menu.setCarried(ItemStack.EMPTY); menu.clicked(host.slot(),0,net.minecraft.world.inventory.ClickType.PICKUP,f.player);
            check(host.inventory().getItem(host.slot()).isEmpty() && drain(f)==0,host.name()+": removal has no install accent");
            f.player.containerMenu=f.player.inventoryMenu;
            menu.setCarried(focus(EssenceFocusTier.RESONANT)); menu.clicked(host.slot(),0,net.minecraft.world.inventory.ClickType.PICKUP,f.player);
            check(host.inventory().getItem(host.slot()).isEmpty() && drain(f)==0,host.name()+": stale menu cannot install or announce");
        }
    }
    private static ItemStack focus(EssenceFocusTier tier) {
        var stack = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get()); EssenceFocusData.setTier(stack,tier); return stack;
    }
    private static void focusHeardOnce(ProjectileNativeInterceptionTest.Fixture f,ProjectileNativeInterceptionTest.Fixture other,FocusHost host,String action) {
        var packets=sounds(f,0); String label=host.name()+action;
        check(count(packets,"machine.focus.install")==1 && count(sounds(f,1),"machine.focus.install")==1
                && sounds(other,0).isEmpty(),label+": one shared occurrence reaches actor and nearby observer only in its dimension");
        var packet=packets.getFirst(); var center=Vec3.atCenterOf(host.block().getBlockPos());
        var cue=AudioCatalog.get().recipe("machine.focus.install");
        check(packet.position().equals(center) && packet.cue()==cue && packet.source()==-1
                && packet.envelope()!=null && packet.envelope().originatingTick()==f.level.getGameTime(),
                label+": host identity/center and explicit freshness use the shared semantic transport");
        drain(f);
    }
    private record Feedback(AudioCatalog.Cue cue,int source,Vec3 position,long occurrence,
                            WorldVisualEventPayload envelope,ClientboundSoundPacket nativePacket) { }
    private static long count(List<Feedback> packets,String cue) {
        var expected=AudioCatalog.get().cue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("essence_ascendance",cue));
        return packets.stream().filter(packet->packet.cue()==expected).count();
    }
    private static List<Feedback> sounds(ProjectileNativeInterceptionTest.Fixture fixture,int index) {
        var channel=fixture.networkChannels.get(index); channel.runPendingTasks(); List<Feedback> result=new ArrayList<>();
        Object packet; while((packet=channel.readOutbound())!=null) {
            if(packet instanceof ClientboundSoundPacket sound) {
                var cue=AudioCatalog.get().cues().stream().filter(candidate->AudioContent.event(candidate)==sound.getSound().value()).findFirst().orElse(null);
                check(cue==null,"confirmed EA features never send a duplicate native sound packet");
                result.add(new Feedback(cue,-1,new Vec3(sound.getX(),sound.getY(),sound.getZ()),sound.getSeed(),null,sound));
            } else if(packet instanceof ClientboundCustomPayloadPacket custom && custom.payload().type().id().equals(WorldVisualEventPayload.TYPE.id())) {
                var payload=decodeFeedback(custom,fixture);
                var event=payload.event(); var cue=AudioCatalog.get().recipe(event.recipeId().toString(),event.parameterA(),event.parameterB());
                if(cue!=null) result.add(new Feedback(cue,event.sourceEntityId(),event.position(),payload.occurrence(),payload,null));
            }
        }
        return result;
    }
    /** Architectury wraps typed payloads in encoded bytes before the native send boundary. */
    private static WorldVisualEventPayload decodeFeedback(ClientboundCustomPayloadPacket packet,ProjectileNativeInterceptionTest.Fixture fixture) {
        if(packet.payload() instanceof WorldVisualEventPayload payload) return payload;
        try {
            byte[] bytes=(byte[])packet.payload().getClass().getMethod("payload").invoke(packet.payload());
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes),fixture.level.registryAccess());
            try {
                var decoded=WorldVisualEventPayload.CODEC.decode(buffer);
                check(!buffer.isReadable(),"shared feedback codec consumes the actual outgoing transport bytes");
                return decoded;
            } finally { buffer.release(); }
        } catch(ReflectiveOperationException failure) {
            throw new AssertionError("Unsupported outgoing shared-feedback wrapper: "+packet.payload().getClass().getName(),failure);
        }
    }
    private static int drain(ProjectileNativeInterceptionTest.Fixture fixture) { int count=0; for(int i=0;i<fixture.networkChannels.size();i++) count+=sounds(fixture,i).size(); return count; }
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
}
