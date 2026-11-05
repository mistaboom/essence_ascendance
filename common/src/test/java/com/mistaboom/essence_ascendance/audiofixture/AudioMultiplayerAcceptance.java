package com.mistaboom.essence_ascendance.audiofixture;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.audio.AudioEngine;
import com.mistaboom.essence_ascendance.client.audio.AudioRuntime;
import com.mistaboom.essence_ascendance.network.FeedbackDispatch;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.ReservoirService;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** External, opt-in fixture only. Two socket clients run the unchanged production mod.
 * File barriers coordinate observation; all tested feedback travels over Minecraft's socket. */
public final class AudioMultiplayerAcceptance {
    private static final Path ROOT=Path.of(System.getProperty("essence.audio.acceptanceDirectory","."));
    private static final String[] NAMES={"EA_A","EA_B"};
    private static final String[] PHASES={"nearby-public","private-a","far-public","dimension-public","overlapping-actors","quiet","native-command",
            "paid-start","paid-late-observer","paid-stop","paid-restart","paid-remove"};
    private static final int[][] EXPECTED={{1,1},{1,0},{1,0},{1,0},{2,2},{0,0},{0,0}};
    private static long started=System.nanoTime();
    private static final java.util.concurrent.atomic.AtomicBoolean EXITING=new java.util.concurrent.atomic.AtomicBoolean();
    private static int phase=-1,step,ticks,checks;
    private static final net.minecraft.core.BlockPos PAID_POS=new net.minecraft.core.BlockPos(0,98,2);
    private static EssenceCrucibleBlockEntity paidSource;
    private static Map<net.minecraft.resources.ResourceLocation,java.math.BigInteger> paidInitial;
    private static Map<net.minecraft.resources.ResourceLocation,java.math.BigInteger> stoppedUnits;
    private static final Map<net.minecraft.resources.ResourceLocation,Long> availableInitial=new HashMap<>(),observerInitial=new HashMap<>();
    private static final Map<net.minecraft.resources.ResourceLocation,Long> stoppedAvailable=new HashMap<>();
    public static void server() {
        System.out.println("AudioMultiplayerAcceptance: production origin="+EssenceAscendance.class.getProtectionDomain().getCodeSource().getLocation());
        TickEvent.SERVER_POST.register(server->{ try { serverTick(server); } catch(Throwable failure) { fail(failure); }});
    }
    private static void serverTick(MinecraftServer server) throws Exception {
        timeout(); ticks++;
        var players=server.getPlayerList();
        ServerPlayer a=players.getPlayerByName(NAMES[0]),b=players.getPlayerByName(NAMES[1]);
        if(a==null || b==null) return;
        if(phase<0) {
            if(!Files.exists(ROOT.resolve("EA_A.joined")) || !Files.exists(ROOT.resolve("EA_B.joined"))) return;
            check(!a.getUUID().equals(b.getUUID()),"distinct server UUIDs");
            check(a.connection!=b.connection,"distinct socket listener instances");
            System.out.println("AudioMultiplayerAcceptance: socket players="+a.getUUID()+","+b.getUUID());
            command(server,"gamemode creative @a");
            for(var player:List.of(a,b)) { player.getAbilities().flying=true; player.onUpdateAbilities(); }
            phase=0; step=0;
        }
        if(phase>=PHASES.length) return;
        String id=phase+"-"+PHASES[phase];
        if(step==0) {
            platform(server.getLevel(net.minecraft.world.level.Level.OVERWORLD),0);
            platform(server.getLevel(phase==3?net.minecraft.world.level.Level.NETHER:net.minecraft.world.level.Level.OVERWORLD),phase==2 || phase==7?200:2);
            command(server,"execute in minecraft:overworld run tp EA_A 0 99 0");
            command(server,"execute in "+(phase==3?"minecraft:the_nether":"minecraft:overworld")+" run tp EA_B "+(phase==2 || phase==7?"200":"2")+" 99 0");
            write("phase",id); step=1; ticks=0;
        } else if(step==1 && ticks>40 && ready(id,"ready")) {
            check(a.position().distanceToSqr(new Vec3(.5,99,.5))<.25,"receiver remains on prepared platform before dispatch: "+a.position());
            check(b.position().distanceToSqr(new Vec3(phase==2 || phase==7?200.5:2.5,99,.5))<.25,"observer remains at assigned range before dispatch: "+b.position());
            if(phase>=7) paidAction(server,a,b);
            else if(phase==6) command(server,"execute at EA_A run playsound essence_ascendance:weapon.cast.release master @a ~ ~ ~ 0.5 1.1");
            else if(phase==1) FeedbackDispatch.player(a,"interface.acknowledge");
            else if(phase==4) {
                FeedbackDispatch.world(a.serverLevel(),"weapon.cast.release",a,a.getEyePosition());
                FeedbackDispatch.world(a.serverLevel(),"weapon.cast.release",b,a.getEyePosition());
            } else if(phase!=5) FeedbackDispatch.world(a.serverLevel(),"weapon.cast.release",a,a.getEyePosition());
            write("sent",id); step=2; ticks=0;
        } else if(step==2 && ready(id,"observed")) {
            if(phase==9 || phase==11) {
                var saved=EssenceSavedData.get(server);
                check(saved.reservoirs().read(a.getUUID()).units().equals(stoppedUnits),id+" reservoir remains unchanged during stopped observation window");
                for(var type:EssenceTypes.ORDERED) check(saved.getPlayerData(a.getUUID()).getAvailable(type)==stoppedAvailable.get(type.id()),id+" no continued personal grant for "+type.id());
            }
            for(int i=0;i<NAMES.length;i++) {
                String data=Files.readString(ROOT.resolve(NAMES[i]+".observed"));
                if(phase>=7) {
                    String[] values=data.split("\n");
                    int wanted=(phase==9 || phase==11 || phase==7 && i==1)?0:1;
                    check(Integer.parseInt(values[1])==wanted,id+" "+NAMES[i]+" owned source count="+values[1]);
                    check(Integer.parseInt(values[2])==wanted,id+" "+NAMES[i]+" native active body count="+values[2]);
                    continue;
                }
                int actual=Integer.parseInt(data.split("\\n")[1]);
                check(actual==EXPECTED[phase][i],id+" "+NAMES[i]+" expected="+EXPECTED[phase][i]+" actual="+actual);
                int nativeCount=Integer.parseInt(data.split("\\n")[2]);
                check(nativeCount==(phase==6?1:EXPECTED[phase][i]),id+" "+NAMES[i]+" native playback count="+nativeCount);
            }
            System.out.println("AudioMultiplayerAcceptance: phase PASS "+id);
            phase++; step=0;
            if(phase==PHASES.length) {
                write("complete","PASS "+checks+" server assertions");
                System.out.println("AudioMultiplayerAcceptance: PASS; "+checks+" server assertions; exact packaged distinct socket clients, recipient/range/dimension/overlap and real paid Crucible source lifecycle; listening remains separate");
            }
        }
    }
    private static void platform(net.minecraft.server.level.ServerLevel level,int centerX) {
        for(int x=centerX-1;x<=centerX+1;x++) for(int z=-1;z<=1;z++)
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,98,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
    }
    private static void paidAction(MinecraftServer server,ServerPlayer a,ServerPlayer b) {
        var saved=EssenceSavedData.get(server);
        if(phase==7) {
            command(server,"execute in minecraft:overworld run setblock 0 98 2 essence_ascendance:essence_crucible");
            check(a.serverLevel().getBlockEntity(PAID_POS) instanceof EssenceCrucibleBlockEntity,"real native Crucible placement");
            paidSource=(EssenceCrucibleBlockEntity)a.serverLevel().getBlockEntity(PAID_POS);
            check(paidSource.bindOnPlacement(a),"real placement binds receiving player");
            Map<net.minecraft.resources.ResourceLocation,java.math.BigInteger> funds=new LinkedHashMap<>();
            for(var type:EssenceTypes.ORDERED) {
                funds.put(type.id(),java.math.BigInteger.valueOf(1_000_000).multiply(ReservoirService.SCALE));
                availableInitial.put(type.id(),saved.getPlayerData(a.getUUID()).getAvailable(type));
                observerInitial.put(type.id(),saved.getPlayerData(b.getUUID()).getAvailable(type));
            }
            check(saved.reservoirs().administer(a.getUUID(),funds),"fixture operator funds actual reservoir");
            paidInitial=saved.reservoirs().read(a.getUUID()).units();
            saved.getPlayerData(a.getUUID()).setCrucibleChannelSelection(63);
            check(paidSource.startChanneling(a),"authorized receiver starts real scheduler claim");
        } else if(phase==9) {
            paidSource.stopChanneling(a.getUUID());
            paidConservation(saved,a,b);
        } else if(phase==10) {
            check(paidSource.startChanneling(a),"authorized receiver restarts real scheduler claim");
        } else if(phase==11) {
            a.serverLevel().removeBlock(PAID_POS,false);
            check(a.serverLevel().getBlockEntity(PAID_POS)==null,"real source block removed");
            paidConservation(saved,a,b);
        }
        if(phase==9 || phase==11) {
            stoppedUnits=saved.reservoirs().read(a.getUUID()).units();
            for(var type:EssenceTypes.ORDERED) stoppedAvailable.put(type.id(),saved.getPlayerData(a.getUUID()).getAvailable(type));
        }
    }
    private static void paidConservation(EssenceSavedData saved,ServerPlayer a,ServerPlayer b) {
        var remaining=saved.reservoirs().read(a.getUUID());
        for(var type:EssenceTypes.ORDERED) {
            long granted=saved.getPlayerData(a.getUUID()).getAvailable(type)-availableInitial.get(type.id());
            var debited=paidInitial.get(type.id()).subtract(remaining.units(type.id()));
            check(granted>0 && debited.equals(java.math.BigInteger.valueOf(granted).multiply(ReservoirService.SCALE)),"paid "+type.id()+" exact reservoir debit equals personal grant: "+granted);
            check(saved.getPlayerData(b.getUUID()).getAvailable(type)==observerInitial.get(type.id()),"observer gains no personal "+type.id());
            System.out.println("AudioMultiplayerAcceptance: paid conservation "+type.id()+" granted="+granted+" debitedUnits="+debited);
        }
    }
    static boolean ready(String id,String suffix) throws Exception {
        for(String name:NAMES) { Path p=ROOT.resolve(name+"."+suffix); if(!Files.exists(p) || !matches(Files.readString(p),id)) return false; }
        return true;
    }
    /** One token boundary for plain phase markers and records carrying newline-separated observations. */
    static boolean matches(String record,String token) { return record.equals(token) || record.startsWith(token+"\n"); }
    static void verifyBarrierRecords() throws java.io.IOException {
        String token="1-late-connection";
        if(!matches(token,token) || !matches(token+"\n",token) || !matches(token+"\nobservation",token)
                || matches("10-late-connection",token) || matches(token+"-stale",token) || matches("",token)
                || matches("old\n"+token,token)) throw new AssertionError("Native fixture barrier token boundaries");
        System.out.println("AudioMultiplayerAcceptance: barrier record regression PASS; plain, terminated and payload tokens; stale/prefix records rejected");
        int[] attempts={0};
        retryAtomicMove(()->{if(++attempts[0]<3) throw new AccessDeniedException("transient fixture reader");},3,0);
        if(attempts[0]!=3) throw new AssertionError("Transient atomic move retry did not complete");
        attempts[0]=0;
        try { retryAtomicMove(()->{attempts[0]++; throw new AccessDeniedException("persistent fixture reader");},3,0); throw new AssertionError("Persistent move failure was ignored"); }
        catch(AccessDeniedException expected) { if(attempts[0]!=3) throw new AssertionError("Atomic move retry is not bounded"); }
        attempts[0]=0;
        try { retryAtomicMove(()->{attempts[0]++; throw new java.io.IOException("not a sharing violation");},3,0); throw new AssertionError("Nonsharing IO failure was ignored"); }
        catch(java.io.IOException expected) { if(attempts[0]!=1) throw new AssertionError("Nonsharing IO failure retried"); }
        System.out.println("AudioMultiplayerAcceptance: atomic move regression PASS; transient reader retry, persistent bounded failure and unrelated IO propagation");
    }
    private static void command(MinecraftServer server,String command) { server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),command); }
    public static void client() { Client.install(); }
    /** Called only by the test mixin when the real production frame reaches its supplement pass. */
    public static void actualFrame() { Client.frame(); }
    public static void actualFrameStart() { Client.supplementSeen=false; }
    public static void actualNativeText(Object text) { Client.nativeText(text); }
    private static final class Client {
        private static AudioEngine engine;
        private static String name,phase="",sent="";
        private static int joined,observing,nativeCount;
        private static boolean finishing;
        private static boolean nativeFrameObserved;
        private static int reversedFrames, eligibleFrames, ticketFrames, nativeTextFrames;
        private static boolean supplementSeen;
        private static long lastFrame;
        private static AudioEngine.Voice paidBody;
        private static final Set<AudioEngine.Voice> paidBodies=Collections.newSetFromMap(new IdentityHashMap<>());
        static void frame() {
            lastFrame=System.nanoTime();
            if(engine==null || !phase.startsWith("0-") || nativeCount==0) return;
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(mc.screen!=null || mc.options.hideGui) return;
            eligibleFrames++;
            supplementSeen=true;
            try {
                var field=com.mistaboom.essence_ascendance.client.audio.CaptionRouter.class.getDeclaredField("STATE");
                field.setAccessible(true);
                var captions=(com.mistaboom.essence_ascendance.audio.SemanticCaptions)field.get(null);
                for(var ticket:captions.lines()) {
                    if(!ticket.cue.id().getPath().equals("weapon.cast.release")) continue;
                    ticketFrames++;
                    if(!captions.supplemental(ticket,true)) nativeFrameObserved=true;
                }
            } catch(ReflectiveOperationException failure) { fail(failure); }
        }
        static void nativeText(Object text) {
            if(engine==null || !phase.startsWith("0-") || nativeCount==0) return;
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(mc.screen!=null || mc.options.hideGui) return;
            try {
                var field=com.mistaboom.essence_ascendance.client.audio.CaptionRouter.class.getDeclaredField("STATE");
                field.setAccessible(true);
                var captions=(com.mistaboom.essence_ascendance.audio.SemanticCaptions)field.get(null);
                for(var ticket:captions.lines()) if(ticket.cue.id().getPath().equals("weapon.cast.release") && Objects.equals(ticket.nativeText,text)) {
                    nativeTextFrames++;
                    if(supplementSeen) reversedFrames++;
                }
            } catch(ReflectiveOperationException failure) { fail(failure); }
        }
        static void install() {
            ClientTickEvent.CLIENT_POST.register(mc->{ try {
                timeout();
                mc.options.pauseOnLostFocus=false;
                if(finishing) {
                    if(mc.level==null && mc.player==null) {
                        check(engine.sources()==0 && engine.voices()==0 && engine.history()==0,"disconnect clears audio ownership");
                        System.out.println("AudioMultiplayerAcceptance: CLIENT PASS; disconnect cleanup; no human listening claim");
                        mc.stop();
                    }
                    return;
                }
                if(mc.level==null || mc.player==null || mc.screen!=null || ++joined<40) return;
                if(engine==null) {
                    var field=AudioRuntime.class.getDeclaredField("ENGINE"); field.setAccessible(true); engine=(AudioEngine)field.get(null);
                    name=mc.player.getName().getString();
                    check(Arrays.asList(NAMES).contains(name),"expected isolated player name");
                    check(mc.getSingleplayerServer()==null,"remote socket world, no integrated server");
                    mc.options.showSubtitles().set(true);
                    mc.getSoundManager().addListener((sound,event,range)->{
                        //? if >=26.1.2
                        /*if(sound.getIdentifier().getNamespace().equals("essence_ascendance")) nativeCount++;*/
                        //? if <26.1.2
                        if(sound.getLocation().getNamespace().equals("essence_ascendance")) nativeCount++;
                    });
                    System.out.println("AudioMultiplayerAcceptance: CLIENT joined "+name+" UUID="+mc.player.getUUID()+" production origin="+EssenceAscendance.class.getProtectionDomain().getCodeSource().getLocation());
                    write(name+".joined",mc.player.getUUID().toString());
                }
                if(Files.exists(ROOT.resolve("complete"))) { AudioPerformanceAcceptance.run(); finishing=true; mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Audio socket fixture complete")); return; }
                if(!Files.exists(ROOT.resolve("phase"))) return;
                String current=Files.readString(ROOT.resolve("phase"));
                if(!current.equals(phase)) {
                    if(System.nanoTime()-lastFrame>java.util.concurrent.TimeUnit.SECONDS.toNanos(1)) return;
                    int next=Integer.parseInt(current.split("-")[0]);
                    double expectedX=name.equals("EA_A")?.5:next==2 || next==7?200.5:2.5;
                    var expectedLevel=name.equals("EA_B") && next==3?net.minecraft.world.level.Level.NETHER:net.minecraft.world.level.Level.OVERWORLD;
                    if(!mc.level.dimension().equals(expectedLevel) || mc.player.position().distanceToSqr(new Vec3(expectedX,99,.5))>=.25) return;
                    phase=current; if(Integer.parseInt(phase.split("-")[0])<=7) engine.clear(); nativeCount=0; write(name+".ready",phase+"\n"); observing=0;
                }
                if(!Files.exists(ROOT.resolve("sent"))) return;
                current=Files.readString(ROOT.resolve("sent"));
                int phaseIndex=Integer.parseInt(phase.split("-")[0]);
                if(current.equals(phase) && !current.equals(sent) && ++observing>=(phaseIndex>=7?80:15)) {
                    if(phase.startsWith("0-")) {
                        String frameEvidence="observed="+nativeFrameObserved+" reversed="+reversedFrames+" eligibleFrames="+eligibleFrames+" ticketFrames="+ticketFrames+" nativeTextFrames="+nativeTextFrames+" screen="+mc.screen+" hidden="+mc.options.hideGui+" subtitles="+mc.options.showSubtitles().get()+" nativeCallbacks="+nativeCount+" player="+mc.player.position();
                        check(nativeFrameObserved && reversedFrames==0,"actual production HUD draws native text before deciding supplemental text; "+frameEvidence);
                        System.out.println("AudioMultiplayerAcceptance: actual HUD frame order PASS; "+frameEvidence+"; observed native text before supplement without manually rendering either overlay");
                    }
                    sent=current;
                    if(phaseIndex>=7) {
                        paidObservation(mc,phaseIndex);
                        return;
                    }
                    write(name+".observed",phase+"\n"+engine.history()+"\n"+nativeCount+"\n");
                    System.out.println("AudioMultiplayerAcceptance: CLIENT observation "+phase+" history="+engine.history()+" native="+nativeCount+" voices="+engine.voices());
                }
            } catch(Throwable failure) { fail(failure); }});
        }
        static void paidObservation(net.minecraft.client.Minecraft mc,int phaseIndex) throws Exception {
            int wanted=(phaseIndex==9 || phaseIndex==11 || phaseIndex==7 && name.equals("EA_B"))?0:1;
            var field=AudioEngine.class.getDeclaredField("sources"); field.setAccessible(true);
            var sources=(Map<?,?>)field.get(engine);
            int nativeActive=0;
            AudioEngine.Voice body=null;
            for(var entry:sources.entrySet()) {
                var key=(AudioEngine.Source)entry.getKey();
                check(key.incarnation() instanceof EssenceCrucibleBlockEntity && key.channel().equals("transfer"),"actual synchronized Crucible owns the continuous source");
                var voiceField=entry.getValue().getClass().getDeclaredField("voice"); voiceField.setAccessible(true);
                body=(AudioEngine.Voice)voiceField.get(entry.getValue());
                if(body!=null) paidBodies.add(body);
                if(body!=null && body.alive() && mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance)body)) nativeActive++;
            }
            check(sources.size()==wanted && engine.voices()==wanted && nativeActive==wanted,"paid source has exactly expected native body: "+phase+" sources="+sources.size()+" voices="+engine.voices()+" active="+nativeActive);
            int expectedCallbacks=(phaseIndex==7 && name.equals("EA_A") || phaseIndex==8 && name.equals("EA_B") || phaseIndex==10)?1:0;
            check(nativeCount==expectedCallbacks,"paid transition starts exactly its required native body: "+phase+" callbacks="+nativeCount+" expected="+expectedCallbacks);
            if(phaseIndex==9 || phaseIndex==11) for(var retired:paidBodies)
                check(!retired.alive() && !mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance)retired),"retired paid native handle is actually stopped, not merely absent from controller ownership: "+phase);
            if(phaseIndex==7) paidBody=body;
            if(phaseIndex==8 && name.equals("EA_A")) check(body==paidBody,"late observer and repeated real transfer snapshots do not restart receiver body");
            write(name+".observed",phase+"\n"+sources.size()+"\n"+nativeActive+"\n");
            System.out.println("AudioMultiplayerAcceptance: real paid CLIENT observation "+phase+" sources="+sources.size()+" nativeActive="+nativeActive+" callbacks="+nativeCount+" retainedNativeHandles="+paidBodies.size()+"; no injected client observer state");
        }
    }
    private static void timeout() { if(System.nanoTime()-started>java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) throw new AssertionError("Socket acceptance timed out"); }
    static void write(String file,String value) throws Exception {
        Path temp=ROOT.resolve(file+".tmp"),target=ROOT.resolve(file);
        Files.writeString(temp,value); retryAtomicMove(()->Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE),250,2_000_000L);
    }
    @FunctionalInterface private interface AtomicMove { void run() throws java.io.IOException; }
    /** Windows readers can briefly deny replacement. Preserve atomic records and propagate persistent failures. */
    private static void retryAtomicMove(AtomicMove action,int attempts,long pauseNanos) throws java.io.IOException {
        for(int attempt=0;;attempt++) {
            try { action.run(); return; }
            catch(AccessDeniedException failure) {
                if(attempt+1>=attempts) throw failure;
                java.util.concurrent.locks.LockSupport.parkNanos(pauseNanos);
                if(Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Interrupted while publishing native fixture barrier");
            }
        }
    }
    private static void check(boolean valid,String message) { if(!valid) throw new AssertionError(message); checks++; }
    /** Shutdown hooks may join the native server thread; never make that thread itself wait inside System.exit. */
    static void fail(Throwable failure) {
        if(!EXITING.compareAndSet(false,true)) return;
        failure.printStackTrace();
        new Thread(()->System.exit(1),"EA native fixture failure exit").start();
    }
}
