package mchorse.bbs_mod.utils.glsl;

import mchorse.bbs_mod.utils.glsl.PpSimulation.BranchPlan;
import mchorse.bbs_mod.utils.glsl.PpSimulation.Dyn;
import mchorse.bbs_mod.utils.glsl.PpSimulation.IfPlan;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Third stage: writes the transformed source. Only planned tokens change; every other character,
 * comments included, stays where it was. A directive line keeps its comments on the same line
 * after the code that replaces it, and removed text leaves its line breaks behind, so lines keep
 * their numbers (apart from the uniform declarations added after {@code #version}).
 */
class GlslRewriter
{
    private final List<GlslToken> tokens;
    private final GlslDirective[] directives;
    private final PpSimulation simulation;
    private final GlslSkeleton skeleton;
    private final String prefix;

    private final String[] replacement;
    private final Map<Integer, StringBuilder> before = new HashMap<>();
    private final Map<Integer, StringBuilder> after = new HashMap<>();

    int conditionals;

    GlslRewriter(List<GlslToken> tokens, GlslDirective[] directives, PpSimulation simulation, GlslSkeleton skeleton, String prefix)
    {
        this.tokens = tokens;
        this.directives = directives;
        this.simulation = simulation;
        this.skeleton = skeleton;
        this.prefix = prefix;
        this.replacement = new String[tokens.size()];
    }

    String write(Collection<GlslOption> uniforms)
    {
        for (Map.Entry<Integer, String> entry : this.simulation.lineEdits.entrySet())
        {
            this.replacement[entry.getKey()] = this.keepBreaks(entry.getKey(), entry.getValue());
        }

        Map<Dyn, GlslSkeleton.StatementConversion> statements = new HashMap<>();
        Set<Dyn> globals = new HashSet<>(this.skeleton.globalConversions);

        for (GlslSkeleton.StatementConversion conversion : this.skeleton.statementConversions)
        {
            statements.put(conversion.dyn, conversion);
        }

        for (IfPlan plan : this.simulation.plans.values())
        {
            if (!plan.dynamic)
            {
                this.settle(plan);
            }
            else if (statements.containsKey(plan.dyn))
            {
                this.statement(plan, statements.get(plan.dyn));
            }
            else if (globals.contains(plan.dyn))
            {
                this.global(plan);
            }
            else
            {
                throw new IllegalStateException("A conditional depending on options was left unconverted");
            }
        }

        for (int index : this.skeleton.blank)
        {
            /* A conditional's own lines inside a dropped declaration keep the nesting intact */
            if (this.tokens.get(index).type != GlslToken.Type.DIRECTIVE)
            {
                this.replacement[index] = this.keepBreaks(index, "");
            }
        }

        for (Map.Entry<Integer, String> entry : this.skeleton.replace.entrySet())
        {
            this.replacement[entry.getKey()] = entry.getValue();
        }

        for (Map.Entry<Integer, String> entry : this.skeleton.before.entrySet())
        {
            this.before(entry.getKey()).append(entry.getValue());
        }

        for (Map.Entry<Integer, String> entry : this.skeleton.after.entrySet())
        {
            this.after(entry.getKey()).append(entry.getValue());
        }

        this.declare(uniforms);

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < this.tokens.size(); i++)
        {
            StringBuilder prefix = this.before.get(i);
            StringBuilder suffix = this.after.get(i);

            if (prefix != null)
            {
                out.append(prefix);
            }

            out.append(this.replacement[i] != null ? this.replacement[i] : this.tokens.get(i).text);

            if (suffix != null)
            {
                out.append(suffix);
            }
        }

        return out.toString();
    }

    /** A conditional decided here (inside an option's block): keep its taken branch only */
    private void settle(IfPlan plan)
    {
        for (BranchPlan branch : plan.branches)
        {
            this.replacement[branch.branch.index] = this.directive(branch.branch.index, "");

            if (!branch.live)
            {
                this.blankBody(plan, branch);
            }
        }

        this.replacement[plan.node.endif] = this.directive(plan.node.endif, "");
    }

    /** A conditional inside a function: if / else if / else */
    private void statement(IfPlan plan, GlslSkeleton.StatementConversion conversion)
    {
        boolean first = true;

        this.conditionals += 1;

        for (BranchPlan branch : plan.branches)
        {
            String code;

            if (!branch.live)
            {
                code = "";
                this.blankBody(plan, branch);
            }
            else if (first)
            {
                code = (conversion.sole ? "{ " : "") + conversion.hoist + "if (" + branch.taken.glsl(this.prefix, true) + ") {";
                first = false;
            }
            else if (branch.own.isTrue())
            {
                code = "} else {";
            }
            else
            {
                code = "} else if (" + branch.own.glsl(this.prefix, true) + ") {";
            }

            this.replacement[branch.branch.index] = this.directive(branch.branch.index, code);
        }

        this.replacement[plan.node.endif] = this.directive(plan.node.endif, conversion.sole ? "} }" : "}");
    }

    /** A conditional around declarations: they all stay */
    private void global(IfPlan plan)
    {
        this.conditionals += 1;

        for (BranchPlan branch : plan.branches)
        {
            this.replacement[branch.branch.index] = this.directive(branch.branch.index, "");

            if (!branch.live)
            {
                this.blankBody(plan, branch);
            }
        }

        StringBuilder declarations = this.skeleton.endifText.get(plan.dyn);

        this.replacement[plan.node.endif] = this.directive(plan.node.endif, declarations == null ? "" : declarations.toString().trim());
    }

    private void blankBody(IfPlan plan, BranchPlan branch)
    {
        int from = branch.branch.index + 1;
        int index = plan.branches.indexOf(branch);
        int to = index + 1 < plan.branches.size() ? plan.branches.get(index + 1).branch.index : plan.node.endif;

        for (int i = from; i < to; i++)
        {
            this.replacement[i] = this.keepBreaks(i, "");
        }
    }

    /** New text for a directive line: the code, then the line's comments, then its line breaks */
    private String directive(int index, String code)
    {
        StringBuilder builder = new StringBuilder(code);

        for (String comment : this.directives[index].comments)
        {
            /* JCPP drops comments of directive lines, so Iris never saw these there */
            if (comment.contains("DRAWBUFFERS:") || comment.contains("RENDERTARGETS:"))
            {
                continue;
            }

            if (builder.length() > 0)
            {
                builder.append(' ');
            }

            builder.append(comment);
        }

        return this.keepBreaks(index, builder.toString());
    }

    /** The text followed by as many line breaks as the token had beyond the ones in the text */
    private String keepBreaks(int index, String text)
    {
        String original = this.tokens.get(index).text;
        int missing = breaks(original) - breaks(text);
        StringBuilder builder = new StringBuilder(text);

        for (int i = 0; i < missing; i++)
        {
            builder.append('\n');
        }

        return builder.toString();
    }

    private static int breaks(String text)
    {
        int count = 0;

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);

            if (c == '\n' || (c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n')))
            {
                count += 1;
            }
        }

        return count;
    }

    /** Uniform declarations after the #version line (or at the very top) */
    private void declare(Collection<GlslOption> uniforms)
    {
        if (uniforms.isEmpty())
        {
            return;
        }

        StringBuilder declarations = new StringBuilder();

        for (GlslOption option : uniforms)
        {
            declarations.append("uniform ").append(option.glslType()).append(' ').append(this.prefix).append(option.name).append(";\n");
        }

        for (int i = 0; i < this.tokens.size(); i++)
        {
            if (this.directives[i] != null && this.directives[i].name.equals("version"))
            {
                if (i + 1 < this.tokens.size() && this.tokens.get(i + 1).type == GlslToken.Type.NEWLINE)
                {
                    this.after(i + 1).append(declarations);
                }
                else
                {
                    this.after(i).append('\n').append(declarations);
                }

                return;
            }
        }

        this.before(0).insert(0, declarations);
    }

    private StringBuilder before(int index)
    {
        return this.before.computeIfAbsent(index, (k) -> new StringBuilder());
    }

    private StringBuilder after(int index)
    {
        return this.after.computeIfAbsent(index, (k) -> new StringBuilder());
    }
}
