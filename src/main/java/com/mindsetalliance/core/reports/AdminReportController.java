package com.mindsetalliance.core.reports;

import com.mindsetalliance.core.common.security.RequireRoles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/admin/rapports")
public class AdminReportController {

    private final AdminReportService reports;

    public AdminReportController(AdminReportService reports) {
        this.reports = reports;
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/historique")
    public List<Map<String, Object>> historique(@RequestParam(required = false) String lang,
                                                @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        ReportI18n.enter(lang, acceptLanguage);
        try {
            return reports.historique();
        } finally {
            ReportI18n.clear();
        }
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/utilisateurs.pdf")
    public ResponseEntity<byte[]> utilisateurs(@RequestParam(required = false) Long departement,
                                               @RequestParam(required = false) String statut,
                                               @RequestParam(required = false) String lang,
                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return pdf(lang, acceptLanguage, "utilisateurs", () -> reports.utilisateurs(departement, statut));
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/permissions.pdf")
    public ResponseEntity<byte[]> permissions(@RequestParam(required = false) String lang,
                                              @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return pdf(lang, acceptLanguage, "permissions", reports::permissions);
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/acces.pdf")
    public ResponseEntity<byte[]> acces(@RequestParam(required = false) String statut,
                                        @RequestParam(required = false) String lang,
                                        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return pdf(lang, acceptLanguage, "acces", () -> reports.acces(statut));
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/organisation.pdf")
    public ResponseEntity<byte[]> organisation(@RequestParam(required = false) String lang,
                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return pdf(lang, acceptLanguage, "organisation", reports::organisation);
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/statistiques.pdf")
    public ResponseEntity<byte[]> statistiques(@RequestParam(required = false) String period,
                                               @RequestParam(required = false) String lang,
                                               @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return pdf(lang, acceptLanguage, "statistiques", () -> reports.statistiques(period));
    }

    private static ResponseEntity<byte[]> pdf(String lang, String acceptLanguage, String slug, Supplier<byte[]> body) {
        ReportI18n.enter(lang, acceptLanguage);
        try {
            byte[] pdf = body.get();
            String prefix = ReportI18n.english() ? "MA-report-" : "MA-rapport-";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + prefix + slug + "-" + stamp() + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdf.length)
                    .body(pdf);
        } finally {
            ReportI18n.clear();
        }
    }

    private static String stamp() {
        return LocalDate.now(ZoneId.of("Africa/Kinshasa")).toString();
    }
}
