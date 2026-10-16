package mchorse.bbs_mod.utils.glsl;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Value of a preprocessor expression that may depend on options turned into uniforms. Arithmetic
 * follows JCPP, the preprocessor Iris runs: 64-bit integers, comparisons give 0 or 1, a number's
 * fraction is dropped. Constant parts fold right away, so an expression that doesn't depend on
 * any such option always ends up as a constant.
 */
public final class PpExpr
{
    public static final PpExpr FALSE = new PpExpr(Kind.CONST, 0, null, false, null, null, null, null);
    public static final PpExpr TRUE = new PpExpr(Kind.CONST, 1, null, false, null, null, null, null);

    enum Kind
    {
        CONST, OPTION, FLAG, UNARY, BINARY, SELECT
    }

    final Kind kind;
    final long value;

    /** Option name of OPTION and FLAG */
    final String name;

    /** OPTION holds a float value, read as its integer part */
    final boolean floating;
    final String op;
    final PpExpr a;
    final PpExpr b;
    final PpExpr c;

    private final int hash;

    private PpExpr(Kind kind, long value, String name, boolean floating, String op, PpExpr a, PpExpr b, PpExpr c)
    {
        this.kind = kind;
        this.value = value;
        this.name = name;
        this.floating = floating;
        this.op = op;
        this.a = a;
        this.b = b;
        this.c = c;
        this.hash = Objects.hash(kind, value, name, floating, op, a, b, c);
    }

    /* Factories */

    public static PpExpr constant(long value)
    {
        return value == 0 ? FALSE : value == 1 ? TRUE : new PpExpr(Kind.CONST, value, null, false, null, null, null, null);
    }

    /** The value of an option turned into a uniform, as the preprocessor would read its number */
    public static PpExpr option(String name, boolean floating)
    {
        return new PpExpr(Kind.OPTION, 0, name, floating, null, null, null, null);
    }

    /** 1 while an on/off option turned into a uniform is on */
    public static PpExpr flag(String name)
    {
        return new PpExpr(Kind.FLAG, 0, name, false, null, null, null, null);
    }

    public static PpExpr unary(String op, PpExpr a)
    {
        if (op.equals("!"))
        {
            return not(a);
        }

        if (a.isConst())
        {
            return constant(op.equals("-") ? -a.value : ~a.value);
        }

        return new PpExpr(Kind.UNARY, 0, null, false, op, a, null, null);
    }

    public static PpExpr not(PpExpr a)
    {
        if (a.isConst())
        {
            return a.value == 0 ? TRUE : FALSE;
        }

        if (a.kind == Kind.UNARY && a.op.equals("!"))
        {
            return bool(a.a);
        }

        if (a.kind == Kind.BINARY)
        {
            String inverse = inverseComparison(a.op);

            if (inverse != null)
            {
                return binary(inverse, a.a, a.b);
            }
            else if (a.op.equals("&&"))
            {
                return or(not(a.a), not(a.b));
            }
            else if (a.op.equals("||"))
            {
                return and(not(a.a), not(a.b));
            }
        }

        if (a.kind == Kind.SELECT)
        {
            return select(a.a, not(a.b), not(a.c));
        }

        return new PpExpr(Kind.UNARY, 0, null, false, "!", a, null, null);
    }

    /** 0 or 1, as the preprocessor's own logical operators make */
    public static PpExpr bool(PpExpr a)
    {
        if (a.isConst())
        {
            return a.value == 0 ? FALSE : TRUE;
        }

        return a.isBoolean() ? a : new PpExpr(Kind.BINARY, 0, null, false, "!=", a, FALSE, null);
    }

    public static PpExpr and(PpExpr a, PpExpr b)
    {
        if (a.isConst())
        {
            return a.value == 0 ? FALSE : bool(b);
        }

        if (b.isConst())
        {
            return b.value == 0 ? FALSE : bool(a);
        }

        a = bool(a);
        b = bool(b);

        if (a.equals(b))
        {
            return a;
        }

        if (a.equals(not(b)))
        {
            return FALSE;
        }

        /* "X == 4 && X != 1" is "X == 4"; "X == 4 && X == 1" never holds */
        PpExpr settled = settle(a, b);

        if (settled != null)
        {
            return settled.isTrue() ? a : FALSE;
        }

        settled = settle(b, a);

        if (settled != null)
        {
            return settled.isTrue() ? b : FALSE;
        }

        return new PpExpr(Kind.BINARY, 0, null, false, "&&", a, b, null);
    }

    public static PpExpr or(PpExpr a, PpExpr b)
    {
        if (a.isConst())
        {
            return a.value != 0 ? TRUE : bool(b);
        }

        if (b.isConst())
        {
            return b.value != 0 ? TRUE : bool(a);
        }

        a = bool(a);
        b = bool(b);

        if (a.equals(b))
        {
            return a;
        }

        if (a.equals(not(b)))
        {
            return TRUE;
        }

        /* "X == 4 || X != 1": the first implies the second */
        PpExpr settled = settle(a, b);

        if (settled != null && settled.isTrue())
        {
            return b;
        }

        settled = settle(b, a);

        if (settled != null && settled.isTrue())
        {
            return a;
        }

        return new PpExpr(Kind.BINARY, 0, null, false, "||", a, b, null);
    }

    /**
     * When the first one pins an option to a value ("X == 4"), the second one with that value in
     * place, if that makes it a constant; null otherwise.
     */
    private static PpExpr settle(PpExpr pin, PpExpr other)
    {
        if (pin.kind != Kind.BINARY || !pin.op.equals("==") || pin.a.kind != Kind.OPTION || !pin.b.isConst())
        {
            return null;
        }

        PpExpr result = other.substitute(pin.a.name, pin.b.value);

        return result.isConst() ? result : null;
    }

    /** This expression with an option's value read as the given number */
    private PpExpr substitute(String option, long value)
    {
        switch (this.kind)
        {
            case CONST: return this;
            case OPTION: return this.name.equals(option) ? constant(value) : this;
            case FLAG: return this;
            case UNARY: return unary(this.op, this.a.substitute(option, value));
            case BINARY: return binary(this.op, this.a.substitute(option, value), this.b.substitute(option, value));
            default: return select(this.a.substitute(option, value), this.b.substitute(option, value), this.c.substitute(option, value));
        }
    }

    public static PpExpr binary(String op, PpExpr a, PpExpr b)
    {
        if (op.equals("&&"))
        {
            return and(a, b);
        }
        else if (op.equals("||"))
        {
            return or(a, b);
        }

        if (a.isConst() && b.isConst())
        {
            return constant(fold(op, a.value, b.value));
        }

        /* A choice between two numbers against a number: choose between the results */
        if (a.kind == Kind.SELECT && a.b.isConst() && a.c.isConst() && b.isConst())
        {
            return select(a.a, binary(op, a.b, b), binary(op, a.c, b));
        }

        if (b.kind == Kind.SELECT && b.b.isConst() && b.c.isConst() && a.isConst())
        {
            return select(b.a, binary(op, a, b.b), binary(op, a, b.c));
        }

        if (isComparison(op))
        {
            if (a.equals(b))
            {
                return op.equals("==") || op.equals("<=") || op.equals(">=") ? TRUE : FALSE;
            }

            /* A truth value compared with 0 or 1 is that truth value or its opposite */
            PpExpr truth = a.isBoolean() && b.isConst() ? a : (b.isBoolean() && a.isConst() ? b : null);

            if (truth != null)
            {
                long other = a.isConst() ? a.value : b.value;

                if (op.equals("==") || op.equals("!="))
                {
                    if (other != 0 && other != 1)
                    {
                        return op.equals("==") ? FALSE : TRUE;
                    }

                    boolean same = (other == 1) == op.equals("==");

                    return same ? truth : not(truth);
                }
            }
        }

        return new PpExpr(Kind.BINARY, 0, null, false, op, a, b, null);
    }

    public static PpExpr select(PpExpr condition, PpExpr a, PpExpr b)
    {
        if (condition.isConst())
        {
            return condition.value != 0 ? a : b;
        }

        if (a.equals(b))
        {
            return a;
        }

        if (condition.kind == Kind.UNARY && condition.op.equals("!"))
        {
            return select(condition.a, b, a);
        }

        if (a.isConst() && b.isConst() && (a.value == 0 || a.value == 1) && (b.value == 0 || b.value == 1))
        {
            return a.value == 1 ? bool(condition) : not(condition);
        }

        return new PpExpr(Kind.SELECT, 0, null, false, null, bool(condition), a, b);
    }

    static long fold(String op, long a, long b)
    {
        switch (op)
        {
            case "*": return a * b;
            case "/": return b == 0 ? 0 : a / b;
            case "%": return b == 0 ? 0 : a % b;
            case "+": return a + b;
            case "-": return a - b;
            case "<<": return a << b;
            case ">>": return a >> b;
            case "<": return a < b ? 1 : 0;
            case ">": return a > b ? 1 : 0;
            case "<=": return a <= b ? 1 : 0;
            case ">=": return a >= b ? 1 : 0;
            case "==": return a == b ? 1 : 0;
            case "!=": return a != b ? 1 : 0;
            case "&": return a & b;
            case "^": return a ^ b;
            case "|": return a | b;
            case "&&": return a != 0 && b != 0 ? 1 : 0;
            case "||": return a != 0 || b != 0 ? 1 : 0;
        }

        throw new IllegalArgumentException("Unknown operator " + op);
    }

    static boolean isComparison(String op)
    {
        return inverseComparison(op) != null;
    }

    private static String inverseComparison(String op)
    {
        switch (op)
        {
            case "==": return "!=";
            case "!=": return "==";
            case "<": return ">=";
            case ">=": return "<";
            case ">": return "<=";
            case "<=": return ">";
        }

        return null;
    }

    /* Queries */

    public boolean isConst()
    {
        return this.kind == Kind.CONST;
    }

    public long value()
    {
        return this.value;
    }

    public boolean isTrue()
    {
        return this.isConst() && this.value != 0;
    }

    public boolean isFalse()
    {
        return this.isConst() && this.value == 0;
    }

    /** Whether it's only ever 0 or 1 */
    public boolean isBoolean()
    {
        switch (this.kind)
        {
            case CONST: return this.value == 0 || this.value == 1;
            case FLAG: return true;
            case UNARY: return this.op.equals("!");
            case BINARY: return isComparison(this.op) || this.op.equals("&&") || this.op.equals("||");
            case SELECT: return this.b.isBoolean() && this.c.isBoolean();
            default: return false;
        }
    }

    /** Names of the options it depends on */
    public void symbols(Set<String> out)
    {
        if (this.name != null)
        {
            out.add(this.name);
        }

        if (this.a != null) this.a.symbols(out);
        if (this.b != null) this.b.symbols(out);
        if (this.c != null) this.c.symbols(out);
    }

    /**
     * The value with every option at the given value: integer part of a value option, 1 or 0 for
     * an on/off one. A missing option counts as 0.
     */
    public long evaluate(Map<String, Long> values)
    {
        switch (this.kind)
        {
            case CONST: return this.value;
            case OPTION:
            case FLAG:
            {
                Long value = values.get(this.name);

                return value == null ? 0 : value;
            }
            case UNARY:
            {
                long a = this.a.evaluate(values);

                return this.op.equals("!") ? (a == 0 ? 1 : 0) : this.op.equals("-") ? -a : ~a;
            }
            case BINARY:
            {
                long a = this.a.evaluate(values);

                if (this.op.equals("&&") && a == 0) return 0;
                if (this.op.equals("||") && a != 0) return 1;

                return fold(this.op, a, this.b.evaluate(values));
            }
            default: return this.a.evaluate(values) != 0 ? this.b.evaluate(values) : this.c.evaluate(values);
        }
    }

    /** Rebuild with what the facts settle replaced by constants */
    PpExpr simplify(PpFacts facts)
    {
        if (facts == null || facts.isEmpty() || this.isConst())
        {
            return this;
        }

        Boolean known = facts.truth(this);

        if (known != null)
        {
            return known ? TRUE : FALSE;
        }

        switch (this.kind)
        {
            case OPTION:
            {
                Long value = facts.value(this.name);

                return value == null ? this : constant(value);
            }
            case FLAG:
            {
                Long value = facts.value(this.name);

                return value == null ? this : constant(value);
            }
            case UNARY: return unary(this.op, this.a.simplify(facts));
            case BINARY: return binary(this.op, this.a.simplify(facts), this.b.simplify(facts));
            default: return select(this.a.simplify(facts), this.b.simplify(facts), this.c.simplify(facts));
        }
    }

    /**
     * The expression as GLSL over the uniforms (option name with the prefix). Integer contexts
     * keep the preprocessor's integer arithmetic; a float option is truncated like JCPP reads it.
     */
    public String glsl(String prefix, boolean bool)
    {
        switch (this.kind)
        {
            case CONST:
                if (bool)
                {
                    return this.value != 0 ? "true" : "false";
                }

                return this.value < 0 ? "(" + this.value + ")" : String.valueOf(this.value);
            case OPTION:
            {
                String value = this.floating ? "int(" + prefix + this.name + ")" : prefix + this.name;

                return bool ? "(" + value + " != 0)" : value;
            }
            case FLAG:
                return bool ? "(" + prefix + this.name + " != 0)" : prefix + this.name;
            case UNARY:
                if (this.op.equals("!"))
                {
                    String not = "!" + this.a.glsl(prefix, true);

                    return bool ? not : "int(" + not + ")";
                }
                else
                {
                    String value = "(" + this.op + this.a.glsl(prefix, false) + ")";

                    return bool ? "(" + value + " != 0)" : value;
                }
            case BINARY:
                if (this.op.equals("&&") || this.op.equals("||"))
                {
                    String value = "(" + this.a.glsl(prefix, true) + " " + this.op + " " + this.b.glsl(prefix, true) + ")";

                    return bool ? value : "int" + value;
                }
                else if (isComparison(this.op))
                {
                    String value = "(" + this.a.glsl(prefix, false) + " " + this.op + " " + this.b.glsl(prefix, false) + ")";

                    return bool ? value : "int" + value;
                }
                else
                {
                    String value = "(" + this.a.glsl(prefix, false) + " " + this.op + " " + this.b.glsl(prefix, false) + ")";

                    return bool ? "(" + value + " != 0)" : value;
                }
            default:
                return "(" + this.a.glsl(prefix, true) + " ? " + this.b.glsl(prefix, bool) + " : " + this.c.glsl(prefix, bool) + ")";
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (!(obj instanceof PpExpr))
        {
            return false;
        }

        PpExpr other = (PpExpr) obj;

        return this.hash == other.hash && this.kind == other.kind && this.value == other.value && this.floating == other.floating
            && Objects.equals(this.name, other.name) && Objects.equals(this.op, other.op)
            && Objects.equals(this.a, other.a) && Objects.equals(this.b, other.b) && Objects.equals(this.c, other.c);
    }

    @Override
    public int hashCode()
    {
        return this.hash;
    }

    @Override
    public String toString()
    {
        return this.glsl("", false);
    }
}
