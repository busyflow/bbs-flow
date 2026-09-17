package mchorse.bbs_mod.ui.film.crowds;

import mchorse.bbs_mod.actions.types.crowd.CrowdFormation;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.crowds.Crowd;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.area.AreaBrush;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Everything about one crowd: how many, what they are, where they stand, and the paint brush.
 *
 * <p>Its own element rather than part of a panel because it is wanted in two places at once -
 * the Crowds editor, which is where crowds are made and listed, and the crowd form's panel,
 * which is where a shot is actually built. A crowd's count and its paint are judged against the
 * shot, so being able to reach them only by leaving the shot was the wrong shape.</p>
 *
 * <p>Both places edit the same crowd. There is one crowd, owned by the film; this is a view of
 * it, not a copy.</p>
 */
public class UICrowdSettings extends UIElement
{
    private static final List<String> MOB_IDS = new ArrayList<>();

    static
    {
        for (RegistryKey<EntityType<?>> key : Registries.ENTITY_TYPE.getKeys())
        {
            MOB_IDS.add(key.getValue().toString());
        }

        MOB_IDS.sort(Comparator.comparing((a) -> a));
    }

    private Crowd crowd;
    private final Runnable onEdit;

    private final UITextbox name;
    private final UIToggle enabled;
    private final UITrackpad start;
    private final UIButton anchor;

    private final UIButton mobType;
    private final UIToggle useActorForm;
    private final UINestedEdit actorForm;
    private final UIButton armor;
    private final UITrackpad count;
    private final UITrackpad seed;
    private final UITrackpad spacing;
    private final UIButton formation;
    private final UITrackpad holeRadius;
    private final UIToggle disableAi;
    private final UIToggle randomYaw;
    private final UIToggle spawnOnBlock;
    private final UIToggle skipUnsafe;

    private final UIElement paintSection;
    private final UIElement whereSection;
    private final UITrackpad brushSize;
    private final UIButton paint;
    private final UIButton erase;
    private final UIButton removeSelection;
    private final UIToggle showOutline;
    private final UILabel paintInfo;

    public UICrowdSettings(Runnable onEdit)
    {
        this.onEdit = onEdit;

        this.name = new UITextbox(64, (text) -> this.edit((crowd) -> crowd.name.set(text)));
        this.enabled = new UIToggle(IKey.constant("Enabled"), (b) -> this.edit((crowd) -> crowd.enabled.set(b.getValue())));
        this.start = new UITrackpad((value) -> this.edit((crowd) -> crowd.start.set(value.intValue())));
        this.start.integer().tooltip(IKey.constant("The tick the crowd appears on."));
        this.anchor = new UIButton(IKey.EMPTY, (b) -> this.openAnchorMenu());
        this.anchor.tooltip(IKey.constant("Which replay the crowd is placed around.\n\nPainted crowds ignore this - painted ground is drawn onto the world, so it already knows where it is."));

        this.mobType = new UIButton(IKey.EMPTY, (b) -> this.openMobPicker());
        this.armor = new UIButton(IKey.constant("Armor..."), (b) -> this.openArmor());
        this.armor.tooltip(IKey.constant("Dress the crowd: weighted armour profiles a member rolls into, mixed per slot."));
        this.useActorForm = new UIToggle(IKey.constant("BBS model"), (b) -> this.edit((crowd) -> crowd.useActorForm.set(b.getValue())));
        this.actorForm = new UINestedEdit((edit) -> this.openFormPicker(edit)).keybinds();
        this.count = new UITrackpad((value) -> this.edit((crowd) -> crowd.count.set(value.intValue())));
        this.count.integer();
        this.seed = new UITrackpad((value) -> this.edit((crowd) -> crowd.seed.set(value.intValue())));
        this.seed.integer();
        this.spacing = new UITrackpad((value) -> this.edit((crowd) -> crowd.spacing.set(value.floatValue())));
        this.spacing.values(0.1D);
        this.formation = new UIButton(IKey.EMPTY, (b) -> this.openFormationMenu());
        this.holeRadius = new UITrackpad((value) -> this.edit((crowd) -> crowd.holeRadius.set(value.floatValue())));
        this.holeRadius.values(0.1D, 0.01D, 0.5D);
        this.disableAi = new UIToggle(IKey.constant("No mob AI"), (b) -> this.edit((crowd) -> crowd.disableAi.set(b.getValue())));
        this.randomYaw = new UIToggle(IKey.constant("Random yaw"), (b) -> this.edit((crowd) -> crowd.randomYaw.set(b.getValue())));
        this.spawnOnBlock = new UIToggle(IKey.constant("Surface"), (b) -> this.edit((crowd) -> crowd.spawnOnBlock.set(b.getValue())));
        this.skipUnsafe = new UIToggle(IKey.constant("Skip blocked"), (b) -> this.edit((crowd) -> crowd.skipUnsafe.set(b.getValue())));

        this.brushSize = new UITrackpad((value) -> this.edit((crowd) -> crowd.brushSize.set(value.intValue())));
        this.brushSize.integer();
        this.paint = new UIButton(IKey.constant("Paint"), (b) -> this.toggleBrush(false));
        this.erase = new UIButton(IKey.constant("Erase"), (b) -> this.toggleBrush(true));
        this.removeSelection = new UIButton(IKey.constant("Remove selection"), (b) ->
        {
            this.edit(Crowd::clearCells);

            if (AreaBrush.onFinishStroke != null)
            {
                AreaBrush.onFinishStroke.run();
            }
        });
        this.removeSelection.color(Colors.NEGATIVE);
        this.showOutline = new UIToggle(IKey.constant("Show outline"), (b) -> this.edit((crowd) -> crowd.showOutline.set(b.getValue())));
        this.showOutline.tooltip(IKey.constant("Draw the painted ground's edge in the viewport.\n\nOff hides it without unpainting anything - for looking at the shot, or for painting a second crowd over the same ground."));
        this.paintInfo = UI.label(IKey.EMPTY);

        this.whereSection = this.section("Where",
            this.row("Formation", this.formation),
            this.row("Spacing", this.spacing),
            this.row("Donut hole", this.holeRadius)
        );

        this.paintSection = this.section("Painted area",
            this.row("Brush size", this.brushSize),
            UI.row(2, this.paint, this.erase),
            this.removeSelection,
            this.showOutline,
            this.paintInfo
        );

        this.column(3).vertical().stretch();

        /* Short sections ride two-up to a row to spend the vertical space better; "Where" stays full
         * width so the paint section can still slot in right under it. */
        this.add(
            UI.row(5,
                this.section("Crowd",
                    this.row("Name", this.name),
                    this.enabled,
                    this.row("Count", this.count),
                    this.row("Seed", this.seed)
                ),
                this.section("When",
                    this.row("Start", this.start),
                    this.row("Anchor", this.anchor)
                )
            ),
            this.whereSection,
            this.paintSection,
            UI.row(5,
                this.section("Placement",
                    UI.row(1, this.spawnOnBlock, this.skipUnsafe),
                    UI.row(1, this.randomYaw, this.disableAi)
                ),
                this.section("NPC Model",
                    this.row("Mob", this.mobType),
                    this.useActorForm,
                    this.row("Model", this.actorForm),
                    this.armor
                )
            )
        );
    }

    private void openArmor()
    {
        if (this.crowd == null)
        {
            return;
        }

        UICrowdArmorOverlayPanel panel = new UICrowdArmorOverlayPanel(this.crowd.armor.get(), () -> this.edit((crowd) -> {}));

        /* Fixed width: the panel is four slot cards two to a row, so it wants a dialog rather than
         * most of the screen - which is what a fraction of the width gave it on a wide monitor. */
        UIOverlay.addOverlay(this.getContext(), panel, 420, 0.85F);
    }

    public Crowd getCrowd()
    {
        return this.crowd;
    }

    public void setCrowd(Crowd crowd)
    {
        this.crowd = crowd;

        /* The brush belongs to whichever crowd is open; leaving it armed on the last one would
         * paint into a crowd nobody is looking at. */
        AreaBrush.disarm();

        this.setVisible(crowd != null);

        if (crowd != null)
        {
            this.fillData();
        }
    }

    private void edit(Consumer<Crowd> consumer)
    {
        if (this.crowd != null)
        {
            consumer.accept(this.crowd);

            if (this.onEdit != null)
            {
                this.onEdit.run();
            }
        }
    }

    private void fillData()
    {
        Crowd crowd = this.crowd;

        this.name.setText(crowd.name.get());
        this.enabled.setValue(crowd.enabled.get());
        this.start.setValue(crowd.start.get());
        this.refreshAnchorLabel();

        this.mobType.label = IKey.constant(crowd.mobType.get());
        this.useActorForm.setValue(crowd.useActorForm.get());
        this.actorForm.setForm(crowd.actorForm.get());
        this.count.setValue(crowd.count.get());
        this.seed.setValue(crowd.seed.get());
        this.spacing.setValue(crowd.spacing.get());
        this.refreshFormationLabel();
        this.holeRadius.setValue(crowd.holeRadius.get());
        this.disableAi.setValue(crowd.disableAi.get());
        this.randomYaw.setValue(crowd.randomYaw.get());
        this.spawnOnBlock.setValue(crowd.spawnOnBlock.get());
        this.skipUnsafe.setValue(crowd.skipUnsafe.get());
        this.brushSize.setValue(crowd.brushSize.get());
        this.showOutline.setValue(crowd.showOutline.get());
    }

    @Override
    public void render(UIContext context)
    {
        if (this.crowd != null)
        {
            boolean painting = this.crowd.getFormation() == CrowdFormation.PAINT;

            if (!painting)
            {
                AreaBrush.disarm();
            }

            /* Taken out of the column rather than hidden in place. An invisible child still
             * occupies its row, which left a blank band the height of the whole paint section
             * sitting between the sections either side of it. */
            this.setPaintVisible(painting);

            boolean armed = AreaBrush.getCrowd() == this.crowd;

            this.paint.custom = armed && !AreaBrush.isErasing();
            this.paint.customColor = Colors.A100 | Colors.ACTIVE;
            this.erase.custom = armed && AreaBrush.isErasing();
            this.erase.customColor = Colors.A100 | Colors.ACTIVE;
            this.paintInfo.label = IKey.constant(this.crowd.getCells().size() + " blocks painted");
        }

        super.render(context);
    }

    /** Paint only means something for painted ground, so the section is only there for it. */
    private void setPaintVisible(boolean visible)
    {
        boolean present = this.paintSection.getParent() != null;

        if (visible == present)
        {
            return;
        }

        if (visible)
        {
            /* Back where it was authored - after "Where", before "Placement". */
            this.addAfter(this.whereSection, this.paintSection);
        }
        else
        {
            this.paintSection.removeFromParent();
        }

        this.resize();
    }

    private void toggleBrush(boolean erasing)
    {
        if (AreaBrush.getCrowd() == this.crowd && AreaBrush.isErasing() == erasing)
        {
            AreaBrush.disarm();

            return;
        }

        AreaBrush.arm(this.crowd, erasing);
    }

    /* Pickers */

    private void openFormPicker(boolean editing)
    {
        if (this.crowd == null)
        {
            return;
        }

        UIFormPalette.open(this, editing, this.crowd.actorForm.get(), (form) ->
        {
            this.edit((crowd) -> crowd.actorForm.set(FormUtils.copy(form)));
            this.actorForm.setForm(form);
        });
    }

    private void openMobPicker()
    {
        if (this.crowd == null)
        {
            return;
        }

        UIListOverlayPanel panel = new UIListOverlayPanel(IKey.constant("Mob type"), (id) ->
        {
            this.edit((crowd) -> crowd.mobType.set(id));
            this.mobType.label = IKey.constant(id);
        });

        panel.addValues(MOB_IDS).setValue(this.crowd.mobType.get());
        UIOverlay.addOverlay(this.getContext(), panel, 240, 300);
    }

    private void openFormationMenu()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            for (CrowdFormation formation : CrowdFormation.values())
            {
                menu.action(Icons.SHAPES, IKey.constant(formation.title), () ->
                {
                    this.edit((crowd) -> crowd.formation.set(formation.ordinal()));
                    this.refreshFormationLabel();
                });
            }
        });
    }

    private void openAnchorMenu()
    {
        Film film = UIFilmPanel.getEditedFilm();

        if (film == null)
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, IKey.constant("(none)"), () ->
            {
                this.edit((crowd) -> crowd.anchor.set(-1));
                this.refreshAnchorLabel();
            });

            List<Replay> replays = film.replays.getList();

            for (int i = 0; i < replays.size(); i++)
            {
                int index = i;

                menu.action(Icons.SCENE, IKey.constant(replays.get(i).getName()), () ->
                {
                    this.edit((crowd) -> crowd.anchor.set(index));
                    this.refreshAnchorLabel();
                });
            }
        });
    }

    private void refreshAnchorLabel()
    {
        Film film = UIFilmPanel.getEditedFilm();
        int index = this.crowd == null ? -1 : this.crowd.anchor.get();
        List<Replay> replays = film == null ? List.of() : film.replays.getList();

        this.anchor.label = IKey.constant(index < 0 || index >= replays.size() ? "(none)" : replays.get(index).getName());
    }

    private void refreshFormationLabel()
    {
        this.formation.label = IKey.constant(this.crowd == null ? "" : this.crowd.getFormation().title);
    }

    /* Layout helpers, matching the clip editors' spacing so the panel doesn't read as foreign. */

    private UIElement row(String label, UIElement element)
    {
        return UI.row(4, UI.label(IKey.constant(label)).w(74), element);
    }

    private UIElement section(String label, UIElement... elements)
    {
        UISection section = new UISection(IKey.constant(label));

        section.fields.add(elements);

        return section;
    }
}
