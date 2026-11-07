package io.spatialinversion.addon;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.CategoryDefinition;
import io.github.pinchan4273.reacademycraft.api.Presentation;
import io.github.pinchan4273.reacademycraft.api.SkillDefinition;
import io.github.pinchan4273.reacademycraft.api.SkillRequirement;
import io.github.pinchan4273.reacademycraft.api.TreePosition;
import io.github.pinchan4273.reacademycraft.api.forge.RegisterAbilitiesEvent;
import io.github.pinchan4273.reacademycraft.api.mc.AcademySafety;
import io.github.pinchan4273.reacademycraft.api.mc.ActivationContext;
import io.github.pinchan4273.reacademycraft.api.mc.SkillBehavior;
import com.mojang.logging.LogUtils;
import io.spatialinversion.addon.config.SpatialConfig;
import io.spatialinversion.addon.core.Portal;
import io.spatialinversion.addon.core.PortalGroup;
import io.spatialinversion.addon.core.PortalManager;
import io.spatialinversion.addon.core.SafeSpot;
import io.spatialinversion.addon.network.NetworkHandler;
import io.spatialinversion.addon.network.packet.FxEndPacket;
import io.spatialinversion.addon.network.packet.FxSpawnPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Spatial Inversion Esper Add-on for Re:AcademyCraft 1.5.0
 * Adds the "Spatial Manipulation" category with 8 level-5 skills.
 */
@Mod(SpatialInversion.MOD_ID)
public final class SpatialInversion {
    public static final String MOD_ID = "spatialinversion";
    static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> SPATIAL_FACTOR = ITEMS.register("spatial_factor",
        () -> new Item(new Item.Properties().stacksTo(16)));

    static AbilityId id(String path) { return AbilityId.parse(MOD_ID + ":" + path); }

    // Category
    static final AbilityId SPATIAL_MANIPULATION = id("spatial_manipulation");

    // Skills (all level 5)
    static final AbilityId SPATIAL_LINK = id("spatial_link");
    static final AbilityId VECTOR_REDIRECT = id("vector_redirect");
    static final AbilityId SPATIAL_EXCHANGE = id("spatial_exchange");
    static final AbilityId DISTORTION_FIELD = id("distortion_field");
    static final AbilityId INVERSION_STRIKE = id("inversion_strike");
    static final AbilityId FOLD_STEP = id("fold_step");
    static final AbilityId SPATIAL_CAGE = id("spatial_cage");
    static final AbilityId ABSOLUTE_INVERSION = id("absolute_inversion");

    public SpatialInversion() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener(this::registerAbilities);
        bus.addListener(this::creativeTab);
        SpatialConfig.register();
        NetworkHandler.init();
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(SPATIAL_FACTOR);
    }

    private static Presentation shown(AbilityId skill) {
        return new Presentation(MOD_ID + ".skill." + skill.path(), MOD_ID + ".skill." + skill.path() + ".desc", null);
    }

    private static Presentation catShown() {
        return new Presentation(MOD_ID + ".ability.spatial_manipulation", null, null);
    }

    private void registerAbilities(RegisterAbilitiesEvent event) {
        var abilities = event.registrar(MOD_ID);

        // Category: Spatial Manipulation
        abilities.category(new CategoryDefinition(
            SPATIAL_MANIPULATION,
            id("spatial_factor"),
            List.of(1, 1, 1, 1, 1), // One controllable skill per level
            catShown()
        ));

        // All skills are level 5
        int level = 5;

        // 1. Spatial Link (TOGGLE)
        abilities.skill(new SkillDefinition(SPATIAL_LINK, SPATIAL_MANIPULATION, level, List.of(),
            ActivationKind.TOGGLE, shown(SPATIAL_LINK), new TreePosition(40, 50, null)));

        // 2. Vector Redirect (INSTANT)
        abilities.skill(new SkillDefinition(VECTOR_REDIRECT, SPATIAL_MANIPULATION, level, List.of(
            new SkillRequirement(SPATIAL_LINK, 0.3f)
        ), ActivationKind.INSTANT, shown(VECTOR_REDIRECT), new TreePosition(100, 30, SPATIAL_LINK)));

        // 3. Spatial Exchange (INSTANT)
        abilities.skill(new SkillDefinition(SPATIAL_EXCHANGE, SPATIAL_MANIPULATION, level, List.of(
            new SkillRequirement(SPATIAL_LINK, 0.3f)
        ), ActivationKind.INSTANT, shown(SPATIAL_EXCHANGE), new TreePosition(100, 70, SPATIAL_LINK)));

        // 4. Distortion Field (INSTANT)
        abilities.skill(new SkillDefinition(DISTORTION_FIELD, SPATIAL_MANIPULATION, level, List.of(
            new SkillRequirement(VECTOR_REDIRECT, 0.5f)
        ), ActivationKind.INSTANT, shown(DISTORTION_FIELD), new TreePosition(160, 20, VECTOR_REDIRECT)));

        // 5. Inversion Strike (TOGGLE)
        abilities.skill(new SkillDefinition(INVERSION_STRIKE, SPATIAL_MANIPULATION, level, List.of(
            new SkillRequirement(VECTOR_REDIRECT, 0.5f)
        ), ActivationKind.TOGGLE, shown(INVERSION_STRIKE), new TreePosition(160, 80, VECTOR_REDIRECT)));

        // 6. Fold Step (INSTANT)
        abilities.skill(new SkillDefinition(FOLD_STEP, SPATIAL_MANIPULATION, level, List.of(
            new SkillRequirement(SPATIAL_EXCHANGE, 0.5f)
        ), ActivationKind.INSTANT, shown(FOLD_STEP), new TreePosition(220, 50, SPATIAL_EXCHANGE)));

        // 7. Spatial Cage (TOGGLE)
        abilities.skill(new SkillDefinition(SPATIAL_CAGE, SPATIAL_MANIPULATION, level, List.of(
            new SkillRequirement(DISTORTION_FIELD, 0.5f),
            new SkillRequirement(INVERSION_STRIKE, 0.5f)
        ), ActivationKind.TOGGLE, shown(SPATIAL_CAGE), new TreePosition(280, 30, DISTORTION_FIELD)));

        // 8. Absolute Inversion (INSTANT - Ultimate)
        abilities.skill(new SkillDefinition(ABSOLUTE_INVERSION, SPATIAL_MANIPULATION, level, List.of(
            new SkillRequirement(SPATIAL_CAGE, 0.5f),
            new SkillRequirement(FOLD_STEP, 0.5f)
        ), ActivationKind.INSTANT, shown(ABSOLUTE_INVERSION), new TreePosition(340, 50, SPATIAL_CAGE)));

        // Register behaviors
        var behaviors = event.behaviors(MOD_ID);
        behaviors.behavior(SPATIAL_LINK, new SpatialLinkBehavior());
        behaviors.behavior(VECTOR_REDIRECT, new VectorRedirectBehavior());
        behaviors.behavior(SPATIAL_EXCHANGE, new SpatialExchangeBehavior());
        behaviors.behavior(DISTORTION_FIELD, new DistortionFieldBehavior());
        behaviors.behavior(INVERSION_STRIKE, new InversionStrikeBehavior());
        behaviors.behavior(FOLD_STEP, new FoldStepBehavior());
        behaviors.behavior(SPATIAL_CAGE, new SpatialCageBehavior());
        behaviors.behavior(ABSOLUTE_INVERSION, new AbsoluteInversionBehavior());
    }

    // =================== SKILL BEHAVIORS ===================

    /**
     * 1. Spatial Link (TOGGLE): Two connected anchors; projectiles and items pass between them.
     * First press: place anchor A. Second press: place anchor B (within range, clamped to max distance).
     * Link lasts for duration, then collapses. Press again to collapse early.
     */
    private static class SpatialLinkBehavior implements SkillBehavior {
        // Per-caster state for the two-step placement
        private static final java.util.Map<UUID, LinkState> PENDING_LINKS = new java.util.concurrent.ConcurrentHashMap<>();

        private static class LinkState {
            Portal anchorA;
            long startTick;
        }

        @Override
        public io.github.pinchan4273.reacademycraft.api.ActivationOutcome start(ActivationContext ctx) {
            var actor = ctx.actor();
            UUID owner = actor.id();

            // Check if we have a pending first anchor
            LinkState pending = PENDING_LINKS.get(owner);

            if (pending == null) {
                // First press: place anchor A
                if (!ctx.pay(SpatialConfig.COMMON.linkCpCost.get(), SpatialConfig.COMMON.linkOverloadCost.get())) {
                    return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_RESOURCE;
                }

                Vec3 anchorPos = findAnchorPosition(actor, SpatialConfig.COMMON.linkPlacementRange.get());
                if (anchorPos == null) return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_INVALID;

                LinkState state = new LinkState();
                state.anchorA = new Portal(anchorPos, actor.look(), 1.2f, 1.2f, true,
                    0, 0, actor.level().getGameTime() + 20 * SpatialConfig.COMMON.linkDurationSeconds.get());
                state.startTick = actor.level().getGameTime();
                PENDING_LINKS.put(owner, state);

                // Show pending anchor effect
                sendPendingEffect(actor, anchorPos);
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.STARTED;
            } else {
                // Second press: place anchor B
                long currentTick = actor.level().getGameTime();
                if (currentTick - pending.startTick > 100) { // 5 second timeout
                    PENDING_LINKS.remove(owner);
                    return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_INVALID;
                }

                Vec3 anchorBPos = findAnchorPosition(actor, SpatialConfig.COMMON.linkPlacementRange.get());
                if (anchorBPos == null) return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_INVALID;

                // Clamp distance between anchors
                double maxDist = SpatialConfig.COMMON.linkMaxAnchorDistance.get();
                double dist = anchorBPos.distanceTo(pending.anchorA.pos);
                if (dist > maxDist) {
                    Vec3 dir = anchorBPos.subtract(pending.anchorA.pos).normalize();
                    anchorBPos = pending.anchorA.pos.add(dir.scale(maxDist));
                }

                if (dist < 2.0) { // Too close
                    PENDING_LINKS.remove(owner);
                    return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_INVALID;
                }

                // Create the link group
                long groupId = PortalManager.createGroupId();
                long expireTick = currentTick + 20L * SpatialConfig.COMMON.linkDurationSeconds.get();

                PortalGroup group = new PortalGroup.Builder()
                    .id(groupId)
                    .kind(PortalGroup.Kind.LINK)
                    .owner(owner)
                    .level(actor.level())
                    .expireTick(expireTick)
                    .capacity(0) // unlimited
                    .includeItems(true)
                    .affectOwn(false)
                    .resistBossOwners(false)
                    .build();

                // Add both portals
                Portal portalA = new Portal(pending.anchorA.pos, actor.look(), 1.2f, 1.2f, true, groupId, 0, expireTick);
                Portal portalB = new Portal(anchorBPos, anchorBPos.subtract(pending.anchorA.pos).normalize(), 1.2f, 1.2f, true, groupId, 1, expireTick);
                group.portals.add(portalA);
                group.portals.add(portalB);

                PortalManager.registerGroup(group);

                // Set cooldown from end of effect
                ctx.setCooldown(20 * SpatialConfig.COMMON.linkCooldownSeconds.get() + 20 * SpatialConfig.COMMON.linkDurationSeconds.get());

                PENDING_LINKS.remove(owner);
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.STARTED;
            }
        }

        @Override
        public void stop(ActivationContext ctx, io.github.pinchan4273.reacademycraft.api.StopReason reason) {
            UUID owner = ctx.actor().id();
            LinkState pending = PENDING_LINKS.remove(owner);
            if (pending != null) {
                // Cancelled during placement - remove pending effect
                sendEndEffect(pending.anchorA.pos, ctx.actor().level());
            } else {
                // Active link - find and remove group
                PortalManager.getAllGroups().stream()
                    .filter(g -> g.owner.equals(owner) && g.kind == PortalGroup.Kind.LINK)
                    .findFirst()
                    .ifPresent(g -> PortalManager.unregisterGroup(g.id));
            }
        }

        private Vec3 findAnchorPosition(io.github.pinchan4273.reacademycraft.api.mc.AbilityActor actor, int range) {
            ServerLevel level = actor.level();
            Vec3 eye = actor.position();
            Vec3 look = actor.look();

            var clip = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor.entity()));
            if (clip.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                Vec3 hit = clip.getLocation();
                // Direction.getNormal() returns Vec3i, convert to Vec3
                Vec3 normal = new Vec3(clip.getDirection().getStepX(), clip.getDirection().getStepY(), clip.getDirection().getStepZ());
                return hit.add(normal.x * 0.5, normal.y * 0.5, normal.z * 0.5);
            }
            return eye.add(look.scale(range));
        }

        private void sendPendingEffect(io.github.pinchan4273.reacademycraft.api.mc.AbilityActor actor, Vec3 pos) {
            FxSpawnPacket packet = new FxSpawnPacket(0, -1, actor.id(), pos, actor.look(), Vec3.ZERO, Vec3.ZERO, 0, 100, 0);
            NetworkHandler.sendToNearby(actor.level(), pos, 160, packet);
        }

        private void sendEndEffect(Vec3 pos, ServerLevel level) {
            // Send end packet to nearby clients
        }
    }

    /**
     * 2. Vector Redirect (INSTANT): Temporary opening redirects incoming projectiles.
     * Placed ~2.4 blocks in front of caster, facing caster's look direction.
     * Exit direction = caster's current look (updates dynamically).
     */
    private static class VectorRedirectBehavior implements SkillBehavior {
        @Override
        public io.github.pinchan4273.reacademycraft.api.ActivationOutcome start(ActivationContext ctx) {
            var actor = ctx.actor();
            if (!ctx.pay(SpatialConfig.COMMON.redirectCpCost.get(), SpatialConfig.COMMON.redirectOverloadCost.get())) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_RESOURCE;
            }

            Vec3 eye = actor.position();
            Vec3 look = actor.look();
            Vec3 portalPos = eye.add(look.scale(2.4));

            long groupId = PortalManager.createGroupId();
            long expireTick = actor.level().getGameTime() + 20L * SpatialConfig.COMMON.redirectDurationSeconds.get();

            PortalGroup group = new PortalGroup.Builder()
                .id(groupId)
                .kind(PortalGroup.Kind.REDIRECT)
                .owner(actor.id())
                .level(actor.level())
                .expireTick(expireTick)
                .capacity(0)
                .includeItems(false)
                .affectOwn(false)
                .resistBossOwners(true)
                .anchorPos(look) // Store look direction for exit
                .build();

            Portal portal = new Portal(portalPos, look, 1.5f, 1.5f, true, groupId, 0, expireTick);
            group.portals.add(portal);

            PortalManager.registerGroup(group);

            // Cooldown from end of effect
            ctx.setCooldown(20 * SpatialConfig.COMMON.redirectCooldownSeconds.get() + 20 * SpatialConfig.COMMON.redirectDurationSeconds.get());
            return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.PERFORMED;
        }
    }

    /**
     * 3. Spatial Exchange (INSTANT): Swap position with an eligible entity.
     * Range 18, check BOTH destinations for safety, bosses can resist.
     */
    private static class SpatialExchangeBehavior implements SkillBehavior {
        @Override
        public io.github.pinchan4273.reacademycraft.api.ActivationOutcome start(ActivationContext ctx) {
            var actor = ctx.actor();
            if (!ctx.pay(SpatialConfig.COMMON.exchangeCpCost.get(), SpatialConfig.COMMON.exchangeOverloadCost.get())) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_RESOURCE;
            }

            // Find target in crosshair
            ServerLevel level = actor.level();
            Vec3 eye = actor.position();
            Vec3 look = actor.look();
            double range = SpatialConfig.COMMON.exchangeRange.get();

            var clip = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor.entity()));
            Vec3 rayEnd = clip.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? eye.add(look.scale(range)) : clip.getLocation();

            // Find living entity along ray
            AABB searchBox = new AABB(eye, rayEnd).inflate(2.0);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != actor.entity() && e.isAlive());

            LivingEntity target = null;
            double bestDist = Double.MAX_VALUE;
            for (LivingEntity e : targets) {
                double d = e.position().distanceTo(eye);
                if (d < bestDist) {
                    bestDist = d;
                    target = e;
                }
            }

            if (target == null) return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_INVALID;

            // Check if boss (Ender Dragon/Wither) - they resist
            String targetType = target.getType().toString();
            if (targetType.contains("ender_dragon") || targetType.contains("wither")) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_PERMISSION;
            }

            // Check safety for both destinations
            Vec3 casterPos = actor.position();
            Vec3 targetPos = target.position();

            if (!SafeSpot.isSafe(level, actor.entity(), targetPos) ||
                !SafeSpot.isSafe(level, target, casterPos)) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_PERMISSION;
            }

            // Check teleport permission via AcademySafety
            if (AcademySafety.checkTeleport(actor, actor.entity(), targetPos, SPATIAL_EXCHANGE) == null ||
                AcademySafety.checkTeleport(actor, target, casterPos, SPATIAL_EXCHANGE) == null) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_PERMISSION;
            }

            // Perform swap
            if (!AcademySafety.teleport(actor, actor.entity(), targetPos, SPATIAL_EXCHANGE)) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_PERMISSION;
            }
            if (!AcademySafety.teleport(actor, target, casterPos, SPATIAL_EXCHANGE)) {
                // Rollback caster
                AcademySafety.teleport(actor, actor.entity(), casterPos, SPATIAL_EXCHANGE);
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_PERMISSION;
            }

            // Reset fall distance
            actor.entity().fallDistance = 0;
            target.fallDistance = 0;

            // Send effects
            sendExchangeEffect(actor.level(), casterPos, targetPos);

            ctx.setCooldown(20 * SpatialConfig.COMMON.exchangeCooldownSeconds.get());
            return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.PERFORMED;
        }

        private void sendExchangeEffect(ServerLevel level, Vec3 posA, Vec3 posB) {
            // Send expanding rings at both positions + connecting flash
            FxSpawnPacket packet = new FxSpawnPacket(6, PortalManager.createGroupId(), null, posA, Vec3.ZERO, posB, Vec3.ZERO, 0, 40, 0);
            NetworkHandler.sendToNearby(level, posA, 160, packet);
        }
    }

    /**
     * 4. Distortion Field (INSTANT): Field bends incoming projectile trajectories.
     * Follows owner, radius 6, lasts 5s.
     */
    private static class DistortionFieldBehavior implements SkillBehavior {
        @Override
        public io.github.pinchan4273.reacademycraft.api.ActivationOutcome start(ActivationContext ctx) {
            var actor = ctx.actor();
            if (!ctx.pay(SpatialConfig.COMMON.fieldCpCost.get(), SpatialConfig.COMMON.fieldOverloadCost.get())) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_RESOURCE;
            }

            long groupId = PortalManager.createGroupId();
            long expireTick = actor.level().getGameTime() + 20L * SpatialConfig.COMMON.fieldDurationSeconds.get();

            PortalGroup group = new PortalGroup.Builder()
                .id(groupId)
                .kind(PortalGroup.Kind.REDIRECT) // Reuse redirect kind for field logic
                .owner(actor.id())
                .level(actor.level())
                .expireTick(expireTick)
                .capacity(0)
                .includeItems(false)
                .affectOwn(false)
                .resistBossOwners(false)
                .fieldRadius(SpatialConfig.COMMON.fieldRadius.get())
                .anchorPos(actor.position())
                .build();

            // No actual portals for field - just visual
            PortalManager.registerGroup(group);

            ctx.setCooldown(20 * SpatialConfig.COMMON.fieldCooldownSeconds.get() + 20 * SpatialConfig.COMMON.fieldDurationSeconds.get());
            return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.PERFORMED;
        }
    }

    /**
     * 5. Inversion Strike (TOGGLE): Concentrated spatial attack through an opening.
     * Delay ~6 ticks, find target, pick exit point ~3.5 blocks away from target
     * at first clear angle (prefer behind, then flanks, then above), deal damage + push.
     */
    private static class InversionStrikeBehavior implements SkillBehavior {
        private static final java.util.Map<UUID, StrikeState> ACTIVE_STRIKES = new java.util.concurrent.ConcurrentHashMap<>();

        private static class StrikeState {
            Vec3 targetPos;
            Vec3 exitPos;
            int delayTicks;
            long startTick;
        }

        @Override
        public io.github.pinchan4273.reacademycraft.api.ActivationOutcome start(ActivationContext ctx) {
            var actor = ctx.actor();
            UUID owner = actor.id();

            StrikeState existing = ACTIVE_STRIKES.get(owner);
            if (existing != null) {
                // Second press = early collapse
                ACTIVE_STRIKES.remove(owner);
                // Trigger immediate strike at stored target
                executeStrike(ctx, existing.targetPos, existing.exitPos);
                ctx.setCooldown(20 * SpatialConfig.COMMON.strikeCooldownSeconds.get());
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.STARTED;
            }

            // First press: find target and prepare
            if (!ctx.pay(SpatialConfig.COMMON.strikeCpCost.get(), SpatialConfig.COMMON.strikeOverloadCost.get())) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_RESOURCE;
            }

            Vec3 targetPos = findStrikeTarget(actor);
            if (targetPos == null) return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_INVALID;

            Vec3 exitPos = SafeSpot.findStrikeExit(actor.level(), actor.entity(), targetPos, 3.5);
            if (exitPos == null) return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_INVALID;

            StrikeState state = new StrikeState();
            state.targetPos = targetPos;
            state.exitPos = exitPos;
            state.delayTicks = SpatialConfig.COMMON.strikeDelayTicks.get();
            state.startTick = actor.level().getGameTime();
            ACTIVE_STRIKES.put(owner, state);

            // Create visual portals
            long groupId = PortalManager.createGroupId();
            long expireTick = state.startTick + 200; // Plenty of time for delay

            PortalGroup group = new PortalGroup.Builder()
                .id(groupId)
                .kind(PortalGroup.Kind.STRIKE)
                .owner(owner)
                .level(actor.level())
                .expireTick(expireTick)
                .build();

            Portal portalA = new Portal(actor.position().add(actor.look().scale(2)), actor.look(), 0.6f, 0.6f, true, groupId, 0, expireTick);
            Portal portalB = new Portal(exitPos, targetPos.subtract(exitPos).normalize(), 0.6f, 0.6f, true, groupId, 1, expireTick);
            group.portals.add(portalA);
            group.portals.add(portalB);

            PortalManager.registerGroup(group);

            return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.STARTED;
        }

        @Override
        public boolean tick(ActivationContext ctx) {
            var actor = ctx.actor();
            UUID owner = actor.id();
            StrikeState state = ACTIVE_STRIKES.get(owner);

            if (state == null) return false; // Ended early

            int age = ctx.age();
            if (age >= state.delayTicks) {
                // Execute strike
                executeStrike(ctx, state.targetPos, state.exitPos);
                ACTIVE_STRIKES.remove(owner);
                return false; // End session
            }
            return true; // Continue waiting
        }

        @Override
        public void stop(ActivationContext ctx, io.github.pinchan4273.reacademycraft.api.StopReason reason) {
            ACTIVE_STRIKES.remove(ctx.actor().id());
        }

        private Vec3 findStrikeTarget(io.github.pinchan4273.reacademycraft.api.mc.AbilityActor actor) {
            ServerLevel level = actor.level();
            Vec3 eye = actor.position();
            Vec3 look = actor.look();
            double range = SpatialConfig.COMMON.strikeRange.get();

            var clip = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor.entity()));
            Vec3 rayEnd = clip.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? eye.add(look.scale(range)) : clip.getLocation();

            // Find nearest living entity within 3 blocks of ray end
            AABB searchBox = new AABB(rayEnd, rayEnd).inflate(3.0);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != actor.entity() && e.isAlive());

            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (LivingEntity e : targets) {
                double d = e.position().distanceTo(rayEnd);
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                }
            }

            return best != null ? best.position() : rayEnd;
        }

        private void executeStrike(ActivationContext ctx, Vec3 targetPos, Vec3 exitPos) {
            var actor = ctx.actor();
            ServerLevel level = actor.level();

            // Find entities near target
            AABB hitBox = new AABB(targetPos, targetPos).inflate(4.0);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, hitBox,
                e -> e != actor.entity() && e.isAlive());

            float baseDamage = SpatialConfig.COMMON.strikeBaseDamage.get().floatValue();
            float profMult = SpatialConfig.COMMON.strikeProficiencyDamageMultiplier.get().floatValue();
            float damage = baseDamage + baseDamage * profMult * ctx.proficiency();

            for (LivingEntity target : targets) {
                if (AcademySafety.mayAttack(actor, target, INVERSION_STRIKE)) {
                    AcademySafety.attack(actor, target, INVERSION_STRIKE, damage);
                    // Small push away from exit point
                    Vec3 push = target.position().subtract(exitPos).normalize().scale(0.8);
                    target.push(push.x, 0.3, push.z);
                    target.hurtMarked = true;
                }
            }

            // Send strike effect
            FxSpawnPacket packet = new FxSpawnPacket(4, PortalManager.createGroupId(), actor.id(),
                actor.position().add(actor.look().scale(2)), actor.look(), exitPos, targetPos.subtract(exitPos).normalize(), 0, 40, 0);
            NetworkHandler.sendToNearby(level, targetPos, 160, packet);
        }
    }

    /**
     * 6. Fold Step (INSTANT): Two openings and travel between them.
     * Max 16 blocks, horizontal and vertical, safe destination required.
     */
    private static class FoldStepBehavior implements SkillBehavior {
        @Override
        public io.github.pinchan4273.reacademycraft.api.ActivationOutcome start(ActivationContext ctx) {
            var actor = ctx.actor();
            if (!ctx.pay(SpatialConfig.COMMON.foldCpCost.get(), SpatialConfig.COMMON.foldOverloadCost.get())) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_RESOURCE;
            }

            ServerLevel level = actor.level();
            Vec3 eye = actor.position();
            Vec3 look = actor.look();
            double maxDist = SpatialConfig.COMMON.foldMaxDistance.get();

            // Search from aim point backwards along ray with small vertical adjustments
            Vec3 targetPos = null;
            for (double d = maxDist; d >= 1; d -= 0.5) {
                for (double vy = -1; vy <= 1; vy += 0.5) {
                    Vec3 candidate = eye.add(look.scale(d)).add(0, vy, 0);
                    if (SafeSpot.isSafe(level, actor.entity(), candidate)) {
                        targetPos = candidate;
                        break;
                    }
                }
                if (targetPos != null) break;
            }

            if (targetPos == null) return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_INVALID;

            // Check teleport permission
            if (AcademySafety.checkTeleport(actor, actor.entity(), targetPos, FOLD_STEP) == null) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_PERMISSION;
            }

            // Perform teleport
            if (!AcademySafety.teleport(actor, actor.entity(), targetPos, FOLD_STEP)) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_PERMISSION;
            }

            actor.entity().fallDistance = 0;

            // Create visual portals
            long groupId = PortalManager.createGroupId();
            long expireTick = level.getGameTime() + 40;

            PortalGroup group = new PortalGroup.Builder()
                .id(groupId)
                .kind(PortalGroup.Kind.FOLD)
                .owner(actor.id())
                .level(level)
                .expireTick(expireTick)
                .build();

            Portal portalA = new Portal(eye.add(look.scale(1)), look, 1.0f, 1.5f, false, groupId, 0, expireTick);
            Portal portalB = new Portal(targetPos, look.scale(-1), 1.0f, 1.5f, false, groupId, 1, expireTick);
            group.portals.add(portalA);
            group.portals.add(portalB);

            PortalManager.registerGroup(group);

            ctx.setCooldown(20 * SpatialConfig.COMMON.foldCooldownSeconds.get());
            return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.PERFORMED;
        }
    }

    /**
     * 7. Spatial Cage (TOGGLE): Four connected openings around an area, radius 8.
     * Redirect projectiles, soft wall restricts escape routes.
     */
    private static class SpatialCageBehavior implements SkillBehavior {
        @Override
        public io.github.pinchan4273.reacademycraft.api.ActivationOutcome start(ActivationContext ctx) {
            var actor = ctx.actor();
            if (!ctx.pay(SpatialConfig.COMMON.cageCpCost.get(), SpatialConfig.COMMON.cageOverloadCost.get())) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_RESOURCE;
            }

            ServerLevel level = actor.level();
            Vec3 eye = actor.position();
            Vec3 look = actor.look();
            double range = SpatialConfig.COMMON.linkPlacementRange.get(); // Reuse link range (24)

            var clip = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor.entity()));
            Vec3 center = clip.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? eye.add(look.scale(range)) : clip.getLocation();

            long groupId = PortalManager.createGroupId();
            long expireTick = level.getGameTime() + 20L * SpatialConfig.COMMON.cageDurationSeconds.get();
            float radius = SpatialConfig.COMMON.cageRadius.get();

            PortalGroup group = new PortalGroup.Builder()
                .id(groupId)
                .kind(PortalGroup.Kind.CAGE)
                .owner(actor.id())
                .level(level)
                .expireTick(expireTick)
                .capacity(0)
                .includeItems(false)
                .affectOwn(false)
                .resistBossOwners(false)
                .fieldRadius(radius)
                .anchorPos(center)
                .build();

            // Create 4 vertical portals on ring facing outward
            for (int i = 0; i < 4; i++) {
                double angle = Math.PI / 2 * i;
                Vec3 pos = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                Vec3 normal = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Portal portal = new Portal(pos, normal, 1.0f, 3.0f, false, groupId, i, expireTick);
                group.portals.add(portal);
            }

            PortalManager.registerGroup(group);

            // Cooldown from end of effect
            ctx.setCooldown(20 * SpatialConfig.COMMON.cageCooldownSeconds.get() + 20 * SpatialConfig.COMMON.cageDurationSeconds.get());
            return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.STARTED;
        }

        @Override
        public void stop(ActivationContext ctx, io.github.pinchan4273.reacademycraft.api.StopReason reason) {
            var actor = ctx.actor();
            PortalManager.getAllGroups().stream()
                .filter(g -> g.owner.equals(actor.id()) && g.kind == PortalGroup.Kind.CAGE)
                .findFirst()
                .ifPresent(g -> PortalManager.unregisterGroup(g.id));
        }
    }

    /**
     * 8. Absolute Inversion (INSTANT - Ultimate): Two enormous spatial surfaces
     * reverse relationship between locations, redirect multiple attacks.
     * Placement range 30, 5s duration, limited capacity (12), bosses resist.
     */
    private static class AbsoluteInversionBehavior implements SkillBehavior {
        @Override
        public io.github.pinchan4273.reacademycraft.api.ActivationOutcome start(ActivationContext ctx) {
            var actor = ctx.actor();
            if (!ctx.pay(SpatialConfig.COMMON.absoluteCpCost.get(), SpatialConfig.COMMON.absoluteOverloadCost.get())) {
                return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.REFUSED_RESOURCE;
            }

            ServerLevel level = actor.level();
            Vec3 eye = actor.position();
            Vec3 look = actor.look();
            double range = SpatialConfig.COMMON.absolutePlacementRange.get();

            var clip = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor.entity()));
            Vec3 posA = clip.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? eye.add(look.scale(range)) : clip.getLocation();
            Vec3 normalA = look.scale(-1); // Facing caster

            // Second surface behind caster
            Vec3 posB = eye.add(look.scale(-3));
            Vec3 normalB = look; // Facing away from caster

            long groupId = PortalManager.createGroupId();
            long expireTick = level.getGameTime() + 20L * SpatialConfig.COMMON.absoluteDurationSeconds.get();
            float width = SpatialConfig.COMMON.absoluteSurfaceWidth.get();
            float height = SpatialConfig.COMMON.absoluteSurfaceHeight.get();

            PortalGroup group = new PortalGroup.Builder()
                .id(groupId)
                .kind(PortalGroup.Kind.ABSOLUTE)
                .owner(actor.id())
                .level(level)
                .expireTick(expireTick)
                .capacity(SpatialConfig.COMMON.absoluteCapacity.get())
                .includeItems(false)
                .affectOwn(false)
                .resistBossOwners(SpatialConfig.COMMON.absoluteBossResist.get())
                .build();

            Portal portalA = new Portal(posA, normalA, width / 2, height / 2, false, groupId, 0, expireTick);
            Portal portalB = new Portal(posB, normalB, width / 2, height / 2, false, groupId, 1, expireTick);
            group.portals.add(portalA);
            group.portals.add(portalB);

            PortalManager.registerGroup(group);

            ctx.setCooldown(20 * SpatialConfig.COMMON.absoluteCooldownSeconds.get() + 20 * SpatialConfig.COMMON.absoluteDurationSeconds.get());
            return io.github.pinchan4273.reacademycraft.api.ActivationOutcome.PERFORMED;
        }
    }
}