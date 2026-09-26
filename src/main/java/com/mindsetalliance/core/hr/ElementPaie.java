package com.mindsetalliance.core.hr;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "elements_paie")
public class ElementPaie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "bulletin_id")
    @JsonIgnore
    private BulletinPaie bulletin;
    private String type;
    private String libelle;
    @Column(precision = 14, scale = 2)
    private BigDecimal montant;

    public Long getId() { return id; }
    public BulletinPaie getBulletin() { return bulletin; }
    public void setBulletin(BulletinPaie bulletin) { this.bulletin = bulletin; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
}
