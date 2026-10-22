package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageBarOverlayPanel;

import java.util.function.BiConsumer;

public class UINameTagPresetOverlayPanel extends UIMessageBarOverlayPanel
{
    public UITextbox nameTag;
    public UITrackpad nameTagHeight;
    public BiConsumer<String, Float> callback;

    public UINameTagPresetOverlayPanel(IKey title, IKey message, Form form, BiConsumer<String, Float> callback)
    {
        super(title, message);

        this.callback = callback;
        this.nameTag = new UITextbox(1000, null);
        this.nameTag.textbox.setPlaceholder(UIKeys.FILM_REPLAY_NAME_TAG);
        this.nameTagHeight = new UITrackpad(null);
        this.nameTagHeight.w(70).tooltip(UIKeys.FILM_REPLAY_NAME_TAG_HEIGHT);

        if (form != null)
        {
            this.nameTag.setText(form.nameTag.get());
            this.nameTagHeight.setValue(form.nameTagHeight.get());
        }

        this.bar.prepend(this.nameTagHeight);
        this.bar.prepend(this.nameTag);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        if (!this.nameTag.textbox.isSelected())
        {
            this.nameTag.textbox.moveCursorToEnd();
        }

        parent.getContext().focus(this.nameTag);
    }

    @Override
    public void confirm()
    {
        super.confirm();

        if (this.callback != null)
        {
            this.callback.accept(this.nameTag.getText(), (float) this.nameTagHeight.getValue());
        }
    }
}
