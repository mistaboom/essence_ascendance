package com.mistaboom.essence_ascendance.audio;

import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The same policy matrix covers every registered cue and every continuous source profile. */
final class AudioCatalogBehaviorTest {
    private static int checks;
    static void run() throws Exception {
        var catalog=AudioCatalog.get();
        for(String recipe:catalog.recipeIds()) if(!recipe.contains(":")) for(float a:new float[]{0,1}) for(float b:new float[]{0,1})
            check(catalog.recipe(recipe,a,b)==catalog.recipe("essence_ascendance:"+recipe,a,b),"qualified shared envelope keeps one recipe owner and contextual selection: "+recipe+"/"+a+"/"+b);
        for(var cue:catalog.cues()) {
            var sink=new AudioRuntimeTest.Recording(); var engine=new AudioEngine(sink); engine.tick(0,Vec3.ZERO);
            var first=new AudioEngine.Context("source",Vec3.ZERO,1);
            check(engine.oneShot(cue,first) && sink.plays==1,"catalog cue shares playback: "+cue.id());
            check(!engine.oneShot(cue,first) && sink.plays==1 && sink.captions==1,"catalog duplicate coalesces both modalities: "+cue.id());
            engine.clear(); engine.tick(0,Vec3.ZERO); sink.gain=0;
            int captions=sink.captions;
            check(engine.oneShot(cue,first) && sink.plays==1 && sink.captions==captions+1,"catalog mute has semantic callback and no dummy voice: "+cue.id());
            engine.clear(); engine.tick(0,Vec3.ZERO); sink.gain=1;
            if(cue.audience().scope()==AudioCatalog.Scope.WORLD) {
                check(engine.oneShot(cue,new AudioEngine.Context("edge",new Vec3(cue.audience().radius(),0,0),2)),"inclusive world range: "+cue.id());
                captions=sink.captions;
                check(!engine.oneShot(cue,new AudioEngine.Context("outside",new Vec3(cue.audience().radius()+.01,0,0),3)) && sink.captions==captions,"range also bounds captions: "+cue.id());
            } else check(engine.oneShot(cue,new AudioEngine.Context("relative",new Vec3(10000,0,0),2)),"relative cue does not inherit world attenuation: "+cue.id());
        }
        // Change each named admission policy once, then exercise every derived cue. No feature exceptions.
        try(var input=AudioCatalogBehaviorTest.class.getResourceAsStream("/assets/essence_ascendance/audio/catalog.json")) {
            var data=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:data.getAsJsonObject("policies").getAsJsonObject("admission").entrySet()) entry.getValue().getAsJsonObject().addProperty("repeatTicks",7);
            var changed=new AudioCatalog(data);
            for(var cue:changed.cues()) {
                var sink=new AudioRuntimeTest.Recording(); sink.gain=0; var engine=new AudioEngine(sink); engine.tick(0,Vec3.ZERO);
                check(engine.oneShot(cue,new AudioEngine.Context("source",Vec3.ZERO,1)),"changed shared policy first occurrence: "+cue.id());
                engine.tick(6,Vec3.ZERO);
                check(!engine.oneShot(cue,new AudioEngine.Context("source",Vec3.ZERO,2)),"changed shared policy suppresses rapid repetition: "+cue.id());
                check(engine.oneShot(cue,new AudioEngine.Context("other player/source",Vec3.ZERO,3)),"repetition remains source scoped: "+cue.id());
                engine.tick(7,Vec3.ZERO);
                check(engine.oneShot(cue,new AudioEngine.Context("source",Vec3.ZERO,4)),"all feature types share changed repeat boundary: "+cue.id());
                engine.clear(); engine.tick(0,Vec3.ZERO);
                Object firstActor=new String("recycled-native-id"),otherActor=new String("recycled-native-id");
                check(engine.oneShot(cue,new AudioEngine.Context(AudioSources.oneShotSource(firstActor,11),Vec3.ZERO,11))
                        && engine.oneShot(cue,new AudioEngine.Context(AudioSources.oneShotSource(otherActor,12),Vec3.ZERO,12)),"distinct actor incarnations at one position retain independent admission: "+cue.id());
                check(!engine.oneShot(cue,new AudioEngine.Context(AudioSources.oneShotSource(firstActor,13),new Vec3(1,0,0),13)),"moving the same actor cannot evade repetition: "+cue.id());
                check(engine.oneShot(cue,new AudioEngine.Context(AudioSources.oneShotSource(new String("recycled-native-id"),14),Vec3.ZERO,14)),"recycled native value cannot inherit an old incarnation repeat gate: "+cue.id());
                check(engine.oneShot(cue,new AudioEngine.Context(AudioSources.oneShotSource(null,15),Vec3.ZERO,15))
                        && engine.oneShot(cue,new AudioEngine.Context(AudioSources.oneShotSource(null,16),Vec3.ZERO,16)),"untracked source occurrences cannot collapse unrelated actors: "+cue.id());
                check(!engine.oneShot(cue,new AudioEngine.Context(AudioSources.oneShotSource(null,15),Vec3.ZERO,15)),"missing native tracking still preserves duplicate suppression: "+cue.id());
            }
        }
        for(String id:catalog.profileIds()) {
            var profile=catalog.profile(id); var sink=new AudioRuntimeTest.Recording(); var engine=new AudioEngine(sink); engine.tick(0,Vec3.ZERO);
            Object source=new Object(),destination=new Object(); var key=new AudioEngine.Source(source,"transfer",destination);
            var context=new AudioEngine.Context(key,Vec3.ZERO,0);
            engine.reconcile(key,profile,context,true,1,()->true); engine.tick(1,Vec3.ZERO);
            check(engine.voices()==1 && sink.plays==1,"profile starts one body: "+id);
            engine.reconcile(key,profile,context,true,1,()->true); engine.tick(2,Vec3.ZERO);
            check(sink.plays==1,"equal profile snapshot cannot restart: "+id);
            sink.gain=0; engine.tick(3,Vec3.ZERO);
            check(engine.voices()==0 && engine.sources()==1,"profile mute keeps only desired source: "+id);
            sink.gain=1; engine.tick(4,Vec3.ZERO); engine.tick(30,Vec3.ZERO);
            check(engine.voices()==1,"profile unmute resumes body: "+id);
            engine.invalidate(); engine.tick(31,Vec3.ZERO); engine.tick(70,Vec3.ZERO);
            check(engine.voices()==1,"profile reload recovers one body: "+id);
            engine.removeIncarnation(new Object()); check(engine.voices()==1,"unrelated endpoint removal preserves body: "+id);
            engine.removeIncarnation(destination); check(engine.voices()==0 && engine.sources()==0,"destination incarnation removal closes source immediately: "+id);
            engine.clear(); engine.tick(0,Vec3.ZERO);
            var shared=new AudioCatalog.Admission("fixture-shared",1,1,0);
            var playback=profile.body().playback();
            var bodyCue=new AudioCatalog.Cue(profile.body().id(),new AudioCatalog.Playback(playback.category(),playback.gain(),playback.pitch(),playback.spatial(),0),profile.body().audience(),shared,profile.body().subtitle());
            var accent=new AudioCatalog.Cue(profile.body().id(),new AudioCatalog.Playback(playback.category(),playback.gain(),playback.pitch(),playback.spatial(),1),profile.body().audience(),shared,profile.body().subtitle());
            var changed=new AudioCatalog.Profile(bodyCue,new AudioCatalog.Lifecycle(0,0,47));
            engine.reconcile(key,changed,context,true,1,()->true); engine.tick(1,Vec3.ZERO);
            check(engine.oneShot(accent,new AudioEngine.Context("priority accent",Vec3.ZERO,1)),"shared priority accent displaces body: "+id);
            ((AudioEngine.Voice)sink.last).close(); engine.tick(2,Vec3.ZERO); engine.tick(40,Vec3.ZERO);
            check(engine.voices()==0,"evicted profile obeys changed restart policy rather than a fixed cooldown: "+id);
            engine.tick(60,Vec3.ZERO); check(engine.voices()==1,"evicted profile recovers after its policy backoff: "+id);
            engine.clear(); engine.tick(0,Vec3.ZERO);
            var activity=new java.util.concurrent.atomic.AtomicBoolean(true);
            var observed=new AudioCatalog.Profile(profile.body(),new AudioCatalog.Lifecycle(2,4,31));
            engine.reconcile(key,observed,context,true,1,()->true,activity::get); engine.tick(1,Vec3.ZERO);
            int starts=sink.plays; activity.set(false); engine.tick(2,Vec3.ZERO);
            check(engine.voices()==1,"ordinary cessation uses configured release instead of invalid-owner removal: "+id);
            activity.set(true); engine.tick(3,Vec3.ZERO);
            check(sink.plays==starts,"renewed transfer during release retains its body: "+id);
            activity.set(false); for(int tick=4;tick<=9;tick++) engine.tick(tick,Vec3.ZERO);
            check(engine.sources()==0 && engine.voices()==0,"zero-transfer observation eventually releases source: "+id);
            activity.set(true); engine.reconcile(key,observed,context,true,2,()->true,activity::get); engine.tick(10,Vec3.ZERO);
            var alternate=new AudioCatalog.Profile(catalog.profile(id.equals("machine.resonance")?"mobility.thrust":"machine.resonance").body(),observed.lifecycle());
            engine.reconcile(key,alternate,context,true,3,()->true,activity::get); engine.tick(11,Vec3.ZERO); engine.invalidate();
            engine.tick(12,Vec3.ZERO); engine.tick(40,Vec3.ZERO);
            check(engine.voices()==0,"reload during profile transition retains policy backoff: "+id);
            engine.tick(60,Vec3.ZERO); check(engine.voices()==1 && sink.lastCue==alternate.body(),"reload resumes newest desired profile only: "+id);
            engine.reconcile(key,alternate,context,false,4,()->true,activity::get);
            for(int tick=61;tick<=66;tick++) engine.tick(tick,Vec3.ZERO);
            check(engine.voices()==0 && engine.sources()==0,"explicit stop cannot be undone by a previously positive activity supplier: "+id);
        }
        // Real destination/pylon changes reuse the same controller and cannot accumulate tails/voices.
        for(boolean player:List.of(false,true)) {
            var sink=new AudioRuntimeTest.Recording(); var engine=new AudioEngine(sink); engine.tick(0,Vec3.ZERO);
            Object source=new Object(),first=new Object(),second=new Object();
            var firstKey=new AudioEngine.Source(source,"transfer",first); var secondKey=new AudioEngine.Source(source,"transfer",second);
            long now=0;
            for(int pylons=0;pylons<=8;pylons++) {
                var profile=catalog.profile(AudioSources.beamProfile(player,pylons));
                for(var key:List.of(firstKey,secondKey)) engine.reconcile(key,profile,new AudioEngine.Context(key,Vec3.ZERO,0),true,pylons+1,()->true);
                for(int step=0;step<profile.lifecycle().releaseTicks()+profile.lifecycle().attackTicks()+3;step++) {
                    engine.tick(++now,Vec3.ZERO);
                    check(engine.voices()<=2,"shared profile release/attack never duplicates a destination voice");
                }
                check(engine.sources()==2 && engine.voices()==2,"two concurrent destinations each keep one pylon-selected body: "+player+"/"+pylons);
            }
            engine.removeIncarnation(first); check(engine.sources()==1 && engine.voices()==1,"one destination removal preserves other player/machine");
            engine.removeIncarnation(source); check(engine.sources()==0 && engine.voices()==0,"physical source removal clears all attached destinations");
        }
        System.out.println("Audio catalog behavior: "+checks+" assertions PASS (parameterized recording-sink policies; no live multiplayer/listening claim)");
    }
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
}
