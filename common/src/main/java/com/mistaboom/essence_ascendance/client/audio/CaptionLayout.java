package com.mistaboom.essence_ascendance.client.audio;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import java.util.List;

/** Native caption geometry is observed, never moved. Supplemental text shares its right-hand area. */
public final class CaptionLayout {
    private CaptionLayout() { }
    public static UiBounds place(int screenWidth,int textY,int textWidth,int lineHeight,List<UiBounds> occupied) {
        var bounds=new UiBounds(screenWidth-textWidth-11,textY-3,textWidth+10,lineHeight+6);
        boolean moved;
        do {
            moved=false;
            for(var obstacle:occupied) if(bounds.intersects(obstacle)) {
                bounds=new UiBounds(bounds.x(),obstacle.y()-4-bounds.height(),bounds.width(),bounds.height());
                moved=true;
            }
        } while(moved);
        return bounds;
    }

    /** Move the right-hand card stack's anchor upward, without changing any observed caption rectangle. */
    public static int cardStackBottom(int normalBottom,int left,int right,UiBounds[] captions) {
        int bottom=normalBottom;
        for(var caption:captions) if(caption!=null && caption.width()>0 && caption.height()>0
                && caption.x()<right && caption.right()>left) bottom=Math.min(bottom,caption.y()-4);
        return bottom;
    }

    /** One bounded footprint for the actual native rows, after native preference/range/expiry filtering. */
    public static UiBounds union(UiBounds first,UiBounds next) {
        if(first==null) return next;
        int left=Math.min(first.x(),next.x()),top=Math.min(first.y(),next.y());
        return new UiBounds(left,top,Math.max(first.right(),next.right())-left,Math.max(first.bottom(),next.bottom())-top);
    }
}
