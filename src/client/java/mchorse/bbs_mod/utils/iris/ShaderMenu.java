package mchorse.bbs_mod.utils.iris;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neutral, Iris-free snapshot of a shader pack's options menu.
 *
 * <p>Built by {@link IrisShaderMenu#buildShaderMenu()} from Iris' {@code OptionMenuContainer}, this
 * model lets the curve picker and the pack's settings mirror the layout of the Iris
 * shader-options menu (screens, columns, sliders, toggles, sub-page links, profiles) without
 * dragging Iris classes into the UI layer.</p>
 */
public class ShaderMenu
{
    /** Sentinel id of the root screen (Iris' main screen has no key). */
    public static final String MAIN_SCREEN = "";

    /** The pack's name as the player knows it, without the archive extension; may be empty. */
    public final String name;

    public final Screen mainScreen;
    public final Map<String, Screen> subScreens;

    /** Animatable options no screen of the menu shows, as cells of their own for a page of them */
    public final List<Cell> notInMenu = new ArrayList<>();

    /** The value the loaded pack runs every option with, the ones no screen shows included */
    public final Map<String, String> values = new HashMap<>();

    /** The pack's profiles in the order it declares them, each with the option values it sets */
    public final Map<String, Map<String, String>> profiles = new LinkedHashMap<>();

    public ShaderMenu(String name, Screen mainScreen, Map<String, Screen> subScreens)
    {
        this.name = name;
        this.mainScreen = mainScreen;
        this.subScreens = subScreens;
    }

    /**
     * Resolve a screen by id. {@code null}/empty/{@link #MAIN_SCREEN} returns the root screen;
     * any other id is looked up among the sub-screens (may return {@code null}).
     */
    public Screen getScreen(String id)
    {
        if (id == null || id.isEmpty())
        {
            return this.mainScreen;
        }

        return this.subScreens.get(id);
    }

    public enum CellType
    {
        EMPTY, OPTION, LINK, PROFILE
    }

    /**
     * A single screen: an ordered list of {@link Cell cells} laid out across {@link #columnCount}
     * columns, exactly mirroring the order Iris uses (including {@link CellType#EMPTY} separators).
     */
    public static class Screen
    {
        public final String id;
        public final int columnCount;
        public final List<Cell> cells;

        public Screen(String id, int columnCount, List<Cell> cells)
        {
            this.id = id;
            this.columnCount = columnCount;
            this.cells = cells;
        }
    }

    /**
     * A single menu cell. Field relevance depends on {@link #type}; use the static factories.
     */
    public static class Cell
    {
        public final CellType type;

        /* OPTION */
        public final String optionId;
        public final boolean slider;
        public final boolean booleanOption;
        public final boolean curvable;
        public final String currentValue;
        public final String defaultValue;
        public final List<String> allowedValues;
        public final int valueIndex;

        /* LINK */
        public final String targetScreenId;

        /* PROFILE */
        public final String profileName;
        public final int profileCount;

        private Cell(CellType type, String optionId, boolean slider, boolean booleanOption, boolean curvable, String currentValue, String defaultValue, List<String> allowedValues, int valueIndex, String targetScreenId, String profileName, int profileCount)
        {
            this.type = type;
            this.optionId = optionId;
            this.slider = slider;
            this.booleanOption = booleanOption;
            this.curvable = curvable;
            this.currentValue = currentValue;
            this.defaultValue = defaultValue;
            this.allowedValues = allowedValues;
            this.valueIndex = valueIndex;
            this.targetScreenId = targetScreenId;
            this.profileName = profileName;
            this.profileCount = profileCount;
        }

        public static Cell empty()
        {
            return new Cell(CellType.EMPTY, null, false, false, false, null, null, Collections.emptyList(), -1, null, null, 0);
        }

        public static Cell option(String optionId, boolean slider, boolean booleanOption, boolean curvable, String currentValue, String defaultValue, List<String> allowedValues, int valueIndex)
        {
            return new Cell(CellType.OPTION, optionId, slider, booleanOption, curvable, currentValue, defaultValue, allowedValues, valueIndex, null, null, 0);
        }

        public static Cell link(String targetScreenId)
        {
            return new Cell(CellType.LINK, null, false, false, false, null, null, Collections.emptyList(), -1, targetScreenId, null, 0);
        }

        public static Cell profile(String profileName, int profileCount)
        {
            return new Cell(CellType.PROFILE, null, false, false, false, null, null, Collections.emptyList(), -1, null, profileName, profileCount);
        }
    }
}
