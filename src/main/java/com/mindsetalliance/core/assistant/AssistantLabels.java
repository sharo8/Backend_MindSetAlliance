package com.mindsetalliance.core.assistant;

import com.mindsetalliance.core.assistant.AssistantDtos.FactCard;
import com.mindsetalliance.core.assistant.AssistantDtos.FactColumn;
import com.mindsetalliance.core.assistant.AssistantToolsService.FactDraft;
import com.mindsetalliance.core.assistant.AssistantToolsService.KpiDraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Libellés des cartes et tableaux de l'assistant, dans la langue de la question (ordre FR, EN, LN, SW). */
final class AssistantLabels {

    private static final Set<String> BADGE_COLUMNS = Set.of("action", "type");

    private static final Map<String, String[]> TITLES = Map.ofEntries(
            Map.entry("staff", new String[]{"Effectifs", "Workforce", "Bato ya mosala", "Wafanyakazi"}),
            Map.entry("permissions", new String[]{"Permissions · {0}", "Permissions · {0}", "Ndingisa · {0}", "Ruhusa · {0}"}),
            Map.entry("holders", new String[]{"Qui a {0}", "Who has {0}", "Nani azali na {0}", "Nani ana {0}"}),
            Map.entry("companyWide", new String[]{"Accès transverses", "Company-wide access", "Bokoti na kompanyi mobimba", "Ufikiaji wa kampuni nzima"}),
            Map.entry("deviations", new String[]{"Dérogations GRANT / DENY", "GRANT / DENY overrides", "Bokeseni GRANT / DENY", "Vighairi GRANT / DENY"}),
            Map.entry("lastLogins", new String[]{"Dernières connexions", "Latest logins", "Bokoti ya nsuka", "Walioingia hivi karibuni"}),
            Map.entry("todayAudit", new String[]{"Audit du jour", "Today's audit", "Audit ya lelo", "Ukaguzi wa leo"}),
            Map.entry("tickets", new String[]{"Tickets", "Tickets", "Batiké", "Tiketi"}),
            Map.entry("ticketsProject", new String[]{"Tickets · {0}", "Tickets · {0}", "Batiké · {0}", "Tiketi · {0}"}),
            Map.entry("cnn", new String[]{"Colis na Nga", "Colis na Nga", "Colis na Nga", "Colis na Nga"})
    );

    private static final Map<String, String[]> KPIS = Map.ofEntries(
            Map.entry("total", new String[]{"Total", "Total", "Nyonso", "Jumla"}),
            Map.entry("active", new String[]{"Actifs", "Active", "Bazali kosala", "Hai"}),
            Map.entry("inactive", new String[]{"Inactifs", "Inactive", "Bazali kosala te", "Wasio hai"}),
            Map.entry("filter", new String[]{"Filtre", "Filter", "Filtre", "Kichujio"}),
            Map.entry("role", new String[]{"Rôle {0}", "Role {0}", "Mokumba {0}", "Jukumu {0}"}),
            Map.entry("count", new String[]{"Nombre", "Count", "Motango", "Idadi"}),
            Map.entry("agents", new String[]{"Agents", "Agents", "Basali", "Wafanyakazi"}),
            Map.entry("unresolved", new String[]{"Non résolus", "Unresolved", "Ebongisami te", "Hazijatatuliwa"}),
            Map.entry("resolutionPct", new String[]{"Résolution %", "Resolution %", "Bobongisi %", "Utatuzi %"}),
            Map.entry("coursesToday", new String[]{"Courses du jour", "Today's deliveries", "Mikumba ya lelo", "Safari za leo"}),
            Map.entry("activeCouriers", new String[]{"Coursiers actifs", "Active couriers", "Ba coursiers bazali kosala", "Wasafirishaji hai"}),
            Map.entry("deliveryRate", new String[]{"Taux livraison %", "Delivery rate %", "Motango ya bopesi %", "Kiwango cha usafirishaji %"}),
            Map.entry("cnnTickets", new String[]{"Tickets CNN", "CNN tickets", "Batiké CNN", "Tiketi za CNN"})
    );

    private static final Map<String, String[]> COLUMNS = Map.ofEntries(
            Map.entry("quand", new String[]{"Quand", "When", "Ntango", "Lini"}),
            Map.entry("acteur", new String[]{"Acteur", "Actor", "Mosali", "Mhusika"}),
            Map.entry("action", new String[]{"Action", "Action", "Likambo", "Kitendo"}),
            Map.entry("objet", new String[]{"Objet", "Object", "Eloko", "Kitu"}),
            Map.entry("code", new String[]{"Code", "Code", "Code", "Msimbo"}),
            Map.entry("libelle", new String[]{"Libellé", "Label", "Nkombo", "Maelezo"}),
            Map.entry("module", new String[]{"Module", "Module", "Eteni", "Moduli"}),
            Map.entry("agent", new String[]{"Agent", "Agent", "Mosali", "Mfanyakazi"}),
            Map.entry("role", new String[]{"Rôle", "Role", "Mokumba", "Jukumu"}),
            Map.entry("perimetre", new String[]{"Périmètre", "Scope", "Esika", "Wigo"}),
            Map.entry("type", new String[]{"Type", "Type", "Lolenge", "Aina"}),
            Map.entry("permission", new String[]{"Permission", "Permission", "Ndingisa", "Ruhusa"}),
            Map.entry("statut", new String[]{"Statut", "Status", "Ezalela", "Hali"}),
            Map.entry("nombre", new String[]{"Nombre", "Count", "Motango", "Idadi"})
    );

    private static final Map<String, String[]> SCOPE_VALUES = Map.of(
            "ENTREPRISE", new String[]{"ENTREPRISE", "COMPANY", "KOMPANYI", "KAMPUNI"}
    );

    private static final Map<String, String> MODULES_EN = Map.of(
            "Opérations", "Operations",
            "Transversal", "Cross-functional",
            "Technique", "Technical",
            "Commercial", "Sales",
            "Direction", "Management",
            "RH", "HR",
            "Juridique", "Legal"
    );

    private static final Map<String, String> TICKET_STATUS_EN = Map.of(
            "OUVERT", "OPEN",
            "PRIS_EN_CHARGE", "ASSIGNED",
            "EN_COURS", "IN_PROGRESS",
            "EN_ATTENTE", "ON_HOLD",
            "RESOLU", "RESOLVED",
            "CLOTURE", "CLOSED",
            "ANNULE", "CANCELLED"
    );

    private AssistantLabels() {
    }

    static FactCard render(FactDraft draft, AssistantLanguage lang) {
        List<Map<String, Object>> sourceRows = draft.rows() == null ? List.of() : draft.rows();
        List<String> keys = sourceRows.isEmpty() ? new ArrayList<>() : new ArrayList<>(sourceRows.getFirst().keySet());
        if (lang != AssistantLanguage.FR) {
            keys.remove("libelle");
        }
        List<FactColumn> columns = keys.stream()
                .map(key -> new FactColumn(key, pick(COLUMNS, key, null, lang), BADGE_COLUMNS.contains(key) ? "action" : null))
                .toList();
        List<Map<String, Object>> rows = sourceRows.stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : keys) {
                out.put(key, value(key, row.get(key), lang));
            }
            return out;
        }).toList();
        List<Map<String, Object>> kpis = (draft.kpis() == null ? List.<KpiDraft>of() : draft.kpis()).stream()
                .map(k -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("label", k.key() == null ? k.arg() : pick(KPIS, k.key(), k.arg(), lang));
                    map.put("value", k.value());
                    map.put("tone", k.tone());
                    return map;
                })
                .toList();
        return new FactCard(pick(TITLES, draft.titleKey(), draft.titleArg(), lang), draft.kind(), rows, kpis, columns);
    }

    private static Object value(String key, Object raw, AssistantLanguage lang) {
        if (!(raw instanceof String text) || lang == AssistantLanguage.FR) {
            return raw;
        }
        if ("perimetre".equals(key) && SCOPE_VALUES.containsKey(text)) {
            return SCOPE_VALUES.get(text)[lang.ordinal()];
        }
        if (lang == AssistantLanguage.EN && "module".equals(key)) {
            return MODULES_EN.getOrDefault(text, text);
        }
        if (lang == AssistantLanguage.EN && "statut".equals(key)) {
            return TICKET_STATUS_EN.getOrDefault(text, text);
        }
        return raw;
    }

    private static String pick(Map<String, String[]> table, String key, String arg, AssistantLanguage lang) {
        String[] labels = table.get(key);
        String template = labels == null ? key : labels[lang.ordinal()];
        return arg == null ? template : template.replace("{0}", arg);
    }
}
