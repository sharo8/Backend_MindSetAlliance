package com.mindsetalliance.core.vitrine;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.Project;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/vitrine")
public class VitrineController {

    private final VitrineKpiRepository kpiRepository;

    public VitrineController(VitrineKpiRepository kpiRepository) {
        this.kpiRepository = kpiRepository;
    }

    @GetMapping
    public List<VitrineKpi> vitrine() {
        Set<String> codes = JwtRoles.visibleProjectCodes(JwtRoles.currentJwt());
        if (codes.contains("*")) {
            return kpiRepository.findAll();
        }
        return kpiRepository.findAll().stream()
                .filter(kpi -> {
                    Project project = kpi.getProject();
                    return project != null && codes.contains(project.getCode());
                })
                .toList();
    }
}
