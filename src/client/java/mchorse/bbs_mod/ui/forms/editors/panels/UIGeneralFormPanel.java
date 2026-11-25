package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class UIGeneralFormPanel extends UIFormPanel
{
    public UIKeybind hotkey;

    public UIToggle visible;
    public UIToggle pickable;
    public UIButton filterTracks;
    
    public UITextbox trackName;
    public UITextbox name;
    public UITextbox nameTag;
    public UITrackpad nameTagHeight;
    public UIPropTransform transform;
    public UITrackpad[] pivot = new UITrackpad[3];

    public UIToggle hitbox;
    public UITrackpad hitboxWidth;
    public UITrackpad hitboxHeight;
    public UISliderTrackpad hitboxSneakMultiplier;
    public UISliderTrackpad hitboxEyeHeight;

    public UITrackpad hp;
    public UITrackpad speed;
    public UITrackpad swingSpeed;
    public UITrackpad stepHeight;

    public UISection armorSection;
    public UIArmorSlot armorHead;
    public UIArmorSlot armorChest;
    public UIArmorSlot armorLegs;
    public UIArmorSlot armorFeet;

    public UIGeneralFormPanel(UIForm editor)
    {
        super(editor);

        this.hotkey = new UIKeybind((combo) ->
        {
            this.form.hotkey.set(combo.keys.isEmpty() ? 0 : combo.keys.get(0));
        });
        this.hotkey.single().tooltip(UIKeys.FORMS_EDITORS_GENERAL_HOTKEY);

        this.visible = UIValues.toggle(UIKeys.FORMS_EDITORS_GENERAL_VISIBLE, () -> this.form.visible);
        this.pickable = UIValues.toggle(UIKeys.FORMS_EDITORS_GENERAL_PICKABLE, () -> this.form.pickable);
        this.pickable.tooltip(UIKeys.FORMS_EDITORS_GENERAL_PICKABLE_TOOLTIP);
        this.filterTracks = new UIButton(UIKeys.FORMS_EDITORS_GENERAL_FILTER_TRACKS, (b) -> this.openTrackFilter());
        this.filterTracks.tooltip(UIKeys.FORMS_EDITORS_GENERAL_FILTER_TRACKS_TOOLTIP);
        this.trackName = UIValues.textbox(120, () -> this.form.trackName);
        this.trackName.tooltip(UIKeys.FORMS_EDITORS_GENERAL_TRACK_NAME_TOOLTIP);
        this.name = UIValues.textbox(120, () -> this.form.name);
        this.nameTag = UIValues.textbox(1000, () -> this.form.nameTag);
        this.nameTag.textbox.setPlaceholder(UIKeys.FILM_REPLAY_NAME_TAG);
        this.nameTagHeight = UIValues.trackpad(() -> this.form.nameTagHeight);
        this.nameTagHeight.tooltip(UIKeys.FILM_REPLAY_NAME_TAG_HEIGHT);

        this.transform = new UIPropTransform().callbacks(() -> this.form.transform).barBackground();
        this.transform.enableHotkeys();
        this.transform.valueBinding(() -> this.transform.setTransform(this.form == null ? null : this.form.transform.get()));

        IKey raw = IKey.constant("%s (%s)");
        IKey[] axes = {UIKeys.GENERAL_X, UIKeys.GENERAL_Y, UIKeys.GENERAL_Z};
        int[] colors = {Colors.RED, Colors.GREEN, Colors.BLUE};

        for (int i = 0; i < 3; i++)
        {
            UITrackpad field = new UITrackpad((v) -> this.setPivot()).block().onlyNumbers();

            field.tooltip(raw.format(UIKeys.MODEL_EDITOR_MODEL_PIVOT, axes[i]));
            field.textbox.setColor(colors[i]);
            this.pivot[i] = field;
        }

        /* Drawn like the model editor's cube pivot row, under the transform it belongs to — but
         * only a sign here, the form has no geometry of its own to center it on. */
        UIIcon pivotIcon = new UIIcon(Icons.SPHERE, null);

        pivotIcon.disabledColor = pivotIcon.hoverColor = Colors.WHITE;
        pivotIcon.setEnabled(false);

        UIElement pivotRow = this.transform.addRow(pivotIcon, this.pivot[0], this.pivot[1], this.pivot[2]);

        UIValues.resettable(pivotRow, () -> this.form == null ? null : this.form.pivot, null);
        pivotRow.valueBinding(this::readPivot);

        this.hitbox = UIValues.toggle(UIKeys.CAMERA_PANELS_ENABLED, () -> this.form.hitbox);
        this.hitboxWidth = UIValues.trackpad(() -> this.form.hitboxWidth);
        this.hitboxWidth.limit(0).tooltip(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_WIDTH);
        this.hitboxHeight = UIValues.trackpad(() -> this.form.hitboxHeight);
        this.hitboxHeight.limit(0).tooltip(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_HEIGHT);
        this.hitboxSneakMultiplier = new UISliderTrackpad((v) -> this.form.hitboxSneakMultiplier.set(v.floatValue()));
        this.hitboxSneakMultiplier.limit(0, 1);
        this.hitboxEyeHeight = new UISliderTrackpad((v) -> this.form.hitboxEyeHeight.set(v.floatValue()));
        this.hitboxEyeHeight.limit(0, 1);

        this.hp = UIValues.trackpad(() -> this.form.hp);
        this.hp.limit(1F);
        this.speed = UIValues.trackpad(() -> this.form.speed);
        this.speed.limit(0F);
        this.swingSpeed = UIValues.trackpad(() -> this.form.swingSpeed);
        this.swingSpeed.limit(0F);
        this.stepHeight = UIValues.trackpad(() -> this.form.stepHeight);
        this.stepHeight.limit(0F);

        UISection display = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_DISPLAY);

        display.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_DISPLAY, this.name),
            this.hotkey, this.visible, this.pickable
        );

        UISection nameTagSection = new UISection(UIKeys.FILM_REPLAY_NAME_TAG);

        nameTagSection.fields.add(this.nameTag, this.nameTagHeight);
        nameTagSection.setExpanded(false);

        UISection tracks = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_TRACKS);

        tracks.fields.add(this.filterTracks, this.trackName);

        UISection transform = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_TRANSFORM);

        transform.fields.add(this.transform);

        UISection hitbox = new UISection(UIKeys.FORMS_EDITORS_GENERAL_HITBOX);

        hitbox.fields.add(
            this.hitbox,
            UI.row(this.hitboxWidth, this.hitboxHeight),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_SNEAK_MULTIPLIER, this.hitboxSneakMultiplier),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_EYE_HEIGHT, this.hitboxEyeHeight)
        );

        UISection movement = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_MOVEMENT);

        movement.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HP, this.hp),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_MOVEMENT_SPEED, this.speed.tooltip(UIKeys.FORMS_EDITORS_GENERAL_MOVEMENT_SPEED_TOOLTIP)),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_SWING_SPEED, this.swingSpeed.tooltip(UIKeys.FORMS_EDITORS_GENERAL_SWING_SPEED_TOOLTIP)),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_STEP_HEIGHT, this.stepHeight)
        );

        this.armorHead = new UIArmorSlot(EquipmentSlot.HEAD, Icons.ARMOR_HELMET, IKey.constant("Helmet"));
        this.armorChest = new UIArmorSlot(EquipmentSlot.CHEST, Icons.ARMOR_CHESTPLATE, IKey.constant("Chestplate"));
        this.armorLegs = new UIArmorSlot(EquipmentSlot.LEGS, Icons.ARMOR_LEGGINGS, IKey.constant("Leggings"));
        this.armorFeet = new UIArmorSlot(EquipmentSlot.FEET, Icons.ARMOR_BOOTS, IKey.constant("Boots"));

        UIElement armorRow = UI.row(this.armorHead, this.armorChest, this.armorLegs, this.armorFeet);
        armorRow.h(24);

        this.armorSection = this.section(IKey.constant("Armor"), "general.armor", false);
        this.armorSection.fields.add(armorRow);

        this.options.add(
            display,
            nameTagSection,
            tracks,
            transform,
            hitbox,
            movement,
            this.armorSection
        );
    }

    @Override
    public void startEdit(Form form)
    {
        super.startEdit(form);

        /* Everything built through UIValues reads its own property — only the controls that
         * aren't bound to one are filled here. */
        this.hotkey.setKeyCombo(new KeyCombo(IKey.EMPTY, form.hotkey.get()));

        this.hitboxSneakMultiplier.setValue(form.hitboxSneakMultiplier.get());
        this.hitboxEyeHeight.setValue(form.hitboxEyeHeight.get());

        FormRenderer renderer = FormUtilsClient.getRenderer(form);
        boolean supportsArmor = renderer != null && renderer.supportsArmor();

        this.armorSection.setVisible(supportsArmor);
        this.armorHead.syncTooltip();
        this.armorChest.syncTooltip();
        this.armorLegs.syncTooltip();
        this.armorFeet.syncTooltip();

        this.options.resize();
    }

    private void setPivot()
    {
        this.form.pivot.set(new Vector3f(
            (float) this.pivot[0].getValue(),
            (float) this.pivot[1].getValue(),
            (float) this.pivot[2].getValue()
        ));
    }

    /** Read on every frame the row is drawn, so an undo or a reset shows at once. */
    private void readPivot()
    {
        if (this.form == null)
        {
            return;
        }

        Vector3f pivot = this.form.pivot.get();

        for (int i = 0; i < 3; i++)
        {
            if (this.pivot[i].getValue() != pivot.get(i))
            {
                this.pivot[i].setValue(pivot.get(i));
            }
        }
    }

    private void openTrackFilter()
    {
        if (this.form == null)
        {
            return;
        }

        Set<String> disabled = this.form.disabledTracks.get();
        Set<String> keys = new LinkedHashSet<>();
        Map<String, Integer> keyToColor = new HashMap<>();
        Map<String, IKey> keyToTitle = new HashMap<>();

        for (TrackDescriptor track : TrackCatalog.of(this.form))
        {
            keys.add(track.filterKey());
            keyToColor.put(track.filterKey(), track.color());
            keyToTitle.put(track.filterKey(), track.title());
        }

        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(disabled, keys, keyToColor, keyToTitle);

        UIOverlay.addOverlay(this.getContext(), panel);

        panel.onClose(e -> this.form.disabledTracks.set(disabled));
    }

    public class UIArmorSlot extends UIElement
    {
        public final EquipmentSlot slot;
        public final Icon placeholder;
        public final IKey emptyLabel;

        public UIArmorSlot(EquipmentSlot slot, Icon placeholder, IKey emptyLabel)
        {
            super();

            this.slot = slot;
            this.placeholder = placeholder;
            this.emptyLabel = emptyLabel;
            this.h(24);
            this.tooltip(emptyLabel);
        }

        public void syncTooltip()
        {
            ItemStack stack = UIGeneralFormPanel.this.form == null
                ? ItemStack.EMPTY
                : UIGeneralFormPanel.this.form.getConfiguredArmor(this.slot);

            this.tooltip(stack.isEmpty() ? this.emptyLabel : IKey.constant(stack.getName().getString()));
        }

        private void setArmor(ItemStack stack)
        {
            Form form = UIGeneralFormPanel.this.form;

            if (form == null)
            {
                return;
            }

            form.getArmorValue(this.slot).set(stack.copy());
            this.syncTooltip();

            if (UIGeneralFormPanel.this.editor != null && UIGeneralFormPanel.this.editor.editor != null && UIGeneralFormPanel.this.editor.editor.renderer != null)
            {
                var entity = UIGeneralFormPanel.this.editor.editor.renderer.getEntity();

                if (entity != null)
                {
                    entity.setEquipmentStack(this.slot, stack.copy());
                }
            }

            FormRenderer renderer = FormUtilsClient.getRenderer(form);

            if (renderer instanceof MobFormRenderer mobRenderer)
            {
                mobRenderer.applyConfiguredArmorToEntity(true);
            }

            if (form.getParent() != null && form.getParent().getParent() instanceof Replay directReplay)
            {
                directReplay.keyframes.applyFormArmorKeyframes(form);
            }
            else
            {
                UIFilmPanel filmPanel = UIGeneralFormPanel.this.getParent(UIFilmPanel.class);

                if (filmPanel != null && filmPanel.replayEditor != null && filmPanel.replayEditor.getReplay() != null)
                {
                    Replay active = filmPanel.replayEditor.getReplay();

                    if (active.form.get() == form)
                    {
                        active.keyframes.applyFormArmorKeyframes(form);
                    }
                }
            }
        }

        private boolean isValidArmorForSlot(Item item)
        {
            if (item instanceof ArmorItem armor)
            {
                return armor.getSlotType() == this.slot;
            }

            return this.slot == EquipmentSlot.CHEST && item instanceof ElytraItem;
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context))
            {
                if (context.mouseButton == 2)
                {
                    this.setArmor(ItemStack.EMPTY);

                    return true;
                }

                if (context.mouseButton == 0 || context.mouseButton == 1)
                {
                    context.replaceContextMenu((menu) ->
                    {
                        menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, () -> this.setArmor(ItemStack.EMPTY));

                        for (Item item : Registries.ITEM)
                        {
                            if (this.isValidArmorForSlot(item))
                            {
                                ItemStack optionStack = new ItemStack(item);

                                menu.action(new ContextAction(this.placeholder, IKey.constant(optionStack.getName().getString()), () -> this.setArmor(optionStack.copy()))
                                {
                                    @Override
                                    public void render(UIContext ctx, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
                                    {
                                        this.renderBackground(ctx, x, y, w, h, hover, selected);

                                        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
                                        MatrixStack matrices = ctx.batcher.getContext().getMatrices();

                                        matrices.push();
                                        consumers.setUI(true);
                                        ctx.batcher.getContext().drawItem(optionStack, x + 2, y + (h - 16) / 2);
                                        consumers.setUI(false);
                                        matrices.pop();

                                        String label = font.limitToWidth(this.label.get(), w - 24);

                                        ctx.batcher.text(label, x + 22, y + (h - font.getHeight()) / 2 + 1, Colors.WHITE, false);
                                    }
                                });
                            }
                        }
                    });

                    return true;
                }
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            boolean hover = this.area.isInside(context);
            ItemStack stack = UIGeneralFormPanel.this.form == null
                ? ItemStack.EMPTY
                : UIGeneralFormPanel.this.form.getConfiguredArmor(this.slot);

            int border = hover ? (Colors.A100 | BBSSettings.primaryColor.get()) : Colors.LIGHTER_GRAY;

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);
            context.batcher.box(this.area.x + 1, this.area.y + 1, this.area.ex() - 1, this.area.ey() - 1, hover ? Colors.A75 : Colors.A50);

            int ix = this.area.x + (this.area.w - 16) / 2;
            int iy = this.area.y + (this.area.h - 16) / 2;

            if (!stack.isEmpty())
            {
                CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
                MatrixStack matrices = context.batcher.getContext().getMatrices();

                matrices.push();
                consumers.setUI(true);
                context.batcher.getContext().drawItem(stack, ix, iy);
                consumers.setUI(false);
                matrices.pop();
            }
            else
            {
                context.batcher.icon(this.placeholder, Colors.A75 | Colors.WHITE, ix, iy);
            }

            super.render(context);
        }
    }
}