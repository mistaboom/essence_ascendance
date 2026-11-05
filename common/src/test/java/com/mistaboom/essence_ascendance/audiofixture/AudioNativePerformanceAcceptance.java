package com.mistaboom.essence_ascendance.audiofixture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.audio.AudioEngine;
import com.mistaboom.essence_ascendance.client.audio.NativeAudio;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Observes the unchanged packaged mod in a real socket world. Never creates audio state or a fake sink. */
public final class AudioNativePerformanceAcceptance {
    public static final int SAMPLE_TICKS=400;
    private static final int CAPACITY=2400;
    private static final java.lang.management.ThreadMXBean THREADS=ManagementFactory.getThreadMXBean();
    private static final com.sun.management.ThreadMXBean ALLOC=THREADS instanceof com.sun.management.ThreadMXBean b?b:null;
    private static final long[] tickNanos=new long[CAPACITY],tickCpu=new long[CAPACITY],tickBytes=new long[CAPACITY];
    private static final long[] machineNanos=new long[CAPACITY],entityNanos=new long[CAPACITY];
    private static final IdentityHashMap<BlockEntity,Integer> population=new IdentityHashMap<>();
    private static final Set<Object> decodeFutures=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Object> decodedBuffers=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String,Long> decodePaths=new TreeMap<>();
    private static long decodeFailures,decodeLatencyNanos;
    private static final ThreadLocal<DecodeWork> decodeWork=new ThreadLocal<>();
    private static final Map<String,long[]> decodeThreads=new TreeMap<>();
    private static long decodeWorkCount,decodeWorkNanos,decodeWorkCpu,decodeWorkHeap;
    private static final class DecodeWork { long started,cpu,heap; String thread; }
    private static AudioEngine engine;
    private static boolean measuring,insideDiscovery;
    private static String phase;
    private static int samples,ticks,frames,expectedConsumers,loadedPopulation,actualLoadedEa,sourceMin,sourceMax,voiceMin,voiceMax;
    private static int expectedNativeBodies;
    private static int nativeMin,nativeMax,nativeSamples,nativeHandlesMax,retiredNativeProbes,discoveryOffers,machineCalls;
    private static boolean[] offered,eaPopulation;
    private static long tickStart,cpuStart,bytesStart,machineStart,entityStart,started;
    private static SystemSample systemBefore;
    private static Map<String,Long> trafficBefore;
    private static JsonObject decodeBefore;
    private static JsonObject settings;
    private static JsonObject loadedClasses;
    private static volatile NativeSample nativeSample=new NativeSample(0,0,"",List.of());
    private static String deviceAtStart;
    private static String lastMeasuredDevice;
    private static int deviceChanges;
    private static final Set<String> measuredDevices=new LinkedHashSet<>();
    private static long lastNativeSample;
    private static long nativeProbeRequested;
    private static boolean gameplayProbeBoundaryVerified;
    private static final Set<SoundInstance> playingIdentities=Collections.newSetFromMap(new IdentityHashMap<>());

    private AudioNativePerformanceAcceptance() { }

    /** Call after settled real machine setup. Expected consumers describes gameplay, not forced audio sources. */
    public static void begin(String id,int consumers,int expectedLoaded) {
        require(!measuring,"previous native measurement still active");
        require(Network.enabled(),"native observation property enabled");
        require(engine!=null,"production AudioRuntime observer has executed");
        require(THREADS.isThreadCpuTimeSupported(),"Java thread CPU measurements supported");
        if(!THREADS.isThreadCpuTimeEnabled()) THREADS.setThreadCpuTimeEnabled(true);
        require(ALLOC!=null && ALLOC.isThreadAllocatedMemorySupported(),"Java heap allocation measurements supported");
        if(!ALLOC.isThreadAllocatedMemoryEnabled()) ALLOC.setThreadAllocatedMemoryEnabled(true);
        phase=id; expectedConsumers=consumers; loadedPopulation=expectedLoaded;
        var profile=com.mistaboom.essence_ascendance.audio.AudioCatalog.get().profile("machine.resonance.p8");
        expectedNativeBodies=Math.min(consumers,Math.min(AudioEngine.LOOP_LIMIT,profile.body().admission().perGroup()));
        population.clear();
        var mc=Minecraft.getInstance();
        require(mc.level!=null && mc.player!=null,"native world and player loaded");
        int x=mc.player.blockPosition().getX()>>4,z=mc.player.blockPosition().getZ()>>4;
        actualLoadedEa=0;
        var classCounts=new TreeMap<String,Integer>();
        for(int dz=-2;dz<=2;dz++) for(int dx=-2;dx<=2;dx++) {
            var chunk=mc.level.getChunkSource().getChunk(x+dx,z+dz,ChunkStatus.FULL,false);
            if(chunk!=null) for(BlockEntity block:chunk.getBlockEntities().values()) {
                population.put(block,population.size());
                classCounts.merge(block.getClass().getName(),1,Integer::sum);
                if(block.getClass().getName().startsWith("com.mistaboom.essence_ascendance.")) actualLoadedEa++;
            }
        }
        require(actualLoadedEa>=expectedLoaded,"real loaded EA block entities="+actualLoadedEa+", expected >="+expectedLoaded);
        offered=new boolean[population.size()];
        eaPopulation=new boolean[population.size()];
        population.forEach((block,index)->eaPopulation[index]=block.getClass().getName().startsWith("com.mistaboom.essence_ascendance."));
        loadedClasses=new JsonObject(); classCounts.forEach(loadedClasses::addProperty);
        samples=ticks=frames=nativeSamples=retiredNativeProbes=discoveryOffers=machineCalls=0;
        sourceMin=voiceMin=nativeMin=Integer.MAX_VALUE; sourceMax=voiceMax=nativeMax=nativeHandlesMax=0;
        playingIdentities.clear();
        deviceAtStart=nativeSample.device(); lastMeasuredDevice=deviceAtStart; deviceChanges=0;
        measuredDevices.clear(); measuredDevices.add(deviceAtStart);
        require(!deviceAtStart.isBlank() && System.nanoTime()-nativeSample.capturedNanos()<3_000_000_000L,"fresh actual OpenAL device observation before measurement");
        String expectedDevice=System.getProperty("essence.audio.expectedSoundDevice","");
        require(expectedDevice.isBlank() || expectedDevice.equals(deviceAtStart),"actual native device matches explicitly configured device: actual="+deviceAtStart+", expected="+expectedDevice);
        Arrays.fill(machineNanos,0); Arrays.fill(entityNanos,0);
        settings=new JsonObject();
        settings.addProperty("hiddenWindowRequested",Boolean.getBoolean("essence.audio.hiddenWindow"));
        //? if >=26.1.2
        /*long window=mc.getWindow().handle();*/
        //? if <26.1.2
        long window=mc.getWindow().getWindow();
        settings.addProperty("actualGlfwVisible",org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_VISIBLE));
        settings.addProperty("actualGlfwFocused",org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_FOCUSED));
        settings.addProperty("renderDistance",mc.options.renderDistance().get()); settings.addProperty("maximumFps",mc.options.framerateLimit().get());
        settings.addProperty("pauseOnLostFocus",mc.options.pauseOnLostFocus); settings.addProperty("actuallyPaused",mc.isPaused());
        settings.addProperty("nativeMasterVolume",mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER));
        settings.addProperty("nativeBlocksVolume",mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.BLOCKS));
        settings.addProperty("nativePlayersVolume",mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.PLAYERS));
        settings.addProperty("nativeSubtitles",mc.options.showSubtitles().get());
        settings.addProperty("selectedSoundDeviceOption",mc.options.soundDevice().get());
        settings.addProperty("expectedSoundDevice",expectedDevice);
        settings.addProperty("actualSoundDevice",deviceAtStart);
        settings.addProperty("audioSettingsOwner","Minecraft");
        settings.addProperty("nativeTrackedPlayers",mc.level.players().size());
        settings.addProperty("processId",ProcessHandle.current().pid());
        settings.addProperty("logicalProcessors",Runtime.getRuntime().availableProcessors());
        //? if >=26.1.2
        /*settings.addProperty("inactiveFpsPolicy",mc.options.inactivityFpsLimit().get().toString());*/
        //? if <26.1.2
        settings.addProperty("inactiveFpsPolicy","native 1.21.1 window-inactivity behavior");
        settings.addProperty("foregroundGraphicsAcceptance",false);
        trafficBefore=Network.snapshot(); decodeBefore=decodeEvidence(); systemBefore=SystemSample.capture(); started=System.nanoTime();
        measuring=true;
        System.out.println("AUDIO_NATIVE_PERFORMANCE_BEGIN "+id+" consumers="+consumers+" actualLoadedNearbyBlockEntities="+population.size()+" actualLoadedEaBlockEntities="+actualLoadedEa);
    }

    /** Called by the real client tick coordinator; wall-time minimum prevents accelerated ticks shortening capture. */
    public static void clientTick() {
        if(!measuring) return;
        ticks++;
        sourceMin=Math.min(sourceMin,engine.sources()); sourceMax=Math.max(sourceMax,engine.sources());
        voiceMin=Math.min(voiceMin,engine.voices()); voiceMax=Math.max(voiceMax,engine.voices());
        require(engine.sources()<=AudioEngine.SOURCE_LIMIT,"real production source bound");
        require(engine.voices()<=AudioEngine.LOOP_LIMIT,"real production continuous voice bound");
        var current=nativeSample;
        if(current.capturedNanos()>started && current.capturedNanos()!=lastNativeSample) {
            // Published only after the executor FIFO barrier; null/retired handles are explicit, never fabricated plays.
            if(current.voices().stream().allMatch(v->v.sampled() || v.retiredBeforeProbe())) {
                lastNativeSample=current.capturedNanos();
                nativeHandlesMax=Math.max(nativeHandlesMax,current.totalNativeHandles());
                measuredDevices.add(current.device());
                if(!current.device().equals(lastMeasuredDevice)) { deviceChanges++; lastMeasuredDevice=current.device(); }
                int playing=0;
                for(var voice:current.voices()) {
                    if(voice.retiredBeforeProbe()) retiredNativeProbes++;
                    if(voice.playing()) { playing++; playingIdentities.add(voice.instance()); }
                }
                nativeMin=Math.min(nativeMin,playing); nativeMax=Math.max(nativeMax,playing); nativeSamples++;
            }
        }
    }
    public static boolean ready() { return measuring && ticks>=SAMPLE_TICKS && System.nanoTime()-started>=20_000_000_000L; }
    /** Shared existing caption-frame observer calls this; it never causes a render. */
    public static void frame() { if(measuring) frames++; }
    public static JsonObject finish() {
        require(ready(),"native measurement has >=400 real client ticks and >=20 seconds");
        measuring=false;
        var after=SystemSample.capture(); long duration=System.nanoTime()-started;
        require(samples>=SAMPLE_TICKS && samples<CAPACITY,"bounded complete AudioRuntime tick samples="+samples);
        require(nativeSamples>=10,"actual native channel observations="+nativeSamples);
        require(measuredDevices.size()==1,"actual native output device remained unchanged during measurement: "+measuredDevices);
        if(expectedConsumers>0) {
            require(sourceMax>0 && voiceMax>0 && nativeMax>0,"real paid source owns an actually playing native channel");
            synchronized(decodeFutures) { require(!decodedBuffers.isEmpty() && decodeWorkCount>0,"EA decoded buffers and actual decoder-worker execution observed"); }
            require(sourceMin==expectedConsumers && sourceMax==expectedConsumers,"fixed paid scene retains every real source throughout the settled window");
            require(voiceMin==expectedNativeBodies && voiceMax==expectedNativeBodies && nativeMin==expectedNativeBodies && nativeMax==expectedNativeBodies,
                    "fixed scene retains its policy-admitted native bodies throughout the window: expected="+expectedNativeBodies);
            require(playingIdentities.size()==expectedNativeBodies && retiredNativeProbes==0,
                    "fixed scene reuses the same actual native body identities with no restart/retired probe: identities="+playingIdentities.size()+", retired="+retiredNativeProbes);
        } else require(sourceMax==0 && voiceMax==0 && nativeMax==0,"settled idle/stop owns no source, voice or playing native channel");
        var report=new JsonObject(); report.addProperty("phase",phase);
        report.addProperty("observerMetricsRevision",Network.METRICS_REVISION);
        report.addProperty("scope","actual packaged client, real loaded/paid native sources; no injected snapshots or synthetic sink");
        report.addProperty("wallNanos",duration); report.addProperty("clientTicks",ticks); report.addProperty("audioRuntimeSamples",samples);
        report.addProperty("actualCaptionFrames",frames); report.addProperty("actualClientTicksPerSecond",ticks*1_000_000_000.0/duration);
        report.addProperty("actualCaptionFramesPerSecond",frames*1_000_000_000.0/duration); report.add("settings",settings);
        report.addProperty("configuredPaidConsumers",expectedConsumers); report.addProperty("expectedLoadedEaBlockEntities",loadedPopulation);
        report.addProperty("fixedSceneExpectedNativeBodies",expectedNativeBodies);
        report.addProperty("fixedSceneNativeContinuityVerified",expectedConsumers>0);
        report.addProperty("actualLoadedEaBlockEntities",actualLoadedEa);
        report.addProperty("actualLoadedNearbyBlockEntities",population.size());
        report.add("actualLoadedBlockEntityClasses",loadedClasses);
        int distinct=0,distinctEa=0; for(int i=0;i<offered.length;i++) if(offered[i]) { distinct++; if(eaPopulation[i]) distinctEa++; }
        require(distinctEa>=loadedPopulation,"real bounded discovery offered actual EA population="+distinctEa+", expected >="+loadedPopulation);
        report.addProperty("distinctLoadedBlockEntitiesOfferedByDiscovery",distinct); report.addProperty("discoveryOffers",discoveryOffers);
        report.addProperty("distinctLoadedEaBlockEntitiesOfferedByDiscovery",distinctEa);
        report.addProperty("machineChangedCallsIncludingSnapshotNotifications",machineCalls);
        report.addProperty("sourcesMin",sourceMin); report.addProperty("sourcesMax",sourceMax);
        report.addProperty("voicesMin",voiceMin); report.addProperty("voicesMax",voiceMax);
        report.addProperty("actualNativePlayingMin",nativeMin); report.addProperty("actualNativePlayingMax",nativeMax);
        report.addProperty("actualNativeSamples",nativeSamples); report.addProperty("distinctActuallyPlayingSoundInstances",playingIdentities.size());
        report.addProperty("allNativeSoundHandlesMax",nativeHandlesMax);
        report.addProperty("nativeHandlesRetiredBeforeProbe",retiredNativeProbes);
        var devices=new JsonArray(); measuredDevices.forEach(devices::add); report.add("actualNativeDevicesDuringWindow",devices);
        report.addProperty("nativeDeviceStableDuringWindow",measuredDevices.size()==1);
        report.addProperty("soundDeviceChangesInWindow",deviceChanges);
        report.addProperty("sourceLimit",AudioEngine.SOURCE_LIMIT); report.addProperty("loopLimit",AudioEngine.LOOP_LIMIT);
        report.add("audioRuntimeWall",distribution(tickNanos,samples)); report.add("audioRuntimeCpu",distribution(tickCpu,samples));
        report.add("audioRuntimeAllocatedBytes",distribution(tickBytes,samples));
        report.add("machineDiscoveryWall",distribution(machineNanos,samples)); report.add("entityDiscoveryWall",distribution(entityNanos,samples));
        report.addProperty("runtimeP95InvestigationThresholdNanos",5_000_000L);
        report.addProperty("runtimeP95WithinInvestigationThreshold",percentile(tickNanos,samples,95)<=5_000_000L);
        report.add("processAndJavaThreads",systemBefore.delta(after));
        report.add("network",Network.delta(trafficBefore));
        report.addProperty("networkPacketScope",Network.COUNT_SCOPE);
        var decodeAfter=decodeEvidence(); report.add("nativeDecodeCumulativeIncludingSetup",decodeAfter);
        var decodeDelta=new JsonObject();
        for(String key:List.of("distinctObservedDecodeFutures","distinctCompletedNativeBuffers","failures","completionLatencyTotalNanos","decoderWorkerCompletions","decoderWorkerWallNanos","decoderWorkerCpuNanos","decoderWorkerAllocatedJavaHeapBytes"))
            decodeDelta.addProperty(key,decodeAfter.get(key).getAsLong()-decodeBefore.get(key).getAsLong());
        decodeDelta.addProperty("scope","new completion observations during this settled window only; setup/cold decode is reported separately in cumulative totals, not counted as steady-window decoding CPU");
        report.add("nativeDecodeDuringMeasuredWindow",decodeDelta);
        report.addProperty("measurementOverhead","two nanoTime reads per timed region; current-thread CPU/allocation reads per runtime tick; 1 Hz native channel queries on the real sound executor; two process/thread snapshots outside tick timing; Netty LongAdder counters");
        report.addProperty("limits","process CPU includes native OpenAL threads and all other client work; Java thread CPU/allocation cannot isolate native mixer CPU or native allocations. Decode future latency includes queue/wait and cached completion. Serialized socket bytes exclude TCP/IP headers. Same-host two-client CPU is not a broad hardware benchmark.");
        System.out.println("AUDIO_NATIVE_PERFORMANCE "+report);
        return report;
    }

    public static void runtimeBegin(AudioEngine actual) {
        engine=actual;
        if(!gameplayProbeBoundaryVerified) verifyGameplayProbeBoundary();
        if(!measuring) return;
        require(samples<CAPACITY,"native measurement sample buffer is bounded");
        cpuStart=THREADS.getCurrentThreadCpuTime(); bytesStart=ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId());
        tickStart=System.nanoTime();
    }
    public static void runtimeEnd() {
        if(!measuring) return;
        tickNanos[samples]=System.nanoTime()-tickStart;
        tickBytes[samples]=ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId())-bytesStart;
        tickCpu[samples]=THREADS.getCurrentThreadCpuTime()-cpuStart; samples++;
    }
    public static void discoveryBegin(boolean machines) {
        if(!measuring) return;
        if(machines) { insideDiscovery=true; machineStart=System.nanoTime(); } else entityStart=System.nanoTime();
    }
    public static void discoveryEnd(boolean machines) {
        if(!measuring) return;
        if(machines) { machineNanos[samples]+=System.nanoTime()-machineStart; insideDiscovery=false; }
        else entityNanos[samples]+=System.nanoTime()-entityStart;
    }
    public static void machineOffered(BlockEntity block) {
        if(!measuring) return;
        machineCalls++;
        if(insideDiscovery) { discoveryOffers++; var i=population.get(block); if(i!=null) offered[i]=true; }
    }

    public static final class NativeVoice {
        private final SoundInstance instance;
        private final String cue;
        private volatile boolean sampled,playing,stopped,retiredBeforeProbe;
        private volatile long observedNanos;
        private NativeVoice(SoundInstance instance) { this.instance=instance; cue=soundId(instance); }
        public SoundInstance instance() { return instance; }
        public String cue() { return cue; }
        public boolean sampled() { return sampled; }
        public boolean playing() { return playing; }
        public boolean stopped() { return stopped; }
        public boolean retiredBeforeProbe() { return retiredBeforeProbe; }
        public long observedNanos() { return observedNanos; }
    }
    public record NativeSample(long capturedNanos,int totalNativeHandles,String device,List<NativeVoice> voices) { }
    private static String soundId(SoundInstance sound) {
        //? if >=26.1.2
        /*return sound.getIdentifier().toString();*/
        //? if <26.1.2
        return sound.getLocation().toString();
    }
    public static NativeSample latestNative() { return nativeSample; }
    public static int nativePlayingCount() { int count=0; for(var voice:nativeSample.voices()) if(voice.sampled && voice.playing) count++; return count; }
    /** Reuse the next real sound-engine tick for brief gameplay activity; measured windows retain the fixed 1 Hz cadence. */
    public static void requestGameplayNativeProbe() {
        require(!measuring,"gameplay probe cannot change measured native observation cadence");
        nativeProbeRequested=0;
    }
    private static void verifyGameplayProbeBoundary() {
        boolean priorMeasuring=measuring; long priorRequest=nativeProbeRequested;
        require(!priorMeasuring,"gameplay probe boundary verification precedes actual measurement");
        try {
            nativeProbeRequested=123;
            requestGameplayNativeProbe();
            require(nativeProbeRequested==0,"gameplay request schedules the existing next native tick probe");
            measuring=true; nativeProbeRequested=456;
            boolean rejected=false;
            try { requestGameplayNativeProbe(); } catch(AssertionError expected) { rejected=true; }
            require(rejected,"measured-window gameplay probe request is rejected");
            require(nativeProbeRequested==456,"rejected request preserves measured probe scheduling");
        } finally { measuring=priorMeasuring; nativeProbeRequested=priorRequest; }
        gameplayProbeBoundaryVerified=true;
        System.out.println("AudioNativePerformanceAcceptance: PASS; 3 gameplay-probe boundary checks (schedule existing probe, reject measured-window request, preserve rejected schedule); original observer state restored");
    }
    public static void observeNativeChannels(Map<SoundInstance,ChannelAccess.ChannelHandle> channels,java.util.concurrent.Executor executor,com.mojang.blaze3d.audio.Library library) {
        if(!Network.enabled()) return;
        long now=System.nanoTime();
        if(now-nativeProbeRequested<1_000_000_000L) return;
        nativeProbeRequested=now;
        List<NativeVoice> voices=new ArrayList<>();
        for(var entry:channels.entrySet()) if(entry.getKey() instanceof NativeAudio.Instance) {
            var voice=new NativeVoice(entry.getKey()); voices.add(voice);
            entry.getValue().execute(channel->{ voice.playing=channel.playing(); voice.stopped=channel.stopped(); voice.observedNanos=System.nanoTime(); voice.sampled=true; });
        }
        //? if >=26.1.2
        /*String device=library.currentDeviceName();*/
        //? if <26.1.2
        String device=library.getCurrentDeviceName();
        var result=new NativeSample(now,channels.size(),device==null?"":device,List.copyOf(voices));
        // Same FIFO executor as handle.execute. A skipped consumer means its channel was retired, not unknown.
        executor.execute(()->{
            for(var voice:result.voices()) if(!voice.sampled) { voice.retiredBeforeProbe=true; voice.stopped=true; }
            nativeSample=result;
        });
    }
    public static void decoded(String path,CompletableFuture<?> future) {
        if(!Network.enabled() || !path.startsWith("essence_ascendance:")) return;
        synchronized(decodeFutures) {
            if(decodeFutures.size()>=256 || !decodeFutures.add(future)) return;
        }
        long request=System.nanoTime();
        future.whenComplete((buffer,failure)->{
            synchronized(decodeFutures) {
                if(failure!=null || buffer==null) decodeFailures++;
                else { decodedBuffers.add(buffer); decodePaths.merge(path,1L,Long::sum); decodeLatencyNanos+=System.nanoTime()-request; }
            }
        });
    }
    /** HEAD/RETURN of Minecraft's actual resource-read/Vorbis-decode worker, not the scheduling call. */
    public static void decodeWorkerBegin(String path) {
        decodeWork.remove();
        if(!Network.enabled() || !path.startsWith("essence_ascendance:")) return;
        require(THREADS.isThreadCpuTimeSupported() && ALLOC!=null && ALLOC.isThreadAllocatedMemorySupported(),"decoder worker CPU/heap observation supported");
        if(!THREADS.isThreadCpuTimeEnabled()) THREADS.setThreadCpuTimeEnabled(true);
        if(!ALLOC.isThreadAllocatedMemoryEnabled()) ALLOC.setThreadAllocatedMemoryEnabled(true);
        var thread=Thread.currentThread();
        var work=new DecodeWork(); work.thread=thread.getName(); decodeWork.set(work);
        work.heap=ALLOC.getThreadAllocatedBytes(thread.threadId()); work.cpu=THREADS.getCurrentThreadCpuTime(); work.started=System.nanoTime();
    }
    public static void decodeWorkerEnd() {
        var work=decodeWork.get(); if(work==null) return;
        long elapsed=System.nanoTime()-work.started,cpu=THREADS.getCurrentThreadCpuTime()-work.cpu;
        long heap=ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId())-work.heap;
        decodeWork.remove();
        synchronized(decodeFutures) {
            decodeWorkCount++; decodeWorkNanos+=elapsed; decodeWorkCpu+=cpu; decodeWorkHeap+=heap;
            var values=decodeThreads.computeIfAbsent(work.thread,k->new long[4]);
            values[0]++; values[1]+=elapsed; values[2]+=cpu; values[3]+=heap;
        }
    }
    public static JsonObject decodeEvidence() {
        synchronized(decodeFutures) {
            var result=new JsonObject(); result.addProperty("distinctObservedDecodeFutures",decodeFutures.size());
            result.addProperty("distinctCompletedNativeBuffers",decodedBuffers.size()); result.addProperty("failures",decodeFailures);
            result.addProperty("completionLatencyTotalNanos",decodeLatencyNanos);
            result.addProperty("decoderWorkerCompletions",decodeWorkCount); result.addProperty("decoderWorkerWallNanos",decodeWorkNanos);
            result.addProperty("decoderWorkerCpuNanos",decodeWorkCpu); result.addProperty("decoderWorkerAllocatedJavaHeapBytes",decodeWorkHeap);
            var workers=new JsonObject();
            decodeThreads.forEach((name,values)-> { var row=new JsonObject(); row.addProperty("completions",values[0]); row.addProperty("wallNanos",values[1]); row.addProperty("cpuNanos",values[2]); row.addProperty("allocatedJavaHeapBytes",values[3]); workers.add(name,row); });
            result.add("actualDecoderWorkerThreads",workers);
            result.addProperty("decoderScope","actual resource read + Vorbis decode + SoundBuffer creation on Minecraft worker thread; excludes later OpenAL buffer upload; heap count excludes native/direct allocations");
            var paths=new JsonObject(); decodePaths.forEach(paths::addProperty); result.add("completedResourcePaths",paths); return result;
        }
    }
    private static long percentile(long[] values,int count,int percent) { long[] sorted=Arrays.copyOf(values,count); Arrays.sort(sorted); return sorted[Math.max(0,(count*percent+99)/100-1)]; }
    private static JsonObject distribution(long[] values,int count) {
        long sum=0,max=0; for(int i=0;i<count;i++) { sum+=values[i]; max=Math.max(max,values[i]); }
        var result=new JsonObject(); result.addProperty("sum",sum); result.addProperty("p50",percentile(values,count,50));
        result.addProperty("p95",percentile(values,count,95)); result.addProperty("max",max); return result;
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError("Native performance: "+message); }

    /** Common-side observer: this nested class contains no client-class linkage. Installed only in opt-in fixture processes. */
    public static final class Network {
        public static final int METRICS_REVISION=3;
        public static final String COUNT_SCOPE="inPackets/outPackets are post-bundle logical messages; outEncodedPackets/outEncodedPayloadPackets count actual native serializer invocations, including unbundled members; encoded bytes precede compression/framing; socket bytes include framing but exclude TCP/IP headers";
        private static final Map<String,LongAdder> totals=new ConcurrentHashMap<>();
        private static final Map<Object,String> types=new ConcurrentHashMap<>();
        private static final java.util.concurrent.atomic.AtomicBoolean identityVerified=new java.util.concurrent.atomic.AtomicBoolean();
        public static boolean enabled() { return Boolean.getBoolean("essence.audio.gameplay") || Boolean.getBoolean("essence.audio.nativePerformance"); }
        private static void add(String key,long amount) { totals.computeIfAbsent(key,k->new LongAdder()).add(amount); }
        public static void install(ChannelHandlerContext context) {
            if(!enabled()) return;
            if(identityVerified.compareAndSet(false,true)) verifyPayloadIdentity();
            var pipeline=context.pipeline();
            if(pipeline.get("ea_audio_measure_wire")!=null) return;
            pipeline.addFirst("ea_audio_measure_wire",new ChannelDuplexHandler() {
                @Override public void channelRead(ChannelHandlerContext ctx,Object message) throws Exception { if(message instanceof ByteBuf bytes) add("inSerializedSocketBytes",bytes.readableBytes()); super.channelRead(ctx,message); }
                @Override public void write(ChannelHandlerContext ctx,Object message,ChannelPromise promise) throws Exception { if(message instanceof ByteBuf bytes) add("outSerializedSocketBytes",bytes.readableBytes()); super.write(ctx,message,promise); }
            });
            pipeline.addBefore(context.name(),"ea_audio_measure_packets",new ChannelDuplexHandler() {
                @Override public void channelRead(ChannelHandlerContext ctx,Object message) throws Exception { observe("in",message); super.channelRead(ctx,message); }
                @Override public void write(ChannelHandlerContext ctx,Object message,ChannelPromise promise) throws Exception { observe("out",message); super.write(ctx,message,promise); }
            });
        }
        private static void observe(String direction,Object message) {
            if(!(message instanceof Packet<?>)) return;
            add(direction+"Packets",1);
            if(message instanceof ClientboundBlockEntityDataPacket) add(direction+"BlockEntitySnapshots",1);
            var payload=payload(message);
            if(payload!=null) add(direction+"Payload:"+type(payload),1);
        }
        private static CustomPacketPayload payload(Object packet) { return packet instanceof ClientboundCustomPayloadPacket p?p.payload():packet instanceof ServerboundCustomPayloadPacket p?p.payload():null; }
        private static String type(CustomPacketPayload payload) {
            // Architectury's generic payload wrapper uses one implementation class for many channel IDs.
            var id=payload.type().id(); return types.computeIfAbsent(id,key->key.toString());
        }
        private record ProbePayload(CustomPacketPayload.Type<ProbePayload> type) implements CustomPacketPayload { }
        public static void verifyPayloadIdentity() {
            var first=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("essence_audio_test","probe_first");
            var second=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("essence_audio_test","probe_second");
            var a=new ProbePayload(new CustomPacketPayload.Type<>(first));
            var b=new ProbePayload(new CustomPacketPayload.Type<>(second));
            if(a.getClass()!=b.getClass() || !type(a).equals(first.toString()) || !type(b).equals(second.toString())
                    || !type(new ProbePayload(new CustomPacketPayload.Type<>(first))).equals(first.toString()))
                throw new AssertionError("Network observer must distinguish different payload IDs sharing one Java implementation class");
            types.remove(first); types.remove(second);
            System.out.println("AudioNativePerformanceAcceptance: PASS; payload-ID observer regression (same class/different IDs, stable repeated ID); metric revision "+METRICS_REVISION);
        }
        /** Actual serializer writer-index delta includes packet ID and payload, before compression/framing. */
        public static void encoded(Object packet,int bytes) {
            if(!enabled()) return;
            add("outEncodedPackets",1);
            add("outEncodedPacketBytes",bytes);
            if(packet instanceof ClientboundBlockEntityDataPacket) { add("outEncodedBlockEntitySnapshots",1); add("outEncodedBlockEntitySnapshotBytes",bytes); }
            var payload=payload(packet);
            if(payload!=null) { String id=type(payload); add("outEncodedPayloadPackets:"+id,1); add("outEncodedPayloadBytes:"+id,bytes); }
        }
        public static Map<String,Long> snapshot() { var out=new TreeMap<String,Long>(); totals.forEach((k,v)->out.put(k,v.sum())); return out; }
        public static JsonObject delta(Map<String,Long> before) { var out=new JsonObject(); snapshot().forEach((k,v)->out.addProperty(k,v-before.getOrDefault(k,0L))); return out; }
    }

    /** Boundary-only JMX snapshots: whole JVM process plus individual Java threads, never claimed as native allocation. */
    public static final class SystemSample {
        private record ThreadValue(String name,long cpu,long allocation) { }
        private final long processCpu;
        private final Map<Long,ThreadValue> threads;
        private SystemSample(long processCpu,Map<Long,ThreadValue> threads) { this.processCpu=processCpu; this.threads=threads; }
        public static SystemSample capture() {
            long process=ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean?bean.getProcessCpuTime():-1;
            var values=new TreeMap<Long,ThreadValue>();
            var bean=ManagementFactory.getThreadMXBean();
            var allocation=bean instanceof com.sun.management.ThreadMXBean b?b:null;
            if(bean.isThreadCpuTimeSupported() && !bean.isThreadCpuTimeEnabled()) bean.setThreadCpuTimeEnabled(true);
            if(allocation!=null && allocation.isThreadAllocatedMemorySupported() && !allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true);
            for(long id:bean.getAllThreadIds()) {
                var info=bean.getThreadInfo(id); if(info==null) continue;
                values.put(id,new ThreadValue(info.getThreadName(),bean.getThreadCpuTime(id),allocation==null?-1:allocation.getThreadAllocatedBytes(id)));
            }
            return new SystemSample(process,values);
        }
        public JsonObject delta(SystemSample after) {
            var result=new JsonObject(); result.addProperty("processCpuNanos",processCpu<0 || after.processCpu<0?-1:after.processCpu-processCpu);
            var list=new JsonArray(); long cpu=0,allocated=0;
            for(var entry:after.threads.entrySet()) {
                var before=threads.get(entry.getKey()); var value=entry.getValue();
                if(before==null) continue;
                long dc=before.cpu<0 || value.cpu<0?-1:value.cpu-before.cpu;
                long da=before.allocation<0 || value.allocation<0?-1:value.allocation-before.allocation;
                var row=new JsonObject(); row.addProperty("id",entry.getKey()); row.addProperty("name",value.name);
                row.addProperty("cpuNanos",dc); row.addProperty("allocatedJavaHeapBytes",da); list.add(row);
                if(dc>=0) cpu+=dc; if(da>=0) allocated+=da;
            }
            result.addProperty("survivingJavaThreadsCpuNanos",cpu); result.addProperty("survivingJavaThreadsAllocatedHeapBytes",allocated);
            result.addProperty("beforeJavaThreadCount",threads.size()); result.addProperty("afterJavaThreadCount",after.threads.size());
            result.addProperty("untrackedThreadChurn",after.threads.keySet().stream().filter(id->!threads.containsKey(id)).count()+threads.keySet().stream().filter(id->!after.threads.containsKey(id)).count());
            result.add("javaThreads",list); return result;
        }
    }
}
