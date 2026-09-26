package com.mindsetalliance.core.tickets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.Departement;
import com.mindsetalliance.core.iam.Project;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tickets")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String reference;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departement_destinataire_id")
    private Departement departementDestinataire;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "demandeur_id")
    private Agent demandeur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attributaire_id")
    private Agent attributaire;

    private String type;
    private String typeAutrePrecision;
    private String systeme = TicketSysteme.SUPPORT;
    private String categorie;
    private String sourceExterne;
    private String sourceReference;
    private String priorite;
    private String statut = TicketStatut.OUVERT.name();
    private String titre;
    private String description;
    private String motifAnnulation;
    private Instant echeance;
    private Instant premierePriseEnChargeAt;
    private Instant resoluAt;
    private boolean slaAlerteEnvoyee;
    private boolean archive;
    private String objetLieType;
    private String objetLieId;
    private String objetLieLibelle;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private List<TicketCommentaire> commentaires = new ArrayList<>();

    @OneToMany(mappedBy = "ticket")
    @JsonIgnoreProperties({"ticket", "contenu", "hibernateLazyInitializer", "handler"})
    private List<TicketPieceJointe> piecesJointes = new ArrayList<>();

    @Transient
    private java.util.Map<String, Object> sla;
    @Transient
    private List<TicketStatutHistorique> historique;

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public Departement getDepartementDestinataire() { return departementDestinataire; }
    public void setDepartementDestinataire(Departement departementDestinataire) { this.departementDestinataire = departementDestinataire; }
    public Agent getDemandeur() { return demandeur; }
    public void setDemandeur(Agent demandeur) { this.demandeur = demandeur; }
    public Agent getAttributaire() { return attributaire; }
    public void setAttributaire(Agent attributaire) { this.attributaire = attributaire; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTypeAutrePrecision() { return typeAutrePrecision; }
    public void setTypeAutrePrecision(String typeAutrePrecision) { this.typeAutrePrecision = typeAutrePrecision; }
    public String getSysteme() { return systeme; }
    public void setSysteme(String systeme) { this.systeme = systeme; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public String getSourceExterne() { return sourceExterne; }
    public void setSourceExterne(String sourceExterne) { this.sourceExterne = sourceExterne; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public String getPriorite() { return priorite; }
    public void setPriorite(String priorite) { this.priorite = priorite; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMotifAnnulation() { return motifAnnulation; }
    public void setMotifAnnulation(String motifAnnulation) { this.motifAnnulation = motifAnnulation; }
    public Instant getEcheance() { return echeance; }
    public void setEcheance(Instant echeance) { this.echeance = echeance; }
    public Instant getPremierePriseEnChargeAt() { return premierePriseEnChargeAt; }
    public void setPremierePriseEnChargeAt(Instant premierePriseEnChargeAt) { this.premierePriseEnChargeAt = premierePriseEnChargeAt; }
    public Instant getResoluAt() { return resoluAt; }
    public void setResoluAt(Instant resoluAt) { this.resoluAt = resoluAt; }
    public boolean isSlaAlerteEnvoyee() { return slaAlerteEnvoyee; }
    public void setSlaAlerteEnvoyee(boolean slaAlerteEnvoyee) { this.slaAlerteEnvoyee = slaAlerteEnvoyee; }
    public boolean isArchive() { return archive; }
    public void setArchive(boolean archive) { this.archive = archive; }
    public String getObjetLieType() { return objetLieType; }
    public void setObjetLieType(String objetLieType) { this.objetLieType = objetLieType; }
    public String getObjetLieId() { return objetLieId; }
    public void setObjetLieId(String objetLieId) { this.objetLieId = objetLieId; }
    public String getObjetLieLibelle() { return objetLieLibelle; }
    public void setObjetLieLibelle(String objetLieLibelle) { this.objetLieLibelle = objetLieLibelle; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<TicketCommentaire> getCommentaires() { return commentaires; }
    public List<TicketPieceJointe> getPiecesJointes() { return piecesJointes; }
    @JsonProperty("categorieIncident")
    public String getCategorieIncident() { return categorie; }
    public java.util.Map<String, Object> getSla() { return sla; }
    public void setSla(java.util.Map<String, Object> sla) { this.sla = sla; }
    public List<TicketStatutHistorique> getHistorique() { return historique; }
    public void setHistorique(List<TicketStatutHistorique> historique) { this.historique = historique; }
}
