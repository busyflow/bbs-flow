package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Every field of a bone's limits, built the same for the rig and for the constraint track's
 * keyframes. The locks first, a grid laid out like the transform editor's rows — move, scale,
 * rotation, each across X, Y, Z — so a lock sits where its channel's number does; then the
 * rotation limits, an axis a row: its switch and the two angles, grey while the axis isn't limited;
 * then the stiffness of the three, shown only where an IK chain runs through the bone — the one
 * step that reads it.
 */
public class UIBoneConstraintFields
{
    public final UIIcon lockTranslateX;
    public final UIIcon lockTranslateY;
    public final UIIcon lockTranslateZ;
    public final UIIcon lockScaleX;
    public final UIIcon lockScaleY;
    public final UIIcon lockScaleZ;
    public final UIIcon lockX;
    public final UIIcon lockY;
    public final UIIcon lockZ;
    public final UIToggle limitX;
    public final UIToggle limitY;
    public final UIToggle limitZ;
    public final UITrackpad minX;
    public final UITrackpad minY;
    public final UITrackpad minZ;
    public final UITrackpad maxX;
    public final UITrackpad maxY;
    public final UITrackpad maxZ;
    public final UISliderTrackpad stiffnessX;
    public final UISliderTrackpad stiffnessY;
    public final UISliderTrackpad stiffnessZ;

    private final UIElement stiffnessTitle;
    private final UIElement stiffnessRow;

    private final Supplier<BoneConstraint> current;
    private final Consumer<Consumer<BoneConstraint>> edit;
    private final Runnable changed;

    /**
     * @param current the limits the fields show, read live by the lock icons
     * @param edit writes a change to the limits as one edit
     * @param changed called after a switch flips, for the host to refill and re-lay the fields
     */
    public UIBoneConstraintFields(Supplier<BoneConstraint> current, Consumer<Consumer<BoneConstraint>> edit, Runnable changed)
    {
        this.current = current;
        this.edit = edit;
        this.changed = changed;

        this.lockTranslateX = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_TRANSLATE, UIKeys.GENERAL_X, (c) -> c.lockTranslateX, (c, v) -> c.lockTranslateX = v);
        this.lockTranslateY = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_TRANSLATE, UIKeys.GENERAL_Y, (c) -> c.lockTranslateY, (c, v) -> c.lockTranslateY = v);
        this.lockTranslateZ = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_TRANSLATE, UIKeys.GENERAL_Z, (c) -> c.lockTranslateZ, (c, v) -> c.lockTranslateZ = v);
        this.lockScaleX = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_SCALE, UIKeys.GENERAL_X, (c) -> c.lockScaleX, (c, v) -> c.lockScaleX = v);
        this.lockScaleY = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_SCALE, UIKeys.GENERAL_Y, (c) -> c.lockScaleY, (c, v) -> c.lockScaleY = v);
        this.lockScaleZ = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_SCALE, UIKeys.GENERAL_Z, (c) -> c.lockScaleZ, (c, v) -> c.lockScaleZ = v);
        this.lockX = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK, UIKeys.GENERAL_X, (c) -> c.lockX, (c, v) -> c.lockX = v);
        this.lockY = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK, UIKeys.GENERAL_Y, (c) -> c.lockY, (c, v) -> c.lockY = v);
        this.lockZ = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK, UIKeys.GENERAL_Z, (c) -> c.lockZ, (c, v) -> c.lockZ = v);

        this.limitX = this.limit(UIKeys.GENERAL_X, (c, v) -> c.limitX = v);
        this.limitY = this.limit(UIKeys.GENERAL_Y, (c, v) -> c.limitY = v);
        this.limitZ = this.limit(UIKeys.GENERAL_Z, (c, v) -> c.limitZ = v);

        this.minX = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_X, Colors.RED, (c, v) -> c.minX = v);
        this.minY = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Y, Colors.GREEN, (c, v) -> c.minY = v);
        this.minZ = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Z, Colors.BLUE, (c, v) -> c.minZ = v);
        this.maxX = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_X, Colors.RED, (c, v) -> c.maxX = v);
        this.maxY = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Y, Colors.GREEN, (c, v) -> c.maxY = v);
        this.maxZ = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Z, Colors.BLUE, (c, v) -> c.maxZ = v);

        this.stiffnessX = this.stiffness(UIKeys.GENERAL_X, Colors.RED, (c, v) -> c.stiffnessX = v);
        this.stiffnessY = this.stiffness(UIKeys.GENERAL_Y, Colors.GREEN, (c, v) -> c.stiffnessY = v);
        this.stiffnessZ = this.stiffness(UIKeys.GENERAL_Z, Colors.BLUE, (c, v) -> c.stiffnessZ = v);

        this.stiffnessTitle = UI.label(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_STIFFNESS_TITLE);
        this.stiffnessRow = UI.row(this.stiffnessX, this.stiffnessY, this.stiffnessZ);
    }

    /**
     * A lock of the grid: a cell as wide as a pad of the transform editor's row, on the pads'
     * surface, with a padlock — open and grey while the axis is free, closed and white under the
     * selection highlight when it is locked. The glyph is the state, read live from the limits.
     */
    private UIIcon lock(IKey kind, IKey axis, Predicate<BoneConstraint> getter, BiConsumer<BoneConstraint, Boolean> setter)
    {
        UIIcon icon = new UIIcon(() -> getter.test(this.current.get()) ? Icons.LOCKED : Icons.UNLOCKED, (b) ->
        {
            boolean locked = getter.test(this.current.get());

            this.edit.accept((c) -> setter.accept(c, !locked));
            this.changed.run();
        })
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                boolean locked = getter.test(UIBoneConstraintFields.this.current.get());

                this.area.render(context.batcher, BBSSettings.inputSurface());

                if (locked)
                {
                    context.batcher.highlight(this.area, Direction.BOTTOM);
                }

                this.iconColor = locked ? Colors.WHITE : Colors.GRAY;

                super.renderSkin(context);
            }
        };

        icon.w(0);
        icon.h(UIConstants.CONTROL_HEIGHT);
        icon.tooltip(kind.format(axis));

        return icon;
    }

    private UIToggle limit(IKey axis, BiConsumer<BoneConstraint, Boolean> setter)
    {
        UIToggle toggle = new UIToggle(IKey.EMPTY, (b) ->
        {
            this.edit.accept((c) -> setter.accept(c, b.getValue()));
            this.changed.run();
        });

        /* As tall as the row, so its switch lines up with the axis letter and the angles. */
        toggle.w(UIConstants.SWITCH_WIDTH).h(UIConstants.CONTROL_HEIGHT);
        toggle.tooltip(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LIMIT.format(axis));

        return toggle;
    }

    private UITrackpad angle(IKey kind, IKey axis, int color, BiConsumer<BoneConstraint, Float> setter)
    {
        UITrackpad pad = new UITrackpad((v) -> this.edit.accept((c) -> setter.accept(c, v.floatValue())));

        pad.angle180();
        pad.textbox.setColor(color);
        pad.tooltip(IKey.constant("%s (%s)").format(kind, axis));

        return pad;
    }

    private UISliderTrackpad stiffness(IKey axis, int color, BiConsumer<BoneConstraint, Float> setter)
    {
        UISliderTrackpad pad = new UISliderTrackpad((v) -> this.edit.accept((c) -> setter.accept(c, v.floatValue())));

        pad.normalized();
        pad.textbox.setColor(color);
        pad.tooltip(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_STIFFNESS.format(axis));

        return pad;
    }

    /** The grid's column heads: X, Y and Z in their colours, over the locks. */
    private static UIElement axisHeads()
    {
        UIElement corner = new UIElement();

        corner.w(UIConstants.CONTROL_HEIGHT);

        return UI.row(UIConstants.MARGIN, 0, UIConstants.CONTROL_HEIGHT, corner,
            axisName(UIKeys.GENERAL_X, Colors.RED), axisName(UIKeys.GENERAL_Y, Colors.GREEN), axisName(UIKeys.GENERAL_Z, Colors.BLUE));
    }

    private static UIElement axisName(IKey axis, int color)
    {
        return UI.label(axis, UIConstants.CONTROL_HEIGHT, Colors.A100 | color).labelAnchor(0.5F, 0.5F);
    }

    /** A row of the grid: the transform editor's marker for the operation, then its three locks. */
    private static UIElement locks(Icon icon, IKey operation, UIIcon x, UIIcon y, UIIcon z)
    {
        return UI.row(UIConstants.MARGIN, 0, UIConstants.CONTROL_HEIGHT, UI.marker(icon, operation), x, y, z);
    }

    /** A rotation limit: the axis letter and its switch, then the two angles. */
    private static UIElement limits(IKey axis, int color, UIToggle limit, UITrackpad min, UITrackpad max)
    {
        UIElement name = axisName(axis, color);

        name.w(UIConstants.CONTROL_HEIGHT);

        return UI.row(UIConstants.MARGIN, 0, UIConstants.CONTROL_HEIGHT, name, limit, min, max);
    }

    /** The rows top to bottom: the locks' grid, the rotation limits, then the stiffness. */
    public UIElement[] rows()
    {
        return new UIElement[] {
            axisHeads(),
            locks(Icons.ALL_DIRECTIONS, UIKeys.TRANSFORMS_TRANSLATE, this.lockTranslateX, this.lockTranslateY, this.lockTranslateZ),
            locks(Icons.SCALE, UIKeys.TRANSFORMS_SCALE, this.lockScaleX, this.lockScaleY, this.lockScaleZ),
            locks(Icons.REFRESH, UIKeys.TRANSFORMS_ROTATE, this.lockX, this.lockY, this.lockZ),
            UI.label(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LIMITS_TITLE).marginTop(UIConstants.SECTION_GAP),
            limits(UIKeys.GENERAL_X, Colors.RED, this.limitX, this.minX, this.maxX),
            limits(UIKeys.GENERAL_Y, Colors.GREEN, this.limitY, this.minY, this.maxY),
            limits(UIKeys.GENERAL_Z, Colors.BLUE, this.limitZ, this.minZ, this.maxZ),
            this.stiffnessTitle,
            this.stiffnessRow
        };
    }

    /** Every field, for a host that greys them out together. */
    public List<UIElement> all()
    {
        return List.of(this.lockTranslateX, this.lockTranslateY, this.lockTranslateZ, this.lockScaleX, this.lockScaleY, this.lockScaleZ,
            this.lockX, this.lockY, this.lockZ, this.limitX, this.limitY, this.limitZ,
            this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ,
            this.stiffnessX, this.stiffnessY, this.stiffnessZ);
    }

    /** An axis' angles in its colour while it is limited, grey while they wait for the switch — still there to edit. */
    private static void tint(UITrackpad min, UITrackpad max, boolean limited, int color)
    {
        int text = limited ? color : Colors.GRAY;

        min.textbox.setColor(text);
        max.textbox.setColor(text);
    }

    /** Push the limits into the fields: an unlimited axis' angles turn grey, the stiffness shows only with {@code stiffness}. */
    public void fill(BoneConstraint c, boolean stiffness)
    {
        this.limitX.setValue(c.limitX);
        this.limitY.setValue(c.limitY);
        this.limitZ.setValue(c.limitZ);
        this.minX.setValue(c.minX);
        this.minY.setValue(c.minY);
        this.minZ.setValue(c.minZ);
        this.maxX.setValue(c.maxX);
        this.maxY.setValue(c.maxY);
        this.maxZ.setValue(c.maxZ);
        tint(this.minX, this.maxX, c.limitX, Colors.RED);
        tint(this.minY, this.maxY, c.limitY, Colors.GREEN);
        tint(this.minZ, this.maxZ, c.limitZ, Colors.BLUE);
        this.stiffnessX.setValue(c.stiffnessX);
        this.stiffnessY.setValue(c.stiffnessY);
        this.stiffnessZ.setValue(c.stiffnessZ);
        this.stiffnessTitle.setVisible(stiffness);
        this.stiffnessRow.setVisible(stiffness);
    }

    /** Greys the fields with their host. */
    public void setEnabled(boolean enabled, BoneConstraint c)
    {
        for (UIElement element : this.all())
        {
            element.setEnabled(enabled);
        }
    }
}
