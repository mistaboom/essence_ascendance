package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.Pose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;

/** Target-specific guards for the deferred arm and item-atlas presentation paths. */
public final class GlaiveRenderContract2612Test {
    private static int checks;

    public static void main(String[] args) throws Exception {
        checkNativeSleeveInheritance();
        var mixin = load("mixin/GlaiveBodyRenderMixin");
        var pose = method(mixin, "essenceAscendance$poseVisibleShoulder");
        check(calls(pose, "client/GlaivePlayerAnimation", "armorModel") == 1,
                "Native armor must follow the same shoulder instead of forming a second arm");
        check(calls(method(load("mixin/GlaivePlayerModelMixin"), "essenceAscendance$poseGlaive"),
                "client/GlaiveClientState2612", "captureArm") == 1,
                "The held hand must capture its exact submitted arm pose");
        check(calls(method(load("mixin/GlaivePlayerDrawPoseMixin"), "essenceAscendance$restoreSubmittedArm"),
                "client/GlaiveClientState2612", "restoreArm") == 1,
                "The deferred player model draw must restore that submitted pose");

        var probe = method(load("client/GlaiveFirstPersonBody"), "render");
        check(probe.desc.equals("(F)V"), "The hand probe must not accept the visible collector");
        check(fieldReads(probe, "client/GlaiveFirstPersonBody", "SILENT") == 1,
                "The first-person hand probe must submit into its non-drawing collector");

        var emission = method(load("client/InventoryItemEmissionRenderer"), "submit");
        check(fieldReads(emission, "net/minecraft/client/renderer/texture/TextureAtlas", "LOCATION_ITEMS") == 1,
                "Emissive item quads must sample the item atlas");
        check(fieldReads(emission, "net/minecraft/client/renderer/texture/TextureAtlas", "LOCATION_BLOCKS") == 0,
                "Emissive item quads must never sample the block atlas");

        try (var stream = GlaiveRenderContract2612Test.class.getResourceAsStream("/essence_ascendance.mixins.json")) {
            var json = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            check(json.contains("\"GlaiveBodyRenderMixin\"")
                            && json.contains("\"GlaivePlayerDrawPoseMixin\""),
                    "The packaged 26.1 mixins must install both deferred arm hooks");
        }
        System.out.println("GlaiveRenderContract2612Test: " + checks + " presentation assertions PASS");
    }

    /** Exercise the real 26.1 model hierarchy, including its deferred setup/reset. */
    private static void checkNativeSleeveInheritance() {
        for (boolean slim : new boolean[]{false, true}) for (var side : HumanoidArm.values()) {
            var root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
            var model = new PlayerModel(root, slim);
            var arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            var sleeve = side == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;
            var name = side == HumanoidArm.RIGHT ? "right" : "left";
            check(arm.getChild(name + "_sleeve") == sleeve, "The native sleeve must be a child of its arm");
            var state = new AvatarRenderState();
            state.showLeftSleeve = state.showRightSleeve = true;
            for (double pitch : new double[]{-80, 0, 80}) for (double yaw : new double[]{0, 123})
                for (double sweep : new double[]{-60, 0, 60}) for (float blend : new float[]{.25F, 1F}) {
                    model.setupAnim(state);
                    var off = side == HumanoidArm.RIGHT ? model.leftArm : model.rightArm;
                    var offPose = off.storePose();
                    var body = new PoseStack();
                    body.translate(.3, -.7, .2);
                    body.scale(-.9375F, -.9375F, .9375F);
                    GlaivePlayerAnimation.poseArm(model, side, body, Vec3.ZERO,
                            new Pose(Vec3.ZERO, yaw, pitch, 4, sweep), blend);
                    check(sleeve.storePose().equals(sleeve.getInitialPose()),
                            "Glaive aiming must leave the child sleeve in its native local pose");
                    check(off.storePose().equals(offPose), "Glaive aiming must preserve the opposite arm");
                    checkSleeveTransform(root, name);
                    var submitted = arm.storePose();
                    GlaiveClientState2612.captureArm(state, model, side);
                    for (int draw = 0; draw < 2; draw++) {
                        model.setupAnim(state);
                        GlaiveClientState2612.restoreArm(state, model);
                        check(arm.storePose().equals(submitted), "Deferred drawing must retain the submitted hand pose");
                        check(sleeve.storePose().equals(sleeve.getInitialPose()),
                                "Deferred restoration must not apply the arm pose a second time to its sleeve");
                        checkSleeveTransform(root, name);
                    }
                }
            state.showLeftSleeve = state.showRightSleeve = false;
            model.setupAnim(state);
            GlaiveClientState2612.restoreArm(state, model);
            check(!sleeve.visible, "Native skin customization must still control sleeve visibility");
        }
    }

    private static void checkSleeveTransform(ModelPart root, String side) {
        var frames = new java.util.HashMap<String, Matrix4f>();
        root.visit(new PoseStack(), (pose, path, index, cube) -> frames.put(path, new Matrix4f(pose.pose())));
        var arm = frames.get("/" + side + "_arm");
        var sleeve = frames.get("/" + side + "_arm/" + side + "_sleeve");
        check(arm != null && sleeve != null && arm.equals(sleeve, 1e-6F),
                "Native traversal must give the sleeve exactly its parent's position and rotation");
    }

    private static ClassNode load(String suffix) throws Exception {
        String name = "com/mistaboom/essence_ascendance/" + suffix;
        try (var stream = GlaiveRenderContract2612Test.class.getResourceAsStream("/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class " + name);
            var result = new ClassNode();
            new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(method -> method.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + owner.name + "." + name));
    }

    private static long calls(MethodNode owner, String target, String name) {
        long count = 0;
        for (var instruction : owner.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith(target) && call.name.equals(name))
                count++;
        return count;
    }

    private static long fieldReads(MethodNode owner, String target, String name) {
        long count = 0;
        for (var instruction : owner.instructions)
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.endsWith(target) && field.name.equals(name)) count++;
        return count;
    }

    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
}
