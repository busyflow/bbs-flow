package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.cubic.shake.ShakeControl;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Every field of a bone's shake, built the same for the shake tab and the shake track's keyframes.
 * Right clicking an amplitude copies, pastes or resets all nine of them at once.
 */
public class UIShakeControlFields
{
    private static final String AMPLITUDE_CLIPBOARD = "_CopyShakeAmplitude";

    public final UISliderTrackpad strength;
    public final UISliderTrackpad frequency;
    public final UISliderTrackpad roughness;
    public final UITrackpad seed;
    public final UITrackpad rotateX;
    public final UITrackpad rotateY;
    public final UITrackpad rotateZ;
    public final UITrackpad translateX;
    public final UITrackpad translateY;
    public final UITrackpad translateZ;
    public final UITrackpad scaleX;
    public final UITrackpad scaleY;
    public final UITrackpad scaleZ;

    private final Consumer<Consumer<ShakeControl>> edit;

    public UIShakeControlFields(Consumer<Consumer<ShakeControl>> edit)
    {
        this.edit = edit;

        this.strength = new UISliderTrackpad(v -> edit.accept(c -> c.strength = v.floatValue()));
        this.strength.normalized().tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_STRENGTH);

        this.frequency = new UISliderTrackpad(v -> edit.accept(c -> c.frequency = v.floatValue()));
        this.frequency.onlyNumbers().values(0.5D, 0.1D, 2D).increment(0.5D).limit(0.1D, 20D);
        this.frequency.tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_FREQUENCY_TOOLTIP);

        this.roughness = new UISliderTrackpad(v -> edit.accept(c -> c.roughness = v.floatValue()));
        this.roughness.normalized().tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_ROUGHNESS_TOOLTIP);

        this.seed = new UITrackpad(v -> edit.accept(c -> c.seed = v.intValue()));
        this.seed.onlyNumbers().integer().values(1D).increment(1D).limit(0D);
        this.seed.tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_SEED_TOOLTIP);

        this.rotateX = this.amplitude(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_X, Colors.RED, (c, v) -> c.rotate.x = v).degrees();
        this.rotateY = this.amplitude(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_Y, Colors.GREEN, (c, v) -> c.rotate.y = v).degrees();
        this.rotateZ = this.amplitude(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_Z, Colors.BLUE, (c, v) -> c.rotate.z = v).degrees();
        this.translateX = this.amplitude(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_X, Colors.RED, (c, v) -> c.translate.x = v).block();
        this.translateY = this.amplitude(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_Y, Colors.GREEN, (c, v) -> c.translate.y = v).block();
        this.translateZ = this.amplitude(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_Z, Colors.BLUE, (c, v) -> c.translate.z = v).block();
        this.scaleX = this.amplitude(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_X, Colors.RED, (c, v) -> c.scale.x = v).factor();
        this.scaleY = this.amplitude(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_Y, Colors.GREEN, (c, v) -> c.scale.y = v).factor();
        this.scaleZ = this.amplitude(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_Z, Colors.BLUE, (c, v) -> c.scale.z = v).factor();
    }

    /** One axis' amplitude: none below zero, an axis at zero simply doesn't shake. */
    private UITrackpad amplitude(IKey kind, IKey axis, int color, BiConsumer<ShakeControl, Float> setter)
    {
        UITrackpad pad = new UITrackpad((v) -> this.edit.accept((c) -> setter.accept(c, v.floatValue())));

        pad.onlyNumbers().limit(0D);
        pad.textbox.setColor(color);
        pad.tooltip(IKey.constant("%s (%s)").format(kind, axis));
        pad.context(this::amplitudeMenu);

        return pad;
    }

    private void amplitudeMenu(ContextMenuManager menu)
    {
        menu.icon(MenuVerb.COPY, this::copyAmplitude).label(UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_COPY_AMPLITUDE);
        menu.icon(MenuVerb.PASTE, this::pasteAmplitude).label(UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_PASTE_AMPLITUDE)
            .enabled(Window.getClipboardMap(AMPLITUDE_CLIPBOARD) != null);
        menu.icon(MenuVerb.RESET, this::resetAmplitude).label(UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_RESET_AMPLITUDE);
    }

    private void copyAmplitude()
    {
        MapType data = new MapType();

        data.put("rotate", DataStorageUtils.vector3fToData(vector(this.rotateX, this.rotateY, this.rotateZ)));
        data.put("translate", DataStorageUtils.vector3fToData(vector(this.translateX, this.translateY, this.translateZ)));
        data.put("scale", DataStorageUtils.vector3fToData(vector(this.scaleX, this.scaleY, this.scaleZ)));

        Window.setClipboard(data, AMPLITUDE_CLIPBOARD);
    }

    /** All nine at once, as one edit (one undo entry). */
    private void pasteAmplitude()
    {
        MapType data = Window.getClipboardMap(AMPLITUDE_CLIPBOARD);

        if (data == null)
        {
            return;
        }

        this.setAmplitude(read(data, "rotate"), read(data, "translate"), read(data, "scale"));
    }

    /** All nine to zero, the bone holding still, as one edit (one undo entry). */
    private void resetAmplitude()
    {
        this.setAmplitude(new Vector3f(), new Vector3f(), new Vector3f());
    }

    /**
     * Writes all nine as one edit (one undo entry) and shows them: the shake tab doesn't refill
     * its fields after an edit, since a typed value already stands in the field it was typed into.
     */
    private void setAmplitude(Vector3f rotate, Vector3f translate, Vector3f scale)
    {
        this.edit.accept((c) ->
        {
            c.rotate.set(rotate);
            c.translate.set(translate);
            c.scale.set(scale);
        });

        this.rotateX.setValue(rotate.x);
        this.rotateY.setValue(rotate.y);
        this.rotateZ.setValue(rotate.z);
        this.translateX.setValue(translate.x);
        this.translateY.setValue(translate.y);
        this.translateZ.setValue(translate.z);
        this.scaleX.setValue(scale.x);
        this.scaleY.setValue(scale.y);
        this.scaleZ.setValue(scale.z);
    }

    private static Vector3f vector(UITrackpad x, UITrackpad y, UITrackpad z)
    {
        return new Vector3f((float) x.getValue(), (float) y.getValue(), (float) z.getValue());
    }

    private static Vector3f read(MapType data, String key)
    {
        Vector3f vector = data.has(key, BaseType.TYPE_LIST) ? DataStorageUtils.vector3fFromData(data.getList(key)) : new Vector3f();

        return vector.set(Math.max(vector.x, 0F), Math.max(vector.y, 0F), Math.max(vector.z, 0F));
    }

    /** Every field, for a host that greys them out together. */
    public List<UIElement> all()
    {
        return List.of(this.strength, this.frequency, this.roughness, this.seed,
            this.rotateX, this.rotateY, this.rotateZ,
            this.translateX, this.translateY, this.translateZ,
            this.scaleX, this.scaleY, this.scaleZ);
    }

    /** The rows of the shake's character: strength, frequency, roughness and variant. */
    public UIElement[] settings()
    {
        return new UIElement[] {
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_SHAKE_STRENGTH, this.strength),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_SHAKE_FREQUENCY, this.frequency),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_SHAKE_ROUGHNESS, this.roughness),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_SHAKE_SEED, this.seed)
        };
    }

    /**
     * The amplitude rows in the transform editor's order (position, scale, rotation), each marked
     * with its icon rather than a label.
     */
    public UIElement[] amplitudes()
    {
        return new UIElement[] {
            UI.row(2, 0, UIConstants.CONTROL_HEIGHT, UI.marker(Icons.ALL_DIRECTIONS, UIKeys.TRANSFORMS_TRANSLATE), this.translateX, this.translateY, this.translateZ),
            UI.row(2, 0, UIConstants.CONTROL_HEIGHT, UI.marker(Icons.SCALE, UIKeys.TRANSFORMS_SCALE), this.scaleX, this.scaleY, this.scaleZ),
            UI.row(2, 0, UIConstants.CONTROL_HEIGHT, UI.marker(Icons.REFRESH, UIKeys.TRANSFORMS_ROTATE), this.rotateX, this.rotateY, this.rotateZ)
        };
    }

    /** Push a control's values into the fields. */
    public void fill(ShakeControl control)
    {
        this.strength.setValue(control.strength);
        this.frequency.setValue(control.frequency);
        this.roughness.setValue(control.roughness);
        this.seed.setValue(control.seed);
        this.rotateX.setValue(control.rotate.x);
        this.rotateY.setValue(control.rotate.y);
        this.rotateZ.setValue(control.rotate.z);
        this.translateX.setValue(control.translate.x);
        this.translateY.setValue(control.translate.y);
        this.translateZ.setValue(control.translate.z);
        this.scaleX.setValue(control.scale.x);
        this.scaleY.setValue(control.scale.y);
        this.scaleZ.setValue(control.scale.z);
    }
}
