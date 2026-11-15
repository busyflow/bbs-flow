package ans.molang;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexical scanner for Bedrock MoLang expressions.
 */
public final class MolangTokenizer {

    public enum TokenType {
        NUMBER,
        STRING,
        IDENT,
        DOT,
        COMMA,
        SEMI,
        COLON,
        QUESTION,
        NULL_COALESCE, // ??
        ARROW,         // ->
        LPAREN,
        RPAREN,
        LBRACE,
        RBRACE,
        LBRACKET,
        RBRACKET,
        ASSIGN,        // =
        EQ,            // ==
        NEQ,           // !=
        LT,
        LTE,
        GT,
        GTE,
        AND,           // &&
        OR,            // ||
        NOT,           // !
        PLUS,
        MINUS,
        STAR,
        SLASH,
        EOF
    }

    public record Token(TokenType type, String text, double numberValue, int pos) {
    }

    private MolangTokenizer() {
    }

    public static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>(32);
        if (input == null || input.isEmpty()) {
            tokens.add(new Token(TokenType.EOF, "", 0.0, 0));
            return tokens;
        }
        int len = input.length();
        int i = 0;
        while (i < len) {
            char ch = input.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            int start = i;
            if (ch == '\'' || ch == '"') {
                char quote = ch;
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len) {
                    char c = input.charAt(i);
                    if (c == '\\' && i + 1 < len) {
                        sb.append(input.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (c == quote) {
                        i++;
                        break;
                    }
                    sb.append(c);
                    i++;
                }
                tokens.add(new Token(TokenType.STRING, sb.toString(), 0.0, start));
                continue;
            }

            if (Character.isDigit(ch) || (ch == '.' && i + 1 < len && Character.isDigit(input.charAt(i + 1)))) {
                boolean hasDot = (ch == '.');
                i++;
                while (i < len) {
                    char c = input.charAt(i);
                    if (Character.isDigit(c)) {
                        i++;
                    } else if (c == '.' && !hasDot) {
                        hasDot = true;
                        i++;
                    } else if ((c == 'e' || c == 'E') && i + 1 < len) {
                        i++;
                        if (i < len && (input.charAt(i) == '+' || input.charAt(i) == '-')) {
                            i++;
                        }
                    } else {
                        break;
                    }
                }
                String numStr = input.substring(start, i);
                // Optional trailing float suffix 'f'
                if (i < len && (input.charAt(i) == 'f' || input.charAt(i) == 'F')) {
                    i++;
                }
                double val = 0.0;
                try {
                    val = Double.parseDouble(numStr);
                } catch (NumberFormatException ignored) {
                }
                tokens.add(new Token(TokenType.NUMBER, numStr, val, start));
                continue;
            }

            if (Character.isLetter(ch) || ch == '_') {
                i++;
                while (i < len) {
                    char c = input.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(new Token(TokenType.IDENT, input.substring(start, i), 0.0, start));
                continue;
            }

            // Two-char operators
            if (i + 1 < len) {
                char next = input.charAt(i + 1);
                if (ch == '-' && next == '>') {
                    tokens.add(new Token(TokenType.ARROW, "->", 0.0, start));
                    i += 2;
                    continue;
                }
                if (ch == '?' && next == '?') {
                    tokens.add(new Token(TokenType.NULL_COALESCE, "??", 0.0, start));
                    i += 2;
                    continue;
                }
                if (ch == '=' && next == '=') {
                    tokens.add(new Token(TokenType.EQ, "==", 0.0, start));
                    i += 2;
                    continue;
                }
                if (ch == '!' && next == '=') {
                    tokens.add(new Token(TokenType.NEQ, "!=", 0.0, start));
                    i += 2;
                    continue;
                }
                if (ch == '<' && next == '=') {
                    tokens.add(new Token(TokenType.LTE, "<=", 0.0, start));
                    i += 2;
                    continue;
                }
                if (ch == '>' && next == '=') {
                    tokens.add(new Token(TokenType.GTE, ">=", 0.0, start));
                    i += 2;
                    continue;
                }
                if (ch == '&' && next == '&') {
                    tokens.add(new Token(TokenType.AND, "&&", 0.0, start));
                    i += 2;
                    continue;
                }
                if (ch == '|' && next == '|') {
                    tokens.add(new Token(TokenType.OR, "||", 0.0, start));
                    i += 2;
                    continue;
                }
            }

            // Single-char punctuation/operators
            TokenType single = switch (ch) {
                case '.' -> TokenType.DOT;
                case ',' -> TokenType.COMMA;
                case ';' -> TokenType.SEMI;
                case ':' -> TokenType.COLON;
                case '?' -> TokenType.QUESTION;
                case '(' -> TokenType.LPAREN;
                case ')' -> TokenType.RPAREN;
                case '{' -> TokenType.LBRACE;
                case '}' -> TokenType.RBRACE;
                case '[' -> TokenType.LBRACKET;
                case ']' -> TokenType.RBRACKET;
                case '=' -> TokenType.ASSIGN;
                case '<' -> TokenType.LT;
                case '>' -> TokenType.GT;
                case '!' -> TokenType.NOT;
                case '+' -> TokenType.PLUS;
                case '-' -> TokenType.MINUS;
                case '*' -> TokenType.STAR;
                case '/' -> TokenType.SLASH;
                default -> null;
            };
            if (single != null) {
                tokens.add(new Token(single, String.valueOf(ch), 0.0, start));
                i++;
            } else {
                // Skip unknown punctuation gracefully rather than failing entire asset load
                i++;
            }
        }
        tokens.add(new Token(TokenType.EOF, "", 0.0, len));
        return tokens;
    }
}
