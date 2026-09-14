package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.posture.PostureService;
import com.mistaboom.essence_ascendance.config.PostureBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Each catalog entry delegates to the same selected-only lifecycle and meter. */
public record PostureHandler(ResourceLocation id) implements SkillEffectHudHandler {
    @Override public void tick(SkillEffectRuntime.Context context) { PostureService.tick(context); }
    @Override public void reconcile(SkillEffectRuntime.Context context) { PostureService.reconcile(context); }
    @Override public void deactivate(SkillEffectRuntime.Context context) { PostureService.forget(context.player()); context.discardState(id()); }
    @Override public List<String> debugLines(SkillEffectRuntime.Context context) { return PostureService.diagnostics(context.player()); }
    @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
        var snapshot = PostureService.snapshot(context.player());
        return card(id, snapshot.meter(), snapshot.stacks(), snapshot.expiresAt(), context.settings().posture(),
                CombatHudActivity.active(context.player()), snapshot.recentDodge());
    }

    /** Shared card factories only; numerical presentation never feeds gameplay state. */
    static SkillEffectHudEntry card(ResourceLocation id, double meter, int stacks, long expiresAt,
                                   PostureBalanceSettings settings, boolean inCombat, boolean recentDodge) {
        if (id.equals(SkillIds.ADAPTIVE_GUARD)) {
            double resistance = stacks == 0 ? 0 : Math.max(0, Math.min(settings.adaptive().maximumStacks(), stacks + 1)
                    - settings.adaptive().minimumHits() + 1) * settings.adaptive().resistancePerStack();
            return SkillEffectHudCards.timed(id, stacks > 0, 0xFFCEA868,
                    SkillEffectHudCards.count(stacks, settings.adaptive().maximumStacks()),
                    List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.posture.adaptive", percent(resistance))),
                    "hud.essence_ascendance.guard.remaining", expiresAt);
        }
        boolean evasive = id.equals(SkillIds.EVASIVE_CURRENT);
        boolean dodged = evasive && recentDodge;
        double maximum = evasive ? settings.evasive().maximumDodgeChance() : settings.bulwark().maximumResistance();
        // Keep Evasive live at zero during combat: shared closing retention would otherwise
        // keep showing the pre-hit charge after a damaging hit has emptied the real meter.
        return SkillEffectHudCards.progress(id, dodged || inCombat && (evasive || meter > 0), evasive ? 0xFF67CABB : 0xFF729ECC,
                SkillEffectHudEntry.Text.translated("hud.essence_ascendance.percent", percent(meter)),
                List.of(dodged ? SkillEffectHudEntry.Text.translated("hud.essence_ascendance.posture.dodged")
                        : SkillEffectHudEntry.Text.translated(evasive ? "hud.essence_ascendance.posture.evasive"
                        : "hud.essence_ascendance.posture.bulwark", percent(meter * maximum))), meter);
    }
    private static String percent(double value) { return String.format(java.util.Locale.ROOT, "%.1f", value * 100); }
}
