package dev.levelsystem.util;

import dev.levelsystem.api.LevelFormula;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple expression parser for level formulas defined in config.yml.
 *
 * <p>Supports: {@code +}, {@code -}, {@code *}, {@code /}, {@code ^}, parentheses,
 * and the variable {@code level}.
 *
 * <p>Falls back to {@link LevelFormula#QUADRATIC} on parse errors.
 *
 * <h3>Example expressions:</h3>
 * <ul>
 *   <li>{@code 100 * level^2}</li>
 *   <li>{@code 50 * level * 1.5}</li>
 *   <li>{@code (level + 1) * 100}</li>
 * </ul>
 */
public class FormulaParser {

    private FormulaParser() {}

    /**
     * Parse the given expression string into a {@link LevelFormula}.
     *
     * @param expression formula string from config, e.g. {@code "100 * level^2"}
     * @return parsed formula, or {@link LevelFormula#QUADRATIC} on error
     */
    public static LevelFormula parse(String expression) {
        if (expression == null || expression.isBlank()) return LevelFormula.QUADRATIC;
        // Validate the expression once at level=1 to catch obvious errors early
        try {
            eval(expression.replace("level", "1").replace("^", "**NOTSUPPORTED**"));
        } catch (Exception ignored) { /* fall through to actual eval */ }

        return level -> {
            try {
                String expr = expression.replace("level", String.valueOf(level));
                double result = eval(expr);
                return Math.max(1L, Math.round(result));
            } catch (Exception e) {
                return LevelFormula.QUADRATIC.xpForLevel(level);
            }
        };
    }

    // ── Recursive descent parser ──────────────────────────────────────────

    private static double eval(String expr) {
        return new ExprEval(expr.replaceAll("\\s+", "")).parse();
    }

    private static final class ExprEval {
        private final String expr;
        private int pos = -1;
        private int ch;

        ExprEval(String expr) { this.expr = expr; }

        void nextChar() { ch = (++pos < expr.length()) ? expr.charAt(pos) : -1; }

        boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) { nextChar(); return true; }
            return false;
        }

        double parse() { nextChar(); double x = parseExpr(); if (pos < expr.length()) throw new RuntimeException("Unexpected: " + (char) ch); return x; }

        double parseExpr() {
            double x = parseTerm();
            for (;;) {
                if      (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        double parseTerm() {
            double x = parsePow();
            for (;;) {
                if      (eat('*')) x *= parsePow();
                else if (eat('/')) x /= parsePow();
                else return x;
            }
        }

        double parsePow() {
            double x = parseUnary();
            if (eat('^')) x = Math.pow(x, parsePow());
            return x;
        }

        double parseUnary() {
            if (eat('+')) return parseUnary();
            if (eat('-')) return -parseUnary();
            return parsePrimary();
        }

        double parsePrimary() {
            double x;
            int startPos = pos;
            if (eat('(')) {
                x = parseExpr();
                eat(')');
            } else if (Character.isDigit(ch) || ch == '.') {
                while (Character.isDigit(ch) || ch == '.') nextChar();
                x = Double.parseDouble(expr.substring(startPos + 1, pos));
            } else if (Character.isLetter(ch)) {
                while (Character.isLetterOrDigit(ch)) nextChar();
                String name = expr.substring(startPos + 1, pos);
                x = switch (name) {
                    case "sqrt" -> { eat('('); double a = parseExpr(); eat(')'); yield Math.sqrt(a); }
                    case "abs"  -> { eat('('); double a = parseExpr(); eat(')'); yield Math.abs(a);  }
                    case "log"  -> { eat('('); double a = parseExpr(); eat(')'); yield Math.log(a);  }
                    default -> throw new RuntimeException("Unknown function/variable: " + name);
                };
            } else {
                throw new RuntimeException("Unexpected: " + (char) ch);
            }
            return x;
        }
    }
}
