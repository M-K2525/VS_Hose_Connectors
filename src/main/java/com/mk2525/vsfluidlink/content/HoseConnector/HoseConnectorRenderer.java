package com.mk2525.vsfluidlink.content.HoseConnector;

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

public class HoseConnectorRenderer implements BlockEntityRenderer<HoseConnectorBlockEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TEXTURE = new ResourceLocation("vsfluidlink", "textures/block/coil.png");
    private static final float WIDTH = 0.375f;
    private final BlockRenderDispatcher blockRenderer;

    public HoseConnectorRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(HoseConnectorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (VSLinkUtil.isVirtualWorld(be.getLevel())) return;

        BlockPos selfPos = be.getBlockPos();
        BlockPos targetPos = be.getTargetPos();
        if (targetPos == null) return;

        if (selfPos.getX() > targetPos.getX() ||
           (selfPos.getX() == targetPos.getX() && selfPos.getY() > targetPos.getY()) ||
           (selfPos.getX() == targetPos.getX() && selfPos.getY() == targetPos.getY() && selfPos.getZ() > targetPos.getZ())) {
            return;
        }
        
        Vec3 startPos = HoseConnectorBlockEntity.getWorldPos(be.getLevel(), selfPos, true);
        Vec3 endPos = HoseConnectorBlockEntity.getWorldPos(be.getLevel(), targetPos, true);
        Vec3 diff = endPos.subtract(startPos);

        if (diff.lengthSqr() < 0.001) return;

        poseStack.pushPose();

        Vec3 localDiff = diff;
        try {
            Class<?> vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            Method getShipManagingPos = vsGameUtilsClass.getMethod("getShipManagingPos", Level.class, BlockPos.class);
            Object ship = getShipManagingPos.invoke(null, be.getLevel(), selfPos);

            if (ship != null) {
                Method getRenderTransform = ship.getClass().getMethod("getRenderTransform");
                Object renderTransform = getRenderTransform.invoke(ship);
                Method getWorldToShip = renderTransform.getClass().getMethod("getWorldToShip");
                Object worldToShipMatrix = getWorldToShip.invoke(renderTransform);

                double m00 = getField(worldToShipMatrix, "m00");
                double m01 = getField(worldToShipMatrix, "m01");
                double m02 = getField(worldToShipMatrix, "m02");
                double m10 = getField(worldToShipMatrix, "m10");
                double m11 = getField(worldToShipMatrix, "m11");
                double m12 = getField(worldToShipMatrix, "m12");
                double m20 = getField(worldToShipMatrix, "m20");
                double m21 = getField(worldToShipMatrix, "m21");
                double m22 = getField(worldToShipMatrix, "m22");

                double dx = diff.x;
                double dy = diff.y;
                double dz = diff.z;

                double lx = m00 * dx + m10 * dy + m20 * dz;
                double ly = m01 * dx + m11 * dy + m21 * dz;
                double lz = m02 * dx + m12 * dy + m22 * dz;

                localDiff = new Vec3(lx, ly, lz);
            }
        } catch (Exception e) {
            // エラー無視
        }

        poseStack.translate(0.5, 0.5, 0.5);
        
        int light = brightestLight(be.getLevel(), selfPos, targetPos);
        
        renderHose(poseStack, bufferSource, localDiff, light);
        renderDecoration(poseStack, bufferSource, localDiff, light);
        
        poseStack.popPose();
    }
    
    private void renderDecoration(PoseStack ms, MultiBufferSource buffer, Vec3 diff, int light) {
        BlockState renderState = ModBlocks.HOSE_DECORATION.get().defaultBlockState();
        
        Vec3 midPoint = diff.scale(0.5);
        
        ms.pushPose();
        ms.translate(midPoint.x, midPoint.y, midPoint.z);
        
        Vector3f direction = new Vector3f((float)diff.x, (float)diff.y, (float)diff.z).normalize();
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

    private void renderHose(PoseStack ms, MultiBufferSource buffer, Vec3 diff, int light) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entityCutout(TEXTURE));

        float length = (float) diff.length();
        
        Vector3f direction = new Vector3f((float)diff.x, (float)diff.y, (float)diff.z);

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

        Vector3f s1 = p1;
        Vector3f s2 = p2;
        Vector3f s3 = p3;
        Vector3f s4 = p4;

        Vector3f e1 = new Vector3f(s1).add(direction);
        Vector3f e2 = new Vector3f(s2).add(direction);
        Vector3f e3 = new Vector3f(s3).add(direction);
        Vector3f e4 = new Vector3f(s4).add(direction);

        Matrix4f m = ms.last().pose();

        float uMin = 5f / 16f;
        float uMax = 11f / 16f;

        float vMax = length * (uMax - uMin) * 0.5f / WIDTH;

        quad(builder, m, s1, e1, e2, s2, uMin, uMax, 0, vMax, light);
        quad(builder, m, s2, e2, e3, s3, uMin, uMax, 0, vMax, light);
        quad(builder, m, s3, e3, e4, s4, uMin, uMax, 0, vMax, light);
        quad(builder, m, s4, e4, e1, s1, uMin, uMax, 0, vMax, light);
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
