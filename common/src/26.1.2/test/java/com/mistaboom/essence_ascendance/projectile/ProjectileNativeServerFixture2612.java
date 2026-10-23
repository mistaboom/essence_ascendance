package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.GlaiveTuning;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import com.mistaboom.essence_ascendance.equipment.WeaponActionDamage;
import com.mistaboom.essence_ascendance.equipment.WeaponActionRuntime;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.visual.EntityPresentationBridge2612;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
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
        System.out.println("ProjectileNativeServerFixture2612: " + checks
                + " registry-bound native arrow, attachment, physics and glaive restart checks PASS");
    }

    private static void rapidGlaive(ServerLevel level) {
        var profile = new GameProfile(UUID.randomUUID(), "GlaiveRestartProbe");
        var client = ClientInformation.createDefault();
        var player = new ServerPlayer(level.getServer(), level, profile, client);
        var transport = new Connection(PacketFlow.SERVERBOUND);
        new ServerGamePacketListenerImpl(level.getServer(), transport, player,
                new CommonListenerCookie(profile, 0, client, false));
        var channel = new EmbeddedChannel(transport);
        channel.close().syncUninterruptibly();
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
