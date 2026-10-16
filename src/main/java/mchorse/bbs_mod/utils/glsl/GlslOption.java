package mchorse.bbs_mod.utils.glsl;

/**
 * A shader pack option that may be turned into a uniform: an on/off {@code #define NAME} (or its
 * commented out {@code //#define NAME}), or a {@code #define NAME value // [values]} with numbers.
 */
public class GlslOption
{
    public enum Kind
    {
        /** On/off: the uniform is an int, 1 when on */
        BOOLEAN,
        INT,
        FLOAT
    }

    public final String name;
    public final Kind kind;

    public GlslOption(String name, Kind kind)
    {
        this.name = name;
        this.kind = kind;
    }

    public boolean isBoolean()
    {
        return this.kind == Kind.BOOLEAN;
    }

    public String glslType()
    {
        return this.kind == Kind.FLOAT ? "float" : "int";
    }
}
