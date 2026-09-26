package com.mindsetalliance.core.vitrine;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.hr.Presence;
import com.mindsetalliance.core.hr.PresenceRepository;
import com.mindsetalliance.core.iam.Project;
import com.mindsetalliance.core.ops.OpsRecord;
import com.mindsetalliance.core.ops.OpsRecordRepository;
import com.mindsetalliance.core.tickets.Ticket;
import com.mindsetalliance.core.tickets.TicketRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HomeDashboardService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Kinshasa");
    private static final List<String> DELAY_BINS = List.of("0-1h", "1-4h", "4-24h", "1-3j", "3j+");
    private static final Map<String, String> STATUS_COLORS = Map.ofEntries(
            Map.entry("OUVERT", "#d97706"),
            Map.entry("PRIS_EN_CHARGE", "#2563eb"),
            Map.entry("EN_COURS", "#2563eb"),
            Map.entry("EN_ATTENTE", "#d97706"),
            Map.entry("RESOLU", "#16a34a"),
            Map.entry("CLOTURE", "#64748b"),
            Map.entry("ANNULE", "#dc2626")
    );
    private static final Map<String, String> STATUS_LABELS = Map.ofEntries(
            Map.entry("OUVERT", "Ouvert"),
            Map.entry("PRIS_EN_CHARGE", "Pris en charge"),
            Map.entry("EN_COURS", "En cours"),
            Map.entry("EN_ATTENTE", "En attente"),
            Map.entry("RESOLU", "Résolu"),
            Map.entry("CLOTURE", "Clôturé"),
            Map.entry("ANNULE", "Annulé")
    );
    private static final Map<String, String> CANAL_LABELS = Map.of(
            "MPESA", "M-Pesa",
            "ORANGE", "Orange Money",
            "AIRTEL", "Airtel Money",
            "VIREMENT", "Virement"
    );
    private static final Map<String, String> CANAL_COLORS = Map.of(
            "MPESA", "#e8b42a",
            "ORANGE", "#f97316",
            "AIRTEL", "#dc2626",
            "VIREMENT", "#16215c"
    );

    private final TicketRepository ticketRepository;
    private final OpsRecordRepository opsRecordRepository;
    private final PresenceRepository presenceRepository;

    public HomeDashboardService(TicketRepository ticketRepository,
                                OpsRecordRepository opsRecordRepository,
                                PresenceRepository presenceRepository) {
        this.ticketRepository = ticketRepository;
        this.opsRecordRepository = opsRecordRepository;
        this.presenceRepository = presenceRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> accueil(String periode, String vueDemandee, String projectCode) {
        Jwt jwt = JwtRoles.currentJwt();
        String vue = resolveVue(jwt, vueDemandee);
        int days = "30j".equalsIgnoreCase(periode) ? 30 : 7;
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(days - 1L);
        Set<String> projects = scopeProjects(JwtRoles.visibleProjectCodes(jwt), projectCode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vue", vue);
        body.put("vuesDisponibles", availableViews(jwt));
        if ("FINANCE".equals(vue)) {
            fillFinance(body, from, today, projects);
        } else if ("RH".equals(vue)) {
            fillRh(body, from, today);
        } else {
            fillTickets(body, from, today, projects);
        }
        return body;
    }

    private String resolveVue(Jwt jwt, String requested) {
        List<String> available = availableViews(jwt);
        if (requested != null && available.contains(requested.toUpperCase(Locale.ROOT))) {
            return requested.toUpperCase(Locale.ROOT);
        }
        return available.getFirst();
    }

    private List<String> availableViews(Jwt jwt) {
        if (JwtRoles.hasFullAccess(jwt)) {
            return List.of("TICKETS", "FINANCE", "RH");
        }
        Set<String> roles = JwtRoles.roleNames(jwt);
        if (roles.contains("FINANCE")) {
            return List.of("FINANCE");
        }
        if (roles.contains("RH")) {
            return List.of("RH");
        }
        return List.of("TICKETS");
    }

    private void fillTickets(Map<String, Object> body, LocalDate from, LocalDate today, Set<String> projects) {
        List<Ticket> tickets = ticketRepository.findAll().stream()
                .filter(t -> inProject(t.getProject(), projects))
                .toList();
        Instant histFrom = today.minusDays(29).atStartOfDay(ZONE).toInstant();
        Map<LocalDate, Long> byDay = tickets.stream()
                .filter(t -> t.getCreatedAt() != null)
                .filter(t -> !localDate(t.getCreatedAt()).isBefore(from) && !localDate(t.getCreatedAt()).isAfter(today))
                .collect(Collectors.groupingBy(t -> localDate(t.getCreatedAt()), Collectors.counting()));
        Map<String, Long> byStatut = tickets.stream()
                .collect(Collectors.groupingBy(t -> t.getStatut() == null ? "OUVERT" : t.getStatut(), Collectors.counting()));
        List<Duration> delays = tickets.stream()
                .filter(t -> t.getCreatedAt() != null && t.getUpdatedAt() != null)
                .filter(t -> t.getUpdatedAt().isAfter(histFrom))
                .filter(t -> Set.of("RESOLU", "CLOTURE").contains(t.getStatut()))
                .map(t -> Duration.between(t.getCreatedAt(), t.getUpdatedAt()))
                .filter(d -> !d.isNegative())
                .toList();
        body.put("titreActivite", "Activité des 7 derniers jours");
        body.put("titreRepartition", "Répartition par statut");
        body.put("titreHistogramme", "Répartition des délais de traitement");
        body.put("uniteActivite", "tickets");
        body.put("activiteParJour", activitySeries(from, today, byDay));
        body.put("repartitionParStatut", statusSlices(byStatut));
        Map<String, Long> bins = binDelays(delays);
        body.put("histogrammeDelais", histogram(bins));
        body.put("syntheseTendance", synthesisTickets(bins));
    }

    private void fillFinance(Map<String, Object> body, LocalDate from, LocalDate today, Set<String> projects) {
        List<OpsRecord> invoices = opsRecordRepository.findByModuleOrderByCreatedAtDesc("INVOICE").stream()
                .filter(r -> inProject(r.getProject(), projects))
                .filter(r -> r.getStatut() == null || !Set.of("ARCHIVE", "ANNULEE").contains(r.getStatut()))
                .toList();
        Map<LocalDate, BigDecimal> byDay = invoices.stream()
                .filter(r -> r.getCreatedAt() != null && r.getMontant() != null)
                .filter(r -> !localDate(r.getCreatedAt()).isBefore(from) && !localDate(r.getCreatedAt()).isAfter(today))
                .collect(Collectors.groupingBy(r -> localDate(r.getCreatedAt()),
                        Collectors.reducing(BigDecimal.ZERO, OpsRecord::getMontant, BigDecimal::add)));
        Map<String, Long> byCanal = invoices.stream()
                .collect(Collectors.groupingBy(r -> r.getCanal() == null ? "VIREMENT" : r.getCanal(), Collectors.counting()));
        Instant histFrom = today.minusDays(29).atStartOfDay(ZONE).toInstant();
        List<Duration> delays = invoices.stream()
                .filter(r -> r.getCreatedAt() != null && r.getUpdatedAt() != null)
                .filter(r -> r.getUpdatedAt().isAfter(histFrom))
                .map(r -> Duration.between(r.getCreatedAt(), r.getUpdatedAt()))
                .filter(d -> !d.isNegative() && !d.isZero())
                .toList();
        body.put("titreActivite", "Recettes encaissées — 7 derniers jours");
        body.put("titreRepartition", "Répartition par canal");
        body.put("titreHistogramme", "Délais de règlement des factures");
        body.put("uniteActivite", "USD");
        body.put("activiteParJour", activitySeriesDecimal(from, today, byDay));
        body.put("repartitionParStatut", canalSlices(byCanal));
        Map<String, Long> bins = binDelays(delays);
        body.put("histogrammeDelais", histogram(bins));
        body.put("syntheseTendance", synthesisFinance(bins));
    }

    private void fillRh(Map<String, Object> body, LocalDate from, LocalDate today) {
        List<Presence> presences = presenceRepository.findAll().stream()
                .filter(p -> p.getJour() != null && !p.getJour().isBefore(from) && !p.getJour().isAfter(today))
                .toList();
        Map<LocalDate, Long> byDay = presences.stream()
                .collect(Collectors.groupingBy(Presence::getJour, Collectors.counting()));
        Map<String, Long> byType = presenceRepository.findAll().stream()
                .collect(Collectors.groupingBy(p -> p.getType() == null ? "PRESENT" : p.getType(), Collectors.counting()));
        body.put("titreActivite", "Présences enregistrées — 7 derniers jours");
        body.put("titreRepartition", "Répartition des présences");
        body.put("titreHistogramme", "Répartition des durées de présence");
        body.put("uniteActivite", "présences");
        body.put("activiteParJour", activitySeries(from, today, byDay));
        List<Map<String, Object>> slices = new ArrayList<>();
        byType.forEach((type, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", type);
            row.put("valeur", count);
            row.put("couleur", "PRESENT".equals(type) ? "#16a34a" : "#d97706");
            slices.add(row);
        });
        body.put("repartitionParStatut", slices);
        Map<String, Long> hours = new LinkedHashMap<>();
        DELAY_BINS.forEach(b -> hours.put(b, 0L));
        presenceRepository.findAll().forEach(p -> {
            double h = p.getHeures() == null ? 0 : p.getHeures().doubleValue();
            String bin = h <= 1 ? "0-1h" : h <= 4 ? "1-4h" : h <= 8 ? "4-24h" : "1-3j";
            hours.merge(bin, 1L, Long::sum);
        });
        body.put("histogrammeDelais", histogram(hours));
        body.put("syntheseTendance", synthesisRh(hours));
    }

    private boolean inProject(Project project, Set<String> codes) {
        if (codes.contains("*")) {
            return true;
        }
        return project != null && codes.contains(project.getCode());
    }

    private Set<String> scopeProjects(Set<String> visible, String projectCode) {
        if (projectCode == null || projectCode.isBlank() || "ENTREPRISE".equalsIgnoreCase(projectCode)) {
            return visible;
        }
        if (visible.contains("*") || visible.contains(projectCode)) {
            return Set.of(projectCode);
        }
        return Set.of();
    }

    private LocalDate localDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    private List<Map<String, Object>> activitySeries(LocalDate from, LocalDate today, Map<LocalDate, Long> byDay) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("jour", weekday(d.getDayOfWeek()));
            row.put("valeur", byDay.getOrDefault(d, 0L));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> activitySeriesDecimal(LocalDate from, LocalDate today, Map<LocalDate, BigDecimal> byDay) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("jour", weekday(d.getDayOfWeek()));
            row.put("valeur", byDay.getOrDefault(d, BigDecimal.ZERO));
            rows.add(row);
        }
        return rows;
    }

    private String weekday(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Lun";
            case TUESDAY -> "Mar";
            case WEDNESDAY -> "Mer";
            case THURSDAY -> "Jeu";
            case FRIDAY -> "Ven";
            case SATURDAY -> "Sam";
            case SUNDAY -> "Dim";
        };
    }

    private List<Map<String, Object>> statusSlices(Map<String, Long> byStatut) {
        return byStatut.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", STATUS_LABELS.getOrDefault(e.getKey(), e.getKey()));
                    row.put("valeur", e.getValue());
                    row.put("couleur", STATUS_COLORS.getOrDefault(e.getKey(), "#64748b"));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> canalSlices(Map<String, Long> byCanal) {
        return byCanal.entrySet().stream()
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", CANAL_LABELS.getOrDefault(e.getKey(), e.getKey()));
                    row.put("valeur", e.getValue());
                    row.put("couleur", CANAL_COLORS.getOrDefault(e.getKey(), "#64748b"));
                    return row;
                })
                .toList();
    }

    private Map<String, Long> binDelays(List<Duration> delays) {
        Map<String, Long> bins = new LinkedHashMap<>();
        DELAY_BINS.forEach(b -> bins.put(b, 0L));
        for (Duration delay : delays) {
            long hours = delay.toHours();
            String bin = hours < 1 ? "0-1h" : hours < 4 ? "1-4h" : hours < 24 ? "4-24h" : hours < 72 ? "1-3j" : "3j+";
            bins.merge(bin, 1L, Long::sum);
        }
        return bins;
    }

    private List<Map<String, Object>> histogram(Map<String, Long> bins) {
        return DELAY_BINS.stream().map(tranche -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tranche", tranche);
            row.put("occurrences", bins.getOrDefault(tranche, 0L));
            return row;
        }).toList();
    }

    private String synthesisTickets(Map<String, Long> bins) {
        String peak = peakBin(bins);
        if (peak == null) {
            return null;
        }
        return switch (peak) {
            case "0-1h" -> "La majorité des tickets sont résolus en moins d’une heure.";
            case "1-4h" -> "La majorité des tickets sont résolus en moins de 4 heures.";
            case "4-24h" -> "La majorité des tickets sont résolus dans la journée.";
            case "1-3j" -> "La majorité des tickets sont résolus en 1 à 3 jours.";
            default -> "La majorité des tickets demandent plus de 3 jours de traitement.";
        };
    }

    private String synthesisFinance(Map<String, Long> bins) {
        String peak = peakBin(bins);
        if (peak == null) {
            return null;
        }
        return switch (peak) {
            case "0-1h", "1-4h" -> "La majorité des factures sont réglées en moins de 4 heures.";
            case "4-24h" -> "La majorité des factures sont réglées dans la journée.";
            default -> "Les délais de règlement se concentrent sur plus d’une journée.";
        };
    }

    private String synthesisRh(Map<String, Long> bins) {
        String peak = peakBin(bins);
        if (peak == null) {
            return null;
        }
        return "La majorité des présences enregistrées correspondent à une journée complète.";
    }

    private String peakBin(Map<String, Long> bins) {
        return bins.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
