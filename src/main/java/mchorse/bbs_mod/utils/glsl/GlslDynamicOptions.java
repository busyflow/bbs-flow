package mchorse.bbs_mod.utils.glsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns shader pack options into uniforms in a GLSL source that hasn't been through the
 * preprocessor yet, so they can change while the game runs. A value option's {@code #define}
 * gets the uniform as its value; preprocessor conditionals that depend on options become
 * runtime {@code if}s (inside functions) or unconditional declarations (outside them), macros
 * that differ between option values get a definition that chooses at runtime, and the
 * declarations around are fixed up so the code stays valid for every value.
 *
 * <p>Everything else stays as it is, byte for byte, comments included: Iris and OptiFine read some
 * of them. Conditionals that don't depend on the options are left to the preprocessor.</p>
 *
 * <p>An option that can't follow (it chooses render targets or a setting Iris reads from the
 * source, sizes an array, sits in the middle of an expression...) is reported as blocked and
 * left to the preprocessor; the work is redone without it until nothing more gets blocked.</p>
 */
public class GlslDynamicOptions
{
    private static final int MAX_PASSES = 64;

    public static class Result
    {
        /** The source, transformed or as it was */
        public final String source;

        /** Options that became uniforms here, by name */
        public final Map<String, GlslOption> uniforms;

        /** Options that have to stay constant, with the reason */
        public final Map<String, String> blocked;

        /** The value of each option as the source sets it */
        public final Map<String, String> values;

        /** Conditionals turned into runtime code */
        public final int conditionals;

        /** Why the source couldn't be transformed at all, or null */
        public final String error;

        private Result(String source, Map<String, GlslOption> uniforms, Map<String, String> blocked, Map<String, String> values, int conditionals, String error)
        {
            this.source = source;
            this.uniforms = uniforms;
            this.blocked = blocked;
            this.values = values;
            this.conditionals = conditionals;
            this.error = error;
        }
    }

    /**
     * @param source GLSL with includes resolved and option values applied
     * @param environment macros defined before the source (name, value), like Iris' environment defines
     * @param options options that may become uniforms, by name
     * @param prefix uniform name prefix
     */
    public static Result transform(String source, List<String[]> environment, Map<String, GlslOption> options, String prefix)
    {
        return transform(source, environment, options, prefix, true);
    }

    /**
     * @param conditionals whether conditionals may become runtime code; without it, options
     * that conditionals test stay as they are
     */
    public static Result transform(String source, List<String[]> environment, Map<String, GlslOption> options, String prefix, boolean conditionals)
    {
        List<GlslToken> tokens = GlslLexer.tokenize(source);
        Map<String, GlslOption> present = present(tokens, options);
        Map<String, String> blocked = new LinkedHashMap<>();

        if (present.isEmpty())
        {
            return unchanged(source, blocked, null);
        }

        GlslDirective[] directives = new GlslDirective[tokens.size()];
        List<PpTree.Node> tree;

        try
        {
            tree = PpTree.parse(tokens, directives);
        }
        catch (PpException e)
        {
            return failed(source, present, blocked, e.getMessage());
        }

        Map<String, PpMacro.State> macros = environment(environment);
        Map<String, GlslOption> dynamic = new HashMap<>(present);

        for (int pass = 0; pass < MAX_PASSES && !dynamic.isEmpty(); pass++)
        {
            PpSimulation simulation = new PpSimulation(tokens, directives, macros, dynamic, prefix, conditionals);

            try
            {
                simulation.run(tree);
            }
            catch (PpException e)
            {
                if (e.symbols.isEmpty())
                {
                    return failed(source, present, blocked, e.getMessage());
                }

                block(blocked, dynamic, e.symbols, e.getMessage());

                continue;
            }

            if (!simulation.blocks.isEmpty())
            {
                block(blocked, dynamic, simulation.blocks);

                continue;
            }

            GlslSkeleton skeleton = new GlslSkeleton(tokens, simulation, prefix);

            if (!skeleton.run())
            {
                /* The code around can't be followed: keep every option this source turns on constant */
                Map<String, String> all = new LinkedHashMap<>();
                String where = skeleton.failure < 0 ? "" : " (line " + line(source, tokens.get(skeleton.failure).start) + ")";

                for (String name : dynamic.keySet())
                {
                    all.put(name, "the code around it couldn't be read" + where);
                }

                block(blocked, dynamic, all);

                continue;
            }

            if (!skeleton.blocked.isEmpty())
            {
                block(blocked, dynamic, skeleton.blocked);

                continue;
            }

            Map<String, GlslOption> uniforms = new TreeMap<>();

            for (String name : simulation.seen)
            {
                GlslOption option = dynamic.get(name);

                if (option != null)
                {
                    uniforms.put(name, option);
                }
            }

            if (uniforms.isEmpty())
            {
                return unchanged(source, blocked, simulation.defaultTexts);
            }

            try
            {
                GlslRewriter rewriter = new GlslRewriter(tokens, directives, simulation, skeleton, prefix);
                String result = rewriter.write(uniforms.values());

                return new Result(result, uniforms, blocked, simulation.defaultTexts, rewriter.conditionals, null);
            }
            catch (RuntimeException e)
            {
                return failed(source, present, blocked, e.getMessage());
            }
        }

        return unchanged(source, blocked, null);
    }

    /** Options mentioned in the source at all: as a name, or in a switched off "//#define" */
    private static Map<String, GlslOption> present(List<GlslToken> tokens, Map<String, GlslOption> options)
    {
        Map<String, GlslOption> present = new HashMap<>();

        for (GlslToken token : tokens)
        {
            String name = null;

            if (token.type == GlslToken.Type.IDENTIFIER)
            {
                name = token.text;
            }
            else if (token.type == GlslToken.Type.DIRECTIVE || token.type == GlslToken.Type.LINE_COMMENT)
            {
                for (GlslToken part : GlslLexer.tokenizeFragment(token.text))
                {
                    if (part.isIdentifier() && options.containsKey(part.text))
                    {
                        present.put(part.text, options.get(part.text));
                    }
                }

                continue;
            }

            if (name != null && options.containsKey(name))
            {
                present.put(name, options.get(name));
            }
        }

        return present;
    }

    private static Map<String, PpMacro.State> environment(List<String[]> environment)
    {
        Map<String, PpMacro.State> macros = new HashMap<>();

        /* JCPP's own macros: defined, but their values aren't followed here (a '#' body makes
         * expanding them fail, so a conditional reading one leaves the source as it is) */
        for (String name : new String[] {"__LINE__", "__FILE__", "__COUNTER__"})
        {
            List<GlslToken> body = new ArrayList<>();

            body.add(new GlslToken(GlslToken.Type.PUNCTUATION, "#", 0));
            macros.put(name, PpMacro.State.of(new PpMacro(name, null, body, null)));
        }

        for (String[] define : environment)
        {
            List<GlslToken> body = new ArrayList<>();

            for (GlslToken token : GlslLexer.tokenizeFragment(define[1] == null ? "" : define[1]))
            {
                if (!token.isTrivia())
                {
                    body.add(token);
                }
            }

            macros.put(define[0], PpMacro.State.of(new PpMacro(define[0], null, body, null)));
        }

        return macros;
    }

    private static int line(String source, int offset)
    {
        int line = 1;

        for (int i = 0; i < offset && i < source.length(); i++)
        {
            if (source.charAt(i) == '\n')
            {
                line += 1;
            }
        }

        return line;
    }

    private static void block(Map<String, String> blocked, Map<String, GlslOption> dynamic, Set<String> symbols, String reason)
    {
        for (String symbol : symbols)
        {
            blocked.putIfAbsent(symbol, reason);
            dynamic.remove(symbol);
        }
    }

    private static void block(Map<String, String> blocked, Map<String, GlslOption> dynamic, Map<String, String> reasons)
    {
        for (Map.Entry<String, String> entry : reasons.entrySet())
        {
            blocked.putIfAbsent(entry.getKey(), entry.getValue());
            dynamic.remove(entry.getKey());
        }
    }

    private static Result unchanged(String source, Map<String, String> blocked, Map<String, String> values)
    {
        return new Result(source, Collections.<String, GlslOption>emptyMap(), blocked, values == null ? Collections.<String, String>emptyMap() : values, 0, null);
    }

    /** Nothing could be done: every option present stays constant */
    private static Result failed(String source, Map<String, GlslOption> present, Map<String, String> blocked, String error)
    {
        for (String name : present.keySet())
        {
            blocked.putIfAbsent(name, "the source couldn't be read: " + error);
        }

        return new Result(source, Collections.<String, GlslOption>emptyMap(), blocked, Collections.<String, String>emptyMap(), 0, error);
    }

}
