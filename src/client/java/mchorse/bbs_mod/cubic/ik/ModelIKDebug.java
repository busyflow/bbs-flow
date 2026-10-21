package mchorse.bbs_mod.cubic.ik;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.DebugOverlay;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.rig.RigHandles;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rig's overlay: each IK chain's goal and pole as diamonds bridged to the chain by dashed
 * relationship lines, and the form's controller bones as cubes in their own colour (gold unless
 * set). A spline's points are edited on the screen overlay instead ({@code SplineOverlay}). The
 * look is fixed; the two settings are how solid and how big it draws
 * ({@code BBSSettings.debugOpacity}, {@code debugSize}), and it always draws through the
 * model. It reads the model-local pivot frames the renderer already produced — IK is applied to
 * the rig before this runs, so the frames are the solved chain (re-solving would double-apply the
 * pole angle).
 *
 * <p>The chains show with the rig's debug switch ({@code BBSSettings.debugOverlay}, also F5); the
 * handles — goals, poles and controllers — show with it too, and on their own while the form is
 * open on the rig tab ({@link RigHandles}), to be clicked without the debug.
 *
 * <p>{@link #prepareStencil} mirrors the handles into the picking pass so a click on one selects
 * its bone, exactly as if its (often mesh-less) bone had been clicked directly. The pick geometry
 * is the visible marker's own shape and size.
 */
public final class ModelIKDebug
{
    /* The fixed look; sizes are fractions of the model's average rest bone length. */
    private static final int POLE_COLOR = 0xff8c26;
    private static final float LINES_SIZE = 0.02F;
    private static final float TARGET_SIZE = 0.09F;
    private static final float POLE_SIZE = 0.07F;
    private static final float CONTROLLER_SIZE = 0.1F;

    /** A marker on a bone: a diamond at a chain's goal or pole, a cube at a controller. */
    private record Handle(String bone, float size, int color, boolean cube)
    {
        void draw(BufferBuilder builder, MatrixStack stack, Vector3f point, float unit, float[] color, float a)
        {
            if (this.cube)
            {
                DebugOverlay.square(builder, stack, point, unit * this.size, color, a);
            }
            else
            {
                DebugOverlay.diamond(builder, stack, point, unit * this.size, color, a);
            }
        }
    }

    private ModelIKDebug()
    {
    }

    /**
     * Reads the solved chains off the rig now and returns their drawing, to run once the
     * form's body parts are down — drawn right away, the parts painted over it even through
     * the model. The rig is shared by every form of the same model, so a body part of that
     * model re-poses it: the frames are read here, never at draw time. Null when there is
     * nothing to draw.
     */
    public static Runnable prepare(MatrixStack stack, IModel model, ModelForm form)
    {
        boolean overlay = BBSSettings.debugOverlay.get();

        if ((!overlay && !RigHandles.shown(form)) || model == null || form == null)
        {
            return null;
        }

        List<ModelIKCache.CompiledChain> enabled = enabledChains(model, form);
        Map<String, Handle> handles = handles(model, form, enabled);
        List<ModelIKCache.CompiledChain> chains = overlay ? enabled : List.of();

        if (chains.isEmpty() && handles.isEmpty())
        {
            return null;
        }

        Map<String, PivotFrame> frames = collectFrames(model, chains, handles.keySet());

        return () -> render(stack, model, chains, handles, frames);
    }

    private static void render(MatrixStack stack, IModel model, List<ModelIKCache.CompiledChain> chains, Map<String, Handle> handles, Map<String, PivotFrame> frames)
    {
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        stack.push();

        if (model.isFacingFlipped())
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
        }

        float unit = DebugOverlay.modelUnit(model) * BBSSettings.debugSize.get();
        float opacity = BBSSettings.debugOpacity.get();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (ModelIKCache.CompiledChain chain : chains)
        {
            drawChain(builder, stack, frames, handles, chain, unit, opacity);
        }

        for (Handle handle : handles.values())
        {
            Vector3f point = position(frames, handle.bone());

            if (point != null)
            {
                handle.draw(builder, stack, point, unit, DebugOverlay.rgb(handle.color()), opacity);
            }
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        stack.pop();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /** The compiled chains whose IK is switched on. */
    private static List<ModelIKCache.CompiledChain> enabledChains(IModel model, ModelForm form)
    {
        List<ModelIKCache.CompiledChain> chains = new ArrayList<>();
        ModelIKCache.Compiled compiled = ModelIKCache.compile(model, form);

        if (compiled == null)
        {
            return chains;
        }

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            if (form.bones.getBone(chain.tip()) != null && form.ik.get().get(chain.tip()).enabled)
            {
                chains.add(chain);
            }
        }

        return chains;
    }

    /**
     * Every handle, one per bone: the chains' goals (in the colour each chain gives its goal) and
     * poles, then the controllers — a bone that is both a controller and a goal or a pole is the
     * controller's cube.
     */
    private static Map<String, Handle> handles(IModel model, ModelForm form, List<ModelIKCache.CompiledChain> chains)
    {
        Map<String, Handle> handles = new LinkedHashMap<>();

        for (ModelIKCache.CompiledChain chain : chains)
        {
            handles.put(chain.target(), new Handle(chain.target(), TARGET_SIZE, targetColor(form, chain.tip()), false));

            if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                handles.putIfAbsent(chain.poleTarget(), new Handle(chain.poleTarget(), POLE_SIZE, POLE_COLOR, false));
            }
        }

        for (BaseValue value : form.bones.getAll())
        {
            if (value instanceof FormBone bone && bone.boneController.get() && model.getAllGroupKeys().contains(bone.getId()))
            {
                handles.put(bone.getId(), new Handle(bone.getId(), CONTROLLER_SIZE, bone.boneControllerColor.get(), true));
            }
        }

        return handles;
    }

    /** The colour the chain on the tip gives the marker at its goal. */
    private static int targetColor(ModelForm form, String tip)
    {
        FormBone formBone = form.bones.getBone(tip);

        return formBone == null ? FormBone.TARGET_COLOR : formBone.ikTargetColor.get();
    }

    private static Map<String, PivotFrame> collectFrames(IModel model, List<ModelIKCache.CompiledChain> chains, Set<String> bones)
    {
        Set<String> wanted = new HashSet<>(bones);

        for (ModelIKCache.CompiledChain chain : chains)
        {
            wanted.add(chain.target());
            wanted.addAll(chain.chainRootToEffector());

            if (chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                wanted.add(chain.poleTarget());
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);

        ModelPivotFrames.collect(model, wanted, frames, null, true);

        return frames;
    }

    /**
     * Mirrors the handles into the picking pass: a pickable diamond at each, registered under
     * its bone. Must run after the model's bones are registered — the marker encodes {@code
     * stencilMap.objectIndex} as its colour and {@code addPicking} then claims that same id.
     * The matrix matches the visual overlay's, and so does the timing: read now, drawn after
     * the body parts (see {@link #prepare}), so a handle shown over a part is clickable there.
     */
    public static Runnable prepareStencil(MatrixStack stack, IModel model, ModelForm modelForm, StencilMap stencilMap, Form form)
    {
        if ((!BBSSettings.debugOverlay.get() && !RigHandles.shown(modelForm)) || model == null || modelForm == null || stencilMap == null)
        {
            return null;
        }

        Map<String, Handle> handles = handles(model, modelForm, enabledChains(model, modelForm));

        if (handles.isEmpty())
        {
            return null;
        }

        Map<String, PivotFrame> frames = collectFrames(model, List.of(), handles.keySet());

        return () -> renderStencil(stack, model, stencilMap, form, handles.values(), frames);
    }

    private static void renderStencil(MatrixStack stack, IModel model, StencilMap stencilMap, Form form, Collection<Handle> handles, Map<String, PivotFrame> frames)
    {
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        stack.push();

        if (model.isFacingFlipped())
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
        }

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float unit = DebugOverlay.modelUnit(model) * BBSSettings.debugSize.get();

        for (Handle handle : handles)
        {
            Vector3f point = position(frames, handle.bone());

            if (point == null)
            {
                continue;
            }

            handle.draw(builder, stack, point, unit, pickColor(stencilMap), 1F);
            stencilMap.addPicking(form, handle.bone());
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        stack.pop();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /** Dashed bridges from the chain to its goal and pole, in their handles' colours. */
    private static void drawChain(BufferBuilder builder, MatrixStack stack, Map<String, PivotFrame> frames, Map<String, Handle> handles, ModelIKCache.CompiledChain chain, float unit, float a)
    {
        List<String> ids = chain.chainRootToEffector();
        int n = ids.size();

        if (n < 2)
        {
            return;
        }

        List<Vector3f> pts = new ArrayList<>(n);

        for (String id : ids)
        {
            Vector3f p = position(frames, id);

            if (p == null)
            {
                return;
            }

            pts.add(p);
        }

        Vector3f target = position(frames, chain.target());

        if (target == null)
        {
            return;
        }

        Vector3f pole = chain.poleTarget() == null || chain.poleTarget().isEmpty() ? null : position(frames, chain.poleTarget());
        Matrix4f matrix = stack.peek().getPositionMatrix();
        float thickness = unit * LINES_SIZE;
        float dash = unit * 0.12F;

        DebugOverlay.segment(builder, matrix, thickness, true, dash, pts.get(n - 1), target, DebugOverlay.rgb(handles.get(chain.target()).color()), 0.4F * a);

        if (pole != null)
        {
            DebugOverlay.segment(builder, matrix, thickness, true, dash, pts.get(1), pole, DebugOverlay.rgb(handles.get(chain.poleTarget()).color()), 0.4F * a);
        }
    }

    /** The colour the picking pass reads back as the stencil's next id. */
    private static float[] pickColor(StencilMap stencilMap)
    {
        int id = stencilMap.objectIndex;

        return new float[] {(id & 0xFF) / 255F, (id >> 8 & 0xFF) / 255F, (id >> 16 & 0xFF) / 255F};
    }

    private static Vector3f position(Map<String, PivotFrame> frames, String bone)
    {
        PivotFrame frame = frames.get(bone);

        return frame == null ? null : new Vector3f(frame.position());
    }
}
