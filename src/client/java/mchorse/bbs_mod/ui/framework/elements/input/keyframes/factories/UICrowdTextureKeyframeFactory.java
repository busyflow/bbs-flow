package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.actions.crowd.CrowdTexture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/** Discrete crowd texture editor with explicit-texture and random-folder modes. */
public class UICrowdTextureKeyframeFactory extends UIKeyframeFactory<CrowdTexture>
{
    private final UIToggle random;
    private final UIToggle recursive;
    private final UIButton texture;
    private final UIButton folder;
    private final UIElement content;

    public UICrowdTextureKeyframeFactory(Keyframe<CrowdTexture> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        if (keyframe.getValue() == null)
        {
            keyframe.setValue(new CrowdTexture());
        }

        keyframe.setDuration(0F);
        this.duration.setVisible(false);

        this.random = new UIToggle(IKey.constant("Random textures from folder"), b ->
        {
            this.edit(v -> v.random = b.getValue());
            this.refresh();
        });
        this.recursive = new UIToggle(IKey.constant("Include texture subfolders"), b -> this.edit(v -> v.recursive = b.getValue()));
        this.texture = new UIButton(IKey.EMPTY, b -> this.pickTexture());
        this.folder = new UIButton(IKey.EMPTY, b -> this.pickFolder());
        this.content = UI.column(
            UI.label(IKey.constant("Crowd Texture")),
            UI.label(IKey.constant("The selected texture state begins exactly on this keyframe.")),
            this.random,
            this.texture,
            this.folder,
            this.recursive
        );

        this.scroll.add(this.content);
        this.refresh();
    }

    private void pickTexture()
    {
        UITexturePicker.open(this.getContext(), this.keyframe.getValue().texture, link ->
        {
            this.edit(v ->
            {
                v.texture = link;
                v.random = false;
            });
            this.refresh();
        });
    }

    private void pickFolder()
    {
        CrowdTexture value = this.keyframe.getValue();
        UIFolderOverlayPanel panel = new UIFolderOverlayPanel(
            IKey.constant("Crowd texture keyframe folder"),
            IKey.constant("Pick a folder containing PNG textures. Member choices stay deterministic."),
            folder ->
            {
                if (folder != null)
                {
                    this.edit(v ->
                    {
                        v.folder = folder;
                        v.random = true;
                    });
                    this.refresh();
                }
            }
        );

        if (value.folder != null)
        {
            panel.list.setPath(value.folder);
        }

        UIOverlay.addOverlay(this.getContext(), panel, 320, 0.8F);
    }

    private void edit(java.util.function.Consumer<CrowdTexture> consumer)
    {
        this.keyframe.preNotify();
        consumer.accept(this.keyframe.getValue());
        this.keyframe.postNotify();
    }

    private void refresh()
    {
        CrowdTexture value = this.keyframe.getValue();
        Link selectedTexture = value.texture;
        Link selectedFolder = value.folder;

        this.random.setValue(value.random);
        this.recursive.setValue(value.recursive);
        this.texture.label = IKey.constant(selectedTexture == null ? "Choose texture..." : selectedTexture.toString());
        this.folder.label = IKey.constant(selectedFolder == null ? "Choose random texture folder..." : selectedFolder.toString());
        this.texture.setVisible(!value.random);
        this.folder.setVisible(value.random);
        this.recursive.setVisible(value.random);
        this.content.resize();
        this.scroll.resize();
    }

    @Override
    public void update()
    {
        super.update();
        this.refresh();
    }
}
