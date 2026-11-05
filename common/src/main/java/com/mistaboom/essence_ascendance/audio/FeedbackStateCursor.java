package com.mistaboom.essence_ascendance.audio;

/** Ordered acceptance for one authoritative source projection, before it can retire another session. */
public final class FeedbackStateCursor {
    private Object owner,world;
    private long revision=Long.MIN_VALUE,generation;
    private boolean terminal;
    public boolean accept(Object owner,Object world,long revision,long generation,boolean present) {
        boolean same=this.owner==owner && this.world==world;
        if(same && (revision<=this.revision || present && (generation<this.generation || terminal && generation==this.generation))) return false;
        if(present && generation<=0) return false;
        if(!same) { this.generation=0; terminal=false; }
        this.owner=owner; this.world=world; this.revision=revision;
        if(present) this.generation=generation;
        terminal=!present;
        return true;
    }
    public void clear() { owner=world=null; revision=Long.MIN_VALUE; generation=0; terminal=false; }
}
