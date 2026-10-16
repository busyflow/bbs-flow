package mchorse.bbs_mod.utils.glsl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The source as nested {@code #if} blocks: runs of ordinary tokens, other directives, and
 * conditionals with their branches. Nodes point into the token list by index.
 */
class PpTree
{
    static abstract class Node
    {}

    /** Tokens [from, to) with no directive among them */
    static class Text extends Node
    {
        final int from;
        final int to;

        Text(int from, int to)
        {
            this.from = from;
            this.to = to;
        }
    }

    /** A directive other than the conditional ones */
    static class Line extends Node
    {
        final int index;
        final GlslDirective directive;

        Line(int index, GlslDirective directive)
        {
            this.index = index;
            this.directive = directive;
        }
    }

    static class If extends Node
    {
        final List<Branch> branches = new ArrayList<>();
        int endif = -1;
        GlslDirective endifDirective;
    }

    static class Branch
    {
        final int index;
        final GlslDirective directive;
        final List<Node> body = new ArrayList<>();

        Branch(int index, GlslDirective directive)
        {
            this.index = index;
            this.directive = directive;
        }

        boolean isElse()
        {
            return this.directive.name.equals("else");
        }
    }

    static List<Node> parse(List<GlslToken> tokens, GlslDirective[] directives) throws PpException
    {
        List<Node> root = new ArrayList<>();
        Deque<If> open = new ArrayDeque<>();
        int textStart = -1;

        for (int i = 0; i < tokens.size(); i++)
        {
            GlslToken token = tokens.get(i);

            if (token.type != GlslToken.Type.DIRECTIVE)
            {
                if (textStart < 0)
                {
                    textStart = i;
                }

                continue;
            }

            List<Node> body = open.isEmpty() ? root : last(open.peek()).body;

            if (textStart >= 0)
            {
                body.add(new Text(textStart, i));
                textStart = -1;
            }

            GlslDirective directive = GlslDirective.parse(token.text);

            directives[i] = directive;

            switch (directive.name)
            {
                case "if":
                case "ifdef":
                case "ifndef":
                {
                    If node = new If();

                    node.branches.add(new Branch(i, directive));
                    body.add(node);
                    open.push(node);

                    break;
                }
                case "elif":
                case "else":
                {
                    If node = open.peek();

                    if (node == null || last(node).isElse())
                    {
                        throw new PpException("#" + directive.name + " without #if", null);
                    }

                    node.branches.add(new Branch(i, directive));

                    break;
                }
                case "endif":
                {
                    If node = open.poll();

                    /* JCPP reports a stray #endif and goes on as if it wasn't there */
                    if (node == null)
                    {
                        body.add(new Line(i, directive));

                        break;
                    }

                    node.endif = i;
                    node.endifDirective = directive;

                    break;
                }
                default:
                    body.add(new Line(i, directive));
            }
        }

        if (!open.isEmpty())
        {
            throw new PpException("Unterminated #if", null);
        }

        if (textStart >= 0)
        {
            root.add(new Text(textStart, tokens.size()));
        }

        return root;
    }

    private static Branch last(If node)
    {
        return node.branches.get(node.branches.size() - 1);
    }
}
