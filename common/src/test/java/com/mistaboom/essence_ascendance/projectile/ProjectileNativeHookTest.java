package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/** Loader-only, initialization-time fixture. Executes transformed hooks and native gravity without constructing or opening a world. */
public final class ProjectileNativeHookTest {
    private static int assertions;
    private ProjectileNativeHookTest() { }
    /** Only the explicit loader test mod uses this entrypoint; terminate development watchers with the test result. */
    public static void runAndExit() {
        if (!Boolean.getBoolean("essence.projectile.nativeHookTest")) throw new IllegalStateException("Explicit native hook test flag required");
        try { run(); ProjectileNativeInterceptionTest.run(); NativeGuardCounterattackTest.run(); NativeGuardRamTest.run(); NativeGuardOutcomeTest.run(); NativeStatusInterceptionTest.run(); NativePostureOutcomeTest.run(); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.exit(0);
    }
    public static void run() {
        try {
            check(Arrays.stream(ServerGamePacketListenerImpl.class.getDeclaredMethods())
                    .anyMatch(method -> method.getName().contains("attackBeforeMining")), "Server block-attack wrapper transformed");
            var allocatorField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            allocatorField.setAccessible(true);
            Object allocator = allocatorField.get(null);
            Method allocate = allocator.getClass().getMethod("allocateInstance", Class.class);
            for (var arrowClass : java.util.List.of(Arrow.class, SpectralArrow.class)) {
                for (boolean client : new boolean[] {false, true}) {
                    var level = (ServerLevel) allocate.invoke(allocator, ServerLevel.class);
                    set(Level.class, level, "isClientSide", client);
                    var arrow = (AbstractArrow) allocate.invoke(allocator, arrowClass);
                    set(Entity.class, arrow, "level", level);
                    set(Entity.class, arrow, "type", arrowClass == Arrow.class ? EntityType.ARROW : EntityType.SPECTRAL_ARROW);
                    defineData(arrow);
                    var access = (ProjectileStateAccess) arrow;
                    access.essenceAscendance$state(new ProjectileState(ProjectileSource.RANGED_PHYSICAL, ProjectilePath.NONE,
                            new java.util.UUID(1, 1), new java.util.UUID(0, 0),
                            net.minecraft.resources.ResourceLocation.parse("minecraft:overworld"), 0,
                            com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings.defaults(), 0));
                    check(access.essenceAscendance$flightScale() == 1, "Native arrow data defaults to ordinary physics");
                    access.essenceAscendance$flightScale(0.125F);
                    var updates = arrow.getEntityData().packDirty();
                    check(updates != null && updates.stream().anyMatch(value -> Float.valueOf(0.125F).equals(value.value())),
                            "Native entity updates carry the server's flight scale");
                    var control = ProjectileControlService.state(arrow);
                    control.dragFactor = 0.125;
                    Vec3 input = new Vec3(0.2, 0.015, 0);
                    arrow.setDeltaMovement(input);
                    if (client) ProjectileControlService.beforeFlight(arrow);
                    else control.physicsInput = input;
                    arrow.setDeltaMovement(input.scale(0.99F));
                    Method gravity = Entity.class.getDeclaredMethod("applyGravity"); gravity.setAccessible(true); gravity.invoke(arrow);
                    check(Math.abs(arrow.getDeltaMovement().y - (input.y * 0.99F - 0.05)) < 1e-9,
                            "Real native arrow gravity reproduces the original full-tick fall");
                    Method hook = Arrays.stream(AbstractArrow.class.getDeclaredMethods())
                            .filter(method -> method.getName().contains("slowNativePhysics")).findFirst().orElseThrow();
                    hook.setAccessible(true); hook.invoke(arrow, new CallbackInfo("tick", false));
                    Vec3 expected = input.scale(Math.pow(0.99F, 0.125)).add(0, -0.05 * 0.125 * 0.125, 0);
                    check(arrow.getDeltaMovement().distanceTo(expected) < 1e-9 && control.physicsInput == null,
                            arrowClass.getSimpleName() + " transformed post-tick gravity correction on " + (client ? "client" : "server"));
                    access.essenceAscendance$flightScale(1);
                    if (client) ProjectileControlService.beforeFlight(arrow);
                    check(!client || control.dragFactor == 1 && control.physicsInput == null,
                            "Client exits slowed physics when the server releases or steals the shot");
                }
            }
            System.out.println("Projectile native hooks passed: " + assertions + " (transformed arrows, client/server physics, input binding; no world)");
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Native projectile hook fixture failed", failure); }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void defineData(AbstractArrow arrow) throws ReflectiveOperationException {
        var builder = new SynchedEntityData.Builder(arrow);
        var defaults = Map.<String, Object>of("DATA_SHARED_FLAGS_ID", (byte) 0, "DATA_AIR_SUPPLY_ID", 300,
                "DATA_CUSTOM_NAME", Optional.empty(), "DATA_CUSTOM_NAME_VISIBLE", false, "DATA_SILENT", false,
                "DATA_NO_GRAVITY", false, "DATA_POSE", Pose.STANDING, "DATA_TICKS_FROZEN", 0);
        for (var entry : defaults.entrySet()) {
            var field = Entity.class.getDeclaredField(entry.getKey()); field.setAccessible(true);
            builder.define((EntityDataAccessor) field.get(null), entry.getValue());
        }
        Method define = (arrow instanceof Arrow ? Arrow.class : AbstractArrow.class)
                .getDeclaredMethod("defineSynchedData", SynchedEntityData.Builder.class);
        define.setAccessible(true); define.invoke(arrow, builder);
        set(Entity.class, arrow, "entityData", builder.build());
    }
    private static void set(Class<?> owner, Object target, String name, Object value) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
