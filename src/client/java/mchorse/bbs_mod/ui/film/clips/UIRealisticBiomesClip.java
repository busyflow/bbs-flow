package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.RealisticBiomesClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.KeyframeState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.clips.Clips;

/**
 * Editor panel for {@link RealisticBiomesClip}: press Edit Keyframes to open the keyframe timeline
 * for Realistic Biomes atmospheric parameters (starting with Ground fog opacity).
 */
public class UIRealisticBiomesClip extends UIClip<RealisticBiomesClip>
{
    public UIKeyframeEditor keyframes;
    public UIButton edit;

    public UIRealisticBiomesClip(RealisticBiomesClip clip, IUIClipsDelegate editor)
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
        this.keyframes.setUndoId("realistic_biomes_keyframes");

        this.edit = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) -> this.openKeyframes());
        this.registerEditKeys(this.edit, this.keyframes);
    }

    private void openKeyframes()
    {
        this.editor.embedView(this.keyframes);
        this.keyframes.view.resetView();
        this.keyframes.view.getGraph().clearSelection();
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(IKey.constant("Realistic Biomes"), this.edit, this.keyFields(this.keyframes)));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        KeyframeState state = this.keyframes.view.getSheets().isEmpty() ? null : this.keyframes.view.cacheState();
        this.keyframes.view.removeAllSheets();

        this.keyframes.view.addSheet(new UIKeyframeSheet(
            RealisticBiomesClip.GROUND_FOG_OPACITY,
            IKey.constant("Ground fog opacity"),
            0x2ecc71,
            this.clip.groundFogOpacity,
            null
        ).seed(() -> 0.5D));

        if (state != null)
        {
            this.keyframes.view.applyState(state);
        }
    }

    @Override
    public void applyUndoData(MapType data)
    {
        if (data.getString("embed").equals("realistic_biomes") && !this.keyframes.hasParent())
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
            data.putString("embed", "realistic_biomes");
        }

        super.collectUndoData(data);
    }
}
