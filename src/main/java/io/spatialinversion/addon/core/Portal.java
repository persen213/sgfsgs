package io.spatialinversion.addon.core;

import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A spatial portal - a planar opening in space with position, normal, shape, and local frame.
 * Used by all Spatial Inversion skills for redirecting entities and projectiles.
 */
public final class Portal {
    public final Vec3 pos;
    public final Vec3 normal;
    public final float halfW;
    public final float halfH;
    public final boolean circular;
    public final Vector3f tangentX;
    public final Vector3f tangentY;
    public final long groupId;
    public final int portalIndex;
    public long expireTick;

    public Portal(Vec3 pos, Vec3 normal, float halfW, float halfH, boolean circular, long groupId, int portalIndex, long expireTick) {
        this.pos = pos;
        this.normal = normal.normalize();
        this.halfW = halfW;
        this.halfH = halfH;
        this.circular = circular;
        this.groupId = groupId;
        this.portalIndex = portalIndex;
        this.expireTick = expireTick;

        // Build local frame: tangentX = right, tangentY = up, normal = forward
        Vec3 arbitrary = Math.abs(normal.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 tx = normal.cross(arbitrary).normalize();
        Vec3 ty = normal.cross(tx).normalize();
        this.tangentX = new Vector3f((float) tx.x, (float) tx.y, (float) tx.z);
        this.tangentY = new Vector3f((float) ty.x, (float) ty.y, (float) ty.z);
    }

    public double signedDistance(Vec3 point) {
        return (point.x - pos.x) * normal.x + (point.y - pos.y) * normal.y + (point.z - pos.z) * normal.z;
    }

    public boolean contains(Vec3 point) {
        double dx = point.x - pos.x;
        double dy = point.y - pos.y;
        double dz = point.z - pos.z;

        float localX = (float) (dx * tangentX.x + dy * tangentX.y + dz * tangentX.z);
        float localY = (float) (dx * tangentY.x + dy * tangentY.y + dz * tangentY.z);

        if (circular) {
            double nx = localX / halfW;
            double ny = localY / halfH;
            return nx * nx + ny * ny <= 1.0;
        } else {
            return Math.abs(localX) <= halfW && Math.abs(localY) <= halfH;
        }
    }

    public int side(Vec3 point) {
        double d = signedDistance(point);
        if (d > 1e-4) return 1;
        if (d < -1e-4) return -1;
        return 0;
    }

    @Nullable
    public static Vec3[] cross(Portal from, Portal to, Vec3 hitPos, Vec3 velocity, float momentumKept, double maxSpeed) {
        Quaternionf rot = new Quaternionf().rotationTo(
            new Vector3f((float) -from.normal.x, (float) -from.normal.y, (float) -from.normal.z),
            new Vector3f((float) to.normal.x, (float) to.normal.y, (float) to.normal.z)
        );

        Vec3 rel = hitPos.subtract(from.pos);
        Vector3f relVec = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
        rot.transform(relVec);
        Vec3 newPos = to.pos.add(relVec.x, relVec.y, relVec.z).add(to.normal.scale(0.6));

        Vector3f velVec = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
        rot.transform(velVec);
        Vec3 newVel = new Vec3(velVec.x, velVec.y, velVec.z).scale(momentumKept);

        double speedSq = newVel.lengthSqr();
        if (speedSq > maxSpeed * maxSpeed) {
            newVel = newVel.normalize().scale(maxSpeed);
        }

        return new Vec3[]{newPos, newVel};
    }
}