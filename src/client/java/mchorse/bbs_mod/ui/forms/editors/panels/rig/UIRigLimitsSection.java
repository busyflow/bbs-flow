package mchorse.bbs_mod.ui.forms.editors.panels.rig;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.cubic.rig.RigClaim;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIRigFormPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.utils.UIBoneConstraintFields;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The bone's limits: the locks of its move and scale, per rotation axis a lock and an angle range,
 * and the stiffness an IK solve turns it with. Every bone has them, so there is no switch — they act
 * once an axis is locked or limited. The section opens on a bone that has any, until it is folded
 * by hand.
 */
public class UIRigLimitsSection extends UIRigSection
{
    /** How the section was last folded by hand this session, or null to follow the bone. */
    private static Boolean folded;

    public final UIBoneConstraintFields controls;
    public final UIButton applyToChildren;

    public UIRigLimitsSection(UIRigFormPanel panel)
    {
        super(panel, RigStep.LIMITS, false);

        this.controls = new UIBoneConstraintFields(this::current, this::edit, panel::refresh);
        this.applyToChildren = new UIButton(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_APPLY_TO_CHILDREN, (b) -> this.applyToChildren());

        this.fields.add(this.controls.rows());
        this.fields.add(this.applyToChildren.marginTop(UIConstants.SECTION_GAP));
    }

    @Override
    protected boolean fill(String bone)
    {
        BoneConstraint constraint = this.current();
        boolean active = constraint.isActive() || constraint.locksTranslateOrScale();

        this.controls.fill(constraint, this.inIKChain(bone));

        /* A fold by hand holds for the session, on a section built anew too (a pick in the viewport
         * rebuilds the tab). */
        boolean expanded = folded == null ? active : folded;

        if (this.isExpanded() != expanded)
        {
            this.setExpanded(expanded);
        }

        return active;
    }

    @Override
    public void switched(String bone, boolean on)
    {}

    @Override
    public void setFieldsEnabled(boolean enabled)
    {
        super.setFieldsEnabled(enabled);

        this.controls.setEnabled(enabled, this.current());
        this.applyToChildren.setEnabled(enabled && !this.descendants(this.panel.getSelectedBone()).isEmpty());
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        boolean toggled = super.subMouseClicked(context);

        if (toggled)
        {
            folded = this.isExpanded();
        }

        return toggled;
    }

    /** The selected bone's limits as stored, or the neutral ones of an untouched bone. */
    private BoneConstraint current()
    {
        FormBone bone = this.panel.getFormBone(this.panel.getSelectedBone());

        return bone == null ? BoneConstraint.DEFAULT : bone.constraints.get();
    }

    /** Edits the selected bone's limits as a value change (one undo entry, one notification). */
    private void edit(Consumer<BoneConstraint> edit)
    {
        this.panel.editFormBone(this.panel.getSelectedBone(), (bone) ->
        {
            BoneConstraint constraint = bone.constraints.get().copy();

            edit.accept(constraint);
            bone.constraints.set(constraint);
        });
    }

    /** Whether an IK chain runs through the bone: the stiffness is the solve's alone. */
    private boolean inIKChain(String bone)
    {
        for (RigClaim claim : this.panel.getOwners().on(bone))
        {
            if (claim.step() == RigStep.IK && claim.inChain())
            {
                return true;
            }
        }

        return false;
    }

    /** The selected bone's limits onto every bone under it, as one change. */
    private void applyToChildren()
    {
        String bone = this.panel.getSelectedBone();
        List<String> descendants = this.descendants(bone);

        if (descendants.isEmpty())
        {
            return;
        }

        BoneConstraint constraint = this.current();

        this.panel.edit((form) ->
        {
            for (String child : descendants)
            {
                form.bones.getOrCreate(child).constraints.set(constraint.copy());
            }
        });
    }

    private List<String> descendants(String bone)
    {
        IModel model = this.panel.getModel();

        if (bone == null || bone.isEmpty() || model == null)
        {
            return Collections.emptyList();
        }

        List<String> descendants = new ArrayList<>(model.getAllChildrenKeys(bone));
        List<String> available = this.panel.getAvailableBones();

        if (!available.isEmpty())
        {
            descendants.removeIf((id) -> !available.contains(id));
        }

        return descendants;
    }
}
