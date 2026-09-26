package com.mindsetalliance.core.finance;

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

@Entity
@Table(name = "budgets")
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;
    private int annee;
    private String libelle;
    @Column(precision = 14, scale = 2)
    private BigDecimal montantPrevu;
    private String devise;

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public int getAnnee() { return annee; }
    public void setAnnee(int annee) { this.annee = annee; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public BigDecimal getMontantPrevu() { return montantPrevu; }
    public void setMontantPrevu(BigDecimal montantPrevu) { this.montantPrevu = montantPrevu; }
    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }
}
