package mchorse.bbs_mod.ui.forms.editors.panels.rig;

import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.cubic.shake.ShakeControl;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIRigFormPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIShakeControlFields;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * The bone's shake: how strongly, how fast and how rough, and how far along each channel. The
 * numbers are the form's shake controls, which the shake track keys over a take.
 */
public class UIRigShakeSection extends UIRigSection
{
    public final UIShakeControlFields controls;

    /** Shown when IK or Spline IK sets the bone's rotation, naming which: the solve takes the shake's rotation out. */
    private final UILabel driven;

    public UIRigShakeSection(UIRigFormPanel panel)
    {
        super(panel, RigStep.SHAKE, true);

        this.controls = new UIShakeControlFields(this::editControl);

        this.driven = UI.label(UIKeys.FORMS_EDITORS_MODEL_SHAKE_DRIVEN, UIConstants.CONTROL_HEIGHT, Colors.ORANGE);
        this.driven.labelAnchor(0, 0.5F);
        this.driven.tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_DRIVEN_TOOLTIP);

        this.fields.add(this.driven);
        this.fields.add(this.controls.settings());
        this.fields.add(this.controls.amplitudes());
    }

    @Override
    protected boolean fill(String bone)
    {
        FormBone formBone = this.panel.getFormBone(bone);

        if (formBone == null || !formBone.hasShake())
        {
            return false;
        }

        RigStep driver = this.panel.getOwners().rotationDriver(bone);

        this.driven.label = driver == RigStep.SPLINE ? UIKeys.FORMS_EDITORS_MODEL_SHAKE_DRIVEN_SPLINE : UIKeys.FORMS_EDITORS_MODEL_SHAKE_DRIVEN;
        this.driven.setVisible(driver != null);
        this.controls.fill(this.control(bone));

        return true;
    }

    /** On starts with the default tremble; off means gone, numbers and all. */
    @Override
    public void switched(String bone, boolean on)
    {
        this.panel.edit((form) ->
        {
            form.bones.getOrCreate(bone).shake.set(on);

            if (!on)
            {
                form.shake.getOriginalValue().controls.remove(bone);
            }
        });
    }

    @Override
    public void setFieldsEnabled(boolean enabled)
    {
        super.setFieldsEnabled(enabled);

        for (UIElement element : this.controls.all())
        {
            element.setEnabled(enabled);
        }
    }

    /** The bone's own numbers; a bone that has none yet shows the defaults. */
    private ShakeControl control(String bone)
    {
        ModelForm form = this.panel.getForm();
        ShakeControl control = form == null ? null : form.shake.getOriginalValue().controls.get(bone);

        return control == null ? ShakeControl.DEFAULT : control;
    }

    /** Edits the selected bone's control in the form's shake controls as one value change. */
    private void editControl(Consumer<ShakeControl> edit)
    {
        ModelForm form = this.panel.getForm();
        String bone = this.panel.getSelectedBone();

        if (form == null || bone.isEmpty())
        {
            return;
        }

        ShakeControl control = this.control(bone).copy();

        edit.accept(control);
        BaseValue.edit(form.shake, (value) -> value.getOriginalValue().controls.put(bone, control));
    }
}
