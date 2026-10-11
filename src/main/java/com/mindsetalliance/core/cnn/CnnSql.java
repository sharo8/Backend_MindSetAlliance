package com.mindsetalliance.core.cnn;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * N'accepte que des lectures. Les valeurs viennent de paramètres liés, jamais d'un SQL collé.
 */
public final class CnnSql {

    private static final Pattern LEADING = Pattern.compile("(?is)^(select|with|show)\\b");
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|alter|create|truncate|replace|grant|revoke|call|execute|lock|rename|load|handler|do|set)\\b"
                    + "|\\bfor\\s+update\\b|\\binto\\s+(outfile|dumpfile)\\b");

    private CnnSql() {
    }

    public static void requireRead(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new CnnWriteRejectedException("Requête vide");
        }
        String stripped = stripComments(sql).strip();
        if (stripped.endsWith(";")) {
            stripped = stripped.substring(0, stripped.length() - 1).strip();
        }
        if (stripped.indexOf(';') >= 0) {
            throw new CnnWriteRejectedException("Plusieurs instructions");
        }
        if (!LEADING.matcher(stripped).find()) {
            throw new CnnWriteRejectedException("Instruction refusée");
        }
        if (FORBIDDEN.matcher(stripped).find()) {
            throw new CnnWriteRejectedException("Instruction refusée");
        }
    }

    static String stripComments(String sql) {
        String withoutBlock = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder out = new StringBuilder(withoutBlock.length());
        boolean quote = false;
        for (int i = 0; i < withoutBlock.length(); i++) {
            char c = withoutBlock.charAt(i);
            if (c == '\'') {
                quote = !quote;
                out.append(c);
                continue;
            }
            if (!quote && c == '-' && i + 1 < withoutBlock.length() && withoutBlock.charAt(i + 1) == '-') {
                break;
            }
            out.append(c);
        }
        return out.toString();
    }

    public static String privilege(String token) {
        return token == null ? "" : token.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
