package mchorse.bbs_mod.utils.glsl;

/**
 * A piece of GLSL source. {@link GlslLexer} covers the source with tokens without gaps, so their
 * texts put together give back the source exactly, comments and line breaks included. That's what
 * lets a rewrite touch only the tokens it means to change.
 */
public class GlslToken
{
    public enum Type
    {
        IDENTIFIER, NUMBER, PUNCTUATION, SPACE, NEWLINE, LINE_COMMENT, BLOCK_COMMENT,
        /** A whole preprocessor line, from its '#' up to (not including) the line break */
        DIRECTIVE,
        OTHER
    }

    public final Type type;
    public final String text;
    public final int start;

    public GlslToken(Type type, String text, int start)
    {
        this.type = type;
        this.text = text;
        this.start = start;
    }

    public int end()
    {
        return this.start + this.text.length();
    }

    public boolean isTrivia()
    {
        return this.type == Type.SPACE || this.type == Type.NEWLINE || this.isComment();
    }

    public boolean isComment()
    {
        return this.type == Type.LINE_COMMENT || this.type == Type.BLOCK_COMMENT;
    }

    public boolean isIdentifier()
    {
        return this.type == Type.IDENTIFIER;
    }

    /** Whether it's this identifier or punctuation */
    public boolean is(String text)
    {
        return (this.type == Type.IDENTIFIER || this.type == Type.PUNCTUATION) && this.text.equals(text);
    }

    @Override
    public String toString()
    {
        return this.type + "(" + this.text + ")";
    }
}
