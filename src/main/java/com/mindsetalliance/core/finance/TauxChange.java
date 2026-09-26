package com.mindsetalliance.core.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "taux_change")
public class TauxChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String deviseSource;
    private String deviseCible;
    @Column(precision = 18, scale = 8)
    private BigDecimal taux;
    private LocalDate dateEffet;

    public Long getId() { return id; }
    public String getDeviseSource() { return deviseSource; }
    public void setDeviseSource(String deviseSource) { this.deviseSource = deviseSource; }
    public String getDeviseCible() { return deviseCible; }
    public void setDeviseCible(String deviseCible) { this.deviseCible = deviseCible; }
    public BigDecimal getTaux() { return taux; }
    public void setTaux(BigDecimal taux) { this.taux = taux; }
    public LocalDate getDateEffet() { return dateEffet; }
    public void setDateEffet(LocalDate dateEffet) { this.dateEffet = dateEffet; }
}
