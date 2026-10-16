package mchorse.bbs_mod.utils.glsl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the expression of an {@code #if}/{@code #elif} the way JCPP does: macros are expanded
 * token by token, {@code defined} looks at the raw name, unknown names are 0, and operators have
 * JCPP's priorities. Options turned into uniforms and macros that differ between option values
 * come out as {@link PpExpr} parts instead of numbers.
 */
class PpEvaluator
{
    private final Map<String, PpMacro.State> macros;
    private final PpFacts facts;
    private final Deque<Item> pending = new ArrayDeque<>();
    private final Set<String> symbols = new HashSet<>();

    PpEvaluator(Map<String, PpMacro.State> macros, PpFacts facts)
    {
        this.macros = macros;
        this.facts = facts;
    }

    PpExpr evaluate(List<GlslToken> tokens) throws PpException
    {
        return this.evaluate(tokens, Collections.<String>emptySet());
    }

    private PpExpr evaluate(List<GlslToken> tokens, Set<String> hide) throws PpException
    {
        for (GlslToken token : tokens)
        {
            this.pending.add(new Item(token, hide));
        }

        if (this.pending.isEmpty())
        {
            throw this.error("Empty expression");
        }

        /* JCPP warns about anything left after the expression and ignores it */
        return this.expr(0).simplify(this.facts);
    }

    /* Parsing, following JCPP's Preprocessor.expr() */

    private PpExpr expr(int priority) throws PpException
    {
        Item item = this.next();

        if (item == null)
        {
            throw this.error("Unexpected end of expression");
        }

        PpExpr lhs;

        if (item.expr != null)
        {
            lhs = item.expr;
        }
        else
        {
            GlslToken token = item.token;

            if (token.is("("))
            {
                lhs = this.expr(0);
                this.expect(")");
            }
            else if (token.is("~") || token.is("-"))
            {
                lhs = PpExpr.unary(token.text, this.expr(11));
            }
            else if (token.is("!"))
            {
                lhs = PpExpr.not(this.expr(11));
            }
            else if (token.type == GlslToken.Type.NUMBER)
            {
                lhs = PpExpr.constant(this.number(token.text));
            }
            else if (token.isIdentifier())
            {
                lhs = token.text.equals("defined") ? this.defined() : PpExpr.FALSE;
            }
            else
            {
                throw this.error("Bad token " + token.text);
            }
        }

        while (true)
        {
            Item op = this.next();
            int opPriority = op == null || op.expr != null ? 0 : priority(op.token);

            if (opPriority == 0 || priority >= opPriority)
            {
                if (op != null)
                {
                    this.pending.addFirst(op);
                }

                break;
            }

            PpExpr rhs = this.expr(opPriority);

            if (op.token.is("?"))
            {
                this.expect(":");

                PpExpr otherwise = this.expr(0);

                lhs = PpExpr.select(lhs, rhs, otherwise);
            }
            else
            {
                lhs = PpExpr.binary(op.token.text, lhs, rhs);
            }
        }

        return lhs;
    }

    private static int priority(GlslToken token)
    {
        if (token.type != GlslToken.Type.PUNCTUATION)
        {
            return 0;
        }

        switch (token.text)
        {
            case "/": case "%": case "*": return 11;
            case "+": case "-": return 10;
            case "<<": case ">>": return 9;
            case "<": case ">": case "<=": case ">=": return 8;
            case "==": case "!=": return 7;
            case "&": return 6;
            case "^": return 5;
            case "|": return 4;
            case "&&": return 3;
            case "||": return 2;
            case "?": return 1;
        }

        return 0;
    }

    private void expect(String text) throws PpException
    {
        Item item = this.next();

        if (item == null || item.expr != null || !item.token.is(text))
        {
            throw this.error("Expected " + text);
        }
    }

    private PpExpr defined() throws PpException
    {
        Item name = this.pending.pollFirst();
        boolean parenthesis = false;

        if (name != null && name.expr == null && name.token.is("("))
        {
            parenthesis = true;
            name = this.pending.pollFirst();
        }

        if (name == null || name.expr != null || !name.token.isIdentifier())
        {
            throw this.error("defined() needs an identifier");
        }

        if (parenthesis)
        {
            Item close = this.pending.pollFirst();

            if (close == null || close.expr != null || !close.token.is(")"))
            {
                throw this.error("Missing ) in defined()");
            }
        }

        PpMacro.State state = this.macros.get(name.token.text);

        if (state == null)
        {
            return PpExpr.FALSE;
        }

        PpExpr defined = state.defined(this.facts);

        defined.symbols(this.symbols);

        return defined;
    }

    long number(String text) throws PpException
    {
        try
        {
            return jcppLong(text);
        }
        catch (NumberFormatException e)
        {
            throw this.error("Bad number " + text);
        }
    }

    /** JCPP's NumericValue.longValue(): the integer part, scaled by a decimal exponent */
    static long jcppLong(String text)
    {
        String s = text.toLowerCase();
        int base = 10;
        int i = 0;

        if (s.startsWith("0x"))
        {
            base = 16;
            i = 2;
        }
        else if (s.length() > 1 && s.charAt(0) == '0' && Character.isDigit(s.charAt(1)) && s.indexOf('.') < 0 && s.indexOf('e') < 0)
        {
            base = 8;
            i = 1;
        }

        int start = i;

        while (i < s.length() && Character.digit(s.charAt(i), base) >= 0)
        {
            i += 1;
        }

        long value = i == start ? 0 : Long.parseLong(s.substring(start, i), base);

        if (base == 10)
        {
            if (i < s.length() && s.charAt(i) == '.')
            {
                i += 1;

                while (i < s.length() && Character.isDigit(s.charAt(i)))
                {
                    i += 1;
                }
            }

            if (i < s.length() && s.charAt(i) == 'e')
            {
                i += 1;

                int sign = 1;

                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-'))
                {
                    sign = s.charAt(i) == '-' ? -1 : 1;
                    i += 1;
                }

                int expStart = i;

                while (i < s.length() && Character.isDigit(s.charAt(i)))
                {
                    i += 1;
                }

                if (i > expStart)
                {
                    value = (long) (value * Math.pow(10, sign * Integer.parseInt(s.substring(expStart, i))));
                }
            }
        }

        return value;
    }

    /* Macro expansion */

    /** Next token with macros expanded, or null at the end */
    private Item next() throws PpException
    {
        while (true)
        {
            Item item = this.pending.pollFirst();

            if (item == null || item.expr != null || !item.token.isIdentifier())
            {
                return item;
            }

            String name = item.token.text;

            if (name.equals("defined") || item.hide.contains(name))
            {
                return item;
            }

            PpMacro.State state = this.macros.get(name);

            if (state == null)
            {
                return item;
            }

            if (state.isStatic())
            {
                PpMacro macro = state.single();

                if (macro == null)
                {
                    return item;
                }

                if (macro.option != null)
                {
                    return new Item(this.optionValue(macro.option));
                }

                List<List<Item>> args = null;

                if (macro.isFunction())
                {
                    args = this.arguments();

                    if (args == null)
                    {
                        return item;
                    }
                }

                this.pushFront(this.substitute(macro, args, with(item.hide, name)));
            }
            else
            {
                return new Item(this.select(state, name, item.hide));
            }
        }
    }

    private PpExpr optionValue(GlslOption option)
    {
        this.symbols.add(option.name);

        return PpExpr.option(option.name, option.kind == GlslOption.Kind.FLOAT).simplify(this.facts);
    }

    /** A macro that differs between option values: each definition becomes one choice */
    private PpExpr select(PpMacro.State state, String name, Set<String> hide) throws PpException
    {
        List<List<Item>> args = null;
        boolean function = false;

        for (PpMacro.Alternative alternative : state.alternatives)
        {
            alternative.when.symbols(this.symbols);
            function |= alternative.macro != null && alternative.macro.isFunction();
        }

        if (function)
        {
            args = this.arguments();

            if (args == null)
            {
                return PpExpr.FALSE;
            }
        }

        PpExpr result = null;

        for (int i = state.alternatives.size() - 1; i >= 0; i--)
        {
            PpMacro.Alternative alternative = state.alternatives.get(i);
            PpMacro macro = alternative.macro;
            PpExpr value;

            if (macro == null)
            {
                value = PpExpr.FALSE;
            }
            else if (macro.option != null)
            {
                value = this.optionValue(macro.option);
            }
            else
            {
                if (macro.isFunction() != function)
                {
                    throw this.error("Macro " + name + " is function-like only for some option values");
                }

                List<Item> body = this.substitute(macro, args, with(hide, name));

                if (!isPrimary(body))
                {
                    throw this.error("Macro " + name + " differs between option values and isn't a single value");
                }

                PpEvaluator evaluator = new PpEvaluator(this.macros, this.facts);

                evaluator.pending.addAll(body);

                try
                {
                    value = evaluator.expr(0);

                    if (!evaluator.pending.isEmpty())
                    {
                        throw evaluator.error("Unexpected tokens in " + name);
                    }
                }
                finally
                {
                    this.symbols.addAll(evaluator.symbols);
                }
            }

            result = result == null ? value : PpExpr.select(alternative.when.simplify(this.facts), value, result);
        }

        return result;
    }

    /** Arguments of a function-like macro call, or null when no '(' follows */
    private List<List<Item>> arguments() throws PpException
    {
        Item open = this.pending.peekFirst();

        if (open == null || open.expr != null || !open.token.is("("))
        {
            return null;
        }

        this.pending.pollFirst();

        List<List<Item>> args = new ArrayList<>();
        List<Item> current = new ArrayList<>();
        int depth = 0;

        while (true)
        {
            Item item = this.pending.pollFirst();

            if (item == null)
            {
                throw this.error("Unterminated macro arguments");
            }

            if (item.expr == null && item.token.is("("))
            {
                depth += 1;
            }
            else if (item.expr == null && item.token.is(")"))
            {
                if (depth == 0)
                {
                    break;
                }

                depth -= 1;
            }
            else if (item.expr == null && item.token.is(",") && depth == 0)
            {
                args.add(current);
                current = new ArrayList<>();

                continue;
            }

            current.add(item);
        }

        if (!current.isEmpty() || !args.isEmpty())
        {
            args.add(current);
        }

        return args;
    }

    private List<Item> substitute(PpMacro macro, List<List<Item>> args, Set<String> hide) throws PpException
    {
        List<Item> result = new ArrayList<>();

        for (GlslToken token : macro.body)
        {
            if (token.is("#") || token.is("##"))
            {
                throw this.error("Stringizing and pasting aren't supported in " + macro.name);
            }

            int param = token.isIdentifier() && macro.params != null ? macro.params.indexOf(token.text) : -1;

            if (param >= 0)
            {
                if (args != null && param < args.size())
                {
                    result.addAll(args.get(param));
                }
            }
            else
            {
                result.add(new Item(token, hide));
            }
        }

        return result;
    }

    private void pushFront(List<Item> items)
    {
        for (int i = items.size() - 1; i >= 0; i--)
        {
            this.pending.addFirst(items.get(i));
        }
    }

    /** Whether the tokens make one operand: unary operators before a number, name, call or parenthesis */
    private static boolean isPrimary(List<Item> items)
    {
        int i = 0;

        while (i < items.size() && items.get(i).expr == null && (items.get(i).token.is("-") || items.get(i).token.is("!") || items.get(i).token.is("~")))
        {
            i += 1;
        }

        if (i >= items.size())
        {
            return false;
        }

        Item first = items.get(i);

        if (first.expr != null || first.token.type == GlslToken.Type.NUMBER)
        {
            return i == items.size() - 1;
        }

        if (first.token.isIdentifier())
        {
            if (first.token.text.equals("defined"))
            {
                int rest = items.size() - i - 1;

                return rest == 1 || (rest == 3 && items.get(i + 1).token != null && items.get(i + 1).token.is("("));
            }

            if (i == items.size() - 1)
            {
                return true;
            }

            /* A call: the name, then one parenthesised group up to the end */
            return items.get(i + 1).expr == null && items.get(i + 1).token.is("(") && closes(items, i + 1);
        }

        return first.token.is("(") && closes(items, i);
    }

    /** Whether the parenthesis at the index closes exactly at the last item */
    private static boolean closes(List<Item> items, int open)
    {
        int depth = 0;

        for (int i = open; i < items.size(); i++)
        {
            Item item = items.get(i);

            if (item.expr != null)
            {
                continue;
            }

            if (item.token.is("("))
            {
                depth += 1;
            }
            else if (item.token.is(")"))
            {
                depth -= 1;

                if (depth == 0)
                {
                    return i == items.size() - 1;
                }
            }
        }

        return false;
    }

    private static Set<String> with(Set<String> hide, String name)
    {
        Set<String> set = new HashSet<>(hide);

        set.add(name);

        return set;
    }

    private PpException error(String message)
    {
        return new PpException(message, new HashSet<>(this.symbols));
    }

    private static class Item
    {
        final GlslToken token;
        final PpExpr expr;
        final Set<String> hide;

        Item(GlslToken token, Set<String> hide)
        {
            this.token = token;
            this.expr = null;
            this.hide = hide;
        }

        Item(PpExpr expr)
        {
            this.token = null;
            this.expr = expr;
            this.hide = Collections.emptySet();
        }

        @Override
        public String toString()
        {
            return this.expr != null ? this.expr.toString() : this.token.text;
        }
    }
}
