package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.movement.TraversalCapabilities;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.List;

import static com.mistaboom.essence_ascendance.movement.TraversalCapabilities.Capability.*;

/** Hook-backed passive capabilities share normal effectiveness, reconciliation and removal.
 * There is no active resource/timer to justify an always-visible HUD card. */
public final class MobilityTraversalEffects {
    private MobilityTraversalEffects() { }
    public static List<SkillEffectHandler> handlers() {
        return TraversalCapabilities.profiles().stream().<SkillEffectHandler>map(Handler::new).toList();
    }
    private record Handler(TraversalCapabilities.Profile profile) implements SkillEffectHudHandler {
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            if (!id().equals(com.mistaboom.essence_ascendance.skill.SkillIds.AQUATIC_BODY)
                    && !id().equals(com.mistaboom.essence_ascendance.skill.SkillIds.LAVABORN)
                    && !id().equals(com.mistaboom.essence_ascendance.skill.SkillIds.WATER_WALKING))
                return SkillHudEvents.card(context, id());
            var player = context.player();
            boolean active = switch (id().getPath()) {
                case "aquatic_body" -> player.isInWater() && TraversalService.fluidBody(player);
                case "lavaborn" -> TraversalService.lavaBody(player);
                case "water_walking" -> TraversalService.canStandOnFluid(player, player.level().getFluidState(player.blockPosition().below()));
                default -> SkillHudEvents.active(context, id());
            };
            String key = switch (id().getPath()) {
                case "aquatic_body" -> "submerged";
                case "lavaborn" -> "lava";
                case "water_walking" -> "surface";
                default -> "terrain";
            };
            return SkillEffectHudEntry.skill(id(), active, 0, SkillEffectHudEntry.Text.translated("hud.essence_ascendance.event." + key),
                    List.of(), SkillEffectHudEntry.Meter.none());
        }
        @Override public ResourceLocation id() { return profile.skill(); }
        @Override public void reconcile(SkillEffectRuntime.Context context) {
            if (profile.capabilities().contains(TERRAIN_CONTACT) && context.player().getTicksFrozen() > 0)
                context.player().setTicksFrozen(0);
            if (profile.capabilities().contains(WATER_BODY)) {
                // Only mining receives a native efficiency floor. Swim Speed belongs to the bonus/equipment
                // pipeline, including native off-ground scaling. Remove only this handler's old exact-ID floor.
                SkillEffectAttributes.minimum(context.player(), Attributes.WATER_MOVEMENT_EFFICIENCY, id(), 0);
                SkillEffectAttributes.minimum(context.player(), Attributes.SUBMERGED_MINING_SPEED, id(), 1);
            }
        }
        @Override public void tick(SkillEffectRuntime.Context context) { reconcile(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            if (profile.capabilities().contains(WATER_BODY)) {
                SkillEffectAttributes.minimum(context.player(), Attributes.WATER_MOVEMENT_EFFICIENCY, id(), 0);
                SkillEffectAttributes.minimum(context.player(), Attributes.SUBMERGED_MINING_SPEED, id(), 0);
            }
            context.discardState(id());
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Binary native traversal capabilities=" + profile.capabilities(),
                    "Supported body immersion=" + TraversalService.fluidBody(context.player())
                            + "; sprint surface=" + TraversalService.surfaceActive(context.player())
                            + "; lava contact protection=" + TraversalService.hideImmersionFire(context.player()),
                    "Mining efficiency only; swimming speed remains bonus/equipment-controlled. Protected lava cannot ignite; unrelated fire is not extinguished. Air supply, attacks, flight and mounts stay native.");
        }
    }
}
