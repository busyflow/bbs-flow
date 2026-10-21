package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.rig.RigOwners;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIActionsFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIRigFormPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.GizmoLocks;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class UIModelForm extends UIForm<ModelForm>
{
    public UIModelFormPanel modelPanel;
    public UIRigFormPanel rigPanel;

    public UIModelForm()
    {
        this.modelPanel = new UIModelFormPanel(this);
        this.modelPanel.poseEditor.transform.hotkeyDrag(() -> this.editor == null ? null : this.editor.buildHotkeyDrag(this.modelPanel.poseEditor.transform));
        this.modelPanel.poseEditor.transform.worldTransform(new FormBoneWorldProvider(this));
        this.modelPanel.poseEditor.transform.rotationConstrained(() ->
        {
            ModelForm form = this.form;
            ModelInstance instance = form == null ? null : ModelFormRenderer.getModel(form);

            return instance != null && RigOwners.of(instance.model, form).locksRotation(this.modelPanel.poseEditor.groups.list.getCurrentFirst());
        });
        this.modelPanel.poseEditor.transform.axisLocks(() -> GizmoLocks.of(this.form, this.modelPanel.poseEditor.groups.list.getCurrent()));
        this.defaultPanel = this.modelPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_MODEL_POSE, ModelForm.ICON);
        this.rigPanel = new UIRigFormPanel(this);
        this.registerPanel(this.rigPanel, UIKeys.FORMS_EDITORS_MODEL_RIG_TITLE, Icons.BONE);
        this.registerPanel(new UIActionsFormPanel(this), UIKeys.FORMS_EDITORS_ACTIONS_TITLE, Icons.MORE);
        this.registerDefaultPanels();

        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, () ->
        {
            if (this.view != this.modelPanel)
            {
                this.setPanel(this.modelPanel);
            }

            this.modelPanel.pick.clickItself();
        });
    }

    @Override
    public UIPoseEditor getPoseEditor()
    {
        return this.modelPanel.poseEditor;
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.putString("splineChain", this.rigPanel.spline.getChainId());
        data.putString("splinePoint", this.rigPanel.spline.getPointId());

        data.put("bones", DataStorageUtils.stringListToData(this.modelPanel.poseEditor.groups.list.getCurrent()));
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        this.rigPanel.spline.select(data.getString("splineChain"), data.getString("splinePoint"));

        if (data.has("bones"))
        {
            this.modelPanel.poseEditor.restoreSelection(DataStorageUtils.stringListFromData(data.get("bones")));
        }
    }

}
