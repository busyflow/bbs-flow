package mchorse.bbs_mod.utils.glsl;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits GLSL into {@link GlslToken}s without losing a single character. A '#' that opens a line
 * (only spaces or block comments before it, as JCPP sees it) takes the rest of the logical line
 * as one {@link GlslToken.Type#DIRECTIVE} token: backslash continuations and block comments
 * spanning lines belong to it.
 */
public class GlslLexer
{
    private static final String[] PUNCTUATION = {
        "<<=", ">>=", "++", "--", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "^^",
        "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "##"
    };

    private static final String SINGLE_PUNCTUATION = "{}[]()<>;:,.?!~+-*/%&|^=#";

    public static List<GlslToken> tokenize(String source)
    {
        return tokenize(source, true);
    }

    /** Split a piece of text, such as a directive, without recognising directives in it. */
    public static List<GlslToken> tokenizeFragment(String text)
    {
        return tokenize(text, false);
    }

    private static List<GlslToken> tokenize(String s, boolean directives)
    {
        List<GlslToken> tokens = new ArrayList<>();
        int n = s.length();
        int i = 0;
        boolean lineStart = true;

        while (i < n)
        {
            char c = s.charAt(i);
            int start = i;
            GlslToken.Type type;

            if (c == '\n' || c == '\r')
            {
                i = skipLineBreak(s, i);
                type = GlslToken.Type.NEWLINE;
            }
            else if (c == ' ' || c == '\t' || c == '\f' || c == 0x0B || isContinuation(s, i))
            {
                while (i < n)
                {
                    char d = s.charAt(i);

                    if (d == ' ' || d == '\t' || d == '\f' || d == 0x0B)
                    {
                        i += 1;
                    }
                    else if (isContinuation(s, i))
                    {
                        i = skipLineBreak(s, i + 1);
                    }
                    else
                    {
                        break;
                    }
                }

                type = GlslToken.Type.SPACE;
            }
            else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/')
            {
                i = endOfLineComment(s, i);
                type = GlslToken.Type.LINE_COMMENT;
            }
            else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*')
            {
                i = endOfBlockComment(s, i);
                type = GlslToken.Type.BLOCK_COMMENT;
            }
            else if (c == '#' && directives && lineStart)
            {
                i = endOfDirective(s, i);
                type = GlslToken.Type.DIRECTIVE;
            }
            else if (isIdentifierStart(c))
            {
                i += 1;

                while (i < n && isIdentifierPart(s.charAt(i)))
                {
                    i += 1;
                }

                type = GlslToken.Type.IDENTIFIER;
            }
            else if (isDigit(c) || (c == '.' && i + 1 < n && isDigit(s.charAt(i + 1))))
            {
                i = endOfNumber(s, i);
                type = GlslToken.Type.NUMBER;
            }
            else
            {
                i = endOfPunctuation(s, i);
                type = i > start ? GlslToken.Type.PUNCTUATION : GlslToken.Type.OTHER;

                if (i == start)
                {
                    i += 1;
                }
            }

            String text = s.substring(start, i);

            tokens.add(new GlslToken(type, text, start));

            /* Like JCPP, block comments don't end the run of blanks a directive may follow */
            if (type == GlslToken.Type.NEWLINE)
            {
                lineStart = true;
            }
            else if (type != GlslToken.Type.SPACE && type != GlslToken.Type.BLOCK_COMMENT)
            {
                lineStart = false;
            }
        }

        return tokens;
    }

    private static boolean isContinuation(String s, int i)
    {
        return s.charAt(i) == '\\' && i + 1 < s.length() && (s.charAt(i + 1) == '\n' || s.charAt(i + 1) == '\r');
    }

    private static int skipLineBreak(String s, int i)
    {
        if (s.charAt(i) == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n')
        {
            return i + 2;
        }

        return i + 1;
    }

    private static int endOfLineComment(String s, int i)
    {
        int n = s.length();

        while (i < n)
        {
            char c = s.charAt(i);

            if (isContinuation(s, i))
            {
                i = skipLineBreak(s, i + 1);
            }
            else if (c == '\n' || c == '\r')
            {
                break;
            }
            else
            {
                i += 1;
            }
        }

        return i;
    }

    private static int endOfBlockComment(String s, int i)
    {
        int end = s.indexOf("*/", i + 2);

        return end < 0 ? s.length() : end + 2;
    }

    private static int endOfDirective(String s, int i)
    {
        int n = s.length();

        while (i < n)
        {
            char c = s.charAt(i);

            if (isContinuation(s, i))
            {
                i = skipLineBreak(s, i + 1);
            }
            else if (c == '\n' || c == '\r')
            {
                break;
            }
            else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*')
            {
                i = endOfBlockComment(s, i);
            }
            else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/')
            {
                i = endOfLineComment(s, i);
            }
            else
            {
                i += 1;
            }
        }

        return i;
    }

    /** A preprocessing number, as in C: digits, letters, dots and the sign right after an exponent. */
    private static int endOfNumber(String s, int i)
    {
        int n = s.length();

        i += 1;

        while (i < n)
        {
            char c = s.charAt(i);

            if (isIdentifierPart(c) || c == '.')
            {
                i += 1;
            }
            else if ((c == '+' || c == '-') && isExponent(s.charAt(i - 1)))
            {
                i += 1;
            }
            else
            {
                break;
            }
        }

        return i;
    }

    private static boolean isExponent(char c)
    {
        return c == 'e' || c == 'E' || c == 'p' || c == 'P';
    }

    private static int endOfPunctuation(String s, int i)
    {
        for (String punctuation : PUNCTUATION)
        {
            if (s.startsWith(punctuation, i))
            {
                return i + punctuation.length();
            }
        }

        return SINGLE_PUNCTUATION.indexOf(s.charAt(i)) >= 0 ? i + 1 : i;
    }

    public static boolean isIdentifierStart(char c)
    {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    public static boolean isIdentifierPart(char c)
    {
        return isIdentifierStart(c) || isDigit(c);
    }

    private static boolean isDigit(char c)
    {
        return c >= '0' && c <= '9';
    }
}
