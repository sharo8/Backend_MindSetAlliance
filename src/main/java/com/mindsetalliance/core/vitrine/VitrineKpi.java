package com.mindsetalliance.core.vitrine;

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
@Table(name = "vitrine_kpis")
public class VitrineKpi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;
    private String categorie;
    private String cle;
    @Column(precision = 18, scale = 4)
    private BigDecimal valeur;
    private Instant derniereMaj = Instant.now();

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public String getCle() { return cle; }
    public void setCle(String cle) { this.cle = cle; }
    public BigDecimal getValeur() { return valeur; }
    public void setValeur(BigDecimal valeur) { this.valeur = valeur; }
    public Instant getDerniereMaj() { return derniereMaj; }
    public void setDerniereMaj(Instant derniereMaj) { this.derniereMaj = derniereMaj; }
}
