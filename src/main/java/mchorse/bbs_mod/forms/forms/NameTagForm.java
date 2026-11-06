package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

public class NameTagForm extends Form
{
    public static final Icon ICON = Icons.FONT;

    public final ValueString text = new ValueString("text", "Name tag");
    public final ValueFloat height = new ValueFloat("height", 0F);
    public final ValueColor color = new ValueColor("color", Color.white());
    public final ValueBoolean seeThrough = new ValueBoolean("see_through", true);
    public final ValueBoolean background = new ValueBoolean("background", true);
    public final ValueBoolean billboard = new ValueBoolean("billboard", true);

    public NameTagForm()
    {
        super();

        this.add(this.text);
        this.add(this.height);
        this.add(this.color);
        this.add(this.seeThrough);
        this.add(this.background);
        this.add(this.billboard);
    }

    @Override
    public String getDefaultDisplayName()
    {
        return this.text.get();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }
}
