package com.mistaboom.essence_ascendance.status;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Set;

/**
 * Reusable skill-owned immunity rules for specific native status effects.
 *
 * <p>The application boundary calls {@link #blocks(ServerPlayer, Holder)} before native effect
 * merging, while skill handlers may call {@link #reconcile(SkillEffectRuntime.Context)} to purge
 * an already-present blocked effect when a skill becomes effective. Binary immunities deliberately
 * carry no generated scalar: the balance engine accounts for the capability separately.</p>
 */
public final class SkillStatusImmunityService {
    private static final List<Rule> RULES = List.of(
            new Rule(SkillIds.WAYLIGHT, Set.of(MobEffects.BLINDNESS, MobEffects.DARKNESS))
    );

    private SkillStatusImmunityService() { }

    public static boolean blocks(ServerPlayer player, Holder<MobEffect> effect) {
        if (player == null || effect == null || !player.isAlive() || player.isRemoved()) return false;
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        for (Rule rule : RULES) {
            if (context.isEffective(rule.skill()) && rule.effects().contains(effect)) return true;
        }
        return false;
    }

    /** Removes effects that were already present when an immunity became effective. */
    public static int reconcile(SkillEffectRuntime.Context context) {
        ServerPlayer player = context.player();
        int removed = 0;
        for (Rule rule : RULES) {
            if (!context.isEffective(rule.skill())) continue;
            for (Holder<MobEffect> effect : rule.effects()) {
                if (player.hasEffect(effect) && player.removeEffect(effect)) removed++;
            }
        }
        return removed;
    }

    private record Rule(ResourceLocation skill, Set<Holder<MobEffect>> effects) { }
}
