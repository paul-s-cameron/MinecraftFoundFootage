package com.sp.entity.client.renderer;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.SmilerComponent;
import com.sp.entity.client.model.SmilerModel;
import com.sp.entity.custom.SmilerEntity;
import com.sp.init.ModModelLayers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public class SmilerRenderer extends MobEntityRenderer<SmilerEntity, SmilerModel<SmilerEntity>> {
    private static final Identifier defaultTexture = new Identifier(SPBRevamped.MOD_ID, "textures/entity/smiler/smiler.png");
    private static final Identifier texture1 = new Identifier(SPBRevamped.MOD_ID, "textures/entity/smiler/smiler1.png");
    private static final Identifier texture2 = new Identifier(SPBRevamped.MOD_ID, "textures/entity/smiler/smiler2.png");

    public SmilerRenderer(EntityRendererFactory.Context context) {
        super(context, new SmilerModel<>(context.getPart(ModModelLayers.SMILER)), 0);
    }

    @Override
    public void render(SmilerEntity mobEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        // The model is a single flat quad with no depth, turned to face the viewer. But
        // LivingEntityRenderer.setupTransforms then rotates everything by (180 - bodyYaw), so the
        // net rotation is only what we asked for while bodyYaw happens to be zero — which it was,
        // back when a smiler could not move or turn. Now that one walks toward a light and looks
        // at what it is hunting, its own facing has to be cancelled out, or the face is rotated
        // edge-on to the camera and a zero-depth quad seen edge-on is nothing at all.
        //
        // Interpolated exactly as setupTransforms interpolates it, so the two cancel every frame
        // rather than only on tick boundaries.
        float bodyYaw = MathHelper.lerpAngleDegrees(g, mobEntity.prevBodyYaw, mobEntity.bodyYaw);

        matrixStack.translate(0,1,0);
        float angle = lookAtEntityAroundYAxis(mobEntity.getEyePos(), camera.getPos()) + bodyYaw;
        matrixStack.multiply(new Quaternionf().rotateXYZ(0, (float) Math.toRadians(angle), 0));
        matrixStack.translate(0,-1,0);


        super.render(mobEntity, f, g, matrixStack, vertexConsumerProvider, i);
    }

    @Override
    public Identifier getTexture(SmilerEntity entity) {
        SmilerComponent component = InitializeComponents.SMILER.get(entity);
        return switch (component.getRandomTexture()) {
            case 1 -> texture1;
            case 2 -> texture2;
            default -> defaultTexture;
        };
    }

    @Override
    protected @Nullable RenderLayer getRenderLayer(SmilerEntity entity, boolean showBody, boolean translucent, boolean showOutline) {
        return super.getRenderLayer(entity, showBody, translucent, showOutline);
    }

    public static float lookAtEntityAroundYAxis(Vec3d position1, Vec3d position2) {
        double d = position2.getX() - position1.getX();
        double e = position2.getZ() - position1.getZ();
        float h = -(float)(MathHelper.atan2(e, d) * 180.0F / (float)Math.PI) - 90.0F;

        return h - 180.0F;
    }
}
