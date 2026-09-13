package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.attunement.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.ArrayList;

/** Bounded wire format for the read-only constellation and its recent audit trail. */
final class AttunementSnapshotCodec {
    private AttunementSnapshotCodec() {}
    static void write(RegistryFriendlyByteBuf b, AttunementSnapshot snapshot) {
        b.writeUtf(snapshot.chapterId(),128); b.writeVarInt(snapshot.requiredCategories()); b.writeVarInt(snapshot.completedCategories()); b.writeBoolean(snapshot.maximumTier()); b.writeVarInt(snapshot.categories().size());
        for (var c : snapshot.categories()) {
            b.writeUtf(c.categoryId(),128); b.writeLong(c.progress()); b.writeLong(c.target()); b.writeDouble(c.investmentMultiplier()); b.writeVarInt(c.methods().size());
            for (var m : c.methods()) { b.writeUtf(m.activityId(),128); b.writeUtf(m.labelKey(),256); b.writeUtf(m.descriptionKey(),256); b.writeLong(m.progress()); b.writeBoolean(m.available()); }
            b.writeVarInt(c.recent().size());
            for (var r : c.recent()) {
                b.writeUtf(r.actionId(),256); b.writeUtf(r.activityId(),128); b.writeUtf(r.categoryId(),128); b.writeUtf(r.sourceSignature(),256);
                b.writeDouble(r.baseValue()); b.writeDouble(r.investmentMultiplier()); b.writeDouble(r.repetitionMultiplier()); b.writeDouble(r.varietyMultiplier()); b.writeLong(r.finalContribution()); b.writeUtf(r.rejectionReason(),256);
            }
        }
    }
    static AttunementSnapshot read(RegistryFriendlyByteBuf b) {
        String chapter = b.readUtf(128); int required = count(b,128), completed = count(b,128); boolean maximum = b.readBoolean(); int categories = count(b,128);
        var rows = new ArrayList<AttunementSnapshot.Category>();
        for (int i=0; i<categories; i++) {
            String category=b.readUtf(128); long progress=bounded(b.readLong()),target=b.readLong();
            if(target<0||target>1_000_000_000_000L)throw new IllegalArgumentException("Invalid Attunement target");
            double investment=finite(b.readDouble());
            if(investment<1||investment>5)throw new IllegalArgumentException("Invalid Attunement acceleration"); int methods=count(b,128);
            var methodRows=new ArrayList<AttunementSnapshot.Method>();
            for (int j=0;j<methods;j++) methodRows.add(new AttunementSnapshot.Method(b.readUtf(128),b.readUtf(256),b.readUtf(256),bounded(b.readLong()),b.readBoolean()));
            int recent=count(b,32); var actions=new ArrayList<AttunementContribution>();
            for(int j=0;j<recent;j++) actions.add(new AttunementContribution(b.readUtf(256),b.readUtf(128),b.readUtf(128),b.readUtf(256),finite(b.readDouble()),finite(b.readDouble()),finite(b.readDouble()),finite(b.readDouble()),bounded(b.readLong()),b.readUtf(256)));
            rows.add(new AttunementSnapshot.Category(category,progress,target,investment,methodRows,actions));
        }
        return new AttunementSnapshot(chapter,required,completed,maximum,rows);
    }
    private static int count(RegistryFriendlyByteBuf b,int max) { int n=b.readVarInt(); if(n<0||n>max)throw new IllegalArgumentException("Invalid Attunement list bound"); return n; }
    private static long bounded(long value) { if(value<0||value>AttunementLedger.SCALE)throw new IllegalArgumentException("Invalid Attunement fraction");return value; }
    private static double finite(double value) { if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid Attunement multiplier");return value; }
}
