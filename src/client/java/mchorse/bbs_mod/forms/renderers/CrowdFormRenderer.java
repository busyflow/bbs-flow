package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.forms.CrowdForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * A crowd has no geometry of its own.
 *
 * <p>Its members are real entities the server spawned, so the world already draws them through
 * the ordinary entity renderer. Drawing anything here as well would put a second copy of the
 * crowd in exactly the same places.</p>
 *
 * <p>Only the palette thumbnail is left, so the form is recognisable when picking one.</p>
 */
public class CrowdFormRenderer extends FormRenderer<CrowdForm>
{
    public CrowdFormRenderer(CrowdForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.icon(Icons.CHICKEN, (x1 + x2) / 2, (y1 + y2) / 2, 0.5F, 0.5F);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {}
}
