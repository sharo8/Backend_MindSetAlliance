package com.mindsetalliance.core.assistant;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Intention de la question : décide si des cartes / tableaux sont renvoyés au client.
 * COUNT → texte seul ; LIST / DETAIL / SUMMARY → faits structurés ; OTHER → tableaux seulement.
 */
public enum AssistantIntent {
    COUNT,
    LIST,
    DETAIL,
    SUMMARY,
    OTHER;

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern SUMMARY_PATTERN = Pattern.compile(
            "\\b(summary|r[ée]sum[ée]|overview|vue\\s+d['’]?ensemble|[ée]tat\\s+(du\\s+)?syst[eè]me|system\\s+(overview|status|summary)|synth[eè]se)\\b",
            FLAGS);
    private static final Pattern LIST_PATTERN = Pattern.compile(
            "\\b(liste|list|montre|montrer|affiche|afficher|show|who\\s+are|qui\\s+sont|noms?|names?)\\b",
            FLAGS);
    private static final Pattern DETAIL_PATTERN = Pattern.compile(
            "\\b(d[ée]taill?e?|detail|compare|comparaison|breakdown|r[ée]partition|tout\\s+sur|statistiques?\\s+compl[eè]tes?)\\b",
            FLAGS);
    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "\\b(combien|how\\s+many|nombre|count|how\\s+much|boni|wangapi)\\b",
            FLAGS);

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Cartes KPI : liste, détail, résumé système. */
    public boolean showsCards() {
        return this == LIST || this == DETAIL || this == SUMMARY;
    }

    /** Tableaux seuls (ex. dernières connexions) autorisés aussi pour OTHER. */
    public boolean showsTables() {
        return showsCards() || this == OTHER;
    }

    public static AssistantIntent detect(String question) {
        if (question == null || question.isBlank()) {
            return OTHER;
        }
        String q = question.strip();
        if (SUMMARY_PATTERN.matcher(q).find()) {
            return SUMMARY;
        }
        if (LIST_PATTERN.matcher(q).find()) {
            return LIST;
        }
        if (DETAIL_PATTERN.matcher(q).find()) {
            return DETAIL;
        }
        if (COUNT_PATTERN.matcher(q).find()) {
            return COUNT;
        }
        return OTHER;
    }

    public boolean includeFact(String kind) {
        if (kind == null) {
            return false;
        }
        if ("table".equals(kind)) {
            return showsTables();
        }
        if ("kpis".equals(kind)) {
            return showsCards();
        }
        return showsCards();
    }
}
