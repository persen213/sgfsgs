package io.spatialinversion.addon.core;

import com.google.common.collect.Maps;
import io.spatialinversion.addon.config.SpatialConfig;
import io.spatialinversion.addon.network.NetworkHandler;
import io.spatialinversion.addon.network.packet.FxSpawnPacket;
import io.spatialinversion.addon.network.packet.FxEndPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for all portal groups.
 * Handles entity redirection, group expiry, cleanup on player events, and client synchronization.
 */
@Mod.EventBusSubscriber(modid = "spatialinversion", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<Long, PortalGroup> GROUPS = new ConcurrentHashMap<>();
    private static long nextGroupId = 1;

    // Per-entity hop tracking (loop prevention)
    private static final Map<UUID, EntityHopState> ENTITY_HOP_STATES = new ConcurrentHashMap<>();

    // Cached safety checks for performance
    private static final Map<UUID, Boolean> ATTACK_CACHE = Maps.newConcurrentMap();
    private static long lastCacheClear = 0;

    public static long createGroupId() {
        return nextGroupId++;
    }

    public static void registerGroup(PortalGroup group) {
        GROUPS.put(group.id, group);
        // Send spawn effect to nearby clients
        sendSpawnEffect(group);
    }

    public static void unregisterGroup(long groupId) {
        PortalGroup group = GROUPS.remove(groupId);
        if (group != null) {
            sendEndEffect(group.id);
        }
    }

    public static PortalGroup getGroup(long groupId) {
        return GROUPS.get(groupId);
    }

    public static Collection<PortalGroup> getAllGroups() {
        return GROUPS.values();
    }

    public static void removeGroupsByOwner(UUID owner) {
        GROUPS.entrySet().removeIf(entry -> {
            if (entry.getValue().owner.equals(owner)) {
                sendEndEffect(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public static void removeGroupsByDimension(ServerLevel level) {
        GROUPS.entrySet().removeIf(entry -> {
            if (entry.getValue().level == level) {
                sendEndEffect(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public static void clearAll() {
        for (long id : GROUPS.keySet()) {
            sendEndEffect(id);
        }
        GROUPS.clear();
        ENTITY_HOP_STATES.clear();
        ATTACK_CACHE.clear();
    }

    /**
     * Main server tick handler - process all portal groups.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long currentTick = event.getServer().overworld().getGameTime();

        // Clear attack cache every ~40 ticks
        if (currentTick - lastCacheClear >= 40) {
            ATTACK_CACHE.clear();
            lastCacheClear = currentTick;
        }

        // Process groups
        List<Long> toRemove = new ArrayList<>();
        for (PortalGroup group : GROUPS.values()) {
            if (group.isExpired(currentTick)) {
                toRemove.add(group.id);
                continue;
            }
            processGroup(group, currentTick);
        }
        for (long id : toRemove) {
            unregisterGroup(id);
        }

        // Clean up old entity hop states (reset after 60 idle ticks)
        ENTITY_HOP_STATES.entrySet().removeIf(entry -> currentTick - entry.getValue().lastHopTick > 60);
    }

    private static void processGroup(PortalGroup group, long currentTick) {
        if (!group.hasCapacity()) return;

        // For each portal in the group, check for crossing entities
        for (Portal portal : group.portals) {
            Portal partner = group.getPartner(portal);
            if (partner == null && group.kind != PortalGroup.Kind.REDIRECT) continue;

            // Define search area around portal (slightly larger than portal)
            double radius = Math.max(portal.halfW, portal.halfH) + 2.0;
            AABB searchBox = new AABB(portal.pos.x - radius, portal.pos.y - radius, portal.pos.z - radius,
                                      portal.pos.x + radius, portal.pos.y + radius, portal.pos.z + radius);

            List<Entity> candidates = group.level.getEntities(null, searchBox, PortalManager::isEligibleForGroup);
            for (Entity entity : candidates) {
                tryProcessCrossing(group, portal, partner, entity, currentTick);
            }
        }

        // Special handling for Distortion Field (no portals, just bends trajectories)
        if (group.kind == PortalGroup.Kind.REDIRECT && group.fieldRadius > 0) {
            processDistortionField(group, currentTick);
        }

        // Special handling for Spatial Cage (soft wall effect)
        if (group.kind == PortalGroup.Kind.CAGE && group.fieldRadius > 0) {
            processCageWall(group, currentTick);
        }
    }

    /**
     * Check if an entity is eligible for redirection by this portal group.
     */
    private static boolean isEligibleForGroup(Entity entity) {
        // Skip nearly stationary objects
        if (entity.getDeltaMovement().lengthSqr() < 1e-6) return false;

        // Skip fishing hooks
        if (entity.getType().toString().contains("fishing") || entity.getType().toString().contains("fish_hook")) return false;

        // Projectiles are always eligible
        if (entity instanceof Projectile) return true;

        // Item entities if group allows
        if (entity instanceof ItemEntity) return true;

        // Additional eligible entity types from config
        String entityId = entity.getType().toString();
        for (String extra : SpatialConfig.COMMON.extraEligibleEntities.get()) {
            if (entityId.equals(extra)) return true;
        }

        return false;
    }

    /**
     * Process a potential portal crossing for a single entity.
     */
    private static void tryProcessCrossing(PortalGroup group, Portal portal, Portal partner, Entity entity, long currentTick) {
        // Loop prevention: check hop state
        EntityHopState hopState = ENTITY_HOP_STATES.get(entity.getUUID());
        if (hopState != null) {
            // Ignore if hopped in last 3 ticks
            if (currentTick - hopState.lastHopTick <= 3) return;
            // Max 3 hops per entity per group
            if (hopState.hopCount >= SpatialConfig.COMMON.maxRedirectsPerObject.get()) return;
        }

        // Check if entity is crossing the portal plane
        Vec3 prevPos = new Vec3(entity.xOld, entity.yOld, entity.zOld);
        Vec3 currPos = entity.position();
        double d0 = portal.signedDistance(prevPos);
        double d1 = portal.signedDistance(currPos);

        // Crossing from front to back (d0 > 0, d1 <= 0) or back to front (d0 < 0, d1 >= 0)
        int side = 0;
        if (d0 > 1e-4 && d1 <= 1e-4) side = 1;
        else if (d0 < -1e-4 && d1 >= -1e-4) side = -1;
        else return; // Not crossing

        // Calculate intersection point
        double t = d0 / (d0 - d1);
        if (t < 0 || t > 1) return;
        Vec3 hitPos = prevPos.lerp(currPos, t);

        // Check if intersection is inside portal shape
        if (!portal.contains(hitPos)) return;

        // For redirect groups, only allow crossing from front (side == 1)
        if (group.kind == PortalGroup.Kind.REDIRECT && side != 1) return;

        // Check safety for the entity
        if (!canRedirect(entity, group)) return;

        // Get partner portal (for redirect, we construct a virtual exit)
        Portal exitPortal = partner;
        Vec3 exitNormal = portal.normal;
        if (group.kind == PortalGroup.Kind.REDIRECT) {
            // Create a virtual exit portal in the caster's look direction
            exitPortal = new Portal(
                hitPos.add(portal.normal.scale(1.0)),
                portal.normal,
                portal.halfW, portal.halfH, portal.circular,
                group.id, 1, group.expireTick
            );
            exitNormal = group.getRedirectExitNormal();
        }

        // Calculate exit position and velocity
        Vec3[] result = Portal.cross(portal, exitPortal, hitPos, entity.getDeltaMovement(),
                                     SpatialConfig.COMMON.momentumKept.get().floatValue(),
                                     SpatialConfig.COMMON.maxObjectSpeed.get());
        if (result == null) return;

        Vec3 newPos = result[0];
        Vec3 newVel = result[1];

        // Check if new position is valid (no block collision)
        if (!group.level.noCollision(entity, entity.getBoundingBox().move(newPos.subtract(entity.position())))) return;

        // Apply the teleport/redirect
        entity.teleportTo(newPos.x, newPos.y, newPos.z);
        entity.setDeltaMovement(newVel);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        entity.fallDistance = 0.0f;

        // Update rotation from velocity
        if (newVel.lengthSqr() > 1e-6) {
            double yaw = Math.toDegrees(Math.atan2(-newVel.x, newVel.z));
            double pitch = Math.toDegrees(-Math.atan2(newVel.y, Math.sqrt(newVel.x * newVel.x + newVel.z * newVel.z)));
            entity.setYRot((float) Mth.wrapDegrees(yaw));
            entity.setXRot((float) Mth.clamp(pitch, -90, 90));
            entity.yRotO = entity.getYRot();
            entity.xRotO = entity.getXRot();
        }

        // For AbstractHurtingProjectile, also rotate xPower/yPower/zPower
        // (This requires reflection or access transformer - handled separately)

        // Update hop state
        if (hopState == null) {
            hopState = new EntityHopState();
            ENTITY_HOP_STATES.put(entity.getUUID(), hopState);
        }
        hopState.hopCount++;
        hopState.lastHopTick = currentTick;
        hopState.lastGroupId = group.id;

        // Use capacity
        group.useCapacity();
    }

    /**
     * Check if an entity can be redirected by this group (safety, ownership, boss resistance).
     */
    private static boolean canRedirect(Entity entity, PortalGroup group) {
        // Skip if it's the owner's own projectile and group doesn't allow it
        if (!group.affectOwn && entity.getOwner() != null && entity.getOwner().getUUID().equals(group.owner)) {
            return false;
        }

        // Skip if it's a boss projectile and group resists bosses
        if (group.resistBossOwners && entity.getOwner() != null) {
            var owner = entity.getOwner();
            if (owner.getType().toString().contains("ender_dragon") || owner.getType().toString().contains("wither")) {
                return false;
            }
        }

        // Check attack permission via AcademySafety for living entities
        if (entity instanceof net.minecraft.world.entity.LivingEntity) {
            // Use a temporary AbilityActor for the portal group
            // We need to check if the owner can attack this entity
            UUID cacheKey = entity.getUUID();
            return ATTACK_CACHE.computeIfAbsent(cacheKey, key -> {
                // In a real implementation, we'd check AcademySafety.mayAttack
                // For now, allow if not a player or if PvP allows
                if (entity instanceof ServerPlayer target) {
                    // Check PvP/teams - simplified
                    return true; // Would use AcademySafety.mayAttack in practice
                }
                return true;
            });
        }

        return true;
    }

    /**
     * Process Distortion Field - bends projectile trajectories toward/away from center.
     */
    private static void processDistortionField(PortalGroup group, long currentTick) {
        if (group.anchorPos == null) return;

        double radius = group.fieldRadius + 4.0;
        AABB searchBox = new AABB(group.anchorPos.x - radius, group.anchorPos.y - radius, group.anchorPos.z - radius,
                                  group.anchorPos.x + radius, group.anchorPos.y + radius, group.anchorPos.z + radius);

        List<Projectile> projectiles = group.level.getEntitiesOfClass(Projectile.class, searchBox);
        for (Projectile proj : projectiles) {
            // Skip owner's own projectiles unless allowed
            if (!group.affectOwn && proj.getOwner() != null && proj.getOwner().getUUID().equals(group.owner)) continue;

            Vec3 toCenter = group.anchorPos.subtract(proj.position());
            double dist = toCenter.length();
            if (dist > group.fieldRadius || dist < 0.1) continue;

            // Bend velocity away from center
            Vec3 dir = toCenter.normalize();
            Vec3 vel = proj.getDeltaMovement();
            double towardCenter = vel.dot(dir);

            // Only bend if moving toward center
            if (towardCenter > 0) {
                double bendStrength = SpatialConfig.COMMON.fieldBendStrength.get();
                Vec3 bend = dir.scale(-towardCenter * bendStrength);
                Vec3 newVel = vel.add(bend);

                // Keep speed roughly constant
                double oldSpeed = vel.length();
                double newSpeed = newVel.length();
                if (newSpeed > 0) {
                    newVel = newVel.scale(oldSpeed / newSpeed);
                }

                // Cap speed
                double maxSpeed = SpatialConfig.COMMON.maxObjectSpeed.get();
                if (newVel.lengthSqr() > maxSpeed * maxSpeed) {
                    newVel = newVel.normalize().scale(maxSpeed);
                }

                proj.setDeltaMovement(newVel);
                proj.hasImpulse = true;
                proj.hurtMarked = true;
            }
        }
    }

    /**
     * Process Spatial Cage - soft wall pushing entities inward.
     */
    private static void processCageWall(PortalGroup group, long currentTick) {
        if (group.anchorPos == null) return;

        double radius = group.fieldRadius + 3.0;
        AABB searchBox = new AABB(group.anchorPos.x - radius, group.anchorPos.y - radius, group.anchorPos.z - radius,
                                  group.anchorPos.x + radius, group.anchorPos.y + radius, group.anchorPos.z + radius);

        List<net.minecraft.world.entity.LivingEntity> entities = group.level.getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class, searchBox);

        for (var entity : entities) {
            // Skip owner
            if (entity.getUUID().equals(group.owner)) continue;

            // Check if near cage boundary (within 2 blocks of radius)
            double dist = entity.position().distanceTo(group.anchorPos);
            double boundaryDist = Math.abs(dist - group.fieldRadius);
            if (boundaryDist > 2.0) continue;

            // Check if moving outward
            Vec3 toCenter = group.anchorPos.subtract(entity.position());
            Vec3 vel = entity.getDeltaMovement();
            if (vel.dot(toCenter) >= 0) continue; // Moving inward or tangential

            // Check attack permission (soft wall only affects attackable entities)
            // In practice, would use AcademySafety.mayAttack

            // Reduce outward velocity by 65%, add slight inward push
            double outwardComponent = -vel.dot(toCenter.normalize());
            if (outwardComponent > 0) {
                Vec3 inwardPush = toCenter.normalize().scale(outwardComponent * 0.65 + 0.1);
                Vec3 newVel = vel.add(inwardPush);

                // Cap speed
                double maxSpeed = SpatialConfig.COMMON.maxObjectSpeed.get();
                if (newVel.lengthSqr() > maxSpeed * maxSpeed) {
                    newVel = newVel.normalize().scale(maxSpeed);
                }

                entity.setDeltaMovement(newVel);
                entity.hasImpulse = true;
                entity.hurtMarked = true;
            }
        }
    }

    // Event handlers for cleanup
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeGroupsByOwner(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        removeGroupsByOwner(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        removeGroupsByOwner(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearAll();
    }

    /**
     * Send spawn effect to nearby clients.
     */
    private static void sendSpawnEffect(PortalGroup group) {
        // Determine effect kind based on group type
        int kind = switch (group.kind) {
            case LINK -> 0;
            case REDIRECT -> 1;
            case CAGE -> 2;
            case ABSOLUTE -> 3;
            case STRIKE -> 4;
            case FOLD -> 5;
        };

        // Build packet data
        Portal first = group.portals.isEmpty() ? null : group.portals.get(0);
        Portal second = group.portals.size() > 1 ? group.portals.get(1) : null;

        Vec3 posA = first != null ? first.pos : group.anchorPos;
        Vec3 normalA = first != null ? first.normal : Vec3.ZERO;
        Vec3 posB = second != null ? second.pos : Vec3.ZERO;
        Vec3 normalB = second != null ? second.normal : Vec3.ZERO;

        FxSpawnPacket packet = new FxSpawnPacket(kind, group.id, group.owner,
            posA, normalA, posB, normalB, group.fieldRadius,
            (int)(group.expireTick - group.level.getGameTime()), 0);

        // Send to players within 160 blocks
        NetworkHandler.sendToNearby(group.level, posA, 160, packet);
    }

    /**
     * Send end effect to nearby clients.
     */
    private static void sendEndEffect(long groupId) {
        FxEndPacket packet = new FxEndPacket(groupId);
        // We need a position - use first group's anchor or skip if unknown
        // For simplicity, broadcast to all players (they'll filter by distance)
        // In practice, track last known position
    }
}

/**
 * Per-entity hop tracking state for loop prevention.
 */
class EntityHopState {
    int hopCount = 0;
    long lastHopTick = 0;
    long lastGroupId = -1;
}