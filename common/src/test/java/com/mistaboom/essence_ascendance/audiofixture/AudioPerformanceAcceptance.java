package com.mistaboom.essence_ascendance.audiofixture;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.audio.AudioCatalog;
import com.mistaboom.essence_ascendance.audio.AudioEngine;
import net.minecraft.world.phys.Vec3;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Controller microbenchmark only: synthetic sources/counting sink, not a dense gameplay or mixer acceptance. */
public final class AudioPerformanceAcceptance {
    private static final int TICKS=200,CANDIDATES=1000;
    private AudioPerformanceAcceptance() { }
    public static void run() {
        var report=new JsonObject();
        report.addProperty("scope","controller microbenchmark; synthetic population and counting sink; excludes native mixer, device, rendering, networking and paid machine gameplay");
        report.addProperty("ticksPerCase",TICKS);
        report.add("idle",measure(false));
        report.add("denseSynthetic",measure(true));
        System.out.println("AUDIO_CONTROLLER_MICROBENCHMARK "+report);
    }
    private static JsonObject measure(boolean dense) {
        var bean=ManagementFactory.getThreadMXBean();
        var allocation=bean instanceof com.sun.management.ThreadMXBean nativeBean
                && nativeBean.isThreadAllocatedMemorySupported() && nativeBean.isThreadAllocatedMemoryEnabled()?nativeBean:null;
        boolean cpu=bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled();
        long thread=Thread.currentThread().threadId();
        var sink=new CountingSink(); var engine=new AudioEngine(sink);
        var profile=AudioCatalog.get().profile("machine.resonance");
        var contexts=new AudioEngine.Context[dense?CANDIDATES:0];
        // Preparation is outside the measured window; the candidates stand in for already-tracked owners.
        for(int i=0;i<contexts.length;i++) {
            var key=new AudioEngine.Source(new Object(),"fixture");
            contexts[i]=new AudioEngine.Context(key,new Vec3(i%16,0,0),0);
            engine.reconcile(key,profile,contexts[i],true,1,()->true);
        }
        engine.tick(0,Vec3.ZERO);
        long[] elapsed=new long[TICKS]; long cpuNanos=0,allocatedBytes=0;
        int maxSources=engine.sources(),maxVoices=engine.voices(),candidateCursor=0;
        for(int tick=1;tick<=TICKS;tick++) {
            long cpuBefore=cpu?bean.getCurrentThreadCpuTime():0;
            long allocatedBefore=allocation==null?0:allocation.getThreadAllocatedBytes(thread);
            long before=System.nanoTime();
            if(dense) for(int visit=0;visit<32;visit++) {
                var context=contexts[candidateCursor++%contexts.length];
                if(!engine.containsSource(context.source())) engine.reconcile(context.source(),profile,context,true,1,()->true);
            }
            engine.tick(tick,Vec3.ZERO);
            elapsed[tick-1]=System.nanoTime()-before;
            if(cpu) cpuNanos+=bean.getCurrentThreadCpuTime()-cpuBefore;
            if(allocation!=null) allocatedBytes+=allocation.getThreadAllocatedBytes(thread)-allocatedBefore;
            maxSources=Math.max(maxSources,engine.sources()); maxVoices=Math.max(maxVoices,engine.voices());
            if(engine.sources()>AudioEngine.SOURCE_LIMIT || engine.voices()>AudioEngine.LOOP_LIMIT
                    || engine.voices()>profile.body().admission().perGroup()) throw new AssertionError("Controller bounds exceeded");
        }
        engine.clear();
        if(engine.sources()!=0 || engine.voices()!=0 || sink.open!=0) throw new AssertionError("Controller benchmark leaked ownership");
        Arrays.sort(elapsed);
        var result=new JsonObject(); result.addProperty("candidatePopulation",contexts.length);
        result.addProperty("maxCandidatesVisitedPerTick",dense?32:0);
        result.addProperty("maxSources",maxSources); result.addProperty("maxVoices",maxVoices);
        result.addProperty("starts",sink.starts); result.addProperty("captionCallbacks",sink.captions);
        result.addProperty("elapsedP50Nanos",elapsed[TICKS/2-1]); result.addProperty("elapsedP95Nanos",elapsed[TICKS*95/100-1]);
        result.addProperty("elapsedMaxNanos",elapsed[TICKS-1]);
        result.addProperty("currentThreadCpuSupported",cpu); result.addProperty("currentThreadCpuNanos",cpu?cpuNanos:-1);
        result.addProperty("currentThreadAllocationSupported",allocation!=null); result.addProperty("currentThreadAllocatedBytes",allocation==null?-1:allocatedBytes);
        result.addProperty("clearedSources",engine.sources()); result.addProperty("clearedVoices",engine.voices());
        return result;
    }
    private static final class CountingSink implements AudioEngine.Sink {
        int starts,open,captions;
        public float gain(AudioCatalog.Cue cue) { return 1; }
        public void caption(AudioCatalog.Cue cue,AudioEngine.Context context,long tick) { captions++; }
        public AudioEngine.Voice play(AudioCatalog.Cue cue,AudioEngine.Context context,boolean loop,float gain) {
            starts++; open++;
            return new AudioEngine.Voice() {
                boolean alive=true;
                public boolean alive() { return alive; }
                public void update(Vec3 position,float gain,float pitch) { }
                public void close() { if(alive) { alive=false; open--; } }
            };
        }
    }
}
