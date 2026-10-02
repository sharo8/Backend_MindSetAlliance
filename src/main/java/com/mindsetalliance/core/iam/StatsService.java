package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.audit.AuditLog;
import com.mindsetalliance.core.audit.AuditLogRepository;
import com.mindsetalliance.core.tickets.Ticket;
import com.mindsetalliance.core.tickets.TicketRepository;
import com.mindsetalliance.core.tickets.TicketStatut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

@Service
public class StatsService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Kinshasa");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE);

    private final AgentRepository agentRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final DepartementRepository departementRepository;
    private final ProjectRepository projectRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TicketRepository ticketRepository;
    private final AuditLogRepository auditLogRepository;

    public StatsService(AgentRepository agentRepository,
                        AgentRoleRepository agentRoleRepository,
                        DepartementRepository departementRepository,
                        ProjectRepository projectRepository,
                        RoleRepository roleRepository,
                        PermissionRepository permissionRepository,
                        TicketRepository ticketRepository,
                        AuditLogRepository auditLogRepository) {
        this.agentRepository = agentRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.departementRepository = departementRepository;
        this.projectRepository = projectRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.ticketRepository = ticketRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(String periodRaw) {
        String period = normalizePeriod(periodRaw);
        LocalDate today = LocalDate.now(ZONE);
        Instant from = fromInstant(period, today);

        List<Agent> agents = agentRepository.findAllWithDepartement();
        List<AgentRole> assignments = agentRoleRepository.findAllWithGraph();
        List<Ticket> tickets = ticketRepository.findAll();
        List<Departement> departements = departementRepository.findAll().stream()
                .filter(d -> !"INACTIF".equalsIgnoreCase(d.getStatut()))
                .toList();
        List<Project> projects = projectRepository.findAll().stream()
                .filter(p -> p.getCode() != null && !"INACTIF".equalsIgnoreCase(p.getStatut()))
                .toList();
        List<Role> roles = roleRepository.findAllWithPermissions();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("period", period);
        body.put("generatedAt", Instant.now().toString());
        body.put("agentsActifs", actifs(agents));
        body.put("agentsParDepartement", parDepartement(agents, departements));
        body.put("agentsParPerimetre", parPerimetre(agents, assignments));
        body.put("ticketsParStatut", ticketsParStatut(tickets, from, period));
        body.put("ancienneteComptes", anciennete(agents, from, period, today));
        body.put("ticketsParJour", ticketsParJour(tickets, period, today));
        body.put("permissionsTop", permissionsTop());
        body.put("rolesParProjet", rolesParProjet(assignments, projects, roles));
        body.put("permissionCoverage", permissionCoverage(roles));
        body.put("ticketsTreemap", ticketsTreemap(tickets, from, period));
        body.put("loginHeatmap", loginHeatmap(from, period, today));
        body.put("ticketFunnel", ticketFunnel(tickets, from, period));
        body.put("agentsVsTickets", agentsVsTickets(agents, tickets, from, period, today));
        body.put("resolutionScatter", resolutionScatter(tickets, from, period));
        return body;
    }

    private static String normalizePeriod(String raw) {
        if (raw == null || raw.isBlank()) {
            return "30";
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (List.of("7", "30", "90", "ALL").contains(value)) {
            return value;
        }
        return "30";
    }

    private static Instant fromInstant(String period, LocalDate today) {
        if ("ALL".equals(period)) {
            return Instant.EPOCH;
        }
        int days = Integer.parseInt(period);
        return today.minusDays(days - 1L).atStartOfDay(ZONE).toInstant();
    }

    private static Map<String, Object> point(String code, String label, long valeur) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("label", label);
        row.put("valeur", valeur);
        return row;
    }

    private Map<String, Object> actifs(List<Agent> agents) {
        long total = agents.size();
        long actifs = agents.stream().filter(a -> "ACTIF".equalsIgnoreCase(a.getStatut())).count();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actifs", actifs);
        row.put("total", total);
        row.put("taux", total == 0 ? 0d : Math.round(actifs * 1000.0 / total) / 10.0);
        return row;
    }

    private List<Map<String, Object>> parDepartement(List<Agent> agents, List<Departement> departements) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        long sans = 0;
        for (Agent agent : agents) {
            if (!"ACTIF".equalsIgnoreCase(agent.getStatut())) {
                continue;
            }
            if (agent.getDepartement() == null) {
                sans++;
            } else {
                counts.merge(agent.getDepartement().getId(), 1L, Long::sum);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Departement dept : departements) {
            rows.add(point(dept.getCode() == null ? String.valueOf(dept.getId()) : dept.getCode(),
                    dept.getNom(), counts.getOrDefault(dept.getId(), 0L)));
        }
        if (sans > 0) {
            rows.add(point("NONE", "Non rattaché", sans));
        }
        return rows;
    }

    private List<Map<String, Object>> parPerimetre(List<Agent> agents, List<AgentRole> assignments) {
        Map<Long, List<AgentRole>> byAgent = new LinkedHashMap<>();
        for (AgentRole assignment : assignments) {
            if (assignment.getAgent() == null) {
                continue;
            }
            byAgent.computeIfAbsent(assignment.getAgent().getId(), k -> new ArrayList<>()).add(assignment);
        }
        long entreprise = 0;
        long projetSeul = 0;
        long autre = 0;
        for (Agent agent : agents) {
            List<AgentRole> roles = byAgent.getOrDefault(agent.getId(), List.of());
            boolean wide = roles.stream().anyMatch(r -> r.getProject() == null);
            long projects = roles.stream()
                    .map(AgentRole::getProject)
                    .filter(Objects::nonNull)
                    .map(Project::getId)
                    .distinct()
                    .count();
            if (wide) {
                entreprise++;
            } else if (projects == 1) {
                projetSeul++;
            } else {
                autre++;
            }
        }
        return List.of(
                point("ENTREPRISE", "Toute l’entreprise", entreprise),
                point("PROJET", "Un seul projet", projetSeul),
                point("AUTRE", "Sans accès / multi-projets", autre)
        );
    }

    private List<Map<String, Object>> ticketsParStatut(List<Ticket> tickets, Instant from, String period) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String code : List.of("OUVERT", "EN_COURS", "EN_ATTENTE", "RESOLU", "CLOTURE", "ANNULE")) {
            counts.put(code, 0L);
        }
        for (Ticket ticket : tickets) {
            if (!"ALL".equals(period) && ticket.getCreatedAt() != null && ticket.getCreatedAt().isBefore(from)) {
                continue;
            }
            String raw = ticket.getStatut() == null ? "OUVERT" : ticket.getStatut();
            String code;
            try {
                code = TicketStatut.from(raw).name();
            } catch (Exception ex) {
                code = raw;
            }
            if ("PRIS_EN_CHARGE".equals(code)) {
                code = "EN_COURS";
            }
            counts.merge(code, 1L, Long::sum);
        }
        Map<String, String> labels = Map.of(
                "OUVERT", "Ouvert",
                "EN_COURS", "En cours",
                "EN_ATTENTE", "En attente",
                "RESOLU", "Résolu",
                "CLOTURE", "Clôturé",
                "ANNULE", "Annulé"
        );
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            rows.add(point(entry.getKey(), labels.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()));
        }
        return rows;
    }

    private List<Map<String, Object>> anciennete(List<Agent> agents, Instant from, String period, LocalDate today) {
        long lt1w = 0;
        long w1to4 = 0;
        long m1to3 = 0;
        long gt3m = 0;
        for (Agent agent : agents) {
            Instant created = agent.getCreatedAt();
            if (created == null) {
                gt3m++;
                continue;
            }
            if (!"ALL".equals(period) && created.isBefore(from)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(created.atZone(ZONE).toLocalDate(), today);
            if (days < 7) {
                lt1w++;
            } else if (days < 28) {
                w1to4++;
            } else if (days < 90) {
                m1to3++;
            } else {
                gt3m++;
            }
        }
        return List.of(
                point("LT_1W", "Moins d’1 semaine", lt1w),
                point("W1_4", "1–4 semaines", w1to4),
                point("M1_3", "1–3 mois", m1to3),
                point("GT_3M", "Plus de 3 mois", gt3m)
        );
    }

    private List<Map<String, Object>> ticketsParJour(List<Ticket> tickets, String period, LocalDate today) {
        LocalDate start;
        if ("ALL".equals(period)) {
            LocalDate first = tickets.stream()
                    .map(Ticket::getCreatedAt)
                    .filter(Objects::nonNull)
                    .map(instant -> instant.atZone(ZONE).toLocalDate())
                    .min(LocalDate::compareTo)
                    .orElse(today.minusDays(29));
            LocalDate floor = today.minusDays(364);
            start = first.isBefore(floor) ? floor : first;
        } else {
            start = today.minusDays(Integer.parseInt(period) - 1L);
        }
        Map<LocalDate, Long> counts = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            counts.put(d, 0L);
        }
        for (Ticket ticket : tickets) {
            if (ticket.getCreatedAt() == null) {
                continue;
            }
            LocalDate day = ticket.getCreatedAt().atZone(ZONE).toLocalDate();
            if (counts.containsKey(day)) {
                counts.merge(day, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, Long> entry : counts.entrySet()) {
            Map<String, Object> row = point(entry.getKey().toString(), entry.getKey().format(DAY_LABEL), entry.getValue());
            row.put("date", entry.getKey().toString());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> permissionsTop() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] tuple : permissionRepository.countRolesPerPermissionTop10()) {
            String code = String.valueOf(tuple[0]);
            String libelle = tuple[1] == null || String.valueOf(tuple[1]).isBlank()
                    ? code
                    : String.valueOf(tuple[1]);
            long n = ((Number) tuple[2]).longValue();
            rows.add(point(code, libelle, n));
        }
        return rows;
    }

    private Map<String, Object> rolesParProjet(List<AgentRole> assignments, List<Project> projects, List<Role> roles) {
        List<String> roleNames = roles.stream().map(Role::getNom).filter(Objects::nonNull).sorted().toList();
        List<Map<String, Object>> series = new ArrayList<>();
        for (Project project : projects) {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String role : roleNames) {
                counts.put(role, 0L);
            }
            for (AgentRole assignment : assignments) {
                if (assignment.getRole() == null || assignment.getRole().getNom() == null) {
                    continue;
                }
                boolean applies = assignment.getProject() == null
                        || (assignment.getProject().getId() != null && assignment.getProject().getId().equals(project.getId()));
                if (applies) {
                    counts.merge(assignment.getRole().getNom(), 1L, Long::sum);
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", project.getCode());
            row.put("label", project.getNom() == null ? project.getCode() : project.getNom());
            row.putAll(counts);
            series.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roles", roleNames);
        body.put("series", series);
        return body;
    }

    private static boolean inPeriod(Instant instant, Instant from, String period) {
        if (instant == null) {
            return "ALL".equals(period);
        }
        return "ALL".equals(period) || !instant.isBefore(from);
    }

    private Map<String, Object> permissionCoverage(List<Role> roles) {
        List<Permission> catalog = permissionRepository.findAll();
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Permission permission : catalog) {
            String module = moduleLabel(permission.getModule());
            totals.merge(module, 1L, Long::sum);
        }
        List<String> modules = new ArrayList<>(totals.keySet());
        List<Map<String, Object>> roleRows = new ArrayList<>();
        for (Role role : roles.stream().sorted(Comparator.comparing(Role::getNom, Comparator.nullsLast(String::compareTo))).toList()) {
            Map<String, Long> owned = new LinkedHashMap<>();
            for (String module : modules) {
                owned.put(module, 0L);
            }
            Set<Permission> perms = role.getPermissions() == null ? Set.of() : role.getPermissions();
            for (Permission permission : perms) {
                owned.merge(moduleLabel(permission.getModule()), 1L, Long::sum);
            }
            List<Map<String, Object>> values = new ArrayList<>();
            for (String module : modules) {
                long total = totals.getOrDefault(module, 0L);
                long has = owned.getOrDefault(module, 0L);
                double pct = total == 0 ? 0d : Math.round(has * 1000.0 / total) / 10.0;
                Map<String, Object> cell = point(module, module, Math.round(pct));
                cell.put("pct", pct);
                cell.put("possedees", has);
                cell.put("total", total);
                values.add(cell);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", role.getNom());
            row.put("label", role.getNom());
            row.put("modules", values);
            roleRows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modules", modules);
        body.put("roles", roleRows);
        return body;
    }

    private static String moduleLabel(String module) {
        if (module == null || module.isBlank()) {
            return "Transversal";
        }
        return module.trim();
    }

    private List<Map<String, Object>> ticketsTreemap(List<Ticket> tickets, Instant from, String period) {
        Map<String, Map<String, Long>> tree = new LinkedHashMap<>();
        for (Ticket ticket : tickets) {
            if (!inPeriod(ticket.getCreatedAt(), from, period)) {
                continue;
            }
            String categorie = ticket.getCategorie() == null || ticket.getCategorie().isBlank()
                    ? "Sans catégorie"
                    : categorieLabel(ticket.getCategorie());
            String priorite = normalizePriorite(ticket.getPriorite());
            tree.computeIfAbsent(categorie, k -> new LinkedHashMap<>()).merge(priorite, 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> entry : tree.entrySet()) {
            Map<String, Object> parent = new LinkedHashMap<>();
            parent.put("name", entry.getKey());
            List<Map<String, Object>> children = new ArrayList<>();
            long sum = 0;
            for (Map.Entry<String, Long> child : entry.getValue().entrySet()) {
                Map<String, Object> leaf = new LinkedHashMap<>();
                leaf.put("name", prioriteLabel(child.getKey()));
                leaf.put("priorite", child.getKey());
                leaf.put("size", child.getValue());
                children.add(leaf);
                sum += child.getValue();
            }
            parent.put("size", sum);
            parent.put("children", children);
            rows.add(parent);
        }
        rows.sort(Comparator.comparing((Map<String, Object> row) -> ((Number) row.get("size")).longValue()).reversed());
        return rows;
    }

    private Map<String, Object> loginHeatmap(Instant from, String period, LocalDate today) {
        Instant heatmapFrom = "ALL".equals(period) ? today.minusDays(89).atStartOfDay(ZONE).toInstant() : from;
        List<AuditLog> logins = auditLogRepository.findByActionSince("LOGIN", heatmapFrom);
        String[] days = { "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche" };
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int d = 1; d <= 7; d++) {
            for (int h = 0; h < 24; h++) {
                counts.put(d + "-" + h, 0L);
            }
        }
        for (AuditLog log : logins) {
            if (log.getCreatedAt() == null) {
                continue;
            }
            var zoned = log.getCreatedAt().atZone(ZONE);
            int day = zoned.getDayOfWeek().getValue();
            int hour = zoned.getHour();
            counts.merge(day + "-" + hour, 1L, Long::sum);
        }
        List<Map<String, Object>> cells = new ArrayList<>();
        long max = 0;
        for (int d = 1; d <= 7; d++) {
            for (int h = 0; h < 24; h++) {
                long valeur = counts.getOrDefault(d + "-" + h, 0L);
                max = Math.max(max, valeur);
                Map<String, Object> cell = point(d + "-" + h, days[d - 1] + " " + String.format("%02dh", h), valeur);
                cell.put("jour", days[d - 1]);
                cell.put("jourIndex", d);
                cell.put("heure", h);
                cells.add(cell);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jours", List.of(days));
        body.put("max", max);
        body.put("cells", cells);
        return body;
    }

    private List<Map<String, Object>> ticketFunnel(List<Ticket> tickets, Instant from, String period) {
        long crees = 0;
        long pris = 0;
        long enCours = 0;
        long resolus = 0;
        long clotures = 0;
        for (Ticket ticket : tickets) {
            if (!inPeriod(ticket.getCreatedAt(), from, period)) {
                continue;
            }
            crees++;
            String statut = ticket.getStatut() == null ? "OUVERT" : ticket.getStatut();
            boolean taken = ticket.getPremierePriseEnChargeAt() != null
                    || Set.of("EN_COURS", "PRIS_EN_CHARGE", "EN_ATTENTE", "RESOLU", "CLOTURE").contains(statut);
            boolean progressed = ticket.getPremierePriseEnChargeAt() != null
                    || Set.of("EN_COURS", "PRIS_EN_CHARGE", "RESOLU", "CLOTURE").contains(statut);
            boolean resolved = ticket.getResoluAt() != null || Set.of("RESOLU", "CLOTURE").contains(statut);
            boolean closed = "CLOTURE".equals(statut);
            if (taken) {
                pris++;
            }
            if (progressed) {
                enCours++;
            }
            if (resolved) {
                resolus++;
            }
            if (closed) {
                clotures++;
            }
        }
        return List.of(
                point("CREES", "Créés", crees),
                point("PRIS_EN_CHARGE", "Pris en charge", pris),
                point("EN_COURS", "En cours", enCours),
                point("RESOLUS", "Résolus", resolus),
                point("CLOTURES", "Clôturés", clotures)
        );
    }

    private List<Map<String, Object>> agentsVsTickets(List<Agent> agents, List<Ticket> tickets, Instant from, String period, LocalDate today) {
        LocalDate start = "ALL".equals(period) ? today.minusWeeks(11) : from.atZone(ZONE).toLocalDate();
        start = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (LocalDate week = start; !week.isAfter(today); week = week.plusWeeks(1)) {
            buckets.put(week, new long[] { 0, 0 });
        }
        for (Agent agent : agents) {
            if (agent.getCreatedAt() == null || !inPeriod(agent.getCreatedAt(), from, period)) {
                continue;
            }
            LocalDate week = agent.getCreatedAt().atZone(ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (buckets.containsKey(week)) {
                buckets.get(week)[0]++;
            }
        }
        for (Ticket ticket : tickets) {
            if (ticket.getCreatedAt() == null || !inPeriod(ticket.getCreatedAt(), from, period)) {
                continue;
            }
            LocalDate week = ticket.getCreatedAt().atZone(ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (buckets.containsKey(week)) {
                buckets.get(week)[1]++;
            }
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> entry : buckets.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("semaine", entry.getKey().toString());
            row.put("label", entry.getKey().format(fmt));
            row.put("agents", entry.getValue()[0]);
            row.put("tickets", entry.getValue()[1]);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> resolutionScatter(List<Ticket> tickets, Instant from, String period) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if (ticket.getResoluAt() == null || ticket.getCreatedAt() == null) {
                continue;
            }
            if (!inPeriod(ticket.getCreatedAt(), from, period)) {
                continue;
            }
            String priorite = normalizePriorite(ticket.getPriorite());
            long minutes = Duration.between(ticket.getCreatedAt(), ticket.getResoluAt()).toMinutes();
            if (minutes < 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ticket.getId());
            row.put("reference", ticket.getReference());
            row.put("priorite", priorite);
            row.put("prioriteLabel", prioriteLabel(priorite));
            row.put("x", prioriteIndex(priorite));
            row.put("heures", Math.round(minutes / 6.0) / 10.0);
            rows.add(row);
            if (rows.size() >= 250) {
                break;
            }
        }
        return rows;
    }

    private static String normalizePriorite(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NORMALE";
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "HIGH", "CRITIQUE" -> "CRITIQUE";
            case "MEDIUM", "ELEVEE" -> "ELEVEE";
            case "LOW", "FAIBLE" -> "FAIBLE";
            default -> "NORMALE";
        };
    }

    private static String prioriteLabel(String code) {
        return switch (code) {
            case "CRITIQUE" -> "Critique";
            case "ELEVEE" -> "Élevée";
            case "FAIBLE" -> "Faible";
            default -> "Normale";
        };
    }

    private static int prioriteIndex(String code) {
        return switch (code) {
            case "FAIBLE" -> 1;
            case "ELEVEE" -> 3;
            case "CRITIQUE" -> 4;
            default -> 2;
        };
    }

    private static String categorieLabel(String code) {
        return switch (code) {
            case "REFUS_COURSE" -> "Refus de course";
            case "RETARD" -> "Retard";
            case "LITIGE_PAIEMENT" -> "Litige paiement";
            case "DESTINATAIRE_INJOIGNABLE" -> "Destinataire injoignable";
            case "BUG" -> "Bug";
            case "DEPLOIEMENT" -> "Déploiement";
            case "CORRECTIF" -> "Correctif";
            case "AUTRE" -> "Autre";
            default -> code.replace('_', ' ');
        };
    }
}
