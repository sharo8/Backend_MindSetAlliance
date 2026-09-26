package com.mindsetalliance.core.documents;

import com.mindsetalliance.core.common.security.JwtRoles;
import com.mindsetalliance.core.common.security.RequireRoles;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.AgentRepository;
import com.mindsetalliance.core.iam.Project;
import com.mindsetalliance.core.iam.ProjectRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "documents")
class DocumentEntreprise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String titre;
    private String categorie;
    private String statut = "ACTIF";
    @Column(name = "motif_archivage")
    private String motifArchivage;
    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;
    private String nomFichier;
    @Lob
    @Column(columnDefinition = "longblob")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private byte[] contenu;
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Agent createdBy;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getMotifArchivage() { return motifArchivage; }
    public void setMotifArchivage(String motifArchivage) { this.motifArchivage = motifArchivage; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }
    public byte[] getContenu() { return contenu; }
    public void setContenu(byte[] contenu) { this.contenu = contenu; }
    public Agent getCreatedBy() { return createdBy; }
    public void setCreatedBy(Agent createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}

interface DocumentRepository extends JpaRepository<DocumentEntreprise, Long> {}

@RestController
@RequestMapping("/api/documents")
class DocumentController {
    private final DocumentRepository repository;
    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;

    DocumentController(DocumentRepository repository, ProjectRepository projectRepository, AgentRepository agentRepository) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.agentRepository = agentRepository;
    }

    @RequireRoles({"DIRECTION", "ADMIN_SYSTEME", "RH", "JURIDIQUE", "FINANCE", "SUPPORT", "DEV", "MARKETING", "COMMERCIAL"})
    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        Set<String> codes = JwtRoles.visibleProjectCodes(JwtRoles.currentJwt());
        return repository.findAll().stream()
                .filter(doc -> {
                    if (codes.contains("*")) {
                        return true;
                    }
                    return doc.getProject() != null && codes.contains(doc.getProject().getCode());
                })
                .map(doc -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", doc.getId());
            row.put("titre", doc.getTitre());
            row.put("categorie", doc.getCategorie());
            row.put("nomFichier", doc.getNomFichier());
            row.put("createdAt", doc.getCreatedAt());
            row.put("projectCode", doc.getProject() == null ? null : doc.getProject().getCode());
            row.put("statut", doc.getStatut());
            return row;
        }).toList();
    }

    @RequireRoles({"DIRECTION", "ADMIN_SYSTEME", "RH", "SUPPORT"})
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam String titre,
                                     @RequestParam(required = false) String categorie,
                                     @RequestParam(required = false) String projectCode,
                                     @RequestParam("fichier") MultipartFile fichier) throws Exception {
        if (titre == null || titre.isBlank()) {
            throw new com.mindsetalliance.core.common.BusinessException("Le titre du document est obligatoire");
        }
        if (fichier == null || fichier.isEmpty()) {
            throw new com.mindsetalliance.core.common.BusinessException("Un fichier est obligatoire");
        }
        DocumentEntreprise doc = new DocumentEntreprise();
        doc.setTitre(titre);
        doc.setCategorie(categorie);
        if (projectCode != null) {
            doc.setProject(projectRepository.findByCode(projectCode).orElse(null));
        }
        doc.setNomFichier(fichier.getOriginalFilename());
        doc.setContenu(fichier.getBytes());
        doc.setCreatedBy(agentRepository.findById(JwtRoles.agentId()).orElse(null));
        DocumentEntreprise saved = repository.save(doc);
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", saved.getId());
        row.put("titre", saved.getTitre());
        row.put("message", "Document enregistré.");
        return row;
    }

    @RequireRoles({"DIRECTION", "ADMIN_SYSTEME", "RH", "SUPPORT"})
    @PostMapping("/{id}/archive")
    public Map<String, Object> archive(@org.springframework.web.bind.annotation.PathVariable Long id,
                                       @org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        String motif = body.get("motif");
        if (motif == null || motif.isBlank()) {
            throw new com.mindsetalliance.core.common.BusinessException("Le motif d'archivage est obligatoire");
        }
        DocumentEntreprise doc = repository.findById(id)
                .orElseThrow(() -> new com.mindsetalliance.core.common.BusinessException("Document introuvable", 404));
        doc.setStatut("ARCHIVE");
        doc.setMotifArchivage(motif);
        repository.save(doc);
        return Map.of("id", id, "statut", "ARCHIVE", "message", "Document archivé.");
    }
}
