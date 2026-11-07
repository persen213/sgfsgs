package io.spatialinversion.addon.client;

import io.spatialinversion.addon.core.Portal;
import io.spatialinversion.addon.network.packet.FxSpawnPacket;
import io.spatialinversion.addon.network.packet.FxEndPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side manager for all visual effects.
 * Handles spawning, updating, and rendering of portal effects.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "spatialinversion", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEffectManager {
    private static final Map<Long, ClientEffect> ACTIVE_EFFECTS = new ConcurrentHashMap<>();

    public static void spawnEffect(FxSpawnPacket packet) {
        ClientEffect effect = switch (packet.kind()) {
            case 0 -> new LinkEffect(packet);
            case 1 -> new RedirectEffect(packet);
            case 2 -> new CageEffect(packet);
            case 3 -> new AbsoluteEffect(packet);
            case 4 -> new StrikeEffect(packet);
            case 5 -> new FoldEffect(packet);
            default -> null;
        };
        if (effect != null) {
            ACTIVE_EFFECTS.put(packet.groupId(), effect);
        }
    }

    public static void endEffect(long groupId) {
        ClientEffect effect = ACTIVE_EFFECTS.remove(groupId);
        if (effect != null) {
            effect.end();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        // Frustum culling
        var frustum = event.getFrustum();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        for (ClientEffect effect : ACTIVE_EFFECTS.values()) {
            if (effect.isExpired()) {
                ACTIVE_EFFECTS.remove(effect.groupId());
                continue;
            }

            AABB bounds = effect.getBoundingBox();
            if (bounds != null && !frustum.isVisible(bounds)) continue;

            effect.render(poseStack, bufferSource, cameraPos, event.getPartialTick());
        }
    }

    public static void clearAll() {
        for (ClientEffect effect : ACTIVE_EFFECTS.values()) {
            effect.end();
        }
        ACTIVE_EFFECTS.clear();
    }

    /**
     * Base class for all client effects.
     */
    public abstract static class ClientEffect {
        protected final long groupId;
        protected final int kind;
        protected final java.util.UUID owner;
        protected final int durationTicks;
        protected int age = 0;
        protected float openProgress = 0f;
        protected boolean closing = false;

        public ClientEffect(FxSpawnPacket packet) {
            this.groupId = packet.groupId();
            this.kind = packet.kind();
            this.owner = packet.owner();
            this.durationTicks = packet.durationTicks();
        }

        public long groupId() { return groupId; }

        public boolean isExpired() {
            return age >= durationTicks + 8;
        }

        public abstract AABB getBoundingBox();

        public abstract void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float partialTick);

        public void tick() {
            age++;
            if (age < 8) {
                openProgress = age / 8f;
            } else if (age > durationTicks) {
                closing = true;
                openProgress = 1f - (age - durationTicks) / 8f;
                if (openProgress < 0) openProgress = 0;
            }
        }

        public void end() {
            closing = true;
            openProgress = 0f;
        }

        protected void setupGlowState() {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(RenderSystem.SrcFactor.SRC_ALPHA, RenderSystem.DstFactor.ONE);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
        }

        protected void restoreGlowState() {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }

        protected void drawQuad(VertexConsumer buffer, Matrix4f mat, float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float r, float g, float b, float a) {
            buffer.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
            buffer.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
            buffer.vertex(mat, x3, y3, z3).color(r, g, b, a).endVertex();
            buffer.vertex(mat, x4, y4, z4).color(r, g, b, a).endVertex();
        }

        protected void drawLine(VertexConsumer buffer, Matrix4f mat, float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float b, float a) {
            buffer.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
            buffer.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
        }
    }

    // =================== EFFECT IMPLEMENTATIONS ===================

    /**
     * Link Effect: Two anchors with crossed ribbon beam, swirling portal faces, dashed rims.
     */
    public static class LinkEffect extends ClientEffect {
        private final Vec3 posA, normalA, posB, normalB;
        private final Portal portalA, portalB;

        public LinkEffect(FxSpawnPacket packet) {
            super(packet);
            this.posA = packet.posA();
            this.normalA = packet.normalA();
            this.posB = packet.posB();
            this.normalB = packet.normalB();
            this.portalA = new Portal(posA, normalA, 1.2f, 1.2f, true, groupId, 0, durationTicks);
            this.portalB = new Portal(posB, normalB, 1.2f, 1.2f, true, groupId, 1, durationTicks);
        }

        @Override
        public AABB getBoundingBox() {
            double r = 2.0;
            return new AABB(posA.x - r, posA.y - r, posA.z - r, posA.x + r, posA.y + r, posA.z + r)
                .minmax(new AABB(posB.x - r, posB.y - r, posB.z - r, posB.x + r, posB.y + r, posB.z + r));
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float partialTick) {
            tick();
            if (openProgress <= 0) return;

            float alpha = openProgress * 0.7f;
            float pulse = (float) (Math.sin((age + partialTick) * 0.15) * 0.1 + 0.9);

            setupGlowState();
            try {
                renderPortalFace(poseStack, bufferSource, cameraPos, portalA, alpha * pulse);
                renderPortalFace(poseStack, bufferSource, cameraPos, portalB, alpha * pulse);
                renderRim(poseStack, bufferSource, cameraPos, portalA, alpha, age + partialTick);
                renderRim(poseStack, bufferSource, cameraPos, portalB, alpha, age + partialTick);
                renderInnerRing(poseStack, bufferSource, cameraPos, portalA, alpha, age + partialTick);
                renderInnerRing(poseStack, bufferSource, cameraPos, portalB, alpha, age + partialTick);
                renderCrossedBeam(poseStack, bufferSource, cameraPos, alpha * pulse);
            } finally {
                restoreGlowState();
            }
        }

        private void renderPortalFace(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Portal portal, float alpha) {
            poseStack.pushPose();
            poseStack.translate(portal.pos.x - cameraPos.x, portal.pos.y - cameraPos.y, portal.pos.z - cameraPos.z);

            float yaw = (float) Math.toDegrees(Math.atan2(portal.normal.z, portal.normal.x)) - 90;
            float pitch = (float) Math.toDegrees(-Math.asin(portal.normal.y));
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(pitch));

            var buffer = bufferSource.getBuffer(RenderType.translucent());
            Matrix4f mat = poseStack.last().pose();
            float size = 1.2f;
            drawQuad(buffer, mat,
                -size, -size, 0,
                size, -size, 0,
                size, size, 0,
                -size, size, 0,
                0.4f, 0.85f, 1.0f, alpha);

            poseStack.popPose();
        }

        private void renderRim(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Portal portal, float alpha, float time) {
            poseStack.pushPose();
            poseStack.translate(portal.pos.x - cameraPos.x, portal.pos.y - cameraPos.y, portal.pos.z - cameraPos.z);

            float yaw = (float) Math.toDegrees(Math.atan2(portal.normal.z, portal.normal.x)) - 90;
            float pitch = (float) Math.toDegrees(-Math.asin(portal.normal.y));
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(pitch));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(time * 30));

            var buffer = bufferSource.getBuffer(RenderType.lines());
            Matrix4f mat = poseStack.last().pose();
            int segments = 32;
            float radius = 1.2f;
            for (int i = 0; i < segments; i++) {
                double a1 = 2 * Math.PI * i / segments;
                double a2 = 2 * Math.PI * (i + 1) / segments;
                float x1 = (float) (Math.cos(a1) * radius);
                float z1 = (float) (Math.sin(a1) * radius);
                float x2 = (float) (Math.cos(a2) * radius);
                float z2 = (float) (Math.sin(a2) * radius);
                if (i % 3 == 0) {
                    drawLine(buffer, mat, x1, 0, z1, x2, 0, z2, 0.4f, 0.85f, 1.0f, alpha);
                }
            }
            poseStack.popPose();
        }

        private void renderInnerRing(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Portal portal, float alpha, float time) {
            poseStack.pushPose();
            poseStack.translate(portal.pos.x - cameraPos.x, portal.pos.y - cameraPos.y, portal.pos.z - cameraPos.z);

            float yaw = (float) Math.toDegrees(Math.atan2(portal.normal.z, portal.normal.x)) - 90;
            float pitch = (float) Math.toDegrees(-Math.asin(portal.normal.y));
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(pitch));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(-time * 45));

            var buffer = bufferSource.getBuffer(RenderType.lines());
            Matrix4f mat = poseStack.last().pose();
            int segments = 24;
            float radius = 0.6f;
            for (int i = 0; i < segments; i++) {
                double a1 = 2 * Math.PI * i / segments;
                double a2 = 2 * Math.PI * (i + 1) / segments;
                float x1 = (float) (Math.cos(a1) * radius);
                float z1 = (float) (Math.sin(a1) * radius);
                float x2 = (float) (Math.cos(a2) * radius);
                float z2 = (float) (Math.sin(a2) * radius);
                if (i % 2 == 0) {
                    drawLine(buffer, mat, x1, 0, z1, x2, 0, z2, 0.22f, 0.45f, 1.0f, alpha);
                }
            }
            poseStack.popPose();
        }

        private void renderCrossedBeam(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float alpha) {
            poseStack.pushPose();
            Vec3 mid = posA.lerp(posB, 0.5);
            poseStack.translate(mid.x - cameraPos.x, mid.y - cameraPos.y, mid.z - cameraPos.z);

            var buffer = bufferSource.getBuffer(RenderType.translucent());
            Matrix4f mat = poseStack.last().pose();
            float len = (float) posA.distanceTo(posB);

            // Cross ribbon 1
            drawQuad(buffer, mat,
                -len/2, 0, -0.1f,
                len/2, 0, -0.1f,
                len/2, 0, 0.1f,
                -len/2, 0, 0.1f,
                0.4f, 0.85f, 1.0f, alpha);

            // Cross ribbon 2 (rotated 90)
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(90));
            drawQuad(buffer, mat,
                -len/2, 0, -0.1f,
                len/2, 0, -0.1f,
                len/2, 0, 0.1f,
                -len/2, 0, 0.1f,
                0.4f, 0.85f, 1.0f, alpha);

            poseStack.popPose();
        }
    }

    /**
     * Redirect Effect: Single portal with outward-flowing rings.
     */
    public static class RedirectEffect extends ClientEffect {
        private final Vec3 pos, normal;
        private final Portal portal;

        public RedirectEffect(FxSpawnPacket packet) {
            super(packet);
            this.pos = packet.posA();
            this.normal = packet.normalA();
            this.portal = new Portal(pos, normal, 1.5f, 1.5f, true, groupId, 0, durationTicks);
        }

        @Override
        public AABB getBoundingBox() {
            double r = 2.5;
            return new AABB(pos.x - r, pos.y - r, pos.z - r, pos.x + r, pos.y + r, pos.z + r);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float partialTick) {
            tick();
            if (openProgress <= 0) return;

            float alpha = openProgress * 0.8f;
            setupGlowState();
            try {
                renderPortalFace(poseStack, bufferSource, cameraPos, portal, alpha);
                renderFlowRings(poseStack, bufferSource, cameraPos, portal, alpha, age + partialTick);
            } finally {
                restoreGlowState();
            }
        }

        private void renderPortalFace(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Portal portal, float alpha) {
            poseStack.pushPose();
            poseStack.translate(portal.pos.x - cameraPos.x, portal.pos.y - cameraPos.y, portal.pos.z - cameraPos.z);

            float yaw = (float) Math.toDegrees(Math.atan2(portal.normal.z, portal.normal.x)) - 90;
            float pitch = (float) Math.toDegrees(-Math.asin(portal.normal.y));
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(pitch));

            var buffer = bufferSource.getBuffer(RenderType.translucent());
            Matrix4f mat = poseStack.last().pose();
            float size = 1.5f;
            drawQuad(buffer, mat,
                -size, -size, 0,
                size, -size, 0,
                size, size, 0,
                -size, size, 0,
                0.45f, 0.82f, 1.0f, alpha);

            poseStack.popPose();
        }

        private void renderFlowRings(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Portal portal, float alpha, float time) {
            poseStack.pushPose();
            poseStack.translate(portal.pos.x - cameraPos.x, portal.pos.y - cameraPos.y, portal.pos.z - cameraPos.z);

            float yaw = (float) Math.toDegrees(Math.atan2(portal.normal.z, portal.normal.x)) - 90;
            float pitch = (float) Math.toDegrees(-Math.asin(portal.normal.y));
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(pitch));

            var buffer = bufferSource.getBuffer(RenderType.lines());
            Matrix4f mat = poseStack.last().pose();
            int ringCount = 4;
            for (int r = 0; r < ringCount; r++) {
                float progress = (time * 0.02f + r * 0.25f) % 1.0f;
                float radius = 0.5f + progress * 1.5f;
                float ringAlpha = alpha * (1.0f - progress);
                int segments = 24;
                for (int i = 0; i < segments; i++) {
                    double a1 = 2 * Math.PI * i / segments;
                    double a2 = 2 * Math.PI * (i + 1) / segments;
                    float x1 = (float) (Math.cos(a1) * radius);
                    float z1 = (float) (Math.sin(a1) * radius);
                    float x2 = (float) (Math.cos(a2) * radius);
                    float z2 = (float) (Math.sin(a2) * radius);
                    drawLine(buffer, mat, x1, 0, z1, x2, 0, z2, 0.4f, 0.85f, 1.0f, ringAlpha);
                }
            }
            poseStack.popPose();
        }
    }

    /**
     * Cage Effect: Four tall portals, floor/ceiling rings, cylinder wall, pillars.
     */
    public static class CageEffect extends ClientEffect {
        private final Vec3 center;
        private final float radius;
        private final Portal[] portals = new Portal[4];

        public CageEffect(FxSpawnPacket packet) {
            super(packet);
            this.center = packet.posA();
            this.radius = packet.radius();
            for (int i = 0; i < 4; i++) {
                double angle = Math.PI / 2 * i;
                Vec3 pos = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                Vec3 normal = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                portals[i] = new Portal(pos, normal, 1.0f, 3.0f, false, groupId, i, durationTicks);
            }
        }

        @Override
        public AABB getBoundingBox() {
            double r = radius + 3;
            return new AABB(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float partialTick) {
            tick();
            if (openProgress <= 0) return;

            float alpha = openProgress * 0.75f;
            setupGlowState();
            try {
                for (Portal p : portals) {
                    renderPortalFace(poseStack, bufferSource, cameraPos, p, alpha);
                }
                renderCylinderWall(poseStack, bufferSource, cameraPos, alpha);
                renderFloorCeilingRings(poseStack, bufferSource, cameraPos, alpha);
                renderPillars(poseStack, bufferSource, cameraPos, alpha);
            } finally {
                restoreGlowState();
            }
        }

        private void renderPortalFace(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Portal portal, float alpha) {
            poseStack.pushPose();
            poseStack.translate(portal.pos.x - cameraPos.x, portal.pos.y - cameraPos.y, portal.pos.z - cameraPos.z);

            float yaw = (float) Math.toDegrees(Math.atan2(portal.normal.z, portal.normal.x)) - 90;
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));

            var buffer = bufferSource.getBuffer(RenderType.translucent());
            Matrix4f mat = poseStack.last().pose();
            float hw = 1.0f, hh = 3.0f;
            drawQuad(buffer, mat,
                -hw, 0, 0,
                hw, 0, 0,
                hw, hh, 0,
                -hw, hh, 0,
                0.45f, 0.82f, 1.0f, alpha);

            poseStack.popPose();
        }

        private void renderCylinderWall(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float alpha) {
            poseStack.pushPose();
            poseStack.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);

            var buffer = bufferSource.getBuffer(RenderType.translucent());
            Matrix4f mat = poseStack.last().pose();
            int segments = 32;
            float h = 3.0f;
            for (int i = 0; i < segments; i++) {
                double a1 = 2 * Math.PI * i / segments;
                double a2 = 2 * Math.PI * (i + 1) / segments;
                float x1 = (float) (Math.cos(a1) * radius);
                float z1 = (float) (Math.sin(a1) * radius);
                float x2 = (float) (Math.cos(a2) * radius);
                float z2 = (float) (Math.sin(a2) * radius);
                float a = (float) (0.3 + 0.4 * Math.sin(age * 0.1 + i));
                drawQuad(buffer, mat,
                    x1, 0, z1,
                    x2, 0, z2,
                    x2, h, z2,
                    x1, h, z1,
                    0.4f, 0.85f, 1.0f, alpha * a);
            }
            poseStack.popPose();
        }

        private void renderFloorCeilingRings(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float alpha) {
            poseStack.pushPose();
            poseStack.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);

            var buffer = bufferSource.getBuffer(RenderType.lines());
            Matrix4f mat = poseStack.last().pose();
            int segments = 32;
            for (int ring = 0; ring < 2; ring++) {
                float y = ring == 0 ? 0 : 3.0f;
                for (int i = 0; i < segments; i++) {
                    double a1 = 2 * Math.PI * i / segments;
                    double a2 = 2 * Math.PI * (i + 1) / segments;
                    float x1 = (float) (Math.cos(a1) * radius);
                    float z1 = (float) (Math.sin(a1) * radius);
                    float x2 = (float) (Math.cos(a2) * radius);
                    float z2 = (float) (Math.sin(a2) * radius);
                    drawLine(buffer, mat, x1, y, z1, x2, y, z2, 0.93f, 0.98f, 1.0f, alpha);
                }
            }
            poseStack.popPose();
        }

        private void renderPillars(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float alpha) {
            poseStack.pushPose();
            poseStack.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);

            var buffer = bufferSource.getBuffer(RenderType.translucent());
            Matrix4f mat = poseStack.last().pose();
            for (int i = 0; i < 4; i++) {
                double angle = Math.PI / 2 * i;
                float x = (float) (Math.cos(angle) * radius);
                float z = (float) (Math.sin(angle) * radius);
                drawQuad(buffer, mat,
                    x - 0.1f, 0, z - 0.1f,
                    x + 0.1f, 0, z - 0.1f,
                    x + 0.1f, 3.0f, z + 0.1f,
                    x - 0.1f, 3.0f, z + 0.1f,
                    1.0f, 1.0f, 1.0f, alpha);
            }
            poseStack.popPose();
        }
    }

    /**
     * Absolute Effect: Two giant rectangular grid surfaces with scanlines and glowing frame.
     */
    public static class AbsoluteEffect extends ClientEffect {
        private final Vec3 posA, normalA, posB, normalB;
        private final float width, height;
        private final Portal portalA, portalB;

        public AbsoluteEffect(FxSpawnPacket packet) {
            super(packet);
            this.posA = packet.posA();
            this.normalA = packet.normalA();
            this.posB = packet.posB();
            this.normalB = packet.normalB();
            this.width = packet.aux() > 0 ? packet.aux() : 13f;
            this.height = packet.radius() > 0 ? packet.radius() : 9f;
            this.portalA = new Portal(posA, normalA, width / 2, height / 2, false, groupId, 0, durationTicks);
            this.portalB = new Portal(posB, normalB, width / 2, height / 2, false, groupId, 1, durationTicks);
        }

        @Override
        public AABB getBoundingBox() {
            double r = Math.max(width, height) / 2 + 4;
            return new AABB(posA.x - r, posA.y - r, posA.z - r, posA.x + r, posA.y + r, posA.z + r)
                .minmax(new AABB(posB.x - r, posB.y - r, posB.z - r, posB.x + r, posB.y + r, posB.z + r));
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float partialTick) {
            tick();
            if (openProgress <= 0) return;

            float alpha = openProgress * 0.8f;
            setupGlowState();
            try {
                renderGridSurface(poseStack, bufferSource, cameraPos, portalA, alpha);
                renderGridSurface(poseStack, bufferSource, cameraPos, portalB, alpha);
                renderGlowingFrame(poseStack, bufferSource, cameraPos, portalA, alpha);
                renderGlowingFrame(poseStack, bufferSource, cameraPos, portalB, alpha);
            } finally {
                restoreGlowState();
            }
        }

        private void renderGridSurface(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Portal portal, float alpha) {
            poseStack.pushPose();
            poseStack.translate(portal.pos.x - cameraPos.x, portal.pos.y - cameraPos.y, portal.pos.z - cameraPos.z);

            float yaw = (float) Math.toDegrees(Math.atan2(portal.normal.z, portal.normal.x)) - 90;
            float pitch = (float) Math.toDegrees(-Math.asin(portal.normal.y));
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(pitch));

            var buffer = bufferSource.getBuffer(RenderType.lines());
            Matrix4f mat = poseStack.last().pose();
            float hw = width / 2f, hh = height / 2f;
            int grid = 10;

            for (int i = 0; i <= grid; i++) {
                float x = -hw + 2 * hw * i / grid;
                drawLine(buffer, mat, x, -hh, 0, x, hh, 0, 0.4f, 0.85f, 1.0f, alpha);
            }
            for (int i = 0; i <= grid; i++) {
                float y = -hh + 2 * hh * i / grid;
                drawLine(buffer, mat, -hw, y, 0, hw, y, 0, 0.4f, 0.85f, 1.0f, alpha);
            }

            // Scanlines (animated)
            float scanOffset = (age * 0.05f) % (2 * hh / grid);
            for (int i = 0; i < grid * 2; i++) {
                float y = -hh + (i * hh / grid + scanOffset) % (2 * hh);
                drawLine(buffer, mat, -hw, y, 0, hw, y, 0, 0.93f, 0.98f, 1.0f, alpha * 0.5f);
            }

            poseStack.popPose();
        }

        private void renderGlowingFrame(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Portal portal, float alpha) {
            poseStack.pushPose();
            poseStack.translate(portal.pos.x - cameraPos.x, portal.pos.y - cameraPos.y, portal.pos.z - cameraPos.z);

            float yaw = (float) Math.toDegrees(Math.atan2(portal.normal.z, portal.normal.x)) - 90;
            float pitch = (float) Math.toDegrees(-Math.asin(portal.normal.y));
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(pitch));

            var buffer = bufferSource.getBuffer(RenderType.lines());
            Matrix4f mat = poseStack.last().pose();
            float hw = width / 2f, hh = height / 2f;
            float pulse = (float) (0.5 + 0.5 * Math.sin(age * 0.2));

            drawLine(buffer, mat, -hw, -hh, 0, hw, -hh, 0, 0.4f, 0.85f, 1.0f, alpha * pulse);
            drawLine(buffer, mat, hw, -hh, 0, hw, hh, 0, 0.4f, 0.85f, 1.0f, alpha * pulse);
            drawLine(buffer, mat, hw, hh, 0, -hw, hh, 0, 0.4f, 0.85f, 1.0f, alpha * pulse);
            drawLine(buffer, mat, -hw, hh, 0, -hw, -hh, 0, 0.4f, 0.85f, 1.0f, alpha * pulse);

            poseStack.popPose();
        }
    }

    /**
     * Strike Effect: Two small portals + jagged lightning ribbon + ground impact ring.
     */
    public static class StrikeEffect extends ClientEffect {
        private final Vec3 posA, normalA, posB, normalB;
        private final Portal portalA, portalB;
        private final List<Vec3> lightningSegments = new ArrayList<>();

        public StrikeEffect(FxSpawnPacket packet) {
            super(packet);
            this.posA = packet.posA();
            this.normalA = packet.normalA();
            this.posB = packet.posB();
            this.normalB = packet.normalB();
            this.portalA = new Portal(posA, normalA, 0.6f, 0.6f, true, groupId, 0, durationTicks);
            this.portalB = new Portal(posB, normalB, 0.6f, 0.6f, true, groupId, 1, durationTicks);
            generateLightning();
        }

        private void generateLightning() {
            lightningSegments.clear();
            lightningSegments.add(posA);
            int segments = 12;
            for (int i = 1; i < segments; i++) {
                double t = (double) i / segments;
                Vec3 mid = posA.lerp(posB, t);
                Vec3 dir = posB.subtract(posA).normalize();
                Vec3 perp1 = new Vec3(-dir.z, 0, dir.x).normalize();
                double offset = (Math.random() - 0.5) * 1.5 * (1 - Math.abs(t - 0.5) * 2);
                lightningSegments.add(mid.add(perp1.scale(offset)));
            }
            lightningSegments.add(posB);
        }

        @Override
        public AABB getBoundingBox() {
            return new AABB(posA.x, posA.y, posA.z, posB.x, posB.y, posB.z).inflate(3.0);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float partialTick) {
            tick();
            if (openProgress <= 0) return;

            float alpha = openProgress * 0.9f;
            setupGlowState();
            try {
                renderPortalFace(poseStack, bufferSource, cameraPos, portalA, alpha);
                renderPortalFace(poseStack, bufferSource, cameraPos, portalB, alpha);
                renderLightningRibbon(poseStack, bufferSource, cameraPos, alpha);
                renderGroundImpactRing(poseStack, bufferSource, cameraPos, posB, alpha);
            } finally {
                restoreGlowState();
            }
        }

        private void renderPortalFace(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Portal portal, float alpha) {
            poseStack.pushPose();
            poseStack.translate(portal.pos.x - cameraPos.x, portal.pos.y - cameraPos.y, portal.pos.z - cameraPos.z);

            float yaw = (float) Math.toDegrees(Math.atan2(portal.normal.z, portal.normal.x)) - 90;
            float pitch = (float) Math.toDegrees(-Math.asin(portal.normal.y));
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(pitch));

            var buffer = bufferSource.getBuffer(RenderType.translucent());
            Matrix4f mat = poseStack.last().pose();
            float size = 0.6f;
            drawQuad(buffer, mat,
                -size, -size, 0,
                size, -size, 0,
                size, size, 0,
                -size, size, 0,
                0.45f, 0.82f, 1.0f, alpha);

            poseStack.popPose();
        }

        private void renderLightningRibbon(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float alpha) {
            var buffer = bufferSource.getBuffer(RenderType.lines());

            // Core bright line
            for (int i = 0; i < lightningSegments.size() - 1; i++) {
                Vec3 p1 = lightningSegments.get(i).subtract(cameraPos);
                Vec3 p2 = lightningSegments.get(i + 1).subtract(cameraPos);
                drawLine(buffer, poseStack.last().pose(),
                    (float)p1.x, (float)p1.y, (float)p1.z,
                    (float)p2.x, (float)p2.y, (float)p2.z,
                    1.0f, 1.0f, 1.0f, alpha);
            }

            // Outer glow
            var glowBuffer = bufferSource.getBuffer(RenderType.translucent());
            for (int i = 0; i < lightningSegments.size() - 1; i++) {
                Vec3 p1 = lightningSegments.get(i).subtract(cameraPos);
                Vec3 p2 = lightningSegments.get(i + 1).subtract(cameraPos);
                drawQuad(glowBuffer, poseStack.last().pose(),
                    (float)p1.x, (float)p1.y, (float)p1.z - 0.1f,
                    (float)p2.x, (float)p2.y, (float)p2.z - 0.1f,
                    (float)p2.x, (float)p2.y, (float)p2.z + 0.1f,
                    (float)p1.x, (float)p1.y, (float)p1.z + 0.1f,
                    0.45f, 0.82f, 1.0f, alpha * 0.3f);
            }
        }

        private void renderGroundImpactRing(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Vec3 pos, float alpha) {
            poseStack.pushPose();
            poseStack.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);

            var buffer = bufferSource.getBuffer(RenderType.lines());
            Matrix4f mat = poseStack.last().pose();
            int segments = 32;
            float maxRadius = 4.0f;
            float progress = (age % 20) / 20.0f;
            float radius = progress * maxRadius;
            float ringAlpha = alpha * (1.0f - progress);
            for (int i = 0; i < segments; i++) {
                double a1 = 2 * Math.PI * i / segments;
                double a2 = 2 * Math.PI * (i + 1) / segments;
                float x1 = (float) (Math.cos(a1) * radius);
                float z1 = (float) (Math.sin(a1) * radius);
                float x2 = (float) (Math.cos(a2) * radius);
                float z2 = (float) (Math.sin(a2) * radius);
                drawLine(buffer, mat, x1, 0, z1, x2, 0, z2, 0.45f, 0.82f, 1.0f, ringAlpha);
            }
            poseStack.popPose();
        }
    }

    /**
     * Fold Effect: Two portals + short streak.
     */
    public static class FoldEffect extends ClientEffect {
        private final Vec3 posA, normalA, posB, normalB;
        private final Portal portalA, portalB;

        public FoldEffect(FxSpawnPacket packet) {
            super(packet);
            this.posA = packet.posA();
            this.normalA = packet.normalA();
            this.posB = packet.posB();
            this.normalB = packet.normalB();
            this.portalA = new Portal(posA, normalA, 1.0f, 1.5f, false, groupId, 0, durationTicks);
            this.portalB = new Portal(posB, normalB, 1.0f, 1.5f, false, groupId, 1, durationTicks);
        }

        @Override
        public AABB getBoundingBox() {
            return new AABB(posA.x, posA.y, posA.z, posB.x, posB.y, posB.z).inflate(2.5);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float partialTick) {
            tick();
            if (openProgress <= 0) return;

            float alpha = openProgress * 0.8f;
            setupGlowState();
            try {
                renderPortalFace(poseStack, bufferSource, cameraPos, portalA, alpha);
                renderPortalFace(poseStack, bufferSource, cameraPos, portalB, alpha);
                renderStreak(poseStack, bufferSource, cameraPos, alpha);
            } finally {
                restoreGlowState();
            }
        }

        private void renderPortalFace(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, Portal portal, float alpha) {
            poseStack.pushPose();
            poseStack.translate(portal.pos.x - cameraPos.x, portal.pos.y - cameraPos.y, portal.pos.z - cameraPos.z);

            float yaw = (float) Math.toDegrees(Math.atan2(portal.normal.z, portal.normal.x)) - 90;
            float pitch = (float) Math.toDegrees(-Math.asin(portal.normal.y));
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));
            poseStack.mulPose(Direction.Axis.Z.rotationDegrees(pitch));

            var buffer = bufferSource.getBuffer(RenderType.translucent());
            Matrix4f mat = poseStack.last().pose();
            float hw = 1.0f, hh = 1.5f;
            drawQuad(buffer, mat,
                -hw, 0, 0,
                hw, 0, 0,
                hw, hh, 0,
                -hw, hh, 0,
                0.45f, 0.82f, 1.0f, alpha);

            poseStack.popPose();
        }

        private void renderStreak(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float alpha) {
            poseStack.pushPose();
            Vec3 mid = posA.lerp(posB, 0.5);
            poseStack.translate(mid.x - cameraPos.x, mid.y - cameraPos.y, mid.z - cameraPos.z);

            var buffer = bufferSource.getBuffer(RenderType.translucent());
            Matrix4f mat = poseStack.last().pose();
            Vec3 dir = posB.subtract(posA).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90;
            poseStack.mulPose(Direction.Axis.Y.rotationDegrees(yaw));

            float len = (float) posA.distanceTo(posB);
            drawQuad(buffer, mat,
                -len/2, 0, -0.2f,
                len/2, 0, -0.2f,
                len/2, 0, 0.2f,
                -len/2, 0, 0.2f,
                0.45f, 0.82f, 1.0f, alpha);

            poseStack.popPose();
        }
    }

    // Exchange effect (rings at both positions)
    public static class ExchangeEffect extends ClientEffect {
        public ExchangeEffect(FxSpawnPacket packet) { super(packet); }
        @Override public AABB getBoundingBox() { return new AABB(0,0,0,0,0,0); }
        @Override public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos, float partialTick) {}
    }
}