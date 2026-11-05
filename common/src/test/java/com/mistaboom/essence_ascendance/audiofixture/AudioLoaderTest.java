package com.mistaboom.essence_ascendance.audiofixture;

import com.mistaboom.essence_ascendance.audio.*;

import net.minecraft.core.registries.BuiltInRegistries;

/** Explicit isolated loader fixture. Production initialization must have registered every generated cue. */
public final class AudioLoaderTest {
    public static void serverAndExit() { run(false); }
    public static void clientAndExit() { run(true); }
    private static void run(boolean client) {
        boolean afterStart=Boolean.getBoolean("essence.audio.afterServerStart");
        try {
            AudioMultiplayerAcceptance.verifyBarrierRecords();
            if(Boolean.getBoolean("essence.audio.gameplay")) {
                Class.forName("com.mistaboom.essence_ascendance.audiofixture.AudioGameplaySession")
                        .getMethod(client?"client":"server").invoke(null);
                return;
            }
            if(Boolean.getBoolean("essence.audio.multiplayer")) {
                Class.forName("com.mistaboom.essence_ascendance.audiofixture.AudioMultiplayerAcceptance")
                        .getMethod(client?"client":"server").invoke(null);
                return;
            }
            int count=0;
            for(var cue:AudioCatalog.get().cues()) {
                if(!cue.id().equals(BuiltInRegistries.SOUND_EVENT.getKey(AudioContent.event(cue)))) throw new AssertionError("Missing registered cue "+cue.id());
                count++;
            }
            if(count<6) throw new AssertionError("Runtime fallback catalog missing");
            if(client) {
                transformed(net.minecraft.client.gui.components.SubtitleOverlay.class,"essenceAscendance$observeCaption");
                transformed(net.minecraft.client.gui.components.SubtitleOverlay.class,"essenceAscendance$nativeTextDrawn");
                transformed(net.minecraft.world.level.block.entity.BlockEntity.class,"essenceAscendance$sourceChanged");
            } else {
                // A client-installed bridge here would link Minecraft.getInstance on a dedicated server.
                AudioSourceBridge.changed(null);
                // 26.1 item initializers bind at world-data reload, after mod initialization.
                // Exercise the real loader lifecycle rather than emulating a vanilla component provider.
                if(afterStart) {
                    dev.architectury.event.events.common.LifecycleEvent.SERVER_STARTED.register(server->{
                        try {
                            com.mistaboom.essence_ascendance.projectile.NativeAudioRoutingTest.run();
                            System.out.println("AudioLoaderTest: PASS; "+AudioCatalog.get().cues().size()+" registered cues; native server lifecycle and in-memory routing acceptance");
                            finishServer(server,0);
                        } catch(Throwable failure) { failure.printStackTrace(); finishServer(server,1); }
                    });
                }
                else com.mistaboom.essence_ascendance.projectile.NativeAudioRoutingTest.run();
            }
            System.out.println("AudioLoaderTest: PASS; "+count+" registered cues; "+(client?"three transformed client hooks":"dedicated-server initialization and inert source bridge")+"; no world/device/audition claim");
            if(client && Boolean.getBoolean("essence.audio.worldAcceptance")) {
                Class.forName("com.mistaboom.essence_ascendance.audiofixture.AudioWorldAcceptance").getMethod("install").invoke(null);
                return;
            }
            if(!client && afterStart) return;
            System.exit(0);
        } catch(Throwable failure) { AudioMultiplayerAcceptance.fail(failure); }
    }
    /** Exit from a separate thread only after native saving/shutdown returns; shutdown hooks join the server. */
    private static void finishServer(net.minecraft.server.MinecraftServer server,int status) {
        Thread serverThread=Thread.currentThread();
        new Thread(()->{
            try { serverThread.join(); System.exit(status); }
            catch(InterruptedException failure) { Thread.currentThread().interrupt(); System.exit(1); }
        },"EA audio fixture exit").start();
        server.halt(false);
    }
    private static void transformed(Class<?> type,String name) throws Exception {
        if(java.util.Arrays.stream(type.getDeclaredMethods()).noneMatch(m->m.getName().endsWith(name))) throw new AssertionError("Untransformed audio hook "+type);
    }
}
