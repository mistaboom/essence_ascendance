package com.mistaboom.essence_ascendance.skill.effect;

/** Native reflection selects its own damage source without consuming an enclosing secondary attribution. */
public final class SkillProcAttributionTest {
    private SkillProcAttributionTest() { }
    public static void verify() {
        Object explosiveAttribution = new Object(), returnedAttribution = new Object();
        for (Object original : new Object[] {explosiveAttribution, returnedAttribution}) {
            if (SkillProcDamageService.visibleContext(original, false) != original
                    || SkillProcDamageService.visibleContext(original, true) != null
                    || SkillProcDamageService.visibleContext(original, false) != original)
                throw new AssertionError("Nested reflection must isolate, then resume, the original payload/return attribution");
        }
        if (SkillProcDamageService.visibleContext(null, false) != null)
            throw new AssertionError("Ordinary native damage must not manufacture a secondary context");
        if (SkillProcDamageService.DamageKind.EXPLOSIVE_PAYLOAD.damageType() != net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION
                || SkillProcDamageService.DamageKind.COMBUSTION.damageType() != net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC
                || SkillProcDamageService.DamageKind.REDIRECTED_PROJECTILE.damageType() != null)
            throw new AssertionError("Payload uses native explosion defenses; existing Combustion and returned native damage keep their type contracts");
    }
}
