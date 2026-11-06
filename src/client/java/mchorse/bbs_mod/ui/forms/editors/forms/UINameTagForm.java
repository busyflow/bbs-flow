package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.NameTagForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UINameTagFormPanel;

public class UINameTagForm extends UIForm<NameTagForm>
{
    public UINameTagForm()
    {
        super();

        this.defaultPanel = new UINameTagFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_NAMETAG_TITLE, NameTagForm.ICON);
        this.registerDefaultPanels();
    }
}
