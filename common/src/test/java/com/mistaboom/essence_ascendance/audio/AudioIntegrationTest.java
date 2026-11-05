package com.mistaboom.essence_ascendance.audio;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import com.mistaboom.essence_ascendance.visual.FlightVisualState;
import com.mistaboom.essence_ascendance.utility.ProcessingAccelerationService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Extends the existing recording-sink suite with real snapshot, output and request boundaries. */
final class AudioIntegrationTest {
    private static int checks;
    static void run() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var catalog=AudioCatalog.get();
        check(catalog.recipe("essence_ascendance:riposte_release",0,0)==null,"expiry/reset does not announce release");
        check(catalog.recipe("essence_ascendance:riposte_release",1,0)!=null,"committed Riposte releases");
        check(catalog.recipe("essence_ascendance:stored_force",0,0)==null && catalog.recipe("essence_ascendance:stored_force",0,1)!=null,"stored-force reset is silent, confirmed spend speaks");
        check(catalog.recipe("essence_ascendance:static_arc",0,0)!=null && catalog.recipe("essence_ascendance:static_arc",1,4)==null,"one arc accent for a primary discharge, no hop chorus");
        for(String repeated:List.of("kindling","frostbite","projectile_guidance","projectile_drag","projectile_intercept","death_defiance"))
            check(catalog.recipe("essence_ascendance:"+repeated)==null,"refresh/per-target/private visual cannot double playback: "+repeated);
        check(catalog.recipe("essence_ascendance:processor_complete")==null && catalog.recipe("process.complete")==null,"generic processing completion remains deliberately silent");
        check(catalog.recipe("essence_ascendance:processor_complete",1,0)==null,"native-owned brewing completion cannot overlap a custom replacement");

        var idle=MachineVisualState.IDLE_CRUCIBLE;
        var demand=new MachineVisualState.Crucible(false,List.of(),100,-1,0,List.of(),List.of(),true,false);
        var transferred=new MachineVisualState.Crucible(false,List.of(),100,-1,0,List.of(),List.of(),true,true);
        var dissolving=new MachineVisualState.Crucible(true,List.of(),100,-1,0,List.of(),List.of(),false,false);
        var recipient=UUID.randomUUID();
        for(var quiet:List.of(idle,demand,transferred,dissolving))
            check(!AudioSources.playerTransfer(quiet,recipient),"idle, demand, aggregate grants and dissolution cannot invent a destination transfer");
        check(catalog.profile("machine.focus")==null && catalog.profile("machine.crucible")==null,"independent Focus and dissolution hum profiles are removed");
        check(!AudioSources.infuserTransfer(MachineVisualState.IDLE_INFUSER),"Infuser waiting/idle never implies work");
        check(!AudioSources.thrust(FlightVisualState.WINGS|FlightVisualState.FATIGUE) && AudioSources.thrust(FlightVisualState.FATIGUE|FlightVisualState.THRUST),"flight consumes tracked thrust, not equipped skill");
        var tag=transferred.save(); check(MachineVisualState.Crucible.read(tag).transferringToInfuser(),"observer transfer fact round-trips without gameplay reconstruction");
        for(int pylons=0;pylons<=10;pylons++) {
            var positions=new ArrayList<BlockPos>(); for(int n=0;n<pylons;n++) positions.add(new BlockPos(n,0,0));
            var flowing=new MachineVisualState.Crucible(false,List.of(new MachineVisualState.PlayerStream(recipient,1,10)),100,-1,0,positions,List.of(),false,false);
            check(AudioSources.playerTransfer(flowing,recipient) && !AudioSources.playerTransfer(flowing,UUID.randomUUID()),"beam selects only a confirmed positive destination at pylon count "+pylons);
            for(boolean player:List.of(false,true)) {
                String expected=(player?"channel.resonance":"machine.resonance")+(pylons==0?"":".p"+Math.min(8,pylons));
                check(AudioSources.beamProfile(player,pylons).equals(expected) && catalog.profile(expected)!=null,"deterministic capped destination/pylon profile "+expected);
            }
        }
        check(!AudioSources.playerTransfer(new MachineVisualState.Crucible(false,List.of(new MachineVisualState.PlayerStream(recipient,1,0)),100,-1,0,List.of(),List.of(),false,false),recipient),"requested zero-rate player stream stays silent");
        var linkedTransfer=new MachineVisualState.Infuser(BlockPos.ZERO,true,5,1,100,10,null,1,MachineVisualState.IDLE_INFUSER.focus());
        check(AudioSources.infuserTransfer(linkedTransfer),"positive actual Infuser grant owns a beam without requiring an independent Focus hum");
        check(!AudioSources.infuserTransfer(new MachineVisualState.Infuser(BlockPos.ZERO,true,5,1,100,0,null,1,linkedTransfer.focus())),"processing demand without positive transfer stays silent");
        check(catalog.recipe("progression.ascend",0,0)!=null && catalog.recipe("progression.ascend",1,0)==catalog.recipe("progression.awaken"),"ceremony context deterministically selects Awakening rather than a weighted variant");
        check(catalog.recipe("progression.ascend",0,0)!=catalog.recipe("progression.ascend",1,0),"full ascendance and Awakening retain distinct authored durations");

        var sink=new AudioRuntimeTest.Recording(); var engine=new AudioEngine(sink); engine.tick(0,Vec3.ZERO);
        var body=catalog.profile("machine.resonance"); var flight=catalog.profile("mobility.thrust");
        Object owner=new Object(); var first=new AudioEngine.Source(owner,"channel/1"); var next=new AudioEngine.Source(owner,"channel/2");
        engine.reconcile(first,body,context(first,0),true,1,()->true,20);
        engine.reconcile(next,flight,context(next,0),true,1,()->true,20);
        engine.tick(1,Vec3.ZERO); check(engine.voices()==2,"two source types compose the shared lease capability");
        engine.tick(19,Vec3.ZERO); engine.reconcile(first,body,context(first,0),true,2,()->true,20);
        engine.tick(20,Vec3.ZERO); check(engine.sources()==1 && engine.voices()==1,"unrenewed source expires; renewed one survives");
        engine.reconcile(next,flight,context(next,0),true,1,()->true,20);
        engine.tick(21,Vec3.ZERO); check(engine.sources()==1,"expired revision cannot resurrect its body");
        engine.reconcile(next,flight,context(next,0),true,2,()->true,20);
        engine.tick(22,Vec3.ZERO); check(engine.voices()==2,"new session revision can resume body-only");
        engine.tick(1000,Vec3.ZERO,true); engine.tick(1001,Vec3.ZERO);
        check(engine.sources()==2,"paused UI time does not consume either source lease");
        engine.reconcile(first,body,context(first,0),false,3,()->true);
        engine.reconcile(first,body,context(first,0),true,2,()->true);
        for(long tick=1002;tick<1010;tick++) engine.tick(tick,Vec3.ZERO);
        check(engine.sources()==1,"delayed active update cannot reverse a newer stop during or after fade");
        engine.reconcile(first,body,context(first,0),true,2,()->true);
        check(engine.sources()==1,"terminal revision is remembered after native voice removal");
        engine.remove(first); check(engine.sources()==1,"old session removal cannot stop replacement session");
        engine.tick(1025,Vec3.ZERO); check(engine.sources()==0 && engine.voices()==0,"remaining lease expires after resumed simulation time");
        engine.reconcile(owner,body,context(owner,100),true,1,()->true);
        engine.tick(1026,Vec3.ZERO); check(engine.voices()==0,"tracked source outside interest stays inaudible");
        engine.tick(1037,new Vec3(100,0,0)); check(engine.voices()==1,"entering range starts an already-active body without another snapshot");
        engine.tick(1038,Vec3.ZERO); check(engine.voices()==0,"leaving range releases native ownership");
        engine.clear(); engine.tick(0,Vec3.ZERO);
        var moving=new java.util.concurrent.atomic.AtomicReference<>(Vec3.ZERO);
        engine.reconcile(owner,flight,new AudioEngine.Context(owner,Vec3.ZERO,1,moving::get),true,1,()->true);
        engine.tick(1,Vec3.ZERO); int starts=sink.plays;
        moving.set(new Vec3(100,0,0)); engine.tick(2,Vec3.ZERO);
        check(engine.voices()==0 && sink.plays==starts,"tracked movement leaves range on the next lifecycle tick without a state packet or renderer");
        moving.set(Vec3.ZERO); engine.tick(13,Vec3.ZERO);
        check(engine.voices()==0,"rapid range return honors the shared restart backoff");
        for(long now=14;now<=32;now++) engine.tick(now,Vec3.ZERO);
        check(engine.voices()==1 && sink.plays==starts+1,"returning tracked source recovers one body through shared interest policy");
        engine.clear(); engine.tick(0,Vec3.ZERO); sink.gain=0;
        for(int i=0;i<64;i++) engine.oneShot(catalog.recipe("combat.explosion"),new AudioEngine.Context(i,Vec3.ZERO,i));
        check(engine.oneShot(catalog.recipe("vitality.rescue"),context(owner,0)),"public saturation reserves admission for critical private feedback");
        engine.clear(); engine.tick(0,Vec3.ZERO);
        check(engine.oneShot(catalog.recipe("combat.guard.impact"),new AudioEngine.Context(owner,Vec3.ZERO,1))
                && engine.oneShot(catalog.recipe("combat.guard.release"),new AudioEngine.Context(owner,Vec3.ZERO,2)),
                "different meaningful cues sharing a concurrency family retain their feedback");
        check(!engine.oneShot(catalog.recipe("combat.guard.impact"),new AudioEngine.Context(owner,Vec3.ZERO,3)),
                "same semantic source/cue still obeys shared repetition policy");

        var receipts=new FeedbackReceipts(); Object screen=new Object(),reopened=new Object();
        receipts.expect(screen,7,11); check(!receipts.accept(reopened,7,11),"reused numeric menu id cannot authorize an old screen result");
        receipts.expect(screen,7,12); check(receipts.accept(screen,7,12) && !receipts.accept(screen,7,12),"accepted outcome produces one acknowledgement");
        receipts.expect(screen,7,13); receipts.forget(screen); check(!receipts.accept(screen,7,13),"screen close invalidates pending results");
        for(int i=0;i<100;i++) receipts.expect(screen,7,i); check(receipts.size()==32,"one-shot request state is bounded without continuous ownership");
        receipts.clear(); check(receipts.size()==0,"disconnect clears private requests");
        var order=new FeedbackStateCursor(); Object world=new Object();
        check(order.accept(owner,world,10,1,true) && order.accept(owner,world,11,2,true),"confirmed replacement advances the projection cursor");
        check(!order.accept(owner,world,12,1,true),"old generation cannot retire replacement even with a newer receipt time");
        check(!order.accept(owner,world,11,0,false),"duplicate/reordered stop cannot remove the current session");
        check(order.accept(owner,world,13,0,false) && !order.accept(owner,world,14,2,true),"committed terminal projection cannot revive the stopped session");
        check(order.accept(owner,world,15,3,true),"new authoritative session remains admissible");
        check(order.accept(owner,new Object(),0,1,true),"new world identity resets projection ordering");
        order.clear(); check(order.accept(owner,world,0,1,true),"disconnect/rewind clears projection epoch");
        check(com.mistaboom.essence_ascendance.client.ClientPacketDispatch.matchesWorldScope(owner,world,owner,world),"queued feedback accepts its receiving player/world incarnation");
        check(!com.mistaboom.essence_ascendance.client.ClientPacketDispatch.matchesWorldScope(owner,world,new Object(),world),"respawn cannot apply old queued activity even on the same connection/world");
        check(!com.mistaboom.essence_ascendance.client.ClientPacketDispatch.matchesWorldScope(owner,world,owner,new Object()),"world replacement rejects old queued feedback even with the same dimension id");

        List<Object> presses=new ArrayList<>();
        com.mistaboom.essence_ascendance.client.audio.AudioUi.install((semantic,control)->presses.add(control));
        var entries=new com.mistaboom.essence_ascendance.client.ui.content.EntryListView<String>(ignored->{});
        var label=net.minecraft.network.chat.Component.literal("Entry");
        entries.prepare(new com.mistaboom.essence_ascendance.client.ui.UiBounds(0,0,100,100),
                List.of(new com.mistaboom.essence_ascendance.client.ui.content.EntryListView.Entry<>("first",label,label,"first"),
                        new com.mistaboom.essence_ascendance.client.ui.content.EntryListView.Entry<>("second",label,label,"second")));
        check(presses.isEmpty(),"preparing/layout of custom lists is silent");
        entries.click(5,5,0); check(presses.size()==1,"custom list mouse activation acknowledges once");
        entries.key(257,0,0); check(presses.size()==2,"custom list keyboard activation uses the same one-shot helper");
        entries.drag(6,6,0,1,1); entries.scroll(5,5,0,1); entries.click(5,5,1);
        check(presses.size()==2,"drag, scroll and secondary-button consumption cannot masquerade as activation");

        var furnace=new FurnaceBlockEntity(BlockPos.ZERO,Blocks.FURNACE.defaultBlockState());
        var before=List.of(new ItemStack(Items.IRON_ORE),new ItemStack(Items.COAL,2),ItemStack.EMPTY);
        var fuelOnly=List.of(new ItemStack(Items.IRON_ORE),new ItemStack(Items.COAL),ItemStack.EMPTY);
        check(!ProcessingAccelerationService.completedOutput(furnace,before,fuelOnly),"fuel use is not a completed processor outcome");
        var produced=List.of(ItemStack.EMPTY,new ItemStack(Items.COAL),new ItemStack(Items.IRON_INGOT));
        check(ProcessingAccelerationService.completedOutput(furnace,before,produced),"new native furnace output is completion");
        check(!ProcessingAccelerationService.completedOutput(furnace,produced,produced),"unchanged native output cannot replay completion");
        var brewing=new BrewingStandBlockEntity(BlockPos.ZERO,Blocks.BREWING_STAND.defaultBlockState());
        check(!ProcessingAccelerationService.completedOutput(brewing,List.of(ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,new ItemStack(Items.NETHER_WART),new ItemStack(Items.BLAZE_POWDER,2)),
                List.of(ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,new ItemStack(Items.NETHER_WART),new ItemStack(Items.BLAZE_POWDER))),"brewing fuel also stays silent");
        coverage(catalog);
        System.out.println("Audio integration: "+checks+" assertions PASS (headless state/output/receipt/lifecycle coverage)");
    }
    private static AudioEngine.Context context(Object source,double x) { return new AudioEngine.Context(source,new Vec3(x,0,0),90001); }
    private static void coverage(AudioCatalog catalog) throws Exception {
        var root=JsonParser.parseString(Files.readString(Path.of(System.getProperty("essence.audio.coverage")))).getAsJsonObject();
        var skills=root.getAsJsonObject("skills");
        var actual=com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry.implementedIds().stream().map(id->id.getPath()).collect(java.util.stream.Collectors.toSet());
        check(skills.keySet().equals(actual),"ledger exactly covers implemented skill registry, not a hard-coded count");
        Set<String> semantics=new HashSet<>();
        for(var field:com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds.class.getFields()) {
            String name=field.getName(); var id=(net.minecraft.resources.ResourceLocation)field.get(null);
            semantics.add((name.startsWith("WORLD_")?"world:":"gui:")+id.getPath());
        }
        check(root.getAsJsonObject("semantics").keySet().equals(semantics),"ledger distinguishes all world and GUI semantic identities");
        for(String category:List.of("skills","semantics","surfaces")) for(var entry:root.getAsJsonObject(category).entrySet()) {
            var value=entry.getValue().getAsJsonObject(); String status=value.get("status").getAsString();
            check(Set.of("implemented","retained_vanilla","intentionally_silent","deferred","blocked").contains(status) && !value.get("reason").getAsString().isBlank(),"explicit coverage decision: "+entry.getKey());
            if(value.has("recipes")) for(var recipe:value.getAsJsonArray("recipes")) check(catalog.recipeIds().contains(recipe.getAsString()),"coverage references real shared recipe: "+recipe);
            if(value.has("profiles")) for(var profile:value.getAsJsonArray("profiles")) check(catalog.profile(profile.getAsString())!=null,"coverage references real shared lifecycle profile: "+profile);
            if(value.has("recipe")) check(catalog.recipeIds().contains(value.get("recipe").getAsString()),"semantic hook uses a resolved recipe");
        }
    }
    private static void check(boolean condition,String message) { checks++; if(!condition) throw new AssertionError(message); }
}
