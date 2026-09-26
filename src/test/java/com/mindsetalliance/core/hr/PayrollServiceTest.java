package com.mindsetalliance.core.hr;

import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock DossierPersonnelRepository dossierRepository;
    @Mock PresenceRepository presenceRepository;
    @Mock BulletinPaieRepository bulletinRepository;
    @Mock AgentRepository agentRepository;

    PayrollService payrollService;

    @BeforeEach
    void setUp() {
        payrollService = new PayrollService(dossierRepository, presenceRepository, bulletinRepository, agentRepository);
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("agent-id-1")
                .claim("roles", List.of(Map.of("projectCode", "CNN", "role", "RH")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void calculTraceChaqueElement() {
        Agent agent = new Agent();
        agent.setNom("Ilunga");
        agent.setPrenom("Patience");
        DossierPersonnel dossier = new DossierPersonnel();
        dossier.setAgent(agent);
        dossier.setSalaireBase(new BigDecimal("1000.00"));
        dossier.setDevise("USD");

        when(bulletinRepository.findByAgentIdAndPeriodeAnneeAndPeriodeMois(1L, 2026, 9)).thenReturn(Optional.empty());
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(dossierRepository.findByAgentId(1L)).thenReturn(Optional.of(dossier));
        when(presenceRepository.findByAgentAndJourBetween(any(), any(), any())).thenReturn(List.of());
        when(bulletinRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BulletinPaie bulletin = payrollService.calculer(1L, 2026, 9,
                List.of(new PayrollService.LigneSaisie("Prime transport", new BigDecimal("50"))),
                List.of(new PayrollService.LigneSaisie("Retenue CNSS", new BigDecimal("30"))));

        assertEquals(4, bulletin.getElements().size());
        assertEquals(0, new BigDecimal("1050.00").compareTo(bulletin.getSalaireBrut()));
        assertEquals(0, new BigDecimal("1020.00").compareTo(bulletin.getNetAPayer()));
    }
}
