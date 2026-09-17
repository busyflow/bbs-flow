package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdLookTarget;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/** Mouse-only replay picker for Crowd Look Target keyframes. */
public class UICrowdLookTargetKeyframeFactory extends UIKeyframeFactory<String>
{
    private final UIButton target;
    private final UIToggle yaw;
    private final UIToggle pitch;
    private final UIToggle bodyYaw;
    private final UIToggle headYaw;

    public UICrowdLookTargetKeyframeFactory(Keyframe<String> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.target = new UIButton(IKey.EMPTY, (button) -> this.openTargetPicker());
        this.yaw = new UIToggle(IKey.constant("Yaw"), b -> this.updateControl(b.getValue(), null, null, null));
        this.pitch = new UIToggle(IKey.constant("Pitch"), b -> this.updateControl(null, b.getValue(), null, null));
        this.bodyYaw = new UIToggle(IKey.constant("Body yaw"), b -> this.updateControl(null, null, b.getValue(), null));
        this.headYaw = new UIToggle(IKey.constant("Head yaw"), b -> this.updateControl(null, null, null, b.getValue()));
        this.target.tooltip(IKey.constant("Pick a replay from the current scene"));
        this.updateTargetLabel();
        this.updateControls();
        this.scroll.add(this.target, UI.row(4, this.yaw, this.pitch), UI.row(4, this.bodyYaw, this.headYaw));
    }

    private Film getFilm()
    {
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);

        return panel == null ? null : panel.getData();
    }

    private void updateTargetLabel()
    {
        Film film = this.getFilm();
        Replay replay = film == null ? null : (Replay) film.replays.get(CrowdLookTarget.parse(this.keyframe.getValue()).replayId());

        this.target.label = IKey.constant(replay == null ? "Pick target replay..." : replay.getName());
    }

    private void openTargetPicker()
    {
        UIContext context = this.getContext();
        Film film = this.getFilm();

        if (context == null || film == null)
        {
            return;
        }

        context.replaceContextMenu((menu) ->
        {
            menu.autoKeys();
            menu.action(Icons.CLOSE, IKey.constant("No target"), Colors.NEGATIVE, () -> this.pickTarget(""));

            for (Replay replay : film.replays.getList())
            {
                String id = replay.getId();
                String label = replay.getName();

                menu.action(Icons.FILM, IKey.constant(label),
                    id.equals(CrowdLookTarget.parse(this.keyframe.getValue()).replayId()), () -> this.pickTarget(id));
            }
        });
    }

    private void pickTarget(String replayId)
    {
        CrowdLookTarget value = CrowdLookTarget.parse(this.keyframe.getValue());

        this.setValue(new CrowdLookTarget(replayId, value.yaw(), value.pitch(), value.bodyYaw(), value.headYaw()).encode());
        this.updateTargetLabel();
    }

    private void updateControl(Boolean yaw, Boolean pitch, Boolean bodyYaw, Boolean headYaw)
    {
        CrowdLookTarget value = CrowdLookTarget.parse(this.keyframe.getValue());

        this.setValue(new CrowdLookTarget(
            value.replayId(),
            yaw == null ? value.yaw() : yaw,
            pitch == null ? value.pitch() : pitch,
            bodyYaw == null ? value.bodyYaw() : bodyYaw,
            headYaw == null ? value.headYaw() : headYaw
        ).encode());
        this.updateControls();
    }

    private void updateControls()
    {
        CrowdLookTarget value = CrowdLookTarget.parse(this.keyframe.getValue());

        this.yaw.setValue(value.yaw());
        this.pitch.setValue(value.pitch());
        this.bodyYaw.setValue(value.bodyYaw());
        this.headYaw.setValue(value.headYaw());
    }

    @Override
    public void update()
    {
        super.update();
        this.updateTargetLabel();
        this.updateControls();
    }
}
