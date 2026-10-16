package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

/**
 * An editor that shows a form on screen — the film editor and the form editor — and can say where
 * a pose of that form stands in the frame being shown, for edits that are measured on the live
 * model rather than derived (fixing a bone in place, see {@code PoseFixInPlace}).
 *
 * <p>Found the way {@link IBoneSelectionHost} is: by walking up the widget tree from the panel.</p>
 */
public interface ILivePoseHost
{
    /**
     * The value the frame on screen renders for {@code property} — the property itself in the form
     * editor, the replay entity's copy of it in the film — with the entity and transition it is
     * rendered with; null when nothing on screen renders it.
     */
    LivePose getLivePose(BaseValue property);

    record LivePose(BaseValueBasic value, IEntity entity, float transition)
    {}
}
