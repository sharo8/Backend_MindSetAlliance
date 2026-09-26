package com.mindsetalliance.core.tickets;

import com.mindsetalliance.core.audit.AuditService;
import com.mindsetalliance.core.common.BusinessException;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import com.mindsetalliance.core.iam.AgentRole;
import com.mindsetalliance.core.iam.AgentRoleRepository;
import com.mindsetalliance.core.iam.Departement;
import com.mindsetalliance.core.iam.DepartementRepository;
import com.mindsetalliance.core.iam.Project;
import com.mindsetalliance.core.iam.ProjectRepository;
import com.mindsetalliance.core.notifications.NotificationService;
import com.mindsetalliance.core.common.security.JwtRoles;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class TicketService {

    private static final Set<String> TYPES = Set.of(
            "DEMANDE_TRAVAIL", "INCIDENT", "ANOMALIE_TECHNIQUE",
            "RECLAMATION_CLIENT", "DEMANDE_ADMINISTRATIVE", "DEMANDE_ACHAT", "AUTRE");
    private static final Set<String> PRIORITES = Set.of("CRITIQUE", "ELEVEE", "NORMALE", "FAIBLE");

    private final TicketRepository ticketRepository;
    private final TicketSequenceRepository sequenceRepository;
    private final TicketCommentaireRepository commentaireRepository;
    private final TicketPieceJointeRepository pieceJointeRepository;
    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;
    private final DepartementRepository departementRepository;
    private final AuditService auditService;
    private final TicketReattributionRepository reattributionRepository;
    private final NotificationService notificationService;
    private final TicketSlaProperties slaProperties;
    private final AgentRoleRepository agentRoleRepository;
    private final TicketStatutHistoriqueRepository statutHistoriqueRepository;

    public TicketService(TicketRepository ticketRepository,
                         TicketSequenceRepository sequenceRepository,
                         TicketCommentaireRepository commentaireRepository,
                         TicketPieceJointeRepository pieceJointeRepository,
                         ProjectRepository projectRepository,
                         AgentRepository agentRepository,
                         DepartementRepository departementRepository,
                         AuditService auditService,
                         TicketReattributionRepository reattributionRepository,
                         NotificationService notificationService,
                         TicketSlaProperties slaProperties,
                         AgentRoleRepository agentRoleRepository,
                         TicketStatutHistoriqueRepository statutHistoriqueRepository) {
        this.ticketRepository = ticketRepository;
        this.sequenceRepository = sequenceRepository;
        this.commentaireRepository = commentaireRepository;
        this.pieceJointeRepository = pieceJointeRepository;
        this.projectRepository = projectRepository;
        this.agentRepository = agentRepository;
        this.departementRepository = departementRepository;
        this.auditService = auditService;
        this.reattributionRepository = reattributionRepository;
        this.notificationService = notificationService;
        this.slaProperties = slaProperties;
        this.agentRoleRepository = agentRoleRepository;
        this.statutHistoriqueRepository = statutHistoriqueRepository;
    }

    @Transactional
    public Ticket create(Long demandeurId, TicketCreateRequest request) {
        validateTypeAndPriority(request.type(), request.priorite());
        if (request.departementDestinataireId() == null) {
            throw new BusinessException("Le département destinataire est obligatoire");
        }
        if (request.echeance() == null) {
            throw new BusinessException("L’échéance souhaitée est obligatoire");
        }
        if ("INCIDENT".equals(request.type())) {
            if (request.categorie() == null || !TicketSysteme.SUPPORT_CATEGORIES.contains(request.categorie())) {
                throw new BusinessException("Catégorie d’incident obligatoire pour un ticket de type Incident");
            }
        }
        String systeme = request.systeme() == null || request.systeme().isBlank() ? TicketSysteme.SUPPORT : request.systeme();
        Project project = projectRepository.findByCode(request.projectCode())
                .orElseThrow(() -> new BusinessException("Projet introuvable"));
        Agent demandeur = agentRepository.findById(demandeurId)
                .orElseThrow(() -> new BusinessException("Demandeur introuvable", 404));
        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setDemandeur(demandeur);
        ticket.setType(request.type());
        applyTypePrecision(ticket, request.type(), request.typeAutrePrecision());
        ticket.setSysteme(systeme);
        ticket.setCategorie("INCIDENT".equals(request.type()) ? request.categorie() : request.categorie());
        if (!"INCIDENT".equals(request.type()) && request.categorie() != null
                && TicketSysteme.SUPPORT_CATEGORIES.contains(request.categorie())) {
            ticket.setCategorie(null);
        }
        if (request.sourceExterne() != null && !request.sourceExterne().isBlank()) {
            ticket.setSourceExterne(request.sourceExterne());
        }
        if (request.sourceReference() != null && !request.sourceReference().isBlank()) {
            ticket.setSourceReference(request.sourceReference());
        }
        ticket.setPriorite(request.priorite());
        ticket.setTitre(request.titre());
        ticket.setDescription(request.description());
        ticket.setEcheance(request.echeance());
        ticket.setObjetLieType(request.objetLieType());
        ticket.setObjetLieId(request.objetLieId());
        ticket.setObjetLieLibelle(request.objetLieLibelle());
        ticket.setStatut(TicketStatut.OUVERT.name());
        ticket.setDepartementDestinataire(departementRepository.findById(request.departementDestinataireId())
                .orElseThrow(() -> new BusinessException("Département introuvable")));
        ticket.setReference(nextReference(project));
        ticketRepository.save(ticket);
        recordStatut(ticket, null, TicketStatut.OUVERT.name(), demandeur);
        auditService.record(demandeurId, "CREATE", "TICKET", ticket.getId(), null, Map.of("reference", ticket.getReference()));
        notifyParties(ticket, "Nouveau ticket " + ticket.getReference(), "Un ticket a été ouvert : " + ticket.getTitre());
        return ticket;
    }

    @Transactional
    public Ticket ingestFromColisNaNga(CnnIncidentIngestController.CnnIncidentRequest request) {
        validateCategorie(TicketSysteme.SUPPORT, request.categorie());
        String sourceRef = request.referenceExterne();
        if (sourceRef == null || sourceRef.isBlank()) {
            sourceRef = "CNN-" + (request.courseId() == null ? "INC" : request.courseId()) + "-" + request.categorie();
        }
        Optional<Ticket> existing = ticketRepository.findBySourceReference(sourceRef);
        if (existing.isPresent()) {
            Ticket ticket = existing.get();
            ticket.setTitre(request.titre());
            ticket.setDescription(request.description());
            ticket.setCategorie(request.categorie());
            if (request.priorite() != null && !request.priorite().isBlank()) {
                ticket.setPriorite(request.priorite());
            }
            ticket.setUpdatedAt(Instant.now());
            return ticket;
        }
        String projectCode = request.projectCode() == null || request.projectCode().isBlank() ? "CNN" : request.projectCode();
        Long systemAgentId = agentRepository.findAll().stream()
                .filter(agent -> "support.cnn@mindsetalliance.cd".equalsIgnoreCase(agent.getEmailPro()))
                .map(Agent::getId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Aucun agent Support pour rattacher l’incident CNN", 500));
        Agent support = agentRepository.findById(systemAgentId).orElseThrow();
        support = hydrateAgent(support);
        Long deptId = support.getDepartement() != null ? support.getDepartement().getId() : defaultDepartementId();
        String priorite = request.priorite() == null || request.priorite().isBlank() ? "NORMALE" : request.priorite();
        Instant echeance = Instant.now().plus(Duration.ofHours(slaProperties.heuresPour(priorite)));
        return create(systemAgentId, new TicketCreateRequest(
                projectCode,
                deptId,
                "INCIDENT",
                priorite,
                request.titre(),
                request.description(),
                echeance,
                null,
                TicketSysteme.SUPPORT,
                request.categorie(),
                "COLIS_NA_NGA",
                sourceRef,
                "COURSE",
                request.courseId() == null ? null : String.valueOf(request.courseId()),
                request.courseId() == null ? null : "Course " + request.courseId(),
                null
        ));
    }

    @Transactional
    public Ticket createFromIncident(TicketCreateRequest request, Long systemAgentId) {
        Long demandeur = systemAgentId != null ? systemAgentId : agentRepository.findAll().stream()
                .findFirst()
                .map(Agent::getId)
                .orElseThrow(() -> new BusinessException("Aucun agent pour rattacher le ticket"));
        if (request.referenceExistante() != null) {
            return ticketRepository.findByReference(request.referenceExistante()).orElseGet(() -> create(demandeur, request));
        }
        String systeme = request.systeme() == null || request.systeme().isBlank() ? TicketSysteme.SUPPORT : request.systeme();
        String categorie = request.categorie() == null || request.categorie().isBlank()
                ? TicketSysteme.categoriesOf(systeme).iterator().next()
                : request.categorie();
        Long deptId = request.departementDestinataireId() != null ? request.departementDestinataireId() : defaultDepartementId();
        Instant echeance = request.echeance() != null
                ? request.echeance()
                : Instant.now().plus(Duration.ofHours(slaProperties.heuresPour(request.priorite())));
        return create(demandeur, new TicketCreateRequest(
                request.projectCode(),
                deptId,
                request.type() == null ? "INCIDENT" : request.type(),
                request.priorite(),
                request.titre(),
                request.description(),
                echeance,
                request.referenceExistante(),
                systeme,
                categorie,
                request.sourceExterne(),
                request.sourceReference(),
                request.objetLieType(),
                request.objetLieId(),
                request.objetLieLibelle(),
                request.typeAutrePrecision()
        ));
    }

    @Transactional
    public Ticket changeStatut(Long ticketId, String cible, String motif, Long agentId) {
        Ticket ticket = get(ticketId);
        TicketStatut from = TicketStatut.from(ticket.getStatut());
        TicketStatut to = TicketStatut.from(cible);
        if (from == TicketStatut.CLOTURE || from == TicketStatut.ANNULE) {
            if (to != TicketStatut.OUVERT && to != TicketStatut.EN_COURS) {
                throw new BusinessException("Réouverture uniquement vers Ouvert ou En cours");
            }
            if (!isResponsable()) {
                throw new BusinessException("Seul un responsable peut rouvrir un ticket clôturé ou annulé", 403);
            }
            Instant closedAt = ticket.getUpdatedAt() == null ? ticket.getCreatedAt() : ticket.getUpdatedAt();
            if (Duration.between(closedAt, Instant.now()).toDays() > slaProperties.getReouvertureJours()) {
                throw new BusinessException("Délai de réouverture dépassé (" + slaProperties.getReouvertureJours() + " jours)");
            }
        } else {
            from.assertTransition(to);
        }
        if (to == TicketStatut.ANNULE && (motif == null || motif.isBlank())) {
            throw new BusinessException("Le motif d'annulation est obligatoire");
        }
        Instant now = Instant.now();
        if ((to == TicketStatut.EN_COURS || to == TicketStatut.EN_ATTENTE) && ticket.getPremierePriseEnChargeAt() == null) {
            ticket.setPremierePriseEnChargeAt(now);
        }
        if (to == TicketStatut.RESOLU) {
            ticket.setResoluAt(now);
        }
        String avant = ticket.getStatut();
        ticket.setStatut(to.name());
        ticket.setMotifAnnulation(motif);
        ticket.setUpdatedAt(now);
        recordStatut(ticket, avant, to.name(), agentRepository.findById(agentId).orElse(null));
        auditService.record(agentId, "CHANGE_STATUS", "TICKET", ticketId, Map.of("statut", avant), Map.of("statut", to.name(), "motif", motif == null ? "" : motif));
        notifyWorkflow(ticket, "Statut " + ticket.getReference(), "Le ticket est passé à " + to.name());
        return ticket;
    }

    @Transactional
    public Ticket update(Long ticketId, TicketCreateRequest request, Long agentId) {
        Ticket ticket = get(ticketId);
        TicketStatut current = TicketStatut.from(ticket.getStatut());
        boolean locked = current == TicketStatut.CLOTURE || current == TicketStatut.ANNULE;
        if (locked && !isResponsable()) {
            throw new BusinessException("Ticket non modifiable à l’état " + current, 403);
        }
        if (!locked) {
            assertModifiable(ticket);
            validateTypeAndPriority(request.type() == null ? ticket.getType() : request.type(),
                    request.priorite() == null ? ticket.getPriorite() : request.priorite());
            if (request.titre() != null) ticket.setTitre(request.titre());
            if (request.description() != null) ticket.setDescription(request.description());
            if (request.priorite() != null) ticket.setPriorite(request.priorite());
            if (request.type() != null) {
                ticket.setType(request.type());
                applyTypePrecision(ticket, request.type(), request.typeAutrePrecision());
            }
            if (request.echeance() != null) ticket.setEcheance(request.echeance());
            if (request.departementDestinataireId() != null) {
                ticket.setDepartementDestinataire(departementRepository.findById(request.departementDestinataireId())
                        .orElseThrow(() -> new BusinessException("Département introuvable")));
            }
            if (request.projectCode() != null) {
                ticket.setProject(projectRepository.findByCode(request.projectCode())
                        .orElseThrow(() -> new BusinessException("Projet introuvable")));
            }
            if ("INCIDENT".equals(ticket.getType())) {
                if (request.categorie() != null) ticket.setCategorie(request.categorie());
            } else if (request.categorie() != null && TicketSysteme.SUPPORT_CATEGORIES.contains(request.categorie())) {
                ticket.setCategorie(null);
            }
            ticket.setObjetLieType(request.objetLieType());
            ticket.setObjetLieId(request.objetLieId());
            ticket.setObjetLieLibelle(request.objetLieLibelle());
            ticket.setUpdatedAt(Instant.now());
        }
        return ticket;
    }

    @Transactional
    public Ticket archiver(Long ticketId, String motif, Long agentId) {
        if (!isResponsable()) {
            throw new BusinessException("Seul un responsable peut retirer un ticket de la file", 403);
        }
        Ticket ticket = get(ticketId);
        if (ticket.isArchive()) {
            return ticket;
        }
        String reason = motif == null || motif.isBlank() ? "Retrait de la file demandé par un responsable" : motif;
        TicketStatut current = TicketStatut.from(ticket.getStatut());
        if (current != TicketStatut.ANNULE && current != TicketStatut.CLOTURE) {
            try {
                changeStatut(ticketId, TicketStatut.ANNULE.name(), reason, agentId);
            } catch (BusinessException ignored) {
                ticket.setMotifAnnulation(reason);
            }
        } else if (ticket.getMotifAnnulation() == null || ticket.getMotifAnnulation().isBlank()) {
            ticket.setMotifAnnulation(reason);
        }
        ticket = get(ticketId);
        ticket.setArchive(true);
        ticket.setUpdatedAt(Instant.now());
        auditService.record(agentId, "ARCHIVE", "TICKET", ticketId, null, Map.of("reference", ticket.getReference()));
        return ticket;
    }

    @Transactional(readOnly = true)
    public List<TicketStatutHistorique> historique(Long ticketId) {
        get(ticketId);
        List<TicketStatutHistorique> rows = statutHistoriqueRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        rows.forEach(row -> row.setAgent(hydrateAgent(row.getAgent())));
        return rows;
    }

    @Transactional
    public Ticket attribuer(Long ticketId, Long attributaireId, Long agentId) {
        Ticket ticket = get(ticketId);
        assertModifiable(ticket);
        Agent attributaire = agentRepository.findById(attributaireId)
                .orElseThrow(() -> new BusinessException("Attributaire introuvable", 404));
        Agent ancien = ticket.getAttributaire();
        Long avant = ancien == null ? null : ancien.getId();
        ticket.setAttributaire(attributaire);
        ticket.setUpdatedAt(Instant.now());
        if (ticket.getPremierePriseEnChargeAt() == null) {
            ticket.setPremierePriseEnChargeAt(Instant.now());
        }
        TicketReattribution hist = new TicketReattribution();
        hist.setTicket(ticket);
        hist.setAncienAgent(ancien);
        hist.setNouvelAgent(attributaire);
        hist.setAuteur(agentRepository.findById(agentId).orElseThrow(() -> new BusinessException("Auteur introuvable", 404)));
        reattributionRepository.save(hist);
        auditService.record(agentId, "ASSIGN", "TICKET", ticketId, Map.of("attributaire", avant == null ? "" : avant), Map.of("attributaire", attributaireId));
        notifyWorkflow(ticket, "Attribution " + ticket.getReference(), "Ticket attribué à " + attributaire.getPrenom() + " " + attributaire.getNom());
        return ticket;
    }

    @Transactional
    public TicketCommentaire commenter(Long ticketId, Long auteurId, String contenu) {
        Ticket ticket = get(ticketId);
        assertModifiable(ticket);
        Agent auteur = agentRepository.findById(auteurId).orElseThrow(() -> new BusinessException("Auteur introuvable", 404));
        TicketCommentaire commentaire = new TicketCommentaire();
        commentaire.setTicket(ticket);
        commentaire.setAuteur(auteur);
        commentaire.setContenu(contenu);
        ticket.getCommentaires().add(commentaire);
        ticket.setUpdatedAt(Instant.now());
        return commentaireRepository.save(commentaire);
    }

    @Transactional
    public TicketPieceJointe attacher(Long ticketId, MultipartFile file) throws Exception {
        Ticket ticket = get(ticketId);
        assertModifiable(ticket);
        TicketPieceJointe piece = new TicketPieceJointe();
        piece.setTicket(ticket);
        piece.setNomFichier(file.getOriginalFilename());
        piece.setTypeMime(file.getContentType());
        piece.setContenu(file.getBytes());
        ticket.setUpdatedAt(Instant.now());
        return pieceJointeRepository.save(piece);
    }

    @Transactional(readOnly = true)
    public TicketPieceJointe getPiece(Long ticketId, Long pieceId) {
        TicketPieceJointe piece = pieceJointeRepository.findById(pieceId)
                .orElseThrow(() -> new BusinessException("Pièce jointe introuvable", 404));
        if (piece.getTicket() == null || !piece.getTicket().getId().equals(ticketId)) {
            throw new BusinessException("Pièce jointe introuvable", 404);
        }
        return piece;
    }

    @Transactional(readOnly = true)
    public List<TicketReattribution> reattributions(Long ticketId) {
        get(ticketId);
        List<TicketReattribution> rows = reattributionRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
        rows.forEach(row -> {
            row.setAncienAgent(hydrateAgent(row.getAncienAgent()));
            row.setNouvelAgent(hydrateAgent(row.getNouvelAgent()));
            row.setAuteur(hydrateAgent(row.getAuteur()));
        });
        return rows;
    }

    @Transactional
    public List<Ticket> search(String projectCode, Long departementId, Long agentId, String statut, String priorite,
                               LocalDate from, LocalDate to, String systeme, String categorie, String type) {
        Specification<Ticket> spec = (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("project", JoinType.INNER);
                root.fetch("demandeur", JoinType.INNER);
                root.fetch("attributaire", JoinType.LEFT);
                root.fetch("departementDestinataire", JoinType.LEFT);
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("archive")));
            Set<String> visible = JwtRoles.visibleProjectCodes(JwtRoles.currentJwt());
            if (projectCode != null && !projectCode.isBlank() && !"ENTREPRISE".equalsIgnoreCase(projectCode)) {
                JwtRoles.assertCanSeeProject(projectCode);
                predicates.add(cb.equal(root.get("project").get("code"), projectCode));
            } else if (!visible.contains("*")) {
                if (visible.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("project").get("code").in(visible));
                }
            }
            if (departementId != null) {
                predicates.add(cb.equal(root.get("departementDestinataire").get("id"), departementId));
            }
            if (agentId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("demandeur").get("id"), agentId),
                        cb.equal(root.get("attributaire").get("id"), agentId)
                ));
            }
            if (statut != null) {
                predicates.add(cb.equal(root.get("statut"), statut));
            }
            if (priorite != null) {
                predicates.add(cb.equal(root.get("priorite"), priorite));
            }
            if (systeme != null && !systeme.isBlank()) {
                predicates.add(cb.equal(root.get("systeme"), systeme));
            }
            if (categorie != null && !categorie.isBlank()) {
                predicates.add(cb.equal(root.get("categorie"), categorie));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<Ticket> tickets = ticketRepository.findAll(spec);
        tickets.forEach(ticket -> {
            hydrateTicket(ticket);
            maybeSlaAlert(ticket);
        });
        return tickets;
    }

    public byte[] exportExcel(List<Ticket> tickets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Tickets");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Référence");
            header.createCell(1).setCellValue("Projet");
            header.createCell(2).setCellValue("Titre");
            header.createCell(3).setCellValue("Univers");
            header.createCell(4).setCellValue("Catégorie");
            header.createCell(5).setCellValue("Statut");
            header.createCell(6).setCellValue("Priorité");
            header.createCell(7).setCellValue("Type");
            int i = 1;
            for (Ticket ticket : tickets) {
                Row row = sheet.createRow(i++);
                row.createCell(0).setCellValue(ticket.getReference());
                row.createCell(1).setCellValue(ticket.getProject().getCode());
                row.createCell(2).setCellValue(ticket.getTitre());
                row.createCell(3).setCellValue(ticket.getSysteme() == null ? "" : ticket.getSysteme());
                row.createCell(4).setCellValue(ticket.getCategorie() == null ? "" : ticket.getCategorie());
                row.createCell(5).setCellValue(ticket.getStatut());
                row.createCell(6).setCellValue(ticket.getPriorite());
                row.createCell(7).setCellValue(ticket.getType());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Impossible d'exporter les tickets");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDepartements() {
        return departementRepository.findAll().stream()
                .map(dep -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", dep.getId());
                    row.put("code", dep.getCode());
                    row.put("nom", dep.getNom());
                    return row;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAssignableAgents() {
        return agentRepository.findAll().stream()
                .map(this::hydrateAgent)
                .filter(agent -> "ACTIF".equals(agent.getStatut()))
                .map(agent -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", agent.getId());
                    row.put("nom", agent.getNom());
                    row.put("prenom", agent.getPrenom());
                    row.put("emailPro", agent.getEmailPro());
                    if (agent.getDepartement() != null) {
                        row.put("departementId", agent.getDepartement().getId());
                        row.put("departementNom", agent.getDepartement().getNom());
                    }
                    return row;
                })
                .toList();
    }

    @Transactional
    public Ticket get(Long id) {
        Ticket ticket = ticketRepository.findById(id).orElseThrow(() -> new BusinessException("Ticket introuvable", 404));
        hydrateTicket(ticket);
        ticket.getCommentaires().forEach(comment -> comment.setAuteur(hydrateAgent(comment.getAuteur())));
        Hibernate.initialize(ticket.getPiecesJointes());
        List<TicketStatutHistorique> hist = statutHistoriqueRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        hist.forEach(row -> row.setAgent(hydrateAgent(row.getAgent())));
        ticket.setHistorique(hist);
        maybeSlaAlert(ticket);
        return ticket;
    }

    private void hydrateTicket(Ticket ticket) {
        if (ticket.getProject() != null) {
            ticket.setProject(Hibernate.unproxy(ticket.getProject(), Project.class));
        }
        if (ticket.getDepartementDestinataire() != null) {
            ticket.setDepartementDestinataire(Hibernate.unproxy(ticket.getDepartementDestinataire(), Departement.class));
        }
        ticket.setDemandeur(hydrateAgent(ticket.getDemandeur()));
        ticket.setAttributaire(hydrateAgent(ticket.getAttributaire()));
        ticket.setSla(computeSla(ticket));
    }

    private Agent hydrateAgent(Agent agent) {
        if (agent == null) {
            return null;
        }
        Agent real = Hibernate.unproxy(agent, Agent.class);
        if (real.getDepartement() != null) {
            real.setDepartement(Hibernate.unproxy(real.getDepartement(), Departement.class));
        }
        return real;
    }

    private String nextReference(Project project) {
        TicketSequence sequence = sequenceRepository.findByProjectId(project.getId()).orElseGet(() -> {
            TicketSequence created = new TicketSequence();
            created.setProject(project);
            created.setLastValue(0);
            return sequenceRepository.save(created);
        });
        sequence.setLastValue(sequence.getLastValue() + 1);
        return project.getCode() + "-" + String.format("%04d", sequence.getLastValue());
    }

    private void validateCategorie(String systeme, String categorie) {
        if (!TicketSysteme.SUPPORT.equals(systeme) && !TicketSysteme.TECHNIQUE.equals(systeme)) {
            throw new BusinessException("Univers de ticket invalide");
        }
        if (categorie == null || !TicketSysteme.categoriesOf(systeme).contains(categorie)) {
            throw new BusinessException("Catégorie invalide pour cet univers de tickets");
        }
    }

    private Map<String, Object> computeSla(Ticket ticket) {
        Instant now = Instant.now();
        Instant created = ticket.getCreatedAt() == null ? now : ticket.getCreatedAt();
        int heures = slaProperties.heuresPour(ticket.getPriorite());
        Instant engagement = created.plus(Duration.ofHours(heures));
        Instant pecEnd = ticket.getPremierePriseEnChargeAt() == null ? now : ticket.getPremierePriseEnChargeAt();
        Instant resEnd = ticket.getResoluAt() == null ? now : ticket.getResoluAt();
        boolean terminal = TicketStatut.CLOTURE.name().equals(ticket.getStatut())
                || TicketStatut.ANNULE.name().equals(ticket.getStatut());
        boolean respecte = !now.isAfter(engagement) || ticket.getResoluAt() != null && !ticket.getResoluAt().isAfter(engagement);
        boolean echeanceDepassee = ticket.getEcheance() != null && now.isAfter(ticket.getEcheance()) && !terminal;
        Map<String, Object> sla = new LinkedHashMap<>();
        sla.put("engagementHeures", heures);
        sla.put("priseEnChargeMinutes", Duration.between(created, pecEnd).toMinutes());
        sla.put("resolutionMinutes", Duration.between(created, resEnd).toMinutes());
        sla.put("respecte", respecte && !echeanceDepassee);
        sla.put("depassement", now.isAfter(engagement) && ticket.getResoluAt() == null && !terminal);
        sla.put("echeanceDepassee", echeanceDepassee);
        sla.put("reouvertureJours", slaProperties.getReouvertureJours());
        return sla;
    }

    private void maybeSlaAlert(Ticket ticket) {
        Map<String, Object> sla = ticket.getSla();
        if (sla == null) {
            return;
        }
        boolean overdue = Boolean.TRUE.equals(sla.get("echeanceDepassee")) || Boolean.TRUE.equals(sla.get("depassement"));
        if (!overdue || ticket.isSlaAlerteEnvoyee()) {
            return;
        }
        notifyWorkflow(ticket, "SLA " + ticket.getReference(), "Engagement ou échéance dépassé pour « " + ticket.getTitre() + " »");
        ticket.setSlaAlerteEnvoyee(true);
    }

    private void notifyWorkflow(Ticket ticket, String titre, String message) {
        Set<Long> seen = new HashSet<>();
        if (ticket.getDemandeur() != null) {
            seen.add(ticket.getDemandeur().getId());
            notificationService.send(ticket.getDemandeur().getId(), "IN_APP", titre, message);
        }
        if (ticket.getAttributaire() != null && seen.add(ticket.getAttributaire().getId())) {
            notificationService.send(ticket.getAttributaire().getId(), "IN_APP", titre, message);
        }
        Long responsableId = findResponsableId(ticket);
        if (responsableId != null && seen.add(responsableId)) {
            notificationService.send(responsableId, "IN_APP", titre, message);
        }
    }

    private Long findResponsableId(Ticket ticket) {
        Long deptId = ticket.getDepartementDestinataire() == null ? null : ticket.getDepartementDestinataire().getId();
        return agentRoleRepository.findAll().stream()
                .filter(AgentRole::isActive)
                .filter(ar -> ar.getRole() != null && ar.getAgent() != null)
                .filter(ar -> {
                    String nom = ar.getRole().getNom();
                    return "DIRECTION".equalsIgnoreCase(nom) || "ADMIN_SYSTEME".equalsIgnoreCase(nom);
                })
                .map(ar -> hydrateAgent(ar.getAgent()))
                .filter(agent -> deptId == null || agent.getDepartement() != null && deptId.equals(agent.getDepartement().getId()))
                .map(Agent::getId)
                .findFirst()
                .orElseGet(() -> {
                    if (deptId == null) {
                        return null;
                    }
                    return agentRepository.findAll().stream()
                            .filter(agent -> "ACTIF".equals(agent.getStatut()))
                            .map(this::hydrateAgent)
                            .filter(agent -> agent.getDepartement() != null && deptId.equals(agent.getDepartement().getId()))
                            .map(Agent::getId)
                            .findFirst()
                            .orElse(null);
                });
    }

    private void notifyParties(Ticket ticket, String titre, String message) {
        notifyWorkflow(ticket, titre, message);
    }

    private void assertModifiable(Ticket ticket) {
        TicketStatut statut = TicketStatut.from(ticket.getStatut());
        if (statut == TicketStatut.CLOTURE || statut == TicketStatut.ANNULE) {
            throw new BusinessException("Ticket non modifiable à l’état " + statut);
        }
    }

    private boolean isResponsable() {
        try {
            return JwtRoles.hasFullAccess(JwtRoles.currentJwt());
        } catch (Exception e) {
            return false;
        }
    }

    private Long defaultDepartementId() {
        return departementRepository.findAll().stream()
                .map(Departement::getId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Aucun département configuré", 500));
    }

    private void applyTypePrecision(Ticket ticket, String type, String precision) {
        if ("AUTRE".equals(type)) {
            if (precision == null || precision.isBlank()) {
                throw new BusinessException("Précisez le type lorsque « Autre » est sélectionné");
            }
            ticket.setTypeAutrePrecision(precision.trim());
        } else {
            ticket.setTypeAutrePrecision(null);
        }
    }

    private void recordStatut(Ticket ticket, String avant, String apres, Agent agent) {
        TicketStatutHistorique row = new TicketStatutHistorique();
        row.setTicket(ticket);
        row.setStatutAvant(avant);
        row.setStatutApres(apres);
        row.setAgent(agent);
        row.setCreatedAt(Instant.now());
        statutHistoriqueRepository.save(row);
    }

    private void validateTypeAndPriority(String type, String priorite) {
        if (!TYPES.contains(type)) {
            throw new BusinessException("Type de ticket invalide");
        }
        if (!PRIORITES.contains(priorite)) {
            throw new BusinessException("Priorité invalide");
        }
    }

    public record TicketCreateRequest(String projectCode, Long departementDestinataireId, String type, String priorite,
                                      String titre, String description, Instant echeance, String referenceExistante,
                                      String systeme, String categorie, String sourceExterne, String sourceReference,
                                      String objetLieType, String objetLieId, String objetLieLibelle, String typeAutrePrecision) {}
}
