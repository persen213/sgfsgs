package io.spatialinversion.addon.core;

import io.spatialinversion.addon.config.SpatialConfig;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Helper for finding safe teleport destinations.
 */
public final class SafeSpot {
    private SafeSpot() {}

    public static boolean isSafe(ServerLevel level, Entity entity, Vec3 pos) {
        if (pos.y <= level.getMinBuildHeight() || pos.y >= level.getMaxBuildHeight()) return false;

        AABB box = entity.getBoundingBox().move(pos.subtract(entity.position()));
        if (!level.noCollision(entity, box)) return false;

        BlockPos feetPos = BlockPos.containing(pos.x, pos.y, pos.z);
        BlockPos headPos = BlockPos.containing(pos.x, pos.y + entity.getBbHeight() - 0.1, pos.z);

        if (isDangerousBlock(level, feetPos) || isDangerousBlock(level, headPos)) return false;

        if (!entity.isCreative() && !entity.isFlying()) {
            boolean foundSupport = false;
            int maxDrop = SpatialConfig.COMMON.safeLandingMaxDrop.get();
            for (int y = 0; y <= maxDrop; y++) {
                BlockPos checkPos = BlockPos.containing(pos.x, pos.y - y, pos.z);
                var state = level.getBlockState(checkPos);
                if (state.isSolid() || state.is(BlockTags.WATER) || state.is(BlockTags.LAVA)) {
                    foundSupport = true;
                    break;
                }
            }
            if (!foundSupport) return false;
        }

        return true;
    }

    private static boolean isDangerousBlock(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(BlockTags.FIRE) ||
               state.is(BlockTags.LAVA) ||
               state.is(BlockTags.CACTUS) ||
               state.is(BlockTags.SWEET_BERRY_BUSH) ||
               state.is(BlockTags.WITHER_ROSE) ||
               state.is(BlockTags.POWDER_SNOW);
    }

    @Nullable
    public static Vec3 findSafeSpot(ServerLevel level, Entity entity, Vec3 target, double searchRadius, int verticalRange) {
        if (isSafe(level, entity, target)) return target;

        for (int ring = 1; ring <= searchRadius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
                    for (int dy = -verticalRange; dy <= verticalRange; dy++) {
                        Vec3 candidate = target.add(dx, dy, dz);
                        if (isSafe(level, entity, candidate)) return candidate;
                    }
                }
            }
        }

        for (int r = 1; r <= searchRadius + verticalRange; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -verticalRange; dy <= verticalRange; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx*dx + dy*dy + dz*dz > r*r) continue;
                        Vec3 candidate = target.add(dx, dy, dz);
                        if (isSafe(level, entity, candidate)) return candidate;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    public static Vec3 findStrikeExit(ServerLevel level, Entity attacker, Vec3 targetPos, double offsetDistance) {
        Vec3 look = attacker.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0, look.z).normalize();

        if (flatLook.lengthSqr() < 1e-6) flatLook = new Vec3(0, 0, 1);

        Vec3[] directions = {
            flatLook.scale(-1),
            new Vec3(-flatLook.z, 0, flatLook.x),
            new Vec3(flatLook.z, 0, -flatLook.x),
            new Vec3(0, 1, 0)
        };

        for (Vec3 dir : directions) {
            Vec3 candidate = targetPos.add(dir.scale(offsetDistance));
            if (isSafe(level, attacker, candidate)) {
                if (level.clip(new net.minecraft.world.phys.clip.ClipContext(
                    candidate, targetPos,
                    net.minecraft.world.phys.clip.ClipContext.Block.COLLIDER,
                    net.minecraft.world.phys.clip.ClipContext.Fluid.NONE,
                    attacker
                )).getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                    return candidate;
                }
            }
        }

        return findSafeSpot(level, attacker, targetPos, offsetDistance + 2, 3);
    }
}