package com.mindsetalliance.core.vitrine;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class HomeDashboardController {

    private final HomeDashboardService service;

    public HomeDashboardController(HomeDashboardService service) {
        this.service = service;
    }

    @GetMapping("/accueil")
    public Map<String, Object> accueil(@RequestParam(defaultValue = "7j") String periode,
                                       @RequestParam(required = false) String vue,
                                       @RequestParam(required = false) String projectCode) {
        return service.accueil(periode, vue, projectCode);
    }
}
