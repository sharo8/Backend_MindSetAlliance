package com.mindsetalliance.core.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindsetalliance.core.audit.AuditLog;
import com.mindsetalliance.core.audit.AuditLogRepository;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentPermissionOverride;
import com.mindsetalliance.core.iam.AgentPermissionOverrideRepository;
import com.mindsetalliance.core.iam.AgentRepository;
import com.mindsetalliance.core.iam.AgentRole;
import com.mindsetalliance.core.iam.AgentRoleRepository;
import com.mindsetalliance.core.iam.DepartementRepository;
import com.mindsetalliance.core.iam.EffectivePermissionService;
import com.mindsetalliance.core.iam.Permission;
import com.mindsetalliance.core.iam.Role;
import com.mindsetalliance.core.iam.RoleRepository;
import com.mindsetalliance.core.notifications.NotificationRepository;
import com.mindsetalliance.core.ops.OpsService;
import com.mindsetalliance.core.tickets.TicketRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class AssistantToolsService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Kinshasa");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZONE);
    private static final Set<String> OPEN_TICKETS = Set.of("OUVERT", "EN_COURS", "EN_ATTENTE", "PRIS_EN_CHARGE");

    private final AgentRepository agentRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final RoleRepository roleRepository;
    private final AgentPermissionOverrideRepository overrideRepository;
    private final AuditLogRepository auditLogRepository;
    private final TicketRepository ticketRepository;
    private final DepartementRepository departementRepository;
    private final NotificationRepository notificationRepository;
    private final OpsService opsService;
    private final EffectivePermissionService effectivePermissionService;

    public AssistantToolsService(AgentRepository agentRepository,
                                 AgentRoleRepository agentRoleRepository,
                                 RoleRepository roleRepository,
                                 AgentPermissionOverrideRepository overrideRepository,
                                 AuditLogRepository auditLogRepository,
                                 TicketRepository ticketRepository,
                                 DepartementRepository departementRepository,
                                 NotificationRepository notificationRepository,
                                 OpsService opsService,
                                 EffectivePermissionService effectivePermissionService) {
        this.agentRepository = agentRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.roleRepository = roleRepository;
        this.overrideRepository = overrideRepository;
        this.auditLogRepository = auditLogRepository;
        this.ticketRepository = ticketRepository;
        this.departementRepository = departementRepository;
        this.notificationRepository = notificationRepository;
        this.opsService = opsService;
        this.effectivePermissionService = effectivePermissionService;
    }

    /** Clés de libellés résolues par {@link AssistantLabels} dans la langue de la question. */
    public record KpiDraft(String key, String arg, Object value, String tone) {
    }

    public record FactDraft(String titleKey, String titleArg, String kind,
                            List<Map<String, Object>> rows, List<KpiDraft> kpis) {
    }

    public record ToolOutcome(Map<String, Object> payload, FactDraft fact) {
    }

    /**
     * Point d'entrée unique : une transaction couvre tout l'outil (évite LazyInitializationException
     * quand open-in-view=false et que execute() s'auto-appelle sans proxy Spring).
     */
    @Transactional(readOnly = true)
    public ToolOutcome execute(String name, JsonNode args) {
        return switch (name) {
            case "get_user_count" -> getUserCount(text(args, "statut"), text(args, "role"));
            case "get_role_permissions" -> getRolePermissions(text(args, "role"));
            case "get_permission_holders" -> getPermissionHolders(text(args, "permission"));
            case "get_company_wide_access" -> getCompanyWideAccess();
            case "get_access_deviations" -> getAccessDeviations();
            case "get_last_logins" -> getLastLogins(intArg(args, "limit", 8));
            case "get_today_audit" -> getTodayAudit(intArg(args, "limit", 12));
            case "get_ticket_stats" -> getTicketStats(text(args, "projet"));
            case "get_cnn_status" -> getCnnStatus();
            case "get_system_summary" -> getSystemSummary();
            default -> new ToolOutcome(Map.of("error", "unknown_tool", "name", name), null);
        };
    }

    @Transactional(readOnly = true)
    public ToolOutcome getUserCount(String statut, String role) {
        requireAny("MANAGE_USERS", "MANAGE_ACCESS", "MANAGE_AGENTS");
        List<Agent> agents = agentRepository.findAllWithDepartement();
        String wantedStatut = blank(statut) ? null : statut.trim().toUpperCase(Locale.ROOT);
        String wantedRole = blank(role) ? null : role.trim().toUpperCase(Locale.ROOT);
        List<AgentRole> assignments = wantedRole == null ? List.of() : agentRoleRepository.findAllWithGraph();
        Set<Long> withRole = assignments.stream()
                .filter(AgentRole::isActive)
                .filter(ar -> ar.getRole() != null && wantedRole.equalsIgnoreCase(ar.getRole().getNom()))
                .map(ar -> ar.getAgent().getId())
                .collect(Collectors.toSet());

        long total = agents.size();
        long actifs = agents.stream().filter(a -> "ACTIF".equalsIgnoreCase(nz(a.getStatut()))).count();
        long inactifs = total - actifs;
        long matching = agents.stream()
                .filter(a -> wantedStatut == null || wantedStatut.equalsIgnoreCase(nz(a.getStatut())))
                .filter(a -> wantedRole == null || withRole.contains(a.getId()))
                .count();
        Map<String, Long> byRole = new TreeMap<>();
        if (wantedRole == null) {
            agentRoleRepository.findAllWithGraph().stream()
                    .filter(AgentRole::isActive)
                    .filter(ar -> ar.getRole() != null)
                    .collect(Collectors.groupingBy(ar -> ar.getRole().getNom(), Collectors.mapping(ar -> ar.getAgent().getId(), Collectors.toSet())))
                    .forEach((nom, ids) -> byRole.put(nom, (long) ids.size()));
        } else {
            byRole.put(wantedRole, (long) withRole.size());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", total);
        payload.put("actifs", actifs);
        payload.put("inactifs", inactifs);
        payload.put("filtreStatut", wantedStatut);
        payload.put("filtreRole", wantedRole);
        payload.put("resultatFiltre", matching);
        payload.put("parRole", byRole);
        // KPIs ciblés : pas de doublon Filtre = Inactifs quand on filtre déjà sur le statut.
        List<KpiDraft> kpis = new ArrayList<>();
        if (wantedStatut == null && wantedRole == null) {
            kpis.add(kpi("total", total, "total"));
            kpis.add(kpi("active", actifs, "positive"));
            kpis.add(kpi("inactive", inactifs, "negative"));
        } else if ("INACTIF".equals(wantedStatut) && wantedRole == null) {
            kpis.add(kpi("inactive", matching, "negative"));
        } else if ("ACTIF".equals(wantedStatut) && wantedRole == null) {
            kpis.add(kpi("active", matching, "positive"));
        } else {
            kpis.add(kpi("total", total, "total"));
            kpis.add(kpi("active", actifs, "positive"));
            kpis.add(kpi("inactive", inactifs, "negative"));
            if (wantedRole != null) {
                kpis.add(new KpiDraft("role", wantedRole, matching, "filter"));
            } else if (matching != total) {
                kpis.add(kpi("filter", matching, "filter"));
            }
        }
        return new ToolOutcome(payload, new FactDraft("staff", null, "kpis", List.of(), kpis));
    }

    @Transactional(readOnly = true)
    public ToolOutcome getRolePermissions(String roleName) {
        requireAny("MANAGE_PERMISSIONS");
        if (blank(roleName)) {
            return new ToolOutcome(Map.of("error", "role_required"), null);
        }
        Role role = roleRepository.findAllWithPermissions().stream()
                .filter(r -> roleName.equalsIgnoreCase(r.getNom()))
                .findFirst()
                .orElse(null);
        if (role == null) {
            return new ToolOutcome(Map.of("error", "unknown_role", "role", roleName), null);
        }
        List<Map<String, Object>> rows = role.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getCode))
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", p.getCode());
                    row.put("libelle", p.getLibelle());
                    row.put("module", p.getModule());
                    return row;
                })
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", role.getNom());
        payload.put("permissions", rows.stream().map(r -> r.get("code")).toList());
        payload.put("count", rows.size());
        return new ToolOutcome(payload, new FactDraft("permissions", role.getNom(), "table", rows, List.of(kpi("count", rows.size(), "total"))));
    }

    @Transactional(readOnly = true)
    public ToolOutcome getPermissionHolders(String permission) {
        requireAny("MANAGE_PERMISSIONS", "MANAGE_ACCESS");
        if (blank(permission)) {
            return new ToolOutcome(Map.of("error", "permission_required"), null);
        }
        String code = permission.trim().toUpperCase(Locale.ROOT);
        Map<Long, Boolean> roleHas = new LinkedHashMap<>();
        for (Role role : roleRepository.findAllWithPermissions()) {
            boolean has = role.getPermissions() != null && role.getPermissions().stream()
                    .anyMatch(p -> code.equalsIgnoreCase(p.getCode()));
            roleHas.put(role.getId(), has);
        }
        List<AgentRole> assignments = agentRoleRepository.findAllWithGraph();
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (AgentRole ar : assignments) {
            if (!ar.isActive() || ar.getRole() == null || ar.getAgent() == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(roleHas.get(ar.getRole().getId()))) {
                continue;
            }
            if (!seen.add(ar.getAgent().getId())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agent", display(ar.getAgent()));
            row.put("role", ar.getRole().getNom());
            row.put("perimetre", ar.getProject() == null ? "ENTREPRISE" : ar.getProject().getCode());
            rows.add(row);
        }
        Map<String, Object> payload = Map.of("permission", code, "agents", rows.size(), "liste", rows.stream().limit(25).toList());
        return new ToolOutcome(payload, new FactDraft("holders", code, "table", rows.stream().limit(15).toList(), List.of(kpi("agents", rows.size(), "total"))));
    }

    @Transactional(readOnly = true)
    public ToolOutcome getCompanyWideAccess() {
        requireAny("MANAGE_ACCESS");
        List<Map<String, Object>> rows = agentRoleRepository.findAllWithGraph().stream()
                .filter(AgentRole::isActive)
                .filter(ar -> ar.getProject() == null)
                .map(ar -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("agent", display(ar.getAgent()));
                    row.put("role", ar.getRole() == null ? "" : ar.getRole().getNom());
                    row.put("perimetre", "ENTREPRISE");
                    return row;
                })
                .toList();
        Map<String, Object> payload = Map.of("count", rows.size(), "agents", rows);
        return new ToolOutcome(payload, new FactDraft("companyWide", null, "table", rows, List.of(kpi("agents", rows.size(), "total"))));
    }

    @Transactional(readOnly = true)
    public ToolOutcome getAccessDeviations() {
        requireAny("MANAGE_ACCESS");
        List<Map<String, Object>> rows = overrideRepository.findAllWithGraph().stream()
                .sorted(Comparator.comparing(AgentPermissionOverride::getCreatedAt).reversed())
                .limit(30)
                .map(o -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("agent", display(o.getAgent()));
                    row.put("type", o.getType());
                    row.put("permission", o.getPermission() == null ? "" : o.getPermission().getCode());
                    row.put("perimetre", o.getProject() == null ? "ENTREPRISE" : o.getProject().getCode());
                    return row;
                })
                .toList();
        long grants = rows.stream().filter(r -> "GRANT".equals(r.get("type"))).count();
        long denys = rows.stream().filter(r -> "DENY".equals(r.get("type"))).count();
        Map<String, Object> payload = Map.of("count", rows.size(), "grant", grants, "deny", denys, "liste", rows);
        return new ToolOutcome(payload, new FactDraft("deviations", null, "table", rows,
                List.of(new KpiDraft(null, "GRANT", grants, "positive"), new KpiDraft(null, "DENY", denys, "negative"))));
    }

    @Transactional(readOnly = true)
    public ToolOutcome getLastLogins(int limit) {
        requireAny("VIEW_AUDIT");
        int cap = Math.min(Math.max(limit, 1), 20);
        List<AuditLog> logs = auditLogRepository.findRecentByAction("LOGIN", PageRequest.of(0, cap));
        List<Map<String, Object>> rows = logs.stream().map(this::auditRow).toList();
        Map<String, Object> payload = Map.of("count", rows.size(), "connexions", rows);
        return new ToolOutcome(payload, new FactDraft("lastLogins", null, "table", rows, List.of()));
    }

    @Transactional(readOnly = true)
    public ToolOutcome getTodayAudit(int limit) {
        requireAny("VIEW_AUDIT");
        Instant from = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        int cap = Math.min(Math.max(limit, 1), 25);
        List<AuditLog> logs = auditLogRepository.findCreatedSince(from).stream().limit(cap).toList();
        Map<String, Long> byAction = logs.stream()
                .collect(Collectors.groupingBy(l -> l.getAction() == null ? "?" : l.getAction(), Collectors.counting()));
        List<Map<String, Object>> rows = logs.stream().map(this::auditRow).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("depuis", from.toString());
        payload.put("count", rows.size());
        payload.put("parAction", byAction);
        payload.put("evenements", rows);
        List<KpiDraft> kpis = byAction.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(4)
                .map(e -> new KpiDraft(null, e.getKey(), e.getValue(), "neutral"))
                .toList();
        return new ToolOutcome(payload, new FactDraft("todayAudit", null, "table", rows, kpis));
    }

    @Transactional(readOnly = true)
    public ToolOutcome getTicketStats(String projet) {
        requireAny("VIEW_TICKETS", "MANAGE_TICKETS");
        String code = blank(projet) ? null : projet.trim().toUpperCase(Locale.ROOT);
        Map<String, Long> byStatut = toCountMap(ticketRepository.countGroupedByStatut(code));
        long total = byStatut.values().stream().mapToLong(Long::longValue).sum();
        long unresolved = byStatut.entrySet().stream()
                .filter(e -> OPEN_TICKETS.contains(nz(e.getKey())))
                .mapToLong(Map.Entry::getValue)
                .sum();
        long resolved = byStatut.entrySet().stream()
                .filter(e -> "RESOLU".equalsIgnoreCase(nz(e.getKey())) || "CLOTURE".equalsIgnoreCase(nz(e.getKey())))
                .mapToLong(Map.Entry::getValue)
                .sum();
        double rate = total == 0 ? 0 : Math.round(1000.0 * resolved / total) / 10.0;
        List<Map<String, Object>> rows = byStatut.entrySet().stream()
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("statut", e.getKey());
                    row.put("nombre", e.getValue());
                    return row;
                })
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projet", code);
        payload.put("total", total);
        payload.put("nonResolus", unresolved);
        payload.put("tauxResolutionPct", rate);
        payload.put("parStatut", byStatut);
        return new ToolOutcome(payload, new FactDraft(
                code == null ? "tickets" : "ticketsProject",
                code,
                "table",
                rows,
                List.of(kpi("total", total, "total"), kpi("unresolved", unresolved, "negative"), kpi("resolutionPct", rate, "positive"))
        ));
    }

    @Transactional(readOnly = true)
    public ToolOutcome getCnnStatus() {
        requireAny("VIEW_FLEET_MAP", "MANAGE_COURSES", "VIEW_TICKETS");
        Map<String, Object> dash = opsService.cnnDashboard("CNN");
        long ticketsCnn = ticketRepository.countActiveByProjectCode("CNN");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("live", dash.get("live"));
        payload.put("source", dash.get("source"));
        String source = String.valueOf(dash.get("source"));
        String integration = switch (source) {
            case "CNN_DB" -> "Agrégats en lecture seule sur la base Colis na Nga. Aucune écriture, aucun détail personnel.";
            case "INDISPONIBLE" -> "Source Colis na Nga indisponible. Les chiffres de démonstration du Core ne sont pas renvoyés.";
            default -> "Le Core n'accède pas à la base CNN. JWT/JWKS prévus — démo via ops_records (INTEGRATION-COLIS-NA-NGA.md).";
        };
        payload.put("integration", integration);
        payload.put("coursesDuJour", dash.get("coursesDuJour"));
        payload.put("coursiersActifs", dash.get("coursiersActifs"));
        payload.put("tauxLivraison", dash.get("tauxLivraison"));
        payload.put("incidentsOuverts", dash.get("incidentsOuverts"));
        payload.put("ticketsCnn", ticketsCnn);
        List<KpiDraft> kpis = List.of(
                kpi("coursesToday", dash.get("coursesDuJour"), "total"),
                kpi("activeCouriers", dash.get("coursiersActifs"), "positive"),
                kpi("deliveryRate", dash.get("tauxLivraison"), "filter"),
                kpi("cnnTickets", ticketsCnn, "neutral")
        );
        return new ToolOutcome(payload, new FactDraft("cnn", null, "kpis", List.of(), kpis));
    }

    /**
     * Résumé système en DTO (chiffres + chaînes uniquement) — pas d'entités JPA.
     * Compteurs SQL uniquement : aucun accès à Project LAZY.
     */
    @Transactional(readOnly = true)
    public ToolOutcome getSystemSummary() {
        requireAny("MANAGE_USERS", "MANAGE_ACCESS", "VIEW_TICKETS", "MANAGE_TICKETS", "VIEW_AUDIT");
        long total = agentRepository.count();
        long actifs = agentRepository.countByStatutIgnoreCase("ACTIF");
        long inactifs = Math.max(0, total - actifs);
        Map<String, Long> openByPriority = toCountMap(ticketRepository.countOpenGroupedByPriorite(OPEN_TICKETS));
        long openTickets = ticketRepository.countOpen(OPEN_TICKETS);
        long unreadNotifs = notificationRepository.countByLuFalse();
        long departments = departementRepository.count();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("utilisateursTotal", total);
        payload.put("utilisateursActifs", actifs);
        payload.put("utilisateursInactifs", inactifs);
        payload.put("ticketsOuverts", openTickets);
        payload.put("ticketsOuvertsParPriorite", openByPriority);
        payload.put("notificationsNonLues", unreadNotifs);
        payload.put("departements", departments);

        List<Map<String, Object>> rows = openByPriority.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("priorite", e.getKey() == null || e.getKey().isBlank() ? "—" : e.getKey());
                    row.put("nombre", e.getValue());
                    return row;
                })
                .toList();
        List<KpiDraft> kpis = List.of(
                kpi("total", total, "total"),
                kpi("active", actifs, "positive"),
                kpi("inactive", inactifs, "negative"),
                kpi("unresolved", openTickets, "negative"),
                new KpiDraft(null, "Notifs", unreadNotifs, "filter"),
                new KpiDraft(null, "Dépts", departments, "neutral")
        );
        return new ToolOutcome(payload, new FactDraft("systemSummary", null, "kpis", rows, kpis));
    }

    private void requireAny(String... permissions) {
        if (JwtRoles.hasFullAccess(JwtRoles.currentJwt())) {
            return;
        }
        Long agentId = JwtRoles.agentId();
        String project = JwtRoles.requestProjectCode();
        boolean ok = false;
        for (String code : permissions) {
            if (effectivePermissionService.isDenied(agentId, code, project)) {
                continue;
            }
            if (effectivePermissionService.hasPermission(agentId, code, project)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            throw new AccessDeniedException("Outil non autorisé pour ce rôle");
        }
    }

    private Map<String, Object> auditRow(AuditLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("quand", log.getCreatedAt() == null ? "" : WHEN.format(log.getCreatedAt()));
        row.put("acteur", display(log.getAgent()));
        row.put("action", log.getAction());
        row.put("objet", log.getObjetType());
        return row;
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new TreeMap<>();
        for (Object[] row : rows) {
            String key = row[0] == null ? "?" : String.valueOf(row[0]);
            long value = row[1] instanceof Number n ? n.longValue() : 0L;
            map.put(key, value);
        }
        return map;
    }

    private static KpiDraft kpi(String key, Object value, String tone) {
        return new KpiDraft(key, null, value, tone);
    }

    private static String display(Agent agent) {
        if (agent == null) {
            return "—";
        }
        String prenom = agent.getPrenom() == null ? "" : agent.getPrenom();
        String nom = agent.getNom() == null ? "" : agent.getNom();
        String name = (prenom + " " + nom).trim();
        return name.isBlank() ? agent.getEmailPro() : name;
    }

    private static String text(JsonNode args, String field) {
        if (args == null || args.isNull() || !args.has(field) || args.get(field).isNull()) {
            return null;
        }
        String v = args.get(field).asText();
        return blank(v) ? null : v;
    }

    private static int intArg(JsonNode args, String field, int fallback) {
        if (args == null || !args.has(field) || args.get(field).isNull()) {
            return fallback;
        }
        return args.get(field).asInt(fallback);
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
