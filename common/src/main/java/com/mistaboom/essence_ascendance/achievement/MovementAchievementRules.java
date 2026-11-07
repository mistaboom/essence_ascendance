package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.MovementOutcome;
import java.util.*;
import java.util.function.IntConsumer;

/** Consumes bounded session checkpoints. Threshold crossings cannot replay a native revoke. */
public final class MovementAchievementRules {
    public static final int MAX_ACTORS = 256;
    private final CommittedReceiptWindow receipts = new CommittedReceiptWindow();
    private final Map<UUID, Checkpoints> actors = new HashMap<>();
    private static final class Checkpoints {
        String glide="", sprint="", surface="", airborne="";
        boolean boost3, glide1000, sprint500, vault5, rush, water32, maximum;
    }
    public boolean consume(MovementOutcome fact,long now,boolean eligible,IntConsumer award) {
        if(fact==null||!eligible)return false;
        var c=fact.context();
        if(!receipts.accept(c.actor(),c.actionId(),c.committedTick(),now))return false;
        if(!actors.containsKey(c.actor())&&actors.size()>=MAX_ACTORS)return false;
        var s=actors.computeIfAbsent(c.actor(),ignored->new Checkpoints());
        switch(fact) {
            case MovementOutcome.Glide g -> {
                if(!s.glide.equals(g.session())) {s.glide=g.session();s.boost3=s.glide1000=false;}
                if(!s.boost3&&g.boosts()>=3){s.boost3=true;award.accept(95);}
                if(!s.glide1000&&g.spatialPath()>=1000&&g.boosts()>0){s.glide1000=true;award.accept(99);}
            }
            case MovementOutcome.Sprint p -> {
                if(!s.sprint.equals(p.session())){s.sprint=p.session();s.sprint500=s.vault5=s.rush=false;}
                if(!s.vault5&&p.vaults()>=5){s.vault5=true;award.accept(101);}
                if(!s.sprint500&&p.horizontalPath()>=500){s.sprint500=true;award.accept(216);}
                if(!s.rush&&p.protectedByRush()){s.rush=true;award.accept(201);}
            }
            case MovementOutcome.Surface w -> {
                if(!s.surface.equals(w.session())){s.surface=w.session();s.water32=false;}
                if(!s.water32&&w.horizontalPath()>=32){s.water32=true;award.accept(102);}
            }
            case MovementOutcome.Momentum m -> {
                boolean maximum=m.maximum()>0&&m.amount()>=m.maximum();
                if(maximum&&!s.maximum)award.accept(100);
                s.maximum=maximum;
            }
            case MovementOutcome.AirborneHit h -> {
                if(!s.airborne.equals(h.session())){s.airborne=h.session();award.accept(145);}
            }
        }
        return true;
    }
    public void forget(UUID actor){actors.remove(actor);}
}
