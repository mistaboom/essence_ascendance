package com.mistaboom.essence_ascendance.audiofixture;

import com.mistaboom.essence_ascendance.audio.*;
import com.mistaboom.essence_ascendance.client.audio.*;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.essence.*;
import com.mistaboom.essence_ascendance.infuser.*;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.movement.*;
import com.mistaboom.essence_ascendance.network.*;
import com.mistaboom.essence_ascendance.pylon.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import java.math.BigInteger;
import java.util.*;

/** Real paid gameplay driven by production C2S handlers. The surrounding session owns all barriers. */
public final class AudioGameplayAcceptance {
    public static final BlockPos CRUCIBLE_POSITION=new BlockPos(0,98,3);
    private static final String[] PHASES=phaseNames();
    private static AudioPaidMachines.Scene scene;
    private static long finiteRequired,finiteCapacity;
    private static ResourceLocation finiteType,finiteTarget;
    private static boolean thrustObserved,projectileObserved;
    private static double lowestStamina=1;
    private static final Set<Integer> projectileIds=new HashSet<>();
    private static Map<ResourceLocation,BigInteger> stoppedPool;
    private static long stoppedPersonal;
    private static int assertions;
    private static int mixedDiagnosticTicks;
    private AudioGameplayAcceptance() { }
    private static String[] phaseNames() {
        var names=new ArrayList<>(List.of("infuser-complete","focus-start-pylons-0"));
        for(int count=1;count<=8;count++) names.add("pylons-up-"+count);
        for(int count=7;count>=0;count--) names.add("pylons-down-"+count);
        names.addAll(List.of("mixed-authorized-flight","flight-stop","infuser-stop","player-start-pylons-0"));
        for(int count=1;count<=8;count++) names.add("player-pylons-up-"+count);
        for(int count=7;count>=0;count--) names.add("player-pylons-down-"+count);
        names.addAll(List.of("player-stop","sources-remove"));
        return names.toArray(String[]::new);
    }
    public static String[] phases() { return PHASES.clone(); }
    public static int observationTicks(int phase) { return phase==18?12:60; }
    private static EssenceInfuserBlockEntity infuser() { return scene.infusers().getFirst(); }
    private static int pylons(int phase) { int local=phase>=21?phase-20:phase; return local<=1?0:local<=9?local-1:local<=17?17-local:0; }
    private static boolean playerFamily(int phase) { return phase>=21 && phase<=38; }
    private static boolean continuousPhase(int phase) { return phase>=1 && phase<=17 || phase>=21 && phase<=37; }
    private static void check(boolean valid,String message) { if(!valid) throw new AssertionError(message); assertions++; }

    public static void serverBegin(MinecraftServer server,ServerPlayer actor,ServerPlayer observer,int phase) {
        if(phase==0) {
            scene=AudioPaidMachines.create(actor,CRUCIBLE_POSITION,1);
            infuser().setItem(EssenceInfuserBlockEntity.INPUT_SLOT,new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()));
            // Fresh placement normalizes on its next block tick. Configure through the authorized setters before quoting this recipe.
            if(infuser().sourceEssence()==null) infuser().cycleSource(actor,1);
            if(infuser().targetEssence()==null) infuser().cycleTarget(actor,1);
            finiteRequired=infuser().sourceRequired(); finiteCapacity=infuser().targetCarrierCapacity();
            finiteType=infuser().sourceEssence().id(); finiteTarget=infuser().targetEssence().id();
            check(finiteRequired>0 && finiteCapacity>0,"actual finite Infuser recipe has positive native cost and output");
            scene.resetLedger(); scene.openMenu(0);
        } else if(phase==1) {
            check(!infuser().processingEnabled(),"finite job was stopped through actual client menu before replacement");
            infuser().setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT,ItemStack.EMPTY);
            infuser().setItem(EssenceInfuserBlockEntity.INPUT_SLOT,AudioPaidMachines.focus(EssenceFocusTier.DORMANT));
            scene.resetLedger(); scene.openMenu(0);
        } else if(phase<=17) {
            scene.verifyFocusPayments(true); scene.setPylons(pylons(phase));
        } else if(phase==18) {
            prepareFlight(actor); scene.openMenu(0);
            thrustObserved=projectileObserved=false; lowestStamina=1; projectileIds.clear(); EquipmentWeaponService.forget(actor);
            mixedDiagnosticTicks=0;
        } else if(phase==20) {
            check(serverReady(server,actor,observer,19),"actual disabled request ended thrust before operator returns to the machine");
            actor.teleportTo(.5,99,.5);
            check(actor.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(infuser().getBlockPos()))<64,"flight returns actor within actual menu reach");
            scene.openMenu(0);
        } else if(phase==21) {
            check(scene.snapshotPool().equals(stoppedPool),"actual Infuser Stop remains unpaid before player-family pass");
            scene.resetLedger();
        } else if(phase>=22 && phase<=37) {
            scene.verifyPlayerPayments(); scene.setPylons(pylons(phase));
        } else if(phase==39) {
            check(scene.snapshotPool().equals(stoppedPool) && scene.personalGranted()==stoppedPersonal,"actual player Stop remains unpaid before removal");
            scene.remove();
        }
        System.out.println("AudioGameplayAcceptance: server begin "+PHASES[phase]);
    }
    private static void prepareFlight(ServerPlayer actor) {
        var data=EssenceSavedData.get(actor.serverLevel().getServer()).getPlayerData(actor.getUUID());
        data.setTier(AscendanceTiers.TRANSCENDENT);
        var required=new LinkedHashSet<ResourceLocation>(); collectPrerequisites(SkillIds.FATIGUE_FLIGHT,required);
        data.grantAllSkillsForAdmin(required.stream().map(SkillRegistry::require).toList());
        data.clearLoadoutSelection(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT);
        data.clearLoadoutSelection(SkillIds.FATIGUE_FLIGHT);
        actor.setGameMode(GameType.SURVIVAL);
        var caster=new ItemStack(AscendanceItems.ASCENDANCE_CASTER.get()); EquipmentTierData.setTier(caster,EquipmentTier.TRANSCENDENT);
        actor.setItemInHand(InteractionHand.MAIN_HAND,caster); EquipmentAttributeService.sync(actor);
        SkillEffectRuntime.refresh(actor); PlayerEssenceSyncService.forceSync(actor);
        check(CommittedSkillAccess.isEffective(actor,SkillIds.FATIGUE_FLIGHT),"real committed skill evaluation authorizes Fatigue Flight");
        // A real ceiling bounds ascent while native collision, held-Jump requests and stamina remain authoritative.
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) actor.serverLevel().setBlockAndUpdate(new BlockPos(x,104,z),net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
        actor.teleportTo(.5,101,.5);
    }
    private static void collectPrerequisites(ResourceLocation id,Set<ResourceLocation> result) {
        if(!result.add(id)) return;
        for(var prerequisite:SkillRegistry.require(id).prerequisites()) collectPrerequisites(prerequisite,result);
    }
    public static void serverTick(MinecraftServer server,ServerPlayer actor,ServerPlayer observer,int phase) {
        if(phase!=18) return;
        var state=SkillEffectRuntime.context(actor).<FlightAbilityState>existingState(SkillIds.FATIGUE_FLIGHT);
        if(state!=null) { thrustObserved|=state.thrusting(); lowestStamina=Math.min(lowestStamina,state.stamina()); }
        EquipmentWeaponService.lastMagicCast(actor).filter(EquipmentWeaponService.MagicCastEvaluation::castPerformed).ifPresent(cast->{
            if(actor.serverLevel().getEntity(cast.projectileId()) instanceof com.mistaboom.essence_ascendance.projectile.MagicBoltEntity) {
                projectileObserved=true; projectileIds.add(cast.projectileId());
            }
        });
        // Bounded, read-only gameplay diagnostics; never runs inside a performance window.
        if(++mixedDiagnosticTicks<=200 && mixedDiagnosticTicks%20==0)
            System.out.println("AudioGameplayAcceptance: MIXED_SERVER_DIAGNOSTIC ticks="+mixedDiagnosticTicks
                    +" actorPosition="+actor.position()+" observerPosition="+observer.position()
                    +" thrust="+(state!=null && state.thrusting())+" stamina="+(state==null?"absent":state.stamina())
                    +" flags="+((com.mistaboom.essence_ascendance.visual.FlightVisualState)actor).essenceAscendance$flightFlags()
                    +" thrustObserved="+thrustObserved+" lowestStamina="+lowestStamina
                    +" projectileObserved="+projectileObserved+" projectileIds="+projectileIds
                    +" cast="+EquipmentWeaponService.lastMagicCast(actor)+" infuserEnabled="+infuser().processingEnabled()
                    +" infuserPaid="+infuser().focusInfusionTotalContributed());
    }
    public static boolean serverReady(MinecraftServer server,ServerPlayer actor,ServerPlayer observer,int phase) {
        if(phase==0) return !infuser().getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).isEmpty() && !infuser().processingEnabled();
        if(phase<=17) return infuser().processingEnabled() && infuser().focusInfusionTotalContributed()>0
                && scene.source().structureSnapshot().activePylons().size()==pylons(phase);
        if(phase==18) return thrustObserved && projectileObserved;
        if(phase==19) {
            var state=SkillEffectRuntime.context(actor).<FlightAbilityState>existingState(SkillIds.FATIGUE_FLIGHT);
            return state!=null && !state.thrusting();
        }
        if(phase==20) return !infuser().processingEnabled();
        if(phase<=37) return scene.source().isChanneling(actor.getUUID()) && scene.personalGranted()>0
                && scene.source().structureSnapshot().activePylons().size()==pylons(phase);
        if(phase==38) return !scene.source().isChanneling(actor.getUUID());
        return actor.serverLevel().getBlockEntity(CRUCIBLE_POSITION)==null;
    }
    public static void serverObserve(MinecraftServer server,ServerPlayer actor,ServerPlayer observer,int phase) {
        check(serverReady(server,actor,observer,phase),"real server outcome is ready for "+PHASES[phase]);
        if(phase==0) {
            var output=infuser().getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT);
            var value=EssentiumCarrierData.readValidated(output).orElseThrow();
            check(output.getCount()==1 && value.amount()==finiteCapacity && value.essence().id().equals(finiteTarget),"one genuine paid carrier output has exact native denomination");
            check(infuser().getItem(EssenceInfuserBlockEntity.INPUT_SLOT).isEmpty(),"actual finite job consumes exactly one Latent Nugget");
            for(var type:EssenceTypes.ORDERED) check(scene.debits().get(type.id()).equals(type.id().equals(finiteType)?BigInteger.valueOf(finiteRequired).multiply(ReservoirService.SCALE):BigInteger.ZERO),"finite output debits exact quoted "+type.id());
            System.out.println("AudioGameplayAcceptance: paid finite output="+value+" sourceDebit="+finiteRequired);
        } else if(phase<=20) {
            long paid=scene.verifyFocusPayments(true); check(paid>0,"paid native Focus work progresses");
            if(phase<=17) check(scene.source().visualState().activePylons().size()==pylons(phase),"server publishes actual Pylon count "+pylons(phase));
            if(phase==18) {
                check(thrustObserved && lowestStamina<1 && projectileObserved && projectileIds.size()==1,"one real projectile and authorized paid-stamina thrust coexist with paid machine work");
                check(infuser().processingEnabled(),"rejected stale interface confirmation cannot interrupt active paid work");
            }
            if(phase==20) stoppedPool=scene.snapshotPool();
            System.out.println("AudioGameplayAcceptance: paid Focus phase="+PHASES[phase]+" contribution="+paid+" pylons="+scene.source().structureSnapshot().activePylons().size()+" lowestStamina="+lowestStamina);
        } else if(phase<=38) {
            long granted=scene.verifyPlayerPayments(); check(granted>0,"actual player channel earns paid personal Essence");
            if(phase<=37) check(scene.source().visualState().activePylons().size()==pylons(phase),"player source publishes actual Pylon count "+pylons(phase));
            else { stoppedPool=scene.snapshotPool(); stoppedPersonal=scene.personalGranted(); }
            System.out.println("AudioGameplayAcceptance: paid player phase="+PHASES[phase]+" personalGrant="+granted+" pylons="+scene.source().structureSnapshot().activePylons().size());
        } else check(scene.snapshotPool().equals(stoppedPool) && scene.personalGranted()==stoppedPersonal,"removed actual sources cannot continue debiting reservoir or granting personal Essence");
        System.out.println("AudioGameplayAcceptance: server phase PASS "+PHASES[phase]+" assertions="+assertions);
    }
    public static void cleanup(MinecraftServer server,ServerPlayer actor,ServerPlayer observer) {
        if(scene!=null) scene.remove();
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) actor.serverLevel().removeBlock(new BlockPos(x,104,z),false);
    }
    /** Called only after automated cleanup passed; the saved manual scene starts completely stopped. */
    public static void prepareHumanScene(ServerPlayer actor) {
        for(int x=-5;x<=6;x++) for(int z=-4;z<=7;z++) actor.serverLevel().setBlockAndUpdate(new BlockPos(x,98,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        scene=AudioPaidMachines.create(actor,CRUCIBLE_POSITION,1); scene.setPylons(8);
        check(!infuser().processingEnabled() && infuser().focusInfusionTotalContributed()==0,"saved human scene has no automatic processing or prior paid work");
        actor.getInventory().add(new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(),16));
        actor.getInventory().add(AudioPaidMachines.focus(EssenceFocusTier.DORMANT));
        actor.setGameMode(GameType.SURVIVAL); actor.teleportTo(.5,99,-1.5); actor.setYRot(0); actor.setXRot(10);
        System.out.println("AudioGameplayAcceptance: human scene ready Infuser=0,98,1 Crucible=0,98,3 Pylons=8 processing=false player=.5,99,-1.5");
    }
    public static void clientBegin(Object client,int phase) { Client.begin((net.minecraft.client.Minecraft)client,phase); }
    public static void clientTick(Object client,int phase) { Client.tick((net.minecraft.client.Minecraft)client,phase); }
    public static boolean clientReady(Object client,int phase) { return Client.ready((net.minecraft.client.Minecraft)client,phase); }
    public static void clientObserve(Object client,int phase) { Client.observe((net.minecraft.client.Minecraft)client,phase); }
    public static void clientCleanup(Object client) { Client.restoreInput((net.minecraft.client.Minecraft)client); }

    private static final class Client {
        private static AudioEngine engine;
        private static int phaseTicks;
        private static boolean startSent,stopSent,requestsSent,mixedSeen,interfaceSent;
        private static boolean inputOwned,previousJump,previousFocus;
        private static int flightInputTicks;
        private static AudioPaidMachines.Client.CrucibleMenuRequest sourceMenuRequest;
        private static final Map<String,Integer> nativeStarts=new LinkedHashMap<>();
        private static final Set<String> captionCues=new HashSet<>();
        private static final Set<net.minecraft.client.resources.sounds.SoundInstance> retired=Collections.newSetFromMap(new IdentityHashMap<>());
        private static net.minecraft.client.resources.sounds.SoundInstance previousBody;
        private static boolean actor(net.minecraft.client.Minecraft mc) { return mc.player.getName().getString().equals("EA_A"); }
        private static void begin(net.minecraft.client.Minecraft mc,int phase) {
            restoreInput(mc);
            if(phase==18) {
                var renderers=(Map<?,?>)AudioWorldAcceptance.read(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class,
                        mc.getEntityRenderDispatcher(),"renderers");
                var renderer=renderers.get(com.mistaboom.essence_ascendance.projectile.ProjectileContent.MAGIC_BOLT.get());
                check(renderer instanceof com.mistaboom.essence_ascendance.client.MagicBoltRenderer,
                        "actual native dispatcher has the approved Caster renderer before either client permits the real cast");
                System.out.println("AudioGameplayAcceptance: native Caster renderer registration PASS actor="+actor(mc)+" renderer="+renderer.getClass().getName());
            }
            if(engine==null) {
                engine=(AudioEngine)AudioWorldAcceptance.read(AudioRuntime.class,null,"ENGINE");
                mc.getSoundManager().addListener((sound,event,range)->{
                    String cue=id(sound); if(cue.startsWith("essence_ascendance:")) nativeStarts.merge(cue,1,Integer::sum);
                });
            }
            phaseTicks=flightInputTicks=0; startSent=stopSent=requestsSent=mixedSeen=interfaceSent=false; nativeStarts.clear(); captionCues.clear();
            sourceMenuRequest=new AudioPaidMachines.Client.CrucibleMenuRequest();
        }
        /** Exercise the ordinary key-to-C2S path without focusing or showing the native window. */
        private static void beginInput(net.minecraft.client.Minecraft mc) {
            check(!inputOwned && mc.screen==null,"ordinary flight input starts after the actual menu closes");
            previousJump=mc.options.keyJump.isDown(); previousFocus=mc.isWindowActive(); inputOwned=true;
            focusInput(mc,true);
        }
        private static void restoreInput(net.minecraft.client.Minecraft mc) {
            if(!inputOwned) return;
            mc.options.keyJump.setDown(previousJump); focusInput(mc,previousFocus); inputOwned=false;
            System.out.println("AudioGameplayAcceptance: ordinary flight key/focus state restored; native GLFW focus unchanged");
        }
        private static void focusInput(net.minecraft.client.Minecraft mc,boolean focused) {
            // These are only Minecraft's input-readiness bits, not a native GLFW focus request.
            //? if >=26.1.2 {
            /*try {
                var field=com.mojang.blaze3d.platform.Window.class.getDeclaredField("focused"); field.setAccessible(true);
                field.setBoolean(mc.getWindow(),focused);
            } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
            *///?}
            //? if <26.1.2
            mc.setWindowActive(focused);
        }
        private static void tick(net.minecraft.client.Minecraft mc,int phase) {
            if(engine==null || mc.player==null) return;
            phaseTicks++;
            var captions=(SemanticCaptions)AudioWorldAcceptance.read(CaptionRouter.class,null,"STATE");
            captions.lines().forEach(ticket->captionCues.add(ticket.cue.id().toString()));
            for(var voice:ownedVoices()) retired.add(voice);
            if(actor(mc)) {
                if(phase==0 && mc.player.containerMenu instanceof EssenceInfuserMenu menu) {
                    if(!startSent && !menu.processingEnabled()) startSent=AudioPaidMachines.Client.toggleCurrentInfuser(mc);
                    if(startSent && !stopSent && menu.processingEnabled() && !menu.getSlot(EssenceInfuserBlockEntity.OUTPUT_SLOT).getItem().isEmpty()) stopSent=AudioPaidMachines.Client.toggleCurrentInfuser(mc);
                    if(stopSent && !menu.processingEnabled()) mc.player.closeContainer();
                } else if(phase==1 && mc.player.containerMenu instanceof EssenceInfuserMenu menu) {
                    if(!startSent && !menu.processingEnabled()) startSent=AudioPaidMachines.Client.toggleCurrentInfuser(mc);
                    if(startSent && menu.processingEnabled()) mc.player.closeContainer();
                } else if(phase==18) {
                    if(!interfaceSent && mc.player.containerMenu instanceof EssenceInfuserMenu menu) {
                        NetworkManager.sendToServer(new InfuserSoulbindPayload(menu.containerId,new UUID(0,0)));
                        mc.player.closeContainer(); interfaceSent=true; beginInput(mc);
                    }
                    if(inputOwned) {
                        // A press/release followed by a sustained second press matches ordinary Fatigue Flight use.
                        mc.options.keyJump.setDown(flightInputTicks<2 || flightInputTicks>=4); flightInputTicks++;
                        if(!requestsSent && nativePlaying("essence_ascendance:mobility.thrust.body")
                                && mc.player.getMainHandItem().is(AscendanceItems.ASCENDANCE_CASTER.get())) {
                            mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND); requestsSent=true;
                        }
                    }
                    if(requestsSent && mc.player.isUsingItem()) mc.gameMode.releaseUsingItem(mc.player);
                } else if(phase==19 && !requestsSent) {
                    // The normal input mixin sends its own release/disabled edge after begin() restores the key.
                    check(!inputOwned && mc.options.keyJump.isDown()==previousJump && mc.isWindowActive()==previousFocus,
                            "ordinary input state restored before native flight-stop acceptance"); requestsSent=true;
                } else if(phase==20 && mc.player.containerMenu instanceof EssenceInfuserMenu menu) {
                    if(!stopSent && menu.processingEnabled()) stopSent=AudioPaidMachines.Client.toggleCurrentInfuser(mc);
                    if(stopSent && !menu.processingEnabled()) mc.player.closeContainer();
                } else if(phase==21 || phase==38) {
                    boolean wanted=phase==21;
                    if(!(mc.player.containerMenu instanceof com.mistaboom.essence_ascendance.crucible.EssenceCrucibleMenu menu)) {
                        if(!requestsSent) sourceMenuRequest.tick(mc,CRUCIBLE_POSITION);
                    } else {
                        var state=com.mistaboom.essence_ascendance.client.EssenceCrucibleClientState.snapshotFor(menu.containerId);
                        if(state!=null && state.channelingForViewer()==wanted) { requestsSent=true; mc.player.closeContainer(); }
                        else if(!startSent) startSent=AudioPaidMachines.Client.pressCurrentCrucible(mc,wanted);
                    }
                }
            }
            if(phase==18 && nativePlaying("essence_ascendance:mobility.thrust.body") && nativePlaying(bodyCue(false,0))
                    && nativeStarts.getOrDefault("essence_ascendance:weapon.cast.release",0)>0) mixedSeen=true;
            // An airborne thrust can end on landing before a 1 Hz performance probe. Observe its real native channel next tick.
            if(phase==18 && !mixedSeen && phaseTicks<=200 && ownedCue("essence_ascendance:mobility.thrust.body"))
                AudioNativePerformanceAcceptance.requestGameplayNativeProbe();
            if(phase==18 && phaseTicks<=200 && phaseTicks%20==0) mixedDiagnostic(mc);
        }
        private static void mixedDiagnostic(net.minecraft.client.Minecraft mc) {
            long now=System.nanoTime();
            var nativeSample=AudioNativePerformanceAcceptance.latestNative();
            var owned=ownedVoices();
            System.out.println("AudioGameplayAcceptance: MIXED_CLIENT_DIAGNOSTIC actor="+actor(mc)+" ticks="+phaseTicks
                    +" mixedSeen="+mixedSeen+" requestsSent="+requestsSent+" nativeStarts="+nativeStarts
                    +" owned="+owned.stream().map(Client::id).toList()+" sources="+engine.sources()+" voices="+engine.voices()
                    +" machinePlaying="+nativePlaying(bodyCue(false,0))+" thrustPlaying="+nativePlaying("essence_ascendance:mobility.thrust.body")
                    +" sampleAgeNanos="+(now-nativeSample.capturedNanos())+" native="+nativeSample.voices().stream()
                    .map(v->v.cue()+"[sampled="+v.sampled()+",playing="+v.playing()+",retired="+v.retiredBeforeProbe()
                            +",ageNanos="+(now-v.observedNanos())+",owned="+owned.stream().anyMatch(sound->sound==v.instance())+"]").toList()
                    +" players="+mc.level.players().stream().map(p->p.getName().getString()+"[id="+p.getId()+",position="+p.position()
                            +",flags="+((com.mistaboom.essence_ascendance.visual.FlightVisualState)p).essenceAscendance$flightFlags()+"]").toList());
        }
        private static boolean ready(net.minecraft.client.Minecraft mc,int phase) {
            if(engine==null || mc.player==null) return false;
            if(phase==0) return !actor(mc) || stopSent && !(mc.player.containerMenu instanceof EssenceInfuserMenu);
            if(continuousPhase(phase)) return nativePlaying(bodyCue(playerFamily(phase),pylons(phase))) && (phase!=21 || !actor(mc) || requestsSent);
            if(phase==18) return mixedSeen && (!actor(mc) || requestsSent && nativeStarts.getOrDefault("essence_ascendance:interface.denied",0)==1);
            if(phase==19) return !ownedCue("essence_ascendance:mobility.thrust.body");
            return ownedVoices().isEmpty() && (phase!=38 || !actor(mc) || requestsSent);
        }
        private static void observe(net.minecraft.client.Minecraft mc,int phase) {
            check(ready(mc,phase),"actual client/native outcome ready "+PHASES[phase]);
            if(phase==0) {
                check(nativeStarts.getOrDefault("essence_ascendance:process.complete",0)==0,"paid completion preserves approved silence");
                check(nativeStarts.getOrDefault("essence_ascendance:interface.acknowledge",0)==0,
                        "real Infuser Start/Stop buttons do not add a custom acknowledgement on either client");
            } else if(continuousPhase(phase)) {
                String expected=bodyCue(playerFamily(phase),pylons(phase));
                var current=ownedVoices().stream().filter(sound->id(sound).equals(expected)).findFirst().orElseThrow();
                check(ownedVoices().size()==1,"one native body per real paid destination after Pylon transition");
                if(phase!=1 && phase!=21) check(current!=previousBody && (previousBody==null || !mc.getSoundManager().isActive(previousBody)),"actual profile change retires previous native handle before replacement owns steady state");
                previousBody=current;
                check(nativeStarts.getOrDefault(expected,0)==1,"real Pylon transition creates its body exactly once");
                check(captionCues.contains(expected),"localized semantic caption follows actual Pylon-selected cue");
            } else if(phase==18) {
                check(mixedSeen && nativeStarts.getOrDefault("essence_ascendance:weapon.cast.release",0)==1,"one actual weapon release coexists with native machine and authorized skill bodies");
                if(actor(mc)) check(inputOwned && flightInputTicks>=4 && mc.isWindowActive() && mc.options.keyJump.isDown(),
                        "ordinary native client key polling owns the sustained flight request; no fixture-authored movement packet");
                if(Boolean.getBoolean("essence.audio.hiddenWindow")) {
                    //? if >=26.1.2
                    /*long window=mc.getWindow().handle();*/
                    //? if <26.1.2
                    long window=mc.getWindow().getWindow();
                    check(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_VISIBLE)==org.lwjgl.glfw.GLFW.GLFW_FALSE
                                    && org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_FOCUSED)==org.lwjgl.glfw.GLFW.GLFW_FALSE,
                            "ordinary input probe leaves actual native GLFW window invisible and unfocused");
                }
                check(nativeStarts.getOrDefault("essence_ascendance:interface.denied",0)==(actor(mc)?1:0),"actual stale-confirmation denial is private during simultaneous activity");
                check(captionCues.contains("essence_ascendance:mobility.thrust.body"),"authorized skill has semantic caption");
                for(var voice:ownedVoices()) if(id(voice).equals("essence_ascendance:mobility.thrust.body"))
                    check(voice.getVolume()>0 && Float.isFinite(voice.getVolume()),"native thrust body has positive finite instance gain");
            } else if(phase==20 || phase>=38) {
                check(ownedVoices().isEmpty(),"stopped/removed paid source has no owned native body");
                for(var old:retired) check(!mc.getSoundManager().isActive(old),"all prior native bodies are inactive independently of empty controller map");
            }
            System.out.println("AudioGameplayAcceptance: client phase PASS "+PHASES[phase]+" actor="+actor(mc)+" nativeStarts="+nativeStarts+" captions="+captionCues+" sources="+engine.sources()+" voices="+engine.voices());
        }
        @SuppressWarnings("unchecked")
        private static List<net.minecraft.client.resources.sounds.SoundInstance> ownedVoices() {
            var voices=new ArrayList<net.minecraft.client.resources.sounds.SoundInstance>();
            var sources=(Map<?,?>)AudioWorldAcceptance.read(AudioEngine.class,engine,"sources");
            for(var active:sources.values()) {
                var voice=AudioWorldAcceptance.read(active.getClass(),active,"voice");
                if(voice instanceof net.minecraft.client.resources.sounds.SoundInstance sound) voices.add(sound);
            }
            return voices;
        }
        private static boolean ownedCue(String cue) { return ownedVoices().stream().anyMatch(sound->id(sound).equals(cue)); }
        private static boolean nativePlaying(String cue) {
            long now=System.nanoTime();
            return AudioNativePerformanceAcceptance.latestNative().voices().stream().anyMatch(voice->voice.cue().equals(cue) && voice.sampled() && voice.playing()
                    && now-voice.observedNanos()<3_000_000_000L && ownedVoices().stream().anyMatch(sound->sound==voice.instance()));
        }
        private static String id(net.minecraft.client.resources.sounds.SoundInstance sound) {
            //? if >=26.1.2
            /*return sound.getIdentifier().toString();*/
            //? if <26.1.2
            return sound.getLocation().toString();
        }
        private static String bodyCue(boolean player,int pylons) { return AudioCatalog.get().profile(AudioSources.beamProfile(player,pylons)).body().id().toString(); }
    }
}
