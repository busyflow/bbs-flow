package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIListGrid;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.ui.utils.presets.UIPresetsOverlayPanel;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.presets.PresetManager;
import net.minecraft.client.render.DiffuseLighting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context-aware keyframe preset picker: filters presets to match the active
 * track type and, for pose / pose_overlay tracks, renders live 3D previews
 * using the current replay's form (with looping animation on hover for
 * multi-keyframe pose presets).
 */
public class UIKeyframePresetsOverlayPanel extends UIPresetsOverlayPanel
{
    private static final float TICKS_PER_SECOND = 20F;

    private final String targetFactoryKey;
    private final boolean poseMode;
    private final Form templateForm;

    private final Map<String, MapType> dataCache = new HashMap<>();
    private final Map<String, PosePresetEntry> presetCache = new HashMap<>();

    private String hoveredPresetId;
    private long hoverStartMs;

    public UIKeyframePresetsOverlayPanel(UICopyPasteController controller, UIKeyframes keyframes, UIKeyframeSheet targetSheet, int mouseX, int mouseY)
    {
        super(controller, mouseX, mouseY);

        IKeyframeFactory targetFactory = targetSheet != null ? targetSheet.channel.getFactory() : null;

        this.targetFactoryKey = targetFactory != null ? CollectionUtils.getKey(KeyframeFactories.FACTORIES, targetFactory) : null;
        this.poseMode = targetFactory == KeyframeFactories.POSE;
        this.templateForm = this.poseMode ? resolveTemplateForm(targetSheet, keyframes) : null;

        if (this.poseMode)
        {
            this.list.list.scroll.scrollItemSize = 32;
            this.list.list.preview = this::renderPosePreview;
            this.list.views();
        }

        this.refreshList();
    }

    public boolean isPoseMode()
    {
        return this.poseMode;
    }

    @Override
    public int getPreferredWidth()
    {
        return this.poseMode ? 440 : 240;
    }

    @Override
    protected void refreshList()
    {
        if (this.presetCache == null)
        {
            return;
        }

        this.dataCache.clear();
        this.presetCache.clear();
        this.hoveredPresetId = null;
        this.list.list.clear();

        List<String> entries = this.controller.manager.listDirectory(this.cwd);

        if (this.targetFactoryKey == null)
        {
            this.addValues(entries);

            return;
        }

        List<String> filtered = new ArrayList<>();

        for (String entry : entries)
        {
            if ("..".equals(entry))
            {
                filtered.add(entry);
            }
            else if (entry.endsWith("/"))
            {
                String folderName = entry.substring(0, entry.length() - 1);
                String subCwd = PresetManager.joinRelative(this.cwd, folderName);

                if (this.folderHasMatchingPreset(subCwd))
                {
                    filtered.add(entry);
                }
            }
            else
            {
                String id = PresetManager.joinRelative(this.cwd, entry);
                MapType data = this.loadCached(id);

                if (presetMatchesFactory(data, this.targetFactoryKey))
                {
                    filtered.add(entry);
                }
            }
        }

        this.addValues(filtered);
    }

    private MapType loadCached(String id)
    {
        if (this.dataCache.containsKey(id))
        {
            return this.dataCache.get(id);
        }

        MapType data = this.controller.manager.load(id);

        this.dataCache.put(id, data);

        return data;
    }

    private boolean folderHasMatchingPreset(String relativePath)
    {
        for (String entry : this.controller.manager.listDirectory(relativePath))
        {
            if ("..".equals(entry))
            {
                continue;
            }

            if (entry.endsWith("/"))
            {
                String sub = PresetManager.joinRelative(relativePath, entry.substring(0, entry.length() - 1));

                if (this.folderHasMatchingPreset(sub))
                {
                    return true;
                }
            }
            else
            {
                String id = PresetManager.joinRelative(relativePath, entry);

                if (presetMatchesFactory(this.loadCached(id), this.targetFactoryKey))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean presetMatchesFactory(MapType data, String factoryKey)
    {
        if (data == null || factoryKey == null)
        {
            return false;
        }

        for (String key : data.keys())
        {
            MapType track = data.getMap(key);

            if (factoryKey.equals(track.getString("type")))
            {
                return true;
            }
        }

        return false;
    }

    private static Form resolveTemplateForm(UIKeyframeSheet targetSheet, UIKeyframes keyframes)
    {
        Form owner = getSheetForm(targetSheet);

        if (owner == null && keyframes != null)
        {
            for (UIKeyframeSheet sheet : keyframes.getSheets())
            {
                owner = getSheetForm(sheet);

                if (owner != null)
                {
                    break;
                }
            }
        }

        if (owner != null)
        {
            return owner;
        }

        ModelForm fallback = new ModelForm();
        List<String> keys = BBSModClient.getModels().getAvailableKeys();

        if (!keys.isEmpty())
        {
            fallback.model.set(keys.contains("steve") ? "steve" : keys.get(0));
        }

        return fallback;
    }

    private static Form getSheetForm(UIKeyframeSheet sheet)
    {
        if (sheet == null)
        {
            return null;
        }

        if (sheet.form != null)
        {
            return sheet.form;
        }

        return sheet.property != null ? FormUtils.getForm(sheet.property) : null;
    }

    private void renderPosePreview(UIContext context, String item, int x, int y, int size)
    {
        if ("..".equals(item))
        {
            context.batcher.icon(Icons.ARROW_UP, Colors.WHITE, x + size / 2F, y + size / 2F, 0.5F, 0.5F);

            return;
        }

        if (item.endsWith("/"))
        {
            context.batcher.icon(Icons.FOLDER, Colors.WHITE, x + size / 2F, y + size / 2F, 0.5F, 0.5F);

            return;
        }

        String id = PresetManager.joinRelative(this.cwd, item);
        PosePresetEntry entry = this.getPoseEntry(id);

        if (entry == null || entry.previewForm == null)
        {
            context.batcher.icon(Icons.POSE, Colors.WHITE, x + size / 2F, y + size / 2F, 0.5F, 0.5F);

            return;
        }

        boolean hovered = this.isItemHovered(context, x, y, size);

        if (entry.animated)
        {
            if (hovered)
            {
                if (!id.equals(this.hoveredPresetId))
                {
                    this.hoveredPresetId = id;
                    this.hoverStartMs = System.currentTimeMillis();
                }

                float duration = entry.maxTick - entry.minTick;
                float elapsed = (System.currentTimeMillis() - this.hoverStartMs) / 1000F;
                float tick = entry.minTick + (elapsed * TICKS_PER_SECOND) % duration;

                entry.applyAtTick(tick);
            }
            else if (id.equals(this.hoveredPresetId))
            {
                this.hoveredPresetId = null;
                entry.applyAtTick(entry.minTick);
            }
        }

        context.batcher.flush();
        DiffuseLighting.enableGuiDepthLighting();

        try
        {
            FormUtilsClient.renderPreview(entry.previewForm, context, x, y, x + size, y + size);
        }
        finally
        {
            context.batcher.flush();
            DiffuseLighting.disableGuiDepthLighting();
        }

        if (entry.animated)
        {
            int color = hovered ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.55F);

            context.batcher.icon(Icons.PLAY, color, x + size - 1, y + 1, 1F, 0F);
        }
    }

    private boolean isItemHovered(UIContext context, int x, int y, int size)
    {
        UIListGrid<String> grid = this.list.getGrid();

        if (grid != null && grid.isVisible())
        {
            return grid.area.isInside(context)
                && context.mouseX >= x - 4 && context.mouseX < x + size + 4
                && context.mouseY >= y - 4 && context.mouseY < y + size + 18;
        }

        return this.list.list.area.isInside(context)
            && context.mouseX >= this.list.list.area.x && context.mouseX < this.list.list.area.ex()
            && context.mouseY >= y - 2 && context.mouseY < y + size + 2;
    }

    private PosePresetEntry getPoseEntry(String id)
    {
        if (this.presetCache.containsKey(id))
        {
            return this.presetCache.get(id);
        }

        MapType data = this.loadCached(id);
        PosePresetEntry entry = PosePresetEntry.create(data, this.templateForm);

        this.presetCache.put(id, entry);

        return entry;
    }

    private static class PosePresetEntry
    {
        public final Form previewForm;
        public final KeyframeChannel<Pose> poseChannel;
        public final KeyframeChannel<Pose> overlayChannel;
        public final float minTick;
        public final float maxTick;
        public final boolean animated;

        private PosePresetEntry(Form previewForm, KeyframeChannel<Pose> poseChannel, KeyframeChannel<Pose> overlayChannel, float minTick, float maxTick)
        {
            this.previewForm = previewForm;
            this.poseChannel = poseChannel;
            this.overlayChannel = overlayChannel;
            this.minTick = minTick;
            this.maxTick = maxTick;
            this.animated = maxTick > minTick + 1e-4F;

            this.applyAtTick(this.minTick);
        }

        @SuppressWarnings("unchecked")
        public static PosePresetEntry create(MapType data, Form templateForm)
        {
            if (data == null || templateForm == null)
            {
                return null;
            }

            Map<String, UIKeyframes.PastedKeyframes> parsed = UIKeyframes.parseKeyframes(data);
            UIKeyframes.PastedKeyframes primary = null;
            UIKeyframes.PastedKeyframes secondary = null;

            for (Map.Entry<String, UIKeyframes.PastedKeyframes> entry : parsed.entrySet())
            {
                if (entry.getValue().factory != KeyframeFactories.POSE || entry.getValue().keyframes.isEmpty())
                {
                    continue;
                }

                String key = entry.getKey();

                if (key.equals("pose") || key.endsWith("/pose"))
                {
                    if (primary != null && secondary == null)
                    {
                        secondary = primary;
                    }

                    primary = entry.getValue();
                }
                else if (primary == null)
                {
                    primary = entry.getValue();
                }
                else if (secondary == null)
                {
                    secondary = entry.getValue();
                }
            }

            if (primary == null)
            {
                return null;
            }

            KeyframeChannel<Pose> poseChannel = buildChannel("pose", primary);
            KeyframeChannel<Pose> overlayChannel = secondary != null ? buildChannel("pose_overlay", secondary) : null;

            float minTick = Float.MAX_VALUE;
            float maxTick = -Float.MAX_VALUE;

            for (Keyframe<Pose> kf : poseChannel.getKeyframes())
            {
                minTick = Math.min(minTick, kf.getTick());
                maxTick = Math.max(maxTick, kf.getTick());
            }

            if (overlayChannel != null)
            {
                for (Keyframe<Pose> kf : overlayChannel.getKeyframes())
                {
                    minTick = Math.min(minTick, kf.getTick());
                    maxTick = Math.max(maxTick, kf.getTick());
                }
            }

            if (minTick == Float.MAX_VALUE)
            {
                minTick = 0F;
                maxTick = 0F;
            }

            Form copy = FormUtils.copy(templateForm);

            if (copy instanceof IPosedForm posed)
            {
                posed.getPose().set(new Pose());
                posed.getPoseOverlay().set(new Pose());
            }

            return new PosePresetEntry(copy, poseChannel, overlayChannel, minTick, maxTick);
        }

        private static KeyframeChannel<Pose> buildChannel(String id, UIKeyframes.PastedKeyframes pasted)
        {
            KeyframeChannel<Pose> channel = new KeyframeChannel<>(id, KeyframeFactories.POSE);

            for (Keyframe kf : pasted.keyframes)
            {
                if (kf.getValue() instanceof Pose pose)
                {
                    int index = channel.insert(kf.getTick(), pose.copy());
                    Keyframe<Pose> inserted = channel.get(index);

                    inserted.copy(kf);
                }
            }

            return channel;
        }

        public void applyAtTick(float tick)
        {
            if (!(this.previewForm instanceof IPosedForm posed))
            {
                return;
            }

            if (this.poseChannel != null && !this.poseChannel.isEmpty())
            {
                Pose pose = this.poseChannel.interpolate(tick);

                if (pose != null)
                {
                    posed.getPose().set(pose.copy());
                }
            }

            if (this.overlayChannel != null && !this.overlayChannel.isEmpty())
            {
                Pose overlay = this.overlayChannel.interpolate(tick);

                if (overlay != null)
                {
                    posed.getPoseOverlay().set(overlay.copy());
                }
            }
        }
    }
}
