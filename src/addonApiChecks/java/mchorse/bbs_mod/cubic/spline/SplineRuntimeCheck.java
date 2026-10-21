package mchorse.bbs_mod.cubic.spline;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.CubicMatrixRenderer;
import mchorse.bbs_mod.cubic.rig.RigOwners;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.ui.utils.SplineEditorUtils;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Real cubic writeback and render-matrix checks; no game window or graphics calls. */
public final class SplineRuntimeCheck
{
    private static int checks;

    public static void main(String[] args)
    {
        run();
    }

    public static void run()
    {
        Model model = new Model(null);
        ModelGroup all = new ModelGroup("All");
        model.topGroups.add(all);
        ModelGroup parent = all;
        for (int i = 0; i < 8; i++)
        {
            ModelGroup bone = new ModelGroup("bone" + i);
            bone.initial.translate.set(4F, 6F, i * 7.02F);
            parent.children.add(bone);
            parent = bone;
        }
        ModelGroup other = new ModelGroup("other");
        other.initial.translate.set(16, 0, 0);
        all.children.add(other);
        ModelGroup jaw = new ModelGroup("jaw");
        jaw.initial.translate.set(16, 4, -3);
        other.children.add(jaw);
        model.initialize();
        model.resetPose();
        ModelInstance instance = new ModelInstance("spline-runtime-check", model, new Animations(null), null);
        ModelForm form = new ModelForm();
        SplineIK spline = new SplineIK("");
        spline.chainLength.set(8);
        spline.tip.set("bone7");
        form.splines.add(spline);
        List<Vector3f> originals = positions(model);
        check(spline.points.getAllTyped().isEmpty(), "New spline has no automatic controls");
        List<Vector3f> controls = List.of(new Vector3f(4F, 6F, 0F).div(16F),
            new Vector3f(4F, 6F, 16.38F).div(16F), new Vector3f(4F, 6F, 32.76F).div(16F),
            new Vector3f(4F, 6F, 49.14F).div(16F));
        for (Vector3f point : controls) addPoint(spline, point);
        model.snapshotChannels();
        ModelSplineRuntime.apply(instance, form);
        List<Vector3f> solved = positions(model);
        check(RigOwners.of(model, form).locksRotation("bone0"), "Active spline owns FK rotation gestures");
        check(RigOwners.of(model, form).locksRotation("bone7"), "Terminal rotation is owned too");
        check(!RigOwners.of(model, form).locksRotation("other"), "Unrelated FK rotations remain editable");
        check(RigOwners.of(model, form).rotationDriver("bone3") == RigStep.SPLINE, "The spline is named as the driver");
        for (int i = 0; i < 8; i++) close(solved.get(i), originals.get(i), "Authored straight curve preserves original pose");
        check(other.orient == null && other.offset == null, "Side branch remains untouched");

        spline.chainLength.set(2);
        check(splineLocks(model, form, "bone6"), "Length includes the tip and its parent");
        check(!splineLocks(model, form, "bone5"), "Length excludes ancestors beyond the count");
        spline.chainLength.set(0);
        check(splineLocks(model, form, "All"), "Zero length reaches the hierarchy root");
        spline.chainLength.set(100);
        check(splineLocks(model, form, "All"), "Length beyond ancestry stops at the root");
        spline.chainLength.set(1);
        check(!splineLocks(model, form, "bone7"), "A single bone cannot form a spline segment");
        spline.tip.set("missing");
        check(!splineLocks(model, form, "bone0"), "Missing tip does not constrain bones");
        spline.tip.set("bone7");
        spline.chainLength.set(8);

        spline.progress.set(25F);
        model.restoreChannels();
        ModelSplineRuntime.apply(instance, form);
        List<Vector3f> advanced = positions(model);
        Vector3f advance = new Vector3f(controls.get(3)).sub(controls.get(0)).mul(0.25F);
        for (int i = 0; i < 8; i++) close(advanced.get(i), new Vector3f(originals.get(i)).add(advance), "Percentage advances the actual cubic chain, including beyond the curve");
        for (int i = 0; i < controls.size(); i++) close(spline.points.getAllTyped().get(i).position.get().translate, controls.get(i), "Progress never moves authored controls");
        spline.progress.set(0F);

        spline.points.getAllTyped().get(1).position.get().translate.x += 1F;
        spline.points.getAllTyped().get(2).position.get().translate.y -= 0.5F;
        model.restoreChannels();
        ModelSplineRuntime.apply(instance, form);
        List<Vector3f> curved = positions(model);
        for (int i = 1; i < 8; i++) check(Math.abs(curved.get(i).distance(curved.get(i - 1)) - 7.02F / 16F) < 1E-4F, "Rendered chord length preserved");
        check(curved.get(3).distance(originals.get(3)) > 0.05F, "Control point bends the actual cubic skeleton");
        for (int i = 0; i < 8; i++)
        {
            ModelGroup bone = model.getGroup("bone" + i);
            close(bone.current.translate, bone.initial.translate, "FK translation never changes");
            close(bone.current.rotate, bone.initial.rotate, "FK rotation never changes");
        }

        model.restoreChannels();
        ModelSplineRuntime.apply(instance, form);
        compare(positions(model), curved, "Repeated render/matrix passes are deterministic");

        spline.progress.set(100F);
        model.restoreChannels();
        ModelSplineRuntime.apply(instance, form);
        close(positions(model).get(0), spline.points.getAllTyped().get(3).position.get().translate, "One hundred percent places the first bone at the last control");
        spline.progress.set(0F);
        model.restoreChannels();
        ModelSplineRuntime.apply(instance, form);
        compare(positions(model), curved, "Scrubbing progress back to zero restores the authored bend");

        ModelForm secondForm = new ModelForm();
        SplineIK secondSpline = new SplineIK("");
        secondSpline.chainLength.set(8);
        secondSpline.tip.set("bone7");
        secondForm.splines.add(secondSpline);
        addPoint(secondSpline, new Vector3f(5, 0, 0));
        addPoint(secondSpline, new Vector3f(5, 0, 4));
        model.resetPose();
        ModelSplineRuntime.apply(instance, secondForm);
        check(Math.abs(positions(model).get(0).x - 5F) < 1E-4F, "Second actor uses its own points");
        model.resetPose();
        ModelSplineRuntime.apply(instance, form);
        compare(positions(model), curved, "Another actor sharing ModelInstance cannot contaminate the result");

        model.restoreChannels();
        spline.influence.set(0F);
        check(!RigOwners.of(model, form).locksRotation("bone0"), "Zero influence releases FK rotation gestures");
        ModelSplineRuntime.apply(instance, form);
        compare(positions(model), originals, "Zero influence returns exact FK");
        check(model.getGroup("bone0").orient == null, "Zero influence writes no orientation");
        spline.influence.set(0.5F);
        ModelSplineRuntime.apply(instance, form);
        List<Vector3f> blended = positions(model);
        for (int i = 0; i < 8; i++) close(blended.get(i), new Vector3f(originals.get(i)).lerp(curved.get(i), 0.5F), "Half influence blends from FK");
        model.restoreChannels();
        spline.influence.set(1F);
        form.splines.getAllTyped().remove(spline);
        ModelSplineRuntime.apply(instance, form);
        compare(positions(model), originals, "Deleting the spline returns exact FK");
        form.splines.add(spline);

        model.restoreChannels();
        ModelSplineRuntime.apply(instance, form);
        Quaternionf tipBefore = model.getGroup("bone7").evaluatedRotation();
        model.restoreChannels();
        spline.twist.set(120F);
        ModelSplineRuntime.apply(instance, form);
        compare(positions(model), curved, "Twist preserves solved joint positions");
        check(Math.abs(tipBefore.dot(model.getGroup("bone7").evaluatedRotation())) < 0.999F, "Twist rotates terminal geometry");
        spline.twist.set(0F);

        model.restoreChannels();
        spline.fit.set(true);
        ModelSplineRuntime.apply(instance, form);
        close(positions(model).get(7), spline.points.getAllTyped().get(3).position.get().translate, "Fit reaches last control");
        spline.fit.set(false);

        model.restoreChannels();
        form.bones.getOrCreate("bone1").physicsEnd.set("bone5");
        ModelSplineRuntime.apply(instance, form);
        compare(positions(model), originals, "Physics conflict rejects only the spline");
        form.bones.getOrCreate("bone1").physicsEnd.set("");
        form.bones.getOrCreate("bone7").ikTarget.set("other");
        form.bones.getOrCreate("bone7").ikChainLength.set(8);
        RenderFrame.invalidate();
        ModelSplineRuntime.apply(instance, form);
        compare(positions(model), originals, "IK conflict rejects the spline");
        form.bones.getOrCreate("bone7").ikTarget.set("");
        RenderFrame.invalidate();

        all.current.rotate.set(23, 51, -14);
        all.current.scale.set(-2F, 0.7F, 1.3F);
        ModelSplineRuntime.apply(instance, form);
        var parentFrame = new org.joml.Matrix4f().rotate(all.evaluatedRotation()).scale(all.current.scale);
        List<Vector3f> parented = positions(model);
        for (int i = 0; i < 8; i++) close(parented.get(i), parentFrame.transformPosition(new Vector3f(curved.get(i))), "Parent affine transform carries curve and skeleton together");
        model.resetPose();
        model.getGroup("bone3").current.scale.set(1F, 2F, 1F);
        ModelSplineRuntime.apply(instance, form);
        check(model.getGroup("bone0").orient == null, "Unsupported nonuniform chain scale rejects atomically");

        /* Regression: captureMatrices exports attachment frames, not the raw cubic parent
         * frame. Compare the editor's recovered frame against actual rendered joints, with
         * a nonzero parent pivot and rotation so neither an extra half-turn nor a forgotten
         * pivot translation can hide behind an identity parent. */
        model.resetPose();
        all.initial.translate.set(8F, -4F, 5F);
        all.reset();
        all.current.rotate.set(25F, 60F, -15F);
        all.current.scale.set(1.2F, 0.8F, 1.4F);
        spline.fit.set(true);
        ModelSplineRuntime.apply(instance, form);

        MatrixCache attachments = new MatrixCache();
        instance.captureMatrices(attachments);
        Matrix4f formFrame = new Matrix4f().translation(2F, 1F, -3F).rotateY(0.4F);
        Matrix4f renderBase = new Matrix4f(formFrame).rotateY((float) Math.PI);
        Matrix4f parentAttachment = new Matrix4f(renderBase).mul(attachments.get("All").matrix());
        Matrix4f editorParent = SplineEditorUtils.parentFrame(parentAttachment, all);
        Map<String, CubicRenderer.PivotFrame> actualFrames = new HashMap<>();
        CubicRenderer.collectPivotFrames(model, new HashSet<>(model.getAllGroupKeys()), actualFrames, renderBase, true);
        close(editorParent.transformPosition(new Vector3f(spline.points.getAllTyped().get(0).position.get().translate)),
            actualFrames.get("bone0").position(), "First spline handle matches rendered root through captured attachment frame");
        close(editorParent.transformPosition(new Vector3f(spline.points.getAllTyped().get(3).position.get().translate)),
            actualFrames.get("bone7").position(), "Last spline handle matches rendered tip through captured attachment frame");
        close(SplineEditorUtils.parentFrame(formFrame, null).transformPosition(new Vector3f(all.initial.translate).mul(1F / 16F)),
            actualFrames.get("All").position(), "Top-level root uses form frame and the model half-turn exactly once");
        checkModelMotion(model, instance, form, spline, all, jaw, controls);
        System.out.println("SplineRuntimeCheck: " + checks + " checks passed");
    }

    private static void checkModelMotion(Model model, ModelInstance instance, ModelForm form, SplineIK spline,
        ModelGroup all, ModelGroup jaw, List<Vector3f> straightPoints)
    {
        List<Vector3f> curvedPoints = spline.points.getAllTyped().stream()
            .map(point -> new Vector3f(point.position.get().translate)).toList();
        model.resetPose();
        spline.fit.set(false);
        spline.moveModel.set(true);
        for (int i = 0; i < straightPoints.size(); i++) spline.points.getAllTyped().get(i).position.get().translate.set(straightPoints.get(i));
        ModelSplineRuntime.Motion rest = ModelSplineRuntime.apply(instance, form);
        check(rest != null && rest.matrix().equals(new Matrix4f(), 2E-4F), "Whole-model rest curve has identity motion at zero progress");
        for (int i = 0; i < curvedPoints.size(); i++) spline.points.getAllTyped().get(i).position.get().translate.set(curvedPoints.get(i));

        Vector3f[] scales = {new Vector3f(1F), new Vector3f(1.2F, 0.8F, 1.4F), new Vector3f(-2F, 0.7F, 1.3F)};
        for (int scenario = 0; scenario < scales.length; scenario++)
        {
            model.resetPose();
            all.current.rotate.set(25F, 60F, -15F);
            all.current.scale.set(scales[scenario]);
            jaw.current.rotate.set(-35F, 10F, 0F);
            ModelGroup root = model.getGroup("bone0");
            root.current.translate.add(2F, -1F, 3F);
            root.orient = new Quaternionf().rotationXYZ(0.1F, 0.2F, -0.1F);
            root.offset = new Vector3f(0.04F, -0.03F, 0.02F);
            Quaternionf originalOrientation = new Quaternionf(root.orient);
            Vector3f originalOffset = new Vector3f(root.offset);
            spline.progress.set(35F);
            spline.influence.set(scenario == 0 ? 0.5F : 1F);
            spline.moveModel.set(false);
            model.snapshotChannels();
            Map<String, Matrix4f> fk = renderedMatrices(model, null);
            check(ModelSplineRuntime.apply(instance, form) == null, "Chain-only mode returns no model prefix");
            Map<String, Matrix4f> chainOnly = renderedMatrices(model, null);
            Matrix4f expectedMotion = new Matrix4f(chainOnly.get("bone0")).mul(new Matrix4f(fk.get("bone0")).invert());

            model.restoreChannels();
            spline.moveModel.set(true);
            ModelSplineRuntime.Motion motion = ModelSplineRuntime.apply(instance, form);
            check(motion != null && motion.chainId().equals(spline.getId()), "Accepted spline identifies its model-motion owner");
            check(motion.matrix().equals(expectedMotion, 2E-4F), "Motion matches the root delta measured by the real cubic renderer");
            check(motion.parentFrame().equals(fk.get("All"), 2E-4F), "Driver controls retain the unmoved parent frame");
            check(root.orient.equals(originalOrientation, 1E-6F), "Motion extraction restores the root channel orientation");
            close(root.offset, originalOffset, "Motion extraction restores the root channel offset");
            Map<String, Matrix4f> moved = renderedMatrices(model, motion.matrix());
            for (int i = 0; i < 8; i++)
            {
                String id = "bone" + i;
                check(moved.get(id).equals(chainOnly.get(id), 3E-4F), "Whole-model mode preserves the solved tail geometry and normal frame");
                Vector3f pivot = new Vector3f(model.getGroup(id).initial.translate).mul(1F / 16F);
                close(moved.get(id).transformPosition(new Vector3f(pivot)), chainOnly.get(id).transformPosition(pivot), "Whole-model mode preserves the actual rendered tail pivot");
            }
            for (String id : List.of("other", "jaw"))
            {
                check(moved.get(id).equals(new Matrix4f(expectedMotion).mul(fk.get(id)), 3E-4F), "Separate head and animated jaw inherit the root motion exactly once");
            }
            check(!moved.get("other").equals(fk.get("other"), 0.01F), "Head in a separate branch actually follows the moving tail");
            close(jaw.current.rotate, new Vector3f(-35F, 10F, 0F), "Local jaw animation remains unchanged");

            model.restoreChannels();
            ModelSplineRuntime.Motion repeated = ModelSplineRuntime.apply(instance, form);
            check(repeated != null && repeated.matrix().equals(motion.matrix(), 2E-4F), "Repeated render passes do not accumulate model motion");
            model.restoreChannels();
            check(ModelSplineRuntime.apply(instance, new ModelForm()) == null, "Another actor sharing this model does not inherit its motion");
            check(renderedMatrices(model, null).get("other").equals(fk.get("other"), 2E-4F), "Shared-model actor keeps its original head pose");

            form.splines.getAllTyped().remove(spline);
            check(ModelSplineRuntime.apply(instance, form) == null, "Deleted model motion is neutral");
            form.splines.add(spline);
            spline.influence.set(0F);
            check(ModelSplineRuntime.apply(instance, form) == null, "Zero influence produces no model motion");
        }

        model.resetPose();
        ModelForm topForm = new ModelForm();
        SplineIK top = new SplineIK("");
        top.chainLength.set(0);
        top.tip.set("bone7");
        top.progress.set(20F);
        topForm.splines.add(top);
        addPoint(top, new Vector3f());
        addPoint(top, new Vector3f(4F, 6F, 0F).div(16F));
        addPoint(top, new Vector3f(4F, 6F, 49.14F).div(16F));
        model.snapshotChannels();
        ModelSplineRuntime.apply(instance, topForm);
        Map<String, Matrix4f> topChainOnly = renderedMatrices(model, null);
        model.restoreChannels();
        top.moveModel.set(true);
        ModelSplineRuntime.Motion topMotion = ModelSplineRuntime.apply(instance, topForm);
        check(topMotion != null && topMotion.parentFrame().equals(new Matrix4f(), 1E-6F), "Top-level driver has an identity parent frame");
        Map<String, Matrix4f> topMoved = renderedMatrices(model, topMotion.matrix());
        for (String id : model.getAllGroupKeys()) check(topMoved.get(id).equals(topChainOnly.get(id), 3E-4F), "Top-level extraction preserves the entire solved model");
    }

    private static boolean splineLocks(Model model, ModelForm form, String bone)
    {
        return RigOwners.of(model, form, java.util.EnumSet.of(RigStep.SPLINE)).locksRotation(bone);
    }

    /** Use the real render traversal: collectPivotFrames intentionally strips base scale/shear. */
    private static Map<String, Matrix4f> renderedMatrices(Model model, Matrix4f prefix)
    {
        MatrixStack stack = new MatrixStack();
        if (prefix != null) stack.peek().getPositionMatrix().set(prefix);
        CubicMatrixRenderer renderer = new CubicMatrixRenderer(model);
        CubicRenderer.processRenderModel(renderer, null, stack, model);
        Map<String, Matrix4f> result = new HashMap<>();
        for (ModelGroup group : model.getAllGroups()) result.put(group.id, new Matrix4f(renderer.matrices.get(group.index)));
        return result;
    }

    private static void addPoint(SplineIK spline, Vector3f point)
    {
        SplinePoint value = new SplinePoint("");
        value.position.get().translate.set(point);
        spline.points.add(value);
    }

    private static List<Vector3f> positions(Model model)
    {
        Map<String, CubicRenderer.PivotFrame> frames = new HashMap<>();
        CubicRenderer.collectPivotFrames(model, new HashSet<>(model.getAllGroupKeys()), frames, null, true);
        List<Vector3f> result = new ArrayList<>();
        for (int i = 0; i < 8; i++) result.add(frames.get("bone" + i).position());
        return result;
    }

    private static void compare(List<Vector3f> actual, List<Vector3f> expected, String message)
    {
        for (int i = 0; i < actual.size(); i++) close(actual.get(i), expected.get(i), message);
    }

    private static void close(Vector3f actual, Vector3f expected, String message)
    {
        check(actual.distance(expected) < 2E-4F, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message)
    {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
