package com.mistaboom.essence_ascendance.audiofixture;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.audio.AudioEngine;
import com.mistaboom.essence_ascendance.crucible.*;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.team.*;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Disposable native gameplay coordinator. Files coordinate phases; gameplay travels over real sockets/C2S. */
public final class AudioGameplaySession {
    private static final Path ROOT=Path.of(System.getProperty("essence.audio.acceptanceDirectory","."));
    private static final boolean FTB=System.getProperty("essence.audio.membership","native").equals("ftb");
    private static final boolean PERFORMANCE_ONLY=Boolean.getBoolean("essence.audio.performanceOnly");
    private static final BlockPos CHANNEL_POS=new BlockPos(0,98,2);
    private static final BlockPos CHANNEL_PYLON=CHANNEL_POS.east();
    private static final String[] INTRO={"owner-channel","late-connection","observer-disconnect","observer-reconnect","outsider-denied",
            "team-create","team-request-or-invite","team-accept","member-channel","member-leave","channel-stop"};
    private static final String[] GAMEPLAY=AudioGameplayAcceptance.phases();
    private static final int PERFORMANCE=INTRO.length+GAMEPLAY.length,TOTAL=PERFORMANCE+6;
    private static final long STARTED=System.nanoTime();
    private static int phase=-1,ticks,offlineTicks,checks;
    private static int lateDiagnosticTicks;
    private static EssenceCrucibleBlockEntity source;
    private static ServerPlayer firstObserver;
    private static Object firstConnection;
    private static UUID observerId;
    private static long paidBaseline,absenceBaseline,memberBaseline,revokedGrant=-1;
    private static Map<ResourceLocation,BigInteger> poolBaseline;
    private static Map<ResourceLocation,Long> personalBaseline;
    private static AudioPaidMachines.Scene dense;
    private static int menuIndex,menuAge,menuReopens;
    private static boolean menuOpened,measuring,gameplayBegun;
    private static Map<String,Long> networkBefore;
    private static AudioNativePerformanceAcceptance.SystemSample systemBefore;
    private static long measureStarted;
    private AudioGameplaySession() { }
    public static void server() {
        System.out.println("AudioGameplaySession: production origin="+EssenceAscendance.class.getProtectionDomain().getCodeSource().getLocation()+" membership="+(FTB?"ftb":"native"));
        TickEvent.SERVER_POST.register(server->{try { serverTick(server); } catch(Throwable failure) { fail(failure); }});
    }
    public static void client() { Client.install(); }
    private static String name(int index) {
        if(index<INTRO.length) return INTRO[index];
        if(index<PERFORMANCE) return GAMEPLAY[index-INTRO.length];
        int local=index-PERFORMANCE; return "native-performance-"+(local/3+1)+"-"+new String[]{"idle-loaded","dense-paid","stopped-loaded"}[local%3];
    }
    private static String id() { return phase+"-"+name(phase); }
    private static boolean marker(String file,String expected) throws Exception {
        var path=ROOT.resolve(file); return Files.exists(path) && AudioMultiplayerAcceptance.matches(Files.readString(path),expected);
    }
    private static void write(String file,String value) throws Exception { AudioMultiplayerAcceptance.write(file,value); }
    private static boolean both(String suffix) throws Exception { return marker("EA_A."+suffix,id()) && marker("EA_B."+suffix,id()); }
    private static EssenceSavedData saved(MinecraftServer server) { return EssenceSavedData.get(server); }
    private static long available(MinecraftServer server,UUID player) {
        long total=0; for(var type:EssenceTypes.ORDERED) total=Math.addExact(total,saved(server).getPlayerData(player).getAvailable(type)); return total;
    }
    private static void placePlayer(ServerPlayer player,double x) {
        for(int xx=-5;xx<=6;xx++) for(int z=-4;z<=7;z++) player.serverLevel().setBlockAndUpdate(new BlockPos(xx,98,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        player.setGameMode(GameType.CREATIVE); player.teleportTo(x,99,.5); player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    private static void serverTick(MinecraftServer server) throws Exception {
        timeout(); ticks++;
        ServerPlayer a=server.getPlayerList().getPlayerByName("EA_A"),b=server.getPlayerList().getPlayerByName("EA_B");
        if(a==null) return;
        if(phase<0) {
            if(!Files.exists(ROOT.resolve("EA_A.joined-0"))) return;
            if(PERFORMANCE_ONLY) {
                if(b==null || !Files.exists(ROOT.resolve("EA_B.joined-0"))) return;
                check(!a.getUUID().equals(b.getUUID()) && a.connection!=b.connection,"performance lane has distinct actual socket clients");
                placePlayer(a,.5); placePlayer(b,2.5); begin(server,a,b,PERFORMANCE); return;
            }
            check(b==null,"observer genuinely absent before source creation");
            placePlayer(a,.5);
            a.serverLevel().setBlockAndUpdate(CHANNEL_POS,EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState());
            source=(EssenceCrucibleBlockEntity)a.serverLevel().getBlockEntity(CHANNEL_POS);
            check(source!=null && source.bindOnPlacement(a),"real Crucible placement binds source");
            a.serverLevel().setBlockAndUpdate(CHANNEL_PYLON,com.mistaboom.essence_ascendance.pylon.EssencePylonContent.ESSENCE_PYLON.get().defaultBlockState());
            var pylon=(com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity)a.serverLevel().getBlockEntity(CHANNEL_PYLON);
            check(pylon!=null && pylon.bindOnPlacement(a),"real focused Pylon binds before initial body starts");
            pylon.setItem(com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity.FOCUS_SLOT,AudioPaidMachines.focus(com.mistaboom.essence_ascendance.pylon.EssenceFocusTier.DORMANT));
            check(pylon.setLink(a,com.mistaboom.essence_ascendance.machine.MachineLink.Mode.LOCKED,new com.mistaboom.essence_ascendance.machine.MachineLink.Source(a.serverLevel().dimension().location(),CHANNEL_POS)),"one actual Pylon supplies two simultaneous paid recipient streams");
            var funds=new LinkedHashMap<ResourceLocation,BigInteger>();
            for(var type:EssenceTypes.ORDERED) funds.put(type.id(),BigInteger.valueOf(1_000_000).multiply(ReservoirService.SCALE));
            check(saved(server).reservoirs().administer(a.getUUID(),funds),"operator funds real authoritative reservoir");
            saved(server).getPlayerData(a.getUUID()).setCrucibleChannelSelection(63);
            poolBaseline=saved(server).reservoirs().read(a.getUUID()).units(); personalBaseline=new LinkedHashMap<>();
            for(var type:EssenceTypes.ORDERED) personalBaseline.put(type.id(),saved(server).getPlayerData(a.getUUID()).getAvailable(type));
            paidBaseline=available(server,a.getUUID()); begin(server,a,b,0); return;
        }
        if(phase>=TOTAL) return;
        if(ticks>5000) throw new AssertionError("Native gameplay phase timed out "+id()+" A="+a.position()+" B="+(b==null?"absent":b.position()));
        if(phase==1 && b!=null && firstObserver==null) {
            // Do not rebuild the platform: it would replace the active machine.
            b.setGameMode(GameType.CREATIVE); b.teleportTo(2.5,99,.5);
            firstObserver=b; firstConnection=b.connection; observerId=b.getUUID();
            check(!a.getUUID().equals(observerId) && a.connection!=b.connection,"late connection is a distinct real socket identity");
            check(available(server,a.getUUID())>absenceBaseline,"paid source continues throughout first observer absence and JVM startup");
        }
        if(phase==1 && b!=null && ++lateDiagnosticTicks<=200 && lateDiagnosticTicks%20==0)
            System.out.println("AudioGameplaySession: LATE_SERVER_DIAGNOSTIC ticks="+lateDiagnosticTicks
                    +" ownerChanneling="+source.isChanneling(a.getUUID())+" ownerAvailable="+available(server,a.getUUID())
                    +" streams="+source.visualState().playerStreams()+" readiness="+saved(server).reservoirs().readiness()
                    +" ownerResolution="+saved(server).resolvePool(a.getUUID())+" observerResolution="+saved(server).resolvePool(b.getUUID())
                    +" ownerPosition="+a.position()+" observerPosition="+b.position());
        if(phase==2) {
            if(b==null) offlineTicks++; else offlineTicks=0;
            if(offlineTicks>=40 && marker("EA_A.observed",id()) && Files.exists(ROOT.resolve("EA_B.disconnected-0"))) {
                check(source.isChanneling(a.getUUID()) && available(server,a.getUUID())>absenceBaseline,"real source continues paid debit during actual socket disconnection");
                conservation(server,a); write("reconnect-observer","source paid while observer offline\n"); begin(server,a,null,3);
            }
            return;
        }
        if(phase==3 && b!=null && b!=firstObserver && !Files.exists(ROOT.resolve("reconnected-server"))) {
            b.setGameMode(GameType.CREATIVE); b.teleportTo(2.5,99,.5);
            check(observerId.equals(b.getUUID()) && b.connection!=firstConnection,"same observer UUID reconnects through a new native socket/player incarnation");
            check(source.isChanneling(a.getUUID()) && available(server,a.getUUID())>absenceBaseline,"source remains paid through reconnect startup interval");
            write("reconnected-server",b.getUUID()+" oldEntity="+firstObserver.getId()+" newEntity="+b.getId()+"\n");
        }
        if(phase>=INTRO.length && phase<PERFORMANCE) {
            if(b==null) return;
            if(!gameplayBegun) {
                if(!both("armed")) return;
                AudioGameplayAcceptance.serverBegin(server,a,b,phase-INTRO.length);
                gameplayBegun=true; ticks=0; write("gameplay-go",id()+"\n");
            }
            AudioGameplayAcceptance.serverTick(server,a,b,phase-INTRO.length);
        }
        if(phase>=PERFORMANCE) { performanceTick(server,a,b); return; }
        if(phase==9 && b!=null && !source.isChanneling(b.getUUID()) && !source.canPlayerUse(b)) {
            long current=available(server,b.getUUID());
            if(revokedGrant<0) revokedGrant=current;
            else check(current==revokedGrant,"departed member receives no further personal grants after authoritative revocation");
        }
        boolean observed=phase==0?marker("EA_A.observed",id()):both("observed");
        if(!observed || ticks<(phase<INTRO.length?60:AudioGameplayAcceptance.observationTicks(phase-INTRO.length)) || !serverReady(server,a,b)) return;
        if(phase<INTRO.length) observeIntro(server,a,b);
        else AudioGameplayAcceptance.serverObserve(server,a,b,phase-INTRO.length);
        System.out.println("AudioGameplaySession: SERVER phase PASS "+id());
        int next=phase+1;
        if(phase==0) {
            // The actor already proved and retained its native paid body. Exercise the real S2C
            // clock path before another client connects; gameplay and source incarnations stay intact.
            var level=a.serverLevel(); long corrected=level.getGameTime()-120;
            //? if >=26.1.2
            /*a.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(corrected,Map.of()));*/
            //? if <26.1.2
            a.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(corrected,level.getDayTime(),true));
            System.out.println("AudioGameplaySession: CLOCK_CORRECTION_SENT actor="+a.getUUID()+" serverGameTime="+level.getGameTime()+" correctedGameTime="+corrected+" paidSourceActive="+source.isChanneling(a.getUUID()));
            absenceBaseline=available(server,a.getUUID()); write("launch-observer","paid source active; no observer connected\n");
        }
        begin(server,a,b,next);
    }
    private static void begin(MinecraftServer server,ServerPlayer a,ServerPlayer b,int next) throws Exception {
        phase=next; ticks=0;
        if(phase>=TOTAL) {
            dense.remove(); if(!PERFORMANCE_ONLY) AudioGameplayAcceptance.prepareHumanScene(a); write("complete","PASS "+checks+" server assertions\n");
            System.out.println(PERFORMANCE_ONLY?"AudioGameplaySession: SERVER PERFORMANCE PASS "+checks+" assertions; actual loaded/paid/stopped sources only; no gameplay, membership transition or reconnect claim"
                    :"AudioGameplaySession: SERVER PASS "+checks+" assertions; real late socket/reconnect, native membership="+(FTB?"ftb":"native")+", paid gameplay and measured native load"); return;
        }
        if(phase==2) { absenceBaseline=available(server,a.getUUID()); offlineTicks=0; }
        if(phase==5) check(!saved(server).resolvePool(a.getUUID()).requireReady().pool().equals(saved(server).resolvePool(b.getUUID()).requireReady().pool()),"outsider has distinct funding membership before command");
        if(phase==6) write("membership-data",saved(server).resolvePool(a.getUUID()).requireReady().pool().id().toString());
        if(phase==7) write("membership-data",FTB?FtbParty.shortName(saved(server).resolvePool(a.getUUID()).requireReady().pool().id()):saved(server).nativeMembership().requests(a.getUUID()).stream().filter(r->r.applicant().equals(b.getUUID())).findFirst().orElseThrow().token().toString());
        if(phase==8) { saved(server).getPlayerData(b.getUUID()).setCrucibleChannelSelection(63); memberBaseline=available(server,b.getUUID()); }
        if(phase==9) { memberBaseline=available(server,b.getUUID()); revokedGrant=-1; }
        if(phase==INTRO.length) { a.serverLevel().removeBlock(CHANNEL_POS,false); a.serverLevel().removeBlock(CHANNEL_PYLON,false); a.teleportTo(.5,99,.5); b.teleportTo(2.5,99,.5); }
        gameplayBegun=false;
        if(phase>=PERFORMANCE) {
            int local=phase-PERFORMANCE;
            if(local==0) { AudioGameplayAcceptance.cleanup(server,a,b); a.setGameMode(GameType.CREATIVE); a.teleportTo(.5,99,.5); b.teleportTo(2.5,99,.5); dense=AudioPaidMachines.create(a,new BlockPos(0,98,3),32); dense.setPylons(8); }
            menuIndex=menuAge=menuReopens=0; menuOpened=false; measuring=false;
        }
        write("phase",id()); System.out.println("AudioGameplaySession: SERVER begin "+id());
    }
    private static boolean serverReady(MinecraftServer server,ServerPlayer a,ServerPlayer b) {
        if(phase==0) return source.isChanneling(a.getUUID()) && available(server,a.getUUID())>paidBaseline;
        if(b==null) return false;
        if(phase==1 || phase==3) return source.isChanneling(a.getUUID());
        if(phase==4) return !source.isChanneling(b.getUUID());
        var ownerResolution=saved(server).resolvePool(a.getUUID()); var observerResolution=saved(server).resolvePool(b.getUUID());
        if(ownerResolution.snapshot().isEmpty() || observerResolution.snapshot().isEmpty()) return false;
        var owner=ownerResolution.requireReady(); var observer=observerResolution.requireReady();
        if(phase==5) return owner.pool().kind()==PoolId.Kind.TEAM;
        if(phase==6) return FTB || !saved(server).nativeMembership().requests(a.getUUID()).isEmpty();
        if(phase==7) return owner.pool().equals(observer.pool()) && owner.members().contains(b.getUUID());
        if(phase==8) return source.isChanneling(a.getUUID()) && source.isChanneling(b.getUUID()) && available(server,b.getUUID())>memberBaseline;
        if(phase==9) return !owner.pool().equals(observer.pool()) && !source.isChanneling(b.getUUID());
        if(phase==10) return !source.isChanneling();
        return AudioGameplayAcceptance.serverReady(server,a,b,phase-INTRO.length);
    }
    private static void observeIntro(MinecraftServer server,ServerPlayer a,ServerPlayer b) {
        check(serverReady(server,a,b),"authoritative result confirms "+id());
        if(phase<=3) conservation(server,a);
        if(phase==0) check(b==null,"paid source and native body established before observer connection permitted");
        if(phase==4) check(!source.canPlayerUse(b),"actual outsider block-use cannot gain machine authority");
        if(phase==7) check(source.canPlayerUse(b),"current shared membership authorizes existing machine");
        if(phase==8) check(available(server,b.getUUID())>memberBaseline,"new member receives actual paid personal grant");
        if(phase==9) {
            check(!source.canPlayerUse(b),"leaving membership immediately revokes existing machine access");
            System.out.println("AudioGameplaySession: membership revocation grants before="+memberBaseline+" after="+available(server,b.getUUID()));
        }
    }
    private static void conservation(MinecraftServer server,ServerPlayer a) {
        var remaining=saved(server).reservoirs().read(a.getUUID()).units();
        for(var type:EssenceTypes.ORDERED) {
            long grant=saved(server).getPlayerData(a.getUUID()).getAvailable(type)-personalBaseline.get(type.id());
            check(grant>0 && poolBaseline.get(type.id()).subtract(remaining.get(type.id())).equals(BigInteger.valueOf(grant).multiply(ReservoirService.SCALE)),"real absence/reconnect grant exactly conserves "+type.id());
        }
        System.out.println("AudioGameplaySession: real paid conservation phase="+id()+" totalPersonal="+available(server,a.getUUID()));
    }
    private static void performanceTick(MinecraftServer server,ServerPlayer a,ServerPlayer b) throws Exception {
        if(b==null) return;
        int mode=(phase-PERFORMANCE)%3; boolean desired=mode==1;
        if(mode!=0 && menuIndex<dense.infusers().size()) {
            var infuser=dense.infusers().get(menuIndex);
            if(!menuOpened) { dense.openMenu(menuIndex); menuOpened=true; menuAge=0; menuReopens=0; }
            menuAge++;
            if(infuser.processingEnabled()==desired && a.containerMenu==a.inventoryMenu) {
                System.out.println("AudioGameplaySession: native Infuser menu confirmation index="+menuIndex+" enabled="+desired+" ageTicks="+menuAge+" reopened="+menuReopens);
                menuIndex++; menuOpened=false; ticks=0;
            } else if(a.containerMenu==a.inventoryMenu && menuAge>=20) {
                check(++menuReopens<=3,"bounded native menu reopen: index="+menuIndex+" actual="+infuser.processingEnabled()+" desired="+desired);
                dense.openMenu(menuIndex); menuAge=0;
            } else check(menuAge<=200,"native Infuser menu action timed out: index="+menuIndex+" actual="+infuser.processingEnabled()+" desired="+desired+" container="+a.containerMenu.containerId);
            return;
        }
        if(!measuring) {
            if(ticks<100 || !both("ready")) return;
            dense.resetLedger(); networkBefore=AudioNativePerformanceAcceptance.Network.snapshot(); systemBefore=AudioNativePerformanceAcceptance.SystemSample.capture();
            measureStarted=System.nanoTime(); measuring=true; write("measure",id()+"\n"); return;
        }
        if(ticks%20!=0 || !both("observed")) return;
        long paid=dense.verifyFocusPayments(desired); check(desired?paid>0:paid==0,"native measured window paid conservation "+id());
        var report=new JsonObject(); report.addProperty("phase",id()); report.addProperty("paidContribution",paid); report.addProperty("loadedMachines",dense.loadedPopulation());
        report.addProperty("observerMetricsRevision",AudioNativePerformanceAcceptance.Network.METRICS_REVISION);
        report.addProperty("networkPacketScope",AudioNativePerformanceAcceptance.Network.COUNT_SCOPE);
        report.addProperty("wallNanos",System.nanoTime()-measureStarted); report.add("network",AudioNativePerformanceAcceptance.Network.delta(networkBefore));
        report.add("processAndJavaThreads",systemBefore.delta(AudioNativePerformanceAcceptance.SystemSample.capture()));
        System.out.println("AUDIO_NATIVE_SERVER_PERFORMANCE "+report);
        System.out.println("AudioGameplaySession: SERVER phase PASS "+id()); begin(server,a,b,phase+1);
    }
    private static void timeout() { if(System.nanoTime()-STARTED>java.util.concurrent.TimeUnit.MINUTES.toNanos(30)) throw new AssertionError("Native gameplay total timeout"); }
    private static void check(boolean valid,String detail) { if(!valid) throw new AssertionError(detail); checks++; }
    private static void fail(Throwable failure) { AudioMultiplayerAcceptance.fail(failure); }

    /** The optional API is linked only in the explicitly selected FTB fixture lane. */
    private static final class FtbParty {
        private static String shortName(UUID id) {
            return dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager().getTeamByID(id).orElseThrow().getShortName();
        }
    }

    /** Dedicated servers never load these native client types. */
    private static final class Client {
        private static net.minecraft.client.Minecraft mc;
        private static AudioEngine engine;
        private static String player,phaseId="";
        private static int index=-1,phaseTicks,joined,generation=Integer.getInteger("essence.audio.connectionGeneration",0);
        private static int lateDiagnosticTicks;
        private static boolean actionSent,observed,disconnecting,finalDisconnect,measurement,readyWritten;
        private static int menuToggled=-1;
        private static AudioPaidMachines.Client.CrucibleMenuRequest sourceMenu;
        private static final Map<String,Integer> starts=new TreeMap<>();
        private static final Set<net.minecraft.client.resources.sounds.SoundInstance> retained=Collections.newSetFromMap(new IdentityHashMap<>());
        private static net.minecraft.client.resources.sounds.SoundInstance originalBody;
        private static long previousGameTime;
        private static int clockCorrectionsSeen;
        private static boolean joinedWritten;
        private static int commandStage,commandTicks;
        private static net.minecraft.client.resources.sounds.SoundInstance commandBody;
        private static boolean actor() { return player.equals("EA_A"); }
        static void install() { ClientTickEvent.CLIENT_POST.register(client->{try { tick(client); } catch(Throwable failure) { AudioGameplayAcceptance.clientCleanup(client); fail(failure); }}); }
        static void tick(net.minecraft.client.Minecraft client) throws Exception {
            timeout(); mc=client; mc.options.pauseOnLostFocus=false;
            if(disconnecting) {
                if(mc.level!=null || mc.player!=null) return;
                check(engine.sources()==0 && engine.voices()==0 && engine.history()==0,"actual native disconnect clears all engine ownership");
                for(var sound:retained) check(!mc.getSoundManager().isActive(sound),"retained actual native handle stopped at disconnect");
                write(player+".disconnected-"+generation,"native ownership and retained handles cleared\n");
                System.out.println(finalDisconnect?(PERFORMANCE_ONLY?"AudioGameplaySession: CLIENT PERFORMANCE PASS; actual socket disconnect cleanup":"AudioGameplaySession: CLIENT PASS; actual socket disconnect cleanup")
                        :"AudioGameplaySession: OBSERVER DISCONNECT PASS; actual socket closed and native handles retired");
                mc.stop(); return;
            }
            if(mc.level==null || mc.player==null) { AudioGameplayAcceptance.clientCleanup(mc); return; }
            // Native measurement has a fixed phase. No filesystem coordination or menu polling in its sample window.
            if(measurement && !observed) { measureTick(); return; }
            if(engine==null) {
                if(mc.screen!=null || ++joined<40) return;
                engine=(AudioEngine)AudioWorldAcceptance.read(com.mistaboom.essence_ascendance.client.audio.AudioRuntime.class,null,"ENGINE"); player=mc.player.getName().getString();
                check(mc.getSingleplayerServer()==null,"real remote socket client");
                check(mc.getUser().getProfileId().equals(mc.player.getUUID()),"launcher profile UUID must match actual offline-server player identity: profile="+mc.getUser().getProfileId()+", server="+mc.player.getUUID());
                System.out.println("AudioGameplaySession: offline profile identity PASS profile="+mc.getUser().getProfileId()+" server="+mc.player.getUUID());
                mc.options.showSubtitles().set(true);
                mc.getSoundManager().addListener((sound,event,range)->{String cue=cue(sound); if(cue.startsWith("essence_ascendance:")) starts.merge(cue,1,Integer::sum);});
            }
            if(!joinedWritten) {
                if(actor() && !PERFORMANCE_ONLY && !commandPreflight()) return;
                write(player+".joined-"+generation,mc.player.getUUID()+"\n");
                System.out.println("AudioGameplaySession: CLIENT joined "+player+" generation="+generation+" UUID="+mc.player.getUUID());
                joinedWritten=true;
            }
            if(actor() && originalBody!=null && index<=3) {
                long currentGameTime=mc.level.getGameTime();
                if(currentGameTime<previousGameTime) {
                    clockCorrectionsSeen++;
                    check(owned().contains(originalBody) && mc.getSoundManager().isActive(originalBody),
                            "actual backward native time packet preserves the existing paid native body");
                    if(clockCorrectionsSeen<=8)
                        System.out.println("AudioGameplaySession: CLOCK_CORRECTION_CONTINUITY PASS oldGameTime="+previousGameTime+" gameTime="+currentGameTime+" body="+System.identityHashCode(originalBody)+" sources="+engine.sources()+" voices="+engine.voices());
                }
                previousGameTime=currentGameTime;
            }
            retained.addAll(owned());
            if(Files.exists(ROOT.resolve("complete"))) { disconnect(true); return; }
            if(!Files.exists(ROOT.resolve("phase"))) return;
            String next=Files.readString(ROOT.resolve("phase"));
            if(!next.equals(phaseId)) {
                int newIndex=Integer.parseInt(next.substring(0,next.indexOf('-')));
                if(newIndex<=3 && (!mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD) || mc.player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(actor()?.5:2.5,99,.5))>.3)) return;
                phaseId=next; index=newIndex; phaseTicks=0; actionSent=observed=measurement=readyWritten=false; menuToggled=-1; sourceMenu=null; starts.clear();
                if(index>=INTRO.length && index<PERFORMANCE) { AudioGameplayAcceptance.clientBegin(mc,index-INTRO.length); write(player+".armed",phaseId+"\n"); }
                System.out.println("AudioGameplaySession: CLIENT begin "+phaseId+" actor="+actor()+" screen="+mc.screen);
            }
            phaseTicks++;
            if(index<INTRO.length) introTick();
            else if(index<PERFORMANCE) {
                if(!marker("gameplay-go",phaseId)) { phaseTicks=0; return; }
                AudioGameplayAcceptance.clientTick(mc,index-INTRO.length);
            }
            else { performanceTick(); return; }
            if(index==1 && mc.level.players().size()>=2 && ++lateDiagnosticTicks<=200 && lateDiagnosticTicks%20==0)
                lateDiagnostic();
            if(observed || phaseTicks<observationTicks() || !clientReady()) return;
            if(index<INTRO.length) observeIntro(); else AudioGameplayAcceptance.clientObserve(mc,index-INTRO.length);
            observed=true; write(player+".observed",phaseId+"\n");
            System.out.println("AudioGameplaySession: CLIENT phase PASS "+phaseId+" starts="+starts+" sources="+engine.sources()+" voices="+engine.voices());
        }
        /** Uses the loader's real local-command interception, then real server fallback, before paid sources start. */
        private static boolean commandPreflight() {
            if(++commandTicks>200) throw new AssertionError("Native audio command preflight timed out stage="+commandStage);
            switch(commandStage) {
                case 0 -> {
                    check(Arrays.stream(net.minecraft.client.gui.components.SubtitleOverlay.class.getDeclaredMethods())
                                    .anyMatch(method->method.getName().endsWith("essenceAscendance$observeNativeBounds")),
                            "exact packaged client applies the read-only native caption observer");
                    var root=mc.getConnection().getCommands().getRoot();
                    var essence=root.getChild("essence");
                    check(root.getChild("ea_audio")==null && essence!=null && essence.getChild("audio")!=null
                                    && essence.getChild("help")!=null && essence.getChild("team")!=null,
                            "actual merged native command tree retains Essence server branches and standard local audio root: "
                                    +(essence==null?"missing essence":essence.getChildren().stream().map(node->node.getName()).toList()));
                    check(completions("essence a").contains("audio"),"actual native root completion offers audio");
                    check(new HashSet<>(completions("essence audio ")).equals(Set.of("help","audition","start","stop")),
                            "actual native completion offers only the retained local audio actions");
                    check(completions("essence audio audition wea").contains("weapon.cast.release"),
                            "actual native completion resolves catalog semantic prefixes");
                    localCommand("essence audio help"); nextCommandStage();
                }
                case 1 -> {
                    if(!chatContains("/essence audio audition")) return false;
                    localCommand("essence audio audition interface.acknowledge"); nextCommandStage();
                }
                case 2 -> {
                    if(starts.getOrDefault("essence_ascendance:interface.acknowledge",0)!=1) return false;
                    localCommand("essence audio start mobility.thrust"); nextCommandStage();
                }
                case 3 -> {
                    AudioNativePerformanceAcceptance.requestGameplayNativeProbe();
                    var bodies=owned();
                    var sample=AudioNativePerformanceAcceptance.latestNative();
                    if(bodies.size()!=1 || !cue(bodies.getFirst()).equals("essence_ascendance:mobility.thrust.body")
                            || sample.voices().stream().noneMatch(v->v.instance()==bodies.getFirst() && v.sampled() && v.playing())) return false;
                    commandBody=bodies.getFirst(); retained.add(commandBody);
                    check(engine.sources()==1,"actual local start command owns one playing native preview body");
                    localCommand("essence audio stop"); nextCommandStage();
                }
                case 4 -> {
                    if(!owned().isEmpty() || engine.voices()!=0 || mc.getSoundManager().isActive(commandBody)) return false;
                    check(true,"actual local stop retires the preview handle before paid gameplay");
                    localCommand("essence help"); nextCommandStage();
                }
                case 5 -> {
                    if(!chatContains("/essence balance")) return false;
                    check(mc.screen==null,"server help falls through local registration and leaves native chat closed");
                    System.out.println("AudioGameplaySession: COMMAND PREFLIGHT PASS; actual ChatScreen Enter for local help/audition/start/stop, native playback/cleanup, merged tree/completion and remote Essence help; no visible window or synthetic command dispatcher");
                    nextCommandStage(); return true;
                }
                default -> { return true; }
            }
            return false;
        }
        private static void nextCommandStage() { commandStage++; commandTicks=0; }
        private static List<String> completions(String command) {
            var dispatcher=mc.getConnection().getCommands();
            var pending=dispatcher.getCompletionSuggestions(dispatcher.parse(command,mc.getConnection().getSuggestionsProvider()));
            check(pending.isDone(),"local audio completions require no server request or render-thread wait");
            return pending.join().getList().stream().map(suggestion->suggestion.getText()).toList();
        }
        private static void localCommand(String command) {
            // Include the native submit-and-close path used by an actual player entering a command.
            //? if >=26.1.2
            /*var chat=new net.minecraft.client.gui.screens.ChatScreen("/"+command,false);*/
            //? if <26.1.2
            var chat=new net.minecraft.client.gui.screens.ChatScreen("/"+command);
            mc.setScreen(chat);
            //? if >=26.1.2
            /*check(chat.keyPressed(new net.minecraft.client.input.KeyEvent(257,0,0)),"native chat consumes Enter");*/
            //? if <26.1.2
            check(chat.keyPressed(257,0,0),"native chat consumes Enter");
            check(mc.screen==null,"native chat submit closes after the command");
            System.out.println("AudioGameplaySession: real native command preflight="+command+" via ChatScreen Enter");
        }
        private static boolean chatContains(String fragment) {
            var messages=(List<?>)AudioWorldAcceptance.read(net.minecraft.client.gui.components.ChatComponent.class,mc.gui.getChat(),"allMessages");
            //? if >=26.1.2
            /*return messages.stream().anyMatch(message->((net.minecraft.client.multiplayer.chat.GuiMessage)message).content().getString().contains(fragment));*/
            //? if <26.1.2
            return messages.stream().anyMatch(message->((net.minecraft.client.GuiMessage)message).content().getString().contains(fragment));
        }
        private static void lateDiagnostic() {
            long now=System.nanoTime(); var sample=AudioNativePerformanceAcceptance.latestNative();
            var block=mc.level.getBlockEntity(CHANNEL_POS);
            System.out.println("AudioGameplaySession: LATE_CLIENT_DIAGNOSTIC player="+player+" ticks="+lateDiagnosticTicks
                    +" owned="+owned().stream().map(Client::cue).toList()+" sources="+engine.sources()+" voices="+engine.voices()
                    +" starts="+starts+" nativeSampleAgeNanos="+(now-sample.capturedNanos())
                    +" native="+sample.voices().stream().map(v->v.cue()+"[sampled="+v.sampled()+",playing="+v.playing()
                            +",retired="+v.retiredBeforeProbe()+",ageNanos="+(now-v.observedNanos())+"]").toList()
                    +" crucible="+(block instanceof EssenceCrucibleBlockEntity crucible?crucible.visualState():block)
                    +" players="+mc.level.players().stream().map(p->p.getName().getString()+"[uuid="+p.getUUID()+",id="+p.getId()+",position="+p.position()+"]").toList());
        }
        private static int observationTicks() { return index>=INTRO.length?AudioGameplayAcceptance.observationTicks(index-INTRO.length):80; }
        private static void introTick() throws Exception {
            if(index==2 && !actor()) { disconnect(false); return; }
            if(index==0 && actor() || index==8 || index==10 && actor()) { channel(index!=10); return; }
            if(index==4 && !actor()) {
                if(!actionSent) openSource();
                if(mc.player.containerMenu instanceof TeamMenu) { actionSent=true; if(phaseTicks>40) mc.player.closeContainer(); }
                return;
            }
            if(actionSent || index<5 || index>9) return;
            String data=Files.exists(ROOT.resolve("membership-data"))?Files.readString(ROOT.resolve("membership-data")):"";
            String command=null;
            if(index==5 && actor()) command=FTB?"ftbteams party create AudioNativeAcceptance":"essence team create AudioNativeAcceptance";
            if(index==6 && (FTB?actor():!actor())) command=FTB?"ftbteams party invite EA_B":"essence team request "+data+" share-stored";
            if(index==7 && (FTB?!actor():actor())) command=FTB?"ftbteams party join "+data:"essence team approve "+data;
            if(index==9 && !actor()) command=FTB?"ftbteams party leave":"essence team leave";
            if(command!=null) { mc.getConnection().sendCommand(command); actionSent=true; System.out.println("AudioGameplaySession: real client command="+command); }
        }
        private static void openSource() {
            if(sourceMenu==null) sourceMenu=new AudioPaidMachines.Client.CrucibleMenuRequest();
            sourceMenu.tick(mc,CHANNEL_POS);
        }
        private static void channel(boolean wanted) throws Exception {
            if(actionSent) return;
            if(!(mc.player.containerMenu instanceof EssenceCrucibleMenu menu)) { openSource(); return; }
            var state=com.mistaboom.essence_ascendance.client.EssenceCrucibleClientState.snapshotFor(menu.containerId); if(state==null) return;
            if(state.channelingForViewer()==wanted) { actionSent=true; mc.player.closeContainer(); return; }
            if(menuToggled!=menu.containerId && AudioPaidMachines.Client.pressCurrentCrucible(mc,wanted)) menuToggled=menu.containerId;
        }
        private static boolean clientReady() {
            // Observe the original actor body after the other native client has actually joined,
            // including its new process/connection incarnation on reconnect.
            if(actor() && (index==1 || index==3) && !Files.exists(ROOT.resolve("EA_B.joined-"+(index==1?0:1)))) return false;
            if(index<=3) return index==2?actor():owned().size()==1 && nativePlaying()==1 && (index!=0 || !actor() || actionSent);
            if(index==4) return actor() || actionSent;
            if(index==8) return actionSent && owned().size()==2 && nativePlaying()==2;
            if(index==9) return owned().size()<=1 && nativePlaying()<=1;
            if(index==10) return (!actor() || actionSent) && owned().isEmpty() && nativePlaying()==0;
            if(index<INTRO.length) return true;
            return AudioGameplayAcceptance.clientReady(mc,index-INTRO.length);
        }
        private static void observeIntro() {
            check(clientReady(),"real client ready "+phaseId);
            if(index<=3) {
                if(index==2) return;
                var current=owned().getFirst();
                if(actor()) {
                    if(originalBody==null) { originalBody=current; previousGameTime=mc.level.getGameTime(); }
                    else check(current==originalBody,"existing actor native body survives true observer connect/reconnect without restart");
                    if(index==1) check(clockCorrectionsSeen>0,"actual backward native clock update was observed before late-connection continuity acceptance");
                }
                check(engine.sources()==1 && engine.voices()==1,"single actual synchronized paid destination has exactly one owned native body");
                String expected=com.mistaboom.essence_ascendance.audio.AudioCatalog.get().profile(com.mistaboom.essence_ascendance.audio.AudioSources.beamProfile(true,1)).body().id().toString();
                check(cue(current).equals(expected),"true join/reconnect receives selected player-transfer body: actual="+cue(current)+" expected="+expected);
            }
            if(index==8) check(engine.sources()==2 && engine.voices()==2,"two authorized recipients own two actual paid destination bodies");
            if(index==9) check(engine.sources()<=1 && owned().size()<=1,"revoked member destination has no continuing owned body");
            if(index==10) for(var old:retained) check(!mc.getSoundManager().isActive(old),"all prior source handles retire after actual client Stop");
        }
        private static void performanceTick() throws Exception {
            int mode=(index-PERFORMANCE)%3;
            if(actor() && mode!=0 && mc.player.containerMenu instanceof com.mistaboom.essence_ascendance.infuser.EssenceInfuserMenu menu) {
                boolean wanted=mode==1;
                if(AudioPaidMachines.Client.infuserMenuReady(mc)) {
                    if(menuToggled==menu.containerId && menu.processingEnabled()==wanted) mc.player.closeContainer();
                    else if(menu.processingEnabled()!=wanted && menuToggled!=menu.containerId && AudioPaidMachines.Client.toggleCurrentInfuser(mc)) menuToggled=menu.containerId;
                }
            }
            if(!readyWritten && phaseTicks>=100 && mc.screen==null) { write(player+".ready",phaseId+"\n"); readyWritten=true; }
            if(observed) return;
            if(!measurement) {
                if(!marker("measure",phaseId)) return;
                AudioNativePerformanceAcceptance.begin(phaseId,mode==1?32:0,41); measurement=true;
            }
            measureTick();
        }
        private static void measureTick() throws Exception {
            AudioNativePerformanceAcceptance.clientTick();
            if(AudioNativePerformanceAcceptance.ready()) {
                var report=AudioNativePerformanceAcceptance.finish();
                if((index-PERFORMANCE)%3!=1) for(var old:retained) check(!mc.getSoundManager().isActive(old),"settled stopped/idle native handles are retired independently of empty ownership");
                write(player+".performance-"+(index-PERFORMANCE),report.toString()+"\n");
                write(player+".observed",phaseId+"\n"); observed=true;
            }
        }
        private static int nativePlaying() { return AudioNativePerformanceAcceptance.nativePlayingCount(); }
        private static List<net.minecraft.client.resources.sounds.SoundInstance> owned() {
            var result=new ArrayList<net.minecraft.client.resources.sounds.SoundInstance>();
            var sources=(Map<?,?>)AudioWorldAcceptance.read(AudioEngine.class,engine,"sources");
            for(var active:sources.values()) {
                var voice=AudioWorldAcceptance.read(active.getClass(),active,"voice"); if(voice instanceof net.minecraft.client.resources.sounds.SoundInstance sound) result.add(sound);
            }
            return result;
        }
        private static void disconnect(boolean complete) { AudioGameplayAcceptance.clientCleanup(mc); disconnecting=true; finalDisconnect=complete; mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Native audio gameplay phase complete")); }
        private static String cue(net.minecraft.client.resources.sounds.SoundInstance sound) {
            //? if >=26.1.2
            /*return sound.getIdentifier().toString();*/
            //? if <26.1.2
            return sound.getLocation().toString();
        }
    }
}
