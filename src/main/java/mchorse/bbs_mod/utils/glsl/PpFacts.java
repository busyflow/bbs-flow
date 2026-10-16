package mchorse.bbs_mod.utils.glsl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What is known to hold inside a branch that depends on options: its conditions and the options
 * pinned to a value. {@link PpExpr#simplify} uses it, so a nested condition that the branch
 * already settles comes out as a constant. Pins shared by a whole pass (options found to stay
 * constant halfway through it) are seen by every copy.
 */
class PpFacts
{
    private final Set<PpExpr> truths = new HashSet<>();
    private final Set<PpExpr> falsehoods = new HashSet<>();
    private final Map<String, Long> values = new HashMap<>();
    private final Map<String, Long> shared;

    PpFacts(Map<String, Long> shared)
    {
        this.shared = shared;
    }

    PpFacts copy()
    {
        PpFacts facts = new PpFacts(this.shared);

        facts.truths.addAll(this.truths);
        facts.falsehoods.addAll(this.falsehoods);
        facts.values.putAll(this.values);

        return facts;
    }

    boolean isEmpty()
    {
        return this.truths.isEmpty() && this.values.isEmpty() && this.shared.isEmpty();
    }

    /** Record that a condition holds, splitting it into the parts that must all hold */
    void assume(PpExpr condition)
    {
        if (condition.isConst())
        {
            return;
        }

        if (condition.kind == PpExpr.Kind.BINARY && condition.op.equals("&&"))
        {
            this.assume(condition.a);
            this.assume(condition.b);

            return;
        }

        this.truths.add(condition);
        this.falsehoods.add(PpExpr.not(condition));

        if (condition.kind == PpExpr.Kind.FLAG)
        {
            this.values.put(condition.name, 1L);
        }
        else if (condition.kind == PpExpr.Kind.UNARY && condition.op.equals("!") && condition.a.kind == PpExpr.Kind.FLAG)
        {
            this.values.put(condition.a.name, 0L);
        }
        else if (condition.kind == PpExpr.Kind.BINARY && condition.op.equals("=="))
        {
            if (condition.a.kind == PpExpr.Kind.OPTION && condition.b.isConst())
            {
                this.values.put(condition.a.name, condition.b.value);
            }
            else if (condition.b.kind == PpExpr.Kind.OPTION && condition.a.isConst())
            {
                this.values.put(condition.b.name, condition.a.value);
            }
        }
    }

    Long value(String name)
    {
        Long value = this.shared.get(name);

        return value != null ? value : this.values.get(name);
    }

    /** True or false when the facts settle this exact expression, null otherwise */
    Boolean truth(PpExpr expr)
    {
        if (this.truths.contains(expr))
        {
            return true;
        }

        if (this.falsehoods.contains(expr))
        {
            return false;
        }

        return null;
    }
}
