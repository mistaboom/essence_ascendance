package com.mistaboom.essence_ascendance.fixture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.Arrays;

/** Initialization-safe 26.1.2 hook probe; item-dependent movement needs a joined client world. */
public final class GuardMovementNativeTest {
    private static int checks;

    private GuardMovementNativeTest() { }

    public static void runAndExit() {
        if (!Boolean.getBoolean("essence.guard.movementNativeTest"))
            throw new IllegalStateException("Explicit guard fixture flag required");
        try { run(); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.exit(0);
    }

    public static void run() {
        check(Arrays.stream(net.minecraft.client.gui.screens.inventory.AnvilScreen.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("useSynchronizedCost")),
                "Shared native client hook transformed: useSynchronizedCost");
        check(Arrays.stream(net.minecraft.client.gui.screens.inventory.AnvilScreen.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("renderFreeCost")),
                "Shared native client hook transformed: renderFreeCost");
        check(Arrays.stream(net.minecraft.client.gui.screens.inventory.EnchantmentScreen.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("resetRenderedOffer")),
                "Shared native client hook transformed: resetRenderedOffer");
        check(Arrays.stream(net.minecraft.client.gui.screens.inventory.EnchantmentScreen.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("drawEfficientCost")),
                "Shared native client hook transformed: drawEfficientCost");
        check(Arrays.stream(net.minecraft.client.renderer.item.ItemModelResolver.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("essenceAscendance$clear")),
                "Shared native client hook transformed: essenceAscendance$clear");
        check(Arrays.stream(net.minecraft.client.renderer.item.ItemModelResolver.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("essenceAscendance$capture")),
                "Shared native client hook transformed: essenceAscendance$capture");
        check(Arrays.stream(net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("submitAccent")),
                "Shared native client hook transformed: submitAccent");
        var client = Minecraft.getInstance();
        check(client.level == null && client.getSingleplayerServer() == null,
                "Initialization fixture has not opened a gameplay world");
        for (String hook : new String[]{"composedWidgets", "composedRenderables",
                "finishComposedWidgets", "composedNarration"})
            check(has(Screen.class, hook), "Native 26 Screen contains shared fullscreen hook " + hook);
        if ("neoforge".equals(System.getProperty("essence.guard.fixtureLoader")))
            check(has(Screen.class, "composedEventWidgets"),
                    "NeoForge 26 Screen contains its native event-widget hook");
        check(has(Entity.class, "guardedContact"), "Native entity contact movement is transformed");
        check(has(LivingEntity.class, "guardedStep"), "Native step height is transformed");
        check(has(LocalPlayer.class, "counterattackReach"),
                "Native 26 LocalPlayer raycast has the Riposte reach wrapper");
        check(has(LocalPlayer.class, "itemUseSpeedMultiplier") && has(LocalPlayer.class, "useSpeed"),
                "Native 26 item-use multiplier has the guard/rod speed hook");
        check(has(LocalPlayer.class, "isSlowDueToUsingItem") && has(LocalPlayer.class, "guardSprint"),
                "Native 26 item-use sprint gate has the guard hook");
        check(has(Entity.class, "collide"), "Native terrain collision entrypoint remains available");
        System.out.println("GuardMovementNativeTest 26.1.2: " + checks
                + " transformed fullscreen, reach, guard speed/sprint and terrain hooks PASS; "
                + "functional ItemStack movement requires a registry-bound client world");
    }

    private static boolean has(Class<?> owner, String name) {
        return Arrays.stream(owner.getDeclaredMethods()).map(Method::getName)
                .anyMatch(method -> method.contains(name));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
