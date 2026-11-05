package com.mistaboom.essence_ascendance.audio;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.verification.TargetTestInputs;
import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Executes the real native pre-device playback and subtitle callback paths. Never opens an audio device. */
public final class AudioNativeTest {
    private static int checks;
    private static final ResourceLocation ID=ResourceLocation.parse("essence_ascendance:test");
    private static final String SOUND_APPLY="(Lnet/minecraft/client/sounds/SoundManager$Preparations;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V";
    private static Sound SAMPLE;
    private static final class BeforeDevice extends RuntimeException { }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        SAMPLE=new Sound(ID,ConstantFloat.of(1),ConstantFloat.of(1),1,Sound.Type.FILE,false,false,16);
        //? if >=26.1.2
        /*boolean modern=true;*/
        //? if <26.1.2
        boolean modern=false;
        probe(0,1,1,false,1,"master zero");
        probe(1,0,1,false,modern?1:0,"category zero");
        probe(1,1,0,false,modern?1:0,"instance gain zero");
        probe(1,1,1,true,0,"missing event");
        probe(1,1,1,false,1,"audible entry to subtitle callback");
        var overlay=new SubtitleOverlay(null); var sample=new Sample(1,false); sample.resolve(null);
        overlay.onPlaySound(sample,sample.events,16); overlay.onPlaySound(sample,sample.events,16);
        var subtitles=field(SubtitleOverlay.class,"subtitles");
        check(((List<?>)subtitles.get(overlay)).size()==1,"native overlay merges identical semantic subtitle text");
        inspect("fabric",modern); inspect("neoforge",modern);
        resources();
        System.out.println("Audio native pre-device/subtitle/resource paths: "+checks+" assertions PASS; Minecraft "+System.getProperty("essence.minecraft")+" (no device, pixels or joined world)");
    }
    private static void probe(float master,float category,float instanceGain,boolean missing,int expected,String label) throws Exception {
        Options options=allocate(Options.class); Map<SoundSource,OptionInstance<Double>> volumes=new EnumMap<>(SoundSource.class);
        for(var source:SoundSource.values()) {
            @SuppressWarnings("unchecked") OptionInstance<Double> option=allocate(OptionInstance.class);
            field(OptionInstance.class,"value").set(option,(double)(source==SoundSource.MASTER?master:category)); volumes.put(source,option);
        }
        field(Options.class,"soundSourceVolumes").set(options,volumes);
        SoundEngine engine=allocate(SoundEngine.class);
        field(SoundEngine.class,"loaded").set(engine,true); field(SoundEngine.class,"options").set(engine,options);
        //? if >=26.1.2
        /*var gains=new it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap<SoundSource>(); gains.defaultReturnValue(1); field(SoundEngine.class,"gainBySource").set(engine,gains);*/
        //? if <26.1.2
        Listener listener=allocate(Listener.class);
        //? if <26.1.2
        field(Listener.class,"gain").set(listener,master);
        //? if <26.1.2
        field(SoundEngine.class,"listener").set(engine,listener);
        int[] callbacks={0}; var overlay=new SubtitleOverlay(null);
        SoundEventListener observer=(instance,events,range)->{
            callbacks[0]++; overlay.onPlaySound(instance,events,range);
            if(master>0 && category>0 && instanceGain>0) throw new BeforeDevice();
        };
        field(SoundEngine.class,"listeners").set(engine,new ArrayList<>(List.of(observer)));
        try { engine.play(new Sample(instanceGain,missing)); } catch(BeforeDevice expectedDeviceBoundary) { }
        check(callbacks[0]==expected,label+": observed native subtitle callbacks "+callbacks[0]+", expected "+expected);
    }
    private static final class Sample extends AbstractSoundInstance {
        final WeighedSoundEvents events=new WeighedSoundEvents(ID,"subtitles.essence_ascendance.combat.explosion");
        final boolean missing;
        Sample(float gain,boolean missing) { super(ID,SoundSource.BLOCKS,RandomSource.create(1)); volume=gain; pitch=1; this.missing=missing; }
        @Override public WeighedSoundEvents resolve(SoundManager manager) { sound=SAMPLE; return missing?null:events; }
    }
    private static void inspect(String loader,boolean modern) throws Exception {
        var brewing=read(loader,"world/level/block/entity/BrewingStandBlockEntity");
        check(brewing.methods.stream().anyMatch(method->
                java.util.stream.StreamSupport.stream(method.instructions.spliterator(),false).anyMatch(n->n instanceof IntInsnNode value && value.operand==1035)
                && java.util.stream.StreamSupport.stream(method.instructions.spliterator(),false).anyMatch(n->n instanceof MethodInsnNode call && call.name.equals("levelEvent"))),
                loader+": native brewing completion owns its level-event result sound");
        ClassNode engine=read(loader,"client/sounds/SoundEngine");
        MethodNode play=engine.methods.stream().filter(m->m.name.equals("play")).findFirst().orElseThrow();
        int callback=-1,silent=-1,master=-1,index=0;
        for(var instruction:play.instructions) {
            if(instruction instanceof MethodInsnNode m) { if(m.name.equals("onPlaySound")) callback=index; if(m.name.equals("canStartSilent")) silent=index; if(m.name.equals("getGain")) master=index; }
            index++;
        }
        check(callback>=0 && silent>=0 && (modern?callback<silent:silent<callback),loader+": exact native category-mute branch order");
        if(!modern) check(master>callback,loader+": native master mute happens after subtitle notification");
        MethodNode tick=engine.methods.stream().filter(m->m.name.equals(modern?"tickInGameSound":"tickNonPaused")).findFirst().orElseThrow();
        check(java.util.stream.StreamSupport.stream(tick.instructions.spliterator(),false).noneMatch(n->n instanceof MethodInsnNode m && m.name.equals("onPlaySound")),loader+": looping ticks do not refresh subtitle callbacks");
        var listener=read(loader,"client/multiplayer/ClientPacketListener");
        var handle=listener.methods.stream().filter(m->m.name.equals("handleSoundEvent")).findFirst().orElseThrow();
        int thread=-1,delivery=-1; index=0;
        for(var instruction:handle.instructions) { if(instruction instanceof MethodInsnNode m) { if(m.name.equals("ensureRunningOnSameThread")) thread=index; if(m.name.equals("playSeededSound")) delivery=index; } index++; }
        check(thread>=0 && delivery>thread,loader+": native sound packets retain thread validation and native playback");
        var overlay=read(loader,"client/gui/components/SubtitleOverlay");
        var render=overlay.methods.stream().filter(m->m.name.equals(modern?"extractRenderState":"render")).findFirst().orElseThrow();
        long textDraws=java.util.stream.StreamSupport.stream(render.instructions.spliterator(),false)
                .filter(n->n instanceof MethodInsnNode m && m.owner.equals(modern?"net/minecraft/client/gui/GuiGraphicsExtractor":"net/minecraft/client/gui/GuiGraphics")
                        && m.name.equals(modern?"text":"drawString") && m.desc.equals("(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)"+(modern?"V":"I"))).count();
        check(textDraws==1,loader+": exact single native subtitle Component draw anchor after native filtering");
        var gui=read(loader,"client/gui/Gui");
        subtitleLayout(loader,modern,render,gui);
        var actionbar=gui.methods.stream().filter(m->m.name.equals(modern?"extractOverlayMessage":"renderOverlayMessage")).findFirst().orElseThrow();
        check(java.util.stream.StreamSupport.stream(actionbar.instructions.spliterator(),false).filter(n->n instanceof MethodInsnNode call
                && call.name.equals(modern?"textWithBackdrop":"drawStringWithBackdrop")
                && call.desc.equals("(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)"+(modern?"V":"I"))).count()==1,
                loader+": actionbar reservation observes the actual native backdrop draw, including loader-adjusted transforms");
        var renderer=read(loader,"client/renderer/GameRenderer");
        if(modern) {
            var extraction=renderer.methods.stream().filter(m->m.name.equals("extractGui")).findFirst().orElseThrow();
            int hudAt=-1,deferredAt=-1; index=0;
            for(var instruction:extraction.instructions) {
                if(instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/Gui")) {
                    if(call.name.equals("extractRenderState")) hudAt=index;
                    if(call.name.equals("extractDeferredSubtitles")) deferredAt=index;
                }
                index++;
            }
            check(hudAt>=0 && deferredAt>hudAt,loader+": actual native subtitles occur after HUD extraction; Gui tail is too early for fallback");
        }
        var frame=new ClassNode();
        new ClassReader(Files.readAllBytes(TargetTestInputs.compiledClass("common","com/mistaboom/essence_ascendance/mixin/AudioCaptionFrameMixin"))).accept(frame,0);
        var finish=frame.methods.stream().filter(m->m.name.equals(modern?"essenceAscendance$finishAudioFrame":"essenceAscendance$finishAudioHud")).findFirst().orElseThrow();
        var inject=finish.visibleAnnotations.stream().filter(a->a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var anchors=(List<AnnotationNode>)inject.values.get(inject.values.indexOf("at")+1);
        var anchor=anchors.getFirst();
        String target=(String)anchor.values.get(anchor.values.indexOf("target")+1);
        String[] shift=(String[])anchor.values.get(anchor.values.indexOf("shift")+1);
        check(target.equals(modern?"Lnet/minecraft/client/gui/Gui;extractDeferredSubtitles()V":"Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V")
                && shift[1].equals("AFTER"),loader+": production fallback follows the actual native subtitle phase");
        var soundManager=read(loader,"client/sounds/SoundManager");
        var apply=soundManager.methods.stream().filter(m->m.name.equals("apply") && m.desc.equals(SOUND_APPLY)).findFirst().orElseThrow();
        int definitions=-1,device=-1,returned=-1,returns=0,commits=0,reloads=0; index=0;
        for(var instruction:apply.instructions) {
            if(instruction instanceof MethodInsnNode call) {
                if(call.owner.equals("net/minecraft/client/sounds/SoundManager$Preparations") && call.name.equals("apply")) { definitions=index; commits++; }
                if(call.owner.equals("net/minecraft/client/sounds/SoundEngine") && call.name.equals("reload")) { device=index; reloads++; }
            }
            if(instruction.getOpcode()==Opcodes.RETURN) { returned=index; returns++; }
            index++;
        }
        check(commits==1 && reloads==1 && returns==1 && definitions>=0 && device>definitions && returned>device,
                loader+": typed native sound apply commits definitions and reloads the engine before its sole return");
    }
    private static void subtitleLayout(String loader,boolean modern,MethodNode render,ClassNode gui) throws Exception {
        String graphics=modern?"net/minecraft/client/gui/GuiGraphicsExtractor":"net/minecraft/client/gui/GuiGraphics";
        int push=-1,fill=-1,text=-1,pop=-1,purge=-1,index=0,fills=0;
        for(var instruction:render.instructions) {
            if(instruction instanceof MethodInsnNode call) {
                if(call.name.equals(modern?"pushMatrix":"pushPose")) push=index;
                if(call.owner.equals(graphics) && call.name.equals("fill") && call.desc.equals("(IIIII)V")) { fill=index; fills++; }
                if(call.owner.equals(graphics) && call.desc.equals("(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)"+(modern?"V":"I"))) text=index;
                if(call.name.equals(modern?"popMatrix":"popPose")) pop=index;
                if(call.name.equals("purgeOldInstances")) purge=index;
            }
            index++;
        }
        check(fills==1 && purge>=0 && push>purge && fill>push && text>fill && pop>text,
                loader+": one native backdrop anchor observes filtered rows inside the unchanged balanced native pose");
        var subtitleHook=new ClassNode();
        new ClassReader(Files.readAllBytes(TargetTestInputs.compiledClass("common","com/mistaboom/essence_ascendance/mixin/AudioSubtitleMixin"))).accept(subtitleHook,0);
        var observer=subtitleHook.methods.stream().filter(m->m.name.equals("essenceAscendance$observeNativeBounds")).findFirst().orElseThrow();
        var observation=observer.visibleAnnotations.stream().filter(a->a.desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;")).findFirst().orElseThrow();
        var backdrop=singleAnchor(observation,loader+": native backdrop observation");
        check(backdrop.values.get(backdrop.values.indexOf("target")+1).equals("L"+graphics+";fill(IIIII)V"),
                loader+": footprint reads the exact native backdrop without remeasuring or refiltering subtitles");
        check(java.util.stream.StreamSupport.stream(observer.instructions.spliterator(),false)
                        .noneMatch(n->n instanceof MethodInsnNode call && (call.name.equals("translate") || call.name.equals("scale")
                                || call.owner.equals("org/spongepowered/asm/mixin/injection/invoke/arg/Args") && Set.of("set","setAll").contains(call.name)
                                || call.owner.equals(graphics) && Set.of("fill","text","drawString").contains(call.name))),
                loader+": footprint observer cannot modify native coordinates, arguments, text or draw order");
        check(subtitleHook.fields.stream().noneMatch(f->f.name.equals("audibleSubtitles") || f.name.contains("Shift") || f.name.contains("Placed")),
                loader+": native subtitles retain their own filtering and placement state");
        var hudHook=new ClassNode();
        new ClassReader(Files.readAllBytes(TargetTestInputs.compiledClass("common","com/mistaboom/essence_ascendance/mixin/SkillEffectHudMixin"))).accept(hudHook,0);
        long nativeCardCalls=subtitleHook.methods.stream().flatMap(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false))
                .filter(n->n instanceof MethodInsnNode call && call.owner.equals("com/mistaboom/essence_ascendance/client/SkillEffectHudOverlay") && call.name.equals("render")).count();
        long hudCardCalls=hudHook.methods.stream().flatMap(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false))
                .filter(n->n instanceof MethodInsnNode call && call.owner.equals("com/mistaboom/essence_ascendance/client/SkillEffectHudOverlay") && call.name.equals("render")).count();
        check(nativeCardCalls==0 && hudCardCalls==1,
                loader+": one original HUD-stage card renderer stays independent of native subtitle callbacks");
        var hudRender=hudHook.methods.stream().filter(m->m.name.equals("essenceAscendance$renderSkillEffectHud")).findFirst().orElseThrow();
        var hudInjection=hudRender.visibleAnnotations.stream().filter(a->a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
        var hudAnchor=singleAnchor(hudInjection,loader+": original card HUD stage");
        check(hudAnchor.values.get(hudAnchor.values.indexOf("value")+1).equals("TAIL"),
                loader+": moving cards cannot be deferred above opaque screens");
        if(!modern) {
            var callbacks=gui.methods.stream().filter(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false)
                    .anyMatch(n->n instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/components/SubtitleOverlay") && call.name.equals("render"))).toList();
            check(callbacks.size()==1 && java.util.stream.StreamSupport.stream(callbacks.getFirst().instructions.spliterator(),false)
                            .noneMatch(n->n instanceof JumpInsnNode || n instanceof MethodInsnNode call && call.name.equals("showSubtitles")),
                    loader+": legacy HUD layer invokes SubtitleOverlay even when native subtitles are disabled");
        }
    }
    private static AnnotationNode singleAnchor(AnnotationNode annotation,String label) {
        Object raw=annotation.values.get(annotation.values.indexOf("at")+1);
        List<?> anchors=raw instanceof List<?> list?list:List.of(raw);
        check(anchors.size()==1 && anchors.getFirst() instanceof AnnotationNode,label+" has exactly one compiled anchor");
        return (AnnotationNode)anchors.getFirst();
    }
    private static void resources() throws Exception {
        var channel=new ClassNode();
        new ClassReader(Files.readAllBytes(TargetTestInputs.compiledClass("common","com/mistaboom/essence_ascendance/client/ChannelSessionsClientState"))).accept(channel,0);
        var draw=channel.methods.stream().filter(method->method.name.equals("render")).findFirst().orElseThrow();
        check(java.util.stream.StreamSupport.stream(draw.instructions.spliterator(),false).noneMatch(n->n instanceof MethodInsnNode call
                        && (call.owner.endsWith("/AudioRuntime") || call.owner.equals(channel.name) && call.name.equals("tick"))),
                "channel drawing cannot drive continuous audio reconciliation or expiry");
        {
            var nativeSounds=JsonParser.parseString(Files.readString(Path.of(System.getProperty("essence.native.minecraft.sounds.json")))).getAsJsonObject();
            var path=Path.of(System.getProperty("essence.processed.common.resources"));
            var generated=JsonParser.parseString(Files.readString(path.resolve("assets/essence_ascendance/sounds.json"))).getAsJsonObject();
            var descriptor=JsonParser.parseString(Files.readString(path.resolve("assets/essence_ascendance/audio/catalog.json"))).getAsJsonObject();
            var mixins=JsonParser.parseString(Files.readString(path.resolve("essence_ascendance.mixins.json"))).getAsJsonObject();
            check(!mixins.toString().contains("AudioNativePacketMixin"),"ordinary playsound/native sound seeds are not intercepted as feature timestamps");
            check(mixins.getAsJsonArray("client").contains(new JsonPrimitive("AudioSoundReloadMixin"))
                            && !mixins.getAsJsonArray("mixins").contains(new JsonPrimitive("AudioSoundReloadMixin")),
                    "native audio resource commit hook is registered only on the client");
            var reloadHook=new ClassNode();
            new ClassReader(Files.readAllBytes(TargetTestInputs.compiledClass("common","com/mistaboom/essence_ascendance/mixin/AudioSoundReloadMixin"))).accept(reloadHook,0);
            var committed=reloadHook.methods.stream().filter(m->m.name.equals("essenceAscendance$soundResourcesCommitted")).findFirst().orElseThrow();
            var injection=committed.visibleAnnotations.stream().filter(a->a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") var methods=(List<String>)injection.values.get(injection.values.indexOf("method")+1);
            @SuppressWarnings("unchecked") var positions=(List<AnnotationNode>)injection.values.get(injection.values.indexOf("at")+1);
            check(methods.equals(List.of("apply"+SOUND_APPLY)) && positions.size()==1
                            && positions.getFirst().values.get(positions.getFirst().values.indexOf("value")+1).equals("TAIL"),
                    "audio invalidation follows the typed native definition commit once, excluding its generic bridge");
            check(java.util.stream.StreamSupport.stream(committed.instructions.spliterator(),false).filter(n->n instanceof MethodInsnNode call
                            && call.owner.equals("com/mistaboom/essence_ascendance/client/audio/AudioRuntime") && call.name.equals("resourcesReloaded")).count()==1,
                    "native commit has one shared audio invalidation dispatch");
            var presentation=new ClassNode();
            new ClassReader(Files.readAllBytes(TargetTestInputs.compiledClass("common","com/mistaboom/essence_ascendance/client/ClientPacketDispatch"))).accept(presentation,0);
            var presentationReload=presentation.methods.stream().filter(m->m.name.equals("resourcesReloaded")).findFirst().orElseThrow();
            check(java.util.stream.StreamSupport.stream(presentationReload.instructions.spliterator(),false).noneMatch(n->n instanceof MethodInsnNode call
                            && call.owner.equals("com/mistaboom/essence_ascendance/client/audio/AudioRuntime")),
                    "unordered visual resource listeners cannot invalidate audio before new sound definitions commit");
            var runtime=new ClassNode();
            new ClassReader(Files.readAllBytes(TargetTestInputs.compiledClass("common","com/mistaboom/essence_ascendance/client/audio/AudioRuntime"))).accept(runtime,0);
            check(runtime.methods.stream().noneMatch(method->method.name.equals("nativePacket")),"feature playback has no parallel native packet interpretation path");
            for(var method:runtime.methods) if(Set.of("tick","playerTransfers","infuserTransfer","reconcile").contains(method.name))
                check(java.util.stream.StreamSupport.stream(method.instructions.spliterator(),false).noneMatch(n->n instanceof MethodInsnNode call && call.name.equals("getGameTime")),
                        "locally observed source lifetime/order cannot use server-correctable game time: "+method.name+method.desc);
            var localReconcile=runtime.methods.stream().filter(method->method.name.equals("reconcile") && (method.access&Opcodes.ACC_PRIVATE)!=0).findFirst().orElseThrow();
            check(java.util.stream.StreamSupport.stream(localReconcile.instructions.spliterator(),false).anyMatch(n->n instanceof FieldInsnNode field
                            && field.getOpcode()==Opcodes.GETSTATIC && field.owner.equals(runtime.name) && field.name.equals("clock")),
                    "one local reconciliation owner supplies presentation-clock revisions for both paid source families");
            check(generated.keySet().equals(AudioCatalog.get().cues().stream().map(cue->cue.id().getPath()).collect(java.util.stream.Collectors.toSet())),"registered catalog and generated native event definitions agree exactly");
            Set<String> expectedFiles=new HashSet<>();
            for(var entry:descriptor.getAsJsonObject("assets").entrySet()) {
                String resource=entry.getValue().getAsJsonObject().get("resource").getAsString();
                expectedFiles.add(resource);
                var id=ResourceLocation.parse(resource);
                check(Files.isRegularFile(path.resolve("assets/"+id.getNamespace()+"/sounds/"+id.getPath()+".ogg")),"every runtime asset resolves to one generated sound file: "+resource);
            }
            Set<String> referencedFiles=new HashSet<>();
            for(var cue:generated.entrySet()) for(var value:cue.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                var sample=value.getAsJsonObject();
                if(sample.has("type") && sample.get("type").getAsString().equals("event")) {
                    String event=sample.get("name").getAsString().substring("minecraft:".length());
                    nativeAlias(nativeSounds,event,new HashSet<>());
                } else { String resource=sample.get("name").getAsString(); referencedFiles.add(resource); check(expectedFiles.contains(resource),"native file reference belongs to runtime catalog: "+resource); }
            }
            check(referencedFiles.equals(expectedFiles),"runtime catalog has no orphan audio assets");
            var language=JsonParser.parseString(Files.readString(path.resolve("assets/essence_ascendance/lang/en_us.json"))).getAsJsonObject();
            for(var cue:AudioCatalog.get().cues()) {
                check(cue.subtitle().equals("none") || language.has(cue.subtitle()) && !language.get(cue.subtitle()).getAsString().isBlank(),"single nonblank subtitle translation owner "+cue.id());
                var definition=generated.getAsJsonObject(cue.id().getPath());
                check(cue.subtitle().equals("none")?!definition.has("subtitle"):definition.has("subtitle") && definition.get("subtitle").getAsString().equals(cue.subtitle()),"semantic/native subtitle keys agree "+cue.id());
            }
        }
        // Inspect common registration/definition ownership without loading any client class.
        for(String type:List.of("AudioCatalog","AudioContent","AudioSourceBridge","AudioEngine")) {
            var node=new ClassNode();
            try(var stream=AudioNativeTest.class.getResourceAsStream("/com/mistaboom/essence_ascendance/audio/"+type+".class")) { new ClassReader(stream).accept(node,0); }
            for(var method:node.methods) for(var instruction:method.instructions)
                if(instruction instanceof MethodInsnNode call) check(!call.owner.startsWith("net/minecraft/client/") && !call.owner.startsWith("com/mistaboom/essence_ascendance/client/"),"server-safe common audio dependency");
        }
    }
    private static void nativeAlias(JsonObject events,String id,Set<String> visiting) {
        check(visiting.add(id),"native event aliases are acyclic: "+id);
        check(events.has(id) && events.getAsJsonObject(id).getAsJsonArray("sounds").size()>0,"valid exact-version vanilla alias "+id);
        for(var entry:events.getAsJsonObject(id).getAsJsonArray("sounds")) if(entry.isJsonObject()) {
            var sample=entry.getAsJsonObject();
            if(sample.has("type") && sample.get("type").getAsString().equals("event")) {
                String nested=sample.get("name").getAsString();
                check(!nested.contains(":") || nested.startsWith("minecraft:"),"vanilla alias stays in exact native namespace");
                nativeAlias(events,nested.replaceFirst("^minecraft:",""),visiting);
            }
        }
        visiting.remove(id);
    }
    private static ClassNode read(String loader,String name) throws Exception {
        try(var stream=TargetTestInputs.nativeClass(loader,"net/minecraft/"+name)) { var node=new ClassNode(); new ClassReader(stream).accept(node,0); return node; }
    }
    private static java.lang.reflect.Field field(Class<?> type,String name) throws Exception { var f=type.getDeclaredField(name); f.setAccessible(true); return f; }
    @SuppressWarnings("unchecked") private static <T>T allocate(Class<T> type) throws Exception {
        var unsafeType=Class.forName("sun.misc.Unsafe"); var singleton=field(unsafeType,"theUnsafe").get(null);
        return (T)unsafeType.getMethod("allocateInstance",Class.class).invoke(singleton,type);
    }
    private static void check(boolean pass,String message) { checks++; if(!pass) throw new AssertionError(message); }
}
