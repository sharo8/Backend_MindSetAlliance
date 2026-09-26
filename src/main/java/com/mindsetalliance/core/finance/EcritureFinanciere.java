package com.mindsetalliance.core.finance;

import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ecritures_financieres")
public class EcritureFinanciere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;
    private String type;
    private String libelle;
    @Column(precision = 14, scale = 2)
    private BigDecimal montant;
    private String devise;
    private String statut;
    private String motifAnnulation;
    @ManyToOne
    @JoinColumn(name = "created_by")
    private Agent createdBy;
    private Instant createdAt = Instant.now();
    private Instant validatedAt;

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getMotifAnnulation() { return motifAnnulation; }
    public void setMotifAnnulation(String motifAnnulation) { this.motifAnnulation = motifAnnulation; }
    public Agent getCreatedBy() { return createdBy; }
    public void setCreatedBy(Agent createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getValidatedAt() { return validatedAt; }
    public void setValidatedAt(Instant validatedAt) { this.validatedAt = validatedAt; }
}
