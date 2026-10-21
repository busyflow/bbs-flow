package mchorse.bbs_mod.ui.forms.editors.panels.rig;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.physics.BonePhysicsIO;
import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.WindControl;
import mchorse.bbs_mod.cubic.rig.RigClaim;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIRigFormPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIPhysicsControlFields;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The physics chain the selected bone hangs in — as its root, a link, or what its end is attached
 * to — edited wherever it is picked up. The chain lives on its root bone, as it always has: the end
 * is how far down it swings. Switched on, a bone gets a chain from it down to the end of its line
 * of first children. The wind blows on every chain of the model, so it sits here once for all.
 */
public class UIRigPhysicsSection extends UIRigSection
{
    public final UIBonePicker start;
    public final UIBonePicker end;
    public final UIBonePicker attach;
    public final UIPhysicsControlFields controls;
    public final UIToggle relativeGravity;
    public final UISliderTrackpad gravityRotateX;
    public final UISliderTrackpad gravityRotateY;
    public final UISliderTrackpad gravityRotateZ;
    public final UITrackpad iterations;
    public final UIToggle collisions;
    public final UISliderTrackpad radius;

    public final UISliderTrackpad windStrength;
    public final UITrackpad windX;
    public final UITrackpad windY;
    public final UITrackpad windZ;
    public final UISliderTrackpad windTurbulence;
    public final UISliderTrackpad windTurbulenceSpeed;
    public final UISliderTrackpad windTurbulenceScale;
    public final UIToggle windLocal;

    /** The root the shown chain lives on, empty while there is none. */
    private String root = "";

    public UIRigPhysicsSection(UIRigFormPanel panel)
    {
        super(panel, RigStep.PHYSICS, true);

        IKey axis = IKey.constant("%s (%s)");

        this.start = new UIBonePicker(this::setStart);
        this.start.menu((picker) -> this.fillMenu(picker, this.root, this.startCandidates()));
        this.start.viewport(panel.viewportPicking());

        this.end = new UIBonePicker(this::setEnd);
        this.end.menu((picker) -> this.fillMenu(picker, this.read().physicsEnd.get(), this.endCandidates()));
        this.end.viewport(panel.viewportPicking());

        this.attach = new UIBonePicker((bone) ->
        {
            this.editRoot((formBone) -> formBone.physicsTargetBone.set(bone));
            this.panel.refresh();
        });
        this.attach.menu((picker) ->
        {
            if (this.panel.getModelInstance() != null && this.panel.getModel() != null && !this.root.isEmpty())
            {
                picker.bones(this.panel.getModel(), this.panel.getModelInstance().getDisabledBones()).none().set(this.read().physicsTargetBone.get());
            }
        });
        this.attach.viewport(panel.viewportPicking());

        this.controls = new UIPhysicsControlFields(this::editControl);

        this.relativeGravity = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY, (b) -> this.editRoot((bone) -> bone.physicsRelativeGravity.set(b.getValue())));
        this.gravityRotateX = this.angle((bone, v) -> bone.physicsGravityRotateX.set(v), Colors.RED, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_X));
        this.gravityRotateY = this.angle((bone, v) -> bone.physicsGravityRotateY.set(v), Colors.GREEN, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_Y));
        this.gravityRotateZ = this.angle((bone, v) -> bone.physicsGravityRotateZ.set(v), Colors.BLUE, axis.format(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION, UIKeys.GENERAL_Z));

        this.iterations = new UITrackpad((v) -> this.editRoot((bone) -> bone.physicsIterations.set(v.intValue())));
        this.iterations.onlyNumbers().integer().values(1D).increment(1D).limit(1D, 20D, true);
        this.iterations.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ITERATIONS);

        this.collisions = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_COLLISIONS, (b) -> this.editRoot((bone) -> bone.physicsCollisions.set(b.getValue())));
        this.radius = new UISliderTrackpad((v) -> this.editRoot((bone) -> bone.physicsRadius.set(v.floatValue())));
        this.radius.normalized();
        this.radius.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RADIUS);

        this.windStrength = new UISliderTrackpad((v) -> this.editWind((w) -> w.strength = v.floatValue()));
        this.windStrength.onlyNumbers().values(0.1D, 0.01D, 0.5D).increment(0.25D).limit(0D, 10D);
        this.windStrength.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_STRENGTH);
        this.windX = windAxis((v) -> this.editWind((w) -> w.x = v.floatValue()), Colors.RED);
        this.windY = windAxis((v) -> this.editWind((w) -> w.y = v.floatValue()), Colors.GREEN);
        this.windZ = windAxis((v) -> this.editWind((w) -> w.z = v.floatValue()), Colors.BLUE);
        this.windTurbulence = new UISliderTrackpad((v) -> this.editWind((w) -> w.turbulence = v.floatValue()));
        this.windTurbulence.normalized();
        this.windTurbulence.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE);
        this.windTurbulenceSpeed = new UISliderTrackpad((v) -> this.editWind((w) -> w.turbulenceSpeed = v.floatValue()));
        this.windTurbulenceSpeed.onlyNumbers().values(0.1D, 0.05D, 0.5D).increment(0.1D).limit(0D, 10D);
        this.windTurbulenceSpeed.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SPEED);
        this.windTurbulenceScale = new UISliderTrackpad((v) -> this.editWind((w) -> w.turbulenceScale = v.floatValue()));
        this.windTurbulenceScale.onlyNumbers().values(0.1D, 0.05D, 0.5D).increment(0.1D).limit(0D, 10D);
        this.windTurbulenceScale.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SCALE);
        this.windLocal = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_LOCAL, (b) -> this.editWind((w) -> w.local = b.getValue()));
        this.windLocal.tooltip(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_LOCAL_TOOLTIP);

        this.page(UIKeys.FORMS_EDITORS_MODEL_RIG_MAIN,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_RIG_START, this.start),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_RIG_END, this.end),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_RIG_ATTACH, this.attach),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT, this.controls.weight),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_GRAVITY, this.controls.gravity),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_STIFFNESS, this.controls.stiffness),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_DAMPING, this.controls.damping),
            this.relativeGravity,
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RELATIVE_GRAVITY_ROTATION),
            UI.row(this.gravityRotateX, this.gravityRotateY, this.gravityRotateZ),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_ITERATIONS, this.iterations)
        );
        this.page(UIKeys.FORMS_EDITORS_MODEL_RIG_COLLISIONS, this.collisions, UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_RADIUS, this.radius));
        this.page(UIKeys.FORMS_EDITORS_MODEL_RIG_WIND,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_STRENGTH, this.windStrength),
            UI.label(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_DIRECTION),
            UI.row(this.windX, this.windY, this.windZ),
            this.windLocal,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE, this.windTurbulence),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SPEED, this.windTurbulenceSpeed),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_PHYSICS_WIND_TURBULENCE_SCALE, this.windTurbulenceScale)
        );
    }

    private UISliderTrackpad angle(java.util.function.BiConsumer<FormBone, Float> setter, int color, IKey tooltip)
    {
        UISliderTrackpad trackpad = new UISliderTrackpad((v) -> this.editRoot((bone) -> setter.accept(bone, v.floatValue()))).angle180();

        trackpad.textbox.setColor(color);
        trackpad.tooltip(tooltip);

        return trackpad;
    }

    private static UITrackpad windAxis(Consumer<Double> callback, int color)
    {
        UITrackpad trackpad = new UITrackpad(callback).onlyNumbers().values(0.1D, 0.5D, 1D).increment(0.1D);

        trackpad.textbox.setColor(color);

        return trackpad;
    }

    @Override
    protected boolean fill(String bone)
    {
        RigClaim claim = this.panel.chainOf(RigStep.PHYSICS, bone);

        this.root = claim == null ? "" : claim.owner();

        FormBone formBone = this.panel.getFormBone(this.root);

        if (formBone == null)
        {
            this.root = "";

            return false;
        }

        ModelForm form = this.panel.getForm();
        PhysicsControl control = form.physics.getOriginalValue().get(this.root);
        WindControl wind = form.wind.get();
        String end = formBone.physicsEnd.get();

        this.setInfo(this.root + " → " + end);
        this.start.setLabel(IKey.raw(this.root));
        this.end.setLabel(IKey.raw(end));
        this.attach.setLabel(IKey.raw(bone(formBone.physicsTargetBone.get())));
        this.controls.weight.setValue(control.weight);
        this.controls.gravity.setValue(control.gravity);
        this.controls.stiffness.setValue(control.stiffness);
        this.controls.damping.setValue(control.damping);
        this.relativeGravity.setValue(formBone.physicsRelativeGravity.get());
        this.gravityRotateX.setValue(formBone.physicsGravityRotateX.get());
        this.gravityRotateY.setValue(formBone.physicsGravityRotateY.get());
        this.gravityRotateZ.setValue(formBone.physicsGravityRotateZ.get());
        this.iterations.setValue(formBone.physicsIterations.get());
        this.collisions.setValue(formBone.physicsCollisions.get());
        this.radius.setValue(formBone.physicsRadius.get());
        this.windStrength.setValue(wind.strength);
        this.windX.setValue(wind.x);
        this.windY.setValue(wind.y);
        this.windZ.setValue(wind.z);
        this.windTurbulence.setValue(wind.turbulence);
        this.windTurbulenceSpeed.setValue(wind.turbulenceSpeed);
        this.windTurbulenceScale.setValue(wind.turbulenceScale);
        this.windLocal.setValue(wind.local);

        return true;
    }

    /** A new chain swings from the bone down its line of first children, to the end of it. */
    @Override
    public void switched(String bone, boolean on)
    {
        IModel model = this.panel.getModel();

        if (on && model != null)
        {
            String end = bone;

            for (Collection<String> children = model.getDirectChildrenKeys(end); !children.isEmpty(); children = model.getDirectChildrenKeys(end))
            {
                end = children.iterator().next();
            }

            String last = end;

            this.panel.edit((form) ->
            {
                FormBone formBone = form.bones.getOrCreate(bone);

                BonePhysicsIO.clearChain(formBone);
                formBone.physicsEnd.set(last);
            });
        }
        else if (!on && !this.root.isEmpty())
        {
            String root = this.root;

            this.panel.edit((form) -> BonePhysicsIO.clearChain(form.bones.getOrCreate(root)));
        }
    }

    @Override
    public void setFieldsEnabled(boolean enabled)
    {
        super.setFieldsEnabled(enabled);

        for (UIElement element : List.of(this.start, this.end, this.attach, this.controls.weight, this.controls.gravity,
            this.controls.stiffness, this.controls.damping, this.relativeGravity, this.gravityRotateX, this.gravityRotateY,
            this.gravityRotateZ, this.iterations, this.collisions, this.radius, this.windStrength, this.windX, this.windY,
            this.windZ, this.windTurbulence, this.windTurbulenceSpeed, this.windTurbulenceScale, this.windLocal))
        {
            element.setEnabled(enabled);
        }
    }

    /* The chain's ends */

    /** Where the chain may start: its end or a bone above it, none with a chain of its own. */
    private List<String> startCandidates()
    {
        IModel model = this.panel.getModel();

        if (model == null || this.root.isEmpty())
        {
            return List.of();
        }

        List<String> candidates = new ArrayList<>(model.getHierarchyGroups(this.read().physicsEnd.get()));

        candidates.removeIf((bone) ->
        {
            FormBone formBone = this.panel.getFormBone(bone);

            return !bone.equals(this.root) && formBone != null && formBone.hasPhysicsChain();
        });

        return candidates;
    }

    /** A new start moves the chain over to it — the chain lives on its root — keeping its end. */
    private void setStart(String bone)
    {
        if (bone == null || bone.equals(this.root) || !this.startCandidates().contains(bone))
        {
            return;
        }

        String from = this.root;

        this.panel.edit((form) ->
        {
            FormBone old = form.bones.getOrCreate(from);
            FormBone moved = form.bones.getOrCreate(bone);
            PhysicsControl control = form.physics.getOriginalValue().get(from).copy();

            moved.physicsEnd.set(old.physicsEnd.get());
            moved.physicsTargetBone.set(old.physicsTargetBone.get());
            moved.physicsIterations.set(old.physicsIterations.get());
            moved.physicsCollisions.set(old.physicsCollisions.get());
            moved.physicsRadius.set(old.physicsRadius.get());
            moved.physicsRelativeGravity.set(old.physicsRelativeGravity.get());
            moved.physicsGravityRotateX.set(old.physicsGravityRotateX.get());
            moved.physicsGravityRotateY.set(old.physicsGravityRotateY.get());
            moved.physicsGravityRotateZ.set(old.physicsGravityRotateZ.get());
            BonePhysicsIO.clearChain(old);
            form.physics.getOriginalValue().controls.put(bone, control);
        });
        this.panel.selectBoneInTree(bone);
    }

    /** Where the chain may end: the root or any bone under it. */
    private List<String> endCandidates()
    {
        IModel model = this.panel.getModel();

        if (model == null || this.root.isEmpty())
        {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();

        candidates.add(this.root);
        candidates.addAll(model.getAllChildrenKeys(this.root));

        return candidates;
    }

    private void setEnd(String bone)
    {
        if (bone != null && this.endCandidates().contains(bone))
        {
            this.editRoot((formBone) -> formBone.physicsEnd.set(bone));
            this.panel.refresh();
        }
    }

    /* Value access */

    private FormBone read()
    {
        FormBone formBone = this.panel.getFormBone(this.root);

        return formBone == null ? new FormBone("") : formBone;
    }

    /** Edits the root's bone values; what changes the chain itself refreshes the tab after. */
    private void editRoot(Consumer<FormBone> edit)
    {
        if (!this.root.isEmpty())
        {
            this.panel.editFormBone(this.root, edit);
        }
    }

    /** Edits the chain's physics scalars as one value change (one undo entry). */
    private void editControl(Consumer<PhysicsControl> edit)
    {
        ModelForm form = this.panel.getForm();
        String root = this.root;

        if (form == null || root.isEmpty())
        {
            return;
        }

        PhysicsControl control = form.physics.getOriginalValue().get(root).copy();

        edit.accept(control);
        BaseValue.edit(form.physics, (value) -> value.getOriginalValue().controls.put(root, control));
    }

    /** Edits the form's wind as one value change (one undo entry). */
    private void editWind(Consumer<WindControl> edit)
    {
        ModelForm form = this.panel.getForm();

        if (form == null)
        {
            return;
        }

        WindControl wind = form.wind.get().copy();

        edit.accept(wind);
        form.wind.set(wind);
    }

    private void fillMenu(UIBonePickerContextMenu picker, String current, List<String> candidates)
    {
        IModel model = this.panel.getModel();

        if (model == null || this.root.isEmpty())
        {
            return;
        }

        Set<String> hidden = new HashSet<>(model.getAllGroupKeys());

        candidates.forEach(hidden::remove);
        picker.bones(model, hidden).set(current);
    }

    private static String bone(String bone)
    {
        return bone == null || bone.isEmpty() ? "-" : bone;
    }
}
