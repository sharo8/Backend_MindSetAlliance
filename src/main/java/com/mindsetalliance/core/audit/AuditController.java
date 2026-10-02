package com.mindsetalliance.core.audit;

import com.mindsetalliance.core.common.security.RequirePermissions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @RequirePermissions({"VIEW_AUDIT"})
    @GetMapping
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(Pageable pageable,
                                          @RequestParam(required = false) Long logId,
                                          @RequestParam(required = false) Long objetId,
                                          @RequestParam(required = false) String objetType) {
        return auditService.list(pageable).map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("action", log.getAction());
            row.put("actionLibelle", actionLibelle(log.getAction()));
            row.put("objetType", log.getObjetType());
            row.put("objetId", log.getObjetId());
            row.put("createdAt", log.getCreatedAt());
            row.put("valeurApres", log.getValeurApres());
            row.put("highlighted", logId != null && logId.equals(log.getId())
                    || (objetId != null && objetId.equals(log.getObjetId())
                    && (objetType == null || objetType.equalsIgnoreCase(log.getObjetType()))));
            if (log.getAgent() != null) {
                Map<String, Object> agent = new LinkedHashMap<>();
                agent.put("emailPro", log.getAgent().getEmailPro());
                agent.put("nom", (log.getAgent().getPrenom() + " " + log.getAgent().getNom()).trim());
                row.put("agent", agent);
            }
            return row;
        });
    }

    static String actionLibelle(String action) {
        if (action == null) {
            return "";
        }
        return switch (action) {
            case "CREATE" -> "Ajout";
            case "UPDATE" -> "Modification";
            case "DELETE" -> "Suppression";
            case "DISABLE" -> "Désactivation";
            case "EXPORT_REPORT" -> "Export de rapport";
            case "ENABLE" -> "Réactivation";
            case "ARCHIVE" -> "Archivage";
            default -> action;
        };
    }
}
