package com.mindsetalliance.core.reports;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class ReportI18n {

    private static final ThreadLocal<String> LANG = ThreadLocal.withInitial(() -> "fr");
    private static final Map<String, String> FR = new LinkedHashMap<>();
    private static final Map<String, String> EN = new LinkedHashMap<>();

    static {
        put("cover.generated", "Généré le ", "Generated on ");
        put("cover.by", "Par ", "By ");
        put("cover.confidential", "Document interne — diffusion restreinte", "Internal document — restricted distribution");
        put("footer.page", "Page ", "Page ");
        put("title.utilisateurs", "Gestion des utilisateurs", "User management");
        put("title.permissions", "Matrice des permissions", "Permissions matrix");
        put("title.acces", "Accès et périmètres", "Access and scopes");
        put("title.organisation", "Départements et sociétés", "Departments and companies");
        put("title.statistiques", "Statistiques globales", "Global statistics");
        put("kpi.total", "Total", "Total");
        put("kpi.actifs", "Actifs", "Active");
        put("kpi.entreprise", "Toute l’entreprise", "Whole company");
        put("kpi.projetSeul", "Un seul projet", "Single project");
        put("kpi.roles", "Rôles", "Roles");
        put("kpi.permissions", "Permissions", "Permissions");
        put("kpi.modules", "Modules", "Modules");
        put("kpi.agents", "Agents", "Agents");
        put("kpi.grants", "Dérogations GRANT", "GRANT exceptions");
        put("kpi.denys", "Dérogations DENY", "DENY exceptions");
        put("kpi.departements", "Départements", "Departments");
        put("kpi.societes", "Sociétés", "Companies");
        put("kpi.taux", "Taux", "Rate");
        put("col.agent", "Agent", "Agent");
        put("col.email", "E-mail", "Email");
        put("col.departement", "Département", "Department");
        put("col.roles", "Rôles", "Roles");
        put("col.perimetre", "Périmètre", "Scope");
        put("col.statut", "Statut", "Status");
        put("col.created", "Créé le", "Created on");
        put("col.permission", "Permission", "Permission");
        put("col.role", "Rôle", "Role");
        put("col.accessType", "Type d’accès", "Access type");
        put("col.type", "Type", "Type");
        put("col.motif", "Motif", "Reason");
        put("col.date", "Date", "Date");
        put("col.code", "Code", "Code");
        put("col.societe", "Société", "Company");
        put("col.agents", "Agents", "Agents");
        put("col.defaultRoles", "Rôles par défaut", "Default roles");
        put("col.value", "Valeur", "Value");
        put("yes", "Oui", "Yes");
        put("none", "Aucun", "None");
        put("noRole", "Aucun rôle", "No role");
        put("dash", "—", "—");
        put("perimeter.company", "Toute l’entreprise", "Whole company");
        put("perimeter.none", "Sans accès", "No access");
        put("access.transverse", "Transverse", "Company-wide");
        put("access.limited", "Projet limité", "Project-limited");
        put("overrides", "Dérogations individuelles", "Individual exceptions");
        put("section.depts", "Départements", "Departments");
        put("section.companies", "Sociétés / projets", "Companies / projects");
        put("filter.allAgents", "Périmètre : tous les agents.", "Scope: all agents.");
        put("filter.prefix", "Filtres : ", "Filters: ");
        put("filter.dept", "département #", "department #");
        put("filter.statut", "statut ", "status ");
        put("statut.ACTIF", "ACTIF", "ACTIVE");
        put("statut.INACTIF", "SUSPENDU", "SUSPENDED");
        put("period.7", "7 jours", "7 days");
        put("period.30", "30 jours", "30 days");
        put("period.90", "90 jours", "90 days");
        put("period.ALL", "Tout l’historique", "Full history");
        put("period.caption", "Période : ", "Period: ");
        put("author.fallback", "Administrateur", "Administrator");
        put("chart.dept", "Agents par département", "Agents by department");
        put("chart.scope", "Périmètre", "Scope");
        put("chart.ticketStatus", "Statuts des tickets", "Ticket statuses");
        put("chart.seniority", "Ancienneté des comptes", "Account seniority");
        put("chart.permTop", "Permissions les plus attribuées", "Most assigned permissions");
        put("chart.funnel", "Cycle de vie des tickets", "Ticket lifecycle");
        put("col.dept", "Département", "Department");
        put("col.scopeType", "Type", "Type");
        put("col.ticketStatus", "Statut", "Status");
        put("col.band", "Tranche", "Band");
        put("col.step", "Étape", "Stage");
        put("role.SUPPORT", "Support", "Support");
        put("role.FINANCE", "Finance", "Finance");
        put("role.RH", "Ressources humaines", "Human resources");
        put("role.JURIDIQUE", "Juridique", "Legal");
        put("role.MARKETING", "Marketing", "Marketing");
        put("role.DEV", "Développement", "Development");
        put("role.DIRECTION", "Direction", "Leadership");
        put("role.COMMERCIAL", "Commercial", "Sales");
        put("role.ADMIN_SYSTEME", "Administration système", "System administration");
        put("role.CONSEIL_ADMINISTRATION", "Conseil d’administration", "Board of directors");
    }

    private ReportI18n() {
    }

    static void enter(String lang, String acceptLanguage) {
        LANG.set(resolve(lang, acceptLanguage));
    }

    static void clear() {
        LANG.remove();
    }

    static boolean english() {
        return "en".equals(LANG.get());
    }

    static String resolve(String lang, String acceptLanguage) {
        String raw = (lang != null && !lang.isBlank()) ? lang : acceptLanguage;
        if (raw == null || raw.isBlank()) {
            return "fr";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("en")) {
            return "en";
        }
        return "fr";
    }

    static String t(String key) {
        Map<String, String> pack = english() ? EN : FR;
        String value = pack.get(key);
        if (value != null) {
            return value;
        }
        return FR.getOrDefault(key, key);
    }

    static String role(String code) {
        if (code == null) {
            return t("dash");
        }
        return t("role." + code);
    }

    static String statut(String statut) {
        if (statut == null) {
            return t("dash");
        }
        String key = "statut." + statut.toUpperCase(Locale.ROOT);
        String value = (english() ? EN : FR).get(key);
        return value != null ? value : statut;
    }

    static String period(String period) {
        String key = switch (period == null ? "30" : period) {
            case "7", "90", "ALL" -> "period." + period;
            default -> "period.30";
        };
        return t(key);
    }

    static String titleForType(String type) {
        return switch (type) {
            case "UTILISATEURS" -> t("title.utilisateurs");
            case "PERMISSIONS" -> t("title.permissions");
            case "ACCES" -> t("title.acces");
            case "ORGANISATION" -> t("title.organisation");
            case "STATISTIQUES" -> t("title.statistiques");
            default -> type == null || type.isBlank() ? "Rapport" : type;
        };
    }

    private static void put(String key, String fr, String en) {
        FR.put(key, fr);
        EN.put(key, en);
    }
}
