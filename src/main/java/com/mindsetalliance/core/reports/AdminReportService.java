package com.mindsetalliance.core.reports;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.AccessAdminService;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentPermissionOverride;
import com.mindsetalliance.core.iam.AgentPermissionOverrideRepository;
import com.mindsetalliance.core.iam.AgentRepository;
import com.mindsetalliance.core.iam.AgentRole;
import com.mindsetalliance.core.iam.AgentRoleRepository;
import com.mindsetalliance.core.iam.Departement;
import com.mindsetalliance.core.iam.DepartementAdminService;
import com.mindsetalliance.core.iam.Permission;
import com.mindsetalliance.core.iam.PermissionRepository;
import com.mindsetalliance.core.iam.Project;
import com.mindsetalliance.core.iam.ProjectRepository;
import com.mindsetalliance.core.iam.Role;
import com.mindsetalliance.core.iam.RoleRepository;
import com.mindsetalliance.core.iam.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminReportService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Kinshasa");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private final AgentRepository agentRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final AgentPermissionOverrideRepository overrideRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ProjectRepository projectRepository;
    private final AccessAdminService accessAdminService;
    private final DepartementAdminService departementAdminService;
    private final StatsService statsService;
    private final AuditService auditService;
    private final TransactionTemplate readTx;

    public AdminReportService(AgentRepository agentRepository,
                              AgentRoleRepository agentRoleRepository,
                              AgentPermissionOverrideRepository overrideRepository,
                              RoleRepository roleRepository,
                              PermissionRepository permissionRepository,
                              ProjectRepository projectRepository,
                              AccessAdminService accessAdminService,
                              DepartementAdminService departementAdminService,
                              StatsService statsService,
                              AuditService auditService,
                              PlatformTransactionManager transactionManager) {
        this.agentRepository = agentRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.overrideRepository = overrideRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.projectRepository = projectRepository;
        this.accessAdminService = accessAdminService;
        this.departementAdminService = departementAdminService;
        this.statsService = statsService;
        this.auditService = auditService;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.readTx.setName("admin-report-read");
    }

    public byte[] utilisateurs(Long departementId, String statut) {
        Map<String, Object> meta = new LinkedHashMap<>();
        byte[] pdf = readTx.execute(status -> {
        List<Agent> agents = agentRepository.findAllWithDepartement().stream()
                .filter(a -> departementId == null || (a.getDepartement() != null && departementId.equals(a.getDepartement().getId())))
                .filter(a -> statut == null || statut.isBlank() || statut.equalsIgnoreCase(a.getStatut()))
                .sorted(Comparator.comparing((Agent a) -> nullToEmpty(a.getNom())).thenComparing(a -> nullToEmpty(a.getPrenom())))
                .toList();
        List<AgentRole> assignments = agentRoleRepository.findAllWithGraph();
        Map<Long, List<AgentRole>> byAgent = assignments.stream()
                .filter(ar -> ar.getAgent() != null)
                .collect(Collectors.groupingBy(ar -> ar.getAgent().getId()));
        Map<String, Object> kpis = accessAdminService.accessStats();
        byte[] built = ReportPdfTheme.build(ReportI18n.t("title.utilisateurs"), author(), true, document -> {
            addKpis(document, new String[] { ReportI18n.t("kpi.total"), ReportI18n.t("kpi.actifs"), ReportI18n.t("kpi.entreprise"), ReportI18n.t("kpi.projetSeul") },
                    new String[] { str(kpis.get("total")), str(kpis.get("actifs")), str(kpis.get("entreprise")), str(kpis.get("projetSeul")) });
            ReportPdfTheme.paragraph(document, filterCaption(departementId, statut));
            PdfPTable table = new PdfPTable(new float[] { 2.2f, 2.6f, 2.2f, 2.4f, 1.8f, 1.2f, 1.3f });
            table.setWidthPercentage(100);
            for (String h : List.of(ReportI18n.t("col.agent"), ReportI18n.t("col.email"), ReportI18n.t("col.departement"), ReportI18n.t("col.roles"), ReportI18n.t("col.perimetre"), ReportI18n.t("col.statut"), ReportI18n.t("col.created"))) {
                table.addCell(ReportPdfTheme.head(h));
            }
            int i = 0;
            for (Agent agent : agents) {
                List<AgentRole> roles = byAgent.getOrDefault(agent.getId(), List.of());
                table.addCell(ReportPdfTheme.body(name(agent), i, true));
                table.addCell(ReportPdfTheme.body(agent.getEmailPro(), i, true));
                table.addCell(ReportPdfTheme.body(agent.getDepartement() == null ? "—" : agent.getDepartement().getNom(), i, true));
                table.addCell(rolesCell(roles, i));
                table.addCell(ReportPdfTheme.body(perimeter(roles), i, true));
                table.addCell(ReportPdfTheme.badge(ReportI18n.statut(agent.getStatut()), ReportPdfTheme.statutColor(agent.getStatut()), i));
                table.addCell(ReportPdfTheme.body(formatDay(agent.getCreatedAt()), i, true));
                i++;
            }
            add(document, table);
        });
        meta.put("departementId", departementId == null ? "" : departementId);
        meta.put("statut", statut == null ? "" : statut);
        meta.put("lignes", agents.size());
        return built;
        });
        audit("UTILISATEURS", meta);
        return java.util.Objects.requireNonNull(pdf);
    }

    public byte[] permissions() {
        Map<String, Object> meta = new LinkedHashMap<>();
        byte[] pdf = readTx.execute(status -> {
        List<Role> roles = roleRepository.findAllWithPermissions().stream()
                .sorted(Comparator.comparing(Role::getNom, Comparator.nullsLast(String::compareTo)))
                .toList();
        List<Permission> permissions = permissionRepository.findAll().stream()
                .sorted(Comparator.comparing((Permission p) -> nullToEmpty(p.getModule()))
                        .thenComparing(p -> nullToEmpty(p.getLibelle())))
                .toList();
        Map<String, List<Permission>> byModule = new LinkedHashMap<>();
        for (Permission permission : permissions) {
            String module = permission.getModule() == null || permission.getModule().isBlank() ? "Transversal" : permission.getModule();
            byModule.computeIfAbsent(module, k -> new ArrayList<>()).add(permission);
        }
        byte[] built = ReportPdfTheme.build(ReportI18n.t("title.permissions"), author(), true, document -> {
            addKpis(document, new String[] { ReportI18n.t("kpi.roles"), ReportI18n.t("kpi.permissions"), ReportI18n.t("kpi.modules") },
                    new String[] { String.valueOf(roles.size()), String.valueOf(permissions.size()), String.valueOf(byModule.size()) });
            float[] widths = new float[roles.size() + 1];
            widths[0] = 3.4f;
            for (int c = 1; c < widths.length; c++) {
                widths[c] = 1f;
            }
            for (Map.Entry<String, List<Permission>> entry : byModule.entrySet()) {
                ReportPdfTheme.h2(document, entry.getKey());
                PdfPTable table = new PdfPTable(widths);
                table.setWidthPercentage(100);
                table.addCell(ReportPdfTheme.head(ReportI18n.t("col.permission")));
                for (Role role : roles) {
                    table.addCell(ReportPdfTheme.head(ReportI18n.role(role.getNom())));
                }
                int i = 0;
                for (Permission permission : entry.getValue()) {
                    table.addCell(ReportPdfTheme.body(permission.getLibelle() == null ? permission.getCode() : permission.getLibelle(), i, true));
                    Set<String> holders = permission.getCode() == null ? Set.of() : roles.stream()
                            .filter(r -> r.getPermissions() != null && r.getPermissions().stream().anyMatch(p -> permission.getCode().equals(p.getCode())))
                            .map(Role::getNom)
                            .collect(Collectors.toSet());
                    for (Role role : roles) {
                        boolean has = holders.contains(role.getNom());
                        PdfPCell cell = ReportPdfTheme.body(has ? ReportI18n.t("yes") : ReportI18n.t("dash"), i, true);
                        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        if (has) {
                            cell.setBackgroundColor(new java.awt.Color(0xec, 0xfc, 0xf3));
                            cell.setPhrase(new Phrase(ReportI18n.t("yes"), ReportPdfTheme.font(7, Font.BOLD, ReportPdfTheme.SUCCESS)));
                        }
                        table.addCell(cell);
                    }
                    i++;
                }
                add(document, table);
            }
        });
        meta.put("roles", roles.size());
        meta.put("permissions", permissions.size());
        return built;
        });
        audit("PERMISSIONS", meta);
        return java.util.Objects.requireNonNull(pdf);
    }

    public byte[] acces(String statut) {
        Map<String, Object> meta = new LinkedHashMap<>();
        byte[] pdf = readTx.execute(status -> {
        List<Agent> agents = agentRepository.findAllWithDepartement().stream()
                .filter(a -> statut == null || statut.isBlank() || statut.equalsIgnoreCase(a.getStatut()))
                .sorted(Comparator.comparing((Agent a) -> nullToEmpty(a.getNom())))
                .toList();
        Map<Long, List<AgentRole>> byAgent = agentRoleRepository.findAllWithGraph().stream()
                .filter(ar -> ar.getAgent() != null)
                .collect(Collectors.groupingBy(ar -> ar.getAgent().getId()));
        Map<Long, List<AgentPermissionOverride>> overrides = overrideRepository.findAllWithGraph().stream()
                .filter(o -> o.getAgent() != null)
                .collect(Collectors.groupingBy(o -> o.getAgent().getId()));
        long grants = overrides.values().stream().flatMap(List::stream).filter(o -> "GRANT".equalsIgnoreCase(o.getType())).count();
        long denys = overrides.values().stream().flatMap(List::stream).filter(o -> "DENY".equalsIgnoreCase(o.getType())).count();
        byte[] built = ReportPdfTheme.build(ReportI18n.t("title.acces"), author(), true, document -> {
            addKpis(document, new String[] { ReportI18n.t("kpi.agents"), ReportI18n.t("kpi.grants"), ReportI18n.t("kpi.denys") },
                    new String[] { String.valueOf(agents.size()), String.valueOf(grants), String.valueOf(denys) });
            for (Agent agent : agents) {
                ReportPdfTheme.h2(document, name(agent) + "  ·  " + nullToEmpty(agent.getEmailPro()));
                List<AgentRole> roles = byAgent.getOrDefault(agent.getId(), List.of());
                PdfPTable table = new PdfPTable(new float[] { 2f, 2.4f, 2.2f });
                table.setWidthPercentage(100);
                table.addCell(ReportPdfTheme.head(ReportI18n.t("col.role")));
                table.addCell(ReportPdfTheme.head(ReportI18n.t("col.perimetre")));
                table.addCell(ReportPdfTheme.head(ReportI18n.t("col.accessType")));
                int i = 0;
                if (roles.isEmpty()) {
                    table.addCell(ReportPdfTheme.body(ReportI18n.t("noRole"), 0, true));
                    table.addCell(ReportPdfTheme.body(ReportI18n.t("dash"), 0, true));
                    table.addCell(ReportPdfTheme.body(ReportI18n.t("dash"), 0, true));
                }
                for (AgentRole assignment : roles) {
                    String role = assignment.getRole() == null ? "—" : assignment.getRole().getNom();
                    table.addCell(ReportPdfTheme.badge(ReportI18n.role(role), ReportPdfTheme.roleColor(role), i));
                    boolean wide = assignment.getProject() == null;
                    table.addCell(ReportPdfTheme.body(wide ? ReportI18n.t("perimeter.company") : assignment.getProject().getNom(), i, true));
                    table.addCell(ReportPdfTheme.body(wide ? ReportI18n.t("access.transverse") : ReportI18n.t("access.limited"), i, true));
                    i++;
                }
                add(document, table);
                List<AgentPermissionOverride> rows = overrides.getOrDefault(agent.getId(), List.of());
                if (!rows.isEmpty()) {
                    ReportPdfTheme.paragraph(document, ReportI18n.t("overrides"));
                    PdfPTable ov = new PdfPTable(new float[] { 1.2f, 2.4f, 2f, 3f, 1.6f });
                    ov.setWidthPercentage(100);
                    for (String h : List.of(ReportI18n.t("col.type"), ReportI18n.t("col.permission"), ReportI18n.t("col.perimetre"), ReportI18n.t("col.motif"), ReportI18n.t("col.date"))) {
                        ov.addCell(ReportPdfTheme.head(h));
                    }
                    int r = 0;
                    for (AgentPermissionOverride override : rows) {
                        boolean grant = "GRANT".equalsIgnoreCase(override.getType());
                        ov.addCell(ReportPdfTheme.badge(override.getType(), grant ? ReportPdfTheme.SUCCESS : ReportPdfTheme.DANGER, r));
                        String perm = override.getPermission() == null ? "—" : override.getPermission().getLibelle();
                        ov.addCell(ReportPdfTheme.body(perm, r, true));
                        ov.addCell(ReportPdfTheme.body(override.getProject() == null ? ReportI18n.t("perimeter.company") : override.getProject().getNom(), r, true));
                        ov.addCell(ReportPdfTheme.body(override.getMotif(), r, true));
                        ov.addCell(ReportPdfTheme.body(formatDay(override.getCreatedAt()), r, true));
                        r++;
                    }
                    add(document, ov);
                }
            }
        });
        meta.put("agents", agents.size());
        meta.put("grants", grants);
        meta.put("denys", denys);
        return built;
        });
        audit("ACCES", meta);
        return java.util.Objects.requireNonNull(pdf);
    }

    public byte[] organisation() {
        Map<String, Object> meta = new LinkedHashMap<>();
        byte[] pdf = readTx.execute(status -> {
        List<Map<String, Object>> departements = departementAdminService.list();
        List<Project> projects = projectRepository.findAll().stream()
                .sorted(Comparator.comparing(Project::getNom, Comparator.nullsLast(String::compareTo)))
                .toList();
        byte[] built = ReportPdfTheme.build(ReportI18n.t("title.organisation"), author(), false, document -> {
            addKpis(document, new String[] { ReportI18n.t("kpi.departements"), ReportI18n.t("kpi.societes") },
                    new String[] { String.valueOf(departements.size()), String.valueOf(projects.size()) });
            ReportPdfTheme.h2(document, ReportI18n.t("section.depts"));
            PdfPTable dept = new PdfPTable(new float[] { 2.6f, 2.8f, 1.2f, 1.2f });
            dept.setWidthPercentage(100);
            for (String h : List.of(ReportI18n.t("col.departement"), ReportI18n.t("col.defaultRoles"), ReportI18n.t("col.agents"), ReportI18n.t("col.statut"))) {
                dept.addCell(ReportPdfTheme.head(h));
            }
            int i = 0;
            for (Map<String, Object> row : departements) {
                dept.addCell(ReportPdfTheme.body(str(row.get("nom")), i, true));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> roles = (List<Map<String, Object>>) row.getOrDefault("roles", List.of());
                String roleNames = roles.stream().map(r -> ReportI18n.role(str(r.get("nom")))).collect(Collectors.joining(", "));
                dept.addCell(ReportPdfTheme.body(roleNames.isBlank() ? "—" : roleNames, i, true));
                dept.addCell(ReportPdfTheme.body(str(row.get("agentsCount")), i, true));
                dept.addCell(ReportPdfTheme.badge(ReportI18n.statut(str(row.get("statut"))), ReportPdfTheme.statutColor(str(row.get("statut"))), i));
                i++;
            }
            add(document, dept);

            ReportPdfTheme.h2(document, ReportI18n.t("section.companies"));
            PdfPTable soc = new PdfPTable(new float[] { 1.2f, 2.6f, 1.4f, 1.2f });
            soc.setWidthPercentage(100);
            for (String h : List.of(ReportI18n.t("col.code"), ReportI18n.t("col.societe"), ReportI18n.t("col.agents"), ReportI18n.t("col.statut"))) {
                soc.addCell(ReportPdfTheme.head(h));
            }
            int j = 0;
            for (Project project : projects) {
                soc.addCell(ReportPdfTheme.body(project.getCode(), j, true));
                soc.addCell(ReportPdfTheme.body(project.getNom(), j, true));
                soc.addCell(ReportPdfTheme.body(String.valueOf(agentRoleRepository.countAgentsWithAccess(project.getId())), j, true));
                soc.addCell(ReportPdfTheme.badge(ReportI18n.statut(project.getStatut()), ReportPdfTheme.statutColor(project.getStatut()), j));
                j++;
            }
            add(document, soc);
        });
        meta.put("departements", departements.size());
        meta.put("societes", projects.size());
        return built;
        });
        audit("ORGANISATION", meta);
        return java.util.Objects.requireNonNull(pdf);
    }

    public byte[] statistiques(String period) {
        Map<String, Object> meta = new LinkedHashMap<>();
        byte[] pdf = readTx.execute(status -> {
        Map<String, Object> snap = statsService.snapshot(period);
        byte[] built = ReportPdfTheme.build(ReportI18n.t("title.statistiques"), author(), false, document -> {
            ReportPdfTheme.paragraph(document, ReportI18n.t("period.caption") + ReportI18n.period(String.valueOf(snap.get("period"))));
            @SuppressWarnings("unchecked")
            Map<String, Object> actifs = (Map<String, Object>) snap.get("agentsActifs");
            addKpis(document, new String[] { ReportI18n.t("kpi.actifs"), ReportI18n.t("kpi.total"), ReportI18n.t("kpi.taux") },
                    new String[] { str(actifs.get("actifs")), str(actifs.get("total")), str(actifs.get("taux")) + " %" });
            addPointsTable(document, ReportI18n.t("chart.dept"), ReportI18n.t("col.dept"), (List<Map<String, Object>>) snap.get("agentsParDepartement"));
            addPointsTable(document, ReportI18n.t("chart.scope"), ReportI18n.t("col.scopeType"), (List<Map<String, Object>>) snap.get("agentsParPerimetre"));
            addPointsTable(document, ReportI18n.t("chart.ticketStatus"), ReportI18n.t("col.ticketStatus"), (List<Map<String, Object>>) snap.get("ticketsParStatut"));
            addPointsTable(document, ReportI18n.t("chart.seniority"), ReportI18n.t("col.band"), (List<Map<String, Object>>) snap.get("ancienneteComptes"));
            addPointsTable(document, ReportI18n.t("chart.permTop"), ReportI18n.t("col.permission"), (List<Map<String, Object>>) snap.get("permissionsTop"));
            addPointsTable(document, ReportI18n.t("chart.funnel"), ReportI18n.t("col.step"), (List<Map<String, Object>>) snap.get("ticketFunnel"));
        });
        meta.put("period", period == null ? "30" : period);
        return built;
        });
        audit("STATISTIQUES", meta);
        return java.util.Objects.requireNonNull(pdf);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historique() {
        return auditService.recentExports().stream().map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("createdAt", log.getCreatedAt());
            Map<String, Object> apres = log.getValeurApres() == null ? Map.of() : log.getValeurApres();
            String type = String.valueOf(apres.getOrDefault("type", ""));
            row.put("type", type);
            row.put("titre", ReportI18n.titleForType(type));
            row.put("kind", reportKind(type));
            row.put("details", apres.get("details"));
            if (log.getAgent() != null) {
                row.put("administrateur", (nullToEmpty(log.getAgent().getPrenom()) + " " + nullToEmpty(log.getAgent().getNom())).trim());
                row.put("email", log.getAgent().getEmailPro());
            } else {
                row.put("administrateur", ReportI18n.t("author.fallback"));
            }
            return row;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private void addPointsTable(Document document, String title, String col, List<Map<String, Object>> rows) {
        ReportPdfTheme.h2(document, title);
        PdfPTable table = new PdfPTable(new float[] { 3.5f, 1.2f });
        table.setWidthPercentage(100);
        table.addCell(ReportPdfTheme.head(col));
        table.addCell(ReportPdfTheme.head(ReportI18n.t("col.value")));
        int i = 0;
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            table.addCell(ReportPdfTheme.body(str(row.get("label")), i, true));
            table.addCell(ReportPdfTheme.body(str(row.get("valeur")), i, true));
            i++;
        }
        add(document, table);
    }

    private void addKpis(Document document, String[] labels, String[] values) {
        try {
            document.add(ReportPdfTheme.kpiRow(labels, values));
        } catch (DocumentException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void add(Document document, PdfPTable table) {
        try {
            document.add(table);
            document.add(new Paragraph(" ", ReportPdfTheme.font(6, Font.NORMAL, ReportPdfTheme.WHITE)));
        } catch (DocumentException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private PdfPCell rolesCell(List<AgentRole> roles, int row) {
        if (roles.isEmpty()) {
            return ReportPdfTheme.body(ReportI18n.t("none"), row, true);
        }
        Phrase phrase = new Phrase();
        int n = 0;
        for (AgentRole assignment : roles) {
            if (n > 0) {
                phrase.add(new Phrase(" ", ReportPdfTheme.font(7, Font.NORMAL, ReportPdfTheme.NAVY)));
            }
            String role = assignment.getRole() == null ? "?" : assignment.getRole().getNom();
            phrase.add(badgeChunk(ReportI18n.role(role), ReportPdfTheme.roleColor(role)));
            n++;
        }
        PdfPCell cell = new PdfPCell(phrase);
        cell.setPadding(5);
        cell.setBorderColor(ReportPdfTheme.LINE);
        if (row % 2 == 1) {
            cell.setBackgroundColor(ReportPdfTheme.ZEBRA);
        }
        return cell;
    }

    private com.lowagie.text.Chunk badgeChunk(String text, java.awt.Color color) {
        com.lowagie.text.Chunk chunk = new com.lowagie.text.Chunk("  " + text + "  ", ReportPdfTheme.font(7, Font.BOLD, ReportPdfTheme.WHITE));
        chunk.setBackground(color, 2, 1, 2, 1);
        return chunk;
    }

    private String perimeter(List<AgentRole> roles) {
        boolean wide = roles.stream().anyMatch(r -> r.getProject() == null);
        if (wide) {
            return ReportI18n.t("perimeter.company");
        }
        String codes = roles.stream()
                .map(AgentRole::getProject)
                .filter(Objects::nonNull)
                .map(Project::getCode)
                .distinct()
                .collect(Collectors.joining(", "));
        return codes.isBlank() ? ReportI18n.t("perimeter.none") : codes;
    }

    private String author() {
        try {
            Long id = JwtRoles.agentId();
            if (id == null) {
                return ReportI18n.t("author.fallback");
            }
            return agentRepository.findById(id)
                    .map(AdminReportService::name)
                    .orElse(ReportI18n.t("author.fallback"));
        } catch (Exception ex) {
            return ReportI18n.t("author.fallback");
        }
    }

    private void audit(String type, Map<String, Object> details) {
        try {
            auditService.record(JwtRoles.agentId(), "EXPORT_REPORT", "RAPPORT", null, null,
                    Map.of("type", type, "details", details == null ? Map.of() : details));
        } catch (Exception ex) {
            log.warn("Journalisation de l’export {} impossible : {}", type, ex.getMessage());
        }
    }

    private static String reportKind(String type) {
        return switch (type) {
            case "UTILISATEURS" -> "utilisateurs";
            case "PERMISSIONS" -> "permissions";
            case "ACCES" -> "acces";
            case "ORGANISATION" -> "organisation";
            case "STATISTIQUES" -> "statistiques";
            default -> "";
        };
    }

    private String filterCaption(Long departementId, String statut) {
        List<String> bits = new ArrayList<>();
        if (departementId != null) {
            bits.add(ReportI18n.t("filter.dept") + departementId);
        }
        if (statut != null && !statut.isBlank()) {
            bits.add(ReportI18n.t("filter.statut") + ReportI18n.statut(statut));
        }
        return bits.isEmpty() ? ReportI18n.t("filter.allAgents") : ReportI18n.t("filter.prefix") + String.join(" · ", bits);
    }

    private static String name(Agent agent) {
        return (nullToEmpty(agent.getPrenom()) + " " + nullToEmpty(agent.getNom())).trim();
    }

    private static String formatDay(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return DAY.format(instant.atZone(ZONE));
    }

    private static String str(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
