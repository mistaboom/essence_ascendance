package com.mistaboom.essence_ascendance.audio;

import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only content additions exercise the actual catalog and controller without another feature manager. */
final class AudioExtensionTest {
    private static int checks;
    static void run() throws Exception {
        AudioCatalog extended;
        try(var input=AudioExtensionTest.class.getResourceAsStream("/assets/essence_ascendance/audio/catalog.json")) {
            var data=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8)).getAsJsonObject();
            var original=data.getAsJsonObject("recipes").getAsJsonObject("interface.acknowledge");
            String id="essence_ascendance:interface.fixture.confirm";
            data.getAsJsonObject("cues").add(id,data.getAsJsonObject("cues").getAsJsonObject(original.get("body").getAsString()).deepCopy());
            var recipe=original.deepCopy(); recipe.addProperty("body",id);
            data.getAsJsonObject("recipes").add("interface.fixture.confirm",recipe);
            var lifecycle=JsonParser.parseString("{\"attackTicks\":0,\"releaseTicks\":1,\"restartBackoffTicks\":1}").getAsJsonObject();
            data.getAsJsonObject("policies").getAsJsonObject("lifecycle").add("fixture_brief",lifecycle);
            var profile=data.getAsJsonObject("sourceProfiles").getAsJsonObject("machine.resonance").deepCopy();
            profile.addProperty("lifecycle","fixture_brief");
            data.getAsJsonObject("sourceProfiles").add("fixture.marker",profile);
            extended=new AudioCatalog(data);
        }
        var cue=extended.recipe("interface.fixture.confirm");
        var family=extended.recipe("interface.acknowledge");
        check(cue!=null && cue!=family && cue.playback()==family.playback() && cue.admission()==family.admission()
                && cue.audience()==family.audience(),"additional interface cue reuses resolved family policies without copied tuning");
        check(extended.recipe("essence_ascendance:interface.fixture.confirm")==cue,"new semantic automatically supports the shared wire name");
        var sink=new AudioRuntimeTest.Recording(); var engine=new AudioEngine(sink); engine.tick(0,Vec3.ZERO);
        Object control=new Object(); var first=new AudioEngine.Context(control,Vec3.ZERO,1);
        check(engine.oneShot(cue,first) && sink.lastCue==cue,"new cue uses the existing one-shot path");
        check(!engine.oneShot(cue,first) && sink.captions==1,"new cue automatically deduplicates both modalities");
        check(!engine.oneShot(cue,new AudioEngine.Context(control,Vec3.ZERO,2)),"new cue automatically obeys the existing family repetition policy");
        engine.tick(cue.admission().repeatTicks(),Vec3.ZERO); sink.gain=0;
        check(engine.oneShot(cue,new AudioEngine.Context(control,Vec3.ZERO,3)) && sink.plays==1 && sink.captions==2,
                "new cue inherits mute-independent captions without a native dummy voice");

        // A tracked marker is a second kind of owner beside a machine. It supplies facts, never owns handles.
        engine.clear(); engine.tick(0,Vec3.ZERO); sink.gain=1;
        Object machine=new Object(),marker=new Object();
        var markerKey=new AudioEngine.Source(marker,"marker");
        var location=new AtomicReference<>(new Vec3(1,0,0)); var active=new AtomicBoolean(true); var valid=new AtomicBoolean(true);
        var standard=extended.profile("machine.resonance"); var brief=extended.profile("fixture.marker");
        engine.reconcile(machine,standard,new AudioEngine.Context(machine,Vec3.ZERO,0),true,1,()->true);
        engine.reconcile(markerKey,standard,new AudioEngine.Context(markerKey,location.get(),0,location::get),true,1,valid::get,active::get);
        engine.tick(1,Vec3.ZERO);
        check(engine.sources()==2 && engine.voices()==2,"another tracked consumer shares one controller and family admission");
        int before=sink.plays;
        location.set(new Vec3(2,0,0)); engine.tick(2,Vec3.ZERO);
        check(sink.lastPosition().equals(location.get()) && sink.plays==before,"tracked consumer moves with no snapshot, resource lookup or restart");
        active.set(false); engine.tick(3,Vec3.ZERO); active.set(true); engine.tick(4,Vec3.ZERO);
        check(sink.plays==before && engine.voices()==2,"tracked activity resumes within the common release without another reconcile");
        engine.reconcile(markerKey,brief,new AudioEngine.Context(markerKey,location.get(),0,location::get),true,2,valid::get,active::get);
        long now=4;
        for(int i=0;i<standard.lifecycle().releaseTicks()+4;i++) {
            engine.tick(++now,Vec3.ZERO);
            check(engine.voices()<=2,"same sample with another lifecycle never creates parallel bodies");
        }
        check(sink.plays==before+1 && engine.voices()==2,"changing a profile lifecycle is not mistaken for an unchanged sample");
        engine.reconcile(markerKey,brief,new AudioEngine.Context(markerKey,location.get(),0),false,3,valid::get);
        engine.tick(++now,Vec3.ZERO); engine.tick(++now,Vec3.ZERO);
        check(engine.sources()==1 && engine.voices()==1,"replacement obeys its one-tick release rather than retaining the former lifecycle");
        engine.reconcile(markerKey,brief,new AudioEngine.Context(markerKey,location.get(),0),true,4,valid::get,active::get);
        engine.tick(++now,Vec3.ZERO); engine.invalidate();
        for(int i=0;i<standard.lifecycle().restartBackoffTicks()+12;i++) engine.tick(++now,Vec3.ZERO);
        check(engine.voices()==2,"both source types recover body-only through the same reload path");
        valid.set(false); engine.tick(++now,Vec3.ZERO);
        check(engine.sources()==1 && engine.voices()==1,"new consumer invalidation removes only its own body");
        engine.remove(machine); check(engine.sources()==0 && engine.voices()==0,"existing removal API completes cleanup without a second manager");
        System.out.println("Audio extension: "+checks+" assertions PASS (test-only cue and tracked marker; no production content changes)");
    }
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
}
