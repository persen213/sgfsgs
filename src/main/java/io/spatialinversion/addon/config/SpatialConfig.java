package io.spatialinversion.addon.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Configuration for Spatial Inversion add-on.
 * All values are tunable per-skill with sensible defaults.
 */
public final class SpatialConfig {
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        var pair = new ForgeConfigSpec.Builder().configure(builder -> {
            COMMON = new Common(builder);
        });
        COMMON_SPEC = pair.getRight();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "spatialinversion-common.toml");
    }

    public static class Common {
        // ===== Global Settings =====
        public final ForgeConfigSpec.DoubleValue maxObjectSpeed;
        public final ForgeConfigSpec.DoubleValue momentumKept;
        public final ForgeConfigSpec.IntValue maxRedirectsPerObject;
        public final ForgeConfigSpec.IntValue safeLandingMaxDrop;
        public final ForgeConfigSpec.ConfigValue<String> extraEligibleEntities;
        public final ForgeConfigSpec.DoubleValue fieldBendStrength;

        // ===== Spatial Link (Skill 1) =====
        public final ForgeConfigSpec.IntValue linkCpCost;
        public final ForgeConfigSpec.IntValue linkOverloadCost;
        public final ForgeConfigSpec.IntValue linkCooldownSeconds;
        public final ForgeConfigSpec.IntValue linkDurationSeconds;
        public final ForgeConfigSpec.IntValue linkPlacementRange;
        public final ForgeConfigSpec.IntValue linkMaxAnchorDistance;

        // ===== Vector Redirect (Skill 2) =====
        public final ForgeConfigSpec.IntValue redirectCpCost;
        public final ForgeConfigSpec.IntValue redirectOverloadCost;
        public final ForgeConfigSpec.IntValue redirectCooldownSeconds;
        public final ForgeConfigSpec.IntValue redirectDurationSeconds;

        // ===== Spatial Exchange (Skill 3) =====
        public final ForgeConfigSpec.IntValue exchangeCpCost;
        public final ForgeConfigSpec.IntValue exchangeOverloadCost;
        public final ForgeConfigSpec.IntValue exchangeCooldownSeconds;
        public final ForgeConfigSpec.IntValue exchangeRange;

        // ===== Distortion Field (Skill 4) =====
        public final ForgeConfigSpec.IntValue fieldCpCost;
        public final ForgeConfigSpec.IntValue fieldOverloadCost;
        public final ForgeConfigSpec.IntValue fieldCooldownSeconds;
        public final ForgeConfigSpec.IntValue fieldDurationSeconds;
        public final ForgeConfigSpec.IntValue fieldRadius;

        // ===== Inversion Strike (Skill 5) =====
        public final ForgeConfigSpec.IntValue strikeCpCost;
        public final ForgeConfigSpec.IntValue strikeOverloadCost;
        public final ForgeConfigSpec.IntValue strikeCooldownSeconds;
        public final ForgeConfigSpec.IntValue strikeDelayTicks;
        public final ForgeConfigSpec.IntValue strikeRange;
        public final ForgeConfigSpec.DoubleValue strikeBaseDamage;
        public final ForgeConfigSpec.DoubleValue strikeProficiencyDamageMultiplier;

        // ===== Fold Step (Skill 6) =====
        public final ForgeConfigSpec.IntValue foldCpCost;
        public final ForgeConfigSpec.IntValue foldOverloadCost;
        public final ForgeConfigSpec.IntValue foldCooldownSeconds;
        public final ForgeConfigSpec.IntValue foldMaxDistance;

        // ===== Spatial Cage (Skill 7) =====
        public final ForgeConfigSpec.IntValue cageCpCost;
        public final ForgeConfigSpec.IntValue cageOverloadCost;
        public final ForgeConfigSpec.IntValue cageCooldownSeconds;
        public final ForgeConfigSpec.IntValue cageDurationSeconds;
        public final ForgeConfigSpec.IntValue cageRadius;
        public final ForgeConfigSpec.DoubleValue cageSoftness; // 0-1, how much outward velocity is reduced

        // ===== Absolute Inversion (Skill 8 - Ultimate) =====
        public final ForgeConfigSpec.IntValue absoluteCpCost;
        public final ForgeConfigSpec.IntValue absoluteOverloadCost;
        public final ForgeConfigSpec.IntValue absoluteCooldownSeconds;
        public final ForgeConfigSpec.IntValue absoluteDurationSeconds;
        public final ForgeConfigSpec.IntValue absolutePlacementRange;
        public final ForgeConfigSpec.IntValue absoluteSurfaceWidth;
        public final ForgeConfigSpec.IntValue absoluteSurfaceHeight;
        public final ForgeConfigSpec.IntValue absoluteCapacity;
        public final ForgeConfigSpec.BooleanValue absoluteBossResist;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            builder.comment("Global settings affecting all skills");
            maxObjectSpeed = builder
                .comment("Maximum speed for redirected entities (blocks/tick)")
                .defineInRange("maxObjectSpeed", 20.0, 0.1, 100.0);
            momentumKept = builder
                .comment("Momentum retention when crossing portals (0.0-1.0)")
                .defineInRange("momentumKept", 0.85, 0.0, 1.0);
            maxRedirectsPerObject = builder
                .comment("Maximum portal crossings per entity per group (0 = unlimited)")
                .defineInRange("maxRedirectsPerObject", 3, 0, 10);
            safeLandingMaxDrop = builder
                .comment("Max blocks to search downward for safe landing (teleport skills)")
                .defineInRange("safeLandingMaxDrop", 8, 0, 64);
            extraEligibleEntities = builder
                .comment("Extra entity IDs (registry names) eligible for redirection. Default: academy:coin, academy:silbarn")
                .define("extraEligibleEntities", "academy:coin,academy:silbarn");
            fieldBendStrength = builder
                .comment("Distortion Field trajectory bend strength (0.0-1.0)")
                .defineInRange("fieldBendStrength", 0.35, 0.0, 1.0);
            builder.pop();

            // Skill 1: Spatial Link
            builder.push("spatial_link");
            builder.comment("Spatial Link - Two connected anchors for projectile/item transport");
            linkCpCost = builder.defineInRange("cpCost", 60, 0, 1000);
            linkOverloadCost = builder.defineInRange("overloadCost", 15, 0, 200);
            linkCooldownSeconds = builder.defineInRange("cooldownSeconds", 10, 0, 7200);
            linkDurationSeconds = builder.defineInRange("durationSeconds", 8, 1, 300);
            linkPlacementRange = builder.defineInRange("placementRange", 24, 1, 100);
            linkMaxAnchorDistance = builder.defineInRange("maxAnchorDistance", 20, 1, 100);
            builder.pop();

            // Skill 2: Vector Redirect
            builder.push("vector_redirect");
            builder.comment("Vector Redirect - Temporary opening redirects incoming projectiles");
            redirectCpCost = builder.defineInRange("cpCost", 80, 0, 1000);
            redirectOverloadCost = builder.defineInRange("overloadCost", 20, 0, 200);
            redirectCooldownSeconds = builder.defineInRange("cooldownSeconds", 16, 0, 7200);
            redirectDurationSeconds = builder.defineInRange("durationSeconds", 3, 1, 100);
            builder.pop();

            // Skill 3: Spatial Exchange
            builder.push("spatial_exchange");
            builder.comment("Spatial Exchange - Swap position with an eligible entity");
            exchangeCpCost = builder.defineInRange("cpCost", 100, 0, 1000);
            exchangeOverloadCost = builder.defineInRange("overloadCost", 25, 0, 200);
            exchangeCooldownSeconds = builder.defineInRange("cooldownSeconds", 22, 0, 7200);
            exchangeRange = builder.defineInRange("range", 18, 1, 100);
            builder.pop();

            // Skill 4: Distortion Field
            builder.push("distortion_field");
            builder.comment("Distortion Field - Field bends incoming projectile trajectories");
            fieldCpCost = builder.defineInRange("cpCost", 120, 0, 1000);
            fieldOverloadCost = builder.defineInRange("overloadCost", 30, 0, 200);
            fieldCooldownSeconds = builder.defineInRange("cooldownSeconds", 28, 0, 7200);
            fieldDurationSeconds = builder.defineInRange("durationSeconds", 5, 1, 100);
            fieldRadius = builder.defineInRange("radius", 6, 1, 32);
            builder.pop();

            // Skill 5: Inversion Strike
            builder.push("inversion_strike");
            builder.comment("Inversion Strike - Concentrated spatial attack through an opening");
            strikeCpCost = builder.defineInRange("cpCost", 140, 0, 1000);
            strikeOverloadCost = builder.defineInRange("overloadCost", 35, 0, 200);
            strikeCooldownSeconds = builder.defineInRange("cooldownSeconds", 15, 0, 7200);
            strikeDelayTicks = builder.defineInRange("delayTicks", 6, 1, 100);
            strikeRange = builder.defineInRange("range", 24, 1, 100);
            strikeBaseDamage = builder.defineInRange("baseDamage", 12.0, 0.0, 100.0);
            strikeProficiencyDamageMultiplier = builder.defineInRange("proficiencyDamageMultiplier", 0.5, 0.0, 2.0);
            builder.pop();

            // Skill 6: Fold Step
            builder.push("fold_step");
            builder.comment("Fold Step - Two openings and travel between them");
            foldCpCost = builder.defineInRange("cpCost", 70, 0, 1000);
            foldOverloadCost = builder.defineInRange("overloadCost", 18, 0, 200);
            foldCooldownSeconds = builder.defineInRange("cooldownSeconds", 12, 0, 7200);
            foldMaxDistance = builder.defineInRange("maxDistance", 16, 1, 100);
            builder.pop();

            // Skill 7: Spatial Cage
            builder.push("spatial_cage");
            builder.comment("Spatial Cage - Four connected openings around an area, restrict escape");
            cageCpCost = builder.defineInRange("cpCost", 160, 0, 1000);
            cageOverloadCost = builder.defineInRange("overloadCost", 40, 0, 200);
            cageCooldownSeconds = builder.defineInRange("cooldownSeconds", 38, 0, 7200);
            cageDurationSeconds = builder.defineInRange("durationSeconds", 6, 1, 100);
            cageRadius = builder.defineInRange("radius", 8, 1, 32);
            cageSoftness = builder.defineInRange("softness", 0.65, 0.0, 1.0);
            builder.pop();

            // Skill 8: Absolute Inversion (Ultimate)
            builder.push("absolute_inversion");
            builder.comment("Absolute Inversion (Ultimate) - Two enormous spatial surfaces reverse spatial relationship");
            absoluteCpCost = builder.defineInRange("cpCost", 300, 0, 2000);
            absoluteOverloadCost = builder.defineInRange("overloadCost", 80, 0, 500);
            absoluteCooldownSeconds = builder.defineInRange("cooldownSeconds", 120, 0, 7200);
            absoluteDurationSeconds = builder.defineInRange("durationSeconds", 5, 1, 100);
            absolutePlacementRange = builder.defineInRange("placementRange", 30, 1, 100);
            absoluteSurfaceWidth = builder.defineInRange("surfaceWidth", 13, 1, 64);
            absoluteSurfaceHeight = builder.defineInRange("surfaceHeight", 9, 1, 64);
            absoluteCapacity = builder.defineInRange("capacity", 12, 1, 100);
            absoluteBossResist = builder.define("bossResist", true);
            builder.pop();
        }
    }
}