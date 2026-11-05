package com.mistaboom.essence_ascendance.audio;

import com.mistaboom.essence_ascendance.client.audio.AudioUi;
import com.mistaboom.essence_ascendance.network.FeedbackDispatch;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Controlled clock and recording sink exercise behavior without a renderer, device, world or network. */
public final class AudioRuntimeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var catalog=AudioCatalog.get();
        var world=catalog.recipe("combat.explosion"); var ui=catalog.recipe("ui.press"); var privateCue=catalog.recipe("interface.acknowledge");
        var machine=catalog.profile("machine.resonance"); var channel=catalog.profile("channel.resonance");
        check(world!=null && ui!=null && machine!=null && channel!=null,"all vertical proofs resolve from generated descriptor");
        Recording sink=new Recording(); AudioEngine engine=new AudioEngine(sink); engine.tick(0,Vec3.ZERO);
        var occurrence=context("source",0,1);
        check(engine.oneShot(world,occurrence),"first confirmed occurrence");
        check(!engine.oneShot(world,occurrence) && sink.plays==1 && sink.captions==1,"duplicate cannot replay audio or caption");
        check(engine.oneShot(world,context("other",0,2)),"independent source/action admitted");
        check(!engine.oneShot(world,context("far",100,3)),"outside policy radius has neither modality");
        check(sink.captions==2,"range drop does not caption hidden facts");
        sink.gain=0; engine.oneShot(privateCue,context("private",0,4));
        check(sink.plays==2 && sink.captions==3,"mute preserves semantic text without a dummy native voice");
        check(!engine.oneShot(privateCue,context("private",0,5)),"same-source repeat gate shared with mute");
        engine.tick(3,Vec3.ZERO); check(engine.oneShot(ui,context("control",0,6)),"local UI uses same one-shot path");
        check(engine.oneShot(privateCue,context("otherPlayer",0,7)),"private acknowledgement uses same path");
        engine.tick(6,Vec3.ZERO,true); check(engine.oneShot(ui,context("control",0,8)),"paused menus still advance local input admission");

        engine.clear(); sink=new Recording(); engine=new AudioEngine(sink); engine.tick(0,Vec3.ZERO);
        Object block=new Object(),player=new Object(); AtomicBoolean valid=new AtomicBoolean(true);
        check(!new AudioEngine.Source(block,"work").equals(new AudioEngine.Source(player,"work")) && !new AudioEngine.Source(block,"work").equals(new AudioEngine.Source(block,"other")),"shared keys distinguish native incarnation and activity channel");
        check(!new AudioEngine.Source(new String("recycled-id"),"work").equals(new AudioEngine.Source(new String("recycled-id"),"work")),"owner value equality cannot merge different native incarnations");
        engine.reconcile(block,machine,context(block,0,1),true,1,valid::get);
        engine.reconcile(player,channel,context(player,0,2),true,1,valid::get);
        engine.tick(1,Vec3.ZERO); check(sink.plays==2 && engine.voices()==2,"block and player share one controller and admission");
        engine.reconcile(block,machine,context(block,1,1),true,1,valid::get);
        engine.tick(2,Vec3.ZERO); check(sink.plays==2 && sink.last.position.x==0,"equal/update does not restart other source");
        engine.reconcile(block,machine,context(block,0,1),false,0,valid::get);
        engine.tick(3,Vec3.ZERO); check(engine.voices()==2,"old revision cannot stop current generation");
        engine.reconcile(block,machine,context(block,0,1),false,2,valid::get);
        engine.reconcile(block,machine,context(block,0,1),true,3,valid::get);
        engine.tick(4,Vec3.ZERO); check(sink.plays==2,"reactivation during fade reuses handle");
        sink.gain=0; engine.tick(5,Vec3.ZERO); check(engine.voices()==0 && engine.sources()==2,"mute stops voices but retains valid desired bodies");
        sink.gain=1; engine.tick(25,Vec3.ZERO); check(engine.voices()==2 && sink.plays==4,"unmute resumes bodies only");
        int captions=sink.captions; engine.tick(50,Vec3.ZERO); check(sink.captions==captions,"body captions do not repeat per waveform/tick");
        engine.tick(102,Vec3.ZERO); check(sink.captions==captions+2,"body caption refresh uses common cadence");
        sink.gain=0; engine.tick(203,Vec3.ZERO);
        check(engine.voices()==0 && engine.sources()==2 && sink.captions==captions+4,
                "vanilla volume mute retains desired continuous bodies and normal semantic caption cadence");
        int mutedCaptions=sink.captions,mutedPlays=sink.plays;
        engine.tick(204,Vec3.ZERO);
        check(sink.captions==mutedCaptions && sink.plays==mutedPlays,"muted continuous source has neither per-tick captions nor dummy playback");
        sink.gain=1; engine.tick(224,Vec3.ZERO);
        check(engine.voices()==2 && sink.plays==mutedPlays+2 && sink.captions==mutedCaptions,
                "native-volume restore recovers only desired bodies without replaying captions or accents");
        int reloadCaptions=sink.captions; engine.invalidate(); check(engine.voices()==0,"reload stops all handles immediately");
        engine.tick(225,Vec3.ZERO); check(engine.voices()==0,"reload recovery respects backoff");
        check(sink.captions==reloadCaptions+2,"reload rebuilds valid body captions before audio recovery");
        engine.tick(245,Vec3.ZERO); check(engine.voices()==2,"reload recovers desired bodies");
        valid.set(false); engine.tick(246,Vec3.ZERO); check(engine.sources()==0 && engine.voices()==0,"invalid incarnations are removed without render or packet");
        Object replacement=new Object(); engine.reconcile(replacement,machine,context(replacement,0,1),true,1,()->true);
        engine.remove(block); engine.tick(247,Vec3.ZERO); check(engine.voices()==1,"stale owner removal cannot kill replacement at same position");
        engine.reconcile(replacement,machine,context(replacement,0,1),false,2,()->true);
        for(long t=248;t<260;t++) engine.tick(t,Vec3.ZERO);
        check(engine.sources()==0 && engine.voices()==0,"inactive body releases and removes desired state");

        engine.clear(); sink=new Recording(); engine=new AudioEngine(sink); engine.tick(0,Vec3.ZERO);
        for(int n=0;n<1000;n++) { Object source=new Object(); engine.reconcile(source,machine,context(source,n%16,0),true,1,()->true); }
        engine.tick(1,Vec3.ZERO); check(engine.sources()<=256 && engine.voices()<=12,"1000 candidate sources stay bounded by shared group/source limits");
        engine.clear(); engine.tick(0,Vec3.ZERO);
        var shared=new AudioCatalog.Admission("shared",1,1,0);
        var body=new AudioCatalog.Cue(machine.body().id(),machine.body().playback(),machine.body().audience(),shared,machine.body().subtitle());
        var accent=new AudioCatalog.Cue(world.id(),world.playback(),world.audience(),shared,world.subtitle());
        Object owner=new Object(); engine.reconcile(owner,new AudioCatalog.Profile(body,machine.lifecycle()),context(owner,10,1),true,1,()->true);
        engine.tick(1,Vec3.ZERO); int before=sink.plays;
        check(engine.oneShot(accent,context("nearAccent",0,2)) && engine.voices()==1 && sink.plays==before+1,"one-shots and bodies share group concurrency and priority eviction");
        engine.tick(2,Vec3.ZERO); check(engine.voices()==1 && sink.plays==before+1,"evicted body cannot displace higher-priority accent");
        engine.clear(); engine.tick(0,Vec3.ZERO);
        check(engine.oneShot(world,context("sameSource",0,1)) && engine.oneShot(world,context("sameSource",0,2)),"zero repeat interval admits distinct same-tick occurrences through concurrency policy");
        engine.clear(); sink.gain=0;
        for(int n=0;n<10000;n++) { engine.tick(n/64,Vec3.ZERO); engine.oneShot(world,context(n,0,n)); }
        check(engine.history()<=448,"10000 world occurrences cannot consume private history reserve");
        check(engine.oneShot(privateCue,context("reserved",0,10001)),"private occurrence admitted while public history saturated");
        engine.tick(1,Vec3.ZERO); check(engine.history()==0,"time rewind invalidates temporal state");
        check(FeedbackDispatch.nearby(Vec3.ZERO,new Vec3(16,0,0),16) && !FeedbackDispatch.nearby(Vec3.ZERO,new Vec3(17,0,0),16),"shared recipient radius boundary");

        SemanticCaptions captionsState=new SemanticCaptions();
        var ticket=captionsState.request(world,0,true,3000,"native.world");
        check(captionsState.lines().size()==1 && captionsState.supplemental(ticket,true),"unconfirmed native callback cannot remove meaningful text");
        captionsState.nativeRendered("native.world"); check(!captionsState.supplemental(ticket,true),"actual native Component draw suppresses duplicate supplement");
        check(!captionsState.supplemental(ticket,false),"vanilla subtitles Off suppresses supplemental text immediately, including before the next tick");
        captionsState.beginFrame(); check(captionsState.supplemental(ticket,true),"F1/covered screen or range/expiry change with no native draw retains one supplement while vanilla subtitles are On");
        captionsState.nativeRendered("unrelated"); check(captionsState.supplemental(ticket,true),"unrelated native captions cannot suppress this cue");
        captionsState.nativeRendered("native.world"); check(!captionsState.supplemental(ticket,true),"returning native caption coalesces without replay");
        captionsState.tick(3001,true); check(captionsState.lines().isEmpty(),"caption expiry does not depend on rendering");
        ticket=captionsState.request(world,4000,true,10000,"native.world");
        captionsState.tick(9000,true); check(captionsState.lines().size()==1,"native notification duration controls semantic retention too");
        captionsState.beginFrame(); captionsState.nativeRendered("native.world"); check(!captionsState.supplemental(ticket,true),"long native subtitle lifetime never creates a second line");
        captionsState.beginFrame(); check(captionsState.supplemental(ticket,true),"same retained cue survives native text expiry without a new occurrence");
        captionsState.tick(9001,false); check(captionsState.lines().isEmpty(),"subtitle preference disables queued supplement");
        check(captionsState.request(world,9002,false,3000,"native.world")==null,"disabled captions do not enqueue");
        check(captionsState.request(privateCue,9003,false,3000,null)==null && captionsState.lines().isEmpty(),
                "vanilla subtitles Off also rejects private/relative captions; no independent preference path exists");
        var relative=captionsState.request(privateCue,9004,true,3000,null);
        ticket=captionsState.request(world,9004,true,3000,"native.world");
        check(captionsState.lines().size()==2 && captionsState.supplemental(relative,true) && captionsState.supplemental(ticket,true),
                "one vanilla subtitles toggle enables neutral private and world semantic captions together");
        captionsState.nativeRendered("native.world");
        check(captionsState.supplemental(relative,true) && !captionsState.supplemental(ticket,true),
                "actual world subtitle draw suppresses only its duplicate while private caption remains neutral");
        captionsState.tick(9005,false);
        check(captionsState.lines().isEmpty() && !captionsState.supplemental(relative,false) && !captionsState.supplemental(ticket,false),
                "one vanilla Off toggle clears both audiences and prevents stale-ticket drawing");
        captionsState.tick(9006,true);
        check(captionsState.lines().isEmpty(),"turning vanilla subtitles back On cannot resurrect old private/world tickets");
        for(int n=0;n<100;n++) {
            var cue=new AudioCatalog.Cue(world.id(),world.playback(),world.audience(),world.admission(),"test."+n);
            captionsState.request(cue,11004,true,3000,"native."+n);
        }
        check(captionsState.lines().size()==8,"caption queue stays bounded");
        captionsState.request(privateCue,11005,true,3000,null);
        for(int n=100;n<120;n++) captionsState.request(new AudioCatalog.Cue(world.id(),world.playback(),world.audience(),world.admission(),"test."+n),11006,true,3000,"native."+n);
        check(captionsState.lines().stream().anyMatch(t->t.cue==privateCue),"ambient pressure cannot evict a higher-priority private caption");
        check(captionsState.lines().iterator().next().cue==privateCue,"highest-priority text is placed first when screen space is limited");
        for(int scale=1;scale<=3;scale++) for(int lineHeight:new int[]{9,12,18}) {
            int width=854/scale,height=480/scale;
            var reserved=List.of(new com.mistaboom.essence_ascendance.client.ui.UiBounds(width/2-60,height-74,120,lineHeight+4),
                    new com.mistaboom.essence_ascendance.client.ui.UiBounds(width-206,height-103,200,44));
            var placed=com.mistaboom.essence_ascendance.client.audio.CaptionLayout.place(width,height-65,Math.min(180,width-24),lineHeight,reserved);
            check(reserved.stream().noneMatch(placed::intersects),"caption avoids actual native actionbar and effect-card bounds at GUI scale "+scale+"/font "+lineHeight);
            var raised=List.of(new com.mistaboom.essence_ascendance.client.ui.UiBounds(width/2-60,height-94,120,lineHeight+4));
            placed=com.mistaboom.essence_ascendance.client.audio.CaptionLayout.place(width,height-85,Math.min(180,width-24),lineHeight,raised);
            check(!placed.intersects(raised.getFirst()),"caption also avoids NeoForge actionbar raised by native HUD height");
        }
        captionHudGeometry();
        captionHudFramesAndMotion();
        captionsState.tick(15000,true); check(captionsState.lines().isEmpty(),"caption expiry is independent of rendering");
        List<Object> presses=new ArrayList<>(); AudioUi.install((semantic,control)->presses.add(control));
        Object screen1=new Object(),screen2=new Object(); AudioUi.press(screen1,"same"); AudioUi.press(screen2,"same");
        check(!presses.get(0).equals(presses.get(1)),"reopened screens have independent control ownership");
        check(FeedbackTime.fresh(200,300) && !FeedbackTime.fresh(200,301),"shared semantic transport retains its bounded freshness");
        check(!FeedbackTime.fresh(200,179),"future/old-world occurrences fail temporal acceptance");
        var event=new com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent(world.id(),net.minecraft.resources.ResourceLocation.parse("minecraft:overworld"),Vec3.ZERO,-1,-1,null,new Vec3(0,1,0),1,1,com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity.STANDARD,com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor.OFFENSE,20,1,0,0);
        var payload=new com.mistaboom.essence_ascendance.network.WorldVisualEventPayload(event,41,200,48);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {
            com.mistaboom.essence_ascendance.network.WorldVisualEventPayload.CODEC.encode(buffer,payload);
            check(payload.equals(com.mistaboom.essence_ascendance.network.WorldVisualEventPayload.CODEC.decode(buffer)) && buffer.readableBytes()==0,"versioned shared envelope round-trips identity, time and independent visual radius");
        } finally { buffer.release(); }
        System.out.println("Audio runtime behavior: "+checks+" assertions PASS (recording sink; no hearing/world claim)");
        AudioIntegrationTest.run();
        AudioCatalogBehaviorTest.run();
        AudioExtensionTest.run();
    }
    private static void captionHudGeometry() {
        int cardWidth=com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudLayout.WIDTH;
        int cardHeight=com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudLayout.HEIGHT;
        for(int scale=1;scale<=4;scale++) for(int lines=1;lines<=8;lines++) {
            int width=1920/scale,height=1080/scale,normalBottom=height-59,left=width-cardWidth-6;
            var nativeBox=new com.mistaboom.essence_ascendance.client.ui.UiBounds(width-153,height-30-lines*10,152,lines*10);
            var footprints=new com.mistaboom.essence_ascendance.client.ui.UiBounds[]{nativeBox};
            int bottom=com.mistaboom.essence_ascendance.client.audio.CaptionLayout.cardStackBottom(normalBottom,left,width-6,footprints);
            var settledCard=new com.mistaboom.essence_ascendance.client.ui.UiBounds(left,bottom-cardHeight,cardWidth,cardHeight);
            check(!settledCard.intersects(nativeBox),"settled right card avoids "+lines+" unchanged native lines at GUI scale "+scale);
            check(bottom<=normalBottom && settledCard.y()>=6,"caption avoidance only lifts the right stack and retains visible space");
            check(nativeBox.equals(footprints[0]) && nativeBox.right()==width-1 && nativeBox.bottom()==height-30,
                    "native box keeps its exact vanilla anchor, dimensions and row spacing");
            check(lines<=2?bottom==normalBottom:bottom<normalBottom,"card anchor changes only once caption height reaches the stack");
            var supplement=com.mistaboom.essence_ascendance.client.audio.CaptionLayout.place(width,nativeBox.y()-4-9-3,180,9,List.of());
            check(supplement.right()==width-1 && supplement.bottom()==nativeBox.y()-4,
                    "neutral supplemental line shares vanilla right edge immediately above actual native rows");
            int combined=com.mistaboom.essence_ascendance.client.audio.CaptionLayout.cardStackBottom(normalBottom,left,width-6,
                    new com.mistaboom.essence_ascendance.client.ui.UiBounds[]{nativeBox,supplement});
            check(combined==supplement.y()-4,"one right-stack policy yields to both native and supplemental captions");
        }
        int width=480,height=270,normalBottom=height-59;
        var nativeRows=new com.mistaboom.essence_ascendance.client.ui.UiBounds[8];
        com.mistaboom.essence_ascendance.client.ui.UiBounds union=null;
        for(int i=0;i<8;i++) {
            nativeRows[i]=new com.mistaboom.essence_ascendance.client.ui.UiBounds(width-153,height-40-i*10,152,10);
            union=com.mistaboom.essence_ascendance.client.audio.CaptionLayout.union(union,nativeRows[i]);
        }
        check(union.equals(new com.mistaboom.essence_ascendance.client.ui.UiBounds(width-153,height-110,152,80)),
                "actual native draw footprints coalesce without another subtitle filter/list");
        check(com.mistaboom.essence_ascendance.client.audio.CaptionLayout.cardStackBottom(normalBottom,274,474,
                        new com.mistaboom.essence_ascendance.client.ui.UiBounds[]{new com.mistaboom.essence_ascendance.client.ui.UiBounds(6,6,200,44)})==normalBottom,
                "unrelated fixed left-hand channel cards do not lift the right-hand stack");
    }
    private static void captionHudFramesAndMotion() throws Exception {
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.clear();
        var nativeBox=new com.mistaboom.essence_ascendance.client.ui.UiBounds(327,160,152,80);
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.nativeBounds(480,270,nativeBox);
        check(com.mistaboom.essence_ascendance.client.audio.CaptionRouter.cardStackBottom(480,270,274,474,211)==211,
                "both render orders consume only the completed frame, never a partially observed caption box");
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.beginFrame();
        check(com.mistaboom.essence_ascendance.client.audio.CaptionRouter.cardStackBottom(480,270,274,474,211)==156,
                "complete native footprint becomes the next frame's upward anchor target");
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.beginFrame();
        check(com.mistaboom.essence_ascendance.client.audio.CaptionRouter.cardStackBottom(480,270,274,474,211)==211,
                "one frame without native draws releases the footprint, including preference-off and expiration");
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.nativeBounds(480,270,nativeBox);
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.beginFrame();
        check(com.mistaboom.essence_ascendance.client.audio.CaptionRouter.cardStackBottom(640,360,434,634,301)==301,
                "viewport resize cannot reuse old scaled caption coordinates");
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.nativeBounds(480,270,nativeBox);
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.screenStarted();
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.beginFrame();
        check(com.mistaboom.essence_ascendance.client.audio.CaptionRouter.cardStackBottom(480,270,274,474,211)==211,
                "an opaque legacy screen removes the covered native footprint");
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.nativeBounds(480,270,nativeBox);
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.beginFrame();
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.clear();
        check(com.mistaboom.essence_ascendance.client.audio.CaptionRouter.cardStackBottom(480,270,274,474,211)==211,
                "shared disconnect/world/reload cleanup removes both caption frame buffers");
        var hud=com.mistaboom.essence_ascendance.client.SkillEffectHudOverlay.class;
        var lift=hud.getDeclaredField("captionLift"); lift.setAccessible(true);
        var animate=hud.getDeclaredMethod("animateCaptionLift",int.class,double.class); animate.setAccessible(true);
        var clear=hud.getDeclaredMethod("clearMotion"); clear.setAccessible(true); clear.invoke(null);
        animate.invoke(null,55,0d);
        check(((Double)lift.get(null))==0,"first frame with zero elapsed time cannot snap the stack upward");
        animate.invoke(null,55,1d/60);
        check(((Double)lift.get(null))>0 && ((Double)lift.get(null))<55,"caption appearance advances through existing smooth opening speed");
        for(int i=0;i<10;i++) animate.invoke(null,55,1d/60);
        check(((Double)lift.get(null))==55,"card stack settles exactly above the fixed native caption box");
        animate.invoke(null,0,0d); check(((Double)lift.get(null))==55,"zero-time release cannot snap the stack down");
        animate.invoke(null,0,1d/60);
        check(((Double)lift.get(null))>0 && ((Double)lift.get(null))<55,"caption expiration uses the existing smooth closing speed");
        for(int i=0;i<20;i++) animate.invoke(null,0,1d/60);
        check(((Double)lift.get(null))==0,"stack returns to its original bottom anchor without overshoot");
        animate.invoke(null,55,1d/60); clear.invoke(null);
        check(((Double)lift.get(null))==0,"HUD identity/hide cleanup clears caption lift with existing slot motion");
    }
    private static AudioEngine.Context context(Object source,double x,long occurrence) { return new AudioEngine.Context(source,new Vec3(x,0,0),occurrence); }
    static final class Recording implements AudioEngine.Sink {
        int plays,captions; float gain=1; TestVoice last; AudioCatalog.Cue lastCue;
        public float gain(AudioCatalog.Cue cue) { return gain; }
        public AudioEngine.Voice play(AudioCatalog.Cue cue,AudioEngine.Context context,boolean loop,float volume) { plays++; lastCue=cue; last=new TestVoice(context.position()); return last; }
        public void caption(AudioCatalog.Cue cue,AudioEngine.Context context,long tick) { captions++; }
        Vec3 lastPosition() { return last.position; }
    }
    private static final class TestVoice implements AudioEngine.Voice {
        boolean alive=true; Vec3 position;
        TestVoice(Vec3 pos) { position=pos; }
        public boolean alive() { return alive; }
        public void update(Vec3 pos,float gain,float pitch) { position=pos; }
        public void close() { alive=false; }
    }
    private static void check(boolean pass,String message) { checks++; if(!pass) throw new AssertionError(message); }
}
