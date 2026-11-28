package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.pose.UIActionsConfigEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;

import java.util.Collection;

public class UIActionsConfigKeyframeFactory extends UIKeyframeFactory<ActionsConfig>
{
    public UIActionsConfigEditor actionsEditor;
    private ActionsConfig displayed;

    /* Which arrangement the fields are in (null until the first layout), so a resize
     * that stays on the same side of the threshold doesn't rebuild the subtree */
    private Boolean wide;

    @SuppressWarnings("unchecked")
    public UIActionsConfigKeyframeFactory(UITrackValue<ActionsConfig> track, UIKeyframes editor)
    {
        super(track, editor);

        Form form = FormUtils.getForm(track.sheet.property);
        FormRenderer renderer = form != null ? FormUtilsClient.getRenderer(form) : null;

        this.actionsEditor = new UIActionsConfigEditor(() ->
        {

        }, () ->
        {
            if (renderer instanceof ModelFormRenderer modelRenderer)
            {
                modelRenderer.resetAnimator();
            }
            else if (renderer != null)
            {
                try
                {
                    renderer.getClass().getMethod("resetAnimator").invoke(renderer);
                }
                catch (Throwable ignored)
                {}
            }
            this.track.setValue(this.displayed);
        });
        this.displayed = track.getValue();

        if (form instanceof ModelForm modelForm)
        {
            this.actionsEditor.setConfigs(this.displayed, modelForm);
        }
        else if (renderer != null)
        {
            Collection<String> animations = null;
            Collection<String> actions = null;

            try
            {
                Object animObj = renderer.getClass().getMethod("getAnimationsList").invoke(renderer);
                if (animObj instanceof Collection<?> c) animations = (Collection<String>) c;
                Object actObj = renderer.getClass().getMethod("getActionsList").invoke(renderer);
                if (actObj instanceof Collection<?> c) actions = (Collection<String>) c;
            }
            catch (Throwable ignored)
            {}

            this.actionsEditor.setConfigs(this.displayed, animations, actions);
        }

        this.scroll.add(this.actionsEditor);
    }

    @Override
    public void update()
    {
        if (this.actionsEditor.speed.isUserEditing() || this.actionsEditor.fade.isUserEditing() || this.actionsEditor.tick.isUserEditing()) return;
        this.displayed = this.getDisplayValue();
        this.actionsEditor.refreshConfigs(this.displayed);
    }

    @Override
    public void resize()
    {
        boolean wide = this.getFlex().getW() > 240;

        if (this.wide == null || this.wide != wide)
        {
            this.wide = wide;
            this.rebuild(wide);
        }

        super.resize();
    }

    private void rebuild(boolean wide)
    {
        this.actionsEditor.removeAll();

        if (wide)
        {
            this.actionsEditor.add(UI.row(
                UI.column(
                    UI.label(UIKeys.FORMS_EDITORS_MODEL_ACTIONS), this.actionsEditor.actions,
                    UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_SPEED, this.actionsEditor.speed).marginTop(UIConstants.SECTION_GAP),
                    this.actionsEditor.loop.marginTop(UIConstants.SECTION_GAP)
                ),
                UI.column(
                    UI.label(UIKeys.FORMS_EDITORS_ACTIONS_ANIMATIONS), this.actionsEditor.animations,
                    UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_FADE, this.actionsEditor.fade),
                    UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_TICK, this.actionsEditor.tick)
                )
            ));
        }
        else
        {
            this.actionsEditor.add(UI.label(UIKeys.FORMS_EDITORS_MODEL_ACTIONS), this.actionsEditor.actions);
            this.actionsEditor.add(UI.label(UIKeys.FORMS_EDITORS_ACTIONS_ANIMATIONS).marginTop(UIConstants.SECTION_GAP), this.actionsEditor.animations, this.actionsEditor.loop.marginTop(UIConstants.SECTION_GAP));
            this.actionsEditor.add(UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_SPEED, this.actionsEditor.speed).marginTop(UIConstants.SECTION_GAP));
            this.actionsEditor.add(UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_FADE, this.actionsEditor.fade));
            this.actionsEditor.add(UI.labelRow(UIKeys.FORMS_EDITORS_ACTIONS_TICK, this.actionsEditor.tick));
        }
    }
}