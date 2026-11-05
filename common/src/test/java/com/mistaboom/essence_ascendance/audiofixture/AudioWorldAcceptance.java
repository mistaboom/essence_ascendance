package com.mistaboom.essence_ascendance.audiofixture;

import com.mistaboom.essence_ascendance.audio.*;
import com.mistaboom.essence_ascendance.client.audio.*;
import com.mistaboom.essence_ascendance.client.transientfx.TransientWorldVisuals;
import com.mistaboom.essence_ascendance.infuser.*;
import com.mistaboom.essence_ascendance.crucible.*;
import com.mistaboom.essence_ascendance.network.*;
import com.mistaboom.essence_ascendance.verification.BlockEntityTestNbtCompat;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import com.mistaboom.essence_ascendance.visual.transientfx.*;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in disposable joined-world acceptance. No production test hooks, money changes or custom asset approval. */
public final class AudioWorldAcceptance extends Screen {
    private record Case(String name,double master,double category,boolean subtitles,
                        boolean hidden,boolean covered,int route,String semantic,int guiScale) {
        Case(String name,double master,double category,boolean subtitles,boolean hidden,boolean covered,int route) {
            this(name,master,category,subtitles,hidden,covered,route,route==0?"combat.explosion":route==1?"interface.acknowledge":"ui.press",2);
        }
    }
    private static final List<Case> CASES=cases();
    private static List<Case> cases() {
        var cases=new ArrayList<>(List.of(
            new Case("audible world envelope",1,1,true,false,false,0),
            new Case("master zero",0,1,true,false,false,0),
            new Case("category zero",1,0,true,false,false,0),
            new Case("fractional native master and category",0.5,0.25,true,false,false,0),
            new Case("vanilla subtitles off",1,1,false,false,false,0),
            new Case("F1 and return",1,1,true,true,false,0),
            new Case("opaque screen and return",1,1,true,false,true,0),
            new Case("private native packet",1,1,true,false,false,1),
            new Case("private master zero",0,1,true,false,false,1),
            new Case("private fractional native master",0.5,1,true,false,false,1),
            new Case("private vanilla subtitles off",1,1,false,false,false,1),
            new Case("interface activation has no caption",1,1,true,false,false,2)));
        for(String semantic:List.of("machine.focus.install","weapon.cast.release","weapon.bolt.impact","element.fire.apply")) {
            for(int mode=0;mode<6;mode++) cases.add(new Case(semantic+" shared settings "+mode,mode==1?0:1,mode==2?0:1,mode!=3,
                    false,false,3,semantic,mode==4?1:mode==5?3:2));
        }
        return List.copyOf(cases);
    }
    private static long ticks,started=System.nanoTime();
    private static boolean opening,installed,finishing;
    private static int joinedTicks,checks;
    private final Minecraft mc=Minecraft.getInstance();
    private final AudioEngine engine=(AudioEngine)read(AudioRuntime.class,null,"ENGINE");
    private final SemanticCaptions captions=(SemanticCaptions)read(CaptionRouter.class,null,"STATE");
    private final SubtitleOverlay nativeOverlay=(SubtitleOverlay)read(net.minecraft.client.gui.Gui.class,mc.gui,"subtitleOverlay");
    private final boolean device=((Boolean)read(net.minecraft.client.sounds.SoundEngine.class,read(SoundManager.class,mc.getSoundManager(),"soundEngine"),"loaded"));
    private int index,stage;
    private long stageTick,bodyStarted;
    private CompletableFuture<Void> operation;
    private BlockPos position;
    private BlockPos cruciblePosition;
    private EssenceCrucibleBlockEntity crucible;
    private EssenceInfuserBlockEntity source,oldSource;
    private AudioEngine.Voice body,previous;
    private boolean longNativeChecked;
    private boolean identityChecked;
    private int identityActor;
    private AudioResourceAcceptance resourceAcceptance;

    private AudioWorldAcceptance() { super(Component.literal("Audio acceptance fixture")); mc.options.pauseOnLostFocus=false; System.out.println("AudioWorldAcceptance: joined; audio device loaded="+device); check(device,"native audio device must be initialized for device acceptance"); }
    public static void install() {
        ClientTickEvent.CLIENT_POST.register(client->{
            try {
                ticks++;
                if(finishing && client.level==null && client.player==null) {
                    var engine=(AudioEngine)read(AudioRuntime.class,null,"ENGINE");
                    var state=(SemanticCaptions)read(CaptionRouter.class,null,"STATE");
                    check(engine.sources()==0 && engine.voices()==0 && state.lines().isEmpty(),"disconnect clears source voices and captions without rendering");
                    System.out.println("AudioWorldAcceptance: PASS; "+checks+" joined-client assertions; real packets, native subtitle draws, vanilla volumes/subtitle toggle, source replacement, reload/device recovery and disconnect; no human mix approval");
                    client.stop(); return;
                }
                if(!installed && client.level!=null && client.player!=null && client.screen==null && ++joinedTicks>40) {
                    installed=true; client.setScreen(new AudioWorldAcceptance());
                } else if(!opening && client.getOverlay()==null && client.screen instanceof TitleScreen) {
                    opening=true; client.createWorldOpenFlows().openWorld("world",()->{ throw new AssertionError("World open cancelled"); });
                }
                if(System.nanoTime()-started>java.util.concurrent.TimeUnit.SECONDS.toNanos(180)) throw new AssertionError("Audio acceptance timed out");
            } catch(Throwable failure) { fail(failure); }
        });
    }
    @Override public boolean isPauseScreen() { return false; }
    //? if >=26.1.2
    /*@Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {*/
    //? if <26.1.2
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float delta) {
        try {
            graphics.fill(0,0,graphics.guiWidth(),graphics.guiHeight(),0xFF202020);
            if(index<CASES.size()) matrix(graphics);
            else if(!identityChecked) sourceIdentity();
            else lifecycle(graphics);
        } catch(Throwable failure) { fail(failure); }
    }
    //? if >=26.1.2
    /*private void matrix(GuiGraphicsExtractor graphics) throws Exception {*/
    //? if <26.1.2
    private void matrix(GuiGraphics graphics) throws Exception {
        Case c=CASES.get(index);
        var cue=AudioCatalog.get().recipe(c.semantic);
        boolean audible=c.master>0 && (cue.playback().category()==SoundSource.MASTER || c.category>0);
        if(stage==0) {
            clear(); volume(SoundSource.MASTER,c.master);
            for(var category:SoundSource.values()) if(category!=SoundSource.MASTER) volume(category,1);
            // MASTER-routed interface cues have no independent category slider; never overwrite Master.
            if(cue.playback().category()!=SoundSource.MASTER) volume(cue.playback().category(),c.category);
            mc.options.guiScale().set(c.guiScale);
            //? if >=26.1.2
            /*mc.resizeGui();*/
            //? if <26.1.2
            mc.resizeDisplay();
            mc.options.showSubtitles().set(c.subtitles); mc.options.hideGui=c.hidden;
            var nativeSink=(AudioEngine.Sink)read(AudioEngine.class,engine,"sink");
            check(Float.compare(nativeSink.gain(cue),audible?cue.playback().gain():0)==0,
                    c.name+": native mixer owns sliders; adapter supplies unchanged authored gain unless fully muted");
            nativeDraw(graphics); // updates the native listener subscription through its actual render path
            if(c.route==2) AudioUi.press(this,"fixture/control");
            else {
                var actor=mc.player.getUUID(); var server=mc.getSingleplayerServer();
                operation=CompletableFuture.runAsync(()->{
                    var player=server.getPlayerList().getPlayer(actor);
                    if(c.route==1) FeedbackDispatch.player(player,c.semantic);
                    else if(c.route==3) FeedbackDispatch.world(player.serverLevel(),c.semantic,player,player.getEyePosition());
                    else CombatVisualFeedback.at(player.serverLevel(),TransientVisualIds.WORLD_EXPLOSIVE_PAYLOAD,player.getEyePosition(),new Vec3(0,1,0),1,1,VisualIntensity.STANDARD,SemanticVisualColor.OFFENSE,20,99,0,0);
                },server);
            }
            stage=1; stageTick=ticks; return;
        }
        if(operation!=null && operation.isCompletedExceptionally()) operation.join();
        if(engine.history()==0) { checkTimeout(80,c.name); return; }
        // Remove every transient visual before validating audio/text. This never gates sound admission.
        TransientWorldVisuals.clear();
        boolean text=c.route!=2 && c.subtitles;
        check(captions.lines().size()==(text?1:0),c.name+": sole vanilla-subtitles semantic eligibility");
        check(engine.voices()==(audible?1:0),c.name+": no dummy muted voice or duplicated activation");
        Screen saved=mc.screen;
        try {
            mc.screen=c.covered?this:null;
            CaptionRouter.beginFrame();
            if(!c.hidden) nativeDraw(graphics);
            if(c.covered) { graphics.fill(0,0,graphics.guiWidth(),graphics.guiHeight(),0xFF202020); CaptionRouter.screenStarted(); }
            if(text) {
                var ticket=captions.lines().iterator().next();
                boolean actualNative=device && audible && c.subtitles && (c.route==0 || c.route==3) && !c.hidden && !c.covered;
                check(captions.supplemental(ticket,c.subtitles)!=actualNative,c.name+": exactly one visible caption path");
                CaptionRouter.render(graphics);
                if((c.hidden || c.covered) && device && audible) {
                    mc.options.hideGui=false; mc.screen=null; CaptionRouter.beginFrame(); nativeDraw(graphics);
                    check(!captions.supplemental(ticket,true),c.name+": same ticket coalesces when native HUD returns");
                }
            } else {
                check(captions.lines().isEmpty(),c.name+": no separate audio caption preference can override native Off or quiet UI");
            }
        } finally { mc.screen=saved; }
        System.out.println("AudioWorldAcceptance: matrix PASS "+c.name);
        index++; stage=0; operation=null;
    }
    private void sourceIdentity() {
        var server=mc.getSingleplayerServer(); var actor=mc.player.getUUID();
        if(stage==0) {
            clear(); volume(SoundSource.MASTER,1); volume(SoundSource.PLAYERS,1);
            mc.options.showSubtitles().set(true);
            operation=CompletableFuture.runAsync(()->{
                var player=server.getPlayerList().getPlayer(actor);
                var other=new net.minecraft.world.entity.item.ItemEntity(player.serverLevel(),player.getX(),player.getY(),player.getZ(),
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
                other.setNoGravity(true); other.setDeltaMovement(Vec3.ZERO); other.setPickUpDelay(32767);
                check(player.serverLevel().addFreshEntity(other),"disposable second actor spawns through native tracking"); identityActor=other.getId();
            },server);
            stage=1; stageTick=ticks; return;
        }
        if(!operation.isDone()) { checkTimeout(80,"second actor spawn"); return; }
        operation.join();
        if(stage==1) {
            if(mc.level.getEntity(identityActor)==null) { checkTimeout(80,"second actor client tracking"); return; }
            operation=CompletableFuture.runAsync(()->{
                var player=server.getPlayerList().getPlayer(actor); var other=player.serverLevel().getEntity(identityActor); var position=player.getEyePosition();
                FeedbackDispatch.world(player.serverLevel(),"weapon.cast.release",player,position);
                FeedbackDispatch.world(player.serverLevel(),"weapon.cast.release",other,position);
                FeedbackDispatch.world(player.serverLevel(),"weapon.cast.release",player,position.add(1.25,0,0));
            },server);
            stage=2; stageTick=ticks; return;
        }
        if(engine.history()<3) { checkTimeout(80,"same-position actor envelopes"); return; }
        check(engine.history()==3 && engine.voices()==2,"same-position tracked actors keep two voices while moved repeat of the first actor is suppressed");
        operation=CompletableFuture.runAsync(()->{ var entity=server.getPlayerList().getPlayer(actor).serverLevel().getEntity(identityActor); if(entity!=null) entity.discard(); },server);
        System.out.println("AudioWorldAcceptance: actor identity PASS; actual local player and tracked disposable entity, three envelopes/two voices; not two-client multiplayer");
        identityChecked=true; stage=0;
    }
    //? if >=26.1.2
    /*private void lifecycle(GuiGraphicsExtractor graphics) throws Exception {*/
    //? if <26.1.2
    private void lifecycle(GuiGraphics graphics) throws Exception {
        if(stage==0) {
            clear(); volume(SoundSource.MASTER,1); volume(SoundSource.PLAYERS,1); volume(SoundSource.BLOCKS,1);
            ceremonyDeferral();
            mc.options.showSubtitles().set(true); mc.options.hideGui=false; mc.options.notificationDisplayTime().set(2D);
            cruciblePosition=mc.player.blockPosition().offset(2,1,0); position=cruciblePosition.offset(2,0,0); var server=mc.getSingleplayerServer(); var actor=mc.player.getUUID();
            operation=CompletableFuture.runAsync(()->{
                var player=server.getPlayerList().getPlayer(actor); player.setGameMode(GameType.SPECTATOR);
                player.serverLevel().setBlockAndUpdate(cruciblePosition,EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState());
                player.serverLevel().setBlockAndUpdate(position,EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState());
            },server);
            stage=1; stageTick=ticks; return;
        }
        if(operation!=null && operation.isCompletedExceptionally()) operation.join();
        if(stage==1) {
            if(!(mc.level.getBlockEntity(position) instanceof EssenceInfuserBlockEntity found)
                    || !(mc.level.getBlockEntity(cruciblePosition) instanceof EssenceCrucibleBlockEntity foundCrucible) || ticks-stageTick<20) { checkTimeout(120,"transfer endpoint tracking"); return; }
            crucible=foundCrucible;
            source=found; snapshot(source,true,false); check(engine.sources()==0,"enabled idle machine remains silent");
            snapshot(source,false,true); bodyStarted=System.nanoTime(); stage=2; stageTick=ticks;
        }
        Screen saved=mc.screen;
        try {
            mc.screen=null; CaptionRouter.beginFrame(); nativeDraw(graphics);
            if(stage==2) {
                if(ticks-stageTick<8) return;
                body=voice(source); check(engine.sources()==1 && (!device || body!=null),"real loaded Infuser snapshot owns one body");
                snapshot(source,false,true); check(voice(source)==body,"equal/update snapshot does not restart native body");
                stage=3;
            }
            if(stage==3) {
                long elapsed=(System.nanoTime()-bodyStarted)/1_000_000;
                var ticket=captions.lines().stream().filter(t->t.cue==AudioCatalog.get().profile("machine.resonance").body()).findFirst().orElse(null);
                if(elapsed>5200 && elapsed<5800 && device && !longNativeChecked) {
                    check(ticket!=null && !captions.supplemental(ticket,true),"long native display preference has no five-second duplicate"); longNativeChecked=true;
                }
                if(elapsed<13500) { CaptionRouter.render(graphics); return; }
                check(!device || longNativeChecked,"long-lived native caption was sampled before expiry");
                check(voice(source)==body && body.alive() && mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance)body),"same native body remains active beyond one full twelve-second sample period");
                System.out.println("AudioWorldAcceptance: full-period native loop PASS; held "+elapsed+" ms on the same active native handle; no perceived-seam approval");
                check(ticket!=null && captions.supplemental(ticket,true),"native body subtitle expiry exposes retained semantic refresh");
                CaptionRouter.render(graphics); snapshot(source,true,false); stage=4; stageTick=ticks; return;
            }
            if(stage==4) {
                if(ticks-stageTick<10) return;
                check(engine.sources()==0 && engine.voices()==0,"inactive observer snapshot releases native body");
                oldSource=source; mc.level.removeBlockEntity(position);
                source=new EssenceInfuserBlockEntity(position,EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState());
                mc.level.setBlockEntity(source); snapshot(source,false,true); stage=5; stageTick=ticks; return;
            }
            if(stage==5) {
                if(ticks-stageTick<8) return;
                previous=voice(source); oldSource.setRemoved();
                check(engine.sources()==1 && (!device || previous!=null) && voice(source)==previous,"old native removal cannot kill replacement at the same position");
                mc.getLanguageManager().setSelected("de_de"); mc.options.languageCode="de_de";
                operation=mc.reloadResourcePacks(); stage=6; stageTick=ticks; return;
            }
            if(stage==6) {
                if(!operation.isDone() || mc.getOverlay()!=null) return;
                operation.join(); check(mc.getLanguageManager().getSelected().equals("de_de"),"real language/resource reload selects requested locale with English fallback captions"); stage=7; stageTick=ticks; return;
            }
            if(stage==7) {
                if(ticks-stageTick<35) return;
                body=voice(source);
                System.out.println("AudioWorldAcceptance: reload sources="+engine.sources()+" voices="+engine.voices()+" replaced="+(body!=previous));
                check(engine.sources()==1 && (!device || body!=null && body!=previous),"real resource reload resumes exactly one body");
                previous=body; mc.getSoundManager().reload(); stage=8; stageTick=ticks; return;
            }
            if(stage==8) {
                if(ticks-stageTick<40) return;
                if(resourceAcceptance==null) {
                    body=voice(source); check(engine.sources()==1 && (!device || body!=null && body!=previous),"native sound-engine restart resumes body with backoff");
                }
                if(Boolean.getBoolean("essence.audio.resourceAcceptance")) {
                    if(resourceAcceptance==null) resourceAcceptance=new AudioResourceAcceptance(engine,()->voice(source),ticks);
                    if(!resourceAcceptance.tick(ticks)) return;
                }
                source.setRemoved(); check(engine.sources()==0 && engine.voices()==0,"native source removal bypasses admission");
                flightLifecycle();
                finishing=true; mc.getConnection().getConnection().disconnect(Component.literal("Audio fixture complete")); stage=9;
            }
        } finally { mc.screen=saved; }
    }
    private void snapshot(EssenceInfuserBlockEntity entity,boolean enabled,boolean active) {
        var original=entity.visualState(); var tag=new CompoundTag();
        tag.put(MachineVisualState.TAG,new MachineVisualState.Infuser(cruciblePosition,enabled,active?5:11,1,100,active?10:0,null,active?1:0,original.focus()).save());
        BlockEntityTestNbtCompat.load(entity,tag,mc.level.registryAccess());
    }
    /** Same runtime ticks and engine as transfer sources; only the tracked presentation flag differs. */
    private void flightLifecycle() {
        var flags=(com.mistaboom.essence_ascendance.visual.FlightVisualState)mc.player;
        int before=flags.essenceAscendance$flightFlags();
        var key=new AudioEngine.Source(mc.player,"thrust");
        try {
            flags.essenceAscendance$flightFlags(before|com.mistaboom.essence_ascendance.visual.FlightVisualState.THRUST);
            AudioRuntime.tick(); AudioRuntime.tick();
            var active=((Map<?,?>)read(AudioEngine.class,engine,"sources")).get(key);
            var voice=active==null?null:(AudioEngine.Voice)read(active.getClass(),active,"voice");
            check(engine.sources()==1 && (!device || voice!=null),"tracked flight thrust owns one native body through shared discovery");
            flags.essenceAscendance$flightFlags(before&~com.mistaboom.essence_ascendance.visual.FlightVisualState.THRUST);
            AudioRuntime.tick();
            check(engine.sources()==1 && (!device || engine.voices()==1),"flight cessation uses shared release policy instead of invalid-incarnation removal");
            flags.essenceAscendance$flightFlags(before|com.mistaboom.essence_ascendance.visual.FlightVisualState.THRUST);
            AudioRuntime.tick(); AudioRuntime.tick();
            active=((Map<?,?>)read(AudioEngine.class,engine,"sources")).get(key);
            check(engine.sources()==1 && (!device || read(active.getClass(),active,"voice")==voice),"thrust resumes during fade without starting another native voice");
            flags.essenceAscendance$flightFlags(before&~com.mistaboom.essence_ascendance.visual.FlightVisualState.THRUST);
            for(int tick=0;tick<AudioCatalog.get().profile("mobility.thrust").lifecycle().releaseTicks()+3;tick++) AudioRuntime.tick();
            check(engine.sources()==0 && engine.voices()==0,"sustained zero thrust releases its body and desired source through common lifecycle");
            System.out.println("AudioWorldAcceptance: flight lifecycle PASS; native tracked flags, configured release, resume without replay and stop; no flight gameplay authorization or listening claim");
        } finally { flags.essenceAscendance$flightFlags(before); }
    }
    private void ceremonyDeferral() throws Exception {
        var event=new WorldVisualEvent(TransientVisualIds.WORLD_ASCENDANCE_CEREMONY,mc.level.dimension().location(),mc.player.position(),
                mc.player.getId(),WorldVisualEvent.NO_ENTITY,null,new Vec3(0,1,0),1,1,VisualIntensity.STANDARD,SemanticVisualColor.DORMANT,160,9191,0,0);
        var nexus=new com.mistaboom.essence_ascendance.client.AscendanceNexusScreen(
                new com.mistaboom.essence_ascendance.nexus.AscendanceNexusMenu(901,mc.player.getInventory()),mc.player.getInventory(),Component.literal("Fixture Nexus"));
        int[] deliveries={0}; Screen saved=mc.screen;
        try {
            mc.screen=nexus;
            check(com.mistaboom.essence_ascendance.client.AscensionAnimation.defer(event,()->deliveries[0]++),"local full ceremony waits for confirmed Nexus release");
            check(com.mistaboom.essence_ascendance.client.AscensionAnimation.defer(event,()->deliveries[0]++) && deliveries[0]==0,"duplicate pending ceremony cannot play either modality early");
            var tier=net.minecraft.resources.ResourceLocation.parse("essence_ascendance:dormant");
            com.mistaboom.essence_ascendance.client.AscensionAnimation.confirmed(tier);
            com.mistaboom.essence_ascendance.client.AscensionAnimation.confirmed(tier);
            check(deliveries[0]==1,"one confirmed handoff releases one shared presentation callback");
            com.mistaboom.essence_ascendance.client.AscensionAnimation.defer(event,()->deliveries[0]++);
            var pendingTick=com.mistaboom.essence_ascendance.client.AscensionAnimation.class.getDeclaredField("pendingTick"); pendingTick.setAccessible(true);
            pendingTick.setLong(null,mc.level.getGameTime()-101);
            com.mistaboom.essence_ascendance.client.AscensionAnimation.tick();
            com.mistaboom.essence_ascendance.client.AscensionAnimation.confirmed(tier);
            check(deliveries[0]==1,"expired pending ceremony cannot release late presentation");
        } finally { com.mistaboom.essence_ascendance.client.AscensionAnimation.clear(); mc.screen=saved; }
    }
    private AudioEngine.Voice voice(Object owner) {
        var active=((Map<?,?>)read(AudioEngine.class,engine,"sources")).get(new AudioEngine.Source(crucible,"transfer",owner));
        return active==null?null:(AudioEngine.Voice)read(active.getClass(),active,"voice");
    }
    private void clear() {
        engine.clear(); CaptionRouter.clear();
        ((List<?>)read(SubtitleOverlay.class,nativeOverlay,"subtitles")).clear();
        ((List<?>)read(SubtitleOverlay.class,nativeOverlay,"audibleSubtitles")).clear();
    }
    //? if >=26.1.2
    /*private void nativeDraw(GuiGraphicsExtractor graphics) { nativeOverlay.extractRenderState(graphics); }*/
    //? if <26.1.2
    private void nativeDraw(GuiGraphics graphics) { nativeOverlay.render(graphics); }
    private void volume(SoundSource category,double volume) { mc.options.getSoundSourceOptionInstance(category).set(volume); }
    private void checkTimeout(int limit,String label) { if(ticks-stageTick>limit) throw new AssertionError("Timed out: "+label); }
    static Object read(Class<?> type,Object owner,String name) {
        try { var field=type.getDeclaredField(runtimeField(type,name)); field.setAccessible(true); return field.get(owner); }
        catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    /** Test-only mapping of reflective native fields when this fixture runs as a production mod. */
    private static final Map<String,String> FIELD_NAMES=new HashMap<>();
    private static String runtimeField(Class<?> type,String name) {
        try { type.getDeclaredField(name); return name; } catch(NoSuchFieldException missing) { }
        String mapping=System.getProperty("essence.audio.fixtureMappings");
        if(mapping==null) return name;
        String key=type.getName()+"#"+name;
        return FIELD_NAMES.computeIfAbsent(key,ignored->{
            try {
                int column=-1;
                for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of(mapping))) {
                    String[] fields=line.split("\t");
                    if(fields[0].equals("c")) {
                        column=-1;
                        for(int i=1;i<fields.length;i++) if(fields[i].replace('/','.').equals(type.getName())) column=i;
                    } else if(column>=0 && fields.length>column+2 && fields[0].isEmpty() && fields[1].equals("f") && fields[fields.length-1].equals(name)) return fields[column+2];
                }
                return name;
            } catch(java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
    }
    private static void check(boolean passed,String label) { if(!passed) throw new AssertionError(label); checks++; }
    private static void fail(Throwable failure) { failure.printStackTrace(); System.exit(1); }
}
