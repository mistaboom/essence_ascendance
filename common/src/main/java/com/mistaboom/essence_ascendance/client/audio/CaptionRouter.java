package com.mistaboom.essence_ascendance.client.audio;

import com.mistaboom.essence_ascendance.audio.*;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.client.Minecraft;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import java.util.*;

/** Eight semantic lines at most. The native overlay's actual draw is the sole suppression signal. */
public final class CaptionRouter {
    private static final SemanticCaptions STATE=new SemanticCaptions();
    private record Text(int available,int width,net.minecraft.util.FormattedCharSequence fitted) { }
    private static final Map<SemanticCaptions.Ticket,Text> TEXT=new IdentityHashMap<>();
    private static final List<com.mistaboom.essence_ascendance.client.ui.UiBounds> OCCUPIED=new ArrayList<>();
    private static final int OCCUPIED_LIMIT=com.mistaboom.essence_ascendance.network.ChannelSessionsPayload.MAX_LINKS+1;
    // Native subtitles are deferred on modern clients. Use one completed frame on both versions so the
    // existing HUD stage can animate around actual draws without moving above screens or filtering twice.
    // Slot zero is the union of native backdrops; the other eight slots are drawn supplemental lines.
    private static UiBounds[] captionBounds=new UiBounds[9],previousCaptionBounds=new UiBounds[9];
    private static int supplementalBounds,viewportWidth,viewportHeight;
    //? if >=26.1.2
    /*private static GuiGraphicsExtractor frameGraphics;*/
    //? if <26.1.2
    private static GuiGraphics frameGraphics;
    private static long now() { return System.nanoTime()/1_000_000; }
    private static double duration() { return 3000*Minecraft.getInstance().options.notificationDisplayTime().get(); }
    private CaptionRouter() { }
    public static boolean enabled() {
        return Minecraft.getInstance().options.showSubtitles().get();
    }
    public static SemanticCaptions.Ticket request(AudioCatalog.Cue cue,long tick) {
        if(!enabled()) return null;
        var definition=Minecraft.getInstance().getSoundManager().getSoundEvent(cue.id());
        Object nativeText=cue.audience().scope()==AudioCatalog.Scope.WORLD && definition!=null?definition.getSubtitle():null;
        return STATE.request(cue,now(),enabled(),duration(),nativeText);
    }
    /** Relative cues use neutral captions. World cues retain native range, direction and lifetime. */
    public static boolean nativeSubtitle(NativeAudio.Instance instance, Component subtitle,float range) {
        return !instance.isRelative();
    }
    public static void beginFrame() {
        STATE.beginFrame(); OCCUPIED.clear(); frameGraphics=null;
        var reusable=previousCaptionBounds; previousCaptionBounds=captionBounds; captionBounds=reusable;
        Arrays.fill(captionBounds,null); supplementalBounds=0;
    }
    /** An opaque legacy screen can cover the native HUD already drawn beneath it. */
    public static void screenStarted() { STATE.beginFrame(); OCCUPIED.clear(); Arrays.fill(captionBounds,null); supplementalBounds=0; }
    public static void reserve(com.mistaboom.essence_ascendance.client.ui.UiBounds bounds) { if(OCCUPIED.size()<OCCUPIED_LIMIT) OCCUPIED.add(bounds); }
    /** Observation only: native text, background, arrows, coordinates and lifetime stay vanilla-owned. */
    public static void nativeBounds(int width,int height,UiBounds bounds) {
        viewport(width,height);
        captionBounds[0]=CaptionLayout.union(captionBounds[0],bounds);
    }
    public static int cardStackBottom(int width,int height,int left,int right,int normalBottom) {
        viewport(width,height);
        return CaptionLayout.cardStackBottom(normalBottom,left,right,previousCaptionBounds);
    }
    private static void viewport(int width,int height) {
        if(viewportWidth==width && viewportHeight==height) return;
        viewportWidth=width; viewportHeight=height;
        Arrays.fill(captionBounds,null); Arrays.fill(previousCaptionBounds,null); supplementalBounds=0;
    }
    //? if >=26.1.2
    /*public static void captureFrame(GuiGraphicsExtractor graphics) { viewport(graphics.guiWidth(),graphics.guiHeight()); frameGraphics=graphics; }*/
    //? if <26.1.2
    public static void captureFrame(GuiGraphics graphics) { viewport(graphics.guiWidth(),graphics.guiHeight()); frameGraphics=graphics; }
    public static void finishFrame() {
        var graphics=frameGraphics; frameGraphics=null;
        if(graphics!=null) render(graphics);
    }
    public static Component nativeRendered(Component text) { STATE.nativeRendered(text); return text; }
    public static void tick(long tick) { STATE.tick(now(),enabled()); TEXT.keySet().removeIf(t->!STATE.lines().contains(t)); }
    public static void clear() {
        STATE.clear(); TEXT.clear(); OCCUPIED.clear(); frameGraphics=null;
        Arrays.fill(captionBounds,null); Arrays.fill(previousCaptionBounds,null); supplementalBounds=0;
        viewportWidth=viewportHeight=0;
    }
    //? if >=26.1.2
    /*public static void render(GuiGraphicsExtractor graphics) {*/
    //? if <26.1.2
    public static void render(GuiGraphics graphics) {
        if(!enabled() || STATE.lines().isEmpty()) return;
        viewport(graphics.guiWidth(),graphics.guiHeight());
        var mc=Minecraft.getInstance();
        // Vanilla's bottom row ends 30 pixels above the viewport bottom. Neutral supplemental lines use
        // that same right-hand area, or continue immediately above the actual native caption group.
        int bottom=captionBounds[0]==null?graphics.guiHeight()-30:captionBounds[0].y()-4;
        int y=bottom-mc.font.lineHeight-3;
        for(var ticket:STATE.lines()) {
            if(!STATE.supplemental(ticket,true)) continue;
            int available=Math.max(0,graphics.guiWidth()-24);
            var cached=TEXT.get(ticket);
            if(cached==null || cached.available()!=available) {
                Component text=EssenceText.audioSubtitle(ticket.cue.subtitle());
                int measured=Math.min(mc.font.width(text),available);
                cached=new Text(available,measured,com.mistaboom.essence_ascendance.client.ui.StyledTextLayout.fit(mc.font,text,measured));
                if(TEXT.size()>=8) TEXT.clear();
                TEXT.put(ticket,cached);
            }
            int width=cached.width();
            var bounds=CaptionLayout.place(graphics.guiWidth(),y,width,mc.font.lineHeight,OCCUPIED);
            if(bounds.y()<16) break;
            int x=bounds.x()+5; y=bounds.y()+3;
            if(supplementalBounds<8) captionBounds[++supplementalBounds]=bounds;
            graphics.fill(bounds.x(),bounds.y(),bounds.right(),bounds.bottom(),0xD0000000);
            //? if >=26.1.2
            /*graphics.text(mc.font,cached.fitted(),x,y,0xFFFFFFFF,false);*/
            //? if <26.1.2
            graphics.drawString(mc.font,cached.fitted(),x,y,0xFFFFFFFF,false);
            y-=mc.font.lineHeight+8; if(y<16) break;
        }
    }
}
