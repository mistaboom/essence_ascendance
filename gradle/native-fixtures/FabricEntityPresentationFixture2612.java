package com.mistaboom.essence_ascendance.fixture;

import com.mistaboom.essence_ascendance.fabric.FabricEntityPresentationSync2612;
import com.mistaboom.essence_ascendance.projectile.ProjectileStateAccess;
import com.mistaboom.essence_ascendance.visual.EntityPresentationBridge2612;
import com.mistaboom.essence_ascendance.visual.FlightVisualState;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;

import java.util.UUID;

/** Registry-bound Fabric probe for the player-join presentation path that failed in the live client. */
public final class FabricEntityPresentationFixture2612 implements ModInitializer {
    private static int checks;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var level = server.overworld();
                var field = FabricEntityPresentationSync2612.class.getDeclaredField("STATE");
                field.setAccessible(true);
                var type = (AttachmentType<?>) field.get(null);
                check(type.isSynced() && !type.isPersistent(),
                        "Presentation state is synchronized and transient");

                var player = new ServerPlayer(server, level,
                        new GameProfile(UUID.randomUUID(), "PresentationFixture"),
                        ClientInformation.createDefault());
                var flight = (FlightVisualState) player;
                check(flight.essenceAscendance$flightFlags() == 0,
                        "New player has default flight state");
                flight.essenceAscendance$flightFlags(3);
                check(flight.essenceAscendance$flightFlags() == 3
                                && EntityPresentationBridge2612.get(player).flightFlags() == 3,
                        "Player flight flags survive the actual Fabric attachment path");
                flight.essenceAscendance$boostTick(42);
                check(flight.essenceAscendance$boostTick() == 42,
                        "Player boost tick survives the actual Fabric attachment path");
                flight.essenceAscendance$flightFlags(0);
                check(flight.essenceAscendance$flightFlags() == 0,
                        "Player flight flags can be cleared during join/disconnect cleanup");

                var arrow = new Arrow(EntityType.ARROW, level);
                var spectral = new SpectralArrow(EntityType.SPECTRAL_ARROW, level);
                var arrowState = (ProjectileStateAccess) arrow;
                check(arrowState.essenceAscendance$flightScale() == 1,
                        "New arrow has default flight scale");
                arrowState.essenceAscendance$flightScale(.25F);
                check(arrowState.essenceAscendance$flightScale() == .25F
                                && EntityPresentationBridge2612.get(arrow).arrowScale() == .25F,
                        "Arrow scale uses Fabric attachment state");
                check(((ProjectileStateAccess) spectral).essenceAscendance$flightScale() == 1,
                        "State is independent between entities");

                System.out.println("FabricEntityPresentationFixture2612: " + checks
                        + " registry-bound player and arrow presentation checks PASS");
                server.halt(false);
            } catch (Throwable failure) {
                failure.printStackTrace();
                Runtime.getRuntime().halt(1);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            System.out.println("FabricEntityPresentationFixture2612: saved world and stopped");
            Runtime.getRuntime().halt(0);
        });
    }

    private static void check(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }
}
