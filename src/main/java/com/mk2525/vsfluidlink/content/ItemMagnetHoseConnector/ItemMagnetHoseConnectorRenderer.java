package com.mk2525.vsfluidlink.content.ItemMagnetHoseConnector;

import com.mk2525.vsfluidlink.registry.ModBlocks;
import com.mk2525.vsfluidlink.util.VSLinkUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.render.BlockEntityRenderHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
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
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ItemMagnetHoseConnectorRenderer extends KineticBlockEntityRenderer<ItemMagnetHoseConnectorBlockEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TEXTURE = ResourceLocation.tryParse("create:textures/block/hose_pulley_coil_scroll.png");
    private static final float WIDTH = 0.4f;
    private final BlockRenderDispatcher blockRenderer;

    public ItemMagnetHoseConnectorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    protected void renderSafe(ItemMagnetHoseConnectorBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
        if (VSLinkUtil.isVirtualWorld(be.getLevel())) return;
        renderShaft(be, poseStack, bufferSource, light);
        
        BlockPos selfPos = be.getBlockPos();
        BlockPos targetPos = be.getTargetPos();
        if (targetPos == null) return;

        if (selfPos.getX() > targetPos.getX() ||
           (selfPos.getX() == targetPos.getX() && selfPos.getY() > targetPos.getY()) ||
           (selfPos.getX() == targetPos.getX() && selfPos.getY() == targetPos.getY() && selfPos.getZ() > targetPos.getZ())) {
            return;
        }
        
        // 闕ｳ・ｭ陟｢繝ｻ繝ｻ郢晢ｽｯ郢晢ｽｼ郢晢ｽｫ郢晉甥・ｺ・ｧ隶灘生・定愾髢・ｾ繝ｻ
        Vec3 startCenterPos = ItemMagnetHoseConnectorBlockEntity.getWorldPos(be.getLevel(), selfPos, true);
        Vec3 endCenterPos = ItemMagnetHoseConnectorBlockEntity.getWorldPos(be.getLevel(), targetPos, true);
        Vec3 diff = endCenterPos.subtract(startCenterPos);

        if (diff.lengthSqr() < 0.001) return;

        poseStack.pushPose();

        // --- 郢ｧ・ｪ郢晁ｼ斐◎郢昴・繝ｨ邵ｺ・ｨ郢晏生縺醍ｹ晏現ﾎ晉ｸｺ・ｮ髫ｪ閧ｲ・ｮ繝ｻ---
        BlockState startState = be.getBlockState();
        BlockState endState = be.getLevel().getBlockState(targetPos);

        Vec3 localDiff = diff;
        Vector3f startOffset = getLocalOffset(startState);
        Vector3f endOffset = new Vector3f();

        try {
            Object selfShip = getShipAt(be.getLevel(), selfPos);
            Object targetShip = getShipAt(be.getLevel(), targetPos);

            // 1. 郢晢ｽｯ郢晢ｽｼ郢晢ｽｫ郢晉甥・ｷ・ｮ陋ｻ繝ｻ繝ｻ郢ｧ・ｯ郢晏現ﾎ晉ｹｧ蛛ｵﾂ竏晢ｽｧ迢励○郢晄じﾎ溽ｹ昴・縺醍ｸｺ・ｮ郢晢ｽｭ郢晢ｽｼ郢ｧ・ｫ郢晢ｽｫ陟趣ｽｧ隶灘衷・ｳ・ｻ邵ｺ・ｫ陞溽判驪､
            if (selfShip != null) {
                localDiff = transformVector(diff, selfShip, true); // worldToShip
            }

            // 2. 驍ｨ繧峨○郢晄じﾎ溽ｹ昴・縺醍ｸｺ・ｮ郢ｧ・ｪ郢晁ｼ斐◎郢昴・繝ｨ郢ｧ螳夲ｽｨ閧ｲ・ｮ蜉ｱ・邵ｲ竏晢ｽｧ迢励○郢晄じﾎ溽ｹ昴・縺醍ｸｺ・ｮ郢晢ｽｭ郢晢ｽｼ郢ｧ・ｫ郢晢ｽｫ陟趣ｽｧ隶灘衷・ｳ・ｻ邵ｺ・ｫ陞溽判驪､
            if (endState.getBlock() instanceof ItemMagnetHoseConnectorBlock) {
                Vec3 targetOffsetWorld = getOffsetFor(be.getLevel(), targetPos, endState);
                if (selfShip != null) {
                    endOffset = transformVector(targetOffsetWorld, selfShip, true).toVector3f(); // worldToShip
                } else {
                    endOffset = new Vector3f((float)targetOffsetWorld.x, (float)targetOffsetWorld.y, (float)targetOffsetWorld.z);
                }
            }
        } catch (Exception e) {
            // 郢ｧ・ｨ郢晢ｽｩ郢晢ｽｼ騾具ｽｺ騾墓ｻ灘・邵ｺ・ｯ邵ｲ竏壹′郢晁ｼ斐◎郢昴・繝ｨ邵ｺ・ｪ邵ｺ蜉ｱ繝ｻ陜ｨ・ｰ闕ｳ鄙ｫ竊鍋ｸｺ繝ｻ・狗ｹｧ繧・・邵ｺ・ｨ邵ｺ蜉ｱ窶ｻ隰蜀怜愛郢ｧ螳夲ｽｩ・ｦ邵ｺ・ｿ郢ｧ繝ｻ
            if (endState.getBlock() instanceof ItemMagnetHoseConnectorBlock) {
                endOffset = getLocalOffset(endState);
            }
        }
        
        // --- 隰蜀怜愛 ---
        poseStack.translate(0.5, 0.5, 0.5);
        
        int brightestLight = brightestLight(be.getLevel(), selfPos, targetPos);
        
        renderHose(poseStack, bufferSource, localDiff, startOffset, endOffset, brightestLight);
        renderDecoration(poseStack, bufferSource, localDiff, startOffset, endOffset, brightestLight);
        
        poseStack.popPose();
    }
    
    @Override
    protected BlockState getRenderedBlockState(ItemMagnetHoseConnectorBlockEntity be) {
        return AllBlocks.SHAFT.get().defaultBlockState().setValue(ShaftBlock.AXIS, getRotationAxisOf(be));
    }

    private void renderShaft(ItemMagnetHoseConnectorBlockEntity be, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        BlockState shaftState = getRenderedBlockState(be);
        renderRotatingBuffer(be, CachedBuffers.block(KINETIC_BLOCK, shaftState), poseStack, bufferSource.getBuffer(RenderType.solid()), light);
    }
    
    private Vector3f getLocalOffset(BlockState state) {
        Direction facing = state.getValue(ItemMagnetHoseConnectorBlock.FACING);
        Vec3i normal = facing.getNormal();
        return new Vector3f(normal.getX(), normal.getY(), normal.getZ()).mul(0.5f);
    }

    private Vec3 getOffsetFor(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(ItemMagnetHoseConnectorBlock.FACING);
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
        if (!VSLinkUtil.isValkyrienSkiesLoaded()) throw new ClassNotFoundException("Valkyrien Skies is not loaded");
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

        float uMin = 6f / 16f;
        float uMax = 10f / 16f;

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
        builder.addVertex(m, pos.x(), pos.y(), pos.z())
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 1, 0);
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
