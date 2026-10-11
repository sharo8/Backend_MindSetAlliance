package com.mindsetalliance.core.vitrine;

import com.mindsetalliance.core.cnn.CnnDatabaseReader;
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
    private final CnnDatabaseReader cnnReader;

    public VitrineController(VitrineKpiRepository kpiRepository, CnnDatabaseReader cnnReader) {
        this.kpiRepository = kpiRepository;
        this.cnnReader = cnnReader;
    }

    @GetMapping
    public List<?> vitrine() {
        Set<String> codes = JwtRoles.visibleProjectCodes(JwtRoles.currentJwt());
        List<VitrineKpi> rows = kpiRepository.findAll();
        if (!codes.contains("*")) {
            rows = rows.stream()
                    .filter(kpi -> {
                        Project project = kpi.getProject();
                        return project != null && codes.contains(project.getCode());
                    })
                    .toList();
        }
        return cnnReader.overlayVitrine(rows);
    }
}
