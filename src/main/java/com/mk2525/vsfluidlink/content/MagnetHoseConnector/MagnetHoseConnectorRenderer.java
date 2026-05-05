package com.mk2525.vsfluidlink.content.MagnetHoseConnector;

import com.mk2525.vsfluidlink.content.HoseConnector.HoseConnectorBlockEntity;
import com.mk2525.vsfluidlink.registry.ModBlocks;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
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
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MagnetHoseConnectorRenderer implements BlockEntityRenderer<MagnetHoseConnectorBlockEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TEXTURE = new ResourceLocation("vsfluidlink", "textures/block/coil.png");
    private static final float WIDTH = 0.375f;
    private final BlockRenderDispatcher blockRenderer;

    public MagnetHoseConnectorRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(MagnetHoseConnectorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (VSLinkUtil.isVirtualWorld(be.getLevel())) return;

        BlockPos selfPos = be.getBlockPos();
        BlockPos targetPos = be.getTargetPos();
        if (targetPos == null) return;

        if (selfPos.getX() > targetPos.getX() ||
           (selfPos.getX() == targetPos.getX() && selfPos.getY() > targetPos.getY()) ||
           (selfPos.getX() == targetPos.getX() && selfPos.getY() == targetPos.getY() && selfPos.getZ() > targetPos.getZ())) {
            return;
        }
        
        // 中心のワールド座標を取得
        Vec3 startCenterPos = HoseConnectorBlockEntity.getWorldPos(be.getLevel(), selfPos, true);
        Vec3 endCenterPos = HoseConnectorBlockEntity.getWorldPos(be.getLevel(), targetPos, true);
        Vec3 diff = endCenterPos.subtract(startCenterPos);

        if (diff.lengthSqr() < 0.001) return;

        poseStack.pushPose();

        // --- オフセットとベクトルの計算 ---
        BlockState startState = be.getBlockState();
        BlockState endState = be.getLevel().getBlockState(targetPos);

        Vec3 localDiff = diff;
        Vector3f startOffset = getLocalOffset(startState);
        Vector3f endOffset = new Vector3f();

        try {
            Object selfShip = getShipAt(be.getLevel(), selfPos);
            Object targetShip = getShipAt(be.getLevel(), targetPos);

            // 1. ワールド差分ベクトルを、始点ブロックのローカル座標系に変換
            if (selfShip != null) {
                localDiff = transformVector(diff, selfShip, true); // worldToShip
            }

            // 2. 終点ブロックのオフセットを計算し、始点ブロックのローカル座標系に変換
            if (endState.getBlock() instanceof MagnetHoseConnectorBlock) {
                Vec3 targetOffsetWorld = getOffsetFor(be.getLevel(), targetPos, endState);
                if (selfShip != null) {
                    endOffset = transformVector(targetOffsetWorld, selfShip, true).toVector3f(); // worldToShip
                } else {
                    endOffset = new Vector3f((float)targetOffsetWorld.x, (float)targetOffsetWorld.y, (float)targetOffsetWorld.z);
                }
            }
        } catch (Exception e) {
            // エラー発生時は、オフセットなしの地上にいるものとして描画を試みる
            if (endState.getBlock() instanceof MagnetHoseConnectorBlock) {
                endOffset = getLocalOffset(endState);
            }
        }
        
        // --- 描画 ---
        poseStack.translate(0.5, 0.5, 0.5);
        
        int light = brightestLight(be.getLevel(), selfPos, targetPos);
        
        renderHose(poseStack, bufferSource, localDiff, startOffset, endOffset, light);
        renderDecoration(poseStack, bufferSource, localDiff, startOffset, endOffset, light);
        
        poseStack.popPose();
    }
    
    private Vector3f getLocalOffset(BlockState state) {
        Direction facing = state.getValue(MagnetHoseConnectorBlock.FACING);
        Vec3i normal = facing.getNormal();
        return new Vector3f(normal.getX(), normal.getY(), normal.getZ()).mul(0.5f);
    }

    private Vec3 getOffsetFor(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(MagnetHoseConnectorBlock.FACING);
        Vec3i normal = facing.getNormal();
        Vec3 offset = new Vec3(normal.getX(), normal.getY(), normal.getZ()).scale(0.5);

        try {
            Object ship = getShipAt(level, pos);
            if (ship != null) {
                return transformVector(offset, ship, false); // Ship to World
            }
        } catch (Exception e) {
            // ignore
        }
        return offset;
    }

    private Vec3 transformVector(Vec3 vec, Object ship, boolean worldToShip) throws Exception {
        Method getRenderTransform = ship.getClass().getMethod("getRenderTransform");
        Object renderTransform = getRenderTransform.invoke(ship);
        
        String matrixName = worldToShip ? "getWorldToShip" : "getShipToWorld";
        Method getMatrix = renderTransform.getClass().getMethod(matrixName);
        Object matrix = getMatrix.invoke(renderTransform);

        double m00 = getField(matrix, "m00"), m01 = getField(matrix, "m01"), m02 = getField(matrix, "m02");
        double m10 = getField(matrix, "m10"), m11 = getField(matrix, "m11"), m12 = getField(matrix, "m12");
        double m20 = getField(matrix, "m20"), m21 = getField(matrix, "m21"), m22 = getField(matrix, "m22");

        double dx = vec.x, dy = vec.y, dz = vec.z;
        double lx = m00 * dx + m10 * dy + m20 * dz;
        double ly = m01 * dx + m11 * dy + m21 * dz;
        double lz = m02 * dx + m12 * dy + m22 * dz;
        
        return new Vec3(lx, ly, lz);
    }
    
    private Object getShipAt(Level level, BlockPos pos) throws Exception {
        Class<?> vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
        Method getShipManagingPos = vsGameUtilsClass.getMethod("getShipManagingPos", Level.class, BlockPos.class);
        return getShipManagingPos.invoke(null, level, pos);
    }

    private void renderDecoration(PoseStack ms, MultiBufferSource buffer, Vec3 localDiff, Vector3f startOffset, Vector3f endOffset, int light) {
        BlockState renderState = ModBlocks.MAGNET_HOSE_DECORATION.get().defaultBlockState();
        
        Vector3f hoseStart = startOffset;
        Vector3f hoseEnd = new Vector3f((float)localDiff.x, (float)localDiff.y, (float)localDiff.z).add(endOffset);
        Vector3f hoseVec = new Vector3f(hoseEnd).sub(hoseStart);
        Vector3f midPoint = new Vector3f(hoseStart).add(hoseVec.mul(0.5f));

        ms.pushPose();
        ms.translate(midPoint.x, midPoint.y, midPoint.z);
        
        Vector3f direction = hoseVec.normalize();
        Vector3f up = new Vector3f(0, 1, 0);
        ms.mulPose(new Quaternionf().rotationTo(up, direction));
        
        ms.translate(-0.5, -0.5, -0.5);
        
        blockRenderer.renderSingleBlock(renderState, ms, buffer, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        
        ms.popPose();
    }
    
    private double getField(Object obj, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(obj);
    }

    private void renderHose(PoseStack ms, MultiBufferSource buffer, Vec3 localDiff, Vector3f startOffset, Vector3f endOffset, int light) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entityCutout(TEXTURE));

        Vector3f hoseStart = startOffset;
        Vector3f hoseEnd = new Vector3f((float)localDiff.x, (float)localDiff.y, (float)localDiff.z).add(endOffset);
        Vector3f hoseVec = new Vector3f(hoseEnd).sub(hoseStart);
        float length = hoseVec.length();
        
        ms.pushPose();
        ms.translate(hoseStart.x, hoseStart.y, hoseStart.z);

        Vector3f direction = new Vector3f(hoseVec).normalize();
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f right;

        if (Math.abs(direction.x()) < 1e-6 && Math.abs(direction.z()) < 1e-6) {
            right = new Vector3f(1, 0, 0);
        } else {
            right = new Vector3f(direction).cross(up).normalize();
        }
        up = new Vector3f(right).cross(direction).normalize();

        float r = WIDTH / 2;
        
        Vector3f p1 = new Vector3f(up).add(right).mul(r);
        Vector3f p2 = new Vector3f(up).sub(right).mul(r);
        Vector3f p3 = new Vector3f(up).negate().sub(right).mul(r);
        Vector3f p4 = new Vector3f(up).negate().add(right).mul(r);

        Vector3f s1 = p1, s2 = p2, s3 = p3, s4 = p4;
        Vector3f e1 = new Vector3f(p1).add(hoseVec);
        Vector3f e2 = new Vector3f(p2).add(hoseVec);
        Vector3f e3 = new Vector3f(p3).add(hoseVec);
        Vector3f e4 = new Vector3f(p4).add(hoseVec);

        Matrix4f m = ms.last().pose();

        float uMin = 5f / 16f, uMax = 11f / 16f;
        quad(builder, m, s1, e1, e2, s2, uMin, uMax, 0, length, light);
        quad(builder, m, s2, e2, e3, s3, uMin, uMax, 0, length, light);
        quad(builder, m, s3, e3, e4, s4, uMin, uMax, 0, length, light);
        quad(builder, m, s4, e4, e1, s1, uMin, uMax, 0, length, light);
        
        ms.popPose();
    }

    private void quad(VertexConsumer builder, Matrix4f m, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4, float uMin, float uMax, float vMin, float vMax, int light) {
        vertex(builder, m, p1, uMin, vMin, light);
        vertex(builder, m, p2, uMin, vMax, light);
        vertex(builder, m, p3, uMax, vMax, light);
        vertex(builder, m, p4, uMax, vMin, light);
    }

    private void vertex(VertexConsumer builder, Matrix4f m, Vector3f pos, float u, float v, int light) {
        builder.vertex(m, pos.x(), pos.y(), pos.z())
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(0, 1, 0)
                .endVertex();
    }
    
    private int brightestLight(Level level, BlockPos pos1, BlockPos pos2) {
        int light1 = getLight(level, pos1);
        int light2 = getLight(level, pos2);
        return Math.max(light1, light2);
    }
    
    private int getLight(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return 15 << 20 | 15 << 4;
        int b = level.getBrightness(LightLayer.BLOCK, pos);
        int s = level.getBrightness(LightLayer.SKY, pos);
        return s << 20 | b << 4;
    }
}
