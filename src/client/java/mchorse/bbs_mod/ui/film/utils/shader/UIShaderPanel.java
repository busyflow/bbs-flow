package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.UICurveClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.resizers.layout.LabelRowResizer;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.iris.ShaderMenu;
import mchorse.bbs_mod.utils.iris.ShaderOptions;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a curve clip can animate, as two sections of the clip's properties: the world's curves
 * ({@link #world}), and this one with the options of the shader pack that's on (picking the pack is
 * F6's), laid out the way Iris' own menu lays them out (screens, the way back up, a search through
 * all of it), one option per row with a control that suits it. The pack's name is in the section's
 * title, and its profile is a row of its own above the options, wherever the pack's menu puts it.
 *
 * <ul>
 *     <li>An option the pack turned into a uniform ({@link ShaderCurves#getCurvable()}) changes at
 *     once, with no reload.</li>
 *     <li>Any other one needs the pack reloaded: its changes pile up, and the bar at the bottom
 *     applies them all with one reload, or drops them.</li>
 *     <li>Right click on an animatable option or a curve of the world makes a curve for it in the
 *     clip, starting from the value it has now. From then on its track sets it: a row a curve of
 *     the film sets at the playhead shows the curve's value there, and can't be changed here.</li>
 * </ul>
 *
 * <p>The section takes whatever height the clip's properties have left, and the options scroll in
 * it under the search. The film editor has one panel, which moves into whichever curve clip is
 * picked ({@link #attach}), so the screen on show, the search, the folds and the changes waiting
 * for a reload stay from one clip to the next. After every load of the pack the panel reads the
 * menu again, since what options there are and which of them can be animated may change, down to
 * the pack's fallback builds. Iris is reached through {@link ShaderOptions} only, so the panel
 * works (and says Iris is missing) without it.</p>
 */
public class UIShaderPanel extends UISection
{
    /** Least height of the options list; whatever the clip's properties have spare goes on top */
    private static final int LIST_HEIGHT = 120;
    private static final int GAP = 4;

    /** The panel's own page with the animatable options the pack's menu leaves out */
    private static final String NOT_IN_MENU = "#not_in_menu";

    private final UIFilmPanel filmPanel;

    /** The section with the world's curves, which goes above this one */
    public final UISection world;

    /** The curve clip the panel is in: the one its curves are made for */
    private UICurveClip owner;

    private ShaderMenu menu;
    private ShaderMenuBrowser browser = new ShaderMenuBrowser(null, Collections.emptyMap());
    private Set<String> curvable = Collections.emptySet();
    private List<String> profileOrder = Collections.emptyList();
    private List<ShaderMenu.Cell> notInMenu = Collections.emptyList();
    private String pack = "";
    private boolean failed;
    private int revision = -1;

    /** Values of options that need a reload, waiting for the bar's Apply */
    private final Map<String, String> pending = new LinkedHashMap<>();

    /** Values the film's curves give at the playhead, by channel id, found every frame */
    private final Map<String, Double> curves = new HashMap<>();

    /** Ids of the colour curves the film has at the playhead */
    private final Set<String> colorCurves = new HashSet<>();

    public final UITextbox search;

    private final UIElement profile;
    private final UIShaderScreenTitle screenTitle;
    private final UIScrollView view;
    private final UIText status;
    private final UIElement bar;

    public UIShaderPanel(UIFilmPanel filmPanel)
    {
        super(UIKeys.CAMERA_PANELS_CURVES_SHADER_SECTION);

        this.filmPanel = filmPanel;
        this.title((IKey) this::sectionTitle);

        this.world = new UISection(UIKeys.CAMERA_PANELS_CURVES_WORLD);

        for (WorldCurve curve : WorldCurve.all())
        {
            this.world.fields.add(new UIWorldCurveRow(this, curve));
        }

        UIButton profile = new UIButton(() -> this.browser.profileLabel(this.currentProfile()), (b) -> this.openProfiles());

        this.profile = new UIElement();
        LabelRowResizer.apply(this.profile, UIShaderOptionRow.CONTROL_WIDTH, true);
        this.profile.add(new UIShaderRowName(UIKeys.CAMERA_PANELS_SHADER_PROFILE::get, () -> Colors.WHITE, null), profile);

        this.search = new UITextbox(100, this::filter);
        this.search.placeholder(UIKeys.GENERAL_SEARCH);
        this.search.h(UIConstants.CONTROL_HEIGHT);

        this.screenTitle = new UIShaderScreenTitle();

        this.view = UI.scrollView(UIConstants.MARGIN, 0);
        this.view.scroll.scrollSpeed = 20;
        this.view.h(LIST_HEIGHT).expand();

        this.status = new UIText().color(Colors.GRAY, true);

        UIButton apply = new UIButton(UIKeys.FILM_SHADER_APPLY, (b) -> this.applyPending());
        UIButton discard = new UIButton(UIKeys.FILM_SHADER_DISCARD, (b) -> this.discardPending());

        this.bar = UI.column(UIConstants.MARGIN, 0,
            new UIShaderRowName(() -> UIKeys.FILM_SHADER_PENDING.format(this.pending.size()).get(), () -> Colors.WHITE, null).h(UIConstants.CONTROL_HEIGHT),
            UI.row(UIConstants.MARGIN, 0, UIConstants.CONTROL_HEIGHT, apply, discard)
        );

        /* The list takes the height that's left, so every layer down to it has to ask for it */
        this.fields.expand();
        this.fields.add(this.profile, this.search, this.screenTitle, this.view, this.status, this.bar);
        this.onToggle((section) -> this.updateExpand());
        this.layoutParts();
    }

    /* === The clip === */

    /**
     * The panel's sections leave the clip they were in; the curves it makes go to the given clip,
     * which puts the sections among its own.
     */
    public void attach(UICurveClip owner)
    {
        this.world.removeFromParent();
        this.removeFromParent();
        this.owner = owner;
    }

    public void focusSearch()
    {
        UIContext context = this.getContext();

        if (context == null || this.menu == null)
        {
            return;
        }

        if (!this.isExpanded())
        {
            this.setExpanded(true);
        }

        context.focus(this.search);
    }

    /** What a track of a curve clip is called: the world curve's name, or the option's in the pack */
    public String channelLabel(String channelId)
    {
        for (UIWorldCurveRow row : this.world.fields.getChildren(UIWorldCurveRow.class))
        {
            if (row.curve.id.equals(channelId))
            {
                return row.curve.label.get();
            }
        }

        if (channelId.startsWith(CurveClip.SHADER_CURVES_PREFIX))
        {
            return this.browser.optionLabel(channelId.substring(CurveClip.SHADER_CURVES_PREFIX.length()));
        }

        return channelId;
    }

    /**
     * Right click on a row: a curve for it in the clip (starting from the value the row shows), or
     * the way to take the clip's curve out.
     */
    void curveActions(ContextMenuManager menu, String channelId, boolean curvable)
    {
        if (this.owner == null)
        {
            return;
        }

        if (this.owner.hasCurve(channelId))
        {
            menu.action(Icons.REMOVE, UIKeys.CAMERA_PANELS_CURVE_REMOVE, () -> this.owner.removeCurve(channelId));
        }
        else if (curvable)
        {
            menu.action(Icons.CURVES, UIKeys.CAMERA_PANELS_CURVE_CREATE, () -> this.owner.createCurve(channelId));
        }
    }

    /**
     * The value a curve starts from: the one its row shows now, which may be what a curve of the
     * film gives it at the playhead. A new curve's first keyframe takes it, so making a curve
     * leaves the picture as it was. A colour for a colour curve; null for a track the panel
     * doesn't know.
     */
    public Object startValue(String channelId)
    {
        for (UIWorldCurveRow row : this.world.fields.getChildren(UIWorldCurveRow.class))
        {
            if (row.curve.id.equals(channelId))
            {
                return row.curve.start();
            }
        }

        if (!channelId.startsWith(CurveClip.SHADER_CURVES_PREFIX))
        {
            return null;
        }

        String optionId = channelId.substring(CurveClip.SHADER_CURVES_PREFIX.length());
        Double curve = this.curveValue(optionId);

        if (curve != null)
        {
            return curve;
        }

        Float value = ShaderCurves.parse(this.value(optionId));

        return value == null ? 0D : value.doubleValue();
    }

    private String sectionTitle()
    {
        String title = UIKeys.CAMERA_PANELS_CURVES_SHADER_SECTION.get();

        return this.menu == null || this.menu.name.isEmpty() ? title : title + " · " + this.menu.name;
    }

    /* === Values === */

    /** The value an option has now: waiting for a reload, or the pack's */
    String value(String optionId)
    {
        String value = this.pending.get(optionId);

        if (value == null && this.menu != null)
        {
            value = this.menu.values.get(optionId);
        }

        return value;
    }

    /** The value a curve of the film gives the option at the playhead, or null */
    Double curveValue(String optionId)
    {
        return this.curvable.contains(optionId) ? this.curves.get(CurveClip.SHADER_CURVES_PREFIX + optionId) : null;
    }

    /** Whether a curve of the film sets a curve of the world at the playhead */
    boolean isCurved(String channelId)
    {
        return this.curves.containsKey(channelId) || this.colorCurves.contains(channelId);
    }

    boolean isCurvable(String optionId)
    {
        return this.curvable.contains(optionId);
    }

    boolean isPending(String optionId)
    {
        return this.pending.containsKey(optionId);
    }

    ShaderMenuBrowser getBrowser()
    {
        return this.browser;
    }

    /**
     * Sets an option: one the pack turned into a uniform takes the value at once, any other waits
     * for the reload the bar at the bottom offers.
     */
    void set(String optionId, String value)
    {
        if (this.menu == null || value == null || value.equals(this.value(optionId)))
        {
            return;
        }

        if (this.curvable.contains(optionId))
        {
            this.menu.values.put(optionId, value);

            try
            {
                ShaderOptions.setLive(optionId, value);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            return;
        }

        if (value.equals(this.menu.values.get(optionId)))
        {
            this.pending.remove(optionId);
        }
        else
        {
            this.pending.put(optionId, value);
        }

        this.layoutParts();
    }

    private void applyPending()
    {
        Map<String, String> values = new LinkedHashMap<>(this.pending);

        this.pending.clear();
        this.layoutParts();

        UIContext context = this.getContext();

        ShaderOptions.reload(() -> ShaderOptions.apply(values),
            () -> UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, UIKeys.UTILITY_SHADER_ERROR)));
    }

    private void discardPending()
    {
        this.pending.clear();
        this.layoutParts();
    }

    /* === Profile === */

    private void openProfiles()
    {
        if (this.menu == null || this.menu.profiles.isEmpty())
        {
            return;
        }

        UIChoiceMenu.of(new ArrayList<>(this.menu.profiles.keySet()))
            .label((profile) -> IKey.constant(this.browser.profileLabel(profile)))
            .current(this.currentProfile())
            .open(this.getContext(), this::applyProfile);
    }

    /** A profile sets its options the way a change by hand does: at once, or waiting for a reload. */
    private void applyProfile(String profile)
    {
        Map<String, String> values = this.menu == null ? null : this.menu.profiles.get(profile);

        if (values == null)
        {
            return;
        }

        for (Map.Entry<String, String> entry : values.entrySet())
        {
            if (this.menu.values.containsKey(entry.getKey()))
            {
                this.set(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * The profile the options match, the way Iris finds it: the one that sets the most options
     * goes first. Null when none matches (a custom setup).
     */
    String currentProfile()
    {
        if (this.menu == null)
        {
            return null;
        }

        for (String profile : this.profileOrder)
        {
            boolean matches = true;

            for (Map.Entry<String, String> entry : this.menu.profiles.get(profile).entrySet())
            {
                String value = this.value(entry.getKey());

                if (value != null && !value.equals(entry.getValue()))
                {
                    matches = false;

                    break;
                }
            }

            if (matches)
            {
                return profile;
            }
        }

        return null;
    }

    /* === Browsing === */

    private void filter(String text)
    {
        this.browser.setQuery(text);
        this.fillView();
        this.view.scroll.scrollTo(0);
    }

    private void open(String screenId)
    {
        if (this.browser.open(screenId))
        {
            this.fillView();
            this.view.scroll.scrollTo(0);
        }
    }

    private void back()
    {
        this.browser.back();
        this.fillView();
        this.view.scroll.scrollTo(0);
    }

    /** Reads the loaded pack's menu again; the screen on show stays, if the pack still has it. */
    private void rebuild()
    {
        String pack = ShaderOptions.currentPack();
        ShaderMenu menu = null;
        Map<String, String> lang = Collections.emptyMap();

        this.failed = false;

        if (ShaderOptions.IRIS)
        {
            try
            {
                menu = ShaderOptions.menu();

                if (menu != null)
                {
                    lang = ShaderOptions.lang();
                }
            }
            catch (LinkageError e)
            {
                /* An Iris build whose option-menu API differs from the one we compiled against */
                menu = null;
                this.failed = true;
            }
        }

        if (!pack.equals(this.pack))
        {
            this.pending.clear();
        }

        this.pack = pack;
        this.menu = menu;
        this.curvable = menu == null ? Collections.emptySet() : ShaderCurves.getCurvable();

        ShaderMenuBrowser browser = new ShaderMenuBrowser(menu, lang);

        browser.addPage(NOT_IN_MENU, UIKeys.CAMERA_PANELS_CURVES_NOT_IN_MENU.get());
        browser.follow(this.browser);
        this.browser = browser;

        if (menu == null)
        {
            this.pending.clear();
            this.profileOrder = Collections.emptyList();
            this.notInMenu = Collections.emptyList();
        }
        else
        {
            /* What the reload applied, or what can now change without one, waits no more */
            ShaderMenu loaded = menu;

            this.pending.entrySet().removeIf((entry) -> this.curvable.contains(entry.getKey())
                || !loaded.values.containsKey(entry.getKey())
                || entry.getValue().equals(loaded.values.get(entry.getKey())));

            List<String> order = new ArrayList<>(menu.profiles.keySet());

            order.sort(Comparator.comparingInt((profile) -> -loaded.profiles.get(profile).size()));
            this.profileOrder = order;

            List<ShaderMenu.Cell> notInMenu = new ArrayList<>(menu.notInMenu);

            notInMenu.sort((a, b) -> browser.optionLabel(a.optionId).compareToIgnoreCase(browser.optionLabel(b.optionId)));
            this.notInMenu = notInMenu;
        }

        double scroll = this.view.scroll.getScroll();

        this.fillView();
        this.layoutParts();
        this.view.scroll.setScroll(scroll);
    }

    private void fillView()
    {
        this.view.removeAll();
        this.screenTitle.setVisible(this.menu != null && (this.browser.isSearching() || !this.browser.isMain()));

        if (this.menu == null)
        {
            return;
        }

        if (this.browser.isSearching())
        {
            this.fillResults();
        }
        else
        {
            String screenId = this.browser.getScreen();

            this.screenTitle.screen(this.browser.screenTitle(screenId), this::back);

            if (NOT_IN_MENU.equals(screenId))
            {
                for (ShaderMenu.Cell cell : this.notInMenu)
                {
                    this.view.add(new UIShaderOptionRow(this, cell, null));
                }
            }
            else
            {
                this.fillScreen(this.menu.getScreen(screenId));
            }
        }

        this.relayout();
    }

    private void fillResults()
    {
        String query = this.browser.getQuery();
        String notInMenuTitle = this.browser.screenTitle(NOT_IN_MENU);
        int found = 0;

        for (ShaderMenuBrowser.Hit hit : this.browser.index((cell) -> true))
        {
            if (hit.matches(query))
            {
                this.view.add(new UIShaderOptionRow(this, hit.cell, hit.where));
                found += 1;
            }
        }

        for (ShaderMenu.Cell cell : this.notInMenu)
        {
            if (ShaderMenuBrowser.matches(this.browser.optionLabel(cell.optionId), cell.optionId, query))
            {
                this.view.add(new UIShaderOptionRow(this, cell, notInMenuTitle));
                found += 1;
            }
        }

        IKey title = found == 0 ? UIKeys.CAMERA_PANELS_CURVES_NOT_FOUND : UIKeys.CAMERA_PANELS_CURVES_FOUND.format(found);

        this.screenTitle.note(title.get());
    }

    /**
     * The screen's cells in a column. The gaps a pack leaves to line its columns up become small
     * spaces between groups, never at the edges. The pack's profile cell is left out, since the
     * profile has a row of its own on top. The main screen ends with the way to the options the
     * menu leaves out.
     */
    private void fillScreen(ShaderMenu.Screen screen)
    {
        if (screen == null)
        {
            return;
        }

        boolean any = false;
        boolean gap = false;

        for (ShaderMenu.Cell cell : screen.cells)
        {
            if (cell.type == ShaderMenu.CellType.PROFILE)
            {
                continue;
            }

            UIElement row = this.cellRow(cell);

            if (row == null)
            {
                gap = any;

                continue;
            }

            if (gap)
            {
                this.view.add(new UIElement().h(GAP));
                gap = false;
            }

            this.view.add(row);
            any = true;
        }

        if (screen == this.menu.mainScreen && !this.notInMenu.isEmpty())
        {
            this.view.add(new UIElement().h(GAP));
            this.view.add(new UIShaderLinkRow(this.browser.screenTitle(NOT_IN_MENU), false, () -> this.open(NOT_IN_MENU)));
        }
    }

    private UIElement cellRow(ShaderMenu.Cell cell)
    {
        if (cell.type == ShaderMenu.CellType.OPTION && cell.optionId != null)
        {
            return new UIShaderOptionRow(this, cell, null);
        }
        else if (cell.type == ShaderMenu.CellType.LINK && cell.targetScreenId != null)
        {
            String screenId = cell.targetScreenId;
            boolean empty = this.browser.countOptions(screenId, (option) -> true) == 0;

            return new UIShaderLinkRow(this.browser.screenTitle(screenId), empty, () -> this.open(screenId));
        }

        return null;
    }

    /** Shows what the state calls for: the pack's parts while there is a pack, a line why not otherwise. */
    private void layoutParts()
    {
        boolean menu = this.menu != null;

        this.profile.setVisible(menu && !this.menu.profiles.isEmpty());
        this.search.setVisible(menu);
        this.view.setVisible(menu);
        this.status.setVisible(!menu);
        this.bar.setVisible(menu && !this.pending.isEmpty());

        if (!menu)
        {
            this.status.text(!ShaderOptions.IRIS ? UIKeys.UTILITY_IRIS_REQUIRED
                : this.failed ? UIKeys.UTILITY_SHADER_ERROR
                : UIKeys.CAMERA_PANELS_CURVES_NO_SHADERS);
        }

        this.updateExpand();
        this.relayout();
    }

    /** Lays the clip's properties out again, if the panel is in them (it is laid out when it gets in) */
    private void relayout()
    {
        if (this.hasParent())
        {
            this.resizeParent();
        }
    }

    /** Only an open section with a menu in it has a use for the room left in the clip's properties. */
    private void updateExpand()
    {
        this.expand(this.isExpanded() && this.menu != null);
    }

    /* === Every frame === */

    /**
     * Called by the clip the panel is in on every frame, whether its sections are in sight or
     * folded: reads the menu again after a load of the pack, and finds what the film's curve clips
     * set at the playhead, the top layer winning.
     */
    public void update()
    {
        int revision = ShaderOptions.revision();

        if (revision != this.revision)
        {
            this.revision = revision;
            this.rebuild();
        }

        this.curves.clear();
        this.colorCurves.clear();

        Film film = this.filmPanel.getData();

        if (film == null)
        {
            return;
        }

        int tick = this.filmPanel.getCursor();

        for (Clip clip : film.camera.getClips(tick))
        {
            if (!clip.enabled.get() || !(clip instanceof CurveClip curveClip))
            {
                continue;
            }

            int relative = tick - clip.tick.get();

            for (KeyframeChannel<Double> channel : curveClip.channels.getChannels())
            {
                if (channel.hasEnabledKeyframes())
                {
                    this.curves.put(channel.getId(), channel.interpolate(relative));
                }
            }

            for (KeyframeChannel<Color> channel : curveClip.channels.getColorChannels())
            {
                if (channel.hasEnabledKeyframes())
                {
                    this.colorCurves.add(channel.getId());
                }
            }
        }
    }
}
