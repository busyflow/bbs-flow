package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.NameTagForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class NameTagFormRenderer extends FormRenderer<NameTagForm>
{
    public NameTagFormRenderer(NameTagForm form)
    {
        super(form);
    }

    @Override
    public boolean isPreviewCameraFacing()
    {
        return this.form.billboard.get();
    }

    @Override
    public AABB getPreviewBounds()
    {
        String content = StringUtils.processColoredText(this.form.text.get());
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        float w = font.getWidth(content) * 0.025F;
        float h = font.fontHeight * 0.025F;

        return new AABB(-w / 2F, -h, -0.05F, w / 2F, 0F, 0.05F);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        String content = StringUtils.processColoredText(this.form.text.get());
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int w = font.getWidth(content);
        int h = font.fontHeight;
        int x = (x1 + x2) / 2;
        int y = (y1 + y2) / 2;

        float opacity = MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25F);
        int background = this.form.background.get() ? ((int) (opacity * 255F) << 24) : 0;
        int color = this.form.color.get().getARGBColor();

        if (background != 0)
        {
            context.batcher.box(x - w / 2 - 2, y - h / 2 - 1, x + w / 2 + 2, y + h / 2 + 1, background);
        }

        context.batcher.text(content, x - w / 2, y - h / 2 + 1, color);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        if (this.form.text.get().isEmpty())
        {
            return;
        }

        Text text = Text.literal(StringUtils.processColoredText(this.form.text.get()));
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int light = context.light;
        boolean isPicking = context.isPicking();

        context.stack.push();

        float heightOffset = this.form.height.get();
        if (heightOffset != 0F)
        {
            context.stack.translate(0F, heightOffset, 0F);
        }

        if (this.form.billboard.get())
        {
            Matrix4f modelMatrix = context.stack.peek().getPositionMatrix();
            Vector3f scale = Vectors.TEMP_3F;

            modelMatrix.getScale(scale);

            modelMatrix.m00(1).m01(0).m02(0);
            modelMatrix.m10(0).m11(1).m12(0);
            modelMatrix.m20(0).m21(0).m22(1);

            modelMatrix.scale(scale);

            context.stack.peek().getNormalMatrix().identity();
        }

        context.stack.scale(0.025F, -0.025F, 0.025F);

        RenderSystem.disableCull();

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

        if (isPicking)
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
            });

            light = 0;
        }

        boolean grouped = !isPicking && FormTranslucentQueue.isActive();

        if (grouped)
        {
            Vector3f origin = context.stack.peek().getPositionMatrix().getTranslation(new Vector3f());

            FormTranslucentQueue.beginGroup(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin), false);
        }

        Matrix4f matrix4f = context.stack.peek().getPositionMatrix();
        float opacity = MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25F);
        int background = this.form.background.get() ? ((int) (opacity * 255F) << 24) : 0;
        float h = (float) (-textRenderer.getWidth(text) / 2);

        int color = this.form.color.get().getARGBColor();
        boolean seeThrough = this.form.seeThrough.get();
        if (context.entity != null && context.entity.isSneaking())
        {
            seeThrough = false;
        }

        if (seeThrough)
        {
            int alpha = (color >> 24) & 0xFF;
            /* Vanilla (EntityRenderer.renderLabelIfPresent) draws the see-through layer at
             * 0x20 alpha; anything higher stacks on the normal layer and looks too white. */
            int seeThroughAlpha = alpha * 0x20 / 255;
            int seeThroughColor = (color & 0x00FFFFFF) | (seeThroughAlpha << 24);

            textRenderer.draw(text, h, 0, seeThroughColor, false, matrix4f, consumers, TextRenderer.TextLayerType.SEE_THROUGH, background, light);
            textRenderer.draw(text, h, 0, color, false, matrix4f, consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        }
        else
        {
            textRenderer.draw(text, h, 0, color, false, matrix4f, consumers, TextRenderer.TextLayerType.NORMAL, background, light);
        }

        consumers.draw();

        if (grouped)
        {
            FormTranslucentQueue.endGroup();
        }

        if (isPicking)
        {
            CustomVertexConsumerProvider.clearRunnables();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        context.stack.pop();
    }
}
