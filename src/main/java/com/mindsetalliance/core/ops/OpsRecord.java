package com.mindsetalliance.core.ops;

import com.mindsetalliance.core.iam.Agent;
import com.mindsetalliance.core.iam.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ops_records")
public class OpsRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String module;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private String titre;

    @Column(columnDefinition = "text")
    private String description;

    private String statut;
    private String priorite;
    private String zone;
    private String canal;
    private BigDecimal montant;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "meta_json", columnDefinition = "longtext")
    private String metaJson;

    private String motif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Agent createdBy;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getPriorite() { return priorite; }
    public void setPriorite(String priorite) { this.priorite = priorite; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public String getMetaJson() { return metaJson; }
    public void setMetaJson(String metaJson) { this.metaJson = metaJson; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public Agent getCreatedBy() { return createdBy; }
    public void setCreatedBy(Agent createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
