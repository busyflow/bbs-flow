package mchorse.bbs_mod.ui.utils.pose;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.PoseManager;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Lightweight visual pose preset browser opened via Ctrl+Shift+V when a Pose or
 * Pose overlay keyframe is selected. Renders viewport-culled 3D previews backed
 * by {@link mchorse.bbs_mod.forms.FormThumbnailCache} so scrolling stays fast.
 */
public class UIPosePresetOverlayPanel extends UIOverlayPanel
{
    private static final int CARD_W = 92;
    private static final int CARD_H = 112;
    private static final int CARD_GAP = 6;
    private static final int CARD_LABEL_H = 20;
    private static final int MAX_BAKES_PER_FRAME = 4;

    private final Consumer<MapType> callback;
    private final String previewModelId;
    private final Link previewTexture;
    private final Map<String, List<PoseEntry>> entriesByGroup = new LinkedHashMap<>();
    private final List<PoseEntry> filtered = new ArrayList<>();

    private String activeGroup = "";
    private String searchQuery = "";

    private final UITextbox search;
    private final UIElement groupBar;
    private final UIPoseGrid grid;

    public UIPosePresetOverlayPanel(Form targetForm, Consumer<MapType> callback)
    {
        super(IKey.constant("Pose Presets"));

        this.callback = callback;

        String detectedGroup = "player/steve";
        String modelId = "player/steve";
        Link texture = null;

        if (targetForm instanceof ModelForm modelForm)
        {
            if (modelForm.model.get() != null && !modelForm.model.get().isEmpty())
            {
                modelId = modelForm.model.get();
            }

            texture = modelForm.texture.get();

            if (FormUtilsClient.getRenderer(modelForm) instanceof ModelFormRenderer renderer)
            {
                ModelInstance model = renderer.getModel();

                if (model != null && model.getPoseGroup() != null && !model.getPoseGroup().isEmpty())
                {
                    detectedGroup = model.getPoseGroup();
                }
            }
        }

        this.previewModelId = modelId;
        this.previewTexture = texture;

        this.loadGroups(detectedGroup);

        this.search = new UITextbox(120, (text) ->
        {
            this.searchQuery = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            this.rebuildFiltered();
        });
        this.search.placeholder(UIKeys.GENERAL_SEARCH);
        this.search.relative(this.content).xy(8, 6).w(1F, -16).h(20);

        this.groupBar = UI.row(4);
        this.groupBar.relative(this.content).xy(8, 30).w(1F, -16).h(18);

        boolean showGroupBar = this.entriesByGroup.size() > 1;
        this.groupBar.setVisible(showGroupBar);

        if (showGroupBar)
        {
            this.rebuildGroupButtons();
        }

        int gridTop = showGroupBar ? 54 : 32;
        this.grid = new UIPoseGrid();
        this.grid.relative(this.content).xy(8, gridTop).w(1F, -16).h(1F, -(gridTop + 20));

        UIElement hint = UI.label(IKey.constant("Click to apply  •  Shift+Click to preview without closing"))
            .color(Colors.LIGHTER_GRAY, true)
            .labelAnchor(0.5F, 0.5F);
        hint.relative(this.content).x(8).y(1F, -18).w(1F, -16).h(16);

        this.content.add(this.search, this.groupBar, this.grid, hint);

        this.rebuildFiltered();
    }

    private void loadGroups(String primaryGroup)
    {
        Set<String> candidates = new LinkedHashSet<>();

        if (primaryGroup != null && !primaryGroup.isEmpty())
        {
            candidates.add(primaryGroup);
        }

        candidates.add("player/steve");
        candidates.add("player/steve_gentle");
        candidates.add("player/alex");

        for (String group : candidates)
        {
            MapType data = PoseManager.INSTANCE.getData(group);

            if (data == null || data.isEmpty())
            {
                continue;
            }

            List<PoseEntry> list = new ArrayList<>();

            for (String key : data.keys())
            {
                if (data.has(key) && data.get(key).isMap())
                {
                    String label = PoseManager.INSTANCE.getLabel(group, key);
                    list.add(new PoseEntry(group, key, label, data.getMap(key)));
                }
            }

            list.sort(Comparator.comparing(e -> e.label.toLowerCase(Locale.ROOT)));

            if (!list.isEmpty())
            {
                this.entriesByGroup.put(group, list);
            }
        }

        if (!this.entriesByGroup.isEmpty())
        {
            this.activeGroup = this.entriesByGroup.containsKey(primaryGroup)
                ? primaryGroup
                : this.entriesByGroup.keySet().iterator().next();
        }
    }

    private void rebuildGroupButtons()
    {
        this.groupBar.removeAll();

        for (String group : this.entriesByGroup.keySet())
        {
            String shortName = group.contains("/") ? group.substring(group.lastIndexOf('/') + 1) : group;
            int count = this.entriesByGroup.get(group).size();
            UIButton button = new UIButton(IKey.constant(shortName + " (" + count + ")"), (b) ->
            {
                this.activeGroup = group;
                this.rebuildGroupButtons();
                this.rebuildFiltered();
                this.groupBar.resize();
            });

            if (group.equals(this.activeGroup))
            {
                button.color(BBSSettings.primaryColor.get());
            }

            button.h(18);
            this.groupBar.add(button);
        }
    }

    private void rebuildFiltered()
    {
        this.filtered.clear();

        List<PoseEntry> source = this.entriesByGroup.get(this.activeGroup);

        if (source != null)
        {
            for (PoseEntry entry : source)
            {
                if (this.searchQuery.isEmpty() || entry.label.toLowerCase(Locale.ROOT).contains(this.searchQuery))
                {
                    this.filtered.add(entry);
                }
            }
        }

        if (this.grid != null)
        {
            this.grid.updateScroll();
        }
    }

    private ModelForm getOrCreatePreviewForm(PoseEntry entry)
    {
        if (entry.previewForm == null)
        {
            ModelForm form = new ModelForm();
            String modelToUse = this.previewModelId;

            if (entry.group.startsWith("player/") && !modelToUse.startsWith("player/"))
            {
                modelToUse = entry.group;
            }

            form.model.set(modelToUse);

            if (this.previewTexture != null && modelToUse.equals(this.previewModelId))
            {
                form.texture.set(this.previewTexture);
            }

            form.pose.get().fromData(entry.poseData);
            entry.previewForm = form;
        }

        return entry.previewForm;
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.close();

            return true;
        }

        return super.subKeyPressed(context);
    }

    private static final class PoseEntry
    {
        final String group;
        final String key;
        final String label;
        final MapType poseData;
        ModelForm previewForm;

        PoseEntry(String group, String key, String label, MapType poseData)
        {
            this.group = group;
            this.key = key;
            this.label = label;
            this.poseData = poseData;
        }
    }

    private class UIPoseGrid extends UIElement
    {
        private final Scroll scroll;
        private int columns = 1;

        public UIPoseGrid()
        {
            this.scroll = new Scroll(this.area);
            this.scroll.scrollSpeed = 28;
        }

        public void updateScroll()
        {
            int w = Math.max(CARD_W, this.area.w - 8);
            this.columns = Math.max(1, (w + CARD_GAP) / (CARD_W + CARD_GAP));
            int rows = (filtered.size() + this.columns - 1) / this.columns;

            this.scroll.scrollSize = Math.max(0, rows * (CARD_H + CARD_GAP));
            this.scroll.clamp();
        }

        @Override
        public void resize()
        {
            super.resize();
            this.updateScroll();
        }

        private int getCardX(int col)
        {
            int totalW = this.columns * CARD_W + (this.columns - 1) * CARD_GAP;
            int offsetX = Math.max(0, (this.area.w - 6 - totalW) / 2);

            return this.area.x + offsetX + col * (CARD_W + CARD_GAP);
        }

        private int getCardY(int row)
        {
            return this.area.y + row * (CARD_H + CARD_GAP) - (int) this.scroll.getScroll();
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (this.scroll.mouseClicked(context))
            {
                return true;
            }

            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                for (int i = 0; i < filtered.size(); i++)
                {
                    int col = i % this.columns;
                    int row = i / this.columns;
                    int cx = this.getCardX(col);
                    int cy = this.getCardY(row);

                    if (cy + CARD_H < this.area.y || cy > this.area.ey())
                    {
                        continue;
                    }

                    if (context.mouseX >= cx && context.mouseX < cx + CARD_W
                        && context.mouseY >= cy && context.mouseY < cy + CARD_H)
                    {
                        PoseEntry entry = filtered.get(i);

                        if (callback != null)
                        {
                            callback.accept((MapType) entry.poseData.copy());
                        }

                        UIUtils.playClick();

                        if (!Window.isShiftPressed())
                        {
                            UIPosePresetOverlayPanel.this.close();
                        }

                        return true;
                    }
                }
            }

            return super.subMouseClicked(context);
        }

        @Override
        public boolean subMouseScrolled(UIContext context)
        {
            return this.scroll.mouseScroll(context);
        }

        @Override
        public boolean subMouseReleased(UIContext context)
        {
            this.scroll.mouseReleased(context);

            return super.subMouseReleased(context);
        }

        @Override
        public void render(UIContext context)
        {
            this.scroll.drag(context);
            this.area.render(context.batcher, BBSSettings.deepSurface());

            if (filtered.isEmpty())
            {
                FontRenderer font = context.batcher.getFont();
                String msg = entriesByGroup.isEmpty()
                    ? "No saved poses found (right-click body parts -> Poses -> Save)"
                    : "No matching poses";
                int tw = font.getWidth(msg);

                context.batcher.textShadow(msg, this.area.mx() - tw / 2, this.area.my() - font.getHeight() / 2, Colors.LIGHTER_GRAY);

                return;
            }

            context.batcher.clip(this.area, context);

            FontRenderer font = context.batcher.getFont();
            int bakesThisFrame = 0;

            for (int i = 0; i < filtered.size(); i++)
            {
                int col = i % this.columns;
                int row = i / this.columns;
                int cx = this.getCardX(col);
                int cy = this.getCardY(row);

                if (cy + CARD_H < this.area.y || cy > this.area.ey())
                {
                    continue;
                }

                boolean hover = this.area.isInside(context)
                    && context.mouseX >= cx && context.mouseX < cx + CARD_W
                    && context.mouseY >= cy && context.mouseY < cy + CARD_H;

                int bg = hover ? BBSSettings.raisedSurface() : BBSSettings.baseSurface();
                context.batcher.box(cx, cy, cx + CARD_W, cy + CARD_H, bg);

                if (hover)
                {
                    context.batcher.outline(cx, cy, cx + CARD_W, cy + CARD_H, BBSSettings.primaryColor(Colors.A100));
                }
                else
                {
                    context.batcher.outline(cx, cy, cx + CARD_W, cy + CARD_H, Colors.setA(Colors.WHITE, 0.12F));
                }

                PoseEntry entry = filtered.get(i);

                if (entry.previewForm != null || bakesThisFrame < MAX_BAKES_PER_FRAME)
                {
                    if (entry.previewForm == null)
                    {
                        bakesThisFrame++;
                    }

                    ModelForm form = getOrCreatePreviewForm(entry);

                    FormUtilsClient.renderPreview(
                        form,
                        context,
                        cx + 4,
                        cy + 4,
                        cx + CARD_W - 4,
                        cy + CARD_H - CARD_LABEL_H
                    );
                }

                String label = font.limitToWidth(entry.label, CARD_W - 8);
                int lw = font.getWidth(label);
                int lx = cx + (CARD_W - lw) / 2;
                int ly = cy + CARD_H - CARD_LABEL_H + (CARD_LABEL_H - font.getHeight()) / 2;

                context.batcher.textShadow(label, lx, ly, hover ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.85F));
            }

            this.scroll.renderScrollbar(context.batcher);
            context.batcher.unclip(context);

            super.render(context);
        }
    }
}
