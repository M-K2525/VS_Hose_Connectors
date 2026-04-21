package com.mk2525.vsfluidlink.content.MagnetChainConnector;

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

public class MagnetChainConnectorRenderer extends KineticBlockEntityRenderer<MagnetChainConnectorBlockEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/chain.png");
    private final BlockRenderDispatcher blockRenderer;

    public MagnetChainConnectorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    protected void renderSafe(MagnetChainConnectorBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
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
        
        Vec3 startPos = VSLinkUtil.Client.getRenderWorldPos(be.getLevel(), selfPos);
        Vec3 endPos = VSLinkUtil.Client.getRenderWorldPos(be.getLevel(), targetPos);
        Vec3 diff = endPos.subtract(startPos);


        if (diff.lengthSqr() < 0.001) return;

        poseStack.pushPose();

        Vec3 localDiff = diff;
        try {
            if (!VSLinkUtil.isValkyrienSkiesLoaded()) throw new ClassNotFoundException("Valkyrien Skies is not loaded");
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
            // Ignore error
        }

        poseStack.translate(0.5, 0.5, 0.5);
        
        int brightestLight = brightestLight(be.getLevel(), selfPos, targetPos);
        
        // 郢ｧ・｢郢昜ｹ斟鍋ｹ晢ｽｼ郢ｧ・ｷ郢晢ｽｧ郢晢ｽｳ騾包ｽｨ邵ｺ・ｮ郢ｧ・ｪ郢晁ｼ斐◎郢昴・繝ｨ髫ｪ閧ｲ・ｮ繝ｻ
        float speed = be.getSpeed();
        float time = be.getLevel().getGameTime() + partialTicks;
        float offset = (time * speed / 20.0f) / 16.0f; // 鬨ｾ貅ｷ・ｺ・ｦ邵ｺ・ｫ陟｢諛環ｧ邵ｺ・ｦ髫ｱ・ｿ隰ｨ・ｴ
        
        // North/South (Z髴・ｽｸ) 隴・ｽｹ陷ｷ莉｣繝ｻ邵ｺ・ｨ邵ｺ髦ｪ繝ｻ陜玲ｫ・ｽｻ・｢隴・ｽｹ陷ｷ莉｣繝ｻ鬮｢・｢闖ｫ繧・帝ｨｾ繝ｻ竊鍋ｸｺ・ｪ郢ｧ荵昶螺郢ｧ竏晄ｸ夐怕・｢
        if (be.getBlockState().getValue(MagnetChainConnectorBlock.FACING).getAxis() == Direction.Axis.Z) {
            offset *= -1;
        }
        
        renderChains(poseStack, bufferSource, localDiff, brightestLight, offset);
        
        poseStack.popPose();
    }
    
    @Override
    protected BlockState getRenderedBlockState(MagnetChainConnectorBlockEntity be) {
        return AllBlocks.SHAFT.get().defaultBlockState().setValue(ShaftBlock.AXIS, getRotationAxisOf(be));
    }

    private void renderShaft(MagnetChainConnectorBlockEntity be, PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        BlockState shaftState = getRenderedBlockState(be);
        renderRotatingBuffer(be, CachedBuffers.block(KINETIC_BLOCK, shaftState), poseStack, bufferSource.getBuffer(RenderType.solid()), light);
    }
    
    private double getField(Object obj, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(obj);
    }

    private void renderChains(PoseStack ms, MultiBufferSource buffer, Vec3 diff, int light, float textureOffset) {
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

        // 郢ｧ・ｪ郢晁ｼ斐◎郢昴・繝ｨ髫ｪ閧ｲ・ｮ繝ｻ(3郢晏ｳｨ繝｣郢昴・= 3/16)
        float offsetDist = 3.0f / 16.0f;
        Vector3f offsetUp = new Vector3f(up).mul(offsetDist);
        Vector3f offsetDown = new Vector3f(up).mul(-offsetDist);

        Matrix4f m = ms.last().pose();

        // 闕ｳ鄙ｫ繝ｻ郢昶・縺臥ｹ晢ｽｼ郢晢ｽｳ (闕ｳﾂ隴・ｽｹ陷ｷ莉｣竊楢恪霈費ｿ･)
        renderChain(builder, m, direction, up, right, offsetUp, length, light, textureOffset);
        // 闕ｳ荵昴・郢昶・縺臥ｹ晢ｽｼ郢晢ｽｳ (鬨ｾ繝ｻ蟀ｿ陷ｷ莉｣竊楢恪霈費ｿ･)
        renderChain(builder, m, direction, up, right, offsetDown, length, light, -textureOffset);
    }

    private void renderChain(VertexConsumer builder, Matrix4f m, Vector3f direction, Vector3f up, Vector3f right, Vector3f offset, float length, int light, float vOffset) {
        float width = 3.0f / 16.0f; // 郢昶・縺臥ｹ晢ｽｼ郢晢ｽｳ邵ｺ・ｮ陝ｷ繝ｻ(3郢晏ｳｨ繝｣郢昴・
        float r = width / 2.0f;

        Vector3f cross1 = new Vector3f(up).add(right).normalize().mul(r); // 45陟趣ｽｦ
        Vector3f cross2 = new Vector3f(up).sub(right).normalize().mul(r); // -45陟趣ｽｦ

        // Plane 1 (UV: 0,0 -> 3,16)
        Vector3f p1_start = new Vector3f(offset).sub(cross1);
        Vector3f p2_start = new Vector3f(offset).add(cross1);
        Vector3f p1_end = new Vector3f(p1_start).add(direction);
        Vector3f p2_end = new Vector3f(p2_start).add(direction);

        float uMin1 = 0.0f;
        float uMax1 = 3.0f / 16.0f;
        
        // Plane 2 (UV: 3,0 -> 6,16)
        Vector3f p3_start = new Vector3f(offset).sub(cross2);
        Vector3f p4_start = new Vector3f(offset).add(cross2);
        Vector3f p3_end = new Vector3f(p3_start).add(direction);
        Vector3f p4_end = new Vector3f(p4_start).add(direction);

        float uMin2 = 3.0f / 16.0f;
        float uMax2 = 6.0f / 16.0f;

        // V陟趣ｽｧ隶灘生繝ｻ郢ｧ・｢郢昜ｹ斟鍋ｹ晢ｽｼ郢ｧ・ｷ郢晢ｽｧ郢晢ｽｳ
        float vMin = vOffset;
        float vMax = length + vOffset;

        // 闕ｳ・｡鬮ｱ・｢隰蜀怜愛
        quad(builder, m, p1_start, p1_end, p2_end, p2_start, uMin1, uMax1, vMin, vMax, light);
        quad(builder, m, p2_start, p2_end, p1_end, p1_start, uMin1, uMax1, vMin, vMax, light);

        quad(builder, m, p3_start, p3_end, p4_end, p4_start, uMin2, uMax2, vMin, vMax, light);
        quad(builder, m, p4_start, p4_end, p3_end, p3_start, uMin2, uMax2, vMin, vMax, light);
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
