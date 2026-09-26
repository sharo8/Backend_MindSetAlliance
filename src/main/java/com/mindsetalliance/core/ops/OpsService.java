package com.mindsetalliance.core.ops;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.AgentRepository;
import com.mindsetalliance.core.iam.ProjectRepository;
import com.mindsetalliance.core.iam.RoleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OpsService {

    private static final Set<String> TERMINAL = Set.of(
            "ARCHIVE", "ANNULE", "ANNULEE", "REJETE", "RESILIE", "DESACTIVE");

    private final OpsRecordRepository repository;
    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;
    private final AuditService auditService;

    private final RoleRepository roleRepository;

    public OpsService(OpsRecordRepository repository, ProjectRepository projectRepository,
                      AgentRepository agentRepository, AuditService auditService, RoleRepository roleRepository) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.agentRepository = agentRepository;
        this.auditService = auditService;
        this.roleRepository = roleRepository;
    }

    public void assertModule(String module, boolean write) {
        if (JwtRoles.hasFullAccess(JwtRoles.currentJwt())) {
            return;
        }
        Set<String> names = JwtRoles.roleNames(JwtRoles.currentJwt());
        boolean ok = Arrays.stream(permissionsOf(module, write))
                .anyMatch(code -> roleRepository.countHavingPermission(names, code) > 0);
        if (!ok) {
            throw new AccessDeniedException("Permission insuffisante pour ce module");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String module, String projectCode) {
        assertModule(module, false);
        JwtRoles.assertCanSeeProject(projectCode);
        return repository.findByModuleOrderByCreatedAtDesc(module.toUpperCase()).stream()
                .filter(record -> matchesProject(record, projectCode))
                .filter(this::visibleToCaller)
                .map(this::toMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> create(String module, Upsert body) {
        assertModule(module, true);
        JwtRoles.assertCanSeeProject(body.projectCode());
        if (body.titre() == null || body.titre().isBlank()) {
            throw new BusinessException("Le titre est obligatoire");
        }
        OpsRecord record = new OpsRecord();
        record.setModule(module.toUpperCase());
        apply(record, body, true);
        record.setCreatedBy(agentRepository.findById(JwtRoles.agentId()).orElse(null));
        repository.save(record);
        auditService.record(JwtRoles.agentId(), "CREATE", "OPS_" + record.getModule(), record.getId(), null, Map.of("titre", record.getTitre()));
        return toMap(record);
    }

    @Transactional
    public Map<String, Object> update(String module, Long id, Upsert body) {
        assertModule(module, true);
        OpsRecord record = get(module, id);
        Map<String, Object> avant = Map.of("titre", record.getTitre(), "statut", record.getStatut());
        apply(record, body, false);
        record.setUpdatedAt(Instant.now());
        auditService.record(JwtRoles.agentId(), "UPDATE", "OPS_" + record.getModule(), id, avant,
                Map.of("titre", record.getTitre(), "statut", record.getStatut()));
        return toMap(record);
    }

    @Transactional
    public Map<String, Object> archive(String module, Long id, String motif) {
        assertModule(module, true);
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif d'annulation ou d'archivage est obligatoire");
        }
        OpsRecord record = get(module, id);
        String avant = record.getStatut();
        String next = switch (module.toUpperCase()) {
            case "COURIER" -> "REJETE";
            case "ENTERPRISE", "PARTNER", "SUBSCRIPTION" -> "RESILIE";
            case "LEAVE" -> "ANNULE";
            default -> "ARCHIVE";
        };
        record.setStatut(next);
        record.setMotif(motif);
        record.setUpdatedAt(Instant.now());
        auditService.record(JwtRoles.agentId(), "ARCHIVE", "OPS_" + record.getModule(), id,
                Map.of("statut", avant), Map.of("statut", next, "motif", motif));
        return toMap(record);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cnnDashboard(String projectCode) {
        if (!JwtRoles.hasFullAccess(JwtRoles.currentJwt())) {
            assertModule("COURSE", false);
        }
        JwtRoles.assertCanSeeProject(projectCode);
        List<OpsRecord> courses = scopedRecords("COURSE", projectCode);
        List<OpsRecord> incidents = scopedRecords("INCIDENT", projectCode);
        List<OpsRecord> coursiers = scopedRecords("COURIER", projectCode);
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Kinshasa"));
        long coursesToday = courses.stream().filter(c -> localDate(c).equals(today)).count();
        long actifs = coursiers.stream()
                .filter(c -> c.getStatut() != null && !TERMINAL.contains(c.getStatut()) && !"REJETE".equals(c.getStatut()))
                .count();
        long livrees = courses.stream().filter(c -> "LIVREE".equals(c.getStatut())).count();
        double taux = courses.isEmpty() ? 0 : Math.round(1000.0 * livrees / courses.size()) / 10.0;
        long incidentsOuverts = incidents.stream().filter(i -> !TERMINAL.contains(i.getStatut()) && !"RESOLU".equals(i.getStatut())).count();

        Map<LocalDate, Long> byDay = courses.stream().collect(Collectors.groupingBy(this::localDate, Collectors.counting()));
        List<Map<String, Object>> volume = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            volume.add(Map.of("jour", d.toString(), "volume", byDay.getOrDefault(d, 0L)));
        }
        Map<String, Long> byZone = courses.stream()
                .collect(Collectors.groupingBy(c -> c.getZone() == null ? "Autre" : c.getZone(), Collectors.counting()));
        List<Map<String, Object>> zones = byZone.entrySet().stream()
                .map(e -> Map.of("zone", (Object) e.getKey(), "volume", e.getValue()))
                .toList();
        Map<String, Long> byStatut = courses.stream()
                .collect(Collectors.groupingBy(c -> c.getStatut() == null ? "INCONNU" : c.getStatut(), Collectors.counting()));
        List<Map<String, Object>> statuts = byStatut.entrySet().stream()
                .map(e -> Map.of("statut", (Object) e.getKey(), "volume", e.getValue()))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("coursesDuJour", coursesToday);
        body.put("coursiersActifs", actifs);
        body.put("tauxLivraison", taux);
        body.put("incidentsOuverts", incidentsOuverts);
        body.put("live", false);
        body.put("source", "CORE_DEMO");
        body.put("scope", projectCode);
        body.put("volume30j", volume);
        body.put("parZone", zones);
        body.put("parStatut", statuts);
        body.put("incidents", incidents.stream().limit(8).map(this::toMap).toList());
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> financeDashboard(String projectCode) {
        JwtRoles.assertCanSeeProject(projectCode);
        List<OpsRecord> invoices = scopedRecords("INVOICE", projectCode);
        BigDecimal recettes = invoices.stream()
                .filter(i -> i.getMontant() != null && !"ANNULEE".equals(i.getStatut()) && !"ARCHIVE".equals(i.getStatut()))
                .map(OpsRecord::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long retard = invoices.stream().filter(i -> "EN_RETARD".equals(i.getStatut())).count();
        Map<String, BigDecimal> byCanal = invoices.stream()
                .filter(i -> i.getCanal() != null && i.getMontant() != null)
                .collect(Collectors.groupingBy(OpsRecord::getCanal,
                        Collectors.reducing(BigDecimal.ZERO, OpsRecord::getMontant, BigDecimal::add)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recettesMois", recettes);
        body.put("depensesMois", recettes.multiply(new BigDecimal("0.42")));
        body.put("facturesRetard", retard);
        body.put("tresorerie", recettes.multiply(new BigDecimal("0.58")));
        List<Map<String, Object>> months = new ArrayList<>();
        String[] labels = {"Avr", "Mai", "Juin", "Juil", "Août", "Sept"};
        for (int i = 0; i < 6; i++) {
            months.add(Map.of("mois", labels[i], "recettes", 800 + i * 120, "depenses", 400 + i * 70));
        }
        body.put("comparaison6m", months);
        body.put("parCanal", byCanal.entrySet().stream()
                .map(e -> Map.of("canal", (Object) e.getKey(), "montant", e.getValue()))
                .toList());
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> rhDashboard(String projectCode) {
        JwtRoles.assertCanSeeProject(projectCode);
        long effectif = agentRepository.findAll().stream().filter(a -> "ACTIF".equals(a.getStatut())).count();
        long conges = scopedRecords("LEAVE", projectCode).stream()
                .filter(l -> "EN_ATTENTE".equals(l.getStatut()))
                .count();
        Map<String, Long> byDep = agentRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        a -> a.getDepartement() == null ? "Sans département" : a.getDepartement().getNom(),
                        Collectors.counting()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("effectif", effectif);
        body.put("presence", 94);
        body.put("congesAttente", conges);
        body.put("contratsEcheance", 2);
        body.put("parDepartement", byDep.entrySet().stream()
                .map(e -> Map.of("departement", (Object) e.getKey(), "effectif", e.getValue()))
                .toList());
        return body;
    }

    private OpsRecord get(String module, Long id) {
        OpsRecord record = repository.findById(id).orElseThrow(() -> new BusinessException("Enregistrement introuvable", 404));
        if (!module.equalsIgnoreCase(record.getModule())) {
            throw new BusinessException("Enregistrement introuvable", 404);
        }
        return record;
    }

    private void apply(OpsRecord record, Upsert body, boolean creating) {
        record.setTitre(body.titre());
        if (body.description() != null) {
            record.setDescription(body.description());
        }
        if (body.statut() != null || creating) {
            record.setStatut(body.statut() == null || body.statut().isBlank() ? defaultStatut(record.getModule()) : body.statut());
        }
        record.setPriorite(body.priorite());
        record.setZone(body.zone());
        record.setCanal(body.canal());
        record.setMontant(body.montant());
        if (body.projectCode() != null && !body.projectCode().isBlank()) {
            record.setProject(projectRepository.findByCode(body.projectCode())
                    .orElseThrow(() -> new BusinessException("Projet introuvable")));
        }
    }

    private String[] permissionsOf(String module, boolean write) {
        return switch (module.toUpperCase()) {
            case "COURSE" -> write ? new String[]{"MANAGE_COURSES"} : new String[]{"VIEW_FLEET_MAP", "MANAGE_COURSES"};
            case "INCIDENT" -> new String[]{"RESOLVE_INCIDENTS"};
            case "COURIER" -> new String[]{"VALIDATE_COURIER"};
            case "ENTERPRISE" -> new String[]{"MANAGE_ENTERPRISE_ACCOUNTS"};
            case "SUBSCRIPTION" -> new String[]{"MANAGE_SUBSCRIPTIONS"};
            case "INVOICE" -> new String[]{"MANAGE_INVOICING"};
            case "B2C" -> new String[]{"VIEW_B2C_VOLUME"};
            case "LEAVE" -> new String[]{"MANAGE_STAFF"};
            case "PARTNER" -> new String[]{"MANAGE_PARTNER_REGISTRY"};
            case "COMPLIANCE" -> new String[]{"MANAGE_COMPLIANCE"};
            case "MEDIA" -> new String[]{"MANAGE_MEDIA_LIBRARY"};
            case "MARKETING" -> new String[]{"VIEW_MARKETING_PERFORMANCE"};
            case "MONITORING" -> new String[]{"VIEW_MONITORING"};
            case "BUG" -> new String[]{"MANAGE_BUG_TICKETS"};
            default -> throw new BusinessException("Module inconnu");
        };
    }

    private List<OpsRecord> scopedRecords(String module, String projectCode) {
        return repository.findByModuleOrderByCreatedAtDesc(module).stream()
                .filter(record -> matchesProject(record, projectCode))
                .filter(this::visibleToCaller)
                .toList();
    }

    private boolean visibleToCaller(OpsRecord record) {
        Set<String> codes = JwtRoles.visibleProjectCodes(JwtRoles.currentJwt());
        if (codes.contains("*")) {
            return true;
        }
        return record.getProject() != null && codes.contains(record.getProject().getCode());
    }

    private String defaultStatut(String module) {
        return switch (module) {
            case "COURIER" -> "EN_VALIDATION";
            case "LEAVE", "BUG" -> "EN_ATTENTE";
            case "COURSE" -> "PLANIFIEE";
            default -> "ACTIF";
        };
    }

    private boolean matchesProject(OpsRecord record, String projectCode) {
        if (projectCode == null || projectCode.isBlank() || "ENTREPRISE".equalsIgnoreCase(projectCode)) {
            return true;
        }
        return record.getProject() != null && projectCode.equalsIgnoreCase(record.getProject().getCode());
    }

    private LocalDate localDate(OpsRecord record) {
        Instant at = record.getCreatedAt() == null ? Instant.now() : record.getCreatedAt();
        return LocalDate.ofInstant(at, ZoneId.of("Africa/Kinshasa"));
    }

    private Map<String, Object> toMap(OpsRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.getId());
        row.put("module", record.getModule());
        row.put("titre", record.getTitre());
        row.put("description", record.getDescription());
        row.put("statut", record.getStatut());
        row.put("priorite", record.getPriorite());
        row.put("zone", record.getZone());
        row.put("canal", record.getCanal());
        row.put("montant", record.getMontant());
        row.put("motif", record.getMotif());
        row.put("projectCode", record.getProject() == null ? null : record.getProject().getCode());
        row.put("createdAt", record.getCreatedAt());
        return row;
    }

    public record Upsert(String titre, String description, String statut, String priorite, String zone,
                         String canal, BigDecimal montant, String projectCode) {}
}
