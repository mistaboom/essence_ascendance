package com.mistaboom.essence_ascendance.client.audio;

import com.mistaboom.essence_ascendance.audio.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Shared native adapter; exact APIs match both supported versions. */
public final class NativeAudio implements AudioEngine.Sink {
    private final java.util.Set<net.minecraft.resources.ResourceLocation> unavailable=new java.util.HashSet<>();
    @Override public float gain(AudioCatalog.Cue cue) {
        var mc=Minecraft.getInstance();
        if(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)<=0
                || mc.options.getSoundSourceVolume(cue.playback().category())<=0) return 0;
        // Minecraft applies Master and category volume when mixing. Only gate fully muted voices here.
        return cue.playback().gain();
    }
    @Override public void caption(AudioCatalog.Cue cue,AudioEngine.Context context,long tick) { CaptionRouter.request(cue,tick); }
    @Override public AudioEngine.Voice play(AudioCatalog.Cue cue,AudioEngine.Context context,boolean loop,float gain) {
        if(unavailable.contains(cue.id())) return null;
        Instance voice=new Instance(cue,context,loop,gain);
        var manager=Minecraft.getInstance().getSoundManager();
        if(voice.resolve(manager)==null || voice.getSound()==net.minecraft.client.sounds.SoundManager.EMPTY_SOUND
                || voice.getSound()==net.minecraft.client.sounds.SoundManager.INTENTIONALLY_EMPTY_SOUND) {
            unavailable.add(cue.id());
            com.mistaboom.essence_ascendance.EssenceAscendance.LOGGER.debug("Unavailable audio cue for this resource epoch: {}",cue.id());
            return null;
        }
        manager.play(voice);
        return voice;
    }
    public void invalidated() { unavailable.clear(); }
    public static final class Instance extends AbstractTickableSoundInstance implements AudioEngine.Voice {
        final AudioCatalog.Cue cue;
        private final long started=AudioRuntime.clock();
        private boolean resolved;
        private net.minecraft.client.sounds.WeighedSoundEvents definition;
        Instance(AudioCatalog.Cue cue,AudioEngine.Context context,boolean loop,float gain) {
            super(AudioContent.event(cue),cue.playback().category(),RandomSource.create(context.occurrence() ^ (loop?Integer.toUnsignedLong(System.identityHashCode(context.source()))*0x9E3779B97F4A7C15L:0)));
            this.cue=cue; looping=loop; delay=0; volume=gain; pitch=cue.playback().pitch();
            relative=cue.audience().scope()!=AudioCatalog.Scope.WORLD;
            attenuation=cue.playback().spatial()&&!relative ? SoundInstance.Attenuation.LINEAR:SoundInstance.Attenuation.NONE;
            update(context.position(),gain,pitch);
        }
        @Override public net.minecraft.client.sounds.WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager manager) {
            if(!resolved) { definition=super.resolve(manager); resolved=true; }
            return definition;
        }
        @Override public void tick() { }
        @Override public boolean alive() { return !isStopped() && (AudioRuntime.clock()-started<3 || Minecraft.getInstance().getSoundManager().isActive(this)); }
        @Override public void update(Vec3 p,float gain,float pitch) { x=relative?0:p.x; y=relative?0:p.y; z=relative?0:p.z; volume=gain; this.pitch=pitch; }
        @Override public boolean canStartSilent() { return looping; }
        @Override public void close() { stop(); Minecraft.getInstance().getSoundManager().stop(this); }
    }
}
