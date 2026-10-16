package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.film.utils.shader.UIShaderPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.KeyframeState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * The curve clip's properties: the clip's own fields, then the sections of the film editor's
 * {@link UIShaderPanel} &mdash; the world and the shader pack &mdash; where curves are made. The
 * tracks themselves are edited in the keyframe editor that replaces the timeline.
 */
public class UICurveClip extends UIClip<CurveClip>
{
    public UIKeyframeEditor keyframes;
    public UIButton edit;

    private UIShaderPanel shader;

    public UICurveClip(CurveClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("curve_keyframes");

        this.keyframes.view.context((menu) ->
        {
            menu.icon(MenuVerb.ADD, () -> this.shader.focusSearch()).label(UIKeys.CAMERA_PANELS_CURVE_ADD);

            UIKeyframeSheet sheet = this.keyframes.view.getGraph().getSheet(this.getContext().mouseX, this.getContext().mouseY);

            menu.icon(MenuVerb.REMOVE, () ->
            {
                this.clip.channels.removeChannel(sheet.channel);
                this.fillData();
            }).label(UIKeys.CAMERA_PANELS_CURVE_REMOVE).enabled(sheet != null);
        });

        this.edit = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) -> this.openKeyframes());
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());

        this.shader = ((UIClipsPanel) this.editor).filmPanel.shader;
        this.shader.attach(this);
    }

    private void openKeyframes()
    {
        this.editor.embedView(this.keyframes);
        this.keyframes.view.resetView();
        this.keyframes.view.getGraph().clearSelection();
    }

    /** A keyframe put on an empty track starts from the value the curve has now, like a new curve */
    private void addKeyframeSheet(KeyframeChannel<?> channel)
    {
        String id = channel.getId();
        int sheetColor = id.hashCode() & Colors.RGB;

        this.keyframes.view.addSheet(new UIKeyframeSheet(id, (IKey) () -> this.shader.channelLabel(id), sheetColor, channel, null)
            .seed(() -> this.startValue(channel)));
    }

    /* === Curves === */

    public boolean hasCurve(String channelId)
    {
        return this.clip.channels.get(channelId) instanceof KeyframeChannel<?>;
    }

    /**
     * Makes a track with one keyframe of the value the curve has now ({@link UIShaderPanel#startValue}),
     * so the picture doesn't change: at the playhead while it's over the clip, at the clip's start
     * otherwise. The keyframe editor opens, since the track is where the curve gets shaped from
     * then on.
     */
    public void createCurve(String channelId)
    {
        if (this.hasCurve(channelId))
        {
            return;
        }

        int tick = this.editor.getCursor() - this.clip.tick.get();
        KeyframeChannel channel = new KeyframeChannel(channelId, CurveClip.isColorChannelId(channelId) ? KeyframeFactories.COLOR : KeyframeFactories.DOUBLE);

        channel.insert(tick >= 0 && tick <= this.clip.duration.get() ? tick : 0, this.startValue(channel));
        this.clip.channels.addChannel(channel);
        this.fillData();

        if (!this.keyframes.hasParent())
        {
            this.openKeyframes();
        }
    }

    private Object startValue(KeyframeChannel<?> channel)
    {
        Object value = this.shader.startValue(channel.getId());

        return value == null ? channel.getFactory().createEmpty() : value;
    }

    public void removeCurve(String channelId)
    {
        if (this.clip.channels.get(channelId) instanceof KeyframeChannel<?> channel)
        {
            this.clip.channels.removeChannel(channel);
            this.fillData();
        }
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:curve"), this.edit));
        this.panels.add(this.shader.world, this.shader);
    }

    @Override
    public void render(UIContext context)
    {
        this.shader.update();

        super.render(context);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        KeyframeState state = this.keyframes.view.getSheets().isEmpty() ? null : this.keyframes.view.cacheState();
        this.keyframes.view.removeAllSheets();

        for (KeyframeChannel<?> channel : this.clip.channels.getAllKeyframeChannels())
        {
            this.addKeyframeSheet(channel);
        }
        if (state != null) this.keyframes.view.applyState(state);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        if (data.getString("embed").equals("curve"))
        {
            this.editor.embedView(this.keyframes);
        }

        super.applyUndoData(data);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        if (this.keyframes.hasParent())
        {
            data.putString("embed", "curve");
        }

        super.collectUndoData(data);
    }
}
