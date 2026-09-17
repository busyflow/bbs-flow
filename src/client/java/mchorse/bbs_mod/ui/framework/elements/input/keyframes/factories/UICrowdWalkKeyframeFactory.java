package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdWalk;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * Editor for one crowd walk waypoint. Drag its gizmo in the viewport to place where the crowd
 * should be; move the keyframe on the timeline to say when it gets there.
 */
public class UICrowdWalkKeyframeFactory extends UIKeyframeFactory<CrowdWalk>
{
    public final UIPropTransform transform = new UIPropTransform();

    private final UITrackpad x;
    private final UITrackpad y;
    private final UITrackpad z;
    private final UITrackpad stagger;
    private final UITrackpad spread;
    private final UIToggle run;
    private final UIToggle faceTravel;
    private final UIToggle terrain;
    private final UIToggle showPath;
    private final UIToggle showPoint;
    private final UIElement content;
    private final Transform edited = new Transform();
    private boolean syncing;

    public UICrowdWalkKeyframeFactory(Keyframe<CrowdWalk> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        if (keyframe.getValue() == null)
        {
            keyframe.setValue(new CrowdWalk());
        }

        keyframe.setDuration(0F);
        this.duration.setVisible(false);

        this.x = trackpad(-10000D, 10000D, 0.25D, v -> this.edit(p -> p.x = v.floatValue()));
        this.y = trackpad(-10000D, 10000D, 0.25D, v -> this.edit(p -> p.y = v.floatValue()));
        this.z = trackpad(-10000D, 10000D, 0.25D, v -> this.edit(p -> p.z = v.floatValue()));
        this.stagger = trackpad(0D, 1D, 0.05D, v -> this.edit(p -> p.stagger = v.floatValue()));
        this.spread = trackpad(0D, 1D, 0.05D, v -> this.edit(p -> p.spread = v.floatValue()));

        this.run = new UIToggle(IKey.constant("Run"), b -> this.edit(p -> p.run = b.getValue()));
        this.faceTravel = new UIToggle(IKey.constant("Face direction of travel"), b -> this.edit(p -> p.faceTravel = b.getValue()));
        this.terrain = new UIToggle(IKey.constant("Follow terrain (uphill/downhill)"), b -> this.edit(p -> p.terrainFollow = b.getValue()));
        this.showPath = new UIToggle(IKey.constant("Show walk path"), b -> this.edit(p -> p.showPath = b.getValue()));
        this.showPoint = new UIToggle(IKey.constant("Show timeline points"), b -> this.edit(p -> p.showPoint = b.getValue()));

        this.stagger.tooltip(IKey.constant("How ragged the crowd is about setting off.\n\n0 moves them as one block. 1 spreads them over about three ticks, so some are a step behind for the whole walk and arrive a step late."));
        this.spread.tooltip(IKey.constant("How much the formation loosens halfway. Exact shape at both ends."));

        this.transform.callbacks(
            () -> this.keyframe.preNotify(),
            () ->
            {
                this.syncFromTransform();
                this.keyframe.postNotify();
                this.display(false);
            },
            () -> this.keyframe.preNotify(IValueListener.FLAG_UNMERGEABLE)
        );
        this.transform.enableTranslateHotkeys();

        /* 2.5 pinned the gizmo to translate-axes here, since a walk point has no scale or
         * rotation for the other handles to drive. 2.6's gizmo is modeless: which handles show
         * is read straight off BBSSettings.gizmoShow* per element, and nothing may override it
         * for one editor. Forcing those settings would change them everywhere else too, so the
         * walk point is dragged with whatever handles the user has left visible. */

        this.content = UI.column(
            UI.label(IKey.constant("Crowd Walk Point")),
            UI.label(IKey.constant("The crowd stands here on this keyframe and walks to the next one.")),
            UI.labelRow(IKey.constant("Stagger"), this.stagger).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(IKey.constant("Spread"), this.spread),
            this.faceTravel.marginTop(UIConstants.SECTION_GAP),
            this.terrain,
            this.run,
            this.showPath.marginTop(UIConstants.SECTION_GAP),
            this.showPoint
        );
        this.scroll.add(this.content);

        /* Keep the gizmo controller attached to the editor but outside the scroll layout. */
        this.transform.setVisible(false);
        this.add(this.transform);
        this.display(true);
    }

    private UITrackpad trackpad(double min, double max, double increment, java.util.function.Consumer<Double> callback)
    {
        return new UITrackpad(callback).limit(min, max).increment(increment).values(increment, increment * 0.2D, increment * 4D);
    }

    public CrowdWalk getPath()
    {
        return this.keyframe.getValue();
    }

    public Keyframe<CrowdWalk> getMotionKeyframe()
    {
        return this.keyframe;
    }

    private void edit(java.util.function.Consumer<CrowdWalk> consumer)
    {
        if (this.syncing)
        {
            return;
        }

        this.keyframe.preNotify();
        consumer.accept(this.getPath());
        this.keyframe.postNotify();
        this.display(true);
    }

    private void syncFromTransform()
    {
        CrowdWalk point = this.getPath();
        Transform value = this.transform.getTransform();

        if (this.syncing || point == null || value == null)
        {
            return;
        }

        point.x = value.translate.x;
        point.y = value.translate.y;
        point.z = value.translate.z;
    }

    private void display(boolean transformToo)
    {
        CrowdWalk point = this.getPath();

        if (point == null)
        {
            return;
        }

        this.syncing = true;

        try
        {
            this.x.setValue(point.x);
            this.y.setValue(point.y);
            this.z.setValue(point.z);
            this.stagger.setValue(point.stagger);
            this.spread.setValue(point.spread);
            this.run.setValue(point.run);
            this.faceTravel.setValue(point.faceTravel);
            this.terrain.setValue(point.terrainFollow);
            this.showPath.setValue(point.showPath);
            this.showPoint.setValue(point.showPoint);
            this.content.resize();
            this.scroll.resize();

            if (transformToo)
            {
                this.edited.identity();
                this.edited.translate.set(point.x, point.y, point.z);
                this.transform.setTransform(this.edited);
            }
        }
        finally
        {
            this.syncing = false;
        }
    }

    @Override
    public void update()
    {
        super.update();
        /* Never reload the edited transform during a live gizmo gesture. Doing so
         * races the overlay handler and makes the handle appear pinned in place. */
        this.display(!this.transform.isEditing());
    }
}
