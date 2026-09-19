package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.utility.UtilitySenseService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Bounded S2C snapshot for Utility Sense Focus presentation. */
public record UtilitySensePayload(UtilitySenseService.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<UtilitySensePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "utility_sense"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UtilitySensePayload> CODEC =
            StreamCodec.of(UtilitySensePayload::write, UtilitySensePayload::read);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buf, UtilitySensePayload payload) {
        var snapshot = payload.snapshot();
        buf.writeResourceLocation(snapshot.dimension());
        buf.writeEnum(snapshot.mode());
        buf.writeBoolean(snapshot.ledger());

        bounded(snapshot.threats().size(), UtilitySenseService.MAX_THREATS, "threats");
        buf.writeVarInt(snapshot.threats().size());
        for (var threat : snapshot.threats()) {
            buf.writeVarInt(threat.entityId());
            buf.writeBoolean(threat.active());
            buf.writeFloat(threat.health());
            buf.writeFloat(threat.maxHealth());
            buf.writeVarInt(threat.armor());
        }

        bounded(snapshot.projectilePaths().size(), UtilitySenseService.MAX_PROJECTILE_PATHS, "projectile paths");
        buf.writeVarInt(snapshot.projectilePaths().size());
        for (var path : snapshot.projectilePaths()) {
            bounded(path.points().size(), UtilitySenseService.MAX_PROJECTILE_PATH_POINTS, "projectile path points");
            buf.writeVarInt(path.historyPoints());
            buf.writeVarInt(path.points().size());
            for (Vec3 point : path.points()) writeVec3(buf, point);
        }

        bounded(snapshot.explosions().size(), UtilitySenseService.MAX_EXPLOSIONS, "explosions");
        buf.writeVarInt(snapshot.explosions().size());
        for (var explosion : snapshot.explosions()) {
            writeVec3(buf, explosion.center());
            buf.writeDouble(explosion.radius());
        }

        buf.writeBoolean(snapshot.waylight() != null);
        if (snapshot.waylight() != null) {
            buf.writeLong(snapshot.waylight().feet().asLong());
        }
    }

    private static UtilitySensePayload read(RegistryFriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        UtilitySenseService.Mode mode = buf.readEnum(UtilitySenseService.Mode.class);
        boolean ledger = buf.readBoolean();

        int threatCount = readCount(buf, UtilitySenseService.MAX_THREATS, "threats");
        List<UtilitySenseService.Threat> threats = new ArrayList<>(threatCount);
        for (int i = 0; i < threatCount; i++) {
            int entityId = buf.readVarInt();
            boolean active = buf.readBoolean();
            float health = finite(buf.readFloat());
            float maxHealth = finite(buf.readFloat());
            int armor = buf.readVarInt();
            threats.add(new UtilitySenseService.Threat(entityId, active, health, maxHealth, armor));
        }

        int pathCount = readCount(buf, UtilitySenseService.MAX_PROJECTILE_PATHS, "projectile paths");
        List<UtilitySenseService.ProjectilePath> paths = new ArrayList<>(pathCount);
        for (int i = 0; i < pathCount; i++) {
            int historyPoints = buf.readVarInt();
            int pointCount = readCount(buf, UtilitySenseService.MAX_PROJECTILE_PATH_POINTS, "projectile path points");
            if (pointCount < 2 || historyPoints < 1 || historyPoints > pointCount)
                throw new IllegalArgumentException("Utility projectile path history out of bounds");
            List<Vec3> points = new ArrayList<>(pointCount);
            for (int point = 0; point < pointCount; point++) points.add(readVec3(buf));
            paths.add(new UtilitySenseService.ProjectilePath(points, historyPoints));
        }

        int explosionCount = readCount(buf, UtilitySenseService.MAX_EXPLOSIONS, "explosions");
        List<UtilitySenseService.ExplosionDanger> explosions = new ArrayList<>(explosionCount);
        for (int i = 0; i < explosionCount; i++) {
            Vec3 center = readVec3(buf);
            double radius = finite(buf.readDouble());
            if (radius < 0 || radius > 128) throw new IllegalArgumentException("Utility explosion radius out of bounds");
            explosions.add(new UtilitySenseService.ExplosionDanger(center, radius));
        }

        UtilitySenseService.WaylightMarker waylight = null;
        if (buf.readBoolean()) {
            waylight = new UtilitySenseService.WaylightMarker(BlockPos.of(buf.readLong()));
        }
        return new UtilitySensePayload(new UtilitySenseService.Snapshot(dimension, mode, ledger,
                threats, paths, explosions, waylight));
    }

    private static void writeVec3(RegistryFriendlyByteBuf buf, Vec3 value) {
        buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
    }

    private static Vec3 readVec3(RegistryFriendlyByteBuf buf) {
        return new Vec3(finite(buf.readDouble()), finite(buf.readDouble()), finite(buf.readDouble()));
    }

    private static int readCount(RegistryFriendlyByteBuf buf, int maximum, String label) {
        int count = buf.readVarInt();
        bounded(count, maximum, label);
        return count;
    }

    private static void bounded(int value, int maximum, String label) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException("Utility " + label + " count out of bounds");
    }

    private static float finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite Utility sense value");
        return value;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite Utility sense value");
        return value;
    }
}
