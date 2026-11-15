package ans.molang;

import ans.molang.MolangExpr.ArrowExpr;
import ans.molang.MolangExpr.AssignExpr;
import ans.molang.MolangExpr.BinaryExpr;
import ans.molang.MolangExpr.BinaryExpr.Op;
import ans.molang.MolangExpr.BlockExpr;
import ans.molang.MolangExpr.BreakExpr;
import ans.molang.MolangExpr.CallExpr;
import ans.molang.MolangExpr.ContinueExpr;
import ans.molang.MolangExpr.IndexExpr;
import ans.molang.MolangExpr.Literal;
import ans.molang.MolangExpr.LoopExpr;
import ans.molang.MolangExpr.MemberExpr;
import ans.molang.MolangExpr.ReturnExpr;
import ans.molang.MolangExpr.TernaryExpr;
import ans.molang.MolangExpr.ThisExpr;
import ans.molang.MolangExpr.UnaryExpr;
import ans.molang.MolangExpr.VariableAccessExpr;
import ans.molang.MolangExpr.VariableAccessExpr.Namespace;
import ans.molang.MolangTokenizer.Token;
import ans.molang.MolangTokenizer.TokenType;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recursive-descent parser and AST cache for Bedrock MoLang expressions and JSON value nodes.
 */
public final class MolangParser {

    private static final ConcurrentHashMap<String, MolangExpr> CACHE = new ConcurrentHashMap<>(2048);

    private final List<Token> tokens;
    private int pos = 0;

    private MolangParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static MolangExpr parse(String expression) {
        if (expression == null) {
            return MolangExpr.ZERO;
        }
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return MolangExpr.ZERO;
        }
        return CACHE.computeIfAbsent(trimmed, MolangParser::parseUncached);
    }

    public static MolangExpr parseJson(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return MolangExpr.ZERO;
        }
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isNumber()) {
                return new Literal(MolangValue.of(p.getAsDouble()));
            }
            if (p.isBoolean()) {
                return p.getAsBoolean() ? MolangExpr.ONE : MolangExpr.ZERO;
            }
            return parse(p.getAsString());
        }
        return MolangExpr.ZERO;
    }

    public static MolangExpr parseUncached(String src) {
        try {
            List<Token> toks = MolangTokenizer.tokenize(src);
            MolangParser parser = new MolangParser(toks);
            return parser.parseScript(false);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Failed to parse MoLang expression: \"" + src + "\" -> " + ex.getMessage(), ex);
        }
    }

    private MolangExpr parseScript(boolean insideBraces) {
        List<MolangExpr> stmts = new ArrayList<>(4);
        while (!check(TokenType.EOF) && (!insideBraces || !check(TokenType.RBRACE))) {
            while (match(TokenType.SEMI)) {
                // Skip empty semicolons
            }
            if (check(TokenType.EOF) || (insideBraces && check(TokenType.RBRACE))) {
                break;
            }
            stmts.add(parseStatement());
            match(TokenType.SEMI);
        }
        if (stmts.isEmpty()) {
            return MolangExpr.ZERO;
        }
        if (stmts.size() == 1) {
            return stmts.get(0);
        }
        return new BlockExpr(stmts);
    }

    private MolangExpr parseStatement() {
        if (check(TokenType.IDENT)) {
            String word = peek().text().toLowerCase(Locale.ROOT);
            if ("return".equals(word)) {
                advance();
                if (check(TokenType.EOF) || check(TokenType.SEMI) || check(TokenType.RBRACE)) {
                    return new ReturnExpr(MolangExpr.ZERO);
                }
                return new ReturnExpr(parseAssignment());
            }
            if ("break".equals(word)) {
                advance();
                return new BreakExpr();
            }
            if ("continue".equals(word)) {
                advance();
                return new ContinueExpr();
            }
        }
        return parseAssignment();
    }

    private MolangExpr parseAssignment() {
        MolangExpr left = parseTernary();
        if (match(TokenType.ASSIGN)) {
            MolangExpr rhs = parseAssignment();
            if (left instanceof VariableAccessExpr varAccess) {
                return new AssignExpr(varAccess, rhs);
            }
            // Fallback: evaluate rhs cleanly even if target isn't assignable
            return rhs;
        }
        return left;
    }

    private MolangExpr parseTernary() {
        MolangExpr cond = parseNullCoalesce();
        if (match(TokenType.QUESTION)) {
            MolangExpr t = parseAssignment();
            MolangExpr f = match(TokenType.COLON) ? parseAssignment() : MolangExpr.ZERO;
            return new TernaryExpr(cond, t, f);
        }
        return cond;
    }

    private MolangExpr parseNullCoalesce() {
        MolangExpr expr = parseOr();
        while (match(TokenType.NULL_COALESCE)) {
            MolangExpr rhs = parseOr();
            expr = new BinaryExpr(Op.NULL_COALESCE, expr, rhs);
        }
        return expr;
    }

    private MolangExpr parseOr() {
        MolangExpr expr = parseAnd();
        while (match(TokenType.OR)) {
            expr = new BinaryExpr(Op.OR, expr, parseAnd());
        }
        return expr;
    }

    private MolangExpr parseAnd() {
        MolangExpr expr = parseEquality();
        while (match(TokenType.AND)) {
            expr = new BinaryExpr(Op.AND, expr, parseEquality());
        }
        return expr;
    }

    private MolangExpr parseEquality() {
        MolangExpr expr = parseRelational();
        while (true) {
            if (match(TokenType.EQ)) {
                expr = new BinaryExpr(Op.EQ, expr, parseRelational());
            } else if (match(TokenType.NEQ)) {
                expr = new BinaryExpr(Op.NEQ, expr, parseRelational());
            } else {
                break;
            }
        }
        return expr;
    }

    private MolangExpr parseRelational() {
        MolangExpr expr = parseAdditive();
        while (true) {
            if (match(TokenType.LT)) {
                expr = new BinaryExpr(Op.LT, expr, parseAdditive());
            } else if (match(TokenType.LTE)) {
                expr = new BinaryExpr(Op.LTE, expr, parseAdditive());
            } else if (match(TokenType.GT)) {
                expr = new BinaryExpr(Op.GT, expr, parseAdditive());
            } else if (match(TokenType.GTE)) {
                expr = new BinaryExpr(Op.GTE, expr, parseAdditive());
            } else {
                break;
            }
        }
        return expr;
    }

    private MolangExpr parseAdditive() {
        MolangExpr expr = parseMultiplicative();
        while (true) {
            if (match(TokenType.PLUS)) {
                expr = new BinaryExpr(Op.ADD, expr, parseMultiplicative());
            } else if (match(TokenType.MINUS)) {
                expr = new BinaryExpr(Op.SUB, expr, parseMultiplicative());
            } else {
                break;
            }
        }
        return expr;
    }

    private MolangExpr parseMultiplicative() {
        MolangExpr expr = parseUnary();
        while (true) {
            if (match(TokenType.STAR)) {
                expr = new BinaryExpr(Op.MUL, expr, parseUnary());
            } else if (match(TokenType.SLASH)) {
                expr = new BinaryExpr(Op.DIV, expr, parseUnary());
            } else {
                break;
            }
        }
        return expr;
    }

    private MolangExpr parseUnary() {
        if (match(TokenType.NOT)) {
            return new UnaryExpr('!', parseUnary());
        }
        if (match(TokenType.MINUS)) {
            return new UnaryExpr('-', parseUnary());
        }
        if (match(TokenType.PLUS)) {
            return parseUnary();
        }
        return parsePostfix();
    }

    private MolangExpr parsePostfix() {
        MolangExpr expr = parsePrimary();
        while (true) {
            if (match(TokenType.ARROW)) {
                MolangExpr member = parsePostfix();
                expr = new ArrowExpr(expr, member);
            } else if (match(TokenType.LBRACKET)) {
                MolangExpr idx = parseAssignment();
                expect(TokenType.RBRACKET);
                expr = new IndexExpr(expr, idx);
            } else if (match(TokenType.DOT)) {
                String m = expectIdent();
                expr = new MemberExpr(expr, m);
            } else {
                break;
            }
        }
        return expr;
    }

    private MolangExpr parsePrimary() {
        if (match(TokenType.NUMBER)) {
            return new Literal(MolangValue.of(prev().numberValue()));
        }
        if (match(TokenType.STRING)) {
            return new Literal(MolangValue.of(prev().text()));
        }
        if (match(TokenType.LPAREN)) {
            MolangExpr inner = parseAssignment();
            expect(TokenType.RPAREN);
            return inner;
        }
        if (match(TokenType.LBRACE)) {
            MolangExpr block = parseScript(true);
            expect(TokenType.RBRACE);
            return block;
        }
        if (match(TokenType.IDENT)) {
            String head = prev().text();
            String headLow = head.toLowerCase(Locale.ROOT);

            if ("this".equals(headLow)) {
                return new ThisExpr();
            }
            if ("true".equals(headLow)) {
                return MolangExpr.ONE;
            }
            if ("false".equals(headLow)) {
                return MolangExpr.ZERO;
            }
            if ("loop".equals(headLow) && check(TokenType.LPAREN)) {
                advance();
                MolangExpr count = parseAssignment();
                expect(TokenType.COMMA);
                MolangExpr body = parseAssignment();
                expect(TokenType.RPAREN);
                return new LoopExpr(count, body);
            }
            if ("for_each".equals(headLow) && check(TokenType.LPAREN)) {
                advance();
                parseAssignment();
                expect(TokenType.COMMA);
                parseAssignment();
                expect(TokenType.COMMA);
                MolangExpr body = parseAssignment();
                expect(TokenType.RPAREN);
                return body;
            }

            if (check(TokenType.DOT)) {
                List<String> segments = new ArrayList<>(4);
                while (match(TokenType.DOT)) {
                    segments.add(expectIdent());
                }

                // Prefix namespace resolution
                if ("q".equals(headLow) || "query".equals(headLow)) {
                    String fn = String.join(".", segments);
                    List<MolangExpr> args = match(TokenType.LPAREN) ? parseArgList() : Collections.emptyList();
                    return new CallExpr(CallExpr.Kind.QUERY, fn, args);
                }
                if ("math".equals(headLow)) {
                    String fn = String.join(".", segments);
                    List<MolangExpr> args = match(TokenType.LPAREN) ? parseArgList() : Collections.emptyList();
                    return new CallExpr(CallExpr.Kind.MATH, fn, args);
                }

                Namespace ns = switch (headLow) {
                    case "v", "variable" -> Namespace.VARIABLE;
                    case "t", "temp" -> Namespace.TEMP;
                    case "c", "context" -> Namespace.CONTEXT;
                    case "array" -> Namespace.ARRAY;
                    case "material" -> Namespace.MATERIAL;
                    case "texture" -> Namespace.TEXTURE;
                    case "geometry" -> Namespace.GEOMETRY;
                    default -> null;
                };
                if (ns != null) {
                    String rootName = segments.get(0);
                    List<String> sub = segments.size() > 1 ? segments.subList(1, segments.size()) : Collections.emptyList();
                    return new VariableAccessExpr(ns, rootName, sub);
                }
            }

            // Bare identifier or string-like symbol used directly (e.g. Geometry.default / bare keyword)
            return new Literal(MolangValue.of(head));
        }

        // Recover gracefully from stray tokens at EOF
        if (!check(TokenType.EOF)) {
            advance();
        }
        return MolangExpr.ZERO;
    }

    private List<MolangExpr> parseArgList() {
        List<MolangExpr> args = new ArrayList<>(4);
        if (check(TokenType.RPAREN)) {
            advance();
            return args;
        }
        while (!check(TokenType.EOF)) {
            args.add(parseAssignment());
            if (!match(TokenType.COMMA)) {
                break;
            }
        }
        expect(TokenType.RPAREN);
        return args;
    }

    private String expectIdent() {
        if (match(TokenType.IDENT)) {
            return prev().text();
        }
        if (match(TokenType.NUMBER)) {
            return prev().text();
        }
        return "";
    }

    private Token peek() {
        return tokens.get(Math.min(pos, tokens.size() - 1));
    }

    private Token prev() {
        return tokens.get(Math.max(0, pos - 1));
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(TokenType type) {
        if (check(type)) {
            pos++;
        }
    }

    private Token advance() {
        Token t = peek();
        if (pos < tokens.size() - 1) {
            pos++;
        }
        return t;
    }
}
