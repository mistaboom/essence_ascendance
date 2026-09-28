package com.mistaboom.essence_ascendance.machine;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.client.FocusVisuals;
import com.mistaboom.essence_ascendance.client.PylonRenderTransform;
import com.mistaboom.essence_ascendance.client.PylonVisuals;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlock;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusBlock;
import com.mistaboom.essence_ascendance.pylon.EssencePylonBlock;
import com.mistaboom.essence_ascendance.pylon.PylonLocalFrame;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/** Real placement callbacks, saved-property codecs, collision geometry and client pose math; no world or GL context. */
public final class MachinePlacementTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // Vanilla bootstrap freezes BLOCK before a loader can register mod blocks. In
        // this disposable fixture JVM reopen only that registry, register three real
        // production block classes, then freeze it before exercising their callbacks.
        var frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(BuiltInRegistries.BLOCK, false);
        var intrusive = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        intrusive.setAccessible(true);
        intrusive.set(BuiltInRegistries.BLOCK, new java.util.IdentityHashMap<>());
        var nexus = Registry.register(BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("essence_test", "nexus"),
                new AscendanceNexusBlock(BlockBehaviour.Properties.of()));
        var infuser = Registry.register(BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("essence_test", "infuser"),
                new EssenceInfuserBlock(BlockBehaviour.Properties.of()));
        var pylon = Registry.register(BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("essence_test", "pylon"),
                new EssencePylonBlock(BlockBehaviour.Properties.of()));
        BuiltInRegistries.BLOCK.freeze();
        horizontal(nexus);
        horizontal(infuser);
        pylon(pylon);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("MachinePlacementTest: " + checks + " placement, legacy states, collision, rotation, Focus and tether checks PASS");
    }

    private static void horizontal(HorizontalMachineBlock block) throws Exception {
        BlockState original = block.defaultBlockState();
        check(original.getValue(HorizontalMachineBlock.FACING) == Direction.WEST,
                "Missing facing preserves authored west-facing geometry");
        JsonObject old = new JsonObject();
        if (block instanceof EssenceInfuserBlock) old.addProperty("focus_lit", "true");
        BlockState oldLoaded = decodeProperties(original, old);
        check(oldLoaded.getValue(HorizontalMachineBlock.FACING) == Direction.WEST,
                "Actual native property codec supplies the legacy facing");
        if (block instanceof EssenceInfuserBlock) {
            check(oldLoaded.getValue(EssenceInfuserBlock.FOCUS_LIT), "Legacy Focus state survives the added facing");
        }
        VoxelShape authored = shape(original);
        Set<BlockState> placedStates = new HashSet<>();
        for (Direction player : Direction.Plane.HORIZONTAL) {
            for (Direction support : Direction.values()) {
                BlockState placed = block.getStateForPlacement(context(support, player, new Direction[]{player}));
                placedStates.add(placed);
                check(placed.getValue(HorizontalMachineBlock.FACING) == player.getOpposite(),
                        "Nexus/Infuser face the player independently of clicked surface");
                if (block instanceof EssenceInfuserBlock) {
                    check(!placed.getValue(EssenceInfuserBlock.FOCUS_LIT), "Placement preserves unlit default");
                }
                PylonLocalFrame frame = HorizontalMachineBlock.frame(player.getOpposite());
                near(frame.localVectorToWorld(new Vec3(-1, 0, 0)), vector(player.getOpposite()),
                        "Authored west front maps to the placement-facing direction");
                equalShape(shape(placed), frame.transformShape(authored), "Horizontal collision rotates with the mesh");
                verifyPose(frame);
                verifyStateTransform(placed, HorizontalMachineBlock.FACING);
            }
        }
        check(placedStates.size() == 4, "Every horizontal player approach creates its own placement state");
    }

    private static void pylon(EssencePylonBlock block) throws Exception {
        BlockState original = block.defaultBlockState();
        VoxelShape authored = shape(original);
        Set<BlockState> placedStates = new HashSet<>();
        for (Direction axis : Direction.values()) {
            JsonObject old = new JsonObject();
            old.addProperty("facing", axis.getSerializedName());
            old.addProperty("focus_lit", "true");
            BlockState oldLoaded = decodeProperties(original, old);
            check(oldLoaded.getValue(EssencePylonBlock.FACING) == axis
                            && oldLoaded.getValue(EssencePylonBlock.ROLL) == 0
                            && oldLoaded.getValue(EssencePylonBlock.FOCUS_LIT),
                    "Every old mount keeps its axis, Focus and exact zero-roll geometry");
            equalShape(shape(oldLoaded), PylonLocalFrame.of(axis).transformShape(authored), "Legacy mount collision retained");
            for (Direction forward : Direction.values()) {
                if (forward.getAxis() == axis.getAxis()) continue;
                // The leading look component deliberately points along the mount: it must be skipped.
                Direction[] looks = {axis.getOpposite(), forward.getOpposite(), forward, axis};
                BlockState placed = block.getStateForPlacement(context(axis, Direction.NORTH, looks));
                placedStates.add(placed);
                PylonLocalFrame frame = PylonLocalFrame.of(placed);
                check(frame.direction() == axis && frame.forwardDirection() == forward,
                        "Surface selection controls mount axis while player look controls independent roll");
                check(!placed.getValue(EssencePylonBlock.FOCUS_LIT), "Pylon placement remains unlit");
                near(frame.axis(), vector(axis), "Tower tip remains pointed away from its actual support");
                near(frame.right().cross(frame.axis()), frame.forward(), "All 24 frames remain right-handed");
                equalShape(shape(placed), frame.transformShape(authored), "Pylon collision includes mount and roll");
                verifyPose(frame);
                verifyStateTransform(placed, EssencePylonBlock.FACING);
                for (Rotation rotation : Rotation.values()) {
                    PylonLocalFrame transformed = PylonLocalFrame.of(placed.rotate(rotation));
                    check(transformed.forwardDirection() == rotation.rotate(forward), "Structure rotation rotates Pylon roll too");
                }
                for (Mirror mirror : Mirror.values()) {
                    BlockState mirrored = placed.mirror(mirror);
                    check(PylonLocalFrame.of(mirrored).forwardDirection() == mirror.mirror(forward),
                            "Structure mirror transforms mount-plane forward direction");
                    check(mirrored.mirror(mirror) == placed, "Applying a mirror twice restores the exact Pylon state");
                }
                MachineVisualState.Focus focus = new MachineVisualState.Focus(MachineVisualState.Host.PYLON,
                        true, null, true, 1);
                FocusVisuals.Context context = FocusVisuals.Context.installed(focus, true, frame);
                check(context.frame().equals(frame), "Physical gem and ornament receive the full Pylon frame");
                Vec3 focusOffset = FocusVisuals.center(context, 47).subtract(new Vec3(.5, .5, .5));
                near(focusOffset.cross(vector(axis)), Vec3.ZERO, "Hovering Focus remains on the tower axis for every mount/roll");
                BlockPos linked = new BlockPos(3, 4, -5);
                MachineVisualState.Pylon visual = new MachineVisualState.Pylon(linked, focus);
                Vec3 anchor = PylonVisuals.tetherAnchor(BlockPos.ZERO, visual, frame);
                Vec3 localAnchor = frame.blockToLocal(anchor);
                check(Math.abs(localAnchor.y - 1.13) < 1.0E-6, "Tether stays at the authored sender height");
                Vec3 link = vector(linked);
                Vec3 projected = link.subtract(frame.axis().scale(link.dot(frame.axis())));
                Vec3 radial = anchor.subtract(frame.localToBlock(new Vec3(.5, 1.13, .5)));
                near(radial.normalize(), projected.normalize(), "Tether anchor continues pointing at the linked Crucible in world space");
            }
        }
        check(placedStates.size() == 24, "All six mount faces support all four independent quarter turns");
    }

    private static void verifyStateTransform(BlockState state,
                                             net.minecraft.world.level.block.state.properties.DirectionProperty property) {
        for (Rotation rotation : Rotation.values()) {
            BlockState rotated = state.rotate(rotation);
            check(rotated.getValue(property) == rotation.rotate(state.getValue(property)), "Structure rotation transforms facing");
        }
        for (Mirror mirror : Mirror.values()) {
            check(state.mirror(mirror).getValue(property) == mirror.mirror(state.getValue(property)), "Mirror transforms facing");
        }
        BlockState roundTrip = state;
        for (int turn = 0; turn < 4; turn++) roundTrip = roundTrip.rotate(Rotation.CLOCKWISE_90);
        check(roundTrip == state, "Four structure quarter turns restore the original state");
    }

    private static void verifyPose(PylonLocalFrame frame) {
        PoseStack pose = new PoseStack();
        PylonRenderTransform.applyAroundBlockCenter(pose, frame);
        for (Vec3 point : new Vec3[]{Vec3.ZERO, new Vec3(.3, 1.6, .7), new Vec3(1, 0, 0),
                new Vec3(0, 1, 0), new Vec3(0, 0, 1)}) {
            Vector3f rendered = pose.last().pose().transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
            Vec3 expected = frame.localToBlock(point);
            near(new Vec3(rendered.x, rendered.y, rendered.z), expected, "BER pose agrees with collision/local-frame transform");
            near(frame.blockToLocal(expected), point, "Points survive local/world/local round trip");
        }
    }

    @SuppressWarnings("unchecked")
    private static BlockState decodeProperties(BlockState defaultState, JsonObject properties) throws Exception {
        var field = StateHolder.class.getDeclaredField("propertiesCodec");
        field.setAccessible(true);
        MapCodec<BlockState> codec = (MapCodec<BlockState>) field.get(defaultState);
        return codec.codec().parse(JsonOps.INSTANCE, properties).getOrThrow();
    }

    private static VoxelShape shape(BlockState state) {
        return state.getShape(null, BlockPos.ZERO);
    }

    private static void equalShape(VoxelShape actual, VoxelShape expected, String message) {
        check(!Shapes.joinIsNotEmpty(actual, expected, BooleanOp.NOT_SAME), message);
    }

    private static Vec3 vector(Direction direction) { return vector(direction.getNormal()); }
    private static Vec3 vector(net.minecraft.core.Vec3i vector) {
        return new Vec3(vector.getX(), vector.getY(), vector.getZ());
    }

    private static void near(Vec3 actual, Vec3 expected, String message) {
        check(actual.distanceToSqr(expected) < 1.0E-10, message + ": " + actual + " versus " + expected);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static PlacementContext context(Direction face, Direction horizontal, Direction[] looks) throws Exception {
        // The real placement callbacks need only directions, not a world or player. Match
        // the existing native fixtures' allocation seam without invoking a null-level constructor.
        var field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object allocator = field.get(null);
        PlacementContext result = (PlacementContext) allocator.getClass().getMethod("allocateInstance", Class.class)
                .invoke(allocator, PlacementContext.class);
        result.face = face;
        result.horizontal = horizontal;
        result.looks = looks;
        return result;
    }

    private static final class PlacementContext extends BlockPlaceContext {
        private Direction face;
        private Direction horizontal;
        private Direction[] looks;
        private PlacementContext() { super(null, null, null, null, null); }
        @Override public Direction getClickedFace() { return face; }
        @Override public Direction getHorizontalDirection() { return horizontal; }
        @Override public Direction[] getNearestLookingDirections() { return looks; }
    }
}
