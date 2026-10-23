package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.GlaiveTuning;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import com.mistaboom.essence_ascendance.equipment.WeaponActionDamage;
import com.mistaboom.essence_ascendance.equipment.WeaponActionRuntime;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.skill.SkillActivationPolicy;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.utility.ExplosionContainmentService;
import com.mistaboom.essence_ascendance.visual.EntityPresentationBridge2612;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Registry-bound native arrow/attachment probe for the real 26.1.2 server lifecycle. */
public final class ProjectileNativeServerFixture2612 {
    private static int checks;

    private ProjectileNativeServerFixture2612() { }

    public static void run(ServerLevel level) throws Exception {
        var magicBolt = ProjectileContent.MAGIC_BOLT.get();
        check(BuiltInRegistries.ENTITY_TYPE.getKey(magicBolt).equals(
                Identifier.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "magic_bolt")),
                "Magic bolt retains its saved entity ID");
        check(magicBolt.canSerialize(), "Magic bolt remains saveable");

        var gravity = Entity.class.getDeclaredMethod("applyGravity");
        gravity.setAccessible(true);
        var correction = Arrays.stream(AbstractArrow.class.getDeclaredMethods())
                .filter(method -> method.getName().contains("slowNativePhysics"))
                .findFirst().orElseThrow();
        correction.setAccessible(true);
        for (AbstractArrow arrow : new AbstractArrow[]{new Arrow(EntityType.ARROW, level),
                new SpectralArrow(EntityType.SPECTRAL_ARROW, level)}) {
            var access = (ProjectileStateAccess) arrow;
            check(access.essenceAscendance$flightScale() == 1,
                    "Real loader arrow attachment starts at ordinary flight speed");
            access.essenceAscendance$flightScale(.125F);
            check(access.essenceAscendance$flightScale() == .125F
                            && EntityPresentationBridge2612.get(arrow).arrowScale() == .125F,
                    "Native arrow presentation attachment stores server-authored flight scale");
            access.essenceAscendance$state(new ProjectileState(ProjectileSource.RANGED_PHYSICAL,
                    ProjectilePath.NONE, new UUID(1, 1), new UUID(0, 0),
                    Identifier.parse("minecraft:overworld"), 0,
                    ProjectileBalanceSettings.defaults(), 0));
            var control = ProjectileControlService.state(arrow);
            control.dragFactor = .125;
            Vec3 input = new Vec3(.2, .015, 0);
            arrow.setDeltaMovement(input);
            control.physicsInput = input;
            arrow.setDeltaMovement(input.scale(.99F));
            gravity.invoke(arrow);
            check(Math.abs(arrow.getDeltaMovement().y - (input.y * .99F - .05)) < 1e-9,
                    "Native arrow gravity still applies its original full-tick fall");
            correction.invoke(arrow, new CallbackInfo("tick", false));
            Vec3 expected = input.scale(Math.pow(.99F, .125)).add(0, -.05 * .125 * .125, 0);
            check(arrow.getDeltaMovement().distanceTo(expected) < 1e-9 && control.physicsInput == null,
                    "Transformed native arrow physics applies server flight scale");
            access.essenceAscendance$flightScale(1);
            check(access.essenceAscendance$flightScale() == 1,
                    "Native attachment releases its slowed flight scale");
        }
        rapidGlaive(level);
        nativeExplosion(level);
        System.out.println("ProjectileNativeServerFixture2612: " + checks
                + " registry-bound native arrow, attachment, physics, glaive and explosion checks PASS");
    }

    @SuppressWarnings("unchecked")
    private static void nativeExplosion(ServerLevel level) throws ReflectiveOperationException {
        // Loading this class forces Mixin to resolve every explosion selector, including
        // NeoForge's hurtEntities() bridge and its hurtEntities(List) implementation.
        var methods = Arrays.stream(ServerExplosion.class.getDeclaredMethods()).map(method -> method.getName()).toList();
        for (String hook : List.of("explosionGuardHit", "explosionGuardImpulse", "explosionGuardPacket",
                "beginContainment", "containedTerrain", "containedDamage", "containedKnockback", "finishContainment")) {
            check(methods.stream().anyMatch(name -> name.contains(hook)),
                    "Real ServerExplosion contains transformed " + hook + " hook");
        }
        var playerList = level.getServer().getPlayerList();
        check(playerList.getPlayers().isEmpty(), "Explosion regression runs only in its isolated fixture server");
        // The public view is unmodifiable on NeoForge. Register this fixture-only probe in
        // the native indexes without invoking the unrelated login/handshake lifecycle.
        var playersField = PlayerList.class.getDeclaredField("players");
        var byIdField = PlayerList.class.getDeclaredField("playersByUUID");
        playersField.setAccessible(true);
        byIdField.setAccessible(true);
        var online = (List<ServerPlayer>) playersField.get(playerList);
        var byId = (Map<UUID, ServerPlayer>) byIdField.get(playerList);
        // ServerStarted precedes ordinary entity-section visibility updates. Temporarily
        // expose the fixture chunk to native spatial queries, then restore its exact state.
        var fixtureChunk = new ChunkPos(0, 0);
        level.getChunk(0, 0);
        var managerField = ServerLevel.class.getDeclaredField("entityManager");
        var visibilityField = PersistentEntitySectionManager.class.getDeclaredField("chunkVisibility");
        managerField.setAccessible(true);
        visibilityField.setAccessible(true);
        var entityManager = (PersistentEntitySectionManager<Entity>) managerField.get(level);
        var visibility = (Map<Long, Visibility>) visibilityField.get(entityManager);
        var priorVisibility = visibility.getOrDefault(fixtureChunk.pack(), Visibility.HIDDEN);
        var profile = new GameProfile(UUID.randomUUID(), "ExplosionProbe");
        var client = ClientInformation.createDefault();
        var player = new ServerPlayer(level.getServer(), level, profile, client);
        var channel = connectPlayer(player, profile, client);
        var data = EssenceSavedData.get(level.getServer()).getPlayerData(player.getUUID());
        Throwable failure = null;
        try {
            // Existing fixture chunks only; KEEP and no fire prohibit terrain changes.
            entityManager.updateChunkStatus(fixtureChunk, Visibility.TICKING);
            player.setPos(1, 300, 1);
            player.setInvulnerable(false);
            player.getAbilities().invulnerable = false;
            level.addNewPlayer(player);
            online.add(player);
            byId.put(player.getUUID(), player);
            check(playerList.getPlayers().contains(player) && playerList.getPlayer(player.getUUID()) == player,
                    "Native player indexes expose the disposable explosion probe");
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            check(player.connection.hasClientLoaded()
                            && !player.isInvulnerableTo(level, player.damageSources().explosion(null, null)),
                    "Native client-loaded acknowledgement removes initial player damage protection");
            check(level.getEntities((Entity) null, player.getBoundingBox().inflate(1)).contains(player),
                    "Native spatial lookup includes the disposable explosion probe");
            check(!player.isCreative() && !player.isSpectator(), "Native explosion probe is a survival player");
            Vec3 center = new Vec3(-1, player.getEyeY(), 1);
            Vec3 before = new Vec3(.1, -.05, .02);

            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            player.setDeltaMovement(before);
            float health = player.getHealth();
            var ordinary = explosion(level, player, center);
            ordinary.explode();
            Vec3 packet = ordinary.getHitPlayers().get(player);
            check(player.getHealth() > 0 && player.getHealth() < health,
                    "Unprotected native explosion executes actual player damage: health " + health
                            + " -> " + player.getHealth() + ", packet impulse=" + packet);
            check(packet != null && packet.lengthSqr() > 0,
                    "Unprotected native explosion creates a nonzero player packet impulse");
            check(packet.distanceToSqr(player.getDeltaMovement().subtract(before)) < 1e-10,
                    "Native explosion push and player packet retain the same velocity delta");

            data.setTier(AscendanceTiers.TRANSCENDENT);
            SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);
            data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.CONTAINMENT_FIELD)));
            var skill = SkillRegistry.require(SkillIds.CONTAINMENT_FIELD);
            if (skill.choiceGroup() != null) data.setLoadoutSelection(skill.choiceGroup(), skill.id());
            else if (skill.activationPolicy() == SkillActivationPolicy.TOGGLE) data.setLoadoutSelection(skill.id(), skill.id());
            SkillEffectRuntime.refresh(player);
            check(SkillEffectRuntime.context(player).isEffective(SkillIds.CONTAINMENT_FIELD),
                    "Real committed skill graph enables Containment Field");
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            player.setDeltaMovement(before);
            health = player.getHealth();
            int priorContained = ExplosionContainmentService.snapshot(SkillEffectRuntime.context(player)).containedThisTick();
            var contained = explosion(level, player, center);
            check(contained.explode() == 0, "Containment clears the actual native exploded-position result");
            check(player.getHealth() == health && player.getDeltaMovement().distanceToSqr(before) < 1e-10,
                    "Containment suppresses native owner damage and push");
            check(!contained.getHitPlayers().containsKey(player), "Containment removes the owner's native packet impulse");
            check(ExplosionContainmentService.snapshot(SkillEffectRuntime.context(player)).containedThisTick() == priorContained + 1,
                    "One native contained explosion records exactly one field event");

            data.clearAllSkillsForAdmin();
            SkillEffectRuntime.refresh(player);
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            player.setDeltaMovement(before);
            health = player.getHealth();
            var restored = explosion(level, player, center);
            restored.explode();
            check(player.getHealth() < health && restored.getHitPlayers().containsKey(player)
                            && player.getDeltaMovement().distanceToSqr(before) > 0,
                    "Removing Containment immediately restores native damage, push and packet impulse");
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            cleanupAfter(failure,
                    data::clearAllSkillsForAdmin,
                    () -> SkillEffectRuntime.forget(player),
                    () -> EquipmentDamageService.forget(player),
                    () -> online.remove(player),
                    () -> byId.remove(player.getUUID(), player),
                    () -> level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED),
                    () -> entityManager.updateChunkStatus(fixtureChunk, priorVisibility),
                    channel::finishAndReleaseAll);
        }
    }

    private static void cleanupAfter(Throwable originalFailure, Runnable... actions) {
        Throwable failure = originalFailure;
        for (var action : actions) {
            try {
                action.run();
            } catch (RuntimeException | Error exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (originalFailure == null && failure != null) {
            throw new AssertionError("Explosion fixture cleanup failed", failure);
        }
    }

    private static ServerExplosion explosion(ServerLevel level, ServerPlayer player, Vec3 center) {
        return new ServerExplosion(level, null, player.damageSources().explosion(null, null),
                new ExplosionDamageCalculator(), center, 2, false, Explosion.BlockInteraction.KEEP);
    }

    private static EmbeddedChannel connectPlayer(ServerPlayer player, GameProfile profile, ClientInformation client) {
        var transport = new Connection(PacketFlow.SERVERBOUND);
        new ServerGamePacketListenerImpl(player.level().getServer(), transport, player,
                new CommonListenerCookie(profile, 0, client, false));
        var channel = new EmbeddedChannel(transport);
        channel.close().syncUninterruptibly();
        return channel;
    }

    private static void rapidGlaive(ServerLevel level) {
        var profile = new GameProfile(UUID.randomUUID(), "GlaiveRestartProbe");
        var client = ClientInformation.createDefault();
        var player = new ServerPlayer(level.getServer(), level, profile, client);
        var channel = connectPlayer(player, profile, client);
        check(player.connection != null && !channel.isOpen()
                        && !player.connection.isAcceptingMessages(),
                "Native 26 fixture player has a real closed in-memory packet channel");
        var stack = new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get());
        EquipmentTierData.setTier(stack, EquipmentTier.LATENT);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        TwoHandedEquipmentService.refresh(player);
        try {
            check(TwoHandedEquipmentService.decision(player).actionWeapon(),
                    "Native 26 player recognizes the registered Glaive action policy");
            check(WeaponActionRuntime.attempt(player, false), "Native 26 swipe accepts");
            var swipe = WeaponActionRuntime.snapshot(player);
            float early = WeaponActionDamage.snapshot(player).readiness();
            check(early < GlaiveTuning.STRONG_HIT_THRESHOLD
                            && Math.abs(early - swipe.readiness(WeaponActionRuntime.now(player) + .5)) < 1e-6,
                    "Native 26 readiness mixin reports the prior action's partial strength");
            check(WeaponActionRuntime.attempt(player, true),
                    "Native 26 stab restarts while the swipe strength meter is low");
            var stab = WeaponActionRuntime.snapshot(player);
            check(stab.actionId() > swipe.actionId() && stab.mode() == WeaponActionRuntime.Mode.STAB
                            && player.getAttackStrengthScale(0) == 0,
                    "Native 26 replacement has a fresh identity and resets combat strength");
            long revision = TwoHandedEquipmentService.decision(player).revision();
            long sentAt = level.getGameTime();
            check(WeaponActionRuntime.input(player, false, 1, sentAt, revision)
                            && WeaponActionRuntime.snapshot(player).actionId() > stab.actionId(),
                    "Native 26 custom input can restart the opposite mode in the same tick");
            var next = WeaponActionRuntime.snapshot(player);
            check(!WeaponActionRuntime.input(player, true, 1, sentAt, revision)
                            && !WeaponActionRuntime.input(player, true, 2, sentAt - 1, revision),
                    "Native 26 duplicate and stale custom inputs cannot replay actions");
            check(WeaponActionRuntime.snapshot(player).actionId() == next.actionId(),
                    "Rejected native 26 inputs leave the current action intact");
        } finally {
            WeaponActionRuntime.clear();
            TwoHandedEquipmentService.forget(player);
            channel.finishAndReleaseAll();
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
