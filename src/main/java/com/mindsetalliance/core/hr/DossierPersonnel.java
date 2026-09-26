package com.mindsetalliance.core.hr;

import com.mindsetalliance.core.common.AesAttributeConverter;
import com.mindsetalliance.core.iam.Agent;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "dossiers_personnel")
public class DossierPersonnel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Convert(converter = AesAttributeConverter.class)
    private String numeroIdentite;

    @Convert(converter = AesAttributeConverter.class)
    private String coordonneesBancaires;

    private LocalDate dateEmbauche;
    @Column(precision = 14, scale = 2)
    private BigDecimal salaireBase;
    private String devise;

    public Long getId() { return id; }
    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }
    public String getNumeroIdentite() { return numeroIdentite; }
    public void setNumeroIdentite(String numeroIdentite) { this.numeroIdentite = numeroIdentite; }
    public String getCoordonneesBancaires() { return coordonneesBancaires; }
    public void setCoordonneesBancaires(String coordonneesBancaires) { this.coordonneesBancaires = coordonneesBancaires; }
    public LocalDate getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(LocalDate dateEmbauche) { this.dateEmbauche = dateEmbauche; }
    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }
    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }
}
