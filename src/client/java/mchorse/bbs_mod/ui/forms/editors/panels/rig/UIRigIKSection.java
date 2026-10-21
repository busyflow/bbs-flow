package mchorse.bbs_mod.ui.forms.editors.panels.rig;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ik.BoneIKIO;
import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
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
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIIKControlFields;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The IK chain the selected bone takes part in — as its tip, a link, the target it reaches for or
 * the pole it bends toward — edited wherever it is picked up. The chain lives on its tip bone, as
 * it always has: the start is how far up it reaches, the end is the tip. Switched on, a bone gets a
 * chain ending at it and starting two bones up, and then it needs only a target.
 */
public class UIRigIKSection extends UIRigSection
{
    /** How many bones a new chain takes, its tip included. */
    private static final int NEW_CHAIN = 3;

    public final UIBonePicker start;
    public final UIBonePicker end;
    public final UIBonePicker target;
    public final UIColor targetColor;
    public final UIToggle pole;
    public final UIBonePicker poleTarget;
    public final UIIKControlFields controls;
    public final UIToggle tipRotation;
    public final UIToggle stretch;
    public final UIToggle squash;
    public final UIToggle classic;

    /** The tip the shown chain lives on, empty while there is none. */
    private String tip = "";

    public UIRigIKSection(UIRigFormPanel panel)
    {
        super(panel, RigStep.IK, true);

        this.start = new UIBonePicker(this::setStart);
        this.start.menu((picker) -> this.fillMenu(picker, this.chain().isEmpty() ? "" : this.chain().get(0), this.ancestors(this.tip)));
        this.start.viewport(panel.viewportPicking());

        this.end = new UIBonePicker(this::setEnd);
        this.end.menu((picker) -> this.fillMenu(picker, this.tip, this.endCandidates()));
        this.end.viewport(panel.viewportPicking());

        this.target = new UIBonePicker((bone) ->
        {
            /* The eyedropper bypasses the popup's graying, so the cycle gate sits on the shared
             * callback: a target the chain itself drives is refused outright. */
            if (!this.isCyclic(bone))
            {
                this.editTip((formBone) -> formBone.ikTarget.set(bone));
            }
        });
        this.target.menu((picker) -> this.fillBoneMenu(picker, this.read((b) -> b.ikTarget.get()), this::isCyclic));
        this.target.viewport(panel.viewportPicking());
        this.target.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_TARGET);

        /* The colour of the marker at the target, kept with the chain. */
        this.targetColor = new UIColor((c) -> this.panel.editFormBone(this.tip, (formBone) -> formBone.ikTargetColor.set(c))).noLabel();
        this.targetColor.withDefault(this::targetColorDefault, () -> this.panel.editFormBone(this.tip, (formBone) -> formBone.ikTargetColor.reset()));
        this.targetColor.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_TARGET_COLOR);
        this.targetColor.w(UIConstants.CONTROL_HEIGHT * 2);

        UIElement targetLabel = new UIElement();

        targetLabel.row(UIConstants.SECTION_GAP).preferred(0).height(UIConstants.CONTROL_HEIGHT);
        targetLabel.add(UI.label(UIKeys.FORMS_EDITORS_MODEL_IK_TARGET_LABEL, UIConstants.CONTROL_HEIGHT).labelAnchor(0, 0.5F), this.targetColor);

        this.pole = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, (b) ->
        {
            this.editControl((c) -> c.pole = b.getValue());
            this.panel.refresh();
        });
        this.pole.h(UIConstants.CONTROL_HEIGHT);

        /* A pole on a chain bone is not fatal (the chain falls back to the automatic pole), so nothing is grayed out. */
        this.poleTarget = new UIBonePicker((bone) -> this.editTip((formBone) -> formBone.ikPoleTarget.set(bone)));
        this.poleTarget.menu((picker) -> this.fillBoneMenu(picker, this.read((b) -> b.ikPoleTarget.get()), null));
        this.poleTarget.viewport(panel.viewportPicking());
        this.poleTarget.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_TARGET);

        this.controls = new UIIKControlFields(this::editControl);

        this.tipRotation = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_TIP_ROTATION, (b) -> this.editTip((bone) -> bone.ikTipRotation.set(b.getValue())));
        this.stretch = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_STRETCH, (b) -> this.editTip((bone) -> bone.ikStretch.set(b.getValue())));
        this.stretch.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_STRETCH_TOOLTIP);
        this.squash = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_SQUASH, (b) -> this.editTip((bone) -> bone.ikSquash.set(b.getValue())));
        this.squash.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_SQUASH_TOOLTIP);
        this.classic = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC, (b) -> this.editTip((bone) -> bone.ikClassic.set(b.getValue())));
        this.classic.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC_TOOLTIP);

        this.page(UIKeys.FORMS_EDITORS_MODEL_RIG_MAIN,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_RIG_START, this.start),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_RIG_END, this.end),
            UI.labelRow(targetLabel, this.target),
            UI.labelRow(this.pole, this.poleTarget),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT, this.controls.weight)
        );
        this.page(UIKeys.FORMS_EDITORS_MODEL_IK_ADVANCED,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE, this.controls.poleAngle),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS, this.controls.softness),
            this.tipRotation,
            this.stretch,
            this.squash,
            this.classic
        );
    }

    @Override
    protected boolean fill(String bone)
    {
        this.tip = this.tipOf(bone);

        FormBone formBone = this.panel.getFormBone(this.tip);

        if (formBone == null)
        {
            this.tip = "";

            return false;
        }

        IKControl control = this.control();
        List<String> chain = this.chain();
        String start = chain.isEmpty() ? this.tip : chain.get(0);
        boolean cyclicTarget = this.isCyclic(formBone.ikTarget.get());
        boolean cyclicPole = this.isCyclic(formBone.ikPoleTarget.get());

        this.setInfo(start + " → " + this.tip);
        this.start.setLabel(IKey.raw(start));
        this.end.setLabel(IKey.raw(this.tip));
        this.target.setLabel(IKey.raw(bone(formBone.ikTarget.get()) + (cyclicTarget ? UIKeys.FORMS_EDITORS_MODEL_IK_CYCLE.get() : "")));
        this.targetColor.setVisible(formBone.hasChain());
        this.targetColor.setColor(this.targetColor());
        this.pole.setValue(control.pole);
        this.poleTarget.setLabel(IKey.raw(bone(formBone.ikPoleTarget.get()) + (cyclicPole ? UIKeys.FORMS_EDITORS_MODEL_IK_POLE_CYCLE.get() : "")));
        this.controls.weight.setValue(control.weight);
        this.controls.softness.setValue(control.softness);
        this.controls.poleAngle.setValue(control.poleAngle);
        this.tipRotation.setValue(formBone.ikTipRotation.get());
        this.stretch.setValue(formBone.ikStretch.get());
        this.squash.setValue(formBone.ikSquash.get());
        this.classic.setValue(formBone.ikClassic.get());

        /* The classic switch is loud about its fallback: a chain that isn't exactly two bones, or
         * shares a bone with another chain, solves on the core instead. */
        this.classic.label = formBone.ikClassic.get() && this.classicFallsBack(formBone) ? UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC_FALLBACK : UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC;

        return true;
    }

    /**
     * The tip of the chain the bone takes part in. A bone switched on but given no target yet has
     * a chain only on itself — it reaches up already, it just has nothing to reach for.
     */
    private String tipOf(String bone)
    {
        RigClaim claim = this.panel.chainOf(RigStep.IK, bone);

        if (claim != null)
        {
            return claim.owner();
        }

        FormBone formBone = this.panel.getFormBone(bone);

        return formBone != null && !formBone.hasChain() && formBone.ikChainLength.get() > 0 ? bone : "";
    }

    @Override
    public void switched(String bone, boolean on)
    {
        if (on)
        {
            int length = Math.min(NEW_CHAIN, this.ancestors(bone).size());

            this.panel.edit((form) ->
            {
                FormBone formBone = form.bones.getOrCreate(bone);

                BoneIKIO.clearChain(formBone);
                formBone.ikChainLength.set(length);
            });
        }
        else if (!this.tip.isEmpty())
        {
            String tip = this.tip;

            this.panel.edit((form) -> BoneIKIO.clearChain(form.bones.getOrCreate(tip)));
        }
    }

    @Override
    public void setFieldsEnabled(boolean enabled)
    {
        super.setFieldsEnabled(enabled);

        for (UIElement element : List.of(this.start, this.end, this.target, this.targetColor, this.pole, this.controls.weight, this.controls.softness,
            this.controls.poleAngle, this.tipRotation, this.stretch, this.squash, this.classic))
        {
            element.setEnabled(enabled);
        }

        this.poleTarget.setEnabled(enabled && this.control().pole);
        this.controls.poleAngle.setEnabled(enabled && this.control().pole);
    }

    /* The chain's ends */

    /** The chain's bones, root to tip. */
    private List<String> chain()
    {
        IModel model = this.panel.getModel();
        FormBone formBone = this.panel.getFormBone(this.tip);

        return model == null || formBone == null ? List.of() : ModelIKRuntime.chainBones(model, this.tip, formBone.ikChainLength.get());
    }

    /** The bone and every bone above it, nearest first: where a chain ending at it may start. */
    private List<String> ancestors(String bone)
    {
        IModel model = this.panel.getModel();

        return model == null || bone.isEmpty() ? List.of() : new ArrayList<>(model.getHierarchyGroups(bone));
    }

    /** The start sets how far up the tip's chain reaches. */
    private void setStart(String bone)
    {
        int index = this.ancestors(this.tip).indexOf(bone);

        if (index >= 0)
        {
            this.editTip((formBone) -> formBone.ikChainLength.set(index + 1));
        }
    }

    /** Where the chain may end: the start or a bone under it, none with a chain of its own. */
    private List<String> endCandidates()
    {
        IModel model = this.panel.getModel();
        List<String> chain = this.chain();

        if (model == null || chain.isEmpty())
        {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();

        candidates.add(chain.get(0));
        candidates.addAll(model.getAllChildrenKeys(chain.get(0)));
        candidates.removeIf((bone) ->
        {
            FormBone formBone = this.panel.getFormBone(bone);

            return !bone.equals(this.tip) && formBone != null && formBone.hasChain();
        });

        return candidates;
    }

    /** A new end moves the chain over to it — the chain lives on its tip — keeping its start. */
    private void setEnd(String bone)
    {
        List<String> chain = this.chain();

        if (bone == null || bone.equals(this.tip) || chain.isEmpty() || !this.endCandidates().contains(bone))
        {
            return;
        }

        String from = this.tip;
        int length = this.ancestors(bone).indexOf(chain.get(0)) + 1;

        if (length <= 0)
        {
            return;
        }

        this.panel.edit((form) ->
        {
            FormBone old = form.bones.getOrCreate(from);
            FormBone moved = form.bones.getOrCreate(bone);
            IKControl control = form.ik.getOriginalValue().get(from).copy();

            moved.ikTarget.set(old.ikTarget.get());
            moved.ikTargetColor.set(old.ikTargetColor.get());
            moved.ikPoleTarget.set(old.ikPoleTarget.get());
            moved.ikChainLength.set(length);
            moved.ikTipRotation.set(old.ikTipRotation.get());
            moved.ikStretch.set(old.ikStretch.get());
            moved.ikSquash.set(old.ikSquash.get());
            moved.ikClassic.set(old.ikClassic.get());
            BoneIKIO.clearChain(old);
            form.ik.getOriginalValue().controls.put(bone, control);
        });
        this.panel.selectBoneInTree(bone);
    }

    /* Value access */

    private IKControl control()
    {
        ModelForm form = this.panel.getForm();

        return form == null || this.tip.isEmpty() ? IKControl.DEFAULT : form.ik.getOriginalValue().get(this.tip);
    }

    private String read(Function<FormBone, String> getter)
    {
        FormBone formBone = this.panel.getFormBone(this.tip);

        return formBone == null ? "" : getter.apply(formBone);
    }

    /** Edits the tip's bone values and shows the result. */
    private void editTip(Consumer<FormBone> edit)
    {
        if (!this.tip.isEmpty())
        {
            this.panel.editFormBone(this.tip, edit);
            this.panel.refresh();
        }
    }

    /** The colour of the marker at the chain's target. */
    private int targetColor()
    {
        FormBone formBone = this.panel.getFormBone(this.tip);

        return formBone == null ? FormBone.TARGET_COLOR : formBone.ikTargetColor.get();
    }

    /** The target marker's default colour while the chain's differs, else null (nothing to reset). */
    private Integer targetColorDefault()
    {
        return this.targetColor() == FormBone.TARGET_COLOR ? null : FormBone.TARGET_COLOR;
    }

    /** Edits the chain's IK scalars as one value change (one undo entry). */
    private void editControl(Consumer<IKControl> edit)
    {
        ModelForm form = this.panel.getForm();
        String tip = this.tip;

        if (form == null || tip.isEmpty())
        {
            return;
        }

        IKControl control = form.ik.getOriginalValue().get(tip).copy();

        edit.accept(control);
        BaseValue.edit(form.ik, (value) -> value.getOriginalValue().controls.put(tip, control));
    }

    private void fillMenu(UIBonePickerContextMenu picker, String current, List<String> candidates)
    {
        IModel model = this.panel.getModel();

        if (model == null || this.tip.isEmpty())
        {
            return;
        }

        Set<String> hidden = new HashSet<>(model.getAllGroupKeys());

        candidates.forEach(hidden::remove);
        picker.bones(model, hidden).set(current);
    }

    private void fillBoneMenu(UIBonePickerContextMenu picker, String current, Predicate<String> disabled)
    {
        if (this.panel.getModelInstance() == null || this.panel.getModel() == null || this.tip.isEmpty())
        {
            return;
        }

        picker.bones(this.panel.getModel(), this.panel.getModelInstance().getDisabledBones()).none().disabled(disabled).set(current);
    }

    /** Whether pointing the chain at the bone would close a feedback loop: it is one of the chain's own. */
    private boolean isCyclic(String bone)
    {
        IModel model = this.panel.getModel();
        FormBone formBone = this.panel.getFormBone(this.tip);

        return model != null && formBone != null && bone != null && !bone.isEmpty()
            && ModelIKRuntime.isCyclicTarget(model, this.tip, formBone.ikChainLength.get(), bone);
    }

    /**
     * Whether the classic chain solves on the core after all: the wrong shape (not exactly two
     * bones), or a bone shared with another chain that's on (overlapping chains solve together).
     */
    private boolean classicFallsBack(FormBone formBone)
    {
        IModel model = this.panel.getModel();
        ModelForm form = this.panel.getForm();

        if (model == null || form == null)
        {
            return false;
        }

        if (!ModelIKRuntime.isClassicShape(model, this.tip, formBone.ikChainLength.get(), formBone.ikTipRotation.get()))
        {
            return true;
        }

        List<String> mine = this.chain();

        for (RigClaim claim : this.panel.getOwners().getClaims())
        {
            if (claim.step() == RigStep.IK && claim.part() == RigClaim.Part.HOME && claim.on()
                && !claim.owner().equals(this.tip) && claim.chain().stream().anyMatch(mine::contains))
            {
                return true;
            }
        }

        return false;
    }

    private static String bone(String bone)
    {
        return bone == null || bone.isEmpty() ? "-" : bone;
    }
}
