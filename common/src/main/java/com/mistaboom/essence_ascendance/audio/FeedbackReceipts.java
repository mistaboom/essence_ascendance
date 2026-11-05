package com.mistaboom.essence_ascendance.audio;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small one-shot request correlation. Screen incarnation, menu id and request id must all match. */
public final class FeedbackReceipts {
    private record Request(int menu,long sequence) { }
    private final Map<Request,Object> pending=new LinkedHashMap<>();
    public void expect(Object owner,int menu,long sequence) {
        pending.put(new Request(menu,sequence),owner);
        while(pending.size()>32) pending.remove(pending.keySet().iterator().next());
    }
    public boolean accept(Object owner,int menu,long sequence) {
        Object expected=pending.remove(new Request(menu,sequence));
        return expected!=null && expected==owner;
    }
    public void forget(Object owner) { pending.values().removeIf(value->value==owner); }
    public void clear() { pending.clear(); }
    public int size() { return pending.size(); }
}
