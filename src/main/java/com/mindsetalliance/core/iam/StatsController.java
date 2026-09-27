package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.common.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @RequireRoles({"ADMIN_SYSTEME", "DIRECTION"})
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(required = false) String period) {
        return statsService.snapshot(period);
    }
}
