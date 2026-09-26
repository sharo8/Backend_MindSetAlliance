package com.mindsetalliance.core.hr;

import com.mindsetalliance.core.iam.Agent;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bulletins_paie")
public class BulletinPaie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "agent_id")
    private Agent agent;
    private int periodeAnnee;
    private int periodeMois;
    @Column(precision = 14, scale = 2)
    private BigDecimal salaireBrut;
    @Column(name = "net_a_payer", precision = 14, scale = 2)
    private BigDecimal netAPayer;
    private String devise;
    private String statut;
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "bulletin", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ElementPaie> elements = new ArrayList<>();

    public Long getId() { return id; }
    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }
    public int getPeriodeAnnee() { return periodeAnnee; }
    public void setPeriodeAnnee(int periodeAnnee) { this.periodeAnnee = periodeAnnee; }
    public int getPeriodeMois() { return periodeMois; }
    public void setPeriodeMois(int periodeMois) { this.periodeMois = periodeMois; }
    public BigDecimal getSalaireBrut() { return salaireBrut; }
    public void setSalaireBrut(BigDecimal salaireBrut) { this.salaireBrut = salaireBrut; }
    public BigDecimal getNetAPayer() { return netAPayer; }
    public void setNetAPayer(BigDecimal netAPayer) { this.netAPayer = netAPayer; }
    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public List<ElementPaie> getElements() { return elements; }
}
