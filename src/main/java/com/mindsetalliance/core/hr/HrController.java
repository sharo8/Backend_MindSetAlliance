package com.mindsetalliance.core.hr;

import com.mindsetalliance.core.common.security.RequireRoles;
import com.mindsetalliance.core.iam.Agent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/hr")
public class HrController {

    private final PayrollService payrollService;
    private final PresenceRepository presenceRepository;
    private final com.mindsetalliance.core.iam.AgentRepository agentRepository;

    public HrController(PayrollService payrollService, PresenceRepository presenceRepository,
                        com.mindsetalliance.core.iam.AgentRepository agentRepository) {
        this.payrollService = payrollService;
        this.presenceRepository = presenceRepository;
        this.agentRepository = agentRepository;
    }

    @RequireRoles({"RH", "FINANCE", "DIRECTION"})
    @GetMapping("/dossiers/{agentId}")
    public DossierPersonnel dossier(@PathVariable Long agentId) {
        return payrollService.getDossier(agentId);
    }

    @RequireRoles({"RH", "DIRECTION"})
    @PutMapping("/dossiers/{agentId}")
    public DossierPersonnel upsert(@PathVariable Long agentId, @RequestBody DossierPersonnel dossier) {
        return payrollService.upsertDossier(agentId, dossier);
    }

    @RequireRoles({"RH", "FINANCE", "DIRECTION"})
    @PostMapping("/paie/calculer")
    public BulletinPaie calculer(@RequestBody CalculPaieRequest request) {
        return payrollService.calculer(request.agentId(), request.annee(), request.mois(), request.primes(), request.retenues());
    }

    @RequireRoles({"RH", "FINANCE", "DIRECTION"})
    @GetMapping("/paie/{id}")
    public BulletinPaie bulletin(@PathVariable Long id) {
        return payrollService.get(id);
    }

    @RequireRoles({"RH", "FINANCE", "DIRECTION"})
    @GetMapping("/paie/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = payrollService.bulletinPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bulletin-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @RequireRoles({"RH", "DIRECTION"})
    @PostMapping("/presences")
    public Presence presence(@RequestBody PresenceRequest request) {
        Agent agent = agentRepository.findById(request.agentId()).orElseThrow();
        Presence presence = new Presence();
        presence.setAgent(agent);
        presence.setJour(request.jour());
        presence.setType(request.type());
        presence.setHeures(request.heures());
        return presenceRepository.save(presence);
    }

    public record CalculPaieRequest(Long agentId, int annee, int mois,
                                    List<PayrollService.LigneSaisie> primes,
                                    List<PayrollService.LigneSaisie> retenues) {}
    public record PresenceRequest(Long agentId, LocalDate jour, String type, BigDecimal heures) {}
}
