package com.mindsetalliance.core.assistant;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Langue d'une question d'administrateur, indépendante de la langue de l'interface.
 * Une question ambiguë (« Tickets CNN ? ») reprend la langue de la dernière question identifiable.
 */
public enum AssistantLanguage {
    FR("fr", "French (français)"),
    EN("en", "English"),
    LN("ln", "Lingala"),
    SW("sw", "Swahili (Kiswahili)");

    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}']+");
    private static final Pattern FR_ELISION = Pattern.compile("^(l|d|qu|c|j|n|s|m|t)'\\p{L}{2,}.*");
    private static final Pattern EN_CONTRACTION = Pattern.compile(".*\\p{L}'(s|t|re|ve|ll|d|m)$");
    private static final Pattern FR_ACCENT = Pattern.compile(".*[éèêëàâùûçôîï].*");

    private static final Map<AssistantLanguage, Set<String>> WORDS = new EnumMap<>(Map.of(
            EN, Set.of("the", "is", "are", "was", "were", "who", "what", "how", "many", "much", "which", "last",
                    "latest", "show", "list", "does", "do", "did", "have", "has", "users", "user", "today", "of",
                    "with", "and", "logged", "login", "give", "me", "please", "open", "count", "people", "person",
                    "can", "tell", "any", "there", "role", "roles", "staff", "employees", "active", "inactive",
                    "access", "why", "when", "where", "this", "that", "for", "from", "to", "my", "our", "all",
                    "most", "recent", "status", "overview", "summary", "unresolved", "holds", "permission"),
            FR, Set.of("le", "la", "les", "des", "du", "de", "est", "sont", "qui", "quel", "quelle", "quels",
                    "quelles", "combien", "dernier", "dernière", "derniers", "dernières", "aujourd", "hui",
                    "utilisateurs", "utilisateur", "rôle", "rôles", "ont", "avec", "pour", "et", "liste", "montre",
                    "donne", "moi", "connexion", "connexions", "connecté", "où", "un", "une", "il", "elle", "ce",
                    "cette", "sur", "dans", "pas", "ne", "quoi", "comment", "personne", "actifs", "inactifs",
                    "accès", "ouverts", "effectifs", "au", "aux", "résumé", "état", "détient"),
            LN, Set.of("ndenge", "boni", "ezali", "bazali", "bato", "moto", "mingi", "lelo", "nini", "te",
                    "kosala", "pesa", "lakisa", "mosala", "basali", "eloko", "ekoki", "oyo", "yango", "mpo", "kaka",
                    "lisusu", "sikoyo", "liboso", "nsuka", "akotaki", "bakotaki", "kokota", "azali", "nzela",
                    "ndingisa", "mokumba", "motango", "pona", "elongo", "bakonzi", "nani"),
            SW, Set.of("ngapi", "wangapi", "gani", "watumiaji", "mtumiaji", "wafanyakazi", "mfanyakazi", "leo",
                    "je", "kuna", "wapi", "ni", "kwa", "mwisho", "jukumu", "ruhusa", "tiketi", "onyesha", "orodha",
                    "ameingia", "kuingia", "aliyeingia", "hali", "sasa", "idadi", "yupi", "hii", "hizo", "ana",
                    "wana", "nipe", "tafadhali", "wote", "kampuni", "wako", "nani")
    ));

    private final String code;
    private final String promptName;

    AssistantLanguage(String code, String promptName) {
        this.code = code;
        this.promptName = promptName;
    }

    public String code() {
        return code;
    }

    public String promptName() {
        return promptName;
    }

    public static AssistantLanguage detect(String question, List<AssistantDtos.ChatTurn> history) {
        AssistantLanguage direct = detectOrNull(question);
        if (direct != null) {
            return direct;
        }
        if (history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                AssistantDtos.ChatTurn turn = history.get(i);
                if (turn == null || "assistant".equalsIgnoreCase(turn.role())) {
                    continue;
                }
                AssistantLanguage previous = detectOrNull(turn.content());
                if (previous != null) {
                    return previous;
                }
            }
        }
        return FR;
    }

    static AssistantLanguage detectOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Map<AssistantLanguage, Integer> score = new EnumMap<>(AssistantLanguage.class);
        for (AssistantLanguage lang : values()) {
            score.put(lang, 0);
        }
        String normalized = text.toLowerCase(Locale.ROOT).replace('’', '\'');
        for (String token : SPLIT.split(normalized)) {
            if (token.isBlank()) {
                continue;
            }
            if (EN_CONTRACTION.matcher(token).matches()) {
                score.merge(EN, 2, Integer::sum);
            } else if (FR_ELISION.matcher(token).matches()) {
                score.merge(FR, 1, Integer::sum);
            }
            if (FR_ACCENT.matcher(token).matches()) {
                score.merge(FR, 1, Integer::sum);
            }
            for (String part : token.split("'")) {
                if (part.isEmpty()) {
                    continue;
                }
                for (AssistantLanguage lang : values()) {
                    if (WORDS.get(lang).contains(part)) {
                        score.merge(lang, 1, Integer::sum);
                    }
                }
            }
        }
        AssistantLanguage best = null;
        int bestScore = 0;
        boolean tie = false;
        for (Map.Entry<AssistantLanguage, Integer> entry : score.entrySet()) {
            if (entry.getValue() > bestScore) {
                best = entry.getKey();
                bestScore = entry.getValue();
                tie = false;
            } else if (entry.getValue() == bestScore && bestScore > 0) {
                tie = true;
            }
        }
        return tie ? null : best;
    }
}
