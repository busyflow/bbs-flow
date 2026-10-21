package mchorse.bbs_mod.ui.forms.editors.panels.rig;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.rig.RigClaim;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.cubic.spline.ModelSplineRuntime;
import mchorse.bbs_mod.cubic.spline.SplineIK;
import mchorse.bbs_mod.cubic.spline.SplinePoint;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIRigFormPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIDeltaPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.utils.SplineEditorUtils;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UISplineControlFields;
import mchorse.bbs_mod.ui.utils.UISplinePointsEditor;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The spline IK the selected bone is in: its chain, the curve's points and how the chain rides
 * them. A spline lives in the form's list of splines, as it always has, ending at its tip bone: the
 * start is how far up it reaches. Switched on, a bone gets a spline ending at it and starting two
 * bones up, with its first point where the chain begins. The points are moved with the gizmo.
 */
public class UIRigSplineSection extends UIRigSection
{
    /** How many bones a new spline takes, its tip included. */
    private static final int NEW_CHAIN = 3;

    public final UIBonePicker start;
    public final UIBonePicker end;
    public final UISplineControlFields controls;
    public final UISplinePointsEditor pointEditor;
    public final UIPropTransform position;
    public final UIToggle moveModel;
    public final UIToggle fit;

    private String chainId = "";

    public static IKey key(String name)
    {
        return L10n.lang("bbs.ui.forms.editors.model.spline." + name);
    }

    public UIRigSplineSection(UIRigFormPanel panel)
    {
        super(panel, RigStep.SPLINE, true);

        this.start = new UIBonePicker(this::setStart);
        this.start.menu((picker) -> this.fillMenu(picker, this.root(), this.ancestors(this.tip())));
        this.start.viewport(panel.viewportPicking());

        this.end = new UIBonePicker(this::setEnd);
        this.end.menu((picker) -> this.fillMenu(picker, this.tip(), this.endCandidates()));
        this.end.viewport(panel.viewportPicking());

        this.controls = new UISplineControlFields((edit) ->
        {
            ModelForm form = this.panel.getForm();

            if (form != null && this.chain() != null)
            {
                BaseValue.edit(form.splineIK, (value) -> edit.accept(this.chain().original()));
            }
        });

        this.moveModel = new UIToggle(key("move_model"), (b) -> { if (this.chain() != null) this.chain().moveModel.set(b.getValue()); });
        this.moveModel.tooltip(key("move_model_tooltip"));
        this.fit = new UIToggle(key("fit"), (b) -> { if (this.chain() != null) this.chain().fit.set(b.getValue()); });
        this.fit.tooltip(key("fit_tooltip"));

        this.position = new UIDeltaPropTransform()
        {
            @Override
            protected void applyToSelection(Consumer<Transform> consumer)
            {
                SplineIK chain = UIRigSplineSection.this.chain();

                if (chain == null)
                {
                    return;
                }

                for (String id : UIRigSplineSection.this.pointEditor.selected())
                {
                    SplinePoint point = chain.points.get(id);

                    if (point != null)
                    {
                        consumer.accept(point.position.getOriginalValue());
                    }
                }
            }
        };
        this.position.noScale().setRotationVisible(false);
        this.position.callbacks(
            () -> { if (this.point() != null) this.panel.getForm().splineIK.preNotify(); },
            () -> { if (this.point() != null) this.panel.getForm().splineIK.postNotify(); },
            () -> { if (this.point() != null) this.panel.getForm().splineIK.preNotify(IValueListener.FLAG_UNMERGEABLE); }
        );
        this.position.enableHotkeys(() -> this.editor().view == panel && this.getGizmoTransform() != null, (op) -> op == TransformOp.TRANSLATE);
        this.position.hotkeyDrag(() -> this.editor().editor == null ? null : this.editor().editor.buildHotkeyDrag(this.position));

        this.pointEditor = new UISplinePointsEditor(this::chain,
            (id) -> this.chain().points.get(id).position.getOriginalValue(), this.position,
            () -> this.initialPointPosition(this.chain()));

        this.page(UIKeys.FORMS_EDITORS_MODEL_RIG_MAIN,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_RIG_START, this.start),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_RIG_END, this.end),
            UI.labelRow(key("influence"), this.controls.influence),
            UI.labelRow(key("progress"), this.controls.progress),
            this.pointEditor
        );
        this.page(key("advanced"), this.moveModel, UI.labelRow(key("twist"), this.controls.twist), this.fit);
    }

    private UIForm editor()
    {
        return this.panel.getEditor();
    }

    @Override
    protected boolean fill(String bone)
    {
        RigClaim claim = this.panel.chainOf(RigStep.SPLINE, bone);
        String id = claim == null ? "" : claim.owner();

        if (!id.equals(this.chainId))
        {
            this.endPointEdit();
            this.chainId = id;
            this.pointEditor.select("");
        }

        SplineIK chain = this.chain();

        if (chain == null)
        {
            this.chainId = "";
            this.pointEditor.refresh();

            return false;
        }

        this.setInfo(this.root() + " → " + this.tip());
        this.start.setLabel(IKey.raw(this.root()));
        this.end.setLabel(IKey.raw(this.tip()));
        this.controls.influence.setValue(chain.influence.get());
        this.controls.progress.setValue(chain.progress.get());
        this.controls.twist.setValue(chain.twist.get());
        this.moveModel.setValue(chain.moveModel.get());
        this.fit.setValue(chain.fit.get());
        this.pointEditor.refresh();

        return true;
    }

    @Override
    public void switched(String bone, boolean on)
    {
        this.endPointEdit();

        if (on)
        {
            this.addChain(bone);
        }
        else if (this.chain() != null)
        {
            SplineIK chain = this.chain();

            this.panel.edit((form) ->
            {
                form.splines.getAllTyped().remove(chain);
                form.splineIK.getOriginalValue().controls.remove(chain.getId());
            });
            this.chainId = "";
            this.pointEditor.select("");
        }
    }

    @Override
    public void setFieldsEnabled(boolean enabled)
    {
        super.setFieldsEnabled(enabled);

        for (UIElement element : List.of(this.start, this.end, this.controls.influence, this.controls.progress, this.controls.twist, this.moveModel, this.fit))
        {
            element.setEnabled(enabled);
        }
    }

    /** A new spline ending at the bone, starting two bones up, with its first point where the chain begins. */
    private void addChain(String bone)
    {
        ModelForm form = this.panel.getForm();

        if (form == null || !this.panel.getAvailableBones().contains(bone))
        {
            return;
        }

        SplineIK chain = new SplineIK("");

        chain.name.set(key("title").get() + " " + (form.splines.getAllTyped().size() + 1));
        chain.tip.set(bone);
        chain.chainLength.set(Math.min(NEW_CHAIN, this.ancestors(bone).size()));

        Vector3f origin = this.initialPointPosition(chain);

        if (origin == null)
        {
            return;
        }

        SplinePoint point = new SplinePoint("");

        point.position.getOriginalValue().translate.set(origin);
        chain.points.add(point);
        BaseValue.edit(form.splines, (list) -> list.add(chain));
        this.select(chain.getId(), point.getId());
    }

    private Vector3f initialPointPosition(SplineIK chain)
    {
        UIForm editor = this.editor();
        UIContext context = this.getContext();

        if (editor.editor == null)
        {
            return null;
        }

        return SplineEditorUtils.rootPosition(FormUtils.getRoot(this.panel.getForm()), editor.editor.renderer.getTargetEntity(),
            context == null ? 0F : context.getTransition(), this.panel.getForm(), chain);
    }

    /* The spline's ends */

    private String tip()
    {
        SplineIK chain = this.chain();

        return chain == null ? "" : chain.tip.get();
    }

    private String root()
    {
        SplineIK chain = this.chain();

        return chain == null ? "" : ModelSplineRuntime.getRoot(this.panel.getForm(), chain);
    }

    private List<String> ancestors(String bone)
    {
        IModel model = this.panel.getModel();

        return model == null || bone.isEmpty() ? List.of() : new ArrayList<>(model.getHierarchyGroups(bone));
    }

    /** The start sets how far up the spline reaches from its tip. */
    private void setStart(String bone)
    {
        SplineIK chain = this.chain();
        int index = this.ancestors(this.tip()).indexOf(bone);

        if (chain != null && index >= 0)
        {
            this.endPointEdit();
            chain.chainLength.set(index + 1);
            this.panel.refresh();
        }
    }

    /** Where the spline may end: its start or a bone under it. */
    private List<String> endCandidates()
    {
        IModel model = this.panel.getModel();
        String root = this.root();

        if (model == null || root.isEmpty())
        {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();

        candidates.add(root);
        candidates.addAll(model.getAllChildrenKeys(root));

        return candidates;
    }

    /** A new end keeps the start: the spline reaches as far up from it as before. */
    private void setEnd(String bone)
    {
        SplineIK chain = this.chain();
        String root = this.root();

        if (chain == null || bone == null || bone.equals(this.tip()) || !this.endCandidates().contains(bone))
        {
            return;
        }

        int length = this.ancestors(bone).indexOf(root) + 1;

        this.endPointEdit();
        this.panel.edit((form) ->
        {
            chain.tip.set(bone);
            chain.chainLength.set(length);
        });
        this.panel.selectBoneInTree(bone);
    }

    private void fillMenu(UIBonePickerContextMenu picker, String current, List<String> candidates)
    {
        IModel model = this.panel.getModel();

        if (model == null || this.chain() == null)
        {
            return;
        }

        Set<String> hidden = new HashSet<>(model.getAllGroupKeys());

        candidates.forEach(hidden::remove);
        picker.bones(model, hidden).set(current);
    }

    /* The spline's points, see the rig tab's spline tool */

    public SplineIK chain()
    {
        ModelForm form = this.panel.getForm();

        return form == null || this.chainId.isEmpty() ? null : form.splines.get(this.chainId);
    }

    public SplinePoint point()
    {
        return this.pointEditor.point();
    }

    public String getChainId()
    {
        return this.chainId;
    }

    public String getPointId()
    {
        return this.pointEditor.pointId();
    }

    /** Shows the spline and picks its point: the selection moves to the spline's tip bone. */
    public void select(String chainId, String point)
    {
        ModelForm form = this.panel.getForm();
        SplineIK chain = form == null ? null : form.splines.get(chainId);

        this.endPointEdit();

        if (chain != null)
        {
            this.chainId = chainId;
            this.panel.selectBoneInTree(chain.tip.get());
        }

        this.pointEditor.select(point);
    }

    public void endPointEdit()
    {
        this.pointEditor.endEdit();
    }

    public UIPropTransform getGizmoTransform()
    {
        if (this.point() == null || !this.isOn())
        {
            return null;
        }

        ModelInstance model = ModelFormRenderer.getModel(this.panel.getForm());

        return model == null || model.model == null || model.model.getBone(this.root()) == null ? null : this.position;
    }

    public Matrix4f getGizmoOrigin(float transition)
    {
        UIForm editor = this.editor();

        if (this.getGizmoTransform() == null || editor.editor == null)
        {
            return null;
        }

        Matrix4f parent = SplineEditorUtils.parentMatrix(FormUtils.getRoot(this.panel.getForm()), editor.editor.renderer.getTargetEntity(), transition, this.panel.getForm(), this.chain());

        return parent == null ? null : parent.translate(this.point().position.getOriginalValue().translate);
    }
}
