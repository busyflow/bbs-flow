package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.iris.ShaderMenu;
import mchorse.bbs_mod.utils.iris.ShaderOptions;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Picks what a curve clip animates, from one panel: the world (time of day, weather, brightness and
 * the like) and, with a shader pack on, the pack's own options.
 *
 * <ul>
 *     <li>A search field on top looks through all of it at once, sub-screens included; a hit
 *     says where it lives.</li>
 *     <li>The world's curves sit under it and stay in sight while the pack's menu is browsed.</li>
 *     <li>The pack's options menu follows as Iris lays it out, one screen at a time, with the way
 *     back up as breadcrumbs. A link with no curves behind it is dimmed, and the animatable options
 *     the menu leaves out get a page of their own at the end of the main screen. Options that
 *     can't be animated stay grey, so the menu keeps its layout.</li>
 * </ul>
 *
 * <p>A click adds a curve and leaves the panel open for the next one; an added curve reads as a
 * picked row, and a second click takes the track out again. The panel keeps one size whichever
 * screen is shown, so it doesn't jump while browsing. Changing the pack's options is not its
 * business: that is the pack's settings, opened from the utility panel (F6).</p>
 */
public class UICurvePicker extends UIOverlayPanel
{
    private static final int PADDING = 6;
    private static final int MARGIN = 4;
    private static final int COLUMN_WIDTH = 210;
    private static final int ICONS_WIDTH = 20;
    private static final int TITLE_HEIGHT = 20;
    private static final int SEARCH_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 16;
    private static final int MAX_WIDTH = 680;
    private static final int MAX_MENU_HEIGHT = 300;

    /** The picker's own page with the animatable options the pack's menu leaves out */
    private static final String NOT_IN_MENU = "#not_in_menu";

    private final ShaderMenuBrowser browser;
    private final Set<String> added;
    private final Consumer<String> onAdd;
    private final Consumer<String> onRemove;
    private final int columns;

    /** Animatable options no screen of the menu shows, in the order they're shown */
    private final List<ShaderMenu.Cell> notInMenu = new ArrayList<>();

    private final UITextbox search;

    private final UIElement worldGrid;
    private final Header header;
    private final UIScrollView view;
    private final GridResizer viewGrid;

    /**
     * Opens the picker with the menu of the pack that's on, if there is one.
     *
     * @param existing ids of the channels the clip has already
     * @param onAdd receives the channel id of every curve picked
     * @param onRemove receives the channel id of every added curve clicked again
     */
    public static void open(UIContext context, Collection<String> existing, Consumer<String> onAdd, Consumer<String> onRemove)
    {
        ShaderMenu menu = null;
        Map<String, String> lang = Collections.emptyMap();

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
            /* An Iris build whose option-menu API differs from the one we compiled against: no
             * menu to mirror, so every animatable option goes on the "not in the menu" page */
            menu = flatMenu();
        }

        UICurvePicker picker = new UICurvePicker(new ShaderMenuBrowser(menu, lang), existing, onAdd, onRemove);

        UIOverlay.addOverlay(context, picker, picker.preferredWidth(), picker.preferredHeight());
    }

    private static ShaderMenu flatMenu()
    {
        Set<String> curvable = ShaderCurves.getCurvable();

        if (curvable.isEmpty())
        {
            return null;
        }

        ShaderMenu menu = new ShaderMenu("", new ShaderMenu.Screen(ShaderMenu.MAIN_SCREEN, 2, Collections.emptyList()), new HashMap<>());

        for (String optionId : curvable)
        {
            menu.notInMenu.add(ShaderMenu.Cell.option(optionId, false, false, true, null, null, Collections.emptyList(), -1));
        }

        return menu;
    }

    private UICurvePicker(ShaderMenuBrowser browser, Collection<String> existing, Consumer<String> onAdd, Consumer<String> onRemove)
    {
        super(UIKeys.CAMERA_PANELS_CURVE_ADD);

        ShaderMenu menu = browser.menu;

        this.browser = browser;
        this.added = new HashSet<>(existing);
        this.onAdd = onAdd;
        this.onRemove = onRemove;
        this.columns = menu == null ? 2 : Math.max(2, menu.mainScreen.columnCount);

        this.browser.addPage(NOT_IN_MENU, UIKeys.CAMERA_PANELS_CURVES_NOT_IN_MENU.get());

        this.search = new UITextbox(100, this::filter);
        this.search.placeholder(UIKeys.GENERAL_SEARCH);
        this.search.relative(this.content).xy(PADDING, PADDING).w(1F, -PADDING * 2).h(SEARCH_HEIGHT);

        this.worldGrid = new UIElement();
        this.worldGrid.relative(this.content).w(1F).h(this.worldHeight());
        this.worldGrid.grid(MARGIN).items(this.columns).padding(PADDING).height(UICurveCell.HEIGHT);

        this.header = new Header();
        this.header.callback = this::goTo;
        this.header.relative(this.content).w(1F).h(HEADER_HEIGHT);

        this.view = new UIScrollView();
        this.view.relative(this.content).w(1F);
        this.view.scroll.scrollSpeed = 20;
        this.viewGrid = this.view.grid(MARGIN);
        this.viewGrid.padding(PADDING).height(UICurveCell.HEIGHT);

        this.content.add(this.search, this.worldGrid, this.header, this.view);

        if (menu != null)
        {
            for (ShaderMenu.Cell cell : menu.notInMenu)
            {
                if (cell.curvable)
                {
                    this.notInMenu.add(cell);
                }
            }

            this.notInMenu.sort((a, b) -> browser.optionLabel(a.optionId).compareToIgnoreCase(browser.optionLabel(b.optionId)));
        }

        this.refresh();
    }

    /* === Sizing === */

    private int preferredWidth()
    {
        int content = this.columns * COLUMN_WIDTH + (this.columns - 1) * MARGIN + PADDING * 2;

        return Math.min(MAX_WIDTH, content + ICONS_WIDTH);
    }

    /** Fits the tallest screen of the menu, so browsing doesn't make the panel jump. */
    private int preferredHeight()
    {
        ShaderMenu menu = this.browser.menu;
        int height = TITLE_HEIGHT + PADDING + SEARCH_HEIGHT + this.worldHeight() + HEADER_HEIGHT;
        int menuHeight = PADDING;

        if (menu != null)
        {
            ShaderMenu.Screen main = menu.mainScreen;

            menuHeight = gridHeight(this.notInMenu.size(), this.columns);
            menuHeight = Math.max(menuHeight, gridHeight(main.cells.size() + (this.notInMenu.isEmpty() ? 0 : 1), main.columnCount));

            for (ShaderMenu.Screen screen : menu.subScreens.values())
            {
                menuHeight = Math.max(menuHeight, gridHeight(screen.cells.size(), screen.columnCount));
            }

            menuHeight = Math.min(MAX_MENU_HEIGHT, menuHeight);
        }

        return height + menuHeight;
    }

    private int worldHeight()
    {
        return gridHeight(WorldCurve.ALL.size(), this.columns);
    }

    private static int gridHeight(int count, int columns)
    {
        columns = Math.max(1, columns);

        int rows = Math.max(1, (count + columns - 1) / columns);

        return rows * UICurveCell.HEIGHT + (rows - 1) * MARGIN + PADDING * 2;
    }

    /* === State === */

    private void filter(String text)
    {
        this.browser.setQuery(text);
        this.refresh();
    }

    private void open(String screenId)
    {
        if (this.browser.open(screenId))
        {
            this.refresh();
        }
    }

    /** A breadcrumb was clicked: back up to that screen. */
    private void goTo(int depth)
    {
        this.browser.goTo(depth);
        this.refresh();
    }

    /** A curve cell was clicked: adds the track, or takes it out if the clip has it already. */
    private void toggle(String channelId, UICurveCell cell)
    {
        if (this.added.remove(channelId))
        {
            this.onRemove.accept(channelId);
            cell.added(false);
        }
        else
        {
            this.added.add(channelId);
            this.onAdd.accept(channelId);
            cell.added(true);
        }
    }

    /** Enter while searching adds the first hit that isn't in the clip yet. */
    @Override
    public void confirm()
    {
        if (!this.browser.isSearching())
        {
            return;
        }

        for (UICurveCell cell : this.view.getChildren(UICurveCell.class))
        {
            if (cell.kind == UICurveCell.Kind.CURVE && !cell.isAdded())
            {
                cell.activate();

                return;
            }
        }
    }

    /* === Building the parts === */

    private void refresh()
    {
        boolean searching = this.browser.isSearching();
        int y = PADDING + SEARCH_HEIGHT;

        this.worldGrid.setVisible(!searching);

        if (searching)
        {
            y += MARGIN;
        }
        else
        {
            /* The grid's own padding keeps it off the search field */
            this.worldGrid.y(y);
            y += this.worldHeight();
        }

        this.header.y(y);
        y += HEADER_HEIGHT;
        this.view.y(y).h(1F, -y);

        this.worldGrid.removeAll();

        for (WorldCurve curve : WorldCurve.ALL)
        {
            this.worldGrid.add(this.worldCell(curve));
        }

        this.view.removeAll();

        if (searching)
        {
            this.fillResults();
        }
        else
        {
            this.fillScreen();
        }

        this.view.scroll.scrollTo(0);

        if (this.hasParent())
        {
            this.resize();
        }
    }

    private void fillScreen()
    {
        ShaderMenu menu = this.browser.menu;

        if (menu == null)
        {
            this.header.crumbs(Collections.singletonList(UIKeys.CAMERA_PANELS_CURVES_NO_SHADERS.get()), true);

            return;
        }

        List<String> crumbs = new ArrayList<>();

        for (String screenId : this.browser.getPath())
        {
            crumbs.add(this.browser.screenTitle(screenId));
        }

        this.header.crumbs(crumbs, false);

        String screenId = this.browser.getScreen();

        if (NOT_IN_MENU.equals(screenId))
        {
            this.viewGrid.items(this.columns);

            for (ShaderMenu.Cell cell : this.notInMenu)
            {
                this.view.add(this.optionCell(cell));
            }

            return;
        }

        ShaderMenu.Screen screen = menu.getScreen(screenId);

        if (screen == null)
        {
            return;
        }

        this.viewGrid.items(Math.max(1, screen.columnCount));

        for (ShaderMenu.Cell cell : screen.cells)
        {
            this.view.add(this.menuCell(cell));
        }

        if (screen == menu.mainScreen && !this.notInMenu.isEmpty())
        {
            this.view.add(this.linkCell(NOT_IN_MENU, this.notInMenu.size()));
        }
    }

    private void fillResults()
    {
        String query = this.browser.getQuery();
        String worldTitle = UIKeys.CAMERA_PANELS_CURVES_WORLD.get();
        String notInMenuTitle = this.browser.screenTitle(NOT_IN_MENU);
        int found = 0;

        this.viewGrid.items(1);

        for (WorldCurve curve : WorldCurve.ALL)
        {
            if (ShaderMenuBrowser.matches(curve.label.get(), curve.id, query))
            {
                this.view.add(this.curveCell(curve.id, curve.label.get()).icon(curve.icon).note(worldTitle));
                found += 1;
            }
        }

        for (ShaderMenuBrowser.Hit hit : this.browser.index((cell) -> cell.curvable))
        {
            if (hit.matches(query))
            {
                this.view.add(this.optionCell(hit.cell).note(hit.where));
                found += 1;
            }
        }

        for (ShaderMenu.Cell cell : this.notInMenu)
        {
            if (ShaderMenuBrowser.matches(this.browser.optionLabel(cell.optionId), cell.optionId, query))
            {
                this.view.add(this.optionCell(cell).note(notInMenuTitle));
                found += 1;
            }
        }

        IKey title = found == 0 ? UIKeys.CAMERA_PANELS_CURVES_NOT_FOUND : UIKeys.CAMERA_PANELS_CURVES_FOUND.format(found);

        this.header.crumbs(Collections.singletonList(title.get()), true);
    }

    private UICurveCell curveCell(String channelId, String label)
    {
        return UICurveCell.curve(label, (cell) -> this.toggle(channelId, cell)).added(this.added.contains(channelId));
    }

    private UICurveCell worldCell(WorldCurve curve)
    {
        UICurveCell cell = this.curveCell(curve.id, curve.label.get()).icon(curve.icon);

        if (curve.color != null)
        {
            cell.swatch(curve.color.getAsInt());
        }
        else
        {
            Double value = curve.value.get();

            if (value != null)
            {
                cell.note(UINumericInput.format(value));
            }
        }

        cell.tooltip(curve.label);

        return cell;
    }

    /** An animatable option, with its value now for a note */
    private UICurveCell optionCell(ShaderMenu.Cell option)
    {
        String label = this.browser.optionLabel(option.optionId);
        UICurveCell cell = this.curveCell(CurveClip.SHADER_CURVES_PREFIX + option.optionId, label);

        cell.note(this.browser.valueLabel(option));
        cell.tooltip(IKey.constant(label + " (" + option.optionId + ")"));

        return cell;
    }

    private UICurveCell menuCell(ShaderMenu.Cell cell)
    {
        if (cell.type == ShaderMenu.CellType.OPTION && cell.optionId != null)
        {
            if (cell.curvable)
            {
                return this.optionCell(cell);
            }

            String label = this.browser.optionLabel(cell.optionId);
            UICurveCell element = UICurveCell.option(label).note(this.browser.valueLabel(cell));

            element.tooltip(IKey.constant(label + " (" + cell.optionId + ")"));

            return element;
        }
        else if (cell.type == ShaderMenu.CellType.LINK && cell.targetScreenId != null)
        {
            return this.linkCell(cell.targetScreenId, this.browser.countOptions(cell.targetScreenId, (option) -> option.curvable));
        }
        else if (cell.type == ShaderMenu.CellType.PROFILE)
        {
            return UICurveCell.option(UIKeys.CAMERA_PANELS_SHADER_PROFILE.get()).note(this.browser.profileLabel(cell.profileName));
        }

        return UICurveCell.empty();
    }

    private UICurveCell linkCell(String screenId, int curves)
    {
        return UICurveCell.link(this.browser.screenTitle(screenId), (cell) -> this.open(screenId)).note("›").muted(curves == 0);
    }

    /**
     * A section's title line. With several crumbs it's the path down the pack's menu, and every
     * crumb but the last takes back up to its screen; a muted one is a plain grey note.
     */
    private static class Header extends UIElement
    {
        private static final int GAP = 6;
        private static final String SEPARATOR = "›";

        public IntConsumer callback;

        private final List<String> crumbs = new ArrayList<>();
        private boolean muted;

        /* Found during render, so a click knows what's under it */
        private int hovered = -1;

        public void crumbs(List<String> crumbs, boolean muted)
        {
            this.crumbs.clear();
            this.crumbs.addAll(crumbs);
            this.muted = muted;
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.hovered >= 0 && this.callback != null && this.area.isInside(context))
            {
                UIUtils.playClick();
                this.callback.accept(this.hovered);

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();
            boolean inside = this.area.isInside(context);
            int x = this.area.x + PADDING;
            int textY = this.area.my(font.getHeight());
            int last = this.crumbs.size() - 1;

            this.hovered = -1;

            for (int i = 0; i <= last; i++)
            {
                if (i > 0)
                {
                    context.batcher.text(SEPARATOR, x, textY, Colors.GRAY);
                    x += font.getWidth(SEPARATOR) + GAP;
                }

                String label = this.crumbs.get(i);

                if (i == last)
                {
                    label = font.limitToWidth(label, this.area.ex() - PADDING - x);
                }

                int w = font.getWidth(label);
                boolean hover = inside && !this.muted && i < last && context.mouseX >= x - GAP / 2 && context.mouseX < x + w + GAP / 2;
                int color = this.muted ? Colors.GRAY : (i == last ? Colors.WHITE : (hover ? Colors.LIGHTEST_GRAY : Colors.LIGHTER_GRAY));

                if (hover)
                {
                    this.hovered = i;
                }

                context.batcher.textShadow(label, x, textY, color);
                x += w + GAP;
            }

            if (this.hovered >= 0)
            {
                context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            }

            super.render(context);
        }
    }
}
