package mchorse.bbs_mod.utils.glsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One definition of a macro. A definition may also stand for an option turned into a uniform:
 * then it expands to the option's runtime value instead of its body.
 */
class PpMacro
{
    final String name;

    /** Null for an object-like macro */
    final List<String> params;

    /** Significant tokens of the replacement */
    final List<GlslToken> body;

    /** The option whose value this macro carries at runtime, or null */
    final GlslOption option;

    private final String bodyText;

    PpMacro(String name, List<String> params, List<GlslToken> body, GlslOption option)
    {
        this.name = name;
        this.params = params;
        this.body = body;
        this.option = option;
        this.bodyText = option != null ? "\u0000" + option.name : GlslDirective.join(body);
    }

    static PpMacro of(GlslDirective directive)
    {
        return new PpMacro(directive.macro, directive.params, directive.body, null);
    }

    static PpMacro option(GlslOption option)
    {
        return new PpMacro(option.name, null, Collections.<GlslToken>emptyList(), option);
    }

    boolean isFunction()
    {
        return this.params != null;
    }

    String bodyText()
    {
        return this.bodyText;
    }

    boolean sameAs(PpMacro other)
    {
        return other != null && Objects.equals(this.params, other.params) && this.bodyText.equals(other.bodyText);
    }

    /**
     * What a name means at a point of the source: one or more definitions, each under a
     * condition. The conditions don't overlap and together always hold. A null definition means
     * the name isn't defined under that condition.
     */
    static class State
    {
        static final State UNDEFINED = new State(Collections.singletonList(new Alternative(PpExpr.TRUE, null)));

        final List<Alternative> alternatives;

        State(List<Alternative> alternatives)
        {
            this.alternatives = alternatives;
        }

        static State of(PpMacro macro)
        {
            return new State(Collections.singletonList(new Alternative(PpExpr.TRUE, macro)));
        }

        /** Whether the name means the same under any option values */
        boolean isStatic()
        {
            return this.alternatives.size() == 1;
        }

        /** The definition when static; null if undefined or not static */
        PpMacro single()
        {
            return this.isStatic() ? this.alternatives.get(0).macro : null;
        }

        PpExpr defined(PpFacts facts)
        {
            PpExpr defined = PpExpr.FALSE;

            for (Alternative alternative : this.alternatives)
            {
                if (alternative.macro != null)
                {
                    defined = PpExpr.or(defined, alternative.when.simplify(facts));
                }
            }

            return defined;
        }

        /** This state where the condition holds, the previous one elsewhere */
        State override(PpExpr condition, PpMacro macro)
        {
            if (condition.isTrue())
            {
                return macro == null ? UNDEFINED : of(macro);
            }

            List<Alternative> alternatives = new ArrayList<>();

            alternatives.add(new Alternative(condition, macro));

            for (Alternative alternative : this.alternatives)
            {
                alternatives.add(new Alternative(PpExpr.and(PpExpr.not(condition), alternative.when), alternative.macro));
            }

            return merge(alternatives);
        }

        /** Join alternatives with the same definition and drop the ones that can't happen */
        static State merge(List<Alternative> alternatives)
        {
            List<Alternative> merged = new ArrayList<>();

            for (Alternative alternative : alternatives)
            {
                if (alternative.when.isFalse())
                {
                    continue;
                }

                boolean joined = false;

                for (int i = 0; i < merged.size(); i++)
                {
                    Alternative existing = merged.get(i);
                    boolean same = existing.macro == null ? alternative.macro == null : existing.macro.sameAs(alternative.macro);

                    if (same)
                    {
                        merged.set(i, new Alternative(PpExpr.or(existing.when, alternative.when), existing.macro));
                        joined = true;

                        break;
                    }
                }

                if (!joined)
                {
                    merged.add(alternative);
                }
            }

            if (merged.size() == 1)
            {
                Alternative only = merged.get(0);

                return only.macro == null ? UNDEFINED : of(only.macro);
            }

            return merged.isEmpty() ? UNDEFINED : new State(merged);
        }
    }

    static class Alternative
    {
        final PpExpr when;
        final PpMacro macro;

        Alternative(PpExpr when, PpMacro macro)
        {
            this.when = when;
            this.macro = macro;
        }
    }
}
