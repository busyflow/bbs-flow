package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIBoneConstraintFields;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.function.Consumer;

/**
 * Editor for a bone's constraints track: the bone's limits, the same fields the rig lays them out
 * with. The value is the bone property's own type, so what this edits is exactly what the form
 * stores statically.
 */
public class UIBoneConstraintKeyframeFactory extends UIKeyframeFactory<BoneConstraint>
{
    public UIBoneConstraintFields fields;

    private boolean syncing;

    public UIBoneConstraintKeyframeFactory(UITrackValue<BoneConstraint> track, UIKeyframes editor)
    {
        super(track, editor);

        this.fields = new UIBoneConstraintFields(this::value, this::edit, this::display);

        this.scroll.add(UI.column(this.fields.rows()).marginTop(UIConstants.SECTION_GAP));

        this.display();
    }

    @Override
    public void update()
    {
        if (!this.fields.minX.isUserEditing() && !this.fields.minY.isUserEditing() && !this.fields.minZ.isUserEditing()
            && !this.fields.maxX.isUserEditing() && !this.fields.maxY.isUserEditing() && !this.fields.maxZ.isUserEditing()
            && !this.fields.stiffnessX.isUserEditing() && !this.fields.stiffnessY.isUserEditing() && !this.fields.stiffnessZ.isUserEditing())
        {
            this.display();
        }
    }

    private BoneConstraint value()
    {
        BoneConstraint c = this.track.getValue();

        return c == null ? BoneConstraint.DEFAULT : c;
    }

    private void display()
    {
        BoneConstraint c = this.value();

        this.syncing = true;

        try
        {
            this.fields.fill(c, true);
            this.fields.setEnabled(true, c);
            this.resize();
        }
        finally
        {
            this.syncing = false;
        }
    }

    private void edit(Consumer<BoneConstraint> consumer)
    {
        if (this.syncing)
        {
            return;
        }

        this.track.edit((selected) ->
        {
            BoneConstraint c = (BoneConstraint) selected;

            if (c == null)
            {
                return;
            }
            consumer.accept(c);
        });
    }
}
