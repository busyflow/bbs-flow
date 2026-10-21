package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.utils.SplineKeyframeEditor;
import mchorse.bbs_mod.ui.utils.UISplinePointsEditor;
import org.joml.Vector3f;

import mchorse.bbs_mod.cubic.spline.*;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.forms.editors.panels.rig.UIRigSplineSection;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.*;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import mchorse.bbs_mod.ui.utils.UISplineControlFields;
import mchorse.bbs_mod.ui.utils.UISplinePointList;

/** One shared key time; chain/point selection edits elements of the compound snapshot. */
public class UISplineKeyframeFactory extends UIKeyframeFactory<SplineControls> implements SplineKeyframeEditor
{
    @Override
    public Transform getGizmoTransform(SplineControls value)
    {
        if (this.pointEditor.pointId().isEmpty()) return null;
        SplineControl control = this.control(value);
        value.controls.put(this.chainId, control);
        return control.point(this.pointEditor.pointId());
    }

    private final ModelForm form;
    public final UIStringList chains;
    public final UISplinePointList points;
    public final UIPropTransform transform;
    private final UISliderTrackpad influence;
    private final UITrackpad progress;
    private final UITrackpad twist;
    private String chainId = "";
    private final UISplinePointsEditor pointEditor;

    public UISplineKeyframeFactory(UITrackValue<SplineControls> track, UIKeyframes editor)
    {
        super(track, editor);
        UIKeyframeSheet sheet = track.sheet;
        this.form = sheet != null && sheet.form instanceof ModelForm model ? model : null;
        this.chains = new UIStringList(values -> this.select(values.isEmpty() ? "" : values.get(0), ""))
        {
            @Override protected String elementToString(UIContext context, int index, String id)
            {
                SplineIK chain = UISplineKeyframeFactory.this.form == null ? null : UISplineKeyframeFactory.this.form.splines.get(id);
                return chain == null ? id : chain.tip.get();
            }
        };
        this.chains.background().h(UIConstants.LIST_ITEM_HEIGHT * 6).expand();
        var fields = new UISplineControlFields(this::edit);
        this.influence = fields.influence;
        this.progress = fields.progress;
        this.twist = fields.twist;
        this.transform = new UIKeyframePropTransform()
        {
            @Override protected UIKeyframes getKeyframes() { return editor; }
            @Override protected void applyToSelection(Consumer<Transform> consumer)
            {
                if (!UISplineKeyframeFactory.this.pointEditor.pointId().isEmpty())
                    UISplineKeyframeFactory.this.edit(control ->
                    {
                        for (String id : UISplineKeyframeFactory.this.pointEditor.selected()) consumer.accept(control.point(id));
                    });
            }

        };
        this.pointEditor = new UISplinePointsEditor(this::source,
            id -> this.control(this.getDisplayValue()).point(id), this.transform, Vector3f::new)
        {
            @Override protected void editStructure(Consumer<ValueSplinePoints> edit)
            {
                editor.editForm(UISplineKeyframeFactory.this.form, () -> edit.accept(UISplineKeyframeFactory.this.source().points()));
            }
            @Override protected void editPositions(Consumer<SplinePositions> edit)
            {
                UISplineKeyframeFactory.this.edit(control -> edit.accept(control.points));
            }
        };
        this.points = this.pointEditor.points;
        this.transform.noScale().setRotationVisible(false);
        this.transform.enableHotkeys(() -> !this.pointEditor.pointId().isEmpty(), op -> op == TransformOp.TRANSLATE);
        this.scroll.add(UI.column(this.chains,
            UI.labelRow(UIRigSplineSection.key("influence"), this.influence),
            UI.labelRow(UIRigSplineSection.key("progress_short"), this.progress),
            UI.labelRow(UIRigSplineSection.key("twist"), this.twist), this.pointEditor).expand());
        List<String> ids = new ArrayList<>();
        if (this.form != null) for (SplineIK chain : this.form.splines.getAllTyped()) ids.add(chain.getId());
        this.chains.setList(ids);
        this.select(ids.isEmpty() ? "" : ids.get(0), "");
    }

    private SplineControl control(SplineControls value)
    {
        SplineControl stored = value.controls.get(this.chainId);
        SplineControl defaults = this.form == null ? null : this.form.splineIK.getOriginalValue().controls.get(this.chainId);
        if (defaults == null) defaults = new SplineControl();
        return stored == null ? defaults.copy() : stored.withDefaults(defaults);
    }

    private void edit(Consumer<SplineControl> edit)
    {
        if (this.chainId.isEmpty()) return;
        this.track.edit(selected ->
        {
            SplineControls value = (SplineControls) selected;
            SplineControl control = this.control(value);
            edit.accept(control);
            value.controls.put(this.chainId, control);
        });
    }

    public void select(String chainId, String pointId)
    {
        if (this.transform.isEditing()) this.transform.endGesture();
        this.chainId = chainId;
        SplineIK chain = this.form == null ? null : this.form.splines.get(chainId);
        List<String> ids = new ArrayList<>();
        if (chain != null) for (SplinePoint point : chain.points.getAllTyped()) ids.add(point.getId());
        this.chains.setCurrent(chainId);
        this.pointEditor.refresh();
        this.pointEditor.select(ids.contains(pointId) ? pointId : ids.isEmpty() ? "" : ids.get(0));
        SplineControl control = this.control(this.getDisplayValue());
        this.influence.setValue(control.influence);
        this.progress.setValue(control.progress);
        this.twist.setValue(control.twist);
        this.influence.setEnabled(chain != null);
        this.progress.setEnabled(chain != null);
        this.twist.setEnabled(chain != null);
        this.transform.setVisible(!this.pointEditor.pointId().isEmpty());
        this.transform.setTransform(this.pointEditor.pointId().isEmpty() ? null : control.point(this.pointEditor.pointId()));
        this.scroll.resize();
    }

    @Override public UISplinePointsEditor pointEditor() { return this.pointEditor; }
    @Override public SplineSource source() { return this.form == null ? null : this.form.splines.get(this.chainId); }
    @Override public void selectPoint(String path)
    {
        String[] parts = TrackId.parse(path).subject().split("/");
        if (!this.chainId.equals(parts[1])) this.select(parts[1], "");
        this.pointEditor.selectInViewport(parts[3], Window.isCtrlPressed() || Window.isShiftPressed());
    }

    @Override
    public void render(UIContext context)
    {
        SplineControl control = this.control(this.getDisplayValue());
        if (!this.influence.isUserEditing()) this.influence.setValue(control.influence);
        if (!this.progress.isUserEditing()) this.progress.setValue(control.progress);
        if (!this.twist.isUserEditing()) this.twist.setValue(control.twist);
        if (!this.transform.isUserEditing() && !this.pointEditor.pointId().isEmpty()) this.transform.setTransform(control.point(this.pointEditor.pointId()));
        super.render(context);
    }
}
