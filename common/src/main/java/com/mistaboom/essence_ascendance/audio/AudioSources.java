package com.mistaboom.essence_ascendance.audio;

import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import com.mistaboom.essence_ascendance.visual.FlightVisualState;

/** Presentation facts only. Never recomputes work, channel grants, loadouts or flight permission. */
public final class AudioSources {
    private AudioSources() { }
    /** Tracked incarnations survive movement; absent tracking must not merge unrelated occurrences. */
    public static Object oneShotSource(Object trackedEntity,long occurrence) {
        return trackedEntity==null?Long.valueOf(occurrence):new AudioEngine.Source(trackedEntity,"occurrence");
    }
    public static String beamProfile(boolean player,int activePylons) {
        int count=Math.clamp(activePylons,0,8);
        return (player?"channel.resonance":"machine.resonance")+(count==0?"":".p"+count);
    }
    public static boolean playerTransfer(MachineVisualState.Crucible state,java.util.UUID player) {
        for(var stream:state.playerStreams()) if(stream.player().equals(player) && playerTransfer(stream)) return true;
        return false;
    }
    public static boolean playerTransfer(MachineVisualState.PlayerStream stream) { return stream.rate()>0 && stream.essenceMask()!=0; }
    public static boolean infuserTransfer(MachineVisualState.Infuser destination) {
        return destination.linked() && destination.throughputPerSecond()>0 && destination.transferredTypes()!=0;
    }
    public static boolean thrust(int trackedFlags) { return (trackedFlags & FlightVisualState.THRUST)!=0; }
}
