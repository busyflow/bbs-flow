package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.NameTagForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UINameTagFormPanel extends UIFormPanel<NameTagForm>
{
    public UIColor color;

    public UINameTagFormPanel(UIForm editor)
    {
        super(editor);

        UITextbox text = UIValues.textbox(1000, () -> this.form.text);
        text.textbox.setPlaceholder(UIKeys.FILM_REPLAY_NAME_TAG);

        UITrackpad height = UIValues.trackpad(() -> this.form.height);
        height.tooltip(UIKeys.FILM_REPLAY_NAME_TAG_HEIGHT);

        this.color = UIValues.color(() -> this.form.color);

        this.options.add(
            UI.label(UIKeys.FILM_REPLAY_NAME_TAG),
            text,
            height,
            this.color,
            UIValues.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, () -> this.form.billboard),
            UIValues.toggle(UIKeys.FORMS_EDITORS_NAMETAG_SEE_THROUGH, () -> this.form.seeThrough),
            UIValues.toggle(UIKeys.FORMS_EDITORS_NAMETAG_BACKGROUND, () -> this.form.background)
        );
    }
}
