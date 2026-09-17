package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.overwrite.POVClip;
import mchorse.bbs_mod.camera.clips.overwrite.PovHudData;
import mchorse.bbs_mod.client.renderer.ItemPredicateDonor;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayItemUse;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 3D First-Person Hand and Held Item Renderer for the POV Camera Clip.
 *
 * Handles:
 * - 3D perspective projection and depth buffer management
 * - First-person bare arm rendering with replay skin (matching vanilla Alex/Steve or custom ModelForm)
 * - Subtle camera rotation sway inertia (arm drift) matching vanilla Minecraft feel
 * - Held item rendering in main hand and offhand
 * - Item use animations (bow drawing, trident charging, eating/drinking, shield blocking, crossbow)
 */
public class UIPovHandRenderer
{
    public static void render(MatrixStack stack, Batcher2D batcher, ClipContext context)
    {
        List<PovHudData> list = POVClip.getPovHuds(context);

        if (list == null || list.isEmpty())
        {
            return;
        }

        PovHudData data = list.get(list.size() - 1);
        POVClip clip = data.clip;

        if (!clip.enabled.get() || !clip.showHands.get() || data.factor <= 0F)
        {
            return;
        }

        Replay replay = data.replay;

        if (replay == null)
        {
            return;
        }

        IEntity entity = null;

        if (context instanceof CameraClipContext cameraContext && cameraContext.entities != null)
        {
            entity = cameraContext.entities.get(replay.getId());
        }

        if (entity == null)
        {
            UIBaseMenu currentMenu = UIScreen.getCurrentMenu();

            if (currentMenu instanceof UIDashboard dashboard && dashboard.getPanels().panel instanceof UIFilmPanel filmPanel)
            {
                entity = filmPanel.getController().getEntities().get(replay.getId());
            }
        }

        batcher.flush();

        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.gl.Framebuffer fb = mc.getFramebuffer();

        int fbWidth = fb.textureWidth;
        int fbHeight = fb.textureHeight;
        float aspect = fbHeight > 0 ? (float) fbWidth / (float) fbHeight : 1.0F;

        float fov = clip.fov.get();

        if (fov <= 0F)
        {
            fov = 70F;
        }

        float fovRad = (float) Math.toRadians(fov);
        Matrix4f projection = new Matrix4f().perspective(fovRad, aspect, 0.05F, 100.0F);

        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);

        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        mc.gameRenderer.getLightmapTextureManager().enable();
        mc.gameRenderer.getOverlayTexture().setupOverlayColor();
        DiffuseLighting.enableGuiDepthLighting();

        double x = replay.keyframes.x.interpolate(data.exactTick);
        double y = replay.keyframes.y.interpolate(data.exactTick);
        double z = replay.keyframes.z.interpolate(data.exactTick);
        int light = LightmapTextureManager.pack(15, 15);

        if (mc.world != null)
        {
            light = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(x, y + 1.62D, z));
        }

        int selectedSlot = replay.keyframes.getSelectedSlot(data.exactTick);
        ItemStack mainStack = replay.keyframes.hotbar.get(selectedSlot).interpolate(data.exactTick, ItemStack.EMPTY);

        if ((mainStack == null || mainStack.isEmpty()) && entity != null)
        {
            mainStack = entity.getEquipmentStack(EquipmentSlot.MAINHAND);
        }
        if ((mainStack == null || mainStack.isEmpty()) && mc.player != null)
        {
            mainStack = mc.player.getMainHandStack();
        }
        if (mainStack == null)
        {
            mainStack = ItemStack.EMPTY;
        }

        ItemStack offStack = replay.keyframes.getEquipmentChannel(EquipmentSlot.OFFHAND).interpolate(data.exactTick, ItemStack.EMPTY);

        if ((offStack == null || offStack.isEmpty()) && entity != null)
        {
            offStack = entity.getEquipmentStack(EquipmentSlot.OFFHAND);
        }
        if ((offStack == null || offStack.isEmpty()) && mc.player != null)
        {
            offStack = mc.player.getOffHandStack();
        }
        if (offStack == null)
        {
            offStack = ItemStack.EMPTY;
        }

        MatrixStack handStack = new MatrixStack();

        float swingProgress = entity != null ? entity.getHandSwingProgress(context.transition) : 0F;
        float offSwingProgress = (entity != null && entity.isSwingingOffHand()) ? entity.getHandSwingProgress(context.transition) : 0F;

        ItemUsePose.Use mainUse = ReplayItemUse.compute(replay, data.exactTick, true);
        ItemUsePose.Use offUse = ReplayItemUse.compute(replay, data.exactTick, false);

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        Form form = entity != null ? entity.getForm() : replay.form.get();

        boolean renderOffHand = !offStack.isEmpty();

        if (mainUse != null && (mainUse.action() == UseAction.BOW || mainUse.action() == UseAction.CROSSBOW))
        {
            renderOffHand = false;
        }

        /* Render Main Hand */
        if (mainStack.isEmpty())
        {
            handStack.push();
            applyArmHoldingItem(handStack, Arm.RIGHT, 0F, swingProgress);
            renderArm(handStack, light, form, replay, entity, Hand.MAIN_HAND);
            handStack.pop();
        }
        else
        {
            handStack.push();
            LivingEntity donor = ItemPredicateDonor.get(mainStack, mainUse);

            if (donor == null && entity instanceof MCEntity mcEntity && mcEntity.getMcEntity() instanceof LivingEntity living)
            {
                donor = living;
            }
            if (donor == null)
            {
                donor = mc.player;
            }

            if (mainUse != null && mainUse.action() != UseAction.NONE)
            {
                applyUseItemTransforms(handStack, Arm.RIGHT, mainUse, mainStack);
            }
            else
            {
                float sqrtSwing = MathHelper.sqrt(swingProgress);
                float offsetX = -0.4F * MathHelper.sin(sqrtSwing * (float) Math.PI);
                float offsetY = 0.2F * MathHelper.sin(sqrtSwing * ((float) Math.PI * 2.0F));
                float offsetZ = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);

                handStack.translate(offsetX, offsetY, offsetZ);
                applyEquipOffset(handStack, Arm.RIGHT, 0F);
                applySwingOffset(handStack, Arm.RIGHT, swingProgress);

                if (CrossbowItem.isCharged(mainStack) && swingProgress < 0.001F)
                {
                    handStack.translate(-0.641864F, 0.0F, 0.0F);
                    handStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(10.0F));
                }
            }

            renderHeldItem(mc, donor, mainStack, ModelTransformationMode.FIRST_PERSON_RIGHT_HAND, false, handStack, immediate, light);
            handStack.pop();
        }

        /* Render Off Hand */
        if (renderOffHand)
        {
            handStack.push();
            LivingEntity donor = ItemPredicateDonor.get(offStack, offUse);

            if (donor == null && entity instanceof MCEntity mcEntity && mcEntity.getMcEntity() instanceof LivingEntity living)
            {
                donor = living;
            }
            if (donor == null)
            {
                donor = mc.player;
            }

            if (offUse != null && offUse.action() != UseAction.NONE)
            {
                applyUseItemTransforms(handStack, Arm.LEFT, offUse, offStack);
            }
            else
            {
                float sqrtSwing = MathHelper.sqrt(offSwingProgress);
                float offsetX = -0.4F * MathHelper.sin(sqrtSwing * (float) Math.PI);
                float offsetY = 0.2F * MathHelper.sin(sqrtSwing * ((float) Math.PI * 2.0F));
                float offsetZ = -0.2F * MathHelper.sin(offSwingProgress * (float) Math.PI);

                handStack.translate(-offsetX, offsetY, offsetZ);
                applyEquipOffset(handStack, Arm.LEFT, 0F);
                applySwingOffset(handStack, Arm.LEFT, offSwingProgress);

                if (CrossbowItem.isCharged(offStack) && offSwingProgress < 0.001F)
                {
                    handStack.translate(0.641864F, 0.0F, 0.0F);
                    handStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-10.0F));
                }
            }

            renderHeldItem(mc, donor, offStack, ModelTransformationMode.FIRST_PERSON_LEFT_HAND, true, handStack, immediate, light);
            handStack.pop();
        }

        immediate.draw();

        DiffuseLighting.disableGuiDepthLighting();
        RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        MatrixStackUtils.restoreMatrices();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
    }

    private static void renderArm(MatrixStack stack, int light, Form form, Replay replay, IEntity entity, Hand hand)
    {
        boolean rendered = false;
        Form formToUse = form;

        if (formToUse == null && entity != null)
        {
            formToUse = entity.getForm();
        }
        if (formToUse == null && replay != null)
        {
            formToUse = replay.form.get();
        }

        if (formToUse != null)
        {
            Form root = FormUtils.getRoot(formToUse);

            if (root instanceof ModelForm modelForm)
            {
                ModelForm toRender = modelForm;

                if (toRender.model.get().isEmpty())
                {
                    toRender = (ModelForm) FormUtils.copy(modelForm);
                    toRender.model.set("player/steve");
                }

                if (FormUtilsClient.getRenderer(toRender) instanceof ModelFormRenderer modelRenderer)
                {
                    modelRenderer.ensureAnimator(0F);
                    rendered = modelRenderer.renderFirstPersonHand(stack, light, hand);
                }
            }
        }

        if (!rendered)
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            AbstractClientPlayerEntity player = null;

            if (entity instanceof MCEntity mcEntity && mcEntity.getMcEntity() instanceof AbstractClientPlayerEntity clientPlayer)
            {
                player = clientPlayer;
            }
            else if (mc.player != null)
            {
                player = mc.player;
            }

            if (player != null)
            {
                EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);

                if (renderer instanceof PlayerEntityRenderer playerRenderer)
                {
                    VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

                    if (hand == Hand.MAIN_HAND)
                    {
                        playerRenderer.renderRightArm(stack, immediate, light, player);
                    }
                    else
                    {
                        playerRenderer.renderLeftArm(stack, immediate, light, player);
                    }

                    immediate.draw();
                    rendered = true;
                }
            }
        }

        if (!rendered)
        {
            ModelForm fallbackForm = new ModelForm();
            fallbackForm.model.set("player/steve");

            if (FormUtilsClient.getRenderer(fallbackForm) instanceof ModelFormRenderer modelRenderer)
            {
                modelRenderer.ensureAnimator(0F);
                modelRenderer.renderFirstPersonHand(stack, light, hand);
            }
        }
    }

    private static void renderHeldItem(MinecraftClient mc, LivingEntity donor, ItemStack item, ModelTransformationMode mode, boolean leftHanded, MatrixStack stack, VertexConsumerProvider.Immediate immediate, int light)
    {
        if (item == null || item.isEmpty())
        {
            return;
        }

        mc.getItemRenderer().renderItem(
            donor,
            item,
            mode,
            leftHanded,
            stack,
            immediate,
            mc.world,
            light,
            OverlayTexture.DEFAULT_UV,
            0
        );
    }

    private static void applyUseItemTransforms(MatrixStack stack, Arm arm, ItemUsePose.Use use, ItemStack item)
    {
        float side = arm == Arm.RIGHT ? 1.0F : -1.0F;

        switch (use.action())
        {
            case BOW:
                applyEquipOffset(stack, arm, 0F);
                stack.translate(side * -0.2785682F, 0.18344387F, 0.15731531F);
                stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-13.935F));
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 35.3F));
                stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -9.785F));
                float bowPull = use.elapsed() / 20.0F;
                bowPull = (bowPull * bowPull + bowPull * 2.0F) / 3.0F;
                if (bowPull > 1.0F) bowPull = 1.0F;
                if (bowPull > 0.1F)
                {
                    float wobble = MathHelper.sin((use.elapsed() - 0.1F) * 1.3F);
                    float wobbleAmount = (bowPull - 0.1F) * wobble;
                    stack.translate(0.0F, wobbleAmount * 0.004F, 0.0F);
                }
                stack.translate(0.0F, 0.0F, bowPull * 0.04F);
                stack.scale(1.0F, 1.0F, 1.0F + bowPull * 0.2F);
                stack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(side * 45.0F));
                break;

            case SPEAR:
                applyEquipOffset(stack, arm, 0F);
                stack.translate(side * -0.5F, 0.7F, 0.1F);
                stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-55.0F));
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 35.3F));
                stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -9.785F));
                float spearPull = use.elapsed() / 10.0F;
                if (spearPull > 1.0F) spearPull = 1.0F;
                if (spearPull > 0.1F)
                {
                    float wobble = MathHelper.sin((use.elapsed() - 0.1F) * 1.3F);
                    float wobbleAmount = (spearPull - 0.1F) * wobble;
                    stack.translate(0.0F, wobbleAmount * 0.004F, 0.0F);
                }
                stack.translate(0.0F, 0.0F, spearPull * 0.2F);
                stack.scale(1.0F, 1.0F, 1.0F + spearPull * 0.2F);
                stack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(side * 45.0F));
                break;

            case CROSSBOW:
                applyEquipOffset(stack, arm, 0F);
                stack.translate(side * -0.4785682F, -0.094387F, 0.05731531F);
                stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-11.935F));
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 65.3F));
                stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -9.785F));
                float pullTime = (float) CrossbowItem.getPullTime(item);
                float crossPull = pullTime > 0 ? use.elapsed() / pullTime : 1.0F;
                if (crossPull > 1.0F) crossPull = 1.0F;
                if (crossPull > 0.1F)
                {
                    float wobble = MathHelper.sin((use.elapsed() - 0.1F) * 1.3F);
                    float wobbleAmount = (crossPull - 0.1F) * wobble;
                    stack.translate(0.0F, wobbleAmount * 0.004F, 0.0F);
                }
                stack.translate(0.0F, 0.0F, crossPull * 0.04F);
                stack.scale(1.0F, 1.0F, 1.0F + crossPull * 0.2F);
                stack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(side * 45.0F));
                break;

            case EAT:
            case DRINK:
                applyEatOrDrinkTransformation(stack, use.elapsed(), arm, item);
                applyEquipOffset(stack, arm, 0F);
                break;

            case BLOCK:
            default:
                applyEquipOffset(stack, arm, 0F);
                break;
        }
    }

    private static void applyEatOrDrinkTransformation(MatrixStack stack, float elapsedTicks, Arm arm, ItemStack item)
    {
        int maxUseTime = item.getMaxUseTime() > 0 ? item.getMaxUseTime() : 32;
        float timeLeft = Math.max(0F, maxUseTime - elapsedTicks);
        float f = timeLeft / (float) maxUseTime;

        if (f < 0.8F)
        {
            float g = MathHelper.abs(MathHelper.cos(timeLeft / 4.0F * (float) Math.PI) * 0.1F);
            stack.translate(0.0F, g, 0.0F);
        }

        float h = 1.0F - (float) Math.pow(f, 27.0D);
        int side = arm == Arm.RIGHT ? 1 : -1;

        stack.translate(h * 0.6F * side, h * -0.5F, 0.0F);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * h * 90.0F));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(h * 10.0F));
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * h * 30.0F));
    }

    private static void applyEquipOffset(MatrixStack stack, Arm arm, float equipProgress)
    {
        float side = arm == Arm.RIGHT ? 1.0F : -1.0F;

        stack.translate(side * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
    }

    private static void applySwingOffset(MatrixStack stack, Arm arm, float swingProgress)
    {
        float side = arm == Arm.RIGHT ? 1.0F : -1.0F;
        float sinSwingSq = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);

        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + sinSwingSq * -20.0F)));

        float sinSqrtSwing = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);

        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * sinSqrtSwing * -20.0F));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSqrtSwing * -80.0F));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F));
    }

    private static void applyArmHoldingItem(MatrixStack stack, Arm arm, float equipProgress, float swingProgress)
    {
        float side = arm == Arm.RIGHT ? 1.0F : -1.0F;
        float sqrtSwing = MathHelper.sqrt(swingProgress);
        float offsetX = -0.3F * MathHelper.sin(sqrtSwing * (float) Math.PI);
        float offsetY = 0.4F * MathHelper.sin(sqrtSwing * ((float) Math.PI * 2.0F));
        float offsetZ = -0.4F * MathHelper.sin(swingProgress * (float) Math.PI);

        stack.translate(side * (offsetX + 0.64000005F), offsetY - 0.6F + equipProgress * -0.6F, offsetZ - 0.71999997F);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45.0F));

        float sinSwingSq = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float sinSqrtSwing = MathHelper.sin(sqrtSwing * (float) Math.PI);

        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * sinSqrtSwing * 70.0F));
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * sinSwingSq * -20.0F));

        stack.translate(side * -1.0F, 3.6F, 3.5F);
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 120.0F));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(200.0F));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -135.0F));
        stack.translate(side * 5.6F, 0.0F, 0.0F);
    }
}
