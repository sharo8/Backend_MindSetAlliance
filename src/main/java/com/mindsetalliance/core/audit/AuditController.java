package com.mindsetalliance.core.audit;

import com.mindsetalliance.core.common.security.RequireRoles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<Map<String, Object>> list(Pageable pageable) {
        return auditService.list(pageable).map(log -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("action", log.getAction());
            row.put("objetType", log.getObjetType());
            row.put("objetId", log.getObjetId());
            row.put("createdAt", log.getCreatedAt());
            if (log.getAgent() != null) {
                row.put("agent", Map.of("emailPro", String.valueOf(log.getAgent().getEmailPro())));
            }
            return row;
        });
    }
}
