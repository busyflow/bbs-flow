package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.iris.ShaderMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finding the way around a shader pack's options menu, for the shader panel: which screen is on
 * show and the way back up from it, the search query, the names the pack's translations give
 * screens, options and values, and the options a screen leads to.
 *
 * <p>Its owner can add pages of its own that open like the pack's screens (the shader panel's
 * page of animatable options the menu leaves out).</p>
 */
public class ShaderMenuBrowser
{
    /** The pack's menu, or null when there is no pack to browse */
    public final ShaderMenu menu;
    public final Map<String, String> lang;

    private final Map<String, String> pages = new HashMap<>();

    /** Screen ids from the main screen down to the one on show */
    private final List<String> path = new ArrayList<>();
    private String query = "";

    public ShaderMenuBrowser(ShaderMenu menu, Map<String, String> lang)
    {
        this.menu = menu;
        this.lang = lang;

        this.path.add(ShaderMenu.MAIN_SCREEN);
    }

    /** A page of the owner's own, opened by its id like one of the pack's screens */
    public void addPage(String id, String title)
    {
        this.pages.put(id, title);
    }

    public boolean isPage(String id)
    {
        return this.pages.containsKey(id);
    }

    /* === Where it is === */

    /** Id of the screen on show */
    public String getScreen()
    {
        return this.path.get(this.path.size() - 1);
    }

    public boolean open(String screenId)
    {
        if (this.pages.containsKey(screenId) || (this.menu != null && this.menu.getScreen(screenId) != null))
        {
            this.path.add(screenId);

            return true;
        }

        return false;
    }

    /** Back up one screen; the main screen stays. */
    public void back()
    {
        if (!this.isMain())
        {
            this.path.remove(this.path.size() - 1);
        }
    }

    public boolean isMain()
    {
        return this.path.size() == 1;
    }

    /** Back up to the screen at the given depth of the path (0 is the main screen). */
    private void goTo(int depth)
    {
        while (this.path.size() > depth + 1)
        {
            this.path.remove(this.path.size() - 1);
        }
    }

    /**
     * Go down the way another browser went, as far as this menu has its screens, and take its
     * query: the pack got reloaded, and the screen on show may be gone from it.
     */
    public void follow(ShaderMenuBrowser other)
    {
        this.goTo(0);

        for (int i = 1; i < other.path.size(); i++)
        {
            if (!this.open(other.path.get(i)))
            {
                break;
            }
        }

        this.query = other.query;
    }

    /* === Search === */

    public boolean isSearching()
    {
        return !this.query.isEmpty();
    }

    public String getQuery()
    {
        return this.query;
    }

    public void setQuery(String text)
    {
        this.query = text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The options of the menu that pass the filter, each once, where it's met first going down
     * from the main screen.
     */
    public List<Hit> index(Predicate<ShaderMenu.Cell> filter)
    {
        List<Hit> hits = new ArrayList<>();

        if (this.menu != null)
        {
            this.indexScreen(ShaderMenu.MAIN_SCREEN, this.screenTitle(ShaderMenu.MAIN_SCREEN), filter, new HashSet<>(), new HashSet<>(), hits);
        }

        return hits;
    }

    /**
     * @param where the screen's place for a hit to show: the pack's name for the main screen, the
     *              path of titles below it for the rest
     */
    private void indexScreen(String screenId, String where, Predicate<ShaderMenu.Cell> filter, Set<String> screens, Set<String> options, List<Hit> hits)
    {
        ShaderMenu.Screen screen = this.menu.getScreen(screenId);

        if (screen == null || !screens.add(screenId))
        {
            return;
        }

        for (ShaderMenu.Cell cell : screen.cells)
        {
            if (cell.type == ShaderMenu.CellType.OPTION && cell.optionId != null && filter.test(cell) && options.add(cell.optionId))
            {
                hits.add(new Hit(cell.optionId, cell, this.optionLabel(cell.optionId), where));
            }
        }

        for (ShaderMenu.Cell cell : screen.cells)
        {
            if (cell.type == ShaderMenu.CellType.LINK && cell.targetScreenId != null)
            {
                String title = this.screenTitle(cell.targetScreenId);

                this.indexScreen(cell.targetScreenId, screen == this.menu.mainScreen ? title : where + " › " + title, filter, screens, options, hits);
            }
        }
    }

    /** How many options that pass the filter a screen leads to, its sub-screens included. */
    public int countOptions(String screenId, Predicate<ShaderMenu.Cell> filter)
    {
        Set<String> options = new HashSet<>();

        if (this.menu != null)
        {
            this.collectOptions(screenId, filter, new HashSet<>(), options);
        }

        return options.size();
    }

    private void collectOptions(String screenId, Predicate<ShaderMenu.Cell> filter, Set<String> screens, Set<String> options)
    {
        ShaderMenu.Screen screen = this.menu.getScreen(screenId);

        if (screen == null || !screens.add(screenId))
        {
            return;
        }

        for (ShaderMenu.Cell cell : screen.cells)
        {
            if (cell.type == ShaderMenu.CellType.OPTION && cell.optionId != null && filter.test(cell))
            {
                options.add(cell.optionId);
            }
            else if (cell.type == ShaderMenu.CellType.LINK && cell.targetScreenId != null)
            {
                this.collectOptions(cell.targetScreenId, filter, screens, options);
            }
        }
    }

    /* === Names === */

    public String screenTitle(String screenId)
    {
        if (screenId == null || screenId.isEmpty())
        {
            return this.menu == null || this.menu.name.isEmpty() ? UIKeys.CAMERA_PANELS_CURVES_SHADER.get() : this.menu.name;
        }

        String page = this.pages.get(screenId);

        return page != null ? page : this.lang.getOrDefault("screen." + screenId, screenId);
    }

    public String optionLabel(String optionId)
    {
        return this.lang.getOrDefault("option." + optionId, optionId);
    }

    /** The pack's description of an option, shown by Iris as its tooltip; may be empty */
    public String optionComment(String optionId)
    {
        return this.lang.getOrDefault("option." + optionId + ".comment", "");
    }

    public String profileLabel(String profile)
    {
        return profile == null ? UIKeys.CAMERA_PANELS_SHADER_CUSTOM.get() : this.lang.getOrDefault("profile." + profile, profile);
    }

    /** A value the way Iris shows it: on/off, or the pack's prefix, name of the value and suffix */
    public String valueLabel(String optionId, String value, boolean booleanOption)
    {
        if (booleanOption)
        {
            return "true".equals(value) ? UIKeys.CAMERA_PANELS_SHADER_ON.get() : UIKeys.CAMERA_PANELS_SHADER_OFF.get();
        }

        value = value == null ? "" : value;

        String prefix = this.lang.getOrDefault("prefix." + optionId, "");
        String suffix = this.lang.getOrDefault("suffix." + optionId, "");
        String localized = this.lang.getOrDefault("value." + optionId + "." + value, value);

        return prefix + localized + suffix;
    }

    public String valueLabel(ShaderMenu.Cell cell)
    {
        return this.valueLabel(cell.optionId, cell.currentValue, cell.booleanOption);
    }

    /** Whether a name or an id holds the (lower case) query */
    public static boolean matches(String label, String id, String query)
    {
        return label.toLowerCase(Locale.ROOT).contains(query) || id.toLowerCase(Locale.ROOT).contains(query);
    }

    /** An option found in the menu */
    public static class Hit
    {
        public final String optionId;
        public final ShaderMenu.Cell cell;
        public final String label;
        public final String where;

        public Hit(String optionId, ShaderMenu.Cell cell, String label, String where)
        {
            this.optionId = optionId;
            this.cell = cell;
            this.label = label;
            this.where = where;
        }

        public boolean matches(String query)
        {
            return ShaderMenuBrowser.matches(this.label, this.optionId, query);
        }
    }
}
