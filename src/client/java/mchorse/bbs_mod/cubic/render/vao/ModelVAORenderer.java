package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

public class ModelVAORenderer
{
    /** Sampler uniform names — setupUniforms runs per draw call, no string building there. */
    private static final String[] SAMPLERS =
    {
        "Sampler0", "Sampler1", "Sampler2", "Sampler3", "Sampler4", "Sampler5",
        "Sampler6", "Sampler7", "Sampler8", "Sampler9", "Sampler10", "Sampler11"
    };

    private static final Matrix4f TEMP_MODEL_VIEW = new Matrix4f();

    private static ShaderProgram passShader;
    private static GlUniform passNormalUniform;
    private static boolean passShaderBound;
    private static int passSavedVAO;
    private static int passSavedEBO;
    private static int worldBatchDepth;
    private static int worldSavedVAO;
    private static int worldSavedEBO;
    private static ShaderProgram worldBoundShader;
    private static boolean internalProgramChange;
    private static int passTex0 = -1;
    private static int passTex1 = -1;

    /**
     * Called from GlStateManagerMixin whenever any code switches GL shader programs.
     * Drops the world-batch persistent shader binding if external rendering (vanilla mobs,
     * items, shadows, particles) took over the active program.
     */
    public static void onProgramChange(int program)
    {
        if (!internalProgramChange && worldBoundShader != null)
        {
            worldBoundShader = null;
            passShaderBound = false;
        }
    }

    private static void unbindWorldShader()
    {
        ShaderProgram shader = worldBoundShader;

        if (shader != null)
        {
            worldBoundShader = null;
            internalProgramChange = true;

            try
            {
                shader.unbind();
            }
            finally
            {
                internalProgramChange = false;
            }
        }
    }

    /**
     * Bracket a batch of model draws (such as a film's world actor loop) so GL_VERTEX_ARRAY_BINDING
     * and GL_ELEMENT_ARRAY_BUFFER_BINDING are queried and restored once instead of per actor,
     * and consecutive VAO actors sharing BBSShaders.getModel() keep the shader program bound.
     */
    public static void beginWorldBatch()
    {
        if (worldBatchDepth == 0)
        {
            worldSavedVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            worldSavedEBO = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            worldBoundShader = null;
        }

        worldBatchDepth += 1;
    }

    public static void endWorldBatch()
    {
        if (worldBatchDepth <= 0)
        {
            return;
        }

        worldBatchDepth -= 1;

        if (worldBatchDepth == 0)
        {
            unbindWorldShader();
            GL30.glBindVertexArray(worldSavedVAO);
            GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, worldSavedEBO);
            net.minecraft.client.render.BufferRenderer.resetCurrentVertexBuffer();
        }
    }

    private static ShaderProgram cachedNormalShader;
    private static GlUniform cachedNormalUniform;

    private static ShaderProgram lastGlobalShader;
    private static long lastGlobalFrame = Long.MIN_VALUE;
    private static final Matrix4f lastGlobalProjection = new Matrix4f();
    private static final Matrix3f lastGlobalViewRot = new Matrix3f();
    private static float lastGlobalFogStart = Float.NaN;
    private static float lastGlobalFogEnd = Float.NaN;
    private static boolean lastGlobalUnlimited;

    private static GlUniform getNormalUniform(ShaderProgram shader)
    {
        if (shader == passShader)
        {
            return passNormalUniform;
        }

        if (shader != cachedNormalShader)
        {
            cachedNormalShader = shader;
            cachedNormalUniform = shader == null ? null : shader.getUniform("NormalMat");
        }

        return cachedNormalUniform;
    }

    public static void beginPass(ShaderProgram shader)
    {
        if (shader == null || passShader != null)
        {
            return;
        }

        passShader = shader;
        passNormalUniform = getNormalUniform(shader);
        passShaderBound = false;
        if (worldBatchDepth == 0)
        {
            passSavedVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            passSavedEBO = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        }
        passTex0 = RenderSystem.getShaderTexture(0);
        passTex1 = RenderSystem.getShaderTexture(1);
        int tex2 = RenderSystem.getShaderTexture(2);

        long frame = mchorse.bbs_mod.forms.renderers.utils.RenderFrame.getFrame();
        boolean unlimited = mchorse.bbs_mod.BBSSettings.unlimitedReplayRenderDistance.get();
        float fogStart = unlimited ? 1_000_000F : RenderSystem.getShaderFogStart();
        float fogEnd = unlimited ? 1_001_000F : RenderSystem.getShaderFogEnd();
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Matrix3f viewRot = RenderSystem.getInverseViewRotationMatrix();

        boolean reuseGlobals = !BBSRendering.isIrisShadersEnabled()
            && shader == lastGlobalShader
            && frame == lastGlobalFrame
            && unlimited == lastGlobalUnlimited
            && fogStart == lastGlobalFogStart
            && fogEnd == lastGlobalFogEnd
            && proj.equals(lastGlobalProjection)
            && viewRot.equals(lastGlobalViewRot);

        if (worldBoundShader != null && (worldBoundShader != shader || !reuseGlobals))
        {
            unbindWorldShader();
        }

        if (worldBoundShader == shader && reuseGlobals)
        {
            /* Keep the model shader bound across consecutive actors in the world batch:
             * only reconcile texture units 0/1/2 (GlStateManager skips redundant GL binds). */
            passShaderBound = true;
            RenderSystem.activeTexture(GL13.GL_TEXTURE2);
            RenderSystem.bindTexture(tex2);
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            RenderSystem.bindTexture(passTex1);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(passTex0);
        }
        else if (reuseGlobals)
        {
            shader.addSampler("Sampler0", passTex0);
            shader.addSampler("Sampler1", passTex1);
            shader.addSampler("Sampler2", tex2);
        }
        else
        {
            setupGlobalUniforms(shader);
            lastGlobalShader = shader;
            lastGlobalFrame = frame;
            lastGlobalUnlimited = unlimited;
            lastGlobalFogStart = fogStart;
            lastGlobalFogEnd = fogEnd;
            lastGlobalProjection.set(proj);
            lastGlobalViewRot.set(viewRot);
        }
    }

    public static void endPass()
    {
        if (passShader == null)
        {
            return;
        }

        if (passShaderBound)
        {
            if (worldBatchDepth > 0 && !BBSRendering.isIrisShadersEnabled())
            {
                worldBoundShader = passShader;
            }
            else
            {
                internalProgramChange = true;

                try
                {
                    passShader.unbind();
                }
                finally
                {
                    internalProgramChange = false;
                }
            }

            passShaderBound = false;
        }

        if (worldBatchDepth == 0)
        {
            GL30.glBindVertexArray(passSavedVAO);
            GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, passSavedEBO);
        }
        else
        {
            /* Invalidate vanilla BufferRenderer's cached VertexBuffer too — otherwise a MobForm/item
             * drawn next inside the world batch skips glBindVertexArray and crashes in glDrawElements. */
            net.minecraft.client.render.BufferRenderer.resetCurrentVertexBuffer();
        }
        passShader = null;
        passNormalUniform = null;
    }

    public static boolean isInPass()
    {
        return passShader != null;
    }

    public static boolean isInPass(ShaderProgram shader)
    {
        return passShader != null && passShader == shader;
    }

    public static boolean isShaderBound(ShaderProgram shader)
    {
        return (passShaderBound && passShader == shader) || (worldBoundShader != null && worldBoundShader == shader);
    }

    /**
     * The full model-view a draw issued right now would use. Deferred translucent commands
     * capture it at enqueue time, because the global model-view is different by the time
     * the queue flushes at the end of the frame.
     */
    public static Matrix4f captureModelView(MatrixStack stack)
    {
        return new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.peek().getPositionMatrix());
    }

    public static void render(ShaderProgram shader, IModelVAO modelVAO, MatrixStack stack, float r, float g, float b, float a, int light, int overlay)
    {
        TEMP_MODEL_VIEW.set(RenderSystem.getModelViewMatrix()).mul(stack.peek().getPositionMatrix());
        render(shader, modelVAO, TEMP_MODEL_VIEW, stack.peek().getNormalMatrix(), r, g, b, a, light, overlay);
    }

    public static void render(ShaderProgram shader, IModelVAO modelVAO, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay)
    {
        if (passShader == shader && shader != null)
        {
            int tex0 = RenderSystem.getShaderTexture(0);
            int tex1 = RenderSystem.getShaderTexture(1);

            if (!BBSRendering.isIrisShadersEnabled())
            {
                if (!passShaderBound)
                {
                    shader.addSampler("Sampler0", tex0);
                    shader.addSampler("Sampler1", tex1);
                    passTex0 = tex0;
                    passTex1 = tex1;
                    setPerDrawUniforms(shader, modelView, normalMat);
                    internalProgramChange = true;

                    try
                    {
                        shader.bind();
                    }
                    finally
                    {
                        internalProgramChange = false;
                    }

                    passShaderBound = true;
                }
                else
                {
                    if (tex0 != passTex0)
                    {
                        passTex0 = tex0;
                        shader.addSampler("Sampler0", tex0);
                    }

                    if (tex1 != passTex1)
                    {
                        passTex1 = tex1;
                        shader.addSampler("Sampler1", tex1);
                    }

                    /* Always ensure GL_TEXTURE0/1 match the shader's samplers in case
                     * external rendering bound a white/other texture directly via GL. */
                    RenderSystem.activeTexture(GL13.GL_TEXTURE1);
                    RenderSystem.bindTexture(tex1);
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    RenderSystem.bindTexture(tex0);

                    uploadPerDrawUniforms(shader, modelView, normalMat);
                }

                modelVAO.render(shader.getFormat(), r, g, b, a, light, overlay);
            }
            else
            {
                if (tex0 != passTex0)
                {
                    passTex0 = tex0;
                    shader.addSampler("Sampler0", tex0);
                }

                if (tex1 != passTex1)
                {
                    passTex1 = tex1;
                    shader.addSampler("Sampler1", tex1);
                }

                setPerDrawUniforms(shader, modelView, normalMat);
                shader.bind();
                modelVAO.render(shader.getFormat(), r, g, b, a, light, overlay);
                shader.unbind();
            }

            return;
        }

        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int currentElementArrayBuffer = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        setupUniforms(shader, modelView, normalMat);

        shader.bind();
        modelVAO.render(shader.getFormat(), r, g, b, a, light, overlay);
        shader.unbind();

        GL30.glBindVertexArray(currentVAO);
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer);
    }

    public static void setPerDrawUniforms(ShaderProgram shader, Matrix4f modelView, Matrix3f normalMat)
    {
        if (shader.modelViewMat != null)
        {
            shader.modelViewMat.set(modelView);
        }

        GlUniform normalUniform = getNormalUniform(shader);

        if (normalUniform != null)
        {
            normalUniform.set(normalMat);
        }
    }

    public static void uploadPerDrawUniforms(ShaderProgram shader, Matrix4f modelView, Matrix3f normalMat)
    {
        if (shader.modelViewMat != null)
        {
            shader.modelViewMat.set(modelView);
            shader.modelViewMat.upload();
        }

        GlUniform normalUniform = getNormalUniform(shader);

        if (normalUniform != null)
        {
            normalUniform.set(normalMat);
            normalUniform.upload();
        }
    }

    public static void setupGlobalUniforms(ShaderProgram shader)
    {
        for (int i = 0; i < SAMPLERS.length; i++)
        {
            shader.addSampler(SAMPLERS[i], RenderSystem.getShaderTexture(i));
        }

        if (shader.projectionMat != null)
        {
            shader.projectionMat.set(RenderSystem.getProjectionMatrix());
        }

        if (shader.viewRotationMat != null)
        {
            shader.viewRotationMat.set(RenderSystem.getInverseViewRotationMatrix());
        }

        if (shader.fogStart != null)
        {
            shader.fogStart.set(mchorse.bbs_mod.BBSSettings.unlimitedReplayRenderDistance.get() ? 1_000_000F : RenderSystem.getShaderFogStart());
        }

        if (shader.fogEnd != null)
        {
            shader.fogEnd.set(mchorse.bbs_mod.BBSSettings.unlimitedReplayRenderDistance.get() ? 1_001_000F : RenderSystem.getShaderFogEnd());
        }

        if (shader.fogColor != null)
        {
            shader.fogColor.set(RenderSystem.getShaderFogColor());
        }

        if (shader.fogShape != null)
        {
            shader.fogShape.set(RenderSystem.getShaderFogShape().getId());
        }

        if (shader.colorModulator != null)
        {
            shader.colorModulator.set(1F, 1F, 1F, 1F);
        }

        if (shader.gameTime != null)
        {
            shader.gameTime.set(RenderSystem.getShaderGameTime());
        }

        if (shader.textureMat != null)
        {
            shader.textureMat.set(RenderSystem.getTextureMatrix());
        }

        RenderSystem.setupShaderLights(shader);
    }

    public static void setupUniforms(MatrixStack stack, ShaderProgram shader)
    {
        TEMP_MODEL_VIEW.set(RenderSystem.getModelViewMatrix()).mul(stack.peek().getPositionMatrix());
        setupUniforms(shader, TEMP_MODEL_VIEW, stack.peek().getNormalMatrix());
    }

    public static void setupUniforms(ShaderProgram shader, Matrix4f modelView, Matrix3f normalMat)
    {
        setupGlobalUniforms(shader);
        setPerDrawUniforms(shader, modelView, normalMat);
    }
}
