package io.spatialinversion.addon.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/**
 * A group of connected portals belonging to a specific skill activation.
 * Manages portal lifetime, entity redirection logic, and per-group configuration.
 */
public final class PortalGroup {
    public enum Kind {
        LINK,           // Two-way link between two anchors
        REDIRECT,       // One-way redirect in front of caster
        CAGE,           // Four portals in a ring, linked in pairs
        ABSOLUTE,       // Two large rectangular surfaces
        STRIKE,         // Two small portals for an attack
        FOLD            // Two portals for teleportation
    }

    public final long id;
    public final Kind kind;
    public final UUID owner;
    public final ServerLevel level;
    public final long expireTick;
    public final List<Portal> portals = new ArrayList<>();
    public final int capacity;
    public final boolean includeItems;
    public final boolean affectOwn;
    public final boolean resistBossOwners;
    public final float fieldRadius;
    public final Vec3 anchorPos;
    public int redirectionsUsed = 0;

    private PortalGroup(Builder b) {
        this.id = b.id;
        this.kind = b.kind;
        this.owner = b.owner;
        this.level = b.level;
        this.expireTick = b.expireTick;
        this.capacity = b.capacity;
        this.includeItems = b.includeItems;
        this.affectOwn = b.affectOwn;
        this.resistBossOwners = b.resistBossOwners;
        this.fieldRadius = b.fieldRadius;
        this.anchorPos = b.anchorPos;
    }

    public boolean isExpired(long currentTick) {
        return currentTick >= expireTick;
    }

    public boolean hasCapacity() {
        return capacity <= 0 || redirectionsUsed < capacity;
    }

    public void useCapacity() {
        if (capacity > 0) redirectionsUsed++;
    }

    @Nullable
    public Portal getPartner(Portal portal) {
        switch (kind) {
            case LINK:
            case FOLD:
            case STRIKE:
                return portal.portalIndex ^ 1 < portals.size() ? portals.get(portal.portalIndex ^ 1) : null;
            case REDIRECT:
                return null;
            case CAGE:
                int partnerIdx = (portal.portalIndex + 2) % 4;
                return partnerIdx < portals.size() ? portals.get(partnerIdx) : null;
            case ABSOLUTE:
                return portal.portalIndex ^ 1 < portals.size() ? portals.get(portal.portalIndex ^ 1) : null;
        }
        return null;
    }

    public Vec3 getRedirectExitNormal() {
        return anchorPos != null ? anchorPos.normalize() : Vec3.ZERO;
    }

    public static class Builder {
        long id;
        Kind kind;
        UUID owner;
        ServerLevel level;
        long expireTick;
        int capacity = 0;
        boolean includeItems = false;
        boolean affectOwn = false;
        boolean resistBossOwners = false;
        float fieldRadius = 0;
        Vec3 anchorPos = Vec3.ZERO;

        public Builder id(long id) { this.id = id; return this; }
        public Builder kind(Kind kind) { this.kind = kind; return this; }
        public Builder owner(UUID owner) { this.owner = owner; return this; }
        public Builder level(ServerLevel level) { this.level = level; return this; }
        public Builder expireTick(long expireTick) { this.expireTick = expireTick; return this; }
        public Builder capacity(int capacity) { this.capacity = capacity; return this; }
        public Builder includeItems(boolean includeItems) { this.includeItems = includeItems; return this; }
        public Builder affectOwn(boolean affectOwn) { this.affectOwn = affectOwn; return this; }
        public Builder resistBossOwners(boolean resistBossOwners) { this.resistBossOwners = resistBossOwners; return this; }
        public Builder fieldRadius(float fieldRadius) { this.fieldRadius = fieldRadius; return this; }
        public Builder anchorPos(Vec3 anchorPos) { this.anchorPos = anchorPos; return this; }

        public PortalGroup build() {
            return new PortalGroup(this);
        }
    }

    public AABB getBoundingBox() {
        if (portals.isEmpty()) return new AABB(BlockPos.ZERO).inflate(1);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Portal p : portals) {
            double r = Math.max(p.halfW, p.halfH);
            minX = Math.min(minX, p.pos.x - r);
            minY = Math.min(minY, p.pos.y - r);
            minZ = Math.min(minZ, p.pos.z - r);
            maxX = Math.max(maxX, p.pos.x + r);
            maxY = Math.max(maxY, p.pos.y + r);
            maxZ = Math.max(maxZ, p.pos.z + r);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(2.0);
    }
}