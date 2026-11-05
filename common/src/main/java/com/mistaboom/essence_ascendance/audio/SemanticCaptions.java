package com.mistaboom.essence_ascendance.audio;

import java.util.*;

/** Semantic eligibility survives native playback. Only text actually drawn suppresses its supplement. */
public final class SemanticCaptions {
    public static final class Ticket {
        public final AudioCatalog.Cue cue;
        public final long expires;
        public final Object nativeText;
        Ticket(AudioCatalog.Cue cue,long now,double duration,Object nativeText) { this.cue=cue; expires=now+(long)duration; this.nativeText=nativeText; }
    }
    private final LinkedHashMap<String,Ticket> lines=new LinkedHashMap<>();
    private final List<Ticket> ordered=new ArrayList<>(8);
    private final Collection<Ticket> view=Collections.unmodifiableList(ordered);
    private static final Comparator<Ticket> PRIORITY=Comparator.comparingInt((Ticket t)->t.cue.playback().priority()).reversed();
    private final Set<String> rendered=new HashSet<>();
    public Ticket request(AudioCatalog.Cue cue,long now,boolean enabled,double duration,Object nativeText) {
        if(cue.subtitle().equals("none") || !enabled) return null;
        var ticket=new Ticket(cue,now,duration,nativeText);
        lines.remove(cue.subtitle());
        if(lines.size()>=8) {
            Ticket victim=null;
            for(var candidate:lines.values()) if(victim==null || candidate.cue.playback().priority()<victim.cue.playback().priority()) victim=candidate;
            if(victim.cue.playback().priority()>cue.playback().priority()) return null;
            lines.remove(victim.cue.subtitle());
        }
        lines.put(cue.subtitle(),ticket); refreshView(); return ticket;
    }
    public void beginFrame() { rendered.clear(); }
    public void nativeRendered(Object text) {
        if(text==null || lines.isEmpty()) return;
        for(var ticket:lines.values()) if(Objects.equals(ticket.nativeText,text)) rendered.add(ticket.cue.subtitle());
    }
    public boolean supplemental(Ticket ticket,boolean subtitlesEnabled) { return subtitlesEnabled && !rendered.contains(ticket.cue.subtitle()); }
    public void tick(long now,boolean enabled) {
        if(lines.values().removeIf(t->t.expires<now || !enabled)) refreshView();
    }
    public Collection<Ticket> lines() { return view; }
    private void refreshView() { ordered.clear(); ordered.addAll(lines.values()); ordered.sort(PRIORITY); }
    public void clear() { lines.clear(); ordered.clear(); rendered.clear(); }
}
