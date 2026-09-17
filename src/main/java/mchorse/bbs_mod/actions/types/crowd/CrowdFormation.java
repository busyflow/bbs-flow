package mchorse.bbs_mod.actions.types.crowd;

public enum CrowdFormation
{
    CIRCLE("Circle"),
    CIRCLE_OUTLINE("Circle outline"),
    DONUT("Donut"),
    LINE("Line"),
    GRID("Grid"),
    SQUARE("Square"),
    SQUARE_OUTLINE("Square outline"),
    BOX("Box"),
    BOX_OUTLINE("Box outline"),
    /**
     * Not a shape at all — the crowd fills ground painted by hand in the editor, at even density
     * over however many separate patches were painted. Appended last so the saved ordinals of
     * every formation above it stay put.
     */
    PAINT("Paint");

    public final String title;

    CrowdFormation(String title)
    {
        this.title = title;
    }

    public static CrowdFormation get(int index)
    {
        CrowdFormation[] values = values();

        if (index < 0 || index >= values.length)
        {
            return CIRCLE;
        }

        return values[index];
    }
}
