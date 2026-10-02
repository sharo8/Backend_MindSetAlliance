package com.mindsetalliance.core.iam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "departements")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Departement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String nom;
    @Column(length = 500)
    private String description;
    @Column(nullable = false)
    private String statut = "ACTIF";

    @ManyToMany
    @JoinTable(name = "departement_roles",
            joinColumns = @JoinColumn(name = "departement_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> rolesDefaut = new HashSet<>();

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Set<Role> getRolesDefaut() { return rolesDefaut; }
    public void setRolesDefaut(Set<Role> rolesDefaut) { this.rolesDefaut = rolesDefaut; }
}
