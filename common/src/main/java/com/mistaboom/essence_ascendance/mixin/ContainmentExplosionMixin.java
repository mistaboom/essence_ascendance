package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.utility.ExplosionContainmentService;
import com.mistaboom.essence_ascendance.utility.UtilityAuraService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Keeps native creature explosion outcomes while containing terrain, items and field owners. */
@Mixin(Explosion.class)
public abstract class ContainmentExplosionMixin {
    @Shadow @Final private Level level;
    @Unique private boolean essenceAscendance$contained;
    @Unique private List<UtilityAuraService.Match> essenceAscendance$fields = List.of();
    @Unique private Set<UUID> essenceAscendance$protectedPlayers = Set.of();

    @Inject(method = "explode", at = @At("HEAD"))
    private void essenceAscendance$beginContainment(CallbackInfo ci) {
        essenceAscendance$contained = false;
        essenceAscendance$fields = List.of();
        essenceAscendance$protectedPlayers = Set.of();
        if (!(level instanceof ServerLevel serverLevel)) return;

        Explosion explosion = (Explosion) (Object) this;
        List<UtilityAuraService.Match> fields = ExplosionContainmentService.matches(serverLevel, explosion.center());
        if (fields.isEmpty()) return;

        Set<UUID> players = new HashSet<>();
        for (UtilityAuraService.Match field : fields) players.add(field.player().getUUID());
        essenceAscendance$contained = true;
        essenceAscendance$fields = fields;
        essenceAscendance$protectedPlayers = Set.copyOf(players);
    }

    @WrapOperation(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
            require = 1)
    private boolean essenceAscendance$containedDamage(Entity entity, DamageSource source, float amount,
                                                       Operation<Boolean> original) {
        if (essenceAscendance$contained && (entity instanceof ItemEntity
                || essenceAscendance$protectedPlayers.contains(entity.getUUID()))) return false;
        return original.call(entity, source, amount);
    }

    @WrapOperation(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"),
            require = 1)
    private void essenceAscendance$containedKnockback(Entity entity, Vec3 movement, Operation<Void> original) {
        if (!essenceAscendance$contained || !essenceAscendance$protectedPlayers.contains(entity.getUUID())) {
            original.call(entity, movement);
        }
    }

    @Inject(method = "explode", at = @At("TAIL"))
    private void essenceAscendance$finishContainment(CallbackInfo ci) {
        if (!essenceAscendance$contained) return;
        Explosion explosion = (Explosion) (Object) this;
        explosion.clearToBlow();
        explosion.getHitPlayers().keySet().removeIf(player ->
                essenceAscendance$protectedPlayers.contains(player.getUUID()));
        ExplosionContainmentService.record(essenceAscendance$fields, explosion.center());
    }
}
