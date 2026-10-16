package mchorse.bbs_mod.utils.glsl;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Options a shader pack's properties file depends on. Iris preprocesses these files with the
 * options' values (shaders.properties choosing programs, buffers and custom uniforms,
 * block.properties the block ids), so what they read can't change at runtime. The menu layout
 * (screen, sliders and profile entries, continued over lines ending with a backslash) only lists
 * options, and Iris reads it from the file as it is. "#" lines other than directives are comments.
 */
public class GlslProperties
{
    /** What Iris keeps of the "#" lines: the ones starting with a JCPP command */
    private static final List<String> COMMANDS = Arrays.asList(
        "define", "elif", "else", "endif", "error", "if", "ifdef", "ifndef", "include", "line", "pragma", "undef", "warning", "include_next", "import"
    );

    public static Set<String> usedOptions(String source, Predicate<String> isOption)
    {
        Set<String> used = new LinkedHashSet<>();
        boolean continued = false;
        boolean layout = false;

        for (String line : source.split("\\R", -1))
        {
            String trimmed = line.trim();

            if (!continued)
            {
                if (trimmed.startsWith("#"))
                {
                    if (isDirective(trimmed))
                    {
                        scan(trimmed, isOption, used);
                    }

                    continue;
                }

                layout = isLayout(key(trimmed));
            }

            if (!layout)
            {
                scan(trimmed, isOption, used);
            }

            continued = endsWithContinuation(trimmed);
        }

        return used;
    }

    private static boolean isDirective(String line)
    {
        for (String command : COMMANDS)
        {
            if (line.startsWith("#" + command))
            {
                return true;
            }
        }

        return false;
    }

    private static String key(String line)
    {
        int end = 0;

        while (end < line.length() && "=: \t".indexOf(line.charAt(end)) < 0)
        {
            end += 1;
        }

        return line.substring(0, end);
    }

    private static boolean isLayout(String key)
    {
        return key.equals("screen") || key.startsWith("screen.") || key.equals("sliders") || key.startsWith("profile.");
    }

    /** An odd number of backslashes at the end continues the line */
    private static boolean endsWithContinuation(String line)
    {
        int count = 0;

        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--)
        {
            count += 1;
        }

        return count % 2 == 1;
    }

    private static void scan(String line, Predicate<String> isOption, Set<String> used)
    {
        for (GlslToken token : GlslLexer.tokenizeFragment(line))
        {
            if (token.isIdentifier() && isOption.test(token.text))
            {
                used.add(token.text);
            }
        }
    }
}
