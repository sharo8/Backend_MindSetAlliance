package com.mindsetalliance.core.ops;

import com.mindsetalliance.core.common.security.RequirePermissions;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops")
@Validated
public class OpsController {

    private final OpsService service;

    public OpsController(OpsService service) {
        this.service = service;
    }

    @RequirePermissions({"VIEW_FLEET_MAP", "MANAGE_COURSES", "RESOLVE_INCIDENTS", "VALIDATE_COURIER",
            "MANAGE_ENTERPRISE_ACCOUNTS", "MANAGE_SUBSCRIPTIONS", "MANAGE_INVOICING", "VIEW_B2C_VOLUME",
            "MANAGE_STAFF", "MANAGE_INTERNAL_AGENTS", "MANAGE_PARTNER_REGISTRY", "MANAGE_COMPLIANCE",
            "VIEW_MARKETING_PERFORMANCE", "MANAGE_MEDIA_LIBRARY", "VIEW_MONITORING", "MANAGE_BUG_TICKETS",
            "VIEW_PAYROLL", "MANAGE_ACCESS", "VIEW_AUDIT"})
    @GetMapping("/{module}")
    public List<Map<String, Object>> list(@PathVariable String module,
                                          @RequestParam(required = false) String projectCode) {
        return service.list(module, projectCode);
    }

    @RequirePermissions({"VIEW_FLEET_MAP", "MANAGE_COURSES", "RESOLVE_INCIDENTS", "VALIDATE_COURIER",
            "MANAGE_ENTERPRISE_ACCOUNTS", "MANAGE_SUBSCRIPTIONS", "MANAGE_INVOICING", "MANAGE_STAFF",
            "MANAGE_INTERNAL_AGENTS", "MANAGE_PARTNER_REGISTRY", "MANAGE_MEDIA_LIBRARY", "MANAGE_BUG_TICKETS",
            "MANAGE_ACCESS"})
    @PostMapping("/{module}")
    public Map<String, Object> create(@PathVariable String module, @RequestBody @Valid OpsService.Upsert body) {
        return service.create(module, body);
    }

    @RequirePermissions({"VIEW_FLEET_MAP", "MANAGE_COURSES", "RESOLVE_INCIDENTS", "VALIDATE_COURIER",
            "MANAGE_ENTERPRISE_ACCOUNTS", "MANAGE_SUBSCRIPTIONS", "MANAGE_INVOICING", "MANAGE_STAFF",
            "MANAGE_INTERNAL_AGENTS", "MANAGE_PARTNER_REGISTRY", "MANAGE_MEDIA_LIBRARY", "MANAGE_BUG_TICKETS",
            "MANAGE_ACCESS"})
    @PutMapping("/{module}/{id}")
    public Map<String, Object> update(@PathVariable String module, @PathVariable Long id, @RequestBody OpsService.Upsert body) {
        return service.update(module, id, body);
    }

    @RequirePermissions({"VIEW_FLEET_MAP", "MANAGE_COURSES", "RESOLVE_INCIDENTS", "VALIDATE_COURIER",
            "MANAGE_ENTERPRISE_ACCOUNTS", "MANAGE_SUBSCRIPTIONS", "MANAGE_INVOICING", "MANAGE_STAFF",
            "MANAGE_INTERNAL_AGENTS", "MANAGE_PARTNER_REGISTRY", "MANAGE_MEDIA_LIBRARY", "MANAGE_BUG_TICKETS",
            "MANAGE_ACCESS"})
    @PostMapping("/{module}/{id}/archive")
    public Map<String, Object> archive(@PathVariable String module, @PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.archive(module, id, body.get("motif"));
    }

    @RequirePermissions({"VIEW_FLEET_MAP", "MANAGE_COURSES", "RESOLVE_INCIDENTS"})
    @GetMapping("/dashboards/cnn")
    public Map<String, Object> cnn(@RequestParam(required = false) String projectCode) {
        return service.cnnDashboard(projectCode);
    }

    @RequirePermissions({"VIEW_FINANCE", "MANAGE_INVOICING", "MANAGE_SUBSCRIPTIONS", "VIEW_B2C_VOLUME"})
    @GetMapping("/dashboards/finance")
    public Map<String, Object> finance(@RequestParam(required = false) String projectCode) {
        return service.financeDashboard(projectCode);
    }

    @RequirePermissions({"MANAGE_HR", "MANAGE_STAFF", "MANAGE_INTERNAL_AGENTS", "VIEW_PAYROLL"})
    @GetMapping("/dashboards/rh")
    public Map<String, Object> rh(@RequestParam(required = false) String projectCode) {
        return service.rhDashboard(projectCode);
    }
}
