package com.mindsetalliance.core.hr;

import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

@Service
public class PayrollService {

    public static final Set<String> ROLES_REMUNERATION = Set.of("RH", "FINANCE", "DIRECTION");

    private final DossierPersonnelRepository dossierRepository;
    private final PresenceRepository presenceRepository;
    private final BulletinPaieRepository bulletinRepository;
    private final AgentRepository agentRepository;

    public PayrollService(DossierPersonnelRepository dossierRepository,
                          PresenceRepository presenceRepository,
                          BulletinPaieRepository bulletinRepository,
                          AgentRepository agentRepository) {
        this.dossierRepository = dossierRepository;
        this.presenceRepository = presenceRepository;
        this.bulletinRepository = bulletinRepository;
        this.agentRepository = agentRepository;
    }

    public void assertCanViewRemuneration() {
        Jwt jwt = JwtRoles.currentJwt();
        if (!JwtRoles.hasAnyRole(jwt, ROLES_REMUNERATION)) {
            throw new AccessDeniedException("Les données de rémunération sont réservées aux rôles RH, FINANCE et DIRECTION");
        }
    }

    @Transactional
    public BulletinPaie calculer(Long agentId, int annee, int mois, List<LigneSaisie> primes, List<LigneSaisie> retenues) {
        assertCanViewRemuneration();
        bulletinRepository.findByAgentIdAndPeriodeAnneeAndPeriodeMois(agentId, annee, mois)
                .ifPresent(existing -> {
                    throw new BusinessException("Un bulletin existe déjà pour cette période");
                });
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        DossierPersonnel dossier = dossierRepository.findByAgentId(agentId)
                .orElseThrow(() -> new BusinessException("Dossier RH introuvable pour cet agent"));
        YearMonth ym = YearMonth.of(annee, mois);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<Presence> presences = presenceRepository.findByAgentAndJourBetween(agent, start, end);

        BigDecimal heuresAttendues = BigDecimal.valueOf(ym.lengthOfMonth() >= 28 ? 160 : 160);
        BigDecimal heuresTravaillees = presences.stream()
                .filter(p -> "PRESENT".equals(p.getType()) || "RETARD".equals(p.getType()))
                .map(p -> p.getHeures() == null ? BigDecimal.ZERO : p.getHeures())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (heuresTravaillees.compareTo(BigDecimal.ZERO) == 0) {
            heuresTravaillees = heuresAttendues;
        }

        BigDecimal salaireBase = dossier.getSalaireBase() == null ? BigDecimal.ZERO : dossier.getSalaireBase();
        BigDecimal salairePresence = salaireBase.multiply(heuresTravaillees)
                .divide(heuresAttendues, 2, RoundingMode.HALF_UP);

        BulletinPaie bulletin = new BulletinPaie();
        bulletin.setAgent(agent);
        bulletin.setPeriodeAnnee(annee);
        bulletin.setPeriodeMois(mois);
        bulletin.setDevise(dossier.getDevise() == null ? "USD" : dossier.getDevise());
        bulletin.setStatut("CALCULE");

        addElement(bulletin, "SALAIRE_BASE", "Salaire de base théorique", salaireBase);
        addElement(bulletin, "PRESENCE", "Salaire au prorata des heures (" + heuresTravaillees + "/" + heuresAttendues + ")", salairePresence);

        BigDecimal totalPrimes = BigDecimal.ZERO;
        if (primes != null) {
            for (LigneSaisie prime : primes) {
                addElement(bulletin, "PRIME", prime.libelle(), prime.montant());
                totalPrimes = totalPrimes.add(prime.montant());
            }
        }
        BigDecimal totalRetenues = BigDecimal.ZERO;
        if (retenues != null) {
            for (LigneSaisie retenue : retenues) {
                addElement(bulletin, "RETENUE", retenue.libelle(), retenue.montant().negate());
                totalRetenues = totalRetenues.add(retenue.montant());
            }
        }

        BigDecimal brut = salairePresence.add(totalPrimes);
        BigDecimal net = brut.subtract(totalRetenues);
        bulletin.setSalaireBrut(brut);
        bulletin.setNetAPayer(net);
        return bulletinRepository.save(bulletin);
    }

    public BulletinPaie get(Long id) {
        assertCanViewRemuneration();
        return bulletinRepository.findById(id).orElseThrow(() -> new BusinessException("Bulletin introuvable", 404));
    }

    public DossierPersonnel getDossier(Long agentId) {
        assertCanViewRemuneration();
        return dossierRepository.findByAgentId(agentId)
                .orElseThrow(() -> new BusinessException("Dossier introuvable", 404));
    }

    @Transactional
    public DossierPersonnel upsertDossier(Long agentId, DossierPersonnel payload) {
        assertCanViewRemuneration();
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> new BusinessException("Agent introuvable", 404));
        DossierPersonnel dossier = dossierRepository.findByAgentId(agentId).orElseGet(DossierPersonnel::new);
        dossier.setAgent(agent);
        dossier.setNumeroIdentite(payload.getNumeroIdentite());
        dossier.setCoordonneesBancaires(payload.getCoordonneesBancaires());
        dossier.setDateEmbauche(payload.getDateEmbauche());
        dossier.setSalaireBase(payload.getSalaireBase());
        dossier.setDevise(payload.getDevise() == null ? "USD" : payload.getDevise());
        return dossierRepository.save(dossier);
    }

    public byte[] bulletinPdf(Long id) {
        BulletinPaie bulletin = get(id);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                cs.newLineAtOffset(50, 750);
                cs.showText("Bulletin de paie — Mindset Alliance");
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(0, -24);
                cs.showText("Agent : " + bulletin.getAgent().getPrenom() + " " + bulletin.getAgent().getNom());
                cs.newLineAtOffset(0, -18);
                cs.showText("Période : " + bulletin.getPeriodeMois() + "/" + bulletin.getPeriodeAnnee());
                cs.newLineAtOffset(0, -18);
                for (ElementPaie element : bulletin.getElements()) {
                    cs.showText(element.getType() + " — " + element.getLibelle() + " : " + element.getMontant() + " " + bulletin.getDevise());
                    cs.newLineAtOffset(0, -16);
                }
                cs.showText("Brut : " + bulletin.getSalaireBrut() + "  |  Net à payer : " + bulletin.getNetAPayer() + " " + bulletin.getDevise());
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Impossible de générer le PDF du bulletin");
        }
    }

    private void addElement(BulletinPaie bulletin, String type, String libelle, BigDecimal montant) {
        ElementPaie element = new ElementPaie();
        element.setBulletin(bulletin);
        element.setType(type);
        element.setLibelle(libelle);
        element.setMontant(montant);
        bulletin.getElements().add(element);
    }

    public record LigneSaisie(String libelle, BigDecimal montant) {}
}
