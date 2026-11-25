package mchorse.bbs_mod.ui.forms.editors;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIItemFormPanel;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIconToggles;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class UIBodyPartEditor extends UIScrollView
{
    /** One folding section holds the part's fields; the form editor refits around it when it folds. */
    public UISection section;
    public UIButton pick;
    public UIToggle useTarget;
    public UIBonePicker bone;
    public UIIconToggles inherit;
    public UIPropTransform transform;

    private final UIFormEditor editor;

    private BodyPart part;

    /** The form the body part is attached to — whose bones the picker offers. */
    private Form owner;

    public UIBodyPartEditor(UIFormEditor editor)
    {
        this.editor = editor;

        this.pick = new UIButton(UIKeys.FORMS_EDITOR_PICK_FORM, (b) ->
        {
            UIForms.FormEntry current = this.editor.formsList.getCurrentFirst();

            this.editor.openFormList(current.part.getForm(), (f) ->
            {
                current.part.setForm(FormUtils.copy(f));

                Form partForm = current.part.getForm();

                if (partForm != null && partForm.getFormId().contains("particle"))
                {
                    current.part.useTarget.set(true);

                    this.useTarget.setValue(true);
                }

                this.editor.refreshFormList();
                this.editor.switchEditor(partForm);
            });
        });

        this.useTarget = new UIToggle(UIKeys.FORMS_EDITOR_USE_TARGET, (b) ->
        {
            this.part.useTarget.set(b.getValue());
        });
        this.useTarget.valueBinding(() ->
        {
            if (this.part != null)
            {
                this.useTarget.setValue(this.part.useTarget.get());
            }
        });

        this.bone = new UIBonePicker((b) ->
        {
            if (this.part == null)
            {
                return;
            }

            this.attachBone(b);
        });
        this.bone.menu((picker) ->
        {
            if (this.part == null || this.owner == null)
            {
                return;
            }

            ModelInstance model = this.owner instanceof ModelForm modelForm ? ModelFormRenderer.getModel(modelForm) : null;

            if (model != null && model.model != null)
            {
                /* Same visibility rule as FormUtilsClient.getBones: disabled bones
                 * hide unless the pose setting shows them. */
                picker.bones(model.model, BBSSettings.poseShowDisabledBones.get() ? null : model.getDisabledBones());
            }
            else
            {
                IBoneHierarchy hierarchy = FormUtilsClient.getBoneHierarchy(this.owner);

                if (hierarchy == null)
                {
                    /* Bones without any tree behind them list flat. */
                    List<String> bones = new ArrayList<>(FormUtilsClient.getBones(this.owner));

                    bones.sort(String::compareToIgnoreCase);
                    picker.list(bones);
                }
                else
                {
                    picker.bones(hierarchy, null);
                }
            }

            picker.none().set(this.part.bone.get());
        });
        this.bone.viewport(new UIBonePicker.Viewport()
        {
            @Override
            public void startPicking(Consumer<String> callback)
            {
                /* The eyedropper catches bones of the OWNER form (the parent the
                 * part hangs off), not the part's own form being edited. */
                UIBodyPartEditor.this.editor.startBonePicking((pair) ->
                {
                    BodyPart part = UIBodyPartEditor.this.part;

                    callback.accept(pair != null && part != null && part.getManager().getOwner() == pair.a ? pair.b : null);
                });
            }

            @Override
            public void stopPicking()
            {
                UIBodyPartEditor.this.editor.stopBonePicking();
            }
        });

        /* Which components of the bone's frame the part rides, as one strip: the same three icons
         * the gizmo uses for the same three ideas. Bound to the part's own values, so it neither
         * needs filling in when the part changes nor writing back when a cell is clicked. */
        this.inherit = new UIIconToggles(null)
            .add(Icons.ALL_DIRECTIONS, UIKeys.INHERIT_POSITION, () -> this.part.inheritPosition)
            .add(Icons.ORBIT, UIKeys.INHERIT_ROTATION, () -> this.part.inheritRotation)
            .add(Icons.SCALE, UIKeys.INHERIT_SCALE, () -> this.part.inheritScale)
            .resettable();

        this.transform = new UIPropTransform().callbacks(() -> this.part.transform).barBackground();
        this.transform.enableHotkeys(this.editor::isBodyPartGizmoMode);
        this.transform.hotkeyDrag(() -> this.editor.buildHotkeyDrag(this.transform));
        this.transform.valueBinding(() -> this.transform.setTransform(this.part == null ? null : this.part.transform.get()));

        this.pick.keys().register(Keys.FORMS_EDIT, this.pick::clickItself);

        this.section = new UISection(UIKeys.FORMS_EDITOR_BODY_PART);

        this.column(UIConstants.MARGIN).vertical().stretch().scroll().padding(UIConstants.SCROLL_PADDING);
        this.scroll.cancelScrolling();
        this.add(this.section);
    }

    public void setPart(BodyPart part, Form form)
    {
        this.part = part;
        this.owner = form;

        this.section.fields.removeAll();

        this.bone.setLabel(this.boneLabel(part.bone.get()));

        /* The inheritance toggles filter the attachment bone's matrix, so they are offered only
         * where there is a bone to attach to at all — the same condition the picker has. */
        if (!FormUtilsClient.getBones(form).isEmpty())
        {
            this.section.fields.add(this.pick, this.bone, this.inherit.labelRow(UIKeys.INHERIT_TITLE), this.useTarget, this.transform);
        }
        else
        {
            this.section.fields.add(this.pick, this.useTarget, this.transform);
        }

        this.scroll.setScroll(0);
        this.resize();
    }

    private IKey boneLabel(String bone)
    {
        return bone == null || bone.isEmpty() ? UIKeys.MODEL_EDITOR_PICK_BONE : IKey.constant(bone);
    }

    /** Attach the active body part to the clicked parent bone; returns whether it did. */
    public boolean pickBone(Pair<Form, String> pair)
    {
        /* Ctrl + clicking to pick the parent bone to attach to */
        if (this.part != null && !pair.b.isEmpty() && this.part.getManager().getOwner() == pair.a)
        {
            this.attachBone(pair.b);

            return true;
        }

        return false;
    }

    private void attachBone(String boneName)
    {
        this.part.bone.set(boneName);
        this.bone.setLabel(this.boneLabel(boneName));
        this.autoSnapItemToHand(boneName);
    }

    private void autoSnapItemToHand(String boneName)
    {
        if (this.part == null || boneName == null || boneName.isEmpty() || !(this.part.getForm() instanceof ItemForm itemForm))
        {
            return;
        }

        String lower = boneName.toLowerCase(Locale.ROOT);
        boolean isRight = lower.equals("rightarm") || lower.equals("right_arm") || lower.equals("right_arm_item")
            || lower.equals("rightitem") || lower.equals("right_arm_end") || lower.equals("right_hand")
            || lower.equals("righthand") || lower.equals("arm_right") || lower.equals("hand_right")
            || lower.endsWith("_right_arm") || lower.endsWith("_rightarm")
            || lower.endsWith("_right_hand") || lower.endsWith("_righthand");
        boolean isLeft = lower.equals("leftarm") || lower.equals("left_arm") || lower.equals("left_arm_item")
            || lower.equals("leftitem") || lower.equals("left_arm_end") || lower.equals("left_hand")
            || lower.equals("lefthand") || lower.equals("arm_left") || lower.equals("hand_left")
            || lower.endsWith("_left_arm") || lower.endsWith("_leftarm")
            || lower.endsWith("_left_hand") || lower.endsWith("_lefthand");

        if (!isRight && !isLeft)
        {
            return;
        }

        ModelTransformationMode mode = isLeft
            ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND
            : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
        itemForm.modelTransform.set(mode);

        if (this.editor != null && this.editor.editor != null && this.editor.editor.view instanceof UIItemFormPanel itemPanel)
        {
            itemPanel.modelTransform.label = IKey.constant(mode.asString());
        }

        FormRenderer ownerRenderer = this.owner == null ? null : FormUtilsClient.getRenderer(this.owner);
        Vector3f offset = ownerRenderer == null ? null : ownerRenderer.getHandItemOffset(boneName, isLeft);

        if (offset == null)
        {
            if (lower.contains("item") || lower.contains("hand") || lower.contains("end"))
            {
                offset = new Vector3f(0F, 0F, 0.125F);
            }
            else
            {
                offset = new Vector3f(isLeft ? 0.0625F : -0.0625F, -0.625F, 0.125F);
            }
        }

        Transform t = new Transform();
        t.translate.set(offset);
        t.rotationMode = Transform.RotationMode.EULER;
        t.rotate.set((float) Math.toRadians(-90F), (float) Math.toRadians(180F), 0F);
        t.scale.set(1F, 1F, 1F);

        this.part.transform.set(t);
        this.transform.setTransform(this.part.transform.get());
    }
}
