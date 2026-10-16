package mchorse.bbs_mod.utils.glsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A preprocessor line taken apart: its name ("define", "if", ...), the significant tokens after
 * the name and the comments written on the line. For {@code #define} also the macro's name,
 * parameters and body. Token offsets are relative to the directive's own text.
 */
public class GlslDirective
{
    /** Directive name; empty for a lone '#' */
    public final String name;
    public final String text;

    /** Every token of the line, the leading '#' included */
    public final List<GlslToken> tokens;

    /** Significant tokens after the name */
    public final List<GlslToken> payload;

    public final List<String> comments;

    /* #define only */
    public String macro;

    /** Null for an object-like macro */
    public List<String> params;
    public boolean variadic;

    /** Significant tokens of the replacement */
    public List<GlslToken> body = Collections.emptyList();

    private GlslDirective(String name, String text, List<GlslToken> tokens, List<GlslToken> payload, List<String> comments)
    {
        this.name = name;
        this.text = text;
        this.tokens = tokens;
        this.payload = payload;
        this.comments = comments;
    }

    public static GlslDirective parse(String text)
    {
        List<GlslToken> tokens = GlslLexer.tokenizeFragment(text);
        List<GlslToken> significant = new ArrayList<>();
        List<String> comments = new ArrayList<>();

        for (GlslToken token : tokens)
        {
            if (token.isComment())
            {
                comments.add(token.text);
            }
            else if (!token.isTrivia())
            {
                significant.add(token);
            }
        }

        /* significant.get(0) is the '#' */
        String name = significant.size() > 1 && significant.get(1).isIdentifier() ? significant.get(1).text : "";
        List<GlslToken> payload = significant.size() > 2 ? significant.subList(2, significant.size()) : Collections.emptyList();
        GlslDirective directive = new GlslDirective(name, text, tokens, payload, comments);

        if (name.equals("define") && !payload.isEmpty() && payload.get(0).isIdentifier())
        {
            directive.parseDefine();
        }

        return directive;
    }

    private void parseDefine()
    {
        GlslToken name = this.payload.get(0);
        int index = 1;

        this.macro = name.text;

        /* Function-like only when the parenthesis touches the name */
        if (index < this.payload.size() && this.payload.get(index).is("(") && this.payload.get(index).start == name.end())
        {
            List<String> params = new ArrayList<>();

            index += 1;

            while (index < this.payload.size() && !this.payload.get(index).is(")"))
            {
                GlslToken token = this.payload.get(index);

                if (token.isIdentifier())
                {
                    params.add(token.text);
                }
                else if (token.text.equals("...") || token.is("."))
                {
                    this.variadic = true;
                }

                index += 1;
            }

            index += 1;
            this.params = params;
        }

        this.body = index < this.payload.size() ? this.payload.subList(index, this.payload.size()) : Collections.<GlslToken>emptyList();
    }

    public boolean isConditional()
    {
        return this.name.equals("if") || this.name.equals("ifdef") || this.name.equals("ifndef")
            || this.name.equals("elif") || this.name.equals("else") || this.name.equals("endif");
    }

    /** The directive's text with the macro body replaced, keeping everything around it */
    public String replaceBody(String replacement)
    {
        if (this.body.isEmpty())
        {
            GlslToken last = this.params != null ? this.findClosingParenthesis() : this.payload.get(0);

            return this.text.substring(0, last.end()) + " " + replacement + this.text.substring(last.end());
        }

        GlslToken first = this.body.get(0);
        GlslToken last = this.body.get(this.body.size() - 1);

        return this.text.substring(0, first.start) + replacement + this.text.substring(last.end());
    }

    private GlslToken findClosingParenthesis()
    {
        for (GlslToken token : this.payload)
        {
            if (token.is(")"))
            {
                return token;
            }
        }

        return this.payload.get(0);
    }

    /** Text of the body with single spaces between tokens */
    public static String join(List<GlslToken> tokens)
    {
        StringBuilder builder = new StringBuilder();

        for (GlslToken token : tokens)
        {
            if (builder.length() > 0)
            {
                builder.append(' ');
            }

            builder.append(token.text);
        }

        return builder.toString();
    }
}
