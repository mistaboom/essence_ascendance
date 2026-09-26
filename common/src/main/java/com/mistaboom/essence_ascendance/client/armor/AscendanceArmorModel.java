package com.mistaboom.essence_ascendance.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One armor slot's meshes attached to vanilla humanoid bones. Vanilla owns the
 * animated transforms, visibility, baby scaling and enchantment foil passes.
 */
public final class AscendanceArmorModel<T extends LivingEntity> extends HumanoidModel<T> {
    public AscendanceArmorModel(EquipmentSlot slot) {
        super(root(slot));
    }

    private static ModelPart root(EquipmentSlot slot) {
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST
                && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) {
            throw new IllegalArgumentException("Unsupported Ascendance armor slot: " + slot);
        }

        Map<String, ModelPart> parts = new LinkedHashMap<>();
        parts.put("head", part(slot == EquipmentSlot.HEAD ? "head" : null, 0, 0, 0));
        parts.put("hat", part(null, 0, 0, 0));
        // The leggings' belt follows torso crouching/rotation independently of leg motion.
        parts.put("body", part(slot == EquipmentSlot.CHEST ? "chest"
                : slot == EquipmentSlot.LEGS ? "belt" : null, 0, 0, 0));
        parts.put("right_arm", part(slot == EquipmentSlot.CHEST ? "right_arm" : null, -5, 2, 0));
        parts.put("left_arm", part(slot == EquipmentSlot.CHEST ? "left_arm" : null, 5, 2, 0));
        parts.put("right_leg", part(slot == EquipmentSlot.LEGS ? "right_leg"
                : slot == EquipmentSlot.FEET ? "right_foot" : null, -1.9F, 12, 0));
        parts.put("left_leg", part(slot == EquipmentSlot.LEGS ? "left_leg"
                : slot == EquipmentSlot.FEET ? "left_foot" : null, 1.9F, 12, 0));
        return new ModelPart(List.of(), parts);
    }

    private static ModelPart part(String mesh, float x, float y, float z) {
        ModelPart part = new ModelPart(mesh == null ? List.of() : List.of(new ArmorMesh(mesh)), Map.of());
        PartPose pose = PartPose.offset(x, y, z);
        part.setInitialPose(pose);
        part.loadPose(pose);
        return part;
    }

    /** Cube is the vanilla extensibility point consumed by ModelPart's transforms and visibility. */
    private static final class ArmorMesh extends ModelPart.Cube {
        private static final int MAGIC = 0x45414D31; // EAM1, shared with the machine meshes.
        private static final Map<String, List<Triangle>> MESHES = new ConcurrentHashMap<>();
        private static final int[] QUAD_VERTICES = {0, 1, 2, 2};

        private final List<Triangle> triangles;

        private ArmorMesh(String name) {
            // No vanilla cube faces: compile emits the imported triangles instead.
            super(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 64, 64, Set.of());
            triangles = MESHES.computeIfAbsent(name, ArmorMesh::load);
        }

        @Override
        public void compile(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color) {
            for (Triangle triangle : triangles) {
                // Entity buffers consume quads. A repeated last vertex encodes one exact triangle.
                for (int i : QUAD_VERTICES) {
                    Vertex v = triangle.vertices[i];
                    buffer.addVertex(pose, v.x, v.y, v.z).setColor(color).setUv(v.u, v.v)
                            .setOverlay(overlay).setLight(light)
                            .setNormal(pose, v.nx, v.ny, v.nz);
                }
            }
        }

        private static List<Triangle> load(String name) {
            String path = "/assets/essence_ascendance/meshes/armor/ascendance/" + name + ".eamesh";
            InputStream resource = AscendanceArmorModel.class.getResourceAsStream(path);
            if (resource == null) throw new IllegalStateException("Missing generated armor mesh: " + path);
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(resource))) {
                int magic = input.readInt();
                if (magic != MAGIC) throw new IOException("Unexpected mesh header: " + Integer.toHexString(magic));
                int count = input.readInt();
                if (count <= 0 || count > 100_000) throw new IOException("Invalid armor triangle count: " + count);
                var triangles = new ArrayList<Triangle>(count);
                for (int i = 0; i < count; i++) {
                    Vertex[] vertices = new Vertex[3];
                    for (int j = 0; j < 3; j++) vertices[j] = readVertex(input);
                    triangles.add(new Triangle(vertices));
                }
                if (input.read() != -1) throw new IOException("Unexpected trailing armor mesh data");
                return List.copyOf(triangles);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load generated Ascendance armor mesh: " + path, e);
            }
        }

        private static Vertex readVertex(DataInputStream input) throws IOException {
            Vertex vertex = new Vertex(input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(),
                    input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat());
            if (!Float.isFinite(vertex.x) || !Float.isFinite(vertex.y) || !Float.isFinite(vertex.z)
                    || !Float.isFinite(vertex.u) || !Float.isFinite(vertex.v)
                    || !Float.isFinite(vertex.nx) || !Float.isFinite(vertex.ny) || !Float.isFinite(vertex.nz)) {
                throw new IOException("Non-finite armor mesh vertex");
            }
            float lengthSquared = vertex.nx * vertex.nx + vertex.ny * vertex.ny + vertex.nz * vertex.nz;
            if (Math.abs(lengthSquared - 1.0F) > 1.0e-4F) {
                throw new IOException("Armor mesh normal must have unit length");
            }
            return vertex;
        }
    }

    private record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) { }
    private record Triangle(Vertex[] vertices) { }
}
