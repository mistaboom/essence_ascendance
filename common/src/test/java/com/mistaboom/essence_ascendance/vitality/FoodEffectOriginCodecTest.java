package com.mistaboom.essence_ascendance.vitality;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;

public final class FoodEffectOriginCodecTest {
    private static int checks;
    private static final class Effect implements FoodEffectOrigin {
        boolean food;
        final Effect hidden;
        Effect(int depth) { hidden = depth > 1 ? new Effect(depth - 1) : null; }
        int depth() { return hidden == null ? 1 : hidden.depth() + 1; }
        public boolean essenceAscendance$foodOrigin() { return food; }
        public void essenceAscendance$foodOrigin(boolean value) { food = value; }
        public FoodEffectOrigin essenceAscendance$hiddenOrigin() { return hidden; }
    }
    private static final Codec<Effect> NATIVE = Codec.intRange(1, 300).fieldOf("native_depth").codec().xmap(Effect::new, Effect::depth);
    private static final Codec<Effect> CODEC = FoodEffectOriginCodec.wrap(NATIVE);
    public static void main(String[] args) {
        checkOps(JsonOps.INSTANCE);
        checkOps(NbtOps.INSTANCE);
        System.out.println("Food effect codec persistence invariants passed: " + checks);
    }
    private static <T> void checkOps(DynamicOps<T> ops) {
        Effect effect = new Effect(3);
        effect.food = true;
        effect.hidden.hidden.food = true;
        T encoded = CODEC.encodeStart(ops, effect).getOrThrow();
        Effect restored = CODEC.parse(ops, encoded).getOrThrow();
        check(restored.food && !restored.hidden.food && restored.hidden.hidden.food, "Hidden chain provenance round trips independently");
        check(NATIVE.parse(ops, encoded).getOrThrow().depth() == 3, "Native schema remains readable without provenance wrapper");
        T ordinary = NATIVE.encodeStart(ops, new Effect(2)).getOrThrow();
        Effect plain = CODEC.parse(ops, ordinary).getOrThrow();
        check(!plain.food && !plain.hidden.food, "Unmarked native effects retain ordinary origin");
        T legacy = ops.mergeToMap(ordinary, ops.createString("essence_ascendance_food_origin"), ops.createBoolean(true)).getOrThrow();
        Effect old = CODEC.parse(ops, legacy).getOrThrow();
        check(old.food && !old.hidden.food, "Legacy root key is accepted without marking hidden effects");
        check(CODEC.parse(ops, ops.emptyMap()).error().isPresent(), "Native decoding failures are preserved");
        Effect deep = new Effect(257); Effect tail = deep;
        for (int i = 0; i < 256; i++) tail = tail.hidden;
        tail.food = true;
        Effect bounded = CODEC.parse(ops, CODEC.encodeStart(ops, deep).getOrThrow()).getOrThrow();
        for (int i = 0; i < 256; i++) bounded = bounded.hidden;
        check(!bounded.food, "Historical 256-level provenance bound is preserved");
    }
    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
}
