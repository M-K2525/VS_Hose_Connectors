package com.mk2525.vsfluidlink.content.MagnetChainConnector;

import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class MagnetChainConnectorRenderer extends KineticBlockEntityRenderer<MagnetChainConnectorBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/chain.png");
    private static final float ANCHOR_OFFSET = 3.0f / 16.0f;

    public MagnetChainConnectorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(MagnetChainConnectorBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
        if (VSLinkUtil.isVirtualWorld(be.getLevel())) {
            return;
        }

        renderShaft(be, poseStack, bufferSource, light);

        BlockPos selfPos = be.getBlockPos();
        BlockPos targetPos = be.getTargetPos();
        if (targetPos == null) {
            return;
        }

        if (selfPos.getX() > targetPos.getX()
                || (selfPos.getX() == targetPos.getX() && selfPos.getY() > targetPos.getY())
                || (selfPos.getX() == targetPos.getX() && selfPos.getY() == targetPos.getY() && selfPos.getZ() > targetPos.getZ())) {
            return;
        }

        Vec3 startPos = VSLinkUtil.Client.getRenderWorldPos(be.getLevel(), selfPos);
        Vec3 endPos = VSLinkUtil.Client.getRenderWorldPos(be.getLevel(), targetPos);
        Vec3 diff = endPos.subtract(startPos);
        if (diff.lengthSqr() < 0.001) {
            return;
        }

        BlockState startState = be.getBlockState();
        BlockState endState = be.getLevel().getBlockState(targetPos);
        Vec3 localDiff = VSLinkUtil.Client.renderWorldVectorToLocal(be.getLevel(), selfPos, diff);

        Vector3f[] startAnchors = getLocalAnchorOffsets(startState, localDiff);
        Vector3f[] endAnchors = new Vector3f[] { new Vector3f(), new Vector3f() };
        if (isChainConnector(endState)) {
            Vec3[] targetOffsetsWorld = getWorldAnchorOffsets(be.getLevel(), targetPos, endState, diff.scale(-1));
            for (int i = 0; i < 2; i++) {
                endAnchors[i] = VSLinkUtil.Client.renderWorldVectorToLocal(be.getLevel(), selfPos, targetOffsetsWorld[i]).toVector3f();
            }
        }
        alignEndAnchors(localDiff, startAnchors, endAnchors);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        int brightestLight = brightestLight(be.getLevel(), selfPos, targetPos);
        float speed = be.getSpeed();
        float time = be.getLevel().getGameTime() + partialTicks;
        float textureOffset = (time * speed / 20.0f) / 16.0f * getAnimationSign(startState, localDiff, startAnchors[0]);

        renderChains(poseStack, bufferSource, localDiff, startAnchors, endAnchors, brightestLight, textureOffset);
        poseStack.popPose();
    }

    @Override
    protected BlockState getRenderedBlockState(MagnetChainConnectorBlockEntity be) {
        return AllBlocks.SHAFT.get().defaultBlockState().setValue(ShaftBlock.AXIS, getRotationAxisOf(be));
    }

    private boolean isChainConnector(BlockState state) {
        return state.getBlock() instanceof MagnetChainConnectorBlock
                || state.getBlock() instanceof com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlock;
    }

    private Vector3f[] getLocalAnchorOffsets(BlockState state, Vec3 connection) {
        Vec3 anchor = getAnchorVector(state, connection);
        Vector3f offset = new Vector3f((float) anchor.x, (float) anchor.y, (float) anchor.z).mul(ANCHOR_OFFSET);
        return new Vector3f[] {
                new Vector3f(offset),
                new Vector3f(offset).negate()
        };
    }

    private Vec3[] getWorldAnchorOffsets(Level level, BlockPos pos, BlockState state, Vec3 connectionWorld) {
        Vec3 connectionLocal = VSLinkUtil.Client.renderWorldVectorToLocal(level, pos, connectionWorld);
        Vec3 offset = getAnchorVector(state, connectionLocal).scale(ANCHOR_OFFSET);
        return new Vec3[] {
                VSLinkUtil.Client.renderLocalVectorToWorld(level, pos, offset),
                VSLinkUtil.Client.renderLocalVectorToWorld(level, pos, offset.scale(-1))
        };
    }

    private Vec3 getAnchorVector(BlockState state, Vec3 connection) {
        Vec3 shaft = axisVector(getRotationAxis(state));
        Vec3 preferred = getPreferredAnchorVector(state, shaft);
        Vec3 fallback = preferred;
        if (connection.lengthSqr() < 1.0e-6) {
            return fallback;
        }

        Vec3 forward = connection.normalize();
        Vec3 anchor = cross(shaft, forward);
        if (anchor.lengthSqr() < 1.0e-6) {
            return fallback;
        }

        anchor = anchor.normalize();
        if (anchor.dot(preferred) < 0) {
            anchor = anchor.scale(-1);
        }
        return anchor;
    }

    private float getAnimationSign(BlockState state, Vec3 connection, Vector3f anchorOffset) {
        if (connection.lengthSqr() < 1.0e-6 || anchorOffset.lengthSquared() < 1.0e-6f) {
            return getLegacyAnimationSign(state);
        }

        Vec3 shaft = axisVector(getRotationAxis(state));
        Vec3 anchor = new Vec3(anchorOffset.x(), anchorOffset.y(), anchorOffset.z()).normalize();
        Vec3 forward = connection.normalize();
        double movement = cross(shaft, anchor).dot(forward);
        if (Math.abs(movement) < 1.0e-6) {
            return getLegacyAnimationSign(state);
        }
        return movement > 0 ? -1.0f : 1.0f;
    }

    private float getLegacyAnimationSign(BlockState state) {
        Direction facing = state.getBlock() instanceof MagnetChainConnectorBlock
                ? state.getValue(MagnetChainConnectorBlock.FACING)
                : state.getValue(com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlock.FACING);
        return facing.getAxis() == Direction.Axis.Z ? -1.0f : 1.0f;
    }

    private Vec3 directionVector(Direction direction) {
        Vec3i normal = direction.getNormal();
        return new Vec3(normal.getX(), normal.getY(), normal.getZ());
    }

    private Vec3 axisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3(1, 0, 0);
            case Y -> new Vec3(0, 1, 0);
            case Z -> new Vec3(0, 0, 1);
        };
    }

    private Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
        );
    }

    private Direction.Axis getRotationAxis(BlockState state) {
        Direction facing = state.getBlock() instanceof MagnetChainConnectorBlock
                ? state.getValue(MagnetChainConnectorBlock.FACING)
                : state.getValue(com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlock.FACING);
        if (facing.getAxis().isVertical() || facing.getAxis() == Direction.Axis.Z) {
            return Direction.Axis.X;
        }
        return Direction.Axis.Z;
    }

    private Vec3 getPreferredAnchorVector(BlockState state, Vec3 shaft) {
        Vec3 preferred = directionVector(getModelSide(state));
        Vec3 projected = preferred.subtract(shaft.scale(preferred.dot(shaft)));
        if (projected.lengthSqr() < 1.0e-6) {
            Vec3 up = directionVector(getModelUp(state));
            projected = up.subtract(shaft.scale(up.dot(shaft)));
        }
        return projected.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : projected.normalize();
    }

    private Direction getModelUp(BlockState state) {
        Direction facing = state.getBlock() instanceof MagnetChainConnectorBlock
                ? state.getValue(MagnetChainConnectorBlock.FACING)
                : state.getValue(com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlock.FACING);
        return switch (facing) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    private Direction getModelSide(BlockState state) {
        Direction facing = state.getBlock() instanceof MagnetChainConnectorBlock
                ? state.getValue(MagnetChainConnectorBlock.FACING)
                : state.getValue(com.mk2525.vsfluidlink.content.ChainConnector.ChainConnectorBlock.FACING);
        return switch (facing.getAxis()) {
            case X -> Direction.SOUTH;
            case Z, Y -> Direction.EAST;
        };
    }

    private void renderShaft(MagnetChainConnectorBlockEntity be, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        BlockState shaftState = getRenderedBlockState(be);
        renderRotatingBuffer(be, CachedBuffers.block(KINETIC_BLOCK, shaftState), poseStack, bufferSource.getBuffer(RenderType.solid()), light);
    }

    private void renderChains(PoseStack ms, MultiBufferSource buffer, Vec3 localDiff, Vector3f[] startAnchors, Vector3f[] endAnchors, int light, float textureOffset) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        Vector3f diff = new Vector3f((float) localDiff.x, (float) localDiff.y, (float) localDiff.z);
        renderSingleChain(ms, builder, new Vector3f(startAnchors[0]), new Vector3f(diff).add(endAnchors[0]), light, textureOffset);
        renderSingleChain(ms, builder, new Vector3f(startAnchors[1]), new Vector3f(diff).add(endAnchors[1]), light, -textureOffset);
    }

    private void alignEndAnchors(Vec3 localDiff, Vector3f[] startAnchors, Vector3f[] endAnchors) {
        Vector3f diff = new Vector3f((float) localDiff.x, (float) localDiff.y, (float) localDiff.z);
        float direct = chainLengthSquared(startAnchors[0], diff, endAnchors[0])
                + chainLengthSquared(startAnchors[1], diff, endAnchors[1]);
        float swapped = chainLengthSquared(startAnchors[0], diff, endAnchors[1])
                + chainLengthSquared(startAnchors[1], diff, endAnchors[0]);
        if (swapped + 1.0e-6f < direct) {
            Vector3f temp = endAnchors[0];
            endAnchors[0] = endAnchors[1];
            endAnchors[1] = temp;
        }
    }

    private float chainLengthSquared(Vector3f start, Vector3f diff, Vector3f end) {
        return new Vector3f(diff).add(end).sub(start).lengthSquared();
    }

    private void renderSingleChain(PoseStack ms, VertexConsumer builder, Vector3f chainStart, Vector3f chainEnd, int light, float textureOffset) {
        Vector3f chainVec = new Vector3f(chainEnd).sub(chainStart);
        float length = chainVec.length();
        if (length < 1.0e-4f) {
            return;
        }

        Vector3f direction = new Vector3f(chainVec).normalize();
        ms.pushPose();
        ms.translate(chainStart.x, chainStart.y, chainStart.z);

        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f right = Math.abs(direction.x()) < 1.0e-6f && Math.abs(direction.z()) < 1.0e-6f
                ? new Vector3f(1, 0, 0)
                : new Vector3f(direction).cross(up).normalize();
        up = new Vector3f(right).cross(direction).normalize();

        renderChain(builder, ms.last().pose(), chainVec, up, right, new Vector3f(), length, light, textureOffset);
        ms.popPose();
    }

    private void renderChain(VertexConsumer builder, Matrix4f matrix, Vector3f direction, Vector3f up, Vector3f right, Vector3f offset, float length, int light, float vOffset) {
        float width = 3.0f / 16.0f;
        float radius = width / 2.0f;

        Vector3f cross1 = new Vector3f(up).add(right).normalize().mul(radius);
        Vector3f cross2 = new Vector3f(up).sub(right).normalize().mul(radius);

        Vector3f p1Start = new Vector3f(offset).sub(cross1);
        Vector3f p2Start = new Vector3f(offset).add(cross1);
        Vector3f p1End = new Vector3f(p1Start).add(direction);
        Vector3f p2End = new Vector3f(p2Start).add(direction);

        Vector3f p3Start = new Vector3f(offset).sub(cross2);
        Vector3f p4Start = new Vector3f(offset).add(cross2);
        Vector3f p3End = new Vector3f(p3Start).add(direction);
        Vector3f p4End = new Vector3f(p4Start).add(direction);

        float vMin = vOffset;
        float vMax = length + vOffset;

        quad(builder, matrix, p1Start, p1End, p2End, p2Start, 0.0f, 3.0f / 16.0f, vMin, vMax, light);
        quad(builder, matrix, p2Start, p2End, p1End, p1Start, 0.0f, 3.0f / 16.0f, vMin, vMax, light);
        quad(builder, matrix, p3Start, p3End, p4End, p4Start, 3.0f / 16.0f, 6.0f / 16.0f, vMin, vMax, light);
        quad(builder, matrix, p4Start, p4End, p3End, p3Start, 3.0f / 16.0f, 6.0f / 16.0f, vMin, vMax, light);
    }

    private void quad(VertexConsumer builder, Matrix4f matrix, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4, float uMin, float uMax, float vMin, float vMax, int light) {
        vertex(builder, matrix, p1, uMin, vMin, light);
        vertex(builder, matrix, p2, uMin, vMax, light);
        vertex(builder, matrix, p3, uMax, vMax, light);
        vertex(builder, matrix, p4, uMax, vMin, light);
    }

    private void vertex(VertexConsumer builder, Matrix4f matrix, Vector3f pos, float u, float v, int light) {
        builder.addVertex(matrix, pos.x(), pos.y(), pos.z())
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 1, 0);
    }

    private int brightestLight(Level level, BlockPos pos1, BlockPos pos2) {
        return Math.max(getLight(level, pos1), getLight(level, pos2));
    }

    private int getLight(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return 15 << 20 | 15 << 4;
        }
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        return sky << 20 | block << 4;
    }
}
