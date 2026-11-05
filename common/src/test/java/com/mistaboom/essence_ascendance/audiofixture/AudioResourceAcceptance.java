package com.mistaboom.essence_ascendance.audiofixture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.audio.*;
import com.mistaboom.essence_ascendance.client.audio.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Opt-in disposable resource-pack fault/recovery probe. Never edits production assets or substitutes a sink. */
public final class AudioResourceAcceptance {
    private static final String PACK_NAME="ea_audio_recovery", PACK_ID="file/"+PACK_NAME;
    private static final ResourceLocation SOUNDS=ResourceLocation.parse("essence_ascendance:sounds.json");
    private static final ResourceLocation CORRUPT=ResourceLocation.parse("essence_ascendance:sounds/fixture/corrupt.ogg");
    private static final byte[] INVALID_OGG="Deliberately invalid fixture bytes; not an approved sound.".getBytes(StandardCharsets.US_ASCII);
    private final Minecraft mc=Minecraft.getInstance();
    private final AudioEngine engine;
    private final Supplier<AudioEngine.Voice> currentVoice;
    private final AudioCatalog.Cue cue=AudioCatalog.get().profile("machine.resonance").body();
    private final SemanticCaptions captions=(SemanticCaptions)read(CaptionRouter.class,null,"STATE");
    private final List<String> originalPacks=List.copyOf(mc.getResourcePackRepository().getSelectedIds());
    private final String originalOrigin=mc.getResourceManager().getResource(SOUNDS).orElseThrow().sourcePackId();
    private final Set<SoundInstance> retained=Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<String> callbackEvidence=new ArrayList<>();
    private final List<String> recoveryFailures=new ArrayList<>();
    private final Path pack;
    private final boolean streaming;
    private CompletableFuture<Void> reload;
    private CompletableFuture<NativeSample> sampling;
    private NativeSample latest;
    private int stage,callbacks,callbacksAtSettlement,checks,samples,maxHandles,maxPlaying,maxVoices,maxSources;
    private long phaseTick,lastSample=-100;
    private boolean done;
    private record NativeSample(int handles,int playing,int ownedSources,int voices,int callbacks,int captionLines) { }

    AudioResourceAcceptance(AudioEngine engine,Supplier<AudioEngine.Voice> currentVoice,long tick) throws Exception {
        this.engine=engine; this.currentVoice=currentVoice; phaseTick=tick;
        var initial=currentVoice.get();
        check(initial instanceof SoundInstance,"recovery begins with actual native source body");
        var sound=(SoundInstance)initial; retained.add(sound); streaming=sound.getSound().shouldStream();
        Path game=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        Path directory=mc.getResourcePackDirectory().toAbsolutePath().normalize();
        check(directory.equals(game.resolve("resourcepacks")) && game.toString().contains("audio-packaged-acceptance"),"fault pack stays in isolated packaged acceptance game directory");
        pack=directory.resolve(PACK_NAME);
        check(!Files.exists(pack),"fault pack is unique to this disposable run");
        Files.createDirectories(pack.resolve("assets/essence_ascendance/sounds/fixture"));
        Files.writeString(pack.resolve("pack.mcmeta"),packMetadata());
        Files.write(pack.resolve("assets/essence_ascendance/sounds/fixture/corrupt.ogg"),INVALID_OGG);
        mc.getSoundManager().addListener((instance,event,range)->{
            if(id(instance).equals(cue.id())) {
                callbacks++;
                if(callbackEvidence.size()<64) callbackEvidence.add("stage="+stage+",reloadPending="+(reload!=null)+",sample="+instance.getSound().getPath()+",worldTick="+(mc.level==null?-1:mc.level.getGameTime()));
            }
        });
        System.out.println("AudioResourceAcceptance: baseline source="+cue.id()+" streaming="+streaming+" originalPack="+originalOrigin+" isolatedPack="+pack);
    }

    /** Called from the existing joined-world fixture; every native observation is asynchronous on its audio executor. */
    boolean tick(long tick) throws Exception {
        if(done) return true;
        if(reload!=null) {
            if(!reload.isDone() || mc.getOverlay()!=null) return false;
            reload.join(); reload=null;
            String origin=mc.getResourceManager().getResource(SOUNDS).orElseThrow().sourcePackId();
            check(origin.equals(stage==3?originalOrigin:PACK_ID),"resource manager selects requested pack for phase "+stage+": "+origin);
            System.out.println("AudioResourceAcceptance: reload settled stage="+stage+" "+runtimeEvidence());
            if(stage==2) {
                var resource=mc.getResourceManager().getResource(CORRUPT).orElseThrow();
                try(var input=resource.open()) { check(resource.sourcePackId().equals(PACK_ID) && Arrays.equals(input.readAllBytes(),INVALID_OGG),"corrupt phase reads only fixture bytes from selected pack"); }
            }
            reset(tick);
        }
        if(sampling!=null && sampling.isDone()) {
            latest=sampling.join(); sampling=null; samples++;
            maxHandles=Math.max(maxHandles,latest.handles()); maxPlaying=Math.max(maxPlaying,latest.playing());
            maxVoices=Math.max(maxVoices,latest.voices()); maxSources=Math.max(maxSources,latest.ownedSources());
            check(latest.ownedSources()==1 && latest.voices()<=1 && latest.handles()<=1 && latest.playing()<=1,"one source stays bounded during resource phase "+stage+": "+latest);
        }
        if(sampling==null && tick-lastSample>=10) { sampling=sample(); lastSample=tick; }
        long elapsed=tick-phaseTick;
        if(stage==4 && elapsed>=40 && latest!=null && samples>=3) {
            report("diagnostic-manual-cache-clear");
            System.out.println("AudioResourceAcceptance: DIAGNOSTIC existing NativeAudio.invalidated() intervention actualPlaying="+latest.playing()+"; unassisted recovery remains FAIL");
            throw new AssertionError("Unassisted resource recovery failures: "+recoveryFailures+"; manual cache-clear actualPlaying="+latest.playing());
        }
        if(stage==0 && elapsed>=20 && latest!=null) {
            check(latest.playing()==1,"baseline native OpenAL channel is actually playing");
            report("baseline"); install(false); stage=1; return false;
        }
        if(stage>0 && elapsed>=120 && latest!=null && samples>=5) {
            check(latest.captionLines()>0,"semantic caption remains available through resource phase "+stage);
            if(stage==1) {
                report("missing");
                var nativeAudio=read(AudioRuntime.class,null,"NATIVE");
                expect(((Set<?>)read(NativeAudio.class,nativeAudio,"unavailable")).contains(cue.id()),"missing sample is remembered for resource epoch");
                expect(maxHandles==0 && maxPlaying==0 && maxVoices==0 && callbacks==0,"missing sample creates no native handle and no repeated native playback");
                install(true); stage=2; return false;
            }
            if(stage==2) {
                report("corrupt");
                expect(callbacks>0,"corrupt sample reaches native playback/decode path");
                check(maxPlaying==0,"invalid fixture bytes never produce a playing native channel");
                mc.getResourcePackRepository().setSelected(originalPacks); clearCallbacks(); System.out.println("AudioResourceAcceptance: restoring "+runtimeEvidence()); reload=mc.reloadResourcePacks(); stage=3; return false;
            }
            report("restored");
            check(List.copyOf(mc.getResourcePackRepository().getSelectedIds()).equals(originalPacks),"original resource-pack selection is restored");
            expect(latest.playing()==1 && latest.handles()==1 && latest.voices()==1,"restoration yields exactly one actually playing native source body");
            var restored=currentVoice.get();
            expect(restored instanceof SoundInstance && restored.alive(),"restored owner has a live native body");
            for(var old:retained) if(old!=restored) check(!mc.getSoundManager().isActive(old),"retired fault/baseline native handle is inactive after restore");
            expect(callbacks==1,"restoration starts exactly one native body");
            if(!recoveryFailures.isEmpty() && latest.playing()==0) {
                var nativeAudio=(NativeAudio)read(AudioRuntime.class,null,"NATIVE");
                if(((Set<?>)read(NativeAudio.class,nativeAudio,"unavailable")).contains(cue.id())) {
                    System.out.println("AudioResourceAcceptance: DIAGNOSTIC clearing only existing unavailable cache after unassisted failure; "+runtimeEvidence());
                    nativeAudio.invalidated(); clearCallbacks(); stage=4; reset(tick); return false;
                }
            }
            check(recoveryFailures.isEmpty(),"resource recovery failures: "+recoveryFailures);
            done=true;
            System.out.println("AudioResourceAcceptance: PASS; "+checks+" assertions; isolated missing/corrupt sample, bounded native state, retained captions and restored actual OpenAL playback; expected corrupt silence is not a defect; no listening claim");
            return true;
        }
        return false;
    }

    private void install(boolean corrupt) throws Exception {
        JsonObject definition=new JsonObject(); definition.addProperty("replace",true); definition.addProperty("subtitle",cue.subtitle());
        JsonObject sample=new JsonObject(); sample.addProperty("name","essence_ascendance:fixture/"+(corrupt?"corrupt":"missing")); sample.addProperty("stream",streaming);
        var sounds=new com.google.gson.JsonArray(); sounds.add(sample); definition.add("sounds",sounds);
        JsonObject root=new JsonObject(); root.add(cue.id().getPath(),definition);
        Files.writeString(pack.resolve("assets/essence_ascendance/sounds.json"),root.toString());
        var repository=mc.getResourcePackRepository(); repository.reload();
        check(repository.getPack(PACK_ID)!=null,"native repository discovers fixture pack");
        var selected=new ArrayList<>(originalPacks); selected.add(PACK_ID); repository.setSelected(selected);
        clearCallbacks(); System.out.println("AudioResourceAcceptance: installing "+(corrupt?"corrupt":"missing")+" "+runtimeEvidence()); reload=mc.reloadResourcePacks();
    }
    private void clearCallbacks() { callbacks=0; callbacksAtSettlement=0; callbackEvidence.clear(); }
    private void reset(long tick) { phaseTick=tick; lastSample=tick-10; latest=null; sampling=null; callbacksAtSettlement=callbacks; samples=maxHandles=maxPlaying=maxVoices=maxSources=0; }
    @SuppressWarnings("unchecked")
    private CompletableFuture<NativeSample> sample() {
        var nativeEngine=(SoundEngine)read(SoundManager.class,mc.getSoundManager(),"soundEngine");
        var handles=(Map<SoundInstance,ChannelAccess.ChannelHandle>)read(SoundEngine.class,nativeEngine,"instanceToChannel");
        var selected=new ArrayList<ChannelAccess.ChannelHandle>();
        for(var entry:handles.entrySet()) if(id(entry.getKey()).equals(cue.id())) { selected.add(entry.getValue()); retained.add(entry.getKey()); }
        int ownedSources=engine.sources(),voices=engine.voices(),starts=callbacks;
        int captionLines=(int)captions.lines().stream().filter(ticket->ticket.cue==cue).count();
        AtomicInteger playing=new AtomicInteger();
        for(var handle:selected) handle.execute(channel->{ if(channel.playing()) playing.incrementAndGet(); });
        CompletableFuture<NativeSample> result=new CompletableFuture<>();
        // FIFO barrier after handle callbacks; a concurrently retired handle legitimately contributes zero.
        ((Executor)read(SoundEngine.class,nativeEngine,"executor")).execute(()->result.complete(new NativeSample(selected.size(),playing.get(),ownedSources,voices,starts,captionLines)));
        return result;
    }
    private String runtimeEvidence() {
        var definition=mc.getSoundManager().getSoundEvent(cue.id());
        var sound=definition==null?null:definition.getSound(net.minecraft.util.RandomSource.create(0));
        var nativeAudio=read(AudioRuntime.class,null,"NATIVE");
        return "presentationEpoch="+com.mistaboom.essence_ascendance.client.ClientPacketDispatch.presentationEpoch()
                +" definitionIdentity="+(definition==null?0:System.identityHashCode(definition))
                +" resolvedSample="+(sound==null?null:sound.getPath())
                +" empty="+(sound==SoundManager.EMPTY_SOUND)+" unavailable="+((Set<?>)read(NativeAudio.class,nativeAudio,"unavailable")).contains(cue.id());
    }
    private void report(String phase) { System.out.println("AudioResourceAcceptance: "+phase+" samples="+samples+" nativeCallbacks="+callbacks+" callbacksAtSettlement="+callbacksAtSettlement+" callbacksAfterSettlement="+(callbacks-callbacksAtSettlement)+" maxNativeHandles="+maxHandles+" maxActuallyPlaying="+maxPlaying+" maxOwnedSources="+maxSources+" maxVoices="+maxVoices+" final="+latest+" callbackEvidence="+callbackEvidence+" "+runtimeEvidence()); }
    private static String packMetadata() throws Exception {
        try(var input=Minecraft.class.getResourceAsStream("/version.json")) {
            if(input==null) throw new AssertionError("Native version metadata unavailable");
            var version=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            var formats=version.getAsJsonObject("pack_version");
            JsonObject metadata=new JsonObject(); metadata.addProperty("description","Disposable Essence audio recovery acceptance fixture");
            if(formats.has("resource")) metadata.addProperty("pack_format",formats.get("resource").getAsInt());
            else {
                var exact=new com.google.gson.JsonArray(); exact.add(formats.get("resource_major").getAsInt()); exact.add(formats.get("resource_minor").getAsInt());
                metadata.add("min_format",exact); metadata.add("max_format",exact.deepCopy());
            }
            JsonObject root=new JsonObject(); root.add("pack",metadata); return root.toString();
        }
    }
    private static ResourceLocation id(SoundInstance sound) {
        //? if >=26.1.2
        /*return sound.getIdentifier();*/
        //? if <26.1.2
        return sound.getLocation();
    }
    private static Object read(Class<?> type,Object owner,String field) { return AudioWorldAcceptance.read(type,owner,field); }
    private void expect(boolean condition,String description) { if(!condition) recoveryFailures.add(description); else checks++; }
    private void check(boolean condition,String description) { if(!condition) throw new AssertionError(description); checks++; }
}
