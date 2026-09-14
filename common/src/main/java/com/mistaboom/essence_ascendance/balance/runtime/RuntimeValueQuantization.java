package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.stat.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Published values use their gameplay unit, not one global whole-number cast. */
public final class RuntimeValueQuantization {
    private RuntimeValueQuantization() {}
    public static double down(double value, double step) {
        if(!Double.isFinite(value)||value<0||!Double.isFinite(step)||step<=0)
            throw new IllegalArgumentException("Quantization requires finite nonnegative values and a positive unit");
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(step),0,RoundingMode.FLOOR)
                .multiply(BigDecimal.valueOf(step)).doubleValue();
    }
    public static double statStep(StatUnit unit) {
        return switch(unit) {
            case PERCENT, LEVELS -> 1;
            case HEARTS -> .5;
            case HEARTS_PER_SECOND -> .01;
            case BLOCKS, SECONDS, FLAT -> .1;
        };
    }
    public static RuntimeBalanceDefinition apply(RuntimeBalanceDefinition runtime) {
        JsonObject json=runtime.toJson(); apply(json); return RuntimeBalanceDefinition.fromJson(json);
    }
    static void apply(JsonObject json) {
        boolean parity=json.getAsJsonObject("composition").has("equipment_apex_parity")
                &&json.getAsJsonObject("composition").get("equipment_apex_parity").getAsDouble()==1;
        for(var entry:json.getAsJsonObject("equipment").entrySet()) {
            JsonObject row=entry.getValue().getAsJsonObject();
            for(String field:List.of("fullSetArmor","fullSetToughness","meleeDamage","rangedDamage","magicDamage"))
                row.addProperty(field,physical(row.get(field).getAsDouble(),1,parity));
            row.addProperty("miningSpeed",Math.max(1,physical(row.get("miningSpeed").getAsDouble(),1,parity)));
            for(String field:List.of("meleeAttackSpeed","rangedAttackSpeed","magicCastSpeed"))
                row.addProperty(field,Math.max(.1,physical(row.get(field).getAsDouble(),.1,parity)));
        }
        JsonObject bonuses=json.getAsJsonObject("statMaxBonuses");
        for(var stat:EssenceStatRegistry.values()) {
            var track=json.getAsJsonObject("balanceProfile").getAsJsonObject("bonusTracks").getAsJsonObject(stat.id().toString());
            // Native collision-derived completion retains its exact additive value even with a continuous slider.
            // Rounding .899999976 down to .8 would discard the measured completion boundary.
            if (!track.getAsJsonObject("inputs").has("native_base_step_height"))
                bonuses.addProperty(stat.id().toString(),down(bonuses.get(stat.id().toString()).getAsDouble(),statStep(stat.unit())));
        }
        BonusTrackGenerator.synchronizeMaxima(json);
        JsonObject shield=json.getAsJsonObject("shield");
        shield.addProperty("baseReflectionPercent",down(shield.get("baseReflectionPercent").getAsDouble(),1));
        for(var entry:shield.getAsJsonObject("innateReflectionBonus").entrySet())entry.setValue(new com.google.gson.JsonPrimitive(down(entry.getValue().getAsDouble(),1)));
        for(var entry:shield.getAsJsonObject("blockAmplification").entrySet())entry.setValue(new com.google.gson.JsonPrimitive(down(entry.getValue().getAsDouble(),.01)));
    }
    private static double physical(double value,double step,boolean parity) {
        return parity?BigDecimal.valueOf(value).divide(BigDecimal.valueOf(step),0,RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(step)).doubleValue():down(value,step);
    }
    static void requireExactValuesOnGrid(JsonObject json) {
        JsonObject quantized=json.deepCopy();apply(quantized);
        if(!quantized.equals(json))throw new IllegalArgumentException("Exact runtime override is off its gameplay unit grid: equipment points use whole units, attack rates tenths, percentages whole percentage points, hearts half-hearts, regeneration hundredths. Supply the exact playable value.");
    }
}
