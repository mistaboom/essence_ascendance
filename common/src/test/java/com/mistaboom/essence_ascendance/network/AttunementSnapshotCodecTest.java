package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.attunement.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.*;

/** Uses the actual game ByteBuf and registered payload codec, with no substitute protocol APIs. */
public final class AttunementSnapshotCodecTest {
    private static int assertions;
    public static void main(String[] args) {
        String category="essence_ascendance:offense";
        var action=new AttunementContribution("server:root:12","deal_damage",category,"minecraft:zombie",42.5,1.75,.1,1.2,8_925,"\u00a7 real source diagnostic");
        var method=new AttunementSnapshot.Method("deal_damage","attunement.essence_ascendance.method.deal_damage","attunement.essence_ascendance.method.deal_damage.description",123456789,true);
        var snapshot=new AttunementSnapshot("essence_ascendance:dormant_to_awakened",2,1,false,List.of(new AttunementSnapshot.Category(category,AttunementLedger.SCALE,100_000,1.75,List.of(method),List.of(action)),new AttunementSnapshot.Category("essence_ascendance:defense",0,100_000,1,List.of(),List.of())));
        var direct=buffer();AttunementSnapshotCodec.write(direct,snapshot);
        byte[] first=bytes(direct);
        check(AttunementSnapshotCodec.read(direct).equals(snapshot)&&direct.readableBytes()==0,"Snapshot exact round trip including doubles, method metadata and rejection text");direct.release();
        var deterministic=buffer();AttunementSnapshotCodec.write(deterministic,snapshot);check(Arrays.equals(first,bytes(deterministic)),"Deterministic wire bytes");deterministic.release();
        var progress=new PlayerEssenceSyncPayload.ProgressState(PlayerEssenceSyncPayload.ProgressStatus.AVAILABLE,"essence_ascendance:awakened",0,0,0,0,1,2,0,List.of(),true,false);
        var payload=new PlayerEssenceSyncPayload(PlayerEssenceSyncPayload.CURRENT_SCHEMA_VERSION,73,"essence_ascendance:dormant","essence_ascendance:generated",List.of(),List.of(),List.of("test:milestone"),List.of(),List.of(),List.of(),snapshot,progress);
        var full=buffer();PlayerEssenceSyncPayload.CODEC.encode(full,payload);
        check(PlayerEssenceSyncPayload.CODEC.decode(full).equals(payload)&&full.readableBytes()==0,"Full server progression payload round trip preserves revision and seals");full.release();
        var maximum=new AttunementSnapshot("",0,1,true,List.of(snapshot.categories().getFirst()));var max=buffer();AttunementSnapshotCodec.write(max,maximum);
        check(AttunementSnapshotCodec.read(max).maximumTier(),"Maximum-tier constellation survives protocol");max.release();
        var negative=buffer();negative.writeUtf("");negative.writeVarInt(-1);rejects(() -> AttunementSnapshotCodec.read(negative));negative.release();
        var excess=buffer();excess.writeUtf("");excess.writeVarInt(0);excess.writeVarInt(0);excess.writeBoolean(false);excess.writeVarInt(129);rejects(() -> AttunementSnapshotCodec.read(excess));excess.release();
        var fraction=header();fraction.writeUtf(category);fraction.writeLong(AttunementLedger.SCALE+1);rejects(() -> AttunementSnapshotCodec.read(fraction));fraction.release();
        var negativeTarget=header();negativeTarget.writeUtf(category);negativeTarget.writeLong(1);negativeTarget.writeLong(-1);rejects(() -> AttunementSnapshotCodec.read(negativeTarget));negativeTarget.release();
        rejects(() -> new AttunementSnapshot("test",1,2,false,List.of(snapshot.categories().getFirst(),snapshot.categories().getFirst())));
        rejects(() -> new AttunementSnapshot.Category(category,1,100,1,List.of(method,method),List.of()));
        var invalidMultiplier=header();invalidMultiplier.writeUtf(category);invalidMultiplier.writeLong(1);invalidMultiplier.writeLong(100);invalidMultiplier.writeDouble(Double.NaN);rejects(() -> AttunementSnapshotCodec.read(invalidMultiplier));invalidMultiplier.release();
        var methodBound=header();methodBound.writeUtf(category);methodBound.writeLong(1);methodBound.writeLong(100);methodBound.writeDouble(1);methodBound.writeVarInt(129);rejects(() -> AttunementSnapshotCodec.read(methodBound));methodBound.release();
        var recentBound=header();recentBound.writeUtf(category);recentBound.writeLong(1);recentBound.writeLong(100);recentBound.writeDouble(1);recentBound.writeVarInt(0);recentBound.writeVarInt(33);rejects(() -> AttunementSnapshotCodec.read(recentBound));recentBound.release();
        var truncated=buffer();truncated.writeBytes(Arrays.copyOf(first,first.length-1));rejects(() -> AttunementSnapshotCodec.read(truncated));truncated.release();
        System.out.println("AttunementSnapshotCodecTest: "+assertions+" checks PASS");
    }
    private static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    private static RegistryFriendlyByteBuf header(){var b=buffer();b.writeUtf("");b.writeVarInt(1);b.writeVarInt(0);b.writeBoolean(false);b.writeVarInt(1);return b;}
    private static byte[] bytes(RegistryFriendlyByteBuf b){byte[] bytes=new byte[b.readableBytes()];b.getBytes(b.readerIndex(),bytes);return bytes;}
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private static void rejects(Runnable action){try{action.run();}catch(RuntimeException expected){assertions++;return;}throw new AssertionError("Malformed wire state accepted");}
}
