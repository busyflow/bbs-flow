package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.utils.resizers.layout.LabelRowResizer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.iris.ShaderMenu;
import mchorse.bbs_mod.utils.iris.ShaderOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The settings of the shader pack that's on, opened from the utility panel (F6), laid out the way
 * Iris' own menu lays them out (screens, the way back up, a search through all of it), one option
 * per row with a control that suits it. The pack's name is in the title, and its profile is a row
 * of its own above the options, wherever the pack's menu puts it.
 *
 * <ul>
 *     <li>An option the pack turned into a uniform ({@link ShaderCurves#getCurvable()}) changes at
 *     once, with no reload.</li>
 *     <li>Any other one needs the pack reloaded: its changes pile up, and the bar at the bottom
 *     applies them all with one reload, or drops them. Closing the panel applies what's left, the
 *     way Iris' menu does.</li>
 * </ul>
 *
 * <p>After every load of the pack the panel reads the menu again, since what options there are
 * and which of them can be animated may change, down to the pack's fallback builds. Iris is reached
 * through {@link ShaderOptions} only, so the panel works (and says Iris is missing) without it.
 * Curves are not its business: they are made in the curve clip's picker.</p>
 */
public class UIShaderOptionsPanel extends UIOverlayPanel
{
    private static final int PADDING = 6;
    private static final int GAP = 4;

    /** The panel's own page with the animatable options the pack's menu leaves out */
    private static final String NOT_IN_MENU = "#not_in_menu";

    private ShaderMenu menu;
    private ShaderMenuBrowser browser = new ShaderMenuBrowser(null, Collections.emptyMap());
    private Set<String> curvable = Collections.emptySet();
    private List<String> profileOrder = Collections.emptyList();
    private List<ShaderMenu.Cell> notInMenu = Collections.emptyList();
    private String pack = "";
    private boolean failed;
    private int revision;

    /** Values of options that need a reload, waiting for the bar's Apply */
    private final Map<String, String> pending = new LinkedHashMap<>();

    private final UITextbox search;

    private final UIElement profile;
    private final UIShaderScreenTitle screenTitle;
    private final UIScrollView view;
    private final UIText status;
    private final UIElement bar;

    public UIShaderOptionsPanel()
    {
        super(UIKeys.UTILITY_SHADER_OPTIONS);

        this.title.label = (IKey) this::panelTitle;

        UIButton profile = new UIButton(() -> this.browser.profileLabel(this.currentProfile()), (b) -> this.openProfiles());

        this.profile = new UIElement();
        LabelRowResizer.apply(this.profile, UIShaderOptionRow.CONTROL_WIDTH, false);
        this.profile.add(new UIShaderRowName(UIKeys.CAMERA_PANELS_SHADER_PROFILE::get, () -> Colors.WHITE, null), profile);

        this.search = new UITextbox(100, this::filter);
        this.search.placeholder(UIKeys.GENERAL_SEARCH);

        this.screenTitle = new UIShaderScreenTitle();

        this.view = UI.scrollView(UIConstants.MARGIN, 0);
        this.view.scroll.scrollSpeed = 20;

        this.status = new UIText().color(Colors.GRAY, true);

        UIButton apply = new UIButton(UIKeys.FILM_SHADER_APPLY, (b) -> this.applyPending());
        UIButton discard = new UIButton(UIKeys.FILM_SHADER_DISCARD, (b) -> this.discardPending());

        this.bar = UI.column(UIConstants.MARGIN, 0,
            new UIShaderRowName(() -> UIKeys.FILM_SHADER_PENDING.format(this.pending.size()).get(), () -> Colors.WHITE, null).h(UIConstants.CONTROL_HEIGHT),
            UI.row(UIConstants.MARGIN, 0, UIConstants.CONTROL_HEIGHT, apply, discard)
        );

        for (UIElement element : new UIElement[] {this.profile, this.search, this.screenTitle, this.view, this.status, this.bar})
        {
            element.relative(this.content).x(PADDING).w(1F, -PADDING * 2);
        }

        this.content.add(this.profile, this.search, this.screenTitle, this.view, this.status, this.bar);

        this.revision = ShaderOptions.revision();
        this.rebuild();
    }

    private String panelTitle()
    {
        String title = UIKeys.UTILITY_SHADER_OPTIONS.get();

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

        ShaderOptions.reload(() -> ShaderOptions.apply(values), () ->
        {
            if (context != null)
            {
                UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, UIKeys.UTILITY_SHADER_ERROR));
            }
        });
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

        if (this.menu != null)
        {
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
        }

        this.layoutParts();
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

    /**
     * Shows what the state calls for, one part under another: the pack's parts while there is a
     * pack, with the list taking the height that's left, or a line why not.
     */
    private void layoutParts()
    {
        boolean menu = this.menu != null;
        boolean bar = menu && !this.pending.isEmpty();
        int barHeight = UIConstants.CONTROL_HEIGHT * 2 + UIConstants.MARGIN;
        int y = PADDING;

        this.profile.setVisible(menu && !this.menu.profiles.isEmpty());
        this.search.setVisible(menu);
        this.view.setVisible(menu);
        this.status.setVisible(!menu);
        this.bar.setVisible(bar);

        if (this.profile.isVisible())
        {
            this.profile.y(y).h(UIConstants.CONTROL_HEIGHT);
            y += UIConstants.CONTROL_HEIGHT + UIConstants.MARGIN;
        }

        this.search.y(y).h(UIConstants.CONTROL_HEIGHT);
        y += UIConstants.CONTROL_HEIGHT + UIConstants.MARGIN;

        if (this.screenTitle.isVisible())
        {
            this.screenTitle.y(y);
            y += UIShaderScreenTitle.HEIGHT;
        }

        this.view.y(y).h(1F, -y - (bar ? barHeight + PADDING + UIConstants.MARGIN : PADDING));
        this.bar.y(1F, -barHeight - PADDING).h(barHeight);
        this.status.y(PADDING).h(1F, -PADDING * 2);

        if (!menu)
        {
            this.status.text(!ShaderOptions.IRIS ? UIKeys.UTILITY_IRIS_REQUIRED
                : this.failed ? UIKeys.UTILITY_SHADER_ERROR
                : UIKeys.CAMERA_PANELS_CURVES_NO_SHADERS);
        }

        if (this.hasParent())
        {
            this.resize();
        }
    }

    @Override
    public void render(UIContext context)
    {
        int revision = ShaderOptions.revision();

        if (revision != this.revision)
        {
            this.revision = revision;
            this.rebuild();
        }

        super.render(context);
    }

    @Override
    public void onClose()
    {
        super.onClose();

        if (!this.pending.isEmpty())
        {
            this.applyPending();
        }
    }
}
