package com.mindsetalliance.core.iam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "agents")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "email_pro", unique = true, nullable = false)
    private String emailPro;
    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;
    private String nom;
    private String prenom;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departement_id")
    private Departement departement;
    private String statut;
    private boolean twoFactorEnabled;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String totpSecret;
    private String telephone;
    private String photoMime;
    @jakarta.persistence.Lob
    @jakarta.persistence.Column(name = "photo_profil", columnDefinition = "longblob")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONGVARBINARY)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private byte[] photoProfil;
    @Column(name = "doit_changer_mot_de_passe")
    private boolean doitChangerMotDePasse;
    @Column(name = "welcome_email_sent")
    private boolean welcomeEmailSent;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getEmailPro() { return emailPro; }
    public void setEmailPro(String emailPro) { this.emailPro = emailPro; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public Departement getDepartement() { return departement; }
    public void setDepartement(Departement departement) { this.departement = departement; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getPhotoMime() { return photoMime; }
    public void setPhotoMime(String photoMime) { this.photoMime = photoMime; }
    public byte[] getPhotoProfil() { return photoProfil; }
    public void setPhotoProfil(byte[] photoProfil) { this.photoProfil = photoProfil; }
    public boolean isDoitChangerMotDePasse() { return doitChangerMotDePasse; }
    public void setDoitChangerMotDePasse(boolean doitChangerMotDePasse) { this.doitChangerMotDePasse = doitChangerMotDePasse; }
    public boolean isWelcomeEmailSent() { return welcomeEmailSent; }
    public void setWelcomeEmailSent(boolean welcomeEmailSent) { this.welcomeEmailSent = welcomeEmailSent; }
    public Instant getCreatedAt() { return createdAt; }
}
