package com.mindsetalliance.core.iam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "projects")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String code;
    private String nom;
    private String description;
    private String statut;
    private LocalDate dateLancement;
    private String deviseGestion;
    private String backendUrl;
    @Column(name = "ville_reference")
    private String villeReference;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public LocalDate getDateLancement() { return dateLancement; }
    public void setDateLancement(LocalDate dateLancement) { this.dateLancement = dateLancement; }
    public String getDeviseGestion() { return deviseGestion; }
    public void setDeviseGestion(String deviseGestion) { this.deviseGestion = deviseGestion; }
    public String getBackendUrl() { return backendUrl; }
    public void setBackendUrl(String backendUrl) { this.backendUrl = backendUrl; }
    public String getVilleReference() { return villeReference; }
    public void setVilleReference(String villeReference) { this.villeReference = villeReference; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
