package com.mindsetalliance.core.finance;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.common.security.RequireRoles;
import com.mindsetalliance.core.iam.AgentRepository;
import com.mindsetalliance.core.iam.ProjectRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface EcritureRepository extends JpaRepository<EcritureFinanciere, Long> {}
interface BudgetRepository extends JpaRepository<Budget, Long> {}
interface TauxChangeRepository extends JpaRepository<TauxChange, Long> {}

@org.springframework.stereotype.Service
class FinanceService {
    private final EcritureRepository ecritureRepository;
    private final BudgetRepository budgetRepository;
    private final TauxChangeRepository tauxRepository;
    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;
    private final AuditService auditService;

    FinanceService(EcritureRepository ecritureRepository, BudgetRepository budgetRepository,
                   TauxChangeRepository tauxRepository, ProjectRepository projectRepository,
                   AgentRepository agentRepository, AuditService auditService) {
        this.ecritureRepository = ecritureRepository;
        this.budgetRepository = budgetRepository;
        this.tauxRepository = tauxRepository;
        this.projectRepository = projectRepository;
        this.agentRepository = agentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public EcritureFinanciere create(EcritureRequest request, Long agentId) {
        if (!List.of("RECETTE", "DEPENSE").contains(request.type())) {
            throw new BusinessException("Type d'écriture invalide");
        }
        EcritureFinanciere e = new EcritureFinanciere();
        if (request.projectCode() != null) {
            e.setProject(projectRepository.findByCode(request.projectCode())
                    .orElseThrow(() -> new BusinessException("Projet introuvable")));
        }
        e.setType(request.type());
        e.setLibelle(request.libelle());
        e.setMontant(request.montant());
        e.setDevise(request.devise() == null ? "USD" : request.devise());
        e.setStatut("BROUILLON");
        e.setCreatedBy(agentRepository.findById(agentId).orElse(null));
        return ecritureRepository.save(e);
    }

    @Transactional
    public EcritureFinanciere valider(Long id, Long agentId) {
        EcritureFinanciere e = get(id);
        if (!"BROUILLON".equals(e.getStatut())) {
            throw new BusinessException("Seule une écriture brouillon peut être validée");
        }
        e.setStatut("VALIDEE");
        e.setValidatedAt(Instant.now());
        auditService.record(agentId, "VALIDATE", "ECRITURE", id, Map.of("statut", "BROUILLON"), Map.of("statut", "VALIDEE"));
        return e;
    }

    @Transactional
    public EcritureFinanciere annuler(Long id, String motif, Long agentId) {
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif d'annulation est obligatoire");
        }
        EcritureFinanciere e = get(id);
        String avant = e.getStatut();
        e.setStatut("ANNULEE");
        e.setMotifAnnulation(motif);
        auditService.record(agentId, "CANCEL", "ECRITURE", id, Map.of("statut", avant), Map.of("statut", "ANNULEE", "motif", motif));
        return e;
    }

    public void refuseDelete(Long id) {
        EcritureFinanciere e = get(id);
        if ("VALIDEE".equals(e.getStatut())) {
            throw new BusinessException("Suppression physique interdite sur une écriture validée. Utilisez l'annulation avec motif.", 409);
        }
        throw new BusinessException("Aucune suppression physique n'est autorisée. Utilisez l'annulation avec motif.", 409);
    }

    public EcritureFinanciere get(Long id) {
        return ecritureRepository.findById(id).orElseThrow(() -> new BusinessException("Écriture introuvable", 404));
    }

    public List<EcritureFinanciere> list() {
        Set<String> codes = JwtRoles.visibleProjectCodes(JwtRoles.currentJwt());
        if (codes.contains("*")) {
            return ecritureRepository.findAll();
        }
        return ecritureRepository.findAll().stream()
                .filter(e -> e.getProject() != null && codes.contains(e.getProject().getCode()))
                .toList();
    }
    public List<Budget> budgets() { return budgetRepository.findAll(); }
    public List<TauxChange> taux() { return tauxRepository.findAll(); }

    @Transactional
    public TauxChange upsertTaux(TauxChange taux) {
        return tauxRepository.save(taux);
    }

    public record EcritureRequest(String projectCode, String type, String libelle, BigDecimal montant, String devise) {}
}

@RestController
@RequestMapping("/api/finance")
class FinanceController {
    private final FinanceService financeService;

    FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @RequireRoles({"FINANCE", "DIRECTION", "ADMIN_SYSTEME"})
    @GetMapping("/ecritures")
    public List<EcritureFinanciere> list() {
        return financeService.list();
    }

    @RequireRoles({"FINANCE", "DIRECTION"})
    @PostMapping("/ecritures")
    public EcritureFinanciere create(@RequestBody FinanceService.EcritureRequest request) {
        return financeService.create(request, JwtRoles.agentId());
    }

    @RequireRoles({"FINANCE", "DIRECTION"})
    @PostMapping("/ecritures/{id}/valider")
    public EcritureFinanciere valider(@PathVariable Long id) {
        return financeService.valider(id, JwtRoles.agentId());
    }

    @RequireRoles({"FINANCE", "DIRECTION"})
    @PostMapping("/ecritures/{id}/annuler")
    public EcritureFinanciere annuler(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return financeService.annuler(id, body.get("motif"), JwtRoles.agentId());
    }

    @RequireRoles({"FINANCE", "DIRECTION"})
    @DeleteMapping("/ecritures/{id}")
    public void delete(@PathVariable Long id) {
        financeService.refuseDelete(id);
    }

    @RequireRoles({"FINANCE", "DIRECTION", "ADMIN_SYSTEME"})
    @GetMapping("/taux")
    public List<TauxChange> taux() {
        return financeService.taux();
    }

    @RequireRoles({"FINANCE", "DIRECTION"})
    @PostMapping("/taux")
    public TauxChange taux(@RequestBody TauxChange taux) {
        return financeService.upsertTaux(taux);
    }

    @RequireRoles({"FINANCE", "DIRECTION", "ADMIN_SYSTEME"})
    @GetMapping("/budgets")
    public List<Budget> budgets() {
        return financeService.budgets();
    }
}
